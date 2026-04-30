const { test, expect } = require("@playwright/test");
const path = require("path");
const fs = require("fs");

const GOOGLE_EMAIL = process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const BASE_URL = process.env.SALEADS_BASE_URL || "";
const SCREENSHOT_DIR = process.env.SALEADS_SCREENSHOT_DIR
  ? path.resolve(process.env.SALEADS_SCREENSHOT_DIR)
  : path.resolve(__dirname, "..", "artifacts", "screenshots");
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

function slug(text) {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function firstVisibleLocator(locators, timeout = 10000) {
  for (const locator of locators) {
    if (await locator.isVisible({ timeout }).catch(() => false)) {
      return locator;
    }
  }
  throw new Error("No expected locator is visible.");
}

async function ensureLoginPageIsReady(page) {
  if (BASE_URL) {
    await page.goto(BASE_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  await waitForUi(page);
  const currentUrl = page.url();
  if (currentUrl === "about:blank" || currentUrl === "data:,") {
    throw new Error(
      "Browser did not start on a login page. Set SALEADS_BASE_URL or preload the browser at the SaleADS login page."
    );
  }
}

async function clickAnyText(page, textOptions, timeout = 15000) {
  for (const option of textOptions) {
    const locator = page.getByText(option, { exact: true }).first();
    if (await locator.isVisible({ timeout }).catch(() => false)) {
      await clickAndWait(page, locator);
      return option;
    }
  }

  throw new Error(`No visible text option found: ${textOptions.join(", ")}`);
}

async function screenshot(page, stepId, label, fullPage = false) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const file = path.join(SCREENSHOT_DIR, `${String(stepId).padStart(2, "0")}-${slug(label)}.png`);
  await page.screenshot({ path: file, fullPage });
  return file;
}

function upsertResult(results, key, passed, details) {
  const existing = results.find((entry) => entry.key === key);
  if (existing) {
    existing.passed = passed;
    existing.details = details;
    return;
  }
  results.push({ key, passed, details });
}

async function executeValidation(results, key, fn) {
  try {
    const details = await fn();
    upsertResult(results, key, true, details || "Validation succeeded.");
    return true;
  } catch (error) {
    upsertResult(results, key, false, error.message);
    return false;
  }
}

async function loginWithGoogle(page, context) {
  const loginButton = await firstVisibleLocator(
    [
      page.getByText("Sign in with Google", { exact: true }).first(),
      page.getByText("Iniciar sesión con Google", { exact: true }).first(),
      page.getByText("Continuar con Google", { exact: true }).first(),
      page.getByText("Acceder con Google", { exact: true }).first(),
      page.getByText("Login con Google", { exact: true }).first(),
      page.getByRole("button", { name: /google/i }).first(),
    ],
    15000
  );

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await loginButton.click();
  const popup = await popupPromise;
  const authPage = popup || page;

  await waitForUi(authPage);
  const googleAccountChip = authPage.getByText(GOOGLE_EMAIL, { exact: true }).first();
  if (await googleAccountChip.isVisible({ timeout: 10000 }).catch(() => false)) {
    await clickAndWait(authPage, googleAccountChip);
  }

  if (popup) {
    await popup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
    await page.bringToFront();
  }
  await waitForUi(page);
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login and validate Mi Negocio full workflow", async ({ page, context }) => {
    test.setTimeout(300000);

    const stepResults = [];
    const evidence = [];
    let termsUrl = "";
    let privacyUrl = "";

    await test.step("1) Login with Google", async () => {
      await executeValidation(stepResults, "Login", async () => {
        await ensureLoginPageIsReady(page);
        await loginWithGoogle(page, context);

        await Promise.any([
          expect(page.locator("aside")).toBeVisible({ timeout: 30000 }),
          expect(page.getByRole("navigation")).toBeVisible({ timeout: 30000 }),
        ]);

        const dashboardShot = await screenshot(page, 1, "dashboard-loaded");
        evidence.push({ step: "Login", screenshot: dashboardShot });
        return "Dashboard and sidebar are visible.";
      });
    });

    await test.step("2) Open Mi Negocio menu", async () => {
      await executeValidation(stepResults, "Mi Negocio menu", async () => {
        await clickAnyText(page, ["Negocio"]);
        await clickAnyText(page, ["Mi Negocio"]);
        await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
        await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();

        const menuShot = await screenshot(page, 2, "mi-negocio-menu-expanded");
        evidence.push({ step: "Mi Negocio menu", screenshot: menuShot });
        return "Submenu expanded with expected options.";
      });
    });

    await test.step("3) Validate Agregar Negocio modal", async () => {
      await executeValidation(stepResults, "Agregar Negocio modal", async () => {
        await clickAnyText(page, ["Agregar Negocio"]);
        await expect(page.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();

        const businessNameInput = await firstVisibleLocator([
          page.getByLabel("Nombre del Negocio", { exact: true }),
          page.getByPlaceholder("Nombre del Negocio"),
          page.locator('input[name*="negocio" i], input[id*="negocio" i]').first(),
        ]);
        await expect(businessNameInput).toBeVisible();
        await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Cancelar", exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Crear Negocio", exact: true })).toBeVisible();

        await businessNameInput.click();
        await waitForUi(page);
        await businessNameInput.fill("Negocio Prueba Automatización");

        const modalShot = await screenshot(page, 3, "crear-nuevo-negocio-modal");
        evidence.push({ step: "Agregar Negocio modal", screenshot: modalShot });

        await clickAndWait(page, page.getByRole("button", { name: "Cancelar", exact: true }));
        return "Modal content and controls validated.";
      });
    });

    await test.step("4) Open Administrar Negocios", async () => {
      await executeValidation(stepResults, "Administrar Negocios view", async () => {
        const administrar = page.getByText("Administrar Negocios", { exact: true }).first();
        if (!(await administrar.isVisible({ timeout: 5000 }).catch(() => false))) {
          await clickAnyText(page, ["Mi Negocio"]);
        }
        await clickAndWait(page, administrar);

        await expect(page.getByText("Información General", { exact: true })).toBeVisible();
        await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
        await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
        await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible();

        const accountShot = await screenshot(page, 4, "administrar-negocios-view", true);
        evidence.push({ step: "Administrar Negocios view", screenshot: accountShot });
        return "Account page sections are visible.";
      });
    });

    await test.step("5) Validate Información General", async () => {
      await executeValidation(stepResults, "Información General", async () => {
        const infoSection = page.getByText("Información General", { exact: true }).first();
        await expect(infoSection).toBeVisible();
        await expect(page.getByText("BUSINESS PLAN", { exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Cambiar Plan", exact: true })).toBeVisible();

        const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
        const emailLocator = page.getByText(emailRegex).first();
        await expect(emailLocator).toBeVisible();

        const infoText = await page.locator("body").innerText();
        const hasNameLikeText = /\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+\b/.test(infoText);
        expect(hasNameLikeText).toBeTruthy();
        return "User name/email, plan and action button are visible.";
      });
    });

    await test.step("6) Validate Detalles de la Cuenta", async () => {
      await executeValidation(stepResults, "Detalles de la Cuenta", async () => {
        await expect(page.getByText("Cuenta creada", { exact: true })).toBeVisible();
        await expect(page.getByText("Estado activo", { exact: true })).toBeVisible();
        await expect(page.getByText("Idioma seleccionado", { exact: true })).toBeVisible();
        return "Expected account details labels are visible.";
      });
    });

    await test.step("7) Validate Tus Negocios", async () => {
      await executeValidation(stepResults, "Tus Negocios", async () => {
        await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Agregar Negocio", exact: true })).toBeVisible();
        await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();

        const businessListItem = page.locator('[role="listitem"], table tbody tr, .business-item').first();
        await expect(businessListItem).toBeVisible();
        return "Business list and controls are visible.";
      });
    });

    async function validateLegalLink(linkText, expectedHeading, stepNumber, reportKey) {
      const currentPage = page;
      const legalLink = currentPage.getByText(linkText, { exact: true }).first();
      await expect(legalLink).toBeVisible();

      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await legalLink.click();
      await currentPage.waitForTimeout(500);

      const popup = await popupPromise;
      const targetPage = popup || currentPage;
      await waitForUi(targetPage);

      await firstVisibleLocator(
        [
          targetPage.getByRole("heading", { name: expectedHeading, exact: true }).first(),
          targetPage.getByText(expectedHeading, { exact: true }).first(),
        ],
        15000
      );
      await expect(targetPage.locator("main, article, body")).toContainText(/\S+/);

      const shot = await screenshot(targetPage, stepNumber, expectedHeading, true);
      evidence.push({ step: reportKey, screenshot: shot });

      const url = targetPage.url();
      if (popup) {
        await popup.close();
        await currentPage.bringToFront();
        await waitForUi(currentPage);
      } else {
        await currentPage.goBack();
        await waitForUi(currentPage);
      }
      return url;
    }

    await test.step("8) Validate Términos y Condiciones", async () => {
      await executeValidation(stepResults, "Términos y Condiciones", async () => {
        termsUrl = await validateLegalLink(
          "Términos y Condiciones",
          "Términos y Condiciones",
          8,
          "Términos y Condiciones"
        );
        return `URL: ${termsUrl}`;
      });
    });

    await test.step("9) Validate Política de Privacidad", async () => {
      await executeValidation(stepResults, "Política de Privacidad", async () => {
        privacyUrl = await validateLegalLink(
          "Política de Privacidad",
          "Política de Privacidad",
          9,
          "Política de Privacidad"
        );
        return `URL: ${privacyUrl}`;
      });
    });

    await test.step("10) Final report", async () => {
      for (const field of REPORT_FIELDS) {
        const present = stepResults.some((entry) => entry.key === field);
        if (!present) {
          upsertResult(stepResults, field, false, "Step did not execute.");
        }
      }

      const report = REPORT_FIELDS.map((field) => {
        const result = stepResults.find((entry) => entry.key === field);
        return {
          field,
          status: result && result.passed ? "PASS" : "FAIL",
          details: result ? result.details : "No details captured",
        };
      });

      const reportFile = path.resolve(__dirname, "..", "artifacts", "saleads-mi-negocio-report.json");
      fs.mkdirSync(path.dirname(reportFile), { recursive: true });
      fs.writeFileSync(
        reportFile,
        JSON.stringify(
          {
            runAt: new Date().toISOString(),
            legalUrls: {
              termsAndConditions: termsUrl,
              privacyPolicy: privacyUrl,
            },
            report,
            evidence,
          },
          null,
          2
        )
      );

      console.log("SaleADS Mi Negocio final report:", report);
      console.log(`Report file: ${reportFile}`);
      console.log(`Terms URL: ${termsUrl}`);
      console.log(`Privacy URL: ${privacyUrl}`);

      const failed = report.filter((entry) => entry.status === "FAIL");
      expect(
        failed,
        `One or more workflow validations failed: ${JSON.stringify(failed, null, 2)}`
      ).toEqual([]);
    });
  });
});
