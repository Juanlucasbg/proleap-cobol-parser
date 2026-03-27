const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const runTimestamp = new Date().toISOString().replace(/[:.]/g, "-");
const outputRoot = path.join(__dirname, "..", "artifacts", runTimestamp);
const screenshotsDir = path.join(outputRoot, "screenshots");
const reportFile = path.join(outputRoot, "final-report.json");

const maybeText = (value) => (value ? value : "No disponible");
const normalize = (text) => text.replace(/\s+/g, " ").trim();

async function waitUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function waitVisibleByText(page, value, timeout = 20000) {
  const target = page.getByText(value, { exact: false }).first();
  await expect(target).toBeVisible({ timeout });
  return target;
}

async function clickVisibleByText(page, value) {
  const target = await waitVisibleByText(page, value);
  await target.click();
  await waitUi(page);
}

async function capture(page, name, fullPage = false) {
  fs.mkdirSync(screenshotsDir, { recursive: true });
  await page.screenshot({
    path: path.join(screenshotsDir, `${name}.png`),
    fullPage
  });
}

function writeFinalReport(payload) {
  fs.mkdirSync(outputRoot, { recursive: true });
  fs.writeFileSync(reportFile, JSON.stringify(payload, null, 2), "utf8");
}

function createStatusMap() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
}

async function isMainAppVisible(page) {
  const sidebar = page.getByRole("navigation").first();
  if (await sidebar.isVisible().catch(() => false)) {
    return true;
  }
  return page.getByText("Negocio", { exact: false }).first().isVisible().catch(() => false);
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  fs.mkdirSync(outputRoot, { recursive: true });
  const status = createStatusMap();
  const startUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL || process.env.SALEADS_URL || null;
  const details = {
    envBaseUrl: startUrl,
    evidence: {
      dashboardScreenshot: null,
      expandedMenuScreenshot: null,
      modalScreenshot: null,
      accountPageScreenshot: null,
      termsScreenshot: null,
      privacyScreenshot: null
    },
    urls: {
      terms: null,
      privacy: null
    },
    notes: []
  };
  try {
    // The prompt states browser starts at login page; baseURL is optional fallback.
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitUi(page);
    } else {
      details.notes.push("No se definió SALEADS_BASE_URL/BASE_URL: se asume sesión ya ubicada en la página de login.");
      if (page.url() === "about:blank") {
        throw new Error(
          "Precondición incumplida: el navegador inició en about:blank. Define SALEADS_BASE_URL o BASE_URL con la página de login de SaleADS."
        );
      }
    }

    // Step 1: Login with Google.
    if (await isMainAppVisible(page)) {
      details.notes.push("Sesión ya autenticada: se omite click de login y se continúa con el flujo.");
    } else {
      const googleLoginCandidates = [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Google"
      ];
      let clickedGoogleLogin = false;
      for (const candidate of googleLoginCandidates) {
        const locator = page.getByRole("button", { name: new RegExp(candidate, "i") }).first();
        if (await locator.isVisible().catch(() => false)) {
          await locator.click();
          clickedGoogleLogin = true;
          break;
        }
      }

      if (!clickedGoogleLogin) {
        const fallback = page.getByText(/google/i).first();
        await expect(fallback).toBeVisible({ timeout: 15000 });
        await fallback.click();
      }
      await waitUi(page);

      // Optional Google account chooser.
      const accountEmail = process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";
      const chooser = page.getByText(accountEmail, { exact: false }).first();
      if (await chooser.isVisible().catch(() => false)) {
        await chooser.click();
        await waitUi(page);
      } else {
        details.notes.push("Selector de cuenta Google no visible (posible sesión previa o flujo alterno).");
      }
    }

    // Validate main interface and left sidebar.
    const sidebarCandidate = page.getByRole("navigation").first();
    if (await sidebarCandidate.isVisible().catch(() => false)) {
      await expect(sidebarCandidate).toBeVisible({ timeout: 30000 });
    } else {
      // Fallback text-based check for sidebar labels.
      await waitVisibleByText(page, "Negocio", 30000);
    }
    await capture(page, "01-dashboard-loaded");
    details.evidence.dashboardScreenshot = path.relative(outputRoot, path.join(screenshotsDir, "01-dashboard-loaded.png"));
    status.Login = "PASS";

    // Step 2: Open Mi Negocio menu.
    await clickVisibleByText(page, "Negocio");
    await clickVisibleByText(page, "Mi Negocio");

    await waitVisibleByText(page, "Agregar Negocio");
    await waitVisibleByText(page, "Administrar Negocios");

    await capture(page, "02-mi-negocio-expanded");
    details.evidence.expandedMenuScreenshot = path.relative(outputRoot, path.join(screenshotsDir, "02-mi-negocio-expanded.png"));
    status["Mi Negocio menu"] = "PASS";

    // Step 3: Validate Agregar Negocio modal.
    await clickVisibleByText(page, "Agregar Negocio");
    await waitVisibleByText(page, "Crear Nuevo Negocio");
    await waitVisibleByText(page, "Nombre del Negocio");
    await waitVisibleByText(page, "Tienes 2 de 3 negocios");
    await waitVisibleByText(page, "Cancelar");
    await waitVisibleByText(page, "Crear Negocio");

    await capture(page, "03-agregar-negocio-modal");
    details.evidence.modalScreenshot = path.relative(outputRoot, path.join(screenshotsDir, "03-agregar-negocio-modal.png"));

    // Optional actions in modal.
    const nombreField = page.getByLabel("Nombre del Negocio", { exact: false }).first();
    if (await nombreField.isVisible().catch(() => false)) {
      await nombreField.fill("Negocio Prueba Automatización");
    } else {
      const inputFallback = page.locator("input").first();
      if (await inputFallback.isVisible().catch(() => false)) {
        await inputFallback.fill("Negocio Prueba Automatización");
      }
    }
    await clickVisibleByText(page, "Cancelar");
    status["Agregar Negocio modal"] = "PASS";

    // Step 4: Open Administrar Negocios.
    const administrarOption = page.getByText("Administrar Negocios", { exact: false }).first();
    if (!(await administrarOption.isVisible().catch(() => false))) {
      await clickVisibleByText(page, "Mi Negocio");
    }
    await clickVisibleByText(page, "Administrar Negocios");

    await waitVisibleByText(page, "Información General", 30000);
    await waitVisibleByText(page, "Detalles de la Cuenta");
    await waitVisibleByText(page, "Tus Negocios");
    await waitVisibleByText(page, "Sección Legal");
    await capture(page, "04-administrar-negocios-page", true);
    details.evidence.accountPageScreenshot = path.relative(outputRoot, path.join(screenshotsDir, "04-administrar-negocios-page.png"));
    status["Administrar Negocios view"] = "PASS";

    // Step 5: Validate Información General.
    const infoGeneralSection = page.locator("section,div").filter({ hasText: "Información General" }).first();
    await expect(infoGeneralSection).toBeVisible();
    await expect(infoGeneralSection.getByText(/@/)).toBeVisible();
    await expect(infoGeneralSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoGeneralSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    status["Información General"] = "PASS";

    // Step 6: Validate Detalles de la Cuenta.
    const detailsSection = page.locator("section,div").filter({ hasText: "Detalles de la Cuenta" }).first();
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
    status["Detalles de la Cuenta"] = "PASS";

    // Step 7: Validate Tus Negocios.
    const negociosSection = page.locator("section,div").filter({ hasText: "Tus Negocios" }).first();
    await expect(negociosSection).toBeVisible();
    await expect(negociosSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(negociosSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    status["Tus Negocios"] = "PASS";

    // Step 8: Validate Términos y Condiciones.
    {
      const link = page.getByText("Términos y Condiciones", { exact: false }).first();
      await expect(link).toBeVisible();

      const beforeUrl = page.url();
      const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
      await link.click();
      const popup = await popupPromise;
      const legalPage = popup || page;
      await legalPage.waitForLoadState("domcontentloaded");
      await waitUi(legalPage);

      await expect(legalPage.getByText(/Términos y Condiciones/i)).toBeVisible({ timeout: 30000 });
      // Generic legal-content validation by paragraph/article text density.
      const legalText = normalize(await legalPage.locator("body").innerText());
      expect(legalText.length).toBeGreaterThan(120);

      await capture(legalPage, "05-terminos-y-condiciones", true);
      details.evidence.termsScreenshot = path.relative(outputRoot, path.join(screenshotsDir, "05-terminos-y-condiciones.png"));
      details.urls.terms = legalPage.url();
      status["Términos y Condiciones"] = "PASS";

      if (popup) {
        await popup.close();
        await page.bringToFront();
        await waitUi(page);
      } else if (page.url() !== beforeUrl) {
        await page.goBack();
        await waitUi(page);
      }
    }

    // Step 9: Validate Política de Privacidad.
    {
      const link = page.getByText("Política de Privacidad", { exact: false }).first();
      await expect(link).toBeVisible();

      const beforeUrl = page.url();
      const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
      await link.click();
      const popup = await popupPromise;
      const legalPage = popup || page;
      await legalPage.waitForLoadState("domcontentloaded");
      await waitUi(legalPage);

      await expect(legalPage.getByText(/Política de Privacidad/i)).toBeVisible({ timeout: 30000 });
      const legalText = normalize(await legalPage.locator("body").innerText());
      expect(legalText.length).toBeGreaterThan(120);

      await capture(legalPage, "06-politica-de-privacidad", true);
      details.evidence.privacyScreenshot = path.relative(outputRoot, path.join(screenshotsDir, "06-politica-de-privacidad.png"));
      details.urls.privacy = legalPage.url();
      status["Política de Privacidad"] = "PASS";

      if (popup) {
        await popup.close();
        await page.bringToFront();
        await waitUi(page);
      } else if (page.url() !== beforeUrl) {
        await page.goBack();
        await waitUi(page);
      }
    }
  } catch (error) {
    details.notes.push(`Error de ejecución: ${error.message}`);
    throw error;
  } finally {
    // Step 10: Final report.
    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      executedAt: new Date().toISOString(),
      status,
      result: Object.values(status).every((value) => value === "PASS") ? "PASS" : "FAIL",
      fields: {
        Login: maybeText(status.Login),
        "Mi Negocio menu": maybeText(status["Mi Negocio menu"]),
        "Agregar Negocio modal": maybeText(status["Agregar Negocio modal"]),
        "Administrar Negocios view": maybeText(status["Administrar Negocios view"]),
        "Información General": maybeText(status["Información General"]),
        "Detalles de la Cuenta": maybeText(status["Detalles de la Cuenta"]),
        "Tus Negocios": maybeText(status["Tus Negocios"]),
        "Términos y Condiciones": maybeText(status["Términos y Condiciones"]),
        "Política de Privacidad": maybeText(status["Política de Privacidad"])
      },
      evidence: details.evidence,
      urls: details.urls,
      notes: details.notes
    };

    writeFinalReport(finalReport);
    test.info().annotations.push({ type: "final-report", description: reportFile });
  }
});
