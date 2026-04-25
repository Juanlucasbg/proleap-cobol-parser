import { writeFile } from "node:fs/promises";
import { expect, Page, BrowserContext, test, Locator, TestInfo } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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
type StepResult = { status: "PASS" | "FAIL"; details?: string };
type FinalReport = Record<ReportField, StepResult>;

async function waitForUiAfterClick(page: Page): Promise<void> {
  await page.waitForTimeout(350);
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => undefined);
  await page.waitForTimeout(350);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiAfterClick(page);
}

async function clickByVisibleText(page: Page, expression: RegExp): Promise<Locator> {
  const candidates = [
    page.getByRole("button", { name: expression }),
    page.getByRole("link", { name: expression }),
    page.getByRole("menuitem", { name: expression }),
    page.getByRole("tab", { name: expression }),
    page.getByText(expression),
  ];

  for (const candidate of candidates) {
    const item = candidate.first();
    if (await item.isVisible({ timeout: 1_500 }).catch(() => false)) {
      await clickAndWait(page, item);
      return item;
    }
  }

  throw new Error(`No clickable visible element found for pattern: ${expression}`);
}

async function firstVisibleLocator(locators: Locator[], timeoutMs = 1_500): Promise<Locator> {
  for (const locator of locators) {
    const first = locator.first();
    if (await first.isVisible({ timeout: timeoutMs }).catch(() => false)) {
      return first;
    }
  }

  throw new Error("No visible locator found in candidates");
}

async function attachScreenshot(testInfo: TestInfo, page: Page, fileName: string): Promise<void> {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  await testInfo.attach(fileName, { path: screenshotPath, contentType: "image/png" });
}

async function expectTextVisible(page: Page, expression: RegExp): Promise<void> {
  await expect(page.getByText(expression).first()).toBeVisible();
}

async function ensureOnLoginPage(page: Page): Promise<void> {
  const envUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (envUrl) {
    await page.goto(envUrl, { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => undefined);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "Browser is on about:blank. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL) to your current SaleADS environment login page.",
    );
  }
}

async function runLegalValidation(
  testInfo: TestInfo,
  context: BrowserContext,
  appPage: Page,
  linkLabel: RegExp,
  headingLabel: RegExp,
  screenshotName: string,
): Promise<{ finalUrl: string }> {
  const popupPromise = context.waitForEvent("page", { timeout: 6_000 }).catch(() => null);

  const link = await firstVisibleLocator([
    appPage.getByRole("link", { name: linkLabel }),
    appPage.getByRole("button", { name: linkLabel }),
    appPage.getByText(linkLabel),
  ]);

  await clickAndWait(appPage, link);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;

  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => undefined);

  await expect(legalPage.getByRole("heading", { name: headingLabel }).first()).toBeVisible({
    timeout: 20_000,
  });

  const legalContent = legalPage.locator("main, article, section, body").first();
  await expect(legalContent).toContainText(/\S+/, { timeout: 15_000 });
  await attachScreenshot(testInfo, legalPage, screenshotName);

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else if (legalPage !== appPage) {
    await appPage.bringToFront();
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await appPage.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => undefined);
  }

  return { finalUrl };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context }, testInfo) => {
    const report: FinalReport = {
      Login: { status: "FAIL", details: "Not executed" },
      "Mi Negocio menu": { status: "FAIL", details: "Not executed" },
      "Agregar Negocio modal": { status: "FAIL", details: "Not executed" },
      "Administrar Negocios view": { status: "FAIL", details: "Not executed" },
      "Información General": { status: "FAIL", details: "Not executed" },
      "Detalles de la Cuenta": { status: "FAIL", details: "Not executed" },
      "Tus Negocios": { status: "FAIL", details: "Not executed" },
      "Términos y Condiciones": { status: "FAIL", details: "Not executed" },
      "Política de Privacidad": { status: "FAIL", details: "Not executed" },
    };

    let termsUrl = "";
    let privacyUrl = "";

    const runStep = async (field: ReportField, action: () => Promise<void>) => {
      try {
        await action();
        report[field] = { status: "PASS" };
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        report[field] = { status: "FAIL", details: message };
      }
    };

    await runStep("Login", async () => {
      await ensureOnLoginPage(page);

      const loginButton = await firstVisibleLocator([
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ]);

      const authPopupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(page, loginButton);

      const authPopup = await authPopupPromise;
      if (authPopup) {
        await authPopup.waitForLoadState("domcontentloaded");
        const accountSelector = authPopup.getByText(GOOGLE_ACCOUNT_EMAIL).first();
        if (await accountSelector.isVisible({ timeout: 6_000 }).catch(() => false)) {
          await accountSelector.click();
          await authPopup.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => undefined);
        }
      } else {
        const samePageSelector = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
        if (await samePageSelector.isVisible({ timeout: 3_000 }).catch(() => false)) {
          await clickAndWait(page, samePageSelector);
        }
      }

      await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 30_000 });
      await attachScreenshot(testInfo, page, "01-dashboard-loaded.png");
    });

    await runStep("Mi Negocio menu", async () => {
      await clickByVisibleText(page, /^Negocio$/i).catch(async () => clickByVisibleText(page, /Mi Negocio/i));
      await clickByVisibleText(page, /Mi Negocio/i);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await attachScreenshot(testInfo, page, "02-mi-negocio-expanded-menu.png");
    });

    await runStep("Agregar Negocio modal", async () => {
      await clickByVisibleText(page, /Agregar Negocio/i);

      await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first()).toBeVisible();
      await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible().catch(async () => {
        await expect(page.getByPlaceholder(/Nombre del Negocio/i).first()).toBeVisible();
      });
      await expectTextVisible(page, /Tienes 2 de 3 negocios/i);
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();
      await attachScreenshot(testInfo, page, "03-agregar-negocio-modal.png");

      const businessNameField = page.getByLabel(/Nombre del Negocio/i).first();
      if (await businessNameField.isVisible().catch(() => false)) {
        await businessNameField.click();
        await businessNameField.fill("Negocio Prueba Automatización");
      }

      await clickAndWait(page, page.getByRole("button", { name: /Cancelar/i }).first());
      await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first()).not.toBeVisible();
    });

    await runStep("Administrar Negocios view", async () => {
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
        await clickByVisibleText(page, /Mi Negocio/i);
      }

      await clickByVisibleText(page, /Administrar Negocios/i);

      await expect(page.getByRole("heading", { name: /Informaci[oó]n General/i }).first()).toBeVisible({
        timeout: 20_000,
      });
      await expect(page.getByRole("heading", { name: /Detalles de la Cuenta/i }).first()).toBeVisible();
      await expect(page.getByRole("heading", { name: /Tus Negocios/i }).first()).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
      await attachScreenshot(testInfo, page, "04-administrar-negocios-account-page.png");
    });

    await runStep("Información General", async () => {
      await expect(page.getByRole("heading", { name: /Informaci[oó]n General/i }).first()).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

      const possibleNameAndEmail = page.locator("main, section, body");
      await expect(possibleNameAndEmail).toContainText(/@/);
      await expect(possibleNameAndEmail).toContainText(/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/);
    });

    await runStep("Detalles de la Cuenta", async () => {
      await expectTextVisible(page, /Cuenta creada/i);
      await expectTextVisible(page, /Estado activo/i);
      await expectTextVisible(page, /Idioma seleccionado/i);
    });

    await runStep("Tus Negocios", async () => {
      await expect(page.getByRole("heading", { name: /Tus Negocios/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
      await expectTextVisible(page, /Tienes 2 de 3 negocios/i);
    });

    await runStep("Términos y Condiciones", async () => {
      const result = await runLegalValidation(
        testInfo,
        context,
        page,
        /T[eé]rminos y Condiciones/i,
        /T[eé]rminos y Condiciones/i,
        "05-terminos-y-condiciones.png",
      );
      termsUrl = result.finalUrl;
    });

    await runStep("Política de Privacidad", async () => {
      const result = await runLegalValidation(
        testInfo,
        context,
        page,
        /Pol[ií]tica de Privacidad/i,
        /Pol[ií]tica de Privacidad/i,
        "06-politica-de-privacidad.png",
      );
      privacyUrl = result.finalUrl;
    });

    const summary = {
      testName: "saleads_mi_negocio_full_test",
      environment: page.url(),
      generatedAt: new Date().toISOString(),
      validations: report,
      legalUrls: {
        termsAndConditions: termsUrl,
        privacyPolicy: privacyUrl,
      },
    };

    const reportPath = testInfo.outputPath("final-report.json");
    await writeFile(reportPath, JSON.stringify(summary, null, 2), "utf-8");
    await testInfo.attach("final-report.json", {
      path: reportPath,
      contentType: "application/json",
    });

    const finalStatePath = testInfo.outputPath("07-final-state.png");
    await page
      .screenshot({
        path: finalStatePath,
        fullPage: true,
      })
      .catch(() => undefined);
    await testInfo.attach("07-final-state.png", {
      path: finalStatePath,
      contentType: "image/png",
    });

    const failedSections = Object.entries(report)
      .filter(([, result]) => result.status === "FAIL")
      .map(([section]) => section);

    expect(
      failedSections,
      `One or more validation groups failed: ${failedSections.join(", ")}. Check attached final-report.json for details.`,
    ).toEqual([]);
  });
});
