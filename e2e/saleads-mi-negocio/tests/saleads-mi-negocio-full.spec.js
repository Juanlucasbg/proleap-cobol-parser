const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function createReportTemplate() {
  return {
    Login: { status: "FAIL", details: "Not executed" },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed" },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed" },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed" },
    "Información General": { status: "FAIL", details: "Not executed" },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed" },
    "Tus Negocios": { status: "FAIL", details: "Not executed" },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed", finalUrl: "" },
    "Política de Privacidad": { status: "FAIL", details: "Not executed", finalUrl: "" }
  };
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUiLoad(page);
}

async function locatorVisible(locator, timeout = 4000) {
  return locator
    .waitFor({ state: "visible", timeout })
    .then(() => true)
    .catch(() => false);
}

async function firstVisible(candidates, timeout = 4000) {
  for (const candidate of candidates) {
    if (await locatorVisible(candidate, timeout)) {
      return candidate;
    }
  }
  throw new Error("Unable to find a visible element from the provided candidates.");
}

function sectionByTitle(page, titleRegex) {
  return page.locator("section, div, article").filter({ has: page.getByText(titleRegex) }).first();
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const baseUrl = process.env.SALEADS_BASE_URL || process.env.SALEADS_URL || process.env.BASE_URL;
  const report = createReportTemplate();
  const errors = [];
  const screenshotsDir = path.join(testInfo.outputDir, "checkpoints");
  await fs.mkdir(screenshotsDir, { recursive: true });

  const saveCheckpoint = async (name, targetPage = page, fullPage = false) => {
    const filePath = path.join(screenshotsDir, `${name}.png`);
    await targetPage.screenshot({ path: filePath, fullPage });
    await testInfo.attach(name, { path: filePath, contentType: "image/png" });
  };

  const runStep = async (name, handler) => {
    try {
      await handler();
      report[name] = { ...report[name], status: "PASS", details: "" };
    } catch (error) {
      report[name] = {
        ...report[name],
        status: "FAIL",
        details: error instanceof Error ? error.message : String(error)
      };
      errors.push(`${name}: ${report[name].details}`);
    }
  };

  const requireSuccessfulStep = (name) => {
    if (report[name].status !== "PASS") {
      throw new Error(`Skipped: prerequisite step "${name}" did not pass.`);
    }
  };

  await runStep("Login", async () => {
    if (!baseUrl) {
      throw new Error("Set SALEADS_BASE_URL (or SALEADS_URL / BASE_URL) to run against the active environment.");
    }

    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);

    const loginButton = await firstVisible(
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }).first(),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google/i).first()
      ],
      12000
    );

    const maybeGooglePopup = context.waitForEvent("page", { timeout: 9000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const googlePage = await maybeGooglePopup;

    if (googlePage) {
      await waitForUiLoad(googlePage);
      const accountOption = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
      if (await locatorVisible(accountOption, 7000)) {
        await clickAndWait(accountOption, googlePage);
      }
    } else {
      const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
      if (await locatorVisible(accountOption, 7000)) {
        await clickAndWait(accountOption, page);
      }
    }

    await waitForUiLoad(page);
    const mainInterface = await firstVisible(
      [
        page.getByRole("main").first(),
        page.locator("main").first(),
        page.locator("div").filter({ hasText: /dashboard|inicio|panel/i }).first()
      ],
      15000
    );
    await expect(mainInterface).toBeVisible();

    const leftSidebar = await firstVisible(
      [page.locator("aside").first(), page.locator("nav").first(), page.getByRole("navigation").first()],
      15000
    );
    await expect(leftSidebar).toBeVisible();
    await saveCheckpoint("step-1-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    requireSuccessfulStep("Login");

    const negocioItem = await firstVisible(
      [
        page.getByRole("button", { name: /^Negocio$/i }).first(),
        page.getByRole("link", { name: /^Negocio$/i }).first(),
        page.getByText(/^Negocio$/i).first()
      ],
      12000
    );
    await clickAndWait(negocioItem, page);

    const miNegocioItem = await firstVisible(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }).first(),
        page.getByRole("link", { name: /^Mi Negocio$/i }).first(),
        page.getByText(/^Mi Negocio$/i).first()
      ],
      12000
    );
    await clickAndWait(miNegocioItem, page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await saveCheckpoint("step-2-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    requireSuccessfulStep("Mi Negocio menu");

    const addBusinessOption = await firstVisible(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }).first(),
        page.getByRole("link", { name: /^Agregar Negocio$/i }).first(),
        page.getByText(/^Agregar Negocio$/i).first()
      ],
      12000
    );
    await clickAndWait(addBusinessOption, page);

    const modalTitle = page.getByText(/^Crear Nuevo Negocio$/i).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();
    await saveCheckpoint("step-3-agregar-negocio-modal");

    const nameInput = page.getByLabel(/Nombre del Negocio/i).first();
    await nameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }).first(), page);
  });

  await runStep("Administrar Negocios view", async () => {
    requireSuccessfulStep("Mi Negocio menu");

    const miNegocioItem = await firstVisible(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }).first(),
        page.getByRole("link", { name: /^Mi Negocio$/i }).first(),
        page.getByText(/^Mi Negocio$/i).first()
      ],
      10000
    );
    if (!(await locatorVisible(page.getByText(/^Administrar Negocios$/i).first(), 1500))) {
      await clickAndWait(miNegocioItem, page);
    }

    const manageBusinessesItem = await firstVisible(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }).first(),
        page.getByRole("link", { name: /^Administrar Negocios$/i }).first(),
        page.getByText(/^Administrar Negocios$/i).first()
      ],
      12000
    );
    await clickAndWait(manageBusinessesItem, page);

    await expect(page.getByText(/^Información General$/i).first()).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
    await saveCheckpoint("step-4-administrar-negocios-page", page, true);
  });

  await runStep("Información General", async () => {
    requireSuccessfulStep("Administrar Negocios view");

    const infoSection = sectionByTitle(page, /^Información General$/i);
    await expect(infoSection).toBeVisible();

    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    await expect(infoSection.getByText(/@/).first()).toBeVisible();
    await expect(infoSection.getByText(/Nombre|Usuario|User|Cuenta/i).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    requireSuccessfulStep("Administrar Negocios view");

    const detailsSection = sectionByTitle(page, /^Detalles de la Cuenta$/i);
    await expect(detailsSection).toBeVisible();

    await expect(detailsSection.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo|Activo/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado|Idioma/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    requireSuccessfulStep("Administrar Negocios view");

    const businessesSection = sectionByTitle(page, /^Tus Negocios$/i);
    await expect(businessesSection).toBeVisible();

    await expect(businessesSection.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();
    await expect(
      businessesSection.getByRole("button", { name: /^Agregar Negocio$/i }).first().or(
        businessesSection.getByRole("link", { name: /^Agregar Negocio$/i }).first()
      )
    ).toBeVisible();
    await expect(businessesSection.locator("li, table, [role='row'], [data-testid*='business']").first()).toBeVisible();
  });

  const validateLegalLink = async (stepName, linkRegex, headingRegex, screenshotName) => {
    const link = await firstVisible(
      [page.getByRole("link", { name: linkRegex }).first(), page.getByText(linkRegex).first()],
      12000
    );

    const maybeNewTab = context.waitForEvent("page", { timeout: 9000 }).catch(() => null);
    await clickAndWait(link, page);
    let targetPage = await maybeNewTab;
    let openedNewTab = true;

    if (!targetPage) {
      openedNewTab = false;
      targetPage = page;
    }

    await waitForUiLoad(targetPage);
    await expect(targetPage.getByText(headingRegex).first()).toBeVisible();

    const legalText = targetPage.locator("main, article, body");
    await expect(legalText).toContainText(/[A-Za-zÁÉÍÓÚáéíóúñÑ]{20,}/);
    await saveCheckpoint(screenshotName, targetPage, true);
    report[stepName].finalUrl = targetPage.url();

    if (openedNewTab) {
      await targetPage.close();
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    }
  };

  await runStep("Términos y Condiciones", async () => {
    requireSuccessfulStep("Administrar Negocios view");

    await validateLegalLink(
      "Términos y Condiciones",
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "step-8-terminos-y-condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    requireSuccessfulStep("Administrar Negocios view");

    await validateLegalLink(
      "Política de Privacidad",
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "step-9-politica-de-privacidad"
    );
  });

  const reportPath = path.join(testInfo.outputDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf-8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });
  console.log("Final workflow report:", JSON.stringify(report, null, 2));

  expect(errors, `Workflow validation failures:\n${errors.join("\n")}`).toEqual([]);
});
