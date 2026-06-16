import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import * as fs from "node:fs/promises";
import * as path from "node:path";

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

type StepResult = {
  status: "PASS" | "FAIL";
  details: string[];
};

type WorkflowReport = {
  name: string;
  generatedAt: string;
  startUrl?: string;
  finalAppUrl?: string;
  legalUrls: {
    terminosYCondiciones?: string;
    politicaDePrivacidad?: string;
  };
  results: Record<ReportField, StepResult>;
};

const START_URL = process.env.SALEADS_START_URL;
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
  "Política de Privacidad"
];

function initReport(): WorkflowReport {
  const results = {} as Record<ReportField, StepResult>;
  for (const field of REPORT_FIELDS) {
    results[field] = {
      status: "FAIL",
      details: ["Step not executed."]
    };
  }

  return {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    startUrl: START_URL,
    legalUrls: {
      terminosYCondiciones: undefined,
      politicaDePrivacidad: undefined
    },
    results
  };
}

function markPass(report: WorkflowReport, field: ReportField, message: string): void {
  report.results[field] = { status: "PASS", details: [message] };
}

function markFail(report: WorkflowReport, field: ReportField, err: unknown): void {
  const message = err instanceof Error ? err.message : String(err);
  report.results[field] = { status: "FAIL", details: [message] };
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiLoad(page);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const count = await candidate.count();
    if (count > 0 && (await candidate.first().isVisible())) {
      return candidate.first();
    }
  }
  return null;
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  await page.screenshot({ path: testInfo.outputPath(name), fullPage });
}

async function withStep(report: WorkflowReport, field: ReportField, fn: () => Promise<void>): Promise<void> {
  try {
    await fn();
    if (report.results[field].status !== "FAIL" || report.results[field].details[0] === "Step not executed.") {
      markPass(report, field, "All required validations passed.");
    }
  } catch (err) {
    markFail(report, field, err);
  }
}

async function openLegalAndValidate(
  page: Page,
  testInfo: TestInfo,
  linkLabel: RegExp,
  headingText: RegExp,
  screenshotName: string,
  accountPageUrl: string | undefined
): Promise<{ finalUrl: string }> {
  const link = page.getByRole("link", { name: linkLabel }).first();
  await expect(link).toBeVisible();

  const popupPromise = page.context().waitForEvent("page", { timeout: 5000 }).catch(() => null);
  await link.click();
  await page.waitForTimeout(800);
  const popup = await popupPromise;

  const targetPage = popup ?? page;
  await targetPage.waitForLoadState("domcontentloaded");
  await expect(targetPage.getByRole("heading", { name: headingText }).first()).toBeVisible();
  await expect(targetPage.locator("p, li").first()).toBeVisible();

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else if (accountPageUrl) {
    await page.goto(accountPageUrl, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(800);
  }

  return { finalUrl };
}

test("SaleADS - Mi Negocio full workflow", async ({ page }, testInfo) => {
  test.setTimeout(Number(process.env.SALEADS_TEST_TIMEOUT_MS || 180000));

  const report = initReport();
  let accountPageUrl: string | undefined;

  if (!START_URL) {
    throw new Error("Set SALEADS_START_URL to the login page for your current environment.");
  }

  await page.goto(START_URL, { waitUntil: "domcontentloaded" });

  await withStep(report, "Login", async () => {
    const loginTrigger = await firstVisible([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
    ]);

    if (!loginTrigger) {
      throw new Error("Could not find a Google login trigger.");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 5000 }).catch(() => null);
    await clickAndWait(page, loginTrigger);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }
    } else {
      const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }
    }

    await expect(page.locator("main, [role='main']").first()).toBeVisible({ timeout: 60000 });
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await withStep(report, "Mi Negocio menu", async () => {
    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);

    if (!miNegocio) {
      throw new Error("Could not find 'Mi Negocio' in sidebar.");
    }

    await clickAndWait(page, miNegocio);
    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();
    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await withStep(report, "Agregar Negocio modal", async () => {
    await clickAndWait(page, page.getByText(/^Agregar Negocio$/i).first());

    await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i))).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    const nombreInput = page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i)).first();
    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }).first());
  });

  await withStep(report, "Administrar Negocios view", async () => {
    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);
    if (miNegocio && !(await page.getByText(/^Administrar Negocios$/i).isVisible().catch(() => false))) {
      await clickAndWait(page, miNegocio);
    }

    await clickAndWait(page, page.getByText(/^Administrar Negocios$/i).first());

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();

    accountPageUrl = page.url();
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page.png", true);
  });

  await withStep(report, "Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    await expect(page.getByText(/@/)).toBeVisible();
    await expect(page.locator("section, div").filter({ hasText: /Información General/i }).first()).toBeVisible();
  });

  await withStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await withStep(report, "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
  });

  await withStep(report, "Términos y Condiciones", async () => {
    const outcome = await openLegalAndValidate(
      page,
      testInfo,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones.png",
      accountPageUrl
    );
    report.legalUrls.terminosYCondiciones = outcome.finalUrl;
  });

  await withStep(report, "Política de Privacidad", async () => {
    const outcome = await openLegalAndValidate(
      page,
      testInfo,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
      "06-politica-de-privacidad.png",
      accountPageUrl
    );
    report.legalUrls.politicaDePrivacidad = outcome.finalUrl;
  });

  report.generatedAt = new Date().toISOString();
  report.finalAppUrl = page.url();

  const reportDir = path.resolve(__dirname, "..", "reports");
  await fs.mkdir(reportDir, { recursive: true });
  const reportPath = path.join(reportDir, "saleads-mi-negocio-last-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedFields = REPORT_FIELDS.filter((field) => report.results[field].status === "FAIL");
  expect(failedFields, `One or more required validations failed. Report: ${reportPath}`).toEqual([]);
});
