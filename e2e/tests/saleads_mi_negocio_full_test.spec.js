const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const DEFAULT_TIMEOUT = 15000;

function nowTimestamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function reportStatus(stepResult) {
  return stepResult ? "PASS" : "FAIL";
}

async function waitAfterUiAction(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function clickByText(page, textPattern, options = {}) {
  const exact = options.exact ?? false;
  const timeout = options.timeout ?? DEFAULT_TIMEOUT;
  const target = page.getByText(textPattern, { exact }).first();
  await expect(target).toBeVisible({ timeout });
  await target.click();
  await waitAfterUiAction(page);
}

async function isVisible(locator, timeout = 5000) {
  try {
    await expect(locator).toBeVisible({ timeout });
    return true;
  } catch {
    return false;
  }
}

async function takeCheckpoint(page, screenshotsDir, fileName, options = {}) {
  await page.screenshot({
    path: path.join(screenshotsDir, fileName),
    fullPage: options.fullPage ?? false,
  });
}

async function clickGoogleButton(page, context) {
  const loginButton = page
    .getByRole("button", { name: /google|sign in|iniciar sesi[oó]n/i })
    .first();

  if (!(await isVisible(loginButton, 12000))) {
    throw new Error("No se encontró el botón de login con Google.");
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await loginButton.click();
  await waitAfterUiAction(page);
  const popup = await popupPromise;

  return popup;
}

async function chooseGoogleAccountIfPrompted(activePage) {
  const accountEmail = "juanlucasbarbiergarzon@gmail.com";
  await activePage.waitForLoadState("domcontentloaded").catch(() => {});

  const accountCell = activePage.getByText(accountEmail, { exact: false }).first();
  if (await isVisible(accountCell, 6000)) {
    await accountCell.click();
    await activePage.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  }
}

async function openLegalLinkAndValidate({
  page,
  context,
  linkText,
  headingPattern,
  screenshotsDir,
  screenshotName,
}) {
  let legalPage = page;
  let openedInNewTab = false;

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickByText(page, new RegExp(linkText, "i"));
  const popup = await popupPromise;

  if (popup) {
    legalPage = popup;
    openedInNewTab = true;
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  } else {
    await page.waitForLoadState("domcontentloaded");
    await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  }

  const heading = legalPage.getByRole("heading", { name: headingPattern }).first();
  const fallbackHeading = legalPage.getByText(headingPattern).first();
  const hasHeading = (await isVisible(heading, 10000)) || (await isVisible(fallbackHeading, 4000));

  let hasBodyContent = false;
  const body = legalPage.locator("main, article, section, body").first();
  if (await isVisible(body, 7000)) {
    const bodyText = (await body.innerText()).trim();
    hasBodyContent = bodyText.length > 100;
  }

  await takeCheckpoint(legalPage, screenshotsDir, screenshotName, { fullPage: true });
  const finalUrl = legalPage.url();

  if (openedInNewTab) {
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitAfterUiAction(page);
  }

  return {
    hasHeading,
    hasBodyContent,
    finalUrl,
  };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "Define SALEADS_LOGIN_URL (o SALEADS_BASE_URL) para ejecutar el flujo en cualquier entorno SaleADS."
    );
  }

  const runId = nowTimestamp();
  const artifactsDir = path.join(process.cwd(), "e2e", "artifacts", runId);
  const screenshotsDir = path.join(artifactsDir, "screenshots");
  ensureDir(screenshotsDir);

  const report = {
    Login: false,
    "Mi Negocio menu": false,
    "Agregar Negocio modal": false,
    "Administrar Negocios view": false,
    "Información General": false,
    "Detalles de la Cuenta": false,
    "Tus Negocios": false,
    "Términos y Condiciones": false,
    "Política de Privacidad": false,
  };

  const legalUrls = {
    "Términos y Condiciones": "",
    "Política de Privacidad": "",
  };

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitAfterUiAction(page);

  // Step 1: Login with Google
  const popup = await clickGoogleButton(page, context);
  if (popup) {
    await chooseGoogleAccountIfPrompted(popup);
  } else {
    await chooseGoogleAccountIfPrompted(page);
  }

  const sidebar = page.getByRole("navigation").first();
  const negocioText = page.getByText(/negocio/i).first();
  const appLoaded = (await isVisible(sidebar, 25000)) || (await isVisible(negocioText, 25000));
  report.Login = appLoaded;
  await takeCheckpoint(page, screenshotsDir, "01-dashboard-after-login.png", { fullPage: true });

  // Step 2: Open Mi Negocio menu
  const negocioSection = page.getByText(/^negocio$/i).first();
  if (await isVisible(negocioSection, 7000)) {
    await negocioSection.click();
    await waitAfterUiAction(page);
  }

  const miNegocioTrigger = page.getByText(/mi negocio/i).first();
  if (await isVisible(miNegocioTrigger, 12000)) {
    await miNegocioTrigger.click();
    await waitAfterUiAction(page);
  }

  const agregarNegocioMenu = page.getByText(/agregar negocio/i).first();
  const administrarNegociosMenu = page.getByText(/administrar negocios/i).first();
  report["Mi Negocio menu"] =
    (await isVisible(agregarNegocioMenu, 10000)) &&
    (await isVisible(administrarNegociosMenu, 10000));
  await takeCheckpoint(page, screenshotsDir, "02-mi-negocio-menu-expanded.png");

  // Step 3: Validate Agregar Negocio modal
  if (await isVisible(agregarNegocioMenu, 5000)) {
    await agregarNegocioMenu.click();
    await waitAfterUiAction(page);
  }

  const modalTitle = page.getByText(/crear nuevo negocio/i).first();
  const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
  const planLimitText = page.getByText(/tienes 2 de 3 negocios/i).first();
  const cancelButton = page.getByRole("button", { name: /cancelar/i }).first();
  const createButton = page.getByRole("button", { name: /crear negocio/i }).first();
  const modalValid =
    (await isVisible(modalTitle, 12000)) &&
    (await isVisible(businessNameInput, 7000)) &&
    (await isVisible(planLimitText, 7000)) &&
    (await isVisible(cancelButton, 7000)) &&
    (await isVisible(createButton, 7000));
  report["Agregar Negocio modal"] = modalValid;
  await takeCheckpoint(page, screenshotsDir, "03-agregar-negocio-modal.png");

  if (modalValid) {
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await cancelButton.click();
    await waitAfterUiAction(page);
  }

  // Step 4: Open Administrar Negocios
  if (!(await isVisible(administrarNegociosMenu, 3000))) {
    if (await isVisible(miNegocioTrigger, 3000)) {
      await miNegocioTrigger.click();
      await waitAfterUiAction(page);
    }
  }
  if (await isVisible(administrarNegociosMenu, 5000)) {
    await administrarNegociosMenu.click();
    await waitAfterUiAction(page);
  }

  const infoGeneral = page.getByText(/informaci[oó]n general/i).first();
  const detallesCuenta = page.getByText(/detalles de la cuenta/i).first();
  const tusNegocios = page.getByText(/tus negocios/i).first();
  const seccionLegal = page.getByText(/secci[oó]n legal/i).first();
  report["Administrar Negocios view"] =
    (await isVisible(infoGeneral, 15000)) &&
    (await isVisible(detallesCuenta, 15000)) &&
    (await isVisible(tusNegocios, 15000)) &&
    (await isVisible(seccionLegal, 15000));
  await takeCheckpoint(page, screenshotsDir, "04-administrar-negocios-view.png", { fullPage: true });

  // Step 5: Validate Información General
  const userNameCandidate = page.locator("h1, h2, [data-testid*='name'], .user-name").first();
  const userEmailCandidate = page.getByText(/@/).first();
  const businessPlan = page.getByText(/business plan/i).first();
  const cambiarPlan = page.getByRole("button", { name: /cambiar plan/i }).first();
  report["Información General"] =
    (await isVisible(userNameCandidate, 10000)) &&
    (await isVisible(userEmailCandidate, 10000)) &&
    (await isVisible(businessPlan, 10000)) &&
    (await isVisible(cambiarPlan, 10000));

  // Step 6: Validate Detalles de la Cuenta
  const cuentaCreada = page.getByText(/cuenta creada/i).first();
  const estadoActivo = page.getByText(/estado activo/i).first();
  const idiomaSeleccionado = page.getByText(/idioma seleccionado/i).first();
  report["Detalles de la Cuenta"] =
    (await isVisible(cuentaCreada, 10000)) &&
    (await isVisible(estadoActivo, 10000)) &&
    (await isVisible(idiomaSeleccionado, 10000));

  // Step 7: Validate Tus Negocios
  const businessList = page.locator("table, ul, [data-testid*='business']").first();
  const addBusinessButton = page.getByRole("button", { name: /agregar negocio/i }).first();
  const businessLimit = page.getByText(/tienes 2 de 3 negocios/i).first();
  report["Tus Negocios"] =
    (await isVisible(businessList, 10000)) &&
    (await isVisible(addBusinessButton, 10000)) &&
    (await isVisible(businessLimit, 10000));

  // Step 8: Validate Términos y Condiciones
  const termsResult = await openLegalLinkAndValidate({
    page,
    context,
    linkText: "Términos y Condiciones",
    headingPattern: /t[eé]rminos y condiciones/i,
    screenshotsDir,
    screenshotName: "05-terminos-y-condiciones.png",
  });
  legalUrls["Términos y Condiciones"] = termsResult.finalUrl;
  report["Términos y Condiciones"] = termsResult.hasHeading && termsResult.hasBodyContent;

  // Step 9: Validate Política de Privacidad
  const privacyResult = await openLegalLinkAndValidate({
    page,
    context,
    linkText: "Política de Privacidad",
    headingPattern: /pol[ií]tica de privacidad/i,
    screenshotsDir,
    screenshotName: "06-politica-de-privacidad.png",
  });
  legalUrls["Política de Privacidad"] = privacyResult.finalUrl;
  report["Política de Privacidad"] = privacyResult.hasHeading && privacyResult.hasBodyContent;

  // Step 10: Final report
  const finalReport = {
    ...Object.fromEntries(Object.entries(report).map(([k, v]) => [k, reportStatus(v)])),
    legalUrls,
  };
  const reportPath = path.join(artifactsDir, "saleads_mi_negocio_final_report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  console.log("SALEADS MI NEGOCIO FINAL REPORT");
  console.table(finalReport);

  const failed = Object.entries(report)
    .filter(([, value]) => !value)
    .map(([name]) => name);
  expect(failed, `Validaciones fallidas: ${failed.join(", ")}`).toEqual([]);
});
