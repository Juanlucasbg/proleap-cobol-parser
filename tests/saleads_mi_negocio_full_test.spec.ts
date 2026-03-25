import { mkdirSync } from "node:fs";
import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";
type StepResult = {
  name: string;
  status: StepStatus;
  details?: string;
  evidence?: string[];
};

const SCREENSHOT_DIR = "test-results/saleads-mi-negocio";

async function waitForUiIdle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {
    // Some SPA transitions do not fully settle on networkidle; best effort only.
  });
  await page.waitForTimeout(500);
}

function nowStamp(): string {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function screenshot(page: Page, suffix: string, fullPage = false): Promise<string> {
  mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const file = `${SCREENSHOT_DIR}/${nowStamp()}-${suffix}.png`;
  await page.screenshot({ path: file, fullPage });
  return file;
}

function normalizeText(value: string | null): string {
  return (value ?? "").replace(/\s+/g, " ").trim();
}

async function firstVisible(page: Page, candidates: string[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const locator = page.getByText(candidate, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }
  return null;
}

async function clickByVisibleText(page: Page, texts: string[]): Promise<void> {
  for (const t of texts) {
    const roleLink = page.getByRole("link", { name: new RegExp(t, "i") }).first();
    if (await roleLink.isVisible().catch(() => false)) {
      await roleLink.click();
      await waitForUiIdle(page);
      return;
    }
  }

  const textLocator = await firstVisible(page, texts);
  if (textLocator) {
    await textLocator.click();
    await waitForUiIdle(page);
    return;
  }

  for (const t of texts) {
    const roleButton = page.getByRole("button", { name: new RegExp(t, "i") }).first();
    if (await roleButton.isVisible().catch(() => false)) {
      await roleButton.click();
      await waitForUiIdle(page);
      return;
    }
  }

  throw new Error(`No clickable element found for texts: ${texts.join(", ")}`);
}

async function selectGoogleAccountIfVisible(targetPage: Page): Promise<boolean> {
  const accountOption = targetPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUiIdle(targetPage);
    return true;
  }
  return false;
}

async function assertVisible(page: Page, text: string): Promise<void> {
  const locator = page.getByText(text, { exact: false }).first();
  await expect(locator, `Expected visible text: ${text}`).toBeVisible({ timeout: 15000 });
}

async function captureNewPageOrNavigate(context: BrowserContext, page: Page, action: () => Promise<void>): Promise<Page> {
  const originalUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await action();
  await waitForUiIdle(page);

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle").catch(() => undefined);
    return popup;
  }

  if (page.url() !== originalUrl) {
    return page;
  }

  // Fallback for slower redirects in same tab.
  await page.waitForURL((url) => url.toString() !== originalUrl, { timeout: 8000 }).catch(() => undefined);
  return page;
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio module workflow", async ({ page, context }) => {
    const results: StepResult[] = [];
    const evidence: string[] = [];

    const setResult = (name: string, status: StepStatus, details?: string, stepEvidence: string[] = []) => {
      results.push({ name, status, details, evidence: stepEvidence.length ? stepEvidence : undefined });
    };

    // Step 1: Login with Google
    try {
      await waitForUiIdle(page);
      const oauthPopupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickByVisibleText(page, ["Sign in with Google", "Iniciar sesión con Google", "Login con Google", "Login"]);

      const oauthPopup = await oauthPopupPromise;
      if (oauthPopup) {
        await oauthPopup.waitForLoadState("domcontentloaded");
        await selectGoogleAccountIfVisible(oauthPopup);
      } else {
        await selectGoogleAccountIfVisible(page);
      }

      const sidebar = page.locator("aside, nav").first();
      await expect(sidebar, "Left sidebar should be visible after login").toBeVisible({ timeout: 30000 });
      const dashboardShot = await screenshot(page, "step1-dashboard-loaded", true);
      evidence.push(dashboardShot);
      setResult("Login", "PASS", "Main app interface and sidebar visible after Google login.", [dashboardShot]);
    } catch (error) {
      const failShot = await screenshot(page, "step1-login-fail", true);
      evidence.push(failShot);
      setResult("Login", "FAIL", String(error), [failShot]);
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByVisibleText(page, ["Mi Negocio"]);
      await waitForUiIdle(page);

      await assertVisible(page, "Agregar Negocio");
      await assertVisible(page, "Administrar Negocios");

      const menuShot = await screenshot(page, "step2-mi-negocio-expanded");
      evidence.push(menuShot);
      setResult("Mi Negocio menu", "PASS", "Mi Negocio expanded with required submenu items visible.", [menuShot]);
    } catch (error) {
      const failShot = await screenshot(page, "step2-mi-negocio-fail", true);
      evidence.push(failShot);
      setResult("Mi Negocio menu", "FAIL", String(error), [failShot]);
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, ["Agregar Negocio"]);
      await waitForUiIdle(page);

      await assertVisible(page, "Crear Nuevo Negocio");
      const inputByPlaceholder = page.getByPlaceholder("Nombre del Negocio").first();
      const inputByLabel = page.getByLabel("Nombre del Negocio").first();
      const businessNameInput = (await inputByPlaceholder.isVisible().catch(() => false))
        ? inputByPlaceholder
        : inputByLabel;
      await expect(businessNameInput).toBeVisible({ timeout: 15000 });
      await assertVisible(page, "Tienes 2 de 3 negocios");
      await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: 10000 });

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      const modalShot = await screenshot(page, "step3-agregar-negocio-modal");
      evidence.push(modalShot);

      await clickByVisibleText(page, ["Cancelar"]);
      setResult("Agregar Negocio modal", "PASS", "Modal validated and closed with Cancelar.", [modalShot]);
    } catch (error) {
      const failShot = await screenshot(page, "step3-modal-fail", true);
      evidence.push(failShot);
      setResult("Agregar Negocio modal", "FAIL", String(error), [failShot]);
    }

    // Step 4: Open Administrar Negocios
    try {
      if (!(await page.getByText("Administrar Negocios", { exact: false }).first().isVisible().catch(() => false))) {
        await clickByVisibleText(page, ["Mi Negocio"]);
      }
      await clickByVisibleText(page, ["Administrar Negocios"]);
      await waitForUiIdle(page);

      await assertVisible(page, "Información General");
      await assertVisible(page, "Detalles de la Cuenta");
      await assertVisible(page, "Tus Negocios");
      await assertVisible(page, "Sección Legal");

      const adminShot = await screenshot(page, "step4-administrar-negocios", true);
      evidence.push(adminShot);
      setResult("Administrar Negocios view", "PASS", "All account sections are visible.", [adminShot]);
    } catch (error) {
      const failShot = await screenshot(page, "step4-admin-fail", true);
      evidence.push(failShot);
      setResult("Administrar Negocios view", "FAIL", String(error), [failShot]);
    }

    // Step 5: Validate Información General
    try {
      const userNameCandidate = page.locator("section,div").filter({ hasText: "Información General" }).first();
      await expect(userNameCandidate).toBeVisible({ timeout: 15000 });
      await assertVisible(page, "BUSINESS PLAN");
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 10000 });

      // User name/email validation is kept flexible to support multiple environments.
      const visibleText = normalizeText(await userNameCandidate.textContent());
      expect(visibleText.length).toBeGreaterThan(0);
      await expect(page.getByText(/@/, { exact: false }).first()).toBeVisible({ timeout: 10000 });
      setResult("Información General", "PASS", "Name/email, BUSINESS PLAN and Cambiar Plan validated.");
    } catch (error) {
      setResult("Información General", "FAIL", String(error));
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await assertVisible(page, "Cuenta creada");
      await assertVisible(page, "Estado activo");
      await assertVisible(page, "Idioma seleccionado");
      setResult("Detalles de la Cuenta", "PASS", "Required account details labels are visible.");
    } catch (error) {
      setResult("Detalles de la Cuenta", "FAIL", String(error));
    }

    // Step 7: Validate Tus Negocios
    try {
      await assertVisible(page, "Tus Negocios");
      await assertVisible(page, "Agregar Negocio");
      await assertVisible(page, "Tienes 2 de 3 negocios");
      setResult("Tus Negocios", "PASS", "Business list context and controls visible.");
    } catch (error) {
      setResult("Tus Negocios", "FAIL", String(error));
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const legalPage = await captureNewPageOrNavigate(context, page, async () => {
        await clickByVisibleText(page, ["Términos y Condiciones"]);
      });

      await expect(legalPage.getByText("Términos y Condiciones", { exact: false }).first()).toBeVisible({
        timeout: 15000
      });
      const legalText = normalizeText(await legalPage.locator("body").innerText());
      expect(legalText.length).toBeGreaterThan(50);

      const termsShot = await screenshot(legalPage, "step8-terminos");
      evidence.push(termsShot);
      setResult("Términos y Condiciones", "PASS", `Validated legal page. URL: ${legalPage.url()}`, [termsShot]);

      if (legalPage !== page) {
        await legalPage.close();
        await page.bringToFront();
        await waitForUiIdle(page);
      } else {
        await page.goBack().catch(() => undefined);
        await waitForUiIdle(page);
      }
    } catch (error) {
      const failShot = await screenshot(page, "step8-terminos-fail", true);
      evidence.push(failShot);
      setResult("Términos y Condiciones", "FAIL", String(error), [failShot]);
    }

    // Step 9: Validate Política de Privacidad
    try {
      const privacyPage = await captureNewPageOrNavigate(context, page, async () => {
        await clickByVisibleText(page, ["Política de Privacidad"]);
      });

      await expect(privacyPage.getByText("Política de Privacidad", { exact: false }).first()).toBeVisible({
        timeout: 15000
      });
      const privacyText = normalizeText(await privacyPage.locator("body").innerText());
      expect(privacyText.length).toBeGreaterThan(50);

      const privacyShot = await screenshot(privacyPage, "step9-politica-privacidad");
      evidence.push(privacyShot);
      setResult("Política de Privacidad", "PASS", `Validated privacy page. URL: ${privacyPage.url()}`, [privacyShot]);

      if (privacyPage !== page) {
        await privacyPage.close();
        await page.bringToFront();
        await waitForUiIdle(page);
      } else {
        await page.goBack().catch(() => undefined);
        await waitForUiIdle(page);
      }
    } catch (error) {
      const failShot = await screenshot(page, "step9-privacy-fail", true);
      evidence.push(failShot);
      setResult("Política de Privacidad", "FAIL", String(error), [failShot]);
    }

    // Step 10: Final report
    const report = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      summary: results,
      evidence
    };

    await test.info().attach("final-report.json", {
      body: Buffer.from(JSON.stringify(report, null, 2), "utf-8"),
      contentType: "application/json"
    });

    // Explicit summary output for CI logs.
    // eslint-disable-next-line no-console
    console.log("saleads_mi_negocio_full_test report:", JSON.stringify(report));

    const failed = results.filter((r) => r.status === "FAIL");
    expect(
      failed,
      `One or more workflow validations failed: ${failed.map((f) => `${f.name} => ${f.details ?? "Unknown error"}`).join(" | ")}`
    ).toHaveLength(0);
  });
});
