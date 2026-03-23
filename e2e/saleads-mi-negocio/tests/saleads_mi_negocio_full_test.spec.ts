import { expect, type BrowserContext, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type ReportStatus = "PASS" | "FAIL";
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

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_EMAIL = process.env.SALEADS_GOOGLE_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const EXPECTED_USER_NAME = process.env.SALEADS_EXPECTED_USER_NAME?.trim() || "";
const EXPECTED_USER_EMAIL = process.env.SALEADS_EXPECTED_USER_EMAIL?.trim() || GOOGLE_EMAIL;
const CONFIGURED_LOGIN_URL =
  process.env.SALEADS_LOGIN_URL?.trim() || process.env.SALEADS_BASE_URL?.trim() || "";

function makeRunDirectory() {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const runDirectory = path.join(process.cwd(), "artifacts", TEST_NAME, timestamp);
  fs.mkdirSync(runDirectory, { recursive: true });
  return runDirectory;
}

function collapseWhitespace(value: string) {
  return value.replace(/\s+/g, " ").trim();
}

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page: Page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function isVisible(locator: Locator, timeout = 5_000) {
  return locator.first().isVisible({ timeout }).catch(() => false);
}

async function findFirstVisible(scope: Page | Locator, patterns: RegExp[]) {
  for (const pattern of patterns) {
    const candidates: Locator[] = [
      scope.getByRole("button", { name: pattern }),
      scope.getByRole("link", { name: pattern }),
      scope.getByRole("menuitem", { name: pattern }),
      scope.getByRole("tab", { name: pattern }),
      scope.getByRole("heading", { name: pattern }),
      scope.getByText(pattern),
    ];

    for (const candidate of candidates) {
      const first = candidate.first();
      if (await isVisible(first, 3_000)) {
        return first;
      }
    }
  }

  return null;
}

async function clickByVisibleText(page: Page, patterns: RegExp[]) {
  const locator = await findFirstVisible(page, patterns);
  if (!locator) {
    throw new Error(`Could not find clickable element for patterns: ${patterns.map((p) => p.source).join(", ")}`);
  }

  await locator.scrollIntoViewIfNeeded().catch(() => undefined);
  await locator.click();
  await waitForUiLoad(page);
  return locator;
}

async function selectGoogleAccountIfShown(candidatePage: Page, email: string) {
  await waitForUiLoad(candidatePage);

  const accountRegex = new RegExp(escapeRegex(email), "i");
  const selectAccountHeading = candidatePage.getByText(
    /choose an account|elige una cuenta|selecciona una cuenta/i,
  );
  const emailLocator = await findFirstVisible(candidatePage, [accountRegex]);

  if ((await isVisible(selectAccountHeading.first(), 4_000)) || emailLocator) {
    if (!emailLocator) {
      throw new Error(`Google account selector appeared but could not find email option: ${email}`);
    }
    await emailLocator.click();
    await waitForUiLoad(candidatePage);
  }
}

async function expectSectionVisible(page: Page, title: RegExp) {
  const sectionByHeading = page.getByRole("heading", { name: title }).first();
  if (await isVisible(sectionByHeading, 5_000)) {
    await expect(sectionByHeading).toBeVisible();
    return;
  }

  const sectionByText = page.getByText(title).first();
  await expect(sectionByText).toBeVisible();
}

async function captureCheckpoint(page: Page, runDirectory: string, name: string, fullPage = false) {
  const filePath = path.join(runDirectory, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function openLegalLinkAndValidate(
  page: Page,
  context: BrowserContext,
  runDirectory: string,
  linkPattern: RegExp,
  headingPattern: RegExp,
  screenshotName: string,
) {
  const appPage = page;
  const appUrlBeforeClick = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickByVisibleText(appPage, [linkPattern]);
  const popupPage = await popupPromise;
  const legalPage = popupPage ?? appPage;

  await waitForUiLoad(legalPage);
  await expect(legalPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible();

  const legalBodyText = collapseWhitespace(await legalPage.locator("body").innerText());
  if (legalBodyText.length < 120) {
    throw new Error(`Legal page content appears too short for ${headingPattern.source}.`);
  }

  await captureCheckpoint(legalPage, runDirectory, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else if (legalPage.url() !== appUrlBeforeClick) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    if (appPage.url() !== appUrlBeforeClick) {
      await appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    }
    await waitForUiLoad(appPage);
  }

  return finalUrl;
}

test(TEST_NAME, async ({ page, context }, testInfo) => {
  const runDirectory = makeRunDirectory();
  const report: Record<ReportField, ReportStatus> = {
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

  const stepErrors: string[] = [];
  const evidence: Record<string, string> = {};
  const legalUrls: Record<"terminos" | "privacidad", string> = {
    terminos: "",
    privacidad: "",
  };

  const runStep = async (field: ReportField, callback: () => Promise<void>) => {
    try {
      await callback();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      stepErrors.push(`${field}: ${message}`);
    }
  };

  await runStep("Login", async () => {
    if (CONFIGURED_LOGIN_URL) {
      await page.goto(CONFIGURED_LOGIN_URL, { waitUntil: "domcontentloaded" });
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No login URL available. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL, or provide a preloaded SaleADS login page.",
      );
    }

    await waitForUiLoad(page);

    const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickByVisibleText(page, [
      /sign in with google/i,
      /iniciar sesi[oó]n con google/i,
      /continuar con google/i,
      /acceder con google/i,
      /google/i,
    ]);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await selectGoogleAccountIfShown(googlePopup, GOOGLE_EMAIL);
      await googlePopup.waitForEvent("close", { timeout: 45_000 }).catch(() => undefined);
    } else {
      await selectGoogleAccountIfShown(page, GOOGLE_EMAIL).catch(() => undefined);
    }

    await waitForUiLoad(page);

    const sidebar =
      (await findFirstVisible(page, [/mi negocio/i, /negocio/i])) ??
      (await findFirstVisible(page, [/dashboard/i, /inicio/i]));
    if (!sidebar) {
      throw new Error("Main interface did not appear after login.");
    }

    evidence.dashboard = await captureCheckpoint(page, runDirectory, "01-dashboard-loaded", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, [/negocio/i]).catch(() => undefined);
    await clickByVisibleText(page, [/mi negocio/i]);

    await expect(findFirstVisible(page, [/agregar negocio/i])).resolves.not.toBeNull();
    await expect(findFirstVisible(page, [/administrar negocios/i])).resolves.not.toBeNull();

    evidence.expandedMenu = await captureCheckpoint(page, runDirectory, "02-mi-negocio-expanded", true);
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, [/agregar negocio/i]);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i).first()).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    const businessNameField = page.getByLabel(/nombre del negocio/i).first();
    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatizacion");

    evidence.modal = await captureCheckpoint(page, runDirectory, "03-agregar-negocio-modal", true);
    await clickByVisibleText(page, [/cancelar/i]);
  });

  await runStep("Administrar Negocios view", async () => {
    await clickByVisibleText(page, [/mi negocio/i]).catch(() => undefined);
    await clickByVisibleText(page, [/administrar negocios/i]);

    await expectSectionVisible(page, /informaci[oó]n general/i);
    await expectSectionVisible(page, /detalles de la cuenta/i);
    await expectSectionVisible(page, /tus negocios/i);
    await expectSectionVisible(page, /secci[oó]n legal/i);

    evidence.accountPage = await captureCheckpoint(page, runDirectory, "04-administrar-negocios", true);
  });

  await runStep("Información General", async () => {
    await expectSectionVisible(page, /informaci[oó]n general/i);

    if (EXPECTED_USER_NAME) {
      await expect(page.getByText(new RegExp(escapeRegex(EXPECTED_USER_NAME), "i")).first()).toBeVisible();
    } else {
      await expect(page.getByText(/nombre|usuario|name/i).first()).toBeVisible();
    }

    await expect(page.getByText(new RegExp(escapeRegex(EXPECTED_USER_EMAIL), "i")).first()).toBeVisible();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expectSectionVisible(page, /detalles de la cuenta/i);
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expectSectionVisible(page, /tus negocios/i);
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();

    const businessList = page.locator("[data-testid*='business'], ul, table, [role='list']").first();
    await expect(businessList).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls.terminos = await openLegalLinkAndValidate(
      page,
      context,
      runDirectory,
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "05-terminos-y-condiciones",
    );
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls.privacidad = await openLegalLinkAndValidate(
      page,
      context,
      runDirectory,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad",
    );
  });

  const finalReport = {
    testName: TEST_NAME,
    generatedAt: new Date().toISOString(),
    loginUrlUsed: CONFIGURED_LOGIN_URL || page.url(),
    report,
    legalUrls,
    evidence,
    stepErrors,
  };

  const reportPath = path.join(runDirectory, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");

  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const hasFailures = Object.values(report).includes("FAIL");
  expect(hasFailures, `Step failures:\n${stepErrors.join("\n")}`).toBeFalsy();
});
