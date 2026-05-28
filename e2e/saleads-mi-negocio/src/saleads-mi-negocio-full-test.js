#!/usr/bin/env node

const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");
require("dotenv").config({
  path: path.resolve(process.cwd(), ".env"),
});

const REQUIRED_REPORT_FIELDS = [
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

const UI_SETTLE_DELAY_MS = Number(process.env.UI_SETTLE_DELAY_MS || 1000);
const DEFAULT_TIMEOUT_MS = Number(process.env.DEFAULT_TIMEOUT_MS || 20000);
const HEADLESS = String(process.env.HEADLESS || "false").toLowerCase() === "true";
const LOGIN_URL =
  process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL || "";

function toStepResult(pass, details) {
  return {
    status: pass ? "PASS" : "FAIL",
    details,
  };
}

function sanitizeFileName(name) {
  return name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

function regexForText(label) {
  return new RegExp(label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

async function waitForUi(page) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 10000 }),
    page.waitForLoadState("networkidle", { timeout: 10000 }),
  ]);
  await page.waitForTimeout(UI_SETTLE_DELAY_MS);
}

async function isVisible(locator, timeout = 3000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function firstVisible(locators, timeout = 5000) {
  for (const locator of locators) {
    if (await isVisible(locator, timeout)) {
      return locator.first();
    }
  }
  return null;
}

async function clickByVisibleText(page, label, opts = {}) {
  const rx = regexForText(label);
  const locator = await firstVisible(
    [
      page.getByRole("button", { name: rx }),
      page.getByRole("link", { name: rx }),
      page.getByRole("menuitem", { name: rx }),
      page.getByRole("tab", { name: rx }),
      page.getByText(rx),
    ],
    opts.timeout || 5000,
  );

  if (!locator) {
    throw new Error(`Could not find clickable element with text "${label}".`);
  }

  await locator.click({ timeout: opts.clickTimeout || DEFAULT_TIMEOUT_MS });
  await waitForUi(page);
  return locator;
}

async function findAnyVisible(page, textOptions, timeout = 6000) {
  for (const option of textOptions) {
    const rx = regexForText(option);
    if (
      await isVisible(page.getByRole("heading", { name: rx }), timeout) ||
      (await isVisible(page.getByText(rx), timeout))
    ) {
      return true;
    }
  }
  return false;
}

async function captureScreenshot(page, dir, label, fullPage = false) {
  const file = path.join(dir, `${sanitizeFileName(label)}.png`);
  await page.screenshot({ path: file, fullPage });
  return file;
}

async function selectGoogleAccountIfVisible(targetPage, email) {
  const selectors = [
    targetPage.getByText(regexForText(email)),
    targetPage.locator(`[data-identifier="${email}"]`),
    targetPage.locator(`[data-email="${email}"]`),
  ];
  const account = await firstVisible(selectors, 8000);
  if (!account) {
    return false;
  }

  await account.click({ timeout: DEFAULT_TIMEOUT_MS });
  await waitForUi(targetPage);
  return true;
}

async function validateLegalPage({
  appPage,
  context,
  linkText,
  expectedHeading,
  screenshotsDir,
  fallbackBackUrl,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickByVisibleText(appPage, linkText, { timeout: 8000 });
  const popup = await popupPromise;
  const targetPage = popup || appPage;

  await targetPage.waitForLoadState("domcontentloaded", { timeout: DEFAULT_TIMEOUT_MS });
  await waitForUi(targetPage);

  const hasHeading = await firstVisible(
    [
      targetPage.getByRole("heading", { name: regexForText(expectedHeading) }),
      targetPage.getByText(regexForText(expectedHeading)),
    ],
    8000,
  );

  const legalParagraphVisible = await isVisible(targetPage.locator("p, article, main, section"), 8000);
  const screenshotPath = await captureScreenshot(targetPage, screenshotsDir, `${linkText}-page`, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
  } else {
    const backOk = await appPage.goBack({ timeout: 8000 }).catch(() => null);
    if (!backOk && fallbackBackUrl) {
      await appPage.goto(fallbackBackUrl, { waitUntil: "domcontentloaded", timeout: DEFAULT_TIMEOUT_MS });
    }
    await waitForUi(appPage);
  }

  return {
    pass: Boolean(hasHeading) && legalParagraphVisible,
    finalUrl,
    screenshotPath,
    headingFound: Boolean(hasHeading),
    legalContentFound: legalParagraphVisible,
  };
}

async function run() {
  if (!LOGIN_URL) {
    throw new Error(
      "Missing login URL. Provide SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL) for the target environment.",
    );
  }

  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.resolve(process.cwd(), "artifacts", runId);
  const screenshotsDir = path.join(artifactsDir, "screenshots");
  await fs.mkdir(screenshotsDir, { recursive: true });

  const report = {
    name: "saleads_mi_negocio_full_test",
    startedAt: new Date().toISOString(),
    environment: {
      loginUrl: LOGIN_URL,
      headless: HEADLESS,
    },
    results: Object.fromEntries(REQUIRED_REPORT_FIELDS.map((field) => [field, toStepResult(false, "Not executed")])),
    evidence: {
      screenshotsDir,
      legalUrls: {
        "Términos y Condiciones": "",
        "Política de Privacidad": "",
      },
    },
  };

  let browser;
  try {
    browser = await chromium.launch({
      headless: HEADLESS,
      slowMo: Number(process.env.SLOW_MO_MS || 150),
    });
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
    });
    const page = await context.newPage();
    page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded", timeout: DEFAULT_TIMEOUT_MS });
    await waitForUi(page);

    // Step 1: Login with Google
    let loginPass = false;
    try {
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickByVisibleText(page, "Sign in with Google", { timeout: 5000 }).catch(async () => {
        await clickByVisibleText(page, "Iniciar sesión con Google", { timeout: 5000 });
      });
      const popup = await popupPromise;

      const googlePage = popup || page;
      await waitForUi(googlePage);
      await selectGoogleAccountIfVisible(googlePage, "juanlucasbarbiergarzon@gmail.com");

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 12000 }).catch(() => {});
      }

      await waitForUi(page);
      const mainVisible = await firstVisible([page.locator("main"), page.locator("[role='main']")], 10000);
      const sidebarVisible = await firstVisible(
        [page.locator("aside"), page.getByText(regexForText("Negocio")), page.locator("nav")],
        10000,
      );
      loginPass = Boolean(mainVisible) && Boolean(sidebarVisible);
      await captureScreenshot(page, screenshotsDir, "dashboard-loaded", true);
      report.results.Login = toStepResult(
        loginPass,
        loginPass ? "Main interface and left sidebar detected." : "Main interface/sidebar not clearly detected after login.",
      );
    } catch (error) {
      report.results.Login = toStepResult(false, `Login flow failed: ${error.message}`);
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByVisibleText(page, "Negocio", { timeout: 6000 }).catch(async () => {
        // It may already be visible as expanded.
        await waitForUi(page);
      });
      await clickByVisibleText(page, "Mi Negocio", { timeout: 8000 });

      const agregarVisible = await isVisible(page.getByText(regexForText("Agregar Negocio")), 8000);
      const administrarVisible = await isVisible(page.getByText(regexForText("Administrar Negocios")), 8000);
      const menuPass = agregarVisible && administrarVisible;

      await captureScreenshot(page, screenshotsDir, "mi-negocio-expanded-menu");
      report.results["Mi Negocio menu"] = toStepResult(
        menuPass,
        menuPass
          ? "Mi Negocio expanded and required submenu options are visible."
          : "Mi Negocio submenu options were not fully visible.",
      );
    } catch (error) {
      report.results["Mi Negocio menu"] = toStepResult(false, `Could not expand Mi Negocio menu: ${error.message}`);
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, "Agregar Negocio", { timeout: 8000 });

      const modalTitle = await isVisible(page.getByText(regexForText("Crear Nuevo Negocio")), 8000);
      const businessNameInput = await firstVisible(
        [
          page.getByLabel(regexForText("Nombre del Negocio")),
          page.getByPlaceholder(regexForText("Nombre del Negocio")),
          page.locator("input").filter({ hasText: "" }),
        ],
        8000,
      );
      const quotaText = await isVisible(page.getByText(regexForText("Tienes 2 de 3 negocios")), 8000);
      const cancelarButton = await isVisible(page.getByRole("button", { name: regexForText("Cancelar") }), 8000);
      const crearButton = await isVisible(page.getByRole("button", { name: regexForText("Crear Negocio") }), 8000);

      if (businessNameInput) {
        await businessNameInput.fill("Negocio Prueba Automatización");
      }
      await captureScreenshot(page, screenshotsDir, "agregar-negocio-modal");

      if (cancelarButton) {
        await clickByVisibleText(page, "Cancelar", { timeout: 5000 });
      }

      const modalPass = modalTitle && Boolean(businessNameInput) && quotaText && cancelarButton && crearButton;
      report.results["Agregar Negocio modal"] = toStepResult(
        modalPass,
        modalPass
          ? "Agregar Negocio modal contains all expected elements."
          : "One or more modal elements were missing.",
      );
    } catch (error) {
      report.results["Agregar Negocio modal"] = toStepResult(false, `Agregar Negocio modal failed: ${error.message}`);
    }

    // Step 4: Open Administrar Negocios
    let adminPageUrl = "";
    try {
      const administrarVisible = await isVisible(page.getByText(regexForText("Administrar Negocios")), 5000);
      if (!administrarVisible) {
        await clickByVisibleText(page, "Mi Negocio", { timeout: 5000 });
      }
      await clickByVisibleText(page, "Administrar Negocios", { timeout: 9000 });

      const seccionInfoGeneral = await findAnyVisible(page, ["Información General"], 10000);
      const seccionDetallesCuenta = await findAnyVisible(page, ["Detalles de la Cuenta"], 10000);
      const seccionTusNegocios = await findAnyVisible(page, ["Tus Negocios"], 10000);
      const seccionLegal = await findAnyVisible(page, ["Sección Legal"], 10000);
      const adminPass = seccionInfoGeneral && seccionDetallesCuenta && seccionTusNegocios && seccionLegal;
      adminPageUrl = page.url();

      await captureScreenshot(page, screenshotsDir, "administrar-negocios-account-page", true);
      report.results["Administrar Negocios view"] = toStepResult(
        adminPass,
        adminPass
          ? "All expected account sections are visible."
          : "One or more account sections are missing.",
      );
    } catch (error) {
      report.results["Administrar Negocios view"] = toStepResult(
        false,
        `Could not open Administrar Negocios: ${error.message}`,
      );
    }

    // Step 5: Validate Información General
    try {
      const userNameVisible = await isVisible(
        page.locator("section,div").filter({ hasText: regexForText("Información General") }).locator("h1,h2,h3,p,span"),
        8000,
      );
      const userEmailVisible =
        (await isVisible(page.getByText(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/), 8000)) ||
        (await isVisible(page.getByText(regexForText("juanlucasbarbiergarzon@gmail.com")), 5000));
      const businessPlanVisible = await isVisible(page.getByText(regexForText("BUSINESS PLAN")), 8000);
      const cambiarPlanVisible = await isVisible(
        page.getByRole("button", { name: regexForText("Cambiar Plan") }),
        8000,
      );
      const infoPass = userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;

      report.results["Información General"] = toStepResult(
        infoPass,
        infoPass
          ? "User data, BUSINESS PLAN and Cambiar Plan are visible."
          : "Información General is missing one or more required elements.",
      );
    } catch (error) {
      report.results["Información General"] = toStepResult(false, `Información General validation failed: ${error.message}`);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      const cuentaCreada = await isVisible(page.getByText(regexForText("Cuenta creada")), 8000);
      const estadoActivo = await isVisible(page.getByText(regexForText("Estado activo")), 8000);
      const idiomaSeleccionado = await isVisible(page.getByText(regexForText("Idioma seleccionado")), 8000);
      const detallesPass = cuentaCreada && estadoActivo && idiomaSeleccionado;

      report.results["Detalles de la Cuenta"] = toStepResult(
        detallesPass,
        detallesPass
          ? "Detalles de la Cuenta includes all requested labels."
          : "Detalles de la Cuenta missing one or more labels.",
      );
    } catch (error) {
      report.results["Detalles de la Cuenta"] = toStepResult(
        false,
        `Detalles de la Cuenta validation failed: ${error.message}`,
      );
    }

    // Step 7: Validate Tus Negocios
    try {
      const businessListVisible = await isVisible(
        page.locator("section,div").filter({ hasText: regexForText("Tus Negocios") }),
        8000,
      );
      const agregarNegocioButton = await isVisible(
        page.getByRole("button", { name: regexForText("Agregar Negocio") }),
        8000,
      );
      const cuotaNegocios = await isVisible(page.getByText(regexForText("Tienes 2 de 3 negocios")), 8000);
      const negociosPass = businessListVisible && agregarNegocioButton && cuotaNegocios;

      report.results["Tus Negocios"] = toStepResult(
        negociosPass,
        negociosPass
          ? "Tus Negocios list, Add button and quota text are visible."
          : "Tus Negocios missing one or more required elements.",
      );
    } catch (error) {
      report.results["Tus Negocios"] = toStepResult(false, `Tus Negocios validation failed: ${error.message}`);
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const terminosResult = await validateLegalPage({
        appPage: page,
        context,
        linkText: "Términos y Condiciones",
        expectedHeading: "Términos y Condiciones",
        screenshotsDir,
        fallbackBackUrl: adminPageUrl,
      });
      report.evidence.legalUrls["Términos y Condiciones"] = terminosResult.finalUrl;
      report.results["Términos y Condiciones"] = toStepResult(
        terminosResult.pass,
        terminosResult.pass
          ? "Legal page heading and content were found."
          : `Legal page validation failed (headingFound=${terminosResult.headingFound}, legalContentFound=${terminosResult.legalContentFound}).`,
      );
    } catch (error) {
      report.results["Términos y Condiciones"] = toStepResult(
        false,
        `Términos y Condiciones validation failed: ${error.message}`,
      );
    }

    // Step 9: Validate Política de Privacidad
    try {
      const politicaResult = await validateLegalPage({
        appPage: page,
        context,
        linkText: "Política de Privacidad",
        expectedHeading: "Política de Privacidad",
        screenshotsDir,
        fallbackBackUrl: adminPageUrl,
      });
      report.evidence.legalUrls["Política de Privacidad"] = politicaResult.finalUrl;
      report.results["Política de Privacidad"] = toStepResult(
        politicaResult.pass,
        politicaResult.pass
          ? "Privacy page heading and content were found."
          : `Privacy page validation failed (headingFound=${politicaResult.headingFound}, legalContentFound=${politicaResult.legalContentFound}).`,
      );
    } catch (error) {
      report.results["Política de Privacidad"] = toStepResult(
        false,
        `Política de Privacidad validation failed: ${error.message}`,
      );
    }
  } finally {
    if (browser) {
      await browser.close();
    }
  }

  report.finishedAt = new Date().toISOString();
  const hasFailures = Object.values(report.results).some((entry) => entry.status === "FAIL");
  report.summary = {
    overallStatus: hasFailures ? "FAIL" : "PASS",
    failedFields: Object.entries(report.results)
      .filter(([, result]) => result.status === "FAIL")
      .map(([key]) => key),
  };

  const reportPath = path.join(artifactsDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  console.log("FINAL_REPORT_PATH:", reportPath);
  console.log("FINAL_REPORT_START");
  console.log(JSON.stringify(report, null, 2));
  console.log("FINAL_REPORT_END");

  process.exitCode = hasFailures ? 1 : 0;
}

run().catch((error) => {
  console.error("Fatal error running SaleADS Mi Negocio workflow:", error);
  process.exit(1);
});
