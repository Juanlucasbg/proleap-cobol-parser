const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

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

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(750);
}

async function isVisible(locator, timeout = 4000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function pickVisible(locators, timeout = 4000) {
  for (const locator of locators) {
    if (await isVisible(locator, timeout)) {
      return locator.first();
    }
  }

  return locators[0].first();
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function saveScreenshot(page, evidenceDir, fileName, fullPage = false) {
  const targetPath = path.join(evidenceDir, fileName);
  await page.screenshot({ path: targetPath, fullPage });
  return targetPath;
}

async function runStep(report, fieldName, handler) {
  try {
    await handler();
    report.results[fieldName] = "PASS";
  } catch (error) {
    report.results[fieldName] = "FAIL";
    report.errors[fieldName] = error.message;
  }
}

async function openLegalLinkAndValidate({
  appPage,
  linkPattern,
  headingPattern,
  evidenceDir,
  screenshotName,
}) {
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const link = await pickVisible(
    [appPage.getByRole("link", { name: linkPattern }), appPage.getByText(linkPattern)],
    5000,
  );

  await clickAndWait(appPage, link);

  let legalPage = await popupPromise;
  if (!legalPage) {
    legalPage = appPage;
  } else {
    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUi(legalPage);
  }

  const heading = await pickVisible(
    [legalPage.getByRole("heading", { name: headingPattern }), legalPage.getByText(headingPattern)],
    20000,
  );
  await expect(heading).toBeVisible();

  await expect(legalPage.locator("body")).toContainText(/\S{20,}/);
  const finalUrl = legalPage.url();
  const screenshotPath = await saveScreenshot(legalPage, evidenceDir, screenshotName, true);

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack().catch(() => {});
    await waitForUi(appPage);
  }

  return { finalUrl, screenshotPath };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const googleAccount = process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";
  const evidenceDir = path.resolve(process.env.E2E_EVIDENCE_DIR || "test-results/evidence");

  fs.mkdirSync(evidenceDir, { recursive: true });

  const report = {
    testName: "saleads_mi_negocio_full_test",
    timestamp: new Date().toISOString(),
    environmentUrl: loginUrl || "NOT_PROVIDED",
    results: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "NOT_RUN"])),
    errors: {},
    evidence: {},
    legalUrls: {},
  };

  let appPage = page;

  await runStep(report, "Login", async () => {
    if (loginUrl) {
      await appPage.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(appPage);
    } else if (appPage.url() === "about:blank") {
      throw new Error("Set SALEADS_LOGIN_URL for the target environment before running this test.");
    }

    const signInButton = await pickVisible(
      [
        appPage.getByRole("button", { name: /google|sign in|iniciar sesi[o\u00f3]n/i }),
        appPage.getByRole("link", { name: /google|sign in|iniciar sesi[o\u00f3]n/i }),
        appPage.getByText(/google/i),
      ],
      12000,
    );

    const popupPromise = appPage.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(appPage, signInButton);

    const popup = await popupPromise;
    const authPage = popup || appPage;

    await authPage.waitForLoadState("domcontentloaded");
    await waitForUi(authPage);

    const accountOption = authPage.getByText(googleAccount, { exact: true });
    if (await isVisible(accountOption, 10000)) {
      await accountOption.click();
      await waitForUi(authPage);
    }

    await appPage.bringToFront();
    await waitForUi(appPage);

    const sidebarCandidate = await pickVisible(
      [appPage.locator("aside"), appPage.getByRole("navigation"), appPage.locator('[class*="sidebar"]')],
      25000,
    );

    await expect(sidebarCandidate).toBeVisible();
    await expect(appPage.getByText(/Negocio/i)).toBeVisible({ timeout: 25000 });

    report.evidence.dashboard = await saveScreenshot(appPage, evidenceDir, "01-dashboard-loaded.png", true);
  });

  await runStep(report, "Mi Negocio menu", async () => {
    const negocioSection = await pickVisible(
      [
        appPage.getByRole("link", { name: /^Negocio$/i }),
        appPage.getByRole("button", { name: /^Negocio$/i }),
        appPage.getByText(/^Negocio$/i),
      ],
      10000,
    );
    await clickAndWait(appPage, negocioSection);

    const miNegocioOption = await pickVisible(
      [
        appPage.getByRole("link", { name: /Mi Negocio/i }),
        appPage.getByRole("button", { name: /Mi Negocio/i }),
        appPage.getByText(/Mi Negocio/i),
      ],
      10000,
    );
    await clickAndWait(appPage, miNegocioOption);

    await expect(appPage.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(appPage.getByText(/Administrar Negocios/i)).toBeVisible();

    report.evidence.miNegocioMenu = await saveScreenshot(
      appPage,
      evidenceDir,
      "02-mi-negocio-menu-expanded.png",
      false,
    );
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    const addBusinessOption = await pickVisible(
      [
        appPage.getByRole("link", { name: /Agregar Negocio/i }),
        appPage.getByRole("button", { name: /Agregar Negocio/i }),
        appPage.getByText(/Agregar Negocio/i),
      ],
      10000,
    );

    await clickAndWait(appPage, addBusinessOption);

    const modal = await pickVisible(
      [
        appPage.getByRole("dialog"),
        appPage.locator('[role="dialog"]'),
        appPage.locator(".modal, [class*='modal']"),
      ],
      10000,
    );
    await expect(modal).toBeVisible();

    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    const businessNameInput = await pickVisible(
      [
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
        modal.locator("input"),
      ],
      5000,
    );
    await expect(businessNameInput).toBeVisible();
    await expect(modal.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    report.evidence.agregarNegocioModal = await saveScreenshot(
      appPage,
      evidenceDir,
      "03-agregar-negocio-modal.png",
      false,
    );

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(appPage, modal.getByRole("button", { name: /Cancelar/i }));
  });

  await runStep(report, "Administrar Negocios view", async () => {
    if (!(await isVisible(appPage.getByText(/Administrar Negocios/i), 3000))) {
      const miNegocioOption = await pickVisible(
        [
          appPage.getByRole("link", { name: /Mi Negocio/i }),
          appPage.getByRole("button", { name: /Mi Negocio/i }),
          appPage.getByText(/Mi Negocio/i),
        ],
        10000,
      );
      await clickAndWait(appPage, miNegocioOption);
    }

    const adminOption = await pickVisible(
      [
        appPage.getByRole("link", { name: /Administrar Negocios/i }),
        appPage.getByRole("button", { name: /Administrar Negocios/i }),
        appPage.getByText(/Administrar Negocios/i),
      ],
      10000,
    );
    await clickAndWait(appPage, adminOption);

    await expect(appPage.getByText(/Informaci(?:o|\u00f3)n General/i)).toBeVisible({ timeout: 20000 });
    await expect(appPage.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(appPage.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(appPage.getByText(/Secci(?:o|\u00f3)n Legal/i)).toBeVisible();

    report.evidence.accountPage = await saveScreenshot(
      appPage,
      evidenceDir,
      "04-administrar-negocios-view.png",
      true,
    );
  });

  await runStep(report, "Informacion General", async () => {
    await expect(appPage.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)).toBeVisible();
    await expect(appPage.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const nameCandidate = appPage
      .locator("h1, h2, h3, p, span, div")
      .filter({ hasNotText: /BUSINESS PLAN|Cambiar Plan|@|Informaci(?:o|\u00f3)n General/i })
      .first();
    await expect(nameCandidate).toBeVisible();
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await expect(appPage.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(appPage.getByText(/Estado activo/i)).toBeVisible();
    await expect(appPage.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    await expect(appPage.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(appPage.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
  });

  await runStep(report, "Terminos y Condiciones", async () => {
    const result = await openLegalLinkAndValidate({
      appPage,
      linkPattern: /T[e\u00e9]rminos y Condiciones/i,
      headingPattern: /T[e\u00e9]rminos y Condiciones/i,
      evidenceDir,
      screenshotName: "05-terminos-y-condiciones.png",
    });

    report.evidence.termsAndConditions = result.screenshotPath;
    report.legalUrls.termsAndConditions = result.finalUrl;
  });

  await runStep(report, "Politica de Privacidad", async () => {
    const result = await openLegalLinkAndValidate({
      appPage,
      linkPattern: /Pol[i\u00ed]tica de Privacidad/i,
      headingPattern: /Pol[i\u00ed]tica de Privacidad/i,
      evidenceDir,
      screenshotName: "06-politica-de-privacidad.png",
    });

    report.evidence.privacyPolicy = result.screenshotPath;
    report.legalUrls.privacyPolicy = result.finalUrl;
  });

  const reportPath = path.resolve(evidenceDir, "saleads-mi-negocio-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf-8");

  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedSteps = Object.entries(report.results)
    .filter(([, status]) => status === "FAIL")
    .map(([step]) => step);

  expect(
    failedSteps,
    `One or more validations failed. See ${reportPath} for details: ${failedSteps.join(", ")}`,
  ).toEqual([]);
});
