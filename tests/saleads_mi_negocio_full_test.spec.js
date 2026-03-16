const { test } = require("@playwright/test");
const fs = require("node:fs/promises");

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

async function isVisible(locator) {
  try {
    return await locator.isVisible();
  } catch (_) {
    return false;
  }
}

async function findFirstVisible(locators) {
  for (const locator of locators) {
    const candidate = locator.first();
    if (await isVisible(candidate)) {
      return candidate;
    }
  }

  throw new Error("No visible locator matched the expected text-based candidates.");
}

async function waitForUiSettle(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(800);
}

async function saveCheckpoint(page, testInfo, filename, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(filename),
    fullPage,
  });
}

async function assertAppLoaded(page) {
  const sidebarAside = page.locator("aside").filter({ hasText: /Negocio|Mi Negocio/i }).first();
  const sidebarNav = page.locator("nav").filter({ hasText: /Negocio|Mi Negocio/i }).first();
  const negocioText = page.getByText(/Negocio|Mi Negocio/i).first();

  await Promise.any([
    sidebarAside.waitFor({ state: "visible", timeout: 60000 }),
    sidebarNav.waitFor({ state: "visible", timeout: 60000 }),
    negocioText.waitFor({ state: "visible", timeout: 60000 }),
  ]);

  const sidebarVisible = (await isVisible(sidebarAside)) || (await isVisible(sidebarNav));
  if (!sidebarVisible) {
    throw new Error("Main interface loaded but sidebar navigation was not clearly visible.");
  }
}

async function openLegalPageFromLink({ page, context, linkText, headingRegex, screenshotName, testInfo }) {
  const trigger = await findFirstVisible([
    page.getByRole("link", { name: new RegExp(linkText, "i") }),
    page.getByRole("button", { name: new RegExp(linkText, "i") }),
    page.getByText(new RegExp(linkText, "i")),
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  await trigger.click();
  await waitForUiSettle(page);

  const popup = await popupPromise;
  const targetPage = popup || page;

  await targetPage.waitForLoadState("domcontentloaded");

  const heading = targetPage.getByRole("heading", { name: headingRegex }).first();
  if (await isVisible(heading)) {
    // Heading found by semantic role.
  } else {
    await targetPage.getByText(headingRegex).first().waitFor({ state: "visible", timeout: 25000 });
  }

  const bodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (bodyText.length < 120) {
    throw new Error(`Expected legal content text for "${linkText}" but found very short content.`);
  }

  await saveCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await page.goBack().catch(() => {});
    await waitForUiSettle(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.setTimeout(10 * 60 * 1000);

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL (or BASE_URL) to the SaleADS login page URL for the target environment."
    );
  }

  const statuses = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  const evidence = {
    terminosUrl: null,
    politicaPrivacidadUrl: null,
  };

  const runStep = async (name, handler) => {
    try {
      await handler();
      statuses[name] = "PASS";
    } catch (error) {
      statuses[name] = "FAIL";
      failures.push(`${name}: ${error.message}`);
      await saveCheckpoint(page, testInfo, `${name.replace(/\s+/g, "_").toLowerCase()}_failure.png`, true).catch(
        () => {}
      );
    }
  };

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiSettle(page);

  await runStep("Login", async () => {
    const loginTrigger = await findFirstVisible([
      page.getByRole("button", {
        name: /Sign in with Google|Iniciar sesión con Google|Continuar con Google|Google|Login|Iniciar sesión/i,
      }),
      page.getByRole("link", {
        name: /Sign in with Google|Iniciar sesión con Google|Continuar con Google|Google|Login|Iniciar sesión/i,
      }),
      page.getByText(/Sign in with Google|Iniciar sesión con Google|Continuar con Google|Google/i),
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await loginTrigger.click();
    await waitForUiSettle(page);

    const popup = await popupPromise;
    const googlePage = popup || page;

    const accountOption = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    if (await isVisible(accountOption)) {
      await accountOption.click();
      await waitForUiSettle(googlePage);
    }

    if (popup) {
      await popup.waitForLoadState("domcontentloaded").catch(() => {});
      await page.bringToFront();
    }

    await assertAppLoaded(page);
    await saveCheckpoint(page, testInfo, "01_dashboard_loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await findFirstVisible([
      page.getByRole("button", { name: /Negocio/i }),
      page.getByRole("link", { name: /Negocio/i }),
      page.getByText(/^Negocio$/i),
      page.getByText(/Negocio/i),
    ]);
    await negocioSection.click();
    await waitForUiSettle(page);

    const miNegocioOption = await findFirstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ]);
    await miNegocioOption.click();
    await waitForUiSettle(page);

    await page.getByText(/Agregar Negocio/i).first().waitFor({ state: "visible", timeout: 20000 });
    await page.getByText(/Administrar Negocios/i).first().waitFor({ state: "visible", timeout: 20000 });
    await saveCheckpoint(page, testInfo, "02_mi_negocio_menu_expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await findFirstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await agregarNegocio.click();
    await waitForUiSettle(page);

    const dialog = page.getByRole("dialog").first();
    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await Promise.any([
      dialog.waitFor({ state: "visible", timeout: 20000 }),
      modalTitle.waitFor({ state: "visible", timeout: 20000 }),
    ]);

    const businessNameField = await findFirstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.getByRole("textbox", { name: /Nombre del Negocio/i }),
    ]);
    await page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first().waitFor({ state: "visible", timeout: 20000 });
    await page.getByRole("button", { name: /Cancelar/i }).first().waitFor({ state: "visible", timeout: 20000 });
    await page.getByRole("button", { name: /Crear Negocio/i }).first().waitFor({ state: "visible", timeout: 20000 });

    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatización");
    await saveCheckpoint(page, testInfo, "03_agregar_negocio_modal.png");

    await page.getByRole("button", { name: /Cancelar/i }).first().click();
    await waitForUiSettle(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocioOption = await findFirstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ]);
    await miNegocioOption.click();
    await waitForUiSettle(page);

    const administrarNegocios = await findFirstVisible([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ]);
    await administrarNegocios.click();
    await waitForUiSettle(page);

    await page.getByText(/Información General/i).first().waitFor({ state: "visible", timeout: 20000 });
    await page.getByText(/Detalles de la Cuenta/i).first().waitFor({ state: "visible", timeout: 20000 });
    await page.getByText(/Tus Negocios/i).first().waitFor({ state: "visible", timeout: 20000 });
    await page.getByText(/Sección Legal/i).first().waitFor({ state: "visible", timeout: 20000 });
    await saveCheckpoint(page, testInfo, "04_administrar_negocios_view.png", true);
  });

  await runStep("Información General", async () => {
    const userName = page.locator("[data-testid*='name'], [data-test*='name']").first();
    const hasUserNameField = await isVisible(userName);
    const hasVisibleEmail = await isVisible(page.getByText(/@/).first());

    if (!hasUserNameField) {
      await page.getByText(/Información General/i).first().waitFor({ state: "visible", timeout: 10000 });
    }
    if (!hasVisibleEmail) {
      throw new Error("Could not confirm user email visibility in Información General.");
    }

    await page.getByText(/BUSINESS PLAN/i).first().waitFor({ state: "visible", timeout: 10000 });
    await page.getByRole("button", { name: /Cambiar Plan/i }).first().waitFor({ state: "visible", timeout: 10000 });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await page.getByText(/Cuenta creada/i).first().waitFor({ state: "visible", timeout: 10000 });
    await page.getByText(/Estado activo/i).first().waitFor({ state: "visible", timeout: 10000 });
    await page.getByText(/Idioma seleccionado/i).first().waitFor({ state: "visible", timeout: 10000 });
  });

  await runStep("Tus Negocios", async () => {
    await page.getByText(/Tus Negocios/i).first().waitFor({ state: "visible", timeout: 10000 });
    await page.getByRole("button", { name: /Agregar Negocio/i }).first().waitFor({ state: "visible", timeout: 10000 });
    await page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first().waitFor({ state: "visible", timeout: 10000 });
  });

  await runStep("Términos y Condiciones", async () => {
    evidence.terminosUrl = await openLegalPageFromLink({
      page,
      context,
      linkText: "Términos y Condiciones",
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotName: "05_terminos_y_condiciones.png",
      testInfo,
    });
  });

  await runStep("Política de Privacidad", async () => {
    evidence.politicaPrivacidadUrl = await openLegalPageFromLink({
      page,
      context,
      linkText: "Política de Privacidad",
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06_politica_de_privacidad.png",
      testInfo,
    });
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    statuses,
    evidence,
    failures,
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_full_report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  if (failures.length > 0) {
    throw new Error(`Validation failures detected:\n- ${failures.join("\n- ")}`);
  }
});
