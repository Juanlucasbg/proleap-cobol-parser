import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_DIR = path.resolve(__dirname, "..");
const RUN_STAMP = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACTS_DIR = path.join(PROJECT_DIR, "artifacts", RUN_STAMP);
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");

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
];

const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "NOT_RUN"]));
const stepErrors = {};
const screenshotPaths = {};
const legalUrls = {};

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page, timeout = 30000) {
  await page.waitForLoadState("domcontentloaded", { timeout });
  await page.waitForLoadState("networkidle", { timeout }).catch(() => {});
  await page.waitForTimeout(500);
}

function sanitizeForFileName(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9_-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

async function captureCheckpoint(page, checkpointName, options = {}) {
  await ensureDir(SCREENSHOTS_DIR);
  const fileName = `${sanitizeForFileName(checkpointName)}.png`;
  const screenshotPath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage: Boolean(options.fullPage) });
  screenshotPaths[checkpointName] = screenshotPath;
}

async function isVisible(locator) {
  return locator.isVisible().catch(() => false);
}

async function clickByVisibleText(page, matcher, intentLabel) {
  const roleLocator = page.getByRole("button", { name: matcher }).first();
  if (await isVisible(roleLocator)) {
    await roleLocator.click();
    await waitForUi(page);
    return;
  }

  const linkLocator = page.getByRole("link", { name: matcher }).first();
  if (await isVisible(linkLocator)) {
    await linkLocator.click();
    await waitForUi(page);
    return;
  }

  const textLocator = page.getByText(matcher).first();
  if (await isVisible(textLocator)) {
    await textLocator.click();
    await waitForUi(page);
    return;
  }

  throw new Error(`Could not find clickable element for: ${intentLabel}`);
}

async function waitForText(page, matcher, label, timeout = 30000) {
  const locator = page.getByText(matcher).first();
  await locator.waitFor({ state: "visible", timeout });
  return locator;
}

async function waitForAny(page, candidates, label, timeout = 30000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    for (const factory of candidates) {
      const locator = factory();
      if (await isVisible(locator.first())) {
        return locator.first();
      }
    }
    await page.waitForTimeout(200);
  }
  throw new Error(`Could not find visible element: ${label}`);
}

async function runStep(stepName, stepAction) {
  try {
    await stepAction();
    results[stepName] = "PASS";
    console.log(`STEP PASS: ${stepName}`);
  } catch (error) {
    results[stepName] = "FAIL";
    stepErrors[stepName] = error instanceof Error ? error.message : String(error);
    console.error(`STEP FAIL: ${stepName} -> ${stepErrors[stepName]}`);
  }
}

async function maybeSelectGoogleAccount(page, email) {
  const accountLocator = page.getByText(email).first();
  try {
    await accountLocator.waitFor({ state: "visible", timeout: 10000 });
    await accountLocator.click();
    await waitForUi(page, 60000);
  } catch {
    console.log("Google account selector not shown; continuing with current session flow.");
  }
}

async function validateLegalPage({
  appPage,
  linkText,
  headingRegex,
  reportField,
  screenshotLabel,
}) {
  const popupPromise = appPage.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
  await clickByVisibleText(appPage, new RegExp(linkText, "i"), linkText);
  const popupPage = await popupPromise;
  const targetPage = popupPage ?? appPage;

  await targetPage.waitForLoadState("domcontentloaded", { timeout: 30000 });
  await targetPage.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});

  const heading = await waitForAny(
    targetPage,
    [
      () => targetPage.getByRole("heading", { name: headingRegex }),
      () => targetPage.getByText(headingRegex),
    ],
    `${linkText} heading`,
    30000,
  );
  await heading.waitFor({ state: "visible", timeout: 30000 });

  const legalText = (await targetPage.locator("main, article, body").first().innerText().catch(() => "")) || "";
  if (legalText.trim().length < 80) {
    throw new Error(`Legal content for "${linkText}" appears empty.`);
  }

  legalUrls[reportField] = targetPage.url();
  await captureCheckpoint(targetPage, screenshotLabel, { fullPage: true });

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUi(appPage);
}

async function main() {
  await ensureDir(ARTIFACTS_DIR);
  await ensureDir(SCREENSHOTS_DIR);

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || "";
  const headless = process.env.HEADLESS !== "false";
  const userDataDir =
    process.env.PLAYWRIGHT_USER_DATA_DIR || path.join(PROJECT_DIR, ".pw-user-data");

  const context = await chromium.launchPersistentContext(userDataDir, {
    headless,
    viewport: { width: 1440, height: 900 },
  });

  const page = context.pages()[0] || (await context.newPage());

  try {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
      await waitForUi(page);
    } else if (page.url().startsWith("about:blank")) {
      throw new Error(
        "No URL available. Set SALEADS_LOGIN_URL (or SALEADS_URL), or launch with a preloaded SaleADS login page.",
      );
    }

    await runStep("Login", async () => {
      await clickByVisibleText(page, /google/i, "Login with Google");
      await maybeSelectGoogleAccount(page, "juanlucasbarbiergarzon@gmail.com");

      await waitForAny(
        page,
        [() => page.locator("aside"), () => page.locator("nav"), () => page.getByText(/Negocio/i)],
        "main application interface",
        60000,
      );
      await waitForAny(
        page,
        [() => page.locator("aside"), () => page.locator("nav")],
        "left sidebar navigation",
        60000,
      );
      await captureCheckpoint(page, "01-dashboard-loaded", { fullPage: true });
    });

    await runStep("Mi Negocio menu", async () => {
      await waitForAny(
        page,
        [() => page.locator("aside"), () => page.locator("nav")],
        "left sidebar",
        30000,
      );
      await clickByVisibleText(page, /Negocio/i, "Negocio section");
      await clickByVisibleText(page, /Mi Negocio/i, "Mi Negocio option");

      await waitForText(page, /Agregar Negocio/i, "Agregar Negocio");
      await waitForText(page, /Administrar Negocios/i, "Administrar Negocios");
      await captureCheckpoint(page, "02-mi-negocio-menu-expanded");
    });

    await runStep("Agregar Negocio modal", async () => {
      await clickByVisibleText(page, /Agregar Negocio/i, "Agregar Negocio");
      await waitForText(page, /Crear Nuevo Negocio/i, "Crear Nuevo Negocio modal title");
      const dialog = await waitForAny(
        page,
        [() => page.getByRole("dialog"), () => page.locator("[role='dialog']"), () => page.locator("form")],
        "Agregar Negocio dialog",
      );

      const nameInput = await waitForAny(
        page,
        [
          () => page.getByLabel(/Nombre del Negocio/i),
          () => page.getByPlaceholder(/Nombre del Negocio/i),
          () => dialog.locator("input"),
        ],
        "Nombre del Negocio input",
      );

      await waitForText(page, /Tienes\s+2\s+de\s+3\s+negocios/i, "Business quota text");
      await waitForAny(
        page,
        [() => page.getByRole("button", { name: /Cancelar/i }), () => page.getByText(/Cancelar/i)],
        "Cancelar button",
      );
      await waitForAny(
        page,
        [() => page.getByRole("button", { name: /Crear Negocio/i }), () => page.getByText(/Crear Negocio/i)],
        "Crear Negocio button",
      );

      await captureCheckpoint(page, "03-agregar-negocio-modal");
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
      await clickByVisibleText(page, /Cancelar/i, "Cancelar");
    });

    await runStep("Administrar Negocios view", async () => {
      const administrarVisible = await isVisible(page.getByText(/Administrar Negocios/i).first());
      if (!administrarVisible) {
        await clickByVisibleText(page, /Mi Negocio/i, "Mi Negocio option");
      }

      await clickByVisibleText(page, /Administrar Negocios/i, "Administrar Negocios");
      await waitForText(page, /Información General/i, "Información General");
      await waitForText(page, /Detalles de la Cuenta/i, "Detalles de la Cuenta");
      await waitForText(page, /Tus Negocios/i, "Tus Negocios");
      await waitForText(page, /Sección Legal/i, "Sección Legal");
      await captureCheckpoint(page, "04-administrar-negocios-view", { fullPage: true });
    });

    await runStep("Información General", async () => {
      await waitForAny(
        page,
        [
          () => page.locator("section").filter({ hasText: /Información General/i }),
          () => page.getByText(/Información General/i),
        ],
        "Información General section",
      );
      await waitForAny(
        page,
        [() => page.getByText(/@/), () => page.getByText(/BUSINESS PLAN/i)],
        "user profile details",
      );
      await waitForText(page, /BUSINESS PLAN/i, "BUSINESS PLAN text");
      await waitForAny(
        page,
        [() => page.getByRole("button", { name: /Cambiar Plan/i }), () => page.getByText(/Cambiar Plan/i)],
        "Cambiar Plan button",
      );
    });

    await runStep("Detalles de la Cuenta", async () => {
      await waitForText(page, /Cuenta creada/i, "Cuenta creada");
      await waitForText(page, /Estado activo/i, "Estado activo");
      await waitForText(page, /Idioma seleccionado/i, "Idioma seleccionado");
    });

    await runStep("Tus Negocios", async () => {
      await waitForText(page, /Tus Negocios/i, "Tus Negocios section");
      await waitForAny(
        page,
        [() => page.getByRole("button", { name: /Agregar Negocio/i }), () => page.getByText(/Agregar Negocio/i)],
        "Agregar Negocio button",
      );
      await waitForText(page, /Tienes\s+2\s+de\s+3\s+negocios/i, "Business quota text");
    });

    await runStep("Términos y Condiciones", async () => {
      await validateLegalPage({
        appPage: page,
        linkText: "Términos y Condiciones",
        headingRegex: /Términos y Condiciones/i,
        reportField: "Términos y Condiciones",
        screenshotLabel: "05-terminos-y-condiciones",
      });
    });

    await runStep("Política de Privacidad", async () => {
      await validateLegalPage({
        appPage: page,
        linkText: "Política de Privacidad",
        headingRegex: /Política de Privacidad/i,
        reportField: "Política de Privacidad",
        screenshotLabel: "06-politica-de-privacidad",
      });
    });
  } finally {
    const report = {
      generatedAt: new Date().toISOString(),
      environment: {
        initialUrl: loginUrl || page.url(),
        currentUrl: page.url(),
      },
      results,
      legalUrls,
      screenshotPaths,
      errors: stepErrors,
    };

    const reportPath = path.join(ARTIFACTS_DIR, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

    console.log("\n=== FINAL REPORT ===");
    for (const field of REPORT_FIELDS) {
      console.log(`- ${field}: ${results[field]}`);
    }
    if (Object.keys(legalUrls).length > 0) {
      console.log("\nLegal URLs:");
      for (const [name, url] of Object.entries(legalUrls)) {
        console.log(`- ${name}: ${url}`);
      }
    }
    console.log(`\nReport path: ${reportPath}`);

    await context.close();
  }

  const hasFailures = Object.values(results).includes("FAIL");
  if (hasFailures) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error("Fatal run error:", error);
  process.exit(1);
});
