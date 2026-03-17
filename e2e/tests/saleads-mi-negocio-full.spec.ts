import { expect, type BrowserContext, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

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

type ReportStatus = "PASS" | "FAIL";

type FinalReport = {
  workflow: string;
  environmentUrl: string | null;
  generatedAt: string;
  results: Record<ReportField, ReportStatus>;
  legalUrls: {
    terminosYCondiciones: string | null;
    politicaDePrivacidad: string | null;
  };
  failures: string[];
};

const EMAIL_TO_SELECT = "juanlucasbarbiergarzon@gmail.com";

const reportTemplate: Record<ReportField, ReportStatus> = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL",
};

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator
    .first()
    .isVisible({ timeout: 1_500 })
    .catch(() => false);
}

async function expectAnyVisible(locators: Locator[], errorMessage: string): Promise<void> {
  for (const locator of locators) {
    if (await isVisible(locator)) {
      return;
    }
  }

  throw new Error(errorMessage);
}

async function resolveClickable(page: Page, pattern: RegExp): Promise<Locator> {
  const candidates: Locator[] = [
    page.getByRole("button", { name: pattern }).first(),
    page.getByRole("link", { name: pattern }).first(),
    page.getByRole("menuitem", { name: pattern }).first(),
    page.locator("[role='button']", { hasText: pattern }).first(),
    page.getByText(pattern).first(),
  ];

  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate;
    }
  }

  throw new Error(`No visible clickable element found for pattern: ${pattern}`);
}

async function clickByText(page: Page, pattern: RegExp): Promise<void> {
  const element = await resolveClickable(page, pattern);
  await element.click();
  await waitForUi(page);
}

async function captureCheckpoint(page: Page, screenshotsDir: string, fileName: string, fullPage = false): Promise<void> {
  await fs.mkdir(screenshotsDir, { recursive: true });
  await page.screenshot({
    path: path.join(screenshotsDir, fileName),
    fullPage,
  });
}

async function markResult(
  report: Record<ReportField, ReportStatus>,
  failures: string[],
  field: ReportField,
  assertion: () => Promise<void>,
): Promise<void> {
  try {
    await assertion();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    const message = error instanceof Error ? error.message : String(error);
    failures.push(`${field}: ${message}`);
  }
}

async function openAndValidateLegalLink(params: {
  page: Page;
  context: BrowserContext;
  linkPattern: RegExp;
  headingPattern: RegExp;
  screenshotsDir: string;
  screenshotFile: string;
}): Promise<string> {
  const { page, context, linkPattern, headingPattern, screenshotsDir, screenshotFile } = params;
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickByText(page, linkPattern);

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  if (popup) {
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => undefined);
    await targetPage.bringToFront();
  }

  const heading = targetPage.getByRole("heading", { name: headingPattern }).first();
  await expect(heading).toBeVisible();

  const legalTextCandidates = [
    targetPage.locator("main p, article p, section p").first(),
    targetPage.locator("main li, article li, section li").first(),
    targetPage.locator("p").first(),
  ];
  await expectAnyVisible(legalTextCandidates, "Legal content text is not visible.");

  await captureCheckpoint(targetPage, screenshotsDir, screenshotFile, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await targetPage.goBack().catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report = { ...reportTemplate };
  const failures: string[] = [];
  const legalUrls: FinalReport["legalUrls"] = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null,
  };

  const workspaceEvidence = path.resolve(process.cwd(), "evidence");
  const screenshotsDir = path.join(workspaceEvidence, "screenshots");
  const reportPath = path.join(workspaceEvidence, "saleads-mi-negocio-final-report.json");
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL ?? null;

  await markResult(report, failures, "Login", async () => {
    if (!loginUrl) {
      throw new Error("Set SALEADS_LOGIN_URL (or BASE_URL) to the current environment login page.");
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const googleLoginPattern = /sign in with google|continuar con google|iniciar sesi[oó]n con google|google/i;
    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickByText(page, googleLoginPattern);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");

      const accountPicker = popup.getByText(EMAIL_TO_SELECT).first();
      if (await isVisible(accountPicker)) {
        await accountPicker.click();
      }

      await popup.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);
      await popup.waitForEvent("close", { timeout: 20_000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await waitForUi(page);
    await expectAnyVisible(
      [
        page.locator("aside").first(),
        page.getByRole("navigation").first(),
        page.locator("[class*='sidebar']").first(),
      ],
      "Left sidebar navigation is not visible.",
    );

    await captureCheckpoint(page, screenshotsDir, "01-dashboard-loaded.png", true);
  });

  await markResult(report, failures, "Mi Negocio menu", async () => {
    await clickByText(page, /negocio/i);
    await clickByText(page, /mi negocio/i);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

    await captureCheckpoint(page, screenshotsDir, "02-mi-negocio-menu-expanded.png");
  });

  await markResult(report, failures, "Agregar Negocio modal", async () => {
    await clickByText(page, /agregar negocio/i);
    const modal = page.getByRole("dialog").first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(modal.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await modal.getByLabel(/nombre del negocio/i).click();
    await modal.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatización");
    await captureCheckpoint(page, screenshotsDir, "03-agregar-negocio-modal.png");
    await modal.getByRole("button", { name: /cancelar/i }).click();
    await waitForUi(page);
  });

  await markResult(report, failures, "Administrar Negocios view", async () => {
    if (!(await isVisible(page.getByText(/administrar negocios/i).first()))) {
      await clickByText(page, /mi negocio/i);
    }

    await clickByText(page, /administrar negocios/i);
    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

    await captureCheckpoint(page, screenshotsDir, "04-administrar-negocios-page.png", true);
  });

  await markResult(report, failures, "Información General", async () => {
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
    await expectAnyVisible(
      [
        page.getByText(/@/).first(),
        page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first(),
      ],
      "User email is not visible.",
    );
  });

  await markResult(report, failures, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await markResult(report, failures, "Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();
  });

  await markResult(report, failures, "Términos y Condiciones", async () => {
    legalUrls.terminosYCondiciones = await openAndValidateLegalLink({
      page,
      context,
      linkPattern: /t[ée]rminos y condiciones/i,
      headingPattern: /t[ée]rminos y condiciones/i,
      screenshotsDir,
      screenshotFile: "08-terminos-y-condiciones.png",
    });
  });

  await markResult(report, failures, "Política de Privacidad", async () => {
    legalUrls.politicaDePrivacidad = await openAndValidateLegalLink({
      page,
      context,
      linkPattern: /pol[ií]tica de privacidad/i,
      headingPattern: /pol[ií]tica de privacidad/i,
      screenshotsDir,
      screenshotFile: "09-politica-de-privacidad.png",
    });
  });

  const finalReport: FinalReport = {
    workflow: "saleads_mi_negocio_full_test",
    environmentUrl: loginUrl,
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls,
    failures,
  };

  await fs.mkdir(workspaceEvidence, { recursive: true });
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");

  console.log(`Final report written to: ${reportPath}`);
  console.table(report);
  if (failures.length > 0) {
    console.log("Validation failures:");
    for (const failure of failures) {
      console.log(`- ${failure}`);
    }
  }

  expect(failures, "One or more workflow validation blocks failed.").toHaveLength(0);
});
