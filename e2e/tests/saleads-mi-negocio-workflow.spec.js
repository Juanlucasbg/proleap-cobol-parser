const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const CHECKPOINT_DIR = path.resolve(__dirname, "..", "artifacts", "screenshots");
const REPORT_DIR = path.resolve(__dirname, "..", "artifacts");
const REPORT_PATH = path.join(REPORT_DIR, "saleads-mi-negocio-report.json");
const START_URL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;

const STEP_LABELS = {
  login: "Login",
  menu: "Mi Negocio menu",
  agregarModal: "Agregar Negocio modal",
  administrar: "Administrar Negocios view",
  infoGeneral: "Información General",
  detalles: "Detalles de la Cuenta",
  tusNegocios: "Tus Negocios",
  terminos: "Términos y Condiciones",
  privacidad: "Política de Privacidad",
};

function ensureArtifactsDir() {
  fs.mkdirSync(CHECKPOINT_DIR, { recursive: true });
  fs.mkdirSync(REPORT_DIR, { recursive: true });
}

async function clickByVisibleText(page, candidates) {
  for (const text of candidates) {
    const locator = page.getByText(text, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      await page.waitForTimeout(700);
      return text;
    }
  }
  throw new Error(`No visible element found for texts: ${candidates.join(", ")}`);
}

async function waitForAnyVisible(page, candidates) {
  for (const text of candidates) {
    const locator = page.getByText(text, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      return text;
    }
  }
  throw new Error(`Expected one of these texts to be visible: ${candidates.join(", ")}`);
}

async function clickLinkAndCaptureTarget(page, context, linkText, expectedHeadingRegex, screenshotName) {
  let legalPage = null;
  const beforeUrl = page.url();

  const pagePromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  const link = page.getByText(linkText, { exact: false }).first();
  await expect(link).toBeVisible();
  await link.click();
  await page.waitForTimeout(1000);

  legalPage = await pagePromise;
  if (legalPage) {
    await legalPage.waitForLoadState("domcontentloaded");
  } else {
    legalPage = page;
    await legalPage.waitForLoadState("domcontentloaded");
    const afterUrl = legalPage.url();
    if (beforeUrl === afterUrl) {
      // No navigation observed yet; still continue validation by content.
      await legalPage.waitForTimeout(1000);
    }
  }

  await expect(legalPage.getByText(expectedHeadingRegex)).toBeVisible();
  const legalTextLength = await legalPage.evaluate(() => (document.body?.innerText || "").trim().length);
  if (legalTextLength < 120) {
    throw new Error(`Legal content for "${linkText}" seems too short (${legalTextLength} chars).`);
  }
  await legalPage.screenshot({
    path: path.join(CHECKPOINT_DIR, screenshotName),
    fullPage: true,
  });

  const finalUrl = legalPage.url();

  if (legalPage !== page) {
    await legalPage.close();
    await page.bringToFront();
    await page.waitForTimeout(700);
  } else {
    await page.goBack().catch(() => undefined);
    await page.waitForTimeout(700);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  ensureArtifactsDir();

  if (page.url() === "about:blank" && START_URL) {
    await page.goto(START_URL, { waitUntil: "domcontentloaded" });
  }

  const report = {
    metadata: {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      startUrl: page.url(),
    },
    results: {},
    legalUrls: {},
  };

  const markPass = (key, details = "") => {
    report.results[STEP_LABELS[key]] = { status: "PASS", details };
  };

  const markFail = (key, error) => {
    const message = error instanceof Error ? error.message : String(error);
    report.results[STEP_LABELS[key]] = { status: "FAIL", details: message };
  };

  // Step 1: Login with Google
  try {
    await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google",
      "Login",
      "Iniciar sesión",
    ]);
    await page.waitForLoadState("domcontentloaded");

    const googleAccount = page
      .getByText("juanlucasbarbiergarzon@gmail.com", { exact: false })
      .first();
    if (await googleAccount.isVisible().catch(() => false)) {
      await googleAccount.click();
      await page.waitForTimeout(1000);
      await page.waitForLoadState("domcontentloaded");
    }

    await waitForAnyVisible(page, ["Negocio", "Mi Negocio"]);
    await page.screenshot({
      path: path.join(CHECKPOINT_DIR, "01-dashboard-loaded.png"),
      fullPage: true,
    });
    markPass("login", "Main app loaded and left sidebar visible.");
  } catch (error) {
    markFail("login", error);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
    await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: false }).first()).toBeVisible();
    await page.screenshot({
      path: path.join(CHECKPOINT_DIR, "02-mi-negocio-expanded.png"),
      fullPage: true,
    });
    markPass("menu", "Menu expanded and submenu items visible.");
  } catch (error) {
    markFail("menu", error);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await clickByVisibleText(page, ["Agregar Negocio"]);
    await expect(page.getByText("Crear Nuevo Negocio", { exact: false }).first()).toBeVisible();
    await expect(page.getByLabel("Nombre del Negocio", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    const negocioInput = page.getByLabel("Nombre del Negocio", { exact: false }).first();
    await negocioInput.click();
    await negocioInput.fill("Negocio Prueba Automatización");

    await page.screenshot({
      path: path.join(CHECKPOINT_DIR, "03-agregar-negocio-modal.png"),
      fullPage: true,
    });

    await page.getByRole("button", { name: /Cancelar/i }).first().click();
    await page.waitForTimeout(800);
    markPass("agregarModal", "Modal content validated and closed with Cancelar.");
  } catch (error) {
    markFail("agregarModal", error);
  }

  // Step 4: Open Administrar Negocios
  try {
    if (!(await page.getByText("Administrar Negocios", { exact: false }).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
    }

    await clickByVisibleText(page, ["Administrar Negocios"]);
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(1000);

    await expect(page.getByText("Información General", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Detalles de la Cuenta", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Sección Legal", { exact: false }).first()).toBeVisible();
    await page.screenshot({
      path: path.join(CHECKPOINT_DIR, "04-administrar-negocios.png"),
      fullPage: true,
    });
    markPass("administrar", "Administrar Negocios sections visible.");
  } catch (error) {
    markFail("administrar", error);
  }

  // Step 5: Validate Información General
  try {
    await expect(page.getByText("BUSINESS PLAN", { exact: false }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const emailLike = page.locator("text=/[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}/").first();
    await expect(emailLike).toBeVisible();

    // Validate user name as any non-empty heading/text block near profile section.
    const possibleName = page
      .locator("h1, h2, h3, [class*='name'], [data-testid*='name']")
      .filter({ hasNotText: /información general|detalles de la cuenta|tus negocios|sección legal/i })
      .first();
    await expect(possibleName).toBeVisible();
    markPass("infoGeneral", "Name, email, plan text, and Cambiar Plan button validated.");
  } catch (error) {
    markFail("infoGeneral", error);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(page.getByText("Cuenta creada", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Estado activo", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Idioma seleccionado", { exact: false }).first()).toBeVisible();
    markPass("detalles", "Detalles de la Cuenta labels validated.");
  } catch (error) {
    markFail("detalles", error);
  }

  // Step 7: Validate Tus Negocios
  try {
    await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false }).first()).toBeVisible();

    const businessesContainer = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessesContainer).toBeVisible();
    markPass("tusNegocios", "Business area and expected controls/text are visible.");
  } catch (error) {
    markFail("tusNegocios", error);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const terminosUrl = await clickLinkAndCaptureTarget(
      page,
      context,
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      "08-terminos-y-condiciones.png",
    );
    report.legalUrls["Términos y Condiciones"] = terminosUrl;
    markPass("terminos", `Validated legal page. URL: ${terminosUrl}`);
  } catch (error) {
    markFail("terminos", error);
  }

  // Step 9: Validate Política de Privacidad
  try {
    const privacidadUrl = await clickLinkAndCaptureTarget(
      page,
      context,
      "Política de Privacidad",
      /Política de Privacidad/i,
      "09-politica-de-privacidad.png",
    );
    report.legalUrls["Política de Privacidad"] = privacidadUrl;
    markPass("privacidad", `Validated legal page. URL: ${privacidadUrl}`);
  } catch (error) {
    markFail("privacidad", error);
  }

  report.metadata.finalUrl = page.url();
  report.metadata.summary = Object.entries(report.results).reduce(
    (acc, [key, value]) => {
      if (value.status === "PASS") acc.pass += 1;
      else acc.fail += 1;
      return acc;
    },
    { pass: 0, fail: 0 },
  );

  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");

  // Fail test if any required step failed.
  const failedSteps = Object.entries(report.results)
    .filter(([, value]) => value.status === "FAIL")
    .map(([name, value]) => `${name}: ${value.details}`);
  expect(failedSteps, `Some workflow steps failed:\n${failedSteps.join("\n")}`).toEqual([]);
});
