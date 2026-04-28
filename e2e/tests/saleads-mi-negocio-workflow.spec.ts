import { expect, test, type BrowserContext, type Page } from "@playwright/test";
import * as fs from "fs/promises";
import * as path from "path";

type StepStatus = "PASS" | "FAIL";

type Report = {
  Login: StepStatus;
  "Mi Negocio menu": StepStatus;
  "Agregar Negocio modal": StepStatus;
  "Administrar Negocios view": StepStatus;
  "Información General": StepStatus;
  "Detalles de la Cuenta": StepStatus;
  "Tus Negocios": StepStatus;
  "Términos y Condiciones": StepStatus;
  "Política de Privacidad": StepStatus;
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const CHECKPOINT_SCREENSHOTS_DIR = "checkpoints";

async function waitAfterClick(page: Page): Promise<void> {
  await Promise.race([
    page.waitForLoadState("domcontentloaded", { timeout: 10_000 }).catch(() => null),
    page.waitForTimeout(1_200),
  ]);
}

async function clickByVisibleText(page: Page, textOrPattern: string | RegExp): Promise<void> {
  const locator = page.getByText(textOrPattern, { exact: false }).first();
  await expect(locator, `Expected visible text to click: ${text}`).toBeVisible({ timeout: 15_000 });
  await locator.click();
  await waitAfterClick(page);
}

async function clickIfVisible(page: Page, textOrPattern: string | RegExp): Promise<boolean> {
  const locator = page.getByText(textOrPattern, { exact: false }).first();
  const visible = await locator.isVisible().catch(() => false);
  if (!visible) return false;
  await locator.click();
  await waitAfterClick(page);
  return true;
}

async function screenshotCheckpoint(page: Page, name: string): Promise<void> {
  const normalized = name
    .toLowerCase()
    .replace(/[\s/]+/g, "-")
    .replace(/[^\w-]+/g, "");
  await fs.mkdir(CHECKPOINT_SCREENSHOTS_DIR, { recursive: true });
  await page.screenshot({
    path: path.join(CHECKPOINT_SCREENSHOTS_DIR, `${normalized}.png`),
    fullPage: true,
  });
}

async function openLegalPageAndValidate(params: {
  context: BrowserContext;
  appPage: Page;
  linkText: string | RegExp;
  expectedHeading: string | RegExp;
  screenshotName: string;
}): Promise<{ finalUrl: string }> {
  const { context, appPage, linkText, expectedHeading, screenshotName } = params;
  const appUrlBefore = appPage.url();
  const oldPages = context.pages();
  const newPagePromise = context.waitForEvent("page", { timeout: 5_000 }).catch(() => null);

  await clickByVisibleText(appPage, linkText);

  let targetPage: Page = appPage;
  const newPage = await newPagePromise;
  if (newPage) {
    await newPage.waitForLoadState("domcontentloaded");
    targetPage = newPage;
  } else if (context.pages().length > oldPages.length) {
    targetPage = context.pages().at(-1) ?? appPage;
    await targetPage.waitForLoadState("domcontentloaded").catch(() => null);
  } else {
    await appPage.waitForLoadState("domcontentloaded").catch(() => null);
  }

  const heading = targetPage.getByText(expectedHeading, { exact: false }).first();
  await expect(heading, "Expected legal page heading should be visible").toBeVisible({
    timeout: 20_000,
  });

  const pageContent = targetPage.locator("main, article, body");
  await expect(pageContent).toContainText(/\S+/, { timeout: 20_000 });

  await screenshotCheckpoint(targetPage, screenshotName);
  const finalUrl = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.close();
    await appPage.bringToFront();
  } else {
    const wentBack = await appPage
      .goBack({ waitUntil: "domcontentloaded" })
      .then(() => true)
      .catch(() => false);
    if (!wentBack || appPage.url() === finalUrl) {
      await appPage.goto(appUrlBefore, { waitUntil: "domcontentloaded" }).catch(() => null);
    }
    await appPage.bringToFront();
  }

  return { finalUrl };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    const report: Report = {
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

    let termsUrl = "";
    let privacyUrl = "";

    try {
      if (page.url() === "about:blank") {
        const loginUrl = process.env.SALEADS_LOGIN_URL;
        if (!loginUrl) {
          throw new Error(
            "The browser started on about:blank. Provide SALEADS_LOGIN_URL for the current SaleADS environment.",
          );
        }
        await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      }

      // Step 1: Login with Google
      // Assumes browser is already at login page in current environment.
      const signInLocator = page
        .getByText(/sign in with google|iniciar con google|google/i)
        .first();
      await expect(signInLocator).toBeVisible({ timeout: 30_000 });

      const googlePopupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
      await signInLocator.click();
      await waitAfterClick(page);

      const googlePage = await googlePopupPromise;
      if (googlePage) {
        await googlePage.waitForLoadState("domcontentloaded");
        const accountOption = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
        }
        await waitAfterClick(googlePage);
      } else {
        const inlineAccountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
        if (await inlineAccountOption.isVisible().catch(() => false)) {
          await inlineAccountOption.click();
          await waitAfterClick(page);
        }
      }

      await expect(page.locator("aside, nav")).toBeVisible({ timeout: 45_000 });
      report.Login = "PASS";
      await screenshotCheckpoint(page, "dashboard-loaded");

      // Step 2: Open Mi Negocio menu
      await clickByVisibleText(page, "Negocio");
      await clickByVisibleText(page, "Mi Negocio");
      await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible({
        timeout: 15_000,
      });
      await expect(page.getByText("Administrar Negocios", { exact: false }).first()).toBeVisible({
        timeout: 15_000,
      });
      report["Mi Negocio menu"] = "PASS";
      await screenshotCheckpoint(page, "mi-negocio-menu-expanded");

      // Step 3: Validate Agregar Negocio modal
      await clickByVisibleText(page, "Agregar Negocio");
      await expect(page.getByText("Crear Nuevo Negocio", { exact: false }).first()).toBeVisible({
        timeout: 15_000,
      });
      await expect(page.getByLabel("Nombre del Negocio", { exact: false })).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false }).first()).toBeVisible({
        timeout: 15_000,
      });
      await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: 15_000 });

      await page.getByLabel("Nombre del Negocio", { exact: false }).fill("Negocio Prueba Automatización");
      await screenshotCheckpoint(page, "agregar-negocio-modal");
      await page.getByRole("button", { name: /Cancelar/i }).click();
      await waitAfterClick(page);
      report["Agregar Negocio modal"] = "PASS";

      // Step 4: Open Administrar Negocios
      const administrarNegociosOption = page.getByText("Administrar Negocios", { exact: false }).first();
      if (!(await administrarNegociosOption.isVisible().catch(() => false))) {
        await clickByVisibleText(page, "Mi Negocio");
      }
      await clickByVisibleText(page, "Administrar Negocios");
      await expect(page.getByText("Información General", { exact: false }).first()).toBeVisible({
        timeout: 20_000,
      });
      await expect(page.getByText("Detalles de la Cuenta", { exact: false }).first()).toBeVisible({
        timeout: 20_000,
      });
      await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible({
        timeout: 20_000,
      });
      await expect(page.getByText(/Sección Legal|Seccion Legal/i, { exact: false }).first()).toBeVisible({
        timeout: 20_000,
      });
      report["Administrar Negocios view"] = "PASS";
      await screenshotCheckpoint(page, "administrar-negocios-account-page");

      // Step 5: Validate Información General
      const infoGeneralSection = page
        .locator("section, div")
        .filter({ hasText: /información general|informacion general/i })
        .first();
      await expect(infoGeneralSection).toBeVisible();
      await expect(infoGeneralSection).toContainText(
        /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i,
      );
      const infoText = (await infoGeneralSection.innerText()).replace(/\s+/g, " ").trim();
      const hasLikelyName = infoText
        .split(/\s{2,}|,/)
        .map((part) => part.trim())
        .some(
          (part) =>
            /^[A-Za-zÁÉÍÓÚÑáéíóúñ' -]{3,}$/.test(part) &&
            !/información general|informacion general|business plan|cambiar plan|@/i.test(part),
        );
      expect(hasLikelyName).toBeTruthy();
      await expect(page.getByText("BUSINESS PLAN", { exact: false }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
      report["Información General"] = "PASS";

      // Step 6: Validate Detalles de la Cuenta
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado.*activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
      report["Detalles de la Cuenta"] = "PASS";

      // Step 7: Validate Tus Negocios
      const negociosSection = page
        .locator("section, div")
        .filter({ hasText: /Tus Negocios/i })
        .first();
      await expect(negociosSection).toBeVisible();
      await expect(negociosSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
      await expect(negociosSection.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
      report["Tus Negocios"] = "PASS";

      // Step 8: Validate Términos y Condiciones
      ({ finalUrl: termsUrl } = await openLegalPageAndValidate({
        context,
        appPage: page,
        linkText: /Términos y Condiciones|Terminos y Condiciones/i,
        expectedHeading: /Términos y Condiciones|Terminos y Condiciones/i,
        screenshotName: "terminos-y-condiciones",
      }));
      report["Términos y Condiciones"] = "PASS";

      // Step 9: Validate Política de Privacidad
      ({ finalUrl: privacyUrl } = await openLegalPageAndValidate({
        context,
        appPage: page,
        linkText: /Política de Privacidad|Politica de Privacidad/i,
        expectedHeading: /Política de Privacidad|Politica de Privacidad/i,
        screenshotName: "politica-de-privacidad",
      }));
      report["Política de Privacidad"] = "PASS";
    } finally {
      const summary = {
        report,
        legalUrls: {
          terminosYCondiciones: termsUrl,
          politicaDePrivacidad: privacyUrl,
        },
      };

      await testInfo.attach("final-report", {
        body: JSON.stringify(summary, null, 2),
        contentType: "application/json",
      });

      // Surface the summary directly in test output.
      // eslint-disable-next-line no-console
      console.log("saleads_mi_negocio_full_test_final_report:", JSON.stringify(summary, null, 2));
    }
  });
});
