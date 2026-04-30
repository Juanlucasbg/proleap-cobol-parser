const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const RUN_STAMP = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACT_DIR = path.resolve(process.cwd(), "test-results", "saleads-mi-negocio", RUN_STAMP);
const REPORT_PATH = path.join(ARTIFACT_DIR, "final-report.json");

const REQUIRED_STEPS_TEMPLATE = {
  Login: false,
  "Mi Negocio menu": false,
  "Agregar Negocio modal": false,
  "Administrar Negocios view": false,
  "Información General": false,
  "Detalles de la Cuenta": false,
  "Tus Negocios": false,
  "Términos y Condiciones": false,
  "Política de Privacidad": false
};

function ensureArtifactDir() {
  fs.mkdirSync(ARTIFACT_DIR, { recursive: true });
}

function normalizeText(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function stepResult(label, passed, details, evidence = {}) {
  return {
    label,
    status: passed ? "PASS" : "FAIL",
    details,
    evidence
  };
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function clickWithUiWait(locator, page) {
  await expect(locator).toBeVisible({ timeout: 30000 });
  await locator.click();
  await waitForUi(page);
}

async function clickSidebarByText(page, labels) {
  for (const rawLabel of labels) {
    const label = rawLabel.trim();
    const literalPattern = `^\\s*${label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*$`;

    const candidateLocators = [
      page.getByRole("link", { name: new RegExp(literalPattern, "i") }),
      page.getByRole("button", { name: new RegExp(literalPattern, "i") }),
      page.locator("a,button,[role='menuitem'],[role='treeitem'],[data-testid]").filter({
        hasText: new RegExp(literalPattern, "i")
      }),
      page.getByText(new RegExp(literalPattern, "i"))
    ];

    for (const locator of candidateLocators) {
      if (await locator.first().isVisible({ timeout: 1000 }).catch(() => false)) {
        await clickWithUiWait(locator.first(), page);
        return true;
      }
    }
  }

  return false;
}

async function clickAndCapturePopup(page, context, labels) {
  const popupPromise = context
    .waitForEvent("page", { timeout: 7000 })
    .then((popup) => popup)
    .catch(() => null);

  const clicked = await clickSidebarByText(page, labels);
  if (!clicked) {
    return { clicked: false, popup: null };
  }

  const popup = await popupPromise;
  return { clicked: true, popup };
}

async function validateLegalLink(page, context, linkLabels, headingPattern, screenshotName) {
  const clickResult = await clickAndCapturePopup(page, context, linkLabels);
  if (!clickResult.clicked) {
    return {
      passed: false,
      details: `No se encontró el enlace legal (${linkLabels.join(" / ")}).`,
      finalUrl: null,
      screenshot: null
    };
  }

  let legalPage = page;
  const popup = clickResult.popup;

  if (popup) {
    legalPage = popup;
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});
  } else {
    await waitForUi(legalPage);
  }

  const headingFound = await legalPage
    .getByRole("heading", { name: headingPattern })
    .first()
    .isVisible({ timeout: 5000 })
    .catch(() => false);

  const contentFound = await legalPage
    .locator("main,article,section,div,p")
    .filter({ hasText: /terminos|condiciones|privacidad|datos|servicio|uso/i })
    .first()
    .isVisible({ timeout: 5000 })
    .catch(() => false);

  const screenshot = path.join(ARTIFACT_DIR, screenshotName);
  await legalPage.screenshot({ path: screenshot, fullPage: true });

  const finalUrl = legalPage.url();
  const passed = headingFound && contentFound;
  const details = passed
    ? "Página legal abierta y validada."
    : `Validación legal incompleta (heading=${headingFound}, contenido=${contentFound}).`;

  if (popup) {
    await legalPage.close().catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
  }

  return { passed, details, finalUrl, screenshot };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context }) => {
    ensureArtifactDir();
    const requiredSteps = { ...REQUIRED_STEPS_TEMPLATE };

    const baseUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL;
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);
    const results = [];

    // Step 1: Login with Google
    const loginAction = await clickAndCapturePopup(page, context, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Login with Google",
      "Continuar con Google"
    ]);
    const loginClicked = loginAction.clicked;

    if (loginClicked) {
      if (loginAction.popup) {
        await loginAction.popup.waitForLoadState("domcontentloaded");
        await loginAction.popup.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});
      }

      const accountSelectionPage = loginAction.popup || page;
      const gmailOption = accountSelectionPage.getByText("juanlucasbarbiergarzon@gmail.com", {
        exact: true
      });

      if (await gmailOption.isVisible({ timeout: 7000 }).catch(() => false)) {
        await clickWithUiWait(gmailOption, accountSelectionPage);
      }

      if (loginAction.popup) {
        await loginAction.popup.close().catch(() => {});
        await page.bringToFront();
      }
    }

    await waitForUi(page);

    const appShellVisible = await page
      .locator("aside,[class*='sidebar'],nav")
      .first()
      .isVisible({ timeout: 30000 })
      .catch(() => false);

    const sidebarVisible = await page
      .locator("aside,[class*='sidebar'],nav")
      .filter({ hasText: /negocio|dashboard|menu|inicio/i })
      .first()
      .isVisible({ timeout: 30000 })
      .catch(() => false);

    const dashboardScreenshot = path.join(ARTIFACT_DIR, "01-dashboard-loaded.png");
    await page.screenshot({ path: dashboardScreenshot, fullPage: true });

    const loginPassed = appShellVisible && sidebarVisible;
    requiredSteps.Login = loginPassed;
    results.push(
      stepResult("Login", loginPassed, "Se valida interfaz principal y sidebar.", {
        screenshot: dashboardScreenshot
      })
    );

    // Step 2: Open Mi Negocio menu
    const negocioFound = await clickSidebarByText(page, ["Negocio"]);
    const miNegocioFound = await clickSidebarByText(page, ["Mi Negocio"]);
    await waitForUi(page);

    const agregarNegocioMenuVisible = await page
      .getByText(/agregar negocio/i)
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    const administrarNegociosMenuVisible = await page
      .getByText(/administrar negocios/i)
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    const menuScreenshot = path.join(ARTIFACT_DIR, "02-mi-negocio-menu-expanded.png");
    await page.screenshot({ path: menuScreenshot, fullPage: true });

    const menuPassed =
      (negocioFound || miNegocioFound) &&
      agregarNegocioMenuVisible &&
      administrarNegociosMenuVisible;
    requiredSteps["Mi Negocio menu"] = menuPassed;
    results.push(
      stepResult("Mi Negocio menu", menuPassed, "Se valida expansión y opciones del submenú.", {
        screenshot: menuScreenshot
      })
    );

    // Step 3: Validate Agregar Negocio modal
    const agregarNegocioClicked = await clickSidebarByText(page, ["Agregar Negocio"]);
    const modalTitleVisible = await page
      .getByRole("heading", { name: /crear nuevo negocio/i })
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    const nombreInputVisible = await page
      .getByLabel(/nombre del negocio/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(async () => {
        return page
          .getByPlaceholder(/nombre del negocio/i)
          .first()
          .isVisible({ timeout: 3000 })
          .catch(() => false);
      });

    const quotaTextVisible = await page
      .getByText(/tienes\s*2\s*de\s*3\s*negocios/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const cancelButtonVisible = await page
      .getByRole("button", { name: /cancelar/i })
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const createButtonVisible = await page
      .getByRole("button", { name: /crear negocio/i })
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (nombreInputVisible) {
      let input = page.getByLabel(/nombre del negocio/i).first();
      if (!(await input.isVisible({ timeout: 1000 }).catch(() => false))) {
        input = page.getByPlaceholder(/nombre del negocio/i).first();
      }
      await input.fill("Negocio Prueba Automatización").catch(() => {});
    }

    const modalScreenshot = path.join(ARTIFACT_DIR, "03-agregar-negocio-modal.png");
    await page.screenshot({ path: modalScreenshot, fullPage: true });

    if (cancelButtonVisible) {
      await clickWithUiWait(page.getByRole("button", { name: /cancelar/i }).first(), page);
    }

    const modalPassed =
      agregarNegocioClicked &&
      modalTitleVisible &&
      nombreInputVisible &&
      quotaTextVisible &&
      cancelButtonVisible &&
      createButtonVisible;
    requiredSteps["Agregar Negocio modal"] = modalPassed;
    results.push(
      stepResult("Agregar Negocio modal", modalPassed, "Se valida modal Crear Nuevo Negocio.", {
        screenshot: modalScreenshot
      })
    );

    // Step 4: Open Administrar Negocios
    await clickSidebarByText(page, ["Mi Negocio"]);
    const administrarNegociosClicked = await clickSidebarByText(page, ["Administrar Negocios"]);
    await waitForUi(page);

    const infoGeneralVisible = await page
      .getByText(/informacion general|información general/i)
      .first()
      .isVisible({ timeout: 20000 })
      .catch(() => false);

    const detallesCuentaVisible = await page
      .getByText(/detalles de la cuenta/i)
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    const tusNegociosVisible = await page
      .getByText(/tus negocios/i)
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    const seccionLegalVisible = await page
      .getByText(/seccion legal|sección legal/i)
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    const administrarScreenshot = path.join(ARTIFACT_DIR, "04-administrar-negocios.png");
    await page.screenshot({ path: administrarScreenshot, fullPage: true });

    const administrarPassed =
      administrarNegociosClicked &&
      infoGeneralVisible &&
      detallesCuentaVisible &&
      tusNegociosVisible &&
      seccionLegalVisible;
    requiredSteps["Administrar Negocios view"] = administrarPassed;
    results.push(
      stepResult(
        "Administrar Negocios view",
        administrarPassed,
        "Se valida la vista de cuenta con sus secciones principales.",
        { screenshot: administrarScreenshot }
      )
    );

    // Step 5: Validate Información General
    const userNameVisible = await page
      .locator("section,div")
      .filter({ hasText: /informacion general|información general/i })
      .locator("h1,h2,h3,h4,p,span,div")
      .filter({ hasText: /[a-z]{2,}\s+[a-z]{2,}/i })
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const userEmailVisible = await page
      .getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const businessPlanVisible = await page
      .getByText(/business plan/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const cambiarPlanVisible = await page
      .getByRole("button", { name: /cambiar plan/i })
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const infoGeneralPassed =
      userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
    requiredSteps["Información General"] = infoGeneralPassed;
    results.push(
      stepResult("Información General", infoGeneralPassed, "Se validan datos y plan de usuario.")
    );

    // Step 6: Validate Detalles de la Cuenta
    const cuentaCreadaVisible = await page
      .getByText(/cuenta creada/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const estadoActivoVisible = await page
      .getByText(/estado activo/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const idiomaSeleccionadoVisible = await page
      .getByText(/idioma seleccionado/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const detallesCuentaPassed = cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible;
    requiredSteps["Detalles de la Cuenta"] = detallesCuentaPassed;
    results.push(
      stepResult(
        "Detalles de la Cuenta",
        detallesCuentaPassed,
        "Se valida información de estado y configuración de cuenta."
      )
    );

    // Step 7: Validate Tus Negocios
    const businessListVisible = await page
      .locator("section,div")
      .filter({ hasText: /tus negocios/i })
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const agregarNegocioButtonVisible = await page
      .getByRole("button", { name: /agregar negocio/i })
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const quotaVisibleInAccount = await page
      .getByText(/tienes\s*2\s*de\s*3\s*negocios/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    const tusNegociosPassed = businessListVisible && agregarNegocioButtonVisible && quotaVisibleInAccount;
    requiredSteps["Tus Negocios"] = tusNegociosPassed;
    results.push(stepResult("Tus Negocios", tusNegociosPassed, "Se valida listado y límite de negocios."));

    // Step 8: Validate Términos y Condiciones
    const terminosValidation = await validateLegalLink(
      page,
      context,
      ["Términos y Condiciones", "Terminos y Condiciones"],
      /terminos y condiciones|términos y condiciones/i,
      "08-terminos-y-condiciones.png"
    );
    requiredSteps["Términos y Condiciones"] = terminosValidation.passed;
    results.push(
      stepResult("Términos y Condiciones", terminosValidation.passed, terminosValidation.details, {
        screenshot: terminosValidation.screenshot,
        finalUrl: terminosValidation.finalUrl
      })
    );

    // Step 9: Validate Política de Privacidad
    const politicaValidation = await validateLegalLink(
      page,
      context,
      ["Política de Privacidad", "Politica de Privacidad"],
      /politica de privacidad|política de privacidad/i,
      "09-politica-de-privacidad.png"
    );
    requiredSteps["Política de Privacidad"] = politicaValidation.passed;
    results.push(
      stepResult("Política de Privacidad", politicaValidation.passed, politicaValidation.details, {
        screenshot: politicaValidation.screenshot,
        finalUrl: politicaValidation.finalUrl
      })
    );

    const normalizedMap = {};
    for (const [key, value] of Object.entries(requiredSteps)) {
      normalizedMap[normalizeText(key)] = value;
    }

    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      baseUrlUsed: baseUrl || "current browser page",
      summary: requiredSteps,
      normalizedSummary: normalizedMap,
      results
    };

    fs.writeFileSync(REPORT_PATH, JSON.stringify(finalReport, null, 2), "utf-8");

    for (const [name, status] of Object.entries(requiredSteps)) {
      expect(
        status,
        `Validation failed for "${name}". Check report: ${REPORT_PATH}`
      ).toBeTruthy();
    }
  });
});
