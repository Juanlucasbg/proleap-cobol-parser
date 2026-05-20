import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const HEADLESS = process.env.HEADLESS === "true";
const START_URL = process.env.SALEADS_URL;
const OUT_DIR = process.env.SCREENSHOT_DIR ?? path.join("artifacts", "saleads-mi-negocio", new Date().toISOString().replace(/[:.]/g, "-"));

const report = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL"
};

const evidence = {
  screenshots: {},
  finalUrls: {}
};

function logStep(message) {
  console.log(`\n=== ${message} ===`);
}

function escapeForRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1200);
}

async function saveShot(page, name, fullPage = false) {
  await fs.mkdir(OUT_DIR, { recursive: true });
  const filePath = path.join(OUT_DIR, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  evidence.screenshots[name] = filePath;
  return filePath;
}

async function clickFirstVisible(page, patterns, options = {}) {
  const clickableLocators = [
    ...patterns.map((pattern) => page.getByRole("button", { name: pattern })),
    ...patterns.map((pattern) => page.getByRole("link", { name: pattern })),
    ...patterns.map((pattern) => page.getByText(pattern))
  ];

  for (const locator of clickableLocators) {
    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click(options);
      await waitForUi(page);
      return true;
    }
  }

  return false;
}

async function assertAnyVisible(page, patterns) {
  for (const pattern of patterns) {
    const locators = [
      page.getByRole("heading", { name: pattern }),
      page.getByRole("button", { name: pattern }),
      page.getByRole("link", { name: pattern }),
      page.getByText(pattern)
    ];

    for (const locator of locators) {
      if (await locator.first().isVisible().catch(() => false)) {
        return true;
      }
    }
  }

  return false;
}

async function clickAndCaptureTab(context, page, patterns) {
  const maybeNewPage = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
  const clicked = await clickFirstVisible(page, patterns);
  if (!clicked) {
    throw new Error(`No clickable element found for patterns: ${patterns.join(", ")}`);
  }

  const newPage = await maybeNewPage;
  if (newPage) {
    await newPage.waitForLoadState("domcontentloaded");
    await newPage.waitForTimeout(1200);
    return { page: newPage, openedInNewTab: true };
  }

  return { page, openedInNewTab: false };
}

async function run() {
  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    if (START_URL) {
      await page.goto(START_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else {
      console.log("SALEADS_URL not provided. Expecting manual navigation if running interactively.");
    }

    logStep("1) Login with Google");
    const loginPopupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    const loginClicked = await clickFirstVisible(page, [
      /sign in with google/i,
      /iniciar sesi[oó]n con google/i,
      /continuar con google/i,
      /google/i
    ]);
    if (!loginClicked) {
      throw new Error("Google login button was not found.");
    }

    const popup = await loginPopupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await popup.waitForTimeout(1000);

      const accountOption = popup.getByText(ACCOUNT_EMAIL).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }

      await waitForUi(popup);
      if (!popup.isClosed()) {
        await popup.close().catch(() => {});
      }
      await page.bringToFront();
    } else {
      const accountOption = page.getByText(ACCOUNT_EMAIL).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitForUi(page);
      }
    }

    const appLoaded = await assertAnyVisible(page, [/negocio/i, /mi negocio/i, /dashboard/i]);
    const sidebarVisible = await assertAnyVisible(page, [/negocio/i, /mi negocio/i, /administrar negocios/i]);
    if (!appLoaded || !sidebarVisible) {
      throw new Error("Main app interface or sidebar did not appear after login.");
    }
    report.Login = "PASS";
    await saveShot(page, "01-dashboard-loaded");

    logStep("2) Open Mi Negocio menu");
    const negocioClicked = await clickFirstVisible(page, [/negocio/i]);
    if (!negocioClicked) {
      throw new Error("Negocio menu entry was not found.");
    }

    const miNegocioClicked = await clickFirstVisible(page, [/mi negocio/i]);
    if (!miNegocioClicked) {
      throw new Error("Mi Negocio option was not found.");
    }

    const submenuVisible = await assertAnyVisible(page, [/agregar negocio/i, /administrar negocios/i]);
    if (!submenuVisible) {
      throw new Error("Mi Negocio submenu did not expand as expected.");
    }
    report["Mi Negocio menu"] = "PASS";
    await saveShot(page, "02-mi-negocio-menu-expanded");

    logStep("3) Validate Agregar Negocio modal");
    const agregarClicked = await clickFirstVisible(page, [/agregar negocio/i]);
    if (!agregarClicked) {
      throw new Error("Agregar Negocio option was not found.");
    }

    const modalChecks = await Promise.all([
      assertAnyVisible(page, [/crear nuevo negocio/i]),
      assertAnyVisible(page, [/nombre del negocio/i]),
      assertAnyVisible(page, [/tienes 2 de 3 negocios/i]),
      assertAnyVisible(page, [/cancelar/i, /crear negocio/i])
    ]);
    if (modalChecks.some((result) => !result)) {
      throw new Error("Agregar Negocio modal validation failed.");
    }
    await saveShot(page, "03-agregar-negocio-modal");

    const nombreInput = page.getByPlaceholder(/nombre del negocio/i).first();
    if (await nombreInput.isVisible().catch(() => false)) {
      await nombreInput.fill("Negocio Prueba Automatizacion");
    } else {
      const labelInput = page.getByLabel(/nombre del negocio/i).first();
      if (await labelInput.isVisible().catch(() => false)) {
        await labelInput.fill("Negocio Prueba Automatizacion");
      }
    }

    await clickFirstVisible(page, [/cancelar/i]);
    report["Agregar Negocio modal"] = "PASS";

    logStep("4) Open Administrar Negocios");
    await clickFirstVisible(page, [/mi negocio/i]);
    const administrarClicked = await clickFirstVisible(page, [/administrar negocios/i]);
    if (!administrarClicked) {
      throw new Error("Administrar Negocios option was not found.");
    }

    const accountSectionsOk = await Promise.all([
      assertAnyVisible(page, [/informaci[oó]n general/i, /informacion general/i]),
      assertAnyVisible(page, [/detalles de la cuenta/i]),
      assertAnyVisible(page, [/tus negocios/i]),
      assertAnyVisible(page, [/secci[oó]n legal/i, /seccion legal/i, /legal/i])
    ]);

    if (accountSectionsOk.some((result) => !result)) {
      throw new Error("Administrar Negocios sections are incomplete.");
    }
    report["Administrar Negocios view"] = "PASS";
    await saveShot(page, "04-administrar-negocios", true);

    logStep("5) Validate Informacion General");
    const emailHandle = ACCOUNT_EMAIL.split("@")[0] ?? "";
    const emailHandlePattern = emailHandle ? new RegExp(escapeForRegex(emailHandle), "i") : /@/i;
    const infoGeneralOk = await Promise.all([
      assertAnyVisible(page, [emailHandlePattern, /nombre/i, /usuario/i, /perfil/i]),
      assertAnyVisible(page, [/@/i]),
      assertAnyVisible(page, [/business plan/i]),
      assertAnyVisible(page, [/cambiar plan/i])
    ]);
    if (infoGeneralOk.some((result) => !result)) {
      throw new Error("Informacion General validation failed.");
    }
    report["Información General"] = "PASS";

    logStep("6) Validate Detalles de la Cuenta");
    const cuentaDetailsOk = await Promise.all([
      assertAnyVisible(page, [/cuenta creada/i]),
      assertAnyVisible(page, [/estado activo/i, /activo/i]),
      assertAnyVisible(page, [/idioma seleccionado/i, /idioma/i])
    ]);
    if (cuentaDetailsOk.some((result) => !result)) {
      throw new Error("Detalles de la Cuenta validation failed.");
    }
    report["Detalles de la Cuenta"] = "PASS";

    logStep("7) Validate Tus Negocios");
    const negociosOk = await Promise.all([
      assertAnyVisible(page, [/tus negocios/i, /negocios/i]),
      assertAnyVisible(page, [/agregar negocio/i]),
      assertAnyVisible(page, [/tienes 2 de 3 negocios/i])
    ]);
    if (negociosOk.some((result) => !result)) {
      throw new Error("Tus Negocios validation failed.");
    }
    report["Tus Negocios"] = "PASS";

    logStep("8) Validate Terminos y Condiciones");
    const terminosResult = await clickAndCaptureTab(context, page, [/t[eé]rminos y condiciones/i, /terminos y condiciones/i]);
    const terminosPage = terminosResult.page;
    const terminosOk = await Promise.all([
      assertAnyVisible(terminosPage, [/t[eé]rminos y condiciones/i, /terminos y condiciones/i]),
      assertAnyVisible(terminosPage, [/legal/i, /contenido/i, /condiciones/i, /privacidad/i])
    ]);
    if (terminosOk.some((result) => !result)) {
      throw new Error("Terminos y Condiciones page validation failed.");
    }
    evidence.finalUrls.terminos = terminosPage.url();
    await saveShot(terminosPage, "08-terminos-y-condiciones");
    report["Términos y Condiciones"] = "PASS";

    if (terminosResult.openedInNewTab) {
      await terminosPage.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }

    logStep("9) Validate Politica de Privacidad");
    const privacidadResult = await clickAndCaptureTab(context, page, [/pol[ií]tica de privacidad/i, /politica de privacidad/i]);
    const privacidadPage = privacidadResult.page;
    const privacidadOk = await Promise.all([
      assertAnyVisible(privacidadPage, [/pol[ií]tica de privacidad/i, /politica de privacidad/i]),
      assertAnyVisible(privacidadPage, [/legal/i, /privacidad/i, /datos/i])
    ]);
    if (privacidadOk.some((result) => !result)) {
      throw new Error("Politica de Privacidad page validation failed.");
    }
    evidence.finalUrls.privacidad = privacidadPage.url();
    await saveShot(privacidadPage, "09-politica-de-privacidad");
    report["Política de Privacidad"] = "PASS";

    if (privacidadResult.openedInNewTab) {
      await privacidadPage.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }

    logStep("10) Final Report");
    await fs.mkdir(OUT_DIR, { recursive: true });
    const finalPayload = { report, evidence, screenshotsDirectory: OUT_DIR };
    await fs.writeFile(path.join(OUT_DIR, "final-report.json"), JSON.stringify(finalPayload, null, 2));
    console.log(JSON.stringify(finalPayload, null, 2));
  } catch (error) {
    console.error("Workflow validation failed:", error?.message ?? error);
    await fs.mkdir(OUT_DIR, { recursive: true });
    const finalPayload = { report, evidence, screenshotsDirectory: OUT_DIR };
    await fs.writeFile(path.join(OUT_DIR, "final-report.json"), JSON.stringify(finalPayload, null, 2));
    console.log(JSON.stringify(finalPayload, null, 2));
    process.exitCode = 1;
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
}

run();
