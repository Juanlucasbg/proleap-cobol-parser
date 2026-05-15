import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

type StepResult = "PASS" | "FAIL";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const NEW_BUSINESS_NAME = "Negocio Prueba Automatizacion";

const toContainsRegex = (text: string): RegExp => {
  const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(escaped, "i");
};

const isVisible = async (locator: Locator, timeout = 5_000): Promise<boolean> => {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
};

const waitForUiToLoad = async (page: Page): Promise<void> => {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
};

const clickAndWait = async (page: Page, locator: Locator): Promise<void> => {
  await locator.first().click();
  await waitForUiToLoad(page);
};

const screenshotCheckpoint = async (
  page: Page,
  testInfo: TestInfo,
  checkpointName: string,
  fullPage = false
): Promise<void> => {
  await page.screenshot({
    path: testInfo.outputPath(`${checkpointName}.png`),
    fullPage
  });
};

const ensureOnLoginPage = async (page: Page): Promise<void> => {
  if (page.url() !== "about:blank") {
    return;
  }

  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "The page started on about:blank and no URL was provided. Set SALEADS_LOGIN_URL to the current environment login page."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToLoad(page);
};

test("saleads mi negocio full workflow", async ({ page }, testInfo) => {
  test.slow();

  const results: Record<ReportField, StepResult> = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Informacion General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Terminos y Condiciones": "FAIL",
    "Politica de Privacidad": "FAIL"
  };
  const legalUrls: Record<string, string> = {};
  const failures: string[] = [];

  const runStep = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      results[field] = "PASS";
    } catch (error) {
      results[field] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      failures.push(`${field}: ${message}`);
    }
  };

  await runStep("Login", async () => {
    await ensureOnLoginPage(page);

    const negocioSidebarItem = page.getByText(/^Negocio$/i).first();
    if (!(await isVisible(negocioSidebarItem, 10_000))) {
      const googleButtonByRole = page.getByRole("button", { name: /google/i }).first();
      const googleButtonByText = page.getByText(/sign in with google|iniciar sesion con google|continuar con google/i).first();
      const googleButton = (await isVisible(googleButtonByRole, 8_000)) ? googleButtonByRole : googleButtonByText;

      if (!(await isVisible(googleButton, 8_000))) {
        throw new Error("Google login button was not visible.");
      }

      const popupPromise = page.waitForEvent("popup", { timeout: 20_000 }).catch(() => null);
      await googleButton.click();
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => undefined);

        const accountSelector = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
        if (await isVisible(accountSelector, 20_000)) {
          await accountSelector.click();
        }

        await popup.waitForTimeout(1_000);
      }
    }

    await expect(page.locator("main, [role='main']").first()).toBeVisible({ timeout: 90_000 });
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 90_000 });
    await screenshotCheckpoint(page, testInfo, "step-1-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    await clickAndWait(page, negocioSection);

    const miNegocioOption = page.getByText(/^Mi Negocio$/i).first();
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await screenshotCheckpoint(page, testInfo, "step-2-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocioMenuItem = page.getByText(/^Agregar Negocio$/i).first();
    await clickAndWait(page, agregarNegocioMenuItem);

    const dialog = page.getByRole("dialog").first();
    const modalScope = (await isVisible(dialog, 5_000)) ? dialog : page.locator("body");

    await expect(modalScope.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(modalScope.getByText(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(modalScope.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(modalScope.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(modalScope.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    const businessNameFieldByLabel = modalScope.getByLabel(/Nombre del Negocio/i).first();
    const businessNameFieldByPlaceholder = modalScope.getByPlaceholder(/Nombre del Negocio/i).first();
    const businessNameField = (await isVisible(businessNameFieldByLabel, 3_000))
      ? businessNameFieldByLabel
      : businessNameFieldByPlaceholder;

    if (await isVisible(businessNameField, 3_000)) {
      await businessNameField.click();
      await businessNameField.fill(NEW_BUSINESS_NAME);
    }

    await screenshotCheckpoint(page, testInfo, "step-3-crear-negocio-modal");
    await clickAndWait(page, modalScope.getByRole("button", { name: /Cancelar/i }).first());
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegociosOption = page.getByText(/^Administrar Negocios$/i).first();
    if (!(await isVisible(administrarNegociosOption, 5_000))) {
      await clickAndWait(page, page.getByText(/^Mi Negocio$/i).first());
    }

    await clickAndWait(page, page.getByText(/^Administrar Negocios$/i).first());
    await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible();
    await screenshotCheckpoint(page, testInfo, "step-4-administrar-negocios", true);
  });

  await runStep("Informacion General", async () => {
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const generalInfoSection = page.getByText(/Informacion General|Información General/i).first();
    await expect(generalInfoSection).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  const validateLegalLink = async (
    linkText: string,
    headingText: string,
    resultField: ReportField,
    checkpointName: string
  ): Promise<void> => {
    await runStep(resultField, async () => {
      const linkByRole = page.getByRole("link", { name: toContainsRegex(linkText) }).first();
      const linkByText = page.getByText(toContainsRegex(linkText)).first();
      const legalLink = (await isVisible(linkByRole, 5_000)) ? linkByRole : linkByText;

      if (!(await isVisible(legalLink, 8_000))) {
        throw new Error(`Legal link "${linkText}" is not visible.`);
      }

      const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
      const navPromise = page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 15_000 }).catch(() => null);

      await legalLink.click();
      await waitForUiToLoad(page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 40_000 });
        await expect(popup.getByRole("heading", { name: toContainsRegex(headingText) }).first()).toBeVisible({
          timeout: 40_000
        });

        const bodyText = (await popup.locator("body").innerText()).trim();
        expect(bodyText.length).toBeGreaterThan(120);

        legalUrls[linkText] = popup.url();
        await screenshotCheckpoint(popup, testInfo, checkpointName, true);
        await popup.close();
        await page.bringToFront();
      } else {
        await navPromise;
        await waitForUiToLoad(page);
        await expect(page.getByRole("heading", { name: toContainsRegex(headingText) }).first()).toBeVisible({
          timeout: 40_000
        });

        const bodyText = (await page.locator("body").innerText()).trim();
        expect(bodyText.length).toBeGreaterThan(120);

        legalUrls[linkText] = page.url();
        await screenshotCheckpoint(page, testInfo, checkpointName, true);

        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
        await waitForUiToLoad(page);
      }
    });
  };

  await validateLegalLink(
    "Términos y Condiciones",
    "Términos y Condiciones",
    "Terminos y Condiciones",
    "step-8-terminos-condiciones"
  );

  await validateLegalLink(
    "Politica de Privacidad",
    "Politica de Privacidad",
    "Politica de Privacidad",
    "step-9-politica-privacidad"
  );

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    results,
    legalUrls,
    failures
  };

  await testInfo.attach("final-mi-negocio-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json"
  });

  await page.evaluate((reportObject) => {
    console.log("Mi Negocio final report:", reportObject);
  }, finalReport);

  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
