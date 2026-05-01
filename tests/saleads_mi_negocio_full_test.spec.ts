import { expect, test, type BrowserContext, type Locator, type Page, type TestInfo } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details: string;
};

type StepKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepReport = Record<StepKey, StepResult>;

const CHECKPOINT_DIR = path.join("test-results", "saleads-mi-negocio");

function ensureCheckpointDir(): void {
  fs.mkdirSync(CHECKPOINT_DIR, { recursive: true });
}

function createInitialReport(): StepReport {
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

function updateReport(report: StepReport, key: StepKey, status: StepStatus, details: string): void {
  report[key] = { status, details };
}

async function attachReport(testInfo: TestInfo, report: StepReport): Promise<void> {
  const printable = JSON.stringify(report, null, 2);
  await testInfo.attach("saleads-mi-negocio-final-report.json", {
    contentType: "application/json",
    body: Buffer.from(printable, "utf-8")
  });
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, fileName: string, fullPage = false): Promise<void> {
  ensureCheckpointDir();
  const filePath = path.join(CHECKPOINT_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(fileName, {
    contentType: "image/png",
    path: filePath
  });
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

function textLocator(page: Page, text: string): Locator {
  return page.getByText(text, { exact: true });
}

async function firstVisible(page: Page, selectors: Locator[], labelForError: string): Promise<Locator> {
  for (const locator of selectors) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }
  throw new Error(`Could not find visible element for: ${labelForError}`);
}

function extractHost(url: string): string | null {
  try {
    const parsed = new URL(url);
    if (!/^https?:/.test(parsed.protocol)) {
      return null;
    }
    return parsed.host;
  } catch {
    return null;
  }
}

async function resolveApplicationPageAfterGoogle(
  context: BrowserContext,
  currentPage: Page,
  appHost: string | null
): Promise<Page> {
  const timeoutMs = 90_000;
  const started = Date.now();

  while (Date.now() - started < timeoutMs) {
    for (const p of context.pages()) {
      const currentUrl = p.url();
      if (!currentUrl) {
        continue;
      }
      try {
        const host = new URL(currentUrl).host;
        const isGoogleHost = /accounts\.google\.com/.test(host);
        const isAppHostMatch = appHost ? host === appHost : !isGoogleHost;
        if (isAppHostMatch && !isGoogleHost) {
          await p.bringToFront();
          await p.waitForLoadState("domcontentloaded");
          return p;
        }
      } catch {
        // ignore unparseable intermediary URLs
      }
    }
    await currentPage.waitForTimeout(500);
  }

  throw new Error("Did not detect return to SaleADS application after Google sign-in.");
}

async function clickGoogleSignIn(page: Page): Promise<void> {
  const candidates = [
    page.getByRole("button", { name: /Sign in with Google/i }),
    page.getByRole("button", { name: /Iniciar sesión con Google/i }),
    page.getByRole("button", { name: /Google/i }),
    page.getByText("Sign in with Google"),
    page.getByText("Iniciar sesión con Google")
  ];
  const loginButton = await firstVisible(page, candidates, "Google login button");
  await clickAndWait(loginButton, page);
}

async function chooseGoogleAccountIfPrompted(page: Page, context: BrowserContext): Promise<void> {
  const accountText = "juanlucasbarbiergarzon@gmail.com";
  for (const candidatePage of context.pages()) {
    const accountOption = candidatePage.getByText(accountText, { exact: false }).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await waitForUi(candidatePage);
      return;
    }
  }

  const accountOption = page.getByText(accountText, { exact: false }).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUi(page);
  }
}

async function expandMiNegocioMenu(page: Page): Promise<void> {
  const negocioEntry = await firstVisible(
    page,
    [
      textLocator(page, "Negocio"),
      page.getByRole("button", { name: /Negocio/i }),
      page.getByRole("link", { name: /Negocio/i })
    ],
    "Negocio sidebar entry"
  );
  await clickAndWait(negocioEntry, page);

  const miNegocio = await firstVisible(
    page,
    [
      textLocator(page, "Mi Negocio"),
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i })
    ],
    "Mi Negocio menu entry"
  );
  await clickAndWait(miNegocio, page);
}

async function openAgregarNegocio(page: Page): Promise<void> {
  const agregarNegocio = await firstVisible(
    page,
    [
      textLocator(page, "Agregar Negocio"),
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i })
    ],
    "Agregar Negocio"
  );
  await clickAndWait(agregarNegocio, page);
}

async function openAdministrarNegocios(page: Page): Promise<void> {
  const administrar = await firstVisible(
    page,
    [
      textLocator(page, "Administrar Negocios"),
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i })
    ],
    "Administrar Negocios"
  );
  await clickAndWait(administrar, page);
}

async function clickLegalLinkAndValidate(
  page: Page,
  context: BrowserContext,
  testInfo: TestInfo,
  linkText: "Términos y Condiciones" | "Política de Privacidad",
  headingPattern: RegExp,
  screenshotFileName: string
): Promise<{ finalUrl: string; openedInNewTab: boolean }> {
  const legalLink = await firstVisible(
    page,
    [
      textLocator(page, linkText),
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByRole("button", { name: new RegExp(linkText, "i") })
    ],
    linkText
  );

  const existingPages = context.pages().length;
  let legalPage = page;
  let openedInNewTab = false;

  const maybePopup = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await legalLink.click();
  await waitForUi(page);

  const popup = await maybePopup;
  if (popup) {
    openedInNewTab = true;
    legalPage = popup;
    await legalPage.waitForLoadState("domcontentloaded");
  } else {
    // If no popup event occurred, check if new tab still appeared.
    if (context.pages().length > existingPages) {
      openedInNewTab = true;
      legalPage = context.pages()[context.pages().length - 1];
      await legalPage.bringToFront();
      await legalPage.waitForLoadState("domcontentloaded");
    } else {
      legalPage = page;
      await legalPage.waitForLoadState("domcontentloaded");
    }
  }

  await expect(legalPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible({ timeout: 20_000 });
  await expect(
    legalPage.locator("main, article, section, body").first().getByText(/\S+/, { exact: false }).first()
  ).toBeVisible();

  ensureCheckpointDir();
  const screenshotPath = path.join(CHECKPOINT_DIR, screenshotFileName);
  await legalPage.screenshot({ path: screenshotPath, fullPage: true });
  await testInfo.attach(screenshotFileName, {
    contentType: "image/png",
    path: screenshotPath
  });

  return { finalUrl: legalPage.url(), openedInNewTab };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login to SaleADS with Google and validate Mi Negocio workflow", async ({ page, context }, testInfo) => {
    const report = createInitialReport();
    let appPage: Page | null = null;
    let currentStep: StepKey = "Login";
    let capturedError: unknown = null;

    try {
      // Step 1: Login with Google
      const loginUrl = process.env.SALEADS_LOGIN_URL;
      const currentPageUrl = page.url();
      const isBlankPage = currentPageUrl === "about:blank" || currentPageUrl === "";

      if (isBlankPage && !loginUrl) {
        throw new Error(
          "Browser started on a blank page. Provide SALEADS_LOGIN_URL or pre-navigate to the SaleADS login page."
        );
      }
      if (loginUrl && isBlankPage) {
        await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      }

      await waitForUi(page);
      const appHost = extractHost(page.url());
      await clickGoogleSignIn(page);
      await chooseGoogleAccountIfPrompted(page, context);
      appPage = await resolveApplicationPageAfterGoogle(context, page, appHost);
      await waitForUi(appPage);

      await expect(appPage.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
      updateReport(report, "Login", "PASS", "Main application interface and left sidebar are visible.");
      await captureCheckpoint(appPage, testInfo, "01-dashboard-after-login.png", true);

      // Step 2: Open Mi Negocio menu
      currentStep = "Mi Negocio menu";
      await expandMiNegocioMenu(appPage);
      await expect(appPage.getByText(/Agregar Negocio/i)).toBeVisible();
      await expect(appPage.getByText(/Administrar Negocios/i)).toBeVisible();
      updateReport(report, "Mi Negocio menu", "PASS", "Mi Negocio submenu expanded with expected options.");
      await captureCheckpoint(appPage, testInfo, "02-mi-negocio-expanded-menu.png", false);

      // Step 3: Validate Agregar Negocio modal
      currentStep = "Agregar Negocio modal";
      await openAgregarNegocio(appPage);
      await expect(appPage.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      const nombreInput = appPage.getByLabel("Nombre del Negocio").first();
      if (await nombreInput.isVisible().catch(() => false)) {
        await expect(nombreInput).toBeVisible();
      } else {
        await expect(appPage.getByPlaceholder(/Nombre del Negocio/i)).toBeVisible();
      }
      await expect(appPage.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(appPage.getByRole("button", { name: "Cancelar" })).toBeVisible();
      await expect(appPage.getByRole("button", { name: "Crear Negocio" })).toBeVisible();
      await captureCheckpoint(appPage, testInfo, "03-agregar-negocio-modal.png", false);

      const visibleNombreInput = await firstVisible(
        appPage,
        [appPage.getByLabel("Nombre del Negocio"), appPage.getByPlaceholder(/Nombre del Negocio/i)],
        "Nombre del Negocio input"
      );
      await visibleNombreInput.fill("Negocio Prueba Automatización");
      await clickAndWait(appPage.getByRole("button", { name: "Cancelar" }), appPage);
      updateReport(report, "Agregar Negocio modal", "PASS", "Modal validated and cancelled after optional input.");

      // Step 4: Open Administrar Negocios
      currentStep = "Administrar Negocios view";
      if (!(await appPage.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
        await expandMiNegocioMenu(appPage);
      }
      await openAdministrarNegocios(appPage);

      await expect(appPage.getByText(/Información General/i)).toBeVisible();
      await expect(appPage.getByText(/Detalles de la Cuenta/i)).toBeVisible();
      await expect(appPage.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(appPage.getByText(/Sección Legal/i)).toBeVisible();
      updateReport(report, "Administrar Negocios view", "PASS", "Account page loaded with all expected sections.");
      await captureCheckpoint(appPage, testInfo, "04-administrar-negocios-account-page.png", true);

      // Step 5: Validate Información General
      currentStep = "Información General";
      const infoSection = appPage.locator("section, div").filter({ hasText: "Información General" }).first();
      await expect(infoSection).toBeVisible();
      await expect(infoSection.getByText(/@/, { exact: false }).first()).toBeVisible();
      await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
      const infoHasLikelyName =
        (await infoSection
          .getByText(/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/)
          .first()
          .isVisible()
          .catch(() => false)) ||
        (await infoSection.getByText(/Nombre/i).first().isVisible().catch(() => false));
      expect(infoHasLikelyName).toBeTruthy();
      updateReport(report, "Información General", "PASS", "Name, email, BUSINESS PLAN and Cambiar Plan validated.");

      // Step 6: Validate Detalles de la Cuenta
      currentStep = "Detalles de la Cuenta";
      const accountDetails = appPage.locator("section, div").filter({ hasText: "Detalles de la Cuenta" }).first();
      await expect(accountDetails).toBeVisible();
      await expect(accountDetails.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(accountDetails.getByText(/Estado activo/i)).toBeVisible();
      await expect(accountDetails.getByText(/Idioma seleccionado/i)).toBeVisible();
      updateReport(report, "Detalles de la Cuenta", "PASS", "Account details labels are visible.");

      // Step 7: Validate Tus Negocios
      currentStep = "Tus Negocios";
      const businessesSection = appPage.locator("section, div").filter({ hasText: "Tus Negocios" }).first();
      await expect(businessesSection).toBeVisible();
      await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      const sectionAddBusiness = await firstVisible(
        appPage,
        [
          businessesSection.getByRole("button", { name: /^Agregar Negocio$/i }),
          businessesSection.getByRole("link", { name: /^Agregar Negocio$/i }),
          businessesSection.getByText("Agregar Negocio")
        ],
        "Tus Negocios > Agregar Negocio"
      );
      await expect(sectionAddBusiness).toBeVisible();
      const listVisible =
        (await businessesSection.locator("li, table tbody tr, [role='row']").first().isVisible().catch(() => false)) ||
        (await businessesSection.getByText(/negocio/i, { exact: false }).first().isVisible().catch(() => false));
      expect(listVisible).toBeTruthy();
      updateReport(report, "Tus Negocios", "PASS", "Business list, action button, and business count text validated.");

      // Step 8: Validate Términos y Condiciones
      currentStep = "Términos y Condiciones";
      const termsOriginUrl = appPage.url();
      const termsResult = await clickLegalLinkAndValidate(
        appPage,
        context,
        testInfo,
        "Términos y Condiciones",
        /Términos y Condiciones/i,
        "05-terminos-y-condiciones.png"
      );
      updateReport(
        report,
        "Términos y Condiciones",
        "PASS",
        `Validated legal page. URL: ${termsResult.finalUrl}. Opened in new tab: ${termsResult.openedInNewTab}.`
      );
      if (termsResult.openedInNewTab) {
        const legalTab = context.pages()[context.pages().length - 1];
        if (legalTab !== appPage) {
          await legalTab.close().catch(() => undefined);
          await appPage.bringToFront();
        }
      } else if (appPage.url() !== termsOriginUrl) {
        await appPage.goBack().catch(() => undefined);
        await waitForUi(appPage);
      }

      // Step 9: Validate Política de Privacidad
      currentStep = "Política de Privacidad";
      const privacyOriginUrl = appPage.url();
      const privacyResult = await clickLegalLinkAndValidate(
        appPage,
        context,
        testInfo,
        "Política de Privacidad",
        /Política de Privacidad/i,
        "06-politica-de-privacidad.png"
      );
      updateReport(
        report,
        "Política de Privacidad",
        "PASS",
        `Validated legal page. URL: ${privacyResult.finalUrl}. Opened in new tab: ${privacyResult.openedInNewTab}.`
      );
      if (privacyResult.openedInNewTab) {
        const legalTab = context.pages()[context.pages().length - 1];
        if (legalTab !== appPage) {
          await legalTab.close().catch(() => undefined);
          await appPage.bringToFront();
        }
      } else if (appPage.url() !== privacyOriginUrl) {
        await appPage.goBack().catch(() => undefined);
        await waitForUi(appPage);
      }
    } catch (error) {
      capturedError = error;
      updateReport(report, currentStep, "FAIL", error instanceof Error ? error.message : String(error));
      const stepOrder: StepKey[] = [
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
      const failedIndex = stepOrder.indexOf(currentStep);
      for (let i = failedIndex + 1; i < stepOrder.length; i += 1) {
        const key = stepOrder[i];
        if (report[key].details === "Not executed.") {
          updateReport(report, key, "FAIL", `Prerequisite failed at "${currentStep}".`);
        }
      }
    } finally {
      await attachReport(testInfo, report);
    }

    const failedSteps = (Object.entries(report) as Array<[StepKey, StepResult]>).filter(([, v]) => v.status === "FAIL");
    expect(capturedError, capturedError instanceof Error ? capturedError.message : "Unexpected workflow error").toBeNull();
    expect(failedSteps, `Some workflow validations failed: ${JSON.stringify(failedSteps)}`).toHaveLength(0);
  });
});
