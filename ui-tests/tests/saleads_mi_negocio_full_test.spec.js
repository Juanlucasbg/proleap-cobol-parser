const { test, expect } = require("@playwright/test");
const {
  takeCheckpoint,
  saveFinalReport,
  waitUi,
  clickByText,
  assertVisibleText,
  createStepTracker
} = require("../utils/saleadsHelpers");

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio full workflow", async ({ page, context, baseURL }) => {
    const tracker = createStepTracker();
    const evidence = {
      screenshots: {},
      legalUrls: {}
    };

    const summary = {
      name: "saleads_mi_negocio_full_test",
      startedAt: new Date().toISOString(),
      report: tracker.toObject(),
      evidence
    };

    const setFailed = () => {
      summary.report = tracker.toObject();
    };

    try {
      // Step 1 - Login with Google
      if (baseURL && page.url() === "about:blank") {
        await page.goto(baseURL, { waitUntil: "domcontentloaded" });
      }
      if (!baseURL && page.url() === "about:blank") {
        throw new Error(
          "Unable to start login flow from about:blank. Provide SALEADS_BASE_URL for this environment."
        );
      }

      await waitUi(page);
      const [googlePopup] = await Promise.all([
        context.waitForEvent("page", { timeout: 8000 }).catch(() => null),
        clickByText(page, "Sign in with Google")
      ]);

      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded");
        const possibleEmailPopup = googlePopup
          .getByText("juanlucasbarbiergarzon@gmail.com", { exact: true })
          .first();
        if (await possibleEmailPopup.isVisible({ timeout: 8000 }).catch(() => false)) {
          await possibleEmailPopup.click();
        }
      } else {
        const possibleEmail = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
        if (await possibleEmail.isVisible({ timeout: 5000 }).catch(() => false)) {
          await possibleEmail.click();
        }
      }

      await waitUi(page);
      await expect(page.locator("aside").first()).toBeVisible();
      evidence.screenshots.dashboardLoaded = await takeCheckpoint(page, "01-dashboard-loaded");
      tracker.pass("Login");

      // Step 2 - Open Mi Negocio menu
      await clickByText(page.locator("aside"), "Negocio");
      await waitUi(page);
      await clickByText(page.locator("aside"), "Mi Negocio");
      await waitUi(page);
      await assertVisibleText(page, "Agregar Negocio");
      await assertVisibleText(page, "Administrar Negocios");
      evidence.screenshots.miNegocioExpanded = await takeCheckpoint(page, "02-mi-negocio-expanded");
      tracker.pass("Mi Negocio menu");

      // Step 3 - Validate Agregar Negocio modal
      await clickByText(page, "Agregar Negocio");
      await waitUi(page);
      await assertVisibleText(page, "Crear Nuevo Negocio");
      await expect(page.getByLabel("Nombre del Negocio").first()).toBeVisible();
      await assertVisibleText(page, "Tienes 2 de 3 negocios");
      await assertVisibleText(page, "Cancelar");
      await assertVisibleText(page, "Crear Negocio");
      evidence.screenshots.agregarNegocioModal = await takeCheckpoint(page, "03-agregar-negocio-modal");

      const nameField = page.getByLabel("Nombre del Negocio").first();
      await nameField.click();
      await nameField.fill("Negocio Prueba Automatización");
      await clickByText(page, "Cancelar");
      await waitUi(page);
      tracker.pass("Agregar Negocio modal");

      // Step 4 - Open Administrar Negocios
      if (!(await page.getByText("Administrar Negocios").first().isVisible().catch(() => false))) {
        await clickByText(page.locator("aside"), "Mi Negocio");
        await waitUi(page);
      }

      await clickByText(page, "Administrar Negocios");
      await waitUi(page);
      await assertVisibleText(page, "Información General");
      await assertVisibleText(page, "Detalles de la Cuenta");
      await assertVisibleText(page, "Tus Negocios");
      await assertVisibleText(page, "Sección Legal");
      evidence.screenshots.administrarNegociosView = await takeCheckpoint(page, "04-administrar-negocios");
      tracker.pass("Administrar Negocios view");

      // Step 5 - Validate Información General
      await expect(page.locator("section,div").filter({ hasText: /@/ }).first()).toBeVisible();
      await expect(page.getByText("BUSINESS PLAN", { exact: false }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: "Cambiar Plan", exact: false }).first()).toBeVisible();
      tracker.pass("Información General");

      // Step 6 - Validate Detalles de la Cuenta
      await assertVisibleText(page, "Cuenta creada");
      await assertVisibleText(page, "Estado activo");
      await assertVisibleText(page, "Idioma seleccionado");
      tracker.pass("Detalles de la Cuenta");

      // Step 7 - Validate Tus Negocios
      await assertVisibleText(page, "Tus Negocios");
      await assertVisibleText(page, "Agregar Negocio");
      await assertVisibleText(page, "Tienes 2 de 3 negocios");
      tracker.pass("Tus Negocios");

      // Step 8 - Validate Términos y Condiciones
      const [termsPage] = await Promise.all([
        context.waitForEvent("page").catch(() => null),
        clickByText(page, "Términos y Condiciones")
      ]);
      const activeTermsPage = termsPage || page;
      await activeTermsPage.waitForLoadState("domcontentloaded");
      await assertVisibleText(activeTermsPage, "Términos y Condiciones");
      await expect(
        activeTermsPage.locator("main,article,section,div,p").filter({ hasText: /.+/ }).first()
      ).toBeVisible();
      evidence.screenshots.termsAndConditions = await takeCheckpoint(activeTermsPage, "08-terminos-y-condiciones");
      evidence.legalUrls.termsAndConditions = activeTermsPage.url();
      if (termsPage) {
        await termsPage.close();
      } else {
        await page.goBack().catch(() => Promise.resolve());
      }
      await waitUi(page);
      tracker.pass("Términos y Condiciones");

      // Step 9 - Validate Política de Privacidad
      const [privacyPage] = await Promise.all([
        context.waitForEvent("page").catch(() => null),
        clickByText(page, "Política de Privacidad")
      ]);
      const activePrivacyPage = privacyPage || page;
      await activePrivacyPage.waitForLoadState("domcontentloaded");
      await assertVisibleText(activePrivacyPage, "Política de Privacidad");
      await expect(
        activePrivacyPage.locator("main,article,section,div,p").filter({ hasText: /.+/ }).first()
      ).toBeVisible();
      evidence.screenshots.privacyPolicy = await takeCheckpoint(activePrivacyPage, "09-politica-de-privacidad");
      evidence.legalUrls.privacyPolicy = activePrivacyPage.url();
      if (privacyPage) {
        await privacyPage.close();
      } else {
        await page.goBack().catch(() => Promise.resolve());
      }
      await waitUi(page);
      tracker.pass("Política de Privacidad");
    } catch (error) {
      setFailed();
      summary.error = {
        message: error.message,
        stack: error.stack
      };
      throw error;
    } finally {
      summary.finishedAt = new Date().toISOString();
      summary.report = tracker.toObject();
      summary.reportPath = await saveFinalReport(summary);
      test.info().annotations.push({
        type: "final_report",
        description: JSON.stringify(summary.report)
      });
    }
  });
});
