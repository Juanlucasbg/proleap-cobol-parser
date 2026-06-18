#!/usr/bin/env node

const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const RUN_ID = new Date().toISOString().replace(/[:.]/g, "-");
const EVIDENCE_DIR = path.join(__dirname, "evidence", RUN_ID);

const report = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL",
};

const legalUrls = {
  "Términos y Condiciones": null,
  "Política de Privacidad": null,
};

async function ensureEvidenceDir() {
  await fs.mkdir(EVIDENCE_DIR, { recursive: true });
}

async function waitForUi(page, timeout = 15000) {
  try {
    await page.waitForLoadState("networkidle", { timeout });
  } catch (_) {
    await page.waitForTimeout(800);
  }
}

async function checkpoint(page, filename, fullPage = false) {
  const filePath = path.join(EVIDENCE_DIR, filename);
  await page.screenshot({ path: filePath, fullPage });
}

async function clickAndWait(page, locator, label) {
  await locator.first().waitFor({ state: "visible", timeout: 15000 });
  await locator.first().click();
  await waitForUi(page);
  console.log(`Clicked: ${label}`);
}

async function expectVisible(locator, description, timeout = 15000) {
  await locator.first().waitFor({ state: "visible", timeout });
  console.log(`Validated: ${description}`);
}

async function openFromCurrentOrEnv(page) {
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (loginUrl) {
    console.log(`Opening login URL from env: ${loginUrl}`);
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  console.log(
    "SALEADS_LOGIN_URL not provided. Expecting the page to already be at SaleADS login."
  );
}

async function loginWithGoogle(page) {
  const signInButton = page
    .getByRole("button", { name: /google|iniciar sesi[oó]n|sign in/i })
    .first();
  await clickAndWait(page, signInButton, "Login / Google sign-in button");

  const accountOption = page.getByText(ACCOUNT_EMAIL, { exact: true });
  try {
    if (await accountOption.first().isVisible({ timeout: 8000 })) {
      await clickAndWait(page, accountOption, `Google account ${ACCOUNT_EMAIL}`);
    }
  } catch (_) {
    console.log("Google account selector did not appear; continuing.");
  }

  await expectVisible(
    page.getByRole("navigation").or(page.locator("aside")),
    "Main app interface with left sidebar visible",
    30000
  );
  await checkpoint(page, "01-dashboard-loaded.png", true);
  report.Login = "PASS";
}

async function openMiNegocioMenu(page) {
  const negocioSection = page.getByText("Negocio", { exact: true }).first();
  const miNegocioOption = page.getByText("Mi Negocio", { exact: true }).first();

  await expectVisible(negocioSection, "Negocio section in sidebar");
  await clickAndWait(page, miNegocioOption, "Mi Negocio");

  await expectVisible(page.getByText("Agregar Negocio", { exact: true }), "Agregar Negocio visible");
  await expectVisible(
    page.getByText("Administrar Negocios", { exact: true }),
    "Administrar Negocios visible"
  );
  await checkpoint(page, "02-mi-negocio-expanded.png");
  report["Mi Negocio menu"] = "PASS";
}

async function validateAgregarNegocioModal(page) {
  await clickAndWait(page, page.getByText("Agregar Negocio", { exact: true }), "Agregar Negocio");

  const modal = page.getByRole("dialog");
  await expectVisible(modal, "Agregar Negocio modal visible");
  await expectVisible(page.getByText("Crear Nuevo Negocio", { exact: true }), "Modal title");
  await expectVisible(page.getByLabel("Nombre del Negocio"), "Nombre del Negocio input");
  await expectVisible(
    page.getByText("Tienes 2 de 3 negocios", { exact: true }),
    "Business quota text"
  );
  await expectVisible(page.getByRole("button", { name: "Cancelar" }), "Cancelar button");
  await expectVisible(page.getByRole("button", { name: "Crear Negocio" }), "Crear Negocio button");

  const nombreInput = page.getByLabel("Nombre del Negocio");
  await nombreInput.fill("Negocio Prueba Automatización");
  await checkpoint(page, "03-agregar-negocio-modal.png");

  await clickAndWait(page, page.getByRole("button", { name: "Cancelar" }), "Cancelar modal");
  report["Agregar Negocio modal"] = "PASS";
}

async function openAdministrarNegocios(page) {
  const administrarNegocios = page.getByText("Administrar Negocios", { exact: true });
  try {
    await expectVisible(administrarNegocios, "Administrar Negocios option", 6000);
  } catch (_) {
    await clickAndWait(page, page.getByText("Mi Negocio", { exact: true }), "Re-expand Mi Negocio");
    await expectVisible(administrarNegocios, "Administrar Negocios option");
  }

  await clickAndWait(page, administrarNegocios, "Administrar Negocios");

  await expectVisible(page.getByText("Información General", { exact: true }), "Información General");
  await expectVisible(page.getByText("Detalles de la Cuenta", { exact: true }), "Detalles de la Cuenta");
  await expectVisible(page.getByText("Tus Negocios", { exact: true }), "Tus Negocios");
  await expectVisible(page.getByText("Sección Legal", { exact: true }), "Sección Legal");

  await checkpoint(page, "04-administrar-negocios-view.png", true);
  report["Administrar Negocios view"] = "PASS";
}

async function validateInformacionGeneral(page) {
  await expectVisible(page.getByText(/business plan/i), "BUSINESS PLAN text");
  await expectVisible(page.getByRole("button", { name: "Cambiar Plan" }), "Cambiar Plan button");

  const userEmail = page.getByText(/@/, { exact: false }).first();
  await expectVisible(userEmail, "User email");

  const profileName = page.locator("h1, h2, h3").filter({ hasNotText: /información|detalles|negocios|legal/i }).first();
  await expectVisible(profileName, "User name");

  report["Información General"] = "PASS";
}

async function validateDetallesCuenta(page) {
  await expectVisible(page.getByText("Cuenta creada", { exact: false }), "Cuenta creada");
  await expectVisible(page.getByText("Estado activo", { exact: false }), "Estado activo");
  await expectVisible(page.getByText("Idioma seleccionado", { exact: false }), "Idioma seleccionado");
  report["Detalles de la Cuenta"] = "PASS";
}

async function validateTusNegocios(page) {
  await expectVisible(page.getByText("Tus Negocios", { exact: true }), "Tus Negocios section");
  await expectVisible(page.getByText("Agregar Negocio", { exact: true }), "Agregar Negocio button");
  await expectVisible(
    page.getByText("Tienes 2 de 3 negocios", { exact: true }),
    "Tienes 2 de 3 negocios text"
  );
  report["Tus Negocios"] = "PASS";
}

async function validateLegalLink(page, linkText, expectedHeading, screenshotName) {
  const baseUrl = page.url();
  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);

  await clickAndWait(page, page.getByText(linkText, { exact: true }), linkText);
  const popup = await popupPromise;
  const legalPage = popup || page;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 30000 });
  await waitForUi(legalPage, 30000);

  await expectVisible(
    legalPage.getByRole("heading", { name: expectedHeading }),
    `${expectedHeading} heading`,
    30000
  );

  // Validate there is legal content beyond the heading itself.
  await expectVisible(
    legalPage.locator("main, article, section, body").locator("p, li").first(),
    `${expectedHeading} legal content`
  );

  legalUrls[expectedHeading] = legalPage.url();
  await checkpoint(legalPage, screenshotName, true);

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (legalPage.url() !== baseUrl) {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(legalPage);
  }
}

async function writeReport() {
  const output = {
    runId: RUN_ID,
    evidenceDir: EVIDENCE_DIR,
    finalReport: report,
    legalUrls,
  };
  const outputPath = path.join(EVIDENCE_DIR, "final-report.json");
  await fs.writeFile(outputPath, JSON.stringify(output, null, 2), "utf8");
  console.log("Final report:");
  console.log(JSON.stringify(output, null, 2));
}

async function main() {
  await ensureEvidenceDir();

  const headless = process.env.HEADLESS !== "false";
  const browser = await chromium.launch({ headless });
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    await openFromCurrentOrEnv(page);
    await loginWithGoogle(page);
    await openMiNegocioMenu(page);
    await validateAgregarNegocioModal(page);
    await openAdministrarNegocios(page);
    await validateInformacionGeneral(page);
    await validateDetallesCuenta(page);
    await validateTusNegocios(page);

    await validateLegalLink(
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05-terminos-y-condiciones.png"
    );
    report["Términos y Condiciones"] = "PASS";

    await validateLegalLink(
      page,
      "Política de Privacidad",
      "Política de Privacidad",
      "06-politica-de-privacidad.png"
    );
    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    console.error("Workflow execution failed:", error);
    await checkpoint(page, "99-failure-state.png", true).catch(() => {});
    process.exitCode = 1;
  } finally {
    await writeReport();
    await context.close();
    await browser.close();
  }
}

main();
