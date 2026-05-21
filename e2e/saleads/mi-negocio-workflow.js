#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const { chromium } = require("@playwright/test");

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
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

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function reportLine(message) {
  process.stdout.write(`[saleads-mi-negocio] ${message}\n`);
}

function toMarkdownReport(report) {
  const lines = [];
  lines.push("# SaleADS Mi Negocio Workflow Report");
  lines.push("");
  lines.push(`- Started: ${report.startedAt}`);
  lines.push(`- Finished: ${report.finishedAt}`);
  lines.push(`- Base URL: ${report.baseUrl || "N/A"}`);
  lines.push(`- Google Account: ${report.googleAccount}`);
  lines.push(`- Overall: ${report.overallStatus}`);
  lines.push("");
  lines.push("## Step Results");
  lines.push("");
  lines.push("| Step | Status | Notes |");
  lines.push("| --- | --- | --- |");
  for (const stepName of REPORT_FIELDS) {
    const step = report.steps[stepName];
    const notes = step.notes.length ? step.notes.join("<br/>") : "-";
    lines.push(`| ${stepName} | ${step.status} | ${notes} |`);
  }
  lines.push("");
  lines.push("## Evidence");
  lines.push("");
  for (const item of report.evidence) {
    lines.push(`- ${item.label}: ${item.path}`);
  }
  lines.push("");
  lines.push("## Legal URLs");
  lines.push("");
  lines.push(`- Términos y Condiciones: ${report.legalUrls.terms || "N/A"}`);
  lines.push(`- Política de Privacidad: ${report.legalUrls.privacy || "N/A"}`);
  lines.push("");
  return lines.join("\n");
}

async function visible(locator, timeout = 5000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch (_) {
    return false;
  }
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(900);
}

async function clickByVisibleText(page, textCandidates) {
  for (const textCandidate of textCandidates) {
    const regex = new RegExp(escapeRegExp(textCandidate), "i");
    const strategies = [
      page.getByRole("button", { name: regex }),
      page.getByRole("link", { name: regex }),
      page.getByRole("menuitem", { name: regex }),
      page.getByRole("tab", { name: regex }),
      page.getByText(regex)
    ];

    for (const locator of strategies) {
      if (await visible(locator, 2000)) {
        await locator.first().click({ timeout: 10000 });
        await waitForUi(page);
        return textCandidate;
      }
    }
  }
  throw new Error(`Could not find clickable text: ${textCandidates.join(", ")}`);
}

async function assertVisible(page, textCandidates, errorMessage) {
  for (const textCandidate of textCandidates) {
    const regex = new RegExp(escapeRegExp(textCandidate), "i");
    const checks = [
      page.getByRole("heading", { name: regex }),
      page.getByRole("button", { name: regex }),
      page.getByRole("link", { name: regex }),
      page.getByText(regex),
      page.getByPlaceholder(regex),
      page.getByLabel(regex)
    ];
    for (const locator of checks) {
      if (await visible(locator, 4000)) {
        return true;
      }
    }
  }
  throw new Error(errorMessage);
}

async function saveScreenshot(page, artifactsDir, report, label, fullPage = false) {
  const fileName = `${String(report.evidence.length + 1).padStart(2, "0")}-${label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "")}.png`;
  const screenshotPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  report.evidence.push({ label, path: screenshotPath });
  reportLine(`Screenshot captured: ${screenshotPath}`);
  return screenshotPath;
}

async function selectGoogleAccountIfNeeded(currentPage, popupPage, email) {
  const candidatePages = [];
  if (popupPage) {
    candidatePages.push(popupPage);
  }
  candidatePages.push(currentPage);

  for (const page of candidatePages) {
    await waitForUi(page);
    if (!/accounts\.google\.com/i.test(page.url())) {
      continue;
    }

    const accountLocator = page.getByText(new RegExp(`^\\s*${escapeRegExp(email)}\\s*$`, "i"));
    if (await visible(accountLocator, 8000)) {
      await accountLocator.first().click();
      await waitForUi(page);
      reportLine(`Selected Google account ${email}.`);
      return true;
    }
  }
  return false;
}

async function openAndValidateLegalPage({
  appPage,
  context,
  report,
  artifactsDir,
  linkTexts,
  headingRegex,
  reportField,
  screenshotLabel
}) {
  const appPageUrlBeforeClick = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickByVisibleText(appPage, linkTexts);
  const popupPage = await popupPromise;

  let legalPage = appPage;
  let openedInNewTab = false;

  if (popupPage) {
    legalPage = popupPage;
    openedInNewTab = true;
    await popupPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await waitForUi(popupPage);
  } else {
    await waitForUi(appPage);
  }

  try {
    const heading = legalPage.getByRole("heading", { name: headingRegex });
    if (await visible(heading, 6000)) {
      report.steps[reportField].notes.push("Heading found.");
    } else {
      const fallbackHeading = legalPage.getByText(headingRegex);
      if (!(await visible(fallbackHeading, 6000))) {
        throw new Error("Legal heading not visible.");
      }
      report.steps[reportField].notes.push("Heading found via fallback text locator.");
    }

    const bodyText = await legalPage.locator("body").innerText();
    if (!bodyText || bodyText.trim().length < 120) {
      throw new Error("Legal content seems empty or too short.");
    }
    report.steps[reportField].notes.push("Legal content text is visible.");

    await saveScreenshot(legalPage, artifactsDir, report, screenshotLabel, true);
    report.steps[reportField].status = "PASS";

    const finalUrl = legalPage.url();
    if (reportField === "Términos y Condiciones") {
      report.legalUrls.terms = finalUrl;
    }
    if (reportField === "Política de Privacidad") {
      report.legalUrls.privacy = finalUrl;
    }
  } catch (error) {
    report.steps[reportField].status = "FAIL";
    report.steps[reportField].notes.push(error.message);
  } finally {
    if (openedInNewTab) {
      await legalPage.close().catch(() => {});
      await appPage.bringToFront().catch(() => {});
      await waitForUi(appPage);
    } else if (appPage.url() !== appPageUrlBeforeClick) {
      await appPage.goBack({ waitUntil: "domcontentloaded", timeout: 15000 }).catch(() => {});
      await waitForUi(appPage);
    }
  }
}

async function main() {
  const artifactsDir = path.resolve(
    process.cwd(),
    "artifacts",
    `saleads-mi-negocio-${nowStamp()}`
  );
  ensureDir(artifactsDir);

  const saleadsUrl =
    process.env.SALEADS_URL ||
    process.env.SALEADS_LOGIN_URL ||
    process.env.BASE_URL ||
    process.env.APP_URL ||
    "";
  const cdpUrl = process.env.SALEADS_CDP_URL || process.env.PLAYWRIGHT_CDP_URL || "";
  const googleAccountEmail = process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;
  const headless = (process.env.HEADLESS || "true").toLowerCase() !== "false";

  const report = {
    startedAt: new Date().toISOString(),
    finishedAt: null,
    baseUrl: saleadsUrl,
    googleAccount: googleAccountEmail,
    overallStatus: "FAIL",
    legalUrls: {
      terms: null,
      privacy: null
    },
    evidence: [],
    steps: Object.fromEntries(
      REPORT_FIELDS.map((name) => [
        name,
        {
          status: "FAIL",
          notes: []
        }
      ])
    )
  };

  let browser;
  let context;
  let page;

  try {
    if (cdpUrl) {
      reportLine(`Connecting to existing browser via CDP: ${cdpUrl}`);
      browser = await chromium.connectOverCDP(cdpUrl);
      context = browser.contexts()[0] || (await browser.newContext({ viewport: { width: 1440, height: 900 } }));
      page = context.pages()[0] || (await context.newPage());
    } else {
      browser = await chromium.launch({ headless });
      context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      page = await context.newPage();
    }

    if (saleadsUrl) {
      reportLine(`Navigating to SaleADS login: ${saleadsUrl}`);
      await page.goto(saleadsUrl, { waitUntil: "domcontentloaded", timeout: 45000 });
      await waitForUi(page);
    } else if (page.url() && page.url() !== "about:blank") {
      reportLine(`Using existing open page as login page: ${page.url()}`);
      await waitForUi(page);
    } else {
      throw new Error(
        "Provide SALEADS_URL/SALEADS_LOGIN_URL (or connect with SALEADS_CDP_URL to an already-open login tab)."
      );
    }

    reportLine("Step 1: Login with Google.");
    try {
      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickByVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Acceder con Google",
        "Continuar con Google"
      ]);
      const popup = await popupPromise;
      await selectGoogleAccountIfNeeded(page, popup, googleAccountEmail);

      await waitForUi(page);
      await assertVisible(page, ["Negocio", "Mi Negocio"], "Sidebar navigation is not visible.");
      await saveScreenshot(page, artifactsDir, report, "dashboard-loaded", true);
      report.steps["Login"].status = "PASS";
      report.steps["Login"].notes.push("Main interface loaded and left navigation is visible.");
    } catch (error) {
      report.steps["Login"].status = "FAIL";
      report.steps["Login"].notes.push(error.message);
      throw error;
    }

    reportLine("Step 2: Open Mi Negocio menu.");
    try {
      await clickByVisibleText(page, ["Negocio"]);
      await clickByVisibleText(page, ["Mi Negocio"]);
      await assertVisible(page, ["Agregar Negocio"], "Agregar Negocio is not visible.");
      await assertVisible(page, ["Administrar Negocios"], "Administrar Negocios is not visible.");
      await saveScreenshot(page, artifactsDir, report, "mi-negocio-menu-expanded", true);
      report.steps["Mi Negocio menu"].status = "PASS";
      report.steps["Mi Negocio menu"].notes.push("Submenu expanded with expected entries.");
    } catch (error) {
      report.steps["Mi Negocio menu"].status = "FAIL";
      report.steps["Mi Negocio menu"].notes.push(error.message);
      throw error;
    }

    reportLine("Step 3: Validate Agregar Negocio modal.");
    try {
      await clickByVisibleText(page, ["Agregar Negocio"]);
      await assertVisible(page, ["Crear Nuevo Negocio"], "Modal title is not visible.");
      await assertVisible(page, ["Nombre del Negocio"], "Nombre del Negocio input is not visible.");
      await assertVisible(page, ["Tienes 2 de 3 negocios"], "Business quota text is not visible.");
      await assertVisible(page, ["Cancelar"], "Cancelar button is not visible.");
      await assertVisible(page, ["Crear Negocio"], "Crear Negocio button is not visible.");

      const businessNameInput = page.getByLabel(/Nombre del Negocio/i);
      if (await visible(businessNameInput, 3000)) {
        await businessNameInput.fill("Negocio Prueba Automatización");
      } else {
        const fallbackInput = page.getByPlaceholder(/Nombre del Negocio/i);
        if (await visible(fallbackInput, 3000)) {
          await fallbackInput.fill("Negocio Prueba Automatización");
        }
      }
      await saveScreenshot(page, artifactsDir, report, "agregar-negocio-modal", true);
      await clickByVisibleText(page, ["Cancelar"]);

      report.steps["Agregar Negocio modal"].status = "PASS";
      report.steps["Agregar Negocio modal"].notes.push("Modal fields/buttons validated and modal closed.");
    } catch (error) {
      report.steps["Agregar Negocio modal"].status = "FAIL";
      report.steps["Agregar Negocio modal"].notes.push(error.message);
      throw error;
    }

    reportLine("Step 4: Open Administrar Negocios.");
    try {
      await clickByVisibleText(page, ["Mi Negocio"]).catch(() => {});
      await clickByVisibleText(page, ["Administrar Negocios"]);
      await assertVisible(page, ["Información General"], "Información General section is missing.");
      await assertVisible(page, ["Detalles de la Cuenta"], "Detalles de la Cuenta section is missing.");
      await assertVisible(page, ["Tus Negocios"], "Tus Negocios section is missing.");
      await assertVisible(page, ["Sección Legal"], "Sección Legal section is missing.");
      await saveScreenshot(page, artifactsDir, report, "administrar-negocios-view", true);
      report.steps["Administrar Negocios view"].status = "PASS";
      report.steps["Administrar Negocios view"].notes.push("Account management page loaded with expected sections.");
    } catch (error) {
      report.steps["Administrar Negocios view"].status = "FAIL";
      report.steps["Administrar Negocios view"].notes.push(error.message);
      throw error;
    }

    reportLine("Step 5: Validate Información General.");
    try {
      await assertVisible(page, ["BUSINESS PLAN"], "BUSINESS PLAN text is missing.");
      await assertVisible(page, ["Cambiar Plan"], "Cambiar Plan button is missing.");
      const allText = await page.locator("body").innerText();
      const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(allText);
      if (!hasEmail) {
        throw new Error("User email is not visible.");
      }
      if (!/nombre|usuario|perfil|account|cuenta/i.test(allText)) {
        throw new Error("User name or account identity text is not visible.");
      }
      report.steps["Información General"].status = "PASS";
      report.steps["Información General"].notes.push("Name/account identity, email, plan and action button validated.");
    } catch (error) {
      report.steps["Información General"].status = "FAIL";
      report.steps["Información General"].notes.push(error.message);
    }

    reportLine("Step 6: Validate Detalles de la Cuenta.");
    try {
      await assertVisible(page, ["Cuenta creada"], "Cuenta creada is missing.");
      await assertVisible(page, ["Estado activo"], "Estado activo is missing.");
      await assertVisible(page, ["Idioma seleccionado"], "Idioma seleccionado is missing.");
      report.steps["Detalles de la Cuenta"].status = "PASS";
      report.steps["Detalles de la Cuenta"].notes.push("Required account details labels are visible.");
    } catch (error) {
      report.steps["Detalles de la Cuenta"].status = "FAIL";
      report.steps["Detalles de la Cuenta"].notes.push(error.message);
    }

    reportLine("Step 7: Validate Tus Negocios.");
    try {
      await assertVisible(page, ["Tus Negocios"], "Tus Negocios section heading is missing.");
      await assertVisible(page, ["Agregar Negocio"], "Agregar Negocio button is missing in business section.");
      await assertVisible(page, ["Tienes 2 de 3 negocios"], "Business quota text is missing in business section.");
      report.steps["Tus Negocios"].status = "PASS";
      report.steps["Tus Negocios"].notes.push("Business list area and controls are visible.");
    } catch (error) {
      report.steps["Tus Negocios"].status = "FAIL";
      report.steps["Tus Negocios"].notes.push(error.message);
    }

    reportLine("Step 8: Validate Términos y Condiciones.");
    await openAndValidateLegalPage({
      appPage: page,
      context,
      report,
      artifactsDir,
      linkTexts: ["Términos y Condiciones", "Terminos y Condiciones"],
      headingRegex: /T[ée]rminos y Condiciones/i,
      reportField: "Términos y Condiciones",
      screenshotLabel: "terminos-y-condiciones"
    });

    reportLine("Step 9: Validate Política de Privacidad.");
    await openAndValidateLegalPage({
      appPage: page,
      context,
      report,
      artifactsDir,
      linkTexts: ["Política de Privacidad", "Politica de Privacidad"],
      headingRegex: /Pol[íi]tica de Privacidad/i,
      reportField: "Política de Privacidad",
      screenshotLabel: "politica-de-privacidad"
    });

    const hasAnyFailure = REPORT_FIELDS.some((field) => report.steps[field].status !== "PASS");
    report.overallStatus = hasAnyFailure ? "FAIL" : "PASS";
  } catch (error) {
    reportLine(`Workflow stopped early: ${error.message}`);
    const hasAnyFailure = REPORT_FIELDS.some((field) => report.steps[field].status !== "PASS");
    report.overallStatus = hasAnyFailure ? "FAIL" : "PASS";
  } finally {
    report.finishedAt = new Date().toISOString();
    if (page) {
      await saveScreenshot(page, artifactsDir, report, "final-application-state", true).catch(() => {});
    }
    if (browser) {
      await browser.close().catch(() => {});
    }

    const jsonPath = path.join(artifactsDir, "report.json");
    const mdPath = path.join(artifactsDir, "report.md");
    fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2), "utf-8");
    fs.writeFileSync(mdPath, toMarkdownReport(report), "utf-8");

    reportLine(`Report JSON: ${jsonPath}`);
    reportLine(`Report Markdown: ${mdPath}`);
    reportLine(`Overall result: ${report.overallStatus}`);
    for (const field of REPORT_FIELDS) {
      reportLine(`${field}: ${report.steps[field].status}`);
    }

    if (report.overallStatus !== "PASS") {
      process.exitCode = 1;
    }
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exit(1);
});
