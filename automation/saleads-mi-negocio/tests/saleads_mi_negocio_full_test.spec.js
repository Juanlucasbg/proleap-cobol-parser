const fs = require("node:fs/promises");
const path = require("node:path");
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

function createReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "NOT_RUN", details: "", evidence: [] };
    return acc;
  }, {});
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function isVisible(locator) {
  try {
    return await locator.first().isVisible();
  } catch (_error) {
    return false;
  }
}

async function textVisible(page, textOrRegex) {
  const regex =
    textOrRegex instanceof RegExp
      ? textOrRegex
      : new RegExp(`\\b${escapeRegex(textOrRegex)}\\b`, "i");

  const candidates = [
    page.getByRole("heading", { name: regex }).first(),
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByText(regex).first()
  ];

  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return true;
    }
  }

  return false;
}

async function clickByVisibleText(page, textOrRegex, options = {}) {
  const regex =
    textOrRegex instanceof RegExp
      ? textOrRegex
      : new RegExp(`^\\s*${escapeRegex(textOrRegex)}\\s*$`, "i");

  const candidates = [
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByRole("menuitem", { name: regex }).first(),
    page.getByRole("tab", { name: regex }).first(),
    page.locator("button, a, [role='button'], [role='menuitem'], [role='tab']").filter({ hasText: regex }).first(),
    page.getByText(regex).first()
  ];

  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      await candidate.click();
      await waitForUi(page);
      return true;
    }
  }

  if (options.optional) {
    return false;
  }

  throw new Error(`Could not find clickable element with text: ${regex.toString()}`);
}

async function captureCheckpoint(page, testInfo, checkpointName, fullPage = false) {
  const safeName = checkpointName.toLowerCase().replace(/[^a-z0-9-_]+/g, "-");
  const outputPath = testInfo.outputPath(`checkpoints/${safeName}.png`);
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function findSidebarPage(browserContext, timeoutMs = 60000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    const pages = browserContext.pages();
    for (const page of pages) {
      const sidebarLocator = page.locator("aside, nav").filter({ hasText: /Mi Negocio|Negocio/i }).first();
      if (await isVisible(sidebarLocator)) {
        return page;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }

  return null;
}

async function legalContentVisible(page) {
  const legalContent = page.locator("main p, article p, section p, p, li").first();
  return isVisible(legalContent);
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.slow();

  const report = createReport();
  const loginUrl = process.env.SALEADS_LOGIN_URL;

  const pass = (field, details = "", evidence = []) => {
    report[field] = { status: "PASS", details, evidence };
  };

  const fail = (field, details = "", evidence = []) => {
    report[field] = { status: "FAIL", details, evidence };
  };

  const skip = (field, details = "") => {
    report[field] = { status: "SKIPPED", details, evidence: [] };
  };

  let appPage = page;

  // Step 1 - Login with Google
  try {
    if (loginUrl) {
      await appPage.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(appPage);
    } else if (appPage.url() === "about:blank") {
      throw new Error(
        "SALEADS_LOGIN_URL is not set and browser is on about:blank. Provide SALEADS_LOGIN_URL or pre-open a login page."
      );
    }

    const alreadyLogged = await findSidebarPage(context, 3000);
    if (!alreadyLogged) {
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickByVisibleText(appPage, /sign in with google|google|continuar con google|iniciar sesi[oó]n/i);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
        const selectedInPopup = await clickByVisibleText(popup, GOOGLE_ACCOUNT_EMAIL, { optional: true });
        if (!selectedInPopup) {
          await clickByVisibleText(appPage, GOOGLE_ACCOUNT_EMAIL, { optional: true });
        }
      } else {
        await clickByVisibleText(appPage, GOOGLE_ACCOUNT_EMAIL, { optional: true });
      }
    }

    const sidebarPage = await findSidebarPage(context, 90000);
    if (!sidebarPage) {
      throw new Error("Main interface/sidebar did not appear after Google login.");
    }

    appPage = sidebarPage;
    const dashboardScreenshot = await captureCheckpoint(appPage, testInfo, "step1-dashboard");
    pass("Login", "Main application UI and left sidebar are visible.", [dashboardScreenshot]);
  } catch (error) {
    fail("Login", error.message || String(error));
  }

  // Step 2 - Open Mi Negocio menu
  if (report["Login"].status === "PASS") {
    try {
      await clickByVisibleText(appPage, "Negocio", { optional: true });
      await clickByVisibleText(appPage, "Mi Negocio");

      await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15000 });

      const menuScreenshot = await captureCheckpoint(appPage, testInfo, "step2-menu-expanded");
      pass("Mi Negocio menu", "Mi Negocio expanded and submenu options are visible.", [menuScreenshot]);
    } catch (error) {
      fail("Mi Negocio menu", error.message || String(error));
    }
  } else {
    skip("Mi Negocio menu", "Skipped because Login failed.");
  }

  // Step 3 - Validate Agregar Negocio modal
  if (report["Mi Negocio menu"].status === "PASS") {
    try {
      await clickByVisibleText(appPage, "Agregar Negocio");

      await expect(appPage.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByLabel(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 15000 });

      await appPage.getByLabel(/Nombre del Negocio/i).fill("Negocio Prueba Automatización");
      await clickByVisibleText(appPage, "Cancelar");

      const modalScreenshot = await captureCheckpoint(appPage, testInfo, "step3-agregar-negocio-modal");
      pass("Agregar Negocio modal", "Modal fields and action buttons were validated.", [modalScreenshot]);
    } catch (error) {
      fail("Agregar Negocio modal", error.message || String(error));
    }
  } else {
    skip("Agregar Negocio modal", "Skipped because Mi Negocio menu step failed.");
  }

  // Step 4 - Open Administrar Negocios
  if (report["Mi Negocio menu"].status === "PASS") {
    try {
      const adminVisible = await textVisible(appPage, /Administrar Negocios/i);
      if (!adminVisible) {
        await clickByVisibleText(appPage, "Mi Negocio", { optional: true });
      }

      await clickByVisibleText(appPage, "Administrar Negocios");

      await expect(appPage.getByText(/Información General/i).first()).toBeVisible({ timeout: 20000 });
      await expect(appPage.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
      await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
      await expect(appPage.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 20000 });

      const accountScreenshot = await captureCheckpoint(appPage, testInfo, "step4-administrar-negocios", true);
      pass("Administrar Negocios view", "All account sections are visible.", [accountScreenshot]);
    } catch (error) {
      fail("Administrar Negocios view", error.message || String(error));
    }
  } else {
    skip("Administrar Negocios view", "Skipped because Mi Negocio menu step failed.");
  }

  // Step 5 - Información General
  if (report["Administrar Negocios view"].status === "PASS") {
    try {
      const userNameVisible =
        (await textVisible(appPage, /juan|lucas|barbier|garzon/i)) ||
        (await textVisible(appPage, /nombre/i));
      const userEmailVisible = await textVisible(appPage, /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
      const planVisible = await textVisible(appPage, /BUSINESS PLAN/i);
      const changePlanVisible = await textVisible(appPage, /Cambiar Plan/i);

      expect(userNameVisible).toBeTruthy();
      expect(userEmailVisible).toBeTruthy();
      expect(planVisible).toBeTruthy();
      expect(changePlanVisible).toBeTruthy();

      pass("Información General", "User identity, plan label, and Cambiar Plan button are visible.");
    } catch (error) {
      fail("Información General", error.message || String(error));
    }
  } else {
    skip("Información General", "Skipped because Administrar Negocios view failed.");
  }

  // Step 6 - Detalles de la Cuenta
  if (report["Administrar Negocios view"].status === "PASS") {
    try {
      await expect(appPage.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 10000 });

      pass("Detalles de la Cuenta", "Cuenta creada, Estado activo e Idioma seleccionado are visible.");
    } catch (error) {
      fail("Detalles de la Cuenta", error.message || String(error));
    }
  } else {
    skip("Detalles de la Cuenta", "Skipped because Administrar Negocios view failed.");
  }

  // Step 7 - Tus Negocios
  if (report["Administrar Negocios view"].status === "PASS") {
    try {
      await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 10000 });

      const businessItemsVisible = await isVisible(appPage.locator("table tbody tr, ul li, [role='row']").first());
      expect(businessItemsVisible).toBeTruthy();

      pass("Tus Negocios", "Business list, Agregar Negocio button, and quota text are visible.");
    } catch (error) {
      fail("Tus Negocios", error.message || String(error));
    }
  } else {
    skip("Tus Negocios", "Skipped because Administrar Negocios view failed.");
  }

  // Step 8 - Términos y Condiciones
  if (report["Administrar Negocios view"].status === "PASS") {
    try {
      const beforeUrl = appPage.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickByVisibleText(appPage, /T[ée]rminos y Condiciones/i);
      const popup = await popupPromise;
      const legalPage = popup || appPage;

      await waitForUi(legalPage);
      await expect(legalPage.getByText(/T[ée]rminos y Condiciones/i).first()).toBeVisible({ timeout: 20000 });
      expect(await legalContentVisible(legalPage)).toBeTruthy();

      const termsScreenshot = await captureCheckpoint(legalPage, testInfo, "step8-terminos", true);
      const termsUrl = legalPage.url();
      pass("Términos y Condiciones", `Validated legal page URL: ${termsUrl}`, [termsScreenshot, termsUrl]);

      if (popup) {
        await popup.close();
        await appPage.bringToFront();
      } else if (appPage.url() !== beforeUrl) {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(appPage);
      }
    } catch (error) {
      fail("Términos y Condiciones", error.message || String(error));
    }
  } else {
    skip("Términos y Condiciones", "Skipped because Administrar Negocios view failed.");
  }

  // Step 9 - Política de Privacidad
  if (report["Administrar Negocios view"].status === "PASS") {
    try {
      const beforeUrl = appPage.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickByVisibleText(appPage, /Pol[íi]tica de Privacidad/i);
      const popup = await popupPromise;
      const legalPage = popup || appPage;

      await waitForUi(legalPage);
      await expect(legalPage.getByText(/Pol[íi]tica de Privacidad/i).first()).toBeVisible({ timeout: 20000 });
      expect(await legalContentVisible(legalPage)).toBeTruthy();

      const privacyScreenshot = await captureCheckpoint(legalPage, testInfo, "step9-privacidad", true);
      const privacyUrl = legalPage.url();
      pass("Política de Privacidad", `Validated legal page URL: ${privacyUrl}`, [privacyScreenshot, privacyUrl]);

      if (popup) {
        await popup.close();
        await appPage.bringToFront();
      } else if (appPage.url() !== beforeUrl) {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(appPage);
      }
    } catch (error) {
      fail("Política de Privacidad", error.message || String(error));
    }
  } else {
    skip("Política de Privacidad", "Skipped because Administrar Negocios view failed.");
  }

  const finalReportJson = JSON.stringify(report, null, 2);
  const finalReportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(finalReportPath, finalReportJson, "utf-8");
  await testInfo.attach("final-report", {
    body: Buffer.from(finalReportJson, "utf-8"),
    contentType: "application/json"
  });

  console.log("Final SaleADS Mi Negocio workflow report:");
  for (const field of REPORT_FIELDS) {
    const result = report[field];
    console.log(`- ${field}: ${result.status}${result.details ? ` (${result.details})` : ""}`);
  }

  const nonPassing = REPORT_FIELDS.filter((field) => report[field].status !== "PASS");
  expect(nonPassing, `Some workflow validations did not pass. See final report: ${finalReportPath}`).toEqual([]);
});
