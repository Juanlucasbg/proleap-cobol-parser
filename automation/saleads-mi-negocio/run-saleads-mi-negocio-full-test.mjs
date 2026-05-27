import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const DEFAULT_TIMEOUT_MS = 20000;
const SHORT_WAIT_MS = 900;
const runId = new Date().toISOString().replace(/[:.]/g, "-");
const outputDir = path.resolve(
  process.cwd(),
  process.env.SALEADS_EVIDENCE_DIR ?? `artifacts/saleads-mi-negocio-${runId}`,
);

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

const reportStatus = new Map(
  REPORT_FIELDS.map((field) => [field, { status: "FAIL", detail: "Not executed" }]),
);

const legalUrls = {
  "Términos y Condiciones": null,
  "Política de Privacidad": null,
};

function setResult(field, passed, detail) {
  reportStatus.set(field, { status: passed ? "PASS" : "FAIL", detail });
  const badge = passed ? "PASS" : "FAIL";
  console.log(`[${badge}] ${field}: ${detail}`);
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function safeScreenshot(page, filename, fullPage = false) {
  const outputPath = path.join(outputDir, filename);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function waitForUiIdle(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(SHORT_WAIT_MS);
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUiIdle(page);
}

async function findVisibleLocator(candidates, timeoutMs = 6000) {
  for (const locator of candidates) {
    const first = locator.first();
    try {
      await first.waitFor({ state: "visible", timeout: timeoutMs });
      return first;
    } catch {
      // Try the next candidate.
    }
  }
  return null;
}

async function isVisible(locator, timeoutMs = 4000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout: timeoutMs });
    return true;
  } catch {
    return false;
  }
}

async function validateLegalLink({
  context,
  appPage,
  linkLocator,
  reportField,
  expectedHeadingRegex,
  screenshotName,
}) {
  const returnUrl = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickAndWait(linkLocator, appPage);
  const popupPage = await popupPromise;
  const legalPage = popupPage ?? appPage;

  await waitForUiIdle(legalPage);

  const headingFound = await isVisible(legalPage.getByRole("heading", { name: expectedHeadingRegex }), 8000)
    || await isVisible(legalPage.getByText(expectedHeadingRegex), 8000);

  const contentFound = await isVisible(legalPage.locator("main p, article p, p").first(), 8000)
    || (await legalPage.locator("body").innerText()).trim().length > 250;

  await safeScreenshot(legalPage, screenshotName, true);

  legalUrls[reportField] = legalPage.url();
  const passed = headingFound && contentFound;
  setResult(reportField, passed, passed
    ? `Validated legal page and captured URL: ${legalPage.url()}`
    : `Could not fully validate legal content. URL captured: ${legalPage.url()}`);

  if (popupPage) {
    await popupPage.close().catch(() => {});
    await appPage.bringToFront().catch(() => {});
    await waitForUiIdle(appPage);
  } else if (appPage.url() !== returnUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.goto(returnUrl, { waitUntil: "domcontentloaded" });
    });
    await waitForUiIdle(appPage);
  }
}

async function run() {
  await ensureDir(outputDir);

  const baseUrl = process.env.SALEADS_BASE_URL ?? process.env.SALEADS_URL ?? null;
  const googleEmail = process.env.SALEADS_GOOGLE_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
  const headless = (process.env.SALEADS_HEADLESS ?? "false").toLowerCase() === "true";
  const cdpUrl = process.env.SALEADS_CDP_URL ?? null;

  let browser;
  let context;
  let page;

  try {
    if (cdpUrl) {
      browser = await chromium.connectOverCDP(cdpUrl);
      const existingContexts = browser.contexts();
      context = existingContexts.length > 0 ? existingContexts[0] : await browser.newContext();
      const pages = context.pages();
      page = pages.length > 0 ? pages[0] : await context.newPage();
    } else {
      browser = await chromium.launch({ headless });
      context = await browser.newContext({
        viewport: { width: 1440, height: 900 },
      });
      page = await context.newPage();
    }

    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
      await waitForUiIdle(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No login page is available. Set SALEADS_BASE_URL (or SALEADS_URL) or connect with SALEADS_CDP_URL to an already-open SaleADS login page.",
      );
    }

    // Step 1: Login with Google.
    const loginButton = await findVisibleLocator([
      page.getByRole("button", { name: /Sign in with Google|Iniciar sesi[oó]n con Google|Google/i }),
      page.getByRole("link", { name: /Sign in with Google|Iniciar sesi[oó]n con Google|Google/i }),
      page.getByText(/Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google/i),
    ], 12000);

    if (!loginButton) {
      throw new Error("Could not find the Google login button on the current page.");
    }

    const loginPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const loginPopup = await loginPopupPromise;
    const loginPage = loginPopup ?? page;
    await waitForUiIdle(loginPage);

    const accountChoice = await findVisibleLocator([
      loginPage.getByRole("button", { name: googleEmail }),
      loginPage.getByText(googleEmail, { exact: true }),
      loginPage.locator(`[data-email="${googleEmail}"]`),
    ], 7000);

    if (accountChoice) {
      await clickAndWait(accountChoice, loginPage);
    }

    if (loginPopup) {
      await loginPopup.close().catch(() => {});
      await page.bringToFront().catch(() => {});
    }

    await waitForUiIdle(page);

    const mainInterfaceVisible = await isVisible(page.locator("main, [role='main']").first(), 12000);
    const sidebarVisible = await isVisible(
      page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio|Dashboard|Inicio/i }).first(),
      12000,
    ) || await isVisible(page.locator("aside, nav").first(), 12000);

    await safeScreenshot(page, "step-1-dashboard-loaded.png", true);
    setResult(
      "Login",
      mainInterfaceVisible && sidebarVisible,
      mainInterfaceVisible && sidebarVisible
        ? "Main interface and left sidebar are visible."
        : "Dashboard or sidebar could not be confirmed after login.",
    );

    // Step 2: Open Mi Negocio menu.
    const negocioMenu = await findVisibleLocator([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
      page.getByText(/^Negocio$/i),
    ], 12000);

    if (!negocioMenu) {
      throw new Error("Could not find Negocio/Mi Negocio in the left sidebar.");
    }

    await clickAndWait(negocioMenu, page);

    const agregarNegocioInMenu = await findVisibleLocator([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ], 10000);

    const administrarNegociosInMenu = await findVisibleLocator([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ], 10000);

    await safeScreenshot(page, "step-2-mi-negocio-menu-expanded.png", true);
    setResult(
      "Mi Negocio menu",
      Boolean(agregarNegocioInMenu && administrarNegociosInMenu),
      agregarNegocioInMenu && administrarNegociosInMenu
        ? "Submenu expanded and both options are visible."
        : "Could not confirm both submenu options.",
    );

    // Step 3: Validate Agregar Negocio modal.
    if (!agregarNegocioInMenu) {
      throw new Error("Agregar Negocio option is not available to validate the modal.");
    }
    await clickAndWait(agregarNegocioInMenu, page);

    const modalTitleVisible = await isVisible(page.getByText(/^Crear Nuevo Negocio$/i), 10000);
    const businessNameInput = await findVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
      page.locator("input[name*='nombre'], input[id*='nombre']"),
    ], 6000);
    const quotaTextVisible = await isVisible(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i), 5000);
    const cancelButton = await findVisibleLocator([
      page.getByRole("button", { name: /^Cancelar$/i }),
      page.getByText(/^Cancelar$/i),
    ], 5000);
    const createButton = await findVisibleLocator([
      page.getByRole("button", { name: /^Crear Negocio$/i }),
      page.getByText(/^Crear Negocio$/i),
    ], 5000);

    await safeScreenshot(page, "step-3-agregar-negocio-modal.png", true);

    if (businessNameInput) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
    }
    if (cancelButton) {
      await clickAndWait(cancelButton, page);
    }

    setResult(
      "Agregar Negocio modal",
      modalTitleVisible && Boolean(businessNameInput) && quotaTextVisible && Boolean(cancelButton) && Boolean(createButton),
      modalTitleVisible && businessNameInput && quotaTextVisible && cancelButton && createButton
        ? "Modal title, field, quota, and action buttons validated."
        : "One or more expected modal elements are missing.",
    );

    // Step 4: Open Administrar Negocios.
    let administrarNegocios = administrarNegociosInMenu;
    if (!administrarNegocios || !(await administrarNegocios.isVisible().catch(() => false))) {
      const reopenMenu = await findVisibleLocator([
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ], 8000);
      if (reopenMenu) {
        await clickAndWait(reopenMenu, page);
      }
      administrarNegocios = await findVisibleLocator([
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ], 10000);
    }

    if (!administrarNegocios) {
      throw new Error("Administrar Negocios option was not found.");
    }

    await clickAndWait(administrarNegocios, page);

    const infoGeneralVisible = await isVisible(page.getByText(/Informaci[oó]n General/i), 12000);
    const detallesCuentaVisible = await isVisible(page.getByText(/Detalles de la Cuenta/i), 12000);
    const tusNegociosVisible = await isVisible(page.getByText(/Tus Negocios/i), 12000);
    const legalSectionVisible = await isVisible(page.getByText(/Secci[oó]n Legal/i), 12000);
    await safeScreenshot(page, "step-4-administrar-negocios-page.png", true);

    setResult(
      "Administrar Negocios view",
      infoGeneralVisible && detallesCuentaVisible && tusNegociosVisible && legalSectionVisible,
      infoGeneralVisible && detallesCuentaVisible && tusNegociosVisible && legalSectionVisible
        ? "All required account sections are visible."
        : "Some required account sections are missing.",
    );

    // Step 5: Información General.
    const infoGeneralContainer = page
      .locator("section, article, div")
      .filter({ has: page.getByText(/Informaci[oó]n General/i) })
      .first();
    const infoSectionText = (await infoGeneralContainer.innerText().catch(() => "")).trim();
    const likelyUserNameVisible = infoSectionText
      .split("\n")
      .map((line) => line.trim())
      .some((line) => line.length >= 3 && /[A-Za-zÁ-ÿ]/.test(line) && !/informaci[oó]n general|business plan|cambiar plan|@/i.test(line));
    const userEmailVisible = await isVisible(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/), 8000);
    const businessPlanVisible = await isVisible(page.getByText(/BUSINESS PLAN/i), 8000);
    const cambiarPlanVisible = await isVisible(page.getByRole("button", { name: /Cambiar Plan/i }), 8000)
      || await isVisible(page.getByText(/Cambiar Plan/i), 8000);

    setResult(
      "Información General",
      likelyUserNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible,
      likelyUserNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible
        ? "Nombre, email, plan y botón de cambio validados."
        : "One or more expected elements in Información General were not found.",
    );

    // Step 6: Detalles de la Cuenta.
    const cuentaCreadaVisible = await isVisible(page.getByText(/Cuenta creada/i), 8000);
    const estadoActivoVisible = await isVisible(page.getByText(/Estado activo/i), 8000);
    const idiomaSeleccionadoVisible = await isVisible(page.getByText(/Idioma seleccionado/i), 8000);
    setResult(
      "Detalles de la Cuenta",
      cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible,
      cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible
        ? "All expected account detail labels are visible."
        : "One or more account detail labels are missing.",
    );

    // Step 7: Tus Negocios.
    const businessListVisible = await isVisible(
      page.locator("section, article, div").filter({ has: page.getByText(/Tus Negocios/i) }).first(),
      8000,
    );
    const addBusinessButtonVisible = await isVisible(page.getByRole("button", { name: /^Agregar Negocio$/i }), 8000)
      || await isVisible(page.getByText(/^Agregar Negocio$/i), 8000);
    const quotaVisibleInBusinesses = await isVisible(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i), 8000);
    setResult(
      "Tus Negocios",
      businessListVisible && addBusinessButtonVisible && quotaVisibleInBusinesses,
      businessListVisible && addBusinessButtonVisible && quotaVisibleInBusinesses
        ? "Business list, add button, and quota text are visible."
        : "Could not validate all expected Tus Negocios elements.",
    );

    // Step 8: Términos y Condiciones.
    const termsLink = await findVisibleLocator([
      page.getByRole("link", { name: /T[ée]rminos y Condiciones/i }),
      page.getByText(/T[ée]rminos y Condiciones/i),
    ], 10000);

    if (termsLink) {
      await validateLegalLink({
        context,
        appPage: page,
        linkLocator: termsLink,
        reportField: "Términos y Condiciones",
        expectedHeadingRegex: /T[ée]rminos y Condiciones/i,
        screenshotName: "step-8-terminos-y-condiciones.png",
      });
    } else {
      setResult("Términos y Condiciones", false, "Could not find Términos y Condiciones link.");
    }

    // Step 9: Política de Privacidad.
    const privacyLink = await findVisibleLocator([
      page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }),
      page.getByText(/Pol[ií]tica de Privacidad/i),
    ], 10000);

    if (privacyLink) {
      await validateLegalLink({
        context,
        appPage: page,
        linkLocator: privacyLink,
        reportField: "Política de Privacidad",
        expectedHeadingRegex: /Pol[ií]tica de Privacidad/i,
        screenshotName: "step-9-politica-de-privacidad.png",
      });
    } else {
      setResult("Política de Privacidad", false, "Could not find Política de Privacidad link.");
    }
  } finally {
    const finalReport = {
      name: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      environment: {
        baseUrl,
        cdpConnected: Boolean(cdpUrl),
        headless,
        googleEmail,
      },
      evidenceDirectory: outputDir,
      legalUrls,
      results: Object.fromEntries(reportStatus),
    };

    const reportPath = path.join(outputDir, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

    console.log("");
    console.log("Final Report");
    console.log("============");
    for (const field of REPORT_FIELDS) {
      const result = reportStatus.get(field);
      console.log(`- ${field}: ${result.status} (${result.detail})`);
    }
    console.log("");
    console.log(`Evidence directory: ${outputDir}`);
    console.log(`Report path: ${reportPath}`);

    const failed = REPORT_FIELDS.some((field) => reportStatus.get(field).status !== "PASS");

    if (browser) {
      await browser.close().catch(() => {});
    }

    if (failed) {
      process.exitCode = 1;
    }
  }
}

run().catch(async (error) => {
  console.error("");
  console.error("Fatal error during test execution:");
  console.error(error);
  process.exitCode = 1;
});
