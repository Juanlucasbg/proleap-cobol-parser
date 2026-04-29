const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const ARTIFACTS_DIR = process.env.E2E_ARTIFACTS_DIR || "artifacts";
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "saleads_mi_negocio_full_report.json");
const TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";
const EXPECTED_GOOGLE_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL;

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

const stepResults = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));

function ensureArtifactsFolders() {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

async function saveCheckpointScreenshot(page, fileName, options = {}) {
  ensureArtifactsFolders();
  const absolutePath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: absolutePath, fullPage: Boolean(options.fullPage) });
  return absolutePath;
}

function normalizeTextSelector(value) {
  return value.replace(/\s+/g, " ").trim();
}

async function clickFirstVisibleByTexts(page, texts) {
  for (const text of texts) {
    const locator = page.getByText(text, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      await page.waitForLoadState("domcontentloaded");
      return text;
    }
  }
  throw new Error(`No visible element found for texts: ${texts.join(", ")}`);
}

async function waitForAnyVisibleText(page, texts, timeout = 20000) {
  for (const text of texts) {
    const locator = page.getByText(text, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      return text;
    }
  }

  const startedAt = Date.now();
  while (Date.now() - startedAt < timeout) {
    for (const text of texts) {
      const locator = page.getByText(text, { exact: false }).first();
      if (await locator.isVisible().catch(() => false)) {
        return text;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`None of the texts became visible: ${texts.join(", ")}`);
}

async function assertTextsVisible(page, texts) {
  for (const text of texts) {
    await expect(page.getByText(text, { exact: false }).first()).toBeVisible();
  }
}

async function assertAtLeastOneTextVisible(page, textCandidates) {
  await waitForAnyVisibleText(page, textCandidates);
}

async function assertTextGroupsVisible(page, groups) {
  for (const candidates of groups) {
    await assertAtLeastOneTextVisible(page, candidates);
  }
}

async function clickSidebarEntry(page, candidates) {
  for (const candidate of candidates) {
    const normalized = normalizeTextSelector(candidate);
    const exactLocator = page.locator("aside").getByText(normalized, { exact: true }).first();
    if (await exactLocator.isVisible().catch(() => false)) {
      await exactLocator.click();
      await page.waitForLoadState("domcontentloaded");
      return normalized;
    }

    const fuzzyLocator = page.locator("aside").getByText(normalized, { exact: false }).first();
    if (await fuzzyLocator.isVisible().catch(() => false)) {
      await fuzzyLocator.click();
      await page.waitForLoadState("domcontentloaded");
      return normalized;
    }
  }

  throw new Error(`Could not click sidebar entry for candidates: ${candidates.join(", ")}`);
}

async function clickLegalLinkAndValidate({ page, linkTexts, titleTexts, reportKey, screenshotFile }) {
  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  const clickedLabel = await clickFirstVisibleByTexts(page, linkTexts);
  const popup = await popupPromise;

  const targetPage = popup || page;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
  } else {
    await page.waitForLoadState("domcontentloaded");
  }

  await waitForAnyVisibleText(targetPage, titleTexts, 25000);
  await saveCheckpointScreenshot(targetPage, screenshotFile, { fullPage: true });

  const url = targetPage.url();
  expect(url).toBeTruthy();
  stepResults[reportKey] = "PASS";

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await page.waitForLoadState("domcontentloaded");
  }

  return { clickedLabel, url };
}

async function doGoogleLogin(page) {
  const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
  const loginButtonText = await clickFirstVisibleByTexts(page, [
    "Sign in with Google",
    "Iniciar sesión con Google",
    "Iniciar sesion con Google",
    "Ingresar con Google",
    "Login with Google",
    "Continuar con Google",
    "Google"
  ]);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    const googleAccountOption = popup.getByText(EXPECTED_GOOGLE_EMAIL, { exact: false }).first();
    if (await googleAccountOption.isVisible().catch(() => false)) {
      await googleAccountOption.click();
    }

    const continueButton = popup.getByRole("button", { name: /continuar|continue|siguiente|next/i }).first();
    if (await continueButton.isVisible().catch(() => false)) {
      await continueButton.click();
    }

    await popup.waitForTimeout(1000);
  } else {
    const googleAccountOption = page.getByText(EXPECTED_GOOGLE_EMAIL, { exact: false }).first();
    if (await googleAccountOption.isVisible().catch(() => false)) {
      await googleAccountOption.click();
      await page.waitForLoadState("domcontentloaded");
    }
  }

  return loginButtonText;
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page }) => {
    const legalUrls = {};
    let loginButtonText = null;
    try {
      if (SALEADS_LOGIN_URL) {
        await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
      }

      // Step 1: Login with Google and validate dashboard/sidebar
      loginButtonText = await doGoogleLogin(page);
      await waitForAnyVisibleText(page, ["Negocio", "Mi Negocio", "Dashboard", "Inicio"], 45000);
      await expect(page.locator("aside").first()).toBeVisible();
      await saveCheckpointScreenshot(page, "01_dashboard_loaded.png", { fullPage: true });
      stepResults["Login"] = "PASS";

      // Step 2: Open Mi Negocio menu
      await clickSidebarEntry(page, ["Negocio"]);
      await clickSidebarEntry(page, ["Mi Negocio"]);
      await assertTextGroupsVisible(page, [["Agregar Negocio"], ["Administrar Negocios"]]);
      await saveCheckpointScreenshot(page, "02_mi_negocio_menu_expanded.png");
      stepResults["Mi Negocio menu"] = "PASS";

      // Step 3: Validate Agregar Negocio modal
      await clickFirstVisibleByTexts(page, ["Agregar Negocio"]);
      await assertTextGroupsVisible(page, [
        ["Crear Nuevo Negocio"],
        ["Nombre del Negocio"],
        ["Tienes 2 de 3 negocios"],
        ["Cancelar"],
        ["Crear Negocio"]
      ]);
      const businessNameInput = page.getByRole("textbox", { name: /Nombre del Negocio/i }).first();
      if (await businessNameInput.isVisible().catch(() => false)) {
        await businessNameInput.click();
        await businessNameInput.fill(TEST_BUSINESS_NAME);
      }
      await saveCheckpointScreenshot(page, "03_agregar_negocio_modal.png");
      await clickFirstVisibleByTexts(page, ["Cancelar"]);
      stepResults["Agregar Negocio modal"] = "PASS";

      // Step 4: Open Administrar Negocios
      await clickSidebarEntry(page, ["Mi Negocio"]);
      await clickFirstVisibleByTexts(page, ["Administrar Negocios"]);
      await page.waitForLoadState("domcontentloaded");
      await assertTextGroupsVisible(page, [
        ["Información General", "Informacion General"],
        ["Detalles de la Cuenta"],
        ["Tus Negocios"],
        ["Sección Legal", "Seccion Legal"]
      ]);
      await saveCheckpointScreenshot(page, "04_administrar_negocios_full.png", { fullPage: true });
      stepResults["Administrar Negocios view"] = "PASS";

      // Step 5: Validate Información General
      await assertTextGroupsVisible(page, [["BUSINESS PLAN"], ["Cambiar Plan"]]);
      const mainText = await page.locator("body").innerText();
      expect(mainText).toContain("@");
      stepResults["Información General"] = "PASS";

      // Step 6: Validate Detalles de la Cuenta
      await assertTextGroupsVisible(page, [["Cuenta creada"], ["Estado activo"], ["Idioma seleccionado"]]);
      stepResults["Detalles de la Cuenta"] = "PASS";

      // Step 7: Validate Tus Negocios
      await assertTextGroupsVisible(page, [["Tus Negocios"], ["Agregar Negocio"], ["Tienes 2 de 3 negocios"]]);
      stepResults["Tus Negocios"] = "PASS";

      // Step 8: Validate Términos y Condiciones
      legalUrls["Términos y Condiciones"] = await clickLegalLinkAndValidate({
        page,
        linkTexts: ["Términos y Condiciones", "Terminos y Condiciones"],
        titleTexts: ["Términos y Condiciones", "Terminos y Condiciones"],
        reportKey: "Términos y Condiciones",
        screenshotFile: "05_terminos_y_condiciones.png"
      });

      // Step 9: Validate Política de Privacidad
      legalUrls["Política de Privacidad"] = await clickLegalLinkAndValidate({
        page,
        linkTexts: ["Política de Privacidad", "Politica de Privacidad"],
        titleTexts: ["Política de Privacidad", "Politica de Privacidad"],
        reportKey: "Política de Privacidad",
        screenshotFile: "06_politica_de_privacidad.png"
      });
    } finally {
      // Step 10: Final report (PASS/FAIL per validation block)
      ensureArtifactsFolders();
      fs.writeFileSync(
        REPORT_PATH,
        JSON.stringify(
          {
            testName: "saleads_mi_negocio_full_test",
            loginUrlUsed: SALEADS_LOGIN_URL || "CURRENT_PAGE",
            loginActionUsed: loginButtonText,
            googleAccountExpected: EXPECTED_GOOGLE_EMAIL,
            results: stepResults,
            legalUrls,
            generatedAt: new Date().toISOString()
          },
          null,
          2
        ),
        "utf-8"
      );
    }
  });
});
