const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const ARTIFACTS_DIR = path.join(__dirname, "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "final-report.json");

const STEP_FIELDS = [
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

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function ensureArtifacts() {
  await fs.mkdir(SCREENSHOTS_DIR, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 30000 });
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch (_) {
    // Some applications keep long polling active; DOM loaded is enough fallback.
  }
}

async function capture(page, name) {
  const filePath = path.join(SCREENSHOTS_DIR, `${nowStamp()}-${name}.png`);
  await page.screenshot({ path: filePath, fullPage: true });
  return filePath;
}

function initialReport() {
  const summary = {};
  for (const field of STEP_FIELDS) {
    summary[field] = {
      status: "FAIL",
      checks: [],
      evidence: []
    };
  }

  return {
    startedAt: new Date().toISOString(),
    environment: {
      loginUrl: process.env.SALEADS_LOGIN_URL || null,
      cdpUrl: process.env.SALEADS_CDP_URL || null,
      headless: process.env.HEADLESS !== "false"
    },
    summary
  };
}

async function visible(locator, timeout = 8000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch (_) {
    return false;
  }
}

async function clickWhenVisible(page, locator, timeout = 15000) {
  await locator.first().waitFor({ state: "visible", timeout });
  await locator.first().click();
  await waitForUi(page);
}

function markCheck(report, section, check, pass, details = null) {
  report.summary[section].checks.push({
    check,
    status: pass ? "PASS" : "FAIL",
    details
  });
}

function finalizeSection(report, section) {
  const allPass = report.summary[section].checks.every((c) => c.status === "PASS");
  report.summary[section].status = allPass ? "PASS" : "FAIL";
}

async function loginStep(page, report) {
  const section = "Login";
  const googleLoginButton = page
    .locator("button, a, [role='button']")
    .filter({ hasText: /google|iniciar sesión|sign in/i });

  const loginButtonVisible = await visible(googleLoginButton, 15000);
  markCheck(report, section, "Login button or 'Sign in with Google' is visible", loginButtonVisible);
  if (!loginButtonVisible) {
    const appMainVisible =
      (await visible(page.locator("main"), 12000)) ||
      (await visible(page.getByText(/dashboard|inicio|negocio|mi negocio/i), 12000));
    const sidebarVisible =
      (await visible(page.locator("aside"), 8000)) ||
      (await visible(page.locator("nav"), 8000)) ||
      (await visible(page.getByText(/negocio|mi negocio/i), 8000));

    markCheck(
      report,
      section,
      "Main application interface appears",
      appMainVisible,
      appMainVisible ? "Already logged in session was detected." : null
    );
    markCheck(report, section, "Left sidebar navigation is visible", sidebarVisible);
    if (appMainVisible && sidebarVisible) {
      report.summary[section].evidence.push(await capture(page, "01-dashboard-loaded"));
    }
    finalizeSection(report, section);
    return;
  }

  await clickWhenVisible(page, googleLoginButton);

  const accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
  if (await visible(accountOption, 8000)) {
    await clickWhenVisible(page, accountOption);
  }

  const appMainVisible =
    (await visible(page.locator("main"), 20000)) ||
    (await visible(page.getByText(/dashboard|inicio|negocio|mi negocio/i), 20000));
  markCheck(report, section, "Main application interface appears", appMainVisible);

  const sidebarVisible =
    (await visible(page.locator("aside"), 10000)) ||
    (await visible(page.locator("nav"), 10000)) ||
    (await visible(page.getByText(/negocio|mi negocio/i), 10000));
  markCheck(report, section, "Left sidebar navigation is visible", sidebarVisible);

  if (appMainVisible && sidebarVisible) {
    report.summary[section].evidence.push(await capture(page, "01-dashboard-loaded"));
  }
  finalizeSection(report, section);
}

async function miNegocioMenuStep(page, report) {
  const section = "Mi Negocio menu";
  const negocioMenu = page.getByText("Negocio", { exact: true });
  const miNegocioOption = page.getByText("Mi Negocio", { exact: true });

  if (await visible(negocioMenu, 7000)) {
    await clickWhenVisible(page, negocioMenu);
  }

  const miNegocioVisible = await visible(miNegocioOption, 10000);
  markCheck(report, section, "'Mi Negocio' option is visible", miNegocioVisible);
  if (miNegocioVisible) {
    await clickWhenVisible(page, miNegocioOption);
  }

  const agregarVisible = await visible(page.getByText("Agregar Negocio", { exact: true }), 10000);
  markCheck(report, section, "'Agregar Negocio' is visible", agregarVisible);

  const administrarVisible = await visible(page.getByText("Administrar Negocios", { exact: true }), 10000);
  markCheck(report, section, "'Administrar Negocios' is visible", administrarVisible);

  markCheck(report, section, "Submenu expands", agregarVisible && administrarVisible);
  if (agregarVisible && administrarVisible) {
    report.summary[section].evidence.push(await capture(page, "02-mi-negocio-menu-expanded"));
  }
  finalizeSection(report, section);
}

async function agregarNegocioModalStep(page, report) {
  const section = "Agregar Negocio modal";

  const addFromMenu = page.getByText("Agregar Negocio", { exact: true }).first();
  if (await visible(addFromMenu, 10000)) {
    await clickWhenVisible(page, addFromMenu);
  }

  const modalTitle = page.getByText("Crear Nuevo Negocio", { exact: true });
  const modalVisible = await visible(modalTitle, 10000);
  markCheck(report, section, "Modal title 'Crear Nuevo Negocio' is visible", modalVisible);

  const nameField =
    page.getByLabel("Nombre del Negocio", { exact: true }).first().or(
      page.getByPlaceholder(/Nombre del Negocio/i).first()
    );
  markCheck(report, section, "Input field 'Nombre del Negocio' exists", await visible(nameField, 6000));

  const limitsText = page.getByText("Tienes 2 de 3 negocios", { exact: true });
  markCheck(report, section, "Text 'Tienes 2 de 3 negocios' is visible", await visible(limitsText, 6000));

  const cancelButton = page.getByRole("button", { name: "Cancelar" });
  const createButton = page.getByRole("button", { name: "Crear Negocio" });
  markCheck(report, section, "Button 'Cancelar' is present", await visible(cancelButton, 6000));
  markCheck(report, section, "Button 'Crear Negocio' is present", await visible(createButton, 6000));

  if (modalVisible) {
    report.summary[section].evidence.push(await capture(page, "03-agregar-negocio-modal"));
  }

  if (await visible(nameField, 3000)) {
    await nameField.fill("Negocio Prueba Automatización");
  }
  if (await visible(cancelButton, 3000)) {
    await clickWhenVisible(page, cancelButton);
  }

  finalizeSection(report, section);
}

async function administrarNegociosViewStep(page, report) {
  const section = "Administrar Negocios view";
  const miNegocioOption = page.getByText("Mi Negocio", { exact: true });
  if (await visible(miNegocioOption, 6000)) {
    await clickWhenVisible(page, miNegocioOption);
  }

  const administrar = page.getByText("Administrar Negocios", { exact: true });
  if (await visible(administrar, 10000)) {
    await clickWhenVisible(page, administrar);
  }

  const infoGeneral = page.getByText("Información General", { exact: true });
  const detallesCuenta = page.getByText("Detalles de la Cuenta", { exact: true });
  const tusNegocios = page.getByText("Tus Negocios", { exact: true });
  const seccionLegal = page.getByText("Sección Legal", { exact: true });

  markCheck(report, section, "Section 'Información General' exists", await visible(infoGeneral, 15000));
  markCheck(report, section, "Section 'Detalles de la Cuenta' exists", await visible(detallesCuenta, 15000));
  markCheck(report, section, "Section 'Tus Negocios' exists", await visible(tusNegocios, 15000));
  markCheck(report, section, "Section 'Sección Legal' exists", await visible(seccionLegal, 15000));

  if (report.summary[section].checks.every((c) => c.status === "PASS")) {
    report.summary[section].evidence.push(await capture(page, "04-administrar-negocios-account-page"));
  }
  finalizeSection(report, section);
}

async function informacionGeneralStep(page, report) {
  const section = "Información General";
  markCheck(report, section, "User name is visible", await visible(page.locator("section,div").getByText(/@|[A-Z][a-z]+/).first(), 8000));
  markCheck(report, section, "User email is visible", await visible(page.getByText(/.+@.+\..+/), 8000));
  markCheck(report, section, "Text 'BUSINESS PLAN' is visible", await visible(page.getByText("BUSINESS PLAN", { exact: true }), 8000));
  markCheck(report, section, "Button 'Cambiar Plan' is visible", await visible(page.getByRole("button", { name: "Cambiar Plan" }), 8000));
  finalizeSection(report, section);
}

async function detallesCuentaStep(page, report) {
  const section = "Detalles de la Cuenta";
  markCheck(report, section, "'Cuenta creada' is visible", await visible(page.getByText("Cuenta creada", { exact: true }), 8000));
  markCheck(report, section, "'Estado activo' is visible", await visible(page.getByText("Estado activo", { exact: true }), 8000));
  markCheck(report, section, "'Idioma seleccionado' is visible", await visible(page.getByText("Idioma seleccionado", { exact: true }), 8000));
  finalizeSection(report, section);
}

async function tusNegociosStep(page, report) {
  const section = "Tus Negocios";
  markCheck(report, section, "Business list is visible", await visible(page.locator("ul, table, div").filter({ hasText: /Negocio|business/i }).first(), 10000));
  markCheck(report, section, "Button 'Agregar Negocio' exists", await visible(page.getByRole("button", { name: "Agregar Negocio" }), 8000));
  markCheck(report, section, "Text 'Tienes 2 de 3 negocios' is visible", await visible(page.getByText("Tienes 2 de 3 negocios", { exact: true }), 8000));
  finalizeSection(report, section);
}

async function openLegalLink(page, context, label) {
  const target = page.getByText(label, { exact: true });
  await target.first().waitFor({ state: "visible", timeout: 10000 });

  const newPagePromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await target.first().click();
  const newPage = await newPagePromise;

  if (newPage) {
    await newPage.waitForLoadState("domcontentloaded", { timeout: 30000 });
    try {
      await newPage.waitForLoadState("networkidle", { timeout: 10000 });
    } catch (_) {
      // no-op
    }
    return { legalPage: newPage, openedInNewTab: true };
  }

  await waitForUi(page);
  return { legalPage: page, openedInNewTab: false };
}

async function closeLegalAndReturn(appPage, legalPage, openedInNewTab) {
  if (openedInNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUi(appPage);
}

async function legalStep(page, context, report, section, linkText, headingText, screenshotName) {
  try {
    const { legalPage, openedInNewTab } = await openLegalLink(page, context, linkText);
    markCheck(report, section, `Heading '${headingText}' is visible`, await visible(legalPage.getByText(headingText, { exact: true }), 12000));
    markCheck(report, section, "Legal content text is visible", await visible(legalPage.locator("p, div, section").filter({ hasText: /\w{20,}/ }).first(), 12000));
    report.summary[section].evidence.push(await capture(legalPage, screenshotName));
    report.summary[section].evidence.push(`Final URL: ${legalPage.url()}`);
    finalizeSection(report, section);
    await closeLegalAndReturn(page, legalPage, openedInNewTab);
  } catch (error) {
    markCheck(report, section, `Open '${linkText}' and validate content`, false, String(error));
    finalizeSection(report, section);
  }
}

async function buildContext() {
  const cdpUrl = process.env.SALEADS_CDP_URL;
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const headless = process.env.HEADLESS !== "false";

  if (cdpUrl) {
    const browser = await chromium.connectOverCDP(cdpUrl);
    const context = browser.contexts()[0];
    if (!context) {
      throw new Error("Connected over CDP but no browser context was found.");
    }
    const page = context.pages()[0];
    if (!page) {
      throw new Error("Connected over CDP but no open page was found.");
    }
    return { browser, context, page, shouldCloseBrowser: false };
  }

  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL (environment-agnostic) or SALEADS_CDP_URL (attach to existing browser on login page)."
    );
  }

  const browser = await chromium.launch({ headless });
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
  await waitForUi(page);
  return { browser, context, page, shouldCloseBrowser: true };
}

async function run() {
  await ensureArtifacts();
  const report = initialReport();
  let browser = null;
  let shouldCloseBrowser = true;

  try {
    const built = await buildContext();
    browser = built.browser;
    shouldCloseBrowser = built.shouldCloseBrowser;
    const { context, page } = built;

    await loginStep(page, report);
    await miNegocioMenuStep(page, report);
    await agregarNegocioModalStep(page, report);
    await administrarNegociosViewStep(page, report);
    await informacionGeneralStep(page, report);
    await detallesCuentaStep(page, report);
    await tusNegociosStep(page, report);
    await legalStep(
      page,
      context,
      report,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "08-terminos-y-condiciones"
    );
    await legalStep(
      page,
      context,
      report,
      "Política de Privacidad",
      "Política de Privacidad",
      "09-politica-de-privacidad"
    );
  } catch (error) {
    report.fatalError = String(error);
  } finally {
    report.finishedAt = new Date().toISOString();
    await fs.writeFile(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
    if (browser && shouldCloseBrowser) {
      await browser.close();
    }
  }

  const failed = Object.values(report.summary).filter((s) => s.status === "FAIL").length;
  console.log(`Report saved to: ${REPORT_PATH}`);
  console.log(`Failed sections: ${failed}/${STEP_FIELDS.length}`);
  process.exitCode = failed > 0 ? 1 : 0;
}

run().catch(async (error) => {
  const report = initialReport();
  report.fatalError = String(error);
  report.finishedAt = new Date().toISOString();
  await ensureArtifacts();
  await fs.writeFile(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
  console.error(error);
  process.exit(1);
});
