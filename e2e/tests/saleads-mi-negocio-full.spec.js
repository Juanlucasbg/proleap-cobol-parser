const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_NAME = "Negocio Prueba Automatización";
const BUSINESS_LIMIT_TEXT = "Tienes 2 de 3 negocios";

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

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible({ timeout: 20_000 });
  await locator.click();
  await waitForUiToSettle(page);
}

async function firstVisibleLocator(candidates) {
  for (const candidate of candidates) {
    const target = candidate.first();
    if (await target.isVisible().catch(() => false)) {
      return target;
    }
  }

  return null;
}

function buildInitialReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = { status: "FAIL", details: "Step not executed." };
  }
  return report;
}

function markPass(report, field, details = "Validation completed.") {
  report[field] = { status: "PASS", details };
}

function markFail(report, field, error) {
  report[field] = {
    status: "FAIL",
    details: error instanceof Error ? error.message : String(error)
  };
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function openAndValidateLegalLink({
  page,
  context,
  linkText,
  headingRegex,
  screenshotFileName,
  testInfo
}) {
  const link = page.getByRole("link", { name: new RegExp(linkText, "i") }).first();
  await expect(link).toBeVisible({ timeout: 20_000 });

  const newPagePromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await link.click();
  await waitForUiToSettle(page);

  const maybeNewPage = await newPagePromise;
  const legalPage = maybeNewPage || page;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});

  await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({
    timeout: 20_000
  });

  const legalBodyText = await legalPage.locator("body").innerText();
  expect(legalBodyText.trim().length, "Expected visible legal content text").toBeGreaterThan(120);

  const screenshotPath = await captureCheckpoint(legalPage, testInfo, screenshotFileName, true);
  const finalUrl = legalPage.url();

  if (maybeNewPage) {
    await maybeNewPage.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToSettle(page);
  }

  return { screenshotPath, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = buildInitialReport();

  if (page.url() === "about:blank") {
    const startUrl = process.env.SALEADS_START_URL || process.env.SALEADS_BASE_URL;
    if (!startUrl) {
      throw new Error(
        "No initial page is open. Set SALEADS_START_URL (or SALEADS_BASE_URL) to the login page in the current environment."
      );
    }
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUiToSettle(page);

  try {
    const loginButton = await firstVisibleLocator([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.locator("button, a").filter({ hasText: /google/i })
    ]);

    if (!loginButton) {
      throw new Error("Could not find login button or 'Sign in with Google' action.");
    }

    const authPopupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const authPage = await authPopupPromise;

    if (authPage) {
      await authPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
      const accountOption = authPage.getByText(GOOGLE_ACCOUNT_EMAIL).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(accountOption, authPage);
      }
      await authPage.waitForClose({ timeout: 30_000 }).catch(() => {});
      await page.bringToFront();
    } else {
      const inlineAccountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
      if (await inlineAccountOption.isVisible().catch(() => false)) {
        await clickAndWait(inlineAccountOption, page);
      }
    }

    await waitForUiToSettle(page);
    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 40_000 });
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 40_000 });

    const screenshotPath = await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
    markPass(report, "Login", `Dashboard and left sidebar visible. Screenshot: ${screenshotPath}`);
  } catch (error) {
    markFail(report, "Login", error);
  }

  try {
    const negocioSection = await firstVisibleLocator([
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i)
    ]);
    if (!negocioSection) {
      throw new Error("Could not locate 'Negocio' section in left sidebar.");
    }

    await clickAndWait(negocioSection, page);

    const miNegocioOption = await firstVisibleLocator([
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i)
    ]);
    if (!miNegocioOption) {
      throw new Error("Could not locate 'Mi Negocio' option.");
    }

    await clickAndWait(miNegocioOption, page);
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 20_000 });

    const screenshotPath = await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", false);
    markPass(report, "Mi Negocio menu", `Submenu expanded with required options. Screenshot: ${screenshotPath}`);
  } catch (error) {
    markFail(report, "Mi Negocio menu", error);
  }

  try {
    const agregarNegocioOption = await firstVisibleLocator([
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i)
    ]);
    if (!agregarNegocioOption) {
      throw new Error("Could not locate 'Agregar Negocio' option.");
    }

    await clickAndWait(agregarNegocioOption, page);

    const modalTitle = page.getByRole("heading", { name: /crear nuevo negocio/i }).first();
    await expect(modalTitle).toBeVisible({ timeout: 20_000 });
    await expect(page.getByLabel(/nombre del negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(BUSINESS_LIMIT_TEXT).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible({ timeout: 20_000 });

    const screenshotPath = await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", false);

    const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    await businessNameInput.click();
    await businessNameInput.fill(BUSINESS_NAME);
    await clickAndWait(page.getByRole("button", { name: /cancelar/i }).first(), page);

    markPass(report, "Agregar Negocio modal", `Modal validated and closed. Screenshot: ${screenshotPath}`);
  } catch (error) {
    markFail(report, "Agregar Negocio modal", error);
  }

  try {
    const administrarNegociosOption = page.getByText(/^administrar negocios$/i).first();
    if (!(await administrarNegociosOption.isVisible().catch(() => false))) {
      const miNegocioOption = page.getByText(/^mi negocio$/i).first();
      if (await miNegocioOption.isVisible().catch(() => false)) {
        await clickAndWait(miNegocioOption, page);
      }
    }

    await clickAndWait(page.getByText(/^administrar negocios$/i).first(), page);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible({ timeout: 30_000 });

    const screenshotPath = await captureCheckpoint(page, testInfo, "04-administrar-negocios-view.png", true);
    markPass(report, "Administrar Negocios view", `Account page sections visible. Screenshot: ${screenshotPath}`);
  } catch (error) {
    markFail(report, "Administrar Negocios view", error);
  }

  try {
    await expect(page.getByText(/business plan/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({
      timeout: 20_000
    });

    const visibleText = await page.locator("body").innerText();
    const hasName = /nombre|usuario|user|perfil/i.test(visibleText);
    const hasEmail = /@/.test(visibleText);

    if (!hasName) {
      throw new Error("Could not confirm visible user name in 'Información General'.");
    }
    if (!hasEmail) {
      throw new Error("Could not confirm visible user email in 'Información General'.");
    }

    markPass(report, "Información General");
  } catch (error) {
    markFail(report, "Información General", error);
  }

  try {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
    markPass(report, "Detalles de la Cuenta");
  } catch (error) {
    markFail(report, "Detalles de la Cuenta", error);
  }

  try {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible({
      timeout: 20_000
    });
    await expect(page.getByText(BUSINESS_LIMIT_TEXT).first()).toBeVisible({ timeout: 20_000 });
    markPass(report, "Tus Negocios");
  } catch (error) {
    markFail(report, "Tus Negocios", error);
  }

  try {
    const { screenshotPath, finalUrl } = await openAndValidateLegalLink({
      page,
      context,
      linkText: "Términos y Condiciones",
      headingRegex: /t[ée]rminos y condiciones/i,
      screenshotFileName: "05-terminos-y-condiciones.png",
      testInfo
    });

    markPass(
      report,
      "Términos y Condiciones",
      `Heading and legal text validated. Screenshot: ${screenshotPath}. URL: ${finalUrl}`
    );
  } catch (error) {
    markFail(report, "Términos y Condiciones", error);
  }

  try {
    const { screenshotPath, finalUrl } = await openAndValidateLegalLink({
      page,
      context,
      linkText: "Política de Privacidad",
      headingRegex: /pol[íi]tica de privacidad/i,
      screenshotFileName: "06-politica-de-privacidad.png",
      testInfo
    });

    markPass(
      report,
      "Política de Privacidad",
      `Heading and legal text validated. Screenshot: ${screenshotPath}. URL: ${finalUrl}`
    );
  } catch (error) {
    markFail(report, "Política de Privacidad", error);
  }

  const orderedReport = REPORT_FIELDS.map((field) => ({
    field,
    status: report[field].status,
    details: report[field].details
  }));

  const humanReadableReport = [
    "SaleADS Mi Negocio workflow final report",
    ...orderedReport.map((entry) => `- ${entry.field}: ${entry.status} (${entry.details})`)
  ].join("\n");

  await testInfo.attach("saleads-mi-negocio-final-report.json", {
    body: Buffer.from(JSON.stringify(orderedReport, null, 2), "utf-8"),
    contentType: "application/json"
  });

  await testInfo.attach("saleads-mi-negocio-final-report.txt", {
    body: Buffer.from(humanReadableReport, "utf-8"),
    contentType: "text/plain"
  });

  const failedEntries = orderedReport.filter((entry) => entry.status === "FAIL");
  expect(
    failedEntries,
    `One or more required workflow validations failed:\n${JSON.stringify(orderedReport, null, 2)}`
  ).toEqual([]);
});
