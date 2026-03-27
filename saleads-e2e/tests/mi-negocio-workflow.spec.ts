import { expect, Page, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";

type StepResult = {
  name: string;
  pass: boolean;
  details: string[];
};

const REPORT_DIR = path.resolve(process.cwd(), "evidence");
const SCREENSHOT_DIR = path.join(REPORT_DIR, "screenshots");
const LEGAL_URLS_PATH = path.join(REPORT_DIR, "legal-urls.json");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];

type FinalReport = Record<ReportField, "PASS" | "FAIL">;

const normalize = (value: string): string =>
  value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();

const escapeRegex = (value: string): string =>
  value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await page.waitForTimeout(400);
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const regex = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
  const candidates = [
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByRole("tab", { name: regex }),
    page.getByText(regex),
  ];

  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      await locator.first().click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`No clickable element found by visible text: "${text}"`);
}

async function ensureDir(pathname: string): Promise<void> {
  await fs.promises.mkdir(pathname, { recursive: true });
}

async function saveCheckpoint(page: Page, filename: string, fullPage = false): Promise<void> {
  await ensureDir(SCREENSHOT_DIR);
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, filename),
    fullPage,
  });
}

async function pickGoogleAccountIfVisible(page: Page, email: string): Promise<void> {
  const emailRegex = new RegExp(escapeRegex(email), "i");
  const chooseEmail = page.getByText(emailRegex).first();
  const useAnother = page.getByText(/usar otra cuenta|use another account/i).first();

  if (await chooseEmail.isVisible().catch(() => false)) {
    await chooseEmail.click();
    await waitForUi(page);
    return;
  }

  if (await useAnother.isVisible().catch(() => false)) {
    await useAnother.click();
    await waitForUi(page);
  }
}

async function openLegalAndCapture(
  page: Page,
  linkText: string,
  expectedHeading: string,
  screenshotName: string,
): Promise<{ finalUrl: string; pass: boolean; details: string[] }> {
  const details: string[] = [];
  let pass = true;
  const context = page.context();
  const originalPage = page;

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickByVisibleText(page, linkText);

  const popup = await popupPromise;
  const targetPage = popup ?? originalPage;
  await waitForUi(targetPage);

  try {
    await expect(targetPage.getByRole("heading", { name: new RegExp(escapeRegex(expectedHeading), "i") })).toBeVisible();
    details.push(`Heading "${expectedHeading}" visible.`);
  } catch (error) {
    pass = false;
    details.push(`Heading "${expectedHeading}" not found: ${(error as Error).message}`);
  }

  const legalTextVisible = await targetPage
    .locator("main, article, section, body")
    .filter({ hasText: /\w{5,}/ })
    .first()
    .isVisible()
    .catch(() => false);
  if (!legalTextVisible) {
    pass = false;
    details.push("Legal content text not visibly detected.");
  } else {
    details.push("Legal content text is visible.");
  }

  await saveCheckpoint(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();
  details.push(`Final URL: ${finalUrl}`);

  if (popup) {
    await popup.close().catch(() => undefined);
    await originalPage.bringToFront();
    await waitForUi(originalPage);
  } else {
    await originalPage.goBack().catch(() => undefined);
    await waitForUi(originalPage);
  }

  return { finalUrl, pass, details };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }) => {
    test.setTimeout(12 * 60 * 1000);
    await ensureDir(REPORT_DIR);
    await ensureDir(SCREENSHOT_DIR);

    // Support both execution models:
    // 1) external harness already opened SaleADS login page
    // 2) standalone Playwright run with an environment-provided login URL
    if (page.url() === "about:blank" || page.url().startsWith("chrome-error://")) {
      const bootstrapUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
      if (bootstrapUrl) {
        await page.goto(bootstrapUrl, { waitUntil: "domcontentloaded" });
        await waitForUi(page);
      }
    }

    const results = new Map<ReportField, StepResult>();
    const legalUrls: Record<string, string> = {};

    const record = (field: ReportField, pass: boolean, details: string[]): void => {
      results.set(field, { name: field, pass, details });
    };

    // Step 1: Login with Google
    {
      const details: string[] = [];
      let pass = true;

      try {
        const googleLoginRegex = /sign in with google|iniciar sesión con google|continuar con google/i;
        const loginCandidates = [
          page.getByRole("button", { name: googleLoginRegex }),
          page.getByRole("link", { name: googleLoginRegex }),
          page.getByText(googleLoginRegex),
        ];

        let clicked = false;
        for (const candidate of loginCandidates) {
          if (await candidate.first().isVisible().catch(() => false)) {
            const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
            await candidate.first().click();
            await waitForUi(page);
            clicked = true;
            const popup = await popupPromise;
            if (popup) {
              await waitForUi(popup);
              await pickGoogleAccountIfVisible(popup, "juanlucasbarbiergarzon@gmail.com");
              if (!popup.isClosed()) {
                await popup.close().catch(() => undefined);
              }
            } else {
              await pickGoogleAccountIfVisible(page, "juanlucasbarbiergarzon@gmail.com");
            }
            break;
          }
        }

        if (!clicked) {
          throw new Error("Google login control not found.");
        }

        await waitForUi(page);
        const sidebarVisible = await page
          .locator("aside, nav")
          .filter({ hasText: /negocio|dashboard|inicio|mi negocio/i })
          .first()
          .isVisible()
          .catch(() => false);
        expect(sidebarVisible).toBeTruthy();
        details.push("Main interface loaded and left sidebar detected.");
        await saveCheckpoint(page, "01-dashboard-loaded.png", true);
      } catch (error) {
        pass = false;
        details.push((error as Error).message);
      }

      record("Login", pass, details);
    }

    // Step 2: Open Mi Negocio menu
    {
      const details: string[] = [];
      let pass = true;
      try {
        await clickByVisibleText(page, "Negocio").catch(async () => {
          await clickByVisibleText(page, "Mi Negocio");
        });

        const addBusinessVisible = await page.getByText(/agregar negocio/i).first().isVisible();
        const manageBusinessVisible = await page.getByText(/administrar negocios/i).first().isVisible();
        expect(addBusinessVisible).toBeTruthy();
        expect(manageBusinessVisible).toBeTruthy();
        details.push("Mi Negocio submenu expanded with expected options.");
        await saveCheckpoint(page, "02-mi-negocio-expanded.png");
      } catch (error) {
        pass = false;
        details.push((error as Error).message);
      }
      record("Mi Negocio menu", pass, details);
    }

    // Step 3: Validate Agregar Negocio modal
    {
      const details: string[] = [];
      let pass = true;
      try {
        await clickByVisibleText(page, "Agregar Negocio");
        const modal = page
          .locator('[role="dialog"], .modal, [data-state="open"]')
          .filter({ hasText: /crear nuevo negocio/i })
          .first();
        await expect(modal).toBeVisible();
        await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
        await expect(modal.getByText(/nombre del negocio/i)).toBeVisible();
        await expect(modal.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
        await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
        await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

        const nameInput = modal
          .locator('input[placeholder*="Nombre"], input[name*="nombre"], input[id*="nombre"], input')
          .first();
        if (await nameInput.isVisible().catch(() => false)) {
          await nameInput.fill("Negocio Prueba Automatización");
        }
        await saveCheckpoint(page, "03-crear-nuevo-negocio-modal.png");
        await clickByVisibleText(page, "Cancelar");
        details.push("Agregar Negocio modal validated and closed.");
      } catch (error) {
        pass = false;
        details.push((error as Error).message);
      }
      record("Agregar Negocio modal", pass, details);
    }

    // Step 4: Open Administrar Negocios
    {
      const details: string[] = [];
      let pass = true;
      try {
        if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
          await clickByVisibleText(page, "Mi Negocio");
        }
        await clickByVisibleText(page, "Administrar Negocios");

        const requiredSections = [
          "Información General",
          "Detalles de la Cuenta",
          "Tus Negocios",
          "Sección Legal",
        ];

        for (const section of requiredSections) {
          await expect(page.getByText(new RegExp(escapeRegex(section), "i")).first()).toBeVisible();
        }
        await saveCheckpoint(page, "04-administrar-negocios-page.png", true);
        details.push("Administrar Negocios page sections are visible.");
      } catch (error) {
        pass = false;
        details.push((error as Error).message);
      }
      record("Administrar Negocios view", pass, details);
    }

    // Step 5: Validate Información General
    {
      const details: string[] = [];
      let pass = true;
      try {
        const emailVisible = await page.getByText(/@/).first().isVisible().catch(() => false);
        const businessPlanVisible = await page.getByText(/business plan/i).first().isVisible().catch(() => false);
        const changePlanVisible = await page.getByRole("button", { name: /cambiar plan/i }).first().isVisible().catch(() => false);
        const userNameVisible = await page
          .locator("section, div")
          .filter({ hasText: /informaci[oó]n general/i })
          .first()
          .locator("h1, h2, h3, p, span, strong")
          .nth(1)
          .isVisible()
          .catch(() => false);

        expect(userNameVisible).toBeTruthy();
        expect(emailVisible).toBeTruthy();
        expect(businessPlanVisible).toBeTruthy();
        expect(changePlanVisible).toBeTruthy();
        details.push("Información General content validated.");
      } catch (error) {
        pass = false;
        details.push((error as Error).message);
      }
      record("Información General", pass, details);
    }

    // Step 6: Validate Detalles de la Cuenta
    {
      const details: string[] = [];
      let pass = true;
      try {
        await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
        await expect(page.getByText(/estado activo|activa|activo/i).first()).toBeVisible();
        await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
        details.push("Detalles de la Cuenta fields validated.");
      } catch (error) {
        pass = false;
        details.push((error as Error).message);
      }
      record("Detalles de la Cuenta", pass, details);
    }

    // Step 7: Validate Tus Negocios
    {
      const details: string[] = [];
      let pass = true;
      try {
        await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
        await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
        await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
        const businessItemsCount = await page.locator("li, tr, [role='row']").filter({ hasText: /\S+/ }).count();
        expect(businessItemsCount).toBeGreaterThan(0);
        details.push("Tus Negocios list and controls validated.");
      } catch (error) {
        pass = false;
        details.push((error as Error).message);
      }
      record("Tus Negocios", pass, details);
    }

    // Step 8: Validate Términos y Condiciones
    {
      const outcome = await openLegalAndCapture(
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "08-terminos-y-condiciones.png",
      );
      legalUrls["terminos_y_condiciones"] = outcome.finalUrl;
      record("Términos y Condiciones", outcome.pass, outcome.details);
    }

    // Step 9: Validate Política de Privacidad
    {
      const outcome = await openLegalAndCapture(
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "09-politica-de-privacidad.png",
      );
      legalUrls["politica_de_privacidad"] = outcome.finalUrl;
      record("Política de Privacidad", outcome.pass, outcome.details);
    }

    // Step 10: Final report
    const report: FinalReport = {
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

    for (const field of REPORT_FIELDS) {
      const result = results.get(field);
      report[field] = result?.pass ? "PASS" : "FAIL";
      const details = result?.details.join(" | ") ?? "No details";
      // Keep output human-readable in CI logs.
      // eslint-disable-next-line no-console
      console.log(`[FINAL REPORT] ${field}: ${report[field]} - ${details}`);
    }

    await fs.promises.writeFile(LEGAL_URLS_PATH, JSON.stringify(legalUrls, null, 2), "utf8");
    await fs.promises.writeFile(path.join(REPORT_DIR, "final-report.json"), JSON.stringify(report, null, 2), "utf8");

    const failedFields = REPORT_FIELDS.filter((field) => report[field] === "FAIL");
    expect(
      failedFields,
      `One or more validation groups failed: ${failedFields.join(", ")}`,
    ).toEqual([]);

    // use normalize so TypeScript keeps helper used (and available for future resilient checks)
    expect(normalize("Mi Negocio")).toBe("mi negocio");
  });
});
