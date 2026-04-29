import { expect, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type Checkpoint = {
  status: "PASS" | "FAIL";
  details: string[];
  evidence: string[];
};

const REPORT_NAME = "saleads_mi_negocio_full_test_report.json";
const GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const CHECKPOINT_FIELDS: ReportField[] = [
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

function createReport(): Record<ReportField, Checkpoint> {
  const report = {} as Record<ReportField, Checkpoint>;
  for (const field of CHECKPOINT_FIELDS) {
    report[field] = { status: "PASS", details: [], evidence: [] };
  }
  return report;
}

function markFailure(report: Record<ReportField, Checkpoint>, field: ReportField, reason: string): void {
  report[field].status = "FAIL";
  report[field].details.push(reason);
}

function markNotExecuted(
  report: Record<ReportField, Checkpoint>,
  startFromIndex: number,
  reason: string
): void {
  for (let index = startFromIndex; index < CHECKPOINT_FIELDS.length; index += 1) {
    const field = CHECKPOINT_FIELDS[index];
    if (report[field].status !== "FAIL") {
      markFailure(report, field, reason);
    }
  }
}

async function saveCheckpointScreenshot(page: Page, screenshotPath: string): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
  await page.screenshot({ path: screenshotPath, fullPage: true });
}

async function resolvePopupAfterClick(page: Page, clickAction: () => Promise<void>): Promise<Page | null> {
  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  await clickAction();
  return popupPromise;
}

function loginPageNotReadyReason(currentUrl: string): string {
  return `Unable to detect SaleADS login page. Current URL is "${currentUrl}". Set SALEADS_BASE_URL/SALEADS_URL/SALEADS_LOGIN_URL or start the run from the login page.`;
}

async function clickVisibleText(page: Page, text: string): Promise<void> {
  const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const roleRegex = new RegExp(`^${escaped}$`, "i");

  const button = page.getByRole("button", { name: roleRegex }).first();
  if (await button.isVisible().catch(() => false)) {
    await button.click();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(1000);
    return;
  }

  const link = page.getByRole("link", { name: roleRegex }).first();
  if (await link.isVisible().catch(() => false)) {
    await link.click();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(1000);
    return;
  }

  const generic = page.getByText(text, { exact: true }).first();
  await generic.click();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

test("saleads_mi_negocio_full_test", async ({ page, context, baseURL }, testInfo) => {
  test.setTimeout(180000);

  const report = createReport();
  const artifactsDir = testInfo.outputPath("artifacts");
  await fs.mkdir(artifactsDir, { recursive: true });

  const configuredUrl =
    process.env.SALEADS_BASE_URL || process.env.SALEADS_URL || process.env.SALEADS_LOGIN_URL || baseURL || "";

  try {
    if (configuredUrl) {
      await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
      await page.waitForTimeout(1500);
      report.Login.details.push(`Started from configured login URL: ${configuredUrl}`);
    } else {
      report.Login.details.push(
        "No URL configured; test assumes execution starts with browser already on SaleADS login page."
      );
    }

    const currentUrl = page.url();
    if (!configuredUrl && (currentUrl === "about:blank" || currentUrl.startsWith("data:"))) {
      markFailure(report, "Login", loginPageNotReadyReason(currentUrl));
      throw new Error(loginPageNotReadyReason(currentUrl));
    }

    // Step 1: Login with Google.
    try {
      const loginByRole = page
        .getByRole("button", { name: /(Sign in with Google|Iniciar sesión con Google|Google)/i })
        .first();
      const loginByLink = page
        .getByRole("link", { name: /(Sign in with Google|Iniciar sesión con Google|Google)/i })
        .first();
      const loginByText = page.getByText(/(Sign in with Google|Google|Iniciar sesión con Google)/i).first();

      if (await loginByRole.isVisible().catch(() => false)) {
        await loginByRole.click();
      } else if (await loginByLink.isVisible().catch(() => false)) {
        await loginByLink.click();
      } else {
        await expect(loginByText).toBeVisible({ timeout: 30000 });
        await loginByText.click();
      }
      await page.waitForLoadState("domcontentloaded");
      await page.waitForTimeout(1500);

      const chooseAccount = page.getByText(GOOGLE_EMAIL, { exact: true }).first();
      if (await chooseAccount.isVisible().catch(() => false)) {
        await chooseAccount.click();
        await page.waitForLoadState("domcontentloaded");
        await page.waitForTimeout(2000);
        report.Login.details.push(`Google account selected: ${GOOGLE_EMAIL}`);
      } else {
        report.Login.details.push("Google account selector not shown; continued with current session.");
      }

      const sidebar = page.locator("aside, nav").first();
      await expect(sidebar).toBeVisible({ timeout: 45000 });
      report.Login.details.push("Main interface and left sidebar are visible.");

      const dashboardShot = path.join(artifactsDir, "01_dashboard_loaded.png");
      await saveCheckpointScreenshot(page, dashboardShot);
      report.Login.evidence.push(dashboardShot);
    } catch (error) {
      markFailure(report, "Login", `Login flow failed: ${String(error)}`);
      throw error;
    }

    // Step 2: Open Mi Negocio menu.
    try {
      await clickVisibleText(page, "Negocio");
      await clickVisibleText(page, "Mi Negocio");

      await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText("Administrar Negocios", { exact: true }).first()).toBeVisible({ timeout: 15000 });
      report["Mi Negocio menu"].details.push("Mi Negocio submenu expanded and expected options are visible.");

      const menuShot = path.join(artifactsDir, "02_mi_negocio_menu_expanded.png");
      await saveCheckpointScreenshot(page, menuShot);
      report["Mi Negocio menu"].evidence.push(menuShot);
    } catch (error) {
      markFailure(report, "Mi Negocio menu", `Menu validation failed: ${String(error)}`);
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal.
    try {
      await clickVisibleText(page, "Agregar Negocio");
      const modalTitle = page.getByText("Crear Nuevo Negocio", { exact: true }).first();
      await expect(modalTitle).toBeVisible({ timeout: 15000 });

      const businessNameInput = page.getByLabel("Nombre del Negocio").first();
      await expect(businessNameInput).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible({ timeout: 10000 });

      const modalShot = path.join(artifactsDir, "03_agregar_negocio_modal.png");
      await saveCheckpointScreenshot(page, modalShot);
      report["Agregar Negocio modal"].evidence.push(modalShot);

      await businessNameInput.click();
      await page.waitForLoadState("domcontentloaded");
      await page.waitForTimeout(800);
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickVisibleText(page, "Cancelar");
      await expect(modalTitle).toBeHidden({ timeout: 10000 });

      report["Agregar Negocio modal"].details.push("Modal fields, quota text, and buttons validated.");
    } catch (error) {
      markFailure(report, "Agregar Negocio modal", `Agregar Negocio modal failed: ${String(error)}`);
      throw error;
    }

    // Step 4: Open Administrar Negocios.
    try {
      if (!(await page.getByText("Administrar Negocios", { exact: true }).first().isVisible().catch(() => false))) {
        await clickVisibleText(page, "Mi Negocio");
      }

      await clickVisibleText(page, "Administrar Negocios");

      await expect(page.getByText("Información General", { exact: true }).first()).toBeVisible({ timeout: 30000 });
      await expect(page.getByText("Detalles de la Cuenta", { exact: true }).first()).toBeVisible({ timeout: 30000 });
      await expect(page.getByText("Tus Negocios", { exact: true }).first()).toBeVisible({ timeout: 30000 });
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 30000 });
      report["Administrar Negocios view"].details.push("All account sections are visible.");

      const accountShot = path.join(artifactsDir, "04_administrar_negocios_full.png");
      await saveCheckpointScreenshot(page, accountShot);
      report["Administrar Negocios view"].evidence.push(accountShot);
    } catch (error) {
      markFailure(report, "Administrar Negocios view", `Administrar Negocios validation failed: ${String(error)}`);
      throw error;
    }

    // Step 5: Validate Información General.
    try {
      const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
      await expect(page.getByText(emailRegex).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText("BUSINESS PLAN", { exact: true }).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible({ timeout: 20000 });

      // Name can vary by environment/user; assert that non-empty profile text is present.
      const possibleName = page.locator("h1, h2, h3, p, span").filter({ hasText: /\S+/ }).first();
      await expect(possibleName).toBeVisible({ timeout: 20000 });
      report["Información General"].details.push("Name-like text, email, BUSINESS PLAN, and Cambiar Plan are visible.");
    } catch (error) {
      markFailure(report, "Información General", `Información General validation failed: ${String(error)}`);
      throw error;
    }

    // Step 6: Validate Detalles de la Cuenta.
    try {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20000 });
      report["Detalles de la Cuenta"].details.push("Cuenta creada, Estado activo, Idioma seleccionado are visible.");
    } catch (error) {
      markFailure(report, "Detalles de la Cuenta", `Detalles de la Cuenta validation failed: ${String(error)}`);
      throw error;
    }

    // Step 7: Validate Tus Negocios.
    try {
      await expect(page.getByText("Tus Negocios", { exact: true }).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: "Agregar Negocio" }).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20000 });
      report["Tus Negocios"].details.push("Business list area, Agregar Negocio button, and quota text are visible.");
    } catch (error) {
      markFailure(report, "Tus Negocios", `Tus Negocios validation failed: ${String(error)}`);
      throw error;
    }

    // Step 8: Validate Términos y Condiciones.
    try {
      const termsLabel = "Términos y Condiciones";
      const termsLink = page.getByRole("link", { name: termsLabel }).first();
      await expect(termsLink).toBeVisible({ timeout: 20000 });

      const termsPage = await resolvePopupAfterClick(page, async () => {
        await termsLink.click();
      });

      const activeTermsPage = termsPage ?? page;
      await activeTermsPage.waitForLoadState("domcontentloaded");
      await activeTermsPage.waitForTimeout(1500);

      await expect(activeTermsPage.getByText(/Términos y Condiciones/i).first()).toBeVisible({ timeout: 30000 });
      await expect(
        activeTermsPage.locator("p, article, section, div").filter({ hasText: /\S{20,}/ }).first()
      ).toBeVisible({
        timeout: 30000
      });

      const termsShot = path.join(artifactsDir, "08_terminos_y_condiciones.png");
      await saveCheckpointScreenshot(activeTermsPage, termsShot);
      report["Términos y Condiciones"].evidence.push(termsShot);
      report["Términos y Condiciones"].details.push(`Final URL: ${activeTermsPage.url()}`);

      if (termsPage) {
        await termsPage.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => Promise.resolve());
        await page.waitForTimeout(1000);
      }
    } catch (error) {
      markFailure(report, "Términos y Condiciones", `Términos y Condiciones validation failed: ${String(error)}`);
      throw error;
    }

    // Step 9: Validate Política de Privacidad.
    try {
      const privacyLabel = "Política de Privacidad";
      const privacyLink = page.getByRole("link", { name: privacyLabel }).first();
      await expect(privacyLink).toBeVisible({ timeout: 20000 });

      const privacyPage = await resolvePopupAfterClick(page, async () => {
        await privacyLink.click();
      });

      const activePrivacyPage = privacyPage ?? page;
      await activePrivacyPage.waitForLoadState("domcontentloaded");
      await activePrivacyPage.waitForTimeout(1500);

      await expect(activePrivacyPage.getByText(/Política de Privacidad/i).first()).toBeVisible({ timeout: 30000 });
      await expect(
        activePrivacyPage.locator("p, article, section, div").filter({ hasText: /\S{20,}/ }).first()
      ).toBeVisible({
        timeout: 30000
      });

      const privacyShot = path.join(artifactsDir, "09_politica_de_privacidad.png");
      await saveCheckpointScreenshot(activePrivacyPage, privacyShot);
      report["Política de Privacidad"].evidence.push(privacyShot);
      report["Política de Privacidad"].details.push(`Final URL: ${activePrivacyPage.url()}`);

      if (privacyPage) {
        await privacyPage.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => Promise.resolve());
        await page.waitForTimeout(1000);
      }
    } catch (error) {
      markFailure(report, "Política de Privacidad", `Política de Privacidad validation failed: ${String(error)}`);
      throw error;
    }
  } catch (error) {
    const firstFailedIndex = CHECKPOINT_FIELDS.findIndex((field) => report[field].status === "FAIL");
    if (firstFailedIndex >= 0 && firstFailedIndex < CHECKPOINT_FIELDS.length - 1) {
      markNotExecuted(report, firstFailedIndex + 1, "Not executed because an earlier required step failed.");
    }
    throw error;
  } finally {
    const reportPath = testInfo.outputPath(REPORT_NAME);
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf-8");
    testInfo.attachments.push({
      name: "final-report",
      path: reportPath,
      contentType: "application/json"
    });
  }
});
