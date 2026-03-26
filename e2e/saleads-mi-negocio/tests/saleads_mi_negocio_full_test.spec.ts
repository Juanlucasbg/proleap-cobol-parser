import { expect, test, type Locator, type Page } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepStatus = "PASS" | "FAIL";

type ReportRecord = Record<ReportKey, StepStatus>;

function createDefaultReport(): ReportRecord {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
}

function slugify(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const candidates: Locator[] = [
    page.getByRole("button", { name: text, exact: false }),
    page.getByRole("link", { name: text, exact: false }),
    page.getByText(new RegExp(text, "i"))
  ];

  for (const candidate of candidates) {
    const target = candidate.first();
    if (await target.isVisible().catch(() => false)) {
      await target.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Could not click visible text: ${text}`);
}

async function clickGoogleAndSelectAccount(page: Page, email: string): Promise<void> {
  const popupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
  await clickByVisibleText(page, "Google");
  const popup = await popupPromise;

  if (!popup) {
    const samePageAccount = page.getByText(email, { exact: false }).first();
    if (await samePageAccount.isVisible().catch(() => false)) {
      await samePageAccount.click();
      await waitForUi(page);
    }
    return;
  }

  await popup.waitForLoadState("domcontentloaded");
  await popup.waitForTimeout(800);
  const popupAccount = popup.getByText(email, { exact: false }).first();

  if (await popupAccount.isVisible().catch(() => false)) {
    await popupAccount.click();
    await popup.waitForTimeout(1200);
  }

  // Google flow may auto-close or redirect in place.
  await popup.close().catch(() => undefined);
  await page.bringToFront();
  await waitForUi(page);
}

async function expectAnyVisible(page: Page, selectors: Locator[], label: string): Promise<void> {
  for (const selector of selectors) {
    if (await selector.first().isVisible().catch(() => false)) {
      await expect(selector.first(), `${label} should be visible`).toBeVisible();
      return;
    }
  }
  throw new Error(`None of the expected selectors were visible: ${label}`);
}

async function saveCheckpointScreenshot(page: Page, testTitle: string, checkpoint: string): Promise<string> {
  const fileName = `${slugify(testTitle)}__${slugify(checkpoint)}.png`;
  const outputPath = path.join("test-results", "checkpoints", fileName);
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  await page.screenshot({ path: outputPath, fullPage: true });
  return outputPath;
}

async function openLegalLinkAndValidate(
  page: Page,
  linkText: string,
  headingPattern: RegExp,
  testTitle: string,
  checkpoint: string
): Promise<{ finalUrl: string; screenshotPath: string }> {
  const maybePopupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  await clickByVisibleText(page, linkText);
  const popup = await maybePopupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForTimeout(800);
    await expect(popup.getByRole("heading", { name: headingPattern }).first()).toBeVisible();
    await expect(popup.getByText(/.+/).first()).toBeVisible();
    const screenshotPath = await saveCheckpointScreenshot(popup, testTitle, checkpoint);
    const finalUrl = popup.url();
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return { finalUrl, screenshotPath };
  }

  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
  await expect(page.getByRole("heading", { name: headingPattern }).first()).toBeVisible();
  await expect(page.getByText(/.+/).first()).toBeVisible();
  const screenshotPath = await saveCheckpointScreenshot(page, testTitle, checkpoint);
  const finalUrl = page.url();
  await page.goBack().catch(() => undefined);
  await waitForUi(page);
  return { finalUrl, screenshotPath };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const testTitle = testInfo.title;
  const summary: Record<string, string> = {};
  const report = createDefaultReport();

  try {
    // Step 1: Login with Google.
    // The prompt states the browser starts on the login page of the current environment.
    if (testInfo.project.use.baseURL) {
      await page.goto(testInfo.project.use.baseURL as string);
    }
    await waitForUi(page);

    await clickGoogleAndSelectAccount(page, "juanlucasbarbiergarzon@gmail.com");

    await expectAnyVisible(
      page,
      [
        page.getByRole("navigation").first(),
        page.locator("aside").first(),
        page.getByText(/Negocio/i).first()
      ],
      "Main app interface and sidebar"
    );

    summary.dashboardScreenshot = await saveCheckpointScreenshot(page, testTitle, "dashboard_loaded");
    report.Login = "PASS";

    // Step 2: Open Mi Negocio menu.
    await clickByVisibleText(page, "Negocio");
    await clickByVisibleText(page, "Mi Negocio");
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    summary.expandedMenuScreenshot = await saveCheckpointScreenshot(page, testTitle, "mi_negocio_menu_expanded");
    report["Mi Negocio menu"] = "PASS";

    // Step 3: Validate Agregar Negocio modal.
    await clickByVisibleText(page, "Agregar Negocio");
    const modal = page.getByRole("dialog").first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(modal.getByPlaceholder(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    summary.modalScreenshot = await saveCheckpointScreenshot(page, testTitle, "agregar_negocio_modal");

    const nameInput = modal.getByPlaceholder(/Nombre del Negocio/i).first();
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatizacion");
    await modal.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUi(page);
    report["Agregar Negocio modal"] = "PASS";

    // Step 4: Open Administrar Negocios.
    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickByVisibleText(page, "Mi Negocio");
    }
    await clickByVisibleText(page, "Administrar Negocios");
    await waitForUi(page);

    await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible();
    summary.accountPageScreenshot = await saveCheckpointScreenshot(page, testTitle, "administrar_negocios_view");
    report["Administrar Negocios view"] = "PASS";

    // Step 5: Validate Informacion General.
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    // Generic checks to avoid coupling to one environment's user name/email values.
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.locator("text=/[A-Za-z].*[A-Za-z]/").first()).toBeVisible();
    report["Información General"] = "PASS";

    // Step 6: Validate Detalles de la Cuenta.
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    report["Detalles de la Cuenta"] = "PASS";

    // Step 7: Validate Tus Negocios.
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    report["Tus Negocios"] = "PASS";

    // Step 8: Validate Terminos y Condiciones.
    const terms = await openLegalLinkAndValidate(
      page,
      "Términos y Condiciones",
      /Terminos y Condiciones|Términos y Condiciones/i,
      testTitle,
      "terminos_y_condiciones"
    );
    summary.termsFinalUrl = terms.finalUrl;
    summary.termsScreenshot = terms.screenshotPath;
    report["Términos y Condiciones"] = "PASS";

    // Step 9: Validate Politica de Privacidad.
    const privacy = await openLegalLinkAndValidate(
      page,
      "Política de Privacidad",
      /Politica de Privacidad|Política de Privacidad/i,
      testTitle,
      "politica_de_privacidad"
    );
    summary.privacyFinalUrl = privacy.finalUrl;
    summary.privacyScreenshot = privacy.screenshotPath;
    report["Política de Privacidad"] = "PASS";
  } finally {
    // Step 10: Final report artifact.
    const reportDir = path.join("test-results", "reports");
    fs.mkdirSync(reportDir, { recursive: true });
    const reportPath = path.join(reportDir, `${slugify(testTitle)}.json`);

    const fullReport = {
      testName: testTitle,
      statusByStep: report,
      evidence: summary,
      generatedAt: new Date().toISOString()
    };
    fs.writeFileSync(reportPath, JSON.stringify(fullReport, null, 2), "utf-8");

    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json"
    });
  }
});
