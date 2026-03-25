import { expect, test } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

type Status = "PASS" | "FAIL";

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

type StepReport = Record<ReportField, Status>;

const WAIT_TIMEOUT = 25_000;
const DEFAULT_SCREENSHOT_DIR = path.resolve(__dirname, "../reports/screenshots");
const DEFAULT_REPORT_FILE = path.resolve(__dirname, "../reports/mi-negocio-final-report.json");

const REPORT_FIELDS: ReportField[] = [
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

function baseReport(): StepReport {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };
}

async function waitUi(page: import("@playwright/test").Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: WAIT_TIMEOUT });
  await page.waitForTimeout(700);
}

function reportPath(): string {
  return process.env.MI_NEGOCIO_REPORT_FILE ?? DEFAULT_REPORT_FILE;
}

function screenshotDir(): string {
  return process.env.MI_NEGOCIO_SCREENSHOT_DIR ?? DEFAULT_SCREENSHOT_DIR;
}

async function saveShot(
  page: import("@playwright/test").Page,
  name: string,
  fullPage = false,
): Promise<string> {
  const dir = screenshotDir();
  mkdirSync(dir, { recursive: true });
  const target = path.join(dir, `${name}.png`);
  await page.screenshot({ path: target, fullPage });
  return target;
}

async function clickAndWait(
  locator: import("@playwright/test").Locator,
  page: import("@playwright/test").Page,
): Promise<void> {
  await locator.click();
  await waitUi(page);
}

async function clickByVisibleText(
  page: import("@playwright/test").Page,
  text: string,
): Promise<void> {
  const exact = page.getByRole("button", { name: text, exact: true }).first();
  if (await exact.isVisible().catch(() => false)) {
    await clickAndWait(exact, page);
    return;
  }

  const fallback = page.getByText(new RegExp(`^\\s*${text}\\s*$`, "i")).first();
  await expect(fallback).toBeVisible({ timeout: WAIT_TIMEOUT });
  await clickAndWait(fallback, page);
}

async function openMiNegocioMenu(page: import("@playwright/test").Page): Promise<void> {
  const negocio = page.getByText(/^Negocio$/i).first();
  await expect(negocio).toBeVisible({ timeout: WAIT_TIMEOUT });
  await clickAndWait(negocio, page);

  const miNegocio = page.getByText(/^Mi Negocio$/i).first();
  await expect(miNegocio).toBeVisible({ timeout: WAIT_TIMEOUT });
  await clickAndWait(miNegocio, page);
}

async function clickLinkAndValidateLegalPage(
  page: import("@playwright/test").Page,
  linkText: string,
  headingRegex: RegExp,
  shotName: string,
): Promise<{ finalUrl: string; screenshot: string }> {
  const link = page.getByRole("link", { name: new RegExp(linkText, "i") }).first();
  await expect(link).toBeVisible({ timeout: WAIT_TIMEOUT });

  const maybePopup = page.waitForEvent("popup", { timeout: 6_000 }).catch(() => null);
  await clickAndWait(link, page);
  const popup = await maybePopup;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: WAIT_TIMEOUT });
    await expect(popup.getByRole("heading", { name: headingRegex })).toBeVisible({ timeout: WAIT_TIMEOUT });
    const legalText = popup.locator("body");
    await expect(legalText).toContainText(/[A-Za-zÀ-ÿ]{20,}/, { timeout: WAIT_TIMEOUT });
    const screenshot = await saveShot(popup, shotName);
    const finalUrl = popup.url();
    await popup.close();
    await page.bringToFront();
    await waitUi(page);
    return { finalUrl, screenshot };
  }

  await expect(page.getByRole("heading", { name: headingRegex })).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.locator("body")).toContainText(/[A-Za-zÀ-ÿ]{20,}/, { timeout: WAIT_TIMEOUT });
  const screenshot = await saveShot(page, shotName);
  const finalUrl = page.url();
  await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => Promise.resolve());
  await waitUi(page);
  return { finalUrl, screenshot };
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const stepReport = baseReport();
  const evidence: Record<string, string | string[]> = {};
  const legalUrls: Record<string, string> = {};

  const startUrl = process.env.SALEADS_START_URL;
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded", timeout: WAIT_TIMEOUT });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No runtime page is open. Provide SALEADS_START_URL for the target environment or run this test from a pre-opened SaleADS login page session.",
    );
  }
  await waitUi(page);

  // Step 1: Login with Google
  const googleLogin = page
    .getByRole("button", { name: /sign in with google|continuar con google|google/i })
    .first();
  await expect(googleLogin).toBeVisible({ timeout: WAIT_TIMEOUT });
  const maybeGooglePopup = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
  await clickAndWait(googleLogin, page);
  const googlePopup = await maybeGooglePopup;

  if (googlePopup) {
    await googlePopup.waitForLoadState("domcontentloaded", { timeout: WAIT_TIMEOUT });
    const popupAccountOption = googlePopup.getByText(/^juanlucasbarbiergarzon@gmail\.com$/i).first();
    if (await popupAccountOption.isVisible().catch(() => false)) {
      await popupAccountOption.click();
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: WAIT_TIMEOUT }).catch(() => Promise.resolve());
    }
    await googlePopup.waitForEvent("close", { timeout: WAIT_TIMEOUT }).catch(() => Promise.resolve());
    await page.bringToFront();
    await waitUi(page);
  } else {
    const accountOption = page.getByText(/^juanlucasbarbiergarzon@gmail\.com$/i).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await clickAndWait(accountOption, page);
    }
  }

  const sidebar = page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio|Dashboard/i }).first();
  await expect(sidebar).toBeVisible({ timeout: WAIT_TIMEOUT });
  stepReport["Login"] = "PASS";
  evidence.dashboard = await saveShot(page, "01-dashboard-loaded");

  // Step 2: Open Mi Negocio menu
  await openMiNegocioMenu(page);
  await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  stepReport["Mi Negocio menu"] = "PASS";
  evidence.miNegocioMenu = await saveShot(page, "02-mi-negocio-expanded");

  // Step 3: Validate Agregar Negocio modal
  await clickByVisibleText(page, "Agregar Negocio");
  await expect(page.getByText(/^Crear Nuevo Negocio$/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByLabel(/^Nombre del Negocio$/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  evidence.agregarNegocioModal = await saveShot(page, "03-agregar-negocio-modal");

  const nombreNegocio = page.getByLabel(/^Nombre del Negocio$/i).first();
  await clickAndWait(nombreNegocio, page);
  await nombreNegocio.fill("Negocio Prueba Automatización");
  await clickByVisibleText(page, "Cancelar");
  stepReport["Agregar Negocio modal"] = "PASS";

  // Step 4: Open Administrar Negocios
  if (!(await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false))) {
    await openMiNegocioMenu(page);
  }
  await clickByVisibleText(page, "Administrar Negocios");
  await expect(page.getByText(/^Información General$/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  stepReport["Administrar Negocios view"] = "PASS";
  evidence.administrarNegocios = await saveShot(page, "04-administrar-negocios-full", true);

  // Step 5: Validate Información General
  await expect(page.getByText(/@/).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  stepReport["Información General"] = "PASS";

  // Step 6: Validate Detalles de la Cuenta
  await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  stepReport["Detalles de la Cuenta"] = "PASS";

  // Step 7: Validate Tus Negocios
  await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: WAIT_TIMEOUT });
  stepReport["Tus Negocios"] = "PASS";

  // Step 8: Validate Términos y Condiciones
  const termsResult = await clickLinkAndValidateLegalPage(
    page,
    "Términos y Condiciones",
    /Términos y Condiciones/i,
    "05-terminos-y-condiciones",
  );
  legalUrls.terminosYCondiciones = termsResult.finalUrl;
  evidence.terminosYCondiciones = termsResult.screenshot;
  stepReport["Términos y Condiciones"] = "PASS";

  // Step 9: Validate Política de Privacidad
  const privacyResult = await clickLinkAndValidateLegalPage(
    page,
    "Política de Privacidad",
    /Política de Privacidad/i,
    "06-politica-de-privacidad",
  );
  legalUrls.politicaDePrivacidad = privacyResult.finalUrl;
  evidence.politicaDePrivacidad = privacyResult.screenshot;
  stepReport["Política de Privacidad"] = "PASS";

  // Step 10: Final report
  const summary = REPORT_FIELDS.map((name) => ({ step: name, status: stepReport[name] }));
  const finalPayload = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      startUrl: startUrl ?? "provided-by-runtime-session",
      currentUrl: page.url(),
    },
    results: stepReport,
    summary,
    legalUrls,
    evidence,
  };

  const reportFilePath = reportPath();
  mkdirSync(path.dirname(reportFilePath), { recursive: true });
  writeFileSync(reportFilePath, JSON.stringify(finalPayload, null, 2), "utf-8");

  const failures = summary.filter((item) => item.status === "FAIL");
  expect(
    failures,
    `Some requested validations failed. See report at ${reportFilePath}`,
  ).toHaveLength(0);
});
