import { expect, test } from "@playwright/test";
import type { Locator, Page } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

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

type StepResult = {
  status: "PASS" | "FAIL";
  details: string;
  evidence: string[];
  finalUrl?: string;
};

const CHECKPOINT_DIR = path.join("test-results", "saleads-mi-negocio-checkpoints");
const REPORT_FILE = path.join("test-results", "saleads-mi-negocio-report.json");
const OPTIONAL_LOGIN_URL = process.env.SALEADS_LOGIN_URL;
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const emptyResult = (): StepResult => ({
  status: "FAIL",
  details: "Step was not executed.",
  evidence: []
});

const initReport = (): Record<StepName, StepResult> => ({
  Login: emptyResult(),
  "Mi Negocio menu": emptyResult(),
  "Agregar Negocio modal": emptyResult(),
  "Administrar Negocios view": emptyResult(),
  "Información General": emptyResult(),
  "Detalles de la Cuenta": emptyResult(),
  "Tus Negocios": emptyResult(),
  "Términos y Condiciones": emptyResult(),
  "Política de Privacidad": emptyResult()
});

const escapeFileName = (name: string): string =>
  name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForTimeout(1_000);
}

async function isVisible(locator: Locator, timeout = 2_000): Promise<boolean> {
  return locator
    .first()
    .isVisible({ timeout })
    .then(() => true)
    .catch(() => false);
}

async function resolveByVisibleText(page: Page, labelRegex: RegExp): Promise<Locator> {
  const candidates = [
    page.getByRole("button", { name: labelRegex }).first(),
    page.getByRole("link", { name: labelRegex }).first(),
    page.getByRole("menuitem", { name: labelRegex }).first(),
    page.getByRole("tab", { name: labelRegex }).first(),
    page.getByLabel(labelRegex).first(),
    page.getByPlaceholder(labelRegex).first(),
    page.getByText(labelRegex).first()
  ];

  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate;
    }
  }

  throw new Error(`No visible element found for pattern ${labelRegex.toString()}`);
}

async function clickByText(page: Page, patterns: RegExp[], actionDescription: string): Promise<Locator> {
  let lastError: Error | undefined;

  for (const pattern of patterns) {
    try {
      const target = await resolveByVisibleText(page, pattern);
      await target.click();
      await waitForUi(page);
      return target;
    } catch (error) {
      lastError = error as Error;
    }
  }

  throw new Error(`Could not ${actionDescription}. ${lastError?.message ?? "No matching element found."}`);
}

async function expectVisibleText(page: Page, pattern: RegExp, assertionLabel: string): Promise<void> {
  const target = await resolveByVisibleText(page, pattern);
  await expect(target, assertionLabel).toBeVisible();
}

async function takeCheckpoint(page: Page, checkpointName: string, fullPage = false): Promise<string> {
  await fs.mkdir(CHECKPOINT_DIR, { recursive: true });
  const filePath = path.join(CHECKPOINT_DIR, `${escapeFileName(checkpointName)}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function runStep(
  report: Record<StepName, StepResult>,
  step: StepName,
  task: (result: StepResult) => Promise<void>
): Promise<void> {
  const result = report[step];

  try {
    await task(result);
    result.status = "PASS";
    result.details = "All validations passed.";
  } catch (error) {
    result.status = "FAIL";
    result.details = error instanceof Error ? error.message : String(error);
  }
}

async function writeReportAndAssert(report: Record<StepName, StepResult>): Promise<void> {
  const tabular = Object.entries(report).map(([step, result]) => ({
    step,
    status: result.status,
    details: result.details,
    finalUrl: result.finalUrl ?? ""
  }));

  await fs.mkdir(path.dirname(REPORT_FILE), { recursive: true });
  await fs.writeFile(REPORT_FILE, JSON.stringify(report, null, 2), "utf8");
  console.table(tabular);
  console.log(`Detailed report file: ${REPORT_FILE}`);

  const failedSteps = tabular.filter((entry) => entry.status === "FAIL");
  expect(
    failedSteps,
    `Failed validation steps: ${failedSteps.map((entry) => entry.step).join(", ") || "none"}`
  ).toEqual([]);
}

async function clickLegalLinkAndValidate(
  page: Page,
  linkRegex: RegExp,
  titleRegex: RegExp,
  checkpointName: string
): Promise<{ url: string; screenshotPath: string }> {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  const link = await resolveByVisibleText(page, linkRegex);

  await link.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const legalPage = popup ?? page;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
  await waitForUi(legalPage);

  await expectVisibleText(legalPage, titleRegex, "Expected legal heading was not visible.");

  const legalBody = legalPage
    .locator("main, article, section, div")
    .filter({ hasText: /t[eé]rminos|condiciones|pol[ií]tica|privacidad|legal/i })
    .first();

  await expect(legalBody, "Legal content body should be visible.").toBeVisible({ timeout: 15_000 });

  const url = legalPage.url();
  const screenshotPath = await takeCheckpoint(legalPage, checkpointName, true);

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  }

  return { url, screenshotPath };
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report = initReport();

  if (OPTIONAL_LOGIN_URL) {
    await page.goto(OPTIONAL_LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  if (page.url() === "about:blank") {
    const preconditionMessage =
      "SaleADS login page is not loaded. Set SALEADS_LOGIN_URL or start from an already opened SaleADS login page.";

    (Object.keys(report) as StepName[]).forEach((stepName) => {
      report[stepName].status = "FAIL";
      report[stepName].details = preconditionMessage;
    });

    await writeReportAndAssert(report);
    return;
  }

  await runStep(report, "Login", async (result) => {
    await clickByText(
      page,
      [
        /sign in with google/i,
        /iniciar sesi[oó]n con google/i,
        /continuar con google/i,
        /^google$/i
      ],
      "click the Google login button"
    );

    const googleAuthPage = page.context().pages().at(-1) ?? page;
    const accountOption = googleAuthPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")).first();

    if (await isVisible(accountOption, 7_000)) {
      await accountOption.click();
      await waitForUi(googleAuthPage);
    }

    await expect(page.locator("aside, nav").first(), "Sidebar navigation should be visible.").toBeVisible({
      timeout: 45_000
    });

    result.evidence.push(await takeCheckpoint(page, "step-1-dashboard-loaded"));
  });

  await runStep(report, "Mi Negocio menu", async (result) => {
    await clickByText(page, [/^Negocio$/i, /Negocio/i], "open the Negocio section");
    await clickByText(page, [/^Mi Negocio$/i, /Mi Negocio/i], "click Mi Negocio");

    await expectVisibleText(page, /^Agregar Negocio$/i, "Agregar Negocio should be visible.");
    await expectVisibleText(page, /^Administrar Negocios$/i, "Administrar Negocios should be visible.");

    result.evidence.push(await takeCheckpoint(page, "step-2-mi-negocio-expanded"));
  });

  await runStep(report, "Agregar Negocio modal", async (result) => {
    await clickByText(page, [/^Agregar Negocio$/i], "open Agregar Negocio modal");

    await expectVisibleText(page, /Crear Nuevo Negocio/i, "Modal title should be visible.");
    await expectVisibleText(page, /Nombre del Negocio/i, "Nombre del Negocio field should be visible.");
    await expectVisibleText(page, /Tienes 2 de 3 negocios/i, "Business usage text should be visible.");
    await expectVisibleText(page, /^Cancelar$/i, "Cancelar button should be visible.");
    await expectVisibleText(page, /Crear Negocio/i, "Crear Negocio button should be visible.");

    const input = await resolveByVisibleText(page, /Nombre del Negocio/i);
    await input.fill("Negocio Prueba Automatización");
    await waitForUi(page);

    result.evidence.push(await takeCheckpoint(page, "step-3-crear-nuevo-negocio-modal"));
    await clickByText(page, [/^Cancelar$/i], "cancel the create business modal");
  });

  await runStep(report, "Administrar Negocios view", async (result) => {
    const adminVisible = await isVisible(page.getByText(/^Administrar Negocios$/i).first(), 2_000);
    if (!adminVisible) {
      await clickByText(page, [/^Mi Negocio$/i, /Mi Negocio/i], "re-open Mi Negocio");
    }

    await clickByText(page, [/^Administrar Negocios$/i], "open Administrar Negocios");

    await expectVisibleText(page, /Informaci[oó]n General/i, "Información General section should be visible.");
    await expectVisibleText(page, /Detalles de la Cuenta/i, "Detalles de la Cuenta section should be visible.");
    await expectVisibleText(page, /Tus Negocios/i, "Tus Negocios section should be visible.");
    await expectVisibleText(page, /Secci[oó]n Legal/i, "Sección Legal should be visible.");

    result.evidence.push(await takeCheckpoint(page, "step-4-administrar-negocios-view", true));
  });

  await runStep(report, "Información General", async () => {
    await expectVisibleText(page, /Informaci[oó]n General/i, "Información General heading should exist.");

    const infoSection = page
      .locator("section, div")
      .filter({ hasText: /Informaci[oó]n General/i })
      .first();
    await expect(infoSection, "Información General container should be visible.").toBeVisible();

    const textLines = (await infoSection.innerText())
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const userNameLine = textLines.find(
      (line) =>
        /^[\p{L}][\p{L}\s'.-]{2,}$/u.test(line) &&
        !/informaci[oó]n general|business plan|cambiar plan|@/i.test(line)
    );
    if (!userNameLine) {
      throw new Error("User name is not clearly visible in Información General.");
    }

    await expectVisibleText(
      page,
      /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i,
      "User email should be visible in Información General."
    );
    await expectVisibleText(page, /BUSINESS PLAN/i, "BUSINESS PLAN text should be visible.");
    await expectVisibleText(page, /Cambiar Plan/i, "Cambiar Plan button should be visible.");
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await expectVisibleText(page, /Cuenta creada/i, "Cuenta creada should be visible.");
    await expectVisibleText(page, /Estado activo/i, "Estado activo should be visible.");
    await expectVisibleText(page, /Idioma seleccionado/i, "Idioma seleccionado should be visible.");
  });

  await runStep(report, "Tus Negocios", async () => {
    await expectVisibleText(page, /Tus Negocios/i, "Tus Negocios heading should be visible.");
    await expectVisibleText(page, /^Agregar Negocio$/i, "Agregar Negocio button should be visible.");
    await expectVisibleText(page, /Tienes 2 de 3 negocios/i, "Business usage text should be visible.");

    const listContainer = page
      .locator("section, div")
      .filter({ hasText: /Tus Negocios/i })
      .first();
    await expect(listContainer, "The business list container should be visible.").toBeVisible();
  });

  await runStep(report, "Términos y Condiciones", async (result) => {
    const { url, screenshotPath } = await clickLegalLinkAndValidate(
      page,
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "step-8-terminos-y-condiciones"
    );
    result.finalUrl = url;
    result.evidence.push(screenshotPath);
  });

  await runStep(report, "Política de Privacidad", async (result) => {
    const { url, screenshotPath } = await clickLegalLinkAndValidate(
      page,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "step-9-politica-de-privacidad"
    );
    result.finalUrl = url;
    result.evidence.push(screenshotPath);
  });

  await writeReportAndAssert(report);
});
