const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("@playwright/test");

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL =
  process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || "";
const HEADLESS = process.env.HEADLESS !== "false";
const ACTION_TIMEOUT_MS = Number(process.env.SALEADS_ACTION_TIMEOUT_MS || "20000");
const WAIT_AFTER_CLICK_MS = Number(process.env.SALEADS_WAIT_AFTER_CLICK_MS || "1200");

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

function timestampId() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function escapeRegExp(input) {
  return input.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function textRegex(text) {
  return new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i");
}

function safeFileName(input) {
  return input
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9-_]+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_|_$/g, "")
    .toLowerCase();
}

async function waitForUi(page) {
  try {
    await page.waitForLoadState("networkidle", { timeout: ACTION_TIMEOUT_MS });
  } catch {
    // Some UI transitions never reach network idle; continue with explicit delay.
  }
  await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
}

async function firstVisible(locators) {
  for (const locator of locators) {
    const candidate = locator.first();
    try {
      await candidate.waitFor({ state: "visible", timeout: 2500 });
      return candidate;
    } catch {
      // Try next candidate.
    }
  }
  return null;
}

function buildTextCandidates(scope, text) {
  const regex = textRegex(text);
  return [
    scope.getByRole("button", { name: regex }),
    scope.getByRole("link", { name: regex }),
    scope.getByRole("menuitem", { name: regex }),
    scope.getByRole("tab", { name: regex }),
    scope.getByText(regex),
  ];
}

async function findVisibleByTexts(scope, texts) {
  const candidates = texts.flatMap((text) => buildTextCandidates(scope, text));
  return firstVisible(candidates);
}

async function expectTextVisible(scope, texts, description) {
  const locator = await findVisibleByTexts(scope, texts);
  if (!locator) {
    throw new Error(`Expected visible element not found: ${description}`);
  }
  return locator;
}

async function clickAndWait(page, locator) {
  await locator.scrollIntoViewIfNeeded();
  await locator.click({ timeout: ACTION_TIMEOUT_MS });
  await waitForUi(page);
}

async function clickWithOptionalPopup(page, locator) {
  const popupPromise = page
    .context()
    .waitForEvent("page", { timeout: 6000 })
    .catch(() => null);

  await clickAndWait(page, locator);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: ACTION_TIMEOUT_MS });
    await waitForUi(popup);
  }

  return popup;
}

async function expectRegexInBody(page, regex, description) {
  const bodyText = await page.locator("body").innerText();
  if (!regex.test(bodyText)) {
    throw new Error(`Expected body text not found: ${description}`);
  }
}

async function runStep(stepName, report, fn) {
  process.stdout.write(`\n--- ${stepName} ---\n`);
  try {
    await fn();
    report.status[stepName] = "PASS";
    process.stdout.write(`PASS: ${stepName}\n`);
  } catch (error) {
    report.status[stepName] = "FAIL";
    report.errors.push({
      step: stepName,
      message: error instanceof Error ? error.message : String(error),
    });
    process.stdout.write(`FAIL: ${stepName}\n`);
    process.stdout.write(`${error instanceof Error ? error.message : String(error)}\n`);
  }
}

async function main() {
  const runId = timestampId();
  const artifactsRoot = path.resolve(__dirname, "..", "artifacts", TEST_NAME, runId);
  const screenshotsDir = path.join(artifactsRoot, "screenshots");
  await fs.mkdir(screenshotsDir, { recursive: true });

  const report = {
    testName: TEST_NAME,
    runId,
    startedAt: new Date().toISOString(),
    environment: {
      loginUrl: LOGIN_URL || null,
      googleAccount: GOOGLE_ACCOUNT,
      headless: HEADLESS,
    },
    status: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])),
    evidence: {
      screenshots: [],
      finalUrls: {
        terminosYCondiciones: null,
        politicaDePrivacidad: null,
      },
    },
    errors: [],
  };

  let browser;
  let context;
  let page;

  const capture = async (name, sourcePage = page, fullPage = false) => {
    const fileName = `${String(report.evidence.screenshots.length + 1).padStart(
      2,
      "0"
    )}_${safeFileName(name)}.png`;
    const screenshotPath = path.join(screenshotsDir, fileName);
    await sourcePage.screenshot({ path: screenshotPath, fullPage });
    report.evidence.screenshots.push(screenshotPath);
    process.stdout.write(`Screenshot: ${screenshotPath}\n`);
  };

  try {
    if (!LOGIN_URL) {
      throw new Error(
        "Missing SALEADS_LOGIN_URL (or SALEADS_BASE_URL). Set the current environment login URL at runtime."
      );
    }

    browser = await chromium.launch({ headless: HEADLESS });
    context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
    page = await context.newPage();

    await page.goto(LOGIN_URL, {
      waitUntil: "domcontentloaded",
      timeout: ACTION_TIMEOUT_MS,
    });
    await waitForUi(page);

    await runStep("Login", report, async () => {
      const sidebarAlreadyVisible = await firstVisible([
        page.locator("aside"),
        page.locator("nav"),
        page.getByText(/Mi Negocio|Negocio/i),
      ]);

      if (!sidebarAlreadyVisible) {
        const loginButton = await expectTextVisible(
          page,
          [
            "Sign in with Google",
            "Iniciar sesion con Google",
            "Iniciar sesion con google",
            "Continuar con Google",
            "Ingresar con Google",
            "Google",
          ],
          "Google login button"
        );

        const authPage = await clickWithOptionalPopup(page, loginButton);
        const accountPage = authPage || page;

        const accountChoice = await findVisibleByTexts(accountPage, [GOOGLE_ACCOUNT]);
        if (accountChoice) {
          await clickAndWait(accountPage, accountChoice);
        }

        if (authPage && !authPage.isClosed()) {
          await authPage.waitForTimeout(1500);
          if (!authPage.isClosed()) {
            await authPage.close();
          }
          await page.bringToFront();
        }
      }

      await expectTextVisible(
        page,
        ["Negocio", "Mi Negocio", "Dashboard"],
        "main application interface"
      );
      await firstVisible([page.locator("aside"), page.locator("nav")]);
      await capture("dashboard_loaded");
    });

    await runStep("Mi Negocio menu", report, async () => {
      const sidebar = (await firstVisible([page.locator("aside"), page.locator("nav")])) || page;

      const negocioSection = await findVisibleByTexts(sidebar, ["Negocio"]);
      if (negocioSection) {
        await clickAndWait(page, negocioSection);
      }

      const miNegocioOption = await expectTextVisible(
        sidebar,
        ["Mi Negocio"],
        "Mi Negocio option in sidebar"
      );
      await clickAndWait(page, miNegocioOption);

      await expectTextVisible(page, ["Agregar Negocio"], "Agregar Negocio submenu option");
      await expectTextVisible(
        page,
        ["Administrar Negocios"],
        "Administrar Negocios submenu option"
      );
      await capture("mi_negocio_menu_expanded");
    });

    await runStep("Agregar Negocio modal", report, async () => {
      const addBusiness = await expectTextVisible(
        page,
        ["Agregar Negocio"],
        "Agregar Negocio action"
      );
      await clickAndWait(page, addBusiness);

      await expectTextVisible(page, ["Crear Nuevo Negocio"], "Crear Nuevo Negocio modal title");

      const nameInput = await firstVisible([
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[placeholder*='Nombre']"),
      ]);
      if (!nameInput) {
        throw new Error("Input field 'Nombre del Negocio' not found in modal.");
      }

      await expectRegexInBody(page, /Tienes\s+2\s+de\s+3\s+negocios/i, "Tienes 2 de 3 negocios");
      await expectTextVisible(page, ["Cancelar"], "Cancelar button");
      await expectTextVisible(page, ["Crear Negocio"], "Crear Negocio button");
      await capture("agregar_negocio_modal");

      await nameInput.fill("Negocio Prueba Automatizacion");
      const cancelButton = await expectTextVisible(page, ["Cancelar"], "Cancelar button");
      await clickAndWait(page, cancelButton);
    });

    await runStep("Administrar Negocios view", report, async () => {
      const adminOptionVisible = await findVisibleByTexts(page, ["Administrar Negocios"]);
      if (!adminOptionVisible) {
        const miNegocioOption = await expectTextVisible(
          page,
          ["Mi Negocio"],
          "Mi Negocio option for submenu expansion"
        );
        await clickAndWait(page, miNegocioOption);
      }

      const manageBusinesses = await expectTextVisible(
        page,
        ["Administrar Negocios"],
        "Administrar Negocios option"
      );
      await clickAndWait(page, manageBusinesses);

      await expectRegexInBody(page, /Informacion General|Información General/i, "Informacion General");
      await expectRegexInBody(page, /Detalles de la Cuenta/i, "Detalles de la Cuenta");
      await expectRegexInBody(page, /Tus Negocios/i, "Tus Negocios");
      await expectRegexInBody(page, /Seccion Legal|Sección Legal/i, "Seccion Legal");
      await capture("administrar_negocios_view", page, true);
    });

    await runStep("Informacion General", report, async () => {
      await expectRegexInBody(page, /BUSINESS PLAN/i, "BUSINESS PLAN");
      await expectTextVisible(page, ["Cambiar Plan"], "Cambiar Plan button");
      await expectRegexInBody(
        page,
        /@[a-z0-9.-]+\.[a-z]{2,}/i,
        "user email in Informacion General section"
      );
    });

    await runStep("Detalles de la Cuenta", report, async () => {
      await expectRegexInBody(page, /Cuenta creada/i, "Cuenta creada");
      await expectRegexInBody(page, /Estado activo|Activo/i, "Estado activo");
      await expectRegexInBody(page, /Idioma seleccionado|Idioma/i, "Idioma seleccionado");
    });

    await runStep("Tus Negocios", report, async () => {
      await expectRegexInBody(page, /Tus Negocios/i, "Tus Negocios section title");
      await expectTextVisible(page, ["Agregar Negocio"], "Agregar Negocio button in business section");
      await expectRegexInBody(page, /Tienes\s+2\s+de\s+3\s+negocios/i, "Tienes 2 de 3 negocios");
    });

    async function validateLegalDocument({
      reportField,
      linkTexts,
      headingRegex,
      screenshotName,
      reportUrlKey,
    }) {
      await runStep(reportField, report, async () => {
        const link = await expectTextVisible(page, linkTexts, `${reportField} link`);
        const legalPage = await clickWithOptionalPopup(page, link);
        const activePage = legalPage || page;

        await expectRegexInBody(activePage, headingRegex, `${reportField} heading`);

        const legalText = await activePage.locator("body").innerText();
        if (legalText.trim().length < 120) {
          throw new Error(`${reportField} content appears empty or too short.`);
        }

        report.evidence.finalUrls[reportUrlKey] = activePage.url();
        await capture(screenshotName, activePage, true);

        if (legalPage) {
          await legalPage.close();
          await page.bringToFront();
          await waitForUi(page);
        } else {
          await page.goBack({ waitUntil: "domcontentloaded", timeout: ACTION_TIMEOUT_MS });
          await waitForUi(page);
        }
      });
    }

    await validateLegalDocument({
      reportField: "Terminos y Condiciones",
      linkTexts: ["Terminos y Condiciones", "Términos y Condiciones"],
      headingRegex: /Terminos y Condiciones|Términos y Condiciones/i,
      screenshotName: "terminos_y_condiciones",
      reportUrlKey: "terminosYCondiciones",
    });

    await validateLegalDocument({
      reportField: "Politica de Privacidad",
      linkTexts: ["Politica de Privacidad", "Política de Privacidad"],
      headingRegex: /Politica de Privacidad|Política de Privacidad/i,
      screenshotName: "politica_de_privacidad",
      reportUrlKey: "politicaDePrivacidad",
    });
  } finally {
    if (context) {
      await context.close();
    }
    if (browser) {
      await browser.close();
    }

    report.finishedAt = new Date().toISOString();
    const hasFailedStep = Object.values(report.status).some((value) => value !== "PASS");
    report.overall = hasFailedStep ? "FAIL" : "PASS";

    const reportPath = path.join(artifactsRoot, `${TEST_NAME}_report.json`);
    await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

    process.stdout.write(`\nReport: ${reportPath}\n`);
    process.stdout.write(`${JSON.stringify(report.status, null, 2)}\n`);
    process.stdout.write(`Overall: ${report.overall}\n`);

    if (hasFailedStep) {
      process.exitCode = 1;
    }
  }
}

main().catch((error) => {
  process.stderr.write(`Unhandled error in ${TEST_NAME}: ${String(error)}\n`);
  process.exitCode = 1;
});
