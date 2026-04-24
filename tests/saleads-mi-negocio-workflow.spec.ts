import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepReport = {
  status: StepStatus;
  details: string[];
};

type FinalReport = Record<
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad",
  StepReport
>;

const ARTIFACTS_DIR = process.env.SALEADS_ARTIFACTS_DIR?.trim() || path.join("test-results", "saleads-mi-negocio");
const SCREENSHOT_ROOT = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_OUTPUT_PATH = path.join(ARTIFACTS_DIR, "final-report.json");
const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT?.trim() || "juanlucasbarbiergarzon@gmail.com";

function createInitialReport(): FinalReport {
  return {
    Login: { status: "FAIL", details: [] },
    "Mi Negocio menu": { status: "FAIL", details: [] },
    "Agregar Negocio modal": { status: "FAIL", details: [] },
    "Administrar Negocios view": { status: "FAIL", details: [] },
    "Información General": { status: "FAIL", details: [] },
    "Detalles de la Cuenta": { status: "FAIL", details: [] },
    "Tus Negocios": { status: "FAIL", details: [] },
    "Términos y Condiciones": { status: "FAIL", details: [] },
    "Política de Privacidad": { status: "FAIL", details: [] },
  };
}

async function waitForUiAfterClick(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(750);
}

async function clickFirstVisible(page: Page, candidates: Locator[]): Promise<Locator | null> {
  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      await locator.first().click();
      await waitForUiAfterClick(page);
      return locator.first();
    }
  }

  return null;
}

function markPass(report: FinalReport, field: keyof FinalReport, detail: string): void {
  report[field].status = "PASS";
  report[field].details.push(detail);
}

function addDetail(report: FinalReport, field: keyof FinalReport, detail: string): void {
  report[field].details.push(detail);
}

async function saveCheckpoint(page: Page, name: string, fullPage = false): Promise<void> {
  fs.mkdirSync(SCREENSHOT_ROOT, { recursive: true });
  await page.screenshot({
    path: path.join(SCREENSHOT_ROOT, `${name}.png`),
    fullPage,
  });
}

async function findSidebar(page: Page): Promise<Locator> {
  const candidates = [
    page.getByRole("navigation"),
    page.locator("aside"),
    page.getByTestId("sidebar"),
    page.locator('[class*="sidebar" i]'),
  ];

  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }

  throw new Error("No visible sidebar navigation found after login.");
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarNegocio = page.getByText("Agregar Negocio", { exact: true });
  if (await agregarNegocio.first().isVisible().catch(() => false)) {
    return;
  }

  const negocioTrigger = page
    .getByText("Mi Negocio", { exact: true })
    .or(page.getByText("Negocio", { exact: true }))
    .first();

  await expect(negocioTrigger, "Expected Negocio/Mi Negocio trigger in sidebar.").toBeVisible();
  await negocioTrigger.click();
  await waitForUiAfterClick(page);
}

async function openLegalLinkAndValidate(
  page: Page,
  context: BrowserContext,
  linkText: string,
  headingText: string,
): Promise<{ finalUrl: string }> {
  const linkLocator = page.getByRole("link", { name: linkText }).first();
  await expect(linkLocator, `Expected legal link "${linkText}" to be visible.`).toBeVisible();

  const popupPromise = context.waitForEvent("page", { timeout: 5_000 }).catch(() => null);
  await linkLocator.click();

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await expect(popup.getByRole("heading", { name: headingText }).first()).toBeVisible();
    const content = popup.locator("main, article, body");
    await expect(content).toContainText(/\S+/);
    await saveCheckpoint(popup, linkText.toLowerCase().replace(/\s+/g, "-"));
    const finalUrl = popup.url();
    await popup.close();
    await page.bringToFront();
    await waitForUiAfterClick(page);
    return { finalUrl };
  }

  await page.waitForLoadState("domcontentloaded");
  await expect(page.getByRole("heading", { name: headingText }).first()).toBeVisible();
  const content = page.locator("main, article, body");
  await expect(content).toContainText(/\S+/);
  await saveCheckpoint(page, linkText.toLowerCase().replace(/\s+/g, "-"));
  const finalUrl = page.url();
  await page.goBack().catch(async () => {
    await page.reload();
  });
  await waitForUiAfterClick(page);
  return { finalUrl };
}

async function selectGoogleAccountIfPresent(page: Page): Promise<boolean> {
  const accountByText = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
  if (await accountByText.isVisible().catch(() => false)) {
    await accountByText.click();
    await waitForUiAfterClick(page);
    return true;
  }

  const accountByRole = page.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }).first();
  if (await accountByRole.isVisible().catch(() => false)) {
    await accountByRole.click();
    await waitForUiAfterClick(page);
    return true;
  }

  return false;
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("login with google and validate Mi Negocio module flow", async ({ page, context }) => {
    const report = createInitialReport();

    try {
      const baseUrl = process.env.SALEADS_BASE_URL?.trim();
      if (baseUrl) {
        await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
        await page.waitForTimeout(750);
      }

      // Step 1: Login with Google
      const popupPromise = context.waitForEvent("page", { timeout: 5_000 }).catch(() => null);
      const loginButton = await clickFirstVisible(page, [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /iniciar sesión con google/i }),
        page.getByRole("button", { name: /continuar con google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/iniciar sesión con google/i),
        page.getByText(/continuar con google/i),
        page.getByRole("button", { name: /google/i }),
      ]);
      expect(loginButton, "No Google login button was visible.").not.toBeNull();

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await selectGoogleAccountIfPresent(popup);
        await popup.waitForLoadState("domcontentloaded").catch(() => {});
        await page.bringToFront();
      } else {
        await selectGoogleAccountIfPresent(page);
      }

      await expect(page.locator("body")).toBeVisible();
      await findSidebar(page);

      await saveCheckpoint(page, "01-dashboard-loaded");
      markPass(report, "Login", "Main interface and left sidebar are visible after Google sign-in.");

      // Step 2: Open Mi Negocio menu
      const sidebar = await findSidebar(page);
      await expect(sidebar).toBeVisible();

      await ensureMiNegocioExpanded(page);
      await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
      await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();
      await saveCheckpoint(page, "02-mi-negocio-expanded");
      markPass(report, "Mi Negocio menu", "Mi Negocio expanded showing Agregar/Administrar options.");

      // Step 3: Validate Agregar Negocio modal
      await page.getByText("Agregar Negocio", { exact: true }).first().click();
      await waitForUiAfterClick(page);

      const modal = page.getByRole("dialog").first();
      await expect(modal).toBeVisible();
      await expect(modal.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
      await expect(modal.getByLabel("Nombre del Negocio")).toBeVisible();
      await expect(modal.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Cancelar" })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

      await modal.getByLabel("Nombre del Negocio").fill("Negocio Prueba Automatización");
      await saveCheckpoint(page, "03-agregar-negocio-modal");
      await modal.getByRole("button", { name: "Cancelar" }).click();
      await waitForUiAfterClick(page);
      markPass(report, "Agregar Negocio modal", "Crear Nuevo Negocio modal and required controls validated.");

      // Step 4: Open Administrar Negocios view
      await ensureMiNegocioExpanded(page);
      await page.getByText("Administrar Negocios", { exact: true }).first().click();
      await waitForUiAfterClick(page);

      await expect(page.getByText("Información General", { exact: true })).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
      await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible();
      await saveCheckpoint(page, "04-administrar-negocios", true);
      markPass(report, "Administrar Negocios view", "All key sections are visible in Administrar Negocios.");

      // Step 5: Validate Informacion General
      const infoSection = page.getByText("Información General", { exact: true }).first().locator("..");
      await expect(infoSection).toContainText(/@/);
      await expect(infoSection).toContainText("BUSINESS PLAN");
      await expect(infoSection.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();
      markPass(report, "Información General", "User info, email, plan text and Cambiar Plan button are visible.");

      // Step 6: Validate Detalles de la Cuenta
      const detailsSection = page.getByText("Detalles de la Cuenta", { exact: true }).first().locator("..");
      await expect(detailsSection).toContainText("Cuenta creada");
      await expect(detailsSection).toContainText("Estado activo");
      await expect(detailsSection).toContainText("Idioma seleccionado");
      markPass(report, "Detalles de la Cuenta", "Account details labels are present.");

      // Step 7: Validate Tus Negocios
      const businessSection = page.getByText("Tus Negocios", { exact: true }).first().locator("..");
      await expect(businessSection).toContainText("Agregar Negocio");
      await expect(businessSection).toContainText("Tienes 2 de 3 negocios");
      await expect(businessSection.locator("li, [role='row'], [class*='business' i']").first()).toBeVisible();
      markPass(report, "Tus Negocios", "Business list, add button, and business limit text are visible.");

      // Step 8: Validate Terminos y Condiciones
      const terms = await openLegalLinkAndValidate(page, context, "Términos y Condiciones", "Términos y Condiciones");
      addDetail(report, "Términos y Condiciones", `Final URL: ${terms.finalUrl}`);
      markPass(report, "Términos y Condiciones", "Terms page heading and legal text validated.");

      // Step 9: Validate Politica de Privacidad
      const privacy = await openLegalLinkAndValidate(page, context, "Política de Privacidad", "Política de Privacidad");
      addDetail(report, "Política de Privacidad", `Final URL: ${privacy.finalUrl}`);
      markPass(report, "Política de Privacidad", "Privacy page heading and legal text validated.");
    } finally {
      fs.mkdirSync(path.dirname(REPORT_OUTPUT_PATH), { recursive: true });
      fs.writeFileSync(REPORT_OUTPUT_PATH, JSON.stringify(report, null, 2), "utf8");
      test.info().attach("saleads-mi-negocio-report", {
        path: REPORT_OUTPUT_PATH,
        contentType: "application/json",
      });
    }
  });
});
