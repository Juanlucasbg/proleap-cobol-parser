const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

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

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const result = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const details = {
    checkpoints: {},
    legalUrls: {},
    errors: [],
  };
  const checkpointsDir = path.join(testInfo.outputDir, "checkpoints");
  await fs.mkdir(checkpointsDir, { recursive: true });

  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.PLAYWRIGHT_BASE_URL ||
    process.env.BASE_URL ||
    "";

  async function waitForUi(targetPage = page) {
    await targetPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await targetPage.waitForTimeout(1000);
  }

  async function capture(targetPage, fileName, label, fullPage = true) {
    const screenshotPath = path.join(checkpointsDir, fileName);
    await targetPage.screenshot({ path: screenshotPath, fullPage });
    details.checkpoints[label] = screenshotPath;
    await testInfo.attach(label, { path: screenshotPath, contentType: "image/png" });
  }

  async function firstVisible(locatorCandidates) {
    for (const locator of locatorCandidates) {
      const first = locator.first();
      try {
        if (await first.isVisible({ timeout: 4000 })) {
          return first;
        }
      } catch {
        // Continue trying the next locator.
      }
    }
    return null;
  }

  async function clickAndWait(locator, targetPage = page) {
    await expect(locator).toBeVisible({ timeout: 15000 });
    await locator.click();
    await waitForUi(targetPage);
  }

  async function writeFinalReportAndAttach() {
    const reportPayload = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      report: result,
      evidence: details.checkpoints,
      legalUrls: details.legalUrls,
      errors: details.errors,
    };

    const reportPath = path.join(testInfo.outputDir, "saleads-mi-negocio-final-report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(reportPayload, null, 2)}\n`, "utf8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    // This line helps CI logs expose the final status quickly.
    console.log("SALEADS_MI_NEGOCIO_REPORT:", JSON.stringify(reportPayload));
  }

  async function chooseGoogleAccountIfVisible(targetPage) {
    const accountEmail = "juanlucasbarbiergarzon@gmail.com";
    const accountOption = await firstVisible([
      targetPage.getByText(accountEmail, { exact: true }),
      targetPage.getByRole("button", { name: new RegExp(accountEmail, "i") }),
      targetPage.locator(`[data-identifier="${accountEmail}"]`),
    ]);

    if (accountOption) {
      await clickAndWait(accountOption, targetPage);
    }
  }

  async function validateLegalPage(linkText, headingRegex, reportKey, fileName) {
    const appPage = page;
    const beforeClickUrl = appPage.url();

    try {
      const legalEntry = await firstVisible([
        appPage.getByRole("link", { name: new RegExp(linkText, "i") }),
        appPage.getByRole("button", { name: new RegExp(linkText, "i") }),
        appPage.getByText(new RegExp(linkText, "i")),
      ]);

      if (!legalEntry) {
        throw new Error(`No se encontro el enlace o boton "${linkText}".`);
      }

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(legalEntry, appPage);
      let legalPage = await popupPromise;

      if (legalPage) {
        await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 });
        await waitForUi(legalPage);
      } else {
        legalPage = appPage;
        await waitForUi(legalPage);
      }

      const legalHeading = await firstVisible([
        legalPage.getByRole("heading", { name: headingRegex }),
        legalPage.getByText(headingRegex),
      ]);

      if (!legalHeading) {
        throw new Error(`No se encontro el encabezado legal esperado para "${linkText}".`);
      }

      await expect(legalHeading).toBeVisible({ timeout: 15000 });
      const legalBodyText = (await legalPage.locator("body").innerText()).trim();
      if (legalBodyText.length < 200) {
        throw new Error(`El contenido legal para "${linkText}" parece insuficiente (${legalBodyText.length} caracteres).`);
      }

      await capture(legalPage, fileName, `${linkText} screenshot`);
      details.legalUrls[linkText] = legalPage.url();
      result[reportKey] = "PASS";

      if (legalPage !== appPage) {
        await legalPage.close().catch(() => {});
        await appPage.bringToFront();
        await waitForUi(appPage);
      } else if (appPage.url() !== beforeClickUrl) {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(appPage);
      }
    } catch (error) {
      result[reportKey] = "FAIL";
      details.errors.push({
        step: reportKey,
        message: error.message,
      });

      if (appPage.url() !== beforeClickUrl) {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(appPage);
      }
    }
  }

  if (page.url() === "about:blank" || page.url().startsWith("data:")) {
    if (!loginUrl) {
      throw new Error(
        "La prueba requiere SALEADS_LOGIN_URL (o BASE_URL / PLAYWRIGHT_BASE_URL) cuando no inicia en la pagina de login."
      );
    }
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  // Step 1 - Login with Google
  let loginOk = false;
  try {
    const loginButton = await firstVisible([
      page.getByRole("button", { name: /google|sign in|iniciar sesion|continuar/i }),
      page.getByRole("link", { name: /google|sign in|iniciar sesion|continuar/i }),
      page.getByText(/sign in with google|iniciar sesion con google|continuar con google/i),
    ]);

    if (!loginButton) {
      throw new Error("No se encontro un boton de inicio de sesion con Google.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
      await waitForUi(popup);
      await chooseGoogleAccountIfVisible(popup);
      await popup.waitForClose({ timeout: 30000 }).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await chooseGoogleAccountIfVisible(page);
      await waitForUi(page);
    }

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator('[class*="sidebar"]'),
    ]);

    if (!sidebar) {
      throw new Error("No se detecto la barra lateral despues del login.");
    }

    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 20000 });
    await capture(page, "01-dashboard-loaded.png", "Dashboard loaded");
    result["Login"] = "PASS";
    loginOk = true;
  } catch (error) {
    result["Login"] = "FAIL";
    details.errors.push({ step: "Login", message: error.message });
  }

  if (!loginOk) {
    await writeFinalReportAndAttach();
    throw new Error("Login failed. Workflow validations were not executed.");
  }

  // Step 2 - Open Mi Negocio menu
  try {
    const negocioSection = await firstVisible([
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
    ]);
    if (negocioSection) {
      await clickAndWait(negocioSection, page);
    }

    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ]);

    if (!miNegocio) {
      throw new Error('No se encontro la opcion "Mi Negocio".');
    }

    await clickAndWait(miNegocio, page);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15000 });
    await capture(page, "02-mi-negocio-expanded.png", "Mi Negocio menu expanded");
    result["Mi Negocio menu"] = "PASS";
  } catch (error) {
    result["Mi Negocio menu"] = "FAIL";
    details.errors.push({ step: "Mi Negocio menu", message: error.message });
  }

  // Step 3 - Validate Agregar Negocio modal
  try {
    const agregarNegocio = await firstVisible([
      page.locator("aside").getByText(/^Agregar Negocio$/i),
      page.getByRole("menuitem", { name: /Agregar Negocio/i }),
      page.getByRole("link", { name: /Agregar Negocio/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    if (!agregarNegocio) {
      throw new Error('No se encontro la opcion "Agregar Negocio".');
    }

    await clickAndWait(agregarNegocio, page);
    const modal = await firstVisible([
      page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
      page.locator('[role="dialog"], .modal').filter({ hasText: /Crear Nuevo Negocio/i }),
    ]);

    if (!modal) {
      throw new Error('No se encontro el modal "Crear Nuevo Negocio".');
    }

    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 15000 });
    const businessNameInput = await firstVisible([
      modal.getByLabel(/Nombre del Negocio/i),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.getByRole("textbox"),
    ]);
    if (!businessNameInput) {
      throw new Error('No se encontro el campo "Nombre del Negocio".');
    }

    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 15000 });
    const cancelButton = await firstVisible([
      modal.getByRole("button", { name: /Cancelar/i }),
      modal.getByText(/^Cancelar$/i),
    ]);
    const createButton = await firstVisible([
      modal.getByRole("button", { name: /Crear Negocio/i }),
      modal.getByText(/Crear Negocio/i),
    ]);

    if (!cancelButton || !createButton) {
      throw new Error('No se encontraron los botones "Cancelar" y "Crear Negocio".');
    }

    await capture(page, "03-agregar-negocio-modal.png", "Agregar Negocio modal");
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(cancelButton, page);
    await expect(modal).toBeHidden({ timeout: 10000 }).catch(() => {});
    result["Agregar Negocio modal"] = "PASS";
  } catch (error) {
    result["Agregar Negocio modal"] = "FAIL";
    details.errors.push({ step: "Agregar Negocio modal", message: error.message });
  }

  // Step 4 - Open Administrar Negocios
  try {
    const administrarNegociosVisible = await page
      .getByText(/Administrar Negocios/i)
      .first()
      .isVisible({ timeout: 3000 })
      .catch(() => false);

    if (!administrarNegociosVisible) {
      const miNegocio = await firstVisible([
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ]);
      if (miNegocio) {
        await clickAndWait(miNegocio, page);
      }
    }

    const administrarNegocios = await firstVisible([
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ]);

    if (!administrarNegocios) {
      throw new Error('No se encontro la opcion "Administrar Negocios".');
    }

    await clickAndWait(administrarNegocios, page);
    await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible({ timeout: 20000 });
    await capture(page, "04-administrar-negocios-page.png", "Administrar Negocios page");
    result["Administrar Negocios view"] = "PASS";
  } catch (error) {
    result["Administrar Negocios view"] = "FAIL";
    details.errors.push({ step: "Administrar Negocios view", message: error.message });
  }

  // Step 5 - Validate Informacion General
  try {
    const infoSection = page
      .locator("section, div, article")
      .filter({ hasText: /Informacion General|Información General/i })
      .first();

    await expect(infoSection).toBeVisible({ timeout: 15000 });
    await expect(
      infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()
    ).toBeVisible({ timeout: 15000 });
    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 15000 });

    const changePlanButton = await firstVisible([
      infoSection.getByRole("button", { name: /Cambiar Plan/i }),
      infoSection.getByText(/Cambiar Plan/i),
    ]);
    if (!changePlanButton) {
      throw new Error('No se encontro el boton "Cambiar Plan".');
    }

    const sectionText = (await infoSection.innerText())
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const likelyUserName = sectionText.some(
      (line) =>
        /^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ' -]{2,}$/.test(line) &&
        !/Informacion General|Información General|BUSINESS PLAN|Cambiar Plan/i.test(line)
    );

    if (!likelyUserName) {
      throw new Error("No se detecto un nombre de usuario visible en Informacion General.");
    }

    result["Información General"] = "PASS";
  } catch (error) {
    result["Información General"] = "FAIL";
    details.errors.push({ step: "Información General", message: error.message });
  }

  // Step 6 - Validate Detalles de la Cuenta
  try {
    const accountDetailsSection = page
      .locator("section, div, article")
      .filter({ hasText: /Detalles de la Cuenta/i })
      .first();

    await expect(accountDetailsSection).toBeVisible({ timeout: 15000 });
    await expect(accountDetailsSection.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
    await expect(accountDetailsSection.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 15000 });
    await expect(accountDetailsSection.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 15000 });
    result["Detalles de la Cuenta"] = "PASS";
  } catch (error) {
    result["Detalles de la Cuenta"] = "FAIL";
    details.errors.push({ step: "Detalles de la Cuenta", message: error.message });
  }

  // Step 7 - Validate Tus Negocios
  try {
    const businessesSection = page
      .locator("section, div, article")
      .filter({ hasText: /Tus Negocios/i })
      .first();

    await expect(businessesSection).toBeVisible({ timeout: 15000 });
    const addBusinessButton = await firstVisible([
      businessesSection.getByRole("button", { name: /Agregar Negocio/i }),
      businessesSection.getByText(/Agregar Negocio/i),
    ]);
    if (!addBusinessButton) {
      throw new Error('No se encontro el boton "Agregar Negocio" en "Tus Negocios".');
    }

    await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({
      timeout: 15000,
    });
    result["Tus Negocios"] = "PASS";
  } catch (error) {
    result["Tus Negocios"] = "FAIL";
    details.errors.push({ step: "Tus Negocios", message: error.message });
  }

  // Step 8 - Validate Terminos y Condiciones
  await validateLegalPage(
    "Términos y Condiciones",
    /Terminos y Condiciones|Términos y Condiciones/i,
    "Términos y Condiciones",
    "05-terminos-y-condiciones.png"
  );

  // Step 9 - Validate Politica de Privacidad
  await validateLegalPage(
    "Política de Privacidad",
    /Politica de Privacidad|Política de Privacidad/i,
    "Política de Privacidad",
    "06-politica-de-privacidad.png"
  );

  await writeFinalReportAndAttach();

  const failedSteps = Object.entries(result)
    .filter(([, status]) => status !== "PASS")
    .map(([step]) => step);

  if (failedSteps.length > 0) {
    throw new Error(`Validaciones fallidas: ${failedSteps.join(", ")}`);
  }
});
