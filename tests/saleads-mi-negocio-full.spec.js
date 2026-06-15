const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

const DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function firstVisible(candidates, timeoutMs = 2000) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    const visible = await locator.isVisible({ timeout: timeoutMs }).catch(() => false);
    if (visible) {
      return locator;
    }
  }
  return null;
}

async function clickAndWait(locator, page) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUiToLoad(page);
}

async function captureCheckpoint(page, testInfo, filename, fullPage = false) {
  const screenshotPath = testInfo.outputPath(filename);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(filename, {
    path: screenshotPath,
    contentType: "image/png"
  });
}

function buildReportTemplate() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
}

async function executeStep(report, errors, field, action) {
  try {
    await action();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    errors.push({
      field,
      message: error instanceof Error ? error.message : String(error)
    });
  }
}

async function ensureMiNegocioExpanded(page) {
  const administrarNegocios = page.getByText(/Administrar Negocios/i).first();
  const alreadyExpanded = await administrarNegocios.isVisible({ timeout: 1500 }).catch(() => false);
  if (alreadyExpanded) {
    return;
  }

  const negocioSection = await firstVisible([
    page.getByRole("button", { name: /^Negocio$/i }),
    page.getByRole("link", { name: /^Negocio$/i }),
    page.getByText(/^Negocio$/i)
  ]);

  if (negocioSection) {
    await clickAndWait(negocioSection, page);
  }

  const miNegocio = await firstVisible([
    page.getByRole("button", { name: /Mi Negocio/i }),
    page.getByRole("link", { name: /Mi Negocio/i }),
    page.getByText(/Mi Negocio/i)
  ]);

  if (!miNegocio) {
    throw new Error("No se encontró la opción 'Mi Negocio' en el menú lateral.");
  }

  await clickAndWait(miNegocio, page);
}

async function openLegalDocument({
  page,
  context,
  testInfo,
  linkTextRegex,
  headingRegex,
  screenshotName
}) {
  const link = await firstVisible([
    page.getByRole("link", { name: linkTextRegex }),
    page.getByRole("button", { name: linkTextRegex }),
    page.getByText(linkTextRegex)
  ]);

  if (!link) {
    throw new Error(`No se encontró el enlace legal: ${linkTextRegex}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  await clickAndWait(link, page);
  const popup = await popupPromise;

  let legalPage = page;
  let openedInPopup = false;

  if (popup) {
    legalPage = popup;
    openedInPopup = true;
    await waitForUiToLoad(legalPage);
  } else {
    await waitForUiToLoad(legalPage);
  }

  await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({ timeout: 20000 });

  const legalText = await legalPage.locator("body").innerText();
  if (!legalText || legalText.trim().length < 120) {
    throw new Error("No se detectó suficiente contenido legal en la página.");
  }

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (openedInPopup) {
    await legalPage.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = buildReportTemplate();
  const errors = [];
  const evidence = {
    "Términos y Condiciones URL": "",
    "Política de Privacidad URL": ""
  };
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.PLAYWRIGHT_TEST_BASE_URL;
  const googleEmail = process.env.GOOGLE_ACCOUNT_EMAIL || DEFAULT_GOOGLE_EMAIL;

  await executeStep(report, errors, "Login", async () => {
    expect(
      loginUrl,
      "Debe definir SALEADS_LOGIN_URL (o PLAYWRIGHT_TEST_BASE_URL) con la URL de login del entorno actual."
    ).toBeTruthy();

    await page.goto(loginUrl);
    await waitForUiToLoad(page);

    const alreadyLoggedIn = await page.getByText(/Mi Negocio|Negocio/i).first().isVisible({ timeout: 2500 }).catch(() => false);

    if (!alreadyLoggedIn) {
      const googleLoginButton = await firstVisible([
        page.getByRole("button", { name: /Sign in with Google|Iniciar sesión con Google|Continuar con Google/i }),
        page.getByRole("link", { name: /Sign in with Google|Iniciar sesión con Google|Continuar con Google/i }),
        page.getByRole("button", { name: /Google/i }),
        page.getByText(/Sign in with Google|Iniciar sesión con Google|Continuar con Google/i)
      ], 5000);

      if (!googleLoginButton) {
        throw new Error("No se encontró el botón de login con Google.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(googleLoginButton, page);
      const popup = await popupPromise;

      if (popup) {
        await waitForUiToLoad(popup);
        const accountOnPopup = popup.getByText(googleEmail, { exact: false }).first();
        const accountVisible = await accountOnPopup.isVisible({ timeout: 8000 }).catch(() => false);
        if (accountVisible) {
          await accountOnPopup.click();
        }
      } else {
        const accountOnCurrentPage = page.getByText(googleEmail, { exact: false }).first();
        const accountVisible = await accountOnCurrentPage.isVisible({ timeout: 6000 }).catch(() => false);
        if (accountVisible) {
          await accountOnCurrentPage.click();
        }
      }
    }

    await waitForUiToLoad(page);
    await expect(page.locator("main, [role='main']").first()).toBeVisible({ timeout: 60000 });
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 60000 });
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await executeStep(report, errors, "Mi Negocio menu", async () => {
    const negocioSection = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ], 5000);

    if (!negocioSection) {
      throw new Error("No se encontró la sección 'Negocio' en el sidebar.");
    }

    await clickAndWait(negocioSection, page);

    const miNegocioOption = await firstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i)
    ]);

    if (!miNegocioOption) {
      throw new Error("No se encontró la opción 'Mi Negocio'.");
    }

    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 10000 });
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png", false);
  });

  await executeStep(report, errors, "Agregar Negocio modal", async () => {
    const agregarNegocio = page.getByText(/^Agregar Negocio$/i).first();
    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 10000 });
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", false);

    const businessNameField = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: /Nombre del Negocio/i })
    ]);

    if (businessNameField) {
      await businessNameField.click();
      await businessNameField.fill("Negocio Prueba Automatización");
    }

    await clickAndWait(page.getByRole("button", { name: /Cancelar/i }).first(), page);
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeHidden({ timeout: 10000 });
  });

  await executeStep(report, errors, "Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);

    const administrarNegocios = await firstVisible([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i)
    ]);

    if (!administrarNegocios) {
      throw new Error("No se encontró la opción 'Administrar Negocios'.");
    }

    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 20000 });
    await captureCheckpoint(page, testInfo, "04-administrar-negocios.png", true);
  });

  await executeStep(report, errors, "Información General", async () => {
    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    await expect(page.getByText(googleEmail, { exact: false }).first()).toBeVisible();

    const profileText = await page.locator("body").innerText();
    if (!/\b[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}\b/.test(profileText)) {
      throw new Error("No se pudo validar un nombre de usuario visible.");
    }
  });

  await executeStep(report, errors, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await executeStep(report, errors, "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await executeStep(report, errors, "Términos y Condiciones", async () => {
    evidence["Términos y Condiciones URL"] = await openLegalDocument({
      page,
      context,
      testInfo,
      linkTextRegex: /Términos y Condiciones/i,
      headingRegex: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png"
    });
  });

  await executeStep(report, errors, "Política de Privacidad", async () => {
    evidence["Política de Privacidad URL"] = await openLegalDocument({
      page,
      context,
      testInfo,
      linkTextRegex: /Política de Privacidad/i,
      headingRegex: /Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png"
    });
  });

  const finalReport = {
    workflow: "saleads_mi_negocio_full_test",
    statusByField: report,
    evidence,
    failedValidations: errors
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report.json", {
    path: reportPath,
    contentType: "application/json"
  });
  console.log(JSON.stringify(finalReport, null, 2));

  expect(errors, "Hay validaciones en FAIL. Revisar final-report.json y screenshots adjuntos.").toEqual([]);
});
