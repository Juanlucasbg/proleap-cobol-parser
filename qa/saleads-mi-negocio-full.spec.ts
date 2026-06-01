import { expect, test, type BrowserContext, type Locator, type Page, type TestInfo } from "@playwright/test";
import { promises as fs } from "node:fs";
import path from "node:path";

const SECTION_NAMES = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
] as const;

type SectionName = (typeof SECTION_NAMES)[number];
type StepStatus = "PASS" | "FAIL" | "SKIPPED";

type StepResult = {
  status: StepStatus;
  details: string[];
  evidence: string[];
  finalUrl?: string;
};

const GOOGLE_EMAIL = process.env.SALEADS_GOOGLE_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const START_URL = process.env.SALEADS_START_URL;

function createReport(): Record<SectionName, StepResult> {
  return Object.fromEntries(
    SECTION_NAMES.map((name) => [
      name,
      {
        status: "SKIPPED",
        details: [],
        evidence: []
      }
    ])
  ) as Record<SectionName, StepResult>;
}

function escapeRegex(raw: string): string {
  return raw.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function textRegex(text: string): RegExp {
  return new RegExp(escapeRegex(text), "i");
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, fileName: string, fullPage = false): Promise<string> {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return path.basename(screenshotPath);
}

function textCandidates(page: Page, text: string): Locator[] {
  const pattern = textRegex(text);
  return [
    page.getByRole("button", { name: pattern }).first(),
    page.getByRole("link", { name: pattern }).first(),
    page.getByRole("menuitem", { name: pattern }).first(),
    page.getByRole("tab", { name: pattern }).first(),
    page.getByText(pattern).first()
  ];
}

async function firstVisibleByText(page: Page, texts: string[], timeoutMs = 10_000): Promise<Locator | null> {
  const stopAt = Date.now() + timeoutMs;
  while (Date.now() < stopAt) {
    for (const text of texts) {
      for (const locator of textCandidates(page, text)) {
        if (await locator.isVisible().catch(() => false)) {
          return locator;
        }
      }
    }
    await page.waitForTimeout(250);
  }
  return null;
}

async function clickByVisibleText(page: Page, texts: string[], timeoutMs = 10_000): Promise<void> {
  const locator = await firstVisibleByText(page, texts, timeoutMs);
  if (!locator) {
    throw new Error(`Could not find visible element for texts: ${texts.join(", ")}`);
  }
  await locator.click();
  await waitForUiToLoad(page);
}

async function assertTextVisible(page: Page, text: string, timeoutMs = 20_000): Promise<void> {
  await expect(page.getByText(textRegex(text)).first()).toBeVisible({ timeout: timeoutMs });
}

async function completeGoogleSelection(googlePage: Page): Promise<boolean> {
  const accountLocator = googlePage.getByText(textRegex(GOOGLE_EMAIL)).first();
  if (await accountLocator.isVisible().catch(() => false)) {
    await accountLocator.click();
    await googlePage.waitForLoadState("domcontentloaded").catch(() => {});
    return true;
  }
  return false;
}

async function waitForAppShell(page: Page): Promise<void> {
  await expect(page.getByText(textRegex("Negocio")).first()).toBeVisible({ timeout: 45_000 });
}

async function openLegalDocument(args: {
  appPage: Page;
  context: BrowserContext;
  linkText: string;
  headingText: string;
  testInfo: TestInfo;
  screenshotName: string;
}): Promise<{ evidence: string; finalUrl: string }> {
  const { appPage, context, linkText, headingText, testInfo, screenshotName } = args;
  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  const legalLink = await firstVisibleByText(appPage, [linkText], 12_000);
  if (!legalLink) {
    throw new Error(`Legal link not found: ${linkText}`);
  }

  await legalLink.click();

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  await legalPage.waitForLoadState("domcontentloaded");
  await waitForUiToLoad(legalPage);

  await assertTextVisible(legalPage, headingText, 20_000);
  const legalContent = await legalPage.locator("body").innerText();
  if (legalContent.trim().length < 120) {
    throw new Error(`${headingText} page content looks too short to be valid legal text.`);
  }

  const evidence = await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  }
  await waitForUiToLoad(appPage);

  return { evidence, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  let appPage = page;

  async function runSection(name: SectionName, fn: (result: StepResult) => Promise<void>): Promise<void> {
    const result = report[name];
    result.status = "PASS";
    try {
      await fn(result);
    } catch (error) {
      result.status = "FAIL";
      result.details.push(`Error: ${errorMessage(error)}`);
    }
  }

  await runSection("Login", async (result) => {
    if (START_URL) {
      await appPage.goto(START_URL, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(appPage);
      result.details.push(`Navigated to SALEADS_START_URL: ${START_URL}`);
    } else {
      result.details.push("SALEADS_START_URL not set; expecting browser to already be at login page.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickByVisibleText(appPage, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Login con Google"
    ]);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const selected = await completeGoogleSelection(popup);
      result.details.push(selected ? `Selected Google account: ${GOOGLE_EMAIL}` : "Google selector opened, account selection was not required.");
    } else {
      const selectedInSameTab = await completeGoogleSelection(appPage).catch(() => false);
      result.details.push(
        selectedInSameTab
          ? `Selected Google account in current tab: ${GOOGLE_EMAIL}`
          : "Google selector did not appear or account was already authenticated."
      );
    }

    if (popup) {
      const popupHasShell = await popup.getByText(textRegex("Negocio")).first().isVisible().catch(() => false);
      if (popupHasShell) {
        appPage = popup;
      }
    }

    try {
      await waitForAppShell(appPage);
    } catch {
      if (popup && appPage !== popup) {
        appPage = popup;
        await waitForAppShell(appPage);
      } else if (popup && appPage === popup) {
        appPage = page;
        await waitForAppShell(appPage);
      } else {
        throw new Error("Application shell did not load after Google login.");
      }
    }
    await expect(appPage.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
    result.details.push("Main application shell loaded and sidebar is visible.");
    result.evidence.push(await captureCheckpoint(appPage, testInfo, "01-dashboard-loaded.png", true));
  });

  await runSection("Mi Negocio menu", async (result) => {
    await clickByVisibleText(appPage, ["Negocio", "Mi Negocio"], 12_000);
    await assertTextVisible(appPage, "Agregar Negocio");
    await assertTextVisible(appPage, "Administrar Negocios");
    result.details.push("Mi Negocio submenu expanded with both required options.");
    result.evidence.push(await captureCheckpoint(appPage, testInfo, "02-mi-negocio-menu-expanded.png"));
  });

  await runSection("Agregar Negocio modal", async (result) => {
    await clickByVisibleText(appPage, ["Agregar Negocio"], 12_000);
    await assertTextVisible(appPage, "Crear Nuevo Negocio");
    await assertTextVisible(appPage, "Nombre del Negocio");
    await assertTextVisible(appPage, "Tienes 2 de 3 negocios");
    await assertTextVisible(appPage, "Cancelar");
    await assertTextVisible(appPage, "Crear Negocio");
    result.details.push("Agregar Negocio modal contains all expected labels and actions.");
    result.evidence.push(await captureCheckpoint(appPage, testInfo, "03-agregar-negocio-modal.png"));

    const nameField = appPage.getByLabel(textRegex("Nombre del Negocio")).first();
    if (await nameField.isVisible().catch(() => false)) {
      await nameField.click();
      await nameField.fill("Negocio Prueba Automatización");
      result.details.push("Optional input action executed in Nombre del Negocio.");
    } else {
      const fallbackField = appPage.getByPlaceholder(textRegex("Nombre del Negocio")).first();
      if (await fallbackField.isVisible().catch(() => false)) {
        await fallbackField.fill("Negocio Prueba Automatización");
        result.details.push("Optional input action executed using placeholder match.");
      }
    }

    await clickByVisibleText(appPage, ["Cancelar"], 10_000);
  });

  await runSection("Administrar Negocios view", async (result) => {
    const administrarVisible = await firstVisibleByText(appPage, ["Administrar Negocios"], 4_000);
    if (!administrarVisible) {
      await clickByVisibleText(appPage, ["Negocio", "Mi Negocio"], 12_000);
    }

    await clickByVisibleText(appPage, ["Administrar Negocios"], 12_000);
    await assertTextVisible(appPage, "Información General");
    await assertTextVisible(appPage, "Detalles de la Cuenta");
    await assertTextVisible(appPage, "Tus Negocios");
    await assertTextVisible(appPage, "Sección Legal");
    result.details.push("Administrar Negocios page loaded with all required sections.");
    result.evidence.push(await captureCheckpoint(appPage, testInfo, "04-administrar-negocios-view-full.png", true));
  });

  await runSection("Información General", async (result) => {
    await assertTextVisible(appPage, "BUSINESS PLAN");
    await assertTextVisible(appPage, "Cambiar Plan");

    const emailVisible =
      (await appPage.getByText(/@/).first().isVisible().catch(() => false)) ||
      (await appPage.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first().isVisible().catch(() => false));
    if (!emailVisible) {
      throw new Error("User email was not visible in Información General.");
    }

    const nameCandidates = [
      appPage.getByText(textRegex("Hola")).first(),
      appPage.getByText(textRegex("Bienvenido")).first(),
      appPage.locator("h1, h2, h3").first()
    ];
    let hasNameIndicator = false;
    for (const candidate of nameCandidates) {
      if (await candidate.isVisible().catch(() => false)) {
        hasNameIndicator = true;
        break;
      }
    }
    if (!hasNameIndicator) {
      throw new Error("User name indicator was not visible in Información General.");
    }

    result.details.push("User name, email, BUSINESS PLAN text and Cambiar Plan button are visible.");
  });

  await runSection("Detalles de la Cuenta", async (result) => {
    await assertTextVisible(appPage, "Cuenta creada");
    await assertTextVisible(appPage, "Estado activo");
    await assertTextVisible(appPage, "Idioma seleccionado");
    result.details.push("Detalles de la Cuenta section shows account creation, active state, and selected language.");
  });

  await runSection("Tus Negocios", async (result) => {
    await assertTextVisible(appPage, "Tus Negocios");
    await assertTextVisible(appPage, "Agregar Negocio");
    await assertTextVisible(appPage, "Tienes 2 de 3 negocios");
    result.details.push("Business list and capacity indicators are present.");
  });

  await runSection("Términos y Condiciones", async (result) => {
    const terms = await openLegalDocument({
      appPage,
      context,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      testInfo,
      screenshotName: "05-terminos-y-condiciones.png"
    });
    result.evidence.push(terms.evidence);
    result.finalUrl = terms.finalUrl;
    result.details.push(`Legal page validated at: ${terms.finalUrl}`);
  });

  await runSection("Política de Privacidad", async (result) => {
    const privacy = await openLegalDocument({
      appPage,
      context,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      testInfo,
      screenshotName: "06-politica-de-privacidad.png"
    });
    result.evidence.push(privacy.evidence);
    result.finalUrl = privacy.finalUrl;
    result.details.push(`Legal page validated at: ${privacy.finalUrl}`);
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    sections: report
  };

  const finalReportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json"
  });

  const failedSections = SECTION_NAMES.filter((sectionName) => report[sectionName].status !== "PASS");
  expect(failedSections, `Sections with FAIL/SKIPPED: ${failedSections.join(", ")}`).toEqual([]);
});
