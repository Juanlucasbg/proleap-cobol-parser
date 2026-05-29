const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

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
  "Política de Privacidad",
];

function createReportTemplate() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        status: "PASS",
        validations: [],
        evidence: [],
        notes: [],
      },
    ]),
  );
}

test.describe("SaleADS Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    const report = createReportTemplate();
    const appPage = page;

    const note = (step, message) => {
      report[step].notes.push(message);
    };

    const markFail = (step, message) => {
      report[step].status = "FAIL";
      report[step].notes.push(message);
    };

    const recordValidation = (step, label, passed) => {
      report[step].validations.push({ label, status: passed ? "PASS" : "FAIL" });
    };

    const waitForUiToLoad = async (targetPage) => {
      await targetPage
        .waitForLoadState("domcontentloaded", { timeout: 30_000 })
        .catch(() => {});
      await targetPage
        .waitForLoadState("networkidle", { timeout: 10_000 })
        .catch(() => {});
    };

    const clickAndWait = async (locator) => {
      await locator.click();
      await waitForUiToLoad(appPage);
    };

    const validateVisible = async (step, label, locator) => {
      let passed = false;
      try {
        await expect(locator).toBeVisible({ timeout: 20_000 });
        passed = true;
      } catch (error) {
        markFail(step, `${label} no visible.`);
        note(step, String(error).split("\n")[0]);
      }

      recordValidation(step, label, passed);
      return passed;
    };

    const saveScreenshot = async (
      step,
      targetPage,
      filename,
      fullPage = false,
    ) => {
      const screenshotPath = testInfo.outputPath(filename);
      await targetPage.screenshot({ path: screenshotPath, fullPage });
      report[step].evidence.push(screenshotPath);
    };

    const writeFinalReport = async () => {
      const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
      await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
      await testInfo.attach("saleads-mi-negocio-report", {
        path: reportPath,
        contentType: "application/json",
      });
      console.log(
        `SaleADS Mi Negocio final report:\n${JSON.stringify(report, null, 2)}`,
      );
    };

    let reportWritten = false;
    try {
      const targetUrl =
        process.env.SALEADS_LOGIN_URL ||
        process.env.SALEADS_BASE_URL ||
        process.env.BASE_URL;

      if (targetUrl) {
        await appPage.goto(targetUrl, { waitUntil: "domcontentloaded" });
      } else if (appPage.url() === "about:blank") {
        markFail(
          "Login",
          "No URL configured. Set SALEADS_LOGIN_URL, SALEADS_BASE_URL or BASE_URL.",
        );
      }

      await waitForUiToLoad(appPage);

      // Step 1 - Login with Google
      const googleLoginButton = appPage
        .locator(
          "button:has-text('Google'), [role='button']:has-text('Google'), a:has-text('Google')",
        )
        .first();

      if (await googleLoginButton.isVisible().catch(() => false)) {
        const popupPromise = context
          .waitForEvent("page", { timeout: 8_000 })
          .catch(() => null);
        await clickAndWait(googleLoginButton);
        const popup = await popupPromise;

        const loginFlowPage = popup ?? appPage;
        await waitForUiToLoad(loginFlowPage);

        const accountSelector = loginFlowPage.getByText(ACCOUNT_EMAIL).first();
        if (await accountSelector.isVisible().catch(() => false)) {
          await accountSelector.click();
          await waitForUiToLoad(loginFlowPage);
        }

        if (popup) {
          await popup.waitForEvent("close", { timeout: 45_000 }).catch(() => {});
          await appPage.bringToFront();
          await waitForUiToLoad(appPage);
        }
      } else {
        markFail(
          "Login",
          "No se encontró un botón visible para iniciar sesión con Google.",
        );
      }

      const leftSidebar = appPage
        .locator("aside, nav")
        .filter({ hasText: /negocio|mi negocio/i })
        .first();

      await validateVisible("Login", "Main application interface appears", appPage.locator("main, [role='main']").first());
      await validateVisible("Login", "Left sidebar navigation is visible", leftSidebar);
      await saveScreenshot("Login", appPage, "01-dashboard-loaded.png", true);

      // Step 2 - Open Mi Negocio menu
      const negocioSection = appPage
        .getByText(/^Negocio$/i)
        .or(appPage.getByRole("button", { name: /negocio/i }))
        .first();

      if (await negocioSection.isVisible().catch(() => false)) {
        await clickAndWait(negocioSection);
      } else {
        note("Mi Negocio menu", "No se encontró item exacto 'Negocio'.");
      }

      const miNegocioOption = appPage
        .getByText(/^Mi Negocio$/i)
        .or(appPage.getByRole("button", { name: /mi negocio/i }))
        .first();

      if (await miNegocioOption.isVisible().catch(() => false)) {
        await clickAndWait(miNegocioOption);
      } else {
        markFail("Mi Negocio menu", "No se encontró opción 'Mi Negocio'.");
      }

      const agregarNegocioItem = appPage
        .getByText(/^Agregar Negocio$/i)
        .or(appPage.getByRole("button", { name: /agregar negocio/i }))
        .first();
      const administrarNegociosItem = appPage
        .getByText(/^Administrar Negocios$/i)
        .or(appPage.getByRole("button", { name: /administrar negocios/i }))
        .first();

      await validateVisible(
        "Mi Negocio menu",
        "Confirm submenu expands",
        appPage.locator("text=Agregar Negocio").first(),
      );
      await validateVisible(
        "Mi Negocio menu",
        "Confirm 'Agregar Negocio' is visible",
        agregarNegocioItem,
      );
      await validateVisible(
        "Mi Negocio menu",
        "Confirm 'Administrar Negocios' is visible",
        administrarNegociosItem,
      );
      await saveScreenshot("Mi Negocio menu", appPage, "02-mi-negocio-expanded.png");

      // Step 3 - Validate Agregar Negocio modal
      if (await agregarNegocioItem.isVisible().catch(() => false)) {
        await clickAndWait(agregarNegocioItem);
      } else {
        markFail("Agregar Negocio modal", "No se pudo clickear 'Agregar Negocio'.");
      }

      const modal = appPage.getByRole("dialog").first();
      await validateVisible(
        "Agregar Negocio modal",
        "Modal title 'Crear Nuevo Negocio' is visible",
        modal.getByText(/Crear Nuevo Negocio/i),
      );
      const nombreNegocioInput = modal
        .getByRole("textbox", { name: /Nombre del Negocio/i })
        .or(modal.getByLabel(/Nombre del Negocio/i))
        .or(modal.locator("input[placeholder*='Nombre del Negocio']"))
        .first();

      await validateVisible(
        "Agregar Negocio modal",
        "Input field 'Nombre del Negocio' exists",
        nombreNegocioInput,
      );
      await validateVisible(
        "Agregar Negocio modal",
        "Text 'Tienes 2 de 3 negocios' is visible",
        modal.getByText(/Tienes 2 de 3 negocios/i),
      );
      await validateVisible(
        "Agregar Negocio modal",
        "Button 'Cancelar' is present",
        modal.getByRole("button", { name: /Cancelar/i }),
      );
      await validateVisible(
        "Agregar Negocio modal",
        "Button 'Crear Negocio' is present",
        modal.getByRole("button", { name: /Crear Negocio/i }),
      );
      await saveScreenshot("Agregar Negocio modal", appPage, "03-agregar-negocio-modal.png");

      if (await nombreNegocioInput.isVisible().catch(() => false)) {
        await nombreNegocioInput.fill("Negocio Prueba Automatización");
      }

      const cancelarButton = modal.getByRole("button", { name: /Cancelar/i });
      if (await cancelarButton.isVisible().catch(() => false)) {
        await clickAndWait(cancelarButton);
      } else {
        markFail("Agregar Negocio modal", "No se encontró botón 'Cancelar'.");
      }

      // Step 4 - Open Administrar Negocios
      if (!(await administrarNegociosItem.isVisible().catch(() => false))) {
        const reopenMiNegocio = appPage
          .getByText(/^Mi Negocio$/i)
          .or(appPage.getByRole("button", { name: /mi negocio/i }))
          .first();
        if (await reopenMiNegocio.isVisible().catch(() => false)) {
          await clickAndWait(reopenMiNegocio);
        }
      }

      if (await administrarNegociosItem.isVisible().catch(() => false)) {
        await clickAndWait(administrarNegociosItem);
      } else {
        markFail(
          "Administrar Negocios view",
          "No se encontró opción 'Administrar Negocios'.",
        );
      }

      await validateVisible(
        "Administrar Negocios view",
        "Section 'Información General' exists",
        appPage.getByText(/Información General/i).first(),
      );
      await validateVisible(
        "Administrar Negocios view",
        "Section 'Detalles de la Cuenta' exists",
        appPage.getByText(/Detalles de la Cuenta/i).first(),
      );
      await validateVisible(
        "Administrar Negocios view",
        "Section 'Tus Negocios' exists",
        appPage.getByText(/Tus Negocios/i).first(),
      );
      await validateVisible(
        "Administrar Negocios view",
        "Section 'Sección Legal' exists",
        appPage.getByText(/Sección Legal/i).first(),
      );
      await saveScreenshot(
        "Administrar Negocios view",
        appPage,
        "04-administrar-negocios.png",
        true,
      );

      // Step 5 - Validate Información General
      const infoGeneralSection = appPage
        .locator("section, div")
        .filter({ has: appPage.getByText(/Información General/i) })
        .first();

      await validateVisible(
        "Información General",
        "User name is visible",
        infoGeneralSection.locator("h1, h2, h3, p, span").filter({
          hasNotText: /Información General|BUSINESS PLAN|Cambiar Plan/i,
        }).first(),
      );
      await validateVisible(
        "Información General",
        "User email is visible",
        infoGeneralSection.getByText(
          /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/,
        ),
      );
      await validateVisible(
        "Información General",
        "Text 'BUSINESS PLAN' is visible",
        infoGeneralSection.getByText(/BUSINESS PLAN/i),
      );
      await validateVisible(
        "Información General",
        "Button 'Cambiar Plan' is visible",
        infoGeneralSection.getByRole("button", { name: /Cambiar Plan/i }),
      );

      // Step 6 - Validate Detalles de la Cuenta
      const detallesSection = appPage
        .locator("section, div")
        .filter({ has: appPage.getByText(/Detalles de la Cuenta/i) })
        .first();

      await validateVisible(
        "Detalles de la Cuenta",
        "'Cuenta creada' is visible",
        detallesSection.getByText(/Cuenta creada/i),
      );
      await validateVisible(
        "Detalles de la Cuenta",
        "'Estado activo' is visible",
        detallesSection.getByText(/Estado activo/i),
      );
      await validateVisible(
        "Detalles de la Cuenta",
        "'Idioma seleccionado' is visible",
        detallesSection.getByText(/Idioma seleccionado/i),
      );

      // Step 7 - Validate Tus Negocios
      const tusNegociosSection = appPage
        .locator("section, div")
        .filter({ has: appPage.getByText(/Tus Negocios/i) })
        .first();

      await validateVisible(
        "Tus Negocios",
        "Business list is visible",
        tusNegociosSection
          .locator("[role='list'], ul, [role='table'], [role='rowgroup'], .card")
          .first(),
      );
      await validateVisible(
        "Tus Negocios",
        "Button 'Agregar Negocio' exists",
        tusNegociosSection
          .getByRole("button", { name: /Agregar Negocio/i })
          .first(),
      );
      await validateVisible(
        "Tus Negocios",
        "Text 'Tienes 2 de 3 negocios' is visible",
        tusNegociosSection.getByText(/Tienes 2 de 3 negocios/i),
      );

      const openLegalLinkAndValidate = async (step, linkText, headingRegex, screenshot) => {
        const legalLink = appPage
          .getByRole("link", { name: new RegExp(linkText, "i") })
          .or(appPage.getByText(new RegExp(linkText, "i")))
          .first();

        if (!(await legalLink.isVisible().catch(() => false))) {
          markFail(step, `No se encontró link '${linkText}'.`);
          return;
        }

        const newTabPromise = context
          .waitForEvent("page", { timeout: 8_000 })
          .catch(() => null);
        await clickAndWait(legalLink);
        const newTab = await newTabPromise;

        const targetPage = newTab ?? appPage;
        await waitForUiToLoad(targetPage);

        await validateVisible(
          step,
          `The page contains the heading '${linkText}'`,
          targetPage
            .getByRole("heading", { name: headingRegex })
            .or(targetPage.getByText(headingRegex))
            .first(),
        );
        await validateVisible(
          step,
          "Legal content text is visible",
          targetPage.locator("main p, article p, p").first(),
        );

        const legalUrl = targetPage.url();
        note(step, `Final URL: ${legalUrl}`);

        await saveScreenshot(step, targetPage, screenshot, true);

        if (newTab) {
          await newTab.close().catch(() => {});
          await appPage.bringToFront();
          await waitForUiToLoad(appPage);
        } else {
          await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
          await waitForUiToLoad(appPage);
        }
      };

      // Step 8 - Validate Términos y Condiciones
      await openLegalLinkAndValidate(
        "Términos y Condiciones",
        "Términos y Condiciones",
        /Términos y Condiciones/i,
        "05-terminos-y-condiciones.png",
      );

      // Step 9 - Validate Política de Privacidad
      await openLegalLinkAndValidate(
        "Política de Privacidad",
        "Política de Privacidad",
        /Política de Privacidad/i,
        "06-politica-de-privacidad.png",
      );

      await writeFinalReport();
      reportWritten = true;

      const failedSteps = Object.entries(report).filter(
        ([, value]) => value.status === "FAIL",
      );
      expect(
        failedSteps,
        `One or more workflow validations failed.\n${JSON.stringify(
          report,
          null,
          2,
        )}`,
      ).toHaveLength(0);
    } finally {
      if (!reportWritten) {
        await writeFinalReport();
      }
    }
  });
});
