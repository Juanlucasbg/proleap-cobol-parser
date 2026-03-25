const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const ARTIFACTS_DIR = path.resolve(__dirname, "../test-results/saleads-mi-negocio");

const SECTION_KEYS = [
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

function normalizeText(value) {
  return (value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
}

async function captureCheckpoint(page, name, fullPage = false) {
  await ensureArtifactsDir();
  const screenshotPath = path.join(ARTIFACTS_DIR, `${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  test.info().attach(name, {
    path: screenshotPath,
    contentType: "image/png"
  });
}

async function waitForUiToStabilize(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
}

function reportPass(report, section) {
  report[section] = "PASS";
}

function reportFail(report, section, error) {
  report[section] = `FAIL: ${error && error.message ? error.message : String(error)}`;
}

async function clickFirstVisible(page, labels, contextDescription) {
  for (const label of labels) {
    const byRole = page.getByRole("button", { name: new RegExp(label, "i") }).first();
    if (await byRole.isVisible().catch(() => false)) {
      await byRole.click();
      await waitForUiToStabilize(page);
      return;
    }

    const byText = page.getByText(new RegExp(label, "i")).first();
    if (await byText.isVisible().catch(() => false)) {
      await byText.click();
      await waitForUiToStabilize(page);
      return;
    }
  }

  throw new Error(`Could not click ${contextDescription} using labels: ${labels.join(", ")}`);
}

async function verifyVisibleText(page, text) {
  const locator = page.getByText(new RegExp(text, "i")).first();
  await expect(locator).toBeVisible({ timeout: 15000 });
}

async function verifyNormalizedTextInBody(page, text) {
  const bodyText = normalizeText(await page.locator("body").innerText());
  const expectedText = normalizeText(text);
  if (!bodyText.includes(expectedText)) {
    throw new Error(`Expected text "${text}" was not found in page body.`);
  }
}

async function maybeChooseGoogleAccount(page) {
  const accountEmail = process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
  const accountLocator = page.getByText(accountEmail, { exact: false }).first();
  if (await accountLocator.isVisible().catch(() => false)) {
    await accountLocator.click();
    await waitForUiToStabilize(page);
  }
}

async function assertUserNameVisibleNearEmail(page) {
  const bodyText = await page.locator("body").innerText();
  const lines = bodyText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  const emailPattern = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
  const emailLineIndex = lines.findIndex((line) => emailPattern.test(line));
  if (emailLineIndex === -1) {
    throw new Error("User email was not found in Información General section.");
  }

  const ignoredLinePatterns = [
    /business plan/i,
    /cambiar plan/i,
    /informacion general|información general/i,
    /detalles de la cuenta/i,
    /tus negocios/i
  ];

  const candidateWindow = lines.slice(Math.max(0, emailLineIndex - 3), Math.min(lines.length, emailLineIndex + 4));
  const nameLikePattern = /^[A-Za-zÀ-ÿ'`.-]+(?:\s+[A-Za-zÀ-ÿ'`.-]+){1,3}$/;
  const hasNameLikeLine = candidateWindow.some((line) => {
    if (emailPattern.test(line)) {
      return false;
    }
    if (ignoredLinePatterns.some((pattern) => pattern.test(line))) {
      return false;
    }
    return nameLikePattern.test(line);
  });

  if (!hasNameLikeLine) {
    throw new Error("User name was not found near the user email.");
  }
}

async function openSidebarMiNegocio(page) {
  const sidebar = page.locator("aside, nav").first();
  await expect(sidebar).toBeVisible({ timeout: 30000 });

  await clickFirstVisible(page, ["Negocio"], "Negocio section");
  await clickFirstVisible(page, ["Mi Negocio"], "Mi Negocio option");
  await waitForUiToStabilize(page);

  await verifyVisibleText(page, "Agregar Negocio");
  await verifyVisibleText(page, "Administrar Negocios");
}

async function ensureMiNegocioExpanded(page) {
  const agregar = page.getByText(/Agregar Negocio/i).first();
  const admin = page.getByText(/Administrar Negocios/i).first();

  if (!(await agregar.isVisible().catch(() => false)) || !(await admin.isVisible().catch(() => false))) {
    await clickFirstVisible(page, ["Mi Negocio"], "Mi Negocio option");
    await waitForUiToStabilize(page);
  }

  await expect(agregar).toBeVisible({ timeout: 10000 });
  await expect(admin).toBeVisible({ timeout: 10000 });
}

async function validateLegalLink(page, appPage, label, headingRegex, reportKey, urlStore, screenshotName) {
  const appUrlBeforeNavigation = appPage.url();
  const link = page.getByRole("link", { name: new RegExp(label, "i") }).first();
  const linkVisible = await link.isVisible().catch(() => false);
  if (!linkVisible) {
    await clickFirstVisible(page, [label], `${label} link fallback`);
  }

  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);

  if (linkVisible) {
    await link.click();
    await waitForUiToStabilize(page);
  }

  const popup = await popupPromise;
  const targetPage = popup || page;

  await targetPage.waitForLoadState("domcontentloaded");
  await expect(targetPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({ timeout: 20000 });

  // Validate that legal text exists beyond the heading.
  const legalContent = targetPage.locator("main, article, body");
  const legalText = normalizeText(await legalContent.innerText());
  if (legalText.length < 100) {
    throw new Error(`Legal content for ${label} seems too short.`);
  }

  await captureCheckpoint(targetPage, screenshotName, true);
  urlStore[reportKey] = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else if (targetPage !== appPage || appPage.url() !== appUrlBeforeNavigation) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.goto(appUrlBeforeNavigation, { waitUntil: "domcontentloaded" });
    });
    await waitForUiToStabilize(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  test.setTimeout(240000);
  await ensureArtifactsDir();

  const report = {};
  const legalUrls = {};

  for (const key of SECTION_KEYS) {
    report[key] = "NOT RUN";
  }

  let firstError = null;

  try {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToStabilize(page);
    }

    // Step 1: Login with Google.
    const googlePopupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
    await clickFirstVisible(
      page,
      ["Sign in with Google", "Login with Google", "Continuar con Google", "Iniciar sesion con Google", "Iniciar sesión con Google"],
      "Google login button"
    );

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      await maybeChooseGoogleAccount(googlePopup);
      await googlePopup.waitForTimeout(1000);
      if (!googlePopup.isClosed()) {
        await googlePopup.close().catch(() => {});
      }
      await page.bringToFront();
    } else {
      await maybeChooseGoogleAccount(page);
    }

    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 60000 });
    await waitForUiToStabilize(page);
    await captureCheckpoint(page, "01-dashboard-loaded", true);
    reportPass(report, "Login");
  } catch (error) {
    reportFail(report, "Login", error);
    firstError = firstError || error;
  }

  if (report["Login"] === "PASS") {
    try {
      // Step 2: Open Mi Negocio menu.
      await openSidebarMiNegocio(page);
      await captureCheckpoint(page, "02-mi-negocio-menu-expanded", true);
      reportPass(report, "Mi Negocio menu");
    } catch (error) {
      reportFail(report, "Mi Negocio menu", error);
      firstError = firstError || error;
    }
  }

  if (report["Mi Negocio menu"] === "PASS") {
    try {
      // Step 3: Validate Agregar Negocio modal.
      await clickFirstVisible(page, ["Agregar Negocio"], "Agregar Negocio option");

      await verifyVisibleText(page, "Crear Nuevo Negocio");
      await verifyVisibleText(page, "Nombre del Negocio");
      await verifyVisibleText(page, "Tienes 2 de 3 negocios");
      await verifyVisibleText(page, "Cancelar");
      await verifyVisibleText(page, "Crear Negocio");

      const negocioInput = page.getByLabel(/Nombre del Negocio/i).first();
      if (await negocioInput.isVisible().catch(() => false)) {
        await negocioInput.click();
        await negocioInput.fill("Negocio Prueba Automatizacion");
        await waitForUiToStabilize(page);
      }

      await captureCheckpoint(page, "03-agregar-negocio-modal", true);
      await clickFirstVisible(page, ["Cancelar"], "Cancelar button");
      reportPass(report, "Agregar Negocio modal");
    } catch (error) {
      reportFail(report, "Agregar Negocio modal", error);
      firstError = firstError || error;
    }
  }

  if (report["Mi Negocio menu"] === "PASS") {
    try {
      // Step 4: Open Administrar Negocios.
      await ensureMiNegocioExpanded(page);
      await clickFirstVisible(page, ["Administrar Negocios"], "Administrar Negocios option");

      await verifyNormalizedTextInBody(page, "Información General");
      await verifyNormalizedTextInBody(page, "Detalles de la Cuenta");
      await verifyNormalizedTextInBody(page, "Tus Negocios");
      await verifyNormalizedTextInBody(page, "Sección Legal");
      await captureCheckpoint(page, "04-administrar-negocios-page", true);
      reportPass(report, "Administrar Negocios view");
    } catch (error) {
      reportFail(report, "Administrar Negocios view", error);
      firstError = firstError || error;
    }
  }

  if (report["Administrar Negocios view"] === "PASS") {
    try {
      // Step 5: Validate Información General.
      await verifyVisibleText(page, "BUSINESS PLAN");
      await verifyVisibleText(page, "Cambiar Plan");
      await assertUserNameVisibleNearEmail(page);
      reportPass(report, "Información General");
    } catch (error) {
      reportFail(report, "Información General", error);
      firstError = firstError || error;
    }
  }

  if (report["Administrar Negocios view"] === "PASS") {
    try {
      // Step 6: Validate Detalles de la Cuenta.
      await verifyNormalizedTextInBody(page, "Cuenta creada");
      await verifyNormalizedTextInBody(page, "Estado activo");
      await verifyNormalizedTextInBody(page, "Idioma seleccionado");
      reportPass(report, "Detalles de la Cuenta");
    } catch (error) {
      reportFail(report, "Detalles de la Cuenta", error);
      firstError = firstError || error;
    }
  }

  if (report["Administrar Negocios view"] === "PASS") {
    try {
      // Step 7: Validate Tus Negocios.
      await verifyNormalizedTextInBody(page, "Tus Negocios");
      await verifyNormalizedTextInBody(page, "Agregar Negocio");
      await verifyNormalizedTextInBody(page, "Tienes 2 de 3 negocios");
      reportPass(report, "Tus Negocios");
    } catch (error) {
      reportFail(report, "Tus Negocios", error);
      firstError = firstError || error;
    }
  }

  if (report["Administrar Negocios view"] === "PASS") {
    try {
      // Step 8: Validate Términos y Condiciones.
      await validateLegalLink(
        page,
        page,
        "T[eé]rminos y Condiciones",
        /Terminos y Condiciones|Términos y Condiciones/i,
        "Términos y Condiciones",
        legalUrls,
        "08-terminos-y-condiciones"
      );
      reportPass(report, "Términos y Condiciones");
    } catch (error) {
      reportFail(report, "Términos y Condiciones", error);
      firstError = firstError || error;
    }
  }

  if (report["Administrar Negocios view"] === "PASS") {
    try {
      // Step 9: Validate Política de Privacidad.
      await validateLegalLink(
        page,
        page,
        "Pol[ií]tica de Privacidad",
        /Politica de Privacidad|Política de Privacidad/i,
        "Política de Privacidad",
        legalUrls,
        "09-politica-de-privacidad"
      );
      reportPass(report, "Política de Privacidad");
    } catch (error) {
      reportFail(report, "Política de Privacidad", error);
      firstError = firstError || error;
    }
  }

  // Step 10: Final report artifact.
  const finalReport = {
    generatedAt: new Date().toISOString(),
    report,
    legalUrls
  };
  const reportPath = path.join(ARTIFACTS_DIR, "final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  test.info().attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  if (firstError) {
    throw firstError;
  }
});
