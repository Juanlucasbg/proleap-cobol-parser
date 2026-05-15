const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

const REPORT_FIELDS = [
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function buildReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function sanitizeFileName(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function saveScreenshot(page, directory, name, fullPage = false) {
  const filePath = path.join(directory, `${sanitizeFileName(name)}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function findClickableByText(page, patterns) {
  for (const pattern of patterns) {
    const candidates = [
      page.getByRole("button", { name: pattern }).first(),
      page.getByRole("link", { name: pattern }).first(),
      page.getByRole("menuitem", { name: pattern }).first(),
      page.getByText(pattern).first(),
    ];

    for (const candidate of candidates) {
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
  }

  return null;
}

async function clickByTextAndWait(page, patterns, errorLabel) {
  const locator = await findClickableByText(page, patterns);
  if (!locator) {
    throw new Error(`Could not find clickable element for ${errorLabel}`);
  }

  await locator.click();
  await waitForUi(page);
  return locator;
}

async function assertVisible(page, patterns, errorLabel) {
  const locator = await findClickableByText(page, patterns);
  if (!locator) {
    throw new Error(`Could not find visible element for ${errorLabel}`);
  }
  await expect(locator).toBeVisible();
}

async function maybeSelectGoogleAccount(candidatePage) {
  const accountOption = candidatePage.getByText(GOOGLE_ACCOUNT_EMAIL).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUi(candidatePage);
    return true;
  }
  return false;
}

async function openLegalLinkAndValidate({
  appPage,
  labelPatterns,
  headingPatterns,
  screenshotDir,
  screenshotName,
  appUrlBefore,
}) {
  const link = await findClickableByText(appPage, labelPatterns);
  if (!link) {
    throw new Error(`Could not find legal link: ${labelPatterns[0]}`);
  }

  const context = appPage.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await link.click();
  const popup = await popupPromise;

  const targetPage = popup || appPage;
  await waitForUi(targetPage);

  await assertVisible(targetPage, headingPatterns, "legal heading");

  const bodyText = await targetPage.locator("body").innerText();
  const condensed = bodyText.replace(/\s+/g, " ").trim();
  expect(condensed.length).toBeGreaterThan(200);

  await saveScreenshot(targetPage, screenshotDir, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    if (appPage.url() !== appUrlBefore) {
      await appPage.goBack().catch(async () => {
        await appPage.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
      });
      await waitForUi(appPage);
    }
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || process.env.BASE_URL || "";
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.join(process.cwd(), "artifacts", "saleads_mi_negocio_full_test", timestamp);
  const resultsDir = path.join(process.cwd(), "results");
  fs.mkdirSync(artifactsDir, { recursive: true });
  fs.mkdirSync(resultsDir, { recursive: true });

  const report = buildReport();
  const detail = {
    screenshots: {},
    urls: {},
    errors: [],
  };

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No login URL provided. Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) to the active environment login page."
    );
  }

  // Step 1: Login with Google
  try {
    await waitForUi(page);
    const loginButtonPatterns = [
      /sign in with google/i,
      /iniciar sesion con google/i,
      /continuar con google/i,
      /google/i,
    ];
    const loginButton = await findClickableByText(page, loginButtonPatterns);
    if (!loginButton) {
      throw new Error("Could not find Google login button");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await loginButton.click();
    const popup = await popupPromise;

    if (popup) {
      await waitForUi(popup);
      await maybeSelectGoogleAccount(popup);
      await popup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
      await page.bringToFront();
    } else {
      await waitForUi(page);
      await maybeSelectGoogleAccount(page);
    }

    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 120000 });
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 120000 });

    detail.screenshots.dashboard = await saveScreenshot(page, artifactsDir, "01_dashboard_loaded");
    report["Login"] = "PASS";
  } catch (error) {
    detail.errors.push(`[Login] ${error.message}`);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await clickByTextAndWait(page, [/negocio/i], "Negocio section");
    await clickByTextAndWait(page, [/mi negocio/i], "Mi Negocio option");
    await assertVisible(page, [/agregar negocio/i], "Agregar Negocio option");
    await assertVisible(page, [/administrar negocios/i], "Administrar Negocios option");
    detail.screenshots.mi_negocio_menu = await saveScreenshot(page, artifactsDir, "02_mi_negocio_menu_expanded");
    report["Mi Negocio menu"] = "PASS";
  } catch (error) {
    detail.errors.push(`[Mi Negocio menu] ${error.message}`);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await clickByTextAndWait(page, [/agregar negocio/i], "Agregar Negocio");
    await assertVisible(page, [/crear nuevo negocio/i], "Crear Nuevo Negocio title");
    await assertVisible(page, [/nombre del negocio/i], "Nombre del Negocio field");
    await assertVisible(page, [/tienes 2 de 3 negocios/i], "business quota text");
    await assertVisible(page, [/cancelar/i], "Cancelar button");
    await assertVisible(page, [/crear negocio/i], "Crear Negocio button");

    const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.fill("Negocio Prueba Automatizacion");
    }

    detail.screenshots.agregar_modal = await saveScreenshot(page, artifactsDir, "03_agregar_negocio_modal");
    await clickByTextAndWait(page, [/cancelar/i], "Cancelar modal button");
    report["Agregar Negocio modal"] = "PASS";
  } catch (error) {
    detail.errors.push(`[Agregar Negocio modal] ${error.message}`);
  }

  // Step 4: Open Administrar Negocios
  try {
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      await clickByTextAndWait(page, [/mi negocio/i], "Mi Negocio re-expand");
    }

    await clickByTextAndWait(page, [/administrar negocios/i], "Administrar Negocios");
    await assertVisible(page, [/informacion general|informaci[o\u00f3]n general/i], "Informacion General");
    await assertVisible(page, [/detalles de la cuenta/i], "Detalles de la Cuenta");
    await assertVisible(page, [/tus negocios/i], "Tus Negocios");
    await assertVisible(
      page,
      [/seccion legal|secci[o\u00f3]n legal/i],
      "Seccion Legal"
    );
    detail.screenshots.administrar_view = await saveScreenshot(
      page,
      artifactsDir,
      "04_administrar_negocios_view",
      true
    );
    report["Administrar Negocios view"] = "PASS";
  } catch (error) {
    detail.errors.push(`[Administrar Negocios view] ${error.message}`);
  }

  // Step 5: Validate Informacion General
  try {
    const emailLocator = page
      .locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/")
      .first();
    await expect(emailLocator).toBeVisible();
    await assertVisible(page, [/business plan/i], "BUSINESS PLAN text");
    await assertVisible(page, [/cambiar plan/i], "Cambiar Plan button");

    const infoSectionText = await page.locator("body").innerText();
    const lines = infoSectionText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const hasLikelyUserName = lines.some((line) => {
      if (line.includes("@")) return false;
      if (/business plan|cambiar plan|informaci[o\u00f3]n general/i.test(line)) return false;
      return /^[A-Za-z][A-Za-z .'-]{2,}$/.test(line);
    });

    if (!hasLikelyUserName) {
      throw new Error("Could not confidently identify user name text");
    }
    report["Información General"] = "PASS";
  } catch (error) {
    detail.errors.push(`[Información General] ${error.message}`);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await assertVisible(page, [/cuenta creada/i], "Cuenta creada");
    await assertVisible(page, [/estado activo/i], "Estado activo");
    await assertVisible(page, [/idioma seleccionado/i], "Idioma seleccionado");
    report["Detalles de la Cuenta"] = "PASS";
  } catch (error) {
    detail.errors.push(`[Detalles de la Cuenta] ${error.message}`);
  }

  // Step 7: Validate Tus Negocios
  try {
    await assertVisible(page, [/tus negocios/i], "Tus Negocios section");
    await assertVisible(page, [/agregar negocio/i], "Agregar Negocio button");
    await assertVisible(page, [/tienes 2 de 3 negocios/i], "business quota text");

    const tusNegociosSection = page.locator("section").filter({ hasText: /tus negocios/i }).first();
    await expect(tusNegociosSection).toBeVisible();

    const listCandidates = [
      tusNegociosSection.locator("li"),
      tusNegociosSection.locator("tr"),
      tusNegociosSection.locator("[data-testid*='business']"),
      tusNegociosSection.locator("[class*='business']"),
    ];

    const hasList = [];
    for (const candidate of listCandidates) {
      hasList.push((await candidate.count()) > 0);
    }
    if (!hasList.some(Boolean)) {
      throw new Error("Could not find business list entries in Tus Negocios");
    }
    report["Tus Negocios"] = "PASS";
  } catch (error) {
    detail.errors.push(`[Tus Negocios] ${error.message}`);
  }

  // Step 8: Validate Terminos y Condiciones
  try {
    const appUrlBefore = page.url();
    const termsUrl = await openLegalLinkAndValidate({
      appPage: page,
      labelPatterns: [/terminos y condiciones|t[e\u00e9]rminos y condiciones/i],
      headingPatterns: [/terminos y condiciones|t[e\u00e9]rminos y condiciones/i],
      screenshotDir: artifactsDir,
      screenshotName: "08_terminos_y_condiciones",
      appUrlBefore,
    });
    detail.urls.terminosYCondiciones = termsUrl;
    report["Términos y Condiciones"] = "PASS";
  } catch (error) {
    detail.errors.push(`[Términos y Condiciones] ${error.message}`);
  }

  // Step 9: Validate Politica de Privacidad
  try {
    const privacyUrl = await openLegalLinkAndValidate({
      appPage: page,
      labelPatterns: [/politica de privacidad|pol[i\u00ed]tica de privacidad/i],
      headingPatterns: [/politica de privacidad|pol[i\u00ed]tica de privacidad/i],
      screenshotDir: artifactsDir,
      screenshotName: "09_politica_de_privacidad",
      appUrlBefore: page.url(),
    });
    detail.urls.politicaDePrivacidad = privacyUrl;
    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    detail.errors.push(`[Política de Privacidad] ${error.message}`);
  }

  // Step 10: Final report
  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    detail,
  };

  const reportPath = path.join(resultsDir, "saleads_mi_negocio_full_test_report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  // Keep summary in logs for CI visibility.
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(finalReport, null, 2));

  const failedFields = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);

  expect(
    failedFields,
    `One or more validations failed.\nFailed fields: ${failedFields.join(", ")}\nErrors:\n${detail.errors.join(
      "\n"
    )}`
  ).toEqual([]);
});
