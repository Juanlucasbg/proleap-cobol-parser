const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || "";
const OUTPUT_DIR = path.join(process.cwd(), "artifacts", "saleads_mi_negocio");
const REPORT_JSON_PATH = path.join(OUTPUT_DIR, "final-report.json");
const REPORT_MD_PATH = path.join(OUTPUT_DIR, "final-report.md");

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

function defaultResults() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

async function waitForUi(page) {
  try {
    await page.waitForLoadState("networkidle", { timeout: 7000 });
  } catch {
    await page.waitForTimeout(900);
  }
}

async function firstVisibleLocator(locators) {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }

  return null;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function selectGoogleAccountIfPrompted(pageOrPopup) {
  const accountOption = pageOrPopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
  if (await accountOption.isVisible({ timeout: 15000 }).catch(() => false)) {
    await accountOption.click();
    await waitForUi(pageOrPopup);
  }
}

async function openLegalLinkAndValidate({
  appPage,
  linkText,
  headingRegex,
  screenshotName,
  legalUrls
}) {
  const linkLocator = appPage.getByRole("link", { name: new RegExp(linkText, "i") }).first();
  if (!(await linkLocator.isVisible().catch(() => false))) {
    throw new Error(`No se encontró el enlace legal "${linkText}".`);
  }

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 12000 }).catch(() => null);
  await clickAndWait(linkLocator, appPage);

  const popup = await popupPromise;
  const legalPage = popup || appPage;
  await legalPage.waitForLoadState("domcontentloaded");
  await waitForUi(legalPage);

  await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible();
  const legalText = (await legalPage.locator("main, body").first().innerText()).replace(/\s+/g, " ").trim();
  if (legalText.length < 120) {
    throw new Error(`No se detectó contenido legal suficiente en ${linkText}.`);
  }

  await legalPage.screenshot({
    path: path.join(OUTPUT_DIR, screenshotName),
    fullPage: true
  });
  legalUrls[linkText] = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUi(appPage);
}

async function writeFinalReport({ results, failures, legalUrls }) {
  const report = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      saleadsLoginUrl: LOGIN_URL || "CURRENT_PAGE",
      googleAccount: GOOGLE_ACCOUNT_EMAIL
    },
    results,
    legalUrls,
    failures
  };

  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  fs.writeFileSync(REPORT_JSON_PATH, JSON.stringify(report, null, 2), "utf8");

  const tableRows = REPORT_FIELDS.map((field) => `| ${field} | ${results[field]} |`).join("\n");
  const legalRows = Object.entries(legalUrls)
    .map(([key, value]) => `- **${key}**: ${value}`)
    .join("\n");
  const failureRows = failures.length ? failures.map((item) => `- ${item}`).join("\n") : "- None";
  const markdown = `# SaleADS Mi Negocio Full Test Report

## Status by requested field
| Field | Result |
| --- | --- |
${tableRows}

## Legal URLs
${legalRows || "- No URLs captured"}

## Failures
${failureRows}
`;

  fs.writeFileSync(REPORT_MD_PATH, markdown, "utf8");
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });

  const results = defaultResults();
  const failures = [];
  const legalUrls = {};

  async function runStep(reportField, stepFn) {
    try {
      await stepFn();
      results[reportField] = "PASS";
    } catch (error) {
      results[reportField] = "FAIL";
      failures.push(`${reportField}: ${error.message}`);
    }
  }

  await runStep("Login", async () => {
    if (LOGIN_URL) {
      await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const loginButton = await firstVisibleLocator([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/(iniciar|sign)\s*(sesión|in)\s*(con|with)\s*google/i),
      page.getByText(/google/i)
    ]);

    if (!loginButton) {
      throw new Error("No se encontró el botón de login con Google.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 12000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfPrompted(popup);
      await popup.waitForEvent("close", { timeout: 60000 }).catch(() => {});
      await page.bringToFront();
    } else {
      await selectGoogleAccountIfPrompted(page);
    }

    await waitForUi(page);
    const sidebar = await firstVisibleLocator([
      page.locator("aside"),
      page.locator("nav"),
      page.getByText(/Negocio|Dashboard|Panel/i)
    ]);

    if (!sidebar) {
      throw new Error("No se detectó la interfaz principal o la barra lateral después del login.");
    }

    await page.screenshot({
      path: path.join(OUTPUT_DIR, "01-dashboard-loaded.png"),
      fullPage: true
    });
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisibleLocator([
      page.getByRole("button", { name: /Negocio/i }),
      page.getByRole("link", { name: /Negocio/i }),
      page.getByText(/^Negocio$/i)
    ]);
    if (!negocioSection) {
      throw new Error("No se encontró la sección Negocio en el sidebar.");
    }
    await clickAndWait(negocioSection, page);

    const miNegocioMenu = await firstVisibleLocator([
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i)
    ]);
    if (!miNegocioMenu) {
      throw new Error("No se encontró la opción Mi Negocio.");
    }
    await clickAndWait(miNegocioMenu, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await page.screenshot({
      path: path.join(OUTPUT_DIR, "02-mi-negocio-menu-expanded.png"),
      fullPage: true
    });
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusinessMenuOption = page.getByText(/Agregar Negocio/i).first();
    await clickAndWait(addBusinessMenuOption, page);

    const modal = page.getByRole("dialog").first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(modal.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(modal.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    await modal.getByLabel(/Nombre del Negocio/i).first().fill("Negocio Prueba Automatización");
    await modal.screenshot({
      path: path.join(OUTPUT_DIR, "03-agregar-negocio-modal.png")
    });
    await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }).first(), page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarMenu = page.getByText(/Administrar Negocios/i).first();
    if (!(await administrarMenu.isVisible().catch(() => false))) {
      const miNegocioMenu = page.getByText(/Mi Negocio/i).first();
      await clickAndWait(miNegocioMenu, page);
    }

    await clickAndWait(page.getByText(/Administrar Negocios/i).first(), page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await page.screenshot({
      path: path.join(OUTPUT_DIR, "04-administrar-negocios-view.png"),
      fullPage: true
    });
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const userEmailVisible = await page.getByText(/@/).first().isVisible().catch(() => false);
    if (!userEmailVisible) {
      throw new Error("No se encontró un correo de usuario visible.");
    }

    const userNameVisible = await firstVisibleLocator([
      page.locator("h1, h2, h3").filter({ hasNotText: /Información General|Detalles de la Cuenta|Tus Negocios/i }),
      page.locator("[data-testid*=name], [class*=name]")
    ]);
    if (!userNameVisible) {
      throw new Error("No se detectó el nombre de usuario visible.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

    const visibleBusinessItems = await page.locator("li, tr, [class*=business], [data-testid*=business]").count();
    if (visibleBusinessItems < 1) {
      throw new Error("No se detectó una lista visible de negocios.");
    }
  });

  await runStep("Términos y Condiciones", async () => {
    await openLegalLinkAndValidate({
      appPage: page,
      linkText: "Términos y Condiciones",
      headingRegex: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      legalUrls
    });
  });

  await runStep("Política de Privacidad", async () => {
    await openLegalLinkAndValidate({
      appPage: page,
      linkText: "Política de Privacidad",
      headingRegex: /Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      legalUrls
    });
  });

  await writeFinalReport({ results, failures, legalUrls });

  expect(failures, `Validaciones fallidas:\n${failures.join("\n")}`).toEqual([]);
});
