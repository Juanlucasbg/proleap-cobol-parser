const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const runTimestamp = new Date().toISOString().replace(/[:.]/g, "-");
const evidenceDir = path.resolve(__dirname, "../screenshots", runTimestamp);
const reportPath = path.resolve(__dirname, "../test-results", `mi-negocio-report-${runTimestamp}.json`);

const checkpoints = [];
const stepResults = {
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

function sanitize(name) {
  return name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9-_]/g, "_")
    .toLowerCase();
}

async function capture(page, label) {
  const fileName = `${sanitize(label)}.png`;
  const filePath = path.join(evidenceDir, fileName);
  await page.screenshot({ path: filePath, fullPage: true });
  checkpoints.push({ label, screenshot: filePath });
}

async function clickByVisibleText(page, candidates) {
  for (const candidate of candidates) {
    const locator = page.getByRole("button", { name: new RegExp(candidate, "i") }).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      return true;
    }

    const link = page.getByRole("link", { name: new RegExp(candidate, "i") }).first();
    if (await link.isVisible().catch(() => false)) {
      await link.click();
      return true;
    }

    const generic = page.getByText(new RegExp(candidate, "i")).first();
    if (await generic.isVisible().catch(() => false)) {
      await generic.click();
      return true;
    }
  }

  return false;
}

async function ensureUiSettled(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function visibleByText(page, regex) {
  return page.getByText(regex).first().isVisible().catch(() => false);
}

async function openAndValidateLegal(page, labelRegex, headingRegex, reportKey) {
  const originPage = page;
  const [maybePopup] = await Promise.all([
    originPage.context().waitForEvent("page", { timeout: 5000 }).catch(() => null),
    originPage.getByText(labelRegex).first().click(),
  ]);

  await ensureUiSettled(originPage);

  let legalPage = originPage;
  if (maybePopup) {
    legalPage = maybePopup;
    await legalPage.waitForLoadState("domcontentloaded");
  }

  await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({ timeout: 20000 });
  const legalBody = legalPage.locator("main,article,body").first();
  await expect(legalBody).toContainText(/\S+/, { timeout: 20000 });

  await capture(legalPage, `${reportKey} page`);
  const finalUrl = legalPage.url();
  checkpoints.push({ label: `${reportKey} final URL`, url: finalUrl });
  stepResults[reportKey] = "PASS";

  if (legalPage !== originPage) {
    await legalPage.close();
    await originPage.bringToFront();
  }
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test.beforeAll(async () => {
    fs.mkdirSync(evidenceDir, { recursive: true });
    fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  });

  test.afterAll(async () => {
    const report = {
      runTimestamp,
      stepResults,
      checkpoints,
    };
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");
    // eslint-disable-next-line no-console
    console.log(`Final report generated at: ${reportPath}`);
  });

  test("executes login and Mi Negocio workflow with evidence", async ({ page }) => {
    const startUrl = process.env.SALEADS_START_URL || process.env.SALEADS_BASE_URL;
    if (startUrl) {
      await page.goto(startUrl);
      await ensureUiSettled(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No initial page available. Set SALEADS_START_URL (preferred) or SALEADS_BASE_URL, or preload the SaleADS login page before running.",
      );
    }

    // Step 1: Login with Google
    const clickedLogin = await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesion con Google",
      "Continuar con Google",
      "Google",
      "Login",
      "Iniciar sesion",
    ]);
    expect(clickedLogin).toBeTruthy();
    await ensureUiSettled(page);

    const googleEmail = process.env.SALEADS_GOOGLE_EMAIL || process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
    const accountChoice = page.getByText(new RegExp(googleEmail.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")).first();
    if (await accountChoice.isVisible().catch(() => false)) {
      await accountChoice.click();
      await ensureUiSettled(page);
    }

    await expect(page.locator("nav,aside").first()).toBeVisible({ timeout: 45000 });
    stepResults.Login = "PASS";
    await capture(page, "dashboard loaded");

    // Step 2: Open Mi Negocio menu
    const sidebar = page.locator("aside,nav").first();
    await expect(sidebar).toBeVisible();

    const negocioClicked = await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
    expect(negocioClicked).toBeTruthy();
    await ensureUiSettled(page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20000 });
    stepResults["Mi Negocio menu"] = "PASS";
    await capture(page, "mi negocio menu expanded");

    // Step 3: Validate Agregar Negocio modal
    await page.getByText(/Agregar Negocio/i).first().click();
    await ensureUiSettled(page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 20000 });
    const nombreInput = page.getByLabel(/Nombre del Negocio/i).first();
    const businessName = process.env.SALEADS_TEST_BUSINESS_NAME || "Negocio Prueba Automatizacion";
    if (await nombreInput.isVisible().catch(() => false)) {
      await nombreInput.click();
      await nombreInput.fill(businessName);
    } else {
      const fallbackInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
      await fallbackInput.fill(businessName);
    }

    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 20000 });
    stepResults["Agregar Negocio modal"] = "PASS";
    await capture(page, "agregar negocio modal");

    await page.getByRole("button", { name: /Cancelar/i }).first().click();
    await ensureUiSettled(page);

    // Step 4: Open Administrar Negocios
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
      await ensureUiSettled(page);
    }
    await page.getByText(/Administrar Negocios/i).first().click();
    await ensureUiSettled(page);

    await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible({ timeout: 30000 });
    stepResults["Administrar Negocios view"] = "PASS";
    await capture(page, "administrar negocios account page");

    // Step 5: Información General
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 20000 });
    const expectedUserName = process.env.SALEADS_USER_NAME;
    const expectedUserEmail = process.env.SALEADS_USER_EMAIL || googleEmail;
    const emailRegex = new RegExp(expectedUserEmail.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");

    if (expectedUserName) {
      await expect(page.getByText(new RegExp(expectedUserName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")).first()).toBeVisible({
        timeout: 20000,
      });
    }

    const exactEmailVisible = await visibleByText(page, emailRegex);
    if (exactEmailVisible) {
      await expect(page.getByText(emailRegex).first()).toBeVisible({ timeout: 20000 });
    } else {
      // Fallback for environments that hide part of the email.
      await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible({ timeout: 20000 });
    }

    stepResults["Información General"] = "PASS";

    // Step 6: Detalles de la Cuenta
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Estado activo|Estado\s*activo/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20000 });
    stepResults["Detalles de la Cuenta"] = "PASS";

    // Step 7: Tus Negocios
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 20000 });
    stepResults["Tus Negocios"] = "PASS";

    // Step 8: Terminos y Condiciones
    await openAndValidateLegal(
      page,
      /Terminos y Condiciones|Términos y Condiciones/i,
      /Terminos y Condiciones|Términos y Condiciones/i,
      "Términos y Condiciones",
    );

    // Step 9: Política de Privacidad
    await openAndValidateLegal(
      page,
      /Politica de Privacidad|Política de Privacidad/i,
      /Politica de Privacidad|Política de Privacidad/i,
      "Política de Privacidad",
    );
  });
});
