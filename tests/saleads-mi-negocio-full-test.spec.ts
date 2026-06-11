import { expect, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type WorkflowField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type WorkflowReport = {
  name: string;
  executedAt: string;
  environment: string;
  finalUrls: {
    terminosYCondiciones: string | null;
    politicaDePrivacidad: string | null;
  };
  results: Record<WorkflowField, StepStatus>;
  failures: string[];
};

const REPORT_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page: Page): Promise<void> {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 10_000 }),
    page.waitForLoadState("networkidle", { timeout: 10_000 })
  ]);
  await page.waitForTimeout(700);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 20_000 });
  await locator.click();
  await waitForUi(page);
}

async function expectAnyVisible(locators: Locator[]): Promise<void> {
  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      return;
    }
  }

  throw new Error("None of the expected locators are visible.");
}

function sanitizeFileName(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9-_]+/g, "-");
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const screenshotsDir = path.join(process.cwd(), "artifacts", REPORT_NAME, "screenshots");
  const reportDir = path.join(process.cwd(), "artifacts", REPORT_NAME);
  fs.mkdirSync(screenshotsDir, { recursive: true });
  fs.mkdirSync(reportDir, { recursive: true });

  const results: Record<WorkflowField, StepStatus> = {
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

  const failures: string[] = [];
  let terminosYCondicionesUrl: string | null = null;
  let politicaDePrivacidadUrl: string | null = null;

  const screenshot = async (name: string, targetPage: Page = page, fullPage = false): Promise<void> => {
    const filePath = path.join(screenshotsDir, `${sanitizeFileName(name)}.png`);
    await targetPage.screenshot({ path: filePath, fullPage });
    await testInfo.attach(path.basename(filePath), {
      path: filePath,
      contentType: "image/png"
    });
  };

  const step = async (field: WorkflowField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      results[field] = "PASS";
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      failures.push(`${field}: ${message}`);
      results[field] = "FAIL";
    }
  };

  await step("Login", async () => {
    const startUrl = process.env.SALEADS_START_URL;
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    const googleLoginButton = page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n/i }).first();
    await clickAndWait(googleLoginButton, page);

    const accountSelector = page.getByText(GOOGLE_ACCOUNT, { exact: false }).first();
    if (await accountSelector.isVisible().catch(() => false)) {
      await clickAndWait(accountSelector, page);
    }

    await expectAnyVisible([
      page.locator("aside").first(),
      page.getByRole("navigation").first()
    ]);

    await screenshot("01-dashboard-loaded");
  });

  await step("Mi Negocio menu", async () => {
    await clickAndWait(page.getByText(/^Negocio$/i).first(), page);
    await clickAndWait(page.getByText(/^Mi Negocio$/i).first(), page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await screenshot("02-mi-negocio-menu-expanded");
  });

  await step("Agregar Negocio modal", async () => {
    await clickAndWait(page.getByText(/Agregar Negocio/i).first(), page);

    const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expectAnyVisible([
      modal.getByLabel(/Nombre del Negocio/i).first(),
      modal.getByPlaceholder(/Nombre del Negocio/i).first(),
      modal.getByText(/Nombre del Negocio/i).first()
    ]);
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    const businessNameField = modal
      .getByLabel(/Nombre del Negocio/i)
      .or(modal.getByPlaceholder(/Nombre del Negocio/i))
      .first();
    if (await businessNameField.isVisible().catch(() => false)) {
      await businessNameField.click();
      await businessNameField.fill("Negocio Prueba Automatización");
    }

    await screenshot("03-crear-nuevo-negocio-modal");
    await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }), page);
  });

  await step("Administrar Negocios view", async () => {
    const administrarNegocios = page.getByText(/Administrar Negocios/i).first();
    if (!(await administrarNegocios.isVisible().catch(() => false))) {
      await clickAndWait(page.getByText(/^Mi Negocio$/i).first(), page);
    }

    await clickAndWait(page.getByText(/Administrar Negocios/i).first(), page);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();

    await screenshot("04-administrar-negocios-account-page", page, true);
  });

  await step("Información General", async () => {
    await expectAnyVisible([
      page.getByText(/@[a-z0-9.-]+\.[a-z]{2,}/i).first(),
      page.getByText(GOOGLE_ACCOUNT, { exact: false }).first()
    ]);
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await step("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await step("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  });

  const validateLegalPage = async (
    field: "Términos y Condiciones" | "Política de Privacidad",
    linkText: RegExp,
    heading: RegExp,
    screenshotName: string
  ): Promise<string> => {
    const link = page.getByRole("link", { name: linkText }).first();
    await expect(link).toBeVisible();

    let popup: Page | null = null;
    await Promise.all([
      page
        .waitForEvent("popup", { timeout: 5_000 })
        .then((newPage) => {
          popup = newPage;
        })
        .catch(() => null),
      link.click()
    ]);

    const legalPage = popup ?? page;
    await waitForUi(legalPage);
    await expect(legalPage.getByRole("heading", { name: heading })).toBeVisible();
    const legalBody = legalPage.locator("body");
    const legalText = (await legalBody.innerText()).trim();
    expect(legalText.length).toBeGreaterThan(120);

    await screenshot(screenshotName, legalPage, true);
    const finalUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else {
      await page.goBack();
      await waitForUi(page);
    }

    return finalUrl;
  };

  await step("Términos y Condiciones", async () => {
    terminosYCondicionesUrl = await validateLegalPage(
      "Términos y Condiciones",
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "05-terminos-y-condiciones"
    );
  });

  await step("Política de Privacidad", async () => {
    politicaDePrivacidadUrl = await validateLegalPage(
      "Política de Privacidad",
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "06-politica-de-privacidad"
    );
  });

  const report: WorkflowReport = {
    name: REPORT_NAME,
    executedAt: new Date().toISOString(),
    environment: process.env.SALEADS_START_URL ?? "current-browser-login-page",
    finalUrls: {
      terminosYCondiciones: terminosYCondicionesUrl,
      politicaDePrivacidad: politicaDePrivacidadUrl
    },
    results,
    failures
  };

  const reportPath = path.join(reportDir, "final-report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  await testInfo.attach("final-report.json", { path: reportPath, contentType: "application/json" });

  if (failures.length > 0) {
    throw new Error(`Workflow completed with failures:\n${failures.join("\n")}`);
  }
});
