const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const screenshotsDir = path.join("test-results", "saleads-mi-negocio-screenshots");
const reportDir = path.join("test-results");
const reportFile = path.join(reportDir, "saleads-mi-negocio-report.json");

const validations = {
  Login: { status: "FAIL", details: [] },
  "Mi Negocio menu": { status: "FAIL", details: [] },
  "Agregar Negocio modal": { status: "FAIL", details: [] },
  "Administrar Negocios view": { status: "FAIL", details: [] },
  "Información General": { status: "FAIL", details: [] },
  "Detalles de la Cuenta": { status: "FAIL", details: [] },
  "Tus Negocios": { status: "FAIL", details: [] },
  "Términos y Condiciones": { status: "FAIL", details: [] },
  "Política de Privacidad": { status: "FAIL", details: [] },
};

const legalUrls = {
  "Términos y Condiciones": null,
  "Política de Privacidad": null,
};

function ensureArtifactFolders() {
  fs.mkdirSync(screenshotsDir, { recursive: true });
  fs.mkdirSync(reportDir, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForTimeout(900);
  await page.waitForLoadState("domcontentloaded");
}

async function checkpoint(page, name, fullPage = false) {
  const safe = name.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const file = path.join(screenshotsDir, `${safe}.png`);
  await page.screenshot({ path: file, fullPage });
  return file;
}

async function clickByVisibleText(page, text, role = "button") {
  const byRole = page.getByRole(role, { name: new RegExp(`^${text}$`, "i") });
  if ((await byRole.count()) > 0 && (await byRole.first().isVisible())) {
    await byRole.first().click();
    await waitForUi(page);
    return;
  }

  const byText = page.getByText(new RegExp(`^${text}$`, "i"));
  await expect(byText.first()).toBeVisible();
  await byText.first().click();
  await waitForUi(page);
}

async function clickSidebarOption(page, text) {
  const target = page
    .locator("aside, nav")
    .locator("button, a, div, span")
    .filter({ hasText: new RegExp(`^${text}$`, "i") })
    .first();
  await expect(target).toBeVisible();
  await target.click();
  await waitForUi(page);
}

async function selectGoogleAccountIfShown(page, email) {
  const emailMatch = page.getByText(email, { exact: true });
  if ((await emailMatch.count()) > 0) {
    await emailMatch.first().click();
    await waitForUi(page);
    return true;
  }
  return false;
}

async function clickLoginAndHandleGoogle(page, context) {
  const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  const googleLoginButton = page.getByText(/sign in with google|continuar con google|google/i).first();
  await expect(googleLoginButton).toBeVisible({ timeout: 45_000 });
  await googleLoginButton.click();
  await waitForUi(page);

  const popupPage = await popupPromise;
  if (!popupPage) {
    await selectGoogleAccountIfShown(page, "juanlucasbarbiergarzon@gmail.com");
    return;
  }

  await popupPage.waitForLoadState("domcontentloaded");
  await selectGoogleAccountIfShown(popupPage, "juanlucasbarbiergarzon@gmail.com");
  await popupPage.waitForTimeout(1200);
  if (!popupPage.isClosed()) {
    await popupPage.close().catch(() => {});
  }
}

function pass(step, message) {
  validations[step].status = "PASS";
  validations[step].details.push(message);
}

function addDetail(step, message) {
  validations[step].details.push(message);
}

async function expectUserNameNearGeneralInfo(page) {
  const infoSection = page.locator("section, div").filter({ hasText: "Información General" }).first();
  await expect(infoSection).toBeVisible();
  const plausibleName = infoSection.locator(":scope p, :scope span, :scope h1, :scope h2, :scope h3");
  await expect(plausibleName.first()).toBeVisible();
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  ensureArtifactFolders();

  const startUrl = process.env.SALEADS_START_URL || process.env.BASE_URL;
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  }

  // Step 1: Login with Google
  try {
    await clickLoginAndHandleGoogle(page, context);

    const appShell = page.locator("aside, nav").first();
    await expect(appShell).toBeVisible({ timeout: 90_000 });
    pass("Login", "Main interface loaded and left sidebar is visible.");
    await checkpoint(page, "01-dashboard-loaded");
  } catch (error) {
    addDetail("Login", `Login step failed: ${error.message}`);
    throw error;
  }

  // Step 2: Open Mi Negocio menu
  await clickSidebarOption(page, "Negocio");
  await clickSidebarOption(page, "Mi Negocio");

  await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
  await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();
  pass("Mi Negocio menu", "Submenu expanded with expected options.");
  await checkpoint(page, "02-mi-negocio-menu-expanded");

  // Step 3: Validate Agregar Negocio modal
  await clickByVisibleText(page, "Agregar Negocio");
  await expect(page.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
  await expect(page.getByPlaceholder(/nombre del negocio/i)).toBeVisible();
  await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
  await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

  const nombreInput = page.getByPlaceholder(/nombre del negocio/i).first();
  await nombreInput.click();
  await nombreInput.fill("Negocio Prueba Automatización");
  await checkpoint(page, "03-agregar-negocio-modal");
  await clickByVisibleText(page, "Cancelar");
  pass("Agregar Negocio modal", "Modal content and controls validated.");

  // Step 4: Open Administrar Negocios
  if ((await page.getByText("Administrar Negocios", { exact: true }).count()) === 0) {
    await clickSidebarOption(page, "Mi Negocio");
  }
  await clickByVisibleText(page, "Administrar Negocios");

  await expect(page.getByText("Información General", { exact: true })).toBeVisible();
  await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
  await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
  await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible();
  pass("Administrar Negocios view", "Account page sections are visible.");
  await checkpoint(page, "04-administrar-negocios", true);

  // Step 5: Validate Información General
  const infoSection = page.locator("section, div").filter({ hasText: "Información General" }).first();
  await expectUserNameNearGeneralInfo(page);
  await expect(infoSection.getByText(/@/)).toBeVisible();
  await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
  await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
  pass("Información General", "Name/email/plan/button validated.");

  // Step 6: Validate Detalles de la Cuenta
  const detallesSection = page.locator("section, div").filter({ hasText: "Detalles de la Cuenta" }).first();
  await expect(detallesSection.getByText(/Cuenta creada/i)).toBeVisible();
  await expect(detallesSection.getByText(/Estado activo/i)).toBeVisible();
  await expect(detallesSection.getByText(/Idioma seleccionado/i)).toBeVisible();
  pass("Detalles de la Cuenta", "Account details labels validated.");

  // Step 7: Validate Tus Negocios
  const negociosSection = page.locator("section, div").filter({ hasText: "Tus Negocios" }).first();
  await expect(negociosSection).toBeVisible();
  await expect(negociosSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
  await expect(negociosSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  pass("Tus Negocios", "Business list, button, and quota text validated.");

  // Step 8: Validate Términos y Condiciones
  const termsPromise = context.waitForEvent("page").catch(() => null);
  await clickByVisibleText(page, "Términos y Condiciones", "link");
  let termsPage = await termsPromise;
  if (termsPage) {
    await termsPage.waitForLoadState("domcontentloaded");
  } else {
    termsPage = page;
  }
  await expect(termsPage.getByText(/Términos y Condiciones/i)).toBeVisible();
  await expect(termsPage.locator("body")).toContainText(/términos|condiciones|legal/i);
  legalUrls["Términos y Condiciones"] = termsPage.url();
  await checkpoint(termsPage, "08-terminos-y-condiciones", true);
  pass("Términos y Condiciones", `Legal page validated. URL: ${termsPage.url()}`);
  if (termsPage !== page) {
    await termsPage.close();
    await page.bringToFront();
    await waitForUi(page);
  }

  // Step 9: Validate Política de Privacidad
  const privacyPromise = context.waitForEvent("page").catch(() => null);
  await clickByVisibleText(page, "Política de Privacidad", "link");
  let privacyPage = await privacyPromise;
  if (privacyPage) {
    await privacyPage.waitForLoadState("domcontentloaded");
  } else {
    privacyPage = page;
  }
  await expect(privacyPage.getByText(/Política de Privacidad/i)).toBeVisible();
  await expect(privacyPage.locator("body")).toContainText(/privacidad|datos|legal/i);
  legalUrls["Política de Privacidad"] = privacyPage.url();
  await checkpoint(privacyPage, "09-politica-de-privacidad", true);
  pass("Política de Privacidad", `Legal page validated. URL: ${privacyPage.url()}`);
  if (privacyPage !== page) {
    await privacyPage.close();
    await page.bringToFront();
    await waitForUi(page);
  }
});

test.afterAll(() => {
  ensureArtifactFolders();
  const report = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    validations,
    legalUrls,
  };
  fs.writeFileSync(reportFile, JSON.stringify(report, null, 2), "utf-8");
});
