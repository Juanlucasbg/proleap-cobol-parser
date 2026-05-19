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

const EMAIL_REGEX = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;

function createReport() {
  return REPORT_FIELDS.reduce((acc, key) => {
    acc[key] = "FAIL";
    return acc;
  }, {});
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiLoad(page);
}

async function saveCheckpoint(page, testInfo, filename, fullPage = false) {
  const filePath = testInfo.outputPath(filename);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(filename, { path: filePath, contentType: "image/png" });
}

function safeError(error) {
  if (!error) {
    return "Unknown error";
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return String(error);
}

async function runStep(report, failures, key, action) {
  try {
    await action();
    report[key] = "PASS";
  } catch (error) {
    report[key] = "FAIL";
    failures.push({ step: key, error: safeError(error) });
  }
}

async function ensureMiNegocioExpanded(page) {
  const addBusiness = page.getByText(/^Agregar Negocio$/i).first();
  const manageBusiness = page.getByText(/^Administrar Negocios$/i).first();
  const alreadyExpanded =
    (await addBusiness.isVisible().catch(() => false)) &&
    (await manageBusiness.isVisible().catch(() => false));

  if (alreadyExpanded) {
    return;
  }

  const negocio = page.getByText(/^Negocio$/i).first();
  if (await negocio.isVisible().catch(() => false)) {
    await clickAndWait(negocio, page);
  }

  const miNegocio = page.getByText(/^Mi Negocio$/i).first();
  await clickAndWait(miNegocio, page);
}

async function resolveLegalLink(legalSection, linkText) {
  const byRole = legalSection.getByRole("link", { name: new RegExp(linkText, "i") }).first();
  if (await byRole.isVisible().catch(() => false)) {
    return byRole;
  }

  return legalSection.getByText(new RegExp(linkText, "i")).first();
}

async function resolveGoogleLoginControl(page) {
  const buttonCandidate = page
    .getByRole("button", { name: /google|iniciar sesi[oó]n|sign in/i })
    .first();
  if (await buttonCandidate.isVisible().catch(() => false)) {
    return buttonCandidate;
  }

  const linkCandidate = page
    .getByRole("link", { name: /google|iniciar sesi[oó]n|sign in/i })
    .first();
  if (await linkCandidate.isVisible().catch(() => false)) {
    return linkCandidate;
  }

  return page.getByText(/google|iniciar sesi[oó]n|sign in/i).first();
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  const failures = [];
  const legalUrls = {};
  const loginUrl = process.env.SALEADS_LOGIN_URL;

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_LOGIN_URL or start the test with an already opened SaleADS login page."
    );
  }

  await runStep(report, failures, "Login", async () => {
    const signInWithGoogle = await resolveGoogleLoginControl(page);

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(signInWithGoogle, page);
    const popup = await popupPromise;

    if (popup) {
      await waitForUiLoad(popup);
      const account = popup.getByText("juanlucasbarbiergarzon@gmail.com").first();
      if (await account.isVisible().catch(() => false)) {
        await clickAndWait(account, popup);
      }
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      const accountInCurrentPage = page.getByText("juanlucasbarbiergarzon@gmail.com").first();
      if (await accountInCurrentPage.isVisible().catch(() => false)) {
        await clickAndWait(accountInCurrentPage, page);
      }
    }

    await expect(page.getByText(/Mi Negocio|Negocio/i).first()).toBeVisible({ timeout: 60000 });
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await saveCheckpoint(page, testInfo, "01_dashboard_loaded.png", true);
  });

  await runStep(report, failures, "Mi Negocio menu", async () => {
    await ensureMiNegocioExpanded(page);
    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();
    await saveCheckpoint(page, testInfo, "02_mi_negocio_menu_expanded.png");
  });

  await runStep(report, failures, "Agregar Negocio modal", async () => {
    await clickAndWait(page.getByText(/^Agregar Negocio$/i).first(), page);
    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeVisible();

    const inputByLabel = page.getByLabel(/Nombre del Negocio/i).first();
    const inputByPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i).first();
    const businessNameInput = (await inputByLabel.isVisible().catch(() => false))
      ? inputByLabel
      : inputByPlaceholder;

    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();
    await saveCheckpoint(page, testInfo, "03_agregar_negocio_modal.png");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }), page);
    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeHidden();
  });

  await runStep(report, failures, "Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);
    await clickAndWait(page.getByText(/^Administrar Negocios$/i).first(), page);
    await expect(page.getByText(/^Información General$/i)).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
    await expect(page.getByText(/^Sección Legal$/i)).toBeVisible();
    await saveCheckpoint(page, testInfo, "04_administrar_negocios_page.png", true);
  });

  await runStep(report, failures, "Información General", async () => {
    const infoSection = page
      .locator("section, div, article")
      .filter({ hasText: /Información General/i })
      .first();
    await expect(infoSection).toBeVisible();

    const sectionText = await infoSection.innerText();
    const lines = sectionText
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean);

    const email = lines.find((line) => EMAIL_REGEX.test(line));
    const userName = lines.find(
      (line) =>
        !EMAIL_REGEX.test(line) &&
        !/informaci[oó]n general|business plan|cambiar plan/i.test(line) &&
        line.length >= 3
    );

    expect(userName, "User name must be visible in Información General").toBeTruthy();
    expect(email, "User email must be visible in Información General").toBeTruthy();
    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runStep(report, failures, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep(report, failures, "Tus Negocios", async () => {
    const businessSection = page
      .locator("section, div, article")
      .filter({ hasText: /Tus Negocios/i })
      .first();

    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const sectionText = await businessSection.innerText();
    expect(
      sectionText.length > 60,
      "Business list content should be visible in Tus Negocios section"
    ).toBeTruthy();
  });

  await runStep(report, failures, "Términos y Condiciones", async () => {
    const legalSection = page
      .locator("section, div, article")
      .filter({ hasText: /Sección Legal/i })
      .first();
    await expect(legalSection).toBeVisible();

    const legalLink = await resolveLegalLink(legalSection, "Términos y Condiciones");
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    const navigationPromise = page
      .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 10000 })
      .catch(() => null);

    await legalLink.click();
    const popup = await popupPromise;

    let legalPage = page;
    if (popup) {
      legalPage = popup;
      await waitForUiLoad(legalPage);
    } else {
      await navigationPromise;
      await waitForUiLoad(page);
    }

    await expect(legalPage.getByText(/Términos y Condiciones/i).first()).toBeVisible();
    const legalText = await legalPage.locator("body").innerText();
    expect(legalText.replace(/\s+/g, " ").length > 200, "Legal content should be visible").toBe(
      true
    );
    await saveCheckpoint(legalPage, testInfo, "05_terminos_y_condiciones.png", true);
    legalUrls["Términos y Condiciones"] = legalPage.url();

    if (popup) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    }
  });

  await runStep(report, failures, "Política de Privacidad", async () => {
    const legalSection = page
      .locator("section, div, article")
      .filter({ hasText: /Sección Legal/i })
      .first();
    await expect(legalSection).toBeVisible();

    const legalLink = await resolveLegalLink(legalSection, "Política de Privacidad");
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    const navigationPromise = page
      .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 10000 })
      .catch(() => null);

    await legalLink.click();
    const popup = await popupPromise;

    let legalPage = page;
    if (popup) {
      legalPage = popup;
      await waitForUiLoad(legalPage);
    } else {
      await navigationPromise;
      await waitForUiLoad(page);
    }

    await expect(legalPage.getByText(/Política de Privacidad/i).first()).toBeVisible();
    const legalText = await legalPage.locator("body").innerText();
    expect(legalText.replace(/\s+/g, " ").length > 200, "Legal content should be visible").toBe(
      true
    );
    await saveCheckpoint(legalPage, testInfo, "06_politica_de_privacidad.png", true);
    legalUrls["Política de Privacidad"] = legalPage.url();

    if (popup) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    }
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls,
    failures
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("saleads_mi_negocio_report.json", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log("Final validation report:\n" + JSON.stringify(finalReport, null, 2));

  expect(
    failures,
    `One or more SaleADS Mi Negocio validations failed:\n${JSON.stringify(failures, null, 2)}`
  ).toEqual([]);
});
