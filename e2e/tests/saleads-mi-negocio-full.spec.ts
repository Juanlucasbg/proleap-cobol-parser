import fs from "node:fs/promises";
import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";

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

type StepResult = "PASS" | "FAIL";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const UI_SETTLE_MS = Number(process.env.UI_SETTLE_MS ?? "900");
const TERMS_TEXT_REGEX = /T[eé]rminos y Condiciones/i;
const PRIVACY_TEXT_REGEX = /Pol[ií]tica de Privacidad/i;
const BUSINESS_LIMIT_TEXT_REGEX = /Tienes\s+2\s+de\s+3\s+negocios/i;
const EMAIL_REGEX = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

function cleanErrorMessage(message: string): string {
  return message.replace(/\u001b\[[0-9;]*m/g, "");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(UI_SETTLE_MS);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function checkpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  options: { fullPage?: boolean } = {}
): Promise<void> {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage: options.fullPage ?? false });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  return null;
}

async function getVisibleByText(page: Page, text: RegExp): Promise<Locator> {
  const roleLocator = await firstVisible([
    page.getByRole("button", { name: text }).first(),
    page.getByRole("link", { name: text }).first()
  ]);
  if (roleLocator) {
    return roleLocator;
  }

  const textLocator = page.getByText(text).first();
  await expect(textLocator).toBeVisible();
  return textLocator;
}

async function pickNombreDelNegocioInput(page: Page): Promise<Locator> {
  const byLabel = page.getByLabel(/Nombre del Negocio/i).first();
  if (await byLabel.isVisible().catch(() => false)) {
    return byLabel;
  }

  const byPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i).first();
  if (await byPlaceholder.isVisible().catch(() => false)) {
    return byPlaceholder;
  }

  const byNearbyText = page.locator("input").filter({ has: page.getByText(/Nombre del Negocio/i) }).first();
  await expect(byNearbyText).toBeVisible();
  return byNearbyText;
}

async function expandMiNegocioMenuIfNeeded(page: Page): Promise<void> {
  const administrar = page.getByText(/^Administrar Negocios$/i).first();
  const agregar = page.getByText(/^Agregar Negocio$/i).first();
  if ((await administrar.isVisible().catch(() => false)) && (await agregar.isVisible().catch(() => false))) {
    return;
  }

  const negocioSection = page.getByText(/^Negocio$/i).first();
  if (await negocioSection.isVisible().catch(() => false)) {
    await clickAndWait(negocioSection, page);
  }

  const miNegocio = await getVisibleByText(page, /^Mi Negocio$/i);
  await clickAndWait(miNegocio, page);
}

async function validateLegalPage(
  page: Page,
  testInfo: TestInfo,
  linkLabel: RegExp,
  headingText: RegExp,
  screenshotName: string
): Promise<string> {
  const legalLink = await getVisibleByText(page, linkLabel);
  const appUrlBeforeClick = page.url();

  const popupPromise = page
    .context()
    .waitForEvent("page", { timeout: 10000 })
    .catch(() => null);

  await legalLink.click();
  const popup = await popupPromise;
  const targetPage = popup ?? page;

  await waitForUi(targetPage);

  const heading = await firstVisible([
    targetPage.getByRole("heading", { name: headingText }).first(),
    targetPage.getByText(headingText).first()
  ]);
  if (!heading) {
    throw new Error(`Could not find heading for legal page using pattern: ${headingText}`);
  }
  await expect(heading).toBeVisible();

  const pageText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(pageText.length).toBeGreaterThan(200);

  await checkpoint(targetPage, testInfo, screenshotName, { fullPage: true });
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return finalUrl;
  }

  if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: Record<ReportField, StepResult> = {
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
  const errors: string[] = [];
  const legalUrls: {
    terminosYCondiciones: string | null;
    politicaDePrivacidad: string | null;
  } = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null
  };

  const runStep = async (field: ReportField, stepFn: () => Promise<void>): Promise<void> => {
    try {
      await stepFn();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      const errorMessage =
        error instanceof Error ? cleanErrorMessage(error.message) : "Unknown validation error";
      errors.push(`[${field}] ${errorMessage}`);
    }
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL or SALEADS_BASE_URL. The workflow is environment-agnostic and does not hardcode domains."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);
  await checkpoint(page, testInfo, "00-login-page-loaded");

  await runStep("Login", async () => {
    const googleLoginButton = await getVisibleByText(
      page,
      /(Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Google)/i
    );
    await clickAndWait(googleLoginButton, page);

    const googleAccountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    if (await googleAccountOption.isVisible().catch(() => false)) {
      await googleAccountOption.click();
      await waitForUi(page);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(page.getByText(/Negocio/i).first()).toBeVisible();
    await checkpoint(page, testInfo, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expandMiNegocioMenuIfNeeded(page);
    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await checkpoint(page, testInfo, "02-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await getVisibleByText(page, /^Agregar Negocio$/i);
    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByText(BUSINESS_LIMIT_TEXT_REGEX).first()).toBeVisible();

    const nombreInput = await pickNombreDelNegocioInput(page);
    await expect(nombreInput).toBeVisible();
    await nombreInput.fill("Negocio Prueba Automatización");

    const cancelarButton = page.getByRole("button", { name: /Cancelar/i }).first();
    const crearButton = page.getByRole("button", { name: /Crear Negocio/i }).first();
    await expect(cancelarButton).toBeVisible();
    await expect(crearButton).toBeVisible();
    await checkpoint(page, testInfo, "03-crear-negocio-modal");

    await clickAndWait(cancelarButton, page);
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeHidden();
  });

  await runStep("Administrar Negocios view", async () => {
    await expandMiNegocioMenuIfNeeded(page);
    await clickAndWait(page.getByText(/^Administrar Negocios$/i).first(), page);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
    await checkpoint(page, testInfo, "04-administrar-negocios", { fullPage: true });
  });

  await runStep("Información General", async () => {
    const infoSection = page
      .locator("section,div")
      .filter({ hasText: /Informaci[oó]n General/i })
      .first();
    await expect(infoSection).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const pageText = await page.locator("body").innerText();
    expect(pageText).toMatch(EMAIL_REGEX);

    const infoLines = (await infoSection.innerText())
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line.length > 0);
    const userNameCandidate = infoLines.find(
      (line) =>
        !EMAIL_REGEX.test(line) &&
        !/informaci[oó]n general/i.test(line) &&
        !/business plan/i.test(line) &&
        !/cambiar plan/i.test(line)
    );
    expect(userNameCandidate, "Expected visible user name in Información General section").toBeTruthy();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(BUSINESS_LIMIT_TEXT_REGEX).first()).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls.terminosYCondiciones = await validateLegalPage(
      page,
      testInfo,
      TERMS_TEXT_REGEX,
      TERMS_TEXT_REGEX,
      "08-terminos-y-condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls.politicaDePrivacidad = await validateLegalPage(
      page,
      testInfo,
      PRIVACY_TEXT_REGEX,
      PRIVACY_TEXT_REGEX,
      "09-politica-de-privacidad"
    );
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    results: report,
    legalUrls,
    errors
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log(JSON.stringify(finalReport, null, 2));

  expect(
    Object.values(report).every((result) => result === "PASS"),
    "One or more required validations failed. Review the final report and screenshots."
  ).toBeTruthy();
});
