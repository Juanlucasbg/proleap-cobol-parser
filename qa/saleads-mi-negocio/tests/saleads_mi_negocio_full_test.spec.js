const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const SCREENSHOT_DIR = path.resolve(__dirname, "..", "artifacts", "screenshots");
const REPORT_DIR = path.resolve(__dirname, "..", "artifacts", "reports");
const REPORT_PATH = path.join(REPORT_DIR, `${TEST_NAME}.report.json`);

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

function createInitialReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        status: "FAIL",
        details: [],
      },
    ]),
  );
}

async function ensureArtifactsDirs() {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
  await fs.mkdir(REPORT_DIR, { recursive: true });
}

async function saveReport(report, metadata = {}) {
  const payload = {
    testName: TEST_NAME,
    generatedAt: new Date().toISOString(),
    metadata,
    results: report,
  };

  await fs.writeFile(REPORT_PATH, JSON.stringify(payload, null, 2), "utf-8");
}

function markPass(report, step, detail) {
  report[step].status = "PASS";
  if (detail) {
    report[step].details.push(detail);
  }
}

function markFail(report, step, error) {
  report[step].status = "FAIL";
  report[step].details.push(error?.message || String(error));
}

async function clickAndWaitForUi(page, locator) {
  await locator.waitFor({ state: "visible" });
  await locator.click();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
}

function withinSection(page, sectionTitlePattern) {
  return page
    .locator("section, div")
    .filter({ has: page.getByText(sectionTitlePattern, { exact: false }) })
    .first();
}

async function openLegalLinkAndValidate({
  page,
  label,
  expectedHeading,
  screenshotName,
}) {
  const link = page.getByRole("link", { name: label }).first();
  await expect(link).toBeVisible();

  const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await link.click();

  const popup = await popupPromise;
  let targetPage = page;
  let openedNewTab = false;

  if (popup) {
    targetPage = popup;
    openedNewTab = true;
    await popup.waitForLoadState("domcontentloaded");
  } else {
    await page.waitForLoadState("domcontentloaded");
  }

  await expect(targetPage.getByText(expectedHeading, { exact: false }).first()).toBeVisible();

  const legalBodyText = await targetPage.locator("body").innerText();
  expect(legalBodyText.trim().length).toBeGreaterThan(150);

  await targetPage.screenshot({
    path: path.join(SCREENSHOT_DIR, screenshotName),
    fullPage: true,
  });

  const finalUrl = targetPage.url();

  if (openedNewTab) {
    await targetPage.close();
    await page.bringToFront();
  } else {
    await page.goBack();
    await page.waitForLoadState("domcontentloaded");
  }

  return finalUrl;
}

test("Login with Google and validate Mi Negocio workflow", async ({ page }) => {
  await ensureArtifactsDirs();

  const report = createInitialReport();
  const metadata = {
    environment: process.env.SALEADS_ENV || "unspecified",
    startUrl: process.env.SALEADS_LOGIN_URL || null,
    termsUrl: null,
    privacyUrl: null,
  };

  try {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (!loginUrl) {
      throw new Error(
        "SALEADS_LOGIN_URL is required so the test can run in any environment without hardcoded domains.",
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });

    // Step 1: Login with Google
    try {
      const googleLoginButton = page
        .getByRole("button", { name: /google|sign in|iniciar sesi[oó]n/i })
        .first();
      await clickAndWaitForUi(page, googleLoginButton);

      const accountSelector = page
        .getByText("juanlucasbarbiergarzon@gmail.com", { exact: false })
        .first();
      if (await accountSelector.isVisible().catch(() => false)) {
        await clickAndWaitForUi(page, accountSelector);
      }

      await expect(page.getByText(/Negocio/i).first()).toBeVisible();
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "01-dashboard-loaded.png"),
        fullPage: true,
      });

      markPass(report, "Login", "Main interface and sidebar are visible after Google login.");
    } catch (error) {
      markFail(report, "Login", error);
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      const negocioSection = page.getByText(/^Negocio$/i).first();
      await clickAndWaitForUi(page, negocioSection);

      const miNegocioOption = page.getByText(/^Mi Negocio$/i).first();
      await clickAndWaitForUi(page, miNegocioOption);

      await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Administrar Negocios", { exact: false }).first()).toBeVisible();

      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "02-mi-negocio-menu-expanded.png"),
        fullPage: true,
      });

      markPass(report, "Mi Negocio menu", "Mi Negocio submenu expanded with required options.");
    } catch (error) {
      markFail(report, "Mi Negocio menu", error);
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const agregarNegocioSubmenu = page.getByText("Agregar Negocio", { exact: false }).first();
      await clickAndWaitForUi(page, agregarNegocioSubmenu);

      await expect(page.getByText("Crear Nuevo Negocio", { exact: false })).toBeVisible();
      const nombreNegocioInput = page.getByLabel("Nombre del Negocio", { exact: false });
      await expect(nombreNegocioInput).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
      await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible();
      await expect(page.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

      await nombreNegocioInput.click();
      await nombreNegocioInput.fill("Negocio Prueba Automatización");
      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "03-agregar-negocio-modal.png"),
        fullPage: true,
      });
      await clickAndWaitForUi(page, page.getByRole("button", { name: "Cancelar" }));

      markPass(report, "Agregar Negocio modal", "Agregar Negocio modal validated successfully.");
    } catch (error) {
      markFail(report, "Agregar Negocio modal", error);
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      const miNegocioMenu = page.getByText(/^Mi Negocio$/i).first();
      if (await miNegocioMenu.isVisible().catch(() => false)) {
        await clickAndWaitForUi(page, miNegocioMenu);
      }

      const administrarNegocios = page.getByText("Administrar Negocios", { exact: false }).first();
      await clickAndWaitForUi(page, administrarNegocios);

      await expect(page.getByText("Información General", { exact: false })).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta", { exact: false })).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: false })).toBeVisible();
      await expect(page.getByText("Sección Legal", { exact: false })).toBeVisible();

      await page.screenshot({
        path: path.join(SCREENSHOT_DIR, "04-administrar-negocios.png"),
        fullPage: true,
      });

      markPass(report, "Administrar Negocios view", "Account page loaded with all expected sections.");
    } catch (error) {
      markFail(report, "Administrar Negocios view", error);
      throw error;
    }

    // Step 5: Validate Información General
    try {
      const infoSection = withinSection(page, /Información General/i);
      await expect(infoSection.getByText(/@|correo|email/i).first()).toBeVisible();
      await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(infoSection.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();
      markPass(report, "Información General", "User/profile and plan details are visible.");
    } catch (error) {
      markFail(report, "Información General", error);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      const accountDetailsSection = withinSection(page, /Detalles de la Cuenta/i);
      await expect(accountDetailsSection.getByText("Cuenta creada", { exact: false })).toBeVisible();
      await expect(accountDetailsSection.getByText("Estado activo", { exact: false })).toBeVisible();
      await expect(accountDetailsSection.getByText("Idioma seleccionado", { exact: false })).toBeVisible();
      markPass(report, "Detalles de la Cuenta", "Account details block contains expected labels.");
    } catch (error) {
      markFail(report, "Detalles de la Cuenta", error);
    }

    // Step 7: Validate Tus Negocios
    try {
      const businessSection = withinSection(page, /Tus Negocios/i);
      await expect(businessSection).toBeVisible();
      await expect(businessSection.getByText("Agregar Negocio", { exact: false })).toBeVisible();
      await expect(businessSection.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
      markPass(report, "Tus Negocios", "Business list and business limits are visible.");
    } catch (error) {
      markFail(report, "Tus Negocios", error);
    }

    // Step 8: Validate Términos y Condiciones
    try {
      metadata.termsUrl = await openLegalLinkAndValidate({
        page,
        label: "Términos y Condiciones",
        expectedHeading: /Términos y Condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png",
      });
      markPass(
        report,
        "Términos y Condiciones",
        `Legal page validated. URL: ${metadata.termsUrl}`,
      );
    } catch (error) {
      markFail(report, "Términos y Condiciones", error);
    }

    // Step 9: Validate Política de Privacidad
    try {
      metadata.privacyUrl = await openLegalLinkAndValidate({
        page,
        label: "Política de Privacidad",
        expectedHeading: /Política de Privacidad/i,
        screenshotName: "06-politica-de-privacidad.png",
      });
      markPass(report, "Política de Privacidad", `Legal page validated. URL: ${metadata.privacyUrl}`);
    } catch (error) {
      markFail(report, "Política de Privacidad", error);
    }
  } finally {
    await saveReport(report, metadata);
    // Step 10: Final report in stdout for CI and automation logs.
    // eslint-disable-next-line no-console
    console.log(JSON.stringify({ finalReport: report, metadata }, null, 2));
  }
});
