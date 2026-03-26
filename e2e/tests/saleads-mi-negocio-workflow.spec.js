const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

const evidenceDir = path.resolve(__dirname, "..", "artifacts");

function normalizeText(value) {
  return (value || "")
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim();
}

async function waitForUiSettle(page) {
  await page.waitForTimeout(600);
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(400);
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function clickByText(page, texts, options = {}) {
  const variants = Array.isArray(texts) ? texts : [texts];
  for (const text of variants) {
    const locator = page.getByText(text, { exact: options.exact ?? false }).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      if (options.waitAfterClick !== false) {
        await waitForUiSettle(page);
      }
      return text;
    }
  }

  if (options.allowRoleButton !== false) {
    for (const text of variants) {
      const button = page.getByRole("button", { name: new RegExp(escapeRegex(text), "i") }).first();
      if (await button.isVisible().catch(() => false)) {
        await button.click();
        if (options.waitAfterClick !== false) {
          await waitForUiSettle(page);
        }
        return text;
      }
    }
  }

  throw new Error(`No clickable element found for texts: ${variants.join(", ")}`);
}

async function ensureVisibleByText(page, texts) {
  const variants = Array.isArray(texts) ? texts : [texts];
  for (const text of variants) {
    const locator = page.getByText(text, { exact: false }).first();
    if (await locator.isVisible().catch(() => false)) {
      await expect(locator).toBeVisible();
      return text;
    }
  }
  throw new Error(`Expected visible text missing: ${variants.join(", ")}`);
}

async function locateSidebar(page) {
  const candidates = [
    page.getByRole("navigation").first(),
    page.locator("aside").first(),
    page.locator('[class*="sidebar"]').first(),
    page.locator("nav").first(),
  ];
  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }
  throw new Error("Left sidebar navigation not found.");
}

async function openLegalLinkAndValidate(page, linkTexts, headingTexts, screenshotName) {
  const appUrlBeforeClick = page.url();
  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  const clickedText = await clickByText(page, linkTexts, { allowRoleButton: false, waitAfterClick: false });
  const popup = await popupPromise;

  let legalPage = page;
  if (popup) {
    legalPage = popup;
    await legalPage.waitForLoadState("domcontentloaded");
  } else {
    await legalPage.waitForLoadState("domcontentloaded");
  }

  await waitForUiSettle(legalPage);

  // Validate heading by resilient text check.
  const bodyText = normalizeText(await legalPage.locator("body").innerText());
  const foundHeading = headingTexts.some((heading) => bodyText.includes(normalizeText(heading)));
  expect(foundHeading, `Heading text missing after clicking "${clickedText}"`).toBeTruthy();

  // Validate that legal content is visible by minimum size.
  expect(bodyText.length, "Legal content appears empty").toBeGreaterThan(120);

  await legalPage.screenshot({
    path: path.join(evidenceDir, screenshotName),
    fullPage: true,
  });

  const finalUrl = legalPage.url();
  if (popup) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUiSettle(page);
  } else if (finalUrl !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUiSettle(page);
  }
  return { clickedText, finalUrl };
}

test.describe("SaleADS Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
    fs.mkdirSync(evidenceDir, { recursive: true });

    const report = {
      Login: "FAIL",
      "Mi Negocio menu": "FAIL",
      "Agregar Negocio modal": "FAIL",
      "Administrar Negocios view": "FAIL",
      "Información General": "FAIL",
      "Detalles de la Cuenta": "FAIL",
      "Tus Negocios": "FAIL",
      "Términos y Condiciones": "FAIL",
      "Política de Privacidad": "FAIL",
      urls: {
        terminosYCondiciones: "",
        politicaDePrivacidad: "",
      },
      failures: [],
    };
    const failures = [];

    const appUrl = process.env.SALEADS_BASE_URL || process.env.SALEADS_URL || process.env.BASE_URL;
    if (!appUrl) {
      throw new Error(
        "Set SALEADS_BASE_URL (or SALEADS_URL/BASE_URL) to the login page of the current SaleADS environment."
      );
    }

    async function runValidationStep(stepTitle, reportKey, fn) {
      await test.step(stepTitle, async () => {
        try {
          await fn();
          report[reportKey] = "PASS";
        } catch (error) {
          const message = error instanceof Error ? error.message : String(error);
          failures.push({ step: reportKey, message });
          report.failures.push({ step: reportKey, message });
          report[reportKey] = "FAIL";
          await page.screenshot({
            path: path.join(evidenceDir, `${reportKey.replace(/[^\w-]+/g, "_")}-failure.png`),
            fullPage: true,
          });
        }
      });
    }

    try {
      await runValidationStep("1) Login with Google and validate dashboard/sidebar", "Login", async () => {
        await page.goto(appUrl, { waitUntil: "domcontentloaded" });
        await waitForUiSettle(page);

        const loginPopupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
        await clickByText(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Google",
        ], { waitAfterClick: false });

        const googlePopup = await loginPopupPromise;
        const loginPage = googlePopup || page;
        await loginPage.waitForLoadState("domcontentloaded");
        await waitForUiSettle(loginPage);

        const accountEmail = "juanlucasbarbiergarzon@gmail.com";
        const accountOption = loginPage.getByText(accountEmail, { exact: false }).first();
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
        } else {
          const accountRoleOption = loginPage
            .getByRole("button", { name: new RegExp(escapeRegex(accountEmail), "i") })
            .first();
          if (await accountRoleOption.isVisible().catch(() => false)) {
            await accountRoleOption.click();
          }
        }

        if (googlePopup) {
          await page.bringToFront();
        }
        await page.waitForLoadState("domcontentloaded");
        await waitForUiSettle(page);

        const sidebar = await locateSidebar(page);
        await expect(sidebar).toBeVisible();

        await page.screenshot({
          path: path.join(evidenceDir, "01-dashboard-loaded.png"),
          fullPage: true,
        });
      });

      await runValidationStep("2) Open Mi Negocio menu", "Mi Negocio menu", async () => {
        await ensureVisibleByText(page, ["Negocio", "Mi Negocio"]);
        await clickByText(page, ["Mi Negocio", "Negocio"]);

        await ensureVisibleByText(page, ["Agregar Negocio"]);
        await ensureVisibleByText(page, ["Administrar Negocios"]);

        await page.screenshot({
          path: path.join(evidenceDir, "02-mi-negocio-menu-expanded.png"),
          fullPage: true,
        });
      });

      await runValidationStep("3) Validate Agregar Negocio modal", "Agregar Negocio modal", async () => {
        await clickByText(page, ["Agregar Negocio"]);
        await ensureVisibleByText(page, ["Crear Nuevo Negocio"]);
        await ensureVisibleByText(page, ["Nombre del Negocio"]);
        await ensureVisibleByText(page, ["Tienes 2 de 3 negocios"]);
        await ensureVisibleByText(page, ["Cancelar"]);
        await ensureVisibleByText(page, ["Crear Negocio"]);

        await page.screenshot({
          path: path.join(evidenceDir, "03-agregar-negocio-modal.png"),
          fullPage: true,
        });

        const input = page.getByLabel("Nombre del Negocio").first();
        if (await input.isVisible().catch(() => false)) {
          await input.click();
          await input.fill("Negocio Prueba Automatización");
        } else {
          const byPlaceholder = page
            .locator('input[placeholder*="Nombre"], input[name*="nombre"], input[id*="nombre"]')
            .first();
          if (await byPlaceholder.isVisible().catch(() => false)) {
            await byPlaceholder.click();
            await byPlaceholder.fill("Negocio Prueba Automatización");
          }
        }

        await clickByText(page, ["Cancelar"]);
      });

      await runValidationStep(
        "4) Open Administrar Negocios and validate account page sections",
        "Administrar Negocios view",
        async () => {
          const adminMenu = page.getByText("Administrar Negocios", { exact: false }).first();
          if (!(await adminMenu.isVisible().catch(() => false))) {
            await clickByText(page, ["Mi Negocio", "Negocio"]);
          }

          await clickByText(page, ["Administrar Negocios"]);
          await waitForUiSettle(page);

          await ensureVisibleByText(page, ["Información General", "Informacion General"]);
          await ensureVisibleByText(page, ["Detalles de la Cuenta", "Detalles de Cuenta"]);
          await ensureVisibleByText(page, ["Tus Negocios"]);
          await ensureVisibleByText(page, ["Sección Legal", "Seccion Legal"]);

          await page.screenshot({
            path: path.join(evidenceDir, "04-administrar-negocios-account-page.png"),
            fullPage: true,
          });
        }
      );

      await runValidationStep("5) Validate Información General", "Información General", async () => {
        const bodyText = await page.locator("body").innerText();
        expect(normalizeText(bodyText)).toContain(normalizeText("BUSINESS PLAN"));
        await ensureVisibleByText(page, ["Cambiar Plan"]);

        const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(bodyText);
        expect(hasEmail, "Expected visible user email").toBeTruthy();

        const bodyLines = bodyText
          .split("\n")
          .map((line) => line.trim())
          .filter(Boolean);
        const hasLikelyName = bodyLines.some((line) => line.length > 2 && line.length <= 80 && !line.includes("@"));
        expect(hasLikelyName, "Expected visible user name-like text").toBeTruthy();
      });

      await runValidationStep("6) Validate Detalles de la Cuenta", "Detalles de la Cuenta", async () => {
        await ensureVisibleByText(page, ["Cuenta creada"]);
        await ensureVisibleByText(page, ["Estado activo", "Estado Activo"]);
        await ensureVisibleByText(page, ["Idioma seleccionado"]);
      });

      await runValidationStep("7) Validate Tus Negocios", "Tus Negocios", async () => {
        await ensureVisibleByText(page, ["Tus Negocios"]);
        await ensureVisibleByText(page, ["Agregar Negocio"]);
        await ensureVisibleByText(page, ["Tienes 2 de 3 negocios"]);
      });

      await runValidationStep("8) Validate Términos y Condiciones", "Términos y Condiciones", async () => {
        const termsResult = await openLegalLinkAndValidate(
          page,
          ["Términos y Condiciones", "Terminos y Condiciones"],
          ["Términos y Condiciones", "Terminos y Condiciones"],
          "05-terminos-y-condiciones.png"
        );
        report.urls.terminosYCondiciones = termsResult.finalUrl;
      });

      await runValidationStep("9) Validate Política de Privacidad", "Política de Privacidad", async () => {
        const privacyResult = await openLegalLinkAndValidate(
          page,
          ["Política de Privacidad", "Politica de Privacidad"],
          ["Política de Privacidad", "Politica de Privacidad"],
          "06-politica-de-privacidad.png"
        );
        report.urls.politicaDePrivacidad = privacyResult.finalUrl;
      });
    } finally {
      await test.step("10) Final report artifact", async () => {
      const reportPath = path.join(evidenceDir, "final-report.json");
      fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");
      await testInfo.attach("final-report", {
        path: reportPath,
        contentType: "application/json",
      });
      });
    }

    expect(failures, `Workflow failures: ${JSON.stringify(failures, null, 2)}`).toEqual([]);
  });
});
