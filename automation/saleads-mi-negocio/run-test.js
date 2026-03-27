const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const ARTIFACTS_DIR = path.join(__dirname, "artifacts");

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

const stepResults = REPORT_FIELDS.map((name) => ({
  name,
  status: "FAIL",
  details: "",
  evidence: [],
}));

function ensureArtifactsDir() {
  fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });
}

function getLoginUrl() {
  return (
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    null
  );
}

function slug(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
    .toLowerCase();
}

async function waitForUiSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

function hasTextLocator(page, text) {
  return page.locator(`text=${text}`).first();
}

async function isVisible(page, text, timeout = 4000) {
  try {
    await hasTextLocator(page, text).waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function safeClickByText(page, text, options = {}) {
  const locator = hasTextLocator(page, text);
  await locator.waitFor({ state: "visible", timeout: options.timeout || 10000 });
  await locator.click();
  await waitForUiSettle(page);
}

async function saveScreenshot(page, fileName, fullPage = false) {
  const filePath = path.join(ARTIFACTS_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function setStepResult(stepName, status, details, evidence = []) {
  const index = stepResults.findIndex((step) => step.name === stepName);
  if (index >= 0) {
    stepResults[index] = { name: stepName, status, details, evidence };
  }
}

async function tryGoogleAccountSelection(page, targetEmail) {
  const emailLocator = hasTextLocator(page, targetEmail);
  if (await emailLocator.isVisible().catch(() => false)) {
    await emailLocator.click();
    await waitForUiSettle(page);
    return true;
  }
  return false;
}

async function clickLegalLinkAndValidate(context, appPage, linkText, headingText, screenshotNameBase) {
  const evidence = [];
  let legalPage = null;
  let finalUrl = "";

  const popupPromise = appPage.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  await safeClickByText(appPage, linkText);
  legalPage = await popupPromise;

  if (!legalPage) {
    legalPage = appPage;
  }

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 });
  await legalPage.waitForTimeout(1200);
  finalUrl = legalPage.url();

  const hasHeading = await isVisible(legalPage, headingText, 10000);
  const hasContent = (await legalPage.locator("body").innerText()).trim().length > 50;

  const shotPath = await saveScreenshot(legalPage, `${screenshotNameBase}.png`, true);
  evidence.push(shotPath);

  if (legalPage !== appPage && !legalPage.isClosed()) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUiSettle(appPage);
  } else if (legalPage === appPage) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiSettle(appPage);
  }

  return {
    ok: hasHeading && hasContent,
    details: hasHeading
      ? `Heading visible and content loaded. Final URL: ${finalUrl}`
      : `Expected heading '${headingText}' not visible. Final URL: ${finalUrl}`,
    evidence,
    finalUrl,
  };
}

async function run() {
  ensureArtifactsDir();

  const loginUrl = getLoginUrl();
  if (!loginUrl) {
    const output = {
      startedAt: new Date().toISOString(),
      status: "FAIL",
      reason:
        "No SALEADS_LOGIN_URL/SALEADS_URL/BASE_URL provided. Cannot navigate to login page in a domain-agnostic way.",
      results: stepResults,
    };
    fs.writeFileSync(
      path.join(ARTIFACTS_DIR, "saleads-mi-negocio-report.json"),
      JSON.stringify(output, null, 2)
    );
    console.error(output.reason);
    process.exit(1);
  }

  const browser = await chromium.launch({
    headless: process.env.HEADLESS !== "false",
  });
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 45000 });
    await waitForUiSettle(page);

    // Step 1: Login
    {
      const evidence = [];
      let clickedLogin = false;
      const loginCandidates = [
        "Sign in with Google",
        "Continuar con Google",
        "Iniciar sesión con Google",
        "Google",
      ];

      for (const candidate of loginCandidates) {
        const locator = hasTextLocator(page, candidate);
        if (await locator.isVisible().catch(() => false)) {
          await locator.click();
          clickedLogin = true;
          await waitForUiSettle(page);
          break;
        }
      }

      if (!clickedLogin) {
        setStepResult(
          "Login",
          "FAIL",
          "No visible Google login trigger was found by text.",
          evidence
        );
      } else {
        await tryGoogleAccountSelection(page, "juanlucasbarbiergarzon@gmail.com").catch(() => {});
        const sidebarVisible = await isVisible(page, "Negocio", 25000);
        const dashboardShot = await saveScreenshot(page, "01-dashboard-loaded.png", true);
        evidence.push(dashboardShot);

        setStepResult(
          "Login",
          sidebarVisible ? "PASS" : "FAIL",
          sidebarVisible
            ? "Main interface loaded and left sidebar detected."
            : "Sidebar not detected after login attempt.",
          evidence
        );
      }
    }

    // Step 2: Open Mi Negocio menu
    {
      const evidence = [];
      let pass = false;
      if (stepResults.find((s) => s.name === "Login").status === "PASS") {
        const negocioVisible = await isVisible(page, "Negocio", 10000);
        if (negocioVisible) {
          await safeClickByText(page, "Negocio");
        }
        await safeClickByText(page, "Mi Negocio");

        const hasAgregar = await isVisible(page, "Agregar Negocio", 10000);
        const hasAdministrar = await isVisible(page, "Administrar Negocios", 10000);
        pass = hasAgregar && hasAdministrar;

        evidence.push(await saveScreenshot(page, "02-mi-negocio-menu-expanded.png", true));
        setStepResult(
          "Mi Negocio menu",
          pass ? "PASS" : "FAIL",
          pass
            ? "Mi Negocio expanded and submenu options visible."
            : "Submenu did not show both expected options.",
          evidence
        );
      } else {
        setStepResult(
          "Mi Negocio menu",
          "FAIL",
          "Skipped because Login step failed.",
          evidence
        );
      }
    }

    // Step 3: Validate Agregar Negocio modal
    {
      const evidence = [];
      let pass = false;
      if (stepResults.find((s) => s.name === "Mi Negocio menu").status === "PASS") {
        await safeClickByText(page, "Agregar Negocio");

        const hasTitle = await isVisible(page, "Crear Nuevo Negocio", 10000);
        const hasNombre = await isVisible(page, "Nombre del Negocio", 10000);
        const hasQuota = await isVisible(page, "Tienes 2 de 3 negocios", 10000);
        const hasCancelar = await isVisible(page, "Cancelar", 10000);
        const hasCrear = await isVisible(page, "Crear Negocio", 10000);

        if (hasNombre) {
          const input = page
            .locator("input")
            .filter({ hasText: "" })
            .first();
          await input.click().catch(() => {});
          await input.fill("Negocio Prueba Automatización").catch(() => {});
        }

        evidence.push(await saveScreenshot(page, "03-agregar-negocio-modal.png", true));

        if (hasCancelar) {
          await safeClickByText(page, "Cancelar");
        }

        pass = hasTitle && hasNombre && hasQuota && hasCancelar && hasCrear;
        setStepResult(
          "Agregar Negocio modal",
          pass ? "PASS" : "FAIL",
          pass
            ? "Modal and required controls validated."
            : "One or more required modal elements were not visible.",
          evidence
        );
      } else {
        setStepResult(
          "Agregar Negocio modal",
          "FAIL",
          "Skipped because Mi Negocio menu step failed.",
          evidence
        );
      }
    }

    // Step 4: Open Administrar Negocios
    {
      const evidence = [];
      let pass = false;
      if (stepResults.find((s) => s.name === "Mi Negocio menu").status === "PASS") {
        if (!(await isVisible(page, "Administrar Negocios", 5000))) {
          if (await isVisible(page, "Mi Negocio", 3000)) {
            await safeClickByText(page, "Mi Negocio");
          }
        }
        await safeClickByText(page, "Administrar Negocios");

        const hasInfoGeneral = await isVisible(page, "Información General", 15000);
        const hasDetalles = await isVisible(page, "Detalles de la Cuenta", 15000);
        const hasTusNegocios = await isVisible(page, "Tus Negocios", 15000);
        const hasLegal = await isVisible(page, "Sección Legal", 15000);

        pass = hasInfoGeneral && hasDetalles && hasTusNegocios && hasLegal;
        evidence.push(await saveScreenshot(page, "04-administrar-negocios-cuenta.png", true));
        setStepResult(
          "Administrar Negocios view",
          pass ? "PASS" : "FAIL",
          pass
            ? "Account page sections are visible."
            : "Missing one or more required account sections.",
          evidence
        );
      } else {
        setStepResult(
          "Administrar Negocios view",
          "FAIL",
          "Skipped because Mi Negocio menu step failed.",
          evidence
        );
      }
    }

    // Step 5: Información General
    {
      const evidence = [];
      if (stepResults.find((s) => s.name === "Administrar Negocios view").status === "PASS") {
        const hasPlan = await isVisible(page, "BUSINESS PLAN", 7000);
        const hasCambiarPlan = await isVisible(page, "Cambiar Plan", 7000);
        const pageText = await page.locator("body").innerText();
        const hasUserName = pageText.trim().length > 0;
        const hasUserEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(pageText);

        const pass = hasUserName && hasUserEmail && hasPlan && hasCambiarPlan;
        setStepResult(
          "Información General",
          pass ? "PASS" : "FAIL",
          pass
            ? "User name/email and plan section were validated."
            : "Missing one or more required items in Información General.",
          evidence
        );
      } else {
        setStepResult(
          "Información General",
          "FAIL",
          "Skipped because Administrar Negocios view step failed.",
          evidence
        );
      }
    }

    // Step 6: Detalles de la Cuenta
    {
      const evidence = [];
      if (stepResults.find((s) => s.name === "Administrar Negocios view").status === "PASS") {
        const hasCuentaCreada = await isVisible(page, "Cuenta creada", 7000);
        const hasEstadoActivo = await isVisible(page, "Estado activo", 7000);
        const hasIdioma = await isVisible(page, "Idioma seleccionado", 7000);
        const pass = hasCuentaCreada && hasEstadoActivo && hasIdioma;
        setStepResult(
          "Detalles de la Cuenta",
          pass ? "PASS" : "FAIL",
          pass
            ? "Detalles de la Cuenta labels are visible."
            : "Missing one or more account detail labels.",
          evidence
        );
      } else {
        setStepResult(
          "Detalles de la Cuenta",
          "FAIL",
          "Skipped because Administrar Negocios view step failed.",
          evidence
        );
      }
    }

    // Step 7: Tus Negocios
    {
      const evidence = [];
      if (stepResults.find((s) => s.name === "Administrar Negocios view").status === "PASS") {
        const hasTusNegocios = await isVisible(page, "Tus Negocios", 7000);
        const hasAgregar = await isVisible(page, "Agregar Negocio", 7000);
        const hasQuota = await isVisible(page, "Tienes 2 de 3 negocios", 7000);
        const pass = hasTusNegocios && hasAgregar && hasQuota;
        setStepResult(
          "Tus Negocios",
          pass ? "PASS" : "FAIL",
          pass
            ? "Business list and quota indicators are visible."
            : "Missing one or more required Tus Negocios elements.",
          evidence
        );
      } else {
        setStepResult(
          "Tus Negocios",
          "FAIL",
          "Skipped because Administrar Negocios view step failed.",
          evidence
        );
      }
    }

    // Step 8: Términos y Condiciones
    {
      const legal = await clickLegalLinkAndValidate(
        context,
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "08-terminos-y-condiciones"
      ).catch((error) => ({
        ok: false,
        details: `Could not validate legal page: ${error.message}`,
        evidence: [],
        finalUrl: "",
      }));
      setStepResult(
        "Términos y Condiciones",
        legal.ok ? "PASS" : "FAIL",
        legal.details,
        legal.evidence
      );
    }

    // Step 9: Política de Privacidad
    {
      const legal = await clickLegalLinkAndValidate(
        context,
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "09-politica-de-privacidad"
      ).catch((error) => ({
        ok: false,
        details: `Could not validate legal page: ${error.message}`,
        evidence: [],
        finalUrl: "",
      }));
      setStepResult(
        "Política de Privacidad",
        legal.ok ? "PASS" : "FAIL",
        legal.details,
        legal.evidence
      );
    }

    const allPassed = stepResults.every((step) => step.status === "PASS");
    const report = {
      startedAt: new Date().toISOString(),
      loginUrl,
      status: allPassed ? "PASS" : "FAIL",
      results: stepResults,
    };

    fs.writeFileSync(
      path.join(ARTIFACTS_DIR, "saleads-mi-negocio-report.json"),
      JSON.stringify(report, null, 2)
    );
    fs.writeFileSync(
      path.join(ARTIFACTS_DIR, "saleads-mi-negocio-report.md"),
      [
        "# SaleADS Mi Negocio Workflow Report",
        "",
        `Overall status: **${report.status}**`,
        "",
        ...stepResults.map(
          (step) => `- **${step.name}**: ${step.status} - ${step.details || "No details"}`
        ),
        "",
      ].join("\n")
    );

    if (!allPassed) {
      process.exitCode = 1;
    }
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
}

run().catch((error) => {
  ensureArtifactsDir();
  const report = {
    startedAt: new Date().toISOString(),
    status: "FAIL",
    fatalError: error.message,
    stack: error.stack,
    results: stepResults,
  };
  fs.writeFileSync(
    path.join(ARTIFACTS_DIR, "saleads-mi-negocio-report.json"),
    JSON.stringify(report, null, 2)
  );
  console.error(error);
  process.exit(1);
});
