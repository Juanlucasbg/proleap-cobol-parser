const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

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

function buildInitialReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "NOT_RUN";
    return acc;
  }, {});
}

async function firstVisibleLocator(candidates, description) {
  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function clickAndWaitForUi(page, locator, description) {
  await expect(locator, `Expected visible element: ${description}`).toBeVisible();
  await locator.click();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function maybeChooseGoogleAccount(activePage, email) {
  const accountCandidate = activePage.getByText(email, { exact: true });
  if (await accountCandidate.first().isVisible().catch(() => false)) {
    await clickAndWaitForUi(activePage, accountCandidate.first(), `Google account ${email}`);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildInitialReport();
  const details = {};
  let fatalError = null;

  const markPass = (field) => {
    report[field] = "PASS";
  };

  const markFail = (field, error) => {
    report[field] = "FAIL";
    details[field] = error instanceof Error ? error.message : String(error);
  };

  try {
    const baseUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL || testInfo.project.use.baseURL;
    if (!baseUrl) {
      throw new Error("Missing SALEADS_BASE_URL (or BASE_URL). This test is environment-agnostic and requires a runtime URL.");
    }

    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });

    // Step 1: Login with Google
    try {
      const loginButton = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /google/i }),
          page.getByRole("link", { name: /google/i }),
          page.getByText(/sign in with google|iniciar sesión con google|ingresar con google|google/i),
        ],
        "Sign in with Google button"
      );

      const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickAndWaitForUi(page, loginButton, "Google login button");

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await maybeChooseGoogleAccount(popup, "juanlucasbarbiergarzon@gmail.com");
        await popup.waitForTimeout(1000);
        await page.bringToFront();
      } else {
        await maybeChooseGoogleAccount(page, "juanlucasbarbiergarzon@gmail.com");
      }

      await expect(page.locator("aside, nav").first()).toBeVisible();
      await page.screenshot({ path: testInfo.outputPath("01-dashboard-loaded.png"), fullPage: true });
      markPass("Login");
    } catch (error) {
      markFail("Login", error);
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      const negocioSection = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /negocio/i }),
          page.getByRole("link", { name: /negocio/i }),
          page.getByText(/^Negocio$/i),
          page.getByText(/negocio/i),
        ],
        "Negocio section"
      );
      await clickAndWaitForUi(page, negocioSection, "Negocio section");

      const miNegocioOption = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        "Mi Negocio option"
      );
      await clickAndWaitForUi(page, miNegocioOption, "Mi Negocio option");

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await page.screenshot({ path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"), fullPage: true });
      markPass("Mi Negocio menu");
    } catch (error) {
      markFail("Mi Negocio menu", error);
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const addBusinessItem = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByRole("link", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i),
        ],
        "Agregar Negocio"
      );
      await clickAndWaitForUi(page, addBusinessItem, "Agregar Negocio action");

      const modal = await firstVisibleLocator(
        [
          page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
          page.locator("div[role='dialog'], .modal").filter({ hasText: /Crear Nuevo Negocio/i }),
        ],
        "Crear Nuevo Negocio modal"
      );

      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible();
      await expect(modal.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      const businessNameInput = modal.getByLabel(/Nombre del Negocio/i);
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndWaitForUi(page, modal.getByRole("button", { name: /Cancelar/i }), "Cancelar modal");

      await page.screenshot({ path: testInfo.outputPath("03-crear-negocio-modal.png"), fullPage: true });
      markPass("Agregar Negocio modal");
    } catch (error) {
      markFail("Agregar Negocio modal", error);
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        const miNegocioOption = await firstVisibleLocator(
          [
            page.getByRole("button", { name: /mi negocio/i }),
            page.getByRole("link", { name: /mi negocio/i }),
            page.getByText(/mi negocio/i),
          ],
          "Mi Negocio option for re-open"
        );
        await clickAndWaitForUi(page, miNegocioOption, "Mi Negocio option re-open");
      }

      const manageBusinesses = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /Administrar Negocios/i }),
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i),
        ],
        "Administrar Negocios option"
      );
      await clickAndWaitForUi(page, manageBusinesses, "Administrar Negocios option");

      await expect(page.getByText(/Información General/i)).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Sección Legal/i)).toBeVisible();
      await page.screenshot({ path: testInfo.outputPath("04-administrar-negocios-view.png"), fullPage: true });
      markPass("Administrar Negocios view");
    } catch (error) {
      markFail("Administrar Negocios view", error);
      throw error;
    }

    // Step 5: Validate Información General
    try {
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      const emailVisible = await page.getByText(/@/).first().isVisible().catch(() => false);
      if (!emailVisible) {
        throw new Error("User email is not visible in Información General.");
      }

      const userNameCandidate = page.locator("main, section").getByText(/[A-Za-zÁÉÍÓÚáéíóú]{2,}\s+[A-Za-zÁÉÍÓÚáéíóú]{2,}/).first();
      if (!(await userNameCandidate.isVisible().catch(() => false))) {
        throw new Error("User name is not visible in Información General.");
      }

      markPass("Información General");
    } catch (error) {
      markFail("Información General", error);
      throw error;
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
      markPass("Detalles de la Cuenta");
    } catch (error) {
      markFail("Detalles de la Cuenta", error);
      throw error;
    }

    // Step 7: Validate Tus Negocios
    try {
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      markPass("Tus Negocios");
    } catch (error) {
      markFail("Tus Negocios", error);
      throw error;
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const termsLink = await firstVisibleLocator(
        [
          page.getByRole("link", { name: /T[eé]rminos y Condiciones/i }),
          page.getByText(/T[eé]rminos y Condiciones/i),
        ],
        "Términos y Condiciones link"
      );

      const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickAndWaitForUi(page, termsLink, "Términos y Condiciones link");

      const termsPage = (await popupPromise) || page;
      await termsPage.waitForLoadState("domcontentloaded");

      await expect(termsPage.getByText(/T[eé]rminos y Condiciones/i).first()).toBeVisible();
      await expect(termsPage.locator("main, article, body").first()).toContainText(/(condiciones|t[eé]rminos|uso|legal)/i);
      await termsPage.screenshot({ path: testInfo.outputPath("08-terminos-y-condiciones.png"), fullPage: true });
      details["Términos y Condiciones URL"] = termsPage.url();

      if (termsPage !== page) {
        await termsPage.close();
        await page.bringToFront();
      }

      markPass("Términos y Condiciones");
    } catch (error) {
      markFail("Términos y Condiciones", error);
      throw error;
    }

    // Step 9: Validate Política de Privacidad
    try {
      const privacyLink = await firstVisibleLocator(
        [
          page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }),
          page.getByText(/Pol[ií]tica de Privacidad/i),
        ],
        "Política de Privacidad link"
      );

      const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickAndWaitForUi(page, privacyLink, "Política de Privacidad link");

      const privacyPage = (await popupPromise) || page;
      await privacyPage.waitForLoadState("domcontentloaded");

      await expect(privacyPage.getByText(/Pol[ií]tica de Privacidad/i).first()).toBeVisible();
      await expect(privacyPage.locator("main, article, body").first()).toContainText(/(privacidad|datos|informaci[oó]n|legal)/i);
      await privacyPage.screenshot({ path: testInfo.outputPath("09-politica-de-privacidad.png"), fullPage: true });
      details["Política de Privacidad URL"] = privacyPage.url();

      if (privacyPage !== page) {
        await privacyPage.close();
        await page.bringToFront();
      }

      markPass("Política de Privacidad");
    } catch (error) {
      markFail("Política de Privacidad", error);
      throw error;
    }
  } catch (error) {
    fatalError = error;
    if (report.Login === "NOT_RUN") {
      markFail("Login", error);
    }
  } finally {
    for (const field of REPORT_FIELDS) {
      if (report[field] === "NOT_RUN") {
        report[field] = "FAIL";
        details[field] = details[field] || "Not executed because a previous required step failed.";
      }
    }

    const finalReport = {
      scenario: "saleads_mi_negocio_full_test",
      triggeredAtUtc: new Date().toISOString(),
      results: report,
      details,
    };

    const reportPath = testInfo.outputPath("final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    // Visible summary in test logs.
    // eslint-disable-next-line no-console
    console.table(report);
    // eslint-disable-next-line no-console
    console.log("Legal URLs:", {
      terms: details["Términos y Condiciones URL"] || "N/A",
      privacy: details["Política de Privacidad URL"] || "N/A",
    });
  }

  if (fatalError) {
    throw fatalError;
  }
});
