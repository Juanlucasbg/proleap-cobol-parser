const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const ARTIFACTS_DIR = path.join(__dirname, "..", "artifacts", "saleads_mi_negocio_full_test");
const TIMESTAMP = new Date().toISOString().replace(/[:.]/g, "-");
const RUN_DIR = path.join(ARTIFACTS_DIR, TIMESTAMP);

/**
 * Store strict PASS/FAIL statuses by report fields requested by automation.
 */
function createReport() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
    finalUrls: {
      terminos: null,
      privacidad: null,
    },
    screenshotDir: RUN_DIR,
  };
}

async function waitForUiReady(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function safeScreenshot(page, fileName, fullPage = true) {
  await page.screenshot({
    path: path.join(RUN_DIR, fileName),
    fullPage,
  });
}

async function clickByVisibleText(page, textOptions) {
  for (const txt of textOptions) {
    const locator = page.getByText(txt, { exact: true }).first();
    if (await locator.count()) {
      await locator.click();
      await waitForUiReady(page);
      return true;
    }
  }
  return false;
}

async function maybeSelectGoogleAccount(page, email) {
  const accountOption = page.getByText(email, { exact: true }).first();
  if (await accountOption.count()) {
    await accountOption.click();
    await waitForUiReady(page);
    return true;
  }
  return false;
}

async function clickAndHandlePossibleNewTab(page, linkText) {
  const trigger = page.getByText(linkText, { exact: true }).first();
  await expect(trigger).toBeVisible();
  const originUrl = page.url();

  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  await trigger.click();

  const popup = await popupPromise;
  if (popup) {
    await waitForUiReady(popup);
    return { target: popup, openedNewTab: true, originUrl };
  }

  await waitForUiReady(page);
  return { target: page, openedNewTab: false, originUrl };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login Google + flujo Mi Negocio completo", async ({ page }) => {
    fs.mkdirSync(RUN_DIR, { recursive: true });
    const report = createReport();
    let testError;

    try {
      const entryUrl = process.env.SALEADS_ENTRY_URL;
      if (entryUrl) {
        await page.goto(entryUrl, { waitUntil: "domcontentloaded" });
        await waitForUiReady(page);
      }

      if (page.url() === "about:blank") {
        throw new Error(
          "No login page is loaded. Provide SALEADS_ENTRY_URL or preload the SaleADS login page before running."
        );
      }

      // Step 1: Login with Google
      const loginButton = page
        .locator("button, a")
        .filter({
          hasText: /Sign in with Google|Iniciar sesión con Google|Continuar con Google|Google/i,
        })
        .first();
      await expect(loginButton).toBeVisible({ timeout: 120000 });
      await loginButton.click();
      const popup = await page.waitForEvent("popup", { timeout: 15000 }).catch(() => null);
      const authPage = popup || page;
      await waitForUiReady(authPage);
      await maybeSelectGoogleAccount(authPage, "juanlucasbarbiergarzon@gmail.com");
      await waitForUiReady(page);

      await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 120000 });
      report.Login = "PASS";
      await safeScreenshot(page, "01-dashboard-loaded.png");

      // Step 2: Open Mi Negocio menu
      const negocioEntry = page.getByText("Negocio", { exact: true }).first();
      await expect(negocioEntry).toBeVisible();
      await negocioEntry.click();
      await waitForUiReady(page);

      const miNegocioEntry = page.getByText("Mi Negocio", { exact: true }).first();
      await expect(miNegocioEntry).toBeVisible();
      await miNegocioEntry.click();
      await waitForUiReady(page);

      await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible();
      await expect(page.getByText("Administrar Negocios", { exact: true }).first()).toBeVisible();
      report["Mi Negocio menu"] = "PASS";
      await safeScreenshot(page, "02-mi-negocio-menu-expanded.png");

      // Step 3: Validate Agregar Negocio modal
      await page.getByText("Agregar Negocio", { exact: true }).first().click();
      await waitForUiReady(page);

      const modalTitle = page.getByText("Crear Nuevo Negocio", { exact: true }).first();
      await expect(modalTitle).toBeVisible();
      await expect(page.getByLabel("Nombre del Negocio").first()).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible();
      await safeScreenshot(page, "03-agregar-negocio-modal.png");

      const inputNombre = page.getByLabel("Nombre del Negocio").first();
      await inputNombre.click();
      await waitForUiReady(page);
      await inputNombre.fill("Negocio Prueba Automatización");
      await page.getByRole("button", { name: "Cancelar" }).first().click();
      await waitForUiReady(page);
      report["Agregar Negocio modal"] = "PASS";

      // Step 4: Open Administrar Negocios
      if (!(await page.getByText("Administrar Negocios", { exact: true }).first().isVisible())) {
        await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
      }

      await page.getByText("Administrar Negocios", { exact: true }).first().click();
      await waitForUiReady(page);

      await expect(page.getByText("Información General", { exact: true }).first()).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta", { exact: true }).first()).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: true }).first()).toBeVisible();
      await expect(page.getByText("Sección Legal", { exact: true }).first()).toBeVisible();
      report["Administrar Negocios view"] = "PASS";
      await safeScreenshot(page, "04-administrar-negocios-view.png");

      // Step 5: Validate Información General
      const infoSection = page
        .locator("section, div")
        .filter({ has: page.getByText("Información General", { exact: true }) })
        .first();
      await expect(infoSection).toContainText(/@/);
      await expect(infoSection).toContainText("BUSINESS PLAN");
      await expect(page.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible();

      const infoText = (await infoSection.textContent()) || "";
      const possibleNameText = infoText
        .replace(/\S+@\S+\.\S+/g, "")
        .replace(/Información General|BUSINESS PLAN|Cambiar Plan/gi, "")
        .trim();
      expect(possibleNameText.length).toBeGreaterThan(0);
      report["Información General"] = "PASS";

      // Step 6: Validate Detalles de la Cuenta
      await expect(page.getByText("Cuenta creada", { exact: true }).first()).toBeVisible();
      await expect(page.getByText("Estado activo", { exact: true }).first()).toBeVisible();
      await expect(page.getByText("Idioma seleccionado", { exact: true }).first()).toBeVisible();
      report["Detalles de la Cuenta"] = "PASS";

      // Step 7: Validate Tus Negocios
      const tusNegociosSection = page
        .locator("section, div")
        .filter({ has: page.getByText("Tus Negocios", { exact: true }) })
        .first();
      await expect(tusNegociosSection).toBeVisible();
      await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true }).first()).toBeVisible();
      await expect(tusNegociosSection).toContainText(/\w+/);
      report["Tus Negocios"] = "PASS";

      // Step 8: Validate Términos y Condiciones
      const terminosResult = await clickAndHandlePossibleNewTab(page, "Términos y Condiciones");
      await expect(
        terminosResult.target.getByText(/Términos y Condiciones|Términos y condiciones/i).first()
      ).toBeVisible({
        timeout: 30000,
      });
      const terminosBody = terminosResult.target.locator("main, article, body").first();
      await expect(terminosBody).toContainText(/\w+/, { timeout: 30000 });
      report.finalUrls.terminos = terminosResult.target.url();
      await safeScreenshot(terminosResult.target, "08-terminos-y-condiciones.png");
      report["Términos y Condiciones"] = "PASS";

      if (terminosResult.openedNewTab) {
        await terminosResult.target.close();
        await page.bringToFront();
        await waitForUiReady(page);
      } else if (page.url() !== terminosResult.originUrl) {
        await page.goBack().catch(() => {});
        await waitForUiReady(page);
      }

      // Step 9: Validate Política de Privacidad
      const privacidadResult = await clickAndHandlePossibleNewTab(page, "Política de Privacidad");
      await expect(
        privacidadResult.target.getByText(/Política de Privacidad|Política de privacidad/i).first()
      ).toBeVisible({
        timeout: 30000,
      });
      const privacidadBody = privacidadResult.target.locator("main, article, body").first();
      await expect(privacidadBody).toContainText(/\w+/, { timeout: 30000 });
      report.finalUrls.privacidad = privacidadResult.target.url();
      await safeScreenshot(privacidadResult.target, "09-politica-de-privacidad.png");
      report["Política de Privacidad"] = "PASS";

      if (privacidadResult.openedNewTab) {
        await privacidadResult.target.close();
        await page.bringToFront();
        await waitForUiReady(page);
      } else if (page.url() !== privacidadResult.originUrl) {
        await page.goBack().catch(() => {});
        await waitForUiReady(page);
      }
    } catch (error) {
      testError = error;
      await safeScreenshot(page, "99-failure-context.png").catch(() => {});
    } finally {
      const reportPath = path.join(RUN_DIR, "final-report.json");
      fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf-8");
      console.log(`[saleads_mi_negocio_full_test] Final report: ${reportPath}`);
      console.log(JSON.stringify(report, null, 2));
    }

    if (testError) {
      throw testError;
    }
  });
});
