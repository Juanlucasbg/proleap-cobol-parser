#!/usr/bin/env node

const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const EMAIL_TO_SELECT = "juanlucasbarbiergarzon@gmail.com";
const RUN_ID = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACTS_DIR =
  process.env.SALEADS_ARTIFACTS_DIR ||
  path.join(process.cwd(), "artifacts", "saleads-mi-negocio-full-test", RUN_ID);

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

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toRegex(candidate) {
  if (candidate instanceof RegExp) {
    return candidate;
  }

  return new RegExp(escapeRegex(candidate), "i");
}

function candidateLocators(root, candidate) {
  const expression = toRegex(candidate);
  return [
    root.getByRole("button", { name: expression }),
    root.getByRole("link", { name: expression }),
    root.getByRole("menuitem", { name: expression }),
    root.getByRole("tab", { name: expression }),
    root.getByRole("heading", { name: expression }),
    root.getByLabel(expression),
    root.getByPlaceholder(expression),
    root.getByText(expression),
  ];
}

async function findFirstVisible(root, candidates, timeoutMs = 7000) {
  for (const candidate of candidates) {
    for (const locator of candidateLocators(root, candidate)) {
      const target = locator.first();
      try {
        await target.waitFor({ state: "visible", timeout: timeoutMs });
        return target;
      } catch (error) {
        // Try next locator strategy.
      }
    }
  }

  return null;
}

async function isAnyVisible(root, candidates, timeoutMs = 4000) {
  return (await findFirstVisible(root, candidates, timeoutMs)) !== null;
}

async function clickVisible(root, candidates, description) {
  const target = await findFirstVisible(root, candidates, 8000);

  if (!target) {
    throw new Error(`No visible element found for: ${description}`);
  }

  await target.click();
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function ensureDir(targetDir) {
  await fs.mkdir(targetDir, { recursive: true });
}

async function takeScreenshot(page, filename, fullPage = false) {
  const filepath = path.join(ARTIFACTS_DIR, filename);
  await page.screenshot({ path: filepath, fullPage });
  return filepath;
}

function initSummary() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

async function openLegalLinkAndValidate({
  appPage,
  context,
  linkCandidates,
  headingCandidates,
  screenshotName,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickVisible(appPage, linkCandidates, String(linkCandidates[0]));
  await waitForUi(appPage);

  const popup = await popupPromise;
  const legalPage = popup || appPage;

  await waitForUi(legalPage);

  const headingVisible = await isAnyVisible(legalPage, headingCandidates, 10000);
  const bodyText = (await legalPage.locator("body").innerText().catch(() => "")).trim();
  const hasLegalText = bodyText.length > 120;
  const url = legalPage.url();
  const screenshotPath = await takeScreenshot(legalPage, screenshotName, true);

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront().catch(() => {});
    await waitForUi(appPage);
  } else if (legalPage.url() !== appPage.url()) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return {
    pass: headingVisible && hasLegalText,
    url,
    screenshotPath,
    headingVisible,
    hasLegalText,
  };
}

async function run() {
  await ensureDir(ARTIFACTS_DIR);

  const report = {
    run_id: RUN_ID,
    run_at_utc: new Date().toISOString(),
    artifacts_dir: ARTIFACTS_DIR,
    summary: initSummary(),
    evidence: {
      screenshots: {},
      urls: {},
    },
    notes: [],
  };

  let browser;
  let context;
  let page;
  let fatalError = null;

  try {
    if (process.env.PLAYWRIGHT_WS_ENDPOINT) {
      browser = await chromium.connectOverCDP(process.env.PLAYWRIGHT_WS_ENDPOINT);
      context = browser.contexts()[0] || (await browser.newContext());
      page = context.pages()[0] || (await context.newPage());
      report.notes.push("Connected to existing browser via PLAYWRIGHT_WS_ENDPOINT.");
    } else {
      browser = await chromium.launch({
        headless: process.env.HEADLESS !== "false",
      });
      context = await browser.newContext();
      page = await context.newPage();
      report.notes.push("Launched local Chromium browser.");
    }

    if (process.env.SALEADS_LOGIN_URL) {
      await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
      report.notes.push("Navigated to SALEADS_LOGIN_URL.");
    } else if (page.url().startsWith("about:blank")) {
      throw new Error(
        "No starting page detected. Set SALEADS_LOGIN_URL or provide PLAYWRIGHT_WS_ENDPOINT already on SaleADS login page."
      );
    } else {
      report.notes.push("Using existing open page as initial login page.");
    }

    // Step 1 - Login with Google
    try {
      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickVisible(
        page,
        [
          /sign in with google/i,
          /iniciar sesi[oó]n con google/i,
          /continuar con google/i,
          /google/i,
        ],
        "Google login button"
      );
      await waitForUi(page);

      const popup = await popupPromise;
      const accountPage = popup || page;
      await waitForUi(accountPage);

      const accountChoice = await findFirstVisible(accountPage, [EMAIL_TO_SELECT], 6000);
      if (accountChoice) {
        await accountChoice.click();
        await waitForUi(accountPage);
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      }

      await page.bringToFront().catch(() => {});
      await waitForUi(page);

      const appVisible = await isAnyVisible(page, [/dashboard/i, /inicio/i, /mi negocio/i, /negocio/i], 15000);
      const sidebarVisible = await page.locator("aside, nav").first().isVisible().catch(() => false);
      report.summary["Login"] = appVisible && sidebarVisible ? "PASS" : "FAIL";
      report.evidence.screenshots.dashboard = await takeScreenshot(page, "01-dashboard.png", true);
    } catch (error) {
      report.summary["Login"] = "FAIL";
      report.notes.push(`Login validation failed: ${error.message}`);
    }

    // Step 2 - Open Mi Negocio menu
    try {
      await clickVisible(page, [/negocio/i], "Negocio section");
      await waitForUi(page);
      await clickVisible(page, [/mi negocio/i], "Mi Negocio option");
      await waitForUi(page);

      const agregarVisible = await isAnyVisible(page, [/agregar negocio/i], 10000);
      const administrarVisible = await isAnyVisible(page, [/administrar negocios/i], 10000);
      report.summary["Mi Negocio menu"] = agregarVisible && administrarVisible ? "PASS" : "FAIL";
      report.evidence.screenshots.mi_negocio_menu = await takeScreenshot(page, "02-mi-negocio-menu.png", true);
    } catch (error) {
      report.summary["Mi Negocio menu"] = "FAIL";
      report.notes.push(`Mi Negocio menu validation failed: ${error.message}`);
    }

    // Step 3 - Validate Agregar Negocio modal
    try {
      await clickVisible(page, [/agregar negocio/i], "Agregar Negocio");
      await waitForUi(page);

      const modalVisible = await isAnyVisible(page, [/crear nuevo negocio/i], 10000);
      const inputVisible = await isAnyVisible(page, [/nombre del negocio/i], 10000);
      const quotaVisible = await isAnyVisible(page, [/tienes 2 de 3 negocios/i], 10000);
      const cancelarVisible = await isAnyVisible(page, [/cancelar/i], 10000);
      const crearVisible = await isAnyVisible(page, [/crear negocio/i], 10000);

      report.summary["Agregar Negocio modal"] =
        modalVisible && inputVisible && quotaVisible && cancelarVisible && crearVisible ? "PASS" : "FAIL";
      report.evidence.screenshots.agregar_negocio_modal = await takeScreenshot(
        page,
        "03-agregar-negocio-modal.png",
        true
      );

      const businessNameInput = await findFirstVisible(page, [/nombre del negocio/i], 5000);
      if (businessNameInput) {
        await businessNameInput.fill("Negocio Prueba Automatización");
      }
      const cancelarButton = await findFirstVisible(page, [/cancelar/i], 5000);
      if (cancelarButton) {
        await cancelarButton.click();
        await waitForUi(page);
      }
    } catch (error) {
      report.summary["Agregar Negocio modal"] = "FAIL";
      report.notes.push(`Agregar Negocio modal validation failed: ${error.message}`);
    }

    // Step 4 - Open Administrar Negocios
    try {
      await clickVisible(page, [/mi negocio/i], "Mi Negocio option");
      await waitForUi(page);
      await clickVisible(page, [/administrar negocios/i], "Administrar Negocios option");
      await waitForUi(page);

      const infoGeneral = await isAnyVisible(page, [/informaci[oó]n general/i], 15000);
      const detallesCuenta = await isAnyVisible(page, [/detalles de la cuenta/i], 15000);
      const tusNegocios = await isAnyVisible(page, [/tus negocios/i], 15000);
      const seccionLegal = await isAnyVisible(page, [/secci[oó]n legal/i], 15000);

      report.summary["Administrar Negocios view"] =
        infoGeneral && detallesCuenta && tusNegocios && seccionLegal ? "PASS" : "FAIL";
      report.evidence.screenshots.administrar_negocios = await takeScreenshot(page, "04-administrar-negocios.png", true);
    } catch (error) {
      report.summary["Administrar Negocios view"] = "FAIL";
      report.notes.push(`Administrar Negocios validation failed: ${error.message}`);
    }

    // Step 5 - Validate Información General
    try {
      const emailVisible = await page
        .locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i")
        .first()
        .isVisible()
        .catch(() => false);
      const businessPlanVisible = await isAnyVisible(page, [/business plan/i], 10000);
      const cambiarPlanVisible = await isAnyVisible(page, [/cambiar plan/i], 10000);
      const userNameVisible = await page
        .locator("h1, h2, h3, strong")
        .filter({ hasText: /./ })
        .first()
        .isVisible()
        .catch(() => false);

      report.summary["Información General"] =
        userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible ? "PASS" : "FAIL";
    } catch (error) {
      report.summary["Información General"] = "FAIL";
      report.notes.push(`Información General validation failed: ${error.message}`);
    }

    // Step 6 - Validate Detalles de la Cuenta
    try {
      const cuentaCreadaVisible = await isAnyVisible(page, [/cuenta creada/i], 10000);
      const estadoActivoVisible = await isAnyVisible(page, [/estado activo/i], 10000);
      const idiomaVisible = await isAnyVisible(page, [/idioma seleccionado/i], 10000);
      report.summary["Detalles de la Cuenta"] =
        cuentaCreadaVisible && estadoActivoVisible && idiomaVisible ? "PASS" : "FAIL";
    } catch (error) {
      report.summary["Detalles de la Cuenta"] = "FAIL";
      report.notes.push(`Detalles de la Cuenta validation failed: ${error.message}`);
    }

    // Step 7 - Validate Tus Negocios
    try {
      const businessListVisible = await isAnyVisible(page, [/tus negocios/i], 10000);
      const addButtonVisible = await isAnyVisible(page, [/agregar negocio/i], 10000);
      const quotaVisible = await isAnyVisible(page, [/tienes 2 de 3 negocios/i], 10000);
      report.summary["Tus Negocios"] = businessListVisible && addButtonVisible && quotaVisible ? "PASS" : "FAIL";
    } catch (error) {
      report.summary["Tus Negocios"] = "FAIL";
      report.notes.push(`Tus Negocios validation failed: ${error.message}`);
    }

    // Step 8 - Validate Términos y Condiciones
    try {
      const termsResult = await openLegalLinkAndValidate({
        appPage: page,
        context,
        linkCandidates: [/t[eé]rminos y condiciones/i],
        headingCandidates: [/t[eé]rminos y condiciones/i],
        screenshotName: "05-terminos-y-condiciones.png",
      });

      report.summary["Términos y Condiciones"] = termsResult.pass ? "PASS" : "FAIL";
      report.evidence.screenshots.terminos_y_condiciones = termsResult.screenshotPath;
      report.evidence.urls.terminos_y_condiciones = termsResult.url;
    } catch (error) {
      report.summary["Términos y Condiciones"] = "FAIL";
      report.notes.push(`Términos y Condiciones validation failed: ${error.message}`);
    }

    // Step 9 - Validate Política de Privacidad
    try {
      const privacyResult = await openLegalLinkAndValidate({
        appPage: page,
        context,
        linkCandidates: [/pol[ií]tica de privacidad/i],
        headingCandidates: [/pol[ií]tica de privacidad/i],
        screenshotName: "06-politica-de-privacidad.png",
      });

      report.summary["Política de Privacidad"] = privacyResult.pass ? "PASS" : "FAIL";
      report.evidence.screenshots.politica_de_privacidad = privacyResult.screenshotPath;
      report.evidence.urls.politica_de_privacidad = privacyResult.url;
    } catch (error) {
      report.summary["Política de Privacidad"] = "FAIL";
      report.notes.push(`Política de Privacidad validation failed: ${error.message}`);
    }
  } catch (error) {
    fatalError = error;
    report.notes.push(`Fatal setup error: ${error.message}`);
  } finally {
    const reportPath = path.join(ARTIFACTS_DIR, "report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

    console.log("Final Report (PASS/FAIL):");
    console.log(JSON.stringify(report.summary, null, 2));
    console.log(`Report file: ${reportPath}`);
    console.log(`Artifacts dir: ${ARTIFACTS_DIR}`);
    if (fatalError) {
      console.error(`Fatal setup error: ${fatalError.message}`);
    }

    if (browser) {
      await browser.close().catch(() => {});
    }

    const failures = Object.values(report.summary).filter((value) => value !== "PASS").length;
    process.exitCode = failures > 0 ? 1 : 0;
  }
}

run().catch((error) => {
  console.error(`Unhandled error while running SaleADS Mi Negocio workflow: ${error.message}`);
  process.exitCode = 1;
});
