const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => null);
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
  await page.waitForTimeout(700);
}

async function waitUntilVisible(locator, timeout = 8000) {
  await locator.first().waitFor({ state: "visible", timeout });
  return locator.first();
}

async function firstVisible(candidates, timeout = 4000) {
  for (const candidate of candidates) {
    try {
      const locator = candidate.first();
      await locator.waitFor({ state: "visible", timeout });
      return locator;
    } catch (error) {
      // Continue trying with the next candidate.
    }
  }

  return null;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUiLoad(page);
}

async function capture(page, screenshotsDir, filename, fullPage = false) {
  const screenshotPath = path.join(screenshotsDir, filename);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

function initializeReportResults() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "NOT_RUN";
    return acc;
  }, {});
}

test(TEST_NAME, async ({ page }, testInfo) => {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.resolve(__dirname, "..", "artifacts");
  const screenshotsDir = path.join(artifactsDir, "screenshots", timestamp);
  const reportsDir = path.join(artifactsDir, "reports");
  await fs.mkdir(screenshotsDir, { recursive: true });
  await fs.mkdir(reportsDir, { recursive: true });

  const results = initializeReportResults();
  const failures = [];
  const evidence = {
    screenshots: [],
    urls: {},
  };
  const appStartUrl = process.env.SALEADS_URL || process.env.PLAYWRIGHT_TEST_BASE_URL || "";

  const record = async (field, action) => {
    try {
      await action();
      results[field] = "PASS";
    } catch (error) {
      results[field] = "FAIL";
      failures.push({
        field,
        message: error instanceof Error ? error.message : String(error),
      });
    }
  };

  if (appStartUrl) {
    await page.goto(appStartUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_URL (or PLAYWRIGHT_TEST_BASE_URL) to the SaleADS login page. The test avoids hardcoded domains."
    );
  }

  await record("Login", async () => {
    const loginButton = await firstVisible(
      [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("button", { name: /sign in/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/inicia sesi[oó]n con google/i),
      ],
      10000
    );

    if (!loginButton) {
      throw new Error("Google login button was not found.");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const popup = await popupPromise;

    const googleSurface = popup || page;
    const accountSelector = await firstVisible(
      [
        googleSurface.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        googleSurface.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        googleSurface.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(".", "\\."), "i")),
      ],
      9000
    );

    if (accountSelector) {
      await clickAndWait(accountSelector, googleSurface);
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 30000 }).catch(() => null);
      await page.bringToFront();
    }

    const sidebar = await firstVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
      ],
      20000
    );

    if (!sidebar) {
      throw new Error("Main application sidebar is not visible after login.");
    }

    await expect(sidebar).toBeVisible();
    evidence.screenshots.push(await capture(page, screenshotsDir, "01-dashboard-after-login.png", true));
  });

  await record("Mi Negocio menu", async () => {
    const negocioMenu = await firstVisible(
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      12000
    );

    if (!negocioMenu) {
      throw new Error("Sidebar section 'Negocio' was not found.");
    }

    await clickAndWait(negocioMenu, page);

    const miNegocioOption = await firstVisible(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ],
      8000
    );

    if (!miNegocioOption) {
      throw new Error("Menu option 'Mi Negocio' was not found.");
    }

    await clickAndWait(miNegocioOption, page);
    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();
    evidence.screenshots.push(await capture(page, screenshotsDir, "02-mi-negocio-menu-expanded.png"));
  });

  await record("Agregar Negocio modal", async () => {
    const addBusiness = await firstVisible(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      8000
    );

    if (!addBusiness) {
      throw new Error("'Agregar Negocio' action was not found.");
    }

    await clickAndWait(addBusiness, page);

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i);
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i))).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    const nameField = await firstVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
      ],
      5000
    );

    if (nameField) {
      await nameField.fill("Negocio Prueba Automatización");
    }

    evidence.screenshots.push(await capture(page, screenshotsDir, "03-agregar-negocio-modal.png"));
    await clickAndWait(await waitUntilVisible(page.getByRole("button", { name: /^Cancelar$/i })), page);
  });

  await record("Administrar Negocios view", async () => {
    const miNegocioAgain = await firstVisible(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ],
      10000
    );

    if (miNegocioAgain) {
      await clickAndWait(miNegocioAgain, page);
    }

    const manageBusinesses = await firstVisible(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      10000
    );

    if (!manageBusinesses) {
      throw new Error("'Administrar Negocios' option was not found.");
    }

    await clickAndWait(manageBusinesses, page);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();
    evidence.screenshots.push(await capture(page, screenshotsDir, "04-administrar-negocios-view.png", true));
  });

  await record("Información General", async () => {
    const infoSectionTitle = await waitUntilVisible(page.getByText(/Información General/i), 10000);
    await infoSectionTitle.scrollIntoViewIfNeeded();

    const infoContainer = page.locator("section,div").filter({ has: page.getByText(/Información General/i) }).first();
    const sectionText = await infoContainer.innerText();

    if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(sectionText)) {
      throw new Error("User email was not found in 'Información General'.");
    }

    if (!/BUSINESS PLAN/i.test(sectionText)) {
      throw new Error("'BUSINESS PLAN' was not found in 'Información General'.");
    }

    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).or(page.getByText(/Cambiar Plan/i))).toBeVisible();

    const normalized = sectionText
      .replace(/Informaci[oó]n General/gi, "")
      .replace(/BUSINESS PLAN/gi, "")
      .replace(/Cambiar Plan/gi, "")
      .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "")
      .trim();

    if (normalized.length < 3) {
      throw new Error("User name was not confidently detected in 'Información General'.");
    }
  });

  await record("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await record("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).or(page.getByText(/^Agregar Negocio$/i))).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  });

  const openAndValidateLegalLink = async (options) => {
    const { linkNameRegex, headingRegex, screenshotName, evidenceKey } = options;
    const link = await firstVisible(
      [
        page.getByRole("link", { name: linkNameRegex }),
        page.getByText(linkNameRegex),
      ],
      12000
    );

    if (!link) {
      throw new Error(`Legal link '${linkNameRegex}' was not found.`);
    }

    await link.scrollIntoViewIfNeeded();

    const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await link.click();
    await waitForUiLoad(page);
    const popup = await popupPromise;

    const legalPage = popup || page;
    await waitForUiLoad(legalPage);
    await expect(legalPage.getByText(headingRegex)).toBeVisible();

    const bodyText = await legalPage.locator("body").innerText();
    if (bodyText.trim().length < 120) {
      throw new Error("Legal content appears too short.");
    }

    evidence.screenshots.push(await capture(legalPage, screenshotsDir, screenshotName, true));
    evidence.urls[evidenceKey] = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUiLoad(page);
    }
  };

  await record("Términos y Condiciones", async () => {
    await openAndValidateLegalLink({
      linkNameRegex: /Términos y Condiciones/i,
      headingRegex: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      evidenceKey: "terminos_y_condiciones_url",
    });
  });

  await record("Política de Privacidad", async () => {
    await openAndValidateLegalLink({
      linkNameRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      evidenceKey: "politica_de_privacidad_url",
    });
  });

  const finalReport = {
    testName: TEST_NAME,
    executedAt: new Date().toISOString(),
    startUrl: appStartUrl || page.url(),
    results,
    evidence,
    failures,
    runArtifactPath: testInfo.outputDir,
  };

  const reportPath = path.join(reportsDir, `${TEST_NAME}-${timestamp}.json`);
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");

  if (failures.length > 0) {
    throw new Error(
      `Workflow completed with ${failures.length} failed validation group(s). Report: ${reportPath}`
    );
  }
});
