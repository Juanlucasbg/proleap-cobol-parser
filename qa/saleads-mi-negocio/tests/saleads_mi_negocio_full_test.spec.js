const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

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
  "Política de Privacidad"
];

const ARTIFACTS_DIR = path.resolve(__dirname, "..", "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_FILE = path.join(ARTIFACTS_DIR, "saleads_mi_negocio_full_test_report.json");

function buildInitialFieldReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {});
}

async function ensureArtifactsDir() {
  await fs.promises.mkdir(SCREENSHOTS_DIR, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function isVisible(locator, timeout = 4_000) {
  try {
    return await locator.first().isVisible({ timeout });
  } catch {
    return false;
  }
}

async function firstVisible(locators, timeout = 4_000) {
  for (const locator of locators) {
    if (await isVisible(locator, timeout)) {
      return locator.first();
    }
  }

  return null;
}

async function captureScreenshot(page, fileName, fullPage = true) {
  const screenshotPath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

function sanitizeErrorMessage(error) {
  const rawMessage = error instanceof Error ? error.message : String(error);
  return rawMessage.replace(/\u001b\[[0-9;]*m/g, "").trim();
}

async function runValidationStep(report, field, callback) {
  try {
    await callback();
    report.results[field] = "PASS";
  } catch (error) {
    report.results[field] = "FAIL";
    report.errors.push({
      field,
      message: sanitizeErrorMessage(error)
    });
  }
}

function buildTextLocatorCandidates(page, textRegex) {
  return [
    page.getByRole("button", { name: textRegex }),
    page.getByRole("link", { name: textRegex }),
    page.getByRole("menuitem", { name: textRegex }),
    page.getByText(textRegex)
  ];
}

function containsLikelyName(rawText) {
  const ignoredPattern =
    /informaci[oó]n general|business plan|cambiar plan|correo|email|plan|detalles|cuenta/i;
  const lines = rawText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  return lines.some(
    (line) =>
      !ignoredPattern.test(line) &&
      /^[A-Za-zÁÉÍÓÚÑáéíóúñ' -]{3,}$/.test(line) &&
      !line.includes("@")
  );
}

async function openLegalLinkAndValidate({
  appPage,
  context,
  linkRegex,
  expectedHeadingRegex,
  screenshotFileName
}) {
  const link = await firstVisible(buildTextLocatorCandidates(appPage, linkRegex), 8_000);
  if (!link) {
    throw new Error(`No se encontró el enlace legal: ${linkRegex}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await link.click();
  await waitForUi(appPage);

  const popupPage = await popupPromise;
  const legalPage = popupPage || appPage;

  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded");
    await popupPage.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => {});
  }

  const heading = await firstVisible(
    [
      legalPage.getByRole("heading", { name: expectedHeadingRegex }),
      legalPage.getByText(expectedHeadingRegex)
    ],
    15_000
  );

  if (!heading) {
    throw new Error(`No se encontró el heading esperado: ${expectedHeadingRegex}`);
  }

  const legalBodyText = await legalPage.locator("body").innerText();
  if (!legalBodyText || legalBodyText.trim().length < 120) {
    throw new Error("El contenido legal visible es insuficiente.");
  }

  const screenshotPath = await captureScreenshot(legalPage, screenshotFileName, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return {
    finalUrl,
    screenshotPath
  };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  await ensureArtifactsDir();

  const report = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: buildInitialFieldReport(),
    evidence: {},
    finalUrls: {},
    errors: []
  };

  const configuredUrl =
    process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;

  if (configuredUrl) {
    try {
      await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    } catch (error) {
      report.errors.push({
        field: "bootstrap",
        message: `No fue posible abrir la URL configurada (${configuredUrl}): ${
          error instanceof Error ? error.message : String(error)
        }`
      });
    }
  }

  await waitForUi(page).catch(() => {});

  await runValidationStep(report, "Login", async () => {
    if (page.url() === "about:blank") {
      throw new Error(
        "No hay URL de login activa. Defina SALEADS_LOGIN_URL / SALEADS_BASE_URL / BASE_URL o abra manualmente la pantalla de login."
      );
    }

    const loginButton = await firstVisible(
      [
        ...buildTextLocatorCandidates(page, /sign in with google/i),
        ...buildTextLocatorCandidates(page, /inicia(r)? sesi[oó]n con google/i),
        ...buildTextLocatorCandidates(page, /continuar con google/i)
      ],
      10_000
    );

    if (!loginButton) {
      throw new Error("No se encontró un botón de login con Google.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popupPage = await popupPromise;
    const authPage = popupPage || page;

    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      await popupPage.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => {});
    }

    const accountOption = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
    if (await isVisible(accountOption, 10_000)) {
      await accountOption.first().click();
      await waitForUi(authPage);
    }

    if (popupPage) {
      await popupPage.waitForEvent("close", { timeout: 60_000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUi(page);

    const sidebarVisible =
      (await isVisible(page.getByRole("navigation"), 20_000)) ||
      (await isVisible(page.getByText(/negocio/i), 20_000));

    if (!sidebarVisible) {
      throw new Error("No se visualiza la interfaz principal o el sidebar izquierdo.");
    }

    report.evidence.dashboard = await captureScreenshot(page, "01_dashboard_loaded.png", true);
  });

  await runValidationStep(report, "Mi Negocio menu", async () => {
    const negocioMenu = await firstVisible(
      [
        ...buildTextLocatorCandidates(page, /^negocio$/i),
        ...buildTextLocatorCandidates(page, /mi negocio/i)
      ],
      12_000
    );

    if (!negocioMenu) {
      throw new Error("No se encontró el menú Negocio o Mi Negocio en el sidebar.");
    }

    await negocioMenu.click();
    await waitForUi(page);

    const miNegocioEntry = await firstVisible(buildTextLocatorCandidates(page, /mi negocio/i), 8_000);
    if (miNegocioEntry && (await isVisible(miNegocioEntry, 2_000))) {
      await miNegocioEntry.click();
      await waitForUi(page);
    }

    const agregarNegocio = await firstVisible(buildTextLocatorCandidates(page, /agregar negocio/i), 10_000);
    const administrarNegocios = await firstVisible(
      buildTextLocatorCandidates(page, /administrar negocios/i),
      10_000
    );

    if (!agregarNegocio || !administrarNegocios) {
      throw new Error("El submenú Mi Negocio no muestra 'Agregar Negocio' y 'Administrar Negocios'.");
    }

    report.evidence.miNegocioMenu = await captureScreenshot(
      page,
      "02_mi_negocio_menu_expanded.png",
      true
    );
  });

  await runValidationStep(report, "Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible(buildTextLocatorCandidates(page, /agregar negocio/i), 10_000);
    if (!agregarNegocio) {
      throw new Error("No se encontró la opción 'Agregar Negocio'.");
    }

    await agregarNegocio.click();
    await waitForUi(page);

    const modalTitle = await firstVisible(buildTextLocatorCandidates(page, /crear nuevo negocio/i), 10_000);
    const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    const businessLimitText = await firstVisible(
      [page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)],
      8_000
    );
    const cancelButton = await firstVisible(buildTextLocatorCandidates(page, /^cancelar$/i), 8_000);
    const createButton = await firstVisible(buildTextLocatorCandidates(page, /crear negocio/i), 8_000);

    if (
      !modalTitle ||
      !(await isVisible(businessNameInput, 8_000)) ||
      !businessLimitText ||
      !cancelButton ||
      !createButton
    ) {
      throw new Error("El modal 'Crear Nuevo Negocio' no contiene todos los elementos esperados.");
    }

    report.evidence.agregarNegocioModal = await captureScreenshot(
      page,
      "03_agregar_negocio_modal.png",
      true
    );

    await businessNameInput.fill("Negocio Prueba Automatización");
    await cancelButton.click();
    await waitForUi(page);
  });

  await runValidationStep(report, "Administrar Negocios view", async () => {
    const administrarNegocios = await firstVisible(
      buildTextLocatorCandidates(page, /administrar negocios/i),
      12_000
    );

    if (!administrarNegocios) {
      const miNegocio = await firstVisible(buildTextLocatorCandidates(page, /mi negocio/i), 8_000);
      if (miNegocio) {
        await miNegocio.click();
        await waitForUi(page);
      }
    }

    const administrarNegociosRetry = await firstVisible(
      buildTextLocatorCandidates(page, /administrar negocios/i),
      12_000
    );
    if (!administrarNegociosRetry) {
      throw new Error("No se encontró la opción 'Administrar Negocios'.");
    }

    await administrarNegociosRetry.click();
    await waitForUi(page);

    const infoGeneral = await firstVisible(
      [page.getByText(/informaci[oó]n general/i), page.getByRole("heading", { name: /informaci[oó]n general/i })],
      12_000
    );
    const detallesCuenta = await firstVisible(
      [page.getByText(/detalles de la cuenta/i), page.getByRole("heading", { name: /detalles de la cuenta/i })],
      12_000
    );
    const tusNegocios = await firstVisible(
      [page.getByText(/tus negocios/i), page.getByRole("heading", { name: /tus negocios/i })],
      12_000
    );
    const seccionLegal = await firstVisible(
      [page.getByText(/secci[oó]n legal/i), page.getByRole("heading", { name: /secci[oó]n legal/i })],
      12_000
    );

    if (!infoGeneral || !detallesCuenta || !tusNegocios || !seccionLegal) {
      throw new Error("No se visualizan todas las secciones requeridas en Administrar Negocios.");
    }

    report.evidence.administrarNegociosView = await captureScreenshot(
      page,
      "04_administrar_negocios_view.png",
      true
    );
  });

  await runValidationStep(report, "Información General", async () => {
    const infoGeneralContainer = page
      .locator("section, div")
      .filter({ has: page.getByText(/informaci[oó]n general/i) })
      .first();

    await expect(infoGeneralContainer).toBeVisible({ timeout: 10_000 });
    const infoText = await infoGeneralContainer.innerText();

    const hasKnownEmail =
      infoText.includes(GOOGLE_ACCOUNT_EMAIL) ||
      /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(infoText);
    const hasName = containsLikelyName(infoText);
    const hasBusinessPlan = /business plan/i.test(infoText);
    const hasCambiarPlan = /cambiar plan/i.test(infoText);

    if (!hasName || !hasKnownEmail || !hasBusinessPlan || !hasCambiarPlan) {
      throw new Error("La sección Información General no contiene todos los elementos esperados.");
    }
  });

  await runValidationStep(report, "Detalles de la Cuenta", async () => {
    const detallesCuentaContainer = page
      .locator("section, div")
      .filter({ has: page.getByText(/detalles de la cuenta/i) })
      .first();

    await expect(detallesCuentaContainer).toBeVisible({ timeout: 10_000 });
    const detallesText = await detallesCuentaContainer.innerText();

    const hasCuentaCreada = /cuenta creada/i.test(detallesText);
    const hasEstadoActivo = /estado activo/i.test(detallesText);
    const hasIdiomaSeleccionado = /idioma seleccionado/i.test(detallesText);

    if (!hasCuentaCreada || !hasEstadoActivo || !hasIdiomaSeleccionado) {
      throw new Error("La sección Detalles de la Cuenta no coincide con la validación esperada.");
    }
  });

  await runValidationStep(report, "Tus Negocios", async () => {
    const tusNegociosContainer = page
      .locator("section, div")
      .filter({ has: page.getByText(/tus negocios/i) })
      .first();

    await expect(tusNegociosContainer).toBeVisible({ timeout: 10_000 });
    const negociosText = await tusNegociosContainer.innerText();

    const addButton = await firstVisible(buildTextLocatorCandidates(page, /agregar negocio/i), 8_000);
    const hasBusinessLimit = /tienes\s+2\s+de\s+3\s+negocios/i.test(negociosText);
    const hasBusinessList = negociosText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean).length >= 4;

    if (!addButton || !hasBusinessLimit || !hasBusinessList) {
      throw new Error("La sección Tus Negocios no muestra lista, botón y límite esperado.");
    }
  });

  await runValidationStep(report, "Términos y Condiciones", async () => {
    const result = await openLegalLinkAndValidate({
      appPage: page,
      context,
      linkRegex: /t[eé]rminos y condiciones/i,
      expectedHeadingRegex: /t[eé]rminos y condiciones/i,
      screenshotFileName: "08_terminos_y_condiciones.png"
    });

    report.evidence.terminosYCondiciones = result.screenshotPath;
    report.finalUrls.terminosYCondiciones = result.finalUrl;
  });

  await runValidationStep(report, "Política de Privacidad", async () => {
    const result = await openLegalLinkAndValidate({
      appPage: page,
      context,
      linkRegex: /pol[ií]tica de privacidad/i,
      expectedHeadingRegex: /pol[ií]tica de privacidad/i,
      screenshotFileName: "09_politica_de_privacidad.png"
    });

    report.evidence.politicaDePrivacidad = result.screenshotPath;
    report.finalUrls.politicaDePrivacidad = result.finalUrl;
  });

  const failedFields = REPORT_FIELDS.filter((field) => report.results[field] !== "PASS");
  await fs.promises.writeFile(REPORT_FILE, `${JSON.stringify(report, null, 2)}\n`, "utf-8");

  expect(failedFields, `Validaciones con FAIL: ${failedFields.join(", ")}`).toEqual([]);
});
