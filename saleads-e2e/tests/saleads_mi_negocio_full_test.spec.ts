import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

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

type StepStatus = "PASS" | "FAIL";

const APP_BASE_URL = process.env.SALEADS_BASE_URL;
const GOOGLE_EMAIL = process.env.SALEADS_GOOGLE_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const ARTIFACTS_DIR = path.resolve(process.cwd(), "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "saleads_mi_negocio_full_test_report.json");

const report: Record<StepKey, StepStatus> = {
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

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function saveCheckpoint(page: Page, name: string, fullPage = false): Promise<void> {
  const safeName = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
  await fs.promises.mkdir(SCREENSHOTS_DIR, { recursive: true });
  await page.screenshot({
    path: path.join(SCREENSHOTS_DIR, `${safeName}.png`),
    fullPage,
  });
}

function textMatchRegex(text: string): RegExp {
  return new RegExp(`^\\s*${text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*$`, "i");
}

async function clickByText(page: Page, text: string): Promise<void> {
  const exact = textMatchRegex(text);
  const candidates: Locator[] = [
    page.getByRole("button", { name: exact }),
    page.getByRole("link", { name: exact }),
    page.getByRole("menuitem", { name: exact }),
    page.getByRole("tab", { name: exact }),
    page.locator("button", { hasText: exact }),
    page.locator("a", { hasText: exact }),
    page.locator("[role='button']", { hasText: exact }),
    page.getByText(exact),
  ];

  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      await locator.first().click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`No visible clickable element found with text "${text}".`);
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const hasAgregar = await page.getByText(textMatchRegex("Agregar Negocio")).first().isVisible().catch(() => false);
  const hasAdministrar = await page
    .getByText(textMatchRegex("Administrar Negocios"))
    .first()
    .isVisible()
    .catch(() => false);

  if (hasAgregar && hasAdministrar) {
    return;
  }

  await clickByText(page, "Mi Negocio");
  await expect(page.getByText(textMatchRegex("Agregar Negocio")).first()).toBeVisible();
  await expect(page.getByText(textMatchRegex("Administrar Negocios")).first()).toBeVisible();
}

async function openLegalAndValidate(
  page: Page,
  label: "Términos y Condiciones" | "Política de Privacidad",
): Promise<{ url: string }> {
  const candidates: Locator[] = [
    page.getByRole("link", { name: textMatchRegex(label) }),
    page.getByRole("button", { name: textMatchRegex(label) }),
    page.locator("a", { hasText: textMatchRegex(label) }),
    page.locator("button", { hasText: textMatchRegex(label) }),
    page.getByText(textMatchRegex(label)),
  ];

  let legalEntry: Locator | null = null;
  for (const candidate of candidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      legalEntry = candidate.first();
      break;
    }
  }

  if (!legalEntry) {
    throw new Error(`No se encontró el acceso legal "${label}".`);
  }

  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  await legalEntry.click();
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForTimeout(700);
    await expect(popup.getByRole("heading", { name: textMatchRegex(label) }).first()).toBeVisible();
    await expect(popup.locator("body")).toContainText(/\S+/);
    await saveCheckpoint(popup, `legal_${label}`, true);
    const finalUrl = popup.url();
    await popup.close();
    await waitForUi(page);
    return { url: finalUrl };
  }

  await waitForUi(page);
  await expect(page.getByRole("heading", { name: textMatchRegex(label) }).first()).toBeVisible();
  await expect(page.locator("body")).toContainText(/\S+/);
  await saveCheckpoint(page, `legal_${label}`, true);
  const finalUrl = page.url();
  await page.goBack().catch(async () => {
    if (APP_BASE_URL) {
      await page.goto(APP_BASE_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  });
  await waitForUi(page);
  return { url: finalUrl };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login to SaleADS with Google and validate Mi Negocio workflow", async ({ page }) => {
    const legalUrls: { terminosYCondiciones: string | null; politicaDePrivacidad: string | null } = {
      terminosYCondiciones: null,
      politicaDePrivacidad: null,
    };
    let runtimeError: string | null = null;

    if (!APP_BASE_URL) {
      throw new Error(
        "SALEADS_BASE_URL no está configurado. Define esta variable para ejecutar en el entorno actual (dev/staging/prod).",
      );
    }

    try {
      await page.goto(APP_BASE_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);

      // 1) Login with Google
      const googleButtonCandidates = [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.locator("button, a", { hasText: /google/i }),
      ];

      let clickedGoogle = false;
      for (const candidate of googleButtonCandidates) {
        if (await candidate.first().isVisible().catch(() => false)) {
          const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
          await candidate.first().click();
          await waitForUi(page);
          const popup = await popupPromise;
          if (popup) {
            await popup.waitForLoadState("domcontentloaded");
            await popup.waitForTimeout(700);

            const accountOption = popup.getByText(textMatchRegex(GOOGLE_EMAIL)).first();
            if (await accountOption.isVisible().catch(() => false)) {
              await accountOption.click();
            }

            // If still in Google auth, leave popup open briefly for manual completion if needed.
            await popup.waitForTimeout(1500);
          }
          clickedGoogle = true;
          break;
        }
      }

      if (!clickedGoogle) {
        throw new Error("No se encontró un botón/enlace de inicio de sesión con Google.");
      }

      await page.waitForTimeout(2500);
      await waitForUi(page);

      const sidebar = page.locator("nav, aside").first();
      await expect(sidebar).toBeVisible();
      await saveCheckpoint(page, "01_dashboard_loaded", true);
      report["Login"] = "PASS";

      // 2) Open Mi Negocio menu
      await clickByText(page, "Negocio").catch(async () => {
        // Some UIs expose Mi Negocio directly in sidebar.
        await waitForUi(page);
      });
      await clickByText(page, "Mi Negocio");
      await expect(page.getByText(textMatchRegex("Agregar Negocio")).first()).toBeVisible();
      await expect(page.getByText(textMatchRegex("Administrar Negocios")).first()).toBeVisible();
      await saveCheckpoint(page, "02_mi_negocio_menu_expanded", true);
      report["Mi Negocio menu"] = "PASS";

      // 3) Validate Agregar Negocio modal
      await clickByText(page, "Agregar Negocio");
      const modal = page
        .locator("[role='dialog'], .modal, [data-testid*='modal']")
        .filter({ hasText: /crear nuevo negocio/i })
        .first();

      await expect(modal).toBeVisible();
      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
      await expect(modal.getByLabel(/nombre del negocio/i).or(modal.getByPlaceholder(/nombre del negocio/i))).toBeVisible();
      await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();
      await saveCheckpoint(page, "03_agregar_negocio_modal", true);

      const nombreInput = modal.getByLabel(/nombre del negocio/i).or(modal.getByPlaceholder(/nombre del negocio/i));
      if (await nombreInput.first().isVisible().catch(() => false)) {
        await nombreInput.first().click();
        await nombreInput.first().fill("Negocio Prueba Automatización");
      }
      await modal.getByRole("button", { name: /cancelar/i }).click();
      await waitForUi(page);
      report["Agregar Negocio modal"] = "PASS";

      // 4) Open Administrar Negocios
      await ensureMiNegocioExpanded(page);
      await clickByText(page, "Administrar Negocios");
      await expect(page.getByText(textMatchRegex("Información General")).first()).toBeVisible();
      await expect(page.getByText(textMatchRegex("Detalles de la Cuenta")).first()).toBeVisible();
      await expect(page.getByText(textMatchRegex("Tus Negocios")).first()).toBeVisible();
      await expect(page.getByText(textMatchRegex("Sección Legal")).first()).toBeVisible();
      await saveCheckpoint(page, "04_administrar_negocios_view", true);
      report["Administrar Negocios view"] = "PASS";

      // 5) Información General
      await expect(page.getByText(/business plan/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
      await expect(page.getByText(/@/).first()).toBeVisible();
      await expect(page.locator("section, div").filter({ hasText: /información general/i }).first()).toContainText(/\S+/);
      report["Información General"] = "PASS";

      // 6) Detalles de la Cuenta
      await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
      report["Detalles de la Cuenta"] = "PASS";

      // 7) Tus Negocios
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
      report["Tus Negocios"] = "PASS";

      // 8) Términos y Condiciones
      const tyc = await openLegalAndValidate(page, "Términos y Condiciones");
      legalUrls.terminosYCondiciones = tyc.url;
      report["Términos y Condiciones"] = "PASS";

      // 9) Política de Privacidad
      const privacy = await openLegalAndValidate(page, "Política de Privacidad");
      legalUrls.politicaDePrivacidad = privacy.url;
      report["Política de Privacidad"] = "PASS";
    } catch (error) {
      runtimeError = error instanceof Error ? error.message : String(error);
      throw error;
    } finally {
      await fs.promises.mkdir(ARTIFACTS_DIR, { recursive: true });
      await fs.promises.writeFile(
        REPORT_PATH,
        JSON.stringify(
          {
            testName: "saleads_mi_negocio_full_test",
            stepStatus: report,
            legalUrls,
            generatedAt: new Date().toISOString(),
            runtimeError,
          },
          null,
          2,
        ),
        "utf-8",
      );
    }
  });
});
