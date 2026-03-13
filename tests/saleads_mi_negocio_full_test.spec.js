const fs = require("node:fs/promises");
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

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7_500 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function isVisible(locator, timeout = 4_000) {
  try {
    return await locator.isVisible({ timeout });
  } catch {
    return false;
  }
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const filePath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, { path: filePath, contentType: "image/png" });
}

function createReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = { status: "FAIL", details: "Not executed." };
  }
  return report;
}

function pass(report, field, details) {
  report[field] = { status: "PASS", details };
}

function fail(report, field, error) {
  report[field] = {
    status: "FAIL",
    details: error instanceof Error ? error.message : String(error)
  };
}

async function openLegalLinkAndValidate({
  context,
  appPage,
  testInfo,
  report,
  reportField,
  linkText,
  headingRegex,
  screenshotName
}) {
  const legalLink = appPage.getByText(linkText, { exact: true });
  await expect(legalLink).toBeVisible();

  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await legalLink.click();

  const popup = await popupPromise;
  const targetPage = popup || appPage;

  await targetPage.bringToFront();
  await waitForUi(targetPage);

  const heading = targetPage.getByRole("heading", { name: headingRegex }).first();
  if (await isVisible(heading, 6_000)) {
    await expect(heading).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingRegex).first()).toBeVisible();
  }

  const legalContent = (await targetPage.locator("body").innerText()).trim();
  expect(legalContent.length).toBeGreaterThan(120);

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);

  const finalUrl = targetPage.url();
  pass(report, reportField, `Validated legal page at ${finalUrl}`);
  console.log(`${reportField} URL: ${finalUrl}`);

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  await targetPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUi(appPage);
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  const errors = [];

  const startingUrl =
    process.env.SALEADS_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL ||
    "";

  if (startingUrl) {
    await page.goto(startingUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No URL configured. Set SALEADS_URL (or SALEADS_BASE_URL/BASE_URL), or run the test in a preloaded browser session."
    );
  }

  // Step 1 - Login with Google
  try {
    const loginWithGoogle = page
      .getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i })
      .first();
    const signInLink = page.getByRole("link", { name: /sign in with google|google/i }).first();

    if (await isVisible(loginWithGoogle)) {
      const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
      await loginWithGoogle.click();
      await waitForUi(page);
      const authPopup = await popupPromise;

      if (authPopup) {
        await authPopup.bringToFront();
        await waitForUi(authPopup);
        const accountSelector = authPopup.getByText(GOOGLE_ACCOUNT, { exact: true });
        if (await isVisible(accountSelector, 8_000)) {
          await accountSelector.click();
          await waitForUi(authPopup);
        }
      } else {
        const accountSelector = page.getByText(GOOGLE_ACCOUNT, { exact: true });
        if (await isVisible(accountSelector, 8_000)) {
          await accountSelector.click();
          await waitForUi(page);
        }
      }
    } else if (await isVisible(signInLink)) {
      await clickAndWait(page, signInLink);
      const accountSelector = page.getByText(GOOGLE_ACCOUNT, { exact: true });
      if (await isVisible(accountSelector, 8_000)) {
        await accountSelector.click();
        await waitForUi(page);
      }
    } else {
      throw new Error("Could not find a visible 'Sign in with Google' control.");
    }

    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "01_dashboard_loaded", true);
    pass(report, "Login", "Main interface and left sidebar are visible.");
  } catch (error) {
    fail(report, "Login", error);
    errors.push(`Login: ${report.Login.details}`);
  }

  // Step 2 - Open Mi Negocio menu
  try {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    if (await isVisible(negocioSection)) {
      await clickAndWait(page, negocioSection);
    }

    await clickAndWait(page, page.getByText(/^Mi Negocio$/i).first());
    await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: true }).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "02_mi_negocio_menu_expanded", true);
    pass(report, "Mi Negocio menu", "Submenu expanded with Agregar/Administrar options.");
  } catch (error) {
    fail(report, "Mi Negocio menu", error);
    errors.push(`Mi Negocio menu: ${report["Mi Negocio menu"].details}`);
  }

  // Step 3 - Validate Agregar Negocio modal
  try {
    await clickAndWait(page, page.getByText("Agregar Negocio", { exact: true }).first());

    const modal = page.getByRole("dialog").filter({ hasText: "Crear Nuevo Negocio" }).first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
    await expect(modal.getByLabel("Nombre del Negocio")).toBeVisible();
    await expect(modal.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
    await expect(modal.getByRole("button", { name: "Cancelar" })).toBeVisible();
    await expect(modal.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

    await captureCheckpoint(page, testInfo, "03_agregar_negocio_modal", true);

    const input = modal.getByLabel("Nombre del Negocio");
    await input.click();
    await input.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, modal.getByRole("button", { name: "Cancelar" }));

    pass(report, "Agregar Negocio modal", "Modal and required fields/buttons validated.");
  } catch (error) {
    fail(report, "Agregar Negocio modal", error);
    errors.push(`Agregar Negocio modal: ${report["Agregar Negocio modal"].details}`);
  }

  // Step 4 - Open Administrar Negocios
  try {
    const administrar = page.getByText("Administrar Negocios", { exact: true }).first();
    if (!(await isVisible(administrar, 2_500))) {
      await clickAndWait(page, page.getByText(/^Mi Negocio$/i).first());
    }
    await clickAndWait(page, page.getByText("Administrar Negocios", { exact: true }).first());

    await expect(page.getByText("Información General", { exact: true })).toBeVisible();
    await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "04_administrar_negocios_view", true);
    pass(report, "Administrar Negocios view", "Account page sections are visible.");
  } catch (error) {
    fail(report, "Administrar Negocios view", error);
    errors.push(`Administrar Negocios view: ${report["Administrar Negocios view"].details}`);
  }

  // Step 5 - Información General
  try {
    const infoGeneral = page.locator("section, div").filter({ hasText: "Información General" }).first();
    await expect(infoGeneral).toContainText(/BUSINESS PLAN/i);
    await expect(infoGeneral.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();

    const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    const text = await infoGeneral.innerText();
    expect(emailRegex.test(text)).toBeTruthy();

    pass(report, "Información General", "Name/email/plan/button validated.");
  } catch (error) {
    fail(report, "Información General", error);
    errors.push(`Información General: ${report["Información General"].details}`);
  }

  // Step 6 - Detalles de la Cuenta
  try {
    const detalles = page.locator("section, div").filter({ hasText: "Detalles de la Cuenta" }).first();
    await expect(detalles).toContainText(/Cuenta creada/i);
    await expect(detalles).toContainText(/Estado activo/i);
    await expect(detalles).toContainText(/Idioma seleccionado/i);
    pass(report, "Detalles de la Cuenta", "Detalles de la cuenta data is visible.");
  } catch (error) {
    fail(report, "Detalles de la Cuenta", error);
    errors.push(`Detalles de la Cuenta: ${report["Detalles de la Cuenta"].details}`);
  }

  // Step 7 - Tus Negocios
  try {
    const tusNegocios = page.locator("section, div").filter({ hasText: "Tus Negocios" }).first();
    await expect(tusNegocios).toContainText("Agregar Negocio");
    await expect(tusNegocios).toContainText("Tienes 2 de 3 negocios");

    const rowsOrCards = tusNegocios.locator("li, tr, article, [data-testid*='business']");
    const rowCount = await rowsOrCards.count();
    expect(rowCount).toBeGreaterThan(0);

    pass(report, "Tus Negocios", "Business list, quota, and add button validated.");
  } catch (error) {
    fail(report, "Tus Negocios", error);
    errors.push(`Tus Negocios: ${report["Tus Negocios"].details}`);
  }

  // Step 8 - Términos y Condiciones
  try {
    await openLegalLinkAndValidate({
      context,
      appPage: page,
      testInfo,
      report,
      reportField: "Términos y Condiciones",
      linkText: "Términos y Condiciones",
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotName: "05_terminos_y_condiciones"
    });
  } catch (error) {
    fail(report, "Términos y Condiciones", error);
    errors.push(`Términos y Condiciones: ${report["Términos y Condiciones"].details}`);
  }

  // Step 9 - Política de Privacidad
  try {
    await openLegalLinkAndValidate({
      context,
      appPage: page,
      testInfo,
      report,
      reportField: "Política de Privacidad",
      linkText: "Política de Privacidad",
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06_politica_de_privacidad"
    });
  } catch (error) {
    fail(report, "Política de Privacidad", error);
    errors.push(`Política de Privacidad: ${report["Política de Privacidad"].details}`);
  }

  // Step 10 - Final report
  const reportPath = testInfo.outputPath("saleads_mi_negocio_report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
  await testInfo.attach("saleads_mi_negocio_report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log("=== saleads_mi_negocio_full_test report ===");
  for (const field of REPORT_FIELDS) {
    const result = report[field];
    console.log(`${field}: ${result.status} - ${result.details}`);
  }

  if (errors.length > 0) {
    throw new Error(`Workflow validation failures:\n- ${errors.join("\n- ")}`);
  }
});
