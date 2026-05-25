const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

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

function createReportTemplate() {
  return REPORT_FIELDS.reduce((report, field) => {
    report[field] = "FAIL";
    return report;
  }, {});
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function locatorVisible(locator, timeoutMs = 5000) {
  try {
    return await locator.isVisible({ timeout: timeoutMs });
  } catch (_error) {
    return false;
  }
}

async function clickVisibleLocator(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function clickByVisibleText(page, texts, label) {
  for (const text of texts) {
    const exactRegex = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
    const partialRegex = new RegExp(escapeRegex(text), "i");
    const candidates = [
      page.getByRole("button", { name: exactRegex }).first(),
      page.getByRole("link", { name: exactRegex }).first(),
      page.getByRole("menuitem", { name: exactRegex }).first(),
      page.getByRole("tab", { name: exactRegex }).first(),
      page.getByText(exactRegex).first(),
      page.getByText(partialRegex).first(),
    ];

    for (const candidate of candidates) {
      if (await locatorVisible(candidate, 2000)) {
        try {
          await clickVisibleLocator(candidate, page);
          return candidate;
        } catch (_error) {
          // try the next candidate
        }
      }
    }
  }

  throw new Error(`Could not click ${label}. Tried: ${texts.join(", ")}`);
}

async function takeCheckpoint(page, testInfo, name, fullPage = false) {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function chooseGoogleAccountIfVisible(page, email) {
  const pages = page.context().pages().slice().reverse();
  const emailRegex = new RegExp(`^\\s*${escapeRegex(email)}\\s*$`, "i");

  for (const candidatePage of pages) {
    const selectors = [
      candidatePage.getByText(emailRegex).first(),
      candidatePage.getByRole("button", { name: emailRegex }).first(),
      candidatePage.getByRole("link", { name: emailRegex }).first(),
    ];

    for (const selector of selectors) {
      if (await locatorVisible(selector, 3000)) {
        await clickVisibleLocator(selector, candidatePage);
        return true;
      }
    }
  }

  return false;
}

async function openLegalDocumentAndReturn({
  page,
  linkText,
  expectedHeadingRegex,
  checkpointName,
  testInfo,
}) {
  const popupPromise = page
    .context()
    .waitForEvent("page", { timeout: 5000 })
    .catch(() => null);

  await clickByVisibleText(page, [linkText], `${linkText} link`);

  const popupPage = await popupPromise;
  const targetPage = popupPage || page;
  await waitForUi(targetPage);

  await expect(
    targetPage.getByRole("heading", { name: expectedHeadingRegex }).first(),
  ).toBeVisible({ timeout: 20000 });

  const legalContentVisible =
    (await locatorVisible(targetPage.locator("main p").first(), 5000)) ||
    (await locatorVisible(targetPage.locator("article p").first(), 5000)) ||
    (await locatorVisible(targetPage.locator("p").first(), 5000));

  expect(legalContentVisible).toBeTruthy();

  await takeCheckpoint(targetPage, testInfo, checkpointName, true);
  const finalUrl = targetPage.url();

  if (popupPage) {
    await popupPage.close();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await expect(page.getByText(/Información General/i).first()).toBeVisible({
    timeout: 20000,
  });

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.BASE_URL ||
    process.env.PLAYWRIGHT_TEST_BASE_URL;

  const report = createReportTemplate();
  const legalUrls = {
    "Términos y Condiciones": null,
    "Política de Privacidad": null,
  };
  let testError = null;

  try {
    if (!loginUrl) {
      throw new Error(
        "SALEADS_LOGIN_URL (or BASE_URL/PLAYWRIGHT_TEST_BASE_URL) is required to start from the login page without hardcoding environment URLs.",
      );
    }

    // Step 1: Login with Google
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginPopupPromise = page
      .context()
      .waitForEvent("page", { timeout: 7000 })
      .catch(() => null);

    await clickByVisibleText(
      page,
      [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Ingresar con Google",
        "Continuar con Google",
      ],
      "Google login button",
    );

    const loginPopup = await loginPopupPromise;
    if (loginPopup) {
      await waitForUi(loginPopup);
    }

    await chooseGoogleAccountIfVisible(page, GOOGLE_ACCOUNT_EMAIL);

    if (loginPopup && !loginPopup.isClosed()) {
      await loginPopup.waitForEvent("close", { timeout: 60000 }).catch(() => {});
    }

    await waitForUi(page);
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({
      timeout: 60000,
    });
    const sidebarVisible =
      (await locatorVisible(page.locator("aside").first(), 6000)) ||
      (await locatorVisible(page.getByRole("navigation").first(), 6000));
    expect(sidebarVisible).toBeTruthy();
    report.Login = "PASS";
    await takeCheckpoint(page, testInfo, "01-dashboard-loaded");

    // Step 2: Open Mi Negocio menu
    await expect(page.getByText(/^Negocio$/i).first()).toBeVisible({
      timeout: 20000,
    });
    await clickByVisibleText(page, ["Mi Negocio"], "Mi Negocio menu option");

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({
      timeout: 15000,
    });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({
      timeout: 15000,
    });
    report["Mi Negocio menu"] = "PASS";
    await takeCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");

    // Step 3: Validate Agregar Negocio modal
    await clickByVisibleText(
      page,
      ["Agregar Negocio"],
      "Agregar Negocio submenu option",
    );

    const modalTitle = page.getByText(/^Crear Nuevo Negocio$/i).first();
    await expect(modalTitle).toBeVisible({ timeout: 15000 });

    let businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
    if (!(await locatorVisible(businessNameInput, 3000))) {
      businessNameInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
    }
    if (!(await locatorVisible(businessNameInput, 3000))) {
      businessNameInput = page.locator("input").first();
    }
    await expect(businessNameInput).toBeVisible({ timeout: 10000 });

    await expect(
      page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i).first(),
    ).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first())
      .toBeVisible({
        timeout: 10000,
      });
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first())
      .toBeVisible({
        timeout: 10000,
      });

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal");
    await clickVisibleLocator(
      page.getByRole("button", { name: /Cancelar/i }).first(),
      page,
    );
    report["Agregar Negocio modal"] = "PASS";

    // Step 4: Open Administrar Negocios
    if (
      !(await locatorVisible(
        page.getByText(/^Administrar Negocios$/i).first(),
        2000,
      ))
    ) {
      await clickByVisibleText(page, ["Mi Negocio"], "Mi Negocio re-expand");
    }

    await clickByVisibleText(
      page,
      ["Administrar Negocios"],
      "Administrar Negocios option",
    );

    await expect(page.getByText(/Información General/i).first()).toBeVisible({
      timeout: 20000,
    });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({
      timeout: 20000,
    });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({
      timeout: 20000,
    });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({
      timeout: 20000,
    });
    report["Administrar Negocios view"] = "PASS";
    await takeCheckpoint(page, testInfo, "04-administrar-negocios-page", true);

    // Step 5: Validate Información General
    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    const nameCandidate = page
      .getByText(/Juan|Lucas|Barbier|Garzon/i)
      .filter({ hasNotText: /@/i })
      .first();
    if (await locatorVisible(nameCandidate, 8000)) {
      await expect(nameCandidate).toBeVisible({ timeout: 15000 });
    } else {
      await expect(page.getByText(/Nombre|Name/i).first()).toBeVisible({
        timeout: 15000,
      });
    }
    await expect(
      page.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")).first(),
    ).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({
      timeout: 15000,
    });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first())
      .toBeVisible({
        timeout: 15000,
      });
    report["Información General"] = "PASS";

    // Step 6: Validate Detalles de la Cuenta
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({
      timeout: 15000,
    });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({
      timeout: 15000,
    });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({
      timeout: 15000,
    });
    report["Detalles de la Cuenta"] = "PASS";

    // Step 7: Validate Tus Negocios
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({
      timeout: 15000,
    });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first())
      .toBeVisible({
        timeout: 15000,
      });
    await expect(
      page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i).first(),
    ).toBeVisible({ timeout: 15000 });
    report["Tus Negocios"] = "PASS";

    // Step 8: Validate Términos y Condiciones
    legalUrls["Términos y Condiciones"] = await openLegalDocumentAndReturn({
      page,
      linkText: "Términos y Condiciones",
      expectedHeadingRegex: /Términos y Condiciones/i,
      checkpointName: "08-terminos-y-condiciones",
      testInfo,
    });
    report["Términos y Condiciones"] = "PASS";

    // Step 9: Validate Política de Privacidad
    legalUrls["Política de Privacidad"] = await openLegalDocumentAndReturn({
      page,
      linkText: "Política de Privacidad",
      expectedHeadingRegex: /Política de Privacidad/i,
      checkpointName: "09-politica-de-privacidad",
      testInfo,
    });
    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    testError = error;
  } finally {
    const finalReportPath = testInfo.outputPath(
      "10-mi-negocio-final-report.json",
    );
    const payload = {
      report,
      evidence: {
        dashboardScreenshot: "01-dashboard-loaded.png",
        expandedMenuScreenshot: "02-mi-negocio-menu-expanded.png",
        modalScreenshot: "03-agregar-negocio-modal.png",
        accountPageScreenshot: "04-administrar-negocios-page.png",
        terminosScreenshot: "08-terminos-y-condiciones.png",
        politicaScreenshot: "09-politica-de-privacidad.png",
        finalUrls: legalUrls,
      },
    };

    await fs.writeFile(finalReportPath, JSON.stringify(payload, null, 2), "utf8");
    await testInfo.attach("final-report", {
      path: finalReportPath,
      contentType: "application/json",
    });
  }

  if (testError) {
    throw testError;
  }
});
