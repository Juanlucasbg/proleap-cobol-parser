const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

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

const ARTIFACT_ROOT = path.resolve(
  __dirname,
  "..",
  "artifacts",
  "saleads_mi_negocio_full_test",
);

async function waitForUiToLoad(page, delayMs = 1200) {
  await page.waitForLoadState("domcontentloaded", { timeout: 45000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(delayMs);
}

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACT_ROOT, { recursive: true });
}

async function capture(page, filename, fullPage = false) {
  const filePath = path.join(ARTIFACT_ROOT, filename);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function clickByVisibleText(page, text, exact = false) {
  const candidates = [
    page.getByRole("button", { name: text, exact }),
    page.getByRole("link", { name: text, exact }),
    page.getByRole("menuitem", { name: text, exact }),
    page.getByRole("tab", { name: text, exact }),
    page.getByText(text, { exact }),
  ];

  for (const locator of candidates) {
    try {
      const first = locator.first();
      if (await first.isVisible({ timeout: 2500 })) {
        await first.click();
        await waitForUiToLoad(page);
        return true;
      }
    } catch (_error) {
      // Keep trying alternative visible selectors by text.
    }
  }

  return false;
}

async function assertVisibleByText(page, text, exact = false) {
  const locator = page.getByText(text, { exact }).first();
  await expect(locator).toBeVisible();
}

function initResults() {
  return Object.fromEntries(
    REPORT_FIELDS.map((name) => [
      name,
      {
        status: "FAIL",
        details: "Not executed.",
        evidence: {},
      },
    ]),
  );
}

function updateResult(results, field, status, details, evidence = {}) {
  results[field] = {
    status: status ? "PASS" : "FAIL",
    details,
    evidence,
  };
}

async function writeFinalReport(report) {
  const reportPath = path.join(ARTIFACT_ROOT, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
  return reportPath;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  await ensureArtifactsDir();

  const results = initResults();
  const startedAt = new Date().toISOString();

  if (process.env.SALEADS_BASE_URL) {
    await page.goto(process.env.SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
  }

  await waitForUiToLoad(page);

  // Step 1: Login with Google.
  try {
    const sidebarVisible = await page.getByText("Negocio", { exact: false }).first().isVisible({
      timeout: 4000,
    }).catch(() => false);

    if (!sidebarVisible) {
      const clickedLogin =
        (await clickByVisibleText(page, "Sign in with Google")) ||
        (await clickByVisibleText(page, "Iniciar sesión con Google")) ||
        (await clickByVisibleText(page, "Continuar con Google")) ||
        (await clickByVisibleText(page, "Google", false));

      if (!clickedLogin) {
        throw new Error("Could not find a Google login button.");
      }

      const googleAccountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await googleAccountOption.isVisible({ timeout: 12000 }).catch(() => false)) {
        await googleAccountOption.click();
      }
    }

    await expect(page.locator("main, [role='main'], aside").first()).toBeVisible({
      timeout: 60000,
    });
    await expect(page.getByText("Negocio", { exact: false }).first()).toBeVisible({
      timeout: 60000,
    });

    const dashboardShot = await capture(page, "01_dashboard_loaded.png");
    updateResult(
      results,
      "Login",
      true,
      "Main interface loaded and left sidebar is visible.",
      { screenshot: dashboardShot },
    );
  } catch (error) {
    updateResult(results, "Login", false, `Login flow validation failed: ${error.message}`);
  }

  // Step 2: Open Mi Negocio menu.
  try {
    const negocioVisible = await page.getByText("Negocio", { exact: false }).first().isVisible({
      timeout: 10000,
    }).catch(() => false);
    if (!negocioVisible) {
      throw new Error("Sidebar section 'Negocio' is not visible.");
    }

    await clickByVisibleText(page, "Negocio", false);
    const clickedMiNegocio = await clickByVisibleText(page, "Mi Negocio", false);
    if (!clickedMiNegocio) {
      throw new Error("Could not click 'Mi Negocio'.");
    }

    await assertVisibleByText(page, "Agregar Negocio", false);
    await assertVisibleByText(page, "Administrar Negocios", false);

    const menuShot = await capture(page, "02_mi_negocio_menu_expanded.png");
    updateResult(
      results,
      "Mi Negocio menu",
      true,
      "Mi Negocio expanded with expected submenu options.",
      { screenshot: menuShot },
    );
  } catch (error) {
    updateResult(results, "Mi Negocio menu", false, `Menu validation failed: ${error.message}`);
  }

  // Step 3: Validate Agregar Negocio modal.
  try {
    const clickedAddBusiness = await clickByVisibleText(page, "Agregar Negocio", false);
    if (!clickedAddBusiness) {
      throw new Error("Could not click 'Agregar Negocio'.");
    }

    await assertVisibleByText(page, "Crear Nuevo Negocio", false);
    await assertVisibleByText(page, "Nombre del Negocio", false);
    await assertVisibleByText(page, "Tienes 2 de 3 negocios", false);
    await expect(page.getByRole("button", { name: "Cancelar", exact: false }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Crear Negocio", exact: false }).first()).toBeVisible();

    const modalShot = await capture(page, "03_agregar_negocio_modal.png");

    const businessNameField = page
      .getByRole("textbox", { name: "Nombre del Negocio", exact: false })
      .first();
    if (await businessNameField.isVisible({ timeout: 2000 }).catch(() => false)) {
      await businessNameField.fill("Negocio Prueba Automatización");
    } else {
      await page.getByPlaceholder("Nombre del Negocio", { exact: false }).first().fill(
        "Negocio Prueba Automatización",
      ).catch(() => {});
    }

    await clickByVisibleText(page, "Cancelar", false);
    updateResult(
      results,
      "Agregar Negocio modal",
      true,
      "Modal content validated and closed successfully.",
      { screenshot: modalShot },
    );
  } catch (error) {
    updateResult(
      results,
      "Agregar Negocio modal",
      false,
      `Agregar Negocio modal validation failed: ${error.message}`,
    );
  }

  // Step 4: Open Administrar Negocios.
  try {
    await clickByVisibleText(page, "Mi Negocio", false).catch(() => {});
    const clickedManage = await clickByVisibleText(page, "Administrar Negocios", false);
    if (!clickedManage) {
      throw new Error("Could not click 'Administrar Negocios'.");
    }

    await assertVisibleByText(page, "Información General", false);
    await assertVisibleByText(page, "Detalles de la Cuenta", false);
    await assertVisibleByText(page, "Tus Negocios", false);
    await assertVisibleByText(page, "Sección Legal", false);

    const manageShot = await capture(page, "04_administrar_negocios.png", true);
    updateResult(
      results,
      "Administrar Negocios view",
      true,
      "Administrar Negocios account page loaded with all expected sections.",
      { screenshot: manageShot },
    );
  } catch (error) {
    updateResult(
      results,
      "Administrar Negocios view",
      false,
      `Administrar Negocios validation failed: ${error.message}`,
    );
  }

  // Step 5: Validate Información General.
  try {
    await assertVisibleByText(page, "Información General", false);
    await expect(page.getByText(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
    await assertVisibleByText(page, "BUSINESS PLAN", false);
    await expect(page.getByRole("button", { name: "Cambiar Plan", exact: false }).first()).toBeVisible();

    const infoBlockText = await page.locator("body").innerText();
    if (!infoBlockText || infoBlockText.trim().length < 20) {
      throw new Error("General information content appears empty.");
    }

    updateResult(
      results,
      "Información General",
      true,
      "Información General includes email, BUSINESS PLAN, and Cambiar Plan.",
    );
  } catch (error) {
    updateResult(
      results,
      "Información General",
      false,
      `Información General validation failed: ${error.message}`,
    );
  }

  // Step 6: Validate Detalles de la Cuenta.
  try {
    await assertVisibleByText(page, "Cuenta creada", false);
    await assertVisibleByText(page, "Estado activo", false);
    await assertVisibleByText(page, "Idioma seleccionado", false);
    updateResult(
      results,
      "Detalles de la Cuenta",
      true,
      "Detalles de la Cuenta shows account creation, status, and language.",
    );
  } catch (error) {
    updateResult(
      results,
      "Detalles de la Cuenta",
      false,
      `Detalles de la Cuenta validation failed: ${error.message}`,
    );
  }

  // Step 7: Validate Tus Negocios.
  try {
    await assertVisibleByText(page, "Tus Negocios", false);
    await expect(page.getByRole("button", { name: "Agregar Negocio", exact: false }).first()).toBeVisible();
    await assertVisibleByText(page, "Tienes 2 de 3 negocios", false);
    updateResult(
      results,
      "Tus Negocios",
      true,
      "Business list and quota text are visible.",
    );
  } catch (error) {
    updateResult(results, "Tus Negocios", false, `Tus Negocios validation failed: ${error.message}`);
  }

  // Step 8: Validate Términos y Condiciones.
  try {
    const appPage = page;
    const previousUrl = appPage.url();
    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

    const clickedTerms =
      (await clickByVisibleText(appPage, "Términos y Condiciones", false)) ||
      (await clickByVisibleText(appPage, "Terminos y Condiciones", false));
    if (!clickedTerms) {
      throw new Error("Could not click 'Términos y Condiciones'.");
    }

    const popup = await popupPromise;
    const legalPage = popup || appPage;
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 60000 });
    await legalPage.waitForTimeout(1200);

    await expect(
      legalPage.getByRole("heading", { name: /T[ée]rminos y Condiciones/i }).first(),
    ).toBeVisible({ timeout: 30000 });
    await expect(legalPage.locator("body")).toContainText(/\S+/, { timeout: 15000 });

    const termsShot = await capture(legalPage, "05_terminos_y_condiciones.png", true);
    const termsUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await appPage.bringToFront();
      await waitForUiToLoad(appPage, 800);
    } else if (appPage.url() !== previousUrl) {
      await appPage.goBack().catch(() => {});
      await waitForUiToLoad(appPage, 800);
    }

    updateResult(results, "Términos y Condiciones", true, "Legal terms page validated.", {
      screenshot: termsShot,
      finalUrl: termsUrl,
    });
  } catch (error) {
    updateResult(
      results,
      "Términos y Condiciones",
      false,
      `Términos y Condiciones validation failed: ${error.message}`,
    );
  }

  // Step 9: Validate Política de Privacidad.
  try {
    const appPage = page;
    const previousUrl = appPage.url();
    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

    const clickedPrivacy = await clickByVisibleText(appPage, "Política de Privacidad", false);
    if (!clickedPrivacy) {
      throw new Error("Could not click 'Política de Privacidad'.");
    }

    const popup = await popupPromise;
    const legalPage = popup || appPage;
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 60000 });
    await legalPage.waitForTimeout(1200);

    await expect(
      legalPage.getByRole("heading", { name: /Pol[íi]tica de Privacidad/i }).first(),
    ).toBeVisible({ timeout: 30000 });
    await expect(legalPage.locator("body")).toContainText(/\S+/, { timeout: 15000 });

    const privacyShot = await capture(legalPage, "06_politica_de_privacidad.png", true);
    const privacyUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await appPage.bringToFront();
      await waitForUiToLoad(appPage, 800);
    } else if (appPage.url() !== previousUrl) {
      await appPage.goBack().catch(() => {});
      await waitForUiToLoad(appPage, 800);
    }

    updateResult(results, "Política de Privacidad", true, "Privacy policy page validated.", {
      screenshot: privacyShot,
      finalUrl: privacyUrl,
    });
  } catch (error) {
    updateResult(
      results,
      "Política de Privacidad",
      false,
      `Política de Privacidad validation failed: ${error.message}`,
    );
  }

  const endedAt = new Date().toISOString();
  const report = {
    name: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
    startedAt,
    endedAt,
    environment: {
      baseUrlFromEnv: process.env.SALEADS_BASE_URL || null,
      finalAppUrl: page.url(),
    },
    summary: REPORT_FIELDS.map((field) => ({
      field,
      status: results[field].status,
      details: results[field].details,
    })),
    evidence: results,
  };

  const reportPath = await writeFinalReport(report);

  for (const field of REPORT_FIELDS) {
    console.log(`${field}: ${results[field].status} - ${results[field].details}`);
  }
  console.log(`Final report written to: ${reportPath}`);

  const failed = REPORT_FIELDS.filter((field) => results[field].status === "FAIL");
  expect(
    failed,
    `One or more validations failed. Review ${path.join(
      ARTIFACT_ROOT,
      "final-report.json",
    )} for details.`,
  ).toEqual([]);
});

