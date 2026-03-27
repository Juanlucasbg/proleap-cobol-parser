const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const EMAIL_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const REQUIRED_REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
];

function nowFileStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function cleanFileName(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function readArgValue(flag) {
  const byEqual = process.argv.find((entry) => entry.startsWith(`${flag}=`));
  if (byEqual) return byEqual.slice(flag.length + 1);
  const idx = process.argv.indexOf(flag);
  if (idx >= 0 && process.argv[idx + 1]) return process.argv[idx + 1];
  return undefined;
}

async function sleep(ms) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function isVisible(locator, timeout = 2500) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch (error) {
    return false;
  }
}

async function firstVisible(builders, timeoutEach = 2000) {
  for (const build of builders) {
    const locator = build();
    if (await isVisible(locator, timeoutEach)) {
      return locator.first();
    }
  }
  return null;
}

async function clickAndSettle(locator, page) {
  await locator.click({ timeout: 10000 });
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await sleep(750);
}

async function expectVisibleOrThrow(page, builders, message) {
  const locator = await firstVisible(
    builders.map((builder) => () => builder(page)),
    3000,
  );
  if (!locator) {
    throw new Error(message);
  }
  return locator;
}

function reportEntry(pass, details, extras = {}) {
  return {
    status: pass ? "PASS" : "FAIL",
    details,
    ...extras,
  };
}

async function run() {
  const runId = nowFileStamp();
  const rootDir = __dirname;
  const artifactsDir = path.join(rootDir, "artifacts", runId);
  ensureDir(artifactsDir);

  const report = {
    name: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
    runId,
    startedAt: new Date().toISOString(),
    environment: {
      loginUrl: null,
      headless: true,
    },
    screenshots: [],
    legalUrls: {},
    results: {},
  };

  const loginUrl =
    readArgValue("--url") ||
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    "";
  const headless = `${process.env.HEADLESS || "true"}` !== "false";
  report.environment.loginUrl = loginUrl || "(not provided)";
  report.environment.headless = headless;

  for (const field of REQUIRED_REPORT_FIELDS) {
    report.results[field] = reportEntry(false, "Not executed.");
  }

  const browser = await chromium.launch({ headless });
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 },
  });
  let page = await context.newPage();

  async function checkpointScreenshot(targetPage, label, fullPage = false) {
    const fileName = `${report.screenshots.length + 1}-${cleanFileName(label)}.png`;
    const absolute = path.join(artifactsDir, fileName);
    await targetPage.screenshot({ path: absolute, fullPage });
    report.screenshots.push({
      label,
      path: path.relative(rootDir, absolute),
    });
  }

  function setResult(field, pass, details, extras = {}) {
    report.results[field] = reportEntry(pass, details, extras);
  }

  async function openLoginPageIfProvided() {
    if (!loginUrl) {
      return;
    }
    await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  }

  function pickAppPage() {
    const pages = context.pages();
    const nonGoogle = pages
      .slice()
      .reverse()
      .find((p) => {
        const url = p.url().toLowerCase();
        return !url.includes("accounts.google.") && !url.includes("google.com/signin");
      });
    return nonGoogle || page;
  }

  async function ensureMiNegocioExpanded(appPage) {
    const miNegocioOption = await firstVisible([
      () => appPage.getByRole("link", { name: /mi negocio/i }),
      () => appPage.getByRole("button", { name: /mi negocio/i }),
      () => appPage.getByText(/mi negocio/i),
    ]);

    if (!miNegocioOption) {
      throw new Error("Could not find 'Mi Negocio' option in sidebar.");
    }

    await clickAndSettle(miNegocioOption, appPage);

    const submenuReady = await firstVisible([
      () => appPage.getByRole("link", { name: /agregar negocio/i }),
      () => appPage.getByRole("button", { name: /agregar negocio/i }),
      () => appPage.getByText(/agregar negocio/i),
    ]);

    if (!submenuReady) {
      throw new Error("Mi Negocio submenu did not expand.");
    }
  }

  async function openLegalLinkAndValidate(appPage, labelRegex, headingRegex, screenshotLabel, reportField, urlKey) {
    const legalTrigger = await firstVisible([
      () => appPage.getByRole("link", { name: labelRegex }),
      () => appPage.getByRole("button", { name: labelRegex }),
      () => appPage.getByText(labelRegex),
    ]);

    if (!legalTrigger) {
      throw new Error(`Could not find legal link: ${labelRegex}.`);
    }

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndSettle(legalTrigger, appPage);
    const popup = await popupPromise;

    let legalPage = appPage;
    if (popup) {
      legalPage = popup;
      await legalPage.bringToFront();
      await legalPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
      await legalPage.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
    } else {
      await appPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
      await appPage.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
    }

    const headingVisible = await firstVisible([
      () => legalPage.getByRole("heading", { name: headingRegex }),
      () => legalPage.getByText(headingRegex),
    ]);

    const legalTextVisible = await firstVisible([
      () => legalPage.locator("article p"),
      () => legalPage.locator("main p"),
      () => legalPage.locator("p"),
    ]);

    if (!headingVisible || !legalTextVisible) {
      throw new Error(`Legal content for '${reportField}' did not load correctly.`);
    }

    report.legalUrls[urlKey] = legalPage.url();
    await checkpointScreenshot(legalPage, screenshotLabel, true);
    setResult(reportField, true, "Legal page validated.", { url: legalPage.url() });

    if (popup) {
      await popup.close({ runBeforeUnload: true }).catch(() => {});
      await appPage.bringToFront();
    } else {
      await appPage.goBack({ waitUntil: "domcontentloaded", timeout: 20000 }).catch(() => {});
      await appPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
    }
  }

  try {
    await openLoginPageIfProvided();

    // Step 1: Login with Google
    try {
      const loginButton = await firstVisible([
        () => page.getByRole("button", { name: /sign in with google|iniciar sesión con google|continuar con google/i }),
        () => page.getByRole("link", { name: /sign in with google|iniciar sesión con google|continuar con google/i }),
        () => page.getByRole("button", { name: /google/i }),
        () => page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
      ], 3500);

      if (!loginButton) {
        throw new Error(
          "Google login button not found. Provide SALEADS_LOGIN_URL or open the SaleADS login page before running.",
        );
      }

      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickAndSettle(loginButton, page);
      const popup = await popupPromise;
      const authPage = popup || page;

      if (popup) {
        await authPage.bringToFront();
        await authPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
        await authPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
      }

      const accountChoice = await firstVisible([
        () => authPage.getByRole("button", { name: new RegExp(EMAIL_ACCOUNT, "i") }),
        () => authPage.getByRole("link", { name: new RegExp(EMAIL_ACCOUNT, "i") }),
        () => authPage.getByText(new RegExp(EMAIL_ACCOUNT, "i")),
      ], 2500);

      if (accountChoice) {
        await clickAndSettle(accountChoice, authPage);
      }

      page = pickAppPage();
      await page.bringToFront();
      await page.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
      await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});

      const appMainVisible = await firstVisible([
        () => page.locator("main"),
        () => page.getByText(/dashboard|inicio|negocio|mi negocio/i),
      ], 5000);

      const sidebarVisible = await firstVisible([
        () => page.locator("aside"),
        () => page.locator("nav"),
        () => page.getByText(/negocio|mi negocio/i),
      ], 5000);

      if (!appMainVisible || !sidebarVisible) {
        throw new Error("Main application interface or sidebar is not visible after login.");
      }

      await checkpointScreenshot(page, "dashboard-loaded", true);
      setResult("Login", true, "Login successful. Main interface and sidebar are visible.");
    } catch (error) {
      setResult("Login", false, error.message);
    }

    // Step 2: Open Mi Negocio menu
    try {
      if (report.results.Login.status !== "PASS") {
        throw new Error("Skipped because Login failed.");
      }

      const negocioSection = await firstVisible([
        () => page.getByRole("button", { name: /negocio/i }),
        () => page.getByRole("link", { name: /negocio/i }),
        () => page.getByText(/^negocio$/i),
      ]);

      if (negocioSection) {
        await clickAndSettle(negocioSection, page);
      }

      await ensureMiNegocioExpanded(page);

      const agregarVisible = await firstVisible([
        () => page.getByRole("link", { name: /agregar negocio/i }),
        () => page.getByRole("button", { name: /agregar negocio/i }),
        () => page.getByText(/agregar negocio/i),
      ]);

      const administrarVisible = await firstVisible([
        () => page.getByRole("link", { name: /administrar negocios/i }),
        () => page.getByRole("button", { name: /administrar negocios/i }),
        () => page.getByText(/administrar negocios/i),
      ]);

      if (!agregarVisible || !administrarVisible) {
        throw new Error("Mi Negocio submenu does not show both required options.");
      }

      await checkpointScreenshot(page, "mi-negocio-menu-expanded", false);
      setResult("Mi Negocio menu", true, "Submenu expanded with Agregar/Administrar options.");
    } catch (error) {
      setResult("Mi Negocio menu", false, error.message);
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      if (report.results["Mi Negocio menu"].status !== "PASS") {
        throw new Error("Skipped because Mi Negocio menu failed.");
      }

      const agregarNegocio = await expectVisibleOrThrow(
        page,
        [
          (p) => p.getByRole("link", { name: /agregar negocio/i }),
          (p) => p.getByRole("button", { name: /agregar negocio/i }),
          (p) => p.getByText(/agregar negocio/i),
        ],
        "Agregar Negocio option not visible.",
      );
      await clickAndSettle(agregarNegocio, page);

      const modalTitle = await firstVisible([
        () => page.getByRole("dialog").getByText(/crear nuevo negocio/i),
        () => page.getByText(/crear nuevo negocio/i),
      ], 5000);
      const businessNameInput = await firstVisible([
        () => page.getByLabel(/nombre del negocio/i),
        () => page.getByPlaceholder(/nombre del negocio/i),
        () => page.locator("input").filter({ hasText: /nombre del negocio/i }),
      ]);
      const quotaText = await firstVisible([() => page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)]);
      const cancelButton = await firstVisible([() => page.getByRole("button", { name: /cancelar/i })]);
      const createButton = await firstVisible([() => page.getByRole("button", { name: /crear negocio/i })]);

      if (!modalTitle || !businessNameInput || !quotaText || !cancelButton || !createButton) {
        throw new Error("Agregar Negocio modal is missing required elements.");
      }

      await checkpointScreenshot(page, "agregar-negocio-modal", false);
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndSettle(cancelButton, page);
      setResult("Agregar Negocio modal", true, "Modal validated and closed via Cancelar.");
    } catch (error) {
      setResult("Agregar Negocio modal", false, error.message);
    }

    // Step 4: Open Administrar Negocios
    try {
      if (report.results["Mi Negocio menu"].status !== "PASS") {
        throw new Error("Skipped because Mi Negocio menu failed.");
      }

      const administrar = await firstVisible([
        () => page.getByRole("link", { name: /administrar negocios/i }),
        () => page.getByRole("button", { name: /administrar negocios/i }),
        () => page.getByText(/administrar negocios/i),
      ]);

      if (!administrar) {
        await ensureMiNegocioExpanded(page);
      }

      const administrarFinal = await expectVisibleOrThrow(
        page,
        [
          (p) => p.getByRole("link", { name: /administrar negocios/i }),
          (p) => p.getByRole("button", { name: /administrar negocios/i }),
          (p) => p.getByText(/administrar negocios/i),
        ],
        "Administrar Negocios option not visible.",
      );
      await clickAndSettle(administrarFinal, page);

      const infoGeneral = await firstVisible([() => page.getByText(/información general/i)], 6000);
      const detallesCuenta = await firstVisible([() => page.getByText(/detalles de la cuenta/i)], 6000);
      const tusNegocios = await firstVisible([() => page.getByText(/tus negocios/i)], 6000);
      const seccionLegal = await firstVisible([() => page.getByText(/sección legal/i)], 6000);

      if (!infoGeneral || !detallesCuenta || !tusNegocios || !seccionLegal) {
        throw new Error("Administrar Negocios page is missing one or more required sections.");
      }

      await checkpointScreenshot(page, "administrar-negocios-page", true);
      setResult("Administrar Negocios view", true, "All major account sections are visible.");
    } catch (error) {
      setResult("Administrar Negocios view", false, error.message);
    }

    // Step 5: Validate Información General
    try {
      if (report.results["Administrar Negocios view"].status !== "PASS") {
        throw new Error("Skipped because Administrar Negocios view failed.");
      }

      const userName = await firstVisible([
        () => page.getByText(/nombre/i),
        () => page.getByText(/usuario/i),
      ]);
      const userEmail = await firstVisible([
        () => page.getByText(new RegExp(EMAIL_ACCOUNT, "i")),
        () => page.getByText(/@/),
      ]);
      const businessPlan = await firstVisible([() => page.getByText(/business plan/i)]);
      const changePlan = await firstVisible([() => page.getByRole("button", { name: /cambiar plan/i })]);

      if (!userName || !userEmail || !businessPlan || !changePlan) {
        throw new Error("Información General is missing one or more required fields.");
      }

      setResult("Información General", true, "Información General validated.");
    } catch (error) {
      setResult("Información General", false, error.message);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      if (report.results["Administrar Negocios view"].status !== "PASS") {
        throw new Error("Skipped because Administrar Negocios view failed.");
      }

      const cuentaCreada = await firstVisible([() => page.getByText(/cuenta creada/i)]);
      const estadoActivo = await firstVisible([() => page.getByText(/estado activo/i)]);
      const idiomaSeleccionado = await firstVisible([() => page.getByText(/idioma seleccionado/i)]);

      if (!cuentaCreada || !estadoActivo || !idiomaSeleccionado) {
        throw new Error("Detalles de la Cuenta validation failed.");
      }

      setResult("Detalles de la Cuenta", true, "Detalles de la Cuenta validated.");
    } catch (error) {
      setResult("Detalles de la Cuenta", false, error.message);
    }

    // Step 7: Validate Tus Negocios
    try {
      if (report.results["Administrar Negocios view"].status !== "PASS") {
        throw new Error("Skipped because Administrar Negocios view failed.");
      }

      const businessList = await firstVisible([
        () => page.locator("ul li"),
        () => page.locator("table tbody tr"),
        () => page.getByText(/tus negocios/i),
      ]);
      const addButton = await firstVisible([
        () => page.getByRole("button", { name: /agregar negocio/i }),
        () => page.getByRole("link", { name: /agregar negocio/i }),
        () => page.getByText(/agregar negocio/i),
      ]);
      const quotaText = await firstVisible([() => page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)]);

      if (!businessList || !addButton || !quotaText) {
        throw new Error("Tus Negocios validation failed.");
      }

      setResult("Tus Negocios", true, "Tus Negocios validated.");
    } catch (error) {
      setResult("Tus Negocios", false, error.message);
    }

    // Step 8: Validate Términos y Condiciones
    try {
      if (report.results["Administrar Negocios view"].status !== "PASS") {
        throw new Error("Skipped because Administrar Negocios view failed.");
      }

      await openLegalLinkAndValidate(
        page,
        /términos y condiciones|terminos y condiciones/i,
        /términos y condiciones|terminos y condiciones/i,
        "terminos-y-condiciones",
        "Términos y Condiciones",
        "terminosYCondiciones",
      );
    } catch (error) {
      setResult("Términos y Condiciones", false, error.message);
    }

    // Step 9: Validate Política de Privacidad
    try {
      if (report.results["Administrar Negocios view"].status !== "PASS") {
        throw new Error("Skipped because Administrar Negocios view failed.");
      }

      await openLegalLinkAndValidate(
        page,
        /política de privacidad|politica de privacidad/i,
        /política de privacidad|politica de privacidad/i,
        "politica-de-privacidad",
        "Política de Privacidad",
        "politicaDePrivacidad",
      );
    } catch (error) {
      setResult("Política de Privacidad", false, error.message);
    }
  } finally {
    report.finishedAt = new Date().toISOString();
    report.durationSeconds =
      (new Date(report.finishedAt).getTime() - new Date(report.startedAt).getTime()) / 1000;

    const required = REQUIRED_REPORT_FIELDS.reduce((acc, key) => {
      acc[key] = report.results[key];
      return acc;
    }, {});
    report.finalSummary = {
      total: REQUIRED_REPORT_FIELDS.length,
      passed: REQUIRED_REPORT_FIELDS.filter((k) => report.results[k]?.status === "PASS").length,
      failed: REQUIRED_REPORT_FIELDS.filter((k) => report.results[k]?.status === "FAIL").length,
      fields: required,
    };

    const jsonPath = path.join(artifactsDir, "final-report.json");
    fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2));

    const markdownLines = [];
    markdownLines.push("# SaleADS Mi Negocio Test Report");
    markdownLines.push("");
    markdownLines.push(`- Run ID: \`${report.runId}\``);
    markdownLines.push(`- Started: ${report.startedAt}`);
    markdownLines.push(`- Finished: ${report.finishedAt}`);
    markdownLines.push(`- Login URL: ${report.environment.loginUrl}`);
    markdownLines.push("");
    markdownLines.push("## PASS/FAIL by requested field");
    markdownLines.push("");
    for (const field of REQUIRED_REPORT_FIELDS) {
      const result = report.results[field];
      markdownLines.push(`- **${field}**: ${result.status} — ${result.details}`);
      if (result.url) {
        markdownLines.push(`  - URL: ${result.url}`);
      }
    }
    markdownLines.push("");
    markdownLines.push("## Screenshots");
    markdownLines.push("");
    if (report.screenshots.length === 0) {
      markdownLines.push("- None captured.");
    } else {
      for (const shot of report.screenshots) {
        markdownLines.push(`- ${shot.label}: \`${shot.path}\``);
      }
    }
    markdownLines.push("");
    markdownLines.push("## Legal URLs");
    markdownLines.push("");
    markdownLines.push(`- Términos y Condiciones: ${report.legalUrls.terminosYCondiciones || "N/A"}`);
    markdownLines.push(`- Política de Privacidad: ${report.legalUrls.politicaDePrivacidad || "N/A"}`);

    const mdPath = path.join(artifactsDir, "final-report.md");
    fs.writeFileSync(mdPath, markdownLines.join("\n"));

    await browser.close();

    // Visible terminal output for cron jobs
    console.log(JSON.stringify(report.finalSummary, null, 2));
    console.log(`Artifacts directory: ${artifactsDir}`);

    if (report.finalSummary.failed > 0) {
      process.exitCode = 1;
    }
  }
}

run().catch((error) => {
  console.error("Fatal execution error:", error);
  process.exit(1);
});
