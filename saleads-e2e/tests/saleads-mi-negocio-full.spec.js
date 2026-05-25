const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login to SaleADS and validate full Mi Negocio workflow", async ({
    page,
    context,
  }, testInfo) => {
    const runReport = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
    const legalUrls = {};
    const checkpointsDir = testInfo.outputPath("checkpoints");
    fs.mkdirSync(checkpointsDir, { recursive: true });

    const startUrl = process.env.SALEADS_START_URL;
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }

    await executeStep("Login", runReport, async () => {
      const loginButton = await firstVisibleLocator(page, [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("button", { name: /sign in|login|iniciar sesi[oó]n/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ]);

      await clickAndWait(page, loginButton);

      const accountSelector = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await isVisible(accountSelector, 6000)) {
        await clickAndWait(page, accountSelector);
      }

      await expect(page.locator("main, [role='main']").first()).toBeVisible({ timeout: 45000 });

      const sidebar = page.locator("aside, nav").filter({
        hasText: /Negocio|Mi Negocio|Dashboard/i,
      }).first();
      await expect(sidebar).toBeVisible({ timeout: 45000 });
      await captureCheckpoint(page, checkpointsDir, "01-dashboard-loaded.png");
    });

    await executeStep("Mi Negocio menu", runReport, async () => {
      const negocioSection = page.getByText(/^Negocio$/i).first();
      await expect(negocioSection).toBeVisible({ timeout: 20000 });

      const miNegocio = await firstVisibleLocator(page, [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);
      await clickAndWait(page, miNegocio);

      await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible({ timeout: 20000 });
      await captureCheckpoint(page, checkpointsDir, "02-mi-negocio-expanded.png");
    });

    await executeStep("Agregar Negocio modal", runReport, async () => {
      const agregarNegocioAction = await firstVisibleLocator(page, [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);
      await clickAndWait(page, agregarNegocioAction);

      const modalTitle = page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first();
      await expect(modalTitle).toBeVisible({ timeout: 20000 });

      const businessNameField = await firstVisibleLocator(page, [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.getByRole("textbox", { name: /Nombre del Negocio/i }),
      ]);

      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible({ timeout: 20000 });
      await captureCheckpoint(page, checkpointsDir, "03-agregar-negocio-modal.png");

      await businessNameField.click();
      await waitForUiToSettle(page);
      await businessNameField.fill("Negocio Prueba Automatización");
      await waitForUiToSettle(page);

      const cancelButton = page.getByRole("button", { name: /^Cancelar$/i }).first();
      await clickAndWait(page, cancelButton);
      await expect(modalTitle).toBeHidden({ timeout: 10000 });
    });

    await executeStep("Administrar Negocios view", runReport, async () => {
      if (!(await isVisible(page.getByText(/^Administrar Negocios$/i).first(), 2000))) {
        const miNegocio = await firstVisibleLocator(page, [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ]);
        await clickAndWait(page, miNegocio);
      }

      const administrarNegocios = await firstVisibleLocator(page, [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ]);
      await clickAndWait(page, administrarNegocios);

      await expect(page.getByRole("heading", { name: /Informaci[oó]n General/i })).toBeVisible({ timeout: 30000 });
      await expect(page.getByRole("heading", { name: /Detalles de la Cuenta/i })).toBeVisible({ timeout: 30000 });
      await expect(page.getByRole("heading", { name: /Tus Negocios/i })).toBeVisible({ timeout: 30000 });
      await expect(page.getByRole("heading", { name: /Secci[oó]n Legal/i })).toBeVisible({ timeout: 30000 });
      await captureCheckpoint(page, checkpointsDir, "04-administrar-negocios-view.png", true);
    });

    await executeStep("Información General", runReport, async () => {
      const section = await sectionByHeading(page, /Informaci[oó]n General/i);
      await expect(section.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 20000 });
      await expect(section.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 20000 });

      const emailLocator = section.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first();
      await expect(emailLocator).toBeVisible({ timeout: 20000 });
      const emailText = (await emailLocator.innerText()).trim();
      const sectionText = await section.innerText();
      assertLikelyUserNameIsVisible(sectionText, emailText);
    });

    await executeStep("Detalles de la Cuenta", runReport, async () => {
      const section = await sectionByHeading(page, /Detalles de la Cuenta/i);
      await expect(section.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 20000 });
      await expect(section.getByText(/Estado activo/i)).toBeVisible({ timeout: 20000 });
      await expect(section.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 20000 });
    });

    await executeStep("Tus Negocios", runReport, async () => {
      const section = await sectionByHeading(page, /Tus Negocios/i);
      await expect(section.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible({ timeout: 20000 });
      await expect(section.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 20000 });

      const listLikeArea = section.locator("ul, ol, table, [role='list'], [role='table'], [data-testid*='business']").first();
      await expect(listLikeArea).toBeVisible({ timeout: 20000 });
    });

    await executeStep("Términos y Condiciones", runReport, async () => {
      legalUrls.terminosYCondiciones = await validateLegalLink({
        page,
        context,
        checkpointsDir,
        linkName: /T[eé]rminos y Condiciones/i,
        headingName: /T[eé]rminos y Condiciones/i,
        screenshotName: "08-terminos-y-condiciones.png",
      });
    });

    await executeStep("Política de Privacidad", runReport, async () => {
      legalUrls.politicaDePrivacidad = await validateLegalLink({
        page,
        context,
        checkpointsDir,
        linkName: /Pol[ií]tica de Privacidad/i,
        headingName: /Pol[ií]tica de Privacidad/i,
        screenshotName: "09-politica-de-privacidad.png",
      });
    });

    const finalReport = {
      test_name: "saleads_mi_negocio_full_test",
      generated_at: new Date().toISOString(),
      results: runReport,
      legal_urls: legalUrls,
    };

    const reportPath = testInfo.outputPath("final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json",
    });
    console.log("Final workflow report:");
    console.table(runReport);
    console.log("Legal URLs:", legalUrls);

    expect(
      Object.values(runReport),
      "Each workflow section must PASS",
    ).not.toContain("FAIL");
  });
});

async function executeStep(stepName, report, fn) {
  try {
    await fn();
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = "FAIL";
    console.error(`[${stepName}] ${error.message}`);
  }
}

async function sectionByHeading(page, headingRegex) {
  const heading = page.getByRole("heading", { name: headingRegex }).first();
  await expect(heading).toBeVisible({ timeout: 20000 });
  const section = heading.locator("xpath=ancestor::section[1]").first();

  if (await isVisible(section, 1000)) {
    return section;
  }

  // Fallback for layouts not using semantic section wrappers.
  return heading.locator("xpath=ancestor::*[self::div or self::article][1]").first();
}

async function validateLegalLink({
  page,
  context,
  checkpointsDir,
  linkName,
  headingName,
  screenshotName,
}) {
  const trigger = await firstVisibleLocator(page, [
    page.getByRole("link", { name: linkName }),
    page.getByRole("button", { name: linkName }),
    page.getByText(linkName),
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickAndWait(page, trigger);
  const popup = await popupPromise;

  const legalPage = popup || page;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 45000 });
  await waitForUiToSettle(legalPage);

  const heading = await firstVisibleLocator(legalPage, [
    legalPage.getByRole("heading", { name: headingName }),
    legalPage.getByText(headingName),
  ]);
  await expect(heading).toBeVisible({ timeout: 30000 });

  const legalText = (await legalPage.locator("main, article, body").first().innerText()).trim();
  if (legalText.length < 120) {
    throw new Error("Legal content text appears too short.");
  }

  await captureCheckpoint(legalPage, checkpointsDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToSettle(page);
  }

  return finalUrl;
}

async function firstVisibleLocator(page, locators, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  let lastError;

  while (Date.now() <= deadline) {
    for (const locator of locators) {
      try {
        if (await isVisible(locator.first(), 250)) {
          return locator.first();
        }
      } catch (error) {
        lastError = error;
      }
    }

    await page.waitForTimeout(250);
  }

  if (lastError) {
    throw lastError;
  }

  throw new Error("Expected a visible element by text, but none was found.");
}

async function isVisible(locator, timeoutMs = 2000) {
  try {
    await locator.waitFor({ state: "visible", timeout: timeoutMs });
    return true;
  } catch {
    return false;
  }
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible({ timeout: 30000 });
  await locator.click();
  await waitForUiToSettle(page);
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 45000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function captureCheckpoint(page, checkpointsDir, name, fullPage = false) {
  await page.screenshot({
    path: path.join(checkpointsDir, name),
    fullPage,
  });
}

function assertLikelyUserNameIsVisible(sectionText, emailText) {
  const blacklist = [
    "información general",
    "informacion general",
    "business plan",
    "cambiar plan",
    "cuenta creada",
    "estado activo",
    "idioma seleccionado",
  ];

  const lines = sectionText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  const probableNameLine = lines.find((line) => {
    const normalized = line.toLowerCase();
    if (normalized.includes("@")) return false;
    if (normalized === emailText.toLowerCase()) return false;
    if (blacklist.some((item) => normalized.includes(item))) return false;
    return /[a-záéíóúñ]{2,}/i.test(line);
  });

  if (!probableNameLine) {
    throw new Error("User name was not detected in 'Información General'.");
  }
}
