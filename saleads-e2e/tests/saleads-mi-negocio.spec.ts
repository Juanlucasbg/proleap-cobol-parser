import { expect, type Locator, type Page, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";

type StepKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type ReportStatus = "PASS" | "FAIL";

type StepResult = {
  status: ReportStatus;
  details: string;
};

const reportOrder: StepKey[] = [
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

const APP_URL = process.env.SALEADS_URL;
const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const ARTIFACT_DIR = path.resolve(process.cwd(), "artifacts");

function initResults(): Record<StepKey, StepResult> {
  return {
    Login: { status: "FAIL", details: "Not executed." },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed." },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed." },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed." },
    "Información General": { status: "FAIL", details: "Not executed." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed." },
    "Tus Negocios": { status: "FAIL", details: "Not executed." },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed." },
    "Política de Privacidad": { status: "FAIL", details: "Not executed." },
  };
}

async function waitAfterAction(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
}

async function safeClick(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded();
  await locator.click();
  await waitAfterAction(page);
}

async function chooseFirstVisible(page: Page, locators: Locator[]): Promise<Locator> {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }

  throw new Error("No visible locator found for requested element set.");
}

async function captureScreenshot(page: Page, fileName: string, fullPage = false): Promise<string> {
  await fs.promises.mkdir(ARTIFACT_DIR, { recursive: true });
  const fullPath = path.join(ARTIFACT_DIR, fileName);
  await page.screenshot({ path: fullPath, fullPage });
  return fullPath;
}

async function clickLegalLinkAndValidate(
  page: Page,
  linkLabel: string,
  headingText: string,
  screenshotName: string,
): Promise<{ screenshotPath: string; finalUrl: string }> {
  const originalPage = page;
  const appUrlBeforeClick = originalPage.url();
  const link = await chooseFirstVisible(originalPage, [
    originalPage.getByRole("link", { name: linkLabel, exact: true }),
    originalPage.getByText(linkLabel, { exact: true }),
    originalPage.getByRole("button", { name: linkLabel, exact: true }),
  ]);

  const popupPromise = originalPage.context().waitForEvent("page", { timeout: 5000 }).catch(() => null);
  await safeClick(originalPage, link);
  const popup = await popupPromise;
  const targetPage = popup ?? originalPage;

  await waitAfterAction(targetPage);
  await expect(targetPage.getByRole("heading", { name: headingText })).toBeVisible();
  await expect(targetPage.locator("body")).toContainText(headingText);

  const screenshotPath = await captureScreenshot(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await originalPage.bringToFront();
  } else {
    await originalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitAfterAction(originalPage);

    // If goBack did not restore app content, fall back to the URL before legal navigation.
    if (originalPage.url() === finalUrl) {
      await originalPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
      await waitAfterAction(originalPage);
    }
  }

  return { screenshotPath, finalUrl };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login and validate full Mi Negocio workflow", async ({ page }, testInfo) => {
    if (!APP_URL) {
      throw new Error("SALEADS_URL is required. Example: SALEADS_URL='https://<env-host>/login' npm run test:mi-negocio");
    }

    const results = initResults();
    const evidence: Record<string, string> = {};
    const legalUrls: Record<string, string> = {};

    try {
      // Step 1: Login with Google
      await page.goto(APP_URL, { waitUntil: "domcontentloaded" });
      await waitAfterAction(page);

      const sidebarVisible = await page.locator("aside").isVisible().catch(() => false);
      const negocioVisible = await page.getByText("Negocio", { exact: true }).isVisible().catch(() => false);

      if (!(sidebarVisible && negocioVisible)) {
        const signInWithGoogle = await chooseFirstVisible(page, [
          page.getByRole("button", { name: /sign in with google/i }),
          page.getByText(/sign in with google/i),
          page.getByRole("button", { name: /google/i }),
        ]);

        const popupPromise = page.context().waitForEvent("page", { timeout: 5000 }).catch(() => null);
        await safeClick(page, signInWithGoogle);

        // Account picker can appear in same tab or popup
        const maybePopup = await popupPromise;
        const authPage = maybePopup ?? page;
        await waitAfterAction(authPage);

        const accountChoice = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
        if (await accountChoice.isVisible().catch(() => false)) {
          await accountChoice.click();
          await waitAfterAction(authPage);
        }

        if (maybePopup) {
          await maybePopup.waitForClose({ timeout: 60000 }).catch(() => undefined);
          await page.bringToFront();
        }
      }

      await expect(page.locator("aside")).toBeVisible();
      await expect(page.locator("aside")).toContainText(/negocio/i);
      evidence["dashboard_screenshot"] = await captureScreenshot(page, "01-dashboard-loaded.png", true);
      results.Login = { status: "PASS", details: "Main app interface and left sidebar are visible after Google login." };

      // Step 2: Open Mi Negocio menu
      const miNegocioMenu = await chooseFirstVisible(page, [
        page.getByRole("button", { name: "Mi Negocio", exact: true }),
        page.getByRole("link", { name: "Mi Negocio", exact: true }),
        page.getByText("Mi Negocio", { exact: true }),
      ]);
      await safeClick(page, miNegocioMenu);

      await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
      await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();
      evidence["mi_negocio_menu_screenshot"] = await captureScreenshot(page, "02-mi-negocio-menu-expanded.png", true);
      results["Mi Negocio menu"] = { status: "PASS", details: "Mi Negocio submenu expanded and required options are visible." };

      // Step 3: Validate Agregar Negocio modal
      await safeClick(page, page.getByText("Agregar Negocio", { exact: true }));
      await expect(page.getByRole("heading", { name: "Crear Nuevo Negocio", exact: true })).toBeVisible();
      await expect(page.getByLabel("Nombre del Negocio")).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
      await expect(page.getByRole("button", { name: "Cancelar", exact: true })).toBeVisible();
      await expect(page.getByRole("button", { name: "Crear Negocio", exact: true })).toBeVisible();
      evidence["agregar_negocio_modal_screenshot"] = await captureScreenshot(page, "03-agregar-negocio-modal.png", true);

      const negocioInput = page.getByLabel("Nombre del Negocio");
      await negocioInput.click();
      await negocioInput.fill("Negocio Prueba Automatización");
      await safeClick(page, page.getByRole("button", { name: "Cancelar", exact: true }));
      results["Agregar Negocio modal"] = { status: "PASS", details: "Modal fields/buttons validated and modal closed using Cancelar." };

      // Step 4: Open Administrar Negocios
      const administrarNegocios = page.getByText("Administrar Negocios", { exact: true });
      if (!(await administrarNegocios.isVisible().catch(() => false))) {
        await safeClick(page, miNegocioMenu);
      }
      await safeClick(page, page.getByText("Administrar Negocios", { exact: true }));
      await expect(page.getByText("Información General", { exact: true })).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
      await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible();
      evidence["administrar_negocios_full_screenshot"] = await captureScreenshot(page, "04-administrar-negocios-page.png", true);
      results["Administrar Negocios view"] = { status: "PASS", details: "All required sections are visible in Administrar Negocios." };

      // Step 5: Validate Información General
      const infoGeneralSection = page.locator("section,div").filter({ hasText: "Información General" }).first();
      await expect(infoGeneralSection).toContainText(/@/);
      await expect(infoGeneralSection).toContainText(/BUSINESS PLAN/i);
      await expect(infoGeneralSection.getByRole("button", { name: "Cambiar Plan", exact: true })).toBeVisible();
      results["Información General"] = { status: "PASS", details: "User identity, plan, and Cambiar Plan button validated." };

      // Step 6: Validate Detalles de la Cuenta
      const detallesCuentaSection = page.locator("section,div").filter({ hasText: "Detalles de la Cuenta" }).first();
      await expect(detallesCuentaSection).toContainText("Cuenta creada");
      await expect(detallesCuentaSection).toContainText(/Estado activo/i);
      await expect(detallesCuentaSection).toContainText("Idioma seleccionado");
      results["Detalles de la Cuenta"] = { status: "PASS", details: "Account detail labels and active state validated." };

      // Step 7: Validate Tus Negocios
      const tusNegociosSection = page.locator("section,div").filter({ hasText: "Tus Negocios" }).first();
      await expect(tusNegociosSection).toBeVisible();
      await expect(tusNegociosSection.getByRole("button", { name: "Agregar Negocio", exact: true })).toBeVisible();
      await expect(tusNegociosSection).toContainText("Tienes 2 de 3 negocios");
      results["Tus Negocios"] = { status: "PASS", details: "Business list area, add button, and quota text validated." };

      // Step 8: Validate Términos y Condiciones
      const termsResult = await clickLegalLinkAndValidate(
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "05-terminos-y-condiciones.png",
      );
      evidence["terminos_screenshot"] = termsResult.screenshotPath;
      legalUrls["terminos_final_url"] = termsResult.finalUrl;
      results["Términos y Condiciones"] = { status: "PASS", details: "Legal page heading/content validated and URL captured." };

      // Step 9: Validate Política de Privacidad
      const privacyResult = await clickLegalLinkAndValidate(
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "06-politica-de-privacidad.png",
      );
      evidence["politica_screenshot"] = privacyResult.screenshotPath;
      legalUrls["politica_final_url"] = privacyResult.finalUrl;
      results["Política de Privacidad"] = { status: "PASS", details: "Privacy page heading/content validated and URL captured." };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      testInfo.attach("workflow-error", {
        body: Buffer.from(message, "utf-8"),
        contentType: "text/plain",
      });
      throw error;
    } finally {
      await fs.promises.mkdir(ARTIFACT_DIR, { recursive: true });
      const finalReport = {
        testName: "saleads_mi_negocio_full_test",
        environment: {
          saleadsUrl: APP_URL ?? null,
          googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
        },
        report: reportOrder.map((name) => ({
          field: name,
          status: results[name].status,
          details: results[name].details,
        })),
        evidence,
        legalUrls,
      };
      const reportPath = path.join(ARTIFACT_DIR, "saleads-mi-negocio-final-report.json");
      await fs.promises.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
      testInfo.attach("final-report-json", {
        body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
        contentType: "application/json",
      });
    }
  });
});
