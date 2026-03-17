const fs = require("fs/promises");
const path = require("path");
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

function slugify(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function firstVisible(label, candidates) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    try {
      await locator.waitFor({ state: "visible", timeout: 8_000 });
      return locator;
    } catch (_error) {
      // try next locator
    }
  }

  throw new Error(`Visible element not found for: ${label}`);
}

async function captureCheckpoint(page, dir, name, fullPage = false) {
  const filePath = path.join(dir, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const screenshotsDir = path.resolve(
    process.env.SALEADS_SCREENSHOTS_DIR || "artifacts/saleads-mi-negocio",
  );
  await fs.mkdir(screenshotsDir, { recursive: true });

  const finalReport = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "NOT_RUN"]));
  const stepErrors = {};
  const evidence = {};
  const legalUrls = {};

  const markStep = (field, status, errorMessage = "") => {
    finalReport[field] = status;
    if (errorMessage) {
      stepErrors[field] = errorMessage;
    }
  };

  const runStep = async (field, fn) => {
    try {
      await fn();
      markStep(field, "PASS");
    } catch (error) {
      markStep(field, "FAIL", error instanceof Error ? error.message : String(error));
    }
  };

  await runStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
    if (!loginUrl) {
      throw new Error(
        "Set SALEADS_LOGIN_URL (or BASE_URL) to the login page of the current SaleADS environment.",
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const googleSignIn = await firstVisible("Google sign-in entry", [
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.locator("button:has-text('Google')"),
      page.locator("a:has-text('Google')"),
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await googleSignIn.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await popup.bringToFront();
      const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
      if ((await accountOption.count()) > 0) {
        await accountOption.first().click();
        await popup.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
      }
      await page.bringToFront();
    } else {
      const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
      if ((await accountOption.count()) > 0) {
        await accountOption.first().click();
      }
    }

    await waitForUi(page);

    const sidebar = await firstVisible("left sidebar navigation", [
      page.locator("aside"),
      page.locator("nav").filter({ hasText: /Negocio|Mi Negocio|Dashboard|Inicio/i }),
      page.getByText(/Negocio|Mi Negocio/i),
    ]);

    await expect(sidebar).toBeVisible();
    await expect(page.locator("body")).toContainText(/Negocio|Mi Negocio|Dashboard|Inicio|Panel/i);

    evidence.dashboard = await captureCheckpoint(page, screenshotsDir, "01-dashboard-loaded", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisible("Negocio section", [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
      page.getByText(/Negocio/i),
    ]);
    await negocioSection.click();
    await waitForUi(page);

    const miNegocio = await firstVisible("Mi Negocio option", [
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ]);
    await miNegocio.click();
    await waitForUi(page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    evidence.menu = await captureCheckpoint(page, screenshotsDir, "02-mi-negocio-menu-expanded", true);
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusiness = await firstVisible("Agregar Negocio", [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await addBusiness.click();
    await waitForUi(page);

    const modal = await firstVisible("Crear Nuevo Negocio modal", [
      page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
      page.locator("[role='dialog']").filter({ hasText: /Crear Nuevo Negocio/i }),
      page.locator("div,section").filter({ hasText: /Crear Nuevo Negocio/i }),
    ]);

    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    const businessNameInput = await firstVisible("Nombre del Negocio input", [
      modal.getByLabel(/Nombre del Negocio/i),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.locator("input[placeholder*='Nombre']"),
      modal.locator("input[name*='nombre']"),
      modal.locator("input").first(),
    ]);
    await expect(businessNameInput).toBeVisible();
    await expect(modal.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    evidence.modal = await captureCheckpoint(page, screenshotsDir, "03-crear-nuevo-negocio-modal", true);

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await modal.getByRole("button", { name: /Cancelar/i }).click();
    await expect(modal).toBeHidden({ timeout: 10_000 });
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page
      .getByText(/Administrar Negocios/i)
      .first()
      .isVisible()
      .catch(() => false);

    if (!administrarVisible) {
      const miNegocio = await firstVisible("Mi Negocio option (re-expand)", [
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ]);
      await miNegocio.click();
      await waitForUi(page);
    }

    const administrar = await firstVisible("Administrar Negocios", [
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ]);
    await administrar.click();
    await waitForUi(page);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();

    evidence.accountPage = await captureCheckpoint(page, screenshotsDir, "04-administrar-negocios-page", true);
  });

  await runStep("Información General", async () => {
    const infoSection = await firstVisible("Información General section", [
      page.locator("section,div").filter({ hasText: /Informaci[oó]n General/i }),
      page.locator("main").filter({ hasText: /Informaci[oó]n General/i }),
    ]);
    const rawSectionText = await infoSection.innerText();
    const text = rawSectionText.replace(/\s+/g, " ").trim();

    const emailMatch = text.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    expect(emailMatch, "User email should be visible").toBeTruthy();
    expect(text).toMatch(/BUSINESS PLAN/i);
    expect(text).toMatch(/Cambiar Plan/i);

    const ignoredNamePatterns =
      /informaci[oó]n general|business plan|cambiar plan|correo|email|plan|cuenta|estado|idioma|negocios/i;
    const probableNameLine = rawSectionText
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .find(
        (line) =>
          !ignoredNamePatterns.test(line) &&
          !line.includes("@") &&
          /[A-Za-zÀ-ÿ]/.test(line) &&
          line.replace(/[^A-Za-zÀ-ÿ]/g, "").length >= 4,
      );
    expect(probableNameLine, "A non-empty user name should be visible").toBeTruthy();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const businessSection = await firstVisible("Tus Negocios section", [
      page.locator("section,div").filter({ hasText: /Tus Negocios/i }),
      page.locator("main").filter({ hasText: /Tus Negocios/i }),
    ]);

    await expect(businessSection.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(businessSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
  });

  const validateLegalPage = async (linkText, headingText, evidenceName) => {
    const sourcePage = page;
    const maybePopup = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

    const legalLink = await firstVisible(linkText, [
      sourcePage.getByRole("link", { name: new RegExp(linkText, "i") }),
      sourcePage.getByRole("button", { name: new RegExp(linkText, "i") }),
      sourcePage.getByText(new RegExp(linkText, "i")),
    ]);
    await legalLink.click();
    await waitForUi(sourcePage);

    const popup = await maybePopup;
    const legalPage = popup || sourcePage;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await popup.bringToFront();
      await waitForUi(popup);
    }

    const heading = await firstVisible(`${headingText} heading`, [
      legalPage.getByRole("heading", { name: new RegExp(headingText, "i") }),
      legalPage.getByText(new RegExp(headingText, "i")),
    ]);
    await expect(heading).toBeVisible();

    const legalText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
    expect(legalText.length, "Legal content should be visible").toBeGreaterThan(120);

    evidence[evidenceName] = await captureCheckpoint(
      legalPage,
      screenshotsDir,
      `${slugify(evidenceName)}-legal-page`,
      true,
    );
    const finalUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await sourcePage.bringToFront();
      await waitForUi(sourcePage);
    } else {
      await legalPage.goBack().catch(() => {});
      await waitForUi(sourcePage);
    }

    return finalUrl;
  };

  await runStep("Términos y Condiciones", async () => {
    legalUrls.terminos = await validateLegalPage(
      "Términos y Condiciones",
      "Términos y Condiciones",
      "08-terminos-y-condiciones",
    );
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls.politica = await validateLegalPage(
      "Política de Privacidad",
      "Política de Privacidad",
      "09-politica-de-privacidad",
    );
  });

  const output = {
    test: "saleads_mi_negocio_full_test",
    statusByField: finalReport,
    legalUrls,
    screenshots: evidence,
    errors: stepErrors,
  };

  await testInfo.attach("final-report.json", {
    body: JSON.stringify(output, null, 2),
    contentType: "application/json",
  });

  // Printed report for CI logs / cron artifacts.
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(output, null, 2));

  const failedFields = Object.entries(finalReport)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);
  expect(
    failedFields,
    `Validation failed for: ${failedFields.join(", ") || "none"}. See attached final-report.json`,
  ).toEqual([]);
});
