const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const TEST_BUSINESS_NAME = process.env.SALEADS_TEST_BUSINESS_NAME || "Negocio Prueba Automatización";

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
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
}

function ensureDir(relativePath) {
  const absolutePath = path.resolve(__dirname, "..", relativePath);
  fs.mkdirSync(absolutePath, { recursive: true });
  return absolutePath;
}

function sanitizeName(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function saveCheckpoint(page, testInfo, checkpointName, fullPage = false) {
  const screenshotDir = ensureDir("../artifacts/screenshots");
  const filename = `${sanitizeName(checkpointName)}-${Date.now()}.png`;
  const screenshotPath = path.join(screenshotDir, filename);

  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(checkpointName, {
    path: screenshotPath,
    contentType: "image/png"
  });

  return screenshotPath;
}

async function clickFirstVisible(candidates, actionName) {
  for (const candidate of candidates) {
    const locator = candidate();
    const isVisible = await locator.first().isVisible().catch(() => false);
    if (!isVisible) {
      continue;
    }

    await locator.first().click();
    return locator.first();
  }

  throw new Error(`No visible element found for action: ${actionName}`);
}

function getClosestSection(headingLocator) {
  return headingLocator.locator("xpath=ancestor::*[self::section or self::div][1]");
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "" }]));
  const legalUrls = {};
  const errors = [];

  async function runBlock(fieldName, fn) {
    try {
      await fn();
      report[fieldName] = { status: "PASS", details: "" };
    } catch (error) {
      report[fieldName] = { status: "FAIL", details: error.message };
      errors.push(`${fieldName}: ${error.message}`);
    }
  }

  // Optional navigation by environment variable (keeps the test environment-agnostic).
  const startUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  }

  await runBlock("Login", async () => {
    const loginPopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickFirstVisible(
      [
        () => page.getByRole("button", { name: /sign in with google|iniciar sesión con google|continuar con google/i }),
        () => page.getByRole("link", { name: /sign in with google|iniciar sesión con google|continuar con google/i }),
        () => page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i)
      ],
      "Login with Google"
    );

    const loginPopup = await loginPopupPromise;
    const loginPage = loginPopup || page;
    await waitForUi(loginPage);

    const accountLocator = loginPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    if (await accountLocator.isVisible().catch(() => false)) {
      await accountLocator.click();
    }

    if (loginPopup) {
      await Promise.race([
        loginPopup.waitForEvent("close", { timeout: 30000 }).catch(() => null),
        page.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => null)
      ]);
    }

    await waitForUi(page);

    // Main application interface + left sidebar validation.
    await expect(page.locator("main, [role='main']").first()).toBeVisible();
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await saveCheckpoint(page, testInfo, "dashboard-loaded");
  });

  await runBlock("Mi Negocio menu", async () => {
    await clickFirstVisible(
      [
        () => page.getByRole("button", { name: /Negocio/i }),
        () => page.getByRole("link", { name: /Negocio/i }),
        () => page.getByText(/^Negocio$/i)
      ],
      "Open Negocio section"
    );
    await waitForUi(page);

    await clickFirstVisible(
      [
        () => page.getByRole("button", { name: /Mi Negocio/i }),
        () => page.getByRole("link", { name: /Mi Negocio/i }),
        () => page.getByText(/Mi Negocio/i)
      ],
      "Open Mi Negocio menu"
    );
    await waitForUi(page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await saveCheckpoint(page, testInfo, "mi-negocio-menu-expanded");
  });

  await runBlock("Agregar Negocio modal", async () => {
    await clickFirstVisible(
      [
        () => page.getByRole("button", { name: /^Agregar Negocio$/i }),
        () => page.getByRole("link", { name: /^Agregar Negocio$/i }),
        () => page.getByText(/^Agregar Negocio$/i)
      ],
      "Open Agregar Negocio modal"
    );
    await waitForUi(page);

    await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first()).toBeVisible();

    const businessNameInput = page
      .getByLabel(/Nombre del Negocio/i)
      .or(page.getByPlaceholder(/Nombre del Negocio/i))
      .first();
    await expect(businessNameInput).toBeVisible();

    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    await businessNameInput.click();
    await businessNameInput.fill(TEST_BUSINESS_NAME);
    await saveCheckpoint(page, testInfo, "agregar-negocio-modal");

    await clickFirstVisible(
      [
        () => page.getByRole("button", { name: /Cancelar/i }),
        () => page.getByText(/^Cancelar$/i)
      ],
      "Close Agregar Negocio modal"
    );
    await waitForUi(page);
  });

  await runBlock("Administrar Negocios view", async () => {
    const administrarOption = page.getByText(/Administrar Negocios/i).first();
    if (!(await administrarOption.isVisible().catch(() => false))) {
      await clickFirstVisible(
        [
          () => page.getByRole("button", { name: /Mi Negocio/i }),
          () => page.getByRole("link", { name: /Mi Negocio/i }),
          () => page.getByText(/Mi Negocio/i)
        ],
        "Re-open Mi Negocio menu"
      );
      await waitForUi(page);
    }

    await clickFirstVisible(
      [
        () => page.getByRole("link", { name: /Administrar Negocios/i }),
        () => page.getByRole("button", { name: /Administrar Negocios/i }),
        () => page.getByText(/Administrar Negocios/i)
      ],
      "Open Administrar Negocios"
    );
    await waitForUi(page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
    await saveCheckpoint(page, testInfo, "administrar-negocios-page", true);
  });

  await runBlock("Información General", async () => {
    const heading = page.getByText(/Información General/i).first();
    await expect(heading).toBeVisible();

    const section = getClosestSection(heading);
    const sectionText = (await section.innerText()).replace(/\s+/g, " ").trim();

    const hasEmail = /[^\s@]+@[^\s@]+\.[^\s@]+/.test(sectionText);
    expect(hasEmail).toBeTruthy();

    const lines = sectionText
      .split(" ")
      .map((word) => word.trim())
      .filter(Boolean);
    expect(lines.length).toBeGreaterThan(6);

    await expect(section.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(section.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runBlock("Detalles de la Cuenta", async () => {
    const section = getClosestSection(page.getByText(/Detalles de la Cuenta/i).first());
    await expect(section.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(section.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(section.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runBlock("Tus Negocios", async () => {
    const section = getClosestSection(page.getByText(/Tus Negocios/i).first());
    await expect(section).toBeVisible();
    await expect(section.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(section.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

    const listCandidates = [
      section.locator("[role='list']"),
      section.locator("ul"),
      section.locator("table"),
      section.locator("[data-testid*='business']")
    ];
    const listVisible = (
      await Promise.all(listCandidates.map((candidate) => candidate.first().isVisible().catch(() => false)))
    ).some(Boolean);
    expect(listVisible).toBeTruthy();
  });

  async function validateLegalLink(linkRegex, headingRegex, reportKey, checkpointName) {
    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await clickFirstVisible(
      [
        () => page.getByRole("link", { name: linkRegex }),
        () => page.getByText(linkRegex)
      ],
      `Open legal link ${reportKey}`
    );

    const popup = await popupPromise;
    const legalPage = popup || page;
    await waitForUi(legalPage);

    const heading = legalPage.getByRole("heading", { name: headingRegex }).first();
    const fallbackHeading = legalPage.getByText(headingRegex).first();
    const headingVisible =
      (await heading.isVisible().catch(() => false)) || (await fallbackHeading.isVisible().catch(() => false));
    expect(headingVisible).toBeTruthy();

    const contentVisible =
      (await legalPage.locator("article p, main p, p").first().isVisible().catch(() => false)) ||
      (await legalPage.locator("main li, article li").first().isVisible().catch(() => false));
    expect(contentVisible).toBeTruthy();

    await saveCheckpoint(legalPage, testInfo, checkpointName);
    legalUrls[reportKey] = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  }

  await runBlock("Términos y Condiciones", async () => {
    await validateLegalLink(
      /Términos y Condiciones|Terminos y Condiciones/i,
      /Términos y Condiciones|Terminos y Condiciones/i,
      "Términos y Condiciones",
      "terminos-y-condiciones"
    );
  });

  await runBlock("Política de Privacidad", async () => {
    await validateLegalLink(
      /Política de Privacidad|Politica de Privacidad/i,
      /Política de Privacidad|Politica de Privacidad/i,
      "Política de Privacidad",
      "politica-de-privacidad"
    );
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    accountUsed: GOOGLE_ACCOUNT_EMAIL,
    results: report,
    legalUrls
  };

  const reportDir = ensureDir("../artifacts");
  const reportPath = path.join(reportDir, "saleads-mi-negocio-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log("SaleADS Mi Negocio final report:");
  console.table(
    Object.entries(report).map(([key, value]) => ({
      step: key,
      status: value.status,
      details: value.details
    }))
  );
  if (Object.keys(legalUrls).length > 0) {
    console.log("Validated legal URLs:", legalUrls);
  }

  expect(errors, `Validation failures:\n${errors.join("\n")}`).toEqual([]);
});
