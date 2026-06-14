const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_QUOTA_TEXT = /Tienes\s+2\s+de\s+3\s+negocios/i;
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

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUiToSettle(page);
}

async function pickFirstVisible(locators, timeoutMs = 15000) {
  for (const locator of locators) {
    try {
      await locator.first().waitFor({ state: "visible", timeout: timeoutMs });
      return locator.first();
    } catch (_error) {
      // Try next locator candidate.
    }
  }

  throw new Error("No visible locator matched the expected text.");
}

async function saveCheckpoint(page, testInfo, fileName, fullPage = false) {
  const shotPath = testInfo.outputPath(path.join("screenshots", fileName));
  await fs.mkdir(path.dirname(shotPath), { recursive: true });
  await page.screenshot({ path: shotPath, fullPage });
  await testInfo.attach(fileName, {
    path: shotPath,
    contentType: "image/png",
  });
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const resultByField = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const details = [];
  const legalUrls = {
    terms: null,
    privacy: null,
  };

  let appPage = page;

  async function runValidationStep(fieldName, callback) {
    try {
      await callback();
      resultByField[fieldName] = "PASS";
    } catch (error) {
      details.push({
        step: fieldName,
        status: "FAIL",
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  async function clickLegalLinkAndValidate({ linkText, headingRegex, screenshotName, urlField }) {
    const link = await pickFirstVisible([
      appPage.getByRole("link", { name: linkText }),
      appPage.getByRole("button", { name: linkText }),
      appPage.locator("a,button").filter({ hasText: linkText }),
    ]);

    const appUrlBeforeClick = appPage.url();
    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

    await link.click();
    await waitForUiToSettle(appPage);

    const popup = await popupPromise;
    const legalPage = popup || appPage;

    await legalPage.waitForLoadState("domcontentloaded");
    await expect(legalPage.getByRole("heading", { name: headingRegex })).toBeVisible();
    await expect(legalPage.locator("main, article, body")).toContainText(headingRegex);

    legalUrls[urlField] = legalPage.url();
    await saveCheckpoint(legalPage, testInfo, screenshotName, true);

    if (popup) {
      await popup.close();
      await appPage.bringToFront();
      await waitForUiToSettle(appPage);
    } else if (legalPage.url() !== appUrlBeforeClick) {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        await appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
      });
      await waitForUiToSettle(appPage);
    }
  }

  await runValidationStep("Login", async () => {
    if (appPage.url() === "about:blank") {
      const loginUrl = process.env.SALEADS_LOGIN_URL;
      if (!loginUrl) {
        throw new Error(
          "Browser opened at about:blank. Set SALEADS_LOGIN_URL or preload the SaleADS login page."
        );
      }

      await appPage.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(appPage);
    }

    const googleSignInButton = await pickFirstVisible([
      appPage.getByRole("button", {
        name: /sign in with google|continue with google|iniciar sesi[oó]n con google|continuar con google/i,
      }),
      appPage.getByRole("link", {
        name: /sign in with google|continue with google|iniciar sesi[oó]n con google|continuar con google/i,
      }),
      appPage.locator("button,a").filter({ hasText: /google/i }),
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

    await clickAndWait(googleSignInButton, appPage);

    const authPage = (await popupPromise) || appPage;
    await authPage.waitForLoadState("domcontentloaded");

    const accountOption = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await waitForUiToSettle(authPage);
    }

    if (authPage !== appPage) {
      await authPage.waitForLoadState("networkidle").catch(() => {});
      await appPage.bringToFront();
      await appPage.waitForLoadState("domcontentloaded");
    }

    await expect(
      appPage.locator("aside").or(appPage.locator("nav")).or(appPage.getByText(/negocio/i))
    ).toBeVisible({ timeout: 45000 });

    await expect(appPage.getByText(/negocio/i)).toBeVisible({ timeout: 45000 });
    await saveCheckpoint(appPage, testInfo, "01-dashboard-loaded.png", true);
  });

  await runValidationStep("Mi Negocio menu", async () => {
    const negocioSection = await pickFirstVisible([
      appPage.getByRole("button", { name: /negocio/i }),
      appPage.getByRole("link", { name: /negocio/i }),
      appPage.locator("a,button,div").filter({ hasText: /^Negocio$/i }),
    ]);
    await clickAndWait(negocioSection, appPage);

    const miNegocioOption = await pickFirstVisible([
      appPage.getByRole("button", { name: /mi negocio/i }),
      appPage.getByRole("link", { name: /mi negocio/i }),
      appPage.locator("a,button,div").filter({ hasText: /mi negocio/i }),
    ]);
    await clickAndWait(miNegocioOption, appPage);

    await expect(appPage.getByText("Agregar Negocio", { exact: false })).toBeVisible();
    await expect(appPage.getByText("Administrar Negocios", { exact: false })).toBeVisible();
    await saveCheckpoint(appPage, testInfo, "02-mi-negocio-menu-expanded.png", true);
  });

  await runValidationStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await pickFirstVisible([
      appPage.getByRole("link", { name: /agregar negocio/i }),
      appPage.getByRole("button", { name: /agregar negocio/i }),
      appPage.getByText("Agregar Negocio", { exact: false }),
    ]);
    await clickAndWait(agregarNegocio, appPage);

    await expect(appPage.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible();
    await expect(appPage.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(appPage.getByText(BUSINESS_QUOTA_TEXT)).toBeVisible();
    await expect(appPage.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await saveCheckpoint(appPage, testInfo, "03-crear-nuevo-negocio-modal.png", true);

    const nombreNegocioField = appPage.getByLabel(/Nombre del Negocio/i);
    await nombreNegocioField.click();
    await waitForUiToSettle(appPage);
    await nombreNegocioField.fill("Negocio Prueba Automatización");
    await waitForUiToSettle(appPage);

    await clickAndWait(appPage.getByRole("button", { name: /^Cancelar$/i }), appPage);
    await expect(appPage.getByRole("heading", { name: /Crear Nuevo Negocio/i })).not.toBeVisible();
  });

  await runValidationStep("Administrar Negocios view", async () => {
    const miNegocioToggle = await pickFirstVisible([
      appPage.getByRole("button", { name: /mi negocio/i }),
      appPage.getByRole("link", { name: /mi negocio/i }),
      appPage.locator("a,button,div").filter({ hasText: /mi negocio/i }),
    ]);
    await clickAndWait(miNegocioToggle, appPage);

    const administrarNegocios = await pickFirstVisible([
      appPage.getByRole("link", { name: /administrar negocios/i }),
      appPage.getByRole("button", { name: /administrar negocios/i }),
      appPage.getByText("Administrar Negocios", { exact: false }),
    ]);
    await clickAndWait(administrarNegocios, appPage);

    await expect(appPage.getByText("Información General", { exact: false })).toBeVisible();
    await expect(appPage.getByText("Detalles de la Cuenta", { exact: false })).toBeVisible();
    await expect(appPage.getByText("Tus Negocios", { exact: false })).toBeVisible();
    await expect(appPage.getByText("Sección Legal", { exact: false })).toBeVisible();
    await saveCheckpoint(appPage, testInfo, "04-administrar-negocios-page.png", true);
  });

  await runValidationStep("Información General", async () => {
    await expect(
      appPage.locator("section,div").filter({ hasText: /Informaci[oó]n General/i }).first()
    ).toBeVisible();
    await expect(appPage.getByText(/@/)).toBeVisible();
    await expect(appPage.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runValidationStep("Detalles de la Cuenta", async () => {
    await expect(
      appPage.locator("section,div").filter({ hasText: /Detalles de la Cuenta/i }).first()
    ).toBeVisible();
    await expect(appPage.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(appPage.getByText(/Estado activo/i)).toBeVisible();
    await expect(appPage.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runValidationStep("Tus Negocios", async () => {
    await expect(appPage.getByText("Tus Negocios", { exact: false })).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(appPage.getByText(BUSINESS_QUOTA_TEXT)).toBeVisible();
  });

  await runValidationStep("Términos y Condiciones", async () => {
    await clickLegalLinkAndValidate({
      linkText: "Términos y Condiciones",
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      urlField: "terms",
    });
  });

  await runValidationStep("Política de Privacidad", async () => {
    await clickLegalLinkAndValidate({
      linkText: "Política de Privacidad",
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      urlField: "privacy",
    });
  });

  const reportPayload = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    finalResult: Object.values(resultByField).every((status) => status === "PASS") ? "PASS" : "FAIL",
    validations: resultByField,
    evidence: {
      termsUrl: legalUrls.terms,
      privacyUrl: legalUrls.privacy,
    },
    failures: details,
  };

  const reportPath = testInfo.outputPath(path.join("artifacts", "final-report.json"));
  await fs.mkdir(path.dirname(reportPath), { recursive: true });
  await fs.writeFile(reportPath, JSON.stringify(reportPayload, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  // Print report to terminal logs for CI discoverability.
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(reportPayload, null, 2));

  expect(reportPayload.finalResult, "One or more SaleADS workflow validations failed.").toBe("PASS");
});
