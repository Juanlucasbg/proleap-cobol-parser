const fs = require("fs/promises");
const { test, expect } = require("@playwright/test");

const REPORT_KEYS = [
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function clickAndWaitForUi(page, locator, waitMs = 1000) {
  await expect(locator).toBeVisible();
  await locator.click();
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(waitMs);
}

async function firstVisibleLocator(page, candidates, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await page.waitForTimeout(400);
  }

  throw new Error("No expected visible locator found before timeout.");
}

async function ensureHeadingOrText(page, regex) {
  const heading = page.getByRole("heading", { name: regex }).first();
  if (await heading.isVisible().catch(() => false)) {
    return;
  }

  const text = page.getByText(regex).first();
  await expect(text).toBeVisible();
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_KEYS.map((key) => [key, "FAIL"]));
  const details = {};
  const legalUrls = {};

  async function checkpointScreenshot(fileName, targetPage = page, fullPage = false) {
    await targetPage.screenshot({
      path: testInfo.outputPath(fileName),
      fullPage
    });
  }

  async function runValidationStep(stepName, callback) {
    try {
      await callback();
      report[stepName] = "PASS";
    } catch (error) {
      details[stepName] = error instanceof Error ? error.message : String(error);
      report[stepName] = "FAIL";
    }
  }

  await runValidationStep("Login", async () => {
    const loginButton = await firstVisibleLocator(page, [
      page.getByRole("button", { name: /Sign in with Google/i }),
      page.getByRole("button", { name: /Iniciar sesión con Google/i }),
      page.getByRole("button", { name: /Continuar con Google/i }),
      page.getByRole("button", { name: /Google/i }),
      page.getByText(/Sign in with Google/i),
      page.getByText(/Iniciar sesión con Google/i)
    ]);

    const [googlePopup] = await Promise.all([
      context.waitForEvent("page", { timeout: 8000 }).catch(() => null),
      clickAndWaitForUi(page, loginButton)
    ]);

    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      const accountOption = googlePopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWaitForUi(googlePopup, accountOption, 1200);
      }
      await googlePopup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
    } else {
      const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWaitForUi(page, accountOption, 1200);
      }
    }

    await page.bringToFront().catch(() => {});
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 45000 });
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 45000 });
    await checkpointScreenshot("01-dashboard-loaded.png", page, true);
  });

  await runValidationStep("Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible();

    const negocioSection = await firstVisibleLocator(page, [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ]);
    await clickAndWaitForUi(page, negocioSection);

    const miNegocioOption = await firstVisibleLocator(page, [
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);
    await clickAndWaitForUi(page, miNegocioOption);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await checkpointScreenshot("02-mi-negocio-expanded-menu.png");
  });

  await runValidationStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisibleLocator(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await clickAndWaitForUi(page, agregarNegocio);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();

    const nombreInput = await firstVisibleLocator(page, [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[placeholder*='Nombre del Negocio']"),
      page.locator("input[name*='negocio' i]")
    ]);

    await expect(nombreInput).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();
    await checkpointScreenshot("03-agregar-negocio-modal.png");

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    const cancelarButton = page.getByRole("button", { name: /^Cancelar$/i }).first();
    await clickAndWaitForUi(page, cancelarButton);
  });

  await runValidationStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocioOption = await firstVisibleLocator(page, [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ]);
      await clickAndWaitForUi(page, miNegocioOption);
    }

    const administrarNegocios = await firstVisibleLocator(page, [
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);
    await clickAndWaitForUi(page, administrarNegocios, 1500);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 30000 });
    await checkpointScreenshot("04-administrar-negocios-account-page.png", page, true);
  });

  await runValidationStep("Información General", async () => {
    await expect(page.getByText(/Información General/i).first()).toBeVisible();

    const emailInView = page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first();
    await expect(emailInView).toBeVisible();

    const nameSignals = [
      page.getByText(/Nombre/i).first(),
      page.getByText(/Perfil/i).first(),
      page.getByText(/Usuario/i).first()
    ];

    let hasNameSignal = false;
    for (const candidate of nameSignals) {
      if (await candidate.isVisible().catch(() => false)) {
        hasNameSignal = true;
        break;
      }
    }

    if (!hasNameSignal) {
      throw new Error("No user-name related text could be confirmed in Información General.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runValidationStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runValidationStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  async function validateLegalLink(stepName, linkRegex, headingRegex, screenshotName, urlKey) {
    await runValidationStep(stepName, async () => {
      const legalLink = await firstVisibleLocator(page, [
        page.getByRole("link", { name: linkRegex }),
        page.getByRole("button", { name: linkRegex }),
        page.getByText(linkRegex)
      ]);

      const [newTab] = await Promise.all([
        context.waitForEvent("page", { timeout: 8000 }).catch(() => null),
        legalLink.click()
      ]);

      const legalPage = newTab || page;
      await legalPage.waitForLoadState("domcontentloaded");
      await legalPage.waitForTimeout(1200);

      await ensureHeadingOrText(legalPage, headingRegex);
      const bodyText = await legalPage.locator("body").innerText();
      if (!bodyText || bodyText.trim().length < 120) {
        throw new Error("Legal content text is not sufficiently visible.");
      }

      legalUrls[urlKey] = legalPage.url();
      await checkpointScreenshot(screenshotName, legalPage, true);

      if (newTab) {
        await newTab.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await page.waitForTimeout(700);
      }
    });
  }

  await validateLegalLink(
    "Términos y Condiciones",
    /Términos y Condiciones/i,
    /Términos y Condiciones/i,
    "05-terminos-y-condiciones.png",
    "Términos y Condiciones URL"
  );

  await validateLegalLink(
    "Política de Privacidad",
    /Política de Privacidad/i,
    /Política de Privacidad/i,
    "06-politica-de-privacidad.png",
    "Política de Privacidad URL"
  );

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    statusByField: report,
    legalUrls,
    failureDetails: details
  };

  await fs.writeFile(testInfo.outputPath("final-report.json"), JSON.stringify(finalReport, null, 2));
  await testInfo.attach("final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });
  console.log(`FINAL_REPORT ${JSON.stringify(finalReport)}`);

  const failed = Object.entries(report).filter(([, status]) => status !== "PASS");
  expect(failed, `One or more workflow validations failed. Details: ${JSON.stringify(details)}`).toEqual([]);
});
