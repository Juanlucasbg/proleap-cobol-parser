import { expect, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

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

type Status = "PASS" | "FAIL";

interface ReportItem {
  status: Status;
  notes: string[];
  evidence: string[];
  finalUrl?: string;
}

type WorkflowReport = Record<ReportField, ReportItem>;

const GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const workflowName = "saleads_mi_negocio_full_test";

function buildInitialReport(): WorkflowReport {
  const fields: ReportField[] = [
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

  return fields.reduce((acc, field) => {
    acc[field] = {
      status: "FAIL",
      notes: ["Step not completed."],
      evidence: [],
    };
    return acc;
  }, {} as WorkflowReport);
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
  await page.waitForTimeout(500);
}

async function isAnyVisible(page: Page, selectors: string[]): Promise<boolean> {
  for (const selector of selectors) {
    const visible = await page.locator(selector).first().isVisible().catch(() => false);
    if (visible) {
      return true;
    }
  }
  return false;
}

async function clickByVisibleText(page: Page, labels: string[]): Promise<string> {
  for (const label of labels) {
    const namePattern = new RegExp(escapeRegex(label), "i");
    const locators = [
      page.getByRole("button", { name: namePattern }).first(),
      page.getByRole("link", { name: namePattern }).first(),
      page.getByRole("menuitem", { name: namePattern }).first(),
      page.getByText(namePattern).first(),
    ];

    for (const locator of locators) {
      const visible = await locator.isVisible().catch(() => false);
      if (visible) {
        await locator.click();
        await waitForUiLoad(page);
        return label;
      }
    }
  }

  throw new Error(`No visible element found for labels: ${labels.join(", ")}`);
}

async function validateTextVisible(page: Page, value: string): Promise<void> {
  await expect(page.getByText(new RegExp(escapeRegex(value), "i")).first()).toBeVisible();
}

async function maybeSelectGoogleAccount(page: Page, email: string): Promise<boolean> {
  const labelPattern = new RegExp(escapeRegex(email), "i");
  const accountByText = page.getByText(labelPattern).first();
  if (await accountByText.isVisible().catch(() => false)) {
    await accountByText.click();
    await waitForUiLoad(page);
    return true;
  }

  const accountByRole = page.getByRole("button", { name: labelPattern }).first();
  if (await accountByRole.isVisible().catch(() => false)) {
    await accountByRole.click();
    await waitForUiLoad(page);
    return true;
  }

  return false;
}

async function captureCheckpoint(page: Page, filePath: string, fullPage = false): Promise<string> {
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.PLAYWRIGHT_TEST_BASE_URL;
  const outputDir = path.resolve(
    process.cwd(),
    process.env.SALEADS_OUTPUT_DIR ?? "test-results/saleads_mi_negocio_full_test",
  );
  await fs.mkdir(outputDir, { recursive: true });

  const report = buildInitialReport();

  const runStep = async (field: ReportField, body: () => Promise<void>): Promise<void> => {
    const notes: string[] = [];
    try {
      await body();
      report[field] = {
        ...report[field],
        status: "PASS",
        notes: notes.length ? notes : ["All validations passed."],
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[field] = {
        ...report[field],
        status: "FAIL",
        notes: [...notes, message],
      };
    }
  };

  await runStep("Login", async () => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    } else {
      await waitForUiLoad(page);
      if (page.url() === "about:blank") {
        throw new Error(
          "No login page available. Provide SALEADS_LOGIN_URL (or PLAYWRIGHT_TEST_BASE_URL) for the current environment.",
        );
      }
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Ingresar con Google",
      "Acceder con Google",
      "Login with Google",
      "Google",
    ]);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await maybeSelectGoogleAccount(popup, GOOGLE_EMAIL);
      await popup.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
    } else {
      await maybeSelectGoogleAccount(page, GOOGLE_EMAIL);
    }

    await waitForUiLoad(page);
    const sidebarVisible = await isAnyVisible(page, ["aside", "nav", "[role='navigation']"]);
    await expect(sidebarVisible).toBeTruthy();

    const dashboardScreenshot = path.join(outputDir, "01-dashboard-loaded.png");
    report["Login"].evidence.push(await captureCheckpoint(page, dashboardScreenshot));
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, ["Negocio"]);
    await clickByVisibleText(page, ["Mi Negocio"]);

    await validateTextVisible(page, "Agregar Negocio");
    await validateTextVisible(page, "Administrar Negocios");

    const menuScreenshot = path.join(outputDir, "02-mi-negocio-menu-expanded.png");
    report["Mi Negocio menu"].evidence.push(await captureCheckpoint(page, menuScreenshot));
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, ["Agregar Negocio"]);
    await validateTextVisible(page, "Crear Nuevo Negocio");
    await validateTextVisible(page, "Nombre del Negocio");
    await validateTextVisible(page, "Tienes 2 de 3 negocios");
    await validateTextVisible(page, "Cancelar");
    await validateTextVisible(page, "Crear Negocio");

    const modalScreenshot = path.join(outputDir, "03-agregar-negocio-modal.png");
    report["Agregar Negocio modal"].evidence.push(await captureCheckpoint(page, modalScreenshot));

    const businessNameInput = page.getByPlaceholder(/nombre del negocio/i).first();
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await waitForUiLoad(page);
    }

    await clickByVisibleText(page, ["Cancelar"]);
  });

  await runStep("Administrar Negocios view", async () => {
    const adminVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!adminVisible) {
      await clickByVisibleText(page, ["Mi Negocio"]);
    }

    await clickByVisibleText(page, ["Administrar Negocios"]);

    await validateTextVisible(page, "Información General");
    await validateTextVisible(page, "Detalles de la Cuenta");
    await validateTextVisible(page, "Tus Negocios");
    await validateTextVisible(page, "Sección Legal");

    const accountPageScreenshot = path.join(outputDir, "04-administrar-negocios-page.png");
    report["Administrar Negocios view"].evidence.push(
      await captureCheckpoint(page, accountPageScreenshot, true),
    );
  });

  await runStep("Información General", async () => {
    const infoSection = page.getByText(/Información General/i).first();
    await expect(infoSection).toBeVisible();

    await expect(page.getByText(/@/).first()).toBeVisible();
    await validateTextVisible(page, "BUSINESS PLAN");
    await validateTextVisible(page, "Cambiar Plan");
  });

  await runStep("Detalles de la Cuenta", async () => {
    await validateTextVisible(page, "Cuenta creada");
    await validateTextVisible(page, "Estado activo");
    await validateTextVisible(page, "Idioma seleccionado");
  });

  await runStep("Tus Negocios", async () => {
    await validateTextVisible(page, "Tus Negocios");
    await validateTextVisible(page, "Agregar Negocio");
    await validateTextVisible(page, "Tienes 2 de 3 negocios");
  });

  await runStep("Términos y Condiciones", async () => {
    const appPage = page;
    const appUrlBeforeClick = appPage.url();
    const newPagePromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

    await clickByVisibleText(appPage, ["Términos y Condiciones"]);
    const legalPage = (await newPagePromise) ?? appPage;

    if (legalPage !== appPage) {
      await legalPage.waitForLoadState("domcontentloaded");
      await legalPage.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => null);
    }

    await validateTextVisible(legalPage, "Términos y Condiciones");
    const legalScreenshot = path.join(outputDir, "05-terminos-y-condiciones.png");
    report["Términos y Condiciones"].evidence.push(
      await captureCheckpoint(legalPage, legalScreenshot, true),
    );
    report["Términos y Condiciones"].finalUrl = legalPage.url();

    if (legalPage !== appPage) {
      await legalPage.close();
      await appPage.bringToFront();
      await waitForUiLoad(appPage);
    } else if (appPage.url() !== appUrlBeforeClick) {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUiLoad(appPage);
    }
  });

  await runStep("Política de Privacidad", async () => {
    const appPage = page;
    const appUrlBeforeClick = appPage.url();
    const newPagePromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

    await clickByVisibleText(appPage, ["Política de Privacidad"]);
    const legalPage = (await newPagePromise) ?? appPage;

    if (legalPage !== appPage) {
      await legalPage.waitForLoadState("domcontentloaded");
      await legalPage.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => null);
    }

    await validateTextVisible(legalPage, "Política de Privacidad");
    const policyScreenshot = path.join(outputDir, "06-politica-de-privacidad.png");
    report["Política de Privacidad"].evidence.push(await captureCheckpoint(legalPage, policyScreenshot, true));
    report["Política de Privacidad"].finalUrl = legalPage.url();

    if (legalPage !== appPage) {
      await legalPage.close();
      await appPage.bringToFront();
      await waitForUiLoad(appPage);
    } else if (appPage.url() !== appUrlBeforeClick) {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUiLoad(appPage);
    }
  });

  const finalReportPath = path.join(outputDir, `${workflowName}-report.json`);
  await fs.writeFile(
    finalReportPath,
    `${JSON.stringify(
      {
        workflow: workflowName,
        generatedAt: new Date().toISOString(),
        report,
      },
      null,
      2,
    )}\n`,
    "utf-8",
  );

  const failedFields = Object.entries(report)
    .filter(([, value]) => value.status === "FAIL")
    .map(([field]) => field);

  expect(failedFields, `Failing report fields: ${failedFields.join(", ")}`).toEqual([]);
});
