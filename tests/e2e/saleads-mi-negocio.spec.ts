import { mkdir } from "node:fs/promises";
import path from "node:path";
import { expect, Locator, Page, test } from "@playwright/test";

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const NEW_BUSINESS_NAME = "Negocio Prueba Automatización";
const SCREENSHOT_DIR = "test-results/saleads-mi-negocio";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details?: string;
  evidence?: string[];
  url?: string;
};

const createDefaultResults = (): Record<ReportField, StepResult> => ({
  Login: { status: "FAIL", details: "Not executed yet." },
  "Mi Negocio menu": { status: "FAIL", details: "Not executed yet." },
  "Agregar Negocio modal": { status: "FAIL", details: "Not executed yet." },
  "Administrar Negocios view": { status: "FAIL", details: "Not executed yet." },
  "Información General": { status: "FAIL", details: "Not executed yet." },
  "Detalles de la Cuenta": { status: "FAIL", details: "Not executed yet." },
  "Tus Negocios": { status: "FAIL", details: "Not executed yet." },
  "Términos y Condiciones": { status: "FAIL", details: "Not executed yet." },
  "Política de Privacidad": { status: "FAIL", details: "Not executed yet." },
});

const errorMessage = (error: unknown): string => {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
};

const escapeRegex = (value: string): string =>
  value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const exactTextRegex = (text: string): RegExp =>
  new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function ensureLocatorVisible(locator: Locator): Promise<boolean> {
  const element = locator.first();
  const count = await element.count();

  if (count === 0) {
    return false;
  }

  return element.isVisible().catch(() => false);
}

async function clickLocator(locator: Locator): Promise<void> {
  await expect(locator.first()).toBeVisible({ timeout: 15_000 });
  await locator.first().click();
}

async function clickByPattern(page: Page, pattern: RegExp): Promise<void> {
  const candidates = [
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByRole("menuitem", { name: pattern }),
    page.getByText(pattern),
  ];

  for (const candidate of candidates) {
    if (await ensureLocatorVisible(candidate)) {
      await clickLocator(candidate);
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`No visible element found for pattern: ${pattern}`);
}

async function clickAnyPattern(page: Page, patterns: RegExp[]): Promise<void> {
  for (const pattern of patterns) {
    try {
      await clickByPattern(page, pattern);
      return;
    } catch {
      // Try next visible option.
    }
  }

  throw new Error(
    `Could not find any clickable element for patterns: ${patterns
      .map((pattern) => pattern.toString())
      .join(", ")}`
  );
}

async function captureScreenshot(
  page: Page,
  fileName: string,
  fullPage = false
): Promise<string> {
  const screenshotPath = path.join(SCREENSHOT_DIR, `${fileName}.png`);
  await mkdir(path.dirname(screenshotPath), { recursive: true });
  await page.screenshot({ path: screenshotPath, fullPage });

  return screenshotPath;
}

async function validateSection(page: Page, sectionTitlePattern: RegExp): Promise<Locator> {
  const heading = page.getByText(sectionTitlePattern).first();
  await expect(heading).toBeVisible({ timeout: 20_000 });

  const section = page
    .locator("section, article, div")
    .filter({ has: page.getByText(sectionTitlePattern) })
    .first();

  await expect(section).toBeVisible({ timeout: 20_000 });
  return section;
}

async function openLegalDocumentAndValidate(options: {
  page: Page;
  linkPattern: RegExp;
  headingPattern: RegExp;
  screenshotName: string;
}): Promise<{ url: string; evidence: string }> {
  const { page, linkPattern, headingPattern, screenshotName } = options;
  const appUrlBeforeClick = page.url();

  const popupPromise = page.context().waitForEvent("page", { timeout: 5_000 }).catch(() => null);
  await clickByPattern(page, linkPattern);

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  await targetPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await waitForUi(targetPage);

  const headingByRole = targetPage.getByRole("heading", { name: headingPattern }).first();
  const headingByText = targetPage.getByText(headingPattern).first();
  if (await headingByRole.isVisible().catch(() => false)) {
    await expect(headingByRole).toBeVisible({ timeout: 20_000 });
  } else {
    await expect(headingByText).toBeVisible({ timeout: 20_000 });
  }

  const legalBody = targetPage.locator("main, article, section, p").filter({ hasText: /.+/ }).first();
  await expect(legalBody).toBeVisible({ timeout: 20_000 });

  const evidence = await captureScreenshot(targetPage, screenshotName, true);
  const url = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return { url, evidence };
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;
  const results = createDefaultResults();
  const legalUrls: Record<string, string> = {};

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to the current environment login page."
    );
  }

  const executeStep = async (field: ReportField, action: () => Promise<StepResult | void>) => {
    try {
      const result = await action();
      results[field] = {
        status: "PASS",
        ...result,
      };
    } catch (error) {
      results[field] = {
        status: "FAIL",
        details: errorMessage(error),
      };
    }
  };

  await executeStep("Login", async () => {
    await clickAnyPattern(page, [
      exactTextRegex("Sign in with Google"),
      /Iniciar sesi.n con Google/i,
      /Inicia sesi.n con Google/i,
      exactTextRegex("Continuar con Google"),
      /google/i,
    ]);

    const accountLocator = page.getByText(ACCOUNT_EMAIL).first();
    if (await accountLocator.isVisible({ timeout: 10_000 }).catch(() => false)) {
      await accountLocator.click();
      await waitForUi(page);
    }

    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 40_000 });
    await expect(page.getByText(/Mi Negocio|Negocio/i).first()).toBeVisible({ timeout: 40_000 });

    const evidence = await captureScreenshot(page, "01-dashboard-loaded");
    return { evidence: [evidence] };
  });

  await executeStep("Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 20_000 });

    await clickByPattern(page, exactTextRegex("Mi Negocio"));
    await expect(page.getByText(exactTextRegex("Agregar Negocio")).first()).toBeVisible({
      timeout: 20_000,
    });
    await expect(page.getByText(exactTextRegex("Administrar Negocios")).first()).toBeVisible({
      timeout: 20_000,
    });

    const evidence = await captureScreenshot(page, "02-mi-negocio-menu-expanded");
    return { evidence: [evidence] };
  });

  await executeStep("Agregar Negocio modal", async () => {
    await clickByPattern(page, exactTextRegex("Agregar Negocio"));

    const modalTitle = page.getByText(exactTextRegex("Crear Nuevo Negocio")).first();
    await expect(modalTitle).toBeVisible({ timeout: 20_000 });

    const businessNameInput =
      page.getByLabel(exactTextRegex("Nombre del Negocio")).first().or(
        page.getByPlaceholder(/Nombre del Negocio/i).first()
      );
    await expect(businessNameInput).toBeVisible({ timeout: 20_000 });

    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
      timeout: 20_000,
    });
    await expect(page.getByRole("button", { name: exactTextRegex("Cancelar") }).first()).toBeVisible({
      timeout: 20_000,
    });
    await expect(
      page.getByRole("button", { name: exactTextRegex("Crear Negocio") }).first()
    ).toBeVisible({
      timeout: 20_000,
    });

    const evidence = await captureScreenshot(page, "03-agregar-negocio-modal");

    await businessNameInput.click();
    await waitForUi(page);
    await businessNameInput.fill(NEW_BUSINESS_NAME);
    await clickByPattern(page, exactTextRegex("Cancelar"));
    await expect(modalTitle).not.toBeVisible({ timeout: 20_000 });
    return { evidence: [evidence] };
  });

  await executeStep("Administrar Negocios view", async () => {
    const administrarNegocios = page.getByText(exactTextRegex("Administrar Negocios")).first();
    if (!(await administrarNegocios.isVisible().catch(() => false))) {
      await clickByPattern(page, exactTextRegex("Mi Negocio"));
    }

    await clickByPattern(page, exactTextRegex("Administrar Negocios"));
    await waitForUi(page);

    await expect(page.getByText(/Informaci.n General/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Secci.n Legal/i).first()).toBeVisible({ timeout: 20_000 });

    const evidence = await captureScreenshot(page, "04-administrar-negocios-view", true);
    return { evidence: [evidence] };
  });

  await executeStep("Información General", async () => {
    const section = await validateSection(page, /Informaci.n General/i);
    await expect(section.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible({
      timeout: 20_000,
    });
    await expect(section.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(section.getByText(/Cambiar Plan/i).first()).toBeVisible({ timeout: 20_000 });

    const sectionText = (await section.innerText()).trim();
    const lines = sectionText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const nonLabelLine = lines.find(
      (line) =>
        !/Informaci.n General|BUSINESS PLAN|Cambiar Plan|@/i.test(line) && /[A-Za-z]{2,}/.test(line)
    );
    expect(nonLabelLine, "A user name should be visible in Información General.").toBeTruthy();
  });

  await executeStep("Detalles de la Cuenta", async () => {
    const section = await validateSection(page, /Detalles de la Cuenta/i);
    await expect(section.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(section.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(section.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await executeStep("Tus Negocios", async () => {
    const section = await validateSection(page, /Tus Negocios/i);
    await expect(section).toBeVisible({ timeout: 20_000 });
    await expect(section.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(section.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
      timeout: 20_000,
    });
  });

  await executeStep("Términos y Condiciones", async () => {
    const { url, evidence } = await openLegalDocumentAndValidate({
      page,
      linkPattern: /T.rminos y Condiciones/i,
      headingPattern: /T.rminos y Condiciones/i,
      screenshotName: "08-terminos-y-condiciones",
    });
    legalUrls.terminosYCondiciones = url;

    return { evidence: [evidence], url };
  });

  await executeStep("Política de Privacidad", async () => {
    const { url, evidence } = await openLegalDocumentAndValidate({
      page,
      linkPattern: /Pol.tica de Privacidad/i,
      headingPattern: /Pol.tica de Privacidad/i,
      screenshotName: "09-politica-de-privacidad",
    });
    legalUrls.politicaDePrivacidad = url;

    return { evidence: [evidence], url };
  });

  const finalReportLines = REPORT_FIELDS.map((field) => {
    const result = results[field];
    const details = result.details ? ` | details: ${result.details}` : "";
    const url = result.url ? ` | url: ${result.url}` : "";
    const evidence = result.evidence?.length ? ` | evidence: ${result.evidence.join(", ")}` : "";
    return `${field}: ${result.status}${details}${url}${evidence}`;
  });

  console.log("========== SALEADS MI NEGOCIO FINAL REPORT ==========");
  for (const line of finalReportLines) {
    console.log(line);
  }
  console.log("Legal URLs:", legalUrls);

  const failedSteps = REPORT_FIELDS.filter((field) => results[field].status === "FAIL");
  expect(
    failedSteps,
    `The following steps failed:\n${failedSteps
      .map((field) => `${field}: ${results[field].details ?? "Unknown error"}`)
      .join("\n")}`
  ).toEqual([]);
});
