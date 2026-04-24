const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const results = {};

const CHECKPOINT_DIR = path.resolve(__dirname, "..", "artifacts", "screenshots");
const REPORT_DIR = path.resolve(__dirname, "..", "artifacts", "reports");
const REPORT_PATH = path.join(REPORT_DIR, "saleads_mi_negocio_full_test_report.json");

const STEP_KEYS = [
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

function ensureDirs() {
  fs.mkdirSync(CHECKPOINT_DIR, { recursive: true });
  fs.mkdirSync(REPORT_DIR, { recursive: true });
}

function sanitizeFileName(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

function markResult(label, status, details = {}) {
  results[label] = {
    ...(results[label] || {}),
    ...details,
    status,
  };
}

async function waitUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
}

async function firstVisibleLocator(page, candidates) {
  for (const candidate of candidates) {
    const locator = page.getByText(candidate, { exact: true }).first();
    if (await locator.isVisible({ timeout: 1000 }).catch(() => false)) {
      return locator;
    }

    const regexLocator = page.getByText(new RegExp(candidate, "i")).first();
    if (await regexLocator.isVisible({ timeout: 1000 }).catch(() => false)) {
      return regexLocator;
    }
  }
  return null;
}

async function clickVisibleText(page, options) {
  const locator = await firstVisibleLocator(page, options);
  if (!locator) {
    throw new Error(`No visible element found for any of: ${options.join(", ")}`);
  }
  await locator.click();
  await waitUi(page);
}

async function screenshot(page, label, fullPage = false) {
  const fileName = `${new Date().toISOString().replace(/[:.]/g, "-")}_${sanitizeFileName(label)}.png`;
  const filePath = path.join(CHECKPOINT_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function validateVisibleTexts(page, checks) {
  for (const text of checks) {
    await expect(page.getByText(new RegExp(text, "i")).first()).toBeVisible({
      timeout: 15000,
    });
  }
}

async function withStep(label, fn) {
  try {
    await fn();
    if (results[label]?.status !== "PASS") {
      markResult(label, "PASS");
    }
    return true;
  } catch (error) {
    markResult(label, "FAIL", { error: error.message });
    return false;
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context }) => {
    ensureDirs();
    for (const key of STEP_KEYS) {
      results[key] = { status: "NOT_RUN" };
    }

    if (process.env.SALEADS_URL) {
      await page.goto(process.env.SALEADS_URL, { waitUntil: "domcontentloaded" });
    }

    // Step 1: Login with Google
    await withStep("Login", async () => {
      await waitUi(page);

      const googlePopupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
      const googlePagePromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);

      await clickVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Google",
        "Login with Google",
      ]);

      const maybePopup = (await googlePopupPromise) || (await googlePagePromise);
      if (maybePopup) {
        await maybePopup.waitForLoadState("domcontentloaded");
        const accountOption = maybePopup.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
        if (await accountOption.isVisible({ timeout: 5000 }).catch(() => false)) {
          await accountOption.click();
          await maybePopup.waitForTimeout(1200);
        }

        const stillOpen = !maybePopup.isClosed();
        if (stillOpen && maybePopup.url() !== page.url()) {
          await maybePopup.close().catch(() => {});
        }
      } else {
        const inlineAccount = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
        if (await inlineAccount.isVisible({ timeout: 5000 }).catch(() => false)) {
          await inlineAccount.click();
        }
      }

      await waitUi(page);

      const appMarker = page.getByRole("navigation").first();
      await expect(appMarker).toBeVisible({ timeout: 30000 });
      await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 30000 });
      const shot = await screenshot(page, "dashboard_loaded_after_google_login");
      markResult("Login", "PASS", { screenshot: shot });
    });

    // Step 2: Open Mi Negocio menu
    await withStep("Mi Negocio menu", async () => {
      await clickVisibleText(page, ["Negocio"]);
      await clickVisibleText(page, ["Mi Negocio"]);
      await validateVisibleTexts(page, ["Agregar Negocio", "Administrar Negocios"]);
      const shot = await screenshot(page, "mi_negocio_expanded_menu");
      markResult("Mi Negocio menu", "PASS", { screenshot: shot });
    });

    // Step 3: Validate Agregar Negocio modal
    await withStep("Agregar Negocio modal", async () => {
      await clickVisibleText(page, ["Agregar Negocio"]);
      await validateVisibleTexts(page, [
        "Crear Nuevo Negocio",
        "Nombre del Negocio",
        "Tienes 2 de 3 negocios",
        "Cancelar",
        "Crear Negocio",
      ]);

      const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
      if (await businessNameInput.isVisible({ timeout: 3000 }).catch(() => false)) {
        await businessNameInput.click();
        await businessNameInput.fill("Negocio Prueba Automatización");
      } else {
        const fallbackInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
        if (await fallbackInput.isVisible({ timeout: 3000 }).catch(() => false)) {
          await fallbackInput.click();
          await fallbackInput.fill("Negocio Prueba Automatización");
        }
      }

      const shot = await screenshot(page, "agregar_negocio_modal");
      markResult("Agregar Negocio modal", "PASS", { screenshot: shot });
      await clickVisibleText(page, ["Cancelar"]);
    });

    // Step 4: Open Administrar Negocios
    await withStep("Administrar Negocios view", async () => {
      const administrarLink = page.getByText(/Administrar Negocios/i).first();
      if (!(await administrarLink.isVisible({ timeout: 2000 }).catch(() => false))) {
        await clickVisibleText(page, ["Mi Negocio"]);
      }
      await clickVisibleText(page, ["Administrar Negocios"]);
      await validateVisibleTexts(page, [
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Sección Legal",
      ]);
      const shot = await screenshot(page, "administrar_negocios_account_page", true);
      markResult("Administrar Negocios view", "PASS", { screenshot: shot });
    });

    // Step 5: Validate Información General
    await withStep("Información General", async () => {
      const infoSection = page.getByText(/Información General/i).first();
      await expect(infoSection).toBeVisible({ timeout: 15000 });

      const emailVisible = page.getByText(/@/).first();
      await expect(emailVisible).toBeVisible({ timeout: 15000 });
      await validateVisibleTexts(page, ["BUSINESS PLAN", "Cambiar Plan"]);

      const maybeKnownName = page.getByText(/juan|lucas|barbier|garzon/i).first();
      const hasKnownName = await maybeKnownName.isVisible({ timeout: 3000 }).catch(() => false);
      if (!hasKnownName) {
        const infoContainer = page.locator("section,div").filter({ hasText: /Información General/i }).first();
        const infoText = await infoContainer.innerText();
        const lines = infoText
          .split("\n")
          .map((line) => line.trim())
          .filter(Boolean);
        const hasLikelyName = lines.some(
          (line) =>
            /^[A-Za-zÁÉÍÓÚÑáéíóúñ' -]{4,}$/.test(line) &&
            !/@/.test(line) &&
            !/informaci[oó]n general|business plan|cambiar plan|cuenta|estado|idioma|negocios|legal/i.test(line),
        );
        expect(hasLikelyName).toBeTruthy();
      }
    });

    // Step 6: Validate Detalles de la Cuenta
    await withStep("Detalles de la Cuenta", async () => {
      await validateVisibleTexts(page, ["Cuenta creada", "Estado activo", "Idioma seleccionado"]);
    });

    // Step 7: Validate Tus Negocios
    await withStep("Tus Negocios", async () => {
      await validateVisibleTexts(page, ["Tus Negocios", "Agregar Negocio", "Tienes 2 de 3 negocios"]);
    });

    // Step 8: Validate Términos y Condiciones
    await withStep("Términos y Condiciones", async () => {
      const appUrl = page.url();
      const [targetPage] = await Promise.all([
        context.waitForEvent("page", { timeout: 10000 }).catch(() => null),
        clickVisibleText(page, ["Términos y Condiciones"]),
      ]);

      const legalPage = targetPage || page;
      await legalPage.waitForLoadState("domcontentloaded");
      await expect(legalPage.getByText(/Términos y Condiciones/i).first()).toBeVisible({
        timeout: 15000,
      });

      const bodyText = legalPage.locator("body");
      await expect(bodyText).toContainText(/Términos|Condiciones|Legal|Uso/i);

      const shot = await screenshot(legalPage, "terminos_y_condiciones_page");
      markResult("Términos y Condiciones", "PASS", {
        screenshot: shot,
        finalUrl: legalPage.url(),
      });

      if (targetPage) {
        await targetPage.close().catch(() => {});
      } else if (page.url() !== appUrl) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
          await page.goto(appUrl, { waitUntil: "domcontentloaded" });
        });
      }
      await page.bringToFront();
      await waitUi(page);
    });

    // Step 9: Validate Política de Privacidad
    await withStep("Política de Privacidad", async () => {
      const appUrl = page.url();
      const [targetPage] = await Promise.all([
        context.waitForEvent("page", { timeout: 10000 }).catch(() => null),
        clickVisibleText(page, ["Política de Privacidad"]),
      ]);

      const legalPage = targetPage || page;
      await legalPage.waitForLoadState("domcontentloaded");
      await expect(legalPage.getByText(/Política de Privacidad/i).first()).toBeVisible({
        timeout: 15000,
      });

      const bodyText = legalPage.locator("body");
      await expect(bodyText).toContainText(/Privacidad|Datos|Legal|Información/i);

      const shot = await screenshot(legalPage, "politica_de_privacidad_page");
      markResult("Política de Privacidad", "PASS", {
        screenshot: shot,
        finalUrl: legalPage.url(),
      });

      if (targetPage) {
        await targetPage.close().catch(() => {});
      } else if (page.url() !== appUrl) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
          await page.goto(appUrl, { waitUntil: "domcontentloaded" });
        });
      }
      await page.bringToFront();
      await waitUi(page);
    });

    const failedSteps = STEP_KEYS.filter((key) => results[key]?.status === "FAIL");
    expect(failedSteps, `Failed steps: ${failedSteps.join(", ")}`).toEqual([]);
  });

  test.afterAll(async () => {
    ensureDirs();
    const ordered = {};
    for (const key of STEP_KEYS) {
      ordered[key] = results[key] || { status: "NOT_RUN" };
    }
    fs.writeFileSync(REPORT_PATH, JSON.stringify(ordered, null, 2), "utf8");
    // eslint-disable-next-line no-console
    console.log(`Final report written to: ${REPORT_PATH}`);
    // eslint-disable-next-line no-console
    console.table(
      STEP_KEYS.map((key) => ({
        step: key,
        status: ordered[key].status,
        finalUrl: ordered[key].finalUrl || "",
      })),
    );
  });
});
