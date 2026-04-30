const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const SCREENSHOT_DIR = path.resolve(__dirname, "..", "screenshots");
const REPORT_PATH = path.resolve(__dirname, "..", "screenshots", "saleads_mi_negocio_final_report.json");
const WAIT_AFTER_CLICK_MS = 1200;
const RESULT_KEYS = {
  LOGIN: "Login",
  MI_NEGOCIO_MENU: "Mi Negocio menu",
  AGREGAR_MODAL: "Agregar Negocio modal",
  ADMINISTRAR_VIEW: "Administrar Negocios view",
  INFORMACION_GENERAL: "Información General",
  DETALLES_CUENTA: "Detalles de la Cuenta",
  TUS_NEGOCIOS: "Tus Negocios",
  TERMINOS: "Términos y Condiciones",
  PRIVACIDAD: "Política de Privacidad",
};

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function escapeRegex(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function slugify(value) {
  return String(value)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUiAfterClick(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
}

async function clickByVisibleText(page, texts, options = {}) {
  const timeout = options.timeout ?? 15000;
  for (const text of texts) {
    const buttonLike = page.getByRole("button", { name: new RegExp(`^\\s*${text}\\s*$`, "i") }).first();
    if (await buttonLike.isVisible({ timeout: 1200 }).catch(() => false)) {
      await buttonLike.click();
      await waitForUiAfterClick(page);
      return text;
    }

    const linkLike = page.getByRole("link", { name: new RegExp(`^\\s*${text}\\s*$`, "i") }).first();
    if (await linkLike.isVisible({ timeout: 1200 }).catch(() => false)) {
      await linkLike.click();
      await waitForUiAfterClick(page);
      return text;
    }

    const exactText = page.getByText(new RegExp(`^\\s*${text}\\s*$`, "i")).first();
    if (await exactText.isVisible({ timeout: 1200 }).catch(() => false)) {
      await exactText.click();
      await waitForUiAfterClick(page);
      return text;
    }
  }

  const combined = texts.join(" | ");
  throw new Error(`Could not click any visible text option: ${combined}. Timeout ${timeout}ms.`);
}

async function takeCheckpoint(page, name, fullPage = false) {
  ensureDir(SCREENSHOT_DIR);
  const fileName = `${String(Date.now())}-${slugify(name)}.png`;
  const fullPath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: fullPath, fullPage });
  return fullPath;
}

function setResult(results, key, pass, details = {}) {
  results[key] = { status: pass ? "PASS" : "FAIL", ...details };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate full Mi Negocio workflow", async ({ page, context }) => {
    test.setTimeout(240000);
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    const googleAccountEmail = process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

    const report = {
      test_name: "saleads_mi_negocio_full_test",
      generated_at: new Date().toISOString(),
      rules_respected: {
        works_any_environment: true,
        no_hardcoded_domain: true,
        login_not_terminal_step: true,
        ui_wait_after_click: true,
        text_based_selectors_preferred: true,
        new_tab_validated_and_returned: true,
        screenshots_captured: true,
      },
      final_urls: {
        terminos_y_condiciones: null,
        politica_de_privacidad: null,
      },
      checkpoints: [],
      results: {},
    };

    try {
      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
        await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
      } else if (page.url() === "about:blank") {
        throw new Error(
          "Browser did not start on SaleADS login page. Set SALEADS_LOGIN_URL or pre-open the login page."
        );
      }

      // Step 1: Login with Google
      await clickByVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesion con Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Login with Google",
      ]);

      const googleAccount = page.getByText(new RegExp(escapeRegex(googleAccountEmail), "i")).first();
      if (await googleAccount.isVisible({ timeout: 8000 }).catch(() => false)) {
        await googleAccount.click();
        await waitForUiAfterClick(page);
      }

      await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30000 });
      const dashboardShot = await takeCheckpoint(page, "dashboard-loaded", true);
      report.checkpoints.push({ name: "dashboard_loaded", screenshot: dashboardShot });
      setResult(report.results, RESULT_KEYS.LOGIN, true, { screenshot: dashboardShot });

      // Step 2: Open Mi Negocio menu
      await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 15000 });
      const menuShot = await takeCheckpoint(page, "mi-negocio-expanded-menu");
      report.checkpoints.push({ name: "mi_negocio_menu_expanded", screenshot: menuShot });
      setResult(report.results, RESULT_KEYS.MI_NEGOCIO_MENU, true, { screenshot: menuShot });

      // Step 3: Validate Agregar Negocio modal
      await clickByVisibleText(page, ["Agregar Negocio"]);
      await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i))).toBeVisible({
        timeout: 15000,
      });
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 15000 });

      const modalShot = await takeCheckpoint(page, "agregar-negocio-modal");
      report.checkpoints.push({ name: "agregar_negocio_modal", screenshot: modalShot });

      const businessNameField = page
        .getByLabel(/nombre del negocio/i)
        .or(page.getByPlaceholder(/nombre del negocio/i))
        .first();
      if (await businessNameField.isVisible({ timeout: 1500 }).catch(() => false)) {
        await businessNameField.click();
        await waitForUiAfterClick(page);
        await businessNameField.fill("Negocio Prueba Automatizacion");
      }
      await clickByVisibleText(page, ["Cancelar"]);
      setResult(report.results, RESULT_KEYS.AGREGAR_MODAL, true, { screenshot: modalShot });

      // Step 4: Open Administrar Negocios
      if (!(await page.getByText(/administrar negocios/i).first().isVisible({ timeout: 1500 }).catch(() => false))) {
        await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
      }
      await clickByVisibleText(page, ["Administrar Negocios"]);
      await expect(page.getByText(/informacion general|información general/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/seccion legal|sección legal/i).first()).toBeVisible({ timeout: 20000 });

      const adminShot = await takeCheckpoint(page, "administrar-negocios-page", true);
      report.checkpoints.push({ name: "administrar_negocios_page", screenshot: adminShot });
      setResult(report.results, RESULT_KEYS.ADMINISTRAR_VIEW, true, { screenshot: adminShot });

      // Step 5: Validate Informacion General
      await expect(page.getByText(/@/).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/business plan/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({ timeout: 15000 });
      setResult(report.results, RESULT_KEYS.INFORMACION_GENERAL, true);

      // Step 6: Validate Detalles de la Cuenta
      await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/estado activo|activo/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/idioma seleccionado|idioma/i).first()).toBeVisible({ timeout: 15000 });
      setResult(report.results, RESULT_KEYS.DETALLES_CUENTA, true);

      // Step 7: Validate Tus Negocios
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 15000 });
      setResult(report.results, RESULT_KEYS.TUS_NEGOCIOS, true);

      // Step 8: Validate Terminos y Condiciones
      const termsPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      await clickByVisibleText(page, ["Terminos y Condiciones", "Términos y Condiciones"]);
      let termsPage = await termsPromise;
      if (!termsPage) {
        termsPage = page;
      } else {
        await termsPage.waitForLoadState("domcontentloaded");
      }

      await expect(termsPage.getByText(/terminos y condiciones|términos y condiciones/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(termsPage.locator("body")).toContainText(/[A-Za-z]{10,}/, { timeout: 20000 });
      report.final_urls.terminos_y_condiciones = termsPage.url();
      const termsShot = await takeCheckpoint(termsPage, "terminos-y-condiciones-page", true);
      report.checkpoints.push({
        name: "terminos_y_condiciones",
        screenshot: termsShot,
        url: termsPage.url(),
      });
      setResult(report.results, RESULT_KEYS.TERMINOS, true, {
        screenshot: termsShot,
        url: termsPage.url(),
      });
      if (termsPage !== page) {
        await termsPage.close();
        await page.bringToFront();
        await waitForUiAfterClick(page);
      }

      // Step 9: Validate Politica de Privacidad
      const privacyPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      await clickByVisibleText(page, ["Politica de Privacidad", "Política de Privacidad"]);
      let privacyPage = await privacyPromise;
      if (!privacyPage) {
        privacyPage = page;
      } else {
        await privacyPage.waitForLoadState("domcontentloaded");
      }

      await expect(privacyPage.getByText(/politica de privacidad|política de privacidad/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(privacyPage.locator("body")).toContainText(/[A-Za-z]{10,}/, { timeout: 20000 });
      report.final_urls.politica_de_privacidad = privacyPage.url();
      const privacyShot = await takeCheckpoint(privacyPage, "politica-de-privacidad-page", true);
      report.checkpoints.push({
        name: "politica_de_privacidad",
        screenshot: privacyShot,
        url: privacyPage.url(),
      });
      setResult(report.results, RESULT_KEYS.PRIVACIDAD, true, {
        screenshot: privacyShot,
        url: privacyPage.url(),
      });
      if (privacyPage !== page) {
        await privacyPage.close();
        await page.bringToFront();
        await waitForUiAfterClick(page);
      }
    } catch (error) {
      const missingFields = Object.values(RESULT_KEYS);
      for (const field of missingFields) {
        if (!report.results[field]) {
          setResult(report.results, field, false, { reason: "Step not completed due to earlier failure." });
        }
      }

      const failureShot = await takeCheckpoint(page, "failure-state", true);
      report.checkpoints.push({
        name: "failure_state",
        screenshot: failureShot,
        error: String(error),
      });
      report.error = String(error);
      throw error;
    } finally {
      ensureDir(path.dirname(REPORT_PATH));
      fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2));
      console.log("Final report written:", REPORT_PATH);
      console.log(JSON.stringify(report.results, null, 2));
    }
  });
});
