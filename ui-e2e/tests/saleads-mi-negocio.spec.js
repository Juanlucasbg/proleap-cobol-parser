const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function firstVisible(locators) {
  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  return null;
}

async function clickByVisibleText(page, textRegex) {
  const target = await firstVisible([
    page.getByRole("button", { name: textRegex }).first(),
    page.getByRole("link", { name: textRegex }).first(),
    page.getByText(textRegex).first(),
  ]);

  if (!target) {
    throw new Error(`No clickable element found for: ${textRegex}`);
  }

  await target.click();
  await waitForUiToSettle(page);
}

async function isVisible(locator, timeout = 15000) {
  try {
    await expect(locator).toBeVisible({ timeout });
    return true;
  } catch {
    return false;
  }
}

async function takeCheckpoint(page, testInfo, name, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage,
  });
}

async function ensureMiNegocioExpanded(page) {
  const agregarNegocio = page.getByText("Agregar Negocio", { exact: true }).first();
  const administrarNegocios = page.getByText("Administrar Negocios", { exact: true }).first();

  if ((await agregarNegocio.isVisible().catch(() => false)) && (await administrarNegocios.isVisible().catch(() => false))) {
    return true;
  }

  const miNegocio = page.getByText("Mi Negocio", { exact: true }).first();
  if (!(await miNegocio.isVisible().catch(() => false))) {
    return false;
  }

  await miNegocio.click();
  await waitForUiToSettle(page);

  return (await agregarNegocio.isVisible().catch(() => false)) && (await administrarNegocios.isVisible().catch(() => false));
}

async function openLegalDocument({
  page,
  linkText,
  headingText,
  screenshotName,
  testInfo,
}) {
  let pass = true;
  const appPage = page;
  let legalPage = appPage;
  let openedInNewTab = false;

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickByVisibleText(appPage, new RegExp(linkText, "i"));
  const popup = await popupPromise;

  if (popup) {
    legalPage = popup;
    openedInNewTab = true;
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await legalPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  } else {
    await waitForUiToSettle(legalPage);
  }

  pass &&= await isVisible(legalPage.getByRole("heading", { name: new RegExp(headingText, "i") }).first());
  pass &&= await isVisible(legalPage.locator("p, li").first());

  await takeCheckpoint(legalPage, testInfo, screenshotName);
  const finalUrl = legalPage.url();

  if (openedInNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUiToSettle(appPage);
  } else {
    await appPage.goBack().catch(() => {});
    await waitForUiToSettle(appPage);
  }

  return {
    pass,
    finalUrl,
  };
}

test("SaleADS.ai Mi Negocio full workflow", async ({ page }, testInfo) => {
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

  const evidence = {
    "Términos y Condiciones URL": "",
    "Política de Privacidad URL": "",
  };

  const configuredUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || "";

  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  }

  let stepPass = true;

  // Step 1: Login with Google.
  stepPass = true;
  try {
    const googleButton = await firstVisible([
      page.getByRole("button", { name: /google/i }).first(),
      page.getByRole("link", { name: /google/i }).first(),
      page.getByText(/google/i).first(),
    ]);

    if (!googleButton) {
      throw new Error("Google login button was not found.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
    await googleButton.click();
    await waitForUiToSettle(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
      const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }
      await popup.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
    } else {
      const accountOptionOnPage = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOptionOnPage.isVisible().catch(() => false)) {
        await accountOptionOnPage.click();
        await waitForUiToSettle(page);
      }
    }

    const sidebarVisible = await isVisible(
      firstVisible([
        page.getByRole("navigation").first(),
        page.locator("aside").first(),
      ]).then((locator) => locator || page.locator("nav, aside").first()),
      30000
    );
    const negocioVisible = await isVisible(page.getByText("Negocio", { exact: true }).first(), 30000);

    stepPass &&= sidebarVisible && negocioVisible;
    await takeCheckpoint(page, testInfo, "01-dashboard-loaded.png");
  } catch {
    stepPass = false;
  }
  report.Login = stepPass;

  // Step 2: Open Mi Negocio menu.
  stepPass = true;
  try {
    await clickByVisibleText(page, /^Negocio$/i);
    await clickByVisibleText(page, /^Mi Negocio$/i);

    const expanded = await ensureMiNegocioExpanded(page);
    const agregarVisible = await isVisible(page.getByText("Agregar Negocio", { exact: true }).first());
    const administrarVisible = await isVisible(page.getByText("Administrar Negocios", { exact: true }).first());

    stepPass &&= expanded && agregarVisible && administrarVisible;
    await takeCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  } catch {
    stepPass = false;
  }
  report["Mi Negocio menu"] = stepPass;

  // Step 3: Validate Agregar Negocio modal.
  stepPass = true;
  try {
    await clickByVisibleText(page, /^Agregar Negocio$/i);

    const modalTitle = page.getByText("Crear Nuevo Negocio", { exact: true }).first();
    const businessNameInput = page.getByLabel("Nombre del Negocio").first();
    const planText = page.getByText("Tienes 2 de 3 negocios", { exact: false }).first();
    const cancelButton = page.getByRole("button", { name: /^Cancelar$/i }).first();
    const createButton = page.getByRole("button", { name: /^Crear Negocio$/i }).first();

    stepPass &&= await isVisible(modalTitle);
    stepPass &&= await isVisible(businessNameInput);
    stepPass &&= await isVisible(planText);
    stepPass &&= await isVisible(cancelButton);
    stepPass &&= await isVisible(createButton);

    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.fill("Negocio Prueba Automatización");
    }

    await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    if (await cancelButton.isVisible().catch(() => false)) {
      await cancelButton.click();
      await waitForUiToSettle(page);
    }
  } catch {
    stepPass = false;
  }
  report["Agregar Negocio modal"] = stepPass;

  // Step 4: Open Administrar Negocios.
  stepPass = true;
  try {
    await ensureMiNegocioExpanded(page);
    await clickByVisibleText(page, /^Administrar Negocios$/i);

    const informacionGeneral = page.getByText("Información General", { exact: false }).first();
    const detallesCuenta = page.getByText("Detalles de la Cuenta", { exact: false }).first();
    const tusNegocios = page.getByText("Tus Negocios", { exact: false }).first();
    const seccionLegal = page.getByText("Sección Legal", { exact: false }).first();

    stepPass &&= await isVisible(informacionGeneral);
    stepPass &&= await isVisible(detallesCuenta);
    stepPass &&= await isVisible(tusNegocios);
    stepPass &&= await isVisible(seccionLegal);

    await takeCheckpoint(page, testInfo, "04-administrar-negocios-view.png", true);
  } catch {
    stepPass = false;
  }
  report["Administrar Negocios view"] = stepPass;

  // Step 5: Validate Información General.
  stepPass = true;
  try {
    const emailVisible = await isVisible(page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first());
    const userNameVisible =
      (await isVisible(page.getByText(/juan|lucas|barbier|garzon/i).first(), 5000)) ||
      (await isVisible(page.getByText(/nombre/i).first(), 5000));
    const businessPlanVisible = await isVisible(page.getByText("BUSINESS PLAN", { exact: false }).first());
    const cambiarPlanVisible = await isVisible(page.getByRole("button", { name: /^Cambiar Plan$/i }).first());

    stepPass &&= userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible;
  } catch {
    stepPass = false;
  }
  report["Información General"] = stepPass;

  // Step 6: Validate Detalles de la Cuenta.
  stepPass = true;
  try {
    const cuentaCreada = await isVisible(page.getByText("Cuenta creada", { exact: false }).first());
    const estadoActivo = await isVisible(page.getByText("Estado activo", { exact: false }).first());
    const idiomaSeleccionado = await isVisible(page.getByText("Idioma seleccionado", { exact: false }).first());

    stepPass &&= cuentaCreada && estadoActivo && idiomaSeleccionado;
  } catch {
    stepPass = false;
  }
  report["Detalles de la Cuenta"] = stepPass;

  // Step 7: Validate Tus Negocios.
  stepPass = true;
  try {
    const tusNegociosHeader = page.getByText("Tus Negocios", { exact: false }).first();
    const addBusinessButton = page.getByRole("button", { name: /^Agregar Negocio$/i }).first();
    const quotaText = page.getByText("Tienes 2 de 3 negocios", { exact: false }).first();
    const businessListCandidate = page.locator("li, tr, [role='row']").first();

    stepPass &&= await isVisible(tusNegociosHeader);
    stepPass &&= await isVisible(addBusinessButton);
    stepPass &&= await isVisible(quotaText);
    stepPass &&=
      (await businessListCandidate.isVisible().catch(() => false)) ||
      (await isVisible(page.getByText(/negocio/i).nth(1), 5000));
  } catch {
    stepPass = false;
  }
  report["Tus Negocios"] = stepPass;

  // Step 8: Validate Términos y Condiciones.
  try {
    const terms = await openLegalDocument({
      page,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo,
    });
    report["Términos y Condiciones"] = terms.pass;
    evidence["Términos y Condiciones URL"] = terms.finalUrl;
  } catch {
    report["Términos y Condiciones"] = false;
  }

  // Step 9: Validate Política de Privacidad.
  try {
    const privacy = await openLegalDocument({
      page,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotName: "06-politica-de-privacidad.png",
      testInfo,
    });
    report["Política de Privacidad"] = privacy.pass;
    evidence["Política de Privacidad URL"] = privacy.finalUrl;
  } catch {
    report["Política de Privacidad"] = false;
  }

  // Step 10: Final report output.
  const finalRows = Object.entries(report).map(([name, pass]) => ({
    check: name,
    status: pass ? "PASS" : "FAIL",
  }));

  console.log("SaleADS Mi Negocio workflow report:");
  console.table(finalRows);
  console.log(`Términos y Condiciones final URL: ${evidence["Términos y Condiciones URL"] || "N/A"}`);
  console.log(`Política de Privacidad final URL: ${evidence["Política de Privacidad URL"] || "N/A"}`);

  const reportPath = testInfo.outputPath("final-workflow-report.json");
  await fs.writeFile(
    reportPath,
    JSON.stringify(
      {
        report,
        evidence,
      },
      null,
      2
    ),
    "utf8"
  );

  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  expect(Object.values(report).every(Boolean)).toBeTruthy();
});
