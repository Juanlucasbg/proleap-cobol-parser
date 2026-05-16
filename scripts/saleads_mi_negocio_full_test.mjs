import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { chromium } from "playwright";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const STEP_KEYS = [
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

function timestamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function buildOutputDir() {
  if (process.env.SALEADS_EVIDENCE_DIR) {
    return process.env.SALEADS_EVIDENCE_DIR;
  }

  return path.join(
    process.cwd(),
    "artifacts",
    "saleads_mi_negocio_full_test",
    timestamp()
  );
}

function createDefaultReport() {
  return STEP_KEYS.reduce((acc, key) => {
    acc[key] = "FAIL";
    return acc;
  }, {});
}

function stepPass(report, key) {
  report[key] = "PASS";
}

function stepFail(report, key, error) {
  report[key] = `FAIL - ${String(error?.message || error)}`;
}

async function waitAfterClick(page) {
  try {
    await page.waitForLoadState("networkidle", { timeout: 7000 });
  } catch {
    try {
      await page.waitForLoadState("domcontentloaded", { timeout: 3000 });
    } catch {
      // UI can be fully SPA-driven and still be stable.
    }
  }

  await page.waitForTimeout(700);
}

async function fileExists(targetPath) {
  try {
    await fs.access(targetPath);
    return true;
  } catch {
    return false;
  }
}

async function capture(page, outputDir, name, fullPage = false) {
  const safeName = name.replace(/\s+/g, "_").toLowerCase();
  const imagePath = path.join(outputDir, `${safeName}.png`);
  await page.screenshot({ path: imagePath, fullPage });
  return imagePath;
}

function asRegExpExact(text) {
  return new RegExp(`^${text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`, "i");
}

function asRegExpContains(text) {
  const pattern = text
    .trim()
    .split(/\s+/)
    .map((part) => part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join("\\s+");
  return new RegExp(pattern, "i");
}

async function firstVisibleLocator(locators, timeoutMs = 3000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      try {
        if (await locator.first().isVisible()) {
          return locator.first();
        }
      } catch {
        // Continue scanning.
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 200));
  }

  return null;
}

async function clickByVisibleText(page, textOptions) {
  for (const text of textOptions) {
    const containsMatcher = asRegExpContains(text);
    const locators = [
      page.getByRole("button", { name: asRegExpExact(text) }),
      page.getByRole("button", { name: containsMatcher }),
      page.getByRole("link", { name: asRegExpExact(text) }),
      page.getByRole("link", { name: containsMatcher }),
      page.getByRole("menuitem", { name: asRegExpExact(text) }),
      page.getByRole("menuitem", { name: containsMatcher }),
      page.getByRole("tab", { name: asRegExpExact(text) }),
      page.getByRole("tab", { name: containsMatcher }),
      page
        .locator("button, a, [role='button'], [role='menuitem'], [role='tab']")
        .filter({ hasText: containsMatcher })
    ];

    const target = await firstVisibleLocator(locators, 2500);
    if (target) {
      await target.click({ timeout: 4000 });
      await waitAfterClick(page);
      return text;
    }
  }

  throw new Error(`Unable to click any option from: ${textOptions.join(", ")}`);
}

async function expectVisibleText(page, text, timeout = 15000) {
  await page.getByText(asRegExpContains(text)).first().waitFor({ state: "visible", timeout });
}

async function expectSectionVisible(page, sectionName) {
  const candidates = [
    page.getByRole("heading", { name: asRegExpExact(sectionName) }),
    page.getByText(asRegExpExact(sectionName)),
    page.locator("section, div, article").filter({ hasText: asRegExpExact(sectionName) })
  ];
  const visible = await firstVisibleLocator(candidates, 10000);
  if (!visible) {
    throw new Error(`Section not visible: ${sectionName}`);
  }
}

async function checkAnyVisible(page, selectors, timeout = 12000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    for (const selector of selectors) {
      try {
        if (await page.locator(selector).first().isVisible()) {
          return true;
        }
      } catch {
        // Continue scanning.
      }
    }
    await page.waitForTimeout(250);
  }
  return false;
}

async function maybeSelectGoogleAccount(candidatePage) {
  const emailLocator = candidatePage.getByText(asRegExpExact(GOOGLE_ACCOUNT_EMAIL)).first();
  const emailAltLocator = candidatePage.getByRole("button", { name: asRegExpExact(GOOGLE_ACCOUNT_EMAIL) }).first();

  if (await emailLocator.isVisible().catch(() => false)) {
    await emailLocator.click();
    await waitAfterClick(candidatePage);
    return true;
  }

  if (await emailAltLocator.isVisible().catch(() => false)) {
    await emailAltLocator.click();
    await waitAfterClick(candidatePage);
    return true;
  }

  return false;
}

async function validateLegalLink({
  context,
  appPage,
  outputDir,
  linkText,
  headingText,
  screenshotName
}) {
  const existingPages = context.pages().length;
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickByVisibleText(appPage, [linkText]);

  let targetPage = await popupPromise;
  if (!targetPage && context.pages().length > existingPages) {
    targetPage = context.pages()[context.pages().length - 1];
  }
  if (!targetPage) {
    targetPage = appPage;
  }

  await targetPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await waitAfterClick(targetPage);

  await expectVisibleText(targetPage, headingText, 15000);

  const bodyText = await targetPage.locator("body").innerText();
  if (!bodyText || bodyText.trim().length < 120) {
    throw new Error(`Legal content appears too short for '${headingText}'.`);
  }

  await capture(targetPage, outputDir, screenshotName, false);
  const finalUrl = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.close().catch(() => {});
    await appPage.bringToFront();
    await waitAfterClick(appPage);
  }

  return finalUrl;
}

async function run() {
  const outputDir = buildOutputDir();
  await fs.mkdir(outputDir, { recursive: true });

  const report = createDefaultReport();
  const details = {
    evidenceDir: outputDir,
    termsAndConditionsUrl: null,
    privacyPolicyUrl: null,
    screenshots: {}
  };

  const headless = process.env.HEADLESS !== "false";
  const browser = await chromium.launch({ headless });
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 }
  });
  const page = await context.newPage();

  try {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || "";
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
      await waitAfterClick(page);
    }

    if (!loginUrl && page.url() === "about:blank") {
      throw new Error(
        "SALEADS_LOGIN_URL (or SALEADS_URL) is required when no initial page is open."
      );
    }

    // Step 1: Login with Google.
    try {
      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      let clickedGoogle = false;
      try {
        await clickByVisibleText(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Login with Google",
          "Google"
        ]);
        clickedGoogle = true;
      } catch {
        // Some environments show a login button first, then Google option.
      }

      if (!clickedGoogle) {
        await clickByVisibleText(page, ["Iniciar sesión", "Inicia sesión", "Login", "Sign in"]);
        await waitAfterClick(page);
        await clickByVisibleText(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Login with Google",
          "Google"
        ]);
      }

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
        await maybeSelectGoogleAccount(popup);
      } else {
        await maybeSelectGoogleAccount(page);
      }

      const appNavSignal = await firstVisibleLocator(
        [
          page.getByText(asRegExpContains("Mi Negocio")),
          page.getByText(asRegExpContains("Negocio")),
          page.getByText(asRegExpContains("Administrar Negocios")),
          page.getByText(asRegExpContains("Agregar Negocio"))
        ],
        20000
      );
      const sidebarVisible = await checkAnyVisible(page, ["aside", "[class*='sidebar']"], 8000);
      if (!appNavSignal || !sidebarVisible) {
        throw new Error("Authenticated app interface with left sidebar was not detected after login.");
      }

      details.screenshots.dashboard = await capture(page, outputDir, "01_dashboard_loaded", false);
      stepPass(report, "Login");
    } catch (error) {
      stepFail(report, "Login", error);
      throw error;
    }

    // Step 2: Open Mi Negocio menu.
    try {
      const sidebarToggleVisible = await firstVisibleLocator(
        [
          page.getByRole("button", { name: asRegExpContains("menu") }),
          page.getByRole("button", { name: asRegExpContains("abrir") }),
          page.getByRole("button", { name: asRegExpContains("sidebar") })
        ],
        2500
      );
      if (sidebarToggleVisible) {
        await sidebarToggleVisible.click().catch(() => {});
        await waitAfterClick(page);
      }

      let miNegocioOpened = false;
      try {
        await clickByVisibleText(page, ["Mi Negocio", "Mi negocio"]);
        miNegocioOpened = true;
      } catch {
        // Continue with parent-menu fallback.
      }

      if (!miNegocioOpened) {
        await clickByVisibleText(page, ["Negocio", "negocio"]);
        await clickByVisibleText(page, ["Mi Negocio", "Mi negocio"]);
      }

      await expectVisibleText(page, "Agregar Negocio");
      await expectVisibleText(page, "Administrar Negocios");

      details.screenshots.miNegocioMenu = await capture(page, outputDir, "02_mi_negocio_menu_expanded", false);
      stepPass(report, "Mi Negocio menu");
    } catch (error) {
      stepFail(report, "Mi Negocio menu", error);
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal.
    try {
      await clickByVisibleText(page, ["Agregar Negocio"]);

      await expectVisibleText(page, "Crear Nuevo Negocio");
      await expectVisibleText(page, "Nombre del Negocio");
      await expectVisibleText(page, "Tienes 2 de 3 negocios");
      await expectVisibleText(page, "Cancelar");
      await expectVisibleText(page, "Crear Negocio");

      const businessNameInput = page.getByLabel(asRegExpExact("Nombre del Negocio")).first();
      if (await businessNameInput.isVisible().catch(() => false)) {
        await businessNameInput.click();
        await businessNameInput.fill("Negocio Prueba Automatización");
      }

      details.screenshots.agregarNegocioModal = await capture(page, outputDir, "03_agregar_negocio_modal", false);
      await clickByVisibleText(page, ["Cancelar"]);
      stepPass(report, "Agregar Negocio modal");
    } catch (error) {
      stepFail(report, "Agregar Negocio modal", error);
      throw error;
    }

    // Step 4: Open Administrar Negocios.
    try {
      if (!(await page.getByText(asRegExpExact("Administrar Negocios")).first().isVisible().catch(() => false))) {
        await clickByVisibleText(page, ["Mi Negocio"]);
      }

      await clickByVisibleText(page, ["Administrar Negocios"]);

      await expectSectionVisible(page, "Información General");
      await expectSectionVisible(page, "Detalles de la Cuenta");
      await expectSectionVisible(page, "Tus Negocios");
      await expectSectionVisible(page, "Sección Legal");

      details.screenshots.administrarNegocios = await capture(page, outputDir, "04_administrar_negocios_page", true);
      stepPass(report, "Administrar Negocios view");
    } catch (error) {
      stepFail(report, "Administrar Negocios view", error);
      throw error;
    }

    // Step 5: Validate Información General.
    try {
      const planVisible = await page.getByText(/BUSINESS PLAN/i).first().isVisible({ timeout: 8000 }).catch(() => false);
      if (!planVisible) {
        throw new Error("BUSINESS PLAN text is not visible.");
      }

      await expectVisibleText(page, "Cambiar Plan");

      const hasEmail = (await page.locator("text=/[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}/").count()) > 0;
      if (!hasEmail) {
        throw new Error("User email is not visible in Información General.");
      }

      const fullText = await page.locator("body").innerText();
      const hasLikelyName = /[A-Za-zÁÉÍÓÚáéíóúñÑ]{2,}\s+[A-Za-zÁÉÍÓÚáéíóúñÑ]{2,}/.test(fullText);
      if (!hasLikelyName) {
        throw new Error("User name is not clearly visible in Información General.");
      }

      stepPass(report, "Información General");
    } catch (error) {
      stepFail(report, "Información General", error);
      throw error;
    }

    // Step 6: Validate Detalles de la Cuenta.
    try {
      await expectVisibleText(page, "Cuenta creada");
      await expectVisibleText(page, "Estado activo");
      await expectVisibleText(page, "Idioma seleccionado");
      stepPass(report, "Detalles de la Cuenta");
    } catch (error) {
      stepFail(report, "Detalles de la Cuenta", error);
      throw error;
    }

    // Step 7: Validate Tus Negocios.
    try {
      await expectSectionVisible(page, "Tus Negocios");
      await expectVisibleText(page, "Agregar Negocio");
      await expectVisibleText(page, "Tienes 2 de 3 negocios");
      stepPass(report, "Tus Negocios");
    } catch (error) {
      stepFail(report, "Tus Negocios", error);
      throw error;
    }

    // Step 8: Validate Términos y Condiciones.
    try {
      details.termsAndConditionsUrl = await validateLegalLink({
        context,
        appPage: page,
        outputDir,
        linkText: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        screenshotName: "05_terminos_y_condiciones"
      });
      stepPass(report, "Términos y Condiciones");
    } catch (error) {
      stepFail(report, "Términos y Condiciones", error);
      throw error;
    }

    // Step 9: Validate Política de Privacidad.
    try {
      details.privacyPolicyUrl = await validateLegalLink({
        context,
        appPage: page,
        outputDir,
        linkText: "Política de Privacidad",
        headingText: "Política de Privacidad",
        screenshotName: "06_politica_de_privacidad"
      });
      stepPass(report, "Política de Privacidad");
    } catch (error) {
      stepFail(report, "Política de Privacidad", error);
      throw error;
    }
  } finally {
    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      statusByStep: report,
      details
    };

    const reportPath = path.join(outputDir, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    if (await fileExists(reportPath)) {
      console.log(JSON.stringify(finalReport, null, 2));
      console.log(`Final report saved at: ${reportPath}`);
    }

    await browser.close();
  }
}

run().catch((error) => {
  console.error("saleads_mi_negocio_full_test failed:", error);
  process.exitCode = 1;
});
