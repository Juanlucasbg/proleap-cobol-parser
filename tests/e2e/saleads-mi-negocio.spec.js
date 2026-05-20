const { test, expect } = require("@playwright/test");

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

const EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
}

async function captureCheckpoint(page, testInfo, fileName, options = { fullPage: true }) {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, ...options });
  await testInfo.attach(fileName, { path, contentType: "image/png" });
}

function candidateLocators(page, matcher) {
  return [
    page.getByRole("button", { name: matcher }).first(),
    page.getByRole("link", { name: matcher }).first(),
    page.getByRole("menuitem", { name: matcher }).first(),
    page.getByRole("tab", { name: matcher }).first(),
    page.getByRole("heading", { name: matcher }).first(),
    page.getByText(matcher).first()
  ];
}

async function clickByVisibleText(page, matcher, description, waitAfterClick = true) {
  const locators = candidateLocators(page, matcher);

  for (const locator of locators) {
    try {
      if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
        await locator.first().scrollIntoViewIfNeeded();
        await locator.first().click();
        if (waitAfterClick) {
          await waitForUiLoad(page);
        }
        return;
      }
    } catch (_error) {
      // Try the next strategy.
    }
  }

  throw new Error(`Could not click element for: ${description}`);
}

async function expectVisibleText(page, matcher, description) {
  const locators = candidateLocators(page, matcher);

  for (const locator of locators) {
    if ((await locator.count()) > 0) {
      await expect(locator.first(), description).toBeVisible();
      return;
    }
  }

  throw new Error(`Expected visible text not found: ${description}`);
}

async function validateLegalPageAndReturn(appPage, testInfo, linkMatcher, headingMatcher, screenshotName) {
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const navigationPromise = appPage
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 8000 })
    .catch(() => null);

  await clickByVisibleText(appPage, linkMatcher, String(linkMatcher), false);

  const popup = await popupPromise;
  let legalPage = appPage;

  if (popup) {
    legalPage = popup;
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  } else {
    await navigationPromise;
  }

  await waitForUiLoad(legalPage);
  await expectVisibleText(legalPage, headingMatcher, "Legal page heading should be visible");
  await expect(legalPage.locator("p").first(), "Legal content text should be visible").toBeVisible();

  await captureCheckpoint(legalPage, testInfo, screenshotName);
  const finalUrl = legalPage.url();

  if (popup) {
    await legalPage.close();
    await appPage.bringToFront();
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  }

  await waitForUiLoad(appPage);
  return finalUrl;
}

test("SaleADS Mi Negocio full workflow validation", async ({ page }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const details = {};
  const failures = [];

  async function runStep(stepName, action) {
    try {
      await action();
      report[stepName] = "PASS";
    } catch (error) {
      report[stepName] = "FAIL";
      failures.push(`${stepName}: ${error.message}`);
    }
  }

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  } else if (page.url().startsWith("about:blank")) {
    throw new Error("Set SALEADS_LOGIN_URL (or SALEADS_URL). The test avoids hardcoded environment URLs.");
  }

  await runStep("Login", async () => {
    const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickByVisibleText(
      page,
      /sign in with google|continuar con google|iniciar sesi[oó]n con google|google/i,
      "Google login button",
      false
    );

    const googlePage = await popupPromise;
    const authPage = googlePage || page;
    await waitForUiLoad(authPage);

    const emailOption = authPage.getByText(EMAIL).first();
    if ((await emailOption.count()) > 0 && (await emailOption.isVisible())) {
      await emailOption.click();
    }

    if (googlePage) {
      await googlePage.waitForEvent("close", { timeout: 30000 }).catch(() => {});
    }

    await waitForUiLoad(page);
    await expectVisibleText(page, /Negocio/i, "Sidebar should include Negocio");
    await expect(page.locator("aside, nav").first(), "Left sidebar navigation should be visible").toBeVisible();
    await captureCheckpoint(page, testInfo, "step-1-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, /^Negocio$/i, "Negocio section");
    await clickByVisibleText(page, /^Mi Negocio$/i, "Mi Negocio option");
    await expectVisibleText(page, /^Agregar Negocio$/i, "Agregar Negocio should be visible");
    await expectVisibleText(page, /^Administrar Negocios$/i, "Administrar Negocios should be visible");
    await captureCheckpoint(page, testInfo, "step-2-mi-negocio-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /^Agregar Negocio$/i, "Agregar Negocio");
    await expectVisibleText(page, /Crear Nuevo Negocio/i, "Modal title");
    await expectVisibleText(page, /Nombre del Negocio/i, "Nombre del Negocio input");
    await expectVisibleText(page, /Tienes 2 de 3 negocios/i, "Business quota text");
    await expectVisibleText(page, /^Cancelar$/i, "Cancelar button");
    await expectVisibleText(page, /^Crear Negocio$/i, "Crear Negocio button");

    const businessNameInput = page
      .locator('input[placeholder*="Nombre"], input[name*="nombre"], input[id*="nombre"]')
      .first();
    if ((await businessNameInput.count()) > 0 && (await businessNameInput.isVisible())) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
    }

    await captureCheckpoint(page, testInfo, "step-3-agregar-negocio-modal.png");
    await clickByVisibleText(page, /^Cancelar$/i, "Cancelar modal");
  });

  await runStep("Administrar Negocios view", async () => {
    if ((await page.getByText(/^Administrar Negocios$/i).count()) === 0) {
      await clickByVisibleText(page, /^Mi Negocio$/i, "Mi Negocio re-expand");
    }

    await clickByVisibleText(page, /^Administrar Negocios$/i, "Administrar Negocios");
    await expectVisibleText(page, /Informaci[oó]n General/i, "Información General section");
    await expectVisibleText(page, /Detalles de la Cuenta/i, "Detalles de la Cuenta section");
    await expectVisibleText(page, /Tus Negocios/i, "Tus Negocios section");
    await expectVisibleText(page, /Secci[oó]n Legal/i, "Sección Legal section");
    await captureCheckpoint(page, testInfo, "step-4-administrar-negocios-page.png");
  });

  await runStep("Información General", async () => {
    await expectVisibleText(page, /BUSINESS PLAN/i, "Business plan text");
    await expectVisibleText(page, /Cambiar Plan/i, "Cambiar Plan button");
    await expectVisibleText(page, /@/i, "User email");

    const userNameCandidate = page.locator("h1, h2, h3, strong").filter({ hasNotText: /BUSINESS PLAN/i }).first();
    await expect(userNameCandidate, "User name should be visible").toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expectVisibleText(page, /Cuenta creada/i, "Cuenta creada text");
    await expectVisibleText(page, /Estado activo/i, "Estado activo text");
    await expectVisibleText(page, /Idioma seleccionado/i, "Idioma seleccionado text");
  });

  await runStep("Tus Negocios", async () => {
    await expectVisibleText(page, /Tus Negocios/i, "Tus Negocios title");
    await expectVisibleText(page, /^Agregar Negocio$/i, "Agregar Negocio button in businesses");
    await expectVisibleText(page, /Tienes 2 de 3 negocios/i, "Business quota text in businesses");
  });

  await runStep("Términos y Condiciones", async () => {
    details.terminosUrl = await validateLegalPageAndReturn(
      page,
      testInfo,
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "step-8-terminos-y-condiciones.png"
    );
  });

  await runStep("Política de Privacidad", async () => {
    details.privacidadUrl = await validateLegalPageAndReturn(
      page,
      testInfo,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "step-9-politica-de-privacidad.png"
    );
  });

  const finalReport = {
    report,
    evidence: {
      "Términos y Condiciones URL": details.terminosUrl || "N/A",
      "Política de Privacidad URL": details.privacidadUrl || "N/A"
    },
    failures
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await testInfo.attach("final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });
  console.log(`FINAL_REPORT: ${JSON.stringify(finalReport)}`);

  await expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
