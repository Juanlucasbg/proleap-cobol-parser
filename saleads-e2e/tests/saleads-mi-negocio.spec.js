const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const WORKFLOW_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const STEP_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad",
];

function createReport(appUrl) {
  return {
    name: WORKFLOW_NAME,
    executedAt: new Date().toISOString(),
    environment: {
      saleadsUrl: appUrl || null,
    },
    results: Object.fromEntries(STEP_FIELDS.map((field) => [field, "FAIL"])),
    stepDetails: {},
    evidence: {
      screenshots: [],
      urls: {},
    },
  };
}

function markStep(report, field, status, details) {
  report.results[field] = status;
  report.stepDetails[field] = details || {};
}

function escRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function firstVisibleLocator(candidates, timeoutMs = 15000) {
  const perCandidateTimeout = Math.max(1000, Math.floor(timeoutMs / candidates.length));
  for (const candidate of candidates) {
    const locator = candidate.first();
    try {
      await locator.waitFor({ state: "visible", timeout: perCandidateTimeout });
      return locator;
    } catch {
      // try next candidate
    }
  }

  throw new Error("No visible locator matched the candidate selectors.");
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiLoad(page);
}

async function takeCheckpointScreenshot(page, screenshotDir, report, name, fullPage = false) {
  const filename = `${Date.now()}-${name}.png`;
  const absolutePath = path.join(screenshotDir, filename);
  await page.screenshot({ path: absolutePath, fullPage });

  report.evidence.screenshots.push({
    name,
    path: absolutePath,
  });
}

async function selectGoogleAccountIfPrompted(loginSurface, googleEmail) {
  const accountMatcher = new RegExp(escRegex(googleEmail), "i");
  const accountLocators = [
    loginSurface.getByText(accountMatcher),
    loginSurface.getByRole("button", { name: accountMatcher }),
    loginSurface.locator(`[data-identifier="${googleEmail}"]`),
  ];

  try {
    const accountOption = await firstVisibleLocator(accountLocators, 12000);
    await accountOption.click();
    await waitForUiLoad(loginSurface);
    return true;
  } catch {
    const currentUrl = loginSurface.url();
    if (/accounts\.google\.com/i.test(currentUrl)) {
      throw new Error(
        `Google account selector appeared but account ${googleEmail} was not found/visible.`,
      );
    }
    return false;
  }
}

function findLikelyUserName(sectionText) {
  const rejected = [
    /informaci(?:o|\u00f3)n general/i,
    /business plan/i,
    /cambiar plan/i,
    /cuenta creada/i,
    /estado activo/i,
    /idioma seleccionado/i,
    /@/,
  ];

  return sectionText
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 2)
    .find((line) => {
      if (rejected.some((rule) => rule.test(line))) {
        return false;
      }
      return /[A-Za-z]{3,}/.test(line);
    });
}

async function validateLegalLink({
  page,
  context,
  appUrl,
  linkTextRegex,
  headingRegex,
  report,
  reportField,
  screenshotDir,
  screenshotName,
  evidenceUrlKey,
}) {
  const link = await firstVisibleLocator(
    [
      page.getByRole("link", { name: linkTextRegex }),
      page.getByText(linkTextRegex),
      page.getByRole("button", { name: linkTextRegex }),
    ],
    20000,
  );

  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await link.click();
  await waitForUiLoad(page);

  const maybeNewTab = await popupPromise;
  const targetPage = maybeNewTab || page;
  await waitForUiLoad(targetPage);

  await expect(
    firstVisibleLocator(
      [targetPage.getByRole("heading", { name: headingRegex }), targetPage.getByText(headingRegex)],
      25000,
    ),
  ).resolves.toBeTruthy();

  const legalBodyText = (await targetPage.locator("body").innerText()).trim();
  if (legalBodyText.length < 120) {
    throw new Error("Legal page content appears too short.");
  }

  await takeCheckpointScreenshot(targetPage, screenshotDir, report, screenshotName, true);
  report.evidence.urls[evidenceUrlKey] = targetPage.url();
  markStep(report, reportField, "PASS", { finalUrl: targetPage.url() });

  if (maybeNewTab) {
    await maybeNewTab.close();
    await page.bringToFront();
    await waitForUiLoad(page);
    return;
  }

  await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
    if (appUrl) {
      await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    }
  });
  await waitForUiLoad(page);
}

test.describe(WORKFLOW_NAME, () => {
  test("Login to SaleADS with Google and validate Mi Negocio workflow", async ({
    page,
    context,
  }, testInfo) => {
    const appUrl = process.env.SALEADS_URL || process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
    const googleEmail = process.env.SALEADS_GOOGLE_EMAIL || DEFAULT_GOOGLE_EMAIL;

    const artifactsRoot = path.join(__dirname, "..", "artifacts");
    const screenshotDir = path.join(artifactsRoot, "screenshots");
    fs.mkdirSync(screenshotDir, { recursive: true });

    const report = createReport(appUrl);
    const failures = [];

    try {
      if (!appUrl) {
        throw new Error(
          "Missing SALEADS_URL (or SALEADS_LOGIN_URL / BASE_URL). A runtime environment URL is required.",
        );
      }

      await page.goto(appUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    } catch (error) {
      const reason = `Cannot start test run: ${error.message}`;
      STEP_FIELDS.forEach((field) => markStep(report, field, "FAIL", { reason }));
      failures.push(reason);

      const failedReportPath = path.join(artifactsRoot, "saleads-mi-negocio-final-report.json");
      fs.mkdirSync(artifactsRoot, { recursive: true });
      fs.writeFileSync(failedReportPath, JSON.stringify(report, null, 2));
      testInfo.attach("final-report", {
        path: failedReportPath,
        contentType: "application/json",
      });
      throw new Error(reason);
    }

    // 1) Login with Google and validate dashboard/sidebar
    try {
      const loginButton = await firstVisibleLocator(
        [
          page.getByRole("button", {
            name: /sign in with google|login with google|continue with google|iniciar sesi[o|ó]n con google|continuar con google|google/i,
          }),
          page.getByRole("link", {
            name: /sign in with google|login with google|continue with google|iniciar sesi[o|ó]n con google|continuar con google|google/i,
          }),
          page.getByText(
            /sign in with google|login with google|continue with google|iniciar sesi[o|ó]n con google|continuar con google|google/i,
          ),
        ],
        25000,
      );

      const popupPromise = page.waitForEvent("popup", { timeout: 12000 }).catch(() => null);
      await clickAndWait(loginButton, page);

      const googlePopup = await popupPromise;
      if (googlePopup) {
        await waitForUiLoad(googlePopup);
        await selectGoogleAccountIfPrompted(googlePopup, googleEmail);
        await googlePopup.waitForEvent("close", { timeout: 120000 }).catch(() => {});
      } else {
        await selectGoogleAccountIfPrompted(page, googleEmail);
      }

      await waitForUiLoad(page);
      const sidebar = page.locator("aside, nav").first();
      await expect(sidebar).toBeVisible({ timeout: 120000 });
      await expect(page.getByText(/mi negocio|negocio/i).first()).toBeVisible({ timeout: 120000 });

      await takeCheckpointScreenshot(page, screenshotDir, report, "01-dashboard-loaded");
      markStep(report, "Login", "PASS", {
        message: "Main interface and left sidebar are visible.",
      });
    } catch (error) {
      failures.push(`Login: ${error.message}`);
      markStep(report, "Login", "FAIL", { reason: error.message });
    }

    // 2) Open Mi Negocio menu and validate expanded options
    try {
      const sidebar = page.locator("aside, nav").first();
      const negocioHeader = await firstVisibleLocator(
        [sidebar.getByText(/^Negocio$/i), sidebar.getByRole("button", { name: /^Negocio$/i }), sidebar.getByText(/Negocio/i)],
        15000,
      );
      await clickAndWait(negocioHeader, page);

      const miNegocio = await firstVisibleLocator(
        [
          sidebar.getByRole("button", { name: /Mi Negocio/i }),
          sidebar.getByRole("link", { name: /Mi Negocio/i }),
          sidebar.getByText(/^Mi Negocio$/i),
          page.getByText(/^Mi Negocio$/i),
        ],
        20000,
      );
      await clickAndWait(miNegocio, page);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

      await takeCheckpointScreenshot(page, screenshotDir, report, "02-mi-negocio-expanded");
      markStep(report, "Mi Negocio menu", "PASS", {
        message: "Submenu expanded and required options are visible.",
      });
    } catch (error) {
      failures.push(`Mi Negocio menu: ${error.message}`);
      markStep(report, "Mi Negocio menu", "FAIL", { reason: error.message });
    }

    // 3) Validate Agregar Negocio modal
    try {
      const agregarNegocioOption = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByRole("link", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i),
        ],
        20000,
      );
      await clickAndWait(agregarNegocioOption, page);

      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();

      const nombreInput = await firstVisibleLocator(
        [
          page.getByLabel(/Nombre del Negocio/i),
          page.getByPlaceholder(/Nombre del Negocio/i),
          page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
          page.locator("input[name*='negocio' i]"),
        ],
        15000,
      );
      await expect(nombreInput).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      await takeCheckpointScreenshot(page, screenshotDir, report, "03-crear-nuevo-negocio-modal");

      await nombreInput.fill("Negocio Prueba Automatizacion");
      await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }), page);

      markStep(report, "Agregar Negocio modal", "PASS", {
        message: "Modal and all required controls validated successfully.",
      });
    } catch (error) {
      failures.push(`Agregar Negocio modal: ${error.message}`);
      markStep(report, "Agregar Negocio modal", "FAIL", { reason: error.message });
    }

    // 4) Open Administrar Negocios and validate account sections
    try {
      const administrarNegociosOption = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /Administrar Negocios/i }),
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i),
        ],
        20000,
      );

      await clickAndWait(administrarNegociosOption, page);
      await expect(page.getByText(/Informaci(?:o|\u00f3)n General/i).first()).toBeVisible({
        timeout: 90000,
      });
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Secci[o|ó]n Legal/i).first()).toBeVisible();

      await takeCheckpointScreenshot(page, screenshotDir, report, "04-administrar-negocios-page", true);
      markStep(report, "Administrar Negocios view", "PASS", {
        message: "All required account page sections are visible.",
      });
    } catch (error) {
      failures.push(`Administrar Negocios view: ${error.message}`);
      markStep(report, "Administrar Negocios view", "FAIL", { reason: error.message });
    }

    // 5) Validate Informacion General
    try {
      const infoHeading = page.getByText(/Informaci(?:o|\u00f3)n General/i).first();
      await expect(infoHeading).toBeVisible();
      const infoSection = infoHeading.locator("xpath=ancestor::*[self::section or self::div][1]");
      const infoText = await infoSection.innerText();

      const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
      if (!emailRegex.test(infoText)) {
        throw new Error("User email is not visible in Informacion General.");
      }

      const likelyName = findLikelyUserName(infoText);
      if (!likelyName) {
        throw new Error("User name is not clearly visible in Informacion General.");
      }

      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

      markStep(report, "Informaci\u00f3n General", "PASS", {
        likelyUserName: likelyName,
      });
    } catch (error) {
      failures.push(`Informaci\u00f3n General: ${error.message}`);
      markStep(report, "Informaci\u00f3n General", "FAIL", { reason: error.message });
    }

    // 6) Validate Detalles de la Cuenta
    try {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();

      markStep(report, "Detalles de la Cuenta", "PASS", {
        message: "Account detail labels are visible.",
      });
    } catch (error) {
      failures.push(`Detalles de la Cuenta: ${error.message}`);
      markStep(report, "Detalles de la Cuenta", "FAIL", { reason: error.message });
    }

    // 7) Validate Tus Negocios
    try {
      const tusNegociosHeading = page.getByText(/Tus Negocios/i).first();
      await expect(tusNegociosHeading).toBeVisible();
      const tusNegociosSection = tusNegociosHeading.locator("xpath=ancestor::*[self::section or self::div][1]");

      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

      const itemCount = await tusNegociosSection
        .locator("li, tbody tr, [role='row'], article, [class*='business' i], [class*='negocio' i]")
        .count();
      if (itemCount < 1) {
        throw new Error("Business list was not detected in Tus Negocios section.");
      }

      markStep(report, "Tus Negocios", "PASS", {
        detectedBusinessItemCount: itemCount,
      });
    } catch (error) {
      failures.push(`Tus Negocios: ${error.message}`);
      markStep(report, "Tus Negocios", "FAIL", { reason: error.message });
    }

    // 8) Validate Terminos y Condiciones
    try {
      await validateLegalLink({
        page,
        context,
        appUrl,
        linkTextRegex: /T(?:e|\u00e9)rminos y Condiciones/i,
        headingRegex: /T(?:e|\u00e9)rminos y Condiciones/i,
        report,
        reportField: "T\u00e9rminos y Condiciones",
        screenshotDir,
        screenshotName: "08-terminos-y-condiciones",
        evidenceUrlKey: "terminosYCondicionesFinalUrl",
      });
    } catch (error) {
      failures.push(`T\u00e9rminos y Condiciones: ${error.message}`);
      markStep(report, "T\u00e9rminos y Condiciones", "FAIL", { reason: error.message });
    }

    // 9) Validate Politica de Privacidad
    try {
      await validateLegalLink({
        page,
        context,
        appUrl,
        linkTextRegex: /Pol(?:i|\u00ed)tica de Privacidad/i,
        headingRegex: /Pol(?:i|\u00ed)tica de Privacidad/i,
        report,
        reportField: "Pol\u00edtica de Privacidad",
        screenshotDir,
        screenshotName: "09-politica-de-privacidad",
        evidenceUrlKey: "politicaDePrivacidadFinalUrl",
      });
    } catch (error) {
      failures.push(`Pol\u00edtica de Privacidad: ${error.message}`);
      markStep(report, "Pol\u00edtica de Privacidad", "FAIL", { reason: error.message });
    }

    // 10) Final report
    const finalReportPath = path.join(artifactsRoot, "saleads-mi-negocio-final-report.json");
    fs.mkdirSync(artifactsRoot, { recursive: true });
    fs.writeFileSync(finalReportPath, JSON.stringify(report, null, 2));
    testInfo.attach("final-report", {
      path: finalReportPath,
      contentType: "application/json",
    });

    if (failures.length > 0) {
      throw new Error(`Workflow validations failed:\n- ${failures.join("\n- ")}`);
    }
  });
});
