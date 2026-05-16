const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_CANDIDATE_TEXTS = [
  "Sign in with Google",
  "Iniciar sesion con Google",
  "Iniciar sesion",
  "Continuar con Google",
  "Login with Google",
];

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

test.describe.configure({ mode: "serial" });

test("SaleADS Mi Negocio full workflow", async ({ page, context }, testInfo) => {
  const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const legalUrls = {};
  const errors = [];

  const screenshotRoot = path.resolve(
    process.cwd(),
    process.env.SALEADS_SCREENSHOT_DIR || "artifacts/screenshots",
  );
  await fs.mkdir(screenshotRoot, { recursive: true });

  const runStep = async (fieldName, fn) => {
    try {
      await fn();
      results[fieldName] = "PASS";
    } catch (error) {
      errors.push(`${fieldName}: ${error.message}`);
      results[fieldName] = "FAIL";
    }
  };

  await ensureOnLoginPage(page);

  await runStep("Login", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await clickByVisibleText(page, LOGIN_CANDIDATE_TEXTS);
    const popup = await popupPromise;

    if (popup) {
      await waitForUiLoad(popup);
      await chooseGoogleAccountIfShown(popup);
      await popup.waitForEvent("close", { timeout: 25000 }).catch(() => undefined);
    } else {
      await chooseGoogleAccountIfShown(page);
    }

    await waitForUiLoad(page);
    await expect(page.getByText(/negocio/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, screenshotRoot, "01-dashboard-loaded", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await expect(page.getByText(/negocio/i).first()).toBeVisible();
    await clickByVisibleText(page, ["Mi Negocio"]);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, screenshotRoot, "02-mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, ["Agregar Negocio"]);
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(await findBusinessNameInput(page)).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, screenshotRoot, "03-agregar-negocio-modal");

    const businessNameInput = await findBusinessNameInput(page);
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickByVisibleText(page, ["Cancelar"]);
  });

  await runStep("Administrar Negocios view", async () => {
    if (!(await isVisible(page.getByText(/Administrar Negocios/i).first()))) {
      await clickByVisibleText(page, ["Mi Negocio"]);
    }

    await clickByVisibleText(page, ["Administrar Negocios"]);
    await expect(page.getByText(/Informacion General|Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Seccion Legal|Secci[oó]n Legal/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, screenshotRoot, "04-administrar-negocios-view", true);
  });

  await runStep("Informacion General", async () => {
    await expect(page.getByText(/Informacion General|Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const section = page
      .locator("section, div")
      .filter({ has: page.getByText(/Tus Negocios/i).first() })
      .first();
    await expect(section).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
  });

  await runStep("Terminos y Condiciones", async () => {
    const url = await openLegalLinkAndReturn({
      page,
      context,
      testInfo,
      screenshotRoot,
      linkTexts: ["Terminos y Condiciones", "Términos y Condiciones"],
      expectedHeading: /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
      screenshotName: "08-terminos-y-condiciones",
    });
    legalUrls["Terminos y Condiciones"] = url;
  });

  await runStep("Politica de Privacidad", async () => {
    const url = await openLegalLinkAndReturn({
      page,
      context,
      testInfo,
      screenshotRoot,
      linkTexts: ["Politica de Privacidad", "Política de Privacidad"],
      expectedHeading: /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
      screenshotName: "09-politica-de-privacidad",
    });
    legalUrls["Politica de Privacidad"] = url;
  });

  const report = {
    results,
    legalUrls,
    errors,
  };

  const reportBody = JSON.stringify(report, null, 2);
  await testInfo.attach("final-report.json", {
    body: Buffer.from(reportBody, "utf8"),
    contentType: "application/json",
  });
  console.log(`\nSaleADS Mi Negocio workflow report:\n${reportBody}\n`);

  expect(errors, "One or more validations failed.").toEqual([]);
});

async function ensureOnLoginPage(page) {
  const targetUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    process.env.PLAYWRIGHT_TEST_BASE_URL;

  if (targetUrl) {
    await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
  }

  if (!targetUrl && page.url() === "about:blank") {
    throw new Error(
      "No URL provided. Set SALEADS_LOGIN_URL (or SALEADS_URL) to run this workflow on the current environment.",
    );
  }

  await waitForUiLoad(page);
}

async function chooseGoogleAccountIfShown(pageOrPopup) {
  const account = pageOrPopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
  const visible = await account.waitFor({ state: "visible", timeout: 8000 }).then(
    () => true,
    () => false,
  );

  if (visible) {
    await account.click();
  }
}

async function clickByVisibleText(page, texts) {
  for (const text of texts) {
    const exactRegex = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
    const containsRegex = new RegExp(escapeRegex(text), "i");
    const candidates = [
      page.getByRole("button", { name: exactRegex }).first(),
      page.getByRole("link", { name: exactRegex }).first(),
      page.getByRole("menuitem", { name: exactRegex }).first(),
      page.getByText(containsRegex).first(),
    ];

    for (const locator of candidates) {
      if (await isVisible(locator)) {
        await locator.click();
        await waitForUiLoad(page);
        return;
      }
    }
  }

  throw new Error(`Could not find clickable element for texts: ${texts.join(", ")}`);
}

async function findBusinessNameInput(page) {
  const byLabel = page.getByLabel(/Nombre del Negocio/i).first();
  if (await isVisible(byLabel)) {
    return byLabel;
  }

  const byPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i).first();
  if (await isVisible(byPlaceholder)) {
    return byPlaceholder;
  }

  throw new Error("Could not locate input field 'Nombre del Negocio'.");
}

async function openLegalLinkAndReturn({
  page,
  context,
  testInfo,
  screenshotRoot,
  linkTexts,
  expectedHeading,
  screenshotName,
}) {
  const newPagePromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickByVisibleText(page, linkTexts);

  const maybeNewPage = await newPagePromise;
  const targetPage = maybeNewPage || page;
  await waitForUiLoad(targetPage);

  await expect(targetPage.getByRole("heading", { name: expectedHeading }).first()).toBeVisible();
  await expect(targetPage.locator("body")).toContainText(
    /terminos|t[eé]rminos|privacidad|condiciones|datos|informacion/i,
  );
  await captureCheckpoint(targetPage, testInfo, screenshotRoot, screenshotName, true);

  const finalUrl = targetPage.url();

  if (maybeNewPage) {
    await maybeNewPage.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiLoad(page);
  }

  return finalUrl;
}

async function captureCheckpoint(page, testInfo, screenshotRoot, checkpointName, fullPage = false) {
  const fileName = `${Date.now()}-${toSlug(checkpointName)}.png`;
  const filePath = path.join(screenshotRoot, fileName);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(`checkpoint-${checkpointName}`, {
    path: filePath,
    contentType: "image/png",
  });
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
  await page.waitForTimeout(400);
}

function toSlug(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function isVisible(locator) {
  return locator.isVisible().catch(() => false);
}
