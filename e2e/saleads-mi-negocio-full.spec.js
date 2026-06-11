const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

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

const ARTIFACTS_DIR = path.join(process.cwd(), "e2e", "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const FINAL_REPORT_PATH = path.join(ARTIFACTS_DIR, "mi-negocio-final-report.json");

function createReportTemplate() {
  return {
    generatedAt: new Date().toISOString(),
    environment: {
      loginUrlProvided: Boolean(process.env.SALEADS_LOGIN_URL),
      loginUrl: process.env.SALEADS_LOGIN_URL || null,
    },
    results: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])),
    evidence: {
      dashboardScreenshot: null,
      expandedMenuScreenshot: null,
      agregarNegocioModalScreenshot: null,
      administrarNegociosScreenshot: null,
      terminosScreenshot: null,
      terminosUrl: null,
      privacidadScreenshot: null,
      privacidadUrl: null,
    },
    errors: {},
  };
}

async function ensureArtifactsDir() {
  await fs.mkdir(SCREENSHOTS_DIR, { recursive: true });
}

async function captureCheckpoint(page, screenshotName, options = {}) {
  const screenshotPath = path.join(SCREENSHOTS_DIR, `${screenshotName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: Boolean(options.fullPage) });
  return screenshotPath;
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(700);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible({ timeout: 20000 });
  await locator.click();
  await waitForUiToSettle(page);
}

async function clickVisibleCandidate(candidates) {
  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click();
      return;
    }
  }

  await candidates[0].click();
}

async function runValidationStep(report, fieldName, action) {
  try {
    await action();
    report.results[fieldName] = "PASS";
  } catch (error) {
    report.results[fieldName] = "FAIL";
    report.errors[fieldName] = error.message;
  }
}

async function openLegalPageAndValidate({
  page,
  context,
  linkText,
  headingRegex,
  screenshotName,
  report,
}) {
  const appPage = page;
  const link = page.getByText(linkText, { exact: false }).first();
  await expect(link).toBeVisible({ timeout: 20000 });

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await link.click();

  const popup = await popupPromise;
  const targetPage = popup || page;
  await waitForUiToSettle(targetPage);

  await expect(targetPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({
    timeout: 20000,
  });

  const visibleParagraphs = await targetPage.locator("p:visible").count();
  expect(visibleParagraphs).toBeGreaterThan(0);

  const screenshotPath = await captureCheckpoint(targetPage, screenshotName, { fullPage: true });
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToSettle(appPage);
  }

  if (/terminos/i.test(linkText)) {
    report.evidence.terminosScreenshot = screenshotPath;
    report.evidence.terminosUrl = finalUrl;
  } else {
    report.evidence.privacidadScreenshot = screenshotPath;
    report.evidence.privacidadUrl = finalUrl;
  }
}

test("SaleADS Mi Negocio full workflow", async ({ page, context }) => {
  const report = createReportTemplate();
  await ensureArtifactsDir();

  await runValidationStep(report, "Login", async () => {
    if (process.env.SALEADS_LOGIN_URL) {
      await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }

    const loginCandidates = [
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      }).first(),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i)
        .first(),
    ];

    await clickVisibleCandidate(loginCandidates);
    await waitForUiToSettle(page);

    const accountSelector = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
    if (await accountSelector.isVisible().catch(() => false)) {
      await accountSelector.click();
      await waitForUiToSettle(page);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 30000 });

    report.evidence.dashboardScreenshot = await captureCheckpoint(page, "01-dashboard-loaded");
  });

  await runValidationStep(report, "Mi Negocio menu", async () => {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    await clickAndWait(negocioSection, page);

    const miNegocioOption = page.getByText(/Mi Negocio/i).first();
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20000 });

    report.evidence.expandedMenuScreenshot = await captureCheckpoint(page, "02-mi-negocio-expanded-menu");
  });

  await runValidationStep(report, "Agregar Negocio modal", async () => {
    const agregarNegocio = page.getByText(/Agregar Negocio/i).first();
    await clickAndWait(agregarNegocio, page);

    await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible({
      timeout: 20000,
    });
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    report.evidence.agregarNegocioModalScreenshot = await captureCheckpoint(
      page,
      "03-agregar-negocio-modal",
    );

    await page.getByLabel(/Nombre del Negocio/i).click();
    await page.getByLabel(/Nombre del Negocio/i).fill("Negocio Prueba Automatizacion");
    await clickAndWait(page.getByRole("button", { name: /Cancelar/i }), page);
  });

  await runValidationStep(report, "Administrar Negocios view", async () => {
    const administrarNegocios = page.getByText(/Administrar Negocios/i).first();
    if (!(await administrarNegocios.isVisible().catch(() => false))) {
      const miNegocio = page.getByText(/Mi Negocio/i).first();
      await clickAndWait(miNegocio, page);
    }

    await clickAndWait(page.getByText(/Administrar Negocios/i).first(), page);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 30000 });

    report.evidence.administrarNegociosScreenshot = await captureCheckpoint(
      page,
      "04-administrar-negocios-view",
      { fullPage: true },
    );
  });

  await runValidationStep(report, "Información General", async () => {
    await expect(
      page.getByText(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,}/).first(),
    ).toBeVisible({
      timeout: 20000,
    });
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    // Prefer checking for a user-specific visible value in this section.
    await expect(
      page
        .locator("section, div")
        .filter({ hasText: /Informaci[oó]n General/i })
        .locator("h1, h2, h3, h4, p, span, strong")
        .first(),
    ).toBeVisible();
  });

  await runValidationStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Estado activo/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 20000 });
  });

  await runValidationStep(report, "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const businessItemsCount = await page.locator("li:visible, [role='row']:visible").count();
    expect(businessItemsCount).toBeGreaterThan(0);
  });

  await runValidationStep(report, "Términos y Condiciones", async () => {
    await openLegalPageAndValidate({
      page,
      context,
      linkText: "Términos y Condiciones",
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones",
      report,
    });
  });

  await runValidationStep(report, "Política de Privacidad", async () => {
    await openLegalPageAndValidate({
      page,
      context,
      linkText: "Política de Privacidad",
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad",
      report,
    });
  });

  await fs.writeFile(FINAL_REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
  await test.info().attach("mi-negocio-final-report", {
    path: FINAL_REPORT_PATH,
    contentType: "application/json",
  });

  const failedSteps = Object.entries(report.results)
    .filter(([, status]) => status === "FAIL")
    .map(([step]) => step);

  expect(
    failedSteps,
    `Validaciones en FAIL: ${failedSteps.join(", ")}. Revisa e2e/artifacts/mi-negocio-final-report.json`,
  ).toEqual([]);
});
