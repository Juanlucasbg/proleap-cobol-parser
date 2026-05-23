import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const TEST_NAME = "saleads_mi_negocio_full_test";
const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS = [
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

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const runId = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.join(__dirname, "artifacts", runId);
const headless = !["false", "0", "no"].includes((process.env.HEADLESS ?? "true").toLowerCase());
const loginUrl =
  process.env.SALEADS_LOGIN_URL ??
  process.env.SALEADS_BASE_URL ??
  process.env.BASE_URL ??
  null;

const report = {
  name: TEST_NAME,
  generatedAt: new Date().toISOString(),
  environment: {
    loginUrl,
    headless,
  },
  steps: Object.fromEntries(REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed.", evidence: [] }])),
  urls: {
    termsAndConditions: null,
    privacyPolicy: null,
  },
  overallStatus: "FAIL",
};

async function ensureArtifactsDir() {
  await fs.mkdir(artifactsDir, { recursive: true });
}

function textPattern(text) {
  return new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

async function isVisible(locator, timeout = 3000) {
  try {
    return await locator.first().isVisible({ timeout });
  } catch {
    return false;
  }
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function clickByVisibleText(page, text) {
  const pattern = textPattern(text);
  const candidates = [
    page.getByRole("button", { name: pattern }).first(),
    page.getByRole("link", { name: pattern }).first(),
    page.getByRole("menuitem", { name: pattern }).first(),
    page.getByRole("tab", { name: pattern }).first(),
    page.getByText(pattern).first(),
  ];

  for (const locator of candidates) {
    if (await isVisible(locator, 1200)) {
      await locator.click();
      await waitForUiLoad(page);
      return;
    }
  }

  throw new Error(`Unable to find clickable element with visible text: "${text}".`);
}

async function anyTextVisible(page, texts) {
  for (const text of texts) {
    if (await isVisible(page.getByText(textPattern(text)).first())) {
      return true;
    }
  }
  return false;
}

function markStep(field, status, details, evidence = []) {
  report.steps[field] = { status, details, evidence };
}

function failFollowingSteps(fromIndex, reason) {
  for (let i = fromIndex; i < REPORT_FIELDS.length; i += 1) {
    const field = REPORT_FIELDS[i];
    if (report.steps[field]?.status === "FAIL" && report.steps[field]?.details === "Not executed.") {
      markStep(field, "FAIL", `Prerequisite failed: ${reason}`);
    }
  }
}

async function captureScreenshot(page, filename, fullPage = false) {
  const screenshotPath = path.join(artifactsDir, filename);
  await page.screenshot({ path: screenshotPath, fullPage });
  return path.relative(__dirname, screenshotPath);
}

async function writeReportAndExit() {
  report.overallStatus = Object.values(report.steps).every((step) => step.status === "PASS") ? "PASS" : "FAIL";
  const reportPath = path.join(artifactsDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  const summary = REPORT_FIELDS.map((field) => `${field}: ${report.steps[field].status}`).join("\n");
  process.stdout.write(`\n${TEST_NAME} report\n${summary}\nReport: ${reportPath}\n`);
  process.exit(report.overallStatus === "PASS" ? 0 : 1);
}

async function pickGoogleAccount(context) {
  for (const candidatePage of context.pages()) {
    if (await isVisible(candidatePage.getByText(textPattern(ACCOUNT_EMAIL)).first(), 1500)) {
      await candidatePage.getByText(textPattern(ACCOUNT_EMAIL)).first().click();
      await waitForUiLoad(candidatePage);
      return true;
    }
  }
  return false;
}

async function validateLegalPage(context, appPage, linkText, expectedHeading, reportField, screenshotName) {
  const knownPages = new Set(context.pages());
  const originalUrl = appPage.url();

  await clickByVisibleText(appPage, linkText);

  let targetPage = appPage;
  let openedNewTab = false;
  for (let i = 0; i < 10; i += 1) {
    const newPage = context.pages().find((pageCandidate) => !knownPages.has(pageCandidate));
    if (newPage) {
      targetPage = newPage;
      openedNewTab = true;
      break;
    }
    await appPage.waitForTimeout(300);
  }

  if (openedNewTab) {
    await waitForUiLoad(targetPage);
  }

  const headingVisible = await isVisible(targetPage.getByRole("heading", { name: textPattern(expectedHeading) }).first(), 4000)
    || await isVisible(targetPage.getByText(textPattern(expectedHeading)).first(), 4000);
  const bodyText = (await targetPage.locator("body").innerText().catch(() => "")).toLowerCase();
  const legalTextVisible =
    bodyText.includes("términos") ||
    bodyText.includes("terminos") ||
    bodyText.includes("privacidad") ||
    bodyText.includes("condiciones");

  const screenshot = await captureScreenshot(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();
  const status = headingVisible && legalTextVisible ? "PASS" : "FAIL";
  const details = status === "PASS"
    ? `Validated legal page "${expectedHeading}" at ${finalUrl}. ${openedNewTab ? "Opened in new tab." : "Navigated in same tab."}`
    : `Could not validate legal page "${expectedHeading}" at ${finalUrl}.`;

  markStep(reportField, status, details, [screenshot]);

  if (reportField === "Términos y Condiciones") {
    report.urls.termsAndConditions = finalUrl;
  } else if (reportField === "Política de Privacidad") {
    report.urls.privacyPolicy = finalUrl;
  }

  if (openedNewTab) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else if (appPage.url() !== originalUrl) {
    await appPage.goBack().catch(() => {});
    await waitForUiLoad(appPage);
  }

  if (status === "FAIL") {
    throw new Error(details);
  }
}

async function run() {
  await ensureArtifactsDir();

  let browser;
  let context;
  let page;

  try {
    browser = await chromium.launch({ headless });
    context = await browser.newContext();
    page = await context.newPage();

    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    } else if (page.url().startsWith("about:blank")) {
      throw new Error(
        "No login page available. Provide SALEADS_LOGIN_URL/SALEADS_BASE_URL/BASE_URL when running headless automation.",
      );
    }

    // Step 1: Login with Google
    await clickByVisibleText(page, "Google").catch(async () => {
      await clickByVisibleText(page, "Sign in with Google");
    });
    await pickGoogleAccount(context);
    await waitForUiLoad(page);

    const mainInterfaceVisible =
      (await isVisible(page.locator("aside"), 5000)) ||
      (await anyTextVisible(page, ["Negocio", "Mi Negocio", "Dashboard"]));

    if (!mainInterfaceVisible) {
      throw new Error("Main interface or left sidebar was not visible after login.");
    }

    const dashboardScreenshot = await captureScreenshot(page, "step-1-dashboard.png", true);
    markStep("Login", "PASS", "Dashboard loaded and sidebar is visible after Google sign-in.", [dashboardScreenshot]);

    // Step 2: Open Mi Negocio menu
    if (await anyTextVisible(page, ["Negocio"])) {
      await clickByVisibleText(page, "Negocio");
    }
    await clickByVisibleText(page, "Mi Negocio");

    const addBusinessVisible = await anyTextVisible(page, ["Agregar Negocio"]);
    const manageBusinessVisible = await anyTextVisible(page, ["Administrar Negocios"]);
    if (!addBusinessVisible || !manageBusinessVisible) {
      throw new Error("Mi Negocio submenu did not expose expected options.");
    }
    const menuScreenshot = await captureScreenshot(page, "step-2-mi-negocio-expanded.png", true);
    markStep(
      "Mi Negocio menu",
      "PASS",
      "Mi Negocio submenu expanded with 'Agregar Negocio' and 'Administrar Negocios'.",
      [menuScreenshot],
    );

    // Step 3: Validate Agregar Negocio modal
    await clickByVisibleText(page, "Agregar Negocio");

    const modalChecks = await Promise.all([
      anyTextVisible(page, ["Crear Nuevo Negocio"]),
      anyTextVisible(page, ["Nombre del Negocio"]),
      anyTextVisible(page, ["Tienes 2 de 3 negocios"]),
      anyTextVisible(page, ["Cancelar"]),
      anyTextVisible(page, ["Crear Negocio"]),
    ]);

    if (modalChecks.some((isPresent) => !isPresent)) {
      throw new Error("Agregar Negocio modal is missing one or more required elements.");
    }

    const modalScreenshot = await captureScreenshot(page, "step-3-crear-negocio-modal.png", true);

    if (await anyTextVisible(page, ["Nombre del Negocio"])) {
      await page
        .getByLabel(textPattern("Nombre del Negocio"))
        .first()
        .fill("Negocio Prueba Automatización")
        .catch(async () => {
          await page
            .getByPlaceholder(textPattern("Nombre del Negocio"))
            .first()
            .fill("Negocio Prueba Automatización")
            .catch(() => {});
        });
    }
    await clickByVisibleText(page, "Cancelar");

    markStep(
      "Agregar Negocio modal",
      "PASS",
      "Crear Nuevo Negocio modal validated with required fields and actions.",
      [modalScreenshot],
    );

    // Step 4: Open Administrar Negocios
    if (!(await anyTextVisible(page, ["Administrar Negocios"]))) {
      await clickByVisibleText(page, "Mi Negocio");
    }
    await clickByVisibleText(page, "Administrar Negocios");

    const adminChecks = await Promise.all([
      anyTextVisible(page, ["Información General"]),
      anyTextVisible(page, ["Detalles de la Cuenta"]),
      anyTextVisible(page, ["Tus Negocios"]),
      anyTextVisible(page, ["Sección Legal"]),
    ]);

    if (adminChecks.some((isPresent) => !isPresent)) {
      throw new Error("Administrar Negocios view is missing one or more required sections.");
    }

    const accountScreenshot = await captureScreenshot(page, "step-4-administrar-negocios.png", true);
    markStep(
      "Administrar Negocios view",
      "PASS",
      "Administrar Negocios page loaded with all expected sections.",
      [accountScreenshot],
    );

    // Step 5: Validate Información General
    const infoGeneralChecks = await Promise.all([
      anyTextVisible(page, ["Juan", "Nombre", "Usuario"]),
      anyTextVisible(page, [ACCOUNT_EMAIL, "@"]),
      anyTextVisible(page, ["BUSINESS PLAN"]),
      anyTextVisible(page, ["Cambiar Plan"]),
    ]);

    if (infoGeneralChecks.some((isPresent) => !isPresent)) {
      throw new Error("Información General section failed one or more validations.");
    }
    markStep(
      "Información General",
      "PASS",
      "Información General includes user info, BUSINESS PLAN and Cambiar Plan.",
    );

    // Step 6: Validate Detalles de la Cuenta
    const accountDetailsChecks = await Promise.all([
      anyTextVisible(page, ["Cuenta creada"]),
      anyTextVisible(page, ["Estado activo"]),
      anyTextVisible(page, ["Idioma seleccionado"]),
    ]);
    if (accountDetailsChecks.some((isPresent) => !isPresent)) {
      throw new Error("Detalles de la Cuenta section failed one or more validations.");
    }
    markStep("Detalles de la Cuenta", "PASS", "Detalles de la Cuenta labels are visible.");

    // Step 7: Validate Tus Negocios
    const businessesChecks = await Promise.all([
      anyTextVisible(page, ["Tus Negocios"]),
      anyTextVisible(page, ["Agregar Negocio"]),
      anyTextVisible(page, ["Tienes 2 de 3 negocios"]),
    ]);
    if (businessesChecks.some((isPresent) => !isPresent)) {
      throw new Error("Tus Negocios section failed one or more validations.");
    }
    markStep("Tus Negocios", "PASS", "Tus Negocios content and counters are visible.");

    // Step 8: Validate Términos y Condiciones
    await validateLegalPage(
      context,
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "Términos y Condiciones",
      "step-8-terminos-y-condiciones.png",
    );

    // Step 9: Validate Política de Privacidad
    await validateLegalPage(
      context,
      page,
      "Política de Privacidad",
      "Política de Privacidad",
      "Política de Privacidad",
      "step-9-politica-de-privacidad.png",
    );
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);

    // Mark the first still-pending step as the direct failure.
    const firstPendingIndex = REPORT_FIELDS.findIndex((field) => report.steps[field].details === "Not executed.");
    if (firstPendingIndex >= 0) {
      markStep(REPORT_FIELDS[firstPendingIndex], "FAIL", reason);
      failFollowingSteps(firstPendingIndex + 1, reason);
    }
  } finally {
    await context?.close().catch(() => {});
    await browser?.close().catch(() => {});
  }

  await writeReportAndExit();
}

run().catch(async (error) => {
  const reason = error instanceof Error ? error.message : String(error);
  const firstPendingIndex = REPORT_FIELDS.findIndex((field) => report.steps[field].details === "Not executed.");
  if (firstPendingIndex >= 0) {
    markStep(REPORT_FIELDS[firstPendingIndex], "FAIL", reason);
    failFollowingSteps(firstPendingIndex + 1, reason);
  }
  await writeReportAndExit();
});
