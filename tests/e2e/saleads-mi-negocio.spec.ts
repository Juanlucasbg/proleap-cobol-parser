import { expect, test, type Locator, type Page } from "@playwright/test";
import { writeFile } from "node:fs/promises";
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

type StepStatus = "PASS" | "FAIL" | "NOT_RUN";

interface StepResult {
  status: StepStatus;
  details?: string;
  evidence: string[];
  finalUrl?: string;
}

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const reportFields: ReportField[] = [
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

function buildReport(): Record<ReportField, StepResult> {
  return reportFields.reduce(
    (acc, field) => ({
      ...acc,
      [field]: {
        status: "NOT_RUN",
        evidence: []
      }
    }),
    {} as Record<ReportField, StepResult>
  );
}

function failMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToLoad(page);
}

function clickableByText(page: Page, label: RegExp): Locator {
  return page.locator("button, a, [role='button'], [role='link']").filter({ hasText: label }).first();
}

async function capture(
  page: Page,
  report: Record<ReportField, StepResult>,
  field: ReportField,
  fileName: string,
  fullPage = false
): Promise<void> {
  const screenshotPath = test.info().outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  report[field].evidence.push(path.basename(screenshotPath));
}

async function runStep(
  report: Record<ReportField, StepResult>,
  field: ReportField,
  action: () => Promise<void>
): Promise<void> {
  try {
    await action();
    report[field].status = "PASS";
  } catch (error) {
    report[field].status = "FAIL";
    report[field].details = failMessage(error);
  }
}

async function openLegalLinkAndValidate(
  page: Page,
  report: Record<ReportField, StepResult>,
  field: "Términos y Condiciones" | "Política de Privacidad",
  triggerLabel: RegExp,
  headingLabel: RegExp,
  screenshotName: string
): Promise<void> {
  const linkOrButton = clickableByText(page, triggerLabel);
  await expect(linkOrButton).toBeVisible();

  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await linkOrButton.click();
  const popup = await popupPromise;
  const legalPage = popup ?? page;

  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForLoadState("networkidle").catch(() => undefined);

  await expect(legalPage.getByRole("heading", { name: headingLabel }).first()).toBeVisible();
  await expect(legalPage.locator("main, article, section, p, div").filter({ hasText: /\S+/ }).first()).toBeVisible();

  await capture(legalPage, report, field, screenshotName, true);
  report[field].finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else {
    await page.goBack();
    await waitForUiToLoad(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report = buildReport();
  const configuredUrl = process.env.SALEADS_START_URL ?? process.env.BASE_URL;

  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Open the SaleADS login page before running or set SALEADS_START_URL/BASE_URL to the current environment login URL."
    );
  }

  await runStep(report, "Login", async () => {
    const loginButton = clickableByText(
      page,
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
    );

    await expect(loginButton).toBeVisible();
    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);

    await loginButton.click();
    const popup = await popupPromise;
    const authPage = popup ?? page;

    await authPage.waitForLoadState("domcontentloaded");

    const accountSelection = authPage.getByText(ACCOUNT_EMAIL).first();
    if (await accountSelection.isVisible({ timeout: 7000 }).catch(() => false)) {
      await accountSelection.click();
      await waitForUiToLoad(authPage);
    }

    if (popup) {
      await page.waitForLoadState("domcontentloaded");
      await page.waitForLoadState("networkidle").catch(() => undefined);
    } else {
      await waitForUiToLoad(page);
    }

    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible();

    await capture(page, report, "Login", "01-dashboard-loaded.png");
  });

  await runStep(report, "Mi Negocio menu", async () => {
    const negocio = page.locator("aside, nav").getByText(/negocio/i).first();
    await clickAndWait(negocio, page);

    const miNegocio = page.locator("aside, nav").getByText(/mi negocio/i).first();
    await clickAndWait(miNegocio, page);

    await expect(page.locator("aside, nav").getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.locator("aside, nav").getByText(/administrar negocios/i).first()).toBeVisible();

    await capture(page, report, "Mi Negocio menu", "02-mi-negocio-menu-expanded.png");
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    const agregarNegocio = page.locator("aside, nav").getByText(/agregar negocio/i).first();
    await clickAndWait(agregarNegocio, page);

    const modal = page.getByRole("dialog").first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
    await expect(modal.getByText("Nombre del Negocio", { exact: false })).toBeVisible();
    await expect(modal.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
    await expect(modal.getByRole("button", { name: "Cancelar" })).toBeVisible();
    await expect(modal.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

    const nombreInput = modal.getByLabel("Nombre del Negocio").or(modal.getByPlaceholder(/nombre del negocio/i)).first();
    if (await nombreInput.isVisible().catch(() => false)) {
      await nombreInput.click();
      await nombreInput.fill("Negocio Prueba Automatización");
    }

    await capture(page, report, "Agregar Negocio modal", "03-agregar-negocio-modal.png");

    await clickAndWait(modal.getByRole("button", { name: "Cancelar" }), page);
    await expect(modal).toBeHidden();
  });

  await runStep(report, "Administrar Negocios view", async () => {
    const miNegocio = page.locator("aside, nav").getByText(/mi negocio/i).first();
    if (!(await page.locator("aside, nav").getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      await clickAndWait(miNegocio, page);
    }

    const administrar = page.locator("aside, nav").getByText(/administrar negocios/i).first();
    await clickAndWait(administrar, page);
    await page.waitForLoadState("networkidle").catch(() => undefined);

    await expect(page.getByText("Información General", { exact: true })).toBeVisible();
    await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
    await expect(page.getByText(/sección legal/i)).toBeVisible();

    await capture(page, report, "Administrar Negocios view", "04-administrar-negocios-view.png", true);
  });

  await runStep(report, "Información General", async () => {
    const infoSection = page.locator("section, div").filter({ hasText: /información general/i }).first();

    await expect(infoSection).toBeVisible();
    await expect(infoSection.getByText(/@/)).toBeVisible();
    await expect(infoSection.locator("strong, h1, h2, h3, p, span").filter({ hasText: /\S+/ }).first()).toBeVisible();
    await expect(infoSection.getByText(/business plan/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    const detailsSection = page.locator("section, div").filter({ hasText: /detalles de la cuenta/i }).first();

    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    const businessSection = page.locator("section, div").filter({ hasText: /tus negocios/i }).first();

    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(businessSection.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(businessSection.locator("li, tr, div").filter({ hasText: /\S+/ }).first()).toBeVisible();
  });

  await runStep(report, "Términos y Condiciones", async () => {
    await openLegalLinkAndValidate(
      page,
      report,
      "Términos y Condiciones",
      /t[ée]rminos y condiciones/i,
      /t[ée]rminos y condiciones/i,
      "05-terminos-y-condiciones.png"
    );
  });

  await runStep(report, "Política de Privacidad", async () => {
    await openLegalLinkAndValidate(
      page,
      report,
      "Política de Privacidad",
      /pol[íi]tica de privacidad/i,
      /pol[íi]tica de privacidad/i,
      "06-politica-de-privacidad.png"
    );
  });

  const reportOutput = {
    generatedAt: new Date().toISOString(),
    startUrl: configuredUrl ?? page.url(),
    results: report
  };

  const reportPath = test.info().outputPath("saleads-mi-negocio-report.json");
  await writeFile(reportPath, `${JSON.stringify(reportOutput, null, 2)}\n`, "utf8");
  await test.info().attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failingFields = Object.entries(report)
    .filter(([, result]) => result.status !== "PASS")
    .map(([field, result]) => `${field}: ${result.status}${result.details ? ` - ${result.details}` : ""}`);

  expect(
    failingFields,
    `Final report contains failing fields:\n${failingFields.join("\n")}\n\nFull report:\n${JSON.stringify(reportOutput, null, 2)}`
  ).toHaveLength(0);
});
