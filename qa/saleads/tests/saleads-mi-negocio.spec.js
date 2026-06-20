const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REQUIRED_REPORT_FIELDS = [
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

function safeFileSegment(value) {
  return value.replace(/[^a-zA-Z0-9-_]/g, "_");
}

async function clickAndSettle(page, locator) {
  await expect(locator).toBeVisible({ timeout: 30_000 });
  await locator.click();
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 8_000 }),
    page.waitForLoadState("domcontentloaded", { timeout: 8_000 }),
    page.waitForTimeout(1_200),
  ]).catch(() => {});
  await page.waitForTimeout(600);
}

function toResult(status, details) {
  return {
    status,
    details: details || "",
  };
}

async function firstVisibleLocator(page, candidates) {
  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }
  return null;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  test.setTimeout(10 * 60 * 1000);

  const startedAt = new Date();
  const runId = safeFileSegment(startedAt.toISOString());
  const artifactsDir = path.join(__dirname, "..", "artifacts", runId);
  fs.mkdirSync(artifactsDir, { recursive: true });

  const report = Object.fromEntries(
    REQUIRED_REPORT_FIELDS.map((field) => [field, toResult("FAIL", "Not executed")]),
  );

  let appPage = page;

  async function checkpoint(name, fullPage = false) {
    const filePath = path.join(artifactsDir, `${safeFileSegment(name)}.png`);
    await appPage.screenshot({ path: filePath, fullPage });
    return filePath;
  }

  async function setStepResult(stepName, fn) {
    try {
      const details = await fn();
      report[stepName] = toResult("PASS", details);
    } catch (error) {
      report[stepName] = toResult("FAIL", error.message);
    }
  }

  async function ensureOnLoginPageIfConfigured() {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await appPage.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await appPage.waitForLoadState("networkidle").catch(() => {});
    }
  }

  async function maybeChooseGoogleAccount(googlePage) {
    const accountLocator = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    if (await accountLocator.isVisible().catch(() => false)) {
      await clickAndSettle(googlePage, accountLocator);
      return "Google account selected";
    }
    return "Google selector not shown (possibly already authenticated)";
  }

  async function restoreAppPageFocus() {
    for (const openPage of context.pages()) {
      const sidebarCandidate = openPage
        .locator("aside, nav")
        .filter({ hasText: /Negocio|Mi Negocio|Dashboard/i })
        .first();
      if (await sidebarCandidate.isVisible().catch(() => false)) {
        appPage = openPage;
        await appPage.bringToFront();
        return;
      }
    }
    await appPage.bringToFront();
  }

  async function validateLegalLink(linkText, headingText, screenshotName, resultField) {
    const link = (await firstVisibleLocator(appPage, [
      appPage.getByRole("link", { name: linkText }).first(),
      appPage.getByRole("button", { name: linkText }).first(),
      appPage.getByText(linkText, { exact: true }).first(),
    ]));

    if (!link) {
      throw new Error(`No se encontró el enlace o botón "${linkText}"`);
    }

    const currentAppUrl = appPage.url();
    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

    await clickAndSettle(appPage, link);

    let legalPage = await popupPromise;
    let openedInNewTab = Boolean(legalPage);
    if (!legalPage) {
      legalPage = appPage;
      openedInNewTab = false;
    }

    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle").catch(() => {});

    await expect(
      legalPage.getByRole("heading", { name: new RegExp(headingText, "i") }).first(),
    ).toBeVisible({ timeout: 30_000 });

    const legalBody = await legalPage.locator("main, article, body").first().innerText();
    if (!legalBody || legalBody.trim().length < 120) {
      throw new Error(`${headingText}: no se encontró contenido legal suficiente`);
    }

    const legalScreenshot = path.join(artifactsDir, `${safeFileSegment(screenshotName)}.png`);
    await legalPage.screenshot({ path: legalScreenshot, fullPage: true });

    const finalUrl = legalPage.url();
    report[resultField] = toResult("PASS", `URL final: ${finalUrl} | screenshot: ${legalScreenshot}`);

    if (openedInNewTab) {
      await legalPage.close();
      await restoreAppPageFocus();
    } else {
      await appPage.goBack().catch(() => {});
      if (appPage.url() !== currentAppUrl) {
        await appPage.goto(currentAppUrl, { waitUntil: "domcontentloaded" }).catch(() => {});
      }
      await appPage.waitForLoadState("networkidle").catch(() => {});
    }
  }

  await ensureOnLoginPageIfConfigured();

  await setStepResult("Login", async () => {
    const loginButton = await firstVisibleLocator(appPage, [
      appPage.getByRole("button", { name: /google/i }),
      appPage.getByRole("link", { name: /google/i }),
      appPage.locator("button:has-text('Google'), a:has-text('Google'), [role='button']:has-text('Google')"),
    ]);
    if (!loginButton) {
      throw new Error("No se encontró un botón/enlace de login con Google");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndSettle(appPage, loginButton);

    const popup = await popupPromise;
    let accountSelectionNote = "No popup de Google detectado";
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      accountSelectionNote = await maybeChooseGoogleAccount(popup);
    } else {
      accountSelectionNote = await maybeChooseGoogleAccount(appPage);
    }

    await restoreAppPageFocus();

    await expect(
      appPage.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio|Dashboard/i }).first(),
    ).toBeVisible({ timeout: 60_000 });

    const dashboardShot = await checkpoint("01_dashboard_loaded", true);
    return `Interfaz principal y sidebar visibles | ${accountSelectionNote} | screenshot: ${dashboardShot}`;
  });

  await setStepResult("Mi Negocio menu", async () => {
    const miNegocioEntry = await firstVisibleLocator(appPage, [
      appPage.getByRole("button", { name: /mi negocio/i }),
      appPage.getByRole("link", { name: /mi negocio/i }),
      appPage.getByText("Mi Negocio", { exact: true }),
      appPage.getByText("Mi Negocio"),
    ]);
    if (!miNegocioEntry) {
      throw new Error("No se encontró la opción 'Mi Negocio' en el menú lateral");
    }

    await clickAndSettle(appPage, miNegocioEntry);

    await expect(appPage.getByText("Agregar Negocio", { exact: true })).toBeVisible();
    await expect(appPage.getByText("Administrar Negocios", { exact: true })).toBeVisible();

    const menuShot = await checkpoint("02_mi_negocio_menu_expanded");
    return `Submenú expandido con opciones visibles | screenshot: ${menuShot}`;
  });

  await setStepResult("Agregar Negocio modal", async () => {
    const addBusinessEntry = await firstVisibleLocator(appPage, [
      appPage.getByRole("button", { name: "Agregar Negocio" }),
      appPage.getByRole("link", { name: "Agregar Negocio" }),
      appPage.getByText("Agregar Negocio", { exact: true }),
    ]);
    if (!addBusinessEntry) {
      throw new Error("No se encontró la opción 'Agregar Negocio'");
    }

    await clickAndSettle(appPage, addBusinessEntry);

    await expect(appPage.getByRole("heading", { name: "Crear Nuevo Negocio" })).toBeVisible();
    const nombreField = appPage.getByLabel("Nombre del Negocio");
    await expect(nombreField).toBeVisible();
    await expect(appPage.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
    await expect(appPage.getByRole("button", { name: "Cancelar" })).toBeVisible();
    await expect(appPage.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

    const modalShot = await checkpoint("03_agregar_negocio_modal");

    await nombreField.click();
    await nombreField.fill("Negocio Prueba Automatización");
    await clickAndSettle(appPage, appPage.getByRole("button", { name: "Cancelar" }));

    return `Modal validado y cerrado correctamente | screenshot: ${modalShot}`;
  });

  await setStepResult("Administrar Negocios view", async () => {
    const administrarEntry = await firstVisibleLocator(appPage, [
      appPage.getByRole("button", { name: "Administrar Negocios" }),
      appPage.getByRole("link", { name: "Administrar Negocios" }),
      appPage.getByText("Administrar Negocios", { exact: true }),
    ]);
    if (!administrarEntry) {
      const miNegocioEntry = await firstVisibleLocator(appPage, [
        appPage.getByRole("button", { name: /mi negocio/i }),
        appPage.getByRole("link", { name: /mi negocio/i }),
        appPage.getByText("Mi Negocio"),
      ]);
      if (!miNegocioEntry) {
        throw new Error("No se pudo reabrir el menú Mi Negocio");
      }
      await clickAndSettle(appPage, miNegocioEntry);
    }

    const administrarAgain = await firstVisibleLocator(appPage, [
      appPage.getByRole("button", { name: "Administrar Negocios" }),
      appPage.getByRole("link", { name: "Administrar Negocios" }),
      appPage.getByText("Administrar Negocios", { exact: true }),
    ]);
    if (!administrarAgain) {
      throw new Error("No se encontró la opción 'Administrar Negocios'");
    }

    await clickAndSettle(appPage, administrarAgain);

    await expect(appPage.getByText("Información General", { exact: true })).toBeVisible();
    await expect(appPage.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
    await expect(appPage.getByText("Tus Negocios", { exact: true })).toBeVisible();
    await expect(appPage.getByText("Sección Legal", { exact: true })).toBeVisible();

    const accountShot = await checkpoint("04_administrar_negocios_view", true);
    return `Vista de cuenta cargada con secciones requeridas | screenshot: ${accountShot}`;
  });

  await setStepResult("Información General", async () => {
    await expect(
      appPage.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first(),
    ).toBeVisible();
    await expect(appPage.getByText("BUSINESS PLAN", { exact: true })).toBeVisible();
    await expect(appPage.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();

    const infoText = await appPage.locator("body").innerText();
    if (!/Información General/i.test(infoText)) {
      throw new Error("No se detectó el bloque de Información General");
    }

    return "Nombre/usuario, email, plan y botón Cambiar Plan visibles";
  });

  await setStepResult("Detalles de la Cuenta", async () => {
    await expect(appPage.getByText("Cuenta creada", { exact: false })).toBeVisible();
    await expect(appPage.getByText("Estado activo", { exact: false })).toBeVisible();
    await expect(appPage.getByText("Idioma seleccionado", { exact: false })).toBeVisible();

    return "Campos de detalle de cuenta visibles";
  });

  await setStepResult("Tus Negocios", async () => {
    await expect(appPage.getByText("Tus Negocios", { exact: true })).toBeVisible();
    await expect(appPage.getByRole("button", { name: "Agregar Negocio" })).toBeVisible();
    await expect(appPage.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();

    return "Listado y capacidad de negocios validados";
  });

  try {
    await validateLegalLink(
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05_terminos_y_condiciones",
      "Términos y Condiciones",
    );
  } catch (error) {
    report["Términos y Condiciones"] = toResult("FAIL", error.message);
  }

  try {
    await validateLegalLink(
      "Política de Privacidad",
      "Política de Privacidad",
      "06_politica_de_privacidad",
      "Política de Privacidad",
    );
  } catch (error) {
    report["Política de Privacidad"] = toResult("FAIL", error.message);
  }

  const reportOutput = {
    name: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate Mi Negocio workflow",
    generatedAt: new Date().toISOString(),
    artifactsDir,
    finalReport: report,
  };
  const reportPath = path.join(artifactsDir, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(reportOutput, null, 2), "utf8");

  // Step 10 explicit output
  console.log("=== FINAL REPORT ===");
  for (const field of REQUIRED_REPORT_FIELDS) {
    const item = report[field];
    console.log(`${field}: ${item.status}${item.details ? ` | ${item.details}` : ""}`);
  }
  console.log(`Report JSON: ${reportPath}`);

  const failedSteps = REQUIRED_REPORT_FIELDS.filter((field) => report[field].status !== "PASS");
  expect(
    failedSteps,
    `Fallaron validaciones: ${failedSteps.join(", ")}. Ver reporte: ${reportPath}`,
  ).toEqual([]);
});
