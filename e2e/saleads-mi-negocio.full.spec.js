const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_KEYS = [
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

function buildInitialReport() {
  const report = {};
  for (const key of REPORT_KEYS) {
    report[key] = {
      status: "FAIL",
      details: ""
    };
  }
  return report;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function clickVisibleText(page, regex, options = {}) {
  const candidates = [
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByRole("menuitem", { name: regex }).first(),
    page.getByRole("tab", { name: regex }).first(),
    page.getByText(regex).first()
  ];

  for (const locator of candidates) {
    if (await locator.isVisible().catch(() => false)) {
      await locator.click(options);
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`No visible element found for text pattern: ${regex}`);
}

async function expectVisibleByText(page, regex) {
  const candidates = [
    page.getByRole("heading", { name: regex }).first(),
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByText(regex).first()
  ];

  for (const locator of candidates) {
    if (await locator.isVisible().catch(() => false)) {
      await expect(locator).toBeVisible();
      return;
    }
  }

  throw new Error(`Expected visible text not found: ${regex}`);
}

async function loginWithGoogle(page) {
  const loginRegex = /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i;
  const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await clickVisibleText(page, loginRegex);

  let googlePage = await popupPromise;

  if (googlePage) {
    await googlePage.waitForLoadState("domcontentloaded");
  } else {
    googlePage = page;
  }

  const accountLocator = googlePage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")).first();
  if (await accountLocator.isVisible().catch(() => false)) {
    await accountLocator.click();
    await googlePage.waitForLoadState("domcontentloaded");
  }

  if (googlePage !== page) {
    await googlePage.waitForEvent("close", { timeout: 15000 }).catch(async () => {
      await googlePage.close().catch(() => {});
    });
    await page.bringToFront();
  }
}

async function validateLegalLink({
  page,
  linkTextRegex,
  headingRegex,
  screenshotPath,
  reportEntry
}) {
  const appUrlBeforeClick = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickVisibleText(page, linkTextRegex);

  let legalPage = await popupPromise;
  if (legalPage) {
    await legalPage.waitForLoadState("domcontentloaded");
  } else {
    legalPage = page;
  }

  const heading = legalPage.getByRole("heading", { name: headingRegex }).first();
  if (await heading.isVisible().catch(() => false)) {
    await expect(heading).toBeVisible();
  } else {
    await expect(legalPage.getByText(headingRegex).first()).toBeVisible();
  }

  const legalText = (await legalPage.locator("body").innerText()).trim();
  expect(legalText.length).toBeGreaterThan(150);

  await legalPage.screenshot({ path: screenshotPath, fullPage: true });
  reportEntry.details = `Validated at URL: ${legalPage.url()}`;

  if (legalPage !== page) {
    await legalPage.close();
    await page.bringToFront();
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goBack().catch(async () => {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  test.setTimeout(240000);

  const artifactsDir = path.resolve(__dirname, "artifacts");
  await fs.mkdir(artifactsDir, { recursive: true });

  const report = buildInitialReport();
  const errors = [];

  const targetLoginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
  if (targetLoginUrl) {
    await page.goto(targetLoginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else {
    throw new Error(
      "Set SALEADS_LOGIN_URL (or SALEADS_URL) to the environment login page. The test intentionally avoids hardcoded domains."
    );
  }

  try {
    await loginWithGoogle(page);
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expectVisibleByText(page, /Negocio/i);
    await page.screenshot({ path: path.join(artifactsDir, "01-dashboard-loaded.png"), fullPage: true });
    report["Login"] = { status: "PASS", details: "Dashboard and sidebar are visible." };
  } catch (error) {
    errors.push(`Login: ${error.message}`);
    report["Login"] = { status: "FAIL", details: error.message };
  }

  try {
    await clickVisibleText(page, /^Negocio$/i);
    await clickVisibleText(page, /^Mi Negocio$/i);
    await expectVisibleByText(page, /Agregar Negocio/i);
    await expectVisibleByText(page, /Administrar Negocios/i);
    await page.screenshot({ path: path.join(artifactsDir, "02-mi-negocio-menu-expanded.png"), fullPage: true });
    report["Mi Negocio menu"] = { status: "PASS", details: "Submenu expanded and options visible." };
  } catch (error) {
    errors.push(`Mi Negocio menu: ${error.message}`);
    report["Mi Negocio menu"] = { status: "FAIL", details: error.message };
  }

  try {
    await clickVisibleText(page, /Agregar Negocio/i);
    await expectVisibleByText(page, /Crear Nuevo Negocio/i);
    const nameInput = page
      .getByLabel(/Nombre del Negocio/i)
      .or(page.getByPlaceholder(/Nombre del Negocio/i))
      .first();
    await expect(nameInput).toBeVisible();
    await expectVisibleByText(page, /Tienes\s*2\s*de\s*3\s*negocios/i);
    await expectVisibleByText(page, /Cancelar/i);
    await expectVisibleByText(page, /Crear Negocio/i);
    await page.screenshot({ path: path.join(artifactsDir, "03-agregar-negocio-modal.png"), fullPage: true });

    await nameInput.fill("Negocio Prueba Automatización");
    await clickVisibleText(page, /Cancelar/i);
    report["Agregar Negocio modal"] = {
      status: "PASS",
      details: "Modal validated, sample name entered, and modal closed."
    };
  } catch (error) {
    errors.push(`Agregar Negocio modal: ${error.message}`);
    report["Agregar Negocio modal"] = { status: "FAIL", details: error.message };
  }

  try {
    await clickVisibleText(page, /^Mi Negocio$/i).catch(async () => {
      await clickVisibleText(page, /^Negocio$/i);
      await clickVisibleText(page, /^Mi Negocio$/i);
    });
    await clickVisibleText(page, /Administrar Negocios/i);
    await expectVisibleByText(page, /Informaci[oó]n General/i);
    await expectVisibleByText(page, /Detalles de la Cuenta/i);
    await expectVisibleByText(page, /Tus Negocios/i);
    await expectVisibleByText(page, /Secci[oó]n Legal/i);
    await page.screenshot({ path: path.join(artifactsDir, "04-administrar-negocios-view.png"), fullPage: true });
    report["Administrar Negocios view"] = {
      status: "PASS",
      details: "Account page with all expected sections is visible."
    };
  } catch (error) {
    errors.push(`Administrar Negocios view: ${error.message}`);
    report["Administrar Negocios view"] = { status: "FAIL", details: error.message };
  }

  try {
    await expectVisibleByText(page, /Informaci[oó]n General/i);
    await expectVisibleByText(page, /(nombre|usuario)/i);
    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
    await expectVisibleByText(page, /BUSINESS PLAN/i);
    await expectVisibleByText(page, /Cambiar Plan/i);
    report["Información General"] = {
      status: "PASS",
      details: "Name/email, plan, and action button validated."
    };
  } catch (error) {
    errors.push(`Información General: ${error.message}`);
    report["Información General"] = { status: "FAIL", details: error.message };
  }

  try {
    await expectVisibleByText(page, /Cuenta creada/i);
    await expectVisibleByText(page, /Estado activo/i);
    await expectVisibleByText(page, /Idioma seleccionado/i);
    report["Detalles de la Cuenta"] = {
      status: "PASS",
      details: "All account detail labels are visible."
    };
  } catch (error) {
    errors.push(`Detalles de la Cuenta: ${error.message}`);
    report["Detalles de la Cuenta"] = { status: "FAIL", details: error.message };
  }

  try {
    await expectVisibleByText(page, /Tus Negocios/i);
    await expectVisibleByText(page, /Agregar Negocio/i);
    await expectVisibleByText(page, /Tienes\s*2\s*de\s*3\s*negocios/i);
    report["Tus Negocios"] = {
      status: "PASS",
      details: "Business list, add button, and quota text validated."
    };
  } catch (error) {
    errors.push(`Tus Negocios: ${error.message}`);
    report["Tus Negocios"] = { status: "FAIL", details: error.message };
  }

  try {
    await validateLegalLink({
      page,
      linkTextRegex: /T[eé]rminos y Condiciones/i,
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotPath: path.join(artifactsDir, "05-terminos-y-condiciones.png"),
      reportEntry: report["Términos y Condiciones"]
    });
    report["Términos y Condiciones"].status = "PASS";
  } catch (error) {
    errors.push(`Términos y Condiciones: ${error.message}`);
    report["Términos y Condiciones"] = { status: "FAIL", details: error.message };
  }

  try {
    await validateLegalLink({
      page,
      linkTextRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotPath: path.join(artifactsDir, "06-politica-de-privacidad.png"),
      reportEntry: report["Política de Privacidad"]
    });
    report["Política de Privacidad"].status = "PASS";
  } catch (error) {
    errors.push(`Política de Privacidad: ${error.message}`);
    report["Política de Privacidad"] = { status: "FAIL", details: error.message };
  }

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAtUtc: new Date().toISOString(),
    results: report,
    overallStatus: errors.length === 0 ? "PASS" : "FAIL",
    errors
  };

  await fs.writeFile(path.join(artifactsDir, "saleads-mi-negocio-final-report.json"), JSON.stringify(finalReport, null, 2));

  expect(errors, `Validation failures:\n${errors.join("\n")}`).toEqual([]);
});
