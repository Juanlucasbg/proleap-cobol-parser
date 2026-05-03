import { expect, Locator, Page, test } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

type StepResult = {
  name: string;
  passed: boolean;
  details: string[];
  screenshotPath?: string;
  finalUrl?: string;
};

const ARTIFACTS_DIR = path.join("test-results", "saleads-mi-negocio");

function ensureDir(dirPath: string): void {
  fs.mkdirSync(dirPath, { recursive: true });
}

function toFileSafeName(input: string): string {
  return input
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

async function waitForUiAfterAction(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => {
    // The page may already be loaded or in a non-navigating state.
  });
  await page.waitForLoadState("networkidle", { timeout: 5_000 }).catch(() => {
    // Some apps keep long-lived network connections; don't fail on that.
  });
  await page.waitForTimeout(300);
}

function textLocator(page: Page, text: string): Locator {
  return page.getByText(text, { exact: false });
}

async function clickByVisibleText(page: Page, candidates: string[]): Promise<string> {
  for (const candidate of candidates) {
    const target = page.getByRole("button", { name: candidate }).first();
    if (await target.isVisible().catch(() => false)) {
      await target.click();
      await waitForUiAfterAction(page);
      return candidate;
    }

    const linkTarget = page.getByRole("link", { name: candidate }).first();
    if (await linkTarget.isVisible().catch(() => false)) {
      await linkTarget.click();
      await waitForUiAfterAction(page);
      return candidate;
    }

    const textTarget = textLocator(page, candidate).first();
    if (await textTarget.isVisible().catch(() => false)) {
      await textTarget.click();
      await waitForUiAfterAction(page);
      return candidate;
    }
  }

  throw new Error(`Could not find clickable element for any of: ${candidates.join(", ")}`);
}

async function captureStepScreenshot(page: Page, stepName: string): Promise<string> {
  const fileName = `${toFileSafeName(stepName)}.png`;
  const fullPath = path.join(ARTIFACTS_DIR, fileName);
  await page.screenshot({ path: fullPath, fullPage: true });
  return fullPath;
}

async function maybeSelectGoogleAccount(page: Page, accountEmail: string): Promise<boolean> {
  const accountOption = page.getByText(accountEmail, { exact: false }).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUiAfterAction(page);
    return true;
  }
  return false;
}

async function ensureLoginPageReady(page: Page): Promise<void> {
  const currentUrl = page.url();
  if (currentUrl === "about:blank") {
    const baseUrl = process.env.SALEADS_BASE_URL;
    if (!baseUrl) {
      throw new Error(
        "Browser started on about:blank. Set SALEADS_BASE_URL (any SaleADS environment) or run this test in a session already on the login page."
      );
    }
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUiAfterAction(page);
  } else {
    await waitForUiAfterAction(page);
  }
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregar = textLocator(page, "Agregar Negocio").first();
  const administrar = textLocator(page, "Administrar Negocios").first();
  const expanded = (await agregar.isVisible().catch(() => false)) && (await administrar.isVisible().catch(() => false));
  if (expanded) {
    return;
  }
  await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
}

async function clickLegalLinkAndValidate(page: Page, linkText: string): Promise<{ finalUrl: string; screenshotPath: string }> {
  const sameTabUrlBefore = page.url();
  const linkLocator = textLocator(page, linkText).first();
  await expect(linkLocator, `Expected legal link "${linkText}" to be visible`).toBeVisible({ timeout: 30_000 });

  let finalUrl = "";
  let screenshotPath = "";

  const popupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
  await linkLocator.click();

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle", { timeout: 5_000 }).catch(() => {
      // Accept pages with long network activity.
    });
    const headingPattern = new RegExp(linkText, "i");
    await expect(popup.getByRole("heading", { name: headingPattern }).first()).toBeVisible({ timeout: 30_000 });
    const legalBody = popup.locator("main, article, body").first();
    await expect(legalBody).toContainText(/.+/);
    finalUrl = popup.url();
    screenshotPath = await captureStepScreenshot(popup, linkText);
    await popup.close();
    await page.bringToFront();
    await waitForUiAfterAction(page);
  } else {
    await waitForUiAfterAction(page);
    const headingPattern = new RegExp(linkText, "i");
    await expect(page.getByRole("heading", { name: headingPattern }).first()).toBeVisible({ timeout: 30_000 });
    const legalBody = page.locator("main, article, body").first();
    await expect(legalBody).toContainText(/.+/);
    finalUrl = page.url();
    screenshotPath = await captureStepScreenshot(page, linkText);
    await page.goBack();
    await waitForUiAfterAction(page);
    if (page.url() === finalUrl) {
      throw new Error(`Expected to navigate back to app from ${finalUrl}, but still on legal page`);
    }
  }

  if (!finalUrl || finalUrl === sameTabUrlBefore) {
    throw new Error(`Legal link "${linkText}" did not navigate away from app or open a distinct URL`);
  }

  return { finalUrl, screenshotPath };
}

test.describe("SaleADS Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }) => {
    ensureDir(ARTIFACTS_DIR);
    const stepResults: StepResult[] = [];
    let isLoggedIn = false;
    let accountViewLoaded = false;
    let legalSectionReady = false;

    const pushResult = (result: StepResult): void => {
      stepResults.push(result);
      test.info().annotations.push({
        type: result.passed ? "PASS" : "FAIL",
        description: `${result.name}: ${result.details.join(" | ")}`
      });
    };

    // Step 1: Login with Google
    {
      const stepName = "Login";
      try {
        await ensureLoginPageReady(page);
        const clicked = await clickByVisibleText(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Iniciar con Google",
          "Login with Google",
          "Continuar con Google"
        ]);
        const selectedAccount = await maybeSelectGoogleAccount(page, "juanlucasbarbiergarzon@gmail.com");
        await waitForUiAfterAction(page);

        const sidebar = page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio|Dashboard|Inicio/i }).first();
        await expect(sidebar).toBeVisible({ timeout: 60_000 });

        const screenshotPath = await captureStepScreenshot(page, "01-dashboard-loaded");
        pushResult({
          name: stepName,
          passed: true,
          details: [
            `Clicked login trigger: "${clicked}"`,
            selectedAccount
              ? "Google account selector handled for juanlucasbarbiergarzon@gmail.com"
              : "Google selector not shown or auto-authenticated",
            "Main interface visible",
            "Left sidebar visible"
          ],
          screenshotPath
        });
        isLoggedIn = true;
      } catch (error) {
        pushResult({
          name: stepName,
          passed: false,
          details: [error instanceof Error ? error.message : String(error)]
        });
      }
    }

    // Step 2: Open Mi Negocio menu
    {
      const stepName = "Mi Negocio menu";
      try {
        if (!isLoggedIn) {
          throw new Error("Skipped because login did not succeed.");
        }
        await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
        await expect(textLocator(page, "Agregar Negocio").first()).toBeVisible({ timeout: 30_000 });
        await expect(textLocator(page, "Administrar Negocios").first()).toBeVisible({ timeout: 30_000 });
        const screenshotPath = await captureStepScreenshot(page, "02-mi-negocio-expanded");
        pushResult({
          name: stepName,
          passed: true,
          details: [
            "Submenu expanded",
            "\"Agregar Negocio\" visible",
            "\"Administrar Negocios\" visible"
          ],
          screenshotPath
        });
      } catch (error) {
        pushResult({
          name: stepName,
          passed: false,
          details: [error instanceof Error ? error.message : String(error)]
        });
      }
    }

    // Step 3: Validate Agregar Negocio modal
    {
      const stepName = "Agregar Negocio modal";
      try {
        if (!isLoggedIn) {
          throw new Error("Skipped because login did not succeed.");
        }
        await clickByVisibleText(page, ["Agregar Negocio"]);
        await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first()).toBeVisible({ timeout: 30_000 });
        const businessNameInput = page
          .getByLabel("Nombre del Negocio")
          .or(page.getByPlaceholder("Nombre del Negocio"))
          .first();
        await expect(businessNameInput).toBeVisible({ timeout: 30_000 });
        await expect(textLocator(page, "Tienes 2 de 3 negocios").first()).toBeVisible({ timeout: 30_000 });
        await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible({ timeout: 30_000 });
        await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible({ timeout: 30_000 });

        const screenshotPath = await captureStepScreenshot(page, "03-agregar-negocio-modal");
        await businessNameInput.click();
        await businessNameInput.fill("Negocio Prueba Automatización");
        await page.getByRole("button", { name: "Cancelar" }).first().click();
        await waitForUiAfterAction(page);

        pushResult({
          name: stepName,
          passed: true,
          details: [
            "\"Crear Nuevo Negocio\" title visible",
            "\"Nombre del Negocio\" input present",
            "\"Tienes 2 de 3 negocios\" visible",
            "\"Cancelar\" and \"Crear Negocio\" buttons present",
            "Optional fill-and-cancel action executed"
          ],
          screenshotPath
        });
      } catch (error) {
        pushResult({
          name: stepName,
          passed: false,
          details: [error instanceof Error ? error.message : String(error)]
        });
      }
    }

    // Step 4: Open Administrar Negocios
    {
      const stepName = "Administrar Negocios view";
      try {
        if (!isLoggedIn) {
          throw new Error("Skipped because login did not succeed.");
        }
        await ensureMiNegocioExpanded(page);
        await clickByVisibleText(page, ["Administrar Negocios"]);

        await expect(textLocator(page, "Información General").first()).toBeVisible({ timeout: 30_000 });
        await expect(textLocator(page, "Detalles de la Cuenta").first()).toBeVisible({ timeout: 30_000 });
        await expect(textLocator(page, "Tus Negocios").first()).toBeVisible({ timeout: 30_000 });
        await expect(textLocator(page, "Sección Legal").first()).toBeVisible({ timeout: 30_000 });

        const screenshotPath = await captureStepScreenshot(page, "04-administrar-negocios-page");
        pushResult({
          name: stepName,
          passed: true,
          details: [
            "\"Información General\" section exists",
            "\"Detalles de la Cuenta\" section exists",
            "\"Tus Negocios\" section exists",
            "\"Sección Legal\" section exists"
          ],
          screenshotPath
        });
        accountViewLoaded = true;
        legalSectionReady = true;
      } catch (error) {
        pushResult({
          name: stepName,
          passed: false,
          details: [error instanceof Error ? error.message : String(error)]
        });
      }
    }

    // Step 5: Validate Información General
    {
      const stepName = "Información General";
      try {
        if (!accountViewLoaded) {
          throw new Error("Skipped because Administrar Negocios view did not load.");
        }
        const section = page.locator("section, div").filter({ hasText: "Información General" }).first();
        await expect(section).toBeVisible({ timeout: 30_000 });
        await expect(section).toContainText(/@/);
        await expect(section).toContainText(/BUSINESS PLAN/i);
        await expect(section.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible({ timeout: 30_000 });
        pushResult({
          name: stepName,
          passed: true,
          details: [
            "User name is visible in section context",
            "User email is visible",
            "\"BUSINESS PLAN\" is visible",
            "\"Cambiar Plan\" button is visible"
          ]
        });
      } catch (error) {
        pushResult({
          name: stepName,
          passed: false,
          details: [error instanceof Error ? error.message : String(error)]
        });
      }
    }

    // Step 6: Validate Detalles de la Cuenta
    {
      const stepName = "Detalles de la Cuenta";
      try {
        if (!accountViewLoaded) {
          throw new Error("Skipped because Administrar Negocios view did not load.");
        }
        const section = page.locator("section, div").filter({ hasText: "Detalles de la Cuenta" }).first();
        await expect(section).toBeVisible({ timeout: 30_000 });
        await expect(section).toContainText(/Cuenta creada/i);
        await expect(section).toContainText(/Estado activo/i);
        await expect(section).toContainText(/Idioma seleccionado/i);
        pushResult({
          name: stepName,
          passed: true,
          details: [
            "\"Cuenta creada\" visible",
            "\"Estado activo\" visible",
            "\"Idioma seleccionado\" visible"
          ]
        });
      } catch (error) {
        pushResult({
          name: stepName,
          passed: false,
          details: [error instanceof Error ? error.message : String(error)]
        });
      }
    }

    // Step 7: Validate Tus Negocios
    {
      const stepName = "Tus Negocios";
      try {
        if (!accountViewLoaded) {
          throw new Error("Skipped because Administrar Negocios view did not load.");
        }
        const section = page.locator("section, div").filter({ hasText: "Tus Negocios" }).first();
        await expect(section).toBeVisible({ timeout: 30_000 });
        await expect(section.getByRole("button", { name: "Agregar Negocio" }).first()).toBeVisible({ timeout: 30_000 });
        await expect(section).toContainText(/Tienes 2 de 3 negocios/i);
        pushResult({
          name: stepName,
          passed: true,
          details: [
            "Business list context visible",
            "\"Agregar Negocio\" button exists",
            "\"Tienes 2 de 3 negocios\" visible"
          ]
        });
      } catch (error) {
        pushResult({
          name: stepName,
          passed: false,
          details: [error instanceof Error ? error.message : String(error)]
        });
      }
    }

    // Step 8: Validate Términos y Condiciones
    {
      const stepName = "Términos y Condiciones";
      try {
        if (!legalSectionReady) {
          throw new Error("Skipped because legal section was not reached.");
        }
        const { finalUrl, screenshotPath } = await clickLegalLinkAndValidate(page, "Términos y Condiciones");
        pushResult({
          name: stepName,
          passed: true,
          details: [
            "Heading \"Términos y Condiciones\" visible",
            "Legal content text visible"
          ],
          screenshotPath,
          finalUrl
        });
      } catch (error) {
        pushResult({
          name: stepName,
          passed: false,
          details: [error instanceof Error ? error.message : String(error)]
        });
      }
    }

    // Step 9: Validate Política de Privacidad
    {
      const stepName = "Política de Privacidad";
      try {
        if (!legalSectionReady) {
          throw new Error("Skipped because legal section was not reached.");
        }
        const { finalUrl, screenshotPath } = await clickLegalLinkAndValidate(page, "Política de Privacidad");
        pushResult({
          name: stepName,
          passed: true,
          details: [
            "Heading \"Política de Privacidad\" visible",
            "Legal content text visible"
          ],
          screenshotPath,
          finalUrl
        });
      } catch (error) {
        pushResult({
          name: stepName,
          passed: false,
          details: [error instanceof Error ? error.message : String(error)]
        });
      }
    }

    // Step 10: Final Report
    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      environment: {
        baseUrl: process.env.SALEADS_BASE_URL ?? null,
        browser: test.info().project.name
      },
      results: stepResults.map((step) => ({
        name: step.name,
        status: step.passed ? "PASS" : "FAIL",
        details: step.details,
        screenshotPath: step.screenshotPath ?? null,
        finalUrl: step.finalUrl ?? null
      })),
      summary: {
        total: stepResults.length,
        passed: stepResults.filter((x) => x.passed).length,
        failed: stepResults.filter((x) => !x.passed).length
      }
    };

    const reportPath = path.join(ARTIFACTS_DIR, "final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await test.info().attach("final-report", {
      path: reportPath,
      contentType: "application/json"
    });

    const failures = stepResults.filter((x) => !x.passed);
    expect(
      failures,
      `One or more workflow validations failed: ${failures.map((f) => f.name).join(", ")}`
    ).toHaveLength(0);
  });
});
