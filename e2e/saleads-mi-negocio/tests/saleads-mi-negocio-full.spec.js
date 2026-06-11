const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

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
  "Política de Privacidad"
];

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toSlug(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function isLocatorVisible(locator, timeout = 6000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

function candidateLocators(page, text) {
  const regex = new RegExp(escapeRegex(text), "i");
  const exactRegex = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");

  return [
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByRole("tab", { name: regex }),
    page.getByText(exactRegex),
    page.getByText(regex)
  ];
}

async function clickByVisibleText(page, texts) {
  for (const text of texts) {
    for (const locator of candidateLocators(page, text)) {
      if (await isLocatorVisible(locator, 2500)) {
        await locator.first().click();
        await waitForUi(page);
        return true;
      }
    }
  }

  return false;
}

async function hasVisibleText(page, text, timeout = 7000) {
  for (const locator of candidateLocators(page, text)) {
    if (await isLocatorVisible(locator, timeout)) {
      return true;
    }
  }

  return false;
}

async function isSidebarVisible(page) {
  const asideVisible = await isLocatorVisible(page.locator("aside"), 5000);
  const navVisible = await isLocatorVisible(page.locator("nav"), 5000);
  return asideVisible || navVisible;
}

async function findGooglePage(context, currentPage) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const currentUrl = currentPage.url() || "";
    if (currentUrl.includes("accounts.google.com")) {
      return currentPage;
    }

    for (const page of context.pages()) {
      const pageUrl = page.url() || "";
      if (pageUrl.includes("accounts.google.com")) {
        return page;
      }
    }

    await currentPage.waitForTimeout(1000);
  }

  return null;
}

async function selectGoogleAccountIfPrompted(context, appPage) {
  const googlePage = await findGooglePage(context, appPage);
  if (!googlePage) {
    return;
  }

  await googlePage.bringToFront();
  await waitForUi(googlePage);

  const accountLocator = googlePage.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i"));
  if (await isLocatorVisible(accountLocator, 10000)) {
    await accountLocator.first().click();
    await waitForUi(googlePage);
  }

  await appPage.bringToFront();
  await waitForUi(appPage);
}

function createReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = "FAIL";
  }
  return report;
}

function printFinalReport(report, evidence) {
  console.log("\n=== SaleADS Mi Negocio Full Workflow Report ===");
  for (const field of REPORT_FIELDS) {
    console.log(`${field}: ${report[field]}`);
  }
  console.log(`Términos y Condiciones URL: ${evidence.termsUrl || "N/A"}`);
  console.log(`Política de Privacidad URL: ${evidence.privacyUrl || "N/A"}`);
}

test("saleads_mi_negocio_full_test", async ({ browser }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  test.skip(
    !loginUrl,
    "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL) to the SaleADS login page of your environment."
  );

  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const screenshotDir = path.join(testInfo.config.outputDir, "..", "screenshots", timestamp);
  fs.mkdirSync(screenshotDir, { recursive: true });

  const capture = async (page, name, fullPage = false) => {
    const filePath = path.join(screenshotDir, `${toSlug(name)}.png`);
    await page.screenshot({ path: filePath, fullPage });
    return filePath;
  };

  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  const page = await context.newPage();

  const report = createReport();
  const evidence = {
    termsUrl: "",
    privacyUrl: ""
  };

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  // Step 1: Login with Google
  try {
    let loggedIn = await isSidebarVisible(page);
    if (!loggedIn) {
      const clickedLogin = await clickByVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Google"
      ]);
      if (clickedLogin) {
        await selectGoogleAccountIfPrompted(context, page);
      }

      for (let attempt = 0; attempt < 20 && !loggedIn; attempt += 1) {
        await waitForUi(page);
        loggedIn = await isSidebarVisible(page);
      }
    }

    const appVisible = (await isLocatorVisible(page.locator("main"), 5000)) || (await hasVisibleText(page, "Dashboard", 3000));
    report["Login"] = loggedIn && appVisible ? "PASS" : "FAIL";
    if (report["Login"] === "PASS") {
      await capture(page, "01-dashboard-loaded");
    }
  } catch {
    report["Login"] = "FAIL";
  }

  // Step 2: Open Mi Negocio menu
  try {
    await clickByVisibleText(page, ["Negocio"]);
    await clickByVisibleText(page, ["Mi Negocio"]);

    const hasAgregar = await hasVisibleText(page, "Agregar Negocio");
    const hasAdministrar = await hasVisibleText(page, "Administrar Negocios");
    report["Mi Negocio menu"] = hasAgregar && hasAdministrar ? "PASS" : "FAIL";

    if (report["Mi Negocio menu"] === "PASS") {
      await capture(page, "02-mi-negocio-menu-expanded");
    }
  } catch {
    report["Mi Negocio menu"] = "FAIL";
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const clickedAgregar = await clickByVisibleText(page, ["Agregar Negocio"]);
    if (clickedAgregar) {
      const modalTitleVisible = await hasVisibleText(page, "Crear Nuevo Negocio");
      const nombreInputVisible = await isLocatorVisible(
        page.getByRole("textbox", { name: /nombre del negocio/i }).or(page.getByPlaceholder(/nombre del negocio/i)),
        5000
      );
      const planTextVisible = await hasVisibleText(page, "Tienes 2 de 3 negocios");
      const cancelarVisible = await hasVisibleText(page, "Cancelar");
      const crearVisible = await hasVisibleText(page, "Crear Negocio");

      if (modalTitleVisible) {
        await capture(page, "03-agregar-negocio-modal");
      }

      if (nombreInputVisible) {
        const input = page.getByRole("textbox", { name: /nombre del negocio/i }).or(page.getByPlaceholder(/nombre del negocio/i)).first();
        await input.click();
        await input.fill("Negocio Prueba Automatización");
      }

      if (cancelarVisible) {
        await clickByVisibleText(page, ["Cancelar"]);
      }

      report["Agregar Negocio modal"] =
        modalTitleVisible && nombreInputVisible && planTextVisible && cancelarVisible && crearVisible ? "PASS" : "FAIL";
    } else {
      report["Agregar Negocio modal"] = "FAIL";
    }
  } catch {
    report["Agregar Negocio modal"] = "FAIL";
  }

  // Step 4: Open Administrar Negocios
  try {
    if (!(await hasVisibleText(page, "Administrar Negocios", 3000))) {
      await clickByVisibleText(page, ["Mi Negocio"]);
    }

    const clickedAdministrar = await clickByVisibleText(page, ["Administrar Negocios"]);
    if (clickedAdministrar) {
      const infoGeneral = await hasVisibleText(page, "Información General");
      const detallesCuenta = await hasVisibleText(page, "Detalles de la Cuenta");
      const tusNegocios = await hasVisibleText(page, "Tus Negocios");
      const seccionLegal = await hasVisibleText(page, "Sección Legal");

      if (infoGeneral || detallesCuenta || tusNegocios || seccionLegal) {
        await capture(page, "04-administrar-negocios-page", true);
      }

      report["Administrar Negocios view"] = infoGeneral && detallesCuenta && tusNegocios && seccionLegal ? "PASS" : "FAIL";
    } else {
      report["Administrar Negocios view"] = "FAIL";
    }
  } catch {
    report["Administrar Negocios view"] = "FAIL";
  }

  // Step 5: Validate Información General
  try {
    const accountText = await page.locator("body").innerText();
    const hasUserEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(accountText);
    const hasBusinessPlan = /business plan/i.test(accountText);
    const hasCambiarPlan = await hasVisibleText(page, "Cambiar Plan");
    const lines = accountText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const hasNameCandidate = lines.some((line) => {
      const lowered = line.toLowerCase();
      return (
        line.length >= 4 &&
        !line.includes("@") &&
        !lowered.includes("información general") &&
        !lowered.includes("business plan") &&
        !lowered.includes("cambiar plan")
      );
    });

    report["Información General"] = hasUserEmail && hasBusinessPlan && hasCambiarPlan && hasNameCandidate ? "PASS" : "FAIL";
  } catch {
    report["Información General"] = "FAIL";
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    const cuentaCreada = await hasVisibleText(page, "Cuenta creada");
    const estadoActivo = await hasVisibleText(page, "Estado activo");
    const idiomaSeleccionado = await hasVisibleText(page, "Idioma seleccionado");
    report["Detalles de la Cuenta"] = cuentaCreada && estadoActivo && idiomaSeleccionado ? "PASS" : "FAIL";
  } catch {
    report["Detalles de la Cuenta"] = "FAIL";
  }

  // Step 7: Validate Tus Negocios
  try {
    const hasTusNegociosHeading = await hasVisibleText(page, "Tus Negocios");
    const hasAgregarButton = await hasVisibleText(page, "Agregar Negocio");
    const hasQuotaText = await hasVisibleText(page, "Tienes 2 de 3 negocios");
    const hasBusinessList =
      (await isLocatorVisible(page.locator("[role='list'], ul, table").first(), 5000)) ||
      (await isLocatorVisible(page.locator("[class*='business'], [id*='business']").first(), 5000));
    report["Tus Negocios"] = hasTusNegociosHeading && hasAgregarButton && hasQuotaText && hasBusinessList ? "PASS" : "FAIL";
  } catch {
    report["Tus Negocios"] = "FAIL";
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const appUrlBefore = page.url();
    const termsLocator =
      page.getByRole("link", { name: /términos y condiciones|terminos y condiciones/i }).first().or(
        page.getByText(/términos y condiciones|terminos y condiciones/i).first()
      );
    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await termsLocator.click();
    const popup = await popupPromise;
    const legalPage = popup || page;
    await waitForUi(legalPage);

    const hasHeading = await hasVisibleText(legalPage, "Términos y Condiciones");
    const legalBody = await legalPage.locator("body").innerText();
    const hasLegalText = legalBody.replace(/\s+/g, " ").trim().length > 150;
    await capture(legalPage, "05-terminos-y-condiciones");
    evidence.termsUrl = legalPage.url();
    report["Términos y Condiciones"] = hasHeading && hasLegalText ? "PASS" : "FAIL";

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
  } catch {
    report["Términos y Condiciones"] = "FAIL";
  }

  // Step 9: Validate Política de Privacidad
  try {
    const appUrlBefore = page.url();
    const privacyLocator = page
      .getByRole("link", { name: /política de privacidad|politica de privacidad/i })
      .first()
      .or(page.getByText(/política de privacidad|politica de privacidad/i).first());
    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await privacyLocator.click();
    const popup = await popupPromise;
    const legalPage = popup || page;
    await waitForUi(legalPage);

    const hasHeading = await hasVisibleText(legalPage, "Política de Privacidad");
    const legalBody = await legalPage.locator("body").innerText();
    const hasLegalText = legalBody.replace(/\s+/g, " ").trim().length > 150;
    await capture(legalPage, "06-politica-de-privacidad");
    evidence.privacyUrl = legalPage.url();
    report["Política de Privacidad"] = hasHeading && hasLegalText ? "PASS" : "FAIL";

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
  } catch {
    report["Política de Privacidad"] = "FAIL";
  }

  printFinalReport(report, evidence);
  await testInfo.attach("saleads-final-report", {
    body: Buffer.from(JSON.stringify({ report, evidence, screenshots: screenshotDir }, null, 2)),
    contentType: "application/json"
  });

  const failedFields = REPORT_FIELDS.filter((field) => report[field] !== "PASS");
  expect(
    failedFields,
    `These validations failed: ${failedFields.length > 0 ? failedFields.join(", ") : "none"}`
  ).toEqual([]);

  await context.close();
});
