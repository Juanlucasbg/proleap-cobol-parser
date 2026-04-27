const { test, expect } = require("@playwright/test");

/**
 * This suite intentionally avoids hardcoding the SaleADS domain.
 * It assumes the runner provides the login URL using SALEADS_LOGIN_URL.
 */
const REQUIRED_LOGIN_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const REQUIRED_BUSINESS_LIMIT_TEXT = "Tienes 2 de 3 negocios";
const TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL || "";

test.describe("SaleADS - Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    const status = {
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

    const legalUrls = {
      terminosYCondiciones: "",
      politicaDePrivacidad: "",
    };

    await test.step("Step 1 - Login with Google", async () => {
      if (SALEADS_LOGIN_URL) {
        await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
      }
      await waitForUi(page);

      const loginButton = page
        .getByRole("button", { name: /sign in with google|google|iniciar sesión con google|ingresar con google/i })
        .first();
      await expect(loginButton).toBeVisible();

      const popupPromise = context.waitForEvent("page").catch(() => null);
      await loginButton.click();

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await trySelectGoogleAccount(popup, REQUIRED_LOGIN_EMAIL);
        await popup.waitForTimeout(1000);
      } else {
        await trySelectGoogleAccount(page, REQUIRED_LOGIN_EMAIL);
      }

      await waitForUi(page);

      await expect(page.locator("aside, nav").first()).toBeVisible();
      await checkpoint(page, testInfo, "01-dashboard-loaded");
      status.Login = "PASS";
    });

    await test.step("Step 2 - Open Mi Negocio menu", async () => {
      await openMiNegocioMenu(page);
      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await checkpoint(page, testInfo, "02-mi-negocio-menu-expanded");
      status["Mi Negocio menu"] = "PASS";
    });

    await test.step("Step 3 - Validate Agregar Negocio modal", async () => {
      await clickByText(page, /Agregar Negocio/i);
      await waitForUi(page);

      const modal = page.getByRole("dialog").first();
      await expect(modal).toBeVisible();
      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible();
      await expect(modal.getByText(new RegExp(REQUIRED_BUSINESS_LIMIT_TEXT, "i"))).toBeVisible();
      await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      await modal.getByLabel(/Nombre del Negocio/i).fill(TEST_BUSINESS_NAME);
      await checkpoint(page, testInfo, "03-agregar-negocio-modal");
      await modal.getByRole("button", { name: /Cancelar/i }).click();
      await waitForUi(page);
      status["Agregar Negocio modal"] = "PASS";
    });

    await test.step("Step 4 - Open Administrar Negocios", async () => {
      await openMiNegocioMenu(page);
      await clickByText(page, /Administrar Negocios/i);
      await waitForUi(page);

      await expect(page.getByText(/Información General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Sección Legal|Legal/i).first()).toBeVisible();
      await checkpoint(page, testInfo, "04-administrar-negocios-page");
      status["Administrar Negocios view"] = "PASS";
    });

    await test.step("Step 5 - Validate Información General", async () => {
      await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

      // User name usually appears near profile/general info card.
      const generalInfoCard = page.locator("section,div").filter({ hasText: /Información General/i }).first();
      await expect(generalInfoCard).toContainText(/\S+/);
      status["Información General"] = "PASS";
    });

    await test.step("Step 6 - Validate Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo|Activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado|Idioma/i).first()).toBeVisible();
      status["Detalles de la Cuenta"] = "PASS";
    });

    await test.step("Step 7 - Validate Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
      await expect(page.getByText(new RegExp(REQUIRED_BUSINESS_LIMIT_TEXT, "i")).first()).toBeVisible();
      status["Tus Negocios"] = "PASS";
    });

    await test.step("Step 8 - Validate Términos y Condiciones", async () => {
      const termsResult = await clickLegalLinkAndValidate(
        page,
        context,
        /Términos y Condiciones/i,
        /Términos y Condiciones/i,
        testInfo,
        "08-terminos-y-condiciones"
      );
      legalUrls.terminosYCondiciones = termsResult.url;
      status["Términos y Condiciones"] = termsResult.ok ? "PASS" : "FAIL";
    });

    await test.step("Step 9 - Validate Política de Privacidad", async () => {
      const privacyResult = await clickLegalLinkAndValidate(
        page,
        context,
        /Política de Privacidad/i,
        /Política de Privacidad/i,
        testInfo,
        "09-politica-de-privacidad"
      );
      legalUrls.politicaDePrivacidad = privacyResult.url;
      status["Política de Privacidad"] = privacyResult.ok ? "PASS" : "FAIL";
    });

    await test.step("Step 10 - Final report", async () => {
      const report = {
        name: "saleads_mi_negocio_full_test",
        status,
        legalUrls,
      };

      await testInfo.attach("saleads-mi-negocio-report", {
        contentType: "application/json",
        body: Buffer.from(JSON.stringify(report, null, 2), "utf8"),
      });

      expect.soft(status.Login, "Login step should pass").toBe("PASS");
      expect.soft(status["Mi Negocio menu"], "Mi Negocio menu should pass").toBe("PASS");
      expect.soft(status["Agregar Negocio modal"], "Agregar Negocio modal should pass").toBe("PASS");
      expect.soft(status["Administrar Negocios view"], "Administrar Negocios view should pass").toBe("PASS");
      expect.soft(status["Información General"], "Información General should pass").toBe("PASS");
      expect.soft(status["Detalles de la Cuenta"], "Detalles de la Cuenta should pass").toBe("PASS");
      expect.soft(status["Tus Negocios"], "Tus Negocios should pass").toBe("PASS");
      expect.soft(status["Términos y Condiciones"], "Términos y Condiciones should pass").toBe("PASS");
      expect.soft(status["Política de Privacidad"], "Política de Privacidad should pass").toBe("PASS");
    });
  });
});

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(700);
}

async function clickByText(page, textRegex) {
  const locator = page.getByText(textRegex).first();
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function openMiNegocioMenu(page) {
  let negocio = page.getByRole("button", { name: /Negocio|Mi Negocio/i }).first();
  if (!(await negocio.isVisible().catch(() => false))) {
    negocio = page.getByText(/Negocio|Mi Negocio/i).first();
  }
  await expect(negocio).toBeVisible();
  await negocio.click();
  await waitForUi(page);
}

async function trySelectGoogleAccount(targetPage, email) {
  const accountItem = targetPage.getByText(new RegExp(email, "i")).first();
  if (await accountItem.isVisible().catch(() => false)) {
    await accountItem.click();
    await targetPage.waitForTimeout(700);
  }
}

async function clickLegalLinkAndValidate(page, context, linkTextRegex, headingRegex, testInfo, screenshotName) {
  const link = page.getByRole("link", { name: linkTextRegex }).first();
  await expect(link).toBeVisible();

  const [maybePopup] = await Promise.all([
    context.waitForEvent("page").catch(() => null),
    link.click(),
  ]);

  if (maybePopup) {
    await maybePopup.waitForLoadState("domcontentloaded");
    await maybePopup.waitForLoadState("networkidle").catch(() => {});

    const heading = maybePopup.getByRole("heading", { name: headingRegex }).first();
    const headingByText = maybePopup.getByText(headingRegex).first();
    const legalBody = maybePopup.locator("main, article, section, body").first();
    const hasHeading =
      (await heading.isVisible().catch(() => false)) ||
      (await headingByText.isVisible().catch(() => false));
    const hasBodyContent = ((await legalBody.textContent()) || "").trim().length > 100;

    await expect.soft(hasHeading).toBeTruthy();
    await expect.soft(hasBodyContent).toBeTruthy();
    await checkpoint(maybePopup, testInfo, screenshotName);
    const url = maybePopup.url();
    await maybePopup.close();
    await waitForUi(page);
    return { ok: !!hasHeading && hasBodyContent, url };
  }

  await waitForUi(page);
  const heading = page.getByRole("heading", { name: headingRegex }).first();
  const headingByText = page.getByText(headingRegex).first();
  const legalBody = page.locator("main, article, section, body").first();
  const hasHeading =
    (await heading.isVisible().catch(() => false)) ||
    (await headingByText.isVisible().catch(() => false));
  const hasBodyContent = ((await legalBody.textContent()) || "").trim().length > 100;

  await expect.soft(hasHeading).toBeTruthy();
  await expect.soft(hasBodyContent).toBeTruthy();
  await checkpoint(page, testInfo, screenshotName);
  return { ok: !!hasHeading && hasBodyContent, url: page.url() };
}

async function checkpoint(targetPage, testInfo, name) {
  const fileName = `${name}.png`;
  const path = testInfo.outputPath(fileName);
  await targetPage.screenshot({ path, fullPage: true });
  await testInfo.attach(name, {
    contentType: "image/png",
    path,
  });
}
