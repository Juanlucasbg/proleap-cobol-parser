const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

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

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function isVisible(locator, timeout = 5000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function firstVisibleLocator(factories, timeout = 3000) {
  for (const createLocator of factories) {
    const locator = createLocator().first();
    if (await isVisible(locator, timeout)) {
      return locator;
    }
  }
  return null;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 10000 }),
    locator.click(),
  ]);
  await waitForUi(page);
}

function createReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        status: "FAIL",
        validations: [],
        evidence: [],
      },
    ]),
  );
}

function recordValidation(report, section, label, pass, details = "") {
  report[section].validations.push({
    label,
    pass,
    details,
  });
}

function addEvidence(report, section, evidencePath) {
  report[section].evidence.push(evidencePath);
}

function finalizeReport(report) {
  for (const section of REPORT_FIELDS) {
    const validations = report[section].validations;
    report[section].status =
      validations.length > 0 && validations.every((validation) => validation.pass)
        ? "PASS"
        : "FAIL";
  }
  return report;
}

async function captureCheckpoint(page, artifactDir, testInfo, name, fullPage = false) {
  const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const filePath = path.join(artifactDir, `${Date.now()}-${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, { path: filePath, contentType: "image/png" });
  return filePath;
}

async function hasLegalContent(page) {
  const content = await page.locator("body").innerText().catch(() => "");
  return content.replace(/\s+/g, " ").trim().length > 250;
}

async function hasLikelyName(page) {
  const content = await page.locator("body").innerText().catch(() => "");
  const candidates =
    content.match(/\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,})+\b/g) || [];
  const filtered = candidates.filter(
    (name) =>
      !/Información General|Detalles de la Cuenta|Tus Negocios|Sección Legal|BUSINESS PLAN|Cambiar Plan/i.test(
        name,
      ),
  );
  return filtered.length > 0;
}

async function validateLegalLink({
  page,
  context,
  report,
  reportKey,
  linkText,
  headingRegex,
  artifactDir,
  testInfo,
}) {
  const linkLocator = await firstVisibleLocator([
    () => page.getByRole("link", { name: new RegExp(`^${escapeRegex(linkText)}$`, "i") }),
    () => page.getByRole("button", { name: new RegExp(`^${escapeRegex(linkText)}$`, "i") }),
    () => page.getByText(new RegExp(`^${escapeRegex(linkText)}$`, "i")),
  ]);

  if (!linkLocator) {
    recordValidation(report, reportKey, `'${linkText}' action is visible`, false);
    return;
  }

  recordValidation(report, reportKey, `'${linkText}' action is visible`, true);

  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await clickAndWait(page, linkLocator);
  const popupPage = await popupPromise;
  const targetPage = popupPage || page;

  await waitForUi(targetPage);

  const headingLocator = await firstVisibleLocator(
    [
      () => targetPage.getByRole("heading", { name: headingRegex }),
      () => targetPage.getByText(headingRegex),
    ],
    10000,
  );

  recordValidation(
    report,
    reportKey,
    `Heading '${linkText}' is visible`,
    Boolean(headingLocator),
    targetPage.url(),
  );

  const legalContentVisible = await hasLegalContent(targetPage);
  recordValidation(
    report,
    reportKey,
    "Legal content text is visible",
    legalContentVisible,
    targetPage.url(),
  );

  const legalScreenshot = await captureCheckpoint(
    targetPage,
    artifactDir,
    testInfo,
    `${reportKey}-page`,
    true,
  );
  addEvidence(report, reportKey, legalScreenshot);

  report[reportKey].finalUrl = targetPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactDir = path.join(process.cwd(), "artifacts", "saleads-mi-negocio", runId);
  fs.mkdirSync(artifactDir, { recursive: true });

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    recordValidation(report, "Login", "Login page is reachable", true, loginUrl);
  } else {
    recordValidation(
      report,
      "Login",
      "Login page is reachable",
      page.url() !== "about:blank",
      "Set SALEADS_LOGIN_URL (or SALEADS_URL) when running in a fresh browser context.",
    );
  }

  const googleButton = await firstVisibleLocator(
    [
      () =>
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
      () =>
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
      () =>
        page.getByText(
          /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        ),
    ],
    10000,
  );

  recordValidation(report, "Login", "Google sign-in action is visible", Boolean(googleButton));

  let googlePopup = null;
  if (googleButton) {
    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickAndWait(page, googleButton);
    googlePopup = await popupPromise;
  }

  const googlePage = googlePopup || (page.url().includes("accounts.google.com") ? page : null);
  if (googlePage) {
    await waitForUi(googlePage);
    const accountLocator = await firstVisibleLocator([
      () => googlePage.getByText(new RegExp(`^${escapeRegex(GOOGLE_ACCOUNT_EMAIL)}$`, "i")),
      () => googlePage.getByRole("button", { name: new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i") }),
      () => googlePage.locator(`[data-email="${GOOGLE_ACCOUNT_EMAIL}"]`),
      () => googlePage.getByRole("link", { name: new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i") }),
    ]);

    recordValidation(
      report,
      "Login",
      `Google account option '${GOOGLE_ACCOUNT_EMAIL}' is visible`,
      Boolean(accountLocator),
    );

    if (accountLocator) {
      await clickAndWait(googlePage, accountLocator);
    }
  } else {
    recordValidation(
      report,
      "Login",
      "Google account selector handled (or not required)",
      true,
      "No explicit Google account picker was detected.",
    );
  }

  if (googlePopup) {
    await page.bringToFront();
  }

  await page.waitForURL((url) => !url.includes("accounts.google.com"), { timeout: 60000 }).catch(() => {});
  await waitForUi(page);

  const sidebarVisible = await isVisible(page.locator("aside"), 12000);
  recordValidation(report, "Login", "Main application interface appears", sidebarVisible);
  recordValidation(report, "Login", "Left sidebar navigation is visible", sidebarVisible);

  if (sidebarVisible) {
    const dashboardShot = await captureCheckpoint(page, artifactDir, testInfo, "dashboard-loaded", true);
    addEvidence(report, "Login", dashboardShot);
  }

  const miNegocioMenu = await firstVisibleLocator([
    () => page.getByRole("link", { name: /mi negocio/i }),
    () => page.getByRole("button", { name: /mi negocio/i }),
    () => page.getByText(/^Mi Negocio$/i),
  ]);

  recordValidation(report, "Mi Negocio menu", "'Mi Negocio' option is visible", Boolean(miNegocioMenu));

  if (miNegocioMenu) {
    await clickAndWait(page, miNegocioMenu);
  }

  const agregarNegocioMenu = await firstVisibleLocator([
    () => page.getByRole("link", { name: /^Agregar Negocio$/i }),
    () => page.getByRole("button", { name: /^Agregar Negocio$/i }),
    () => page.getByText(/^Agregar Negocio$/i),
  ]);

  const administrarNegociosMenu = await firstVisibleLocator([
    () => page.getByRole("link", { name: /^Administrar Negocios$/i }),
    () => page.getByRole("button", { name: /^Administrar Negocios$/i }),
    () => page.getByText(/^Administrar Negocios$/i),
  ]);

  recordValidation(report, "Mi Negocio menu", "Submenu expands", Boolean(agregarNegocioMenu || administrarNegociosMenu));
  recordValidation(report, "Mi Negocio menu", "'Agregar Negocio' is visible", Boolean(agregarNegocioMenu));
  recordValidation(
    report,
    "Mi Negocio menu",
    "'Administrar Negocios' is visible",
    Boolean(administrarNegociosMenu),
  );

  if (agregarNegocioMenu || administrarNegociosMenu) {
    const expandedMenuShot = await captureCheckpoint(page, artifactDir, testInfo, "mi-negocio-expanded-menu");
    addEvidence(report, "Mi Negocio menu", expandedMenuShot);
  }

  if (agregarNegocioMenu) {
    await clickAndWait(page, agregarNegocioMenu);
  }

  const modalTitle = page.getByText(/^Crear Nuevo Negocio$/i);
  const modalVisible = await isVisible(modalTitle, 10000);
  recordValidation(report, "Agregar Negocio modal", "Modal title 'Crear Nuevo Negocio' is visible", modalVisible);

  const nombreInput = await firstVisibleLocator([
    () => page.getByLabel(/Nombre del Negocio/i),
    () => page.getByPlaceholder(/Nombre del Negocio/i),
    () => page.locator("input[name*='nombre' i], input[placeholder*='Nombre' i]"),
  ]);
  recordValidation(report, "Agregar Negocio modal", "Input field 'Nombre del Negocio' exists", Boolean(nombreInput));

  const negociosLimitTextVisible = await isVisible(page.getByText(/Tienes 2 de 3 negocios/i), 8000);
  recordValidation(
    report,
    "Agregar Negocio modal",
    "Text 'Tienes 2 de 3 negocios' is visible",
    negociosLimitTextVisible,
  );

  const cancelarButton = await firstVisibleLocator([
    () => page.getByRole("button", { name: /^Cancelar$/i }),
    () => page.getByText(/^Cancelar$/i),
  ]);

  const crearNegocioButton = await firstVisibleLocator([
    () => page.getByRole("button", { name: /^Crear Negocio$/i }),
    () => page.getByText(/^Crear Negocio$/i),
  ]);

  recordValidation(report, "Agregar Negocio modal", "Button 'Cancelar' is present", Boolean(cancelarButton));
  recordValidation(report, "Agregar Negocio modal", "Button 'Crear Negocio' is present", Boolean(crearNegocioButton));

  if (modalVisible) {
    const modalShot = await captureCheckpoint(page, artifactDir, testInfo, "agregar-negocio-modal");
    addEvidence(report, "Agregar Negocio modal", modalShot);
  }

  if (nombreInput && cancelarButton) {
    await nombreInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, cancelarButton);
  }

  let administrarOption = administrarNegociosMenu;
  if (!administrarOption && miNegocioMenu) {
    await clickAndWait(page, miNegocioMenu);
    administrarOption = await firstVisibleLocator([
      () => page.getByRole("link", { name: /^Administrar Negocios$/i }),
      () => page.getByRole("button", { name: /^Administrar Negocios$/i }),
      () => page.getByText(/^Administrar Negocios$/i),
    ]);
  }

  recordValidation(
    report,
    "Administrar Negocios view",
    "'Administrar Negocios' action is visible",
    Boolean(administrarOption),
  );

  if (administrarOption) {
    await clickAndWait(page, administrarOption);
  }

  const infoGeneralVisible = await isVisible(page.getByText(/^Información General$/i), 12000);
  const detallesCuentaVisible = await isVisible(page.getByText(/^Detalles de la Cuenta$/i), 12000);
  const tusNegociosVisible = await isVisible(page.getByText(/^Tus Negocios$/i), 12000);
  const seccionLegalVisible = await isVisible(page.getByText(/^Sección Legal$/i), 12000);

  recordValidation(report, "Administrar Negocios view", "Section 'Información General' exists", infoGeneralVisible);
  recordValidation(
    report,
    "Administrar Negocios view",
    "Section 'Detalles de la Cuenta' exists",
    detallesCuentaVisible,
  );
  recordValidation(report, "Administrar Negocios view", "Section 'Tus Negocios' exists", tusNegociosVisible);
  recordValidation(report, "Administrar Negocios view", "Section 'Sección Legal' exists", seccionLegalVisible);

  if (infoGeneralVisible || detallesCuentaVisible || tusNegociosVisible || seccionLegalVisible) {
    const accountPageShot = await captureCheckpoint(
      page,
      artifactDir,
      testInfo,
      "administrar-negocios-account-page",
      true,
    );
    addEvidence(report, "Administrar Negocios view", accountPageShot);
  }

  const userNameVisible = await hasLikelyName(page);
  const userEmailVisible = await isVisible(
    page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
    6000,
  );
  const businessPlanVisible = await isVisible(page.getByText(/BUSINESS PLAN/i), 6000);
  const cambiarPlanVisible = await isVisible(page.getByRole("button", { name: /Cambiar Plan/i }), 6000);

  recordValidation(report, "Información General", "User name is visible", userNameVisible);
  recordValidation(report, "Información General", "User email is visible", userEmailVisible);
  recordValidation(report, "Información General", "Text 'BUSINESS PLAN' is visible", businessPlanVisible);
  recordValidation(report, "Información General", "Button 'Cambiar Plan' is visible", cambiarPlanVisible);

  const cuentaCreadaVisible = await isVisible(page.getByText(/Cuenta creada/i), 6000);
  const estadoActivoVisible = await isVisible(page.getByText(/Estado activo/i), 6000);
  const idiomaSeleccionadoVisible = await isVisible(page.getByText(/Idioma seleccionado/i), 6000);

  recordValidation(report, "Detalles de la Cuenta", "'Cuenta creada' is visible", cuentaCreadaVisible);
  recordValidation(report, "Detalles de la Cuenta", "'Estado activo' is visible", estadoActivoVisible);
  recordValidation(
    report,
    "Detalles de la Cuenta",
    "'Idioma seleccionado' is visible",
    idiomaSeleccionadoVisible,
  );

  const businessListCount = await page.locator("table tbody tr, [role='listitem'], [class*='business']").count();
  const businessListVisible = businessListCount > 0;
  const agregarNegocioButtonVisible = await isVisible(page.getByRole("button", { name: /^Agregar Negocio$/i }), 6000);
  const negociosQuotaVisible = await isVisible(page.getByText(/Tienes 2 de 3 negocios/i), 6000);

  recordValidation(report, "Tus Negocios", "Business list is visible", businessListVisible);
  recordValidation(report, "Tus Negocios", "Button 'Agregar Negocio' exists", agregarNegocioButtonVisible);
  recordValidation(report, "Tus Negocios", "Text 'Tienes 2 de 3 negocios' is visible", negociosQuotaVisible);

  await validateLegalLink({
    page,
    context,
    report,
    reportKey: "Términos y Condiciones",
    linkText: "Términos y Condiciones",
    headingRegex: /Términos y Condiciones/i,
    artifactDir,
    testInfo,
  });

  await validateLegalLink({
    page,
    context,
    report,
    reportKey: "Política de Privacidad",
    linkText: "Política de Privacidad",
    headingRegex: /Política de Privacidad/i,
    artifactDir,
    testInfo,
  });

  const finalReport = finalizeReport(report);
  const reportPath = path.join(artifactDir, "final-report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf-8");
  await testInfo.attach("final-report-json", { path: reportPath, contentType: "application/json" });

  const summaryTable = REPORT_FIELDS.map((field) => ({
    section: field,
    status: finalReport[field].status,
  }));
  console.table(summaryTable);
  console.log(`Final report written to: ${reportPath}`);

  const failedSections = REPORT_FIELDS.filter((field) => finalReport[field].status === "FAIL");
  expect(
    failedSections,
    `One or more workflow sections failed. Check the JSON report at ${reportPath}.`,
  ).toEqual([]);
});
