import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const TEST_NAME = "saleads_mi_negocio_full_test";
const STEP_FIELDS = [
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

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const RUN_ID = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACTS_DIR = path.resolve(__dirname, "artifacts", RUN_ID);
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");

fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });

const report = {
  name: TEST_NAME,
  runId: RUN_ID,
  executedAt: new Date().toISOString(),
  environment: {
    loginUrl: sanitizeUrlForReport(
      process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || ""
    ),
    headless: parseBooleanEnv("SALEADS_HEADLESS", true),
    browser: "chromium"
  },
  results: Object.fromEntries(
    STEP_FIELDS.map((step) => [
      step,
      {
        status: "FAIL",
        details: "Not executed.",
        evidence: []
      }
    ])
  )
};

function sanitizeUrlForReport(urlValue) {
  if (!urlValue) {
    return "";
  }

  try {
    const parsed = new URL(urlValue);
    return `${parsed.protocol}//${parsed.host}${parsed.pathname}`;
  } catch {
    return urlValue;
  }
}

function parseBooleanEnv(name, defaultValue) {
  const raw = process.env[name];
  if (!raw) {
    return defaultValue;
  }

  const normalized = raw.trim().toLowerCase();
  if (["0", "false", "no", "off"].includes(normalized)) {
    return false;
  }
  if (["1", "true", "yes", "on"].includes(normalized)) {
    return true;
  }
  return defaultValue;
}

function updateStep(step, status, details, evidenceItems = []) {
  report.results[step] = {
    status,
    details,
    evidence: evidenceItems
  };
}

function appendEvidence(step, item) {
  report.results[step].evidence.push(item);
}

function markAsPrerequisiteFailed(step, reason) {
  if (report.results[step].details === "Not executed.") {
    updateStep(step, "FAIL", `Prerequisite failed: ${reason}`);
  }
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function normalizeReportPath(absPath) {
  return path.relative(__dirname, absPath).split(path.sep).join("/");
}

async function saveScreenshot(page, fileName, fullPage = false) {
  const absolutePath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({
    path: absolutePath,
    fullPage
  });
  return normalizeReportPath(absolutePath);
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function findVisibleLocator(page, candidates, timeoutMs = 15000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    for (const candidateFactory of candidates) {
      const candidate = candidateFactory().first();
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error("No visible candidate element was found in time.");
}

async function clickAndWait(page, locator) {
  await locator.waitFor({ state: "visible", timeout: 15000 });
  await locator.click();
  await waitForUi(page);
}

async function textMustBeVisible(page, matcher, timeoutMs = 15000) {
  const regex = matcher instanceof RegExp ? matcher : new RegExp(escapeRegex(matcher), "i");
  const locator = page.getByText(regex).first();
  await locator.waitFor({ state: "visible", timeout: timeoutMs });
  return locator;
}

async function isSidebarVisible(page) {
  const candidates = [
    () => page.locator("aside"),
    () => page.getByRole("navigation"),
    () => page.getByText(/negocio|mi negocio/i)
  ];

  for (const build of candidates) {
    if (await build().first().isVisible().catch(() => false)) {
      return true;
    }
  }

  return false;
}

function inferNameFromSectionText(sectionText, emailMatch) {
  const ignoredPatterns = [
    /informacion general/i,
    /business plan/i,
    /cambiar plan/i,
    /cuenta creada/i,
    /estado activo/i,
    /idioma seleccionado/i,
    /tienes\s+\d+\s+de\s+\d+\s+negocios/i
  ];

  const lines = sectionText
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 2);

  return lines.find((line) => {
    if (emailMatch && line.includes(emailMatch[0])) {
      return false;
    }
    if (!/[A-Za-z]/.test(line)) {
      return false;
    }
    return !ignoredPatterns.some((pattern) => pattern.test(line));
  });
}

async function openAndValidateLegalPage({
  appPage,
  context,
  linkMatcher,
  headingRegex,
  screenshotName
}) {
  const matcherRegex =
    linkMatcher instanceof RegExp ? linkMatcher : new RegExp(escapeRegex(linkMatcher), "i");
  const linkLocator = await findVisibleLocator(appPage, [
    () => appPage.getByRole("link", { name: matcherRegex }),
    () => appPage.getByRole("button", { name: matcherRegex }),
    () => appPage.getByText(matcherRegex)
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const navPromise = appPage
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 8000 })
    .catch(() => null);

  await linkLocator.click();

  const popupPage = await popupPromise;
  let targetPage = appPage;
  let openedNewTab = false;

  if (popupPage) {
    openedNewTab = true;
    targetPage = popupPage;
    await waitForUi(targetPage);
  } else {
    await navPromise;
    await waitForUi(appPage);
  }

  await textMustBeVisible(targetPage, headingRegex, 15000);

  const legalText = (await targetPage.locator("body").innerText()).trim();
  if (legalText.length < 120) {
    throw new Error(
      `Legal page content is unexpectedly short (${legalText.length} chars).`
    );
  }

  const screenshot = await saveScreenshot(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (openedNewTab) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage
      .goBack({ waitUntil: "domcontentloaded", timeout: 15000 })
      .catch(async () => {
        await appPage.reload({ waitUntil: "domcontentloaded", timeout: 15000 }).catch(() => {});
      });
    await waitForUi(appPage);
  }

  return {
    screenshot,
    finalUrl,
    openedNewTab
  };
}

function writeFinalReport() {
  const values = Object.values(report.results);
  const passCount = values.filter((result) => result.status === "PASS").length;
  const failCount = values.length - passCount;

  report.summary = {
    passCount,
    failCount,
    overallStatus: failCount === 0 ? "PASS" : "FAIL"
  };

  const reportPath = path.join(ARTIFACTS_DIR, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");
  return reportPath;
}

let browser;
let exitCode = 0;

try {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "Missing SALEADS_LOGIN_URL (or SALEADS_BASE_URL). A URL must be provided so the script can open the current SaleADS environment login page."
    );
  }

  browser = await chromium.launch({
    headless: parseBooleanEnv("SALEADS_HEADLESS", true)
  });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  let page = await context.newPage();

  await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 45000 });
  await waitForUi(page);

  // Step 1 - Login with Google
  try {
    if (!(await isSidebarVisible(page))) {
      const loginButton = await findVisibleLocator(page, [
        () => page.getByRole("button", { name: /sign in with google|login with google/i }),
        () => page.getByRole("button", { name: /iniciar sesion con google|continuar con google/i }),
        () => page.getByText(/sign in with google|iniciar sesion con google|continuar con google/i)
      ]);

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, loginButton);

      const googlePopup = await popupPromise;
      if (googlePopup) {
        await waitForUi(googlePopup);
        const accountLocator = await findVisibleLocator(
          googlePopup,
          [
            () => googlePopup.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
            () =>
              googlePopup.getByRole("button", {
                name: /juanlucasbarbiergarzon@gmail\.com/i
              }),
            () =>
              googlePopup.getByRole("link", {
                name: /juanlucasbarbiergarzon@gmail\.com/i
              })
          ],
          12000
        ).catch(() => null);

        if (accountLocator) {
          await accountLocator.click();
        }

        await googlePopup.waitForEvent("close", { timeout: 15000 }).catch(() => {});
        await page.bringToFront();
        await waitForUi(page);
      } else {
        const accountLocator = await findVisibleLocator(
          page,
          [
            () => page.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
            () =>
              page.getByRole("button", {
                name: /juanlucasbarbiergarzon@gmail\.com/i
              })
          ],
          7000
        ).catch(() => null);

        if (accountLocator) {
          await clickAndWait(page, accountLocator);
        }
      }
    }

    await page.waitForFunction(() => {
      const hasAside = Boolean(document.querySelector("aside"));
      const hasNav = Boolean(document.querySelector("nav"));
      const bodyText = document.body?.innerText || "";
      return hasAside || hasNav || /negocio|mi negocio/i.test(bodyText);
    }, { timeout: 30000 });

    const dashboardShot = await saveScreenshot(page, "01-dashboard-loaded.png", true);
    updateStep(
      "Login",
      "PASS",
      "Main application interface loaded and left sidebar is visible.",
      [{ type: "screenshot", path: dashboardShot }]
    );
  } catch (error) {
    updateStep("Login", "FAIL", `Login validation failed: ${error.message}`);
    markAsPrerequisiteFailed("Mi Negocio menu", "Login step failed.");
    markAsPrerequisiteFailed("Agregar Negocio modal", "Login step failed.");
    markAsPrerequisiteFailed("Administrar Negocios view", "Login step failed.");
    markAsPrerequisiteFailed("Información General", "Login step failed.");
    markAsPrerequisiteFailed("Detalles de la Cuenta", "Login step failed.");
    markAsPrerequisiteFailed("Tus Negocios", "Login step failed.");
    markAsPrerequisiteFailed("Términos y Condiciones", "Login step failed.");
    markAsPrerequisiteFailed("Política de Privacidad", "Login step failed.");
    throw error;
  }

  // Step 2 - Open Mi Negocio menu
  try {
    const negocioMenu = await findVisibleLocator(page, [
      () => page.getByRole("button", { name: /negocio|mi negocio/i }),
      () => page.getByRole("link", { name: /negocio|mi negocio/i }),
      () => page.getByText(/negocio|mi negocio/i)
    ]);
    await clickAndWait(page, negocioMenu);

    await textMustBeVisible(page, /agregar negocio/i, 10000);
    await textMustBeVisible(page, /administrar negocios/i, 10000);

    const menuShot = await saveScreenshot(page, "02-mi-negocio-menu-expanded.png");
    updateStep(
      "Mi Negocio menu",
      "PASS",
      "Mi Negocio submenu expanded with both required options visible.",
      [{ type: "screenshot", path: menuShot }]
    );
  } catch (error) {
    updateStep("Mi Negocio menu", "FAIL", `Could not expand Mi Negocio menu: ${error.message}`);
    markAsPrerequisiteFailed("Agregar Negocio modal", "Mi Negocio menu step failed.");
    markAsPrerequisiteFailed("Administrar Negocios view", "Mi Negocio menu step failed.");
    markAsPrerequisiteFailed("Información General", "Mi Negocio menu step failed.");
    markAsPrerequisiteFailed("Detalles de la Cuenta", "Mi Negocio menu step failed.");
    markAsPrerequisiteFailed("Tus Negocios", "Mi Negocio menu step failed.");
    markAsPrerequisiteFailed("Términos y Condiciones", "Mi Negocio menu step failed.");
    markAsPrerequisiteFailed("Política de Privacidad", "Mi Negocio menu step failed.");
    throw error;
  }

  // Step 3 - Validate Agregar Negocio modal
  try {
    const agregarNegocio = await findVisibleLocator(page, [
      () => page.getByRole("link", { name: /agregar negocio/i }),
      () => page.getByRole("button", { name: /agregar negocio/i }),
      () => page.getByText(/agregar negocio/i)
    ]);
    await clickAndWait(page, agregarNegocio);

    await textMustBeVisible(page, /crear nuevo negocio/i, 15000);
    await textMustBeVisible(page, /nombre del negocio/i, 10000);
    await textMustBeVisible(page, /tienes\s+2\s+de\s+3\s+negocios/i, 10000);
    await textMustBeVisible(page, /cancelar/i, 10000);
    await textMustBeVisible(page, /crear negocio/i, 10000);

    const modalShot = await saveScreenshot(page, "03-agregar-negocio-modal.png");
    appendEvidence("Agregar Negocio modal", { type: "screenshot", path: modalShot });

    const negocioInput = await findVisibleLocator(
      page,
      [
        () => page.getByLabel(/nombre del negocio/i),
        () => page.getByPlaceholder(/nombre del negocio/i),
        () => page.locator("input[name*='negocio'], input[id*='negocio']")
      ],
      6000
    ).catch(() => null);

    if (negocioInput) {
      await negocioInput.click();
      await negocioInput.fill("Negocio Prueba Automatizacion");
      await waitForUi(page);
    }

    const cancelButton = await findVisibleLocator(page, [
      () => page.getByRole("button", { name: /cancelar/i }),
      () => page.getByText(/cancelar/i)
    ]);
    await clickAndWait(page, cancelButton);

    updateStep(
      "Agregar Negocio modal",
      "PASS",
      "Modal content validated and optional input/cancel actions executed.",
      report.results["Agregar Negocio modal"].evidence
    );
  } catch (error) {
    updateStep(
      "Agregar Negocio modal",
      "FAIL",
      `Agregar Negocio modal validation failed: ${error.message}`
    );
    // Continue trying next major step, since menu might still allow navigation.
  }

  // Step 4 - Open Administrar Negocios
  try {
    const administrarVisible = await page
      .getByText(/administrar negocios/i)
      .first()
      .isVisible()
      .catch(() => false);
    if (!administrarVisible) {
      const negocioMenu = await findVisibleLocator(page, [
        () => page.getByRole("button", { name: /negocio|mi negocio/i }),
        () => page.getByText(/negocio|mi negocio/i)
      ]);
      await clickAndWait(page, negocioMenu);
    }

    const administrarNegocios = await findVisibleLocator(page, [
      () => page.getByRole("link", { name: /administrar negocios/i }),
      () => page.getByRole("button", { name: /administrar negocios/i }),
      () => page.getByText(/administrar negocios/i)
    ]);
    await clickAndWait(page, administrarNegocios);

    await textMustBeVisible(page, /informacion general/i, 15000);
    await textMustBeVisible(page, /detalles de la cuenta/i, 15000);
    await textMustBeVisible(page, /tus negocios/i, 15000);
    await textMustBeVisible(page, /seccion legal/i, 15000);

    const accountShot = await saveScreenshot(page, "04-administrar-negocios-page-full.png", true);
    updateStep(
      "Administrar Negocios view",
      "PASS",
      "Administrar Negocios page loaded with all required account sections.",
      [{ type: "screenshot", path: accountShot }]
    );
  } catch (error) {
    updateStep(
      "Administrar Negocios view",
      "FAIL",
      `Administrar Negocios view validation failed: ${error.message}`
    );
    markAsPrerequisiteFailed("Información General", "Administrar Negocios view step failed.");
    markAsPrerequisiteFailed("Detalles de la Cuenta", "Administrar Negocios view step failed.");
    markAsPrerequisiteFailed("Tus Negocios", "Administrar Negocios view step failed.");
    markAsPrerequisiteFailed("Términos y Condiciones", "Administrar Negocios view step failed.");
    markAsPrerequisiteFailed("Política de Privacidad", "Administrar Negocios view step failed.");
    throw error;
  }

  // Step 5 - Validate Informacion General
  try {
    await textMustBeVisible(page, /business plan/i, 12000);
    await textMustBeVisible(page, /cambiar plan/i, 12000);

    const bodyText = await page.locator("body").innerText();
    const emailMatch = bodyText.match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
    if (!emailMatch) {
      throw new Error("Could not find a visible email address.");
    }

    const inferredName = inferNameFromSectionText(bodyText, emailMatch);
    if (!inferredName) {
      throw new Error("Could not infer a visible user name text.");
    }

    updateStep(
      "Información General",
      "PASS",
      `General information validated. Name candidate: "${inferredName}". Email: ${emailMatch[0]}.`
    );
  } catch (error) {
    updateStep("Información General", "FAIL", `Informacion General validation failed: ${error.message}`);
  }

  // Step 6 - Validate Detalles de la Cuenta
  try {
    await textMustBeVisible(page, /cuenta creada/i, 12000);
    await textMustBeVisible(page, /estado activo/i, 12000);
    await textMustBeVisible(page, /idioma seleccionado/i, 12000);
    updateStep(
      "Detalles de la Cuenta",
      "PASS",
      "Detalles de la Cuenta fields are visible."
    );
  } catch (error) {
    updateStep(
      "Detalles de la Cuenta",
      "FAIL",
      `Detalles de la Cuenta validation failed: ${error.message}`
    );
  }

  // Step 7 - Validate Tus Negocios
  try {
    await textMustBeVisible(page, /tus negocios/i, 12000);
    await textMustBeVisible(page, /agregar negocio/i, 12000);
    await textMustBeVisible(page, /tienes\s+2\s+de\s+3\s+negocios/i, 12000);

    const sectionText = await page.locator("body").innerText();
    const listLikeCandidates = sectionText
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line.length > 2)
      .filter(
        (line) =>
          !/tus negocios|agregar negocio|tienes\s+\d+\s+de\s+\d+\s+negocios/i.test(line)
      );

    if (listLikeCandidates.length === 0) {
      throw new Error("Business list details are not visible.");
    }

    updateStep("Tus Negocios", "PASS", "Tus Negocios section content is visible.");
  } catch (error) {
    updateStep("Tus Negocios", "FAIL", `Tus Negocios validation failed: ${error.message}`);
  }

  // Step 8 - Validate Terminos y Condiciones
  try {
    const termsResult = await openAndValidateLegalPage({
      appPage: page,
      context,
      linkMatcher: /t[eé]rminos y condiciones/i,
      headingRegex: /t[eé]rminos y condiciones/i,
      screenshotName: "08-terminos-y-condiciones.png"
    });
    updateStep(
      "Términos y Condiciones",
      "PASS",
      "Terminos y Condiciones legal page validated.",
      [
        { type: "screenshot", path: termsResult.screenshot },
        { type: "url", value: termsResult.finalUrl },
        { type: "newTab", value: String(termsResult.openedNewTab) }
      ]
    );
  } catch (error) {
    updateStep(
      "Términos y Condiciones",
      "FAIL",
      `Terminos y Condiciones validation failed: ${error.message}`
    );
  }

  // Step 9 - Validate Politica de Privacidad
  try {
    const privacyResult = await openAndValidateLegalPage({
      appPage: page,
      context,
      linkMatcher: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: "09-politica-de-privacidad.png"
    });
    updateStep(
      "Política de Privacidad",
      "PASS",
      "Politica de Privacidad legal page validated.",
      [
        { type: "screenshot", path: privacyResult.screenshot },
        { type: "url", value: privacyResult.finalUrl },
        { type: "newTab", value: String(privacyResult.openedNewTab) }
      ]
    );
  } catch (error) {
    updateStep(
      "Política de Privacidad",
      "FAIL",
      `Politica de Privacidad validation failed: ${error.message}`
    );
  }
} catch (error) {
  exitCode = 1;
  if (report.results.Login.details === "Not executed.") {
    updateStep("Login", "FAIL", error.message);
    markAsPrerequisiteFailed("Mi Negocio menu", "Login step failed.");
    markAsPrerequisiteFailed("Agregar Negocio modal", "Login step failed.");
    markAsPrerequisiteFailed("Administrar Negocios view", "Login step failed.");
    markAsPrerequisiteFailed("Información General", "Login step failed.");
    markAsPrerequisiteFailed("Detalles de la Cuenta", "Login step failed.");
    markAsPrerequisiteFailed("Tus Negocios", "Login step failed.");
    markAsPrerequisiteFailed("Términos y Condiciones", "Login step failed.");
    markAsPrerequisiteFailed("Política de Privacidad", "Login step failed.");
  }
} finally {
  if (browser) {
    await browser.close().catch(() => {});
  }
}

const reportPath = writeFinalReport();
console.log(`Final report: ${reportPath}`);
console.log(JSON.stringify(report.summary, null, 2));

if (report.summary.overallStatus === "FAIL") {
  exitCode = 1;
}

process.exit(exitCode);
