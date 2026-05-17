const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const STEP_NAMES = [
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

const ARTIFACTS_DIR = path.join(__dirname, "..", "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "saleads_mi_negocio_full_report.json");

function createReport() {
  const results = {};
  for (const stepName of STEP_NAMES) {
    results[stepName] = {
      status: "NOT_RUN",
      notes: [],
      evidence: [],
    };
  }

  return {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    target: process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL || null,
    results,
  };
}

function toRegex(value) {
  if (value instanceof RegExp) {
    return value;
  }

  const escaped = value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(escaped, "i");
}

function sanitizeFilename(value) {
  return value.replace(/[^a-zA-Z0-9-_]+/g, "-").replace(/-+/g, "-").replace(/^-|-$/g, "").toLowerCase();
}

function addNote(report, step, note) {
  report.results[step].notes.push(note);
}

function addEvidence(report, step, evidence) {
  report.results[step].evidence.push(evidence);
}

function setStepStatus(report, step, status, error) {
  report.results[step].status = status;
  if (error) {
    report.results[step].error = error instanceof Error ? error.message : String(error);
  }
}

async function ensureArtifactsDirs() {
  await fs.mkdir(SCREENSHOTS_DIR, { recursive: true });
}

async function writeReport(report) {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
  await fs.writeFile(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 5000 });
  } catch {
    // Some apps keep network activity alive; do not fail the flow on this.
  }
}

async function captureScreenshot(page, checkpointName, fullPage = false) {
  const filename = `${Date.now()}-${sanitizeFilename(checkpointName)}.png`;
  const screenshotPath = path.join(SCREENSHOTS_DIR, filename);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function assertNoInfrastructureError(page, checkpointName) {
  const errorMarker = await getFirstVisible(
    [
      page.getByText(/ssl handshake failed/i),
      page.getByText(/error code\s*525/i),
      page.getByText(/host error/i),
      page.getByText(/cloudflare/i),
      page.getByText(/bad gateway|502/i),
    ],
    1000
  );

  if (!errorMarker) {
    return;
  }

  const screenshotPath = await captureScreenshot(page, `infra-error-${checkpointName}`, true);
  throw new Error(
    `Infrastructure error page detected at '${checkpointName}'. See screenshot: ${screenshotPath}`
  );
}

async function getFirstVisible(locators, timeoutMs = 3000) {
  for (const locator of locators) {
    const first = locator.first();
    try {
      await first.waitFor({ state: "visible", timeout: timeoutMs });
      return first;
    } catch {
      // try next locator
    }
  }
  return null;
}

async function expectAnyVisible(locators, errorMessage, timeoutMs = 5000) {
  const locator = await getFirstVisible(locators, timeoutMs);
  expect(locator, errorMessage).toBeTruthy();
  return locator;
}

function buildTextLocators(root, pattern) {
  const regex = toRegex(pattern);
  return [
    root.getByRole("button", { name: regex }),
    root.getByRole("link", { name: regex }),
    root.getByRole("menuitem", { name: regex }),
    root.getByRole("tab", { name: regex }),
    root.getByText(regex),
  ];
}

async function clickByVisibleText(page, labels, options = {}) {
  const root = options.root || page;
  const timeoutMs = options.timeoutMs || 5000;

  for (const label of labels) {
    const locators = buildTextLocators(root, label);
    const target = await getFirstVisible(locators, timeoutMs);
    if (!target) {
      continue;
    }

    await target.click();
    await waitForUiLoad(page);
    return true;
  }

  throw new Error(`Could not find clickable element with labels: ${labels.map(String).join(", ")}`);
}

async function tryClickByVisibleText(page, labels, options = {}) {
  try {
    return await clickByVisibleText(page, labels, options);
  } catch {
    return false;
  }
}

async function runStep(report, stepName, action) {
  try {
    await action();
    setStepStatus(report, stepName, "PASS");
  } catch (error) {
    setStepStatus(report, stepName, "FAIL", error);
  }
}

function ensureStepPassed(report, requiredStepName, currentStepName) {
  if (report.results[requiredStepName].status === "PASS") {
    return;
  }

  const reason = report.results[requiredStepName].error || report.results[requiredStepName].status;
  throw new Error(
    `Blocked: '${currentStepName}' requires '${requiredStepName}' to pass first. Upstream result: ${reason}`
  );
}

async function openLegalLinkAndValidate({
  page,
  context,
  linkLabel,
  headingLabel,
  report,
  stepName,
  fallbackReturnUrl,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await clickByVisibleText(page, [linkLabel], { timeoutMs: 6000 });
  const popup = await popupPromise;

  let legalPage = page;
  if (popup) {
    legalPage = popup;
    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUiLoad(legalPage);
  } else {
    await waitForUiLoad(page);
  }

  await expectAnyVisible(
    [
      legalPage.getByRole("heading", { name: toRegex(headingLabel) }),
      legalPage.getByText(toRegex(headingLabel)),
    ],
    `No se encontró el encabezado legal: ${headingLabel}`,
    15000
  );

  await expectAnyVisible(
    [
      legalPage.locator("main p"),
      legalPage.locator("article p"),
      legalPage.locator("body p"),
      legalPage.getByText(/\w{4,}/),
    ],
    "No se encontró contenido legal visible",
    12000
  );

  const legalScreenshot = await captureScreenshot(legalPage, `legal-${stepName}`, true);
  const finalUrl = legalPage.url();
  addEvidence(report, stepName, { type: "screenshot", path: legalScreenshot });
  addEvidence(report, stepName, { type: "url", value: finalUrl });
  addNote(report, stepName, `Final URL: ${finalUrl}`);

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiLoad(page);
    return;
  }

  const navigatedBack = await page
    .goBack({ waitUntil: "domcontentloaded", timeout: 20000 })
    .then(() => true)
    .catch(() => false);

  if (!navigatedBack && fallbackReturnUrl) {
    await page.goto(fallbackReturnUrl, { waitUntil: "domcontentloaded" });
  }
  await waitForUiLoad(page);
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  test.setTimeout(10 * 60 * 1000);
  const report = createReport();
  let accountPageUrl = null;

  await ensureArtifactsDirs();

  await runStep(report, "Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
      await assertNoInfrastructureError(page, "login-page-open");
      addNote(report, "Login", `Opened login URL from environment variable: ${loginUrl}`);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Browser started on about:blank. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL) to a SaleADS login page URL."
      );
    }

    const googlePopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickByVisibleText(page, [/sign in with google/i, /iniciar sesi[oó]n con google/i, /google/i], {
      timeoutMs: 10000,
    });

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      const popupAccount = googlePopup.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      const popupHasAccount = await getFirstVisible([popupAccount], 8000);
      if (popupHasAccount) {
        await popupHasAccount.click();
        addNote(report, "Login", "Selected target Google account in popup selector.");
      }
      await googlePopup.waitForEvent("close", { timeout: 45000 }).catch(() => undefined);
      await page.bringToFront();
    } else {
      const samePageAccount = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      const accountOption = await getFirstVisible([samePageAccount], 7000);
      if (accountOption) {
        await accountOption.click();
        addNote(report, "Login", "Selected target Google account in same tab selector.");
      }
    }

    await waitForUiLoad(page);
    await assertNoInfrastructureError(page, "after-google-login-click");

    await expectAnyVisible(
      [page.getByText(/dashboard|panel|inicio|home/i), page.locator("main")],
      "No se detectó la interfaz principal tras el login",
      20000
    );

    await expectAnyVisible(
      [page.locator("aside"), page.getByRole("navigation"), page.locator('[class*="sidebar"]')],
      "No se detectó navegación lateral",
      12000
    );

    await expectAnyVisible(
      [
        page.getByText(/mi negocio/i),
        page.getByText(/negocio/i),
        page.getByText(/administrar negocios/i),
        page.getByText(/agregar negocio/i),
      ],
      "No se detectó el menú de negocio después del login",
      12000
    );

    const dashboardShot = await captureScreenshot(page, "01-dashboard-loaded", true);
    addEvidence(report, "Login", { type: "screenshot", path: dashboardShot });
  });

  await runStep(report, "Mi Negocio menu", async () => {
    ensureStepPassed(report, "Login", "Mi Negocio menu");

    const clickedMiNegocioDirectly = await tryClickByVisibleText(page, [/^mi negocio$/i, /mi negocio/i], {
      timeoutMs: 6000,
    });

    if (!clickedMiNegocioDirectly) {
      await clickByVisibleText(page, [/^negocio$/i, /negocio/i], { timeoutMs: 6000 });
      await clickByVisibleText(page, [/^mi negocio$/i, /mi negocio/i], { timeoutMs: 6000 });
    }

    await expectAnyVisible([page.getByText(/agregar negocio/i)], "No se visualiza 'Agregar Negocio'");
    await expectAnyVisible([page.getByText(/administrar negocios/i)], "No se visualiza 'Administrar Negocios'");

    const expandedMenuShot = await captureScreenshot(page, "02-mi-negocio-menu-expanded");
    addEvidence(report, "Mi Negocio menu", { type: "screenshot", path: expandedMenuShot });
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    ensureStepPassed(report, "Mi Negocio menu", "Agregar Negocio modal");
    await clickByVisibleText(page, [/^agregar negocio$/i, /agregar negocio/i], { timeoutMs: 6000 });

    await expectAnyVisible(
      [page.getByRole("heading", { name: /crear nuevo negocio/i }), page.getByText(/crear nuevo negocio/i)],
      "No se abrió el modal 'Crear Nuevo Negocio'",
      12000
    );

    const nombreInput = await expectAnyVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator('input[name*="nombre"], input[placeholder*="Nombre"]'),
      ],
      "No se encontró el campo 'Nombre del Negocio'"
    );

    await expectAnyVisible(
      [page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)],
      "No se visualiza 'Tienes 2 de 3 negocios'"
    );
    await expectAnyVisible([page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)], "No se visualiza botón 'Cancelar'");
    await expectAnyVisible(
      [page.getByRole("button", { name: /crear negocio/i }), page.getByText(/crear negocio/i)],
      "No se visualiza botón 'Crear Negocio'"
    );

    const modalShot = await captureScreenshot(page, "03-agregar-negocio-modal");
    addEvidence(report, "Agregar Negocio modal", { type: "screenshot", path: modalShot });

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, [/^cancelar$/i], { timeoutMs: 6000 });
    await page
      .getByText(/crear nuevo negocio/i)
      .first()
      .waitFor({ state: "hidden", timeout: 12000 })
      .catch(() => undefined);
  });

  await runStep(report, "Administrar Negocios view", async () => {
    ensureStepPassed(report, "Mi Negocio menu", "Administrar Negocios view");

    const adminVisible = await getFirstVisible([page.getByText(/administrar negocios/i)], 2500);
    if (!adminVisible) {
      await clickByVisibleText(page, [/^mi negocio$/i, /mi negocio/i], { timeoutMs: 6000 });
    }

    await clickByVisibleText(page, [/administrar negocios/i], { timeoutMs: 8000 });
    await waitForUiLoad(page);

    await expectAnyVisible(
      [page.getByRole("heading", { name: /informaci[oó]n general/i }), page.getByText(/informaci[oó]n general/i)],
      "No se encontró sección 'Información General'",
      12000
    );
    await expectAnyVisible(
      [page.getByRole("heading", { name: /detalles de la cuenta/i }), page.getByText(/detalles de la cuenta/i)],
      "No se encontró sección 'Detalles de la Cuenta'",
      12000
    );
    await expectAnyVisible(
      [page.getByRole("heading", { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      "No se encontró sección 'Tus Negocios'",
      12000
    );
    await expectAnyVisible(
      [page.getByRole("heading", { name: /secci[oó]n legal/i }), page.getByText(/secci[oó]n legal/i)],
      "No se encontró sección 'Sección Legal'",
      12000
    );

    accountPageUrl = page.url();
    addNote(report, "Administrar Negocios view", `Account page URL: ${accountPageUrl}`);

    const accountPageShot = await captureScreenshot(page, "04-administrar-negocios-view", true);
    addEvidence(report, "Administrar Negocios view", { type: "screenshot", path: accountPageShot });
  });

  await runStep(report, "Información General", async () => {
    ensureStepPassed(report, "Administrar Negocios view", "Información General");

    await expectAnyVisible(
      [page.getByText(/juanlucasbarbiergarzon@gmail\.com/i), page.getByText(/@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)],
      "No se visualiza el email del usuario",
      8000
    );

    await expectAnyVisible(
      [page.getByText(/juan|lucas|barbier|garzon/i), page.getByText(/nombre/i)],
      "No se visualiza un nombre de usuario",
      8000
    );

    await expectAnyVisible([page.getByText(/business plan/i)], "No se visualiza 'BUSINESS PLAN'", 8000);
    await expectAnyVisible(
      [page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)],
      "No se visualiza botón 'Cambiar Plan'",
      8000
    );
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    ensureStepPassed(report, "Administrar Negocios view", "Detalles de la Cuenta");

    await expectAnyVisible([page.getByText(/cuenta creada/i)], "No se visualiza 'Cuenta creada'");
    await expectAnyVisible([page.getByText(/estado activo/i)], "No se visualiza 'Estado activo'");
    await expectAnyVisible([page.getByText(/idioma seleccionado/i)], "No se visualiza 'Idioma seleccionado'");
  });

  await runStep(report, "Tus Negocios", async () => {
    ensureStepPassed(report, "Administrar Negocios view", "Tus Negocios");

    await expectAnyVisible(
      [page.locator('section:has-text("Tus Negocios") ul li'), page.locator('section:has-text("Tus Negocios") table tr').nth(1), page.locator('[class*="business"]')],
      "No se visualiza una lista de negocios",
      12000
    );

    await expectAnyVisible(
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/^agregar negocio$/i)],
      "No se visualiza botón 'Agregar Negocio'"
    );
    await expectAnyVisible(
      [page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)],
      "No se visualiza texto 'Tienes 2 de 3 negocios'"
    );
  });

  await runStep(report, "Términos y Condiciones", async () => {
    ensureStepPassed(report, "Administrar Negocios view", "Términos y Condiciones");

    await openLegalLinkAndValidate({
      page,
      context,
      linkLabel: /t[ée]rminos y condiciones/i,
      headingLabel: /t[ée]rminos y condiciones/i,
      report,
      stepName: "Términos y Condiciones",
      fallbackReturnUrl: accountPageUrl,
    });
  });

  await runStep(report, "Política de Privacidad", async () => {
    ensureStepPassed(report, "Administrar Negocios view", "Política de Privacidad");

    await openLegalLinkAndValidate({
      page,
      context,
      linkLabel: /pol[íi]tica de privacidad/i,
      headingLabel: /pol[íi]tica de privacidad/i,
      report,
      stepName: "Política de Privacidad",
      fallbackReturnUrl: accountPageUrl,
    });
  });

  await writeReport(report);

  const failingSteps = Object.entries(report.results)
    .filter(([, result]) => result.status !== "PASS")
    .map(([stepName, result]) => `${stepName}: ${result.error || result.status}`);

  expect(
    failingSteps,
    `Validation failures detected. Full report at ${REPORT_PATH}\n${failingSteps.join("\n")}`
  ).toEqual([]);
});
