const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const runId = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsRoot = path.join(__dirname, "..", "artifacts", runId);
const screenshotsDir = path.join(artifactsRoot, "screenshots");
const reportPath = path.join(artifactsRoot, "report.json");

const stepLabels = {
  login: "Login",
  miNegocioMenu: "Mi Negocio menu",
  agregarNegocioModal: "Agregar Negocio modal",
  administrarNegociosView: "Administrar Negocios view",
  informacionGeneral: "Información General",
  detallesCuenta: "Detalles de la Cuenta",
  tusNegocios: "Tus Negocios",
  terminos: "Términos y Condiciones",
  politica: "Política de Privacidad"
};

const stepStatus = {};
const stepErrors = {};
const evidence = {
  screenshots: [],
  finalUrls: {}
};

function ensureDirs() {
  fs.mkdirSync(screenshotsDir, { recursive: true });
}

function markPass(stepKey) {
  stepStatus[stepKey] = "PASS";
}

function markFail(stepKey, error) {
  stepStatus[stepKey] = "FAIL";
  stepErrors[stepKey] = error?.message || String(error);
}

async function settleUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function clickByVisibleText(page, text, options = {}) {
  const exact = options.exact !== false;
  const roleCandidates = [
    page.getByRole("button", { name: text, exact }),
    page.getByRole("link", { name: text, exact }),
    page.getByRole("menuitem", { name: text, exact }),
    page.getByText(text, { exact })
  ];

  for (const locator of roleCandidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      await locator.first().click();
      await settleUi(page);
      return;
    }
  }

  throw new Error(`Could not find clickable element with visible text: ${text}`);
}

async function screenshot(page, name, fullPage = false) {
  const safeName = name.replace(/[^a-zA-Z0-9_-]/g, "_");
  const filePath = path.join(screenshotsDir, `${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });
  evidence.screenshots.push(filePath);
}

async function assertVisibleText(page, text, options = {}) {
  const exact = options.exact !== false;
  await expect(page.getByText(text, { exact }).first()).toBeVisible({ timeout: 20_000 });
}

async function validateLegalLink(page, linkText, expectedHeading) {
  let linkLocator = page.getByRole("link", { name: linkText }).first();
  if (!(await linkLocator.isVisible().catch(() => false))) {
    linkLocator = page.getByRole("button", { name: linkText }).first();
  }
  if (!(await linkLocator.isVisible().catch(() => false))) {
    linkLocator = page.getByText(linkText, { exact: false }).first();
  }
  await expect(linkLocator).toBeVisible({ timeout: 15_000 });

  const [popupMaybe] = await Promise.all([
    page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null),
    linkLocator.click()
  ]);

  let targetPage = page;
  if (popupMaybe) {
    targetPage = popupMaybe;
    await targetPage.waitForLoadState("domcontentloaded");
  } else {
    await page.waitForLoadState("domcontentloaded");
  }

  await targetPage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
  const heading = targetPage.getByRole("heading", { name: expectedHeading }).first();
  if (await heading.isVisible().catch(() => false)) {
    await expect(heading).toBeVisible({ timeout: 20_000 });
  } else {
    await expect(targetPage.getByText(expectedHeading, { exact: false }).first()).toBeVisible({
      timeout: 20_000
    });
  }

  // Generic legal content check: body has substantial text.
  const contentLength = await targetPage.locator("body").innerText().then((t) => t.trim().length);
  expect(contentLength).toBeGreaterThan(200);

  await screenshot(targetPage, `${linkText}_page`, true);
  const currentUrl = targetPage.url();

  if (targetPage !== page) {
    await targetPage.close();
    await page.bringToFront();
    await settleUi(page);
  }

  return currentUrl;
}

async function assertLikelyUserNameVisible(infoSection) {
  const profileText = await infoSection.innerText();
  const lines = profileText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  const ignoredPatterns = [
    /informaci[oó]n general/i,
    /business plan/i,
    /cambiar plan/i,
    /@/,
    /cuenta creada/i,
    /estado activo/i,
    /idioma seleccionado/i
  ];

  const hasLikelyName = lines.some((line) => {
    if (ignoredPatterns.some((pattern) => pattern.test(line))) {
      return false;
    }
    return /^[A-Za-zÀ-ÿ'`.-]+(?:\s+[A-Za-zÀ-ÿ'`.-]+)+$/.test(line);
  });

  const hasNameLabel = await infoSection.getByText(/nombre|usuario|user/i).first().isVisible().catch(() => false);
  expect(hasLikelyName || hasNameLabel).toBeTruthy();
}

async function writeReport() {
  const report = {
    name: "saleads_mi_negocio_full_test",
    runId,
    generatedAt: new Date().toISOString(),
    results: Object.entries(stepLabels).map(([key, label]) => ({
      field: label,
      status: stepStatus[key] || "FAIL",
      error: stepErrors[key] || null
    })),
    evidence
  };
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test.beforeAll(() => {
    ensureDirs();
  });

  test.afterAll(async () => {
    await writeReport();
  });

  test("saleads_mi_negocio_full_test", async ({ page }) => {
    // Requires caller to provide URL for current environment.
    const appUrl = process.env.SALEADS_URL || process.env.BASE_URL || process.env.APP_URL;
    if (!appUrl) {
      throw new Error("SALEADS_URL is required and must point to the current SaleADS login page.");
    }

    await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    await settleUi(page);

    // 1) Login with Google.
    try {
      const signInCandidates = [
        page.getByRole("button", { name: /google/i }).first(),
        page.getByRole("button", { name: /sign in/i }).first(),
        page.getByText(/sign in with google/i).first(),
        page.getByText(/iniciar sesi[oó]n con google/i).first()
      ];

      let clicked = false;
      for (const candidate of signInCandidates) {
        if (await candidate.isVisible().catch(() => false)) {
          const [popupMaybe] = await Promise.all([
            page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null),
            candidate.click()
          ]);

          if (popupMaybe) {
            await popupMaybe.waitForLoadState("domcontentloaded");
            // If account selector appears, choose required account when visible.
            const account = popupMaybe.getByText("juanlucasbarbiergarzon@gmail.com").first();
            if (await account.isVisible().catch(() => false)) {
              await account.click();
            }
            await popupMaybe.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);
            await popupMaybe.close().catch(() => undefined);
            await page.bringToFront();
          }

          clicked = true;
          break;
        }
      }

      if (!clicked) {
        throw new Error("Could not locate login button or 'Sign in with Google'.");
      }

      // Confirm main interface + sidebar.
      await settleUi(page);
      await expect(
        page.locator("aside, nav").filter({ hasText: /negocio|dashboard|inicio/i }).first()
      ).toBeVisible({ timeout: 30_000 });
      await screenshot(page, "01_dashboard_loaded");
      markPass("login");
    } catch (error) {
      markFail("login", error);
      throw error;
    }

    // 2) Open Mi Negocio menu.
    try {
      if (await page.getByText("Negocio", { exact: false }).first().isVisible().catch(() => false)) {
        await clickByVisibleText(page, "Negocio", { exact: false });
      }
      await clickByVisibleText(page, "Mi Negocio", { exact: false });
      await assertVisibleText(page, "Agregar Negocio", { exact: false });
      await assertVisibleText(page, "Administrar Negocios", { exact: false });
      await screenshot(page, "02_mi_negocio_expanded");
      markPass("miNegocioMenu");
    } catch (error) {
      markFail("miNegocioMenu", error);
      throw error;
    }

    // 3) Validate Agregar Negocio modal.
    try {
      await clickByVisibleText(page, "Agregar Negocio", { exact: false });
      await assertVisibleText(page, "Crear Nuevo Negocio", { exact: false });
      await expect(page.getByLabel("Nombre del Negocio").first()).toBeVisible({ timeout: 15_000 });
      await assertVisibleText(page, "Tienes 2 de 3 negocios", { exact: false });
      await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible({
        timeout: 10_000
      });
      await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible({
        timeout: 10_000
      });
      await screenshot(page, "03_agregar_negocio_modal");

      const nameInput = page.getByLabel("Nombre del Negocio").first();
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
      await clickByVisibleText(page, "Cancelar");
      markPass("agregarNegocioModal");
    } catch (error) {
      markFail("agregarNegocioModal", error);
      throw error;
    }

    // 4) Open Administrar Negocios.
    try {
      if (!(await page.getByText("Administrar Negocios", { exact: false }).first().isVisible())) {
        await clickByVisibleText(page, "Mi Negocio", { exact: false });
      }
      await clickByVisibleText(page, "Administrar Negocios", { exact: false });
      await assertVisibleText(page, "Información General", { exact: false });
      await assertVisibleText(page, "Detalles de la Cuenta", { exact: false });
      await assertVisibleText(page, "Tus Negocios", { exact: false });
      await assertVisibleText(page, "Sección Legal", { exact: false });
      await screenshot(page, "04_administrar_negocios_page", true);
      markPass("administrarNegociosView");
    } catch (error) {
      markFail("administrarNegociosView", error);
      throw error;
    }

    // 5) Validate Información General.
    try {
      const infoSection = page.locator("section,div").filter({ hasText: /informaci[oó]n general/i }).first();
      await expect(infoSection).toBeVisible({ timeout: 15_000 });
      await assertLikelyUserNameVisible(infoSection);
      await expect(infoSection.getByText(/@/).first()).toBeVisible({ timeout: 15_000 });
      await assertVisibleText(infoSection, "BUSINESS PLAN", { exact: false });
      await expect(infoSection.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible({
        timeout: 10_000
      });
      markPass("informacionGeneral");
    } catch (error) {
      markFail("informacionGeneral", error);
      throw error;
    }

    // 6) Validate Detalles de la Cuenta.
    try {
      await assertVisibleText(page, "Cuenta creada", { exact: false });
      await assertVisibleText(page, "Estado activo", { exact: false });
      await assertVisibleText(page, "Idioma seleccionado", { exact: false });
      markPass("detallesCuenta");
    } catch (error) {
      markFail("detallesCuenta", error);
      throw error;
    }

    // 7) Validate Tus Negocios.
    try {
      await assertVisibleText(page, "Tus Negocios", { exact: false });
      await assertVisibleText(page, "Agregar Negocio", { exact: false });
      await assertVisibleText(page, "Tienes 2 de 3 negocios", { exact: false });
      markPass("tusNegocios");
    } catch (error) {
      markFail("tusNegocios", error);
      throw error;
    }

    // 8) Validate Términos y Condiciones.
    try {
      evidence.finalUrls.terminos = await validateLegalLink(
        page,
        "Términos y Condiciones",
        "Términos y Condiciones"
      );
      markPass("terminos");
    } catch (error) {
      markFail("terminos", error);
      throw error;
    }

    // 9) Validate Política de Privacidad.
    try {
      evidence.finalUrls.politica = await validateLegalLink(
        page,
        "Política de Privacidad",
        "Política de Privacidad"
      );
      markPass("politica");
    } catch (error) {
      markFail("politica", error);
      throw error;
    }
  });
});
