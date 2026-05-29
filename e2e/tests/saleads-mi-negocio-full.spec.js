const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const CHECK_TIMEOUT_MS = 8_000;
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
      if (!passed) {
        report[step].status = "FAIL";
      }
    };

    const waitForUiToLoad = async (targetPage) => {
      if (!targetPage || targetPage.isClosed()) {
        return;
      }

      await targetPage
        .waitForLoadState("domcontentloaded", { timeout: 30_000 })
        .catch(() => {});
      await targetPage
        .waitForLoadState("networkidle", { timeout: 10_000 })
        .catch(() => {});
    };

    const isVisible = async (locator, timeout = CHECK_TIMEOUT_MS) => {
      if (!locator) {
        return false;
      }

      return locator
        .first()
        .isVisible({ timeout })
        .catch(() => false);
    };

    const pickVisible = async (locators) => {
      for (const locator of locators) {
        if (await isVisible(locator, 2_500)) {
          return locator.first();
        }
      }
      return locators[0].first();
    };

    const clickAndWait = async (step, label, locator) => {
      if (!(await isVisible(locator))) {
        markFail(step, `${label} no es visible para click.`);
        return false;
      }

      try {
        await locator.first().click();
        await waitForUiToLoad(appPage);
        return true;
      } catch (error) {
        markFail(step, `Error al hacer click en '${label}'.`);
        note(step, String(error).split("\n")[0]);
        return false;
      }
    };

    const validateVisible = async (step, label, locator) => {
      const passed = await isVisible(locator);
      recordValidation(step, label, passed);
      if (!passed) {
        note(step, `${label} no visible.`);
      }
      return passed;
    };

    const saveScreenshot = async (
      step,
      targetPage,
      filename,
      fullPage = false,
    ) => {
      const screenshotPath = testInfo.outputPath(filename);
      try {
        if (!targetPage || targetPage.isClosed()) {
          markFail(step, `No se pudo tomar screenshot '${filename}': página cerrada.`);
          return;
        }

        await targetPage.screenshot({ path: screenshotPath, fullPage });
        report[step].evidence.push(screenshotPath);
      } catch (error) {
        markFail(step, `No se pudo tomar screenshot '${filename}'.`);
        note(step, String(error).split("\n")[0]);
      }
    };

    const finalizeReport = () => {
      for (const field of REPORT_FIELDS) {
        if (report[field].validations.length === 0 && report[field].notes.length === 0) {
          report[field].status = "FAIL";
          report[field].notes.push("Step was not executed.");
        }
      }
    };

    const writeFinalReport = async () => {
      finalizeReport();

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
      const googleLoginButton = await pickVisible([
        appPage.getByRole("button", { name: /sign in with google/i }),
        appPage.getByRole("button", { name: /iniciar sesión con google/i }),
        appPage.getByRole("button", { name: /continuar con google/i }),
        appPage.locator("button:has-text('Google')"),
        appPage.locator("a:has-text('Google')"),
      ]);

      if (await isVisible(googleLoginButton)) {
        const popupPromise = context
          .waitForEvent("page", { timeout: 8_000 })
          .catch(() => null);
        await clickAndWait("Login", "Google login", googleLoginButton);
        const popup = await popupPromise;

        const loginFlowPage = popup ?? appPage;
        await waitForUiToLoad(loginFlowPage);

        const accountSelector = loginFlowPage.getByText(ACCOUNT_EMAIL).first();
        if (await isVisible(accountSelector, 5_000)) {
          await accountSelector.click().catch(() => {});
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

      await validateVisible(
        "Login",
        "Main application interface appears",
        appPage.locator("main, [role='main']").first(),
      );
      await validateVisible("Login", "Left sidebar navigation is visible", leftSidebar);
      await saveScreenshot("Login", appPage, "01-dashboard-loaded.png", true);

      // Step 2 - Open Mi Negocio menu
      const negocioSection = await pickVisible([
        appPage.getByRole("button", { name: /^Negocio$/i }),
        appPage.getByText(/^Negocio$/i),
      ]);
      await clickAndWait("Mi Negocio menu", "Negocio", negocioSection);

      const miNegocioOption = await pickVisible([
        appPage.getByRole("button", { name: /^Mi Negocio$/i }),
        appPage.getByText(/^Mi Negocio$/i),
      ]);
      await clickAndWait("Mi Negocio menu", "Mi Negocio", miNegocioOption);

      const agregarNegocioItem = await pickVisible([
        appPage.getByRole("button", { name: /^Agregar Negocio$/i }),
        appPage.getByText(/^Agregar Negocio$/i),
      ]);
      const administrarNegociosItem = await pickVisible([
        appPage.getByRole("button", { name: /^Administrar Negocios$/i }),
        appPage.getByText(/^Administrar Negocios$/i),
      ]);

      await validateVisible(
        "Mi Negocio menu",
        "Confirm submenu expands",
        appPage.getByText(/Agregar Negocio/i).first(),
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
      await clickAndWait("Agregar Negocio modal", "Agregar Negocio", agregarNegocioItem);

      const modal = appPage.getByRole("dialog").first();
      await validateVisible(
        "Agregar Negocio modal",
        "Modal title 'Crear Nuevo Negocio' is visible",
        modal.getByText(/Crear Nuevo Negocio/i).first(),
      );

      const nombreNegocioInput = await pickVisible([
        modal.getByRole("textbox", { name: /Nombre del Negocio/i }),
        modal.getByLabel(/Nombre del Negocio/i),
        modal.locator("input[placeholder*='Nombre del Negocio']"),
      ]);

      await validateVisible(
        "Agregar Negocio modal",
        "Input field 'Nombre del Negocio' exists",
        nombreNegocioInput,
      );
      await validateVisible(
        "Agregar Negocio modal",
        "Text 'Tienes 2 de 3 negocios' is visible",
        modal.getByText(/Tienes 2 de 3 negocios/i).first(),
      );
      await validateVisible(
        "Agregar Negocio modal",
        "Button 'Cancelar' is present",
        modal.getByRole("button", { name: /Cancelar/i }).first(),
      );
      await validateVisible(
        "Agregar Negocio modal",
        "Button 'Crear Negocio' is present",
        modal.getByRole("button", { name: /Crear Negocio/i }).first(),
      );
      await saveScreenshot("Agregar Negocio modal", appPage, "03-agregar-negocio-modal.png");

      if (await isVisible(nombreNegocioInput, 2_500)) {
        await nombreNegocioInput.fill("Negocio Prueba Automatización").catch(() => {});
      }

      const cancelarButton = modal.getByRole("button", { name: /Cancelar/i }).first();
      await clickAndWait("Agregar Negocio modal", "Cancelar", cancelarButton);

      // Step 4 - Open Administrar Negocios
      if (!(await isVisible(administrarNegociosItem, 2_500))) {
        await clickAndWait("Administrar Negocios view", "Mi Negocio", miNegocioOption);
      }
      await clickAndWait(
        "Administrar Negocios view",
        "Administrar Negocios",
        administrarNegociosItem,
      );

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
        .filter({ has: appPage.getByText(/Información General/i).first() })
        .first();

      await validateVisible(
        "Información General",
        "User name is visible",
        infoGeneralSection
          .locator("h1, h2, h3, p, span")
          .filter({ hasNotText: /Información General|BUSINESS PLAN|Cambiar Plan/i })
          .first(),
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
        infoGeneralSection.getByText(/BUSINESS PLAN/i).first(),
      );
      await validateVisible(
        "Información General",
        "Button 'Cambiar Plan' is visible",
        infoGeneralSection.getByRole("button", { name: /Cambiar Plan/i }).first(),
      );

      // Step 6 - Validate Detalles de la Cuenta
      const detallesSection = appPage
        .locator("section, div")
        .filter({ has: appPage.getByText(/Detalles de la Cuenta/i).first() })
        .first();

      await validateVisible(
        "Detalles de la Cuenta",
        "'Cuenta creada' is visible",
        detallesSection.getByText(/Cuenta creada/i).first(),
      );
      await validateVisible(
        "Detalles de la Cuenta",
        "'Estado activo' is visible",
        detallesSection.getByText(/Estado activo/i).first(),
      );
      await validateVisible(
        "Detalles de la Cuenta",
        "'Idioma seleccionado' is visible",
        detallesSection.getByText(/Idioma seleccionado/i).first(),
      );

      // Step 7 - Validate Tus Negocios
      const tusNegociosSection = appPage
        .locator("section, div")
        .filter({ has: appPage.getByText(/Tus Negocios/i).first() })
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
        tusNegociosSection.getByText(/Tienes 2 de 3 negocios/i).first(),
      );

      const openLegalLinkAndValidate = async (
        step,
        linkText,
        headingRegex,
        screenshot,
      ) => {
        const legalLink = await pickVisible([
          appPage.getByRole("link", { name: new RegExp(linkText, "i") }),
          appPage.getByText(new RegExp(linkText, "i")),
        ]);

        const newTabPromise = context
          .waitForEvent("page", { timeout: 8_000 })
          .catch(() => null);

        if (!(await clickAndWait(step, linkText, legalLink))) {
          return;
        }

        const newTab = await newTabPromise;

        const targetPage = newTab ?? appPage;
        await waitForUiToLoad(targetPage);

        const headingLocator = await pickVisible([
          targetPage.getByRole("heading", { name: headingRegex }),
          targetPage.getByText(headingRegex),
        ]);

        await validateVisible(
          step,
          `The page contains the heading '${linkText}'`,
          headingLocator,
        );
        await validateVisible(
          step,
          "Legal content text is visible",
          targetPage.locator("main p, article p, p").first(),
        );

        note(step, `Final URL: ${targetPage.url()}`);
        await saveScreenshot(step, targetPage, screenshot, true);

        if (newTab) {
          await newTab.close().catch(() => {});
          await appPage.bringToFront().catch(() => {});
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
