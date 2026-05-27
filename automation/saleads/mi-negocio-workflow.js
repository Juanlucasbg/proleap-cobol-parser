#!/usr/bin/env node

const { chromium } = require("playwright");
const fs = require("node:fs/promises");
const path = require("node:path");

const ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
const HEADLESS = process.env.HEADLESS !== "false";

const REPORT_FIELDS = [
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

const checkpointDir = path.resolve(
  process.cwd(),
  "artifacts",
  "saleads-mi-negocio"
);

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function asRegex(value) {
  if (value instanceof RegExp) {
    return value;
  }

  return new RegExp(escapeRegex(value), "i");
}

async function ensureDirectory(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page, reason) {
  try {
    await page.waitForLoadState("domcontentloaded", { timeout: 15000 });
  } catch (error) {
    console.log(`[wait] domcontentloaded timeout after: ${reason}`);
  }

  try {
    await page.waitForLoadState("networkidle", { timeout: 12000 });
  } catch (error) {
    console.log(`[wait] networkidle timeout after: ${reason}`);
  }

  await page.waitForTimeout(700);
}

async function takeCheckpoint(page, fileName, fullPage = false) {
  await ensureDirectory(checkpointDir);
  const fullPath = path.join(checkpointDir, fileName);
  await page.screenshot({ path: fullPath, fullPage });
  return fullPath;
}

async function firstVisibleLocator(scope, matchers, timeoutMs = 15000) {
  const candidates = [];

  for (const matcher of matchers) {
    const rx = asRegex(matcher);
    candidates.push(scope.getByRole("button", { name: rx }).first());
    candidates.push(scope.getByRole("link", { name: rx }).first());
    candidates.push(scope.getByRole("menuitem", { name: rx }).first());
    candidates.push(scope.getByRole("tab", { name: rx }).first());
    candidates.push(scope.getByText(rx).first());
  }

  for (const locator of candidates) {
    if (await locator.isVisible({ timeout: timeoutMs }).catch(() => false)) {
      return locator;
    }
  }

  throw new Error(
    `No visible element found for: ${matchers
      .map((matcher) => matcher.toString())
      .join(", ")}`
  );
}

async function clickByVisibleText(page, matchers, reason) {
  const locator = await firstVisibleLocator(page, matchers);
  await locator.click();
  await waitForUi(page, reason);
  return locator;
}

async function expectVisible(page, matchers, description) {
  await firstVisibleLocator(page, matchers);
  console.log(`[ok] ${description}`);
}

async function waitForSidebar(page) {
  const sidebar = page.locator("aside, nav").first();
  await sidebar.waitFor({ state: "visible", timeout: 20000 });
  return sidebar;
}

async function clickLegalAndValidate({
  appPage,
  linkMatchers,
  headingMatcher,
  screenshotName
}) {
  const popupPromise = appPage.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  const link = await firstVisibleLocator(appPage, linkMatchers);
  await link.click();

  let targetPage = await popupPromise;
  if (!targetPage) {
    targetPage = appPage;
  }

  await waitForUi(targetPage, `opening legal link ${headingMatcher.toString()}`);
  await expectVisible(targetPage, [headingMatcher], `heading ${headingMatcher}`);

  const legalBlock = targetPage.locator("main, article, section, [class*='legal'], p").first();
  await legalBlock.waitFor({ state: "visible", timeout: 15000 });
  const pageText = (await targetPage.locator("body").innerText()).trim();

  if (pageText.length < 150) {
    throw new Error("Legal page content appears too short.");
  }

  const screenshotPath = await takeCheckpoint(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage, "returning to app tab");
  }

  return { screenshotPath, finalUrl };
}

function makeEmptyReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = { status: "FAIL", details: "Not executed." };
  }
  return report;
}

function printReport(report, legalEvidence) {
  console.log("\n================ FINAL REPORT ================\n");
  for (const field of REPORT_FIELDS) {
    const row = report[field];
    console.log(`${field}: ${row.status}`);
    if (row.details) {
      console.log(`  details: ${row.details}`);
    }
  }

  if (legalEvidence.termsUrl) {
    console.log(`\nTérminos y Condiciones URL: ${legalEvidence.termsUrl}`);
  }
  if (legalEvidence.privacyUrl) {
    console.log(`Política de Privacidad URL: ${legalEvidence.privacyUrl}`);
  }

  console.log(`\nScreenshots stored at: ${checkpointDir}`);
}

async function main() {
  if (!LOGIN_URL) {
    throw new Error(
      "Missing login URL. Provide SALEADS_LOGIN_URL (or SALEADS_URL) for the current environment."
    );
  }

  const report = makeEmptyReport();
  const legalEvidence = { termsUrl: null, privacyUrl: null };

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  try {
    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page, "initial login page");

    // Step 1: Login with Google
    try {
      const loginPopupPromise = page
        .waitForEvent("popup", { timeout: 10000 })
        .catch(() => null);

      await clickByVisibleText(
        page,
        [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Login with Google",
          /google/i
        ],
        "clicking Google login"
      );

      const loginPopup = await loginPopupPromise;
      const authPage = loginPopup || page;
      await waitForUi(authPage, "google account selection screen");

      const accountLocator = await firstVisibleLocator(authPage, [ACCOUNT_EMAIL], 8000).catch(
        () => null
      );
      if (accountLocator) {
        await accountLocator.click();
        await waitForUi(authPage, "choosing Google account");
      }

      if (loginPopup) {
        await loginPopup.waitForEvent("close", { timeout: 90000 }).catch(() => null);
        await page.bringToFront();
      }

      await waitForUi(page, "post-login dashboard");
      await waitForSidebar(page);
      await expectVisible(page, [/dashboard|inicio|panel|negocio/i], "main app interface");

      const dashboardShot = await takeCheckpoint(page, "01-dashboard-loaded.png", true);
      report["Login"] = {
        status: "PASS",
        details: `Dashboard loaded and sidebar visible. Screenshot: ${dashboardShot}`
      };
    } catch (error) {
      report["Login"] = { status: "FAIL", details: error.message };
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByVisibleText(page, ["Negocio", /negocio/i], "opening Negocio section");
      await clickByVisibleText(page, ["Mi Negocio", /mi negocio/i], "expanding Mi Negocio menu");
      await expectVisible(page, ["Agregar Negocio"], "Agregar Negocio option");
      await expectVisible(page, ["Administrar Negocios"], "Administrar Negocios option");

      const menuShot = await takeCheckpoint(page, "02-mi-negocio-menu-expanded.png");
      report["Mi Negocio menu"] = {
        status: "PASS",
        details: `Mi Negocio submenu expanded. Screenshot: ${menuShot}`
      };
    } catch (error) {
      report["Mi Negocio menu"] = { status: "FAIL", details: error.message };
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, ["Agregar Negocio"], "opening Agregar Negocio modal");
      await expectVisible(page, ["Crear Nuevo Negocio"], "modal title");
      await expectVisible(page, ["Nombre del Negocio"], "Nombre del Negocio field");
      await expectVisible(page, ["Tienes 2 de 3 negocios"], "business quota text");
      await expectVisible(page, ["Cancelar"], "Cancelar button");
      await expectVisible(page, ["Crear Negocio"], "Crear Negocio button");

      const modalShot = await takeCheckpoint(page, "03-agregar-negocio-modal.png");

      const businessNameInput = page
        .getByRole("textbox", { name: /nombre del negocio/i })
        .first();
      if (await businessNameInput.isVisible().catch(() => false)) {
        await businessNameInput.click();
        await businessNameInput.fill("Negocio Prueba Automatización");
      }

      await clickByVisibleText(page, ["Cancelar"], "closing Agregar Negocio modal");
      report["Agregar Negocio modal"] = {
        status: "PASS",
        details: `Modal validated and closed. Screenshot: ${modalShot}`
      };
    } catch (error) {
      report["Agregar Negocio modal"] = { status: "FAIL", details: error.message };
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      const adminOptionVisible = await firstVisibleLocator(
        page,
        ["Administrar Negocios"],
        4000
      ).catch(() => null);

      if (!adminOptionVisible) {
        await clickByVisibleText(page, ["Mi Negocio", /mi negocio/i], "re-expanding Mi Negocio");
      }

      await clickByVisibleText(page, ["Administrar Negocios"], "opening account page");
      await expectVisible(page, ["Información General"], "Información General section");
      await expectVisible(page, ["Detalles de la Cuenta"], "Detalles de la Cuenta section");
      await expectVisible(page, ["Tus Negocios"], "Tus Negocios section");
      await expectVisible(page, ["Sección Legal"], "Sección Legal section");

      const accountShot = await takeCheckpoint(page, "04-administrar-negocios-page.png", true);
      report["Administrar Negocios view"] = {
        status: "PASS",
        details: `Account page loaded. Screenshot: ${accountShot}`
      };
    } catch (error) {
      report["Administrar Negocios view"] = { status: "FAIL", details: error.message };
      throw error;
    }

    // Step 5: Validate Información General
    try {
      await expectVisible(page, [ACCOUNT_EMAIL, /@[a-z0-9.-]+\.[a-z]{2,}/i], "user email text");
      await expectVisible(page, ["BUSINESS PLAN"], "BUSINESS PLAN label");
      await expectVisible(page, ["Cambiar Plan"], "Cambiar Plan button");

      const infoSection = page
        .locator("section, div")
        .filter({ hasText: /información general/i })
        .first();
      const textContent = (await infoSection.innerText().catch(() => "")).trim();
      const hasNameLikeText =
        textContent
          .split("\n")
          .map((line) => line.trim())
          .filter(Boolean)
          .some(
            (line) =>
              !line.includes("@") &&
              !/información general|business plan|cambiar plan/i.test(line) &&
              line.length > 2
          ) || false;

      if (!hasNameLikeText) {
        throw new Error("Could not confidently detect user name text in Información General.");
      }

      report["Información General"] = {
        status: "PASS",
        details: "User name, email, plan label and Cambiar Plan were visible."
      };
    } catch (error) {
      report["Información General"] = { status: "FAIL", details: error.message };
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await expectVisible(page, ["Cuenta creada"], "Cuenta creada text");
      await expectVisible(page, ["Estado activo"], "Estado activo text");
      await expectVisible(page, ["Idioma seleccionado"], "Idioma seleccionado text");
      report["Detalles de la Cuenta"] = {
        status: "PASS",
        details: "All required account detail labels are visible."
      };
    } catch (error) {
      report["Detalles de la Cuenta"] = { status: "FAIL", details: error.message };
    }

    // Step 7: Validate Tus Negocios
    try {
      await expectVisible(page, ["Tus Negocios"], "Tus Negocios section heading");
      await expectVisible(page, ["Agregar Negocio"], "Agregar Negocio button");
      await expectVisible(page, ["Tienes 2 de 3 negocios"], "Tus Negocios quota text");
      report["Tus Negocios"] = {
        status: "PASS",
        details: "Business list section and required texts are visible."
      };
    } catch (error) {
      report["Tus Negocios"] = { status: "FAIL", details: error.message };
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const termsEvidence = await clickLegalAndValidate({
        appPage: page,
        linkMatchers: ["Términos y Condiciones", /términos/i],
        headingMatcher: /términos y condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png"
      });
      legalEvidence.termsUrl = termsEvidence.finalUrl;
      report["Términos y Condiciones"] = {
        status: "PASS",
        details: `Heading + legal text validated. Screenshot: ${termsEvidence.screenshotPath}. URL: ${termsEvidence.finalUrl}`
      };
    } catch (error) {
      report["Términos y Condiciones"] = { status: "FAIL", details: error.message };
    }

    // Step 9: Validate Política de Privacidad
    try {
      const privacyEvidence = await clickLegalAndValidate({
        appPage: page,
        linkMatchers: ["Política de Privacidad", /privacidad/i],
        headingMatcher: /política de privacidad/i,
        screenshotName: "06-politica-de-privacidad.png"
      });
      legalEvidence.privacyUrl = privacyEvidence.finalUrl;
      report["Política de Privacidad"] = {
        status: "PASS",
        details: `Heading + legal text validated. Screenshot: ${privacyEvidence.screenshotPath}. URL: ${privacyEvidence.finalUrl}`
      };
    } catch (error) {
      report["Política de Privacidad"] = { status: "FAIL", details: error.message };
    }
  } finally {
    printReport(report, legalEvidence);
    await browser.close();
  }

  const failed = REPORT_FIELDS.filter((field) => report[field].status !== "PASS");
  if (failed.length > 0) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(`Workflow execution failed: ${error.message}`);
  process.exitCode = 1;
});
