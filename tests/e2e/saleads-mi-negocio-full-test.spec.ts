import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import { promises as fs } from "node:fs";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

type Status = "PASS" | "FAIL";
type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

interface FinalReport {
  status: Record<ReportField, Status>;
  legalUrls: {
    terminosYCondiciones: string | null;
    politicaDePrivacidad: string | null;
  };
  screenshots: string[];
  errors: Record<string, string>;
}

function reportTemplate(): FinalReport {
  return {
    status: {
      Login: "FAIL",
      "Mi Negocio menu": "FAIL",
      "Agregar Negocio modal": "FAIL",
      "Administrar Negocios view": "FAIL",
      "Información General": "FAIL",
      "Detalles de la Cuenta": "FAIL",
      "Tus Negocios": "FAIL",
      "Términos y Condiciones": "FAIL",
      "Política de Privacidad": "FAIL"
    },
    legalUrls: {
      terminosYCondiciones: null,
      politicaDePrivacidad: null
    },
    screenshots: [],
    errors: {}
  };
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function screenshot(
  page: Page,
  testInfo: TestInfo,
  filename: string,
  report: FinalReport,
  fullPage = false
): Promise<void> {
  const outputPath = testInfo.outputPath(filename);
  await page.screenshot({ path: outputPath, fullPage });
  report.screenshots.push(filename);
}

async function firstVisible(page: Page, texts: string[], timeoutMs = 12_000): Promise<Locator> {
  const started = Date.now();

  while (Date.now() - started < timeoutMs) {
    for (const text of texts) {
      const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      const candidates: Locator[] = [
        page.getByRole("button", { name: new RegExp(escaped, "i") }).first(),
        page.getByRole("link", { name: new RegExp(escaped, "i") }).first(),
        page.getByText(new RegExp(escaped, "i")).first()
      ];

      for (const candidate of candidates) {
        if ((await candidate.count()) > 0 && (await candidate.isVisible().catch(() => false))) {
          return candidate;
        }
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`No visible element found for texts: ${texts.join(", ")}`);
}

async function clickByText(page: Page, texts: string[]): Promise<void> {
  const target = await firstVisible(page, texts);
  await target.click();
  await waitForUi(page);
}

async function validateSectionExists(page: Page, heading: string): Promise<Locator> {
  const sectionHeading = page.getByText(new RegExp(heading, "i")).first();
  await expect(sectionHeading).toBeVisible({ timeout: 30_000 });
  return sectionHeading;
}

async function maybeSelectGoogleAccount(googlePage: Page): Promise<void> {
  await googlePage.waitForLoadState("domcontentloaded");

  const accountOption = googlePage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")).first();
  if ((await accountOption.count()) > 0 && (await accountOption.isVisible().catch(() => false))) {
    await accountOption.click();
    await googlePage.waitForTimeout(800);
  }
}

async function openLegalDocument(
  appPage: Page,
  linkText: string,
  expectedHeading: string,
  screenshotName: string,
  testInfo: TestInfo,
  report: FinalReport
): Promise<string> {
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  await clickByText(appPage, [linkText]);

  const popup = await popupPromise;
  const docPage = popup ?? appPage;

  await docPage.waitForLoadState("domcontentloaded");
  await expect(docPage.getByRole("heading", { name: new RegExp(expectedHeading, "i") })).toBeVisible({
    timeout: 30_000
  });

  const bodyText = (await docPage.locator("body").innerText()).trim();
  expect(bodyText.length).toBeGreaterThan(expectedHeading.length + 30);

  await screenshot(docPage, testInfo, screenshotName, report, true);

  const finalUrl = docPage.url();
  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = reportTemplate();
  const configuredUrl = process.env.SALEADS_URL || process.env.BASE_URL;

  const runValidation = async (field: ReportField, work: () => Promise<void>) => {
    try {
      await work();
      report.status[field] = "PASS";
    } catch (error) {
      report.status[field] = "FAIL";
      report.errors[field] = error instanceof Error ? error.message : String(error);
    }
  };

  await runValidation("Login", async () => {
    if (page.url() === "about:blank") {
      if (!configuredUrl) {
        throw new Error(
          "No active login page detected. Set SALEADS_URL (or BASE_URL) so the test can open the current environment."
        );
      }
      await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickByText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Ingresar con Google",
      "Continuar con Google",
      "Google"
    ]);

    const popup = await popupPromise;
    if (popup) {
      await maybeSelectGoogleAccount(popup);
      await popup.waitForEvent("close", { timeout: 40_000 }).catch(() => undefined);
    } else if (page.url().includes("accounts.google.com")) {
      await maybeSelectGoogleAccount(page);
    }

    await waitForUi(page);
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 60_000 });

    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 30_000 });
    await screenshot(page, testInfo, "01-dashboard-loaded.png", report, true);
  });

  await runValidation("Mi Negocio menu", async () => {
    await clickByText(page, ["Negocio", "Mi Negocio"]);
    await clickByText(page, ["Mi Negocio", "Negocio"]);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await screenshot(page, testInfo, "02-mi-negocio-menu-expanded.png", report);
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickByText(page, ["Agregar Negocio"]);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: 20_000 });
    await screenshot(page, testInfo, "03-agregar-negocio-modal.png", report);

    const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
    if ((await businessNameInput.count()) > 0 && (await businessNameInput.isVisible().catch(() => false))) {
      await businessNameInput.fill("Negocio Prueba Automatización");
    }

    await clickByText(page, ["Cancelar"]);
  });

  await runValidation("Administrar Negocios view", async () => {
    if (!((await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false)) as boolean)) {
      await clickByText(page, ["Mi Negocio", "Negocio"]);
    }

    await clickByText(page, ["Administrar Negocios"]);
    await validateSectionExists(page, "Información General");
    await validateSectionExists(page, "Detalles de la Cuenta");
    await validateSectionExists(page, "Tus Negocios");
    await validateSectionExists(page, "Sección Legal");
    await screenshot(page, testInfo, "04-administrar-negocios-cuenta.png", report, true);
  });

  await runValidation("Información General", async () => {
    const infoCard = page
      .locator("section, div")
      .filter({ has: page.getByText(/Información General/i).first() })
      .first();

    await expect(infoCard).toBeVisible({ timeout: 20_000 });
    await expect(infoCard.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i").first()).toBeVisible({
      timeout: 20_000
    });
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 20_000 });

    const infoText = (await infoCard.innerText()).trim();
    expect(infoText.replace(/\s+/g, " ").length).toBeGreaterThan(40);
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await runValidation("Tus Negocios", async () => {
    const businessesCard = page
      .locator("section, div")
      .filter({ has: page.getByText(/Tus Negocios/i).first() })
      .first();

    await expect(businessesCard).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });

    const businessCardText = (await businessesCard.innerText()).trim();
    expect(businessCardText.replace(/\s+/g, " ").length).toBeGreaterThan(35);
  });

  await runValidation("Términos y Condiciones", async () => {
    report.legalUrls.terminosYCondiciones = await openLegalDocument(
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05-terminos-y-condiciones.png",
      testInfo,
      report
    );
  });

  await runValidation("Política de Privacidad", async () => {
    report.legalUrls.politicaDePrivacidad = await openLegalDocument(
      page,
      "Política de Privacidad",
      "Política de Privacidad",
      "06-politica-de-privacidad.png",
      testInfo,
      report
    );
  });

  const finalReportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(finalReportPath, JSON.stringify(report, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json"
  });

  const failedChecks = Object.entries(report.status).filter(([, value]) => value === "FAIL");
  expect(
    failedChecks,
    failedChecks.length === 0
      ? "All SaleADS Mi Negocio validations passed."
      : `Validations failed: ${failedChecks.map(([name]) => name).join(", ")}`
  ).toHaveLength(0);
});
