const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

const ARTIFACTS_DIR = path.join(__dirname, "..", "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");

function ensureArtifactsDir() {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

async function safeScreenshot(page, fileName, fullPage = false) {
  const target = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: target, fullPage });
  return target;
}

async function clickByVisibleText(page, text, options = {}) {
  const candidateLocators = [
    page.getByRole("button", { name: text, exact: false }),
    page.getByRole("link", { name: text, exact: false }),
    page.getByText(text, { exact: false }),
  ];

  for (const locator of candidateLocators) {
    const count = await locator.count();
    if (count > 0) {
      const first = locator.first();
      if (await first.isVisible().catch(() => false)) {
        await first.click(options);
        await page.waitForLoadState("networkidle");
        return true;
      }
    }
  }
  return false;
}

async function clickAndWaitForPossibleNewTab(page, context, text) {
  let popup = null;
  let clicked = false;
  const popupPromise = context
    .waitForEvent("page", { timeout: 5000 })
    .then((p) => p)
    .catch(() => null);

  clicked = await clickByVisibleText(page, text);
  if (!clicked) {
    return { clicked: false, popup: null, activePage: page };
  }

  popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle");
    return { clicked: true, popup, activePage: popup };
  }

  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
  return { clicked: true, popup: null, activePage: page };
}

async function clickLoginWithGoogle(page, context) {
  const loginCandidates = [
    "Sign in with Google",
    "Continuar con Google",
    "Iniciar sesión con Google",
    "Login with Google",
    "Google",
  ];

  for (const label of loginCandidates) {
    let popup = null;
    const popupPromise = context
      .waitForEvent("page", { timeout: 4000 })
      .then((p) => p)
      .catch(() => null);

    const clicked = await clickByVisibleText(page, label);
    if (!clicked) {
      continue;
    }

    popup = await popupPromise;
    const googlePage = popup ?? page;
    await googlePage.waitForLoadState("domcontentloaded").catch(() => {});

    const googleAccount = googlePage.getByText(
      "juanlucasbarbiergarzon@gmail.com",
      {
        exact: false,
      }
    );

    if (await googleAccount.count()) {
      await googleAccount.first().click();
      await googlePage.waitForLoadState("networkidle").catch(() => {});
    }

    if (popup) {
      await page.bringToFront();
      await page.waitForLoadState("networkidle").catch(() => {});
    }

    return true;
  }

  return false;
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("validates Mi Negocio full workflow", async ({ page, context }) => {
    test.setTimeout(15 * 60 * 1000);
    ensureArtifactsDir();

    const report = {
      Login: "FAIL",
      "Mi Negocio menu": "FAIL",
      "Agregar Negocio modal": "FAIL",
      "Administrar Negocios view": "FAIL",
      "Información General": "FAIL",
      "Detalles de la Cuenta": "FAIL",
      "Tus Negocios": "FAIL",
      "Términos y Condiciones": "FAIL",
      "Política de Privacidad": "FAIL",
    };

    const legalUrls = {
      "Términos y Condiciones": null,
      "Política de Privacidad": null,
    };

    // Step 1: Login with Google.
    if (process.env.SALEADS_LOGIN_URL) {
      await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "networkidle" });
    }

    const loginClicked = await clickLoginWithGoogle(page, context);
    expect(loginClicked).toBeTruthy();

    await page.waitForLoadState("domcontentloaded");
    await page.waitForLoadState("networkidle");

    const sidebar = page.locator("aside, nav").first();
    const sidebarVisible = await sidebar.isVisible().catch(() => false);
    const negocioTextVisible = await page
      .getByText("Negocio", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    report.Login = sidebarVisible && negocioTextVisible ? "PASS" : "FAIL";
    expect
      .soft(sidebarVisible && negocioTextVisible)
      .toBeTruthy();

    await safeScreenshot(page, "01-dashboard-loaded.png");

    // Step 2: Open Mi Negocio menu.
    const negocioClicked = await clickByVisibleText(page, "Negocio");
    expect(negocioClicked).toBeTruthy();

    const miNegocioClicked = await clickByVisibleText(page, "Mi Negocio");
    expect(miNegocioClicked).toBeTruthy();

    const agregarMenuVisible = await page
      .getByText("Agregar Negocio", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    const administrarMenuVisible = await page
      .getByText("Administrar Negocios", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    report["Mi Negocio menu"] =
      agregarMenuVisible && administrarMenuVisible ? "PASS" : "FAIL";

    expect.soft(agregarMenuVisible).toBeTruthy();
    expect.soft(administrarMenuVisible).toBeTruthy();
    await safeScreenshot(page, "02-mi-negocio-menu-expanded.png");

    // Step 3: Validate Agregar Negocio modal.
    const agregarClicked = await clickByVisibleText(page, "Agregar Negocio");
    expect(agregarClicked).toBeTruthy();

    const modal = page
      .locator('[role="dialog"], .modal, [data-testid*="modal"]')
      .first();
    await expect.soft(modal).toBeVisible({ timeout: 15000 });

    const modalTitle = page.getByText("Crear Nuevo Negocio", { exact: false });
    const businessNameInput = page.getByLabel("Nombre del Negocio", {
      exact: false,
    });
    const quotaText = page.getByText("Tienes 2 de 3 negocios", {
      exact: false,
    });
    const cancelButton = page.getByRole("button", {
      name: "Cancelar",
      exact: false,
    });
    const createButton = page.getByRole("button", {
      name: "Crear Negocio",
      exact: false,
    });

    const modalChecks = await Promise.all([
      modalTitle.first().isVisible().catch(() => false),
      businessNameInput.first().isVisible().catch(() => false),
      quotaText.first().isVisible().catch(() => false),
      cancelButton.first().isVisible().catch(() => false),
      createButton.first().isVisible().catch(() => false),
    ]);
    report["Agregar Negocio modal"] = modalChecks.every(Boolean)
      ? "PASS"
      : "FAIL";

    expect.soft(modalChecks.every(Boolean)).toBeTruthy();
    await safeScreenshot(page, "03-agregar-negocio-modal.png");

    if (await businessNameInput.count()) {
      await businessNameInput.first().click();
      await businessNameInput.first().fill("Negocio Prueba Automatizacion");
    }
    if (await cancelButton.count()) {
      await cancelButton.first().click();
      await page.waitForLoadState("networkidle");
    } else {
      await page.keyboard.press("Escape");
      await page.waitForLoadState("networkidle");
    }

    // Step 4: Open Administrar Negocios.
    const adminVisibleBeforeClick = await page
      .getByText("Administrar Negocios", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    if (!adminVisibleBeforeClick) {
      await clickByVisibleText(page, "Mi Negocio");
    }

    const administrarClicked = await clickByVisibleText(
      page,
      "Administrar Negocios"
    );
    expect(administrarClicked).toBeTruthy();

    const infoGeneral = page.getByText("Información General", { exact: false });
    const detallesCuenta = page.getByText("Detalles de la Cuenta", {
      exact: false,
    });
    const tusNegocios = page.getByText("Tus Negocios", { exact: false });
    const seccionLegal = page.getByText("Sección Legal", { exact: false });
    const adminChecks = await Promise.all([
      infoGeneral.first().isVisible().catch(() => false),
      detallesCuenta.first().isVisible().catch(() => false),
      tusNegocios.first().isVisible().catch(() => false),
      seccionLegal.first().isVisible().catch(() => false),
    ]);
    report["Administrar Negocios view"] = adminChecks.every(Boolean)
      ? "PASS"
      : "FAIL";
    expect.soft(adminChecks.every(Boolean)).toBeTruthy();
    await safeScreenshot(page, "04-administrar-negocios-page.png", true);

    // Step 5: Validate Informacion General.
    const userNameVisible = await page
      .locator(
        [
          '[data-testid*="name"]',
          '[class*="name"]',
          'text=/^[A-Za-zÀ-ÿ\\s]{3,}$/',
        ].join(",")
      )
      .first()
      .isVisible()
      .catch(() => false);
    const userEmailVisible = await page
      .getByText(/@/, { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    const businessPlanVisible = await page
      .getByText("BUSINESS PLAN", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    const cambiarPlanVisible = await page
      .getByRole("button", { name: "Cambiar Plan", exact: false })
      .first()
      .isVisible()
      .catch(() => false);

    report["Información General"] =
      userNameVisible &&
      userEmailVisible &&
      businessPlanVisible &&
      cambiarPlanVisible
        ? "PASS"
        : "FAIL";
    expect.soft(report["Información General"]).toBe("PASS");

    // Step 6: Validate Detalles de la Cuenta.
    const cuentaCreadaVisible = await page
      .getByText("Cuenta creada", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    const estadoActivoVisible = await page
      .getByText("Estado activo", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    const idiomaSeleccionadoVisible = await page
      .getByText("Idioma seleccionado", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);

    report["Detalles de la Cuenta"] =
      cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible
        ? "PASS"
        : "FAIL";
    expect.soft(report["Detalles de la Cuenta"]).toBe("PASS");

    // Step 7: Validate Tus Negocios.
    const businessListVisible = await page
      .locator(
        '[data-testid*="business"], [class*="business"], [role="list"], table'
      )
      .first()
      .isVisible()
      .catch(() => false);
    const addBusinessButtonVisible = await page
      .getByRole("button", { name: "Agregar Negocio", exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    const quotaVisible = await page
      .getByText("Tienes 2 de 3 negocios", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);

    report["Tus Negocios"] =
      businessListVisible && addBusinessButtonVisible && quotaVisible
        ? "PASS"
        : "FAIL";
    expect.soft(report["Tus Negocios"]).toBe("PASS");

    // Step 8: Validate Terminos y Condiciones.
    const administrarNegociosUrl = page.url();

    const terminosResult = await clickAndWaitForPossibleNewTab(
      page,
      context,
      "Términos y Condiciones"
    );
    expect(terminosResult.clicked).toBeTruthy();

    const terminosPage = terminosResult.activePage;
    const terminosHeadingVisible = await terminosPage
      .getByText("Términos y Condiciones", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    const terminosContentVisible = await terminosPage
      .locator("main, article, p")
      .first()
      .isVisible()
      .catch(() => false);
    report["Términos y Condiciones"] =
      terminosHeadingVisible && terminosContentVisible ? "PASS" : "FAIL";

    legalUrls["Términos y Condiciones"] = terminosPage.url();
    await safeScreenshot(
      terminosPage,
      "05-terminos-y-condiciones.png",
      true
    );

    if (terminosResult.popup) {
      await terminosResult.popup.close();
      await page.bringToFront();
      await page.waitForLoadState("networkidle");
    } else {
      await page.goto(administrarNegociosUrl, { waitUntil: "networkidle" });
      await page.waitForLoadState("networkidle");
    }

    // Step 9: Validate Politica de Privacidad.
    const privacidadResult = await clickAndWaitForPossibleNewTab(
      page,
      context,
      "Política de Privacidad"
    );
    expect(privacidadResult.clicked).toBeTruthy();

    const privacidadPage = privacidadResult.activePage;
    const privacidadHeadingVisible = await privacidadPage
      .getByText("Política de Privacidad", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    const privacidadContentVisible = await privacidadPage
      .locator("main, article, p")
      .first()
      .isVisible()
      .catch(() => false);
    report["Política de Privacidad"] =
      privacidadHeadingVisible && privacidadContentVisible ? "PASS" : "FAIL";

    legalUrls["Política de Privacidad"] = privacidadPage.url();
    await safeScreenshot(
      privacidadPage,
      "06-politica-de-privacidad.png",
      true
    );

    if (privacidadResult.popup) {
      await privacidadResult.popup.close();
      await page.bringToFront();
      await page.waitForLoadState("networkidle");
    } else {
      await page.goto(administrarNegociosUrl, { waitUntil: "networkidle" });
      await page.waitForLoadState("networkidle");
    }

    // Step 10: Final report artifact with PASS/FAIL per requested fields.
    const detailedReport = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      finalStatus: Object.values(report).every((status) => status === "PASS")
        ? "PASS"
        : "FAIL",
      steps: report,
      legalUrls,
      screenshotsDirectory: SCREENSHOTS_DIR,
      note: "This test is environment-agnostic and uses visible text selectors whenever possible.",
    };

    fs.writeFileSync(
      path.join(ARTIFACTS_DIR, "mi-negocio-final-report.json"),
      `${JSON.stringify(detailedReport, null, 2)}\n`,
      "utf8"
    );

    // Attach report to test output for CI visibility.
    await test.info().attach("mi-negocio-final-report", {
      body: JSON.stringify(detailedReport, null, 2),
      contentType: "application/json",
    });
  });
});
