const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const STATUS_KEYS = [
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

function createStatusReport() {
  return Object.fromEntries(STATUS_KEYS.map((key) => [key, "FAIL"]));
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => null);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => null);
}

async function takeCheckpoint(page, testInfo, fileName, fullPage = false) {
  await waitForUi(page);
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function firstVisibleLocator(candidates, timeout = 3500) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    const visible = await locator.isVisible({ timeout }).catch(() => false);
    if (visible) {
      return locator;
    }
  }
  return null;
}

async function assertTextVisible(page, textPattern, timeout = 12000) {
  await expect(page.getByText(textPattern).first()).toBeVisible({ timeout });
}

test("SaleADS Mi Negocio complete workflow", async ({ page, context }, testInfo) => {
  const baseUrl =
    process.env.SALEADS_BASE_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    process.env.PLAYWRIGHT_TEST_BASE_URL;

  if (!baseUrl) {
    throw new Error(
      "Missing URL. Set SALEADS_BASE_URL (or SALEADS_URL/BASE_URL) to the SaleADS login page for the current environment."
    );
  }

  const report = createStatusReport();
  const failures = [];
  const legalUrls = {
    "Términos y Condiciones": null,
    "Política de Privacidad": null,
  };

  const recordStep = async (name, stepFn) => {
    try {
      await stepFn();
      report[name] = "PASS";
    } catch (error) {
      report[name] = "FAIL";
      failures.push(`${name}: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await recordStep("Login", async () => {
    const loginButton = await firstVisibleLocator([
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
      page.getByRole("button", { name: /continuar con google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[oó]n con google/i),
      page.getByText(/google/i),
    ]);

    if (!loginButton) {
      throw new Error("Could not locate Google login button.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const googlePage = await popupPromise;
    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded", { timeout: 30000 });
      const accountOption = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL).first();
      const accountVisible = await accountOption.isVisible({ timeout: 8000 }).catch(() => false);
      if (accountVisible) {
        await accountOption.click();
        await waitForUi(googlePage);
      }
    }

    await waitForUi(page);
    const sidebar = await firstVisibleLocator([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("[class*='sidebar']"),
    ]);

    if (!sidebar) {
      throw new Error("Main application sidebar was not visible after login.");
    }

    await takeCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await recordStep("Mi Negocio menu", async () => {
    const miNegocioToggle = await firstVisibleLocator([
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i),
      page.getByText(/mi negocio/i),
      page.getByText(/negocio/i),
    ]);

    if (!miNegocioToggle) {
      throw new Error("Mi Negocio menu was not found in the sidebar.");
    }

    await miNegocioToggle.click();
    await waitForUi(page);

    await assertTextVisible(page, /^Agregar Negocio$/i);
    await assertTextVisible(page, /^Administrar Negocios$/i);

    await takeCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await recordStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisibleLocator([
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i),
    ]);
    if (!agregarNegocio) {
      throw new Error("Could not find Agregar Negocio action.");
    }

    await agregarNegocio.click();
    await waitForUi(page);

    await assertTextVisible(page, /crear nuevo negocio/i);
    await assertTextVisible(page, /nombre del negocio/i);
    await assertTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i);
    await assertTextVisible(page, /^cancelar$/i);
    await assertTextVisible(page, /crear negocio/i);

    const businessNameField = await firstVisibleLocator([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.getByRole("textbox", { name: /nombre del negocio/i }),
      page.locator("input[type='text']"),
    ]);
    if (businessNameField) {
      await businessNameField.click();
      await businessNameField.fill("Negocio Prueba Automatización");
    }

    await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const cancelarButton = await firstVisibleLocator([
      page.getByRole("button", { name: /^cancelar$/i }),
      page.getByText(/^cancelar$/i),
    ]);
    if (!cancelarButton) {
      throw new Error("Cancel button was not found in Agregar Negocio modal.");
    }
    await cancelarButton.click();
    await waitForUi(page);
  });

  await recordStep("Administrar Negocios view", async () => {
    const administrarOption = await firstVisibleLocator([
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByText(/^administrar negocios$/i),
    ]);

    if (!administrarOption) {
      const miNegocioToggle = await firstVisibleLocator([
        page.getByText(/^mi negocio$/i),
        page.getByText(/mi negocio/i),
      ]);
      if (miNegocioToggle) {
        await miNegocioToggle.click();
        await waitForUi(page);
      }
    }

    const administrarVisible = await firstVisibleLocator([
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByText(/^administrar negocios$/i),
    ]);

    if (!administrarVisible) {
      throw new Error("Administrar Negocios option is not visible.");
    }

    await administrarVisible.click();
    await waitForUi(page);

    await assertTextVisible(page, /informaci[oó]n general/i);
    await assertTextVisible(page, /detalles de la cuenta/i);
    await assertTextVisible(page, /tus negocios/i);
    await assertTextVisible(page, /secci[oó]n legal/i);

    await takeCheckpoint(page, testInfo, "04-administrar-negocios-view.png", true);
  });

  await recordStep("Información General", async () => {
    await assertTextVisible(page, /@/i);
    await assertTextVisible(page, /business plan/i);
    await assertTextVisible(page, /cambiar plan/i);
  });

  await recordStep("Detalles de la Cuenta", async () => {
    await assertTextVisible(page, /cuenta creada/i);
    await assertTextVisible(page, /estado activo/i);
    await assertTextVisible(page, /idioma seleccionado/i);
  });

  await recordStep("Tus Negocios", async () => {
    await assertTextVisible(page, /tus negocios/i);
    await assertTextVisible(page, /^agregar negocio$/i);
    await assertTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i);
  });

  const validateLegalLink = async (stepName, linkPattern, headingPattern, screenshotName) => {
    await recordStep(stepName, async () => {
      const sourcePage = page;
      const legalLink = await firstVisibleLocator([
        sourcePage.getByRole("link", { name: linkPattern }),
        sourcePage.getByText(linkPattern),
      ]);
      if (!legalLink) {
        throw new Error(`Could not find link for ${stepName}.`);
      }

      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await legalLink.click();
      await waitForUi(sourcePage);

      const popup = await popupPromise;
      const targetPage = popup ?? sourcePage;
      await waitForUi(targetPage);

      await expect(targetPage.getByText(headingPattern).first()).toBeVisible({ timeout: 15000 });

      const legalBodyText = (await targetPage.locator("body").innerText()).trim();
      if (legalBodyText.length < 80) {
        throw new Error(`${stepName} content appears too short.`);
      }

      legalUrls[stepName] = targetPage.url();
      await takeCheckpoint(targetPage, testInfo, screenshotName, true);

      if (popup) {
        await popup.close();
        await sourcePage.bringToFront();
      } else {
        await targetPage.goBack().catch(() => null);
        await waitForUi(sourcePage);
      }
    });
  };

  await validateLegalLink(
    "Términos y Condiciones",
    /t[eé]rminos y condiciones/i,
    /t[eé]rminos y condiciones/i,
    "08-terminos-y-condiciones.png"
  );

  await validateLegalLink(
    "Política de Privacidad",
    /pol[ií]tica de privacidad/i,
    /pol[ií]tica de privacidad/i,
    "09-politica-de-privacidad.png"
  );

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    configuredBaseUrl: baseUrl,
    results: report,
    legalUrls,
    failures,
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.mkdir(path.dirname(reportPath), { recursive: true });
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log(`Final Report: ${JSON.stringify(finalReport, null, 2)}`);
  expect(
    Object.values(report).every((stepResult) => stepResult === "PASS"),
    `At least one workflow step failed: ${JSON.stringify(finalReport, null, 2)}`
  ).toBeTruthy();
});
