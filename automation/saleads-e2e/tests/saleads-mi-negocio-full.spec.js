const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

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

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(800);
}

function normalizeFileName(text) {
  return text
    .toLowerCase()
    .replace(/[^\w\s-]/g, "")
    .trim()
    .replace(/\s+/g, "-");
}

async function expectVisibleText(page, text) {
  const locator = page.getByText(text, { exact: false }).first();
  await expect(locator).toBeVisible();
}

async function clickVisibleByText(page, candidates) {
  for (const text of candidates) {
    const roleButton = page.getByRole("button", { name: new RegExp(text, "i") }).first();
    if (await roleButton.isVisible().catch(() => false)) {
      await roleButton.click();
      await waitForUi(page);
      return text;
    }

    const roleLink = page.getByRole("link", { name: new RegExp(text, "i") }).first();
    if (await roleLink.isVisible().catch(() => false)) {
      await roleLink.click();
      await waitForUi(page);
      return text;
    }

    const generic = page.getByText(new RegExp(text, "i")).first();
    if (await generic.isVisible().catch(() => false)) {
      await generic.click();
      await waitForUi(page);
      return text;
    }
  }

  throw new Error(`No visible element found for texts: ${candidates.join(", ")}`);
}

async function takeCheckpoint(page, testInfo, name, fullPage = false) {
  const screenshotsDir = testInfo.outputPath("screenshots");
  await fs.mkdir(screenshotsDir, { recursive: true });
  const filePath = path.join(screenshotsDir, `${normalizeFileName(name)}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, {
    path: filePath,
    contentType: "image/png"
  });
}

async function openLegalLinkAndValidate({
  page,
  testInfo,
  linkText,
  headingText,
  reportField,
  report
}) {
  const context = page.context();
  const link = page.getByRole("link", { name: new RegExp(linkText, "i") }).first();
  await expect(link).toBeVisible();

  const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
  await link.click();

  const popup = await popupPromise;
  const legalPage = popup || page;
  await waitForUi(legalPage);

  const heading = legalPage.getByRole("heading", { name: new RegExp(headingText, "i") }).first();
  await expect(heading).toBeVisible();

  const legalTextBlock = legalPage.locator("main, article, section, body").first();
  await expect(legalTextBlock).toContainText(/\S+/);

  await takeCheckpoint(legalPage, testInfo, `${reportField} page`, true);
  const legalUrl = legalPage.url();
  await testInfo.attach(`${reportField} URL`, {
    body: legalUrl,
    contentType: "text/plain"
  });

  report[reportField] = "PASS";

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));

  const configuredUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Browser is on about:blank. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL, or preload the login page in your runner."
    );
  }

  // Step 1: Login with Google
  await clickVisibleByText(page, [
    "Sign in with Google",
    "Iniciar sesión con Google",
    "Continuar con Google",
    "Google"
  ]);

  const googleAccountOption = page
    .getByText(/juanlucasbarbiergarzon@gmail\.com/i)
    .first();
  if (await googleAccountOption.isVisible().catch(() => false)) {
    await googleAccountOption.click();
    await waitForUi(page);
  }

  await expect(page.locator("aside").first()).toBeVisible();
  report["Login"] = "PASS";
  await takeCheckpoint(page, testInfo, "Dashboard loaded");

  // Step 2: Open Mi Negocio menu
  await clickVisibleByText(page, ["Negocio", "Mi Negocio"]);
  await expectVisibleText(page, "Agregar Negocio");
  await expectVisibleText(page, "Administrar Negocios");
  report["Mi Negocio menu"] = "PASS";
  await takeCheckpoint(page, testInfo, "Mi Negocio expanded menu");

  // Step 3: Validate Agregar Negocio modal
  await clickVisibleByText(page, ["Agregar Negocio"]);
  await expectVisibleText(page, "Crear Nuevo Negocio");
  await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
  await expectVisibleText(page, "Tienes 2 de 3 negocios");
  await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();
  await takeCheckpoint(page, testInfo, "Agregar Negocio modal");

  const businessNameField = page.getByLabel(/Nombre del Negocio/i).first();
  await businessNameField.click();
  await businessNameField.fill("Negocio Prueba Automatización");
  await clickVisibleByText(page, ["Cancelar"]);
  report["Agregar Negocio modal"] = "PASS";

  // Step 4: Open Administrar Negocios
  await clickVisibleByText(page, ["Mi Negocio"]);
  await clickVisibleByText(page, ["Administrar Negocios"]);
  await expectVisibleText(page, "Información General");
  await expectVisibleText(page, "Detalles de la Cuenta");
  await expectVisibleText(page, "Tus Negocios");
  await expectVisibleText(page, "Sección Legal");
  report["Administrar Negocios view"] = "PASS";
  await takeCheckpoint(page, testInfo, "Administrar Negocios account page", true);

  // Step 5: Validate Información General
  await expect(page.getByText(/@/).first()).toBeVisible();
  await expectVisibleText(page, "BUSINESS PLAN");
  await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  report["Información General"] = "PASS";

  // Step 6: Validate Detalles de la Cuenta
  await expectVisibleText(page, "Cuenta creada");
  await expectVisibleText(page, "Estado activo");
  await expectVisibleText(page, "Idioma seleccionado");
  report["Detalles de la Cuenta"] = "PASS";

  // Step 7: Validate Tus Negocios
  await expectVisibleText(page, "Tus Negocios");
  await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
  await expectVisibleText(page, "Tienes 2 de 3 negocios");
  report["Tus Negocios"] = "PASS";

  // Step 8: Validate Términos y Condiciones
  await openLegalLinkAndValidate({
    page,
    testInfo,
    linkText: "Términos y Condiciones",
    headingText: "Términos y Condiciones",
    reportField: "Términos y Condiciones",
    report
  });

  // Step 9: Validate Política de Privacidad
  await openLegalLinkAndValidate({
    page,
    testInfo,
    linkText: "Política de Privacidad",
    headingText: "Política de Privacidad",
    reportField: "Política de Privacidad",
    report
  });

  // Step 10: Final report
  const reportJson = JSON.stringify(report, null, 2);
  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, reportJson, "utf-8");
  await testInfo.attach("Final PASS/FAIL report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log("Final SaleADS report:");
  console.log(reportJson);

  for (const [field, status] of Object.entries(report)) {
    expect(status, `Expected PASS for: ${field}`).toBe("PASS");
  }
});
