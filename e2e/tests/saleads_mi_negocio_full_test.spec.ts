import { expect, type BrowserContext, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

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

type FinalReport = Record<ReportField, StepStatus>;

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

const GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function createInitialReport(): FinalReport {
  return REPORT_FIELDS.reduce(
    (acc, current) => ({ ...acc, [current]: "FAIL" }),
    {} as FinalReport
  );
}

function runId(): string {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function toSafeFileName(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function isVisible(locator: Locator, timeout = 2_500): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function firstVisible(page: Page, locators: Locator[]): Promise<Locator> {
  for (const locator of locators) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }

  throw new Error("No visible locator matched.");
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 45_000 });
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(
  page: Page,
  artifactsDir: string,
  screenshotName: string,
  fullPage = false
): Promise<void> {
  const filePath = path.join(artifactsDir, `${screenshotName}.png`);
  await page.screenshot({ path: filePath, fullPage });
}

async function looksLikeMainApp(page: Page): Promise<boolean> {
  const sidebarSignals = [
    page.locator("aside").first(),
    page.getByRole("navigation").first(),
    page.getByText(/Negocio|Mi Negocio/i).first()
  ];

  for (const signal of sidebarSignals) {
    if (await isVisible(signal)) {
      return true;
    }
  }

  return false;
}

async function openSaleadsLoginIfNeeded(page: Page): Promise<void> {
  const envUrl = process.env.SALEADS_BASE_URL;

  if (page.url() === "about:blank" && envUrl) {
    await page.goto(envUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  if (page.url() === "about:blank" && !envUrl) {
    throw new Error(
      "Page is about:blank. Set SALEADS_BASE_URL or open SaleADS login page before running."
    );
  }
}

async function pickGoogleAccountIfVisible(candidatePage: Page, email: string): Promise<boolean> {
  const accountOption = candidatePage.getByText(email, { exact: true }).first();

  if (await isVisible(accountOption, 8_000)) {
    await accountOption.click();
    await candidatePage.waitForLoadState("domcontentloaded").catch(() => undefined);
    return true;
  }

  return false;
}

async function performGoogleLoginIfNeeded(page: Page, context: BrowserContext): Promise<void> {
  if (await looksLikeMainApp(page)) {
    return;
  }

  const loginButton = await firstVisible(page, [
    page.getByRole("button", { name: /sign in with google|continuar con google|google/i }),
    page.getByText(/sign in with google|continuar con google/i),
    page.getByText(/google/i)
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  await clickAndWait(loginButton, page);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded").catch(() => undefined);
    await pickGoogleAccountIfVisible(popup, GOOGLE_EMAIL);
    await popup.waitForClose({ timeout: 90_000 }).catch(() => undefined);
    await page.bringToFront();
  } else {
    await pickGoogleAccountIfVisible(page, GOOGLE_EMAIL);
  }

  await expect
    .poll(() => looksLikeMainApp(page), {
      timeout: 120_000,
      message: "Main application interface with sidebar should be visible after login."
    })
    .toBe(true);
}

async function openMiNegocioMenu(page: Page): Promise<void> {
  const negocioSection = await firstVisible(page, [
    page.getByRole("button", { name: /^Negocio$/i }),
    page.getByRole("link", { name: /^Negocio$/i }),
    page.getByText(/^Negocio$/i)
  ]);
  await clickAndWait(negocioSection, page);

  const miNegocioEntry = await firstVisible(page, [
    page.getByRole("button", { name: /^Mi Negocio$/i }),
    page.getByRole("link", { name: /^Mi Negocio$/i }),
    page.getByText(/^Mi Negocio$/i)
  ]);
  await clickAndWait(miNegocioEntry, page);
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarNegocio = page.getByText(/^Agregar Negocio$/i).first();
  const administrarNegocios = page.getByText(/^Administrar Negocios$/i).first();

  if (await isVisible(agregarNegocio) && await isVisible(administrarNegocios)) {
    return;
  }

  const miNegocioEntry = await firstVisible(page, [
    page.getByRole("button", { name: /^Mi Negocio$/i }),
    page.getByRole("link", { name: /^Mi Negocio$/i }),
    page.getByText(/^Mi Negocio$/i)
  ]);
  await clickAndWait(miNegocioEntry, page);
}

async function validateLegalDocument(
  page: Page,
  context: BrowserContext,
  artifactsDir: string,
  linkTextPattern: RegExp,
  headingPattern: RegExp,
  screenshotName: string
): Promise<string> {
  const link = await firstVisible(page, [
    page.getByRole("link", { name: linkTextPattern }),
    page.getByText(linkTextPattern)
  ]);

  const appUrlBefore = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickAndWait(link, page);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded").catch(() => undefined);
    await expect(popup.getByRole("heading", { name: headingPattern })).toBeVisible({
      timeout: 45_000
    });
    await expect(popup.locator("main, article, body").first()).toContainText(
      /(t[eé]rminos|condiciones|privacidad|uso|datos)/i,
      { timeout: 30_000 }
    );
    await captureCheckpoint(popup, artifactsDir, screenshotName, true);
    const popupUrl = popup.url();
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return popupUrl;
  }

  await expect(page.getByRole("heading", { name: headingPattern })).toBeVisible({
    timeout: 45_000
  });
  await expect(page.locator("main, article, body").first()).toContainText(
    /(t[eé]rminos|condiciones|privacidad|uso|datos)/i,
    { timeout: 30_000 }
  );
  await captureCheckpoint(page, artifactsDir, screenshotName, true);
  const legalUrl = page.url();

  if (legalUrl !== appUrlBefore) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(page);
  }

  return legalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createInitialReport();
  const failures: string[] = [];
  const legalUrls: Record<"terms" | "privacy", string> = {
    terms: "",
    privacy: ""
  };

  const artifactsDir = path.resolve(process.cwd(), "artifacts", `saleads-mi-negocio-${runId()}`);
  fs.mkdirSync(artifactsDir, { recursive: true });

  const validate = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      failures.push(`[${field}] ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  try {
    await validate("Login", async () => {
      await openSaleadsLoginIfNeeded(page);
      await performGoogleLoginIfNeeded(page, context);
      await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 45_000 });
      await captureCheckpoint(page, artifactsDir, "01-dashboard-loaded");
    });

    await validate("Mi Negocio menu", async () => {
      await openMiNegocioMenu(page);
      await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible({ timeout: 30_000 });
      await captureCheckpoint(page, artifactsDir, "02-mi-negocio-menu-expanded");
    });

    await validate("Agregar Negocio modal", async () => {
      const addBusinessEntry = await firstVisible(page, [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ]);
      await clickAndWait(addBusinessEntry, page);

      const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
      await expect(modal).toBeVisible({ timeout: 30_000 });
      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(modal.getByPlaceholder(/Nombre del Negocio/i)).toBeVisible();
      await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

      await modal.getByPlaceholder(/Nombre del Negocio/i).fill("Negocio Prueba Automatizacion");
      await captureCheckpoint(page, artifactsDir, "03-agregar-negocio-modal");
      await clickAndWait(modal.getByRole("button", { name: /^Cancelar$/i }), page);
      await expect(modal).toBeHidden({ timeout: 15_000 });
    });

    await validate("Administrar Negocios view", async () => {
      await ensureMiNegocioExpanded(page);

      const manageBusinesses = await firstVisible(page, [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ]);
      await clickAndWait(manageBusinesses, page);

      await expect(page.getByText(/^Informacion General$|^Información General$/i)).toBeVisible({
        timeout: 45_000
      });
      await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
      await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
      await expect(page.getByText(/^Seccion Legal$|^Sección Legal$/i)).toBeVisible();
      await captureCheckpoint(page, artifactsDir, "04-administrar-negocios", true);
    });

    await validate("Información General", async () => {
      await expect(page.getByText(/@/)).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /^Cambiar Plan$/i })).toBeVisible();
    });

    await validate("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await validate("Tus Negocios", async () => {
      await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible({ timeout: 30_000 });
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    });

    await validate("Términos y Condiciones", async () => {
      legalUrls.terms = await validateLegalDocument(
        page,
        context,
        artifactsDir,
        /T[eé]rminos y Condiciones/i,
        /T[eé]rminos y Condiciones/i,
        "05-terminos-y-condiciones"
      );
    });

    await validate("Política de Privacidad", async () => {
      legalUrls.privacy = await validateLegalDocument(
        page,
        context,
        artifactsDir,
        /Pol[ií]tica de Privacidad/i,
        /Pol[ií]tica de Privacidad/i,
        "06-politica-de-privacidad"
      );
    });
  } finally {
    const finalReportPayload = {
      test: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      report,
      evidence: {
        screenshotsDir: artifactsDir,
        termsFinalUrl: legalUrls.terms,
        privacyFinalUrl: legalUrls.privacy
      },
      failures
    };

    const finalReportPath = path.join(artifactsDir, "final-report.json");
    fs.writeFileSync(finalReportPath, JSON.stringify(finalReportPayload, null, 2), "utf-8");

    await testInfo.attach("final-report", {
      contentType: "application/json",
      path: finalReportPath
    });

    console.log("Final validation report:", JSON.stringify(finalReportPayload, null, 2));
  }

  expect(failures, failures.join("\n")).toEqual([]);
});
