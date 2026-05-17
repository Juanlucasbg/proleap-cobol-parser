const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const REPORT_FIELDS = [
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

function makeInitialReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = {
      status: "FAIL",
      details: "Not executed."
    };
  }
  return report;
}

function markPass(report, field, details = "All validations passed.") {
  report[field] = { status: "PASS", details };
}

function markFail(report, field, error) {
  const details = error instanceof Error ? error.message : String(error);
  report[field] = { status: "FAIL", details };
}

function ensureDir(directoryPath) {
  fs.mkdirSync(directoryPath, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 15000 });
  } catch {
    // Network idle may not fire in SPAs with persistent connections.
  }
}

async function firstVisible(locator) {
  const count = await locator.count();
  for (let index = 0; index < count; index += 1) {
    const candidate = locator.nth(index);
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }
  throw new Error("No visible element found for the requested locator.");
}

async function clickByVisibleText(page, regex) {
  const byRoleButton = page.getByRole("button", { name: regex });
  const byRoleLink = page.getByRole("link", { name: regex });
  const byText = page.getByText(regex);

  const strategies = [byRoleButton, byRoleLink, byText];
  for (const locator of strategies) {
    if ((await locator.count()) > 0) {
      const target = await firstVisible(locator);
      await target.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Could not find clickable visible element for: ${regex}`);
}

async function screenshotCheckpoint(page, directory, name, fullPage = false) {
  const checkpointPath = path.join(directory, name);
  await page.screenshot({
    path: checkpointPath,
    fullPage
  });
  return checkpointPath;
}

async function runStep(report, field, action) {
  try {
    await action();
    markPass(report, field);
  } catch (error) {
    markFail(report, field, error);
    throw error;
  }
}

function markNotExecutedAsBlocked(report) {
  for (const field of REPORT_FIELDS) {
    if (report[field].details === "Not executed.") {
      report[field] = {
        status: "FAIL",
        details: "Not executed due to previous step failure."
      };
    }
  }
}

async function openLegalDocumentAndReturn({
  context,
  appPage,
  checkpointsDir,
  linkTextRegex,
  headingRegex,
  screenshotName
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await clickByVisibleText(appPage, linkTextRegex);
  const popupPage = await popupPromise;

  let legalPage = appPage;
  if (popupPage) {
    legalPage = popupPage;
    await waitForUi(legalPage);
  }

  await expect(legalPage.getByText(headingRegex).first()).toBeVisible();
  const legalContent = legalPage.locator("main, article, section, p, div").filter({ hasText: /./ }).first();
  await expect(legalContent).toBeVisible();

  const screenshotPath = await screenshotCheckpoint(legalPage, checkpointsDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack().catch(() => null);
    await waitForUi(appPage);
  }

  return { screenshotPath, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const rootResultsDir = path.resolve(__dirname, "..", "test-results");
  const checkpointsDir = path.join(rootResultsDir, "checkpoints");
  ensureDir(rootResultsDir);
  ensureDir(checkpointsDir);

  const report = makeInitialReport();
  const evidence = {};
  const legalUrls = {};

  const startUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;

  let workflowError = null;

  try {
    await runStep(report, "Login", async () => {
      if (startUrl) {
        await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      } else if (page.url() === "about:blank") {
        throw new Error(
          "No login URL available. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to run this test in your current environment."
        );
      }

      await waitForUi(page);

      const googlePopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickByVisibleText(
        page,
        /(sign in with google|continuar con google|iniciar sesi[oó]n con google|ingresar con google|google)/i
      );

      const googlePopup = await googlePopupPromise;
      const googlePage = googlePopup || page;
      await waitForUi(googlePage);

      const accountEmail = /juanlucasbarbiergarzon@gmail\.com/i;
      const accountLocators = [
        googlePage.getByRole("button", { name: accountEmail }),
        googlePage.getByRole("link", { name: accountEmail }),
        googlePage.getByText(accountEmail)
      ];

      for (const locator of accountLocators) {
        if ((await locator.count()) > 0) {
          const target = await firstVisible(locator);
          await target.click();
          await waitForUi(googlePage);
          break;
        }
      }

      if (googlePopup) {
        await page.bringToFront();
      }

      await waitForUi(page);
      await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible();

      evidence.dashboard = await screenshotCheckpoint(page, checkpointsDir, "01-dashboard-loaded.png");
    });

    await runStep(report, "Mi Negocio menu", async () => {
      await clickByVisibleText(page, /Negocio/i);
      await clickByVisibleText(page, /Mi Negocio/i);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

      evidence.miNegocioMenu = await screenshotCheckpoint(page, checkpointsDir, "02-mi-negocio-menu-expanded.png");
    });

    await runStep(report, "Agregar Negocio modal", async () => {
      await clickByVisibleText(page, /Agregar Negocio/i);

      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      evidence.agregarNegocioModal = await screenshotCheckpoint(page, checkpointsDir, "03-crear-negocio-modal.png");

      const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
      if (await businessNameInput.isVisible().catch(() => false)) {
        await businessNameInput.fill("Negocio Prueba Automatizacion");
      }

      await clickByVisibleText(page, /Cancelar/i);
      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).not.toBeVisible();
    });

    await runStep(report, "Administrar Negocios view", async () => {
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
        await clickByVisibleText(page, /Mi Negocio/i);
      }

      await clickByVisibleText(page, /Administrar Negocios/i);

      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

      evidence.administrarNegocios = await screenshotCheckpoint(page, checkpointsDir, "04-administrar-negocios.png", true);
    });

    await runStep(report, "Información General", async () => {
      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
      await expect(page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first()).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      const nameHints = page.getByText(/Nombre|Usuario|Perfil|Cuenta/i);
      await expect(nameHints.first()).toBeVisible();
    });

    await runStep(report, "Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });

    await runStep(report, "Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    });

    await runStep(report, "Términos y Condiciones", async () => {
      const termsResult = await openLegalDocumentAndReturn({
        context,
        appPage: page,
        checkpointsDir,
        linkTextRegex: /T[eé]rminos y Condiciones/i,
        headingRegex: /T[eé]rminos y Condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png"
      });

      evidence.terminos = termsResult.screenshotPath;
      legalUrls.terminos = termsResult.finalUrl;
    });

    await runStep(report, "Política de Privacidad", async () => {
      const privacyResult = await openLegalDocumentAndReturn({
        context,
        appPage: page,
        checkpointsDir,
        linkTextRegex: /Pol[ií]tica de Privacidad/i,
        headingRegex: /Pol[ií]tica de Privacidad/i,
        screenshotName: "06-politica-de-privacidad.png"
      });

      evidence.politica = privacyResult.screenshotPath;
      legalUrls.politica = privacyResult.finalUrl;
    });
  } catch (error) {
    workflowError = error;
    markNotExecutedAsBlocked(report);
  }

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls,
    evidence
  };

  const reportPath = path.join(rootResultsDir, "saleads_mi_negocio_full_test_report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  // This log line helps CI collectors locate the report quickly.
  // eslint-disable-next-line no-console
  console.log(`FINAL_REPORT_PATH=${reportPath}`);

  if (workflowError) {
    throw workflowError;
  }
});
