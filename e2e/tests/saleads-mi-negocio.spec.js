const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const SCREENSHOT_DIR = path.join(__dirname, "..", "test-results", "screenshots");
const REPORT_DIR = path.join(__dirname, "..", "test-results");
const REPORT_PATH = path.join(REPORT_DIR, "saleads-mi-negocio-report.json");
const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

function slug(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

function toRegExpByText(text) {
  return new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function clickByText(page, text) {
  const candidateLocators = [
    page.getByRole("button", { name: toRegExpByText(text) }),
    page.getByRole("link", { name: toRegExpByText(text) }),
    page.getByRole("menuitem", { name: toRegExpByText(text) }),
    page.getByRole("tab", { name: toRegExpByText(text) }),
    page.getByRole("heading", { name: toRegExpByText(text) }),
    page.getByText(toRegExpByText(text)),
  ];

  for (const locator of candidateLocators) {
    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      await first.click({ timeout: 15_000 });
      await waitForUi(page);
      return true;
    }
  }

  return false;
}

async function ensureVisible(page, text, reportKey, report) {
  const locator = page.getByText(toRegExpByText(text)).first();
  const visible = await locator.isVisible().catch(() => false);
  if (!visible) {
    report[reportKey] = "FAIL";
  }
  expect(visible, `Expected visible text: ${text}`).toBeTruthy();
}

async function captureCheckpoint(page, name) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const screenshotPath = path.join(SCREENSHOT_DIR, `${slug(name)}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  fs.mkdirSync(REPORT_DIR, { recursive: true });

  const report = {
    Login: "PASS",
    "Mi Negocio menu": "PASS",
    "Agregar Negocio modal": "PASS",
    "Administrar Negocios view": "PASS",
    "Información General": "PASS",
    "Detalles de la Cuenta": "PASS",
    "Tus Negocios": "PASS",
    "Términos y Condiciones": "PASS",
    "Política de Privacidad": "PASS",
    evidence: {
      dashboardScreenshot: null,
      miNegocioMenuScreenshot: null,
      agregarNegocioModalScreenshot: null,
      administrarNegociosScreenshot: null,
      terminosScreenshot: null,
      terminosFinalUrl: null,
      politicaScreenshot: null,
      politicaFinalUrl: null,
    },
  };

  const markFail = (key, error) => {
    report[key] = "FAIL";
    // Keep a compact per-section error trace inside report JSON.
    report.evidence[`${slug(key)}Error`] = String(error && error.message ? error.message : error);
  };
  const stepErrors = [];
  const runStep = async (reportKey, fn) => {
    try {
      await fn();
    } catch (error) {
      markFail(reportKey, error);
      stepErrors.push(`${reportKey}: ${error && error.message ? error.message : String(error)}`);
    }
  };

  await test.step("Step 1: Login with Google and validate dashboard", async () => {
    await runStep("Login", async () => {
      await waitForUi(page);

      const loginClicked =
        (await clickByText(page, "Sign in with Google")) ||
        (await clickByText(page, "Login with Google")) ||
        (await clickByText(page, "Continuar con Google")) ||
        (await clickByText(page, "Iniciar sesión con Google"));

      expect(loginClicked, "Google login button should be clickable").toBeTruthy();

      const googleEmailOption = page
        .getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"))
        .first();
      if (await googleEmailOption.isVisible().catch(() => false)) {
        await googleEmailOption.click({ timeout: 15_000 });
        await waitForUi(page);
      }

      // Validate that main app and left sidebar are visible after login.
      const mainVisible = await page.locator("main").first().isVisible().catch(() => false);
      expect(mainVisible, "Main application interface should be visible").toBeTruthy();
      const sidebar = page.getByRole("navigation").first();
      const sidebarVisible = await sidebar.isVisible().catch(() => false);
      expect(sidebarVisible, "Left sidebar should be visible").toBeTruthy();

      await captureCheckpoint(page, "01-dashboard-loaded");
      report.evidence.dashboardScreenshot = "test-results/screenshots/01-dashboard-loaded.png";
    });
  });

  await test.step("Step 2: Open Mi Negocio menu and validate submenu", async () => {
    await runStep("Mi Negocio menu", async () => {
      const negocioClicked =
        (await clickByText(page, "Negocio")) || (await clickByText(page, "Mi Negocio"));
      expect(negocioClicked, "Negocio/Mi Negocio menu should be clickable").toBeTruthy();

      await ensureVisible(page, "Agregar Negocio", "Mi Negocio menu", report);
      await ensureVisible(page, "Administrar Negocios", "Mi Negocio menu", report);

      await captureCheckpoint(page, "02-mi-negocio-menu-expanded");
      report.evidence.miNegocioMenuScreenshot =
        "test-results/screenshots/02-mi-negocio-menu-expanded.png";
    });
  });

  await test.step("Step 3: Validate Agregar Negocio modal", async () => {
    await runStep("Agregar Negocio modal", async () => {
      const clicked = await clickByText(page, "Agregar Negocio");
      expect(clicked, "Agregar Negocio should be clickable").toBeTruthy();

      await ensureVisible(page, "Crear Nuevo Negocio", "Agregar Negocio modal", report);
      const businessNameInput = page
        .getByRole("textbox", { name: /Nombre del Negocio/i })
        .first();
      expect(
        await businessNameInput.isVisible().catch(() => false),
        "Nombre del Negocio field should be visible",
      ).toBeTruthy();
      await ensureVisible(page, "Tienes 2 de 3 negocios", "Agregar Negocio modal", report);

      const cancelButton = page.getByRole("button", { name: /Cancelar/i }).first();
      const createButton = page.getByRole("button", { name: /Crear Negocio/i }).first();
      expect(await cancelButton.isVisible().catch(() => false), "Cancelar button should be visible").toBeTruthy();
      expect(await createButton.isVisible().catch(() => false), "Crear Negocio button should be visible").toBeTruthy();

      await businessNameInput.click({ timeout: 15_000 });
      await businessNameInput.fill("Negocio Prueba Automatización");
      await captureCheckpoint(page, "03-agregar-negocio-modal");
      report.evidence.agregarNegocioModalScreenshot =
        "test-results/screenshots/03-agregar-negocio-modal.png";

      await cancelButton.click({ timeout: 15_000 });
      await waitForUi(page);
    });
  });

  await test.step("Step 4: Open Administrar Negocios and validate sections", async () => {
    await runStep("Administrar Negocios view", async () => {
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
        await clickByText(page, "Mi Negocio");
      }

      const clicked = await clickByText(page, "Administrar Negocios");
      expect(clicked, "Administrar Negocios should be clickable").toBeTruthy();

      await ensureVisible(page, "Información General", "Administrar Negocios view", report);
      await ensureVisible(page, "Detalles de la Cuenta", "Administrar Negocios view", report);
      await ensureVisible(page, "Tus Negocios", "Administrar Negocios view", report);
      await ensureVisible(page, "Sección Legal", "Administrar Negocios view", report);

      await captureCheckpoint(page, "04-administrar-negocios");
      report.evidence.administrarNegociosScreenshot =
        "test-results/screenshots/04-administrar-negocios.png";
    });
  });

  await test.step("Step 5: Validate Información General", async () => {
    await runStep("Información General", async () => {
      await ensureVisible(page, "BUSINESS PLAN", "Información General", report);
      const cambiarPlanVisible = await page
        .getByRole("button", { name: /Cambiar Plan/i })
        .first()
        .isVisible()
        .catch(() => false);
      expect(cambiarPlanVisible, "Cambiar Plan button should be visible").toBeTruthy();

      // Lightweight validation of visible profile identity.
      const emailVisible = await page
        .getByText(/@/i)
        .first()
        .isVisible()
        .catch(() => false);
      const userNameLikeVisible = await page
        .locator("h1, h2, h3, [data-testid*='name'], [class*='name']")
        .first()
        .isVisible()
        .catch(() => false);
      expect(emailVisible, "User email should be visible").toBeTruthy();
      expect(userNameLikeVisible, "User name should be visible").toBeTruthy();
    });
  });

  await test.step("Step 6: Validate Detalles de la Cuenta", async () => {
    await runStep("Detalles de la Cuenta", async () => {
      await ensureVisible(page, "Cuenta creada", "Detalles de la Cuenta", report);
      await ensureVisible(page, "Estado activo", "Detalles de la Cuenta", report);
      await ensureVisible(page, "Idioma seleccionado", "Detalles de la Cuenta", report);
    });
  });

  await test.step("Step 7: Validate Tus Negocios", async () => {
    await runStep("Tus Negocios", async () => {
      await ensureVisible(page, "Tus Negocios", "Tus Negocios", report);
      const addButtonVisible = await page
        .getByRole("button", { name: /Agregar Negocio/i })
        .first()
        .isVisible()
        .catch(() => false);
      expect(addButtonVisible, "Agregar Negocio button should exist in Tus Negocios").toBeTruthy();
      await ensureVisible(page, "Tienes 2 de 3 negocios", "Tus Negocios", report);
    });
  });

  await test.step("Step 8: Validate Términos y Condiciones", async () => {
    await runStep("Términos y Condiciones", async () => {
      const trigger = page
        .getByRole("link", { name: /T[eé]rminos y Condiciones/i })
        .or(page.getByRole("button", { name: /T[eé]rminos y Condiciones/i }))
        .first();
      const opensNewTab = await trigger
        .evaluate((el) => el.getAttribute("target") === "_blank")
        .catch(() => false);

      let legalPage = page;
      if (opensNewTab) {
        const [newPage] = await Promise.all([
          context.waitForEvent("page"),
          trigger.click({ timeout: 15_000 }),
        ]);
        legalPage = newPage;
        await legalPage.waitForLoadState("domcontentloaded");
      } else {
        await trigger.click({ timeout: 15_000 });
        await waitForUi(page);
      }

      await expect(
        legalPage.getByRole("heading", { name: /T[eé]rminos y Condiciones/i }).first(),
      ).toBeVisible();
      await expect(legalPage.locator("main, article, section, p").first()).toBeVisible();

      await captureCheckpoint(legalPage, "08-terminos-y-condiciones");
      report.evidence.terminosScreenshot = "test-results/screenshots/08-terminos-y-condiciones.png";
      report.evidence.terminosFinalUrl = legalPage.url();

      if (legalPage !== page) {
        await legalPage.close();
        await page.bringToFront();
        await waitForUi(page);
      }
    });
  });

  await test.step("Step 9: Validate Política de Privacidad", async () => {
    await runStep("Política de Privacidad", async () => {
      const trigger = page
        .getByRole("link", { name: /Pol[ií]tica de Privacidad/i })
        .or(page.getByRole("button", { name: /Pol[ií]tica de Privacidad/i }))
        .first();
      const opensNewTab = await trigger
        .evaluate((el) => el.getAttribute("target") === "_blank")
        .catch(() => false);

      let legalPage = page;
      if (opensNewTab) {
        const [newPage] = await Promise.all([
          context.waitForEvent("page"),
          trigger.click({ timeout: 15_000 }),
        ]);
        legalPage = newPage;
        await legalPage.waitForLoadState("domcontentloaded");
      } else {
        await trigger.click({ timeout: 15_000 });
        await waitForUi(page);
      }

      await expect(
        legalPage.getByRole("heading", { name: /Pol[ií]tica de Privacidad/i }).first(),
      ).toBeVisible();
      await expect(legalPage.locator("main, article, section, p").first()).toBeVisible();

      await captureCheckpoint(legalPage, "09-politica-de-privacidad");
      report.evidence.politicaScreenshot = "test-results/screenshots/09-politica-de-privacidad.png";
      report.evidence.politicaFinalUrl = legalPage.url();

      if (legalPage !== page) {
        await legalPage.close();
        await page.bringToFront();
        await waitForUi(page);
      }
    });
  });

  fs.writeFileSync(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  test.info().attach("saleads-mi-negocio-report", {
    path: REPORT_PATH,
    contentType: "application/json",
  });
  expect(
    stepErrors,
    `Some workflow validations failed:\n${stepErrors.join("\n")}`,
  ).toHaveLength(0);
});
