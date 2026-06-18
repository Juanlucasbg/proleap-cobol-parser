const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.setTimeout(240000);

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || process.env.BASE_URL;
  const runAt = new Date().toISOString();
  const report = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
  const failureDetails = [];
  const legalUrls = {};

  const evidenceDir = path.join(testInfo.outputDir, "checkpoints");
  fs.mkdirSync(evidenceDir, { recursive: true });

  const saveScreenshot = async (name, targetPage = page, fullPage = false) => {
    const filePath = path.join(evidenceDir, `${name}.png`);
    await targetPage.screenshot({ path: filePath, fullPage });
    await testInfo.attach(name, { path: filePath, contentType: "image/png" });
  };

  const waitForUiLoad = async (targetPage = page) => {
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {
      // Some UI actions do not trigger network traffic consistently.
    });
  };

  const expectVisibleWithFallback = async (description, locators) => {
    for (const locator of locators) {
      if (await locator.isVisible({ timeout: 5000 }).catch(() => false)) {
        return locator;
      }
    }
    throw new Error(`${description} is not visible.`);
  };

  const clickAndWait = async (targetPage, description, locators) => {
    const locator = await expectVisibleWithFallback(description, locators);
    await locator.click();
    await waitForUiLoad(targetPage);
  };

  const runValidation = async (reportField, fn) => {
    try {
      await fn();
      report[reportField] = "PASS";
    } catch (error) {
      report[reportField] = "FAIL";
      failureDetails.push(`${reportField}: ${error.message}`);
    }
  };

  await runValidation("Login", async () => {
    expect(loginUrl, "Set SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL) before running this test.").toBeTruthy();
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);

    const loginCandidates = [
      page.getByRole("button", { name: /sign in with google|continuar con google|iniciar sesi[oó]n con google/i }).first(),
      page.getByText(/sign in with google|continuar con google|iniciar sesi[oó]n con google/i).first(),
      page.getByRole("button", { name: /google/i }).first()
    ];

    const loginButton = await expectVisibleWithFallback("Google login button", loginCandidates);
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUiLoad(page);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      const accountOption = googlePopup.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
      if (await accountOption.isVisible({ timeout: 5000 }).catch(() => false)) {
        await accountOption.click();
      }
      await waitForUiLoad(page);
    } else {
      const accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
      if (await accountOption.isVisible({ timeout: 5000 }).catch(() => false)) {
        await accountOption.click();
        await waitForUiLoad(page);
      }
    }

    const sidebarCandidates = [
      page.locator("aside").first(),
      page.getByText(/negocio/i).first(),
      page.getByText(/mi negocio/i).first()
    ];
    await expectVisibleWithFallback("main app sidebar", sidebarCandidates);
    await saveScreenshot("01-dashboard-loaded", page, true);
  });

  await runValidation("Mi Negocio menu", async () => {
    const negocioSection = await expectVisibleWithFallback("Negocio section", [
      page.getByText(/^Negocio$/i).first(),
      page.getByText(/negocio/i).first()
    ]);
    await expect(negocioSection).toBeVisible();

    await clickAndWait(page, "Mi Negocio option", [
      page.getByText(/^Mi Negocio$/i).first(),
      page.getByRole("button", { name: /mi negocio/i }).first(),
      page.getByRole("link", { name: /mi negocio/i }).first()
    ]);

    await expectVisibleWithFallback("Agregar Negocio submenu", [
      page.getByText(/^Agregar Negocio$/i).first(),
      page.getByRole("link", { name: /agregar negocio/i }).first(),
      page.getByRole("button", { name: /agregar negocio/i }).first()
    ]);
    await expectVisibleWithFallback("Administrar Negocios submenu", [
      page.getByText(/^Administrar Negocios$/i).first(),
      page.getByRole("link", { name: /administrar negocios/i }).first(),
      page.getByRole("button", { name: /administrar negocios/i }).first()
    ]);

    await saveScreenshot("02-mi-negocio-expanded", page, true);
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickAndWait(page, "Agregar Negocio option", [
      page.getByRole("link", { name: /^Agregar Negocio$/i }).first(),
      page.getByRole("button", { name: /^Agregar Negocio$/i }).first(),
      page.getByText(/^Agregar Negocio$/i).first()
    ]);

    await expectVisibleWithFallback("Crear Nuevo Negocio title", [
      page.getByRole("heading", { name: /crear nuevo negocio/i }).first(),
      page.getByText(/crear nuevo negocio/i).first()
    ]);
    const nameInput = await expectVisibleWithFallback("Nombre del Negocio input", [
      page.getByLabel(/nombre del negocio/i).first(),
      page.getByPlaceholder(/nombre del negocio/i).first(),
      page.locator('input[name*="negocio" i], input[placeholder*="Negocio" i]').first()
    ]);
    await expectVisibleWithFallback("business slots text", [page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i).first()]);
    await expectVisibleWithFallback("Cancelar button", [page.getByRole("button", { name: /^Cancelar$/i }).first()]);
    await expectVisibleWithFallback("Crear Negocio button", [page.getByRole("button", { name: /^Crear Negocio$/i }).first()]);

    await saveScreenshot("03-agregar-negocio-modal", page);
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, "Cancelar button", [page.getByRole("button", { name: /^Cancelar$/i }).first()]);
  });

  await runValidation("Administrar Negocios view", async () => {
    // Menu can collapse after closing the modal, so we ensure it is expanded.
    const adminVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
    if (!adminVisible) {
      await clickAndWait(page, "Mi Negocio option", [
        page.getByText(/^Mi Negocio$/i).first(),
        page.getByRole("button", { name: /mi negocio/i }).first(),
        page.getByRole("link", { name: /mi negocio/i }).first()
      ]);
    }

    await clickAndWait(page, "Administrar Negocios option", [
      page.getByRole("link", { name: /^Administrar Negocios$/i }).first(),
      page.getByRole("button", { name: /^Administrar Negocios$/i }).first(),
      page.getByText(/^Administrar Negocios$/i).first()
    ]);

    await expectVisibleWithFallback("Información General section", [page.getByText(/informaci[oó]n general/i).first()]);
    await expectVisibleWithFallback("Detalles de la Cuenta section", [page.getByText(/detalles de la cuenta/i).first()]);
    await expectVisibleWithFallback("Tus Negocios section", [page.getByText(/tus negocios/i).first()]);
    await expectVisibleWithFallback("Sección Legal section", [page.getByText(/secci[oó]n legal/i).first()]);

    await saveScreenshot("04-administrar-negocios-page", page, true);
  });

  await runValidation("Información General", async () => {
    await expectVisibleWithFallback("user name", [
      page.locator('[data-testid*="name" i]').first(),
      page.locator("h1,h2,strong").filter({ hasText: /.+/ }).first()
    ]);
    await expectVisibleWithFallback("user email", [page.getByText(/@/i).first()]);
    await expectVisibleWithFallback("BUSINESS PLAN text", [page.getByText(/BUSINESS PLAN/i).first()]);
    await expectVisibleWithFallback("Cambiar Plan button", [page.getByRole("button", { name: /cambiar plan/i }).first()]);
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expectVisibleWithFallback("Cuenta creada", [page.getByText(/cuenta creada/i).first()]);
    await expectVisibleWithFallback("Estado activo", [page.getByText(/estado activo/i).first()]);
    await expectVisibleWithFallback("Idioma seleccionado", [page.getByText(/idioma seleccionado/i).first()]);
  });

  await runValidation("Tus Negocios", async () => {
    await expectVisibleWithFallback("business list", [
      page.locator("ul,table,div").filter({ hasText: /negocio/i }).first(),
      page.getByText(/tus negocios/i).first()
    ]);
    await expectVisibleWithFallback("Agregar Negocio button", [
      page.getByRole("button", { name: /agregar negocio/i }).first(),
      page.getByRole("link", { name: /agregar negocio/i }).first()
    ]);
    await expectVisibleWithFallback("Tienes 2 de 3 negocios text", [page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i).first()]);
  });

  const validateLegalPage = async (reportField, linkNameRegex, headingRegex, screenshotName, urlKey) => {
    await runValidation(reportField, async () => {
      const legalLink = await expectVisibleWithFallback(`${reportField} link`, [
        page.getByRole("link", { name: linkNameRegex }).first(),
        page.getByText(linkNameRegex).first()
      ]);

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await legalLink.click();
      await waitForUiLoad(page);

      const popup = await popupPromise;
      const legalPage = popup || page;
      await waitForUiLoad(legalPage);

      const headingLocator = await expectVisibleWithFallback(`${reportField} heading`, [
        legalPage.getByRole("heading", { name: headingRegex }).first(),
        legalPage.getByText(headingRegex).first()
      ]);
      await expect(headingLocator).toBeVisible();

      const bodyText = (await legalPage.locator("body").innerText()).trim();
      expect(bodyText.length, `${reportField} content should not be empty.`).toBeGreaterThan(120);
      legalUrls[urlKey] = legalPage.url();

      await saveScreenshot(screenshotName, legalPage, true);

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {
          // If history navigation is blocked we keep current page and continue.
        });
      }
      await waitForUiLoad(page);
    });
  };

  await validateLegalPage(
    "Términos y Condiciones",
    /t[ée]rminos y condiciones/i,
    /t[ée]rminos y condiciones/i,
    "05-terminos-condiciones",
    "terminosYCondicionesUrl"
  );

  await validateLegalPage(
    "Política de Privacidad",
    /pol[íi]tica de privacidad/i,
    /pol[íi]tica de privacidad/i,
    "06-politica-privacidad",
    "politicaDePrivacidadUrl"
  );

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    executedAt: runAt,
    environment: loginUrl || "unknown",
    validations: report,
    legalUrls,
    failures: failureDetails
  };

  const reportPath = path.join(testInfo.outputDir, "saleads-mi-negocio-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });
  console.log("SaleADS Mi Negocio validation report:");
  console.log(JSON.stringify(finalReport, null, 2));

  const failedFields = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);

  expect(
    failedFields,
    `The following validation groups failed: ${failedFields.join(", ")}`
  ).toEqual([]);
});
