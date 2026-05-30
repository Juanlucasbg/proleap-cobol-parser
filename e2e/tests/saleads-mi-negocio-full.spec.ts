import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type Status = "PASS" | "FAIL";

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

interface ReportEntry {
  status: Status;
  details: string[];
  evidence: string[];
  finalUrl?: string;
}

type WorkflowReport = Record<ReportKey, ReportEntry>;

const REPORT_KEYS: ReportKey[] = [
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

function createInitialReport(): WorkflowReport {
  return REPORT_KEYS.reduce((acc, key) => {
    acc[key] = { status: "FAIL", details: [], evidence: [] };
    return acc;
  }, {} as WorkflowReport);
}

function normalizeError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

async function settleUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 10_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(700);
}

async function firstVisible(candidates: Locator[], description: string): Promise<Locator> {
  for (const candidate of candidates) {
    const locator = candidate.first();
    const count = await locator.count();
    if (count === 0) {
      continue;
    }
    const visible = await locator.isVisible().catch(() => false);
    if (visible) {
      return locator;
    }
  }
  throw new Error(`No visible element found for: ${description}`);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await settleUi(page);
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<string> {
  const screenshotsDir = path.join(process.cwd(), "artifacts", "screenshots");
  await fs.mkdir(screenshotsDir, { recursive: true });
  const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
  const filePath = path.join(screenshotsDir, `${Date.now()}-${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });

  await testInfo.attach(name, {
    path: filePath,
    contentType: "image/png"
  });

  return filePath;
}

async function getSectionByHeading(page: Page, headingRegex: RegExp): Promise<Locator> {
  const heading = await firstVisible(
    [
      page.getByRole("heading", { name: headingRegex }),
      page.getByText(headingRegex)
    ],
    `Section heading ${headingRegex}`
  );

  const parentSection = heading.locator("xpath=ancestor::section[1]");
  if (await parentSection.count()) {
    return parentSection.first();
  }
  return heading.locator("xpath=ancestor::*[self::div or self::article][1]");
}

async function executeStep(
  key: ReportKey,
  report: WorkflowReport,
  action: () => Promise<void>
): Promise<void> {
  try {
    await action();
    report[key].status = "PASS";
  } catch (error) {
    report[key].status = "FAIL";
    report[key].details.push(normalizeError(error));
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createInitialReport();
  const saleadsLoginUrl = process.env.SALEADS_LOGIN_URL;
  let appPage = page;

  if (saleadsLoginUrl) {
    await page.goto(saleadsLoginUrl, { waitUntil: "domcontentloaded" });
    await settleUi(page);
  }

  await executeStep("Login", report, async () => {
    const alreadyInsideApp = await page.locator("aside, nav").first().isVisible().catch(() => false);
    if (alreadyInsideApp) {
      const dashboardShot = await captureCheckpoint(page, testInfo, "dashboard-loaded");
      report["Login"].evidence.push(dashboardShot);
      report["Login"].details.push("Session already authenticated; application interface is visible.");
      return;
    }

    const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
    const loginButton = await firstVisible(
      [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/(sign in with google|iniciar sesi[oó]n con google|continuar con google)/i)
      ],
      "Google login button"
    );

    await clickAndWait(loginButton, page);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
      const accountEntry = popup.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      if (await accountEntry.isVisible().catch(() => false)) {
        await accountEntry.click();
      }
    } else {
      const samePageAccount = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      if (await samePageAccount.isVisible().catch(() => false)) {
        await samePageAccount.click();
      }
    }

    await settleUi(page);

    const sidebar = await firstVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.getByText(/negocio/i)
      ],
      "main app sidebar"
    );
    await expect(sidebar).toBeVisible();

    const dashboardShot = await captureCheckpoint(page, testInfo, "dashboard-loaded");
    report["Login"].evidence.push(dashboardShot);
    report["Login"].details.push("Main application interface and sidebar are visible.");
  });

  await executeStep("Mi Negocio menu", report, async () => {
    const negocioSection = await firstVisible(
      [
        appPage.getByRole("button", { name: /^negocio$/i }),
        appPage.getByRole("link", { name: /^negocio$/i }),
        appPage.getByText(/^negocio$/i)
      ],
      "Negocio section"
    );
    await clickAndWait(negocioSection, appPage);

    const miNegocioItem = await firstVisible(
      [
        appPage.getByRole("button", { name: /mi negocio/i }),
        appPage.getByRole("link", { name: /mi negocio/i }),
        appPage.getByText(/mi negocio/i)
      ],
      "Mi Negocio option"
    );
    await clickAndWait(miNegocioItem, appPage);

    await expect(appPage.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/administrar negocios/i).first()).toBeVisible();

    const menuShot = await captureCheckpoint(appPage, testInfo, "mi-negocio-menu-expanded");
    report["Mi Negocio menu"].evidence.push(menuShot);
    report["Mi Negocio menu"].details.push("Mi Negocio submenu expanded with expected options.");
  });

  await executeStep("Agregar Negocio modal", report, async () => {
    const addBusinessOption = await firstVisible(
      [
        appPage.getByRole("button", { name: /agregar negocio/i }),
        appPage.getByRole("link", { name: /agregar negocio/i }),
        appPage.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio option"
    );
    await clickAndWait(addBusinessOption, appPage);

    const modalTitle = appPage.getByText(/crear nuevo negocio/i).first();
    await expect(modalTitle).toBeVisible();

    const nameInput = await firstVisible(
      [
        appPage.getByLabel(/nombre del negocio/i),
        appPage.getByPlaceholder(/nombre del negocio/i),
        appPage.locator("input[type='text'], input")
      ],
      "Nombre del Negocio input"
    );
    await expect(nameInput).toBeVisible();

    await expect(appPage.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    await nameInput.fill("Negocio Prueba Automatización");

    const modalShot = await captureCheckpoint(appPage, testInfo, "agregar-negocio-modal");
    report["Agregar Negocio modal"].evidence.push(modalShot);

    const cancelButton = appPage.getByRole("button", { name: /cancelar/i }).first();
    await clickAndWait(cancelButton, appPage);

    report["Agregar Negocio modal"].details.push("Modal and required controls validated.");
  });

  await executeStep("Administrar Negocios view", report, async () => {
    const administrarNegocios = appPage.getByText(/administrar negocios/i).first();
    if (!(await administrarNegocios.isVisible().catch(() => false))) {
      const miNegocio = await firstVisible(
        [
          appPage.getByRole("button", { name: /mi negocio/i }),
          appPage.getByRole("link", { name: /mi negocio/i }),
          appPage.getByText(/mi negocio/i)
        ],
        "Mi Negocio for re-expand"
      );
      await clickAndWait(miNegocio, appPage);
    }

    await clickAndWait(appPage.getByText(/administrar negocios/i).first(), appPage);

    await expect(appPage.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(appPage.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(appPage.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(appPage.getByText(/secci[oó]n legal/i).first()).toBeVisible();

    const accountPageShot = await captureCheckpoint(appPage, testInfo, "administrar-negocios-page", true);
    report["Administrar Negocios view"].evidence.push(accountPageShot);
    report["Administrar Negocios view"].details.push("Administrar Negocios page loaded with all required sections.");
  });

  await executeStep("Información General", report, async () => {
    const infoSection = await getSectionByHeading(appPage, /informaci[oó]n general/i);
    const infoText = await infoSection.innerText();

    expect(infoText).toMatch(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
    expect(infoText.replace(/\s+/g, " ").trim().length).toBeGreaterThan(20);
    await expect(infoSection.getByText(/business plan/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    report["Información General"].details.push("User details, plan label and action button validated.");
  });

  await executeStep("Detalles de la Cuenta", report, async () => {
    const detailsSection = await getSectionByHeading(appPage, /detalles de la cuenta/i);
    await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/idioma seleccionado/i)).toBeVisible();

    report["Detalles de la Cuenta"].details.push("All required account detail labels are visible.");
  });

  await executeStep("Tus Negocios", report, async () => {
    const businessesSection = await getSectionByHeading(appPage, /tus negocios/i);
    const sectionText = await businessesSection.innerText();

    await expect(businessesSection.getByText(/agregar negocio/i)).toBeVisible();
    await expect(businessesSection.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    expect(sectionText.replace(/\s+/g, " ").trim().length).toBeGreaterThan(30);

    report["Tus Negocios"].details.push("Business section and capacity message are visible.");
  });

  const validateLegalPage = async (
    reportKey: "Términos y Condiciones" | "Política de Privacidad",
    linkPattern: RegExp,
    headingPattern: RegExp,
    screenshotName: string
  ): Promise<void> => {
    const legalSection = await getSectionByHeading(appPage, /secci[oó]n legal/i);
    const link = await firstVisible(
      [
        legalSection.getByRole("link", { name: linkPattern }),
        legalSection.getByRole("button", { name: linkPattern }),
        legalSection.getByText(linkPattern)
      ],
      `${reportKey} legal link`
    );

    const newPagePromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await link.click();
    await settleUi(appPage);

    const possibleNewPage = await newPagePromise;
    const targetPage = possibleNewPage ?? appPage;

    await targetPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
    await targetPage.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);

    const headingLocator = await firstVisible(
      [
        targetPage.getByRole("heading", { name: headingPattern }),
        targetPage.getByText(headingPattern)
      ],
      `${reportKey} heading`
    );
    await expect(headingLocator).toBeVisible();

    const legalBodyText = await targetPage.locator("body").innerText();
    expect(legalBodyText.replace(/\s+/g, " ").trim().length).toBeGreaterThan(150);

    const legalShot = await captureCheckpoint(targetPage, testInfo, screenshotName);
    report[reportKey].evidence.push(legalShot);
    report[reportKey].finalUrl = targetPage.url();
    report[reportKey].details.push(`Validated legal page at URL: ${targetPage.url()}`);

    if (possibleNewPage) {
      await possibleNewPage.close();
      await appPage.bringToFront();
      await settleUi(appPage);
    } else {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await settleUi(appPage);
    }
  };

  await executeStep("Términos y Condiciones", report, async () => {
    await validateLegalPage(
      "Términos y Condiciones",
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "terminos-y-condiciones"
    );
  });

  await executeStep("Política de Privacidad", report, async () => {
    await validateLegalPage(
      "Política de Privacidad",
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "politica-de-privacidad"
    );
  });

  const reportDir = path.join(process.cwd(), "artifacts", "report");
  await fs.mkdir(reportDir, { recursive: true });
  const finalReportPath = path.join(reportDir, "saleads-mi-negocio-final-report.json");
  await fs.writeFile(finalReportPath, JSON.stringify(report, null, 2), "utf-8");

  await testInfo.attach("final-report", {
    path: finalReportPath,
    contentType: "application/json"
  });

  const summary = REPORT_KEYS.map((key) => `${key}: ${report[key].status}`).join(" | ");
  console.log(`FINAL REPORT SUMMARY -> ${summary}`);
  console.log(`FINAL REPORT FILE -> ${finalReportPath}`);

  const failed = REPORT_KEYS.filter((key) => report[key].status !== "PASS");
  expect(
    failed,
    `One or more workflow validations failed.\n${JSON.stringify(report, null, 2)}`
  ).toEqual([]);
});
