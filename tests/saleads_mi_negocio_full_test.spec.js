const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const ARTIFACTS_DIR = path.join(__dirname, "..", "test-results", "saleads_mi_negocio_full_test");
const GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function sanitizeName(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function clickByVisibleText(page, texts, options = {}) {
  const candidates = Array.isArray(texts) ? texts : [texts];
  for (const text of candidates) {
    const exactLocator = page.getByText(text, { exact: true });
    if (await exactLocator.first().isVisible().catch(() => false)) {
      await exactLocator.first().click(options);
      await waitForUi(page);
      return text;
    }

    const partialLocator = page.getByText(text);
    if (await partialLocator.first().isVisible().catch(() => false)) {
      await partialLocator.first().click(options);
      await waitForUi(page);
      return text;
    }
  }

  throw new Error(`No visible clickable element found for: ${candidates.join(" | ")}`);
}

async function expectAnyVisible(page, texts, stepLabel, report, key) {
  const candidates = Array.isArray(texts) ? texts : [texts];
  for (const text of candidates) {
    const exactLocator = page.getByText(text, { exact: true });
    if (await exactLocator.first().isVisible().catch(() => false)) {
      report[key] = { status: "PASS", detail: `Found text: "${text}"` };
      return text;
    }

    const partialLocator = page.getByText(text);
    if (await partialLocator.first().isVisible().catch(() => false)) {
      report[key] = { status: "PASS", detail: `Found text: "${text}"` };
      return text;
    }
  }

  report[key] = {
    status: "FAIL",
    detail: `None of these texts were visible for ${stepLabel}: ${candidates.join(" | ")}`
  };
  throw new Error(report[key].detail);
}

async function maybeSelectGoogleAccount(page, context) {
  const accountTile = page.getByText(GOOGLE_EMAIL, { exact: true });
  if (await accountTile.first().isVisible().catch(() => false)) {
    await accountTile.first().click();
    await waitForUi(page);
    return;
  }

  const popup = await context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  if (!popup) {
    return;
  }

  await popup.waitForLoadState("domcontentloaded");
  const popupAccount = popup.getByText(GOOGLE_EMAIL, { exact: true });
  if (await popupAccount.first().isVisible().catch(() => false)) {
    await popupAccount.first().click();
  }
  await popup.waitForTimeout(1000).catch(() => {});
}

async function screenshotCheckpoint(page, name) {
  const fileName = `${sanitizeName(name)}.png`;
  const filePath = path.join(ARTIFACTS_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage: true });
  return filePath;
}

async function getNombreDelNegocioInput(page) {
  const byLabel = page.getByLabel("Nombre del Negocio").first();
  if (await byLabel.isVisible().catch(() => false)) {
    return byLabel;
  }

  const byPlaceholder = page.getByPlaceholder("Nombre del Negocio").first();
  if (await byPlaceholder.isVisible().catch(() => false)) {
    return byPlaceholder;
  }

  return null;
}

function initReport() {
  return {
    Login: { status: "FAIL", detail: "Not executed" },
    "Mi Negocio menu": { status: "FAIL", detail: "Not executed" },
    "Agregar Negocio modal": { status: "FAIL", detail: "Not executed" },
    "Administrar Negocios view": { status: "FAIL", detail: "Not executed" },
    "Información General": { status: "FAIL", detail: "Not executed" },
    "Detalles de la Cuenta": { status: "FAIL", detail: "Not executed" },
    "Tus Negocios": { status: "FAIL", detail: "Not executed" },
    "Términos y Condiciones": { status: "FAIL", detail: "Not executed" },
    "Política de Privacidad": { status: "FAIL", detail: "Not executed" }
  };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context }) => {
    fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });
    const report = initReport();
    const legalUrls = {
      terminos: "",
      privacidad: ""
    };
    let testError = null;

    const baseUrl = process.env.SALEADS_BASE_URL;
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    try {
      // 1) Login with Google
      try {
        await clickByVisibleText(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Google"
        ]);
        await maybeSelectGoogleAccount(page, context);

        await expectAnyVisible(
          page,
          ["Negocio", "Mi Negocio", "Dashboard", "Inicio"],
          "main app shell after login",
          report,
          "Login"
        );

        // Confirm a sidebar-like navigation is visible.
        const sidebar = page.locator("aside, nav").first();
        await expect(sidebar).toBeVisible({ timeout: 15000 });
        report.Login = { status: "PASS", detail: "Logged in and sidebar/main shell visible" };
        await screenshotCheckpoint(page, "01_dashboard_loaded");
      } catch (error) {
        report.Login = { status: "FAIL", detail: String(error.message || error) };
        throw error;
      }

      // 2) Open Mi Negocio menu
      try {
        await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
        await expectAnyVisible(page, ["Agregar Negocio"], "Mi Negocio submenu", report, "Mi Negocio menu");
        await expectAnyVisible(
          page,
          ["Administrar Negocios"],
          "Mi Negocio submenu",
          report,
          "Mi Negocio menu"
        );
        report["Mi Negocio menu"] = {
          status: "PASS",
          detail: "Mi Negocio expanded and submenu options are visible"
        };
        await screenshotCheckpoint(page, "02_mi_negocio_menu_expanded");
      } catch (error) {
        report["Mi Negocio menu"] = { status: "FAIL", detail: String(error.message || error) };
        throw error;
      }

      // 3) Validate Agregar Negocio modal
      try {
        await clickByVisibleText(page, ["Agregar Negocio"]);
        await expectAnyVisible(
          page,
          ["Crear Nuevo Negocio"],
          "Agregar Negocio modal title",
          report,
          "Agregar Negocio modal"
        );

        const input = await getNombreDelNegocioInput(page);
        if (!input) {
          throw new Error('Input "Nombre del Negocio" is not visible by label or placeholder');
        }

        await expect(page.getByText("Tienes 2 de 3 negocios")).toBeVisible();
        await expect(page.getByText("Cancelar", { exact: true })).toBeVisible();
        await expect(page.getByText("Crear Negocio", { exact: true })).toBeVisible();

        await input.click();
        await input.fill("Negocio Prueba Automatización");
        await screenshotCheckpoint(page, "03_agregar_negocio_modal");
        await clickByVisibleText(page, ["Cancelar"]);
        report["Agregar Negocio modal"] = {
          status: "PASS",
          detail: "Modal fields and actions validated"
        };
      } catch (error) {
        report["Agregar Negocio modal"] = { status: "FAIL", detail: String(error.message || error) };
        throw error;
      }

      // 4) Open Administrar Negocios
      try {
        if (!(await page.getByText("Administrar Negocios", { exact: true }).first().isVisible().catch(() => false))) {
          await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
        }

        await clickByVisibleText(page, ["Administrar Negocios"]);
        await expectAnyVisible(
          page,
          ["Información General"],
          "Administrar Negocios page",
          report,
          "Administrar Negocios view"
        );
        await expect(page.getByText("Detalles de la Cuenta")).toBeVisible();
        await expect(page.getByText("Tus Negocios")).toBeVisible();
        await expect(page.getByText("Sección Legal")).toBeVisible();
        await screenshotCheckpoint(page, "04_administrar_negocios_page");
        report["Administrar Negocios view"] = {
          status: "PASS",
          detail: "Account page sections are visible"
        };
      } catch (error) {
        report["Administrar Negocios view"] = { status: "FAIL", detail: String(error.message || error) };
        throw error;
      }

      // 5) Validate Información General
      try {
        const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
        await expect(page.getByText(emailRegex).first()).toBeVisible();
        await expect(page.getByText("BUSINESS PLAN")).toBeVisible();
        await expect(page.getByText("Cambiar Plan")).toBeVisible();
        // Name can vary by environment/user; assert a non-empty heading/text block near info section.
        const nameCandidate = page
          .locator("section,div")
          .filter({ hasText: "Información General" })
          .locator("h1,h2,h3,h4,p,span")
          .first();
        await expect(nameCandidate).toBeVisible();
        report["Información General"] = {
          status: "PASS",
          detail: "Name/email/plan controls validated"
        };
      } catch (error) {
        report["Información General"] = { status: "FAIL", detail: String(error.message || error) };
        throw error;
      }

      // 6) Validate Detalles de la Cuenta
      try {
        await expect(page.getByText("Cuenta creada")).toBeVisible();
        await expect(page.getByText("Estado activo")).toBeVisible();
        await expect(page.getByText("Idioma seleccionado")).toBeVisible();
        report["Detalles de la Cuenta"] = {
          status: "PASS",
          detail: "Account detail labels are visible"
        };
      } catch (error) {
        report["Detalles de la Cuenta"] = { status: "FAIL", detail: String(error.message || error) };
        throw error;
      }

      // 7) Validate Tus Negocios
      try {
        await expect(page.getByText("Tus Negocios")).toBeVisible();
        await expect(page.getByText("Agregar Negocio")).toBeVisible();
        await expect(page.getByText("Tienes 2 de 3 negocios")).toBeVisible();
        report["Tus Negocios"] = {
          status: "PASS",
          detail: "Business list section and capacity text validated"
        };
      } catch (error) {
        report["Tus Negocios"] = { status: "FAIL", detail: String(error.message || error) };
        throw error;
      }

      // 8) Validate Términos y Condiciones
      try {
        const termsPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
        await clickByVisibleText(page, ["Términos y Condiciones"]);
        const termsPopup = await termsPromise;
        const termsPage = termsPopup || page;
        await waitForUi(termsPage);
        await expect(termsPage.getByText("Términos y Condiciones")).toBeVisible({ timeout: 15000 });
        await expect(termsPage.locator("main,article,div,p").filter({ hasText: /./ }).first()).toBeVisible();
        legalUrls.terminos = termsPage.url();
        await screenshotCheckpoint(termsPage, "05_terminos_y_condiciones");
        if (termsPopup) {
          await termsPopup.close().catch(() => {});
          await page.bringToFront();
        }
        report["Términos y Condiciones"] = {
          status: "PASS",
          detail: `Validated heading and legal text. URL: ${legalUrls.terminos}`
        };
      } catch (error) {
        report["Términos y Condiciones"] = { status: "FAIL", detail: String(error.message || error) };
        throw error;
      }

      // 9) Validate Política de Privacidad
      try {
        const privacyPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
        await clickByVisibleText(page, ["Política de Privacidad"]);
        const privacyPopup = await privacyPromise;
        const privacyPage = privacyPopup || page;
        await waitForUi(privacyPage);
        await expect(privacyPage.getByText("Política de Privacidad")).toBeVisible({ timeout: 15000 });
        await expect(privacyPage.locator("main,article,div,p").filter({ hasText: /./ }).first()).toBeVisible();
        legalUrls.privacidad = privacyPage.url();
        await screenshotCheckpoint(privacyPage, "06_politica_de_privacidad");
        if (privacyPopup) {
          await privacyPopup.close().catch(() => {});
          await page.bringToFront();
        }
        report["Política de Privacidad"] = {
          status: "PASS",
          detail: `Validated heading and legal text. URL: ${legalUrls.privacidad}`
        };
      } catch (error) {
        report["Política de Privacidad"] = { status: "FAIL", detail: String(error.message || error) };
        throw error;
      }
    } catch (error) {
      testError = error;
    } finally {
      const finalReportPath = path.join(ARTIFACTS_DIR, "final-report.json");
      fs.writeFileSync(
        finalReportPath,
        JSON.stringify(
          {
            testName: "saleads_mi_negocio_full_test",
            generatedAt: new Date().toISOString(),
            legalUrls,
            results: report
          },
          null,
          2
        )
      );
      // Keep this visible in the terminal output for CI logs.
      console.log("=== FINAL REPORT ===");
      console.log(JSON.stringify(report, null, 2));
      console.log(`Términos y Condiciones URL: ${legalUrls.terminos || "N/A"}`);
      console.log(`Política de Privacidad URL: ${legalUrls.privacidad || "N/A"}`);
      console.log(`Report file: ${finalReportPath}`);
    }

    if (testError) {
      throw testError;
    }
  });
});
