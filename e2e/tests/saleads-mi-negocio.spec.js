const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function sanitizeStepName(stepName) {
  return stepName
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

function resolveScreenshotPath(stepName) {
  return path.join("artifacts", "screenshots", `${sanitizeStepName(stepName)}.png`);
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function clickFirstVisible(locators, page) {
  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      await waitForUiAfterClick(page);
      return true;
    }
  }

  return false;
}

async function waitForUiAfterClick(page) {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null),
    page.waitForTimeout(900),
  ]);
}

async function clickByVisibleText(page, texts) {
  for (const text of texts) {
    const escapedText = escapeRegex(text);
    const clicked = await clickFirstVisible(
      [
        page.getByRole("button", { name: new RegExp(`^${escapedText}$`, "i") }).first(),
        page.getByRole("link", { name: new RegExp(`^${escapedText}$`, "i") }).first(),
        page.getByText(text, { exact: true }).first(),
        page.getByText(new RegExp(escapedText, "i")).first(),
      ],
      page
    );
    if (clicked) {
      return true;
    }
  }

  return false;
}

async function validateAnyVisible(page, texts) {
  for (const text of texts) {
    const target = page.getByText(text, { exact: true }).first();
    if (await target.isVisible().catch(() => false)) {
      return true;
    }
  }

  return false;
}

async function selectGoogleAccountIfVisible(context, currentPage, email) {
  const pages = context.pages();
  for (const p of pages) {
    const candidate = p.getByText(email, { exact: true }).first();
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click();
      await waitForUiAfterClick(p);
      if (p !== currentPage) {
        await currentPage.bringToFront().catch(() => null);
      }
      return true;
    }
  }

  return false;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.setTimeout(300000);

  const results = [];
  const legalUrls = {
    "Términos y Condiciones": null,
    "Política de Privacidad": null,
  };

  const record = (step, passed, details) => {
    results.push({ step, passed, details });
  };

  async function attachCheckpoint(name, fullPage = false) {
    const screenshotPath = resolveScreenshotPath(name);
    fs.mkdirSync(path.dirname(screenshotPath), { recursive: true });
    await page.screenshot({ path: screenshotPath, fullPage });
    await testInfo.attach(name, {
      path: screenshotPath,
      contentType: "image/png",
    });
  }

  const appUrl = process.env.SALEADS_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (appUrl) {
    await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    await waitForUiAfterClick(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No login page loaded. Set SALEADS_URL (or SALEADS_BASE_URL/BASE_URL) to any SaleADS environment login URL."
    );
  }

  // Step 1: Login with Google
  try {
    await expect(page.locator("body")).toBeVisible();

    const pagesBeforeLogin = new Set(context.pages());
    const googlePopupPromise = context.waitForEvent("page", { timeout: 4000 }).catch(() => null);
    const clickedLogin =
      (await clickFirstVisible(
        [
          page.getByRole("button", { name: /google/i }).first(),
          page.getByRole("link", { name: /google/i }).first(),
        ],
        page
      )) ||
      (await clickByVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Acceder con Google",
        "Login with Google",
      ]));

    expect(clickedLogin).toBeTruthy();
    const popupAfterLogin = (await googlePopupPromise) || context.pages().find((p) => !pagesBeforeLogin.has(p));
    if (popupAfterLogin) {
      await popupAfterLogin.waitForLoadState("domcontentloaded").catch(() => null);
    }
    await selectGoogleAccountIfVisible(context, page, GOOGLE_ACCOUNT_EMAIL);

    const sidebarVisible = await page
      .locator("aside, nav")
      .first()
      .isVisible()
      .catch(() => false);

    const negocioVisible = await validateAnyVisible(page, ["Negocio", "Mi Negocio"]);
    expect(sidebarVisible || negocioVisible).toBeTruthy();

    await attachCheckpoint("step-1-dashboard-loaded");
    record("Login", true, "Main interface and sidebar/navigation are visible.");
  } catch (error) {
    record("Login", false, error.message);
  }

  // Step 2: Open Mi Negocio menu
  try {
    const clickedNegocio = await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
    expect(clickedNegocio).toBeTruthy();

    await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: true }).first()).toBeVisible();

    await attachCheckpoint("step-2-mi-negocio-expanded");
    record("Mi Negocio menu", true, "Submenu expanded with expected options.");
  } catch (error) {
    record("Mi Negocio menu", false, error.message);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await page.getByText("Agregar Negocio", { exact: true }).first().click();
    await waitForUiAfterClick(page);

    await expect(page.getByText("Crear Nuevo Negocio", { exact: true }).first()).toBeVisible();
    const byLabel = page.getByLabel(/Nombre del Negocio/i).first();
    const byPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i).first();
    const businessNameInput = (await byLabel.isVisible().catch(() => false)) ? byLabel : byPlaceholder;
    await expect(businessNameInput.first()).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Cancelar", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Crear Negocio", { exact: true }).first()).toBeVisible();

    await businessNameInput.first().fill("Negocio Prueba Automatización");
    await attachCheckpoint("step-3-agregar-negocio-modal");
    await page.getByText("Cancelar", { exact: true }).first().click();
    await waitForUiAfterClick(page);

    record("Agregar Negocio modal", true, "Modal and all required controls validated.");
  } catch (error) {
    record("Agregar Negocio modal", false, error.message);
  }

  // Step 4: Open Administrar Negocios
  try {
    if (!(await page.getByText("Administrar Negocios", { exact: true }).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
    }

    await page.getByText("Administrar Negocios", { exact: true }).first().click();
    await waitForUiAfterClick(page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal|Seccion Legal/i).first()).toBeVisible();

    await attachCheckpoint("step-4-administrar-negocios-view", true);
    record("Administrar Negocios view", true, "Account page sections are visible.");
  } catch (error) {
    record("Administrar Negocios view", false, error.message);
  }

  // Step 5: Validate Información General
  try {
    await expect(page.getByText("BUSINESS PLAN", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Cambiar Plan", { exact: true }).first()).toBeVisible();

    const hasEmail = await page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i").first().isVisible();
    expect(hasEmail).toBeTruthy();

    const hasKnownAccountName = await page
      .locator("h1, h2, h3, p, span, div")
      .filter({ hasText: /juan|lucas|barbier|garzon/i })
      .first()
      .isVisible()
      .catch(() => false);
    const hasNameLabelOrValue = await validateAnyVisible(page, ["Nombre", "Nombre del usuario", "User name", "Usuario"]);
    expect(hasKnownAccountName || hasNameLabelOrValue).toBeTruthy();

    record("Información General", true, "Name/email, plan, and button are visible.");
  } catch (error) {
    record("Información General", false, error.message);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(page.getByText("Cuenta creada", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Estado activo", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Idioma seleccionado", { exact: true }).first()).toBeVisible();

    record("Detalles de la Cuenta", true, "All expected account details labels are visible.");
  } catch (error) {
    record("Detalles de la Cuenta", false, error.message);
  }

  // Step 7: Validate Tus Negocios
  try {
    await expect(page.getByText("Tus Negocios", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true }).first()).toBeVisible();

    const businessSection = page.locator("section,div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible();

    const businessListVisible = await businessSection
      .locator("li, tr, [role='listitem'], [role='row']")
      .first()
      .isVisible()
      .catch(() => false);
    const businessCardsVisible = await businessSection
      .locator("[class*='business'], [data-testid*='business']")
      .first()
      .isVisible()
      .catch(() => false);
    expect(businessListVisible || businessCardsVisible).toBeTruthy();

    record("Tus Negocios", true, "Business section/list and usage text validated.");
  } catch (error) {
    record("Tus Negocios", false, error.message);
  }

  async function validateLegalLink(linkText, expectedHeading, screenshotName) {
    const currentPage = page;
    const pagesBefore = new Set(context.pages());
    const popupPromise = context.waitForEvent("page", { timeout: 3000 }).catch(() => null);
    const clicked = await clickByVisibleText(currentPage, [linkText]);
    expect(clicked).toBeTruthy();

    let popup = await popupPromise;
    if (!popup) {
      popup = context.pages().find((p) => !pagesBefore.has(p)) || null;
    }
    let targetPage = currentPage;
    if (popup) {
      targetPage = popup;
      await popup.waitForLoadState("domcontentloaded");
      await popup.waitForLoadState("networkidle").catch(() => null);
    } else {
      await currentPage.waitForLoadState("domcontentloaded");
      await currentPage.waitForLoadState("networkidle").catch(() => null);
    }

    await expect(targetPage.getByText(new RegExp(escapeRegex(expectedHeading), "i")).first()).toBeVisible();

    const legalContentVisible = await targetPage
      .locator("main, article, section, p, div")
      .filter({ hasText: /términos|condiciones|privacidad|datos|información/i })
      .first()
      .isVisible()
      .catch(() => false);
    expect(legalContentVisible).toBeTruthy();

    const finalUrl = targetPage.url();
    legalUrls[expectedHeading] = finalUrl;

    const screenshotPath = resolveScreenshotPath(screenshotName);
    fs.mkdirSync(path.dirname(screenshotPath), { recursive: true });
    await targetPage.screenshot({ path: screenshotPath, fullPage: true });
    await testInfo.attach(screenshotName, {
      path: screenshotPath,
      contentType: "image/png",
    });

    if (popup) {
      await popup.close();
      await currentPage.bringToFront();
      await waitForUiAfterClick(currentPage);
    } else {
      await currentPage.goBack().catch(() => null);
      await waitForUiAfterClick(currentPage);
    }
  }

  // Step 8: Validate Términos y Condiciones
  try {
    await validateLegalLink(
      "Términos y Condiciones",
      "Términos y Condiciones",
      "step-8-terminos-y-condiciones"
    );
    record(
      "Términos y Condiciones",
      true,
      `Legal page validated. URL: ${legalUrls["Términos y Condiciones"]}`
    );
  } catch (error) {
    record("Términos y Condiciones", false, error.message);
  }

  // Step 9: Validate Política de Privacidad
  try {
    await validateLegalLink(
      "Política de Privacidad",
      "Política de Privacidad",
      "step-9-politica-de-privacidad"
    );
    record(
      "Política de Privacidad",
      true,
      `Legal page validated. URL: ${legalUrls["Política de Privacidad"]}`
    );
  } catch (error) {
    record("Política de Privacidad", false, error.message);
  }

  // Step 10: Final report
  const finalReport = {
    Login: results.find((x) => x.step === "Login")?.passed ? "PASS" : "FAIL",
    "Mi Negocio menu": results.find((x) => x.step === "Mi Negocio menu")?.passed ? "PASS" : "FAIL",
    "Agregar Negocio modal": results.find((x) => x.step === "Agregar Negocio modal")?.passed ? "PASS" : "FAIL",
    "Administrar Negocios view": results.find((x) => x.step === "Administrar Negocios view")?.passed ? "PASS" : "FAIL",
    "Información General": results.find((x) => x.step === "Información General")?.passed ? "PASS" : "FAIL",
    "Detalles de la Cuenta": results.find((x) => x.step === "Detalles de la Cuenta")?.passed ? "PASS" : "FAIL",
    "Tus Negocios": results.find((x) => x.step === "Tus Negocios")?.passed ? "PASS" : "FAIL",
    "Términos y Condiciones": results.find((x) => x.step === "Términos y Condiciones")?.passed ? "PASS" : "FAIL",
    "Política de Privacidad": results.find((x) => x.step === "Política de Privacidad")?.passed ? "PASS" : "FAIL",
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify({ finalReport, details: results, legalUrls }, null, 2), "utf-8"),
    contentType: "application/json",
  });

  console.log("Final workflow report:", JSON.stringify({ finalReport, legalUrls }, null, 2));

  const allPassed = Object.values(finalReport).every((status) => status === "PASS");
  expect(allPassed, `One or more workflow validations failed: ${JSON.stringify(finalReport)}`).toBeTruthy();
});
