const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const REPORT_DIR = path.resolve(__dirname, "../artifacts/reports");
const SCREENSHOT_DIR = path.resolve(__dirname, "../artifacts/screenshots");

const VALIDATION_FIELDS = [
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

const APP_LOAD_TIMEOUT_MS = 25_000;
const LEGAL_LOAD_TIMEOUT_MS = 35_000;

function nowIsoSafe() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function normalizeWhitespace(value) {
  if (!value) {
    return "";
  }

  return value.replace(/\s+/g, " ").trim();
}

function textRegex(text) {
  return new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
}

function containsRegex(text) {
  return new RegExp(escapeRegex(text), "i");
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: APP_LOAD_TIMEOUT_MS });
  await page.waitForLoadState("networkidle", { timeout: APP_LOAD_TIMEOUT_MS }).catch(() => {
    // Some apps keep background polling forever. DOM readiness is sufficient in that case.
  });
  await page.waitForTimeout(650);
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function capture(page, name) {
  await ensureDir(SCREENSHOT_DIR);
  const filePath = path.join(SCREENSHOT_DIR, `${name}-${nowIsoSafe()}.png`);
  await page.screenshot({ path: filePath, fullPage: true });
  return filePath;
}

async function clickByVisibleText(page, text, options = {}) {
  const exact = options.exact !== false;
  const timeout = options.timeout ?? APP_LOAD_TIMEOUT_MS;
  const regex = exact ? textRegex(text) : containsRegex(text);

  const candidates = [
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByText(regex)
  ];

  for (const locator of candidates) {
    const count = await locator.count();
    if (count > 0) {
      await locator.first().waitFor({ state: "visible", timeout });
      await locator.first().click({ timeout });
      await waitForUiToLoad(page);
      return true;
    }
  }

  return false;
}

async function ensureSidebar(page) {
  const sidebarCandidates = [
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator("[data-testid*='sidebar']"),
    page.locator("[class*='sidebar']")
  ];

  for (const locator of sidebarCandidates) {
    if ((await locator.count()) > 0) {
      await expect(locator.first()).toBeVisible({ timeout: APP_LOAD_TIMEOUT_MS });
      return;
    }
  }

  throw new Error("Left sidebar navigation was not detected.");
}

async function maybeHandleGoogleAccountPicker(page) {
  const accountEmail = "juanlucasbarbiergarzon@gmail.com";

  const pageContext = page.context();
  const accountPopupPromise = pageContext.waitForEvent("page", { timeout: 12_000 }).catch(() => null);

  // Wait a moment for either same-page redirect or popup creation.
  await page.waitForTimeout(1200);
  const popup = await accountPopupPromise;

  const possibleGooglePages = [page, popup].filter(Boolean);
  for (const activePage of possibleGooglePages) {
    await activePage.waitForLoadState("domcontentloaded", { timeout: 12_000 }).catch(() => {
      // Ignore; we still try selectors if possible.
    });

    const accountChip = activePage.getByText(new RegExp(escapeRegex(accountEmail), "i"));
    if ((await accountChip.count()) > 0) {
      await accountChip.first().click({ timeout: 10_000 });
      await waitForUiToLoad(activePage);
      return;
    }

    const useAnotherAccount = activePage.getByText(/use another account|usar otra cuenta/i);
    if ((await useAnotherAccount.count()) > 0) {
      // Not expected for this flow; leave explicit error for better diagnosis.
      throw new Error("Google picker asked for another account; expected pre-existing account chip.");
    }
  }
}

async function clickTextWithPopupSupport(page, text) {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  const clicked = await clickByVisibleText(page, text, { exact: true });

  if (!clicked) {
    throw new Error(`Unable to click visible text "${text}".`);
  }

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: LEGAL_LOAD_TIMEOUT_MS });
    await popup.waitForLoadState("networkidle", { timeout: LEGAL_LOAD_TIMEOUT_MS }).catch(() => {});
    await popup.waitForTimeout(600);
    return { targetPage: popup, openedInNewTab: true };
  }

  await page.waitForLoadState("domcontentloaded", { timeout: LEGAL_LOAD_TIMEOUT_MS });
  await page.waitForLoadState("networkidle", { timeout: LEGAL_LOAD_TIMEOUT_MS }).catch(() => {});
  await page.waitForTimeout(600);
  return { targetPage: page, openedInNewTab: false };
}

async function writeFinalReport(report) {
  await ensureDir(REPORT_DIR);
  const filePath = path.join(REPORT_DIR, `saleads_mi_negocio_full_test_report-${nowIsoSafe()}.json`);
  await fs.writeFile(filePath, JSON.stringify(report, null, 2), "utf8");
  return filePath;
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login to SaleADS and validate Mi Negocio workflow", async ({ page }) => {
    const saleadsUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL;
    const stepResults = Object.fromEntries(VALIDATION_FIELDS.map((field) => [field, "FAIL"]));
    const notes = [];
    const legalUrls = {};
    const screenshots = {};
    let executionError = null;

    try {
      if (saleadsUrl) {
        await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
        await waitForUiToLoad(page);
      } else {
        // Keep environment-agnostic behavior: if no URL is supplied, continue from current browser page.
        notes.push("No SALEADS_BASE_URL/BASE_URL provided. Continuing from current page.");
      }

      if (!saleadsUrl && page.url() === "about:blank") {
        throw new Error("Current page is about:blank. Provide SALEADS_BASE_URL/BASE_URL for autonomous runs.");
      }

      // STEP 1 - Login with Google
      {
        const loginClicked = (await clickByVisibleText(page, "Sign in with Google")) ||
          (await clickByVisibleText(page, "Iniciar sesión con Google")) ||
          (await clickByVisibleText(page, "Login with Google")) ||
          (await clickByVisibleText(page, "Iniciar con Google")) ||
          (await clickByVisibleText(page, "Google", { exact: false }));

        if (!loginClicked) {
          throw new Error("Could not find the login button or 'Sign in with Google'.");
        }

        await maybeHandleGoogleAccountPicker(page);
        await waitForUiToLoad(page);
        await ensureSidebar(page);

        stepResults["Login"] = "PASS";
        screenshots.loginDashboard = await capture(page, "01-dashboard-after-login");
      }

      // STEP 2 - Open Mi Negocio menu
      {
        const negocioClicked = (await clickByVisibleText(page, "Negocio")) ||
          (await clickByVisibleText(page, "Mi Negocio"));

        if (!negocioClicked) {
          throw new Error("Could not open 'Negocio' section in left sidebar.");
        }

        await expect(page.getByText(textRegex("Agregar Negocio")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByText(textRegex("Administrar Negocios")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });

        stepResults["Mi Negocio menu"] = "PASS";
        screenshots.miNegocioExpandedMenu = await capture(page, "02-mi-negocio-menu-expanded");
      }

      // STEP 3 - Validate Agregar Negocio modal
      {
        const openModal = await clickByVisibleText(page, "Agregar Negocio");
        if (!openModal) {
          throw new Error("Could not click 'Agregar Negocio'.");
        }

        await expect(page.getByText(textRegex("Crear Nuevo Negocio")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByText(textRegex("Nombre del Negocio")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByText(containsRegex("Tienes 2 de 3 negocios")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByRole("button", { name: textRegex("Cancelar") }).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByRole("button", { name: textRegex("Crear Negocio") }).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });

        screenshots.agregarNegocioModal = await capture(page, "03-agregar-negocio-modal");

        const nameInput = page.getByRole("textbox", { name: /Nombre del Negocio/i });
        if ((await nameInput.count()) > 0) {
          await nameInput.first().fill("Negocio Prueba Automatización");
        } else {
          // Fallback if no accessible label is wired.
          const fallbackInput = page.locator("input[type='text'], input:not([type]), textarea").first();
          if ((await fallbackInput.count()) > 0) {
            await fallbackInput.fill("Negocio Prueba Automatización");
          }
        }

        const cancelClicked = await clickByVisibleText(page, "Cancelar");
        if (!cancelClicked) {
          throw new Error("Could not close 'Crear Nuevo Negocio' modal using 'Cancelar'.");
        }

        stepResults["Agregar Negocio modal"] = "PASS";
      }

      // STEP 4 - Open Administrar Negocios
      {
        const adminVisible = await page.getByText(textRegex("Administrar Negocios")).first().isVisible().catch(() => false);
        if (!adminVisible) {
          await clickByVisibleText(page, "Mi Negocio");
          await clickByVisibleText(page, "Negocio");
        }

        const clicked = await clickByVisibleText(page, "Administrar Negocios");
        if (!clicked) {
          throw new Error("Could not click 'Administrar Negocios'.");
        }

        await expect(page.getByText(textRegex("Información General")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByText(textRegex("Detalles de la Cuenta")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByText(textRegex("Tus Negocios")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByText(textRegex("Sección Legal")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });

        stepResults["Administrar Negocios view"] = "PASS";
        screenshots.administrarNegociosPage = await capture(page, "04-administrar-negocios-full-page");
      }

      // STEP 5 - Validate Información General
      {
        const pageText = normalizeWhitespace(await page.innerText("body"));
        const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(pageText);
        const hasBusinessPlan = /BUSINESS PLAN/i.test(pageText);
        const hasChangePlanButton = (await page.getByRole("button", { name: /Cambiar Plan/i }).count()) > 0;

        // User name can be account name or profile label; we check a profile area for non-empty text.
        const infoGeneralBlock = page.getByText(textRegex("Información General")).first().locator("xpath=ancestor::*[self::section or self::div][1]");
        const infoText = normalizeWhitespace(await infoGeneralBlock.innerText().catch(() => ""));
        const hasPotentialUserName = infoText.split(" ").length >= 2;

        expect(hasPotentialUserName).toBeTruthy();
        expect(hasEmail).toBeTruthy();
        expect(hasBusinessPlan).toBeTruthy();
        expect(hasChangePlanButton).toBeTruthy();

        stepResults["Información General"] = "PASS";
      }

      // STEP 6 - Validate Detalles de la Cuenta
      {
        await expect(page.getByText(containsRegex("Cuenta creada")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByText(containsRegex("Estado activo")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByText(containsRegex("Idioma seleccionado")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });

        stepResults["Detalles de la Cuenta"] = "PASS";
      }

      // STEP 7 - Validate Tus Negocios
      {
        await expect(page.getByText(textRegex("Tus Negocios")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByText(textRegex("Agregar Negocio")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });
        await expect(page.getByText(containsRegex("Tienes 2 de 3 negocios")).first()).toBeVisible({
          timeout: APP_LOAD_TIMEOUT_MS
        });

        const businessesSectionText = normalizeWhitespace(await page.innerText("body"));
        expect(businessesSectionText.length).toBeGreaterThan(0);
        stepResults["Tus Negocios"] = "PASS";
      }

      // STEP 8 - Validate Términos y Condiciones
      {
        const { targetPage, openedInNewTab } = await clickTextWithPopupSupport(page, "Términos y Condiciones");
        const targetText = normalizeWhitespace(await targetPage.innerText("body"));

        expect(/Términos y Condiciones/i.test(targetText)).toBeTruthy();
        expect(targetText.length).toBeGreaterThan(120);

        stepResults["Términos y Condiciones"] = "PASS";
        screenshots.terminosCondiciones = await capture(targetPage, "08-terminos-condiciones");
        legalUrls.terminosYCondiciones = targetPage.url();

        if (openedInNewTab) {
          await targetPage.close();
        } else {
          await targetPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        }
        await page.bringToFront();
        await waitForUiToLoad(page);
      }

      // STEP 9 - Validate Política de Privacidad
      {
        const { targetPage, openedInNewTab } = await clickTextWithPopupSupport(page, "Política de Privacidad");
        const targetText = normalizeWhitespace(await targetPage.innerText("body"));

        expect(/Política de Privacidad/i.test(targetText)).toBeTruthy();
        expect(targetText.length).toBeGreaterThan(120);

        stepResults["Política de Privacidad"] = "PASS";
        screenshots.politicaPrivacidad = await capture(targetPage, "09-politica-privacidad");
        legalUrls.politicaPrivacidad = targetPage.url();

        if (openedInNewTab) {
          await targetPage.close();
        } else {
          await targetPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        }
        await page.bringToFront();
        await waitForUiToLoad(page);
      }
    } catch (error) {
      executionError = error;
      notes.push(`Execution error: ${error.message}`);
      try {
        screenshots.failureState = await capture(page, "99-failure-state");
      } catch {
        // Ignore screenshot secondary failures.
      }
    } finally {
      const report = {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        baseUrl: saleadsUrl || page.url(),
        results: stepResults,
        legalUrls,
        screenshots,
        notes
      };

      const reportPath = await writeFinalReport(report);
      notes.push(`Final report saved: ${reportPath}`);
      await test.info().attach("final-report", {
        body: JSON.stringify(report, null, 2),
        contentType: "application/json"
      });
    }

    if (executionError) {
      throw executionError;
    }
  });
});
