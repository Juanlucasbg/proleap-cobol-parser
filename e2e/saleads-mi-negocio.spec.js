const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const SCREENSHOT_DIR = path.join(process.cwd(), "artifacts", "saleads-mi-negocio");
const REPORT_DIR = path.join(process.cwd(), "artifacts");
const REPORT_PATH = path.join(REPORT_DIR, "saleads-mi-negocio-report.json");

function ensureArtifactsDir() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  fs.mkdirSync(REPORT_DIR, { recursive: true });
}

async function waitForUi(contextPage) {
  await contextPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await contextPage.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
  await contextPage.waitForTimeout(800);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }

  return null;
}

async function clickFirstVisible(candidates, clickOptions) {
  const locator = await firstVisibleLocator(candidates);
  if (!locator) {
    return false;
  }

  await locator.click(clickOptions);
  return true;
}

function passFail(ok, details) {
  return {
    status: ok ? "PASS" : "FAIL",
    details,
  };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  ensureArtifactsDir();

  const baseLoginUrl = process.env.SALEADS_LOGIN_URL;
  if (!baseLoginUrl) {
    throw new Error(
      "SALEADS_LOGIN_URL is required. Provide the environment login URL so this test can run in dev/staging/production without hardcoded domains."
    );
  }

  const report = {
    Login: passFail(false, "Not executed"),
    "Mi Negocio menu": passFail(false, "Not executed"),
    "Agregar Negocio modal": passFail(false, "Not executed"),
    "Administrar Negocios view": passFail(false, "Not executed"),
    "Informacion General": passFail(false, "Not executed"),
    "Detalles de la Cuenta": passFail(false, "Not executed"),
    "Tus Negocios": passFail(false, "Not executed"),
    "Terminos y Condiciones": passFail(false, "Not executed"),
    "Politica de Privacidad": passFail(false, "Not executed"),
    evidence: {
      screenshots: [],
      finalUrls: {},
    },
  };

  const addEvidenceScreenshot = async (name, targetPage = page, fullPage = false) => {
    const cleanName = name.replace(/[^a-zA-Z0-9-_]/g, "_");
    const screenshotPath = path.join(SCREENSHOT_DIR, `${cleanName}.png`);
    await targetPage.screenshot({ path: screenshotPath, fullPage });
    report.evidence.screenshots.push(screenshotPath);
  };

  const assertVisible = async (label, candidates) => {
    const locator = await firstVisibleLocator(candidates);
    expect(locator, `${label} should be visible`).not.toBeNull();
    await expect(locator).toBeVisible({ timeout: 20000 });
    return locator;
  };

  await page.goto(baseLoginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  // Step 1: Login with Google.
  const popupPromise = page.waitForEvent("popup", { timeout: 12000 }).catch(() => null);
  const clickedGoogleLogin = await clickFirstVisible(
    [
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[o\u00f3]n con google/i),
      page.locator("button:has-text('Google')"),
      page.locator("a:has-text('Google')"),
    ],
    { timeout: 15000 }
  );

  expect(clickedGoogleLogin, "Google login trigger should be clickable").toBeTruthy();
  await waitForUi(page);

  let googlePage = await popupPromise;
  if (!googlePage && /accounts\.google\.com/i.test(page.url())) {
    googlePage = page;
  }

  if (googlePage) {
    await waitForUi(googlePage);
    await clickFirstVisible(
      [
        googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
        googlePage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        googlePage.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      ],
      { timeout: 15000 }
    );
    await waitForUi(googlePage);
  }

  await assertVisible("main application interface", [page.locator("main"), page.locator("[role='main']"), page.locator("body")]);
  await assertVisible("left sidebar navigation", [page.locator("aside"), page.locator("nav"), page.getByText(/negocio/i)]);
  report.Login = passFail(true, "Application dashboard and sidebar became visible after Google login flow.");
  await addEvidenceScreenshot("01_dashboard_loaded");

  // Step 2: Open Mi Negocio menu.
  const clickedNegocio = await clickFirstVisible(
    [
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i),
    ],
    { timeout: 15000 }
  );
  expect(clickedNegocio, "Negocio section should be clickable").toBeTruthy();
  await waitForUi(page);

  const clickedMiNegocio = await clickFirstVisible(
    [
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i),
    ],
    { timeout: 15000 }
  );
  expect(clickedMiNegocio, "Mi Negocio option should be clickable").toBeTruthy();
  await waitForUi(page);

  await assertVisible("Agregar Negocio", [page.getByText(/^agregar negocio$/i), page.getByRole("button", { name: /^agregar negocio$/i }), page.getByRole("link", { name: /^agregar negocio$/i })]);
  await assertVisible("Administrar Negocios", [page.getByText(/^administrar negocios$/i), page.getByRole("button", { name: /^administrar negocios$/i }), page.getByRole("link", { name: /^administrar negocios$/i })]);
  report["Mi Negocio menu"] = passFail(true, "Mi Negocio submenu expanded and expected entries were visible.");
  await addEvidenceScreenshot("02_mi_negocio_menu_expanded");

  // Step 3: Validate Agregar Negocio modal.
  const clickedAgregarNegocio = await clickFirstVisible(
    [
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i),
    ],
    { timeout: 15000 }
  );
  expect(clickedAgregarNegocio, "Agregar Negocio should be clickable").toBeTruthy();
  await waitForUi(page);

  await assertVisible("Crear Nuevo Negocio modal title", [page.getByText(/^crear nuevo negocio$/i), page.getByRole("heading", { name: /^crear nuevo negocio$/i })]);
  const nombreNegocioInput = await assertVisible("Nombre del Negocio input", [page.getByPlaceholder(/nombre del negocio/i), page.getByLabel(/nombre del negocio/i), page.locator("input[name*='nombre' i]")]);
  await assertVisible("Tienes 2 de 3 negocios text", [page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)]);
  await assertVisible("Cancelar button", [page.getByRole("button", { name: /^cancelar$/i }), page.getByText(/^cancelar$/i)]);
  await assertVisible("Crear Negocio button", [page.getByRole("button", { name: /^crear negocio$/i }), page.getByText(/^crear negocio$/i)]);

  await nombreNegocioInput.click({ timeout: 10000 });
  await nombreNegocioInput.fill("Negocio Prueba Automatizacion");
  await clickFirstVisible([page.getByRole("button", { name: /^cancelar$/i }), page.getByText(/^cancelar$/i)], { timeout: 10000 });
  await waitForUi(page);
  report["Agregar Negocio modal"] = passFail(true, "Agregar Negocio modal showed title, fields, counters and action buttons.");
  await addEvidenceScreenshot("03_agregar_negocio_modal");

  // Step 4: Open Administrar Negocios.
  await clickFirstVisible(
    [
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i),
    ],
    { timeout: 10000 }
  );
  await waitForUi(page);

  const clickedAdministrarNegocios = await clickFirstVisible(
    [
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByText(/^administrar negocios$/i),
    ],
    { timeout: 15000 }
  );
  expect(clickedAdministrarNegocios, "Administrar Negocios should be clickable").toBeTruthy();
  await waitForUi(page);

  await assertVisible("Informacion General section", [page.getByRole("heading", { name: /^informaci[o\u00f3]n general$/i }), page.getByText(/^informaci[o\u00f3]n general$/i)]);
  await assertVisible("Detalles de la Cuenta section", [page.getByRole("heading", { name: /^detalles de la cuenta$/i }), page.getByText(/^detalles de la cuenta$/i)]);
  await assertVisible("Tus Negocios section", [page.getByRole("heading", { name: /^tus negocios$/i }), page.getByText(/^tus negocios$/i)]);
  await assertVisible("Seccion Legal section", [page.getByRole("heading", { name: /^secci[o\u00f3]n legal$/i }), page.getByText(/^secci[o\u00f3]n legal$/i)]);
  report["Administrar Negocios view"] = passFail(true, "Account management view displayed all required sections.");
  await addEvidenceScreenshot("04_administrar_negocios_page", page, true);

  // Step 5: Validate Informacion General.
  await assertVisible("User name", [page.locator("[data-testid*='name' i]"), page.locator("text=/@/").locator("..")]);
  await assertVisible("User email", [page.getByText(/@/, { exact: false })]);
  await assertVisible("BUSINESS PLAN text", [page.getByText(/business plan/i)]);
  await assertVisible("Cambiar Plan button", [page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)]);
  report["Informacion General"] = passFail(true, "User identity and plan controls were visible.");

  // Step 6: Validate Detalles de la Cuenta.
  await assertVisible("Cuenta creada", [page.getByText(/cuenta creada/i)]);
  await assertVisible("Estado activo", [page.getByText(/estado activo/i)]);
  await assertVisible("Idioma seleccionado", [page.getByText(/idioma seleccionado/i)]);
  report["Detalles de la Cuenta"] = passFail(true, "Account detail labels were visible.");

  // Step 7: Validate Tus Negocios.
  await assertVisible("Business list", [page.locator("ul, table, [role='list'], [role='table']").filter({ hasText: /negocio/i }), page.getByText(/tus negocios/i).locator("..")]);
  await assertVisible("Agregar Negocio button in businesses", [page.getByRole("button", { name: /^agregar negocio$/i }), page.getByText(/^agregar negocio$/i)]);
  await assertVisible("Tienes 2 de 3 negocios in businesses", [page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)]);
  report["Tus Negocios"] = passFail(true, "Business area and limits were visible.");

  // Step 8: Validate Terminos y Condiciones.
  const termsPopupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
  const clickedTerms = await clickFirstVisible(
    [
      page.getByRole("link", { name: /t[e\u00e9]rminos y condiciones/i }),
      page.getByText(/t[e\u00e9]rminos y condiciones/i),
    ],
    { timeout: 15000 }
  );
  expect(clickedTerms, "Terminos y Condiciones link should be clickable").toBeTruthy();
  await waitForUi(page);

  let termsPage = await termsPopupPromise;
  if (!termsPage) {
    termsPage = page;
  }

  await waitForUi(termsPage);
  await assertVisible("Terminos y Condiciones heading", [termsPage.getByRole("heading", { name: /t[e\u00e9]rminos y condiciones/i }), termsPage.getByText(/t[e\u00e9]rminos y condiciones/i)]);
  await assertVisible("Legal content in terms page", [termsPage.locator("p, article, main")]);
  report.evidence.finalUrls.terms = termsPage.url();
  report["Terminos y Condiciones"] = passFail(true, "Terms page was opened and legal content was visible.");
  await addEvidenceScreenshot("05_terminos_y_condiciones", termsPage, true);

  if (termsPage !== page) {
    await termsPage.close();
    await page.bringToFront();
    await waitForUi(page);
  }

  // Step 9: Validate Politica de Privacidad.
  const privacyPopupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
  const clickedPrivacy = await clickFirstVisible(
    [
      page.getByRole("link", { name: /pol[i\u00ed]tica de privacidad/i }),
      page.getByText(/pol[i\u00ed]tica de privacidad/i),
    ],
    { timeout: 15000 }
  );
  expect(clickedPrivacy, "Politica de Privacidad link should be clickable").toBeTruthy();
  await waitForUi(page);

  let privacyPage = await privacyPopupPromise;
  if (!privacyPage) {
    privacyPage = page;
  }

  await waitForUi(privacyPage);
  await assertVisible("Politica de Privacidad heading", [privacyPage.getByRole("heading", { name: /pol[i\u00ed]tica de privacidad/i }), privacyPage.getByText(/pol[i\u00ed]tica de privacidad/i)]);
  await assertVisible("Legal content in privacy page", [privacyPage.locator("p, article, main")]);
  report.evidence.finalUrls.privacy = privacyPage.url();
  report["Politica de Privacidad"] = passFail(true, "Privacy page was opened and legal content was visible.");
  await addEvidenceScreenshot("06_politica_de_privacidad", privacyPage, true);

  if (privacyPage !== page) {
    await privacyPage.close();
    await page.bringToFront();
    await waitForUi(page);
  }

  // Step 10: Final report.
  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
  console.log(`Final report written to ${REPORT_PATH}`);
  console.log(JSON.stringify(report, null, 2));
});
