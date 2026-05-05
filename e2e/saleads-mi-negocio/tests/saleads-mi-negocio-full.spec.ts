import { test, expect, type Page, type BrowserContext } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import dotenv from "dotenv";

dotenv.config();

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

type ReportEntry = {
  status: StepStatus;
  details: string;
};

const OUTPUT_DIR = path.resolve(process.cwd(), "artifacts");
const SCREENSHOT_DIR = path.join(OUTPUT_DIR, "screenshots");
const REPORT_FILE = path.join(OUTPUT_DIR, "saleads_mi_negocio_report.json");
const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";

const TEXT = {
  sidebarNegocio: "Negocio",
  menuMiNegocio: "Mi Negocio",
  menuAgregarNegocio: "Agregar Negocio",
  menuAdministrarNegocios: "Administrar Negocios",
  modalCrearNuevoNegocio: "Crear Nuevo Negocio",
  inputNombreDelNegocio: "Nombre del Negocio",
  negociosUsage: "Tienes 2 de 3 negocios",
  buttonCancelar: "Cancelar",
  buttonCrearNegocio: "Crear Negocio",
  sectionInformacionGeneral: "Información General",
  sectionDetallesCuenta: "Detalles de la Cuenta",
  sectionTusNegocios: "Tus Negocios",
  sectionLegal: "Sección Legal",
  businessPlan: "BUSINESS PLAN",
  cambiarPlan: "Cambiar Plan",
  cuentaCreada: "Cuenta creada",
  estadoActivo: "Estado activo",
  idiomaSeleccionado: "Idioma seleccionado",
  terminos: "Términos y Condiciones",
  privacidad: "Política de Privacidad",
  headingTerminos: "Términos y Condiciones",
  headingPrivacidad: "Política de Privacidad",
  loginGoogleA: "Sign in with Google",
  loginGoogleB: "Iniciar sesión con Google",
  loginGoogleC: "Continue with Google",
  loginGoogleD: "Continuar con Google",
};

function ensureOutputDirs(): void {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

async function waitForUiSettled(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
}

async function screenshot(page: Page, name: string): Promise<void> {
  await waitForUiSettled(page);
  const safe = name.replace(/\s+/g, "_").toLowerCase();
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, `${safe}.png`),
    fullPage: true,
  });
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const candidates = [
    page.getByRole("button", { name: text, exact: true }),
    page.getByRole("link", { name: text, exact: true }),
    page.getByText(text, { exact: true }),
  ];

  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      await locator.first().click();
      await waitForUiSettled(page);
      return;
    }
  }

  throw new Error(`No clickable visible element found with text "${text}"`);
}

async function expectVisibleText(page: Page, text: string): Promise<void> {
  const locators = [
    page.getByRole("heading", { name: text }),
    page.getByText(text, { exact: true }),
    page.getByText(text),
  ];

  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      await expect(locator.first()).toBeVisible();
      return;
    }
  }

  await expect(page.getByText(text)).toBeVisible();
}

function initReport(): Record<ReportKey, ReportEntry> {
  return {
    Login: { status: "FAIL", details: "Not executed" },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed" },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed" },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed" },
    "Información General": { status: "FAIL", details: "Not executed" },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed" },
    "Tus Negocios": { status: "FAIL", details: "Not executed" },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed" },
    "Política de Privacidad": { status: "FAIL", details: "Not executed" },
  };
}

async function maybeSelectGoogleAccount(page: Page): Promise<void> {
  const emailOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
  if (await emailOption.first().isVisible().catch(() => false)) {
    await emailOption.first().click();
    await waitForUiSettled(page);
  }
}

async function clickGoogleLoginAndHandleAuth(page: Page, context: BrowserContext): Promise<void> {
  const loginTexts = [TEXT.loginGoogleA, TEXT.loginGoogleB, TEXT.loginGoogleC, TEXT.loginGoogleD];
  let clickedLogin = false;

  for (const loginText of loginTexts) {
    const candidate = page.getByText(loginText).first();
    if (await candidate.isVisible().catch(() => false)) {
      const popupPromise = context.waitForEvent("page", { timeout: 3000 }).catch(() => null);
      await clickByVisibleText(page, loginText);
      clickedLogin = true;

      const popup = await popupPromise;
      if (popup) {
        await popup.bringToFront();
        await waitForUiSettled(popup);
        await maybeSelectGoogleAccount(popup);
      } else {
        await maybeSelectGoogleAccount(page);
      }
      return;
    }
  }

  if (!clickedLogin) {
    throw new Error("Google login button was not found on page.");
  }
}

async function expectLegalContentVisible(legalPage: Page): Promise<void> {
  const textContent = await legalPage.locator("body").innerText();
  if (!textContent || textContent.trim().length < 80) {
    throw new Error("Legal content text is not visible or too short.");
  }
}

async function openLegalLinkAndValidate(
  context: BrowserContext,
  appPage: Page,
  linkText: string,
  headingText: string,
  screenshotName: string,
): Promise<{ finalUrl: string }> {
  const appPageUrlBefore = appPage.url();
  const existingPages = context.pages().length;

  await clickByVisibleText(appPage, linkText);

  let legalPage: Page = appPage;
  if (context.pages().length > existingPages) {
    legalPage = context.pages()[context.pages().length - 1];
    await legalPage.bringToFront();
    await waitForUiSettled(legalPage);
  } else {
    await appPage.waitForTimeout(700);
    if (appPage.url() !== appPageUrlBefore) {
      legalPage = appPage;
      await waitForUiSettled(legalPage);
    }
  }

  await expectVisibleText(legalPage, headingText);
  await expectLegalContentVisible(legalPage);
  await screenshot(legalPage, screenshotName);

  return { finalUrl: legalPage.url() };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login and validate Mi Negocio module workflow", async ({ page, context }) => {
    ensureOutputDirs();
    const report = initReport();
    const urls: Record<string, string> = {};

    try {
      // Step 1: Login with Google.
      await waitForUiSettled(page);

      await clickGoogleLoginAndHandleAuth(page, context);

      await expectVisibleText(page, TEXT.sidebarNegocio);
      await expectVisibleText(page, TEXT.menuMiNegocio);
      await screenshot(page, "01_dashboard_loaded");
      report.Login = { status: "PASS", details: "Dashboard loaded and sidebar visible." };

      // Step 2: Open Mi Negocio menu.
      await clickByVisibleText(page, TEXT.menuMiNegocio);
      await expectVisibleText(page, TEXT.menuAgregarNegocio);
      await expectVisibleText(page, TEXT.menuAdministrarNegocios);
      await screenshot(page, "02_mi_negocio_expanded");
      report["Mi Negocio menu"] = {
        status: "PASS",
        details: "Mi Negocio submenu expanded with expected options.",
      };

      // Step 3: Validate Agregar Negocio modal.
      await clickByVisibleText(page, TEXT.menuAgregarNegocio);
      await expectVisibleText(page, TEXT.modalCrearNuevoNegocio);
      await expectVisibleText(page, TEXT.inputNombreDelNegocio);
      await expectVisibleText(page, TEXT.negociosUsage);
      await expectVisibleText(page, TEXT.buttonCancelar);
      await expectVisibleText(page, TEXT.buttonCrearNegocio);
      await screenshot(page, "03_agregar_negocio_modal");

      const nombreInput = page.getByLabel(TEXT.inputNombreDelNegocio);
      if (await nombreInput.first().isVisible().catch(() => false)) {
        await nombreInput.fill("Negocio Prueba Automatización");
      } else {
        const fallbackInput = page.getByPlaceholder(TEXT.inputNombreDelNegocio);
        if (await fallbackInput.first().isVisible().catch(() => false)) {
          await fallbackInput.fill("Negocio Prueba Automatización");
        }
      }

      await clickByVisibleText(page, TEXT.buttonCancelar);
      report["Agregar Negocio modal"] = {
        status: "PASS",
        details: "Modal and expected controls validated successfully.",
      };

      // Step 4: Open Administrar Negocios.
      if (!(await page.getByText(TEXT.menuAdministrarNegocios).first().isVisible().catch(() => false))) {
        await clickByVisibleText(page, TEXT.menuMiNegocio);
      }
      await clickByVisibleText(page, TEXT.menuAdministrarNegocios);
      await expectVisibleText(page, TEXT.sectionInformacionGeneral);
      await expectVisibleText(page, TEXT.sectionDetallesCuenta);
      await expectVisibleText(page, TEXT.sectionTusNegocios);
      await expectVisibleText(page, TEXT.sectionLegal);
      await screenshot(page, "04_administrar_negocios_page");
      report["Administrar Negocios view"] = {
        status: "PASS",
        details: "Account page sections are visible.",
      };

      // Step 5: Validate Informacion General.
      // Name validation is environment-dependent; assert at least one text node with letters
      // exists near account information and an email is present.
      const infoSection = page.locator("section,div").filter({ hasText: TEXT.sectionInformacionGeneral }).first();
      if (await infoSection.isVisible().catch(() => false)) {
        await expect(infoSection.getByText(/[A-Za-z][A-Za-z ]{2,}/)).toBeVisible();
      }
      await expect(page.getByText(/@/)).toBeVisible();
      await expectVisibleText(page, TEXT.businessPlan);
      await expectVisibleText(page, TEXT.cambiarPlan);
      report["Información General"] = {
        status: "PASS",
        details: "User identity, plan and action button are visible.",
      };

      // Step 6: Validate Detalles de la Cuenta.
      await expectVisibleText(page, TEXT.cuentaCreada);
      await expectVisibleText(page, TEXT.estadoActivo);
      await expectVisibleText(page, TEXT.idiomaSeleccionado);
      report["Detalles de la Cuenta"] = {
        status: "PASS",
        details: "Account details fields validated.",
      };

      // Step 7: Validate Tus Negocios.
      await expectVisibleText(page, TEXT.sectionTusNegocios);
      await expectVisibleText(page, TEXT.menuAgregarNegocio);
      await expectVisibleText(page, TEXT.negociosUsage);
      report["Tus Negocios"] = {
        status: "PASS",
        details: "Business section, add button and business limit text validated.",
      };

      // Step 8: Validate Términos y Condiciones.
      const termsResult = await openLegalLinkAndValidate(
        context,
        page,
        TEXT.terminos,
        TEXT.headingTerminos,
        "05_terminos_y_condiciones",
      );
      urls[TEXT.terminos] = termsResult.finalUrl;
      report["Términos y Condiciones"] = {
        status: "PASS",
        details: `Legal page opened and validated. URL: ${termsResult.finalUrl}`,
      };

      // Return to app tab if needed.
      if (context.pages().length > 1) {
        await page.bringToFront();
        await waitForUiSettled(page);
      }

      // Step 9: Validate Política de Privacidad.
      const privacyResult = await openLegalLinkAndValidate(
        context,
        page,
        TEXT.privacidad,
        TEXT.headingPrivacidad,
        "06_politica_de_privacidad",
      );
      urls[TEXT.privacidad] = privacyResult.finalUrl;
      report["Política de Privacidad"] = {
        status: "PASS",
        details: `Legal page opened and validated. URL: ${privacyResult.finalUrl}`,
      };

      if (context.pages().length > 1) {
        await page.bringToFront();
        await waitForUiSettled(page);
      }
    } catch (error) {
      await screenshot(page, "error_checkpoint").catch(() => undefined);
      throw error;
    } finally {
      const payload = {
        test: "saleads_mi_negocio_full_test",
        report,
        evidence: {
          screenshotsDir: SCREENSHOT_DIR,
          legalUrls: urls,
        },
        generatedAt: new Date().toISOString(),
      };
      fs.mkdirSync(OUTPUT_DIR, { recursive: true });
      fs.writeFileSync(REPORT_FILE, `${JSON.stringify(payload, null, 2)}\n`, "utf8");
      test.info().attach("saleads_mi_negocio_report", {
        body: JSON.stringify(payload, null, 2),
        contentType: "application/json",
      });
    }
  });
});
