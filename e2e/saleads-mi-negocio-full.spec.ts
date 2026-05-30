import { mkdirSync } from "node:fs";
import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

const GOOGLE_ACCOUNT_EMAIL = process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";
const SALEADS_START_URL = process.env.SALEADS_START_URL;

const REPORT_FIELDS: ReportField[] = [
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

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function findFirstVisible(page: Page, candidates: Locator[], timeoutMs = 15_000): Promise<Locator> {
  const timeoutAt = Date.now() + timeoutMs;

  while (Date.now() < timeoutAt) {
    for (const candidate of candidates) {
      const first = candidate.first();

      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error("No visible element was found for provided candidates.");
}

async function ensureLoginPageLoaded(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    await waitForUiToSettle(page);
    return;
  }

  if (!SALEADS_START_URL) {
    throw new Error(
      "Browser started on about:blank. Provide SALEADS_START_URL or open the SaleADS login page before running."
    );
  }

  await page.goto(SALEADS_START_URL, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);
}

async function clickAndWait(target: Locator, page: Page): Promise<void> {
  await expect(target).toBeVisible({ timeout: 20_000 });
  await target.click();
  await waitForUiToSettle(page);
}

async function selectGoogleAccountIfPrompted(targetPage: Page): Promise<void> {
  const accountOption = targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();

  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUiToSettle(targetPage);
  }
}

async function clickLegalLinkAndValidate(params: {
  appPage: Page;
  context: BrowserContext;
  linkLabel: string;
  heading: string;
  screenshotName: string;
}): Promise<string> {
  const { appPage, context, linkLabel, heading, screenshotName } = params;

  const legalLink = await findFirstVisible(appPage, [
    appPage.getByRole("link", { name: new RegExp(escapeRegex(linkLabel), "i") }),
    appPage.getByRole("button", { name: new RegExp(escapeRegex(linkLabel), "i") }),
    appPage.getByText(new RegExp(escapeRegex(linkLabel), "i"))
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await clickAndWait(legalLink, appPage);
  const popup = await popupPromise;

  const legalPage = popup ?? appPage;
  await waitForUiToSettle(legalPage);

  await expect(legalPage.getByText(new RegExp(escapeRegex(heading), "i")).first()).toBeVisible({ timeout: 20_000 });
  await expect(legalPage.locator("body")).toContainText(/\S+/, { timeout: 20_000 });

  await legalPage.screenshot({ path: `test-results/${screenshotName}`, fullPage: true });
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close({ runBeforeUnload: true }).catch(() => undefined);
    await appPage.bringToFront();
    await waitForUiToSettle(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiToSettle(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  mkdirSync("test-results", { recursive: true });

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])) as Record<ReportField, StepStatus>;
  const errors: string[] = [];
  const legalUrls: Record<"Términos y Condiciones" | "Política de Privacidad", string> = {
    "Términos y Condiciones": "",
    "Política de Privacidad": ""
  };

  const runStep = async (reportField: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[reportField] = "PASS";
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      errors.push(`${reportField}: ${message}`);
    }
  };

  await runStep("Login", async () => {
    await ensureLoginPageLoaded(page);

    const googleLoginButton = await findFirstVisible(page, [
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|inicia sesión con google|continuar con google/i)
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(googleLoginButton, page);

    const popup = await popupPromise;
    const authPage = popup ?? page;
    await selectGoogleAccountIfPrompted(authPage);

    if (popup) {
      await Promise.race([
        popup.waitForEvent("close", { timeout: 25_000 }),
        page.waitForLoadState("domcontentloaded", { timeout: 25_000 })
      ]).catch(() => undefined);
    }

    await waitForUiToSettle(page);

    const sidebar = await findFirstVisible(page, [page.locator("aside"), page.getByRole("navigation")], 45_000);
    await expect(sidebar).toBeVisible({ timeout: 45_000 });
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 45_000 });

    await page.screenshot({ path: "test-results/01-dashboard-loaded.png", fullPage: true });
  });

  await runStep("Mi Negocio menu", async () => {
    const miNegocio = await findFirstVisible(page, [
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);
    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20_000 });

    await page.screenshot({ path: "test-results/02-mi-negocio-expanded.png", fullPage: true });
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusiness = await findFirstVisible(page, [
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await clickAndWait(addBusiness, page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible({ timeout: 20_000 });

    const businessNameInput = await findFirstVisible(page, [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator('input[name*="negocio"], input[name*="Negocio"]'),
      page.locator("input[type='text']")
    ]);
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");

    await page.screenshot({ path: "test-results/03-agregar-negocio-modal.png", fullPage: true });

    const cancelButton = page.getByRole("button", { name: /^Cancelar$/i }).first();
    await clickAndWait(cancelButton, page);
  });

  await runStep("Administrar Negocios view", async () => {
    const adminNegociosVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);

    if (!adminNegociosVisible) {
      const miNegocio = await findFirstVisible(page, [
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ]);
      await clickAndWait(miNegocio, page);
    }

    const administrarNegocios = await findFirstVisible(page, [
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 20_000 });

    await page.screenshot({ path: "test-results/04-administrar-negocios-page.png", fullPage: true });
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 20_000 });

    const pageText = await page.locator("body").innerText();

    if (!pageText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)) {
      throw new Error("No user email was detected in the account view.");
    }

    if (!pageText.match(/nombre|usuario|perfil|bienvenido/i)) {
      throw new Error("No user name indicator was detected in Información General.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    const addBusinessButton = await findFirstVisible(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await expect(addBusinessButton).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });

    const businessRows = page.locator(
      "li, tr, [role='listitem'], [class*='business'], [class*='Business'], [data-testid*='business'], [data-testid*='negocio']"
    );
    const count = await businessRows.count();

    if (count < 1) {
      throw new Error("Could not detect a visible business list element in Tus Negocios.");
    }
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await clickLegalLinkAndValidate({
      appPage: page,
      context,
      linkLabel: "Términos y Condiciones",
      heading: "Términos y Condiciones",
      screenshotName: "05-terminos-y-condiciones.png"
    });
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await clickLegalLinkAndValidate({
      appPage: page,
      context,
      linkLabel: "Política de Privacidad",
      heading: "Política de Privacidad",
      screenshotName: "06-politica-de-privacidad.png"
    });
  });

  const finalReport = {
    ...report,
    finalUrls: legalUrls
  };

  const finalReportBody = JSON.stringify(finalReport, null, 2);
  await testInfo.attach("final-report", {
    body: finalReportBody,
    contentType: "application/json"
  });

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT", finalReportBody);

  if (errors.length > 0) {
    throw new Error(`Workflow validation failed:\n${errors.join("\n")}`);
  }
});
