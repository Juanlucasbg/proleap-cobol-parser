const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

const runId = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.join(__dirname, "..", "artifacts", runId);

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: [] }]));
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function findVisibleLocator(page, label, candidates, timeoutMs = 15000) {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      const isVisible = await locator.isVisible().catch(() => false);

      if (isVisible) {
        return locator;
      }
    }

    await page.waitForTimeout(300);
  }

  throw new Error(`No visible element found for: ${label}`);
}

async function saveCheckpoint(page, fileName, fullPage = false) {
  await page.screenshot({
    path: path.join(artifactsDir, fileName),
    fullPage,
  });
}

async function maybeSelectGoogleAccount(page, email) {
  const accountCandidate = await findVisibleLocator(
    page,
    `Google account ${email}`,
    [
      page.getByText(email, { exact: true }),
      page.getByRole("button", { name: new RegExp(email, "i") }),
      page.getByRole("link", { name: new RegExp(email, "i") }),
    ],
    9000,
  ).catch(() => null);

  if (accountCandidate) {
    await clickAndWait(accountCandidate, page);
  }
}

async function ensureMiNegocioExpanded(page) {
  const isExpanded =
    (await page.getByText("Agregar Negocio", { exact: true }).first().isVisible().catch(() => false)) &&
    (await page.getByText("Administrar Negocios", { exact: true }).first().isVisible().catch(() => false));

  if (isExpanded) {
    return;
  }

  const negocio = await findVisibleLocator(page, "Negocio section", [
    page.getByRole("button", { name: /^Negocio$/i }),
    page.getByText(/^Negocio$/i),
  ]);
  await clickAndWait(negocio, page);

  const miNegocio = await findVisibleLocator(page, "Mi Negocio option", [
    page.getByRole("button", { name: /^Mi Negocio$/i }),
    page.getByText(/^Mi Negocio$/i),
  ]);
  await clickAndWait(miNegocio, page);
}

async function validateLegalDocumentAndReturn({
  appPage,
  linkTextRegex,
  headingRegex,
  screenshotName,
}) {
  const legalLink = await findVisibleLocator(appPage, `Legal link ${linkTextRegex}`, [
    appPage.getByRole("link", { name: linkTextRegex }),
    appPage.getByText(linkTextRegex),
  ]);

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await legalLink.click();
  await appPage.waitForTimeout(500);

  let targetPage = await popupPromise;
  const openedInNewTab = Boolean(targetPage);

  if (!targetPage) {
    targetPage = appPage;
  }

  await targetPage.waitForLoadState("domcontentloaded", { timeout: 20000 });

  const legalHeading = await findVisibleLocator(targetPage, "legal heading", [
    targetPage.getByRole("heading", { name: headingRegex }),
    targetPage.getByText(headingRegex),
  ]);
  await expect(legalHeading).toBeVisible();

  const legalBodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(legalBodyText.length).toBeGreaterThan(180);

  await saveCheckpoint(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (openedInNewTab) {
    await targetPage.close();
    await appPage.bringToFront();
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await appPage.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  await fs.mkdir(artifactsDir, { recursive: true });

  const report = createReport();
  const failures = [];
  const persistReport = async () => {
    const reportPath = path.join(artifactsDir, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: reportPath,
      contentType: "application/json",
    });
  };

  const runStep = async (field, execution) => {
    try {
      await execution();
      report[field].status = "PASS";
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[field].status = "FAIL";
      report[field].details.push(message);
      failures.push(`${field}: ${message}`);
      await saveCheckpoint(page, `${field.replace(/\s+/g, "_").toLowerCase()}_failure.png`, true).catch(() => {});
    }
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    const message =
      "Missing SALEADS_LOGIN_URL (or SALEADS_BASE_URL). Provide the login page URL for the target environment.";
    report.Login.status = "FAIL";
    report.Login.details.push(message);
    failures.push(`Login: ${message}`);
    await persistReport();
    throw new Error(message);
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });

  await runStep("Login", async () => {
    const googleLoginButton = await findVisibleLocator(page, "Google login button", [
      page.getByRole("button", { name: /sign in with google|iniciar sesión con google|continuar con google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 9000 }).catch(() => null);
    await clickAndWait(googleLoginButton, page);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
      await maybeSelectGoogleAccount(popup, GOOGLE_ACCOUNT_EMAIL);
      await popup.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
    } else {
      await maybeSelectGoogleAccount(page, GOOGLE_ACCOUNT_EMAIL);
    }

    const sidebar = await findVisibleLocator(page, "Left sidebar navigation", [
      page.getByRole("navigation"),
      page.locator("aside"),
    ], 45000);
    await expect(sidebar).toBeVisible();

    await saveCheckpoint(page, "01_dashboard_loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await findVisibleLocator(page, "Negocio section", [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    await clickAndWait(negocioSection, page);

    const miNegocioOption = await findVisibleLocator(page, "Mi Negocio option", [
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();

    await saveCheckpoint(page, "02_mi_negocio_menu_expanded.png", true);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await findVisibleLocator(page, "Agregar Negocio option", [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await clickAndWait(agregarNegocio, page);

    const modalTitle = page.getByRole("heading", { name: /Crear Nuevo Negocio/i });
    await expect(modalTitle).toBeVisible();

    const businessNameField = await findVisibleLocator(page, "Nombre del Negocio input", [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByRole("textbox", { name: /Nombre del Negocio/i }),
      page.getByPlaceholder(/Nombre del Negocio/i),
    ]);

    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    await saveCheckpoint(page, "03_agregar_negocio_modal.png", true);

    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatización");

    const cancelButton = await findVisibleLocator(page, "Cancelar button", [
      page.getByRole("button", { name: /^Cancelar$/i }),
      page.getByText(/^Cancelar$/i),
    ]);
    await clickAndWait(cancelButton, page);
    await expect(modalTitle).toBeHidden({ timeout: 12000 });
  });

  await runStep("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);

    const administrarNegocios = await findVisibleLocator(page, "Administrar Negocios option", [
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByRole("heading", { name: /Información General/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Detalles de la Cuenta/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Tus Negocios/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Sección Legal/i })).toBeVisible();

    await saveCheckpoint(page, "04_administrar_negocios_page.png", true);
  });

  await runStep("Información General", async () => {
    const infoSectionHeading = page.getByRole("heading", { name: /Información General/i });
    await expect(infoSectionHeading).toBeVisible();

    const infoSectionContainer = infoSectionHeading.locator("xpath=ancestor::section[1]");
    const infoSectionText = (await infoSectionContainer.innerText()).replace(/\s+/g, " ").trim();

    expect(infoSectionText).toMatch(/BUSINESS PLAN/i);
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    expect(infoSectionText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    expect(infoSectionText).toMatch(/Nombre|Usuario|User/i);
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSectionHeading = page.getByRole("heading", { name: /Detalles de la Cuenta/i });
    await expect(detailsSectionHeading).toBeVisible();

    const detailsSectionContainer = detailsSectionHeading.locator("xpath=ancestor::section[1]");
    const detailsText = (await detailsSectionContainer.innerText()).replace(/\s+/g, " ").trim();

    expect(detailsText).toMatch(/Cuenta creada/i);
    expect(detailsText).toMatch(/Estado activo/i);
    expect(detailsText).toMatch(/Idioma seleccionado/i);
  });

  await runStep("Tus Negocios", async () => {
    const businessesSectionHeading = page.getByRole("heading", { name: /Tus Negocios/i });
    await expect(businessesSectionHeading).toBeVisible();

    const businessesSection = businessesSectionHeading.locator("xpath=ancestor::section[1]");
    const businessesText = (await businessesSection.innerText()).replace(/\s+/g, " ").trim();

    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    expect(businessesText).toMatch(/Tienes 2 de 3 negocios/i);
    expect(businessesText.length).toBeGreaterThan(60);
  });

  await runStep("Términos y Condiciones", async () => {
    const finalUrl = await validateLegalDocumentAndReturn({
      appPage: page,
      linkTextRegex: /Términos y Condiciones/i,
      headingRegex: /Términos y Condiciones/i,
      screenshotName: "05_terminos_y_condiciones.png",
    });
    report["Términos y Condiciones"].details.push(`Final URL: ${finalUrl}`);
  });

  await runStep("Política de Privacidad", async () => {
    const finalUrl = await validateLegalDocumentAndReturn({
      appPage: page,
      linkTextRegex: /Política de Privacidad/i,
      headingRegex: /Política de Privacidad/i,
      screenshotName: "06_politica_de_privacidad.png",
    });
    report["Política de Privacidad"].details.push(`Final URL: ${finalUrl}`);
  });

  await persistReport();

  if (failures.length > 0) {
    throw new Error(`Workflow validation failed:\n${failures.join("\n")}`);
  }
});
