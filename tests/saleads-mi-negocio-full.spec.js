const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const REPORT_DIR = path.resolve(__dirname, "..", "e2e-artifacts", "reports");
const SCREENSHOT_DIR = path.resolve(__dirname, "..", "e2e-artifacts", "screenshots");
const WAIT_AFTER_CLICK_MS = Number(process.env.SALEADS_UI_WAIT_MS || 1200);

function normalizeText(str) {
  return (str || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function textRegex(text) {
  const accentMap = {
    a: "[aáàâäã]",
    e: "[eéèêë]",
    i: "[iíìîï]",
    o: "[oóòôöõ]",
    u: "[uúùûü]",
    n: "[nñ]",
    c: "[cç]",
  };
  const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const accentInsensitive = escaped
    .split("")
    .map((char) => {
      const lower = char.toLowerCase();
      return accentMap[lower] || char;
    })
    .join("")
    .replace(/\s+/g, "\\s+");

  return new RegExp(accentInsensitive, "i");
}

async function waitForUi(page, timeout = 10000) {
  await page.waitForLoadState("domcontentloaded", { timeout }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout }).catch(() => {});
  await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
}

async function isVisible(locator) {
  return locator.first().isVisible().catch(() => false);
}

async function ensureVisible(locator, timeout = 12000) {
  await expect(locator.first()).toBeVisible({ timeout });
}

async function clickFirstVisible(page, candidates, opts = {}) {
  const timeout = opts.timeout ?? 12000;
  for (const locator of candidates) {
    if (await isVisible(locator)) {
      await locator.first().click();
      await waitForUi(page, timeout);
      return locator;
    }
  }
  throw new Error("No clickable visible candidate matched.");
}

async function findByText(page, text, options = {}) {
  const exact = options.exact ?? false;
  const role = options.role;
  const regex = textRegex(text);

  const variants = role
    ? [page.getByRole(role, { name: regex, exact })]
    : [
        page.getByRole("button", { name: regex, exact }),
        page.getByRole("link", { name: regex, exact }),
        page.getByText(regex, { exact }),
      ];

  for (const locator of variants) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }
  return null;
}

async function capture(page, name, fullPage = false) {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
  const safeName = name.replace(/[^a-zA-Z0-9-_]/g, "_");
  const filePath = path.join(SCREENSHOT_DIR, `${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function findBusinessNameInput(page) {
  const candidates = [
    page.getByLabel(textRegex("Nombre del Negocio")),
    page.getByPlaceholder(textRegex("Nombre del Negocio")),
    page.getByPlaceholder(textRegex("Nombre")),
    page.locator('input[aria-label*="Nombre"], input[placeholder*="Nombre"]'),
    page.locator("input, textarea").filter({ hasText: textRegex("Nombre del Negocio") }),
  ];

  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate.first();
    }
  }
  return null;
}

function newReport() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };
}

function ensureAppUrlConfigured() {
  const configured = process.env.SALEADS_BASE_URL;
  if (configured && configured.trim()) {
    return;
  }
  throw new Error(
    "SALEADS_BASE_URL env var is required so this test can target any SaleADS environment without hardcoded URLs."
  );
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }) => {
    ensureAppUrlConfigured();
    const report = newReport();
    const evidences = [];
    const notes = [];

    await fs.mkdir(REPORT_DIR, { recursive: true });

    async function validateLegalLink(linkText, reportKey, screenshotName) {
      const legalLink =
        (await findByText(page, linkText, { role: "link" })) ||
        (await findByText(page, linkText));
      if (!legalLink) {
        throw new Error(`No se encontró enlace legal '${linkText}'.`);
      }

      const currentUrl = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
      await legalLink.click();
      await waitForUi(page);

      const popup = await popupPromise;
      const legalPage = popup || page;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 15000 });
        await popup.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
      } else if (page.url() === currentUrl) {
        await page.waitForTimeout(1000);
      }

      const legalBody = normalizeText(await legalPage.locator("body").innerText());
      expect(legalBody.includes(normalizeText(linkText))).toBeTruthy();
      expect(legalBody.length > 50).toBeTruthy();

      const screenshot = await capture(legalPage, screenshotName);
      evidences.push({
        step: reportKey,
        screenshot,
        finalUrl: legalPage.url(),
      });
      report[reportKey] = "PASS";

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await page.goBack().catch(() => {});
        await waitForUi(page);
      }
    }

    let scenarioError = null;
    try {
      // Step 1: Login with Google
      await page.goto("/", { waitUntil: "domcontentloaded" });
      await waitForUi(page);

      const loginCandidates = [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("button", { name: /(iniciar|inicia|sign)\s*(sesion|session|in)/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/(continuar|continuar con|sign in|iniciar).*google/i),
      ];

      await clickFirstVisible(page, loginCandidates).catch(async (error) => {
        const screenshot = await capture(page, "step1_login_not_found");
        evidences.push({ step: "Login", screenshot });
        throw error;
      });

      const accountSelector = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i);
      if (await isVisible(accountSelector)) {
        await accountSelector.first().click();
        await waitForUi(page);
      }

      const appRootCandidates = [
        page.locator("aside"),
        page.locator("nav"),
        page.getByText(/negocio/i),
      ];
      let sidebarVisible = false;
      for (const candidate of appRootCandidates) {
        if (await isVisible(candidate)) {
          sidebarVisible = true;
          break;
        }
      }
      expect(sidebarVisible).toBeTruthy();
      report.Login = "PASS";

      evidences.push({
        step: "Login",
        screenshot: await capture(page, "step1_dashboard_loaded"),
      });

      // Step 2: Open Mi Negocio menu
      const negocioLocator =
        (await findByText(page, "Negocio")) ||
        page.getByRole("button", { name: /negocio/i }).first();
      await ensureVisible(negocioLocator);
      await negocioLocator.click();
      await waitForUi(page);

      const miNegocioLocator =
        (await findByText(page, "Mi Negocio")) ||
        page.getByRole("button", { name: /mi negocio/i }).first();
      await ensureVisible(miNegocioLocator);
      await miNegocioLocator.click();
      await waitForUi(page);

      await ensureVisible(page.getByText(textRegex("Agregar Negocio")));
      await ensureVisible(page.getByText(textRegex("Administrar Negocios")));
      report["Mi Negocio menu"] = "PASS";

      evidences.push({
        step: "Mi Negocio menu",
        screenshot: await capture(page, "step2_mi_negocio_expanded"),
      });

      // Step 3: Validate Agregar Negocio modal
      const agregarNegocioMenu = await findByText(page, "Agregar Negocio");
      if (!agregarNegocioMenu) {
        throw new Error("No se encontró la opción 'Agregar Negocio'.");
      }
      await agregarNegocioMenu.click();
      await waitForUi(page);

      const modalRoot = page.locator('[role="dialog"], .modal, [class*="modal"]').first();
      await ensureVisible(modalRoot);
      await ensureVisible(page.getByText(textRegex("Crear Nuevo Negocio")));
      const businessNameInput = await findBusinessNameInput(page);
      expect(businessNameInput, "Expected 'Nombre del Negocio' input to exist.").toBeTruthy();
      await ensureVisible(page.getByText(textRegex("Tienes 2 de 3 negocios")));
      await ensureVisible(page.getByRole("button", { name: textRegex("Cancelar") }));
      await ensureVisible(page.getByRole("button", { name: textRegex("Crear Negocio") }));

      evidences.push({
        step: "Agregar Negocio modal",
        screenshot: await capture(page, "step3_agregar_negocio_modal"),
      });

      if (businessNameInput && (await isVisible(businessNameInput))) {
        await businessNameInput.click();
        await businessNameInput.fill("Negocio Prueba Automatización");
        await waitForUi(page);
      } else {
        notes.push("Optional input action skipped: 'Nombre del Negocio' field not interactable.");
      }

      const cancelarBtn = page.getByRole("button", { name: textRegex("Cancelar") }).first();
      if (await isVisible(cancelarBtn)) {
        await cancelarBtn.click();
        await waitForUi(page);
      }
      report["Agregar Negocio modal"] = "PASS";

      // Step 4: Open Administrar Negocios
      const miNegocioAgain = await findByText(page, "Mi Negocio");
      if (
        miNegocioAgain &&
        (await isVisible(page.getByText(textRegex("Administrar Negocios")))) === false
      ) {
        await miNegocioAgain.click();
        await waitForUi(page);
      }

      const administrarNegocios = await findByText(page, "Administrar Negocios");
      if (!administrarNegocios) {
        throw new Error("No se encontró 'Administrar Negocios'.");
      }
      await administrarNegocios.click();
      await waitForUi(page, 15000);

      await ensureVisible(page.getByText(textRegex("Información General")));
      await ensureVisible(page.getByText(textRegex("Detalles de la Cuenta")));
      await ensureVisible(page.getByText(textRegex("Tus Negocios")));
      await ensureVisible(page.getByText(textRegex("Sección Legal")));
      report["Administrar Negocios view"] = "PASS";
      evidences.push({
        step: "Administrar Negocios view",
        screenshot: await capture(page, "step4_administrar_negocios", true),
      });

      // Step 5: Información General
      const bodyText = normalizeText(await page.locator("body").innerText());
      expect(bodyText.includes("business plan")).toBeTruthy();
      await ensureVisible(page.getByRole("button", { name: textRegex("Cambiar Plan") }));
      const hasUserIdentity =
        /@/.test(await page.locator("body").innerText()) &&
        (await page.locator("body").innerText()).trim().length > 0;
      expect(hasUserIdentity).toBeTruthy();
      report["Información General"] = "PASS";

      // Step 6: Detalles de la Cuenta
      await ensureVisible(page.getByText(textRegex("Cuenta creada")));
      await ensureVisible(page.getByText(textRegex("Estado activo")));
      await ensureVisible(page.getByText(textRegex("Idioma seleccionado")));
      report["Detalles de la Cuenta"] = "PASS";

      // Step 7: Tus Negocios
      await ensureVisible(page.getByText(textRegex("Tus Negocios")));
      await ensureVisible(page.getByText(textRegex("Tienes 2 de 3 negocios")));
      await ensureVisible(page.getByRole("button", { name: textRegex("Agregar Negocio") }));
      const businessItemVisible =
        (await page.locator("ul li, [class*='business'], [data-testid*='business']").count()) > 0;
      expect(businessItemVisible).toBeTruthy();
      report["Tus Negocios"] = "PASS";

      // Step 8: Términos y Condiciones
      await validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "step8_terminos");

      // Step 9: Política de Privacidad
      await validateLegalLink("Política de Privacidad", "Política de Privacidad", "step9_privacidad");
    } catch (error) {
      scenarioError = error;
      notes.push(`Execution error: ${error.message}`);
      evidences.push({
        step: "Failure checkpoint",
        screenshot: await capture(page, "failure_checkpoint"),
        finalUrl: page.url(),
      });
    } finally {
      // Step 10: Final report
      const finalReport = {
        test: "saleads_mi_negocio_full_test",
        timestamp: new Date().toISOString(),
        environment: {
          baseUrl: process.env.SALEADS_BASE_URL || null,
          browser: "chromium",
        },
        validations: report,
        evidences,
        notes,
      };

      const reportPath = path.join(REPORT_DIR, "saleads_mi_negocio_full_test.report.json");
      await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
      test.info().annotations.push({ type: "final-report", description: reportPath });
    }

    if (scenarioError) {
      throw scenarioError;
    }
  });
});
