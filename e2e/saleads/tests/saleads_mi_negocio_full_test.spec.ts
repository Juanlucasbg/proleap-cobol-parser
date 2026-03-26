import { expect, Page, test } from "@playwright/test";
import path from "node:path";
import fs from "node:fs";

const DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const SCREENSHOT_DIR = path.join(process.cwd(), "test-results", "screenshots");
const REPORT_PATH = path.join(process.cwd(), "test-results", "mi-negocio-final-report.json");

type Status = "PASS" | "FAIL";

type Report = {
  [key: string]: {
    status: Status;
    details: string[];
  };
};

async function waitForUiIdle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(800);
}

async function waitForAnyVisible(page: Page, selectors: string[], timeout = 15000) {
  const start = Date.now();
  const end = start + timeout;

  while (Date.now() < end) {
    for (const selector of selectors) {
      const locator = page.locator(selector).first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`None of the selectors became visible: ${selectors.join(", ")}`);
}

async function clickByVisibleText(page: Page, label: string): Promise<void> {
  const candidates = [
    page.getByRole("button", { name: new RegExp(`^\\s*${escapeRegex(label)}\\s*$`, "i") }),
    page.getByRole("link", { name: new RegExp(`^\\s*${escapeRegex(label)}\\s*$`, "i") }),
    page.getByText(new RegExp(`^\\s*${escapeRegex(label)}\\s*$`, "i")),
  ];

  for (const candidate of candidates) {
    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      await first.click();
      return;
    }
  }

  throw new Error(`Unable to find visible clickable element with label "${label}"`);
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function safeScreenshot(page: Page, filename: string, fullPage = false): Promise<void> {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, filename),
    fullPage,
  });
}

function initReport(): Report {
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

function markPass(report: Report, field: keyof Report, message: string): void {
  report[field].status = "PASS";
  report[field].details.push(message);
}

function markFail(report: Report, field: keyof Report, message: string): void {
  report[field].status = "FAIL";
  report[field].details.push(message);
}

async function expandMiNegocioMenu(page: Page): Promise<void> {
  const miNegocio = page.getByText(/^Mi Negocio$/i).first();

  if (await miNegocio.isVisible().catch(() => false)) {
    await miNegocio.click();
    await waitForUiIdle(page);
    return;
  }

  const negocio = page.getByText(/^Negocio$/i).first();
  if (await negocio.isVisible().catch(() => false)) {
    await negocio.click();
    await waitForUiIdle(page);
  }

  await clickByVisibleText(page, "Mi Negocio");
  await waitForUiIdle(page);
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = initReport();
  const accountEmail = process.env.SALEADS_GOOGLE_EMAIL ?? DEFAULT_ACCOUNT_EMAIL;
  const accountPassword = process.env.SALEADS_GOOGLE_PASSWORD;

  try {
    // Step 1: Login with Google and validate app shell.
    const currentUrl = page.url();
    if (!currentUrl || currentUrl === "about:blank") {
      if (!process.env.SALEADS_BASE_URL) {
        throw new Error(
          "Page is about:blank and SALEADS_BASE_URL is not set. Set SALEADS_BASE_URL to the current environment login URL."
        );
      }
      await page.goto("/", { waitUntil: "domcontentloaded" });
    }
    await waitForUiIdle(page);

    const googleLoginEntry = await waitForAnyVisible(page, [
      'button:has-text("Sign in with Google")',
      'button:has-text("Iniciar con Google")',
      'button:has-text("Google")',
      'a:has-text("Sign in with Google")',
      'a:has-text("Iniciar con Google")',
      'text=/sign\\s*in\\s*with\\s*google/i',
      'text=/iniciar\\s*con\\s*google/i',
    ]);

    const loginPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await googleLoginEntry.click();
    const popup =
      (await loginPopupPromise) ??
      context.pages().find((candidate) => candidate !== page && /accounts\.google\.com/i.test(candidate.url()));

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountChoice = popup.getByText(new RegExp(escapeRegex(accountEmail), "i")).first();
      if (await accountChoice.isVisible().catch(() => false)) {
        await accountChoice.click();
      }
      await popup.waitForTimeout(1000);
      if (/accounts\.google\.com/i.test(popup.url()) && accountPassword) {
        const passwordInput = popup.locator('input[type="password"]').first();
        if (await passwordInput.isVisible().catch(() => false)) {
          await passwordInput.fill(accountPassword);
        }
      }
      if (/accounts\.google\.com/i.test(popup.url())) {
        const continueButton = popup.getByRole("button", { name: /continuar|continue|siguiente|next|iniciar sesión|sign in/i }).first();
        if (await continueButton.isVisible().catch(() => false)) {
          await continueButton.click();
        }
      }
    } else {
      const inlineChoice = page.getByText(new RegExp(escapeRegex(accountEmail), "i")).first();
      if (await inlineChoice.isVisible().catch(() => false)) {
        await inlineChoice.click();
      }
    }

    await waitForUiIdle(page);

    const sidebar = await waitForAnyVisible(page, [
      'nav[aria-label*="sidebar" i]',
      "aside",
      'text=/negocio/i',
    ]);
    await expect(sidebar).toBeVisible();
    await safeScreenshot(page, "01-dashboard-loaded.png", true);
    markPass(report, "Login", "Main application interface and left sidebar are visible.");

    // Step 2: Open Mi Negocio menu and validate options.
    await expandMiNegocioMenu(page);

    const agregarMenu = page.getByText(/^Agregar Negocio$/i).first();
    const administrarMenu = page.getByText(/^Administrar Negocios$/i).first();
    await expect(agregarMenu).toBeVisible();
    await expect(administrarMenu).toBeVisible();
    await safeScreenshot(page, "02-mi-negocio-menu-expanded.png", true);
    markPass(report, "Mi Negocio menu", "Mi Negocio expanded with Agregar/Administrar Negocios options.");

    // Step 3: Validate Agregar Negocio modal.
    await agregarMenu.click();
    await waitForUiIdle(page);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i).first()).toBeVisible();
    const businessNameInput = page
      .locator('input[placeholder*="Nombre del Negocio" i], input[name*="nombre" i], input[id*="nombre" i]')
      .first();
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await safeScreenshot(page, "03-agregar-negocio-modal.png", true);
    await page.getByRole("button", { name: /^Cancelar$/i }).first().click();
    await waitForUiIdle(page);
    markPass(report, "Agregar Negocio modal", "Modal content validated and closed with Cancelar.");

    // Step 4: Open Administrar Negocios and validate sections.
    if (!(await administrarMenu.isVisible().catch(() => false))) {
      await expandMiNegocioMenu(page);
    }
    await clickByVisibleText(page, "Administrar Negocios");
    await waitForUiIdle(page);

    const infoGeneral = page.getByText(/^Información General$/i).first();
    const detallesCuenta = page.getByText(/^Detalles de la Cuenta$/i).first();
    const tusNegocios = page.getByText(/^Tus Negocios$/i).first();
    const seccionLegal = page.getByText(/^Sección Legal$/i).first();
    await expect(infoGeneral).toBeVisible();
    await expect(detallesCuenta).toBeVisible();
    await expect(tusNegocios).toBeVisible();
    await expect(seccionLegal).toBeVisible();
    await safeScreenshot(page, "04-administrar-negocios-page.png", true);
    markPass(report, "Administrar Negocios view", "All required account sections are visible.");

    // Step 5: Validate Informacion General.
    await expect(
      page.locator('section:has-text("Información General"), div:has-text("Información General")').first()
    ).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cambiar Plan$/i }).first()).toBeVisible();

    const userIdentityVisible =
      (await page.getByText(/@/).first().isVisible().catch(() => false)) ||
      (await page.locator('[data-testid*="email" i], [class*="email" i]').first().isVisible().catch(() => false));
    expect(userIdentityVisible).toBeTruthy();
    markPass(report, "Información General", "Name/email identity area, BUSINESS PLAN, and Cambiar Plan are visible.");

    // Step 6: Validate Detalles de la Cuenta.
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    markPass(report, "Detalles de la Cuenta", "Account detail labels are visible.");

    // Step 7: Validate Tus Negocios.
    const negociosSection = page.locator('section:has-text("Tus Negocios"), div:has-text("Tus Negocios")').first();
    await expect(negociosSection).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    markPass(report, "Tus Negocios", "Business list section, add button, and business quota text are visible.");

    // Step 8: Validate Terms and Conditions (same tab or new tab).
    const termsLink = page.getByRole("link", { name: /T[eé]rminos y Condiciones/i }).first();
    await expect(termsLink).toBeVisible();

    const pageBeforeTerms = page;
    const termsPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await termsLink.click();
    const termsPopup = await termsPopupPromise;
    const termsPage = termsPopup ?? pageBeforeTerms;
    await termsPage.waitForLoadState("domcontentloaded");
    await termsPage.waitForLoadState("networkidle");
    await expect(termsPage.getByText(/T[eé]rminos y Condiciones/i).first()).toBeVisible();
    await expect(termsPage.locator("main, article, body").first()).toContainText(/\w+/);
    await safeScreenshot(termsPage, "05-terminos-y-condiciones.png", true);
    markPass(report, "Términos y Condiciones", `Validated legal page. Final URL: ${termsPage.url()}`);

    if (termsPopup) {
      await termsPopup.close();
      await pageBeforeTerms.bringToFront();
      await waitForUiIdle(pageBeforeTerms);
    } else if (termsPage.url() !== pageBeforeTerms.url()) {
      await pageBeforeTerms.goBack();
      await waitForUiIdle(pageBeforeTerms);
    }

    // Step 9: Validate Privacy Policy (same tab or new tab).
    const privacyLink = page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }).first();
    await expect(privacyLink).toBeVisible();

    const pageBeforePrivacy = page;
    const privacyPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await privacyLink.click();
    const privacyPopup = await privacyPopupPromise;
    const privacyPage = privacyPopup ?? pageBeforePrivacy;
    await privacyPage.waitForLoadState("domcontentloaded");
    await privacyPage.waitForLoadState("networkidle");
    await expect(privacyPage.getByText(/Pol[ií]tica de Privacidad/i).first()).toBeVisible();
    await expect(privacyPage.locator("main, article, body").first()).toContainText(/\w+/);
    await safeScreenshot(privacyPage, "06-politica-de-privacidad.png", true);
    markPass(report, "Política de Privacidad", `Validated legal page. Final URL: ${privacyPage.url()}`);

    if (privacyPopup) {
      await privacyPopup.close();
      await pageBeforePrivacy.bringToFront();
      await waitForUiIdle(pageBeforePrivacy);
    } else if (privacyPage.url() !== pageBeforePrivacy.url()) {
      await pageBeforePrivacy.goBack();
      await waitForUiIdle(pageBeforePrivacy);
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    testInfo.attach("fatal-error", {
      body: Buffer.from(message, "utf-8"),
      contentType: "text/plain",
    });

    for (const key of Object.keys(report) as Array<keyof Report>) {
      if (report[key].details.length === 0) {
        markFail(report, key, `Not reached due to upstream failure: ${message}`);
      }
    }

    throw error;
  } finally {
    fs.mkdirSync(path.dirname(REPORT_PATH), { recursive: true });
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf-8");

    testInfo.attach("final-report", {
      body: Buffer.from(JSON.stringify(report, null, 2), "utf-8"),
      contentType: "application/json",
    });
  }
});
