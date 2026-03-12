const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright");

const CHECKPOINTS = [
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

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function normalizeText(value) {
  return (value || "").replace(/\s+/g, " ").trim();
}

function boolToStatus(value) {
  return value ? "PASS" : "FAIL";
}

async function isVisible(locator) {
  try {
    return await locator.isVisible({ timeout: 5000 });
  } catch {
    return false;
  }
}

async function pickFirstVisible(locators) {
  for (const locator of locators) {
    if (await isVisible(locator)) {
      return locator;
    }
  }
  return null;
}

async function clickAndWait(page, locator) {
  await locator.click({ timeout: 20000 });
  await waitForUi(page);
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(1200);
}

async function safeScreenshot(page, outputPath, fullPage = false) {
  try {
    await page.screenshot({ path: outputPath, fullPage });
  } catch (error) {
    console.warn(`Could not capture screenshot ${outputPath}: ${error.message}`);
  }
}

async function hasVisibleText(page, pattern) {
  const locator = page.getByText(pattern).first();
  return isVisible(locator);
}

async function validateTermsOrPrivacy(pageToValidate, titleRegex) {
  const headingCandidates = [
    pageToValidate.getByRole("heading", { name: titleRegex }).first(),
    pageToValidate.getByText(titleRegex).first(),
  ];
  const heading = await pickFirstVisible(headingCandidates);
  const hasHeading = !!heading;

  const bodyText = normalizeText(await pageToValidate.locator("body").innerText({ timeout: 10000 }).catch(() => ""));
  const hasLegalText = bodyText.length > 150;

  return {
    hasHeading,
    hasLegalText,
  };
}

async function run() {
  const artifactsRoot = path.join(__dirname, "artifacts");
  const runId = nowStamp();
  const runDir = path.join(artifactsRoot, runId);
  const screenshotsDir = path.join(runDir, "screenshots");
  const latestReportPath = path.join(artifactsRoot, "latest-report.json");
  const latestSummaryPath = path.join(artifactsRoot, "latest-summary.txt");

  ensureDir(screenshotsDir);

  const startUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    "";

  const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
  const headless = process.env.HEADED === "1" ? false : true;

  const report = {
    name: "saleads_mi_negocio_full_test",
    runId,
    runAt: new Date().toISOString(),
    environment: {
      startUrl: startUrl || null,
      hostname: startUrl ? new URL(startUrl).hostname : null,
      headless,
      googleAccount,
    },
    evidence: {
      screenshotsDir,
      dashboardScreenshot: null,
      expandedMenuScreenshot: null,
      agregarModalScreenshot: null,
      administrarNegociosScreenshot: null,
      terminosScreenshot: null,
      terminosUrl: null,
      privacidadScreenshot: null,
      privacidadUrl: null,
      errorStateScreenshot: null,
    },
    checkpoints: Object.fromEntries(CHECKPOINTS.map((item) => [item, { status: "FAIL", details: [] }])),
    errors: [],
  };

  let browser;
  let context;
  let page;
  let appPageUrlBeforeLegal = null;

  try {
    browser = await chromium.launch({ headless });
    context = await browser.newContext({ ignoreHTTPSErrors: true });
    page = await context.newPage();

    if (!startUrl) {
      throw new Error(
        "Missing start URL. Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) to the SaleADS login page URL for the target environment.",
      );
    }

    await page.goto(startUrl, { waitUntil: "domcontentloaded", timeout: 45000 });
    await waitForUi(page);

    const initialTitle = normalizeText(await page.title().catch(() => ""));
    const initialBody = normalizeText(await page.locator("body").innerText().catch(() => ""));
    if (
      /error code 525|ssl handshake failed|host error/i.test(initialTitle) ||
      /error code 525|ssl handshake failed|host error/i.test(initialBody)
    ) {
      throw new Error(`Target environment is unavailable (${initialTitle || "Cloudflare host error"}).`);
    }

    // Step 1 - Login with Google
    let activeLoginPage = page;
    const genericLoginButton = await pickFirstVisible([
      page.getByRole("button", { name: /iniciar sesión|iniciar sesion|sign in|login|acceder/i }).first(),
      page.getByRole("link", { name: /iniciar sesión|iniciar sesion|sign in|login|acceder/i }).first(),
      page.getByText(/iniciar sesión|iniciar sesion|sign in|login|acceder/i).first(),
    ]);

    if (genericLoginButton) {
      const entryPopupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      await clickAndWait(page, genericLoginButton);
      const entryPopup = await entryPopupPromise;
      if (entryPopup) {
        await entryPopup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
        await waitForUi(entryPopup);
        activeLoginPage = entryPopup;
      }
    }

    const googleLoginCandidates = [
      activeLoginPage.getByRole("button", { name: /sign in with google|iniciar sesión con google|google/i }).first(),
      activeLoginPage.getByText(/sign in with google|iniciar sesión con google|continuar con google/i).first(),
      activeLoginPage.getByRole("button", { name: /google/i }).first(),
    ];
    const googleLoginButton = await pickFirstVisible(googleLoginCandidates);
    if (!googleLoginButton) {
      throw new Error("Could not find login or 'Sign in with Google' control on the provided page.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickAndWait(activeLoginPage, googleLoginButton);
    let googlePage = await popupPromise;
    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
      await waitForUi(googlePage);
    }

    const accountPage = googlePage || activeLoginPage;
    const accountOption = accountPage.getByText(googleAccount, { exact: false }).first();
    if (await isVisible(accountOption)) {
      await clickAndWait(accountPage, accountOption);
    }

    await waitForUi(page);

    let appPage = page;
    let leftSidebar = await pickFirstVisible([
      appPage.getByRole("navigation").first(),
      appPage.locator("aside").first(),
      appPage.getByText(/Negocio/i).first(),
    ]);

    if (!leftSidebar) {
      for (const candidatePage of context.pages()) {
        if (candidatePage === appPage) {
          continue;
        }
        await waitForUi(candidatePage);
        const candidateSidebar = await pickFirstVisible([
          candidatePage.getByRole("navigation").first(),
          candidatePage.locator("aside").first(),
          candidatePage.getByText(/Negocio/i).first(),
        ]);
        if (candidateSidebar) {
          appPage = candidatePage;
          leftSidebar = candidateSidebar;
          break;
        }
      }
    }

    page = appPage;
    const mainUiVisible = !!leftSidebar;
    report.checkpoints["Login"].status = boolToStatus(mainUiVisible);
    report.checkpoints["Login"].details.push(
      mainUiVisible
        ? "Main interface loaded and left sidebar was detected."
        : "Main interface did not load or left sidebar was not found after login.",
    );

    const dashboardShot = path.join(screenshotsDir, "01-dashboard-after-login.png");
    await safeScreenshot(page, dashboardShot, true);
    report.evidence.dashboardScreenshot = dashboardShot;

    // Step 2 - Open Mi Negocio menu
    const negocioSection = await pickFirstVisible([
      page.getByText(/^Negocio$/i).first(),
      page.getByRole("button", { name: /Negocio/i }).first(),
      page.getByRole("link", { name: /Negocio/i }).first(),
      page.getByText(/Negocio/i).first(),
    ]);
    if (negocioSection) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocioEntry = await pickFirstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }).first(),
      page.getByRole("link", { name: /Mi Negocio/i }).first(),
      page.getByText(/Mi Negocio/i).first(),
    ]);
    if (miNegocioEntry) {
      await clickAndWait(page, miNegocioEntry);
    }

    const addBusinessVisible = await hasVisibleText(page, /Agregar Negocio/i);
    const manageBusinessVisible = await hasVisibleText(page, /Administrar Negocios/i);
    const menuPass = addBusinessVisible && manageBusinessVisible;
    report.checkpoints["Mi Negocio menu"].status = boolToStatus(menuPass);
    report.checkpoints["Mi Negocio menu"].details.push(
      menuPass
        ? "Mi Negocio menu expanded and both submenu items are visible."
        : "Submenu expansion or expected menu items were not fully visible.",
    );

    const menuShot = path.join(screenshotsDir, "02-mi-negocio-menu-expanded.png");
    await safeScreenshot(page, menuShot);
    report.evidence.expandedMenuScreenshot = menuShot;

    // Step 3 - Validate Agregar Negocio modal
    const agregarNegocioMenuAction = await pickFirstVisible([
      page.getByRole("menuitem", { name: /Agregar Negocio/i }).first(),
      page.getByRole("button", { name: /Agregar Negocio/i }).first(),
      page.getByRole("link", { name: /Agregar Negocio/i }).first(),
      page.getByText(/Agregar Negocio/i).first(),
    ]);

    if (agregarNegocioMenuAction) {
      await clickAndWait(page, agregarNegocioMenuAction);
    }

    const modalTitleVisible = await hasVisibleText(page, /Crear Nuevo Negocio/i);
    const nombreNegocioFieldVisible = await isVisible(page.getByLabel(/Nombre del Negocio/i).first()) ||
      await isVisible(page.getByPlaceholder(/Nombre del Negocio/i).first()) ||
      await isVisible(page.getByRole("textbox", { name: /Nombre del Negocio/i }).first());
    const quotaTextVisible = await hasVisibleText(page, /Tienes\s+2\s+de\s+3\s+negocios/i);
    const cancelVisible = await isVisible(page.getByRole("button", { name: /Cancelar/i }).first());
    const createVisible = await isVisible(page.getByRole("button", { name: /Crear Negocio/i }).first());
    const modalPass = modalTitleVisible && nombreNegocioFieldVisible && quotaTextVisible && cancelVisible && createVisible;
    report.checkpoints["Agregar Negocio modal"].status = boolToStatus(modalPass);
    report.checkpoints["Agregar Negocio modal"].details.push(
      modalPass
        ? "Modal and all required fields/actions were visible."
        : "One or more modal validations were not satisfied.",
    );

    const modalShot = path.join(screenshotsDir, "03-agregar-negocio-modal.png");
    await safeScreenshot(page, modalShot);
    report.evidence.agregarModalScreenshot = modalShot;

    // Optional interactions in modal.
    const negocioInput = await pickFirstVisible([
      page.getByLabel(/Nombre del Negocio/i).first(),
      page.getByPlaceholder(/Nombre del Negocio/i).first(),
      page.getByRole("textbox", { name: /Nombre del Negocio/i }).first(),
    ]);
    if (negocioInput) {
      await negocioInput.click({ timeout: 10000 }).catch(() => {});
      await negocioInput.fill("Negocio Prueba Automatización").catch(() => {});
    }

    const cancelModalButton = page.getByRole("button", { name: /Cancelar/i }).first();
    if (await isVisible(cancelModalButton)) {
      await clickAndWait(page, cancelModalButton);
    }

    // Step 4 - Open Administrar Negocios
    const miNegocioAgain = await pickFirstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }).first(),
      page.getByRole("link", { name: /Mi Negocio/i }).first(),
      page.getByText(/Mi Negocio/i).first(),
    ]);
    if (miNegocioAgain) {
      await clickAndWait(page, miNegocioAgain);
    }

    const administrarNegocios = await pickFirstVisible([
      page.getByRole("menuitem", { name: /Administrar Negocios/i }).first(),
      page.getByRole("button", { name: /Administrar Negocios/i }).first(),
      page.getByRole("link", { name: /Administrar Negocios/i }).first(),
      page.getByText(/Administrar Negocios/i).first(),
    ]);
    if (administrarNegocios) {
      await clickAndWait(page, administrarNegocios);
    }

    const infoGeneralVisible = await hasVisibleText(page, /Información General/i);
    const detallesCuentaVisible = await hasVisibleText(page, /Detalles de la Cuenta/i);
    const tusNegociosVisible = await hasVisibleText(page, /Tus Negocios/i);
    const legalVisible = await hasVisibleText(page, /Sección Legal/i);
    const administrarPass = infoGeneralVisible && detallesCuentaVisible && tusNegociosVisible && legalVisible;
    report.checkpoints["Administrar Negocios view"].status = boolToStatus(administrarPass);
    report.checkpoints["Administrar Negocios view"].details.push(
      administrarPass
        ? "All expected account sections are visible."
        : "One or more account sections were not found.",
    );

    const accountShot = path.join(screenshotsDir, "04-administrar-negocios-page.png");
    await safeScreenshot(page, accountShot, true);
    report.evidence.administrarNegociosScreenshot = accountShot;
    appPageUrlBeforeLegal = page.url();

    // Step 5 - Información General
    const infoGeneralChecks = [
      await isVisible(page.getByText(/@/).first()),
      await hasVisibleText(page, /BUSINESS PLAN/i),
      await isVisible(page.getByRole("button", { name: /Cambiar Plan/i }).first()),
    ];

    // User name can vary by account, so we infer it from presence of a profile/person display line.
    const possibleUserName = await pickFirstVisible([
      page.getByRole("heading").first(),
      page.getByText(/juan|lucas|barbier|garzon/i).first(),
      page.locator("section").locator("p,span,div").first(),
    ]);
    infoGeneralChecks.push(!!possibleUserName);

    const infoGeneralPass = infoGeneralChecks.every(Boolean);
    report.checkpoints["Información General"].status = boolToStatus(infoGeneralPass);
    report.checkpoints["Información General"].details.push(
      infoGeneralPass
        ? "User identity, email, plan label, and plan action button were validated."
        : "At least one Información General element was not visible.",
    );

    // Step 6 - Detalles de la Cuenta
    const detallesPass =
      (await hasVisibleText(page, /Cuenta creada/i)) &&
      (await hasVisibleText(page, /Estado activo/i)) &&
      (await hasVisibleText(page, /Idioma seleccionado/i));
    report.checkpoints["Detalles de la Cuenta"].status = boolToStatus(detallesPass);
    report.checkpoints["Detalles de la Cuenta"].details.push(
      detallesPass
        ? "All required account-detail labels are visible."
        : "One or more account-detail labels were not found.",
    );

    // Step 7 - Tus Negocios
    const tusNegociosPass =
      (await hasVisibleText(page, /Tus Negocios/i)) &&
      (await hasVisibleText(page, /Agregar Negocio/i)) &&
      (await hasVisibleText(page, /Tienes\s+2\s+de\s+3\s+negocios/i));
    report.checkpoints["Tus Negocios"].status = boolToStatus(tusNegociosPass);
    report.checkpoints["Tus Negocios"].details.push(
      tusNegociosPass
        ? "Business list area and quota/action controls are visible."
        : "Business list validations were incomplete.",
    );

    // Step 8 - Términos y Condiciones
    const terminosLink = await pickFirstVisible([
      page.getByRole("link", { name: /Términos y Condiciones/i }).first(),
      page.getByText(/Términos y Condiciones/i).first(),
    ]);

    if (terminosLink) {
      const termsPopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, terminosLink);
      let termsPage = await termsPopupPromise;
      if (termsPage) {
        await termsPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
        await waitForUi(termsPage);
      } else {
        termsPage = page;
        await waitForUi(termsPage);
      }

      const termsValidation = await validateTermsOrPrivacy(termsPage, /Términos y Condiciones/i);
      const termsPass = termsValidation.hasHeading && termsValidation.hasLegalText;
      report.checkpoints["Términos y Condiciones"].status = boolToStatus(termsPass);
      report.checkpoints["Términos y Condiciones"].details.push(
        termsPass
          ? "Terms heading and legal text content are visible."
          : "Terms page missing expected heading or enough legal text.",
      );

      const terminosShot = path.join(screenshotsDir, "05-terminos-y-condiciones.png");
      await safeScreenshot(termsPage, terminosShot, true);
      report.evidence.terminosScreenshot = terminosShot;
      report.evidence.terminosUrl = termsPage.url();

      if (termsPage !== page) {
        await termsPage.close().catch(() => {});
        await page.bringToFront().catch(() => {});
        await waitForUi(page);
      } else if (appPageUrlBeforeLegal) {
        await page.goto(appPageUrlBeforeLegal, { waitUntil: "domcontentloaded", timeout: 30000 }).catch(() => {});
        await waitForUi(page);
      }
    } else {
      report.checkpoints["Términos y Condiciones"].status = "FAIL";
      report.checkpoints["Términos y Condiciones"].details.push("Could not find 'Términos y Condiciones' link.");
    }

    // Step 9 - Política de Privacidad
    const privacidadLink = await pickFirstVisible([
      page.getByRole("link", { name: /Política de Privacidad/i }).first(),
      page.getByText(/Política de Privacidad/i).first(),
    ]);

    if (privacidadLink) {
      const privacyPopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, privacidadLink);
      let privacyPage = await privacyPopupPromise;
      if (privacyPage) {
        await privacyPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
        await waitForUi(privacyPage);
      } else {
        privacyPage = page;
        await waitForUi(privacyPage);
      }

      const privacyValidation = await validateTermsOrPrivacy(privacyPage, /Política de Privacidad/i);
      const privacyPass = privacyValidation.hasHeading && privacyValidation.hasLegalText;
      report.checkpoints["Política de Privacidad"].status = boolToStatus(privacyPass);
      report.checkpoints["Política de Privacidad"].details.push(
        privacyPass
          ? "Privacy heading and legal text content are visible."
          : "Privacy page missing expected heading or enough legal text.",
      );

      const privacyShot = path.join(screenshotsDir, "06-politica-de-privacidad.png");
      await safeScreenshot(privacyPage, privacyShot, true);
      report.evidence.privacidadScreenshot = privacyShot;
      report.evidence.privacidadUrl = privacyPage.url();

      if (privacyPage !== page) {
        await privacyPage.close().catch(() => {});
        await page.bringToFront().catch(() => {});
        await waitForUi(page);
      } else if (appPageUrlBeforeLegal) {
        await page.goto(appPageUrlBeforeLegal, { waitUntil: "domcontentloaded", timeout: 30000 }).catch(() => {});
        await waitForUi(page);
      }
    } else {
      report.checkpoints["Política de Privacidad"].status = "FAIL";
      report.checkpoints["Política de Privacidad"].details.push("Could not find 'Política de Privacidad' link.");
    }
  } catch (error) {
    const fatalMessage = error.message;
    report.errors.push(fatalMessage);
    for (const checkpoint of CHECKPOINTS) {
      if (!report.checkpoints[checkpoint].details.length) {
        report.checkpoints[checkpoint].details.push(`Blocked by fatal error: ${fatalMessage}`);
      }
    }
    if (page) {
      const fatalShot = path.join(screenshotsDir, "00-fatal-error-state.png");
      await safeScreenshot(page, fatalShot, true);
      report.evidence.errorStateScreenshot = fatalShot;
    }
  } finally {
    if (context) {
      await context.close().catch(() => {});
    }
    if (browser) {
      await browser.close().catch(() => {});
    }
  }

  const summaryLines = [];
  summaryLines.push("SaleADS Mi Negocio Workflow Validation");
  summaryLines.push(`Run ID: ${report.runId}`);
  summaryLines.push(`Run At: ${report.runAt}`);
  summaryLines.push("");
  summaryLines.push("Checkpoint Results:");

  for (const checkpoint of CHECKPOINTS) {
    const result = report.checkpoints[checkpoint];
    summaryLines.push(`- ${checkpoint}: ${result.status}`);
  }

  summaryLines.push("");
  summaryLines.push("Evidence:");
  summaryLines.push(`- Dashboard screenshot: ${report.evidence.dashboardScreenshot || "N/A"}`);
  summaryLines.push(`- Mi Negocio menu screenshot: ${report.evidence.expandedMenuScreenshot || "N/A"}`);
  summaryLines.push(`- Agregar Negocio modal screenshot: ${report.evidence.agregarModalScreenshot || "N/A"}`);
  summaryLines.push(`- Administrar Negocios screenshot: ${report.evidence.administrarNegociosScreenshot || "N/A"}`);
  summaryLines.push(`- Términos screenshot: ${report.evidence.terminosScreenshot || "N/A"}`);
  summaryLines.push(`- Términos URL: ${report.evidence.terminosUrl || "N/A"}`);
  summaryLines.push(`- Privacidad screenshot: ${report.evidence.privacidadScreenshot || "N/A"}`);
  summaryLines.push(`- Privacidad URL: ${report.evidence.privacidadUrl || "N/A"}`);
  summaryLines.push(`- Fatal error screenshot: ${report.evidence.errorStateScreenshot || "N/A"}`);

  if (report.errors.length) {
    summaryLines.push("");
    summaryLines.push("Errors:");
    for (const err of report.errors) {
      summaryLines.push(`- ${err}`);
    }
  }

  ensureDir(artifactsRoot);
  fs.writeFileSync(path.join(runDir, "report.json"), JSON.stringify(report, null, 2), "utf8");
  fs.writeFileSync(path.join(runDir, "summary.txt"), summaryLines.join("\n"), "utf8");
  fs.writeFileSync(latestReportPath, JSON.stringify(report, null, 2), "utf8");
  fs.writeFileSync(latestSummaryPath, summaryLines.join("\n"), "utf8");

  console.log(summaryLines.join("\n"));

  const anyFailure = CHECKPOINTS.some((checkpoint) => report.checkpoints[checkpoint].status !== "PASS");
  if (anyFailure || report.errors.length) {
    process.exitCode = 1;
  }
}

run();
