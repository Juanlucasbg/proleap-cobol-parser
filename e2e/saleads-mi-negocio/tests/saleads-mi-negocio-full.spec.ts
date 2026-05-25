import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type StepField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

type StepResult = {
  status: "PASS" | "FAIL";
  checks: string[];
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUiAfterClick(page: Page): Promise<void> {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 7_000 }).catch(() => undefined),
    page.waitForLoadState("domcontentloaded", { timeout: 7_000 }).catch(() => undefined),
    page.waitForTimeout(1_000),
  ]);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiAfterClick(page);
}

async function byVisibleText(page: Page, text: string | RegExp): Promise<Locator> {
  const roleBased = page.getByRole("button", { name: text }).first();
  if (await roleBased.count()) {
    return roleBased;
  }

  const linkBased = page.getByRole("link", { name: text }).first();
  if (await linkBased.count()) {
    return linkBased;
  }

  const textBased = page.getByText(text).first();
  if (await textBased.count()) {
    return textBased;
  }

  return page.locator("text=/^$/").first();
}

async function saveCheckpoint(page: Page, testInfo: TestInfo, name: string, fullPage = true): Promise<void> {
  const filePath = testInfo.outputPath(name);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, { path: filePath, contentType: "image/png" });
}

test("saleads_mi_negocio_full_test", async ({ page, context, baseURL }, testInfo) => {
  const report: Record<StepField, StepResult> = {
    Login: { status: "PASS", checks: [] },
    "Mi Negocio menu": { status: "PASS", checks: [] },
    "Agregar Negocio modal": { status: "PASS", checks: [] },
    "Administrar Negocios view": { status: "PASS", checks: [] },
    "Informacion General": { status: "PASS", checks: [] },
    "Detalles de la Cuenta": { status: "PASS", checks: [] },
    "Tus Negocios": { status: "PASS", checks: [] },
    "Terminos y Condiciones": { status: "PASS", checks: [] },
    "Politica de Privacidad": { status: "PASS", checks: [] },
  };
  const hardFailures: string[] = [];
  const legalUrls: Record<string, string> = {};

  const markPass = (field: StepField, check: string) => {
    report[field].checks.push(`PASS: ${check}`);
  };

  const markFail = (field: StepField, check: string, error: unknown) => {
    report[field].status = "FAIL";
    const message = error instanceof Error ? error.message : String(error);
    report[field].checks.push(`FAIL: ${check} -> ${message}`);
    hardFailures.push(`${field}: ${check} -> ${message}`);
  };

  await test.step("Step 1 - Login with Google", async () => {
    try {
      if (!baseURL) {
        throw new Error("Set SALEADS_URL or BASE_URL before running this test.");
      }

      await page.goto(baseURL, { waitUntil: "domcontentloaded" });
      await page.waitForLoadState("networkidle");

      let loginButton = await byVisibleText(page, /sign in with google|iniciar.*google|continuar.*google|google/i);
      if (!(await loginButton.count())) {
        loginButton = await byVisibleText(page, /login|iniciar sesi[oó]n|ingresar|acceder/i);
      }

      const popupPromise = context.waitForEvent("page", { timeout: 6_000 }).catch(() => null);
      await clickAndWait(loginButton, page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL).first();
        if (await accountOption.count()) {
          await clickAndWait(accountOption, popup);
        }
        await popup.waitForLoadState("networkidle").catch(() => undefined);
      } else {
        const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
        if (await accountOption.count()) {
          await clickAndWait(accountOption, page);
        }
      }

      await page.waitForLoadState("domcontentloaded");
      await page.waitForLoadState("networkidle").catch(() => undefined);

      await expect(page.locator("aside, nav").first()).toBeVisible();
      markPass("Login", "Main application interface appears");
      await expect(page.getByText(/negocio|dashboard|mi negocio/i).first()).toBeVisible();
      markPass("Login", "Left sidebar navigation is visible");

      await saveCheckpoint(page, testInfo, "01-dashboard-loaded.png");
    } catch (error) {
      markFail("Login", "Login with Google flow", error);
    }
  });

  await test.step("Step 2 - Open Mi Negocio menu", async () => {
    try {
      const negocioTrigger = await byVisibleText(page, /negocio|mi negocio/i);
      await clickAndWait(negocioTrigger, page);

      const miNegocio = await byVisibleText(page, /mi negocio/i);
      await clickAndWait(miNegocio, page);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      markPass("Mi Negocio menu", "Agregar Negocio is visible");
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
      markPass("Mi Negocio menu", "Administrar Negocios is visible");
      markPass("Mi Negocio menu", "Mi Negocio submenu expands");

      await saveCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", false);
    } catch (error) {
      markFail("Mi Negocio menu", "Open and validate Mi Negocio submenu", error);
    }
  });

  await test.step("Step 3 - Validate Agregar Negocio modal", async () => {
    try {
      const agregarNegocio = await byVisibleText(page, /agregar negocio/i);
      await clickAndWait(agregarNegocio, page);

      const modal = page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
      await expect(modal).toBeVisible();
      markPass("Agregar Negocio modal", "Modal title Crear Nuevo Negocio is visible");

      await expect(modal.getByText(/nombre del negocio/i)).toBeVisible();
      markPass("Agregar Negocio modal", "Input field Nombre del Negocio exists");
      await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      markPass("Agregar Negocio modal", "Text Tienes 2 de 3 negocios is visible");
      await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();
      markPass("Agregar Negocio modal", "Buttons Cancelar and Crear Negocio are present");

      let nombreInput = modal.getByRole("textbox", { name: /nombre del negocio/i }).first();
      if (!(await nombreInput.count())) {
        nombreInput = modal.getByPlaceholder(/nombre del negocio/i).first();
      }
      if (await nombreInput.count()) {
        await nombreInput.click();
        await nombreInput.fill("Negocio Prueba Automatizacion");
      }

      await saveCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");
      await clickAndWait(modal.getByRole("button", { name: /cancelar/i }), page);
    } catch (error) {
      markFail("Agregar Negocio modal", "Open and validate Crear Nuevo Negocio modal", error);
    }
  });

  await test.step("Step 4 - Open Administrar Negocios", async () => {
    try {
      if (!(await page.getByText(/administrar negocios/i).first().isVisible())) {
        const miNegocio = await byVisibleText(page, /mi negocio/i);
        await clickAndWait(miNegocio, page);
      }

      const administrarNegocios = await byVisibleText(page, /administrar negocios/i);
      await clickAndWait(administrarNegocios, page);

      await expect(page.getByText(/informacion general/i).first()).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByText(/seccion legal/i).first()).toBeVisible();
      markPass("Administrar Negocios view", "Informacion General is visible");
      markPass("Administrar Negocios view", "Detalles de la Cuenta is visible");
      markPass("Administrar Negocios view", "Tus Negocios is visible");
      markPass("Administrar Negocios view", "Seccion Legal is visible");

      await saveCheckpoint(page, testInfo, "04-administrar-negocios-account-page.png");
    } catch (error) {
      markFail("Administrar Negocios view", "Open and validate account page sections", error);
    }
  });

  await test.step("Step 5 - Validate Informacion General", async () => {
    try {
      await expect(page.getByText(/@/).first()).toBeVisible();
      markPass("Informacion General", "User email is visible");
      await expect(page.getByText(/business plan/i).first()).toBeVisible();
      markPass("Informacion General", "BUSINESS PLAN is visible");
      await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
      markPass("Informacion General", "Cambiar Plan button is visible");

      const profileNameCandidate = page.locator("h1, h2, h3, strong").filter({ hasNotText: /business plan/i }).first();
      await expect(profileNameCandidate).toBeVisible();
      markPass("Informacion General", "User name is visible");
    } catch (error) {
      markFail("Informacion General", "Validate Informacion General section", error);
    }
  });

  await test.step("Step 6 - Validate Detalles de la Cuenta", async () => {
    try {
      await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
      markPass("Detalles de la Cuenta", "Cuenta creada is visible");
      markPass("Detalles de la Cuenta", "Estado activo is visible");
      markPass("Detalles de la Cuenta", "Idioma seleccionado is visible");
    } catch (error) {
      markFail("Detalles de la Cuenta", "Validate Detalles de la Cuenta section", error);
    }
  });

  await test.step("Step 7 - Validate Tus Negocios", async () => {
    try {
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      markPass("Tus Negocios", "Business list is visible");
      await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
      markPass("Tus Negocios", "Agregar Negocio button exists");
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
      markPass("Tus Negocios", "Tienes 2 de 3 negocios text is visible");
    } catch (error) {
      markFail("Tus Negocios", "Validate Tus Negocios section", error);
    }
  });

  const validateLegalLink = async (
    field: StepField,
    linkRegex: RegExp,
    headingRegex: RegExp,
    screenshotName: string,
  ) => {
    try {
      const appPage = page;
      const appPageUrlBefore = appPage.url();
      const existingPages = context.pages().length;
      const newPagePromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

      const legalLink = await byVisibleText(appPage, linkRegex);
      await clickAndWait(legalLink, appPage);

      const possibleNewPage = await newPagePromise;
      const targetPage = possibleNewPage ?? appPage;
      await targetPage.waitForLoadState("domcontentloaded");
      await targetPage.waitForLoadState("networkidle").catch(() => undefined);

      await expect(targetPage.getByText(headingRegex).first()).toBeVisible();
      markPass(field, "Legal heading is visible");

      const bodyText = targetPage.locator("main, article, body").first();
      await expect(bodyText).toContainText(/\w{20,}/);
      markPass(field, "Legal content text is visible");

      legalUrls[field] = targetPage.url();
      markPass(field, `Final URL: ${targetPage.url()}`);

      await saveCheckpoint(targetPage, testInfo, screenshotName);

      if (context.pages().length > existingPages && possibleNewPage) {
        await possibleNewPage.close();
        await appPage.bringToFront();
      } else if (appPage.url() !== appPageUrlBefore) {
        await appPage.goBack().catch(() => undefined);
        await appPage.bringToFront();
      }

      await waitForUiAfterClick(appPage);
    } catch (error) {
      markFail(field, `Validate legal link ${linkRegex}`, error);
    }
  };

  await test.step("Step 8 - Validate Terminos y Condiciones", async () => {
    await validateLegalLink(
      "Terminos y Condiciones",
      /terminos y condiciones|términos y condiciones/i,
      /terminos y condiciones|términos y condiciones/i,
      "05-terminos-y-condiciones.png",
    );
  });

  await test.step("Step 9 - Validate Politica de Privacidad", async () => {
    await validateLegalLink(
      "Politica de Privacidad",
      /politica de privacidad|política de privacidad/i,
      /politica de privacidad|política de privacidad/i,
      "06-politica-de-privacidad.png",
    );
  });

  await test.step("Step 10 - Final report", async () => {
    const reportPayload = {
      metadata: {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
      },
      results: report,
      urls: legalUrls,
    };

    const reportFile = testInfo.outputPath("saleads-mi-negocio-final-report.json");
    await fs.mkdir(path.dirname(reportFile), { recursive: true });
    await fs.writeFile(reportFile, JSON.stringify(reportPayload, null, 2), "utf-8");
    await testInfo.attach("saleads-mi-negocio-final-report.json", {
      path: reportFile,
      contentType: "application/json",
    });

    console.log("\n=== SaleADS Mi Negocio Final Report ===");
    for (const [field, result] of Object.entries(reportPayload.results)) {
      console.log(`${field}: ${result.status}`);
      for (const check of result.checks) {
        console.log(`  - ${check}`);
      }
    }
    if (Object.keys(legalUrls).length) {
      console.log(`Legal URLs: ${JSON.stringify(legalUrls, null, 2)}`);
    }
  });

  if (hardFailures.length > 0) {
    throw new Error(`Validation failures:\n${hardFailures.join("\n")}`);
  }
});
