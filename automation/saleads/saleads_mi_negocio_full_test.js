#!/usr/bin/env node

const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

const runId = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.join(__dirname, "artifacts", runId);
const screenshotsDir = path.join(artifactsDir, "screenshots");
const reportPath = path.join(artifactsDir, "report.json");

const report = {
  name: "saleads_mi_negocio_full_test",
  runId,
  startedAt: new Date().toISOString(),
  environment: {
    saleadsLoginUrl: process.env.SALEADS_LOGIN_URL || null,
    browserWsEndpoint: process.env.BROWSER_WS_ENDPOINT || null,
    headless: process.env.HEADLESS !== "false",
  },
  evidence: {
    screenshots: [],
    finalUrls: {},
  },
  summary: Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }]),
  ),
};

let browser;
let context;
let appPage;
let usedPopupForGoogle = false;

function nowIso() {
  return new Date().toISOString();
}

function safeFileName(input) {
  return input
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

function mark(field, status, details) {
  report.summary[field] = {
    status,
    details,
    updatedAt: nowIso(),
  };
}

function markPass(field, details) {
  mark(field, "PASS", details);
}

function markFail(field, details) {
  mark(field, "FAIL", details);
}

async function ensureDirs() {
  await fs.mkdir(screenshotsDir, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await page.waitForTimeout(750);
}

async function takeScreenshot(page, checkpoint, options = {}) {
  const fileName = `${String(report.evidence.screenshots.length + 1).padStart(2, "0")}_${safeFileName(checkpoint)}.png`;
  const destination = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: destination, fullPage: Boolean(options.fullPage) });
  report.evidence.screenshots.push({
    checkpoint,
    path: path.relative(__dirname, destination),
    createdAt: nowIso(),
  });
}

async function clickFirstVisible(page, candidates, clickOptions = {}) {
  for (const candidate of candidates) {
    const locator = candidate(page).first();
    const visible = await locator.isVisible().catch(() => false);

    if (!visible) {
      continue;
    }

    await locator.click(clickOptions).catch(async () => {
      await locator.click({ force: true, ...clickOptions });
    });
    await waitForUi(page);
    return true;
  }

  return false;
}

async function isVisible(locatorFactory) {
  return locatorFactory().first().isVisible().catch(() => false);
}

function textCandidate(textOrRegex) {
  return (page) => page.getByText(textOrRegex, { exact: typeof textOrRegex === "string" });
}

function roleCandidate(role, textOrRegex) {
  return (page) =>
    page.getByRole(role, {
      name: textOrRegex,
      exact: typeof textOrRegex === "string",
    });
}

async function initializeBrowser() {
  const cdpEndpoint = process.env.BROWSER_WS_ENDPOINT;
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const headless = process.env.HEADLESS !== "false";

  if (cdpEndpoint) {
    browser = await chromium.connectOverCDP(cdpEndpoint);
    context = browser.contexts()[0] || (await browser.newContext());
    appPage = context.pages()[0] || (await context.newPage());
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  if (!loginUrl) {
    throw new Error(
      "Missing SALEADS_LOGIN_URL or BROWSER_WS_ENDPOINT. Provide SALEADS_LOGIN_URL to open the login page, or BROWSER_WS_ENDPOINT to use an already-open browser session.",
    );
  }

  browser = await chromium.launch({ headless });
  context = await browser.newContext();
  appPage = await context.newPage();
  await appPage.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60_000 });
  await waitForUi(appPage);
}

async function executeGoogleLogin() {
  const loginClicked = await clickFirstVisible(appPage, [
    roleCandidate("button", /sign in with google/i),
    roleCandidate("button", /iniciar sesi[oó]n con google/i),
    roleCandidate("button", /continuar con google/i),
    roleCandidate("button", /google/i),
    textCandidate(/sign in with google/i),
    textCandidate(/iniciar sesi[oó]n con google/i),
    textCandidate(/continuar con google/i),
  ]);

  if (!loginClicked) {
    markFail("Login", "Google login button not found.");
    return false;
  }

  // A Google popup/account selector may appear. If it appears, select the configured account.
  const popup = await appPage.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
  if (popup) {
    usedPopupForGoogle = true;
    await popup.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => {});
    await waitForUi(popup);
    const selected = await clickFirstVisible(popup, [
      textCandidate(new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
      roleCandidate("button", new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
    ]);
    if (selected) {
      await waitForUi(popup);
    }
  } else {
    const selectedInline = await clickFirstVisible(appPage, [
      textCandidate(new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
      roleCandidate("button", new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
    ]);
    if (selectedInline) {
      await waitForUi(appPage);
    }
  }

  if (usedPopupForGoogle) {
    await appPage.bringToFront();
    await waitForUi(appPage);
  }

  const mainUiVisible = await isVisible(() =>
    appPage.locator("nav, aside").filter({ hasText: /negocio|dashboard|inicio/i }),
  );
  const sidebarVisible = await isVisible(() => appPage.locator("aside, nav"));

  if (mainUiVisible && sidebarVisible) {
    await takeScreenshot(appPage, "dashboard_loaded");
    markPass("Login", "Dashboard and left sidebar are visible after Google login.");
    return true;
  }

  markFail("Login", "Could not confirm dashboard and sidebar after login.");
  return false;
}

async function openMiNegocioMenu() {
  const negocioFound = await clickFirstVisible(appPage, [
    roleCandidate("button", /negocio/i),
    roleCandidate("link", /negocio/i),
    textCandidate(/^negocio$/i),
    textCandidate(/negocio/i),
  ]);

  const miNegocioFound = await clickFirstVisible(appPage, [
    roleCandidate("button", /mi negocio/i),
    roleCandidate("link", /mi negocio/i),
    textCandidate(/mi negocio/i),
  ]);

  if (!negocioFound && !miNegocioFound) {
    markFail("Mi Negocio menu", "Could not find Negocio or Mi Negocio in the sidebar.");
    return false;
  }

  const agregarVisible = await isVisible(() => appPage.getByText(/agregar negocio/i));
  const administrarVisible = await isVisible(() => appPage.getByText(/administrar negocios/i));

  if (agregarVisible && administrarVisible) {
    await takeScreenshot(appPage, "mi_negocio_menu_expanded");
    markPass(
      "Mi Negocio menu",
      "Mi Negocio submenu is expanded with Agregar Negocio and Administrar Negocios.",
    );
    return true;
  }

  markFail(
    "Mi Negocio menu",
    "Mi Negocio submenu did not show both Agregar Negocio and Administrar Negocios.",
  );
  return false;
}

async function validateAgregarNegocioModal() {
  const clicked = await clickFirstVisible(appPage, [
    roleCandidate("button", /agregar negocio/i),
    roleCandidate("link", /agregar negocio/i),
    textCandidate(/agregar negocio/i),
  ]);

  if (!clicked) {
    markFail("Agregar Negocio modal", "Agregar Negocio action not found.");
    return false;
  }

  const titleVisible = await isVisible(() => appPage.getByText(/crear nuevo negocio/i));
  const inputVisible = await isVisible(() =>
    appPage.getByRole("textbox", { name: /nombre del negocio/i }),
  );
  const quotaVisible = await isVisible(() => appPage.getByText(/tienes 2 de 3 negocios/i));
  const cancelVisible = await isVisible(() => appPage.getByRole("button", { name: /cancelar/i }));
  const createVisible = await isVisible(() =>
    appPage.getByRole("button", { name: /crear negocio/i }),
  );

  if (titleVisible && inputVisible && quotaVisible && cancelVisible && createVisible) {
    await takeScreenshot(appPage, "agregar_negocio_modal");

    const input = appPage.getByRole("textbox", { name: /nombre del negocio/i }).first();
    if (await input.isVisible().catch(() => false)) {
      await input.click().catch(() => {});
      await input.fill("Negocio Prueba Automatización").catch(() => {});
    }

    await clickFirstVisible(appPage, [
      roleCandidate("button", /cancelar/i),
      textCandidate(/^cancelar$/i),
    ]);

    markPass(
      "Agregar Negocio modal",
      "Modal displayed expected title, input, quota text, and action buttons.",
    );
    return true;
  }

  markFail(
    "Agregar Negocio modal",
    "Modal did not contain all expected elements (title/input/quota/buttons).",
  );
  return false;
}

async function openAdministrarNegocios() {
  const administrarClicked = await clickFirstVisible(appPage, [
    roleCandidate("button", /administrar negocios/i),
    roleCandidate("link", /administrar negocios/i),
    textCandidate(/administrar negocios/i),
  ]);

  if (!administrarClicked) {
    // Retry by re-expanding Mi Negocio if menu collapsed.
    await clickFirstVisible(appPage, [
      roleCandidate("button", /mi negocio/i),
      roleCandidate("link", /mi negocio/i),
      textCandidate(/mi negocio/i),
    ]);
  }

  const administrarClickedRetry = administrarClicked
    ? true
    : await clickFirstVisible(appPage, [
        roleCandidate("button", /administrar negocios/i),
        roleCandidate("link", /administrar negocios/i),
        textCandidate(/administrar negocios/i),
      ]);

  if (!administrarClickedRetry) {
    markFail("Administrar Negocios view", "Could not open Administrar Negocios.");
    return false;
  }

  const generalInfoVisible = await isVisible(() => appPage.getByText(/informaci[oó]n general/i));
  const accountDetailsVisible = await isVisible(() =>
    appPage.getByText(/detalles de la cuenta/i),
  );
  const businessesVisible = await isVisible(() => appPage.getByText(/tus negocios/i));
  const legalVisible = await isVisible(() => appPage.getByText(/secci[oó]n legal/i));

  if (generalInfoVisible && accountDetailsVisible && businessesVisible && legalVisible) {
    await takeScreenshot(appPage, "administrar_negocios_account_page", { fullPage: true });
    markPass(
      "Administrar Negocios view",
      "Account page loaded with Información General, Detalles de la Cuenta, Tus Negocios and Sección Legal.",
    );
    return true;
  }

  markFail(
    "Administrar Negocios view",
    "Account page is missing one or more required sections after opening Administrar Negocios.",
  );
  return false;
}

async function validateInformacionGeneral() {
  const userNameVisible = await isVisible(() =>
    appPage
      .locator("section, div, article")
      .filter({ hasText: /informaci[oó]n general/i })
      .locator("h1, h2, h3, p, span")
      .filter({ hasNotText: /^$/ }),
  );
  const emailVisible = await isVisible(() =>
    appPage.locator("section, div, article").filter({ hasText: /@/ }),
  );
  const businessPlanVisible = await isVisible(() => appPage.getByText(/business plan/i));
  const cambiarPlanVisible = await isVisible(() =>
    appPage.getByRole("button", { name: /cambiar plan/i }),
  );

  if (userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible) {
    markPass(
      "Información General",
      "User name/email and plan controls are visible in Información General.",
    );
    return true;
  }

  markFail("Información General", "Missing one or more expected elements in Información General.");
  return false;
}

async function validateDetallesDeLaCuenta() {
  const cuentaCreadaVisible = await isVisible(() => appPage.getByText(/cuenta creada/i));
  const estadoActivoVisible = await isVisible(() => appPage.getByText(/estado activo/i));
  const idiomaVisible = await isVisible(() => appPage.getByText(/idioma seleccionado/i));

  if (cuentaCreadaVisible && estadoActivoVisible && idiomaVisible) {
    markPass(
      "Detalles de la Cuenta",
      "Cuenta creada, Estado activo, and Idioma seleccionado are visible.",
    );
    return true;
  }

  markFail(
    "Detalles de la Cuenta",
    "Missing one or more expected fields in Detalles de la Cuenta.",
  );
  return false;
}

async function validateTusNegocios() {
  const listVisible = await isVisible(() =>
    appPage.locator("section, div, article").filter({ hasText: /tus negocios/i }),
  );
  const addButtonVisible = await isVisible(() =>
    appPage.getByRole("button", { name: /agregar negocio/i }),
  );
  const quotaVisible = await isVisible(() => appPage.getByText(/tienes 2 de 3 negocios/i));

  if (listVisible && addButtonVisible && quotaVisible) {
    markPass(
      "Tus Negocios",
      "Business list, Agregar Negocio button and quota text are visible.",
    );
    return true;
  }

  markFail("Tus Negocios", "Missing business list, add button, or quota text in Tus Negocios.");
  return false;
}

async function validateLegalLink(options) {
  const { linkTextRegex, headingRegex, reportField, evidenceKey, screenshotName } = options;
  const popupPromise = appPage.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);

  const clicked = await clickFirstVisible(appPage, [
    roleCandidate("link", linkTextRegex),
    roleCandidate("button", linkTextRegex),
    textCandidate(linkTextRegex),
  ]);

  if (!clicked) {
    markFail(reportField, `Could not click "${linkTextRegex}" in legal section.`);
    return false;
  }

  const popupPage = await popupPromise;
  const legalPage = popupPage || appPage;
  await legalPage.bringToFront().catch(() => {});
  await waitForUi(legalPage);

  const headingVisible = await isVisible(() =>
    legalPage.getByRole("heading", { name: headingRegex }),
  );
  const legalContentVisible = await isVisible(() =>
    legalPage.locator("article, main, section, body").filter({ hasText: /\w{20,}/ }),
  );

  await takeScreenshot(legalPage, screenshotName);

  const finalUrl = legalPage.url();
  report.evidence.finalUrls[evidenceKey] = finalUrl;

  if (headingVisible && legalContentVisible) {
    markPass(reportField, `Legal page validated with expected heading. URL: ${finalUrl}`);
  } else {
    markFail(reportField, `Legal page missing heading/content validation. URL: ${finalUrl}`);
  }

  if (popupPage) {
    await popupPage.close().catch(() => {});
    await appPage.bringToFront().catch(() => {});
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded", timeout: 30_000 }).catch(() => {});
    await waitForUi(appPage);
  }

  return headingVisible && legalContentVisible;
}

async function finalizeReport() {
  report.completedAt = new Date().toISOString();
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  const printable = {};
  for (const field of REPORT_FIELDS) {
    printable[field] = report.summary[field].status;
  }

  // Keep console output concise and machine-friendly for CI logs.
  console.log("\nSaleADS Mi Negocio workflow validation summary:");
  console.table(printable);
  console.log(`Report file: ${reportPath}`);
  console.log(`Screenshots: ${screenshotsDir}`);
  console.log(`Final legal URLs: ${JSON.stringify(report.evidence.finalUrls, null, 2)}`);
}

async function main() {
  await ensureDirs();

  try {
    await initializeBrowser();
    await executeGoogleLogin();
    await openMiNegocioMenu();
    await validateAgregarNegocioModal();
    await openAdministrarNegocios();
    await validateInformacionGeneral();
    await validateDetallesDeLaCuenta();
    await validateTusNegocios();
    await validateLegalLink({
      linkTextRegex: /t[ée]rminos y condiciones/i,
      headingRegex: /t[ée]rminos y condiciones/i,
      reportField: "Términos y Condiciones",
      evidenceKey: "terminosYCondiciones",
      screenshotName: "terminos_y_condiciones",
    });
    await validateLegalLink({
      linkTextRegex: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      reportField: "Política de Privacidad",
      evidenceKey: "politicaDePrivacidad",
      screenshotName: "politica_de_privacidad",
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    for (const field of REPORT_FIELDS) {
      if (report.summary[field].status === "FAIL" && report.summary[field].details === "Not executed") {
        markFail(field, `Execution blocked before step could run: ${message}`);
      }
    }
    report.error = {
      message,
      stack: error instanceof Error ? error.stack : null,
    };
  } finally {
    await finalizeReport();

    const keepBrowserOpen = process.env.SALEADS_KEEP_BROWSER_OPEN === "true";
    if (!keepBrowserOpen) {
      await context?.close().catch(() => {});
      await browser?.close().catch(() => {});
    }
  }
}

main().catch(async (error) => {
  console.error("Fatal error:", error);
  process.exitCode = 1;
});
