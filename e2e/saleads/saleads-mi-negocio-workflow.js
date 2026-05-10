const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || "";
const GOOGLE_EMAIL =
  process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const HEADLESS = process.env.HEADLESS !== "false";
const SLOW_MO = Number(process.env.SLOW_MO || 150);

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

function stamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function capture(page, outputDir, name, fullPage = false) {
  const shotPath = path.join(outputDir, `${name}.png`);
  await page.screenshot({ path: shotPath, fullPage });
  return shotPath;
}

async function waitForUi(page) {
  try {
    await page.waitForLoadState("networkidle", { timeout: 15000 });
  } catch (_error) {
    await page.waitForTimeout(800);
  }
  await page.waitForTimeout(500);
}

async function firstVisible(candidates, timeoutMs = 20000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const locator of candidates) {
      if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
        return locator.first();
      }
    }
    await candidates[0].page().waitForTimeout(250);
  }
  throw new Error("No matching visible element found.");
}

async function assertVisible(locator, message, timeout = 15000) {
  await locator.first().waitFor({ state: "visible", timeout });
  return message;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function handleGoogleSelectorIfVisible(targetPage) {
  await waitForUi(targetPage);

  const accountPicker = targetPage.getByText(new RegExp(GOOGLE_EMAIL, "i"));
  if ((await accountPicker.count()) > 0 && (await accountPicker.first().isVisible())) {
    await clickAndWait(accountPicker.first(), targetPage);
    return "Selected existing Google account.";
  }

  const emailInput = targetPage.locator(
    'input[type="email"], input[name="identifier"], input[autocomplete="username"]',
  );
  if ((await emailInput.count()) > 0 && (await emailInput.first().isVisible())) {
    await emailInput.first().fill(GOOGLE_EMAIL);
    const nextButton = await firstVisible([
      targetPage.getByRole("button", { name: /Next|Siguiente/i }),
      targetPage.getByText(/Next|Siguiente/i),
    ]);
    await clickAndWait(nextButton, targetPage);
    return "Filled Google email and continued.";
  }

  return "No Google account selector interaction was needed.";
}

async function openLegalAndReturn({
  appPage,
  context,
  linkPattern,
  headingPattern,
  outputDir,
  screenshotName,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const link = await firstVisible([
    appPage.getByRole("link", { name: linkPattern }),
    appPage.getByText(linkPattern),
  ]);

  await clickAndWait(link, appPage);
  let legalPage = await popupPromise;
  const openedNewTab = Boolean(legalPage);

  if (!legalPage) {
    legalPage = appPage;
  } else {
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 });
    await waitForUi(legalPage);
  }

  await assertVisible(
    legalPage.getByRole("heading", { name: headingPattern }),
    "Legal heading is visible.",
    20000,
  );

  const legalContent = legalPage.locator("main, article, body");
  await legalContent.first().waitFor({ state: "visible", timeout: 10000 });
  const legalText = await legalContent.first().innerText();
  if (!legalText || legalText.trim().length < 120) {
    throw new Error("Legal content appears to be missing or too short.");
  }

  const screenshotPath = await capture(legalPage, outputDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (openedNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" });
    await waitForUi(appPage);
  }

  return { screenshotPath, finalUrl };
}

async function run() {
  const outputDir = path.join(__dirname, "artifacts", stamp());
  await ensureDir(outputDir);

  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = { status: "FAIL", details: [], evidence: [] };
  }

  let browser;
  let context;
  let appPage;

  const setPass = (name, details = [], evidence = []) => {
    report[name] = { status: "PASS", details, evidence };
  };

  const setFail = (name, error, details = [], evidence = []) => {
    report[name] = {
      status: "FAIL",
      details: [...details, `Error: ${error.message || String(error)}`],
      evidence,
    };
  };

  if (!LOGIN_URL) {
    const missingUrlError =
      "Missing SALEADS_LOGIN_URL (or SALEADS_URL). Provide the environment login page URL.";
    report["Login"] = {
      status: "FAIL",
      details: [missingUrlError],
      evidence: [],
    };
    for (const field of REPORT_FIELDS) {
      if (field === "Login") {
        continue;
      }
      report[field] = {
        status: "FAIL",
        details: ["Not executed because login URL was not provided."],
        evidence: [],
      };
    }

    const finalReport = {
      generatedAt: new Date().toISOString(),
      loginUrl: LOGIN_URL,
      rulesApplied: [
        "No fixed domain in code. URL is environment-provided.",
        "Visible-text selectors are preferred.",
        "UI wait is applied after each click.",
        "New-tab legal flows are handled and returned to app.",
        "Screenshots are captured at required checkpoints.",
      ],
      steps: report,
    };

    const reportPath = path.join(outputDir, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    console.log("SALEADS_LOGIN_URL is required to execute UI validation.");
    console.log(`Report: ${reportPath}`);
    process.exitCode = 1;
    return;
  }

  try {
    browser = await chromium.launch({ headless: HEADLESS, slowMo: SLOW_MO });
    context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
    appPage = await context.newPage();

    try {
      await appPage.goto(LOGIN_URL, { waitUntil: "domcontentloaded", timeout: 60000 });
      await waitForUi(appPage);

      const googleButton = await firstVisible([
        appPage.getByRole("button", { name: /Google|Sign in|Iniciar|Continuar/i }),
        appPage.getByRole("link", { name: /Google|Sign in|Iniciar|Continuar/i }),
        appPage.getByText(/Google|Sign in|Iniciar|Continuar/i),
      ]);

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(googleButton, appPage);
      const popup = await popupPromise;

      const googleFlowNotes = [];
      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 25000 });
        googleFlowNotes.push(await handleGoogleSelectorIfVisible(popup));
        try {
          await popup.waitForClose({ timeout: 30000 });
        } catch (_error) {
          // The popup can stay open in some Google auth flows.
        }
      }
      googleFlowNotes.push(await handleGoogleSelectorIfVisible(appPage));
      await appPage.bringToFront();
      await waitForUi(appPage);

      await assertVisible(
        await firstVisible(
          [appPage.getByText(/Negocio/i), appPage.locator("aside"), appPage.locator("nav")],
          20000,
        ),
        "Sidebar/main application is visible.",
      );

      const dashboardShot = await capture(appPage, outputDir, "01-dashboard-loaded");
      setPass("Login", ["Main app interface loaded.", "Left sidebar is visible.", ...googleFlowNotes], [
        dashboardShot,
      ]);
    } catch (error) {
      setFail("Login", error);
    }

    try {
      const negocioSection = await firstVisible([
        appPage.getByRole("button", { name: /Negocio/i }),
        appPage.getByRole("link", { name: /Negocio/i }),
        appPage.getByText(/^Negocio$/i),
        appPage.getByText(/Negocio/i),
      ]);
      await clickAndWait(negocioSection, appPage);

      const miNegocio = await firstVisible([
        appPage.getByRole("button", { name: /Mi Negocio/i }),
        appPage.getByRole("link", { name: /Mi Negocio/i }),
        appPage.getByText(/Mi Negocio/i),
      ]);
      await clickAndWait(miNegocio, appPage);

      await assertVisible(appPage.getByText(/Agregar Negocio/i), "Agregar Negocio is visible.");
      await assertVisible(
        appPage.getByText(/Administrar Negocios/i),
        "Administrar Negocios is visible.",
      );

      const menuShot = await capture(appPage, outputDir, "02-mi-negocio-expanded");
      setPass(
        "Mi Negocio menu",
        ["Submenu expanded with Agregar Negocio and Administrar Negocios."],
        [menuShot],
      );
    } catch (error) {
      setFail("Mi Negocio menu", error);
    }

    try {
      const agregarNegocio = await firstVisible([
        appPage.getByRole("button", { name: /^Agregar Negocio$/i }),
        appPage.getByRole("link", { name: /^Agregar Negocio$/i }),
        appPage.getByText(/^Agregar Negocio$/i),
      ]);
      await clickAndWait(agregarNegocio, appPage);

      await assertVisible(appPage.getByText(/Crear Nuevo Negocio/i), "Modal title visible.");
      const businessNameInput = await firstVisible([
        appPage.getByLabel(/Nombre del Negocio/i),
        appPage.getByPlaceholder(/Nombre del Negocio/i),
        appPage.locator('input[name*="negocio"], input[id*="negocio"]'),
      ]);
      await assertVisible(appPage.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i), "Capacity text visible.");
      await assertVisible(appPage.getByRole("button", { name: /Cancelar/i }), "Cancelar visible.");
      await assertVisible(
        appPage.getByRole("button", { name: /Crear Negocio/i }),
        "Crear Negocio visible.",
      );

      await businessNameInput.fill("Negocio Prueba Automatizacion");
      const modalShot = await capture(appPage, outputDir, "03-crear-negocio-modal");
      const cancelBtn = await firstVisible([
        appPage.getByRole("button", { name: /Cancelar/i }),
        appPage.getByText(/^Cancelar$/i),
      ]);
      await clickAndWait(cancelBtn, appPage);

      setPass(
        "Agregar Negocio modal",
        [
          "Modal title, input, usage text, and action buttons validated.",
          "Optional fill and cancel action completed.",
        ],
        [modalShot],
      );
    } catch (error) {
      setFail("Agregar Negocio modal", error);
    }

    try {
      const miNegocioAgain = await firstVisible([
        appPage.getByRole("button", { name: /Mi Negocio/i }),
        appPage.getByRole("link", { name: /Mi Negocio/i }),
        appPage.getByText(/Mi Negocio/i),
      ]);
      await clickAndWait(miNegocioAgain, appPage);

      const administrarNegocios = await firstVisible([
        appPage.getByRole("button", { name: /Administrar Negocios/i }),
        appPage.getByRole("link", { name: /Administrar Negocios/i }),
        appPage.getByText(/Administrar Negocios/i),
      ]);
      await clickAndWait(administrarNegocios, appPage);

      await assertVisible(appPage.getByText(/Informacion General|Información General/i), "Informacion General visible.");
      await assertVisible(
        appPage.getByText(/Detalles de la Cuenta/i),
        "Detalles de la Cuenta visible.",
      );
      await assertVisible(appPage.getByText(/Tus Negocios/i), "Tus Negocios visible.");
      await assertVisible(
        appPage.getByText(/Seccion Legal|Sección Legal/i),
        "Seccion Legal visible.",
      );

      const accountShot = await capture(appPage, outputDir, "04-administrar-negocios", true);
      setPass(
        "Administrar Negocios view",
        [
          "All required account sections are visible: Informacion General, Detalles de la Cuenta, Tus Negocios, Seccion Legal.",
        ],
        [accountShot],
      );
    } catch (error) {
      setFail("Administrar Negocios view", error);
    }

    try {
      await assertVisible(appPage.getByText(/BUSINESS PLAN/i), "Business plan label visible.");
      await assertVisible(appPage.getByRole("button", { name: /Cambiar Plan/i }), "Cambiar Plan visible.");

      const possibleEmail = appPage.locator('text=/@/');
      if ((await possibleEmail.count()) < 1) {
        throw new Error("User email was not detected in Informacion General.");
      }

      const possibleName = appPage.locator("h1, h2, h3, p, span").filter({
        hasText: /^[A-Za-z][A-Za-z .'-]{2,}$/,
      });
      if ((await possibleName.count()) < 1) {
        throw new Error("User name was not confidently detected in Informacion General.");
      }

      setPass("Informacion General", [
        "User name and email are visible.",
        "BUSINESS PLAN and Cambiar Plan are visible.",
      ]);
    } catch (error) {
      setFail("Informacion General", error);
    }

    try {
      await assertVisible(appPage.getByText(/Cuenta creada/i), "Cuenta creada visible.");
      await assertVisible(
        appPage.getByText(/Estado activo|Estado\s+activo/i),
        "Estado activo visible.",
      );
      await assertVisible(
        appPage.getByText(/Idioma seleccionado/i),
        "Idioma seleccionado visible.",
      );

      setPass("Detalles de la Cuenta", [
        "Cuenta creada, Estado activo and Idioma seleccionado are visible.",
      ]);
    } catch (error) {
      setFail("Detalles de la Cuenta", error);
    }

    try {
      await assertVisible(appPage.getByText(/Tus Negocios/i), "Tus Negocios section visible.");
      await assertVisible(appPage.getByRole("button", { name: /Agregar Negocio/i }), "Agregar Negocio button visible.");
      await assertVisible(
        appPage.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i),
        "Business capacity text visible.",
      );

      setPass("Tus Negocios", [
        "Business list, Agregar Negocio button and usage text are visible.",
      ]);
    } catch (error) {
      setFail("Tus Negocios", error);
    }

    try {
      const legalResult = await openLegalAndReturn({
        appPage,
        context,
        linkPattern: /Terminos y Condiciones|Términos y Condiciones/i,
        headingPattern: /Terminos y Condiciones|Términos y Condiciones/i,
        outputDir,
        screenshotName: "08-terminos-y-condiciones",
      });

      setPass(
        "Terminos y Condiciones",
        [`Legal content validated. Final URL: ${legalResult.finalUrl}`],
        [legalResult.screenshotPath],
      );
    } catch (error) {
      setFail("Terminos y Condiciones", error);
    }

    try {
      const privacyResult = await openLegalAndReturn({
        appPage,
        context,
        linkPattern: /Politica de Privacidad|Política de Privacidad/i,
        headingPattern: /Politica de Privacidad|Política de Privacidad/i,
        outputDir,
        screenshotName: "09-politica-de-privacidad",
      });

      setPass(
        "Politica de Privacidad",
        [`Legal content validated. Final URL: ${privacyResult.finalUrl}`],
        [privacyResult.screenshotPath],
      );
    } catch (error) {
      setFail("Politica de Privacidad", error);
    }
  } finally {
    if (browser) {
      await browser.close();
    }
  }

  const finalReport = {
    generatedAt: new Date().toISOString(),
    loginUrl: LOGIN_URL,
    rulesApplied: [
      "No fixed domain in code. URL is environment-provided.",
      "Visible-text selectors are preferred.",
      "UI wait is applied after each click.",
      "New-tab legal flows are handled and returned to app.",
      "Screenshots are captured at required checkpoints.",
    ],
    steps: report,
  };

  const reportPath = path.join(outputDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

  console.log("=== SaleADS Mi Negocio Workflow Report ===");
  for (const field of REPORT_FIELDS) {
    const entry = report[field];
    console.log(`${field}: ${entry.status}`);
    if (entry.details.length > 0) {
      console.log(`  - ${entry.details.join("\n  - ")}`);
    }
    if (entry.evidence.length > 0) {
      console.log(`  - Evidence: ${entry.evidence.join(", ")}`);
    }
  }
  console.log(`Report: ${reportPath}`);

  const hasFailure = Object.values(report).some((step) => step.status !== "PASS");
  if (hasFailure) {
    process.exitCode = 1;
  }
}

run().catch((error) => {
  console.error("Workflow execution failed before report generation.");
  console.error(error);
  process.exitCode = 1;
});
