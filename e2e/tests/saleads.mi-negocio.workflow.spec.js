const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

test.describe("SaleADS Mi Negocio module workflow", () => {
  test("login with Google and validate full Mi Negocio flow", async ({
    page,
    context,
    baseURL,
  }) => {
    const finalReport = {
      Login: "FAIL",
      "Mi Negocio menu": "FAIL",
      "Agregar Negocio modal": "FAIL",
      "Administrar Negocios view": "FAIL",
      "Información General": "FAIL",
      "Detalles de la Cuenta": "FAIL",
      "Tus Negocios": "FAIL",
      "Términos y Condiciones": "FAIL",
      "Política de Privacidad": "FAIL",
    };

    const markCurrentAndRethrow = (label, error) => {
      finalReport[label] = "FAIL";
      throw error;
    };

    const safeClickByText = async (regex, options = {}) => {
      const locators = [
        page.getByRole("button", { name: regex }),
        page.getByRole("link", { name: regex }),
        page.getByText(regex).first(),
      ];

      for (const locator of locators) {
        try {
          if (await locator.isVisible({ timeout: options.timeout ?? 3000 })) {
            await locator.click();
            await page.waitForLoadState("networkidle");
            return;
          }
        } catch (_error) {
          // Keep trying the next candidate.
        }
      }

      throw new Error(`Unable to click element by text ${String(regex)}`);
    };

    const clickAndCapturePopupOrNav = async (labelRegex) => {
      const previousUrl = page.url();
      const contextPagesBefore = context.pages().length;

      let popup;
      try {
        const maybePopup = page.waitForEvent("popup", { timeout: 5000 });
        await safeClickByText(labelRegex);
        popup = await maybePopup;
      } catch (_error) {
        popup = null;
      }

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await popup.waitForLoadState("networkidle");
        return {
          targetPage: popup,
          cameFromPopup: true,
          finalUrl: popup.url(),
        };
      }

      // If a popup was not captured, still ensure the page settled after click.
      await page.waitForLoadState("domcontentloaded");
      await page.waitForLoadState("networkidle");

      const contextPagesAfter = context.pages().length;
      if (contextPagesAfter > contextPagesBefore) {
        const newest = context.pages()[contextPagesAfter - 1];
        await newest.waitForLoadState("domcontentloaded");
        await newest.waitForLoadState("networkidle");
        return {
          targetPage: newest,
          cameFromPopup: true,
          finalUrl: newest.url(),
        };
      }

      return {
        targetPage: page,
        cameFromPopup: false,
        finalUrl: page.url() === "about:blank" ? previousUrl : page.url(),
      };
    };

    const assertVisibleByText = async (regex, timeout = 20000) => {
      await expect(page.getByText(regex).first()).toBeVisible({ timeout });
    };

    try {
      if (baseURL) {
        await page.goto(baseURL, { waitUntil: "domcontentloaded" });
      }
      await page.waitForLoadState("networkidle");

      // Step 1: Login with Google.
      try {
        await safeClickByText(/sign in with google|continuar con google|google/i);

        const googleAccount = page
          .getByText(new RegExp(ACCOUNT_EMAIL, "i"))
          .first();
        if (await googleAccount.isVisible({ timeout: 7000 }).catch(() => false)) {
          await googleAccount.click();
        }

        await page.waitForLoadState("domcontentloaded");
        await page.waitForLoadState("networkidle");

        const sidebar = page
          .locator("aside, nav")
          .filter({ hasText: /negocio|mi negocio|dashboard|inicio/i })
          .first();

        await expect(
          sidebar.or(page.getByText(/negocio|mi negocio/i).first()),
        ).toBeVisible({
          timeout: 30000,
        });

        finalReport.Login = "PASS";
        await page.screenshot({
          path: "test-results/screenshots/01-dashboard-loaded.png",
          fullPage: true,
        });
      } catch (error) {
        test.info().annotations.push({
          type: "step-1-failure",
          description: String(error),
        });
        markCurrentAndRethrow("Login", error);
      }

      // Step 2: Open Mi Negocio menu.
      try {
        await safeClickByText(/mi negocio/i);
        await assertVisibleByText(/agregar negocio/i);
        await assertVisibleByText(/administrar negocios/i);
        finalReport["Mi Negocio menu"] = "PASS";
        await page.screenshot({
          path: "test-results/screenshots/02-mi-negocio-menu-expanded.png",
          fullPage: true,
        });
      } catch (error) {
        markCurrentAndRethrow("Mi Negocio menu", error);
      }

      // Step 3: Validate Agregar Negocio modal.
      try {
        await safeClickByText(/agregar negocio/i);
        await expect(page.getByRole("dialog")).toBeVisible({ timeout: 15000 });
        await assertVisibleByText(/crear nuevo negocio/i);
        await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible({
          timeout: 15000,
        });
        await assertVisibleByText(/tienes 2 de 3 negocios/i);
        await expect(
          page.getByRole("button", { name: /cancelar/i }),
        ).toBeVisible({ timeout: 15000 });
        await expect(
          page.getByRole("button", { name: /crear negocio/i }),
        ).toBeVisible({ timeout: 15000 });
        await page.screenshot({
          path: "test-results/screenshots/03-crear-negocio-modal.png",
          fullPage: true,
        });

        const businessNameField = page.getByLabel(/nombre del negocio/i);
        await businessNameField.click();
        await businessNameField.fill("Negocio Prueba Automatización");
        await page.getByRole("button", { name: /cancelar/i }).click();
        await page.waitForLoadState("networkidle");
        await expect(page.getByRole("dialog")).not.toBeVisible({
          timeout: 15000,
        });
        finalReport["Agregar Negocio modal"] = "PASS";
      } catch (error) {
        markCurrentAndRethrow("Agregar Negocio modal", error);
      }

      // Step 4: Open Administrar Negocios view.
      try {
        if (
          !(await page
            .getByText(/administrar negocios/i)
            .first()
            .isVisible({ timeout: 3000 }))
        ) {
          await safeClickByText(/mi negocio/i);
        }
        await safeClickByText(/administrar negocios/i);
        await assertVisibleByText(/información general/i, 30000);
        await assertVisibleByText(/detalles de la cuenta/i, 30000);
        await assertVisibleByText(/tus negocios/i, 30000);
        await assertVisibleByText(/sección legal/i, 30000);
        finalReport["Administrar Negocios view"] = "PASS";
        await page.screenshot({
          path: "test-results/screenshots/04-administrar-negocios-vista-completa.png",
          fullPage: true,
        });
      } catch (error) {
        markCurrentAndRethrow("Administrar Negocios view", error);
      }

      // Step 5: Validate Información General.
      try {
        await assertVisibleByText(/business plan/i);
        await expect(
          page.getByRole("button", { name: /cambiar plan/i }),
        ).toBeVisible({ timeout: 20000 });

        // Name/email validations are text-based without fixed values to support all environments.
        const accountEmailLike = page.getByText(/@/).first();
        await expect(accountEmailLike).toBeVisible({ timeout: 20000 });
        const generalSection = page.getByText(/información general/i).first();
        await expect(generalSection).toBeVisible({ timeout: 20000 });
        finalReport["Información General"] = "PASS";
      } catch (error) {
        markCurrentAndRethrow("Información General", error);
      }

      // Step 6: Validate Detalles de la Cuenta.
      try {
        await assertVisibleByText(/cuenta creada/i);
        await assertVisibleByText(/estado activo|activo/i);
        await assertVisibleByText(/idioma seleccionado|idioma/i);
        finalReport["Detalles de la Cuenta"] = "PASS";
      } catch (error) {
        markCurrentAndRethrow("Detalles de la Cuenta", error);
      }

      // Step 7: Validate Tus Negocios.
      try {
        await assertVisibleByText(/tus negocios/i);
        await expect(
          page.getByRole("button", { name: /agregar negocio/i }),
        ).toBeVisible({ timeout: 20000 });
        await assertVisibleByText(/tienes 2 de 3 negocios/i);
        finalReport["Tus Negocios"] = "PASS";
      } catch (error) {
        markCurrentAndRethrow("Tus Negocios", error);
      }

      // Step 8: Validate Términos y Condiciones.
      try {
        const termsResult = await clickAndCapturePopupOrNav(
          /términos y condiciones/i,
        );
        await expect(
          termsResult.targetPage
            .getByRole("heading", { name: /términos y condiciones/i })
            .or(termsResult.targetPage.getByText(/términos y condiciones/i).first()),
        ).toBeVisible({ timeout: 25000 });
        await expect(
          termsResult.targetPage
            .getByText(/términos|condiciones|legal|uso|servicio/i)
            .first(),
        ).toBeVisible({ timeout: 25000 });
        await termsResult.targetPage.screenshot({
          path: "test-results/screenshots/05-terminos-y-condiciones.png",
          fullPage: true,
        });
        test.info().annotations.push({
          type: "evidence-terms-final-url",
          description: termsResult.finalUrl,
        });
        finalReport["Términos y Condiciones"] = "PASS";

        if (termsResult.cameFromPopup) {
          await termsResult.targetPage.close();
          await page.bringToFront();
        } else {
          await page.goBack({ waitUntil: "networkidle" }).catch(async () => {
            await page.waitForLoadState("networkidle");
          });
        }
      } catch (error) {
        markCurrentAndRethrow("Términos y Condiciones", error);
      }

      // Step 9: Validate Política de Privacidad.
      try {
        const privacyResult = await clickAndCapturePopupOrNav(
          /política de privacidad/i,
        );
        await expect(
          privacyResult.targetPage
            .getByRole("heading", { name: /política de privacidad/i })
            .or(privacyResult.targetPage.getByText(/política de privacidad/i).first()),
        ).toBeVisible({ timeout: 25000 });
        await expect(
          privacyResult.targetPage
            .getByText(/privacidad|datos|legal|información|personal/i)
            .first(),
        ).toBeVisible({ timeout: 25000 });
        await privacyResult.targetPage.screenshot({
          path: "test-results/screenshots/06-politica-de-privacidad.png",
          fullPage: true,
        });
        test.info().annotations.push({
          type: "evidence-privacy-final-url",
          description: privacyResult.finalUrl,
        });
        finalReport["Política de Privacidad"] = "PASS";

        if (privacyResult.cameFromPopup) {
          await privacyResult.targetPage.close();
          await page.bringToFront();
        } else {
          await page.goBack({ waitUntil: "networkidle" }).catch(async () => {
            await page.waitForLoadState("networkidle");
          });
        }
      } catch (error) {
        markCurrentAndRethrow("Política de Privacidad", error);
      }
    } finally {
      // Step 10: Final report is always logged, even if previous steps fail.
      test.info().annotations.push({
        type: "saleads-final-report",
        description: JSON.stringify(finalReport),
      });
      // Helpful in CI logs and local CLI output.
      // eslint-disable-next-line no-console
      console.log("SaleADS Mi Negocio validation report:", finalReport);
    }
  });
});
