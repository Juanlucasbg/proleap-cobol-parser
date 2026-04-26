import fs from "node:fs";
import path from "node:path";

import { expect, test, type Locator, type Page } from "@playwright/test";

type StepStatus = "PASS" | "FAIL" | "SKIPPED";

type StepResult = {
  status: StepStatus;
  details: string[];
  screenshots: string[];
  url?: string;
};

type FinalReport = {
  Login: StepResult;
  "Mi Negocio menu": StepResult;
  "Agregar Negocio modal": StepResult;
  "Administrar Negocios view": StepResult;
  "Información General": StepResult;
  "Detalles de la Cuenta": StepResult;
  "Tus Negocios": StepResult;
  "Términos y Condiciones": StepResult;
  "Política de Privacidad": StepResult;
};

const REPORT_DIR = path.join(process.cwd(), "test-results");
const REPORT_PATH = path.join(REPORT_DIR, "saleads-mi-negocio-report.json");

const STEP_KEYS: (keyof FinalReport)[] = [
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

function makeStep(): StepResult {
  return { status: "SKIPPED", details: [], screenshots: [] };
}

function createReport(): FinalReport {
  return {
    Login: makeStep(),
    "Mi Negocio menu": makeStep(),
    "Agregar Negocio modal": makeStep(),
    "Administrar Negocios view": makeStep(),
    "Información General": makeStep(),
    "Detalles de la Cuenta": makeStep(),
    "Tus Negocios": makeStep(),
    "Términos y Condiciones": makeStep(),
    "Política de Privacidad": makeStep()
  };
}

function ensureReportDir(): void {
  if (!fs.existsSync(REPORT_DIR)) {
    fs.mkdirSync(REPORT_DIR, { recursive: true });
  }
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
}

async function captureStepScreenshot(
  page: Page,
  report: FinalReport,
  key: keyof FinalReport,
  screenshotName: string,
  fullPage = false
): Promise<void> {
  const shotPath = `screenshots/${screenshotName}`;
  await page.screenshot({
    path: path.join(REPORT_DIR, shotPath),
    fullPage
  });
  report[key].screenshots.push(shotPath);
}

async function clickByText(page: Page, labels: string[]): Promise<Locator> {
  for (const label of labels) {
    const roleButton = page.getByRole("button", { name: label, exact: false }).first();
    if (await roleButton.isVisible().catch(() => false)) {
      await roleButton.click();
      return roleButton;
    }

    const roleLink = page.getByRole("link", { name: label, exact: false }).first();
    if (await roleLink.isVisible().catch(() => false)) {
      await roleLink.click();
      return roleLink;
    }

    const textNode = page.getByText(label, { exact: false }).first();
    if (await textNode.isVisible().catch(() => false)) {
      await textNode.click();
      return textNode;
    }
  }

  throw new Error(`Could not find clickable element for labels: ${labels.join(", ")}`);
}

async function assertVisibleByText(page: Page, text: string): Promise<void> {
  await expect(page.getByText(text, { exact: false }).first()).toBeVisible();
}

async function assertVisibleByRole(
  page: Page,
  role: "button" | "link" | "textbox",
  name: string
): Promise<void> {
  await expect(page.getByRole(role, { name, exact: false }).first()).toBeVisible();
}

function markFail(step: StepResult, error: unknown): void {
  step.status = "FAIL";
  step.details.push(error instanceof Error ? error.message : String(error));
}

async function validateGoogleAccountChooserIfPresent(page: Page, step: StepResult): Promise<void> {
  const email = "juanlucasbarbiergarzon@gmail.com";
  const accountOption = page.getByText(email, { exact: false }).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    step.details.push(`Google account selected: ${email}`);
    await waitForUi(page);
  } else {
    step.details.push("Google account chooser not visible in current tab; continuing.");
  }
}

async function clickLegalAndValidate(
  appPage: Page,
  report: FinalReport,
  stepKey: "Términos y Condiciones" | "Política de Privacidad",
  linkText: string,
  headingText: string,
  screenshotName: string
): Promise<void> {
  const context = appPage.context();
  const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);

  await clickByText(appPage, [linkText]);
  await waitForUi(appPage);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  await waitForUi(targetPage);
  await expect(targetPage.getByText(headingText, { exact: false }).first()).toBeVisible();

  const bodyText = (await targetPage.locator("body").innerText()).trim();
  if (bodyText.length < 80) {
    throw new Error(`Legal content for "${headingText}" appears too short.`);
  }

  await captureStepScreenshot(targetPage, report, stepKey, screenshotName, true);
  report[stepKey].url = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  ensureReportDir();
  const report = createReport();

  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.saleads_login_url;
  if (!loginUrl) {
    for (const stepKey of STEP_KEYS) {
      report[stepKey].status = "FAIL";
      report[stepKey].details.push(
        "Missing SALEADS_LOGIN_URL environment variable. This test needs a target login page to run."
      );
    }
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
    expect(report.Login.status, "Missing SALEADS_LOGIN_URL").toBe("PASS");
    return;
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  // Step 1: Login with Google
  try {
    const loginStep = report.Login;
    await clickByText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google"
    ]);

    const popup = await context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await validateGoogleAccountChooserIfPresent(popup, loginStep);
      await popup.close().catch(() => undefined);
      await page.bringToFront();
    } else {
      await validateGoogleAccountChooserIfPresent(page, loginStep);
    }

    await waitForUi(page);
    await expect(page.locator("aside").first()).toBeVisible();
    await expect(page.locator("main").first()).toBeVisible();
    await captureStepScreenshot(page, report, "Login", "01-dashboard-loaded.png", true);

    loginStep.status = "PASS";
    loginStep.details.push("Main app interface loaded and sidebar is visible.");
  } catch (error) {
    markFail(report.Login, error);
  }

  // Step 2: Open Mi Negocio menu
  try {
    if (report.Login.status !== "PASS") {
      throw new Error("Skipped because login did not complete successfully.");
    }
    await clickByText(page, ["Negocio"]);
    await waitForUi(page);
    await clickByText(page, ["Mi Negocio"]);
    await waitForUi(page);

    await assertVisibleByText(page, "Agregar Negocio");
    await assertVisibleByText(page, "Administrar Negocios");
    await captureStepScreenshot(page, report, "Mi Negocio menu", "02-mi-negocio-menu-expanded.png");

    report["Mi Negocio menu"].status = "PASS";
    report["Mi Negocio menu"].details.push(
      "Mi Negocio expanded with Agregar Negocio and Administrar Negocios visible."
    );
  } catch (error) {
    markFail(report["Mi Negocio menu"], error);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    if (report["Mi Negocio menu"].status !== "PASS") {
      throw new Error("Skipped because Mi Negocio menu is unavailable.");
    }

    await clickByText(page, ["Agregar Negocio"]);
    await waitForUi(page);

    await assertVisibleByText(page, "Crear Nuevo Negocio");
    await assertVisibleByRole(page, "textbox", "Nombre del Negocio");
    await assertVisibleByText(page, "Tienes 2 de 3 negocios");
    await assertVisibleByRole(page, "button", "Cancelar");
    await assertVisibleByRole(page, "button", "Crear Negocio");

    await captureStepScreenshot(page, report, "Agregar Negocio modal", "03-agregar-negocio-modal.png");

    const businessNameInput = page.getByRole("textbox", { name: "Nombre del Negocio", exact: false }).first();
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickByText(page, ["Cancelar"]);
    await waitForUi(page);

    report["Agregar Negocio modal"].status = "PASS";
    report["Agregar Negocio modal"].details.push("Agregar Negocio modal validated and closed.");
  } catch (error) {
    markFail(report["Agregar Negocio modal"], error);
  }

  // Step 4: Open Administrar Negocios
  try {
    if (report["Mi Negocio menu"].status !== "PASS") {
      throw new Error("Skipped because Mi Negocio menu is unavailable.");
    }

    if (!(await page.getByText("Administrar Negocios", { exact: false }).first().isVisible().catch(() => false))) {
      await clickByText(page, ["Mi Negocio"]);
      await waitForUi(page);
    }
    await clickByText(page, ["Administrar Negocios"]);
    await waitForUi(page);

    await assertVisibleByText(page, "Información General");
    await assertVisibleByText(page, "Detalles de la Cuenta");
    await assertVisibleByText(page, "Tus Negocios");
    await assertVisibleByText(page, "Sección Legal");
    await captureStepScreenshot(
      page,
      report,
      "Administrar Negocios view",
      "04-administrar-negocios-account-page.png",
      true
    );

    report["Administrar Negocios view"].status = "PASS";
    report["Administrar Negocios view"].details.push("Administrar Negocios page loaded with all required sections.");
  } catch (error) {
    markFail(report["Administrar Negocios view"], error);
  }

  // Step 5: Validate Información General
  try {
    if (report["Administrar Negocios view"].status !== "PASS") {
      throw new Error("Skipped because Administrar Negocios view did not load.");
    }
    await assertVisibleByText(page, "BUSINESS PLAN");
    await assertVisibleByRole(page, "button", "Cambiar Plan");

    // Generic user identity signals without hardcoded values.
    const emailVisible =
      (await page.getByText(/@/, { exact: false }).first().isVisible().catch(() => false)) ||
      (await page.locator("text=/[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}/").first().isVisible().catch(() => false));
    if (!emailVisible) {
      throw new Error("User email was not detected in Información General.");
    }

    const userNameCandidate = page.locator("main h1, main h2, main h3").first();
    if (!(await userNameCandidate.isVisible().catch(() => false))) {
      throw new Error("User name heading was not detected in Información General.");
    }

    report["Información General"].status = "PASS";
    report["Información General"].details.push("User name/email, plan text, and Cambiar Plan are visible.");
  } catch (error) {
    markFail(report["Información General"], error);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    if (report["Administrar Negocios view"].status !== "PASS") {
      throw new Error("Skipped because Administrar Negocios view did not load.");
    }
    await assertVisibleByText(page, "Cuenta creada");
    await assertVisibleByText(page, "Estado activo");
    await assertVisibleByText(page, "Idioma seleccionado");
    report["Detalles de la Cuenta"].status = "PASS";
    report["Detalles de la Cuenta"].details.push("Detalles de la Cuenta labels are visible.");
  } catch (error) {
    markFail(report["Detalles de la Cuenta"], error);
  }

  // Step 7: Validate Tus Negocios
  try {
    if (report["Administrar Negocios view"].status !== "PASS") {
      throw new Error("Skipped because Administrar Negocios view did not load.");
    }
    await assertVisibleByText(page, "Tus Negocios");
    await assertVisibleByText(page, "Tienes 2 de 3 negocios");
    await assertVisibleByText(page, "Agregar Negocio");
    report["Tus Negocios"].status = "PASS";
    report["Tus Negocios"].details.push("Business list section and quota text are visible.");
  } catch (error) {
    markFail(report["Tus Negocios"], error);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    if (report["Administrar Negocios view"].status !== "PASS") {
      throw new Error("Skipped because Administrar Negocios view did not load.");
    }
    await clickLegalAndValidate(
      page,
      report,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05-terminos-y-condiciones.png"
    );
    report["Términos y Condiciones"].status = "PASS";
    report["Términos y Condiciones"].details.push("Legal page opened and validated.");
  } catch (error) {
    markFail(report["Términos y Condiciones"], error);
  }

  // Step 9: Validate Política de Privacidad
  try {
    if (report["Administrar Negocios view"].status !== "PASS") {
      throw new Error("Skipped because Administrar Negocios view did not load.");
    }
    await clickLegalAndValidate(
      page,
      report,
      "Política de Privacidad",
      "Política de Privacidad",
      "Política de Privacidad",
      "06-politica-de-privacidad.png"
    );
    report["Política de Privacidad"].status = "PASS";
    report["Política de Privacidad"].details.push("Privacy page opened and validated.");
  } catch (error) {
    markFail(report["Política de Privacidad"], error);
  }

  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");

  for (const stepKey of STEP_KEYS) {
    expect(report[stepKey].status, `Step failed: ${stepKey}. Details: ${report[stepKey].details.join(" | ")}`).toBe(
      "PASS"
    );
  }
});
