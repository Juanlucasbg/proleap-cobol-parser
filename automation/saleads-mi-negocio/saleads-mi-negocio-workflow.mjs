import { chromium } from "playwright";
import { promises as fs } from "node:fs";
import path from "node:path";

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL =
  process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
const HEADLESS = process.env.HEADLESS !== "false";
const NOW = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACT_ROOT =
  process.env.SALEADS_ARTIFACTS_DIR ||
  path.resolve(process.cwd(), "artifacts", `saleads-mi-negocio-${NOW}`);
const SCREENSHOT_DIR = path.join(ARTIFACT_ROOT, "screenshots");
const REPORT_PATH = path.join(ARTIFACT_ROOT, "report.json");
const SUMMARY_PATH = path.join(ARTIFACT_ROOT, "summary.md");

const report = {
  generatedAt: new Date().toISOString(),
  artifactsDirectory: ARTIFACT_ROOT,
  environment: {
    loginUrl: LOGIN_URL || null,
    headless: HEADLESS
  },
  results: {
    Login: { status: "FAIL", details: [] },
    "Mi Negocio menu": { status: "FAIL", details: [] },
    "Agregar Negocio modal": { status: "FAIL", details: [] },
    "Administrar Negocios view": { status: "FAIL", details: [] },
    "Información General": { status: "FAIL", details: [] },
    "Detalles de la Cuenta": { status: "FAIL", details: [] },
    "Tus Negocios": { status: "FAIL", details: [] },
    "Términos y Condiciones": { status: "FAIL", details: [] },
    "Política de Privacidad": { status: "FAIL", details: [] }
  },
  screenshots: [],
  legalUrls: {
    terminosYCondiciones: null,
    politicaDePrivacidad: null
  }
};

async function ensureArtifacts() {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
}

async function writeOutputs() {
  const allPassed = Object.values(report.results).every((item) => item.status === "PASS");
  report.overallStatus = allPassed ? "PASS" : "FAIL";

  await fs.writeFile(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
  await fs.writeFile(SUMMARY_PATH, buildSummaryMarkdown(), "utf8");

  console.log(buildSummaryMarkdown());
  console.log(`\nJSON report: ${REPORT_PATH}`);
  console.log(`Summary report: ${SUMMARY_PATH}`);
}

function buildSummaryMarkdown() {
  const lines = [
    "# SaleADS Mi Negocio Workflow Report",
    "",
    `Generated at: ${report.generatedAt}`,
    "",
    "| Checkpoint | Status |",
    "|---|---|"
  ];

  for (const [field, result] of Object.entries(report.results)) {
    lines.push(`| ${field} | ${result.status} |`);
  }

  lines.push("");
  lines.push(`Overall: **${report.overallStatus || "FAIL"}**`);
  lines.push("");

  if (report.legalUrls.terminosYCondiciones || report.legalUrls.politicaDePrivacidad) {
    lines.push("## Final URLs");
    if (report.legalUrls.terminosYCondiciones) {
      lines.push(`- Términos y Condiciones: ${report.legalUrls.terminosYCondiciones}`);
    }
    if (report.legalUrls.politicaDePrivacidad) {
      lines.push(`- Política de Privacidad: ${report.legalUrls.politicaDePrivacidad}`);
    }
    lines.push("");
  }

  const failures = Object.entries(report.results).filter(([, value]) => value.status !== "PASS");
  if (failures.length > 0) {
    lines.push("## Failure Details");
    for (const [field, value] of failures) {
      lines.push(`- **${field}**`);
      for (const detail of value.details) {
        lines.push(`  - ${detail}`);
      }
    }
  }

  return lines.join("\n");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 });
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch {
    // Keep the flow moving in pages with long polling.
  }
  await page.waitForTimeout(600);
}

async function isVisible(locator, timeout = 2500) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

function locatorCandidates(page, pattern) {
  return [
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByRole("menuitem", { name: pattern }),
    page.getByRole("tab", { name: pattern }),
    page.getByRole("option", { name: pattern }),
    page.getByText(pattern)
  ];
}

async function clickByVisibleText(page, patterns, { waitAfterClick = true } = {}) {
  for (const pattern of patterns) {
    for (const candidate of locatorCandidates(page, pattern)) {
      if (await isVisible(candidate, 1500)) {
        await candidate.first().click();
        if (waitAfterClick) {
          await waitForUi(page);
        }
        return;
      }
    }
  }

  throw new Error(`Unable to find a clickable element for patterns: ${patterns.map(String).join(", ")}`);
}

async function assertVisibleText(page, patterns, message) {
  for (const pattern of patterns) {
    for (const candidate of locatorCandidates(page, pattern)) {
      if (await isVisible(candidate, 3000)) {
        return;
      }
    }
  }

  throw new Error(message);
}

async function capture(page, name, { fullPage = false } = {}) {
  const safeName = name.replace(/[^a-zA-Z0-9-_]/g, "_");
  const filePath = path.join(SCREENSHOT_DIR, `${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });
  report.screenshots.push(filePath);
  return filePath;
}

function addStepFailure(field, error) {
  report.results[field].status = "FAIL";
  report.results[field].details.push(error instanceof Error ? error.message : String(error));
}

async function runValidation(field, fn) {
  try {
    await fn();
    report.results[field].status = "PASS";
    report.results[field].details.push("Validation completed successfully.");
  } catch (error) {
    addStepFailure(field, error);
  }
}

function extractLikelyUserName(sectionText) {
  const excludedTerms = [
    "información general",
    "business plan",
    "cambiar plan",
    "correo",
    "email",
    "cuenta creada",
    "estado activo",
    "idioma seleccionado",
    "tienes",
    "negocios"
  ];

  const lines = sectionText
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length >= 3 && /[A-Za-zÁÉÍÓÚÑáéíóúñ]/.test(line));

  return lines.find((line) => {
    const lowerLine = line.toLowerCase();
    return !excludedTerms.some((term) => lowerLine.includes(term)) && !/@/.test(line);
  });
}

async function openLegalDocument({
  appPage,
  linkPatterns,
  headingPatterns,
  reportField,
  screenshotName
}) {
  const context = appPage.context();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const navigationPromise = appPage.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 10000 }).catch(
    () => null
  );

  await clickByVisibleText(appPage, linkPatterns, { waitAfterClick: false });
  const popup = await popupPromise;

  let legalPage = appPage;
  if (popup) {
    legalPage = popup;
    await waitForUi(legalPage);
  } else {
    await navigationPromise;
    await waitForUi(legalPage);
  }

  await assertVisibleText(
    legalPage,
    headingPatterns,
    `${reportField}: heading was not visible after opening the legal page.`
  );

  const bodyText = await legalPage.locator("body").innerText();
  if (bodyText.trim().length < 200) {
    throw new Error(`${reportField}: legal content appears empty or too short.`);
  }

  await capture(legalPage, screenshotName, { fullPage: true });
  const finalUrl = legalPage.url();

  if (popup) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

if (!LOGIN_URL) {
  console.error(
    "Missing URL. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL) to the current SaleADS login page."
  );
  process.exit(1);
}

let browser;
try {
  await ensureArtifacts();

  browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 }
  });
  const page = await context.newPage();

  await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runValidation("Login", async () => {
    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await clickByVisibleText(page, [/sign in with google/i, /iniciar sesi[oó]n con google/i, /google/i]);

    const popup = await popupPromise;
    if (popup) {
      await waitForUi(popup);
      if (await isVisible(popup.getByText(GOOGLE_ACCOUNT, { exact: true }), 5000)) {
        await popup.getByText(GOOGLE_ACCOUNT, { exact: true }).click();
      }
      await popup.waitForEvent("close", { timeout: 15000 }).catch(() => {});
      await waitForUi(page);
    } else if (await isVisible(page.getByText(GOOGLE_ACCOUNT, { exact: true }), 4000)) {
      await page.getByText(GOOGLE_ACCOUNT, { exact: true }).click();
      await waitForUi(page);
    }

    await assertVisibleText(
      page,
      [/sidebar/i, /^Negocio$/i, /Mi Negocio/i, /Dashboard/i],
      "Main application interface did not appear after Google login."
    );

    const sidebarVisible =
      (await isVisible(page.locator("aside"), 4000)) ||
      (await isVisible(page.getByRole("navigation"), 4000)) ||
      (await isVisible(page.locator("[class*='sidebar']"), 4000));

    if (!sidebarVisible) {
      throw new Error("Left sidebar navigation is not visible.");
    }

    await capture(page, "01-dashboard-loaded", { fullPage: true });
  });

  await runValidation("Mi Negocio menu", async () => {
    await clickByVisibleText(page, [/Negocio/i]);
    await clickByVisibleText(page, [/Mi Negocio/i]);

    await assertVisibleText(page, [/Agregar Negocio/i], "The 'Agregar Negocio' option is not visible.");
    await assertVisibleText(page, [/Administrar Negocios/i], "The 'Administrar Negocios' option is not visible.");
    await capture(page, "02-mi-negocio-menu-expanded", { fullPage: true });
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, [/Agregar Negocio/i]);

    await assertVisibleText(page, [/Crear Nuevo Negocio/i], "Modal title 'Crear Nuevo Negocio' is not visible.");
    await assertVisibleText(page, [/Nombre del Negocio/i], "Input label 'Nombre del Negocio' is missing.");
    await assertVisibleText(page, [/Tienes 2 de 3 negocios/i], "Expected business quota text is missing.");
    await assertVisibleText(page, [/Cancelar/i], "Button 'Cancelar' is missing.");
    await assertVisibleText(page, [/Crear Negocio/i], "Button 'Crear Negocio' is missing.");
    await capture(page, "03-crear-nuevo-negocio-modal", { fullPage: true });

    const nameInput = page.getByLabel(/Nombre del Negocio/i);
    if (await isVisible(nameInput, 2500)) {
      await nameInput.fill("Negocio Prueba Automatización");
    } else if (await isVisible(page.getByPlaceholder(/Nombre del Negocio/i), 2500)) {
      await page.getByPlaceholder(/Nombre del Negocio/i).fill("Negocio Prueba Automatización");
    }

    await clickByVisibleText(page, [/Cancelar/i]);
  });

  await runValidation("Administrar Negocios view", async () => {
    if (!(await isVisible(page.getByText(/Administrar Negocios/i), 1500))) {
      if (!(await isVisible(page.getByText(/Mi Negocio/i), 1500))) {
        await clickByVisibleText(page, [/Negocio/i]);
      }
      await clickByVisibleText(page, [/Mi Negocio/i]);
    }

    await clickByVisibleText(page, [/Administrar Negocios/i]);
    await assertVisibleText(page, [/Información General/i], "Section 'Información General' was not found.");
    await assertVisibleText(page, [/Detalles de la Cuenta/i], "Section 'Detalles de la Cuenta' was not found.");
    await assertVisibleText(page, [/Tus Negocios/i], "Section 'Tus Negocios' was not found.");
    await assertVisibleText(page, [/Secci[oó]n Legal/i], "Section 'Sección Legal' was not found.");
    await capture(page, "04-administrar-negocios-view", { fullPage: true });
  });

  await runValidation("Información General", async () => {
    await assertVisibleText(page, [/Información General/i], "Section title 'Información General' is missing.");

    const emailLocator = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    if (!(await isVisible(emailLocator, 4000))) {
      throw new Error("User email is not visible in the account view.");
    }

    await assertVisibleText(page, [/BUSINESS PLAN/i], "Text 'BUSINESS PLAN' is not visible.");
    await assertVisibleText(page, [/Cambiar Plan/i], "Button 'Cambiar Plan' is not visible.");

    const infoHeading = page.getByText(/Información General/i).first();
    const infoSection = infoHeading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
    const sectionText = await infoSection.innerText().catch(() => "");
    const probableName = extractLikelyUserName(sectionText);
    if (!probableName) {
      throw new Error("Could not confirm a visible user name in 'Información General'.");
    }
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await assertVisibleText(page, [/Cuenta creada/i], "'Cuenta creada' is not visible.");
    await assertVisibleText(page, [/Estado activo/i], "'Estado activo' is not visible.");
    await assertVisibleText(page, [/Idioma seleccionado/i], "'Idioma seleccionado' is not visible.");
  });

  await runValidation("Tus Negocios", async () => {
    await assertVisibleText(page, [/Tus Negocios/i], "Section title 'Tus Negocios' is not visible.");
    await assertVisibleText(page, [/Agregar Negocio/i], "Button 'Agregar Negocio' is not visible.");
    await assertVisibleText(page, [/Tienes 2 de 3 negocios/i], "Text 'Tienes 2 de 3 negocios' is missing.");

    const businessesHeading = page.getByText(/Tus Negocios/i).first();
    const businessesSection = businessesHeading.locator(
      "xpath=ancestor::*[self::section or self::article or self::div][1]"
    );
    const sectionText = await businessesSection.innerText().catch(() => "");
    const contentLength = sectionText.replace(/Tus Negocios|Agregar Negocio|Tienes 2 de 3 negocios/gi, "").trim().length;
    if (contentLength < 10) {
      throw new Error("The business list area appears empty.");
    }
  });

  await runValidation("Términos y Condiciones", async () => {
    report.legalUrls.terminosYCondiciones = await openLegalDocument({
      appPage: page,
      linkPatterns: [/T[eé]rminos y Condiciones/i],
      headingPatterns: [/T[eé]rminos y Condiciones/i],
      reportField: "Términos y Condiciones",
      screenshotName: "05-terminos-y-condiciones"
    });
  });

  await runValidation("Política de Privacidad", async () => {
    report.legalUrls.politicaDePrivacidad = await openLegalDocument({
      appPage: page,
      linkPatterns: [/Pol[ií]tica de Privacidad/i],
      headingPatterns: [/Pol[ií]tica de Privacidad/i],
      reportField: "Política de Privacidad",
      screenshotName: "06-politica-de-privacidad"
    });
  });
} catch (error) {
  console.error("Unexpected execution error:", error);
} finally {
  if (browser) {
    await browser.close();
  }
  await writeOutputs();

  const failedSteps = Object.values(report.results).filter((value) => value.status !== "PASS");
  if (failedSteps.length > 0) {
    process.exitCode = 1;
  }
}
