import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import fs from "node:fs/promises";

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
};

type FinalReport = Record<StepName, StepResult>;

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function createInitialReport(): FinalReport {
  return {
    Login: { status: "FAIL", details: "Not executed." },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed." },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed." },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed." },
    "Información General": { status: "FAIL", details: "Not executed." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed." },
    "Tus Negocios": { status: "FAIL", details: "Not executed." },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed." },
    "Política de Privacidad": { status: "FAIL", details: "Not executed." }
  };
}

async function ensureVisible(locator: Locator, message: string): Promise<void> {
  await expect(locator, message).toBeVisible();
}

async function waitAfterClick(page: Page): Promise<void> {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => null),
    page.waitForLoadState("domcontentloaded", { timeout: 10_000 }).catch(() => null)
  ]);
  await page.waitForTimeout(500);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click({ timeout: 20_000 });
  await waitAfterClick(page);
}

async function captureScreenshot(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<void> {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png"
  });
}

async function writeFinalReport(testInfo: TestInfo, report: FinalReport, urls: Record<string, string>): Promise<void> {
  const finalPayload = {
    generatedAt: new Date().toISOString(),
    report,
    legalUrls: urls
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_final_report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalPayload, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json"
  });
}

async function selectGoogleAccountIfPresent(page: Page): Promise<boolean> {
  const accountByText = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
  if (await accountByText.first().isVisible({ timeout: 5_000 }).catch(() => false)) {
    await accountByText.first().click();
    await waitAfterClick(page);
    return true;
  }

  const accountOption = page.locator(`div[role="link"]:has-text("${GOOGLE_ACCOUNT_EMAIL}")`);
  if (await accountOption.first().isVisible({ timeout: 2_000 }).catch(() => false)) {
    await accountOption.first().click();
    await waitAfterClick(page);
    return true;
  }

  return false;
}

async function openLoginPageIfProvided(page: Page): Promise<void> {
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await waitAfterClick(page);
  }
}

async function runLegalValidation(
  page: Page,
  testInfo: TestInfo,
  linkText: string,
  expectedHeading: RegExp,
  screenshotName: string
): Promise<{ url: string; details: string }> {
  const appUrlBefore = page.url();
  const link = page.getByRole("link", { name: new RegExp(linkText, "i") }).first();
  await ensureVisible(link, `Expected legal link "${linkText}" to be visible.`);

  const popupPromise = page.context().waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await clickAndWait(page, link);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 });
    await ensureVisible(
      popup.getByRole("heading", { name: expectedHeading }).first(),
      `Expected heading ${expectedHeading} in popup.`
    );
    await expect(popup.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúÑñ]{30,}/);
    const popupShot = testInfo.outputPath(`${screenshotName}.png`);
    await popup.screenshot({ path: popupShot, fullPage: true });
    await testInfo.attach(screenshotName, { path: popupShot, contentType: "image/png" });
    const popupUrl = popup.url();
    await popup.close({ runBeforeUnload: true }).catch(() => null);
    await page.bringToFront();
    return { url: popupUrl, details: `Validated in new tab: ${popupUrl}` };
  }

  await ensureVisible(
    page.getByRole("heading", { name: expectedHeading }).first(),
    `Expected heading ${expectedHeading} after legal navigation.`
  );
  await expect(page.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúÑñ]{30,}/);
  await captureScreenshot(page, testInfo, screenshotName, true);
  const sameTabUrl = page.url();

  if (sameTabUrl !== appUrlBefore) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitAfterClick(page);
  }

  return { url: sameTabUrl, details: `Validated in same tab: ${sameTabUrl}` };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createInitialReport();
  const legalUrls: Record<string, string> = {};

  await openLoginPageIfProvided(page);

  const step = async (name: StepName, action: () => Promise<string>): Promise<void> => {
    try {
      const details = await action();
      report[name] = { status: "PASS", details };
    } catch (error) {
      const details = error instanceof Error ? error.message : String(error);
      report[name] = { status: "FAIL", details };
    }
  };

  await step("Login", async () => {
    const currentUrl = page.url();
    if (
      currentUrl === "about:blank" &&
      !process.env.SALEADS_LOGIN_URL &&
      !process.env.SALEADS_BASE_URL
    ) {
      throw new Error(
        "Browser is on about:blank. Provide SALEADS_LOGIN_URL or SALEADS_BASE_URL to start on the login page."
      );
    }

    const loginButton = page
      .getByRole("button", { name: /(google|sign in|iniciar sesión)/i })
      .or(page.getByRole("link", { name: /(google|sign in|iniciar sesión)/i }))
      .first();
    await ensureVisible(loginButton, "Expected a login button or Google sign-in option.");

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => null);
      await selectGoogleAccountIfPresent(popup);
      await popup.waitForTimeout(1000);
      if (!popup.isClosed()) {
        await popup.close({ runBeforeUnload: true }).catch(() => null);
      }
      await page.bringToFront();
    } else {
      await selectGoogleAccountIfPresent(page);
    }

    const sidebar = page.locator("aside, nav, [role='navigation']").first();
    await ensureVisible(sidebar, "Main application sidebar should be visible after login.");
    await ensureVisible(page.getByText(/Negocio|Mi Negocio/i), "Expected main application content after login.");
    await captureScreenshot(page, testInfo, "01-dashboard-loaded", true);
    return "Dashboard and left sidebar are visible.";
  });

  await step("Mi Negocio menu", async () => {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    if (await negocioSection.isVisible().catch(() => false)) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocio = page.getByRole("button", { name: /Mi Negocio/i }).first();
    const miNegocioFallback = page.getByText(/Mi Negocio/i).first();
    if (await miNegocio.isVisible().catch(() => false)) {
      await clickAndWait(page, miNegocio);
    } else {
      await clickAndWait(page, miNegocioFallback);
    }

    await ensureVisible(page.getByText(/Agregar Negocio/i).first(), "Expected 'Agregar Negocio' in submenu.");
    await ensureVisible(
      page.getByText(/Administrar Negocios/i).first(),
      "Expected 'Administrar Negocios' in submenu."
    );
    await captureScreenshot(page, testInfo, "02-mi-negocio-expanded", true);
    return "Mi Negocio submenu expanded with Agregar/Administrar options visible.";
  });

  await step("Agregar Negocio modal", async () => {
    await clickAndWait(page, page.getByText(/Agregar Negocio/i).first());
    const modal = page.getByRole("dialog").first();

    await ensureVisible(modal, "Expected Agregar Negocio modal dialog to appear.");
    await ensureVisible(
      modal.getByText(/Crear Nuevo Negocio/i),
      "Expected modal title 'Crear Nuevo Negocio'."
    );
    await ensureVisible(
      modal.getByLabel(/Nombre del Negocio/i).or(modal.getByPlaceholder(/Nombre del Negocio/i)),
      "Expected 'Nombre del Negocio' input."
    );
    await ensureVisible(
      modal.getByText(/Tienes 2 de 3 negocios/i),
      "Expected usage text 'Tienes 2 de 3 negocios'."
    );
    await ensureVisible(modal.getByRole("button", { name: /Cancelar/i }), "Expected 'Cancelar' button.");
    await ensureVisible(modal.getByRole("button", { name: /Crear Negocio/i }), "Expected 'Crear Negocio' button.");

    const nombreInput = modal
      .getByLabel(/Nombre del Negocio/i)
      .or(modal.getByPlaceholder(/Nombre del Negocio/i))
      .first();
    await nombreInput.click();
    await waitAfterClick(page);
    await nombreInput.fill("Negocio Prueba Automatización");

    await captureScreenshot(page, testInfo, "03-agregar-negocio-modal", true);
    await clickAndWait(page, modal.getByRole("button", { name: /Cancelar/i }));
    return "Agregar Negocio modal validated and closed with Cancelar.";
  });

  await step("Administrar Negocios view", async () => {
    const administrarOption = page.getByText(/Administrar Negocios/i).first();
    if (!(await administrarOption.isVisible().catch(() => false))) {
      const miNegocio = page.getByText(/Mi Negocio/i).first();
      await clickAndWait(page, miNegocio);
    }

    await clickAndWait(page, page.getByText(/Administrar Negocios/i).first());

    await ensureVisible(page.getByText(/Información General/i).first(), "Expected section 'Información General'.");
    await ensureVisible(page.getByText(/Detalles de la Cuenta/i).first(), "Expected section 'Detalles de la Cuenta'.");
    await ensureVisible(page.getByText(/Tus Negocios/i).first(), "Expected section 'Tus Negocios'.");
    await ensureVisible(page.getByText(/Sección Legal/i).first(), "Expected section 'Sección Legal'.");
    await captureScreenshot(page, testInfo, "04-administrar-negocios-page", true);
    return "Administrar Negocios page loaded with all required sections.";
  });

  await step("Información General", async () => {
    const section = page.getByText(/Información General/i).first().locator("..");
    await ensureVisible(section.getByText(/@/).first(), "Expected user email in Información General section.");
    await ensureVisible(section.getByText(/[A-Za-z]{2,}\s+[A-Za-z]{2,}/).first(), "Expected user name text.");
    await ensureVisible(section.getByText(/BUSINESS PLAN/i), "Expected plan text 'BUSINESS PLAN'.");
    await ensureVisible(section.getByRole("button", { name: /Cambiar Plan/i }), "Expected 'Cambiar Plan' button.");
    return "Información General content is visible.";
  });

  await step("Detalles de la Cuenta", async () => {
    const section = page.getByText(/Detalles de la Cuenta/i).first().locator("..");
    await ensureVisible(section.getByText(/Cuenta creada/i), "Expected 'Cuenta creada' field.");
    await ensureVisible(section.getByText(/Estado activo/i), "Expected 'Estado activo' field.");
    await ensureVisible(section.getByText(/Idioma seleccionado/i), "Expected 'Idioma seleccionado' field.");
    return "Detalles de la Cuenta fields are visible.";
  });

  await step("Tus Negocios", async () => {
    const section = page.getByText(/Tus Negocios/i).first().locator("..");
    await ensureVisible(section, "Expected 'Tus Negocios' section.");
    await ensureVisible(section.getByText(/Agregar Negocio/i).first(), "Expected 'Agregar Negocio' action.");
    await ensureVisible(
      section.getByText(/Tienes 2 de 3 negocios/i),
      "Expected text 'Tienes 2 de 3 negocios' in business section."
    );
    return "Tus Negocios section is visible with limits and action button.";
  });

  await step("Términos y Condiciones", async () => {
    const result = await runLegalValidation(
      page,
      testInfo,
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones"
    );
    legalUrls["Términos y Condiciones"] = result.url;
    return result.details;
  });

  await step("Política de Privacidad", async () => {
    const result = await runLegalValidation(
      page,
      testInfo,
      "Política de Privacidad",
      /Política de Privacidad/i,
      "06-politica-de-privacidad"
    );
    legalUrls["Política de Privacidad"] = result.url;
    return result.details;
  });

  await writeFinalReport(testInfo, report, legalUrls);
  console.table(report);
  console.log("Legal URLs:", legalUrls);

  const failedSteps = (Object.entries(report) as Array<[StepName, StepResult]>)
    .filter(([, result]) => result.status === "FAIL")
    .map(([name, result]) => `${name}: ${result.details}`);

  expect(
    failedSteps,
    `One or more workflow validations failed:\n${failedSteps.join("\n")}`
  ).toEqual([]);
});
