const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad"
];

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "NOT_EXECUTED"]));
  const evidence = {};

  const screenshotsDir = path.join(testInfo.outputDir, "screenshots");
  fs.mkdirSync(screenshotsDir, { recursive: true });

  await runStep(report, "Login", async () => {
    await openLoginPageIfNeeded(page);

    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesion con Google",
      "Iniciar sesion",
      "Continuar con Google",
      "Google"
    ]);

    const popupPage = await popupPromise;
    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded", { timeout: 20_000 });
      await selectGoogleAccountIfPrompted(popupPage);
      await popupPage.waitForTimeout(500);
    } else {
      await selectGoogleAccountIfPrompted(page);
    }

    await waitForUiToSettle(page);
    await expect(getSidebarLocator(page)).toBeVisible({ timeout: 40_000 });
    await takeCheckpoint(page, screenshotsDir, "01-dashboard-loaded");
  });

  if (report.Login !== "PASS") {
    markRemainingAsBlocked(report, "Login");
    await finalizeAndAssertReport(testInfo, report, evidence);
    return;
  }

  await runStep(report, "Mi Negocio menu", async () => {
    await expect(getSidebarLocator(page)).toBeVisible();
    await expect(page.getByText("Negocio", { exact: false }).first()).toBeVisible();

    await clickByVisibleText(page, ["Mi Negocio"]);
    await waitForUiToSettle(page);

    await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: false }).first()).toBeVisible();
    await takeCheckpoint(page, screenshotsDir, "02-mi-negocio-expanded");
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, ["Agregar Negocio"]);
    await waitForUiToSettle(page);

    const modal = page.getByRole("dialog").first();
    await expect(modal).toBeVisible({ timeout: 20_000 });
    await expect(modal.getByText("Crear Nuevo Negocio", { exact: false })).toBeVisible();
    await expect(modal.getByText("Nombre del Negocio", { exact: false })).toBeVisible();
    await expect(modal.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await takeCheckpoint(page, screenshotsDir, "03-crear-negocio-modal");

    const businessNameField = modal
      .locator("input, textarea")
      .filter({ has: modal.getByText("Nombre del Negocio", { exact: false }) })
      .first();

    if (await businessNameField.isVisible().catch(() => false)) {
      await businessNameField.fill("Negocio Prueba Automatizacion");
    } else {
      const firstInput = modal.locator("input, textarea").first();
      if (await firstInput.isVisible().catch(() => false)) {
        await firstInput.fill("Negocio Prueba Automatizacion");
      }
    }

    await modal.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUiToSettle(page);
    await expect(modal).toBeHidden({ timeout: 10_000 });
  });

  await runStep(report, "Administrar Negocios view", async () => {
    if (!(await page.getByText("Administrar Negocios", { exact: false }).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, ["Mi Negocio"]);
      await waitForUiToSettle(page);
    }

    await clickByVisibleText(page, ["Administrar Negocios"]);
    await waitForUiToSettle(page);

    await expect(page.getByText(/Informaci[o\u00f3]n General/i).first()).toBeVisible();
    await expect(page.getByText("Detalles de la Cuenta", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible();
    await expect(page.getByText(/Secci[o\u00f3]n Legal/i).first()).toBeVisible();

    await takeCheckpoint(page, screenshotsDir, "04-administrar-negocios", { fullPage: true });
  });

  await runStep(report, "Informaci\u00f3n General", async () => {
    const infoSection = await sectionByHeading(page, /Informaci[o\u00f3]n General/i);
    await expect(infoSection).toBeVisible();

    const emailLocator = infoSection.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();
    await expect(emailLocator).toBeVisible();
    await expect(infoSection.getByText("BUSINESS PLAN", { exact: false })).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const infoText = (await infoSection.innerText()).replace(/\s+/g, " ").trim();
    if (infoText.length < 30) {
      throw new Error("Informacion General appears empty.");
    }
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    const detailsSection = await sectionByHeading(page, /Detalles de la Cuenta/i);
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText("Cuenta creada", { exact: false })).toBeVisible();
    await expect(detailsSection.getByText("Estado activo", { exact: false })).toBeVisible();
    await expect(detailsSection.getByText("Idioma seleccionado", { exact: false })).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    const businessesSection = await sectionByHeading(page, /Tus Negocios/i);
    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(businessesSection.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();

    const businessEntries = businessesSection.locator("li, [role='row'], [class*='business']");
    if ((await businessEntries.count()) < 1) {
      const sectionText = (await businessesSection.innerText()).trim();
      if (!sectionText) {
        throw new Error("No business list content was found in 'Tus Negocios'.");
      }
    }
  });

  await runStep(report, "T\u00e9rminos y Condiciones", async () => {
    const result = await openAndValidateLegalLink({
      page,
      context,
      linkName: /T[e\u00e9]rminos y Condiciones/i,
      headingName: /T[e\u00e9]rminos y Condiciones/i,
      screenshotsDir,
      screenshotName: "05-terminos-y-condiciones"
    });
    evidence.terminosUrl = result.finalUrl;
  });

  await runStep(report, "Pol\u00edtica de Privacidad", async () => {
    const result = await openAndValidateLegalLink({
      page,
      context,
      linkName: /Pol[i\u00ed]tica de Privacidad/i,
      headingName: /Pol[i\u00ed]tica de Privacidad/i,
      screenshotsDir,
      screenshotName: "06-politica-de-privacidad"
    });
    evidence.politicaUrl = result.finalUrl;
  });

  await finalizeAndAssertReport(testInfo, report, evidence);
});

async function runStep(report, stepName, stepAction) {
  try {
    await stepAction();
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = `FAIL - ${normalizeErrorMessage(error)}`;
  }
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function openLoginPageIfNeeded(page) {
  const currentUrl = page.url();
  if (currentUrl && currentUrl !== "about:blank") {
    await waitForUiToSettle(page);
    return;
  }

  const configuredUrl = process.env.SALEADS_LOGIN_URL;
  if (!configuredUrl) {
    throw new Error("Set SALEADS_LOGIN_URL if the browser is not already at the SaleADS login page.");
  }

  await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);
}

async function clickByVisibleText(page, texts) {
  for (const text of texts) {
    const regex = new RegExp(escapeRegex(text), "i");
    const options = [
      page.getByRole("button", { name: regex }).first(),
      page.getByRole("link", { name: regex }).first(),
      page.getByText(regex).first()
    ];

    for (const locator of options) {
      if (await locator.isVisible({ timeout: 1_500 }).catch(() => false)) {
        await locator.click();
        await waitForUiToSettle(page);
        return;
      }
    }
  }

  throw new Error(`Unable to find clickable element by visible text: ${texts.join(", ")}`);
}

async function selectGoogleAccountIfPrompted(sourcePage) {
  const accountOption = sourcePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountOption.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await accountOption.click();
    await sourcePage.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
  }
}

function getSidebarLocator(page) {
  return page.locator("aside, nav[aria-label*='sidebar' i], nav").first();
}

async function takeCheckpoint(page, screenshotsDir, name, options = {}) {
  await page.screenshot({
    path: path.join(screenshotsDir, `${name}.png`),
    fullPage: options.fullPage || false
  });
}

async function sectionByHeading(page, headingRegex) {
  const heading = page.getByRole("heading", { name: headingRegex }).first();
  await expect(heading).toBeVisible();

  const section = page
    .locator("section, article, div")
    .filter({ has: heading })
    .first();

  if (await section.isVisible().catch(() => false)) {
    return section;
  }

  return page.locator("body");
}

async function openAndValidateLegalLink(params) {
  const { page, context, linkName, headingName, screenshotsDir, screenshotName } = params;
  const appUrlBeforeOpen = page.url();

  const link = page.getByRole("link", { name: linkName }).first();
  let clickTarget = link;
  if (!(await link.isVisible({ timeout: 10_000 }).catch(() => false))) {
    const fallbackLink = page.getByText(linkName).first();
    await expect(fallbackLink).toBeVisible();
    clickTarget = fallbackLink;
  }

  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await clickTarget.click();
  const popupPage = await popupPromise;
  if (popupPage) {
    await validateLegalPage(popupPage, headingName, screenshotsDir, screenshotName);
    const finalUrl = popupPage.url();
    await popupPage.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
    return { finalUrl };
  }

  await waitForUiToSettle(page);
  await validateLegalPage(page, headingName, screenshotsDir, screenshotName);
  const finalUrl = page.url();

  if (page.url() !== appUrlBeforeOpen) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    if (page.url() === finalUrl) {
      await page.goto(appUrlBeforeOpen, { waitUntil: "domcontentloaded" }).catch(() => null);
    }
    await waitForUiToSettle(page);
  }

  return { finalUrl };
}

async function validateLegalPage(page, headingName, screenshotsDir, screenshotName) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 });
  await waitForUiToSettle(page);

  await expect(page.getByRole("heading", { name: headingName }).first()).toBeVisible();

  const legalText = (await page.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (legalText.length < 120) {
    throw new Error("Legal content appears too short or missing.");
  }

  await takeCheckpoint(page, screenshotsDir, screenshotName, { fullPage: true });
}

function markRemainingAsBlocked(report, blockingStepName) {
  for (const [step, status] of Object.entries(report)) {
    if (status === "NOT_EXECUTED") {
      report[step] = `FAIL - Blocked because '${blockingStepName}' did not complete successfully.`;
    }
  }
}

async function finalizeAndAssertReport(testInfo, report, evidence) {
  const finalPayload = {
    workflow: "saleads_mi_negocio_full_test",
    report,
    evidence
  };

  await testInfo.attach("final-report", {
    body: JSON.stringify(finalPayload, null, 2),
    contentType: "application/json"
  });

  const failures = Object.entries(report).filter(([, status]) => status !== "PASS");
  if (failures.length > 0) {
    const failureText = failures.map(([step, status]) => `${step}: ${status}`).join("\n");
    throw new Error(`Workflow validation failed:\n${failureText}`);
  }
}

function normalizeErrorMessage(error) {
  if (!error) {
    return "Unknown error";
  }

  const message = String(error.message || error);
  return message.replace(/\s+/g, " ").trim().slice(0, 450);
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
