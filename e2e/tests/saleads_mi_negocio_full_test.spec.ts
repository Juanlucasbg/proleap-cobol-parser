import { BrowserContext, expect, Page, test } from "@playwright/test";
import path from "node:path";
import { mkdirSync } from "node:fs";

type StepStatus = "PASS" | "FAIL";

type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

const BASE_URL = process.env.SALEADS_BASE_URL;
const GOOGLE_ACCOUNT = process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";
const VIEWPORT = { width: 1600, height: 1200 };
const ARTIFACT_DIR = path.resolve(__dirname, "..", "artifacts");

const report: Record<ReportKey, StepStatus> = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL"
};

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio module workflow", async ({ page, context }, testInfo) => {
    mkdirSync(ARTIFACT_DIR, { recursive: true });
    await page.setViewportSize(VIEWPORT);
    await gotoLoginPage(page);

    try {
      await stepLoginWithGoogle(page, context);
      report.Login = "PASS";

      await stepOpenMiNegocioMenu(page);
      report["Mi Negocio menu"] = "PASS";

      await stepValidateAgregarNegocioModal(page);
      report["Agregar Negocio modal"] = "PASS";

      await stepOpenAdministrarNegocios(page);
      report["Administrar Negocios view"] = "PASS";

      await stepValidateInformacionGeneral(page);
      report["Información General"] = "PASS";

      await stepValidateDetallesCuenta(page);
      report["Detalles de la Cuenta"] = "PASS";

      await stepValidateTusNegocios(page);
      report["Tus Negocios"] = "PASS";

      await stepValidateLegalLink(page, "Términos y Condiciones", /T[ée]rminos y Condiciones/i, "terminos");
      report["Términos y Condiciones"] = "PASS";

      await stepValidateLegalLink(page, "Política de Privacidad", /Pol[íi]tica de Privacidad/i, "privacidad");
      report["Política de Privacidad"] = "PASS";
    } finally {
      await testInfo.attach("final-report.json", {
        body: Buffer.from(JSON.stringify(report, null, 2), "utf8"),
        contentType: "application/json"
      });
      console.table(report);
    }
  });
});

async function gotoLoginPage(page: Page): Promise<void> {
  if (BASE_URL) {
    await page.goto(BASE_URL, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No SALEADS_BASE_URL provided and browser is on about:blank. Set SALEADS_BASE_URL or start the test from an already-open SaleADS login page."
    );
  }
  await page.waitForLoadState("networkidle");
}

async function stepLoginWithGoogle(page: Page, context: BrowserContext): Promise<void> {
  const loginTrigger = page
    .getByRole("button", { name: /iniciar sesi[oó]n con google|sign in with google|google/i })
    .or(page.getByText(/iniciar sesi[oó]n con google|sign in with google/i).first());

  await expect(loginTrigger).toBeVisible({ timeout: 45000 });

  const popupPromise = context.waitForEvent("page").catch(() => null);
  await loginTrigger.click();
  await page.waitForLoadState("networkidle");

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    const accountSelector = popup.getByText(new RegExp(GOOGLE_ACCOUNT, "i"));
    if (await accountSelector.isVisible({ timeout: 8000 }).catch(() => false)) {
      await accountSelector.click();
    }
    await popup.waitForTimeout(1000);
  } else {
    const accountSelector = page.getByText(new RegExp(GOOGLE_ACCOUNT, "i"));
    if (await accountSelector.isVisible({ timeout: 8000 }).catch(() => false)) {
      await accountSelector.click();
      await page.waitForLoadState("networkidle");
    }
  }

  await expect(page.locator("aside")).toBeVisible({ timeout: 60000 });
  await checkpoint(page, "01-dashboard-loaded");
}

async function stepOpenMiNegocioMenu(page: Page): Promise<void> {
  const sidebar = page.locator("aside");
  await expect(sidebar).toBeVisible({ timeout: 30000 });

  const negocioSection = page
    .getByRole("button", { name: /negocio/i })
    .or(page.getByText(/^negocio$/i).first());

  await expect(negocioSection).toBeVisible({ timeout: 30000 });
  await negocioSection.click();
  await page.waitForLoadState("networkidle");

  const miNegocioOption = page.getByRole("button", { name: /mi negocio/i }).or(page.getByText(/mi negocio/i).first());
  await expect(miNegocioOption).toBeVisible({ timeout: 30000 });
  await miNegocioOption.click();
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(/agregar negocio/i)).toBeVisible({ timeout: 30000 });
  await expect(page.getByText(/administrar negocios/i)).toBeVisible({ timeout: 30000 });
  await checkpoint(page, "02-mi-negocio-expanded");
}

async function stepValidateAgregarNegocioModal(page: Page): Promise<void> {
  await page.getByText(/agregar negocio/i).first().click();
  await page.waitForLoadState("networkidle");

  const modalTitle = page.getByText(/crear nuevo negocio/i);
  await expect(modalTitle).toBeVisible({ timeout: 30000 });
  const nombreInput = page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i));
  await expect(nombreInput).toBeVisible({ timeout: 30000 });
  await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 30000 });
  await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 30000 });
  await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 30000 });

  await checkpoint(page, "03-agregar-negocio-modal");

  await nombreInput.click();
  await nombreInput.fill("Negocio Prueba Automatización");
  await page.waitForTimeout(400);
  await page.getByRole("button", { name: /cancelar/i }).click();
  await page.waitForLoadState("networkidle");
}

async function stepOpenAdministrarNegocios(page: Page): Promise<void> {
  const administrarNegocios = page.getByText(/administrar negocios/i).first();
  if (!(await administrarNegocios.isVisible().catch(() => false))) {
    const miNegocioOption = page.getByRole("button", { name: /mi negocio/i }).or(page.getByText(/mi negocio/i).first());
    await miNegocioOption.click();
    await page.waitForLoadState("networkidle");
  }

  await page.getByText(/administrar negocios/i).first().click();
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 30000 });
  await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 30000 });
  await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 30000 });
  await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible({ timeout: 30000 });
  await checkpoint(page, "04-administrar-negocios-view", true);
}

async function stepValidateInformacionGeneral(page: Page): Promise<void> {
  const section = page.getByText(/informaci[oó]n general/i).first();
  await expect(section).toBeVisible({ timeout: 30000 });

  await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 30000 });
  await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible({ timeout: 30000 });

  // Validate visible user profile information with flexible patterns.
  await expect(page.locator("text=/@/").first()).toBeVisible({ timeout: 30000 });
  await expect(page.locator("h1, h2, h3, [class*='name']").first()).toBeVisible({ timeout: 30000 });
}

async function stepValidateDetallesCuenta(page: Page): Promise<void> {
  await expect(page.getByText(/cuenta creada/i)).toBeVisible({ timeout: 30000 });
  await expect(page.getByText(/estado activo|activo/i)).toBeVisible({ timeout: 30000 });
  await expect(page.getByText(/idioma seleccionado/i)).toBeVisible({ timeout: 30000 });
}

async function stepValidateTusNegocios(page: Page): Promise<void> {
  await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 30000 });
  await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 30000 });
  await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 30000 });
}

async function stepValidateLegalLink(
  page: Page,
  linkText: string,
  headingPattern: RegExp,
  slug: string
): Promise<void> {
  const link = page.getByRole("link", { name: new RegExp(linkText, "i") }).or(page.getByText(new RegExp(linkText, "i")).first());

  await expect(link).toBeVisible({ timeout: 30000 });

  const popupPromise = page.context().waitForEvent("page").catch(() => null);
  await link.click();
  await page.waitForLoadState("networkidle");

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle");
    await expect(popup.getByText(headingPattern)).toBeVisible({ timeout: 30000 });
    await expect(popup.locator("p").first()).toBeVisible({ timeout: 30000 });
    const finalUrl = popup.url();
    await checkpoint(popup, `05-${slug}-legal-page`);
    console.log(`[${linkText}] final URL: ${finalUrl}`);
    await popup.close();
    await page.bringToFront();
  } else {
    await expect(page.getByText(headingPattern)).toBeVisible({ timeout: 30000 });
    await expect(page.locator("p").first()).toBeVisible({ timeout: 30000 });
    const finalUrl = page.url();
    await checkpoint(page, `05-${slug}-legal-page`);
    console.log(`[${linkText}] final URL: ${finalUrl}`);
    await page.goBack({ waitUntil: "networkidle" }).catch(() => undefined);
  }
}

async function checkpoint(page: Page, name: string, fullPage = false): Promise<void> {
  await page.waitForLoadState("networkidle");
  await page.screenshot({
    path: path.join(ARTIFACT_DIR, `${name}.png`),
    fullPage
  });
}
