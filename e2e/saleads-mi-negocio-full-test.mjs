import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const runId = new Date().toISOString().replaceAll(":", "-").replaceAll(".", "-");
const artifactsDir = path.join(scriptDir, "artifacts", runId);
const finalReportPath = path.join(artifactsDir, "final-report.json");

fs.mkdirSync(artifactsDir, { recursive: true });

const results = Object.fromEntries(
  REPORT_FIELDS.map((field) => [
    field,
    { status: "FAIL", details: "Not executed.", evidence: [] },
  ]),
);

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function normalizeText(text) {
  return text
    .normalize("NFD")
    .replaceAll(/\p{M}/gu, "")
    .toLowerCase();
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

function setPass(field, details) {
  results[field].status = "PASS";
  results[field].details = details;
}

function setFail(field, details) {
  results[field].status = "FAIL";
  results[field].details = details;
}

function setPrerequisiteFail(field, prerequisite) {
  setFail(field, `Prerequisite failed: ${prerequisite}.`);
}

function addEvidence(field, evidence) {
  results[field].evidence.push(evidence);
}

async function saveScreenshot(page, fileName, field, fullPage = false) {
  const fullPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: fullPath, fullPage });
  addEvidence(field, { type: "screenshot", path: path.relative(scriptDir, fullPath) });
}

function candidateLocatorsByText(page, text) {
  const regex = new RegExp(escapeRegExp(text), "i");
  return [
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByRole("tab", { name: regex }),
    page.getByRole("heading", { name: regex }),
    page.getByText(regex),
  ];
}

async function isTextVisible(page, text) {
  for (const locator of candidateLocatorsByText(page, text)) {
    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return true;
    }
  }
  return false;
}

async function clickFirstVisible(page, texts, options = {}) {
  const waitAfterClick = options.waitAfterClick ?? true;
  for (const text of texts) {
    for (const locator of candidateLocatorsByText(page, text)) {
      const first = locator.first();
      if (await first.isVisible().catch(() => false)) {
        try {
          await first.click({ timeout: 4000 });
          if (waitAfterClick) {
            await waitForUiLoad(page);
          }
          return text;
        } catch {
          // Try next matching locator if this node is not clickable.
        }
      }
    }
  }
  return null;
}

async function getSectionText(page, sectionHeading) {
  const section = page.locator("section").filter({
    has: page.getByRole("heading", { name: new RegExp(escapeRegExp(sectionHeading), "i") }),
  });
  if (await section.first().isVisible().catch(() => false)) {
    return section.first().innerText();
  }
  const bodyText = await page.locator("body").innerText().catch(() => "");
  const normalizedHeading = normalizeText(sectionHeading);
  const lines = bodyText.split("\n");
  const headingIndex = lines.findIndex((line) => normalizeText(line).includes(normalizedHeading));
  if (headingIndex < 0) {
    return "";
  }
  return lines.slice(headingIndex, Math.min(lines.length, headingIndex + 25)).join("\n");
}

async function validateLegalLink({
  appPage,
  linkText,
  headingText,
  field,
  screenshotName,
}) {
  const popupPromise = appPage.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
  const originalUrl = appPage.url();
  const clicked = await clickFirstVisible(appPage, [linkText], { waitAfterClick: false });
  if (!clicked) {
    setFail(field, `Unable to click "${linkText}" link.`);
    return;
  }

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;
  await waitForUiLoad(targetPage);

  const headingVisible = await isTextVisible(targetPage, headingText);
  const bodyText = await targetPage.locator("body").innerText().catch(() => "");
  const legalContentVisible = bodyText.trim().length > 180;
  const finalUrl = targetPage.url();

  addEvidence(field, { type: "url", value: finalUrl });
  await saveScreenshot(targetPage, screenshotName, field, true);

  if (headingVisible && legalContentVisible) {
    setPass(field, `"${headingText}" heading and legal content are visible.`);
  } else {
    setFail(
      field,
      `Validation failed for "${linkText}". headingVisible=${headingVisible}, legalContentVisible=${legalContentVisible}.`,
    );
  }

  if (popup) {
    await popup.close().catch(() => undefined);
    await appPage.bringToFront().catch(() => undefined);
    await waitForUiLoad(appPage);
  } else if (appPage.url() !== originalUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiLoad(appPage);
  }
}

function failDownstreamFrom(index, prerequisiteField) {
  for (let i = index; i < REPORT_FIELDS.length; i += 1) {
    if (results[REPORT_FIELDS[i]].details === "Not executed.") {
      setPrerequisiteFail(REPORT_FIELDS[i], prerequisiteField);
    }
  }
}

async function run() {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.SALEADS_URL || "";
  const headless = process.env.HEADLESS !== "false";

  let browser;
  let context;
  let page;

  try {
    browser = await chromium.launch({ headless });
    context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    page = await context.newPage();

    if (!loginUrl) {
      setFail(
        "Login",
        "Missing SALEADS_LOGIN_URL (or SALEADS_BASE_URL / SALEADS_URL). Provide the current environment login page URL.",
      );
      failDownstreamFrom(1, "Login");
      return;
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await waitForUiLoad(page);

    // Step 1: Login with Google
    const popupPromise = page.waitForEvent("popup", { timeout: 9000 }).catch(() => null);
    const clickedLogin = await clickFirstVisible(
      page,
      [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Google",
        "Iniciar sesión",
      ],
      { waitAfterClick: false },
    );

    if (!clickedLogin) {
      setFail("Login", "Login button was not found.");
      failDownstreamFrom(1, "Login");
      return;
    }

    const popup = await popupPromise;
    const authPage = popup ?? page;
    await waitForUiLoad(authPage);

    const onGoogleAuth = authPage.url().includes("accounts.google.com");
    if (onGoogleAuth) {
      await clickFirstVisible(authPage, [GOOGLE_ACCOUNT_EMAIL], { waitAfterClick: false }).catch(
        () => undefined,
      );
      await waitForUiLoad(authPage);
    }

    if (popup) {
      await popup.waitForClose({ timeout: 45000 }).catch(() => undefined);
      await page.bringToFront().catch(() => undefined);
    }

    await waitForUiLoad(page);

    const mainInterfaceVisible =
      (await isTextVisible(page, "Dashboard")) ||
      (await isTextVisible(page, "Inicio")) ||
      (await isTextVisible(page, "Negocio")) ||
      (await isTextVisible(page, "Mi Negocio"));
    const sidebarVisible =
      (await page.locator("aside").first().isVisible().catch(() => false)) ||
      (await page.getByRole("navigation").first().isVisible().catch(() => false));

    if (mainInterfaceVisible && sidebarVisible) {
      setPass("Login", "Main application interface and left sidebar are visible.");
      await saveScreenshot(page, "01-dashboard-loaded.png", "Login", true);
    } else {
      setFail(
        "Login",
        `Main application interface validation failed. mainInterfaceVisible=${mainInterfaceVisible}, sidebarVisible=${sidebarVisible}.`,
      );
      failDownstreamFrom(1, "Login");
      return;
    }

    // Step 2: Open Mi Negocio menu
    await clickFirstVisible(page, ["Negocio"], { waitAfterClick: true }).catch(() => undefined);
    const clickedMiNegocio = await clickFirstVisible(page, ["Mi Negocio"], { waitAfterClick: true });
    const agregarVisible = await isTextVisible(page, "Agregar Negocio");
    const administrarVisible = await isTextVisible(page, "Administrar Negocios");

    if (clickedMiNegocio && agregarVisible && administrarVisible) {
      setPass(
        "Mi Negocio menu",
        "Mi Negocio menu expanded with Agregar Negocio and Administrar Negocios visible.",
      );
      await saveScreenshot(page, "02-mi-negocio-menu-expanded.png", "Mi Negocio menu", true);
    } else {
      setFail(
        "Mi Negocio menu",
        `Menu validation failed. clickedMiNegocio=${Boolean(
          clickedMiNegocio,
        )}, agregarVisible=${agregarVisible}, administrarVisible=${administrarVisible}.`,
      );
      failDownstreamFrom(2, "Mi Negocio menu");
      return;
    }

    // Step 3: Validate Agregar Negocio modal
    const clickedAgregarNegocio = await clickFirstVisible(page, ["Agregar Negocio"], {
      waitAfterClick: true,
    });
    const modalTitleVisible = await isTextVisible(page, "Crear Nuevo Negocio");
    const nombreInputVisible =
      (await page.getByLabel(/Nombre del Negocio/i).first().isVisible().catch(() => false)) ||
      (await page.getByPlaceholder(/Nombre del Negocio/i).first().isVisible().catch(() => false));
    const quotaTextVisible = await isTextVisible(page, "Tienes 2 de 3 negocios");
    const cancelarVisible = await isTextVisible(page, "Cancelar");
    const crearNegocioVisible = await isTextVisible(page, "Crear Negocio");

    if (
      clickedAgregarNegocio &&
      modalTitleVisible &&
      nombreInputVisible &&
      quotaTextVisible &&
      cancelarVisible &&
      crearNegocioVisible
    ) {
      setPass(
        "Agregar Negocio modal",
        "Modal shows title, input, quota text, and action buttons as expected.",
      );
    } else {
      setFail(
        "Agregar Negocio modal",
        `Modal validation failed. title=${modalTitleVisible}, input=${nombreInputVisible}, quota=${quotaTextVisible}, cancelar=${cancelarVisible}, crear=${crearNegocioVisible}.`,
      );
    }

    if (await page.getByLabel(/Nombre del Negocio/i).first().isVisible().catch(() => false)) {
      await page
        .getByLabel(/Nombre del Negocio/i)
        .first()
        .fill("Negocio Prueba Automatización")
        .catch(() => undefined);
    } else if (
      await page.getByPlaceholder(/Nombre del Negocio/i).first().isVisible().catch(() => false)
    ) {
      await page
        .getByPlaceholder(/Nombre del Negocio/i)
        .first()
        .fill("Negocio Prueba Automatización")
        .catch(() => undefined);
    }

    if (modalTitleVisible) {
      await saveScreenshot(page, "03-agregar-negocio-modal.png", "Agregar Negocio modal", true);
      await clickFirstVisible(page, ["Cancelar"], { waitAfterClick: true }).catch(() => undefined);
    }

    // Step 4: Open Administrar Negocios
    if (!(await isTextVisible(page, "Administrar Negocios"))) {
      await clickFirstVisible(page, ["Mi Negocio"], { waitAfterClick: true }).catch(() => undefined);
    }
    const clickedAdministrar = await clickFirstVisible(page, ["Administrar Negocios"], {
      waitAfterClick: true,
    });

    const informacionGeneralVisible = await isTextVisible(page, "Información General");
    const detallesCuentaVisible = await isTextVisible(page, "Detalles de la Cuenta");
    const tusNegociosVisible = await isTextVisible(page, "Tus Negocios");
    const seccionLegalVisible = await isTextVisible(page, "Sección Legal");

    if (
      clickedAdministrar &&
      informacionGeneralVisible &&
      detallesCuentaVisible &&
      tusNegociosVisible &&
      seccionLegalVisible
    ) {
      setPass(
        "Administrar Negocios view",
        "Administrar Negocios page loaded with all required sections visible.",
      );
      await saveScreenshot(page, "04-administrar-negocios-view.png", "Administrar Negocios view", true);
    } else {
      setFail(
        "Administrar Negocios view",
        `Administrar Negocios validation failed. informaciónGeneral=${informacionGeneralVisible}, detallesCuenta=${detallesCuentaVisible}, tusNegocios=${tusNegociosVisible}, seccionLegal=${seccionLegalVisible}.`,
      );
      failDownstreamFrom(4, "Administrar Negocios view");
      return;
    }

    // Step 5: Validate Información General
    const infoGeneralText = await getSectionText(page, "Información General");
    const userEmailVisible = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoGeneralText);
    const infoLines = infoGeneralText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const userNameVisible = infoLines.some(
      (line) =>
        /^[\p{L}\s.'-]{3,}$/u.test(line) &&
        !line.includes("@") &&
        !/información general|business plan|cambiar plan/i.test(line),
    );
    const businessPlanVisible = /business plan/i.test(infoGeneralText);
    const cambiarPlanVisible = await isTextVisible(page, "Cambiar Plan");

    if (userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible) {
      setPass(
        "Información General",
        "User name, user email, BUSINESS PLAN and Cambiar Plan are visible.",
      );
    } else {
      setFail(
        "Información General",
        `Validation failed. userNameVisible=${userNameVisible}, userEmailVisible=${userEmailVisible}, businessPlanVisible=${businessPlanVisible}, cambiarPlanVisible=${cambiarPlanVisible}.`,
      );
    }

    // Step 6: Validate Detalles de la Cuenta
    const detallesText = await getSectionText(page, "Detalles de la Cuenta");
    const cuentaCreadaVisible = /cuenta creada/i.test(detallesText);
    const estadoActivoVisible = /estado activo/i.test(detallesText);
    const idiomaSeleccionadoVisible = /idioma seleccionado/i.test(detallesText);

    if (cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible) {
      setPass(
        "Detalles de la Cuenta",
        "Cuenta creada, Estado activo and Idioma seleccionado are visible.",
      );
    } else {
      setFail(
        "Detalles de la Cuenta",
        `Validation failed. cuentaCreadaVisible=${cuentaCreadaVisible}, estadoActivoVisible=${estadoActivoVisible}, idiomaSeleccionadoVisible=${idiomaSeleccionadoVisible}.`,
      );
    }

    // Step 7: Validate Tus Negocios
    const tusNegociosText = await getSectionText(page, "Tus Negocios");
    const businessListVisible =
      (await page
        .locator("section")
        .filter({ hasText: /Tus Negocios/i })
        .locator("li")
        .first()
        .isVisible()
        .catch(() => false)) || tusNegociosText.split("\n").filter((line) => line.trim()).length >= 4;
    const agregarNegocioButtonVisible = await isTextVisible(page, "Agregar Negocio");
    const negociosQuotaVisible = /tienes 2 de 3 negocios/i.test(tusNegociosText);

    if (businessListVisible && agregarNegocioButtonVisible && negociosQuotaVisible) {
      setPass(
        "Tus Negocios",
        "Business list, Agregar Negocio button and quota text are visible.",
      );
    } else {
      setFail(
        "Tus Negocios",
        `Validation failed. businessListVisible=${businessListVisible}, agregarNegocioButtonVisible=${agregarNegocioButtonVisible}, negociosQuotaVisible=${negociosQuotaVisible}.`,
      );
    }

    // Step 8: Validate Términos y Condiciones
    await validateLegalLink({
      appPage: page,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      field: "Términos y Condiciones",
      screenshotName: "08-terminos-y-condiciones.png",
    });

    // Step 9: Validate Política de Privacidad
    await validateLegalLink({
      appPage: page,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      field: "Política de Privacidad",
      screenshotName: "09-politica-de-privacidad.png",
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (results.Login.details === "Not executed.") {
      setFail("Login", `Unexpected runtime error: ${message}`);
      failDownstreamFrom(1, "Login");
    }
  } finally {
    if (page) {
      await page.close().catch(() => undefined);
    }
    if (context) {
      await context.close().catch(() => undefined);
    }
    if (browser) {
      await browser.close().catch(() => undefined);
    }
  }
}

await run();

const finalReport = {
  name: "saleads_mi_negocio_full_test",
  goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
  generatedAt: new Date().toISOString(),
  overallStatus: REPORT_FIELDS.every((field) => results[field].status === "PASS") ? "PASS" : "FAIL",
  results,
};

fs.writeFileSync(finalReportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
console.log(`Final report written to ${finalReportPath}`);
console.log(JSON.stringify(finalReport, null, 2));

if (finalReport.overallStatus === "FAIL") {
  process.exitCode = 1;
}
