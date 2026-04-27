import { expect, test, type Locator, type Page } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type ValidationStatus = "PASS" | "FAIL";

type ReportEntry = {
  field: string;
  status: ValidationStatus;
  details: string[];
};

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
] as const;

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_NEGOCIO_NAME = "Negocio Prueba Automatización";

function createReport(): Map<string, ReportEntry> {
  return new Map(
    REPORT_FIELDS.map((field) => [
      field,
      {
        field,
        status: "FAIL",
        details: [],
      },
    ]),
  );
}

function pass(report: Map<string, ReportEntry>, field: string, detail: string): void {
  const entry = report.get(field);
  if (!entry) {
    return;
  }
  entry.status = "PASS";
  entry.details.push(detail);
}

function fail(report: Map<string, ReportEntry>, field: string, detail: string): never {
  const entry = report.get(field);
  if (entry) {
    entry.status = "FAIL";
    entry.details.push(detail);
  }
  throw new Error(`[${field}] ${detail}`);
}

async function clickAndWaitUi(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function saveCheckpoint(page: Page, testOutputDir: string, filename: string, fullPage = false): Promise<void> {
  await page.waitForLoadState("networkidle");
  await page.screenshot({
    path: path.join(testOutputDir, filename),
    fullPage,
  });
}

function textLocator(page: Page, text: string): Locator {
  return page.getByText(text, { exact: false });
}

async function openTermsOrPrivacy(
  page: Page,
  linkText: string,
  headingText: string,
  testOutputDir: string,
  screenshotName: string,
): Promise<{ finalUrl: string }> {
  const link = page.getByRole("link", { name: linkText }).first();
  await expect(link).toBeVisible();

  const popupPromise = page
    .waitForEvent("popup", { timeout: 8_000 })
    .then((popup) => ({ kind: "popup" as const, target: popup }))
    .catch(() => null);
  const navPromise = page
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 8_000 })
    .then(() => ({ kind: "same-tab" as const, target: page }))
    .catch(() => null);

  await clickAndWaitUi(page, link);

  const pageOrPopup = await Promise.race([popupPromise, navPromise]);
  if (!pageOrPopup) {
    throw new Error(`No popup or navigation detected after clicking '${linkText}'.`);
  }

  const targetPage = pageOrPopup.target;
  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForLoadState("networkidle");

  await expect(targetPage.getByRole("heading", { name: headingText }).first()).toBeVisible();
  await expect(targetPage.locator("body")).toContainText(headingText);
  await saveCheckpoint(targetPage, testOutputDir, screenshotName, true);

  const finalUrl = targetPage.url();
  if (pageOrPopup.kind === "popup") {
    await targetPage.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle");
  }

  return { finalUrl };
}

async function ensureOnLoginPage(page: Page): Promise<void> {
  if (page.url() === "about:blank") {
    const baseUrl = process.env.SALEADS_URL;
    if (!baseUrl) {
      throw new Error(
        "No page is open and SALEADS_URL is not provided. Open the SaleADS login page first or set SALEADS_URL.",
      );
    }
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  }
}

async function maybeHandleGoogleAccountSelection(page: Page): Promise<void> {
  const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountOption.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await clickAndWaitUi(page, accountOption);
  }
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarNegocioOption = textLocator(page, "Agregar Negocio").first();
  if (await agregarNegocioOption.isVisible({ timeout: 2_000 }).catch(() => false)) {
    return;
  }
  const negocioSection = page.getByRole("button", { name: /negocio|mi negocio/i }).first();
  await clickAndWaitUi(page, negocioSection);
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const testOutputDir = testInfo.outputDir;
  const legalUrls: Record<string, string> = {};

  await ensureOnLoginPage(page);

  try {
    // Step 1: Login with Google
    const signInGoogleButton = page
      .getByRole("button", { name: /google|sign in/i })
      .or(page.getByRole("link", { name: /google|sign in/i }))
      .first();
    await clickAndWaitUi(page, signInGoogleButton);
    await maybeHandleGoogleAccountSelection(page);

    const leftSidebar = page.locator("aside, nav").filter({ hasText: /negocio|dashboard|mi negocio/i }).first();
    await expect(leftSidebar).toBeVisible({ timeout: 90_000 });
    await saveCheckpoint(page, testOutputDir, "01-dashboard-loaded.png");
    pass(report, "Login", "Dashboard loaded and sidebar is visible.");

    // Step 2: Open Mi Negocio menu
    await ensureMiNegocioExpanded(page);
    await expect(textLocator(page, "Agregar Negocio").first()).toBeVisible();
    await expect(textLocator(page, "Administrar Negocios").first()).toBeVisible();
    await saveCheckpoint(page, testOutputDir, "02-mi-negocio-expanded-menu.png");
    pass(report, "Mi Negocio menu", "Mi Negocio menu expanded with expected submenu entries.");

    // Step 3: Validate Agregar Negocio modal
    const agregarNegocioMenuItem = textLocator(page, "Agregar Negocio").first();
    await clickAndWaitUi(page, agregarNegocioMenuItem);
    const modalTitle = page.getByRole("heading", { name: "Crear Nuevo Negocio" }).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel("Nombre del Negocio").first()).toBeVisible();
    await expect(textLocator(page, "Tienes 2 de 3 negocios").first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible();

    const businessNameInput = page.getByLabel("Nombre del Negocio").first();
    await businessNameInput.click();
    await businessNameInput.fill(TEST_NEGOCIO_NAME);
    await saveCheckpoint(page, testOutputDir, "03-agregar-negocio-modal.png");
    await clickAndWaitUi(page, page.getByRole("button", { name: "Cancelar" }).first());
    pass(report, "Agregar Negocio modal", "Modal validations passed and optional input/cancel flow executed.");

    // Step 4: Open Administrar Negocios
    await ensureMiNegocioExpanded(page);
    const administrarNegociosMenuItem = textLocator(page, "Administrar Negocios").first();
    await clickAndWaitUi(page, administrarNegociosMenuItem);
    await expect(textLocator(page, "Información General").first()).toBeVisible();
    await expect(textLocator(page, "Detalles de la Cuenta").first()).toBeVisible();
    await expect(textLocator(page, "Tus Negocios").first()).toBeVisible();
    await expect(textLocator(page, "Sección Legal").first()).toBeVisible();
    await saveCheckpoint(page, testOutputDir, "04-administrar-negocios-page.png", true);
    pass(report, "Administrar Negocios view", "Account page and all key sections are visible.");

    // Step 5: Validate Información General
    const infoGeneralSection = page.locator("section, div").filter({ hasText: "Información General" }).first();
    const infoGeneralText = (await infoGeneralSection.textContent()) ?? "";
    expect(infoGeneralText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    expect(infoGeneralText.replace(/\s+/g, " ").trim().length).toBeGreaterThan(40);
    await expect(infoGeneralSection).toContainText("BUSINESS PLAN");
    await expect(infoGeneralSection.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible();
    pass(report, "Información General", "Name/email, plan label, and change plan CTA are visible.");

    // Step 6: Validate Detalles de la Cuenta
    const detallesSection = page.locator("section, div").filter({ hasText: "Detalles de la Cuenta" }).first();
    await expect(detallesSection).toContainText("Cuenta creada");
    await expect(detallesSection).toContainText("Estado activo");
    await expect(detallesSection).toContainText("Idioma seleccionado");
    pass(report, "Detalles de la Cuenta", "All expected account detail labels are visible.");

    // Step 7: Validate Tus Negocios
    const tusNegociosSection = page.locator("section, div").filter({ hasText: "Tus Negocios" }).first();
    await expect(tusNegociosSection).toContainText("Agregar Negocio");
    await expect(tusNegociosSection).toContainText("Tienes 2 de 3 negocios");
    pass(report, "Tus Negocios", "Business list section and quota indicators are visible.");

    // Step 8: Validate Términos y Condiciones
    const termsResult = await openTermsOrPrivacy(
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      testOutputDir,
      "05-terminos-y-condiciones.png",
    );
    legalUrls["Términos y Condiciones"] = termsResult.finalUrl;
    pass(report, "Términos y Condiciones", `Legal page validated. URL: ${termsResult.finalUrl}`);

    // Step 9: Validate Política de Privacidad
    const privacyResult = await openTermsOrPrivacy(
      page,
      "Política de Privacidad",
      "Política de Privacidad",
      testOutputDir,
      "06-politica-de-privacidad.png",
    );
    legalUrls["Política de Privacidad"] = privacyResult.finalUrl;
    pass(report, "Política de Privacidad", `Legal page validated. URL: ${privacyResult.finalUrl}`);
  } catch (error) {
    // Keep failure context in report for final output file.
    const errorMessage = error instanceof Error ? error.message : String(error);
    const firstPending = REPORT_FIELDS.find((field) => report.get(field)?.status !== "PASS");
    if (firstPending) {
      const entry = report.get(firstPending);
      if (entry) {
        entry.status = "FAIL";
        entry.details.push(errorMessage);
      }
    }
    throw error;
  } finally {
    const reportRows = REPORT_FIELDS.map((field) => {
      const entry = report.get(field);
      return {
        field,
        status: entry?.status ?? "FAIL",
        details: entry?.details ?? [],
      };
    });

    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      result: reportRows,
      evidence: {
        screenshotsDir: testOutputDir,
        legalUrls,
      },
    };

    await fs.writeFile(path.join(testOutputDir, "final-report.json"), JSON.stringify(finalReport, null, 2), "utf8");
    // This table is intended for CI logs and quick human visibility.
    // eslint-disable-next-line no-console
    console.table(reportRows);
    // eslint-disable-next-line no-console
    console.log("Legal URLs:", legalUrls);
  }
});
