#!/usr/bin/env node

/* eslint-disable no-console */

const fs = require("fs/promises");
const path = require("path");

const {
  chromium,
  errors: { TimeoutError },
} = require("playwright");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL;
const PLAYWRIGHT_CDP_URL = process.env.PLAYWRIGHT_CDP_URL;
const HEADLESS = process.env.HEADLESS === "true";
const SLOW_MO = Number(process.env.SLOW_MO || "0");

const REPORT_KEYS = [
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

const CHECKPOINT_DIR = path.join(
  process.cwd(),
  "artifacts",
  "saleads_mi_negocio_full_test",
  new Date().toISOString().replace(/[:.]/g, "-")
);

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page) {
  try {
    await page.waitForLoadState("domcontentloaded", { timeout: 10000 });
  } catch (_) {
    // Keep going if the app uses SPA transitions heavily.
  }

  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch (_) {
    // Many apps maintain background requests; this is best effort.
  }

  await page.waitForTimeout(800);
}

async function safeScreenshot(page, fileName, fullPage = false) {
  const filePath = path.join(CHECKPOINT_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function isVisible(locator) {
  try {
    return await locator.first().isVisible({ timeout: 2000 });
  } catch (_) {
    return false;
  }
}

function textLocators(page, text, exact = true) {
  return [
    page.getByRole("button", { name: text, exact }),
    page.getByRole("link", { name: text, exact }),
    page.getByRole("menuitem", { name: text, exact }),
    page.getByRole("tab", { name: text, exact }),
    page.getByText(text, { exact }),
  ];
}

async function findFirstVisible(page, texts, exact = true) {
  for (const text of texts) {
    for (const locator of textLocators(page, text, exact)) {
      if (await isVisible(locator)) {
        return locator.first();
      }
    }
  }
  return null;
}

async function clickByVisibleText(page, texts, options = {}) {
  const locator = await findFirstVisible(page, texts, options.exact ?? true);
  if (!locator) {
    throw new Error(`Element not found using visible texts: ${texts.join(", ")}`);
  }
  await locator.click();
  await waitForUi(page);
}

async function validateVisible(page, validations) {
  for (const validation of validations) {
    if (validation.type === "text") {
      const locator = await findFirstVisible(page, validation.values, validation.exact ?? true);
      if (!locator || !(await isVisible(locator))) {
        throw new Error(`Validation failed: missing text -> ${validation.values.join(" / ")}`);
      }
    } else if (validation.type === "locator") {
      if (!(await isVisible(page.locator(validation.selector)))) {
        throw new Error(`Validation failed: missing locator -> ${validation.selector}`);
      }
    } else {
      throw new Error(`Unknown validation type: ${validation.type}`);
    }
  }
}

async function openConnectedOrFreshBrowser() {
  if (PLAYWRIGHT_CDP_URL) {
    const browser = await chromium.connectOverCDP(PLAYWRIGHT_CDP_URL);
    const context = browser.contexts()[0] || (await browser.newContext());
    const page = context.pages()[0] || (await context.newPage());
    return { browser, context, page, connected: true };
  }

  const browser = await chromium.launch({ headless: HEADLESS, slowMo: SLOW_MO });
  const context = await browser.newContext();
  const page = await context.newPage();
  return { browser, context, page, connected: false };
}

async function maybeSelectGoogleAccount(context, page) {
  const popupPromise = context
    .waitForEvent("page", { timeout: 7000 })
    .catch(() => null);

  await waitForUi(page);
  const popup = await popupPromise;
  const targetPage = popup || page;

  try {
    await targetPage.waitForLoadState("domcontentloaded", { timeout: 10000 });
  } catch (_) {
    // Best effort.
  }

  const accountOption = await findFirstVisible(
    targetPage,
    [GOOGLE_ACCOUNT_EMAIL, "Usar otra cuenta", "Use another account"],
    false
  );

  if (accountOption && (await isVisible(accountOption))) {
    await accountOption.click();
    await waitForUi(targetPage);
  }

  if (popup) {
    try {
      await popup.waitForEvent("close", { timeout: 25000 });
    } catch (_) {
      // If popup does not close automatically, continue with current app page.
    }
  }

  await waitForUi(page);
}

async function openLegalLinkAndReturn({
  page,
  context,
  linkTexts,
  headingTexts,
  screenshotName,
}) {
  const currentPage = page;
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickByVisibleText(page, linkTexts, { exact: false });
  const newPage = await popupPromise;
  const legalPage = newPage || currentPage;

  await waitForUi(legalPage);

  await validateVisible(legalPage, [
    { type: "text", values: headingTexts, exact: false },
  ]);

  const hasBodyContent =
    (await legalPage.locator("p, article, section").count()) > 0 ||
    ((await legalPage.locator("body").innerText()).trim().length > 200);
  if (!hasBodyContent) {
    throw new Error("Validation failed: legal content text is not visible.");
  }

  await safeScreenshot(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (newPage) {
    await newPage.close();
    await currentPage.bringToFront();
    await waitForUi(currentPage);
  } else {
    await currentPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(currentPage);
  }

  return finalUrl;
}

async function run() {
  await ensureDir(CHECKPOINT_DIR);

  const report = Object.fromEntries(REPORT_KEYS.map((key) => [key, "FAIL"]));
  const evidence = {
    screenshots: [],
    finalUrls: {},
  };

  const { browser, context, page, connected } = await openConnectedOrFreshBrowser();

  try {
    if (SALEADS_LOGIN_URL) {
      await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (!connected) {
      throw new Error(
        "SALEADS_LOGIN_URL is required unless PLAYWRIGHT_CDP_URL is used with a browser already on SaleADS login."
      );
    }

    // Step 1: Login with Google
    await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesion con Google",
      "Iniciar sesion",
      "Login with Google",
      "Continuar con Google",
    ], { exact: false });

    await maybeSelectGoogleAccount(context, page);

    await validateVisible(page, [
      { type: "locator", selector: "nav, aside" },
      { type: "text", values: ["Negocio"], exact: false },
    ]);

    evidence.screenshots.push(await safeScreenshot(page, "01_dashboard_loaded.png", true));
    report.Login = "PASS";

    // Step 2: Open Mi Negocio menu
    await clickByVisibleText(page, ["Negocio"], { exact: false });
    await clickByVisibleText(page, ["Mi Negocio"], { exact: false });

    await validateVisible(page, [
      { type: "text", values: ["Agregar Negocio"], exact: false },
      { type: "text", values: ["Administrar Negocios"], exact: false },
    ]);

    evidence.screenshots.push(await safeScreenshot(page, "02_mi_negocio_menu_expanded.png", true));
    report["Mi Negocio menu"] = "PASS";

    // Step 3: Validate Agregar Negocio modal
    await clickByVisibleText(page, ["Agregar Negocio"], { exact: false });

    await validateVisible(page, [
      { type: "text", values: ["Crear Nuevo Negocio"], exact: false },
      { type: "text", values: ["Nombre del Negocio"], exact: false },
      { type: "text", values: ["Tienes 2 de 3 negocios"], exact: false },
      { type: "text", values: ["Cancelar"], exact: true },
      { type: "text", values: ["Crear Negocio"], exact: false },
    ]);

    evidence.screenshots.push(await safeScreenshot(page, "03_agregar_negocio_modal.png", true));

    const negocioInput = page
      .getByLabel("Nombre del Negocio", { exact: false })
      .or(page.getByPlaceholder("Nombre del Negocio"));

    if (await isVisible(negocioInput)) {
      await negocioInput.first().click();
      await negocioInput.first().fill("Negocio Prueba Automatizacion");
      await waitForUi(page);
    }

    await clickByVisibleText(page, ["Cancelar"], { exact: true });
    report["Agregar Negocio modal"] = "PASS";

    // Step 4: Open Administrar Negocios
    const administrarVisible = await isVisible(
      (await findFirstVisible(page, ["Administrar Negocios"], false)) || page.locator("_never_")
    );
    if (!administrarVisible) {
      await clickByVisibleText(page, ["Mi Negocio"], { exact: false });
    }
    await clickByVisibleText(page, ["Administrar Negocios"], { exact: false });

    await validateVisible(page, [
      { type: "text", values: ["Informacion General"], exact: false },
      { type: "text", values: ["Detalles de la Cuenta"], exact: false },
      { type: "text", values: ["Tus Negocios"], exact: false },
      { type: "text", values: ["Seccion Legal"], exact: false },
    ]);

    evidence.screenshots.push(await safeScreenshot(page, "04_administrar_negocios_full.png", true));
    report["Administrar Negocios view"] = "PASS";

    // Step 5: Validate Informacion General
    await validateVisible(page, [
      { type: "text", values: ["BUSINESS PLAN"], exact: false },
      { type: "text", values: ["Cambiar Plan"], exact: false },
      { type: "text", values: ["@"], exact: false },
    ]);
    report["Información General"] = "PASS";

    // Step 6: Validate Detalles de la Cuenta
    await validateVisible(page, [
      { type: "text", values: ["Cuenta creada"], exact: false },
      { type: "text", values: ["Estado activo"], exact: false },
      { type: "text", values: ["Idioma seleccionado"], exact: false },
    ]);
    report["Detalles de la Cuenta"] = "PASS";

    // Step 7: Validate Tus Negocios
    await validateVisible(page, [
      { type: "text", values: ["Tus Negocios"], exact: false },
      { type: "text", values: ["Agregar Negocio"], exact: false },
      { type: "text", values: ["Tienes 2 de 3 negocios"], exact: false },
    ]);
    report["Tus Negocios"] = "PASS";

    // Step 8: Validate Terminos y Condiciones
    evidence.finalUrls["Términos y Condiciones"] = await openLegalLinkAndReturn({
      page,
      context,
      linkTexts: ["Terminos y Condiciones", "Términos y Condiciones"],
      headingTexts: ["Terminos y Condiciones", "Términos y Condiciones"],
      screenshotName: "05_terminos_y_condiciones.png",
    });
    report["Términos y Condiciones"] = "PASS";

    // Step 9: Validate Politica de Privacidad
    evidence.finalUrls["Política de Privacidad"] = await openLegalLinkAndReturn({
      page,
      context,
      linkTexts: ["Politica de Privacidad", "Política de Privacidad"],
      headingTexts: ["Politica de Privacidad", "Política de Privacidad"],
      screenshotName: "06_politica_de_privacidad.png",
    });
    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    const errorMessage =
      error instanceof TimeoutError ? `Timeout: ${error.message}` : error.message;
    console.error("[saleads_mi_negocio_full_test] Execution failed:", errorMessage);
  } finally {
    await browser.close();
  }

  console.log("\n=== FINAL REPORT ===");
  console.log(JSON.stringify(report, null, 2));
  console.log("\n=== EVIDENCE ===");
  console.log(JSON.stringify(evidence, null, 2));

  const failed = Object.values(report).some((status) => status !== "PASS");
  process.exitCode = failed ? 1 : 0;
}

run().catch((error) => {
  console.error("[saleads_mi_negocio_full_test] Unhandled error:", error);
  process.exit(1);
});
