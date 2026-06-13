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

const UI_WAIT_AFTER_CLICK_MS = 500;

function buildRegex(textOrRegex) {
  if (textOrRegex instanceof RegExp) {
    return textOrRegex;
  }

  const escaped = textOrRegex.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(escaped, "i");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");

  try {
    await page.waitForLoadState("networkidle", { timeout: 8000 });
  } catch (_error) {
    // Some views keep network activity alive (analytics/polling); continue safely.
  }

  await page.waitForTimeout(UI_WAIT_AFTER_CLICK_MS);
}

async function findVisibleByText(page, textOptions, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs;
  const options = Array.isArray(textOptions) ? textOptions : [textOptions];
  const locators = [];

  for (const option of options) {
    const pattern = buildRegex(option);
    locators.push(page.getByRole("button", { name: pattern }).first());
    locators.push(page.getByRole("link", { name: pattern }).first());
    locators.push(page.getByRole("menuitem", { name: pattern }).first());
    locators.push(page.getByRole("tab", { name: pattern }).first());
    locators.push(page.getByRole("heading", { name: pattern }).first());
    locators.push(page.getByText(pattern).first());
    locators.push(page.locator("label", { hasText: pattern }).first());
  }

  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await page.waitForTimeout(250);
  }

  return null;
}

async function clickVisibleText(page, textOptions, description) {
  const locator = await findVisibleByText(page, textOptions);
  if (!locator) {
    throw new Error(`Could not find clickable text for "${description}"`);
  }

  await locator.click();
  await waitForUi(page);
}

async function assertVisibleByText(page, textOptions, description) {
  const locator = await findVisibleByText(page, textOptions);
  expect(locator, `Expected visible text: ${description}`).not.toBeNull();
}

async function attachCheckpoint(page, testInfo, name, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage
  });
}

async function clickAndValidateLegalPage({
  appPage,
  linkTexts,
  headingTexts,
  reportKey,
  screenshotName,
  legalResults
}) {
  const context = appPage.context();
  const applicationUrl = appPage.url();
  const link = await findVisibleByText(appPage, linkTexts);

  if (!link) {
    throw new Error(`Could not find legal link for "${reportKey}"`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
  await link.click();
  await waitForUi(appPage);

  const popupPage = await popupPromise;
  const legalPage = popupPage || appPage;
  await waitForUi(legalPage);

  const heading = await findVisibleByText(legalPage, headingTexts, 10000);
  expect(heading, `${reportKey} heading should be visible`).not.toBeNull();

  const legalBody = legalPage
    .locator("main, article, section, body")
    .filter({ hasText: buildRegex(headingTexts[0]) })
    .first();

  const bodyVisible = await legalBody.isVisible().catch(() => false);
  if (!bodyVisible) {
    const paragraphsCount = await legalPage.locator("p, li").count();
    expect(paragraphsCount, `${reportKey} legal content should be visible`).toBeGreaterThan(3);
  }

  await attachCheckpoint(legalPage, legalResults.testInfo, screenshotName, true);
  legalResults.urls[reportKey] = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  if (appPage.url() !== applicationUrl) {
    await appPage.goBack();
    await waitForUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const errors = [];
  const legalUrls = {};
  const startUrl = process.env.SALEADS_LOGIN_URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  const runStep = async (field, fn) => {
    try {
      await fn();
      results[field] = "PASS";
    } catch (error) {
      errors.push(`${field}: ${error.message}`);
      results[field] = "FAIL";
    }
  };

  await runStep("Login", async () => {
    if (!startUrl && page.url() === "about:blank") {
      throw new Error(
        "Page is about:blank. Set SALEADS_LOGIN_URL or pre-open SaleADS login page before running."
      );
    }

    await clickVisibleText(
      page,
      [
        /Sign in with Google/i,
        /Continuar con Google/i,
        /Iniciar sesi[oó]n con Google/i,
        /^Google$/i
      ],
      "Google login"
    );

    const googleAccount = await findVisibleByText(
      page,
      [/juanlucasbarbiergarzon@gmail\.com/i],
      7000
    );
    if (googleAccount) {
      await googleAccount.click();
      await waitForUi(page);
    }

    await assertVisibleByText(
      page,
      [/Dashboard/i, /Inicio/i, /Panel/i, /Mi Negocio/i],
      "main application interface"
    );

    const sidebar = page.locator("aside, nav").first();
    const sidebarVisible = await sidebar.isVisible().catch(() => false);
    if (!sidebarVisible) {
      await assertVisibleByText(page, [/Negocio/i], "left sidebar navigation");
    }

    await attachCheckpoint(page, testInfo, "01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    await clickVisibleText(page, [/Negocio/i], "Negocio section");
    await clickVisibleText(page, [/Mi Negocio/i], "Mi Negocio option");

    await assertVisibleByText(page, [/Agregar Negocio/i], "Agregar Negocio submenu");
    await assertVisibleByText(page, [/Administrar Negocios/i], "Administrar Negocios submenu");

    await attachCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickVisibleText(page, [/Agregar Negocio/i], "Agregar Negocio action");

    await assertVisibleByText(page, [/Crear Nuevo Negocio/i], "modal title");
    const businessNameInput =
      (await findVisibleByText(page, [/Nombre del Negocio/i])) ||
      page.getByPlaceholder(/Nombre del Negocio/i).first();
    const inputVisible = businessNameInput
      ? await businessNameInput.isVisible().catch(() => false)
      : false;
    expect(inputVisible, "Nombre del Negocio input should be visible").toBeTruthy();

    await assertVisibleByText(page, [/Tienes\s*2\s*de\s*3\s*negocios/i], "business quota text");
    await assertVisibleByText(page, [/Cancelar/i], "Cancelar button");
    await assertVisibleByText(page, [/Crear Negocio/i], "Crear Negocio button");

    await attachCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await waitForUi(page);
    await clickVisibleText(page, [/Cancelar/i], "Cerrar modal con Cancelar");
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegociosVisible = await findVisibleByText(page, [/Administrar Negocios/i], 2500);
    if (!administrarNegociosVisible) {
      const miNegocio = await findVisibleByText(page, [/Mi Negocio/i], 2500);
      if (miNegocio) {
        await miNegocio.click();
        await waitForUi(page);
      }
    }

    await clickVisibleText(page, [/Administrar Negocios/i], "Administrar Negocios option");

    await assertVisibleByText(page, [/Informaci[oó]n General/i], "Información General section");
    await assertVisibleByText(page, [/Detalles de la Cuenta/i], "Detalles de la Cuenta section");
    await assertVisibleByText(page, [/Tus Negocios/i], "Tus Negocios section");
    await assertVisibleByText(page, [/Secci[oó]n Legal/i], "Sección Legal section");

    await attachCheckpoint(page, testInfo, "04-administrar-negocios-full-page.png", true);
  });

  await runStep("Información General", async () => {
    await assertVisibleByText(page, [/Informaci[oó]n General/i], "Información General header");
    await assertVisibleByText(page, [/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i], "user email");
    await assertVisibleByText(page, [/BUSINESS PLAN/i], "BUSINESS PLAN text");
    await assertVisibleByText(page, [/Cambiar Plan/i], "Cambiar Plan button");

    const nameField = await findVisibleByText(page, [/Nombre/i], 5000);
    expect(nameField, "User name should be visible").not.toBeNull();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await assertVisibleByText(page, [/Detalles de la Cuenta/i], "Detalles de la Cuenta header");
    await assertVisibleByText(page, [/Cuenta creada/i], "Cuenta creada");
    await assertVisibleByText(page, [/Estado activo/i], "Estado activo");
    await assertVisibleByText(page, [/Idioma seleccionado/i], "Idioma seleccionado");
  });

  await runStep("Tus Negocios", async () => {
    await assertVisibleByText(page, [/Tus Negocios/i], "Tus Negocios header");
    await assertVisibleByText(page, [/Agregar Negocio/i], "Agregar Negocio button");
    await assertVisibleByText(page, [/Tienes\s*2\s*de\s*3\s*negocios/i], "business quota in list");

    const possibleList = page.locator("ul, table, [role='list'], [role='table']").first();
    const listVisible = await possibleList.isVisible().catch(() => false);
    expect(listVisible, "Business list should be visible").toBeTruthy();
  });

  await runStep("Términos y Condiciones", async () => {
    await clickAndValidateLegalPage({
      appPage: page,
      linkTexts: [/T[eé]rminos y Condiciones/i, /Terminos y Condiciones/i],
      headingTexts: [/T[eé]rminos y Condiciones/i, /Terminos y Condiciones/i],
      reportKey: "Términos y Condiciones",
      screenshotName: "05-terminos-y-condiciones.png",
      legalResults: { urls: legalUrls, testInfo }
    });
  });

  await runStep("Política de Privacidad", async () => {
    await clickAndValidateLegalPage({
      appPage: page,
      linkTexts: [/Pol[ií]tica de Privacidad/i],
      headingTexts: [/Pol[ií]tica de Privacidad/i],
      reportKey: "Política de Privacidad",
      screenshotName: "06-politica-de-privacidad.png",
      legalResults: { urls: legalUrls, testInfo }
    });
  });

  const finalReport = {
    Login: results.Login,
    "Mi Negocio menu": results["Mi Negocio menu"],
    "Agregar Negocio modal": results["Agregar Negocio modal"],
    "Administrar Negocios view": results["Administrar Negocios view"],
    "Información General": results["Información General"],
    "Detalles de la Cuenta": results["Detalles de la Cuenta"],
    "Tus Negocios": results["Tus Negocios"],
    "Términos y Condiciones": results["Términos y Condiciones"],
    "Política de Privacidad": results["Política de Privacidad"]
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify({ finalReport, legalUrls, errors }, null, 2), "utf-8"),
    contentType: "application/json"
  });

  console.log("SaleADS Mi Negocio Final Report:");
  console.table(finalReport);
  console.log("Legal URLs:", legalUrls);

  const failedFields = Object.entries(finalReport)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);

  expect(
    failedFields,
    `One or more validation groups failed: ${failedFields.join(", ")}.\nErrors:\n${errors.join("\n")}`
  ).toEqual([]);
});
