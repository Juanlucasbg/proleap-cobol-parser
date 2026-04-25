import { test, expect, Page, Locator } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type FinalReport = {
  Login: StepStatus;
  "Mi Negocio menu": StepStatus;
  "Agregar Negocio modal": StepStatus;
  "Administrar Negocios view": StepStatus;
  "Información General": StepStatus;
  "Detalles de la Cuenta": StepStatus;
  "Tus Negocios": StepStatus;
  "Términos y Condiciones": StepStatus;
  "Política de Privacidad": StepStatus;
};

const REPORT_FILE_NAME = "saleads-mi-negocio-final-report.json";
const SCREENSHOT_DIR = "test-results/screenshots";

function createDefaultReport(): FinalReport {
  return {
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
}

async function waitForUiAfterClick(page: Page): Promise<void> {
  await Promise.all([
    page.waitForLoadState("domcontentloaded").catch(() => {}),
    page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {}),
  ]);
}

async function ensureScreenshotDir(): Promise<void> {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

async function firstVisible(candidates: Locator[], timeout = 20_000): Promise<Locator> {
  for (const candidate of candidates) {
    if ((await candidate.count()) === 0) {
      continue;
    }

    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  const fallback = candidates[0].first();
  await expect(fallback).toBeVisible({ timeout });
  return fallback;
}

async function clickLocatorAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUiAfterClick(page);
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const locator = await firstVisible([
    page.getByRole("button", { name: text }),
    page.getByRole("link", { name: text }),
    page.getByText(text, { exact: true }),
  ]);
  await clickLocatorAndWait(page, locator);
}

async function clickByVisibleTextContains(page: Page, text: string): Promise<void> {
  const locator = await firstVisible([
    page.getByRole("button", { name: new RegExp(text, "i") }),
    page.getByRole("link", { name: new RegExp(text, "i") }),
    page.getByText(new RegExp(text, "i")),
  ]);
  await clickLocatorAndWait(page, locator);
}

async function clickByRegex(page: Page, regex: RegExp): Promise<void> {
  const locator = await firstVisible([
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByText(regex),
  ]);
  await clickLocatorAndWait(page, locator);
}

async function ensureOnLoginPage(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    return;
  }

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (!loginUrl) {
    throw new Error(
      "Page started on about:blank. Set SALEADS_LOGIN_URL or provide a preloaded login page.",
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiAfterClick(page);
}

async function takeCheckpoint(page: Page, fileName: string, fullPage = false): Promise<void> {
  await ensureScreenshotDir();
  await page.screenshot({
    path: `${SCREENSHOT_DIR}/${fileName}`,
    fullPage,
  });
}

async function clickLegalLinkAndValidate(
  page: Page,
  linkText: string,
  headingText: string,
  screenshotFile: string,
): Promise<{ passed: boolean; finalUrl: string | null }> {
  const link = page.getByText(linkText).first();
  await expect(link).toBeVisible({ timeout: 20000 });

  const [popup] = await Promise.all([
    page.waitForEvent("popup", { timeout: 4000 }).catch(() => null),
    link.click(),
  ]);

  const targetPage = popup ?? page;
  await targetPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await targetPage.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});

  const heading = targetPage.getByRole("heading", { name: headingText }).first();
  if ((await heading.count()) > 0) {
    await expect(heading).toBeVisible({ timeout: 15000 });
  } else {
    await expect(targetPage.getByText(headingText).first()).toBeVisible({ timeout: 15000 });
  }

  // Generic legal content check: paragraph-like text should exist.
  const contentProbe = targetPage.locator("p, article, main, div").filter({ hasText: /./ }).first();
  await expect(contentProbe).toBeVisible({ timeout: 15000 });

  await takeCheckpoint(targetPage, screenshotFile, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiAfterClick(page);
  }

  return { passed: true, finalUrl };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login and validate Mi Negocio module workflow", async ({ page }, testInfo) => {
    const report = createDefaultReport();
    const issues: string[] = [];
    const legalUrls: Record<string, string | null> = {
      "Términos y Condiciones": null,
      "Política de Privacidad": null,
    };

    try {
      // Step 1: Login with Google.
      try {
        await ensureOnLoginPage(page);

        const loginButton = await firstVisible([
          page.getByRole("button", { name: /google|sign in with google|iniciar sesión con google/i }),
          page.getByRole("link", { name: /google|sign in with google|iniciar sesión con google/i }),
          page.getByText(/google|sign in with google|iniciar sesión con google/i),
        ]);
        const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
        await clickLocatorAndWait(page, loginButton);

        const popup = await popupPromise;
        if (popup) {
          await popup.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
          const accountOption = popup.getByText("juanlucasbarbiergarzon@gmail.com").first();
          if (await accountOption.isVisible().catch(() => false)) {
            await accountOption.click();
          }
          await popup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
          await page.bringToFront();
          await waitForUiAfterClick(page);
        } else {
          const googleAccountOption = page.getByText("juanlucasbarbiergarzon@gmail.com").first();
          if (
            (await googleAccountOption.count()) > 0 &&
            (await googleAccountOption.isVisible().catch(() => false))
          ) {
            await googleAccountOption.click();
            await waitForUiAfterClick(page);
          }
        }

        const sidebar = page.locator("aside, nav").first();
        await expect(sidebar).toBeVisible({ timeout: 45000 });
        report.Login = "PASS";
        await takeCheckpoint(page, "01-dashboard-loaded.png", true);
      } catch (error) {
        report.Login = "FAIL";
        issues.push(`Login: ${error instanceof Error ? error.message : String(error)}`);
      }

      // Step 2: Open Mi Negocio menu under Negocio.
      try {
        const negocioSection = page.getByText("Negocio", { exact: true }).first();
        if (await negocioSection.isVisible().catch(() => false)) {
          await clickLocatorAndWait(page, negocioSection);
        }

        await clickByVisibleTextContains(page, "Mi Negocio");
        await expect(page.getByText("Agregar Negocio").first()).toBeVisible({ timeout: 20000 });
        await expect(page.getByText("Administrar Negocios").first()).toBeVisible({ timeout: 20000 });
        report["Mi Negocio menu"] = "PASS";
        await takeCheckpoint(page, "02-mi-negocio-menu-expanded.png", true);
      } catch (error) {
        report["Mi Negocio menu"] = "FAIL";
        issues.push(`Mi Negocio menu: ${error instanceof Error ? error.message : String(error)}`);
      }

      // Step 3: Validate Agregar Negocio modal.
      try {
        await clickByVisibleTextContains(page, "Agregar Negocio");
        await expect(page.getByText("Crear Nuevo Negocio").first()).toBeVisible({ timeout: 20000 });
        const nombreNegocioInput = await firstVisible([
          page.getByLabel("Nombre del Negocio"),
          page.getByPlaceholder("Nombre del Negocio"),
        ]);
        await expect(nombreNegocioInput).toBeVisible({ timeout: 10000 });
        await expect(page.getByText("Tienes 2 de 3 negocios").first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible({ timeout: 10000 });
        await takeCheckpoint(page, "03-agregar-negocio-modal.png", true);

        await nombreNegocioInput.fill("Negocio Prueba Automatización");
        await clickByVisibleText(page, "Cancelar");
        report["Agregar Negocio modal"] = "PASS";
      } catch (error) {
        report["Agregar Negocio modal"] = "FAIL";
        issues.push(`Agregar Negocio modal: ${error instanceof Error ? error.message : String(error)}`);
      }

      // Step 4: Open Administrar Negocios.
      try {
        const administrarNegocios = page.getByText("Administrar Negocios").first();
        if (!(await administrarNegocios.isVisible().catch(() => false))) {
          await clickByVisibleTextContains(page, "Mi Negocio");
        }
        await clickByVisibleTextContains(page, "Administrar Negocios");

        await expect(page.getByText("Información General").first()).toBeVisible({ timeout: 20000 });
        await expect(page.getByText("Detalles de la Cuenta").first()).toBeVisible({ timeout: 20000 });
        await expect(page.getByText("Tus Negocios").first()).toBeVisible({ timeout: 20000 });
        await expect(page.getByText("Sección Legal").first()).toBeVisible({ timeout: 20000 });
        report["Administrar Negocios view"] = "PASS";
        await takeCheckpoint(page, "04-administrar-negocios-view.png", true);
      } catch (error) {
        report["Administrar Negocios view"] = "FAIL";
        issues.push(`Administrar Negocios view: ${error instanceof Error ? error.message : String(error)}`);
      }

      // Step 5: Validate Información General.
      try {
        const infoSection = page.getByText("Información General").first().locator("xpath=ancestor::*[1]");
        const email = await firstVisible([
          infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
          page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
        ]);
        await expect(email).toBeVisible({ timeout: 20000 });

        const nameCandidate = await firstVisible([
          infoSection.locator("h1, h2, h3, h4, p, span, strong").filter({
            hasNotText: /@|BUSINESS PLAN|Cambiar Plan|Información General/i,
          }),
          page.locator("h1, h2, h3, h4").filter({ hasNotText: /Mi Negocio|Negocio/i }),
        ]);
        await expect(nameCandidate).toBeVisible({ timeout: 15000 });
        await expect(page.getByText("BUSINESS PLAN").first()).toBeVisible({ timeout: 15000 });
        await expect(page.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible({ timeout: 15000 });
        report["Información General"] = "PASS";
      } catch (error) {
        report["Información General"] = "FAIL";
        issues.push(`Información General: ${error instanceof Error ? error.message : String(error)}`);
      }

      // Step 6: Validate Detalles de la Cuenta.
      try {
        await expect(page.getByText("Cuenta creada").first()).toBeVisible({ timeout: 15000 });
        await expect(page.getByText("Estado activo").first()).toBeVisible({ timeout: 15000 });
        await expect(page.getByText("Idioma seleccionado").first()).toBeVisible({ timeout: 15000 });
        report["Detalles de la Cuenta"] = "PASS";
      } catch (error) {
        report["Detalles de la Cuenta"] = "FAIL";
        issues.push(`Detalles de la Cuenta: ${error instanceof Error ? error.message : String(error)}`);
      }

      // Step 7: Validate Tus Negocios.
      try {
        await expect(page.getByText("Tus Negocios").first()).toBeVisible({ timeout: 15000 });
        await expect(page.getByText("Agregar Negocio").first()).toBeVisible({ timeout: 15000 });
        await expect(page.getByText("Tienes 2 de 3 negocios").first()).toBeVisible({ timeout: 15000 });
        const businessListCandidate = await firstVisible([
          page.locator("[data-testid*='business'], [class*='business']").first(),
          page.locator("li, tr, [role='row']").first(),
          page.getByText("Tus Negocios").first(),
        ]);
        await expect(businessListCandidate).toBeVisible({ timeout: 15000 });
        report["Tus Negocios"] = "PASS";
      } catch (error) {
        report["Tus Negocios"] = "FAIL";
        issues.push(`Tus Negocios: ${error instanceof Error ? error.message : String(error)}`);
      }

      // Step 8: Validate Términos y Condiciones.
      try {
        const termsResult = await clickLegalLinkAndValidate(
          page,
          "Términos y Condiciones",
          "Términos y Condiciones",
          "05-terminos-y-condiciones.png",
        );
        report["Términos y Condiciones"] = termsResult.passed ? "PASS" : "FAIL";
        legalUrls["Términos y Condiciones"] = termsResult.finalUrl;
      } catch (error) {
        report["Términos y Condiciones"] = "FAIL";
        issues.push(`Términos y Condiciones: ${error instanceof Error ? error.message : String(error)}`);
      }

      // Step 9: Validate Política de Privacidad.
      try {
        const privacyResult = await clickLegalLinkAndValidate(
          page,
          "Política de Privacidad",
          "Política de Privacidad",
          "06-politica-de-privacidad.png",
        );
        report["Política de Privacidad"] = privacyResult.passed ? "PASS" : "FAIL";
        legalUrls["Política de Privacidad"] = privacyResult.finalUrl;
      } catch (error) {
        report["Política de Privacidad"] = "FAIL";
        issues.push(`Política de Privacidad: ${error instanceof Error ? error.message : String(error)}`);
      }
    } finally {
      // Step 10: Final report.
      const reportPayload = {
        report,
        legalUrls,
        issues,
        generatedAt: new Date().toISOString(),
        testName: "saleads_mi_negocio_full_test",
      };

      const reportPath = testInfo.outputPath(REPORT_FILE_NAME);
      fs.mkdirSync(path.dirname(reportPath), { recursive: true });
      fs.writeFileSync(reportPath, JSON.stringify(reportPayload, null, 2), "utf8");
      await testInfo.attach("final-report", {
        path: reportPath,
        contentType: "application/json",
      });
    }

    expect(issues, `Workflow failed with ${issues.length} issue(s):\n${issues.join("\n")}`).toEqual([]);
  });
});
