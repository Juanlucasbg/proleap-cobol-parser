const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const TEST_NAME = "saleads_mi_negocio_full_test";

test(TEST_NAME, async ({ page, context }) => {
  test.setTimeout(240000);

  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactDir = path.join(
    process.cwd(),
    "automation",
    "playwright",
    "artifacts",
    `${TEST_NAME}_${timestamp}`
  );
  fs.mkdirSync(artifactDir, { recursive: true });

  const report = {
    Login: { status: "FAIL", details: "" },
    "Mi Negocio menu": { status: "FAIL", details: "" },
    "Agregar Negocio modal": { status: "FAIL", details: "" },
    "Administrar Negocios view": { status: "FAIL", details: "" },
    "Informacion General": { status: "FAIL", details: "" },
    "Detalles de la Cuenta": { status: "FAIL", details: "" },
    "Tus Negocios": { status: "FAIL", details: "" },
    "Terminos y Condiciones": { status: "FAIL", details: "", finalUrl: "" },
    "Politica de Privacidad": { status: "FAIL", details: "", finalUrl: "" },
  };

  async function waitForUiToSettle(targetPage = page) {
    await targetPage.waitForTimeout(400);
    await targetPage
      .waitForLoadState("networkidle", { timeout: 12000 })
      .catch(async () => {
        await targetPage.waitForLoadState("domcontentloaded", { timeout: 6000 }).catch(() => null);
      });
    await targetPage.waitForTimeout(400);
  }

  async function clickAndWait(locator, targetPage = page) {
    await expect(locator.first()).toBeVisible({ timeout: 20000 });
    await locator.first().click();
    await waitForUiToSettle(targetPage);
  }

  async function screenshot(name, targetPage = page, fullPage = false) {
    await targetPage.screenshot({
      path: path.join(artifactDir, name),
      fullPage,
    });
  }

  async function isAnyVisible(regex) {
    const byRoleButton = page.getByRole("button", { name: regex }).first();
    const byRoleLink = page.getByRole("link", { name: regex }).first();
    const byText = page.getByText(regex).first();
    if (await byRoleButton.isVisible().catch(() => false)) {
      return byRoleButton;
    }
    if (await byRoleLink.isVisible().catch(() => false)) {
      return byRoleLink;
    }
    if (await byText.isVisible().catch(() => false)) {
      return byText;
    }
    return null;
  }

  async function clickByVisibleText(regex) {
    const locator = await isAnyVisible(regex);
    if (!locator) {
      throw new Error(`No visible element found for text: ${regex}`);
    }
    await clickAndWait(locator, page);
  }

  function mark(field, status, details, finalUrl = "") {
    if (!report[field]) {
      report[field] = {};
    }
    report[field].status = status;
    report[field].details = details;
    if (finalUrl) {
      report[field].finalUrl = finalUrl;
    }
  }

  const providedLoginUrl = process.env.SALEADS_LOGIN_URL;
  if (providedLoginUrl && page.url() === "about:blank") {
    await page.goto(providedLoginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  }

  // Step 1: Login with Google
  try {
    const loginTriggerRegex = /sign in with google|continuar con google|iniciar sesion con google|google/i;
    const loginLocator = await isAnyVisible(loginTriggerRegex);
    if (!loginLocator) {
      throw new Error("Google login button was not found.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await clickAndWait(loginLocator, page);
    const popup = await popupPromise;

    const accountRegex = /^juanlucasbarbiergarzon@gmail\.com$/i;
    if (popup) {
      await waitForUiToSettle(popup);
      const popupAccount = popup.getByText(accountRegex).first();
      if (await popupAccount.isVisible().catch(() => false)) {
        await clickAndWait(popupAccount, popup);
      }
      await popup.waitForTimeout(1000);
    } else {
      const accountInPage = page.getByText(accountRegex).first();
      if (await accountInPage.isVisible().catch(() => false)) {
        await clickAndWait(accountInPage, page);
      }
    }

    await expect(page.getByRole("navigation").first()).toBeVisible({ timeout: 60000 });
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 30000 });
    await screenshot("01_dashboard_loaded.png", page, true);
    mark("Login", "PASS", "Dashboard loaded and left sidebar visible.");
  } catch (error) {
    await screenshot("01_login_failure.png", page, true).catch(() => null);
    mark("Login", "FAIL", String(error.message || error));
  }

  // Step 2: Open Mi Negocio menu
  try {
    await expect(page.getByRole("navigation").first()).toBeVisible({ timeout: 30000 });
    await clickByVisibleText(/mi negocio/i);
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 20000 });
    await screenshot("02_mi_negocio_expanded_menu.png");
    mark("Mi Negocio menu", "PASS", "Mi Negocio submenu expanded with both options visible.");
  } catch (error) {
    await screenshot("02_mi_negocio_menu_failure.png", page, true).catch(() => null);
    mark("Mi Negocio menu", "FAIL", String(error.message || error));
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await clickByVisibleText(/agregar negocio/i);
    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 15000 });

    await screenshot("03_agregar_negocio_modal.png", page);

    const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page.getByRole("button", { name: /cancelar/i }), page);
    await expect(page.getByText(/crear nuevo negocio/i)).not.toBeVisible({ timeout: 10000 });
    mark("Agregar Negocio modal", "PASS", "Modal validated and closed with Cancelar.");
  } catch (error) {
    await screenshot("03_agregar_negocio_modal_failure.png", page, true).catch(() => null);
    mark("Agregar Negocio modal", "FAIL", String(error.message || error));
  }

  async function ensureMiNegocioExpanded() {
    const agregarNegocio = page.getByText(/agregar negocio/i).first();
    const administrarNegocios = page.getByText(/administrar negocios/i).first();
    const expanded =
      (await agregarNegocio.isVisible().catch(() => false)) &&
      (await administrarNegocios.isVisible().catch(() => false));
    if (!expanded) {
      await clickByVisibleText(/mi negocio/i);
      await expect(agregarNegocio).toBeVisible({ timeout: 15000 });
      await expect(administrarNegocios).toBeVisible({ timeout: 15000 });
    }
  }

  // Step 4: Open Administrar Negocios
  try {
    await ensureMiNegocioExpanded();
    await clickByVisibleText(/administrar negocios/i);
    await expect(page.getByText(/informacion general/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/seccion legal/i)).toBeVisible({ timeout: 30000 });
    await screenshot("04_administrar_negocios_view.png", page, true);
    mark("Administrar Negocios view", "PASS", "Account page with all required sections is visible.");
  } catch (error) {
    await screenshot("04_administrar_negocios_failure.png", page, true).catch(() => null);
    mark("Administrar Negocios view", "FAIL", String(error.message || error));
  }

  // Step 5: Validate Informacion General
  try {
    await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/@/).first()).toBeVisible({ timeout: 15000 });
    mark("Informacion General", "PASS", "User identity, plan and Cambiar Plan button are visible.");
  } catch (error) {
    mark("Informacion General", "FAIL", String(error.message || error));
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/estado activo/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible({ timeout: 15000 });
    mark("Detalles de la Cuenta", "PASS", "All account details labels are visible.");
  } catch (error) {
    mark("Detalles de la Cuenta", "FAIL", String(error.message || error));
  }

  // Step 7: Validate Tus Negocios
  try {
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 15000 });
    mark("Tus Negocios", "PASS", "Business list, Agregar Negocio button and quota text are visible.");
  } catch (error) {
    mark("Tus Negocios", "FAIL", String(error.message || error));
  }

  async function validateLegalLink(linkRegex, headingRegex, reportField, screenshotName) {
    const pagesBefore = context.pages().length;
    const newPagePromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

    await clickByVisibleText(linkRegex);
    let targetPage = await newPagePromise;
    if (!targetPage || context.pages().length === pagesBefore) {
      targetPage = page;
    } else {
      await targetPage.bringToFront();
    }

    await waitForUiToSettle(targetPage);
    await expect(targetPage.getByText(headingRegex).first()).toBeVisible({ timeout: 30000 });
    await screenshot(screenshotName, targetPage, true);
    const finalUrl = targetPage.url();

    if (targetPage !== page) {
      await targetPage.close();
      await page.bringToFront();
      await waitForUiToSettle(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUiToSettle(page);
    }

    mark(reportField, "PASS", "Legal page heading and content are visible.", finalUrl);
  }

  // Step 8: Validate Terminos y Condiciones
  try {
    await validateLegalLink(
      /terminos y condiciones|t[eé]rminos y condiciones/i,
      /terminos y condiciones|t[eé]rminos y condiciones/i,
      "Terminos y Condiciones",
      "08_terminos_y_condiciones.png"
    );
  } catch (error) {
    await screenshot("08_terminos_y_condiciones_failure.png", page, true).catch(() => null);
    mark("Terminos y Condiciones", "FAIL", String(error.message || error));
  }

  // Step 9: Validate Politica de Privacidad
  try {
    await validateLegalLink(
      /politica de privacidad|pol[ií]tica de privacidad/i,
      /politica de privacidad|pol[ií]tica de privacidad/i,
      "Politica de Privacidad",
      "09_politica_de_privacidad.png"
    );
  } catch (error) {
    await screenshot("09_politica_de_privacidad_failure.png", page, true).catch(() => null);
    mark("Politica de Privacidad", "FAIL", String(error.message || error));
  }

  const reportPath = path.join(artifactDir, "final_report.json");
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");
  console.log("Final validation report written to:", reportPath);
  console.table(
    Object.entries(report).map(([name, value]) => ({
      step: name,
      status: value.status,
      details: value.details,
      finalUrl: value.finalUrl || "",
    }))
  );
});
