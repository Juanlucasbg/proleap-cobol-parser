const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

const FIELD_BY_STEP_ID = {
  1: "Login",
  2: "Mi Negocio menu",
  3: "Agregar Negocio modal",
  4: "Administrar Negocios view",
  5: "Información General",
  6: "Detalles de la Cuenta",
  7: "Tus Negocios",
  8: "Términos y Condiciones",
  9: "Política de Privacidad",
};

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

function normalizeText(value) {
  return (value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function isLocatorVisible(locator, timeout = 2500) {
  return locator.first().isVisible({ timeout }).catch(() => false);
}

async function clickByVisibleText(page, candidates, options = {}) {
  const timeoutPerCandidate = options.timeoutPerCandidate ?? 2500;

  for (const candidate of candidates) {
    const exactButton = page.getByRole("button", { name: new RegExp(`^\\s*${escapeRegex(candidate)}\\s*$`, "i") });
    if (await isLocatorVisible(exactButton, timeoutPerCandidate)) {
      await exactButton.first().click();
      await waitForUi(page);
      return candidate;
    }

    const link = page.getByRole("link", { name: new RegExp(`${escapeRegex(candidate)}`, "i") });
    if (await isLocatorVisible(link, timeoutPerCandidate)) {
      await link.first().click();
      await waitForUi(page);
      return candidate;
    }

    const textNode = page.getByText(candidate, { exact: false });
    if (await isLocatorVisible(textNode, timeoutPerCandidate)) {
      await textNode.first().click();
      await waitForUi(page);
      return candidate;
    }
  }

  throw new Error(`Unable to click any candidate text: ${candidates.join(" | ")}`);
}

async function visibleByTexts(page, candidates, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const textNode = page.getByText(candidate, { exact: false });
      if (await isLocatorVisible(textNode, 800)) {
        return { visible: true, matched: candidate };
      }

      const heading = page.getByRole("heading", { name: new RegExp(`${escapeRegex(candidate)}`, "i") });
      if (await isLocatorVisible(heading, 800)) {
        return { visible: true, matched: candidate };
      }
    }
    await page.waitForTimeout(400);
  }

  return { visible: false, matched: null };
}

async function visibleByRegexInBody(page, regex, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const bodyText = await page.locator("body").innerText().catch(() => "");
    if (regex.test(bodyText)) {
      return true;
    }
    await page.waitForTimeout(400);
  }
  return false;
}

async function captureScreenshot(page, screenshotPath, fullPage = false) {
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function startStep(id, name) {
  return {
    id,
    name,
    reportField: FIELD_BY_STEP_ID[id] || name,
    status: "FAIL",
    validations: [],
    actions: [],
    evidence: {
      screenshots: [],
      finalUrl: null,
    },
    notes: [],
  };
}

function addValidation(step, name, pass, details = "") {
  step.validations.push({ name, status: pass ? "PASS" : "FAIL", details });
}

function finalizeStep(step) {
  step.status = step.validations.length > 0 && step.validations.every((v) => v.status === "PASS") ? "PASS" : "FAIL";
}

async function chooseGoogleAccountIfShown(context, email) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    for (const p of context.pages()) {
      if (p.isClosed()) {
        continue;
      }
      const emailNode = p.getByText(email, { exact: false });
      if (await isLocatorVisible(emailNode, 800)) {
        await emailNode.first().click().catch(() => {});
        await waitForUi(p);
        return true;
      }
    }
    await context.waitForEvent("page", { timeout: 1000 }).catch(() => {});
  }
  return false;
}

async function findAppPage(context, timeoutMs = 35000) {
  const deadline = Date.now() + timeoutMs;
  const appTextHints = ["Negocio", "Mi Negocio", "Administrar Negocios", "Dashboard"];

  while (Date.now() < deadline) {
    for (const p of context.pages()) {
      if (p.isClosed()) {
        continue;
      }
      for (const hint of appTextHints) {
        const visible = await isLocatorVisible(p.getByText(hint, { exact: false }), 700);
        if (visible) {
          return p;
        }
      }
    }
    await context.waitForEvent("page", { timeout: 1200 }).catch(() => {});
  }

  return context.pages().find((p) => !p.isClosed()) || null;
}

async function clickLegalLinkAndValidate(page, context, linkCandidates, headingCandidates, artifactPath) {
  const popupPromise = context.waitForEvent("page", { timeout: 9000 }).catch(() => null);
  await clickByVisibleText(page, linkCandidates);

  const popup = await popupPromise;
  const targetPage = popup || page;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
    await waitForUi(popup);
  } else {
    await waitForUi(page);
  }

  const headingVisible = await visibleByTexts(targetPage, headingCandidates, 18000);
  const legalContentVisible = await visibleByRegexInBody(
    targetPage,
    /(terminos|condiciones|privacidad|datos personales|uso|responsabilidad|politica)/i,
    18000
  );
  const url = targetPage.url();
  await captureScreenshot(targetPage, artifactPath, true);

  return {
    headingVisible: headingVisible.visible,
    headingMatched: headingVisible.matched,
    legalContentVisible,
    finalUrl: url,
    openedInNewTab: Boolean(popup),
    targetPage,
  };
}

async function returnToApplicationPage(page, legalTargetPage, openedInNewTab) {
  if (openedInNewTab && legalTargetPage && !legalTargetPage.isClosed()) {
    await legalTargetPage.close().catch(() => {});
    await page.bringToFront().catch(() => {});
    await waitForUi(page);
    return;
  }

  await page.goBack({ waitUntil: "domcontentloaded", timeout: 12000 }).catch(() => {});
  await waitForUi(page);
}

async function run() {
  const startedAt = new Date().toISOString();
  const runId = nowStamp();
  const artifactsDir = path.join(__dirname, "artifacts", runId);
  await ensureDir(artifactsDir);

  const report = {
    name: TEST_NAME,
    startedAt,
    finishedAt: null,
    environment: {
      loginUrl: process.env.SALEADS_LOGIN_URL || null,
      headless: process.env.HEADLESS !== "false",
    },
    steps: [],
    summary: {},
  };

  const browser = await chromium.launch({ headless: process.env.HEADLESS !== "false" });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  let page = await context.newPage();

  let loginOk = false;
  let menuOk = false;
  let accountViewOk = false;

  try {
    const step1 = startStep(1, "Login with Google");
    report.steps.push(step1);
    const loginUrl = process.env.SALEADS_LOGIN_URL;

    if (loginUrl) {
      step1.actions.push(`Navigating to login URL provided by env var SALEADS_LOGIN_URL: ${loginUrl}`);
      await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
      await waitForUi(page);
    } else {
      step1.actions.push("SALEADS_LOGIN_URL not provided; test continues on current page context.");
    }

    try {
      await clickByVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Login with Google",
        "Ingresar con Google",
        "Continuar con Google",
      ]);
      step1.actions.push("Clicked Google login button.");
    } catch (error) {
      step1.notes.push(`Could not click Google login button: ${error.message}`);
    }

    const accountSelected = await chooseGoogleAccountIfShown(context, ACCOUNT_EMAIL);
    if (accountSelected) {
      step1.actions.push(`Selected Google account ${ACCOUNT_EMAIL}.`);
    } else {
      step1.actions.push("Google account chooser not detected or account already selected.");
    }

    const candidateAppPage = await findAppPage(context, 40000);
    if (candidateAppPage) {
      page = candidateAppPage;
      await page.bringToFront().catch(() => {});
    }

    const mainVisible = await visibleByTexts(page, ["Negocio", "Mi Negocio", "Dashboard", "Inicio"], 25000);
    const sidebarVisible =
      (await isLocatorVisible(page.locator("aside"), 3000)) ||
      (await visibleByTexts(page, ["Negocio", "Mi Negocio"], 9000)).visible;
    addValidation(step1, "Main application interface appears", mainVisible.visible, mainVisible.matched || "");
    addValidation(step1, "Left sidebar navigation is visible", sidebarVisible);

    const step1Shot = path.join(artifactsDir, "step-1-dashboard.png");
    step1.evidence.screenshots.push(await captureScreenshot(page, step1Shot, true));

    finalizeStep(step1);
    loginOk = step1.status === "PASS";

    const step2 = startStep(2, "Open Mi Negocio menu");
    report.steps.push(step2);
    if (!loginOk) {
      addValidation(step2, "Prerequisite: Login successful", false, "Skipped because login step failed.");
      finalizeStep(step2);
    } else {
      try {
        await clickByVisibleText(page, ["Negocio"]);
        await clickByVisibleText(page, ["Mi Negocio"]);
      } catch (error) {
        step2.notes.push(`Menu click sequence had issues: ${error.message}`);
      }

      const submenuExpanded = (await visibleByTexts(page, ["Agregar Negocio", "Administrar Negocios"], 10000)).visible;
      const agregarVisible = (await visibleByTexts(page, ["Agregar Negocio"], 10000)).visible;
      const administrarVisible = (await visibleByTexts(page, ["Administrar Negocios"], 10000)).visible;

      addValidation(step2, "Mi Negocio submenu expands", submenuExpanded);
      addValidation(step2, "'Agregar Negocio' is visible", agregarVisible);
      addValidation(step2, "'Administrar Negocios' is visible", administrarVisible);

      const step2Shot = path.join(artifactsDir, "step-2-mi-negocio-menu.png");
      step2.evidence.screenshots.push(await captureScreenshot(page, step2Shot, true));
      finalizeStep(step2);
    }
    menuOk = step2.status === "PASS";

    const step3 = startStep(3, "Validate Agregar Negocio modal");
    report.steps.push(step3);
    if (!menuOk) {
      addValidation(step3, "Prerequisite: Mi Negocio menu available", false, "Skipped because menu step failed.");
      finalizeStep(step3);
    } else {
      try {
        await clickByVisibleText(page, ["Agregar Negocio"]);
      } catch (error) {
        step3.notes.push(`Could not click 'Agregar Negocio': ${error.message}`);
      }

      const modalTitle = (await visibleByTexts(page, ["Crear Nuevo Negocio"], 10000)).visible;
      const nombreNegocio = (await visibleByTexts(page, ["Nombre del Negocio"], 7000)).visible;
      const quotaText = (await visibleByTexts(page, ["Tienes 2 de 3 negocios"], 7000)).visible;
      const cancelar = (await visibleByTexts(page, ["Cancelar"], 7000)).visible;
      const crear = (await visibleByTexts(page, ["Crear Negocio"], 7000)).visible;

      addValidation(step3, "Modal title 'Crear Nuevo Negocio' is visible", modalTitle);
      addValidation(step3, "Input field 'Nombre del Negocio' exists", nombreNegocio);
      addValidation(step3, "Text 'Tienes 2 de 3 negocios' is visible", quotaText);
      addValidation(step3, "Buttons 'Cancelar' and 'Crear Negocio' are present", cancelar && crear);

      const step3Shot = path.join(artifactsDir, "step-3-agregar-negocio-modal.png");
      step3.evidence.screenshots.push(await captureScreenshot(page, step3Shot, true));

      if (nombreNegocio) {
        const input = page.getByLabel("Nombre del Negocio").first();
        if (await isLocatorVisible(input, 1000)) {
          await input.fill("Negocio Prueba Automatización").catch(() => {});
        } else {
          await page.getByPlaceholder(/nombre/i).first().fill("Negocio Prueba Automatización").catch(() => {});
        }
      }
      await clickByVisibleText(page, ["Cancelar"]).catch(() => {});

      finalizeStep(step3);
    }

    const step4 = startStep(4, "Open Administrar Negocios");
    report.steps.push(step4);
    if (!menuOk) {
      addValidation(step4, "Prerequisite: Mi Negocio menu available", false, "Skipped because menu step failed.");
      finalizeStep(step4);
    } else {
      await clickByVisibleText(page, ["Mi Negocio"]).catch(() => {});
      try {
        await clickByVisibleText(page, ["Administrar Negocios"]);
      } catch (error) {
        step4.notes.push(`Could not click 'Administrar Negocios': ${error.message}`);
      }

      const infoGeneral = (await visibleByTexts(page, ["Información General", "Informacion General"], 12000)).visible;
      const detalles = (await visibleByTexts(page, ["Detalles de la Cuenta", "Detalles de la cuenta"], 12000)).visible;
      const negocios = (await visibleByTexts(page, ["Tus Negocios"], 12000)).visible;
      const legal = (await visibleByTexts(page, ["Sección Legal", "Seccion Legal"], 12000)).visible;

      addValidation(step4, "Section 'Información General' exists", infoGeneral);
      addValidation(step4, "Section 'Detalles de la Cuenta' exists", detalles);
      addValidation(step4, "Section 'Tus Negocios' exists", negocios);
      addValidation(step4, "Section 'Sección Legal' exists", legal);

      const step4Shot = path.join(artifactsDir, "step-4-administrar-negocios.png");
      step4.evidence.screenshots.push(await captureScreenshot(page, step4Shot, true));
      finalizeStep(step4);
    }
    accountViewOk = step4.status === "PASS";

    const step5 = startStep(5, "Validate Información General");
    report.steps.push(step5);
    if (!accountViewOk) {
      addValidation(step5, "Prerequisite: Administrar Negocios page loaded", false, "Skipped because account page failed.");
      finalizeStep(step5);
    } else {
      const bodyText = await page.locator("body").innerText().catch(() => "");
      const emailVisible = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(bodyText);
      const accountLocalPart = ACCOUNT_EMAIL.split("@")[0];
      const possibleNameVisible =
        normalizeText(bodyText).includes(normalizeText(process.env.SALEADS_EXPECTED_USER_NAME || "")) ||
        normalizeText(bodyText).includes(normalizeText(accountLocalPart));
      const businessPlanVisible = (await visibleByTexts(page, ["BUSINESS PLAN"], 7000)).visible;
      const cambiarPlanVisible = (await visibleByTexts(page, ["Cambiar Plan"], 7000)).visible;

      addValidation(step5, "User name is visible", possibleNameVisible);
      addValidation(step5, "User email is visible", emailVisible);
      addValidation(step5, "Text 'BUSINESS PLAN' is visible", businessPlanVisible);
      addValidation(step5, "Button 'Cambiar Plan' is visible", cambiarPlanVisible);
      finalizeStep(step5);
    }

    const step6 = startStep(6, "Validate Detalles de la Cuenta");
    report.steps.push(step6);
    if (!accountViewOk) {
      addValidation(step6, "Prerequisite: Administrar Negocios page loaded", false, "Skipped because account page failed.");
      finalizeStep(step6);
    } else {
      const cuentaCreada = (await visibleByTexts(page, ["Cuenta creada"], 7000)).visible;
      const estadoActivo = (await visibleByTexts(page, ["Estado activo"], 7000)).visible;
      const idioma = (await visibleByTexts(page, ["Idioma seleccionado"], 7000)).visible;
      addValidation(step6, "'Cuenta creada' is visible", cuentaCreada);
      addValidation(step6, "'Estado activo' is visible", estadoActivo);
      addValidation(step6, "'Idioma seleccionado' is visible", idioma);
      finalizeStep(step6);
    }

    const step7 = startStep(7, "Validate Tus Negocios");
    report.steps.push(step7);
    if (!accountViewOk) {
      addValidation(step7, "Prerequisite: Administrar Negocios page loaded", false, "Skipped because account page failed.");
      finalizeStep(step7);
    } else {
      const listVisible = (await visibleByTexts(page, ["Tus Negocios"], 7000)).visible;
      const addButtonVisible = (await visibleByTexts(page, ["Agregar Negocio"], 7000)).visible;
      const quotaVisible = (await visibleByTexts(page, ["Tienes 2 de 3 negocios"], 7000)).visible;
      addValidation(step7, "Business list is visible", listVisible);
      addValidation(step7, "Button 'Agregar Negocio' exists", addButtonVisible);
      addValidation(step7, "Text 'Tienes 2 de 3 negocios' is visible", quotaVisible);
      finalizeStep(step7);
    }

    const step8 = startStep(8, "Validate Términos y Condiciones");
    report.steps.push(step8);
    if (!accountViewOk) {
      addValidation(step8, "Prerequisite: Administrar Negocios page loaded", false, "Skipped because account page failed.");
      finalizeStep(step8);
    } else {
      try {
        const step8Shot = path.join(artifactsDir, "step-8-terminos.png");
        const legalResult = await clickLegalLinkAndValidate(
          page,
          context,
          ["Términos y Condiciones", "Terminos y Condiciones"],
          ["Términos y Condiciones", "Terminos y Condiciones"],
          step8Shot
        );
        step8.evidence.screenshots.push(step8Shot);
        step8.evidence.finalUrl = legalResult.finalUrl;
        addValidation(step8, "Page contains heading 'Términos y Condiciones'", legalResult.headingVisible, legalResult.headingMatched || "");
        addValidation(step8, "Legal content text is visible", legalResult.legalContentVisible);
        await returnToApplicationPage(page, legalResult.targetPage, legalResult.openedInNewTab);
      } catch (error) {
        addValidation(step8, "Open and validate Términos y Condiciones", false, error.message);
      }
      finalizeStep(step8);
    }

    const step9 = startStep(9, "Validate Política de Privacidad");
    report.steps.push(step9);
    if (!accountViewOk) {
      addValidation(step9, "Prerequisite: Administrar Negocios page loaded", false, "Skipped because account page failed.");
      finalizeStep(step9);
    } else {
      try {
        const step9Shot = path.join(artifactsDir, "step-9-politica-privacidad.png");
        const legalResult = await clickLegalLinkAndValidate(
          page,
          context,
          ["Política de Privacidad", "Politica de Privacidad"],
          ["Política de Privacidad", "Politica de Privacidad"],
          step9Shot
        );
        step9.evidence.screenshots.push(step9Shot);
        step9.evidence.finalUrl = legalResult.finalUrl;
        addValidation(step9, "Page contains heading 'Política de Privacidad'", legalResult.headingVisible, legalResult.headingMatched || "");
        addValidation(step9, "Legal content text is visible", legalResult.legalContentVisible);
        await returnToApplicationPage(page, legalResult.targetPage, legalResult.openedInNewTab);
      } catch (error) {
        addValidation(step9, "Open and validate Política de Privacidad", false, error.message);
      }
      finalizeStep(step9);
    }
  } catch (fatal) {
    report.fatalError = fatal.message;
  } finally {
    for (const field of reportFields) {
      const relatedStep = report.steps.find((s) => s.reportField === field);
      report.summary[field] = relatedStep && relatedStep.status === "PASS" ? "PASS" : "FAIL";
    }

    report.finishedAt = new Date().toISOString();
    const reportPath = path.join(artifactsDir, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

    const markdownLines = [];
    markdownLines.push(`# ${TEST_NAME} - Final Report`);
    markdownLines.push("");
    markdownLines.push(`- Started: ${report.startedAt}`);
    markdownLines.push(`- Finished: ${report.finishedAt}`);
    markdownLines.push(`- Artifacts: ${artifactsDir}`);
    markdownLines.push("");
    markdownLines.push("## PASS / FAIL by requested field");
    markdownLines.push("");
    for (const field of reportFields) {
      markdownLines.push(`- ${field}: ${report.summary[field]}`);
    }
    markdownLines.push("");
    markdownLines.push("## Step Evidence");
    markdownLines.push("");
    for (const step of report.steps) {
      markdownLines.push(`### Step ${step.id} - ${step.name} (${step.status})`);
      for (const validation of step.validations) {
        markdownLines.push(`- [${validation.status}] ${validation.name}${validation.details ? ` (${validation.details})` : ""}`);
      }
      for (const shot of step.evidence.screenshots) {
        markdownLines.push(`- Screenshot: ${shot}`);
      }
      if (step.evidence.finalUrl) {
        markdownLines.push(`- Final URL: ${step.evidence.finalUrl}`);
      }
      if (step.notes.length > 0) {
        for (const note of step.notes) {
          markdownLines.push(`- Note: ${note}`);
        }
      }
      markdownLines.push("");
    }

    const markdownPath = path.join(artifactsDir, "final-report.md");
    await fs.writeFile(markdownPath, markdownLines.join("\n"), "utf8");

    await browser.close();

    // Console output for CI logs.
    console.log(`Artifacts directory: ${artifactsDir}`);
    console.log("Summary:");
    for (const field of reportFields) {
      console.log(`- ${field}: ${report.summary[field]}`);
    }
    console.log(`Detailed JSON report: ${reportPath}`);

    const hasFailures = Object.values(report.summary).some((status) => status === "FAIL");
    if (hasFailures) {
      process.exitCode = 1;
    }
  }
}

run().catch((error) => {
  console.error("Unhandled test runner error:", error);
  process.exit(1);
});
