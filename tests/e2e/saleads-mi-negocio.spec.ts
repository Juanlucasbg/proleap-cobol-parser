import fs from "node:fs";
import { expect, test, type BrowserContext, type Page } from "@playwright/test";

type ValidationStatus = "PASS" | "FAIL";

type StepReport = {
  name: string;
  status: ValidationStatus;
  details: string[];
};

const ACCOUNT_EMAIL = process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_PATH = process.env.SALEADS_LOGIN_PATH || "/";
const BUSINESS_NAME = process.env.SALEADS_TEST_BUSINESS_NAME || "Negocio Prueba Automatizacion";

function addCheckpoint(stepName: string): StepReport {
  const item: StepReport = { name: stepName, status: "PASS", details: [] };
  return item;
}

function failCheckpoint(step: StepReport, detail: string): void {
  step.status = "FAIL";
  step.details.push(detail);
}

function passDetail(step: StepReport, detail: string): void {
  step.details.push(detail);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
}

async function clickAndWait(locatorAction: () => Promise<void>, page: Page): Promise<void> {
  await locatorAction();
  await waitForUi(page);
}

async function assertVisibleByText(
  page: Page,
  text: string | RegExp,
  step: StepReport,
  message: string
): Promise<void> {
  const element = page.getByText(text, { exact: false }).first();
  try {
    await expect(element).toBeVisible({ timeout: 15000 });
    passDetail(step, `${message}: visible`);
  } catch (error) {
    failCheckpoint(step, `${message}: not visible`);
    throw error;
  }
}

async function locateSidebar(page: Page): Promise<void> {
  const sidebarCandidates = [
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator("[data-testid*='sidebar']"),
  ];

  for (const candidate of sidebarCandidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      return;
    }
  }

  throw new Error("Left sidebar navigation was not found.");
}

async function getAppPage(context: BrowserContext, currentPage: Page): Promise<Page> {
  const pages = context.pages();
  const byUrl = pages.find((p) => /saleads/i.test(p.url()));
  return byUrl ?? currentPage;
}

async function openLegalAndValidate(
  context: BrowserContext,
  appPage: Page,
  linkText: string,
  heading: RegExp,
  screenshotName: string
): Promise<{ url: string; status: ValidationStatus; details: string[] }> {
  const details: string[] = [];
  let status: ValidationStatus = "PASS";
  const appUrlBeforeClick = appPage.url();

  const legalLink = appPage.getByText(linkText, { exact: false }).first();
  await expect(legalLink).toBeVisible({ timeout: 15000 });

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickAndWait(() => legalLink.click(), appPage);
  const popup = await popupPromise;

  const legalPage = popup ?? appPage;
  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForLoadState("networkidle");

  try {
    await expect(legalPage.getByText(heading, { exact: false }).first()).toBeVisible({ timeout: 15000 });
    details.push(`Heading matched: ${heading}`);
  } catch {
    status = "FAIL";
    details.push(`Heading not found: ${heading}`);
  }

  const bodyText = await legalPage.locator("body").innerText();
  if (bodyText.trim().length > 100) {
    details.push("Legal body content is visible");
  } else {
    status = "FAIL";
    details.push("Legal body content is too short or missing");
  }

  await legalPage.screenshot({
    path: `test-results/screenshots/${screenshotName}.png`,
    fullPage: true,
  });
  details.push(`Screenshot captured: ${screenshotName}.png`);

  const finalUrl = legalPage.url();
  details.push(`Final URL: ${finalUrl}`);

  if (popup) {
    await popup.close();
  } else {
    await appPage.bringToFront();
    if (appPage.url() !== appUrlBeforeClick) {
      await appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(appPage);
  }

  return { url: finalUrl, status, details };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("Login Google and validate Mi Negocio module workflow", async ({ page, context }, testInfo) => {
    fs.mkdirSync("test-results/screenshots", { recursive: true });
    const report: StepReport[] = [];
    const baseUrl = process.env.SALEADS_BASE_URL;

    const loginStep = addCheckpoint("Login");
    const menuStep = addCheckpoint("Mi Negocio menu");
    const modalStep = addCheckpoint("Agregar Negocio modal");
    const adminStep = addCheckpoint("Administrar Negocios view");
    const infoStep = addCheckpoint("Información General");
    const detailsStep = addCheckpoint("Detalles de la Cuenta");
    const businessesStep = addCheckpoint("Tus Negocios");
    const termsStep = addCheckpoint("Términos y Condiciones");
    const privacyStep = addCheckpoint("Política de Privacidad");
    report.push(
      loginStep,
      menuStep,
      modalStep,
      adminStep,
      infoStep,
      detailsStep,
      businessesStep,
      termsStep,
      privacyStep
    );

    await test.step("1) Login with Google", async () => {
      if (baseUrl) {
        await page.goto(new URL(LOGIN_PATH, baseUrl).toString(), { waitUntil: "domcontentloaded" });
        await waitForUi(page);
      } else {
        passDetail(loginStep, "SALEADS_BASE_URL not provided; using current browser page as login screen");
      }

      const loginButton = page
        .getByRole("button", { name: /google|sign in with google|inicia[r]? con google/i })
        .or(page.getByText(/google|sign in with google|inicia[r]? con google/i).first())
        .first();
      await expect(loginButton).toBeVisible({ timeout: 20000 });

      const popupPromise = context.waitForEvent("page", { timeout: 15000 }).catch(() => null);
      await clickAndWait(() => loginButton.click(), page);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const emailOption = popup.getByText(new RegExp(ACCOUNT_EMAIL, "i")).first();
        if (await emailOption.isVisible().catch(() => false)) {
          await clickAndWait(() => emailOption.click(), popup);
          passDetail(loginStep, `Google account selected: ${ACCOUNT_EMAIL}`);
        } else {
          passDetail(loginStep, "Google account picker did not show configured account, continuing");
        }
      }

      const appPage = await getAppPage(context, page);
      await appPage.bringToFront();
      await waitForUi(appPage);

      try {
        await locateSidebar(appPage);
        passDetail(loginStep, "Main app interface and left sidebar are visible");
      } catch (error) {
        failCheckpoint(loginStep, "Main app interface or left sidebar not visible after login");
        throw error;
      }

      await appPage.screenshot({
        path: "test-results/screenshots/01-dashboard-loaded.png",
        fullPage: true,
      });
      passDetail(loginStep, "Screenshot captured: 01-dashboard-loaded.png");
    });

    const appPage = await getAppPage(context, page);
    await appPage.bringToFront();
    await waitForUi(appPage);

    await test.step("2) Open Mi Negocio menu", async () => {
      const negocioSection = appPage.getByText(/^Negocio$/i).first();
      await expect(negocioSection).toBeVisible({ timeout: 15000 });
      await clickAndWait(() => negocioSection.click(), appPage);

      const miNegocioOption = appPage.getByText(/^Mi Negocio$/i).first();
      if (await miNegocioOption.isVisible().catch(() => false)) {
        await clickAndWait(() => miNegocioOption.click(), appPage);
      }

      await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15000 });
      passDetail(menuStep, "Mi Negocio submenu expanded with expected options");

      await appPage.screenshot({
        path: "test-results/screenshots/02-mi-negocio-menu-expanded.png",
        fullPage: true,
      });
      passDetail(menuStep, "Screenshot captured: 02-mi-negocio-menu-expanded.png");
    });

    await test.step("3) Validate Agregar Negocio modal", async () => {
      const addBusinessLink = appPage.getByText(/Agregar Negocio/i).first();
      await clickAndWait(() => addBusinessLink.click(), appPage);

      const modalTitle = appPage.getByText(/Crear Nuevo Negocio/i).first();
      await expect(modalTitle).toBeVisible({ timeout: 15000 });

      const businessNameInput = appPage
        .getByLabel(/Nombre del Negocio/i)
        .first()
        .or(appPage.getByPlaceholder(/Nombre del Negocio/i).first())
        .or(appPage.locator("input").filter({ hasText: /Nombre del Negocio/i }).first());
      await expect(businessNameInput).toBeVisible({ timeout: 15000 });

      await expect(appPage.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 15000 });
      passDetail(modalStep, "Modal fields and actions are visible");

      if (await businessNameInput.isVisible().catch(() => false)) {
        await businessNameInput.click();
        await businessNameInput.fill(BUSINESS_NAME);
        passDetail(modalStep, "Optional fill action completed");
      }

      await appPage.screenshot({
        path: "test-results/screenshots/03-agregar-negocio-modal.png",
        fullPage: true,
      });
      passDetail(modalStep, "Screenshot captured: 03-agregar-negocio-modal.png");

      await clickAndWait(() => appPage.getByRole("button", { name: /Cancelar/i }).first().click(), appPage);
    });

    await test.step("4) Open Administrar Negocios", async () => {
      const manageBusinesses = appPage.getByText(/Administrar Negocios/i).first();
      if (!(await manageBusinesses.isVisible().catch(() => false))) {
        await clickAndWait(() => appPage.getByText(/^Negocio$/i).first().click(), appPage);
      }
      await clickAndWait(() => appPage.getByText(/Administrar Negocios/i).first().click(), appPage);

      await assertVisibleByText(appPage, /Informacion General|Información General/i, adminStep, "Informacion General section");
      await assertVisibleByText(
        appPage,
        /Detalles de la Cuenta/i,
        adminStep,
        "Detalles de la Cuenta section"
      );
      await assertVisibleByText(appPage, /Tus Negocios/i, adminStep, "Tus Negocios section");
      await assertVisibleByText(appPage, /Seccion Legal|Sección Legal/i, adminStep, "Seccion Legal section");

      await appPage.screenshot({
        path: "test-results/screenshots/04-administrar-negocios-page.png",
        fullPage: true,
      });
      passDetail(adminStep, "Screenshot captured: 04-administrar-negocios-page.png");
    });

    await test.step("5) Validate Informacion General", async () => {
      const userCard = appPage.locator("section,div").filter({
        hasText: /Informacion General|Información General/i,
      });

      try {
        await expect(userCard.first()).toBeVisible({ timeout: 15000 });
      } catch (error) {
        failCheckpoint(infoStep, "Informacion General container not visible");
        throw error;
      }

      const profileText = await userCard.first().innerText();
      if (/@/.test(profileText)) {
        passDetail(infoStep, "User email appears in Informacion General");
      } else {
        failCheckpoint(infoStep, "User email not detected in Informacion General");
      }

      if (/\S+\s+\S+/.test(profileText)) {
        passDetail(infoStep, "User name appears in Informacion General");
      } else {
        failCheckpoint(infoStep, "User name not clearly detected in Informacion General");
      }

      if (/BUSINESS PLAN/i.test(profileText)) {
        passDetail(infoStep, "BUSINESS PLAN text visible");
      } else {
        failCheckpoint(infoStep, "BUSINESS PLAN text not visible");
      }

      const changePlanButton = appPage.getByRole("button", { name: /Cambiar Plan/i }).first();
      if (await changePlanButton.isVisible().catch(() => false)) {
        passDetail(infoStep, "Cambiar Plan button visible");
      } else {
        failCheckpoint(infoStep, "Cambiar Plan button not visible");
      }
    });

    await test.step("6) Validate Detalles de la Cuenta", async () => {
      await assertVisibleByText(appPage, /Cuenta creada/i, detailsStep, "Cuenta creada");
      await assertVisibleByText(appPage, /Estado\s*activo/i, detailsStep, "Estado activo");
      await assertVisibleByText(appPage, /Idioma seleccionado/i, detailsStep, "Idioma seleccionado");
    });

    await test.step("7) Validate Tus Negocios", async () => {
      await assertVisibleByText(appPage, /Tus Negocios/i, businessesStep, "Business list section heading");
      await assertVisibleByText(appPage, /Agregar Negocio/i, businessesStep, "Agregar Negocio button/text");
      await assertVisibleByText(appPage, /Tienes 2 de 3 negocios/i, businessesStep, "Business usage limit text");
    });

    await test.step("8) Validate Terminos y Condiciones", async () => {
      const legalResult = await openLegalAndValidate(
        context,
        appPage,
        "Términos y Condiciones",
        /Terminos y Condiciones|Términos y Condiciones/i,
        "08-terminos-y-condiciones"
      );
      legalResult.details.forEach((d) => passDetail(termsStep, d));
      if (legalResult.status === "FAIL") {
        termsStep.status = "FAIL";
      }
      await appPage.bringToFront();
      await waitForUi(appPage);
    });

    await test.step("9) Validate Politica de Privacidad", async () => {
      const legalResult = await openLegalAndValidate(
        context,
        appPage,
        "Política de Privacidad",
        /Politica de Privacidad|Política de Privacidad/i,
        "09-politica-de-privacidad"
      );
      legalResult.details.forEach((d) => passDetail(privacyStep, d));
      if (legalResult.status === "FAIL") {
        privacyStep.status = "FAIL";
      }
      await appPage.bringToFront();
      await waitForUi(appPage);
    });

    await test.step("10) Final report", async () => {
      const reportLines = report.map((item) => `${item.name}: ${item.status}${item.details.length ? ` | ${item.details.join(" ; ")}` : ""}`);
      const summary = `SaleADS Mi Negocio validation report\n${reportLines.join("\n")}`;
      testInfo.annotations.push({ type: "final-report", description: summary });
      console.log(summary);

      const failed = report.filter((item) => item.status === "FAIL");
      expect(
        failed,
        `One or more validations failed:\n${failed.map((i) => `- ${i.name}: ${i.details.join(" ; ")}`).join("\n")}`
      ).toHaveLength(0);
    });
  });
});
