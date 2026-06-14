const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL;

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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function findVisibleClickable(page, label, timeout = 15000) {
  const escaped = escapeRegExp(label);
  const exact = new RegExp(`^\\s*${escaped}\\s*$`, "i");
  const partial = new RegExp(escaped, "i");
  const candidates = [
    page.getByRole("button", { name: exact }).first(),
    page.getByRole("link", { name: exact }).first(),
    page.getByRole("menuitem", { name: exact }).first(),
    page.getByRole("button", { name: partial }).first(),
    page.getByRole("link", { name: partial }).first(),
    page.getByRole("menuitem", { name: partial }).first(),
    page.getByText(exact).first(),
    page.getByText(partial).first()
  ];
  const perCandidateTimeout = Math.max(1000, Math.floor(timeout / candidates.length));

  for (const candidate of candidates) {
    const visible = await candidate
      .waitFor({ state: "visible", timeout: perCandidateTimeout })
      .then(() => true)
      .catch(() => false);
    if (visible) {
      return candidate;
    }
  }

  throw new Error(`No visible clickable element found for text "${label}".`);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const filePath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, { path: filePath, contentType: "image/png" });
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function pickGoogleAccountIfPrompted(page) {
  await waitForUi(page);

  const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  const visible = await accountOption
    .waitFor({ state: "visible", timeout: 6000 })
    .then(() => true)
    .catch(() => false);

  if (visible) {
    await accountOption.click();
    await waitForUi(page);
  }
}

async function openLegalLink(page, context, label, headingRegex, screenshotName, testInfo) {
  const link = await findVisibleClickable(page, label, 15000);
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await link.click();
  const popup = await popupPromise;

  const targetPage = popup || page;
  await waitForUi(targetPage);
  await expect(targetPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible();

  const bodyText = await targetPage.locator("body").innerText();
  if (bodyText.trim().length < 120) {
    throw new Error(`Legal content for "${label}" is unexpectedly short.`);
  }

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  }

  return finalUrl;
}

test("SaleADS.ai Mi Negocio workflow with Google login", async ({ page, context }, testInfo) => {
  const results = new Map();
  const legalUrls = {};

  async function runValidation(field, action) {
    try {
      await action();
      results.set(field, { status: "PASS" });
    } catch (error) {
      results.set(field, {
        status: "FAIL",
        details: error instanceof Error ? error.message : String(error)
      });
      await captureCheckpoint(page, testInfo, `failure-${field.replace(/\s+/g, "-").toLowerCase()}`, true).catch(
        () => {}
      );
    }
  }

  await runValidation("Login", async () => {
    if (!LOGIN_URL) {
      throw new Error("Set SALEADS_LOGIN_URL to the current environment login page.");
    }

    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const googleLogin = await findVisibleClickable(page, "Google", 20000);
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await googleLogin.click();

    const popup = await popupPromise;
    if (popup) {
      await pickGoogleAccountIfPrompted(popup);
      await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
      await page.bringToFront();
    } else {
      await pickGoogleAccountIfPrompted(page);
    }

    await waitForUi(page);
    await expect(page.getByText(/Negocio/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded", true);
  });

  await runValidation("Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible();

    const negocio = await findVisibleClickable(page, "Negocio", 12000);
    await clickAndWait(page, negocio);

    const miNegocio = await findVisibleClickable(page, "Mi Negocio", 12000);
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
  });

  await runValidation("Agregar Negocio modal", async () => {
    const agregarNegocio = await findVisibleClickable(page, "Agregar Negocio", 12000);
    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");

    const nameInput = page.getByLabel(/Nombre del Negocio/i).first();
    await nameInput.fill("Negocio Prueba Automatización");
    const cancelButton = await findVisibleClickable(page, "Cancelar", 12000);
    await clickAndWait(page, cancelButton);
  });

  await runValidation("Administrar Negocios view", async () => {
    let administrar = await findVisibleClickable(page, "Administrar Negocios", 6000).catch(() => null);
    if (!administrar) {
      const miNegocio = await findVisibleClickable(page, "Mi Negocio", 12000);
      await clickAndWait(page, miNegocio);
      administrar = await findVisibleClickable(page, "Administrar Negocios", 12000);
    }

    await clickAndWait(page, administrar);
    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page", true);
  });

  await runValidation("Información General", async () => {
    const infoHeading = page.getByText(/Información General/i).first();
    await expect(infoHeading).toBeVisible();

    const container = infoHeading.locator("xpath=ancestor::*[self::section or self::div][1]").first();
    const sectionText = await container.innerText();

    const hasName = sectionText
      .split("\n")
      .map((line) => line.trim())
      .some(
        (line) =>
          line.length > 2 &&
          !line.includes("@") &&
          !/información general|business plan|cambiar plan/i.test(line)
      );
    if (!hasName) {
      throw new Error("Could not detect visible user name in Información General.");
    }

    const emailPattern = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    if (!emailPattern.test(sectionText)) {
      throw new Error("Could not detect visible user email in Información General.");
    }

    await expect(container.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(container.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    const businessesHeading = page.getByText(/Tus Negocios/i).first();
    await expect(businessesHeading).toBeVisible();

    const businessListVisible =
      (await page.locator("li, tr, [role='row'], [class*='business']").count()) > 0 ||
      (await page.getByText(/Negocio/i).count()) > 1;
    if (!businessListVisible) {
      throw new Error("Business list is not visible in 'Tus Negocios'.");
    }

    const addButton = await findVisibleClickable(page, "Agregar Negocio", 10000);
    await expect(addButton).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runValidation("Términos y Condiciones", async () => {
    legalUrls.terminos = await openLegalLink(
      page,
      context,
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones",
      testInfo
    );
  });

  await runValidation("Política de Privacidad", async () => {
    legalUrls.privacidad = await openLegalLink(
      page,
      context,
      "Política de Privacidad",
      /Política de Privacidad/i,
      "06-politica-de-privacidad",
      testInfo
    );
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    environment: LOGIN_URL || "not-provided",
    account: GOOGLE_ACCOUNT_EMAIL,
    generatedAt: new Date().toISOString(),
    legalUrls,
    results: REPORT_FIELDS.map((field) => ({
      field,
      status: results.get(field)?.status || "FAIL",
      details: results.get(field)?.details || ""
    }))
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedFields = finalReport.results.filter((item) => item.status !== "PASS");
  expect(
    failedFields,
    `Validation failed for: ${failedFields.map((item) => item.field).join(", ")}`
  ).toHaveLength(0);
});
