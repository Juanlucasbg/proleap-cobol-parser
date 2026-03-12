import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const FIELD_NAMES = [
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

const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.resolve(
  process.env.ARTIFACTS_DIR || `artifacts/${TEST_NAME}/${timestamp}`,
);
mkdirSync(artifactsDir, { recursive: true });

const report = {
  name: TEST_NAME,
  startedAt: new Date().toISOString(),
  artifactsDir,
  browser: {
    headless: process.env.HEADLESS !== "false",
    slowMoMs: Number(process.env.SLOW_MO_MS || 100),
    startUrl: process.env.SALEADS_START_URL || null,
    browserWsEndpoint: process.env.BROWSER_WS_ENDPOINT || null,
  },
  passFail: Object.fromEntries(FIELD_NAMES.map((name) => [name, "FAIL"])),
  stepLogs: [],
  evidence: {
    screenshots: [],
    urls: {},
  },
  errors: [],
};

function setFieldResult(fieldName, passed, detail) {
  report.passFail[fieldName] = passed ? "PASS" : "FAIL";
  report.stepLogs.push({
    fieldName,
    status: passed ? "PASS" : "FAIL",
    detail,
    at: new Date().toISOString(),
  });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(Number(process.env.UI_SETTLE_MS || 1200));
}

async function isVisible(locator, timeout = 6000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function assertVisible(locator, message, timeout = 10000) {
  await locator.first().waitFor({ state: "visible", timeout });
  return message;
}

async function clickFirstVisible(page, label, locatorFactories) {
  let lastError = null;
  for (const createLocator of locatorFactories) {
    const locator = createLocator(page).first();
    try {
      await locator.waitFor({ state: "visible", timeout: 3500 });
      await locator.click();
      await waitForUi(page);
      return;
    } catch (error) {
      lastError = error;
    }
  }

  throw new Error(
    `No se pudo hacer click en "${label}". Último error: ${lastError ? lastError.message : "sin detalle"}`,
  );
}

async function expectAnyVisible(locatorFactories, timeout = 7000) {
  for (const createLocator of locatorFactories) {
    const locator = createLocator().first();
    if (await isVisible(locator, timeout)) {
      return true;
    }
  }
  return false;
}

async function takeScreenshot(page, label, fullPage = false) {
  const safe = label.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "");
  const filePath = path.join(artifactsDir, `${String(report.evidence.screenshots.length + 1).padStart(2, "0")}_${safe}.png`);
  await page.screenshot({ path: filePath, fullPage });
  report.evidence.screenshots.push(filePath);
}

function hasLegalContent(text) {
  const clean = text.replace(/\s+/g, " ").trim();
  return clean.length >= 200;
}

function parsePotentialUserName(textBlock) {
  const ignored = [
    /informaci[oó]n general/i,
    /business plan/i,
    /cambiar plan/i,
    /@/,
    /cuenta creada/i,
    /estado activo/i,
    /idioma seleccionado/i,
    /tienes\s+\d+\s+de\s+\d+\s+negocios/i,
    /agregar negocio/i,
    /detalles de la cuenta/i,
    /tus negocios/i,
    /secci[oó]n legal/i,
  ];

  const candidates = textBlock
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  return candidates.some((line) => {
    if (ignored.some((rx) => rx.test(line))) return false;
    if (!/[a-zA-ZÀ-ÿ]/.test(line)) return false;
    return line.split(/\s+/).length >= 2;
  });
}

async function clickLegalAndValidate({
  appPage,
  context,
  fieldName,
  linkRegex,
  headingRegex,
  screenshotLabel,
  urlKey,
}) {
  const existingPages = new Set(context.pages());
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickFirstVisible(appPage, fieldName, [
    (p) => p.getByRole("link", { name: linkRegex }),
    (p) => p.getByRole("button", { name: linkRegex }),
    (p) => p.getByText(linkRegex, { exact: false }),
  ]);

  let legalPage = await popupPromise;
  if (!legalPage || existingPages.has(legalPage)) {
    legalPage = appPage;
  }

  await waitForUi(legalPage);
  await assertVisible(legalPage.getByText(headingRegex), `Heading ${headingRegex} visible`);
  const bodyText = await legalPage.locator("body").innerText();
  if (!hasLegalContent(bodyText)) {
    throw new Error(`${fieldName}: el contenido legal no parece suficiente.`);
  }

  report.evidence.urls[urlKey] = legalPage.url();
  await takeScreenshot(legalPage, screenshotLabel, true);

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }
}

async function main() {
  let browser;
  let context;
  let page;

  try {
    if (process.env.BROWSER_WS_ENDPOINT) {
      browser = await chromium.connectOverCDP(process.env.BROWSER_WS_ENDPOINT);
      context = browser.contexts()[0] || (await browser.newContext());
      page = context.pages()[0] || (await context.newPage());
    } else {
      browser = await chromium.launch({
        headless: process.env.HEADLESS !== "false",
        slowMo: Number(process.env.SLOW_MO_MS || 100),
      });
      context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
      page = await context.newPage();
    }

    if (process.env.SALEADS_START_URL) {
      await page.goto(process.env.SALEADS_START_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No hay URL inicial. Define SALEADS_START_URL o BROWSER_WS_ENDPOINT con una pestaña ya abierta en el login.",
      );
    }

    // STEP 1: Login with Google.
    try {
      const googlePopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickFirstVisible(page, "Sign in with Google", [
        (p) => p.getByRole("button", { name: /google/i }),
        (p) => p.getByRole("link", { name: /google/i }),
        (p) => p.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i),
      ]);

      let googlePage = await googlePopupPromise;
      if (!googlePage && /accounts\.google\.com/i.test(page.url())) {
        googlePage = page;
      }

      if (googlePage) {
        await waitForUi(googlePage);
        await clickFirstVisible(googlePage, GOOGLE_EMAIL, [
          (p) => p.getByText(new RegExp(GOOGLE_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
          (p) => p.getByRole("link", { name: new RegExp(GOOGLE_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i") }),
          (p) => p.locator(`text=${GOOGLE_EMAIL}`),
        ]).catch(() => {
          // If account is already selected and redirect happened, continue.
        });
      }

      // Pick a non-Google page if available after auth.
      for (const candidate of context.pages()) {
        if (!/accounts\.google\.com/i.test(candidate.url())) {
          page = candidate;
          break;
        }
      }

      await waitForUi(page);
      const mainUiVisible = await isVisible(page.locator("aside, nav, [data-testid*='sidebar'], [class*='sidebar']"), 10000);
      const negocioVisible = await isVisible(page.getByText(/negocio|mi negocio/i), 10000);
      if (!mainUiVisible || !negocioVisible) {
        throw new Error("No se detectó la interfaz principal ni la navegación lateral.");
      }

      await takeScreenshot(page, "dashboard_loaded", true);
      setFieldResult("Login", true, "Login completado y sidebar visible.");
    } catch (error) {
      await takeScreenshot(page, "login_failure", true).catch(() => {});
      setFieldResult("Login", false, error.message);
    }

    // STEP 2: Open Mi Negocio menu.
    try {
      await clickFirstVisible(page, "Negocio", [
        (p) => p.getByRole("link", { name: /negocio/i }),
        (p) => p.getByRole("button", { name: /negocio/i }),
        (p) => p.getByText(/negocio/i),
      ]).catch(() => {});

      await clickFirstVisible(page, "Mi Negocio", [
        (p) => p.getByRole("link", { name: /mi negocio/i }),
        (p) => p.getByRole("button", { name: /mi negocio/i }),
        (p) => p.getByText(/mi negocio/i),
      ]);

      await assertVisible(page.getByText(/agregar negocio/i), "Agregar Negocio visible");
      await assertVisible(page.getByText(/administrar negocios/i), "Administrar Negocios visible");
      await takeScreenshot(page, "mi_negocio_expanded_menu", true);

      setFieldResult("Mi Negocio menu", true, "Menú Mi Negocio expandido con opciones esperadas.");
    } catch (error) {
      await takeScreenshot(page, "mi_negocio_menu_failure", true).catch(() => {});
      setFieldResult("Mi Negocio menu", false, error.message);
    }

    // STEP 3: Validate Agregar Negocio modal.
    try {
      await clickFirstVisible(page, "Agregar Negocio", [
        (p) => p.getByRole("button", { name: /^agregar negocio$/i }),
        (p) => p.getByRole("link", { name: /^agregar negocio$/i }),
        (p) => p.getByText(/^agregar negocio$/i),
      ]);

      await assertVisible(page.getByText(/crear nuevo negocio/i), "Modal title visible");
      const hasNombreInput = await expectAnyVisible([
        () => page.getByLabel(/nombre del negocio/i),
        () => page.getByPlaceholder(/nombre del negocio/i),
        () => page.locator("input[name*='nombre'], input[id*='nombre']"),
      ]);
      if (!hasNombreInput) {
        throw new Error("No se encontró el input 'Nombre del Negocio'.");
      }

      await assertVisible(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i), "Plan usage text visible");
      await assertVisible(page.getByRole("button", { name: /cancelar/i }), "Cancelar visible");
      await assertVisible(page.getByRole("button", { name: /crear negocio/i }), "Crear Negocio visible");

      const nombreInput = page
        .getByLabel(/nombre del negocio/i)
        .or(page.getByPlaceholder(/nombre del negocio/i))
        .or(page.locator("input[name*='nombre'], input[id*='nombre']"))
        .first();
      if (await isVisible(nombreInput, 2500)) {
        await nombreInput.fill("Negocio Prueba Automatización");
      }
      await takeScreenshot(page, "agregar_negocio_modal", true);
      await clickFirstVisible(page, "Cancelar", [
        (p) => p.getByRole("button", { name: /cancelar/i }),
        (p) => p.getByText(/^cancelar$/i),
      ]);

      setFieldResult("Agregar Negocio modal", true, "Modal validado y cerrado.");
    } catch (error) {
      await takeScreenshot(page, "agregar_negocio_modal_failure", true).catch(() => {});
      setFieldResult("Agregar Negocio modal", false, error.message);
    }

    // STEP 4: Open Administrar Negocios and validate page sections.
    try {
      await clickFirstVisible(page, "Mi Negocio (re-open)", [
        (p) => p.getByRole("link", { name: /mi negocio/i }),
        (p) => p.getByRole("button", { name: /mi negocio/i }),
        (p) => p.getByText(/mi negocio/i),
      ]).catch(() => {});

      await clickFirstVisible(page, "Administrar Negocios", [
        (p) => p.getByRole("link", { name: /administrar negocios/i }),
        (p) => p.getByRole("button", { name: /administrar negocios/i }),
        (p) => p.getByText(/administrar negocios/i),
      ]);

      await assertVisible(page.getByText(/informaci[oó]n general/i), "Información General visible");
      await assertVisible(page.getByText(/detalles de la cuenta/i), "Detalles de la Cuenta visible");
      await assertVisible(page.getByText(/tus negocios/i), "Tus Negocios visible");
      await assertVisible(page.getByText(/secci[oó]n legal/i), "Sección Legal visible");
      await takeScreenshot(page, "administrar_negocios_account_page", true);

      setFieldResult("Administrar Negocios view", true, "Vista de cuenta cargada con secciones esperadas.");
    } catch (error) {
      await takeScreenshot(page, "administrar_negocios_view_failure", true).catch(() => {});
      setFieldResult("Administrar Negocios view", false, error.message);
    }

    // STEP 5: Validate Información General.
    try {
      const bodyText = await page.locator("body").innerText();
      const emailVisible = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(bodyText);
      const userNameVisible = parsePotentialUserName(bodyText);
      const businessPlanVisible = /business plan/i.test(bodyText);
      const cambiarPlanVisible = await isVisible(page.getByRole("button", { name: /cambiar plan/i }), 7000);

      if (!userNameVisible) throw new Error("No se detectó nombre de usuario visible.");
      if (!emailVisible) throw new Error("No se detectó email de usuario visible.");
      if (!businessPlanVisible) throw new Error("No se detectó texto 'BUSINESS PLAN'.");
      if (!cambiarPlanVisible) throw new Error("No se detectó el botón 'Cambiar Plan'.");

      setFieldResult("Información General", true, "Nombre, email, BUSINESS PLAN y Cambiar Plan visibles.");
    } catch (error) {
      await takeScreenshot(page, "informacion_general_failure", true).catch(() => {});
      setFieldResult("Información General", false, error.message);
    }

    // STEP 6: Validate Detalles de la Cuenta.
    try {
      await assertVisible(page.getByText(/cuenta creada/i), "Cuenta creada visible");
      await assertVisible(page.getByText(/estado activo/i), "Estado activo visible");
      await assertVisible(page.getByText(/idioma seleccionado/i), "Idioma seleccionado visible");

      setFieldResult("Detalles de la Cuenta", true, "Detalle de cuenta validado.");
    } catch (error) {
      await takeScreenshot(page, "detalles_cuenta_failure", true).catch(() => {});
      setFieldResult("Detalles de la Cuenta", false, error.message);
    }

    // STEP 7: Validate Tus Negocios.
    try {
      await assertVisible(page.getByText(/tus negocios/i), "Tus Negocios visible");
      await assertVisible(page.getByRole("button", { name: /agregar negocio/i }), "Agregar Negocio button visible");
      await assertVisible(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i), "Quota text visible");

      const sectionText = await page.locator("body").innerText();
      const listSignals = [/tus negocios/i, /agregar negocio/i, /negocio/i];
      if (!listSignals.every((rx) => rx.test(sectionText))) {
        throw new Error("No hay señales suficientes de listado de negocios visible.");
      }

      setFieldResult("Tus Negocios", true, "Sección Tus Negocios validada.");
    } catch (error) {
      await takeScreenshot(page, "tus_negocios_failure", true).catch(() => {});
      setFieldResult("Tus Negocios", false, error.message);
    }

    // STEP 8: Validate Términos y Condiciones.
    try {
      await clickLegalAndValidate({
        appPage: page,
        context,
        fieldName: "Términos y Condiciones",
        linkRegex: /t[eé]rminos y condiciones/i,
        headingRegex: /t[eé]rminos y condiciones/i,
        screenshotLabel: "terminos_y_condiciones",
        urlKey: "terminosYCondiciones",
      });
      setFieldResult("Términos y Condiciones", true, "Página legal validada.");
    } catch (error) {
      await takeScreenshot(page, "terminos_condiciones_failure", true).catch(() => {});
      setFieldResult("Términos y Condiciones", false, error.message);
    }

    // STEP 9: Validate Política de Privacidad.
    try {
      await clickLegalAndValidate({
        appPage: page,
        context,
        fieldName: "Política de Privacidad",
        linkRegex: /pol[ií]tica de privacidad/i,
        headingRegex: /pol[ií]tica de privacidad/i,
        screenshotLabel: "politica_de_privacidad",
        urlKey: "politicaDePrivacidad",
      });
      setFieldResult("Política de Privacidad", true, "Página legal validada.");
    } catch (error) {
      await takeScreenshot(page, "politica_privacidad_failure", true).catch(() => {});
      setFieldResult("Política de Privacidad", false, error.message);
    }
  } catch (error) {
    if (page) {
      await takeScreenshot(page, "fatal_error", true).catch(() => {});
    }
    report.errors.push({
      message: error.message,
      stack: error.stack,
    });
  } finally {
    report.endedAt = new Date().toISOString();
    report.allPassed = Object.values(report.passFail).every((status) => status === "PASS");

    const reportPath = path.join(artifactsDir, "final-report.json");
    writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

    const reportTxtPath = path.join(artifactsDir, "final-report.txt");
    const lines = [
      `Test: ${TEST_NAME}`,
      `Started: ${report.startedAt}`,
      `Ended: ${report.endedAt}`,
      "",
      ...FIELD_NAMES.map((name) => `${name}: ${report.passFail[name]}`),
      "",
      `Términos y Condiciones URL: ${report.evidence.urls.terminosYCondiciones || "N/A"}`,
      `Política de Privacidad URL: ${report.evidence.urls.politicaDePrivacidad || "N/A"}`,
      "",
      `Screenshots:`,
      ...report.evidence.screenshots.map((p) => `- ${p}`),
      "",
      report.allPassed ? "OVERALL: PASS" : "OVERALL: FAIL",
    ];
    writeFileSync(reportTxtPath, `${lines.join("\n")}\n`, "utf8");

    // Emit concise summary in stdout for CI logs.
    console.log(JSON.stringify({
      test: TEST_NAME,
      overall: report.allPassed ? "PASS" : "FAIL",
      passFail: report.passFail,
      reportPath,
      reportTxtPath,
      screenshots: report.evidence.screenshots.length,
      legalUrls: report.evidence.urls,
      errors: report.errors.map((e) => e.message),
    }, null, 2));

    if (context) {
      await context.close().catch(() => {});
    }
    if (browser) {
      await browser.close().catch(() => {});
    }

    process.exitCode = report.allPassed ? 0 : 1;
  }
}

main();
