const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const SCREENSHOT_DIR = path.join("artifacts", TEST_NAME, "screenshots");
const REPORT_DIR = path.join("artifacts", TEST_NAME);
const REPORT_PATH = path.join(REPORT_DIR, "report.json");

const STEP_KEYS = {
  login: "Login",
  menu: "Mi Negocio menu",
  modal: "Agregar Negocio modal",
  admin: "Administrar Negocios view",
  infoGeneral: "Información General",
  detallesCuenta: "Detalles de la Cuenta",
  tusNegocios: "Tus Negocios",
  terminos: "Términos y Condiciones",
  privacidad: "Política de Privacidad",
};

function ensureArtifactsFolders() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function reportTemplate() {
  return {
    testName: TEST_NAME,
    startedAt: new Date().toISOString(),
    environmentUrlAtStart: null,
    checkpoints: [],
    finalUrls: {
      terminosYCondiciones: null,
      politicaDePrivacidad: null,
    },
    steps: Object.fromEntries(
      Object.values(STEP_KEYS).map((name) => [name, { status: "FAIL", details: [] }]),
    ),
    finishedAt: null,
  };
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function openLoginPageIfNeeded(page) {
  const currentUrl = page.url();
  if (currentUrl && currentUrl !== "about:blank") {
    return;
  }

  const dynamicStartUrl = process.env.SALEADS_URL || process.env.BASE_URL;
  if (!dynamicStartUrl) {
    throw new Error(
      "Browser started on about:blank. Provide SALEADS_URL (or BASE_URL) to open the current environment login page.",
    );
  }

  await page.goto(dynamicStartUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToLoad(page);
}

async function clickAndWaitUi(page, locator) {
  await expect(locator).toBeVisible({ timeout: 20000 });
  await locator.click();
  await waitForUiToLoad(page);
}

async function firstVisible(page, selectors) {
  for (const selector of selectors) {
    const loc = page.locator(selector).first();
    if (await loc.isVisible().catch(() => false)) {
      return loc;
    }
  }
  return null;
}

function markPass(report, stepName, detail) {
  report.steps[stepName].status = "PASS";
  if (detail) {
    report.steps[stepName].details.push(detail);
  }
}

function markFail(report, stepName, detail) {
  report.steps[stepName].status = "FAIL";
  if (detail) {
    report.steps[stepName].details.push(detail);
  }
}

async function captureCheckpoint(page, report, name, fullPage = false) {
  const fileName = `${String(report.checkpoints.length + 1).padStart(2, "0")}-${name}.png`;
  const filePath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  report.checkpoints.push({
    name,
    screenshot: filePath,
    url: page.url(),
    capturedAt: new Date().toISOString(),
  });
}

async function clickLegalLinkAndValidate({
  page,
  linkLabel,
  expectedHeadingRegex,
  report,
  reportKey,
  screenshotName,
}) {
  const link = page.getByRole("link", { name: linkLabel }).first();
  await expect(link).toBeVisible({ timeout: 20000 });

  const originalPage = page;
  const context = page.context();
  const pagesBefore = context.pages().length;

  await Promise.all([
    page.waitForTimeout(150),
    link.click(),
  ]);

  let legalPage = originalPage;
  // Give the browser a moment to open a tab if link uses target="_blank"
  await page.waitForTimeout(1200);
  const pagesAfter = context.pages();
  if (pagesAfter.length > pagesBefore) {
    legalPage = pagesAfter[pagesAfter.length - 1];
    await legalPage.bringToFront();
  }

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 30000 });
  await legalPage.waitForTimeout(900);

  const headingByRole = legalPage.getByRole("heading", { name: expectedHeadingRegex }).first();
  const headingByText = legalPage.getByText(expectedHeadingRegex).first();
  const headingVisible =
    (await headingByRole.isVisible().catch(() => false)) ||
    (await headingByText.isVisible().catch(() => false));

  expect(headingVisible).toBeTruthy();

  const bodyText = await legalPage.locator("body").innerText();
  expect(bodyText.trim().length).toBeGreaterThan(80);

  await captureCheckpoint(legalPage, report, screenshotName, true);
  report.finalUrls[reportKey] = legalPage.url();

  // Cleanup: return to app tab.
  if (legalPage !== originalPage) {
    await legalPage.close();
    await originalPage.bringToFront();
    await waitForUiToLoad(originalPage);
  }
}

test.describe(TEST_NAME, () => {
  test("Login by Google and validate full Mi Negocio workflow", async ({ page }) => {
    ensureArtifactsFolders();
    const report = reportTemplate();

    await openLoginPageIfNeeded(page);
    report.environmentUrlAtStart = page.url();
    await waitForUiToLoad(page);

    // Step 1 - Login with Google
    try {
      const signInButton =
        (await firstVisible(page, [
          "button:has-text('Sign in with Google')",
          "button:has-text('Iniciar sesión con Google')",
          "a:has-text('Sign in with Google')",
          "a:has-text('Iniciar sesión con Google')",
          "button:has-text('Google')",
          "a:has-text('Google')",
        ])) ||
        page.getByRole("button", { name: /google/i }).first();

      const popupPromise = page.context().waitForEvent("page", { timeout: 5000 }).catch(() => null);
      await clickAndWaitUi(page, signInButton);
      const authPopup = await popupPromise;

      if (authPopup) {
        await authPopup.waitForLoadState("domcontentloaded", { timeout: 30000 });
        await authPopup.bringToFront();

        const popupAccountCandidate = authPopup
          .locator("div[role='button'], li, div")
          .filter({ hasText: "juanlucasbarbiergarzon@gmail.com" })
          .first();

        if (await popupAccountCandidate.isVisible().catch(() => false)) {
          await popupAccountCandidate.click();
          await authPopup.waitForTimeout(700);
        }

        await page.bringToFront();
        await waitForUiToLoad(page);
      }

      const accountCandidate = page
        .locator("div[role='button'], li, div")
        .filter({ hasText: "juanlucasbarbiergarzon@gmail.com" })
        .first();

      if (await accountCandidate.isVisible().catch(() => false)) {
        await clickAndWaitUi(page, accountCandidate);
      }

      const sidebar = await firstVisible(page, [
        "nav",
        "aside",
        "[data-testid*='sidebar']",
        "text=Negocio",
      ]);
      await expect(sidebar).toBeVisible({ timeout: 45000 });

      await captureCheckpoint(page, report, "dashboard-loaded", true);
      markPass(report, STEP_KEYS.login, "Main interface and left navigation are visible.");
    } catch (error) {
      markFail(report, STEP_KEYS.login, String(error));
    }

    // Step 2 - Open Mi Negocio menu
    try {
      const negocioSection =
        (await firstVisible(page, [
          "button:has-text('Negocio')",
          "a:has-text('Negocio')",
          "text=Negocio",
        ])) || page.getByText("Negocio").first();
      await clickAndWaitUi(page, negocioSection);

      const miNegocioOption =
        (await firstVisible(page, [
          "button:has-text('Mi Negocio')",
          "a:has-text('Mi Negocio')",
          "text=Mi Negocio",
        ])) || page.getByText("Mi Negocio").first();
      await clickAndWaitUi(page, miNegocioOption);

      await expect(page.getByText("Agregar Negocio")).toBeVisible({ timeout: 20000 });
      await expect(page.getByText("Administrar Negocios")).toBeVisible({ timeout: 20000 });

      await captureCheckpoint(page, report, "mi-negocio-menu-expanded");
      markPass(report, STEP_KEYS.menu, "Mi Negocio submenu is expanded with both options visible.");
    } catch (error) {
      markFail(report, STEP_KEYS.menu, String(error));
    }

    // Step 3 - Validate Agregar Negocio modal
    try {
      const agregarNegocioMenuItem = page.getByText("Agregar Negocio").first();
      await clickAndWaitUi(page, agregarNegocioMenuItem);

      await expect(page.getByText("Crear Nuevo Negocio")).toBeVisible({ timeout: 20000 });
      await expect(page.getByLabel("Nombre del Negocio")).toBeVisible({ timeout: 20000 });
      await expect(page.getByText("Tienes 2 de 3 negocios")).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: "Crear Negocio" })).toBeVisible({ timeout: 20000 });

      const nameField = page.getByLabel("Nombre del Negocio");
      await nameField.click();
      await nameField.fill("Negocio Prueba Automatización");

      await captureCheckpoint(page, report, "agregar-negocio-modal");
      await clickAndWaitUi(page, page.getByRole("button", { name: "Cancelar" }));
      markPass(report, STEP_KEYS.modal, "Modal content and controls validated successfully.");
    } catch (error) {
      markFail(report, STEP_KEYS.modal, String(error));
    }

    // Step 4 - Open Administrar Negocios
    try {
      const miNegocioOption =
        (await firstVisible(page, [
          "button:has-text('Mi Negocio')",
          "a:has-text('Mi Negocio')",
          "text=Mi Negocio",
        ])) || page.getByText("Mi Negocio").first();

      if (!(await page.getByText("Administrar Negocios").first().isVisible().catch(() => false))) {
        await clickAndWaitUi(page, miNegocioOption);
      }

      await clickAndWaitUi(page, page.getByText("Administrar Negocios").first());

      await expect(page.getByText("Información General")).toBeVisible({ timeout: 30000 });
      await expect(page.getByText("Detalles de la Cuenta")).toBeVisible({ timeout: 30000 });
      await expect(page.getByText("Tus Negocios")).toBeVisible({ timeout: 30000 });
      await expect(page.getByText("Sección Legal")).toBeVisible({ timeout: 30000 });

      await captureCheckpoint(page, report, "administrar-negocios-page", true);
      markPass(report, STEP_KEYS.admin, "All required sections are displayed.");
    } catch (error) {
      markFail(report, STEP_KEYS.admin, String(error));
    }

    // Step 5 - Validate Información General
    try {
      await expect(page.getByText("Información General")).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/@/)).toBeVisible({ timeout: 20000 });
      await expect(page.getByText("BUSINESS PLAN")).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: "Cambiar Plan" })).toBeVisible({ timeout: 20000 });
      markPass(report, STEP_KEYS.infoGeneral, "User data, plan label, and action button are visible.");
    } catch (error) {
      markFail(report, STEP_KEYS.infoGeneral, String(error));
    }

    // Step 6 - Validate Detalles de la Cuenta
    try {
      await expect(page.getByText("Cuenta creada")).toBeVisible({ timeout: 20000 });
      await expect(page.getByText("Estado activo")).toBeVisible({ timeout: 20000 });
      await expect(page.getByText("Idioma seleccionado")).toBeVisible({ timeout: 20000 });
      markPass(report, STEP_KEYS.detallesCuenta, "All account detail labels are visible.");
    } catch (error) {
      markFail(report, STEP_KEYS.detallesCuenta, String(error));
    }

    // Step 7 - Validate Tus Negocios
    try {
      await expect(page.getByText("Tus Negocios")).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: "Agregar Negocio" })).toBeVisible({ timeout: 20000 });
      await expect(page.getByText("Tienes 2 de 3 negocios")).toBeVisible({ timeout: 20000 });
      markPass(report, STEP_KEYS.tusNegocios, "Business list section and limits are visible.");
    } catch (error) {
      markFail(report, STEP_KEYS.tusNegocios, String(error));
    }

    // Step 8 - Validate Términos y Condiciones
    try {
      await clickLegalLinkAndValidate({
        page,
        linkLabel: "Términos y Condiciones",
        expectedHeadingRegex: /Términos y Condiciones/i,
        report,
        reportKey: "terminosYCondiciones",
        screenshotName: "terminos-y-condiciones",
      });
      markPass(report, STEP_KEYS.terminos, "Legal page heading/content verified and URL captured.");
    } catch (error) {
      markFail(report, STEP_KEYS.terminos, String(error));
    }

    // Step 9 - Validate Política de Privacidad
    try {
      await clickLegalLinkAndValidate({
        page,
        linkLabel: "Política de Privacidad",
        expectedHeadingRegex: /Política de Privacidad/i,
        report,
        reportKey: "politicaDePrivacidad",
        screenshotName: "politica-de-privacidad",
      });
      markPass(report, STEP_KEYS.privacidad, "Privacy page heading/content verified and URL captured.");
    } catch (error) {
      markFail(report, STEP_KEYS.privacidad, String(error));
    }

    report.finishedAt = new Date().toISOString();
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
    console.log(`Final report written to ${REPORT_PATH}`);
    console.log(JSON.stringify(report.steps, null, 2));

    const failed = Object.entries(report.steps).filter(([, value]) => value.status === "FAIL");
    expect(failed, `Some validations failed. Report: ${REPORT_PATH}`).toHaveLength(0);
  });
});
