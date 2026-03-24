import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_TIMEOUT_MS = Number(process.env.PW_DEFAULT_TIMEOUT_MS || 20000);
const ARTIFACTS_ROOT = path.resolve(process.env.ARTIFACTS_DIR || "artifacts");
const RUN_STAMP = new Date().toISOString().replace(/[:.]/g, "-");
const RUN_DIR = path.join(ARTIFACTS_ROOT, `${TEST_NAME}-${RUN_STAMP}`);
const SCREENSHOTS_DIR = path.join(RUN_DIR, "screenshots");
const REPORT_PATH = path.join(RUN_DIR, "report.json");
const RESULT_PATH = path.join(RUN_DIR, "result.json");
const LOGIN_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const START_URL = process.env.SALEADS_START_URL || process.env.START_URL || "";

const report = {
  testName: TEST_NAME,
  startedAt: new Date().toISOString(),
  environment: {
    startUrlProvided: Boolean(START_URL),
    startUrl: START_URL || null,
    googleAccountEmail: LOGIN_EMAIL,
    headless: process.env.HEADLESS !== "false",
  },
  evidence: {
    runDirectory: RUN_DIR,
    screenshots: [],
    urls: {},
  },
  steps: [],
};

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function accentInsensitiveRegex(text) {
  const chars = {
    a: "[aáàäâã]",
    e: "[eéèëê]",
    i: "[iíìïî]",
    o: "[oóòöôõ]",
    u: "[uúùüû]",
    n: "[nñ]",
    c: "[cç]",
  };

  const pattern = [...text.toLowerCase()]
    .map((char) => {
      if (chars[char]) return chars[char];
      if (/\s/.test(char)) return "\\s+";
      return escapeRegex(char);
    })
    .join("");

  return new RegExp(pattern, "i");
}

async function ensureDirectories() {
  await fs.mkdir(SCREENSHOTS_DIR, { recursive: true });
}

async function writeJson(filePath, data) {
  await fs.writeFile(filePath, `${JSON.stringify(data, null, 2)}\n`, "utf8");
}

function getStepRecord(stepLabel) {
  const existing = report.steps.find((item) => item.step === stepLabel);
  if (existing) return existing;

  const created = {
    step: stepLabel,
    status: "PENDING",
    checks: [],
  };
  report.steps.push(created);
  return created;
}

function registerCheck(stepLabel, name, passed, details = "") {
  const stepRecord = getStepRecord(stepLabel);
  stepRecord.checks.push({
    name,
    passed,
    details: details || undefined,
  });

  const allPassed = stepRecord.checks.every((check) => check.passed);
  const hasAnyFailed = stepRecord.checks.some((check) => !check.passed);
  stepRecord.status = hasAnyFailed ? "FAIL" : allPassed ? "PASS" : "PENDING";
}

async function waitForStableUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function captureScreenshot(page, name, fullPage = false) {
  const fileName = `${String(report.evidence.screenshots.length + 1).padStart(2, "0")}-${name}.png`;
  const filePath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  report.evidence.screenshots.push(filePath);
}

async function visibleTextExists(page, text) {
  const locator = page.getByText(accentInsensitiveRegex(text), { exact: false }).first();
  await locator.waitFor({ state: "visible", timeout: DEFAULT_TIMEOUT_MS });
  return locator;
}

async function clickByText(page, text, options = {}) {
  const locator = page.getByRole("button", { name: accentInsensitiveRegex(text) }).first();
  const count = await locator.count();

  if (count > 0) {
    await locator.waitFor({ state: "visible", timeout: DEFAULT_TIMEOUT_MS });
    await locator.click();
    await waitForStableUi(page);
    return;
  }

  const fallback = page.getByText(accentInsensitiveRegex(text), { exact: false }).first();
  await fallback.waitFor({ state: "visible", timeout: DEFAULT_TIMEOUT_MS });
  await fallback.click(options);
  await waitForStableUi(page);
}

async function ensureSidebarVisible(page) {
  const nav = page.locator("aside, nav").first();
  const navVisible = await nav.isVisible().catch(() => false);
  if (navVisible) return;

  // Fallback for sidebars without semantic tags.
  const menuTexts = ["Negocio", "Mi Negocio", "Dashboard"];
  for (const menuText of menuTexts) {
    const element = page.getByText(accentInsensitiveRegex(menuText), { exact: false }).first();
    if (await element.isVisible().catch(() => false)) {
      return;
    }
  }

  throw new Error("Left sidebar navigation could not be confirmed as visible.");
}

async function maybeSelectGoogleAccount(page, email) {
  const emailRegex = accentInsensitiveRegex(email);
  const emailCandidate = page.getByText(emailRegex, { exact: false }).first();
  if (await emailCandidate.isVisible({ timeout: 4000 }).catch(() => false)) {
    await emailCandidate.click();
    await waitForStableUi(page);
    return true;
  }

  const useAnotherAccount = page.getByText(accentInsensitiveRegex("Use another account"), { exact: false }).first();
  if (await useAnotherAccount.isVisible({ timeout: 2000 }).catch(() => false)) {
    throw new Error(
      `Google account chooser appeared but '${email}' was not listed. Manual intervention may be required.`
    );
  }

  return false;
}

async function maybeExpandMiNegocio(page) {
  const subOption = page.getByText(accentInsensitiveRegex("Agregar Negocio"), { exact: false }).first();
  if (await subOption.isVisible({ timeout: 1500 }).catch(() => false)) return;

  await clickByText(page, "Mi Negocio");
}

async function clickGoogleLoginAndResolveAuthPage(page) {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);

  await clickByText(page, "Sign in with Google").catch(async () => {
    await clickByText(page, "Login with Google");
  });

  const popupPage = await popupPromise;
  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded");
    await popupPage.waitForTimeout(700);
    return popupPage;
  }

  return page;
}

async function clickLegalAndResolveTarget(page, text) {
  const appPage = page;
  const context = page.context();
  const previousUrl = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
  await clickByText(appPage, text);
  const popupPage = await popupPromise;

  const currentUrl = appPage.url();
  const currentUrlChanged = currentUrl && previousUrl && currentUrl !== previousUrl;
  if (currentUrlChanged) {
    return { targetPage: appPage, openedNewTab: false };
  }

  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded");
    await popupPage.waitForTimeout(700);
    return { targetPage: popupPage, openedNewTab: true };
  }

  const pagesAfter = context.pages();
  // Last fallback: check if any other page became active with a URL.
  const otherPage = pagesAfter.find((candidate) => candidate !== appPage && candidate.url());
  if (otherPage) {
    await otherPage.waitForLoadState("domcontentloaded");
    await otherPage.waitForTimeout(700);
    return { targetPage: otherPage, openedNewTab: true };
  }

  throw new Error(`Could not determine navigation target after clicking '${text}'.`);
}

async function returnToAccountPage(page, accountPageUrl) {
  if (!accountPageUrl) return;
  if (page.url() === accountPageUrl) return;

  await page.goto(accountPageUrl, { waitUntil: "domcontentloaded" });
  await waitForStableUi(page);
}

async function validateLegalPage(targetPage, titleText, stepLabel, urlEvidenceKey, screenshotName) {
  await visibleTextExists(targetPage, titleText);
  registerCheck(stepLabel, `Heading '${titleText}' visible`, true);

  const legalBodyCandidate = targetPage
    .locator("main, article, section, body")
    .filter({ hasText: /condiciones|privacidad|datos|responsabilidad|servicio|usuario/i })
    .first();

  const bodyVisible = await legalBodyCandidate.isVisible().catch(() => false);
  if (!bodyVisible) {
    // Fallback heuristic: legal pages should contain enough visible text.
    const bodyText = (await targetPage.locator("body").innerText().catch(() => "")).trim();
    const hasSufficientText = bodyText.length > 150;
    registerCheck(
      stepLabel,
      "Legal content text is visible",
      hasSufficientText,
      hasSufficientText ? "" : "Body text was too short to confidently classify as legal content."
    );
  } else {
    registerCheck(stepLabel, "Legal content text is visible", true);
  }

  report.evidence.urls[urlEvidenceKey] = targetPage.url();
  await captureScreenshot(targetPage, screenshotName, true);
}

async function run() {
  await ensureDirectories();

  const browser = await chromium.launch({
    headless: process.env.HEADLESS !== "false",
  });

  let context;
  try {
    context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
    });
    const page = await context.newPage();
    page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

    if (START_URL) {
      await page.goto(START_URL, { waitUntil: "domcontentloaded" });
      await waitForStableUi(page);
    } else {
      await waitForStableUi(page);
    }

    // Step 1: Login with Google
    const step1 = "Login";
    const originalPage = page;
    const authPage = await clickGoogleLoginAndResolveAuthPage(originalPage);
    registerCheck(step1, "Login button clicked", true);

    const accountExplicitlySelected = await maybeSelectGoogleAccount(authPage, LOGIN_EMAIL);
    registerCheck(
      step1,
      "Google account selection handled",
      true,
      accountExplicitlySelected
        ? `Selected account: ${LOGIN_EMAIL}`
        : `Account chooser not shown or account already active: ${LOGIN_EMAIL}`
    );

    if (authPage !== originalPage) {
      await Promise.race([
        authPage.waitForEvent("close", { timeout: 30000 }).catch(() => null),
        originalPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => null),
      ]);
      await originalPage.bringToFront();
      await waitForStableUi(originalPage);
    }

    await ensureSidebarVisible(originalPage);
    registerCheck(step1, "Main interface visible", true);
    registerCheck(step1, "Left sidebar visible", true);
    await captureScreenshot(originalPage, "dashboard-loaded", true);

    // Step 2: Open Mi Negocio menu
    const step2 = "Mi Negocio menu";
    await visibleTextExists(page, "Negocio");
    await clickByText(originalPage, "Mi Negocio");
    registerCheck(step2, "Mi Negocio clicked", true);
    await visibleTextExists(page, "Agregar Negocio");
    registerCheck(step2, "'Agregar Negocio' visible", true);
    await visibleTextExists(page, "Administrar Negocios");
    registerCheck(step2, "'Administrar Negocios' visible", true);
    await captureScreenshot(page, "mi-negocio-menu-expanded");

    // Step 3: Validate Agregar Negocio modal
    const step3 = "Agregar Negocio modal";
    await clickByText(originalPage, "Agregar Negocio");
    await visibleTextExists(originalPage, "Crear Nuevo Negocio");
    registerCheck(step3, "Modal title visible", true);
    const nombreInput = originalPage.getByLabel(accentInsensitiveRegex("Nombre del Negocio")).first();
    const inputVisible = await nombreInput.isVisible().catch(() => false);
    if (inputVisible) {
      registerCheck(step3, "'Nombre del Negocio' input exists", true);
      await nombreInput.click();
      await nombreInput.fill("Negocio Prueba Automatizacion");
    } else {
      const placeholderInput = originalPage
        .getByPlaceholder(accentInsensitiveRegex("Nombre del Negocio"))
        .first();
      await placeholderInput.waitFor({ state: "visible", timeout: DEFAULT_TIMEOUT_MS });
      registerCheck(step3, "'Nombre del Negocio' input exists", true, "Validated through placeholder fallback.");
      await placeholderInput.click();
      await placeholderInput.fill("Negocio Prueba Automatizacion");
    }
    await visibleTextExists(originalPage, "Tienes 2 de 3 negocios");
    registerCheck(step3, "Business quota text visible", true);
    await visibleTextExists(originalPage, "Cancelar");
    registerCheck(step3, "'Cancelar' button present", true);
    await visibleTextExists(originalPage, "Crear Negocio");
    registerCheck(step3, "'Crear Negocio' button present", true);
    await captureScreenshot(originalPage, "agregar-negocio-modal");
    await clickByText(originalPage, "Cancelar");

    // Step 4: Open Administrar Negocios
    const step4 = "Administrar Negocios view";
    await maybeExpandMiNegocio(originalPage);
    await clickByText(originalPage, "Administrar Negocios");
    report.evidence.urls.accountPage = originalPage.url();
    await visibleTextExists(originalPage, "Informacion General");
    registerCheck(step4, "'Información General' section exists", true);
    await visibleTextExists(originalPage, "Detalles de la Cuenta");
    registerCheck(step4, "'Detalles de la Cuenta' section exists", true);
    await visibleTextExists(originalPage, "Tus Negocios");
    registerCheck(step4, "'Tus Negocios' section exists", true);
    await visibleTextExists(originalPage, "Seccion Legal");
    registerCheck(step4, "'Sección Legal' section exists", true);
    await captureScreenshot(originalPage, "administrar-negocios-account-page", true);

    // Step 5: Validate Informacion General
    const step5 = "Información General";
    const infoGeneralContainer = originalPage
      .locator("section,div", { hasText: accentInsensitiveRegex("Informacion General") })
      .first();
    const infoGeneralText = await infoGeneralContainer.innerText().catch(() => "");
    const nonLabelLines = infoGeneralText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .filter((line) => !/informacion general|business plan|cambiar plan/i.test(line))
      .filter((line) => !/@/.test(line));
    const userNameVisible = nonLabelLines.length > 0;
    registerCheck(step5, "User name is visible", userNameVisible);

    const userEmailVisible = await originalPage
      .getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)
      .first()
      .isVisible()
      .catch(() => false);
    registerCheck(step5, "User email is visible", userEmailVisible);

    await visibleTextExists(originalPage, "BUSINESS PLAN");
    registerCheck(step5, "'BUSINESS PLAN' text is visible", true);
    await visibleTextExists(originalPage, "Cambiar Plan");
    registerCheck(step5, "'Cambiar Plan' button is visible", true);

    // Step 6: Validate Detalles de la Cuenta
    const step6 = "Detalles de la Cuenta";
    await visibleTextExists(originalPage, "Cuenta creada");
    registerCheck(step6, "'Cuenta creada' is visible", true);
    await visibleTextExists(originalPage, "Estado activo");
    registerCheck(step6, "'Estado activo' is visible", true);
    await visibleTextExists(originalPage, "Idioma seleccionado");
    registerCheck(step6, "'Idioma seleccionado' is visible", true);

    // Step 7: Validate Tus Negocios
    const step7 = "Tus Negocios";
    await visibleTextExists(originalPage, "Tus Negocios");
    registerCheck(step7, "Business list section is visible", true);
    const addBizButton = originalPage
      .locator("section,div", { hasText: accentInsensitiveRegex("Tus Negocios") })
      .getByText(accentInsensitiveRegex("Agregar Negocio"), { exact: false })
      .first();
    const addBizVisible = await addBizButton.isVisible().catch(() => false);
    registerCheck(step7, "'Agregar Negocio' button exists", addBizVisible);
    await visibleTextExists(originalPage, "Tienes 2 de 3 negocios");
    registerCheck(step7, "Business quota text is visible", true);

    // Step 8: Validate Términos y Condiciones
    const step8 = "Términos y Condiciones";
    const termsResolution = await clickLegalAndResolveTarget(originalPage, "Términos y Condiciones");
    await validateLegalPage(
      termsResolution.targetPage,
      "Términos y Condiciones",
      step8,
      "terminosYCondiciones",
      "terminos-y-condiciones"
    );
    if (termsResolution.openedNewTab && termsResolution.targetPage !== originalPage) {
      await termsResolution.targetPage.close();
      await originalPage.bringToFront();
      await waitForStableUi(originalPage);
    } else {
      await returnToAccountPage(originalPage, report.evidence.urls.accountPage);
    }

    // Step 9: Validate Política de Privacidad
    const step9 = "Política de Privacidad";
    const privacyResolution = await clickLegalAndResolveTarget(originalPage, "Política de Privacidad");
    await validateLegalPage(
      privacyResolution.targetPage,
      "Política de Privacidad",
      step9,
      "politicaDePrivacidad",
      "politica-de-privacidad"
    );
    if (privacyResolution.openedNewTab && privacyResolution.targetPage !== originalPage) {
      await privacyResolution.targetPage.close();
      await originalPage.bringToFront();
      await waitForStableUi(originalPage);
    } else {
      await returnToAccountPage(originalPage, report.evidence.urls.accountPage);
    }

    // Step 10: Final Report
    const finalSummary = {
      Login: getStepRecord("Login").status,
      "Mi Negocio menu": getStepRecord("Mi Negocio menu").status,
      "Agregar Negocio modal": getStepRecord("Agregar Negocio modal").status,
      "Administrar Negocios view": getStepRecord("Administrar Negocios view").status,
      "Información General": getStepRecord("Información General").status,
      "Detalles de la Cuenta": getStepRecord("Detalles de la Cuenta").status,
      "Tus Negocios": getStepRecord("Tus Negocios").status,
      "Términos y Condiciones": getStepRecord("Términos y Condiciones").status,
      "Política de Privacidad": getStepRecord("Política de Privacidad").status,
    };

    report.finishedAt = new Date().toISOString();
    report.finalSummary = finalSummary;
    report.overallStatus = Object.values(finalSummary).every((status) => status === "PASS") ? "PASS" : "FAIL";
    await writeJson(REPORT_PATH, report);

    const terminalResult = {
      overallStatus: report.overallStatus,
      finalSummary,
      evidence: {
        reportPath: REPORT_PATH,
        screenshotsDirectory: SCREENSHOTS_DIR,
        urls: report.evidence.urls,
      },
    };
    await writeJson(RESULT_PATH, terminalResult);
    process.stdout.write(`${JSON.stringify(terminalResult, null, 2)}\n`);
    process.exitCode = report.overallStatus === "PASS" ? 0 : 1;
  } catch (error) {
    const serializedError = {
      name: error?.name || "Error",
      message: error?.message || String(error),
      stack: error?.stack || "",
    };

    report.finishedAt = new Date().toISOString();
    report.overallStatus = "FAIL";
    report.error = serializedError;
    await writeJson(REPORT_PATH, report);

    const terminalResult = {
      overallStatus: "FAIL",
      error: serializedError,
      evidence: {
        reportPath: REPORT_PATH,
        screenshotsDirectory: SCREENSHOTS_DIR,
        urls: report.evidence.urls,
      },
    };
    await writeJson(RESULT_PATH, terminalResult);
    process.stdout.write(`${JSON.stringify(terminalResult, null, 2)}\n`);
    process.exitCode = 1;
  } finally {
    await context?.close().catch(() => undefined);
    await browser.close().catch(() => undefined);
  }
}

await run();
