const fs = require("fs/promises");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_KEYS = [
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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 7000 }),
    page.waitForLoadState("domcontentloaded", { timeout: 7000 })
  ]).catch(() => {});
  await page.waitForTimeout(800);
}

async function firstVisibleLocator(locators) {
  for (const locator of locators) {
    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  return null;
}

async function clickByVisibleText(page, text, scope = page) {
  const exactRegex = new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i");
  const containsRegex = new RegExp(escapeRegExp(text), "i");

  const target = await firstVisibleLocator([
    scope.getByRole("button", { name: exactRegex }),
    scope.getByRole("link", { name: exactRegex }),
    scope.getByRole("menuitem", { name: exactRegex }),
    scope.getByText(exactRegex),
    scope.getByRole("button", { name: containsRegex }),
    scope.getByRole("link", { name: containsRegex }),
    scope.getByText(containsRegex)
  ]);

  if (!target) {
    throw new Error(`No visible target found for text: "${text}"`);
  }

  await target.click();
  await waitForUi(page);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const fileName = `${name.replace(/[^a-zA-Z0-9_-]/g, "_")}.png`;
  const filePath = testInfo.outputPath(fileName);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, {
    path: filePath,
    contentType: "image/png"
  });
  return filePath;
}

function sectionByHeading(page, headingRegex) {
  return page
    .locator("section, article, div")
    .filter({ has: page.getByText(headingRegex) })
    .first();
}

async function selectGoogleAccountIfVisible(page) {
  const accountByEmail = await firstVisibleLocator([
    page.getByRole("button", { name: new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i") }),
    page.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i"))
  ]);

  if (accountByEmail) {
    await accountByEmail.click();
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = {};
  const errors = {};
  const evidence = {};
  const finalUrls = {};

  for (const key of REPORT_KEYS) {
    report[key] = "SKIPPED";
  }

  const runStep = async (label, stepFn) => {
    try {
      await stepFn();
      report[label] = "PASS";
    } catch (error) {
      report[label] = "FAIL";
      errors[label] = error instanceof Error ? error.message : String(error);
    }
  };

  const baseUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL || "";
  if (baseUrl) {
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else {
    // Keep environment-agnostic behavior: caller can pre-open any SaleADS login page.
    await waitForUi(page);
  }

  await runStep("Login", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

    const loginButton = await firstVisibleLocator([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
    ]);

    if (!loginButton) {
      throw new Error("Google login button not found.");
    }

    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
      await waitForUi(popup);
      await selectGoogleAccountIfVisible(popup);
      await popup.waitForTimeout(1500).catch(() => {});
    } else {
      await selectGoogleAccountIfVisible(page);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 45000 });
    await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({ timeout: 45000 });

    evidence.dashboard = await captureCheckpoint(page, testInfo, "01_dashboard_loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 30000 });

    await clickByVisibleText(page, "Mi Negocio", sidebar).catch(async () => {
      await clickByVisibleText(page, "Negocio", sidebar);
    });

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20000 });

    evidence.menuExpanded = await captureCheckpoint(page, testInfo, "02_mi_negocio_menu_expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, "Agregar Negocio");

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i))).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    evidence.agregarNegocioModal = await captureCheckpoint(page, testInfo, "03_agregar_negocio_modal");

    const businessNameInput = await firstVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: /Nombre del Negocio/i })
    ]);

    if (businessNameInput) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }

    await clickByVisibleText(page, "Cancelar");
  });

  await runStep("Administrar Negocios view", async () => {
    const sidebar = page.locator("aside, nav").first();
    if (await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false)) {
      await clickByVisibleText(page, "Administrar Negocios");
    } else {
      await clickByVisibleText(page, "Mi Negocio", sidebar).catch(async () => {
        await clickByVisibleText(page, "Negocio", sidebar);
      });
      await clickByVisibleText(page, "Administrar Negocios");
    }

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 30000 });

    evidence.accountPage = await captureCheckpoint(page, testInfo, "04_administrar_negocios_page", true);
  });

  await runStep("Información General", async () => {
    const infoSection = sectionByHeading(page, /Información General/i);
    await expect(infoSection).toBeVisible();

    await expect(infoSection.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i"))).toBeVisible();
    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const visibleTextCount = await infoSection.locator(":scope *").filter({ hasText: /\S+/ }).count();
    expect(visibleTextCount).toBeGreaterThan(3);
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = sectionByHeading(page, /Detalles de la Cuenta/i);
    await expect(detailsSection).toBeVisible();

    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const businessSection = sectionByHeading(page, /Tus Negocios/i);
    await expect(businessSection).toBeVisible();

    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();

    const hasBusinessList =
      (await businessSection.locator("li, [role='listitem'], tr, .card, .business-item").count()) > 0 ||
      (await businessSection.locator(":scope *").filter({ hasText: /\S+/ }).count()) > 5;
    expect(hasBusinessList).toBeTruthy();
  });

  const validateLegalDocument = async (linkText, headingRegex, reportKey, screenshotName) => {
    await runStep(reportKey, async () => {
      const legalSection = sectionByHeading(page, /Secci[oó]n Legal/i);
      await expect(legalSection).toBeVisible({ timeout: 20000 });

      const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
      const sameTabNavPromise = page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 5000 }).catch(() => null);

      await clickByVisibleText(page, linkText, legalSection);

      const popup = await popupPromise;
      let legalPage = page;

      if (popup) {
        legalPage = popup;
        await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
      } else {
        await sameTabNavPromise;
      }

      await waitForUi(legalPage);
      await expect(legalPage.getByText(headingRegex).first()).toBeVisible({ timeout: 20000 });

      const legalBodyCount = await legalPage.locator("p, li, article, main, section").filter({ hasText: /\S+/ }).count();
      expect(legalBodyCount).toBeGreaterThan(0);

      evidence[screenshotName] = await captureCheckpoint(legalPage, testInfo, screenshotName, true);
      finalUrls[reportKey] = legalPage.url();

      if (legalPage !== page) {
        await legalPage.close({ runBeforeUnload: true }).catch(() => {});
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      }
      await waitForUi(page);
    });
  };

  await validateLegalDocument(
    "Términos y Condiciones",
    /T[eé]rminos y Condiciones/i,
    "Términos y Condiciones",
    "08_terminos_y_condiciones"
  );

  await validateLegalDocument(
    "Política de Privacidad",
    /Pol[ií]tica de Privacidad/i,
    "Política de Privacidad",
    "09_politica_de_privacidad"
  );

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
    generatedAt: new Date().toISOString(),
    report: REPORT_KEYS.map((key) => ({
      field: key,
      status: report[key],
      error: errors[key] || null,
      finalUrl: finalUrls[key] || null
    })),
    evidence
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_full_test_report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  await testInfo.attach("final_report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedSteps = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([name]) => name);

  expect(
    failedSteps,
    `One or more validations failed.\n${JSON.stringify(finalReport.report, null, 2)}`
  ).toEqual([]);
});
