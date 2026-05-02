import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepReport = {
  status: StepStatus;
  details: string[];
};

type FinalReport = {
  Login: StepReport;
  "Mi Negocio menu": StepReport;
  "Agregar Negocio modal": StepReport;
  "Administrar Negocios view": StepReport;
  "Información General": StepReport;
  "Detalles de la Cuenta": StepReport;
  "Tus Negocios": StepReport;
  "Términos y Condiciones": StepReport;
  "Política de Privacidad": StepReport;
};

const CHECKPOINTS_DIR = path.join(process.cwd(), "test-results", "saleads-mi-negocio");

function emptyStepReport(): StepReport {
  return { status: "FAIL", details: [] };
}

function createInitialReport(): FinalReport {
  return {
    Login: emptyStepReport(),
    "Mi Negocio menu": emptyStepReport(),
    "Agregar Negocio modal": emptyStepReport(),
    "Administrar Negocios view": emptyStepReport(),
    "Información General": emptyStepReport(),
    "Detalles de la Cuenta": emptyStepReport(),
    "Tus Negocios": emptyStepReport(),
    "Términos y Condiciones": emptyStepReport(),
    "Política de Privacidad": emptyStepReport(),
  };
}

async function captureCheckpoint(page: Page, name: string, fullPage = false): Promise<string> {
  fs.mkdirSync(CHECKPOINTS_DIR, { recursive: true });
  const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
  const filePath = path.join(CHECKPOINTS_DIR, `${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function waitForUiIdle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function clickByText(page: Page, text: string): Promise<void> {
  const target = page.getByText(text, { exact: true }).first();
  await expect(target, `Expected visible text: ${text}`).toBeVisible({ timeout: 20000 });
  await target.click();
  await waitForUiIdle(page);
}

async function assertAnyVisible(page: Page, labels: string[]): Promise<Locator> {
  for (const label of labels) {
    const locator = page.getByText(label, { exact: true }).first();
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  for (const label of labels) {
    const locator = page.getByRole("button", { name: label, exact: true }).first();
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  throw new Error(`None of these labels were visible: ${labels.join(", ")}`);
}

async function clickMenuOptionWithRetry(page: Page, optionText: string): Promise<void> {
  const option = page.getByText(optionText, { exact: true }).first();
  if (!(await option.isVisible().catch(() => false))) {
    const miNegocio = page.getByText("Mi Negocio", { exact: true }).first();
    if (await miNegocio.isVisible().catch(() => false)) {
      await miNegocio.click();
      await waitForUiIdle(page);
    }
  }

  await clickByText(page, optionText);
}

async function validateLegalPage(
  context: BrowserContext,
  appPage: Page,
  linkText: string,
  headingText: string,
  screenshotName: string,
): Promise<{ screenshotPath: string; finalUrl: string; openedInNewTab: boolean }> {
  const link = appPage.getByText(linkText, { exact: true }).first();
  await expect(link, `Missing legal link: ${linkText}`).toBeVisible({ timeout: 20000 });

  const popupPromise = appPage.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  await link.click();

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  await targetPage.waitForLoadState("domcontentloaded", { timeout: 30000 });
  await targetPage.waitForTimeout(1000);
  await expect(targetPage.getByRole("heading", { name: headingText }).first()).toBeVisible({ timeout: 20000 });

  const legalContent = targetPage.locator("main, article, section, body").first();
  await expect(legalContent).toContainText(/\w{15,}/, { timeout: 20000 });

  const screenshotPath = await captureCheckpoint(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" });
    await waitForUiIdle(appPage);
  }

  return { screenshotPath, finalUrl, openedInNewTab: Boolean(popup) };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login to SaleADS and validate Mi Negocio workflow", async ({ page, context }) => {
    const report = createInitialReport();

    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL ?? "";
    expect(loginUrl, "SALEADS_LOGIN_URL or BASE_URL must be provided for environment-agnostic execution.").not.toBe("");

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiIdle(page);

    // Step 1: Login with Google
    try {
      const loginButton = await assertAnyVisible(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
      ]);
      await loginButton.click();
      await waitForUiIdle(page);

      const chooseAccount = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
      if (await chooseAccount.isVisible().catch(() => false)) {
        await chooseAccount.click();
        await waitForUiIdle(page);
      }

      const sidebar = page.locator("aside, nav").filter({ hasText: "Mi Negocio" }).first();
      await expect(sidebar).toBeVisible({ timeout: 30000 });
      const dashboardShot = await captureCheckpoint(page, "01-dashboard-loaded");

      report.Login = {
        status: "PASS",
        details: [
          "Main application interface displayed after Google login.",
          "Left sidebar navigation visible.",
          `Checkpoint screenshot: ${dashboardShot}`,
        ],
      };
    } catch (error) {
      report.Login = { status: "FAIL", details: [`Login validation failed: ${(error as Error).message}`] };
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByText(page, "Mi Negocio");
      await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible({ timeout: 20000 });
      await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible({ timeout: 20000 });
      const menuShot = await captureCheckpoint(page, "02-mi-negocio-menu-expanded");

      report["Mi Negocio menu"] = {
        status: "PASS",
        details: [
          "Mi Negocio submenu expanded successfully.",
          "'Agregar Negocio' and 'Administrar Negocios' are visible.",
          `Checkpoint screenshot: ${menuShot}`,
        ],
      };
    } catch (error) {
      report["Mi Negocio menu"] = {
        status: "FAIL",
        details: [`Mi Negocio menu validation failed: ${(error as Error).message}`],
      };
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickMenuOptionWithRetry(page, "Agregar Negocio");

      await expect(page.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible({ timeout: 20000 });
      await expect(page.getByLabel("Nombre del Negocio", { exact: true })).toBeVisible({ timeout: 20000 });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: "Cancelar", exact: true })).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: "Crear Negocio", exact: true })).toBeVisible({ timeout: 20000 });

      const modalShot = await captureCheckpoint(page, "03-agregar-negocio-modal");
      const businessNameInput = page.getByLabel("Nombre del Negocio", { exact: true });
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickByText(page, "Cancelar");
      report["Agregar Negocio modal"] = {
        status: "PASS",
        details: [
          "Modal and required controls validated.",
          "Optional input interaction completed and modal closed with Cancelar.",
          `Checkpoint screenshot: ${modalShot}`,
        ],
      };
    } catch (error) {
      report["Agregar Negocio modal"] = {
        status: "FAIL",
        details: [`Agregar Negocio modal validation failed: ${(error as Error).message}`],
      };
      throw error;
    }

    // Step 4: Open Administrar Negocios and validate sections
    try {
      await clickMenuOptionWithRetry(page, "Administrar Negocios");
      await expect(page.getByText("Información General", { exact: true })).toBeVisible({ timeout: 30000 });
      await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible({ timeout: 30000 });
      await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible({ timeout: 30000 });
      await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible({ timeout: 30000 });

      const accountShot = await captureCheckpoint(page, "04-administrar-negocios-account-page", true);
      report["Administrar Negocios view"] = {
        status: "PASS",
        details: [
          "Account page loaded with all required sections.",
          `Checkpoint screenshot: ${accountShot}`,
        ],
      };
    } catch (error) {
      report["Administrar Negocios view"] = {
        status: "FAIL",
        details: [`Administrar Negocios validation failed: ${(error as Error).message}`],
      };
      throw error;
    }

    // Step 5: Validate Información General
    try {
      const infoSection = page.locator("section, div").filter({ hasText: "Información General" }).first();
      await expect(infoSection).toBeVisible({ timeout: 20000 });
      await expect(infoSection).toContainText("@", { timeout: 20000 });
      await expect(infoSection.getByText("BUSINESS PLAN", { exact: true })).toBeVisible({ timeout: 20000 });
      await expect(infoSection.getByRole("button", { name: "Cambiar Plan", exact: true })).toBeVisible({ timeout: 20000 });

      report["Información General"] = {
        status: "PASS",
        details: [
          "User identity data visible (name/email area).",
          "'BUSINESS PLAN' and 'Cambiar Plan' validated.",
        ],
      };
    } catch (error) {
      report["Información General"] = {
        status: "FAIL",
        details: [`Información General validation failed: ${(error as Error).message}`],
      };
      throw error;
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      const detailsSection = page.locator("section, div").filter({ hasText: "Detalles de la Cuenta" }).first();
      await expect(detailsSection).toBeVisible({ timeout: 20000 });
      await expect(detailsSection.getByText("Cuenta creada", { exact: true })).toBeVisible({ timeout: 20000 });
      await expect(detailsSection.getByText("Estado activo", { exact: true })).toBeVisible({ timeout: 20000 });
      await expect(detailsSection.getByText("Idioma seleccionado", { exact: true })).toBeVisible({ timeout: 20000 });

      report["Detalles de la Cuenta"] = {
        status: "PASS",
        details: ["Cuenta creada, Estado activo e Idioma seleccionado visibles."],
      };
    } catch (error) {
      report["Detalles de la Cuenta"] = {
        status: "FAIL",
        details: [`Detalles de la Cuenta validation failed: ${(error as Error).message}`],
      };
      throw error;
    }

    // Step 7: Validate Tus Negocios
    try {
      const businessSection = page.locator("section, div").filter({ hasText: "Tus Negocios" }).first();
      await expect(businessSection).toBeVisible({ timeout: 20000 });
      await expect(businessSection.getByRole("button", { name: "Agregar Negocio", exact: true })).toBeVisible({
        timeout: 20000,
      });
      await expect(businessSection.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible({ timeout: 20000 });

      report["Tus Negocios"] = {
        status: "PASS",
        details: ["Business list area and required controls/text validated."],
      };
    } catch (error) {
      report["Tus Negocios"] = {
        status: "FAIL",
        details: [`Tus Negocios validation failed: ${(error as Error).message}`],
      };
      throw error;
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const termsEvidence = await validateLegalPage(
        context,
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "08-terminos-y-condiciones",
      );

      report["Términos y Condiciones"] = {
        status: "PASS",
        details: [
          "Heading and legal content verified.",
          `Opened in new tab: ${termsEvidence.openedInNewTab}`,
          `Final URL: ${termsEvidence.finalUrl}`,
          `Checkpoint screenshot: ${termsEvidence.screenshotPath}`,
        ],
      };
    } catch (error) {
      report["Términos y Condiciones"] = {
        status: "FAIL",
        details: [`Términos y Condiciones validation failed: ${(error as Error).message}`],
      };
      throw error;
    }

    // Step 9: Validate Política de Privacidad
    try {
      const privacyEvidence = await validateLegalPage(
        context,
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "09-politica-de-privacidad",
      );

      report["Política de Privacidad"] = {
        status: "PASS",
        details: [
          "Heading and legal content verified.",
          `Opened in new tab: ${privacyEvidence.openedInNewTab}`,
          `Final URL: ${privacyEvidence.finalUrl}`,
          `Checkpoint screenshot: ${privacyEvidence.screenshotPath}`,
        ],
      };
    } catch (error) {
      report["Política de Privacidad"] = {
        status: "FAIL",
        details: [`Política de Privacidad validation failed: ${(error as Error).message}`],
      };
      throw error;
    } finally {
      const reportPath = path.join(CHECKPOINTS_DIR, "final-report.json");
      fs.mkdirSync(CHECKPOINTS_DIR, { recursive: true });
      fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf-8");
      test.info().attach("saleads-final-report", {
        contentType: "application/json",
        body: Buffer.from(JSON.stringify(report, null, 2), "utf-8"),
      });

      const statuses = Object.entries(report).map(([step, value]) => `${step}: ${value.status}`);
      expect.soft(statuses.every((item) => item.endsWith("PASS")), `Final report contains failures:\n${statuses.join("\n")}`).toBeTruthy();
    }
  });
});
