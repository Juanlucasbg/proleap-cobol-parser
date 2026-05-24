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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
}

async function isVisible(locator) {
  return locator.first().isVisible().catch(() => false);
}

async function clickByVisibleText(page, labels) {
  const candidates = [];

  for (const label of labels) {
    const regex = label instanceof RegExp ? label : new RegExp(`^${escapeRegex(label)}$`, "i");
    candidates.push(page.getByRole("button", { name: regex }));
    candidates.push(page.getByRole("link", { name: regex }));
    candidates.push(page.getByRole("menuitem", { name: regex }));
    candidates.push(page.getByRole("tab", { name: regex }));
    candidates.push(page.getByText(regex));
  }

  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      await candidate.first().click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Could not find any visible element with labels: ${labels.map(String).join(", ")}`);
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function validateLegalDocument({
  page,
  testInfo,
  linkLabel,
  headingRegex,
  screenshotName,
  urls,
}) {
  const originalUrl = page.url();

  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  await clickByVisibleText(page, [linkLabel]);
  await waitForUi(page);
  const popup = await popupPromise;

  const targetPage = popup ?? page;
  await waitForUi(targetPage);

  const headingLocator = targetPage
    .locator("h1, h2, h3")
    .filter({ hasText: headingRegex })
    .first();

  if (await isVisible(headingLocator)) {
    await expect(headingLocator).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingRegex).first()).toBeVisible();
  }

  const legalBodyText = await targetPage.locator("body").innerText();
  if (legalBodyText.trim().length < 200) {
    throw new Error(`Expected substantial legal text for "${linkLabel}", but body text was too short.`);
  }

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);
  urls[linkLabel] = targetPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== originalUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.setTimeout(240000);

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const details = {};
  const urls = {};

  async function runValidation(fieldName, action) {
    try {
      await action();
      report[fieldName] = "PASS";
    } catch (error) {
      details[fieldName] = error instanceof Error ? error.message : String(error);
      await captureCheckpoint(
        page,
        testInfo,
        `${fieldName.toLowerCase().replace(/[^a-z0-9]+/gi, "_")}_failure.png`,
        true,
      ).catch(() => {});
    }
  }

  // Step 1: Login with Google.
  await runValidation("Login", async () => {
    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);

    await clickByVisibleText(page, [
      /sign in with google/i,
      /iniciar sesi[oó]n con google/i,
      /continuar con google/i,
      /acceder con google/i,
      /google/i,
    ]);

    const popup = await popupPromise;
    const accountPage = popup ?? page;

    const accountLocator = accountPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    if (await isVisible(accountLocator)) {
      await accountLocator.click();
      await waitForUi(accountPage);
    }

    if (popup && !popup.isClosed()) {
      await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
    }

    await waitForUi(page);

    await expect(
      page.locator("aside, nav").filter({ hasText: /negocio|mi negocio/i }).first(),
    ).toBeVisible({ timeout: 25000 });
    await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({ timeout: 25000 });

    await captureCheckpoint(page, testInfo, "01_dashboard_loaded.png", true);
  });

  // Step 2: Open Mi Negocio menu.
  await runValidation("Mi Negocio menu", async () => {
    const negocioItem = page.getByText(/^Negocio$/i).first();
    if (await isVisible(negocioItem)) {
      await negocioItem.click();
      await waitForUi(page);
    }

    await clickByVisibleText(page, [/mi negocio/i]);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 15000 });

    await captureCheckpoint(page, testInfo, "02_mi_negocio_menu_expanded.png");
  });

  // Step 3: Validate Agregar Negocio modal.
  await runValidation("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, [/agregar negocio/i]);

    const modal = page.locator('[role="dialog"], .modal, [data-testid*="modal"]').first();
    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible({ timeout: 20000 });
    await expect(modal.getByText(/nombre del negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    const nameInput = modal
      .locator('input[placeholder*="Nombre"], input[name*="nombre"], input[id*="nombre"], input')
      .first();
    if (await isVisible(nameInput)) {
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
    }

    await captureCheckpoint(page, testInfo, "03_agregar_negocio_modal.png");
    await modal.getByRole("button", { name: /cancelar/i }).click();
    await waitForUi(page);
  });

  // Step 4: Open Administrar Negocios view.
  await runValidation("Administrar Negocios view", async () => {
    if (!(await isVisible(page.getByText(/administrar negocios/i).first()))) {
      await clickByVisibleText(page, [/mi negocio/i]);
    }

    await clickByVisibleText(page, [/administrar negocios/i]);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "04_administrar_negocios_view.png", true);
  });

  // Step 5: Validate Información General.
  await runValidation("Información General", async () => {
    const infoSection = page
      .locator("section, div")
      .filter({ hasText: /informaci[oó]n general/i })
      .first();

    await expect(infoSection).toBeVisible({ timeout: 15000 });
    await expect(infoSection.getByText(/@/).first()).toBeVisible();
    await expect(infoSection.getByText(/business plan/i).first()).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const text = await infoSection.innerText();
    if (!text || text.replace(/\s+/g, " ").trim().length < 20) {
      throw new Error("Expected visible user name and profile content in Información General.");
    }
  });

  // Step 6: Validate Detalles de la Cuenta.
  await runValidation("Detalles de la Cuenta", async () => {
    const accountSection = page
      .locator("section, div")
      .filter({ hasText: /detalles de la cuenta/i })
      .first();

    await expect(accountSection).toBeVisible({ timeout: 15000 });
    await expect(accountSection.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(accountSection.getByText(/estado activo/i).first()).toBeVisible();
    await expect(accountSection.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  // Step 7: Validate Tus Negocios.
  await runValidation("Tus Negocios", async () => {
    const businessSection = page
      .locator("section, div")
      .filter({ hasText: /tus negocios/i })
      .first();

    await expect(businessSection).toBeVisible({ timeout: 15000 });
    await expect(businessSection.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(businessSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
  });

  // Step 8: Validate Términos y Condiciones.
  await runValidation("Términos y Condiciones", async () => {
    await validateLegalDocument({
      page,
      testInfo,
      linkLabel: /t[eé]rminos y condiciones/i,
      headingRegex: /t[eé]rminos y condiciones/i,
      screenshotName: "08_terminos_y_condiciones.png",
      urls,
    });
  });

  // Step 9: Validate Política de Privacidad.
  await runValidation("Política de Privacidad", async () => {
    await validateLegalDocument({
      page,
      testInfo,
      linkLabel: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: "09_politica_de_privacidad.png",
      urls,
    });
  });

  // Step 10: Final report.
  const finalReport = {
    report,
    evidence: {
      finalUrls: urls,
    },
    failures: details,
  };

  console.log("SALEADS MI NEGOCIO FINAL REPORT");
  console.table(report);
  if (Object.keys(urls).length > 0) {
    console.log("Legal URLs:", urls);
  }
  if (Object.keys(details).length > 0) {
    console.log("Failure details:", details);
  }

  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json",
  });

  expect(
    REPORT_FIELDS.every((field) => report[field] === "PASS"),
    `One or more validations failed. Final report: ${JSON.stringify(finalReport)}`,
  ).toBeTruthy();
});
