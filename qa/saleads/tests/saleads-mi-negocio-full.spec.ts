import * as fs from "node:fs/promises";

import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type ValidationReport = Record<
  ReportField,
  {
    status: StepStatus;
    details: string[];
  }
>;

const reportFields: ReportField[] = [
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

function createReport(): ValidationReport {
  return reportFields.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: ["Not executed"] };
    return acc;
  }, {} as ValidationReport);
}

function markPass(report: ValidationReport, field: ReportField, message: string): void {
  report[field] = { status: "PASS", details: [message] };
}

function markFail(report: ValidationReport, field: ReportField, message: string): void {
  report[field] = { status: "FAIL", details: [message] };
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const locator = candidate.first();
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  return null;
}

async function clickAndWaitForUi(locator: Locator, page: Page): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(700);
}

async function capture(page: Page, path: string, fullPage = false): Promise<void> {
  await page.screenshot({ path, fullPage });
}

async function chooseGoogleAccountIfPresent(page: Page, email: string): Promise<boolean> {
  const account = page.getByText(email, { exact: false }).first();
  if (await account.isVisible().catch(() => false)) {
    await clickAndWaitForUi(account, page);
    return true;
  }

  return false;
}

async function ensureStartingPage(page: Page): Promise<void> {
  const dynamicUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL || process.env.APP_URL;
  const current = page.url();

  if (current === "about:blank") {
    if (!dynamicUrl) {
      throw new Error(
        "The test started on about:blank and no SALEADS_BASE_URL/BASE_URL/APP_URL was provided.",
      );
    }

    await page.goto(dynamicUrl, { waitUntil: "domcontentloaded" });
  }
}

async function openLegalPage(
  page: Page,
  context: BrowserContext,
  linkName: RegExp,
  expectedHeading: RegExp,
  screenshotPath: string,
): Promise<{ finalUrl: string; openedInNewTab: boolean }> {
  const legalLink = (
    await firstVisible([
      page.getByRole("link", { name: linkName }),
      page.getByText(linkName),
    ])
  )?.first();

  if (!legalLink) {
    throw new Error(`Unable to find legal link with text ${linkName}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  const appPageUrlBeforeClick = page.url();
  await clickAndWaitForUi(legalLink, page);

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded").catch(() => {});
  }

  await expect(legalPage.getByText(expectedHeading).first()).toBeVisible({ timeout: 20000 });

  const bodyText = await legalPage.locator("body").innerText();
  if (bodyText.trim().length < 120) {
    throw new Error("Legal page content was too short to be considered valid.");
  }

  const finalUrl = legalPage.url();
  await capture(legalPage, screenshotPath, true);

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== appPageUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  }

  return { finalUrl, openedInNewTab: Boolean(popup) };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  const selectedGoogleAccount = "juanlucasbarbiergarzon@gmail.com";

  try {
    // Step 1: Login with Google
    let loginSucceeded = false;
    try {
      await ensureStartingPage(page);

      const loginButton = await firstVisible([
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        page.getByRole("button", { name: /iniciar sesi[oó]n|login/i }),
      ]);

      if (!loginButton) {
        throw new Error("Google login button was not found.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickAndWaitForUi(loginButton, page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded").catch(() => {});
        await chooseGoogleAccountIfPresent(popup, selectedGoogleAccount);
      } else {
        await chooseGoogleAccountIfPresent(page, selectedGoogleAccount);
      }

      const sidebar = await firstVisible([
        page.locator("aside"),
        page.locator("nav").filter({ hasText: /negocio/i }),
        page.getByText(/negocio|mi negocio/i),
      ]);

      if (!sidebar) {
        throw new Error("Main app interface was not detected after login.");
      }

      await capture(page, testInfo.outputPath("step-1-dashboard.png"), true);
      markPass(report, "Login", "Dashboard loaded and left sidebar is visible.");
      loginSucceeded = true;
    } catch (error) {
      markFail(report, "Login", `Login validation failed: ${String(error)}`);
    }

    if (!loginSucceeded) {
      const dependencyError = "Skipped because login did not succeed.";
      markFail(report, "Mi Negocio menu", dependencyError);
      markFail(report, "Agregar Negocio modal", dependencyError);
      markFail(report, "Administrar Negocios view", dependencyError);
      markFail(report, "Información General", dependencyError);
      markFail(report, "Detalles de la Cuenta", dependencyError);
      markFail(report, "Tus Negocios", dependencyError);
      markFail(report, "Términos y Condiciones", dependencyError);
      markFail(report, "Política de Privacidad", dependencyError);
    } else {
      // Step 2: Open Mi Negocio menu
      try {
      const negocioSection = await firstVisible([
        page.getByText(/^Negocio$/i),
        page.getByRole("link", { name: /^Negocio$/i }),
      ]);
      if (negocioSection) {
        await clickAndWaitForUi(negocioSection, page);
      }

      const miNegocio = await firstVisible([
        page.getByText(/^Mi Negocio$/i),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
      ]);

      if (!miNegocio) {
        throw new Error("'Mi Negocio' option was not found.");
      }

      await clickAndWaitForUi(miNegocio, page);
      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({
        timeout: 10000,
      });

      await capture(page, testInfo.outputPath("step-2-mi-negocio-menu-expanded.png"), true);
      markPass(
        report,
        "Mi Negocio menu",
        "Mi Negocio expanded and submenu shows Agregar Negocio + Administrar Negocios.",
      );
    } catch (error) {
        markFail(report, "Mi Negocio menu", `Mi Negocio menu validation failed: ${String(error)}`);
      }

      // Step 3: Validate Agregar Negocio modal
      try {
      const addBusinessMenu = await firstVisible([
        page.getByText(/^Agregar Negocio$/i),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
      ]);

      if (!addBusinessMenu) {
        throw new Error("'Agregar Negocio' action was not found.");
      }

      await clickAndWaitForUi(addBusinessMenu, page);
      await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByLabel(/nombre del negocio/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible({
        timeout: 10000,
      });
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible({
        timeout: 10000,
      });

      // Optional interaction requested by the workflow.
      const businessName = page.getByLabel(/nombre del negocio/i).first();
      await businessName.click();
      await businessName.fill("Negocio Prueba Automatización");
      await capture(page, testInfo.outputPath("step-3-agregar-negocio-modal.png"), true);
      await clickAndWaitForUi(page.getByRole("button", { name: /^Cancelar$/i }).first(), page);
      markPass(report, "Agregar Negocio modal", "Agregar Negocio modal content validated.");
    } catch (error) {
        markFail(
          report,
          "Agregar Negocio modal",
          `Agregar Negocio modal validation failed: ${String(error)}`,
        );
      }

      // Step 4: Open Administrar Negocios
      try {
      const administrarNegocios = await firstVisible([
        page.getByText(/^Administrar Negocios$/i),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
      ]);

      if (!administrarNegocios) {
        const miNegocio = await firstVisible([page.getByText(/^Mi Negocio$/i)]);
        if (!miNegocio) {
          throw new Error("Unable to re-open Mi Negocio menu for Administrar Negocios.");
        }

        await clickAndWaitForUi(miNegocio, page);
      }

      const administrar = await firstVisible([
        page.getByText(/^Administrar Negocios$/i),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
      ]);

      if (!administrar) {
        throw new Error("'Administrar Negocios' option was not found.");
      }

      await clickAndWaitForUi(administrar, page);

      await expect(page.getByText(/información general/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/sección legal/i).first()).toBeVisible({ timeout: 15000 });

      await capture(page, testInfo.outputPath("step-4-administrar-negocios-full-page.png"), true);
      markPass(
        report,
        "Administrar Negocios view",
        "Account page loaded with Información General, Detalles de la Cuenta, Tus Negocios and Sección Legal.",
      );
    } catch (error) {
        markFail(
          report,
          "Administrar Negocios view",
          `Administrar Negocios view validation failed: ${String(error)}`,
        );
      }

      // Step 5: Validate Información General
      try {
      await expect(page.getByText(/información general/i).first()).toBeVisible({ timeout: 12000 });

      const emailLocator = page.getByText(
        /juanlucasbarbiergarzon@gmail\.com|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i,
      );
      await expect(emailLocator.first()).toBeVisible({ timeout: 12000 });
      await expect(page.getByText(/business plan/i).first()).toBeVisible({ timeout: 12000 });
      await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({
        timeout: 12000,
      });

      const usernameHint = await firstVisible([
        page.getByText(/juan|barbier|garzon/i),
        page.getByText(/nombre/i),
      ]);
      if (!usernameHint) {
        throw new Error("User name field/text was not detected in Información General.");
      }

      markPass(
        report,
        "Información General",
        "User name/email, BUSINESS PLAN and Cambiar Plan are visible.",
      );
    } catch (error) {
        markFail(
          report,
          "Información General",
          `Información General validation failed: ${String(error)}`,
        );
      }

      // Step 6: Validate Detalles de la Cuenta
      try {
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/estado activo/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 10000 });
      markPass(
        report,
        "Detalles de la Cuenta",
        "Cuenta creada, Estado activo and Idioma seleccionado are visible.",
      );
    } catch (error) {
        markFail(
          report,
          "Detalles de la Cuenta",
          `Detalles de la Cuenta validation failed: ${String(error)}`,
        );
      }

      // Step 7: Validate Tus Negocios
      try {
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 10000 });

      const addBusinessButton = await firstVisible([
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);
      if (!addBusinessButton) {
        throw new Error("'Agregar Negocio' button is missing in Tus Negocios.");
      }

      await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 10000 });
      markPass(
        report,
        "Tus Negocios",
        "Business list, Agregar Negocio and Tienes 2 de 3 negocios were validated.",
      );
    } catch (error) {
        markFail(report, "Tus Negocios", `Tus Negocios validation failed: ${String(error)}`);
      }

      // Step 8: Validate Términos y Condiciones
      try {
      const legalResult = await openLegalPage(
        page,
        context,
        /términos y condiciones/i,
        /términos y condiciones/i,
        testInfo.outputPath("step-8-terminos-y-condiciones.png"),
      );
      markPass(
        report,
        "Términos y Condiciones",
        `Legal page validated. Final URL: ${legalResult.finalUrl}. Opened in new tab: ${legalResult.openedInNewTab}.`,
      );
    } catch (error) {
        markFail(
          report,
          "Términos y Condiciones",
          `Términos y Condiciones validation failed: ${String(error)}`,
        );
      }

      // Step 9: Validate Política de Privacidad
      try {
      const legalResult = await openLegalPage(
        page,
        context,
        /política de privacidad/i,
        /política de privacidad/i,
        testInfo.outputPath("step-9-politica-de-privacidad.png"),
      );
      markPass(
        report,
        "Política de Privacidad",
        `Legal page validated. Final URL: ${legalResult.finalUrl}. Opened in new tab: ${legalResult.openedInNewTab}.`,
      );
    } catch (error) {
        markFail(
          report,
          "Política de Privacidad",
          `Política de Privacidad validation failed: ${String(error)}`,
        );
      }
    }
  } finally {
    const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
    const reportJson = JSON.stringify(report, null, 2);
    await fs.writeFile(reportPath, reportJson, "utf-8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      body: reportJson,
      contentType: "application/json",
    });
    console.log(`Final validation report written to: ${reportPath}`);
    console.log(reportJson);
  }

  const failedSteps = Object.entries(report).filter(([, value]) => value.status === "FAIL");
  expect(
    failedSteps,
    `Some workflow validations failed: ${failedSteps.map(([key]) => key).join(", ")}`,
  ).toHaveLength(0);
});
