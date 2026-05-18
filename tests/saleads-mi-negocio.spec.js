const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

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
const EMAIL_REGEX = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

const escapeRegex = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const normalizeSpace = (value) => value.replace(/\s+/g, " ").trim();

async function waitForUi(page, timeout = 12000) {
  try {
    await page.waitForLoadState("domcontentloaded", { timeout });
  } catch (_error) {
    // Some SPA transitions do not trigger new load states.
  }

  try {
    await page.waitForLoadState("networkidle", { timeout: Math.min(timeout, 8000) });
  } catch (_error) {
    // Do not fail the workflow if network remains busy.
  }

  await page.waitForTimeout(700);
}

async function findVisibleText(page, textOrRegex, timeout = 5000) {
  const textRegex =
    textOrRegex instanceof RegExp ? textOrRegex : new RegExp(escapeRegex(textOrRegex), "i");

  const candidates = [
    page.getByRole("button", { name: textRegex }).first(),
    page.getByRole("link", { name: textRegex }).first(),
    page.getByRole("menuitem", { name: textRegex }).first(),
    page.getByRole("heading", { name: textRegex }).first(),
    page.getByText(textRegex).first(),
  ];

  for (const locator of candidates) {
    if (await locator.isVisible({ timeout }).catch(() => false)) {
      return locator;
    }
  }

  return null;
}

async function clickByVisibleText(page, textOrRegex) {
  const locator = await findVisibleText(page, textOrRegex, 6000);
  if (!locator) {
    return false;
  }

  await locator.click({ timeout: 10000 });
  await waitForUi(page);
  return true;
}

async function isTextVisible(page, textOrRegex, timeout = 12000) {
  const locator = await findVisibleText(page, textOrRegex, timeout);
  return locator !== null;
}

async function takeCheckpoint(page, evidenceDir, name, fullPage = false) {
  const screenshotPath = path.join(evidenceDir, `${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function hasAnyLoginTrigger(page) {
  return (
    (await isTextVisible(
      page,
      /sign in with google|iniciar sesión con google|iniciar sesion con google|continuar con google|google/i,
      2000,
    )) ||
    (await isTextVisible(page, /iniciar sesión|iniciar sesion|login|sign in|entrar/i, 2000))
  );
}

async function tryNavigateToLogin(page) {
  const currentUrl = page.url();
  if (!currentUrl || currentUrl.startsWith("about:")) {
    return false;
  }

  const parsed = new URL(currentUrl);
  const candidateUrls = [`${parsed.origin}/login`, `${parsed.origin}/es/login`, `${parsed.origin}/en/login`];

  for (const candidate of candidateUrls) {
    await page.goto(candidate, { waitUntil: "domcontentloaded", timeout: 20000 }).catch(() => null);
    await waitForUi(page, 12000);
    if (await hasAnyLoginTrigger(page)) {
      return true;
    }
  }

  return false;
}

async function validateLegalPage({
  page,
  context,
  linkLabel,
  headingRegex,
  evidenceDir,
  screenshotName,
}) {
  const appUrlBefore = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const clicked = await clickByVisibleText(page, linkLabel);

  if (!clicked) {
    return { passed: false, finalUrl: null, reason: `No se encontró el enlace ${linkLabel}` };
  }

  const popup = await popupPromise;
  const targetPage = popup || page;
  await waitForUi(targetPage, 18000);

  const hasHeading = await isTextVisible(targetPage, headingRegex, 15000);
  const bodyText = normalizeSpace(await targetPage.locator("body").innerText());
  const hasLegalText = bodyText.length > 200;
  const finalUrl = targetPage.url();

  await takeCheckpoint(targetPage, evidenceDir, screenshotName, true);

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== appUrlBefore) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(page);
  }

  return {
    passed: hasHeading && hasLegalText,
    finalUrl,
    reason:
      hasHeading && hasLegalText
        ? "Validación legal correcta."
        : "No se encontró encabezado o contenido legal suficiente.",
  };
}

function inferUserNameVisible(pageText) {
  const lines = pageText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  const emailIdx = lines.findIndex((line) => EMAIL_REGEX.test(line));
  if (emailIdx <= 0) {
    return false;
  }

  const candidate = lines[emailIdx - 1];
  return !/información general|business plan|cambiar plan|detalles de la cuenta/i.test(candidate);
}

test("saleads_mi_negocio_full_test", async ({ playwright }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, false]));
  const errors = {};
  const evidenceDir = path.join(process.cwd(), "e2e-artifacts", "saleads-mi-negocio");
  await fs.mkdir(evidenceDir, { recursive: true });

  const cdpUrl = process.env.SALEADS_CDP_URL;
  const configuredStartUrl = process.env.SALEADS_START_URL || process.env.SALEADS_LOGIN_URL;
  const headless = process.env.SALEADS_HEADLESS !== "false";

  let browser;
  let context;
  let page;
  let closeBrowser = true;

  try {
    if (cdpUrl) {
      browser = await playwright.chromium.connectOverCDP(cdpUrl);
      const contexts = browser.contexts();
      context = contexts[0] || (await browser.newContext());
      page = context.pages()[0] || (await context.newPage());
      closeBrowser = false;
    } else {
      browser = await playwright.chromium.launch({ headless });
      context = await browser.newContext({
        viewport: { width: 1600, height: 1000 },
      });
      page = await context.newPage();
      if (configuredStartUrl) {
        await page.goto(configuredStartUrl, { waitUntil: "domcontentloaded" });
      }
    }

    await waitForUi(page, 15000);

    if (page.url() === "about:blank" && !configuredStartUrl && !cdpUrl) {
      throw new Error(
        "No hay URL inicial cargada. Define SALEADS_START_URL o usa SALEADS_CDP_URL con una pestaña ya abierta en login.",
      );
    }

    // Step 1: Login with Google and validate dashboard/sidebar.
    try {
      const alreadyInApp = await isTextVisible(
        page,
        /mi\s*negocio|negocio|my\s*business|business/i,
        5000,
      );
      if (!alreadyInApp) {
        let hasLoginSurface = await hasAnyLoginTrigger(page);
        if (!hasLoginSurface) {
          await clickByVisibleText(page, /iniciar sesión|iniciar sesion|login|sign in|entrar/i).catch(
            () => false,
          );
          await waitForUi(page, 12000);
          hasLoginSurface = await hasAnyLoginTrigger(page);
        }

        if (!hasLoginSurface) {
          await tryNavigateToLogin(page);
        }

        let clickedLogin = await clickByVisibleText(
          page,
          /sign in with google|iniciar sesión con google|iniciar sesion con google|continuar con google|google/i,
        );

        if (!clickedLogin) {
          clickedLogin = await clickByVisibleText(
            page,
            /continuar|continue|sign in|iniciar sesión|iniciar sesion/i,
          );
        }

        if (clickedLogin) {
          await clickByVisibleText(page, GOOGLE_ACCOUNT_EMAIL).catch(() => false);
          await waitForUi(page, 25000);
        }
      }

      const mainInterfaceVisible =
        (await isTextVisible(page, /dashboard|inicio|panel|mi\s*negocio|negocio/i, 25000)) ||
        (await page.locator("aside, nav").first().isVisible({ timeout: 12000 }).catch(() => false));
      const sidebarVisible = await isTextVisible(page, /negocio|mi\s*negocio/i, 20000);
      report["Login"] = mainInterfaceVisible && sidebarVisible;
      await takeCheckpoint(page, evidenceDir, "01-dashboard-loaded", true);
    } catch (error) {
      report["Login"] = false;
      errors["Login"] = String(error);
    }

    // Step 2: Open Mi Negocio menu and validate submenu entries.
    try {
      await clickByVisibleText(page, /negocio/i).catch(() => false);
      await clickByVisibleText(page, /mi\s*negocio/i);
      const hasAgregarNegocio = await isTextVisible(page, /agregar negocio/i, 12000);
      const hasAdministrarNegocios = await isTextVisible(page, /administrar negocios/i, 12000);
      report["Mi Negocio menu"] = hasAgregarNegocio && hasAdministrarNegocios;
      await takeCheckpoint(page, evidenceDir, "02-mi-negocio-menu-expanded");
    } catch (error) {
      report["Mi Negocio menu"] = false;
      errors["Mi Negocio menu"] = String(error);
    }

    // Step 3: Validate Agregar Negocio modal.
    try {
      await clickByVisibleText(page, /agregar negocio/i);
      const hasTitle = await isTextVisible(page, /crear nuevo negocio/i, 12000);
      const hasNombreInput = await isTextVisible(page, /nombre del negocio/i, 10000);
      const hasQuotaText = await isTextVisible(page, /tienes\s+2\s+de\s+3\s+negocios/i, 10000);
      const hasCancelar = await isTextVisible(page, /cancelar/i, 10000);
      const hasCrearNegocio = await isTextVisible(page, /crear negocio/i, 10000);
      report["Agregar Negocio modal"] =
        hasTitle && hasNombreInput && hasQuotaText && hasCancelar && hasCrearNegocio;

      await takeCheckpoint(page, evidenceDir, "03-agregar-negocio-modal");

      const nombreInput =
        (await findVisibleText(page, /nombre del negocio/i, 2000)) ||
        page.locator("input[placeholder*='Nombre'], input[name*='nombre']").first();
      if (await nombreInput.isVisible({ timeout: 3000 }).catch(() => false)) {
        await nombreInput.fill("Negocio Prueba Automatización");
      }
      await clickByVisibleText(page, /cancelar/i);
    } catch (error) {
      report["Agregar Negocio modal"] = false;
      errors["Agregar Negocio modal"] = String(error);
    }

    // Step 4: Open Administrar Negocios and validate account sections.
    try {
      if (!(await isTextVisible(page, /administrar negocios/i, 3000))) {
        await clickByVisibleText(page, /mi\s*negocio/i);
      }

      await clickByVisibleText(page, /administrar negocios/i);

      const hasInfoGeneral = await isTextVisible(page, /información general/i, 15000);
      const hasDetallesCuenta = await isTextVisible(page, /detalles de la cuenta/i, 12000);
      const hasTusNegocios = await isTextVisible(page, /tus negocios/i, 12000);
      const hasSeccionLegal = await isTextVisible(page, /sección legal|seccion legal/i, 12000);
      report["Administrar Negocios view"] =
        hasInfoGeneral && hasDetallesCuenta && hasTusNegocios && hasSeccionLegal;
      await takeCheckpoint(page, evidenceDir, "04-administrar-negocios-page", true);
    } catch (error) {
      report["Administrar Negocios view"] = false;
      errors["Administrar Negocios view"] = String(error);
    }

    // Step 5: Validate Información General.
    try {
      const pageText = await page.locator("body").innerText();
      const hasUserName = inferUserNameVisible(pageText);
      const hasUserEmail = EMAIL_REGEX.test(pageText);
      const hasPlan = /BUSINESS PLAN/i.test(pageText);
      const hasCambiarPlan = /Cambiar Plan/i.test(pageText);
      report["Información General"] = hasUserName && hasUserEmail && hasPlan && hasCambiarPlan;
    } catch (error) {
      report["Información General"] = false;
      errors["Información General"] = String(error);
    }

    // Step 6: Validate Detalles de la Cuenta.
    try {
      const hasCuentaCreada = await isTextVisible(page, /cuenta creada/i, 10000);
      const hasEstadoActivo = await isTextVisible(page, /estado activo/i, 10000);
      const hasIdiomaSeleccionado = await isTextVisible(page, /idioma seleccionado/i, 10000);
      report["Detalles de la Cuenta"] = hasCuentaCreada && hasEstadoActivo && hasIdiomaSeleccionado;
    } catch (error) {
      report["Detalles de la Cuenta"] = false;
      errors["Detalles de la Cuenta"] = String(error);
    }

    // Step 7: Validate Tus Negocios.
    try {
      const hasBusinessList = await isTextVisible(page, /tus negocios|negocios/i, 10000);
      const hasAgregarNegocioBtn = await isTextVisible(page, /agregar negocio/i, 10000);
      const hasQuotaText = await isTextVisible(page, /tienes\s+2\s+de\s+3\s+negocios/i, 10000);
      report["Tus Negocios"] = hasBusinessList && hasAgregarNegocioBtn && hasQuotaText;
    } catch (error) {
      report["Tus Negocios"] = false;
      errors["Tus Negocios"] = String(error);
    }

    // Step 8: Validate Términos y Condiciones.
    try {
      const termsValidation = await validateLegalPage({
        page,
        context,
        linkLabel: /términos y condiciones|terminos y condiciones/i,
        headingRegex: /términos y condiciones|terminos y condiciones/i,
        evidenceDir,
        screenshotName: "08-terminos-y-condiciones",
      });
      report["Términos y Condiciones"] = termsValidation.passed;
      if (termsValidation.finalUrl) {
        console.log(`Términos y Condiciones URL: ${termsValidation.finalUrl}`);
      }
    } catch (error) {
      report["Términos y Condiciones"] = false;
      errors["Términos y Condiciones"] = String(error);
    }

    // Step 9: Validate Política de Privacidad.
    try {
      const privacyValidation = await validateLegalPage({
        page,
        context,
        linkLabel: /política de privacidad|politica de privacidad/i,
        headingRegex: /política de privacidad|politica de privacidad/i,
        evidenceDir,
        screenshotName: "09-politica-de-privacidad",
      });
      report["Política de Privacidad"] = privacyValidation.passed;
      if (privacyValidation.finalUrl) {
        console.log(`Política de Privacidad URL: ${privacyValidation.finalUrl}`);
      }
    } catch (error) {
      report["Política de Privacidad"] = false;
      errors["Política de Privacidad"] = String(error);
    }

    // Step 10: Final report.
    const summary = {
      reportName: "saleads_mi_negocio_full_test",
      executionUrl: page.url(),
      evidenceDirectory: evidenceDir,
      results: report,
      errors,
    };

    console.log("=== SaleADS Mi Negocio Final Report ===");
    for (const field of REPORT_FIELDS) {
      console.log(`${field}: ${report[field] ? "PASS" : "FAIL"}`);
    }
    console.log(`Evidence directory: ${evidenceDir}`);
    console.log(`Final report JSON: ${JSON.stringify(summary, null, 2)}`);

    await testInfo.attach("saleads-mi-negocio-report", {
      body: JSON.stringify(summary, null, 2),
      contentType: "application/json",
    });

    const failedFields = REPORT_FIELDS.filter((field) => !report[field]);
    expect(
      failedFields,
      `Workflow validation failures: ${failedFields.join(", ") || "none"}\n` +
        JSON.stringify(summary, null, 2),
    ).toEqual([]);
  } finally {
    if (closeBrowser && browser) {
      await browser.close();
    } else if (browser && cdpUrl) {
      await browser.close().catch(() => undefined);
    }
  }
});
