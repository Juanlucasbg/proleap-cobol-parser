import { expect, test, type Locator, type Page } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  label: string;
  status: StepStatus;
  details: string[];
};

const reportItems: StepResult[] = [];
const screenshotsDir = join(process.cwd(), "artifacts", "screenshots");
const reportDir = join(process.cwd(), "artifacts");
const googleAccountEmail = "juanlucasbarbiergarzon@gmail.com";

function slug(input: string): string {
  return input
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
}

async function checkpoint(page: Page, name: string, fullPage = false): Promise<string> {
  mkdirSync(screenshotsDir, { recursive: true });
  const filePath = join(screenshotsDir, `${new Date().toISOString().replace(/[:.]/g, "-")}-${slug(name)}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function remember(label: string, status: StepStatus, details: string[]): void {
  reportItems.push({ label, status, details });
}

function getByTextAlternatives(page: Page, values: string[]): Locator {
  return page.getByText(new RegExp(values.map((value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|"), "i")).first();
}

async function findVisibleText(page: Page, texts: string[]): Promise<Locator> {
  for (const text of texts) {
    const candidate = page.getByText(text, { exact: false }).first();
    if (await candidate.isVisible({ timeout: 3_000 }).catch(() => false)) {
      return candidate;
    }
  }

  const alternatives = getByTextAlternatives(page, texts);
  await expect(alternatives).toBeVisible({ timeout: 30_000 });
  return alternatives;
}

async function clickVisibleText(page: Page, texts: string[]): Promise<void> {
  const locator = await findVisibleText(page, texts);
  await locator.click();
  await waitForUi(page);
}

async function optionallyPickGoogleAccount(page: Page): Promise<void> {
  const accountOption = page.getByText(googleAccountEmail).first();
  if (await accountOption.isVisible({ timeout: 10_000 }).catch(() => false)) {
    await accountOption.click();
    await waitForUi(page);
  }
}

async function ensureSidebarVisible(page: Page): Promise<void> {
  const sidebar = page.locator("aside, nav").filter({ hasText: /Negocio|Dashboard|Mi Negocio/i }).first();
  await expect(sidebar).toBeVisible({ timeout: 60_000 });
}

async function ensureExpandedMiNegocio(page: Page): Promise<void> {
  const adminItem = getByTextAlternatives(page, ["Administrar Negocios"]);
  if (await adminItem.isVisible().catch(() => false)) {
    return;
  }

  await clickVisibleText(page, ["Mi Negocio"]);
  await expect(getByTextAlternatives(page, ["Agregar Negocio"])).toBeVisible({ timeout: 20_000 });
  await expect(getByTextAlternatives(page, ["Administrar Negocios"])).toBeVisible({ timeout: 20_000 });
}

async function openLegalLinkFromSection(
  page: Page,
  label: string,
  headingRegex: RegExp
): Promise<{ url: string; screenshot: string }> {
  const legalAnchor = page.getByRole("link", { name: new RegExp(label, "i") }).first();
  await expect(legalAnchor).toBeVisible({ timeout: 25_000 });

  const opensNewTab = (await legalAnchor.getAttribute("target")) === "_blank";
  let newTab: Page | null = null;

  if (opensNewTab) {
    [newTab] = await Promise.all([
      page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null),
      legalAnchor.click()
    ]);
  } else {
    await legalAnchor.click();
  }

  if (newTab) {
    await newTab.waitForLoadState("domcontentloaded");
    await newTab.waitForLoadState("networkidle");
    await expect(newTab.getByRole("heading", { name: headingRegex }).first()).toBeVisible({ timeout: 30_000 });
    await expect(newTab.locator("body")).toContainText(/\S.{40,}/);
    const shot = await checkpoint(newTab, label, true);
    const url = newTab.url();
    await newTab.close();
    await page.bringToFront();
    await waitForUi(page);
    return { url, screenshot: shot };
  }

  await waitForUi(page);
  await expect(page.getByRole("heading", { name: headingRegex }).first()).toBeVisible({ timeout: 30_000 });
  await expect(page.locator("body")).toContainText(/\S.{40,}/);
  const shot = await checkpoint(page, label, true);
  const url = page.url();
  await page.goBack().catch(() => undefined);
  await waitForUi(page);
  return { url, screenshot: shot };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }) => {
    reportItems.length = 0;
    const details: string[] = [];
    mkdirSync(reportDir, { recursive: true });

    // Step 1 - Login with Google
    try {
      await waitForUi(page);
      if (!(await page.locator("aside, nav").filter({ hasText: /Negocio|Dashboard|Mi Negocio/i }).first().isVisible().catch(() => false))) {
        await clickVisibleText(page, ["Sign in with Google", "Continuar con Google", "Iniciar sesión con Google"]);
        await optionallyPickGoogleAccount(page);
      }
      await ensureSidebarVisible(page);
      const shot = await checkpoint(page, "dashboard-loaded");
      details.push(`Dashboard screenshot: ${shot}`);
      remember("Login", "PASS", ["Main interface visible", "Sidebar visible"]);
    } catch (error) {
      remember("Login", "FAIL", [String(error)]);
      throw error;
    }

    // Step 2 - Open Mi Negocio menu
    try {
      await clickVisibleText(page, ["Negocio"]);
      await clickVisibleText(page, ["Mi Negocio"]);
      await expect(getByTextAlternatives(page, ["Agregar Negocio"])).toBeVisible({ timeout: 20_000 });
      await expect(getByTextAlternatives(page, ["Administrar Negocios"])).toBeVisible({ timeout: 20_000 });
      const shot = await checkpoint(page, "mi-negocio-menu-expanded");
      details.push(`Expanded menu screenshot: ${shot}`);
      remember("Mi Negocio menu", "PASS", [
        "Submenu expanded",
        "Agregar Negocio visible",
        "Administrar Negocios visible"
      ]);
    } catch (error) {
      remember("Mi Negocio menu", "FAIL", [String(error)]);
      throw error;
    }

    // Step 3 - Validate Agregar Negocio modal
    try {
      await clickVisibleText(page, ["Agregar Negocio"]);
      await expect(getByTextAlternatives(page, ["Crear Nuevo Negocio"])).toBeVisible({ timeout: 20_000 });
      await expect(page.getByLabel("Nombre del Negocio").or(page.getByPlaceholder("Nombre del Negocio"))).toBeVisible({
        timeout: 20_000
      });
      await expect(getByTextAlternatives(page, ["Tienes 2 de 3 negocios"])).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole("button", { name: "Crear Negocio" })).toBeVisible({ timeout: 20_000 });

      const nameField = page
        .getByLabel("Nombre del Negocio")
        .or(page.getByPlaceholder("Nombre del Negocio"))
        .first();
      await nameField.click();
      await nameField.fill("Negocio Prueba Automatización");

      const shot = await checkpoint(page, "agregar-negocio-modal");
      details.push(`Modal screenshot: ${shot}`);
      await page.getByRole("button", { name: "Cancelar" }).click();
      await waitForUi(page);

      remember("Agregar Negocio modal", "PASS", [
        "Modal title visible",
        "Nombre del Negocio field visible",
        "Business quota text visible",
        "Cancelar and Crear Negocio buttons visible"
      ]);
    } catch (error) {
      remember("Agregar Negocio modal", "FAIL", [String(error)]);
      throw error;
    }

    // Step 4 - Open Administrar Negocios
    try {
      await ensureExpandedMiNegocio(page);
      await clickVisibleText(page, ["Administrar Negocios"]);
      await expect(getByTextAlternatives(page, ["Información General"])).toBeVisible({ timeout: 30_000 });
      await expect(getByTextAlternatives(page, ["Detalles de la Cuenta"])).toBeVisible({ timeout: 30_000 });
      await expect(getByTextAlternatives(page, ["Tus Negocios"])).toBeVisible({ timeout: 30_000 });
      await expect(getByTextAlternatives(page, ["Sección Legal", "Seccion Legal"])).toBeVisible({ timeout: 30_000 });
      const shot = await checkpoint(page, "administrar-negocios-account-page", true);
      details.push(`Account page screenshot: ${shot}`);
      remember("Administrar Negocios view", "PASS", [
        "Información General visible",
        "Detalles de la Cuenta visible",
        "Tus Negocios visible",
        "Sección Legal visible"
      ]);
    } catch (error) {
      remember("Administrar Negocios view", "FAIL", [String(error)]);
      throw error;
    }

    // Step 5 - Validate Información General
    try {
      await expect(page.locator("body")).toContainText(/BUSINESS PLAN/i);
      await expect(getByTextAlternatives(page, ["Cambiar Plan"])).toBeVisible({ timeout: 20_000 });
      await expect(page.locator("body")).toContainText(/@[a-z0-9.-]+\.[a-z]{2,}/i);
      remember("Información General", "PASS", [
        "Name/email present",
        "BUSINESS PLAN visible",
        "Cambiar Plan visible"
      ]);
    } catch (error) {
      remember("Información General", "FAIL", [String(error)]);
      throw error;
    }

    // Step 6 - Validate Detalles de la Cuenta
    try {
      await expect(getByTextAlternatives(page, ["Cuenta creada"])).toBeVisible({ timeout: 20_000 });
      await expect(getByTextAlternatives(page, ["Estado activo"])).toBeVisible({ timeout: 20_000 });
      await expect(getByTextAlternatives(page, ["Idioma seleccionado"])).toBeVisible({ timeout: 20_000 });
      remember("Detalles de la Cuenta", "PASS", [
        "Cuenta creada visible",
        "Estado activo visible",
        "Idioma seleccionado visible"
      ]);
    } catch (error) {
      remember("Detalles de la Cuenta", "FAIL", [String(error)]);
      throw error;
    }

    // Step 7 - Validate Tus Negocios
    try {
      await expect(getByTextAlternatives(page, ["Tus Negocios"])).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible({ timeout: 20_000 });
      await expect(getByTextAlternatives(page, ["Tienes 2 de 3 negocios"])).toBeVisible({ timeout: 20_000 });
      remember("Tus Negocios", "PASS", [
        "Business list section visible",
        "Agregar Negocio button visible",
        "Quota text visible"
      ]);
    } catch (error) {
      remember("Tus Negocios", "FAIL", [String(error)]);
      throw error;
    }

    // Step 8 - Validate Términos y Condiciones
    try {
      const legal = await openLegalLinkFromSection(page, "Términos y Condiciones", /Términos y Condiciones/i);
      details.push(`Términos screenshot: ${legal.screenshot}`);
      details.push(`Términos URL: ${legal.url}`);
      remember("Términos y Condiciones", "PASS", [
        "Heading visible",
        "Legal content visible",
        `Final URL: ${legal.url}`
      ]);
    } catch (error) {
      remember("Términos y Condiciones", "FAIL", [String(error)]);
      throw error;
    }

    // Step 9 - Validate Política de Privacidad
    try {
      const legal = await openLegalLinkFromSection(page, "Política de Privacidad", /Política de Privacidad/i);
      details.push(`Privacidad screenshot: ${legal.screenshot}`);
      details.push(`Privacidad URL: ${legal.url}`);
      remember("Política de Privacidad", "PASS", [
        "Heading visible",
        "Legal content visible",
        `Final URL: ${legal.url}`
      ]);
    } catch (error) {
      remember("Política de Privacidad", "FAIL", [String(error)]);
      throw error;
    } finally {
      const reportPath = join(reportDir, "saleads-mi-negocio-report.json");
      writeFileSync(
        reportPath,
        JSON.stringify(
          {
            testName: "saleads_mi_negocio_full_test",
            generatedAt: new Date().toISOString(),
            results: reportItems,
            evidence: details
          },
          null,
          2
        )
      );
    }

    // Step 10 - enforce all pass in one place
    const failed = reportItems.filter((item) => item.status === "FAIL");
    expect(
      failed,
      `One or more workflow validations failed. See artifacts/saleads-mi-negocio-report.json`
    ).toHaveLength(0);
  });
});
