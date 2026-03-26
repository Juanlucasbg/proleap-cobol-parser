import { expect, Locator, Page, test } from "@playwright/test";
import fs from "fs";
import path from "path";

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

const GOOGLE_EMAIL =
  process.env.SALEADS_GOOGLE_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const TEST_BUSINESS_NAME =
  process.env.SALEADS_TEST_BUSINESS_NAME ?? "Negocio Prueba Automatización";
const TEST_NAME = "saleads_mi_negocio_full_test";
const ARTIFACTS_DIR = path.resolve(__dirname, "..", "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORTS_DIR = path.join(ARTIFACTS_DIR, "reports");

function sanitizeFileName(input: string): string {
  return input
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9_-]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {
    // Some SPA transitions do not settle on networkidle.
  });
}

async function findVisible(
  candidates: Locator[],
  timeoutMs = 20_000,
): Promise<Locator | null> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
    await sleep(250);
  }
  return null;
}

function buildReport(): Record<ReportField, StepResult> {
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

  return fields.reduce(
    (acc, field) => ({
      ...acc,
      [field]: {
        status: "FAIL",
        details: [],
        evidence: [],
      },
    }),
    {} as Record<ReportField, StepResult>,
  );
}

function errorMessage(err: unknown): string {
  if (err instanceof Error) {
    return err.message;
  }
  return String(err);
}

function setPass(report: Record<ReportField, StepResult>, field: ReportField): void {
  report[field].status = "PASS";
}

function setFail(
  report: Record<ReportField, StepResult>,
  field: ReportField,
  detail: string,
): void {
  report[field].status = "FAIL";
  report[field].details.push(detail);
}

function addEvidence(
  report: Record<ReportField, StepResult>,
  field: ReportField,
  evidencePath: string,
): void {
  report[field].evidence.push(evidencePath);
}

async function saveScreenshot(page: Page, label: string, fullPage = false): Promise<string> {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const fileName = `${timestamp}_${sanitizeFileName(label)}.png`;
  const absolutePath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: absolutePath, fullPage });
  return absolutePath;
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

function containsLikelyUserName(text: string): boolean {
  const blocked = [
    /informaci[oó]n general/i,
    /business plan/i,
    /cambiar plan/i,
    /plan/i,
    /correo/i,
    /email/i,
  ];

  const lines = text
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean);

  return lines.some(
    (line) =>
      !blocked.some((regex) => regex.test(line)) &&
      !line.includes("@") &&
      /[a-zA-ZÀ-ÿ]/.test(line) &&
      line.length >= 4,
  );
}

async function validateLegalPage(
  page: Page,
  linkName: RegExp,
  headingName: RegExp,
  screenshotLabel: string,
): Promise<{ finalUrl: string; screenshotPath: string }> {
  const context = page.context();
  const appUrlBeforeNavigation = page.url();
  const link = await findVisible(
    [
      page.getByRole("link", { name: linkName }).first(),
      page.getByRole("button", { name: linkName }).first(),
      page.locator("a, button", { hasText: linkName }).first(),
      page.getByText(linkName).first(),
    ],
    20_000,
  );

  if (!link) {
    throw new Error(`No se encontró el link legal: ${linkName}`);
  }

  const pageEventPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await link.click();

  const newPage = await pageEventPromise;
  const legalPage = newPage ?? page;
  await legalPage.bringToFront();
  await waitForUi(legalPage);

  const heading = await findVisible(
    [
      legalPage.getByRole("heading", { name: headingName }).first(),
      legalPage.getByText(headingName).first(),
    ],
    20_000,
  );
  if (!heading) {
    throw new Error(`No se encontró encabezado legal: ${headingName}`);
  }

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  if (bodyText.length < 200) {
    throw new Error("No se detectó contenido legal suficiente en la página.");
  }

  const screenshotPath = await saveScreenshot(legalPage, screenshotLabel, true);
  const finalUrl = legalPage.url();

  if (newPage) {
    await newPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrlBeforeNavigation, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(page);
  }

  return { finalUrl, screenshotPath };
}

test(TEST_NAME, async ({ page }) => {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
  fs.mkdirSync(REPORTS_DIR, { recursive: true });

  const report = buildReport();

  const baseUrl = process.env.SALEADS_BASE_URL;
  if (page.url() === "about:blank") {
    if (!baseUrl) {
      throw new Error(
        "Define SALEADS_BASE_URL o inicia la prueba con el navegador ya ubicado en la pantalla de login.",
      );
    }
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);

  // Step 1 - Login with Google
  try {
    const loginButton = await findVisible(
      [
        page.getByRole("button", {
          name: /google|sign in|iniciar sesi[oó]n|continuar/i,
        }).first(),
        page.getByRole("link", {
          name: /google|sign in|iniciar sesi[oó]n|continuar/i,
        }).first(),
        page.getByText(/google|sign in|iniciar sesi[oó]n|continuar/i).first(),
      ],
      25_000,
    );

    if (!loginButton) {
      throw new Error("No se encontró el botón de Login o Sign in with Google.");
    }

    const pageEventPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await loginButton.click();

    const maybeGooglePage = await pageEventPromise;
    const authPage = maybeGooglePage ?? page;
    await authPage.bringToFront();
    await waitForUi(authPage);

    const accountOption = await findVisible(
      [
        authPage.getByText(GOOGLE_EMAIL, { exact: false }).first(),
        authPage.getByRole("button", { name: GOOGLE_EMAIL }).first(),
        authPage.getByRole("link", { name: GOOGLE_EMAIL }).first(),
      ],
      15_000,
    );

    if (accountOption) {
      await accountOption.click();
      await waitForUi(authPage);
    }

    if (maybeGooglePage) {
      await maybeGooglePage.waitForEvent("close", { timeout: 20_000 }).catch(() => {
        // Some flows keep popup open until manually closed.
      });
      await page.bringToFront();
    }

    await waitForUi(page);

    const sidebar = await findVisible(
      [
        page.locator("aside").first(),
        page.getByRole("navigation").first(),
        page.locator("[class*='sidebar']").first(),
      ],
      25_000,
    );
    if (!sidebar) {
      throw new Error("No se detectó la barra lateral después del login.");
    }

    const dashboardScreenshot = await saveScreenshot(page, "dashboard_loaded");
    addEvidence(report, "Login", dashboardScreenshot);
    setPass(report, "Login");
  } catch (err) {
    setFail(report, "Login", errorMessage(err));
  }

  // Step 2 - Open Mi Negocio menu
  try {
    const miNegocio = await findVisible(
      [
        page.getByRole("button", { name: /mi negocio/i }).first(),
        page.getByRole("link", { name: /mi negocio/i }).first(),
        page.getByText(/mi negocio/i).first(),
      ],
      20_000,
    );
    if (!miNegocio) {
      throw new Error("No se encontró la opción Mi Negocio.");
    }

    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

    const expandedMenuScreenshot = await saveScreenshot(page, "mi_negocio_expanded_menu");
    addEvidence(report, "Mi Negocio menu", expandedMenuScreenshot);
    setPass(report, "Mi Negocio menu");
  } catch (err) {
    setFail(report, "Mi Negocio menu", errorMessage(err));
  }

  // Step 3 - Validate Agregar Negocio modal
  try {
    const agregarNegocio = await findVisible(
      [
        page.getByRole("button", { name: /^agregar negocio$/i }).first(),
        page.getByRole("link", { name: /^agregar negocio$/i }).first(),
        page.getByText(/^agregar negocio$/i).first(),
      ],
      20_000,
    );
    if (!agregarNegocio) {
      throw new Error("No se encontró la opción Agregar Negocio.");
    }

    await clickAndWait(page, agregarNegocio);

    const modal = page.getByRole("dialog").first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(modal.getByText(/nombre del negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    const modalScreenshot = await saveScreenshot(page, "agregar_negocio_modal");
    addEvidence(report, "Agregar Negocio modal", modalScreenshot);

    const input = modal.locator("input").first();
    await expect(input).toBeVisible();
    await input.click();
    await input.fill(TEST_BUSINESS_NAME);
    await modal.getByRole("button", { name: /cancelar/i }).click();
    await expect(modal).toBeHidden({ timeout: 10_000 });

    setPass(report, "Agregar Negocio modal");
  } catch (err) {
    setFail(report, "Agregar Negocio modal", errorMessage(err));
  }

  // Step 4 - Open Administrar Negocios
  try {
    const adminEntryVisible = await page.getByText(/administrar negocios/i).first().isVisible();
    if (!adminEntryVisible) {
      const miNegocio = await findVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }).first(),
          page.getByRole("link", { name: /mi negocio/i }).first(),
          page.getByText(/mi negocio/i).first(),
        ],
        10_000,
      );
      if (miNegocio) {
        await clickAndWait(page, miNegocio);
      }
    }

    const administrarNegocios = await findVisible(
      [
        page.getByRole("button", { name: /administrar negocios/i }).first(),
        page.getByRole("link", { name: /administrar negocios/i }).first(),
        page.getByText(/administrar negocios/i).first(),
      ],
      20_000,
    );
    if (!administrarNegocios) {
      throw new Error("No se encontró Administrar Negocios.");
    }
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

    const accountScreenshot = await saveScreenshot(page, "administrar_negocios_view", true);
    addEvidence(report, "Administrar Negocios view", accountScreenshot);
    setPass(report, "Administrar Negocios view");
  } catch (err) {
    setFail(report, "Administrar Negocios view", errorMessage(err));
  }

  // Step 5 - Validate Información General
  try {
    const infoHeading = page.getByText(/informaci[oó]n general/i).first();
    await expect(infoHeading).toBeVisible();
    const infoSection = infoHeading.locator(
      "xpath=ancestor::*[self::section or self::article or self::div][1]",
    );
    const infoText = (await infoSection.innerText()).trim();

    expect(infoText).toMatch(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
    expect(containsLikelyUserName(infoText)).toBeTruthy();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();

    setPass(report, "Información General");
  } catch (err) {
    setFail(report, "Información General", errorMessage(err));
  }

  // Step 6 - Validate Detalles de la Cuenta
  try {
    const detailsHeading = page.getByText(/detalles de la cuenta/i).first();
    await expect(detailsHeading).toBeVisible();
    const detailsSection = detailsHeading.locator(
      "xpath=ancestor::*[self::section or self::article or self::div][1]",
    );

    await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/idioma seleccionado/i)).toBeVisible();

    setPass(report, "Detalles de la Cuenta");
  } catch (err) {
    setFail(report, "Detalles de la Cuenta", errorMessage(err));
  }

  // Step 7 - Validate Tus Negocios
  try {
    const businessesHeading = page.getByText(/tus negocios/i).first();
    await expect(businessesHeading).toBeVisible();
    const businessesSection = businessesHeading.locator(
      "xpath=ancestor::*[self::section or self::article or self::div][1]",
    );

    const listCandidates = [
      businessesSection.locator("li"),
      businessesSection.locator("[role='listitem']"),
      businessesSection.locator("table tbody tr"),
      businessesSection.locator("[class*='business']"),
    ];

    const hasList = (
      await Promise.all(
        listCandidates.map(async (candidate) => {
          const count = await candidate.count();
          return count > 0;
        }),
      )
    ).some(Boolean);

    if (!hasList) {
      const sectionText = (await businessesSection.innerText()).trim();
      if (sectionText.length < 40) {
        throw new Error("No se detectó una lista visible en Tus Negocios.");
      }
    }

    await expect(
      businessesSection.getByRole("button", { name: /agregar negocio/i }).first(),
    ).toBeVisible();
    await expect(businessesSection.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();

    setPass(report, "Tus Negocios");
  } catch (err) {
    setFail(report, "Tus Negocios", errorMessage(err));
  }

  // Step 8 - Validate Términos y Condiciones
  try {
    const terms = await validateLegalPage(
      page,
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "terminos_y_condiciones",
    );
    report["Términos y Condiciones"].finalUrl = terms.finalUrl;
    addEvidence(report, "Términos y Condiciones", terms.screenshotPath);
    setPass(report, "Términos y Condiciones");
  } catch (err) {
    setFail(report, "Términos y Condiciones", errorMessage(err));
  }

  // Step 9 - Validate Política de Privacidad
  try {
    const privacy = await validateLegalPage(
      page,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "politica_de_privacidad",
    );
    report["Política de Privacidad"].finalUrl = privacy.finalUrl;
    addEvidence(report, "Política de Privacidad", privacy.screenshotPath);
    setPass(report, "Política de Privacidad");
  } catch (err) {
    setFail(report, "Política de Privacidad", errorMessage(err));
  }

  // Step 10 - Final report
  const finalReport = {
    testName: TEST_NAME,
    generatedAt: new Date().toISOString(),
    results: report,
  };

  const reportPath = path.join(REPORTS_DIR, `${TEST_NAME}-report.json`);
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");

  const failedFields = (Object.keys(report) as ReportField[]).filter(
    (field) => report[field].status === "FAIL",
  );

  console.log(JSON.stringify(finalReport, null, 2));
  expect(failedFields, `Validaciones fallidas: ${failedFields.join(", ")}`).toEqual([]);
});
