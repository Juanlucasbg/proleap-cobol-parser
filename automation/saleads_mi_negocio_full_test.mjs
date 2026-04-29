import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const APP_URL = process.env.SALEADS_APP_URL || process.env.SALEADS_BASE_URL || process.env.APP_URL;
const CDP_ENDPOINT = process.env.SALEADS_CDP_ENDPOINT || process.env.PW_CDP_ENDPOINT;
const HEADLESS = (process.env.SALEADS_HEADLESS || "true").toLowerCase() === "true";
const ACTION_PAUSE_MS = Number(process.env.SALEADS_ACTION_PAUSE_MS || 900);
const DEFAULT_TIMEOUT_MS = Number(process.env.SALEADS_TIMEOUT_MS || 20000);
const RUN_ID = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACTS_DIR = path.resolve(
  "automation-artifacts",
  `saleads_mi_negocio_full_test_${RUN_ID}`
);

const report = {
  name: "saleads_mi_negocio_full_test",
  startedAt: new Date().toISOString(),
  config: {
    appUrl: APP_URL || null,
    cdpEndpointProvided: Boolean(CDP_ENDPOINT),
    headless: HEADLESS,
    googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
  },
  checkpoints: [],
  results: {
    Login: { status: "FAIL", details: [] },
    "Mi Negocio menu": { status: "FAIL", details: [] },
    "Agregar Negocio modal": { status: "FAIL", details: [] },
    "Administrar Negocios view": { status: "FAIL", details: [] },
    "Información General": { status: "FAIL", details: [] },
    "Detalles de la Cuenta": { status: "FAIL", details: [] },
    "Tus Negocios": { status: "FAIL", details: [] },
    "Términos y Condiciones": { status: "FAIL", details: [], finalUrl: null },
    "Política de Privacidad": { status: "FAIL", details: [], finalUrl: null },
  },
  finishedAt: null,
};

function log(msg) {
  console.log(`[saleads_mi_negocio_full_test] ${msg}`);
}

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
}

async function capture(page, name, fullPage = false) {
  const file = path.join(ARTIFACTS_DIR, name);
  await page.screenshot({ path: file, fullPage });
  report.checkpoints.push(file);
  log(`Screenshot: ${file}`);
}

async function updateResult(key, isPass, detail) {
  if (!report.results[key]) {
    report.results[key] = { status: "FAIL", details: [] };
  }
  report.results[key].details.push(detail);
  report.results[key].status = isPass ? "PASS" : "FAIL";
}

async function clickAndSettle(page, locator, label) {
  await locator.waitFor({ state: "visible", timeout: DEFAULT_TIMEOUT_MS });
  await locator.click();
  log(`Clicked: ${label}`);
  await page.waitForTimeout(ACTION_PAUSE_MS);
  try {
    await page.waitForLoadState("networkidle", { timeout: 5000 });
  } catch {
    // Not every UI action triggers a network-idle event.
  }
  await page.waitForTimeout(300);
}

async function expectVisible(locator, label, timeout = DEFAULT_TIMEOUT_MS) {
  try {
    await locator.waitFor({ state: "visible", timeout });
    log(`PASS visible: ${label}`);
    return true;
  } catch {
    log(`FAIL visible: ${label}`);
    return false;
  }
}

async function getFirstVisible(page, locators) {
  for (const locator of locators) {
    try {
      if (await locator.first().isVisible()) {
        return locator.first();
      }
    } catch {
      // Continue checking other candidates.
    }
  }
  return null;
}

async function clickGoogleLogin(page, context) {
  const googleButton = await getFirstVisible(page, [
    page.getByRole("button", {
      name: /sign in with google|iniciar sesión con google|continuar con google|google/i,
    }),
    page.getByRole("link", {
      name: /sign in with google|iniciar sesión con google|continuar con google|google/i,
    }),
    page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
  ]);

  if (!googleButton) {
    throw new Error("No login button or 'Sign in with Google' trigger was found.");
  }

  const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
  await clickAndSettle(page, googleButton, "Sign in with Google");
  const popup = await popupPromise;
  return popup;
}

async function chooseGoogleAccountIfPrompted(targetPage) {
  if (!targetPage) {
    return;
  }

  try {
    await targetPage.waitForLoadState("domcontentloaded", { timeout: DEFAULT_TIMEOUT_MS });
  } catch {
    // Continue with best effort.
  }

  const accountLocator = targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountLocator.isVisible().catch(() => false)) {
    await clickAndSettle(targetPage, accountLocator, `Google account ${GOOGLE_ACCOUNT_EMAIL}`);
    return;
  }

  const useAnotherAccount = targetPage
    .getByText(/use another account|usar otra cuenta/i)
    .first();
  if (await useAnotherAccount.isVisible().catch(() => false)) {
    await clickAndSettle(targetPage, useAnotherAccount, "Use another account");
    const emailInput = targetPage.locator('input[type="email"]').first();
    if (await emailInput.isVisible().catch(() => false)) {
      await emailInput.fill(GOOGLE_ACCOUNT_EMAIL);
      await targetPage.keyboard.press("Enter");
      await targetPage.waitForTimeout(ACTION_PAUSE_MS);
    }
  }
}

async function resolveApplicationPage(context, fallbackPage) {
  const pages = context.pages();
  const candidates = [...pages];
  if (fallbackPage && !candidates.includes(fallbackPage)) {
    candidates.push(fallbackPage);
  }

  for (const candidate of candidates) {
    try {
      await candidate.waitForTimeout(250);
      const hasMain = await candidate.locator("main, [role='main']").first().isVisible();
      const hasSidebarKeywords = await candidate
        .getByText(/negocio|mi negocio|administrar negocios/i)
        .first()
        .isVisible()
        .catch(() => false);

      if (hasMain || hasSidebarKeywords) {
        await candidate.bringToFront().catch(() => {});
        return candidate;
      }
    } catch {
      // Continue searching.
    }
  }

  return fallbackPage;
}

async function sectionByHeading(page, headingText) {
  const heading = page.getByText(new RegExp(`^${headingText}$`, "i")).first();
  if (!(await heading.isVisible().catch(() => false))) {
    return null;
  }

  const section = heading.locator("xpath=ancestor::*[self::section or self::div][1]");
  if (await section.isVisible().catch(() => false)) {
    return section;
  }
  return null;
}

async function validateInfoGeneral(page) {
  const key = "Información General";
  const section = await sectionByHeading(page, "Información General");
  if (!section) {
    await updateResult(key, false, "Section 'Información General' was not found.");
    return;
  }

  const sectionText = (await section.innerText()).replace(/\s+/g, " ").trim();
  const hasEmail = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(sectionText);
  const hasBusinessPlan = /BUSINESS PLAN/i.test(sectionText);
  const changePlanButtonVisible = await expectVisible(
    section.getByRole("button", { name: /cambiar plan/i }).first(),
    "Cambiar Plan button"
  );
  const hasLikelyName = sectionText
    .split(" ")
    .filter(Boolean)
    .some((token) => /^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ'-]{2,}$/.test(token));

  const allPass = hasEmail && hasBusinessPlan && changePlanButtonVisible && hasLikelyName;
  await updateResult(
    key,
    allPass,
    `nameVisible=${hasLikelyName}, emailVisible=${hasEmail}, businessPlanVisible=${hasBusinessPlan}, cambiarPlanVisible=${changePlanButtonVisible}`
  );
}

async function validateDetallesCuenta(page) {
  const key = "Detalles de la Cuenta";
  const section = await sectionByHeading(page, "Detalles de la Cuenta");
  if (!section) {
    await updateResult(key, false, "Section 'Detalles de la Cuenta' was not found.");
    return;
  }

  const hasCuentaCreada = await expectVisible(
    section.getByText(/cuenta creada/i).first(),
    "Cuenta creada"
  );
  const hasEstadoActivo = await expectVisible(
    section.getByText(/estado activo/i).first(),
    "Estado activo"
  );
  const hasIdioma = await expectVisible(
    section.getByText(/idioma seleccionado/i).first(),
    "Idioma seleccionado"
  );

  await updateResult(
    key,
    hasCuentaCreada && hasEstadoActivo && hasIdioma,
    `cuentaCreada=${hasCuentaCreada}, estadoActivo=${hasEstadoActivo}, idiomaSeleccionado=${hasIdioma}`
  );
}

async function validateTusNegocios(page) {
  const key = "Tus Negocios";
  const section = await sectionByHeading(page, "Tus Negocios");
  if (!section) {
    await updateResult(key, false, "Section 'Tus Negocios' was not found.");
    return;
  }

  const addButton = await expectVisible(
    section.getByRole("button", { name: /agregar negocio/i }).first(),
    "Agregar Negocio button in Tus Negocios"
  );
  const quotaText = await expectVisible(
    section.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first(),
    "Tienes 2 de 3 negocios text"
  );

  const listLikeItemsCount = await section
    .locator("li, [role='listitem'], table tbody tr, article, .business-card")
    .count()
    .catch(() => 0);
  const hasBusinessList = listLikeItemsCount > 0 || /negocio/i.test(await section.innerText());

  await updateResult(
    key,
    addButton && quotaText && hasBusinessList,
    `businessListVisible=${hasBusinessList}, agregarNegocioVisible=${addButton}, quotaTextVisible=${quotaText}`
  );
}

async function openAndValidateLegalLink({
  page,
  context,
  resultKey,
  linkTextRegex,
  headingRegex,
  screenshotFile,
}) {
  const link = page.getByRole("link", { name: linkTextRegex }).first();
  const clicked = await expectVisible(link, `${resultKey} link`);
  if (!clicked) {
    await updateResult(resultKey, false, "Legal link was not visible.");
    return page;
  }

  const originalPage = page;
  const originalUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await clickAndSettle(page, link, resultKey);

  const popupPage = await popupPromise;
  const legalPage = popupPage || page;

  try {
    await legalPage.waitForLoadState("domcontentloaded", { timeout: DEFAULT_TIMEOUT_MS });
  } catch {
    // Continue best effort.
  }
  await legalPage.waitForTimeout(ACTION_PAUSE_MS);

  const headingVisible = await expectVisible(
    legalPage.getByText(headingRegex).first(),
    `${resultKey} heading`
  );
  const contentVisible =
    (await legalPage.locator("p, article, section").first().isVisible().catch(() => false)) ||
    (await legalPage.locator("body").innerText().then((t) => t.trim().length > 120).catch(() => false));
  if (!contentVisible) {
    log(`FAIL visible: ${resultKey} legal content`);
  } else {
    log(`PASS visible: ${resultKey} legal content`);
  }

  await capture(legalPage, screenshotFile, true);
  const finalUrl = legalPage.url();

  report.results[resultKey].finalUrl = finalUrl;
  await updateResult(
    resultKey,
    headingVisible && contentVisible,
    `headingVisible=${headingVisible}, legalContentVisible=${contentVisible}, finalUrl=${finalUrl}`
  );

  if (popupPage) {
    await popupPage.close({ runBeforeUnload: true }).catch(() => {});
    await originalPage.bringToFront();
    await originalPage.waitForTimeout(ACTION_PAUSE_MS);
    return originalPage;
  }

  if (legalPage.url() !== originalUrl) {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await legalPage.waitForTimeout(ACTION_PAUSE_MS);
  }

  return legalPage;
}

function printFinalReport() {
  const order = [
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

  console.log("\n=== FINAL REPORT: saleads_mi_negocio_full_test ===");
  for (const key of order) {
    const entry = report.results[key];
    console.log(`- ${key}: ${entry.status}`);
    for (const detail of entry.details) {
      console.log(`  • ${detail}`);
    }
    if (entry.finalUrl) {
      console.log(`  • finalUrl=${entry.finalUrl}`);
    }
  }
}

async function writeReportFiles() {
  const reportPath = path.join(ARTIFACTS_DIR, "report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  const summaryPath = path.join(ARTIFACTS_DIR, "report.txt");
  const lines = ["saleads_mi_negocio_full_test", ""];
  for (const [key, value] of Object.entries(report.results)) {
    lines.push(`${key}: ${value.status}`);
    for (const detail of value.details) {
      lines.push(`  - ${detail}`);
    }
    if (value.finalUrl) {
      lines.push(`  - finalUrl=${value.finalUrl}`);
    }
  }
  lines.push("", `artifactsDir=${ARTIFACTS_DIR}`);
  await fs.writeFile(summaryPath, `${lines.join("\n")}\n`, "utf8");
}

async function main() {
  await ensureArtifactsDir();

  let browser;
  let context;
  let page;

  try {
    if (CDP_ENDPOINT) {
      log(`Connecting over CDP endpoint: ${CDP_ENDPOINT}`);
      browser = await chromium.connectOverCDP(CDP_ENDPOINT);
      context = browser.contexts()[0] || (await browser.newContext());
      page = context.pages()[0] || (await context.newPage());
    } else {
      browser = await chromium.launch({
        headless: HEADLESS,
        slowMo: Number(process.env.SALEADS_SLOW_MO || 50),
      });
      context = await browser.newContext();
      page = await context.newPage();
    }

    page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

    if (APP_URL) {
      log(`Navigating to login page: ${APP_URL}`);
      await page.goto(APP_URL, { waitUntil: "domcontentloaded" });
      await page.waitForTimeout(ACTION_PAUSE_MS);
    } else {
      if (!CDP_ENDPOINT) {
        throw new Error(
          "Missing startup target. Set SALEADS_APP_URL (or SALEADS_BASE_URL / APP_URL) to navigate to login, or set SALEADS_CDP_ENDPOINT to attach to an already-open browser page."
        );
      }
      log("No explicit app URL provided; continuing with attached browser's current page.");
    }

    // Step 1: Login with Google
    let popupPage = await clickGoogleLogin(page, context);
    if (popupPage) {
      await chooseGoogleAccountIfPrompted(popupPage);
      await popupPage.waitForTimeout(ACTION_PAUSE_MS);
    } else if (/accounts\.google\.com/i.test(page.url())) {
      await chooseGoogleAccountIfPrompted(page);
    }
    page = await resolveApplicationPage(context, page);

    await page.waitForTimeout(1500);
    const mainUiVisible = await expectVisible(
      page.locator("main, [role='main']").first(),
      "Main app interface"
    );
    const sidebarVisible =
      (await page
        .locator("aside, nav")
        .first()
        .isVisible()
        .catch(() => false)) &&
      (await page.getByText(/negocio|mi negocio/i).first().isVisible().catch(() => false));

    await capture(page, "01_dashboard_loaded.png", true);
    await updateResult(
      "Login",
      mainUiVisible && sidebarVisible,
      `mainInterfaceVisible=${mainUiVisible}, leftSidebarVisible=${sidebarVisible}`
    );

    // Step 2: Open Mi Negocio menu
    const negocioMenu = await getFirstVisible(page, [
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
    ]);
    if (negocioMenu) {
      await clickAndSettle(page, negocioMenu, "Negocio");
    }

    const miNegocio = await getFirstVisible(page, [
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
    ]);
    if (!miNegocio) {
      throw new Error("Could not find 'Mi Negocio' option in sidebar.");
    }
    await clickAndSettle(page, miNegocio, "Mi Negocio");

    const agregarNegocioVisible = await expectVisible(
      page.getByText(/^Agregar Negocio$/i).first(),
      "Agregar Negocio in submenu"
    );
    const administrarNegociosVisible = await expectVisible(
      page.getByText(/^Administrar Negocios$/i).first(),
      "Administrar Negocios in submenu"
    );
    await capture(page, "02_mi_negocio_menu_expanded.png", true);
    await updateResult(
      "Mi Negocio menu",
      agregarNegocioVisible && administrarNegociosVisible,
      `submenuExpanded=true, agregarNegocioVisible=${agregarNegocioVisible}, administrarNegociosVisible=${administrarNegociosVisible}`
    );

    // Step 3: Validate Agregar Negocio modal
    await clickAndSettle(page, page.getByText(/^Agregar Negocio$/i).first(), "Agregar Negocio");
    const modal = page.getByRole("dialog").first();
    const modalVisible = await expectVisible(modal, "Crear Nuevo Negocio modal");
    const modalTitleVisible = await expectVisible(
      modal.getByText(/crear nuevo negocio/i).first(),
      "Modal title Crear Nuevo Negocio"
    );
    const negocioInputVisible = await expectVisible(
      modal.getByLabel(/nombre del negocio/i).first(),
      "Nombre del Negocio input"
    );
    const quotaVisible = await expectVisible(
      modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first(),
      "Tienes 2 de 3 negocios text in modal"
    );
    const cancelVisible = await expectVisible(
      modal.getByRole("button", { name: /cancelar/i }).first(),
      "Cancelar button in modal"
    );
    const createVisible = await expectVisible(
      modal.getByRole("button", { name: /crear negocio/i }).first(),
      "Crear Negocio button in modal"
    );

    if (negocioInputVisible) {
      const input = modal.getByLabel(/nombre del negocio/i).first();
      await input.click();
      await input.fill("Negocio Prueba Automatización");
      await page.waitForTimeout(ACTION_PAUSE_MS);
    }

    await capture(page, "03_agregar_negocio_modal.png", true);

    if (cancelVisible) {
      await clickAndSettle(
        page,
        modal.getByRole("button", { name: /cancelar/i }).first(),
        "Cancelar modal"
      );
      await modal.waitFor({ state: "hidden", timeout: DEFAULT_TIMEOUT_MS }).catch(() => {});
    }

    await updateResult(
      "Agregar Negocio modal",
      modalVisible &&
        modalTitleVisible &&
        negocioInputVisible &&
        quotaVisible &&
        cancelVisible &&
        createVisible,
      `modalVisible=${modalVisible}, titleVisible=${modalTitleVisible}, inputVisible=${negocioInputVisible}, quotaVisible=${quotaVisible}, cancelarVisible=${cancelVisible}, crearNegocioVisible=${createVisible}`
    );

    // Step 4: Open Administrar Negocios
    const miNegocioAgain = await getFirstVisible(page, [
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
    ]);
    if (miNegocioAgain) {
      await clickAndSettle(page, miNegocioAgain, "Mi Negocio (expand again if needed)");
    }
    await clickAndSettle(
      page,
      page.getByText(/^Administrar Negocios$/i).first(),
      "Administrar Negocios"
    );

    const infoGeneralVisible = await expectVisible(
      page.getByText(/^Información General$/i).first(),
      "Información General section"
    );
    const detallesCuentaVisible = await expectVisible(
      page.getByText(/^Detalles de la Cuenta$/i).first(),
      "Detalles de la Cuenta section"
    );
    const tusNegociosVisible = await expectVisible(
      page.getByText(/^Tus Negocios$/i).first(),
      "Tus Negocios section"
    );
    const seccionLegalVisible = await expectVisible(
      page.getByText(/^Sección Legal$/i).first(),
      "Sección Legal section"
    );

    await capture(page, "04_administrar_negocios_view.png", true);
    await updateResult(
      "Administrar Negocios view",
      infoGeneralVisible && detallesCuentaVisible && tusNegociosVisible && seccionLegalVisible,
      `informacionGeneral=${infoGeneralVisible}, detallesCuenta=${detallesCuentaVisible}, tusNegocios=${tusNegociosVisible}, seccionLegal=${seccionLegalVisible}`
    );

    // Step 5-7: Section validations
    await validateInfoGeneral(page);
    await validateDetallesCuenta(page);
    await validateTusNegocios(page);

    // Step 8: Términos y Condiciones
    page = await openAndValidateLegalLink({
      page,
      context,
      resultKey: "Términos y Condiciones",
      linkTextRegex: /términos y condiciones|terminos y condiciones/i,
      headingRegex: /términos y condiciones|terminos y condiciones/i,
      screenshotFile: "05_terminos_y_condiciones.png",
    });

    // Step 9: Política de Privacidad
    page = await openAndValidateLegalLink({
      page,
      context,
      resultKey: "Política de Privacidad",
      linkTextRegex: /política de privacidad|politica de privacidad/i,
      headingRegex: /política de privacidad|politica de privacidad/i,
      screenshotFile: "06_politica_de_privacidad.png",
    });
  } catch (error) {
    log(`Execution error: ${error.message}`);
    if (page) {
      await capture(page, "error_state.png", true).catch(() => {});
    }
  } finally {
    report.finishedAt = new Date().toISOString();
    await writeReportFiles();
    printFinalReport();

    if (browser) {
      await browser.close().catch(() => {});
    }
  }

  const hasFailures = Object.values(report.results).some((entry) => entry.status !== "PASS");
  if (hasFailures) {
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
