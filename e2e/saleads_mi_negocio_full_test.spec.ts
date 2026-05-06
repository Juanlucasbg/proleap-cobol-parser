import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const ARTIFACTS_DIR = path.join(process.cwd(), "e2e-artifacts", "saleads_mi_negocio_full_test");

type ReportKey =
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

type StepResult = {
  status: ReportStatus;
  details: string[];
  evidence: string[];
  finalUrl?: string;
};

type StepReport = Record<ReportKey, StepResult>;

const reportKeys: ReportKey[] = [
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

function createInitialReport(): StepReport {
  return reportKeys.reduce((accumulator, key) => {
    accumulator[key] = { status: "FAIL", details: [], evidence: [] };
    return accumulator;
  }, {} as StepReport);
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForTimeout(500);
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 5000 }),
    page.waitForLoadState("networkidle", { timeout: 5000 }),
  ]);
}

async function firstVisibleLocator(locators: Locator[], timeout = 3000): Promise<Locator | null> {
  for (const locator of locators) {
    const candidate = locator.first();
    try {
      await candidate.waitFor({ state: "visible", timeout });
      return candidate;
    } catch {
      // Continue scanning candidates.
    }
  }

  return null;
}

async function captureCheckpoint(page: Page, fileName: string, fullPage = true): Promise<string> {
  await mkdir(ARTIFACTS_DIR, { recursive: true });
  const screenshotPath = path.join(ARTIFACTS_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function captureCheckpointSafe(page: Page, fileName: string, fullPage = true): Promise<string | undefined> {
  try {
    return await captureCheckpoint(page, fileName, fullPage);
  } catch {
    return undefined;
  }
}

function addPass(report: StepReport, key: ReportKey, details: string, evidence: string[] = []): void {
  report[key] = {
    status: "PASS",
    details: [details],
    evidence,
    finalUrl: report[key].finalUrl,
  };
}

function addFailure(report: StepReport, key: ReportKey, details: string, evidence: string[] = []): void {
  report[key] = {
    status: "FAIL",
    details: [details],
    evidence,
    finalUrl: report[key].finalUrl,
  };
}

async function ensureStartingPage(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    await waitForUiToSettle(page);
    return;
  }

  const configuredBaseUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (!configuredBaseUrl) {
    throw new Error(
      "No starting page available. Set SALEADS_BASE_URL (or BASE_URL) to the environment login URL.",
    );
  }

  await page.goto(configuredBaseUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);
}

async function clickAny(page: Page, candidates: Locator[], actionName: string): Promise<void> {
  const target = await firstVisibleLocator(candidates, 5000);
  if (!target) {
    throw new Error(`Could not locate clickable element for: ${actionName}`);
  }

  await target.click();
  await waitForUiToSettle(page);
}

async function maybeSelectGoogleAccount(context: BrowserContext): Promise<void> {
  const popup = await context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
  if (!popup) {
    return;
  }

  await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => undefined);
  const accountChoice = await firstVisibleLocator(
    [
      popup.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
      popup.getByText(ACCOUNT_EMAIL, { exact: false }),
    ],
    7000,
  );

  if (accountChoice) {
    await accountChoice.click();
    await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => undefined);
  }
}

async function verifyTextVisible(page: Page, patterns: RegExp[]): Promise<void> {
  for (const pattern of patterns) {
    await expect(page.getByText(pattern, { exact: false }).first()).toBeVisible({ timeout: 15000 });
  }
}

async function openLegalPageAndValidate(
  page: Page,
  context: BrowserContext,
  linkPattern: RegExp,
  headingPattern: RegExp,
  screenshotName: string,
  appUrl: string,
): Promise<{ screenshotPath: string; finalUrl: string }> {
  const legalLink = await firstVisibleLocator(
    [
      page.getByRole("link", { name: linkPattern }),
      page.getByRole("button", { name: linkPattern }),
      page.getByText(linkPattern, { exact: false }),
    ],
    5000,
  );

  if (!legalLink) {
    throw new Error(`Could not locate legal link with pattern ${linkPattern}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const currentUrl = page.url();
  await legalLink.click();
  await waitForUiToSettle(page);

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => undefined);

  if (!popup && legalPage.url() === currentUrl) {
    await Promise.allSettled([
      legalPage.waitForURL(/.+/, { timeout: 10000 }),
      legalPage.waitForTimeout(1000),
    ]);
  }

  await expect(legalPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible({ timeout: 15000 });
  const legalContent = legalPage.locator("p, li, article, main, section").filter({ hasText: /\S+/ }).first();
  await expect(legalContent).toBeVisible({ timeout: 15000 });

  const screenshotPath = await captureCheckpoint(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close({ runBeforeUnload: true }).catch(() => undefined);
    await page.bringToFront();
  } else if (legalPage.url() !== appUrl && appUrl.length > 0) {
    await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiToSettle(page);
  }

  return { screenshotPath, finalUrl };
}

test.describe("SaleADS Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }) => {
    test.setTimeout(300000);
    const report = createInitialReport();
    await mkdir(ARTIFACTS_DIR, { recursive: true });
    let appUrl = "";

    try {
      // Step 1: Login with Google
      await ensureStartingPage(page);
      const loginCandidates = [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n|login|acceder/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i, { exact: false }),
      ];
      await clickAny(page, loginCandidates, "Login with Google");
      await maybeSelectGoogleAccount(context);

      const sidebarCandidate = await firstVisibleLocator(
        [
          page.locator("aside").filter({ hasText: /negocio|mi negocio|inicio|dashboard/i }),
          page.getByRole("navigation").filter({ hasText: /negocio|mi negocio|inicio|dashboard/i }),
          page.getByText(/mi negocio|negocio/i, { exact: false }),
        ],
        30000,
      );
      if (!sidebarCandidate) {
        throw new Error("Main app interface did not appear after Google login.");
      }

      const dashboardScreenshot = await captureCheckpoint(page, "step-1-dashboard-loaded.png");
      addPass(report, "Login", "Main interface and left sidebar were visible after login.", [dashboardScreenshot]);
      appUrl = page.url();
    } catch (error) {
      const screenshot = await captureCheckpointSafe(page, "step-1-login-failure.png");
      addFailure(report, "Login", `Login validation failed: ${String(error)}`, screenshot ? [screenshot] : []);
    }

    if (report.Login.status !== "PASS") {
      for (const key of reportKeys) {
        if (key === "Login") {
          continue;
        }
        addFailure(
          report,
          key,
          "Skipped because login failed. Resolve step 1 before executing the remaining workflow.",
        );
      }
    } else {
      try {
      // Step 2: Open Mi Negocio menu
      const negocioSection = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /negocio/i }),
          page.getByRole("link", { name: /negocio/i }),
          page.getByText(/^Negocio$/i, { exact: false }),
        ],
        5000,
      );
      if (negocioSection) {
        await negocioSection.click();
        await waitForUiToSettle(page);
      }

      await clickAny(
        page,
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i, { exact: false }),
        ],
        "Open Mi Negocio menu",
      );

      await verifyTextVisible(page, [/Agregar Negocio/i, /Administrar Negocios/i]);
      const menuScreenshot = await captureCheckpoint(page, "step-2-mi-negocio-menu-expanded.png");
      addPass(report, "Mi Negocio menu", "Mi Negocio expanded and required submenu options are visible.", [
        menuScreenshot,
      ]);
    } catch (error) {
      const screenshot = await captureCheckpointSafe(page, "step-2-mi-negocio-menu-failure.png");
      addFailure(report, "Mi Negocio menu", `Mi Negocio menu validation failed: ${String(error)}`, screenshot ? [screenshot] : []);
    }

      try {
      // Step 3: Validate Agregar Negocio modal
      await clickAny(
        page,
        [
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByRole("link", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i, { exact: false }),
        ],
        "Open Agregar Negocio modal",
      );

      await verifyTextVisible(page, [/Crear Nuevo Negocio/i, /Nombre del Negocio/i, /Tienes 2 de 3 negocios/i]);
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 15000 });

      const modalScreenshot = await captureCheckpoint(page, "step-3-agregar-negocio-modal.png");

      const businessNameField = await firstVisibleLocator(
        [
          page.getByRole("textbox", { name: /Nombre del Negocio/i }),
          page.getByLabel(/Nombre del Negocio/i),
          page.getByPlaceholder(/Nombre del Negocio/i),
          page.locator('input[name*="nombre" i], input[id*="nombre" i]').first(),
        ],
        3000,
      );

      if (businessNameField) {
        await businessNameField.click();
        await businessNameField.fill("Negocio Prueba Automatización");
        await waitForUiToSettle(page);
      }

      await clickAny(page, [page.getByRole("button", { name: /Cancelar/i })], "Close modal with Cancelar");
      addPass(report, "Agregar Negocio modal", "Modal content and controls were validated successfully.", [
        modalScreenshot,
      ]);
    } catch (error) {
      const screenshot = await captureCheckpointSafe(page, "step-3-agregar-negocio-modal-failure.png");
      addFailure(report, "Agregar Negocio modal", `Agregar Negocio modal validation failed: ${String(error)}`, [
        ...(screenshot ? [screenshot] : []),
      ]);
    }

      try {
      // Step 4: Open Administrar Negocios
      const adminVisible = await firstVisibleLocator(
        [page.getByRole("button", { name: /Administrar Negocios/i }), page.getByRole("link", { name: /Administrar Negocios/i })],
        1500,
      );
      if (!adminVisible) {
        await clickAny(
          page,
          [
            page.getByRole("button", { name: /mi negocio/i }),
            page.getByRole("link", { name: /mi negocio/i }),
            page.getByText(/mi negocio/i, { exact: false }),
          ],
          "Re-expand Mi Negocio menu",
        );
      }

      await clickAny(
        page,
        [
          page.getByRole("button", { name: /Administrar Negocios/i }),
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i, { exact: false }),
        ],
        "Open Administrar Negocios",
      );

      await verifyTextVisible(page, [
        /Informaci[oó]n General/i,
        /Detalles de la Cuenta/i,
        /Tus Negocios/i,
        /Secci[oó]n Legal/i,
      ]);

      const accountPageScreenshot = await captureCheckpoint(page, "step-4-administrar-negocios-page.png");
      addPass(report, "Administrar Negocios view", "Administrar Negocios loaded with all required sections.", [
        accountPageScreenshot,
      ]);
      appUrl = page.url();
    } catch (error) {
      const screenshot = await captureCheckpointSafe(page, "step-4-administrar-negocios-failure.png");
      addFailure(report, "Administrar Negocios view", `Administrar Negocios view validation failed: ${String(error)}`, [
        ...(screenshot ? [screenshot] : []),
      ]);
    }

      try {
      // Step 5: Validate Información General
      await verifyTextVisible(page, [/BUSINESS PLAN/i, /Cambiar Plan/i]);
      const infoSection = page.getByText(/Informaci[oó]n General/i, { exact: false }).first();
      await expect(infoSection).toBeVisible({ timeout: 15000 });

      const userEmailVisible = await firstVisibleLocator([page.getByText(/@/, { exact: false })], 3000);
      if (!userEmailVisible) {
        throw new Error("User email was not visible in Información General.");
      }

      const userNameVisible = await firstVisibleLocator(
        [
          page.getByText(/Bienvenido|Hola|Usuario|Cuenta/i, { exact: false }),
          page.locator("h1, h2, h3").filter({ hasText: /\S+/ }).first(),
        ],
        5000,
      );
      if (!userNameVisible) {
        throw new Error("User name (or identity text) was not visible.");
      }

      addPass(report, "Información General", "User identity, plan label, and Cambiar Plan button are visible.");
    } catch (error) {
      addFailure(report, "Información General", `Información General validation failed: ${String(error)}`);
    }

      try {
      // Step 6: Validate Detalles de la Cuenta
      await verifyTextVisible(page, [/Cuenta creada/i, /Estado activo/i, /Idioma seleccionado/i]);
      addPass(report, "Detalles de la Cuenta", "Detalles de la Cuenta shows creation, status, and language information.");
    } catch (error) {
      addFailure(report, "Detalles de la Cuenta", `Detalles de la Cuenta validation failed: ${String(error)}`);
    }

      try {
      // Step 7: Validate Tus Negocios
      await verifyTextVisible(page, [/Tus Negocios/i, /Tienes 2 de 3 negocios/i]);
      const addBusinessButton = await firstVisibleLocator(
        [page.getByRole("button", { name: /^Agregar Negocio$/i }), page.getByText(/^Agregar Negocio$/i, { exact: false })],
        5000,
      );
      if (!addBusinessButton) {
        throw new Error("Agregar Negocio button is not visible inside Tus Negocios.");
      }

      addPass(report, "Tus Negocios", "Business list area, Agregar Negocio button, and business quota text are visible.");
    } catch (error) {
      addFailure(report, "Tus Negocios", `Tus Negocios validation failed: ${String(error)}`);
    }

      try {
      // Step 8: Validate Términos y Condiciones
      const terms = await openLegalPageAndValidate(
        page,
        context,
        /T[eé]rminos y Condiciones/i,
        /T[eé]rminos y Condiciones/i,
        "step-8-terminos-y-condiciones.png",
        appUrl,
      );
      report["Términos y Condiciones"].finalUrl = terms.finalUrl;
      addPass(report, "Términos y Condiciones", "Legal page opened and heading/content were validated.", [terms.screenshotPath]);
    } catch (error) {
      const screenshot = await captureCheckpointSafe(page, "step-8-terminos-y-condiciones-failure.png");
      addFailure(
        report,
        "Términos y Condiciones",
        `Términos y Condiciones validation failed: ${String(error)}`,
        screenshot ? [screenshot] : [],
      );
    }

      try {
      // Step 9: Validate Política de Privacidad
      const privacy = await openLegalPageAndValidate(
        page,
        context,
        /Pol[ií]tica de Privacidad/i,
        /Pol[ií]tica de Privacidad/i,
        "step-9-politica-de-privacidad.png",
        appUrl,
      );
      report["Política de Privacidad"].finalUrl = privacy.finalUrl;
      addPass(report, "Política de Privacidad", "Privacy page opened and heading/content were validated.", [
        privacy.screenshotPath,
      ]);
    } catch (error) {
      const screenshot = await captureCheckpointSafe(page, "step-9-politica-de-privacidad-failure.png");
      addFailure(report, "Política de Privacidad", `Política de Privacidad validation failed: ${String(error)}`, [
        ...(screenshot ? [screenshot] : []),
      ]);
    }
    }

    // Step 10: Final report
    const finalReportPath = path.join(ARTIFACTS_DIR, "final-report.json");
    await writeFile(
      finalReportPath,
      `${JSON.stringify(
        {
          suite: "saleads_mi_negocio_full_test",
          generatedAt: new Date().toISOString(),
          results: report,
        },
        null,
        2,
      )}\n`,
      "utf8",
    );

    const failedSteps = reportKeys.filter((key) => report[key].status === "FAIL");
    if (failedSteps.length > 0) {
      throw new Error(`One or more workflow validations failed: ${failedSteps.join(", ")}. See ${finalReportPath}`);
    }
  });
});
