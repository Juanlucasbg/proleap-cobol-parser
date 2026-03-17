const { test, expect } = require("@playwright/test");

const REPORT_FIELDS = {
  LOGIN: "Login",
  MI_NEGOCIO_MENU: "Mi Negocio menu",
  AGREGAR_NEGOCIO_MODAL: "Agregar Negocio modal",
  ADMINISTRAR_NEGOCIOS_VIEW: "Administrar Negocios view",
  INFORMACION_GENERAL: "Informacion General",
  DETALLES_CUENTA: "Detalles de la Cuenta",
  TUS_NEGOCIOS: "Tus Negocios",
  TERMINOS: "Terminos y Condiciones",
  PRIVACIDAD: "Politica de Privacidad"
};

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.values(REPORT_FIELDS).reduce((acc, key) => {
    acc[key] = "FAIL";
    return acc;
  }, {});
  const errors = [];
  const evidence = {
    terminosUrl: null,
    privacidadUrl: null
  };

  const runStep = async (field, action) => {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      errors.push(`${field}: ${error.message}`);
      await page.screenshot({
        path: testInfo.outputPath(`failure-${field.replace(/\s+/g, "-").toLowerCase()}.png`),
        fullPage: true
      });
    }
  };

  const attachScreenshot = async (name, targetPage = page, fullPage = false) => {
    const screenshotPath = testInfo.outputPath(`${name}.png`);
    await targetPage.screenshot({ path: screenshotPath, fullPage });
    await testInfo.attach(name, {
      path: screenshotPath,
      contentType: "image/png"
    });
  };

  const waitForUiAfterClick = async () => {
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(500);
  };

  const clickVisibleText = async (textRegex) => {
    const candidate = page.getByText(textRegex).first();
    await expect(candidate).toBeVisible();
    await candidate.click();
    await waitForUiAfterClick();
  };

  const ensureOnLoginPage = async () => {
    if (page.url() === "about:blank") {
      const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
      if (!loginUrl) {
        throw new Error(
          "Browser is blank. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL for environment-agnostic navigation."
        );
      }
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }
  };

  await runStep(REPORT_FIELDS.LOGIN, async () => {
    await ensureOnLoginPage();
    const googleLoginButton = page
      .getByRole("button", {
        name: /sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google|google/i
      })
      .first();

    await expect(googleLoginButton).toBeVisible();

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await googleLoginButton.click();
    await waitForUiAfterClick();

    const googlePopup = await popupPromise;

    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      const account = googlePopup.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
      if (await account.isVisible().catch(() => false)) {
        await account.click();
      }
      await googlePopup.waitForTimeout(1000);
      await page.bringToFront();
    } else {
      const inlineAccount = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
      if (await inlineAccount.isVisible().catch(() => false)) {
        await inlineAccount.click();
        await waitForUiAfterClick();
      }
    }

    await expect(page.getByText(/negocio/i).first()).toBeVisible();
    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible();
    await attachScreenshot("01-dashboard-loaded");
  });

  await runStep(REPORT_FIELDS.MI_NEGOCIO_MENU, async () => {
    await clickVisibleText(/^mi negocio$/i);
    await expect(page.getByText(/^agregar negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^administrar negocios$/i).first()).toBeVisible();
    await attachScreenshot("02-mi-negocio-expanded");
  });

  await runStep(REPORT_FIELDS.AGREGAR_NEGOCIO_MODAL, async () => {
    await clickVisibleText(/^agregar negocio$/i);
    await expect(page.getByText(/^crear nuevo negocio$/i)).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^crear negocio$/i })).toBeVisible();

    await page.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatizacion");
    await attachScreenshot("03-crear-nuevo-negocio-modal");
    await page.getByRole("button", { name: /^cancelar$/i }).click();
    await waitForUiAfterClick();
  });

  await runStep(REPORT_FIELDS.ADMINISTRAR_NEGOCIOS_VIEW, async () => {
    const administrar = page.getByText(/^administrar negocios$/i).first();
    if (!(await administrar.isVisible().catch(() => false))) {
      await clickVisibleText(/^mi negocio$/i);
    }
    await clickVisibleText(/^administrar negocios$/i);

    await expect(page.getByText(/^informacion general$|^información general$/i).first()).toBeVisible();
    await expect(page.getByText(/^detalles de la cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/^tus negocios$/i).first()).toBeVisible();
    await expect(page.getByText(/^seccion legal$|^sección legal$/i).first()).toBeVisible();
    await attachScreenshot("04-administrar-negocios", page, true);
  });

  await runStep(REPORT_FIELDS.INFORMACION_GENERAL, async () => {
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^cambiar plan$/i })).toBeVisible();

    const infoSection = page.locator("section, div").filter({ hasText: /informacion general|información general/i }).first();
    await expect(infoSection).toContainText(/.+/);
  });

  await runStep(REPORT_FIELDS.DETALLES_CUENTA, async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep(REPORT_FIELDS.TUS_NEGOCIOS, async () => {
    await expect(page.getByText(/^tus negocios$/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^agregar negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
  });

  const validateLegalLink = async (linkTextRegex, headingRegex, screenshotName, evidenceField) => {
    const appPage = page;
    const previousUrl = appPage.url();
    const popupPromise = appPage.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await clickVisibleText(linkTextRegex);
    const popup = await popupPromise;

    const legalPage = popup || appPage;
    await legalPage.waitForLoadState("domcontentloaded");
    await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible();
    await expect(legalPage.locator("main, article, body").first()).toContainText(/.{40,}/);

    evidence[evidenceField] = legalPage.url();
    await attachScreenshot(screenshotName, legalPage, true);

    if (popup) {
      await popup.close();
      await appPage.bringToFront();
    } else if (appPage.url() !== previousUrl) {
      await appPage.goBack({ waitUntil: "domcontentloaded" });
      await appPage.bringToFront();
    }
  };

  await runStep(REPORT_FIELDS.TERMINOS, async () => {
    await validateLegalLink(
      /terminos y condiciones|términos y condiciones/i,
      /terminos y condiciones|términos y condiciones/i,
      "05-terminos-y-condiciones",
      "terminosUrl"
    );
  });

  await runStep(REPORT_FIELDS.PRIVACIDAD, async () => {
    await validateLegalLink(
      /politica de privacidad|política de privacidad/i,
      /politica de privacidad|política de privacidad/i,
      "06-politica-de-privacidad",
      "privacidadUrl"
    );
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    report,
    evidence,
    errors
  };

  await testInfo.attach("final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });

  if (errors.length > 0) {
    throw new Error(`One or more validations failed.\n${errors.join("\n")}`);
  }
});
