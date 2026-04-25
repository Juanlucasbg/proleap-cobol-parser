import { expect, Page, TestInfo, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

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

type StepReport = Record<ReportKey, StepStatus>;

const APP_URL = process.env.SALEADS_URL;
const APP_BASE_URL = process.env.SALEADS_BASE_URL;
const GOOGLE_ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

const DEFAULT_TIMEOUT_MS = 30_000;

function textRegex(value: string): RegExp {
  return new RegExp(`^\\s*${escapeRegex(value)}\\s*$`, "i");
}

function containsTextRegex(value: string): RegExp {
  return new RegExp(escapeRegex(value), "i");
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: DEFAULT_TIMEOUT_MS });
  await page.waitForLoadState("networkidle", { timeout: DEFAULT_TIMEOUT_MS }).catch(() => {
    // Some apps keep open connections (websockets), so domcontentloaded is enough fallback.
  });
}

async function checkpointScreenshot(
  page: Page,
  testInfo: TestInfo,
  filename: string,
  options?: { fullPage?: boolean }
): Promise<void> {
  const screenshotPath = testInfo.outputPath(filename);
  await page.screenshot({ path: screenshotPath, fullPage: options?.fullPage ?? false });
  await testInfo.attach(filename, {
    path: screenshotPath,
    contentType: "image/png"
  });
}

async function clickByVisibleText(page: Page, label: string): Promise<void> {
  const target = page
    .getByRole("button", { name: containsTextRegex(label) })
    .or(page.getByRole("link", { name: containsTextRegex(label) }))
    .or(page.getByText(textRegex(label)));

  await expect(target.first()).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });
  await target.first().click();
  await waitForUi(page);
}

async function ensureSidebarVisible(page: Page): Promise<void> {
  const sidebar = page
    .locator("nav, aside, [class*='sidebar'], [data-testid*='sidebar']")
    .filter({ hasText: containsTextRegex("Negocio") });
  await expect(sidebar.first()).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });
}

async function openOrReuseLegalPageFromAction(page: Page, action: () => Promise<void>): Promise<Page> {
  const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  await action();
  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: DEFAULT_TIMEOUT_MS });
    await popup.waitForLoadState("networkidle", { timeout: DEFAULT_TIMEOUT_MS }).catch(() => {
      // See waitForUi note.
    });
    return popup;
  }
  return page;
}

function initReport(): StepReport {
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

async function writeFinalReport(testInfo: TestInfo, report: StepReport, details: Record<string, string>): Promise<void> {
  const reportPath = testInfo.outputPath("saleads_mi_negocio_final_report.json");
  const payload = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    details
  };
  fs.writeFileSync(reportPath, JSON.stringify(payload, null, 2), "utf8");
  await testInfo.attach("saleads_mi_negocio_final_report.json", {
    path: reportPath,
    contentType: "application/json"
  });
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login + Mi Negocio complete workflow", async ({ page }, testInfo) => {
    test.setTimeout(180_000);

    if (!APP_URL && !APP_BASE_URL) {
      throw new Error(
        "Set SALEADS_URL or SALEADS_BASE_URL to run in any SaleADS environment without hardcoded domains."
      );
    }

    const report = initReport();
    const details: Record<string, string> = {};
    const targetUrl = APP_URL ?? "/";

    try {
      await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);

      // Step 1: Login with Google
      try {
        await clickByVisibleText(page, "Sign in with Google").catch(async () => {
          await clickByVisibleText(page, "Google");
        });

        const googleAccountCard = page.getByText(containsTextRegex(GOOGLE_ACCOUNT_EMAIL)).first();
        if (await googleAccountCard.isVisible({ timeout: 5_000 }).catch(() => false)) {
          await googleAccountCard.click();
        }

        await waitForUi(page);
        await ensureSidebarVisible(page);
        await checkpointScreenshot(page, testInfo, "01-dashboard-loaded.png");

        report.Login = "PASS";
        details.Login = "Main interface and left sidebar are visible after Google login.";
      } catch (error) {
        details.Login = `Login flow failed: ${String(error)}`;
        throw error;
      }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByVisibleText(page, "Negocio").catch(async () => {
        await clickByVisibleText(page, "Mi Negocio");
      });
      await clickByVisibleText(page, "Mi Negocio");

      await expect(page.getByText(textRegex("Agregar Negocio"))).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });
      await expect(page.getByText(textRegex("Administrar Negocios"))).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });

      await checkpointScreenshot(page, testInfo, "02-mi-negocio-expanded.png");
      report["Mi Negocio menu"] = "PASS";
      details["Mi Negocio menu"] = "Menu expanded and submenu options are visible.";
    } catch (error) {
      details["Mi Negocio menu"] = `Mi Negocio menu validation failed: ${String(error)}`;
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, "Agregar Negocio");

      await expect(page.getByText(textRegex("Crear Nuevo Negocio"))).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });
      const businessNameField = page
        .getByRole("textbox", { name: containsTextRegex("Nombre del Negocio") })
        .or(page.getByPlaceholder(containsTextRegex("Nombre del Negocio")));
      await expect(businessNameField.first()).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });

      await expect(page.getByText(containsTextRegex("Tienes 2 de 3 negocios"))).toBeVisible({
        timeout: DEFAULT_TIMEOUT_MS
      });
      await expect(page.getByRole("button", { name: textRegex("Cancelar") })).toBeVisible({
        timeout: DEFAULT_TIMEOUT_MS
      });
      await expect(page.getByRole("button", { name: containsTextRegex("Crear Negocio") })).toBeVisible({
        timeout: DEFAULT_TIMEOUT_MS
      });

      await checkpointScreenshot(page, testInfo, "03-agregar-negocio-modal.png");

      // Optional actions requested in prompt
      await businessNameField.first().click();
      await businessNameField.first().fill("Negocio Prueba Automatización");
      await clickByVisibleText(page, "Cancelar");

      report["Agregar Negocio modal"] = "PASS";
      details["Agregar Negocio modal"] = "Modal and required controls are visible; optional fill+cancel executed.";
    } catch (error) {
      details["Agregar Negocio modal"] = `Agregar Negocio modal validation failed: ${String(error)}`;
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      const administrarOption = page.getByText(textRegex("Administrar Negocios")).first();
      if (!(await administrarOption.isVisible().catch(() => false))) {
        await clickByVisibleText(page, "Mi Negocio");
      }
      await clickByVisibleText(page, "Administrar Negocios");

      await expect(page.getByText(containsTextRegex("Información General"))).toBeVisible({
        timeout: DEFAULT_TIMEOUT_MS
      });
      await expect(page.getByText(containsTextRegex("Detalles de la Cuenta"))).toBeVisible({
        timeout: DEFAULT_TIMEOUT_MS
      });
      await expect(page.getByText(containsTextRegex("Tus Negocios"))).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });
      await expect(page.getByText(containsTextRegex("Sección Legal"))).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });

      await checkpointScreenshot(page, testInfo, "04-administrar-negocios-page.png", { fullPage: true });
      report["Administrar Negocios view"] = "PASS";
      details["Administrar Negocios view"] = "All expected account sections are present.";
    } catch (error) {
      details["Administrar Negocios view"] = `Administrar Negocios page validation failed: ${String(error)}`;
      throw error;
    }

    // Step 5: Validate Información General
    try {
      const infoGeneralSection = page
        .locator("section, div")
        .filter({ hasText: containsTextRegex("Información General") })
        .first();
      await expect(infoGeneralSection).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });
      await expect(infoGeneralSection).toContainText(containsTextRegex("@"), { timeout: DEFAULT_TIMEOUT_MS });
      await expect(infoGeneralSection).toContainText(containsTextRegex("BUSINESS PLAN"), { timeout: DEFAULT_TIMEOUT_MS });
      await expect(
        infoGeneralSection
          .getByRole("button", { name: containsTextRegex("Cambiar Plan") })
          .or(infoGeneralSection.getByText(containsTextRegex("Cambiar Plan")))
          .first()
      ).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });

      report["Información General"] = "PASS";
      details["Información General"] = "Name/email, BUSINESS PLAN and Cambiar Plan are visible.";
    } catch (error) {
      details["Información General"] = `Información General validation failed: ${String(error)}`;
      throw error;
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      const detailsSection = page
        .locator("section, div")
        .filter({ hasText: containsTextRegex("Detalles de la Cuenta") })
        .first();
      await expect(detailsSection).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });
      await expect(detailsSection).toContainText(containsTextRegex("Cuenta creada"), { timeout: DEFAULT_TIMEOUT_MS });
      await expect(detailsSection).toContainText(containsTextRegex("Estado activo"), { timeout: DEFAULT_TIMEOUT_MS });
      await expect(detailsSection).toContainText(containsTextRegex("Idioma seleccionado"), { timeout: DEFAULT_TIMEOUT_MS });

      report["Detalles de la Cuenta"] = "PASS";
      details["Detalles de la Cuenta"] = "Cuenta creada, Estado activo, and Idioma seleccionado are visible.";
    } catch (error) {
      details["Detalles de la Cuenta"] = `Detalles de la Cuenta validation failed: ${String(error)}`;
      throw error;
    }

    // Step 7: Validate Tus Negocios
    try {
      const businessesSection = page
        .locator("section, div")
        .filter({ hasText: containsTextRegex("Tus Negocios") })
        .first();
      await expect(businessesSection).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });
      await expect(
        businessesSection
          .getByRole("button", { name: containsTextRegex("Agregar Negocio") })
          .or(businessesSection.getByText(textRegex("Agregar Negocio")))
          .first()
      ).toBeVisible({ timeout: DEFAULT_TIMEOUT_MS });
      await expect(businessesSection).toContainText(containsTextRegex("Tienes 2 de 3 negocios"), {
        timeout: DEFAULT_TIMEOUT_MS
      });

      report["Tus Negocios"] = "PASS";
      details["Tus Negocios"] = "Business list area, add button, and quota text are visible.";
    } catch (error) {
      details["Tus Negocios"] = `Tus Negocios validation failed: ${String(error)}`;
      throw error;
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const termsPage = await openOrReuseLegalPageFromAction(page, async () => {
        await clickByVisibleText(page, "Términos y Condiciones");
      });
      await expect(termsPage.getByRole("heading", { name: containsTextRegex("Términos y Condiciones") })).toBeVisible({
        timeout: DEFAULT_TIMEOUT_MS
      });
      await expect(termsPage.locator("main, article, body")).toContainText(/\S+/, { timeout: DEFAULT_TIMEOUT_MS });

      await checkpointScreenshot(termsPage, testInfo, "08-terminos-y-condiciones.png", { fullPage: true });
      details["Términos y Condiciones URL"] = termsPage.url();
      report["Términos y Condiciones"] = "PASS";
      details["Términos y Condiciones"] = "Heading and legal text are visible.";

      if (termsPage !== page) {
        await termsPage.close();
        await page.bringToFront();
        await waitForUi(page);
      }
    } catch (error) {
      details["Términos y Condiciones"] = `Términos y Condiciones validation failed: ${String(error)}`;
      throw error;
    }

      // Step 9: Validate Política de Privacidad
      try {
        const privacyPage = await openOrReuseLegalPageFromAction(page, async () => {
          await clickByVisibleText(page, "Política de Privacidad");
        });
        await expect(privacyPage.getByRole("heading", { name: containsTextRegex("Política de Privacidad") })).toBeVisible({
          timeout: DEFAULT_TIMEOUT_MS
        });
        await expect(privacyPage.locator("main, article, body")).toContainText(/\S+/, { timeout: DEFAULT_TIMEOUT_MS });

        await checkpointScreenshot(privacyPage, testInfo, "09-politica-de-privacidad.png", { fullPage: true });
        details["Política de Privacidad URL"] = privacyPage.url();
        report["Política de Privacidad"] = "PASS";
        details["Política de Privacidad"] = "Heading and legal text are visible.";

        if (privacyPage !== page) {
          await privacyPage.close();
          await page.bringToFront();
          await waitForUi(page);
        }
      } catch (error) {
        details["Política de Privacidad"] = `Política de Privacidad validation failed: ${String(error)}`;
        throw error;
      }
    } finally {
      await writeFinalReport(testInfo, report, details);
    }
  });
});

test.afterEach(async ({}, testInfo) => {
  // Keep a flat export in repository for external runners if desired.
  const outputReportPath = testInfo.outputPath("saleads_mi_negocio_final_report.json");
  if (fs.existsSync(outputReportPath)) {
    const exportPath = path.resolve(process.cwd(), "test-results", "saleads_mi_negocio_final_report.json");
    fs.mkdirSync(path.dirname(exportPath), { recursive: true });
    fs.copyFileSync(outputReportPath, exportPath);
  }
});
