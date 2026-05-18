import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type Status = "PASS" | "FAIL";

type StepResult = {
  status: Status;
  details: string;
  evidence: string[];
  finalUrl?: string;
};

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
] as const;

const ARTIFACTS_DIR = path.join(process.cwd(), "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "saleads-mi-negocio-report.json");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function ensureArtifactsDirs(): void {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

function initReport(): Record<(typeof REPORT_FIELDS)[number], StepResult> {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = {
      status: "FAIL",
      details: "Step was not executed.",
      evidence: [],
    };
    return acc;
  }, {} as Record<(typeof REPORT_FIELDS)[number], StepResult>);
}

async function clickAndWaitForUi(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded();
  await Promise.all([
    page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {}),
    locator.click(),
  ]);
  await page.waitForTimeout(800);
}

async function firstVisible(
  candidates: Locator[],
  timeoutMs = 7000,
): Promise<Locator | null> {
  for (const candidate of candidates) {
    const visible = await candidate.first().isVisible({ timeout: timeoutMs }).catch(() => false);
    if (visible) return candidate.first();
  }
  return null;
}

async function checkpointScreenshot(page: Page, label: string, fullPage = false): Promise<string> {
  const sanitized = label
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
  const screenshotPath = path.join(SCREENSHOTS_DIR, `${Date.now()}-${sanitized}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function validateVisibleText(page: Page, text: RegExp): Promise<void> {
  await expect(page.getByText(text).first()).toBeVisible();
}

async function openLegalDocumentAndValidate(
  appPage: Page,
  linkText: RegExp,
  headingText: RegExp,
): Promise<{ screenshotPath: string; finalUrl: string }> {
  const context = appPage.context();
  const legalLink = await firstVisible([
    appPage.getByRole("link", { name: linkText }),
    appPage.getByRole("button", { name: linkText }),
    appPage.getByText(linkText),
  ]);

  if (!legalLink) {
    throw new Error(`Could not locate legal link with text ${linkText}.`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const currentUrl = appPage.url();
  await clickAndWaitForUi(appPage, legalLink);
  const popup = await popupPromise;

  const legalPage = popup ?? appPage;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await validateVisibleText(legalPage, headingText);

  const bodyText = await legalPage.locator("body").innerText();
  if (bodyText.trim().length < 100) {
    throw new Error("Legal content appears too short or missing.");
  }

  const screenshotPath = await checkpointScreenshot(legalPage, headingText.source, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
  } else if (appPage.url() !== currentUrl) {
    await appPage.goBack().catch(() => {});
    await appPage.waitForLoadState("domcontentloaded").catch(() => {});
  }

  return { screenshotPath, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  ensureArtifactsDirs();
  const report = initReport();
  const startUrl = process.env.SALEADS_START_URL ?? process.env.SALEADS_URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No start URL detected. Set SALEADS_START_URL (or SALEADS_URL) to the current SaleADS login page URL.",
    );
  }

  // Step 1: Login with Google.
  try {
    const googleLoginButton = await firstVisible([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesion con google|iniciar sesión con google/i),
    ]);

    if (!googleLoginButton) {
      throw new Error("Could not find a login button for Google.");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWaitForUi(page, googleLoginButton);
    const popup = await popupPromise;
    const authPage = popup ?? page;

    const accountOption = await firstVisible(
      [
        authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        authPage.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
      ],
      10000,
    );
    if (accountOption) {
      await clickAndWaitForUi(authPage, accountOption);
    }

    await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
    const sidebar = await firstVisible(
      [page.getByRole("navigation"), page.locator("aside"), page.locator('[class*="sidebar"]')],
      15000,
    );
    if (!sidebar) {
      throw new Error("Main app interface/sidebar did not become visible after login.");
    }

    const dashboardShot = await checkpointScreenshot(page, "dashboard-loaded", true);
    report["Login"] = {
      status: "PASS",
      details: "Dashboard/main app shell loaded and sidebar is visible.",
      evidence: [dashboardShot],
    };
  } catch (error) {
    report["Login"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Unknown error during login step.",
      evidence: [],
    };
  }

  // Step 2: Open Mi Negocio menu.
  try {
    const negocioSection = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    if (negocioSection) {
      await clickAndWaitForUi(page, negocioSection);
    }

    const miNegocioOption = await firstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    if (!miNegocioOption) {
      throw new Error("Could not find 'Mi Negocio' option in the sidebar.");
    }
    await clickAndWaitForUi(page, miNegocioOption);

    await validateVisibleText(page, /Agregar Negocio/i);
    await validateVisibleText(page, /Administrar Negocios/i);
    const menuShot = await checkpointScreenshot(page, "mi-negocio-menu-expandido");
    report["Mi Negocio menu"] = {
      status: "PASS",
      details: "Menu expanded and required submenu options are visible.",
      evidence: [menuShot],
    };
  } catch (error) {
    report["Mi Negocio menu"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Unknown error validating Mi Negocio menu.",
      evidence: [],
    };
  }

  // Step 3: Validate Agregar Negocio modal.
  try {
    const agregarNegocio = await firstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    if (!agregarNegocio) {
      throw new Error("Could not find 'Agregar Negocio' action.");
    }
    await clickAndWaitForUi(page, agregarNegocio);

    await validateVisibleText(page, /Crear Nuevo Negocio/i);
    await validateVisibleText(page, /Nombre del Negocio/i);
    await validateVisibleText(page, /Tienes 2 de 3 negocios/i);
    await validateVisibleText(page, /^Cancelar$/i);
    await validateVisibleText(page, /^Crear Negocio$/i);

    const nombreNegocioInput = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: "" }),
    ]);
    if (nombreNegocioInput) {
      await nombreNegocioInput.fill("Negocio Prueba Automatización");
    }

    const modalShot = await checkpointScreenshot(page, "modal-crear-nuevo-negocio");
    const cancelarButton = await firstVisible([
      page.getByRole("button", { name: /^Cancelar$/i }),
      page.getByText(/^Cancelar$/i),
    ]);
    if (cancelarButton) {
      await clickAndWaitForUi(page, cancelarButton);
    }

    report["Agregar Negocio modal"] = {
      status: "PASS",
      details: "Modal and required controls were validated.",
      evidence: [modalShot],
    };
  } catch (error) {
    report["Agregar Negocio modal"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Unknown error validating Agregar Negocio modal.",
      evidence: [],
    };
  }

  // Step 4: Open Administrar Negocios.
  try {
    const miNegocioOption = await firstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    if (miNegocioOption) {
      await clickAndWaitForUi(page, miNegocioOption);
    }

    const administrarNegociosOption = await firstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    if (!administrarNegociosOption) {
      throw new Error("Could not find 'Administrar Negocios' option.");
    }
    await clickAndWaitForUi(page, administrarNegociosOption);

    await validateVisibleText(page, /Información General/i);
    await validateVisibleText(page, /Detalles de la Cuenta/i);
    await validateVisibleText(page, /Tus Negocios/i);
    await validateVisibleText(page, /Sección Legal/i);
    const accountPageShot = await checkpointScreenshot(page, "administrar-negocios", true);

    report["Administrar Negocios view"] = {
      status: "PASS",
      details: "Account page loaded with all expected sections.",
      evidence: [accountPageShot],
    };
  } catch (error) {
    report["Administrar Negocios view"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Unknown error opening Administrar Negocios view.",
      evidence: [],
    };
  }

  // Step 5: Validate Información General.
  try {
    await validateVisibleText(page, /Información General/i);
    await validateVisibleText(page, /BUSINESS PLAN/i);
    await validateVisibleText(page, /Cambiar Plan/i);

    // Name and email visibility checks are intentionally broad because they vary by account.
    const emailVisible = await page.getByText(/@/i).first().isVisible().catch(() => false);
    if (!emailVisible) {
      throw new Error("Expected user email to be visible in Información General.");
    }

    const sectionText = await page.locator("body").innerText();
    const looksLikeName = /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(sectionText);
    if (!looksLikeName) {
      throw new Error("Expected user name to be visible in Información General.");
    }

    report["Información General"] = {
      status: "PASS",
      details: "Name, email, plan label, and plan change button are visible.",
      evidence: [],
    };
  } catch (error) {
    report["Información General"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Unknown error validating Información General.",
      evidence: [],
    };
  }

  // Step 6: Validate Detalles de la Cuenta.
  try {
    await validateVisibleText(page, /Detalles de la Cuenta/i);
    await validateVisibleText(page, /Cuenta creada/i);
    await validateVisibleText(page, /Estado activo/i);
    await validateVisibleText(page, /Idioma seleccionado/i);

    report["Detalles de la Cuenta"] = {
      status: "PASS",
      details: "All account details labels are visible.",
      evidence: [],
    };
  } catch (error) {
    report["Detalles de la Cuenta"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Unknown error validating Detalles de la Cuenta.",
      evidence: [],
    };
  }

  // Step 7: Validate Tus Negocios.
  try {
    await validateVisibleText(page, /Tus Negocios/i);
    await validateVisibleText(page, /Agregar Negocio/i);
    await validateVisibleText(page, /Tienes 2 de 3 negocios/i);

    report["Tus Negocios"] = {
      status: "PASS",
      details: "Business list section, add button, and quota text are visible.",
      evidence: [],
    };
  } catch (error) {
    report["Tus Negocios"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Unknown error validating Tus Negocios.",
      evidence: [],
    };
  }

  // Step 8: Validate Términos y Condiciones.
  try {
    const legalResult = await openLegalDocumentAndValidate(
      page,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
    );

    report["Términos y Condiciones"] = {
      status: "PASS",
      details: "Legal heading and content are visible.",
      evidence: [legalResult.screenshotPath],
      finalUrl: legalResult.finalUrl,
    };
  } catch (error) {
    report["Términos y Condiciones"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Unknown error validating Términos y Condiciones.",
      evidence: [],
    };
  }

  // Step 9: Validate Política de Privacidad.
  try {
    const legalResult = await openLegalDocumentAndValidate(
      page,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
    );

    report["Política de Privacidad"] = {
      status: "PASS",
      details: "Legal heading and content are visible.",
      evidence: [legalResult.screenshotPath],
      finalUrl: legalResult.finalUrl,
    };
  } catch (error) {
    report["Política de Privacidad"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Unknown error validating Política de Privacidad.",
      evidence: [],
    };
  }

  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
  console.log(`SaleADS report generated at: ${REPORT_PATH}`);
  console.table(
    Object.entries(report).map(([name, result]) => ({
      step: name,
      status: result.status,
      details: result.details,
      finalUrl: result.finalUrl ?? "",
    })),
  );

  const failedSteps = Object.entries(report)
    .filter(([, result]) => result.status === "FAIL")
    .map(([name, result]) => `${name}: ${result.details}`);
  expect(failedSteps, `Failed validations:\n${failedSteps.join("\n")}`).toEqual([]);
});
