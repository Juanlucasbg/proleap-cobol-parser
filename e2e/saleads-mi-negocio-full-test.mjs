import { chromium } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const APP_LOGIN_URL = process.env.SALEADS_LOGIN_URL;
const GOOGLE_ACCOUNT_EMAIL = process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const HEADLESS = process.env.HEADLESS !== "false";
const ACTION_TIMEOUT_MS = Number(process.env.SALEADS_ACTION_TIMEOUT_MS ?? 15000);
const NAV_TIMEOUT_MS = Number(process.env.SALEADS_NAV_TIMEOUT_MS ?? 30000);

const runId = new Date().toISOString().replaceAll(":", "-").replaceAll(".", "-");
const artifactsDir = path.join(__dirname, "artifacts", `saleads-mi-negocio-${runId}`);

const reportFields = [
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

const finalReport = {
  name: "saleads_mi_negocio_full_test",
  startedAt: new Date().toISOString(),
  statusByField: Object.fromEntries(reportFields.map((field) => [field, "FAIL"])),
  detailsByField: Object.fromEntries(reportFields.map((field) => [field, "Not executed."])),
  evidence: {
    screenshots: [],
    finalUrls: {},
  },
  runtime: {
    loginUrlProvided: Boolean(APP_LOGIN_URL),
    headless: HEADLESS,
    actionTimeoutMs: ACTION_TIMEOUT_MS,
    navigationTimeoutMs: NAV_TIMEOUT_MS,
  },
};

const sectionNames = [
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Sección Legal",
];

const state = {
  browser: null,
  context: null,
  page: null,
  appPageUrlBeforeLegal: null,
};

async function ensureDir(targetDir) {
  await fs.mkdir(targetDir, { recursive: true });
}

async function writeFinalReport() {
  finalReport.finishedAt = new Date().toISOString();
  const allPassed = Object.values(finalReport.statusByField).every((status) => status === "PASS");
  finalReport.overallStatus = allPassed ? "PASS" : "FAIL";

  const reportPath = path.join(artifactsDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

  console.log(`\nFinal report written to: ${reportPath}`);
  console.table(
    reportFields.map((field) => ({
      field,
      status: finalReport.statusByField[field],
      detail: finalReport.detailsByField[field],
    })),
  );
  return { reportPath, allPassed };
}

function setFieldResult(field, passed, detail) {
  finalReport.statusByField[field] = passed ? "PASS" : "FAIL";
  finalReport.detailsByField[field] = detail;
}

function setPrerequisiteFailure(field, prerequisiteField) {
  setFieldResult(field, false, `Prerequisite failed: ${prerequisiteField}.`);
}

async function safeWaitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: NAV_TIMEOUT_MS }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function captureScreenshot(page, label, fullPage = false) {
  const fileName = `${String(finalReport.evidence.screenshots.length + 1).padStart(2, "0")}-${label
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/gi, "-")
    .replaceAll(/(^-|-$)/g, "")}.png`;
  const screenshotPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  finalReport.evidence.screenshots.push({
    label,
    path: screenshotPath,
    pageUrl: page.url(),
  });
}

async function findVisibleLocator(candidates, timeoutMs = 3000) {
  for (const locator of candidates) {
    try {
      await locator.first().waitFor({ state: "visible", timeout: timeoutMs });
      return locator.first();
    } catch {
      // continue trying next candidate
    }
  }
  return null;
}

async function clickAndWait(page, locator) {
  await locator.click({ timeout: ACTION_TIMEOUT_MS });
  await safeWaitForUi(page);
}

async function loginWithGoogle() {
  const field = "Login";
  const page = state.page;
  if (!APP_LOGIN_URL) {
    setFieldResult(
      field,
      false,
      "Missing SALEADS_LOGIN_URL. Set SALEADS_LOGIN_URL to the current environment login page.",
    );
    return false;
  }

  await page.goto(APP_LOGIN_URL, { timeout: NAV_TIMEOUT_MS, waitUntil: "domcontentloaded" });
  await safeWaitForUi(page);

  const sidebarAlreadyVisible = await findVisibleLocator(
    [
      page.getByRole("navigation"),
      page.locator("aside"),
      page.getByText("Mi Negocio", { exact: false }),
    ],
    2500,
  );

  if (sidebarAlreadyVisible) {
    await captureScreenshot(page, "dashboard-loaded");
    setFieldResult(field, true, "Session already authenticated; dashboard and sidebar are visible.");
    return true;
  }

  const googleButton = await findVisibleLocator(
    [
      page.getByRole("button", { name: /sign in with google|iniciar sesión con google|continuar con google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesión con google|continuar con google/i }),
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
    ],
    7000,
  );

  if (!googleButton) {
    setFieldResult(field, false, "Google login button was not found on the login page.");
    return false;
  }

  const popupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
  await clickAndWait(page, googleButton);
  const popupPage = await popupPromise;

  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded", { timeout: NAV_TIMEOUT_MS }).catch(() => {});
    const accountCandidate = await findVisibleLocator(
      [
        popupPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        popupPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        popupPage.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      ],
      6000,
    );
    if (accountCandidate) {
      await accountCandidate.click({ timeout: ACTION_TIMEOUT_MS });
    }
    await safeWaitForUi(page);
  } else {
    const accountCandidate = await findVisibleLocator(
      [
        page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        page.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      ],
      5000,
    );
    if (accountCandidate) {
      await clickAndWait(page, accountCandidate);
    }
  }

  const appVisible = await findVisibleLocator(
    [
      page.getByRole("navigation"),
      page.locator("aside"),
      page.getByText("Mi Negocio", { exact: false }),
      page.getByText("Negocio", { exact: false }),
    ],
    20000,
  );

  if (!appVisible) {
    setFieldResult(field, false, "Dashboard/sidebar did not appear after Google login.");
    return false;
  }

  await captureScreenshot(page, "dashboard-loaded");
  setFieldResult(field, true, "Dashboard and left sidebar are visible after login.");
  return true;
}

async function openMiNegocioMenu() {
  const field = "Mi Negocio menu";
  const page = state.page;

  const negocioSection = await findVisibleLocator(
    [
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^Negocio$/, { exact: true }),
    ],
    8000,
  );
  if (!negocioSection) {
    setFieldResult(field, false, "Sidebar item 'Negocio' not found.");
    return false;
  }
  await clickAndWait(page, negocioSection);

  const miNegocio = await findVisibleLocator(
    [
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByText(/^Mi Negocio$/, { exact: true }),
    ],
    8000,
  );
  if (!miNegocio) {
    setFieldResult(field, false, "Sidebar option 'Mi Negocio' not found.");
    return false;
  }
  await clickAndWait(page, miNegocio);

  const agregar = await findVisibleLocator(
    [
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^Agregar Negocio$/, { exact: true }),
    ],
    8000,
  );
  const administrar = await findVisibleLocator(
    [
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByText(/^Administrar Negocios$/, { exact: true }),
    ],
    8000,
  );

  if (!agregar || !administrar) {
    setFieldResult(field, false, "Mi Negocio submenu did not expose both required options.");
    return false;
  }

  await captureScreenshot(page, "mi-negocio-expanded");
  setFieldResult(field, true, "Submenu expanded and both options are visible.");
  return true;
}

async function validateAgregarNegocioModal() {
  const field = "Agregar Negocio modal";
  const page = state.page;

  const addButton = await findVisibleLocator(
    [
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^Agregar Negocio$/, { exact: true }),
    ],
    7000,
  );
  if (!addButton) {
    setFieldResult(field, false, "Could not find 'Agregar Negocio' option.");
    return false;
  }

  await clickAndWait(page, addButton);

  const modalTitle = await findVisibleLocator([page.getByRole("heading", { name: /crear nuevo negocio/i })], 9000);
  if (!modalTitle) {
    setFieldResult(field, false, "Modal 'Crear Nuevo Negocio' was not displayed.");
    return false;
  }

  const nameInput = await findVisibleLocator(
    [
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").filter({ hasText: "" }),
    ],
    4000,
  );
  const quotaTextVisible = await findVisibleLocator([page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)], 4000);
  const cancelButton = await findVisibleLocator([page.getByRole("button", { name: /^cancelar$/i })], 4000);
  const createButton = await findVisibleLocator([page.getByRole("button", { name: /^crear negocio$/i })], 4000);

  if (!nameInput || !quotaTextVisible || !cancelButton || !createButton) {
    setFieldResult(
      field,
      false,
      "Modal is visible but one or more required controls are missing (input/quota/cancel/create).",
    );
    return false;
  }

  await nameInput.click({ timeout: ACTION_TIMEOUT_MS });
  await nameInput.fill("Negocio Prueba Automatización", { timeout: ACTION_TIMEOUT_MS });

  await captureScreenshot(page, "agregar-negocio-modal");
  await clickAndWait(page, cancelButton);
  setFieldResult(field, true, "Modal structure validated and closed with Cancelar.");
  return true;
}

async function openAdministrarNegocios() {
  const field = "Administrar Negocios view";
  const page = state.page;

  const administrar = await findVisibleLocator(
    [
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByText(/^Administrar Negocios$/, { exact: true }),
    ],
    3000,
  );

  if (!administrar) {
    const negocio = await findVisibleLocator(
      [
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^Negocio$/, { exact: true }),
      ],
      5000,
    );
    if (negocio) {
      await clickAndWait(page, negocio);
    }
    const miNegocio = await findVisibleLocator(
      [
        page.getByRole("button", { name: /^mi negocio$/i }),
        page.getByRole("link", { name: /^mi negocio$/i }),
        page.getByText(/^Mi Negocio$/, { exact: true }),
      ],
      5000,
    );
    if (miNegocio) {
      await clickAndWait(page, miNegocio);
    }
  }

  const administrarRetry = await findVisibleLocator(
    [
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByText(/^Administrar Negocios$/, { exact: true }),
    ],
    8000,
  );
  if (!administrarRetry) {
    setFieldResult(field, false, "Could not locate 'Administrar Negocios'.");
    return false;
  }

  await clickAndWait(page, administrarRetry);
  state.appPageUrlBeforeLegal = page.url();

  for (const sectionName of sectionNames) {
    const sectionVisible = await findVisibleLocator([page.getByText(new RegExp(`^${sectionName}$`, "i"))], 6000);
    if (!sectionVisible) {
      setFieldResult(field, false, `Missing required section '${sectionName}' in account page.`);
      return false;
    }
  }

  await captureScreenshot(page, "administrar-negocios-view", true);
  setFieldResult(field, true, "Account page and required sections are visible.");
  return true;
}

async function validateInformacionGeneral() {
  const field = "Información General";
  const page = state.page;
  const bodyText = (await page.textContent("body")) ?? "";

  const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(bodyText);
  const hasNameSignal =
    /nombre/i.test(bodyText) ||
    /usuario/i.test(bodyText) ||
    /perfil/i.test(bodyText) ||
    /\b[a-záéíóúñ]+ [a-záéíóúñ]+\b/i.test(bodyText);
  const hasBusinessPlan = /business plan/i.test(bodyText);
  const hasCambiarPlan = await findVisibleLocator([page.getByRole("button", { name: /cambiar plan/i })], 4000);

  if (!hasEmail || !hasNameSignal || !hasBusinessPlan || !hasCambiarPlan) {
    setFieldResult(
      field,
      false,
      "Missing at least one validation in Información General (name/email/business plan/cambiar plan).",
    );
    return false;
  }

  setFieldResult(field, true, "Información General shows user details and plan controls.");
  return true;
}

async function validateDetallesCuenta() {
  const field = "Detalles de la Cuenta";
  const page = state.page;
  const bodyText = ((await page.textContent("body")) ?? "").toLowerCase();

  const hasCuentaCreada = /cuenta creada/.test(bodyText);
  const hasEstadoActivo = /estado[^\n\r]{0,40}activo/.test(bodyText) || /estado activo/.test(bodyText);
  const hasIdioma = /idioma seleccionado/.test(bodyText);

  if (!hasCuentaCreada || !hasEstadoActivo || !hasIdioma) {
    setFieldResult(field, false, "Missing one or more account detail validations.");
    return false;
  }

  setFieldResult(field, true, "Detalles de la Cuenta fields are visible.");
  return true;
}

async function validateTusNegocios() {
  const field = "Tus Negocios";
  const page = state.page;
  const bodyText = (await page.textContent("body")) ?? "";

  const hasSection = /tus negocios/i.test(bodyText);
  const hasAddButton = await findVisibleLocator(
    [
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^Agregar Negocio$/, { exact: true }),
    ],
    4000,
  );
  const hasQuotaText = /tienes\s+2\s+de\s+3\s+negocios/i.test(bodyText);

  if (!hasSection || !hasAddButton || !hasQuotaText) {
    setFieldResult(field, false, "Tus Negocios validations failed (list/button/quota text).");
    return false;
  }

  setFieldResult(field, true, "Tus Negocios section, button, and quota text are visible.");
  return true;
}

async function validateLegalPage({
  reportField,
  triggerText,
  expectedHeading,
  screenshotLabel,
}) {
  const page = state.page;

  const legalTrigger = await findVisibleLocator(
    [
      page.getByRole("link", { name: new RegExp(`^${triggerText}$`, "i") }),
      page.getByRole("button", { name: new RegExp(`^${triggerText}$`, "i") }),
      page.getByText(new RegExp(`^${triggerText}$`, "i")),
    ],
    8000,
  );

  if (!legalTrigger) {
    setFieldResult(reportField, false, `Could not find legal link '${triggerText}'.`);
    return false;
  }

  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  await legalTrigger.click({ timeout: ACTION_TIMEOUT_MS });
  const popup = await popupPromise;

  const legalPage = popup ?? page;
  await safeWaitForUi(legalPage);

  const heading = await findVisibleLocator(
    [
      legalPage.getByRole("heading", { name: new RegExp(expectedHeading, "i") }),
      legalPage.getByText(new RegExp(expectedHeading, "i")),
    ],
    10000,
  );

  const textContent = (await legalPage.textContent("body")) ?? "";
  const hasLegalContent = textContent.replaceAll(/\s+/g, " ").trim().length >= 200;

  finalReport.evidence.finalUrls[reportField] = legalPage.url();
  await captureScreenshot(legalPage, screenshotLabel, true);

  if (!heading || !hasLegalContent) {
    setFieldResult(
      reportField,
      false,
      `Legal page validation failed (heading/content). URL: ${legalPage.url()}`,
    );
  } else {
    setFieldResult(reportField, true, `Validated legal page at URL: ${legalPage.url()}`);
  }

  if (popup) {
    await popup.close({ runBeforeUnload: true }).catch(() => {});
    await page.bringToFront().catch(() => {});
  } else if (state.appPageUrlBeforeLegal) {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: NAV_TIMEOUT_MS }).catch(async () => {
      await page.goto(state.appPageUrlBeforeLegal, { waitUntil: "domcontentloaded", timeout: NAV_TIMEOUT_MS });
    });
    await safeWaitForUi(page);
  }

  return finalReport.statusByField[reportField] === "PASS";
}

async function run() {
  await ensureDir(artifactsDir);

  state.browser = await chromium.launch({ headless: HEADLESS });
  state.context = await state.browser.newContext({
    viewport: { width: 1600, height: 1000 },
  });
  state.context.setDefaultTimeout(ACTION_TIMEOUT_MS);
  state.context.setDefaultNavigationTimeout(NAV_TIMEOUT_MS);
  state.page = await state.context.newPage();

  let loginOk = false;
  let menuOk = false;
  let agregarModalOk = false;
  let administrarOk = false;

  try {
    loginOk = await loginWithGoogle();

    if (!loginOk) {
      for (const field of reportFields) {
        if (field !== "Login") {
          setPrerequisiteFailure(field, "Login");
        }
      }
      return;
    }

    menuOk = await openMiNegocioMenu();
    if (!menuOk) {
      const dependentFields = reportFields.filter((field) => !["Login", "Mi Negocio menu"].includes(field));
      for (const field of dependentFields) {
        setPrerequisiteFailure(field, "Mi Negocio menu");
      }
      return;
    }

    agregarModalOk = await validateAgregarNegocioModal();
    if (!agregarModalOk) {
      setPrerequisiteFailure("Administrar Negocios view", "Agregar Negocio modal");
      setPrerequisiteFailure("Información General", "Agregar Negocio modal");
      setPrerequisiteFailure("Detalles de la Cuenta", "Agregar Negocio modal");
      setPrerequisiteFailure("Tus Negocios", "Agregar Negocio modal");
      setPrerequisiteFailure("Términos y Condiciones", "Agregar Negocio modal");
      setPrerequisiteFailure("Política de Privacidad", "Agregar Negocio modal");
      return;
    }

    administrarOk = await openAdministrarNegocios();
    if (!administrarOk) {
      setPrerequisiteFailure("Información General", "Administrar Negocios view");
      setPrerequisiteFailure("Detalles de la Cuenta", "Administrar Negocios view");
      setPrerequisiteFailure("Tus Negocios", "Administrar Negocios view");
      setPrerequisiteFailure("Términos y Condiciones", "Administrar Negocios view");
      setPrerequisiteFailure("Política de Privacidad", "Administrar Negocios view");
      return;
    }

    const infoOk = await validateInformacionGeneral();
    const detailsOk = await validateDetallesCuenta();
    const negociosOk = await validateTusNegocios();

    if (!infoOk || !detailsOk || !negociosOk) {
      if (finalReport.statusByField["Términos y Condiciones"] === "FAIL") {
        setPrerequisiteFailure("Términos y Condiciones", "Administrar Negocios view");
      }
      if (finalReport.statusByField["Política de Privacidad"] === "FAIL") {
        setPrerequisiteFailure("Política de Privacidad", "Administrar Negocios view");
      }
      return;
    }

    await validateLegalPage({
      reportField: "Términos y Condiciones",
      triggerText: "Términos y Condiciones",
      expectedHeading: "Términos y Condiciones",
      screenshotLabel: "terminos-y-condiciones",
    });

    await validateLegalPage({
      reportField: "Política de Privacidad",
      triggerText: "Política de Privacidad",
      expectedHeading: "Política de Privacidad",
      screenshotLabel: "politica-de-privacidad",
    });
  } catch (error) {
    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    finalReport.unhandledError = message;
    for (const field of reportFields) {
      if (finalReport.detailsByField[field] === "Not executed.") {
        setFieldResult(field, false, `Unhandled error before execution: ${message}`);
      }
    }
  } finally {
    await state.context?.close().catch(() => {});
    await state.browser?.close().catch(() => {});
  }
}

await run();
const { allPassed } = await writeFinalReport();
if (!allPassed) {
  process.exitCode = 1;
}
