const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
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

function timestamp() {
  return new Date().toISOString();
}

function safeFileName(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9._-]+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_|_$/g, "")
    .toLowerCase();
}

async function waitForUi(page, waitMs = 800) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(waitMs);
}

async function isVisible(locator, timeout = 3000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function resolveFirstVisible(candidates, timeout = 4000) {
  for (const candidate of candidates) {
    if (!candidate) {
      continue;
    }

    if (await isVisible(candidate, timeout)) {
      return candidate.first();
    }
  }

  return null;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page, report, name, fullPage = false) {
  const fileName = `${String(report.checkpoints.length + 1).padStart(2, "0")}_${safeFileName(name)}.png`;
  const screenshotPath = path.join(report.artifactsDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  report.checkpoints.push({
    name,
    file: screenshotPath,
    capturedAt: timestamp(),
  });
}

function initReport() {
  const artifactsDir = path.join(process.cwd(), "artifacts", TEST_NAME);
  fs.mkdirSync(artifactsDir, { recursive: true });

  const results = {};
  for (const field of REPORT_FIELDS) {
    results[field] = "FAIL";
  }

  return {
    testName: TEST_NAME,
    generatedAt: timestamp(),
    artifactsDir,
    startUrl: null,
    finalResults: results,
    details: {},
    legalUrls: {},
    checkpoints: [],
  };
}

function setResult(report, field, pass, details) {
  report.finalResults[field] = pass ? "PASS" : "FAIL";
  report.details[field] = details;
}

async function getSectionContainer(page, headingText) {
  const headingRegex = new RegExp(`^${escapeRegex(headingText)}$`, "i");
  const heading = await resolveFirstVisible(
    [
      page.getByRole("heading", { name: headingRegex }),
      page.getByText(headingRegex),
    ],
    5000
  );

  if (!heading) {
    return null;
  }

  return heading.locator("xpath=ancestor::*[self::section or self::div][1]");
}

async function validateLegalPage({
  page,
  report,
  linkRegex,
  headingRegex,
  reportField,
  urlField,
  checkpointName,
}) {
  const link = await resolveFirstVisible([
    page.getByRole("link", { name: linkRegex }),
    page.getByRole("button", { name: linkRegex }),
    page.getByText(linkRegex),
  ]);

  if (!link) {
    return {
      pass: false,
      details: "No se encontró el enlace legal solicitado.",
      finalUrl: null,
    };
  }

  const currentUrl = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickAndWait(link, page);

  let targetPage = await popupPromise;
  let openedNewTab = false;

  if (targetPage) {
    openedNewTab = true;
    await targetPage.waitForLoadState("domcontentloaded").catch(() => {});
  } else {
    targetPage = page;
    await waitForUi(targetPage, 1200);
  }

  const headingVisible = await isVisible(targetPage.getByRole("heading", { name: headingRegex }), 12000)
    || await isVisible(targetPage.getByText(headingRegex), 12000);
  const bodyHasText = await targetPage
    .locator("body")
    .innerText()
    .then((text) => text.replace(/\s+/g, " ").trim().length > 180)
    .catch(() => false);

  const finalUrl = targetPage.url();
  report.legalUrls[urlField] = finalUrl;
  await captureCheckpoint(targetPage, report, checkpointName, true);

  if (openedNewTab) {
    await targetPage.close().catch(() => {});
    await page.bringToFront().catch(() => {});
  } else if (page.url() !== currentUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page, 1000);
  }

  const pass = headingVisible && bodyHasText;
  const details = pass
    ? `Validado correctamente en URL: ${finalUrl}`
    : `Validación incompleta (heading: ${headingVisible}, contenido legal: ${bodyHasText}, URL: ${finalUrl || "N/A"})`;

  return { pass, details, finalUrl };
}

test(TEST_NAME, async ({ page }, testInfo) => {
  const report = initReport();

  const startUrl = process.env.SALEADS_START_URL || process.env.BASE_URL;
  report.startUrl = startUrl || "pre-opened-login-page";

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page, 1200);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No start URL provided. Set SALEADS_START_URL (or BASE_URL) to the SaleADS login page for the current environment."
    );
  }

  // Step 1: Login with Google
  try {
    const loginButton = await resolveFirstVisible([
      page.getByRole("button", {
        name: /sign in with google|iniciar sesión con google|inicia sesión con google|continuar con google|google/i,
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesión con google|inicia sesión con google|continuar con google|google/i,
      }),
      page.getByText(/sign in with google|iniciar sesión con google|inicia sesión con google|continuar con google/i),
    ]);

    if (!loginButton) {
      throw new Error("No se encontró el botón de login con Google.");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const possibleGooglePage = await popupPromise;
    const authSurface = possibleGooglePage || page;

    if (possibleGooglePage) {
      await authSurface.waitForLoadState("domcontentloaded").catch(() => {});
      await waitForUi(authSurface, 800);
    }

    const accountLocator = await resolveFirstVisible([
      authSurface.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT), "i")),
      authSurface.getByRole("button", { name: new RegExp(escapeRegex(GOOGLE_ACCOUNT), "i") }),
      authSurface.getByRole("link", { name: new RegExp(escapeRegex(GOOGLE_ACCOUNT), "i") }),
    ]);

    if (accountLocator) {
      await clickAndWait(accountLocator, authSurface);
    }

    if (possibleGooglePage) {
      await page.bringToFront().catch(() => {});
    }

    const sidebarVisible =
      (await isVisible(page.getByRole("navigation"), 45000)) ||
      (await isVisible(page.locator("aside"), 45000)) ||
      (await isVisible(page.locator('[class*="sidebar"]'), 45000));

    const mainUiVisible =
      (await isVisible(page.getByRole("main"), 15000)) ||
      (await isVisible(page.locator("main"), 15000)) ||
      sidebarVisible;

    await captureCheckpoint(page, report, "dashboard_loaded");
    setResult(
      report,
      "Login",
      mainUiVisible && sidebarVisible,
      `Interfaz principal visible: ${mainUiVisible}. Sidebar visible: ${sidebarVisible}.`
    );
  } catch (error) {
    setResult(report, "Login", false, `Error en login: ${error.message}`);
  }

  // Step 2: Open Mi Negocio menu
  try {
    const negocioSection = await resolveFirstVisible([
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i),
    ]);

    if (negocioSection) {
      await clickAndWait(negocioSection, page);
    }

    const miNegocioOption = await resolveFirstVisible([
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i),
    ]);

    if (!miNegocioOption) {
      throw new Error("No se encontró la opción 'Mi Negocio'.");
    }

    await clickAndWait(miNegocioOption, page);

    const agregarVisible = await isVisible(
      page.getByRole("link", { name: /^agregar negocio$/i }).or(page.getByText(/^agregar negocio$/i)),
      8000
    );
    const administrarVisible = await isVisible(
      page.getByRole("link", { name: /^administrar negocios$/i }).or(page.getByText(/^administrar negocios$/i)),
      8000
    );

    await captureCheckpoint(page, report, "mi_negocio_menu_expanded");
    setResult(
      report,
      "Mi Negocio menu",
      agregarVisible && administrarVisible,
      `'Agregar Negocio' visible: ${agregarVisible}. 'Administrar Negocios' visible: ${administrarVisible}.`
    );
  } catch (error) {
    setResult(report, "Mi Negocio menu", false, `Error en menú Mi Negocio: ${error.message}`);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const agregarNegocioOption = await resolveFirstVisible([
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i),
    ]);

    if (!agregarNegocioOption) {
      throw new Error("No se encontró la opción 'Agregar Negocio'.");
    }

    await clickAndWait(agregarNegocioOption, page);

    const modalTitleVisible =
      (await isVisible(page.getByRole("heading", { name: /^crear nuevo negocio$/i }), 8000)) ||
      (await isVisible(page.getByText(/^crear nuevo negocio$/i), 8000));
    const nombreFieldVisible =
      (await isVisible(page.getByLabel(/^nombre del negocio$/i), 4000)) ||
      (await isVisible(page.getByPlaceholder(/nombre del negocio/i), 4000));
    const quotaVisible = await isVisible(page.getByText(/tienes 2 de 3 negocios/i), 4000);
    const cancelarVisible = await isVisible(page.getByRole("button", { name: /^cancelar$/i }), 4000);
    const crearVisible = await isVisible(page.getByRole("button", { name: /^crear negocio$/i }), 4000);

    await captureCheckpoint(page, report, "agregar_negocio_modal");

    const nombreInput = await resolveFirstVisible([
      page.getByLabel(/^nombre del negocio$/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").first(),
    ]);

    if (nombreInput) {
      await nombreInput.fill("Negocio Prueba Automatización");
      await waitForUi(page, 500);
    }

    const cancelarButton = await resolveFirstVisible([page.getByRole("button", { name: /^cancelar$/i })], 3000);
    if (cancelarButton) {
      await clickAndWait(cancelarButton, page);
    }

    const pass = modalTitleVisible && nombreFieldVisible && quotaVisible && cancelarVisible && crearVisible;
    setResult(
      report,
      "Agregar Negocio modal",
      pass,
      `Título: ${modalTitleVisible}, Nombre field: ${nombreFieldVisible}, cuota: ${quotaVisible}, Cancelar: ${cancelarVisible}, Crear Negocio: ${crearVisible}.`
    );
  } catch (error) {
    setResult(report, "Agregar Negocio modal", false, `Error en modal Agregar Negocio: ${error.message}`);
  }

  // Step 4: Open Administrar Negocios
  try {
    const administrarNegociosOption = await resolveFirstVisible([
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByText(/^administrar negocios$/i),
    ]);

    if (!administrarNegociosOption) {
      const miNegocioOption = await resolveFirstVisible([
        page.getByRole("button", { name: /^mi negocio$/i }),
        page.getByRole("link", { name: /^mi negocio$/i }),
        page.getByText(/^mi negocio$/i),
      ]);
      if (miNegocioOption) {
        await clickAndWait(miNegocioOption, page);
      }
    }

    const administrarAgain = await resolveFirstVisible([
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByText(/^administrar negocios$/i),
    ]);

    if (!administrarAgain) {
      throw new Error("No se encontró la opción 'Administrar Negocios'.");
    }

    await clickAndWait(administrarAgain, page);
    await waitForUi(page, 1400);

    const infoGeneralVisible =
      (await isVisible(page.getByRole("heading", { name: /^información general$/i }), 12000)) ||
      (await isVisible(page.getByText(/^información general$/i), 12000));
    const detallesVisible =
      (await isVisible(page.getByRole("heading", { name: /^detalles de la cuenta$/i }), 12000)) ||
      (await isVisible(page.getByText(/^detalles de la cuenta$/i), 12000));
    const negociosVisible =
      (await isVisible(page.getByRole("heading", { name: /^tus negocios$/i }), 12000)) ||
      (await isVisible(page.getByText(/^tus negocios$/i), 12000));
    const legalVisible =
      (await isVisible(page.getByRole("heading", { name: /^sección legal$/i }), 12000)) ||
      (await isVisible(page.getByText(/^sección legal$/i), 12000));

    await captureCheckpoint(page, report, "administrar_negocios_page", true);

    const pass = infoGeneralVisible && detallesVisible && negociosVisible && legalVisible;
    setResult(
      report,
      "Administrar Negocios view",
      pass,
      `Información General: ${infoGeneralVisible}, Detalles de la Cuenta: ${detallesVisible}, Tus Negocios: ${negociosVisible}, Sección Legal: ${legalVisible}.`
    );
  } catch (error) {
    setResult(report, "Administrar Negocios view", false, `Error en Administrar Negocios: ${error.message}`);
  }

  // Step 5: Validate Información General
  try {
    const infoSection = await getSectionContainer(page, "Información General");
    const infoSectionText = infoSection ? await infoSection.innerText().catch(() => "") : "";
    const normalizedInfo = infoSectionText.replace(/\s+/g, " ").trim();

    const emailVisible =
      (await isVisible(page.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT), "i")), 5000)) ||
      (await isVisible(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i), 5000));
    const businessPlanVisible = await isVisible(page.getByText(/business plan/i), 5000);
    const cambiarPlanVisible =
      (await isVisible(page.getByRole("button", { name: /^cambiar plan$/i }), 5000)) ||
      (await isVisible(page.getByRole("link", { name: /^cambiar plan$/i }), 5000)) ||
      (await isVisible(page.getByText(/^cambiar plan$/i), 5000));

    const candidateLines = normalizedInfo
      .split(/(?=[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)/)
      .map((line) => line.trim())
      .filter(Boolean);

    const userNameVisible = candidateLines.some(
      (line) =>
        /[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}(?:\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,})+/.test(line) &&
        !/@/.test(line) &&
        !/información general|business plan|cambiar plan/i.test(line)
    );

    const pass = userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible;
    setResult(
      report,
      "Información General",
      pass,
      `Nombre visible: ${userNameVisible}, email visible: ${emailVisible}, BUSINESS PLAN: ${businessPlanVisible}, Cambiar Plan: ${cambiarPlanVisible}.`
    );
  } catch (error) {
    setResult(report, "Información General", false, `Error en validación Información General: ${error.message}`);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    const cuentaCreadaVisible = await isVisible(page.getByText(/cuenta creada/i), 5000);
    const estadoActivoVisible = await isVisible(page.getByText(/estado activo/i), 5000);
    const idiomaVisible = await isVisible(page.getByText(/idioma seleccionado/i), 5000);

    const pass = cuentaCreadaVisible && estadoActivoVisible && idiomaVisible;
    setResult(
      report,
      "Detalles de la Cuenta",
      pass,
      `'Cuenta creada': ${cuentaCreadaVisible}, 'Estado activo': ${estadoActivoVisible}, 'Idioma seleccionado': ${idiomaVisible}.`
    );
  } catch (error) {
    setResult(report, "Detalles de la Cuenta", false, `Error en Detalles de la Cuenta: ${error.message}`);
  }

  // Step 7: Validate Tus Negocios
  try {
    const negociosSection = await getSectionContainer(page, "Tus Negocios");
    const addButtonVisible =
      (await isVisible(page.getByRole("button", { name: /^agregar negocio$/i }), 5000)) ||
      (await isVisible(page.getByRole("link", { name: /^agregar negocio$/i }), 5000));
    const quotaVisible = await isVisible(page.getByText(/tienes 2 de 3 negocios/i), 5000);

    let businessListVisible = false;
    if (negociosSection) {
      const itemCount = await negociosSection
        .locator("li, [role='row'], [data-testid*='business'], [class*='business']")
        .count()
        .catch(() => 0);
      const sectionText = await negociosSection.innerText().catch(() => "");
      businessListVisible = itemCount > 0 || /negocio/i.test(sectionText);
    }

    const pass = businessListVisible && addButtonVisible && quotaVisible;
    setResult(
      report,
      "Tus Negocios",
      pass,
      `Lista de negocios visible: ${businessListVisible}, botón Agregar Negocio: ${addButtonVisible}, cuota: ${quotaVisible}.`
    );
  } catch (error) {
    setResult(report, "Tus Negocios", false, `Error en validación Tus Negocios: ${error.message}`);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const legalResult = await validateLegalPage({
      page,
      report,
      linkRegex: /^términos y condiciones$/i,
      headingRegex: /^términos y condiciones$/i,
      reportField: "Términos y Condiciones",
      urlField: "terminosYCondiciones",
      checkpointName: "terminos_y_condiciones",
    });
    setResult(report, "Términos y Condiciones", legalResult.pass, legalResult.details);
  } catch (error) {
    setResult(report, "Términos y Condiciones", false, `Error en Términos y Condiciones: ${error.message}`);
  }

  // Step 9: Validate Política de Privacidad
  try {
    const legalResult = await validateLegalPage({
      page,
      report,
      linkRegex: /^política de privacidad$/i,
      headingRegex: /^política de privacidad$/i,
      reportField: "Política de Privacidad",
      urlField: "politicaDePrivacidad",
      checkpointName: "politica_de_privacidad",
    });
    setResult(report, "Política de Privacidad", legalResult.pass, legalResult.details);
  } catch (error) {
    setResult(report, "Política de Privacidad", false, `Error en Política de Privacidad: ${error.message}`);
  }

  // Step 10: Final report
  const finalReportPath = path.join(report.artifactsDir, "final-report.json");
  const serializableReport = {
    ...report,
    artifactsDir: report.artifactsDir,
  };
  fs.writeFileSync(finalReportPath, JSON.stringify(serializableReport, null, 2), "utf8");

  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  // Keep test strict while still generating a full report.
  const failures = Object.entries(report.finalResults).filter(([, value]) => value !== "PASS");
  expect(
    failures,
    `Final report contains failures. See ${finalReportPath} for full details.`
  ).toEqual([]);
});
