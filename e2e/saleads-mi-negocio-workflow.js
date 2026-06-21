#!/usr/bin/env node

const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || "";
const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const HEADLESS = process.env.SALEADS_HEADLESS !== "false";
const ARTIFACTS_DIR =
  process.env.SALEADS_ARTIFACTS_DIR ||
  path.join(process.cwd(), "artifacts", "saleads-mi-negocio");
const WAIT_AFTER_CLICK_MS = Number(process.env.SALEADS_WAIT_AFTER_CLICK_MS || "1200");

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

function nowIso() {
  return new Date().toISOString();
}

function normalizeName(name) {
  return name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

function buildReportTemplate() {
  const results = {};
  for (const field of REPORT_FIELDS) {
    results[field] = {
      status: "NOT_RUN",
      details: [],
    };
  }

  return {
    executionStartedAt: nowIso(),
    loginUrl: LOGIN_URL || null,
    artifactsDir: ARTIFACTS_DIR,
    legalUrls: {
      terminosYCondiciones: null,
      politicaDePrivacidad: null,
    },
    results,
    overallStatus: "FAIL",
  };
}

function addDetail(report, field, message) {
  report.results[field].details.push(message);
}

function setStepStatus(report, field, pass) {
  report.results[field].status = pass ? "PASS" : "FAIL";
}

function summarizeValidation(validations) {
  return validations
    .map((item) => `${item.passed ? "PASS" : "FAIL"} - ${item.label}`)
    .join("; ");
}

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
}

async function waitForUiLoad(page) {
  try {
    await page.waitForLoadState("domcontentloaded", { timeout: 15000 });
  } catch (_err) {
    // No-op: this accommodates SPAs where no navigation happens.
  }

  try {
    await page.waitForLoadState("networkidle", { timeout: 7000 });
  } catch (_err) {
    // No-op: some apps keep background polling.
  }

  await page.waitForTimeout(800);
}

async function waitAfterClick(page) {
  await waitForUiLoad(page);
  await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
}

async function saveScreenshot(page, name, fullPage = false) {
  const filePath = path.join(ARTIFACTS_DIR, `${name}.png`);
  await page.screenshot({
    path: filePath,
    fullPage,
  });
  return filePath;
}

async function resolveVisibleLocator(candidates, timeoutMs = 3500) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    try {
      await locator.waitFor({
        state: "visible",
        timeout: timeoutMs,
      });
      return locator;
    } catch (_err) {
      // Try next candidate.
    }
  }
  return null;
}

async function clickVisible(candidates, page, errMessage) {
  const locator = await resolveVisibleLocator(candidates);
  if (!locator) {
    throw new Error(errMessage);
  }
  await locator.click();
  await waitAfterClick(page);
  return locator;
}

async function existsVisible(locator) {
  try {
    await locator.first().waitFor({ state: "visible", timeout: 4000 });
    return true;
  } catch (_err) {
    return false;
  }
}

async function getContainerFromHeading(page, headingRegex) {
  const heading = await resolveVisibleLocator([
    page.getByRole("heading", { name: headingRegex }),
    page.getByText(headingRegex),
  ]);

  if (!heading) {
    return null;
  }

  const sectionCandidate = heading.locator("xpath=ancestor::section[1]");
  if ((await sectionCandidate.count()) > 0) {
    return sectionCandidate.first();
  }

  const articleCandidate = heading.locator("xpath=ancestor::article[1]");
  if ((await articleCandidate.count()) > 0) {
    return articleCandidate.first();
  }

  return heading.locator("xpath=ancestor::div[1]").first();
}

async function validateLegalPage({ context, appPage, linkText, headingRegex, screenshotName }) {
  const initialAppUrl = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const navigationPromise = appPage
    .waitForNavigation({
      timeout: 10000,
      waitUntil: "domcontentloaded",
    })
    .catch(() => null);

  await clickVisible(
    [
      appPage.getByRole("link", { name: new RegExp(linkText, "i") }),
      appPage.getByRole("button", { name: new RegExp(linkText, "i") }),
      appPage.getByText(new RegExp(linkText, "i")),
    ],
    appPage,
    `No se encontró el elemento legal: ${linkText}`
  );

  const popupPage = await popupPromise;
  const legalPage = popupPage || appPage;
  if (!popupPage) {
    await navigationPromise;
  }

  await waitForUiLoad(legalPage);

  const headingVisible =
    (await resolveVisibleLocator(
      [legalPage.getByRole("heading", { name: headingRegex }), legalPage.getByText(headingRegex)],
      4000
    )) !== null;
  const bodyText = await legalPage.locator("body").innerText();
  const hasLegalContent = bodyText.trim().length > 200;

  const screenshotPath = await saveScreenshot(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else if (legalPage === appPage && finalUrl !== initialAppUrl) {
    try {
      await appPage.goBack({ waitUntil: "domcontentloaded", timeout: 10000 });
      await waitForUiLoad(appPage);
    } catch (_err) {
      // No-op if browser history does not allow back navigation.
    }
  }

  return {
    headingVisible,
    hasLegalContent,
    screenshotPath,
    finalUrl,
  };
}

async function run() {
  const report = buildReportTemplate();

  if (!LOGIN_URL) {
    throw new Error(
      "SALEADS_LOGIN_URL (or SALEADS_URL) is required. The script intentionally avoids hardcoded domains."
    );
  }

  await ensureArtifactsDir();

  const browser = await chromium.launch({
    headless: HEADLESS,
  });

  const context = await browser.newContext({
    viewport: { width: 1440, height: 960 },
  });
  const page = await context.newPage();

  try {
    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);

    // Step 1: Login with Google
    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|acceder con google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|acceder con google/i,
        }),
        page.getByText(/google/i),
      ],
      page,
      "No se encontró el botón de inicio de sesión con Google."
    );

    const googlePage = await popupPromise;
    if (googlePage) {
      await waitForUiLoad(googlePage);
      const accountPicker = await resolveVisibleLocator(
        [
          googlePage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
          googlePage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        ],
        6000
      );
      if (accountPicker) {
        await accountPicker.click();
        await waitForUiLoad(googlePage);
      }
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      const inlineAccount = await resolveVisibleLocator(
        [
          page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
          page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        ],
        6000
      );
      if (inlineAccount) {
        await inlineAccount.click();
        await waitAfterClick(page);
      }
    }

    const sidebarVisible =
      (await existsVisible(page.locator("aside"))) ||
      (await existsVisible(page.getByRole("navigation")));
    const appLoaded = !(await existsVisible(
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|acceder con google/i,
      })
    ));

    const loginValidations = [
      { label: "Main application interface appears", passed: appLoaded },
      { label: "Left sidebar navigation is visible", passed: sidebarVisible },
    ];

    addDetail(report, "Login", summarizeValidation(loginValidations));
    setStepStatus(
      report,
      "Login",
      loginValidations.every((item) => item.passed)
    );
    addDetail(report, "Login", `Screenshot: ${await saveScreenshot(page, "step-1-dashboard")}`);

    // Step 2: Open Mi Negocio menu
    await clickVisible(
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/^mi negocio$/i),
        page.getByText(/^negocio$/i),
      ],
      page,
      "No se encontró el menú 'Mi Negocio' en el sidebar."
    );

    const agregarMenuVisible =
      (await resolveVisibleLocator(
        [page.getByRole("menuitem", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
        3000
      )) !== null;
    const administrarMenuVisible =
      (await resolveVisibleLocator(
        [
          page.getByRole("menuitem", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ],
        3000
      )) !== null;
    const submenuExpanded = agregarMenuVisible && administrarMenuVisible;

    const step2Validations = [
      { label: "Mi Negocio submenu expands", passed: submenuExpanded },
      { label: "Agregar Negocio is visible", passed: agregarMenuVisible },
      { label: "Administrar Negocios is visible", passed: administrarMenuVisible },
    ];
    addDetail(report, "Mi Negocio menu", summarizeValidation(step2Validations));
    setStepStatus(
      report,
      "Mi Negocio menu",
      step2Validations.every((item) => item.passed)
    );
    addDetail(
      report,
      "Mi Negocio menu",
      `Screenshot: ${await saveScreenshot(page, "step-2-mi-negocio-expanded")}`
    );

    // Step 3: Validate Agregar Negocio modal
    await clickVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ],
      page,
      "No se encontró la opción 'Agregar Negocio'."
    );

    const modalTitleVisible = await existsVisible(page.getByText(/crear nuevo negocio/i));
    const nombreFieldVisible =
      (await existsVisible(page.getByLabel(/nombre del negocio/i))) ||
      (await existsVisible(page.getByPlaceholder(/nombre del negocio/i)));
    const quotaVisible = await existsVisible(page.getByText(/tienes 2 de 3 negocios/i));
    const cancelarVisible =
      (await resolveVisibleLocator(
        [page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)],
        2500
      )) !== null;
    const crearVisible =
      (await resolveVisibleLocator(
        [page.getByRole("button", { name: /crear negocio/i }), page.getByText(/crear negocio/i)],
        2500
      )) !== null;

    const step3Validations = [
      { label: "Modal title 'Crear Nuevo Negocio' visible", passed: modalTitleVisible },
      { label: "Input field 'Nombre del Negocio' visible", passed: nombreFieldVisible },
      { label: "Text 'Tienes 2 de 3 negocios' visible", passed: quotaVisible },
      { label: "Buttons 'Cancelar' and 'Crear Negocio' present", passed: cancelarVisible && crearVisible },
    ];
    const step3ScreenshotPath = await saveScreenshot(page, "step-3-agregar-negocio-modal");

    if (nombreFieldVisible) {
      const inputLocator = (await existsVisible(page.getByLabel(/nombre del negocio/i)))
        ? page.getByLabel(/nombre del negocio/i)
        : page.getByPlaceholder(/nombre del negocio/i);
      await inputLocator.fill("Negocio Prueba Automatización");
      await waitForUiLoad(page);
    }
    if (cancelarVisible) {
      await clickVisible(
        [page.getByRole("button", { name: /^cancelar$/i }), page.getByText(/^cancelar$/i)],
        page,
        "No se encontró el botón 'Cancelar' del modal."
      );
    }

    addDetail(report, "Agregar Negocio modal", summarizeValidation(step3Validations));
    setStepStatus(
      report,
      "Agregar Negocio modal",
      step3Validations.every((item) => item.passed)
    );
    addDetail(report, "Agregar Negocio modal", `Screenshot: ${step3ScreenshotPath}`);

    // Step 4: Open Administrar Negocios
    const administrarBeforeClickVisible = await existsVisible(page.getByText(/administrar negocios/i));
    if (!administrarBeforeClickVisible) {
      await clickVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/^mi negocio$/i),
        ],
        page,
        "No se pudo expandir nuevamente 'Mi Negocio'."
      );
    }

    await clickVisible(
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ],
      page,
      "No se encontró la opción 'Administrar Negocios'."
    );

    const infoGeneralVisible = await existsVisible(page.getByText(/informaci[oó]n general/i));
    const detallesCuentaVisible = await existsVisible(page.getByText(/detalles de la cuenta/i));
    const tusNegociosVisible = await existsVisible(page.getByText(/tus negocios/i));
    const legalSectionVisible =
      (await existsVisible(page.getByText(/secci[oó]n legal/i))) ||
      (await existsVisible(page.getByText(/t[eé]rminos y condiciones/i)));

    const step4Validations = [
      { label: "Section 'Información General' exists", passed: infoGeneralVisible },
      { label: "Section 'Detalles de la Cuenta' exists", passed: detallesCuentaVisible },
      { label: "Section 'Tus Negocios' exists", passed: tusNegociosVisible },
      { label: "Section 'Sección Legal' exists", passed: legalSectionVisible },
    ];

    addDetail(report, "Administrar Negocios view", summarizeValidation(step4Validations));
    setStepStatus(
      report,
      "Administrar Negocios view",
      step4Validations.every((item) => item.passed)
    );
    addDetail(
      report,
      "Administrar Negocios view",
      `Screenshot: ${await saveScreenshot(page, "step-4-administrar-negocios", true)}`
    );

    // Step 5: Validate Información General
    const infoContainer = await getContainerFromHeading(page, /informaci[oó]n general/i);
    const infoText = infoContainer ? await infoContainer.innerText() : await page.locator("body").innerText();
    const hasUserEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoText);
    const hasBusinessPlan = /business plan/i.test(infoText);
    const hasCambiarPlanButton =
      (await resolveVisibleLocator(
        [page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)],
        3000
      )) !== null;

    const textWithoutEmail = infoText.replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, " ");
    const hasUserName = /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(textWithoutEmail);

    const step5Validations = [
      { label: "User name is visible", passed: hasUserName },
      { label: "User email is visible", passed: hasUserEmail },
      { label: "Text 'BUSINESS PLAN' is visible", passed: hasBusinessPlan },
      { label: "Button 'Cambiar Plan' is visible", passed: hasCambiarPlanButton },
    ];
    addDetail(report, "Información General", summarizeValidation(step5Validations));
    setStepStatus(
      report,
      "Información General",
      step5Validations.every((item) => item.passed)
    );

    // Step 6: Validate Detalles de la Cuenta
    const detallesContainer = await getContainerFromHeading(page, /detalles de la cuenta/i);
    const detallesText = detallesContainer
      ? await detallesContainer.innerText()
      : await page.locator("body").innerText();

    const step6Validations = [
      { label: "'Cuenta creada' is visible", passed: /cuenta creada/i.test(detallesText) },
      { label: "'Estado activo' is visible", passed: /estado activo/i.test(detallesText) },
      { label: "'Idioma seleccionado' is visible", passed: /idioma seleccionado/i.test(detallesText) },
    ];
    addDetail(report, "Detalles de la Cuenta", summarizeValidation(step6Validations));
    setStepStatus(
      report,
      "Detalles de la Cuenta",
      step6Validations.every((item) => item.passed)
    );

    // Step 7: Validate Tus Negocios
    const negociosContainer = await getContainerFromHeading(page, /tus negocios/i);
    const negociosText = negociosContainer
      ? await negociosContainer.innerText()
      : await page.locator("body").innerText();

    const hasAddBusinessButton =
      (await resolveVisibleLocator(
        [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
        3000
      )) !== null;

    const step7Validations = [
      { label: "Business list is visible", passed: /tus negocios|negocio/i.test(negociosText) },
      { label: "Button 'Agregar Negocio' exists", passed: hasAddBusinessButton },
      { label: "Text 'Tienes 2 de 3 negocios' is visible", passed: /tienes 2 de 3 negocios/i.test(negociosText) },
    ];
    addDetail(report, "Tus Negocios", summarizeValidation(step7Validations));
    setStepStatus(
      report,
      "Tus Negocios",
      step7Validations.every((item) => item.passed)
    );

    // Step 8: Validate Términos y Condiciones
    const terminosResult = await validateLegalPage({
      context,
      appPage: page,
      linkText: "Términos y Condiciones",
      headingRegex: /t[eé]rminos y condiciones/i,
      screenshotName: "step-8-terminos-condiciones",
    });
    report.legalUrls.terminosYCondiciones = terminosResult.finalUrl;

    const step8Validations = [
      { label: "Heading 'Términos y Condiciones' visible", passed: terminosResult.headingVisible },
      { label: "Legal content text is visible", passed: terminosResult.hasLegalContent },
    ];
    addDetail(report, "Términos y Condiciones", summarizeValidation(step8Validations));
    addDetail(report, "Términos y Condiciones", `Screenshot: ${terminosResult.screenshotPath}`);
    addDetail(report, "Términos y Condiciones", `Final URL: ${terminosResult.finalUrl}`);
    setStepStatus(
      report,
      "Términos y Condiciones",
      step8Validations.every((item) => item.passed)
    );

    // Step 9: Validate Política de Privacidad
    const politicaResult = await validateLegalPage({
      context,
      appPage: page,
      linkText: "Política de Privacidad",
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: "step-9-politica-privacidad",
    });
    report.legalUrls.politicaDePrivacidad = politicaResult.finalUrl;

    const step9Validations = [
      { label: "Heading 'Política de Privacidad' visible", passed: politicaResult.headingVisible },
      { label: "Legal content text is visible", passed: politicaResult.hasLegalContent },
    ];
    addDetail(report, "Política de Privacidad", summarizeValidation(step9Validations));
    addDetail(report, "Política de Privacidad", `Screenshot: ${politicaResult.screenshotPath}`);
    addDetail(report, "Política de Privacidad", `Final URL: ${politicaResult.finalUrl}`);
    setStepStatus(
      report,
      "Política de Privacidad",
      step9Validations.every((item) => item.passed)
    );

    const allPass = REPORT_FIELDS.every((field) => report.results[field].status === "PASS");
    report.overallStatus = allPass ? "PASS" : "FAIL";
    report.executionFinishedAt = nowIso();

    const reportPath = path.join(ARTIFACTS_DIR, `report-${normalizeName(report.executionFinishedAt)}.json`);
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

    console.log(JSON.stringify(report, null, 2));
    console.log(`Report written to ${reportPath}`);
  } finally {
    await context.close();
    await browser.close();
  }
}

run().catch(async (error) => {
  const fallbackReport = buildReportTemplate();
  fallbackReport.executionFinishedAt = nowIso();
  fallbackReport.error = error.message;
  fallbackReport.overallStatus = "FAIL";
  for (const field of REPORT_FIELDS) {
    fallbackReport.results[field].status = "FAIL";
    fallbackReport.results[field].details.push(`Execution aborted: ${error.message}`);
  }

  await ensureArtifactsDir();
  const reportPath = path.join(ARTIFACTS_DIR, `report-error-${normalizeName(fallbackReport.executionFinishedAt)}.json`);
  await fs.writeFile(reportPath, JSON.stringify(fallbackReport, null, 2), "utf8");

  console.error(error);
  console.error(`Report written to ${reportPath}`);
  process.exitCode = 1;
});
