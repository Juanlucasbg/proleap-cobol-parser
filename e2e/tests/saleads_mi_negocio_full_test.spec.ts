import fs from "node:fs";
import { expect, Locator, Page, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepReport = Record<ReportKey, StepStatus>;

const PRINTABLE_REPORT_ORDER: ReportKey[] = [
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

function createDefaultReport(): StepReport {
  return {
    "Login": "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
}

function byTextPattern(text: string): RegExp {
  return new RegExp(text, "i");
}

async function waitAfterClick(page: Page): Promise<void> {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 10_000 }),
    page.waitForTimeout(1_500)
  ]);
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator.isVisible().catch(() => false);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const first = candidate.first();
    if (await isVisible(first)) {
      return first;
    }
  }

  return null;
}

async function clickFirstVisible(page: Page, candidates: Locator[]): Promise<boolean> {
  const locator = await firstVisible(candidates);
  if (!locator) {
    return false;
  }

  await locator.click();
  await waitAfterClick(page);
  return true;
}

async function ensureStartPage(page: Page): Promise<void> {
  if (page.url() === "about:blank") {
    const startUrl = process.env.SALEADS_START_URL;
    if (!startUrl) {
      throw new Error(
        "Browser started on about:blank. Set SALEADS_START_URL or open the SaleADS login page before running this test."
      );
    }

    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitAfterClick(page);
  }
}

async function ensureSidebarVisible(page: Page): Promise<void> {
  const found = await firstVisible([
    page.getByText(byTextPattern("Mi Negocio")),
    page.getByText(byTextPattern("Negocio")),
    page.locator("nav"),
    page.locator("aside")
  ]);

  expect(found).not.toBeNull();
}

async function openMiNegocio(page: Page): Promise<void> {
  const clicked = await clickFirstVisible(page, [
    page.getByRole("button", { name: byTextPattern("Mi Negocio") }),
    page.getByRole("link", { name: byTextPattern("Mi Negocio") }),
    page.getByText(byTextPattern("Mi Negocio")),
    page.getByRole("button", { name: byTextPattern("Negocio") }),
    page.getByRole("link", { name: byTextPattern("Negocio") }),
    page.getByText(byTextPattern("Negocio"))
  ]);

  expect(clicked, "Mi Negocio menu was not found").toBeTruthy();
}

async function ensureSubmenuVisible(page: Page): Promise<void> {
  const agregar = page.getByText(byTextPattern("Agregar Negocio")).first();
  const administrar = page.getByText(byTextPattern("Administrar Negocios")).first();

  if (!(await isVisible(agregar)) || !(await isVisible(administrar))) {
    await openMiNegocio(page);
  }

  await expect(agregar).toBeVisible();
  await expect(administrar).toBeVisible();
}

async function clickActionByText(page: Page, actionText: string): Promise<void> {
  const clicked = await clickFirstVisible(page, [
    page.getByRole("button", { name: byTextPattern(actionText) }),
    page.getByRole("link", { name: byTextPattern(actionText) }),
    page.getByText(byTextPattern(actionText))
  ]);
  expect(clicked, `Could not click action: ${actionText}`).toBeTruthy();
}

async function validateLegalLink(
  page: Page,
  label: string,
  heading: string,
  screenshotName: string
): Promise<string> {
  const sourceUrl = page.url();
  const popupPromise = page.waitForEvent("popup", { timeout: 4_000 }).catch(() => null);
  await clickActionByText(page, label);
  const popup = await popupPromise;

  const targetPage = popup ?? page;
  await targetPage.waitForLoadState("domcontentloaded");
  await Promise.race([
    targetPage.waitForLoadState("networkidle", { timeout: 10_000 }),
    targetPage.waitForTimeout(1_500)
  ]);

  await expect(targetPage.getByText(byTextPattern(heading)).first()).toBeVisible();
  await expect(targetPage.locator("body")).toContainText(/\S.{40,}/, { timeout: 10_000 });

  await targetPage.screenshot({
    path: `screenshots/${screenshotName}`,
    fullPage: true
  });

  const finalUrl = targetPage.url();
  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitAfterClick(page);
  } else if (page.url() !== sourceUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitAfterClick(page);
  }

  return finalUrl;
}

async function runStep(
  key: ReportKey,
  report: StepReport,
  failures: string[],
  action: () => Promise<void>
): Promise<void> {
  try {
    await action();
    report[key] = "PASS";
  } catch (error) {
    report[key] = "FAIL";
    const message = error instanceof Error ? error.message : String(error);
    failures.push(`${key}: ${message}`);
    console.error(`[step-failure] ${key}: ${message}`);
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page }) => {
    test.setTimeout(240_000);

    const report = createDefaultReport();
    const failures: string[] = [];
    const evidenceUrls: Partial<Record<"terms" | "privacy", string>> = {};
    fs.mkdirSync("screenshots", { recursive: true });
    fs.mkdirSync("reports", { recursive: true });

    await runStep("Login", report, failures, async () => {
      await ensureStartPage(page);

      const popupPromise = page.waitForEvent("popup", { timeout: 4_000 }).catch(() => null);
      const clickedLogin = await clickFirstVisible(page, [
        page.getByRole("button", {
          name: /Sign in with Google|Google|Iniciar sesión con Google|Continuar con Google/i
        }),
        page.getByRole("link", {
          name: /Sign in with Google|Google|Iniciar sesión con Google|Continuar con Google/i
        }),
        page.getByText(/Sign in with Google|Iniciar sesión con Google|Continuar con Google/i)
      ]);
      expect(clickedLogin, "Google login button was not found").toBeTruthy();

      const googlePopup = await popupPromise;
      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded");
        const popupAccountSelector = googlePopup.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
        if (await isVisible(popupAccountSelector)) {
          await popupAccountSelector.click();
        }
        await googlePopup.waitForEvent("close", { timeout: 30_000 }).catch(() => null);
        await page.bringToFront();
        await waitAfterClick(page);
      } else {
        const accountSelector = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
        if (await isVisible(accountSelector)) {
          await accountSelector.click();
          await waitAfterClick(page);
        }
      }

      await expect(page.locator("body")).toContainText(/\S+/, { timeout: 30_000 });
      await ensureSidebarVisible(page);

      await page.screenshot({
        path: "screenshots/01-dashboard-after-login.png",
        fullPage: true
      });
    });

    await runStep("Mi Negocio menu", report, failures, async () => {
      await ensureSidebarVisible(page);
      await openMiNegocio(page);
      await ensureSubmenuVisible(page);

      await page.screenshot({
        path: "screenshots/02-mi-negocio-expanded-menu.png",
        fullPage: true
      });
    });

    await runStep("Agregar Negocio modal", report, failures, async () => {
      await ensureSubmenuVisible(page);
      await clickActionByText(page, "Agregar Negocio");

      const modalTitle = page.getByText(byTextPattern("Crear Nuevo Negocio")).first();
      const nombreField = await firstVisible([
        page.getByLabel(byTextPattern("Nombre del Negocio")),
        page.getByRole("textbox", { name: byTextPattern("Nombre del Negocio") }),
        page.getByPlaceholder(byTextPattern("Nombre del Negocio")),
        page.locator("input").filter({ hasText: byTextPattern("Nombre del Negocio") })
      ]);
      const quotaText = page.getByText(byTextPattern("Tienes\\s*2\\s*de\\s*3\\s*negocios")).first();
      const cancelarBtn = page.getByRole("button", { name: byTextPattern("Cancelar") }).first();
      const crearBtn = page.getByRole("button", { name: byTextPattern("Crear Negocio") }).first();

      await expect(modalTitle).toBeVisible();
      expect(nombreField, "Nombre del Negocio field was not found").not.toBeNull();
      await expect(quotaText).toBeVisible();
      await expect(cancelarBtn).toBeVisible();
      await expect(crearBtn).toBeVisible();

      await page.screenshot({
        path: "screenshots/03-agregar-negocio-modal.png",
        fullPage: true
      });

      if (nombreField) {
        await nombreField.click();
        await nombreField.fill("Negocio Prueba Automatización");
      }
      await cancelarBtn.click();
      await waitAfterClick(page);
    });

    await runStep("Administrar Negocios view", report, failures, async () => {
      await ensureSubmenuVisible(page);
      await clickActionByText(page, "Administrar Negocios");

      await expect(page.getByText(byTextPattern("Información General")).first()).toBeVisible();
      await expect(page.getByText(byTextPattern("Detalles de la Cuenta")).first()).toBeVisible();
      await expect(page.getByText(byTextPattern("Tus Negocios")).first()).toBeVisible();
      await expect(page.getByText(byTextPattern("Sección Legal")).first()).toBeVisible();

      await page.screenshot({
        path: "screenshots/04-administrar-negocios-cuenta.png",
        fullPage: true
      });
    });

    await runStep("Información General", report, failures, async () => {
      await expect(page.getByText(byTextPattern("BUSINESS PLAN")).first()).toBeVisible();
      await expect(page.getByRole("button", { name: byTextPattern("Cambiar Plan") }).first()).toBeVisible();

      await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();

      const userNameVisible = await firstVisible([
        page.getByText(/juan\s*lucas|barbier|garzon/i),
        page.getByText(/Nombre de usuario|Usuario|User Name|Nombre/i)
      ]);
      expect(userNameVisible, "User name was not detected in Información General").not.toBeNull();
    });

    await runStep("Detalles de la Cuenta", report, failures, async () => {
      await expect(page.getByText(byTextPattern("Cuenta creada")).first()).toBeVisible();
      await expect(page.getByText(byTextPattern("Estado activo")).first()).toBeVisible();
      await expect(page.getByText(byTextPattern("Idioma seleccionado")).first()).toBeVisible();
    });

    await runStep("Tus Negocios", report, failures, async () => {
      await expect(page.getByText(byTextPattern("Tus Negocios")).first()).toBeVisible();
      await expect(page.getByText(byTextPattern("Agregar Negocio")).first()).toBeVisible();
      await expect(page.getByText(byTextPattern("Tienes\\s*2\\s*de\\s*3\\s*negocios")).first()).toBeVisible();

      const businessListVisible = await firstVisible([
        page.locator("table tbody tr"),
        page.locator("[role='listitem']"),
        page.locator("ul li")
      ]);
      expect(businessListVisible, "Business list was not visible").not.toBeNull();
    });

    await runStep("Términos y Condiciones", report, failures, async () => {
      const termsUrl = await validateLegalLink(
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "05-terminos-y-condiciones.png"
      );
      evidenceUrls.terms = termsUrl;
      console.log(`[evidence] Términos y Condiciones URL: ${termsUrl}`);
    });

    await runStep("Política de Privacidad", report, failures, async () => {
      const privacyUrl = await validateLegalLink(
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "06-politica-de-privacidad.png"
      );
      evidenceUrls.privacy = privacyUrl;
      console.log(`[evidence] Política de Privacidad URL: ${privacyUrl}`);
    });

    console.log("=== saleads_mi_negocio_full_test FINAL REPORT ===");
    for (const key of PRINTABLE_REPORT_ORDER) {
      console.log(`${key}: ${report[key]}`);
    }

    fs.writeFileSync(
      "reports/saleads_mi_negocio_final_report.json",
      JSON.stringify(
        {
          testName: "saleads_mi_negocio_full_test",
          report,
          evidenceUrls
        },
        null,
        2
      ),
      "utf-8"
    );

    if (failures.length > 0) {
      throw new Error(`One or more steps failed:\n${failures.join("\n")}`);
    }
  });
});
