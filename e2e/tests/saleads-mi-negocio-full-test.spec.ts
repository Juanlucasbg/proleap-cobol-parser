import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type ReportStatus = "PASS" | "FAIL";
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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
const UI_SETTLE_MS = 900;
const UI_TIMEOUT_MS = 20000;

function buildInitialReport(): Record<ReportField, ReportStatus> {
  return REPORT_FIELDS.reduce<Record<ReportField, ReportStatus>>(
    (accumulator, key) => {
      accumulator[key] = "FAIL";
      return accumulator;
    },
    {} as Record<ReportField, ReportStatus>,
  );
}

function sanitizeFileNameSegment(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await page.waitForTimeout(UI_SETTLE_MS);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible({ timeout: UI_TIMEOUT_MS });
  await locator.click();
  await waitForUiToSettle(page);
}

async function firstVisibleLocator(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const target = candidate.first();
    const visible = await target.isVisible().catch(() => false);
    if (visible) {
      return target;
    }
  }

  return null;
}

async function captureCheckpoint(
  page: Page,
  checkpointName: string,
  screenshotPaths: string[],
): Promise<void> {
  const screenshotsDir = path.resolve(__dirname, "..", "artifacts", "screenshots");
  await fs.mkdir(screenshotsDir, { recursive: true });

  const fileName = `${Date.now()}-${sanitizeFileNameSegment(checkpointName)}.png`;
  const filePath = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: filePath, fullPage: true });
  screenshotPaths.push(filePath);
}

async function ensureTextVisible(page: Page, expression: RegExp): Promise<void> {
  const heading = page.getByRole("heading", { name: expression }).first();
  const isHeadingVisible = await heading.isVisible().catch(() => false);

  if (isHeadingVisible) {
    await expect(heading).toBeVisible({ timeout: UI_TIMEOUT_MS });
    return;
  }

  await expect(page.getByText(expression).first()).toBeVisible({
    timeout: UI_TIMEOUT_MS,
  });
}

async function chooseGoogleAccountIfShown(googlePage: Page): Promise<void> {
  const accountOption = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  const shouldSelectAccount = await accountOption.isVisible().catch(() => false);

  if (shouldSelectAccount) {
    await accountOption.click();
    await waitForUiToSettle(googlePage);
  }
}

async function markStep(
  report: Record<ReportField, ReportStatus>,
  failures: string[],
  field: ReportField,
  runner: () => Promise<void>,
): Promise<void> {
  try {
    await runner();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    const reason = error instanceof Error ? error.message : String(error);
    failures.push(`${field}: ${reason}`);
  }
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
  const report = buildInitialReport();
  const failures: string[] = [];
  const screenshotPaths: string[] = [];
  const legalUrls: Record<string, string> = {};
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;

  test.skip(
    !loginUrl,
    "Set SALEADS_LOGIN_URL (preferred) or SALEADS_BASE_URL for environment-agnostic execution.",
  );

  await page.goto(loginUrl as string, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);

  await markStep(report, failures, "Login", async () => {
    const sidebarHint = page.getByText(/mi negocio|negocio/i).first();
    const alreadyInApp = await sidebarHint.isVisible().catch(() => false);

    if (!alreadyInApp) {
      const googleButton = await firstVisibleLocator([
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.locator("button, a, [role='button']").filter({ hasText: /google/i }),
      ]);

      expect(googleButton, "Google login trigger is required").not.toBeNull();

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(googleButton as Locator, page);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await chooseGoogleAccountIfShown(popup);
      } else {
        await chooseGoogleAccountIfShown(page);
      }
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
    await expect(page.getByText(/negocio/i).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
    await captureCheckpoint(page, "dashboard-loaded", screenshotPaths);
  });

  await markStep(report, failures, "Mi Negocio menu", async () => {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    const miNegocio = page.getByText(/^Mi Negocio$/i).first();

    if (await negocioSection.isVisible().catch(() => false)) {
      await clickAndWait(negocioSection, page);
    }

    await clickAndWait(miNegocio, page);
    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
    await captureCheckpoint(page, "mi-negocio-menu-expanded", screenshotPaths);
  });

  await markStep(report, failures, "Agregar Negocio modal", async () => {
    await clickAndWait(page.getByText(/^Agregar Negocio$/i).first(), page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });

    await captureCheckpoint(page, "agregar-negocio-modal", screenshotPaths);

    const nombreNegocioInput = page.getByLabel(/Nombre del Negocio/i).first();
    await nombreNegocioInput.click();
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /Cancelar/i }).first(), page);
  });

  await markStep(report, failures, "Administrar Negocios view", async () => {
    const administrarNegocios = page.getByText(/^Administrar Negocios$/i).first();
    const optionVisible = await administrarNegocios.isVisible().catch(() => false);

    if (!optionVisible) {
      await clickAndWait(page.getByText(/^Mi Negocio$/i).first(), page);
    }

    await clickAndWait(page.getByText(/^Administrar Negocios$/i).first(), page);
    await ensureTextVisible(page, /Informaci[oó]n General/i);
    await ensureTextVisible(page, /Detalles de la Cuenta/i);
    await ensureTextVisible(page, /Tus Negocios/i);
    await ensureTextVisible(page, /Secci[oó]n Legal/i);
    await captureCheckpoint(page, "administrar-negocios-view", screenshotPaths);
  });

  await markStep(report, failures, "Información General", async () => {
    const userNameHint = page.locator("h1, h2, h3, p, span, div").filter({
      hasText: /@|usuario|bienvenido|hola/i,
    });
    await expect(userNameHint.first()).toBeVisible({ timeout: UI_TIMEOUT_MS });
    await expect(page.getByText(/@/).first()).toBeVisible({ timeout: UI_TIMEOUT_MS });
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
  });

  await markStep(report, failures, "Detalles de la Cuenta", async () => {
    await ensureTextVisible(page, /Cuenta creada/i);
    await ensureTextVisible(page, /Estado activo/i);
    await ensureTextVisible(page, /Idioma seleccionado/i);
  });

  await markStep(report, failures, "Tus Negocios", async () => {
    await ensureTextVisible(page, /Tus Negocios/i);
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });
    await ensureTextVisible(page, /Tienes\s*2\s*de\s*3\s*negocios/i);
  });

  await markStep(report, failures, "Términos y Condiciones", async () => {
    const termsLink = page.getByRole("link", { name: /T[eé]rminos y Condiciones/i }).first();
    await expect(termsLink).toBeVisible({ timeout: UI_TIMEOUT_MS });

    const appUrlBeforeClick = page.url();
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await termsLink.click();
    await waitForUiToSettle(page);

    const popup = await popupPromise;
    const termsPage = popup ?? page;
    await termsPage.waitForLoadState("domcontentloaded");
    await ensureTextVisible(termsPage, /T[eé]rminos y Condiciones/i);
    await expect(termsPage.locator("p, li, article, section").first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });

    legalUrls["Términos y Condiciones"] = termsPage.url();
    await captureCheckpoint(termsPage, "terminos-y-condiciones", screenshotPaths);

    if (popup) {
      await popup.close();
    } else if (page.url() !== appUrlBeforeClick) {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }
  });

  await markStep(report, failures, "Política de Privacidad", async () => {
    const privacyLink = page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }).first();
    await expect(privacyLink).toBeVisible({ timeout: UI_TIMEOUT_MS });

    const appUrlBeforeClick = page.url();
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await privacyLink.click();
    await waitForUiToSettle(page);

    const popup = await popupPromise;
    const privacyPage = popup ?? page;
    await privacyPage.waitForLoadState("domcontentloaded");
    await ensureTextVisible(privacyPage, /Pol[ií]tica de Privacidad/i);
    await expect(privacyPage.locator("p, li, article, section").first()).toBeVisible({
      timeout: UI_TIMEOUT_MS,
    });

    legalUrls["Política de Privacidad"] = privacyPage.url();
    await captureCheckpoint(privacyPage, "politica-de-privacidad", screenshotPaths);

    if (popup) {
      await popup.close();
    } else if (page.url() !== appUrlBeforeClick) {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }
  });

  const artifactsDir = path.resolve(__dirname, "..", "artifacts", "reports");
  await fs.mkdir(artifactsDir, { recursive: true });
  const reportPath = path.join(artifactsDir, "saleads-mi-negocio-final-report.json");

  await fs.writeFile(
    reportPath,
    JSON.stringify(
      {
        name: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        environment: {
          loginUrl,
        },
        results: report,
        legalUrls,
        screenshots: screenshotPaths,
        failures,
      },
      null,
      2,
    ),
    "utf-8",
  );

  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  expect(
    failures,
    `One or more validation groups failed:\n${failures.join("\n")}\nReport: ${reportPath}`,
  ).toEqual([]);
});
