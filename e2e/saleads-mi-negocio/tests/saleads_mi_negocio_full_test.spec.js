const fs = require("fs/promises");
const { expect, test } = require("@playwright/test");

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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function newReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        status: "FAIL",
        details: "Not executed",
      },
    ]),
  );
}

async function safeIsVisible(locator, timeout = 2000) {
  try {
    await locator.waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickAndWait(page, locator) {
  await locator.click({ timeout: 15000 });
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function checkpoint(page, testInfo, name, fullPage = true) {
  const fileName = `${name.replace(/\s+/g, "_").toLowerCase()}.png`;
  const outputPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: outputPath, fullPage });
  await testInfo.attach(name, { path: outputPath, contentType: "image/png" });
}

async function findVisibleByRegex(page, regex) {
  const candidates = [
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByRole("menuitem", { name: regex }).first(),
    page.getByRole("tab", { name: regex }).first(),
    page.getByText(regex).first(),
  ];

  for (const candidate of candidates) {
    if (await safeIsVisible(candidate)) {
      return candidate;
    }
  }

  throw new Error(`No visible element found for regex: ${regex}`);
}

async function findVisibleByText(page, text) {
  const regex = new RegExp(escapeRegExp(text), "i");
  return findVisibleByRegex(page, regex);
}

function setPass(report, field, details = "Validated successfully") {
  report[field] = { status: "PASS", details };
}

function setFail(report, field, error) {
  report[field] = {
    status: "FAIL",
    details: error instanceof Error ? error.message : String(error),
  };
}

function getFailedFields(report) {
  return Object.entries(report)
    .filter(([, status]) => status.status === "FAIL")
    .map(([field]) => field);
}

async function runStep(report, field, callback) {
  try {
    await callback();
    setPass(report, field);
    return true;
  } catch (error) {
    setFail(report, field, error);
    return false;
  }
}

async function ensureInitialLoginPage(page) {
  if (page.url() !== "about:blank") {
    await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    return;
  }

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (!loginUrl) {
    throw new Error(
      "Browser started on about:blank. Provide SALEADS_LOGIN_URL or pre-open the SaleADS login page.",
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function ensureMiNegocioExpanded(page) {
  const agregarNegocio = page.getByText(/Agregar Negocio/i).first();
  if (await safeIsVisible(agregarNegocio)) {
    return;
  }

  const negocio = await findVisibleByText(page, "Negocio");
  await clickAndWait(page, negocio);

  const miNegocio = await findVisibleByText(page, "Mi Negocio");
  await clickAndWait(page, miNegocio);

  await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 10000 });
}

async function validateLegalLink({
  appPage,
  context,
  testInfo,
  linkText,
  headingRegex,
  screenshotName,
}) {
  const legalLink = await findVisibleByText(appPage, linkText);
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await clickAndWait(appPage, legalLink);

  const popupPage = await popupPromise;
  const legalPage = popupPage || appPage;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 });
  await legalPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});

  const legalHeading = legalPage.getByRole("heading", { name: headingRegex }).first();
  if (!(await safeIsVisible(legalHeading, 5000))) {
    await expect(legalPage.getByText(headingRegex).first()).toBeVisible({ timeout: 12000 });
  }

  const legalContent = (await legalPage.locator("body").innerText()).trim();
  if (legalContent.length < 120) {
    throw new Error(`Legal content for "${linkText}" looks too short.`);
  }

  await checkpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await appPage.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await appPage.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
  const report = newReport();
  const legalUrls = {};

  try {
    const loginOk = await runStep(report, "Login", async () => {
      await ensureInitialLoginPage(page);

      const loginButton = await findVisibleByRegex(
        page,
        /(Sign in with Google|Continuar con Google|Iniciar sesi[oó]n con Google|Google)/i,
      );
      await clickAndWait(page, loginButton);

      const googleAccount = page
        .getByText(/juanlucasbarbiergarzon@gmail\.com/i, { exact: false })
        .first();
      if (await safeIsVisible(googleAccount, 7000)) {
        await clickAndWait(page, googleAccount);
      }

      const sidebar = page.locator("aside, nav").first();
      await expect(sidebar).toBeVisible({ timeout: 30000 });
      await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 20000 });
      await checkpoint(page, testInfo, "01_dashboard_loaded", true);
    });

    if (!loginOk) {
      throw new Error("Cannot continue workflow because login validations failed.");
    }

    const menuOk = await runStep(report, "Mi Negocio menu", async () => {
      const negocioOption = await findVisibleByText(page, "Negocio");
      await clickAndWait(page, negocioOption);

      const miNegocioOption = await findVisibleByText(page, "Mi Negocio");
      await clickAndWait(page, miNegocioOption);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15000 });
      await checkpoint(page, testInfo, "02_mi_negocio_expanded_menu", false);
    });

    if (!menuOk) {
      throw new Error("Cannot continue workflow because Mi Negocio menu validations failed.");
    }

    await runStep(report, "Agregar Negocio modal", async () => {
      await ensureMiNegocioExpanded(page);

      const agregarNegocio = await findVisibleByText(page, "Agregar Negocio");
      await clickAndWait(page, agregarNegocio);

      const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
      await expect(modal).toBeVisible({ timeout: 15000 });
      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible();
      await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
      await checkpoint(page, testInfo, "03_agregar_negocio_modal", false);

      const nameField = modal.getByLabel(/Nombre del Negocio/i);
      await nameField.click();
      await nameField.fill("Negocio Prueba Automatización");
      await clickAndWait(page, modal.getByRole("button", { name: /Cancelar/i }));
      await expect(modal).toBeHidden({ timeout: 10000 });
    });

    const adminOk = await runStep(report, "Administrar Negocios view", async () => {
      await ensureMiNegocioExpanded(page);
      const administrarNegocios = await findVisibleByText(page, "Administrar Negocios");
      await clickAndWait(page, administrarNegocios);

      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 20000 });
      await checkpoint(page, testInfo, "04_administrar_negocios_view", true);
    });

    if (!adminOk) {
      throw new Error("Cannot continue workflow because Administrar Negocios view failed.");
    }

    await runStep(report, "Información General", async () => {
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({
        timeout: 10000,
      });

      const accountEmail = page
        .getByText(/juanlucasbarbiergarzon@gmail\.com|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
        .first();
      await expect(accountEmail).toBeVisible({ timeout: 10000 });

      const bodyText = await page.locator("body").innerText();
      const hasPossibleName =
        /\bjuan\b/i.test(bodyText) || /\busuario\b/i.test(bodyText) || /\bnombre\b/i.test(bodyText);
      if (!hasPossibleName) {
        throw new Error("Could not confirm visible user name text in Información General section.");
      }
    });

    await runStep(report, "Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 10000 });
    });

    await runStep(report, "Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({
        timeout: 10000,
      });
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 10000 });

      const negocioRows = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
      await expect(negocioRows).toBeVisible();
    });

    await runStep(report, "Términos y Condiciones", async () => {
      legalUrls["Términos y Condiciones"] = await validateLegalLink({
        appPage: page,
        context,
        testInfo,
        linkText: "Términos y Condiciones",
        headingRegex: /T[eé]rminos y Condiciones/i,
        screenshotName: "05_terminos_y_condiciones",
      });
    });

    await runStep(report, "Política de Privacidad", async () => {
      legalUrls["Política de Privacidad"] = await validateLegalLink({
        appPage: page,
        context,
        testInfo,
        linkText: "Política de Privacidad",
        headingRegex: /Pol[ií]tica de Privacidad/i,
        screenshotName: "06_politica_de_privacidad",
      });
    });
  } finally {
    const reportPayload = {
      name: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      startUrl: page.url(),
      legalUrls,
      results: report,
    };

    const reportPath = testInfo.outputPath("saleads_mi_negocio_full_test-report.json");
    await fs.writeFile(reportPath, JSON.stringify(reportPayload, null, 2), "utf8");
    await testInfo.attach("saleads_mi_negocio_full_test-report", {
      path: reportPath,
      contentType: "application/json",
    });
  }

  const failedFields = getFailedFields(report);
  expect(
    failedFields,
    `These validations failed: ${failedFields.join(", ") || "none"}`,
  ).toEqual([]);
});
