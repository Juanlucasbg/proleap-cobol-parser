import { expect, Page, TestInfo, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";

type StepName =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepStatus = "PASS" | "FAIL" | "SKIPPED";

interface StepResult {
  status: StepStatus;
  details: string[];
  evidence: string[];
}

interface RunReport {
  startedAt: string;
  finishedAt?: string;
  environment: {
    loginUrl?: string;
    baseURL?: string;
  };
  evidence: {
    screenshots: string[];
    terminosUrl?: string;
    privacidadUrl?: string;
  };
  steps: Record<StepName, StepResult>;
}

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_NAME = "Negocio Prueba Automatización";

const ALL_STEPS: StepName[] = [
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

function createStepResult(): StepResult {
  return { status: "SKIPPED", details: [], evidence: [] };
}

function createReport(testInfo: TestInfo, loginUrl: string | undefined, baseURL: string | undefined): RunReport {
  const steps: Record<StepName, StepResult> = {
    "Login": createStepResult(),
    "Mi Negocio menu": createStepResult(),
    "Agregar Negocio modal": createStepResult(),
    "Administrar Negocios view": createStepResult(),
    "Información General": createStepResult(),
    "Detalles de la Cuenta": createStepResult(),
    "Tus Negocios": createStepResult(),
    "Términos y Condiciones": createStepResult(),
    "Política de Privacidad": createStepResult(),
  };

  const report: RunReport = {
    startedAt: new Date().toISOString(),
    environment: { loginUrl, baseURL },
    evidence: { screenshots: [] },
    steps,
  };

  testInfo.attach("saleads-report-initial", {
    body: Buffer.from(JSON.stringify(report, null, 2), "utf8"),
    contentType: "application/json",
  });

  return report;
}

function markPass(report: RunReport, step: StepName, message: string): void {
  const item = report.steps[step];
  item.status = "PASS";
  item.details.push(message);
}

function markFail(report: RunReport, step: StepName, message: string): void {
  const item = report.steps[step];
  item.status = "FAIL";
  item.details.push(message);
}

function markSkipped(report: RunReport, step: StepName, message: string): void {
  const item = report.steps[step];
  if (item.status !== "FAIL") {
    item.status = "SKIPPED";
  }
  item.details.push(message);
}

async function attachAndTrackScreenshot(
  page: Page,
  report: RunReport,
  testInfo: TestInfo,
  step: StepName,
  checkpointName: string,
  fullPage = false
): Promise<void> {
  const safeName = checkpointName.replace(/[^a-zA-Z0-9_-]+/g, "_");
  const outputPath = testInfo.outputPath(`${step}-${safeName}.png`);
  await page.screenshot({ path: outputPath, fullPage });
  await testInfo.attach(`${step} - ${checkpointName}`, {
    path: outputPath,
    contentType: "image/png",
  });
  report.steps[step].evidence.push(outputPath);
  report.evidence.screenshots.push(outputPath);
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1200);
}

async function clickByText(page: Page, text: string): Promise<void> {
  const target = page.getByText(text, { exact: true }).first();
  await expect(target).toBeVisible({ timeout: 15_000 });
  await target.click();
  await waitForUiLoad(page);
}

async function trySelectGoogleAccount(page: Page, report: RunReport): Promise<void> {
  const accountLocator = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
  if (await accountLocator.isVisible({ timeout: 4_000 }).catch(() => false)) {
    await accountLocator.click();
    report.steps["Login"].details.push(`Google account selected: ${GOOGLE_ACCOUNT_EMAIL}`);
    await waitForUiLoad(page);
  } else {
    report.steps["Login"].details.push("Google account selector not displayed; continuing with existing Google session.");
  }
}

async function validateLegalLink(page: Page, report: RunReport, testInfo: TestInfo, step: StepName, linkText: string): Promise<void> {
  const appTab = page;
  const link = appTab.getByText(linkText, { exact: true }).first();
  await expect(link).toBeVisible({ timeout: 15_000 });

  const popupPromise = appTab.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
  await link.click();
  await waitForUiLoad(appTab);

  const popup = await popupPromise;
  const legalPage = popup ?? appTab;
  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForTimeout(1000);

  const headingLocator = legalPage.getByRole("heading", { name: linkText }).first();
  await expect(headingLocator).toBeVisible({ timeout: 15_000 });

  const legalContentVisible =
    (await legalPage.getByText(/(términos|condiciones|privacidad|datos personales|uso)/i).first().isVisible().catch(() => false)) ||
    (await legalPage.locator("article, main, section, p").first().isVisible().catch(() => false));
  expect(legalContentVisible).toBeTruthy();

  const finalUrl = legalPage.url();
  markPass(report, step, `Legal page loaded successfully at ${finalUrl}`);
  if (step === "Términos y Condiciones") {
    report.evidence.terminosUrl = finalUrl;
  } else if (step === "Política de Privacidad") {
    report.evidence.privacidadUrl = finalUrl;
  }
  await attachAndTrackScreenshot(legalPage, report, testInfo, step, "legal-page", true);

  if (popup) {
    await popup.close();
    await appTab.bringToFront();
    await waitForUiLoad(appTab);
    report.steps[step].details.push("Validation happened in popup tab; tab closed and switched back.");
  } else {
    await appTab.goBack({ waitUntil: "domcontentloaded" }).catch(() => Promise.resolve());
    await waitForUiLoad(appTab);
    report.steps[step].details.push("Validation happened in same tab; navigated back to app.");
  }
}

async function finalizeReport(report: RunReport, testInfo: TestInfo): Promise<void> {
  report.finishedAt = new Date().toISOString();
  const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const rootArtifacts = path.resolve(process.cwd(), "artifacts");
  fs.mkdirSync(rootArtifacts, { recursive: true });
  fs.writeFileSync(path.join(rootArtifacts, "saleads-mi-negocio-report.json"), JSON.stringify(report, null, 2), "utf8");
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, baseURL }, testInfo) => {
    const envLoginUrl = process.env.SALEADS_LOGIN_URL?.trim();
    const loginUrl = envLoginUrl || baseURL;
    const report = createReport(testInfo, loginUrl, baseURL);

    try {
      // Step 1: Login with Google
      if (!loginUrl) {
        markFail(
          report,
          "Login",
          "Missing SALEADS_LOGIN_URL and no baseURL configured. Provide login page URL via env var or Playwright baseURL."
        );
      } else {
        await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
        await waitForUiLoad(page);

        const signInGoogle = page
          .getByRole("button", { name: /sign in with google|iniciar sesión con google|google/i })
          .first();
        await expect(signInGoogle).toBeVisible({ timeout: 20_000 });
        await signInGoogle.click();
        await waitForUiLoad(page);
        await trySelectGoogleAccount(page, report);

        const sidebarVisible =
          (await page.getByRole("navigation").first().isVisible({ timeout: 20_000 }).catch(() => false)) ||
          (await page.locator("aside").first().isVisible({ timeout: 20_000 }).catch(() => false));
        expect(sidebarVisible).toBeTruthy();
        markPass(report, "Login", "Main app interface and left sidebar are visible after login.");
        await attachAndTrackScreenshot(page, report, testInfo, "Login", "dashboard-loaded", true);
      }

      // Step 2: Open Mi Negocio menu
      if (report.steps["Login"].status !== "PASS") {
        markSkipped(report, "Mi Negocio menu", "Skipped because Login failed.");
      } else {
        await clickByText(page, "Negocio");
        await clickByText(page, "Mi Negocio");
        const agregarVisible = await page.getByText("Agregar Negocio", { exact: true }).first().isVisible().catch(() => false);
        const administrarVisible = await page
          .getByText("Administrar Negocios", { exact: true })
          .first()
          .isVisible()
          .catch(() => false);

        expect(agregarVisible).toBeTruthy();
        expect(administrarVisible).toBeTruthy();
        markPass(report, "Mi Negocio menu", "Menu expanded and both submenu options are visible.");
        await attachAndTrackScreenshot(page, report, testInfo, "Mi Negocio menu", "menu-expanded");
      }

      // Step 3: Validate Agregar Negocio modal
      if (report.steps["Mi Negocio menu"].status !== "PASS") {
        markSkipped(report, "Agregar Negocio modal", "Skipped because Mi Negocio menu step failed.");
      } else {
        await clickByText(page, "Agregar Negocio");
        const modalTitle = page.getByText("Crear Nuevo Negocio", { exact: true }).first();
        await expect(modalTitle).toBeVisible({ timeout: 15_000 });
        await expect(page.getByLabel("Nombre del Negocio").first()).toBeVisible({ timeout: 15_000 });
        await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true }).first()).toBeVisible({ timeout: 15_000 });
        await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible({ timeout: 15_000 });
        await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible({ timeout: 15_000 });
        await attachAndTrackScreenshot(page, report, testInfo, "Agregar Negocio modal", "modal-open");

        const businessNameInput = page.getByLabel("Nombre del Negocio").first();
        await businessNameInput.fill(BUSINESS_NAME);
        await page.getByRole("button", { name: "Cancelar" }).first().click();
        await waitForUiLoad(page);

        markPass(report, "Agregar Negocio modal", "Modal content validated and cancelled successfully.");
      }

      // Step 4: Open Administrar Negocios
      if (report.steps["Mi Negocio menu"].status !== "PASS") {
        markSkipped(report, "Administrar Negocios view", "Skipped because Mi Negocio menu step failed.");
      } else {
        const administrarOption = page.getByText("Administrar Negocios", { exact: true }).first();
        if (!(await administrarOption.isVisible().catch(() => false))) {
          await clickByText(page, "Mi Negocio");
        }
        await clickByText(page, "Administrar Negocios");

        await expect(page.getByText("Información General", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
        await expect(page.getByText("Detalles de la Cuenta", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
        await expect(page.getByText("Tus Negocios", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
        await expect(page.getByText("Sección Legal", { exact: true }).first()).toBeVisible({ timeout: 20_000 });

        markPass(report, "Administrar Negocios view", "Account page with expected sections loaded.");
        await attachAndTrackScreenshot(page, report, testInfo, "Administrar Negocios view", "account-page-full", true);
      }

      // Step 5: Validate Información General
      if (report.steps["Administrar Negocios view"].status !== "PASS") {
        markSkipped(report, "Información General", "Skipped because Administrar Negocios view failed.");
      } else {
        const hasName = await page.locator("text=/[A-Za-zÁÉÍÓÚáéíóúÑñ]+\\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]+/").first().isVisible().catch(() => false);
        const hasEmail = await page.locator("text=/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}/").first().isVisible().catch(() => false);
        const hasBusinessPlan = await page.getByText("BUSINESS PLAN", { exact: true }).first().isVisible().catch(() => false);
        const hasCambiarPlan = await page.getByRole("button", { name: "Cambiar Plan" }).first().isVisible().catch(() => false);

        expect(hasName).toBeTruthy();
        expect(hasEmail).toBeTruthy();
        expect(hasBusinessPlan).toBeTruthy();
        expect(hasCambiarPlan).toBeTruthy();

        markPass(report, "Información General", "Name, email, plan text and 'Cambiar Plan' button are visible.");
      }

      // Step 6: Validate Detalles de la Cuenta
      if (report.steps["Administrar Negocios view"].status !== "PASS") {
        markSkipped(report, "Detalles de la Cuenta", "Skipped because Administrar Negocios view failed.");
      } else {
        await expect(page.getByText("Cuenta creada", { exact: true }).first()).toBeVisible({ timeout: 15_000 });
        await expect(page.getByText("Estado activo", { exact: true }).first()).toBeVisible({ timeout: 15_000 });
        await expect(page.getByText("Idioma seleccionado", { exact: true }).first()).toBeVisible({ timeout: 15_000 });

        markPass(report, "Detalles de la Cuenta", "Account details labels are visible.");
      }

      // Step 7: Validate Tus Negocios
      if (report.steps["Administrar Negocios view"].status !== "PASS") {
        markSkipped(report, "Tus Negocios", "Skipped because Administrar Negocios view failed.");
      } else {
        const businessListVisible =
          (await page.getByRole("list").first().isVisible().catch(() => false)) ||
          (await page.locator("table, [data-testid*='business']").first().isVisible().catch(() => false));
        const addBusinessButtonVisible = await page.getByRole("button", { name: "Agregar Negocio" }).first().isVisible().catch(() => false);
        const quotaTextVisible = await page.getByText("Tienes 2 de 3 negocios", { exact: true }).first().isVisible().catch(() => false);

        expect(businessListVisible).toBeTruthy();
        expect(addBusinessButtonVisible).toBeTruthy();
        expect(quotaTextVisible).toBeTruthy();

        markPass(report, "Tus Negocios", "Business list, add button and quota text are visible.");
      }

      // Step 8: Validate Términos y Condiciones
      if (report.steps["Administrar Negocios view"].status !== "PASS") {
        markSkipped(report, "Términos y Condiciones", "Skipped because Administrar Negocios view failed.");
      } else {
        await validateLegalLink(page, report, testInfo, "Términos y Condiciones", "Términos y Condiciones");
      }

      // Step 9: Validate Política de Privacidad
      if (report.steps["Administrar Negocios view"].status !== "PASS") {
        markSkipped(report, "Política de Privacidad", "Skipped because Administrar Negocios view failed.");
      } else {
        await validateLegalLink(page, report, testInfo, "Política de Privacidad", "Política de Privacidad");
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      let markedFailureStep = false;
      for (const step of ALL_STEPS) {
        if (!markedFailureStep && report.steps[step].status === "SKIPPED") {
          markFail(report, step, `Fatal execution error: ${message}`);
          markedFailureStep = true;
        } else if (report.steps[step].status === "SKIPPED" && report.steps[step].details.length === 0) {
          markSkipped(report, step, `Skipped after fatal execution error: ${message}`);
        }
      }
      throw error;
    } finally {
      await finalizeReport(report, testInfo);
    }

    // Hard assert mandatory steps with explicit diagnostics for cron consumers.
    for (const step of ALL_STEPS) {
      expect(report.steps[step].status, `${step} should be PASS. Details: ${report.steps[step].details.join(" | ")}`).toBe("PASS");
    }
  });
});
