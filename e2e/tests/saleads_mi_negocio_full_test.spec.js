const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

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

function normalizeText(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/\s+/g, " ")
    .trim();
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    const count = await locator.count();
    if (count > 0 && (await locator.first().isVisible())) {
      return locator.first();
    }
  }

  return null;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToSettle(page);
}

async function takeCheckpoint(testInfo, page, evidenceDir, name, fullPage = false) {
  const safe = name.replace(/\s+/g, "_").toLowerCase();
  const filePath = path.join(evidenceDir, `${safe}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, { path: filePath, contentType: "image/png" });
}

async function ensureGoogleAccountSelection(page) {
  const accountLocator = await firstVisibleLocator([
    page.getByText(ACCOUNT_EMAIL, { exact: true }),
    page.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
    page.locator(`[data-email="${ACCOUNT_EMAIL}"]`)
  ]);

  if (accountLocator) {
    await clickAndWait(page, accountLocator);
  }
}

async function findByVisibleText(page, label) {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const looseRegex = new RegExp(escaped, "i");

  return firstVisibleLocator([
    page.getByRole("button", { name: looseRegex }),
    page.getByRole("link", { name: looseRegex }),
    page.getByRole("menuitem", { name: looseRegex }),
    page.getByRole("tab", { name: looseRegex }),
    page.getByText(looseRegex)
  ]);
}

async function openLegalLinkAndValidate({
  appPage,
  browserContext,
  linkText,
  headingRegex,
  testInfo,
  evidenceDir,
  screenshotName
}) {
  const link = await findByVisibleText(appPage, linkText);
  expect(link, `No se encontró el enlace legal: ${linkText}`).toBeTruthy();

  const appUrlBefore = appPage.url();
  const popupPromise = browserContext
    .waitForEvent("page", { timeout: 12000 })
    .catch(() => null);

  await clickAndWait(appPage, link);
  let legalPage = await popupPromise;
  let openedNewTab = Boolean(legalPage);

  if (!legalPage) {
    legalPage = appPage;
    await waitForUiToSettle(legalPage);
  } else {
    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUiToSettle(legalPage);
  }

  const heading = await firstVisibleLocator([
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex)
  ]);
  expect(heading, `No se encontró el encabezado legal esperado (${linkText}).`).toBeTruthy();

  const bodyText = await legalPage.locator("body").innerText();
  expect(
    bodyText.replace(/\s+/g, " ").trim().length,
    "No se detectó contenido legal suficiente."
  ).toBeGreaterThan(120);

  await takeCheckpoint(testInfo, legalPage, evidenceDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (openedNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
  } else if (appPage.url() !== appUrlBefore) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToSettle(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const evidenceDir = path.join(testInfo.outputDir, "checkpoints");
  fs.mkdirSync(evidenceDir, { recursive: true });

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  const legalUrls = {
    "Términos y Condiciones": "",
    "Política de Privacidad": ""
  };

  const startUrl = process.env.SALEADS_START_URL;
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  }

  async function runValidation(field, callback) {
    try {
      await callback();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      failures.push(`${field}: ${error.message}`);
    }
  }

  await runValidation("Login", async () => {
    if (page.url() === "about:blank") {
      throw new Error(
        "La prueba inició en about:blank. Abra el login de SaleADS o pase SALEADS_START_URL."
      );
    }

    const loginTrigger = await firstVisibleLocator([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|continuar con google|iniciar con google/i)
    ]);

    expect(loginTrigger, "No se encontró botón de login con Google.").toBeTruthy();

    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickAndWait(page, loginTrigger);
    const authPopup = await popupPromise;

    if (authPopup) {
      await authPopup.waitForLoadState("domcontentloaded");
      await waitForUiToSettle(authPopup);
      await ensureGoogleAccountSelection(authPopup);
      await authPopup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
      await page.bringToFront();
    } else {
      await ensureGoogleAccountSelection(page);
    }

    await waitForUiToSettle(page);

    const sidebar = await firstVisibleLocator([
      page.getByRole("navigation"),
      page.locator("aside"),
      page.locator('[class*="sidebar"]')
    ]);
    expect(sidebar, "No se detectó la barra lateral principal tras login.").toBeTruthy();
    await takeCheckpoint(testInfo, page, evidenceDir, "01_dashboard_loaded");
  });

  await runValidation("Mi Negocio menu", async () => {
    const negocio = await findByVisibleText(page, "Negocio");
    expect(negocio, "No se encontró la sección 'Negocio' en la barra lateral.").toBeTruthy();
    await clickAndWait(page, negocio);

    const miNegocio = await findByVisibleText(page, "Mi Negocio");
    expect(miNegocio, "No se encontró la opción 'Mi Negocio'.").toBeTruthy();
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();
    await takeCheckpoint(testInfo, page, evidenceDir, "02_mi_negocio_menu_expanded");
  });

  await runValidation("Agregar Negocio modal", async () => {
    const addBusiness = await findByVisibleText(page, "Agregar Negocio");
    expect(addBusiness, "No se encontró 'Agregar Negocio' en el menú.").toBeTruthy();
    await clickAndWait(page, addBusiness);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await takeCheckpoint(testInfo, page, evidenceDir, "03_agregar_negocio_modal");

    const businessNameInput = page.getByLabel(/Nombre del Negocio/i);
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");

    await clickAndWait(page, page.getByRole("button", { name: /Cancelar/i }));
  });

  await runValidation("Administrar Negocios view", async () => {
    const adminOptionVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!adminOptionVisible) {
      const miNegocio = await findByVisibleText(page, "Mi Negocio");
      if (miNegocio) {
        await clickAndWait(page, miNegocio);
      }
    }

    const manageBusinesses = await findByVisibleText(page, "Administrar Negocios");
    expect(manageBusinesses, "No se encontró la opción 'Administrar Negocios'.").toBeTruthy();
    await clickAndWait(page, manageBusinesses);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();
    await takeCheckpoint(testInfo, page, evidenceDir, "04_administrar_negocios_view", true);
  });

  await runValidation("Información General", async () => {
    const bodyText = normalizeText(await page.locator("body").innerText());
    expect(bodyText.includes(normalizeText(ACCOUNT_EMAIL))).toBeTruthy();
    expect(bodyText.includes("business plan")).toBeTruthy();

    const changePlanButton = await findByVisibleText(page, "Cambiar Plan");
    expect(changePlanButton, "No se encontró botón 'Cambiar Plan'.").toBeTruthy();

    const hasLikelyUserName = /[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+/.test(
      await page.locator("body").innerText()
    );
    expect(hasLikelyUserName, "No se detectó un nombre de usuario visible.").toBeTruthy();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const visibleBusinessCards = await page
      .locator('[class*="business"], [class*="negocio"], table, [role="row"]')
      .count();
    expect(visibleBusinessCards, "No se detectó listado visible de negocios.").toBeGreaterThan(0);
  });

  await runValidation("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await openLegalLinkAndValidate({
      appPage: page,
      browserContext: context,
      linkText: "Términos y Condiciones",
      headingRegex: /Términos y Condiciones/i,
      testInfo,
      evidenceDir,
      screenshotName: "05_terminos_y_condiciones"
    });
  });

  await runValidation("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await openLegalLinkAndValidate({
      appPage: page,
      browserContext: context,
      linkText: "Política de Privacidad",
      headingRegex: /Política de Privacidad/i,
      testInfo,
      evidenceDir,
      screenshotName: "06_politica_de_privacidad"
    });
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls,
    failureDetails: failures
  };

  const reportPath = path.join(testInfo.outputDir, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log("Final Report (PASS/FAIL):");
  for (const field of REPORT_FIELDS) {
    console.log(`- ${field}: ${report[field]}`);
  }
  console.log(`- URL Términos y Condiciones: ${legalUrls["Términos y Condiciones"] || "N/A"}`);
  console.log(`- URL Política de Privacidad: ${legalUrls["Política de Privacidad"] || "N/A"}`);

  expect(
    failures,
    `Validaciones fallidas:\n${failures.join("\n") || "Ninguna"}`
  ).toEqual([]);
});
