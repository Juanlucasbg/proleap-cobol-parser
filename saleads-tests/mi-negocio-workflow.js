import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const EMAIL_TO_SELECT = "juanlucasbarbiergarzon@gmail.com";
const UI_WAIT_TIMEOUT_MS = Number(process.env.UI_WAIT_TIMEOUT_MS || 20000);
const CDP_URL =
  process.env.CHROME_CDP_URL ||
  process.env.PLAYWRIGHT_CDP_URL ||
  "http://127.0.0.1:9222";

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function nowTag() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function slugify(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function ensureDirectory(targetPath) {
  await fs.mkdir(targetPath, { recursive: true });
}

async function waitForUiLoad(page) {
  try {
    await page.waitForLoadState("domcontentloaded", { timeout: UI_WAIT_TIMEOUT_MS });
  } catch {
    // Some SPA interactions do not trigger navigation events.
  }

  try {
    await page.waitForLoadState("networkidle", { timeout: 5000 });
  } catch {
    // networkidle may never happen in highly dynamic UIs.
  }

  await page.waitForTimeout(500);
}

function locatorCandidates(target, label) {
  const regex = new RegExp(escapeRegExp(label), "i");

  return [
    target.getByRole("button", { name: regex }).first(),
    target.getByRole("link", { name: regex }).first(),
    target.getByRole("menuitem", { name: regex }).first(),
    target.getByRole("tab", { name: regex }).first(),
    target.getByRole("treeitem", { name: regex }).first(),
    target.getByText(regex).first(),
  ];
}

async function isLocatorVisible(locator) {
  try {
    return await locator.isVisible();
  } catch {
    return false;
  }
}

async function findVisibleLocator(target, labels, timeoutMs = UI_WAIT_TIMEOUT_MS) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() <= deadline) {
    for (const label of labels) {
      for (const locator of locatorCandidates(target, label)) {
        if (await isLocatorVisible(locator)) {
          return locator;
        }
      }
    }
    await target.waitForTimeout(300);
  }

  throw new Error(`Unable to find a visible element for labels: ${labels.join(" | ")}`);
}

async function textIsVisible(target, labels, timeoutMs = 2000) {
  try {
    await findVisibleLocator(target, labels, timeoutMs);
    return true;
  } catch {
    return false;
  }
}

function addValidation(step, validation, status, details = "") {
  step.validations.push({ validation, status, details });
}

async function validateVisibleByText(step, target, labels, validation) {
  try {
    await findVisibleLocator(target, labels);
    addValidation(step, validation, "PASS");
    return true;
  } catch (error) {
    addValidation(step, validation, "FAIL", error.message);
    return false;
  }
}

async function validateVisibleByLocator(step, locator, validation) {
  try {
    await locator.first().waitFor({ state: "visible", timeout: UI_WAIT_TIMEOUT_MS });
    addValidation(step, validation, "PASS");
    return true;
  } catch (error) {
    addValidation(step, validation, "FAIL", error.message);
    return false;
  }
}

async function clickByText(step, page, labels, actionName) {
  const locator = await findVisibleLocator(page, labels);
  await locator.click();
  step.actions.push(actionName);
  await waitForUiLoad(page);
}

async function takeScreenshot(page, screenshotsDir, label, options = {}) {
  const fileName = `${nowTag()}-${slugify(label)}.png`;
  const fullPath = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: fullPath, fullPage: Boolean(options.fullPage) });
  return fullPath;
}

async function selectGoogleAccountIfPrompted(step, context) {
  const deadline = Date.now() + 15000;
  const labels = [EMAIL_TO_SELECT];

  while (Date.now() <= deadline) {
    for (const page of context.pages()) {
      try {
        const locator = await findVisibleLocator(page, labels, 1000);
        await locator.click();
        step.actions.push(`Selected Google account: ${EMAIL_TO_SELECT}`);
        await waitForUiLoad(page);
        return true;
      } catch {
        // Continue searching until timeout.
      }
    }
    await context.pages()[0]?.waitForTimeout(300);
  }

  step.notes.push("Google account selector was not displayed; continuing with active session.");
  return false;
}

function buildStep(id, name, reportField) {
  return {
    id,
    name,
    reportField,
    status: "PASS",
    actions: [],
    validations: [],
    evidence: [],
    notes: [],
  };
}

function finalizeStep(step) {
  if (step.validations.some((item) => item.status === "FAIL")) {
    step.status = "FAIL";
  }
  if (step.notes.some((note) => note.startsWith("ERROR:"))) {
    step.status = "FAIL";
  }
  return step;
}

async function safeGoBack(page, step) {
  try {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 10000 });
    await waitForUiLoad(page);
    step.actions.push("Returned to application tab using browser back.");
  } catch (error) {
    step.notes.push(`Unable to go back automatically: ${error.message}`);
  }
}

async function run() {
  const startedAt = new Date().toISOString();
  const runId = nowTag();
  const screenshotsDir = path.resolve("screenshots", `run-${runId}`);
  const reportsDir = path.resolve("reports");
  await ensureDirectory(screenshotsDir);
  await ensureDirectory(reportsDir);

  const workflowSteps = [];
  let browser;
  let appPage;

  try {
    browser = await chromium.connectOverCDP(CDP_URL);
    const contexts = browser.contexts();
    if (!contexts.length) {
      throw new Error("No browser context found. Open the browser with remote debugging enabled.");
    }

    const context = contexts[0];
    const pages = context.pages();
    if (!pages.length) {
      throw new Error("No open tab found. Ensure SaleADS login page is open before running.");
    }

    appPage = pages[0];
    await appPage.bringToFront();
    await waitForUiLoad(appPage);

    // Step 1: Login with Google
    {
      const step = buildStep(1, "Login with Google", "Login");
      try {
        await clickByText(step, appPage, ["Sign in with Google", "Iniciar sesion con Google", "Google"], "Click login with Google");
        await selectGoogleAccountIfPrompted(step, context);
        await validateVisibleByLocator(step, appPage.locator("aside, nav"), "Confirm the left sidebar navigation is visible.");
        await validateVisibleByText(step, appPage, ["Negocio", "Dashboard", "Inicio"], "Confirm the main application interface appears.");
        const shot = await takeScreenshot(appPage, screenshotsDir, "dashboard-loaded");
        step.evidence.push({ type: "screenshot", path: shot });
      } catch (error) {
        step.notes.push(`ERROR: ${error.message}`);
      }
      workflowSteps.push(finalizeStep(step));
    }

    // Step 2: Open Mi Negocio menu
    {
      const step = buildStep(2, "Open Mi Negocio menu", "Mi Negocio menu");
      try {
        await clickByText(step, appPage, ["Mi Negocio"], "Open Mi Negocio menu");
        await validateVisibleByText(step, appPage, ["Agregar Negocio"], "Confirm 'Agregar Negocio' is visible.");
        await validateVisibleByText(step, appPage, ["Administrar Negocios"], "Confirm 'Administrar Negocios' is visible.");
        await validateVisibleByText(step, appPage, ["Agregar Negocio", "Administrar Negocios"], "Confirm the submenu expands.");
        const shot = await takeScreenshot(appPage, screenshotsDir, "mi-negocio-menu-expanded");
        step.evidence.push({ type: "screenshot", path: shot });
      } catch (error) {
        step.notes.push(`ERROR: ${error.message}`);
      }
      workflowSteps.push(finalizeStep(step));
    }

    // Step 3: Validate Agregar Negocio modal
    {
      const step = buildStep(3, "Validate Agregar Negocio modal", "Agregar Negocio modal");
      try {
        await clickByText(step, appPage, ["Agregar Negocio"], "Click 'Agregar Negocio'");
        await validateVisibleByText(step, appPage, ["Crear Nuevo Negocio"], "Modal title 'Crear Nuevo Negocio' is visible.");

        let nameInput = appPage.getByLabel(/Nombre del Negocio/i).first();
        if (!(await isLocatorVisible(nameInput))) {
          nameInput = appPage.getByPlaceholder(/Nombre del Negocio/i).first();
        }
        await validateVisibleByLocator(step, nameInput, "Input field 'Nombre del Negocio' exists.");
        await validateVisibleByText(step, appPage, ["Tienes 2 de 3 negocios"], "Text 'Tienes 2 de 3 negocios' is visible.");
        await validateVisibleByText(step, appPage, ["Cancelar"], "Button 'Cancelar' is present.");
        await validateVisibleByText(step, appPage, ["Crear Negocio"], "Button 'Crear Negocio' is present.");

        if (await isLocatorVisible(nameInput)) {
          await nameInput.click();
          await nameInput.fill("Negocio Prueba Automatizacion");
          step.actions.push("Typed 'Negocio Prueba Automatizacion' in business name field.");
        }

        const shot = await takeScreenshot(appPage, screenshotsDir, "agregar-negocio-modal");
        step.evidence.push({ type: "screenshot", path: shot });

        await clickByText(step, appPage, ["Cancelar"], "Close modal using 'Cancelar'");
      } catch (error) {
        step.notes.push(`ERROR: ${error.message}`);
      }
      workflowSteps.push(finalizeStep(step));
    }

    // Step 4: Open Administrar Negocios
    {
      const step = buildStep(4, "Open Administrar Negocios", "Administrar Negocios view");
      try {
        const administrarVisible = await textIsVisible(appPage, ["Administrar Negocios"]);
        if (!administrarVisible) {
          await clickByText(step, appPage, ["Mi Negocio"], "Expand Mi Negocio again");
        }

        await clickByText(step, appPage, ["Administrar Negocios"], "Open Administrar Negocios");
        await validateVisibleByText(step, appPage, ["Informacion General", "Información General"], "Section 'Información General' exists.");
        await validateVisibleByText(step, appPage, ["Detalles de la Cuenta"], "Section 'Detalles de la Cuenta' exists.");
        await validateVisibleByText(step, appPage, ["Tus Negocios"], "Section 'Tus Negocios' exists.");
        await validateVisibleByText(step, appPage, ["Seccion Legal", "Sección Legal"], "Section 'Sección Legal' exists.");
        const shot = await takeScreenshot(appPage, screenshotsDir, "administrar-negocios-page", { fullPage: true });
        step.evidence.push({ type: "screenshot", path: shot });
      } catch (error) {
        step.notes.push(`ERROR: ${error.message}`);
      }
      workflowSteps.push(finalizeStep(step));
    }

    // Step 5: Validate Información General
    {
      const step = buildStep(5, "Validate Información General", "Información General");
      try {
        await validateVisibleByText(step, appPage, ["@", ".com", ".ai"], "User email is visible.");
        await validateVisibleByText(step, appPage, ["Nombre", "Name"], "User name is visible.");
        await validateVisibleByText(step, appPage, ["BUSINESS PLAN"], "Text 'BUSINESS PLAN' is visible.");
        await validateVisibleByText(step, appPage, ["Cambiar Plan"], "Button 'Cambiar Plan' is visible.");
      } catch (error) {
        step.notes.push(`ERROR: ${error.message}`);
      }
      workflowSteps.push(finalizeStep(step));
    }

    // Step 6: Validate Detalles de la Cuenta
    {
      const step = buildStep(6, "Validate Detalles de la Cuenta", "Detalles de la Cuenta");
      try {
        await validateVisibleByText(step, appPage, ["Cuenta creada"], "'Cuenta creada' is visible.");
        await validateVisibleByText(step, appPage, ["Estado activo"], "'Estado activo' is visible.");
        await validateVisibleByText(step, appPage, ["Idioma seleccionado"], "'Idioma seleccionado' is visible.");
      } catch (error) {
        step.notes.push(`ERROR: ${error.message}`);
      }
      workflowSteps.push(finalizeStep(step));
    }

    // Step 7: Validate Tus Negocios
    {
      const step = buildStep(7, "Validate Tus Negocios", "Tus Negocios");
      try {
        await validateVisibleByText(step, appPage, ["Tus Negocios"], "Business list is visible.");
        await validateVisibleByText(step, appPage, ["Agregar Negocio"], "Button 'Agregar Negocio' exists.");
        await validateVisibleByText(step, appPage, ["Tienes 2 de 3 negocios"], "Text 'Tienes 2 de 3 negocios' is visible.");
      } catch (error) {
        step.notes.push(`ERROR: ${error.message}`);
      }
      workflowSteps.push(finalizeStep(step));
    }

    async function validateLegalPageStep(stepId, linkLabels, headingLabels, reportField, screenshotLabel) {
      const step = buildStep(stepId, `Validate ${reportField}`, reportField);
      const context = appPage.context();
      const existingPages = new Set(context.pages());
      const appUrlBeforeOpen = appPage.url();

      try {
        await clickByText(step, appPage, linkLabels, `Click '${reportField}'`);
        await appPage.waitForTimeout(1200);

        let legalPage = appPage;
        for (const page of context.pages()) {
          if (!existingPages.has(page)) {
            legalPage = page;
            break;
          }
        }

        if (legalPage !== appPage) {
          await legalPage.bringToFront();
          await waitForUiLoad(legalPage);
          step.actions.push("Legal page opened in a new tab.");
        } else {
          step.actions.push("Legal page opened in the current tab.");
          await waitForUiLoad(appPage);
        }

        await validateVisibleByText(step, legalPage, headingLabels, `The page contains the heading '${reportField}'.`);
        await validateVisibleByText(
          step,
          legalPage,
          ["condiciones", "terminos", "términos", "privacidad", "datos", "legal"],
          "Legal content text is visible."
        );

        const legalUrl = legalPage.url();
        step.evidence.push({ type: "url", value: legalUrl });
        const shot = await takeScreenshot(legalPage, screenshotsDir, screenshotLabel);
        step.evidence.push({ type: "screenshot", path: shot });

        if (legalPage !== appPage) {
          await legalPage.close();
          await appPage.bringToFront();
        } else if (appPage.url() !== appUrlBeforeOpen) {
          await safeGoBack(appPage, step);
        }
      } catch (error) {
        step.notes.push(`ERROR: ${error.message}`);
      }

      workflowSteps.push(finalizeStep(step));
    }

    // Step 8: Validate Términos y Condiciones
    await validateLegalPageStep(
      8,
      ["Terminos y Condiciones", "Términos y Condiciones"],
      ["Terminos y Condiciones", "Términos y Condiciones"],
      "Términos y Condiciones",
      "terminos-y-condiciones"
    );

    // Step 9: Validate Política de Privacidad
    await validateLegalPageStep(
      9,
      ["Politica de Privacidad", "Política de Privacidad"],
      ["Politica de Privacidad", "Política de Privacidad"],
      "Política de Privacidad",
      "politica-de-privacidad"
    );
  } catch (error) {
    workflowSteps.push({
      id: 0,
      name: "Execution bootstrap",
      reportField: "Execution",
      status: "FAIL",
      actions: [],
      validations: [],
      evidence: [],
      notes: [`ERROR: ${error.message}`],
    });
  } finally {
    if (browser) {
      await browser.close();
    }
  }

  const summary = {};
  for (const step of workflowSteps) {
    if (step.reportField) {
      summary[step.reportField] = step.status;
    }
  }

  const finalReport = {
    runId,
    startedAt,
    endedAt: new Date().toISOString(),
    cdpUrl: CDP_URL,
    steps: workflowSteps,
    finalSummary: summary,
  };

  const reportPath = path.resolve("reports", `mi-negocio-report-${runId}.json`);
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

  console.log("SaleADS Mi Negocio workflow report generated:");
  console.log(reportPath);
  console.log(JSON.stringify(summary, null, 2));

  const hasFailure = workflowSteps.some((step) => step.status === "FAIL");
  process.exitCode = hasFailure ? 1 : 0;
}

run().catch((error) => {
  console.error("Fatal error while running workflow:", error);
  process.exit(1);
});
