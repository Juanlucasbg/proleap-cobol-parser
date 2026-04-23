const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const startUrl =
    process.env.SALEADS_START_URL ||
    process.env.BASE_URL ||
    process.env.PLAYWRIGHT_TEST_BASE_URL ||
    "";

  const startedAt = new Date();
  const runId = startedAt.toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.resolve(__dirname, "..", "artifacts");
  const screenshotsDir = path.join(artifactsDir, "screenshots", runId);
  const reportsDir = path.join(artifactsDir, "reports");
  fs.mkdirSync(screenshotsDir, { recursive: true });
  fs.mkdirSync(reportsDir, { recursive: true });

  const report = {
    test_name: "saleads_mi_negocio_full_test",
    started_at: startedAt.toISOString(),
    final_url: null,
    legal_urls: {
      terminos_y_condiciones: null,
      politica_de_privacidad: null,
    },
    screenshots: [],
    errors: {},
    results: {
      Login: "FAIL",
      "Mi Negocio menu": "FAIL",
      "Agregar Negocio modal": "FAIL",
      "Administrar Negocios view": "FAIL",
      "Información General": "FAIL",
      "Detalles de la Cuenta": "FAIL",
      "Tus Negocios": "FAIL",
      "Términos y Condiciones": "FAIL",
      "Política de Privacidad": "FAIL",
    },
  };

  async function waitForUi(targetPage = page) {
    await targetPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await targetPage.waitForTimeout(800);
  }

  async function saveScreenshot(fileName, targetPage = page, fullPage = false) {
    const filePath = path.join(screenshotsDir, fileName);
    await targetPage.screenshot({ path: filePath, fullPage });
    report.screenshots.push(filePath);
  }

  async function firstVisible(candidates) {
    for (const locator of candidates) {
      const target = locator.first();
      if (await target.isVisible().catch(() => false)) {
        return target;
      }
    }
    return null;
  }

  async function getVisibleOrThrow(candidates, errorMessage) {
    const locator = await firstVisible(candidates);
    if (!locator) {
      throw new Error(errorMessage);
    }
    return locator;
  }

  async function clickAndWait(locator, targetPage = page) {
    await expect(locator).toBeVisible();
    await locator.click();
    await waitForUi(targetPage);
  }

  async function runValidationStep(label, fn) {
    try {
      await fn();
      report.results[label] = "PASS";
    } catch (error) {
      report.results[label] = "FAIL";
      report.errors[label] = error instanceof Error ? error.message : String(error);
    }
  }

  async function ensureInitialPage() {
    if (page.url() === "about:blank") {
      if (!startUrl) {
        throw new Error(
          "No start URL was provided. Set SALEADS_START_URL (or BASE_URL) to run this test in your current SaleADS environment.",
        );
      }
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);
  }

  async function ensureMiNegocioExpanded(sidebar) {
    const administrarVisible = await sidebar
      .getByRole("link", { name: /Administrar Negocios/i })
      .first()
      .isVisible()
      .catch(() => false);
    if (administrarVisible) {
      return;
    }

    const miNegocioToggle = await getVisibleOrThrow(
      [
        sidebar.getByRole("button", { name: /Mi Negocio/i }),
        sidebar.getByRole("link", { name: /Mi Negocio/i }),
        sidebar.getByText(/Mi Negocio/i),
      ],
      "Could not find 'Mi Negocio' in sidebar.",
    );
    await clickAndWait(miNegocioToggle);
  }

  async function openLegalPageAndReturn(linkText, headingText, screenshotName, reportKey) {
    const appPage = page;
    const appUrlBefore = appPage.url();
    const link = await getVisibleOrThrow(
      [appPage.getByRole("link", { name: linkText }), appPage.getByText(linkText)],
      `Could not find legal link '${linkText}'.`,
    );

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(link);
    const popup = await popupPromise;

    const targetPage = popup || appPage;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
      await waitForUi(popup);
    } else {
      await waitForUi(appPage);
    }

    await expect(targetPage.getByRole("heading", { name: headingText }).first()).toBeVisible({ timeout: 20000 });
    const visibleContent = await getVisibleOrThrow(
      [
        targetPage.locator("main p"),
        targetPage.locator("article p"),
        targetPage.locator("section p"),
        targetPage.locator("p"),
      ],
      `Could not confirm legal content on '${headingText}' page.`,
    );
    await expect(visibleContent).toBeVisible();

    await saveScreenshot(screenshotName, targetPage, true);
    report.legal_urls[reportKey] = targetPage.url();

    if (popup) {
      await popup.close().catch(() => {});
      await appPage.bringToFront();
      await waitForUi(appPage);
      return;
    }

    if (appPage.url() !== appUrlBefore) {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        await appPage.goto(appUrlBefore, { waitUntil: "domcontentloaded" }).catch(() => {});
      });
      await waitForUi(appPage);
    }
  }

  await ensureInitialPage();

  await runValidationStep("Login", async () => {
    const loginButton = await getVisibleOrThrow(
      [
        page.getByRole("button", { name: /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google/i }),
        page.getByRole("link", { name: /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google/i }),
        page.getByText(/Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google/i),
      ],
      "Could not find a Google login control.",
    );

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(loginButton);
    const loginPopup = await popupPromise;
    const loginFlowPage = loginPopup || page;

    if (loginPopup) {
      await loginPopup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
    }

    const accountOption = await firstVisible([
      loginFlowPage.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
      loginFlowPage.getByRole("link", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
      loginFlowPage.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
    ]);
    if (accountOption) {
      await clickAndWait(accountOption, loginFlowPage);
    }

    if (loginPopup) {
      await loginPopup.waitForClose({ timeout: 20000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUi(page);

    const mainAppSignal = await getVisibleOrThrow(
      [page.locator("aside"), page.getByRole("navigation"), page.getByText(/Negocio/i)],
      "Main application interface did not load after login.",
    );
    await expect(mainAppSignal).toBeVisible();
    await saveScreenshot("01-dashboard-loaded.png");
  });

  await runValidationStep("Mi Negocio menu", async () => {
    const sidebar = await getVisibleOrThrow([page.locator("aside"), page.getByRole("navigation")], "Sidebar not visible.");
    await expect(sidebar).toBeVisible();

    const negocioSection = await getVisibleOrThrow(
      [sidebar.getByText(/Negocio/i), page.getByText(/Negocio/i)],
      "Could not find 'Negocio' section.",
    );
    await expect(negocioSection).toBeVisible();

    const miNegocio = await getVisibleOrThrow(
      [
        sidebar.getByRole("button", { name: /Mi Negocio/i }),
        sidebar.getByRole("link", { name: /Mi Negocio/i }),
        sidebar.getByText(/Mi Negocio/i),
      ],
      "Could not find 'Mi Negocio' option.",
    );
    await clickAndWait(miNegocio);

    const agregarNegocio = await getVisibleOrThrow(
      [sidebar.getByRole("link", { name: /Agregar Negocio/i }), sidebar.getByText(/Agregar Negocio/i)],
      "'Agregar Negocio' not visible after expanding submenu.",
    );
    const administrarNegocios = await getVisibleOrThrow(
      [sidebar.getByRole("link", { name: /Administrar Negocios/i }), sidebar.getByText(/Administrar Negocios/i)],
      "'Administrar Negocios' not visible after expanding submenu.",
    );

    await expect(agregarNegocio).toBeVisible();
    await expect(administrarNegocios).toBeVisible();
    await saveScreenshot("02-mi-negocio-expanded.png");
  });

  await runValidationStep("Agregar Negocio modal", async () => {
    const agregarNegocioMenuItem = await getVisibleOrThrow(
      [page.getByRole("link", { name: /Agregar Negocio/i }), page.getByText(/Agregar Negocio/i)],
      "Could not find 'Agregar Negocio' submenu item.",
    );
    await clickAndWait(agregarNegocioMenuItem);

    const modalTitle = page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first();
    await expect(modalTitle).toBeVisible({ timeout: 20000 });
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const cancelButton = page.getByRole("button", { name: /Cancelar/i }).first();
    const createButton = page.getByRole("button", { name: /Crear Negocio/i }).first();
    await expect(cancelButton).toBeVisible();
    await expect(createButton).toBeVisible();

    const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");

    await saveScreenshot("03-agregar-negocio-modal.png");

    await clickAndWait(cancelButton);
    await expect(modalTitle).toBeHidden({ timeout: 15000 });
  });

  await runValidationStep("Administrar Negocios view", async () => {
    const sidebar = await getVisibleOrThrow([page.locator("aside"), page.getByRole("navigation")], "Sidebar not visible.");
    await ensureMiNegocioExpanded(sidebar);

    const administrarNegocios = await getVisibleOrThrow(
      [sidebar.getByRole("link", { name: /Administrar Negocios/i }), sidebar.getByText(/Administrar Negocios/i)],
      "Could not find 'Administrar Negocios'.",
    );
    await clickAndWait(administrarNegocios);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 20000 });

    await saveScreenshot("04-administrar-negocios-view.png", page, true);
  });

  await runValidationStep("Información General", async () => {
    const infoHeading = page.getByText(/Informaci[oó]n General/i).first();
    await expect(infoHeading).toBeVisible();

    const userEmail = await getVisibleOrThrow(
      [page.getByText(/@/i), page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i")],
      "User email is not visible in account page.",
    );
    await expect(userEmail).toBeVisible();

    const possibleUserName = await firstVisible([
      page.getByText(/juanlucasbarbiergarzon/i),
      page.getByText(/juan\\s*lucas/i),
      page.getByText(/barbier/i),
    ]);
    if (!possibleUserName) {
      throw new Error("User name is not clearly visible.");
    }
    await expect(possibleUserName).toBeVisible();

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runValidationStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runValidationStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const businessListSignal = await getVisibleOrThrow(
      [page.locator("table"), page.locator("ul"), page.locator("ol"), page.getByText(/Negocio/i)],
      "Business list is not visible.",
    );
    await expect(businessListSignal).toBeVisible();
  });

  await runValidationStep("Términos y Condiciones", async () => {
    await openLegalPageAndReturn(
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "05-terminos-y-condiciones.png",
      "terminos_y_condiciones",
    );
  });

  await runValidationStep("Política de Privacidad", async () => {
    await openLegalPageAndReturn(
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "06-politica-de-privacidad.png",
      "politica_de_privacidad",
    );
  });

  report.final_url = page.url();
  report.ended_at = new Date().toISOString();
  report.report_fields = [
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

  const reportPath = path.join(reportsDir, `saleads_mi_negocio_full_test_${runId}.json`);
  fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log("Final report:", JSON.stringify(report.results, null, 2));
  console.log(`Full report saved to ${reportPath}`);

  const failedSteps = Object.entries(report.results)
    .filter(([, status]) => status !== "PASS")
    .map(([label]) => label);

  expect(
    failedSteps,
    failedSteps.length ? `Failed validations: ${failedSteps.join(", ")}` : "All validations passed.",
  ).toEqual([]);
});
