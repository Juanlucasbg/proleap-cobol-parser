import { expect, test, type Locator, type Page } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  id: number;
  name: string;
  status: StepStatus;
  details: string[];
};

const SCREENSHOT_DIR = process.env.SALEADS_EVIDENCE_DIR ?? "e2e-artifacts";
const DEFAULT_LOGIN_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const DEFAULT_LONG_TIMEOUT = 45_000;

async function ensureEvidenceDir(): Promise<void> {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
}

async function captureCheckpoint(
  page: Page,
  screenshotName: string,
  fullPage = true,
): Promise<void> {
  await ensureEvidenceDir();
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, screenshotName),
    fullPage,
  });
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
  await page.waitForLoadState("networkidle").catch(() => {
    // Some apps keep long-polling connections; timeout here is non-fatal.
  });
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible({ timeout: DEFAULT_LONG_TIMEOUT });
  await locator.click();
  await waitForUiLoad(page);
}

async function clickFirstVisibleAndWait(
  page: Page,
  selectors: string[],
): Promise<boolean> {
  for (const selector of selectors) {
    const candidate = page.locator(selector).first();
    if (await candidate.isVisible().catch(() => false)) {
      await clickAndWait(candidate, page);
      return true;
    }
  }

  return false;
}

async function maybeClickGoogleAccount(page: Page): Promise<void> {
  const accountLocator = page.getByText(DEFAULT_LOGIN_EMAIL, { exact: true });
  if (await accountLocator.isVisible({ timeout: 7_000 }).catch(() => false)) {
    await clickAndWait(accountLocator, page);
  }
}

async function clickFirstVisibleLocator(
  page: Page,
  locators: Locator[],
): Promise<Locator | null> {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      await clickAndWait(locator.first(), page);
      return locator.first();
    }
  }

  return null;
}

async function openLegalLinkAndValidate(
  page: Page,
  linkText: string,
  headingText: string,
  screenshotName: string,
): Promise<{ finalUrl: string }> {
  const appUrl = page.url();
  const link = page.getByText(linkText, { exact: true }).first();
  await expect(link).toBeVisible({ timeout: DEFAULT_LONG_TIMEOUT });

  const [popupOrNull] = await Promise.all([
    page
      .waitForEvent("popup", { timeout: 8_000 })
      .then((popup) => popup)
      .catch(() => null),
    link.click(),
  ]);

  if (!popupOrNull) {
    await waitForUiLoad(page);
    await expect(page.getByText(headingText, { exact: false }).first()).toBeVisible({
      timeout: DEFAULT_LONG_TIMEOUT,
    });
    const legalTextVisible = await page
      .locator("main, article, .content, body")
      .first()
      .innerText();
    expect(legalTextVisible.length).toBeGreaterThan(100);
    await captureCheckpoint(page, screenshotName);
    const currentUrl = page.url();
    await page
      .goBack()
      .then(() => waitForUiLoad(page))
      .catch(async () => {
        await page.goto(appUrl);
        await waitForUiLoad(page);
      });
    return { finalUrl: currentUrl };
  }

  await popupOrNull.waitForLoadState("domcontentloaded");
  await popupOrNull.waitForLoadState("networkidle").catch(() => {
    // Non-fatal if the legal page keeps network open.
  });

  await expect(popupOrNull.getByText(headingText, { exact: false }).first()).toBeVisible({
    timeout: DEFAULT_LONG_TIMEOUT,
  });

  const popupBodyText = await popupOrNull.locator("body").innerText();
  expect(popupBodyText.length).toBeGreaterThan(100);
  await ensureEvidenceDir();
  await popupOrNull.screenshot({
    path: path.join(SCREENSHOT_DIR, screenshotName),
    fullPage: true,
  });
  const finalUrl = popupOrNull.url();
  await popupOrNull.close();
  await page.bringToFront();
  await waitForUiLoad(page);

  return { finalUrl };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login Google + Mi Negocio workflow", async ({ page }, testInfo) => {
    const stepResults: StepResult[] = [];
    const legalUrls: Record<string, string> = {};

    async function runStep(
      id: number,
      name: string,
      implementation: (details: string[]) => Promise<void>,
    ): Promise<boolean> {
      const details: string[] = [];
      try {
        await test.step(`${id}. ${name}`, async () => {
          await implementation(details);
        });
        stepResults.push({ id, name, status: "PASS", details });
        return true;
      } catch (error) {
        const asError = error as Error;
        details.push(asError.message);
        stepResults.push({ id, name, status: "FAIL", details });
        return false;
      }
    }

    await ensureEvidenceDir();

    await runStep(1, "Login with Google", async (details) => {
      if (page.url() === "about:blank") {
        if (!process.env.SALEADS_BASE_URL) {
          throw new Error(
            "La página no está abierta. Define SALEADS_BASE_URL o inicia con la pantalla de login abierta.",
          );
        }
        await page.goto(process.env.SALEADS_BASE_URL);
        await waitForUiLoad(page);
      }

      const loginLocator = await clickFirstVisibleLocator(page, [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/iniciar sesi[oó]n con google/i),
        page.getByRole("button", { name: /google/i }),
      ]);

      if (!loginLocator) {
        throw new Error(
          "No se encontró botón de inicio de sesión con Google en la página actual.",
        );
      }

      const popup = await page
        .waitForEvent("popup", { timeout: 8_000 })
        .then((p) => p)
        .catch(() => null);

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await maybeClickGoogleAccount(popup);
        await popup.waitForTimeout(1_000);
        if (!popup.isClosed()) {
          await popup.close().catch(() => {
            // Non-fatal; some OAuth flows auto-close.
          });
        }
        await page.bringToFront();
      } else {
        await maybeClickGoogleAccount(page);
      }
      await waitForUiLoad(page);

      const mainApp = page.locator("aside, nav, [role='navigation']").first();
      await expect(mainApp).toBeVisible({ timeout: DEFAULT_LONG_TIMEOUT });
      await expect(page.getByText("Negocio", { exact: false }).first()).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });

      await captureCheckpoint(page, "01-dashboard-loaded.png");

      details.push("Aplicación principal y sidebar visibles.");
    });

    await runStep(2, "Open Mi Negocio menu", async (details) => {
      const negocioSection = page.getByText("Negocio", { exact: true }).first();
      await clickAndWait(negocioSection, page);

      const miNegocio = page.getByText("Mi Negocio", { exact: true }).first();
      await clickAndWait(miNegocio, page);

      await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });
      await expect(
        page.getByText("Administrar Negocios", { exact: true }),
      ).toBeVisible({ timeout: DEFAULT_LONG_TIMEOUT });

      await captureCheckpoint(page, "02-mi-negocio-menu-expanded.png");

      details.push("Submenú Mi Negocio expandido con opciones esperadas.");
    });

    await runStep(3, "Validate Agregar Negocio modal", async (details) => {
      const addBusiness = page.getByText("Agregar Negocio", { exact: true }).first();
      await clickAndWait(addBusiness, page);

      await expect(
        page.getByRole("heading", { name: "Crear Nuevo Negocio" }),
      ).toBeVisible({ timeout: DEFAULT_LONG_TIMEOUT });
      const businessNameField = page.getByLabel("Nombre del Negocio");
      await expect(businessNameField).toBeVisible({ timeout: DEFAULT_LONG_TIMEOUT });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });
      await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });
      await expect(page.getByRole("button", { name: "Crear Negocio" })).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });

      await captureCheckpoint(page, "03-crear-nuevo-negocio-modal.png");

      await businessNameField.click();
      await businessNameField.fill("Negocio Prueba Automatización");
      await clickAndWait(page.getByRole("button", { name: "Cancelar" }), page);

      details.push("Modal validado y cerrado con Cancelar.");
    });

    await runStep(4, "Open Administrar Negocios", async (details) => {
      const adminOption = page.getByText("Administrar Negocios", { exact: true }).first();

      if (!(await adminOption.isVisible().catch(() => false))) {
        const miNegocio = page.getByText("Mi Negocio", { exact: true }).first();
        await clickAndWait(miNegocio, page);
      }

      await clickAndWait(page.getByText("Administrar Negocios", { exact: true }).first(), page);

      await expect(page.getByText("Información General", { exact: false }).first()).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });
      await expect(
        page.getByText("Detalles de la Cuenta", { exact: false }).first(),
      ).toBeVisible({ timeout: DEFAULT_LONG_TIMEOUT });
      await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });
      await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });

      await captureCheckpoint(page, "04-administrar-negocios-full-page.png");

      details.push("Vista Administrar Negocios cargada y secciones visibles.");
    });

    await runStep(5, "Validate Información General", async (details) => {
      const infoSection = page.locator("section, div").filter({
        has: page.getByRole("heading", { name: "Información General" }),
      });

      await expect(infoSection.getByText(/@/)).toBeVisible({ timeout: DEFAULT_LONG_TIMEOUT });
      await expect(infoSection.getByText("BUSINESS PLAN")).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });
      await expect(
        infoSection.getByRole("button", { name: "Cambiar Plan" }),
      ).toBeVisible({ timeout: DEFAULT_LONG_TIMEOUT });

      const sectionText = await infoSection.first().innerText();
      expect(sectionText.length).toBeGreaterThan(20);
      details.push("Nombre/email y plan visibles en Información General.");
    });

    await runStep(6, "Validate Detalles de la Cuenta", async (details) => {
      await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });
      await expect(page.getByText("Estado activo", { exact: false })).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });
      await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });

      details.push("Campos de Detalles de la Cuenta visibles.");
    });

    await runStep(7, "Validate Tus Negocios", async (details) => {
      await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });
      await expect(page.getByRole("button", { name: "Agregar Negocio" })).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible({
        timeout: DEFAULT_LONG_TIMEOUT,
      });

      const cardOrList = page
        .locator("ul, table, [role='list'], [class*='business'], [class*='negocio']")
        .first();
      await expect(cardOrList).toBeVisible({ timeout: DEFAULT_LONG_TIMEOUT });

      details.push("Listado de negocios y límites de plan visibles.");
    });

    await runStep(8, "Validate Términos y Condiciones", async (details) => {
      const result = await openLegalLinkAndValidate(
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "08-terminos-y-condiciones.png",
      );
      legalUrls.terminosYCondiciones = result.finalUrl;
      details.push(`URL final: ${result.finalUrl}`);
    });

    await runStep(9, "Validate Política de Privacidad", async (details) => {
      const result = await openLegalLinkAndValidate(
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "09-politica-de-privacidad.png",
      );
      legalUrls.politicaDePrivacidad = result.finalUrl;
      details.push(`URL final: ${result.finalUrl}`);
    });

    const stepMapping: Record<string, number> = {
      Login: 1,
      "Mi Negocio menu": 2,
      "Agregar Negocio modal": 3,
      "Administrar Negocios view": 4,
      "Información General": 5,
      "Detalles de la Cuenta": 6,
      "Tus Negocios": 7,
      "Términos y Condiciones": 8,
      "Política de Privacidad": 9,
    };

    const finalReport = Object.fromEntries(
      Object.entries(stepMapping).map(([label, id]) => {
        const result = stepResults.find((step) => step.id === id);
        return [label, result?.status ?? "FAIL"];
      }),
    );

    await testInfo.attach("final-report.json", {
      body: Buffer.from(
        JSON.stringify(
          {
            testName: "saleads_mi_negocio_full_test",
            report: finalReport,
            stepResults,
            legalUrls,
          },
          null,
          2,
        ),
      ),
      contentType: "application/json",
    });

    const finalReportPath = path.join(SCREENSHOT_DIR, "final-report.json");
    await fs.writeFile(
      finalReportPath,
      JSON.stringify(
        {
          testName: "saleads_mi_negocio_full_test",
          report: finalReport,
          stepResults,
          legalUrls,
        },
        null,
        2,
      ),
      "utf8",
    );

    const failedSteps = stepResults.filter((step) => step.status === "FAIL");
    expect(
      failedSteps,
      `Fallaron ${failedSteps.length} pasos: ${failedSteps
        .map((step) => `${step.id}-${step.name}`)
        .join(", ")}`,
    ).toHaveLength(0);
  });
});
