import fs from "fs";
import path from "path";
import { expect, test, type Page } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

const checkpointsDir = path.resolve(
  __dirname,
  "..",
  "artifacts",
  "screenshots",
);
const reportFile = path.resolve(__dirname, "..", "artifacts", "final-report.json");

const report = {
  Login: "FAIL" as StepStatus,
  "Mi Negocio menu": "FAIL" as StepStatus,
  "Agregar Negocio modal": "FAIL" as StepStatus,
  "Administrar Negocios view": "FAIL" as StepStatus,
  "Información General": "FAIL" as StepStatus,
  "Detalles de la Cuenta": "FAIL" as StepStatus,
  "Tus Negocios": "FAIL" as StepStatus,
  "Términos y Condiciones": "FAIL" as StepStatus,
  "Política de Privacidad": "FAIL" as StepStatus,
};

const waitAfterClick = async (page: Page): Promise<void> => {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
};

const waitForPopup = async (page: Page): Promise<Page | null> => {
  return page.context().waitForEvent("page", { timeout: 5_000 }).catch(() => null);
};

const clickByVisibleText = async (page: Page, text: string): Promise<void> => {
  const locator = page.getByText(text, { exact: false }).first();
  await expect(locator).toBeVisible({ timeout: 30_000 });
  await locator.click();
  await waitAfterClick(page);
};

const ensureSidebarIsVisible = async (page: Page): Promise<void> => {
  const sidebar = page.locator("aside").first();
  if (await sidebar.count()) {
    await expect(sidebar).toBeVisible();
    return;
  }

  const negocioText = page.getByText("Negocio", { exact: false }).first();
  await expect(negocioText).toBeVisible();
};

const validateLegalPage = async (
  appPage: Page,
  linkText: string,
  headingText: string,
  screenshotName: string,
): Promise<string> => {
  const appPageUrlBeforeClick = appPage.url();
  const link = appPage.getByText(linkText, { exact: false }).first();
  await expect(link).toBeVisible({ timeout: 30_000 });

  const [popupOrNull] = await Promise.all([
    waitForPopup(appPage),
    link.click(),
  ]);

  const targetPage = popupOrNull ?? appPage;
  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForTimeout(1_000);

  await expect(
    targetPage.getByRole("heading", { name: headingText, exact: false }).first(),
  ).toBeVisible({ timeout: 30_000 });

  await expect(targetPage.locator("body")).toContainText(
    /t[eé]rminos|condiciones|privacidad|legal|datos/i,
    { timeout: 30_000 },
  );

  await targetPage.screenshot({
    path: path.join(checkpointsDir, screenshotName),
    fullPage: true,
  });

  const finalUrl = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitAfterClick(appPage);
  } else {
    await appPage
      .goBack({ waitUntil: "domcontentloaded" })
      .catch(async () => appPage.goto(appPageUrlBeforeClick, { waitUntil: "domcontentloaded" }));
    await waitAfterClick(appPage);
  }

  return finalUrl;
};

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page }) => {
    fs.mkdirSync(checkpointsDir, { recursive: true });
    fs.mkdirSync(path.dirname(reportFile), { recursive: true });

    const startUrl = process.env.SALEADS_LOGIN_URL;
    const googleAccountEmail =
      process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
    if (!startUrl) {
      throw new Error(
        "Missing SALEADS_LOGIN_URL env var. Set it to the current environment login URL (dev/staging/prod).",
      );
    }

    let terminosUrl = "";
    let privacidadUrl = "";

    try {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });

      // Step 1: Login with Google
      const signInButton = page
        .locator("button, a, [role='button']")
        .filter({ hasText: /google|sign in|iniciar sesión/i })
        .first();
      await expect(signInButton).toBeVisible({ timeout: 30_000 });

      const popupPromise = waitForPopup(page);
      await signInButton.click();

      const authPage = await popupPromise;
      if (authPage) {
        await authPage.waitForLoadState("domcontentloaded");
        const accountOption = authPage
          .getByText(googleAccountEmail, { exact: false })
          .first();
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
        }
        await authPage.waitForTimeout(1_500);
        await authPage.close().catch(() => {
          // If OAuth closes itself or blocks manual closing, continue.
        });
      }

      await page.bringToFront();
      await page.waitForLoadState("domcontentloaded");
      await ensureSidebarIsVisible(page);
      await page.screenshot({
        path: path.join(checkpointsDir, "step1-dashboard-loaded.png"),
        fullPage: true,
      });
      report.Login = "PASS";

      // Step 2: Open Mi Negocio menu
      await clickByVisibleText(page, "Negocio");
      await clickByVisibleText(page, "Mi Negocio");

      await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible();
      await expect(
        page.getByText("Administrar Negocios", { exact: false }).first(),
      ).toBeVisible();

      await page.screenshot({
        path: path.join(checkpointsDir, "step2-mi-negocio-menu-expanded.png"),
        fullPage: true,
      });
      report["Mi Negocio menu"] = "PASS";

      // Step 3: Validate Agregar Negocio modal
      await clickByVisibleText(page, "Agregar Negocio");

      await expect(page.getByText("Crear Nuevo Negocio", { exact: false }).first()).toBeVisible();
      const businessNameInput = page
        .getByLabel("Nombre del Negocio", { exact: false })
        .or(page.getByPlaceholder("Nombre del Negocio", { exact: false }))
        .first();
      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible();

      await page.screenshot({
        path: path.join(checkpointsDir, "step3-agregar-negocio-modal.png"),
        fullPage: true,
      });

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await page.getByRole("button", { name: "Cancelar" }).first().click();
      await waitAfterClick(page);
      report["Agregar Negocio modal"] = "PASS";

      // Step 4: Open Administrar Negocios
      const adminNegociosLink = page.getByText("Administrar Negocios", { exact: false }).first();
      if (!(await adminNegociosLink.isVisible().catch(() => false))) {
        await clickByVisibleText(page, "Mi Negocio");
      }
      await adminNegociosLink.click();
      await waitAfterClick(page);

      await expect(page.getByText("Información General", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Sección Legal", { exact: false }).first()).toBeVisible();

      await page.screenshot({
        path: path.join(checkpointsDir, "step4-administrar-negocios-view.png"),
        fullPage: true,
      });
      report["Administrar Negocios view"] = "PASS";

      // Step 5: Validate Información General
      const infoSection = page.locator("section,div").filter({
        has: page.getByText("Información General", { exact: false }),
      }).first();
      await expect(infoSection).toContainText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
      await expect(infoSection).toContainText(/[A-Za-zÀ-ÿ]+(?:\s+[A-Za-zÀ-ÿ]+)+/);
      await expect(infoSection).toContainText(/BUSINESS PLAN/i);
      await expect(page.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible();
      report["Información General"] = "PASS";

      // Step 6: Validate Detalles de la Cuenta
      await expect(page.getByText("Cuenta creada", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Estado activo", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Idioma seleccionado", { exact: false }).first()).toBeVisible();
      report["Detalles de la Cuenta"] = "PASS";

      // Step 7: Validate Tus Negocios
      const businessSection = page.locator("section,div").filter({
        has: page.getByText("Tus Negocios", { exact: false }),
      }).first();
      await expect(businessSection).toBeVisible();
      await expect(
        businessSection.getByRole("button", { name: "Agregar Negocio" }).first(),
      ).toBeVisible();
      await expect(
        businessSection.getByText("Tienes 2 de 3 negocios", { exact: false }).first(),
      ).toBeVisible();
      report["Tus Negocios"] = "PASS";

      // Step 8: Validate Términos y Condiciones
      terminosUrl = await validateLegalPage(
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "step8-terminos-y-condiciones.png",
      );
      report["Términos y Condiciones"] = "PASS";

      // Step 9: Validate Política de Privacidad
      privacidadUrl = await validateLegalPage(
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "step9-politica-de-privacidad.png",
      );
      report["Política de Privacidad"] = "PASS";
    } finally {
      const finalReport = {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        environmentUrl: startUrl,
        legalUrls: {
          terminosYCondiciones: terminosUrl,
          politicaDePrivacidad: privacidadUrl,
        },
        results: report,
      };

      fs.writeFileSync(reportFile, JSON.stringify(finalReport, null, 2), "utf8");
    }
  });
});
