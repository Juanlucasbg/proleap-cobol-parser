const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

const ARTIFACTS_DIR = path.join(__dirname, "..", "artifacts");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "saleads-mi-negocio-report.json");
const CHECKPOINT_DIR = path.join(ARTIFACTS_DIR, "checkpoints");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

function buildInitialReport() {
  return {
    generatedAt: new Date().toISOString(),
    results: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])),
    evidence: {
      screenshots: [],
      urls: {}
    },
    notes: []
  };
}

const TX = {
  google: /google|sign in with google|iniciar sesi[oó]n con google/i,
  negocio: /negocio/i,
  miNegocio: /mi negocio/i,
  agregarNegocio: /agregar negocio/i,
  administrarNegocios: /administrar negocios/i,
  crearNuevoNegocio: /crear nuevo negocio/i,
  nombreNegocio: /nombre del negocio/i,
  limiteNegocios: /tienes\s+2\s+de\s+3\s+negocios/i,
  infoGeneral: /informaci[oó]n general/i,
  detallesCuenta: /detalles de la cuenta/i,
  tusNegocios: /tus negocios/i,
  seccionLegal: /secci[oó]n legal/i,
  terminos: /t[eé]rminos y condiciones/i,
  privacidad: /pol[ií]tica de privacidad/i
};

let report = buildInitialReport();

function ensureDirs() {
  fs.mkdirSync(CHECKPOINT_DIR, { recursive: true });
}

async function checkpoint(page, name, fullPage = false) {
  const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const screenshotPath = path.join(CHECKPOINT_DIR, `${Date.now()}-${safeName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  report.evidence.screenshots.push(screenshotPath);
}

async function waitForUi(page) {
  // Network can stay open in SPA apps; combine load states for resilience.
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 4000 });
  } catch (_err) {
    // Best-effort only.
  }
}

function pass(field) {
  report.results[field] = "PASS";
}

function fail(field, reason) {
  report.results[field] = "FAIL";
  if (reason) {
    report.notes.push(`${field}: ${reason}`);
  }
}

async function clickByText(page, text, options = {}) {
  const target = page.getByText(text).first();
  await target.waitFor({ state: "visible", timeout: options.timeout ?? 20000 });
  await target.click();
  await waitForUi(page);
}

async function clickRoleByName(page, role, name, options = {}) {
  const target = page.getByRole(role, { name }).first();
  await target.waitFor({ state: "visible", timeout: options.timeout ?? 20000 });
  await target.click();
  await waitForUi(page);
}

async function assertVisibleText(page, text, timeout = 20000) {
  await expect(page.getByText(text).first()).toBeVisible({ timeout });
}

async function assertVisibleByPattern(page, pattern, timeout = 20000) {
  await expect(page.getByText(pattern).first()).toBeVisible({ timeout });
}

async function tryClickFirstVisible(candidates, options = {}) {
  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      if (options.beforeClick) {
        options.beforeClick();
      }
      await candidate.click();
      return true;
    }
  }

  return false;
}

async function ensureLoginPageReachable(page) {
  if (page.url() !== "about:blank") {
    return;
  }

  const baseUrl = process.env.SALEADS_BASE_URL;
  if (baseUrl) {
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  report.notes.push(
    "Page started at about:blank and SALEADS_BASE_URL was not provided. " +
      "Set SALEADS_BASE_URL to run this test in a fresh browser context."
  );
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test.afterEach(async () => {
    ensureDirs();
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf-8");
  });

  test("saleads_mi_negocio_full_test", async ({ page, context }) => {
    report = buildInitialReport();
    report.generatedAt = new Date().toISOString();
    ensureDirs();
    await ensureLoginPageReachable(page);
    await waitForUi(page);

    // Step 1: Login with Google
    try {
      const signInCandidates = [
        page.getByRole("button", { name: TX.google }).first(),
        page.getByRole("link", { name: TX.google }).first(),
        page.getByText(TX.google).first()
      ];

      let popupPromise = null;
      const clicked = await tryClickFirstVisible(signInCandidates, {
        beforeClick: () => {
          popupPromise = page.waitForEvent("popup", { timeout: 15000 }).catch(() => null);
        }
      });
      if (!clicked) {
        throw new Error("Google login trigger was not visible.");
      }

      const popup = popupPromise ? await popupPromise : null;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const account = popup
          .getByText("juanlucasbarbiergarzon@gmail.com", { exact: true })
          .first();
        if (await account.isVisible().catch(() => false)) {
          await account.click();
          await waitForUi(popup);
        } else {
          report.notes.push("Google selector opened but target account was not visible.");
        }
      } else {
        // Google flow may happen in same page; continue.
        await waitForUi(page);
      }

      await waitForUi(page);

      // Validate app shell visible (main interface + left sidebar).
      const sidebarCandidate = page.locator("aside, nav").first();
      await expect(sidebarCandidate).toBeVisible({ timeout: 60000 });
      await checkpoint(page, "dashboard-loaded");
      pass("Login");
    } catch (err) {
      fail("Login", String(err));
      throw err;
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByText(page, TX.negocio);
      await clickByText(page, TX.miNegocio);
      await assertVisibleByPattern(page, TX.agregarNegocio);
      await assertVisibleByPattern(page, TX.administrarNegocios);
      await checkpoint(page, "mi-negocio-menu-expanded");
      pass("Mi Negocio menu");
    } catch (err) {
      fail("Mi Negocio menu", String(err));
      throw err;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByText(page, TX.agregarNegocio);
      await assertVisibleByPattern(page, TX.crearNuevoNegocio);
      await expect(
        page.getByLabel(TX.nombreNegocio).or(page.getByPlaceholder(TX.nombreNegocio)).first()
      ).toBeVisible({ timeout: 20000 });
      await assertVisibleByPattern(page, TX.limiteNegocios);
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 20000 });
      await checkpoint(page, "agregar-negocio-modal");

      // Optional actions requested in test definition.
      const nameInput = page
        .getByLabel(TX.nombreNegocio)
        .or(page.getByPlaceholder(TX.nombreNegocio))
        .first();
      await nameInput.fill("Negocio Prueba Automatizacion");
      await clickRoleByName(page, "button", /cancelar/i);
      pass("Agregar Negocio modal");
    } catch (err) {
      fail("Agregar Negocio modal", String(err));
      throw err;
    }

    // Step 4: Open Administrar Negocios
    try {
      // Re-expand if needed
      if (!(await page.getByText(TX.administrarNegocios).first().isVisible().catch(() => false))) {
        if (await page.getByText(TX.miNegocio).first().isVisible().catch(() => false)) {
          await clickByText(page, TX.miNegocio);
        } else {
          await clickByText(page, TX.negocio);
          await clickByText(page, TX.miNegocio);
        }
      }

      await clickByText(page, TX.administrarNegocios);
      await assertVisibleByPattern(page, TX.infoGeneral);
      await assertVisibleByPattern(page, TX.detallesCuenta);
      await assertVisibleByPattern(page, TX.tusNegocios);
      await assertVisibleByPattern(page, TX.seccionLegal);
      await checkpoint(page, "administrar-negocios", true);
      pass("Administrar Negocios view");
    } catch (err) {
      fail("Administrar Negocios view", String(err));
      throw err;
    }

    // Step 5: Validate Informacion General
    try {
      const emailLocator = page.getByText(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/).first();
      await expect(emailLocator).toBeVisible({ timeout: 20000 });

      // Heuristic: at least one visible non-email, non-label text near account area.
      const nameCandidate = page
        .locator("h1, h2, h3, h4, p, span, div")
        .filter({ hasText: /[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}/ })
        .filter({ hasNotText: /informaci[oó]n general|business plan|cambiar plan|@/i })
        .first();
      await expect(nameCandidate).toBeVisible({ timeout: 20000 });

      await assertVisibleByPattern(page, /business plan/i);
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible({ timeout: 20000 });
      pass("Información General");
    } catch (err) {
      fail("Información General", String(err));
      throw err;
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await assertVisibleByPattern(page, /cuenta creada/i);
      await assertVisibleByPattern(page, /estado activo/i);
      await assertVisibleByPattern(page, /idioma seleccionado/i);
      pass("Detalles de la Cuenta");
    } catch (err) {
      fail("Detalles de la Cuenta", String(err));
      throw err;
    }

    // Step 7: Validate Tus Negocios
    try {
      await assertVisibleByPattern(page, TX.tusNegocios);
      await expect(page.getByRole("button", { name: TX.agregarNegocio })).toBeVisible({ timeout: 20000 });
      await assertVisibleByPattern(page, TX.limiteNegocios);
      pass("Tus Negocios");
    } catch (err) {
      fail("Tus Negocios", String(err));
      throw err;
    }

    // Step 8: Validate Terminos y Condiciones
    try {
      const termsTrigger = page.getByText(TX.terminos).first();
      await termsTrigger.waitFor({ state: "visible", timeout: 20000 });

      const originalPage = page;
      const originalUrl = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await termsTrigger.click();
      await waitForUi(page);

      const termsPage = (await popupPromise) || originalPage;
      await termsPage.waitForLoadState("domcontentloaded");
      await waitForUi(termsPage);

      await expect(termsPage.getByText(TX.terminos).first()).toBeVisible({ timeout: 30000 });
      await expect(
        termsPage.locator("p, li").filter({ hasText: /\S.{25,}/ }).first()
      ).toBeVisible({ timeout: 30000 });
      await checkpoint(termsPage, "terminos-y-condiciones", true);
      report.evidence.urls["Términos y Condiciones"] = termsPage.url();

      if (termsPage !== originalPage) {
        await termsPage.close();
        await originalPage.bringToFront();
      } else if (page.url() !== originalUrl) {
        await page.goBack();
        await waitForUi(page);
      }

      pass("Términos y Condiciones");
    } catch (err) {
      fail("Términos y Condiciones", String(err));
      throw err;
    }

    // Step 9: Validate Politica de Privacidad
    try {
      const privacyTrigger = page.getByText(TX.privacidad).first();
      await privacyTrigger.waitFor({ state: "visible", timeout: 20000 });

      const originalPage = page;
      const originalUrl = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await privacyTrigger.click();
      await waitForUi(page);

      const privacyPage = (await popupPromise) || originalPage;
      await privacyPage.waitForLoadState("domcontentloaded");
      await waitForUi(privacyPage);

      await expect(privacyPage.getByText(TX.privacidad).first()).toBeVisible({ timeout: 30000 });
      await expect(
        privacyPage.locator("p, li").filter({ hasText: /\S.{25,}/ }).first()
      ).toBeVisible({ timeout: 30000 });
      await checkpoint(privacyPage, "politica-de-privacidad", true);
      report.evidence.urls["Política de Privacidad"] = privacyPage.url();

      if (privacyPage !== originalPage) {
        await privacyPage.close();
        await originalPage.bringToFront();
      } else if (page.url() !== originalUrl) {
        await page.goBack();
        await waitForUi(page);
      }

      pass("Política de Privacidad");
    } catch (err) {
      fail("Política de Privacidad", String(err));
      throw err;
    }

    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf-8");
  });
});
