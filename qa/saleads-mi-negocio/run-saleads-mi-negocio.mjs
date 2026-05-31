import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS = [
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

const outputDir =
  process.env.SALEADS_EVIDENCE_DIR ||
  path.resolve(
    process.cwd(),
    "artifacts",
    `saleads-mi-negocio-${new Date().toISOString().replaceAll(":", "-")}`
  );

const report = {
  name: "saleads_mi_negocio_full_test",
  generatedAt: new Date().toISOString(),
  environment: {
    startUrl: process.env.SALEADS_START_URL || null,
    connectedMode: Boolean(
      process.env.PW_CONNECT_WS_ENDPOINT ||
        process.env.PLAYWRIGHT_WS_ENDPOINT ||
        process.env.PW_CDP_ENDPOINT
    ),
    outputDir
  },
  steps: Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      { status: "NOT_RUN", validations: [], evidence: [], details: [] }
    ])
  ),
  legalUrls: {
    termsAndConditions: null,
    privacyPolicy: null
  }
};

let browser;
let context;
let appPage;
let screenshotCounter = 0;

function escapeRegex(rawValue) {
  return rawValue.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function addValidation(step, name, passed, detail = "") {
  step.validations.push({ name, passed, detail });
  if (!passed && detail) {
    step.details.push(detail);
  }
}

function finalizeStep(step) {
  const hasFailedValidation = step.validations.some((item) => !item.passed);
  const hasErrors = step.details.length > 0;
  step.status = hasFailedValidation || hasErrors ? "FAIL" : "PASS";
}

async function waitForUi(page) {
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch {
    await page.waitForLoadState("domcontentloaded", { timeout: 10000 });
  }
  await page.waitForTimeout(600);
}

function candidateLocators(page, needle) {
  return [
    page.getByRole("button", { name: needle }),
    page.getByRole("link", { name: needle }),
    page.getByRole("menuitem", { name: needle }),
    page.getByRole("tab", { name: needle }),
    page.getByRole("option", { name: needle }),
    page.getByText(needle)
  ];
}

async function firstVisibleLocator(page, needle, timeout = 12000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    for (const locator of candidateLocators(page, needle)) {
      if ((await locator.count()) === 0) {
        continue;
      }
      const first = locator.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
    await page.waitForTimeout(300);
  }
  return null;
}

async function clickByText(page, needle, timeout = 12000) {
  const locator = await firstVisibleLocator(page, needle, timeout);
  if (!locator) {
    throw new Error(`No visible element found for: ${needle}`);
  }
  await locator.click();
  await waitForUi(page);
  return locator;
}

async function isVisibleText(page, needle, timeout = 8000) {
  return Boolean(await firstVisibleLocator(page, needle, timeout));
}

async function capture(page, label, fullPage = false) {
  screenshotCounter += 1;
  const fileName = `${String(screenshotCounter).padStart(2, "0")}-${label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")}.png`;
  const filePath = path.join(outputDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function runStep(field, runner) {
  const step = report.steps[field];
  try {
    await runner(step);
  } catch (error) {
    step.details.push(error instanceof Error ? error.message : String(error));
  } finally {
    finalizeStep(step);
  }
}

async function withLinkedPage(page, linkRegex) {
  const existingPages = new Set(context.pages());
  const newPagePromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickByText(page, linkRegex);
  const poppedPage = await newPagePromise;
  if (poppedPage && !existingPages.has(poppedPage)) {
    await poppedPage.waitForLoadState("domcontentloaded", { timeout: 15000 });
    await waitForUi(poppedPage);
    return { targetPage: poppedPage, openedNewTab: true };
  }
  await waitForUi(page);
  return { targetPage: page, openedNewTab: false };
}

async function initBrowserAndPage() {
  const wsEndpoint =
    process.env.PW_CONNECT_WS_ENDPOINT || process.env.PLAYWRIGHT_WS_ENDPOINT;
  const cdpEndpoint = process.env.PW_CDP_ENDPOINT;

  if (cdpEndpoint) {
    browser = await chromium.connectOverCDP(cdpEndpoint);
  } else if (wsEndpoint) {
    browser = await chromium.connect(wsEndpoint);
  } else {
    browser = await chromium.launch({ headless: process.env.HEADLESS !== "false" });
  }

  context =
    browser.contexts()[0] ||
    (await browser.newContext({
      viewport: { width: 1440, height: 900 }
    }));

  appPage = context.pages()[0] || (await context.newPage());
  if (process.env.SALEADS_START_URL) {
    await appPage.goto(process.env.SALEADS_START_URL, {
      waitUntil: "domcontentloaded"
    });
    await waitForUi(appPage);
  }

  if (!process.env.SALEADS_START_URL && appPage.url() === "about:blank") {
    throw new Error(
      "No initial SaleADS page detected. Set SALEADS_START_URL or connect to an existing browser session already on the SaleADS login page."
    );
  }
}

async function maybeSelectGoogleAccount() {
  const emailRegex = new RegExp(escapeRegex(ACCOUNT_EMAIL), "i");
  const deadline = Date.now() + 20000;
  while (Date.now() < deadline) {
    for (const page of context.pages()) {
      const accountOption = await firstVisibleLocator(page, emailRegex, 400);
      if (accountOption) {
        await accountOption.click();
        await waitForUi(page);
        return true;
      }
    }
    await appPage.waitForTimeout(500);
  }
  return false;
}

async function runWorkflow() {
  await fs.mkdir(outputDir, { recursive: true });
  await initBrowserAndPage();

  await runStep("Login", async (step) => {
    const loginPopupPromise = appPage
      .waitForEvent("popup", { timeout: 7000 })
      .catch(() => null);
    await clickByText(
      appPage,
      /sign in with google|iniciar sesión con google|ingresar con google|continuar con google|google/i
    );
    const popup = await loginPopupPromise;
    if (popup) {
      await waitForUi(popup);
    }
    await maybeSelectGoogleAccount();

    const sidebarVisible = await isVisibleText(appPage, /Negocio|Mi Negocio/i, 30000);
    addValidation(step, "Main application interface appears", sidebarVisible, sidebarVisible ? "" : "Sidebar/navigation text was not found after login.");
    addValidation(step, "Left sidebar navigation is visible", sidebarVisible, sidebarVisible ? "" : "No visible left navigation element matched Negocio/Mi Negocio.");

    const dashboardShot = await capture(appPage, "dashboard-loaded");
    step.evidence.push(dashboardShot);
  });

  await runStep("Mi Negocio menu", async (step) => {
    await clickByText(appPage, /Negocio/i);
    await clickByText(appPage, /Mi Negocio/i);

    const agregarVisible = await isVisibleText(appPage, /Agregar Negocio/i);
    const administrarVisible = await isVisibleText(appPage, /Administrar Negocios/i);

    addValidation(step, "Mi Negocio submenu expands", agregarVisible || administrarVisible, "Neither expected submenu option became visible.");
    addValidation(step, "Agregar Negocio is visible", agregarVisible, "Agregar Negocio was not visible.");
    addValidation(step, "Administrar Negocios is visible", administrarVisible, "Administrar Negocios was not visible.");

    const menuShot = await capture(appPage, "mi-negocio-menu-expanded");
    step.evidence.push(menuShot);
  });

  await runStep("Agregar Negocio modal", async (step) => {
    await clickByText(appPage, /Agregar Negocio/i);

    const modalTitleVisible = await isVisibleText(appPage, /Crear Nuevo Negocio/i, 12000);
    const inputVisible = await isVisibleText(appPage, /Nombre del Negocio/i, 8000);
    const quotaVisible = await isVisibleText(appPage, /Tienes 2 de 3 negocios/i, 8000);
    const cancelVisible = await isVisibleText(appPage, /Cancelar/i, 8000);
    const createVisible = await isVisibleText(appPage, /Crear Negocio/i, 8000);

    addValidation(step, "Modal title 'Crear Nuevo Negocio' is visible", modalTitleVisible, "Expected modal title was not found.");
    addValidation(step, "Input field 'Nombre del Negocio' exists", inputVisible, "Nombre del Negocio input/label was not found.");
    addValidation(step, "Text 'Tienes 2 de 3 negocios' is visible", quotaVisible, "Business quota text was not found.");
    addValidation(step, "Buttons 'Cancelar' and 'Crear Negocio' are present", cancelVisible && createVisible, "Expected modal buttons were not both visible.");

    const inputLocator = await firstVisibleLocator(appPage, /Nombre del Negocio/i, 2000);
    if (inputLocator) {
      await inputLocator.click();
      await inputLocator.fill("Negocio Prueba Automatización").catch(() => {});
      await waitForUi(appPage);
    }
    await clickByText(appPage, /Cancelar/i);

    const modalShot = await capture(appPage, "agregar-negocio-modal");
    step.evidence.push(modalShot);
  });

  await runStep("Administrar Negocios view", async (step) => {
    if (!(await isVisibleText(appPage, /Administrar Negocios/i, 2500))) {
      await clickByText(appPage, /Mi Negocio/i);
    }
    await clickByText(appPage, /Administrar Negocios/i);

    const infoGeneral = await isVisibleText(appPage, /Información General/i, 15000);
    const accountDetails = await isVisibleText(appPage, /Detalles de la Cuenta/i, 8000);
    const businesses = await isVisibleText(appPage, /Tus Negocios/i, 8000);
    const legal = await isVisibleText(appPage, /Sección Legal/i, 8000);

    addValidation(step, "Section 'Información General' exists", infoGeneral, "Información General section was not found.");
    addValidation(step, "Section 'Detalles de la Cuenta' exists", accountDetails, "Detalles de la Cuenta section was not found.");
    addValidation(step, "Section 'Tus Negocios' exists", businesses, "Tus Negocios section was not found.");
    addValidation(step, "Section 'Sección Legal' exists", legal, "Sección Legal section was not found.");

    const accountShot = await capture(appPage, "administrar-negocios-page", true);
    step.evidence.push(accountShot);
  });

  await runStep("Información General", async (step) => {
    const emailVisible = await isVisibleText(
      appPage,
      /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i,
      8000
    );
    const nameHintVisible = await isVisibleText(
      appPage,
      /Nombre|Usuario|Perfil|Cuenta/i,
      8000
    );
    const planVisible = await isVisibleText(appPage, /BUSINESS PLAN/i, 8000);
    const changePlanVisible = await isVisibleText(appPage, /Cambiar Plan/i, 8000);

    addValidation(step, "User name is visible", nameHintVisible, "User name label/value hint was not detected in Información General.");
    addValidation(step, "User email is visible", emailVisible, "A visible email address was not detected.");
    addValidation(step, "Text 'BUSINESS PLAN' is visible", planVisible, "BUSINESS PLAN text was not found.");
    addValidation(step, "Button 'Cambiar Plan' is visible", changePlanVisible, "Cambiar Plan button was not found.");
  });

  await runStep("Detalles de la Cuenta", async (step) => {
    const createdVisible = await isVisibleText(appPage, /Cuenta creada/i, 8000);
    const activeVisible = await isVisibleText(appPage, /Estado activo/i, 8000);
    const languageVisible = await isVisibleText(appPage, /Idioma seleccionado/i, 8000);

    addValidation(step, "'Cuenta creada' is visible", createdVisible, "Cuenta creada text was not found.");
    addValidation(step, "'Estado activo' is visible", activeVisible, "Estado activo text was not found.");
    addValidation(step, "'Idioma seleccionado' is visible", languageVisible, "Idioma seleccionado text was not found.");
  });

  await runStep("Tus Negocios", async (step) => {
    const businessesHeading = await isVisibleText(appPage, /Tus Negocios/i, 8000);
    const addBusinessButton = await isVisibleText(appPage, /Agregar Negocio/i, 8000);
    const quotaVisible = await isVisibleText(appPage, /Tienes 2 de 3 negocios/i, 8000);

    addValidation(step, "Business list is visible", businessesHeading, "Tus Negocios section/list was not visible.");
    addValidation(step, "Button 'Agregar Negocio' exists", addBusinessButton, "Agregar Negocio button was not found in Tus Negocios.");
    addValidation(step, "Text 'Tienes 2 de 3 negocios' is visible", quotaVisible, "Business quota text was not found.");
  });

  await runStep("Términos y Condiciones", async (step) => {
    const { targetPage, openedNewTab } = await withLinkedPage(appPage, /Términos y Condiciones/i);

    const headingVisible = await isVisibleText(targetPage, /Términos y Condiciones/i, 15000);
    const bodyText = await targetPage.locator("body").innerText();
    const legalContentVisible = bodyText.replace(/\s+/g, " ").length > 150;

    addValidation(step, "The page contains the heading 'Términos y Condiciones'", headingVisible, "Terms page heading was not found.");
    addValidation(step, "Legal content text is visible", legalContentVisible, "Legal content text appears empty or too short.");

    report.legalUrls.termsAndConditions = targetPage.url();
    const shot = await capture(targetPage, "terminos-y-condiciones", true);
    step.evidence.push(shot);

    if (openedNewTab) {
      await targetPage.close();
      await appPage.bringToFront();
      await waitForUi(appPage);
    } else {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(appPage);
    }
  });

  await runStep("Política de Privacidad", async (step) => {
    const { targetPage, openedNewTab } = await withLinkedPage(appPage, /Política de Privacidad/i);

    const headingVisible = await isVisibleText(targetPage, /Política de Privacidad/i, 15000);
    const bodyText = await targetPage.locator("body").innerText();
    const legalContentVisible = bodyText.replace(/\s+/g, " ").length > 150;

    addValidation(step, "The page contains the heading 'Política de Privacidad'", headingVisible, "Privacy page heading was not found.");
    addValidation(step, "Legal content text is visible", legalContentVisible, "Legal content text appears empty or too short.");

    report.legalUrls.privacyPolicy = targetPage.url();
    const shot = await capture(targetPage, "politica-de-privacidad", true);
    step.evidence.push(shot);

    if (openedNewTab) {
      await targetPage.close();
      await appPage.bringToFront();
      await waitForUi(appPage);
    } else {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(appPage);
    }
  });
}

function buildSummaryMarkdown() {
  const lines = [];
  lines.push("# SaleADS Mi Negocio Workflow Report");
  lines.push("");
  lines.push(`- Generated at: ${report.generatedAt}`);
  lines.push(`- Evidence directory: ${outputDir}`);
  lines.push("");
  lines.push("| Step | Status |");
  lines.push("| --- | --- |");
  for (const field of REPORT_FIELDS) {
    lines.push(`| ${field} | ${report.steps[field].status} |`);
  }
  lines.push("");
  lines.push(`- Términos y Condiciones URL: ${report.legalUrls.termsAndConditions || "N/A"}`);
  lines.push(`- Política de Privacidad URL: ${report.legalUrls.privacyPolicy || "N/A"}`);
  return lines.join("\n");
}

async function main() {
  let exitCode = 0;
  try {
    await runWorkflow();
  } catch (error) {
    exitCode = 1;
    for (const field of REPORT_FIELDS) {
      if (report.steps[field].status === "NOT_RUN") {
        report.steps[field].status = "FAIL";
        report.steps[field].details.push(
          `Execution stopped before running this step: ${
            error instanceof Error ? error.message : String(error)
          }`
        );
      }
    }
  } finally {
    const summary = buildSummaryMarkdown();
    await fs.writeFile(
      path.join(outputDir, "saleads-mi-negocio-report.json"),
      JSON.stringify(report, null, 2),
      "utf-8"
    );
    await fs.writeFile(
      path.join(outputDir, "saleads-mi-negocio-report.md"),
      summary,
      "utf-8"
    );
    console.log(summary);

    const hasFail = REPORT_FIELDS.some((field) => report.steps[field].status !== "PASS");
    if (hasFail) {
      exitCode = 1;
    }
    if (browser) {
      await browser.close().catch(() => {});
    }
    process.exitCode = exitCode;
  }
}

await main();
