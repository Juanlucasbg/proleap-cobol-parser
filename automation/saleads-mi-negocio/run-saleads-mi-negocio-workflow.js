#!/usr/bin/env node

const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const WORKFLOW_NAME = "saleads_mi_negocio_full_test";
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
  "Política de Privacidad",
];

const normalize = (value) => value.trim().toLowerCase().replace(/\s+/g, " ");

function safeTimestamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function toErrorString(error) {
  if (!error) {
    return "Unknown error";
  }

  if (typeof error === "string") {
    return error;
  }

  if (error && typeof error.message === "string") {
    return error.message;
  }

  return String(error);
}

async function waitForUi(page, timeoutMs = 20000) {
  await page.waitForLoadState("domcontentloaded", { timeout: timeoutMs });
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {
    // networkidle is not always reached on highly dynamic SPAs.
  });
  await page.waitForTimeout(500);
}

async function hasVisible(locator) {
  const count = await locator.count();
  if (!count) {
    return false;
  }
  return locator.first().isVisible();
}

async function mustFindVisible(candidates, errorMessage) {
  for (const candidate of candidates) {
    if (await hasVisible(candidate)) {
      return candidate.first();
    }
  }
  throw new Error(errorMessage);
}

async function main() {
  const baseUrl = process.env.SALEADS_LOGIN_URL;
  const googleAccountEmail = process.env.GOOGLE_ACCOUNT_EMAIL || DEFAULT_GOOGLE_ACCOUNT;
  const headless = process.env.HEADLESS === "true";
  const slowMo = Number.parseInt(process.env.SLOW_MO_MS || "0", 10);
  const actionTimeout = Number.parseInt(process.env.ACTION_TIMEOUT_MS || "35000", 10);
  const outputDir =
    process.env.OUTPUT_DIR ||
    path.join(process.cwd(), "artifacts", "saleads-mi-negocio", safeTimestamp());

  if (!baseUrl) {
    throw new Error(
      "SALEADS_LOGIN_URL is required. Use the current environment login URL so this run remains environment-agnostic."
    );
  }

  await fs.mkdir(outputDir, { recursive: true });

  const report = {
    name: WORKFLOW_NAME,
    startedAt: new Date().toISOString(),
    environment: {
      saleadsLoginUrl: baseUrl,
      headless,
      googleAccountEmail,
    },
    artifactsDirectory: outputDir,
    steps: {},
    evidence: {
      screenshots: [],
      urls: {},
    },
  };

  let screenshotCounter = 0;
  let browser;
  let context;
  let page;

  const capture = async (targetPage, fileStem, fullPage = false) => {
    screenshotCounter += 1;
    const fileName = `${String(screenshotCounter).padStart(2, "0")}-${fileStem}.png`;
    const filePath = path.join(outputDir, fileName);
    await targetPage.screenshot({ path: filePath, fullPage });
    report.evidence.screenshots.push(filePath);
    return filePath;
  };

  const step = async (field, executor) => {
    const stepResult = {
      status: "FAIL",
      checks: [],
      error: null,
      startedAt: new Date().toISOString(),
      finishedAt: null,
    };

    const passCheck = (message) => stepResult.checks.push({ status: "PASS", message });
    const failCheck = (message) => stepResult.checks.push({ status: "FAIL", message });

    try {
      await executor({ passCheck, failCheck });
      stepResult.status = stepResult.checks.some((check) => check.status === "FAIL")
        ? "FAIL"
        : "PASS";
    } catch (error) {
      stepResult.error = toErrorString(error);
      stepResult.status = "FAIL";
      failCheck(stepResult.error);
      if (page) {
        const stem = `failure-${field.replace(/\s+/g, "-").toLowerCase()}`;
        await capture(page, stem, true).catch(() => {
          // Keep original error as primary failure signal.
        });
      }
    }

    stepResult.finishedAt = new Date().toISOString();
    report.steps[field] = stepResult;
  };

  const requireTextVisible = async (targetPage, textRegex, failureMessage) => {
    const textNode = targetPage.getByText(textRegex).first();
    if (!(await hasVisible(textNode))) {
      throw new Error(failureMessage);
    }
  };

  const openLegalLink = async ({ linkText, headingRegex, reportField, screenshotStem }) => {
    await step(reportField, async ({ passCheck }) => {
      const legalLink = await mustFindVisible(
        [
          page.getByRole("link", { name: new RegExp(linkText, "i") }),
          page.getByRole("button", { name: new RegExp(linkText, "i") }),
          page.getByText(new RegExp(linkText, "i")),
        ],
        `${linkText} action is not visible.`
      );

      const knownPages = new Set(context.pages());
      const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);

      await legalLink.click();
      await page.waitForTimeout(700);

      let legalPage = await popupPromise;
      if (!legalPage) {
        const newPage = context.pages().find((candidate) => !knownPages.has(candidate));
        legalPage = newPage || page;
      }

      await waitForUi(legalPage, 40000);

      const heading = legalPage.getByRole("heading", { name: headingRegex }).first();
      const hasHeading = (await hasVisible(heading)) || (await hasVisible(legalPage.getByText(headingRegex)));
      if (!hasHeading) {
        throw new Error(`${linkText} page does not show expected heading.`);
      }

      const bodyText = (await legalPage.locator("body").innerText()).trim();
      if (bodyText.length < 150) {
        throw new Error(`${linkText} page does not show enough legal content.`);
      }

      await capture(legalPage, screenshotStem, true);
      report.evidence.urls[linkText] = legalPage.url();
      passCheck(`${linkText} content is visible at ${legalPage.url()}`);

      if (legalPage !== page) {
        await legalPage.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {
          // If no history entry exists, keep current page.
        });
      }

      await waitForUi(page, 25000);
    });
  };

  try {
    browser = await chromium.launch({ headless, slowMo });
    context = await browser.newContext();
    page = await context.newPage();
    page.setDefaultTimeout(actionTimeout);

    await step("Login", async ({ passCheck }) => {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page, 40000);

      const loginButton = await mustFindVisible(
        [
          page.getByRole("button", { name: /sign in with google|iniciar sesión con google|continuar con google|google/i }),
          page.getByRole("link", { name: /sign in with google|iniciar sesión con google|continuar con google|google/i }),
          page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
        ],
        "Google login button is not visible."
      );

      const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
      await loginButton.click();

      const authPage = (await popupPromise) || page;
      await waitForUi(authPage, 40000);

      const accountOption = authPage.getByText(googleAccountEmail, { exact: true }).first();
      if (await hasVisible(accountOption)) {
        await accountOption.click();
        passCheck(`Selected Google account ${googleAccountEmail}.`);
      } else {
        passCheck("Google account chooser did not appear or account was pre-selected.");
      }

      if (authPage !== page) {
        await authPage.waitForClose({ timeout: 60000 }).catch(() => {
          // Some auth flows keep the popup open while redirect happens.
        });
      }

      await waitForUi(page, 60000);

      const sidebar = await mustFindVisible(
        [
          page.locator("aside").filter({ hasText: /negocio|mi negocio|dashboard|inicio/i }),
          page.locator("nav").filter({ hasText: /negocio|mi negocio|dashboard|inicio/i }),
          page.getByText(/mi negocio|negocio/i),
        ],
        "Main application interface did not load after login."
      );

      if (!(await hasVisible(sidebar))) {
        throw new Error("Left sidebar navigation is not visible.");
      }

      await capture(page, "01-dashboard-loaded", true);
      passCheck("Dashboard loaded and left sidebar is visible.");
    });

    await step("Mi Negocio menu", async ({ passCheck }) => {
      const miNegocio = await mustFindVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/^mi negocio$/i),
          page.getByText(/mi negocio/i),
        ],
        "'Mi Negocio' option is not visible in sidebar."
      );

      await miNegocio.click();
      await waitForUi(page, 25000);

      await requireTextVisible(page, /agregar negocio/i, "'Agregar Negocio' submenu entry is not visible.");
      await requireTextVisible(
        page,
        /administrar negocios/i,
        "'Administrar Negocios' submenu entry is not visible."
      );

      await capture(page, "02-mi-negocio-expanded", true);
      passCheck("Mi Negocio submenu expanded with both expected options.");
    });

    await step("Agregar Negocio modal", async ({ passCheck }) => {
      const agregarNegocio = await mustFindVisible(
        [
          page.getByRole("button", { name: /^agregar negocio$/i }),
          page.getByRole("link", { name: /^agregar negocio$/i }),
          page.getByText(/^agregar negocio$/i),
        ],
        "'Agregar Negocio' action is not visible."
      );

      await agregarNegocio.click();
      await waitForUi(page, 25000);

      const modal = await mustFindVisible(
        [
          page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }),
          page.locator("[role='dialog']").filter({ hasText: /crear nuevo negocio/i }),
          page.locator("div").filter({ hasText: /crear nuevo negocio/i }),
        ],
        "Crear Nuevo Negocio modal did not appear."
      );

      if (!(await hasVisible(modal.getByText(/crear nuevo negocio/i)))) {
        throw new Error("Modal title 'Crear Nuevo Negocio' is not visible.");
      }

      const businessNameInput = await mustFindVisible(
        [
          modal.getByLabel(/nombre del negocio/i),
          modal.getByPlaceholder(/nombre del negocio/i),
          modal.locator("input").first(),
        ],
        "Input field 'Nombre del Negocio' is missing in modal."
      );

      if (!(await hasVisible(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)))) {
        throw new Error("Text 'Tienes 2 de 3 negocios' is missing in modal.");
      }

      if (!(await hasVisible(modal.getByRole("button", { name: /cancelar/i })))) {
        throw new Error("Button 'Cancelar' is missing in modal.");
      }

      if (!(await hasVisible(modal.getByRole("button", { name: /crear negocio/i })))) {
        throw new Error("Button 'Crear Negocio' is missing in modal.");
      }

      await capture(page, "03-agregar-negocio-modal", true);

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatizacion");
      const cancelButton = await mustFindVisible(
        [modal.getByRole("button", { name: /cancelar/i }), modal.getByText(/cancelar/i)],
        "Could not find 'Cancelar' in modal."
      );
      await cancelButton.click();
      await waitForUi(page, 25000);
      passCheck("Agregar Negocio modal validated and dismissed.");
    });

    await step("Administrar Negocios view", async ({ passCheck }) => {
      const administrarNegociosVisible = await hasVisible(page.getByText(/administrar negocios/i).first());
      if (!administrarNegociosVisible) {
        const miNegocioToggle = await mustFindVisible(
          [
            page.getByRole("button", { name: /mi negocio/i }),
            page.getByRole("link", { name: /mi negocio/i }),
            page.getByText(/mi negocio/i),
          ],
          "Could not re-open Mi Negocio menu."
        );
        await miNegocioToggle.click();
        await waitForUi(page, 25000);
      }

      const administrarNegocios = await mustFindVisible(
        [
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ],
        "'Administrar Negocios' option is not visible."
      );

      await administrarNegocios.click();
      await waitForUi(page, 45000);

      await requireTextVisible(page, /información general|informacion general/i, "Missing 'Información General' section.");
      await requireTextVisible(page, /detalles de la cuenta/i, "Missing 'Detalles de la Cuenta' section.");
      await requireTextVisible(page, /tus negocios/i, "Missing 'Tus Negocios' section.");
      await requireTextVisible(page, /sección legal|seccion legal/i, "Missing 'Sección Legal' section.");

      await capture(page, "04-administrar-negocios-view", true);
      passCheck("Administrar Negocios account page loaded with all main sections.");
    });

    await step("Información General", async ({ passCheck, failCheck }) => {
      await requireTextVisible(page, /información general|informacion general/i, "No se encontró 'Información General'.");

      const pageText = normalize(await page.locator("body").innerText());
      const userNameVisible = /nombre/.test(pageText) || /perfil/.test(pageText);
      if (!userNameVisible) {
        failCheck("Could not confidently identify user name indicator text.");
      } else {
        passCheck("User name indicator is visible.");
      }

      const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(await page.locator("body").innerText());
      if (!hasEmail) {
        failCheck("User email is not visible.");
      } else {
        passCheck("User email is visible.");
      }

      await requireTextVisible(page, /business plan/i, "Text 'BUSINESS PLAN' is not visible.");
      passCheck("Text 'BUSINESS PLAN' is visible.");

      await requireTextVisible(page, /cambiar plan/i, "Button 'Cambiar Plan' is not visible.");
      passCheck("Button 'Cambiar Plan' is visible.");
    });

    await step("Detalles de la Cuenta", async ({ passCheck }) => {
      await requireTextVisible(page, /detalles de la cuenta/i, "No se encontró 'Detalles de la Cuenta'.");
      await requireTextVisible(page, /cuenta creada/i, "Text 'Cuenta creada' is not visible.");
      await requireTextVisible(page, /estado activo/i, "Text 'Estado activo' is not visible.");
      await requireTextVisible(page, /idioma seleccionado/i, "Text 'Idioma seleccionado' is not visible.");
      passCheck("Detalles de la Cuenta section has all expected labels.");
    });

    await step("Tus Negocios", async ({ passCheck, failCheck }) => {
      await requireTextVisible(page, /tus negocios/i, "No se encontró 'Tus Negocios'.");
      await requireTextVisible(page, /agregar negocio/i, "Button 'Agregar Negocio' not found in Tus Negocios.");
      await requireTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i, "Quota text not found in Tus Negocios.");

      const businessItems = page.locator("li, [role='listitem'], table tbody tr, .card, .business-item");
      const itemCount = await businessItems.count();
      if (itemCount < 1) {
        failCheck("Business list could not be confirmed from common list/table/card patterns.");
      } else {
        passCheck(`Business list is visible with at least ${itemCount} item(s).`);
      }
    });

    await openLegalLink({
      linkText: "Términos y Condiciones",
      headingRegex: /términos y condiciones|terminos y condiciones/i,
      reportField: "Términos y Condiciones",
      screenshotStem: "05-terminos-y-condiciones",
    });

    await openLegalLink({
      linkText: "Política de Privacidad",
      headingRegex: /política de privacidad|politica de privacidad/i,
      reportField: "Política de Privacidad",
      screenshotStem: "06-politica-de-privacidad",
    });
  } finally {
    report.finishedAt = new Date().toISOString();
    if (browser) {
      await browser.close();
    }

    const summary = {};
    for (const field of REPORT_FIELDS) {
      summary[field] = report.steps[field]?.status || "FAIL";
    }

    report.summary = summary;

    const reportJsonPath = path.join(outputDir, "report.json");
    const reportMdPath = path.join(outputDir, "report.md");

    const markdownLines = [
      `# ${WORKFLOW_NAME}`,
      "",
      `- Started at: ${report.startedAt}`,
      `- Finished at: ${report.finishedAt}`,
      `- Login URL used: ${report.environment.saleadsLoginUrl}`,
      "",
      "## Final Report (PASS/FAIL)",
      "",
      "| Validation Step | Result |",
      "| --- | --- |",
      ...REPORT_FIELDS.map((field) => `| ${field} | ${report.summary[field]} |`),
      "",
      "## Legal URLs",
      "",
      `- Términos y Condiciones: ${report.evidence.urls["Términos y Condiciones"] || "N/A"}`,
      `- Política de Privacidad: ${report.evidence.urls["Política de Privacidad"] || "N/A"}`,
      "",
      "## Screenshots",
      "",
      ...report.evidence.screenshots.map((screenshotPath) => `- ${screenshotPath}`),
      "",
    ];

    await fs.writeFile(reportJsonPath, JSON.stringify(report, null, 2), "utf8");
    await fs.writeFile(reportMdPath, markdownLines.join("\n"), "utf8");

    const failed = REPORT_FIELDS.filter((field) => report.summary[field] !== "PASS");
    if (failed.length) {
      console.error(`Workflow completed with failures in: ${failed.join(", ")}`);
      process.exitCode = 1;
    } else {
      console.log("Workflow completed successfully.");
      process.exitCode = 0;
    }
  }
}

main().catch((error) => {
  console.error(toErrorString(error));
  process.exit(1);
});
