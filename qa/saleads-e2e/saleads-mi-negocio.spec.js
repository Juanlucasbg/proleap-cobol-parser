const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const SCREENSHOT_DIR = process.env.SALEADS_SCREENSHOT_DIR || path.join(__dirname, "artifacts", "screenshots");
const REPORT_DIR = process.env.SALEADS_REPORT_DIR || path.join(__dirname, "artifacts", "reports");
const REPORT_FILE_NAME = "saleads-mi-negocio-report.json";
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function createDefaultReportEntry() {
  return {
    status: "FAIL",
    details: [],
    evidence: []
  };
}

function ensureDirectory(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function fileSafeSegment(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function captureCheckpoint(page, label, options = {}) {
  ensureDirectory(SCREENSHOT_DIR);
  const fileName = `${Date.now()}-${fileSafeSegment(label)}.png`;
  const screenshotPath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage: options.fullPage ?? true });
  return screenshotPath;
}

async function resolveVisibleLocator(page, locatorFactories, timeoutMs = 10_000) {
  for (const locatorFactory of locatorFactories) {
    const locator = locatorFactory(page).first();
    try {
      await locator.waitFor({ state: "visible", timeout: timeoutMs });
      return locator;
    } catch (_err) {
      // Intentionally continue to next selector strategy.
    }
  }

  return null;
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function stepGuard(stepName, reportField, report, stepHandler) {
  try {
    await stepHandler();
    report[reportField].status = "PASS";
    report[reportField].details.push(`${stepName}: validaciones completadas.`);
  } catch (err) {
    report[reportField].status = "FAIL";
    report[reportField].details.push(`${stepName}: ${err.message}`);
  }
}

async function openLegalLink({
  page,
  context,
  report,
  reportField,
  linkFactories,
  headingFactories,
  screenshotLabel,
  legalUrls
}) {
  const legalLink = await resolveVisibleLocator(page, linkFactories, 20_000);
  if (!legalLink) {
    throw new Error(`No se encontró el enlace legal para "${reportField}".`);
  }

  const previousUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 5_000 }).catch(() => null);
  await legalLink.click();

  const popupPage = await popupPromise;
  const legalPage = popupPage || page;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 60_000 }).catch(() => {});
  await waitForUi(legalPage);

  const legalHeading = await resolveVisibleLocator(legalPage, headingFactories, 20_000);
  if (!legalHeading) {
    throw new Error(`No se encontró el encabezado esperado de "${reportField}".`);
  }

  const legalText = (await legalPage.locator("body").innerText()).trim();
  if (legalText.length < 150) {
    throw new Error(`El contenido legal de "${reportField}" parece incompleto.`);
  }

  const legalScreenshot = await captureCheckpoint(legalPage, screenshotLabel, { fullPage: true });
  report[reportField].evidence.push(legalScreenshot);
  report[reportField].evidence.push(`URL final: ${legalPage.url()}`);
  legalUrls[reportField] = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  if (page.url() !== previousUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  ensureDirectory(REPORT_DIR);
  test.setTimeout(12 * 60 * 1000);

  const report = {
    Login: createDefaultReportEntry(),
    "Mi Negocio menu": createDefaultReportEntry(),
    "Agregar Negocio modal": createDefaultReportEntry(),
    "Administrar Negocios view": createDefaultReportEntry(),
    "Información General": createDefaultReportEntry(),
    "Detalles de la Cuenta": createDefaultReportEntry(),
    "Tus Negocios": createDefaultReportEntry(),
    "Términos y Condiciones": createDefaultReportEntry(),
    "Política de Privacidad": createDefaultReportEntry()
  };

  const legalUrls = {};
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  let loginSucceeded = false;
  let administrarNegociosLoaded = false;

  await stepGuard("Login con Google", "Login", report, async () => {
    if (!loginUrl) {
      throw new Error("Define SALEADS_LOGIN_URL con la página de login del entorno actual.");
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginButton = await resolveVisibleLocator(
      page,
      [
        (p) => p.getByRole("button", { name: /sign in with google/i }),
        (p) => p.getByRole("button", { name: /iniciar sesión con google/i }),
        (p) => p.getByRole("button", { name: /continuar con google/i }),
        (p) => p.getByText(/sign in with google/i),
        (p) => p.getByText(/iniciar sesión con google/i),
        (p) => p.getByText(/google/i)
      ],
      20_000
    );

    if (!loginButton) {
      throw new Error("No se encontró el botón de acceso con Google.");
    }

    await clickAndWait(page, loginButton);

    await page.waitForTimeout(1_200);
    const externalPages = context.pages().filter((candidatePage) => candidatePage !== page && !candidatePage.isClosed());
    const authPage = externalPages.length > 0 ? externalPages[externalPages.length - 1] : page;

    if (authPage !== page) {
      await authPage.bringToFront();
      await waitForUi(authPage);
    }

    const googleAccountOption = await resolveVisibleLocator(
      authPage,
      [
        (p) => p.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        (p) => p.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        (p) => p.locator(`[data-identifier="${GOOGLE_ACCOUNT_EMAIL}"]`)
      ],
      5_000
    );

    if (googleAccountOption) {
      await googleAccountOption.click();
      await waitForUi(authPage);
    }

    await page.bringToFront();
    await waitForUi(page);

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 90_000 });
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 90_000 });

    const dashboardScreenshot = await captureCheckpoint(page, "dashboard-cargado", { fullPage: true });
    report.Login.evidence.push(dashboardScreenshot);
    loginSucceeded = true;
  });

  await stepGuard("Abrir menú Mi Negocio", "Mi Negocio menu", report, async () => {
    if (!loginSucceeded) {
      throw new Error("No se ejecuta porque el login no fue exitoso.");
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/^Negocio$/i).first()).toBeVisible({ timeout: 30_000 });

    const miNegocioOption = await resolveVisibleLocator(
      page,
      [
        (p) => p.getByRole("button", { name: /^Mi Negocio$/i }),
        (p) => p.getByRole("link", { name: /^Mi Negocio$/i }),
        (p) => p.getByText(/^Mi Negocio$/i)
      ],
      30_000
    );

    if (!miNegocioOption) {
      throw new Error("No se encontró la opción 'Mi Negocio' en el sidebar.");
    }

    await clickAndWait(page, miNegocioOption);
    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: 30_000 });

    const expandedMenuScreenshot = await captureCheckpoint(page, "mi-negocio-menu-expandido");
    report["Mi Negocio menu"].evidence.push(expandedMenuScreenshot);
  });

  await stepGuard("Validar modal Agregar Negocio", "Agregar Negocio modal", report, async () => {
    if (!loginSucceeded) {
      throw new Error("No se ejecuta porque el login no fue exitoso.");
    }

    const agregarNegocioMenuOption = await resolveVisibleLocator(
      page,
      [
        (p) => p.getByRole("button", { name: /^Agregar Negocio$/i }),
        (p) => p.getByRole("link", { name: /^Agregar Negocio$/i }),
        (p) => p.getByText(/^Agregar Negocio$/i)
      ],
      30_000
    );

    if (!agregarNegocioMenuOption) {
      throw new Error("No se encontró la opción de menú 'Agregar Negocio'.");
    }

    await clickAndWait(page, agregarNegocioMenuOption);

    const modalTitle = await resolveVisibleLocator(
      page,
      [
        (p) => p.getByRole("heading", { name: /crear nuevo negocio/i }),
        (p) => p.getByText(/crear nuevo negocio/i)
      ],
      20_000
    );

    if (!modalTitle) {
      throw new Error("No apareció el modal 'Crear Nuevo Negocio'.");
    }

    const businessNameInput = await resolveVisibleLocator(
      page,
      [
        (p) => p.getByLabel(/nombre del negocio/i),
        (p) => p.getByPlaceholder(/nombre del negocio/i),
        (p) => p.locator("input[name*='negocio'], input[id*='negocio']")
      ],
      20_000
    );

    if (!businessNameInput) {
      throw new Error("No se encontró el input 'Nombre del Negocio'.");
    }

    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
    const cancelButton = await resolveVisibleLocator(
      page,
      [
        (p) => p.getByRole("button", { name: /^Cancelar$/i }),
        (p) => p.getByText(/^Cancelar$/i)
      ],
      20_000
    );
    const createButton = await resolveVisibleLocator(
      page,
      [
        (p) => p.getByRole("button", { name: /^Crear Negocio$/i }),
        (p) => p.getByText(/^Crear Negocio$/i)
      ],
      20_000
    );

    if (!cancelButton || !createButton) {
      throw new Error("No se encontraron los botones 'Cancelar' y/o 'Crear Negocio'.");
    }

    const modalScreenshot = await captureCheckpoint(page, "modal-crear-negocio");
    report["Agregar Negocio modal"].evidence.push(modalScreenshot);

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, cancelButton);
  });

  await stepGuard("Abrir Administrar Negocios", "Administrar Negocios view", report, async () => {
    if (!loginSucceeded) {
      throw new Error("No se ejecuta porque el login no fue exitoso.");
    }

    const adminOptionVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
    if (!adminOptionVisible) {
      const miNegocioOption = await resolveVisibleLocator(
        page,
        [
          (p) => p.getByRole("button", { name: /^Mi Negocio$/i }),
          (p) => p.getByRole("link", { name: /^Mi Negocio$/i }),
          (p) => p.getByText(/^Mi Negocio$/i)
        ],
        15_000
      );

      if (!miNegocioOption) {
        throw new Error("No se pudo reabrir el menú 'Mi Negocio'.");
      }

      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegociosOption = await resolveVisibleLocator(
      page,
      [
        (p) => p.getByRole("button", { name: /^Administrar Negocios$/i }),
        (p) => p.getByRole("link", { name: /^Administrar Negocios$/i }),
        (p) => p.getByText(/^Administrar Negocios$/i)
      ],
      20_000
    );

    if (!administrarNegociosOption) {
      throw new Error("No se encontró la opción 'Administrar Negocios'.");
    }

    await clickAndWait(page, administrarNegociosOption);

    await expect(page.getByText(/información general/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/sección legal/i).first()).toBeVisible({ timeout: 30_000 });

    const accountScreenshot = await captureCheckpoint(page, "administrar-negocios-vista-completa", { fullPage: true });
    report["Administrar Negocios view"].evidence.push(accountScreenshot);
    administrarNegociosLoaded = true;
  });

  await stepGuard("Validar Información General", "Información General", report, async () => {
    if (!administrarNegociosLoaded) {
      throw new Error("No se ejecuta porque no se cargó la vista Administrar Negocios.");
    }

    await expect(page.getByText(/información general/i).first()).toBeVisible({ timeout: 20_000 });

    const bodyText = await page.locator("body").innerText();
    const emailMatch = bodyText.match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
    if (!emailMatch) {
      throw new Error("No se encontró un correo visible de usuario.");
    }

    const infoSection = page.locator("section,div").filter({ hasText: /información general/i }).first();
    const infoText = (await infoSection.innerText()).split("\n").map((line) => line.trim()).filter(Boolean);
    const hasUserName = infoText.some(
      (line) =>
        /^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ .'-]{2,}$/.test(line) &&
        !/información general|business plan|cambiar plan|@/i.test(line)
    );

    if (!hasUserName) {
      throw new Error("No se detectó un nombre de usuario visible en Información General.");
    }

    await expect(page.getByText(/business plan/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({ timeout: 20_000 });
  });

  await stepGuard("Validar Detalles de la Cuenta", "Detalles de la Cuenta", report, async () => {
    if (!administrarNegociosLoaded) {
      throw new Error("No se ejecuta porque no se cargó la vista Administrar Negocios.");
    }

    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await stepGuard("Validar Tus Negocios", "Tus Negocios", report, async () => {
    if (!administrarNegociosLoaded) {
      throw new Error("No se ejecuta porque no se cargó la vista Administrar Negocios.");
    }

    const businessSection = page.locator("section,div").filter({ hasText: /tus negocios/i }).first();
    await expect(businessSection).toBeVisible({ timeout: 20_000 });

    const businessListCandidatesCount = await businessSection
      .locator("ul li, table tr, [class*='business'], [data-testid*='business']")
      .count();
    if (businessListCandidatesCount < 1) {
      throw new Error("No se encontró una lista visible de negocios.");
    }

    const addBusinessButton = await resolveVisibleLocator(
      page,
      [
        (p) => p.getByRole("button", { name: /^Agregar Negocio$/i }),
        (p) => p.getByText(/^Agregar Negocio$/i)
      ],
      20_000
    );

    if (!addBusinessButton) {
      throw new Error("No se encontró el botón 'Agregar Negocio' en Tus Negocios.");
    }

    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await stepGuard("Validar Términos y Condiciones", "Términos y Condiciones", report, async () => {
    if (!administrarNegociosLoaded) {
      throw new Error("No se ejecuta porque no se cargó la vista Administrar Negocios.");
    }

    await openLegalLink({
      page,
      context,
      report,
      reportField: "Términos y Condiciones",
      linkFactories: [
        (p) => p.getByRole("link", { name: /términos y condiciones/i }),
        (p) => p.getByText(/términos y condiciones/i)
      ],
      headingFactories: [
        (p) => p.getByRole("heading", { name: /términos y condiciones/i }),
        (p) => p.getByText(/términos y condiciones/i)
      ],
      screenshotLabel: "terminos-y-condiciones",
      legalUrls
    });
  });

  await stepGuard("Validar Política de Privacidad", "Política de Privacidad", report, async () => {
    if (!administrarNegociosLoaded) {
      throw new Error("No se ejecuta porque no se cargó la vista Administrar Negocios.");
    }

    await openLegalLink({
      page,
      context,
      report,
      reportField: "Política de Privacidad",
      linkFactories: [
        (p) => p.getByRole("link", { name: /política de privacidad/i }),
        (p) => p.getByText(/política de privacidad/i)
      ],
      headingFactories: [
        (p) => p.getByRole("heading", { name: /política de privacidad/i }),
        (p) => p.getByText(/política de privacidad/i)
      ],
      screenshotLabel: "politica-de-privacidad",
      legalUrls
    });
  });

  const reportPayload = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    loginUrl: loginUrl || "(not provided)",
    legalUrls,
    results: report
  };

  const reportPath = path.join(REPORT_DIR, REPORT_FILE_NAME);
  fs.writeFileSync(reportPath, JSON.stringify(reportPayload, null, 2), "utf8");

  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedSteps = Object.entries(report)
    .filter(([_name, entry]) => entry.status !== "PASS")
    .map(([name]) => name);

  expect(
    failedSteps,
    `Validaciones fallidas: ${failedSteps.join(", ")}. Revisa ${reportPath} para evidencia detallada.`
  ).toEqual([]);
});
