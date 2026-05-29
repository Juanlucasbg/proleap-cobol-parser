import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type WorkflowReport = Record<
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad",
  StepStatus
>;

const EMAIL_TO_SELECT = "juanlucasbarbiergarzon@gmail.com";
const TERMS_REGEX = /T[eé]rminos y Condiciones/i;
const PRIVACY_REGEX = /Pol[ií]tica de Privacidad/i;

function initReport(): WorkflowReport {
  return {
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
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(400);
}

async function checkpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<void> {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator.first()).toBeVisible({ timeout: 20_000 });
  await locator.first().click();
  await waitForUi(page);
}

async function isVisible(locator: Locator, timeout = 3_000): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function validateLegalLink(
  page: Page,
  testInfo: TestInfo,
  linkName: string,
  linkPattern: RegExp,
  screenshotName: string
): Promise<string> {
  const context = page.context();
  const legalLink = page.getByRole("link", { name: linkPattern }).or(page.getByText(linkPattern)).first();

  await expect(legalLink).toBeVisible({ timeout: 20_000 });

  const newPagePromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await legalLink.click();
  await waitForUi(page);

  const maybeNewPage = await newPagePromise;
  const targetPage = maybeNewPage ?? page;

  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);

  const heading = targetPage.getByRole("heading", { name: linkPattern }).or(targetPage.getByText(linkPattern)).first();
  await expect(heading).toBeVisible({ timeout: 20_000 });

  const legalContent = targetPage.locator("main p, article p, section p, p, li").first();
  await expect(legalContent).toBeVisible({ timeout: 20_000 });

  await checkpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (maybeNewPage) {
    await maybeNewPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    // Keep the flow in the app view if link navigates same tab.
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  await testInfo.attach(`${linkName} URL`, {
    body: Buffer.from(finalUrl, "utf-8"),
    contentType: "text/plain"
  });

  return finalUrl;
}

test("SaleADS Mi Negocio full workflow", async ({ page }, testInfo) => {
  const report = initReport();
  const notes: string[] = [];
  const legalUrls: Record<string, string> = {};

  const skipNavigation = process.env.SALEADS_SKIP_NAVIGATION === "true";
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;

  if (!skipNavigation) {
    if (!loginUrl) {
      throw new Error(
        "Missing target URL. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL), or use SALEADS_SKIP_NAVIGATION=true to start from an already-open login page."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  const runStep = async (step: keyof WorkflowReport, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      report[step] = "PASS";
    } catch (error) {
      report[step] = "FAIL";
      notes.push(`${step}: ${(error as Error).message}`);
    }
  };

  await runStep("Login", async () => {
    const googleButton = page
      .getByRole("button", { name: /google|iniciar sesi[oó]n con google|sign in with google/i })
      .or(page.getByRole("link", { name: /google|iniciar sesi[oó]n con google|sign in with google/i }))
      .or(page.getByText(/google/i))
      .first();

    await expect(googleButton).toBeVisible({ timeout: 25_000 });

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await googleButton.click();

    const popup = await popupPromise;
    const activePage = popup ?? page;
    await activePage.waitForLoadState("domcontentloaded");
    await activePage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);

    const accountOption = activePage.getByText(EMAIL_TO_SELECT).first();
    if (await isVisible(accountOption, 5_000)) {
      await accountOption.click();
      await activePage.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 40_000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await waitForUi(page);

    const sidebar = page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio/i }).first();
    await expect(sidebar).toBeVisible({ timeout: 30_000 });
    await checkpoint(page, testInfo, "01-dashboard-loaded", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = page.getByText(/^Negocio$/i).or(page.getByRole("button", { name: /^Negocio$/i })).first();
    if (await isVisible(negocioSection, 5_000)) {
      await clickAndWait(negocioSection, page);
    }

    const miNegocio = page.getByText(/^Mi Negocio$/i).or(page.getByRole("button", { name: /^Mi Negocio$/i })).first();
    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible({ timeout: 15_000 });

    await checkpoint(page, testInfo, "02-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickAndWait(page.getByText(/^Agregar Negocio$/i).first(), page);

    const modal = page.getByRole("dialog").or(page.locator("[role='dialog'], .modal").filter({ hasText: /Crear Nuevo Negocio/i })).first();
    await expect(modal).toBeVisible({ timeout: 15_000 });
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 15_000 });
    await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible({ timeout: 15_000 });
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 15_000 });
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible({ timeout: 15_000 });
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: 15_000 });

    const nameInput = modal
      .getByLabel(/Nombre del Negocio/i)
      .or(modal.getByPlaceholder(/Nombre del Negocio/i))
      .or(modal.locator("input[type='text']").first())
      .first();

    if (await isVisible(nameInput, 3_000)) {
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
    }

    await checkpoint(page, testInfo, "03-agregar-negocio-modal");
    await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }).first(), page);
  });

  await runStep("Administrar Negocios view", async () => {
    const adminOption = page.getByText(/^Administrar Negocios$/i).first();
    if (!(await isVisible(adminOption, 5_000))) {
      await clickAndWait(page.getByText(/^Mi Negocio$/i).first(), page);
    }

    await clickAndWait(page.getByText(/^Administrar Negocios$/i).first(), page);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible({ timeout: 20_000 });

    await checkpoint(page, testInfo, "04-administrar-negocios-full", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 15_000 });

    // Generic checks to avoid environment-specific hardcoded values.
    const emailText = page.getByText(/@/).first();
    await expect(emailText).toBeVisible({ timeout: 15_000 });

    const userNameCandidate = page
      .locator("h1, h2, h3, p, span, strong")
      .filter({ hasText: /[A-Za-zÁÉÍÓÚáéíóúñ]{3,}\s+[A-Za-zÁÉÍÓÚáéíóúñ]{3,}/ })
      .first();
    await expect(userNameCandidate).toBeVisible({ timeout: 15_000 });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Estado activo/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 15_000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 15_000 });
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await validateLegalLink(
      page,
      testInfo,
      "Términos y Condiciones",
      TERMS_REGEX,
      "05-terminos-y-condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await validateLegalLink(
      page,
      testInfo,
      "Política de Privacidad",
      PRIVACY_REGEX,
      "06-politica-de-privacidad"
    );
  });

  const finalReport = {
    statusByStep: report,
    legalUrls,
    errors: notes
  };

  const reportJson = JSON.stringify(finalReport, null, 2);
  await testInfo.attach("final-report.json", {
    body: Buffer.from(reportJson, "utf-8"),
    contentType: "application/json"
  });

  // Export for CI parsers that scan stdout.
  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(reportJson);

  expect(
    Object.values(report).every((status) => status === "PASS"),
    `One or more workflow sections failed.\n${reportJson}`
  ).toBeTruthy();
});
