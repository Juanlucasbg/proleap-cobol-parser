#!/usr/bin/env node

const { chromium } = require("playwright");
const fs = require("fs/promises");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
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

const ARTIFACTS_DIR = process.env.SALEADS_ARTIFACTS_DIR
  ? path.resolve(process.env.SALEADS_ARTIFACTS_DIR)
  : path.resolve(__dirname, "artifacts");
const TIMESTAMP = new Date().toISOString().replace(/[:.]/g, "-");
const RUN_DIR = path.join(ARTIFACTS_DIR, `${TEST_NAME}-${TIMESTAMP}`);
const SCREENSHOTS_DIR = path.join(RUN_DIR, "screenshots");
const REPORT_PATH = path.join(RUN_DIR, "report.json");

const stripAccents = (value) =>
  (value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();

const toRegex = (value) => {
  if (value instanceof RegExp) return value;
  return new RegExp(value, "i");
};

async function waitForUi(page) {
  try {
    await page.waitForLoadState("domcontentloaded", { timeout: 10000 });
  } catch (_error) {
    // Continue even if DOMContentLoaded does not fire again.
  }

  try {
    await page.waitForLoadState("networkidle", { timeout: 8000 });
  } catch (_error) {
    // Some SPAs never become fully network idle.
  }

  await page.waitForTimeout(600);
}

async function captureScreenshot(page, fileName, fullPage = false) {
  const screenshotPath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function bodyContains(page, expectedText, timeoutMs = 12000) {
  const expected = stripAccents(expectedText);
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    const bodyText = await page.locator("body").innerText().catch(() => "");
    if (stripAccents(bodyText).includes(expected)) {
      return true;
    }
    await page.waitForTimeout(350);
  }

  return false;
}

async function clickByVisibleText(page, patterns) {
  const list = Array.isArray(patterns) ? patterns : [patterns];
  const roleOrder = ["button", "link", "menuitem", "tab", "treeitem"];

  for (const pattern of list) {
    const regex = toRegex(pattern);

    for (const role of roleOrder) {
      const locator = page.getByRole(role, { name: regex }).first();
      if (await locator.isVisible().catch(() => false)) {
        await locator.click();
        await waitForUi(page);
        return true;
      }
    }

    const textLocator = page.getByText(regex).first();
    if (await textLocator.isVisible().catch(() => false)) {
      await textLocator.click();
      await waitForUi(page);
      return true;
    }
  }

  return false;
}

async function assertTextVisible(page, expectedText, message) {
  const found = await bodyContains(page, expectedText);
  if (!found) {
    throw new Error(message || `Expected text not found: ${expectedText}`);
  }
}

async function assertSidebarVisible(page) {
  const sidebar = page.locator("aside").first();
  if (await sidebar.isVisible().catch(() => false)) {
    return;
  }

  const nav = page.getByRole("navigation").first();
  if (await nav.isVisible().catch(() => false)) {
    return;
  }

  throw new Error("Left sidebar navigation is not visible.");
}

function createInitialReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = {
      status: "FAIL",
      details: "Not executed.",
    };
  }
  return report;
}

function setReport(report, field, status, details) {
  report[field] = {
    status,
    details,
  };
}

async function withStep(report, field, fn) {
  try {
    const details = await fn();
    setReport(report, field, "PASS", details || "Validated successfully.");
  } catch (error) {
    setReport(
      report,
      field,
      "FAIL",
      error && error.message ? error.message : String(error),
    );
  }
}

async function openLegalPage({
  appPage,
  context,
  linkPattern,
  headingText,
  screenshotFileName,
}) {
  const previousUrl = appPage.url();
  const popupPromise = context
    .waitForEvent("page", { timeout: 12000 })
    .catch(() => null);

  const clicked = await clickByVisibleText(appPage, linkPattern);
  if (!clicked) {
    throw new Error(`Legal link not found: ${String(linkPattern)}`);
  }

  const maybePopup = await popupPromise;
  const legalPage = maybePopup || appPage;
  await waitForUi(legalPage);

  await assertTextVisible(
    legalPage,
    headingText,
    `Expected legal heading not found: ${headingText}`,
  );

  const legalText = await legalPage.locator("body").innerText().catch(() => "");
  if (stripAccents(legalText).length < 120) {
    throw new Error("Legal content appears too short or not visible.");
  }

  const screenshotPath = await captureScreenshot(
    legalPage,
    screenshotFileName,
    true,
  );
  const finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
  } else if (appPage.url() !== previousUrl) {
    await appPage.goBack().catch(() => {});
    await waitForUi(appPage);
  }

  return { screenshotPath, finalUrl };
}

async function main() {
  await fs.mkdir(SCREENSHOTS_DIR, { recursive: true });

  const report = createInitialReport();
  const artifacts = {
    screenshots: {},
    legalUrls: {},
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const googleAccount =
    process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;
  const userDataDir = path.resolve(
    process.env.SALEADS_USER_DATA_DIR || path.join(__dirname, ".pw-user-data"),
  );
  const headless = String(process.env.SALEADS_HEADLESS || "false") === "true";

  const context = await chromium.launchPersistentContext(userDataDir, {
    headless,
  });

  let appPage = context.pages()[0] || (await context.newPage());

  let runError = null;
  try {
    if (loginUrl) {
      await appPage.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(appPage);
    } else {
      const currentUrl = appPage.url();
      if (!currentUrl || currentUrl === "about:blank") {
        throw new Error(
          "No login URL configured. Set SALEADS_LOGIN_URL or open the login page in persistent browser profile.",
        );
      }
    }

    await withStep(report, "Login", async () => {
      const popupPromise = context
        .waitForEvent("page", { timeout: 12000 })
        .catch(() => null);

      const clickedLogin = await clickByVisibleText(appPage, [
        /sign in with google/i,
        /iniciar sesion con google/i,
        /inicia sesion con google/i,
        /continuar con google/i,
        /google/i,
      ]);

      if (!clickedLogin) {
        throw new Error("Google login button was not found.");
      }

      const googlePage = (await popupPromise) || appPage;
      await waitForUi(googlePage);

      await clickByVisibleText(googlePage, [
        new RegExp(googleAccount.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
      ]).catch(() => false);

      if (googlePage !== appPage) {
        await googlePage.waitForEvent("close", { timeout: 45000 }).catch(() => {
          // If popup does not auto-close, continue and inspect app pages.
        });

        const candidates = context.pages();
        for (const pageCandidate of candidates) {
          const bodyText = await pageCandidate
            .locator("body")
            .innerText()
            .catch(() => "");
          const normalized = stripAccents(bodyText);
          if (
            normalized.includes("mi negocio") ||
            normalized.includes("negocio") ||
            normalized.includes("dashboard")
          ) {
            appPage = pageCandidate;
            break;
          }
        }
      }

      await waitForUi(appPage);
      await assertSidebarVisible(appPage);

      artifacts.screenshots.dashboard = await captureScreenshot(
        appPage,
        "01-dashboard.png",
      );

      return "Main interface and sidebar are visible after Google login.";
    });

    await withStep(report, "Mi Negocio menu", async () => {
      await assertSidebarVisible(appPage);

      await clickByVisibleText(appPage, [/negocio/i]).catch(() => false);
      const clickedMiNegocio = await clickByVisibleText(appPage, [/mi negocio/i]);
      if (!clickedMiNegocio) {
        throw new Error("'Mi Negocio' option was not found in sidebar.");
      }

      await assertTextVisible(
        appPage,
        "Agregar Negocio",
        "'Agregar Negocio' is not visible after expanding menu.",
      );
      await assertTextVisible(
        appPage,
        "Administrar Negocios",
        "'Administrar Negocios' is not visible after expanding menu.",
      );

      artifacts.screenshots.menuExpanded = await captureScreenshot(
        appPage,
        "02-mi-negocio-menu-expanded.png",
      );

      return "Mi Negocio submenu expanded and both options are visible.";
    });

    await withStep(report, "Agregar Negocio modal", async () => {
      const clickedAgregarNegocio = await clickByVisibleText(appPage, [
        /^agregar negocio$/i,
      ]);
      if (!clickedAgregarNegocio) {
        throw new Error("'Agregar Negocio' could not be clicked.");
      }

      await assertTextVisible(
        appPage,
        "Crear Nuevo Negocio",
        "Modal title 'Crear Nuevo Negocio' is not visible.",
      );
      await assertTextVisible(
        appPage,
        "Nombre del Negocio",
        "Input label 'Nombre del Negocio' is not visible.",
      );
      await assertTextVisible(
        appPage,
        "Tienes 2 de 3 negocios",
        "Business quota text is not visible.",
      );
      await assertTextVisible(
        appPage,
        "Cancelar",
        "'Cancelar' button not found in modal.",
      );
      await assertTextVisible(
        appPage,
        "Crear Negocio",
        "'Crear Negocio' button not found in modal.",
      );

      const namedInput = appPage.getByLabel(/nombre del negocio/i).first();
      if (await namedInput.isVisible().catch(() => false)) {
        await namedInput.click();
        await namedInput.fill("Negocio Prueba Automatizacion");
      } else {
        const modalInput = appPage.locator('[role="dialog"] input').first();
        if (await modalInput.isVisible().catch(() => false)) {
          await modalInput.click();
          await modalInput.fill("Negocio Prueba Automatizacion");
        }
      }

      artifacts.screenshots.agregarModal = await captureScreenshot(
        appPage,
        "03-agregar-negocio-modal.png",
      );

      await clickByVisibleText(appPage, [/cancelar/i]).catch(() => false);
      await waitForUi(appPage);

      return "Agregar Negocio modal validated and closed with Cancelar.";
    });

    await withStep(report, "Administrar Negocios view", async () => {
      const administrarVisible = await bodyContains(appPage, "Administrar Negocios", 3000);
      if (!administrarVisible) {
        await clickByVisibleText(appPage, [/mi negocio/i]).catch(() => false);
      }

      const clickedAdministrar = await clickByVisibleText(appPage, [
        /administrar negocios/i,
      ]);
      if (!clickedAdministrar) {
        throw new Error("'Administrar Negocios' option was not found.");
      }

      await assertTextVisible(
        appPage,
        "Información General",
        "'Información General' section not found.",
      );
      await assertTextVisible(
        appPage,
        "Detalles de la Cuenta",
        "'Detalles de la Cuenta' section not found.",
      );
      await assertTextVisible(
        appPage,
        "Tus Negocios",
        "'Tus Negocios' section not found.",
      );
      await assertTextVisible(
        appPage,
        "Sección Legal",
        "'Sección Legal' section not found.",
      );

      artifacts.screenshots.accountPage = await captureScreenshot(
        appPage,
        "04-administrar-negocios.png",
        true,
      );

      return "Administrar Negocios page and all main sections are visible.";
    });

    await withStep(report, "Información General", async () => {
      await assertTextVisible(appPage, "BUSINESS PLAN", "'BUSINESS PLAN' text is missing.");
      await assertTextVisible(appPage, "Cambiar Plan", "'Cambiar Plan' button is missing.");

      const nameLikeTextVisible = await appPage
        .locator(
          "text=/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/",
        )
        .first()
        .isVisible()
        .catch(() => false);
      if (!nameLikeTextVisible) {
        throw new Error("User name-like text does not appear visible.");
      }

      const emailPatternFound = await bodyContains(appPage, "@", 3000);
      if (!emailPatternFound) {
        throw new Error("User email does not appear visible.");
      }

      return "User info, plan label and 'Cambiar Plan' button are visible.";
    });

    await withStep(report, "Detalles de la Cuenta", async () => {
      await assertTextVisible(appPage, "Cuenta creada", "'Cuenta creada' text not visible.");
      await assertTextVisible(appPage, "Estado activo", "'Estado activo' text not visible.");
      await assertTextVisible(
        appPage,
        "Idioma seleccionado",
        "'Idioma seleccionado' text not visible.",
      );
      return "Detalles de la Cuenta section validated.";
    });

    await withStep(report, "Tus Negocios", async () => {
      await assertTextVisible(appPage, "Tus Negocios", "Business list section title is missing.");
      await assertTextVisible(
        appPage,
        "Agregar Negocio",
        "'Agregar Negocio' button in business section is missing.",
      );
      await assertTextVisible(
        appPage,
        "Tienes 2 de 3 negocios",
        "Business quota text is missing in Tus Negocios.",
      );
      return "Tus Negocios section validated.";
    });

    await withStep(report, "Términos y Condiciones", async () => {
      const legalResult = await openLegalPage({
        appPage,
        context,
        linkPattern: [/t[eé]rminos y condiciones/i],
        headingText: "Términos y Condiciones",
        screenshotFileName: "05-terminos-y-condiciones.png",
      });

      artifacts.screenshots.terminos = legalResult.screenshotPath;
      artifacts.legalUrls.terminos = legalResult.finalUrl;
      return `Legal page validated. URL: ${legalResult.finalUrl}`;
    });

    await withStep(report, "Política de Privacidad", async () => {
      const legalResult = await openLegalPage({
        appPage,
        context,
        linkPattern: [/pol[ií]tica de privacidad/i],
        headingText: "Política de Privacidad",
        screenshotFileName: "06-politica-de-privacidad.png",
      });

      artifacts.screenshots.politica = legalResult.screenshotPath;
      artifacts.legalUrls.politica = legalResult.finalUrl;
      return `Legal page validated. URL: ${legalResult.finalUrl}`;
    });
  } catch (error) {
    if (report.Login.details === "Not executed.") {
      setReport(
        report,
        "Login",
        "FAIL",
        error && error.message ? error.message : String(error),
      );
    }
    runError = error;
  } finally {
    const finalPayload = {
      name: TEST_NAME,
      generatedAt: new Date().toISOString(),
      report,
      artifacts,
      notes: {
        loginUrl: loginUrl || "Not provided",
        googleAccount,
      },
    };

    await fs.writeFile(REPORT_PATH, JSON.stringify(finalPayload, null, 2), "utf8");

    // Keep browser context closed to avoid dangling processes.
    await context.close();

    console.log(JSON.stringify(finalPayload, null, 2));
    console.log(`Report written to: ${REPORT_PATH}`);
  }

  if (runError) {
    throw runError;
  }
}

main().catch(async (error) => {
  console.error(error);
  process.exitCode = 1;
});
