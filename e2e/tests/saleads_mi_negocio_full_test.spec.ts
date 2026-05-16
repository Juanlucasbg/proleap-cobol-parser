import fs from "node:fs";
import path from "node:path";
import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

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

type ReportStatus = "PASS" | "FAIL";

type ReportEntry = {
  status: ReportStatus;
  details: string[];
  evidence: string[];
  finalUrl?: string;
};

const REPORT_FIELDS: ReportField[] = [
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

function createRunDirectory(): string {
  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const runDirectory = path.join(process.cwd(), "artifacts", "saleads_mi_negocio_full_test", runId);
  fs.mkdirSync(runDirectory, { recursive: true });
  return runDirectory;
}

function createEmptyReport(): Record<ReportField, ReportEntry> {
  return REPORT_FIELDS.reduce(
    (report, field) => ({
      ...report,
      [field]: { status: "FAIL", details: [], evidence: [] }
    }),
    {} as Record<ReportField, ReportEntry>
  );
}

function markPass(report: Record<ReportField, ReportEntry>, field: ReportField, detail: string): void {
  report[field].status = "PASS";
  report[field].details.push(detail);
}

function markFail(report: Record<ReportField, ReportEntry>, field: ReportField, detail: string): void {
  report[field].status = "FAIL";
  report[field].details.push(detail);
}

function addEvidence(report: Record<ReportField, ReportEntry>, field: ReportField, evidencePath: string): void {
  report[field].evidence.push(evidencePath);
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
}

async function isVisible(locator: Locator, timeout = 5_000): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickFirstVisible(candidates: Locator[], actionName: string): Promise<void> {
  for (const candidate of candidates) {
    if (await isVisible(candidate, 4_000)) {
      await candidate.first().click();
      return;
    }
  }

  throw new Error(`Unable to find clickable element for: ${actionName}`);
}

async function captureCheckpoint(page: Page, runDirectory: string, fileName: string, fullPage = false): Promise<string> {
  const filePath = path.join(runDirectory, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const administrarVisible = await isVisible(page.getByText(/Administrar Negocios/i), 2_000);
  if (administrarVisible) {
    return;
  }

  await clickFirstVisible(
    [
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/^Mi Negocio$/i)
    ],
    "open Mi Negocio"
  );
  await waitForUiToLoad(page);
}

async function trySelectGoogleAccount(targetPage: Page, accountEmail: string): Promise<boolean> {
  const accountOptionCandidates = [
    targetPage.getByText(new RegExp(accountEmail, "i")),
    targetPage.getByRole("button", { name: new RegExp(accountEmail, "i") }),
    targetPage.getByRole("link", { name: new RegExp(accountEmail, "i") })
  ];

  for (const candidate of accountOptionCandidates) {
    if (await isVisible(candidate, 12_000)) {
      await candidate.first().click();
      return true;
    }
  }

  return false;
}

async function validateLegalPage(
  page: Page,
  context: BrowserContext,
  runDirectory: string,
  report: Record<ReportField, ReportEntry>,
  field: ReportField,
  linkText: string,
  expectedHeading: RegExp,
  screenshotName: string
): Promise<void> {
  const appPage = page;
  try {
    const linkLocatorCandidates = [
      appPage.getByRole("link", { name: new RegExp(linkText, "i") }),
      appPage.getByText(new RegExp(linkText, "i"))
    ];

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickFirstVisible(linkLocatorCandidates, `open ${linkText}`);

    const popupPage = await popupPromise;
    const targetPage = popupPage ?? appPage;
    await waitForUiToLoad(targetPage);

    const headingLocator = targetPage.getByRole("heading", { name: expectedHeading });
    const headingFallback = targetPage.getByText(expectedHeading);
    const hasHeading = (await isVisible(headingLocator, 10_000)) || (await isVisible(headingFallback, 10_000));

    const legalParagraph = targetPage.locator("p, li").first();
    const hasLegalContent = await isVisible(legalParagraph, 10_000);

    if (!hasHeading || !hasLegalContent) {
      throw new Error(`Validation failed for ${linkText}. heading=${hasHeading}, content=${hasLegalContent}`);
    }

    const screenshotPath = await captureCheckpoint(targetPage, runDirectory, screenshotName, true);
    addEvidence(report, field, screenshotPath);
    report[field].finalUrl = targetPage.url();
    markPass(report, field, `${linkText} validation completed. URL: ${targetPage.url()}`);

    if (popupPage) {
      await popupPage.close().catch(() => undefined);
      await appPage.bringToFront();
      await waitForUiToLoad(appPage);
    } else {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUiToLoad(appPage);
    }
  } catch (error) {
    markFail(report, field, `${linkText} validation error: ${(error as Error).message}`);
  }
}

function writeFinalReport(runDirectory: string, report: Record<ReportField, ReportEntry>): string {
  const reportPath = path.join(runDirectory, "final-report.json");
  fs.writeFileSync(
    reportPath,
    JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        results: report
      },
      null,
      2
    )
  );
  return reportPath;
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio module workflow", async ({ page, context }, testInfo) => {
    test.setTimeout(15 * 60 * 1_000);

    const runDirectory = createRunDirectory();
    const report = createEmptyReport();
    const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT ?? DEFAULT_GOOGLE_ACCOUNT;
    const loginUrl = process.env.SALEADS_LOGIN_URL;

    if (page.url() === "about:blank") {
      if (!loginUrl) {
        const missingUrlMessage =
          "Set SALEADS_LOGIN_URL with the login page URL of the target SaleADS environment.";
        for (const field of REPORT_FIELDS) {
          markFail(report, field, missingUrlMessage);
        }
        const reportPath = writeFinalReport(runDirectory, report);
        await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });
        throw new Error(missingUrlMessage);
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    }

    try {
      // Step 1: Login with Google
      const loginCandidates = [
        page.getByRole("button", { name: /Sign in with Google|Iniciar sesión con Google|Google/i }),
        page.getByRole("link", { name: /Sign in with Google|Iniciar sesión con Google|Google/i }),
        page.getByText(/Sign in with Google|Iniciar sesión con Google|Google/i)
      ];

      const googlePopupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await clickFirstVisible(loginCandidates, "Sign in with Google");

      const googlePopupPage = await googlePopupPromise;
      if (googlePopupPage) {
        await waitForUiToLoad(googlePopupPage);
        await trySelectGoogleAccount(googlePopupPage, googleAccount);
      } else {
        await trySelectGoogleAccount(page, googleAccount);
      }

      await waitForUiToLoad(page);
      const sidebarVisible =
        (await isVisible(page.locator("aside, nav").first(), 20_000)) || (await isVisible(page.getByText(/Negocio/i), 20_000));
      if (!sidebarVisible) {
        throw new Error("Main app interface or left sidebar not visible after login.");
      }

      const dashboardScreenshot = await captureCheckpoint(page, runDirectory, "01-dashboard-loaded.png", true);
      addEvidence(report, "Login", dashboardScreenshot);
      markPass(report, "Login", "Main application interface and left sidebar are visible.");
    } catch (error) {
      markFail(report, "Login", (error as Error).message);
    }

    try {
      // Step 2: Open Mi Negocio menu
      await clickFirstVisible(
        [
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByRole("link", { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i)
        ],
        "open Negocio section"
      );
      await waitForUiToLoad(page);

      await clickFirstVisible(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ],
        "open Mi Negocio section"
      );
      await waitForUiToLoad(page);

      await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();

      const menuScreenshot = await captureCheckpoint(page, runDirectory, "02-mi-negocio-expanded.png");
      addEvidence(report, "Mi Negocio menu", menuScreenshot);
      markPass(report, "Mi Negocio menu", "Mi Negocio submenu expanded with required options.");
    } catch (error) {
      markFail(report, "Mi Negocio menu", (error as Error).message);
    }

    try {
      // Step 3: Validate Agregar Negocio modal
      await clickFirstVisible(
        [
          page.getByRole("menuitem", { name: /^Agregar Negocio$/i }),
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByRole("link", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i)
        ],
        "open Agregar Negocio modal"
      );
      await waitForUiToLoad(page);

      const modalTitle = page.getByText(/Crear Nuevo Negocio/i);
      const negocioNameInput = page.getByRole("textbox", { name: /Nombre del Negocio/i });
      const limitText = page.getByText(/Tienes 2 de 3 negocios/i);
      const cancelarButton = page.getByRole("button", { name: /^Cancelar$/i });
      const crearNegocioButton = page.getByRole("button", { name: /Crear Negocio/i });

      await expect(modalTitle).toBeVisible();
      await expect(negocioNameInput).toBeVisible();
      await expect(limitText).toBeVisible();
      await expect(cancelarButton).toBeVisible();
      await expect(crearNegocioButton).toBeVisible();

      await negocioNameInput.fill("Negocio Prueba Automatización");
      await waitForUiToLoad(page);
      await cancelarButton.click();
      await waitForUiToLoad(page);

      const modalScreenshot = await captureCheckpoint(page, runDirectory, "03-agregar-negocio-modal.png");
      addEvidence(report, "Agregar Negocio modal", modalScreenshot);
      markPass(report, "Agregar Negocio modal", "Agregar Negocio modal contains all required controls.");
    } catch (error) {
      markFail(report, "Agregar Negocio modal", (error as Error).message);
    }

    try {
      // Step 4: Open Administrar Negocios
      await ensureMiNegocioExpanded(page);

      await clickFirstVisible(
        [
          page.getByRole("menuitem", { name: /^Administrar Negocios$/i }),
          page.getByRole("button", { name: /^Administrar Negocios$/i }),
          page.getByRole("link", { name: /^Administrar Negocios$/i }),
          page.getByText(/^Administrar Negocios$/i)
        ],
        "open Administrar Negocios"
      );
      await waitForUiToLoad(page);

      await expect(page.getByText(/Información General/i)).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Sección Legal/i)).toBeVisible();

      const accountPageScreenshot = await captureCheckpoint(page, runDirectory, "04-administrar-negocios-page.png", true);
      addEvidence(report, "Administrar Negocios view", accountPageScreenshot);
      markPass(report, "Administrar Negocios view", "All account sections are visible.");
    } catch (error) {
      markFail(report, "Administrar Negocios view", (error as Error).message);
    }

    try {
      // Step 5: Validate Información General
      const bodyText = await page.locator("body").innerText();
      const hasEmail = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i.test(bodyText);
      const hasBusinessPlan = /BUSINESS PLAN/i.test(bodyText);
      const hasPlanButton = await isVisible(page.getByRole("button", { name: /Cambiar Plan/i }), 10_000);
      const hasUserName =
        (await isVisible(page.getByText(/Nombre/i), 5_000)) || (await isVisible(page.getByText(/Usuario/i), 5_000));

      if (!hasEmail || !hasBusinessPlan || !hasPlanButton || !hasUserName) {
        throw new Error(
          `Información General validation failed: hasEmail=${hasEmail}, hasBusinessPlan=${hasBusinessPlan}, hasPlanButton=${hasPlanButton}, hasUserName=${hasUserName}`
        );
      }

      markPass(report, "Información General", "Información General has user data and plan controls.");
    } catch (error) {
      markFail(report, "Información General", (error as Error).message);
    }

    try {
      // Step 6: Validate Detalles de la Cuenta
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
      markPass(report, "Detalles de la Cuenta", "Detalles de la Cuenta values are visible.");
    } catch (error) {
      markFail(report, "Detalles de la Cuenta", (error as Error).message);
    }

    try {
      // Step 7: Validate Tus Negocios
      const tusNegociosHeading = page.getByText(/Tus Negocios/i);
      const addBusinessButtonVisible =
        (await isVisible(page.getByRole("button", { name: /^Agregar Negocio$/i }), 10_000)) ||
        (await isVisible(page.getByRole("link", { name: /^Agregar Negocio$/i }), 10_000));
      const hasBusinessLimitText = await isVisible(page.getByText(/Tienes 2 de 3 negocios/i), 10_000);
      const hasBusinessList =
        (await page.locator("li, tr, [role='listitem'], [role='row']").count()) > 0 ||
        (await isVisible(page.getByText(/Negocio/i), 5_000));

      await expect(tusNegociosHeading).toBeVisible();
      if (!addBusinessButtonVisible || !hasBusinessLimitText || !hasBusinessList) {
        throw new Error(
          `Tus Negocios validation failed: addButton=${addBusinessButtonVisible}, limitText=${hasBusinessLimitText}, listVisible=${hasBusinessList}`
        );
      }

      markPass(report, "Tus Negocios", "Tus Negocios section is visible with controls and limits.");
    } catch (error) {
      markFail(report, "Tus Negocios", (error as Error).message);
    }

    // Step 8: Validate Términos y Condiciones
    await validateLegalPage(
      page,
      context,
      runDirectory,
      report,
      "Términos y Condiciones",
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones.png"
    );

    // Step 9: Validate Política de Privacidad
    await validateLegalPage(
      page,
      context,
      runDirectory,
      report,
      "Política de Privacidad",
      "Política de Privacidad",
      /Política de Privacidad/i,
      "06-politica-de-privacidad.png"
    );

    // Step 10: Final report
    const reportPath = writeFinalReport(runDirectory, report);
    await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

    const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
    expect(failedFields, `Workflow validations failed. Check report: ${reportPath}`).toEqual([]);
  });
});
