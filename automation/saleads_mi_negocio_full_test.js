#!/usr/bin/env node

/**
 * SaleADS Mi Negocio full workflow validation.
 *
 * Run examples:
 *   SALEADS_LOGIN_URL="https://<env-host>/login" npx -y -p playwright node automation/saleads_mi_negocio_full_test.js
 *   BROWSER_CDP_URL="http://127.0.0.1:9222" npx -y -p playwright node automation/saleads_mi_negocio_full_test.js
 */

const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL || "";
const BROWSER_CDP_URL = process.env.BROWSER_CDP_URL || "";
const HEADLESS = process.env.HEADLESS !== "false";
const STEP_TIMEOUT = Number(process.env.STEP_TIMEOUT_MS || 30000);

const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir =
  process.env.ARTIFACTS_DIR ||
  path.join(process.cwd(), "artifacts", `${TEST_NAME}_${timestamp}`);

const report = {
  testName: TEST_NAME,
  triggeredAt: new Date().toISOString(),
  config: {
    headless: HEADLESS,
    saleadsLoginUrlConfigured: Boolean(SALEADS_LOGIN_URL),
    browserCdpUrlConfigured: Boolean(BROWSER_CDP_URL),
    googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
    artifactsDir,
  },
  evidence: {
    screenshots: [],
    urls: {},
  },
  steps: {
    Login: { status: "FAIL", details: [] },
    "Mi Negocio menu": { status: "FAIL", details: [] },
    "Agregar Negocio modal": { status: "FAIL", details: [] },
    "Administrar Negocios view": { status: "FAIL", details: [] },
    "Información General": { status: "FAIL", details: [] },
    "Detalles de la Cuenta": { status: "FAIL", details: [] },
    "Tus Negocios": { status: "FAIL", details: [] },
    "Términos y Condiciones": { status: "FAIL", details: [] },
    "Política de Privacidad": { status: "FAIL", details: [] },
  },
};

let screenshotIndex = 1;
function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function setStepStatus(stepName, status, detail) {
  report.steps[stepName].status = status;
  if (detail) {
    report.steps[stepName].details.push(detail);
  }
}

function addStepDetail(stepName, detail) {
  if (detail) {
    report.steps[stepName].details.push(detail);
  }
}

async function waitForUi(page) {
  await page.waitForTimeout(350);
  try {
    await page.waitForLoadState("domcontentloaded", { timeout: 10000 });
  } catch (_) {
    // Keep progressing even if SPA doesn't emit this state.
  }
  try {
    await page.waitForLoadState("networkidle", { timeout: 15000 });
  } catch (_) {
    // Some apps have persistent connections and never go network-idle.
  }
}

async function checkpoint(page, label, options = {}) {
  const safeLabel = label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
  const file = `${String(screenshotIndex).padStart(2, "0")}_${safeLabel}.png`;
  screenshotIndex += 1;
  const fullPath = path.join(artifactsDir, file);
  await page.screenshot({ path: fullPath, fullPage: Boolean(options.fullPage) });
  report.evidence.screenshots.push(fullPath);
  return fullPath;
}

async function clickVisibleText(page, texts, clickOptions = {}) {
  const textList = Array.isArray(texts) ? texts : [texts];

  for (const text of textList) {
    const exactButton = page.getByRole("button", { name: text, exact: true });
    if ((await exactButton.count()) > 0 && (await exactButton.first().isVisible())) {
      await exactButton.first().click(clickOptions);
      await waitForUi(page);
      return text;
    }

    const exactLink = page.getByRole("link", { name: text, exact: true });
    if ((await exactLink.count()) > 0 && (await exactLink.first().isVisible())) {
      await exactLink.first().click(clickOptions);
      await waitForUi(page);
      return text;
    }

    const partial = page.getByText(text, { exact: false });
    if ((await partial.count()) > 0 && (await partial.first().isVisible())) {
      await partial.first().click(clickOptions);
      await waitForUi(page);
      return text;
    }
  }

  throw new Error(`No visible clickable element found for text(s): ${textList.join(", ")}`);
}

async function expectVisibleText(page, texts) {
  const textList = Array.isArray(texts) ? texts : [texts];
  for (const text of textList) {
    if (text instanceof RegExp) {
      const regexMatch = page.getByText(text);
      if ((await regexMatch.count()) > 0 && (await regexMatch.first().isVisible())) {
        return String(text);
      }
      continue;
    }

    const exact = page.getByText(text, { exact: true });
    if ((await exact.count()) > 0 && (await exact.first().isVisible())) {
      return text;
    }
    const partial = page.getByText(text, { exact: false });
    if ((await partial.count()) > 0 && (await partial.first().isVisible())) {
      return text;
    }
  }
  throw new Error(`Expected visible text not found: ${textList.join(", ")}`);
}

async function isVisibleText(page, text) {
  if (text instanceof RegExp) {
    const regexMatch = page.getByText(text);
    return (await regexMatch.count()) > 0 && (await regexMatch.first().isVisible());
  }
  const exact = page.getByText(text, { exact: true });
  if ((await exact.count()) > 0 && (await exact.first().isVisible())) {
    return true;
  }
  const partial = page.getByText(text, { exact: false });
  return (await partial.count()) > 0 && (await partial.first().isVisible());
}

async function tryClickGoogleAccount(page) {
  const accountByText = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
  if ((await accountByText.count()) > 0 && (await accountByText.first().isVisible())) {
    await accountByText.first().click();
    await waitForUi(page);
    return true;
  }
  return false;
}

async function maybeExpandMiNegocio(page) {
  const submenuAgregar = page.getByText("Agregar Negocio", { exact: false });
  const submenuAdministrar = page.getByText("Administrar Negocios", { exact: false });
  const submenuVisible =
    ((await submenuAgregar.count()) > 0 && (await submenuAgregar.first().isVisible())) ||
    ((await submenuAdministrar.count()) > 0 && (await submenuAdministrar.first().isVisible()));

  if (submenuVisible) {
    return;
  }

  try {
    await clickVisibleText(page, ["Mi Negocio", "Negocio"]);
  } catch (_) {
    // Keep fallback below.
  }
}

async function clickLegalAndValidate(page, context, linkText, headingText, urlKey, screenshotLabel) {
  const existingPages = context.pages();
  let legalPage = page;
  let openedNewTab = false;

  const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
  await clickVisibleText(page, linkText);
  const popup = await popupPromise;

  if (popup) {
    legalPage = popup;
    openedNewTab = true;
    await legalPage.waitForLoadState("domcontentloaded", { timeout: STEP_TIMEOUT });
    await waitForUi(legalPage);
  } else {
    await waitForUi(page);
  }

  await expectVisibleText(legalPage, headingText);
  report.evidence.urls[urlKey] = legalPage.url();
  await checkpoint(legalPage, screenshotLabel, { fullPage: true });

  if (openedNewTab) {
    await legalPage.close();
    const appPage = existingPages[0] || context.pages()[0];
    if (appPage && !appPage.isClosed()) {
      await appPage.bringToFront();
      await waitForUi(appPage);
    }
  } else {
    await legalPage.goBack({ timeout: STEP_TIMEOUT }).catch(() => {});
    await waitForUi(legalPage);
  }
}

function writeReport() {
  ensureDir(artifactsDir);
  const jsonPath = path.join(artifactsDir, "report.json");
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2), "utf8");

  const lines = [];
  lines.push(`# ${TEST_NAME}`);
  lines.push("");
  lines.push(`- Triggered at: ${report.triggeredAt}`);
  lines.push(`- Artifacts: ${artifactsDir}`);
  lines.push("");
  lines.push("| Field | Status |");
  lines.push("|---|---|");
  for (const [field, value] of Object.entries(report.steps)) {
    lines.push(`| ${field} | ${value.status} |`);
  }
  lines.push("");
  lines.push("## URLs");
  lines.push("");
  lines.push(`- Términos y Condiciones: ${report.evidence.urls.terms || "N/A"}`);
  lines.push(`- Política de Privacidad: ${report.evidence.urls.privacy || "N/A"}`);
  lines.push("");
  lines.push("## Screenshots");
  lines.push("");
  for (const shot of report.evidence.screenshots) {
    lines.push(`- ${shot}`);
  }

  const mdPath = path.join(artifactsDir, "report.md");
  fs.writeFileSync(mdPath, `${lines.join("\n")}\n`, "utf8");
  return { jsonPath, mdPath };
}

async function run() {
  ensureDir(artifactsDir);

  let browser = null;
  let context = null;
  let page = null;

  try {
    if (BROWSER_CDP_URL) {
      browser = await chromium.connectOverCDP(BROWSER_CDP_URL);
      context = browser.contexts()[0] || (await browser.newContext());
      page = context.pages()[0] || (await context.newPage());
    } else {
      browser = await chromium.launch({ headless: HEADLESS });
      context = await browser.newContext({ viewport: { width: 1440, height: 1024 } });
      page = await context.newPage();
    }

    // Step 1 - Login with Google.
    try {
      if (SALEADS_LOGIN_URL) {
        await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded", timeout: STEP_TIMEOUT });
        await waitForUi(page);
      } else if (!BROWSER_CDP_URL) {
        throw new Error("Set SALEADS_LOGIN_URL or BROWSER_CDP_URL to start from the login page.");
      }

      await clickVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Google",
      ]);

      await tryClickGoogleAccount(page);
      await waitForUi(page);

      const appIndicators = [
        "Mi Negocio",
        "Agregar Negocio",
        "Administrar Negocios",
        "Información General",
        "Tus Negocios",
      ];
      let indicatorVisible = false;
      for (const indicator of appIndicators) {
        if (await isVisibleText(page, indicator)) {
          indicatorVisible = true;
          break;
        }
      }

      const sidebarSelectors = ["aside", "nav[aria-label*='sidebar' i]", "[class*='sidebar' i]"];
      let sidebarVisible = false;
      for (const selector of sidebarSelectors) {
        const locator = page.locator(selector);
        if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
          sidebarVisible = true;
          break;
        }
      }

      const mainInterfaceVisible = indicatorVisible && sidebarVisible;

      if (!mainInterfaceVisible) {
        throw new Error("Main application interface with left sidebar not confirmed after login.");
      }

      await checkpoint(page, "dashboard_loaded");
      setStepStatus("Login", "PASS", "Dashboard and left navigation are visible.");
    } catch (error) {
      addStepDetail("Login", String(error.message || error));
      await checkpoint(page, "login_failure").catch(() => {});
      throw error;
    }

    // Step 2 - Open Mi Negocio menu.
    try {
      await clickVisibleText(page, ["Negocio", "Mi Negocio"]);
      await waitForUi(page);
      await expectVisibleText(page, "Agregar Negocio");
      await expectVisibleText(page, "Administrar Negocios");
      await checkpoint(page, "mi_negocio_menu_expanded");
      setStepStatus("Mi Negocio menu", "PASS", "Submenu expanded and required options are visible.");
    } catch (error) {
      setStepStatus("Mi Negocio menu", "FAIL", String(error.message || error));
    }

    // Step 3 - Validate Agregar Negocio modal.
    try {
      await clickVisibleText(page, "Agregar Negocio");
      await expectVisibleText(page, "Crear Nuevo Negocio");
      await expectVisibleText(page, "Nombre del Negocio");
      await expectVisibleText(page, "Tienes 2 de 3 negocios");
      await expectVisibleText(page, "Cancelar");
      await expectVisibleText(page, "Crear Negocio");
      await checkpoint(page, "agregar_negocio_modal");

      const nameInput = page.getByLabel("Nombre del Negocio", { exact: false });
      if ((await nameInput.count()) > 0) {
        await nameInput.first().click();
        await nameInput.first().fill("Negocio Prueba Automatización");
      } else {
        const fallbackInput = page.getByPlaceholder("Nombre del Negocio", { exact: false });
        if ((await fallbackInput.count()) > 0) {
          await fallbackInput.first().click();
          await fallbackInput.first().fill("Negocio Prueba Automatización");
        }
      }

      await clickVisibleText(page, "Cancelar");
      setStepStatus("Agregar Negocio modal", "PASS", "Modal validated and closed with Cancelar.");
    } catch (error) {
      setStepStatus("Agregar Negocio modal", "FAIL", String(error.message || error));
    }

    // Step 4 - Open Administrar Negocios.
    try {
      await maybeExpandMiNegocio(page);
      await clickVisibleText(page, "Administrar Negocios");
      await waitForUi(page);
      await expectVisibleText(page, "Información General");
      await expectVisibleText(page, "Detalles de la Cuenta");
      await expectVisibleText(page, "Tus Negocios");
      await expectVisibleText(page, "Sección Legal");
      await checkpoint(page, "administrar_negocios_view", { fullPage: true });
      setStepStatus("Administrar Negocios view", "PASS", "All expected account sections are visible.");
    } catch (error) {
      setStepStatus("Administrar Negocios view", "FAIL", String(error.message || error));
    }

    // Step 5 - Validate Información General.
    try {
      await expectVisibleText(page, "Información General");
      await expectVisibleText(page, /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/);
      await expectVisibleText(page, ["BUSINESS PLAN", "Business Plan"]);
      await expectVisibleText(page, "Cambiar Plan");
      setStepStatus("Información General", "PASS", "Name/email, plan and Cambiar Plan are visible.");
    } catch (error) {
      setStepStatus("Información General", "FAIL", String(error.message || error));
    }

    // Step 6 - Validate Detalles de la Cuenta.
    try {
      await expectVisibleText(page, "Cuenta creada");
      await expectVisibleText(page, "Estado activo");
      await expectVisibleText(page, "Idioma seleccionado");
      setStepStatus("Detalles de la Cuenta", "PASS", "All required account detail labels are visible.");
    } catch (error) {
      setStepStatus("Detalles de la Cuenta", "FAIL", String(error.message || error));
    }

    // Step 7 - Validate Tus Negocios.
    try {
      await expectVisibleText(page, "Tus Negocios");
      await expectVisibleText(page, "Agregar Negocio");
      await expectVisibleText(page, "Tienes 2 de 3 negocios");
      setStepStatus("Tus Negocios", "PASS", "Business list area and limits are visible.");
    } catch (error) {
      setStepStatus("Tus Negocios", "FAIL", String(error.message || error));
    }

    // Step 8 - Validate Términos y Condiciones.
    try {
      await clickLegalAndValidate(
        page,
        context,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "terms",
        "terminos_y_condiciones"
      );
      setStepStatus("Términos y Condiciones", "PASS", "Legal page title/content validated.");
    } catch (error) {
      setStepStatus("Términos y Condiciones", "FAIL", String(error.message || error));
    }

    // Step 9 - Validate Política de Privacidad.
    try {
      await clickLegalAndValidate(
        page,
        context,
        "Política de Privacidad",
        "Política de Privacidad",
        "privacy",
        "politica_de_privacidad"
      );
      setStepStatus("Política de Privacidad", "PASS", "Legal page title/content validated.");
    } catch (error) {
      setStepStatus("Política de Privacidad", "FAIL", String(error.message || error));
    }
  } catch (fatalError) {
    addStepDetail("Login", `Fatal execution error: ${String(fatalError.message || fatalError)}`);
    for (const [stepName, stepData] of Object.entries(report.steps)) {
      if (stepName === "Login") {
        continue;
      }
      if (stepData.details.length === 0) {
        stepData.details.push("Not executed due to prerequisite failure in earlier steps.");
      }
    }
  } finally {
    if (page && !page.isClosed() && report.evidence.screenshots.length === 0) {
      await checkpoint(page, "final_state").catch(() => {});
    }
    const { jsonPath, mdPath } = writeReport();
    if (context) {
      await context.close().catch(() => {});
    }
    if (browser) {
      await browser.close().catch(() => {});
    }

    const failed = Object.values(report.steps).filter((step) => step.status !== "PASS");
    console.log(JSON.stringify(report.steps, null, 2));
    console.log(`Report JSON: ${jsonPath}`);
    console.log(`Report MD:   ${mdPath}`);
    process.exitCode = failed.length ? 1 : 0;
  }
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
