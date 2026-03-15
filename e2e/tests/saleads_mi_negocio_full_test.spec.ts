import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

const GOOGLE_ACCOUNT_EMAIL = process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL ?? "";

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

type ReportStatus = "PASS" | "FAIL";

function toRegex(text: string): RegExp {
  const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(escaped, "i");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
  await page.waitForTimeout(400);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return null;
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const matcher = toRegex(text);
  const locator = await firstVisible([
    page.getByRole("button", { name: matcher }),
    page.getByRole("link", { name: matcher }),
    page.getByRole("menuitem", { name: matcher }),
    page.getByText(matcher)
  ]);

  if (!locator) {
    throw new Error(`Unable to find clickable element with text: ${text}`);
  }

  await locator.click();
  await waitForUi(page);
}

async function captureScreenshot(page: Page, dir: string, name: string, fullPage = false): Promise<string> {
  const screenshotPath = path.join(dir, `${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

function initializeReport(): Record<ReportField, ReportStatus> {
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

function hasLikelyUserName(textContent: string): boolean {
  const candidates = textContent
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length >= 3);

  return candidates.some((line) => {
    const normalized = line.toLowerCase();
    const isLabel =
      normalized.includes("información general") ||
      normalized.includes("business plan") ||
      normalized.includes("cambiar plan") ||
      normalized.includes("cuenta creada") ||
      normalized.includes("estado activo") ||
      normalized.includes("idioma seleccionado") ||
      normalized.includes("tienes 2 de 3 negocios");
    const isEmail = /@/.test(line);
    const looksLikeName = /^[\p{L}][\p{L}\s.'-]{1,}$/u.test(line);
    return !isLabel && !isEmail && looksLikeName;
  });
}

async function selectGoogleAccountIfShown(page: Page): Promise<void> {
  const accountMatcher = toRegex(GOOGLE_ACCOUNT_EMAIL);
  const accountOption = await firstVisible([
    page.getByRole("button", { name: accountMatcher }),
    page.getByRole("link", { name: accountMatcher }),
    page.getByText(accountMatcher)
  ]);

  if (accountOption) {
    await accountOption.click();
    await waitForUi(page);
  }
}

async function clickMiNegocio(page: Page): Promise<void> {
  const locator = await firstVisible([
    page.getByRole("link", { name: /mi negocio/i }),
    page.getByRole("button", { name: /mi negocio/i }),
    page.getByRole("menuitem", { name: /mi negocio/i }),
    page.getByText(/mi negocio/i),
    page.getByText(/^negocio$/i)
  ]);

  if (!locator) {
    throw new Error("No fue posible ubicar 'Mi Negocio' en el menú lateral.");
  }

  await locator.click();
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = initializeReport();
  const errors: string[] = [];
  const evidence: Record<string, string> = {};
  const legalUrls: Record<string, string> = {};

  const runScreenshotsDir = path.join(process.cwd(), "screenshots", `${Date.now()}`);
  await fs.mkdir(runScreenshotsDir, { recursive: true });

  if (page.url() === "about:blank") {
    if (!SALEADS_LOGIN_URL) {
      throw new Error(
        "No login page available. Set SALEADS_LOGIN_URL (or BASE_URL) to run this test in any SaleADS environment."
      );
    }

    await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);

  // Step 1: Login with Google
  try {
    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    const loginTrigger = await firstVisible([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i)
    ]);

    if (!loginTrigger) {
      throw new Error("No se encontró botón de login con Google.");
    }

    await loginTrigger.click();
    await waitForUi(page);

    const maybePopup = await popupPromise;
    if (maybePopup) {
      await maybePopup.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfShown(maybePopup);
      await maybePopup.close().catch(() => undefined);
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await selectGoogleAccountIfShown(page);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
    await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({ timeout: 60000 });

    evidence["dashboard"] = await captureScreenshot(page, runScreenshotsDir, "01-dashboard-loaded");
    report.Login = "PASS";
  } catch (error) {
    errors.push(`Login: ${String(error)}`);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await clickMiNegocio(page);
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

    evidence["mi_negocio_menu"] = await captureScreenshot(page, runScreenshotsDir, "02-mi-negocio-menu-expanded");
    report["Mi Negocio menu"] = "PASS";
  } catch (error) {
    errors.push(`Mi Negocio menu: ${String(error)}`);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await clickByVisibleText(page, "Agregar Negocio");
    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    evidence["agregar_negocio_modal"] = await captureScreenshot(page, runScreenshotsDir, "03-agregar-negocio-modal");

    // Optional action requested in workflow.
    await page.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatizacion");
    await clickByVisibleText(page, "Cancelar");
    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeHidden();

    report["Agregar Negocio modal"] = "PASS";
  } catch (error) {
    errors.push(`Agregar Negocio modal: ${String(error)}`);
  }

  // Step 4: Open Administrar Negocios
  try {
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      await clickMiNegocio(page);
    }

    await clickByVisibleText(page, "Administrar Negocios");
    await expect(page.getByText(/información general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/sección legal/i).first()).toBeVisible();

    evidence["administrar_negocios"] = await captureScreenshot(
      page,
      runScreenshotsDir,
      "04-administrar-negocios",
      true
    );
    report["Administrar Negocios view"] = "PASS";
  } catch (error) {
    errors.push(`Administrar Negocios view: ${String(error)}`);
  }

  // Step 5: Validate Información General
  try {
    await expect(page.getByText(/información general/i).first()).toBeVisible();
    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const bodyText = await page.locator("body").innerText();
    expect(hasLikelyUserName(bodyText)).toBeTruthy();

    report["Información General"] = "PASS";
  } catch (error) {
    errors.push(`Información General: ${String(error)}`);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();

    report["Detalles de la Cuenta"] = "PASS";
  } catch (error) {
    errors.push(`Detalles de la Cuenta: ${String(error)}`);
  }

  // Step 7: Validate Tus Negocios
  try {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();

    const businessListVisible = await firstVisible([
      page.locator("table"),
      page.getByRole("list"),
      page.locator("[data-testid*='business']"),
      page.locator("div").filter({ hasText: /tus negocios/i })
    ]);
    expect(Boolean(businessListVisible)).toBeTruthy();

    report["Tus Negocios"] = "PASS";
  } catch (error) {
    errors.push(`Tus Negocios: ${String(error)}`);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickByVisibleText(page, "Términos y Condiciones");
    const legalPage = (await popupPromise) ?? page;

    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUi(legalPage);
    await expect(legalPage.getByText(/términos y condiciones/i).first()).toBeVisible();

    const legalText = (await legalPage.locator("body").innerText()).trim();
    expect(legalText.length).toBeGreaterThan(120);

    evidence["terminos"] = await captureScreenshot(legalPage, runScreenshotsDir, "05-terminos-y-condiciones");
    legalUrls["Términos y Condiciones"] = legalPage.url();

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack().catch(() => undefined);
      await waitForUi(page);
    }

    report["Términos y Condiciones"] = "PASS";
  } catch (error) {
    errors.push(`Términos y Condiciones: ${String(error)}`);
  }

  // Step 9: Validate Política de Privacidad
  try {
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickByVisibleText(page, "Política de Privacidad");
    const legalPage = (await popupPromise) ?? page;

    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUi(legalPage);
    await expect(legalPage.getByText(/política de privacidad/i).first()).toBeVisible();

    const legalText = (await legalPage.locator("body").innerText()).trim();
    expect(legalText.length).toBeGreaterThan(120);

    evidence["privacidad"] = await captureScreenshot(legalPage, runScreenshotsDir, "06-politica-de-privacidad");
    legalUrls["Política de Privacidad"] = legalPage.url();

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack().catch(() => undefined);
      await waitForUi(page);
    }

    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    errors.push(`Política de Privacidad: ${String(error)}`);
  }

  // Step 10: Final report
  const reportRows = Object.entries(report).map(([field, status]) => ({ field, status }));
  console.table(reportRows);
  console.log("Legal URLs:", legalUrls);
  console.log("Screenshot folder:", runScreenshotsDir);
  if (errors.length > 0) {
    console.log("Validation errors:", errors);
  }

  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: Buffer.from(
      JSON.stringify(
        {
          report,
          legalUrls,
          evidence,
          screenshotFolder: runScreenshotsDir,
          errors
        },
        null,
        2
      )
    ),
    contentType: "application/json"
  });

  expect(Object.values(report).every((status) => status === "PASS")).toBeTruthy();
});
