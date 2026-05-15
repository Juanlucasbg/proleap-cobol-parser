const fs = require("node:fs");
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

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function getFirstVisibleByText(page, candidates) {
  for (const candidate of candidates) {
    const locator =
      candidate instanceof RegExp
        ? page.getByText(candidate)
        : page.getByText(candidate, { exact: true });

    const count = await locator.count();
    for (let i = 0; i < count; i += 1) {
      const item = locator.nth(i);
      if (await item.isVisible()) {
        return item;
      }
    }
  }

  throw new Error(`No visible element found for candidates: ${candidates.join(", ")}`);
}

async function clickByVisibleText(page, candidates) {
  const target = await getFirstVisibleByText(page, candidates);
  await target.click();
  await waitForUi(page);
}

async function hasVisibleByText(page, candidates) {
  for (const candidate of candidates) {
    const locator =
      candidate instanceof RegExp
        ? page.getByText(candidate)
        : page.getByText(candidate, { exact: true });

    const count = await locator.count();
    for (let i = 0; i < count; i += 1) {
      if (await locator.nth(i).isVisible()) {
        return true;
      }
    }
  }

  return false;
}

function formatFailure(error) {
  const raw = (error && error.message ? error.message : String(error)) || "Unknown failure";
  const withoutAnsi = raw.replace(/\u001b\[[0-9;]*m/g, "");
  const lines = withoutAnsi
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  return lines.slice(0, 3).join(" | ");
}

async function validateLegalDocument({
  page,
  context,
  linkCandidates,
  headingMatcher,
  screenshotName,
  reportEvidence,
  appUrl,
  testInfo
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const link = await getFirstVisibleByText(page, linkCandidates);
  await link.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const targetPage = popup || page;

  await waitForUi(targetPage);

  const heading = targetPage.getByRole("heading", { name: headingMatcher }).first();
  if (await heading.count()) {
    await expect(heading).toBeVisible({ timeout: 20000 });
  } else {
    await expect(targetPage.getByText(headingMatcher).first()).toBeVisible({ timeout: 20000 });
  }

  await expect(targetPage.locator("p, li, main, article").first()).toBeVisible({
    timeout: 20000
  });

  const screenshotPath = testInfo.outputPath(screenshotName);
  await targetPage.screenshot({ path: screenshotPath, fullPage: true });
  reportEvidence.screenshots.push(screenshotPath);
  reportEvidence.urls.push(targetPage.url());

  if (popup) {
    if (!popup.isClosed()) {
      await popup.close().catch(() => {});
    }
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  if (appUrl) {
    await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL: not executed"]));
  const evidence = {
    screenshots: [],
    urls: []
  };

  let applicationUrl = "";
  let loginOk = false;
  let accountViewOk = false;

  const runValidation = async (field, fn) => {
    try {
      await fn();
      report[field] = "PASS";
    } catch (error) {
      report[field] = `FAIL: ${formatFailure(error)}`;
    }
  };

  if (process.env.SALEADS_BASE_URL) {
    await page.goto(process.env.SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await runValidation("Login", async () => {
    if (page.url() === "about:blank") {
      throw new Error(
        "Starting page is blank. Provide SALEADS_BASE_URL or open the SaleADS login page before running."
      );
    }

    const googleLoginCandidates = [
      /Sign in with Google/i,
      /Iniciar sesi[óo]n con Google/i,
      /Continuar con Google/i
    ];

    if (!(await hasVisibleByText(page, googleLoginCandidates))) {
      const entryLoginCandidates = [
        /Iniciar sesi[óo]n/i,
        /Iniciar sesión/i,
        /Sign in/i,
        /Log in/i,
        /^Login$/i,
        /Acceder/i
      ];

      if (await hasVisibleByText(page, entryLoginCandidates)) {
        await clickByVisibleText(page, entryLoginCandidates);
      }
    }

    if (!(await hasVisibleByText(page, googleLoginCandidates))) {
      const loginUrl = new URL("/login", page.url()).toString();
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickByVisibleText(page, googleLoginCandidates);

    const popup = await popupPromise;
    const authPage = popup || page;
    await waitForUi(authPage);

    const accountOption = authPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
    if (await accountOption.count()) {
      await accountOption.first().click();
      await waitForUi(authPage);
    }

    if (popup) {
      await popup.waitForClose({ timeout: 45000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav, [role='navigation']").first()).toBeVisible({
      timeout: 45000
    });
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 45000 });

    const screenshotPath = testInfo.outputPath("01-dashboard-loaded.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    evidence.screenshots.push(screenshotPath);
    loginOk = true;
  });

  await runValidation("Mi Negocio menu", async () => {
    if (!loginOk) {
      throw new Error("Prerequisite failed: Login");
    }

    await expect(page.locator("aside, nav, [role='navigation']").first()).toBeVisible({
      timeout: 15000
    });

    await clickByVisibleText(page, [/^Negocio$/i, /Negocio/i]);
    await clickByVisibleText(page, [/^Mi Negocio$/i, /Mi Negocio/i]);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15000 });

    const screenshotPath = testInfo.outputPath("02-mi-negocio-expanded.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    evidence.screenshots.push(screenshotPath);
  });

  await runValidation("Agregar Negocio modal", async () => {
    if (report["Mi Negocio menu"] !== "PASS") {
      throw new Error("Prerequisite failed: Mi Negocio menu");
    }

    await clickByVisibleText(page, [/^Agregar Negocio$/i, /Agregar Negocio/i]);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 15000 });
    const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
    if (await businessNameInput.count()) {
      await expect(businessNameInput).toBeVisible({ timeout: 15000 });
    } else {
      await expect(page.getByPlaceholder(/Nombre del Negocio/i).first()).toBeVisible({
        timeout: 15000
      });
    }

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({
      timeout: 15000
    });
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({
      timeout: 15000
    });

    const screenshotPath = testInfo.outputPath("03-agregar-negocio-modal.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    evidence.screenshots.push(screenshotPath);

    if (await businessNameInput.count()) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
    }
    await page.getByRole("button", { name: /Cancelar/i }).first().click();
    await waitForUi(page);
  });

  await runValidation("Administrar Negocios view", async () => {
    if (report["Mi Negocio menu"] !== "PASS") {
      throw new Error("Prerequisite failed: Mi Negocio menu");
    }

    const adminOption = page.getByText(/Administrar Negocios/i).first();
    if (!(await adminOption.isVisible())) {
      await clickByVisibleText(page, [/^Mi Negocio$/i, /Mi Negocio/i]);
    }

    await clickByVisibleText(page, [/^Administrar Negocios$/i, /Administrar Negocios/i]);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 30000 });

    applicationUrl = page.url();

    const screenshotPath = testInfo.outputPath("04-administrar-negocios.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    evidence.screenshots.push(screenshotPath);
    accountViewOk = true;
  });

  await runValidation("Información General", async () => {
    if (!accountViewOk) {
      throw new Error("Prerequisite failed: Administrar Negocios view");
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({
      timeout: 15000
    });
    await expect(
      page.getByText(/juanlucasbarbiergarzon@gmail\.com|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()
    ).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Juan|Lucas|Barbier|Garzon|Nombre/i).first()).toBeVisible({
      timeout: 15000
    });
  });

  await runValidation("Detalles de la Cuenta", async () => {
    if (!accountViewOk) {
      throw new Error("Prerequisite failed: Administrar Negocios view");
    }

    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 15000 });
  });

  await runValidation("Tus Negocios", async () => {
    if (!accountViewOk) {
      throw new Error("Prerequisite failed: Administrar Negocios view");
    }

    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({
      timeout: 15000
    });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
  });

  await runValidation("Términos y Condiciones", async () => {
    if (!accountViewOk) {
      throw new Error("Prerequisite failed: Administrar Negocios view");
    }

    await validateLegalDocument({
      page,
      context,
      linkCandidates: [/^Términos y Condiciones$/i, /Términos y Condiciones/i],
      headingMatcher: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      reportEvidence: evidence,
      appUrl: applicationUrl,
      testInfo
    });
  });

  await runValidation("Política de Privacidad", async () => {
    if (!accountViewOk) {
      throw new Error("Prerequisite failed: Administrar Negocios view");
    }

    await validateLegalDocument({
      page,
      context,
      linkCandidates: [/^Política de Privacidad$/i, /Política de Privacidad/i],
      headingMatcher: /Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      reportEvidence: evidence,
      appUrl: applicationUrl,
      testInfo
    });
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    evidence
  };

  const reportPath = testInfo.outputPath("final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_START");
  console.log(JSON.stringify(finalReport, null, 2));
  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_END");

  const failedSteps = Object.entries(report).filter(([, status]) => status !== "PASS");
  expect(
    failedSteps,
    `Some required validations failed: ${JSON.stringify(failedSteps, null, 2)}`
  ).toEqual([]);
});
