const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const WORKFLOW_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

const STARTED_AT = new Date();
const RUN_ID = STARTED_AT.toISOString().replace(/[:.]/g, "-");
const ARTIFACTS_DIR = path.join(__dirname, "artifacts", RUN_ID);
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "report.json");

const LOGIN_URL = process.env.SALEADS_LOGIN_URL || "";
const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || DEFAULT_GOOGLE_ACCOUNT;
const HEADLESS = process.env.HEADLESS !== "false";
const ACTION_TIMEOUT_MS = Number(process.env.ACTION_TIMEOUT_MS || 30000);

function ensureArtifactsDirectory() {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: ACTION_TIMEOUT_MS });
  await page
    .waitForLoadState("networkidle", { timeout: 12000 })
    .catch(() => undefined);
  await sleep(500);
}

async function clickAndWait(locator, page) {
  await locator.waitFor({ state: "visible", timeout: ACTION_TIMEOUT_MS });
  await locator.click();
  await waitForUi(page);
}

async function getFirstVisibleLocator(candidates, timeoutMs = ACTION_TIMEOUT_MS) {
  const endAt = Date.now() + timeoutMs;

  while (Date.now() < endAt) {
    for (const locator of candidates) {
      const count = await locator.count().catch(() => 0);
      if (count < 1) {
        continue;
      }

      const isVisible = await locator.first().isVisible().catch(() => false);
      if (isVisible) {
        return locator.first();
      }
    }

    await sleep(200);
  }

  throw new Error("No visible locator found among provided candidates.");
}

function baseStepResult() {
  return {
    status: "FAIL",
    checks: [],
    evidence: [],
    errors: [],
  };
}

function addCheck(stepResult, name, passed, details = "") {
  stepResult.checks.push({ name, passed, details });
}

async function validateVisibleText(page, textRegex, checkName, stepResult) {
  const locator = page.getByText(textRegex).first();
  try {
    await locator.waitFor({ state: "visible", timeout: ACTION_TIMEOUT_MS });
    addCheck(stepResult, checkName, true);
    return true;
  } catch (error) {
    addCheck(stepResult, checkName, false, error.message);
    return false;
  }
}

async function validateVisibleLocator(locator, checkName, stepResult) {
  try {
    await locator.waitFor({ state: "visible", timeout: ACTION_TIMEOUT_MS });
    addCheck(stepResult, checkName, true);
    return true;
  } catch (error) {
    addCheck(stepResult, checkName, false, error.message);
    return false;
  }
}

async function saveScreenshot(page, name, stepResult, options = {}) {
  const screenshotPath = path.join(SCREENSHOTS_DIR, `${name}.png`);
  await page.screenshot({
    path: screenshotPath,
    fullPage: options.fullPage ?? false,
  });
  stepResult.evidence.push({ type: "screenshot", path: screenshotPath });
}

function finalizeStep(stepResult) {
  const hasFailedChecks = stepResult.checks.some((check) => !check.passed);
  const hasErrors = stepResult.errors.length > 0;
  stepResult.status = !hasFailedChecks && !hasErrors ? "PASS" : "FAIL";
}

async function runStep(stepName, fn, report) {
  const stepResult = baseStepResult();
  report.steps[stepName] = stepResult;

  try {
    await fn(stepResult);
  } catch (error) {
    stepResult.errors.push(error.message);
  }

  finalizeStep(stepResult);
}

async function selectGoogleAccountIfShown(page, email, stepResult, popupPromise = null) {
  const popup = popupPromise ? await popupPromise : null;
  const authPage = popup || page;

  await authPage.waitForLoadState("domcontentloaded", {
    timeout: ACTION_TIMEOUT_MS,
  });

  const accountCandidate = await getFirstVisibleLocator(
    [
      authPage.getByRole("button", { name: new RegExp(email, "i") }),
      authPage.getByText(new RegExp(email.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
      authPage.locator(`text=${email}`),
    ],
    7000
  ).catch(() => null);

  if (accountCandidate) {
    await accountCandidate.click();
    await waitForUi(authPage);
    addCheck(stepResult, `Google account selected (${email})`, true);
  } else {
    addCheck(
      stepResult,
      "Google account selector handled",
      true,
      "Account selector did not appear; continuing."
    );
  }

  if (popup && !popup.isClosed()) {
    await popup.waitForEvent("close", { timeout: 15000 }).catch(() => undefined);
    await page.bringToFront().catch(() => undefined);
  }
}

async function openLegalLinkAndValidate({
  page,
  linkText,
  headingRegex,
  screenshotName,
  stepResult,
  appReturnUrl,
}) {
  const legalLink = await getFirstVisibleLocator([
    page.getByRole("link", { name: new RegExp(linkText, "i") }),
    page.getByRole("button", { name: new RegExp(linkText, "i") }),
    page.getByText(new RegExp(linkText, "i")),
  ]);

  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await legalLink.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const targetPage = popup || page;
  await waitForUi(targetPage);

  await validateVisibleText(
    targetPage,
    headingRegex,
    `Heading visible: ${headingRegex}`,
    stepResult
  );

  const legalTextVisible = await targetPage
    .locator("body")
    .innerText()
    .then((text) => text.trim().length > 120)
    .catch(() => false);

  addCheck(stepResult, "Legal content text is visible", legalTextVisible);

  await saveScreenshot(targetPage, screenshotName, stepResult, { fullPage: true });
  stepResult.evidence.push({ type: "url", value: targetPage.url() });

  if (popup) {
    await popup.close().catch(() => undefined);
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  const backSucceeded = await page
    .goBack({ waitUntil: "domcontentloaded", timeout: 12000 })
    .then(() => true)
    .catch(() => false);

  if (!backSucceeded && appReturnUrl) {
    await page.goto(appReturnUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }
}

async function runWorkflow() {
  ensureArtifactsDirectory();

  const report = {
    name: WORKFLOW_NAME,
    startedAt: STARTED_AT.toISOString(),
    config: {
      loginUrlProvided: Boolean(LOGIN_URL),
      loginUrl: LOGIN_URL || null,
      googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
      headless: HEADLESS,
    },
    steps: {},
    summary: {},
  };

  const browser = await chromium.launch({
    headless: HEADLESS,
  });
  const context = await browser.newContext();
  const page = await context.newPage();
  page.setDefaultTimeout(ACTION_TIMEOUT_MS);

  let appReturnUrl = "";

  try {
    if (LOGIN_URL) {
      await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    await runStep("Login", async (stepResult) => {
      const loginButton = await getFirstVisibleLocator(
        [
          page.getByRole("button", { name: /sign in with google|google/i }),
          page.getByRole("link", { name: /sign in with google|google/i }),
          page.getByText(/sign in with google|google/i),
        ],
        20000
      );

      const googlePopupPromise = context
        .waitForEvent("page", { timeout: 8000 })
        .catch(() => null);
      await clickAndWait(loginButton, page);
      await selectGoogleAccountIfShown(
        page,
        GOOGLE_ACCOUNT_EMAIL,
        stepResult,
        googlePopupPromise
      );
      await waitForUi(page);

      await validateVisibleLocator(
        page.locator("aside, nav").first(),
        "Left sidebar navigation is visible",
        stepResult
      );

      const mainInterfaceVisible = await page
        .locator("main, [role='main'], .main-content")
        .first()
        .isVisible()
        .catch(() => false);
      addCheck(stepResult, "Main application interface appears", mainInterfaceVisible);

      await saveScreenshot(page, "01-dashboard-loaded", stepResult);
    }, report);

    await runStep("Mi Negocio menu", async (stepResult) => {
      const negocioSection = await getFirstVisibleLocator([
        page.getByRole("button", { name: /negocio/i }),
        page.getByText(/^Negocio$/i),
        page.getByText(/negocio/i),
      ]);
      await clickAndWait(negocioSection, page);

      const miNegocio = await getFirstVisibleLocator([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);
      await clickAndWait(miNegocio, page);

      await validateVisibleText(
        page,
        /agregar negocio/i,
        "Submenu item 'Agregar Negocio' visible",
        stepResult
      );
      await validateVisibleText(
        page,
        /administrar negocios/i,
        "Submenu item 'Administrar Negocios' visible",
        stepResult
      );
      addCheck(stepResult, "Mi Negocio submenu expanded", true);

      await saveScreenshot(page, "02-mi-negocio-expanded", stepResult);
    }, report);

    await runStep("Agregar Negocio modal", async (stepResult) => {
      const agregarNegocio = await getFirstVisibleLocator([
        page.getByRole("button", { name: /^agregar negocio$/i }),
        page.getByRole("link", { name: /^agregar negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);
      await clickAndWait(agregarNegocio, page);

      const modalTitle = page.getByText(/crear nuevo negocio/i).first();
      await validateVisibleLocator(
        modalTitle,
        "Modal title 'Crear Nuevo Negocio' visible",
        stepResult
      );

      const businessNameField = await getFirstVisibleLocator(
        [
          page.getByLabel(/nombre del negocio/i),
          page.getByPlaceholder(/nombre del negocio/i),
          page.locator("input").filter({ hasText: /nombre del negocio/i }),
        ],
        10000
      ).catch(() => null);

      if (businessNameField) {
        addCheck(stepResult, "Input field 'Nombre del Negocio' exists", true);
        await businessNameField.click();
        await businessNameField.fill("Negocio Prueba Automatización");
      } else {
        addCheck(stepResult, "Input field 'Nombre del Negocio' exists", false);
      }

      await validateVisibleText(
        page,
        /tienes\s*2\s*de\s*3\s*negocios/i,
        "Text 'Tienes 2 de 3 negocios' visible",
        stepResult
      );
      await validateVisibleText(
        page,
        /^Cancelar$/i,
        "Button 'Cancelar' present",
        stepResult
      );
      await validateVisibleText(
        page,
        /crear negocio/i,
        "Button 'Crear Negocio' present",
        stepResult
      );

      await saveScreenshot(page, "03-agregar-negocio-modal", stepResult);

      const cancelarButton = await getFirstVisibleLocator([
        page.getByRole("button", { name: /^cancelar$/i }),
        page.getByText(/^Cancelar$/i),
      ]);
      await clickAndWait(cancelarButton, page);
    }, report);

    await runStep("Administrar Negocios view", async (stepResult) => {
      const administrarNegocios = await getFirstVisibleLocator([
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ]);
      await clickAndWait(administrarNegocios, page);
      await waitForUi(page);

      appReturnUrl = page.url();

      await validateVisibleText(
        page,
        /informaci[oó]n general/i,
        "Section 'Información General' exists",
        stepResult
      );
      await validateVisibleText(
        page,
        /detalles de la cuenta/i,
        "Section 'Detalles de la Cuenta' exists",
        stepResult
      );
      await validateVisibleText(
        page,
        /tus negocios/i,
        "Section 'Tus Negocios' exists",
        stepResult
      );
      await validateVisibleText(
        page,
        /secci[oó]n legal/i,
        "Section 'Sección Legal' exists",
        stepResult
      );

      await saveScreenshot(page, "04-administrar-negocios", stepResult, {
        fullPage: true,
      });
    }, report);

    await runStep("Información General", async (stepResult) => {
      const bodyText = await page.locator("body").innerText();
      const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
      const hasEmail = emailRegex.test(bodyText);
      addCheck(stepResult, "User email is visible", hasEmail);

      const hasUserName = bodyText
        .toLowerCase()
        .includes("juan");
      addCheck(stepResult, "User name is visible", hasUserName);

      await validateVisibleText(
        page,
        /business plan/i,
        "Text 'BUSINESS PLAN' is visible",
        stepResult
      );
      await validateVisibleText(
        page,
        /cambiar plan/i,
        "Button 'Cambiar Plan' is visible",
        stepResult
      );
    }, report);

    await runStep("Detalles de la Cuenta", async (stepResult) => {
      await validateVisibleText(
        page,
        /cuenta creada/i,
        "'Cuenta creada' is visible",
        stepResult
      );
      await validateVisibleText(
        page,
        /estado activo/i,
        "'Estado activo' is visible",
        stepResult
      );
      await validateVisibleText(
        page,
        /idioma seleccionado/i,
        "'Idioma seleccionado' is visible",
        stepResult
      );
    }, report);

    await runStep("Tus Negocios", async (stepResult) => {
      await validateVisibleText(
        page,
        /tus negocios/i,
        "Business list section is visible",
        stepResult
      );
      await validateVisibleText(
        page,
        /^Agregar Negocio$/i,
        "Button 'Agregar Negocio' exists",
        stepResult
      );
      await validateVisibleText(
        page,
        /tienes\s*2\s*de\s*3\s*negocios/i,
        "Text 'Tienes 2 de 3 negocios' is visible",
        stepResult
      );
    }, report);

    await runStep("Términos y Condiciones", async (stepResult) => {
      await openLegalLinkAndValidate({
        page,
        linkText: "Términos y Condiciones",
        headingRegex: /t[eé]rminos y condiciones/i,
        screenshotName: "05-terminos-y-condiciones",
        stepResult,
        appReturnUrl,
      });
    }, report);

    await runStep("Política de Privacidad", async (stepResult) => {
      await openLegalLinkAndValidate({
        page,
        linkText: "Política de Privacidad",
        headingRegex: /pol[ií]tica de privacidad/i,
        screenshotName: "06-politica-de-privacidad",
        stepResult,
        appReturnUrl,
      });
    }, report);
  } finally {
    report.finishedAt = new Date().toISOString();
    report.summary = Object.fromEntries(
      Object.entries(report.steps).map(([name, result]) => [name, result.status])
    );
    report.overallStatus = Object.values(report.summary).every((status) => status === "PASS")
      ? "PASS"
      : "FAIL";

    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");

    console.log(`Workflow: ${WORKFLOW_NAME}`);
    console.log(`Artifacts directory: ${ARTIFACTS_DIR}`);
    console.log(`Report: ${REPORT_PATH}`);
    console.table(report.summary);
    console.log(`Overall status: ${report.overallStatus}`);

    await context.close().catch(() => undefined);
    await browser.close().catch(() => undefined);
  }
}

runWorkflow().catch((error) => {
  console.error("Fatal workflow error:", error);
  process.exitCode = 1;
});
