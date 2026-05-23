#!/usr/bin/env node

const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_EVIDENCE_DIR = path.join("artifacts", TEST_NAME);
const DEFAULT_TIMEOUT_MS = 20000;

const REPORT_KEYS = [
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

function buildReportTemplate() {
  return REPORT_KEYS.reduce((acc, key) => {
    acc[key] = "NOT RUN";
    return acc;
  }, {});
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

function cleanText(value) {
  return (value || "").replace(/\s+/g, " ").trim();
}

async function waitForUi(page) {
  await page.waitForTimeout(600);
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(250);
}

async function locatorVisible(locator, timeout = DEFAULT_TIMEOUT_MS) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function firstVisible(locators, timeout = DEFAULT_TIMEOUT_MS) {
  for (const locator of locators) {
    if (await locatorVisible(locator, timeout)) {
      return locator.first();
    }
  }
  return null;
}

async function clickAndWait(locator, pageForWait) {
  await locator.click();
  await waitForUi(pageForWait);
}

function regexesFromNames(names) {
  return names.map((name) => new RegExp(name, "i"));
}

async function assertAnyVisible(locators, errorMessage, timeout = DEFAULT_TIMEOUT_MS) {
  const candidate = await firstVisible(locators, timeout);
  if (!candidate) {
    throw new Error(errorMessage);
  }
  return candidate;
}

async function takeScreenshot(page, evidenceDir, filename, fullPage = false) {
  const filePath = path.join(evidenceDir, filename);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function formatError(error) {
  const message = cleanText(error && error.message ? error.message : String(error));
  return message || "Unknown error";
}

async function clickLinkWithPossibleNewTab(context, sourcePage, linkLocator) {
  const newPagePromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
  await clickAndWait(linkLocator, sourcePage);
  const newPage = await newPagePromise;

  if (newPage) {
    await newPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await newPage.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
    return { openedNewTab: true, targetPage: newPage };
  }

  return { openedNewTab: false, targetPage: sourcePage };
}

async function ensureMiNegocioExpanded(page) {
  const agregarNegocioVisible = await locatorVisible(
    page.getByRole("menuitem", { name: /Agregar Negocio/i }),
    2500,
  );
  if (agregarNegocioVisible) {
    return;
  }

  const miNegocioOption = await assertAnyVisible(
    [
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ],
    "No se encontró la opción 'Mi Negocio' para expandir el menú.",
  );
  await clickAndWait(miNegocioOption, page);
}

async function validateGoogleAccountSelection(authPage) {
  const accountByEmail = authPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i"));
  if (await locatorVisible(accountByEmail, 10000)) {
    await clickAndWait(accountByEmail.first(), authPage);
    return true;
  }

  const accountByButton = authPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") });
  if (await locatorVisible(accountByButton, 5000)) {
    await clickAndWait(accountByButton.first(), authPage);
    return true;
  }

  return false;
}

async function validateSectionHeadings(page) {
  const headings = [
    /Informaci[oó]n General/i,
    /Detalles de la Cuenta/i,
    /Tus Negocios/i,
    /Secci[oó]n Legal/i,
  ];

  for (const heading of headings) {
    const headingLocator = await assertAnyVisible(
      [page.getByRole("heading", { name: heading }), page.getByText(heading)],
      `No se encontró la sección requerida: ${heading}`,
    );
    await headingLocator.scrollIntoViewIfNeeded();
  }
}

async function findSectionContainer(page, headingRegex) {
  const heading = await assertAnyVisible(
    [page.getByRole("heading", { name: headingRegex }), page.getByText(headingRegex)],
    `No se encontró el encabezado de sección: ${headingRegex}`,
  );
  const container = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  return container.first();
}

async function validateInfoGeneral(page) {
  const infoSection = await findSectionContainer(page, /Informaci[oó]n General/i);

  const emailLocator = infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
  if (!(await locatorVisible(emailLocator, 10000))) {
    throw new Error("No se encontró un email visible en 'Información General'.");
  }

  const nameLocator = await firstVisible(
    [
      infoSection.getByText(/Nombre/i),
      infoSection.locator("p, span, strong").filter({ hasText: /[A-Za-z]{2,}\s+[A-Za-z]{2,}/ }),
    ],
    10000,
  );
  if (!nameLocator) {
    throw new Error("No se encontró el nombre de usuario en 'Información General'.");
  }

  await assertAnyVisible(
    [infoSection.getByText(/BUSINESS PLAN/i), page.getByText(/BUSINESS PLAN/i)],
    "No se encontró el texto 'BUSINESS PLAN'.",
  );

  await assertAnyVisible(
    [
      infoSection.getByRole("button", { name: /Cambiar Plan/i }),
      page.getByRole("button", { name: /Cambiar Plan/i }),
      infoSection.getByText(/Cambiar Plan/i),
    ],
    "No se encontró el botón 'Cambiar Plan'.",
  );
}

async function validateDetallesCuenta(page) {
  const detailsSection = await findSectionContainer(page, /Detalles de la Cuenta/i);
  const requiredTexts = regexesFromNames(["Cuenta creada", "Estado activo", "Idioma seleccionado"]);

  for (const textPattern of requiredTexts) {
    await assertAnyVisible(
      [detailsSection.getByText(textPattern), page.getByText(textPattern)],
      `No se encontró el texto requerido en Detalles de la Cuenta: ${textPattern}`,
    );
  }
}

async function validateTusNegocios(page) {
  const businessSection = await findSectionContainer(page, /Tus Negocios/i);
  const interactiveCount = await businessSection.locator("button, a, li, [role='row'], article").count();
  if (interactiveCount < 1) {
    throw new Error("No se detectó listado visible de negocios en 'Tus Negocios'.");
  }

  await assertAnyVisible(
    [businessSection.getByRole("button", { name: /Agregar Negocio/i }), page.getByRole("button", { name: /Agregar Negocio/i })],
    "No se encontró el botón 'Agregar Negocio' en 'Tus Negocios'.",
  );

  await assertAnyVisible(
    [businessSection.getByText(/Tienes 2 de 3 negocios/i), page.getByText(/Tienes 2 de 3 negocios/i)],
    "No se encontró el texto 'Tienes 2 de 3 negocios' en 'Tus Negocios'.",
  );
}

async function validateLegalPageContent(targetPage, headingRegex) {
  await assertAnyVisible(
    [targetPage.getByRole("heading", { name: headingRegex }), targetPage.getByText(headingRegex)],
    `No se encontró el encabezado legal esperado: ${headingRegex}`,
    20000,
  );

  const bodyText = cleanText(await targetPage.locator("body").innerText());
  if (bodyText.length < 120) {
    throw new Error("No se detectó contenido legal suficiente en la página abierta.");
  }
}

async function run() {
  const evidenceDir = process.env.SALEADS_EVIDENCE_DIR || DEFAULT_EVIDENCE_DIR;
  const startUrl = process.env.SALEADS_START_URL;
  const headless = process.env.SALEADS_HEADLESS !== "false";
  const browser = await chromium.launch({ headless });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const report = buildReportTemplate();
  const details = {};
  const legalUrls = {};

  await ensureDir(evidenceDir);

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url().startsWith("about:blank")) {
    console.warn(
      "[WARN] SALEADS_START_URL no está definido. El script asume que debe iniciar en la pantalla de login.",
    );
  }

  try {
    try {
      const loginButton = await assertAnyVisible(
        [
          page.getByRole("button", { name: /Sign in with Google|Iniciar sesi[oó]n con Google|Ingresar con Google/i }),
          page.getByRole("link", { name: /Sign in with Google|Iniciar sesi[oó]n con Google|Ingresar con Google/i }),
          page.locator("button, a").filter({ hasText: /Google/i }),
        ],
        "No se encontró el botón de login con Google.",
      );

      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickAndWait(loginButton, page);
      const popupPage = await popupPromise;

      if (popupPage) {
        await popupPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
      }

      const authPage = popupPage || page;
      const isGoogleAuthFlow =
        /accounts\.google\.com/i.test(authPage.url()) ||
        (await locatorVisible(authPage.getByText(/Choose an account|Elige una cuenta/i), 5000));

      if (isGoogleAuthFlow) {
        await validateGoogleAccountSelection(authPage);
      }

      if (popupPage && !popupPage.isClosed()) {
        await popupPage.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      }

      await waitForUi(page);

      await assertAnyVisible(
        [
          page.locator("aside"),
          page.locator("nav"),
          page.getByText(/Mi Negocio|Negocio|Dashboard/i),
        ],
        "No se detectó la interfaz principal o la barra lateral luego del login.",
      );

      await takeScreenshot(page, evidenceDir, "01-dashboard-loaded.png");
      report["Login"] = "PASS";
    } catch (error) {
      report["Login"] = "FAIL";
      details["Login"] = formatError(error);
    }

    try {
      await ensureMiNegocioExpanded(page);

      await assertAnyVisible(
        [
          page.getByRole("menuitem", { name: /Agregar Negocio/i }),
          page.getByRole("button", { name: /Agregar Negocio/i }),
          page.getByText(/Agregar Negocio/i),
        ],
        "No se visualizó 'Agregar Negocio' al expandir 'Mi Negocio'.",
      );
      await assertAnyVisible(
        [
          page.getByRole("menuitem", { name: /Administrar Negocios/i }),
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i),
        ],
        "No se visualizó 'Administrar Negocios' al expandir 'Mi Negocio'.",
      );

      await takeScreenshot(page, evidenceDir, "02-mi-negocio-expanded.png");
      report["Mi Negocio menu"] = "PASS";
    } catch (error) {
      report["Mi Negocio menu"] = "FAIL";
      details["Mi Negocio menu"] = formatError(error);
    }

    try {
      const agregarNegocio = await assertAnyVisible(
        [
          page.getByRole("menuitem", { name: /Agregar Negocio/i }),
          page.getByRole("button", { name: /Agregar Negocio/i }),
          page.getByText(/Agregar Negocio/i),
        ],
        "No se encontró la opción 'Agregar Negocio'.",
      );
      await clickAndWait(agregarNegocio, page);

      const modalTitle = await assertAnyVisible(
        [
          page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
          page.getByRole("dialog").getByText(/Crear Nuevo Negocio/i),
          page.getByText(/Crear Nuevo Negocio/i),
        ],
        "No apareció el modal 'Crear Nuevo Negocio'.",
      );

      const modal = modalTitle.locator("xpath=ancestor::*[self::div or self::section][@role='dialog' or @aria-modal='true' or contains(@class,'modal')][1]");
      const modalRoot = (await locatorVisible(modal, 1500)) ? modal : page.getByRole("dialog");

      await assertAnyVisible(
        [modalRoot.getByLabel(/Nombre del Negocio/i), modalRoot.getByPlaceholder(/Nombre del Negocio/i), modalRoot.getByText(/Nombre del Negocio/i)],
        "No se encontró el campo 'Nombre del Negocio'.",
      );
      await assertAnyVisible(
        [modalRoot.getByText(/Tienes 2 de 3 negocios/i), page.getByText(/Tienes 2 de 3 negocios/i)],
        "No se encontró el texto 'Tienes 2 de 3 negocios' dentro del modal.",
      );
      await assertAnyVisible(
        [modalRoot.getByRole("button", { name: /Cancelar/i }), page.getByRole("button", { name: /Cancelar/i })],
        "No se encontró el botón 'Cancelar' en el modal.",
      );
      const crearNegocioBtn = await assertAnyVisible(
        [modalRoot.getByRole("button", { name: /Crear Negocio/i }), page.getByRole("button", { name: /Crear Negocio/i })],
        "No se encontró el botón 'Crear Negocio' en el modal.",
      );
      await crearNegocioBtn.scrollIntoViewIfNeeded();

      const nombreField = await firstVisible(
        [modalRoot.getByLabel(/Nombre del Negocio/i), modalRoot.getByPlaceholder(/Nombre del Negocio/i), modalRoot.locator("input").first()],
        5000,
      );
      if (nombreField) {
        await nombreField.fill("Negocio Prueba Automatización");
      }

      await takeScreenshot(page, evidenceDir, "03-agregar-negocio-modal.png");

      const cancelarBtn = await assertAnyVisible(
        [modalRoot.getByRole("button", { name: /Cancelar/i }), page.getByRole("button", { name: /Cancelar/i })],
        "No se encontró el botón 'Cancelar' para cerrar el modal.",
      );
      await clickAndWait(cancelarBtn, page);

      report["Agregar Negocio modal"] = "PASS";
    } catch (error) {
      report["Agregar Negocio modal"] = "FAIL";
      details["Agregar Negocio modal"] = formatError(error);
    }

    try {
      await ensureMiNegocioExpanded(page);
      const administrarNegocios = await assertAnyVisible(
        [
          page.getByRole("menuitem", { name: /Administrar Negocios/i }),
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i),
        ],
        "No se encontró la opción 'Administrar Negocios'.",
      );

      await clickAndWait(administrarNegocios, page);
      await validateSectionHeadings(page);
      await takeScreenshot(page, evidenceDir, "04-administrar-negocios-page.png", true);

      report["Administrar Negocios view"] = "PASS";
    } catch (error) {
      report["Administrar Negocios view"] = "FAIL";
      details["Administrar Negocios view"] = formatError(error);
    }

    try {
      await validateInfoGeneral(page);
      report["Información General"] = "PASS";
    } catch (error) {
      report["Información General"] = "FAIL";
      details["Información General"] = formatError(error);
    }

    try {
      await validateDetallesCuenta(page);
      report["Detalles de la Cuenta"] = "PASS";
    } catch (error) {
      report["Detalles de la Cuenta"] = "FAIL";
      details["Detalles de la Cuenta"] = formatError(error);
    }

    try {
      await validateTusNegocios(page);
      report["Tus Negocios"] = "PASS";
    } catch (error) {
      report["Tus Negocios"] = "FAIL";
      details["Tus Negocios"] = formatError(error);
    }

    try {
      const legalSection = await findSectionContainer(page, /Secci[oó]n Legal/i);
      const termsLink = await assertAnyVisible(
        [
          legalSection.getByRole("link", { name: /T[ée]rminos y Condiciones/i }),
          legalSection.getByText(/T[ée]rminos y Condiciones/i),
          page.getByRole("link", { name: /T[ée]rminos y Condiciones/i }),
          page.getByText(/T[ée]rminos y Condiciones/i),
        ],
        "No se encontró el enlace de 'Términos y Condiciones'.",
      );

      const { openedNewTab, targetPage } = await clickLinkWithPossibleNewTab(context, page, termsLink);
      await validateLegalPageContent(targetPage, /T[ée]rminos y Condiciones/i);
      legalUrls["Términos y Condiciones"] = targetPage.url();
      await takeScreenshot(targetPage, evidenceDir, "05-terminos-y-condiciones.png", true);

      if (openedNewTab) {
        await targetPage.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }

      report["Términos y Condiciones"] = "PASS";
    } catch (error) {
      report["Términos y Condiciones"] = "FAIL";
      details["Términos y Condiciones"] = formatError(error);
    }

    try {
      const legalSection = await findSectionContainer(page, /Secci[oó]n Legal/i);
      const privacyLink = await assertAnyVisible(
        [
          legalSection.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }),
          legalSection.getByText(/Pol[ií]tica de Privacidad/i),
          page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }),
          page.getByText(/Pol[ií]tica de Privacidad/i),
        ],
        "No se encontró el enlace de 'Política de Privacidad'.",
      );

      const { openedNewTab, targetPage } = await clickLinkWithPossibleNewTab(context, page, privacyLink);
      await validateLegalPageContent(targetPage, /Pol[ií]tica de Privacidad/i);
      legalUrls["Política de Privacidad"] = targetPage.url();
      await takeScreenshot(targetPage, evidenceDir, "06-politica-de-privacidad.png", true);

      if (openedNewTab) {
        await targetPage.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }

      report["Política de Privacidad"] = "PASS";
    } catch (error) {
      report["Política de Privacidad"] = "FAIL";
      details["Política de Privacidad"] = formatError(error);
    }
  } finally {
    await browser.close();
  }

  const allPassed = Object.values(report).every((result) => result === "PASS");
  const finalOutput = {
    test: TEST_NAME,
    result: allPassed ? "PASS" : "FAIL",
    report,
    details,
    legalUrls,
    evidenceDir: path.resolve(evidenceDir),
  };

  console.log(JSON.stringify(finalOutput, null, 2));
  process.exitCode = allPassed ? 0 : 1;
}

run().catch((error) => {
  const fallbackOutput = {
    test: TEST_NAME,
    result: "FAIL",
    report: buildReportTemplate(),
    details: {
      fatal: formatError(error),
    },
  };
  console.error(JSON.stringify(fallbackOutput, null, 2));
  process.exitCode = 1;
});
