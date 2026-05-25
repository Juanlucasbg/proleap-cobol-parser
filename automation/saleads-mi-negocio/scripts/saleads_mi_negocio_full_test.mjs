#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { chromium } from "playwright";

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT = process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL;
const HEADLESS = process.env.SALEADS_HEADLESS !== "false";
const WAIT_AFTER_CLICK_MS = Number.parseInt(process.env.SALEADS_WAIT_MS ?? "1200", 10);
const ARTIFACTS_DIR = path.resolve(
  process.env.SALEADS_ARTIFACTS_DIR ?? "./artifacts/saleads-mi-negocio",
);

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
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function slugify(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
    .toLowerCase();
}

function timestamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
}

function buildTextLocators(page, text) {
  const nameRegex = new RegExp(escapeRegExp(text), "i");
  return [
    page.getByRole("button", { name: nameRegex }).first(),
    page.getByRole("link", { name: nameRegex }).first(),
    page.getByRole("menuitem", { name: nameRegex }).first(),
    page.getByRole("tab", { name: nameRegex }).first(),
    page.getByText(nameRegex).first(),
  ];
}

async function findVisibleLocatorByTexts(page, texts, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const text of texts) {
      for (const locator of buildTextLocators(page, text)) {
        const visible = await locator.isVisible().catch(() => false);
        if (visible) {
          return { locator, matchedText: text };
        }
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`None of these texts were visible: ${texts.join(" | ")}`);
}

async function clickByTexts(page, texts, options = {}) {
  const { timeoutMs = 20000, waitAfterClick = true } = options;
  const { locator, matchedText } = await findVisibleLocatorByTexts(page, texts, timeoutMs);
  await locator.click();
  if (waitAfterClick) {
    await waitForUi(page);
  }
  return matchedText;
}

async function assertTextsVisible(page, texts, timeoutMs = 15000) {
  for (const text of texts) {
    const { locator } = await findVisibleLocatorByTexts(page, [text], timeoutMs);
    const visible = await locator.isVisible().catch(() => false);
    if (!visible) {
      throw new Error(`Expected visible text not found: ${text}`);
    }
  }
}

async function assertAnyTextVisible(page, texts, timeoutMs = 15000) {
  await findVisibleLocatorByTexts(page, texts, timeoutMs);
}

async function screenshot(page, label, options = {}) {
  const filename = `${timestamp()}-${slugify(label)}.png`;
  const outputPath = path.join(ARTIFACTS_DIR, filename);
  await page.screenshot({ path: outputPath, fullPage: options.fullPage ?? false });
  return outputPath;
}

function createResults() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: [], evidence: {} }]),
  );
}

function createReport() {
  return {
    name: TEST_NAME,
    started_at: new Date().toISOString(),
    finished_at: null,
    config: {
      login_url: LOGIN_URL ?? null,
      google_account: GOOGLE_ACCOUNT,
      headless: HEADLESS,
      wait_after_click_ms: WAIT_AFTER_CLICK_MS,
      artifacts_dir: ARTIFACTS_DIR,
    },
    results: createResults(),
    summary: {
      passed: 0,
      failed: 0,
      status: "FAIL",
    },
  };
}

function markStep(report, step, status, details, evidence = {}) {
  report.results[step] = {
    status,
    details: Array.isArray(details) ? details : [details],
    evidence,
  };
}

function computeSummary(report) {
  let passed = 0;
  let failed = 0;

  for (const field of REPORT_FIELDS) {
    if (report.results[field].status === "PASS") {
      passed += 1;
    } else {
      failed += 1;
    }
  }

  report.summary = {
    passed,
    failed,
    status: failed === 0 ? "PASS" : "FAIL",
  };
  report.finished_at = new Date().toISOString();
}

async function trySelectGoogleAccount(page) {
  const accountLocator = page.getByText(new RegExp(`^\\s*${escapeRegExp(GOOGLE_ACCOUNT)}\\s*$`, "i"));
  const visible = await accountLocator.first().isVisible().catch(() => false);
  if (visible) {
    await accountLocator.first().click();
    await waitForUi(page);
    return true;
  }

  return false;
}

async function openMiNegocioMenu(page) {
  const submenuTexts = ["Agregar Negocio", "Administrar Negocios"];
  const submenuReady = await isAnyTextVisible(page, submenuTexts, 2000);
  if (submenuReady) {
    return;
  }

  const menuAttempts = [
    ["Mi Negocio"],
    ["Negocio", "Mi Negocio"],
  ];

  for (const attempt of menuAttempts) {
    try {
      for (const text of attempt) {
        await clickByTexts(page, [text], { timeoutMs: 10000 });
      }
      const expanded = await isAnyTextVisible(page, submenuTexts, 5000);
      if (expanded) {
        return;
      }
    } catch {
      // Try the next menu strategy.
    }
  }

  throw new Error("Unable to expand Mi Negocio menu.");
}

async function isAnyTextVisible(page, texts, timeoutMs = 3000) {
  try {
    await assertAnyTextVisible(page, texts, timeoutMs);
    return true;
  } catch {
    return false;
  }
}

function hasNameLikeText(sectionText) {
  const lines = sectionText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => !/@/.test(line))
    .filter((line) => !/informaci[oó]n general|business plan|cambiar plan/i.test(line));

  return lines.some((line) => /^[A-Za-zÀ-ÿ' -]{4,}$/.test(line));
}

async function validateLegalLink(context, appPage, linkText, headingText, label) {
  const appUrlBeforeClick = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickByTexts(appPage, [linkText], { waitAfterClick: false });
  const popup = await popupPromise;

  const legalPage = popup ?? appPage;
  await waitForUi(legalPage);

  await assertAnyTextVisible(legalPage, [headingText], 25000);
  const bodyText = await legalPage.locator("body").innerText();
  if (bodyText.trim().length < 100) {
    throw new Error(`Legal page "${headingText}" loaded but visible content looks too short.`);
  }

  const capture = await screenshot(legalPage, label, { fullPage: true });
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== appUrlBeforeClick) {
    await appPage.goBack().catch(() => {});
    await waitForUi(appPage);
  }

  return { screenshot: capture, final_url: finalUrl };
}

async function findBusinessNameInput(page) {
  const labelLocator = page.getByLabel(/Nombre del Negocio/i).first();
  if (await labelLocator.isVisible().catch(() => false)) {
    return labelLocator;
  }

  const placeholderLocator = page.getByPlaceholder(/Nombre del Negocio/i).first();
  if (await placeholderLocator.isVisible().catch(() => false)) {
    return placeholderLocator;
  }

  const genericInput = page.locator("input").first();
  if (await genericInput.isVisible().catch(() => false)) {
    return genericInput;
  }

  throw new Error("Could not locate the input field 'Nombre del Negocio'.");
}

async function run() {
  const report = createReport();
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
  let runnerError = null;

  const browser = await chromium.launch({
    headless: HEADLESS,
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });
  const page = await context.newPage();

  try {
    if (!LOGIN_URL) {
      throw new Error(
        "SALEADS_LOGIN_URL is required in this runtime. No URL is hardcoded by design to support any environment.",
      );
    }

    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    // Step 1 - Login with Google.
    try {
      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickByTexts(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Inicia sesión con Google",
        "Continuar con Google",
        "Login with Google",
      ]);
      const popup = await popupPromise;
      const authPage = popup ?? page;
      await waitForUi(authPage);

      await trySelectGoogleAccount(authPage);

      if (popup) {
        await popup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
        await page.bringToFront();
      }

      await waitForUi(page);
      await assertAnyTextVisible(page, ["Mi Negocio", "Negocio"], 45000);

      const sidebarVisible =
        (await page.locator("aside").first().isVisible().catch(() => false)) ||
        (await page.getByRole("navigation").first().isVisible().catch(() => false));
      if (!sidebarVisible) {
        throw new Error("Main application loaded but sidebar navigation is not visible.");
      }

      const dashShot = await screenshot(page, "dashboard-loaded");
      markStep(
        report,
        "Login",
        "PASS",
        [
          "Main application interface is visible after Google sign-in.",
          "Left sidebar navigation is visible.",
        ],
        { screenshot: dashShot },
      );
    } catch (error) {
      const failShot = await screenshot(page, "login-failure").catch(() => null);
      markStep(report, "Login", "FAIL", error.message, { screenshot: failShot });
    }

    // Step 2 - Open Mi Negocio menu.
    try {
      await openMiNegocioMenu(page);
      await assertTextsVisible(page, ["Agregar Negocio", "Administrar Negocios"], 10000);
      const menuShot = await screenshot(page, "mi-negocio-menu-expanded");
      markStep(
        report,
        "Mi Negocio menu",
        "PASS",
        [
          "Mi Negocio submenu expanded successfully.",
          "'Agregar Negocio' and 'Administrar Negocios' are visible.",
        ],
        { screenshot: menuShot },
      );
    } catch (error) {
      const failShot = await screenshot(page, "mi-negocio-menu-failure").catch(() => null);
      markStep(report, "Mi Negocio menu", "FAIL", error.message, { screenshot: failShot });
    }

    // Step 3 - Validate Agregar Negocio modal.
    try {
      await openMiNegocioMenu(page);
      await clickByTexts(page, ["Agregar Negocio"]);
      await assertTextsVisible(
        page,
        ["Crear Nuevo Negocio", "Nombre del Negocio", "Tienes 2 de 3 negocios", "Cancelar", "Crear Negocio"],
        15000,
      );

      const input = await findBusinessNameInput(page);
      await input.click();
      await input.fill("Negocio Prueba Automatización");
      const modalShot = await screenshot(page, "agregar-negocio-modal");
      await clickByTexts(page, ["Cancelar"]);
      markStep(
        report,
        "Agregar Negocio modal",
        "PASS",
        [
          "Modal 'Crear Nuevo Negocio' opened with required input, quota, and action buttons.",
          "Typed sample business name and closed with 'Cancelar'.",
        ],
        { screenshot: modalShot },
      );
    } catch (error) {
      const failShot = await screenshot(page, "agregar-negocio-modal-failure").catch(() => null);
      markStep(report, "Agregar Negocio modal", "FAIL", error.message, { screenshot: failShot });
    }

    // Step 4 - Open Administrar Negocios.
    try {
      await openMiNegocioMenu(page);
      await clickByTexts(page, ["Administrar Negocios"]);
      await assertTextsVisible(
        page,
        ["Información General", "Detalles de la Cuenta", "Tus Negocios", "Sección Legal"],
        20000,
      );
      const accountShot = await screenshot(page, "administrar-negocios", { fullPage: true });
      markStep(
        report,
        "Administrar Negocios view",
        "PASS",
        [
          "Account page loaded with all expected sections.",
          "Información General, Detalles de la Cuenta, Tus Negocios, and Sección Legal are visible.",
        ],
        { screenshot: accountShot },
      );
    } catch (error) {
      const failShot = await screenshot(page, "administrar-negocios-failure").catch(() => null);
      markStep(report, "Administrar Negocios view", "FAIL", error.message, { screenshot: failShot });
    }

    // Step 5 - Validate Información General.
    try {
      await assertTextsVisible(page, ["Información General", "BUSINESS PLAN", "Cambiar Plan"], 12000);
      const infoGeneralText = await page.locator("body").innerText();
      const emailVisible = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoGeneralText);
      if (!emailVisible) {
        throw new Error("No user email address was detected on the page.");
      }
      if (!hasNameLikeText(infoGeneralText)) {
        throw new Error("No user name-like text was detected on the page.");
      }

      markStep(
        report,
        "Información General",
        "PASS",
        [
          "User name-like text is visible.",
          "User email is visible.",
          "BUSINESS PLAN and 'Cambiar Plan' are visible.",
        ],
      );
    } catch (error) {
      markStep(report, "Información General", "FAIL", error.message);
    }

    // Step 6 - Validate Detalles de la Cuenta.
    try {
      await assertTextsVisible(page, ["Cuenta creada", "Estado activo", "Idioma seleccionado"], 12000);
      markStep(
        report,
        "Detalles de la Cuenta",
        "PASS",
        [
          "'Cuenta creada' is visible.",
          "'Estado activo' is visible.",
          "'Idioma seleccionado' is visible.",
        ],
      );
    } catch (error) {
      markStep(report, "Detalles de la Cuenta", "FAIL", error.message);
    }

    // Step 7 - Validate Tus Negocios.
    try {
      await assertTextsVisible(page, ["Tus Negocios", "Agregar Negocio", "Tienes 2 de 3 negocios"], 12000);
      markStep(
        report,
        "Tus Negocios",
        "PASS",
        [
          "Business list section is visible.",
          "Agregar Negocio button exists.",
          "Business quota text is visible.",
        ],
      );
    } catch (error) {
      markStep(report, "Tus Negocios", "FAIL", error.message);
    }

    // Step 8 - Validate Términos y Condiciones.
    try {
      const legalResult = await validateLegalLink(
        context,
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "terminos-y-condiciones",
      );
      markStep(
        report,
        "Términos y Condiciones",
        "PASS",
        [
          "Legal page heading and body content are visible.",
          `Final URL: ${legalResult.final_url}`,
        ],
        legalResult,
      );
    } catch (error) {
      const failShot = await screenshot(page, "terminos-failure").catch(() => null);
      markStep(report, "Términos y Condiciones", "FAIL", error.message, { screenshot: failShot });
    }

    // Step 9 - Validate Política de Privacidad.
    try {
      const legalResult = await validateLegalLink(
        context,
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "politica-de-privacidad",
      );
      markStep(
        report,
        "Política de Privacidad",
        "PASS",
        [
          "Privacy page heading and body content are visible.",
          `Final URL: ${legalResult.final_url}`,
        ],
        legalResult,
      );
    } catch (error) {
      const failShot = await screenshot(page, "privacidad-failure").catch(() => null);
      markStep(report, "Política de Privacidad", "FAIL", error.message, { screenshot: failShot });
    }
  } catch (error) {
    runnerError = error;
    if (!report.results.Login.details.length) {
      markStep(report, "Login", "FAIL", error.message);
    }
  } finally {
    computeSummary(report);

    const reportPath = path.join(ARTIFACTS_DIR, `${timestamp()}-final-report.json`);
    await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

    console.log("=== SaleADS Mi Negocio Full Test Report ===");
    for (const field of REPORT_FIELDS) {
      console.log(`${field}: ${report.results[field].status}`);
    }
    console.log(`Summary: ${report.summary.status} (passed: ${report.summary.passed}, failed: ${report.summary.failed})`);
    console.log(`Artifacts directory: ${ARTIFACTS_DIR}`);
    console.log(`Report file: ${reportPath}`);
    if (runnerError) {
      console.log(`Runner setup/runtime error: ${runnerError.message}`);
    }

    await context.close().catch(() => {});
    await browser.close().catch(() => {});

    process.exitCode = report.summary.failed === 0 ? 0 : 1;
  }
}

run().catch((error) => {
  console.error("Fatal test runner error:", error);
  process.exit(1);
});
