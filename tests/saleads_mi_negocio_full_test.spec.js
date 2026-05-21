const fs = require("fs/promises");
const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toRegexMatchers(matcher) {
  if (matcher instanceof RegExp) {
    return [matcher];
  }

  const exact = new RegExp(`^\\s*${escapeRegex(matcher)}\\s*$`, "i");
  const partial = new RegExp(escapeRegex(matcher), "i");
  return [exact, partial];
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

async function resolveVisibleByText(page, matcher) {
  for (const regex of toRegexMatchers(matcher)) {
    const candidates = [
      page.getByRole("button", { name: regex }).first(),
      page.getByRole("link", { name: regex }).first(),
      page.getByRole("menuitem", { name: regex }).first(),
      page.getByRole("tab", { name: regex }).first(),
      page.getByText(regex).first()
    ];

    for (const locator of candidates) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
  }

  return null;
}

async function clickByVisibleText(page, matcher, label = matcher.toString()) {
  const locator = await resolveVisibleByText(page, matcher);
  expect(locator, `Expected visible element with text ${label}`).not.toBeNull();
  await locator.click();
  await waitForUi(page);
}

async function expectVisibleText(page, matcher, label = matcher.toString()) {
  const locator = await resolveVisibleByText(page, matcher);
  expect(locator, `Expected visible text ${label}`).not.toBeNull();
  await expect(locator).toBeVisible();
}

async function saveScreenshot(page, testInfo, fileName, fullPage = false) {
  const filePath = testInfo.outputPath(fileName);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(fileName, { path: filePath, contentType: "image/png" });
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const errors = [];
  const legalUrls = {};

  const runStep = async (field, fn) => {
    try {
      await fn();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      errors.push(`[${field}] ${error.message}`);
    }
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  }
  await waitForUi(page);

  await runStep("Login", async () => {
    const sidebar = page.locator("aside, nav").filter({ hasText: /mi negocio|negocio/i }).first();
    const alreadyLoggedIn = await sidebar.isVisible().catch(() => false);

    if (!alreadyLoggedIn) {
      const loginButton =
        (await resolveVisibleByText(page, /sign in with google/i)) ||
        (await resolveVisibleByText(page, /iniciar sesi[oó]n con google/i)) ||
        (await resolveVisibleByText(page, /continuar con google/i));

      expect(loginButton, "Google login button was not found").not.toBeNull();

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await loginButton.click();
      await waitForUi(page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const accountOption = popup.getByText(ACCOUNT_EMAIL, { exact: true }).first();
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
        }
        await popup.waitForClose({ timeout: 45000 }).catch(() => {});
      } else {
        const accountOption = page.getByText(ACCOUNT_EMAIL, { exact: true }).first();
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
          await waitForUi(page);
        }
      }
    }

    await expect(page.locator("aside, nav").first()).toBeVisible();
    await saveScreenshot(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await resolveVisibleByText(page, /negocio/i);
    if (negocioSection) {
      await negocioSection.click();
      await waitForUi(page);
    }

    await clickByVisibleText(page, /mi negocio/i, "Mi Negocio");
    await expectVisibleText(page, /agregar negocio/i, "Agregar Negocio");
    await expectVisibleText(page, /administrar negocios/i, "Administrar Negocios");
    await saveScreenshot(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /agregar negocio/i, "Agregar Negocio");
    await expectVisibleText(page, /crear nuevo negocio/i, "Crear Nuevo Negocio");
    await expectVisibleText(page, /nombre del negocio/i, "Nombre del Negocio");
    await expectVisibleText(page, /tienes 2 de 3 negocios/i, "Tienes 2 de 3 negocios");
    await expectVisibleText(page, /cancelar/i, "Cancelar");
    await expectVisibleText(page, /crear negocio/i, "Crear Negocio");
    await saveScreenshot(page, testInfo, "03-agregar-negocio-modal.png");

    const labelInput = page.getByLabel(/nombre del negocio/i).first();
    const placeholderInput = page.getByPlaceholder(/nombre del negocio/i).first();
    if (await labelInput.isVisible().catch(() => false)) {
      await labelInput.fill("Negocio Prueba Automatizacion");
      await waitForUi(page);
    } else if (await placeholderInput.isVisible().catch(() => false)) {
      await placeholderInput.fill("Negocio Prueba Automatizacion");
      await waitForUi(page);
    }
    await clickByVisibleText(page, /cancelar/i, "Cancelar");
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarLink = await resolveVisibleByText(page, /administrar negocios/i);
    if (!administrarLink) {
      await clickByVisibleText(page, /mi negocio/i, "Mi Negocio");
    }
    await clickByVisibleText(page, /administrar negocios/i, "Administrar Negocios");
    await expectVisibleText(page, /informaci[oó]n general/i, "Información General");
    await expectVisibleText(page, /detalles de la cuenta/i, "Detalles de la Cuenta");
    await expectVisibleText(page, /tus negocios/i, "Tus Negocios");
    await expectVisibleText(page, /secci[oó]n legal/i, "Sección Legal");
    await saveScreenshot(page, testInfo, "04-administrar-negocios-view.png", true);
  });

  await runStep("Información General", async () => {
    const emailPattern = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    await expect(page.getByText(emailPattern).first()).toBeVisible();

    const userLabel = page.getByText(/nombre( de usuario)?/i).first();
    const profileName = page.getByText(/^[A-Za-zÀ-ÿ]+(?:\s+[A-Za-zÀ-ÿ]+){1,3}$/).first();
    if (await userLabel.isVisible().catch(() => false)) {
      await expect(userLabel).toBeVisible();
    } else {
      await expect(profileName).toBeVisible();
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expectVisibleText(page, /cambiar plan/i, "Cambiar Plan");
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expectVisibleText(page, /cuenta creada/i, "Cuenta creada");
    await expectVisibleText(page, /estado activo/i, "Estado activo");
    await expectVisibleText(page, /idioma seleccionado/i, "Idioma seleccionado");
  });

  await runStep("Tus Negocios", async () => {
    await expectVisibleText(page, /tus negocios/i, "Tus Negocios");
    await expectVisibleText(page, /agregar negocio/i, "Agregar Negocio");
    await expectVisibleText(page, /tienes 2 de 3 negocios/i, "Tienes 2 de 3 negocios");
  });

  const validateLegalLink = async (reportField, linkMatcher, headingMatcher, screenshotName) => {
    await runStep(reportField, async () => {
      const originalUrl = page.url();
      const newTabPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

      await clickByVisibleText(page, linkMatcher, reportField);

      const newTab = await newTabPromise;
      const targetPage = newTab || page;
      await targetPage.waitForLoadState("domcontentloaded");
      await targetPage.waitForTimeout(1000);

      const heading = targetPage.getByRole("heading", { name: headingMatcher }).first();
      if (await heading.isVisible().catch(() => false)) {
        await expect(heading).toBeVisible();
      } else {
        await expect(targetPage.getByText(headingMatcher).first()).toBeVisible();
      }

      await saveScreenshot(targetPage, testInfo, screenshotName, true);
      legalUrls[reportField] = targetPage.url();

      if (newTab) {
        await newTab.close();
        await page.bringToFront();
        await waitForUi(page);
      } else if (page.url() !== originalUrl) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }
    });
  };

  await validateLegalLink(
    "Términos y Condiciones",
    /t[ée]rminos y condiciones/i,
    /t[ée]rminos y condiciones/i,
    "05-terminos-y-condiciones.png"
  );
  await validateLegalLink(
    "Política de Privacidad",
    /pol[ií]tica de privacidad/i,
    /pol[ií]tica de privacidad/i,
    "06-politica-de-privacidad.png"
  );

  const finalReport = {
    report,
    legalUrls,
    errors
  };
  const reportPath = testInfo.outputPath("saleads_mi_negocio_final_report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads_mi_negocio_final_report", {
    path: reportPath,
    contentType: "application/json"
  });
  console.log("saleads_mi_negocio_full_test report:", JSON.stringify(finalReport, null, 2));

  expect(errors, "One or more workflow steps failed").toEqual([]);
});
