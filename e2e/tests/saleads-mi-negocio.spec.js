const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const snapshotsDir = path.resolve(__dirname, "..", "artifacts", "screenshots");

function sanitizeFileName(text) {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function isTruthyString(value) {
  return typeof value === "string" && value.trim().length > 0;
}

async function ensureLoaded(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

async function screenshot(page, name, fullPage = false) {
  fs.mkdirSync(snapshotsDir, { recursive: true });
  const filePath = path.join(snapshotsDir, `${sanitizeFileName(name)}.png`);
  await page.screenshot({ path: filePath, fullPage });
}

async function maybeNavigateToConfiguredUrl(page) {
  const targetUrl = process.env.SALEADS_URL;
  if (isTruthyString(targetUrl)) {
    await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
    await ensureLoaded(page);
  }
}

async function tryClickByVisibleText(page, text) {
  const exactRegex = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
  const partialRegex = new RegExp(escapeRegex(text), "i");
  const locators = [
    page.getByRole("button", { name: exactRegex }),
    page.getByRole("link", { name: exactRegex }),
    page.getByText(exactRegex),
    page.getByRole("button", { name: partialRegex }),
    page.getByRole("link", { name: partialRegex }),
    page.getByText(partialRegex),
  ];

  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      await locator.first().click();
      await ensureLoaded(page);
      return true;
    }
  }
  return false;
}

async function clickByVisibleText(page, text) {
  const clicked = await tryClickByVisibleText(page, text);
  if (!clicked) {
    throw new Error(`Unable to find clickable element with visible text: ${text}`);
  }
}

async function clickFirstAvailableText(page, textOptions) {
  for (const option of textOptions) {
    if (await tryClickByVisibleText(page, option)) {
      return option;
    }
  }
  throw new Error(`Unable to click any expected text: ${textOptions.join(", ")}`);
}

async function assertAnyVisible(page, texts) {
  for (const text of texts) {
    const locator = page.getByText(new RegExp(escapeRegex(text), "i"));
    if (await locator.first().isVisible().catch(() => false)) {
      return;
    }
  }
  throw new Error(`None of the expected texts are visible: ${texts.join(", ")}`);
}

async function openLegalLinkAndValidate(page, context, linkText, headingRegex, contentKeywords, screenshotName) {
  const popupPromise = context.waitForEvent("page", { timeout: 15000 }).catch(() => null);
  await clickByVisibleText(page, linkText);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await expect(popup.getByText(headingRegex)).toBeVisible();
    await assertAnyVisible(popup, contentKeywords);
    await screenshot(popup, screenshotName);
    const legalUrl = popup.url();
    await popup.close();
    await page.bringToFront();
    await ensureLoaded(page);
    return legalUrl;
  }

  await ensureLoaded(page);
  await expect(page.getByText(headingRegex)).toBeVisible();
  await assertAnyVisible(page, contentKeywords);
  await screenshot(page, screenshotName);
  const legalUrl = page.url();
  await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await ensureLoaded(page);
  return legalUrl;
}

function createStepReport() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createStepReport();
  const stepErrors = {};
  const legalUrls = {
    terminosYCondiciones: "",
    politicaDePrivacidad: "",
  };

  const runStep = async (stepName, stepFn) => {
    try {
      await stepFn();
      report[stepName] = "PASS";
    } catch (error) {
      report[stepName] = "FAIL";
      stepErrors[stepName] = String(error);
      testInfo.annotations.push({ type: `step-error-${stepName}`, description: String(error) });
    }
  };

  const requirePassedStep = (stepName) => {
    if (report[stepName] !== "PASS") {
      throw new Error(`Skipped because prerequisite step failed: ${stepName}`);
    }
  };

  await maybeNavigateToConfiguredUrl(page);

  await runStep("Login", async () => {
    await assertAnyVisible(page, [
      "Google",
      "Sign in with Google",
      "Iniciar con Google",
      "Continuar con Google",
    ]);

    const googlePopupPromise = context.waitForEvent("page", { timeout: 20000 }).catch(() => null);
    await clickFirstAvailableText(page, [
      "Sign in with Google",
      "Iniciar con Google",
      "Continuar con Google",
      "Google",
    ]);

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      const account = googlePopup.getByText(/juanlucasbarbiergarzon@gmail\.com/i);
      if (await account.first().isVisible().catch(() => false)) {
        await account.first().click();
        await googlePopup.waitForTimeout(1000);
      }
      await page.bringToFront();
      await ensureLoaded(page);
    }

    await assertAnyVisible(page, ["Negocio", "Dashboard", "Inicio"]);
    await assertAnyVisible(page, ["Negocio", "Mi Negocio"]);
    await screenshot(page, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    requirePassedStep("Login");
    await clickByVisibleText(page, "Negocio");
    await clickByVisibleText(page, "Mi Negocio");
    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();
    await screenshot(page, "02-mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    requirePassedStep("Mi Negocio menu");
    await clickByVisibleText(page, "Agregar Negocio");
    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    const inputByLabel = page.getByLabel(/Nombre del Negocio/i);
    const inputByPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i);
    if (await inputByLabel.first().isVisible().catch(() => false)) {
      await inputByLabel.fill("Negocio Prueba Automatización");
    } else if (await inputByPlaceholder.first().isVisible().catch(() => false)) {
      await inputByPlaceholder.fill("Negocio Prueba Automatización");
    } else {
      throw new Error("Input field 'Nombre del Negocio' was not found.");
    }

    await screenshot(page, "03-agregar-negocio-modal");
    await clickByVisibleText(page, "Cancelar");
  });

  await runStep("Administrar Negocios view", async () => {
    requirePassedStep("Mi Negocio menu");
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, "Mi Negocio");
    }
    await clickByVisibleText(page, "Administrar Negocios");
    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();
    await screenshot(page, "04-administrar-negocios-cuenta", true);
  });

  await runStep("Información General", async () => {
    requirePassedStep("Administrar Negocios view");
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    await expect(
      page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first(),
    ).toBeVisible();
    await assertAnyVisible(page, ["Nombre", "Usuario", "Perfil", "Cuenta"]);
  });

  await runStep("Detalles de la Cuenta", async () => {
    requirePassedStep("Administrar Negocios view");
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    requirePassedStep("Administrar Negocios view");
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    requirePassedStep("Administrar Negocios view");
    legalUrls.terminosYCondiciones = await openLegalLinkAndValidate(
      page,
      context,
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      ["Política", "responsabilidad", "usuario", "servicio"],
      "08-terminos-y-condiciones",
    );
  });

  await runStep("Política de Privacidad", async () => {
    requirePassedStep("Administrar Negocios view");
    legalUrls.politicaDePrivacidad = await openLegalLinkAndValidate(
      page,
      context,
      "Política de Privacidad",
      /Política de Privacidad/i,
      ["datos", "privacidad", "información", "usuario"],
      "09-politica-de-privacidad",
    );
  });

  const reportPath = path.resolve(__dirname, "..", "artifacts", "saleads-mi-negocio-report.json");
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    stepResults: report,
    stepErrors,
    evidence: {
      screenshotsDirectory: snapshotsDir,
      terminosYCondicionesUrl: legalUrls.terminosYCondiciones,
      politicaDePrivacidadUrl: legalUrls.politicaDePrivacidad,
    },
    completedAt: new Date().toISOString(),
  };
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  console.log("Final Report:", JSON.stringify(finalReport, null, 2));

  const failedSteps = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([name]) => name);
  expect(failedSteps, `Workflow failed for steps: ${failedSteps.join(", ")}`).toEqual([]);
});
