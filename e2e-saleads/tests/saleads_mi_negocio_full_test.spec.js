const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const TEST_NAME = "saleads_mi_negocio_full_test";
const SCREENSHOTS_DIR = path.resolve(__dirname, "..", "screenshots");
const REPORTS_DIR = path.resolve(__dirname, "..", "reports");

function slug(text) {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function screenshot(page, fileName, fullPage = false) {
  await ensureDir(SCREENSHOTS_DIR);
  await page.screenshot({
    path: path.join(SCREENSHOTS_DIR, fileName),
    fullPage,
  });
}

function recordStep(report, key, status, details = {}) {
  report.steps[key] = {
    status,
    ...details,
  };
}

function textLocator(page, text, options = {}) {
  return page.getByText(text, { exact: options.exact ?? false });
}

async function clickFirstVisible(locators) {
  for (const locator of locators) {
    const count = await locator.count();
    if (count > 0 && (await locator.first().isVisible())) {
      await locator.first().click();
      return true;
    }
  }
  return false;
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test(TEST_NAME, async ({ page, context }) => {
    const report = {
      testName: TEST_NAME,
      generatedAt: new Date().toISOString(),
      finalStatus: "PASS",
      steps: {},
    };

    await ensureDir(REPORTS_DIR);

    const currentUrl = page.url();
    if (!currentUrl || currentUrl === "about:blank") {
      const baseUrl = process.env.SALEADS_BASE_URL;
      if (!baseUrl) {
        throw new Error(
          "No active SaleADS page found. Open SaleADS login page first or set SALEADS_BASE_URL.",
        );
      }
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUiLoad(page);

    // Step 1: Login with Google
    try {
      const loginClicked = await clickFirstVisible([
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        textLocator(page, "Sign in with Google"),
        textLocator(page, "Iniciar sesión con Google"),
      ]);

      if (loginClicked) {
        await waitForUiLoad(page);
      }

      const googleEmailOption = textLocator(
        page,
        "juanlucasbarbiergarzon@gmail.com",
      );
      if ((await googleEmailOption.count()) > 0 && (await googleEmailOption.first().isVisible())) {
        await googleEmailOption.first().click();
        await waitForUiLoad(page);
      }

      const sidebar = page.locator("aside, nav").filter({
        hasText: /negocio|mi negocio|dashboard|inicio/i,
      });
      await expect(sidebar.first()).toBeVisible({ timeout: 60_000 });

      await screenshot(page, "01-dashboard-loaded.png");
      recordStep(report, "Login", "PASS", {
        validations: [
          "Main application interface appears",
          "Left sidebar navigation is visible",
        ],
      });
    } catch (error) {
      recordStep(report, "Login", "FAIL", { error: String(error) });
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      const negocioMenu = page
        .getByRole("button", { name: /mi negocio|negocio/i })
        .first();
      if ((await negocioMenu.count()) > 0) {
        await negocioMenu.click();
      } else {
        await textLocator(page, "Mi Negocio").first().click();
      }
      await waitForUiLoad(page);

      await expect(textLocator(page, "Agregar Negocio").first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(textLocator(page, "Administrar Negocios").first()).toBeVisible({
        timeout: 30_000,
      });

      await screenshot(page, "02-mi-negocio-menu-expanded.png");
      recordStep(report, "Mi Negocio menu", "PASS");
    } catch (error) {
      recordStep(report, "Mi Negocio menu", "FAIL", { error: String(error) });
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await textLocator(page, "Agregar Negocio").first().click();
      await waitForUiLoad(page);

      await expect(textLocator(page, "Crear Nuevo Negocio").first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(page.getByLabel("Nombre del Negocio").or(page.getByPlaceholder("Nombre del Negocio")).first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(textLocator(page, "Tienes 2 de 3 negocios").first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(
        page.getByRole("button", { name: /Crear Negocio/i }).first(),
      ).toBeVisible({ timeout: 30_000 });

      await screenshot(page, "03-agregar-negocio-modal.png");

      const nameInput = page
        .getByLabel("Nombre del Negocio")
        .or(page.getByPlaceholder("Nombre del Negocio"))
        .first();
      await nameInput.fill("Negocio Prueba Automatizacion");
      await page.getByRole("button", { name: "Cancelar" }).first().click();
      await waitForUiLoad(page);

      recordStep(report, "Agregar Negocio modal", "PASS");
    } catch (error) {
      recordStep(report, "Agregar Negocio modal", "FAIL", { error: String(error) });
      throw error;
    }

    // Step 4: Open Administrar Negocios and validate sections
    try {
      const administrar = textLocator(page, "Administrar Negocios").first();
      if (!(await administrar.isVisible())) {
        const negocioMenu = page
          .getByRole("button", { name: /mi negocio|negocio/i })
          .first();
        if ((await negocioMenu.count()) > 0) {
          await negocioMenu.click();
        } else {
          await textLocator(page, "Mi Negocio").first().click();
        }
        await waitForUiLoad(page);
      }

      await textLocator(page, "Administrar Negocios").first().click();
      await waitForUiLoad(page);

      await expect(textLocator(page, "Informacion General").or(textLocator(page, "Información General")).first()).toBeVisible({ timeout: 30_000 });
      await expect(textLocator(page, "Detalles de la Cuenta").first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(textLocator(page, "Tus Negocios").first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(textLocator(page, "Seccion Legal").or(textLocator(page, "Sección Legal")).first()).toBeVisible({
        timeout: 30_000,
      });

      await screenshot(page, "04-administrar-negocios-page.png", true);
      recordStep(report, "Administrar Negocios view", "PASS");
    } catch (error) {
      recordStep(report, "Administrar Negocios view", "FAIL", {
        error: String(error),
      });
      throw error;
    }

    // Step 5: Validate Información General
    try {
      const businessPlan = textLocator(page, "BUSINESS PLAN");
      await expect(businessPlan.first()).toBeVisible({ timeout: 30_000 });
      await expect(textLocator(page, "Cambiar Plan").first()).toBeVisible({
        timeout: 30_000,
      });

      // Heuristic user identity checks in information panel.
      await expect(
        page.locator("section,div").filter({ hasText: /@/ }).first(),
      ).toBeVisible({ timeout: 30_000 });
      await expect(
        page.locator("section,div").filter({ hasText: /[A-Za-z]{2,}\s+[A-Za-z]{2,}/ }).first(),
      ).toBeVisible({ timeout: 30_000 });

      recordStep(report, "Información General", "PASS");
    } catch (error) {
      recordStep(report, "Información General", "FAIL", { error: String(error) });
      throw error;
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await expect(textLocator(page, "Cuenta creada").first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(textLocator(page, "Estado activo").first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(textLocator(page, "Idioma seleccionado").first()).toBeVisible({
        timeout: 30_000,
      });

      recordStep(report, "Detalles de la Cuenta", "PASS");
    } catch (error) {
      recordStep(report, "Detalles de la Cuenta", "FAIL", { error: String(error) });
      throw error;
    }

    // Step 7: Validate Tus Negocios
    try {
      await expect(textLocator(page, "Tus Negocios").first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(textLocator(page, "Agregar Negocio").first()).toBeVisible({
        timeout: 30_000,
      });
      await expect(textLocator(page, "Tienes 2 de 3 negocios").first()).toBeVisible({
        timeout: 30_000,
      });

      recordStep(report, "Tus Negocios", "PASS");
    } catch (error) {
      recordStep(report, "Tus Negocios", "FAIL", { error: String(error) });
      throw error;
    }

    async function validateLegalLink(stepKey, linkText, headingText, screenshotFile) {
      let legalPage = page;
      let finalUrl = "";

      const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(
        () => null,
      );
      await textLocator(page, linkText).first().click();
      await waitForUiLoad(page);

      const popup = await popupPromise;
      if (popup) {
        legalPage = popup;
        await legalPage.waitForLoadState("domcontentloaded");
        await legalPage.waitForTimeout(700);
      } else {
        await page.waitForLoadState("domcontentloaded");
      }

      await expect(textLocator(legalPage, headingText).first()).toBeVisible({
        timeout: 30_000,
      });

      await expect(
        legalPage.locator("p, article, section").filter({ hasText: /.+/ }).first(),
      ).toBeVisible({ timeout: 30_000 });

      finalUrl = legalPage.url();
      await screenshot(legalPage, screenshotFile, true);

      recordStep(report, stepKey, "PASS", {
        finalUrl,
      });

      if (popup) {
        await legalPage.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" });
        await waitForUiLoad(page);
      }
    }

    // Step 8: Validate Términos y Condiciones
    try {
      await validateLegalLink(
        "Términos y Condiciones",
        "Términos y Condiciones",
        "Términos y Condiciones",
        "08-terminos-y-condiciones.png",
      );
    } catch (error) {
      recordStep(report, "Términos y Condiciones", "FAIL", {
        error: String(error),
      });
      throw error;
    }

    // Step 9: Validate Política de Privacidad
    try {
      await validateLegalLink(
        "Política de Privacidad",
        "Política de Privacidad",
        "Política de Privacidad",
        "09-politica-de-privacidad.png",
      );
    } catch (error) {
      recordStep(report, "Política de Privacidad", "FAIL", {
        error: String(error),
      });
      throw error;
    }

    // Step 10: Final report
    const requiredFields = [
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

    for (const field of requiredFields) {
      if (!report.steps[field]) {
        recordStep(report, field, "FAIL", {
          error: "Step not executed",
        });
      }
    }

    const hasFailures = Object.values(report.steps).some(
      (step) => step.status !== "PASS",
    );
    report.finalStatus = hasFailures ? "FAIL" : "PASS";

    const reportName = `${slug(TEST_NAME)}-report.json`;
    await fs.writeFile(
      path.join(REPORTS_DIR, reportName),
      JSON.stringify(report, null, 2),
      "utf-8",
    );

    expect(report.finalStatus, "One or more validation steps failed").toBe("PASS");
  });
});
