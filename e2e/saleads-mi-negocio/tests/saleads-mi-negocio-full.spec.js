const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL = process.env.SALEADS_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL;

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

const createDefaultReport = () =>
  Object.fromEntries(REPORT_KEYS.map((key) => [key, "FAIL"]));

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(300);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function clickByVisibleText(page, nameRegex) {
  const candidates = [
    page.getByRole("button", { name: nameRegex }).first(),
    page.getByRole("link", { name: nameRegex }).first(),
    page.getByText(nameRegex).first()
  ];

  for (const locator of candidates) {
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      await waitForUiLoad(page);
      return;
    }
  }

  throw new Error(`No visible element found for ${nameRegex}.`);
}

async function maybeSelectGoogleAccount(targetPage) {
  if (!targetPage || !targetPage.url().includes("accounts.google.com")) {
    return;
  }

  const accountCandidate = targetPage
    .locator("div[role='link'], div[role='button'], li, [data-identifier]")
    .filter({ hasText: ACCOUNT_EMAIL })
    .first();

  if (await accountCandidate.isVisible().catch(() => false)) {
    await accountCandidate.click();
  } else {
    // Fallback by visible text in case Google markup changes.
    await targetPage.getByText(new RegExp(ACCOUNT_EMAIL, "i")).first().click();
  }
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const outputPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: outputPath, fullPage });
  await testInfo.attach(name, { path: outputPath, contentType: "image/png" });
}

async function openLegalPageAndValidate({
  appPage,
  linkLabelRegex,
  headingRegex,
  testInfo,
  screenshotName
}) {
  const context = appPage.context();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const navPromise = appPage.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 10000 }).catch(() => null);

  await clickByVisibleText(appPage, linkLabelRegex);

  const popup = await popupPromise;
  await navPromise;

  const legalPage = popup || appPage;
  await waitForUiLoad(legalPage);

  await expect(legalPage.getByText(headingRegex).first()).toBeVisible({ timeout: 15000 });
  await expect(legalPage.locator("body")).toContainText(/\S{20,}/);

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  if (!LOGIN_URL) {
    throw new Error(
      "SALEADS_LOGIN_URL is required. Provide the environment login URL without hardcoding a specific domain."
    );
  }

  const report = createDefaultReport();
  const extraEvidence = {
    "Términos y Condiciones URL": "",
    "Política de Privacidad URL": ""
  };

  const markPass = (key) => {
    report[key] = "PASS";
  };

  try {
    // Step 1: Login with Google.
    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);

    const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickByVisibleText(page, /google|sign in|iniciar sesión/i);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await maybeSelectGoogleAccount(popup);
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await maybeSelectGoogleAccount(page);
      await waitForUiLoad(page);
    }

    const sidebar = page.locator("aside, nav").filter({ hasText: /negocio|mi negocio/i }).first();
    await expect(sidebar).toBeVisible({ timeout: 25000 });
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded");
    markPass("Login");

    // Step 2: Open Mi Negocio menu.
    await clickByVisibleText(page, /mi negocio/i);
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 15000 });
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded");
    markPass("Mi Negocio menu");

    // Step 3: Validate Agregar Negocio modal.
    await clickByVisibleText(page, /agregar negocio/i);
    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByLabel(/nombre del negocio/i).first()).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    await page.getByLabel(/nombre del negocio/i).first().click();
    await page
      .getByLabel(/nombre del negocio/i)
      .first()
      .fill("Negocio Prueba Automatización");
    await captureCheckpoint(page, testInfo, "03-crear-nuevo-negocio-modal");
    await clickByVisibleText(page, /cancelar/i);
    markPass("Agregar Negocio modal");

    // Step 4: Open Administrar Negocios.
    const administrarNegocios = page.getByText(/administrar negocios/i).first();
    if (!(await administrarNegocios.isVisible().catch(() => false))) {
      await clickByVisibleText(page, /mi negocio/i);
    }
    await administrarNegocios.click();
    await waitForUiLoad(page);

    await expect(page.getByText(/información general/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/sección legal/i).first()).toBeVisible({ timeout: 15000 });
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page", true);
    markPass("Administrar Negocios view");

    // Step 5: Validate Información General.
    const infoGeneralSection = page.locator("section, div").filter({ hasText: /información general/i }).first();
    const infoText = await infoGeneralSection.innerText();
    const hasEmail = infoText.includes(ACCOUNT_EMAIL) || /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoText);
    const hasNameLikeText = infoText
      .split("\n")
      .map((line) => line.trim())
      .some(
        (line) =>
          line.length >= 3 &&
          !/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(line) &&
          !/información general|business plan|cambiar plan/i.test(line)
      );

    expect(hasEmail).toBeTruthy();
    expect(hasNameLikeText).toBeTruthy();
    await expect(infoGeneralSection).toContainText(/business plan/i);
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
    markPass("Información General");

    // Step 6: Validate Detalles de la Cuenta.
    const detallesSection = page.locator("section, div").filter({ hasText: /detalles de la cuenta/i }).first();
    await expect(detallesSection).toContainText(/cuenta creada/i);
    await expect(detallesSection).toContainText(/estado activo/i);
    await expect(detallesSection).toContainText(/idioma seleccionado/i);
    markPass("Detalles de la Cuenta");

    // Step 7: Validate Tus Negocios.
    const negociosSection = page.locator("section, div").filter({ hasText: /tus negocios/i }).first();
    await expect(negociosSection).toBeVisible();
    await expect(negociosSection.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(negociosSection).toContainText(/tienes\s*2\s*de\s*3\s*negocios/i);
    markPass("Tus Negocios");

    // Step 8: Validate Términos y Condiciones.
    extraEvidence["Términos y Condiciones URL"] = await openLegalPageAndValidate({
      appPage: page,
      linkLabelRegex: /términos y condiciones/i,
      headingRegex: /términos y condiciones/i,
      testInfo,
      screenshotName: "05-terminos-y-condiciones"
    });
    markPass("Términos y Condiciones");

    // Step 9: Validate Política de Privacidad.
    extraEvidence["Política de Privacidad URL"] = await openLegalPageAndValidate({
      appPage: page,
      linkLabelRegex: /política de privacidad/i,
      headingRegex: /política de privacidad/i,
      testInfo,
      screenshotName: "06-politica-de-privacidad"
    });
    markPass("Política de Privacidad");
  } finally {
    const finalReportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
    const payload = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      report,
      evidence: extraEvidence
    };

    await fs.mkdir(path.dirname(finalReportPath), { recursive: true });
    await fs.writeFile(finalReportPath, JSON.stringify(payload, null, 2), "utf8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: finalReportPath,
      contentType: "application/json"
    });
  }
});
