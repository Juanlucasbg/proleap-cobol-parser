import { expect, type Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details?: string;
  url?: string;
};

type Report = Record<string, StepResult>;

const STEP_NAMES = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
] as const;

const GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_NAME = "Negocio Prueba Automatización";

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => null);
  await page.waitForTimeout(400);
}

function normalizeError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function writeReport(testOutputDir: string, report: Report): Promise<void> {
  const reportPath = path.join(testOutputDir, "saleads-mi-negocio-report.json");
  await fs.mkdir(testOutputDir, { recursive: true });
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf-8");
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
  const report: Report = Object.fromEntries(
    STEP_NAMES.map((name) => [name, { status: "FAIL", details: "Not executed" }])
  );

  const checkpoint = async (name: string, fullPage = false): Promise<void> => {
    await page.screenshot({
      path: testInfo.outputPath(name),
      fullPage
    });
  };

  const runStep = async (
    stepName: (typeof STEP_NAMES)[number],
    action: () => Promise<void>
  ): Promise<void> => {
    try {
      await action();
      report[stepName] = { status: "PASS" };
    } catch (error) {
      report[stepName] = {
        status: "FAIL",
        details: normalizeError(error)
      };
    }
  };

  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL ||
    process.env.SALEADS_URL;

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  } else {
    throw new Error(
      "Missing login page URL. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL or BASE_URL."
    );
  }

  await waitForUi(page);

  await runStep("Login", async () => {
    const googleButton = page
      .locator("button, a")
      .filter({ hasText: /google|sign in|iniciar sesión|continuar/i })
      .first();

    await expect(googleButton).toBeVisible();

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await googleButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    const googleAuthPage = popup ?? page;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
    }

    const accountOption = googleAuthPage
      .locator("div, span, li")
      .filter({ hasText: GOOGLE_EMAIL })
      .first();

    if (await accountOption.isVisible({ timeout: 10000 }).catch(() => false)) {
      await accountOption.click();
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 45000 }).catch(() => null);
    }

    await waitForUi(page);

    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(page.getByText(/Mi Negocio|Negocio/i).first()).toBeVisible();

    await checkpoint("01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocio = page.getByText("Negocio", { exact: true }).first();
    await expect(negocio).toBeVisible();
    await negocio.click();
    await waitForUi(page);

    const miNegocio = page.getByText("Mi Negocio", { exact: true }).first();
    await expect(miNegocio).toBeVisible();
    await miNegocio.click();
    await waitForUi(page);

    await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: true }).first()).toBeVisible();

    await checkpoint("02-mi-negocio-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = page.getByText("Agregar Negocio", { exact: true }).first();
    await expect(agregarNegocio).toBeVisible();
    await agregarNegocio.click();
    await waitForUi(page);

    await expect(page.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
    const nombreInput = page.locator('input[placeholder*="Nombre"], input[name*="nombre" i]').first();
    await expect(nombreInput).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancelar", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Crear Negocio", exact: true })).toBeVisible();

    await checkpoint("03-agregar-negocio-modal.png");

    await nombreInput.click();
    await nombreInput.fill(BUSINESS_NAME);
    await page.getByRole("button", { name: "Cancelar", exact: true }).click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocio = page.getByText("Mi Negocio", { exact: true }).first();
    if (await miNegocio.isVisible().catch(() => false)) {
      await miNegocio.click();
      await waitForUi(page);
    }

    const administrar = page.getByText("Administrar Negocios", { exact: true }).first();
    await expect(administrar).toBeVisible();
    await administrar.click();
    await waitForUi(page);

    await expect(page.getByText("Información General", { exact: true })).toBeVisible();
    await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
    await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible();

    await checkpoint("04-administrar-negocios-full.png", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText("BUSINESS PLAN", { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cambiar Plan", exact: true })).toBeVisible();
    await expect(page.locator("body")).toContainText(/@/);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible();
    await expect(page.getByText("Estado activo", { exact: false })).toBeVisible();
    await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Agregar Negocio", exact: true })).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
  });

  const validateLegalDocument = async (
    reportKey: "Términos y Condiciones" | "Política de Privacidad",
    linkText: string
  ): Promise<void> => {
    try {
      const legalLink = page.getByRole("link", { name: linkText, exact: true }).first();
      await expect(legalLink).toBeVisible();

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      const navigationPromise = page
        .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 10000 })
        .catch(() => null);

      await legalLink.click();
      await waitForUi(page);

      const popup = await popupPromise;
      const targetPage = popup ?? page;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
      } else {
        await navigationPromise;
        await waitForUi(page);
      }

      await expect(targetPage.getByRole("heading", { name: new RegExp(linkText, "i") })).toBeVisible();
      await expect(targetPage.locator("main, article, body").first()).toContainText(/\w{20,}/);

      const screenshotPath = reportKey === "Términos y Condiciones" ? "05-terminos.png" : "06-politica.png";

      await targetPage.screenshot({ path: testInfo.outputPath(screenshotPath), fullPage: true });

      report[reportKey] = {
        status: "PASS",
        url: targetPage.url()
      };

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
        await waitForUi(page);
      }
    } catch (error) {
      report[reportKey] = {
        status: "FAIL",
        details: normalizeError(error)
      };
    }
  };

  await validateLegalDocument("Términos y Condiciones", "Términos y Condiciones");
  await validateLegalDocument("Política de Privacidad", "Política de Privacidad");

  await writeReport(testInfo.outputDir, report);

  const failedSteps = Object.entries(report)
    .filter(([, result]) => result.status === "FAIL")
    .map(([step]) => step);

  console.log("saleads_mi_negocio_full_test report", report);
  expect(
    failedSteps,
    `The following workflow validations failed: ${failedSteps.join(", ")}`
  ).toEqual([]);
});
