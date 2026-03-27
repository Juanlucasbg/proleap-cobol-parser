import { expect, type BrowserContext, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs/promises";
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

type ReportStatus = "PASS" | "FAIL";

interface StepResult {
  status: ReportStatus;
  details: string;
  screenshots: string[];
  finalUrl?: string;
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

const GOOGLE_ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const RUN_ID = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACTS_DIR = path.resolve(process.cwd(), "artifacts", "saleads_mi_negocio_full_test", RUN_ID);
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");

function buildInitialReport(): Record<ReportField, StepResult> {
  return REPORT_FIELDS.reduce(
    (accumulator, field) => ({
      ...accumulator,
      [field]: {
        status: "FAIL",
        details: "Step not executed.",
        screenshots: [],
      },
    }),
    {} as Record<ReportField, StepResult>,
  );
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function pickVisible(candidates: Locator[], description: string): Promise<Locator> {
  for (const candidate of candidates) {
    const visible = await candidate.first().isVisible({ timeout: 2000 }).catch(() => false);
    if (visible) {
      return candidate.first();
    }
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function clickAndWaitUi(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 20000 });
  const possibleNavigation = page.waitForLoadState("domcontentloaded", { timeout: 12000 }).catch(() => undefined);
  await locator.click();
  await possibleNavigation;
  await page.waitForTimeout(900);
}

async function captureScreenshot(page: Page, fileName: string, fullPage = false): Promise<string> {
  await fs.mkdir(SCREENSHOTS_DIR, { recursive: true });
  const outputPath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function chooseGoogleAccountIfPrompt(targetPage: Page): Promise<void> {
  const accountLocator = targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  const accountVisible = await accountLocator.isVisible({ timeout: 10000 }).catch(() => false);
  if (!accountVisible) {
    return;
  }

  await clickAndWaitUi(targetPage, accountLocator);

  const continueLocator = targetPage.getByRole("button", { name: /continuar|continue|siguiente|next/i }).first();
  const continueVisible = await continueLocator.isVisible({ timeout: 1500 }).catch(() => false);
  if (continueVisible) {
    await clickAndWaitUi(targetPage, continueLocator);
  }
}

async function ensureEntryPoint(page: Page): Promise<void> {
  const configuredUrl = process.env.SALEADS_URL ?? process.env.BASE_URL;
  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(1000);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error("Set SALEADS_URL (or BASE_URL) to the environment login page before running this test.");
  }
}

async function openLegalPage(
  page: Page,
  context: BrowserContext,
  linkLabel: string,
  headingPattern: RegExp,
): Promise<{ finalUrl: string; screenshot: string }> {
  const link = await pickVisible(
    [
      page.getByRole("link", { name: new RegExp(linkLabel, "i") }),
      page.getByRole("button", { name: new RegExp(linkLabel, "i") }),
      page.getByText(new RegExp(linkLabel, "i")),
    ],
    linkLabel,
  );

  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await clickAndWaitUi(page, link);

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => undefined);
  await legalPage.waitForTimeout(1500);

  const heading = await pickVisible(
    [legalPage.getByRole("heading", { name: headingPattern }), legalPage.getByText(headingPattern)],
    `${linkLabel} heading`,
  );
  await expect(heading).toBeVisible();

  const legalText = (await legalPage.locator("body").innerText()).trim();
  expect(legalText.length, `Expected legal content text on ${linkLabel}.`).toBeGreaterThan(180);

  const screenshot = await captureScreenshot(legalPage, `${linkLabel.replace(/\s+/g, "_").toLowerCase()}.png`, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => undefined);
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await page.waitForTimeout(900);
  }

  return { finalUrl, screenshot };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report = buildInitialReport();
  const errors: string[] = [];

  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });

  const runStep = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[field].status = "PASS";
      if (report[field].details === "Step not executed.") {
        report[field].details = "All validations passed.";
      }
    } catch (error) {
      const message = errorMessage(error);
      report[field].status = "FAIL";
      report[field].details = message;
      errors.push(`${field}: ${message}`);
    }
  };

  await runStep("Login", async () => {
    await ensureEntryPoint(page);

    const sidebarVisibleBeforeLogin = await page
      .getByText(/mi negocio|administrar negocios|negocio/i)
      .first()
      .isVisible({ timeout: 3000 })
      .catch(() => false);

    if (!sidebarVisibleBeforeLogin) {
      const signInButton = await pickVisible(
        [
          page.getByRole("button", { name: /google|iniciar sesión|sign in/i }),
          page.getByRole("link", { name: /google|iniciar sesión|sign in/i }),
          page.getByText(/google|iniciar sesión|sign in/i),
        ],
        "Google login trigger",
      );

      const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
      await clickAndWaitUi(page, signInButton);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => undefined);
        await chooseGoogleAccountIfPrompt(popup);
      } else {
        await chooseGoogleAccountIfPrompt(page);
      }
    }

    await expect(page.getByText(/mi negocio|administrar negocios|negocio/i).first()).toBeVisible({ timeout: 90000 });
    await expect(page.locator("main, [role='main']").first()).toBeVisible({ timeout: 30000 });

    const screenshot = await captureScreenshot(page, "01_dashboard_loaded.png", true);
    report.Login.screenshots.push(screenshot);
    report.Login.details = "Dashboard loaded and left sidebar visible.";
  });

  await runStep("Mi Negocio menu", async () => {
    const miNegocioMenu = await pickVisible(
      [
        page.getByRole("button", { name: /^mi negocio$/i }),
        page.getByRole("link", { name: /^mi negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      "Mi Negocio menu",
    );

    await clickAndWaitUi(page, miNegocioMenu);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: 15000 });

    const screenshot = await captureScreenshot(page, "02_mi_negocio_expanded.png", true);
    report["Mi Negocio menu"].screenshots.push(screenshot);
    report["Mi Negocio menu"].details = "Mi Negocio menu expanded and submenu options are visible.";
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusinessTrigger = await pickVisible(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      "Agregar Negocio option",
    );
    await clickAndWaitUi(page, addBusinessTrigger);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 15000 });

    const screenshot = await captureScreenshot(page, "03_agregar_negocio_modal.png", true);
    report["Agregar Negocio modal"].screenshots.push(screenshot);

    const nameInput = await pickVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
        page.locator("input[name*='nombre' i]"),
      ],
      "Nombre del Negocio input",
    );
    await nameInput.fill("Negocio Prueba Automatizacion");

    const cancelButton = await pickVisible(
      [page.getByRole("button", { name: /Cancelar/i }), page.getByText(/^Cancelar$/i)],
      "Cancelar button",
    );
    await clickAndWaitUi(page, cancelButton);

    report["Agregar Negocio modal"].details = "Agregar Negocio modal validated and closed via Cancelar.";
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegociosOptionVisible = await page
      .getByText(/^Administrar Negocios$/i)
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);

    if (!administrarNegociosOptionVisible) {
      const miNegocioMenu = await pickVisible(
        [
          page.getByRole("button", { name: /^mi negocio$/i }),
          page.getByRole("link", { name: /^mi negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        "Mi Negocio menu reopen",
      );
      await clickAndWaitUi(page, miNegocioMenu);
    }

    const administrarNegociosOption = await pickVisible(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      "Administrar Negocios option",
    );
    await clickAndWaitUi(page, administrarNegociosOption);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 30000 });

    const screenshot = await captureScreenshot(page, "04_administrar_negocios_view.png", true);
    report["Administrar Negocios view"].screenshots.push(screenshot);
    report["Administrar Negocios view"].details = "Administrar Negocios page loaded with all required sections.";
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 15000 });

    const pageText = await page.locator("body").innerText();
    expect(pageText, "Expected user email to be visible in account information.").toMatch(
      /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i,
    );
    expect(pageText, "Expected user name to be visible in account information.").toMatch(
      /[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+/,
    );

    report["Información General"].details = "User name, email, plan, and Cambiar Plan button are visible.";
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 15000 });
    report["Detalles de la Cuenta"].details = "Cuenta creada, Estado activo, and Idioma seleccionado are visible.";
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({ timeout: 15000 });

    const businessEntriesCount = await page
      .locator("li, tr, [role='row']")
      .filter({ hasText: /negocio|business/i })
      .count();
    expect(businessEntriesCount, "Expected at least one business entry in Tus Negocios section.").toBeGreaterThan(0);

    report["Tus Negocios"].details = "Tus Negocios section and business list are visible.";
  });

  await runStep("Términos y Condiciones", async () => {
    const legalEvidence = await openLegalPage(page, context, "Términos y Condiciones", /Términos y Condiciones/i);
    report["Términos y Condiciones"].screenshots.push(legalEvidence.screenshot);
    report["Términos y Condiciones"].finalUrl = legalEvidence.finalUrl;
    report["Términos y Condiciones"].details = `Legal page validated. Final URL: ${legalEvidence.finalUrl}`;
  });

  await runStep("Política de Privacidad", async () => {
    const legalEvidence = await openLegalPage(page, context, "Política de Privacidad", /Política de Privacidad/i);
    report["Política de Privacidad"].screenshots.push(legalEvidence.screenshot);
    report["Política de Privacidad"].finalUrl = legalEvidence.finalUrl;
    report["Política de Privacidad"].details = `Legal page validated. Final URL: ${legalEvidence.finalUrl}`;
  });

  const markdownLines = [
    "# saleads_mi_negocio_full_test report",
    "",
    `Generated at: ${new Date().toISOString()}`,
    "",
    "| Validation | Status | Details | URL |",
    "|---|---|---|---|",
    ...REPORT_FIELDS.map((field) => {
      const result = report[field];
      const details = result.details.replace(/\|/g, "\\|");
      const finalUrl = result.finalUrl ?? "";
      return `| ${field} | ${result.status} | ${details} | ${finalUrl} |`;
    }),
    "",
    "## Evidence",
    "",
    ...REPORT_FIELDS.flatMap((field) => {
      const result = report[field];
      if (result.screenshots.length === 0) {
        return [`- ${field}: (no screenshots)`];
      }
      return result.screenshots.map((shot) => `- ${field}: ${shot}`);
    }),
    "",
  ];

  await fs.writeFile(path.join(ARTIFACTS_DIR, "final-report.json"), JSON.stringify(report, null, 2), "utf8");
  await fs.writeFile(path.join(ARTIFACTS_DIR, "final-report.md"), markdownLines.join("\n"), "utf8");

  expect(
    errors,
    errors.length === 0 ? "All required validations passed." : `One or more validations failed:\n${errors.join("\n")}`,
  ).toEqual([]);
});
