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
  "Política de Privacidad",
];

async function waitForUiToSettle(page) {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 8000 }),
    page.waitForTimeout(1200),
  ]).catch(() => {});
  await page.waitForTimeout(350);
}

async function firstVisibleLocator(page, regex) {
  const candidates = [
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByRole("tab", { name: regex }),
    page.getByRole("heading", { name: regex }),
    page.getByText(regex),
  ];

  for (const locator of candidates) {
    const count = await locator.count();
    if (!count) {
      continue;
    }
    for (let index = 0; index < count; index += 1) {
      const item = locator.nth(index);
      if (await item.isVisible().catch(() => false)) {
        return item;
      }
    }
  }
  return null;
}

async function clickByVisibleText(page, regex) {
  const target = await firstVisibleLocator(page, regex);
  if (!target) {
    throw new Error(`No visible element found for pattern ${regex}`);
  }
  await target.click();
  await waitForUiToSettle(page);
}

async function screenshotCheckpoint(page, testInfo, name, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(`${name}.png`),
    fullPage,
  });
}

async function checkVisible(page, regex) {
  const locator = await firstVisibleLocator(page, regex);
  return Boolean(locator);
}

async function recordStep(report, name, stepFn) {
  try {
    await stepFn();
    report[name] = "PASS";
  } catch (error) {
    report[name] = "FAIL";
    report.__errors.push(`${name}: ${error.message}`);
  }
}

async function trySelectGoogleAccount(page, email) {
  const accountItem = await firstVisibleLocator(
    page,
    new RegExp(email.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
  );

  if (accountItem) {
    await accountItem.click();
    await waitForUiToSettle(page);
  }
}

async function clickLegalAndValidate({
  page,
  context,
  linkRegex,
  headingRegex,
  report,
  reportKey,
  testInfo,
  screenshotName,
  urlStoreKey,
}) {
  const popupPromise = context
    .waitForEvent("page", { timeout: 7000 })
    .catch(() => null);

  await clickByVisibleText(page, linkRegex);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20000 });
    await waitForUiToSettle(popup);
    await expect(
      popup.getByRole("heading", { name: headingRegex }).first(),
    ).toBeVisible();
    const legalText = await popup.locator("body").innerText();
    expect(legalText.trim().length).toBeGreaterThan(120);
    report[urlStoreKey] = popup.url();
    await screenshotCheckpoint(popup, testInfo, screenshotName, true);
    await popup.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
    report[reportKey] = "PASS";
    return;
  }

  await expect(page.getByRole("heading", { name: headingRegex }).first()).toBeVisible();
  const legalText = await page.locator("body").innerText();
  expect(legalText.trim().length).toBeGreaterThan(120);
  report[urlStoreKey] = page.url();
  await screenshotCheckpoint(page, testInfo, screenshotName, true);

  await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUiToSettle(page);
  report[reportKey] = "PASS";
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
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
    termsFinalUrl: "",
    privacyFinalUrl: "",
    __errors: [],
  };

  if (page.url() === "about:blank" && process.env.SALEADS_BASE_URL) {
    await page.goto(process.env.SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  }

  // Step 1: Login with Google and validate main app shell.
  await recordStep(report, "Login", async () => {
    await clickByVisibleText(
      page,
      /(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)/i,
    );
    await trySelectGoogleAccount(page, "juanlucasbarbiergarzon@gmail.com");

    const sidebar = page.locator("aside").first();
    await expect(sidebar).toBeVisible({ timeout: 45000 });
    const negocioVisible = await checkVisible(page, /negocio/i);
    expect(negocioVisible).toBeTruthy();
    await screenshotCheckpoint(page, testInfo, "01-dashboard-loaded", true);
  });

  // Step 2: Open Mi Negocio menu and validate submenu items.
  await recordStep(report, "Mi Negocio menu", async () => {
    await clickByVisibleText(page, /^mi negocio$/i);
    const agregarVisible = await checkVisible(page, /^agregar negocio$/i);
    const administrarVisible = await checkVisible(page, /^administrar negocios$/i);
    expect(agregarVisible).toBeTruthy();
    expect(administrarVisible).toBeTruthy();
    await screenshotCheckpoint(page, testInfo, "02-mi-negocio-expanded", true);
  });

  // Step 3: Validate Agregar Negocio modal.
  await recordStep(report, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /^agregar negocio$/i);
    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();

    const nameField = page.getByLabel(/nombre del negocio/i);
    await expect(nameField).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await screenshotCheckpoint(page, testInfo, "03-agregar-negocio-modal", true);
    await nameField.click();
    await nameField.fill("Negocio Prueba Automatizacion");
    await page.getByRole("button", { name: /cancelar/i }).click();
    await waitForUiToSettle(page);
  });

  // Step 4: Open Administrar Negocios and validate sections.
  await recordStep(report, "Administrar Negocios view", async () => {
    if (!(await checkVisible(page, /^administrar negocios$/i))) {
      await clickByVisibleText(page, /^mi negocio$/i);
    }
    await clickByVisibleText(page, /^administrar negocios$/i);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();
    await screenshotCheckpoint(page, testInfo, "04-administrar-negocios-page", true);
  });

  // Step 5: Información General.
  await recordStep(report, "Información General", async () => {
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    await expect(page.getByText(/@/)).toBeVisible();

    const possibleUserName = await page.locator("h1, h2, h3, p, span").filter({ hasText: /[A-Za-z]{2,}\s+[A-Za-z]{2,}/ }).first();
    expect(await possibleUserName.isVisible().catch(() => false)).toBeTruthy();
  });

  // Step 6: Detalles de la Cuenta.
  await recordStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  // Step 7: Tus Negocios.
  await recordStep(report, "Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^agregar negocio$/i })).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
  });

  // Step 8: Términos y Condiciones.
  try {
    await clickLegalAndValidate({
      page,
      context,
      linkRegex: /t[eé]rminos y condiciones/i,
      headingRegex: /t[eé]rminos y condiciones/i,
      report,
      reportKey: "Términos y Condiciones",
      testInfo,
      screenshotName: "08-terminos-y-condiciones",
      urlStoreKey: "termsFinalUrl",
    });
  } catch (error) {
    report["Términos y Condiciones"] = "FAIL";
    report.__errors.push(`Términos y Condiciones: ${error.message}`);
  }

  // Step 9: Política de Privacidad.
  try {
    await clickLegalAndValidate({
      page,
      context,
      linkRegex: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      report,
      reportKey: "Política de Privacidad",
      testInfo,
      screenshotName: "09-politica-de-privacidad",
      urlStoreKey: "privacyFinalUrl",
    });
  } catch (error) {
    report["Política de Privacidad"] = "FAIL";
    report.__errors.push(`Política de Privacidad: ${error.message}`);
  }

  const reportPayload = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: REPORT_FIELDS.reduce((acc, field) => {
      acc[field] = report[field];
      return acc;
    }, {}),
    evidence: {
      termsFinalUrl: report.termsFinalUrl,
      privacyFinalUrl: report.privacyFinalUrl,
    },
    errors: report.__errors,
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_final_report.json");
  await fs.writeFile(reportPath, JSON.stringify(reportPayload, null, 2), "utf8");
  testInfo.attachments.push({
    name: "saleads-mi-negocio-final-report",
    contentType: "application/json",
    path: reportPath,
  });

  // Required final report for automation logs.
  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_START");
  console.log(JSON.stringify(reportPayload, null, 2));
  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_END");

  expect(report.__errors, report.__errors.join("\n")).toHaveLength(0);
});
