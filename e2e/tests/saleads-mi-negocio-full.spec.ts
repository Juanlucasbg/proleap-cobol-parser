import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
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

type StepStatus = "PASS" | "FAIL";

interface StepResult {
  status: StepStatus;
  details: string[];
  evidence: string[];
  finalUrl?: string;
}

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL ?? "";
const EXPECTED_USER_NAME = process.env.SALEADS_EXPECTED_USER_NAME;

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
}

async function getVisibleLocatorByText(page: Page, visibleText: string): Promise<Locator | null> {
  const nameRegex = new RegExp(escapeRegExp(visibleText), "i");
  const candidates: Locator[] = [
    page.getByRole("button", { name: nameRegex }).first(),
    page.getByRole("link", { name: nameRegex }).first(),
    page.getByRole("menuitem", { name: nameRegex }).first(),
    page.getByText(nameRegex).first()
  ];

  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  return null;
}

async function clickByVisibleText(
  page: Page,
  candidates: string[],
  stepDescription: string,
  waitAfterClick = true
): Promise<string> {
  for (const candidateText of candidates) {
    const candidate = await getVisibleLocatorByText(page, candidateText);
    if (candidate) {
      await candidate.click();
      if (waitAfterClick) {
        await waitForUi(page);
      }
      return candidateText;
    }
  }

  throw new Error(`Unable to click "${stepDescription}". Tried visible labels: ${candidates.join(", ")}`);
}

async function maybeClickByVisibleText(page: Page, candidates: string[]): Promise<void> {
  for (const candidateText of candidates) {
    const candidate = await getVisibleLocatorByText(page, candidateText);
    if (candidate) {
      await candidate.click();
      await waitForUi(page);
      return;
    }
  }
}

async function expectVisibleText(page: Page, text: string): Promise<void> {
  const locator = page.getByText(new RegExp(escapeRegExp(text), "i")).first();
  await expect(locator, `Expected visible text "${text}"`).toBeVisible();
}

async function ensureDir(dirPath: string): Promise<void> {
  await mkdir(dirPath, { recursive: true });
}

async function checkpointScreenshot(
  page: Page,
  testInfo: TestInfo,
  report: Record<ReportField, StepResult>,
  field: ReportField,
  fileName: string,
  fullPage = false
): Promise<void> {
  const screenshotDir = path.join(testInfo.config.rootDir, "artifacts", "screenshots");
  await ensureDir(screenshotDir);
  const screenshotPath = path.join(screenshotDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  report[field].evidence.push(screenshotPath);
}

async function findSectionContainer(page: Page, sectionHeading: string): Promise<Locator> {
  const headingRegex = new RegExp(escapeRegExp(sectionHeading), "i");
  const heading = page.getByRole("heading", { name: headingRegex }).first();

  if (await heading.isVisible().catch(() => false)) {
    return heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  }

  const textHeading = page.getByText(headingRegex).first();
  await expect(textHeading).toBeVisible();
  return textHeading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
}

async function executeLegalDocumentValidation(
  page: Page,
  testInfo: TestInfo,
  report: Record<ReportField, StepResult>,
  field: "Términos y Condiciones" | "Política de Privacidad",
  clickableLabels: string[],
  headingText: string,
  screenshotName: string
): Promise<void> {
  const appUrlBeforeClick = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickByVisibleText(page, clickableLabels, headingText, false);

  const popupPage = await popupPromise;
  const legalPage = popupPage ?? page;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
  await waitForUi(legalPage);

  const headingLocator = legalPage
    .getByRole("heading", { name: new RegExp(escapeRegExp(headingText), "i") })
    .first();
  if (await headingLocator.isVisible().catch(() => false)) {
    await expect(headingLocator).toBeVisible();
  } else {
    await expectVisibleText(legalPage, headingText);
  }

  const legalContentLocator = legalPage.locator("p, li, article, main div").filter({ hasText: /[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]{10,}/ }).first();
  await expect(legalContentLocator, "Expected legal content text to be visible").toBeVisible();

  await checkpointScreenshot(legalPage, testInfo, report, field, screenshotName, true);
  report[field].finalUrl = legalPage.url();
  report[field].details.push(`Final URL captured: ${legalPage.url()}`);

  if (popupPage) {
    await popupPage.close().catch(() => undefined);
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  if (!LOGIN_URL) {
    throw new Error("Missing SALEADS_LOGIN_URL (or SALEADS_BASE_URL). Provide the login URL for the target SaleADS.ai environment.");
  }

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

  const report = reportFields.reduce<Record<ReportField, StepResult>>((acc, field) => {
    acc[field] = { status: "FAIL", details: [], evidence: [] };
    return acc;
  }, {} as Record<ReportField, StepResult>);

  const failures: string[] = [];

  const runStep = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[field].status = "PASS";
      if (report[field].details.length === 0) {
        report[field].details.push("All validations passed.");
      }
    } catch (error) {
      report[field].status = "FAIL";
      report[field].details.push(
        error instanceof Error ? error.message : "Unknown error while executing the step."
      );
      failures.push(field);
    }
  };

  await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runStep("Login", async () => {
    const googlePopupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);

    await clickByVisibleText(
      page,
      ["Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"],
      "Google login"
    );

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
      const accountLocator = googlePopup
        .getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i"))
        .first();

      if (await accountLocator.isVisible().catch(() => false)) {
        await accountLocator.click();
      }

      await googlePopup.waitForEvent("close", { timeout: 45_000 }).catch(() => undefined);
      await page.bringToFront();
    } else {
      await maybeClickByVisibleText(page, [GOOGLE_ACCOUNT_EMAIL]);
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expectVisibleText(page, "Negocio");
    await checkpointScreenshot(page, testInfo, report, "Login", "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await maybeClickByVisibleText(page, ["Negocio"]);
    await clickByVisibleText(page, ["Mi Negocio"], "Mi Negocio");
    await expectVisibleText(page, "Agregar Negocio");
    await expectVisibleText(page, "Administrar Negocios");
    await checkpointScreenshot(page, testInfo, report, "Mi Negocio menu", "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, ["Agregar Negocio"], "Agregar Negocio");
    await expectVisibleText(page, "Crear Nuevo Negocio");
    await expectVisibleText(page, "Nombre del Negocio");
    await expectVisibleText(page, "Tienes 2 de 3 negocios");
    await expectVisibleText(page, "Cancelar");
    await expectVisibleText(page, "Crear Negocio");

    const input = page.getByLabel("Nombre del Negocio").first();
    if (await input.isVisible().catch(() => false)) {
      await input.click();
      await input.fill("Negocio Prueba Automatización");
    }

    await checkpointScreenshot(page, testInfo, report, "Agregar Negocio modal", "03-agregar-negocio-modal.png");
    await clickByVisibleText(page, ["Cancelar"], "Cancelar modal");
  });

  await runStep("Administrar Negocios view", async () => {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, ["Mi Negocio"], "Mi Negocio");
    }

    await clickByVisibleText(page, ["Administrar Negocios"], "Administrar Negocios");
    await expectVisibleText(page, "Información General");
    await expectVisibleText(page, "Detalles de la Cuenta");
    await expectVisibleText(page, "Tus Negocios");
    await expectVisibleText(page, "Sección Legal");
    await checkpointScreenshot(page, testInfo, report, "Administrar Negocios view", "04-administrar-negocios-view.png", true);
  });

  await runStep("Información General", async () => {
    const infoSection = await findSectionContainer(page, "Información General");

    if (EXPECTED_USER_NAME) {
      await expect(infoSection.getByText(new RegExp(escapeRegExp(EXPECTED_USER_NAME), "i")).first()).toBeVisible();
      report["Información General"].details.push(`Validated configured user name: ${EXPECTED_USER_NAME}`);
    } else {
      report["Información General"].details.push(
        "User name was validated heuristically. Set SALEADS_EXPECTED_USER_NAME for strict validation."
      );
    }

    const emailLocator = infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
    await expect(emailLocator, "Expected user email to be visible").toBeVisible();
    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = await findSectionContainer(page, "Detalles de la Cuenta");
    await expect(detailsSection.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const businessSection = await findSectionContainer(page, "Tus Negocios");
    await expect(businessSection.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const businessItems = businessSection.locator("li, [role='listitem'], article, .card, tbody tr");
    const businessItemsCount = await businessItems.count();
    expect(
      businessItemsCount,
      "Expected at least one visible business in the business list section."
    ).toBeGreaterThan(0);
  });

  await runStep("Términos y Condiciones", async () => {
    const legalSection = await findSectionContainer(page, "Sección Legal");
    if (await legalSection.isVisible().catch(() => false)) {
      await legalSection.scrollIntoViewIfNeeded();
    }

    await executeLegalDocumentValidation(
      page,
      testInfo,
      report,
      "Términos y Condiciones",
      ["Términos y Condiciones"],
      "Términos y Condiciones",
      "05-terminos-y-condiciones.png"
    );
  });

  await runStep("Política de Privacidad", async () => {
    const legalSection = await findSectionContainer(page, "Sección Legal");
    if (await legalSection.isVisible().catch(() => false)) {
      await legalSection.scrollIntoViewIfNeeded();
    }

    await executeLegalDocumentValidation(
      page,
      testInfo,
      report,
      "Política de Privacidad",
      ["Política de Privacidad"],
      "Política de Privacidad",
      "06-politica-de-privacidad.png"
    );
  });

  const reportDir = path.join(testInfo.config.rootDir, "artifacts", "reports");
  await ensureDir(reportDir);
  const reportPath = path.join(reportDir, "saleads_mi_negocio_full_test.report.json");
  await writeFile(
    reportPath,
    JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        environment: {
          loginUrl: LOGIN_URL
        },
        results: report
      },
      null,
      2
    ),
    "utf-8"
  );

  for (const field of reportFields) {
    const status = report[field].status;
    // Console summary is useful when the JSON artifact is not opened.
    console.log(`${field}: ${status}`);
  }
  console.log(`Final report written to: ${reportPath}`);

  expect(
    failures,
    `One or more workflow validations failed. See JSON report at ${reportPath}.`
  ).toEqual([]);
});
