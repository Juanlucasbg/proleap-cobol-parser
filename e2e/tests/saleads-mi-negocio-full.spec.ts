import { expect, type BrowserContext, type Page, test } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

type Status = "PASS" | "FAIL";

type StepResult = {
  name: string;
  status: Status;
  details: string[];
};

type Report = {
  login: StepResult;
  miNegocioMenu: StepResult;
  agregarNegocioModal: StepResult;
  administrarNegociosView: StepResult;
  informacionGeneral: StepResult;
  detallesCuenta: StepResult;
  tusNegocios: StepResult;
  terminosCondiciones: StepResult;
  politicaPrivacidad: StepResult;
};

const SCREENSHOT_DIR = path.join(process.cwd(), "test-results", "checkpoints");
const FINAL_REPORT_PATH = path.join(process.cwd(), "test-results", "mi-negocio-final-report.json");
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function initialStep(name: string): StepResult {
  return { name, status: "PASS", details: [] };
}

function setFailed(step: StepResult, message: string): void {
  step.status = "FAIL";
  step.details.push(message);
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

function byVisibleText(page: Page, text: string) {
  return page.getByText(text, { exact: true });
}

function byVisibleTextFlexible(page: Page, text: string) {
  return page.getByText(text, { exact: false });
}

async function clickByText(page: Page, text: string): Promise<void> {
  await byVisibleText(page, text).first().click();
  await waitForUiToSettle(page);
}

async function takeCheckpoint(page: Page, fileName: string, fullPage = false): Promise<void> {
  mkdirSync(SCREENSHOT_DIR, { recursive: true });
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, fileName),
    fullPage,
  });
}

async function maybePickGoogleAccount(page: Page): Promise<void> {
  const accountChoice = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
  if (await accountChoice.first().isVisible({ timeout: 4000 }).catch(() => false)) {
    await accountChoice.first().click();
    await waitForUiToSettle(page);
  }
}

async function openLegalLinkAndCapture(
  context: BrowserContext,
  appPage: Page,
  linkText: string,
  headingText: string,
  screenshotName: string,
): Promise<{ finalUrl: string; valid: boolean; detail: string }> {
  const link = appPage.getByText(linkText, { exact: true }).first();
  await expect(link).toBeVisible({ timeout: 20000 });

  const popupPromise = appPage.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  await link.click();
  await waitForUiToSettle(appPage);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForTimeout(800);
  } else {
    await appPage.waitForLoadState("domcontentloaded");
    await appPage.waitForTimeout(800);
  }

  const headingFound = await targetPage
    .getByRole("heading", { name: headingText })
    .first()
    .isVisible({ timeout: 10000 })
    .catch(() => false);
  const bodyTextFound = await targetPage.locator("body").textContent();
  const hasBodyContent = Boolean(bodyTextFound && bodyTextFound.trim().length > 80);

  await takeCheckpoint(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else {
    // Same-tab navigation: return to application after validation.
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await appPage.waitForTimeout(800);
    const pages = context.pages().filter((p) => !p.isClosed());
    if (pages.length > 0 && pages[0] !== appPage) {
      await pages[0].bringToFront();
    }
  }

  return {
    finalUrl,
    valid: headingFound && hasBodyContent,
    detail: `heading=${headingFound}, content=${hasBodyContent}, url=${finalUrl}`,
  };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("Login via Google and validate Mi Negocio module", async ({ context, page }) => {
    const report: Report = {
      login: initialStep("Login"),
      miNegocioMenu: initialStep("Mi Negocio menu"),
      agregarNegocioModal: initialStep("Agregar Negocio modal"),
      administrarNegociosView: initialStep("Administrar Negocios view"),
      informacionGeneral: initialStep("Información General"),
      detallesCuenta: initialStep("Detalles de la Cuenta"),
      tusNegocios: initialStep("Tus Negocios"),
      terminosCondiciones: initialStep("Términos y Condiciones"),
      politicaPrivacidad: initialStep("Política de Privacidad"),
    };

    // Step 1: Login with Google.
    await waitForUiToSettle(page);
    const signInWithGoogleButton = page.getByRole("button", { name: /google|iniciar sesión|sign in/i }).first();
    if (!(await signInWithGoogleButton.isVisible({ timeout: 25000 }).catch(() => false))) {
      setFailed(report.login, "No se encontró botón de login con Google.");
    } else {
      const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
      await signInWithGoogleButton.click();
      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await maybePickGoogleAccount(popup);
        await popup.waitForTimeout(1200);
      } else {
        await maybePickGoogleAccount(page);
      }

      await waitForUiToSettle(page);
      const sidebarVisible =
        (await page.getByRole("navigation").first().isVisible({ timeout: 30000 }).catch(() => false)) ||
        (await page.locator("aside").first().isVisible({ timeout: 30000 }).catch(() => false));
      if (!sidebarVisible) {
        setFailed(report.login, "No se visualiza sidebar después del login.");
      }
      await takeCheckpoint(page, "01-dashboard-loaded.png", true);
    }

    // Step 2: Open Mi Negocio menu.
    if (report.login.status === "PASS") {
      const negocioEntry = page.getByText("Negocio", { exact: true }).first();
      if (!(await negocioEntry.isVisible({ timeout: 20000 }).catch(() => false))) {
        setFailed(report.miNegocioMenu, "No se encontró sección 'Negocio' en sidebar.");
      } else {
        await negocioEntry.click();
        await waitForUiToSettle(page);
      }

      const miNegocio = page.getByText("Mi Negocio", { exact: true }).first();
      if (!(await miNegocio.isVisible({ timeout: 20000 }).catch(() => false))) {
        setFailed(report.miNegocioMenu, "No se encontró opción 'Mi Negocio'.");
      } else {
        await miNegocio.click();
        await waitForUiToSettle(page);
      }

      const agregarVisible = await byVisibleText(page, "Agregar Negocio").first().isVisible({ timeout: 12000 }).catch(() => false);
      const administrarVisible = await byVisibleText(page, "Administrar Negocios").first().isVisible({ timeout: 12000 }).catch(() => false);
      if (!agregarVisible || !administrarVisible) {
        setFailed(report.miNegocioMenu, "No se expandió submenu Mi Negocio correctamente.");
      }
      await takeCheckpoint(page, "02-mi-negocio-menu-expanded.png", true);
    }

    // Step 3: Validate Agregar Negocio modal.
    if (report.miNegocioMenu.status === "PASS") {
      await clickByText(page, "Agregar Negocio");
      const modalTitle = byVisibleText(page, "Crear Nuevo Negocio").first();
      const nombreInput = page
        .getByLabel("Nombre del Negocio")
        .or(page.getByPlaceholder("Nombre del Negocio"))
        .first();
      const quotaText = byVisibleText(page, "Tienes 2 de 3 negocios").first();
      const cancelButton = page.getByRole("button", { name: "Cancelar" }).first();
      const createButton = page.getByRole("button", { name: "Crear Negocio" }).first();

      const allPresent = (await modalTitle.isVisible({ timeout: 12000 }).catch(() => false)) &&
        (await nombreInput.isVisible({ timeout: 12000 }).catch(() => false)) &&
        (await quotaText.isVisible({ timeout: 12000 }).catch(() => false)) &&
        (await cancelButton.isVisible({ timeout: 12000 }).catch(() => false)) &&
        (await createButton.isVisible({ timeout: 12000 }).catch(() => false));

      if (!allPresent) {
        setFailed(report.agregarNegocioModal, "Elementos requeridos del modal no encontrados.");
      } else {
        await nombreInput.click();
        await nombreInput.fill("Negocio Prueba Automatización");
        await takeCheckpoint(page, "03-agregar-negocio-modal.png", true);
        await cancelButton.click();
        await waitForUiToSettle(page);
      }
    }

    // Step 4: Open Administrar Negocios.
    if (report.miNegocioMenu.status === "PASS") {
      if (!(await byVisibleText(page, "Administrar Negocios").first().isVisible({ timeout: 5000 }).catch(() => false))) {
        await clickByText(page, "Mi Negocio");
      }
      await clickByText(page, "Administrar Negocios");

      const infoGeneral = byVisibleText(page, "Información General").first();
      const detallesCuenta = byVisibleText(page, "Detalles de la Cuenta").first();
      const tusNegocios = byVisibleText(page, "Tus Negocios").first();
      const legal = byVisibleText(page, "Sección Legal").first();
      const pageLoaded = (await infoGeneral.isVisible({ timeout: 20000 }).catch(() => false)) &&
        (await detallesCuenta.isVisible({ timeout: 20000 }).catch(() => false)) &&
        (await tusNegocios.isVisible({ timeout: 20000 }).catch(() => false)) &&
        (await legal.isVisible({ timeout: 20000 }).catch(() => false));
      if (!pageLoaded) {
        setFailed(report.administrarNegociosView, "Vista Administrar Negocios incompleta.");
      }
      await takeCheckpoint(page, "04-administrar-negocios-account-page.png", true);
    }

    // Step 5: Validate Información General.
    if (report.administrarNegociosView.status === "PASS") {
      const infoGeneralCard = page.locator("section,div").filter({ hasText: "Información General" }).first();
      const nameVisible = await infoGeneralCard.getByText(/^[^@]+$/).first().isVisible({ timeout: 12000 }).catch(() => false);
      const emailVisible = await page.getByText(/@/, { exact: false }).first().isVisible({ timeout: 12000 }).catch(() => false);
      const planVisible = await byVisibleText(page, "BUSINESS PLAN").first().isVisible({ timeout: 12000 }).catch(() => false);
      const cambiarPlanVisible = await page.getByRole("button", { name: "Cambiar Plan" }).first().isVisible({ timeout: 12000 }).catch(() => false);

      if (!nameVisible || !emailVisible || !planVisible || !cambiarPlanVisible) {
        setFailed(report.informacionGeneral, "Información General no presenta todos los campos esperados.");
      }
    }

    // Step 6: Validate Detalles de la Cuenta.
    if (report.administrarNegociosView.status === "PASS") {
      const created = await byVisibleTextFlexible(page, "Cuenta creada").first().isVisible({ timeout: 12000 }).catch(() => false);
      const active = await byVisibleTextFlexible(page, "Estado activo").first().isVisible({ timeout: 12000 }).catch(() => false);
      const language = await byVisibleTextFlexible(page, "Idioma seleccionado").first().isVisible({ timeout: 12000 }).catch(() => false);
      if (!created || !active || !language) {
        setFailed(report.detallesCuenta, "Detalles de la Cuenta no contiene todos los textos esperados.");
      }
    }

    // Step 7: Validate Tus Negocios.
    if (report.administrarNegociosView.status === "PASS") {
      const tusNegociosTitle = await byVisibleText(page, "Tus Negocios").first().isVisible({ timeout: 12000 }).catch(() => false);
      const addButton = await page.getByRole("button", { name: "Agregar Negocio" }).first().isVisible({ timeout: 12000 }).catch(() => false);
      const quota = await byVisibleText(page, "Tienes 2 de 3 negocios").first().isVisible({ timeout: 12000 }).catch(() => false);
      if (!tusNegociosTitle || !addButton || !quota) {
        setFailed(report.tusNegocios, "Sección Tus Negocios no cumple con validaciones.");
      }
    }

    // Step 8: Validate Términos y Condiciones.
    if (report.administrarNegociosView.status === "PASS") {
      const result = await openLegalLinkAndCapture(
        context,
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "05-terminos-y-condiciones.png",
      );
      report.terminosCondiciones.details.push(`Final URL: ${result.finalUrl}`);
      if (!result.valid) {
        setFailed(report.terminosCondiciones, `Validación legal falló: ${result.detail}`);
      }
    }

    // Step 9: Validate Política de Privacidad.
    if (report.administrarNegociosView.status === "PASS") {
      const result = await openLegalLinkAndCapture(
        context,
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "06-politica-de-privacidad.png",
      );
      report.politicaPrivacidad.details.push(`Final URL: ${result.finalUrl}`);
      if (!result.valid) {
        setFailed(report.politicaPrivacidad, `Validación legal falló: ${result.detail}`);
      }
    }

    mkdirSync(path.dirname(FINAL_REPORT_PATH), { recursive: true });
    writeFileSync(FINAL_REPORT_PATH, JSON.stringify(report, null, 2), "utf8");

    const failedSteps = Object.values(report).filter((step) => step.status === "FAIL");
    expect(
      failedSteps,
      `Workflow failed in steps: ${failedSteps.map((s) => `${s.name}: ${s.details.join("; ")}`).join(" | ")}`,
    ).toHaveLength(0);
  });
});
