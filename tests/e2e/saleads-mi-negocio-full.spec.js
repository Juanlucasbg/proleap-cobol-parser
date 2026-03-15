const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_NAME = "saleads_mi_negocio_full_test";

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

test.describe(TEST_NAME, () => {
  test("Login with Google and validate Mi Negocio full workflow", async ({ page, context }, testInfo) => {
    test.setTimeout(8 * 60 * 1000);

    const timestamp = nowStamp();
    const screenshotsDir = path.join(process.cwd(), "artifacts", "screenshots", TEST_NAME, timestamp);
    const reportsDir = path.join(process.cwd(), "artifacts", "reports");
    fs.mkdirSync(screenshotsDir, { recursive: true });
    fs.mkdirSync(reportsDir, { recursive: true });

    const report = {
      Login: "FAIL - Not executed",
      "Mi Negocio menu": "FAIL - Not executed",
      "Agregar Negocio modal": "FAIL - Not executed",
      "Administrar Negocios view": "FAIL - Not executed",
      "Información General": "FAIL - Not executed",
      "Detalles de la Cuenta": "FAIL - Not executed",
      "Tus Negocios": "FAIL - Not executed",
      "Términos y Condiciones": "FAIL - Not executed",
      "Política de Privacidad": "FAIL - Not executed",
    };

    const evidence = {
      "Términos y Condiciones URL": "",
      "Política de Privacidad URL": "",
      screenshotsDir,
    };

    const failures = [];

    const markStep = (field, passed, details = "") => {
      report[field] = passed ? "PASS" : `FAIL${details ? ` - ${details}` : ""}`;
      if (!passed) {
        failures.push(`${field}: ${details || "validation failed"}`);
      }
    };

    const safeUiWait = async (targetPage = page, waitMs = 1000) => {
      await targetPage.waitForLoadState("domcontentloaded");
      await targetPage.waitForTimeout(waitMs);
    };

    const screenshot = async (name, targetPage = page, fullPage = true) => {
      const imagePath = path.join(screenshotsDir, `${name}.png`);
      await targetPage.screenshot({ path: imagePath, fullPage });
      await testInfo.attach(name, { path: imagePath, contentType: "image/png" });
      return imagePath;
    };

    const visible = async (locator, timeout = 4000) => {
      try {
        await locator.first().waitFor({ state: "visible", timeout });
        return true;
      } catch {
        return false;
      }
    };

    const firstVisibleLocator = async (locators, timeout = 4000) => {
      for (const locator of locators) {
        if (await visible(locator, timeout)) {
          return locator.first();
        }
      }
      return null;
    };

    const step = async (field, fn) => {
      try {
        await fn();
        markStep(field, true);
      } catch (error) {
        markStep(field, false, error.message);
      }
    };

    const getSectionContainer = async (headingPattern) => {
      const heading = await firstVisibleLocator(
        [
          page.getByRole("heading", { name: headingPattern }),
          page.getByText(headingPattern),
        ],
        12000,
      );
      if (!heading) {
        throw new Error(`Section heading ${headingPattern} not visible.`);
      }
      return heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
    };

    const chooseGoogleAccountIfNeeded = async (googlePage) => {
      await safeUiWait(googlePage, 750);
      const accountOption = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
      if (await visible(accountOption, 9000)) {
        await accountOption.click();
        await safeUiWait(googlePage, 1200);
        return;
      }

      const emailInput = googlePage.locator('input[type="email"]').first();
      if (await visible(emailInput, 9000)) {
        await emailInput.fill(GOOGLE_ACCOUNT_EMAIL);
        const nextButton = await firstVisibleLocator([
          googlePage.getByRole("button", { name: /Next|Siguiente/i }),
          googlePage.getByText(/Next|Siguiente/i),
        ]);
        if (!nextButton) {
          throw new Error("Google 'Next' button was not found after entering email.");
        }
        await nextButton.click();
        await safeUiWait(googlePage, 1200);
        return;
      }

      throw new Error(
        `Google account selector did not show '${GOOGLE_ACCOUNT_EMAIL}' and email input was unavailable.`,
      );
    };

    const validateLegalDocument = async (linkTextPattern, headingPattern, reportField, urlField, screenshotName) => {
      const appPage = page;
      const legalLink = await firstVisibleLocator(
        [appPage.getByRole("link", { name: linkTextPattern }), appPage.getByText(linkTextPattern)],
        8000,
      );
      if (!legalLink) {
        throw new Error(`Legal link '${linkTextPattern}' not found.`);
      }

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await legalLink.click();
      await safeUiWait(appPage, 1000);
      const popup = await popupPromise;

      const targetPage = popup || appPage;
      await safeUiWait(targetPage, 1500);

      const heading = await firstVisibleLocator(
        [targetPage.getByRole("heading", { name: headingPattern }), targetPage.getByText(headingPattern)],
        15000,
      );
      if (!heading) {
        throw new Error(`Heading '${headingPattern}' not visible in legal content.`);
      }

      const legalContent = (await targetPage.locator("body").innerText()).trim();
      if (legalContent.length < 120) {
        throw new Error("Legal content text appears too short.");
      }

      evidence[urlField] = targetPage.url();
      await screenshot(screenshotName, targetPage, true);

      if (popup) {
        await popup.close();
        await appPage.bringToFront();
      } else {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      }

      await safeUiWait(appPage, 800);
      markStep(reportField, true);
    };

    // Step 1 - Login with Google
    await step("Login", async () => {
      const optionalLoginUrl = process.env.SALEADS_LOGIN_URL;
      if ((page.url() === "about:blank" || page.url().startsWith("data:")) && optionalLoginUrl) {
        await page.goto(optionalLoginUrl, { waitUntil: "domcontentloaded" });
      }

      await safeUiWait(page, 1200);

      const sidebarAlreadyVisible = await visible(
        page.locator("aside, [role='navigation'], nav").filter({ hasText: /Negocio|Mi Negocio|Dashboard/i }),
        5000,
      );

      if (!sidebarAlreadyVisible) {
        const loginButton = await firstVisibleLocator(
          [
            page.getByRole("button", { name: /Sign in with Google|Iniciar sesión con Google|Google/i }),
            page.getByRole("link", { name: /Sign in with Google|Iniciar sesión con Google|Google/i }),
            page.getByText(/Sign in with Google|Iniciar sesión con Google|Continuar con Google/i),
          ],
          15000,
        );

        if (!loginButton) {
          throw new Error("Google login trigger was not found on the current page.");
        }

        const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
        await loginButton.click();
        await safeUiWait(page, 1500);
        const popup = await popupPromise;

        if (popup) {
          await chooseGoogleAccountIfNeeded(popup);
        } else {
          await chooseGoogleAccountIfNeeded(page);
        }
      }

      await expect(
        page.locator("aside, [role='navigation'], nav").filter({ hasText: /Negocio|Mi Negocio|Dashboard/i }).first(),
      ).toBeVisible({ timeout: 60000 });

      await screenshot("01-dashboard-loaded", page, true);
    });

    // Step 2 - Open Mi Negocio menu
    await step("Mi Negocio menu", async () => {
      const negocioSection = await firstVisibleLocator(
        [page.getByText(/^Negocio$/i), page.getByRole("button", { name: /^Negocio$/i })],
        10000,
      );
      if (negocioSection) {
        await negocioSection.click();
        await safeUiWait(page, 800);
      }

      const miNegocioMenu = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        10000,
      );
      if (!miNegocioMenu) {
        throw new Error("'Mi Negocio' option was not found.");
      }
      await miNegocioMenu.click();
      await safeUiWait(page, 900);

      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: 10000 });
      await screenshot("02-mi-negocio-expanded", page, true);
    });

    // Step 3 - Validate Agregar Negocio modal
    await step("Agregar Negocio modal", async () => {
      const agregarNegocioOption = await firstVisibleLocator(
        [page.getByRole("link", { name: /^Agregar Negocio$/i }), page.getByText(/^Agregar Negocio$/i)],
        8000,
      );
      if (!agregarNegocioOption) {
        throw new Error("'Agregar Negocio' menu option is not visible.");
      }

      await agregarNegocioOption.click();
      await safeUiWait(page, 900);

      const modalTitle = page.getByText(/^Crear Nuevo Negocio$/i).first();
      await expect(modalTitle).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible({ timeout: 10000 });

      const businessNameInput = page
        .getByRole("textbox", { name: /Nombre del Negocio/i })
        .or(page.getByPlaceholder(/Nombre del Negocio/i))
        .first();

      if (await visible(businessNameInput, 4000)) {
        await businessNameInput.click();
        await businessNameInput.fill("Negocio Prueba Automatizacion");
      }

      await screenshot("03-agregar-negocio-modal", page, true);
      await page.getByRole("button", { name: /^Cancelar$/i }).first().click();
      await safeUiWait(page, 900);
    });

    // Step 4 - Open Administrar Negocios
    await step("Administrar Negocios view", async () => {
      const miNegocioMenu = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        6000,
      );
      if (miNegocioMenu && !(await visible(page.getByText(/^Administrar Negocios$/i).first(), 1500))) {
        await miNegocioMenu.click();
        await safeUiWait(page, 700);
      }

      const administrarNegocios = await firstVisibleLocator(
        [page.getByRole("link", { name: /^Administrar Negocios$/i }), page.getByText(/^Administrar Negocios$/i)],
        10000,
      );
      if (!administrarNegocios) {
        throw new Error("'Administrar Negocios' option not visible.");
      }
      await administrarNegocios.click();
      await safeUiWait(page, 1500);

      await expect(page.getByText(/^Información General$/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 15000 });
      await screenshot("04-administrar-negocios-page", page, true);
    });

    // Step 5 - Validate Información General
    await step("Información General", async () => {
      const infoSection = await getSectionContainer(/^Información General$/i);
      const sectionText = (await infoSection.innerText()).replace(/\s+/g, " ").trim();

      const emailRegex = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,}/;
      if (!emailRegex.test(sectionText)) {
        throw new Error("User email is not visible in 'Información General'.");
      }

      const hasLikelyName =
        /Nombre|Usuario|Perfil/i.test(sectionText) ||
        /[A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,}/.test(sectionText.replace(GOOGLE_ACCOUNT_EMAIL, ""));
      if (!hasLikelyName) {
        throw new Error("User name-like text is not visible in 'Información General'.");
      }

      await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 10000 });
      await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({
        timeout: 10000,
      });
    });

    // Step 6 - Validate Detalles de la Cuenta
    await step("Detalles de la Cuenta", async () => {
      const detailsSection = await getSectionContainer(/^Detalles de la Cuenta$/i);
      await expect(detailsSection.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 10000 });
      await expect(detailsSection.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 10000 });
      await expect(detailsSection.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 10000 });
    });

    // Step 7 - Validate Tus Negocios
    await step("Tus Negocios", async () => {
      const businessSection = await getSectionContainer(/^Tus Negocios$/i);
      await expect(businessSection.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({
        timeout: 10000,
      });
      await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 10000 });

      const listCandidates = businessSection.locator("li, [role='listitem'], [role='row'], tr");
      if ((await listCandidates.count()) === 0) {
        const text = (await businessSection.innerText()).trim();
        if (!/negocio/i.test(text)) {
          throw new Error("Business list is not visible in 'Tus Negocios'.");
        }
      }
    });

    // Step 8 - Validate Términos y Condiciones
    try {
      await validateLegalDocument(
        /^Términos y Condiciones$/i,
        /^Términos y Condiciones$/i,
        "Términos y Condiciones",
        "Términos y Condiciones URL",
        "08-terminos-y-condiciones",
      );
    } catch (error) {
      markStep("Términos y Condiciones", false, error.message);
    }

    // Step 9 - Validate Política de Privacidad
    try {
      await validateLegalDocument(
        /^Política de Privacidad$/i,
        /^Política de Privacidad$/i,
        "Política de Privacidad",
        "Política de Privacidad URL",
        "09-politica-de-privacidad",
      );
    } catch (error) {
      markStep("Política de Privacidad", false, error.message);
    }

    // Step 10 - Final report
    const finalReport = { ...report, ...evidence };
    const reportPath = path.join(reportsDir, `${TEST_NAME}-${timestamp}.json`);
    fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

    // Important for automation logs.
    console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
    console.log(JSON.stringify(finalReport, null, 2));

    if (failures.length > 0) {
      throw new Error(`Workflow validations failed:\n- ${failures.join("\n- ")}`);
    }
  });
});
