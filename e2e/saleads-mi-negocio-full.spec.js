const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

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
  "Política de Privacidad",
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
}

function locatorsByVisibleText(page, text) {
  const exactPattern = new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i");
  const partialPattern = new RegExp(escapeRegExp(text), "i");
  return [
    page.getByRole("button", { name: exactPattern }).first(),
    page.getByRole("link", { name: exactPattern }).first(),
    page.getByRole("menuitem", { name: exactPattern }).first(),
    page.getByRole("tab", { name: exactPattern }).first(),
    page.getByRole("option", { name: exactPattern }).first(),
    page.getByText(exactPattern).first(),
    page.getByText(partialPattern).first(),
  ];
}

async function resolveVisibleLocator(page, text, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  const candidates = locatorsByVisibleText(page, text);

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const count = await candidate.count().catch(() => 0);
      if (!count) {
        continue;
      }
      const isVisible = await candidate.isVisible().catch(() => false);
      if (isVisible) {
        return candidate;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`No visible element found with text "${text}"`);
}

async function clickByVisibleText(page, text) {
  const locator = await resolveVisibleLocator(page, text);
  await locator.click();
  await waitForUiLoad(page);
}

async function clickFirstVisibleText(page, textOptions) {
  for (const text of textOptions) {
    try {
      await clickByVisibleText(page, text);
      return text;
    } catch (error) {
      // Continue trying the next visible text option.
    }
  }
  throw new Error(`Unable to click any candidate text: ${textOptions.join(", ")}`);
}

async function expectVisibleText(page, text) {
  const locator = await resolveVisibleLocator(page, text);
  await expect(locator).toBeVisible();
}

async function takeCheckpointScreenshot(page, testInfo, filename, fullPage = false) {
  const path = testInfo.outputPath(filename);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(filename, { path, contentType: "image/png" });
}

function findSectionContainer(page, headingText) {
  return page
    .locator("section, article, div")
    .filter({ has: page.getByText(new RegExp(`^\\s*${escapeRegExp(headingText)}\\s*$`, "i")) })
    .first();
}

async function validateLegalLink({
  page,
  testInfo,
  linkText,
  expectedHeading,
  screenshotFile,
}) {
  const previousUrl = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 5000 }).catch(() => null);
  const clickable = await resolveVisibleLocator(page, linkText);
  await clickable.click();

  const popup = await popupPromise;
  const targetPage = popup || page;

  await waitForUiLoad(targetPage);
  await expectVisibleText(targetPage, expectedHeading);

  const legalParagraph = targetPage
    .locator("p, li, div")
    .filter({ hasText: /[A-Za-zÁÉÍÓÚÑáéíóúñ]{12,}/ })
    .first();
  await expect(legalParagraph).toBeVisible();

  await takeCheckpointScreenshot(targetPage, testInfo, screenshotFile, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== previousUrl) {
    await page.goBack().catch(() => {});
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test("SaleADS Mi Negocio full workflow", async ({ page }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const errors = [];
  const evidence = {};

  async function runStep(label, fn) {
    try {
      await fn();
      report[label] = "PASS";
    } catch (error) {
      report[label] = "FAIL";
      errors.push(`[${label}] ${error.message}`);
    }
  }

  await runStep("Login", async () => {
    if (page.url() === "about:blank") {
      const appUrl = process.env.SALEADS_APP_URL || process.env.BASE_URL;
      if (!appUrl) {
        throw new Error(
          "Browser started on about:blank. Provide SALEADS_APP_URL or open the login page before running."
        );
      }
      await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUiLoad(page);

    const googlePopupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickFirstVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google",
    ]);

    const googlePage = await googlePopupPromise;
    const authPage = googlePage || page;
    await waitForUiLoad(authPage);

    const accountLocator = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    if (await accountLocator.isVisible().catch(() => false)) {
      await accountLocator.click();
      await waitForUiLoad(authPage);
    }

    if (googlePage) {
      await googlePage.waitForEvent("close", { timeout: 30000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUiLoad(page);

    const mainInterface = page.locator("main").first();
    const leftSidebar = page.locator("aside, nav, [role='navigation']").first();
    await expect(mainInterface).toBeVisible({ timeout: 30000 });
    await expect(leftSidebar).toBeVisible({ timeout: 30000 });

    await takeCheckpointScreenshot(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    if (report.Login !== "PASS") {
      throw new Error("Blocked because login did not pass.");
    }

    await clickFirstVisibleText(page, ["Negocio", "Mi Negocio"]);
    if (!(await resolveVisibleLocator(page, "Agregar Negocio", 4000).catch(() => null))) {
      await clickByVisibleText(page, "Mi Negocio");
    }

    await expectVisibleText(page, "Agregar Negocio");
    await expectVisibleText(page, "Administrar Negocios");

    await takeCheckpointScreenshot(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    if (report["Mi Negocio menu"] !== "PASS") {
      throw new Error("Blocked because Mi Negocio menu step did not pass.");
    }

    await clickByVisibleText(page, "Agregar Negocio");
    const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();

    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.locator("input").first()).toBeVisible();
    await expect(modal.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await takeCheckpointScreenshot(page, testInfo, "03-agregar-negocio-modal.png");

    await modal.locator("input").first().fill("Negocio Prueba Automatización");
    await modal.getByRole("button", { name: /Cancelar/i }).click();
    await expect(modal).toBeHidden();
    await waitForUiLoad(page);
  });

  await runStep("Administrar Negocios view", async () => {
    if (report["Mi Negocio menu"] !== "PASS") {
      throw new Error("Blocked because Mi Negocio menu step did not pass.");
    }

    if (!(await resolveVisibleLocator(page, "Administrar Negocios", 4000).catch(() => null))) {
      await clickByVisibleText(page, "Mi Negocio");
    }

    await clickByVisibleText(page, "Administrar Negocios");
    await expectVisibleText(page, "Información General");
    await expectVisibleText(page, "Detalles de la Cuenta");
    await expectVisibleText(page, "Tus Negocios");
    await expectVisibleText(page, "Sección Legal");

    await takeCheckpointScreenshot(page, testInfo, "04-administrar-negocios-view.png", true);
  });

  await runStep("Información General", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Blocked because account view did not load.");
    }

    const infoSection = findSectionContainer(page, "Información General");
    await expect(infoSection).toBeVisible();

    const emailLocator = infoSection
      .locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i")
      .first();
    await expect(emailLocator).toBeVisible();

    const candidateName = infoSection
      .locator("h1, h2, h3, h4, p, span, div")
      .filter({ hasText: /^[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}(?:\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,})+$/ })
      .first();
    await expect(candidateName).toBeVisible();

    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Blocked because account view did not load.");
    }

    const detailsSection = findSectionContainer(page, "Detalles de la Cuenta");
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Blocked because account view did not load.");
    }

    const businessSection = findSectionContainer(page, "Tus Negocios");
    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const addBusinessButton = businessSection.getByRole("button", { name: /Agregar Negocio/i }).first();
    await expect(addBusinessButton).toBeVisible();

    const businessItems = businessSection.locator("li, [role='listitem'], tr, [role='row'], article");
    const itemCount = await businessItems.count();
    if (itemCount < 1) {
      throw new Error("Business list is not visible or is empty.");
    }
  });

  await runStep("Términos y Condiciones", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Blocked because account view did not load.");
    }
    const finalUrl = await validateLegalLink({
      page,
      testInfo,
      linkText: "Términos y Condiciones",
      expectedHeading: "Términos y Condiciones",
      screenshotFile: "08-terminos-y-condiciones.png",
    });
    evidence.terminosYCondicionesUrl = finalUrl;
  });

  await runStep("Política de Privacidad", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Blocked because account view did not load.");
    }
    const finalUrl = await validateLegalLink({
      page,
      testInfo,
      linkText: "Política de Privacidad",
      expectedHeading: "Política de Privacidad",
      screenshotFile: "09-politica-de-privacidad.png",
    });
    evidence.politicaDePrivacidadUrl = finalUrl;
  });

  const finalReport = {
    reportName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    resultByField: report,
    evidence,
    errors,
  };
  const reportPath = testInfo.outputPath("10-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("10-final-report.json", { path: reportPath, contentType: "application/json" });

  // Printed summary makes it easy to capture pass/fail and legal final URLs in CI logs.
  console.log("[SALEADS FINAL REPORT]");
  console.log(JSON.stringify(finalReport, null, 2));

  const allPassed = REPORT_FIELDS.every((field) => report[field] === "PASS");
  expect(allPassed).toBeTruthy();
});
