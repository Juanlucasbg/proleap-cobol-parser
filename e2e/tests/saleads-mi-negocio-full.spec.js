const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || "";

const SCREENSHOT_DIR = path.join(
  __dirname,
  "..",
  "screenshots",
  "saleads-mi-negocio"
);
const REPORT_PATH = path.join(
  __dirname,
  "..",
  "test-results",
  "saleads-mi-negocio-report.json"
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
  "Política de Privacidad"
];

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function clickByVisibleText(page, text) {
  const exactPattern = new RegExp(`^${escapeRegExp(text)}$`, "i");
  const containsPattern = new RegExp(escapeRegExp(text), "i");

  const locators = [
    page.getByRole("button", { name: exactPattern }),
    page.getByRole("link", { name: exactPattern }),
    page.getByRole("menuitem", { name: exactPattern }),
    page.getByRole("tab", { name: exactPattern }),
    page.getByText(exactPattern),
    page.getByText(containsPattern)
  ];

  for (const locator of locators) {
    try {
      await locator.first().click({ timeout: 2500 });
      await waitForUiToLoad(page);
      return;
    } catch (error) {
      // Try next visible-text strategy.
    }
  }

  throw new Error(`Could not click element with visible text "${text}".`);
}

async function clickAny(page, labels) {
  for (const label of labels) {
    try {
      await clickByVisibleText(page, label);
      return label;
    } catch (error) {
      // Keep trying alternatives.
    }
  }

  throw new Error(`Could not click any of: ${labels.join(", ")}.`);
}

async function captureScreenshot(page, report, name, fullPage = false) {
  const destination = path.join(SCREENSHOT_DIR, name);
  await page.screenshot({ path: destination, fullPage });
  report.evidence.screenshots.push(destination);
}

async function runStep(report, label, callback) {
  try {
    await callback();
    report.results[label] = {
      status: "PASS"
    };
  } catch (error) {
    report.results[label] = {
      status: "FAIL",
      details: error.message
    };
  }
}

async function validateLegalPage({
  page,
  context,
  linkText,
  headingText,
  screenshotName,
  report,
  urlKey,
  returnUrl
}) {
  const popupPromise = context
    .waitForEvent("page", { timeout: 8000 })
    .catch(() => null);

  await clickByVisibleText(page, linkText);

  const popup = await popupPromise;
  const legalPage = popup || page;

  await waitForUiToLoad(legalPage);

  const headingPattern = new RegExp(escapeRegExp(headingText), "i");
  const headingLocator = legalPage.getByRole("heading", { name: headingPattern });

  if ((await headingLocator.count()) > 0) {
    await expect(headingLocator.first()).toBeVisible();
  } else {
    await expect(legalPage.getByText(headingPattern).first()).toBeVisible();
  }

  const legalTextLength = await legalPage.evaluate(() => {
    const text = (document.body && document.body.innerText) || "";
    return text.replace(/\s+/g, " ").trim().length;
  });
  expect(legalTextLength).toBeGreaterThan(120);

  await captureScreenshot(legalPage, report, screenshotName, true);
  report.evidence.finalUrls[urlKey] = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
    return;
  }

  if (returnUrl && page.url() !== returnUrl) {
    await page.goto(returnUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToLoad(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  ensureDir(SCREENSHOT_DIR);
  ensureDir(path.dirname(REPORT_PATH));

  const report = {
    testName: "saleads_mi_negocio_full_test",
    startedAt: new Date().toISOString(),
    loginUrl: SALEADS_LOGIN_URL || "NOT_PROVIDED",
    evidence: {
      screenshots: [],
      finalUrls: {}
    },
    results: {}
  };

  try {
    if (!SALEADS_LOGIN_URL) {
      throw new Error(
        "Missing SALEADS_LOGIN_URL. Provide a login page URL for the target SaleADS environment."
      );
    }

    await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUiToLoad(page);

    await runStep(report, "Login", async () => {
      const sidebar = page.locator("aside");

      if (!(await sidebar.first().isVisible().catch(() => false))) {
        const googlePopupPromise = context
          .waitForEvent("page", { timeout: 10000 })
          .catch(() => null);

        await clickAny(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Login con Google",
          "Google"
        ]);

        const googlePopup = await googlePopupPromise;
        if (googlePopup) {
          await waitForUiToLoad(googlePopup);
          await clickAny(googlePopup, [GOOGLE_ACCOUNT_EMAIL]);
          await googlePopup.waitForEvent("close", { timeout: 15000 }).catch(() => {});
          await page.bringToFront();
          await waitForUiToLoad(page);
        }
      }

      await expect(page.locator("aside").first()).toBeVisible();
      await expect(page.getByText(/Negocio/i).first()).toBeVisible();
      await captureScreenshot(page, report, "01-dashboard-loaded.png");
    });

    await runStep(report, "Mi Negocio menu", async () => {
      await clickByVisibleText(page, "Negocio");
      await clickByVisibleText(page, "Mi Negocio");

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await captureScreenshot(page, report, "02-mi-negocio-expanded.png");
    });

    await runStep(report, "Agregar Negocio modal", async () => {
      await clickByVisibleText(page, "Agregar Negocio");

      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();

      const nameInputByLabel = page.getByLabel(/Nombre del Negocio/i);
      const nameInputByPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i);
      if ((await nameInputByLabel.count()) > 0) {
        await expect(nameInputByLabel.first()).toBeVisible();
        await nameInputByLabel.first().click();
        await nameInputByLabel.first().fill("Negocio Prueba Automatización");
      } else {
        await expect(nameInputByPlaceholder.first()).toBeVisible();
        await nameInputByPlaceholder.first().click();
        await nameInputByPlaceholder.first().fill("Negocio Prueba Automatización");
      }

      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      await captureScreenshot(page, report, "03-crear-negocio-modal.png");
      await clickByVisibleText(page, "Cancelar");
    });

    await runStep(report, "Administrar Negocios view", async () => {
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
        await clickByVisibleText(page, "Mi Negocio");
      }

      await clickByVisibleText(page, "Administrar Negocios");
      await waitForUiToLoad(page);

      await expect(page.getByText(/Información General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

      report.evidence.finalUrls.applicationAccountPage = page.url();
      await captureScreenshot(page, report, "04-administrar-negocios-view.png", true);
    });

    await runStep(report, "Información General", async () => {
      const infoSection = page
        .locator("section,div")
        .filter({ hasText: /Información General/i })
        .first();
      await expect(infoSection).toBeVisible();

      const infoText = await infoSection.innerText();
      const emailRegex = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
      expect(emailRegex.test(infoText)).toBeTruthy();
      expect(infoText).toMatch(/BUSINESS PLAN/i);

      const cambiarPlanButton = page.getByRole("button", { name: /Cambiar Plan/i });
      const cambiarPlanLink = page.getByRole("link", { name: /Cambiar Plan/i });
      if ((await cambiarPlanButton.count()) > 0) {
        await expect(cambiarPlanButton.first()).toBeVisible();
      } else {
        await expect(cambiarPlanLink.first()).toBeVisible();
      }

      const visibleLines = infoText
        .split("\n")
        .map((line) => line.trim())
        .filter((line) => line.length > 0);
      expect(visibleLines.length).toBeGreaterThan(3);
    });

    await runStep(report, "Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });

    await runStep(report, "Tus Negocios", async () => {
      const businessSection = page
        .locator("section,div")
        .filter({ hasText: /Tus Negocios/i })
        .first();
      await expect(businessSection).toBeVisible();

      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();

      const businessText = await businessSection.innerText();
      expect(businessText.replace(/\s+/g, " ").trim().length).toBeGreaterThan(30);
    });

    await runStep(report, "Términos y Condiciones", async () => {
      await validateLegalPage({
        page,
        context,
        linkText: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        screenshotName: "05-terminos-y-condiciones.png",
        report,
        urlKey: "terminosYCondiciones",
        returnUrl: report.evidence.finalUrls.applicationAccountPage
      });
    });

    await runStep(report, "Política de Privacidad", async () => {
      await validateLegalPage({
        page,
        context,
        linkText: "Política de Privacidad",
        headingText: "Política de Privacidad",
        screenshotName: "06-politica-de-privacidad.png",
        report,
        urlKey: "politicaDePrivacidad",
        returnUrl: report.evidence.finalUrls.applicationAccountPage
      });
    });
  } finally {
    for (const field of REPORT_FIELDS) {
      if (!report.results[field]) {
        report.results[field] = {
          status: "FAIL",
          details: "Step was not executed."
        };
      }
    }

    report.finishedAt = new Date().toISOString();
    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
  }

  const failedFields = Object.entries(report.results)
    .filter(([, result]) => result.status !== "PASS")
    .map(([field]) => field);

  expect(
    failedFields,
    `Validation failed for: ${failedFields.join(", ")}. Check ${REPORT_PATH} for details.`
  ).toHaveLength(0);
});
