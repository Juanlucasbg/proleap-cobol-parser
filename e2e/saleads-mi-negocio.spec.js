const fs = require("node:fs/promises");
const path = require("node:path");
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
  "Política de Privacidad",
];

const SCREENSHOT_DIR = path.join("artifacts", "saleads-mi-negocio", "screenshots");
const REPORT_PATH = path.join("artifacts", "saleads-mi-negocio", "final-report.json");
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toRegex(value) {
  return value instanceof RegExp ? value : new RegExp(escapeRegExp(value), "i");
}

function toFileName(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 8000 });
  } catch (_error) {
    // SPA updates may never become fully idle; keep flow moving.
  }
  await page.waitForTimeout(800);
}

async function isVisible(locator) {
  try {
    return await locator.first().isVisible();
  } catch (_error) {
    return false;
  }
}

async function clickByVisibleText(scope, labels, stepLabel) {
  const timeoutMs = 15000;
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const candidates = [];
    for (const label of labels) {
      const regex = toRegex(label);
      candidates.push(scope.getByRole("button", { name: regex }).first());
      candidates.push(scope.getByRole("link", { name: regex }).first());
      candidates.push(scope.getByRole("menuitem", { name: regex }).first());
      candidates.push(scope.getByRole("tab", { name: regex }).first());
      candidates.push(scope.getByText(regex).first());
    }

    for (const candidate of candidates) {
      if (await isVisible(candidate)) {
        await candidate.click();
        if ("waitForLoadState" in scope) {
          await waitForUi(scope);
        }
        return;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 400));
  }

  throw new Error(`No clickable element found for step "${stepLabel}".`);
}

async function expectAnyVisible(scope, labels, assertionMessage) {
  const timeoutMs = 15000;
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const label of labels) {
      const regex = toRegex(label);
      const candidates = [
        scope.getByRole("heading", { name: regex }).first(),
        scope.getByRole("button", { name: regex }).first(),
        scope.getByRole("link", { name: regex }).first(),
        scope.getByText(regex).first(),
      ];

      for (const candidate of candidates) {
        if (await isVisible(candidate)) {
          return;
        }
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 400));
  }

  throw new Error(assertionMessage);
}

async function takeScreenshot(page, label, screenshots, fullPage = false) {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
  const fileName = `${Date.now()}-${toFileName(label)}.png`;
  const screenshotPath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  screenshots.push(screenshotPath);
}

async function executeStep(report, errors, key, action) {
  try {
    await action();
    report[key] = "PASS";
  } catch (error) {
    report[key] = "FAIL";
    errors[key] = error instanceof Error ? error.message : String(error);
  }
}

async function ensureOnLoginPage(page) {
  if (process.env.SALEADS_URL) {
    await page.goto(process.env.SALEADS_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "SALEADS_URL is not set. Provide SALEADS_URL to start from the current environment login page."
    );
  }
}

test("SaleADS Mi Negocio full workflow", async ({ page, context }) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const errors = {};
  const screenshots = [];
  const legalUrls = {};

  await executeStep(report, errors, "Login", async () => {
    await ensureOnLoginPage(page);

    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickByVisibleText(
      page,
      [
        /sign in with google/i,
        /iniciar sesi[oó]n con google/i,
        /continuar con google/i,
        /google/i,
      ],
      "Login with Google"
    );

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await isVisible(accountOption)) {
        await accountOption.click();
      }
      await popup.waitForTimeout(1200);
      try {
        await popup.close({ runBeforeUnload: true });
      } catch (_error) {
        // Ignore when the popup already auto-closed.
      }
      await page.bringToFront();
    } else if (/accounts\.google\.com/i.test(page.url())) {
      const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await isVisible(accountOption)) {
        await accountOption.click();
      }
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expectAnyVisible(
      page,
      [/negocio/i, /dashboard/i, /inicio/i],
      "Main application interface was not detected after Google login."
    );
    await takeScreenshot(page, "dashboard-loaded", screenshots, true);
  });

  await executeStep(report, errors, "Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expectAnyVisible(page, [/negocio/i], "Sidebar does not contain 'Negocio'.");
    await clickByVisibleText(page, [/mi negocio/i], "Open Mi Negocio menu");

    await expectAnyVisible(
      page,
      [/agregar negocio/i],
      "'Agregar Negocio' is not visible after expanding Mi Negocio."
    );
    await expectAnyVisible(
      page,
      [/administrar negocios/i],
      "'Administrar Negocios' is not visible after expanding Mi Negocio."
    );
    await takeScreenshot(page, "mi-negocio-menu-expanded", screenshots, true);
  });

  await executeStep(report, errors, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, [/agregar negocio/i], "Open Agregar Negocio modal");
    await expectAnyVisible(
      page,
      [/crear nuevo negocio/i],
      "Modal title 'Crear Nuevo Negocio' was not visible."
    );
    await expectAnyVisible(
      page,
      [/nombre del negocio/i],
      "Input 'Nombre del Negocio' was not visible."
    );
    await expectAnyVisible(
      page,
      [/tienes 2 de 3 negocios/i],
      "Text 'Tienes 2 de 3 negocios' was not visible in modal."
    );
    await expectAnyVisible(page, [/cancelar/i], "Button 'Cancelar' was not present.");
    await expectAnyVisible(page, [/crear negocio/i], "Button 'Crear Negocio' was not present.");

    if (await isVisible(page.getByLabel(/nombre del negocio/i).first())) {
      await page.getByLabel(/nombre del negocio/i).first().fill("Negocio Prueba Automatización");
    } else {
      const firstInput = page.locator("input").first();
      await expect(firstInput).toBeVisible();
      await firstInput.fill("Negocio Prueba Automatización");
    }

    await takeScreenshot(page, "agregar-negocio-modal", screenshots, true);
    await clickByVisibleText(page, [/cancelar/i], "Close Agregar Negocio modal");
    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeHidden();
    await waitForUi(page);
  });

  await executeStep(report, errors, "Administrar Negocios view", async () => {
    if (!(await isVisible(page.getByText(/administrar negocios/i).first()))) {
      await clickByVisibleText(page, [/mi negocio/i], "Re-open Mi Negocio menu");
    }

    await clickByVisibleText(page, [/administrar negocios/i], "Open Administrar Negocios");
    await expectAnyVisible(page, [/informaci[oó]n general/i], "Missing section 'Información General'.");
    await expectAnyVisible(page, [/detalles de la cuenta/i], "Missing section 'Detalles de la Cuenta'.");
    await expectAnyVisible(page, [/tus negocios/i], "Missing section 'Tus Negocios'.");
    await expectAnyVisible(page, [/secci[oó]n legal/i], "Missing section 'Sección Legal'.");
    await takeScreenshot(page, "administrar-negocios-page", screenshots, true);
  });

  await executeStep(report, errors, "Información General", async () => {
    await expectAnyVisible(page, [/business plan/i], "Text 'BUSINESS PLAN' was not visible.");
    await expectAnyVisible(page, [/cambiar plan/i], "Button 'Cambiar Plan' was not visible.");

    // Generic checks for account identity labels/values.
    await expectAnyVisible(page, [/nombre/i, /name/i], "User name label/value was not visible.");
    await expectAnyVisible(page, [/email/i, /@/], "User email label/value was not visible.");
  });

  await executeStep(report, errors, "Detalles de la Cuenta", async () => {
    await expectAnyVisible(page, [/cuenta creada/i], "'Cuenta creada' was not visible.");
    await expectAnyVisible(page, [/estado activo/i, /activo/i], "'Estado activo' was not visible.");
    await expectAnyVisible(
      page,
      [/idioma seleccionado/i, /idioma/i],
      "'Idioma seleccionado' was not visible."
    );
  });

  await executeStep(report, errors, "Tus Negocios", async () => {
    await expectAnyVisible(page, [/tus negocios/i], "Business list section title is not visible.");
    await expectAnyVisible(page, [/agregar negocio/i], "Button 'Agregar Negocio' was not visible.");
    await expectAnyVisible(page, [/tienes 2 de 3 negocios/i], "Capacity text was not visible.");
  });

  async function validateLegalLink(linkLabel, headingRegex, reportKey, screenshotLabel) {
    const appPage = page;
    const beforeUrl = appPage.url();
    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);

    await clickByVisibleText(appPage, [linkLabel], `Open ${reportKey}`);

    const popup = await popupPromise;
    const legalPage = popup || appPage;
    await waitForUi(legalPage);

    const headingLocator = legalPage.getByRole("heading", { name: headingRegex }).first();
    if (await isVisible(headingLocator)) {
      await expect(headingLocator).toBeVisible();
    } else {
      await expect(legalPage.getByText(headingRegex).first()).toBeVisible();
    }

    const legalContent = await legalPage.locator("body").innerText();
    expect(legalContent.trim().length).toBeGreaterThan(120);

    legalUrls[reportKey] = legalPage.url();
    await takeScreenshot(legalPage, screenshotLabel, screenshots, true);

    if (popup) {
      await popup.close();
      await appPage.bringToFront();
      await waitForUi(appPage);
      return;
    }

    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await legalPage.goto(beforeUrl, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(appPage);
  }

  await executeStep(report, errors, "Términos y Condiciones", async () => {
    await validateLegalLink(
      /t[ée]rminos y condiciones/i,
      /t[ée]rminos y condiciones/i,
      "Términos y Condiciones",
      "terminos-y-condiciones"
    );
  });

  await executeStep(report, errors, "Política de Privacidad", async () => {
    await validateLegalLink(
      /pol[íi]tica de privacidad/i,
      /pol[íi]tica de privacidad/i,
      "Política de Privacidad",
      "politica-de-privacidad"
    );
  });

  await fs.mkdir(path.dirname(REPORT_PATH), { recursive: true });
  const finalReport = {
    generatedAt: new Date().toISOString(),
    saleadsUrl: process.env.SALEADS_URL || null,
    finalStatus: Object.values(report).every((status) => status === "PASS") ? "PASS" : "FAIL",
    report,
    errors,
    legalUrls,
    screenshots,
  };
  await fs.writeFile(REPORT_PATH, JSON.stringify(finalReport, null, 2), "utf8");

  const failedFields = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);
  expect(
    failedFields,
    `Workflow failed for: ${failedFields.join(", ")}. See ${REPORT_PATH} for details.`
  ).toEqual([]);
});
