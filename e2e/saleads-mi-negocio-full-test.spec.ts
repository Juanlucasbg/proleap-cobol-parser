import fs from "node:fs";
import path from "node:path";
import { expect, type Locator, type Page, test } from "@playwright/test";

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type ReportStatus = "PASS" | "FAIL";

type ReportRecord = {
  status: ReportStatus;
  details: string;
};

function createInitialReport(): Record<ReportField, ReportRecord> {
  return REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = { status: "FAIL", details: "Not executed" };
      return acc;
    },
    {} as Record<ReportField, ReportRecord>,
  );
}

function nowSlug(): string {
  return new Date().toISOString().replaceAll(":", "-").replaceAll(".", "-");
}

function safeErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForTimeout(700);
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => undefined);
}

async function waitForVisibleLocator(page: Page, locators: Locator[], timeoutMs = 15_000): Promise<Locator> {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of locators) {
      const candidate = locator.first();
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error("No visible locator found before timeout.");
}

function byText(page: Page, matcher: RegExp): Locator[] {
  return [
    page.getByRole("button", { name: matcher }),
    page.getByRole("link", { name: matcher }),
    page.getByRole("menuitem", { name: matcher }),
    page.getByText(matcher),
  ];
}

async function clickByVisibleText(page: Page, matcher: RegExp, description: string): Promise<void> {
  const locator = await waitForVisibleLocator(page, byText(page, matcher));
  await locator.click();
  await waitForUi(page);
  test.info().annotations.push({ type: "action", description: `Clicked: ${description}` });
}

async function expectAnyVisible(locators: Locator[], fieldName: string): Promise<void> {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      return;
    }
  }

  throw new Error(`Expected visible element not found: ${fieldName}`);
}

async function saveScreenshot(page: Page, evidenceDir: string, fileName: string, fullPage = false): Promise<string> {
  const outputPath = path.join(evidenceDir, `${fileName}.png`);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

test(TEST_NAME, async ({ page, context, baseURL }) => {
  const runId = nowSlug();
  const evidenceDir = path.join("evidence", TEST_NAME, runId);
  fs.mkdirSync(evidenceDir, { recursive: true });

  const report = createInitialReport();
  const legalUrls: Record<"terminos" | "privacidad", string> = {
    terminos: "",
    privacidad: "",
  };

  const runStep = async (field: ReportField, stepBody: () => Promise<void>) => {
    try {
      await stepBody();
      report[field] = { status: "PASS", details: "Validated successfully." };
    } catch (error) {
      report[field] = { status: "FAIL", details: safeErrorMessage(error) };
      await saveScreenshot(page, evidenceDir, `failure-${field.replaceAll(" ", "-").toLowerCase()}`, true).catch(
        () => undefined,
      );
    }
  };

  await runStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? baseURL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    if (page.url() === "about:blank") {
      throw new Error(
        "Browser was not on a SaleADS login page. Provide SALEADS_LOGIN_URL or SALEADS_BASE_URL at runtime.",
      );
    }

    const loginLocators = [
      ...byText(page, /sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ...byText(page, /google/i),
    ];
    const loginButton = await waitForVisibleLocator(page, loginLocators, 20_000);

    const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await loginButton.click();

    const googlePage = await popupPromise;
    if (googlePage) {
      await waitForUi(googlePage);
      const accountLocator = await waitForVisibleLocator(
        googlePage,
        [
          googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
          googlePage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        ],
        7_500,
      ).catch(() => null);

      if (accountLocator) {
        await accountLocator.click();
      }

      await waitForUi(googlePage);
    } else {
      await waitForUi(page);
      const accountLocator = await waitForVisibleLocator(
        page,
        [page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false })],
        5_000,
      ).catch(() => null);

      if (accountLocator) {
        await accountLocator.click();
      }
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav")).toBeVisible({ timeout: 30_000 });
    await saveScreenshot(page, evidenceDir, "01-dashboard-loaded", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav")).toBeVisible({ timeout: 15_000 });
    await clickByVisibleText(page, /negocio/i, "Negocio section");
    await clickByVisibleText(page, /mi negocio/i, "Mi Negocio option");

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 10_000 });
    await saveScreenshot(page, evidenceDir, "02-mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /agregar negocio/i, "Agregar Negocio");

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible({ timeout: 10_000 });
    await expectAnyVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByRole("textbox", { name: /nombre del negocio/i }),
        page.getByPlaceholder(/nombre del negocio/i),
      ],
      "Nombre del Negocio",
    );
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();
    await saveScreenshot(page, evidenceDir, "03-agregar-negocio-modal");

    const nameInput = await waitForVisibleLocator(page, [
      page.getByLabel(/nombre del negocio/i),
      page.getByRole("textbox", { name: /nombre del negocio/i }),
      page.getByPlaceholder(/nombre del negocio/i),
    ]);
    await nameInput.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, /cancelar/i, "Cancelar modal");
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickByVisibleText(page, /mi negocio/i, "Expand Mi Negocio");
    }

    await clickByVisibleText(page, /administrar negocios/i, "Administrar Negocios");

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible({ timeout: 15_000 });
    await saveScreenshot(page, evidenceDir, "04-administrar-negocios-view", true);
  });

  await runStep("Información General", async () => {
    const infoSection = page.locator("section, div").filter({ hasText: /informaci[oó]n general/i }).first();
    await expect(infoSection).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/@[a-z0-9.-]+\.[a-z]{2,}/i).first()).toBeVisible();
    await expect(page.getByText(/\bBUSINESS PLAN\b/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = page.locator("section, div").filter({ hasText: /detalles de la cuenta/i }).first();
    await expect(detailsSection).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const businessesSection = page.locator("section, div").filter({ hasText: /tus negocios/i }).first();
    await expect(businessesSection).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  const openLegalAndReturn = async (
    linkMatcher: RegExp,
    headingMatcher: RegExp,
    screenshotName: string,
    urlField: "terminos" | "privacidad",
  ) => {
    const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickByVisibleText(page, linkMatcher, `Legal link ${linkMatcher.toString()}`);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await waitForUi(legalPage);

    await expect(
      legalPage.getByRole("heading", { name: headingMatcher }).or(legalPage.getByText(headingMatcher)),
    ).toBeVisible({ timeout: 15_000 });

    const legalParagraphs = legalPage.locator("p, li");
    const paragraphCount = await legalParagraphs.count();
    if (paragraphCount === 0) {
      throw new Error("Legal content text was not visible.");
    }

    await saveScreenshot(legalPage, evidenceDir, screenshotName, true);
    legalUrls[urlField] = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(page);
    }
  };

  await runStep("Términos y Condiciones", async () => {
    await openLegalAndReturn(
      /t[ée]rminos y condiciones/i,
      /t[ée]rminos y condiciones/i,
      "05-terminos-condiciones",
      "terminos",
    );
  });

  await runStep("Política de Privacidad", async () => {
    await openLegalAndReturn(
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "06-politica-privacidad",
      "privacidad",
    );
  });

  const finalReport = {
    testName: TEST_NAME,
    generatedAt: new Date().toISOString(),
    urls: legalUrls,
    results: report,
  };

  const reportPath = path.join(evidenceDir, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

  await test.info().attach("saleads-mi-negocio-final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json",
  });

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  if (failedFields.length > 0) {
    throw new Error(
      `Validation failures found in: ${failedFields.join(", ")}. See ${reportPath} and screenshots in ${evidenceDir}.`,
    );
  }
});
