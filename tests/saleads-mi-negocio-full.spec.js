const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

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
  "Política de Privacidad",
];

function createReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }]),
  );
}

function screenshotPath(fileName) {
  return path.resolve("test-results", "saleads-mi-negocio", fileName);
}

async function saveScreenshot(page, fileName, fullPage = false) {
  const filePath = screenshotPath(fileName);
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
}

async function firstVisible(page, locators, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      try {
        if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
          return locator.first();
        }
      } catch (error) {
        // Keep polling across all candidates.
      }
    }

    await page.waitForTimeout(250);
  }

  return null;
}

async function clickByText(page, texts, description) {
  for (const text of texts) {
    const matcher = new RegExp(text, "i");
    const locator = await firstVisible(page, [
      page.getByRole("button", { name: matcher }),
      page.getByRole("link", { name: matcher }),
      page.getByText(matcher),
    ]);

    if (locator) {
      await locator.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Could not click ${description}.`);
}

async function assertTextVisible(page, textRegex, description) {
  const locator = page.getByText(textRegex).first();
  await expect(locator, `${description} should be visible`).toBeVisible();
}

async function ensureMiNegocioExpanded(page) {
  const addBusinessVisible = await firstVisible(page, [page.getByText(/Agregar Negocio/i)], 2000);
  const adminBusinessVisible = await firstVisible(page, [page.getByText(/Administrar Negocios/i)], 2000);

  if (addBusinessVisible && adminBusinessVisible) {
    return;
  }

  await clickByText(page, ["Mi Negocio"], "Mi Negocio");
  await assertTextVisible(page, /Agregar Negocio/i, "Agregar Negocio");
  await assertTextVisible(page, /Administrar Negocios/i, "Administrar Negocios");
}

async function runStep(report, key, fn) {
  try {
    const details = await fn();
    report[key] = { status: "PASS", details: details || "Validated successfully" };
  } catch (error) {
    report[key] = { status: "FAIL", details: error.message };
  }
}

async function openLegalDocument({
  page,
  report,
  reportKey,
  linkTextRegex,
  headingRegex,
  screenshotName,
}) {
  const context = page.context();
  const link = await firstVisible(page, [
    page.getByRole("link", { name: linkTextRegex }),
    page.getByText(linkTextRegex),
  ]);

  if (!link) {
    throw new Error(`Link ${linkTextRegex} not found.`);
  }

  const newPagePromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await link.click();
  await waitForUi(page);

  let targetPage = page;
  const maybeNewPage = await newPagePromise;

  if (maybeNewPage) {
    targetPage = maybeNewPage;
    await waitForUi(targetPage);
  }

  await assertTextVisible(targetPage, headingRegex, `Heading ${headingRegex}`);
  const screenshot = await saveScreenshot(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (targetPage !== page) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  report[reportKey] = {
    status: "PASS",
    details: "Validated legal document page",
    url: finalUrl,
    screenshot,
  };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_LOGIN_URL (or SALEADS_URL). Test must start from the SaleADS login page.",
    );
  }

  await runStep(report, "Login", async () => {
    const loginButton = await firstVisible(page, [
      page.getByRole("button", { name: /Sign in with Google|Iniciar sesi[oó]n con Google|Google/i }),
      page.getByRole("link", { name: /Sign in with Google|Iniciar sesi[oó]n con Google|Google/i }),
      page.getByText(/Sign in with Google|Iniciar sesi[oó]n con Google|Google/i),
    ]);

    if (!loginButton) {
      throw new Error("Google login button was not found.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    const googlePage = popup || page;

    const accountOption = await firstVisible(googlePage, [
      googlePage.getByText(GOOGLE_ACCOUNT_EMAIL),
      googlePage.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
    ], 10000);

    if (accountOption) {
      await accountOption.click();
      await waitForUi(googlePage);
    }

    if (popup) {
      await page.bringToFront();
    }

    const sidebar = await firstVisible(page, [
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/Negocio|Mi Negocio/i),
    ], 30000);

    if (!sidebar) {
      throw new Error("Main interface/sidebar did not appear after login.");
    }

    const screenshot = await saveScreenshot(page, "01-dashboard-loaded.png", true);
    return `Dashboard loaded and sidebar visible. Screenshot: ${screenshot}`;
  });

  await runStep(report, "Mi Negocio menu", async () => {
    await clickByText(page, ["Negocio"], "Negocio section");
    await clickByText(page, ["Mi Negocio"], "Mi Negocio option");
    await assertTextVisible(page, /Agregar Negocio/i, "Agregar Negocio");
    await assertTextVisible(page, /Administrar Negocios/i, "Administrar Negocios");
    const screenshot = await saveScreenshot(page, "02-mi-negocio-menu-expanded.png");
    return `Mi Negocio menu expanded correctly. Screenshot: ${screenshot}`;
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    await ensureMiNegocioExpanded(page);
    await clickByText(page, ["Agregar Negocio"], "Agregar Negocio option");
    await assertTextVisible(page, /Crear Nuevo Negocio/i, "Crear Nuevo Negocio title");

    const nameInput = await firstVisible(page, [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
      page.locator("input[name*='nombre'], input[id*='nombre']"),
    ]);

    if (!nameInput) {
      throw new Error("Input field 'Nombre del Negocio' was not found.");
    }

    await assertTextVisible(page, /Tienes 2 de 3 negocios/i, "Business count text");
    await assertTextVisible(page, /Cancelar/i, "Cancelar button");
    await assertTextVisible(page, /Crear Negocio/i, "Crear Negocio button");

    const screenshot = await saveScreenshot(page, "03-agregar-negocio-modal.png");
    await nameInput.fill("Negocio Prueba Automatización");
    await clickByText(page, ["Cancelar"], "Cancelar modal button");

    return `Agregar Negocio modal validated. Screenshot: ${screenshot}`;
  });

  await runStep(report, "Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);
    await clickByText(page, ["Administrar Negocios"], "Administrar Negocios option");

    await assertTextVisible(page, /Información General/i, "Información General section");
    await assertTextVisible(page, /Detalles de la Cuenta/i, "Detalles de la Cuenta section");
    await assertTextVisible(page, /Tus Negocios/i, "Tus Negocios section");
    await assertTextVisible(page, /Sección Legal/i, "Sección Legal section");

    const screenshot = await saveScreenshot(page, "04-administrar-negocios-view-full.png", true);
    return `Administrar Negocios page loaded. Screenshot: ${screenshot}`;
  });

  await runStep(report, "Información General", async () => {
    await assertTextVisible(page, /Información General/i, "Información General section");
    await assertTextVisible(page, /BUSINESS PLAN/i, "BUSINESS PLAN text");
    await assertTextVisible(page, /Cambiar Plan/i, "Cambiar Plan button");

    const userIdentity = await firstVisible(page, [
      page.getByText(/@/),
      page.getByText(/Nombre|Name|Usuario/i),
    ], 10000);

    if (!userIdentity) {
      throw new Error("User name/email was not detected in Información General.");
    }

    return "User identity, plan, and action button are visible.";
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await assertTextVisible(page, /Detalles de la Cuenta/i, "Detalles de la Cuenta section");
    await assertTextVisible(page, /Cuenta creada/i, "Cuenta creada");
    await assertTextVisible(page, /Estado activo/i, "Estado activo");
    await assertTextVisible(page, /Idioma seleccionado/i, "Idioma seleccionado");
    return "Account details fields are visible.";
  });

  await runStep(report, "Tus Negocios", async () => {
    await assertTextVisible(page, /Tus Negocios/i, "Tus Negocios section");
    await assertTextVisible(page, /Agregar Negocio/i, "Agregar Negocio button");
    await assertTextVisible(page, /Tienes 2 de 3 negocios/i, "Business count text");

    const businessListVisible = await firstVisible(page, [
      page.locator("[role='list']"),
      page.locator("table"),
      page.getByText(/Negocio/i),
    ]);

    if (!businessListVisible) {
      throw new Error("Business list is not visible.");
    }

    return "Business list and controls are visible.";
  });

  try {
    await openLegalDocument({
      page,
      report,
      reportKey: "Términos y Condiciones",
      linkTextRegex: /Términos y Condiciones/i,
      headingRegex: /Términos y Condiciones/i,
      screenshotName: "08-terminos-y-condiciones.png",
    });
  } catch (error) {
    report["Términos y Condiciones"] = { status: "FAIL", details: error.message };
  }

  try {
    await openLegalDocument({
      page,
      report,
      reportKey: "Política de Privacidad",
      linkTextRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "09-politica-de-privacidad.png",
    });
  } catch (error) {
    report["Política de Privacidad"] = { status: "FAIL", details: error.message };
  }

  const reportOutputPath = path.resolve("test-results", "saleads-mi-negocio", "final-report.json");
  fs.mkdirSync(path.dirname(reportOutputPath), { recursive: true });
  fs.writeFileSync(reportOutputPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: JSON.stringify(report, null, 2),
    contentType: "application/json",
  });

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(JSON.stringify(report, null, 2));

  const failedFields = Object.entries(report).filter(([, result]) => result.status !== "PASS");
  expect(
    failedFields,
    `The following workflow validations failed:\n${JSON.stringify(failedFields, null, 2)}`,
  ).toEqual([]);
});
