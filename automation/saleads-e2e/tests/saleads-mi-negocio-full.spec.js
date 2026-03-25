const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const LOGIN_URL = process.env.SALEADS_LOGIN_URL;
const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";

const finalReport = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL",
};

const capturedUrls = {};

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
}

async function screenshotCheckpoint(page, checkpointName) {
  const safeName = checkpointName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  await page.screenshot({
    path: path.join("artifacts", `${safeName}.png`),
    fullPage: true,
  });
}

async function clickByVisibleText(page, texts) {
  for (const text of texts) {
    const roleButton = page.getByRole("button", { name: new RegExp(escapeRegExp(text), "i") }).first();
    if (await roleButton.isVisible().catch(() => false)) {
      await roleButton.click();
      await waitForUiLoad(page);
      return true;
    }

    const link = page.getByRole("link", { name: new RegExp(escapeRegExp(text), "i") }).first();
    if (await link.isVisible().catch(() => false)) {
      await link.click();
      await waitForUiLoad(page);
      return true;
    }

    const anyText = page.getByText(new RegExp(`^${escapeRegExp(text)}$`, "i")).first();
    if (await anyText.isVisible().catch(() => false)) {
      await anyText.click();
      await waitForUiLoad(page);
      return true;
    }
  }
  return false;
}

async function saveFinalReport() {
  await fs.mkdir("artifacts", { recursive: true });
  await fs.writeFile(
    path.join("artifacts", "final-report.json"),
    JSON.stringify(
      {
        name: "saleads_mi_negocio_full_test",
        report: finalReport,
        urls: capturedUrls,
      },
      null,
      2
    ),
    "utf8"
  );
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio full workflow", async ({ page, context }) => {
    test.skip(!LOGIN_URL, "SALEADS_LOGIN_URL env var is required.");
    await fs.mkdir("artifacts", { recursive: true });

    // Step 1: Login with Google
    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);

    const loginClicked = await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google",
    ]);
    expect(loginClicked).toBeTruthy();

    const googleEmailOption = page.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i")).first();
    if (await googleEmailOption.isVisible().catch(() => false)) {
      await googleEmailOption.click();
      await waitForUiLoad(page);
    }

    await expect(page.locator("aside")).toBeVisible({ timeout: 60000 });
    await expect(page.getByText(/Negocio/i)).toBeVisible({ timeout: 60000 });
    finalReport.Login = "PASS";
    await screenshotCheckpoint(page, "01-dashboard-loaded");

    // Step 2: Open Mi Negocio menu
    const negocioClicked = await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
    expect(negocioClicked).toBeTruthy();
    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible({ timeout: 20000 });
    finalReport["Mi Negocio menu"] = "PASS";
    await screenshotCheckpoint(page, "02-mi-negocio-expanded");

    // Step 3: Validate Agregar Negocio modal
    const addBusinessClicked = await clickByVisibleText(page, ["Agregar Negocio"]);
    expect(addBusinessClicked).toBeTruthy();
    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: 20000 });
    await screenshotCheckpoint(page, "03-agregar-negocio-modal");

    const businessNameInput = page.getByLabel(/Nombre del Negocio/i);
    await businessNameInput.fill("Negocio Prueba Automatización");
    await page.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUiLoad(page);
    finalReport["Agregar Negocio modal"] = "PASS";

    // Step 4: Open Administrar Negocios
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
    }
    const manageBusinessesClicked = await clickByVisibleText(page, ["Administrar Negocios"]);
    expect(manageBusinessesClicked).toBeTruthy();
    await expect(page.getByText(/Información General/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Sección Legal/i)).toBeVisible({ timeout: 30000 });
    finalReport["Administrar Negocios view"] = "PASS";
    await screenshotCheckpoint(page, "04-administrar-negocios-view");

    // Step 5: Validate Información General
    await expect(page.getByText(/@/)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 20000 });
    finalReport["Información General"] = "PASS";

    // Step 6: Validate Detalles de la Cuenta
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Estado activo/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 20000 });
    finalReport["Detalles de la Cuenta"] = "PASS";

    // Step 7: Validate Tus Negocios
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 20000 });
    finalReport["Tus Negocios"] = "PASS";

    // Step 8: Validate Términos y Condiciones
    const termsLocator = page.getByRole("link", { name: /Términos y Condiciones/i }).first();
    await expect(termsLocator).toBeVisible({ timeout: 20000 });
    const termsPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await termsLocator.click();
    await waitForUiLoad(page);

    const termsPage = (await termsPopupPromise) || page;
    await waitForUiLoad(termsPage);
    await expect(termsPage.getByText(/Términos y Condiciones/i)).toBeVisible({ timeout: 30000 });
    await expect(termsPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúñÑ]{20,}/, {
      timeout: 30000,
    });
    capturedUrls.terminosYCondiciones = termsPage.url();
    await screenshotCheckpoint(termsPage, "08-terminos-y-condiciones");
    finalReport["Términos y Condiciones"] = "PASS";

    if (termsPage !== page && !termsPage.isClosed()) {
      await termsPage.close();
    }
    await page.bringToFront();
    await waitForUiLoad(page);

    // Step 9: Validate Política de Privacidad
    const privacyLocator = page.getByRole("link", { name: /Política de Privacidad/i }).first();
    await expect(privacyLocator).toBeVisible({ timeout: 20000 });
    const privacyPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await privacyLocator.click();
    await waitForUiLoad(page);

    const privacyPage = (await privacyPopupPromise) || page;
    await waitForUiLoad(privacyPage);
    await expect(privacyPage.getByText(/Política de Privacidad/i)).toBeVisible({ timeout: 30000 });
    await expect(privacyPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúñÑ]{20,}/, {
      timeout: 30000,
    });
    capturedUrls.politicaDePrivacidad = privacyPage.url();
    await screenshotCheckpoint(privacyPage, "09-politica-de-privacidad");
    finalReport["Política de Privacidad"] = "PASS";

    if (privacyPage !== page && !privacyPage.isClosed()) {
      await privacyPage.close();
    }
    await page.bringToFront();
    await waitForUiLoad(page);

    // Step 10: Final report
    await saveFinalReport();
  });

  test.afterEach(async () => {
    await saveFinalReport();
  });
});
