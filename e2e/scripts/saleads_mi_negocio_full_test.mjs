import { chromium } from "playwright";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const TEST_NAME = "saleads_mi_negocio_full_test";
const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad",
];

const LOGIN_URL =
  process.env.SALEADS_LOGIN_URL?.trim() ||
  process.env.saleads_login_url?.trim() ||
  "";
const HEADLESS = process.env.HEADLESS !== "false";
const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(scriptDir, "..");

const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.join(projectRoot, "artifacts", `${TEST_NAME}_${timestamp}`);

const report = {
  name: TEST_NAME,
  generatedAt: new Date().toISOString(),
  environment: {
    saleadsLoginUrl: LOGIN_URL || null,
    headless: HEADLESS,
  },
  results: Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        status: "FAIL",
        details: [],
        evidence: {
          screenshots: [],
          url: null,
          newTabOpened: null,
        },
      },
    ]),
  ),
};

function addDetail(field, detail) {
  report.results[field].details.push(detail);
}

function markPass(field, detail) {
  report.results[field].status = "PASS";
  if (detail) {
    addDetail(field, detail);
  }
}

function markFail(field, detail) {
  report.results[field].status = "FAIL";
  if (detail) {
    addDetail(field, detail);
  }
}

function markPrerequisiteFailure(field, prerequisiteField) {
  markFail(
    field,
    `Prerequisite failed: '${prerequisiteField}' did not pass, so this validation could not continue.`,
  );
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function findFirstVisible(page, patterns, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const pattern of patterns) {
      const locators = [
        page.getByRole("button", { name: pattern }).first(),
        page.getByRole("link", { name: pattern }).first(),
        page.getByText(pattern, { exact: false }).first(),
      ];

      for (const locator of locators) {
        if (await locator.isVisible().catch(() => false)) {
          return locator;
        }
      }
    }
    await page.waitForTimeout(300);
  }

  throw new Error(`Unable to find visible element for patterns: ${patterns.join(", ")}`);
}

async function assertVisibleText(pageOrLocator, textPattern, message) {
  const locator = pageOrLocator.getByText(textPattern, { exact: false }).first();
  await locator.waitFor({ state: "visible", timeout: 15000 });
  return message;
}

async function captureScreenshot(page, field, fileName, fullPage = false) {
  const screenshotPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  report.results[field].evidence.screenshots.push(screenshotPath);
}

async function pickGoogleAccountIfShown(candidatePage) {
  const accountLocator = candidatePage.getByText(ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountLocator.isVisible().catch(() => false)) {
    await accountLocator.click();
    await waitForUiLoad(candidatePage);
    return true;
  }

  return false;
}

async function findAppPage(context, fallbackPage, timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const pages = [...context.pages()].reverse();
    for (const page of pages) {
      const hasSidebar =
        (await page
          .locator("aside, nav")
          .filter({ hasText: /Negocio|Mi Negocio|Dashboard/i })
          .first()
          .isVisible()
          .catch(() => false)) ||
        (await page.getByText(/Mi Negocio|Negocio/i).first().isVisible().catch(() => false));

      if (hasSidebar) {
        return page;
      }
    }
    await waitForUiLoad(fallbackPage);
  }

  throw new Error("Main application interface with sidebar was not detected after login.");
}

async function openLegalLinkAndValidate({
  appPage,
  context,
  linkPattern,
  headingPattern,
  field,
  screenshotName,
  returnUrl,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const link = await findFirstVisible(appPage, [linkPattern]);
  await link.click();
  await waitForUiLoad(appPage);

  const popup = await popupPromise;
  const legalPage = popup || appPage;
  await waitForUiLoad(legalPage);

  report.results[field].evidence.newTabOpened = Boolean(popup);
  await assertVisibleText(legalPage, headingPattern, "Heading is visible.");

  const bodyText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (bodyText.length < 120) {
    throw new Error("Legal page content appears too short; expected substantive legal text.");
  }

  report.results[field].evidence.url = legalPage.url();
  await captureScreenshot(legalPage, field, screenshotName, true);

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else if (returnUrl && legalPage.url() !== returnUrl) {
    await legalPage
      .goBack({ waitUntil: "domcontentloaded", timeout: 10000 })
      .catch(async () => {
        await legalPage.goto(returnUrl, { waitUntil: "domcontentloaded", timeout: 30000 });
      });
    await waitForUiLoad(legalPage);
  }
}

async function run() {
  await mkdir(artifactsDir, { recursive: true });

  if (!LOGIN_URL) {
    markFail("Login", "Missing required environment variable SALEADS_LOGIN_URL.");
    markPrerequisiteFailure("Mi Negocio menu", "Login");
    markPrerequisiteFailure("Agregar Negocio modal", "Mi Negocio menu");
    markPrerequisiteFailure("Administrar Negocios view", "Agregar Negocio modal");
    markPrerequisiteFailure("Informaci\u00f3n General", "Administrar Negocios view");
    markPrerequisiteFailure("Detalles de la Cuenta", "Informaci\u00f3n General");
    markPrerequisiteFailure("Tus Negocios", "Detalles de la Cuenta");
    markPrerequisiteFailure("T\u00e9rminos y Condiciones", "Tus Negocios");
    markPrerequisiteFailure("Pol\u00edtica de Privacidad", "T\u00e9rminos y Condiciones");
    return;
  }

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await context.newPage();
  let appPage = page;
  let adminPageUrl = null;

  try {
    // Step 1: Login with Google.
    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded", timeout: 45000 });
    await waitForUiLoad(page);

    const loginButton = await findFirstVisible(page, [
      /Sign in with Google/i,
      /Iniciar sesi[o\u00f3]n con Google/i,
      /Continuar con Google/i,
      /Google/i,
      /Iniciar sesi[o\u00f3]n/i,
      /Login/i,
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await loginButton.click();
    await waitForUiLoad(page);
    const popup = await popupPromise;

    if (popup) {
      await waitForUiLoad(popup);
      await pickGoogleAccountIfShown(popup);
    } else if (page.url().includes("accounts.google.com")) {
      await pickGoogleAccountIfShown(page);
    }

    appPage = await findAppPage(context, page);
    await waitForUiLoad(appPage);
    await assertVisibleText(appPage, /Negocio|Mi Negocio/i, "Sidebar is visible.");
    await captureScreenshot(appPage, "Login", "01_dashboard_loaded.png", true);
    markPass("Login", "Dashboard loaded and left sidebar navigation is visible.");

    // Step 2: Open Mi Negocio menu.
    const negocioEntry = await findFirstVisible(appPage, [/Negocio/i]);
    await negocioEntry.click();
    await waitForUiLoad(appPage);

    const miNegocioEntry = await findFirstVisible(appPage, [/Mi Negocio/i]);
    await miNegocioEntry.click();
    await waitForUiLoad(appPage);

    await assertVisibleText(appPage, /Agregar Negocio/i, "Agregar Negocio is visible.");
    await assertVisibleText(
      appPage,
      /Administrar Negocios/i,
      "Administrar Negocios is visible.",
    );
    await captureScreenshot(appPage, "Mi Negocio menu", "02_mi_negocio_expanded.png");
    markPass("Mi Negocio menu", "Mi Negocio submenu expanded with expected options.");

    // Step 3: Validate Agregar Negocio modal.
    const agregarNegocio = await findFirstVisible(appPage, [/Agregar Negocio/i]);
    await agregarNegocio.click();
    await waitForUiLoad(appPage);

    await assertVisibleText(appPage, /Crear Nuevo Negocio/i, "Modal title is visible.");
    const nombreInput = appPage
      .locator("input[placeholder*='Nombre'], input[name*='nombre'], input")
      .first();
    await nombreInput.waitFor({ state: "visible", timeout: 10000 });
    await assertVisibleText(appPage, /Tienes 2 de 3 negocios/i, "Capacity text is visible.");
    await assertVisibleText(appPage, /Cancelar/i, "Cancelar button is present.");
    await assertVisibleText(appPage, /Crear Negocio/i, "Crear Negocio button is present.");
    await captureScreenshot(appPage, "Agregar Negocio modal", "03_agregar_negocio_modal.png");

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatizacion");
    const cancelarButton = await findFirstVisible(appPage, [/Cancelar/i]);
    await cancelarButton.click();
    await waitForUiLoad(appPage);
    markPass("Agregar Negocio modal", "Agregar Negocio modal validated and closed.");

    // Step 4: Open Administrar Negocios.
    if (!(await appPage.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      const reopenMiNegocio = await findFirstVisible(appPage, [/Mi Negocio/i]);
      await reopenMiNegocio.click();
      await waitForUiLoad(appPage);
    }

    const administrarNegocios = await findFirstVisible(appPage, [/Administrar Negocios/i]);
    await administrarNegocios.click();
    await waitForUiLoad(appPage);

    await assertVisibleText(appPage, /Informaci[o\u00f3]n General/i, "Informacion General visible.");
    await assertVisibleText(appPage, /Detalles de la Cuenta/i, "Detalles de la Cuenta visible.");
    await assertVisibleText(appPage, /Tus Negocios/i, "Tus Negocios visible.");
    await assertVisibleText(appPage, /Secci[o\u00f3]n Legal/i, "Seccion Legal visible.");
    await captureScreenshot(
      appPage,
      "Administrar Negocios view",
      "04_administrar_negocios_full.png",
      true,
    );
    adminPageUrl = appPage.url();
    markPass("Administrar Negocios view", "Administrar Negocios sections are visible.");

    // Step 5: Validate Informacion General.
    const infoSection = appPage
      .locator("section, div")
      .filter({ hasText: /Informaci[o\u00f3]n General/i })
      .first();
    await infoSection.waitFor({ state: "visible", timeout: 15000 });
    const infoText = (await infoSection.innerText()).replace(/\s+/g, " ").trim();

    if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoText)) {
      throw new Error("User email was not detected in Informacion General section.");
    }
    if (!/BUSINESS PLAN/i.test(infoText)) {
      throw new Error("BUSINESS PLAN text was not detected.");
    }
    await assertVisibleText(infoSection, /Cambiar Plan/i, "Cambiar Plan button is visible.");

    const possibleNameLines = infoText
      .split(" ")
      .join(" ")
      .split(/[|]/)
      .filter((line) => line.trim().length > 3);
    if (!possibleNameLines.length) {
      throw new Error("User name could not be inferred from Informacion General section.");
    }
    markPass("Informaci\u00f3n General", "Informacion General validations succeeded.");

    // Step 6: Validate Detalles de la Cuenta.
    const detailsSection = appPage
      .locator("section, div")
      .filter({ hasText: /Detalles de la Cuenta/i })
      .first();
    await detailsSection.waitFor({ state: "visible", timeout: 15000 });
    await assertVisibleText(detailsSection, /Cuenta creada/i, "Cuenta creada is visible.");
    await assertVisibleText(detailsSection, /Estado activo/i, "Estado activo is visible.");
    await assertVisibleText(
      detailsSection,
      /Idioma seleccionado/i,
      "Idioma seleccionado is visible.",
    );
    markPass("Detalles de la Cuenta", "Detalles de la Cuenta validations succeeded.");

    // Step 7: Validate Tus Negocios.
    const negociosSection = appPage
      .locator("section, div")
      .filter({ hasText: /Tus Negocios/i })
      .first();
    await negociosSection.waitFor({ state: "visible", timeout: 15000 });
    await assertVisibleText(negociosSection, /Agregar Negocio/i, "Agregar Negocio button exists.");
    await assertVisibleText(
      negociosSection,
      /Tienes 2 de 3 negocios/i,
      "Capacity text is visible in Tus Negocios.",
    );

    const businessRows = negociosSection.locator("li, tr, [role='row'], .business-item");
    if ((await businessRows.count()) < 1) {
      throw new Error("Business list appears empty or not visible in Tus Negocios section.");
    }
    markPass("Tus Negocios", "Tus Negocios validations succeeded.");

    // Step 8: Validate Terminos y Condiciones.
    await openLegalLinkAndValidate({
      appPage,
      context,
      linkPattern: /T[e\u00e9]rminos y Condiciones/i,
      headingPattern: /T[e\u00e9]rminos y Condiciones/i,
      field: "T\u00e9rminos y Condiciones",
      screenshotName: "08_terminos_y_condiciones.png",
      returnUrl: adminPageUrl,
    });
    markPass(
      "T\u00e9rminos y Condiciones",
      "Legal page validated and returned to the application tab.",
    );

    // Step 9: Validate Politica de Privacidad.
    await openLegalLinkAndValidate({
      appPage,
      context,
      linkPattern: /Pol[i\u00ed]tica de Privacidad/i,
      headingPattern: /Pol[i\u00ed]tica de Privacidad/i,
      field: "Pol\u00edtica de Privacidad",
      screenshotName: "09_politica_de_privacidad.png",
      returnUrl: adminPageUrl,
    });
    markPass("Pol\u00edtica de Privacidad", "Privacy policy page validated and returned.");
  } catch (error) {
    const failedField = REPORT_FIELDS.find((field) => report.results[field].status === "FAIL");
    const targetField = failedField || "Login";
    markFail(targetField, error instanceof Error ? error.message : String(error));

    const fieldIndex = REPORT_FIELDS.indexOf(targetField);
    for (let i = fieldIndex + 1; i < REPORT_FIELDS.length; i += 1) {
      if (!report.results[REPORT_FIELDS[i]].details.length) {
        markPrerequisiteFailure(REPORT_FIELDS[i], targetField);
      }
    }
  } finally {
    await context.close();
    await browser.close();
  }
}

async function writeReportAndExit() {
  const reportPath = path.join(artifactsDir, "final-report.json");
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  console.log(`Report written to: ${reportPath}`);
  for (const field of REPORT_FIELDS) {
    console.log(`${field}: ${report.results[field].status}`);
  }

  if (REPORT_FIELDS.some((field) => report.results[field].status !== "PASS")) {
    process.exitCode = 1;
  }
}

await run().catch((error) => {
  markFail("Login", `Unhandled runtime error: ${error instanceof Error ? error.message : error}`);
});
await writeReportAndExit();
