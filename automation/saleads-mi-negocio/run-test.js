const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const ARTIFACTS_DIR = path.join(__dirname, "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "report.json");

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const DEFAULT_TIMEOUT_MS = Number(process.env.SALEADS_TIMEOUT_MS || 30000);
const UI_SETTLE_MS = Number(process.env.SALEADS_UI_SETTLE_MS || 1000);
const HEADLESS = process.env.SALEADS_HEADLESS !== "false";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL || "";

const report = {
  name: "saleads_mi_negocio_full_test",
  triggeredAt: new Date().toISOString(),
  environment: {
    loginUrlConfigured: Boolean(LOGIN_URL),
    loginUrlValue: LOGIN_URL || null,
    headless: HEADLESS,
    timeoutMs: DEFAULT_TIMEOUT_MS,
    uiSettleMs: UI_SETTLE_MS
  },
  steps: {},
  finalStatus: "FAIL",
  notes: [],
  evidence: {
    screenshots: [],
    urls: {}
  }
};

function ensureDir(targetPath) {
  fs.mkdirSync(targetPath, { recursive: true });
}

function markStep(stepKey, update) {
  const current = report.steps[stepKey] || {};
  report.steps[stepKey] = { ...current, ...update };
}

function sanitizeFileName(input) {
  return input.replace(/[^a-zA-Z0-9-_]/g, "_");
}

async function waitForUiAfterClick(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(UI_SETTLE_MS);
}

async function findByVisibleText(page, candidates, options = {}) {
  const exact = options.exact ?? true;
  for (const candidate of candidates) {
    const locator = page.getByText(candidate, { exact });
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }
  return null;
}

async function clickByVisibleText(page, candidates, options = {}) {
  const locator = await findByVisibleText(page, candidates, options);
  if (!locator) {
    throw new Error(`Unable to find visible element for any of: ${candidates.join(", ")}`);
  }
  await locator.click({ timeout: DEFAULT_TIMEOUT_MS });
  await waitForUiAfterClick(page);
}

async function captureCheckpoint(page, name, fullPage = false) {
  const fileName = `${new Date().toISOString().replace(/[:.]/g, "-")}-${sanitizeFileName(name)}.png`;
  const fullPath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: fullPath, fullPage });
  report.evidence.screenshots.push({
    name,
    path: path.relative(__dirname, fullPath),
    fullPage
  });
}

async function optionalGoogleAccountSelection(page) {
  const accountLocator = page.getByText(GOOGLE_ACCOUNT, { exact: true });
  if (await accountLocator.first().isVisible().catch(() => false)) {
    await accountLocator.first().click({ timeout: 10000 });
    await waitForUiAfterClick(page);
    return true;
  }
  return false;
}

async function validateTextsVisible(page, label, requiredTexts) {
  const missing = [];
  for (const text of requiredTexts) {
    const locator = page.getByText(text, { exact: true });
    const visible = await locator.first().isVisible().catch(() => false);
    if (!visible) {
      missing.push(text);
    }
  }
  if (missing.length > 0) {
    throw new Error(`${label} missing expected texts: ${missing.join(", ")}`);
  }
}

async function validateContainsVisibleText(page, label, requiredTexts) {
  const missing = [];
  for (const text of requiredTexts) {
    const locator = page.getByText(text);
    const visible = await locator.first().isVisible().catch(() => false);
    if (!visible) {
      missing.push(text);
    }
  }
  if (missing.length > 0) {
    throw new Error(`${label} missing expected content: ${missing.join(", ")}`);
  }
}

async function openLegalDocumentFromClick(appPage, linkTextCandidates) {
  const linkLocator = await findByVisibleText(appPage, linkTextCandidates, { exact: true });
  if (!linkLocator) {
    throw new Error(`Could not find legal link: ${linkTextCandidates.join(", ")}`);
  }

  const currentUrl = appPage.url();
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await linkLocator.click({ timeout: DEFAULT_TIMEOUT_MS });
  await waitForUiAfterClick(appPage);

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded").catch(() => {});
    await popup.waitForLoadState("networkidle").catch(() => {});
    await popup.waitForTimeout(UI_SETTLE_MS);
    return {
      legalPage: popup,
      url: popup.url(),
      cleanup: async () => {
        await popup.close();
        await appPage.bringToFront();
        await waitForUiAfterClick(appPage);
      }
    };
  }

  if (appPage.url() !== currentUrl) {
    await appPage.waitForLoadState("domcontentloaded").catch(() => {});
    await appPage.waitForLoadState("networkidle").catch(() => {});
    await appPage.waitForTimeout(UI_SETTLE_MS);
    return {
      legalPage: appPage,
      url: appPage.url(),
      cleanup: async () => {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUiAfterClick(appPage);
      }
    };
  }

  throw new Error("No navigation or new tab detected after clicking legal link.");
}

async function run() {
  ensureDir(ARTIFACTS_DIR);
  ensureDir(SCREENSHOTS_DIR);

  let browser;
  try {
    browser = await chromium.launch({ headless: HEADLESS });
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

    if (LOGIN_URL) {
      await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUiAfterClick(page);
    } else {
      report.notes.push(
        "SALEADS_LOGIN_URL is not configured. Test expects the browser to already be on a SaleADS login page."
      );
    }

    // Step 1: Login with Google
    try {
      await clickByVisibleText(page, ["Sign in with Google", "Iniciar sesión con Google", "Google"]);
      await optionalGoogleAccountSelection(page);
      await validateContainsVisibleText(page, "Login validation", ["Negocio"]);
      const sidebarVisible =
        (await page.locator("aside").first().isVisible().catch(() => false)) ||
        (await page.getByText("Negocio").first().isVisible().catch(() => false));
      if (!sidebarVisible) {
        throw new Error("Left sidebar navigation is not visible after login.");
      }
      await captureCheckpoint(page, "dashboard-loaded");
      markStep("Login", { status: "PASS", details: "Main interface and sidebar are visible." });
    } catch (error) {
      markStep("Login", { status: "FAIL", details: String(error.message || error) });
    }

    const loginPassed = report.steps.Login?.status === "PASS";
    if (!loginPassed) {
      const skipReason = "Skipped because Login step failed.";
      [
        "Mi Negocio menu",
        "Agregar Negocio modal",
        "Administrar Negocios view",
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Términos y Condiciones",
        "Política de Privacidad"
      ].forEach((key) => markStep(key, { status: "FAIL", details: skipReason }));
      return;
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByVisibleText(page, ["Negocio"]);
      await clickByVisibleText(page, ["Mi Negocio"]);
      await validateTextsVisible(page, "Mi Negocio menu", ["Agregar Negocio", "Administrar Negocios"]);
      await captureCheckpoint(page, "mi-negocio-menu-expanded");
      markStep("Mi Negocio menu", { status: "PASS", details: "Submenu expanded and options visible." });
    } catch (error) {
      markStep("Mi Negocio menu", { status: "FAIL", details: String(error.message || error) });
    }

    const miNegocioPassed = report.steps["Mi Negocio menu"]?.status === "PASS";
    if (!miNegocioPassed) {
      const skipReason = "Skipped because Mi Negocio menu validation failed.";
      [
        "Agregar Negocio modal",
        "Administrar Negocios view",
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Términos y Condiciones",
        "Política de Privacidad"
      ].forEach((key) => markStep(key, { status: "FAIL", details: skipReason }));
      return;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, ["Agregar Negocio"]);
      await validateTextsVisible(page, "Agregar Negocio modal", [
        "Crear Nuevo Negocio",
        "Nombre del Negocio",
        "Tienes 2 de 3 negocios",
        "Cancelar",
        "Crear Negocio"
      ]);

      const inputField = page.getByLabel("Nombre del Negocio");
      const labelVisible = await inputField.first().isVisible().catch(() => false);
      if (!labelVisible) {
        const placeholderVisible = await page
          .getByPlaceholder("Nombre del Negocio")
          .first()
          .isVisible()
          .catch(() => false);
        if (!placeholderVisible) {
          throw new Error("Input field 'Nombre del Negocio' was not found.");
        }
      }

      const modalInput = (await inputField.first().isVisible().catch(() => false))
        ? inputField.first()
        : page.getByPlaceholder("Nombre del Negocio").first();
      await modalInput.click();
      await modalInput.fill("Negocio Prueba Automatización");
      await captureCheckpoint(page, "agregar-negocio-modal");
      await clickByVisibleText(page, ["Cancelar"]);
      markStep("Agregar Negocio modal", {
        status: "PASS",
        details: "Modal fields and actions were validated."
      });
    } catch (error) {
      markStep("Agregar Negocio modal", { status: "FAIL", details: String(error.message || error) });
    }

    // Step 4: Open Administrar Negocios
    try {
      const administrarVisible = await page.getByText("Administrar Negocios", { exact: true }).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        await clickByVisibleText(page, ["Mi Negocio"]);
      }
      await clickByVisibleText(page, ["Administrar Negocios"]);
      await validateContainsVisibleText(page, "Administrar Negocios view", [
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Sección Legal"
      ]);
      await captureCheckpoint(page, "administrar-negocios-page", true);
      markStep("Administrar Negocios view", { status: "PASS", details: "Account sections loaded." });
    } catch (error) {
      markStep("Administrar Negocios view", { status: "FAIL", details: String(error.message || error) });
    }

    const adminPagePassed = report.steps["Administrar Negocios view"]?.status === "PASS";
    if (!adminPagePassed) {
      const skipReason = "Skipped because Administrar Negocios page failed to load.";
      [
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Términos y Condiciones",
        "Política de Privacidad"
      ].forEach((key) => markStep(key, { status: "FAIL", details: skipReason }));
      return;
    }

    // Step 5: Validate Información General
    try {
      const hasAnyName =
        (await page.locator("text=/[A-Za-zÁÉÍÓÚáéíóúñÑ ]{4,}/").first().isVisible().catch(() => false)) ||
        (await page.locator("h1, h2, h3").first().isVisible().catch(() => false));
      const hasAnyEmail = await page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i").first().isVisible().catch(() => false);
      if (!hasAnyName) {
        throw new Error("User name was not found in Información General.");
      }
      if (!hasAnyEmail) {
        throw new Error("User email was not found in Información General.");
      }
      await validateContainsVisibleText(page, "Información General", ["BUSINESS PLAN", "Cambiar Plan"]);
      markStep("Información General", { status: "PASS", details: "General profile and plan details are visible." });
    } catch (error) {
      markStep("Información General", { status: "FAIL", details: String(error.message || error) });
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await validateContainsVisibleText(page, "Detalles de la Cuenta", [
        "Cuenta creada",
        "Estado activo",
        "Idioma seleccionado"
      ]);
      markStep("Detalles de la Cuenta", { status: "PASS", details: "Account details labels are visible." });
    } catch (error) {
      markStep("Detalles de la Cuenta", { status: "FAIL", details: String(error.message || error) });
    }

    // Step 7: Validate Tus Negocios
    try {
      await validateContainsVisibleText(page, "Tus Negocios", [
        "Tus Negocios",
        "Agregar Negocio",
        "Tienes 2 de 3 negocios"
      ]);
      const listVisible =
        (await page.locator("ul, table").first().isVisible().catch(() => false)) ||
        (await page.locator("[role='list'], [role='table']").first().isVisible().catch(() => false));
      if (!listVisible) {
        throw new Error("Business list is not visible.");
      }
      markStep("Tus Negocios", { status: "PASS", details: "Business section and controls validated." });
    } catch (error) {
      markStep("Tus Negocios", { status: "FAIL", details: String(error.message || error) });
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const legalContext = await openLegalDocumentFromClick(
        page,
        ["Términos y Condiciones", "Terminos y Condiciones"]
      );
      await validateContainsVisibleText(legalContext.legalPage, "Términos y Condiciones", ["Términos y Condiciones"]);
      const legalTextVisible =
        (await legalContext.legalPage.locator("p").first().isVisible().catch(() => false)) ||
        (await legalContext.legalPage.locator("article").first().isVisible().catch(() => false));
      if (!legalTextVisible) {
        throw new Error("Legal content text is not visible in Términos y Condiciones.");
      }
      await captureCheckpoint(legalContext.legalPage, "terminos-y-condiciones-page");
      report.evidence.urls["terminos-y-condiciones"] = legalContext.url;
      await legalContext.cleanup();
      markStep("Términos y Condiciones", {
        status: "PASS",
        details: "Legal page heading/content validated and URL captured."
      });
    } catch (error) {
      markStep("Términos y Condiciones", { status: "FAIL", details: String(error.message || error) });
    }

    // Step 9: Validate Política de Privacidad
    try {
      const legalContext = await openLegalDocumentFromClick(
        page,
        ["Política de Privacidad", "Politica de Privacidad"]
      );
      await validateContainsVisibleText(legalContext.legalPage, "Política de Privacidad", ["Política de Privacidad"]);
      const legalTextVisible =
        (await legalContext.legalPage.locator("p").first().isVisible().catch(() => false)) ||
        (await legalContext.legalPage.locator("article").first().isVisible().catch(() => false));
      if (!legalTextVisible) {
        throw new Error("Legal content text is not visible in Política de Privacidad.");
      }
      await captureCheckpoint(legalContext.legalPage, "politica-de-privacidad-page");
      report.evidence.urls["politica-de-privacidad"] = legalContext.url;
      await legalContext.cleanup();
      markStep("Política de Privacidad", {
        status: "PASS",
        details: "Legal page heading/content validated and URL captured."
      });
    } catch (error) {
      markStep("Política de Privacidad", { status: "FAIL", details: String(error.message || error) });
    }
  } catch (fatalError) {
    report.notes.push(`Fatal execution error: ${String(fatalError.message || fatalError)}`);
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
    const keys = [
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
    const hasFailure = keys.some((key) => report.steps[key]?.status !== "PASS");
    report.finalStatus = hasFailure ? "FAIL" : "PASS";
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf-8");
    process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
    process.exitCode = report.finalStatus === "PASS" ? 0 : 1;
  }
}

run();
