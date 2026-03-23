const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function createStepReport() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
    legalUrls: {
      terminosYCondiciones: "",
      politicaDePrivacidad: ""
    },
    notes: []
  };
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {
    // Some SPAs keep connections open; DOM ready is enough.
  });
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function safeCheck(stepName, report, fn) {
  try {
    await fn();
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = "FAIL";
    report.notes.push(`${stepName}: ${error.message}`);
  }
}

async function ensureAppPage(page) {
  const currentUrl = page.url();
  if (currentUrl === "about:blank") {
    const configuredUrl = process.env.SALEADS_URL || process.env.BASE_URL;
    if (!configuredUrl) {
      throw new Error(
        "Page is about:blank and no SALEADS_URL/BASE_URL was provided."
      );
    }
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }
}

async function capture(page, testInfo, name, fullPage = false) {
  const filePath = testInfo.outputPath(name);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, { path: filePath, contentType: "image/png" });
}

async function clickLegalAndValidate({
  page,
  context,
  testInfo,
  linkRegex,
  expectedHeadingRegex,
  screenshotName,
  urlField,
  report
}) {
  const legalEntry = page
    .getByRole("link", { name: linkRegex })
    .or(page.getByRole("button", { name: linkRegex }))
    .first();
  await expect(legalEntry).toBeVisible();

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await legalEntry.click();

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle").catch(() => {});
    await expect(popup.getByRole("heading", { name: expectedHeadingRegex }).first()).toBeVisible();
    await expect(popup.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóú]{20,}/);
    await capture(popup, testInfo, screenshotName, true);
    report.legalUrls[urlField] = popup.url();
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  await waitForUi(page);
  await expect(page.getByRole("heading", { name: expectedHeadingRegex }).first()).toBeVisible();
  await expect(page.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóú]{20,}/);
  await capture(page, testInfo, screenshotName, true);
  report.legalUrls[urlField] = page.url();
  await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createStepReport();

  await ensureAppPage(page);

  await safeCheck("Login", report, async () => {
    const googleButton = page
      .getByRole("button", {
        name: /google|sign in with google|continuar con google|iniciar sesión con google/i
      })
      .or(
        page.getByRole("link", {
          name: /google|sign in with google|continuar con google|iniciar sesión con google/i
        })
      )
      .first();

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(googleButton, page);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountOption = popup.getByText(ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }
      await popup.waitForLoadState("networkidle").catch(() => {});
      await popup.close().catch(() => {});
      await page.bringToFront();
    } else {
      const accountOption = page.getByText(ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await capture(page, testInfo, "checkpoint-1-dashboard.png", true);
  });

  await safeCheck("Mi Negocio menu", report, async () => {
    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible();

    const negocioSection = sidebar.getByText(/negocio/i).first();
    if (await negocioSection.isVisible().catch(() => false)) {
      await clickAndWait(negocioSection, page);
    }

    const miNegocio = sidebar.getByText(/mi negocio/i).first();
    await clickAndWait(miNegocio, page);

    await expect(sidebar.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(sidebar.getByText(/administrar negocios/i).first()).toBeVisible();
    await capture(page, testInfo, "checkpoint-2-mi-negocio-menu-expanded.png");
  });

  await safeCheck("Agregar Negocio modal", report, async () => {
    const sidebar = page.locator("aside, nav").first();
    const addBusiness = sidebar.getByText(/agregar negocio/i).first();
    await clickAndWait(addBusiness, page);

    const modal = page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(modal.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    const businessNameInput = modal.getByLabel(/nombre del negocio/i);
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await capture(page, testInfo, "checkpoint-3-crear-nuevo-negocio-modal.png");
    await clickAndWait(modal.getByRole("button", { name: /cancelar/i }), page);
    await expect(modal).toBeHidden();
  });

  await safeCheck("Administrar Negocios view", report, async () => {
    const sidebar = page.locator("aside, nav").first();
    const miNegocio = sidebar.getByText(/mi negocio/i).first();
    if (await miNegocio.isVisible().catch(() => false)) {
      await clickAndWait(miNegocio, page);
    }

    const manageBusinesses = sidebar.getByText(/administrar negocios/i).first();
    await clickAndWait(manageBusinesses, page);

    await expect(page.getByText(/información general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/sección legal/i).first()).toBeVisible();
    await capture(page, testInfo, "checkpoint-4-administrar-negocios-page.png", true);
  });

  await safeCheck("Información General", report, async () => {
    const infoGeneralSection = page
      .locator("section,div")
      .filter({ hasText: /información general/i })
      .first();

    await expect(infoGeneralSection).toContainText(/@/);
    await expect(infoGeneralSection).toContainText(/BUSINESS PLAN/i);
    await expect(infoGeneralSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    const hasUserName = await infoGeneralSection
      .locator("p,h1,h2,h3,h4,span,strong")
      .filter({ hasText: /[A-Za-zÁÉÍÓÚáéíóúÑñ]{3,}\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}/ })
      .first()
      .isVisible()
      .catch(() => false);
    expect(hasUserName).toBeTruthy();
  });

  await safeCheck("Detalles de la Cuenta", report, async () => {
    const accountDetailsSection = page
      .locator("section,div")
      .filter({ hasText: /detalles de la cuenta/i })
      .first();
    await expect(accountDetailsSection.getByText(/cuenta creada/i)).toBeVisible();
    await expect(accountDetailsSection.getByText(/estado activo/i)).toBeVisible();
    await expect(accountDetailsSection.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await safeCheck("Tus Negocios", report, async () => {
    const businessesSection = page
      .locator("section,div")
      .filter({ hasText: /tus negocios/i })
      .first();
    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(businessesSection.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await safeCheck("Términos y Condiciones", report, async () => {
    await clickLegalAndValidate({
      page,
      context,
      testInfo,
      linkRegex: /términos y condiciones|terminos y condiciones/i,
      expectedHeadingRegex: /términos y condiciones|terminos y condiciones/i,
      screenshotName: "checkpoint-5-terminos-y-condiciones.png",
      urlField: "terminosYCondiciones",
      report
    });
  });

  await safeCheck("Política de Privacidad", report, async () => {
    await clickLegalAndValidate({
      page,
      context,
      testInfo,
      linkRegex: /política de privacidad|politica de privacidad/i,
      expectedHeadingRegex: /política de privacidad|politica de privacidad/i,
      screenshotName: "checkpoint-6-politica-de-privacidad.png",
      urlField: "politicaDePrivacidad",
      report
    });
  });

  const reportJson = JSON.stringify(report, null, 2);
  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, reportJson, "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const summary = [
    `Login: ${report.Login}`,
    `Mi Negocio menu: ${report["Mi Negocio menu"]}`,
    `Agregar Negocio modal: ${report["Agregar Negocio modal"]}`,
    `Administrar Negocios view: ${report["Administrar Negocios view"]}`,
    `Información General: ${report["Información General"]}`,
    `Detalles de la Cuenta: ${report["Detalles de la Cuenta"]}`,
    `Tus Negocios: ${report["Tus Negocios"]}`,
    `Términos y Condiciones: ${report["Términos y Condiciones"]}`,
    `Política de Privacidad: ${report["Política de Privacidad"]}`,
    `URL Términos y Condiciones: ${report.legalUrls.terminosYCondiciones || "N/A"}`,
    `URL Política de Privacidad: ${report.legalUrls.politicaDePrivacidad || "N/A"}`
  ].join("\n");

  const summaryPath = testInfo.outputPath("saleads-mi-negocio-final-report.txt");
  await fs.writeFile(summaryPath, `${summary}\n`, "utf8");
  await testInfo.attach("final-report-summary", {
    path: summaryPath,
    contentType: "text/plain"
  });

  const failingSteps = Object.entries(report)
    .filter(([key, value]) => key !== "legalUrls" && key !== "notes")
    .filter(([, value]) => value !== "PASS")
    .map(([key]) => key);

  expect(
    failingSteps,
    `One or more validations failed.\n${summary}\nNotes:\n${report.notes.join("\n")}`
  ).toEqual([]);
});
