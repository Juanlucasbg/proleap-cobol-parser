#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

const ARTIFACTS_DIR = path.resolve(
  process.env.SALEADS_ARTIFACTS_DIR || path.join("e2e", "artifacts", TEST_NAME),
);
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "report.json");
const GOOGLE_EMAIL = process.env.SALEADS_GOOGLE_EMAIL || DEFAULT_GOOGLE_EMAIL;
const START_URL =
  process.env.SALEADS_URL ||
  process.env.SALEADS_LOGIN_URL ||
  process.env.BASE_URL ||
  "";
const HEADLESS =
  (process.env.SALEADS_HEADLESS || process.env.HEADLESS || "true").toLowerCase() !== "false";

const report = {
  name: TEST_NAME,
  startedAt: new Date().toISOString(),
  startUrlProvided: Boolean(START_URL),
  fields: {},
  checkpoints: [],
  legalUrls: {
    terminosYCondiciones: null,
    politicaDePrivacidad: null,
  },
  notes: [],
  error: null,
};

for (const field of REPORT_FIELDS) {
  report.fields[field] = {
    status: "FAIL",
    details: [],
  };
}

function ensureArtifactsDir() {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

function pushDetail(field, detail) {
  if (!report.fields[field]) {
    return;
  }

  report.fields[field].details.push(detail);
}

function setFieldStatus(field, pass, details = []) {
  if (!report.fields[field]) {
    return;
  }

  report.fields[field].status = pass ? "PASS" : "FAIL";
  for (const detail of details) {
    pushDetail(field, detail);
  }
}

async function waitForUi(page, reason) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await page.waitForTimeout(700);
  report.checkpoints.push({
    type: "wait",
    reason,
    url: page.url(),
    at: new Date().toISOString(),
  });
}

function textLocators(page, pattern) {
  return [
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByRole("menuitem", { name: pattern }),
    page.getByRole("tab", { name: pattern }),
    page.getByRole("option", { name: pattern }),
    page.getByRole("heading", { name: pattern }),
    page.getByText(pattern),
  ];
}

async function firstVisible(locators) {
  for (const locator of locators) {
    const candidate = locator.first();
    try {
      if (await candidate.isVisible({ timeout: 750 })) {
        return candidate;
      }
    } catch (error) {
      // Keep trying locator variants.
    }
  }

  return null;
}

async function waitForVisibleLocator(page, locatorFactory, label, timeoutMs = 20_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const locator = await firstVisible(locatorFactory(page));
    if (locator) {
      return locator;
    }
    await page.waitForTimeout(350);
  }

  throw new Error(`No visible element found for: ${label}`);
}

async function clickByText(page, pattern, label, timeoutMs = 20_000) {
  const locator = await waitForVisibleLocator(page, () => textLocators(page, pattern), label, timeoutMs);
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click({ timeout: 10_000 });
  await waitForUi(page, `after click: ${label}`);
  return locator;
}

async function existsByText(page, pattern, timeoutMs = 12_000) {
  try {
    await waitForVisibleLocator(page, () => textLocators(page, pattern), `validate ${pattern}`, timeoutMs);
    return true;
  } catch (error) {
    return false;
  }
}

async function existsAny(page, checks, timeoutMs = 12_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const check of checks) {
      try {
        if (await check()) {
          return true;
        }
      } catch (error) {
        // Ignore and continue.
      }
    }

    await page.waitForTimeout(300);
  }

  return false;
}

async function capture(page, fileName, fullPage = false) {
  const screenshotPath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  report.checkpoints.push({
    type: "screenshot",
    file: screenshotPath,
    url: page.url(),
    at: new Date().toISOString(),
  });
}

async function hasLegalText(page) {
  const contentText = await page.locator("body").innerText().catch(() => "");
  return contentText.replace(/\s+/g, " ").trim().length > 120;
}

async function clickLegalLinkAndValidate(page, linkPattern, headingPattern, screenshotName, reportField, urlKey) {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickByText(page, linkPattern, reportField);
  const popup = await popupPromise;

  const legalPage = popup || page;
  if (popup) {
    await waitForUi(legalPage, `${reportField} popup page loaded`);
  }

  const headingVisible = await existsByText(legalPage, headingPattern, 12_000);
  const legalTextVisible = await hasLegalText(legalPage);
  await capture(legalPage, screenshotName, true);

  report.legalUrls[urlKey] = legalPage.url();
  const pass = headingVisible && legalTextVisible;
  setFieldStatus(reportField, pass, [
    `heading visible: ${headingVisible}`,
    `legal text visible: ${legalTextVisible}`,
    `final url: ${legalPage.url()}`,
  ]);

  if (popup) {
    await legalPage.close().catch(() => {});
    await page.bringToFront().catch(() => {});
    await waitForUi(page, `returned to application after ${reportField}`);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 15_000 }).catch(() => {});
    await waitForUi(page, `returned to application via goBack after ${reportField}`);
  }
}

async function loginWithGoogle(page) {
  if (START_URL) {
    await page.goto(START_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page, "initial page load from configured start url");
  } else {
    report.notes.push(
      "No SALEADS_URL / SALEADS_LOGIN_URL / BASE_URL configured. Assuming browser starts on login page.",
    );
    await waitForUi(page, "initial page load without configured url");
  }

  const loginPattern = /(sign in with google|iniciar sesi.n con google|continuar con google|google)/i;
  const googlePopupPromise = page.waitForEvent("popup", { timeout: 7_500 }).catch(() => null);
  await clickByText(page, loginPattern, "login with Google");
  const googlePopup = await googlePopupPromise;
  const accountSelectionPage = googlePopup || page;

  if (googlePopup) {
    await waitForUi(googlePopup, "google selector popup loaded");
  }

  const accountVisible = await existsByText(
    accountSelectionPage,
    new RegExp(GOOGLE_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
    12_000,
  );

  if (accountVisible) {
    const accountLocator = await waitForVisibleLocator(
      accountSelectionPage,
      () =>
        textLocators(
          accountSelectionPage,
          new RegExp(GOOGLE_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
        ),
      "google account selection",
      12_000,
    );
    await accountLocator.click({ timeout: 10_000 });
    await waitForUi(accountSelectionPage, "after selecting Google account");
  } else {
    report.notes.push(`Google account '${GOOGLE_EMAIL}' selector did not appear; continuing.`);
  }

  if (googlePopup) {
    await googlePopup.waitForEvent("close", { timeout: 20_000 }).catch(() => {});
    await page.bringToFront().catch(() => {});
  }

  await waitForUi(page, "post-login application load");

  const mainVisible = await existsAny(
    page,
    [
      async () => page.locator("main").first().isVisible(),
      async () => page.locator("aside").first().isVisible(),
      async () => existsByText(page, /Negocio/i, 2_000),
    ],
    20_000,
  );

  const leftSidebarVisible = await existsAny(
    page,
    [
      async () => page.locator("aside").first().isVisible(),
      async () => page.getByRole("navigation").first().isVisible(),
      async () => existsByText(page, /Mi Negocio/i, 3_000),
    ],
    15_000,
  );

  await capture(page, "step1_dashboard_loaded.png", true);
  setFieldStatus("Login", mainVisible && leftSidebarVisible, [
    `main interface visible: ${mainVisible}`,
    `left sidebar visible: ${leftSidebarVisible}`,
  ]);
}

async function openMiNegocioMenu(page) {
  const negocioVisible = await existsByText(page, /Negocio/i, 10_000);
  if (negocioVisible) {
    await clickByText(page, /Negocio/i, "Negocio section");
  }

  await clickByText(page, /Mi Negocio/i, "Mi Negocio option");

  const agregarVisible = await existsByText(page, /Agregar Negocio/i, 10_000);
  const administrarVisible = await existsByText(page, /Administrar Negocios/i, 10_000);
  await capture(page, "step2_mi_negocio_expanded.png", true);

  setFieldStatus("Mi Negocio menu", agregarVisible && administrarVisible, [
    `submenu expanded: ${agregarVisible || administrarVisible}`,
    `'Agregar Negocio' visible: ${agregarVisible}`,
    `'Administrar Negocios' visible: ${administrarVisible}`,
  ]);
}

async function validateAgregarNegocioModal(page) {
  await clickByText(page, /Agregar Negocio/i, "Agregar Negocio (sidebar)");

  const modalTitleVisible = await existsByText(page, /Crear Nuevo Negocio/i, 12_000);
  const nombreInputVisible = await existsAny(
    page,
    [
      async () => page.getByLabel(/Nombre del Negocio/i).first().isVisible(),
      async () => page.getByPlaceholder(/Nombre del Negocio/i).first().isVisible(),
      async () => page.locator("input").first().isVisible(),
    ],
    12_000,
  );
  const limitTextVisible = await existsByText(page, /Tienes 2 de 3 negocios/i, 10_000);
  const cancelarVisible = await existsByText(page, /Cancelar/i, 8_000);
  const crearVisible = await existsByText(page, /Crear Negocio/i, 8_000);

  await capture(page, "step3_agregar_negocio_modal.png", true);

  if (nombreInputVisible) {
    const inputLocator = page.getByLabel(/Nombre del Negocio/i).first();
    const fallbackInput = page.locator("input").first();
    if (await inputLocator.isVisible().catch(() => false)) {
      await inputLocator.fill("Negocio Prueba Automatizacion");
    } else if (await fallbackInput.isVisible().catch(() => false)) {
      await fallbackInput.fill("Negocio Prueba Automatizacion");
    }
  }

  if (cancelarVisible) {
    await clickByText(page, /Cancelar/i, "Cancelar button");
  }

  setFieldStatus(
    "Agregar Negocio modal",
    modalTitleVisible && nombreInputVisible && limitTextVisible && cancelarVisible && crearVisible,
    [
      `modal title visible: ${modalTitleVisible}`,
      `nombre input visible: ${nombreInputVisible}`,
      `limit text visible: ${limitTextVisible}`,
      `'Cancelar' visible: ${cancelarVisible}`,
      `'Crear Negocio' visible: ${crearVisible}`,
    ],
  );
}

async function openAdministrarNegocios(page) {
  const administrarVisible = await existsByText(page, /Administrar Negocios/i, 5_000);
  if (!administrarVisible) {
    await clickByText(page, /Mi Negocio/i, "expand Mi Negocio for Administrar Negocios");
  }

  await clickByText(page, /Administrar Negocios/i, "Administrar Negocios");
  await waitForUi(page, "Administrar Negocios page loaded");

  const informacionGeneralVisible = await existsByText(page, /Informaci.n General/i, 15_000);
  const detallesCuentaVisible = await existsByText(page, /Detalles de la Cuenta/i, 15_000);
  const tusNegociosVisible = await existsByText(page, /Tus Negocios/i, 15_000);
  const seccionLegalVisible = await existsByText(page, /Secci.n Legal/i, 15_000);
  await capture(page, "step4_administrar_negocios_full.png", true);

  setFieldStatus(
    "Administrar Negocios view",
    informacionGeneralVisible && detallesCuentaVisible && tusNegociosVisible && seccionLegalVisible,
    [
      `'Informacion General' visible: ${informacionGeneralVisible}`,
      `'Detalles de la Cuenta' visible: ${detallesCuentaVisible}`,
      `'Tus Negocios' visible: ${tusNegociosVisible}`,
      `'Seccion Legal' visible: ${seccionLegalVisible}`,
    ],
  );

  const userNameVisible = await existsAny(
    page,
    [
      async () => page.locator("[data-testid*='name']").first().isVisible(),
      async () => page.locator("text=/[A-Z][a-z]+\\s+[A-Z][a-z]+/").first().isVisible(),
    ],
    8_000,
  );
  const userEmailVisible = await existsAny(
    page,
    [
      async () => page.locator("text=/@/").first().isVisible(),
      async () => page.locator("[data-testid*='email']").first().isVisible(),
    ],
    8_000,
  );
  const businessPlanVisible = await existsByText(page, /BUSINESS PLAN/i, 12_000);
  const cambiarPlanVisible = await existsByText(page, /Cambiar Plan/i, 12_000);

  setFieldStatus(
    "Información General",
    userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible,
    [
      `user name visible: ${userNameVisible}`,
      `user email visible: ${userEmailVisible}`,
      `'BUSINESS PLAN' visible: ${businessPlanVisible}`,
      `'Cambiar Plan' visible: ${cambiarPlanVisible}`,
    ],
  );

  const cuentaCreadaVisible = await existsByText(page, /Cuenta creada/i, 10_000);
  const estadoActivoVisible = await existsByText(page, /Estado activo/i, 10_000);
  const idiomaSeleccionadoVisible = await existsByText(page, /Idioma seleccionado/i, 10_000);

  setFieldStatus("Detalles de la Cuenta", cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible, [
    `'Cuenta creada' visible: ${cuentaCreadaVisible}`,
    `'Estado activo' visible: ${estadoActivoVisible}`,
    `'Idioma seleccionado' visible: ${idiomaSeleccionadoVisible}`,
  ]);

  const businessListVisible = await existsAny(
    page,
    [
      async () => page.locator("table").first().isVisible(),
      async () => page.locator("ul li").first().isVisible(),
      async () => existsByText(page, /Tus Negocios/i, 4_000),
    ],
    10_000,
  );
  const addBusinessButtonVisible = await existsByText(page, /Agregar Negocio/i, 10_000);
  const businessLimitVisible = await existsByText(page, /Tienes 2 de 3 negocios/i, 10_000);

  setFieldStatus("Tus Negocios", businessListVisible && addBusinessButtonVisible && businessLimitVisible, [
    `business list visible: ${businessListVisible}`,
    `'Agregar Negocio' visible: ${addBusinessButtonVisible}`,
    `'Tienes 2 de 3 negocios' visible: ${businessLimitVisible}`,
  ]);
}

function writeReportAndPrint() {
  report.finishedAt = new Date().toISOString();
  report.allPassed = Object.values(report.fields).every((item) => item.status === "PASS");
  fs.writeFileSync(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  const orderedSummary = REPORT_FIELDS.map((field) => ({
    field,
    status: report.fields[field].status,
  }));

  console.log("=== SaleADS Mi Negocio Test Report ===");
  for (const row of orderedSummary) {
    console.log(`${row.field}: ${row.status}`);
  }
  console.log(`Report: ${REPORT_PATH}`);
}

async function run() {
  ensureArtifactsDir();

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1100 },
  });
  const page = await context.newPage();

  try {
    await loginWithGoogle(page);
    await openMiNegocioMenu(page);
    await validateAgregarNegocioModal(page);
    await openAdministrarNegocios(page);
    await clickLegalLinkAndValidate(
      page,
      /T.rminos y Condiciones/i,
      /T.rminos y Condiciones/i,
      "step8_terminos_y_condiciones.png",
      "Términos y Condiciones",
      "terminosYCondiciones",
    );
    await clickLegalLinkAndValidate(
      page,
      /Pol.tica de Privacidad/i,
      /Pol.tica de Privacidad/i,
      "step9_politica_de_privacidad.png",
      "Política de Privacidad",
      "politicaDePrivacidad",
    );
  } catch (error) {
    report.error = error instanceof Error ? error.message : String(error);
    report.notes.push("Execution stopped due to a blocking error.");
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }

  writeReportAndPrint();
  process.exitCode = report.allPassed ? 0 : 1;
}

run().catch((error) => {
  report.error = error instanceof Error ? error.message : String(error);
  writeReportAndPrint();
  process.exitCode = 1;
});
