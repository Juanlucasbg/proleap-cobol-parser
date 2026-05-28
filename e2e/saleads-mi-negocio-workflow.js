#!/usr/bin/env node

const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const TARGET_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const UI_WAIT_MS = Number.parseInt(process.env.UI_WAIT_MS || "1200", 10);
const STEP_TIMEOUT_MS = Number.parseInt(process.env.STEP_TIMEOUT_MS || "15000", 10);

function sanitizeName(input) {
  return input
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(UI_WAIT_MS);
}

async function isVisible(locator, timeout = STEP_TIMEOUT_MS) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch (_) {
    return false;
  }
}

async function clickFirstVisible(page, candidates, actionLabel) {
  for (const candidate of candidates) {
    const locator = candidate();
    if (await isVisible(locator, 1800)) {
      await locator.first().click();
      await waitForUi(page);
      return true;
    }
  }
  throw new Error(`Could not locate clickable element for: ${actionLabel}`);
}

async function captureScreenshot(page, artifactsDir, label, options = {}) {
  const fileName = `${Date.now()}-${sanitizeName(label)}.png`;
  const filePath = path.join(artifactsDir, fileName);
  await page.screenshot({
    path: filePath,
    fullPage: Boolean(options.fullPage),
  });
  return filePath;
}

async function getExistingOrNewPage() {
  const cdpEndpoint = process.env.BROWSER_WS_ENDPOINT;
  const startUrl = process.env.SALEADS_URL;

  if (cdpEndpoint) {
    const browser = await chromium.connectOverCDP(cdpEndpoint);
    const context = browser.contexts()[0] || (await browser.newContext());
    const existingPage = context.pages()[0] || (await context.newPage());
    return { browser, context, page: existingPage, attachedToExistingBrowser: true };
  }

  const browser = await chromium.launch({ headless: process.env.HEADLESS !== "false" });
  const context = await browser.newContext();
  const page = await context.newPage();

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else {
    throw new Error(
      "No SALEADS_URL or BROWSER_WS_ENDPOINT was provided. " +
        "Set SALEADS_URL for fresh runs, or BROWSER_WS_ENDPOINT to attach to an existing browser already on the login page."
    );
  }

  return { browser, context, page, attachedToExistingBrowser: false };
}

function createStepResult(id, name) {
  return {
    id,
    name,
    status: "FAIL",
    validations: [],
    evidence: [],
    errors: [],
  };
}

function addValidation(step, label, pass, details = "") {
  step.validations.push({ label, pass, details });
}

async function addScreenshotEvidence(step, page, artifactsDir, label, options = {}) {
  const screenshotPath = await captureScreenshot(page, artifactsDir, label, options);
  step.evidence.push({ type: "screenshot", label, path: screenshotPath });
}

function finalizeStep(step) {
  const validationPass = step.validations.every((item) => item.pass);
  const noErrors = step.errors.length === 0;
  step.status = validationPass && noErrors ? "PASS" : "FAIL";
}

async function runStep(report, id, name, stepFn) {
  const step = createStepResult(id, name);
  try {
    await stepFn(step);
  } catch (error) {
    step.errors.push(error instanceof Error ? error.message : String(error));
  } finally {
    finalizeStep(step);
    report.steps.push(step);
  }
}

async function validateLegalLink({
  appPage,
  context,
  step,
  artifactsDir,
  linkRegex,
  headingRegex,
  screenshotLabel,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickFirstVisible(
    appPage,
    [
      () => appPage.getByRole("link", { name: linkRegex }),
      () => appPage.getByRole("button", { name: linkRegex }),
      () => appPage.getByText(linkRegex),
    ],
    `Open legal link ${String(linkRegex)}`
  );

  const popup = await popupPromise;
  const legalPage = popup || appPage;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.bringToFront();
    await popup.waitForTimeout(UI_WAIT_MS);
  }

  const headingVisible = await isVisible(
    legalPage.getByRole("heading", { name: headingRegex }),
    STEP_TIMEOUT_MS
  );
  addValidation(step, `Heading ${headingRegex} is visible`, headingVisible);

  const bodyText = await legalPage.locator("body").innerText();
  const hasLegalContent = bodyText.trim().length > 200;
  addValidation(step, "Legal content text is visible", hasLegalContent, `Body length=${bodyText.length}`);

  await addScreenshotEvidence(step, legalPage, artifactsDir, screenshotLabel, { fullPage: true });
  step.evidence.push({ type: "url", label: "final_url", value: legalPage.url() });

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(appPage);
  }
}

function buildFinalReportCard(steps) {
  const fieldMap = [
    { field: "Login", id: 1 },
    { field: "Mi Negocio menu", id: 2 },
    { field: "Agregar Negocio modal", id: 3 },
    { field: "Administrar Negocios view", id: 4 },
    { field: "Informacion General", id: 5 },
    { field: "Detalles de la Cuenta", id: 6 },
    { field: "Tus Negocios", id: 7 },
    { field: "Terminos y Condiciones", id: 8 },
    { field: "Politica de Privacidad", id: 9 },
  ];

  return fieldMap.map((item) => {
    const step = steps.find((s) => s.id === item.id);
    return { field: item.field, status: step ? step.status : "FAIL" };
  });
}

async function main() {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.join(process.cwd(), "artifacts", TEST_NAME, timestamp);
  await ensureDir(artifactsDir);

  const report = {
    name: TEST_NAME,
    startedAt: new Date().toISOString(),
    environment: {
      saleadsUrl: process.env.SALEADS_URL || null,
      usedCdpAttach: Boolean(process.env.BROWSER_WS_ENDPOINT),
      headless: process.env.HEADLESS !== "false",
    },
    steps: [],
    finalReport: [],
  };

  const { browser, context, page, attachedToExistingBrowser } = await getExistingOrNewPage();

  try {
    await waitForUi(page);

    await runStep(report, 1, "Login with Google", async (step) => {
      await clickFirstVisible(
        page,
        [
          () => page.getByRole("button", { name: /sign in with google|iniciar sesi[o\u00f3]n con google|continuar con google/i }),
          () => page.getByRole("link", { name: /sign in with google|iniciar sesi[o\u00f3]n con google|continuar con google/i }),
          () => page.getByText(/google/i),
        ],
        "Sign in with Google"
      );

      const googlePopup = await context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded");
        const accountLocator = googlePopup.getByText(new RegExp(TARGET_GOOGLE_ACCOUNT, "i"));
        if (await isVisible(accountLocator, 8000)) {
          await accountLocator.first().click();
          await googlePopup.waitForTimeout(UI_WAIT_MS);
        }
        await googlePopup.waitForEvent("close", { timeout: 20000 }).catch(() => undefined);
      }

      await waitForUi(page);

      const appMainVisible = await isVisible(page.locator("main"), STEP_TIMEOUT_MS);
      addValidation(step, "Main application interface appears", appMainVisible);

      const leftSidebarVisible =
        (await isVisible(page.locator("aside"), 5000)) ||
        (await isVisible(page.getByRole("navigation"), 5000));
      addValidation(step, "Left sidebar navigation is visible", leftSidebarVisible);

      await addScreenshotEvidence(step, page, artifactsDir, "dashboard-loaded");
    });

    await runStep(report, 2, "Open Mi Negocio menu", async (step) => {
      await clickFirstVisible(
        page,
        [
          () => page.getByText(/Negocio/i),
          () => page.getByRole("button", { name: /Negocio/i }),
          () => page.getByRole("link", { name: /Negocio/i }),
        ],
        "Negocio section"
      );

      await clickFirstVisible(
        page,
        [
          () => page.getByText(/Mi Negocio/i),
          () => page.getByRole("button", { name: /Mi Negocio/i }),
          () => page.getByRole("link", { name: /Mi Negocio/i }),
        ],
        "Mi Negocio menu"
      );

      const agregarVisible = await isVisible(page.getByText(/Agregar Negocio/i), STEP_TIMEOUT_MS);
      addValidation(step, "Agregar Negocio is visible", agregarVisible);

      const administrarVisible = await isVisible(page.getByText(/Administrar Negocios/i), STEP_TIMEOUT_MS);
      addValidation(step, "Administrar Negocios is visible", administrarVisible);

      const submenuExpanded = agregarVisible && administrarVisible;
      addValidation(step, "Mi Negocio submenu expands", submenuExpanded);

      await addScreenshotEvidence(step, page, artifactsDir, "mi-negocio-expanded-menu");
    });

    await runStep(report, 3, "Validate Agregar Negocio modal", async (step) => {
      await clickFirstVisible(
        page,
        [
          () => page.getByRole("button", { name: /Agregar Negocio/i }),
          () => page.getByRole("link", { name: /Agregar Negocio/i }),
          () => page.getByText(/Agregar Negocio/i),
        ],
        "Agregar Negocio"
      );

      const modalTitle = page.getByText(/Crear Nuevo Negocio/i);
      addValidation(step, "Modal title 'Crear Nuevo Negocio' is visible", await isVisible(modalTitle, STEP_TIMEOUT_MS));

      const businessNameInput = page.getByLabel(/Nombre del Negocio/i);
      const inputVisible =
        (await isVisible(businessNameInput, 5000)) ||
        (await isVisible(page.getByPlaceholder(/Nombre del Negocio/i), 5000));
      addValidation(step, "Input field 'Nombre del Negocio' exists", inputVisible);

      const quotaTextVisible = await isVisible(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i), STEP_TIMEOUT_MS);
      addValidation(step, "Text 'Tienes 2 de 3 negocios' is visible", quotaTextVisible);

      const cancelVisible = await isVisible(page.getByRole("button", { name: /Cancelar/i }), STEP_TIMEOUT_MS);
      addValidation(step, "Button 'Cancelar' is present", cancelVisible);

      const createVisible = await isVisible(page.getByRole("button", { name: /Crear Negocio/i }), STEP_TIMEOUT_MS);
      addValidation(step, "Button 'Crear Negocio' is present", createVisible);

      await addScreenshotEvidence(step, page, artifactsDir, "agregar-negocio-modal");

      if (inputVisible) {
        if (await isVisible(businessNameInput, 3000)) {
          await businessNameInput.fill("Negocio Prueba Automatizacion");
        } else {
          await page.getByPlaceholder(/Nombre del Negocio/i).fill("Negocio Prueba Automatizacion");
        }
      }

      if (cancelVisible) {
        await page.getByRole("button", { name: /Cancelar/i }).click();
        await waitForUi(page);
      }
    });

    await runStep(report, 4, "Open Administrar Negocios", async (step) => {
      if (!(await isVisible(page.getByText(/Administrar Negocios/i), 2000))) {
        await clickFirstVisible(
          page,
          [
            () => page.getByText(/Mi Negocio/i),
            () => page.getByRole("button", { name: /Mi Negocio/i }),
          ],
          "Re-expand Mi Negocio menu"
        );
      }

      await clickFirstVisible(
        page,
        [
          () => page.getByRole("button", { name: /Administrar Negocios/i }),
          () => page.getByRole("link", { name: /Administrar Negocios/i }),
          () => page.getByText(/Administrar Negocios/i),
        ],
        "Administrar Negocios"
      );

      addValidation(
        step,
        "Section 'Informacion General' exists",
        await isVisible(page.getByText(/Informaci[o\u00f3]n General/i), STEP_TIMEOUT_MS)
      );
      addValidation(
        step,
        "Section 'Detalles de la Cuenta' exists",
        await isVisible(page.getByText(/Detalles de la Cuenta/i), STEP_TIMEOUT_MS)
      );
      addValidation(
        step,
        "Section 'Tus Negocios' exists",
        await isVisible(page.getByText(/Tus Negocios/i), STEP_TIMEOUT_MS)
      );
      addValidation(
        step,
        "Section 'Seccion Legal' exists",
        await isVisible(page.getByText(/Secci[o\u00f3]n Legal/i), STEP_TIMEOUT_MS)
      );

      await addScreenshotEvidence(step, page, artifactsDir, "administrar-negocios-view", { fullPage: true });
    });

    await runStep(report, 5, "Validate Informacion General", async (step) => {
      const emailVisible =
        (await isVisible(page.getByText(new RegExp(TARGET_GOOGLE_ACCOUNT, "i")), 3000)) ||
        (await isVisible(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/), 3000));
      addValidation(step, "User email is visible", emailVisible);

      const infoSection = page.locator("section, div").filter({ hasText: /Informaci[o\u00f3]n General/i }).first();
      let infoText = "";
      if (await isVisible(infoSection, 3000)) {
        infoText = await infoSection.innerText();
      }
      const hasLikelyUserName = /[A-Za-z]{2,}\s+[A-Za-z]{2,}/.test(infoText);
      addValidation(step, "User name is visible", hasLikelyUserName, hasLikelyUserName ? "" : "No clear two-word name found");

      addValidation(step, "Text 'BUSINESS PLAN' is visible", await isVisible(page.getByText(/BUSINESS PLAN/i), STEP_TIMEOUT_MS));
      addValidation(step, "Button 'Cambiar Plan' is visible", await isVisible(page.getByRole("button", { name: /Cambiar Plan/i }), STEP_TIMEOUT_MS));
    });

    await runStep(report, 6, "Validate Detalles de la Cuenta", async (step) => {
      addValidation(step, "'Cuenta creada' is visible", await isVisible(page.getByText(/Cuenta creada/i), STEP_TIMEOUT_MS));
      addValidation(step, "'Estado activo' is visible", await isVisible(page.getByText(/Estado activo/i), STEP_TIMEOUT_MS));
      addValidation(
        step,
        "'Idioma seleccionado' is visible",
        await isVisible(page.getByText(/Idioma seleccionado/i), STEP_TIMEOUT_MS)
      );
    });

    await runStep(report, 7, "Validate Tus Negocios", async (step) => {
      addValidation(step, "Business list is visible", await isVisible(page.getByText(/Tus Negocios/i), STEP_TIMEOUT_MS));
      addValidation(step, "Button 'Agregar Negocio' exists", await isVisible(page.getByRole("button", { name: /Agregar Negocio/i }), STEP_TIMEOUT_MS));
      addValidation(
        step,
        "Text 'Tienes 2 de 3 negocios' is visible",
        await isVisible(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i), STEP_TIMEOUT_MS)
      );
    });

    await runStep(report, 8, "Validate Terminos y Condiciones", async (step) => {
      await validateLegalLink({
        appPage: page,
        context,
        step,
        artifactsDir,
        linkRegex: /T[e\u00e9]rminos y Condiciones/i,
        headingRegex: /T[e\u00e9]rminos y Condiciones/i,
        screenshotLabel: "terminos-y-condiciones",
      });
    });

    await runStep(report, 9, "Validate Politica de Privacidad", async (step) => {
      await validateLegalLink({
        appPage: page,
        context,
        step,
        artifactsDir,
        linkRegex: /Pol[i\u00ed]tica de Privacidad/i,
        headingRegex: /Pol[i\u00ed]tica de Privacidad/i,
        screenshotLabel: "politica-de-privacidad",
      });
    });

    report.finalReport = buildFinalReportCard(report.steps);
    report.endedAt = new Date().toISOString();

    const allPassed = report.finalReport.every((item) => item.status === "PASS");
    report.status = allPassed ? "PASS" : "FAIL";

    const jsonReportPath = path.join(artifactsDir, "report.json");
    await fs.writeFile(jsonReportPath, JSON.stringify(report, null, 2), "utf8");

    const summaryLines = [
      `# ${TEST_NAME}`,
      "",
      `- Overall: **${report.status}**`,
      `- Started: ${report.startedAt}`,
      `- Ended: ${report.endedAt}`,
      "",
      "## Final Report",
      "",
      "| Checkpoint | Result |",
      "| --- | --- |",
      ...report.finalReport.map((item) => `| ${item.field} | ${item.status} |`),
      "",
      `JSON report: \`${jsonReportPath}\``,
    ];
    const markdownReportPath = path.join(artifactsDir, "report.md");
    await fs.writeFile(markdownReportPath, `${summaryLines.join("\n")}\n`, "utf8");

    console.log(`Workflow finished with status: ${report.status}`);
    console.log(`Artifacts directory: ${artifactsDir}`);
    console.log(`Report JSON: ${jsonReportPath}`);
    console.log(`Report Markdown: ${markdownReportPath}`);
  } finally {
    if (!attachedToExistingBrowser) {
      await browser.close();
    }
  }
}

main().catch((error) => {
  console.error("Fatal test execution error:", error);
  process.exitCode = 1;
});
