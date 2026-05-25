const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function sanitizeFilePart(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function initReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      { status: "FAIL", details: [], evidence: [] },
    ]),
  );
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function isVisible(locator, timeout = 2000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch (_error) {
    return false;
  }
}

async function findClickableByVisibleText(pageOrLocator, textOptions) {
  for (const text of textOptions) {
    const exactTextPattern = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
    const candidates = [
      pageOrLocator.getByRole("button", { name: exactTextPattern }).first(),
      pageOrLocator.getByRole("link", { name: exactTextPattern }).first(),
      pageOrLocator.getByRole("menuitem", { name: exactTextPattern }).first(),
      pageOrLocator.getByText(exactTextPattern).first(),
      pageOrLocator
        .locator("button, a, [role='button'], [role='link'], [role='menuitem']")
        .filter({ hasText: exactTextPattern })
        .first(),
    ];

    for (const candidate of candidates) {
      if (await isVisible(candidate, 1200)) {
        return candidate;
      }
    }
  }

  throw new Error(
    `Could not find a visible clickable element with text: ${textOptions.join(
      ", ",
    )}`,
  );
}

async function findSectionByTitle(page, title) {
  const titlePattern = new RegExp(`^\\s*${escapeRegex(title)}\\s*$`, "i");
  const heading = page.getByText(titlePattern).first();
  await expect(heading).toBeVisible();

  const section = page
    .locator("section, article, div")
    .filter({ has: heading })
    .first();
  if (await isVisible(section, 1500)) {
    return section;
  }

  return page.locator("body");
}

async function maybeSelectGoogleAccount(authPage) {
  const accountChoice = authPage
    .getByText(new RegExp(`^\\s*${escapeRegex(ACCOUNT_EMAIL)}\\s*$`, "i"))
    .first();

  if (await isVisible(accountChoice, 10000)) {
    await accountChoice.click();
    await waitForUi(authPage);
  }
}

async function ensureStartingPage(page) {
  if (page.url() !== "about:blank") {
    return;
  }

  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL;

  if (!loginUrl) {
    throw new Error(
      "Browser is on about:blank. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to run in any SaleADS environment.",
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.join(
    process.cwd(),
    "e2e-artifacts",
    "saleads-mi-negocio-full-test",
    runId,
  );
  fs.mkdirSync(artifactsDir, { recursive: true });

  const report = initReport();

  async function checkpoint(field, label, screenshotPage = page, fullPage = false) {
    const screenshotName = `${sanitizeFilePart(field)}-${sanitizeFilePart(
      label,
    )}.png`;
    const screenshotPath = path.join(artifactsDir, screenshotName);
    await screenshotPage.screenshot({ path: screenshotPath, fullPage });
    report[field].evidence.push(screenshotPath);
  }

  async function runStep(field, executor) {
    try {
      await executor();
      report[field].status = "PASS";
    } catch (error) {
      report[field].status = "FAIL";
      report[field].details.push(error.message);
    }
  }

  await runStep("Login", async () => {
    await ensureStartingPage(page);

    const loginButton = await findClickableByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Login with Google",
    ]);

    const popupPromise = context
      .waitForEvent("page", { timeout: 12000 })
      .catch(() => null);

    await clickAndWait(page, loginButton);

    const authPopup = await popupPromise;
    if (authPopup) {
      await authPopup.waitForLoadState("domcontentloaded");
      await maybeSelectGoogleAccount(authPopup);
    } else {
      await maybeSelectGoogleAccount(page);
    }

    await expect(page.getByText(/negocio|dashboard|inicio/i).first()).toBeVisible({
      timeout: 90000,
    });
    await expect(page.locator("aside, nav").first()).toBeVisible({
      timeout: 90000,
    });
    await checkpoint("Login", "dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioOption = await findClickableByVisibleText(page, ["Negocio"]);
    await clickAndWait(page, negocioOption);

    const miNegocioOption = await findClickableByVisibleText(page, [
      "Mi Negocio",
    ]);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await checkpoint("Mi Negocio menu", "menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusinessOption = await findClickableByVisibleText(page, [
      "Agregar Negocio",
    ]);
    await clickAndWait(page, addBusinessOption);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i).first()).toBeVisible();
    const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(
      page.getByRole("button", { name: /^Crear Negocio$/i }).first(),
    ).toBeVisible();

    await checkpoint("Agregar Negocio modal", "modal-open");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(
      page,
      page.getByRole("button", { name: /^Cancelar$/i }).first(),
    );
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await isVisible(
      page.getByText(/^Administrar Negocios$/i).first(),
      2000,
    );
    if (!administrarVisible) {
      const miNegocioOption = await findClickableByVisibleText(page, [
        "Mi Negocio",
      ]);
      await clickAndWait(page, miNegocioOption);
    }

    const manageBusinessesOption = await findClickableByVisibleText(page, [
      "Administrar Negocios",
    ]);
    await clickAndWait(page, manageBusinessesOption);

    await expect(page.getByText(/^Información General$/i).first()).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
    await expect(page.getByText(/^Sección Legal$/i).first()).toBeVisible();
    await checkpoint("Administrar Negocios view", "account-page", page, true);
  });

  await runStep("Información General", async () => {
    const infoSection = await findSectionByTitle(page, "Información General");
    await expect(
      infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first(),
    ).toBeVisible();

    const sectionTexts = (await infoSection.locator("*").allInnerTexts())
      .map((text) => text.trim())
      .filter(Boolean);
    const userName = sectionTexts.find(
      (text) =>
        text.split(/\s+/).length >= 2 &&
        !text.includes("@") &&
        !/BUSINESS PLAN/i.test(text),
    );
    expect(userName, "User name is visible").toBeTruthy();

    await expect(infoSection.getByText(/^BUSINESS PLAN$/i).first()).toBeVisible();
    await expect(
      infoSection.getByRole("button", { name: /^Cambiar Plan$/i }).first(),
    ).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = await findSectionByTitle(page, "Detalles de la Cuenta");
    await expect(detailsSection.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(
      detailsSection.getByText(/Idioma seleccionado/i).first(),
    ).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const businessesSection = await findSectionByTitle(page, "Tus Negocios");
    await expect(
      businessesSection.getByRole("button", { name: /^Agregar Negocio$/i }).first(),
    ).toBeVisible();
    await expect(
      businessesSection.getByText(/Tienes 2 de 3 negocios/i).first(),
    ).toBeVisible();

    const businessEntries = businessesSection.locator(
      "li, tr, [role='listitem'], [role='row'], [data-testid*='business'], [class*='business']",
    );
    const businessEntriesCount = await businessEntries.count();
    if (businessEntriesCount === 0) {
      const businessSectionText = (await businessesSection.innerText()).trim();
      expect(
        businessSectionText.length > 60,
        "Business list content is visible in 'Tus Negocios'",
      ).toBeTruthy();
    }
  });

  async function validateLegalLink(field, linkText, headingText) {
    const legalSection = await findSectionByTitle(page, "Sección Legal");
    const legalLink = await findClickableByVisibleText(legalSection, [linkText]);
    const applicationUrlBeforeClick = page.url();

    const popupPromise = context
      .waitForEvent("page", { timeout: 12000 })
      .catch(() => null);

    await clickAndWait(page, legalLink);

    const popupPage = await popupPromise;
    const legalPage = popupPage || page;
    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUi(legalPage);

    await expect(legalPage.getByText(new RegExp(headingText, "i")).first()).toBeVisible();

    const legalText = (await legalPage.locator("body").innerText()).trim();
    expect(legalText.length > 200, "Legal content text is visible").toBeTruthy();

    report[field].details.push(`Final URL: ${legalPage.url()}`);
    await checkpoint(field, "legal-page", legalPage, true);

    if (popupPage) {
      await popupPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (page.url() !== applicationUrlBeforeClick) {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  }

  await runStep("Términos y Condiciones", async () => {
    await validateLegalLink(
      "Términos y Condiciones",
      "Términos y Condiciones",
      "Términos y Condiciones",
    );
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalLink(
      "Política de Privacidad",
      "Política de Privacidad",
      "Política de Privacidad",
    );
  });

  const finalResults = REPORT_FIELDS.map((field) => ({
    field,
    status: report[field].status,
    details: report[field].details,
    evidence: report[field].evidence,
  }));
  const finalReport = {
    test: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: finalResults,
  };
  const finalReportPath = path.join(artifactsDir, "final-report.json");
  fs.writeFileSync(finalReportPath, JSON.stringify(finalReport, null, 2));

  await test.info().attach("mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  console.log("Mi Negocio final report:", JSON.stringify(finalResults, null, 2));

  const failures = finalResults.filter((entry) => entry.status !== "PASS");
  expect(failures, "One or more workflow validations failed").toEqual([]);
});
