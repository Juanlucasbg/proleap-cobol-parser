import { expect, test, type Locator, type Page } from "@playwright/test";
import { promises as fs } from "fs";
import * as path from "path";

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

interface StepReport {
  status: StepStatus;
  details: string[];
  screenshot?: string;
  url?: string;
}

const REPORT_FIELDS: ReportField[] = [
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

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const ARTIFACTS_DIR = path.join("artifacts", "checkpoints");
const FINAL_REPORT_PATH = path.join("artifacts", "saleads_mi_negocio_final_report.json");

function initializeReport(): Record<ReportField, StepReport> {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      { status: "FAIL", details: ["Step not executed."] } satisfies StepReport,
    ]),
  ) as Record<ReportField, StepReport>;
}

function sanitizeFileName(value: string): string {
  return value
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function clickAndWait(page: Page, target: Locator): Promise<void> {
  await target.first().scrollIntoViewIfNeeded().catch(() => {});
  await target.first().click();
  await waitForUi(page);
}

async function pickFirstVisible(page: Page, candidates: Locator[], timeoutMs = 12_000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const current = candidate.first();
      if (await current.isVisible().catch(() => false)) {
        return current;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error("Could not find a visible element from the expected text-based selectors.");
}

async function captureCheckpoint(page: Page, label: string, fullPage = false): Promise<string> {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
  const screenshotPath = path.join(ARTIFACTS_DIR, `${sanitizeFileName(label)}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

function markPass(
  report: Record<ReportField, StepReport>,
  field: ReportField,
  details: string[],
  screenshot?: string,
  url?: string,
): void {
  report[field] = {
    status: "PASS",
    details,
    screenshot,
    url,
  };
}

function markFail(report: Record<ReportField, StepReport>, field: ReportField, error: unknown): void {
  const message = error instanceof Error ? error.message : String(error);
  report[field] = {
    status: "FAIL",
    details: [message],
  };
}

async function writeFinalReport(report: Record<ReportField, StepReport>): Promise<void> {
  await fs.mkdir(path.dirname(FINAL_REPORT_PATH), { recursive: true });
  await fs.writeFile(FINAL_REPORT_PATH, JSON.stringify(report, null, 2), "utf-8");
}

async function validateLegalPage(
  appPage: Page,
  report: Record<ReportField, StepReport>,
  field: "Términos y Condiciones" | "Política de Privacidad",
  linkNameRegex: RegExp,
  headingRegex: RegExp,
): Promise<void> {
  const context = appPage.context();
  const newPagePromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);

  const link = await pickFirstVisible(appPage, [
    appPage.getByRole("link", { name: linkNameRegex }),
    appPage.getByText(linkNameRegex),
  ]);

  await clickAndWait(appPage, link);

  const popup = await newPagePromise;
  const legalPage = popup ?? appPage;
  const openedInNewTab = Boolean(popup);

  if (openedInNewTab && popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
    await popup.bringToFront();
  }

  let headingValidated = false;
  try {
    await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({ timeout: 30_000 });
    headingValidated = true;
  } catch {
    await expect(legalPage.getByText(headingRegex).first()).toBeVisible({ timeout: 30_000 });
    headingValidated = true;
  }

  if (!headingValidated) {
    throw new Error(`Could not validate heading ${headingRegex.toString()}.`);
  }

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  expect(bodyText.length).toBeGreaterThan(80);

  const screenshot = await captureCheckpoint(legalPage, field, true);
  const finalUrl = legalPage.url();
  markPass(
    report,
    field,
    ["Legal heading visible.", "Legal content text visible."],
    screenshot,
    finalUrl,
  );

  if (openedInNewTab && popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = initializeReport();
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || testInfo.project.use.baseURL;

  if (!loginUrl || typeof loginUrl !== "string") {
    throw new Error(
      "SALEADS_LOGIN_URL (or BASE_URL) is required. The test does not hardcode domains to stay environment agnostic.",
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  // Step 1: Login with Google
  try {
    const loginButton = await pickFirstVisible(page, [
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
      page.getByRole("button", { name: /continuar con google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/google/i),
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
    await clickAndWait(page, loginButton);

    const popup = await popupPromise;
    const authPage = popup ?? page;

    const accountSelector = await pickFirstVisible(authPage, [
      authPage.getByText(ACCOUNT_EMAIL, { exact: false }),
      authPage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
    ], 8_000).catch(() => null);

    if (accountSelector) {
      await clickAndWait(authPage, accountSelector);
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 90_000 }).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    }

    await expect(
      page.getByText(/negocio|dashboard|panel/i).first(),
      "Main application interface was not detected after login.",
    ).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/negocio/i).first(), "Left sidebar navigation should be visible.").toBeVisible({
      timeout: 30_000,
    });

    const screenshot = await captureCheckpoint(page, "dashboard-loaded");
    markPass(report, "Login", ["Main interface visible.", "Left sidebar navigation visible."], screenshot);
  } catch (error) {
    markFail(report, "Login", error);
  }

  // Step 2: Open Mi Negocio menu
  try {
    const negocioSection = await pickFirstVisible(page, [
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i),
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocio = await pickFirstVisible(page, [
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i),
    ]);
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 20_000 });

    const screenshot = await captureCheckpoint(page, "mi-negocio-menu-expanded");
    markPass(
      report,
      "Mi Negocio menu",
      ["Submenu expanded.", "'Agregar Negocio' visible.", "'Administrar Negocios' visible."],
      screenshot,
    );
  } catch (error) {
    markFail(report, "Mi Negocio menu", error);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const agregarNegocio = await pickFirstVisible(page, [
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i),
    ]);
    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 20_000 });
    const nombreInput = await pickFirstVisible(page, [
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.getByRole("textbox", { name: /nombre del negocio/i }),
    ]);
    await expect(nombreInput).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible({ timeout: 20_000 });

    await nombreInput.fill("Negocio Prueba Automatizacion");
    const screenshot = await captureCheckpoint(page, "agregar-negocio-modal");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }).first());

    markPass(
      report,
      "Agregar Negocio modal",
      [
        "Modal title 'Crear Nuevo Negocio' visible.",
        "Input 'Nombre del Negocio' visible.",
        "Business quota text visible.",
        "Buttons 'Cancelar' and 'Crear Negocio' visible.",
      ],
      screenshot,
    );
  } catch (error) {
    markFail(report, "Agregar Negocio modal", error);
  }

  // Step 4: Open Administrar Negocios
  try {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocio = await pickFirstVisible(page, [
        page.getByRole("button", { name: /^mi negocio$/i }),
        page.getByRole("link", { name: /^mi negocio$/i }),
        page.getByText(/^mi negocio$/i),
      ]);
      await clickAndWait(page, miNegocio);
    }

    const administrarNegocios = await pickFirstVisible(page, [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ]);
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible({ timeout: 30_000 });

    const screenshot = await captureCheckpoint(page, "administrar-negocios-page", true);
    markPass(
      report,
      "Administrar Negocios view",
      [
        "'Información General' section visible.",
        "'Detalles de la Cuenta' section visible.",
        "'Tus Negocios' section visible.",
        "'Sección Legal' section visible.",
      ],
      screenshot,
    );
  } catch (error) {
    markFail(report, "Administrar Negocios view", error);
  }

  // Step 5: Validate Información General
  try {
    await expect(page.getByText(/business plan/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({ timeout: 20_000 });

    const profileDetails = page.locator("section,div").filter({
      hasText: /@|plan|business plan/i,
    });
    await expect(profileDetails.first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/@[a-z0-9.-]+\.[a-z]{2,}/i).first()).toBeVisible({ timeout: 20_000 });

    markPass(report, "Información General", [
      "User name or profile block visible.",
      "User email visible.",
      "'BUSINESS PLAN' visible.",
      "'Cambiar Plan' button visible.",
    ]);
  } catch (error) {
    markFail(report, "Información General", error);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });

    markPass(report, "Detalles de la Cuenta", [
      "'Cuenta creada' visible.",
      "'Estado activo' visible.",
      "'Idioma seleccionado' visible.",
    ]);
  } catch (error) {
    markFail(report, "Detalles de la Cuenta", error);
  }

  // Step 7: Validate Tus Negocios
  try {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^agregar negocio$/i }).first()).toBeVisible({ timeout: 20_000 });

    const businessList = page.locator("section,div").filter({
      hasText: /tus negocios|negocio/i,
    });
    await expect(businessList.first()).toBeVisible({ timeout: 20_000 });

    markPass(report, "Tus Negocios", [
      "Business list block visible.",
      "'Agregar Negocio' button visible.",
      "Business quota text visible.",
    ]);
  } catch (error) {
    markFail(report, "Tus Negocios", error);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    await validateLegalPage(
      page,
      report,
      "Términos y Condiciones",
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
    );
  } catch (error) {
    markFail(report, "Términos y Condiciones", error);
  }

  // Step 9: Validate Política de Privacidad
  try {
    await validateLegalPage(
      page,
      report,
      "Política de Privacidad",
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
    );
  } catch (error) {
    markFail(report, "Política de Privacidad", error);
  }

  // Step 10: Final report
  await writeFinalReport(report);
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: FINAL_REPORT_PATH,
    contentType: "application/json",
  });

  const failedValidations = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failedValidations,
    `Some validations failed. See ${FINAL_REPORT_PATH} for detailed PASS/FAIL statuses.`,
  ).toHaveLength(0);
});
