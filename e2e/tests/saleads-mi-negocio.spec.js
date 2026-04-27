const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const OUTPUT_DIR = path.resolve(process.cwd(), "e2e", "artifacts", "saleads-mi-negocio");
const SCREENSHOTS_DIR = path.join(OUTPUT_DIR, "screenshots");
const CHECK_TIMEOUT_MS = Number(process.env.SALEADS_CHECK_TIMEOUT_MS || 7000);

const googleAccountEmail = process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const appUrl = process.env.SALEADS_URL;

function ensureOutputDirs() {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

function normalizeName(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUiLoad(page) {
  // Avoid hard dependency on networkidle because SPAs can keep network busy.
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => null);
  const possibleLoaders = page.locator(
    '[aria-busy="true"], [role="progressbar"], .loading, .loader, .spinner, [data-testid*="loading"]'
  );
  await possibleLoaders.first().waitFor({ state: "hidden", timeout: 5000 }).catch(() => null);
  await page.waitForTimeout(750);
}

async function clickAndWait(target) {
  const page = target.page();
  await target.scrollIntoViewIfNeeded();
  await target.click();
  await waitForUiLoad(page);
}

function stepRecord(steps, key, title) {
  if (!steps[key]) {
    steps[key] = {
      title,
      pass: true,
      checks: [],
      evidence: [],
      details: []
    };
  }
  return steps[key];
}

function addCheck(steps, key, title, name, pass, detail) {
  const rec = stepRecord(steps, key, title);
  rec.checks.push({ name, pass, detail: detail || "" });
  if (!pass) {
    rec.pass = false;
  }
}

function addEvidence(steps, key, title, label, value) {
  const rec = stepRecord(steps, key, title);
  rec.evidence.push({ label, value });
}

function addDetail(steps, key, title, detail) {
  const rec = stepRecord(steps, key, title);
  rec.details.push(detail);
}

async function captureScreenshot(page, steps, key, title, checkpointName, options = {}) {
  const file = `${checkpointName}.png`;
  const screenshotPath = path.join(SCREENSHOTS_DIR, file);
  await page.screenshot({ path: screenshotPath, fullPage: !!options.fullPage });
  addEvidence(steps, key, title, `screenshot:${checkpointName}`, screenshotPath);
  return screenshotPath;
}

async function expectVisibleAndRecord(steps, key, title, locator, checkName) {
  try {
    await expect(locator).toBeVisible({ timeout: CHECK_TIMEOUT_MS });
    addCheck(steps, key, title, checkName, true);
    return true;
  } catch (error) {
    addCheck(steps, key, title, checkName, false, error.message);
    return false;
  }
}

async function findFirstVisible(page, candidates) {
  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }
  return null;
}

async function clickAny(page, steps, key, title, candidates, checkName) {
  const locator = await findFirstVisible(page, candidates);
  if (!locator) {
    addCheck(steps, key, title, checkName, false, "No matching visible locator found");
    return false;
  }
  await clickAndWait(locator);
  addCheck(steps, key, title, checkName, true);
  return true;
}

async function openLegalLink(page, steps, key, title, linkText, screenshotName) {
  const link = page.getByRole("link", { name: new RegExp(linkText, "i") });
  if (!(await link.isVisible().catch(() => false))) {
    addCheck(steps, key, title, `Link "${linkText}" visible`, false, "Link is not visible");
    return null;
  }
  addCheck(steps, key, title, `Link "${linkText}" visible`, true);

  const context = page.context();
  const previousPages = context.pages().length;

  await link.scrollIntoViewIfNeeded();
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  const currentUrlBeforeClick = page.url();
  await link.click();
  await waitForUiLoad(page);

  const popup = await popupPromise;
  const activePage = popup || page;
  await waitForUiLoad(activePage);

  if (popup) {
    addDetail(steps, key, title, `New tab opened for ${linkText}.`);
  } else {
    addDetail(steps, key, title, `Navigation stayed on current tab for ${linkText}.`);
  }

  const heading = activePage.getByRole("heading", { name: new RegExp(linkText, "i") });
  await expectVisibleAndRecord(steps, key, title, heading, `Heading "${linkText}" visible`);

  const legalContentCandidates = [
    activePage.getByText(/(condiciones|privacidad|datos personales|usuario|servicio)/i),
    activePage.locator("main"),
    activePage.locator("article")
  ];
  const legalContent = await findFirstVisible(activePage, legalContentCandidates);
  addCheck(
    steps,
    key,
    title,
    "Legal content text visible",
    !!legalContent,
    legalContent ? "" : "No visible legal content locator found"
  );

  await captureScreenshot(activePage, steps, key, title, screenshotName, { fullPage: true });
  addEvidence(steps, key, title, "final_url", activePage.url());

  if (popup) {
    await popup.close();
    await page.bringToFront();
    const currentPages = context.pages().length;
    addDetail(
      steps,
      key,
      title,
      `Closed legal tab and returned to app tab (pages before: ${previousPages}, after close: ${currentPages}).`
    );
  } else {
    await page.goBack().catch(() => null);
    await waitForUiLoad(page);
    addDetail(
      steps,
      key,
      title,
      `Returned to app page after same-tab legal navigation (from: ${currentUrlBeforeClick}).`
    );
  }

  return activePage.url();
}

function buildSummary(steps) {
  return {
    Login: steps.login?.pass ?? false,
    "Mi Negocio menu": steps.miNegocioMenu?.pass ?? false,
    "Agregar Negocio modal": steps.agregarNegocioModal?.pass ?? false,
    "Administrar Negocios view": steps.administrarNegocios?.pass ?? false,
    "Información General": steps.informacionGeneral?.pass ?? false,
    "Detalles de la Cuenta": steps.detallesCuenta?.pass ?? false,
    "Tus Negocios": steps.tusNegocios?.pass ?? false,
    "Términos y Condiciones": steps.terminos?.pass ?? false,
    "Política de Privacidad": steps.privacidad?.pass ?? false
  };
}

function markStepNotExecuted(steps, key, title, reason) {
  addCheck(steps, key, title, "Step executed", false, reason);
}

function writeFinalReports(page, steps) {
  const summary = buildSummary(steps);
  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      saleadsUrlProvided: Boolean(appUrl),
      currentUrl: page.url(),
      googleAccountEmail
    },
    summary,
    steps
  };

  const reportFile = path.join(OUTPUT_DIR, "final-report.json");
  fs.writeFileSync(reportFile, JSON.stringify(finalReport, null, 2), "utf8");

  const markdownLines = [
    "# SaleADS Mi Negocio - Final Report",
    "",
    `Generated at: ${finalReport.generatedAt}`,
    "",
    "## PASS/FAIL Summary",
    ""
  ];

  for (const [label, pass] of Object.entries(summary)) {
    markdownLines.push(`- ${label}: ${pass ? "PASS" : "FAIL"}`);
  }

  markdownLines.push("", `JSON report: ${reportFile}`);
  markdownLines.push(`Screenshots folder: ${SCREENSHOTS_DIR}`);
  fs.writeFileSync(path.join(OUTPUT_DIR, "final-report.md"), markdownLines.join("\n"), "utf8");

  return summary;
}

test.describe("SaleADS Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }) => {
    ensureOutputDirs();
    const steps = {};

    stepRecord(steps, "login", "Login");
    stepRecord(steps, "miNegocioMenu", "Mi Negocio menu");
    stepRecord(steps, "agregarNegocioModal", "Agregar Negocio modal");
    stepRecord(steps, "administrarNegocios", "Administrar Negocios view");
    stepRecord(steps, "informacionGeneral", "Información General");
    stepRecord(steps, "detallesCuenta", "Detalles de la Cuenta");
    stepRecord(steps, "tusNegocios", "Tus Negocios");
    stepRecord(steps, "terminos", "Términos y Condiciones");
    stepRecord(steps, "privacidad", "Política de Privacidad");

    if (appUrl) {
      await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUiLoad(page);

    const currentUrl = page.url();
    if (!appUrl && currentUrl === "about:blank") {
      addCheck(
        steps,
        "login",
        "Login",
        "Login page available",
        false,
        "No SALEADS_URL provided and browser started on about:blank. Provide SALEADS_URL in headless/CI mode."
      );
      markStepNotExecuted(
        steps,
        "miNegocioMenu",
        "Mi Negocio menu",
        "Not executed because login precondition failed."
      );
      markStepNotExecuted(
        steps,
        "agregarNegocioModal",
        "Agregar Negocio modal",
        "Not executed because login precondition failed."
      );
      markStepNotExecuted(
        steps,
        "administrarNegocios",
        "Administrar Negocios view",
        "Not executed because login precondition failed."
      );
      markStepNotExecuted(
        steps,
        "informacionGeneral",
        "Información General",
        "Not executed because login precondition failed."
      );
      markStepNotExecuted(
        steps,
        "detallesCuenta",
        "Detalles de la Cuenta",
        "Not executed because login precondition failed."
      );
      markStepNotExecuted(
        steps,
        "tusNegocios",
        "Tus Negocios",
        "Not executed because login precondition failed."
      );
      markStepNotExecuted(
        steps,
        "terminos",
        "Términos y Condiciones",
        "Not executed because login precondition failed."
      );
      markStepNotExecuted(
        steps,
        "privacidad",
        "Política de Privacidad",
        "Not executed because login precondition failed."
      );
      const summary = writeFinalReports(page, steps);
      const allPassed = Object.values(summary).every(Boolean);
      expect(allPassed).toBeTruthy();
      return;
    }

    const loginClicked = await clickAny(
      page,
      steps,
      "login",
      "Login",
      [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /login with google/i }),
        page.getByRole("button", { name: /continuar con google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/continuar con google/i)
      ],
      "Click login / Google button"
    );

    if (loginClicked) {
      const googleAccountEntry = page
        .context()
        .pages()
        .flatMap((p) => [p.getByText(new RegExp(googleAccountEmail, "i")), p.getByRole("button", { name: new RegExp(googleAccountEmail, "i") })]);

      for (const accountLocator of googleAccountEntry) {
        if (await accountLocator.isVisible().catch(() => false)) {
          await clickAndWait(accountLocator);
          addCheck(steps, "login", "Login", `Selected Google account ${googleAccountEmail}`, true);
          break;
        }
      }
    }

    const sidebarVisible = await expectVisibleAndRecord(
      steps,
      "login",
      "Login",
      page.locator("aside").or(page.getByRole("navigation")),
      "Left sidebar navigation is visible"
    );

    const appInterfaceVisible = await expectVisibleAndRecord(
      steps,
      "login",
      "Login",
      page.locator("main").or(page.getByRole("main")),
      "Main application interface appears"
    );

    if (sidebarVisible || appInterfaceVisible) {
      await captureScreenshot(page, steps, "login", "Login", "01-dashboard-loaded");
    }

    await clickAny(
      page,
      steps,
      "miNegocioMenu",
      "Mi Negocio menu",
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/^mi negocio$/i),
        page.getByText(/negocio/i)
      ],
      "Open Mi Negocio menu"
    );

    await expectVisibleAndRecord(
      steps,
      "miNegocioMenu",
      "Mi Negocio menu",
      page.getByText(/agregar negocio/i),
      "Agregar Negocio is visible"
    );
    await expectVisibleAndRecord(
      steps,
      "miNegocioMenu",
      "Mi Negocio menu",
      page.getByText(/administrar negocios/i),
      "Administrar Negocios is visible"
    );
    const submenuExpanded =
      (steps.miNegocioMenu.checks.find((check) => check.name === "Agregar Negocio is visible")?.pass ?? false) &&
      (steps.miNegocioMenu.checks.find((check) => check.name === "Administrar Negocios is visible")?.pass ?? false);
    addCheck(
      steps,
      "miNegocioMenu",
      "Mi Negocio menu",
      "Submenu expands",
      submenuExpanded,
      submenuExpanded ? "" : "Submenu did not expose expected options."
    );
    await captureScreenshot(page, steps, "miNegocioMenu", "Mi Negocio menu", "02-mi-negocio-expanded");

    const agregarNegocioClicked = await clickAny(
      page,
      steps,
      "agregarNegocioModal",
      "Agregar Negocio modal",
      [
        page.getByRole("menuitem", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByText(/^agregar negocio$/i)
      ],
      "Click Agregar Negocio"
    );

    if (agregarNegocioClicked) {
      await expectVisibleAndRecord(
        steps,
        "agregarNegocioModal",
        "Agregar Negocio modal",
        page.getByText(/crear nuevo negocio/i),
        "Modal title \"Crear Nuevo Negocio\" is visible"
      );
      await expectVisibleAndRecord(
        steps,
        "agregarNegocioModal",
        "Agregar Negocio modal",
        page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i)),
        "Input \"Nombre del Negocio\" exists"
      );
      await expectVisibleAndRecord(
        steps,
        "agregarNegocioModal",
        "Agregar Negocio modal",
        page.getByText(/tienes 2 de 3 negocios/i),
        "Text \"Tienes 2 de 3 negocios\" is visible"
      );
      await expectVisibleAndRecord(
        steps,
        "agregarNegocioModal",
        "Agregar Negocio modal",
        page.getByRole("button", { name: /cancelar/i }),
        "Button \"Cancelar\" is present"
      );
      await expectVisibleAndRecord(
        steps,
        "agregarNegocioModal",
        "Agregar Negocio modal",
        page.getByRole("button", { name: /crear negocio/i }),
        "Button \"Crear Negocio\" is present"
      );

      const nombreField = await findFirstVisible(page, [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i)
      ]);
      if (nombreField) {
        await nombreField.click();
        await nombreField.fill("Negocio Prueba Automatización");
        addCheck(steps, "agregarNegocioModal", "Agregar Negocio modal", "Typed test business name", true);
      }

      await captureScreenshot(page, steps, "agregarNegocioModal", "Agregar Negocio modal", "03-agregar-negocio-modal");

      const cancelButton = page.getByRole("button", { name: /cancelar/i });
      if (await cancelButton.isVisible().catch(() => false)) {
        await clickAndWait(cancelButton);
        addCheck(steps, "agregarNegocioModal", "Agregar Negocio modal", "Closed modal with Cancelar", true);
      }
    }

    await clickAny(
      page,
      steps,
      "administrarNegocios",
      "Administrar Negocios view",
      [
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByText(/^administrar negocios$/i)
      ],
      "Open Administrar Negocios"
    );

    await expectVisibleAndRecord(
      steps,
      "administrarNegocios",
      "Administrar Negocios view",
      page.getByText(/informaci[oó]n general/i),
      "Section \"Información General\" exists"
    );
    await expectVisibleAndRecord(
      steps,
      "administrarNegocios",
      "Administrar Negocios view",
      page.getByText(/detalles de la cuenta/i),
      "Section \"Detalles de la Cuenta\" exists"
    );
    await expectVisibleAndRecord(
      steps,
      "administrarNegocios",
      "Administrar Negocios view",
      page.getByText(/tus negocios/i),
      "Section \"Tus Negocios\" exists"
    );
    await expectVisibleAndRecord(
      steps,
      "administrarNegocios",
      "Administrar Negocios view",
      page.getByText(/secci[oó]n legal/i),
      "Section \"Sección Legal\" exists"
    );
    await captureScreenshot(page, steps, "administrarNegocios", "Administrar Negocios view", "04-administrar-negocios", { fullPage: true });

    await expectVisibleAndRecord(
      steps,
      "informacionGeneral",
      "Información General",
      page.getByText(/@/),
      "User email is visible"
    );
    const userNameCandidate = await findFirstVisible(page, [
      page.getByText(/bienvenido|hola/i),
      page.locator("section").filter({ hasText: /informaci[oó]n general/i }).locator("h1, h2, h3, p")
    ]);
    addCheck(
      steps,
      "informacionGeneral",
      "Información General",
      "User name is visible",
      !!userNameCandidate,
      userNameCandidate ? "" : "Could not identify a visible user name element"
    );
    await expectVisibleAndRecord(
      steps,
      "informacionGeneral",
      "Información General",
      page.getByText(/business plan/i),
      "Text \"BUSINESS PLAN\" is visible"
    );
    await expectVisibleAndRecord(
      steps,
      "informacionGeneral",
      "Información General",
      page.getByRole("button", { name: /cambiar plan/i }),
      "Button \"Cambiar Plan\" is visible"
    );

    await expectVisibleAndRecord(
      steps,
      "detallesCuenta",
      "Detalles de la Cuenta",
      page.getByText(/cuenta creada/i),
      "\"Cuenta creada\" is visible"
    );
    await expectVisibleAndRecord(
      steps,
      "detallesCuenta",
      "Detalles de la Cuenta",
      page.getByText(/estado activo/i),
      "\"Estado activo\" is visible"
    );
    await expectVisibleAndRecord(
      steps,
      "detallesCuenta",
      "Detalles de la Cuenta",
      page.getByText(/idioma seleccionado/i),
      "\"Idioma seleccionado\" is visible"
    );

    const businessList = await findFirstVisible(page, [
      page.getByRole("table"),
      page.getByRole("list"),
      page.locator("section").filter({ hasText: /tus negocios/i })
    ]);
    addCheck(
      steps,
      "tusNegocios",
      "Tus Negocios",
      "Business list is visible",
      !!businessList,
      businessList ? "" : "Could not identify business list container"
    );
    await expectVisibleAndRecord(
      steps,
      "tusNegocios",
      "Tus Negocios",
      page.getByRole("button", { name: /agregar negocio/i }).or(page.getByRole("link", { name: /agregar negocio/i })),
      "Button \"Agregar Negocio\" exists"
    );
    await expectVisibleAndRecord(
      steps,
      "tusNegocios",
      "Tus Negocios",
      page.getByText(/tienes 2 de 3 negocios/i),
      "Text \"Tienes 2 de 3 negocios\" is visible"
    );

    await openLegalLink(page, steps, "terminos", "Términos y Condiciones", "Términos y Condiciones", "05-terminos-condiciones");
    await openLegalLink(page, steps, "privacidad", "Política de Privacidad", "Política de Privacidad", "06-politica-privacidad");

    const summary = writeFinalReports(page, steps);
    const allPassed = Object.values(summary).every(Boolean);
    expect(allPassed).toBeTruthy();
  });
});
