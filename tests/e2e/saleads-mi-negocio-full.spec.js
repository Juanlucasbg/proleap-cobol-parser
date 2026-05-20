const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const REPORT_KEYS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

const EMAIL_UNDER_TEST = "juanlucasbarbiergarzon@gmail.com";
const NEW_BUSINESS_NAME = "Negocio Prueba Automatizacion";

function buildInitialReport() {
  return Object.fromEntries(REPORT_KEYS.map((key) => [key, "FAIL"]));
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function clickVisibleText(page, regex, description) {
  const candidates = [
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByText(regex),
  ];

  for (const locator of candidates) {
    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      await first.click();
      await waitForUiToSettle(page);
      return;
    }
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function takeCheckpoint(page, testInfo, fileName, fullPage = false) {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function captureLegalPage({
  appPage,
  linkRegex,
  expectedHeadingRegex,
  screenshotName,
  details,
  detailKey,
  testInfo,
}) {
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickVisibleText(appPage, linkRegex, `legal link ${linkRegex}`);
  const popup = await popupPromise;

  const legalPage = popup || appPage;
  await legalPage.waitForLoadState("domcontentloaded");
  const heading = legalPage
    .getByRole("heading", { name: expectedHeadingRegex })
    .or(legalPage.getByText(expectedHeadingRegex))
    .first();
  await expect(heading).toBeVisible();

  const pageBody = legalPage.locator("body");
  await expect(pageBody).toContainText(/.{40,}/);

  details[detailKey] = legalPage.url();
  await takeCheckpoint(legalPage, testInfo, screenshotName, true);

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToSettle(appPage);
  } else {
    await legalPage.goBack().catch(() => {});
    await waitForUiToSettle(appPage);
  }
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
    const startUrl = process.env.SALEADS_START_URL;
    const report = buildInitialReport();
    const details = {
      terminosUrl: null,
      politicaPrivacidadUrl: null,
      errors: {},
    };

    const currentUrl = page.url();
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    } else if (currentUrl === "about:blank") {
      throw new Error(
        "Set SALEADS_START_URL to the SaleADS login page for your current environment."
      );
    }
    await waitForUiToSettle(page);

    const runValidation = async (key, callback) => {
      try {
        await callback();
        report[key] = "PASS";
      } catch (error) {
        details.errors[key] = error.message;
      }
    };

    await runValidation("Login", async () => {
      const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickVisibleText(
        page,
        /sign in with google|iniciar sesion con google|continuar con google|google/i,
        "Google login"
      );

      const popup = await popupPromise;
      const googleSurface = popup || page;

      const accountLocator = googleSurface.getByText(EMAIL_UNDER_TEST, { exact: true }).first();
      if (await accountLocator.isVisible().catch(() => false)) {
        await accountLocator.click();
        await waitForUiToSettle(googleSurface);
      }

      if (popup) {
        await popup.waitForClose({ timeout: 45000 }).catch(() => {});
      }

      await expect(page.locator("aside, nav").first()).toBeVisible();
      await expect(page.getByText(/Negocio/i).first()).toBeVisible();
      await takeCheckpoint(page, testInfo, "step-1-dashboard-loaded.png", true);
    });

    await runValidation("Mi Negocio menu", async () => {
      await clickVisibleText(page, /Mi Negocio/i, "Mi Negocio menu");
      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await takeCheckpoint(page, testInfo, "step-2-mi-negocio-expanded.png");
    });

    await runValidation("Agregar Negocio modal", async () => {
      await clickVisibleText(page, /Agregar Negocio/i, "Agregar Negocio");

      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
      const negocioInput = page
        .getByLabel(/Nombre del Negocio/i)
        .or(page.getByPlaceholder(/Nombre del Negocio/i))
        .first();
      await expect(negocioInput).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

      await takeCheckpoint(page, testInfo, "step-3-crear-negocio-modal.png");

      await negocioInput.click();
      await negocioInput.fill(NEW_BUSINESS_NAME);
      await clickVisibleText(page, /Cancelar/i, "Cancelar modal");
    });

    await runValidation("Administrar Negocios view", async () => {
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
        await clickVisibleText(page, /Mi Negocio/i, "Mi Negocio menu re-open");
      }

      await clickVisibleText(page, /Administrar Negocios/i, "Administrar Negocios");

      await expect(page.getByText(/Informaci.n General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Secci.n Legal/i).first()).toBeVisible();

      await takeCheckpoint(page, testInfo, "step-4-administrar-negocios.png", true);
    });

    await runValidation("Informacion General", async () => {
      await expect(page.getByText(EMAIL_UNDER_TEST).first()).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

      const pageText = await page.locator("body").innerText();
      if (!/\b[A-Za-z]{2,}\s+[A-Za-z]{2,}\b/.test(pageText)) {
        throw new Error("User name pattern was not detected on the page.");
      }
    });

    await runValidation("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });

    await runValidation("Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

      const negocioRows = page.locator("li, tr, [role='row']");
      if ((await negocioRows.count()) < 1) {
        throw new Error("Business list rows are not visible.");
      }
    });

    await runValidation("Terminos y Condiciones", async () => {
      await captureLegalPage({
        appPage: page,
        linkRegex: /T.rminos y Condiciones|Terminos y Condiciones/i,
        expectedHeadingRegex: /T.rminos y Condiciones|Terminos y Condiciones/i,
        screenshotName: "step-8-terminos-y-condiciones.png",
        details,
        detailKey: "terminosUrl",
        testInfo,
      });
    });

    await runValidation("Politica de Privacidad", async () => {
      await captureLegalPage({
        appPage: page,
        linkRegex: /Pol.tica de Privacidad|Politica de Privacidad/i,
        expectedHeadingRegex: /Pol.tica de Privacidad|Politica de Privacidad/i,
        screenshotName: "step-9-politica-de-privacidad.png",
        details,
        detailKey: "politicaPrivacidadUrl",
        testInfo,
      });
    });

    const workflowReport = {
      report,
      details,
      generatedAt: new Date().toISOString(),
    };

    const artifactsDir = path.join(process.cwd(), "e2e-artifacts");
    await fs.mkdir(artifactsDir, { recursive: true });
    const reportPath = path.join(artifactsDir, "saleads-mi-negocio-report.json");
    await fs.writeFile(reportPath, JSON.stringify(workflowReport, null, 2), "utf8");

    await testInfo.attach("saleads-mi-negocio-report", {
      path: reportPath,
      contentType: "application/json",
    });

    console.table(report);
    console.log(JSON.stringify(details, null, 2));

    const failedKeys = Object.entries(report)
      .filter(([, status]) => status !== "PASS")
      .map(([key]) => key);

    expect(
      failedKeys,
      `Failed validations: ${failedKeys.join(", ")}. See e2e-artifacts/saleads-mi-negocio-report.json`
    ).toEqual([]);
  });
});
