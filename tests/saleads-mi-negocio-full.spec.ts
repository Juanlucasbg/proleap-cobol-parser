import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepId =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepResult = {
  status: "PASS" | "FAIL";
  details: string;
  evidence?: string[];
  finalUrl?: string;
};

const REPORT_DIR = path.join(process.cwd(), "artifacts", "saleads-mi-negocio");
const SCREENSHOT_DIR = path.join(REPORT_DIR, "screenshots");
const REPORT_FILE = path.join(REPORT_DIR, "final-report.json");
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const APP_WAIT_TIMEOUT = 60_000;

const stepResults: Record<StepId, StepResult> = {
  Login: { status: "FAIL", details: "Not executed." },
  "Mi Negocio menu": { status: "FAIL", details: "Not executed." },
  "Agregar Negocio modal": { status: "FAIL", details: "Not executed." },
  "Administrar Negocios view": { status: "FAIL", details: "Not executed." },
  "Información General": { status: "FAIL", details: "Not executed." },
  "Detalles de la Cuenta": { status: "FAIL", details: "Not executed." },
  "Tus Negocios": { status: "FAIL", details: "Not executed." },
  "Términos y Condiciones": { status: "FAIL", details: "Not executed." },
  "Política de Privacidad": { status: "FAIL", details: "Not executed." },
};

function ensureArtifactFolders(): void {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function sanitizeForFilename(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function capture(page: Page, checkpointName: string, fullPage = false): Promise<string> {
  ensureArtifactFolders();
  const file = `${new Date().toISOString().replace(/[:.]/g, "-")}-${sanitizeForFilename(checkpointName)}.png`;
  const absolutePath = path.join(SCREENSHOT_DIR, file);
  await page.screenshot({ path: absolutePath, fullPage });
  return absolutePath;
}

function markPass(step: StepId, details: string, evidence: string[] = [], finalUrl?: string): void {
  stepResults[step] = { status: "PASS", details, evidence, finalUrl };
}

function markFail(step: StepId, details: string, evidence: string[] = [], finalUrl?: string): void {
  stepResults[step] = { status: "FAIL", details, evidence, finalUrl };
}

function writeReport(): void {
  ensureArtifactFolders();
  const payload = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    reportFields: [
      "Login",
      "Mi Negocio menu",
      "Agregar Negocio modal",
      "Administrar Negocios view",
      "Información General",
      "Detalles de la Cuenta",
      "Tus Negocios",
      "Términos y Condiciones",
      "Política de Privacidad",
    ],
    results: stepResults,
  };
  fs.writeFileSync(REPORT_FILE, JSON.stringify(payload, null, 2), "utf-8");
}

function makeVisibleTextRegex(text: string): RegExp {
  const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`^\\s*${escaped}\\s*$`, "i");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: APP_WAIT_TIMEOUT }).catch(() => {
    // Some SPAs keep long polling; domcontentloaded is enough fallback.
  });
}

async function clickTextButton(page: Page, text: string): Promise<void> {
  const roleButton = page.getByRole("button", { name: makeVisibleTextRegex(text) });
  if (await roleButton.first().isVisible().catch(() => false)) {
    await roleButton.first().click();
    await waitForUi(page);
    return;
  }

  const roleLink = page.getByRole("link", { name: makeVisibleTextRegex(text) });
  if (await roleLink.first().isVisible().catch(() => false)) {
    await roleLink.first().click();
    await waitForUi(page);
    return;
  }

  const textLocator = page.getByText(makeVisibleTextRegex(text));
  await expect(textLocator.first(), `Expected clickable text "${text}"`).toBeVisible();
  await textLocator.first().click();
  await waitForUi(page);
}

async function ensureMainInterfaceLoaded(page: Page): Promise<void> {
  const sidebarCandidates = [
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator('[data-testid*="sidebar"]'),
    page.locator('[class*="sidebar"]'),
  ];
  for (const candidate of sidebarCandidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      return;
    }
  }
  throw new Error("Left sidebar navigation is not visible after login.");
}

async function maybeSelectGoogleAccount(page: Page, context: BrowserContext): Promise<void> {
  const candidates: Array<Page | null> = [page];
  for (const p of context.pages()) {
    if (!candidates.includes(p)) {
      candidates.push(p);
    }
  }

  const accountRegex = new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
  for (const candidate of candidates) {
    if (!candidate) {
      continue;
    }
    const locator = candidate.getByText(accountRegex);
    if (await locator.first().isVisible().catch(() => false)) {
      await locator.first().click();
      await waitForUi(candidate);
      return;
    }
  }
}

async function findLegalLinkInSection(page: Page, linkText: string): Promise<Locator> {
  const legalSection = page
    .locator("section,div,article")
    .filter({
      has: page.getByText(/secci[oó]n legal|legal/i),
    })
    .first();

  if (await legalSection.isVisible().catch(() => false)) {
    const linkInSection = legalSection.getByRole("link", { name: new RegExp(linkText, "i") }).first();
    if (await linkInSection.isVisible().catch(() => false)) {
      return linkInSection;
    }
    const textInSection = legalSection.getByText(new RegExp(`^\\s*${linkText}\\s*$`, "i")).first();
    if (await textInSection.isVisible().catch(() => false)) {
      return textInSection;
    }
  }

  const globalLink = page.getByRole("link", { name: new RegExp(linkText, "i") }).first();
  if (await globalLink.isVisible().catch(() => false)) {
    return globalLink;
  }
  return page.getByText(new RegExp(`^\\s*${linkText}\\s*$`, "i")).first();
}

async function openAndValidateLegalDocument(
  page: Page,
  context: BrowserContext,
  linkText: "Términos y Condiciones" | "Política de Privacidad",
  headingRegex: RegExp,
): Promise<{ screenshot: string; finalUrl: string }> {
  const appPage = page;
  const legalLink = await findLegalLinkInSection(appPage, linkText);
  await expect(legalLink, `${linkText} link should be visible`).toBeVisible();

  const existingPages = new Set(context.pages());

  await Promise.all([
    legalLink.click(),
    appPage.waitForTimeout(300),
  ]);

  await waitForUi(appPage);

  let targetPage: Page = appPage;
  const newPage = context.pages().find((p) => !existingPages.has(p));
  if (newPage) {
    targetPage = newPage;
    await waitForUi(targetPage);
  }

  const heading = targetPage.getByText(headingRegex).first();
  await expect(heading, `Expected legal heading for ${linkText}`).toBeVisible({ timeout: APP_WAIT_TIMEOUT });

  const bodyCandidates = [
    targetPage.locator("main p"),
    targetPage.locator("article p"),
    targetPage.locator("p"),
  ];
  let hasBodyText = false;
  for (const candidate of bodyCandidates) {
    if ((await candidate.count()) > 0) {
      hasBodyText = true;
      break;
    }
  }
  expect(hasBodyText, "Expected legal content text to be visible").toBeTruthy();

  const screenshot = await capture(targetPage, linkText, true);
  const finalUrl = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack().catch(() => undefined);
    await waitForUi(appPage);
  }

  return { screenshot, finalUrl };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context }) => {
    test.setTimeout(300_000);
    ensureArtifactFolders();

    try {
      await waitForUi(page);

      // Step 1: Login with Google.
      try {
        const signInCandidates = [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Google",
        ];
        let clicked = false;
        for (const candidate of signInCandidates) {
          const button = page
            .locator("button, a, div")
            .filter({ hasText: new RegExp(candidate, "i") })
            .first();
          if (await button.isVisible().catch(() => false)) {
            await button.click();
            clicked = true;
            break;
          }
        }
        if (!clicked) {
          // If already logged in and on app shell, continue.
          await ensureMainInterfaceLoaded(page);
        } else {
          await maybeSelectGoogleAccount(page, context);
          await ensureMainInterfaceLoaded(page);
        }

        const dashboardShot = await capture(page, "dashboard-loaded");
        markPass("Login", "Main application and left sidebar are visible after login.", [dashboardShot]);
      } catch (error) {
        const failureShot = await capture(page, "login-failure");
        markFail("Login", (error as Error).message, [failureShot], page.url());
        throw error;
      }

      // Step 2: Open Mi Negocio menu.
      try {
        await clickTextButton(page, "Negocio");
        await clickTextButton(page, "Mi Negocio");
        await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
        await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
        const menuShot = await capture(page, "mi-negocio-expanded-menu");
        markPass("Mi Negocio menu", "Mi Negocio submenu expanded with required options.", [menuShot]);
      } catch (error) {
        const failureShot = await capture(page, "mi-negocio-menu-failure");
        markFail("Mi Negocio menu", (error as Error).message, [failureShot], page.url());
        throw error;
      }

      // Step 3: Validate Agregar Negocio modal.
      try {
        await clickTextButton(page, "Agregar Negocio");
        await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: APP_WAIT_TIMEOUT });
        await expect(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i))).toBeVisible();
        await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
        await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
        await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

        const modalShot = await capture(page, "agregar-negocio-modal");

        const nameField = page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i)).first();
        if (await nameField.isVisible().catch(() => false)) {
          await nameField.click();
          await nameField.fill("Negocio Prueba Automatización");
        }
        await clickTextButton(page, "Cancelar");
        markPass("Agregar Negocio modal", "Crear Nuevo Negocio modal displayed expected content.", [modalShot]);
      } catch (error) {
        const failureShot = await capture(page, "agregar-negocio-modal-failure");
        markFail("Agregar Negocio modal", (error as Error).message, [failureShot], page.url());
        throw error;
      }

      // Step 4: Open Administrar Negocios.
      try {
        if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
          await clickTextButton(page, "Mi Negocio");
        }
        await clickTextButton(page, "Administrar Negocios");
        await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible({ timeout: APP_WAIT_TIMEOUT });
        await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: APP_WAIT_TIMEOUT });
        await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: APP_WAIT_TIMEOUT });
        await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible({ timeout: APP_WAIT_TIMEOUT });
        const accountShot = await capture(page, "administrar-negocios-view", true);
        markPass("Administrar Negocios view", "Account administration page has all expected sections.", [accountShot]);
      } catch (error) {
        const failureShot = await capture(page, "administrar-negocios-failure");
        markFail("Administrar Negocios view", (error as Error).message, [failureShot], page.url());
        throw error;
      }

      // Step 5: Validate Información General.
      try {
        await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
        await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
        const emailRegex = /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i;
        await expect(page.getByText(emailRegex).first()).toBeVisible();

        // Name heuristic: non-empty text block near Informacion General that is not known labels.
        const infoSection = page
          .locator("section,div,article")
          .filter({ has: page.getByText(/Informaci[oó]n General/i) })
          .first();
        const visibleText = (await infoSection.innerText()).trim();
        expect(visibleText.length).toBeGreaterThan(20);
        markPass("Información General", "User name/email and plan information are visible.");
      } catch (error) {
        const failureShot = await capture(page, "informacion-general-failure");
        markFail("Información General", (error as Error).message, [failureShot], page.url());
        throw error;
      }

      // Step 6: Validate Detalles de la Cuenta.
      try {
        await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
        await expect(page.getByText(/Estado activo/i)).toBeVisible();
        await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
        markPass("Detalles de la Cuenta", "Detalles de la Cuenta fields are visible.");
      } catch (error) {
        const failureShot = await capture(page, "detalles-cuenta-failure");
        markFail("Detalles de la Cuenta", (error as Error).message, [failureShot], page.url());
        throw error;
      }

      // Step 7: Validate Tus Negocios.
      try {
        await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
        await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
        await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
        const section = page
          .locator("section,div,article")
          .filter({ has: page.getByText(/Tus Negocios/i) })
          .first();
        const listItems = await section.locator("li, tr, [role='listitem']").count();
        expect(listItems).toBeGreaterThanOrEqual(1);
        markPass("Tus Negocios", "Business list and capacity text are visible.");
      } catch (error) {
        const failureShot = await capture(page, "tus-negocios-failure");
        markFail("Tus Negocios", (error as Error).message, [failureShot], page.url());
        throw error;
      }

      // Step 8: Validate Términos y Condiciones.
      try {
        const { screenshot, finalUrl } = await openAndValidateLegalDocument(
          page,
          context,
          "Términos y Condiciones",
          /T[eé]rminos y Condiciones/i,
        );
        markPass("Términos y Condiciones", "Legal page opened and content was validated.", [screenshot], finalUrl);
      } catch (error) {
        const failureShot = await capture(page, "terminos-condiciones-failure");
        markFail("Términos y Condiciones", (error as Error).message, [failureShot], page.url());
        throw error;
      }

      // Step 9: Validate Política de Privacidad.
      try {
        const { screenshot, finalUrl } = await openAndValidateLegalDocument(
          page,
          context,
          "Política de Privacidad",
          /Pol[ií]tica de Privacidad/i,
        );
        markPass("Política de Privacidad", "Legal page opened and content was validated.", [screenshot], finalUrl);
      } catch (error) {
        const failureShot = await capture(page, "politica-privacidad-failure");
        markFail("Política de Privacidad", (error as Error).message, [failureShot], page.url());
        throw error;
      }
    } finally {
      writeReport();
    }
  });
});
