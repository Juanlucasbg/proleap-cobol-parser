import { expect, Locator, Page, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";
type StepResult = { name: string; status: StepStatus; details: string[] };

const CHECKPOINT_DIR = "artifacts/screenshots";

function accentInsensitiveRegex(label: string): RegExp {
  const escaped = label
    .trim()
    .replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
    .replace(/\s+/g, "\\s+")
    .replace(/a/gi, "[aáàäâã]")
    .replace(/e/gi, "[eéèëê]")
    .replace(/i/gi, "[iíìïî]")
    .replace(/o/gi, "[oóòöôõ]")
    .replace(/u/gi, "[uúùüû]")
    .replace(/n/gi, "[nñ]");
  return new RegExp(escaped, "i");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(650);
}

function textLocator(page: Page, label: string): Locator {
  return page.getByText(accentInsensitiveRegex(label), { exact: false }).first();
}

async function clickVisibleText(page: Page, label: string): Promise<void> {
  const nameMatcher = accentInsensitiveRegex(label);
  const roles: ("button" | "link" | "menuitem" | "tab" | "treeitem")[] = [
    "button",
    "link",
    "menuitem",
    "tab",
    "treeitem",
  ];

  for (const role of roles) {
    const candidate = page.getByRole(role, { name: nameMatcher }).first();
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click();
      await waitForUi(page);
      return;
    }
  }

  const fallback = textLocator(page, label);
  await expect(fallback, `Expected visible text target: ${label}`).toBeVisible({ timeout: 20_000 });
  await fallback.click();
  await waitForUi(page);
}

async function expectVisibleText(page: Page, label: string, timeout = 15_000): Promise<void> {
  await expect(textLocator(page, label), `Expected visible text: ${label}`).toBeVisible({ timeout });
}

async function takeCheckpoint(page: Page, fileName: string, fullPage = false): Promise<void> {
  await page.screenshot({ path: `${CHECKPOINT_DIR}/${fileName}`, fullPage });
}

async function runStep(results: StepResult[], name: string, action: () => Promise<void>): Promise<void> {
  const result: StepResult = { name, status: "PASS", details: [] };
  try {
    await action();
  } catch (error) {
    result.status = "FAIL";
    result.details.push(error instanceof Error ? error.message : String(error));
  }
  results.push(result);
}

async function openLegalLinkAndValidate(
  page: Page,
  linkText: string,
  screenshotFile: string,
  annotationType: string,
): Promise<void> {
  const openerUrl = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickVisibleText(page, linkText);
  const popup = await popupPromise;
  const legalPage = popup ?? page;

  await legalPage.waitForLoadState("domcontentloaded");
  await expectVisibleText(legalPage, linkText, 20_000);
  await expect(legalPage.locator("body")).toContainText(/[A-Za-z]{4,}/, { timeout: 20_000 });
  await takeCheckpoint(legalPage, screenshotFile, true);

  test.info().annotations.push({ type: annotationType, description: legalPage.url() });
  // Ensure captured URLs are visible in test output artifacts.
  console.log(`${annotationType}: ${legalPage.url()}`);

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  if (page.url() !== openerUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }
}

async function completeGoogleLoginIfPrompted(page: Page): Promise<void> {
  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  const googleButton = page
    .getByRole("button", { name: /google|sign in|iniciar sesion|ingresar/i })
    .or(page.getByRole("link", { name: /google|sign in|iniciar sesion|ingresar/i }))
    .first();

  await expect(googleButton).toBeVisible({ timeout: 25_000 });
  await googleButton.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const loginSurface = popup ?? page;
  await loginSurface.waitForLoadState("domcontentloaded");

  const accountOption = loginSurface.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await loginSurface.waitForLoadState("domcontentloaded");
    await loginSurface.waitForTimeout(800);
  }

  if (popup) {
    // Popup-based OAuth can close itself or remain open; handle both safely.
    await popup.waitForTimeout(1200);
    if (!popup.isClosed()) {
      await popup.close();
    }
    await page.bringToFront();
    await waitForUi(page);
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page }) => {
    const results: StepResult[] = [];

    if (process.env.SALEADS_LOGIN_URL) {
      await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);

    // Step 1
    await runStep(results, "Login", async () => {
      await completeGoogleLoginIfPrompted(page);

      await expect(page.locator("aside,nav")).toBeVisible({ timeout: 35_000 });
      await expectVisibleText(page, "Negocio", 35_000);
      await takeCheckpoint(page, "01-dashboard-loaded.png");
    });

    // Step 2
    await runStep(results, "Mi Negocio menu", async () => {
      await clickVisibleText(page, "Negocio");
      await clickVisibleText(page, "Mi Negocio");
      await expectVisibleText(page, "Agregar Negocio");
      await expectVisibleText(page, "Administrar Negocios");
      await takeCheckpoint(page, "02-mi-negocio-expanded.png");
    });

    // Step 3
    await runStep(results, "Agregar Negocio modal", async () => {
      await clickVisibleText(page, "Agregar Negocio");
      await expectVisibleText(page, "Crear Nuevo Negocio");
      await expectVisibleText(page, "Nombre del Negocio");
      await expectVisibleText(page, "Tienes 2 de 3 negocios");
      await expectVisibleText(page, "Cancelar");
      await expectVisibleText(page, "Crear Negocio");
      await takeCheckpoint(page, "03-agregar-negocio-modal.png");

      const businessInput = page
        .getByRole("textbox", { name: accentInsensitiveRegex("Nombre del Negocio") })
        .or(page.getByPlaceholder(accentInsensitiveRegex("Nombre del Negocio")))
        .first();

      if (await businessInput.isVisible().catch(() => false)) {
        await businessInput.click();
        await businessInput.fill("Negocio Prueba Automatizacion");
      }

      await clickVisibleText(page, "Cancelar");
    });

    // Step 4
    await runStep(results, "Administrar Negocios view", async () => {
      if (!(await textLocator(page, "Administrar Negocios").isVisible().catch(() => false))) {
        await clickVisibleText(page, "Mi Negocio");
      }

      await clickVisibleText(page, "Administrar Negocios");
      await expectVisibleText(page, "Informacion General", 25_000);
      await expectVisibleText(page, "Detalles de la Cuenta");
      await expectVisibleText(page, "Tus Negocios");
      await expectVisibleText(page, "Seccion Legal");
      await takeCheckpoint(page, "04-administrar-negocios-page.png", true);
    });

    // Step 5
    await runStep(results, "Información General", async () => {
      await expectVisibleText(page, "Informacion General");
      await expect(page.locator("h1,h2,h3,h4,div,span,p").filter({ hasText: /[A-Za-z]{2,}\s+[A-Za-z]{2,}/ }).first()).toBeVisible({
        timeout: 12_000,
      });
      await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible({ timeout: 12_000 });
      await expectVisibleText(page, "BUSINESS PLAN");
      await expectVisibleText(page, "Cambiar Plan");
    });

    // Step 6
    await runStep(results, "Detalles de la Cuenta", async () => {
      await expectVisibleText(page, "Cuenta creada");
      await expectVisibleText(page, "Estado activo");
      await expectVisibleText(page, "Idioma seleccionado");
    });

    // Step 7
    await runStep(results, "Tus Negocios", async () => {
      await expectVisibleText(page, "Tus Negocios");
      await expectVisibleText(page, "Agregar Negocio");
      await expectVisibleText(page, "Tienes 2 de 3 negocios");
    });

    // Step 8
    await runStep(results, "Términos y Condiciones", async () => {
      await openLegalLinkAndValidate(
        page,
        "Terminos y Condiciones",
        "05-terminos-y-condiciones.png",
        "terminos_url",
      );
    });

    // Step 9
    await runStep(results, "Política de Privacidad", async () => {
      await openLegalLinkAndValidate(
        page,
        "Politica de Privacidad",
        "06-politica-de-privacidad.png",
        "privacidad_url",
      );
    });

    // Step 10
    const finalOrder = [
      "Login",
      "Mi Negocio menu",
      "Agregar Negocio modal",
      "Administrar Negocios view",
      "Información General",
      "Detalles de la Cuenta",
      "Tus Negocios",
      "Términos y Condiciones",
      "Política de Privacidad",
    ];
    const sorted = finalOrder.map((name) => results.find((item) => item.name === name)).filter(Boolean) as StepResult[];
    const reportLines = sorted.map((item) => `${item.name}: ${item.status}`);
    const failed = sorted.filter((item) => item.status === "FAIL");

    test.info().annotations.push({ type: "final_report", description: reportLines.join("\n") });
    console.log("Final validation report:");
    for (const line of reportLines) console.log(`- ${line}`);

    if (failed.length > 0) {
      const details = failed
        .map((item) => `${item.name}: ${item.details.join(" | ") || "Validation failed."}`)
        .join("\n");
      throw new Error(`One or more workflow validations failed:\n${details}`);
    }
  });
});
