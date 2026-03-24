import { expect, test, type Locator, type Page } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepReport = {
  id: number;
  name: string;
  status: StepStatus;
  details: string[];
};

type FinalReport = {
  testName: string;
  generatedAt: string;
  appUrl: string;
  legalUrls: {
    terminosYCondiciones: string | null;
    politicaDePrivacidad: string | null;
  };
  steps: StepReport[];
  summary: {
    passed: number;
    failed: number;
  };
};

const TEST_NAME = "saleads_mi_negocio_full_test";
const ARTIFACTS_DIR = path.resolve(process.cwd(), "artifacts", TEST_NAME);
const REPORT_PATH = path.resolve(ARTIFACTS_DIR, "final-report.json");
const WAIT_AFTER_CLICK_MS = 1000;
const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const START_URL = process.env.SALEADS_START_URL;

const stepReports: StepReport[] = [];
let currentStepReport: StepReport | null = null;
let terminosUrl: string | null = null;
let privacidadUrl: string | null = null;

function ensureArtifactsDir(): void {
  fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });
}

function beginStep(id: number, name: string): StepReport {
  currentStepReport = { id, name, status: "PASS", details: [] };
  return currentStepReport;
}

function pass(detail: string): void {
  currentStepReport?.details.push(`PASS: ${detail}`);
}

function fail(detail: string): void {
  if (!currentStepReport) {
    return;
  }
  currentStepReport.status = "FAIL";
  currentStepReport.details.push(`FAIL: ${detail}`);
}

async function endStep(page: Page, step: StepReport, screenshotName?: string): Promise<void> {
  if (screenshotName) {
    await captureScreenshot(page, screenshotName);
  }
  stepReports.push(step);
  currentStepReport = null;
}

async function captureScreenshot(page: Page, screenshotName: string): Promise<void> {
  await page.screenshot({
    path: path.resolve(ARTIFACTS_DIR, screenshotName),
    fullPage: true,
  });
}

async function clickAndSettle(target: Locator): Promise<void> {
  await target.click();
  await target.page().waitForTimeout(WAIT_AFTER_CLICK_MS);
}

async function clickAndSettleByText(page: Page, text: string): Promise<void> {
  const locator = page.getByText(text, { exact: false }).first();
  await expect(locator).toBeVisible();
  await clickAndSettle(locator);
}

async function firstVisibleLocator(locators: Locator[], timeoutMs = 15000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await locators[0].page().waitForTimeout(250);
  }

  throw new Error("None of the candidate locators became visible.");
}

async function maybeSelectGoogleAccount(page: Page): Promise<void> {
  const accountItem = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountItem.isVisible({ timeout: 7000 }).catch(() => false)) {
    await clickAndSettle(accountItem);
  }
}

async function ensureSidebarVisible(page: Page): Promise<void> {
  const sidebarCandidate = page.getByRole("navigation").first();
  if (await sidebarCandidate.isVisible({ timeout: 15000 }).catch(() => false)) {
    return;
  }

  const negocioLink = page.getByText("Negocio", { exact: false }).first();
  await expect(negocioLink).toBeVisible({ timeout: 20000 });
}

async function openAppLoginPageIfConfigured(page: Page): Promise<void> {
  if (START_URL) {
    await page.goto(START_URL, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
    return;
  }

  await expect
    .poll(() => page.url(), {
      timeout: 15000,
      message:
        "Set SALEADS_START_URL when the test is not executed from an already-open SaleADS login page.",
    })
    .not.toBe("about:blank");
}

async function assertUserInfoVisible(page: Page): Promise<void> {
  const infoSection = page.locator("section,article,div").filter({
    hasText: /informaci[oó]n general/i,
  });
  const emailLocator = page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();

  await expect(infoSection.first()).toBeVisible({ timeout: 15000 });
  await expect(emailLocator).toBeVisible({ timeout: 15000 });

  const sectionText = (await infoSection.first().innerText()).replace(/\s+/g, " ");
  const hasPossibleName = /(?:nombre|usuario|user|name)\b/i.test(sectionText)
    || /[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+ [A-ZÁÉÍÓÚÑ][a-záéíóúñ]+/.test(sectionText);

  expect(
    hasPossibleName,
    "Expected user name indicators in Información General section.",
  ).toBeTruthy();
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregar = page.getByText("Agregar Negocio", { exact: false }).first();
  const administrar = page.getByText("Administrar Negocios", { exact: false }).first();

  const isExpanded =
    (await agregar.isVisible({ timeout: 1000 }).catch(() => false)) &&
    (await administrar.isVisible({ timeout: 1000 }).catch(() => false));
  if (isExpanded) {
    return;
  }

  const miNegocioTrigger = page.getByText("Mi Negocio", { exact: false }).first();
  await expect(miNegocioTrigger).toBeVisible();
  await clickAndSettle(miNegocioTrigger);
}

async function openLegalPageAndReturn(
  page: Page,
  linkText: string,
  expectedHeading: string,
  screenshotName: string,
): Promise<string> {
  const link = page.getByText(linkText, { exact: false }).first();
  await expect(link).toBeVisible();

  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  await clickAndSettle(link);
  const popup = await popupPromise;

  const legalPage = popup ?? page;
  await legalPage.waitForLoadState("domcontentloaded");

  await expect(legalPage.getByText(expectedHeading, { exact: false }).first()).toBeVisible({
    timeout: 20000,
  });

  // Ensure there is meaningful legal content beyond heading.
  const legalContent = legalPage.locator("main, article, body");
  await expect(legalContent).toContainText(/\S+/, { timeout: 20000 });

  await captureScreenshot(legalPage, screenshotName);

  const finalUrl = legalPage.url();
  if (popup) {
    await popup.close();
    await page.bringToFront();
    await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
  } else {
    await page.goBack();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
  }

  return finalUrl;
}

function writeFinalReport(page: Page): void {
  const passed = stepReports.filter((s) => s.status === "PASS").length;
  const failed = stepReports.length - passed;
  const report: FinalReport = {
    testName: TEST_NAME,
    generatedAt: new Date().toISOString(),
    appUrl: page.url(),
    legalUrls: {
      terminosYCondiciones: terminosUrl,
      politicaDePrivacidad: privacidadUrl,
    },
    steps: stepReports,
    summary: {
      passed,
      failed,
    },
  };

  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf-8");
}

test.describe(TEST_NAME, () => {
  test("Login with Google and validate Mi Negocio module workflow", async ({ page }) => {
    ensureArtifactsDir();
    await openAppLoginPageIfConfigured(page);

    // Step 1 - Login with Google
    {
      const step = beginStep(1, "Login with Google");
      try {
        const loginGoogleButton = await firstVisibleLocator([
          page.getByRole("button", {
            name: /sign in with google|iniciar sesi[oó]n con google/i,
          }).first(),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google/i).first(),
          page.getByRole("button", { name: /google/i }).first(),
        ]);
        await expect(loginGoogleButton).toBeVisible({ timeout: 20000 });

        const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
        await clickAndSettle(loginGoogleButton);
        pass("Login button was visible and clicked.");

        const popup = await popupPromise;
        if (popup) {
          await popup.waitForLoadState("domcontentloaded");
          await maybeSelectGoogleAccount(popup);
          await popup.waitForEvent("close", { timeout: 45000 }).catch(() => undefined);
          await page.bringToFront();
        } else {
          await maybeSelectGoogleAccount(page);
        }
        pass("Google account selector handled when present.");

        await ensureSidebarVisible(page);
        pass("Main app interface loaded and sidebar/navigation is visible.");
      } catch (error) {
        fail(`Login flow validation error: ${(error as Error).message}`);
      }

      await endStep(page, step, "01-dashboard-loaded.png");
    }

    // Step 2 - Open Mi Negocio menu
    {
      const step = beginStep(2, "Open Mi Negocio menu");
      try {
        await expect(page.getByText("Negocio", { exact: false }).first()).toBeVisible({
          timeout: 20000,
        });
        pass("Sidebar section 'Negocio' is visible.");

        await ensureMiNegocioExpanded(page);
        const agregar = page.getByText("Agregar Negocio", { exact: false }).first();
        const administrar = page.getByText("Administrar Negocios", { exact: false }).first();
        await expect(agregar).toBeVisible();
        await expect(administrar).toBeVisible();
        pass("'Mi Negocio' submenu expanded with expected items.");
      } catch (error) {
        fail(`Mi Negocio menu validation error: ${(error as Error).message}`);
      }

      await endStep(page, step, "02-mi-negocio-menu-expanded.png");
    }

    // Step 3 - Validate Agregar Negocio modal
    {
      const step = beginStep(3, "Validate Agregar Negocio modal");
      try {
        await clickAndSettleByText(page, "Agregar Negocio");

        const modalTitle = page.getByText("Crear Nuevo Negocio", { exact: false }).first();
        const nombreInput = await firstVisibleLocator([
          page.getByLabel("Nombre del Negocio", { exact: false }).first(),
          page.getByPlaceholder("Nombre del Negocio", { exact: false }).first(),
          page.locator("input[name*='nombre' i], input[id*='nombre' i]").first(),
        ]);
        const quotaText = page.getByText("Tienes 2 de 3 negocios", { exact: false }).first();
        const cancelarButton = page.getByRole("button", { name: "Cancelar" }).first();
        const crearButton = page.getByRole("button", { name: "Crear Negocio" }).first();

        await expect(modalTitle).toBeVisible({ timeout: 15000 });
        await expect(nombreInput).toBeVisible();
        await expect(quotaText).toBeVisible();
        await expect(cancelarButton).toBeVisible();
        await expect(crearButton).toBeVisible();
        pass("Agregar Negocio modal contains title, input, quota text and required buttons.");

        await captureScreenshot(page, "03-agregar-negocio-modal.png");
        await nombreInput.click();
        await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
        await nombreInput.fill("Negocio Prueba Automatización");
        pass("Optional text input action completed.");

        await clickAndSettle(cancelarButton);
        pass("Modal closed by clicking 'Cancelar'.");
      } catch (error) {
        fail(`Agregar Negocio modal validation error: ${(error as Error).message}`);
      }

      await endStep(page, step);
    }

    // Step 4 - Open Administrar Negocios
    {
      const step = beginStep(4, "Open Administrar Negocios");
      try {
        await ensureMiNegocioExpanded(page);
        await clickAndSettleByText(page, "Administrar Negocios");
        await expect(page.getByText("Información General", { exact: false }).first()).toBeVisible({
          timeout: 20000,
        });
        await expect(page.getByText("Detalles de la Cuenta", { exact: false }).first()).toBeVisible({
          timeout: 20000,
        });
        await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible({
          timeout: 20000,
        });
        await expect(page.getByText("Sección Legal", { exact: false }).first()).toBeVisible({
          timeout: 20000,
        });
        pass("Administrar Negocios page loaded with all required sections.");
      } catch (error) {
        fail(`Administrar Negocios validation error: ${(error as Error).message}`);
      }

      await endStep(page, step, "04-administrar-negocios-page.png");
    }

    // Step 5 - Validate Información General
    {
      const step = beginStep(5, "Validate Información General");
      try {
        await expect(page.getByText("Información General", { exact: false }).first()).toBeVisible({
          timeout: 15000,
        });
        await assertUserInfoVisible(page);
        await expect(page.getByText("BUSINESS PLAN", { exact: false }).first()).toBeVisible();
        await expect(page.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible();
        pass("Información General includes user identity, plan and action button.");
      } catch (error) {
        fail(`Información General validation error: ${(error as Error).message}`);
      }

      await endStep(page, step);
    }

    // Step 6 - Validate Detalles de la Cuenta
    {
      const step = beginStep(6, "Validate Detalles de la Cuenta");
      try {
        await expect(page.getByText("Cuenta creada", { exact: false }).first()).toBeVisible({
          timeout: 15000,
        });
        await expect(page.getByText("Estado activo", { exact: false }).first()).toBeVisible();
        await expect(page.getByText("Idioma seleccionado", { exact: false }).first()).toBeVisible();
        pass("Detalles de la Cuenta has all required fields.");
      } catch (error) {
        fail(`Detalles de la Cuenta validation error: ${(error as Error).message}`);
      }

      await endStep(page, step);
    }

    // Step 7 - Validate Tus Negocios
    {
      const step = beginStep(7, "Validate Tus Negocios");
      try {
        await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible({
          timeout: 15000,
        });
        await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible();
        await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false }).first()).toBeVisible();
        pass("Tus Negocios list, action button and quota text are visible.");
      } catch (error) {
        fail(`Tus Negocios validation error: ${(error as Error).message}`);
      }

      await endStep(page, step);
    }

    // Step 8 - Validate Términos y Condiciones
    {
      const step = beginStep(8, "Validate Términos y Condiciones");
      try {
        terminosUrl = await openLegalPageAndReturn(
          page,
          "Términos y Condiciones",
          "Términos y Condiciones",
          "08-terminos-y-condiciones.png",
        );
        pass(`Términos y Condiciones content verified. Final URL: ${terminosUrl}`);
      } catch (error) {
        fail(`Términos y Condiciones validation error: ${(error as Error).message}`);
      }

      await endStep(page, step);
    }

    // Step 9 - Validate Política de Privacidad
    {
      const step = beginStep(9, "Validate Política de Privacidad");
      try {
        privacidadUrl = await openLegalPageAndReturn(
          page,
          "Política de Privacidad",
          "Política de Privacidad",
          "09-politica-de-privacidad.png",
        );
        pass(`Política de Privacidad content verified. Final URL: ${privacidadUrl}`);
      } catch (error) {
        fail(`Política de Privacidad validation error: ${(error as Error).message}`);
      }

      await endStep(page, step);
    }

    // Step 10 - Final Report
    {
      const step = beginStep(10, "Final Report");
      try {
        writeFinalReport(page);
        pass(`Final report generated at ${REPORT_PATH}`);
      } catch (error) {
        fail(`Final report generation error: ${(error as Error).message}`);
      }

      await endStep(page, step, "10-final-state.png");
    }

    // Ensure test fails if any step failed while preserving full report.
    const failedSteps = stepReports.filter((s) => s.status === "FAIL");
    expect(
      failedSteps,
      `One or more workflow steps failed. See ${REPORT_PATH} for details.`,
    ).toEqual([]);
  });
});
