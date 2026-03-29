import { mkdir, writeFile } from "node:fs/promises";
import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

interface StepResult {
  name: string;
  status: StepStatus;
  details?: string;
}

const screenshotDir = "artifacts/mi-negocio";
const reportPath = "artifacts/mi-negocio/final-report.json";
const providedBaseUrl = process.env.SALEADS_BASE_URL;

async function waitForUiAfterAction(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await page.waitForTimeout(500);
}

async function clickVisibleByText(page: Page, candidates: string[]): Promise<Locator> {
  for (const candidate of candidates) {
    const nameRegex = new RegExp(candidate, "i");
    const byRoleButton = page.getByRole("button", { name: nameRegex }).first();
    if (await byRoleButton.isVisible().catch(() => false)) {
      await byRoleButton.click();
      await waitForUiAfterAction(page);
      return byRoleButton;
    }

    const byRoleLink = page.getByRole("link", { name: nameRegex }).first();
    if (await byRoleLink.isVisible().catch(() => false)) {
      await byRoleLink.click();
      await waitForUiAfterAction(page);
      return byRoleLink;
    }

    const byText = page.getByText(nameRegex).first();
    if (await byText.isVisible().catch(() => false)) {
      await byText.click();
      await waitForUiAfterAction(page);
      return byText;
    }
  }

  throw new Error(`No visible clickable element found for: ${candidates.join(", ")}`);
}

async function waitForAnyTextVisible(page: Page, texts: string[]): Promise<void> {
  for (const text of texts) {
    const locator = page.getByText(new RegExp(text, "i")).first();
    if (await locator.isVisible().catch(() => false)) {
      return;
    }
  }

  await expect(page.getByText(new RegExp(texts[0], "i")).first()).toBeVisible();
}

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function getFirstVisible(locatorCandidates: Locator[]): Promise<Locator> {
  for (const locator of locatorCandidates) {
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  throw new Error("None of the provided locator candidates is visible.");
}

async function runStep(
  stepName: string,
  results: StepResult[],
  action: () => Promise<void>
): Promise<void> {
  await test.step(stepName, async () => {
    try {
      await action();
      results.push({ name: stepName, status: "PASS" });
    } catch (error) {
      results.push({ name: stepName, status: "FAIL", details: toErrorMessage(error) });
    }
  });
}

async function selectGoogleAccountIfPresent(signInPage: Page): Promise<void> {
  const accountLocator = signInPage.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
  if (await accountLocator.isVisible().catch(() => false)) {
    await accountLocator.click();
    await waitForUiAfterAction(signInPage);
  }
}

async function clickGoogleLogin(page: Page, context: BrowserContext): Promise<void> {
  const popupPromise = context.waitForEvent("page", { timeout: 6_000 }).catch(() => null);
  await clickVisibleByText(page, [
    "Sign in with Google",
    "Iniciar sesión con Google",
    "Continuar con Google",
    "Google",
    "Login",
    "Iniciar sesión",
  ]);

  const googlePopup = await popupPromise;
  if (!googlePopup) {
    await selectGoogleAccountIfPresent(page);
    return;
  }

  await googlePopup.waitForLoadState("domcontentloaded");
  await selectGoogleAccountIfPresent(googlePopup);

  await Promise.race([
    googlePopup.waitForEvent("close").catch(() => undefined),
    page.waitForLoadState("domcontentloaded").catch(() => undefined),
    page.waitForTimeout(5_000),
  ]);
}

async function validateLegalLink(
  context: BrowserContext,
  appPage: Page,
  linkLabel: string,
  headingExpected: string,
  screenshotName: string
): Promise<string> {
  const appUrlBefore = appPage.url();
  const clickable = await getFirstVisible([
    appPage.getByRole("link", { name: new RegExp(linkLabel, "i") }).first(),
    appPage.getByText(new RegExp(linkLabel, "i")).first(),
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 5_000 }).catch(() => null);
  await clickable.click();
  const newTab = await popupPromise;

  if (newTab) {
    await newTab.waitForLoadState("domcontentloaded");
    await newTab.waitForLoadState("networkidle").catch(() => undefined);
    await expect(newTab.getByText(new RegExp(headingExpected, "i")).first()).toBeVisible();
    await expect(newTab.locator("body")).toContainText(/\S+/);
    await newTab.screenshot({ path: `${screenshotDir}/${screenshotName}`, fullPage: true });
    const url = newTab.url();
    await newTab.close();
    await appPage.bringToFront();
    await waitForUiAfterAction(appPage);
    return url;
  }

  await waitForUiAfterAction(appPage);
  await expect(appPage).not.toHaveURL(appUrlBefore);
  await expect(appPage.getByText(new RegExp(headingExpected, "i")).first()).toBeVisible();
  await expect(appPage.locator("body")).toContainText(/\S+/);
  await appPage.screenshot({ path: `${screenshotDir}/${screenshotName}`, fullPage: true });
  const url = appPage.url();

  if (appPage.url() !== appUrlBefore) {
    await appPage.goBack().catch(() => undefined);
    await waitForUiAfterAction(appPage);
  }

  return url;
}

test.describe("SaleADS Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }) => {
    const results: StepResult[] = [];
    const legalUrls: Record<string, string> = {};

    await mkdir(screenshotDir, { recursive: true });

    await runStep("Login", results, async () => {
      if (providedBaseUrl) {
        await page.goto(providedBaseUrl, { waitUntil: "domcontentloaded" });
        await waitForUiAfterAction(page);
      } else {
        await expect(page.url(), "Provide SALEADS_BASE_URL when the session starts on about:blank").not.toBe(
          "about:blank"
        );
      }

      await clickGoogleLogin(page, context);
      await waitForAnyTextVisible(page, ["Negocio", "Dashboard", "Mi Negocio", "Administrar Negocios"]);
      await expect(page.locator("aside").first()).toBeVisible();
      await page.screenshot({ path: `${screenshotDir}/01-dashboard-loaded.png`, fullPage: true });
    });

    await runStep("Mi Negocio menu", results, async () => {
      await expect(page.getByText(/Negocio/i).first()).toBeVisible();
      await clickVisibleByText(page, ["Mi Negocio"]);
      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await page.screenshot({ path: `${screenshotDir}/02-mi-negocio-expanded.png`, fullPage: true });
    });

    await runStep("Agregar Negocio modal", results, async () => {
      await clickVisibleByText(page, ["Agregar Negocio"]);

      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
      const businessNameInput = await getFirstVisible([
        page.getByLabel(/Nombre del Negocio/i).first(),
        page.getByPlaceholder(/Nombre del Negocio/i).first(),
      ]);
      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();
      await page.screenshot({ path: `${screenshotDir}/03-agregar-negocio-modal.png`, fullPage: true });

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickVisibleByText(page, ["Cancelar"]);
    });

    await runStep("Administrar Negocios view", results, async () => {
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
        await clickVisibleByText(page, ["Mi Negocio"]);
      }

      await clickVisibleByText(page, ["Administrar Negocios"]);
      await expect(page.getByText(/Información General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
      await page.screenshot({ path: `${screenshotDir}/04-administrar-negocios.png`, fullPage: true });
    });

    await runStep("Información General", results, async () => {
      await expect(page.getByText(/Información General/i).first()).toBeVisible();
      await expect(page.getByText(/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/).first()).toBeVisible();
      await expect(page.getByText(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-z]{2,}/i).first()).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    });

    await runStep("Detalles de la Cuenta", results, async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });

    await runStep("Tus Negocios", results, async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.locator("section,div").filter({ hasText: /Tus Negocios/i }).first()).toBeVisible();
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
      await getFirstVisible([
        page.getByRole("button", { name: /Agregar Negocio/i }).first(),
        page.getByRole("link", { name: /Agregar Negocio/i }).first(),
        page.getByText(/Agregar Negocio/i).first(),
      ]);
    });

    await runStep("Términos y Condiciones", results, async () => {
      legalUrls["Términos y Condiciones"] = await validateLegalLink(
        context,
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "08-terminos-y-condiciones.png"
      );
    });

    await runStep("Política de Privacidad", results, async () => {
      legalUrls["Política de Privacidad"] = await validateLegalLink(
        context,
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "09-politica-de-privacidad.png"
      );
    });

    await test.step("Final report", async () => {
      const report = {
        Login: results.find((x) => x.name === "Login")?.status ?? "FAIL",
        "Mi Negocio menu": results.find((x) => x.name === "Mi Negocio menu")?.status ?? "FAIL",
        "Agregar Negocio modal": results.find((x) => x.name === "Agregar Negocio modal")?.status ?? "FAIL",
        "Administrar Negocios view": results.find((x) => x.name === "Administrar Negocios view")?.status ?? "FAIL",
        "Información General": results.find((x) => x.name === "Información General")?.status ?? "FAIL",
        "Detalles de la Cuenta": results.find((x) => x.name === "Detalles de la Cuenta")?.status ?? "FAIL",
        "Tus Negocios": results.find((x) => x.name === "Tus Negocios")?.status ?? "FAIL",
        "Términos y Condiciones": results.find((x) => x.name === "Términos y Condiciones")?.status ?? "FAIL",
        "Política de Privacidad": results.find((x) => x.name === "Política de Privacidad")?.status ?? "FAIL",
        legalUrls,
        stepResults: results,
      };

      test.info().annotations.push({
        type: "final-report",
        description: JSON.stringify(report),
      });

      await writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
      console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
      console.log(JSON.stringify(report, null, 2));
    });

    const failures = results.filter((result) => result.status === "FAIL");
    expect(
      failures,
      `Workflow completed with failures: ${failures.map((x) => `${x.name}: ${x.details ?? "Unknown error"}`).join(" | ")}`
    ).toEqual([]);
  });
});
