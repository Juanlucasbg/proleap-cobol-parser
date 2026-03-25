const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const ARTIFACTS_DIR = path.resolve(__dirname, "..", "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "saleads-mi-negocio-report.json");

function normalizeText(value) {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

function textContains(haystack, needle) {
  return normalizeText(haystack).includes(normalizeText(needle));
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
}

async function clickByText(page, textCandidates) {
  for (const text of textCandidates) {
    const locator = page.getByText(text, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      await waitForUiToLoad(page);
      return text;
    }
  }

  throw new Error(`Unable to click any candidate text: ${textCandidates.join(", ")}`);
}

async function clickByTextAndMaybeOpenPopup(page, textCandidates) {
  let clickedText = "";
  let popupPage = null;
  for (const text of textCandidates) {
    const locator = page.getByText(text, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      const popupPromise = page.context().waitForEvent("page", { timeout: 5000 }).catch(() => null);
      await locator.click();
      clickedText = text;
      popupPage = await popupPromise;
      if (popupPage) {
        await popupPage.waitForLoadState("domcontentloaded");
      } else {
        await waitForUiToLoad(page);
      }
      return { clickedText, popupPage };
    }
  }

  throw new Error(`Unable to click any candidate text: ${textCandidates.join(", ")}`);
}

async function ensureVisibleText(page, textCandidates) {
  for (const text of textCandidates) {
    const locator = page.getByText(text, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      return text;
    }
  }

  throw new Error(`None of the expected texts are visible: ${textCandidates.join(", ")}`);
}

async function maybeSelectGoogleAccount(page) {
  const googleAccount = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false });
  if (await googleAccount.first().isVisible({ timeout: 6000 }).catch(() => false)) {
    await googleAccount.first().click();
    await waitForUiToLoad(page);
  }
}

async function capture(page, fileName) {
  await page.screenshot({
    path: path.join(SCREENSHOTS_DIR, fileName),
    fullPage: true
  });
}

async function openLegalLinkAndValidate(page, linkText, expectedHeading, screenshotName, resultBucket) {
  const beforePages = page.context().pages().length;
  await clickByText(page, [linkText]);

  let legalPage = page;
  if (page.context().pages().length > beforePages) {
    legalPage = page.context().pages()[page.context().pages().length - 1];
    await legalPage.bringToFront();
  }

  await waitForUiToLoad(legalPage);

  const pageText = await legalPage.locator("body").innerText();
  const headingOk = textContains(pageText, expectedHeading);
  const contentOk = pageText.trim().length > 50;

  if (!headingOk) {
    throw new Error(`${expectedHeading} heading not found on legal page`);
  }
  if (!contentOk) {
    throw new Error(`Legal content not visible for ${linkText}`);
  }

  await capture(legalPage, screenshotName);
  resultBucket.url = legalPage.url();

  if (legalPage !== page) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
  const saleadsUrl = process.env.SALEADS_URL;

  const report = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    pass: true,
    results: {
      Login: "FAIL",
      "Mi Negocio menu": "FAIL",
      "Agregar Negocio modal": "FAIL",
      "Administrar Negocios view": "FAIL",
      "Información General": "FAIL",
      "Detalles de la Cuenta": "FAIL",
      "Tus Negocios": "FAIL",
      "Términos y Condiciones": "FAIL",
      "Política de Privacidad": "FAIL"
    },
    evidence: {
      dashboardScreenshot: "",
      expandedMenuScreenshot: "",
      agregarNegocioModalScreenshot: "",
      administrarNegociosScreenshot: "",
      terminosScreenshot: "",
      terminosUrl: "",
      privacidadScreenshot: "",
      privacidadUrl: ""
    },
    errors: []
  };

  async function markSuccess(key) {
    report.results[key] = "PASS";
  }

  async function markFailure(key, error) {
    report.results[key] = "FAIL";
    report.pass = false;
    report.errors.push(`[${key}] ${error.message}`);
  }

  // Step 1: Login with Google.
  try {
    if (saleadsUrl && !/^https?:\/\//i.test(page.url())) {
      await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUiToLoad(page);
    const { popupPage } = await clickByTextAndMaybeOpenPopup(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Login with Google",
      "Acceder con Google"
    ]);
    if (popupPage) {
      await maybeSelectGoogleAccount(popupPage);
      await popupPage.close().catch(() => null);
      await page.bringToFront();
    } else {
      await maybeSelectGoogleAccount(page);
    }
    await waitForUiToLoad(page);

    // Validate main app interface + left sidebar.
    await ensureVisibleText(page, ["Negocio", "Dashboard", "Inicio", "Mi Negocio"]);
    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 30000 });

    await capture(page, "01-dashboard-loaded.png");
    report.evidence.dashboardScreenshot = "artifacts/screenshots/01-dashboard-loaded.png";
    await markSuccess("Login");
  } catch (error) {
    await markFailure("Login", error);
  }

  // Step 2: Open Mi Negocio menu.
  try {
    await clickByText(page, ["Negocio"]);
    await clickByText(page, ["Mi Negocio"]);
    await ensureVisibleText(page, ["Agregar Negocio"]);
    await ensureVisibleText(page, ["Administrar Negocios"]);

    await capture(page, "02-mi-negocio-expanded-menu.png");
    report.evidence.expandedMenuScreenshot = "artifacts/screenshots/02-mi-negocio-expanded-menu.png";
    await markSuccess("Mi Negocio menu");
  } catch (error) {
    await markFailure("Mi Negocio menu", error);
  }

  // Step 3: Validate Agregar Negocio modal.
  try {
    await clickByText(page, ["Agregar Negocio"]);
    await ensureVisibleText(page, ["Crear Nuevo Negocio"]);
    await ensureVisibleText(page, ["Nombre del Negocio"]);
    await ensureVisibleText(page, ["Tienes 2 de 3 negocios"]);
    await ensureVisibleText(page, ["Cancelar"]);
    await ensureVisibleText(page, ["Crear Negocio"]);

    await capture(page, "03-agregar-negocio-modal.png");
    report.evidence.agregarNegocioModalScreenshot = "artifacts/screenshots/03-agregar-negocio-modal.png";

    const businessNameInput = page.getByLabel("Nombre del Negocio", { exact: false }).first();
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.fill("Negocio Prueba Automatizacion");
    }
    await clickByText(page, ["Cancelar"]);
    await waitForUiToLoad(page);
    await markSuccess("Agregar Negocio modal");
  } catch (error) {
    await markFailure("Agregar Negocio modal", error);
  }

  // Step 4: Open Administrar Negocios.
  try {
    // Re-expand if collapsed.
    if (!(await page.getByText("Administrar Negocios", { exact: false }).first().isVisible().catch(() => false))) {
      await clickByText(page, ["Mi Negocio"]);
    }
    await clickByText(page, ["Administrar Negocios"]);
    await waitForUiToLoad(page);

    await ensureVisibleText(page, ["Información General", "Informacion General"]);
    await ensureVisibleText(page, ["Detalles de la Cuenta"]);
    await ensureVisibleText(page, ["Tus Negocios"]);
    await ensureVisibleText(page, ["Sección Legal", "Seccion Legal"]);

    await capture(page, "04-administrar-negocios-page.png");
    report.evidence.administrarNegociosScreenshot = "artifacts/screenshots/04-administrar-negocios-page.png";
    await markSuccess("Administrar Negocios view");
  } catch (error) {
    await markFailure("Administrar Negocios view", error);
  }

  // Step 5: Validate Información General.
  try {
    await ensureVisibleText(page, ["BUSINESS PLAN"]);
    await ensureVisibleText(page, ["Cambiar Plan"]);
    const pageText = await page.locator("body").innerText();

    const hasEmail = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i.test(pageText);
    const hasLikelyName =
      pageText
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean)
        .some((line) => line.length >= 4 && line.length <= 40 && /^[A-Za-zÀ-ÿ' -]+$/.test(line));

    if (!hasEmail) {
      throw new Error("User email not visible in Informacion General");
    }
    if (!hasLikelyName) {
      throw new Error("User name not detected in Informacion General");
    }

    await markSuccess("Información General");
  } catch (error) {
    await markFailure("Información General", error);
  }

  // Step 6: Validate Detalles de la Cuenta.
  try {
    await ensureVisibleText(page, ["Cuenta creada"]);
    await ensureVisibleText(page, ["Estado activo", "Activo"]);
    await ensureVisibleText(page, ["Idioma seleccionado", "Idioma"]);
    await markSuccess("Detalles de la Cuenta");
  } catch (error) {
    await markFailure("Detalles de la Cuenta", error);
  }

  // Step 7: Validate Tus Negocios.
  try {
    await ensureVisibleText(page, ["Tus Negocios"]);
    await ensureVisibleText(page, ["Agregar Negocio"]);
    await ensureVisibleText(page, ["Tienes 2 de 3 negocios"]);
    await markSuccess("Tus Negocios");
  } catch (error) {
    await markFailure("Tus Negocios", error);
  }

  // Step 8: Validate Términos y Condiciones.
  try {
    const terminosEvidence = {};
    await openLegalLinkAndValidate(
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05-terminos-y-condiciones.png",
      terminosEvidence
    );
    report.evidence.terminosScreenshot = "artifacts/screenshots/05-terminos-y-condiciones.png";
    report.evidence.terminosUrl = terminosEvidence.url || "";
    await markSuccess("Términos y Condiciones");
  } catch (error) {
    await markFailure("Términos y Condiciones", error);
  }

  // Step 9: Validate Política de Privacidad.
  try {
    const privacidadEvidence = {};
    await openLegalLinkAndValidate(
      page,
      "Política de Privacidad",
      "Política de Privacidad",
      "06-politica-de-privacidad.png",
      privacidadEvidence
    );
    report.evidence.privacidadScreenshot = "artifacts/screenshots/06-politica-de-privacidad.png";
    report.evidence.privacidadUrl = privacidadEvidence.url || "";
    await markSuccess("Política de Privacidad");
  } catch (error) {
    await markFailure("Política de Privacidad", error);
  }

  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");

  // Step 10: Final report pass/fail should fail the test if any step failed.
  if (!report.pass) {
    throw new Error(`One or more workflow validations failed. See ${REPORT_PATH}`);
  }
});
