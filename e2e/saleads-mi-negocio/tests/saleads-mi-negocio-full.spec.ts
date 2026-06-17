import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

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

type FinalReport = Record<ReportKey, StepStatus>;

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const SCREENSHOT_DIR =
  process.env.SALEADS_SCREENSHOT_DIR ||
  path.join(process.cwd(), "artifacts", "saleads-mi-negocio");
const SALEADS_URL = process.env.SALEADS_URL;
const GOOGLE_ACCOUNT =
  process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;

function slugify(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

async function ensureArtifactsDir(): Promise<void> {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
}

async function waitForUi(page: Page, timeoutMs = 12000): Promise<void> {
  try {
    await page.waitForLoadState("networkidle", { timeout: timeoutMs });
  } catch {
    await page.waitForLoadState("domcontentloaded", { timeout: timeoutMs });
    await page.waitForTimeout(1000);
  }
}

async function captureCheckpoint(page: Page, name: string): Promise<string> {
  const screenshotPath = path.join(
    SCREENSHOT_DIR,
    `${Date.now()}-${slugify(name)}.png`,
  );
  await page.screenshot({ path: screenshotPath, fullPage: true });
  return screenshotPath;
}

async function getVisibleLocator(
  page: Page,
  regex: RegExp,
  options?: { roles?: Array<"button" | "link" | "menuitem" | "heading"> },
): Promise<Locator> {
  const roles = options?.roles || ["button", "link", "menuitem", "heading"];

  for (const role of roles) {
    const byRole = page.getByRole(role, { name: regex }).first();
    if (await byRole.isVisible().catch(() => false)) {
      return byRole;
    }
  }

  const byText = page.getByText(regex).first();
  if (await byText.isVisible().catch(() => false)) {
    return byText;
  }

  // Last chance with a short wait to handle delayed rendering.
  for (const role of roles) {
    const delayed = page.getByRole(role, { name: regex }).first();
    try {
      await expect(delayed).toBeVisible({ timeout: 6000 });
      return delayed;
    } catch {
      // Continue looking through fallback selectors.
    }
  }

  await expect(byText).toBeVisible({ timeout: 6000 });
  return byText;
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function chooseGoogleAccountIfPrompted(page: Page): Promise<void> {
  const accountOption = page.getByText(new RegExp(GOOGLE_ACCOUNT, "i")).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUi(page, 20000);
  }
}

async function assertLegalContentVisible(page: Page): Promise<void> {
  const contentCandidates = [
    page.locator("main p, article p, section p").first(),
    page.locator("main li, article li, section li").first(),
    page.locator("body p, body li").first(),
  ];

  let contentFound = false;
  for (const candidate of contentCandidates) {
    if (await candidate.isVisible().catch(() => false)) {
      contentFound = true;
      break;
    }
  }

  expect(
    contentFound,
    "Expected legal content text to be visible on the page.",
  ).toBeTruthy();
}

async function openLegalLinkAndValidate(
  appPage: Page,
  linkTextRegex: RegExp,
  expectedHeadingRegex: RegExp,
  screenshotName: string,
): Promise<{ finalUrl: string }> {
  const context = appPage.context();
  const link = await getVisibleLocator(appPage, linkTextRegex, {
    roles: ["link", "button", "menuitem"],
  });
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await link.click();
  let legalPage = await popupPromise;

  if (legalPage) {
    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUi(legalPage, 25000);
    const heading = legalPage.getByRole("heading", { name: expectedHeadingRegex }).first();

    if (await heading.isVisible().catch(() => false)) {
      await expect(heading).toBeVisible();
    } else {
      await expect(legalPage.getByText(expectedHeadingRegex).first()).toBeVisible();
    }

    await assertLegalContentVisible(legalPage);
    await captureCheckpoint(legalPage, screenshotName);
    const finalUrl = legalPage.url();
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return { finalUrl };
  }

  legalPage = appPage;
  await legalPage.waitForLoadState("domcontentloaded");
  await waitForUi(legalPage, 25000);
  const heading = legalPage.getByRole("heading", { name: expectedHeadingRegex }).first();

  if (await heading.isVisible().catch(() => false)) {
    await expect(heading).toBeVisible();
  } else {
    await expect(legalPage.getByText(expectedHeadingRegex).first()).toBeVisible();
  }

  await assertLegalContentVisible(legalPage);
  await captureCheckpoint(legalPage, screenshotName);
  const finalUrl = legalPage.url();
  await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => Promise.resolve());
  await waitForUi(appPage);
  return { finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  await ensureArtifactsDir();

  const report: FinalReport = {
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

  const failures: string[] = [];
  const finalUrls: Record<"terminos" | "privacidad", string> = {
    terminos: "",
    privacidad: "",
  };

  const runStep = async (key: ReportKey, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[key] = "PASS";
    } catch (error) {
      report[key] = "FAIL";
      failures.push(
        `${key}: ${error instanceof Error ? error.message : String(error)}`,
      );
    }
  };

  await runStep("Login", async () => {
    if (SALEADS_URL) {
      await page.goto(SALEADS_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page, 30000);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_URL for the current environment so the test can start from the login page.",
      );
    }

    const loginButton = await getVisibleLocator(
      page,
      /sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google|google/i,
      { roles: ["button", "link"] },
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();

    const popupPage = await popupPromise;
    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      await chooseGoogleAccountIfPrompted(popupPage);
      await popupPage.waitForEvent("close", { timeout: 45000 }).catch(() => Promise.resolve());
      await page.bringToFront();
    } else {
      await chooseGoogleAccountIfPrompted(page);
    }

    await waitForUi(page, 45000);
    await expect(
      page.getByText(/negocio|mi negocio/i).first(),
      "Expected main app interface after login.",
    ).toBeVisible({ timeout: 45000 });

    const sidebarVisible =
      (await page.locator("aside").first().isVisible().catch(() => false)) ||
      (await page.getByRole("navigation").first().isVisible().catch(() => false));

    expect(sidebarVisible, "Expected left sidebar navigation to be visible.").toBeTruthy();
    await captureCheckpoint(page, "dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await getVisibleLocator(page, /^negocio$/i, {
      roles: ["button", "link", "menuitem"],
    });
    await clickAndWait(page, negocioSection);

    const miNegocio = await getVisibleLocator(page, /mi negocio/i, {
      roles: ["button", "link", "menuitem"],
    });
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, "mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await getVisibleLocator(page, /agregar negocio/i, {
      roles: ["button", "link", "menuitem"],
    });
    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();

    const businessNameFieldCandidates = [
      page.getByLabel(/nombre del negocio/i).first(),
      page.getByPlaceholder(/nombre del negocio/i).first(),
      page.locator("input").first(),
    ];
    let businessNameField: Locator | null = null;
    for (const candidate of businessNameFieldCandidates) {
      if (await candidate.isVisible().catch(() => false)) {
        businessNameField = candidate;
        break;
      }
    }

    if (!businessNameField) {
      throw new Error("Could not find the 'Nombre del Negocio' input field.");
    }

    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(
      page.getByRole("button", { name: /crear negocio/i }).first(),
    ).toBeVisible();

    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatización");
    await captureCheckpoint(page, "agregar-negocio-modal");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }).first());
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegociosVisible = await page
      .getByText(/administrar negocios/i)
      .first()
      .isVisible()
      .catch(() => false);

    if (!administrarNegociosVisible) {
      const miNegocio = await getVisibleLocator(page, /mi negocio/i, {
        roles: ["button", "link", "menuitem"],
      });
      await clickAndWait(page, miNegocio);
    }

    const administrarNegocios = await getVisibleLocator(page, /administrar negocios/i, {
      roles: ["button", "link", "menuitem"],
    });
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/informacion general|información general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/seccion legal|sección legal/i).first()).toBeVisible();
    await captureCheckpoint(page, "administrar-negocios-page");
  });

  await runStep("Información General", async () => {
    const hasUserName =
      (await page.locator("[data-testid*='name']").first().isVisible().catch(() => false)) ||
      (await page.locator("h1, h2, strong").first().isVisible().catch(() => false));
    expect(hasUserName, "Expected user name to be visible in Información General.").toBeTruthy();

    await expect(
      page.getByText(/@[a-z0-9.-]+\.[a-z]{2,}/i).first(),
      "Expected user email to be visible.",
    ).toBeVisible();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    const { finalUrl } = await openLegalLinkAndValidate(
      page,
      /terminos y condiciones|términos y condiciones/i,
      /terminos y condiciones|términos y condiciones/i,
      "terminos-y-condiciones",
    );
    finalUrls.terminos = finalUrl;
  });

  await runStep("Política de Privacidad", async () => {
    const { finalUrl } = await openLegalLinkAndValidate(
      page,
      /politica de privacidad|política de privacidad/i,
      /politica de privacidad|política de privacidad/i,
      "politica-de-privacidad",
    );
    finalUrls.privacidad = finalUrl;
  });

  const summary = {
    report,
    evidence: {
      screenshotsDir: SCREENSHOT_DIR,
      finalUrls,
    },
    generatedAt: new Date().toISOString(),
  };

  const reportPath = path.join(SCREENSHOT_DIR, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(summary, null, 2), "utf-8");
  await testInfo.attach("saleads-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  if (failures.length > 0) {
    throw new Error(`One or more validations failed:\n- ${failures.join("\n- ")}`);
  }
});
