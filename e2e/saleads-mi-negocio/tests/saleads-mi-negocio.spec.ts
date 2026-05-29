import fs from "node:fs/promises";
import path from "node:path";
import { BrowserContext, expect, Locator, Page, test } from "@playwright/test";

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

type StepResult = {
  status: "PASS" | "FAIL";
  details: string;
  screenshots: string[];
  url?: string;
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

test("SaleADS Mi Negocio full workflow", async ({ page, context }) => {
  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.resolve(process.cwd(), "artifacts", runId);
  await fs.mkdir(artifactsDir, { recursive: true });

  const report = new Map<ReportField, StepResult>();

  const setResult = (
    field: ReportField,
    status: "PASS" | "FAIL",
    details: string,
    screenshots: string[] = [],
    url?: string,
  ) => {
    report.set(field, { status, details, screenshots, url });
  };

  const runStep = async (field: ReportField, action: () => Promise<{ details?: string; screenshots?: string[]; url?: string }>) => {
    try {
      const outcome = await action();
      setResult(field, "PASS", outcome.details ?? "Validated successfully.", outcome.screenshots ?? [], outcome.url);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setResult(field, "FAIL", message);
    }
  };

  await runStep("Login", async () => {
    await ensureLoginPageLoaded(page);

    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Acceder con Google",
      "Google",
    ]);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await maybeSelectGoogleAccount(popup);
      await waitForUi(popup);
      await popup.waitForEvent("close", { timeout: 45000 }).catch(() => null);
    } else {
      await maybeSelectGoogleAccount(page);
    }

    await waitForUi(page);

    await expect(page.getByRole("navigation").first().or(page.locator("aside").first())).toBeVisible({
      timeout: 30000,
    });

    const dashboardShot = await takeScreenshot(page, artifactsDir, "01-dashboard-loaded", true);
    return {
      details: "Main interface and left sidebar are visible after Google login.",
      screenshots: [dashboardShot],
    };
  });

  await runStep("Mi Negocio menu", async () => {
    await openMiNegocioMenu(page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    const menuShot = await takeScreenshot(page, artifactsDir, "02-mi-negocio-menu-expanded");
    return {
      details: "Mi Negocio submenu expanded with expected options.",
      screenshots: [menuShot],
    };
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, ["Agregar Negocio"]);
    await waitForUi(page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i)).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    const input = page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i));
    if (await input.first().isVisible().catch(() => false)) {
      await input.first().click();
      await input.first().fill("Negocio Prueba Automatización");
    }

    const modalShot = await takeScreenshot(page, artifactsDir, "03-agregar-negocio-modal");
    await clickByVisibleText(page, ["Cancelar"]);
    await waitForUi(page);

    return {
      details: "Agregar Negocio modal content and actions validated.",
      screenshots: [modalShot],
    };
  });

  await runStep("Administrar Negocios view", async () => {
    await openMiNegocioMenu(page);
    await clickByVisibleText(page, ["Administrar Negocios"]);
    await waitForUi(page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    const accountShot = await takeScreenshot(page, artifactsDir, "04-administrar-negocios-view", true);
    return {
      details: "Account page sections are visible.",
      screenshots: [accountShot],
    };
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    await expect(page.getByText(/@/).first()).toBeVisible();

    const nameVisible = await firstVisible(
      page.getByText(/Nombre|Usuario/i),
      page.getByTestId(/name/i),
      page.locator("section").filter({ hasText: /Información General/i }).locator("h1,h2,h3,p,span").first(),
    );
    if (!nameVisible) {
      throw new Error("User name was not clearly visible in Información General.");
    }

    return {
      details: "Información General shows user, plan and actions.",
    };
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    return {
      details: "Detalles de la Cuenta labels are visible.",
    };
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();

    const businessListVisible = await firstVisible(
      page.locator("section").filter({ hasText: /Tus Negocios/i }).locator("li,table,[role='row']").first(),
      page.getByText(/Negocio/i).nth(1),
    );
    if (!businessListVisible) {
      throw new Error("Business list is not visible in Tus Negocios section.");
    }

    return {
      details: "Tus Negocios section and controls are visible.",
    };
  });

  await runStep("Términos y Condiciones", async () => {
    const legalResult = await validateLegalLink({
      page,
      context,
      artifactsDir,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotName: "08-terminos-y-condiciones",
    });

    return {
      details: "Términos y Condiciones page validated.",
      screenshots: [legalResult.screenshotPath],
      url: legalResult.finalUrl,
    };
  });

  await runStep("Política de Privacidad", async () => {
    const legalResult = await validateLegalLink({
      page,
      context,
      artifactsDir,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotName: "09-politica-de-privacidad",
    });

    return {
      details: "Política de Privacidad page validated.",
      screenshots: [legalResult.screenshotPath],
      url: legalResult.finalUrl,
    };
  });

  const orderedReport = REPORT_FIELDS.map((field) => ({
    field,
    ...(report.get(field) ?? {
      status: "FAIL",
      details: "Step did not run.",
      screenshots: [],
    }),
  }));

  const reportPath = path.join(artifactsDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(orderedReport, null, 2), "utf8");

  console.log(`\nFinal report: ${reportPath}`);
  console.table(
    orderedReport.map((item) => ({
      field: item.field,
      status: item.status,
      details: item.details,
      url: item.url ?? "",
    })),
  );

  const failed = orderedReport.filter((item) => item.status === "FAIL");
  expect(failed, `One or more validations failed. Check ${reportPath}`).toEqual([]);
});

async function ensureLoginPageLoaded(page: Page) {
  const configuredUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  if (page.url() === "about:blank" && configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);

  if (page.url() === "about:blank") {
    throw new Error(
      "Browser is still on about:blank. Set SALEADS_LOGIN_URL/SALEADS_BASE_URL or open SaleADS login page before running.",
    );
  }
}

async function waitForUi(page: Page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => null);
  await page.waitForTimeout(600);
}

async function clickByVisibleText(page: Page, texts: string[]) {
  const attempts: Locator[] = [];

  for (const text of texts) {
    const pattern = new RegExp(escapeRegExp(text), "i");
    attempts.push(page.getByRole("button", { name: pattern }).first());
    attempts.push(page.getByRole("link", { name: pattern }).first());
    attempts.push(page.getByText(pattern).first());
  }

  for (const locator of attempts) {
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Unable to click visible element with text: ${texts.join(" | ")}`);
}

async function maybeSelectGoogleAccount(page: Page) {
  const account = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
  if (await account.isVisible().catch(() => false)) {
    await account.click();
    await waitForUi(page);
  }
}

async function openMiNegocioMenu(page: Page) {
  const miNegocioVisible = await page.getByText(/Mi Negocio/i).first().isVisible().catch(() => false);
  if (!miNegocioVisible) {
    await clickByVisibleText(page, ["Negocio"]);
  }

  const agregarVisible = await page.getByText(/Agregar Negocio/i).first().isVisible().catch(() => false);
  const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);

  if (!agregarVisible || !administrarVisible) {
    await clickByVisibleText(page, ["Mi Negocio"]);
  }
}

async function takeScreenshot(page: Page, artifactsDir: string, name: string, fullPage = false) {
  const screenshotPath = path.join(artifactsDir, `${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function firstVisible(...locators: Locator[]) {
  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      return true;
    }
  }
  return false;
}

async function validateLegalLink(params: {
  page: Page;
  context: BrowserContext;
  artifactsDir: string;
  linkText: string;
  headingText: string;
  screenshotName: string;
}) {
  const { page, context, artifactsDir, linkText, headingText, screenshotName } = params;
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickByVisibleText(page, [linkText]);
  const popup = await popupPromise;

  const legalPage = popup ?? page;
  await legalPage.waitForLoadState("domcontentloaded");
  await waitForUi(legalPage);

  await expect(legalPage.getByText(new RegExp(escapeRegExp(headingText), "i")).first()).toBeVisible();

  const legalContentText = await legalPage.locator("body").innerText();
  if (legalContentText.trim().length < 250) {
    throw new Error(`${headingText} content appears too short to be considered valid legal text.`);
  }

  const screenshotPath = await takeScreenshot(legalPage, artifactsDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(page);
  }

  return { screenshotPath, finalUrl };
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
