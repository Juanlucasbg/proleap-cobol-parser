#!/usr/bin/env node

/* eslint-disable no-console */

const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const REPORT_FIELDS = [
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

const TEXT_PATTERNS = {
  signInWithGoogle: /^(sign in with google|continue with google|iniciar sesi[oó]n con google|continuar con google)$/i,
  miNegocio: /^mi negocio$/i,
  negocioSection: /^negocio$/i,
  agregarNegocio: /^agregar negocio$/i,
  administrarNegocios: /^administrar negocios$/i,
  crearNuevoNegocio: /^crear nuevo negocio$/i,
  nombreDelNegocio: /^nombre del negocio$/i,
  quota: /^tienes 2 de 3 negocios$/i,
  cancelar: /^cancelar$/i,
  crearNegocio: /^crear negocio$/i,
  informacionGeneral: /^informaci[oó]n general$/i,
  detallesCuenta: /^detalles de la cuenta$/i,
  tusNegocios: /^tus negocios$/i,
  seccionLegal: /^secci[oó]n legal$/i,
  businessPlan: /^business plan$/i,
  cambiarPlan: /^cambiar plan$/i,
  cuentaCreada: /^cuenta creada$/i,
  estadoActivo: /^estado activo$/i,
  idiomaSeleccionado: /^idioma seleccionado$/i,
  terminos: /^t[eé]rminos y condiciones$/i,
  privacidad: /^pol[ií]tica de privacidad$/i
};

function createInitialReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        status: "FAIL",
        checks: [],
        evidence: {}
      }
    ])
  );
}

function parseHeadlessFlag(value) {
  if (value == null) {
    return true;
  }

  return value.toLowerCase() !== "false";
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUiLoad(page) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 12000 }),
    page.waitForLoadState("networkidle", { timeout: 12000 })
  ]);
  await page.waitForTimeout(900);
}

async function capture(page, screenshotDir, name, fullPage = false) {
  const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "");
  const filePath = path.join(screenshotDir, `${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function buildVisibleLocator(page, pattern) {
  return page.getByText(pattern, { exact: false }).first();
}

async function clickVisibleText(page, pattern, description) {
  const locators = [
    page.getByRole("button", { name: pattern }).first(),
    page.getByRole("link", { name: pattern }).first(),
    page.getByRole("menuitem", { name: pattern }).first(),
    page.getByRole("tab", { name: pattern }).first(),
    page.getByRole("heading", { name: pattern }).first(),
    buildVisibleLocator(page, pattern)
  ];

  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      await locator.click({ timeout: 12000 });
      await waitForUiLoad(page);
      return;
    }
  }

  throw new Error(`Could not find clickable element for: ${description}`);
}

async function assertVisible(page, pattern, checkName) {
  const visible = await buildVisibleLocator(page, pattern).isVisible().catch(() => false);
  if (!visible) {
    throw new Error(`Validation failed: ${checkName}`);
  }
}

async function maybeSelectGoogleAccount(page, accountEmail) {
  const accountLocator = page.getByText(accountEmail, { exact: true }).first();
  if (await accountLocator.isVisible().catch(() => false)) {
    await accountLocator.click({ timeout: 10000 });
    await waitForUiLoad(page);
  }
}

async function loginWithGoogle({ appPage, context, accountEmail }) {
  const existingPages = new Set(context.pages());
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickVisibleText(appPage, TEXT_PATTERNS.signInWithGoogle, "Sign in with Google");

  let authPage = await popupPromise;
  if (!authPage) {
    const allPages = context.pages();
    authPage = allPages.find((candidate) => !existingPages.has(candidate)) || appPage;
  }

  await waitForUiLoad(authPage);
  await maybeSelectGoogleAccount(authPage, accountEmail);

  if (authPage !== appPage) {
    await authPage.waitForEvent("close", { timeout: 15000 }).catch(() => null);
    await appPage.bringToFront();
  }

  await waitForUiLoad(appPage);
}

async function completeLegalValidation({
  appPage,
  context,
  linkPattern,
  headingPattern,
  screenshotDir,
  screenshotName
}) {
  const existingPages = new Set(context.pages());
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickVisibleText(appPage, linkPattern, linkPattern.toString());

  let activeLegalPage = await popupPromise;
  if (!activeLegalPage) {
    const allPages = context.pages();
    activeLegalPage = allPages.find((candidate) => !existingPages.has(candidate)) || appPage;
  }

  await waitForUiLoad(activeLegalPage);
  await assertVisible(activeLegalPage, headingPattern, `Heading ${headingPattern}`);

  // Basic legal content presence by checking that body has enough text.
  const bodyText = await activeLegalPage.locator("body").innerText();
  if (!bodyText || bodyText.trim().length < 120) {
    throw new Error("Legal content text is not sufficiently visible.");
  }

  const screenshotPath = await capture(activeLegalPage, screenshotDir, screenshotName, true);
  const finalUrl = activeLegalPage.url();

  if (activeLegalPage !== appPage) {
    await activeLegalPage.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUiLoad(appPage);
  }

  return { screenshotPath, finalUrl };
}

async function run() {
  const baseUrl = process.env.SALEADS_BASE_URL || "";
  const headless = parseHeadlessFlag(process.env.HEADLESS);
  const googleEmail = process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
  const results = createInitialReport();
  const startedAt = new Date().toISOString().replace(/[:.]/g, "-");
  const outputRoot = path.join(process.cwd(), "artifacts", startedAt);
  const screenshotDir = path.join(outputRoot, "screenshots");

  await ensureDir(screenshotDir);

  const browser = await chromium.launch({
    headless
  });

  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 }
  });
  const page = await context.newPage();

  const validateWithChecks = async (field, checks, evidenceBuilder) => {
    try {
      for (const check of checks) {
        await check();
        results[field].checks.push({ result: "PASS" });
      }

      if (evidenceBuilder) {
        results[field].evidence = await evidenceBuilder();
      }

      results[field].status = "PASS";
    } catch (error) {
      results[field].checks.push({ result: "FAIL", message: error.message });
      results[field].status = "FAIL";
    }
  };

  try {
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    }

    // Step 1: Login with Google
    await validateWithChecks(
      "Login",
      [
        async () => {
          await loginWithGoogle({
            appPage: page,
            context,
            accountEmail: googleEmail
          });
        },
        async () => {
          await waitForUiLoad(page);
          const sidebarVisible = await page
            .locator("aside")
            .first()
            .isVisible()
            .catch(() => false);
          const negocioVisible = await buildVisibleLocator(page, TEXT_PATTERNS.negocioSection)
            .isVisible()
            .catch(() => false);
          if (!sidebarVisible && !negocioVisible) {
            throw new Error("Main app interface/sidebar did not appear after login.");
          }
        }
      ],
      async () => ({
        dashboardScreenshot: await capture(page, screenshotDir, "dashboard_after_login", true)
      })
    );

    // Step 2: Open Mi Negocio menu
    await validateWithChecks(
      "Mi Negocio menu",
      [
        async () => {
          const miNegocioVisible = await buildVisibleLocator(page, TEXT_PATTERNS.miNegocio)
            .isVisible()
            .catch(() => false);
          if (!miNegocioVisible) {
            await clickVisibleText(page, TEXT_PATTERNS.negocioSection, "Negocio section");
          }
          await clickVisibleText(page, TEXT_PATTERNS.miNegocio, "Mi Negocio");
        },
        async () => assertVisible(page, TEXT_PATTERNS.agregarNegocio, "Agregar Negocio visible"),
        async () =>
          assertVisible(page, TEXT_PATTERNS.administrarNegocios, "Administrar Negocios visible")
      ],
      async () => ({
        expandedMenuScreenshot: await capture(page, screenshotDir, "mi_negocio_menu_expanded", false)
      })
    );

    // Step 3: Validate Agregar Negocio modal
    let agregarNegocioModalScreenshot = null;
    await validateWithChecks(
      "Agregar Negocio modal",
      [
        async () => {
          await clickVisibleText(page, TEXT_PATTERNS.agregarNegocio, "Agregar Negocio");
        },
        async () => assertVisible(page, TEXT_PATTERNS.crearNuevoNegocio, "Crear Nuevo Negocio title"),
        async () => assertVisible(page, TEXT_PATTERNS.nombreDelNegocio, "Nombre del Negocio input"),
        async () => assertVisible(page, TEXT_PATTERNS.quota, "Tienes 2 de 3 negocios"),
        async () => assertVisible(page, TEXT_PATTERNS.cancelar, "Cancelar button"),
        async () => assertVisible(page, TEXT_PATTERNS.crearNegocio, "Crear Negocio button"),
        async () => {
          agregarNegocioModalScreenshot = await capture(
            page,
            screenshotDir,
            "agregar_negocio_modal",
            false
          );
        },
        async () => {
          const inputByLabel = page.getByLabel(TEXT_PATTERNS.nombreDelNegocio).first();
          if (await inputByLabel.isVisible().catch(() => false)) {
            await inputByLabel.fill("Negocio Prueba Automatización");
          }
          await clickVisibleText(page, TEXT_PATTERNS.cancelar, "Cancelar");
        }
      ],
      async () => ({
        modalScreenshot: agregarNegocioModalScreenshot
      })
    );

    // Step 4: Open Administrar Negocios and validate sections
    await validateWithChecks(
      "Administrar Negocios view",
      [
        async () => {
          const adminVisible = await buildVisibleLocator(page, TEXT_PATTERNS.administrarNegocios)
            .isVisible()
            .catch(() => false);
          if (!adminVisible) {
            await clickVisibleText(page, TEXT_PATTERNS.miNegocio, "Mi Negocio");
          }
          await clickVisibleText(page, TEXT_PATTERNS.administrarNegocios, "Administrar Negocios");
        },
        async () => assertVisible(page, TEXT_PATTERNS.informacionGeneral, "Información General section"),
        async () => assertVisible(page, TEXT_PATTERNS.detallesCuenta, "Detalles de la Cuenta section"),
        async () => assertVisible(page, TEXT_PATTERNS.tusNegocios, "Tus Negocios section"),
        async () => assertVisible(page, TEXT_PATTERNS.seccionLegal, "Sección Legal section")
      ],
      async () => ({
        accountPageScreenshot: await capture(page, screenshotDir, "administrar_negocios_full_page", true)
      })
    );

    // Step 5: Validate Información General
    await validateWithChecks("Información General", [
      async () => {
        const nameVisible = await page
          .locator("section,div")
          .filter({ hasText: TEXT_PATTERNS.informacionGeneral })
          .locator("h1,h2,h3,h4,p,span,strong")
          .first()
          .isVisible()
          .catch(() => false);
        if (!nameVisible) {
          throw new Error("User name section is not visible.");
        }
      },
      async () => {
        const emailVisible = await page
          .getByText(/^[^\s@]+@[^\s@]+\.[^\s@]+$/)
          .first()
          .isVisible()
          .catch(() => false);
        if (!emailVisible) {
          throw new Error("User email is not visible.");
        }
      },
      async () => assertVisible(page, TEXT_PATTERNS.businessPlan, "BUSINESS PLAN text"),
      async () => assertVisible(page, TEXT_PATTERNS.cambiarPlan, "Cambiar Plan button")
    ]);

    // Step 6: Validate Detalles de la Cuenta
    await validateWithChecks("Detalles de la Cuenta", [
      async () => assertVisible(page, TEXT_PATTERNS.cuentaCreada, "Cuenta creada text"),
      async () => assertVisible(page, TEXT_PATTERNS.estadoActivo, "Estado activo text"),
      async () => assertVisible(page, TEXT_PATTERNS.idiomaSeleccionado, "Idioma seleccionado text")
    ]);

    // Step 7: Validate Tus Negocios
    await validateWithChecks("Tus Negocios", [
      async () => assertVisible(page, TEXT_PATTERNS.tusNegocios, "Tus Negocios heading"),
      async () => assertVisible(page, TEXT_PATTERNS.agregarNegocio, "Agregar Negocio button"),
      async () => assertVisible(page, TEXT_PATTERNS.quota, "Tienes 2 de 3 negocios text")
    ]);

    // Step 8: Validate Términos y Condiciones
    await validateWithChecks(
      "Términos y Condiciones",
      [
        async () => assertVisible(page, TEXT_PATTERNS.seccionLegal, "Sección Legal present"),
        async () => {
          const legalEvidence = await completeLegalValidation({
            appPage: page,
            context,
            linkPattern: TEXT_PATTERNS.terminos,
            headingPattern: TEXT_PATTERNS.terminos,
            screenshotDir,
            screenshotName: "terminos_y_condiciones"
          });
          results["Términos y Condiciones"].evidence = legalEvidence;
        }
      ],
      null
    );

    // Step 9: Validate Política de Privacidad
    await validateWithChecks(
      "Política de Privacidad",
      [
        async () => assertVisible(page, TEXT_PATTERNS.seccionLegal, "Sección Legal present"),
        async () => {
          const legalEvidence = await completeLegalValidation({
            appPage: page,
            context,
            linkPattern: TEXT_PATTERNS.privacidad,
            headingPattern: TEXT_PATTERNS.privacidad,
            screenshotDir,
            screenshotName: "politica_de_privacidad"
          });
          results["Política de Privacidad"].evidence = legalEvidence;
        }
      ],
      null
    );
  } finally {
    const reportPath = path.join(outputRoot, "final-report.json");
    await fs.writeFile(
      reportPath,
      JSON.stringify(
        {
          testName: "saleads_mi_negocio_full_test",
          generatedAt: new Date().toISOString(),
          baseUrlUsed: baseUrl || null,
          report: results
        },
        null,
        2
      ),
      "utf8"
    );

    await browser.close();

    const summaryTable = REPORT_FIELDS.map((field) => ({
      field,
      status: results[field].status
    }));

    console.log("\nFinal Report");
    console.table(summaryTable);
    console.log(`Artifacts: ${outputRoot}`);
  }

  const hasFailure = REPORT_FIELDS.some((field) => results[field].status !== "PASS");
  process.exitCode = hasFailure ? 1 : 0;
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
