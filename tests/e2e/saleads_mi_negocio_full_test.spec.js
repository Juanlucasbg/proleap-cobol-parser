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
  "Política de Privacidad",
];

function buildReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {});
}

function reportToText(report) {
  return REPORT_FIELDS.map((field) => `${field}: ${report[field]}`).join("\n");
}

async function clickAndWaitForUi(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(700);
}

async function clickFirstVisible(page, candidates) {
  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      await clickAndWaitForUi(page, locator.first());
      return locator.first();
    }
  }

  throw new Error("No visible candidate matched the expected action.");
}

function sectionByHeading(page, headingName) {
  const heading = page.getByRole("heading", { name: new RegExp(headingName, "i") }).first();
  return heading.locator("xpath=ancestor::*[self::section or self::div][1]");
}

async function validateLegalLink({
  page,
  testInfo,
  linkText,
  expectedHeading,
  screenshotName,
  report,
  reportKey,
  errors,
}) {
  try {
    const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    const link = page.getByRole("link", { name: new RegExp(linkText, "i") }).first();

    await clickAndWaitForUi(page, link);
    const popup = await popupPromise;

    let legalPage = page;
    if (popup) {
      legalPage = popup;
      await legalPage.waitForLoadState("domcontentloaded");
    } else {
      await legalPage.waitForLoadState("domcontentloaded").catch(() => {});
    }

    await expect(
      legalPage.getByRole("heading", { name: new RegExp(expectedHeading, "i") }).first()
    ).toBeVisible();

    const legalBody = await legalPage.locator("body").innerText();
    expect(legalBody.trim().length).toBeGreaterThan(100);

    await legalPage.screenshot({ path: testInfo.outputPath(screenshotName), fullPage: true });
    await testInfo.attach(`${reportKey}-final-url`, {
      body: Buffer.from(legalPage.url(), "utf-8"),
      contentType: "text/plain",
    });

    report[reportKey] = "PASS";

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else {
      await page.goBack().catch(() => {});
      await page.waitForLoadState("domcontentloaded").catch(() => {});
    }
  } catch (error) {
    report[reportKey] = "FAIL";
    errors.push(`${reportKey}: ${error.message}`);
    await page.screenshot({
      path: testInfo.outputPath(`${reportKey.toLowerCase().replaceAll(" ", "_")}_failed.png`),
      fullPage: true,
    });
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
  const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
  const report = buildReport();
  const errors = [];

  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL or BASE_URL with the current environment login page URL."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });

  // Step 1: Login with Google.
  try {
    await clickFirstVisible(page, [
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesión con google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesión con google/i),
    ]);

    const googleAccountOption = page.getByText(googleAccount).first();
    if (await googleAccountOption.isVisible().catch(() => false)) {
      await clickAndWaitForUi(page, googleAccountOption);
    }

    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 60000 });
    await page.screenshot({ path: testInfo.outputPath("01_dashboard_loaded.png"), fullPage: true });
    report["Login"] = "PASS";
  } catch (error) {
    report["Login"] = "FAIL";
    errors.push(`Login: ${error.message}`);
  }

  // Step 2: Open Mi Negocio menu.
  try {
    await clickFirstVisible(page, [
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i),
    ]);

    await expect(page.getByText(/^agregar negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^administrar negocios$/i).first()).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath("02_mi_negocio_menu_expanded.png"), fullPage: true });
    report["Mi Negocio menu"] = "PASS";
  } catch (error) {
    report["Mi Negocio menu"] = "FAIL";
    errors.push(`Mi Negocio menu: ${error.message}`);
  }

  // Step 3: Validate Agregar Negocio modal.
  try {
    await clickAndWaitForUi(page, page.getByText(/^agregar negocio$/i).first());

    await expect(page.getByRole("heading", { name: /crear nuevo negocio/i })).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWaitForUi(page, page.getByRole("button", { name: /cancelar/i }).first());

    await page.screenshot({ path: testInfo.outputPath("03_agregar_negocio_modal.png"), fullPage: true });
    report["Agregar Negocio modal"] = "PASS";
  } catch (error) {
    report["Agregar Negocio modal"] = "FAIL";
    errors.push(`Agregar Negocio modal: ${error.message}`);
  }

  // Step 4: Open Administrar Negocios and validate main sections.
  try {
    const administrarNegocios = page.getByText(/^administrar negocios$/i).first();
    if (!(await administrarNegocios.isVisible().catch(() => false))) {
      await clickFirstVisible(page, [
        page.getByRole("button", { name: /^mi negocio$/i }),
        page.getByRole("link", { name: /^mi negocio$/i }),
        page.getByText(/^mi negocio$/i),
      ]);
    }

    await clickAndWaitForUi(page, page.getByText(/^administrar negocios$/i).first());
    await expect(page.getByRole("heading", { name: /información general/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /detalles de la cuenta/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /tus negocios/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /sección legal/i })).toBeVisible();

    await page.screenshot({ path: testInfo.outputPath("04_administrar_negocios_view.png"), fullPage: true });
    report["Administrar Negocios view"] = "PASS";
  } catch (error) {
    report["Administrar Negocios view"] = "FAIL";
    errors.push(`Administrar Negocios view: ${error.message}`);
  }

  // Step 5: Validate Información General.
  try {
    const infoGeneralSection = sectionByHeading(page, "Información General");
    const sectionText = await infoGeneralSection.innerText();

    expect(sectionText).toContain("BUSINESS PLAN");
    await expect(infoGeneralSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    expect(sectionText).toMatch(/[^\s]{2,}/);
    expect(sectionText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);

    report["Información General"] = "PASS";
  } catch (error) {
    report["Información General"] = "FAIL";
    errors.push(`Información General: ${error.message}`);
  }

  // Step 6: Validate Detalles de la Cuenta.
  try {
    const detallesSection = sectionByHeading(page, "Detalles de la Cuenta");
    const detallesText = await detallesSection.innerText();

    expect(detallesText).toMatch(/cuenta creada/i);
    expect(detallesText).toMatch(/estado activo/i);
    expect(detallesText).toMatch(/idioma seleccionado/i);

    report["Detalles de la Cuenta"] = "PASS";
  } catch (error) {
    report["Detalles de la Cuenta"] = "FAIL";
    errors.push(`Detalles de la Cuenta: ${error.message}`);
  }

  // Step 7: Validate Tus Negocios.
  try {
    const negociosSection = sectionByHeading(page, "Tus Negocios");
    const negociosText = await negociosSection.innerText();

    await expect(negociosSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    expect(negociosText).toMatch(/tienes 2 de 3 negocios/i);
    expect(negociosText.trim().length).toBeGreaterThan(30);

    report["Tus Negocios"] = "PASS";
  } catch (error) {
    report["Tus Negocios"] = "FAIL";
    errors.push(`Tus Negocios: ${error.message}`);
  }

  // Steps 8 and 9: Legal links validation.
  await validateLegalLink({
    page,
    testInfo,
    linkText: "Términos y Condiciones",
    expectedHeading: "Términos y Condiciones",
    screenshotName: "05_terminos_y_condiciones.png",
    report,
    reportKey: "Términos y Condiciones",
    errors,
  });

  await validateLegalLink({
    page,
    testInfo,
    linkText: "Política de Privacidad",
    expectedHeading: "Política de Privacidad",
    screenshotName: "06_politica_de_privacidad.png",
    report,
    reportKey: "Política de Privacidad",
    errors,
  });

  const finalReport = reportToText(report);
  await testInfo.attach("final-report", {
    body: Buffer.from(finalReport, "utf-8"),
    contentType: "text/plain",
  });
  console.log(`\nFinal Report:\n${finalReport}\n`);

  expect(errors, `One or more workflow steps failed.\n${finalReport}`).toEqual([]);
});
