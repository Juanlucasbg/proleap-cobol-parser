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

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function isVisible(locator) {
  try {
    return await locator.isVisible();
  } catch (_error) {
    return false;
  }
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 45000 });
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
}

async function clickByVisibleText(page, text) {
  const exactTextRegex = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
  const partialTextRegex = new RegExp(escapeRegex(text), "i");
  const roles = ["button", "link", "menuitem", "tab", "option"];

  for (const role of roles) {
    const exactRoleLocator = page.getByRole(role, { name: exactTextRegex }).first();
    if (await isVisible(exactRoleLocator)) {
      await exactRoleLocator.click();
      return;
    }
  }

  for (const role of roles) {
    const partialRoleLocator = page.getByRole(role, { name: partialTextRegex }).first();
    if (await isVisible(partialRoleLocator)) {
      await partialRoleLocator.click();
      return;
    }
  }

  const exactTextLocator = page.getByText(exactTextRegex).first();
  if (await isVisible(exactTextLocator)) {
    await exactTextLocator.click();
    return;
  }

  const partialTextLocator = page.getByText(partialTextRegex).first();
  await expect(partialTextLocator).toBeVisible();
  await partialTextLocator.click();
}

async function clickFirstMatchingText(page, textCandidates) {
  let lastError;

  for (const text of textCandidates) {
    try {
      await clickByVisibleText(page, text);
      return text;
    } catch (error) {
      lastError = error;
    }
  }

  throw (
    lastError ||
    new Error(`Could not find clickable element for texts: ${textCandidates.join(", ")}`)
  );
}

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

async function runStep(stepName, report, details, failures, action) {
  try {
    await action();
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = "FAIL";
    details[stepName] = error instanceof Error ? error.message : String(error);
    failures.push(`${stepName}: ${details[stepName]}`);
  }
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function selectGoogleAccountIfShown(page, accountEmail) {
  await waitForUiLoad(page);

  const accountLocator = page.getByText(new RegExp(escapeRegex(accountEmail), "i")).first();
  if (await isVisible(accountLocator)) {
    await accountLocator.click();
    await waitForUiLoad(page);
  }
}

async function openAndValidateLegalLink({
  page,
  context,
  linkText,
  headingText,
  screenshotName,
  testInfo,
}) {
  const appUrlBefore = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);

  await clickByVisibleText(page, linkText);
  await waitForUiLoad(page);

  const popup = await popupPromise;
  const legalPage = popup || page;

  await waitForUiLoad(legalPage);

  const headingRegex = new RegExp(escapeRegex(headingText), "i");
  const heading = legalPage.getByRole("heading", { name: headingRegex }).first();
  if (await isVisible(heading)) {
    await expect(heading).toBeVisible();
  } else {
    await expect(legalPage.getByText(headingRegex).first()).toBeVisible();
  }

  const contentLocator = legalPage
    .locator("main p, article p, p, [role='main'] p")
    .filter({ hasText: /\S+/ })
    .first();
  await expect(contentLocator).toBeVisible();

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else if (page.url() !== appUrlBefore) {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 30000 }).catch(() => null);
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.slow();

  const report = createReport();
  const details = {};
  const failures = [];
  const evidence = {};
  const accountEmail = "juanlucasbarbiergarzon@gmail.com";
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || "";

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  }

  await runStep("Login", report, details, failures, async () => {
    const sidebarLocator = page.locator("aside, [role='navigation']").first();
    if (!(await isVisible(sidebarLocator))) {
      const loginTexts = [
        "Sign in with Google",
        "Iniciar sesion con Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Login with Google",
      ];

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickFirstMatchingText(page, loginTexts);
      await waitForUiLoad(page);

      const popup = await popupPromise;
      if (popup) {
        await waitForUiLoad(popup);
        await selectGoogleAccountIfShown(popup, accountEmail);
        await popup.waitForEvent("close", { timeout: 60000 }).catch(() => {});
      } else {
        await selectGoogleAccountIfShown(page, accountEmail);
      }
    }

    await waitForUiLoad(page);
    await expect(page.locator("main, [role='main']").first()).toBeVisible();
    await expect(page.locator("aside, [role='navigation']").first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
    evidence.dashboardScreenshot = testInfo.outputPath("01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", report, details, failures, async () => {
    await expect(page.locator("aside, [role='navigation']").first()).toBeVisible();

    if (await isVisible(page.getByText(/Negocio/i).first())) {
      await clickByVisibleText(page, "Negocio");
      await waitForUiLoad(page);
    }

    await clickByVisibleText(page, "Mi Negocio");
    await waitForUiLoad(page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png", false);
    evidence.miNegocioScreenshot = testInfo.outputPath("02-mi-negocio-expanded.png");
  });

  await runStep("Agregar Negocio modal", report, details, failures, async () => {
    await clickByVisibleText(page, "Agregar Negocio");
    await waitForUiLoad(page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", false);
    evidence.agregarNegocioModalScreenshot = testInfo.outputPath("03-agregar-negocio-modal.png");

    const nameField = page.getByLabel(/Nombre del Negocio/i).first();
    if (await isVisible(nameField)) {
      await nameField.click();
      await nameField.fill("Negocio Prueba Automatización");
    }

    await clickByVisibleText(page, "Cancelar");
    await waitForUiLoad(page);
  });

  await runStep("Administrar Negocios view", report, details, failures, async () => {
    if (!(await isVisible(page.getByText(/Administrar Negocios/i).first()))) {
      await clickByVisibleText(page, "Mi Negocio");
      await waitForUiLoad(page);
    }

    await clickByVisibleText(page, "Administrar Negocios");
    await waitForUiLoad(page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-view.png", true);
    evidence.administrarNegociosScreenshot = testInfo.outputPath("04-administrar-negocios-view.png");
  });

  await runStep("Información General", report, details, failures, async () => {
    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const bodyText = await page.locator("body").innerText();
    const emailMatch = bodyText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    expect(emailMatch, "Expected user email to be visible").toBeTruthy();

    const nameSignals = [
      page.getByText(/Nombre|Usuario/i).first(),
      page.getByText(/Juan|Lucas|Barbier|Garzon/i).first(),
    ];
    const hasNameSignal = (await isVisible(nameSignals[0])) || (await isVisible(nameSignals[1]));
    expect(hasNameSignal, "Expected user name to be visible").toBeTruthy();
  });

  await runStep("Detalles de la Cuenta", report, details, failures, async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", report, details, failures, async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runStep("Términos y Condiciones", report, details, failures, async () => {
    evidence.terminosUrl = await openAndValidateLegalLink({
      page,
      context,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo,
    });
  });

  await runStep("Política de Privacidad", report, details, failures, async () => {
    evidence.politicaPrivacidadUrl = await openAndValidateLegalLink({
      page,
      context,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotName: "06-politica-de-privacidad.png",
      testInfo,
    });
  });

  const finalReport = {
    report,
    details,
    evidence,
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.table(
    REPORT_FIELDS.map((field) => ({
      check: field,
      status: report[field],
      detail: details[field] || "",
    })),
  );

  if (evidence.terminosUrl) {
    console.log(`Términos y Condiciones URL: ${evidence.terminosUrl}`);
  }
  if (evidence.politicaPrivacidadUrl) {
    console.log(`Política de Privacidad URL: ${evidence.politicaPrivacidadUrl}`);
  }

  if (failures.length > 0) {
    throw new Error(`One or more validations failed:\n- ${failures.join("\n- ")}`);
  }
});
