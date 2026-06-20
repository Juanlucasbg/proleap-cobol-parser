import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const TEST_NAME = "saleads_mi_negocio_full_test";
const TARGET_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const baseUrl = process.env.SALEADS_BASE_URL ?? process.env.BASE_URL ?? "";
const headless = process.env.HEADLESS !== "false";

const runId = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.resolve(
  process.cwd(),
  "artifacts",
  TEST_NAME,
  runId,
);
const reportPath = path.join(artifactsDir, "report.json");

const reportFields = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const normalizeWhitespace = (value) => value.replace(/\s+/g, " ").trim();

const results = Object.fromEntries(
  reportFields.map((field) => [
    field,
    { status: "FAIL", details: "Not executed", evidence: [] },
  ]),
);

let screenshotIndex = 1;

const ensureDir = async (dirPath) => {
  await fs.mkdir(dirPath, { recursive: true });
};

const waitForUi = async (page) => {
  try {
    await page.waitForLoadState("domcontentloaded", { timeout: 20_000 });
  } catch (_error) {
    // Best effort: continue even if domcontentloaded wait expires.
  }

  try {
    await page.waitForLoadState("networkidle", { timeout: 15_000 });
  } catch (_error) {
    // Best effort: many apps keep long-polling requests active.
  }

  await page.waitForTimeout(500);
};

const screenshot = async (page, label, fullPage = false) => {
  const fileName = `${String(screenshotIndex).padStart(2, "0")}-${label}.png`;
  screenshotIndex += 1;
  const filePath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
};

const firstVisibleLocator = async (scope, patterns, timeoutMs = 20_000) => {
  const startedAt = Date.now();
  const waiter = typeof scope.waitForTimeout === "function" ? scope : scope.page();

  while (Date.now() - startedAt < timeoutMs) {
    for (const pattern of patterns) {
      const locator = scope.getByText(pattern, { exact: false }).first();
      const visible = await locator.isVisible().catch(() => false);
      if (visible) {
        return locator;
      }
    }
    await waiter.waitForTimeout(300);
  }

  return null;
};

const clickByVisibleText = async (scope, pageForWait, patterns, errorMessage) => {
  const locator = await firstVisibleLocator(scope, patterns, 20_000);
  if (!locator) {
    throw new Error(errorMessage);
  }
  await locator.click();
  await waitForUi(pageForWait);
  return locator;
};

const expectVisibleText = async (scope, patterns, errorMessage, timeoutMs = 20_000) => {
  const locator = await firstVisibleLocator(scope, patterns, timeoutMs);
  if (!locator) {
    throw new Error(errorMessage);
  }
  return locator;
};

const setResultPass = (field, details, evidence = []) => {
  results[field] = { status: "PASS", details, evidence };
};

const setResultFail = (field, error, evidence = []) => {
  results[field] = {
    status: "FAIL",
    details: error instanceof Error ? error.message : String(error),
    evidence,
  };
};

const runStep = async (field, runFn) => {
  try {
    const payload = await runFn();
    setResultPass(field, payload?.details ?? "Step validated.", payload?.evidence ?? []);
  } catch (error) {
    setResultFail(field, error);
  }
};

const maybeSelectGoogleAccount = async (appPage, popupPage) => {
  const emailPattern = new RegExp(escapeRegex(TARGET_EMAIL), "i");
  const candidatePages = [popupPage, appPage].filter(Boolean);

  for (const candidatePage of candidatePages) {
    await waitForUi(candidatePage);
    const account = candidatePage.getByText(emailPattern, { exact: false }).first();
    const visible = await account.isVisible().catch(() => false);
    if (visible) {
      await account.click();
      await waitForUi(candidatePage);
      return true;
    }
  }

  return false;
};

const validateLegalPage = async ({
  appPage,
  context,
  linkPatterns,
  headingPatterns,
  screenshotLabel,
  fallbackAppUrl,
}) => {
  const link = await firstVisibleLocator(appPage, linkPatterns, 15_000);
  if (!link) {
    throw new Error(`Could not find legal link matching: ${linkPatterns.join(", ")}`);
  }

  const possiblePopup = context
    .waitForEvent("page", { timeout: 8_000 })
    .catch(() => null);

  await link.click();
  await waitForUi(appPage);

  const popup = await possiblePopup;
  const legalPage = popup ?? appPage;
  await waitForUi(legalPage);

  await expectVisibleText(
    legalPage,
    headingPatterns,
    "Legal page heading is not visible.",
    20_000,
  );

  const legalBodyText = normalizeWhitespace(await legalPage.locator("body").innerText());
  if (legalBodyText.length < 120) {
    throw new Error("Legal page content appears too short to validate.");
  }

  const screenshotPath = await screenshot(legalPage, screenshotLabel, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (fallbackAppUrl) {
    await appPage.goto(fallbackAppUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(appPage);
  }

  return {
    screenshotPath,
    finalUrl,
    openedInNewTab: Boolean(popup),
  };
};

const run = async () => {
  await ensureDir(artifactsDir);

  const report = {
    name: TEST_NAME,
    goal: "Login with Google and validate Mi Negocio workflow end to end.",
    startedAt: new Date().toISOString(),
    environment: {
      baseUrl: baseUrl || null,
      headless,
    },
    results,
  };

  let browser;

  try {
    if (!baseUrl) {
      throw new Error(
        "Missing SALEADS_BASE_URL or BASE_URL. Provide the current environment login URL.",
      );
    }

    browser = await chromium.launch({ headless });
    const context = await browser.newContext();
    const page = await context.newPage();

    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    await runStep("Login", async () => {
      const loginButton = await firstVisibleLocator(
        page,
        [/sign in with google/i, /iniciar sesion con google/i, /google/i],
        20_000,
      );
      if (!loginButton) {
        throw new Error("Google login button was not found.");
      }

      const popupPromise = context
        .waitForEvent("page", { timeout: 8_000 })
        .catch(() => null);

      await loginButton.click();
      await waitForUi(page);

      const popup = await popupPromise;
      await maybeSelectGoogleAccount(page, popup);

      if (popup) {
        await popup.waitForEvent("close", { timeout: 60_000 }).catch(() => null);
      }

      await page.bringToFront();
      await waitForUi(page);

      const sidebar = page
        .locator("aside, nav")
        .filter({ hasText: /negocio|mi negocio|dashboard/i })
        .first();
      const sidebarVisible = await sidebar.isVisible().catch(() => false);
      if (!sidebarVisible) {
        throw new Error("Main app sidebar navigation is not visible after login.");
      }

      const dashboardShot = await screenshot(page, "dashboard-loaded");
      return {
        details: "Main interface and sidebar are visible after Google login.",
        evidence: [dashboardShot],
      };
    });

    await runStep("Mi Negocio menu", async () => {
      await expectVisibleText(page, [/negocio/i], "Sidebar section 'Negocio' is not visible.");
      await clickByVisibleText(
        page,
        page,
        [/mi negocio/i],
        "Option 'Mi Negocio' is not visible in the sidebar.",
      );

      await expectVisibleText(
        page,
        [/agregar negocio/i],
        "Submenu entry 'Agregar Negocio' is not visible.",
      );
      await expectVisibleText(
        page,
        [/administrar negocios/i],
        "Submenu entry 'Administrar Negocios' is not visible.",
      );

      const menuShot = await screenshot(page, "mi-negocio-menu-expanded");
      return {
        details: "Mi Negocio submenu expanded with expected entries.",
        evidence: [menuShot],
      };
    });

    await runStep("Agregar Negocio modal", async () => {
      await clickByVisibleText(
        page,
        page,
        [/agregar negocio/i],
        "Could not click 'Agregar Negocio'.",
      );

      const modal = page.getByRole("dialog").first();
      const modalVisible = await modal
        .waitFor({ state: "visible", timeout: 15_000 })
        .then(() => true)
        .catch(() => false);
      if (!modalVisible) {
        throw new Error("The 'Crear Nuevo Negocio' modal did not appear.");
      }

      await expectVisibleText(modal, [/crear nuevo negocio/i], "Modal title is missing.");

      const nameInput = modal.getByLabel(/nombre del negocio/i).first();
      const nameInputVisible =
        (await nameInput.isVisible().catch(() => false)) ||
        (await modal
          .getByPlaceholder(/nombre del negocio/i)
          .first()
          .isVisible()
          .catch(() => false));
      if (!nameInputVisible) {
        throw new Error("Input 'Nombre del Negocio' is not visible.");
      }

      await expectVisibleText(
        modal,
        [/tienes\s*2\s*de\s*3\s*negocios/i],
        "Usage text 'Tienes 2 de 3 negocios' is missing in the modal.",
      );
      await expectVisibleText(modal, [/cancelar/i], "Button 'Cancelar' is missing.");
      await expectVisibleText(modal, [/crear negocio/i], "Button 'Crear Negocio' is missing.");

      if (await nameInput.isVisible().catch(() => false)) {
        await nameInput.click();
        await nameInput.fill("Negocio Prueba Automatizacion");
      }

      const modalShot = await screenshot(page, "agregar-negocio-modal");
      await clickByVisibleText(
        modal,
        page,
        [/cancelar/i],
        "Could not close modal with 'Cancelar'.",
      );

      return {
        details: "Agregar Negocio modal contains all expected fields and controls.",
        evidence: [modalShot],
      };
    });

    await runStep("Administrar Negocios view", async () => {
      const adminVisible = await page
        .getByText(/administrar negocios/i, { exact: false })
        .first()
        .isVisible()
        .catch(() => false);

      if (!adminVisible) {
        await clickByVisibleText(
          page,
          page,
          [/mi negocio/i],
          "Could not re-open 'Mi Negocio' to access admin entry.",
        );
      }

      await clickByVisibleText(
        page,
        page,
        [/administrar negocios/i],
        "Could not open 'Administrar Negocios'.",
      );

      await expectVisibleText(page, [/informacion general/i], "Section 'Informacion General' missing.");
      await expectVisibleText(
        page,
        [/detalles de la cuenta/i],
        "Section 'Detalles de la Cuenta' missing.",
      );
      await expectVisibleText(page, [/tus negocios/i], "Section 'Tus Negocios' missing.");
      await expectVisibleText(page, [/seccion legal/i], "Section 'Seccion Legal' missing.");

      const adminViewShot = await screenshot(page, "administrar-negocios-view", true);
      return {
        details: "Administrar Negocios page shows all expected sections.",
        evidence: [adminViewShot],
      };
    });

    await runStep("Informacion General", async () => {
      const bodyText = await page.locator("body").innerText();
      const normalized = normalizeWhitespace(bodyText);

      const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(normalized);
      if (!hasEmail) {
        throw new Error("User email was not found on the account page.");
      }

      const hasBusinessPlan = /business\s*plan/i.test(normalized);
      if (!hasBusinessPlan) {
        throw new Error("Text 'BUSINESS PLAN' is not visible.");
      }

      const hasChangePlan = /cambiar\s*plan/i.test(normalized);
      if (!hasChangePlan) {
        throw new Error("Button 'Cambiar Plan' is not visible.");
      }

      const lines = bodyText
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);
      const knownLabels = new Set([
        "Informacion General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Seccion Legal",
        "BUSINESS PLAN",
        "Cambiar Plan",
      ]);
      const possibleUserName = lines.find(
        (line) =>
          line.length >= 3 &&
          !line.includes("@") &&
          !knownLabels.has(line) &&
          /^[A-Za-z][A-Za-z .'-]{2,}$/.test(line),
      );

      if (!possibleUserName) {
        throw new Error("User name could not be confidently identified on the page.");
      }

      return {
        details: "User name/email, BUSINESS PLAN text, and Cambiar Plan are visible.",
      };
    });

    await runStep("Detalles de la Cuenta", async () => {
      await expectVisibleText(page, [/cuenta creada/i], "'Cuenta creada' is not visible.");
      await expectVisibleText(page, [/estado activo/i], "'Estado activo' is not visible.");
      await expectVisibleText(
        page,
        [/idioma seleccionado/i],
        "'Idioma seleccionado' is not visible.",
      );
      return { details: "Detalles de la Cuenta section validated." };
    });

    await runStep("Tus Negocios", async () => {
      await expectVisibleText(page, [/tus negocios/i], "Section title 'Tus Negocios' is not visible.");
      await expectVisibleText(page, [/agregar negocio/i], "Button 'Agregar Negocio' is missing.");
      await expectVisibleText(
        page,
        [/tienes\s*2\s*de\s*3\s*negocios/i],
        "Text 'Tienes 2 de 3 negocios' is not visible.",
      );

      const businessListVisible =
        (await page.locator("ul li, [role='listitem']").first().isVisible().catch(() => false)) ||
        (await page
          .locator("div, section")
          .filter({ hasText: /negocio/i })
          .nth(1)
          .isVisible()
          .catch(() => false));
      if (!businessListVisible) {
        throw new Error("Business list is not visible in 'Tus Negocios'.");
      }

      return { details: "Tus Negocios section validated." };
    });

    await runStep("Terminos y Condiciones", async () => {
      const appUrl = page.url();
      const legalResult = await validateLegalPage({
        appPage: page,
        context,
        linkPatterns: [/terminos y condiciones/i, /terminos/i],
        headingPatterns: [/terminos y condiciones/i, /terminos/i],
        screenshotLabel: "terminos-y-condiciones",
        fallbackAppUrl: appUrl,
      });

      return {
        details: `Terms page validated at URL: ${legalResult.finalUrl}`,
        evidence: [legalResult.screenshotPath, legalResult.finalUrl],
      };
    });

    await runStep("Politica de Privacidad", async () => {
      const appUrl = page.url();
      const legalResult = await validateLegalPage({
        appPage: page,
        context,
        linkPatterns: [/politica de privacidad/i, /privacidad/i],
        headingPatterns: [/politica de privacidad/i, /privacidad/i],
        screenshotLabel: "politica-de-privacidad",
        fallbackAppUrl: appUrl,
      });

      return {
        details: `Privacy page validated at URL: ${legalResult.finalUrl}`,
        evidence: [legalResult.screenshotPath, legalResult.finalUrl],
      };
    });
  } catch (error) {
    report.runError = error instanceof Error ? error.message : String(error);
  } finally {
    if (browser) {
      await browser.close();
    }
  }

  report.finishedAt = new Date().toISOString();
  report.results = results;
  const failedCount = Object.values(results).filter((item) => item.status === "FAIL").length;
  report.summary = {
    total: reportFields.length,
    passed: reportFields.length - failedCount,
    failed: failedCount,
  };
  report.status = failedCount > 0 ? "FAIL" : "PASS";

  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  const printableSummary = reportFields.map((field) => ({
    field,
    status: results[field].status,
    details: results[field].details,
  }));

  console.table(printableSummary);
  console.log(`\nReport written to: ${reportPath}`);
  console.log(`Artifacts directory: ${artifactsDir}`);

  if (report.status === "FAIL") {
    process.exitCode = 1;
  }
};

await run();
