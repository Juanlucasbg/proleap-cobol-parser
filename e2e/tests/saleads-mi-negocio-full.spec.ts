import { expect, Locator, Page, test } from "@playwright/test";
import * as fs from "node:fs";
import * as path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  id: number;
  name: string;
  status: StepStatus;
  details: string[];
  evidence: string[];
  urls: string[];
};

const outputDir = process.env.SALEADS_OUTPUT_DIR ?? "artifacts/saleads-mi-negocio-full-test";
const runId = new Date().toISOString().replaceAll(":", "-");

const reportFields = [
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

function makeFileName(stepId: number, label: string): string {
  const normalized = label
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");

  return `step-${String(stepId).padStart(2, "0")}-${normalized}.png`;
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function screenshot(page: Page, stepId: number, label: string): Promise<string> {
  const fileName = makeFileName(stepId, label);
  const fullPath = path.join(outputDir, runId, fileName);
  fs.mkdirSync(path.dirname(fullPath), { recursive: true });
  await page.screenshot({ path: fullPath, fullPage: true });
  return fullPath;
}

function getTextLocator(page: Page, text: string) {
  return page.getByText(text, { exact: false });
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator.isVisible().catch(() => false);
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    if (await isVisible(locator)) {
      return locator;
    }
  }
  return null;
}

async function clickByVisibleText(page: Page, candidates: string[]): Promise<string> {
  for (const candidate of candidates) {
    const locator = page.getByRole("button", { name: candidate, exact: false }).first();
    if (await isVisible(locator)) {
      await locator.click();
      return candidate;
    }

    const generic = getTextLocator(page, candidate).first();
    if (await isVisible(generic)) {
      await generic.click();
      return candidate;
    }
  }
  throw new Error(`Could not find clickable element using visible text: ${candidates.join(", ")}`);
}

async function assertVisibleText(page: Page, text: string): Promise<void> {
  await expect(getTextLocator(page, text).first()).toBeVisible();
}

async function maybeSelectGoogleAccount(page: Page, accountEmail: string): Promise<boolean> {
  const accountLocator = page.getByText(accountEmail, { exact: false }).first();
  if (await isVisible(accountLocator)) {
    await accountLocator.click();
    return true;
  }
  return false;
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context, baseURL }) => {
    const results: StepResult[] = [];
    const appUrl = process.env.SALEADS_BASE_URL ?? baseURL;

    if (!appUrl) {
      throw new Error("Set SALEADS_BASE_URL env var or playwright baseURL.");
    }

    const pushResult = (result: StepResult) => results.push(result);

    await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const step1: StepResult = {
      id: 1,
      name: "Login",
      status: "PASS",
      details: [],
      evidence: [],
      urls: []
    };
    try {
      const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
      const clickedLabel = await clickByVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Google"
      ]);
      step1.details.push(`Clicked login action: ${clickedLabel}`);
      await waitForUi(page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const selectedOnPopup = await maybeSelectGoogleAccount(popup, "juanlucasbarbiergarzon@gmail.com");
        if (selectedOnPopup) {
          step1.details.push("Selected expected Google account in Google popup.");
          await popup.waitForTimeout(600);
        } else {
          step1.details.push("Google popup detected, but account selector was not shown.");
        }
        await popup.waitForEvent("close", { timeout: 20_000 }).catch(() => undefined);
        await page.bringToFront();
      } else {
        const selectedOnMainPage = await maybeSelectGoogleAccount(page, "juanlucasbarbiergarzon@gmail.com");
        if (selectedOnMainPage) {
          step1.details.push("Selected expected Google account.");
          await waitForUi(page);
        } else {
          step1.details.push("Google selector not visible, session may already be authenticated.");
        }
      }

      await waitForUi(page);
      const sidebar = await firstVisible([page.locator("aside").first(), page.getByRole("navigation").first()]);
      expect(sidebar, "Left sidebar navigation should be visible after login.").not.toBeNull();
      step1.details.push("Main app and left sidebar are visible.");
      step1.evidence.push(await screenshot(page, 1, "dashboard-loaded"));
      step1.urls.push(page.url());
    } catch (error) {
      step1.status = "FAIL";
      step1.details.push(`Error: ${(error as Error).message}`);
      step1.evidence.push(await screenshot(page, 1, "login-failure"));
    }
    pushResult(step1);

    const step2: StepResult = {
      id: 2,
      name: "Mi Negocio menu",
      status: "PASS",
      details: [],
      evidence: [],
      urls: [page.url()]
    };
    try {
      await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
      await waitForUi(page);
      await assertVisibleText(page, "Agregar Negocio");
      await assertVisibleText(page, "Administrar Negocios");
      step2.details.push("Mi Negocio menu expanded and both submenu options are visible.");
      step2.evidence.push(await screenshot(page, 2, "mi-negocio-menu-expanded"));
    } catch (error) {
      step2.status = "FAIL";
      step2.details.push(`Error: ${(error as Error).message}`);
      step2.evidence.push(await screenshot(page, 2, "mi-negocio-menu-failure"));
    }
    pushResult(step2);

    const step3: StepResult = {
      id: 3,
      name: "Agregar Negocio modal",
      status: "PASS",
      details: [],
      evidence: [],
      urls: [page.url()]
    };
    try {
      await clickByVisibleText(page, ["Agregar Negocio"]);
      await waitForUi(page);

      await assertVisibleText(page, "Crear Nuevo Negocio");
      const nameInput = await firstVisible([
        page.getByLabel("Nombre del Negocio").first(),
        page.getByPlaceholder("Nombre del Negocio").first()
      ]);
      expect(nameInput, "Nombre del Negocio field should be visible.").not.toBeNull();
      await assertVisibleText(page, "Tienes 2 de 3 negocios");
      await assertVisibleText(page, "Cancelar");
      await assertVisibleText(page, "Crear Negocio");
      step3.details.push("Modal validations passed.");
      step3.evidence.push(await screenshot(page, 3, "agregar-negocio-modal"));

      if (nameInput && (await isVisible(nameInput))) {
        await nameInput.fill("Negocio Prueba Automatización");
        step3.details.push("Typed optional business name.");
      }
      await clickByVisibleText(page, ["Cancelar"]);
      await waitForUi(page);
      step3.details.push("Closed modal with Cancelar.");
    } catch (error) {
      step3.status = "FAIL";
      step3.details.push(`Error: ${(error as Error).message}`);
      step3.evidence.push(await screenshot(page, 3, "agregar-negocio-modal-failure"));
    }
    pushResult(step3);

    const step4: StepResult = {
      id: 4,
      name: "Administrar Negocios view",
      status: "PASS",
      details: [],
      evidence: [],
      urls: []
    };
    try {
      const adminOption = getTextLocator(page, "Administrar Negocios").first();
      if (!(await adminOption.isVisible().catch(() => false))) {
        await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
        await waitForUi(page);
      }
      await clickByVisibleText(page, ["Administrar Negocios"]);
      await waitForUi(page);

      await assertVisibleText(page, "Información General");
      await assertVisibleText(page, "Detalles de la Cuenta");
      await assertVisibleText(page, "Tus Negocios");
      await assertVisibleText(page, "Sección Legal");

      step4.details.push("Account page sections validated.");
      step4.evidence.push(await screenshot(page, 4, "administrar-negocios-full-page"));
      step4.urls.push(page.url());
    } catch (error) {
      step4.status = "FAIL";
      step4.details.push(`Error: ${(error as Error).message}`);
      step4.evidence.push(await screenshot(page, 4, "administrar-negocios-failure"));
    }
    pushResult(step4);

    const step5: StepResult = {
      id: 5,
      name: "Información General",
      status: "PASS",
      details: [],
      evidence: [],
      urls: [page.url()]
    };
    try {
      await assertVisibleText(page, "BUSINESS PLAN");
      await assertVisibleText(page, "Cambiar Plan");

      const emailPattern = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
      const emailLocator = page.getByText(emailPattern).first();
      await expect(emailLocator).toBeVisible();

      const infoSection = getTextLocator(page, "Información General").first().locator("xpath=ancestor::*[self::section or self::div][1]");
      const sectionText = await infoSection.innerText().catch(() => "");
      const lines = sectionText
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean)
        .filter((line) => !line.includes("@") && !line.includes("BUSINESS PLAN") && !line.includes("Cambiar Plan"));
      const hasLikelyName = lines.some((line) => /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(line));
      expect(hasLikelyName, "Expected user name to be visible in Información General.").toBeTruthy();

      step5.details.push("User name, user email, plan, and Cambiar Plan are visible.");
    } catch (error) {
      step5.status = "FAIL";
      step5.details.push(`Error: ${(error as Error).message}`);
    }
    pushResult(step5);

    const step6: StepResult = {
      id: 6,
      name: "Detalles de la Cuenta",
      status: "PASS",
      details: [],
      evidence: [],
      urls: [page.url()]
    };
    try {
      await assertVisibleText(page, "Cuenta creada");
      await assertVisibleText(page, "Estado activo");
      await assertVisibleText(page, "Idioma seleccionado");
      step6.details.push("Account details section fields are visible.");
    } catch (error) {
      step6.status = "FAIL";
      step6.details.push(`Error: ${(error as Error).message}`);
    }
    pushResult(step6);

    const step7: StepResult = {
      id: 7,
      name: "Tus Negocios",
      status: "PASS",
      details: [],
      evidence: [],
      urls: [page.url()]
    };
    try {
      await assertVisibleText(page, "Tus Negocios");
      await assertVisibleText(page, "Agregar Negocio");
      await assertVisibleText(page, "Tienes 2 de 3 negocios");
      step7.details.push("Business list callout and add button are visible.");
    } catch (error) {
      step7.status = "FAIL";
      step7.details.push(`Error: ${(error as Error).message}`);
    }
    pushResult(step7);

    const validateLegalLink = async (
      stepId: number,
      stepName: string,
      linkText: string,
      headingText: string
    ): Promise<StepResult> => {
      const step: StepResult = {
        id: stepId,
        name: stepName,
        status: "PASS",
        details: [],
        evidence: [],
        urls: []
      };

      try {
        const openInSameTab = getTextLocator(page, linkText).first();
        const currentPageUrl = page.url();

        const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
        await openInSameTab.click();
        await waitForUi(page);

        const popup = await popupPromise;
        const targetPage = popup ?? page;
        await targetPage.waitForLoadState("domcontentloaded");
        await targetPage.waitForTimeout(600);

        await expect(targetPage.getByRole("heading", { name: headingText, exact: false }).first()).toBeVisible();
        const bodyText = (await targetPage.locator("body").innerText()).trim();
        expect(bodyText.length, `${headingText} page should contain legal body content.`).toBeGreaterThan(120);
        step.details.push(`Validated ${headingText} heading and legal content text.`);

        const evidencePath = await screenshot(targetPage, stepId, `${headingText}-page`);
        step.evidence.push(evidencePath);
        step.urls.push(targetPage.url());

        if (popup) {
          await popup.close();
          await page.bringToFront();
        } else if (page.url() !== currentPageUrl) {
          await page.goBack({ waitUntil: "domcontentloaded" });
          await waitForUi(page);
        }
      } catch (error) {
        step.status = "FAIL";
        step.details.push(`Error: ${(error as Error).message}`);
        step.evidence.push(await screenshot(page, stepId, `${headingText}-failure`));
      }

      return step;
    };

    pushResult(await validateLegalLink(8, "Términos y Condiciones", "Términos y Condiciones", "Términos y Condiciones"));
    pushResult(await validateLegalLink(9, "Política de Privacidad", "Política de Privacidad", "Política de Privacidad"));

    const summary = Object.fromEntries(
      reportFields.map((field) => {
        const result = results.find((item) => item.name === field || (field === "Login" && item.id === 1));
        return [field, result?.status ?? "FAIL"];
      })
    );

    const report = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      appUrl,
      summary,
      steps: results
    };

    const reportPath = path.join(outputDir, runId, "final-report.json");
    fs.mkdirSync(path.dirname(reportPath), { recursive: true });
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");
    test.info().annotations.push({ type: "final-report", description: reportPath });

    const failedSteps = results.filter((step) => step.status === "FAIL").map((step) => `${step.id}: ${step.name}`);
    expect(
      failedSteps,
      failedSteps.length > 0 ? `Failed steps: ${failedSteps.join(", ")}. See ${reportPath}` : "All steps passed."
    ).toHaveLength(0);
  });
});
