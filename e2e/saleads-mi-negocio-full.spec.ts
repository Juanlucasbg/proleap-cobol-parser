import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const FIELD_LOGIN = "Login";
const FIELD_MENU = "Mi Negocio menu";
const FIELD_MODAL = "Agregar Negocio modal";
const FIELD_ADMIN_VIEW = "Administrar Negocios view";
const FIELD_INFO_GENERAL = "Informaci\u00f3n General";
const FIELD_ACCOUNT_DETAILS = "Detalles de la Cuenta";
const FIELD_BUSINESSES = "Tus Negocios";
const FIELD_TERMS = "T\u00e9rminos y Condiciones";
const FIELD_PRIVACY = "Pol\u00edtica de Privacidad";

const REPORT_FIELDS = [
  FIELD_LOGIN,
  FIELD_MENU,
  FIELD_MODAL,
  FIELD_ADMIN_VIEW,
  FIELD_INFO_GENERAL,
  FIELD_ACCOUNT_DETAILS,
  FIELD_BUSINESSES,
  FIELD_TERMS,
  FIELD_PRIVACY
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type ValidationReport = Record<ReportField, StepStatus>;

function initializeReport(): ValidationReport {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])) as ValidationReport;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  const filePath = testInfo.outputPath(`screenshots/${name}.png`);
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await page.screenshot({ path: filePath, fullPage });
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator.first().isVisible().catch(() => false);
}

async function firstVisible(locators: Locator[]): Promise<Locator> {
  for (const locator of locators) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }

  throw new Error("No expected element is currently visible.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function openLegalTarget(page: Page, link: Locator): Promise<{ targetPage: Page; openedNewTab: boolean }> {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await link.click();

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForTimeout(1000);
    return { targetPage: popup, openedNewTab: true };
  }

  await waitForUi(page);
  return { targetPage: page, openedNewTab: false };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;
  test.skip(!loginUrl, "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to the environment login URL.");

  const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT ?? DEFAULT_GOOGLE_ACCOUNT;
  const report = initializeReport();
  const failures: string[] = [];
  const legalUrls: { terms: string | null; privacy: string | null } = { terms: null, privacy: null };

  const runField = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      failures.push(`${field}: ${errorMessage(error)}`);
    }
  };

  await page.goto(loginUrl as string, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runField(FIELD_LOGIN, async () => {
    const loginButton = await firstVisible([
      page.getByRole("button", { name: /google|sign in|iniciar sesi[o\u00f3]n|login|continuar/i }),
      page.getByRole("link", { name: /google|sign in|iniciar sesi[o\u00f3]n|login|continuar/i }),
      page.getByText(/google|sign in|iniciar sesi[o\u00f3]n|login|continuar/i)
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;
    const authPage = popup ?? page;

    await waitForUi(authPage);

    const accountOption = authPage.getByText(googleAccount, { exact: false }).first();
    if (await isVisible(accountOption)) {
      await accountOption.click();
      await waitForUi(page);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 120000 });
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 120000 });
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded");
  });

  await runField(FIELD_MENU, async () => {
    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);

    await clickAndWait(page, miNegocio);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
  });

  await runField(FIELD_MODAL, async () => {
    const addBusinessEntry = await firstVisible([
      page.getByRole("button", { name: /Agregar Negocio/i }),
      page.getByRole("link", { name: /Agregar Negocio/i }),
      page.getByText(/Agregar Negocio/i)
    ]);

    await clickAndWait(page, addBusinessEntry);
    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible();

    const businessNameField = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator('[role="dialog"] input, .modal input').first()
    ]);

    await expect(businessNameField).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    const cancelButton = page.getByRole("button", { name: /Cancelar/i }).first();
    const createButton = page.getByRole("button", { name: /Crear Negocio/i }).first();
    await expect(cancelButton).toBeVisible();
    await expect(createButton).toBeVisible();
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");

    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, cancelButton);
    await expect(modalTitle).toBeHidden();
  });

  await runField(FIELD_ADMIN_VIEW, async () => {
    const adminVisible = await isVisible(page.getByText(/Administrar Negocios/i));
    if (!adminVisible) {
      const miNegocio = await firstVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ]);
      await clickAndWait(page, miNegocio);
    }

    const adminEntry = await firstVisible([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i)
    ]);

    await clickAndWait(page, adminEntry);
    await expect(page.getByText(/Informaci[o\u00f3]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[o\u00f3]n Legal/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "04-administrar-negocios", true);
  });

  await runField(FIELD_INFO_GENERAL, async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const emailPattern = /@/;
    await expect(page.getByText(emailPattern).first()).toBeVisible();

    const infoSection = page.locator("section, div").filter({ has: page.getByText(/Informaci[o\u00f3]n General/i) }).first();
    await expect(infoSection).toBeVisible();
    const infoText = (await infoSection.textContent()) ?? "";
    const containsPotentialName = /\b[A-Za-z]{3,}\s+[A-Za-z]{3,}\b/.test(infoText);
    expect(containsPotentialName).toBeTruthy();
  });

  await runField(FIELD_ACCOUNT_DETAILS, async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runField(FIELD_BUSINESSES, async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runField(FIELD_TERMS, async () => {
    const startingUrl = page.url();
    const termsLink = await firstVisible([
      page.getByRole("link", { name: /T[e\u00e9]rminos y Condiciones/i }),
      page.getByText(/T[e\u00e9]rminos y Condiciones/i)
    ]);

    const { targetPage, openedNewTab } = await openLegalTarget(page, termsLink);
    await expect(targetPage.getByText(/T[e\u00e9]rminos y Condiciones/i).first()).toBeVisible();
    await expect(targetPage.locator("p, li").first()).toBeVisible();

    legalUrls.terms = targetPage.url();
    await captureCheckpoint(targetPage, testInfo, "05-terminos-y-condiciones", true);

    if (openedNewTab) {
      await targetPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (page.url() !== startingUrl) {
      await page.goBack();
      await waitForUi(page);
    }
  });

  await runField(FIELD_PRIVACY, async () => {
    const startingUrl = page.url();
    const privacyLink = await firstVisible([
      page.getByRole("link", { name: /Pol[i\u00ed]tica de Privacidad/i }),
      page.getByText(/Pol[i\u00ed]tica de Privacidad/i)
    ]);

    const { targetPage, openedNewTab } = await openLegalTarget(page, privacyLink);
    await expect(targetPage.getByText(/Pol[i\u00ed]tica de Privacidad/i).first()).toBeVisible();
    await expect(targetPage.locator("p, li").first()).toBeVisible();

    legalUrls.privacy = targetPage.url();
    await captureCheckpoint(targetPage, testInfo, "06-politica-de-privacidad", true);

    if (openedNewTab) {
      await targetPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (page.url() !== startingUrl) {
      await page.goBack();
      await waitForUi(page);
    }
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    timestamp: new Date().toISOString(),
    statusByField: report,
    legalUrls
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  // eslint-disable-next-line no-console
  console.log(`Final report: ${JSON.stringify(finalReport, null, 2)}`);

  if (failures.length > 0) {
    throw new Error(`Workflow validation failed:\n${failures.join("\n")}`);
  }
});
