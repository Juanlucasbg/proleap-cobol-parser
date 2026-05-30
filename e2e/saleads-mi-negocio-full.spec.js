const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad"
];

function sanitizeName(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(300);
}

function candidateLocators(page, matcher) {
  return [
    page.getByRole("button", { name: matcher }).first(),
    page.getByRole("link", { name: matcher }).first(),
    page.getByRole("menuitem", { name: matcher }).first(),
    page.getByRole("tab", { name: matcher }).first(),
    page.getByRole("heading", { name: matcher }).first(),
    page.locator("[role='button'], [role='menuitem'], button, a").filter({ hasText: matcher }).first(),
    page.getByText(matcher).first()
  ];
}

async function resolveVisibleLocator(page, matcher, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of candidateLocators(page, matcher)) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`No visible element found for matcher: ${matcher}`);
}

async function clickVisibleText(page, matcher, timeoutMs = 15000) {
  const locator = await resolveVisibleLocator(page, matcher, timeoutMs);
  await locator.click();
  await waitForUiToLoad(page);
}

async function isTextVisible(page, matcher, timeoutMs = 5000) {
  try {
    await resolveVisibleLocator(page, matcher, timeoutMs);
    return true;
  } catch {
    return false;
  }
}

async function chooseGoogleAccountIfPresented(page) {
  if (await isTextVisible(page, new RegExp(GOOGLE_ACCOUNT_EMAIL, "i"), 7000)) {
    await clickVisibleText(page, new RegExp(GOOGLE_ACCOUNT_EMAIL, "i"));
  }
}

async function expectVisibleText(page, matcher, timeoutMs = 15000) {
  const locator = await resolveVisibleLocator(page, matcher, timeoutMs);
  await expect(locator).toBeVisible({ timeout: timeoutMs });
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  expect(loginUrl, "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) before running the test.").toBeTruthy();

  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const evidenceDir = path.join("e2e-artifacts", "checkpoints", runId);
  const reportDir = path.join("e2e-artifacts", "reports");
  fs.mkdirSync(evidenceDir, { recursive: true });
  fs.mkdirSync(reportDir, { recursive: true });

  const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = {};
  const legalUrls = {};
  let screenshotIndex = 1;

  const checkpoint = async (targetPage, label, fullPage = false) => {
    const filePath = path.join(evidenceDir, `${String(screenshotIndex).padStart(2, "0")}-${sanitizeName(label)}.png`);
    screenshotIndex += 1;
    await targetPage.screenshot({ path: filePath, fullPage });
    return filePath;
  };

  const runStep = async (fieldName, callback) => {
    try {
      await callback();
      results[fieldName] = "PASS";
    } catch (error) {
      results[fieldName] = "FAIL";
      failures[fieldName] = error instanceof Error ? error.message : String(error);
      await checkpoint(page, `failure-${fieldName}`, true).catch(() => {});
    }
  };

  const openLegalPageAndValidate = async (linkMatcher, headingMatcher, screenshotLabel, urlKey) => {
    const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await clickVisibleText(page, linkMatcher);
    const popup = await popupPromise;

    const legalPage = popup || page;
    await legalPage.bringToFront();
    await waitForUiToLoad(legalPage);

    await expectVisibleText(legalPage, headingMatcher, 20000);
    const bodyText = await legalPage.locator("body").innerText();
    expect(bodyText.trim().length, `Expected non-empty legal content for ${urlKey}.`).toBeGreaterThan(120);

    legalUrls[urlKey] = legalPage.url();
    await checkpoint(legalPage, screenshotLabel, true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUiToLoad(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUiToLoad(page);
    }
  };

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToLoad(page);

  await runStep("Login", async () => {
    const googleLoginMatcher = /sign in with google|iniciar sesion con google|iniciar con google|continuar con google|google/i;
    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);

    await clickVisibleText(page, googleLoginMatcher, 20000);
    const popup = await popupPromise;

    if (popup) {
      await popup.bringToFront();
      await waitForUiToLoad(popup);
      await chooseGoogleAccountIfPresented(popup);
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      await page.bringToFront();
    } else {
      await chooseGoogleAccountIfPresented(page);
    }

    await waitForUiToLoad(page);
    await expectVisibleText(page, /Negocio/i, 30000);
    await expectVisibleText(page, /Mi Negocio/i, 30000);
    await checkpoint(page, "dashboard-loaded", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await clickVisibleText(page, /Negocio/i);
    await clickVisibleText(page, /Mi Negocio/i);
    await expectVisibleText(page, /Agregar Negocio/i);
    await expectVisibleText(page, /Administrar Negocios/i);
    await checkpoint(page, "mi-negocio-expanded-menu");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickVisibleText(page, /Agregar Negocio/i);
    await expectVisibleText(page, /Crear Nuevo Negocio/i, 20000);
    await expectVisibleText(page, /Nombre del Negocio/i);
    await expectVisibleText(page, /Tienes 2 de 3 negocios/i);
    await expectVisibleText(page, /Cancelar/i);
    await expectVisibleText(page, /Crear Negocio/i);
    await checkpoint(page, "agregar-negocio-modal");

    let businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
    if (!(await businessNameInput.isVisible().catch(() => false))) {
      businessNameInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
    }
    if (!(await businessNameInput.isVisible().catch(() => false))) {
      businessNameInput = page.locator("input").first();
    }
    await expect(businessNameInput).toBeVisible();
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickVisibleText(page, /Cancelar/i);
  });

  await runStep("Administrar Negocios view", async () => {
    if (!(await isTextVisible(page, /Administrar Negocios/i, 2000))) {
      await clickVisibleText(page, /Mi Negocio/i);
    }

    await clickVisibleText(page, /Administrar Negocios/i);
    await expectVisibleText(page, /Informacion General|Informaci[oó]n General/i, 30000);
    await expectVisibleText(page, /Detalles de la Cuenta/i);
    await expectVisibleText(page, /Tus Negocios/i);
    await expectVisibleText(page, /Seccion Legal|Secci[oó]n Legal/i);
    await checkpoint(page, "administrar-negocios-account-page", true);
  });

  await runStep("Informaci\u00f3n General", async () => {
    await expectVisibleText(page, /BUSINESS PLAN/i);
    await expectVisibleText(page, /Cambiar Plan/i);
    await expectVisibleText(page, /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    await expectVisibleText(page, /Nombre|Perfil|Usuario|Cuenta/i);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expectVisibleText(page, /Cuenta creada/i);
    await expectVisibleText(page, /Estado activo/i);
    await expectVisibleText(page, /Idioma seleccionado/i);
  });

  await runStep("Tus Negocios", async () => {
    await expectVisibleText(page, /Tus Negocios/i);
    await expectVisibleText(page, /Agregar Negocio/i);
    await expectVisibleText(page, /Tienes 2 de 3 negocios/i);
  });

  await runStep("T\u00e9rminos y Condiciones", async () => {
    await openLegalPageAndValidate(
      /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
      /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
      "terminos-y-condiciones-page",
      "terminosYCondicionesUrl"
    );
  });

  await runStep("Pol\u00edtica de Privacidad", async () => {
    await openLegalPageAndValidate(
      /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
      /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
      "politica-de-privacidad-page",
      "politicaDePrivacidadUrl"
    );
  });

  const summary = REPORT_FIELDS.map((field) => ({
    step: field,
    status: results[field],
    details: failures[field] || "OK"
  }));

  const finalReport = {
    scenario: "saleads_mi_negocio_full_test",
    triggeredAt: new Date().toISOString(),
    loginUrl,
    results,
    failures,
    legalUrls
  };

  const reportPath = path.join(reportDir, `saleads-mi-negocio-full-${runId}.json`);
  fs.writeFileSync(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");

  console.table(summary);
  console.log("Final URLs:", legalUrls);
  console.log("Report file:", reportPath);

  await test.info().attach("saleads-mi-negocio-final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf8"),
    contentType: "application/json"
  });

  expect(
    REPORT_FIELDS.every((field) => results[field] === "PASS"),
    "One or more validation groups failed. See report artifact for details."
  ).toBeTruthy();
});
