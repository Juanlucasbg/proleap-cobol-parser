import { expect, Locator, Page, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";

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

type Status = "PASS" | "FAIL";

const REPORT_KEYS: ReportKey[] = [
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

function regexFromText(text: string): RegExp {
  return new RegExp(`^\\s*${text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*$`, "i");
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10_000 });
  } catch {
    // Some app views keep polling in the background.
  }
  await page.waitForTimeout(350);
}

async function firstVisibleLocator(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    if ((await candidate.count()) > 0) {
      const first = candidate.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
  }
  return null;
}

async function clickVisibleText(page: Page, text: string | RegExp): Promise<void> {
  const candidates = [
    page.getByRole("button", { name: text }).first(),
    page.getByRole("link", { name: text }).first(),
    page.getByRole("menuitem", { name: text }).first(),
    page.getByRole("tab", { name: text }).first(),
    page.getByText(text).first(),
  ];
  const target = await firstVisibleLocator(candidates);
  expect(target, `No visible element found for "${text.toString()}"`).not.toBeNull();
  await target!.click();
  await waitForUiLoad(page);
}

async function saveCheckpoint(page: Page, filename: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: `test-results/saleads-mi-negocio/${filename}`,
    fullPage,
  });
}

async function isMainAppVisible(page: Page): Promise<boolean> {
  const sidebarLike = await firstVisibleLocator([
    page.locator("aside").first(),
    page.locator("nav").first(),
    page.locator('[class*="sidebar"]').first(),
    page.getByText(/Mi\s+Negocio|Negocio/i).first(),
  ]);
  return sidebarLike !== null;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report: Record<ReportKey, Status> = {
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
  let termsUrl = "";
  let privacyUrl = "";

  await mkdir("test-results/saleads-mi-negocio", { recursive: true });

  const markStep = async (key: ReportKey, callback: () => Promise<void>) => {
    try {
      await callback();
      report[key] = "PASS";
    } catch (error) {
      report[key] = "FAIL";
      failures.push(`${key}: ${(error as Error).message}`);
    }
  };

  await markStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    }

    if (!(await isMainAppVisible(page))) {
      const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await clickVisibleText(
        page,
        /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Google/i,
      );

      const popup = await popupPromise;
      const accountPage = popup ?? page;
      await waitForUiLoad(accountPage);

      const accountPicker = accountPage.getByText(regexFromText(GOOGLE_ACCOUNT_EMAIL)).first();
      if (await accountPicker.isVisible().catch(() => false)) {
        await accountPicker.click();
        await waitForUiLoad(accountPage);
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 45_000 }).catch(() => undefined);
      }
    }

    await expect.poll(async () => isMainAppVisible(page), { timeout: 90_000 }).toBeTruthy();
    await expect(page.getByText(/Mi\s+Negocio|Negocio/i).first()).toBeVisible();
    await saveCheckpoint(page, "01-dashboard-loaded.png");
  });

  await markStep("Mi Negocio menu", async () => {
    const negocioGroup = await firstVisibleLocator([
      page.getByRole("button", { name: /Negocio/i }),
      page.getByRole("link", { name: /Negocio/i }),
      page.getByText(regexFromText("Negocio")).first(),
    ]);
    if (negocioGroup) {
      await negocioGroup.click();
      await waitForUiLoad(page);
    }

    await clickVisibleText(page, /Mi\s+Negocio/i);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await saveCheckpoint(page, "02-mi-negocio-menu-expanded.png");
  });

  await markStep("Agregar Negocio modal", async () => {
    await clickVisibleText(page, /Agregar Negocio/i);

    await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible();
    const businessNameField = await firstVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ has: page.locator('label:has-text("Nombre del Negocio")') }),
    ]);
    expect(businessNameField, "Input 'Nombre del Negocio' was not found").not.toBeNull();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await businessNameField!.click();
    await waitForUiLoad(page);
    await businessNameField!.fill("Negocio Prueba Automatizacion");
    await clickVisibleText(page, /Cancelar/i);
    await saveCheckpoint(page, "03-agregar-negocio-modal.png");
  });

  await markStep("Administrar Negocios view", async () => {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickVisibleText(page, /Mi\s+Negocio/i);
    }
    await clickVisibleText(page, /Administrar Negocios/i);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
    await saveCheckpoint(page, "04-administrar-negocios-page.png", true);
  });

  await markStep("Información General", async () => {
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const pageText = await page.locator("body").innerText();
    expect(pageText).toMatch(/Nombre|Usuario|Perfil/i);
  });

  await markStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await markStep("Tus Negocios", async () => {
    const businessList = await firstVisibleLocator([
      page.locator('[role="list"]').first(),
      page.locator("ul").first(),
      page.locator("table").first(),
      page.getByText(/Tus Negocios/i).first(),
    ]);
    expect(businessList, "Business list section is not visible").not.toBeNull();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  const validateLegalPage = async (
    linkText: RegExp,
    headingText: RegExp,
    screenshotName: string,
  ): Promise<string> => {
    const appUrlBefore = page.url();
    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickVisibleText(page, linkText);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await waitForUiLoad(legalPage);

    if (!popup && legalPage.url() === appUrlBefore) {
      await legalPage.waitForURL((url) => url.toString() !== appUrlBefore, { timeout: 20_000 });
      await waitForUiLoad(legalPage);
    }

    const heading = await firstVisibleLocator([
      legalPage.getByRole("heading", { name: headingText }).first(),
      legalPage.getByText(headingText).first(),
    ]);
    expect(heading, "Legal page heading was not found").not.toBeNull();

    const legalText = (await legalPage.locator("body").innerText()).trim();
    expect(legalText.length).toBeGreaterThan(150);
    await saveCheckpoint(legalPage, screenshotName, true);

    const finalUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUiLoad(legalPage);
    }

    return finalUrl;
  };

  await markStep("Términos y Condiciones", async () => {
    termsUrl = await validateLegalPage(
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "08-terminos-y-condiciones.png",
    );
  });

  await markStep("Política de Privacidad", async () => {
    privacyUrl = await validateLegalPage(
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "09-politica-de-privacidad.png",
    );
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    evidence: {
      termsAndConditionsUrl: termsUrl,
      privacyPolicyUrl: privacyUrl,
      screenshotsDir: "test-results/saleads-mi-negocio",
    },
    failures,
  };

  await writeFile(
    "test-results/saleads-mi-negocio/final-report.json",
    `${JSON.stringify(finalReport, null, 2)}\n`,
    "utf8",
  );

  for (const key of REPORT_KEYS) {
    console.log(`${key}: ${report[key]}`);
  }
  if (termsUrl) {
    console.log(`Términos y Condiciones URL final: ${termsUrl}`);
  }
  if (privacyUrl) {
    console.log(`Política de Privacidad URL final: ${privacyUrl}`);
  }

  expect(
    failures,
    `One or more validations failed:\n${failures.map((failure) => `- ${failure}`).join("\n")}`,
  ).toEqual([]);
});
