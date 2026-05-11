const fs = require("fs/promises");
const { test, expect } = require("@playwright/test");

const REPORT_KEYS = [
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

function buildDefaultReport() {
  return REPORT_KEYS.reduce((acc, key) => {
    acc[key] = "FAIL";
    return acc;
  }, {});
}

function escapeRegex(raw) {
  return raw.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function asPattern(textOrPattern, exact = true) {
  if (textOrPattern instanceof RegExp) {
    return textOrPattern;
  }

  return exact
    ? new RegExp(`^\\s*${escapeRegex(textOrPattern)}\\s*$`, "i")
    : new RegExp(escapeRegex(textOrPattern), "i");
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 6000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    const isVisible = await locator.first().isVisible().catch(() => false);
    if (isVisible) {
      return locator.first();
    }
  }

  return null;
}

async function findClickableByVisibleText(page, label) {
  const pattern = asPattern(label, false);

  return firstVisibleLocator([
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByRole("menuitem", { name: pattern }),
    page.getByRole("tab", { name: pattern }),
    page.getByRole("option", { name: pattern }),
    page.getByText(pattern),
  ]);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToSettle(page);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const path = testInfo.outputPath(name);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function assertVisibleByText(page, textOrPattern, errorMessage) {
  const pattern = asPattern(textOrPattern, false);
  const locator = await firstVisibleLocator([
    page.getByRole("heading", { name: pattern }),
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByText(pattern),
  ]);

  if (!locator) {
    throw new Error(errorMessage);
  }

  await expect(locator).toBeVisible();
}

async function maybeSelectGoogleAccount(loginPage, accountEmail) {
  const accountOption = await findClickableByVisibleText(loginPage, accountEmail);
  if (accountOption) {
    await clickAndWait(loginPage, accountOption);
  }
}

async function validateLegalPage({
  page,
  context,
  testInfo,
  reportKey,
  linkText,
  headingText,
  screenshotName,
  legalUrls,
  applicationUrl,
}) {
  const link = await findClickableByVisibleText(page, linkText);
  if (!link) {
    throw new Error(`No se encontró el link legal: ${linkText}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await clickAndWait(page, link);

  const popupPage = await popupPromise;
  const legalPage = popupPage ?? page;

  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded");
    await waitForUiToSettle(popupPage);
  }

  await assertVisibleByText(
    legalPage,
    headingText,
    `No se encontró el encabezado legal: ${headingText}`
  );

  const legalBody = await legalPage.locator("body").innerText();
  if (!legalBody || legalBody.trim().length < 150) {
    throw new Error(`El contenido legal visible es insuficiente para ${reportKey}.`);
  }

  legalUrls[reportKey] = legalPage.url();
  await captureCheckpoint(legalPage, testInfo, screenshotName, true);

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
    return;
  }

  if (page.url() !== applicationUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(applicationUrl, { waitUntil: "domcontentloaded" });
    });
    await waitForUiToSettle(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = buildDefaultReport();
  const failures = [];
  const legalUrls = {};
  const accountEmail = "juanlucasbarbiergarzon@gmail.com";

  async function runStep(reportKey, execution) {
    try {
      await execution();
      report[reportKey] = "PASS";
    } catch (error) {
      report[reportKey] = "FAIL";
      failures.push(`${reportKey}: ${error.message}`);
    }
  }

  await runStep("Login", async () => {
    const baseUrl =
      process.env.SALEADS_BASE_URL || process.env.BASE_URL || process.env.SALEADS_LOGIN_URL;

    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No hay URL inicial. Define SALEADS_BASE_URL/BASE_URL o inicia la prueba con el login cargado."
      );
    }

    const loginTrigger = await firstVisibleLocator([
      page.getByRole("button", { name: /sign in with google|login with google|google/i }),
      page.getByRole("link", { name: /sign in with google|login with google|google/i }),
      page.getByText(/sign in with google|login with google|iniciar sesión con google|google/i),
    ]);

    if (!loginTrigger) {
      throw new Error("No se encontró el botón de login con Google.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickAndWait(page, loginTrigger);
    const popupPage = await popupPromise;

    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      await maybeSelectGoogleAccount(popupPage, accountEmail);
      await popupPage.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      await page.bringToFront();
    } else {
      await maybeSelectGoogleAccount(page, accountEmail);
    }

    await waitForUiToSettle(page);
    await assertVisibleByText(page, /Negocio|Mi Negocio/i, "No se detectó la navegación lateral.");

    const sidebar = await firstVisibleLocator([
      page.locator("aside"),
      page.getByRole("navigation"),
    ]);
    if (!sidebar) {
      throw new Error("No se detectó el sidebar principal tras el login.");
    }

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocio = await findClickableByVisibleText(page, "Negocio");
    if (negocio) {
      await clickAndWait(page, negocio);
    }

    const miNegocio = await findClickableByVisibleText(page, "Mi Negocio");
    if (!miNegocio) {
      throw new Error("No se encontró la opción Mi Negocio en el sidebar.");
    }
    await clickAndWait(page, miNegocio);

    await assertVisibleByText(page, "Agregar Negocio", "No se mostró Agregar Negocio.");
    await assertVisibleByText(page, "Administrar Negocios", "No se mostró Administrar Negocios.");
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await findClickableByVisibleText(page, "Agregar Negocio");
    if (!agregarNegocio) {
      throw new Error("No se encontró el acceso a Agregar Negocio.");
    }
    await clickAndWait(page, agregarNegocio);

    const modal = await firstVisibleLocator([
      page.getByRole("dialog", { name: /Crear Nuevo Negocio/i }),
      page.getByText(/Crear Nuevo Negocio/i),
    ]);
    if (!modal) {
      throw new Error("No apareció el modal Crear Nuevo Negocio.");
    }

    await assertVisibleByText(page, "Crear Nuevo Negocio", "Falta el título del modal.");
    await assertVisibleByText(page, "Nombre del Negocio", "Falta el campo Nombre del Negocio.");
    await assertVisibleByText(page, "Tienes 2 de 3 negocios", "Falta el contador de negocios.");
    await assertVisibleByText(page, "Cancelar", "Falta el botón Cancelar.");
    await assertVisibleByText(page, "Crear Negocio", "Falta el botón Crear Negocio.");
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const nombreInput = await firstVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator('input[name*="negocio"], input[id*="negocio"]').first(),
    ]);
    if (nombreInput) {
      await nombreInput.fill("Negocio Prueba Automatización");
    }

    const cancelar = await findClickableByVisibleText(page, "Cancelar");
    if (!cancelar) {
      throw new Error("No se encontró el botón Cancelar del modal.");
    }
    await clickAndWait(page, cancelar);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegocios = await findClickableByVisibleText(page, "Administrar Negocios");
    if (!administrarNegocios) {
      const miNegocio = await findClickableByVisibleText(page, "Mi Negocio");
      if (!miNegocio) {
        throw new Error("No se encontró Mi Negocio para reabrir el menú.");
      }
      await clickAndWait(page, miNegocio);
    }

    const administrarNegociosRetry = await findClickableByVisibleText(page, "Administrar Negocios");
    if (!administrarNegociosRetry) {
      throw new Error("No se encontró la opción Administrar Negocios.");
    }

    await clickAndWait(page, administrarNegociosRetry);
    await assertVisibleByText(page, "Información General", "No se encontró Información General.");
    await assertVisibleByText(page, "Detalles de la Cuenta", "No se encontró Detalles de la Cuenta.");
    await assertVisibleByText(page, "Tus Negocios", "No se encontró Tus Negocios.");
    await assertVisibleByText(page, "Sección Legal", "No se encontró la Sección Legal.");
    await captureCheckpoint(page, testInfo, "04-administrar-negocios.png", true);
  });

  await runStep("Información General", async () => {
    const bodyText = await page.locator("body").innerText();

    if (!bodyText.match(/@/)) {
      throw new Error("No se detectó un correo visible en Información General.");
    }

    await assertVisibleByText(page, /BUSINESS PLAN/i, "No se encontró BUSINESS PLAN.");
    await assertVisibleByText(page, "Cambiar Plan", "No se encontró Cambiar Plan.");

    const nameCandidate = await firstVisibleLocator([
      page.locator("h1, h2, h3").filter({ hasText: /[A-Za-z]{2,}/ }),
      page.locator("[data-testid*='user'], [class*='user']").first(),
    ]);
    if (!nameCandidate) {
      throw new Error("No se detectó nombre de usuario visible.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await assertVisibleByText(page, "Cuenta creada", "No se encontró 'Cuenta creada'.");
    await assertVisibleByText(page, "Estado activo", "No se encontró 'Estado activo'.");
    await assertVisibleByText(page, "Idioma seleccionado", "No se encontró 'Idioma seleccionado'.");
  });

  await runStep("Tus Negocios", async () => {
    await assertVisibleByText(page, "Tus Negocios", "No se encontró sección Tus Negocios.");
    await assertVisibleByText(page, "Agregar Negocio", "No se encontró botón Agregar Negocio.");
    await assertVisibleByText(page, "Tienes 2 de 3 negocios", "No se encontró el contador 2 de 3.");

    const businessItems = page.locator("li, tr, [role='row'], [class*='business'], [data-testid*='business']");
    const itemCount = await businessItems.count();
    if (itemCount < 1) {
      throw new Error("No se detectó listado de negocios visible.");
    }
  });

  const applicationUrl = page.url();

  await runStep("Términos y Condiciones", async () => {
    await validateLegalPage({
      page,
      context,
      testInfo,
      reportKey: "Términos y Condiciones",
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotName: "08-terminos-y-condiciones.png",
      legalUrls,
      applicationUrl,
    });
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalPage({
      page,
      context,
      testInfo,
      reportKey: "Política de Privacidad",
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotName: "09-politica-de-privacidad.png",
      legalUrls,
      applicationUrl,
    });
  });

  const finalReport = {
    generatedAt: new Date().toISOString(),
    report,
    legalUrls,
    failures,
  };

  const reportPath = testInfo.outputPath("10-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("10-final-report.json", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log("SaleADS Mi Negocio workflow report:");
  console.table(report);
  console.log("Legal URLs:", legalUrls);

  expect(failures, failures.join("\n")).toEqual([]);
});
