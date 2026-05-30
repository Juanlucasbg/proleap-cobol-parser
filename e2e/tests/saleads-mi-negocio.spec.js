const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const START_URL =
  process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || "";
const UI_WAIT_MS = Number(process.env.SALEADS_UI_WAIT_MS || 1200);

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

const LEGAL_TEXT_PATTERN = /(t[eé]rminos|condiciones|privacidad|legal)/i;

function createEmptyReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: "No ejecutado." };
    return acc;
  }, {});
}

function toErrorMessage(error) {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(UI_WAIT_MS);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible({ timeout: 20_000 });
  await locator.click();
  await waitForUiToSettle(page);
}

async function firstVisible(locators, options = {}) {
  const { timeoutMs = 20_000, throwOnTimeout = true } = options;
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of locators) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 300));
  }

  if (!throwOnTimeout) {
    return null;
  }

  throw new Error("No se encontró un elemento visible dentro del timeout.");
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  return path;
}

async function runValidation(report, fieldName, validationFn) {
  try {
    const metadata = (await validationFn()) || {};
    report[fieldName] = {
      status: "PASS",
      details: "OK",
      ...metadata
    };
  } catch (error) {
    report[fieldName] = {
      status: "FAIL",
      details: toErrorMessage(error)
    };
  }
}

async function writeReport(report, testInfo) {
  const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json"
  });
}

test.describe("SaleADS.ai - Mi Negocio workflow", () => {
  test("login con Google y validación completa de Mi Negocio", async ({
    page,
    context
  }, testInfo) => {
    const report = createEmptyReport();

    if (START_URL) {
      await page.goto(START_URL, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Configura SALEADS_LOGIN_URL (o SALEADS_BASE_URL) para iniciar desde la pantalla de login sin hardcodear dominio."
      );
    }

    await runValidation(report, "Login", async () => {
      const loginButton = await firstVisible([
        page
          .getByRole("button", {
            name: /(sign in with google|iniciar sesi[oó]n con google|google)/i
          })
          .first(),
        page.getByText(/(sign in with google|iniciar sesi[oó]n con google)/i).first(),
        page.getByText(/google/i).first()
      ]);

      const popupPromise = page
        .waitForEvent("popup", { timeout: 7_000 })
        .catch(() => null);
      await clickAndWait(page, loginButton);

      const popup = await popupPromise;
      const authPage = popup || page;
      await waitForUiToSettle(authPage);

      const accountOption = await firstVisible(
        [
          authPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")).first(),
          authPage
            .getByRole("button", {
              name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")
            })
            .first(),
          authPage.locator(`[data-email="${GOOGLE_ACCOUNT_EMAIL}"]`).first()
        ],
        { timeoutMs: 12_000, throwOnTimeout: false }
      );

      if (accountOption) {
        await clickAndWait(authPage, accountOption);
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 45_000 }).catch(() => {});
        await page.bringToFront();
      }

      await waitForUiToSettle(page);

      await firstVisible([
        page.locator("main").first(),
        page.getByText(/(dashboard|panel|inicio|mi negocio)/i).first()
      ]);

      await firstVisible([
        page.locator("aside").first(),
        page.getByRole("navigation").first()
      ]);

      await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
    });

    await runValidation(report, "Mi Negocio menu", async () => {
      const negocioSection = await firstVisible(
        [
          page.getByRole("button", { name: /^Negocio$/i }).first(),
          page.getByRole("link", { name: /^Negocio$/i }).first(),
          page.getByText(/^Negocio$/i).first()
        ],
        { timeoutMs: 10_000, throwOnTimeout: false }
      );

      if (negocioSection) {
        await clickAndWait(page, negocioSection);
      }

      const miNegocioOption = await firstVisible([
        page.getByRole("button", { name: /^Mi Negocio$/i }).first(),
        page.getByRole("link", { name: /^Mi Negocio$/i }).first(),
        page.getByText(/^Mi Negocio$/i).first()
      ]);

      await clickAndWait(page, miNegocioOption);

      await firstVisible([
        page.getByRole("link", { name: /Agregar Negocio/i }).first(),
        page.getByRole("button", { name: /Agregar Negocio/i }).first(),
        page.getByText(/Agregar Negocio/i).first()
      ]);

      await firstVisible([
        page.getByRole("link", { name: /Administrar Negocios/i }).first(),
        page.getByRole("button", { name: /Administrar Negocios/i }).first(),
        page.getByText(/Administrar Negocios/i).first()
      ]);

      await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png", true);
    });

    await runValidation(report, "Agregar Negocio modal", async () => {
      const agregarNegocioEntry = await firstVisible([
        page.getByRole("link", { name: /Agregar Negocio/i }).first(),
        page.getByRole("button", { name: /Agregar Negocio/i }).first(),
        page.getByText(/Agregar Negocio/i).first()
      ]);

      await clickAndWait(page, agregarNegocioEntry);

      await firstVisible([
        page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first(),
        page.getByText(/Crear Nuevo Negocio/i).first()
      ]);

      const businessNameInput = await firstVisible([
        page.getByLabel(/Nombre del Negocio/i).first(),
        page.getByPlaceholder(/Nombre del Negocio/i).first(),
        page.getByRole("textbox", { name: /Nombre del Negocio/i }).first()
      ]);

      await firstVisible([page.getByText(/Tienes 2 de 3 negocios/i).first()]);
      await firstVisible([page.getByRole("button", { name: /Cancelar/i }).first()]);
      await firstVisible([
        page.getByRole("button", { name: /Crear Negocio/i }).first()
      ]);

      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", true);

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");

      const cancelarButton = await firstVisible([
        page.getByRole("button", { name: /Cancelar/i }).first()
      ]);
      await clickAndWait(page, cancelarButton);
    });

    await runValidation(report, "Administrar Negocios view", async () => {
      const administrarNegociosVisible = await firstVisible(
        [
          page.getByRole("link", { name: /Administrar Negocios/i }).first(),
          page.getByRole("button", { name: /Administrar Negocios/i }).first(),
          page.getByText(/Administrar Negocios/i).first()
        ],
        { timeoutMs: 5_000, throwOnTimeout: false }
      );

      if (!administrarNegociosVisible) {
        const miNegocioOption = await firstVisible([
          page.getByRole("button", { name: /^Mi Negocio$/i }).first(),
          page.getByRole("link", { name: /^Mi Negocio$/i }).first(),
          page.getByText(/^Mi Negocio$/i).first()
        ]);

        await clickAndWait(page, miNegocioOption);
      }

      const administrarNegociosEntry = await firstVisible([
        page.getByRole("link", { name: /Administrar Negocios/i }).first(),
        page.getByRole("button", { name: /Administrar Negocios/i }).first(),
        page.getByText(/Administrar Negocios/i).first()
      ]);

      await clickAndWait(page, administrarNegociosEntry);

      await firstVisible([page.getByText(/Informaci[oó]n General/i).first()]);
      await firstVisible([page.getByText(/Detalles de la Cuenta/i).first()]);
      await firstVisible([page.getByText(/Tus Negocios/i).first()]);
      await firstVisible([page.getByText(/Secci[oó]n Legal/i).first()]);

      await captureCheckpoint(page, testInfo, "04-administrar-negocios-view.png", true);
    });

    await runValidation(report, "Información General", async () => {
      await firstVisible([
        page.getByText(/Informaci[oó]n General/i).first(),
        page.getByRole("heading", { name: /Informaci[oó]n General/i }).first()
      ]);

      await firstVisible(
        [page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()],
        { timeoutMs: 20_000 }
      );

      await firstVisible(
        [
          page.getByText(/Nombre/i).first(),
          page.locator('[data-testid*="name" i]').first(),
          page.locator('[class*="name" i]').first()
        ],
        { timeoutMs: 15_000 }
      );

      await firstVisible([page.getByText(/BUSINESS PLAN/i).first()]);
      await firstVisible([page.getByRole("button", { name: /Cambiar Plan/i }).first()]);
    });

    await runValidation(report, "Detalles de la Cuenta", async () => {
      await firstVisible([page.getByText(/Cuenta creada/i).first()]);
      await firstVisible([page.getByText(/Estado activo/i).first()]);
      await firstVisible([page.getByText(/Idioma seleccionado/i).first()]);
    });

    await runValidation(report, "Tus Negocios", async () => {
      await firstVisible([
        page.getByText(/Tus Negocios/i).first(),
        page.getByRole("heading", { name: /Tus Negocios/i }).first()
      ]);

      await firstVisible(
        [
          page.getByRole("table").first(),
          page.getByRole("list").first(),
          page.locator('[data-testid*="business" i]').first()
        ],
        { timeoutMs: 20_000 }
      );

      await firstVisible([
        page.getByRole("button", { name: /Agregar Negocio/i }).first(),
        page.getByRole("link", { name: /Agregar Negocio/i }).first()
      ]);

      await firstVisible([page.getByText(/Tienes 2 de 3 negocios/i).first()]);
    });

    await runValidation(report, "Términos y Condiciones", async () => {
      const terminosLink = await firstVisible([
        page.getByRole("link", { name: /T[eé]rminos y Condiciones/i }).first(),
        page.getByText(/T[eé]rminos y Condiciones/i).first()
      ]);

      const newTabPromise = context
        .waitForEvent("page", { timeout: 7_000 })
        .catch(() => null);
      await clickAndWait(page, terminosLink);

      const legalPageFromTab = await newTabPromise;
      const legalPage = legalPageFromTab || page;

      await waitForUiToSettle(legalPage);

      await firstVisible([
        legalPage
          .getByRole("heading", { name: /T[eé]rminos y Condiciones/i })
          .first(),
        legalPage.getByText(/T[eé]rminos y Condiciones/i).first()
      ]);

      await firstVisible(
        [
          legalPage.locator("p").first(),
          legalPage.locator("li").first(),
          legalPage.getByText(LEGAL_TEXT_PATTERN).first()
        ],
        { timeoutMs: 20_000 }
      );

      await captureCheckpoint(legalPage, testInfo, "05-terminos-y-condiciones.png", true);
      const finalUrl = legalPage.url();

      if (legalPageFromTab) {
        await legalPageFromTab.close();
        await page.bringToFront();
        await waitForUiToSettle(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUiToSettle(page);
      }

      return { url: finalUrl };
    });

    await runValidation(report, "Política de Privacidad", async () => {
      const privacidadLink = await firstVisible([
        page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }).first(),
        page.getByText(/Pol[ií]tica de Privacidad/i).first()
      ]);

      const newTabPromise = context
        .waitForEvent("page", { timeout: 7_000 })
        .catch(() => null);
      await clickAndWait(page, privacidadLink);

      const legalPageFromTab = await newTabPromise;
      const legalPage = legalPageFromTab || page;

      await waitForUiToSettle(legalPage);

      await firstVisible([
        legalPage
          .getByRole("heading", { name: /Pol[ií]tica de Privacidad/i })
          .first(),
        legalPage.getByText(/Pol[ií]tica de Privacidad/i).first()
      ]);

      await firstVisible(
        [
          legalPage.locator("p").first(),
          legalPage.locator("li").first(),
          legalPage.getByText(LEGAL_TEXT_PATTERN).first()
        ],
        { timeoutMs: 20_000 }
      );

      await captureCheckpoint(legalPage, testInfo, "06-politica-de-privacidad.png", true);
      const finalUrl = legalPage.url();

      if (legalPageFromTab) {
        await legalPageFromTab.close();
        await page.bringToFront();
        await waitForUiToSettle(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUiToSettle(page);
      }

      return { url: finalUrl };
    });

    await writeReport(report, testInfo);

    // Final Report (Step 10): console-visible PASS/FAIL summary per required field.
    console.table(
      Object.entries(report).map(([field, result]) => ({
        validation: field,
        status: result.status,
        details: result.details,
        url: result.url || ""
      }))
    );

    const failedFields = Object.entries(report)
      .filter(([, result]) => result.status === "FAIL")
      .map(([field, result]) => `${field}: ${result.details}`);

    expect(
      failedFields,
      `Validaciones fallidas:\n${failedFields.join("\n")}`
    ).toEqual([]);
  });
});
