#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const ARTIFACTS_DIR = path.resolve(process.cwd(), "artifacts", TEST_NAME);
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "report.json");

const STEP_KEYS = [
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

function ensureArtifactsDirectories() {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

function slugify(text) {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function nowIso() {
  return new Date().toISOString();
}

function initReport() {
  const statusByStep = {};
  for (const key of STEP_KEYS) {
    statusByStep[key] = {
      status: "FAIL",
      details: [],
      validations: [],
      screenshots: [],
      url: null,
    };
  }

  return {
    name: TEST_NAME,
    startedAt: nowIso(),
    environment: {
      saleadsLoginUrl: process.env.SALEADS_LOGIN_URL || null,
      headless: process.env.HEADLESS !== "false",
    },
    statusByStep,
    endedAt: null,
  };
}

function addDetail(report, stepKey, message) {
  report.statusByStep[stepKey].details.push(message);
}

function addValidation(report, stepKey, name, passed) {
  report.statusByStep[stepKey].validations.push({ name, passed });
}

function markPass(report, stepKey) {
  report.statusByStep[stepKey].status = "PASS";
}

function markFail(report, stepKey, reason) {
  report.statusByStep[stepKey].status = "FAIL";
  if (reason) {
    addDetail(report, stepKey, reason);
  }
}

async function waitUiAfterAction(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(800);
}

function textRegex(text) {
  return new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i");
}

function containsTextRegex(text) {
  return new RegExp(escapeRegExp(text), "i");
}

function locatorCandidatesByText(page, text) {
  return [
    page.getByRole("button", { name: containsTextRegex(text) }).first(),
    page.getByRole("link", { name: containsTextRegex(text) }).first(),
    page.getByRole("menuitem", { name: containsTextRegex(text) }).first(),
    page.getByRole("tab", { name: containsTextRegex(text) }).first(),
    page.getByText(textRegex(text)).first(),
    page.getByText(containsTextRegex(text)).first(),
    page.locator(`text=${text}`).first(),
  ];
}

async function getFirstVisibleLocator(locators, timeoutMs = 3000) {
  const timeoutAt = Date.now() + timeoutMs;
  while (Date.now() < timeoutAt) {
    for (const locator of locators) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 120));
  }
  return null;
}

async function clickByVisibleText(page, labels, options = {}) {
  for (const label of labels) {
    const loc = await getFirstVisibleLocator(locatorCandidatesByText(page, label), options.timeoutMs || 3500);
    if (loc) {
      await loc.click({ timeout: options.clickTimeoutMs || 7000 });
      await waitUiAfterAction(page);
      return label;
    }
  }
  throw new Error(`No clickable visible element found for labels: ${labels.join(", ")}`);
}

async function isVisibleByText(page, text, timeoutMs = 7000) {
  const loc = await getFirstVisibleLocator(locatorCandidatesByText(page, text), timeoutMs);
  return Boolean(loc);
}

async function validateVisibleText(report, stepKey, page, label, timeoutMs = 9000) {
  const visible = await isVisibleByText(page, label, timeoutMs);
  addValidation(report, stepKey, `Visible: ${label}`, visible);
  return visible;
}

async function screenshot(report, stepKey, page, name, options = {}) {
  const filename = `${slugify(stepKey)}_${slugify(name)}_${Date.now()}.png`;
  const fullPath = path.join(SCREENSHOTS_DIR, filename);
  await page.screenshot({
    path: fullPath,
    fullPage: Boolean(options.fullPage),
  });
  report.statusByStep[stepKey].screenshots.push(fullPath);
}

async function failStep(report, stepKey, page, error) {
  const reason = error instanceof Error ? error.message : String(error);
  markFail(report, stepKey, reason);
  if (page && !page.isClosed()) {
    await screenshot(report, stepKey, page, "failure").catch(() => {});
  }
}

async function findAppPage(context, currentPage) {
  const pages = context.pages();
  const preferred = [currentPage, ...pages.filter((p) => p !== currentPage)];
  for (const p of preferred) {
    if (p.isClosed()) continue;
    const hasSidebar = await p.locator("aside, nav").first().isVisible().catch(() => false);
    const hasNegocio = await isVisibleByText(p, "Negocio", 1000).catch(() => false);
    if (hasSidebar || hasNegocio) {
      return p;
    }
  }
  return currentPage;
}

async function chooseGoogleAccountIfShown(report, page) {
  const email = "juanlucasbarbiergarzon@gmail.com";
  const accountVisible = await isVisibleByText(page, email, 4000);
  if (!accountVisible) {
    return false;
  }

  await clickByVisibleText(page, [email], { timeoutMs: 5000, clickTimeoutMs: 7000 });
  await waitUiAfterAction(page);
  addDetail(report, "Login", `Selected Google account: ${email}`);
  return true;
}

async function run() {
  ensureArtifactsDirectories();
  const report = initReport();
  let browser = null;
  let context = null;
  let page = null;
  const loginUrl = process.env.SALEADS_LOGIN_URL;

  try {
    if (!loginUrl) {
      throw new Error(
        "SALEADS_LOGIN_URL is required for this automation run. It is intentionally not hardcoded to keep the test environment-agnostic."
      );
    }

    browser = await chromium.launch({
      headless: process.env.HEADLESS !== "false",
    });

    context = await browser.newContext({
      viewport: { width: 1600, height: 1000 },
    });
    page = await context.newPage();

    await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await waitUiAfterAction(page);

    // Step 1: Login with Google
    try {
      const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      const clickedLabel = await clickByVisibleText(page, [
        "Iniciar sesión",
        "Iniciar sesion",
        "Login",
        "Sign in",
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Iniciar sesion con Google",
        "Continuar con Google",
        "Login with Google",
      ]);
      addDetail(report, "Login", `Clicked login entrypoint: ${clickedLabel}`);

      // Some environments expose Google only after opening auth/login page.
      const googleButtonVisible = await isVisibleByText(page, "Google", 4000);
      if (googleButtonVisible && !/google/i.test(clickedLabel)) {
        await clickByVisibleText(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Iniciar sesion con Google",
          "Continuar con Google",
          "Google",
        ]);
      }

      let authPage = (await popupPromise) || page;

      await chooseGoogleAccountIfShown(report, authPage).catch(() => false);

      if (authPage !== page && !authPage.isClosed()) {
        await authPage.waitForLoadState("domcontentloaded").catch(() => {});
      }

      page = await findAppPage(context, page);
      await page.bringToFront();
      await waitUiAfterAction(page);

      const appVisible = await validateVisibleText(report, "Login", page, "Negocio", 12000);
      const sidebarVisible = await page.locator("aside, nav").first().isVisible().catch(() => false);
      addValidation(report, "Login", "Left sidebar is visible", sidebarVisible);

      if (!appVisible || !sidebarVisible) {
        throw new Error("Main application interface did not load after Google login.");
      }

      await screenshot(report, "Login", page, "dashboard_loaded");
      markPass(report, "Login");
    } catch (error) {
      await failStep(report, "Login", page, error);
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
      const submenuExpanded = (await isVisibleByText(page, "Agregar Negocio", 7000))
        && (await isVisibleByText(page, "Administrar Negocios", 7000));
      addValidation(report, "Mi Negocio menu", "Submenu expanded", submenuExpanded);
      addValidation(
        report,
        "Mi Negocio menu",
        "Visible: Agregar Negocio",
        await isVisibleByText(page, "Agregar Negocio", 5000)
      );
      addValidation(
        report,
        "Mi Negocio menu",
        "Visible: Administrar Negocios",
        await isVisibleByText(page, "Administrar Negocios", 5000)
      );
      if (!submenuExpanded) {
        throw new Error("Mi Negocio submenu is not expanded with expected options.");
      }
      await screenshot(report, "Mi Negocio menu", page, "expanded_menu");
      markPass(report, "Mi Negocio menu");
    } catch (error) {
      await failStep(report, "Mi Negocio menu", page, error);
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, ["Agregar Negocio"]);
      const modalTitle = await validateVisibleText(report, "Agregar Negocio modal", page, "Crear Nuevo Negocio");
      const inputVisible = await page.getByLabel(containsTextRegex("Nombre del Negocio")).first().isVisible().catch(async () => {
        const byText = await page.getByPlaceholder(containsTextRegex("Nombre del Negocio")).first().isVisible().catch(() => false);
        return byText;
      });
      addValidation(report, "Agregar Negocio modal", "Input: Nombre del Negocio exists", inputVisible);

      const capacityText = await validateVisibleText(report, "Agregar Negocio modal", page, "Tienes 2 de 3 negocios");
      const cancelVisible = await validateVisibleText(report, "Agregar Negocio modal", page, "Cancelar");
      const createVisible = await validateVisibleText(report, "Agregar Negocio modal", page, "Crear Negocio");

      if (!(modalTitle && inputVisible && capacityText && cancelVisible && createVisible)) {
        throw new Error("Agregar Negocio modal does not contain all expected elements.");
      }

      const nameField = page
        .getByLabel(containsTextRegex("Nombre del Negocio"))
        .first();
      if (await nameField.isVisible().catch(() => false)) {
        await nameField.click();
        await nameField.fill("Negocio Prueba Automatizacion");
      }
      await screenshot(report, "Agregar Negocio modal", page, "modal_validation");
      await clickByVisibleText(page, ["Cancelar"]);
      markPass(report, "Agregar Negocio modal");
    } catch (error) {
      await failStep(report, "Agregar Negocio modal", page, error);
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      if (!(await isVisibleByText(page, "Administrar Negocios", 2500))) {
        await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
      }
      await clickByVisibleText(page, ["Administrar Negocios"]);

      const infoGeneral = await validateVisibleText(report, "Administrar Negocios view", page, "Información General");
      const detalles = await validateVisibleText(report, "Administrar Negocios view", page, "Detalles de la Cuenta");
      const tusNegocios = await validateVisibleText(report, "Administrar Negocios view", page, "Tus Negocios");
      const legal = await validateVisibleText(report, "Administrar Negocios view", page, "Sección Legal");

      if (!(infoGeneral && detalles && tusNegocios && legal)) {
        throw new Error("Administrar Negocios page is missing required sections.");
      }

      await screenshot(report, "Administrar Negocios view", page, "account_page", { fullPage: true });
      markPass(report, "Administrar Negocios view");
    } catch (error) {
      await failStep(report, "Administrar Negocios view", page, error);
      throw error;
    }

    // Step 5: Validate Información General
    try {
      const userNameVisible = await page.locator("section, div").filter({ hasText: /@|BUSINESS PLAN|Cambiar Plan/i }).first().isVisible().catch(() => false);
      addValidation(report, "Información General", "User name visible", userNameVisible);
      addValidation(report, "Información General", "User email visible", await page.getByText(/@/).first().isVisible().catch(() => false));
      addValidation(report, "Información General", "Visible: BUSINESS PLAN", await isVisibleByText(page, "BUSINESS PLAN", 5000));
      addValidation(report, "Información General", "Visible: Cambiar Plan", await isVisibleByText(page, "Cambiar Plan", 5000));

      const allPassed = report.statusByStep["Información General"].validations.every((v) => v.passed);
      if (!allPassed) {
        throw new Error("Información General validations failed.");
      }
      markPass(report, "Información General");
    } catch (error) {
      await failStep(report, "Información General", page, error);
      throw error;
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      const created = await validateVisibleText(report, "Detalles de la Cuenta", page, "Cuenta creada");
      const active = await validateVisibleText(report, "Detalles de la Cuenta", page, "Estado activo");
      const language = await validateVisibleText(report, "Detalles de la Cuenta", page, "Idioma seleccionado");
      if (!(created && active && language)) {
        throw new Error("Detalles de la Cuenta validations failed.");
      }
      markPass(report, "Detalles de la Cuenta");
    } catch (error) {
      await failStep(report, "Detalles de la Cuenta", page, error);
      throw error;
    }

    // Step 7: Validate Tus Negocios
    try {
      const listVisible = await page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first().isVisible().catch(() => false);
      addValidation(report, "Tus Negocios", "Business list visible", listVisible);
      addValidation(report, "Tus Negocios", "Visible: Agregar Negocio", await isVisibleByText(page, "Agregar Negocio", 5000));
      addValidation(report, "Tus Negocios", "Visible: Tienes 2 de 3 negocios", await isVisibleByText(page, "Tienes 2 de 3 negocios", 5000));
      const allPassed = report.statusByStep["Tus Negocios"].validations.every((v) => v.passed);
      if (!allPassed) {
        throw new Error("Tus Negocios validations failed.");
      }
      markPass(report, "Tus Negocios");
    } catch (error) {
      await failStep(report, "Tus Negocios", page, error);
      throw error;
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const originalAppPage = page;
      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickByVisibleText(page, ["Términos y Condiciones", "Terminos y Condiciones"]);
      let legalPage = await popupPromise;
      let openedNewTab = true;

      if (!legalPage) {
        openedNewTab = false;
        legalPage = page;
      }

      if (openedNewTab) {
        await legalPage.waitForLoadState("domcontentloaded").catch(() => {});
      } else {
        await waitUiAfterAction(legalPage);
      }

      const headingOk =
        (await isVisibleByText(legalPage, "Términos y Condiciones", 8000))
        || (await isVisibleByText(legalPage, "Terminos y Condiciones", 8000));

      const legalTextVisible = await legalPage.locator("body").innerText().then((t) => t.trim().length > 120).catch(() => false);
      addValidation(report, "Términos y Condiciones", "Heading visible", headingOk);
      addValidation(report, "Términos y Condiciones", "Legal content visible", legalTextVisible);

      await screenshot(report, "Términos y Condiciones", legalPage, "legal_page");
      report.statusByStep["Términos y Condiciones"].url = legalPage.url();

      if (!(headingOk && legalTextVisible)) {
        throw new Error("Términos y Condiciones validation failed.");
      }

      if (openedNewTab) {
        await legalPage.close().catch(() => {});
        page = originalAppPage;
        await page.bringToFront();
      } else if (legalPage.url() !== originalAppPage.url()) {
        await page.goBack().catch(() => {});
      }

      await waitUiAfterAction(page);
      markPass(report, "Términos y Condiciones");
    } catch (error) {
      await failStep(report, "Términos y Condiciones", page, error);
      throw error;
    }

    // Step 9: Validate Política de Privacidad
    try {
      const originalAppPage = page;
      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickByVisibleText(page, ["Política de Privacidad", "Politica de Privacidad"]);
      let legalPage = await popupPromise;
      let openedNewTab = true;

      if (!legalPage) {
        openedNewTab = false;
        legalPage = page;
      }

      if (openedNewTab) {
        await legalPage.waitForLoadState("domcontentloaded").catch(() => {});
      } else {
        await waitUiAfterAction(legalPage);
      }

      const headingOk =
        (await isVisibleByText(legalPage, "Política de Privacidad", 8000))
        || (await isVisibleByText(legalPage, "Politica de Privacidad", 8000));

      const legalTextVisible = await legalPage.locator("body").innerText().then((t) => t.trim().length > 120).catch(() => false);
      addValidation(report, "Política de Privacidad", "Heading visible", headingOk);
      addValidation(report, "Política de Privacidad", "Legal content visible", legalTextVisible);

      await screenshot(report, "Política de Privacidad", legalPage, "legal_page");
      report.statusByStep["Política de Privacidad"].url = legalPage.url();

      if (!(headingOk && legalTextVisible)) {
        throw new Error("Política de Privacidad validation failed.");
      }

      if (openedNewTab) {
        await legalPage.close().catch(() => {});
        page = originalAppPage;
        await page.bringToFront();
      } else if (legalPage.url() !== originalAppPage.url()) {
        await page.goBack().catch(() => {});
      }

      await waitUiAfterAction(page);
      markPass(report, "Política de Privacidad");
    } catch (error) {
      await failStep(report, "Política de Privacidad", page, error);
      throw error;
    }
  } catch (fatalError) {
    let fatalAssigned = false;
    for (const key of STEP_KEYS) {
      if (report.statusByStep[key].status === "PASS") {
        continue;
      }

      if (!fatalAssigned && report.statusByStep[key].details.length === 0) {
        addDetail(report, key, `Execution stopped: ${fatalError.message}`);
        fatalAssigned = true;
      } else if (report.statusByStep[key].details.length === 0) {
        addDetail(report, key, "Not executed due to an earlier failure.");
      }
    }
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }

    report.endedAt = nowIso();
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
  }

  const printable = {};
  for (const key of STEP_KEYS) {
    printable[key] = report.statusByStep[key].status;
  }

  console.log("Final Report (PASS/FAIL per step):");
  console.table(printable);
  console.log(`Detailed report: ${REPORT_PATH}`);

  const hasFailures = Object.values(printable).some((status) => status !== "PASS");
  process.exit(hasFailures ? 1 : 0);
}

run().catch((error) => {
  console.error("Unhandled test error:", error);
  process.exit(1);
});
