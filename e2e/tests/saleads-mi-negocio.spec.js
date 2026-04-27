const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const GOOGLE_ACCOUNT =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const TEST_BUSINESS_NAME =
  process.env.SALEADS_TEST_BUSINESS_NAME || "Negocio Prueba Automatizacion";
const UI_PAUSE_MS = Number(process.env.SALEADS_UI_PAUSE_MS || 500);

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

function buildDefaultReport() {
  return REPORT_KEYS.reduce((acc, key) => {
    acc[key] = "FAIL: Not executed";
    return acc;
  }, {});
}

function shortError(error) {
  if (!error) {
    return "Unknown error";
  }

  return String(error.message || error)
    .split("\n")[0]
    .trim();
}

async function findVisibleLocator(candidates, timeout = 12000) {
  for (const candidate of candidates) {
    const locator = candidate.first();

    try {
      await locator.waitFor({ state: "visible", timeout });
      return locator;
    } catch (error) {
      // Try next candidate.
    }
  }

  return null;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {
    // SPAs may keep network requests open for long periods.
  });
  if (UI_PAUSE_MS > 0) {
    await page.waitForTimeout(UI_PAUSE_MS);
  }
}

async function capture(page, testInfo, fileName, fullPage = false) {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function openLegalLink({
  appPage,
  linkPattern,
  headingPattern,
  screenshotName,
  testInfo
}) {
  const legalLink = await findVisibleLocator([
    appPage.getByRole("link", { name: linkPattern }),
    appPage.getByRole("button", { name: linkPattern }),
    appPage.getByText(linkPattern),
    appPage.locator(`a:has-text("${linkPattern.source}")`)
  ]);
  expect(legalLink, "Expected legal link/button to be visible").not.toBeNull();

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await clickAndWait(appPage, legalLink);
  const popupPage = await popupPromise;

  const legalPage = popupPage || appPage;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 30000 });

  const heading = await findVisibleLocator(
    [
      legalPage.getByRole("heading", { name: headingPattern }),
      legalPage.getByText(headingPattern)
    ],
    20000
  );
  expect(heading, "Expected legal page heading").not.toBeNull();

  const legalText = (await legalPage.locator("body").innerText()).trim();
  expect(legalText.length, "Expected legal content text to be visible").toBeGreaterThan(120);

  const url = legalPage.url();
  const screenshotPath = await capture(legalPage, testInfo, screenshotName, true);

  if (popupPage) {
    await popupPage.close().catch(() => {
      // If already closed by browser, continue.
    });
    await appPage.bringToFront();
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {
      // If app prevents history navigation, continue in current tab.
    });
  }

  await appPage.waitForLoadState("domcontentloaded").catch(() => {});
  if (UI_PAUSE_MS > 0) {
    await appPage.waitForTimeout(UI_PAUSE_MS);
  }

  return { url, screenshotPath, openedNewTab: Boolean(popupPage) };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildDefaultReport();
  const evidence = {};
  const legalUrls = {};
  const notes = [];

  const baseUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (!baseUrl) {
    throw new Error(
      "Missing SALEADS_BASE_URL (or BASE_URL). Provide the SaleADS login URL for the target environment."
    );
  }

  await page.goto(baseUrl, { waitUntil: "domcontentloaded" });

  let loginOk = false;
  let menuOk = false;
  let administrarOk = false;

  // Step 1: Login with Google.
  try {
    const sidebarAlreadyVisible = await page
      .getByText(/Mi Negocio|Negocio|Administrar Negocios/i)
      .first()
      .isVisible()
      .catch(() => false);

    if (!sidebarAlreadyVisible) {
      const loginTrigger = await findVisibleLocator([
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|iniciar con google|google/i),
        page.locator("button:has-text('Google')")
      ]);
      expect(loginTrigger, "Expected a Google login button").not.toBeNull();

      const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, loginTrigger);
      const popup = await popupPromise;
      const authPage = popup || page;

      await authPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});

      const accountChoice = await findVisibleLocator(
        [
          authPage.getByText(GOOGLE_ACCOUNT, { exact: false }),
          authPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT, "i") }),
          authPage.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT, "i") })
        ],
        12000
      );
      if (accountChoice) {
        await clickAndWait(authPage, accountChoice);
      }

      if (popup) {
        await popup.waitForClose({ timeout: 45000 }).catch(() => {});
      }
    }

    const sidebarNav = await findVisibleLocator([
      page.locator("aside"),
      page.locator("nav"),
      page.getByText(/Mi Negocio|Negocio|Administrar Negocios/i)
    ], 45000);
    expect(sidebarNav, "Expected main app interface with sidebar").not.toBeNull();

    evidence.dashboard = await capture(page, testInfo, "01-dashboard-loaded.png", true);
    report.Login = "PASS";
    loginOk = true;
  } catch (error) {
    report.Login = `FAIL: ${shortError(error)}`;
    notes.push("Login failed; downstream checks may be blocked.");
  }

  // Step 2: Open Mi Negocio menu.
  try {
    expect(loginOk, "Cannot continue menu checks without login").toBeTruthy();

    const negocioSection = await findVisibleLocator([
      page.getByRole("button", { name: /negocio/i }),
      page.getByRole("link", { name: /negocio/i }),
      page.getByText(/^Negocio$/i),
      page.locator("nav").getByText(/Negocio/i)
    ]);
    if (negocioSection) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocio = await findVisibleLocator([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    expect(miNegocio, "Expected 'Mi Negocio' option in sidebar").not.toBeNull();
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    evidence.miNegocioMenu = await capture(page, testInfo, "02-mi-negocio-expanded.png", true);
    report["Mi Negocio menu"] = "PASS";
    menuOk = true;
  } catch (error) {
    report["Mi Negocio menu"] = `FAIL: ${shortError(error)}`;
    notes.push("Mi Negocio menu validation failed.");
  }

  // Step 3: Validate Agregar Negocio modal.
  try {
    expect(menuOk, "Cannot continue modal checks without Mi Negocio menu").toBeTruthy();

    const agregarNegocio = await findVisibleLocator([
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    expect(agregarNegocio, "Expected 'Agregar Negocio' control").not.toBeNull();
    await clickAndWait(page, agregarNegocio);

    const modal = page.getByRole("dialog");
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    evidence.agregarNegocioModal = await capture(page, testInfo, "03-agregar-negocio-modal.png", true);

    const businessNameInput = await findVisibleLocator([
      modal.getByRole("textbox", { name: /Nombre del Negocio/i }),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.locator("input")
    ]);
    if (businessNameInput) {
      await businessNameInput.fill(TEST_BUSINESS_NAME);
    }

    const cancelarBtn = modal.getByRole("button", { name: /Cancelar/i });
    await clickAndWait(page, cancelarBtn);
    await expect(modal).not.toBeVisible();

    report["Agregar Negocio modal"] = "PASS";
  } catch (error) {
    report["Agregar Negocio modal"] = `FAIL: ${shortError(error)}`;
    notes.push("Agregar Negocio modal validation failed.");
  }

  // Step 4: Open Administrar Negocios.
  try {
    expect(menuOk, "Cannot continue account page checks without menu").toBeTruthy();

    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocio = await findVisibleLocator([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ]);
      expect(miNegocio, "Expected 'Mi Negocio' control to expand submenu").not.toBeNull();
      await clickAndWait(page, miNegocio);
    }

    const administrarNegocios = await findVisibleLocator([
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    expect(administrarNegocios, "Expected 'Administrar Negocios' option").not.toBeNull();
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

    evidence.accountPage = await capture(page, testInfo, "04-administrar-negocios-full-page.png", true);
    report["Administrar Negocios view"] = "PASS";
    administrarOk = true;
  } catch (error) {
    report["Administrar Negocios view"] = `FAIL: ${shortError(error)}`;
    notes.push("Administrar Negocios section could not be validated.");
  }

  // Step 5: Validate Información General.
  try {
    expect(administrarOk, "Cannot validate account sections without account page").toBeTruthy();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.getByText(/[A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,}/).first()).toBeVisible();

    report["Información General"] = "PASS";
  } catch (error) {
    report["Información General"] = `FAIL: ${shortError(error)}`;
    notes.push("Información General validation failed.");
  }

  // Step 6: Validate Detalles de la Cuenta.
  try {
    expect(administrarOk, "Cannot validate account sections without account page").toBeTruthy();
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();

    report["Detalles de la Cuenta"] = "PASS";
  } catch (error) {
    report["Detalles de la Cuenta"] = `FAIL: ${shortError(error)}`;
    notes.push("Detalles de la Cuenta validation failed.");
  }

  // Step 7: Validate Tus Negocios.
  try {
    expect(administrarOk, "Cannot validate account sections without account page").toBeTruthy();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    report["Tus Negocios"] = "PASS";
  } catch (error) {
    report["Tus Negocios"] = `FAIL: ${shortError(error)}`;
    notes.push("Tus Negocios validation failed.");
  }

  // Step 8: Validate Términos y Condiciones.
  try {
    expect(administrarOk, "Cannot validate legal links without account page").toBeTruthy();
    const result = await openLegalLink({
      appPage: page,
      linkPattern: /T[eé]rminos y Condiciones/i,
      headingPattern: /T[eé]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo
    });
    legalUrls.terminosYCondiciones = result.url;
    evidence.terminosYCondiciones = result.screenshotPath;
    report["Términos y Condiciones"] = "PASS";
  } catch (error) {
    report["Términos y Condiciones"] = `FAIL: ${shortError(error)}`;
    notes.push("Términos y Condiciones validation failed.");
  }

  // Step 9: Validate Política de Privacidad.
  try {
    expect(administrarOk, "Cannot validate legal links without account page").toBeTruthy();
    const result = await openLegalLink({
      appPage: page,
      linkPattern: /Pol[ií]tica de Privacidad/i,
      headingPattern: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo
    });
    legalUrls.politicaDePrivacidad = result.url;
    evidence.politicaDePrivacidad = result.screenshotPath;
    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    report["Política de Privacidad"] = `FAIL: ${shortError(error)}`;
    notes.push("Política de Privacidad validation failed.");
  }

  // Step 10: Final Report.
  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: baseUrl,
    googleAccount: GOOGLE_ACCOUNT,
    results: report,
    legalUrls,
    evidence,
    notes
  };

  const outputReportPath = testInfo.outputPath("mi-negocio-final-report.json");
  await fs.writeFile(outputReportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  await testInfo.attach("mi-negocio-final-report", {
    path: outputReportPath,
    contentType: "application/json"
  });

  const artifactsDir = path.resolve(__dirname, "..", "artifacts");
  await fs.mkdir(artifactsDir, { recursive: true });
  const latestReportPath = path.join(artifactsDir, "mi-negocio-final-report.latest.json");
  await fs.writeFile(latestReportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");

  const failedSteps = Object.entries(report).filter(([, status]) => !status.startsWith("PASS"));
  expect(
    failedSteps,
    `One or more workflow validations failed: ${failedSteps
      .map(([name, status]) => `${name} => ${status}`)
      .join(" | ")}`
  ).toHaveLength(0);
});
