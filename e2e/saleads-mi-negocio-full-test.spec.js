const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const OPTIONAL_LOGIN_URL = process.env.SALEADS_LOGIN_URL;

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

const labelRegex = {
  negocio: /Negocio/i,
  miNegocio: /Mi Negocio/i,
  agregarNegocio: /Agregar Negocio/i,
  administrarNegocios: /Administrar Negocios/i,
  crearNuevoNegocio: /Crear Nuevo Negocio/i,
  nombreNegocio: /Nombre del Negocio/i,
  cupoNegocios: /Tienes 2 de 3 negocios/i,
  cancelar: /Cancelar/i,
  crearNegocio: /Crear Negocio/i,
  infoGeneral: /Informaci[oó]n General/i,
  detallesCuenta: /Detalles de la Cuenta/i,
  tusNegocios: /Tus Negocios/i,
  seccionLegal: /Secci[oó]n Legal/i,
  businessPlan: /BUSINESS PLAN/i,
  cambiarPlan: /Cambiar Plan/i,
  cuentaCreada: /Cuenta creada/i,
  estadoActivo: /Estado activo/i,
  idiomaSeleccionado: /Idioma seleccionado/i,
  terminos: /T[eé]rminos y Condiciones/i,
  privacidad: /Pol[ií]tica de Privacidad/i,
  signInGoogle: /Google|Sign in|Iniciar sesi[oó]n|Acceder/i
};

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await delay(1000);
}

async function firstVisible(locators, timeoutMs = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const locator of locators) {
      if ((await locator.count()) > 0) {
        const candidate = locator.first();
        if (await candidate.isVisible().catch(() => false)) {
          return candidate;
        }
      }
    }
    await delay(250);
  }

  throw new Error("No visible locator found within timeout.");
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function runSection(statuses, failures, sectionName, callback) {
  try {
    await callback();
    statuses[sectionName] = "PASS";
  } catch (error) {
    statuses[sectionName] = "FAIL";
    failures[sectionName] = error.message;
  }
}

async function openLegalLink({
  page,
  context,
  linkRegex,
  headingRegex,
  screenshotPath,
  urlStore,
  urlStoreKey
}) {
  const link = await firstVisible([
    page.getByRole("link", { name: linkRegex }),
    page.getByRole("button", { name: linkRegex }),
    page.getByText(linkRegex)
  ]);

  const currentAppUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await link.click();

  const popup = await popupPromise;
  const targetPage = popup || page;
  await targetPage.waitForLoadState("domcontentloaded");

  const heading = targetPage.getByRole("heading", { name: headingRegex }).first();
  const headingVisible = await heading.isVisible().catch(() => false);
  if (headingVisible) {
    await expect(heading).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingRegex).first()).toBeVisible();
  }

  // Validate legal content is not empty.
  const bodyText = (await targetPage.locator("body").innerText()).trim();
  expect(bodyText.length).toBeGreaterThan(100);

  await targetPage.screenshot({ path: screenshotPath, fullPage: true });
  urlStore[urlStoreKey] = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  if (page.url() !== currentAppUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const statuses = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = {};
  const evidence = {
    screenshots: [],
    urls: {}
  };

  if (page.url() === "about:blank" && OPTIONAL_LOGIN_URL) {
    await page.goto(OPTIONAL_LOGIN_URL, { waitUntil: "domcontentloaded" });
  }

  await runSection(statuses, failures, "Login", async () => {
    if (page.url() === "about:blank") {
      throw new Error(
        "Browser is not on the SaleADS login page. Set SALEADS_LOGIN_URL or open SaleADS before running."
      );
    }

    const loginButton = await firstVisible([
      page.getByRole("button", { name: labelRegex.signInGoogle }),
      page.getByRole("link", { name: labelRegex.signInGoogle }),
      page.getByText(/Google/i)
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }
      await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
    } else {
      const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }
    }

    await page.bringToFront();
    await page.waitForLoadState("domcontentloaded");

    const sidebar = await firstVisible(
      [page.locator("aside"), page.locator("nav"), page.getByText(labelRegex.negocio)],
      30000
    );
    await expect(sidebar).toBeVisible();
    await expect(page.getByText(labelRegex.negocio).first()).toBeVisible({ timeout: 30000 });

    const screenshotPath = testInfo.outputPath("01-dashboard-loaded.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    evidence.screenshots.push(screenshotPath);
  });

  await runSection(statuses, failures, "Mi Negocio menu", async () => {
    const negocioButton = await firstVisible([
      page.getByRole("button", { name: labelRegex.negocio }),
      page.getByRole("link", { name: labelRegex.negocio }),
      page.getByText(/^Negocio$/i)
    ]);
    await clickAndWait(negocioButton, page);

    const miNegocioButton = await firstVisible([
      page.getByRole("button", { name: labelRegex.miNegocio }),
      page.getByRole("link", { name: labelRegex.miNegocio }),
      page.getByText(labelRegex.miNegocio)
    ]);
    await clickAndWait(miNegocioButton, page);

    await expect(page.getByText(labelRegex.agregarNegocio).first()).toBeVisible();
    await expect(page.getByText(labelRegex.administrarNegocios).first()).toBeVisible();

    const screenshotPath = testInfo.outputPath("02-mi-negocio-menu-expanded.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    evidence.screenshots.push(screenshotPath);
  });

  await runSection(statuses, failures, "Agregar Negocio modal", async () => {
    const addBusinessButton = await firstVisible([
      page.getByRole("button", { name: labelRegex.agregarNegocio }),
      page.getByRole("link", { name: labelRegex.agregarNegocio }),
      page.getByText(labelRegex.agregarNegocio)
    ]);
    await clickAndWait(addBusinessButton, page);

    const modal = page.getByRole("dialog").filter({ hasText: labelRegex.crearNuevoNegocio }).first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText(labelRegex.crearNuevoNegocio)).toBeVisible();
    await expect(modal.getByText(labelRegex.nombreNegocio)).toBeVisible();
    await expect(modal.getByText(labelRegex.cupoNegocios)).toBeVisible();
    await expect(modal.getByRole("button", { name: labelRegex.cancelar })).toBeVisible();
    await expect(modal.getByRole("button", { name: labelRegex.crearNegocio })).toBeVisible();

    const businessNameInput = modal.getByLabel(labelRegex.nombreNegocio).first();
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.fill("Negocio Prueba Automatizacion");
    }

    const cancelButton = modal.getByRole("button", { name: labelRegex.cancelar });
    await clickAndWait(cancelButton, page);

    const screenshotPath = testInfo.outputPath("03-agregar-negocio-modal.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    evidence.screenshots.push(screenshotPath);
  });

  await runSection(statuses, failures, "Administrar Negocios view", async () => {
    const adminVisible = await page.getByText(labelRegex.administrarNegocios).first().isVisible().catch(() => false);
    if (!adminVisible) {
      const miNegocioButton = await firstVisible([
        page.getByRole("button", { name: labelRegex.miNegocio }),
        page.getByRole("link", { name: labelRegex.miNegocio }),
        page.getByText(labelRegex.miNegocio)
      ]);
      await clickAndWait(miNegocioButton, page);
    }

    const administrarButton = await firstVisible([
      page.getByRole("button", { name: labelRegex.administrarNegocios }),
      page.getByRole("link", { name: labelRegex.administrarNegocios }),
      page.getByText(labelRegex.administrarNegocios)
    ]);
    await clickAndWait(administrarButton, page);

    await expect(page.getByText(labelRegex.infoGeneral).first()).toBeVisible();
    await expect(page.getByText(labelRegex.detallesCuenta).first()).toBeVisible();
    await expect(page.getByText(labelRegex.tusNegocios).first()).toBeVisible();
    await expect(page.getByText(labelRegex.seccionLegal).first()).toBeVisible();

    const screenshotPath = testInfo.outputPath("04-administrar-negocios-page.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    evidence.screenshots.push(screenshotPath);
  });

  await runSection(statuses, failures, "Información General", async () => {
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.getByText(labelRegex.businessPlan).first()).toBeVisible();
    await expect(page.getByRole("button", { name: labelRegex.cambiarPlan }).first()).toBeVisible();
  });

  await runSection(statuses, failures, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(labelRegex.cuentaCreada).first()).toBeVisible();
    await expect(page.getByText(labelRegex.estadoActivo).first()).toBeVisible();
    await expect(page.getByText(labelRegex.idiomaSeleccionado).first()).toBeVisible();
  });

  await runSection(statuses, failures, "Tus Negocios", async () => {
    await expect(page.getByText(labelRegex.tusNegocios).first()).toBeVisible();
    await expect(page.getByText(labelRegex.cupoNegocios).first()).toBeVisible();
    await expect(page.getByRole("button", { name: labelRegex.agregarNegocio }).first()).toBeVisible();
  });

  await runSection(statuses, failures, "Términos y Condiciones", async () => {
    const screenshotPath = testInfo.outputPath("05-terminos-y-condiciones.png");
    await openLegalLink({
      page,
      context,
      linkRegex: labelRegex.terminos,
      headingRegex: labelRegex.terminos,
      screenshotPath,
      urlStore: evidence.urls,
      urlStoreKey: "terminosYCondiciones"
    });
    evidence.screenshots.push(screenshotPath);
  });

  await runSection(statuses, failures, "Política de Privacidad", async () => {
    const screenshotPath = testInfo.outputPath("06-politica-de-privacidad.png");
    await openLegalLink({
      page,
      context,
      linkRegex: labelRegex.privacidad,
      headingRegex: labelRegex.privacidad,
      screenshotPath,
      urlStore: evidence.urls,
      urlStoreKey: "politicaDePrivacidad"
    });
    evidence.screenshots.push(screenshotPath);
  });

  const report = {
    name: "saleads_mi_negocio_full_test",
    statuses,
    failures,
    evidence
  };

  // Final report artifact for CI parsing and audit.
  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(report, null, 2)),
    contentType: "application/json"
  });
  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(JSON.stringify(report, null, 2));

  const failedSections = Object.entries(statuses)
    .filter(([, status]) => status === "FAIL")
    .map(([name]) => name);

  expect(
    failedSections,
    `Some required sections failed validation: ${failedSections.join(", ")}`
  ).toEqual([]);
});
