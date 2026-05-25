const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const STEP_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad",
];

const runId = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.join(__dirname, "artifacts", `${TEST_NAME}_${runId}`);
const screenshotsDir = path.join(artifactsDir, "screenshots");
const reportPath = path.join(artifactsDir, "final-report.json");

const report = Object.fromEntries(
  STEP_FIELDS.map((field) => [
    field,
    {
      status: "FAIL",
      details: "Not executed.",
      evidence: [],
    },
  ])
);

const headless = (process.env.HEADLESS || "false").toLowerCase() === "true";
const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
const googleAccountEmail = process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toRegex(value) {
  if (value instanceof RegExp) {
    return value;
  }
  return new RegExp(escapeRegExp(value), "i");
}

function ensureArtifactFolders() {
  fs.mkdirSync(screenshotsDir, { recursive: true });
}

function markResult(field, status, details, evidence = []) {
  report[field] = {
    status,
    details,
    evidence,
  };
}

async function safeVisible(locator, timeoutMs = 3_000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout: timeoutMs });
    return true;
  } catch {
    return false;
  }
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(1_000);
}

async function captureScreenshot(page, fileName, fullPage = false) {
  const filePath = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function clickByVisibleText(page, textPatterns, options = {}) {
  const { required = true } = options;
  const candidates = textPatterns.map(toRegex);

  for (const pattern of candidates) {
    const locators = [
      page.getByRole("button", { name: pattern }),
      page.getByRole("link", { name: pattern }),
      page.getByRole("menuitem", { name: pattern }),
      page.getByRole("tab", { name: pattern }),
      page.getByText(pattern),
    ];

    for (const locator of locators) {
      if (await safeVisible(locator, 2_500)) {
        await locator.first().scrollIntoViewIfNeeded().catch(() => {});
        await locator.first().click({ timeout: 10_000 });
        await waitForUiLoad(page);
        return pattern.toString();
      }
    }
  }

  if (required) {
    throw new Error(`Could not find clickable element for: ${textPatterns.join(", ")}`);
  }

  return null;
}

async function expectAnyVisibleText(page, textPatterns, timeoutMs = 20_000) {
  const started = Date.now();
  const candidates = textPatterns.map(toRegex);

  while (Date.now() - started < timeoutMs) {
    for (const pattern of candidates) {
      if (await safeVisible(page.getByText(pattern), 1_000)) {
        return pattern.toString();
      }
    }
    await page.waitForTimeout(400);
  }

  throw new Error(`None of these texts became visible: ${textPatterns.join(", ")}`);
}

async function expectAnyVisibleLocator(locators, timeoutMs = 20_000) {
  const started = Date.now();

  while (Date.now() - started < timeoutMs) {
    for (const locator of locators) {
      if (await safeVisible(locator, 1_000)) {
        return true;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 400));
  }

  throw new Error("Expected locator was not visible in time.");
}

async function typeBusinessNameIfPossible(page, value) {
  const fieldCandidates = [
    page.getByLabel(/Nombre del Negocio/i),
    page.getByPlaceholder(/Nombre del Negocio/i),
    page.locator("input[name*='negocio' i]"),
    page.locator("input[id*='negocio' i]"),
  ];

  for (const candidate of fieldCandidates) {
    if (await safeVisible(candidate, 2_500)) {
      await candidate.first().click();
      await candidate.first().fill(value);
      return true;
    }
  }

  return false;
}

async function selectGoogleAccountIfShown(page) {
  const accountMatcher = new RegExp(escapeRegExp(googleAccountEmail), "i");
  const accountLocator = page.getByText(accountMatcher);

  if (await safeVisible(accountLocator, 7_000)) {
    await accountLocator.first().click({ timeout: 10_000 });
    await waitForUiLoad(page);
    return true;
  }

  return false;
}

async function validateLegalDocument({
  appPage,
  context,
  clickPatterns,
  headingPattern,
  reportLabel,
  screenshotFileName,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  const appPageUrlBeforeClick = appPage.url();

  await clickByVisibleText(appPage, clickPatterns, { required: true });

  let legalPage = await popupPromise;
  if (legalPage) {
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
    await legalPage.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  } else {
    legalPage = appPage;
  }

  await expectAnyVisibleText(legalPage, [headingPattern], 25_000);
  const bodyText = await legalPage.locator("body").innerText();
  if (!bodyText || bodyText.trim().length < 100) {
    throw new Error(`Legal content for '${reportLabel}' appears to be empty.`);
  }

  const screenshotPath = await captureScreenshot(legalPage, screenshotFileName, true);
  const finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close({ runBeforeUnload: true }).catch(() => {});
    await appPage.bringToFront().catch(() => {});
  } else {
    const navigatedBack = await appPage
      .goBack({ waitUntil: "domcontentloaded", timeout: 15_000 })
      .catch(() => null);
    if (!navigatedBack) {
      await appPage.goto(appPageUrlBeforeClick, { waitUntil: "domcontentloaded", timeout: 20_000 });
    }
  }

  await waitForUiLoad(appPage);
  return { finalUrl, screenshotPath };
}

async function run() {
  ensureArtifactFolders();

  if (!loginUrl) {
    throw new Error(
      "SALEADS_LOGIN_URL (or SALEADS_BASE_URL) is required so the script can open the login page in any environment."
    );
  }

  const browser = await chromium.launch({ headless });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  const createdEvidence = [];

  try {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 45_000 });
    await waitForUiLoad(page);

    // 1) Login with Google
    {
      const popupPromise = context.waitForEvent("page", { timeout: 15_000 }).catch(() => null);
      await clickByVisibleText(page, [
        /Sign in with Google/i,
        /Continuar con Google/i,
        /Ingresar con Google/i,
        /Google/i,
        /Login/i,
      ]);

      const googlePage = await popupPromise;
      if (googlePage) {
        await googlePage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
        await selectGoogleAccountIfShown(googlePage);
        await googlePage.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
      } else {
        await selectGoogleAccountIfShown(page);
      }

      await page.bringToFront().catch(() => {});
      await waitForUiLoad(page);

      await expectAnyVisibleLocator(
        [page.locator("aside"), page.locator("nav"), page.getByText(/Mi Negocio|Negocio/i)],
        30_000
      );
      await expectAnyVisibleText(page, [/Mi Negocio|Negocio/i], 25_000);
      const dashboardShot = await captureScreenshot(page, "01-dashboard-loaded.png", true);
      createdEvidence.push(dashboardShot);
      markResult(
        "Login",
        "PASS",
        "Main app UI and left sidebar became visible after Google login.",
        [dashboardShot]
      );
    }

    // 2) Open Mi Negocio menu
    {
      await clickByVisibleText(page, [/Negocio/i], { required: false });
      await clickByVisibleText(page, [/Mi Negocio/i]);
      await expectAnyVisibleText(page, [/Agregar Negocio/i], 20_000);
      await expectAnyVisibleText(page, [/Administrar Negocios/i], 20_000);

      const menuShot = await captureScreenshot(page, "02-mi-negocio-expanded-menu.png", true);
      createdEvidence.push(menuShot);
      markResult(
        "Mi Negocio menu",
        "PASS",
        "Mi Negocio expanded and both submenu options are visible.",
        [menuShot]
      );
    }

    // 3) Validate Agregar Negocio modal
    {
      await clickByVisibleText(page, [/Agregar Negocio/i]);
      await expectAnyVisibleText(page, [/Crear Nuevo Negocio/i], 20_000);
      await expectAnyVisibleText(page, [/Nombre del Negocio/i], 20_000);
      await expectAnyVisibleText(page, [/Tienes\\s*2\\s*de\\s*3\\s*negocios/i], 20_000);
      await expectAnyVisibleText(page, [/Cancelar/i], 20_000);
      await expectAnyVisibleText(page, [/Crear Negocio/i], 20_000);

      await typeBusinessNameIfPossible(page, "Negocio Prueba Automatizacion");
      const modalShot = await captureScreenshot(page, "03-agregar-negocio-modal.png", true);
      createdEvidence.push(modalShot);
      await clickByVisibleText(page, [/Cancelar/i], { required: false });

      markResult(
        "Agregar Negocio modal",
        "PASS",
        "Crear Nuevo Negocio modal fields and actions are visible.",
        [modalShot]
      );
    }

    // 4) Open Administrar Negocios
    {
      if (!(await safeVisible(page.getByText(/Administrar Negocios/i), 1_500))) {
        await clickByVisibleText(page, [/Mi Negocio/i], { required: false });
      }
      await clickByVisibleText(page, [/Administrar Negocios/i]);
      await expectAnyVisibleText(page, [/Informaci[oó]n General/i], 20_000);
      await expectAnyVisibleText(page, [/Detalles de la Cuenta/i], 20_000);
      await expectAnyVisibleText(page, [/Tus Negocios/i], 20_000);
      await expectAnyVisibleText(page, [/Secci[oó]n Legal/i], 20_000);

      const accountPageShot = await captureScreenshot(page, "04-administrar-negocios-page.png", true);
      createdEvidence.push(accountPageShot);
      markResult(
        "Administrar Negocios view",
        "PASS",
        "Account page sections are visible.",
        [accountPageShot]
      );
    }

    // 5) Validate Informacion General
    {
      await expectAnyVisibleText(page, [/Informaci[oó]n General/i], 15_000);
      await expectAnyVisibleLocator(
        [
          page.locator("section,div").filter({ hasText: /@/ }).first(),
          page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/),
        ],
        15_000
      );
      await expectAnyVisibleText(page, [/BUSINESS PLAN/i], 15_000);
      await expectAnyVisibleText(page, [/Cambiar Plan/i], 15_000);
      markResult(
        "Informaci\u00f3n General",
        "PASS",
        "User identity, plan label, and Cambiar Plan button are visible."
      );
    }

    // 6) Validate Detalles de la Cuenta
    {
      await expectAnyVisibleText(page, [/Detalles de la Cuenta/i], 15_000);
      await expectAnyVisibleText(page, [/Cuenta creada/i], 15_000);
      await expectAnyVisibleText(page, [/Estado activo/i], 15_000);
      await expectAnyVisibleText(page, [/Idioma seleccionado/i], 15_000);
      markResult(
        "Detalles de la Cuenta",
        "PASS",
        "Account detail labels are visible."
      );
    }

    // 7) Validate Tus Negocios
    {
      await expectAnyVisibleText(page, [/Tus Negocios/i], 15_000);
      await expectAnyVisibleText(page, [/Agregar Negocio/i], 15_000);
      await expectAnyVisibleText(page, [/Tienes\\s*2\\s*de\\s*3\\s*negocios/i], 15_000);
      markResult(
        "Tus Negocios",
        "PASS",
        "Business list area, add button, and business quota text are visible."
      );
    }

    // 8) Validate Terminos y Condiciones
    {
      const { finalUrl, screenshotPath } = await validateLegalDocument({
        appPage: page,
        context,
        clickPatterns: [/T[e\u00e9]rminos y Condiciones/i, /Terminos y Condiciones/i],
        headingPattern: /T[eé]rminos y Condiciones/i,
        reportLabel: "T\u00e9rminos y Condiciones",
        screenshotFileName: "05-terminos-y-condiciones.png",
      });
      createdEvidence.push(screenshotPath);
      markResult(
        "T\u00e9rminos y Condiciones",
        "PASS",
        `Heading and legal content validated. Final URL: ${finalUrl}`,
        [screenshotPath]
      );
    }

    // 9) Validate Politica de Privacidad
    {
      const { finalUrl, screenshotPath } = await validateLegalDocument({
        appPage: page,
        context,
        clickPatterns: [/Pol[i\u00ed]tica de Privacidad/i, /Politica de Privacidad/i],
        headingPattern: /Pol[ií]tica de Privacidad/i,
        reportLabel: "Pol\u00edtica de Privacidad",
        screenshotFileName: "06-politica-de-privacidad.png",
      });
      createdEvidence.push(screenshotPath);
      markResult(
        "Pol\u00edtica de Privacidad",
        "PASS",
        `Heading and legal content validated. Final URL: ${finalUrl}`,
        [screenshotPath]
      );
    }
  } catch (error) {
    const firstPendingField = STEP_FIELDS.find((field) => report[field].details === "Not executed.");
    if (firstPendingField) {
      markResult(firstPendingField, "FAIL", `Execution stopped on this step: ${error.message}`);
    }

    for (const field of STEP_FIELDS) {
      if (report[field].details === "Not executed.") {
        markResult(field, "FAIL", "Not executed due to a failure in an earlier critical step.");
      }
    }
  } finally {
    const reportPayload = {
      name: TEST_NAME,
      generatedAtUtc: new Date().toISOString(),
      artifactsDirectory: artifactsDir,
      screenshotsDirectory: screenshotsDir,
      results: report,
    };

    fs.writeFileSync(reportPath, JSON.stringify(reportPayload, null, 2));

    console.log("=== Final Report ===");
    for (const field of STEP_FIELDS) {
      const stepResult = report[field];
      console.log(`${field}: ${stepResult.status} - ${stepResult.details}`);
    }
    console.log(`Report JSON: ${reportPath}`);
    console.log(`Screenshots captured: ${createdEvidence.length}`);

    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }

  const hasFailure = STEP_FIELDS.some((field) => report[field].status !== "PASS");
  if (hasFailure) {
    process.exitCode = 1;
  }
}

run().catch((error) => {
  console.error(`Fatal error while executing ${TEST_NAME}:`, error);
  process.exitCode = 1;
});
