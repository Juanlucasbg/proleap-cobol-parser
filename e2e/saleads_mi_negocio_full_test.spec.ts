import fs from "node:fs";
import { expect, type Locator, type Page, test } from "@playwright/test";

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

type StepResult = "PASS" | "FAIL";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
}

async function visible(locator: Locator, timeout = 5_000): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function firstVisible(candidates: Locator[], timeout = 5_000): Promise<Locator> {
  for (const candidate of candidates) {
    if (await visible(candidate, timeout)) {
      return candidate.first();
    }
  }

  throw new Error("No visible candidate element was found.");
}

async function maybeChooseGoogleAccount(targetPage: Page): Promise<void> {
  const accountOption = targetPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")).first();
  if (await visible(accountOption, 5_000)) {
    await accountOption.click();
    await waitForUi(targetPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const results: Record<ReportField, StepResult> = {
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

  const errors: string[] = [];
  const legalUrls: { termsAndConditions?: string; privacyPolicy?: string } = {};

  const runStep = async (field: ReportField, fn: () => Promise<void>) => {
    try {
      await fn();
      results[field] = "PASS";
    } catch (error) {
      results[field] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      errors.push(`${field}: ${message}`);
    }
  };

  await runStep("Login", async () => {
    const configuredUrl =
      process.env.SALEADS_BASE_URL || process.env.BASE_URL || process.env.SALEADS_LOGIN_URL;

    const sidebar = page.locator("aside, nav").first();
    const alreadyAuthenticated = await visible(sidebar, 5_000);

    if (!alreadyAuthenticated) {
      if (page.url() === "about:blank" && !configuredUrl) {
        throw new Error(
          "No URL configured. Set SALEADS_BASE_URL (or BASE_URL/SALEADS_LOGIN_URL) for the current SaleADS environment.",
        );
      }

      if (configuredUrl) {
        await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
        await waitForUi(page);
      }

      const loginButton = await firstVisible([
        page.getByRole("button", { name: /google|iniciar.*google|continuar.*google|sign in/i }),
        page.getByRole("link", { name: /google|iniciar.*google|continuar.*google|sign in/i }),
        page.locator("button, a").filter({ hasText: /google/i }),
      ]);

      const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await loginButton.click();
      await waitForUi(page);

      const popupPage = await popupPromise;
      if (popupPage) {
        await popupPage.waitForLoadState("domcontentloaded");
        await maybeChooseGoogleAccount(popupPage);
        await popupPage.waitForTimeout(1_000);
      } else {
        await maybeChooseGoogleAccount(page);
      }
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60_000 });

    // Checkpoint evidence: dashboard loaded after login.
    await page.screenshot({
      path: testInfo.outputPath("01_dashboard_loaded.png"),
      fullPage: true,
    });
  });

  await runStep("Mi Negocio menu", async () => {
    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 30_000 });

    const negocioMenu = await firstVisible(
      [sidebar.getByText(/^Negocio$/i), page.getByText(/^Negocio$/i)],
      10_000,
    );
    await negocioMenu.click();
    await waitForUi(page);

    const miNegocio = await firstVisible(
      [sidebar.getByText(/^Mi Negocio$/i), page.getByText(/^Mi Negocio$/i)],
      10_000,
    );
    await miNegocio.click();
    await waitForUi(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({
      timeout: 20_000,
    });

    await page.screenshot({
      path: testInfo.outputPath("02_mi_negocio_menu_expanded.png"),
      fullPage: true,
    });
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible([page.getByText(/^Agregar Negocio$/i)], 10_000);
    await agregarNegocio.click();
    await waitForUi(page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 20_000 });

    let nameInput = page.getByLabel(/Nombre del Negocio/i).first();
    if (!(await visible(nameInput, 3_000))) {
      nameInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
    }
    await expect(nameInput).toBeVisible({ timeout: 20_000 });

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: 20_000 });

    await page.screenshot({
      path: testInfo.outputPath("03_agregar_negocio_modal.png"),
      fullPage: true,
    });

    // Optional interaction from the requested workflow.
    await nameInput.fill("Negocio Prueba Automatización");
    await page.getByRole("button", { name: /^Cancelar$/i }).click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await visible(page.getByText(/^Administrar Negocios$/i).first(), 2_000);
    if (!administrarVisible) {
      const miNegocio = await firstVisible([page.getByText(/^Mi Negocio$/i)], 10_000);
      await miNegocio.click();
      await waitForUi(page);
    }

    const administrarNegocios = await firstVisible([page.getByText(/^Administrar Negocios$/i)], 10_000);
    await administrarNegocios.click();
    await waitForUi(page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 30_000 });

    await page.screenshot({
      path: testInfo.outputPath("04_administrar_negocios_page.png"),
      fullPage: true,
    });
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 20_000 });

    // User identity can appear in different places/components.
    const emailLocator = page.getByText(/@/).first();
    await expect(emailLocator).toBeVisible({ timeout: 20_000 });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible({ timeout: 20_000 });
  });

  const validateLegalLink = async (
    field: ReportField,
    linkText: RegExp,
    headingText: RegExp,
    screenshotFileName: string,
    urlField: "termsAndConditions" | "privacyPolicy",
  ) => {
    await runStep(field, async () => {
      const sourcePage = page;
      const originalUrl = sourcePage.url();

      const link = await firstVisible(
        [
          sourcePage.getByRole("link", { name: linkText }),
          sourcePage.getByRole("button", { name: linkText }),
          sourcePage.getByText(linkText),
        ],
        10_000,
      );

      const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await link.click();
      await waitForUi(sourcePage);

      let legalPage = await popupPromise;
      if (!legalPage) {
        legalPage = sourcePage;
      }

      await legalPage.waitForLoadState("domcontentloaded");
      await waitForUi(legalPage);

      await expect(legalPage.getByText(headingText).first()).toBeVisible({ timeout: 30_000 });
      const legalContent = await legalPage.locator("body").innerText();
      expect(legalContent.replace(/\s+/g, " ").trim().length).toBeGreaterThan(100);

      await legalPage.screenshot({
        path: testInfo.outputPath(screenshotFileName),
        fullPage: true,
      });

      legalUrls[urlField] = legalPage.url();

      if (legalPage !== sourcePage) {
        await legalPage.close();
        await sourcePage.bringToFront();
        await waitForUi(sourcePage);
      } else if (sourcePage.url() !== originalUrl) {
        await sourcePage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
          if (originalUrl && originalUrl !== "about:blank") {
            await sourcePage.goto(originalUrl, { waitUntil: "domcontentloaded" });
          }
        });
        await waitForUi(sourcePage);
      }
    });
  };

  await validateLegalLink(
    "Términos y Condiciones",
    /Términos y Condiciones/i,
    /Términos y Condiciones/i,
    "05_terminos_y_condiciones.png",
    "termsAndConditions",
  );

  await validateLegalLink(
    "Política de Privacidad",
    /Política de Privacidad/i,
    /Política de Privacidad/i,
    "06_politica_de_privacidad.png",
    "privacyPolicy",
  );

  const report = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environmentUrl: process.env.SALEADS_BASE_URL || process.env.BASE_URL || null,
    results,
    legalUrls,
    errors,
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_full_test_report.json");
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json",
  });

  expect(
    errors,
    errors.length
      ? `One or more workflow validations failed:\n${errors.map((entry) => `- ${entry}`).join("\n")}`
      : "",
  ).toEqual([]);
});
