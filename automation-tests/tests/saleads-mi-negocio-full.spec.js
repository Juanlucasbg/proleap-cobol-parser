const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const WORKFLOW_REPORT_FIELDS = [
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

function createEmptyReport() {
  return Object.fromEntries(WORKFLOW_REPORT_FIELDS.map((field) => [field, "NOT_RUN"]));
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
}

async function clickAndWait(page, locator) {
  await locator.waitFor({ state: "visible", timeout: 20000 });
  await locator.click();
  await waitForUiToLoad(page);
}

async function firstVisibleLocator(candidates) {
  for (const candidateFactory of candidates) {
    const locator = candidateFactory();
    const hasElements = (await locator.count()) > 0;
    if (!hasElements) {
      continue;
    }

    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return null;
}

async function mustFindVisible(candidates, failureMessage) {
  const locator = await firstVisibleLocator(candidates);
  if (!locator) {
    throw new Error(failureMessage);
  }
  return locator;
}

async function attachScreenshot(page, testInfo, label, fullPage = false) {
  const filename = `${label.toLowerCase().replace(/[^a-z0-9]+/g, "-")}.png`;
  const path = testInfo.outputPath(filename);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(`screenshot-${label}`, {
    contentType: "image/png",
    path
  });
}

async function ensureMiNegocioExpanded(page) {
  const submenuVisible =
    (await page.getByText(/Agregar Negocio/i).first().isVisible().catch(() => false)) &&
    (await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false));

  if (submenuVisible) {
    return;
  }

  const negocioEntry = await mustFindVisible(
    [
      () => page.getByRole("button", { name: /^Negocio$/i }),
      () => page.getByRole("link", { name: /^Negocio$/i }),
      () => page.getByText(/^Negocio$/i)
    ],
    "No se encontró la sección 'Negocio' en la barra lateral."
  );
  await clickAndWait(page, negocioEntry);

  const miNegocioEntry = await mustFindVisible(
    [
      () => page.getByRole("button", { name: /Mi Negocio/i }),
      () => page.getByRole("link", { name: /Mi Negocio/i }),
      () => page.getByText(/Mi Negocio/i)
    ],
    "No se encontró la opción 'Mi Negocio'."
  );
  await clickAndWait(page, miNegocioEntry);
}

async function validateStep(report, stepName, fn) {
  try {
    await fn();
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = `FAIL - ${error.message.split("\n")[0]}`;
  }
}

async function validateLegalPage({
  page,
  testInfo,
  linkRegex,
  headingRegex,
  screenshotLabel
}) {
  const legalLink = await mustFindVisible(
    [
      () => page.getByRole("link", { name: linkRegex }),
      () => page.getByRole("button", { name: linkRegex }),
      () => page.getByText(linkRegex)
    ],
    `No se encontró el enlace legal: ${linkRegex}`
  );

  const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await legalLink.click();

  const popupPage = await popupPromise;
  const legalPage = popupPage || page;
  await waitForUiToLoad(legalPage);

  const legalHeading = await mustFindVisible(
    [
      () => legalPage.getByRole("heading", { name: headingRegex }),
      () => legalPage.getByText(headingRegex)
    ],
    `No se encontró el título legal esperado: ${headingRegex}`
  );
  await expect(legalHeading).toBeVisible();

  await expect(legalPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúÑñ]/);
  await attachScreenshot(legalPage, testInfo, screenshotLabel);

  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else {
    await page.goBack().catch(() => {});
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.setTimeout(5 * 60 * 1000);

  const report = createEmptyReport();
  const legalUrls = {
    terminosYCondiciones: "N/A",
    politicaDePrivacidad: "N/A"
  };

  await validateStep(report, "Login", async () => {
    const targetUrl = process.env.SALEADS_URL;
    if (targetUrl && page.url() === "about:blank") {
      await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUiToLoad(page);

    const loginWithGoogleButton = await mustFindVisible(
      [
        () => page.getByRole("button", { name: /Google/i }),
        () => page.getByRole("link", { name: /Google/i }),
        () =>
          page.getByText(/(Iniciar sesión|Sign in|Continuar|Continue).*(Google)|(Google).*(Iniciar sesión|Sign in|Continuar|Continue)/i)
      ],
      "No se encontró el botón de login con Google."
    );
    await clickAndWait(page, loginWithGoogleButton);

    const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
    if (await accountOption.isVisible({ timeout: 12000 }).catch(() => false)) {
      await clickAndWait(page, accountOption);
    }

    const appInterface = await mustFindVisible(
      [() => page.locator("main"), () => page.locator("[role='main']"), () => page.locator("aside")],
      "No se detectó la interfaz principal de la aplicación."
    );
    await expect(appInterface).toBeVisible();

    const leftSidebar = await mustFindVisible(
      [() => page.locator("aside"), () => page.getByRole("navigation"), () => page.locator("[class*='sidebar']")],
      "No se detectó la barra lateral izquierda después del login."
    );
    await expect(leftSidebar).toBeVisible();

    await attachScreenshot(page, testInfo, "dashboard-loaded");
  });

  await validateStep(report, "Mi Negocio menu", async () => {
    if (report["Login"] !== "PASS") {
      throw new Error("Bloqueado: el login no fue exitoso.");
    }

    await ensureMiNegocioExpanded(page);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await attachScreenshot(page, testInfo, "mi-negocio-menu-expanded");
  });

  await validateStep(report, "Agregar Negocio modal", async () => {
    if (report["Mi Negocio menu"] !== "PASS") {
      throw new Error("Bloqueado: no se pudo expandir el menú Mi Negocio.");
    }

    const agregarNegocioMenuEntry = await mustFindVisible(
      [
        () => page.getByRole("button", { name: /^Agregar Negocio$/i }),
        () => page.getByRole("link", { name: /^Agregar Negocio$/i }),
        () => page.getByText(/^Agregar Negocio$/i)
      ],
      "No se encontró la opción 'Agregar Negocio'."
    );
    await clickAndWait(page, agregarNegocioMenuEntry);

    const modalTitle = await mustFindVisible(
      [
        () => page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
        () => page.getByText(/Crear Nuevo Negocio/i)
      ],
      "No apareció el modal 'Crear Nuevo Negocio'."
    );
    await expect(modalTitle).toBeVisible();

    const nombreDelNegocioInput = await mustFindVisible(
      [
        () => page.getByLabel(/Nombre del Negocio/i),
        () => page.getByPlaceholder(/Nombre del Negocio/i),
        () => page.locator("input[placeholder*='Nombre']"),
        () => page.locator("input[name*='nombre']")
      ],
      "No se encontró el campo 'Nombre del Negocio'."
    );
    await expect(nombreDelNegocioInput).toBeVisible();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    await attachScreenshot(page, testInfo, "agregar-negocio-modal");

    await nombreDelNegocioInput.click();
    await nombreDelNegocioInput.fill("Negocio Prueba Automatización");

    const cancelButton = page.getByRole("button", { name: /Cancelar/i }).first();
    await clickAndWait(page, cancelButton);
  });

  await validateStep(report, "Administrar Negocios view", async () => {
    if (report["Mi Negocio menu"] !== "PASS") {
      throw new Error("Bloqueado: no se pudo abrir el menú Mi Negocio.");
    }

    await ensureMiNegocioExpanded(page);

    const administrarNegociosEntry = await mustFindVisible(
      [
        () => page.getByRole("button", { name: /Administrar Negocios/i }),
        () => page.getByRole("link", { name: /Administrar Negocios/i }),
        () => page.getByText(/Administrar Negocios/i)
      ],
      "No se encontró la opción 'Administrar Negocios'."
    );
    await clickAndWait(page, administrarNegociosEntry);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await attachScreenshot(page, testInfo, "administrar-negocios-view", true);
  });

  await validateStep(report, "Información General", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Bloqueado: no se abrió la vista Administrar Negocios.");
    }

    const infoGeneralSection = await mustFindVisible(
      [
        () => page.locator("section, div").filter({ hasText: /Información General/i }),
        () => page.getByText(/Información General/i).locator("..")
      ],
      "No se encontró la sección 'Información General'."
    );

    await expect(infoGeneralSection.getByText(/Nombre|Usuario|User/i).first()).toBeVisible();
    await expect(infoGeneralSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expect(infoGeneralSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(infoGeneralSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await validateStep(report, "Detalles de la Cuenta", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Bloqueado: no se abrió la vista Administrar Negocios.");
    }

    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await validateStep(report, "Tus Negocios", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Bloqueado: no se abrió la vista Administrar Negocios.");
    }

    const tusNegociosSection = await mustFindVisible(
      [
        () => page.locator("section, div").filter({ hasText: /Tus Negocios/i }),
        () => page.getByText(/Tus Negocios/i).locator("..")
      ],
      "No se encontró la sección 'Tus Negocios'."
    );

    await expect(tusNegociosSection).toBeVisible();
    await expect(tusNegociosSection.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(tusNegociosSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await validateStep(report, "Términos y Condiciones", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Bloqueado: no se abrió la vista Administrar Negocios.");
    }

    legalUrls.terminosYCondiciones = await validateLegalPage({
      page,
      testInfo,
      linkRegex: /Términos y Condiciones/i,
      headingRegex: /Términos y Condiciones/i,
      screenshotLabel: "terminos-y-condiciones"
    });
  });

  await validateStep(report, "Política de Privacidad", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Bloqueado: no se abrió la vista Administrar Negocios.");
    }

    legalUrls.politicaDePrivacidad = await validateLegalPage({
      page,
      testInfo,
      linkRegex: /Política de Privacidad/i,
      headingRegex: /Política de Privacidad/i,
      screenshotLabel: "politica-de-privacidad"
    });
  });

  console.log("=== SaleADS Mi Negocio Final Report ===");
  for (const field of WORKFLOW_REPORT_FIELDS) {
    console.log(`${field}: ${report[field]}`);
  }
  console.log(`Términos y Condiciones URL: ${legalUrls.terminosYCondiciones}`);
  console.log(`Política de Privacidad URL: ${legalUrls.politicaDePrivacidad}`);

  const failures = WORKFLOW_REPORT_FIELDS.filter((field) => report[field] !== "PASS");
  expect(
    failures,
    `Se encontraron validaciones fallidas:\n${failures.map((field) => `- ${field}: ${report[field]}`).join("\n")}`
  ).toEqual([]);
});
