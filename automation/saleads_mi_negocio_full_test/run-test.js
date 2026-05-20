#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
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

const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
const outputRoot =
  process.env.SALEADS_TEST_OUTPUT_DIR ||
  path.join(process.cwd(), "artifacts", TEST_NAME, timestamp);
const screenshotsDir = path.join(outputRoot, "screenshots");
const reportPath = path.join(outputRoot, "report.json");

function ensureDir(targetPath) {
  fs.mkdirSync(targetPath, { recursive: true });
}

function normalizeLabel(value) {
  return value
    .toLowerCase()
    .replace(/\s+/g, "_")
    .replace(/[^\w-]/g, "");
}

async function waitForUi(page, timeoutMs = 1200) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(timeoutMs);
}

async function findFirstVisible(page, candidates, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of candidates) {
      const first = locator.first();
      const count = await first.count();
      if (count > 0 && (await first.isVisible().catch(() => false))) {
        return first;
      }
    }

    await page.waitForTimeout(350);
  }

  return null;
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function ensureTextVisible(page, textPattern, timeoutMs = 20000) {
  const locator = page.getByText(textPattern).first();
  await locator.waitFor({ state: "visible", timeout: timeoutMs });
  return locator;
}

async function screenshot(page, label, report) {
  const fileName = `${Date.now()}_${normalizeLabel(label)}.png`;
  const filePath = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: filePath, fullPage: true });
  report.evidence.screenshots.push(filePath);
  return filePath;
}

function initializeReport() {
  const stepResults = {};
  for (const field of REPORT_FIELDS) {
    stepResults[field] = {
      status: "FAIL",
      details: "",
      evidence: []
    };
  }

  return {
    name: TEST_NAME,
    goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
    startedAt: new Date().toISOString(),
    finishedAt: null,
    overallStatus: "FAIL",
    legalUrls: {},
    stepResults,
    evidence: {
      screenshots: []
    },
    notes: []
  };
}

function markStep(report, field, status, details, evidence = []) {
  report.stepResults[field] = {
    status: status ? "PASS" : "FAIL",
    details,
    evidence
  };
}

async function run() {
  ensureDir(outputRoot);
  ensureDir(screenshotsDir);

  const report = initializeReport();
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const headless = process.env.HEADLESS !== "false";

  let browser;
  let context;
  let page;

  try {
    browser = await chromium.launch({ headless });
    context = await browser.newContext({
      viewport: { width: 1440, height: 960 }
    });
    page = await context.newPage();

    if (!loginUrl) {
      throw new Error(
        "SALEADS_LOGIN_URL is required. Set it to the environment login page URL (dev/staging/prod)."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await waitForUi(page, 2000);

    // Step 1: Login with Google
    const loginButton = await findFirstVisible(page, [
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesión con google/i }),
      page.getByRole("button", { name: /google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesión con google/i),
      page.getByText(/google/i)
    ]);

    if (!loginButton) {
      throw new Error("Could not find a Google login button on the login page.");
    }

    const loginPopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const loginPopup = await loginPopupPromise;
    const loginSurface = loginPopup || page;

    if (loginPopup) {
      await loginPopup.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
      await waitForUi(loginPopup, 1200);
    }

    const chooseAccount = await findFirstVisible(loginSurface, [
      loginSurface.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
      loginSurface.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i })
    ], 8000);
    if (chooseAccount) {
      await clickAndWait(loginSurface, chooseAccount);
    }

    if (loginPopup) {
      await loginPopup.close().catch(() => {});
      await page.bringToFront();
    }

    await waitForUi(page, 2500);

    const sidebarVisible = await findFirstVisible(page, [
      page.getByText(/negocio/i),
      page.getByRole("navigation"),
      page.getByText(/mi negocio/i)
    ], 20000);
    if (!sidebarVisible) {
      throw new Error("Main app interface/sidebar was not visible after login.");
    }

    const dashboardShot = await screenshot(page, "dashboard_loaded", report);
    markStep(report, "Login", true, "Google login flow reached main application with sidebar visible.", [
      dashboardShot
    ]);

    // Step 2: Open Mi Negocio menu
    const negocioSection = await findFirstVisible(page, [
      page.getByText(/^negocio$/i),
      page.getByRole("button", { name: /negocio/i }),
      page.getByText(/negocio/i)
    ]);
    if (!negocioSection) {
      throw new Error("Could not find sidebar section 'Negocio'.");
    }
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await findFirstVisible(page, [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    if (!miNegocioOption) {
      throw new Error("Could not find option 'Mi Negocio'.");
    }
    await clickAndWait(page, miNegocioOption);

    await ensureTextVisible(page, /agregar negocio/i);
    await ensureTextVisible(page, /administrar negocios/i);
    const menuShot = await screenshot(page, "mi_negocio_menu_expanded", report);
    markStep(
      report,
      "Mi Negocio menu",
      true,
      "Mi Negocio submenu expanded and both required options are visible.",
      [menuShot]
    );

    // Step 3: Validate Agregar Negocio modal
    const agregarNegocioMenu = await findFirstVisible(page, [
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i)
    ]);
    if (!agregarNegocioMenu) {
      throw new Error("Could not find 'Agregar Negocio' option.");
    }

    await clickAndWait(page, agregarNegocioMenu);

    await ensureTextVisible(page, /crear nuevo negocio/i);
    const businessNameInput = await findFirstVisible(page, [
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[type='text'], input:not([type]), textarea")
    ], 10000);
    if (!businessNameInput) {
      throw new Error("Input 'Nombre del Negocio' was not found in modal.");
    }

    await ensureTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i);
    await ensureTextVisible(page, /cancelar/i);
    await ensureTextVisible(page, /crear negocio/i);

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    const modalShot = await screenshot(page, "agregar_negocio_modal", report);

    const cancelButton = await findFirstVisible(page, [
      page.getByRole("button", { name: /^cancelar$/i }),
      page.getByText(/^cancelar$/i)
    ]);
    if (cancelButton) {
      await clickAndWait(page, cancelButton);
    }

    markStep(
      report,
      "Agregar Negocio modal",
      true,
      "Modal validated with title, input, business limit text and action buttons.",
      [modalShot]
    );

    // Step 4: Open Administrar Negocios
    const miNegocioAgain = await findFirstVisible(page, [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    if (miNegocioAgain) {
      await clickAndWait(page, miNegocioAgain);
    }

    const adminOption = await findFirstVisible(page, [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    if (!adminOption) {
      throw new Error("Could not find 'Administrar Negocios' option.");
    }

    await clickAndWait(page, adminOption);
    await ensureTextVisible(page, /informaci[oó]n general/i, 30000);
    await ensureTextVisible(page, /detalles de la cuenta/i);
    await ensureTextVisible(page, /tus negocios/i);
    await ensureTextVisible(page, /secci[oó]n legal/i);

    const adminPageShot = await screenshot(page, "administrar_negocios_page", report);
    markStep(
      report,
      "Administrar Negocios view",
      true,
      "All expected account sections are present in Administrar Negocios.",
      [adminPageShot]
    );

    // Step 5: Validate Información General
    await ensureTextVisible(page, /business plan/i);
    await ensureTextVisible(page, /cambiar plan/i);
    const hasEmail = await findFirstVisible(page, [page.getByText(/@/i)], 8000);
    const hasLikelyUserName = await findFirstVisible(page, [
      page.getByText(/[A-Za-z]{2,}\s+[A-Za-z]{2,}/),
      page.getByRole("heading")
    ], 8000);
    if (!hasEmail || !hasLikelyUserName) {
      throw new Error("Could not verify user name/email in Información General.");
    }

    markStep(
      report,
      "Información General",
      true,
      "User identity, plan text and plan change action are visible."
    );

    // Step 6: Validate Detalles de la Cuenta
    await ensureTextVisible(page, /cuenta creada/i);
    await ensureTextVisible(page, /estado activo/i);
    await ensureTextVisible(page, /idioma seleccionado/i);
    markStep(
      report,
      "Detalles de la Cuenta",
      true,
      "All required details are visible in account details section."
    );

    // Step 7: Validate Tus Negocios
    await ensureTextVisible(page, /tus negocios/i);
    await ensureTextVisible(page, /agregar negocio/i);
    await ensureTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i);
    markStep(
      report,
      "Tus Negocios",
      true,
      "Business list area and business limit indicators are visible."
    );

    // Step 8 and 9: Legal links
    async function validateLegalLink(linkRegex, headingRegex, reportField, screenshotLabel) {
      const appUrlBefore = page.url();
      const linkLocator = await findFirstVisible(page, [
        page.getByRole("link", { name: linkRegex }),
        page.getByText(linkRegex)
      ], 15000);

      if (!linkLocator) {
        throw new Error(`Could not find legal link for ${reportField}.`);
      }

      const popupPromise = context.waitForEvent("page", { timeout: 9000 }).catch(() => null);
      await clickAndWait(page, linkLocator);
      let target = await popupPromise;

      if (target) {
        await target.waitForLoadState("domcontentloaded", { timeout: 30000 });
        await waitForUi(target);
      } else {
        target = page;
      }

      await ensureTextVisible(target, headingRegex, 30000);
      const bodyText = await target.locator("body").innerText();
      if (!bodyText || bodyText.trim().length < 120) {
        throw new Error(`${reportField} page appears to have no meaningful legal content.`);
      }

      const legalShot = await screenshot(target, screenshotLabel, report);
      report.legalUrls[reportField] = target.url();

      if (target !== page) {
        await target.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
          await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
        });
        await waitForUi(page);
      }

      markStep(
        report,
        reportField,
        true,
        `${reportField} page validated and URL captured.`,
        [legalShot, target.url()]
      );
    }

    await validateLegalLink(
      /términos y condiciones|terminos y condiciones/i,
      /términos y condiciones|terminos y condiciones/i,
      "Términos y Condiciones",
      "terminos_y_condiciones"
    );

    await validateLegalLink(
      /política de privacidad|politica de privacidad/i,
      /política de privacidad|politica de privacidad/i,
      "Política de Privacidad",
      "politica_de_privacidad"
    );

    report.overallStatus = Object.values(report.stepResults).every((step) => step.status === "PASS")
      ? "PASS"
      : "FAIL";
  } catch (error) {
    report.notes.push(`Execution error: ${error.message}`);
  } finally {
    report.finishedAt = new Date().toISOString();
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");

    if (page) {
      await page.close().catch(() => {});
    }
    if (context) {
      await context.close().catch(() => {});
    }
    if (browser) {
      await browser.close().catch(() => {});
    }
  }

  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  process.stdout.write(`Report written to: ${reportPath}\n`);
  process.exit(report.overallStatus === "PASS" ? 0 : 1);
}

run();
