import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_LIMIT_REGEX = /Tienes\s+2\s+de\s+3\s+negocios/i;

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

type Status = "PASS" | "FAIL";

const REPORT_FIELDS: ReportField[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

function createInitialReport(): Record<ReportField, Status> {
  return REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = "FAIL";
      return acc;
    },
    {} as Record<ReportField, Status>,
  );
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiAfterClick(page: Page, clickAction: () => Promise<void>): Promise<void> {
  await clickAction();
  await page.waitForLoadState("domcontentloaded", { timeout: 10_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const resolved = candidate.first();
    const visible = await resolved.isVisible({ timeout: 2_500 }).catch(() => false);
    if (visible) {
      return resolved;
    }
  }
  return null;
}

async function requireVisibleText(page: Page, text: string): Promise<Locator> {
  const regex = new RegExp(escapeRegex(text), "i");
  const locator = await firstVisible([
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByText(regex),
  ]);
  if (!locator) {
    throw new Error(`Could not find visible text: "${text}"`);
  }
  await expect(locator).toBeVisible();
  return locator;
}

async function requireVisiblePattern(page: Page, pattern: RegExp): Promise<Locator> {
  const locator = await firstVisible([
    page.getByRole("heading", { name: pattern }),
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByText(pattern),
  ]);
  if (!locator) {
    throw new Error(`Could not find visible pattern: ${String(pattern)}`);
  }
  await expect(locator).toBeVisible();
  return locator;
}

test("saleads_mi_negocio_full_test", async ({ context, page }) => {
  const report = createInitialReport();
  const failures: string[] = [];
  const legalUrls: Record<string, string> = {};

  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.join(process.cwd(), "e2e-artifacts", "saleads-mi-negocio", runId);
  fs.mkdirSync(artifactsDir, { recursive: true });

  const captureCheckpoint = async (
    filename: string,
    targetPage: Page = page,
    fullPage: boolean = false,
  ): Promise<void> => {
    const screenshotPath = path.join(artifactsDir, `${filename}.png`);
    await targetPage.screenshot({ path: screenshotPath, fullPage });
  };

  const runValidation = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      const reason = error instanceof Error ? error.message : String(error);
      failures.push(`${field}: ${reason}`);
    }
  };

  const clickByText = async (targetPage: Page, text: string): Promise<void> => {
    const element = await requireVisibleText(targetPage, text);
    await waitForUiAfterClick(targetPage, async () => {
      await element.click();
    });
  };

  await runValidation("Login", async () => {
    if (process.env.SALEADS_LOGIN_URL) {
      await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
    }

    await page.waitForLoadState("domcontentloaded");

    const loginButton = await firstVisible([
      page.getByRole("button", { name: /Google/i }),
      page.getByRole("link", { name: /Google/i }),
      page.getByText(/Sign in with Google|Iniciar sesi[oó]n con Google/i),
    ]);
    if (!loginButton) {
      throw new Error("Login button for Google was not found.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
    await waitForUiAfterClick(page, async () => {
      await loginButton.click();
    });
    const popupPage = await popupPromise;

    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      const accountInPopup = await firstVisible([
        popupPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        popupPage.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
      ]);
      if (accountInPopup) {
        await waitForUiAfterClick(popupPage, async () => {
          await accountInPopup.click();
        });
      }
      await page.bringToFront();
    } else {
      const accountOnMainPage = await firstVisible([
        page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        page.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
      ]);
      if (accountOnMainPage) {
        await waitForUiAfterClick(page, async () => {
          await accountOnMainPage.click();
        });
      }
    }

    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.locator("nav"),
      page.getByText(/Negocio|Mi Negocio/i),
    ]);
    if (!sidebar) {
      throw new Error("Sidebar navigation is not visible after login.");
    }
    await expect(sidebar).toBeVisible();

    const appShell = await firstVisible([page.getByRole("main"), page.locator("main"), page.locator("body")]);
    if (!appShell) {
      throw new Error("Main application interface is not visible after login.");
    }

    await captureCheckpoint("01-dashboard-loaded");
  });

  await runValidation("Mi Negocio menu", async () => {
    await clickByText(page, "Negocio");
    await clickByText(page, "Mi Negocio");

    await requireVisibleText(page, "Agregar Negocio");
    await requireVisibleText(page, "Administrar Negocios");
    await captureCheckpoint("02-mi-negocio-expanded");
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickByText(page, "Agregar Negocio");

    await requireVisiblePattern(page, /Crear Nuevo Negocio/i);
    const businessNameField = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.getByRole("textbox", { name: /Nombre del Negocio/i }),
    ]);
    if (!businessNameField) {
      throw new Error("Input 'Nombre del Negocio' was not found in modal.");
    }
    await expect(businessNameField).toBeVisible();

    await requireVisiblePattern(page, BUSINESS_LIMIT_REGEX);
    await requireVisibleText(page, "Cancelar");
    await requireVisiblePattern(page, /Crear Negocio/i);

    await businessNameField.fill("Negocio Prueba Automatizacion");
    await captureCheckpoint("03-agregar-negocio-modal");
    await clickByText(page, "Cancelar");
  });

  await runValidation("Administrar Negocios view", async () => {
    await clickByText(page, "Mi Negocio");
    await clickByText(page, "Administrar Negocios");

    await requireVisiblePattern(page, /Informaci[oó]n General/i);
    await requireVisiblePattern(page, /Detalles de la Cuenta/i);
    await requireVisiblePattern(page, /Tus Negocios/i);
    await requireVisiblePattern(page, /Secci[oó]n Legal/i);
    await captureCheckpoint("04-administrar-negocios-view", page, true);
  });

  await runValidation("Informacion General", async () => {
    const infoSection = page.locator("section, div").filter({ hasText: /Informaci[oó]n General/i }).first();
    await expect(infoSection).toBeVisible();

    const userNameLike = await firstVisible([
      infoSection.getByText(/Nombre|Usuario/i),
      infoSection.locator("h1, h2, h3, p, span").filter({ hasNotText: /@|BUSINESS PLAN|Cambiar Plan/i }),
    ]);
    if (!userNameLike) {
      throw new Error("User name was not identified in 'Informacion General'.");
    }

    const userEmail = await firstVisible([
      infoSection.getByText(/@/),
      page.getByText(/@/),
    ]);
    if (!userEmail) {
      throw new Error("User email was not visible in 'Informacion General'.");
    }

    await requireVisiblePattern(page, /BUSINESS PLAN/i);
    await requireVisibleText(page, "Cambiar Plan");
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await requireVisiblePattern(page, /Cuenta creada/i);
    await requireVisiblePattern(page, /Estado activo/i);
    await requireVisiblePattern(page, /Idioma seleccionado/i);
  });

  await runValidation("Tus Negocios", async () => {
    const businessesSection = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessesSection).toBeVisible();

    const businessList = await firstVisible([
      businessesSection.locator("table"),
      businessesSection.locator("ul"),
      businessesSection.locator("div").filter({ hasText: /Negocio/i }),
    ]);
    if (!businessList) {
      throw new Error("Business list was not visible in 'Tus Negocios'.");
    }

    await requireVisibleText(page, "Agregar Negocio");
    await requireVisiblePattern(page, BUSINESS_LIMIT_REGEX);
  });

  const validateLegalLink = async (
    field: ReportField,
    linkText: string,
    headingPattern: RegExp,
    screenshotName: string,
    urlKey: string,
  ): Promise<void> => {
    await runValidation(field, async () => {
      const appPage = page;
      const linkRegex = new RegExp(escapeRegex(linkText), "i");
      const link = await firstVisible([
        appPage.getByRole("link", { name: linkRegex }),
        appPage.getByRole("button", { name: linkRegex }),
        appPage.getByText(linkRegex),
      ]);
      if (!link) {
        throw new Error(`Could not find legal link: ${linkText}`);
      }

      const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await waitForUiAfterClick(appPage, async () => {
        await link.click();
      });

      const popupPage = await popupPromise;
      const targetPage = popupPage ?? appPage;
      const openedNewTab = Boolean(popupPage);

      await targetPage.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => {});
      await targetPage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});

      await requireVisiblePattern(targetPage, headingPattern);

      const legalText = await firstVisible([
        targetPage.locator("main p"),
        targetPage.locator("article p"),
        targetPage.locator("p"),
        targetPage.locator("li"),
      ]);
      if (!legalText) {
        throw new Error(`Legal content text was not visible for "${linkText}".`);
      }
      await expect(legalText).toBeVisible();

      legalUrls[urlKey] = targetPage.url();
      await captureCheckpoint(screenshotName, targetPage, true);

      if (openedNewTab && popupPage) {
        await popupPage.close();
        await appPage.bringToFront();
      } else {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await appPage.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
      }
    });
  };

  await validateLegalLink(
    "Terminos y Condiciones",
    "Terminos y Condiciones",
    /T[eé]rminos y Condiciones/i,
    "08-terminos-y-condiciones",
    "terminosYCondicionesUrl",
  );

  await validateLegalLink(
    "Politica de Privacidad",
    "Politica de Privacidad",
    /Pol[ií]tica de Privacidad/i,
    "09-politica-de-privacidad",
    "politicaDePrivacidadUrl",
  );

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    statusByField: report,
    legalUrls,
    failures,
    artifactsDir,
  };

  const finalReportPath = path.join(artifactsDir, "final-report.json");
  fs.writeFileSync(finalReportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await test.info().attach("final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  console.log("Final validation report:", JSON.stringify(finalReport, null, 2));
  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
