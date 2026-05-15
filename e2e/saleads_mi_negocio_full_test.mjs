import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL ?? "";
const HEADLESS = process.env.HEADLESS !== "false";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

const timestamp = new Date().toISOString().replaceAll(":", "-");
const artifactsDir = path.resolve("artifacts", `${TEST_NAME}_${timestamp}`);
const reportPath = path.join(artifactsDir, "final-report.json");

const report = {
  name: TEST_NAME,
  goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
  generatedAt: new Date().toISOString(),
  environment: {
    loginUrl: LOGIN_URL || null,
    headless: HEADLESS
  },
  steps: Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        status: "FAIL",
        validations: [],
        details: [],
        evidence: {
          screenshots: [],
          urls: []
        }
      }
    ])
  )
};

function addValidation(stepName, description, passed, detail = "") {
  report.steps[stepName].validations.push({
    description,
    passed,
    detail
  });
}

function setStepStatusFromValidations(stepName) {
  const validations = report.steps[stepName].validations;
  const passed = validations.length > 0 && validations.every((item) => item.passed);
  report.steps[stepName].status = passed ? "PASS" : "FAIL";
}

function sanitizeFileName(value) {
  return value
    .normalize("NFKD")
    .replaceAll(/[\u0300-\u036f]/g, "")
    .replaceAll(/[^a-zA-Z0-9_-]/g, "-")
    .replaceAll(/-+/g, "-")
    .replaceAll(/^-|-$/g, "")
    .toLowerCase();
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1200);
}

async function isVisible(locator, timeout = 8000) {
  try {
    return await locator.first().isVisible({ timeout });
  } catch {
    return false;
  }
}

async function anyVisible(locators, timeout = 8000) {
  for (const locator of locators) {
    if (await isVisible(locator, timeout)) {
      return true;
    }
  }
  return false;
}

async function firstVisible(locators, timeout = 8000) {
  for (const locator of locators) {
    if (await isVisible(locator, timeout)) {
      return locator.first();
    }
  }
  return null;
}

async function saveScreenshot(page, stepName, checkpointName, fullPage = false) {
  const fileName = `${sanitizeFileName(stepName)}_${sanitizeFileName(checkpointName)}.png`;
  const fullPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: fullPath, fullPage });
  report.steps[stepName].evidence.screenshots.push(fullPath);
}

function markRemainingAsPrerequisiteFailed(startAtIndex, reason) {
  for (let i = startAtIndex; i < REPORT_FIELDS.length; i += 1) {
    const stepName = REPORT_FIELDS[i];
    if (report.steps[stepName].validations.length === 0) {
      addValidation(stepName, "Prerequisite satisfied", false, reason);
    }
    report.steps[stepName].details.push(`Prerequisite failed: ${reason}`);
    setStepStatusFromValidations(stepName);
  }
}

async function validateLegalLink({
  stepName,
  originalPage,
  context,
  linkPatterns,
  headingPattern,
  appReturnUrl
}) {
  const link = await firstVisible(
    linkPatterns.map((pattern) => originalPage.getByText(pattern, { exact: false }))
  );

  if (!link) {
    addValidation(stepName, "Legal link is visible", false, "Could not find legal link by text.");
    setStepStatusFromValidations(stepName);
    return;
  }

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await link.click();
  await waitForUi(originalPage);

  const popupPage = await popupPromise;
  const legalPage = popupPage ?? originalPage;
  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded");
    await popupPage.waitForTimeout(1200);
  }

  const headingVisible = await anyVisible(
    [
      legalPage.getByRole("heading", { name: headingPattern }),
      legalPage.getByText(headingPattern)
    ],
    15000
  );
  addValidation(
    stepName,
    "Expected legal heading is visible",
    headingVisible,
    headingVisible ? "" : "Heading not found after opening legal page."
  );

  const legalTextLength = ((await legalPage.locator("body").innerText().catch(() => "")).trim()).length;
  const legalTextVisible = legalTextLength > 180;
  addValidation(
    stepName,
    "Legal content text is visible",
    legalTextVisible,
    legalTextVisible ? "" : `Body text length too short: ${legalTextLength}`
  );

  report.steps[stepName].evidence.urls.push(legalPage.url());
  await saveScreenshot(legalPage, stepName, "legal-page", true);

  if (popupPage) {
    await popupPage.close();
    await originalPage.bringToFront();
    await waitForUi(originalPage);
  } else {
    await originalPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      if (appReturnUrl) {
        await originalPage.goto(appReturnUrl, { waitUntil: "domcontentloaded" });
      }
    });
    await waitForUi(originalPage);
  }

  setStepStatusFromValidations(stepName);
}

async function writeReportAndPrintSummary() {
  const finalReport = {};
  for (const field of REPORT_FIELDS) {
    finalReport[field] = report.steps[field].status;
  }

  report.finalReport = finalReport;
  report.summary = {
    passed: Object.values(finalReport).filter((value) => value === "PASS").length,
    failed: Object.values(finalReport).filter((value) => value === "FAIL").length
  };

  await fs.mkdir(artifactsDir, { recursive: true });
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  console.log(`Report written to: ${reportPath}`);
  console.table(finalReport);
}

async function run() {
  await fs.mkdir(artifactsDir, { recursive: true });

  if (!LOGIN_URL) {
    addValidation(
      "Login",
      "SALEADS_LOGIN_URL is configured",
      false,
      "Missing SALEADS_LOGIN_URL environment variable."
    );
    report.steps.Login.details.push(
      "Set SALEADS_LOGIN_URL to the SaleADS login page for the target environment."
    );
    setStepStatusFromValidations("Login");
    markRemainingAsPrerequisiteFailed(1, "Login URL precondition not met.");
    await writeReportAndPrintSummary();
    process.exitCode = 1;
    return;
  }

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  try {
    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginButton = await firstVisible([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      page.getByRole("link", { name: /google/i })
    ], 12000);

    addValidation(
      "Login",
      "Login button or 'Sign in with Google' is visible",
      Boolean(loginButton),
      loginButton ? "" : "Google login button not found."
    );

    if (!loginButton) {
      setStepStatusFromValidations("Login");
      markRemainingAsPrerequisiteFailed(1, "Could not start Google login.");
      await writeReportAndPrintSummary();
      process.exitCode = 1;
      return;
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);
    const popupPage = await popupPromise;

    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      await popupPage.waitForTimeout(1200);
      const accountSelector = await firstVisible(
        [
          popupPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
          popupPage.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
          popupPage.getByRole("link", { name: GOOGLE_ACCOUNT_EMAIL })
        ],
        5000
      );

      if (accountSelector) {
        await accountSelector.click();
        await popupPage.waitForTimeout(1000);
        report.steps.Login.details.push(`Google account selected: ${GOOGLE_ACCOUNT_EMAIL}`);
      } else {
        report.steps.Login.details.push("Google account selector was opened but account was not visible.");
      }
    } else {
      report.steps.Login.details.push("No popup detected after clicking login (same-tab auth or existing session).");
    }

    const mainInterfaceVisible = await anyVisible(
      [
        page.locator("aside"),
        page.getByText(/mi negocio|negocio/i),
        page.getByRole("navigation")
      ],
      25000
    );
    addValidation(
      "Login",
      "Main application interface appears",
      mainInterfaceVisible,
      mainInterfaceVisible ? "" : "Main app interface was not detected after login."
    );

    const sidebarVisible = await anyVisible(
      [page.locator("aside"), page.getByRole("navigation"), page.getByText(/negocio/i)],
      25000
    );
    addValidation(
      "Login",
      "Left sidebar navigation is visible",
      sidebarVisible,
      sidebarVisible ? "" : "Sidebar navigation was not visible."
    );

    await saveScreenshot(page, "Login", "dashboard-loaded", true);
    setStepStatusFromValidations("Login");

    if (report.steps.Login.status !== "PASS") {
      markRemainingAsPrerequisiteFailed(1, "Login validations failed.");
      await writeReportAndPrintSummary();
      process.exitCode = 1;
      return;
    }

    const negocioSection = await firstVisible(
      [page.getByText(/^Negocio$/i), page.getByText(/negocio/i)],
      12000
    );
    if (negocioSection) {
      await negocioSection.click();
      await waitForUi(page);
    }

    const miNegocioOption = await firstVisible(
      [page.getByText(/^Mi Negocio$/i), page.getByRole("link", { name: /mi negocio/i })],
      12000
    );

    addValidation(
      "Mi Negocio menu",
      "Option 'Mi Negocio' is visible",
      Boolean(miNegocioOption),
      miNegocioOption ? "" : "Could not find 'Mi Negocio' option."
    );

    if (!miNegocioOption) {
      setStepStatusFromValidations("Mi Negocio menu");
      markRemainingAsPrerequisiteFailed(2, "Mi Negocio option not found.");
      await writeReportAndPrintSummary();
      process.exitCode = 1;
      return;
    }

    await miNegocioOption.click();
    await waitForUi(page);

    const agregarMenuVisible = await anyVisible(
      [page.getByText(/^Agregar Negocio$/i), page.getByRole("link", { name: /agregar negocio/i })],
      10000
    );
    addValidation("Mi Negocio menu", "'Agregar Negocio' is visible", agregarMenuVisible);

    const administrarMenuVisible = await anyVisible(
      [page.getByText(/^Administrar Negocios$/i), page.getByRole("link", { name: /administrar negocios/i })],
      10000
    );
    addValidation("Mi Negocio menu", "'Administrar Negocios' is visible", administrarMenuVisible);
    await saveScreenshot(page, "Mi Negocio menu", "expanded-menu", false);
    setStepStatusFromValidations("Mi Negocio menu");

    if (report.steps["Mi Negocio menu"].status !== "PASS") {
      markRemainingAsPrerequisiteFailed(2, "Mi Negocio submenu did not expand correctly.");
      await writeReportAndPrintSummary();
      process.exitCode = 1;
      return;
    }

    const agregarNegocioLink = await firstVisible(
      [page.getByText(/^Agregar Negocio$/i), page.getByRole("link", { name: /agregar negocio/i })],
      10000
    );

    if (agregarNegocioLink) {
      await agregarNegocioLink.click();
      await waitForUi(page);
    }

    const modalTitleVisible = await anyVisible(
      [page.getByText(/Crear Nuevo Negocio/i), page.getByRole("heading", { name: /Crear Nuevo Negocio/i })],
      10000
    );
    addValidation("Agregar Negocio modal", "Modal title 'Crear Nuevo Negocio' is visible", modalTitleVisible);

    const nombreInputVisible = await anyVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.getByRole("textbox", { name: /Nombre del Negocio/i })
      ],
      10000
    );
    addValidation("Agregar Negocio modal", "Input field 'Nombre del Negocio' exists", nombreInputVisible);

    const limitTextVisible = await anyVisible([page.getByText(/Tienes 2 de 3 negocios/i)], 8000);
    addValidation("Agregar Negocio modal", "Text 'Tienes 2 de 3 negocios' is visible", limitTextVisible);

    const cancelButton = await firstVisible(
      [page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)],
      8000
    );
    addValidation("Agregar Negocio modal", "Button 'Cancelar' is present", Boolean(cancelButton));

    const createButton = await firstVisible(
      [page.getByRole("button", { name: /Crear Negocio/i }), page.getByText(/Crear Negocio/i)],
      8000
    );
    addValidation("Agregar Negocio modal", "Button 'Crear Negocio' is present", Boolean(createButton));

    await saveScreenshot(page, "Agregar Negocio modal", "modal-visible", false);

    if (nombreInputVisible) {
      const input = await firstVisible(
        [
          page.getByLabel(/Nombre del Negocio/i),
          page.getByPlaceholder(/Nombre del Negocio/i),
          page.getByRole("textbox", { name: /Nombre del Negocio/i })
        ],
        5000
      );
      if (input) {
        await input.fill("Negocio Prueba Automatización");
      }
    }

    if (cancelButton) {
      await cancelButton.click();
      await waitForUi(page);
    }

    setStepStatusFromValidations("Agregar Negocio modal");

    if (report.steps["Agregar Negocio modal"].status !== "PASS") {
      markRemainingAsPrerequisiteFailed(3, "Agregar Negocio modal validations failed.");
      await writeReportAndPrintSummary();
      process.exitCode = 1;
      return;
    }

    let administrarNegociosOption = await firstVisible(
      [page.getByText(/^Administrar Negocios$/i), page.getByRole("link", { name: /administrar negocios/i })],
      5000
    );
    if (!administrarNegociosOption && miNegocioOption) {
      await miNegocioOption.click();
      await waitForUi(page);
      administrarNegociosOption = await firstVisible(
        [page.getByText(/^Administrar Negocios$/i), page.getByRole("link", { name: /administrar negocios/i })],
        8000
      );
    }

    addValidation(
      "Administrar Negocios view",
      "Option 'Administrar Negocios' is visible",
      Boolean(administrarNegociosOption),
      administrarNegociosOption ? "" : "Could not find 'Administrar Negocios' option."
    );

    if (!administrarNegociosOption) {
      setStepStatusFromValidations("Administrar Negocios view");
      markRemainingAsPrerequisiteFailed(4, "Administrar Negocios option not found.");
      await writeReportAndPrintSummary();
      process.exitCode = 1;
      return;
    }

    await administrarNegociosOption.click();
    await waitForUi(page);
    await page.waitForTimeout(1500);

    const infoGeneralVisible = await anyVisible(
      [page.getByRole("heading", { name: /Informaci[oó]n General/i }), page.getByText(/Informaci[oó]n General/i)],
      15000
    );
    addValidation("Administrar Negocios view", "Section 'Información General' exists", infoGeneralVisible);

    const detallesCuentaVisible = await anyVisible(
      [page.getByRole("heading", { name: /Detalles de la Cuenta/i }), page.getByText(/Detalles de la Cuenta/i)],
      15000
    );
    addValidation("Administrar Negocios view", "Section 'Detalles de la Cuenta' exists", detallesCuentaVisible);

    const tusNegociosVisible = await anyVisible(
      [page.getByRole("heading", { name: /Tus Negocios/i }), page.getByText(/Tus Negocios/i)],
      15000
    );
    addValidation("Administrar Negocios view", "Section 'Tus Negocios' exists", tusNegociosVisible);

    const legalSectionVisible = await anyVisible(
      [page.getByRole("heading", { name: /Secci[oó]n Legal/i }), page.getByText(/Secci[oó]n Legal/i)],
      15000
    );
    addValidation("Administrar Negocios view", "Section 'Sección Legal' exists", legalSectionVisible);

    await saveScreenshot(page, "Administrar Negocios view", "account-page-full", true);
    setStepStatusFromValidations("Administrar Negocios view");

    const userNameVisible = await anyVisible(
      [
        page.getByText(/Nombre/i),
        page.getByText(/Usuario/i),
        page.getByRole("heading").filter({ hasText: /\S+/ })
      ],
      10000
    );
    addValidation("Información General", "User name is visible", userNameVisible);

    const userEmailVisible = await anyVisible(
      [page.getByText(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/), page.getByText(/@/)],
      10000
    );
    addValidation("Información General", "User email is visible", userEmailVisible);

    const businessPlanVisible = await anyVisible([page.getByText(/BUSINESS PLAN/i)], 10000);
    addValidation("Información General", "Text 'BUSINESS PLAN' is visible", businessPlanVisible);

    const cambiarPlanVisible = await anyVisible(
      [page.getByRole("button", { name: /Cambiar Plan/i }), page.getByText(/Cambiar Plan/i)],
      10000
    );
    addValidation("Información General", "Button 'Cambiar Plan' is visible", cambiarPlanVisible);
    setStepStatusFromValidations("Información General");

    const cuentaCreadaVisible = await anyVisible([page.getByText(/Cuenta creada/i)], 10000);
    addValidation("Detalles de la Cuenta", "'Cuenta creada' is visible", cuentaCreadaVisible);

    const estadoActivoVisible = await anyVisible([page.getByText(/Estado activo/i)], 10000);
    addValidation("Detalles de la Cuenta", "'Estado activo' is visible", estadoActivoVisible);

    const idiomaVisible = await anyVisible([page.getByText(/Idioma seleccionado/i)], 10000);
    addValidation("Detalles de la Cuenta", "'Idioma seleccionado' is visible", idiomaVisible);
    setStepStatusFromValidations("Detalles de la Cuenta");

    const businessListVisible = await anyVisible(
      [
        page.getByRole("heading", { name: /Tus Negocios/i }),
        page.locator("ul, table, [role='list']").filter({ hasText: /Negocio/i })
      ],
      10000
    );
    addValidation("Tus Negocios", "Business list is visible", businessListVisible);

    const agregarButtonVisible = await anyVisible(
      [page.getByRole("button", { name: /Agregar Negocio/i }), page.getByText(/^Agregar Negocio$/i)],
      10000
    );
    addValidation("Tus Negocios", "Button 'Agregar Negocio' exists", agregarButtonVisible);

    const limitInListVisible = await anyVisible([page.getByText(/Tienes 2 de 3 negocios/i)], 10000);
    addValidation("Tus Negocios", "Text 'Tienes 2 de 3 negocios' is visible", limitInListVisible);
    setStepStatusFromValidations("Tus Negocios");

    const appUrlBeforeTerms = page.url();
    await validateLegalLink({
      stepName: "Términos y Condiciones",
      originalPage: page,
      context,
      linkPatterns: [/T[eé]rminos y Condiciones/i],
      headingPattern: /T[eé]rminos y Condiciones/i,
      appReturnUrl: appUrlBeforeTerms
    });

    const appUrlBeforePrivacy = page.url();
    await validateLegalLink({
      stepName: "Política de Privacidad",
      originalPage: page,
      context,
      linkPatterns: [/Pol[ií]tica de Privacidad/i],
      headingPattern: /Pol[ií]tica de Privacidad/i,
      appReturnUrl: appUrlBeforePrivacy
    });
  } finally {
    await browser.close();
    await writeReportAndPrintSummary();
  }

  const hasFailures = REPORT_FIELDS.some((field) => report.steps[field].status === "FAIL");
  process.exitCode = hasFailures ? 1 : 0;
}

run().catch(async (error) => {
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  report.unhandledError = message;
  for (const field of REPORT_FIELDS) {
    if (report.steps[field].validations.length === 0) {
      addValidation(field, "Unhandled error prevented execution", false, message);
      setStepStatusFromValidations(field);
    }
  }
  await writeReportAndPrintSummary();
  process.exitCode = 1;
});
