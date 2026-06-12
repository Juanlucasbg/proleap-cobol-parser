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

async function getVisibleByText(
  page: Page,
  textRegex: RegExp,
  role: "button" | "link" | null = null
): Promise<Locator> {
  const roleLocator = role ? page.getByRole(role, { name: textRegex }).first() : null;
  if (roleLocator && (await roleLocator.isVisible().catch(() => false))) {
    return roleLocator;
  }

  const textLocator = page.getByText(textRegex).first();
  await expect(textLocator).toBeVisible();
  return textLocator;
}

async function pickNombreDelNegocioInput(page: Page): Promise<Locator> {
  const byLabel = page.getByLabel(/Nombre del Negocio/i).first();
  if (await byLabel.isVisible().catch(() => false)) {
    return byLabel;
  }

  const byPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i).first();
  await expect(byPlaceholder).toBeVisible();
  return byPlaceholder;
}

async function validateLegalPage(
  page: Page,
  testInfo: TestInfo,
  linkLabel: RegExp,
  headingText: RegExp,
  screenshotName: string
): Promise<string> {
  const link = await getVisibleByText(page, linkLabel, "link");
  const appUrlBeforeClick = page.url();
  const popupPromise = page
    .context()
    .waitForEvent("page", { timeout: 7000 })
    .catch(() => null);

  await link.click();

  const popup = await popupPromise;
  const targetPage = popup ?? page;
  await waitForUi(targetPage);

  const headingByRole = targetPage.getByRole("heading", { name: headingText }).first();
  if (await headingByRole.isVisible().catch(() => false)) {
    await expect(headingByRole).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingText).first()).toBeVisible();
  }

  const content = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(content.length).toBeGreaterThan(200);

  await checkpoint(targetPage, testInfo, screenshotName, { fullPage: true });
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== appUrlBeforeClick) {
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
  const legalUrls: { terminosYCondiciones: string | null; politicaDePrivacidad: string | null } = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null
  };

  const runStep = async (field: ReportField, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      errors.push(
        `[${field}] ${error instanceof Error ? error.message : "Unknown validation error"}`
      );
    }
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL or SALEADS_BASE_URL. The test is environment-agnostic and does not hardcode a domain."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runStep("Login", async () => {
    const loginButton = await getVisibleByText(page, /google/i, "button");
    await clickAndWait(loginButton, page);

    const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await waitForUi(page);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(page.getByText(/Negocio/i).first()).toBeVisible();
    await checkpoint(page, testInfo, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    if (await negocioSection.isVisible().catch(() => false)) {
      await clickAndWait(negocioSection, page);
    }

    const miNegocioOption = await getVisibleByText(page, /^Mi Negocio$/i);
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await checkpoint(page, testInfo, "02-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocioOption = await getVisibleByText(page, /^Agregar Negocio$/i);
    await clickAndWait(agregarNegocioOption, page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const nombreInput = await pickNombreDelNegocioInput(page);
    await nombreInput.fill("Negocio Prueba Automatización");

    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();
    await checkpoint(page, testInfo, "03-crear-negocio-modal");

    await clickAndWait(page.getByRole("button", { name: /Cancelar/i }).first(), page);
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeHidden();
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarOption = page.getByText(/^Administrar Negocios$/i).first();
    if (!(await administrarOption.isVisible().catch(() => false))) {
      const miNegocioOption = await getVisibleByText(page, /^Mi Negocio$/i);
      await clickAndWait(miNegocioOption, page);
    }

    await clickAndWait(page.getByText(/^Administrar Negocios$/i).first(), page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
    await checkpoint(page, testInfo, "04-administrar-negocios-account-page", { fullPage: true });
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    // Generic user identity validation without hardcoding account-specific values.
    await expect(page.locator("text=/@/").first()).toBeVisible();
    const userNameCandidate = page
      .locator("section, main, div")
      .filter({ hasText: /BUSINESS PLAN/i })
      .locator("h1,h2,h3,p,span")
      .first();
    await expect(userNameCandidate).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls.terminosYCondiciones = await validateLegalPage(
      page,
      testInfo,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      "08-terminos-y-condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls.politicaDePrivacidad = await validateLegalPage(
      page,
      testInfo,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
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
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

  // Keep the output explicit for cron and CI logs.
  console.log(JSON.stringify(finalReport, null, 2));

  expect(
    Object.values(report).every((result) => result === "PASS"),
    "One or more required validations failed. See final-report attachment and step errors."
  ).toBeTruthy();
});
