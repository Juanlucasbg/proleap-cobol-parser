import { expect, test, type Locator, type Page } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepReport = {
  name: string;
  status: StepStatus;
  details: string[];
};

const ARTIFACTS_DIR = "e2e-artifacts/saleads-mi-negocio";
const CHECKPOINTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_FILE = path.join(ARTIFACTS_DIR, "final-report.json");

const WORKFLOW_FIELDS = [
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

const uiLoadTimeoutMs = 25000;

async function ensureArtifactsDir(): Promise<void> {
  await fs.mkdir(CHECKPOINTS_DIR, { recursive: true });
}

function escapeForRegex(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function textRegex(text: string): RegExp {
  return new RegExp(`\\b${escapeForRegex(text)}\\b`, "i");
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: uiLoadTimeoutMs }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => undefined);
  await page.waitForTimeout(700);
}

async function clickAndWait(target: Locator, page: Page): Promise<void> {
  await expect(target).toBeVisible({ timeout: uiLoadTimeoutMs });
  await target.click();
  await waitForUiLoad(page);
}

function toLocator(page: Page, text: string): Locator {
  return page.getByText(textRegex(text), { exact: false });
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
      return locator.first();
    }
  }
  return null;
}

async function clickVisibleByText(page: Page, texts: string[]): Promise<Locator> {
  const locators = texts.map((text) => toLocator(page, text));
  const found = await firstVisible(locators);
  if (!found) {
    throw new Error(`No clickable visible element found for text variants: ${texts.join(", ")}`);
  }
  await clickAndWait(found, page);
  return found;
}

async function maybeSelectGoogleAccount(page: Page, email: string): Promise<boolean> {
  const accountCard = page.getByText(textRegex(email), { exact: false }).first();
  if ((await accountCard.count()) > 0 && (await accountCard.isVisible().catch(() => false))) {
    await accountCard.click();
    await waitForUiLoad(page);
    return true;
  }
  return false;
}

async function clickGoogleLoginButtonAndHandlePopup(page: Page): Promise<void> {
  const loginCandidates = [
    "Sign in with Google",
    "Iniciar sesión con Google",
    "Continuar con Google",
    "Google"
  ];
  const locators = loginCandidates.map((text) => toLocator(page, text));
  const found = await firstVisible(locators);
  if (!found) {
    throw new Error(`No visible Google login button found for: ${loginCandidates.join(", ")}`);
  }

  const [googlePopup] = await Promise.all([
    page.waitForEvent("popup", { timeout: 6000 }).catch(() => null),
    found.click()
  ]);

  const authPage = googlePopup ?? page;
  await waitForUiLoad(authPage);
  await maybeSelectGoogleAccount(authPage, "juanlucasbarbiergarzon@gmail.com");

  if (googlePopup) {
    await googlePopup.waitForClose({ timeout: 60000 }).catch(() => undefined);
    await page.bringToFront();
    await waitForUiLoad(page);
  } else {
    await waitForUiLoad(page);
  }
}

async function screenshot(page: Page, name: string, fullPage = false): Promise<string> {
  const filePath = path.join(CHECKPOINTS_DIR, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function assertTextVisible(page: Page, text: string): Promise<void> {
  await expect(toLocator(page, text).first()).toBeVisible({ timeout: uiLoadTimeoutMs });
}

async function openLegalLinkAndValidate(params: {
  appPage: Page;
  linkText: string;
  headingText: string;
  screenshotName: string;
}): Promise<{ url: string }> {
  const { appPage, linkText, headingText, screenshotName } = params;

  const link = toLocator(appPage, linkText).first();
  await expect(link).toBeVisible({ timeout: uiLoadTimeoutMs });

  const [popupOrNull] = await Promise.all([
    appPage.waitForEvent("popup", { timeout: 6000 }).catch(() => null),
    link.click()
  ]);

  const targetPage = popupOrNull ?? appPage;
  await waitForUiLoad(targetPage);
  await expect(toLocator(targetPage, headingText).first()).toBeVisible({ timeout: uiLoadTimeoutMs });
  await expect(targetPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúñÑ]/, { timeout: uiLoadTimeoutMs });

  await screenshot(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popupOrNull) {
    await popupOrNull.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else {
    await appPage.goBack().catch(() => undefined);
    await waitForUiLoad(appPage);
  }

  return { url: finalUrl };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }) => {
    await ensureArtifactsDir();

    const appUrl = process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;
    if (appUrl) {
      await page.goto(appUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
      await waitForUiLoad(page);
    }

    const report = new Map<string, StepReport>();
    const setStep = (name: string, status: StepStatus, details: string[]) => {
      report.set(name, { name, status, details });
    };
    const hasFailed = (name: string): boolean => report.get(name)?.status === "FAIL";
    const markAsBlocked = (name: string, blocker: string) =>
      setStep(name, "FAIL", [`Step could not execute due to prior failure: ${blocker}`]);

    // 1) Login with Google
    try {
      await clickGoogleLoginButtonAndHandlePopup(page);
      await assertTextVisible(page, "Negocio");
      await expect(page.locator("aside").first()).toBeVisible({ timeout: uiLoadTimeoutMs });
      const shot = await screenshot(page, "01-dashboard-loaded");
      setStep("Login", "PASS", [
        "Main application interface loaded after Google login.",
        "Left sidebar is visible.",
        `Screenshot: ${shot}`
      ]);
    } catch (error) {
      setStep("Login", "FAIL", [String(error)]);
    }

    // 2) Open Mi Negocio menu
    if (hasFailed("Login")) {
      markAsBlocked("Mi Negocio menu", "Login");
    } else {
      try {
        await clickVisibleByText(page, ["Negocio", "Mi Negocio"]);
        await assertTextVisible(page, "Agregar Negocio");
        await assertTextVisible(page, "Administrar Negocios");
        const shot = await screenshot(page, "02-mi-negocio-menu-expanded");
        setStep("Mi Negocio menu", "PASS", [
          "Mi Negocio submenu expanded.",
          "Agregar Negocio visible.",
          "Administrar Negocios visible.",
          `Screenshot: ${shot}`
        ]);
      } catch (error) {
        setStep("Mi Negocio menu", "FAIL", [String(error)]);
      }
    }

    // 3) Validate Agregar Negocio modal
    if (hasFailed("Mi Negocio menu")) {
      markAsBlocked("Agregar Negocio modal", "Mi Negocio menu");
    } else {
      try {
        await clickVisibleByText(page, ["Agregar Negocio"]);
        await assertTextVisible(page, "Crear Nuevo Negocio");
        await expect(page.getByLabel(textRegex("Nombre del Negocio")).first()).toBeVisible({ timeout: uiLoadTimeoutMs });
        await assertTextVisible(page, "Tienes 2 de 3 negocios");
        await assertTextVisible(page, "Cancelar");
        await assertTextVisible(page, "Crear Negocio");
        const shot = await screenshot(page, "03-agregar-negocio-modal");

        const nameInput = page.getByLabel(textRegex("Nombre del Negocio")).first();
        await nameInput.click();
        await nameInput.fill("Negocio Prueba Automatización");
        await clickVisibleByText(page, ["Cancelar"]);
        setStep("Agregar Negocio modal", "PASS", [
          "Crear Nuevo Negocio modal validated.",
          "Nombre del Negocio input is present.",
          "Plan limit text and action buttons are visible.",
          `Screenshot: ${shot}`
        ]);
      } catch (error) {
        setStep("Agregar Negocio modal", "FAIL", [String(error)]);
      }
    }

    // 4) Open Administrar Negocios
    if (hasFailed("Mi Negocio menu")) {
      markAsBlocked("Administrar Negocios view", "Mi Negocio menu");
    } else {
      try {
        const adminOption = toLocator(page, "Administrar Negocios").first();
        if (!(await adminOption.isVisible().catch(() => false))) {
          await clickVisibleByText(page, ["Negocio", "Mi Negocio"]);
        }
        await clickVisibleByText(page, ["Administrar Negocios"]);

        await assertTextVisible(page, "Información General");
        await assertTextVisible(page, "Detalles de la Cuenta");
        await assertTextVisible(page, "Tus Negocios");
        await assertTextVisible(page, "Sección Legal");

        const shot = await screenshot(page, "04-administrar-negocios-page", true);
        setStep("Administrar Negocios view", "PASS", [
          "Account management page loaded.",
          "Información General, Detalles de la Cuenta, Tus Negocios and Sección Legal are visible.",
          `Screenshot: ${shot}`
        ]);
      } catch (error) {
        setStep("Administrar Negocios view", "FAIL", [String(error)]);
      }
    }

    // 5) Validate Información General
    if (hasFailed("Administrar Negocios view")) {
      markAsBlocked("Información General", "Administrar Negocios view");
    } else {
      try {
        await assertTextVisible(page, "BUSINESS PLAN");
        await assertTextVisible(page, "Cambiar Plan");

        const generalSection = page.locator("section, div").filter({ hasText: textRegex("Información General") }).first();
        await expect(generalSection).toBeVisible({ timeout: uiLoadTimeoutMs });
        const sectionText = await generalSection.innerText();
        const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(sectionText);
        const lines = sectionText
          .split("\n")
          .map((line) => line.trim())
          .filter(Boolean);
        const hasNameLikeLine = lines.some((line) => {
          if (!/^[A-Za-zÁÉÍÓÚáéíóúñÑ]{2,}(?:\s+[A-Za-zÁÉÍÓÚáéíóúñÑ]{2,})+$/.test(line)) {
            return false;
          }
          return !/(información general|business plan|cambiar plan|plan)/i.test(line);
        });

        expect(hasEmail, "Expected visible user email in Información General section").toBeTruthy();
        expect(hasNameLikeLine, "Expected visible user name-like text in Información General section").toBeTruthy();

        setStep("Información General", "PASS", [
          "Información General section visible.",
          "User name-like text and user email are visible.",
          "BUSINESS PLAN and Cambiar Plan are visible."
        ]);
      } catch (error) {
        setStep("Información General", "FAIL", [String(error)]);
      }
    }

    // 6) Validate Detalles de la Cuenta
    if (hasFailed("Administrar Negocios view")) {
      markAsBlocked("Detalles de la Cuenta", "Administrar Negocios view");
    } else {
      try {
        await assertTextVisible(page, "Cuenta creada");
        await assertTextVisible(page, "Estado activo");
        await assertTextVisible(page, "Idioma seleccionado");
        setStep("Detalles de la Cuenta", "PASS", [
          "Cuenta creada visible.",
          "Estado activo visible.",
          "Idioma seleccionado visible."
        ]);
      } catch (error) {
        setStep("Detalles de la Cuenta", "FAIL", [String(error)]);
      }
    }

    // 7) Validate Tus Negocios
    if (hasFailed("Administrar Negocios view")) {
      markAsBlocked("Tus Negocios", "Administrar Negocios view");
    } else {
      try {
        await assertTextVisible(page, "Tus Negocios");
        await assertTextVisible(page, "Agregar Negocio");
        await assertTextVisible(page, "Tienes 2 de 3 negocios");
        setStep("Tus Negocios", "PASS", [
          "Business list section visible.",
          "Agregar Negocio button exists.",
          "Tienes 2 de 3 negocios text visible."
        ]);
      } catch (error) {
        setStep("Tus Negocios", "FAIL", [String(error)]);
      }
    }

    // 8) Validate Términos y Condiciones
    if (hasFailed("Administrar Negocios view")) {
      markAsBlocked("Términos y Condiciones", "Administrar Negocios view");
    } else {
      try {
        const termsResult = await openLegalLinkAndValidate({
          appPage: page,
          linkText: "Términos y Condiciones",
          headingText: "Términos y Condiciones",
          screenshotName: "08-terminos-y-condiciones"
        });
        setStep("Términos y Condiciones", "PASS", [
          "Legal heading and content validated.",
          `Final URL: ${termsResult.url}`,
          `Screenshot: ${path.join(CHECKPOINTS_DIR, "08-terminos-y-condiciones.png")}`
        ]);
      } catch (error) {
        setStep("Términos y Condiciones", "FAIL", [String(error)]);
      }
    }

    // 9) Validate Política de Privacidad
    if (hasFailed("Administrar Negocios view")) {
      markAsBlocked("Política de Privacidad", "Administrar Negocios view");
    } else {
      try {
        const privacyResult = await openLegalLinkAndValidate({
          appPage: page,
          linkText: "Política de Privacidad",
          headingText: "Política de Privacidad",
          screenshotName: "09-politica-de-privacidad"
        });
        setStep("Política de Privacidad", "PASS", [
          "Legal heading and content validated.",
          `Final URL: ${privacyResult.url}`,
          `Screenshot: ${path.join(CHECKPOINTS_DIR, "09-politica-de-privacidad.png")}`
        ]);
      } catch (error) {
        setStep("Política de Privacidad", "FAIL", [String(error)]);
      }
    }

    // 10) Final Report
    const finalRows = WORKFLOW_FIELDS.map((field) => {
      const step = report.get(field);
      return {
        field,
        status: step?.status ?? "FAIL",
        details: step?.details ?? ["Step did not execute."]
      };
    });

    await fs.writeFile(
      REPORT_FILE,
      JSON.stringify(
        {
          testName: "saleads_mi_negocio_full_test",
          generatedAt: new Date().toISOString(),
          summary: finalRows
        },
        null,
        2
      ),
      "utf8"
    );

    for (const row of finalRows) {
      // Structured console output is easy to parse in CI logs.
      console.log(`[${row.status}] ${row.field} :: ${row.details.join(" | ")}`);
    }

    const failed = finalRows.filter((row) => row.status === "FAIL");
    expect(failed, `Workflow failures found. Report at ${REPORT_FILE}`).toHaveLength(0);
  });
});
