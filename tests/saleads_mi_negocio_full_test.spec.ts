import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";
import fs from "fs";
import path from "path";
import * as dotenv from "dotenv";

dotenv.config();

type StepResult = {
  key:
    | "Login"
    | "Mi Negocio menu"
    | "Agregar Negocio modal"
    | "Administrar Negocios view"
    | "Información General"
    | "Detalles de la Cuenta"
    | "Tus Negocios"
    | "Términos y Condiciones"
    | "Política de Privacidad";
  status: "PASS" | "FAIL";
  details: string;
};

const results: StepResult[] = [];
const REPORT_KEYS: StepResult["key"][] = [
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
const evidenceDir = path.resolve(process.cwd(), "artifacts", "saleads-mi-negocio");
const reportPath = path.join(evidenceDir, "final-report.json");
const legalUrlPath = path.join(evidenceDir, "legal-urls.json");

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login Google y validar flujo completo Mi Negocio", async ({ page, context }) => {
    await ensureEvidenceDir();
    await navigateToLoginIfNeeded(page);

    let blockedReason = "";
    let appPage = page;
    try {
      appPage = await stepLoginWithGoogle(page, context);
      await stepOpenMiNegocioMenu(appPage);
      await stepValidateAgregarNegocioModal(appPage);
      await stepOpenAdministrarNegocios(appPage);
      await stepValidateInformacionGeneral(appPage);
      await stepValidateDetallesCuenta(appPage);
      await stepValidateTusNegocios(appPage);
      await stepValidateTerminos(context, appPage);
      await stepValidatePrivacidad(context, appPage);
    } catch (error) {
      blockedReason = error instanceof Error ? error.message : String(error);
      throw error;
    } finally {
      ensureCompleteFinalReport(blockedReason);
    }
  });
});

test.afterAll(async () => {
  await ensureEvidenceDir();
  fs.writeFileSync(reportPath, JSON.stringify(results, null, 2), "utf8");
  // Console summary requested by step 10.
  // eslint-disable-next-line no-console
  console.log("\n=== Final Report (PASS/FAIL) ===");
  for (const entry of results) {
    // eslint-disable-next-line no-console
    console.log(`${entry.key}: ${entry.status} - ${entry.details}`);
  }
});

async function stepLoginWithGoogle(page: Page, context: BrowserContext): Promise<Page> {
  try {
    const signInButton = await firstVisible([
      page.getByRole("button", { name: /sign in with google|continuar con google|iniciar con google/i }),
      page.getByText(/sign in with google|continuar con google|iniciar con google/i),
      page.getByRole("button", { name: /google/i })
    ]);
    await expect(signInButton).toBeVisible();

    const authPagePromise = waitForPossibleNewTab(context, page);
    await clickAndWait(page, signInButton);
    const authPage = await authPagePromise;

    const accountCell = authPage.getByText("juanlucasbarbiergarzon@gmail.com");
    if (await accountCell.first().isVisible({ timeout: 4000 }).catch(() => false)) {
      await clickAndWait(authPage, accountCell.first());
    }

    const appPage = await waitForMainAppInterface(context, page, authPage);
    const sidebar = await firstVisible([
      appPage.locator("aside"),
      appPage.getByRole("navigation"),
      appPage.getByText(/negocio|mi negocio|dashboard/i).first()
    ]);
    await expect(sidebar).toBeVisible({ timeout: 30000 });
    await waitForUiLoad(appPage);
    await checkpoint(appPage, "01-dashboard-loaded.png", true);

    recordPass("Login", "Main app and left navigation are visible.");
    return appPage;
  } catch (error) {
    recordFail("Login", error);
    throw error;
  }
}

async function stepOpenMiNegocioMenu(page: Page): Promise<void> {
  try {
    const negocioSection = await firstVisible([
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i)
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await firstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();
    await checkpoint(page, "02-mi-negocio-menu-expanded.png");

    recordPass("Mi Negocio menu", "Submenu expanded with Agregar/Administrar options visible.");
  } catch (error) {
    recordFail("Mi Negocio menu", error);
    throw error;
  }
}

async function stepValidateAgregarNegocioModal(page: Page): Promise<void> {
  try {
    const agregarNegocioMenuItem = page.getByText(/agregar negocio/i).first();
    await clickAndWait(page, agregarNegocioMenuItem);

    const modalTitle = page.getByText(/crear nuevo negocio/i);
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i))).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await checkpoint(page, "03-agregar-negocio-modal.png");

    const businessNameField = page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i));
    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }));

    recordPass("Agregar Negocio modal", "Modal validated with required controls and usage text.");
  } catch (error) {
    recordFail("Agregar Negocio modal", error);
    throw error;
  }
}

async function stepOpenAdministrarNegocios(page: Page): Promise<void> {
  try {
    const miNegocioEntry = await firstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);

    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      await clickAndWait(page, miNegocioEntry);
    }

    await clickAndWait(page, page.getByText(/administrar negocios/i).first());
    await waitForUiLoad(page);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();
    await checkpoint(page, "04-administrar-negocios-page.png", true);

    recordPass("Administrar Negocios view", "Account page and all main sections are visible.");
  } catch (error) {
    recordFail("Administrar Negocios view", error);
    throw error;
  }
}

async function stepValidateInformacionGeneral(page: Page): Promise<void> {
  try {
    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/@/)).toBeVisible();
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const userNameCandidate = await firstVisible([
      page.locator("section").filter({ hasText: /informaci[oó]n general/i }).locator("h1, h2, h3, p, span, div").first(),
      page.getByText(/juan|lucas/i).first()
    ]);
    await expect(userNameCandidate).toBeVisible();

    recordPass("Información General", "User name/email, BUSINESS PLAN, and Cambiar Plan are visible.");
  } catch (error) {
    recordFail("Información General", error);
    throw error;
  }
}

async function stepValidateDetallesCuenta(page: Page): Promise<void> {
  try {
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();

    recordPass("Detalles de la Cuenta", "Cuenta creada, Estado activo and Idioma seleccionado are visible.");
  } catch (error) {
    recordFail("Detalles de la Cuenta", error);
    throw error;
  }
}

async function stepValidateTusNegocios(page: Page): Promise<void> {
  try {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const businessListVisible = await firstVisible([
      page.locator("table").first(),
      page.locator("ul").filter({ hasText: /negocio/i }).first(),
      page.locator("div").filter({ hasText: /tus negocios/i }).first()
    ]);
    await expect(businessListVisible).toBeVisible();

    recordPass("Tus Negocios", "Business list, add button and usage limit text are visible.");
  } catch (error) {
    recordFail("Tus Negocios", error);
    throw error;
  }
}

async function stepValidateTerminos(context: BrowserContext, appPage: Page): Promise<void> {
  const legalUrls = readLegalUrls();
  try {
    const legalPromise = waitForPossibleNewTab(context, appPage);
    await clickAndWait(appPage, appPage.getByText(/t[ée]rminos y condiciones/i).first());
    const legalPage = await legalPromise;

    await expect(legalPage.getByText(/t[ée]rminos y condiciones/i)).toBeVisible({ timeout: 30000 });
    await expect(legalPage.locator("main, article, section, body").first()).toBeVisible();
    await checkpoint(legalPage, "05-terminos-y-condiciones.png");
    legalUrls.terminosYCondiciones = legalPage.url();

    if (legalPage !== appPage) {
      await legalPage.close();
    }
    await appPage.bringToFront();
    await waitForUiLoad(appPage);

    writeLegalUrls(legalUrls);
    recordPass("Términos y Condiciones", `Legal page visible. URL: ${legalUrls.terminosYCondiciones}`);
  } catch (error) {
    writeLegalUrls(legalUrls);
    recordFail("Términos y Condiciones", error);
    throw error;
  }
}

async function stepValidatePrivacidad(context: BrowserContext, appPage: Page): Promise<void> {
  const legalUrls = readLegalUrls();
  try {
    const legalPromise = waitForPossibleNewTab(context, appPage);
    await clickAndWait(appPage, appPage.getByText(/pol[ií]tica de privacidad/i).first());
    const legalPage = await legalPromise;

    await expect(legalPage.getByText(/pol[ií]tica de privacidad/i)).toBeVisible({ timeout: 30000 });
    await expect(legalPage.locator("main, article, section, body").first()).toBeVisible();
    await checkpoint(legalPage, "06-politica-de-privacidad.png");
    legalUrls.politicaDePrivacidad = legalPage.url();

    if (legalPage !== appPage) {
      await legalPage.close();
    }
    await appPage.bringToFront();
    await waitForUiLoad(appPage);

    writeLegalUrls(legalUrls);
    recordPass("Política de Privacidad", `Legal page visible. URL: ${legalUrls.politicaDePrivacidad}`);
  } catch (error) {
    writeLegalUrls(legalUrls);
    recordFail("Política de Privacidad", error);
    throw error;
  }
}

async function clickAndWait(page: Page, target: Locator): Promise<void> {
  await expect(target).toBeVisible({ timeout: 15000 });
  await target.click();
  await waitForUiLoad(page);
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  // Small buffer for React/Vue transitions after each click.
  await page.waitForTimeout(800);
}

async function firstVisible(candidates: Locator[]): Promise<Locator> {
  for (const locator of candidates) {
    if (await locator.first().isVisible({ timeout: 2500 }).catch(() => false)) {
      return locator.first();
    }
  }
  return candidates[0].first();
}

async function waitForPossibleNewTab(context: BrowserContext, page: Page): Promise<Page> {
  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  const pagePromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    return popup;
  }
  const maybePage = await pagePromise;
  if (maybePage) {
    await maybePage.waitForLoadState("domcontentloaded");
    return maybePage;
  }
  await page.waitForLoadState("domcontentloaded");
  return page;
}

async function waitForMainAppInterface(
  context: BrowserContext,
  originalPage: Page,
  authPage: Page
): Promise<Page> {
  const deadline = Date.now() + 45_000;

  while (Date.now() < deadline) {
    const candidates = [...new Set<Page>([originalPage, authPage, ...context.pages()])];
    for (const candidate of candidates) {
      const navVisible = await candidate
        .locator("aside, nav")
        .first()
        .isVisible({ timeout: 1000 })
        .catch(() => false);
      const textVisible = await candidate
        .getByText(/negocio|mi negocio|dashboard/i)
        .first()
        .isVisible({ timeout: 1000 })
        .catch(() => false);
      if (navVisible || textVisible) {
        return candidate;
      }
    }
    await originalPage.waitForTimeout(1000);
  }

  throw new Error("Main application interface did not appear after Google sign-in.");
}

async function checkpoint(page: Page, filename: string, fullPage = false): Promise<void> {
  await ensureEvidenceDir();
  await page.screenshot({
    path: path.join(evidenceDir, filename),
    fullPage
  });
}

async function ensureEvidenceDir(): Promise<void> {
  await fs.promises.mkdir(evidenceDir, { recursive: true });
}

function recordPass(key: StepResult["key"], details: string): void {
  upsertResult({ key, status: "PASS", details });
}

function recordFail(key: StepResult["key"], error: unknown): void {
  const message = error instanceof Error ? error.message : String(error);
  upsertResult({ key, status: "FAIL", details: message });
}

function upsertResult(next: StepResult): void {
  const index = results.findIndex((entry) => entry.key === next.key);
  if (index >= 0) {
    results[index] = next;
  } else {
    results.push(next);
  }
}

function ensureCompleteFinalReport(blockedReason: string): void {
  for (const key of REPORT_KEYS) {
    const exists = results.some((entry) => entry.key === key);
    if (!exists) {
      const reason = blockedReason
        ? `Not executed after previous failure: ${blockedReason}`
        : "Not executed.";
      upsertResult({ key, status: "FAIL", details: reason });
    }
  }
}

async function navigateToLoginIfNeeded(page: Page): Promise<void> {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL?.trim() ||
    process.env.SALEADS_BASE_URL?.trim() ||
    process.env.BASE_URL?.trim() ||
    "";

  const isBlank = page.url() === "about:blank";
  if (isBlank && !loginUrl) {
    throw new Error(
      "No login URL available. Set SALEADS_LOGIN_URL, SALEADS_BASE_URL, or BASE_URL to run this test."
    );
  }

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  }
}

type LegalUrls = {
  terminosYCondiciones: string;
  politicaDePrivacidad: string;
};

function readLegalUrls(): LegalUrls {
  if (fs.existsSync(legalUrlPath)) {
    return JSON.parse(fs.readFileSync(legalUrlPath, "utf8")) as LegalUrls;
  }

  return { terminosYCondiciones: "", politicaDePrivacidad: "" };
}

function writeLegalUrls(urls: LegalUrls): void {
  fs.writeFileSync(legalUrlPath, JSON.stringify(urls, null, 2), "utf8");
}
