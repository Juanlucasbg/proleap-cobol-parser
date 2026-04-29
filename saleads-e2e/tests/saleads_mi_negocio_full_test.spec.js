const fs = require("fs");
const { test, expect } = require("@playwright/test");
const { createStepReporter } = require("../helpers/reporter");

const ACCOUNT_EMAIL = process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const BASE_URL = process.env.SALEADS_BASE_URL;
const EXPECTED_BUSINESS_USAGE = process.env.SALEADS_EXPECTED_BUSINESS_USAGE || "Tienes 2 de 3 negocios";
const MODAL_BUSINESS_NAME = "Negocio Prueba Automatización";

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function normalizeAccentedPattern(text) {
  return text
    .replace(/a/gi, "[aáàäâ]")
    .replace(/e/gi, "[eéèëê]")
    .replace(/i/gi, "[iíìïî]")
    .replace(/o/gi, "[oóòöô]")
    .replace(/u/gi, "[uúùüû]");
}

async function waitUiAfterClick(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function clickVisibleByText(page, text) {
  const regex = new RegExp(normalizeAccentedPattern(escapeRegex(text)), "i");
  const candidates = [
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByRole("menuitem", { name: regex }).first(),
    page.getByText(regex).first()
  ];

  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click();
      await waitUiAfterClick(page);
      return;
    }
  }

  throw new Error(`No clickable visible element found for text "${text}"`);
}

async function takeCheckpoint(page, testInfo, fileName) {
  const checkpointPath = testInfo.outputPath(`${fileName}.png`);
  await page.screenshot({ path: checkpointPath, fullPage: true });
  await testInfo.attach(fileName, {
    path: checkpointPath,
    contentType: "image/png"
  });
}

async function findBusinessNameInput(page) {
  const candidates = [
    page.getByLabel(/Nombre del Negocio/i).first(),
    page.getByPlaceholder(/Nombre del Negocio/i).first(),
    page.locator("input").filter({ hasText: /Nombre del Negocio/i }).first(),
    page.locator("input[type='text']").first()
  ];

  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  throw new Error('No input found for "Nombre del Negocio"');
}

async function waitForSidebar(page) {
  const sidebar = page.getByRole("navigation").first();
  if (await sidebar.isVisible().catch(() => false)) {
    return;
  }

  const menuLabel = page.getByText(/Negocio|Mi Negocio/i).first();
  await expect(menuLabel).toBeVisible({ timeout: 60_000 });
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login and validate Mi Negocio workflow", async ({ page, context }, testInfo) => {
    const reporter = createStepReporter();
    const failedSteps = [];

    async function runValidationStep(stepName, callback) {
      await test.step(stepName, async () => {
        try {
          await callback();
        } catch (error) {
          const message = error instanceof Error ? error.message : String(error);
          failedSteps.push({ step: stepName, message });
        }
      });
    }

    if (BASE_URL) {
      await page.goto(BASE_URL, { waitUntil: "domcontentloaded" });
    }

    await runValidationStep("1. Login with Google", async () => {
      const loginTrigger = page
        .getByText(/Sign in with Google|Iniciar sesión con Google|Continuar con Google/i)
        .first();

      if (await loginTrigger.isVisible().catch(() => false)) {
        const popupPromise = context.waitForEvent("page", { timeout: 20_000 }).catch(() => null);
        await loginTrigger.click();
        await waitUiAfterClick(page);

        const popup = await popupPromise;
        if (popup) {
          await popup.waitForLoadState("domcontentloaded");
          const accountOption = popup.getByText(ACCOUNT_EMAIL, { exact: false }).first();
          if (await accountOption.isVisible().catch(() => false)) {
            await accountOption.click();
            await popup.waitForLoadState("domcontentloaded");
          }
          await popup.waitForEvent("close", { timeout: 90_000 }).catch(async () => {
            if (!popup.isClosed()) {
              await popup.close();
            }
          });
        }
      }

      await page.waitForLoadState("networkidle").catch(() => {});
      await waitForSidebar(page);
      await takeCheckpoint(page, testInfo, "01-dashboard-loaded");
      reporter.pass("Login", "Main interface loaded and left sidebar is visible.");
    });

    await runValidationStep("2. Open Mi Negocio menu", async () => {
      await clickVisibleByText(page, "Negocio");
      await clickVisibleByText(page, "Mi Negocio");
      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 30_000 });
      await takeCheckpoint(page, testInfo, "02-mi-negocio-expanded");
      reporter.pass("Mi Negocio menu", "Submenu expanded with Agregar and Administrar options.");
    });

    await runValidationStep("3. Validate Agregar Negocio modal", async () => {
      await clickVisibleByText(page, "Agregar Negocio");
      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 30_000 });

      const businessNameInput = await findBusinessNameInput(page);
      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(new RegExp(normalizeAccentedPattern(EXPECTED_BUSINESS_USAGE), "i")).first())
        .toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

      await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal");
      await businessNameInput.fill(MODAL_BUSINESS_NAME);
      await page.getByRole("button", { name: /Cancelar/i }).first().click();
      await waitUiAfterClick(page);

      reporter.pass("Agregar Negocio modal", "Modal fields/buttons validated and canceled.");
    });

    await runValidationStep("4. Open Administrar Negocios", async () => {
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
        await clickVisibleByText(page, "Mi Negocio");
      }

      await clickVisibleByText(page, "Administrar Negocios");
      await page.waitForLoadState("networkidle").catch(() => {});

      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 30_000 });
      await takeCheckpoint(page, testInfo, "04-administrar-negocios");

      reporter.pass("Administrar Negocios view", "Account page sections are visible.");
    });

    await runValidationStep("5. Validate Información General", async () => {
      await expect(page.getByText(new RegExp(escapeRegex(ACCOUNT_EMAIL), "i")).first()).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
      reporter.pass("Información General", "Name/email and plan controls are visible.");
    });

    await runValidationStep("6. Validate Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo|activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
      reporter.pass("Detalles de la Cuenta", "Account details labels are visible.");
    });

    await runValidationStep("7. Validate Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
      await expect(page.getByText(new RegExp(normalizeAccentedPattern(EXPECTED_BUSINESS_USAGE), "i")).first())
        .toBeVisible();
      reporter.pass("Tus Negocios", "Business list section and expected quota text are visible.");
    });

    async function validateLegalPage(label, headingRegex, screenshotName) {
      const newTabPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
      await clickVisibleByText(page, label);

      const popup = await newTabPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await popup.waitForLoadState("networkidle").catch(() => {});
        await expect(popup.getByText(headingRegex).first()).toBeVisible({ timeout: 30_000 });
        await expect(popup.locator("body")).toContainText(/[A-Za-z0-9]{10,}/);
        await takeCheckpoint(popup, testInfo, screenshotName);

        const finalUrl = popup.url();
        await popup.close();
        await page.bringToFront();
        await waitUiAfterClick(page);
        return finalUrl;
      }

      await page.waitForLoadState("domcontentloaded");
      await page.waitForLoadState("networkidle").catch(() => {});
      await expect(page.getByText(headingRegex).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.locator("body")).toContainText(/[A-Za-z0-9]{10,}/);
      await takeCheckpoint(page, testInfo, screenshotName);
      const finalUrl = page.url();
      await page.goBack().catch(() => {});
      await page.waitForLoadState("domcontentloaded");
      return finalUrl;
    }

    await runValidationStep("8. Validate Términos y Condiciones", async () => {
      const termsUrl = await validateLegalPage(
        "Términos y Condiciones",
        /T[eé]rminos y Condiciones/i,
        "08-terminos-y-condiciones"
      );
      reporter.setLegalUrl("terminosYCondiciones", termsUrl);
      reporter.pass("Términos y Condiciones", `Heading/content validated. Final URL: ${termsUrl}`);
    });

    await runValidationStep("9. Validate Política de Privacidad", async () => {
      const privacyUrl = await validateLegalPage(
        "Política de Privacidad",
        /Pol[ií]tica de Privacidad/i,
        "09-politica-de-privacidad"
      );
      reporter.setLegalUrl("politicaDePrivacidad", privacyUrl);
      reporter.pass("Política de Privacidad", `Heading/content validated. Final URL: ${privacyUrl}`);
    });

    await test.step("10. Final Report", async () => {
      for (const failure of failedSteps) {
        if (failure.step.includes("Login")) reporter.fail("Login", failure.message);
        if (failure.step.includes("Mi Negocio menu")) reporter.fail("Mi Negocio menu", failure.message);
        if (failure.step.includes("Agregar Negocio modal")) reporter.fail("Agregar Negocio modal", failure.message);
        if (failure.step.includes("Open Administrar Negocios")) reporter.fail("Administrar Negocios view", failure.message);
        if (failure.step.includes("Información General")) reporter.fail("Información General", failure.message);
        if (failure.step.includes("Detalles de la Cuenta")) reporter.fail("Detalles de la Cuenta", failure.message);
        if (failure.step.includes("Tus Negocios")) reporter.fail("Tus Negocios", failure.message);
        if (failure.step.includes("Términos y Condiciones")) reporter.fail("Términos y Condiciones", failure.message);
        if (failure.step.includes("Política de Privacidad")) reporter.fail("Política de Privacidad", failure.message);
      }

      const textReport = reporter.renderSummary();
      const textReportPath = testInfo.outputPath("final-report.txt");
      fs.writeFileSync(textReportPath, textReport, "utf8");
      await testInfo.attach("final-report", { path: textReportPath, contentType: "text/plain" });

      const jsonReportPath = testInfo.outputPath("final-report.json");
      reporter.writeJsonReport(jsonReportPath);
      await testInfo.attach("final-report-json", {
        path: jsonReportPath,
        contentType: "application/json"
      });

      console.log(`\n${textReport}\n`);
      expect(failedSteps, `Step failures: ${JSON.stringify(failedSteps, null, 2)}`).toEqual([]);
    });
  });
});
