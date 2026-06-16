const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

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

function createInitialReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: [] };
    return acc;
  }, {});
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }
  return null;
}

async function expectVisibleText(page, regex, errorMessage) {
  await expect(
    page.getByText(regex).first(),
    errorMessage
  ).toBeVisible({ timeout: 20000 });
}

test("SaleADS Mi Negocio full workflow", async ({ page, context }, testInfo) => {
  const report = createInitialReport();
  const errors = [];
  const evidence = {
    screenshots: [],
    legalUrls: {}
  };

  const addDetail = (field, detail) => report[field].details.push(detail);

  const step = async (field, action) => {
    try {
      await action();
      report[field].status = "PASS";
    } catch (error) {
      errors.push(`${field}: ${error.message}`);
      addDetail(field, `Error: ${error.message}`);
      report[field].status = "FAIL";
    }
  };

  const screenshot = async (name, fullPage = false) => {
    const file = testInfo.outputPath(name);
    await page.screenshot({ path: file, fullPage });
    evidence.screenshots.push(file);
    return file;
  };

  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL;

  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) so the test can open the current environment login page without hardcoding a domain."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  let appUrl = page.url();

  await step("Login", async () => {
    const loginButton = await firstVisibleLocator([
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|google/i
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|google/i
      }),
      page.locator("button, a").filter({
        hasText: /sign in with google|iniciar sesi[oó]n con google|google/i
      })
    ]);

    expect(loginButton, "Google login button should be visible").not.toBeNull();

    const popupPromise = context
      .waitForEvent("page", { timeout: 12000 })
      .catch(() => null);

    await loginButton.click();
    await waitForUi(page);

    const googlePage = await popupPromise;

    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded");

      const accountCandidate = await firstVisibleLocator([
        googlePage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }),
        googlePage.locator("div[role='link'], li, button").filter({
          hasText: "juanlucasbarbiergarzon@gmail.com"
        })
      ]);

      if (accountCandidate) {
        await accountCandidate.click();
      }
    }

    await waitForUi(page);
    await expect(
      page.locator("main, [role='main']").first(),
      "Main interface should appear after login"
    ).toBeVisible({ timeout: 45000 });

    const sidebar = await firstVisibleLocator([
      page.locator("aside"),
      page.locator("nav").filter({ hasText: /negocio|mi negocio|dashboard/i }),
      page.locator("[class*='sidebar']")
    ]);
    expect(sidebar, "Left sidebar navigation should be visible").not.toBeNull();

    const shot = await screenshot("01-dashboard-loaded.png");
    addDetail("Login", `Dashboard screenshot: ${shot}`);
  });

  await step("Mi Negocio menu", async () => {
    const negocioEntry = await firstVisibleLocator([
      page.getByRole("button", { name: /mi negocio|negocio/i }),
      page.getByRole("link", { name: /mi negocio|negocio/i }),
      page.locator("a, button, div").filter({ hasText: /^Mi Negocio$/i })
    ]);

    expect(negocioEntry, "Mi Negocio menu entry should be visible").not.toBeNull();
    await negocioEntry.click();
    await waitForUi(page);

    await expectVisibleText(
      page,
      /Agregar Negocio/i,
      "Agregar Negocio should be visible"
    );
    await expectVisibleText(
      page,
      /Administrar Negocios/i,
      "Administrar Negocios should be visible"
    );

    const shot = await screenshot("02-mi-negocio-menu-expanded.png");
    addDetail("Mi Negocio menu", `Expanded menu screenshot: ${shot}`);
  });

  await step("Agregar Negocio modal", async () => {
    const agregarNegocioAction = await firstVisibleLocator([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.locator("a, button, div").filter({ hasText: /^Agregar Negocio$/i })
    ]);

    expect(agregarNegocioAction, "Agregar Negocio action should be visible").not.toBeNull();
    await agregarNegocioAction.click();
    await waitForUi(page);

    await expectVisibleText(
      page,
      /Crear Nuevo Negocio/i,
      "Modal title should be visible"
    );

    const nombreInput = await firstVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator(
        "input[name*='nombre' i], input[placeholder*='Nombre' i], input[type='text']"
      )
    ]);

    expect(nombreInput, "Nombre del Negocio input should exist").not.toBeNull();
    await expectVisibleText(
      page,
      /Tienes\s+2\s+de\s+3\s+negocios/i,
      "Business quota text should be visible"
    );
    await expectVisibleText(page, /^Cancelar$/i, "Cancelar button should be visible");
    await expectVisibleText(page, /Crear Negocio/i, "Crear Negocio button should be visible");

    const shot = await screenshot("03-agregar-negocio-modal.png");
    addDetail("Agregar Negocio modal", `Modal screenshot: ${shot}`);

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");

    const cancelButton = await firstVisibleLocator([
      page.getByRole("button", { name: /^Cancelar$/i }),
      page.locator("button").filter({ hasText: /^Cancelar$/i })
    ]);
    expect(cancelButton, "Cancelar button should be available to close modal").not.toBeNull();
    await cancelButton.click();
    await waitForUi(page);
  });

  await step("Administrar Negocios view", async () => {
    const adminOption = await firstVisibleLocator([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.locator("a, button, div").filter({ hasText: /Administrar Negocios/i })
    ]);

    expect(adminOption, "Administrar Negocios option should be visible").not.toBeNull();
    await adminOption.click();
    await waitForUi(page);

    await expectVisibleText(page, /Informaci[oó]n General/i, "Información General should exist");
    await expectVisibleText(
      page,
      /Detalles de la Cuenta/i,
      "Detalles de la Cuenta should exist"
    );
    await expectVisibleText(page, /Tus Negocios/i, "Tus Negocios should exist");
    await expectVisibleText(page, /Secci[oó]n Legal/i, "Sección Legal should exist");

    appUrl = page.url();
    const shot = await screenshot("04-administrar-negocios-full.png", true);
    addDetail("Administrar Negocios view", `Account page screenshot: ${shot}`);
  });

  await step("Información General", async () => {
    await expectVisibleText(page, /BUSINESS PLAN/i, "BUSINESS PLAN should be visible");
    await expectVisibleText(page, /Cambiar Plan/i, "Cambiar Plan button should be visible");

    const emailCandidate = page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i").first();
    await expect(emailCandidate, "User email should be visible").toBeVisible({ timeout: 15000 });

    await expect(
      page.getByText(/juan|lucas|barbier/i).first(),
      "User name should be visible"
    ).toBeVisible({ timeout: 15000 });
  });

  await step("Detalles de la Cuenta", async () => {
    await expectVisibleText(page, /Cuenta creada/i, "Cuenta creada should be visible");
    await expectVisibleText(page, /Estado activo/i, "Estado activo should be visible");
    await expectVisibleText(
      page,
      /Idioma seleccionado/i,
      "Idioma seleccionado should be visible"
    );
  });

  await step("Tus Negocios", async () => {
    await expectVisibleText(page, /Tus Negocios/i, "Tus Negocios section should be visible");
    await expectVisibleText(page, /^Agregar Negocio$/i, "Agregar Negocio button should exist");
    await expectVisibleText(
      page,
      /Tienes\s+2\s+de\s+3\s+negocios/i,
      "Quota text should be visible in business section"
    );

    const businessEntry = await firstVisibleLocator([
      page.locator("ul li, [role='listitem']").filter({ hasText: /negocio/i }),
      page.locator("table tbody tr"),
      page.locator("[class*='business']").first()
    ]);
    expect(businessEntry, "Business list should be visible").not.toBeNull();
  });

  const validateLegalPage = async (field, linkRegex, headingRegex, screenshotName) => {
    const legalLink = await firstVisibleLocator([
      page.getByRole("link", { name: linkRegex }),
      page.getByRole("button", { name: linkRegex }),
      page.locator("a, button, span").filter({ hasText: linkRegex })
    ]);

    expect(legalLink, `${field} link should be visible`).not.toBeNull();

    const popupPromise = context
      .waitForEvent("page", { timeout: 6000 })
      .catch(() => null);

    const previousUrl = page.url();
    await legalLink.click();
    await waitForUi(page);

    const popup = await popupPromise;
    const legalPage = popup || page;

    await legalPage.waitForLoadState("domcontentloaded");
    await expect(
      legalPage.getByRole("heading", { name: headingRegex }).first(),
      `${field} heading should be visible`
    ).toBeVisible({ timeout: 30000 });

    await expect(
      legalPage.locator("main, article, body").filter({
        hasText: /t[eé]rminos|condiciones|privacidad|datos personales|legal/i
      }),
      `${field} legal content should be visible`
    ).toBeVisible({ timeout: 30000 });

    const targetForScreenshot = popup || page;
    const file = testInfo.outputPath(screenshotName);
    await targetForScreenshot.screenshot({ path: file, fullPage: true });
    evidence.screenshots.push(file);
    evidence.legalUrls[field] = legalPage.url();
    addDetail(field, `URL: ${legalPage.url()}`);
    addDetail(field, `Screenshot: ${file}`);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (page.url() !== previousUrl) {
      await page.goBack().catch(async () => {
        await page.goto(appUrl, { waitUntil: "domcontentloaded" });
      });
      await waitForUi(page);
    }
  };

  await step("Términos y Condiciones", async () => {
    await validateLegalPage(
      "Términos y Condiciones",
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "05-terminos-y-condiciones.png"
    );
  });

  await step("Política de Privacidad", async () => {
    await validateLegalPage(
      "Política de Privacidad",
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "06-politica-de-privacidad.png"
    );
  });

  const finalReport = {
    workflow: "saleads_mi_negocio_full_test",
    triggeredAt: new Date().toISOString(),
    environment: {
      loginUrl
    },
    results: report,
    evidence,
    errors
  };

  const reportFile = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportFile, JSON.stringify(finalReport, null, 2), "utf-8");
  console.log(`Final report written to ${reportFile}`);
  console.log(JSON.stringify(finalReport, null, 2));

  expect(errors, `Failed validations:\n${errors.join("\n")}`).toEqual([]);
});
