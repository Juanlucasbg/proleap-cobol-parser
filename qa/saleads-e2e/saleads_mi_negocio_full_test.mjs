import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT = process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL ?? "";
const HEADLESS = process.env.HEADLESS !== "false";
const SLOW_MO = Number(process.env.PW_SLOW_MO ?? 0);
const NOW = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACTS_DIR = path.resolve("artifacts", `${TEST_NAME}-${NOW}`);
const REPORT_PATH = path.join(ARTIFACTS_DIR, "report.json");

const report = {
  name: TEST_NAME,
  startedAt: new Date().toISOString(),
  config: {
    loginUrlProvided: Boolean(LOGIN_URL),
    headless: HEADLESS,
    slowMo: SLOW_MO,
    googleAccount: GOOGLE_ACCOUNT
  },
  evidence: [],
  stepResults: {},
  legalUrls: {
    terminosYCondiciones: null,
    politicaDePrivacidad: null
  }
};

const orderedStepNames = [
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

function setStepResult(stepName, status, details) {
  report.stepResults[stepName] = { status, details };
}

function markBlockedStepsAfterLogin() {
  for (const stepName of orderedStepNames.slice(1)) {
    if (!report.stepResults[stepName]) {
      setStepResult(stepName, "FAIL", "Blocked because Login step did not complete.");
    }
  }
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function resolveFirstVisible(candidates, timeoutMs, description) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const located = candidate.first();
      const count = await candidate.count().catch(() => 0);
      if (count > 0 && (await located.isVisible().catch(() => false))) {
        return located;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(`Could not find visible element for: ${description}`);
}

async function expectVisible(candidates, timeoutMs, description) {
  const located = await resolveFirstVisible(candidates, timeoutMs, description);
  await located.waitFor({ state: "visible", timeout: timeoutMs });
  return located;
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function screenshot(page, fileName, fullPage = false) {
  const outputPath = path.join(ARTIFACTS_DIR, fileName);
  await page.screenshot({ path: outputPath, fullPage });
  report.evidence.push(outputPath);
}

async function validateLegalPage({
  page,
  context,
  linkText,
  headingPattern,
  screenshotName,
  urlKey
}) {
  const link = await expectVisible(
    [page.getByRole("link", { name: new RegExp(linkText, "i") }), page.getByText(new RegExp(linkText, "i"))],
    20000,
    linkText
  );

  const previousUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
  const navPromise = page.waitForNavigation({ timeout: 12000 }).catch(() => null);

  await link.click();
  await waitForUi(page);

  const popup = await popupPromise;
  await navPromise.catch(() => {});

  let targetPage = page;
  if (popup) {
    targetPage = popup;
    await waitForUi(targetPage);
  }

  await expectVisible(
    [targetPage.getByRole("heading", { name: headingPattern }), targetPage.getByText(headingPattern)],
    20000,
    `Heading ${headingPattern}`
  );

  const pageBody = await targetPage.locator("body").innerText().catch(() => "");
  if (pageBody.trim().length < 120) {
    throw new Error(`Legal content appears too short for ${linkText}.`);
  }

  report.legalUrls[urlKey] = targetPage.url();
  await screenshot(targetPage, screenshotName, true);

  if (popup) {
    await targetPage.close().catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== previousUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }
}

async function writeReportAndExit() {
  const summary = {};
  for (const stepName of orderedStepNames) {
    summary[stepName] = report.stepResults[stepName]?.status ?? "FAIL";
  }

  report.summary = summary;
  report.finishedAt = new Date().toISOString();
  report.overallStatus = Object.values(summary).every((value) => value === "PASS") ? "PASS" : "FAIL";

  await fs.writeFile(REPORT_PATH, JSON.stringify(report, null, 2), "utf-8");
  console.log(JSON.stringify(report, null, 2));

  process.exitCode = report.overallStatus === "PASS" ? 0 : 1;
}

async function run() {
  await ensureDir(ARTIFACTS_DIR);

  const browser = await chromium.launch({ headless: HEADLESS, slowMo: SLOW_MO });
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await context.newPage();

  let loginSucceeded = false;

  try {
    if (!LOGIN_URL) {
      throw new Error(
        "Set SALEADS_LOGIN_URL to the current environment login page. The test is URL-agnostic and does not hard-code a domain."
      );
    }

    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginButton = await expectVisible(
      [
        page.getByRole("button", { name: /sign in with google|continue with google|continuar con google/i }),
        page.getByRole("link", { name: /sign in with google|continue with google|continuar con google/i }),
        page.getByText(/sign in with google|continue with google|continuar con google/i)
      ],
      30000,
      "Sign in with Google button"
    );

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const googlePage = (await popupPromise) ?? page;

    await waitForUi(googlePage);

    const accountChoice = await resolveFirstVisible(
      [googlePage.getByText(GOOGLE_ACCOUNT, { exact: true }), googlePage.getByText(new RegExp(GOOGLE_ACCOUNT, "i"))],
      12000,
      `Google account ${GOOGLE_ACCOUNT}`
    ).catch(() => null);

    if (accountChoice) {
      await clickAndWait(googlePage, accountChoice);
    }

    if (googlePage !== page) {
      await googlePage.waitForEvent("close", { timeout: 70000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUi(page);

    await expectVisible([page.locator("aside"), page.locator("[role='navigation']"), page.locator("nav")], 30000, "Left sidebar");
    await expectVisible([page.getByText(/mi negocio/i), page.getByText(/negocio/i)], 30000, "Main application sidebar content");

    await screenshot(page, "01-dashboard-loaded.png", true);
    setStepResult("Login", "PASS", "Main app and left sidebar are visible after Google login.");
    loginSucceeded = true;
  } catch (error) {
    setStepResult("Login", "FAIL", error instanceof Error ? error.message : String(error));
    await screenshot(page, "01-login-failed.png", true).catch(() => {});
  }

  if (!loginSucceeded) {
    markBlockedStepsAfterLogin();
    await context.close();
    await browser.close();
    await writeReportAndExit();
    return;
  }

  try {
    const negocioSection = await expectVisible(
      [page.getByRole("button", { name: /negocio/i }), page.getByText(/negocio/i)],
      20000,
      "Negocio section"
    );
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await expectVisible(
      [page.getByRole("button", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
      20000,
      "Mi Negocio option"
    );
    await clickAndWait(page, miNegocioOption);

    await expectVisible([page.getByText(/agregar negocio/i)], 15000, "Agregar Negocio");
    await expectVisible([page.getByText(/administrar negocios/i)], 15000, "Administrar Negocios");

    await screenshot(page, "02-mi-negocio-expanded-menu.png", true);
    setStepResult("Mi Negocio menu", "PASS", "Mi Negocio submenu expanded with both required options.");
  } catch (error) {
    setStepResult("Mi Negocio menu", "FAIL", error instanceof Error ? error.message : String(error));
  }

  try {
    const agregarNegocio = await expectVisible([page.getByText(/agregar negocio/i)], 20000, "Agregar Negocio menu item");
    await clickAndWait(page, agregarNegocio);

    await expectVisible([page.getByText(/crear nuevo negocio/i)], 20000, "Crear Nuevo Negocio title");
    const nombreInput = await expectVisible(
      [page.getByLabel(/nombre del negocio/i), page.getByPlaceholder(/nombre del negocio/i), page.locator("input")],
      20000,
      "Nombre del Negocio input"
    );
    await expectVisible([page.getByText(/tienes 2 de 3 negocios/i)], 15000, "Tienes 2 de 3 negocios");
    await expectVisible([page.getByRole("button", { name: /cancelar/i })], 15000, "Cancelar button");
    await expectVisible([page.getByRole("button", { name: /crear negocio/i })], 15000, "Crear Negocio button");

    await screenshot(page, "03-agregar-negocio-modal.png", true);

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    const cancelButton = await expectVisible([page.getByRole("button", { name: /cancelar/i })], 15000, "Cancelar button");
    await clickAndWait(page, cancelButton);

    setStepResult("Agregar Negocio modal", "PASS", "Modal validations passed and optional input/cancel actions executed.");
  } catch (error) {
    setStepResult("Agregar Negocio modal", "FAIL", error instanceof Error ? error.message : String(error));
  }

  try {
    const miNegocioOption = await expectVisible(
      [page.getByRole("button", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
      20000,
      "Mi Negocio option (re-open if collapsed)"
    );
    await clickAndWait(page, miNegocioOption);

    const administrarNegocios = await expectVisible(
      [page.getByText(/administrar negocios/i), page.getByRole("button", { name: /administrar negocios/i })],
      20000,
      "Administrar Negocios"
    );
    await clickAndWait(page, administrarNegocios);

    await expectVisible([page.getByText(/informaci[oó]n general/i)], 20000, "Información General section");
    await expectVisible([page.getByText(/detalles de la cuenta/i)], 20000, "Detalles de la Cuenta section");
    await expectVisible([page.getByText(/tus negocios/i)], 20000, "Tus Negocios section");
    await expectVisible([page.getByText(/secci[oó]n legal/i)], 20000, "Sección Legal section");

    await screenshot(page, "04-administrar-negocios-account-page.png", true);
    setStepResult("Administrar Negocios view", "PASS", "Account page loaded with all required sections.");
  } catch (error) {
    setStepResult("Administrar Negocios view", "FAIL", error instanceof Error ? error.message : String(error));
  }

  try {
    await expectVisible([page.getByText(/business plan/i)], 20000, "BUSINESS PLAN");
    await expectVisible([page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)], 20000, "Cambiar Plan");

    const bodyText = await page.locator("body").innerText().catch(() => "");
    if (!bodyText.match(/@/)) {
      throw new Error("Could not detect user email in visible page text.");
    }

    setStepResult("Información General", "PASS", "User details, BUSINESS PLAN, and Cambiar Plan are visible.");
  } catch (error) {
    setStepResult("Información General", "FAIL", error instanceof Error ? error.message : String(error));
  }

  try {
    await expectVisible([page.getByText(/cuenta creada/i)], 15000, "Cuenta creada");
    await expectVisible([page.getByText(/estado activo/i), page.getByText(/activo/i)], 15000, "Estado activo");
    await expectVisible([page.getByText(/idioma seleccionado/i), page.getByText(/idioma/i)], 15000, "Idioma seleccionado");
    setStepResult("Detalles de la Cuenta", "PASS", "All required account detail fields are visible.");
  } catch (error) {
    setStepResult("Detalles de la Cuenta", "FAIL", error instanceof Error ? error.message : String(error));
  }

  try {
    await expectVisible([page.getByText(/tus negocios/i)], 15000, "Tus Negocios section");
    await expectVisible(
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      15000,
      "Agregar Negocio button in business section"
    );
    await expectVisible([page.getByText(/tienes 2 de 3 negocios/i)], 15000, "Tienes 2 de 3 negocios in business section");
    setStepResult("Tus Negocios", "PASS", "Business list and limits are visible.");
  } catch (error) {
    setStepResult("Tus Negocios", "FAIL", error instanceof Error ? error.message : String(error));
  }

  try {
    await validateLegalPage({
      page,
      context,
      linkText: "Términos y Condiciones",
      headingPattern: /t[eé]rminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      urlKey: "terminosYCondiciones"
    });
    setStepResult("Términos y Condiciones", "PASS", "Legal page heading/content validated and URL captured.");
  } catch (error) {
    setStepResult("Términos y Condiciones", "FAIL", error instanceof Error ? error.message : String(error));
  }

  try {
    await validateLegalPage({
      page,
      context,
      linkText: "Política de Privacidad",
      headingPattern: /pol[ií]tica de privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      urlKey: "politicaDePrivacidad"
    });
    setStepResult("Política de Privacidad", "PASS", "Legal page heading/content validated and URL captured.");
  } catch (error) {
    setStepResult("Política de Privacidad", "FAIL", error instanceof Error ? error.message : String(error));
  }

  await context.close();
  await browser.close();
  await writeReportAndExit();
}

run().catch(async (error) => {
  setStepResult("Login", "FAIL", `Unexpected script error: ${error instanceof Error ? error.message : String(error)}`);
  markBlockedStepsAfterLogin();
  await writeReportAndExit();
});
