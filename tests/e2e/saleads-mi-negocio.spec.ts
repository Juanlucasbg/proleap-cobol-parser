import { expect, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepReport = {
  status: StepStatus;
  details: string;
};

type WorkflowReport = {
  testName: string;
  generatedAt: string;
  environment: string;
  finalUrls: Record<string, string>;
  screenshots: string[];
  steps: Record<string, StepReport>;
  overallStatus: StepStatus;
};

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
] as const;

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {
    // Some pages keep background network requests open; DOM ready is enough for this flow.
  });
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 20_000 });
  await locator.click();
  await waitForUiLoad(page);
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }
  return null;
}

function createReport(environment: string): WorkflowReport {
  const steps: Record<string, StepReport> = {};
  for (const field of REPORT_FIELDS) {
    steps[field] = {
      status: "FAIL",
      details: "Not executed."
    };
  }

  return {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment,
    finalUrls: {},
    screenshots: [],
    steps,
    overallStatus: "FAIL"
  };
}

function updateStep(
  report: WorkflowReport,
  stepName: (typeof REPORT_FIELDS)[number],
  status: StepStatus,
  details: string
): void {
  report.steps[stepName] = { status, details };
}

async function captureCheckpoint(page: Page, name: string, report: WorkflowReport): Promise<void> {
  const artifactsDir = path.join(process.cwd(), "tests", "e2e", "artifacts");
  fs.mkdirSync(artifactsDir, { recursive: true });

  const filename = `${Date.now()}-${name}.png`;
  const screenshotPath = path.join(artifactsDir, filename);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  report.screenshots.push(screenshotPath);
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const targetUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_APP_URL ?? process.env.SALEADS_BASE_URL;
  const environment = targetUrl ? new URL(targetUrl).origin : "runtime-browser-session";
  const report = createReport(environment);

  if (targetUrl) {
    await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No URL found. Set SALEADS_LOGIN_URL, SALEADS_APP_URL, or SALEADS_BASE_URL to run in any environment."
    );
  }

  // Step 1: Login with Google
  try {
    const loginControl = await firstVisible([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i).first()
    ]);

    if (!loginControl) {
      throw new Error("Google login button was not found.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 12_000 }).catch(() => null);
    await clickAndWait(loginControl, page);

    const popup = await popupPromise;

    if (popup) {
      await waitForUiLoad(popup);
      const accountOption = popup.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(accountOption, popup);
      }
      await popup.waitForEvent("close", { timeout: 30_000 }).catch(() => {
        // Popup may stay open after auth due to provider flow.
      });
    } else if (page.url().includes("accounts.google.com")) {
      const accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(accountOption, page);
      }
    }

    await waitForUiLoad(page);

    const sidebar = await firstVisible([
      page.locator("aside").first(),
      page.getByRole("navigation").first(),
      page.locator("[class*='sidebar']").first()
    ]);
    if (!sidebar) {
      throw new Error("Sidebar navigation did not appear after login.");
    }
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 30_000 });

    await captureCheckpoint(page, "01-dashboard-loaded", report);
    updateStep(report, "Login", "PASS", "Main interface loaded and left sidebar is visible.");
  } catch (error) {
    updateStep(report, "Login", "FAIL", `Login validation failed: ${(error as Error).message}`);
  }

  // Step 2: Open Mi Negocio menu
  try {
    const negocioEntry = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }).first(),
      page.getByRole("link", { name: /^Negocio$/i }).first(),
      page.getByText(/^Negocio$/i).first()
    ]);
    if (negocioEntry) {
      await clickAndWait(negocioEntry, page);
    }

    const miNegocioEntry = await firstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }).first(),
      page.getByRole("link", { name: /Mi Negocio/i }).first(),
      page.getByText(/Mi Negocio/i).first()
    ]);
    if (!miNegocioEntry) {
      throw new Error("Could not find 'Mi Negocio' in sidebar.");
    }
    await clickAndWait(miNegocioEntry, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20_000 });

    await captureCheckpoint(page, "02-mi-negocio-menu-expanded", report);
    updateStep(report, "Mi Negocio menu", "PASS", "Mi Negocio submenu expanded with expected options.");
  } catch (error) {
    updateStep(report, "Mi Negocio menu", "FAIL", `Mi Negocio menu validation failed: ${(error as Error).message}`);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const addBusinessTrigger = await firstVisible([
      page.getByRole("menuitem", { name: /Agregar Negocio/i }).first(),
      page.getByRole("button", { name: /Agregar Negocio/i }).first(),
      page.getByText(/^Agregar Negocio$/i).first()
    ]);
    if (!addBusinessTrigger) {
      throw new Error("Could not find 'Agregar Negocio' option.");
    }
    await clickAndWait(addBusinessTrigger, page);

    const modal = page.getByRole("dialog").first();
    await expect(modal).toBeVisible({ timeout: 20_000 });
    await expect(modal.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible();
    await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    const businessNameInput = await firstVisible([
      modal.getByLabel(/Nombre del Negocio/i).first(),
      modal.getByRole("textbox", { name: /Nombre del Negocio/i }).first(),
      modal.getByPlaceholder(/Nombre del Negocio/i).first()
    ]);
    if (businessNameInput) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await waitForUiLoad(page);
    }

    await captureCheckpoint(page, "03-agregar-negocio-modal", report);

    const cancelButton = modal.getByRole("button", { name: /Cancelar/i }).first();
    await clickAndWait(cancelButton, page);
    await expect(modal).toBeHidden({ timeout: 20_000 });

    updateStep(report, "Agregar Negocio modal", "PASS", "Modal content and controls validated.");
  } catch (error) {
    updateStep(
      report,
      "Agregar Negocio modal",
      "FAIL",
      `Agregar Negocio modal validation failed: ${(error as Error).message}`
    );
  }

  // Step 4: Open Administrar Negocios
  try {
    const adminOption = page.getByText(/Administrar Negocios/i).first();
    if (!(await adminOption.isVisible().catch(() => false))) {
      const miNegocioEntry = await firstVisible([
        page.getByRole("button", { name: /Mi Negocio/i }).first(),
        page.getByRole("link", { name: /Mi Negocio/i }).first(),
        page.getByText(/Mi Negocio/i).first()
      ]);
      if (miNegocioEntry) {
        await clickAndWait(miNegocioEntry, page);
      }
    }

    await clickAndWait(page.getByText(/Administrar Negocios/i).first(), page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 20_000 });

    await captureCheckpoint(page, "04-administrar-negocios", report);
    updateStep(report, "Administrar Negocios view", "PASS", "Account management view loaded with key sections.");
  } catch (error) {
    updateStep(
      report,
      "Administrar Negocios view",
      "FAIL",
      `Administrar Negocios validation failed: ${(error as Error).message}`
    );
  }

  // Step 5: Validate Información General
  try {
    const infoSection = page
      .locator("section, div")
      .filter({ has: page.getByText(/Información General/i).first() })
      .first();

    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 20_000 });
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 20_000 });
    await expect(infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)).toBeVisible({ timeout: 20_000 });

    const infoText = await infoSection.innerText();
    if (infoText.trim().length < 20) {
      throw new Error("Información General section does not contain enough visible user data.");
    }

    updateStep(
      report,
      "Información General",
      "PASS",
      "User data, BUSINESS PLAN text, and Cambiar Plan button are visible."
    );
  } catch (error) {
    updateStep(
      report,
      "Información General",
      "FAIL",
      `Información General validation failed: ${(error as Error).message}`
    );
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });

    updateStep(report, "Detalles de la Cuenta", "PASS", "Detalles de la Cuenta labels are visible.");
  } catch (error) {
    updateStep(
      report,
      "Detalles de la Cuenta",
      "FAIL",
      `Detalles de la Cuenta validation failed: ${(error as Error).message}`
    );
  }

  // Step 7: Validate Tus Negocios
  try {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });

    updateStep(report, "Tus Negocios", "PASS", "Business list and count information are visible.");
  } catch (error) {
    updateStep(report, "Tus Negocios", "FAIL", `Tus Negocios validation failed: ${(error as Error).message}`);
  }

  const validateLegalLink = async (
    linkText: string,
    expectedHeading: string,
    reportField: "Términos y Condiciones" | "Política de Privacidad",
    screenshotName: string,
    urlKey: string
  ): Promise<void> => {
    const appPage = page;
    const legalLink = await firstVisible([
      appPage.getByRole("link", { name: new RegExp(linkText, "i") }).first(),
      appPage.getByRole("button", { name: new RegExp(linkText, "i") }).first(),
      appPage.getByText(new RegExp(linkText, "i")).first()
    ]);

    if (!legalLink) {
      throw new Error(`Could not find legal link '${linkText}'.`);
    }

    const newPagePromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickAndWait(legalLink, appPage);
    const newPage = await newPagePromise;

    const legalPage = newPage ?? appPage;
    await waitForUiLoad(legalPage);

    await expect(legalPage.getByRole("heading", { name: new RegExp(expectedHeading, "i") })).toBeVisible({
      timeout: 25_000
    });
    const legalBody = await legalPage.locator("body").innerText();
    if (legalBody.trim().length < 150) {
      throw new Error(`Legal content for '${expectedHeading}' appears too short.`);
    }

    report.finalUrls[urlKey] = legalPage.url();
    await captureCheckpoint(legalPage, screenshotName, report);

    if (newPage) {
      await newPage.close();
      await appPage.bringToFront();
      await waitForUiLoad(appPage);
    } else {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {
        // Ignore if the app uses in-app navigation without browser history.
      });
      await waitForUiLoad(appPage);
    }

    updateStep(report, reportField, "PASS", `${expectedHeading} content is visible and URL captured.`);
  };

  // Step 8: Validate Términos y Condiciones
  try {
    await validateLegalLink(
      "Términos y Condiciones",
      "Términos y Condiciones",
      "Términos y Condiciones",
      "08-terminos-y-condiciones",
      "terminosYCondiciones"
    );
  } catch (error) {
    updateStep(
      report,
      "Términos y Condiciones",
      "FAIL",
      `Términos y Condiciones validation failed: ${(error as Error).message}`
    );
  }

  // Step 9: Validate Política de Privacidad
  try {
    await validateLegalLink(
      "Política de Privacidad",
      "Política de Privacidad",
      "Política de Privacidad",
      "09-politica-de-privacidad",
      "politicaDePrivacidad"
    );
  } catch (error) {
    updateStep(
      report,
      "Política de Privacidad",
      "FAIL",
      `Política de Privacidad validation failed: ${(error as Error).message}`
    );
  }

  report.overallStatus = REPORT_FIELDS.every((field) => report.steps[field].status === "PASS") ? "PASS" : "FAIL";
  report.generatedAt = new Date().toISOString();

  const artifactsDir = path.join(process.cwd(), "tests", "e2e", "artifacts");
  fs.mkdirSync(artifactsDir, { recursive: true });
  const reportPath = path.join(artifactsDir, "mi-negocio-latest-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf-8");

  console.log("=== saleads_mi_negocio_full_test final report ===");
  for (const field of REPORT_FIELDS) {
    const step = report.steps[field];
    console.log(`${field}: ${step.status} - ${step.details}`);
  }
  console.log(`Overall: ${report.overallStatus}`);
  console.log(`Report file: ${reportPath}`);
  if (Object.keys(report.finalUrls).length > 0) {
    console.log("Captured legal URLs:", report.finalUrls);
  }

  expect(report.overallStatus, "One or more workflow validations failed. Review mi-negocio-latest-report.json.").toBe(
    "PASS"
  );
});
