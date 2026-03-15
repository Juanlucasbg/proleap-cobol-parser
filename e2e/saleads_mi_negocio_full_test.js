#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const OUTPUT_DIR =
  process.env.SALEADS_EVIDENCE_DIR ||
  path.join(process.cwd(), "artifacts", "saleads-mi-negocio");
const SCREENSHOT_DIR = path.join(OUTPUT_DIR, "screenshots");
const REPORT_PATH = path.join(OUTPUT_DIR, "report.json");

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

function ensureDirs() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function buildReport() {
  const fields = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "PENDING", details: "" }]),
  );

  return {
    name: "saleads_mi_negocio_full_test",
    startedAt: new Date().toISOString(),
    environment: {
      loginUrl: process.env.SALEADS_LOGIN_URL || null,
      cdpUrlProvided: Boolean(process.env.PW_CDP_URL),
      headless: process.env.HEADLESS !== "false",
    },
    steps: fields,
    screenshots: [],
    legalUrls: {},
    notes: [],
  };
}

function pass(report, field, details) {
  report.steps[field] = { status: "PASS", details: details || "" };
}

function fail(report, field, details) {
  report.steps[field] = { status: "FAIL", details: details || "" };
}

function addNote(report, text) {
  report.notes.push(text);
}

async function isVisible(locator) {
  try {
    return await locator.first().isVisible({ timeout: 1500 });
  } catch {
    return false;
  }
}

async function waitUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(1200);
}

async function screenshot(page, report, name, fullPage = false) {
  const fileName = `${String(report.screenshots.length + 1).padStart(2, "0")}_${name}.png`;
  const filePath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  report.screenshots.push(filePath);
}

async function firstVisible(locators) {
  for (const locator of locators) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }
  return null;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitUi(page);
}

async function clickByVisibleText(page, candidates) {
  const locators = candidates.flatMap((name) => [
    page.getByRole("button", { name }),
    page.getByRole("link", { name }),
    page.getByText(name, { exact: false }),
  ]);

  const locator = await firstVisible(locators);
  if (!locator) {
    throw new Error(
      `Could not find a visible element for any of: ${candidates
        .map((v) => v.toString())
        .join(", ")}`,
    );
  }

  await clickAndWait(locator, page);
}

async function validateVisible(page, label, locators) {
  const target = await firstVisible(locators);
  if (!target) {
    throw new Error(`Missing expected UI element: ${label}`);
  }
}

function writeReport(report) {
  report.endedAt = new Date().toISOString();
  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2));
}

async function getAppPage(context) {
  const existingPages = context.pages();
  if (existingPages.length > 0) {
    const page = existingPages[0];
    await page.bringToFront();
    await waitUi(page);
    return page;
  }

  return context.newPage();
}

async function validateLegalPage({
  appPage,
  context,
  report,
  linkNames,
  reportField,
  headingRegex,
  screenshotName,
  legalKey,
}) {
  const currentUrl = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickByVisibleText(appPage, linkNames);

  const popup = await popupPromise;
  const legalPage = popup || appPage;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await legalPage.waitForTimeout(1200);

  await validateVisible(legalPage, reportField, [
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex),
  ]);

  const legalContentLocator = legalPage.locator("main, article, body p");
  const contentCount = await legalContentLocator.count();
  if (contentCount < 1) {
    throw new Error(`Legal page has no readable content for ${reportField}`);
  }

  await screenshot(legalPage, report, screenshotName, true);
  report.legalUrls[legalKey] = legalPage.url();
  pass(report, reportField, `${reportField} validated.`);

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
    await waitUi(appPage);
    return;
  }

  if (appPage.url() !== currentUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitUi(appPage);
  }
}

async function run() {
  ensureDirs();
  const report = buildReport();

  let browser;
  let context;

  try {
    if (process.env.PW_CDP_URL) {
      browser = await chromium.connectOverCDP(process.env.PW_CDP_URL);
      context = browser.contexts()[0] || (await browser.newContext());
      addNote(report, "Connected to existing browser via PW_CDP_URL.");
    } else {
      browser = await chromium.launch({
        headless: process.env.HEADLESS !== "false",
      });
      context = await browser.newContext({
        viewport: { width: 1440, height: 900 },
      });
      addNote(report, "Launched a new Chromium session.");
    }

    const page = await getAppPage(context);

    if (!process.env.PW_CDP_URL) {
      const loginUrl = process.env.SALEADS_LOGIN_URL;
      if (!loginUrl) {
        throw new Error(
          "SALEADS_LOGIN_URL is required when PW_CDP_URL is not provided.",
        );
      }
      await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 45000 });
      await waitUi(page);
    }

    // Step 1: Login with Google
    try {
      const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);

      await clickByVisibleText(page, [
        /sign in with google/i,
        /iniciar sesi[oó]n con google/i,
        /continuar con google/i,
        /google/i,
      ]);

      const googlePopup = await popupPromise;
      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});

        const accountLocator = await firstVisible([
          googlePopup.getByText(ACCOUNT_EMAIL, { exact: false }),
          googlePopup.getByRole("button", { name: ACCOUNT_EMAIL }),
        ]);

        if (accountLocator) {
          await accountLocator.click();
          await googlePopup.waitForTimeout(1000);
        } else {
          addNote(
            report,
            "Google account selector did not display the configured account. Continuing flow.",
          );
        }
      }

      await waitUi(page);
      await validateVisible(page, "main application interface", [
        page.locator("main"),
        page.getByRole("navigation"),
      ]);
      await validateVisible(page, "left sidebar navigation", [
        page.locator("aside"),
        page.getByRole("navigation"),
      ]);
      await screenshot(page, report, "01_dashboard_loaded", true);
      pass(report, "Login", "Main interface and left sidebar are visible.");
    } catch (error) {
      fail(report, "Login", error.message);
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByVisibleText(page, [/mi negocio/i, /negocio/i]);
      await validateVisible(page, "Agregar Negocio", [page.getByText(/agregar negocio/i)]);
      await validateVisible(page, "Administrar Negocios", [
        page.getByText(/administrar negocios/i),
      ]);
      await screenshot(page, report, "02_mi_negocio_menu_expanded");
      pass(
        report,
        "Mi Negocio menu",
        "Mi Negocio menu expanded with Agregar Negocio and Administrar Negocios.",
      );
    } catch (error) {
      fail(report, "Mi Negocio menu", error.message);
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, [/agregar negocio/i]);
      await validateVisible(page, "Crear Nuevo Negocio modal", [
        page.getByRole("heading", { name: /crear nuevo negocio/i }),
        page.getByText(/crear nuevo negocio/i),
      ]);
      await validateVisible(page, "Nombre del Negocio", [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.getByText(/nombre del negocio/i),
      ]);
      await validateVisible(page, "Tienes 2 de 3 negocios", [
        page.getByText(/tienes 2 de 3 negocios/i),
      ]);
      await validateVisible(page, "Cancelar button", [
        page.getByRole("button", { name: /cancelar/i }),
      ]);
      await validateVisible(page, "Crear Negocio button", [
        page.getByRole("button", { name: /crear negocio/i }),
      ]);

      const nameInput = await firstVisible([
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator("input").filter({ hasText: /nombre del negocio/i }),
      ]);

      if (nameInput) {
        await nameInput.fill("Negocio Prueba Automatización");
      }

      await screenshot(page, report, "03_agregar_negocio_modal");
      await clickByVisibleText(page, [/cancelar/i]);
      pass(report, "Agregar Negocio modal", "Modal validated and closed with Cancelar.");
    } catch (error) {
      fail(report, "Agregar Negocio modal", error.message);
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      if (!(await isVisible(page.getByText(/administrar negocios/i)))) {
        await clickByVisibleText(page, [/mi negocio/i, /negocio/i]);
      }
      await clickByVisibleText(page, [/administrar negocios/i]);
      await waitUi(page);

      await validateVisible(page, "Información General", [
        page.getByRole("heading", { name: /informaci[oó]n general/i }),
        page.getByText(/informaci[oó]n general/i),
      ]);
      await validateVisible(page, "Detalles de la Cuenta", [
        page.getByRole("heading", { name: /detalles de la cuenta/i }),
        page.getByText(/detalles de la cuenta/i),
      ]);
      await validateVisible(page, "Tus Negocios", [
        page.getByRole("heading", { name: /tus negocios/i }),
        page.getByText(/tus negocios/i),
      ]);
      await validateVisible(page, "Sección Legal", [
        page.getByRole("heading", { name: /secci[oó]n legal/i }),
        page.getByText(/secci[oó]n legal/i),
      ]);
      await screenshot(page, report, "04_administrar_negocios_page", true);
      pass(
        report,
        "Administrar Negocios view",
        "Account page loaded with all expected sections.",
      );
    } catch (error) {
      fail(report, "Administrar Negocios view", error.message);
      throw error;
    }

    // Step 5: Validate Información General
    try {
      await validateVisible(page, "user email", [page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)]);
      await validateVisible(page, "BUSINESS PLAN", [page.getByText(/business plan/i)]);
      await validateVisible(page, "Cambiar Plan", [
        page.getByRole("button", { name: /cambiar plan/i }),
        page.getByText(/cambiar plan/i),
      ]);

      const possibleUserName = page.locator("h1, h2, h3, strong").first();
      const nameText = ((await possibleUserName.textContent().catch(() => "")) || "").trim();
      if (!nameText) {
        throw new Error("Could not confirm visible user name in Información General.");
      }

      pass(report, "Información General", "User name/email, plan and Cambiar Plan validated.");
    } catch (error) {
      fail(report, "Información General", error.message);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await validateVisible(page, "Cuenta creada", [page.getByText(/cuenta creada/i)]);
      await validateVisible(page, "Estado activo", [page.getByText(/estado activo/i)]);
      await validateVisible(page, "Idioma seleccionado", [page.getByText(/idioma seleccionado/i)]);
      pass(report, "Detalles de la Cuenta", "Account details fields are visible.");
    } catch (error) {
      fail(report, "Detalles de la Cuenta", error.message);
    }

    // Step 7: Validate Tus Negocios
    try {
      await validateVisible(page, "business list", [
        page.locator("ul, table, [data-testid*='business']").first(),
        page.getByText(/tus negocios/i),
      ]);
      await validateVisible(page, "Agregar Negocio button", [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ]);
      await validateVisible(page, "Tienes 2 de 3 negocios", [
        page.getByText(/tienes 2 de 3 negocios/i),
      ]);
      pass(report, "Tus Negocios", "Business list and limits validated.");
    } catch (error) {
      fail(report, "Tus Negocios", error.message);
    }

    // Step 8: Validate Términos y Condiciones
    try {
      await validateLegalPage({
        appPage: page,
        context,
        report,
        linkNames: [/t[ée]rminos y condiciones/i],
        reportField: "Términos y Condiciones",
        headingRegex: /t[ée]rminos y condiciones/i,
        screenshotName: "05_terminos_y_condiciones",
        legalKey: "terminosYCondiciones",
      });
    } catch (error) {
      fail(report, "Términos y Condiciones", error.message);
    }

    // Step 9: Validate Política de Privacidad
    try {
      await validateLegalPage({
        appPage: page,
        context,
        report,
        linkNames: [/pol[ií]tica de privacidad/i],
        reportField: "Política de Privacidad",
        headingRegex: /pol[ií]tica de privacidad/i,
        screenshotName: "06_politica_de_privacidad",
        legalKey: "politicaDePrivacidad",
      });
    } catch (error) {
      fail(report, "Política de Privacidad", error.message);
    }
  } catch (fatalError) {
    addNote(report, `Fatal error: ${fatalError.message}`);
  } finally {
    writeReport(report);
    if (browser) {
      await browser.close().catch(() => {});
    }
  }

  const failedFields = REPORT_FIELDS.filter(
    (field) => report.steps[field].status !== "PASS",
  );

  if (failedFields.length > 0) {
    console.error(
      `Completed with failures in: ${failedFields.join(", ")}. Report: ${REPORT_PATH}`,
    );
    process.exitCode = 1;
  } else {
    console.log(`All validations passed. Report: ${REPORT_PATH}`);
  }
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
