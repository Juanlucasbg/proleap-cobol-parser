#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const ARTIFACTS_DIR = path.resolve(__dirname, "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_JSON_PATH = path.join(ARTIFACTS_DIR, "report.json");
const REPORT_TXT_PATH = path.join(ARTIFACTS_DIR, "report.txt");

const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.saleads_login_url || "";
const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ||
  process.env.GOOGLE_ACCOUNT_EMAIL ||
  "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_NAME =
  process.env.SALEADS_TEST_BUSINESS_NAME || "Negocio Prueba Automatizacion";
const HEADLESS = `${process.env.PW_HEADLESS || "true"}`.toLowerCase() !== "false";

/**
 * @typedef {Object} StepResult
 * @property {number} id
 * @property {string} name
 * @property {"PASS"|"FAIL"|"SKIPPED"} status
 * @property {string[]} checks
 * @property {string[]} evidence
 * @property {string[]} details
 */

const stepDefinitions = [
  { id: 1, name: "Login" },
  { id: 2, name: "Mi Negocio menu" },
  { id: 3, name: "Agregar Negocio modal" },
  { id: 4, name: "Administrar Negocios view" },
  { id: 5, name: "Información General" },
  { id: 6, name: "Detalles de la Cuenta" },
  { id: 7, name: "Tus Negocios" },
  { id: 8, name: "Términos y Condiciones" },
  { id: 9, name: "Política de Privacidad" },
];

const stepResults = new Map(
  stepDefinitions.map((definition) => [
    definition.id,
    {
      id: definition.id,
      name: definition.name,
      status: "SKIPPED",
      checks: [],
      evidence: [],
      details: [],
    },
  ])
);

function ensureArtifactsDirs() {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

function timestamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function sanitizeForFileName(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function markStep(stepId, partial) {
  const current = stepResults.get(stepId);
  stepResults.set(stepId, {
    ...current,
    ...partial,
    checks: partial.checks || current.checks,
    evidence: partial.evidence || current.evidence,
    details: partial.details || current.details,
  });
}

function pass(stepId, checks, details = [], evidence = []) {
  markStep(stepId, { status: "PASS", checks, details, evidence });
}

function fail(stepId, checks, details = [], evidence = []) {
  markStep(stepId, { status: "FAIL", checks, details, evidence });
}

function skip(stepId, reason) {
  markStep(stepId, { status: "SKIPPED", checks: [], details: [reason], evidence: [] });
}

async function safeScreenshot(page, stepId, label) {
  const fileName = `${String(stepId).padStart(2, "0")}-${sanitizeForFileName(label)}-${timestamp()}.png`;
  const fullPath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: fullPath, fullPage: true });
  return path.relative(ARTIFACTS_DIR, fullPath);
}

async function waitUiSettle(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(1200);
}

async function clickByVisibleText(page, candidates, options = {}) {
  const timeout = options.timeout || 12000;
  for (const candidate of candidates) {
    const locator = page.getByRole("button", { name: candidate, exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click({ timeout });
      await waitUiSettle(page);
      return { clicked: true, text: candidate, target: "button" };
    }
  }

  for (const candidate of candidates) {
    const locator = page.getByRole("link", { name: candidate, exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click({ timeout });
      await waitUiSettle(page);
      return { clicked: true, text: candidate, target: "link" };
    }
  }

  for (const candidate of candidates) {
    const locator = page.getByText(candidate, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click({ timeout });
      await waitUiSettle(page);
      return { clicked: true, text: candidate, target: "text" };
    }
  }

  return { clicked: false, text: "", target: "" };
}

async function expectVisibleText(page, text, timeout = 12000) {
  const locator = page.getByText(text, { exact: false }).first();
  try {
    await locator.waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function expectVisibleLabelOrPlaceholder(page, text, timeout = 12000) {
  const byLabel = page.getByLabel(text, { exact: false }).first();
  if (await byLabel.isVisible().catch(() => false)) {
    return true;
  }
  const byPlaceholder = page.getByPlaceholder(text, { exact: false }).first();
  if (await byPlaceholder.isVisible().catch(() => false)) {
    return true;
  }
  const byText = page.getByText(text, { exact: false }).first();
  try {
    await byText.waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function findGoogleChooserAndSelect(page, context) {
  const chooserCandidates = [
    context.pages().find((p) => p !== page),
    ...context.pages().filter((p) => p !== page),
  ].filter(Boolean);

  for (const candidatePage of chooserCandidates) {
    await candidatePage.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
    const selected = await clickByVisibleText(candidatePage, [GOOGLE_ACCOUNT_EMAIL], { timeout: 8000 });
    if (selected.clicked) {
      await waitUiSettle(candidatePage);
      return true;
    }
  }

  const selectedOnMain = await clickByVisibleText(page, [GOOGLE_ACCOUNT_EMAIL], { timeout: 8000 });
  if (selectedOnMain.clicked) {
    await waitUiSettle(page);
    return true;
  }
  return false;
}

async function openLegalLinkAndValidate(page, linkText, expectedHeading, stepId) {
  const details = [];
  const checks = [];
  const evidence = [];
  const originalPage = page;

  const linkLocator = page.getByRole("link", { name: linkText, exact: false }).first();
  const buttonLocator = page.getByRole("button", { name: linkText, exact: false }).first();
  if (
    !(await linkLocator.isVisible().catch(() => false)) &&
    !(await buttonLocator.isVisible().catch(() => false))
  ) {
    const fallback = page.getByText(linkText, { exact: false }).first();
    if (!(await fallback.isVisible().catch(() => false))) {
      fail(stepId, checks, [`No se encontro el enlace '${linkText}' en la seccion legal.`], evidence);
      return { ok: false, page: originalPage, finalUrl: "" };
    }
  }

  let openedPage = null;
  const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);

  const clicked = await clickByVisibleText(page, [linkText], { timeout: 10000 });
  if (!clicked.clicked) {
    fail(stepId, checks, [`No se pudo hacer click en '${linkText}'.`], evidence);
    return { ok: false, page: originalPage, finalUrl: "" };
  }

  openedPage = await popupPromise;
  let activePage = page;
  if (openedPage) {
    activePage = openedPage;
    details.push(`El enlace '${linkText}' se abrio en una nueva pestana.`);
    await waitUiSettle(activePage);
  } else {
    details.push(`El enlace '${linkText}' navego en la misma pestana.`);
    await waitUiSettle(activePage);
  }

  const headingFound =
    (await expectVisibleText(activePage, expectedHeading, 15000)) ||
    (await expectVisibleText(activePage, expectedHeading.normalize("NFD").replace(/[\u0300-\u036f]/g, ""), 7000));
  checks.push(`Heading '${expectedHeading}' visible: ${headingFound ? "PASS" : "FAIL"}`);

  const legalTextVisible =
    (await expectVisibleText(activePage, "Términos", 7000)) ||
    (await expectVisibleText(activePage, "Terminos", 7000)) ||
    (await expectVisibleText(activePage, "Política", 7000)) ||
    (await expectVisibleText(activePage, "Politica", 7000)) ||
    (await expectVisibleText(activePage, "privacidad", 7000)) ||
    (await expectVisibleText(activePage, "condiciones", 7000));
  checks.push(`Contenido legal visible: ${legalTextVisible ? "PASS" : "FAIL"}`);

  const screenshot = await safeScreenshot(activePage, stepId, `${linkText}-legal-page`);
  evidence.push(screenshot);

  const finalUrl = activePage.url();
  details.push(`URL final: ${finalUrl}`);

  if (headingFound && legalTextVisible) {
    pass(stepId, checks, details, evidence);
  } else {
    fail(stepId, checks, details, evidence);
  }

  if (openedPage) {
    await openedPage.close().catch(() => {});
    await originalPage.bringToFront().catch(() => {});
    await waitUiSettle(originalPage);
    return { ok: headingFound && legalTextVisible, page: originalPage, finalUrl };
  }

  await originalPage.goBack({ timeout: 15000 }).catch(() => {});
  await waitUiSettle(originalPage);
  return { ok: headingFound && legalTextVisible, page: originalPage, finalUrl };
}

function hasFailed(stepId) {
  return stepResults.get(stepId)?.status === "FAIL";
}

function prerequisitePassed(...stepIds) {
  return stepIds.every((stepId) => stepResults.get(stepId)?.status === "PASS");
}

function isStepStillSkipped(stepId) {
  return stepResults.get(stepId)?.status === "SKIPPED";
}

async function run() {
  ensureArtifactsDirs();

  const meta = {
    executedAt: new Date().toISOString(),
    loginUrlInput: LOGIN_URL || "(not provided)",
    headless: HEADLESS,
    googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
    environmentAgnosticMode: true,
  };

  let browser;
  let context;
  let page;

  try {
    browser = await chromium.launch({ headless: HEADLESS });
    context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    page = await context.newPage();

    if (LOGIN_URL) {
      await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded", timeout: 30000 });
      await waitUiSettle(page);
    } else {
      skip(
        1,
        "SALEADS_LOGIN_URL no definido. No fue posible abrir automaticamente la pantalla de login."
      );
      for (const stepId of [2, 3, 4, 5, 6, 7, 8, 9]) {
        skip(stepId, "Omitido porque el login no pudo ejecutarse sin URL de entorno.");
      }
    }

    // Step 1: Login with Google.
    if (isStepStillSkipped(1)) {
      // Step already intentionally skipped when no login URL is provided.
    } else {
      const checks = [];
      const details = [];
      const evidence = [];

      const clickResult = await clickByVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesion con Google",
        "Continuar con Google",
        "Google",
        "Login with Google",
      ]);

      if (!clickResult.clicked) {
        fail(
          1,
          ["Boton de login con Google visible/clickable: FAIL"],
          ["No se encontro un boton o enlace de login con Google por texto visible."],
          evidence
        );
      } else {
        checks.push("Boton de login con Google visible/clickable: PASS");
        details.push(`Se hizo click usando selector por texto: '${clickResult.text}' (${clickResult.target}).`);
        await findGoogleChooserAndSelect(page, context);
        await waitUiSettle(page);

        const appVisible = await expectVisibleText(page, "Negocio", 25000).catch(() => false);
        const sidebarVisible =
          (await page.getByRole("navigation").first().isVisible().catch(() => false)) ||
          (await expectVisibleText(page, "Mi Negocio", 8000)) ||
          (await expectVisibleText(page, "Negocio", 8000));

        checks.push(`Interfaz principal visible: ${appVisible ? "PASS" : "FAIL"}`);
        checks.push(`Sidebar izquierda visible: ${sidebarVisible ? "PASS" : "FAIL"}`);

        const dashboardShot = await safeScreenshot(page, 1, "dashboard-loaded");
        evidence.push(dashboardShot);

        if (appVisible && sidebarVisible) {
          pass(1, checks, details, evidence);
        } else {
          fail(1, checks, details, evidence);
        }
      }
    }

    if (hasFailed(1)) {
      for (const stepId of [2, 3, 4, 5, 6, 7, 8, 9]) {
        skip(stepId, "Omitido porque fallo el login.");
      }
    }

    // Step 2: Open Mi Negocio menu.
    if (prerequisitePassed(1) && isStepStillSkipped(2)) {
      const checks = [];
      const details = [];
      const evidence = [];

      const clickNegocio = await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
      if (!clickNegocio.clicked) {
        fail(
          2,
          ["Menu 'Negocio' clickable: FAIL"],
          ["No se encontro la opcion 'Negocio' o 'Mi Negocio' en la barra lateral."],
          evidence
        );
      } else {
        checks.push("Menu 'Negocio' clickable: PASS");
        const miNegocioVisible = await expectVisibleText(page, "Mi Negocio", 8000);
        const agregarVisible = await expectVisibleText(page, "Agregar Negocio", 12000);
        const administrarVisible = await expectVisibleText(page, "Administrar Negocios", 12000);

        checks.push(`Submenu expandido: ${miNegocioVisible ? "PASS" : "FAIL"}`);
        checks.push(`'Agregar Negocio' visible: ${agregarVisible ? "PASS" : "FAIL"}`);
        checks.push(`'Administrar Negocios' visible: ${administrarVisible ? "PASS" : "FAIL"}`);

        const menuShot = await safeScreenshot(page, 2, "mi-negocio-menu-expanded");
        evidence.push(menuShot);

        details.push(`Click inicial en: '${clickNegocio.text}' (${clickNegocio.target}).`);

        if (miNegocioVisible && agregarVisible && administrarVisible) {
          pass(2, checks, details, evidence);
        } else {
          fail(2, checks, details, evidence);
        }
      }
    }

    if (!prerequisitePassed(1, 2)) {
      for (const stepId of [3, 4, 5, 6, 7, 8, 9]) {
        if (isStepStillSkipped(stepId)) {
          skip(stepId, "Omitido por falla en pasos previos (login/menu).");
        }
      }
    }

    // Step 3: Validate Agregar Negocio modal.
    if (prerequisitePassed(1, 2) && isStepStillSkipped(3)) {
      const checks = [];
      const details = [];
      const evidence = [];

      const clickAgregar = await clickByVisibleText(page, ["Agregar Negocio"]);
      if (!clickAgregar.clicked) {
        fail(3, ["Click en 'Agregar Negocio': FAIL"], ["No se pudo abrir el modal."], evidence);
      } else {
        checks.push("Click en 'Agregar Negocio': PASS");
        const titleVisible =
          (await expectVisibleText(page, "Crear Nuevo Negocio", 12000)) ||
          (await expectVisibleText(page, "Crear nuevo negocio", 12000));
        const nameFieldVisible =
          (await expectVisibleLabelOrPlaceholder(page, "Nombre del Negocio", 12000)) ||
          (await expectVisibleLabelOrPlaceholder(page, "Nombre de Negocio", 12000));
        const quotaVisible =
          (await expectVisibleText(page, "Tienes 2 de 3 negocios", 12000)) ||
          (await expectVisibleText(page, "2 de 3 negocios", 12000));
        const cancelButton = page.getByRole("button", { name: "Cancelar", exact: false }).first();
        const createButton = page.getByRole("button", { name: "Crear Negocio", exact: false }).first();
        const cancelButtonVisible =
          (await cancelButton.isVisible().catch(() => false)) ||
          (await expectVisibleText(page, "Cancelar", 4000));
        const createButtonVisible =
          (await createButton.isVisible().catch(() => false)) ||
          (await expectVisibleText(page, "Crear Negocio", 4000)) ||
          (await expectVisibleText(page, "Crear negocio", 4000));

        checks.push(`Titulo 'Crear Nuevo Negocio' visible: ${titleVisible ? "PASS" : "FAIL"}`);
        checks.push(`Campo 'Nombre del Negocio' visible: ${nameFieldVisible ? "PASS" : "FAIL"}`);
        checks.push(`Texto cuota 'Tienes 2 de 3 negocios' visible: ${quotaVisible ? "PASS" : "FAIL"}`);
        checks.push(`Boton 'Cancelar' visible: ${cancelButtonVisible ? "PASS" : "FAIL"}`);
        checks.push(`Boton 'Crear Negocio' visible: ${createButtonVisible ? "PASS" : "FAIL"}`);

        const modalShot = await safeScreenshot(page, 3, "agregar-negocio-modal");
        evidence.push(modalShot);

        let nameInput = page.getByLabel("Nombre del Negocio", { exact: false }).first();
        if (!(await nameInput.isVisible().catch(() => false))) {
          nameInput = page.getByPlaceholder("Nombre del Negocio", { exact: false }).first();
        }
        if (await nameInput.isVisible().catch(() => false)) {
          await nameInput.click();
          await nameInput.fill(BUSINESS_NAME).catch(() => {});
          details.push("Se ejecuto accion opcional: escribir nombre de negocio de prueba.");
        }

        if (await cancelButton.isVisible().catch(() => false)) {
          await cancelButton.click({ timeout: 8000 }).catch(() => {});
          await waitUiSettle(page);
          details.push("Se ejecuto accion opcional: cerrar modal con boton 'Cancelar'.");
        } else {
          await clickByVisibleText(page, ["Cancelar"], { timeout: 8000 });
          details.push("Se ejecuto accion opcional: cerrar modal por texto 'Cancelar'.");
        }

        if (
          titleVisible &&
          nameFieldVisible &&
          quotaVisible &&
          cancelButtonVisible &&
          createButtonVisible
        ) {
          pass(3, checks, details, evidence);
        } else {
          fail(3, checks, details, evidence);
        }
      }
    }

    if (!prerequisitePassed(1, 2, 3)) {
      for (const stepId of [4, 5, 6, 7, 8, 9]) {
        if (isStepStillSkipped(stepId)) {
          skip(stepId, "Omitido por falla en pasos previos.");
        }
      }
    }

    // Step 4: Open Administrar Negocios page.
    if (prerequisitePassed(1, 2, 3) && isStepStillSkipped(4)) {
      const checks = [];
      const details = [];
      const evidence = [];

      if (!(await expectVisibleText(page, "Administrar Negocios", 3000))) {
        await clickByVisibleText(page, ["Negocio", "Mi Negocio"], { timeout: 8000 });
      }

      const clickAdministrar = await clickByVisibleText(page, ["Administrar Negocios"]);
      if (!clickAdministrar.clicked) {
        fail(
          4,
          ["Click en 'Administrar Negocios': FAIL"],
          ["No se pudo abrir la vista de administracion de negocios."],
          evidence
        );
      } else {
        checks.push("Click en 'Administrar Negocios': PASS");
        await waitUiSettle(page);

        const infoGeneral = await expectVisibleText(page, "Información General", 15000).catch(() => false);
        const infoGeneralNoAccent = infoGeneral || (await expectVisibleText(page, "Informacion General", 6000));
        const detallesCuenta = await expectVisibleText(page, "Detalles de la Cuenta", 12000);
        const tusNegocios = await expectVisibleText(page, "Tus Negocios", 12000);
        const legalSection =
          (await expectVisibleText(page, "Sección Legal", 12000)) ||
          (await expectVisibleText(page, "Seccion Legal", 12000));

        checks.push(`Seccion 'Informacion General' visible: ${infoGeneralNoAccent ? "PASS" : "FAIL"}`);
        checks.push(`Seccion 'Detalles de la Cuenta' visible: ${detallesCuenta ? "PASS" : "FAIL"}`);
        checks.push(`Seccion 'Tus Negocios' visible: ${tusNegocios ? "PASS" : "FAIL"}`);
        checks.push(`Seccion 'Seccion Legal' visible: ${legalSection ? "PASS" : "FAIL"}`);

        const accountShot = await safeScreenshot(page, 4, "administrar-negocios-account-page");
        evidence.push(accountShot);

        if (infoGeneralNoAccent && detallesCuenta && tusNegocios && legalSection) {
          pass(4, checks, details, evidence);
        } else {
          fail(4, checks, details, evidence);
        }
      }
    }

    // Step 5: Validate Información General.
    {
      if (!prerequisitePassed(4)) {
        if (isStepStillSkipped(5)) {
          skip(5, "Omitido por falla al abrir 'Administrar Negocios'.");
        }
      } else {
        const checks = [];
        const details = [];
        const evidence = [];
        const anyNameVisible =
          (await expectVisibleText(page, "Nombre", 10000)) ||
          (await expectVisibleText(page, GOOGLE_ACCOUNT_EMAIL.split("@")[0], 6000));
        const anyEmailVisible = await expectVisibleText(page, "@", 10000);
        const businessPlanVisible = await expectVisibleText(page, "BUSINESS PLAN", 10000);
        const cambiarPlanVisible = await expectVisibleText(page, "Cambiar Plan", 10000);

        checks.push(`Nombre de usuario visible: ${anyNameVisible ? "PASS" : "FAIL"}`);
        checks.push(`Email de usuario visible: ${anyEmailVisible ? "PASS" : "FAIL"}`);
        checks.push(`Texto 'BUSINESS PLAN' visible: ${businessPlanVisible ? "PASS" : "FAIL"}`);
        checks.push(`Boton 'Cambiar Plan' visible: ${cambiarPlanVisible ? "PASS" : "FAIL"}`);

        const ok = anyNameVisible && anyEmailVisible && businessPlanVisible && cambiarPlanVisible;
        if (ok) {
          pass(5, checks, details, evidence);
        } else {
          fail(5, checks, details, evidence);
        }
      }
    }

    // Step 6: Validate Detalles de la Cuenta.
    {
      if (!prerequisitePassed(4)) {
        if (isStepStillSkipped(6)) {
          skip(6, "Omitido por falla al abrir 'Administrar Negocios'.");
        }
      } else {
        const checks = [];
        const createdVisible = await expectVisibleText(page, "Cuenta creada", 10000);
        const activeVisible = await expectVisibleText(page, "Estado activo", 10000);
        const languageVisible = await expectVisibleText(page, "Idioma seleccionado", 10000);

        checks.push(`'Cuenta creada' visible: ${createdVisible ? "PASS" : "FAIL"}`);
        checks.push(`'Estado activo' visible: ${activeVisible ? "PASS" : "FAIL"}`);
        checks.push(`'Idioma seleccionado' visible: ${languageVisible ? "PASS" : "FAIL"}`);

        if (createdVisible && activeVisible && languageVisible) {
          pass(6, checks);
        } else {
          fail(6, checks);
        }
      }
    }

    // Step 7: Validate Tus Negocios.
    {
      if (!prerequisitePassed(4)) {
        if (isStepStillSkipped(7)) {
          skip(7, "Omitido por falla al abrir 'Administrar Negocios'.");
        }
      } else {
        const checks = [];
        const listVisible =
          (await expectVisibleText(page, "Tus Negocios", 8000)) ||
          (await expectVisibleText(page, "Negocios", 8000));
        const addButtonVisible = await expectVisibleText(page, "Agregar Negocio", 8000);
        const quotaVisible =
          (await expectVisibleText(page, "Tienes 2 de 3 negocios", 8000)) ||
          (await expectVisibleText(page, "2 de 3 negocios", 8000));

        checks.push(`Lista de negocios visible: ${listVisible ? "PASS" : "FAIL"}`);
        checks.push(`Boton 'Agregar Negocio' visible: ${addButtonVisible ? "PASS" : "FAIL"}`);
        checks.push(`Texto 'Tienes 2 de 3 negocios' visible: ${quotaVisible ? "PASS" : "FAIL"}`);

        if (listVisible && addButtonVisible && quotaVisible) {
          pass(7, checks);
        } else {
          fail(7, checks);
        }
      }
    }

    // Step 8: Validate Términos y Condiciones.
    {
      if (!prerequisitePassed(4)) {
        if (isStepStillSkipped(8)) {
          skip(8, "Omitido por falla al abrir 'Administrar Negocios'.");
        }
      } else {
        await openLegalLinkAndValidate(page, "Términos y Condiciones", "Términos y Condiciones", 8);
      }
    }

    // Step 9: Validate Política de Privacidad.
    {
      if (!prerequisitePassed(4)) {
        if (isStepStillSkipped(9)) {
          skip(9, "Omitido por falla al abrir 'Administrar Negocios'.");
        }
      } else {
        await openLegalLinkAndValidate(page, "Política de Privacidad", "Política de Privacidad", 9);
      }
    }
  } catch (error) {
    const unknownFailures = Array.from(stepResults.values()).filter((s) => s.status === "SKIPPED");
    if (unknownFailures.length) {
      const reason = `Error inesperado durante la ejecucion: ${error.message}`;
      for (const item of unknownFailures) {
        fail(item.id, ["Ejecucion interrumpida: FAIL"], [reason]);
      }
    }
  } finally {
    if (context) {
      await context.close().catch(() => {});
    }
    if (browser) {
      await browser.close().catch(() => {});
    }
  }

  const finalStatus = Object.fromEntries(
    stepDefinitions.map((definition) => {
      const raw = stepResults.get(definition.id).status;
      const normalized = raw === "PASS" ? "PASS" : "FAIL";
      return [definition.name, normalized];
    })
  );

  const report = {
    meta,
    finalStatus,
    steps: stepDefinitions.map((definition) => stepResults.get(definition.id)),
  };

  fs.writeFileSync(REPORT_JSON_PATH, JSON.stringify(report, null, 2));

  const lines = [
    "saleads_mi_negocio_full_test - Final Report",
    `Executed at: ${meta.executedAt}`,
    `Login URL: ${meta.loginUrlInput}`,
    `Headless: ${meta.headless}`,
    "",
    "Step Status:",
    ...stepDefinitions.map((definition) => {
      const result = stepResults.get(definition.id);
      const normalized = result.status === "PASS" ? "PASS" : "FAIL";
      return `- ${definition.id}. ${definition.name}: ${normalized}`;
    }),
    "",
    "Detailed Results:",
  ];

  for (const definition of stepDefinitions) {
    const step = stepResults.get(definition.id);
    const normalized = step.status === "PASS" ? "PASS" : "FAIL";
    lines.push(`\n[${step.id}] ${step.name} => ${normalized}`);
    if (step.checks.length) {
      lines.push("Checks:");
      step.checks.forEach((check) => lines.push(`  - ${check}`));
    }
    if (step.details.length) {
      lines.push("Details:");
      step.details.forEach((detail) => lines.push(`  - ${detail}`));
    }
    if (step.evidence.length) {
      lines.push("Evidence:");
      step.evidence.forEach((entry) => lines.push(`  - artifacts/${entry}`));
    }
  }

  fs.writeFileSync(REPORT_TXT_PATH, `${lines.join("\n")}\n`);

  const hasAnyFailure = Array.from(stepResults.values()).some((step) => step.status !== "PASS");
  if (hasAnyFailure) {
    console.error(`Workflow finished with failures. See ${REPORT_TXT_PATH}`);
    process.exitCode = 1;
  } else {
    console.log(`Workflow finished successfully. See ${REPORT_TXT_PATH}`);
  }
}

run();
