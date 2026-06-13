#!/usr/bin/env node

const { chromium } = require("playwright");
const fs = require("node:fs/promises");
const path = require("node:path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const WAIT_TIMEOUT_MS = Number(process.env.SALEADS_WAIT_TIMEOUT_MS || 15000);

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

function nowTimestampForPath() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function toErrorMessage(error) {
  if (!error) {
    return "Unknown error";
  }

  if (error instanceof Error) {
    return error.stack || error.message;
  }

  return String(error);
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUiToLoad(page) {
  await page.waitForTimeout(250);
  await page.waitForLoadState("domcontentloaded", { timeout: WAIT_TIMEOUT_MS }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: WAIT_TIMEOUT_MS }).catch(() => {});
  await page.waitForTimeout(400);
}

async function firstVisible(locators, timeout = 4000) {
  for (const locator of locators) {
    try {
      await locator.first().waitFor({ state: "visible", timeout });
      return locator.first();
    } catch (_error) {
      // Try next locator.
    }
  }

  return null;
}

async function isVisible(locator, timeout = 4000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch (_error) {
    return false;
  }
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUiToLoad(page);
}

async function captureScreenshot(page, filePath, fullPage = false) {
  await page.screenshot({ path: filePath, fullPage });
}

function initReport(artifactDir) {
  return {
    name: TEST_NAME,
    goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
    executedAt: new Date().toISOString(),
    artifactDirectory: artifactDir,
    summary: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])),
    steps: {},
    evidence: {
      screenshots: [],
      finalUrls: {},
    },
    errors: [],
  };
}

function stepResultTemplate(stepId, stepName) {
  return {
    id: stepId,
    name: stepName,
    pass: false,
    validations: [],
    notes: [],
  };
}

function pushValidation(stepResult, label, pass, details = "") {
  stepResult.validations.push({
    label,
    pass,
    details,
  });
}

async function resolveLoginButton(page) {
  return firstVisible([
    page.getByRole("button", { name: /sign in with google|continuar con google|google/i }),
    page.getByRole("link", { name: /sign in with google|continuar con google|google/i }),
    page.getByText(/sign in with google|continuar con google|google/i),
  ]);
}

async function resolveMenuOption(page, labelRegex) {
  return firstVisible([
    page.getByRole("button", { name: labelRegex }),
    page.getByRole("link", { name: labelRegex }),
    page.getByText(labelRegex),
  ]);
}

async function validateLegalPage({
  context,
  appPage,
  linkRegex,
  expectedHeadingRegex,
  screenshotPath,
  stepName,
  returnUrl,
}) {
  const stepResult = stepResultTemplate(stepName === "Términos y Condiciones" ? 8 : 9, stepName);
  const link = await resolveMenuOption(appPage, linkRegex);

  if (!link) {
    pushValidation(stepResult, `Link ${stepName} visible`, false, "No se encontró el enlace legal.");
    return {
      pass: false,
      stepResult,
      finalUrl: "",
    };
  }

  const possibleNewTab = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
  await clickAndWait(link, appPage);

  const popupPage = await possibleNewTab;
  const legalPage = popupPage || appPage;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: WAIT_TIMEOUT_MS }).catch(() => {});
  await legalPage.waitForLoadState("networkidle", { timeout: WAIT_TIMEOUT_MS }).catch(() => {});

  const headingVisible = await isVisible(
    legalPage.getByRole("heading", { name: expectedHeadingRegex }),
    5000,
  ) || await isVisible(legalPage.getByText(expectedHeadingRegex), 5000);

  const bodyText = (await legalPage.locator("body").innerText().catch(() => "")).trim();
  const legalTextVisible = bodyText.length > 200;

  pushValidation(stepResult, `Encabezado ${stepName} visible`, headingVisible);
  pushValidation(stepResult, "Contenido legal visible", legalTextVisible, `Longitud: ${bodyText.length}`);

  await captureScreenshot(legalPage, screenshotPath, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close().catch(() => {});
    await appPage.bringToFront().catch(() => {});
    await waitForUiToLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded", timeout: WAIT_TIMEOUT_MS }).catch(async () => {
      if (returnUrl) {
        await appPage.goto(returnUrl, { waitUntil: "domcontentloaded", timeout: WAIT_TIMEOUT_MS }).catch(() => {});
      }
    });
    await waitForUiToLoad(appPage);
  }

  stepResult.pass = headingVisible && legalTextVisible;

  return {
    pass: stepResult.pass,
    stepResult,
    finalUrl,
  };
}

async function run() {
  const artifactsDir = path.join(process.cwd(), "artifacts", TEST_NAME, nowTimestampForPath());
  await ensureDir(artifactsDir);
  const report = initReport(artifactsDir);

  let browser;
  let context;
  let page;

  try {
    if (process.env.PW_CDP_URL) {
      browser = await chromium.connectOverCDP(process.env.PW_CDP_URL);
      context = browser.contexts()[0] || await browser.newContext();
      page = context.pages()[0] || await context.newPage();
    } else {
      browser = await chromium.launch({ headless: process.env.HEADLESS !== "false" });
      context = await browser.newContext();
      page = await context.newPage();

      if (!process.env.SALEADS_LOGIN_URL) {
        throw new Error(
          "SALEADS_LOGIN_URL is required when PW_CDP_URL is not provided. " +
            "To respect environment neutrality, no default domain is hardcoded.",
        );
      }

      await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded", timeout: WAIT_TIMEOUT_MS });
      await waitForUiToLoad(page);
    }

    const accountEmail = process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;

    // Step 1 - Login with Google
    {
      const step = stepResultTemplate(1, "Login with Google");
      const loginButton = await resolveLoginButton(page);
      if (loginButton) {
        await clickAndWait(loginButton, page);
      }

      const accountOption = await firstVisible([
        page.getByRole("button", { name: new RegExp(accountEmail, "i") }),
        page.getByRole("link", { name: new RegExp(accountEmail, "i") }),
        page.getByText(new RegExp(accountEmail, "i")),
      ], 5000);

      if (accountOption) {
        await clickAndWait(accountOption, page);
      } else {
        step.notes.push("No se mostró selector de cuenta de Google o ya había sesión activa.");
      }

      const appInterfaceVisible = await firstVisible([
        page.locator("main"),
        page.locator("aside"),
        page.getByText(/dashboard|panel|inicio|mi negocio|negocio/i),
      ], 10000);
      const sidebarVisible = await firstVisible([
        page.locator("aside"),
        page.locator("nav"),
        page.getByText(/negocio|mi negocio/i),
      ], 10000);

      const appVisible = Boolean(appInterfaceVisible);
      const leftNavVisible = Boolean(sidebarVisible);

      pushValidation(step, "Interfaz principal visible", appVisible);
      pushValidation(step, "Sidebar izquierdo visible", leftNavVisible);

      const screenshotPath = path.join(artifactsDir, "01_dashboard_loaded.png");
      await captureScreenshot(page, screenshotPath, true);
      report.evidence.screenshots.push(screenshotPath);

      step.pass = appVisible && leftNavVisible;
      report.steps.Login = step;
      report.summary.Login = step.pass ? "PASS" : "FAIL";
    }

    // Step 2 - Open Mi Negocio menu
    {
      const step = stepResultTemplate(2, "Open Mi Negocio menu");
      const negocioSection = await resolveMenuOption(page, /negocio/i);
      if (negocioSection) {
        await clickAndWait(negocioSection, page);
      }

      const miNegocioOption = await resolveMenuOption(page, /mi negocio/i);
      if (miNegocioOption) {
        await clickAndWait(miNegocioOption, page);
      }

      const agregarNegocioVisible = await isVisible(page.getByText(/agregar negocio/i), 8000);
      const administrarNegociosVisible = await isVisible(page.getByText(/administrar negocios/i), 8000);
      const submenuExpanded = agregarNegocioVisible && administrarNegociosVisible;

      pushValidation(step, "Submenu expandido", submenuExpanded);
      pushValidation(step, "Agregar Negocio visible", agregarNegocioVisible);
      pushValidation(step, "Administrar Negocios visible", administrarNegociosVisible);

      const screenshotPath = path.join(artifactsDir, "02_mi_negocio_menu_expanded.png");
      await captureScreenshot(page, screenshotPath, true);
      report.evidence.screenshots.push(screenshotPath);

      step.pass = submenuExpanded;
      report.steps["Mi Negocio menu"] = step;
      report.summary["Mi Negocio menu"] = step.pass ? "PASS" : "FAIL";
    }

    // Step 3 - Validate Agregar Negocio modal
    {
      const step = stepResultTemplate(3, "Validate Agregar Negocio modal");
      const agregarNegocioOption = await resolveMenuOption(page, /agregar negocio/i);
      if (!agregarNegocioOption) {
        throw new Error("No se encontró la opción Agregar Negocio.");
      }

      await clickAndWait(agregarNegocioOption, page);

      const modalTitleVisible = await isVisible(page.getByText(/crear nuevo negocio/i), 10000);
      const nombreInputVisible = await isVisible(
        page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i)),
        10000,
      );
      const quotaTextVisible = await isVisible(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i), 10000);
      const cancelButtonVisible = await isVisible(page.getByRole("button", { name: /cancelar/i }), 10000);
      const createButtonVisible = await isVisible(page.getByRole("button", { name: /crear negocio/i }), 10000);

      pushValidation(step, "Título Crear Nuevo Negocio visible", modalTitleVisible);
      pushValidation(step, "Campo Nombre del Negocio visible", nombreInputVisible);
      pushValidation(step, "Texto Tienes 2 de 3 negocios visible", quotaTextVisible);
      pushValidation(step, "Botón Cancelar visible", cancelButtonVisible);
      pushValidation(step, "Botón Crear Negocio visible", createButtonVisible);

      const screenshotPath = path.join(artifactsDir, "03_agregar_negocio_modal.png");
      await captureScreenshot(page, screenshotPath, true);
      report.evidence.screenshots.push(screenshotPath);

      const nombreInput = await firstVisible([
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
      ], 2000);
      if (nombreInput) {
        await nombreInput.click();
        await nombreInput.fill("Negocio Prueba Automatización");
        await waitForUiToLoad(page);
      }

      const cancelButton = await resolveMenuOption(page, /cancelar/i);
      if (cancelButton) {
        await clickAndWait(cancelButton, page);
      }

      step.pass =
        modalTitleVisible &&
        nombreInputVisible &&
        quotaTextVisible &&
        cancelButtonVisible &&
        createButtonVisible;
      report.steps["Agregar Negocio modal"] = step;
      report.summary["Agregar Negocio modal"] = step.pass ? "PASS" : "FAIL";
    }

    // Step 4 - Open Administrar Negocios
    {
      const step = stepResultTemplate(4, "Open Administrar Negocios");

      const miNegocioOption = await resolveMenuOption(page, /mi negocio/i);
      if (miNegocioOption) {
        await clickAndWait(miNegocioOption, page);
      }

      const administrarOption = await resolveMenuOption(page, /administrar negocios/i);
      if (!administrarOption) {
        throw new Error("No se encontró la opción Administrar Negocios.");
      }

      await clickAndWait(administrarOption, page);

      const infoGeneralVisible = await isVisible(page.getByText(/informaci[oó]n general/i), 10000);
      const accountDetailsVisible = await isVisible(page.getByText(/detalles de la cuenta/i), 10000);
      const tusNegociosVisible = await isVisible(page.getByText(/tus negocios/i), 10000);
      const legalSectionVisible = await isVisible(page.getByText(/secci[oó]n legal/i), 10000);

      pushValidation(step, "Sección Información General visible", infoGeneralVisible);
      pushValidation(step, "Sección Detalles de la Cuenta visible", accountDetailsVisible);
      pushValidation(step, "Sección Tus Negocios visible", tusNegociosVisible);
      pushValidation(step, "Sección Legal visible", legalSectionVisible);

      const screenshotPath = path.join(artifactsDir, "04_administrar_negocios_page.png");
      await captureScreenshot(page, screenshotPath, true);
      report.evidence.screenshots.push(screenshotPath);

      step.pass = infoGeneralVisible && accountDetailsVisible && tusNegociosVisible && legalSectionVisible;
      report.steps["Administrar Negocios view"] = step;
      report.summary["Administrar Negocios view"] = step.pass ? "PASS" : "FAIL";
    }

    // Step 5 - Validate Información General
    {
      const step = stepResultTemplate(5, "Validate Información General");
      const infoSection = page.locator("section, div").filter({ hasText: /informaci[oó]n general/i }).first();
      const sectionText = ((await infoSection.innerText().catch(() => "")) || "").replace(/\s+/g, " ").trim();

      const userNameVisible = /\b[A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,}\b/.test(sectionText);
      const userEmailVisible =
        /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i.test(sectionText) ||
        (await isVisible(page.getByText(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i), 5000));
      const businessPlanVisible = /business plan/i.test(sectionText) || await isVisible(page.getByText(/business plan/i), 5000);
      const changePlanVisible = await isVisible(page.getByRole("button", { name: /cambiar plan/i }), 5000);

      pushValidation(step, "Nombre de usuario visible", userNameVisible);
      pushValidation(step, "Email de usuario visible", userEmailVisible);
      pushValidation(step, "Texto BUSINESS PLAN visible", businessPlanVisible);
      pushValidation(step, "Botón Cambiar Plan visible", changePlanVisible);

      step.pass = userNameVisible && userEmailVisible && businessPlanVisible && changePlanVisible;
      report.steps["Información General"] = step;
      report.summary["Información General"] = step.pass ? "PASS" : "FAIL";
    }

    // Step 6 - Validate Detalles de la Cuenta
    {
      const step = stepResultTemplate(6, "Validate Detalles de la Cuenta");
      const detailsSection = page.locator("section, div").filter({ hasText: /detalles de la cuenta/i }).first();
      const detailsText = ((await detailsSection.innerText().catch(() => "")) || "").replace(/\s+/g, " ").trim();

      const createdVisible = /cuenta creada/i.test(detailsText) || await isVisible(page.getByText(/cuenta creada/i), 5000);
      const statusVisible = /estado activo/i.test(detailsText) || await isVisible(page.getByText(/estado activo/i), 5000);
      const languageVisible = /idioma seleccionado/i.test(detailsText) || await isVisible(page.getByText(/idioma seleccionado/i), 5000);

      pushValidation(step, "Cuenta creada visible", createdVisible);
      pushValidation(step, "Estado activo visible", statusVisible);
      pushValidation(step, "Idioma seleccionado visible", languageVisible);

      step.pass = createdVisible && statusVisible && languageVisible;
      report.steps["Detalles de la Cuenta"] = step;
      report.summary["Detalles de la Cuenta"] = step.pass ? "PASS" : "FAIL";
    }

    // Step 7 - Validate Tus Negocios
    {
      const step = stepResultTemplate(7, "Validate Tus Negocios");
      const businessesSection = page.locator("section, div").filter({ hasText: /tus negocios/i }).first();

      const businessListVisible = await isVisible(
        businessesSection.locator("li, table, [role='row'], [data-testid*='business']"),
        5000,
      ) || /tus negocios/i.test(await businessesSection.innerText().catch(() => ""));
      const addBusinessButtonVisible = await isVisible(page.getByRole("button", { name: /agregar negocio/i }), 5000);
      const quotaTextVisible = await isVisible(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i), 5000);

      pushValidation(step, "Listado de negocios visible", businessListVisible);
      pushValidation(step, "Botón Agregar Negocio visible", addBusinessButtonVisible);
      pushValidation(step, "Texto Tienes 2 de 3 negocios visible", quotaTextVisible);

      step.pass = businessListVisible && addBusinessButtonVisible && quotaTextVisible;
      report.steps["Tus Negocios"] = step;
      report.summary["Tus Negocios"] = step.pass ? "PASS" : "FAIL";
    }

    // Step 8 - Validate Términos y Condiciones
    {
      const returnUrl = page.url();
      const screenshotPath = path.join(artifactsDir, "05_terminos_y_condiciones.png");
      const result = await validateLegalPage({
        context,
        appPage: page,
        linkRegex: /t[eé]rminos y condiciones/i,
        expectedHeadingRegex: /t[eé]rminos y condiciones/i,
        screenshotPath,
        stepName: "Términos y Condiciones",
        returnUrl,
      });

      report.evidence.screenshots.push(screenshotPath);
      report.evidence.finalUrls["Términos y Condiciones"] = result.finalUrl;
      report.steps["Términos y Condiciones"] = result.stepResult;
      report.summary["Términos y Condiciones"] = result.pass ? "PASS" : "FAIL";
    }

    // Step 9 - Validate Política de Privacidad
    {
      const returnUrl = page.url();
      const screenshotPath = path.join(artifactsDir, "06_politica_de_privacidad.png");
      const result = await validateLegalPage({
        context,
        appPage: page,
        linkRegex: /pol[ií]tica de privacidad/i,
        expectedHeadingRegex: /pol[ií]tica de privacidad/i,
        screenshotPath,
        stepName: "Política de Privacidad",
        returnUrl,
      });

      report.evidence.screenshots.push(screenshotPath);
      report.evidence.finalUrls["Política de Privacidad"] = result.finalUrl;
      report.steps["Política de Privacidad"] = result.stepResult;
      report.summary["Política de Privacidad"] = result.pass ? "PASS" : "FAIL";
    }
  } catch (error) {
    report.errors.push(toErrorMessage(error));
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
  }

  const reportPath = path.join(artifactsDir, "report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf-8");

  console.log(`\n=== ${TEST_NAME} report ===`);
  console.log(JSON.stringify(report.summary, null, 2));
  console.log(`Report path: ${reportPath}`);
  console.log(`Screenshots: ${report.evidence.screenshots.length}`);

  const failed = Object.values(report.summary).filter((result) => result !== "PASS").length;
  process.exitCode = failed > 0 ? 1 : 0;
}

run().catch((error) => {
  console.error("Fatal error while running automation:", toErrorMessage(error));
  process.exitCode = 1;
});
