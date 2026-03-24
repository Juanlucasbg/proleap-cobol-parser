import { expect, Locator, Page, test } from "@playwright/test";
import fs from "fs";
import path from "path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  id: number;
  name: string;
  status: StepStatus;
  details: string[];
  evidence: string[];
};

type FinalReport = {
  testName: string;
  startedAt: string;
  finishedAt?: string;
  currentUrlAtStart?: string;
  finalApplicationUrl?: string;
  externalUrls: Record<string, string>;
  steps: StepResult[];
  summary: Record<string, StepStatus>;
};

const CHECKPOINT_DIR = path.resolve(process.cwd(), "artifacts", "checkpoints");
const REPORT_DIR = path.resolve(process.cwd(), "artifacts", "reports");
const TEST_NAME = "saleads_mi_negocio_full_test";
const BUSINESS_NAME = "Negocio Prueba Automatizacion";
const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

const WAIT_UI_MS = 1500;
const NAVIGATION_TIMEOUT_MS = 45000;

async function waitUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(WAIT_UI_MS);
}

async function waitUiForPage(p: Page): Promise<void> {
  await p.waitForLoadState("domcontentloaded");
  await p.waitForTimeout(WAIT_UI_MS);
}

async function completeGooglePopupAndReturn(appPage: Page, popup: Page): Promise<void> {
  await waitUiForPage(popup);
  const popupAccount = popup.getByText(GOOGLE_ACCOUNT, { exact: false }).first();
  if (await popupAccount.isVisible().catch(() => false)) {
    await popupAccount.click();
    await waitUiForPage(popup);
  }

  // OAuth flows often close this window automatically after account selection.
  if (!popup.isClosed()) {
    await popup.waitForEvent("close", { timeout: 15000 }).catch(() => undefined);
  }
  if (!popup.isClosed()) {
    await popup.close().catch(() => undefined);
  }
  await appPage.bringToFront();
  await waitUi(appPage);
}

async function getVisibleBusinessNameInput(page: Page): Promise<Locator> {
  const candidates: Locator[] = [
    page.getByLabel("Nombre del Negocio").first(),
    page.getByPlaceholder("Nombre del Negocio").first(),
    page.locator('input[name*="negocio"]').first(),
    page.locator('input[id*="negocio"]').first(),
  ];

  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }
  throw new Error("Input 'Nombre del Negocio' was not visible in modal.");
}

function textVariants(value: string): string[] {
  const normalized = value.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  if (normalized === value) {
    return [value];
  }
  return [value, normalized];
}

async function expectAnyVisibleTextWithTimeout(
  page: Page,
  values: string[],
  timeoutMs: number,
): Promise<void> {
  let lastError: Error | null = null;
  for (const value of values) {
    try {
      await expect(page.getByText(value, { exact: false }).first()).toBeVisible({
        timeout: timeoutMs,
      });
      return;
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));
    }
  }
  throw lastError ?? new Error(`None of the expected texts were visible: ${values.join(", ")}`);
}

function ensureDirs(): void {
  fs.mkdirSync(CHECKPOINT_DIR, { recursive: true });
  fs.mkdirSync(REPORT_DIR, { recursive: true });
}

function slug(value: string): string {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function step(
  id: number,
  name: string,
  status: StepStatus = "PASS",
  details: string[] = [],
  evidence: string[] = [],
): StepResult {
  return { id, name, status, details, evidence };
}

async function screenshot(page: Page, label: string, fullPage = false): Promise<string> {
  ensureDirs();
  const file = `${String(Date.now())}-${slug(label)}.png`;
  const output = path.join(CHECKPOINT_DIR, file);
  await page.screenshot({ path: output, fullPage });
  return output;
}

function getSummary(steps: StepResult[]): Record<string, StepStatus> {
  return {
    Login: steps.find((s) => s.id === 1)?.status ?? "FAIL",
    "Mi Negocio menu": steps.find((s) => s.id === 2)?.status ?? "FAIL",
    "Agregar Negocio modal": steps.find((s) => s.id === 3)?.status ?? "FAIL",
    "Administrar Negocios view": steps.find((s) => s.id === 4)?.status ?? "FAIL",
    "Información General": steps.find((s) => s.id === 5)?.status ?? "FAIL",
    "Detalles de la Cuenta": steps.find((s) => s.id === 6)?.status ?? "FAIL",
    "Tus Negocios": steps.find((s) => s.id === 7)?.status ?? "FAIL",
    "Términos y Condiciones": steps.find((s) => s.id === 8)?.status ?? "FAIL",
    "Política de Privacidad": steps.find((s) => s.id === 9)?.status ?? "FAIL",
  };
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const byRoleButton = page.getByRole("button", { name: text });
  if (await byRoleButton.first().isVisible().catch(() => false)) {
    await byRoleButton.first().click();
    await waitUi(page);
    return;
  }

  const byRoleLink = page.getByRole("link", { name: text });
  if (await byRoleLink.first().isVisible().catch(() => false)) {
    await byRoleLink.first().click();
    await waitUi(page);
    return;
  }

  const genericText = page.getByText(text, { exact: false });
  await expect(genericText.first()).toBeVisible({ timeout: NAVIGATION_TIMEOUT_MS });
  await genericText.first().click();
  await waitUi(page);
}

async function openLegalLink(
  page: Page,
  linkTexts: string[],
): Promise<{ url: string; screenshotPath: string }> {
  const appTab = page;

  let linkLocator = page.getByText(linkTexts[0], { exact: false }).first();
  for (const linkText of linkTexts) {
    const roleLocator = page.getByRole("link", { name: linkText }).first();
    if (await roleLocator.isVisible().catch(() => false)) {
      linkLocator = roleLocator;
      break;
    }
    const textLocator = page.getByText(linkText, { exact: false }).first();
    if (await textLocator.isVisible().catch(() => false)) {
      linkLocator = textLocator;
      break;
    }
  }

  const [maybePopup] = await Promise.all([
    page.waitForEvent("popup", { timeout: 5000 }).catch(() => null),
    linkLocator.click(),
  ]);

  if (maybePopup) {
    await waitUiForPage(maybePopup);
    await expectAnyVisibleTextWithTimeout(maybePopup, linkTexts, NAVIGATION_TIMEOUT_MS);
    const legalContentVisible = await maybePopup
      .locator("main, article, section, p")
      .first()
      .isVisible()
      .catch(() => false);
    expect(legalContentVisible).toBeTruthy();
    const shot = await screenshot(maybePopup, linkTexts[0], true);
    const url = maybePopup.url();
    await maybePopup.close();
    await appTab.bringToFront();
    await waitUi(appTab);
    return { url, screenshotPath: shot };
  }

  await waitUi(page);
  await expectAnyVisibleTextWithTimeout(page, linkTexts, NAVIGATION_TIMEOUT_MS);
  const legalContentVisible = await page
    .locator("main, article, section, p")
    .first()
    .isVisible()
    .catch(() => false);
  expect(legalContentVisible).toBeTruthy();
  const shot = await screenshot(page, linkTexts[0], true);
  const url = page.url();
  await page.goBack().catch(() => undefined);
  await waitUi(page);
  return { url, screenshotPath: shot };
}

test.describe(TEST_NAME, () => {
  test("Login via Google and validate Mi Negocio workflow", async ({ page }) => {
    ensureDirs();

    const report: FinalReport = {
      testName: TEST_NAME,
      startedAt: new Date().toISOString(),
      currentUrlAtStart: page.url(),
      externalUrls: {},
      steps: [],
      summary: {},
    };

    let failedStep = 0;
    try {
      // Step 1: Login with Google
      const s1 = step(1, "Login with Google");
      const providedLoginUrl = process.env.SALEADS_LOGIN_URL?.trim();
      if (page.url() === "about:blank") {
        if (!providedLoginUrl) {
          throw new Error(
            "Browser started at about:blank. Set SALEADS_LOGIN_URL or start the test from the SaleADS login page.",
          );
        }
        await page.goto(providedLoginUrl, { waitUntil: "domcontentloaded" });
      }
      await waitUi(page);
      report.currentUrlAtStart = page.url();

      const signInCandidates = [
        page.getByRole("button", { name: /sign in with google/i }).first(),
        page.getByRole("button", { name: /google/i }).first(),
        page.getByText(/sign in with google/i).first(),
      ];

      let signInClicked = false;
      let googlePopup: Page | null = null;
      for (const candidate of signInCandidates) {
        if (await candidate.isVisible().catch(() => false)) {
          const [popup] = await Promise.all([
            page.waitForEvent("popup", { timeout: 7000 }).catch(() => null),
            candidate.click(),
          ]);
          googlePopup = popup;
          signInClicked = true;
          break;
        }
      }
      if (!signInClicked) {
        throw new Error("Google login button was not found on current page.");
      }
      await waitUi(page);

      // Handle optional Google account selector popup or same-page account choice.
      const accountOption = page.getByText(GOOGLE_ACCOUNT, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitUi(page);
      } else {
        const popup = googlePopup ?? (await page.waitForEvent("popup", { timeout: 7000 }).catch(() => null));
        if (popup) {
          await completeGooglePopupAndReturn(page, popup);
        }
      }

      await expect(page.locator("aside")).toBeVisible({ timeout: NAVIGATION_TIMEOUT_MS });
      s1.details.push("Main application interface visible.");
      s1.details.push("Left sidebar navigation is visible.");
      s1.evidence.push(await screenshot(page, "dashboard-loaded", true));
      report.steps.push(s1);

      // Step 2: Open Mi Negocio menu
      const s2 = step(2, "Open Mi Negocio menu");
      const negocioVisible = await page.getByText("Negocio", { exact: false }).first().isVisible().catch(() => false);
      if (negocioVisible) {
        await clickByVisibleText(page, "Negocio");
      }
      await clickByVisibleText(page, "Mi Negocio");
      await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expect(
        page.getByText("Administrar Negocios", { exact: false }).first(),
      ).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      s2.details.push("Mi Negocio submenu expanded.");
      s2.details.push("'Agregar Negocio' visible.");
      s2.details.push("'Administrar Negocios' visible.");
      s2.evidence.push(await screenshot(page, "mi-negocio-expanded", true));
      report.steps.push(s2);

      // Step 3: Validate Agregar Negocio modal
      const s3 = step(3, "Validate Agregar Negocio modal");
      await clickByVisibleText(page, "Agregar Negocio");
      await expect(page.getByText("Crear Nuevo Negocio", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      const businessNameField = await getVisibleBusinessNameInput(page);
      await expect(businessNameField).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await businessNameField.fill(BUSINESS_NAME);
      s3.evidence.push(await screenshot(page, "agregar-negocio-modal", true));
      await clickByVisibleText(page, "Cancelar");
      s3.details.push("Modal title and required controls validated.");
      s3.details.push("Optional field typing performed and modal cancelled.");
      report.steps.push(s3);

      // Step 4: Open Administrar Negocios
      const s4 = step(4, "Open Administrar Negocios");
      if (!(await page.getByText("Administrar Negocios", { exact: false }).first().isVisible().catch(() => false))) {
        await clickByVisibleText(page, "Mi Negocio");
      }
      await clickByVisibleText(page, "Administrar Negocios");
      await expectAnyVisibleTextWithTimeout(page, textVariants("Información General"), NAVIGATION_TIMEOUT_MS);
      await expect(page.getByText("Detalles de la Cuenta", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expectAnyVisibleTextWithTimeout(page, textVariants("Sección Legal"), NAVIGATION_TIMEOUT_MS);
      s4.details.push("Account page sections are visible.");
      s4.evidence.push(await screenshot(page, "administrar-negocios", true));
      report.steps.push(s4);

      // Step 5: Validate Informacion General
      const s5 = step(5, "Validate Informacion General");
      await expect(page.getByText("BUSINESS PLAN", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expect(page.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      // Name and email can vary in selector shape, use resilient regex/text check.
      await expect(page.locator("text=/@/").first()).toBeVisible({ timeout: NAVIGATION_TIMEOUT_MS });
      const hasVisibleHeading = await page
        .getByText(/informacion general/i)
        .first()
        .isVisible()
        .catch(() => false);
      const hasVisibleName = await page
        .locator("h1, h2, h3, [data-testid*=name], [class*=name]")
        .first()
        .isVisible()
        .catch(() => false);
      expect(hasVisibleHeading || hasVisibleName).toBeTruthy();
      s5.details.push("User email is visible.");
      s5.details.push("User name or heading context is visible.");
      s5.details.push("Plan and action button are visible.");
      report.steps.push(s5);

      // Step 6: Validate Detalles de la Cuenta
      const s6 = step(6, "Validate Detalles de la Cuenta");
      await expect(page.getByText("Cuenta creada", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expect(page.getByText("Estado activo", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expect(page.getByText("Idioma seleccionado", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      s6.details.push("Detalles de la Cuenta labels are visible.");
      report.steps.push(s6);

      // Step 7: Validate Tus Negocios
      const s7 = step(7, "Validate Tus Negocios");
      await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expect(page.getByRole("button", { name: "Agregar Negocio" }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false }).first()).toBeVisible({
        timeout: NAVIGATION_TIMEOUT_MS,
      });
      s7.details.push("Business list area and limits text are visible.");
      report.steps.push(s7);

      // Step 8: Validate Terminos y Condiciones
      const s8 = step(8, "Validate Terminos y Condiciones");
      const termsResult = await openLegalLink(page, textVariants("Términos y Condiciones"));
      report.externalUrls["Términos y Condiciones"] = termsResult.url;
      s8.evidence.push(termsResult.screenshotPath);
      if (termsResult.url.length > 0) {
        s8.details.push("Legal link opened.");
      }
      s8.details.push("Heading and legal content text are visible.");
      s8.details.push(`Final URL: ${termsResult.url}`);
      report.steps.push(s8);

      // Step 9: Validate Politica de Privacidad
      const s9 = step(9, "Validate Politica de Privacidad");
      const policyResult = await openLegalLink(page, textVariants("Política de Privacidad"));
      report.externalUrls["Política de Privacidad"] = policyResult.url;
      s9.evidence.push(policyResult.screenshotPath);
      if (policyResult.url.length > 0) {
        s9.details.push("Legal link opened.");
      }
      s9.details.push("Heading and legal content text are visible.");
      s9.details.push(`Final URL: ${policyResult.url}`);
      report.steps.push(s9);
    } catch (error) {
      failedStep = report.steps.length + 1;
      const failed = step(
        failedStep,
        `Failure at step ${failedStep}`,
        "FAIL",
        [error instanceof Error ? error.message : String(error)],
      );
      failed.evidence.push(await screenshot(page, `failure-step-${failedStep}`, true));
      report.steps.push(failed);
      throw error;
    } finally {
      report.finishedAt = new Date().toISOString();
      report.finalApplicationUrl = page.url();
      report.summary = getSummary(report.steps);
      ensureDirs();
      const outputPath = path.join(REPORT_DIR, `${TEST_NAME}.json`);
      fs.writeFileSync(outputPath, JSON.stringify(report, null, 2), "utf-8");
    }
  });
});
