import fs from "node:fs/promises";
import path from "node:path";
import { expect, type Locator, type Page, test } from "@playwright/test";

type StepResult = {
  key: string;
  status: "PASS" | "FAIL";
  details: string;
};

const checkpoint = async (page: Page, name: string): Promise<void> => {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
  await fs.mkdir(path.join("test-results", "screenshots"), { recursive: true });
  await page.screenshot({
    path: `test-results/screenshots/${name}.png`,
    fullPage: true
  });
};

const clickAndSettle = async (
  page: Page,
  target: Locator,
  actionLabel: string
): Promise<void> => {
  await expect(target, `Expected actionable element: ${actionLabel}`).toBeVisible({
    timeout: 20000
  });
  await target.click();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
};

const resolveFirstVisible = async (
  locators: Locator[],
  actionLabel: string
): Promise<Locator> => {
  for (const locator of locators) {
    if ((await locator.count()) === 0) {
      continue;
    }

    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  throw new Error(`No visible locator found for: ${actionLabel}`);
};

const maybeSelectGoogleAccount = async (page: Page): Promise<void> => {
  const accountTarget = page.getByText("juanlucasbarbiergarzon@gmail.com", {
    exact: false
  });
  if ((await accountTarget.count()) > 0) {
    if (await accountTarget.first().isVisible().catch(() => false)) {
      await clickAndSettle(page, accountTarget.first(), "Google account selection");
    }
  }
};

const returnToApplicationTab = async (page: Page, appUrl: string): Promise<void> => {
  await page.bringToFront();
  if (!page.url().startsWith(appUrl)) {
    await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(1000);
  }
};

test.describe("SaleADS - Mi Negocio full workflow", () => {
  test("logs in via Google and validates full Mi Negocio flow", async ({ page }, testInfo) => {
    const report: StepResult[] = [];
    const appUrl = process.env.SALEADS_BASE_URL ?? process.env.SALEADS_URL;

    if (!appUrl) {
      throw new Error(
        "Missing SALEADS_BASE_URL. Set it to the current SaleADS environment login URL."
      );
    }

    await page.goto(appUrl, { waitUntil: "domcontentloaded" });

    // Step 1: Login with Google
    try {
      const loginButton = await resolveFirstVisible(
        [
          page.getByRole("button", { name: /sign in with google/i }),
          page.getByRole("button", { name: /google/i }),
          page.getByText(/sign in with google/i),
          page.getByText(/iniciar sesi[oó]n con google/i),
          page.getByRole("button", { name: /iniciar sesi[oó]n/i }),
          page.getByRole("button", { name: /login/i })
        ],
        "Login button"
      );

      await clickAndSettle(page, loginButton, "Login button");
      await maybeSelectGoogleAccount(page);

      await expect(
        page.locator("aside, nav").filter({ hasText: /negocio|mi negocio|dashboard/i }).first()
      ).toBeVisible({ timeout: 45000 });

      await checkpoint(page, "01-dashboard-loaded");
      report.push({
        key: "Login",
        status: "PASS",
        details: "Main app UI and left navigation are visible."
      });
    } catch (error) {
      report.push({
        key: "Login",
        status: "FAIL",
        details: `Login flow failed: ${(error as Error).message}`
      });
      throw error;
    }

    // Common menu locators
    const negocioMenuLocators = [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByRole("button", { name: /negocio/i }),
      page.getByRole("link", { name: /negocio/i }),
      page.getByText(/^mi negocio$/i),
      page.getByText(/^negocio$/i)
    ];

    // Step 2: Open Mi Negocio menu
    try {
      await clickAndSettle(
        page,
        await resolveFirstVisible(negocioMenuLocators, "Mi Negocio menu"),
        "Mi Negocio menu"
      );
      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({
        timeout: 15000
      });

      await checkpoint(page, "02-mi-negocio-menu-expanded");
      report.push({
        key: "Mi Negocio menu",
        status: "PASS",
        details: "Submenu expanded with Agregar Negocio and Administrar Negocios."
      });
    } catch (error) {
      report.push({
        key: "Mi Negocio menu",
        status: "FAIL",
        details: `Menu expansion failed: ${(error as Error).message}`
      });
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickAndSettle(page, page.getByText(/agregar negocio/i).first(), "Agregar Negocio");
      const modal = page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
      await expect(modal).toBeVisible({ timeout: 15000 });
      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
      await expect(modal.getByLabel(/nombre del negocio/i)).toBeVisible();
      await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      await checkpoint(page, "03-agregar-negocio-modal");
      await modal.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatización");
      await clickAndSettle(page, modal.getByRole("button", { name: /cancelar/i }), "Cancelar modal");
      report.push({
        key: "Agregar Negocio modal",
        status: "PASS",
        details: "Modal fields, quota text and buttons validated."
      });
    } catch (error) {
      report.push({
        key: "Agregar Negocio modal",
        status: "FAIL",
        details: `Modal validation failed: ${(error as Error).message}`
      });
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      if ((await page.getByText(/administrar negocios/i).count()) === 0) {
        await clickAndSettle(
          page,
          await resolveFirstVisible(negocioMenuLocators, "Re-open Mi Negocio menu"),
          "Re-open Mi Negocio menu"
        );
      }

      await clickAndSettle(
        page,
        page.getByText(/administrar negocios/i).first(),
        "Administrar Negocios"
      );

      await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({
        timeout: 20000
      });
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({
        timeout: 20000
      });
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible({ timeout: 20000 });

      await checkpoint(page, "04-administrar-negocios-view");
      report.push({
        key: "Administrar Negocios view",
        status: "PASS",
        details: "All expected account sections are visible."
      });
    } catch (error) {
      report.push({
        key: "Administrar Negocios view",
        status: "FAIL",
        details: `Failed to open account management view: ${(error as Error).message}`
      });
      throw error;
    }

    // Step 5: Validate Información General
    try {
      const infoSection = page
        .locator("section, div, article")
        .filter({ hasText: /informaci[oó]n general/i })
        .first();

      await expect(infoSection).toContainText(/@/i, { timeout: 15000 });
      await expect(infoSection).toContainText(/business plan/i);
      await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

      report.push({
        key: "Información General",
        status: "PASS",
        details: "User profile data, BUSINESS PLAN and Cambiar Plan are visible."
      });
    } catch (error) {
      report.push({
        key: "Información General",
        status: "FAIL",
        details: `Información General validation failed: ${(error as Error).message}`
      });
      throw error;
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      const detailsSection = page
        .locator("section, div, article")
        .filter({ hasText: /detalles de la cuenta/i })
        .first();

      await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible();
      await expect(detailsSection.getByText(/estado activo/i)).toBeVisible();
      await expect(detailsSection.getByText(/idioma seleccionado/i)).toBeVisible();

      report.push({
        key: "Detalles de la Cuenta",
        status: "PASS",
        details: "Cuenta creada, Estado activo and Idioma seleccionado are visible."
      });
    } catch (error) {
      report.push({
        key: "Detalles de la Cuenta",
        status: "FAIL",
        details: `Detalles de la Cuenta validation failed: ${(error as Error).message}`
      });
      throw error;
    }

    // Step 7: Validate Tus Negocios
    try {
      const businessSection = page
        .locator("section, div, article")
        .filter({ hasText: /tus negocios/i })
        .first();

      await expect(businessSection).toBeVisible();
      await expect(
        businessSection.getByRole("button", { name: /agregar negocio/i }).first()
      ).toBeVisible();
      await expect(businessSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

      report.push({
        key: "Tus Negocios",
        status: "PASS",
        details: "Business list, add button and quota text are visible."
      });
    } catch (error) {
      report.push({
        key: "Tus Negocios",
        status: "FAIL",
        details: `Tus Negocios validation failed: ${(error as Error).message}`
      });
      throw error;
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const termsLink = page.getByText(/t[eé]rminos y condiciones/i).first();
      await expect(termsLink).toBeVisible({ timeout: 15000 });

      const popupPromise = page.waitForEvent("popup", { timeout: 4000 }).catch(() => null);
      await clickAndSettle(page, termsLink, "Términos y Condiciones");
      const popup = await popupPromise;
      const legalPage = popup ?? page;

      await legalPage.waitForLoadState("domcontentloaded");
      await expect(legalPage.getByText(/t[eé]rminos y condiciones/i).first()).toBeVisible({
        timeout: 20000
      });
      await expect(legalPage.locator("body")).toContainText(/[A-Za-zÀ-ÿ]{5,}/, {
        timeout: 20000
      });
      await fs.mkdir(path.join("test-results", "screenshots"), { recursive: true });
      await legalPage.screenshot({
        path: "test-results/screenshots/08-terminos-y-condiciones.png",
        fullPage: true
      });

      report.push({
        key: "Términos y Condiciones",
        status: "PASS",
        details: `Legal page validated. Final URL: ${legalPage.url()}`
      });

      if (popup) {
        await popup.close();
      }
      await returnToApplicationTab(page, appUrl);
    } catch (error) {
      report.push({
        key: "Términos y Condiciones",
        status: "FAIL",
        details: `Terms validation failed: ${(error as Error).message}`
      });
      throw error;
    }

    // Step 9: Validate Política de Privacidad
    try {
      const privacyLink = page.getByText(/pol[ií]tica de privacidad/i).first();
      await expect(privacyLink).toBeVisible({ timeout: 15000 });

      const popupPromise = page.waitForEvent("popup", { timeout: 4000 }).catch(() => null);
      await clickAndSettle(page, privacyLink, "Política de Privacidad");
      const popup = await popupPromise;
      const legalPage = popup ?? page;

      await legalPage.waitForLoadState("domcontentloaded");
      await expect(legalPage.getByText(/pol[ií]tica de privacidad/i).first()).toBeVisible({
        timeout: 20000
      });
      await expect(legalPage.locator("body")).toContainText(/[A-Za-zÀ-ÿ]{5,}/, {
        timeout: 20000
      });
      await fs.mkdir(path.join("test-results", "screenshots"), { recursive: true });
      await legalPage.screenshot({
        path: "test-results/screenshots/09-politica-de-privacidad.png",
        fullPage: true
      });

      report.push({
        key: "Política de Privacidad",
        status: "PASS",
        details: `Legal page validated. Final URL: ${legalPage.url()}`
      });

      if (popup) {
        await popup.close();
      }
      await returnToApplicationTab(page, appUrl);
    } catch (error) {
      report.push({
        key: "Política de Privacidad",
        status: "FAIL",
        details: `Privacy validation failed: ${(error as Error).message}`
      });
      throw error;
    } finally {
      // Step 10: Final report
      // Expose summary directly in test output so automation can parse PASS/FAIL by field.
      const headers = [
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

      // Ensure every requested field is present in report even on early failures.
      for (const field of headers) {
        if (!report.find((entry) => entry.key === field)) {
          report.push({
            key: field,
            status: "FAIL",
            details: "Not reached due to an earlier failure."
          });
        }
      }

      // Keep deterministic order for machine/human parsing.
      const sortedReport = headers.map((key) => report.find((entry) => entry.key === key)!);
      // eslint-disable-next-line no-console
      console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_START");
      for (const item of sortedReport) {
        // eslint-disable-next-line no-console
        console.log(`${item.key}: ${item.status} - ${item.details}`);
      }
      // eslint-disable-next-line no-console
      console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_END");

      const reportJson = JSON.stringify(sortedReport, null, 2);
      await testInfo.attach("final-report.json", {
        body: Buffer.from(reportJson, "utf-8"),
        contentType: "application/json"
      });
    }
  });
});
