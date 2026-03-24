const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
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
  "Política de Privacidad"
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function timestampToken() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function waitForUiLoad(page) {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => null),
    page.waitForLoadState("domcontentloaded", { timeout: 7000 }).catch(() => null),
    page.waitForTimeout(1200)
  ]);
}

async function isLocatorVisible(locator, timeout = 3000) {
  try {
    await expect(locator.first()).toBeVisible({ timeout });
    return true;
  } catch {
    return false;
  }
}

async function findFirstVisible(locatorFactories, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const factory of locatorFactories) {
      const locator = factory();
      if (await isLocatorVisible(locator, 500)) {
        return locator.first();
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  return null;
}

async function clickAndWait(page, locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => null);
  await locator.click();
  await waitForUiLoad(page);
}

async function takeCheckpointScreenshot(targetPage, screenshotDir, name, fullPage = false) {
  const filePath = path.join(screenshotDir, `${name}_${timestampToken()}.png`);
  await targetPage.screenshot({ path: filePath, fullPage });
  return filePath;
}

function buildTextBasedLocators(page, text) {
  const escaped = escapeRegExp(text);
  const exactRegex = new RegExp(`^\\s*${escaped}\\s*$`, "i");

  return [
    () => page.getByRole("button", { name: exactRegex }),
    () => page.getByRole("link", { name: exactRegex }),
    () => page.getByRole("menuitem", { name: exactRegex }),
    () => page.getByRole("tab", { name: exactRegex }),
    () => page.getByRole("treeitem", { name: exactRegex }),
    () => page.getByText(exactRegex)
  ];
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const runId = timestampToken();
  const artifactsRoot = path.resolve(__dirname, "..", "artifacts");
  const screenshotDir = path.join(artifactsRoot, "screenshots");
  const reportDir = path.join(artifactsRoot, "reports");

  fs.mkdirSync(screenshotDir, { recursive: true });
  fs.mkdirSync(reportDir, { recursive: true });

  const results = {};
  for (const field of REPORT_FIELDS) {
    results[field] = {
      pass: false,
      details: []
    };
  }

  const evidence = [];
  const legalUrls = {};

  const startUrl = process.env.SALEADS_START_URL || process.env.BASE_URL;
  if (page.url() === "about:blank" && startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  }

  const currentUrl = page.url();
  if (!currentUrl || currentUrl === "about:blank") {
    throw new Error(
      "No application URL detected. Open the SaleADS login page first or set SALEADS_START_URL."
    );
  }

  testInfo.setTimeout(testInfo.timeout + 120000);

  // Step 1 - Login with Google.
  const sidebarLocatorFactories = [
    () => page.locator("aside"),
    () => page.getByRole("navigation"),
    () => page.getByText(/\bNegocio\b/i),
    () => page.getByText(/\bMi Negocio\b/i)
  ];

  let sidebarVisible = Boolean(await findFirstVisible(sidebarLocatorFactories, 8000));

  if (!sidebarVisible) {
    const loginButton = await findFirstVisible(
      [
        () => page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        () => page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        () => page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
      ],
      20000
    );

    if (loginButton) {
      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 25000 }).catch(() => null);

        const accountOption = await findFirstVisible(
          [
            () => popup.getByText(/^juanlucasbarbiergarzon@gmail\.com$/i),
            () => popup.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
            () => popup.getByRole("link", { name: /juanlucasbarbiergarzon@gmail\.com/i })
          ],
          12000
        );

        if (accountOption) {
          await clickAndWait(popup, accountOption);
        }
      } else {
        const accountOptionOnMainPage = await findFirstVisible(
          [
            () => page.getByText(/^juanlucasbarbiergarzon@gmail\.com$/i),
            () => page.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
            () => page.getByRole("link", { name: /juanlucasbarbiergarzon@gmail\.com/i })
          ],
          12000
        );

        if (accountOptionOnMainPage) {
          await clickAndWait(page, accountOptionOnMainPage);
        }
      }
    }
  }

  await waitForUiLoad(page);
  sidebarVisible = Boolean(await findFirstVisible(sidebarLocatorFactories, 30000));
  const appInterfaceVisible = Boolean(
    await findFirstVisible(
      [
        () => page.locator("main"),
        () => page.getByText(/\bDashboard\b/i),
        () => page.getByText(/\bMi Negocio\b/i)
      ],
      20000
    )
  );

  results["Login"].pass = sidebarVisible && appInterfaceVisible;
  results["Login"].details.push(`Sidebar visible: ${sidebarVisible}`);
  results["Login"].details.push(`Main interface visible: ${appInterfaceVisible}`);
  evidence.push(
    await takeCheckpointScreenshot(page, screenshotDir, "step1_dashboard_loaded", true)
  );

  // Step 2 - Open Mi Negocio menu.
  const negocioSection = await findFirstVisible(buildTextBasedLocators(page, "Negocio"), 12000);
  if (negocioSection) {
    await clickAndWait(page, negocioSection);
  }

  const miNegocioOption = await findFirstVisible(buildTextBasedLocators(page, "Mi Negocio"), 12000);
  if (miNegocioOption) {
    await clickAndWait(page, miNegocioOption);
  }

  const agregarNegocioInMenu = Boolean(
    await findFirstVisible(buildTextBasedLocators(page, "Agregar Negocio"), 12000)
  );
  const administrarNegociosInMenu = Boolean(
    await findFirstVisible(buildTextBasedLocators(page, "Administrar Negocios"), 12000)
  );

  results["Mi Negocio menu"].pass = agregarNegocioInMenu && administrarNegociosInMenu;
  results["Mi Negocio menu"].details.push(`"Agregar Negocio" visible: ${agregarNegocioInMenu}`);
  results["Mi Negocio menu"].details.push(
    `"Administrar Negocios" visible: ${administrarNegociosInMenu}`
  );
  evidence.push(
    await takeCheckpointScreenshot(page, screenshotDir, "step2_mi_negocio_expanded_menu", true)
  );

  // Step 3 - Validate Agregar Negocio modal.
  let addBusinessSource = await findFirstVisible(
    [
      ...buildTextBasedLocators(page, "Agregar Negocio"),
      () => page.getByRole("button", { name: /agregar negocio/i })
    ],
    12000
  );

  if (!addBusinessSource) {
    const reopenMenu = await findFirstVisible(buildTextBasedLocators(page, "Mi Negocio"), 8000);
    if (reopenMenu) {
      await clickAndWait(page, reopenMenu);
      addBusinessSource = await findFirstVisible(
        [
          ...buildTextBasedLocators(page, "Agregar Negocio"),
          () => page.getByRole("button", { name: /agregar negocio/i })
        ],
        12000
      );
    }
  }

  if (addBusinessSource) {
    await clickAndWait(page, addBusinessSource);
  }

  const modalTitleVisible = await isLocatorVisible(
    page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
    15000
  );
  const businessNameInputVisible =
    (await isLocatorVisible(page.getByLabel(/Nombre del Negocio/i), 6000)) ||
    (await isLocatorVisible(page.getByPlaceholder(/Nombre del Negocio/i), 6000));
  const businessQuotaVisible = await isLocatorVisible(
    page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i),
    6000
  );
  const cancelButtonVisible = await isLocatorVisible(
    page.getByRole("button", { name: /Cancelar/i }),
    6000
  );
  const createButtonVisible = await isLocatorVisible(
    page.getByRole("button", { name: /Crear Negocio/i }),
    6000
  );

  results["Agregar Negocio modal"].pass =
    modalTitleVisible &&
    businessNameInputVisible &&
    businessQuotaVisible &&
    cancelButtonVisible &&
    createButtonVisible;
  results["Agregar Negocio modal"].details.push(
    `"Crear Nuevo Negocio" visible: ${modalTitleVisible}`
  );
  results["Agregar Negocio modal"].details.push(
    `"Nombre del Negocio" input visible: ${businessNameInputVisible}`
  );
  results["Agregar Negocio modal"].details.push(
    `"Tienes 2 de 3 negocios" visible: ${businessQuotaVisible}`
  );
  results["Agregar Negocio modal"].details.push(`"Cancelar" button visible: ${cancelButtonVisible}`);
  results["Agregar Negocio modal"].details.push(
    `"Crear Negocio" button visible: ${createButtonVisible}`
  );

  if (businessNameInputVisible) {
    const nameInput =
      (await isLocatorVisible(page.getByLabel(/Nombre del Negocio/i), 1000))
        ? page.getByLabel(/Nombre del Negocio/i).first()
        : page.getByPlaceholder(/Nombre del Negocio/i).first();
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
  }

  evidence.push(await takeCheckpointScreenshot(page, screenshotDir, "step3_agregar_negocio_modal", true));

  if (cancelButtonVisible) {
    const cancelButton = page.getByRole("button", { name: /Cancelar/i }).first();
    await clickAndWait(page, cancelButton);
  }

  // Step 4 - Open Administrar Negocios.
  const miNegocioAgain = await findFirstVisible(buildTextBasedLocators(page, "Mi Negocio"), 12000);
  if (miNegocioAgain) {
    await clickAndWait(page, miNegocioAgain);
  }

  const administrarNegocios = await findFirstVisible(
    buildTextBasedLocators(page, "Administrar Negocios"),
    12000
  );
  if (administrarNegocios) {
    await clickAndWait(page, administrarNegocios);
  }
  await waitForUiLoad(page);

  const infoGeneralVisible = await isLocatorVisible(page.getByText(/^Informaci[oó]n General$/i), 15000);
  const detallesCuentaVisible = await isLocatorVisible(page.getByText(/^Detalles de la Cuenta$/i), 15000);
  const tusNegociosVisible = await isLocatorVisible(page.getByText(/^Tus Negocios$/i), 15000);
  const seccionLegalVisible = await isLocatorVisible(page.getByText(/^Secci[oó]n Legal$/i), 15000);

  results["Administrar Negocios view"].pass =
    infoGeneralVisible && detallesCuentaVisible && tusNegociosVisible && seccionLegalVisible;
  results["Administrar Negocios view"].details.push(
    `"Información General" visible: ${infoGeneralVisible}`
  );
  results["Administrar Negocios view"].details.push(
    `"Detalles de la Cuenta" visible: ${detallesCuentaVisible}`
  );
  results["Administrar Negocios view"].details.push(`"Tus Negocios" visible: ${tusNegociosVisible}`);
  results["Administrar Negocios view"].details.push(`"Sección Legal" visible: ${seccionLegalVisible}`);
  evidence.push(
    await takeCheckpointScreenshot(page, screenshotDir, "step4_administrar_negocios_page", true)
  );

  // Step 5 - Validate Información General.
  const userEmailVisible =
    (await isLocatorVisible(page.getByText(/juanlucasbarbiergarzon@gmail\.com/i), 5000)) ||
    (await isLocatorVisible(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i), 5000));
  const userNameVisible =
    (await isLocatorVisible(page.getByText(/\bJuan\b/i), 5000)) ||
    (await isLocatorVisible(page.getByText(/\bNombre\b/i), 5000)) ||
    (await isLocatorVisible(page.getByText(/\bUsuario\b/i), 5000));
  const businessPlanVisible = await isLocatorVisible(page.getByText(/BUSINESS PLAN/i), 5000);
  const cambiarPlanVisible = await isLocatorVisible(
    page.getByRole("button", { name: /Cambiar Plan/i }),
    5000
  );

  results["Información General"].pass =
    userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
  results["Información General"].details.push(`User name visible: ${userNameVisible}`);
  results["Información General"].details.push(`User email visible: ${userEmailVisible}`);
  results["Información General"].details.push(`"BUSINESS PLAN" visible: ${businessPlanVisible}`);
  results["Información General"].details.push(`"Cambiar Plan" visible: ${cambiarPlanVisible}`);

  // Step 6 - Validate Detalles de la Cuenta.
  const cuentaCreadaVisible = await isLocatorVisible(page.getByText(/Cuenta creada/i), 5000);
  const estadoActivoVisible = await isLocatorVisible(page.getByText(/Estado activo/i), 5000);
  const idiomaSeleccionadoVisible = await isLocatorVisible(
    page.getByText(/Idioma seleccionado/i),
    5000
  );

  results["Detalles de la Cuenta"].pass =
    cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible;
  results["Detalles de la Cuenta"].details.push(`"Cuenta creada" visible: ${cuentaCreadaVisible}`);
  results["Detalles de la Cuenta"].details.push(`"Estado activo" visible: ${estadoActivoVisible}`);
  results["Detalles de la Cuenta"].details.push(
    `"Idioma seleccionado" visible: ${idiomaSeleccionadoVisible}`
  );

  // Step 7 - Validate Tus Negocios.
  const businessListVisible =
    (await isLocatorVisible(page.locator("table").first(), 4000)) ||
    (await isLocatorVisible(page.locator("[role='list']").first(), 4000)) ||
    (await isLocatorVisible(page.locator("ul").first(), 4000));
  const addBusinessButtonVisible = Boolean(
    await findFirstVisible(
      [
        () => page.getByRole("button", { name: /Agregar Negocio/i }),
        () => page.getByRole("link", { name: /Agregar Negocio/i }),
        () => page.getByText(/^Agregar Negocio$/i)
      ],
      5000
    )
  );
  const quotaVisibleInBusinesses = await isLocatorVisible(
    page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i),
    5000
  );

  results["Tus Negocios"].pass =
    businessListVisible && addBusinessButtonVisible && quotaVisibleInBusinesses;
  results["Tus Negocios"].details.push(`Business list visible: ${businessListVisible}`);
  results["Tus Negocios"].details.push(`"Agregar Negocio" exists: ${addBusinessButtonVisible}`);
  results["Tus Negocios"].details.push(
    `"Tienes 2 de 3 negocios" visible: ${quotaVisibleInBusinesses}`
  );

  // Helper for steps 8 and 9 (legal links).
  async function validateLegalLink({
    fieldName,
    linkText,
    headingPattern,
    screenshotName
  }) {
    const appUrlBeforeClick = page.url();
    const link = await findFirstVisible(buildTextBasedLocators(page, linkText), 12000);

    if (!link) {
      results[fieldName].pass = false;
      results[fieldName].details.push(`Could not locate legal link: "${linkText}"`);
      return;
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    const navigationPromise = page
      .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 12000 })
      .catch(() => null);

    await clickAndWait(page, link);

    const popup = await popupPromise;
    const targetPage = popup || page;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => null);
    } else {
      await navigationPromise;
      await waitForUiLoad(page);
    }

    const headingVisible = await isLocatorVisible(
      targetPage.getByRole("heading", { name: headingPattern }),
      20000
    );
    const legalTextVisible =
      (await isLocatorVisible(targetPage.locator("main p, article p, body p").first(), 8000)) ||
      (await isLocatorVisible(
        targetPage.getByText(/t[ée]rminos|condiciones|pol[íi]tica|privacidad|legal/i),
        8000
      ));

    const legalScreenshot = await takeCheckpointScreenshot(
      targetPage,
      screenshotDir,
      screenshotName,
      true
    );
    evidence.push(legalScreenshot);
    legalUrls[fieldName] = targetPage.url();

    results[fieldName].pass = headingVisible && legalTextVisible;
    results[fieldName].details.push(`Heading visible: ${headingVisible}`);
    results[fieldName].details.push(`Legal body text visible: ${legalTextVisible}`);
    results[fieldName].details.push(`Final URL: ${targetPage.url()}`);
    results[fieldName].details.push(`Opened in new tab: ${Boolean(popup)}`);

    if (popup) {
      await popup.close().catch(() => null);
      await page.bringToFront();
      await waitForUiLoad(page);
    } else if (page.url() !== appUrlBeforeClick) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUiLoad(page);
    }
  }

  // Step 8 - Términos y Condiciones.
  await validateLegalLink({
    fieldName: "Términos y Condiciones",
    linkText: "Términos y Condiciones",
    headingPattern: /T[ée]rminos y Condiciones/i,
    screenshotName: "step8_terminos_y_condiciones"
  });

  // Step 9 - Política de Privacidad.
  await validateLegalLink({
    fieldName: "Política de Privacidad",
    linkText: "Política de Privacidad",
    headingPattern: /Pol[íi]tica de Privacidad/i,
    screenshotName: "step9_politica_de_privacidad"
  });

  // Step 10 - Final report.
  const finalReport = {
    test_name: "saleads_mi_negocio_full_test",
    run_id: runId,
    started_from_url: currentUrl,
    executed_at: new Date().toISOString(),
    summary: REPORT_FIELDS.map((field) => ({
      field,
      status: results[field].pass ? "PASS" : "FAIL",
      details: results[field].details
    })),
    legal_urls: legalUrls,
    screenshots: evidence
  };

  const reportPath = path.join(reportDir, `saleads_mi_negocio_final_report_${runId}.json`);
  fs.writeFileSync(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf-8");

  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  for (const row of finalReport.summary) {
    console.log(`${row.status} - ${row.field}`);
    for (const detail of row.details) {
      console.log(`  • ${detail}`);
    }
  }

  const failedFields = REPORT_FIELDS.filter((field) => !results[field].pass);
  expect(
    failedFields,
    `Some workflow validations failed: ${failedFields.length ? failedFields.join(", ") : "none"}`
  ).toEqual([]);
});
