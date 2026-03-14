const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function findVisibleLocator(page, candidates, timeoutMs = 15_000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      try {
        if (await candidate.first().isVisible()) {
          return candidate.first();
        }
      } catch {
        // Try next candidate.
      }
    }

    await page.waitForTimeout(250);
  }

  return null;
}

function createStepRecorder() {
  const fields = [
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

  const results = Object.fromEntries(
    fields.map((name) => [name, { status: "PASS", checks: [] }]),
  );

  return {
    fields,
    results,
    pass(field, message) {
      results[field].checks.push({ status: "PASS", message });
    },
    fail(field, message, error) {
      results[field].status = "FAIL";
      results[field].checks.push({
        status: "FAIL",
        message,
        error: error ? String(error.message || error) : undefined,
      });
    },
  };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const recorder = createStepRecorder();
  const evidence = {
    screenshots: {},
    urls: {},
  };
  const artifactsDir = path.join(testInfo.outputDir, "saleads_mi_negocio_full_test");
  await fs.mkdir(artifactsDir, { recursive: true });

  async function takeCheckpoint(name, targetPage = page, fullPage = false) {
    const filename = `${name.replace(/\s+/g, "_").toLowerCase()}.png`;
    const destination = path.join(artifactsDir, filename);
    await targetPage.screenshot({ path: destination, fullPage });
    evidence.screenshots[name] = destination;
  }

  async function validateVisible(field, message, locator, timeout = 15_000) {
    try {
      await expect(locator).toBeVisible({ timeout });
      recorder.pass(field, message);
      return true;
    } catch (error) {
      recorder.fail(field, message, error);
      return false;
    }
  }

  async function clickAndWait(locator, targetPage = page) {
    await locator.click();
    await waitForUiLoad(targetPage);
  }

  async function ensureMenuExpanded() {
    const negocioTrigger = await findVisibleLocator(page, [
      page.getByRole("button", { name: /negocio/i }),
      page.getByRole("link", { name: /negocio/i }),
      page.getByText(/^negocio$/i),
    ]);

    if (negocioTrigger) {
      await clickAndWait(negocioTrigger);
    }

    const miNegocioTrigger = await findVisibleLocator(page, [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/^mi negocio$/i),
    ]);

    if (miNegocioTrigger) {
      await clickAndWait(miNegocioTrigger);
    }
  }

  async function validateLegalPage(field, linkLabel, headingLabel, screenshotName) {
    const legalLink = await findVisibleLocator(page, [
      page.getByRole("link", { name: new RegExp(escapeRegex(linkLabel), "i") }),
      page.getByRole("button", { name: new RegExp(escapeRegex(linkLabel), "i") }),
      page.getByText(new RegExp(`^\\s*${escapeRegex(linkLabel)}\\s*$`, "i")),
    ]);

    if (!legalLink) {
      recorder.fail(field, `No se encontró el enlace '${linkLabel}'.`);
      return;
    }

    const currentAppUrl = page.url();
    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

    await legalLink.click();
    await waitForUiLoad(page);

    const popup = await popupPromise;
    const targetPage = popup || page;
    await waitForUiLoad(targetPage);

    await validateVisible(
      field,
      `Heading '${headingLabel}' visible.`,
      targetPage.getByRole("heading", { name: new RegExp(escapeRegex(headingLabel), "i") }).first(),
    );

    try {
      const bodyText = (await targetPage.locator("body").innerText()).trim();
      if (bodyText.length > 180) {
        recorder.pass(field, "Legal content text is visible.");
      } else {
        recorder.fail(field, "Legal content text is too short or not visible.");
      }
    } catch (error) {
      recorder.fail(field, "Unable to read legal content text.", error);
    }

    evidence.urls[field] = targetPage.url();
    await takeCheckpoint(screenshotName, targetPage, true);

    if (popup && !popup.isClosed()) {
      await popup.close();
      await page.bringToFront();
      await waitForUiLoad(page);
      return;
    }

    if (page.url() !== currentAppUrl) {
      await page.goBack().catch(() => {});
      await waitForUiLoad(page);
    }
  }

  // STEP 1: Login with Google
  const startUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL ||
    process.env.URL;

  if (page.url() === "about:blank") {
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    } else {
      recorder.fail(
        "Login",
        "Browser started at about:blank and no URL was provided. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL).",
      );
    }
  }

  const loginButton = await findVisibleLocator(page, [
    page.getByRole("button", { name: /google/i }),
    page.getByRole("button", { name: /sign in/i }),
    page.getByRole("button", { name: /iniciar sesi[oó]n/i }),
    page.getByRole("link", { name: /google/i }),
    page.getByText(/sign in with google/i),
  ], 25_000);

  if (loginButton) {
    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await loginButton.click();
    await waitForUiLoad(page);

    const popup = await popupPromise;
    const googlePage = popup || page;
    await waitForUiLoad(googlePage);

    const accountLocator = await findVisibleLocator(googlePage, [
      googlePage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }),
      googlePage.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
      googlePage.getByRole("link", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
    ], 8_000);

    if (accountLocator) {
      await clickAndWait(accountLocator, googlePage);
      recorder.pass("Login", "Google account selector handled.");
    } else {
      recorder.pass("Login", "Google account selector did not appear, continuing.");
    }

    if (popup && !popup.isClosed()) {
      await popup.waitForTimeout(1_000).catch(() => {});
    }
  } else {
    recorder.fail("Login", "Google login button was not found.");
  }

  const appMainVisible = await validateVisible(
    "Login",
    "Main application interface appears.",
    (await findVisibleLocator(page, [page.locator("main"), page.locator("#root")], 20_000)) || page.locator("body"),
  );

  const sidebar = await findVisibleLocator(page, [
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator("[class*='sidebar']"),
  ], 20_000);

  if (sidebar) {
    recorder.pass("Login", "Left sidebar navigation is visible.");
  } else {
    recorder.fail("Login", "Left sidebar navigation is not visible.");
  }

  if (appMainVisible || sidebar) {
    await takeCheckpoint("dashboard_loaded", page, true);
  }

  // STEP 2: Open Mi Negocio menu
  await ensureMenuExpanded();

  const addBusinessMenuOption = await findVisibleLocator(page, [
    page.getByRole("link", { name: /agregar negocio/i }),
    page.getByRole("button", { name: /agregar negocio/i }),
    page.getByText(/^agregar negocio$/i),
  ]);

  const manageBusinessMenuOption = await findVisibleLocator(page, [
    page.getByRole("link", { name: /administrar negocios/i }),
    page.getByRole("button", { name: /administrar negocios/i }),
    page.getByText(/^administrar negocios$/i),
  ]);

  if (addBusinessMenuOption) {
    recorder.pass("Mi Negocio menu", "'Agregar Negocio' is visible.");
  } else {
    recorder.fail("Mi Negocio menu", "'Agregar Negocio' is not visible.");
  }

  if (manageBusinessMenuOption) {
    recorder.pass("Mi Negocio menu", "'Administrar Negocios' is visible.");
  } else {
    recorder.fail("Mi Negocio menu", "'Administrar Negocios' is not visible.");
  }

  if (addBusinessMenuOption || manageBusinessMenuOption) {
    recorder.pass("Mi Negocio menu", "Mi Negocio submenu is expanded.");
    await takeCheckpoint("mi_negocio_menu_expanded", page, true);
  } else {
    recorder.fail("Mi Negocio menu", "Mi Negocio submenu did not expand.");
  }

  // STEP 3: Validate Agregar Negocio modal
  if (addBusinessMenuOption) {
    await clickAndWait(addBusinessMenuOption);
  } else {
    recorder.fail("Agregar Negocio modal", "Cannot open modal because 'Agregar Negocio' menu option was not found.");
  }

  const modal = await findVisibleLocator(page, [
    page.getByRole("dialog"),
    page.locator("[role='dialog']"),
    page.locator(".modal:visible"),
  ], 10_000);

  if (!modal) {
    recorder.fail("Agregar Negocio modal", "Modal window did not appear.");
  } else {
    await validateVisible(
      "Agregar Negocio modal",
      "Modal title 'Crear Nuevo Negocio' is visible.",
      modal.getByText(/crear nuevo negocio/i).first(),
    );
    await validateVisible(
      "Agregar Negocio modal",
      "Input field 'Nombre del Negocio' exists.",
      modal.getByLabel(/nombre del negocio/i).first(),
    );
    await validateVisible(
      "Agregar Negocio modal",
      "Text 'Tienes 2 de 3 negocios' is visible.",
      modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first(),
    );
    await validateVisible(
      "Agregar Negocio modal",
      "Button 'Cancelar' is present.",
      modal.getByRole("button", { name: /cancelar/i }).first(),
    );
    await validateVisible(
      "Agregar Negocio modal",
      "Button 'Crear Negocio' is present.",
      modal.getByRole("button", { name: /crear negocio/i }).first(),
    );

    const businessNameInput = modal.getByLabel(/nombre del negocio/i).first();
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.click();
      await waitForUiLoad(page);
      await businessNameInput.fill("Negocio Prueba Automatización");
      recorder.pass("Agregar Negocio modal", "Optional input entry completed.");
    }

    await takeCheckpoint("agregar_negocio_modal", page, true);

    const cancelButton = modal.getByRole("button", { name: /cancelar/i }).first();
    if (await cancelButton.isVisible().catch(() => false)) {
      await clickAndWait(cancelButton);
    }
  }

  // STEP 4: Open Administrar Negocios
  await ensureMenuExpanded();

  const manageBusinessesOption = await findVisibleLocator(page, [
    page.getByRole("link", { name: /administrar negocios/i }),
    page.getByRole("button", { name: /administrar negocios/i }),
    page.getByText(/^administrar negocios$/i),
  ]);

  if (manageBusinessesOption) {
    await clickAndWait(manageBusinessesOption);
  } else {
    recorder.fail("Administrar Negocios view", "Unable to find 'Administrar Negocios' option.");
  }

  await validateVisible(
    "Administrar Negocios view",
    "Section 'Información General' exists.",
    page.getByText(/^información general$/i).first(),
  );
  await validateVisible(
    "Administrar Negocios view",
    "Section 'Detalles de la Cuenta' exists.",
    page.getByText(/^detalles de la cuenta$/i).first(),
  );
  await validateVisible(
    "Administrar Negocios view",
    "Section 'Tus Negocios' exists.",
    page.getByText(/^tus negocios$/i).first(),
  );
  await validateVisible(
    "Administrar Negocios view",
    "Section 'Sección Legal' exists.",
    page.getByText(/^sección legal$/i).first(),
  );
  await takeCheckpoint("administrar_negocios_account_page", page, true);

  // STEP 5: Validate Información General
  const accountEmailLocator = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
  const accountNameLocator = await findVisibleLocator(page, [
    page.getByText(/juanlucas|barbier|garzon/i),
    page.getByText(/nombre/i),
    page.getByText(/usuario/i),
  ]);

  if (accountNameLocator) {
    recorder.pass("Información General", "User name is visible.");
  } else {
    recorder.fail("Información General", "User name is not visible.");
  }

  await validateVisible("Información General", "User email is visible.", accountEmailLocator);
  await validateVisible(
    "Información General",
    "Text 'BUSINESS PLAN' is visible.",
    page.getByText(/business plan/i).first(),
  );
  await validateVisible(
    "Información General",
    "Button 'Cambiar Plan' is visible.",
    page.getByRole("button", { name: /cambiar plan/i }).first(),
  );

  // STEP 6: Validate Detalles de la Cuenta
  await validateVisible(
    "Detalles de la Cuenta",
    "'Cuenta creada' is visible.",
    page.getByText(/cuenta creada/i).first(),
  );
  await validateVisible(
    "Detalles de la Cuenta",
    "'Estado activo' is visible.",
    page.getByText(/estado activo/i).first(),
  );
  await validateVisible(
    "Detalles de la Cuenta",
    "'Idioma seleccionado' is visible.",
    page.getByText(/idioma seleccionado/i).first(),
  );

  // STEP 7: Validate Tus Negocios
  const businessesSection = await findVisibleLocator(page, [
    page.getByText(/^tus negocios$/i),
  ]);

  if (businessesSection) {
    recorder.pass("Tus Negocios", "Business list section is visible.");
  } else {
    recorder.fail("Tus Negocios", "Business list section is not visible.");
  }

  await validateVisible(
    "Tus Negocios",
    "Button 'Agregar Negocio' exists.",
    page.getByRole("button", { name: /agregar negocio/i }).first(),
  );
  await validateVisible(
    "Tus Negocios",
    "Text 'Tienes 2 de 3 negocios' is visible.",
    page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first(),
  );

  // STEP 8: Validate Términos y Condiciones
  await validateLegalPage(
    "Términos y Condiciones",
    "Términos y Condiciones",
    "Términos y Condiciones",
    "terminos_y_condiciones",
  );

  // STEP 9: Validate Política de Privacidad
  await validateLegalPage(
    "Política de Privacidad",
    "Política de Privacidad",
    "Política de Privacidad",
    "politica_de_privacidad",
  );

  // STEP 10: Final Report
  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
    generatedAt: new Date().toISOString(),
    report: Object.fromEntries(
      recorder.fields.map((field) => [field, recorder.results[field].status]),
    ),
    details: recorder.results,
    evidence,
  };

  const reportPath = path.join(artifactsDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

  // Attach report to Playwright output and fail test if any section failed.
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedSections = recorder.fields.filter(
    (field) => recorder.results[field].status === "FAIL",
  );
  expect(
    failedSections,
    `The following sections failed: ${failedSections.join(", ")}`,
  ).toEqual([]);
});
