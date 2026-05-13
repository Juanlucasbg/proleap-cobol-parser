#!/usr/bin/env node

const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const DEFAULT_TIMEOUT = 20_000;

async function safeWaitForUi(page) {
  await page.waitForTimeout(700);
  await page.waitForLoadState("domcontentloaded", { timeout: 10_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
}

function normalizeFileSegment(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9-_.]/g, "_")
    .toLowerCase();
}

async function ensureVisible(locator, label, timeout = DEFAULT_TIMEOUT) {
  await locator.first().waitFor({ state: "visible", timeout });
  return { label, pass: true };
}

async function clickVisible(page, text, options = {}) {
  const exact = options.exact ?? true;
  const timeout = options.timeout ?? DEFAULT_TIMEOUT;
  const candidates = [
    page.getByRole("button", { name: text, exact }),
    page.getByRole("link", { name: text, exact }),
    page.getByRole("menuitem", { name: text, exact }),
    page.getByRole("tab", { name: text, exact }),
    page.getByLabel(text, { exact }),
    page.getByText(text, { exact }),
  ];

  for (const locator of candidates) {
    if ((await locator.count()) > 0) {
      const target = locator.first();
      if (await target.isVisible().catch(() => false)) {
        await target.click({ timeout });
        await safeWaitForUi(page);
        return true;
      }
    }
  }

  return false;
}

async function screenshot(page, outputDir, name, fullPage = false) {
  const filename = `${normalizeFileSegment(name)}.png`;
  const targetPath = path.join(outputDir, filename);
  await page.screenshot({ path: targetPath, fullPage });
  return targetPath;
}

async function maybeSelectGoogleAccount(page, email) {
  const accountRow = page.getByText(email, { exact: true }).first();
  if (await accountRow.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await accountRow.click();
    await safeWaitForUi(page);
    return true;
  }
  return false;
}

async function withLegalPage(page, linkText, expectedHeading, outputDir) {
  const context = page.context();
  const existingPages = new Set(context.pages());
  const newPagePromise = context
    .waitForEvent("page", { timeout: 8_000 })
    .catch(() => null);

  const clicked = await clickVisible(page, linkText);
  if (!clicked) {
    throw new Error(`Could not click legal link: ${linkText}`);
  }

  let legalPage = await newPagePromise;
  const openedNewTab = legalPage && !existingPages.has(legalPage);
  if (!openedNewTab) {
    legalPage = page;
  }

  await legalPage.waitForLoadState("domcontentloaded", { timeout: DEFAULT_TIMEOUT });
  await safeWaitForUi(legalPage);

  await ensureVisible(
    legalPage.getByRole("heading", { name: expectedHeading }).or(legalPage.getByText(expectedHeading)),
    `${expectedHeading} heading visible`,
  );

  const paragraphCount = await legalPage.locator("p").count();
  const hasLegalText = paragraphCount > 0 || (await legalPage.locator("article, main, section").count()) > 0;
  if (!hasLegalText) {
    throw new Error(`No legal content detected on ${linkText} page.`);
  }

  const screenshotPath = await screenshot(legalPage, outputDir, `legal_${linkText}`, true);
  const finalUrl = legalPage.url();

  if (openedNewTab) {
    await legalPage.close();
    await page.bringToFront();
    await safeWaitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: DEFAULT_TIMEOUT }).catch(() => {});
    await safeWaitForUi(page);
  }

  return { screenshotPath, finalUrl };
}

async function main() {
  const outputDir = path.join(
    process.cwd(),
    "artifacts",
    new Date().toISOString().replace(/[:.]/g, "-"),
  );
  await fs.mkdir(outputDir, { recursive: true });

  const report = {
    Login: { status: "FAIL", notes: [] },
    "Mi Negocio menu": { status: "FAIL", notes: [] },
    "Agregar Negocio modal": { status: "FAIL", notes: [] },
    "Administrar Negocios view": { status: "FAIL", notes: [] },
    "Información General": { status: "FAIL", notes: [] },
    "Detalles de la Cuenta": { status: "FAIL", notes: [] },
    "Tus Negocios": { status: "FAIL", notes: [] },
    "Términos y Condiciones": { status: "FAIL", notes: [] },
    "Política de Privacidad": { status: "FAIL", notes: [] },
  };

  const evidence = {};
  let browser;
  let context;
  let page;

  try {
    const wsEndpoint = process.env.PLAYWRIGHT_WS_ENDPOINT;
    const startUrl = process.env.SALEADS_START_URL;
    const headless = process.env.HEADLESS !== "false";

    if (wsEndpoint) {
      browser = await chromium.connectOverCDP(wsEndpoint);
      context = browser.contexts()[0] || (await browser.newContext());
      page = context.pages()[0] || (await context.newPage());
    } else {
      browser = await chromium.launch({ headless });
      context = await browser.newContext();
      page = await context.newPage();
      if (startUrl) {
        await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      }
    }

    if (page.url() === "about:blank" && !startUrl) {
      throw new Error(
        "No starting page available. Provide SALEADS_START_URL or PLAYWRIGHT_WS_ENDPOINT with an existing SaleADS login tab.",
      );
    }

    await page.setDefaultTimeout(DEFAULT_TIMEOUT);
    await safeWaitForUi(page);

    // Step 1: Login with Google
    const loginClicked =
      (await clickVisible(page, "Sign in with Google")) ||
      (await clickVisible(page, "Iniciar sesión con Google")) ||
      (await clickVisible(page, "Login con Google")) ||
      (await clickVisible(page, "Acceder con Google"));
    if (!loginClicked) {
      throw new Error("Could not find a Google login button.");
    }
    await maybeSelectGoogleAccount(page, "juanlucasbarbiergarzon@gmail.com");
    await safeWaitForUi(page);
    await ensureVisible(
      page
        .locator("aside")
        .or(page.getByText("Negocio"))
        .or(page.getByText("Mi Negocio")),
      "Left sidebar visible",
    );
    evidence.dashboard = await screenshot(page, outputDir, "dashboard_loaded", true);
    report.Login.status = "PASS";
    report.Login.notes.push("Main interface and sidebar visible after login.");

    // Step 2: Open Mi Negocio menu
    await clickVisible(page, "Negocio").catch(() => {});
    const miNegocioClicked = await clickVisible(page, "Mi Negocio");
    if (!miNegocioClicked) {
      throw new Error("Could not click Mi Negocio.");
    }
    await ensureVisible(page.getByText("Agregar Negocio"), "Agregar Negocio visible");
    await ensureVisible(page.getByText("Administrar Negocios"), "Administrar Negocios visible");
    evidence.miNegocioMenu = await screenshot(page, outputDir, "mi_negocio_menu_expanded", true);
    report["Mi Negocio menu"].status = "PASS";
    report["Mi Negocio menu"].notes.push("Submenu expanded with required options.");

    // Step 3: Validate Agregar Negocio modal
    if (!(await clickVisible(page, "Agregar Negocio"))) {
      throw new Error("Could not click Agregar Negocio.");
    }
    await ensureVisible(page.getByText("Crear Nuevo Negocio"), "Crear Nuevo Negocio visible");
    await ensureVisible(
      page.getByLabel("Nombre del Negocio").or(page.getByPlaceholder("Nombre del Negocio")),
      "Nombre del Negocio input visible",
    );
    await ensureVisible(page.getByText("Tienes 2 de 3 negocios"), "Usage text visible");
    await ensureVisible(page.getByRole("button", { name: "Cancelar" }), "Cancelar button visible");
    await ensureVisible(page.getByRole("button", { name: "Crear Negocio" }), "Crear Negocio button visible");
    evidence.agregarNegocioModal = await screenshot(page, outputDir, "agregar_negocio_modal", true);

    const nameInput = page.getByLabel("Nombre del Negocio").or(page.getByPlaceholder("Nombre del Negocio")).first();
    if (await nameInput.isVisible().catch(() => false)) {
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
      await safeWaitForUi(page);
    }
    await clickVisible(page, "Cancelar");
    report["Agregar Negocio modal"].status = "PASS";
    report["Agregar Negocio modal"].notes.push("Modal structure and controls validated.");

    // Step 4: Open Administrar Negocios
    await clickVisible(page, "Mi Negocio").catch(() => {});
    if (!(await clickVisible(page, "Administrar Negocios"))) {
      throw new Error("Could not click Administrar Negocios.");
    }
    await ensureVisible(page.getByText("Información General"), "Información General section visible");
    await ensureVisible(page.getByText("Detalles de la Cuenta"), "Detalles de la Cuenta section visible");
    await ensureVisible(page.getByText("Tus Negocios"), "Tus Negocios section visible");
    await ensureVisible(page.getByText("Sección Legal"), "Sección Legal section visible");
    evidence.accountPage = await screenshot(page, outputDir, "administrar_negocios_account_page", true);
    report["Administrar Negocios view"].status = "PASS";
    report["Administrar Negocios view"].notes.push("All account sections rendered.");

    // Step 5: Información General
    const infoGeneralChecks = [
      page.getByText(/BUSINESS PLAN/i),
      page.getByRole("button", { name: "Cambiar Plan" }),
    ];
    for (const check of infoGeneralChecks) {
      await ensureVisible(check, "Información General check");
    }
    const userNameLike = page.locator("section, div").filter({ hasText: /@/ }).first();
    if (!(await userNameLike.isVisible().catch(() => false))) {
      throw new Error("Could not confidently detect user email/name in Información General.");
    }
    report["Información General"].status = "PASS";
    report["Información General"].notes.push("User identity, plan text, and Cambiar Plan are visible.");

    // Step 6: Detalles de la Cuenta
    await ensureVisible(page.getByText("Cuenta creada"), "Cuenta creada visible");
    await ensureVisible(page.getByText("Estado activo"), "Estado activo visible");
    await ensureVisible(page.getByText("Idioma seleccionado"), "Idioma seleccionado visible");
    report["Detalles de la Cuenta"].status = "PASS";
    report["Detalles de la Cuenta"].notes.push("All required account details labels are visible.");

    // Step 7: Tus Negocios
    await ensureVisible(page.getByText("Tus Negocios"), "Tus Negocios section visible");
    await ensureVisible(page.getByRole("button", { name: "Agregar Negocio" }), "Agregar Negocio button exists");
    await ensureVisible(page.getByText("Tienes 2 de 3 negocios"), "Usage text visible in Tus Negocios");
    report["Tus Negocios"].status = "PASS";
    report["Tus Negocios"].notes.push("Business section, button, and quota text validated.");

    // Step 8: Términos y Condiciones
    const termsEvidence = await withLegalPage(page, "Términos y Condiciones", "Términos y Condiciones", outputDir);
    evidence.terms = termsEvidence.screenshotPath;
    evidence.termsUrl = termsEvidence.finalUrl;
    report["Términos y Condiciones"].status = "PASS";
    report["Términos y Condiciones"].notes.push(`URL: ${termsEvidence.finalUrl}`);

    // Step 9: Política de Privacidad
    const privacyEvidence = await withLegalPage(page, "Política de Privacidad", "Política de Privacidad", outputDir);
    evidence.privacy = privacyEvidence.screenshotPath;
    evidence.privacyUrl = privacyEvidence.finalUrl;
    report["Política de Privacidad"].status = "PASS";
    report["Política de Privacidad"].notes.push(`URL: ${privacyEvidence.finalUrl}`);

    // Step 10: Final report
    const finalReportPath = path.join(outputDir, "final-report.json");
    await fs.writeFile(
      finalReportPath,
      JSON.stringify(
        {
          workflow: "saleads_mi_negocio_full_test",
          generatedAt: new Date().toISOString(),
          report,
          evidence,
        },
        null,
        2,
      ),
      "utf8",
    );

    console.log("=== Final Report ===");
    for (const [name, result] of Object.entries(report)) {
      console.log(`${name}: ${result.status}`);
      for (const note of result.notes) {
        console.log(`  - ${note}`);
      }
    }
    console.log(`Artifacts directory: ${outputDir}`);
    console.log(`Final report file: ${finalReportPath}`);
  } catch (error) {
    console.error("Workflow execution failed:", error.message);
    console.error(error.stack);
    process.exitCode = 1;
  } finally {
    if (browser && !process.env.PLAYWRIGHT_WS_ENDPOINT) {
      await browser.close();
    }
  }
}

main();
