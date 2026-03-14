const fs = require("fs");
const { expect, test } = require("@playwright/test");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function maybeClickAccountSelection(targetPage, email) {
  const accountOption = targetPage.getByText(email, { exact: false });

  if ((await accountOption.count()) > 0) {
    await accountOption.first().click();
    await waitForUi(targetPage);
    return true;
  }

  return false;
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  const outputPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
  const report = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }])
  );
  const legalUrls = {};

  async function runStep(field, handler) {
    try {
      const details = await handler();
      report[field] = { status: "PASS", ...(details ? { details } : {}) };
    } catch (error) {
      report[field] = {
        status: "FAIL",
        details: error instanceof Error ? error.message : String(error)
      };
    }
  }

  const providedUrl = process.env.SALEADS_START_URL;
  if (providedUrl) {
    await page.goto(providedUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else {
    await waitForUi(page);
  }

  await runStep("Login", async () => {
    const loginButton = page
      .getByRole("button", {
        name: /sign in with google|iniciar sesión con google|continuar con google|google/i
      })
      .first();
    const loginText = page
      .getByText(/sign in with google|iniciar sesión con google|continuar con google/i)
      .first();

    const hasLoginButton = (await loginButton.count()) > 0;
    const hasLoginText = (await loginText.count()) > 0;

    if (hasLoginButton || hasLoginText) {
      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, hasLoginButton ? loginButton : loginText);

      const popup = await popupPromise;
      if (popup) {
        await waitForUi(popup);
        await maybeClickAccountSelection(popup, "juanlucasbarbiergarzon@gmail.com");
      } else {
        await maybeClickAccountSelection(page, "juanlucasbarbiergarzon@gmail.com");
      }
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30000 });
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png");
    return "Main application interface and left sidebar are visible.";
  });

  await runStep("Mi Negocio menu", async () => {
    const miNegocioOption = page.getByText(/mi negocio/i).first();
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
    return "Submenu expanded with Agregar Negocio and Administrar Negocios.";
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickAndWait(page, page.getByText(/agregar negocio/i).first());

    const modal = page
      .locator('[role="dialog"], [aria-modal="true"], .modal')
      .filter({ hasText: /crear nuevo negocio/i })
      .first();

    await expect(modal).toBeVisible();
    await expect(modal.getByText(/crear nuevo negocio/i).first()).toBeVisible();
    await expect(modal.getByText(/nombre del negocio/i).first()).toBeVisible();
    await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const businessNameInput = modal
      .locator('input[placeholder*="Negocio" i], input[name*="negocio" i], input[id*="negocio" i], input')
      .first();
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, modal.getByRole("button", { name: /cancelar/i }).first());

    return "Crear Nuevo Negocio modal validated and cancelled after optional input.";
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarOption = page.getByText(/administrar negocios/i).first();
    if ((await administrarOption.count()) === 0 || !(await administrarOption.isVisible())) {
      await clickAndWait(page, page.getByText(/mi negocio/i).first());
    }

    await clickAndWait(page, page.getByText(/administrar negocios/i).first());

    await expect(page.getByText(/información general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/sección legal/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-view.png", true);

    return "All account sections are visible.";
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();

    const visibleEmail = page.getByText(/@/).first();
    await expect(visibleEmail).toBeVisible();

    const userNameCandidate = page
      .locator("h1, h2, h3, p, span, div")
      .filter({ hasNotText: /@|business plan|cambiar plan/i })
      .first();
    await expect(userNameCandidate).toBeVisible();

    return "User name, email, BUSINESS PLAN and Cambiar Plan are visible.";
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
    return "Cuenta creada, Estado activo and Idioma seleccionado are visible.";
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    return "Business list area, Agregar Negocio button and quota text are visible.";
  });

  async function validateLegalPage({ linkText, headingRegex, reportField, screenshotName }) {
    await runStep(reportField, async () => {
      const link = page.getByText(linkText, { exact: false }).first();
      await expect(link).toBeVisible();

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await link.click();
      const popup = await popupPromise;

      const legalPage = popup || page;
      await waitForUi(legalPage);

      let headingValidated = false;
      try {
        await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible();
        headingValidated = true;
      } catch (_error) {
        headingValidated = false;
      }

      if (!headingValidated) {
        await expect(legalPage.getByText(headingRegex).first()).toBeVisible();
      }

      const legalText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
      expect(legalText.length).toBeGreaterThan(80);

      await captureCheckpoint(legalPage, testInfo, screenshotName, true);
      legalUrls[reportField] = legalPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }

      return `Validated legal content and captured URL: ${legalUrls[reportField]}`;
    });
  }

  await validateLegalPage({
    linkText: "Términos y Condiciones",
    headingRegex: /términos y condiciones/i,
    reportField: "Términos y Condiciones",
    screenshotName: "08-terminos-y-condiciones.png"
  });

  await validateLegalPage({
    linkText: "Política de Privacidad",
    headingRegex: /política de privacidad/i,
    reportField: "Política de Privacidad",
    screenshotName: "09-politica-de-privacidad.png"
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generated_at: new Date().toISOString(),
    results: report,
    legal_urls: legalUrls
  };

  const reportPath = testInfo.outputPath("10-final-report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

  // Needed by automation systems that parse stdout for step outcomes.
  console.log("FINAL_REPORT", JSON.stringify(finalReport));

  const failedSteps = Object.entries(report)
    .filter(([, result]) => result.status === "FAIL")
    .map(([field]) => field);

  expect(
    failedSteps,
    `Failed validation steps: ${failedSteps.join(", ") || "None"}`
  ).toEqual([]);
});
