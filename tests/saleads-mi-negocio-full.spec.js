const fs = require("node:fs/promises");
const path = require("node:path");
const { test } = require("@playwright/test");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const runTimestamp = new Date().toISOString().replaceAll(":", "-");
  const runDir = path.join("test-results", "saleads-mi-negocio", runTimestamp);
  await fs.mkdir(runDir, { recursive: true });

  /** @type {Record<string, "PASS" | "FAIL">} */
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const evidence = {
    screenshots: [],
    finalUrls: {}
  };

  const waitForUiToLoad = async (targetPage = page) => {
    await targetPage.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => null);
    await targetPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => null);
    await targetPage.waitForTimeout(400);
  };

  const isVisible = async (locator, timeout = 8000) => {
    try {
      await locator.first().waitFor({ state: "visible", timeout });
      return true;
    } catch {
      return false;
    }
  };

  const capture = async (name, targetPage = page, fullPage = false) => {
    const shotPath = path.join(runDir, `${name}.png`);
    await targetPage.screenshot({ path: shotPath, fullPage });
    evidence.screenshots.push(shotPath);
  };

  const setStatus = (key, isPass) => {
    report[key] = isPass ? "PASS" : "FAIL";
  };

  const findFirstClickableByText = async (patterns, targetPage = page) => {
    for (const pattern of patterns) {
      const button = targetPage.getByRole("button", { name: pattern }).first();
      if (await isVisible(button, 2000)) {
        return button;
      }

      const link = targetPage.getByRole("link", { name: pattern }).first();
      if (await isVisible(link, 2000)) {
        return link;
      }

      const byText = targetPage.getByText(pattern).first();
      if (await isVisible(byText, 2000)) {
        return byText;
      }
    }

    return null;
  };

  const maybeSelectGoogleAccount = async (targetPage) => {
    const accountOption = targetPage.getByText(ACCOUNT_EMAIL, { exact: false }).first();
    if (await isVisible(accountOption, 7000)) {
      await accountOption.click();
      await waitForUiToLoad(targetPage);
      return true;
    }

    return false;
  };

  const openAndValidateLegalDocument = async (labelPattern, headingPattern, reportKey, screenshotBaseName) => {
    const legalLink = await findFirstClickableByText([labelPattern]);
    if (!legalLink) {
      setStatus(reportKey, false);
      return;
    }

    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await legalLink.click();
    await waitForUiToLoad(page);

    const popup = await popupPromise;
    const targetPage = popup ?? page;
    await waitForUiToLoad(targetPage);

    const headingVisible =
      (await isVisible(targetPage.getByRole("heading", { name: headingPattern }).first(), 10000)) ||
      (await isVisible(targetPage.getByText(headingPattern).first(), 10000));
    const legalContentVisible = await isVisible(targetPage.locator("p, li").first(), 10000);

    await capture(screenshotBaseName, targetPage, true);
    evidence.finalUrls[reportKey] = targetPage.url();
    setStatus(reportKey, headingVisible && legalContentVisible);

    if (popup) {
      await popup.close().catch(() => null);
      await page.bringToFront();
      await waitForUiToLoad(page);
      return;
    }

    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUiToLoad(page);
  };

  const startUrl = process.env.SALEADS_START_URL || process.env.BASE_URL || "";
  if ((page.url() === "about:blank" || page.url() === "") && startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  }
  await waitForUiToLoad(page);

  // Step 1: Login with Google.
  const loginButton = await findFirstClickableByText([
    /Sign in with Google/i,
    /Iniciar sesion con Google/i,
    /Iniciar sesi[oó]n con Google/i,
    /Continuar con Google/i,
    /Google/i
  ]);

  let loginPassed = false;
  if (loginButton) {
    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUiToLoad(page);

    const popup = await popupPromise;
    if (popup) {
      await waitForUiToLoad(popup);
      await maybeSelectGoogleAccount(popup);
      await popup.waitForEvent("close", { timeout: 25000 }).catch(() => null);
      await page.bringToFront();
      await waitForUiToLoad(page);
    } else {
      await maybeSelectGoogleAccount(page);
      await waitForUiToLoad(page);
    }

    const sidebarVisible =
      (await isVisible(page.locator("aside").first(), 12000)) ||
      (await isVisible(page.getByRole("navigation").first(), 12000));
    const negocioTextVisible = await isVisible(page.getByText(/Negocio/i).first(), 12000);
    loginPassed = sidebarVisible && negocioTextVisible;
  }

  setStatus("Login", loginPassed);
  await capture("01-dashboard-loaded", page, true);

  // Step 2: Open Mi Negocio menu.
  const negocioSection = await findFirstClickableByText([/^Negocio$/i, /Negocio/i]);
  if (negocioSection) {
    await negocioSection.click();
    await waitForUiToLoad(page);
  }

  const miNegocioItem = await findFirstClickableByText([/^Mi Negocio$/i, /Mi Negocio/i]);
  if (miNegocioItem) {
    await miNegocioItem.click();
    await waitForUiToLoad(page);
  }

  const agregarNegocioVisible = await isVisible(page.getByText(/Agregar Negocio/i).first(), 10000);
  const administrarNegociosVisible = await isVisible(page.getByText(/Administrar Negocios/i).first(), 10000);
  setStatus("Mi Negocio menu", agregarNegocioVisible && administrarNegociosVisible);
  await capture("02-mi-negocio-menu-expanded", page);

  // Step 3: Validate Agregar Negocio modal.
  const agregarNegocioTrigger = await findFirstClickableByText([/^Agregar Negocio$/i, /Agregar Negocio/i]);
  if (agregarNegocioTrigger) {
    await agregarNegocioTrigger.click();
    await waitForUiToLoad(page);
  }

  const modalTitleVisible = await isVisible(page.getByText(/Crear Nuevo Negocio/i).first(), 10000);
  let nombreInput = page.getByLabel(/Nombre del Negocio/i).first();
  if (!(await isVisible(nombreInput, 1000))) {
    nombreInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
  }
  if (!(await isVisible(nombreInput, 1000))) {
    nombreInput = page.locator("input[name*=negocio], input[id*=negocio]").first();
  }
  const nombreInputVisible = await isVisible(nombreInput, 8000);
  const businessLimitTextVisible = await isVisible(page.getByText(/Tienes 2 de 3 negocios/i).first(), 8000);
  const cancelarVisible = await isVisible(page.getByRole("button", { name: /Cancelar/i }).first(), 8000);
  const crearNegocioVisible = await isVisible(page.getByRole("button", { name: /Crear Negocio/i }).first(), 8000);

  await capture("03-agregar-negocio-modal", page);
  setStatus(
    "Agregar Negocio modal",
    modalTitleVisible && nombreInputVisible && businessLimitTextVisible && cancelarVisible && crearNegocioVisible
  );

  if (nombreInputVisible) {
    await nombreInput.fill("Negocio Prueba Automatizacion");
  }

  const cancelButton = page.getByRole("button", { name: /Cancelar/i }).first();
  if (await isVisible(cancelButton, 3000)) {
    await cancelButton.click();
    await waitForUiToLoad(page);
  }

  // Step 4: Open Administrar Negocios and validate the account page sections.
  if (!(await isVisible(page.getByText(/Administrar Negocios/i).first(), 3000))) {
    const miNegocioAgain = await findFirstClickableByText([/^Mi Negocio$/i, /Mi Negocio/i]);
    if (miNegocioAgain) {
      await miNegocioAgain.click();
      await waitForUiToLoad(page);
    }
  }

  const administrarNegociosItem = await findFirstClickableByText([/^Administrar Negocios$/i, /Administrar Negocios/i]);
  if (administrarNegociosItem) {
    await administrarNegociosItem.click();
    await waitForUiToLoad(page);
  }

  const infoGeneralSectionVisible = await isVisible(page.getByText(/Informacion General|Informaci[oó]n General/i).first(), 12000);
  const detallesCuentaSectionVisible = await isVisible(page.getByText(/Detalles de la Cuenta/i).first(), 12000);
  const tusNegociosSectionVisible = await isVisible(page.getByText(/Tus Negocios/i).first(), 12000);
  const seccionLegalVisible = await isVisible(page.getByText(/Seccion Legal|Secci[oó]n Legal/i).first(), 12000);
  setStatus(
    "Administrar Negocios view",
    infoGeneralSectionVisible && detallesCuentaSectionVisible && tusNegociosSectionVisible && seccionLegalVisible
  );
  await capture("04-administrar-negocios-page", page, true);

  // Step 5: Validate Informacion General.
  const emailVisible = await isVisible(page.getByText(/@/).first(), 8000);
  const businessPlanVisible = await isVisible(page.getByText(/BUSINESS PLAN/i).first(), 8000);
  const cambiarPlanVisible = await isVisible(page.getByRole("button", { name: /Cambiar Plan/i }).first(), 8000);
  const expectedUserName = process.env.SALEADS_EXPECTED_USER_NAME;
  const userNameVisible = expectedUserName
    ? await isVisible(page.getByText(expectedUserName, { exact: false }).first(), 8000)
    : await isVisible(page.locator("h1, h2, h3").filter({ hasText: /[A-Za-z].*[A-Za-z]/ }).first(), 8000);
  setStatus("Información General", userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible);

  // Step 6: Validate Detalles de la Cuenta.
  const cuentaCreadaVisible = await isVisible(page.getByText(/Cuenta creada/i).first(), 8000);
  const estadoActivoVisible = await isVisible(page.getByText(/Estado activo/i).first(), 8000);
  const idiomaSeleccionadoVisible = await isVisible(page.getByText(/Idioma seleccionado/i).first(), 8000);
  setStatus("Detalles de la Cuenta", cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible);

  // Step 7: Validate Tus Negocios.
  const businessListVisible =
    (await isVisible(page.locator("table").first(), 4000)) ||
    (await isVisible(page.locator("ul, ol").filter({ hasText: /Negocio/i }).first(), 4000)) ||
    (await isVisible(page.locator("div").filter({ hasText: /Negocio/i }).nth(1), 4000));
  const addBusinessButtonVisible = await isVisible(page.getByRole("button", { name: /Agregar Negocio/i }).first(), 8000);
  const limitTextVisibleOnAccount = await isVisible(page.getByText(/Tienes 2 de 3 negocios/i).first(), 8000);
  setStatus("Tus Negocios", businessListVisible && addBusinessButtonVisible && limitTextVisibleOnAccount);

  // Step 8: Validate Terminos y Condiciones.
  await openAndValidateLegalDocument(
    /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
    /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
    "Términos y Condiciones",
    "05-terminos-y-condiciones"
  );

  // Step 9: Validate Politica de Privacidad.
  await openAndValidateLegalDocument(
    /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
    /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
    "Política de Privacidad",
    "06-politica-de-privacidad"
  );

  // Step 10: Final report with PASS/FAIL by required fields.
  const output = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    evidence
  };
  const reportPath = path.join(runDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(output, null, 2), "utf8");

  // This log line is intentionally machine-readable for CI artifacts parsing.
  console.log(`SALEADS_MI_NEGOCIO_REPORT=${reportPath}`);
  console.table(report);

  const failedSteps = Object.entries(report)
    .filter(([, status]) => status === "FAIL")
    .map(([name]) => name);
  if (failedSteps.length) {
    throw new Error(`Validation failures: ${failedSteps.join(", ")}`);
  }
});
