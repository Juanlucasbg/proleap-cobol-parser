const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL_ENV = "SALEADS_LOGIN_URL";

test.describe("SaleADS Mi Negocio module workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    test.setTimeout(10 * 60 * 1000);

    const results = {
      Login: false,
      "Mi Negocio menu": false,
      "Agregar Negocio modal": false,
      "Administrar Negocios view": false,
      "Información General": false,
      "Detalles de la Cuenta": false,
      "Tus Negocios": false,
      "Términos y Condiciones": false,
      "Política de Privacidad": false,
    };
    const legalUrls = {
      "Términos y Condiciones": null,
      "Política de Privacidad": null,
    };

    let appPage = page;
    let appUrl = page.url();

    const clickByText = async (text) => {
      const locator = page.getByText(text, { exact: true });
      await expect(locator).toBeVisible();
      await locator.click();
      await page.waitForLoadState("networkidle");
    };

    const clickFirstVisible = async (locators) => {
      for (const locator of locators) {
        if (await locator.first().isVisible().catch(() => false)) {
          await locator.first().click();
          await page.waitForLoadState("networkidle");
          return true;
        }
      }
      return false;
    };

    const screenshot = async (name, fullPage = false) => {
      await page.screenshot({
        path: testInfo.outputPath(`${name}.png`),
        fullPage,
      });
    };

    const mark = (key, value) => {
      results[key] = value;
      testInfo.annotations.push({ type: "validation", description: `${key}: ${value ? "PASS" : "FAIL"}` });
    };

    const isGoogleDomain = (url) => /accounts\.google\.com|google\.com/i.test(url);

    try {
      // Optional entrypoint for any environment without hardcoded domains.
      const loginUrlFromEnv = process.env[SALEADS_LOGIN_URL_ENV];
      if (loginUrlFromEnv) {
        await page.goto(loginUrlFromEnv, { waitUntil: "networkidle" });
      } else if (page.url() === "about:blank") {
        throw new Error(
          `No login page loaded. Set ${SALEADS_LOGIN_URL_ENV} to the current environment login URL, or run this test with the browser already on SaleADS login page.`
        );
      }

      // Step 1: Login with Google from current page (no hardcoded URL).
      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      const loginClicked = await clickFirstVisible([
        page.getByRole("button", { name: /sign in with google|google|iniciar con google|continuar con google/i }),
        page.getByRole("link", { name: /sign in with google|google|iniciar con google|continuar con google/i }),
        page.locator("button:has-text('Google')"),
        page.locator("a:has-text('Google')"),
      ]);

      expect(loginClicked).toBeTruthy();

      // Handle popup or same-tab Google selector.
      let googlePage = page;
      const popup = await popupPromise;
      if (popup) {
        googlePage = popup;
      } else {
        const otherOpenPage = context
          .pages()
          .find((candidatePage) => candidatePage !== page && !candidatePage.isClosed());
        if (otherOpenPage) {
          googlePage = otherOpenPage;
        }
      }

      await googlePage.waitForLoadState("domcontentloaded");

      if (isGoogleDomain(googlePage.url())) {
        const accountOption = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
          await googlePage.waitForLoadState("networkidle");
        }
      }

      // Ensure we are back to app page and dashboard/sidebar is visible.
      if (googlePage !== page) {
        await page.bringToFront();
      }

      await expect(page.locator("aside, nav")).toBeVisible({ timeout: 45000 });
      await expect(page.locator("aside, nav").first()).toBeVisible();
      appUrl = page.url();

      await screenshot("01-dashboard-loaded");
      mark("Login", true);

      // Step 2: Open Mi Negocio menu.
      await clickByText("Negocio");
      await clickByText("Mi Negocio");
      await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
      await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();
      await screenshot("02-mi-negocio-menu-expanded");
      mark("Mi Negocio menu", true);

      // Step 3: Validate Agregar Negocio modal.
      await clickByText("Agregar Negocio");
      await expect(page.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
      const negocioNameInput = page
        .getByLabel("Nombre del Negocio")
        .or(page.getByPlaceholder("Nombre del Negocio"))
        .or(page.locator("input[name*='nombre'], input[placeholder*='Nombre del Negocio']"));
      await expect(negocioNameInput.first()).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
      await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible();
      await expect(page.getByRole("button", { name: "Crear Negocio" })).toBeVisible();
      await screenshot("03-agregar-negocio-modal");

      if (await negocioNameInput.first().isVisible().catch(() => false)) {
        await negocioNameInput.first().click();
        await negocioNameInput.first().fill("Negocio Prueba Automatización");
      }
      await page.getByRole("button", { name: "Cancelar" }).click();
      await page.waitForLoadState("networkidle");
      mark("Agregar Negocio modal", true);

      // Step 4: Open Administrar Negocios.
      if (!(await page.getByText("Administrar Negocios", { exact: true }).isVisible().catch(() => false))) {
        await clickByText("Mi Negocio");
      }

      await clickByText("Administrar Negocios");
      await expect(page.getByText("Información General", { exact: true })).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
      await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible();
      await screenshot("04-administrar-negocios", true);
      mark("Administrar Negocios view", true);

      // Step 5: Información General.
      const infoGeneralOk =
        (await page.getByText(/@/).first().isVisible().catch(() => false)) &&
        (await page.getByText("BUSINESS PLAN", { exact: true }).isVisible().catch(() => false)) &&
        (await page.getByRole("button", { name: "Cambiar Plan" }).isVisible().catch(() => false));
      mark("Información General", infoGeneralOk);
      expect(infoGeneralOk).toBeTruthy();

      // Step 6: Detalles de la Cuenta.
      const detallesCuentaOk =
        (await page.getByText("Cuenta creada", { exact: true }).isVisible().catch(() => false)) &&
        (await page.getByText("Estado activo", { exact: true }).isVisible().catch(() => false)) &&
        (await page.getByText("Idioma seleccionado", { exact: true }).isVisible().catch(() => false));
      mark("Detalles de la Cuenta", detallesCuentaOk);
      expect(detallesCuentaOk).toBeTruthy();

      // Step 7: Tus Negocios.
      const tusNegociosOk =
        (await page.getByText("Tus Negocios", { exact: true }).isVisible().catch(() => false)) &&
        (await page.getByRole("button", { name: "Agregar Negocio" }).isVisible().catch(() => false)) &&
        (await page.getByText("Tienes 2 de 3 negocios", { exact: true }).isVisible().catch(() => false));
      mark("Tus Negocios", tusNegociosOk);
      expect(tusNegociosOk).toBeTruthy();

      const openLegalAndValidate = async (linkText, headingText, screenshotName, resultKey) => {
        appPage = page;
        const [newPage] = await Promise.all([
          context.waitForEvent("page", { timeout: 8000 }).catch(() => null),
          page.getByText(linkText, { exact: true }).click(),
        ]);

        await page.waitForLoadState("networkidle");

        let targetPage = page;
        if (newPage) {
          targetPage = newPage;
          await targetPage.waitForLoadState("domcontentloaded");
          await targetPage.waitForLoadState("networkidle");
        }

        await expect(targetPage.getByRole("heading", { name: headingText })).toBeVisible({ timeout: 30000 });
        const legalParagraph = targetPage.locator("p, article, section").filter({ hasText: /t[eé]rminos|privacidad|datos|uso/i }).first();
        await expect(legalParagraph).toBeVisible();

        await targetPage.screenshot({
          path: testInfo.outputPath(`${screenshotName}.png`),
          fullPage: true,
        });

        legalUrls[resultKey] = targetPage.url();
        testInfo.annotations.push({ type: "evidence", description: `${resultKey} URL: ${legalUrls[resultKey]}` });
        mark(resultKey, true);

        if (newPage) {
          await appPage.bringToFront();
          if (appUrl) {
            await appPage.goto(appUrl, { waitUntil: "networkidle" });
          }
        }
      };

      // Step 8: Validate Términos y Condiciones.
      await openLegalAndValidate(
        "Términos y Condiciones",
        "Términos y Condiciones",
        "05-terminos-y-condiciones",
        "Términos y Condiciones"
      );

      // Step 9: Validate Política de Privacidad.
      await openLegalAndValidate(
        "Política de Privacidad",
        "Política de Privacidad",
        "06-politica-de-privacidad",
        "Política de Privacidad"
      );
    } finally {
      // Step 10: Final report output in test logs.
      const orderedReport = [
        ["Login", results.Login],
        ["Mi Negocio menu", results["Mi Negocio menu"]],
        ["Agregar Negocio modal", results["Agregar Negocio modal"]],
        ["Administrar Negocios view", results["Administrar Negocios view"]],
        ["Información General", results["Información General"]],
        ["Detalles de la Cuenta", results["Detalles de la Cuenta"]],
        ["Tus Negocios", results["Tus Negocios"]],
        ["Términos y Condiciones", results["Términos y Condiciones"]],
        ["Política de Privacidad", results["Política de Privacidad"]],
      ];

      // eslint-disable-next-line no-console
      console.log("=== FINAL WORKFLOW REPORT ===");
      for (const [label, passed] of orderedReport) {
        // eslint-disable-next-line no-console
        console.log(`${label}: ${passed ? "PASS" : "FAIL"}`);
      }
      // eslint-disable-next-line no-console
      console.log(`Términos y Condiciones URL: ${legalUrls["Términos y Condiciones"] || "N/A"}`);
      // eslint-disable-next-line no-console
      console.log(`Política de Privacidad URL: ${legalUrls["Política de Privacidad"] || "N/A"}`);
    }
  });
});
