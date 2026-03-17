import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import dotenv from "dotenv";
import fs from "node:fs";

dotenv.config();

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";

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
type StepStatus = "PASS" | "FAIL";

type StepReport = {
  status: StepStatus;
  details: string[];
  evidence: string[];
  finalUrl?: string;
};

type Report = Record<ReportField, StepReport>;

const buildInitialReport = (): Report =>
  Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      { status: "FAIL", details: ["Step not executed."], evidence: [] },
    ]),
  ) as Report;

const escapeRegex = (input: string): string =>
  input.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForTimeout(700);
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 5_000 }),
    page.waitForLoadState("networkidle", { timeout: 5_000 }),
  ]);
}

async function firstVisibleLocator(
  locators: Locator[],
  timeoutMs = 7_000,
): Promise<Locator | null> {
  for (const locator of locators) {
    const candidate = locator.first();
    const visible = await candidate.isVisible({ timeout: timeoutMs }).catch(() => false);
    if (visible) {
      return candidate;
    }
  }
  return null;
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  report: Report,
  field: ReportField,
  filename: string,
  fullPage = false,
): Promise<void> {
  const screenshotPath = testInfo.outputPath(filename);
  await page.screenshot({ path: screenshotPath, fullPage });
  report[field].evidence.push(screenshotPath);
}

function pass(report: Report, field: ReportField, detail: string): void {
  if (report[field].details.length === 1 && report[field].details[0] === "Step not executed.") {
    report[field].details = [];
  }
  report[field].status = "PASS";
  report[field].details.push(detail);
}

function fail(report: Report, field: ReportField, detail: string): void {
  report[field].status = "FAIL";
  if (report[field].details.length === 1 && report[field].details[0] === "Step not executed.") {
    report[field].details = [];
  }
  report[field].details.push(detail);
}

async function executeStep(
  report: Report,
  field: ReportField,
  handler: () => Promise<void>,
): Promise<void> {
  try {
    await handler();
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    fail(report, field, message);
  }
}

async function clickLinkAndValidateLegal(
  page: Page,
  testInfo: TestInfo,
  report: Report,
  field: "Términos y Condiciones" | "Política de Privacidad",
  linkTextRegex: RegExp,
  headingRegex: RegExp,
): Promise<void> {
  const appPage = page;
  const link = await firstVisibleLocator([
    appPage.getByRole("link", { name: linkTextRegex }),
    appPage.getByText(linkTextRegex),
  ]);
  expect(link, `Could not find legal link: ${linkTextRegex}`).not.toBeNull();

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await link!.click();
  await waitForUiToSettle(appPage);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 30_000 });
    await waitForUiToSettle(popup);
  } else {
    await Promise.allSettled([
      appPage.waitForLoadState("domcontentloaded", { timeout: 15_000 }),
      appPage.waitForLoadState("networkidle", { timeout: 15_000 }),
    ]);
  }

  await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible();
  const legalBodyText = (await legalPage.locator("body").innerText()).trim();
  expect(
    legalBodyText.length,
    `Expected legal content text on ${field}, but body text was too short.`,
  ).toBeGreaterThan(120);

  report[field].finalUrl = legalPage.url();
  pass(report, field, `Validated legal page and captured URL: ${legalPage.url()}`);
  await captureCheckpoint(
    legalPage,
    testInfo,
    report,
    field,
    `${field.toLowerCase().replace(/\s+/g, "_").replace(/[^a-z_]/g, "")}.png`,
    true,
  );

  if (popup) {
    await appPage.bringToFront();
    await waitForUiToSettle(appPage);
    return;
  }

  await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
    const appUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
    if (!appUrl) {
      return;
    }
    await appPage.goto(appUrl, { waitUntil: "domcontentloaded" });
  });
  await waitForUiToSettle(appPage);
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildInitialReport();

  try {
    const appUrl = process.env.SALEADS_LOGIN_URL ?? testInfo.project.use.baseURL;
    if (!appUrl || typeof appUrl !== "string") {
      throw new Error(
        "Missing SALEADS_LOGIN_URL (or SALEADS_BASE_URL) environment variable; this test is environment-agnostic and requires the active environment URL.",
      );
    }

    await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);

    await executeStep(report, "Login", async () => {
      const loginButton = await firstVisibleLocator([
        page.getByRole("button", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ]);
      expect(loginButton, "Login button / Sign in with Google was not visible.").not.toBeNull();

      const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
      await loginButton!.click();
      await waitForUiToSettle(page);

      const popup = await popupPromise;
      const authPage = popup ?? page;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 30_000 });
        await waitForUiToSettle(popup);
      }

      const accountOption = authPage.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")).first();
      if (await accountOption.isVisible({ timeout: 8_000 }).catch(() => false)) {
        await accountOption.click();
        await waitForUiToSettle(authPage);
      }

      await page.bringToFront();
      const negocioVisible = page.getByText(/negocio/i).first();
      await expect(negocioVisible).toBeVisible({ timeout: 60_000 });

      const sidebarVisible =
        (await page.locator("aside").first().isVisible({ timeout: 5_000 }).catch(() => false)) ||
        (await page.locator("nav").first().isVisible({ timeout: 5_000 }).catch(() => false));
      expect(sidebarVisible, "Expected left sidebar navigation to be visible.").toBeTruthy();

      pass(report, "Login", "Dashboard and left sidebar navigation were displayed after login.");
      await captureCheckpoint(page, testInfo, report, "Login", "01_dashboard_loaded.png");
    });

    await executeStep(report, "Mi Negocio menu", async () => {
      const negocioItem = await firstVisibleLocator([
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ]);
      if (negocioItem) {
        await negocioItem.click();
        await waitForUiToSettle(page);
      }

      const miNegocio = await firstVisibleLocator([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);
      expect(miNegocio, "Could not find 'Mi Negocio' in sidebar navigation.").not.toBeNull();
      await miNegocio!.click();
      await waitForUiToSettle(page);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

      pass(report, "Mi Negocio menu", "'Mi Negocio' expanded and showed expected submenu options.");
      await captureCheckpoint(page, testInfo, report, "Mi Negocio menu", "02_mi_negocio_expanded.png");
    });

    await executeStep(report, "Agregar Negocio modal", async () => {
      const agregarNegocio = await firstVisibleLocator([
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ]);
      expect(agregarNegocio, "Could not find 'Agregar Negocio' option.").not.toBeNull();
      await agregarNegocio!.click();
      await waitForUiToSettle(page);

      await expect(page.getByRole("heading", { name: /crear nuevo negocio/i })).toBeVisible();
      const negocioInput = await firstVisibleLocator([
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
      ]);
      expect(negocioInput, "Input 'Nombre del Negocio' is missing.").not.toBeNull();
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();
      await captureCheckpoint(page, testInfo, report, "Agregar Negocio modal", "03_agregar_negocio_modal.png");

      await negocioInput!.click();
      await negocioInput!.fill("Negocio Prueba Automatización");
      await page.getByRole("button", { name: /cancelar/i }).click();
      await waitForUiToSettle(page);

      pass(report, "Agregar Negocio modal", "Modal and required fields/buttons were validated.");
    });

    await executeStep(report, "Administrar Negocios view", async () => {
      const miNegocio = await firstVisibleLocator([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);
      if (miNegocio) {
        await miNegocio.click();
        await waitForUiToSettle(page);
      }

      const administrarNegocios = await firstVisibleLocator([
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ]);
      expect(administrarNegocios, "Could not find 'Administrar Negocios' option.").not.toBeNull();
      await administrarNegocios!.click();
      await waitForUiToSettle(page);

      await expect(page.getByRole("heading", { name: /informaci[oó]n general/i })).toBeVisible();
      await expect(page.getByRole("heading", { name: /detalles de la cuenta/i })).toBeVisible();
      await expect(page.getByRole("heading", { name: /tus negocios/i })).toBeVisible();
      await expect(page.getByRole("heading", { name: /secci[oó]n legal/i })).toBeVisible();

      pass(report, "Administrar Negocios view", "Account page loaded with all expected sections.");
      await captureCheckpoint(page, testInfo, report, "Administrar Negocios view", "04_administrar_negocios_full.png", true);
    });

    await executeStep(report, "Información General", async () => {
      const infoHeading = page.getByRole("heading", { name: /informaci[oó]n general/i }).first();
      await expect(infoHeading).toBeVisible();
      const infoSection = infoHeading.locator(
        "xpath=ancestor::*[self::section or self::article or self::div][1]",
      );

      const expectedUserName = process.env.SALEADS_EXPECTED_USER_NAME;
      const nameLocator = expectedUserName
        ? infoSection.getByText(new RegExp(escapeRegex(expectedUserName), "i")).first()
        : infoSection.locator(
            "xpath=.//*[self::span or self::p or self::h1 or self::h2 or self::h3][normalize-space(text())!='' and not(contains(text(), '@'))]",
          ).first();
      await expect(nameLocator).toBeVisible();

      const emailLocator = infoSection.getByText(
        new RegExp(escapeRegex(process.env.SALEADS_EXPECTED_EMAIL ?? GOOGLE_ACCOUNT_EMAIL), "i"),
      );
      await expect(emailLocator.first()).toBeVisible();
      await expect(infoSection.getByText(/business plan/i).first()).toBeVisible();
      await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

      pass(report, "Información General", "User profile summary and plan details were visible.");
    });

    await executeStep(report, "Detalles de la Cuenta", async () => {
      const detailsHeading = page.getByRole("heading", { name: /detalles de la cuenta/i }).first();
      await expect(detailsHeading).toBeVisible();
      const detailsSection = detailsHeading.locator(
        "xpath=ancestor::*[self::section or self::article or self::div][1]",
      );

      await expect(detailsSection.getByText(/cuenta creada/i).first()).toBeVisible();
      await expect(detailsSection.getByText(/estado activo|estado\s*activo/i).first()).toBeVisible();
      await expect(detailsSection.getByText(/idioma seleccionado/i).first()).toBeVisible();

      pass(report, "Detalles de la Cuenta", "Account detail labels were validated.");
    });

    await executeStep(report, "Tus Negocios", async () => {
      const businessHeading = page.getByRole("heading", { name: /tus negocios/i }).first();
      await expect(businessHeading).toBeVisible();
      const businessSection = businessHeading.locator(
        "xpath=ancestor::*[self::section or self::article or self::div][1]",
      );

      await expect(
        businessSection.locator("xpath=.//*[self::ul or self::table or self::div][.//text()]").first(),
      ).toBeVisible();
      await expect(businessSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
      await expect(businessSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

      pass(report, "Tus Negocios", "Business list, quota text, and add button were visible.");
    });

    await executeStep(report, "Términos y Condiciones", async () => {
      await clickLinkAndValidateLegal(
        page,
        testInfo,
        report,
        "Términos y Condiciones",
        /t[ée]rminos y condiciones/i,
        /t[ée]rminos y condiciones/i,
      );
    });

    await executeStep(report, "Política de Privacidad", async () => {
      await clickLinkAndValidateLegal(
        page,
        testInfo,
        report,
        "Política de Privacidad",
        /pol[ií]tica de privacidad/i,
        /pol[ií]tica de privacidad/i,
      );
    });
  } finally {
    const finalReportPath = testInfo.outputPath("saleads_mi_negocio_final_report.json");
    fs.writeFileSync(finalReportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    await testInfo.attach("saleads_mi_negocio_final_report", {
      path: finalReportPath,
      contentType: "application/json",
    });

    const simplified = Object.fromEntries(
      REPORT_FIELDS.map((field) => [field, report[field].status]),
    );
    // Keep a machine-readable final step report in CI logs.
    console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
    console.log(JSON.stringify(simplified, null, 2));
  }

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(failedFields, `Failed steps: ${failedFields.join(", ")}`).toEqual([]);
});
