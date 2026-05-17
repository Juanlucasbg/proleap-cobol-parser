const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_BUSINESS_NAME = "Negocio Prueba Automatización";

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

function createInitialReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }]),
  );
}

function sanitizeFilename(name) {
  return name.toLowerCase().replace(/[^a-z0-9-_]+/g, "-");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
}

async function isVisible(locator, timeout = 3000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function firstVisible(...locators) {
  for (const locator of locators) {
    const target = locator.first();
    if (await target.isVisible().catch(() => false)) {
      return target;
    }
  }

  return null;
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const checkpointPath = testInfo.outputPath(`${sanitizeFilename(name)}.png`);
  await page.screenshot({ path: checkpointPath, fullPage });
  await testInfo.attach(name, {
    path: checkpointPath,
    contentType: "image/png",
  });
}

async function assertUserNameVisible(page) {
  const expectedName = process.env.SALEADS_EXPECTED_USER_NAME;
  if (expectedName) {
    await expect(page.getByText(expectedName, { exact: false })).toBeVisible();
    return;
  }

  const candidateLabels = page.locator("main h1, main h2, main h3, main strong, main span, main p");
  const maxCandidates = Math.min(await candidateLabels.count(), 80);

  for (let index = 0; index < maxCandidates; index += 1) {
    const text = (await candidateLabels.nth(index).innerText()).trim();
    if (!text || text.length < 3 || text.length > 90) {
      continue;
    }
    if (/@/.test(text)) {
      continue;
    }
    if (
      /información general|detalles de la cuenta|tus negocios|sección legal|business plan|cambiar plan|cuenta creada|estado activo|idioma seleccionado|agregar negocio|administrar negocios/i.test(
        text,
      )
    ) {
      continue;
    }
    if (/^[A-Za-zÁÉÍÓÚÑáéíóúñ'`´\- ]+$/.test(text)) {
      return;
    }
  }

  throw new Error(
    "Could not confidently detect the user name. Set SALEADS_EXPECTED_USER_NAME for strict validation.",
  );
}

async function openAndValidateLegalPage({
  page,
  context,
  testInfo,
  linkText,
  headingPattern,
  screenshotName,
}) {
  const legalLink = await firstVisible(
    page.getByRole("link", { name: linkText }),
    page.getByRole("button", { name: linkText }),
    page.getByText(linkText),
  );

  if (!legalLink) {
    throw new Error(`Could not find legal link: ${linkText}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await legalLink.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const targetPage = popup || page;

  if (popup) {
    await targetPage.waitForLoadState("domcontentloaded");
    await waitForUi(targetPage);
  }

  const headingByRole = targetPage.getByRole("heading", { name: headingPattern });
  if (await isVisible(headingByRole, 7000)) {
    await expect(headingByRole).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingPattern)).toBeVisible({ timeout: 15000 });
  }

  await expect(targetPage.locator("main p, article p, p").first()).toBeVisible({ timeout: 15000 });
  await captureCheckpoint(targetPage, testInfo, screenshotName, true);

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createInitialReport();
  const failedFields = [];
  const legalUrls = {};

  const runStep = async (fieldName, stepFn) => {
    try {
      const details = await stepFn();
      report[fieldName] = { status: "PASS", details: details || "" };
    } catch (error) {
      report[fieldName] = { status: "FAIL", details: error.message };
      failedFields.push(fieldName);
    }
  };

  const loginUrl =
    process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;

  await runStep("Login", async () => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Browser is on about:blank. Provide SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) or pre-open the SaleADS login page before running.",
      );
    }

    const loginButton = await firstVisible(
      page.getByRole("button", {
        name: /sign in with google|iniciar sesión con google|iniciar sesion con google|continuar con google|google/i,
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesión con google|iniciar sesion con google|continuar con google|google/i,
      }),
      page.getByText(
        /sign in with google|iniciar sesión con google|iniciar sesion con google|continuar con google/i,
      ),
    );

    if (!loginButton) {
      throw new Error("Could not find a login trigger for Google sign-in.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 15000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);
    const popup = await popupPromise;

    const googlePage = popup || page;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await waitForUi(popup);
    }

    const accountOption = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
    if (await isVisible(accountOption, 10000)) {
      await accountOption.click();
      await waitForUi(googlePage);
    }

    if (popup) {
      await page.bringToFront();
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav, [role='navigation']").first()).toBeVisible({
      timeout: 90000,
    });
    await expect(page.locator("main").first()).toBeVisible({ timeout: 90000 });

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisible(
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
    );
    if (negocioSection) {
      await negocioSection.click();
      await waitForUi(page);
    }

    const miNegocioOption = await firstVisible(
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
    );

    if (!miNegocioOption) {
      throw new Error("Could not find the 'Mi Negocio' option in the left sidebar.");
    }

    await miNegocioOption.click();
    await waitForUi(page);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible({ timeout: 15000 });
    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusinessOption = await firstVisible(
      page.getByRole("button", { name: /Agregar Negocio/i }),
      page.getByRole("link", { name: /Agregar Negocio/i }),
      page.getByText(/^Agregar Negocio$/i),
    );

    if (!addBusinessOption) {
      throw new Error("Could not find the 'Agregar Negocio' option.");
    }

    await addBusinessOption.click();
    await waitForUi(page);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 15000 });

    let businessNameInput = page.getByLabel(/Nombre del Negocio/i);
    if (!(await isVisible(businessNameInput, 3000))) {
      businessNameInput = page.getByPlaceholder(/Nombre del Negocio/i);
    }

    await expect(businessNameInput).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 15000 });

    const cancelButton = page.getByRole("button", { name: /Cancelar/i });
    const createButton = page.getByRole("button", { name: /Crear Negocio/i });

    await expect(cancelButton).toBeVisible({ timeout: 15000 });
    await expect(createButton).toBeVisible({ timeout: 15000 });
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");

    await businessNameInput.click();
    await businessNameInput.fill(TEST_BUSINESS_NAME);
    await cancelButton.click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const adminVisible = await isVisible(page.getByText(/Administrar Negocios/i), 2000);
    if (!adminVisible) {
      const miNegocioOption = await firstVisible(
        page.getByText(/^Mi Negocio$/i),
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
      );
      if (miNegocioOption) {
        await miNegocioOption.click();
        await waitForUi(page);
      }
    }

    const manageBusinessOption = await firstVisible(
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/^Administrar Negocios$/i),
    );

    if (!manageBusinessOption) {
      throw new Error("Could not find the 'Administrar Negocios' option.");
    }

    await manageBusinessOption.click();
    await waitForUi(page);

    await expect(page.getByText(/Información General/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Sección Legal/i)).toBeVisible({ timeout: 30000 });
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page", true);
  });

  await runStep("Información General", async () => {
    await assertUserNameVisible(page);
    await expect(page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first()).toBeVisible(
      { timeout: 15000 },
    );
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({
      timeout: 15000,
    });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Estado activo/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 15000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 15000 });

    const addBusinessControl = await firstVisible(
      page.getByRole("button", { name: /Agregar Negocio/i }),
      page.getByRole("link", { name: /Agregar Negocio/i }),
      page.getByText(/^Agregar Negocio$/i),
    );
    if (!addBusinessControl) {
      throw new Error("Could not find the 'Agregar Negocio' control in 'Tus Negocios'.");
    }

    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 15000 });

    const candidateRows = page.locator(
      "main li, main [role='row'], main table tbody tr, main [data-testid*='business']",
    );
    if ((await candidateRows.count()) === 0) {
      throw new Error("Could not detect business list entries in 'Tus Negocios'.");
    }
  });

  await runStep("Términos y Condiciones", async () => {
    const termsUrl = await openAndValidateLegalPage({
      page,
      context,
      testInfo,
      linkText: /Términos y Condiciones/i,
      headingPattern: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones",
    });
    legalUrls.terminosYCondiciones = termsUrl;
    return `Final URL: ${termsUrl}`;
  });

  await runStep("Política de Privacidad", async () => {
    const privacyUrl = await openAndValidateLegalPage({
      page,
      context,
      testInfo,
      linkText: /Política de Privacidad/i,
      headingPattern: /Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad",
    });
    legalUrls.politicaDePrivacidad = privacyUrl;
    return `Final URL: ${privacyUrl}`;
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    loginUrl: loginUrl || null,
    legalUrls,
    results: report,
  };

  const outputReportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  fs.writeFileSync(outputReportPath, JSON.stringify(finalReport, null, 2), "utf8");

  const sharedReportDir = path.join(process.cwd(), "test-results");
  fs.mkdirSync(sharedReportDir, { recursive: true });
  fs.writeFileSync(
    path.join(sharedReportDir, "saleads-mi-negocio-report.json"),
    JSON.stringify(finalReport, null, 2),
    "utf8",
  );

  console.log("Final report for saleads_mi_negocio_full_test");
  for (const field of REPORT_FIELDS) {
    const entry = report[field];
    const detailsText = entry.details ? ` | ${entry.details}` : "";
    console.log(`${field}: ${entry.status}${detailsText}`);
  }

  expect(
    failedFields,
    `One or more SaleADS workflow validations failed: ${failedFields.join(", ")}`,
  ).toEqual([]);
});
