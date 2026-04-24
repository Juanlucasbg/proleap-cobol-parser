import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";
import fs from "fs";
import path from "path";

type ReportStatus = "PASS" | "FAIL";

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

interface WorkflowReport {
  runName: string;
  executedAt: string;
  environment: string;
  legalUrls: {
    terminosYCondiciones: string | null;
    politicaDePrivacidad: string | null;
  };
  results: Record<ReportField, ReportStatus>;
}

const RUN_NAME = "saleads_mi_negocio_full_test";
const ARTIFACTS_DIR = path.resolve(
  __dirname,
  "..",
  process.env.SALEADS_ARTIFACTS_DIR?.trim() || "artifacts"
);
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORTS_DIR = path.join(ARTIFACTS_DIR, "reports");
const REPORT_FILE = path.join(REPORTS_DIR, `${RUN_NAME}.json`);

const EMAIL = process.env.SALEADS_GOOGLE_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";

function normalizeText(value: string): string {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
}

function hasTerm(normalizedSource: string, options: string[]): boolean {
  return options.some((option) => normalizedSource.includes(normalizeText(option)));
}

async function clickAndWaitUi(page: Page, locator: Locator): Promise<void> {
  await locator.waitFor({ state: "visible" });
  await locator.click();
  await Promise.all([
    page.waitForLoadState("domcontentloaded").catch(() => null),
    page.waitForLoadState("networkidle").catch(() => null),
    page.waitForTimeout(500)
  ]);
}

async function waitForAnyVisible(page: Page, selectors: Array<string>, timeoutMs = 15_000): Promise<Locator> {
  const start = Date.now();
  const timeoutAt = start + timeoutMs;

  while (Date.now() < timeoutAt) {
    for (const selector of selectors) {
      const locator = page.locator(selector);
      if (await locator.first().isVisible().catch(() => false)) {
        return locator.first();
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`None of the selectors became visible: ${selectors.join(", ")}`);
}

async function clickByCandidateText(page: Page, candidates: string[]): Promise<void> {
  for (const text of candidates) {
    const exact = page.getByText(text, { exact: true }).first();
    if (await exact.isVisible().catch(() => false)) {
      await clickAndWaitUi(page, exact);
      return;
    }

    const partial = page.getByText(text).first();
    if (await partial.isVisible().catch(() => false)) {
      await clickAndWaitUi(page, partial);
      return;
    }
  }

  throw new Error(`Could not find clickable text among: ${candidates.join(", ")}`);
}

async function pickGoogleAccountIfPrompted(page: Page): Promise<void> {
  const accountSelector = page.getByText(EMAIL).first();
  if (await accountSelector.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await clickAndWaitUi(page, accountSelector);
  }
}

async function expectTextVisible(page: Page, textCandidates: string[]): Promise<void> {
  let found = false;
  let lastError: unknown = null;
  for (const candidate of textCandidates) {
    const locator = page.getByText(candidate).first();
    try {
      await expect(locator).toBeVisible({ timeout: 20_000 });
      found = true;
      break;
    } catch (error) {
      lastError = error;
    }
  }

  if (!found) {
    throw new Error(
      `None of expected texts are visible: ${textCandidates.join(", ")}; last error: ${String(lastError)}`
    );
  }
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarVisible = await page.getByText("Agregar Negocio").first().isVisible().catch(() => false);
  const administrarVisible = await page.getByText("Administrar Negocios").first().isVisible().catch(() => false);

  if (agregarVisible && administrarVisible) {
    return;
  }

  await clickByCandidateText(page, ["Mi Negocio", "Negocio"]);
}

async function capture(page: Page, checkpoint: string, fullPage = false): Promise<void> {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
  const screenshotPath = path.join(SCREENSHOTS_DIR, checkpoint);
  await page.screenshot({ path: screenshotPath, fullPage });
}

async function verifyLegalPageAndReturn(
  context: BrowserContext,
  appPage: Page,
  linkTextCandidates: string[],
  headingCandidates: string[],
  screenshotName: string
): Promise<string> {
  let opener: Locator | null = null;
  for (const candidate of linkTextCandidates) {
    const candidateLocator = appPage.getByText(candidate).first();
    if (await candidateLocator.isVisible().catch(() => false)) {
      opener = candidateLocator;
      break;
    }
  }
  if (!opener) {
    throw new Error(`Could not find legal link with texts: ${linkTextCandidates.join(", ")}`);
  }
  await opener.waitFor({ state: "visible", timeout: 20_000 });

  const popupPromise = context.waitForEvent("page", { timeout: 5_000 }).catch(() => null);
  await opener.click();

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;

  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForLoadState("networkidle");

  await expectTextVisible(legalPage, headingCandidates);
  const pageBody = normalizeText(await legalPage.locator("body").innerText());

  const isExpectedContent = hasTerm(pageBody, headingCandidates) || hasTerm(pageBody, ["terminos", "politica"]);
  if (!isExpectedContent) {
    throw new Error(`Legal content text not detected for link ${linkTextCandidates.join(" / ")}`);
  }

  await capture(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else {
    await appPage.goBack().catch(() => null);
    await appPage.waitForLoadState("domcontentloaded");
    await appPage.waitForLoadState("networkidle");
  }

  return finalUrl;
}

test.describe("SaleADS Mi Negocio Full Workflow", () => {
  test("Login with Google and validate Mi Negocio module workflow", async ({ page, context }) => {
    const baseUrl = process.env.SALEADS_BASE_URL;
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    }

    await page.waitForLoadState("domcontentloaded");
    await page.waitForLoadState("networkidle");

    const report: WorkflowReport = {
      runName: RUN_NAME,
      executedAt: new Date().toISOString(),
      environment: baseUrl ?? "current-open-login-page",
      legalUrls: {
        terminosYCondiciones: null,
        politicaDePrivacidad: null
      },
      results: {
        Login: "FAIL",
        "Mi Negocio menu": "FAIL",
        "Agregar Negocio modal": "FAIL",
        "Administrar Negocios view": "FAIL",
        "Información General": "FAIL",
        "Detalles de la Cuenta": "FAIL",
        "Tus Negocios": "FAIL",
        "Términos y Condiciones": "FAIL",
        "Política de Privacidad": "FAIL"
      }
    };

    const writeReport = (): void => {
      fs.mkdirSync(REPORTS_DIR, { recursive: true });
      fs.writeFileSync(REPORT_FILE, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    };

    try {
      // Step 1: Login with Google
      const loginButton = await waitForAnyVisible(page, [
        'button:has-text("Sign in with Google")',
        'button:has-text("Iniciar sesión con Google")',
        'button:has-text("Iniciar sesion con Google")',
        'button:has-text("Google")',
        'text="Sign in with Google"',
        'text="Iniciar sesión con Google"',
        'text="Iniciar sesion con Google"'
      ]);
      await clickAndWaitUi(page, loginButton);
      await pickGoogleAccountIfPrompted(page);

      await waitForAnyVisible(page, [
        'nav >> text=Negocio',
        'aside >> text=Negocio',
        'text=Mi Negocio',
        'text=Agregar Negocio'
      ]);
      await capture(page, "01-dashboard-loaded.png", true);
      report.results.Login = "PASS";

      // Step 2: Open Mi Negocio menu
      await ensureMiNegocioExpanded(page);
      await expectTextVisible(page, ["Agregar Negocio"]);
      await expectTextVisible(page, ["Administrar Negocios"]);
      await capture(page, "02-mi-negocio-expanded.png", false);
      report.results["Mi Negocio menu"] = "PASS";

      // Step 3: Validate Agregar Negocio modal
      await clickByCandidateText(page, ["Agregar Negocio"]);
      await expectTextVisible(page, ["Crear Nuevo Negocio"]);
      await expectTextVisible(page, ["Nombre del Negocio"]);
      await expectTextVisible(page, ["Tienes 2 de 3 negocios"]);
      await expectTextVisible(page, ["Cancelar"]);
      await expectTextVisible(page, ["Crear Negocio"]);
      const nombreNegocioInput = page.getByPlaceholder("Nombre del Negocio").first();
      if (await nombreNegocioInput.isVisible().catch(() => false)) {
        await nombreNegocioInput.click();
        await nombreNegocioInput.fill("Negocio Prueba Automatización");
      }
      await capture(page, "03-agregar-negocio-modal.png", false);
      await clickByCandidateText(page, ["Cancelar"]);
      report.results["Agregar Negocio modal"] = "PASS";

      // Step 4: Open Administrar Negocios
      await ensureMiNegocioExpanded(page);
      await clickByCandidateText(page, ["Administrar Negocios"]);
      await expectTextVisible(page, ["Información General", "Informacion General"]);
      await expectTextVisible(page, ["Detalles de la Cuenta"]);
      await expectTextVisible(page, ["Tus Negocios"]);
      await expectTextVisible(page, ["Sección Legal", "Seccion Legal"]);
      await capture(page, "04-administrar-negocios-page.png", true);
      report.results["Administrar Negocios view"] = "PASS";

      // Step 5: Validate Información General
      await expectTextVisible(page, ["BUSINESS PLAN"]);
      await expectTextVisible(page, ["Cambiar Plan"]);
      await expect(page.getByText(EMAIL).first()).toBeVisible({ timeout: 20_000 });
      const nameCandidates = [
        page.locator("section:has-text('Información General') h1, section:has-text('Informacion General') h1").first(),
        page.locator("section:has-text('Información General') h2, section:has-text('Informacion General') h2").first(),
        page.locator("section:has-text('Información General') p, section:has-text('Informacion General') p").first()
      ];
      let hasVisibleName = false;
      for (const locator of nameCandidates) {
        if (await locator.isVisible().catch(() => false)) {
          const text = (await locator.innerText()).trim();
          if (text.length > 0 && !normalizeText(text).includes(normalizeText(EMAIL))) {
            hasVisibleName = true;
            break;
          }
        }
      }
      expect(hasVisibleName).toBeTruthy();
      report.results["Información General"] = "PASS";

      // Step 6: Validate Detalles de la Cuenta
      await expectTextVisible(page, ["Cuenta creada"]);
      await expectTextVisible(page, ["Estado activo"]);
      await expectTextVisible(page, ["Idioma seleccionado"]);
      report.results["Detalles de la Cuenta"] = "PASS";

      // Step 7: Validate Tus Negocios
      await expectTextVisible(page, ["Tus Negocios"]);
      await expectTextVisible(page, ["Agregar Negocio"]);
      await expectTextVisible(page, ["Tienes 2 de 3 negocios"]);
      report.results["Tus Negocios"] = "PASS";

      // Step 8: Validate Términos y Condiciones
      report.legalUrls.terminosYCondiciones = await verifyLegalPageAndReturn(
        context,
        page,
        ["Términos y Condiciones"],
        ["Términos y Condiciones", "Terminos y Condiciones"],
        "05-terminos-y-condiciones.png"
      );
      report.results["Términos y Condiciones"] = "PASS";

      // Step 9: Validate Política de Privacidad
      report.legalUrls.politicaDePrivacidad = await verifyLegalPageAndReturn(
        context,
        page,
        ["Política de Privacidad"],
        ["Política de Privacidad", "Politica de Privacidad"],
        "06-politica-de-privacidad.png"
      );
      report.results["Política de Privacidad"] = "PASS";

      writeReport();
    } catch (error) {
      writeReport();
      throw error;
    }
  });
});
