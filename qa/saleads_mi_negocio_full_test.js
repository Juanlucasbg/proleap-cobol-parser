#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const DEFAULT_TIMEOUT_MS = Number(process.env.SALEADS_TIMEOUT_MS || 25000);
const RUN_ID = new Date().toISOString().replaceAll(":", "-").replaceAll(".", "-");
const RUN_DIR = path.resolve(
  process.cwd(),
  "artifacts",
  TEST_NAME,
  RUN_ID
);
const SCREENSHOTS_DIR = path.join(RUN_DIR, "screenshots");
const REPORT_PATH = path.join(RUN_DIR, "report.json");

const FINAL_FIELDS = [
  { key: "login", label: "Login" },
  { key: "miNegocioMenu", label: "Mi Negocio menu" },
  { key: "agregarNegocioModal", label: "Agregar Negocio modal" },
  { key: "administrarNegociosView", label: "Administrar Negocios view" },
  { key: "informacionGeneral", label: "Información General" },
  { key: "detallesCuenta", label: "Detalles de la Cuenta" },
  { key: "tusNegocios", label: "Tus Negocios" },
  { key: "terminosCondiciones", label: "Términos y Condiciones" },
  { key: "politicaPrivacidad", label: "Política de Privacidad" },
];

function mkdirp(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function nowIso() {
  return new Date().toISOString();
}

function normalizeWhitespace(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
}

function boolToStatus(value) {
  return value ? "PASS" : "FAIL";
}

function createStep(id, name) {
  return {
    id,
    name,
    startedAt: nowIso(),
    finishedAt: null,
    status: "PASS",
    actions: [],
    validations: [],
    evidence: [],
    notes: [],
  };
}

function logAction(step, text) {
  step.actions.push({ at: nowIso(), text });
}

function logNote(step, text) {
  step.notes.push({ at: nowIso(), text });
}

function logValidation(step, name, passed, details = "") {
  step.validations.push({
    at: nowIso(),
    name,
    status: boolToStatus(passed),
    details: normalizeWhitespace(details),
  });
  if (!passed) {
    step.status = "FAIL";
  }
}

function finishStep(step) {
  step.finishedAt = nowIso();
}

async function waitForUi(page, reason = "ui sync") {
  await page.waitForTimeout(350);
  await page.waitForLoadState("domcontentloaded", { timeout: 8000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(350);
  return reason;
}

async function isVisible(locator, timeout = 2500) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch (error) {
    return false;
  }
}

async function clickFirstVisible(candidates, step, actionName) {
  for (const candidate of candidates) {
    const locator = candidate();
    if (await isVisible(locator)) {
      logAction(step, `${actionName}: clicking visible target`);
      await locator.first().click({ timeout: DEFAULT_TIMEOUT_MS });
      await waitForUi(locator.page(), actionName);
      return true;
    }
  }
  logNote(step, `${actionName}: no visible candidate matched`);
  return false;
}

async function capture(page, step, fileName, fullPage = false) {
  const screenshotPath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  step.evidence.push({
    at: nowIso(),
    type: "screenshot",
    path: screenshotPath,
  });
}

async function verifyAnyVisible(validators) {
  for (const validator of validators) {
    if (await validator()) {
      return true;
    }
  }
  return false;
}

function extractContentPreview(rawText) {
  return normalizeWhitespace(rawText).slice(0, 220);
}

async function run() {
  mkdirp(SCREENSHOTS_DIR);

  const report = {
    name: TEST_NAME,
    startedAt: nowIso(),
    environment: {
      loginUrlEnv: process.env.SALEADS_LOGIN_URL || null,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      nodeVersion: process.version,
    },
    steps: [],
    finalReport: {},
    finalUrls: {
      terminosCondiciones: null,
      politicaPrivacidad: null,
    },
    status: "PASS",
    error: null,
  };

  for (const field of FINAL_FIELDS) {
    report.finalReport[field.label] = "FAIL";
  }

  let browser;
  let context;
  let page;
  let applicationPage;

  const step1 = createStep(1, "Login with Google");
  report.steps.push(step1);

  try {
    browser = await chromium.launch({
      headless: process.env.HEADLESS !== "false",
      slowMo: Number(process.env.SALEADS_SLOWMO_MS || 0),
    });
    context = await browser.newContext();
    page = await context.newPage();

    if (process.env.SALEADS_LOGIN_URL) {
      logAction(step1, `Navigating to login URL from env: ${process.env.SALEADS_LOGIN_URL}`);
      await page.goto(process.env.SALEADS_LOGIN_URL, {
        waitUntil: "domcontentloaded",
        timeout: DEFAULT_TIMEOUT_MS,
      });
      await waitForUi(page, "initial login page load");
    } else {
      logNote(
        step1,
        "SALEADS_LOGIN_URL is not set. Attempting to proceed from current page as requested."
      );
    }

    const currentUrl = page.url();
    if (currentUrl === "about:blank" && !process.env.SALEADS_LOGIN_URL) {
      throw new Error(
        "Cannot continue from about:blank without SALEADS_LOGIN_URL. Set SALEADS_LOGIN_URL to the active environment login page."
      );
    }

    const possibleGooglePopup = context
      .waitForEvent("page", { timeout: 12000 })
      .catch(() => null);

    const clickedGoogleButton = await clickFirstVisible(
      [
        () => page.getByRole("button", { name: /sign in with google|continuar con google|google/i }),
        () => page.getByRole("link", { name: /sign in with google|continuar con google|google/i }),
        () => page.getByText(/sign in with google|continuar con google|google/i),
      ],
      step1,
      "Click Google login"
    );

    logValidation(
      step1,
      "Login button or 'Sign in with Google' clicked",
      clickedGoogleButton,
      clickedGoogleButton ? "Google login trigger clicked." : "No login trigger found by visible text."
    );

    const popupPage = await possibleGooglePopup;
    if (popupPage) {
      logAction(step1, "Google popup detected");
      await popupPage.waitForLoadState("domcontentloaded", { timeout: DEFAULT_TIMEOUT_MS }).catch(() => {});

      const chooseKnownAccount = await clickFirstVisible(
        [
          () => popupPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
          () => popupPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
          () => popupPage.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        ],
        step1,
        "Choose Google account"
      );

      logValidation(
        step1,
        `Google account selector includes ${GOOGLE_ACCOUNT_EMAIL}`,
        chooseKnownAccount,
        chooseKnownAccount
          ? "Configured account selected."
          : "Account selector did not present the configured email in visible text."
      );

      await popupPage.waitForTimeout(1000);
    } else {
      logNote(step1, "No popup detected; login may have happened in same tab.");
    }

    applicationPage = page;
    await waitForUi(applicationPage, "post-login ui");

    const hasMainInterface = await verifyAnyVisible([
      () => isVisible(applicationPage.getByText(/dashboard|inicio|panel/i), 7000),
      () => isVisible(applicationPage.locator("main"), 7000),
    ]);
    const hasSidebar = await verifyAnyVisible([
      () => isVisible(applicationPage.locator("aside"), 7000),
      () => isVisible(applicationPage.locator("nav"), 7000),
      () => isVisible(applicationPage.getByText(/mi negocio|negocio/i), 7000),
    ]);

    logValidation(step1, "Main application interface appears", hasMainInterface);
    logValidation(step1, "Left sidebar navigation is visible", hasSidebar);
    await capture(applicationPage, step1, "01-dashboard-loaded.png", true);

    finishStep(step1);
    report.finalReport["Login"] = step1.status;

    const step2 = createStep(2, "Open Mi Negocio menu");
    report.steps.push(step2);

    const clickedMiNegocio = await clickFirstVisible(
      [
        () => applicationPage.getByText(/^Mi Negocio$/i),
        () => applicationPage.getByRole("button", { name: /mi negocio/i }),
        () => applicationPage.getByRole("link", { name: /mi negocio/i }),
        () => applicationPage.getByText(/^Negocio$/i),
      ],
      step2,
      "Open Mi Negocio"
    );
    logValidation(step2, "Mi Negocio entry clicked", clickedMiNegocio);

    const agregarNegocioVisible = await isVisible(
      applicationPage.getByText(/agregar negocio/i),
      7000
    );
    const administrarNegociosVisible = await isVisible(
      applicationPage.getByText(/administrar negocios/i),
      7000
    );
    logValidation(step2, "Submenu expanded", agregarNegocioVisible || administrarNegociosVisible);
    logValidation(step2, "'Agregar Negocio' is visible", agregarNegocioVisible);
    logValidation(step2, "'Administrar Negocios' is visible", administrarNegociosVisible);
    await capture(applicationPage, step2, "02-mi-negocio-expanded-menu.png", true);

    finishStep(step2);
    report.finalReport["Mi Negocio menu"] = step2.status;

    const step3 = createStep(3, "Validate Agregar Negocio modal");
    report.steps.push(step3);

    const clickedAgregarNegocio = await clickFirstVisible(
      [
        () => applicationPage.getByText(/^Agregar Negocio$/i),
        () => applicationPage.getByRole("button", { name: /^Agregar Negocio$/i }),
        () => applicationPage.getByRole("link", { name: /^Agregar Negocio$/i }),
      ],
      step3,
      "Open Agregar Negocio modal"
    );
    logValidation(step3, "Clicked 'Agregar Negocio'", clickedAgregarNegocio);

    const modalTitle = applicationPage.getByText(/crear nuevo negocio/i);
    const modalVisible = await isVisible(modalTitle, 7000);
    logValidation(step3, "Modal title 'Crear Nuevo Negocio' is visible", modalVisible);

    const negocioInputExists = await verifyAnyVisible([
      () => isVisible(applicationPage.getByLabel(/nombre del negocio/i), 3000),
      () => isVisible(applicationPage.getByPlaceholder(/nombre del negocio/i), 3000),
      () => isVisible(applicationPage.locator("input[name*=negocio i]"), 3000),
    ]);
    const limitTextVisible = await isVisible(
      applicationPage.getByText(/tienes\s*2\s*de\s*3\s*negocios/i),
      5000
    );
    const cancelVisible = await isVisible(applicationPage.getByRole("button", { name: /cancelar/i }), 5000);
    const createVisible = await isVisible(
      applicationPage.getByRole("button", { name: /crear negocio/i }),
      5000
    );

    logValidation(step3, "Input field 'Nombre del Negocio' exists", negocioInputExists);
    logValidation(step3, "Text 'Tienes 2 de 3 negocios' is visible", limitTextVisible);
    logValidation(step3, "Button 'Cancelar' is present", cancelVisible);
    logValidation(step3, "Button 'Crear Negocio' is present", createVisible);
    await capture(applicationPage, step3, "03-agregar-negocio-modal.png", true);

    if (modalVisible && negocioInputExists) {
      logAction(step3, "Running optional modal interaction");
      const input = applicationPage
        .getByLabel(/nombre del negocio/i)
        .or(applicationPage.getByPlaceholder(/nombre del negocio/i))
        .first();
      await input.click({ timeout: 5000 }).catch(() => {});
      await input.fill("Negocio Prueba Automatizacion").catch(() => {});
      if (cancelVisible) {
        await applicationPage.getByRole("button", { name: /cancelar/i }).first().click().catch(() => {});
        await waitForUi(applicationPage, "close modal with cancel");
      }
    }

    finishStep(step3);
    report.finalReport["Agregar Negocio modal"] = step3.status;

    const step4 = createStep(4, "Open Administrar Negocios");
    report.steps.push(step4);

    let administrarVisibleNow = await isVisible(applicationPage.getByText(/administrar negocios/i), 2500);
    if (!administrarVisibleNow) {
      await clickFirstVisible(
        [
          () => applicationPage.getByText(/^Mi Negocio$/i),
          () => applicationPage.getByRole("button", { name: /mi negocio/i }),
        ],
        step4,
        "Re-expand Mi Negocio"
      );
      administrarVisibleNow = await isVisible(applicationPage.getByText(/administrar negocios/i), 5000);
    }

    const clickedAdministrar = await clickFirstVisible(
      [
        () => applicationPage.getByText(/^Administrar Negocios$/i),
        () => applicationPage.getByRole("button", { name: /^Administrar Negocios$/i }),
        () => applicationPage.getByRole("link", { name: /^Administrar Negocios$/i }),
      ],
      step4,
      "Open Administrar Negocios"
    );
    logValidation(step4, "Clicked 'Administrar Negocios'", clickedAdministrar);

    const infoGeneralVisible = await isVisible(applicationPage.getByText(/informacion general/i), 7000);
    const detallesCuentaVisible = await isVisible(
      applicationPage.getByText(/detalles de la cuenta/i),
      7000
    );
    const tusNegociosVisible = await isVisible(applicationPage.getByText(/tus negocios/i), 7000);
    const seccionLegalVisible = await isVisible(applicationPage.getByText(/seccion legal/i), 7000);

    logValidation(step4, "Section 'Informacion General' exists", infoGeneralVisible);
    logValidation(step4, "Section 'Detalles de la Cuenta' exists", detallesCuentaVisible);
    logValidation(step4, "Section 'Tus Negocios' exists", tusNegociosVisible);
    logValidation(step4, "Section 'Seccion Legal' exists", seccionLegalVisible);
    await capture(applicationPage, step4, "04-administrar-negocios-page.png", true);

    finishStep(step4);
    report.finalReport["Administrar Negocios view"] = step4.status;

    const step5 = createStep(5, "Validate Informacion General");
    report.steps.push(step5);

    const userNameVisible = await verifyAnyVisible([
      () => isVisible(applicationPage.getByText(/juan|lucas|barbier|garzon/i), 5000),
      () => isVisible(applicationPage.getByText(/nombre/i), 5000),
    ]);
    const userEmailVisible = await isVisible(
      applicationPage.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
      5000
    );
    const businessPlanVisible = await isVisible(applicationPage.getByText(/business plan/i), 5000);
    const cambiarPlanVisible = await isVisible(
      applicationPage.getByRole("button", { name: /cambiar plan/i }),
      5000
    );

    logValidation(step5, "User name is visible", userNameVisible);
    logValidation(step5, "User email is visible", userEmailVisible);
    logValidation(step5, "Text 'BUSINESS PLAN' is visible", businessPlanVisible);
    logValidation(step5, "Button 'Cambiar Plan' is visible", cambiarPlanVisible);
    finishStep(step5);
    report.finalReport["Información General"] = step5.status;

    const step6 = createStep(6, "Validate Detalles de la Cuenta");
    report.steps.push(step6);

    const cuentaCreadaVisible = await isVisible(applicationPage.getByText(/cuenta creada/i), 5000);
    const estadoActivoVisible = await isVisible(applicationPage.getByText(/estado activo/i), 5000);
    const idiomaVisible = await isVisible(
      applicationPage.getByText(/idioma seleccionado|idioma/i),
      5000
    );

    logValidation(step6, "'Cuenta creada' is visible", cuentaCreadaVisible);
    logValidation(step6, "'Estado activo' is visible", estadoActivoVisible);
    logValidation(step6, "'Idioma seleccionado' is visible", idiomaVisible);
    finishStep(step6);
    report.finalReport["Detalles de la Cuenta"] = step6.status;

    const step7 = createStep(7, "Validate Tus Negocios");
    report.steps.push(step7);

    const businessListVisible = await verifyAnyVisible([
      () => isVisible(applicationPage.getByText(/tus negocios/i), 5000),
      () => isVisible(applicationPage.locator("table"), 5000),
      () => isVisible(applicationPage.locator("ul"), 5000),
    ]);
    const addBusinessButtonVisible = await verifyAnyVisible([
      () => isVisible(applicationPage.getByRole("button", { name: /^Agregar Negocio$/i }), 5000),
      () => isVisible(applicationPage.getByText(/^Agregar Negocio$/i), 5000),
    ]);
    const limitTextVisibleOnPage = await isVisible(
      applicationPage.getByText(/tienes\s*2\s*de\s*3\s*negocios/i),
      5000
    );

    logValidation(step7, "Business list is visible", businessListVisible);
    logValidation(step7, "Button 'Agregar Negocio' exists", addBusinessButtonVisible);
    logValidation(step7, "Text 'Tienes 2 de 3 negocios' is visible", limitTextVisibleOnPage);
    finishStep(step7);
    report.finalReport["Tus Negocios"] = step7.status;

    const step8 = createStep(8, "Validate Terminos y Condiciones");
    report.steps.push(step8);

    const termsPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    const clickedTerms = await clickFirstVisible(
      [
        () => applicationPage.getByRole("link", { name: /terminos y condiciones|t[eé]rminos y condiciones/i }),
        () => applicationPage.getByText(/terminos y condiciones|t[eé]rminos y condiciones/i),
      ],
      step8,
      "Open Terminos y Condiciones"
    );
    logValidation(step8, "Clicked 'Terminos y Condiciones'", clickedTerms);

    let termsPage = await termsPopupPromise;
    let termsOpenedInNewTab = false;
    if (termsPage) {
      termsOpenedInNewTab = true;
      await termsPage.waitForLoadState("domcontentloaded", { timeout: DEFAULT_TIMEOUT_MS }).catch(() => {});
      await waitForUi(termsPage, "terms popup load");
    } else {
      termsPage = applicationPage;
      await waitForUi(termsPage, "terms same-tab load");
    }

    const termsHeadingVisible = await verifyAnyVisible([
      () => isVisible(termsPage.getByRole("heading", { name: /terminos y condiciones|t[eé]rminos y condiciones/i }), 7000),
      () => isVisible(termsPage.getByText(/terminos y condiciones|t[eé]rminos y condiciones/i), 7000),
    ]);
    const termsBodyVisible = (await termsPage.locator("body").innerText().catch(() => "")).length > 120;

    logValidation(step8, "Heading 'Terminos y Condiciones' is visible", termsHeadingVisible);
    logValidation(step8, "Legal content text is visible", termsBodyVisible);

    await capture(termsPage, step8, "08-terminos-y-condiciones.png", true);
    report.finalUrls.terminosCondiciones = termsPage.url();
    step8.evidence.push({
      at: nowIso(),
      type: "url",
      url: termsPage.url(),
      preview: extractContentPreview(await termsPage.locator("body").innerText().catch(() => "")),
    });

    if (termsOpenedInNewTab) {
      await termsPage.close().catch(() => {});
      await applicationPage.bringToFront();
      await waitForUi(applicationPage, "return from terms tab");
    } else if (termsPage !== applicationPage) {
      await applicationPage.bringToFront().catch(() => {});
    } else {
      await applicationPage.goBack({ timeout: DEFAULT_TIMEOUT_MS }).catch(() => {});
      await waitForUi(applicationPage, "return from terms same tab");
    }

    finishStep(step8);
    report.finalReport["Términos y Condiciones"] = step8.status;

    const step9 = createStep(9, "Validate Politica de Privacidad");
    report.steps.push(step9);

    const privacyPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    const clickedPrivacy = await clickFirstVisible(
      [
        () => applicationPage.getByRole("link", { name: /politica de privacidad|pol[ií]tica de privacidad/i }),
        () => applicationPage.getByText(/politica de privacidad|pol[ií]tica de privacidad/i),
      ],
      step9,
      "Open Politica de Privacidad"
    );
    logValidation(step9, "Clicked 'Politica de Privacidad'", clickedPrivacy);

    let privacyPage = await privacyPopupPromise;
    let privacyOpenedInNewTab = false;
    if (privacyPage) {
      privacyOpenedInNewTab = true;
      await privacyPage.waitForLoadState("domcontentloaded", { timeout: DEFAULT_TIMEOUT_MS }).catch(() => {});
      await waitForUi(privacyPage, "privacy popup load");
    } else {
      privacyPage = applicationPage;
      await waitForUi(privacyPage, "privacy same-tab load");
    }

    const privacyHeadingVisible = await verifyAnyVisible([
      () => isVisible(privacyPage.getByRole("heading", { name: /politica de privacidad|pol[ií]tica de privacidad/i }), 7000),
      () => isVisible(privacyPage.getByText(/politica de privacidad|pol[ií]tica de privacidad/i), 7000),
    ]);
    const privacyBodyVisible = (await privacyPage.locator("body").innerText().catch(() => "")).length > 120;

    logValidation(step9, "Heading 'Politica de Privacidad' is visible", privacyHeadingVisible);
    logValidation(step9, "Legal content text is visible", privacyBodyVisible);

    await capture(privacyPage, step9, "09-politica-de-privacidad.png", true);
    report.finalUrls.politicaPrivacidad = privacyPage.url();
    step9.evidence.push({
      at: nowIso(),
      type: "url",
      url: privacyPage.url(),
      preview: extractContentPreview(await privacyPage.locator("body").innerText().catch(() => "")),
    });

    if (privacyOpenedInNewTab) {
      await privacyPage.close().catch(() => {});
      await applicationPage.bringToFront();
      await waitForUi(applicationPage, "return from privacy tab");
    } else {
      await applicationPage.goBack({ timeout: DEFAULT_TIMEOUT_MS }).catch(() => {});
      await waitForUi(applicationPage, "return from privacy same tab");
    }

    finishStep(step9);
    report.finalReport["Política de Privacidad"] = step9.status;
  } catch (error) {
    report.status = "FAIL";
    report.error = normalizeWhitespace(error && error.stack ? error.stack : String(error));
    step1.status = "FAIL";
    if (!step1.finishedAt) {
      finishStep(step1);
    }
  } finally {
    for (const field of FINAL_FIELDS) {
      const status = report.finalReport[field.label];
      if (status !== "PASS") {
        report.status = "FAIL";
      }
    }

    report.finishedAt = nowIso();
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");

    if (page && !page.isClosed()) {
      await page.close().catch(() => {});
    }
    if (context) {
      await context.close().catch(() => {});
    }
    if (browser) {
      await browser.close().catch(() => {});
    }

    console.log(JSON.stringify(report, null, 2));
    console.log(`\nReport written to ${REPORT_PATH}`);
  }
}

run().catch((error) => {
  console.error("Unhandled error in test runner:");
  console.error(error);
  process.exitCode = 1;
});
