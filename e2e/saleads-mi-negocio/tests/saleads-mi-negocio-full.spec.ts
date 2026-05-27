import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

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

interface WorkflowReport {
  testName: string;
  executedAt: string;
  environment: {
    loginUrl: string;
    currentHost: string;
  };
  results: Record<ReportField, StepResult>;
  evidence: {
    dashboardScreenshot?: string;
    menuScreenshot?: string;
    modalScreenshot?: string;
    accountPageScreenshot?: string;
    termsScreenshot?: string;
    termsUrl?: string;
    privacyScreenshot?: string;
    privacyUrl?: string;
  };
  errors: Partial<Record<ReportField, string>>;
}

const REPORT_FIELDS: ReportField[] = [
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

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
}

async function firstVisible(locator: Locator, timeout = 2_000): Promise<boolean> {
  return locator.first().isVisible({ timeout }).catch(() => false);
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const exactTextRegex = new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i");
  const containsTextRegex = new RegExp(escapeRegExp(text), "i");
  const candidates: Locator[] = [
    page.getByRole("button", { name: exactTextRegex }),
    page.getByRole("link", { name: exactTextRegex }),
    page.getByRole("menuitem", { name: exactTextRegex }),
    page.getByRole("tab", { name: exactTextRegex }),
    page.getByRole("option", { name: exactTextRegex }),
    page.getByText(exactTextRegex),
    page.getByText(containsTextRegex),
  ];

  for (const candidate of candidates) {
    if (await firstVisible(candidate)) {
      await candidate.first().click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`No se encontró un elemento visible con texto "${text}".`);
}

async function screenshotCheckpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  options: { fullPage?: boolean } = {},
): Promise<string | undefined> {
  const outputPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: outputPath, fullPage: !!options.fullPage });
  await testInfo.attach(name, { path: outputPath, contentType: "image/png" });
  return outputPath;
}

function sectionByHeading(page: Page, headingPattern: RegExp): Locator {
  return page
    .locator("section, article, div")
    .filter({ has: page.getByRole("heading", { name: headingPattern }) })
    .first();
}

async function assertAnyVisibleText(container: Page | Locator, patterns: RegExp[]): Promise<void> {
  for (const pattern of patterns) {
    if (await firstVisible(container.getByText(pattern), 1_500)) {
      return;
    }
  }
  throw new Error(`No visible text matched any of: ${patterns.map((p) => p.toString()).join(", ")}`);
}

async function runStep(
  report: WorkflowReport,
  step: ReportField,
  callback: () => Promise<void>,
): Promise<void> {
  try {
    await callback();
    report.results[step] = "PASS";
  } catch (error) {
    report.results[step] = "FAIL";
    report.errors[step] = error instanceof Error ? error.message : String(error);
  }
}

async function clickLegalLinkAndValidate(
  page: Page,
  testInfo: TestInfo,
  linkText: string,
  headingRegex: RegExp,
  screenshotName: string,
): Promise<{ finalUrl: string; screenshotPath?: string }> {
  const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
  await clickByVisibleText(page, linkText);

  const popup = await popupPromise;
  const target = popup ?? page;
  await waitForUi(target);

  await expect(target.getByRole("heading", { name: headingRegex })).toBeVisible();
  await assertAnyVisibleText(target, [/\b(condiciones|privacidad|uso|informaci[oó]n|datos)\b/i, /saleads/i]);

  const screenshotPath = await screenshotCheckpoint(target, testInfo, screenshotName, { fullPage: true });
  const finalUrl = target.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  return { finalUrl, screenshotPath };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const configuredLoginUrl = process.env.SALEADS_LOGIN_URL?.trim() ?? "";
  const report: WorkflowReport = {
    testName: "saleads_mi_negocio_full_test",
    executedAt: new Date().toISOString(),
    environment: {
      loginUrl: configuredLoginUrl || "NOT_PROVIDED",
      currentHost: "NOT_AVAILABLE",
    },
    results: REPORT_FIELDS.reduce(
      (acc, field) => {
        acc[field] = "FAIL";
        return acc;
      },
      {} as Record<ReportField, StepResult>,
    ),
    evidence: {},
    errors: {},
  };

  try {
    if (configuredLoginUrl) {
      await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No hay SALEADS_LOGIN_URL configurada. Define SALEADS_LOGIN_URL para ejecutar este flujo en cualquier entorno.",
      );
    }

    report.environment.currentHost = new URL(page.url()).host;

    await runStep(report, "Login", async () => {
      const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
      const loginCandidates = [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Google",
      ];

      let clicked = false;
      for (const label of loginCandidates) {
        try {
          await clickByVisibleText(page, label);
          clicked = true;
          break;
        } catch {
          // keep trying alternatives
        }
      }
      if (!clicked) {
        throw new Error("No se encontró botón de login con Google.");
      }

      const popup = await popupPromise;
      const googlePage = popup ?? page;
      const accountEmail = "juanlucasbarbiergarzon@gmail.com";
      const accountLocator = googlePage.getByText(accountEmail, { exact: true });

      if (await firstVisible(accountLocator, 12_000)) {
        await accountLocator.click();
      }

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 12_000 }).catch(() => undefined);
      }

      await waitForUi(page);
      await expect(page.locator("aside, nav").first()).toBeVisible();
      await expect(page.getByText(/Negocio/i)).toBeVisible();

      report.evidence.dashboardScreenshot = await screenshotCheckpoint(page, testInfo, "01-dashboard-loaded");
    });

    await runStep(report, "Mi Negocio menu", async () => {
      await expect(page.locator("aside, nav").first()).toBeVisible();

      await clickByVisibleText(page, "Mi Negocio");
      await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();

      report.evidence.menuScreenshot = await screenshotCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
    });

    await runStep(report, "Agregar Negocio modal", async () => {
      await clickByVisibleText(page, "Agregar Negocio");
      const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
      await expect(modal).toBeVisible();
      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible();
      await expect(modal.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      await modal.getByLabel(/Nombre del Negocio/i).fill("Negocio Prueba Automatización");
      report.evidence.modalScreenshot = await screenshotCheckpoint(page, testInfo, "03-agregar-negocio-modal");
      await modal.getByRole("button", { name: /Cancelar/i }).click();
      await expect(modal).toBeHidden();
      await waitForUi(page);
    });

    await runStep(report, "Administrar Negocios view", async () => {
      if (!(await firstVisible(page.getByText(/Administrar Negocios/i), 1_500))) {
        await clickByVisibleText(page, "Mi Negocio");
      }

      await clickByVisibleText(page, "Administrar Negocios");
      await expect(page.getByRole("heading", { name: /Informaci[oó]n General/i })).toBeVisible();
      await expect(page.getByRole("heading", { name: /Detalles de la Cuenta/i })).toBeVisible();
      await expect(page.getByRole("heading", { name: /Tus Negocios/i })).toBeVisible();
      await expect(page.getByRole("heading", { name: /Secci[oó]n Legal/i })).toBeVisible();

      report.evidence.accountPageScreenshot = await screenshotCheckpoint(page, testInfo, "04-account-page", {
        fullPage: true,
      });
    });

    await runStep(report, "Información General", async () => {
      const generalSection = sectionByHeading(page, /Informaci[oó]n General/i);
      await expect(generalSection).toBeVisible();
      await expect(generalSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(generalSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      await assertAnyVisibleText(generalSection, [/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i]);
      await assertAnyVisibleText(generalSection, [/[A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,}/]);
    });

    await runStep(report, "Detalles de la Cuenta", async () => {
      const accountDetailsSection = sectionByHeading(page, /Detalles de la Cuenta/i);
      await expect(accountDetailsSection).toBeVisible();
      await expect(accountDetailsSection.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(accountDetailsSection.getByText(/Estado activo/i)).toBeVisible();
      await expect(accountDetailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await runStep(report, "Tus Negocios", async () => {
      const businessesSection = sectionByHeading(page, /Tus Negocios/i);
      await expect(businessesSection).toBeVisible();
      await expect(businessesSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
      await expect(businessesSection.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();

      const visibleItems =
        (await businessesSection.locator("li:visible, article:visible, tr:visible, [role='row']:visible").count()) > 0;
      if (!visibleItems) {
        throw new Error("No se detectó una lista visible de negocios.");
      }
    });

    await runStep(report, "Términos y Condiciones", async () => {
      const result = await clickLegalLinkAndValidate(
        page,
        testInfo,
        "Términos y Condiciones",
        /T[eé]rminos y Condiciones/i,
        "05-terminos-y-condiciones",
      );
      report.evidence.termsUrl = result.finalUrl;
      report.evidence.termsScreenshot = result.screenshotPath;
    });

    await runStep(report, "Política de Privacidad", async () => {
      const result = await clickLegalLinkAndValidate(
        page,
        testInfo,
        "Política de Privacidad",
        /Pol[ií]tica de Privacidad/i,
        "06-politica-de-privacidad",
      );
      report.evidence.privacyUrl = result.finalUrl;
      report.evidence.privacyScreenshot = result.screenshotPath;
    });
  } finally {
    const reportDirectory = path.join(process.cwd(), "artifacts");
    fs.mkdirSync(reportDirectory, { recursive: true });
    const reportPath = path.join(reportDirectory, "saleads_mi_negocio_report.json");
    fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    await testInfo.attach("saleads-mi-negocio-report", {
      path: reportPath,
      contentType: "application/json",
    });
  }

  const failedSteps = REPORT_FIELDS.filter((field) => report.results[field] !== "PASS");
  expect(
    failedSteps,
    `Validaciones fallidas: ${failedSteps.join(", ")}. Revisa artifacts/saleads_mi_negocio_report.json`,
  ).toEqual([]);
});
