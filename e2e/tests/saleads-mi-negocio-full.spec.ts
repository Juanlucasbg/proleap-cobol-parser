import { BrowserContext, expect, Locator, Page, test, TestInfo } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type ValidationField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type ValidationReport = Record<
  ValidationField,
  {
    status: "PASS" | "FAIL";
    details: string;
  }
>;

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const LOGIN_URL =
  process.env.SALEADS_LOGIN_URL ||
  process.env.SALEADS_BASE_URL ||
  process.env.BASE_URL;

const REPORT_FIELDS: ValidationField[] = [
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

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context }, testInfo) => {
    if (!LOGIN_URL) {
      throw new Error(
        "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to run the environment-agnostic SaleADS test."
      );
    }

    const report = createInitialReport();
    const legalUrls: Record<string, string> = {};
    let appPage = page;

    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);

    await runValidation(report, "Login", async () => {
      const loginTrigger = await findFirstVisible(page, [
        page.getByRole("button", { name: /google|iniciar sesi[oó]n|sign in/i }).first(),
        page.getByRole("link", { name: /google|iniciar sesi[oó]n|sign in/i }).first(),
        page.getByText(/google|iniciar sesi[oó]n|sign in/i).first(),
      ]);

      if (!loginTrigger) {
        throw new Error("Google login button was not found.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
      await clickAndWait(page, loginTrigger);
      const popup = await popupPromise;

      const authPage = popup ?? page;
      await authPage.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => undefined);
      await waitForUiToSettle(authPage);

      const accountChoice = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
      if (await isVisible(accountChoice, 8_000)) {
        await clickAndWait(authPage, accountChoice);
      }

      appPage = await findApplicationPage(context, page, popup ?? undefined);
      await expectVisibleText(appPage, /Negocio|Mi Negocio/i, 25_000);
      await expectVisibleSidebar(appPage);
      await captureCheckpoint(appPage, testInfo, "01-dashboard-loaded", true);
    });

    await runValidation(report, "Mi Negocio menu", async () => {
      await clickVisibleText(appPage, [/Negocio/i, /Mi Negocio/i], "Negocio or Mi Negocio menu");
      await clickVisibleText(appPage, [/Mi Negocio/i], "Mi Negocio option");

      await expectVisibleText(appPage, /Agregar Negocio/i);
      await expectVisibleText(appPage, /Administrar Negocios/i);
      await captureCheckpoint(appPage, testInfo, "02-mi-negocio-menu-expanded");
    });

    await runValidation(report, "Agregar Negocio modal", async () => {
      await clickVisibleText(appPage, [/Agregar Negocio/i], "Agregar Negocio option");

      await expectVisibleText(appPage, /Crear Nuevo Negocio/i);
      await expectVisibleText(appPage, /Nombre del Negocio/i);
      await expectVisibleText(appPage, /Tienes 2 de 3 negocios/i);
      await expectVisibleText(appPage, /Cancelar/i);
      await expectVisibleText(appPage, /Crear Negocio/i);
      await captureCheckpoint(appPage, testInfo, "03-agregar-negocio-modal");

      const nameInput = await findFirstVisible(appPage, [
        appPage.getByLabel(/Nombre del Negocio/i).first(),
        appPage.getByPlaceholder(/Nombre del Negocio/i).first(),
        appPage.locator("input[name*='negocio' i]").first(),
      ]);

      if (nameInput) {
        await nameInput.click();
        await nameInput.fill("Negocio Prueba Automatización");
      }

      const cancelButton = await findFirstVisible(appPage, [
        appPage.getByRole("button", { name: /^Cancelar$/i }).first(),
        appPage.getByText(/^Cancelar$/i).first(),
      ]);

      if (cancelButton) {
        await clickAndWait(appPage, cancelButton);
      }
    });

    await runValidation(report, "Administrar Negocios view", async () => {
      if (!(await isTextVisible(appPage, /Administrar Negocios/i, 2_000))) {
        await clickVisibleText(appPage, [/Mi Negocio/i, /Negocio/i], "Mi Negocio menu");
      }

      await clickVisibleText(appPage, [/Administrar Negocios/i], "Administrar Negocios option");
      await waitForUiToSettle(appPage);

      await expectVisibleText(appPage, /Informaci[oó]n General/i);
      await expectVisibleText(appPage, /Detalles de la Cuenta/i);
      await expectVisibleText(appPage, /Tus Negocios/i);
      await expectVisibleText(appPage, /Secci[oó]n Legal/i);
      await captureCheckpoint(appPage, testInfo, "04-administrar-negocios", true);
    });

    await runValidation(report, "Información General", async () => {
      await expectVisibleText(appPage, /@/i);
      await expectVisibleText(appPage, /BUSINESS PLAN/i);
      await expectVisibleText(appPage, /Cambiar Plan/i);
    });

    await runValidation(report, "Detalles de la Cuenta", async () => {
      await expectVisibleText(appPage, /Cuenta creada/i);
      await expectVisibleText(appPage, /Estado activo/i);
      await expectVisibleText(appPage, /Idioma seleccionado/i);
    });

    await runValidation(report, "Tus Negocios", async () => {
      await expectVisibleText(appPage, /Tus Negocios/i);
      await expectVisibleText(appPage, /Agregar Negocio/i);
      await expectVisibleText(appPage, /Tienes 2 de 3 negocios/i);
    });

    await runValidation(report, "Términos y Condiciones", async () => {
      legalUrls["Términos y Condiciones"] = await validateLegalDocument({
        appPage,
        context,
        linkPattern: /T[ée]rminos y Condiciones/i,
        headingPattern: /T[ée]rminos y Condiciones/i,
        screenshotName: "05-terminos-y-condiciones",
        testInfo,
      });
    });

    await runValidation(report, "Política de Privacidad", async () => {
      legalUrls["Política de Privacidad"] = await validateLegalDocument({
        appPage,
        context,
        linkPattern: /Pol[ií]tica de Privacidad/i,
        headingPattern: /Pol[ií]tica de Privacidad/i,
        screenshotName: "06-politica-de-privacidad",
        testInfo,
      });
    });

    const finalSummary = {
      name: "saleads_mi_negocio_full_test",
      loginUrl: LOGIN_URL,
      executedAt: new Date().toISOString(),
      validationResults: report,
      legalUrls,
    };

    const reportDir = testInfo.outputPath("e2e-artifacts");
    await mkdir(reportDir, { recursive: true });
    const reportPath = path.join(reportDir, "saleads-mi-negocio-final-report.json");
    await writeFile(reportPath, JSON.stringify(finalSummary, null, 2), "utf8");
    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
    expect(
      failedFields,
      `Some validations failed:\n${JSON.stringify(
        failedFields.map((field) => ({ field, details: report[field].details })),
        null,
        2
      )}`
    ).toEqual([]);
  });
});

async function validateLegalDocument({
  appPage,
  context,
  linkPattern,
  headingPattern,
  screenshotName,
  testInfo,
}: {
  appPage: Page;
  context: BrowserContext;
  linkPattern: RegExp;
  headingPattern: RegExp;
  screenshotName: string;
  testInfo: TestInfo;
}): Promise<string> {
  const legalLink = await findFirstVisible(appPage, [
    appPage.getByRole("link", { name: linkPattern }).first(),
    appPage.getByRole("button", { name: linkPattern }).first(),
    appPage.getByText(linkPattern).first(),
  ]);

  if (!legalLink) {
    throw new Error(`Legal link not found for pattern: ${linkPattern}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
  await clickAndWait(appPage, legalLink);
  const popup = await popupPromise;

  const legalPage = popup ?? appPage;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 45_000 }).catch(() => undefined);
  await waitForUiToSettle(legalPage);

  await expectVisibleText(legalPage, headingPattern, 30_000);

  const legalText = (await legalPage.locator("body").innerText()).trim();
  if (legalText.length < 80) {
    throw new Error("Legal content is unexpectedly short or empty.");
  }

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToSettle(appPage);
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiToSettle(appPage);
  }

  return finalUrl;
}

async function runValidation(report: ValidationReport, field: ValidationField, block: () => Promise<void>) {
  try {
    await block();
    report[field] = { status: "PASS", details: "Validation succeeded." };
  } catch (error) {
    report[field] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error),
    };
  }
}

function createInitialReport(): ValidationReport {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: "Not executed." };
    return acc;
  }, {} as ValidationReport);
}

async function clickVisibleText(page: Page, patterns: RegExp[], description: string) {
  const candidates: Locator[] = [];
  for (const pattern of patterns) {
    candidates.push(page.getByRole("button", { name: pattern }).first());
    candidates.push(page.getByRole("link", { name: pattern }).first());
    candidates.push(page.getByRole("menuitem", { name: pattern }).first());
    candidates.push(page.getByText(pattern).first());
  }

  const target = await findFirstVisible(page, candidates);
  if (!target) {
    throw new Error(`Could not find visible element for ${description}.`);
  }

  await clickAndWait(page, target);
}

async function findApplicationPage(
  context: BrowserContext,
  fallback: Page,
  alternative?: Page
): Promise<Page> {
  const candidates = [alternative, fallback].filter((candidate): candidate is Page => !!candidate);
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const pages = [...new Set([...context.pages(), ...candidates])];
    for (const candidate of pages) {
      if (candidate.isClosed()) {
        continue;
      }
      await candidate.waitForLoadState("domcontentloaded", { timeout: 1_500 }).catch(() => undefined);
      if (await hasSidebar(candidate)) {
        return candidate;
      }
    }
    await fallback.waitForTimeout(750);
  }

  return fallback;
}

async function hasSidebar(page: Page): Promise<boolean> {
  const sidebarLocator = await findFirstVisible(page, [
    page.locator("aside").first(),
    page.locator("nav").first(),
    page.getByText(/Mi Negocio|Negocio/i).first(),
  ]);
  return !!sidebarLocator;
}

async function expectVisibleSidebar(page: Page) {
  const sidebarLocator = await findFirstVisible(page, [
    page.locator("aside").first(),
    page.locator("nav").first(),
    page.getByText(/Mi Negocio|Negocio/i).first(),
  ]);

  if (!sidebarLocator) {
    throw new Error("Left sidebar navigation is not visible.");
  }
}

async function expectVisibleText(page: Page, textPattern: RegExp, timeout = 20_000) {
  const target = await findFirstVisible(
    page,
    [page.getByRole("heading", { name: textPattern }).first(), page.getByText(textPattern).first()],
    timeout
  );

  if (!target) {
    throw new Error(`Expected text not visible: ${textPattern}`);
  }
}

async function isTextVisible(page: Page, textPattern: RegExp, timeout = 2_500) {
  return isVisible(page.getByText(textPattern).first(), timeout);
}

async function findFirstVisible(page: Page, locators: Locator[], timeout = 4_000): Promise<Locator | null> {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await isVisible(locator, 250)) {
        return locator;
      }
    }
    await page.waitForTimeout(150);
  }
  return null;
}

async function isVisible(locator: Locator, timeout = 500) {
  try {
    return await locator.isVisible({ timeout });
  } catch {
    return false;
  }
}

async function clickAndWait(page: Page, locator: Locator) {
  await locator.click();
  await waitForUiToSettle(page);
}

async function waitForUiToSettle(page: Page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => undefined);
  await page.waitForTimeout(350);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
) {
  const imagePath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: imagePath, fullPage });
  await testInfo.attach(name, { path: imagePath, contentType: "image/png" });
}
