const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUiToLoad(page);
}

async function firstVisible(page, candidates, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of candidates) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }

    await page.waitForTimeout(200);
  }

  return null;
}

async function findClickableByText(page, textPattern, timeoutMs = 15000) {
  const locator = await firstVisible(
    page,
    [
      page.getByRole("button", { name: textPattern }).first(),
      page.getByRole("link", { name: textPattern }).first(),
      page.getByRole("menuitem", { name: textPattern }).first(),
      page.getByText(textPattern).first()
    ],
    timeoutMs
  );

  if (!locator) {
    throw new Error(`Could not find clickable element by text: ${textPattern}`);
  }

  return locator;
}

async function validateLegalDocument({
  page,
  context,
  linkText,
  headingText,
  screenshotName,
  testInfo
}) {
  const legalLink = await findClickableByText(page, linkText, 15000);
  const possibleNewPage = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickAndWait(page, legalLink);

  const newPage = await possibleNewPage;
  const legalPage = newPage || page;

  if (newPage) {
    await waitForUiToLoad(legalPage);
  }

  const headingLocator = await firstVisible(legalPage, [
    legalPage.getByRole("heading", { name: headingText }).first(),
    legalPage.getByText(headingText).first()
  ]);

  if (!headingLocator) {
    throw new Error(`No heading found for legal page: ${headingText}`);
  }

  await expect(headingLocator).toBeVisible();
  await expect(legalPage.locator("body")).toContainText(/\S{40,}/);

  await legalPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true
  });

  const finalUrl = legalPage.url();

  if (newPage) {
    await newPage.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else {
    await page.goBack().catch(() => {});
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const evidence = {};
  const errors = [];
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;

  async function runSection(field, action) {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      errors.push(`[${field}] ${error.message}`);
    }
  }

  await runSection("Login", async () => {
    if (page.url() === "about:blank") {
      if (!loginUrl) {
        throw new Error(
          "Page started at about:blank. Provide SALEADS_LOGIN_URL or SALEADS_BASE_URL to open the login page dynamically."
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUiToLoad(page);

    const loginButton = await findClickableByText(page, /sign in with google|iniciar sesi[oó]n con google|google/i);
    await clickAndWait(page, loginButton);

    const accountLocator = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();

    if (await accountLocator.isVisible().catch(() => false)) {
      await clickAndWait(page, accountLocator);
    }

    const mainInterface = await firstVisible(page, [
      page.locator("main").first(),
      page.locator("[role='main']").first(),
      page.locator("section").first()
    ]);
    const sidebar = await firstVisible(page, [
      page.getByRole("navigation").first(),
      page.locator("aside").first(),
      page.locator("nav").first()
    ]);

    if (!mainInterface || !sidebar) {
      throw new Error("Main app interface or left sidebar did not appear after login.");
    }

    await expect(mainInterface).toBeVisible();
    await expect(sidebar).toBeVisible();

    const dashboardShot = testInfo.outputPath("01_dashboard_loaded.png");
    await page.screenshot({ path: dashboardShot, fullPage: true });
    evidence.dashboardScreenshot = dashboardShot;
  });

  await runSection("Mi Negocio menu", async () => {
    const negocioSection = await findClickableByText(page, /negocio/i, 15000);
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await findClickableByText(page, /mi negocio/i, 15000);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

    const menuShot = testInfo.outputPath("02_mi_negocio_expanded_menu.png");
    await page.screenshot({ path: menuShot, fullPage: true });
    evidence.miNegocioExpandedScreenshot = menuShot;
  });

  await runSection("Agregar Negocio modal", async () => {
    const agregarNegocio = await findClickableByText(page, /agregar negocio/i, 15000);
    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();

    const nombreNegocioInput = await firstVisible(page, [
      page.getByLabel(/nombre del negocio/i).first(),
      page.getByPlaceholder(/nombre del negocio/i).first(),
      page.locator("input[name*='negocio' i]").first(),
      page.locator("input[id*='negocio' i]").first(),
      page.locator("input[type='text']").first()
    ]);

    if (!nombreNegocioInput) {
      throw new Error("Input field 'Nombre del Negocio' not found in modal.");
    }

    await expect(nombreNegocioInput).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    const modalShot = testInfo.outputPath("03_agregar_negocio_modal.png");
    await page.screenshot({ path: modalShot, fullPage: true });
    evidence.agregarNegocioModalScreenshot = modalShot;

    await nombreNegocioInput.click();
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }).first());
  });

  await runSection("Administrar Negocios view", async () => {
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      const miNegocioToggle = await findClickableByText(page, /mi negocio/i, 10000);
      await clickAndWait(page, miNegocioToggle);
    }

    const administrarNegocios = await findClickableByText(page, /administrar negocios/i, 15000);
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

    const accountShot = testInfo.outputPath("04_administrar_negocios_full_page.png");
    await page.screenshot({ path: accountShot, fullPage: true });
    evidence.administrarNegociosScreenshot = accountShot;
  });

  await runSection("Información General", async () => {
    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(GOOGLE_ACCOUNT_EMAIL).first()).toBeVisible();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();

    const hasNameLabel = await firstVisible(page, [
      page.getByText(/nombre/i).first(),
      page.getByText(/usuario/i).first(),
      page.getByText(/perfil/i).first()
    ]);

    if (!hasNameLabel) {
      throw new Error("User name label or value was not visible.");
    }
  });

  await runSection("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runSection("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();

    const businessList = await firstVisible(page, [
      page.locator("ul li").first(),
      page.locator("table tbody tr").first(),
      page.locator("[role='listitem']").first(),
      page.locator("[role='row']").first()
    ]);

    if (!businessList) {
      throw new Error("Business list was not visible in 'Tus Negocios'.");
    }
  });

  await runSection("Términos y Condiciones", async () => {
    const termsUrl = await validateLegalDocument({
      page,
      context,
      linkText: /t[eé]rminos y condiciones/i,
      headingText: /t[eé]rminos y condiciones/i,
      screenshotName: "05_terminos_y_condiciones.png",
      testInfo
    });

    evidence.terminosUrl = termsUrl;
  });

  await runSection("Política de Privacidad", async () => {
    const privacyUrl = await validateLegalDocument({
      page,
      context,
      linkText: /pol[ií]tica de privacidad/i,
      headingText: /pol[ií]tica de privacidad/i,
      screenshotName: "06_politica_de_privacidad.png",
      testInfo
    });

    evidence.politicaPrivacidadUrl = privacyUrl;
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    baseUrlUsed: loginUrl || null,
    report,
    evidence,
    errors
  };

  const reportPath = testInfo.outputPath("final_report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });
  console.log("FINAL_REPORT_JSON:", JSON.stringify(finalReport));

  const failedSteps = Object.entries(report)
    .filter(([, result]) => result !== "PASS")
    .map(([field]) => field);

  expect(
    failedSteps,
    `Some workflow validations failed: ${failedSteps.join(", ")}\nDetails: ${errors.join(" | ")}`
  ).toEqual([]);
});
