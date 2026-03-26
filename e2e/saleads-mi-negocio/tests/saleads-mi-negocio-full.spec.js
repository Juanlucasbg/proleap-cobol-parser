const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

const DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function toRegex(text) {
  return new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function firstVisible(candidates, timeoutMs = 7000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of candidates) {
      const current = locator.first();
      if (await current.isVisible().catch(() => false)) {
        return current;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 200));
  }

  throw new Error("No visible locator matched within timeout.");
}

async function captureCheckpoint(page, testInfo, name, fullPage = true) {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({
    path: screenshotPath,
    fullPage,
  });

  await testInfo.attach(`checkpoint:${name}`, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

function buildInitialReport() {
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

async function openLegalLinkAndValidate({
  page,
  context,
  testInfo,
  returnUrl,
  linkText,
  headingPattern,
  screenshotName,
}) {
  const legalLink = await firstVisible([
    page.getByRole("link", { name: toRegex(linkText) }),
    page.getByText(toRegex(linkText)),
  ]);

  const [maybeNewTab] = await Promise.all([
    context.waitForEvent("page", { timeout: 7000 }).catch(() => null),
    legalLink.click(),
  ]);

  const targetPage = maybeNewTab || page;
  await waitForUi(targetPage);

  const legalHeading = await firstVisible([
    targetPage.getByRole("heading", { name: headingPattern }),
    targetPage.getByText(headingPattern),
  ]);
  await expect(legalHeading).toBeVisible();

  const legalBodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(legalBodyText.length).toBeGreaterThan(200);

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (maybeNewTab) {
    await maybeNewTab.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    if (page.url() !== returnUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    }
    if (page.url() !== returnUrl && returnUrl) {
      await page.goto(returnUrl, { waitUntil: "domcontentloaded" }).catch(() => {});
    }
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.setTimeout(240000);

  const report = buildInitialReport();
  const errors = [];
  const evidence = {
    termsUrl: "",
    privacyUrl: "",
  };

  const baseUrl = process.env.SALEADS_BASE_URL;
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const expectedEmail = process.env.SALEADS_EXPECTED_USER_EMAIL || DEFAULT_GOOGLE_EMAIL;
  const expectedName = process.env.SALEADS_EXPECTED_USER_NAME;

  async function runStep(stepName, reportKey, action) {
    await test.step(stepName, async () => {
      try {
        await action();
        if (reportKey) {
          report[reportKey] = "PASS";
        }
      } catch (error) {
        const suffix = (reportKey || stepName).replace(/\s+/g, "-").toLowerCase();
        await captureCheckpoint(page, testInfo, `failed-${suffix}`, true).catch(() => {});
        errors.push({
          step: stepName,
          message: error instanceof Error ? error.message : String(error),
        });
      }
    });
  }

  await runStep("1) Login with Google", "Login", async () => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    } else if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);

    if (!loginUrl && !baseUrl && page.url() === "about:blank") {
      throw new Error("No URL provided and current browser page is blank.");
    }

    const googleButton = await firstVisible([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
      page.getByText(/google/i),
    ]);

    const [maybeGoogleTab] = await Promise.all([
      context.waitForEvent("page", { timeout: 7000 }).catch(() => null),
      googleButton.click(),
    ]);

    if (maybeGoogleTab) {
      await waitForUi(maybeGoogleTab);
      const accountChoice = maybeGoogleTab.getByText(expectedEmail, { exact: false }).first();
      if (await accountChoice.isVisible().catch(() => false)) {
        await accountChoice.click();
      }
    } else {
      const accountChoiceOnMainPage = page.getByText(expectedEmail, { exact: false }).first();
      if (await accountChoiceOnMainPage.isVisible().catch(() => false)) {
        await accountChoiceOnMainPage.click();
      }
    }
    await waitForUi(page);

    const appRoot = await firstVisible([page.locator("aside"), page.locator("nav"), page.locator("main")]);
    await expect(appRoot).toBeVisible();

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.locator("nav").filter({ hasText: /negocio|dashboard|inicio/i }),
      page.locator("nav"),
    ]);
    await expect(sidebar).toBeVisible();

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded", true);
  });

  await runStep("2) Open Mi Negocio menu", "Mi Negocio menu", async () => {
    const negocioMenu = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    await negocioMenu.click();
    await waitForUi(page);

    const miNegocioOption = await firstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    await miNegocioOption.click();
    await waitForUi(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded", true);
  });

  await runStep("3) Validate Agregar Negocio modal", "Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await agregarNegocio.click();
    await waitForUi(page);

    const modal = await firstVisible([
      page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
      page.locator("[role='dialog'], .modal").filter({ hasText: /Crear Nuevo Negocio/i }),
    ]);
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/^Crear Nuevo Negocio$/i).first()).toBeVisible();

    const nombreInput = await firstVisible([
      modal.getByLabel(/^Nombre del Negocio$/i),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.locator("input").first(),
    ]);

    await expect(modal.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(modal.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(modal.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatizacion");
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal", true);

    await modal.getByRole("button", { name: /^Cancelar$/i }).first().click();
    await waitForUi(page);
  });

  await runStep("4) Open Administrar Negocios", "Administrar Negocios view", async () => {
    const administrarOption = page.getByText(/^Administrar Negocios$/i).first();
    if (!(await administrarOption.isVisible().catch(() => false))) {
      const miNegocioOption = await firstVisible([
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);
      await miNegocioOption.click();
      await waitForUi(page);
    }

    await page.getByText(/^Administrar Negocios$/i).first().click();
    await waitForUi(page);

    await expect(page.getByText(/^Información General$/i).first()).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page", true);
  });

  await runStep("5) Validate Información General", "Información General", async () => {
    const infoSection = page.locator("section, div").filter({ hasText: /^Información General$/i }).first();
    await expect(infoSection).toBeVisible();

    if (expectedName) {
      await expect(page.getByText(new RegExp(escapeRegex(expectedName), "i")).first()).toBeVisible();
    } else {
      const profileTexts = (await infoSection.innerText())
        .split("\n")
        .map((entry) => entry.trim())
        .filter((entry) => entry.length > 2);
      expect(profileTexts.length).toBeGreaterThan(2);
    }

    await expect(page.getByText(new RegExp(escapeRegex(expectedEmail), "i")).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cambiar Plan$/i }).first()).toBeVisible();
  });

  await runStep("6) Validate Detalles de la Cuenta", "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("7) Validate Tus Negocios", "Tus Negocios", async () => {
    const negociosSection = page.locator("section, div").filter({ hasText: /^Tus Negocios$/i }).first();
    await expect(negociosSection).toBeVisible();

    const businessListContainer = await firstVisible([
      negociosSection.locator("ul, ol, table, [role='list'], [role='table']"),
      negociosSection.locator("li, [role='listitem'], tr, article, .business-item"),
    ]);
    await expect(businessListContainer).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runStep("8) Validate Términos y Condiciones", "Términos y Condiciones", async () => {
    const returnUrl = page.url();
    evidence.termsUrl = await openLegalLinkAndValidate({
      page,
      context,
      testInfo,
      returnUrl,
      linkText: "Términos y Condiciones",
      headingPattern: /Términos y Condiciones/i,
      screenshotName: "08-terminos-y-condiciones",
    });
  });

  await runStep("9) Validate Política de Privacidad", "Política de Privacidad", async () => {
    const returnUrl = page.url();
    evidence.privacyUrl = await openLegalLinkAndValidate({
      page,
      context,
      testInfo,
      returnUrl,
      linkText: "Política de Privacidad",
      headingPattern: /Política de Privacidad/i,
      screenshotName: "09-politica-de-privacidad",
    });
  });

  await test.step("10) Final report", async () => {
    const finalReport = {
      name: "saleads_mi_negocio_full_test",
      result: report,
      evidence,
      errors,
      generatedAt: new Date().toISOString(),
    };

    const reportPath = testInfo.outputPath("final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    // Printed summary for CI logs.
    console.log(JSON.stringify(finalReport, null, 2));
  });

  expect(errors, `One or more validations failed. ${JSON.stringify(errors, null, 2)}`).toEqual([]);
});
