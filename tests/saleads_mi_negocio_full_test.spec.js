const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const TEST_NAME = "saleads_mi_negocio_full_test";
const ARTIFACT_DIR = path.join(process.cwd(), "artifacts", TEST_NAME);
const REPORT_FILE = path.join(ARTIFACT_DIR, "final-report.json");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

function ensureArtifactDir() {
  fs.mkdirSync(ARTIFACT_DIR, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 6000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function capture(page, name, fullPage = false) {
  const filePath = path.join(ARTIFACT_DIR, name);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function firstVisible(candidates, timeoutMs = 8000) {
  for (const locator of candidates) {
    try {
      await locator.first().waitFor({ state: "visible", timeout: timeoutMs });
      return locator.first();
    } catch (error) {
      // Try next candidate.
    }
  }
  return null;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

function createReportStore() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = {
      status: "FAIL",
      details: "Not executed.",
    };
  }
  return report;
}

test("SaleADS.ai Mi Negocio full workflow", async ({ page, context }) => {
  ensureArtifactDir();

  const report = createReportStore();
  const errors = [];
  const legalUrls = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null,
  };
  const startUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL || null;

  const failStep = async (stepName, error, failureScreenshotName) => {
    const detail = error instanceof Error ? error.message : String(error);
    report[stepName] = {
      status: "FAIL",
      details: detail,
    };
    errors.push({ step: stepName, error: detail });
    await capture(page, failureScreenshotName, true).catch(() => {});
  };

  // Step 1 - Login with Google
  try {
    if (page.url() === "about:blank") {
      if (!startUrl) {
        throw new Error(
          "Browser did not start on a login page. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL for this environment."
        );
      }
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const loginButton = await firstVisible(
      [
        page.getByRole("button", { name: /Sign in with Google|Iniciar con Google|Google/i }),
        page.getByRole("link", { name: /Sign in with Google|Iniciar con Google|Google/i }),
        page.getByText(/Sign in with Google|Iniciar con Google|Continuar con Google|Google/i),
      ],
      12000
    );

    if (!loginButton) {
      throw new Error("Could not locate the login button or Sign in with Google option.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const googlePage = await popupPromise;

    const expectedGoogleAccount = "juanlucasbarbiergarzon@gmail.com";
    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
      await googlePage.bringToFront();

      const accountOption = await firstVisible(
        [
          googlePage.getByText(expectedGoogleAccount, { exact: true }),
          googlePage.getByRole("button", { name: new RegExp(expectedGoogleAccount, "i") }),
          googlePage.getByRole("link", { name: new RegExp(expectedGoogleAccount, "i") }),
        ],
        8000
      );

      if (accountOption) {
        await accountOption.click();
        await waitForUi(googlePage);
      }

      await googlePage.waitForEvent("close", { timeout: 30000 }).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const accountOptionSamePage = await firstVisible(
        [
          page.getByText(expectedGoogleAccount, { exact: true }),
          page.getByRole("button", { name: new RegExp(expectedGoogleAccount, "i") }),
          page.getByRole("link", { name: new RegExp(expectedGoogleAccount, "i") }),
        ],
        6000
      );

      if (accountOptionSamePage) {
        await accountOptionSamePage.click();
        await waitForUi(page);
      }
    }

    const leftSidebar = await firstVisible(
      [
        page.locator("aside:visible"),
        page.getByRole("navigation"),
        page.locator("nav:visible"),
      ],
      20000
    );

    if (!leftSidebar) {
      throw new Error("Main app loaded but left sidebar navigation was not found.");
    }

    await capture(page, "step-1-dashboard-loaded.png", true);
    report["Login"] = {
      status: "PASS",
      details: "Main interface and left sidebar are visible after Google login.",
    };
  } catch (error) {
    await failStep("Login", error, "step-1-login-failed.png");
  }

  // Step 2 - Open Mi Negocio menu
  try {
    const negocioEntry = await firstVisible(
      [
        page.getByRole("button", { name: /Negocio/i }),
        page.getByRole("link", { name: /Negocio/i }),
        page.getByText(/^Negocio$/i),
      ],
      12000
    );
    if (!negocioEntry) {
      throw new Error("Negocio section was not found in sidebar.");
    }
    await clickAndWait(page, negocioEntry);

    const miNegocioEntry = await firstVisible(
      [
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ],
      12000
    );
    if (!miNegocioEntry) {
      throw new Error("Mi Negocio option was not found.");
    }
    await clickAndWait(page, miNegocioEntry);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();
    await capture(page, "step-2-mi-negocio-menu-expanded.png", true);

    report["Mi Negocio menu"] = {
      status: "PASS",
      details: "Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios visible.",
    };
  } catch (error) {
    await failStep("Mi Negocio menu", error, "step-2-mi-negocio-menu-failed.png");
  }

  // Step 3 - Validate Agregar Negocio modal
  try {
    const agregarNegocio = await firstVisible(
      [
        page.getByRole("button", { name: /Agregar Negocio/i }),
        page.getByRole("link", { name: /Agregar Negocio/i }),
        page.getByText(/Agregar Negocio/i),
      ],
      10000
    );
    if (!agregarNegocio) {
      throw new Error("Agregar Negocio option is not visible.");
    }
    await clickAndWait(page, agregarNegocio);

    const modalTitle = await firstVisible(
      [page.getByRole("heading", { name: /Crear Nuevo Negocio/i }), page.getByText(/Crear Nuevo Negocio/i)],
      12000
    );
    if (!modalTitle) {
      throw new Error("Crear Nuevo Negocio modal did not appear.");
    }

    await expect(page.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await capture(page, "step-3-agregar-negocio-modal.png", true);

    const businessNameField = await firstVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input").filter({ hasText: "" }),
      ],
      4000
    );
    if (businessNameField) {
      await businessNameField.click();
      await businessNameField.fill("Negocio Prueba Automatizacion");
    }
    await clickAndWait(page, page.getByRole("button", { name: /Cancelar/i }));

    report["Agregar Negocio modal"] = {
      status: "PASS",
      details: "Modal content validated and closed via Cancelar.",
    };
  } catch (error) {
    await failStep("Agregar Negocio modal", error, "step-3-agregar-negocio-modal-failed.png");
  }

  // Step 4 - Open Administrar Negocios
  try {
    const administrarNegocios = await firstVisible(
      [
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ],
      10000
    );

    if (!administrarNegocios) {
      const miNegocioToggle = await firstVisible(
        [page.getByRole("button", { name: /Mi Negocio/i }), page.getByText(/Mi Negocio/i)],
        5000
      );
      if (!miNegocioToggle) {
        throw new Error("Could not re-open Mi Negocio menu to access Administrar Negocios.");
      }
      await clickAndWait(page, miNegocioToggle);
    }

    const administrarAgain = await firstVisible(
      [
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ],
      10000
    );

    if (!administrarAgain) {
      throw new Error("Administrar Negocios option was not available.");
    }

    await clickAndWait(page, administrarAgain);
    await expect(page.getByText(/Informaci.n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci.n Legal|Legal/i)).toBeVisible();
    await capture(page, "step-4-administrar-negocios-view.png", true);

    report["Administrar Negocios view"] = {
      status: "PASS",
      details: "All required account sections are visible.",
    };
  } catch (error) {
    await failStep("Administrar Negocios view", error, "step-4-administrar-negocios-failed.png");
  }

  // Step 5 - Validate Informacion General
  try {
    await expect(page.getByText(/Informaci.n General/i)).toBeVisible();
    await expect(page.getByText(/juanlucasbarbiergarzon@gmail.com/i)).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const nameIndicators = [
      page.getByText(/Nombre|Name|Usuario/i),
      page.getByText(/Juan|Lucas|Barbier|Garzon/i),
      page.locator("h1, h2, h3").filter({ hasText: /Juan|Lucas|Barbier|Garzon/i }),
    ];
    const userNameVisible = await firstVisible(nameIndicators, 4000);
    if (!userNameVisible) {
      throw new Error("User name could not be validated in Informacion General.");
    }

    report["Informacion General"] = {
      status: "PASS",
      details: "User identity, email, BUSINESS PLAN, and Cambiar Plan are visible.",
    };
  } catch (error) {
    await failStep("Informacion General", error, "step-5-informacion-general-failed.png");
  }

  // Step 6 - Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo|Activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado|Idioma/i)).toBeVisible();
    report["Detalles de la Cuenta"] = {
      status: "PASS",
      details: "Cuenta creada, Estado activo, and Idioma seleccionado are visible.",
    };
  } catch (error) {
    await failStep("Detalles de la Cuenta", error, "step-6-detalles-cuenta-failed.png");
  }

  // Step 7 - Validate Tus Negocios
  try {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const listVisible = await firstVisible(
      [
        page.locator("ul li").first(),
        page.locator("table tbody tr").first(),
        page.locator('[class*="business"]').first(),
      ],
      7000
    );
    if (!listVisible) {
      throw new Error("Business list was not found in Tus Negocios section.");
    }

    report["Tus Negocios"] = {
      status: "PASS",
      details: "Business list, Agregar Negocio button, and usage text are visible.",
    };
  } catch (error) {
    await failStep("Tus Negocios", error, "step-7-tus-negocios-failed.png");
  }

  async function validateLegalLink({ reportName, linkPattern, headingPattern, screenshotName, legalKey }) {
    const appPage = page;
    const appUrl = appPage.url();

    const link = await firstVisible(
      [appPage.getByRole("link", { name: linkPattern }), appPage.getByText(linkPattern)],
      10000
    );
    if (!link) {
      throw new Error(`Legal link not found: ${linkPattern}`);
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(appPage, link);
    const popup = await popupPromise;
    const legalPage = popup || appPage;

    await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
    await waitForUi(legalPage);

    const legalHeading = await firstVisible(
      [
        legalPage.getByRole("heading", { name: headingPattern }),
        legalPage.getByText(headingPattern),
      ],
      15000
    );
    if (!legalHeading) {
      throw new Error(`Could not validate legal heading for ${reportName}.`);
    }

    const legalContentVisible = await firstVisible(
      [
        legalPage.getByText(/T.rminos|Condiciones|Privacidad|Uso|Datos|Pol.tica/i),
        legalPage.locator("main p").first(),
        legalPage.locator("article p").first(),
      ],
      8000
    );
    if (!legalContentVisible) {
      throw new Error(`Could not validate legal content for ${reportName}.`);
    }

    legalUrls[legalKey] = legalPage.url();
    await capture(legalPage, screenshotName, true);

    if (popup && !popup.isClosed()) {
      await popup.close();
      await appPage.bringToFront();
      await waitForUi(appPage);
    } else if (!popup) {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        if (appUrl) {
          await appPage.goto(appUrl, { waitUntil: "domcontentloaded" });
        }
      });
      await waitForUi(appPage);
    }
  }

  // Step 8 - Validate Terminos y Condiciones
  try {
    await validateLegalLink({
      reportName: "Terminos y Condiciones",
      linkPattern: /T.rminos y Condiciones/i,
      headingPattern: /T.rminos y Condiciones/i,
      screenshotName: "step-8-terminos-y-condiciones.png",
      legalKey: "terminosYCondiciones",
    });

    report["Terminos y Condiciones"] = {
      status: "PASS",
      details: "Legal page opened and heading/content validated.",
    };
  } catch (error) {
    await failStep("Terminos y Condiciones", error, "step-8-terminos-failed.png");
  }

  // Step 9 - Validate Politica de Privacidad
  try {
    await validateLegalLink({
      reportName: "Politica de Privacidad",
      linkPattern: /Pol.tica de Privacidad/i,
      headingPattern: /Pol.tica de Privacidad/i,
      screenshotName: "step-9-politica-privacidad.png",
      legalKey: "politicaDePrivacidad",
    });

    report["Politica de Privacidad"] = {
      status: "PASS",
      details: "Legal page opened and heading/content validated.",
    };
  } catch (error) {
    await failStep("Politica de Privacidad", error, "step-9-politica-failed.png");
  }

  // Step 10 - Final report
  const failedSteps = Object.entries(report)
    .filter(([, value]) => value.status !== "PASS")
    .map(([stepName]) => stepName);

  const finalReport = {
    testName: TEST_NAME,
    generatedAt: new Date().toISOString(),
    startUrl,
    results: report,
    legalUrls,
    overallStatus: failedSteps.length === 0 ? "PASS" : "FAIL",
    failedSteps,
    errors,
    evidenceDirectory: ARTIFACT_DIR,
  };

  fs.writeFileSync(REPORT_FILE, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  console.log(`Final report written to: ${REPORT_FILE}`);
  console.log(JSON.stringify(finalReport, null, 2));

  expect(
    failedSteps,
    `One or more workflow validations failed. See report at ${REPORT_FILE}`
  ).toEqual([]);
});
