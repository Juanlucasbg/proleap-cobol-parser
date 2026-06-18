const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ||
  "juanlucasbarbiergarzon@gmail.com";

const reportTemplate = () => ({
  Login: { pass: false, details: [] },
  "Mi Negocio menu": { pass: false, details: [] },
  "Agregar Negocio modal": { pass: false, details: [] },
  "Administrar Negocios view": { pass: false, details: [] },
  "Información General": { pass: false, details: [] },
  "Detalles de la Cuenta": { pass: false, details: [] },
  "Tus Negocios": { pass: false, details: [] },
  "Términos y Condiciones": { pass: false, details: [] },
  "Política de Privacidad": { pass: false, details: [] },
});

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForTimeout(500);
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle").catch(() => {});
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    const count = await locator.count();
    if (!count) {
      continue;
    }

    const target = locator.first();
    const visible = await target.isVisible().catch(() => false);
    if (visible) {
      return target;
    }
  }

  return null;
}

async function findByText(page, text) {
  const regex = new RegExp(`^${escapeRegex(text)}$`, "i");
  const looseRegex = new RegExp(escapeRegex(text), "i");

  return firstVisibleLocator([
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByRole("tab", { name: regex }),
    page.getByRole("heading", { name: regex }),
    page.getByText(regex),
    page.getByText(looseRegex),
  ]);
}

async function findByAnyText(page, texts) {
  for (const text of texts) {
    const locator = await findByText(page, text);
    if (locator) {
      return locator;
    }
  }

  return null;
}

async function isAnyTextVisible(page, texts) {
  return Boolean(await findByAnyText(page, texts));
}

function markResult(report, section, pass, detail) {
  report[section].pass = pass;
  report[section].details.push(detail);
}

async function validateLegalPage({
  appPage,
  testInfo,
  linkText,
  headingText,
  screenshotName,
}) {
  const link = await findByAnyText(appPage, [linkText]);
  expect(link, `No se encontró el link "${linkText}".`).not.toBeNull();

  const popupPromise = appPage.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
  const navigationPromise = appPage
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 12000 })
    .catch(() => null);

  await link.click();
  await waitForUi(appPage);

  const popup = await popupPromise;
  const legalPage = popup || appPage;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle").catch(() => {});
  } else {
    await navigationPromise;
    await waitForUi(appPage);
  }

  const heading = await findByAnyText(legalPage, [headingText]);
  expect(heading, `No se encontró el heading "${headingText}".`).not.toBeNull();

  const bodyText = await legalPage.locator("body").innerText();
  expect(
    bodyText.trim().length,
    `La página legal "${linkText}" no contiene texto suficiente.`,
  ).toBeGreaterThan(120);

  const finalUrl = legalPage.url();
  await legalPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true,
  });

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("SaleADS - Mi Negocio full workflow", async ({ page }, testInfo) => {
  const report = reportTemplate();
  const legalUrls = {};

  const baseUrl =
    process.env.SALEADS_BASE_URL || process.env.BASE_URL || process.env.APP_URL;

  if (baseUrl) {
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No base URL provided. Set SALEADS_BASE_URL (or BASE_URL/APP_URL) to run this test in the target environment.",
    );
  }

  // Step 1: Login with Google
  {
    const loginButton = await findByAnyText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Login with Google",
    ]);

    expect(loginButton, "No se encontró botón de login con Google.").not.toBeNull();

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");

      const accountOption = await findByAnyText(googlePopup, [GOOGLE_ACCOUNT_EMAIL]);
      if (accountOption) {
        await accountOption.click();
      }

      await googlePopup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
    }

    await waitForUi(page);

    const mainInterfaceVisible = await firstVisibleLocator([
      page.locator("main"),
      page.locator("[role='main']"),
      page.getByText(/dashboard|inicio|panel/i),
    ]);
    expect(mainInterfaceVisible, "No se detectó la interfaz principal.").not.toBeNull();

    const leftSidebarVisible = await firstVisibleLocator([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("[class*='sidebar']"),
    ]);
    expect(leftSidebarVisible, "No se detectó sidebar izquierdo.").not.toBeNull();

    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true,
    });

    markResult(report, "Login", true, "Login con Google y dashboard validados.");
  }

  // Step 2: Open Mi Negocio menu
  {
    const negocio = await findByAnyText(page, ["Negocio"]);
    expect(negocio, 'No se encontró sección "Negocio" en sidebar.').not.toBeNull();
    await negocio.click();
    await waitForUi(page);

    const miNegocio = await findByAnyText(page, ["Mi Negocio"]);
    expect(miNegocio, 'No se encontró opción "Mi Negocio".').not.toBeNull();
    await miNegocio.click();
    await waitForUi(page);

    expect(
      await isAnyTextVisible(page, ["Agregar Negocio"]),
      '"Agregar Negocio" no visible tras expandir menú.',
    ).toBeTruthy();
    expect(
      await isAnyTextVisible(page, ["Administrar Negocios"]),
      '"Administrar Negocios" no visible tras expandir menú.',
    ).toBeTruthy();

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-expanded.png"),
      fullPage: true,
    });

    markResult(report, "Mi Negocio menu", true, "Menú Mi Negocio expandido y validado.");
  }

  // Step 3: Validate Agregar Negocio modal
  {
    const agregarNegocio = await findByAnyText(page, ["Agregar Negocio"]);
    expect(agregarNegocio, 'No se encontró opción "Agregar Negocio".').not.toBeNull();
    await agregarNegocio.click();
    await waitForUi(page);

    expect(
      await isAnyTextVisible(page, ["Crear Nuevo Negocio"]),
      'No se encontró el título "Crear Nuevo Negocio".',
    ).toBeTruthy();

    const nombreNegocioInput = await firstVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[name*='nombre']").first(),
    ]);
    expect(nombreNegocioInput, 'No se encontró el input "Nombre del Negocio".').not.toBeNull();

    expect(
      await isAnyTextVisible(page, ["Tienes 2 de 3 negocios"]),
      'No se encontró el texto "Tienes 2 de 3 negocios".',
    ).toBeTruthy();

    expect(await isAnyTextVisible(page, ["Cancelar"]), 'Botón "Cancelar" no encontrado.').toBeTruthy();
    expect(
      await isAnyTextVisible(page, ["Crear Negocio"]),
      'Botón "Crear Negocio" no encontrado.',
    ).toBeTruthy();

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true,
    });

    await nombreNegocioInput.click();
    await nombreNegocioInput.fill("Negocio Prueba Automatización");

    const cancelar = await findByAnyText(page, ["Cancelar"]);
    expect(cancelar, 'No se encontró botón "Cancelar" para cerrar modal.').not.toBeNull();
    await cancelar.click();
    await waitForUi(page);

    markResult(report, "Agregar Negocio modal", true, "Modal validado y cerrado.");
  }

  // Step 4: Open Administrar Negocios
  {
    if (!(await isAnyTextVisible(page, ["Administrar Negocios"]))) {
      const miNegocio = await findByAnyText(page, ["Mi Negocio"]);
      if (miNegocio) {
        await miNegocio.click();
        await waitForUi(page);
      }
    }

    const administrarNegocios = await findByAnyText(page, ["Administrar Negocios"]);
    expect(
      administrarNegocios,
      'No se encontró opción "Administrar Negocios".',
    ).not.toBeNull();

    await administrarNegocios.click();
    await waitForUi(page);

    expect(
      await isAnyTextVisible(page, ["Información General"]),
      'No se encontró sección "Información General".',
    ).toBeTruthy();
    expect(
      await isAnyTextVisible(page, ["Detalles de la Cuenta"]),
      'No se encontró sección "Detalles de la Cuenta".',
    ).toBeTruthy();
    expect(
      await isAnyTextVisible(page, ["Tus Negocios"]),
      'No se encontró sección "Tus Negocios".',
    ).toBeTruthy();
    expect(
      await isAnyTextVisible(page, ["Sección Legal"]),
      'No se encontró sección "Sección Legal".',
    ).toBeTruthy();

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-view.png"),
      fullPage: true,
    });

    markResult(report, "Administrar Negocios view", true, "Vista de cuenta validada.");
  }

  // Step 5: Validate Información General
  {
    const emailText = await firstVisibleLocator([
      page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
      page.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")),
    ]);

    expect(emailText, "No se detectó email de usuario en Información General.").not.toBeNull();
    expect(
      await isAnyTextVisible(page, ["BUSINESS PLAN"]),
      'No se encontró texto "BUSINESS PLAN".',
    ).toBeTruthy();
    expect(
      await isAnyTextVisible(page, ["Cambiar Plan"]),
      'No se encontró botón "Cambiar Plan".',
    ).toBeTruthy();

    const userNameVisible = await firstVisibleLocator([
      page.getByText(/Juan|Lucas|Barbier/i),
      page.locator("[data-testid*='name']"),
      page.locator("[class*='name']"),
    ]);
    expect(userNameVisible, "No se detectó nombre de usuario visible.").not.toBeNull();

    markResult(report, "Información General", true, "Nombre, email y plan visibles.");
  }

  // Step 6: Validate Detalles de la Cuenta
  {
    expect(
      await isAnyTextVisible(page, ["Cuenta creada"]),
      'No se encontró "Cuenta creada".',
    ).toBeTruthy();
    expect(await isAnyTextVisible(page, ["Estado activo"]), 'No se encontró "Estado activo".').toBeTruthy();
    expect(
      await isAnyTextVisible(page, ["Idioma seleccionado"]),
      'No se encontró "Idioma seleccionado".',
    ).toBeTruthy();

    markResult(report, "Detalles de la Cuenta", true, "Detalles de cuenta validados.");
  }

  // Step 7: Validate Tus Negocios
  {
    const negociosSection = await findByAnyText(page, ["Tus Negocios"]);
    expect(negociosSection, 'No se encontró sección "Tus Negocios".').not.toBeNull();

    expect(
      await isAnyTextVisible(page, ["Agregar Negocio"]),
      'No se encontró botón "Agregar Negocio" en sección de negocios.',
    ).toBeTruthy();
    expect(
      await isAnyTextVisible(page, ["Tienes 2 de 3 negocios"]),
      'No se encontró el texto "Tienes 2 de 3 negocios" en sección de negocios.',
    ).toBeTruthy();

    const businessListItem = await firstVisibleLocator([
      page.locator("ul li"),
      page.locator("[class*='business-card']"),
      page.locator("[class*='business-item']"),
      page.locator("[data-testid*='business']"),
    ]);
    expect(businessListItem, "No se detectó lista de negocios visible.").not.toBeNull();

    markResult(report, "Tus Negocios", true, "Sección de negocios validada.");
  }

  // Step 8: Validate Términos y Condiciones
  {
    legalUrls["Términos y Condiciones"] = await validateLegalPage({
      appPage: page,
      testInfo,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotName: "08-terminos-y-condiciones.png",
    });

    markResult(
      report,
      "Términos y Condiciones",
      true,
      `Página legal validada. URL final: ${legalUrls["Términos y Condiciones"]}`,
    );
  }

  // Step 9: Validate Política de Privacidad
  {
    legalUrls["Política de Privacidad"] = await validateLegalPage({
      appPage: page,
      testInfo,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotName: "09-politica-de-privacidad.png",
    });

    markResult(
      report,
      "Política de Privacidad",
      true,
      `Página legal validada. URL final: ${legalUrls["Política de Privacidad"]}`,
    );
  }

  // Step 10: Final report
  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    results: report,
    legalUrls,
  };

  const reportPayload = JSON.stringify(finalReport, null, 2);
  await testInfo.attach("saleads-mi-negocio-final-report.json", {
    body: reportPayload,
    contentType: "application/json",
  });

  console.log(reportPayload);

  const failedSections = Object.entries(report)
    .filter(([, status]) => !status.pass)
    .map(([name]) => name);

  expect(
    failedSections,
    `Secciones con FAIL: ${failedSections.join(", ") || "Ninguna"}`,
  ).toHaveLength(0);
});
