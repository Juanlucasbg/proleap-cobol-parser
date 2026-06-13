const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function shortError(error) {
  if (!error) {
    return "Unknown error";
  }

  const msg = typeof error === "string" ? error : error.message || String(error);
  return msg.split("\n").slice(0, 2).join(" ").trim();
}

function ensureReportDir(testInfo) {
  const reportDir = testInfo.outputPath("saleads_mi_negocio_full_test");
  fs.mkdirSync(reportDir, { recursive: true });
  return reportDir;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
}

async function clickByVisibleText(page, matcher, options = {}) {
  const candidates = [
    page.getByRole("button", { name: matcher }).first(),
    page.getByRole("link", { name: matcher }).first(),
    page.getByRole("menuitem", { name: matcher }).first(),
    page.getByRole("tab", { name: matcher }).first(),
    page.getByText(matcher).first()
  ];

  for (const locator of candidates) {
    try {
      await locator.waitFor({ state: "visible", timeout: 2500 });
      await locator.click();
      if (options.waitForUi !== false) {
        await waitForUi(page);
      }
      return;
    } catch (_ignored) {
      // Continue trying fallback locators.
    }
  }

  throw new Error(`Could not click element with visible text: ${matcher}`);
}

async function expectVisibleByText(page, matcher, description) {
  const candidates = [
    page.getByRole("heading", { name: matcher }).first(),
    page.getByRole("button", { name: matcher }).first(),
    page.getByRole("link", { name: matcher }).first(),
    page.getByText(matcher).first()
  ];

  for (const locator of candidates) {
    try {
      await expect(locator).toBeVisible({ timeout: 4000 });
      return;
    } catch (_ignored) {
      // Continue trying fallback locators.
    }
  }

  throw new Error(`Expected visible text not found (${description})`);
}

async function maybeSelectGoogleAccount(page) {
  const accountByText = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  const accountByRole = page.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }).first();

  if (await accountByText.isVisible().catch(() => false)) {
    await accountByText.click();
    await waitForUi(page);
    return true;
  }

  if (await accountByRole.isVisible().catch(() => false)) {
    await accountByRole.click();
    await waitForUi(page);
    return true;
  }

  return false;
}

async function capture(page, reportDir, fileName, fullPage = false) {
  const screenshotPath = path.join(reportDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function validateLegalPageAndReturn(appPage, linkMatcher, headingMatcher, reportDir, screenshotName) {
  const context = appPage.context();
  const maybePopup = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await clickByVisibleText(appPage, linkMatcher, { waitForUi: false });

  const popup = await maybePopup;
  const legalPage = popup || appPage;

  await waitForUi(legalPage);
  await expectVisibleByText(legalPage, headingMatcher, `heading ${headingMatcher}`);

  const legalContent = legalPage.locator("main p, article p, section p, p").first();
  await expect(legalContent).toBeVisible({ timeout: 15000 });

  await capture(legalPage, reportDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const reportDir = ensureReportDir(testInfo);
  const finalReportPath = path.join(reportDir, "final_report.json");
  const statusByField = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "NOT_RUN"]));
  const evidence = {
    screenshots: {},
    termsAndConditionsUrl: null,
    privacyPolicyUrl: null
  };

  const stepFailures = [];

  const runStep = async (fieldName, fn) => {
    try {
      await fn();
      statusByField[fieldName] = "PASS";
    } catch (error) {
      statusByField[fieldName] = `FAIL: ${shortError(error)}`;
      stepFailures.push({ fieldName, error: shortError(error) });
      await capture(page, reportDir, `${fieldName.replace(/\s+/g, "_").toLowerCase()}_failure.png`, true).catch(() => {});
    }
  };

  await test.step("Bootstrap current environment page", async () => {
    if (page.url() === "about:blank") {
      const loginUrl = process.env.SALEADS_LOGIN_URL;

      if (!loginUrl) {
        test.skip(
          "Set SALEADS_LOGIN_URL (or open the login page manually before running) to keep this test environment-agnostic."
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  });

  let appPage = page;

  await runStep("Login", async () => {
    const context = page.context();
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

    await clickByVisibleText(page, /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i, {
      waitForUi: false
    });

    const popup = await popupPromise;
    if (popup) {
      await waitForUi(popup);
      await maybeSelectGoogleAccount(popup);

      // If Google flow remains in popup and then redirects back into app, keep using that tab.
      if (!popup.isClosed() && !/accounts\.google|oauth|signin/i.test(popup.url())) {
        appPage = popup;
      }
    } else if (/accounts\.google|oauth|signin/i.test(page.url())) {
      await maybeSelectGoogleAccount(page);
    }

    await waitForUi(appPage);
    await expect(appPage.locator("aside, nav").first()).toBeVisible({ timeout: 40000 });
    await expectVisibleByText(appPage, /negocio/i, "sidebar text negocio");

    evidence.screenshots.dashboard = await capture(appPage, reportDir, "01_dashboard_loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(appPage, /mi negocio/i);
    await expectVisibleByText(appPage, /agregar negocio/i, "submenu Agregar Negocio");
    await expectVisibleByText(appPage, /administrar negocios/i, "submenu Administrar Negocios");

    evidence.screenshots.miNegocioMenu = await capture(appPage, reportDir, "02_mi_negocio_menu_expanded.png", true);
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(appPage, /agregar negocio/i);

    const modal = appPage.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
    await expect(modal).toBeVisible({ timeout: 15000 });
    await expect(modal.getByText(/crear nuevo negocio/i).first()).toBeVisible();
    await expect(modal.getByText(/nombre del negocio/i).first()).toBeVisible();
    await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    const businessNameInput = modal.getByLabel(/nombre del negocio/i).first();
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.fill("Negocio Prueba Automatización");
    } else {
      const fallbackInput = modal.locator("input").first();
      await fallbackInput.fill("Negocio Prueba Automatización");
    }

    evidence.screenshots.agregarNegocioModal = await capture(appPage, reportDir, "03_agregar_negocio_modal.png", true);

    await modal.getByRole("button", { name: /cancelar/i }).first().click();
    await waitForUi(appPage);
    await expect(modal).not.toBeVisible({ timeout: 10000 });
  });

  await runStep("Administrar Negocios view", async () => {
    const adminOption = appPage.getByText(/administrar negocios/i).first();
    const adminVisible = await adminOption.isVisible().catch(() => false);
    if (!adminVisible) {
      await clickByVisibleText(appPage, /mi negocio/i);
    }

    await clickByVisibleText(appPage, /administrar negocios/i);
    await expectVisibleByText(appPage, /información general/i, "section Información General");
    await expectVisibleByText(appPage, /detalles de la cuenta/i, "section Detalles de la Cuenta");
    await expectVisibleByText(appPage, /tus negocios/i, "section Tus Negocios");
    await expectVisibleByText(appPage, /sección legal/i, "section Sección Legal");

    evidence.screenshots.administrarNegocios = await capture(appPage, reportDir, "04_administrar_negocios.png", true);
  });

  await runStep("Información General", async () => {
    await expect(appPage.getByText(/@/).first()).toBeVisible({ timeout: 10000 });
    await expect(appPage.getByText(/business plan/i).first()).toBeVisible({ timeout: 10000 });
    await expect(appPage.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({ timeout: 10000 });

    const profileNameCandidate = appPage.locator("h1, h2, h3, strong, [data-testid*=name]").first();
    await expect(profileNameCandidate).toBeVisible({ timeout: 10000 });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expectVisibleByText(appPage, /cuenta creada/i, "Cuenta creada");
    await expectVisibleByText(appPage, /estado activo/i, "Estado activo");
    await expectVisibleByText(appPage, /idioma seleccionado/i, "Idioma seleccionado");
  });

  await runStep("Tus Negocios", async () => {
    await expectVisibleByText(appPage, /tus negocios/i, "Section title Tus Negocios");
    await expectVisibleByText(appPage, /agregar negocio/i, "button Agregar Negocio");
    await expectVisibleByText(appPage, /tienes\s*2\s*de\s*3\s*negocios/i, "usage text 2 de 3");
  });

  await runStep("Términos y Condiciones", async () => {
    evidence.termsAndConditionsUrl = await validateLegalPageAndReturn(
      appPage,
      /términos y condiciones/i,
      /términos y condiciones/i,
      reportDir,
      "05_terminos_y_condiciones.png"
    );
  });

  await runStep("Política de Privacidad", async () => {
    evidence.privacyPolicyUrl = await validateLegalPageAndReturn(
      appPage,
      /política de privacidad/i,
      /política de privacidad/i,
      reportDir,
      "06_politica_de_privacidad.png"
    );
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    statusByField,
    evidence,
    stepFailures
  };

  fs.writeFileSync(finalReportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("final_report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });

  const failedFields = Object.entries(statusByField).filter(([, result]) => !String(result).startsWith("PASS"));
  expect(
    failedFields,
    `Workflow validation report:\n${JSON.stringify(finalReport, null, 2)}`
  ).toEqual([]);
});
