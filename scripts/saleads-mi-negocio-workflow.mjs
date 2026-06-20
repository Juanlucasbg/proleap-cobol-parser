import { chromium } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function settleUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 });
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch {
    // Some pages use long polling; DOM ready is enough.
  }
}

async function isVisible(locator, timeout = 5000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickPreferredByText(page, text) {
  const regex = new RegExp(escapeRegex(text), "i");
  const candidates = [
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByRole("menuitem", { name: regex }).first(),
    page.getByText(regex, { exact: false }).first()
  ];

  for (const locator of candidates) {
    if (await isVisible(locator, 1500)) {
      await locator.click();
      await settleUi(page);
      return;
    }
  }

  throw new Error(`No visible element found for text: "${text}"`);
}

async function expectVisibleByText(page, text, description = text) {
  const regex = typeof text === "string" ? new RegExp(escapeRegex(text), "i") : text;
  const candidates = [
    page.getByRole("heading", { name: regex }).first(),
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByText(regex, { exact: false }).first()
  ];

  for (const locator of candidates) {
    if (await isVisible(locator, 3000)) {
      return locator;
    }
  }

  throw new Error(`Expected visible text not found: ${description}`);
}

async function screenshot(page, screenshotsDir, fileName, fullPage = false) {
  const filePath = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function trySelectGoogleAccount(pages) {
  for (const page of pages) {
    const locator = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    if (await isVisible(locator, 2000)) {
      await locator.click();
      await settleUi(page);
      return true;
    }
  }
  return false;
}

function emptyStepReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "NOT_RUN", details: "", evidence: [] };
    return acc;
  }, {});
}

function markStep(report, field, status, details = "", evidence = []) {
  report.steps[field] = { status, details, evidence };
}

async function openLegalLinkAndValidate({
  page,
  context,
  linkText,
  expectedHeading,
  screenshotName,
  screenshotsDir,
  report
}) {
  const appUrlBefore = page.url();
  const newPagePromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);

  await clickPreferredByText(page, linkText);
  const newPage = await newPagePromise;

  let targetPage = page;
  if (newPage) {
    targetPage = newPage;
    await settleUi(targetPage);
    await targetPage.bringToFront();
  }

  await expectVisibleByText(targetPage, expectedHeading, `heading "${expectedHeading}"`);
  const contentLength = await targetPage.evaluate(() => document.body?.innerText?.trim().length || 0);
  if (contentLength < 120) {
    throw new Error(`Legal content appears too short (${contentLength} characters).`);
  }

  const shot = await screenshot(targetPage, screenshotsDir, screenshotName, true);
  report.finalUrls[linkText] = targetPage.url();

  if (newPage) {
    await targetPage.close();
    await page.bringToFront();
    await settleUi(page);
  } else if (page.url() !== appUrlBefore) {
    await page.goBack();
    await settleUi(page);
  }

  return shot;
}

async function run() {
  const runDir = path.join(process.cwd(), "artifacts", "saleads-mi-negocio", nowStamp());
  const screenshotsDir = path.join(runDir, "screenshots");
  await ensureDir(screenshotsDir);
  const startUrl = process.env.SALEADS_START_URL;

  if (!startUrl) {
    throw new Error(
      "SALEADS_START_URL is required for this standalone run. It can point to any SaleADS environment login page."
    );
  }

  const report = {
    name: "saleads_mi_negocio_full_test",
    startedAt: new Date().toISOString(),
    runDir,
    environment: {
      startUrl,
      headless: process.env.HEADLESS !== "false"
    },
    finalUrls: {},
    steps: emptyStepReport()
  };

  const browser = await chromium.launch({
    headless: process.env.HEADLESS !== "false",
    slowMo: Number(process.env.SLOW_MO_MS || 250)
  });

  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    await page.goto(startUrl, { waitUntil: "domcontentloaded", timeout: 45000 });
    await settleUi(page);

    // Step 1 - Login with Google.
    try {
      const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
      await clickPreferredByText(page, "Google");
      const popup = await popupPromise;
      if (popup) {
        await settleUi(popup);
      }

      await trySelectGoogleAccount(popup ? [popup, page] : [page]);
      await settleUi(page);

      await expectVisibleByText(page, /Negocio|Mi Negocio|Dashboard|Inicio/i, "main app interface");
      const sidebar = page.locator("aside, nav").first();
      if (!(await isVisible(sidebar, 4000))) {
        throw new Error("Left sidebar navigation is not visible.");
      }

      const shot = await screenshot(page, screenshotsDir, "01-dashboard-loaded.png");
      markStep(report, "Login", "PASS", "Main interface and sidebar are visible after login.", [shot]);
    } catch (error) {
      markStep(report, "Login", "FAIL", error.message);
    }

    // Step 2 - Open Mi Negocio menu.
    try {
      if (await isVisible(page.getByText(/Negocio/i).first(), 1500)) {
        await clickPreferredByText(page, "Negocio");
      }
      await clickPreferredByText(page, "Mi Negocio");
      await expectVisibleByText(page, "Agregar Negocio");
      await expectVisibleByText(page, "Administrar Negocios");
      const shot = await screenshot(page, screenshotsDir, "02-mi-negocio-menu-expanded.png");
      markStep(report, "Mi Negocio menu", "PASS", "Mi Negocio submenu expanded with expected options.", [shot]);
    } catch (error) {
      markStep(report, "Mi Negocio menu", "FAIL", error.message);
    }

    // Step 3 - Validate Agregar Negocio modal.
    try {
      await clickPreferredByText(page, "Agregar Negocio");
      await expectVisibleByText(page, "Crear Nuevo Negocio");
      await expectVisibleByText(page, "Nombre del Negocio");
      await expectVisibleByText(page, "Tienes 2 de 3 negocios");
      await expectVisibleByText(page, "Cancelar");
      await expectVisibleByText(page, "Crear Negocio");

      const nameInput = page.getByLabel(/Nombre del Negocio/i).first();
      if (await isVisible(nameInput, 1500)) {
        await nameInput.fill("Negocio Prueba Automatización");
      }
      await clickPreferredByText(page, "Cancelar");
      const shot = await screenshot(page, screenshotsDir, "03-agregar-negocio-modal.png");
      markStep(report, "Agregar Negocio modal", "PASS", "Modal content validated and closed with Cancelar.", [shot]);
    } catch (error) {
      markStep(report, "Agregar Negocio modal", "FAIL", error.message);
    }

    // Step 4 - Open Administrar Negocios.
    try {
      if (!(await isVisible(page.getByText(/Administrar Negocios/i).first(), 1000))) {
        await clickPreferredByText(page, "Mi Negocio");
      }
      await clickPreferredByText(page, "Administrar Negocios");
      await expectVisibleByText(page, "Información General");
      await expectVisibleByText(page, "Detalles de la Cuenta");
      await expectVisibleByText(page, "Tus Negocios");
      await expectVisibleByText(page, "Sección Legal");
      const shot = await screenshot(page, screenshotsDir, "04-administrar-negocios-view.png", true);
      markStep(report, "Administrar Negocios view", "PASS", "Account sections are visible.", [shot]);
    } catch (error) {
      markStep(report, "Administrar Negocios view", "FAIL", error.message);
    }

    // Step 5 - Validate Información General.
    try {
      await expectVisibleByText(page, "BUSINESS PLAN");
      await expectVisibleByText(page, "Cambiar Plan");
      await expectVisibleByText(page, /@/, "user email");
      await expectVisibleByText(page, /Nombre|Name|Usuario/i, "user name");
      markStep(report, "Información General", "PASS", "User identity and plan details are visible.");
    } catch (error) {
      markStep(report, "Información General", "FAIL", error.message);
    }

    // Step 6 - Validate Detalles de la Cuenta.
    try {
      await expectVisibleByText(page, "Cuenta creada");
      await expectVisibleByText(page, "Estado activo");
      await expectVisibleByText(page, "Idioma seleccionado");
      markStep(report, "Detalles de la Cuenta", "PASS", "Account detail fields are visible.");
    } catch (error) {
      markStep(report, "Detalles de la Cuenta", "FAIL", error.message);
    }

    // Step 7 - Validate Tus Negocios.
    try {
      await expectVisibleByText(page, "Tus Negocios");
      await expectVisibleByText(page, "Agregar Negocio");
      await expectVisibleByText(page, "Tienes 2 de 3 negocios");
      const businessList = page.locator("table tbody tr, ul li, [role='row']").first();
      if (!(await isVisible(businessList, 3000))) {
        throw new Error("Business list is not visible.");
      }
      markStep(report, "Tus Negocios", "PASS", "Business list and counters are visible.");
    } catch (error) {
      markStep(report, "Tus Negocios", "FAIL", error.message);
    }

    // Step 8 - Validate Términos y Condiciones.
    try {
      const shot = await openLegalLinkAndValidate({
        page,
        context,
        linkText: "Términos y Condiciones",
        expectedHeading: /Términos y Condiciones/i,
        screenshotName: "08-terminos-y-condiciones.png",
        screenshotsDir,
        report
      });
      markStep(report, "Términos y Condiciones", "PASS", "Legal page heading and content validated.", [shot]);
    } catch (error) {
      markStep(report, "Términos y Condiciones", "FAIL", error.message);
    }

    // Step 9 - Validate Política de Privacidad.
    try {
      const shot = await openLegalLinkAndValidate({
        page,
        context,
        linkText: "Política de Privacidad",
        expectedHeading: /Política de Privacidad/i,
        screenshotName: "09-politica-de-privacidad.png",
        screenshotsDir,
        report
      });
      markStep(report, "Política de Privacidad", "PASS", "Legal page heading and content validated.", [shot]);
    } catch (error) {
      markStep(report, "Política de Privacidad", "FAIL", error.message);
    }
  } finally {
    report.finishedAt = new Date().toISOString();
    const reportPath = path.join(runDir, "report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    await browser.close();

    console.log("Final Report");
    for (const field of REPORT_FIELDS) {
      const step = report.steps[field];
      console.log(`- ${field}: ${step.status}${step.details ? ` (${step.details})` : ""}`);
    }
    console.log(`Report file: ${reportPath}`);
    if (Object.values(report.steps).some((step) => step.status !== "PASS")) {
      process.exitCode = 1;
    }
  }
}

run().catch((error) => {
  console.error("Unhandled error during SaleADS workflow validation:", error);
  process.exitCode = 1;
});
