import { expect, test, type BrowserContext, type Locator, type Page, type TestInfo } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
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

type ReportEntry = {
  status: ReportStatus;
  details: string[];
};

const REPORT_FIELDS: ReportField[] = [
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function createReport(): Record<ReportField, ReportEntry> {
  return REPORT_FIELDS.reduce<Record<ReportField, ReportEntry>>((acc, field) => {
    acc[field] = { status: "FAIL", details: [] };
    return acc;
  }, {} as Record<ReportField, ReportEntry>);
}

function markPass(report: Record<ReportField, ReportEntry>, field: ReportField, detail: string): void {
  if (report[field].status !== "FAIL" || report[field].details.length === 0) {
    report[field].status = "PASS";
  }
  report[field].details.push(detail);
}

function markFail(report: Record<ReportField, ReportEntry>, field: ReportField, detail: string): void {
  report[field].status = "FAIL";
  report[field].details.push(detail);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function firstVisibleLocator(candidates: Locator[]): Promise<Locator> {
  for (const candidate of candidates) {
    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }
  throw new Error("No visible locator found for the requested action.");
}

async function clickVisibleAndWait(page: Page, candidates: Locator[]): Promise<void> {
  const target = await firstVisibleLocator(candidates);
  await target.click();
  await waitForUi(page);
}

async function captureScreenshot(testInfo: TestInfo, page: Page, fileName: string, fullPage = false): Promise<void> {
  const screenshotPath = testInfo.outputPath(fileName);
  await mkdir(path.dirname(screenshotPath), { recursive: true });
  await page.screenshot({ path: screenshotPath, fullPage });
}

async function openLegalLinkAndValidate(params: {
  appPage: Page;
  context: BrowserContext;
  testInfo: TestInfo;
  linkPattern: RegExp;
  headingPattern: RegExp;
  screenshotName: string;
}): Promise<string> {
  const { appPage, context, testInfo, linkPattern, headingPattern, screenshotName } = params;
  const appUrlBefore = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickVisibleAndWait(appPage, [
    appPage.getByRole("link", { name: linkPattern }),
    appPage.getByText(linkPattern),
  ]);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  await waitForUi(legalPage);

  await expect(legalPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible({ timeout: 30000 });
  await expect(legalPage.locator("main, article, body").first()).toContainText(/[A-Za-zÁÉÍÓÚáéíóú]{4,}/, {
    timeout: 15000,
  });

  await captureScreenshot(testInfo, legalPage, screenshotName, true);
  const legalUrl = legalPage.url();

  if (popup) {
    await popup.close({ runBeforeUnload: true }).catch(() => undefined);
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== appUrlBefore) {
    const hasHistory = await appPage.evaluate(() => window.history.length > 1).catch(() => false);
    if (hasHistory) {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    } else {
      await appPage.goto(appUrlBefore, { waitUntil: "domcontentloaded" }).catch(() => undefined);
    }
    await waitForUi(appPage);
  }

  return legalUrl;
}

async function ensureStartingPage(page: Page): Promise<void> {
  const providedUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL;
  if (page.url() === "about:blank" && providedUrl) {
    await page.goto(providedUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();

  await test.step("Step 1 - Login with Google", async () => {
    try {
      await ensureStartingPage(page);

      const currentUrl = page.url();
      if (currentUrl === "about:blank") {
        throw new Error(
          "No starting login page detected. Provide SALEADS_LOGIN_URL (or SALEADS_URL), or run this test with a pre-opened SaleADS login page."
        );
      }

      const popupPromise = context.waitForEvent("page", { timeout: 15000 }).catch(() => null);
      await clickVisibleAndWait(page, [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ]);

      const authPage = (await popupPromise) ?? page;
      await waitForUi(authPage);

      const accountLocator = authPage.getByText(new RegExp(`^${escapeRegex(GOOGLE_ACCOUNT_EMAIL)}$`));
      if (await accountLocator.first().isVisible().catch(() => false)) {
        await accountLocator.first().click();
        await waitForUi(authPage);
      }

      const sidebarLocator = page.locator("aside, nav").first();
      await expect(sidebarLocator).toBeVisible({ timeout: 60000 });
      await expect(page.getByText(/negocio/i)).toBeVisible({ timeout: 60000 });

      await captureScreenshot(testInfo, page, "01-dashboard-loaded.png");
      markPass(report, "Login", "Dashboard and sidebar are visible after Google login.");
    } catch (error) {
      markFail(report, "Login", `Login validation failed: ${(error as Error).message}`);
    }
  });

  await test.step("Step 2 - Open Mi Negocio menu", async () => {
    try {
      await clickVisibleAndWait(page, [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);

      await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible({ timeout: 15000 });
      await captureScreenshot(testInfo, page, "02-mi-negocio-menu-expanded.png");

      markPass(report, "Mi Negocio menu", "Mi Negocio submenu expanded with expected options.");
    } catch (error) {
      markFail(report, "Mi Negocio menu", `Menu validation failed: ${(error as Error).message}`);
    }
  });

  await test.step("Step 3 - Validate Agregar Negocio modal", async () => {
    try {
      await clickVisibleAndWait(page, [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);

      await expect(page.getByRole("heading", { name: /crear nuevo negocio/i })).toBeVisible({ timeout: 15000 });
      await expect(page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i))).toBeVisible({
        timeout: 15000,
      });
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 15000 });

      await captureScreenshot(testInfo, page, "03-agregar-negocio-modal.png");

      const nameInput = page
        .getByLabel(/nombre del negocio/i)
        .or(page.getByPlaceholder(/nombre del negocio/i))
        .first();
      if (await nameInput.isVisible().catch(() => false)) {
        await nameInput.click();
        await nameInput.fill("Negocio Prueba Automatizacion");
      }

      await clickVisibleAndWait(page, [page.getByRole("button", { name: /cancelar/i }), page.getByText(/^Cancelar$/i)]);
      markPass(report, "Agregar Negocio modal", "Agregar Negocio modal content and controls validated.");
    } catch (error) {
      markFail(report, "Agregar Negocio modal", `Modal validation failed: ${(error as Error).message}`);
    }
  });

  await test.step("Step 4 - Open Administrar Negocios", async () => {
    try {
      const administrarVisible = await page.getByText(/^Administrar Negocios$/i).isVisible().catch(() => false);
      if (!administrarVisible) {
        await clickVisibleAndWait(page, [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ]);
      }

      await clickVisibleAndWait(page, [
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ]);

      await expect(page.getByRole("heading", { name: /informaci[oó]n general/i })).toBeVisible({ timeout: 30000 });
      await expect(page.getByRole("heading", { name: /detalles de la cuenta/i })).toBeVisible({ timeout: 30000 });
      await expect(page.getByRole("heading", { name: /tus negocios/i })).toBeVisible({ timeout: 30000 });
      await expect(page.getByRole("heading", { name: /secci[oó]n legal/i })).toBeVisible({ timeout: 30000 });

      await captureScreenshot(testInfo, page, "04-administrar-negocios-view.png", true);
      markPass(report, "Administrar Negocios view", "Administrar Negocios page loaded with all expected sections.");
    } catch (error) {
      markFail(report, "Administrar Negocios view", `Administrar Negocios validation failed: ${(error as Error).message}`);
    }
  });

  await test.step("Step 5 - Validate Información General", async () => {
    try {
      await expect(page.getByText(/@/)).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible({ timeout: 15000 });

      const likelyUserName = page.locator("h1, h2, h3, p, span").filter({ hasNotText: /@|BUSINESS PLAN|Cambiar Plan/i }).first();
      await expect(likelyUserName).toBeVisible({ timeout: 15000 });

      markPass(report, "Información General", "User name, email, plan text, and plan button are visible.");
    } catch (error) {
      markFail(report, "Información General", `Información General validation failed: ${(error as Error).message}`);
    }
  });

  await test.step("Step 6 - Validate Detalles de la Cuenta", async () => {
    try {
      await expect(page.getByText(/cuenta creada/i)).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/estado activo/i)).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible({ timeout: 15000 });
      markPass(report, "Detalles de la Cuenta", "Detalles de la Cuenta labels are visible.");
    } catch (error) {
      markFail(report, "Detalles de la Cuenta", `Detalles de la Cuenta validation failed: ${(error as Error).message}`);
    }
  });

  await test.step("Step 7 - Validate Tus Negocios", async () => {
    try {
      await expect(page.getByRole("heading", { name: /tus negocios/i })).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: /agregar negocio/i }).or(page.getByText(/^Agregar Negocio$/i))).toBeVisible({
        timeout: 15000,
      });
      markPass(report, "Tus Negocios", "Business list area, add button, and business-count text are visible.");
    } catch (error) {
      markFail(report, "Tus Negocios", `Tus Negocios validation failed: ${(error as Error).message}`);
    }
  });

  await test.step("Step 8 - Validate Términos y Condiciones", async () => {
    try {
      const legalUrl = await openLegalLinkAndValidate({
        appPage: page,
        context,
        testInfo,
        linkPattern: /t[eé]rminos y condiciones/i,
        headingPattern: /t[eé]rminos y condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png",
      });
      markPass(report, "Términos y Condiciones", `Legal page validated. Final URL: ${legalUrl}`);
    } catch (error) {
      markFail(report, "Términos y Condiciones", `Términos y Condiciones validation failed: ${(error as Error).message}`);
    }
  });

  await test.step("Step 9 - Validate Política de Privacidad", async () => {
    try {
      const legalUrl = await openLegalLinkAndValidate({
        appPage: page,
        context,
        testInfo,
        linkPattern: /pol[ií]tica de privacidad/i,
        headingPattern: /pol[ií]tica de privacidad/i,
        screenshotName: "06-politica-de-privacidad.png",
      });
      markPass(report, "Política de Privacidad", `Legal page validated. Final URL: ${legalUrl}`);
    } catch (error) {
      markFail(report, "Política de Privacidad", `Política de Privacidad validation failed: ${(error as Error).message}`);
    }
  });

  await test.step("Step 10 - Final report", async () => {
    const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
    await mkdir(path.dirname(reportPath), { recursive: true });
    await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    const summary = REPORT_FIELDS.map((field) => `${field}: ${report[field].status}`).join("\n");
    // Printing a plain-text summary helps cron logs and quick triage.
    // eslint-disable-next-line no-console
    console.log(`\nSaleADS Mi Negocio Final Report\n${summary}\n`);
  });

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(failedFields, `Failed validation fields: ${failedFields.join(", ")}`).toEqual([]);
});
