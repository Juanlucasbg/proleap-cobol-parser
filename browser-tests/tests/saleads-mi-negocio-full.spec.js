const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

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

function buildReportTemplate() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {
    // Some pages keep active requests running; do not fail on that.
  });
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    const target = locator.first();
    if (await target.isVisible().catch(() => false)) {
      return target;
    }
  }
  return null;
}

async function requireVisibleLocator(candidates, failureMessage) {
  const locator = await firstVisibleLocator(candidates);
  if (!locator) {
    throw new Error(failureMessage);
  }
  await expect(locator).toBeVisible();
  return locator;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function takeCheckpoint(page, runId, checkpointName, fullPage = true) {
  const screenshotsDir = path.resolve(__dirname, "..", "screenshots");
  fs.mkdirSync(screenshotsDir, { recursive: true });
  const safeName = checkpointName.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const outputPath = path.join(screenshotsDir, `${runId}-${safeName}.png`);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function ensureMiNegocioExpanded(page) {
  let agregarVisible = await page
    .getByText(/^Agregar Negocio$/i)
    .first()
    .isVisible()
    .catch(() => false);
  if (agregarVisible) {
    return;
  }

  const negocio = await firstVisibleLocator([
    page.getByRole("link", { name: /^Negocio$/i }),
    page.getByRole("button", { name: /^Negocio$/i }),
    page.getByText(/^Negocio$/i),
  ]);
  if (negocio) {
    await clickAndWait(page, negocio);
  }

  const miNegocio = await requireVisibleLocator(
    [
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ],
    "No se encontró la opción 'Mi Negocio' en el menú lateral."
  );
  await clickAndWait(page, miNegocio);

  agregarVisible = await page
    .getByText(/^Agregar Negocio$/i)
    .first()
    .isVisible()
    .catch(() => false);
  if (!agregarVisible) {
    throw new Error("El submenú de 'Mi Negocio' no se expandió correctamente.");
  }
}

async function openLegalLinkAndValidate(page, linkText, headingText, runId) {
  const legalLink = await requireVisibleLocator(
    [
      page.getByRole("link", { name: new RegExp(`^${linkText}$`, "i") }),
      page.getByText(new RegExp(`^${linkText}$`, "i")),
    ],
    `No se encontró el enlace legal '${linkText}'.`
  );

  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  await legalLink.click();

  let legalPage = page;
  const popup = await popupPromise;
  if (popup) {
    legalPage = popup;
  }

  await waitForUi(legalPage);

  const heading = await requireVisibleLocator(
    [
      legalPage.getByRole("heading", { name: new RegExp(headingText, "i") }),
      legalPage.getByText(new RegExp(headingText, "i")),
    ],
    `No se encontró el encabezado '${headingText}' en la página legal.`
  );
  await expect(heading).toBeVisible();

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  if (bodyText.length < 100) {
    throw new Error(`La página '${headingText}' no tiene contenido legal suficiente visible.`);
  }

  const screenshotPath = await takeCheckpoint(legalPage, runId, headingText);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack().catch(() => {});
    await waitForUi(page);
  }

  return { screenshotPath, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const runId = `${Date.now()}`;
  const report = buildReportTemplate();
  const details = {};
  const evidence = {};

  const startUrl = process.env.SALEADS_START_URL;
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_START_URL to the current environment login page. The test does not hardcode any URL."
    );
  }

  async function runValidation(field, action) {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      details[field] = error instanceof Error ? error.message : String(error);
    }
  }

  await runValidation("Login", async () => {
    const loginButton = await requireVisibleLocator(
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|google/i),
      ],
      "No se encontró el botón de login con Google."
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    if (popup) {
      await waitForUi(popup);
      const accountOption = await firstVisibleLocator([
        popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        popup.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
      ]);
      if (accountOption) {
        await accountOption.click();
        await popup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      }
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const inlineAccount = await firstVisibleLocator([
        page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        page.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
      ]);
      if (inlineAccount) {
        await clickAndWait(page, inlineAccount);
      }
    }

    const sidebar = await requireVisibleLocator(
      [page.locator("aside"), page.getByRole("navigation"), page.locator("nav")],
      "No se encontró la barra lateral luego del login."
    );
    await expect(sidebar).toBeVisible();

    evidence.dashboardScreenshot = await takeCheckpoint(page, runId, "dashboard-loaded");
  });

  await runValidation("Mi Negocio menu", async () => {
    await ensureMiNegocioExpanded(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    evidence.miNegocioMenuScreenshot = await takeCheckpoint(page, runId, "mi-negocio-menu-expanded");
  });

  await runValidation("Agregar Negocio modal", async () => {
    const agregarNegocio = await requireVisibleLocator(
      [
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      "No se encontró la opción 'Agregar Negocio'."
    );
    await clickAndWait(page, agregarNegocio);

    await requireVisibleLocator(
      [page.getByRole("heading", { name: /Crear Nuevo Negocio/i }), page.getByText(/Crear Nuevo Negocio/i)],
      "No apareció el modal 'Crear Nuevo Negocio'."
    );
    const nombreInput = await requireVisibleLocator(
      [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)],
      "No se encontró el campo 'Nombre del Negocio'."
    );
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

    await nombreInput.fill("Negocio Prueba Automatización");
    evidence.agregarNegocioModalScreenshot = await takeCheckpoint(page, runId, "agregar-negocio-modal");

    const cancelar = page.getByRole("button", { name: /^Cancelar$/i }).first();
    await clickAndWait(page, cancelar);
  });

  await runValidation("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);

    const administrarNegocios = await requireVisibleLocator(
      [
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      "No se encontró la opción 'Administrar Negocios'."
    );
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

    evidence.accountPageScreenshot = await takeCheckpoint(page, runId, "administrar-negocios", true);
  });

  await runValidation("Información General", async () => {
    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();

    const visibleEmail = await firstVisibleLocator([page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)]);
    if (!visibleEmail) {
      throw new Error("No se encontró ningún email visible en 'Información General'.");
    }

    const nameSignals = await firstVisibleLocator([
      page.getByText(/Nombre|Usuario|Name/i),
      page.getByText(GOOGLE_ACCOUNT_EMAIL.split("@")[0], { exact: false }),
    ]);
    if (!nameSignals) {
      throw new Error("No se encontró señal visible del nombre de usuario en 'Información General'.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();

    const businessList = await firstVisibleLocator([
      page.locator("table"),
      page.locator("ul").filter({ hasText: /Negocio/i }),
      page.locator("[class*='business'], [id*='business']"),
    ]);
    if (!businessList) {
      throw new Error("No se encontró listado visible de negocios.");
    }
  });

  await runValidation("Términos y Condiciones", async () => {
    const result = await openLegalLinkAndValidate(
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      runId
    );
    evidence.terminosScreenshot = result.screenshotPath;
    details.terminosFinalUrl = result.finalUrl;
  });

  await runValidation("Política de Privacidad", async () => {
    const result = await openLegalLinkAndValidate(page, "Política de Privacidad", "Política de Privacidad", runId);
    evidence.politicaScreenshot = result.screenshotPath;
    details.politicaFinalUrl = result.finalUrl;
  });

  const reportPath = path.resolve(__dirname, "..", "screenshots", `saleads-mi-negocio-report-${runId}.json`);
  const reportPayload = {
    generatedAt: new Date().toISOString(),
    report,
    details,
    evidence,
  };
  fs.writeFileSync(reportPath, JSON.stringify(reportPayload, null, 2), "utf8");

  console.table(report);
  console.log(`Final report saved at: ${reportPath}`);
  if (details.terminosFinalUrl) {
    console.log(`Términos y Condiciones URL: ${details.terminosFinalUrl}`);
  }
  if (details.politicaFinalUrl) {
    console.log(`Política de Privacidad URL: ${details.politicaFinalUrl}`);
  }

  const failedFields = Object.entries(report)
    .filter(([, status]) => status === "FAIL")
    .map(([field]) => field);
  expect(failedFields, `Validation failures: ${failedFields.join(", ")}`).toEqual([]);
});
