const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const SCREENSHOT_DIR = path.join(
  process.cwd(),
  "artifacts",
  "saleads_mi_negocio_full_test",
);
const REPORT_PATH = path.join(SCREENSHOT_DIR, "final-report.json");
const DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function ensureArtifactsDir() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function safeErrorMessage(error) {
  if (!error) {
    return "Unknown error";
  }

  if (typeof error === "string") {
    return error;
  }

  if (error.message) {
    return error.message;
  }

  return JSON.stringify(error);
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 30000 });
  await page.waitForTimeout(900);
}

async function waitForAnyVisible(page, factories, description, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() <= deadline) {
    for (const factory of factories) {
      const locator = factory().first();
      const isVisible = await locator.isVisible().catch(() => false);

      if (isVisible) {
        return locator;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Element not visible: ${description}`);
}

async function clickAndWait(page, locator, clickDescription) {
  await locator.click();
  await waitForUiLoad(page);
  // Extra short buffer for animated sidebars/modals.
  await page.waitForTimeout(300);
}

async function takeCheckpoint(page, fileName, fullPage = false) {
  ensureArtifactsDir();
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, fileName),
    fullPage,
  });
}

async function openLegalLink(appPage, context, linkLocator) {
  const appUrlBeforeClick = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await linkLocator.click();
  const popupPage = await popupPromise;

  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded", { timeout: 30000 });
    await popupPage.waitForTimeout(900);

    return {
      legalPage: popupPage,
      openedNewTab: true,
      appUrlBeforeClick,
    };
  }

  await waitForUiLoad(appPage);
  return {
    legalPage: appPage,
    openedNewTab: false,
    appUrlBeforeClick,
  };
}

async function validateLegalPage(legalPage, headingRegex) {
  const heading = await waitForAnyVisible(
    legalPage,
    [
      () => legalPage.getByRole("heading", { name: headingRegex }),
      () => legalPage.getByText(headingRegex),
    ],
    `Legal heading ${headingRegex}`,
    20000,
  );

  await expect(heading).toBeVisible();

  const bodyText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();

  if (bodyText.length < 200) {
    throw new Error("Legal content text is too short to be considered valid.");
  }
}

async function returnToApplicationTab(appPage, legalPage, openedNewTab, appUrlBeforeClick) {
  if (openedNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
    return;
  }

  if (appPage.url() !== appUrlBeforeClick) {
    await appPage
      .goBack({ waitUntil: "domcontentloaded" })
      .catch(async () => appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" }));
    await waitForUiLoad(appPage);
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context }) => {
    ensureArtifactsDir();

    const report = {
      Login: { status: "FAIL", details: "Not executed" },
      "Mi Negocio menu": { status: "FAIL", details: "Not executed" },
      "Agregar Negocio modal": { status: "FAIL", details: "Not executed" },
      "Administrar Negocios view": { status: "FAIL", details: "Not executed" },
      "Información General": { status: "FAIL", details: "Not executed" },
      "Detalles de la Cuenta": { status: "FAIL", details: "Not executed" },
      "Tus Negocios": { status: "FAIL", details: "Not executed" },
      "Términos y Condiciones": { status: "FAIL", details: "Not executed" },
      "Política de Privacidad": { status: "FAIL", details: "Not executed" },
    };

    const evidence = {
      screenshots: {},
      legalUrls: {},
    };
    const failures = [];
    const googleEmail = process.env.SALEADS_GOOGLE_EMAIL || DEFAULT_GOOGLE_EMAIL;
    const loginUrl =
      process.env.SALEADS_LOGIN_URL ||
      process.env.SALEADS_BASE_URL ||
      process.env.TARGET_URL ||
      "";

    const runStep = async (fieldName, stepFn, failureScreenshotName) => {
      try {
        await stepFn();
        report[fieldName] = { status: "PASS" };
      } catch (error) {
        report[fieldName] = { status: "FAIL", details: safeErrorMessage(error) };
        failures.push(`${fieldName}: ${safeErrorMessage(error)}`);

        try {
          await takeCheckpoint(page, failureScreenshotName, true);
          evidence.screenshots[`${fieldName} failure`] = failureScreenshotName;
        } catch (_screenshotError) {
          // No-op: preserve original failure reason.
        }
      }
    };

    if (!loginUrl) {
      throw new Error(
        "Set SALEADS_LOGIN_URL, SALEADS_BASE_URL, or TARGET_URL so the test can open the current environment login page.",
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);

    await runStep(
      "Login",
      async () => {
        const sidebarLocators = [
          () => page.locator("aside"),
          () => page.getByRole("navigation"),
          () => page.locator("nav"),
        ];
        let alreadyLoggedIn = false;

        for (const factory of sidebarLocators) {
          const isVisible = await factory().first().isVisible().catch(() => false);
          if (isVisible) {
            alreadyLoggedIn = true;
            break;
          }
        }

        if (!alreadyLoggedIn) {
          const loginButton = await waitForAnyVisible(
            page,
            [
              () => page.getByRole("button", { name: /Sign in with Google/i }),
              () => page.getByRole("button", { name: /Iniciar sesi[oó]n con Google/i }),
              () => page.getByRole("button", { name: /Continuar con Google/i }),
              () => page.getByText(/Sign in with Google/i),
              () => page.getByText(/Iniciar sesi[oó]n con Google/i),
              () => page.getByText(/Continuar con Google/i),
            ],
            "Google login button",
            20000,
          );

          const googlePopupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
          await clickAndWait(page, loginButton, "Google login click");
          const googlePopup = await googlePopupPromise;

          if (googlePopup) {
            await googlePopup.waitForLoadState("domcontentloaded", { timeout: 30000 });
            const accountLocator = await waitForAnyVisible(
              googlePopup,
              [
                () => googlePopup.getByRole("button", { name: new RegExp(googleEmail, "i") }),
                () => googlePopup.getByText(googleEmail),
              ],
              `Google account ${googleEmail}`,
              12000,
            ).catch(() => null);

            if (accountLocator) {
              await accountLocator.click();
              await googlePopup.waitForTimeout(1200);
            }
          }
        }

        await waitForAnyVisible(
          page,
          [
            () => page.locator("aside"),
            () => page.getByRole("navigation"),
            () => page.locator("nav"),
          ],
          "main application sidebar",
          30000,
        );

        await takeCheckpoint(page, "01_dashboard_loaded.png", true);
        evidence.screenshots.dashboard = "01_dashboard_loaded.png";
      },
      "01_login_failure.png",
    );

    await runStep(
      "Mi Negocio menu",
      async () => {
        const negocioSection = await waitForAnyVisible(
          page,
          [
            () => page.getByRole("button", { name: /^Negocio$/i }),
            () => page.getByText(/^Negocio$/i),
          ],
          "Negocio menu section",
        );
        await clickAndWait(page, negocioSection, "Negocio section click");

        const miNegocioOption = await waitForAnyVisible(
          page,
          [
            () => page.getByRole("button", { name: /Mi Negocio/i }),
            () => page.getByText(/Mi Negocio/i),
          ],
          "Mi Negocio option",
        );
        await clickAndWait(page, miNegocioOption, "Mi Negocio click");

        await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
        await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

        await takeCheckpoint(page, "02_mi_negocio_menu_expanded.png", false);
        evidence.screenshots.miNegocioMenu = "02_mi_negocio_menu_expanded.png";
      },
      "02_mi_negocio_menu_failure.png",
    );

    await runStep(
      "Agregar Negocio modal",
      async () => {
        const agregarNegocio = await waitForAnyVisible(
          page,
          [
            () => page.getByRole("button", { name: /^Agregar Negocio$/i }),
            () => page.getByText(/^Agregar Negocio$/i),
          ],
          "Agregar Negocio menu item",
        );
        await clickAndWait(page, agregarNegocio, "Agregar Negocio click");

        const modalTitle = await waitForAnyVisible(
          page,
          [
            () => page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
            () => page.getByText(/Crear Nuevo Negocio/i),
          ],
          "Crear Nuevo Negocio modal title",
        );
        await expect(modalTitle).toBeVisible();
        await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
        await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
        await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
        await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

        await takeCheckpoint(page, "03_agregar_negocio_modal.png", false);
        evidence.screenshots.agregarNegocioModal = "03_agregar_negocio_modal.png";

        const nombreInput = page.getByLabel(/Nombre del Negocio/i);
        await nombreInput.click();
        await nombreInput.fill("Negocio Prueba Automatizacion");
        await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }), "Cancelar modal");
      },
      "03_agregar_negocio_modal_failure.png",
    );

    await runStep(
      "Administrar Negocios view",
      async () => {
        const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);

        if (!administrarVisible) {
          const miNegocioOption = await waitForAnyVisible(
            page,
            [
              () => page.getByRole("button", { name: /Mi Negocio/i }),
              () => page.getByText(/Mi Negocio/i),
            ],
            "Mi Negocio option (re-expand)",
          );
          await clickAndWait(page, miNegocioOption, "Mi Negocio re-expand");
        }

        const administrarNegocios = await waitForAnyVisible(
          page,
          [
            () => page.getByRole("button", { name: /Administrar Negocios/i }),
            () => page.getByText(/Administrar Negocios/i),
          ],
          "Administrar Negocios option",
        );
        await clickAndWait(page, administrarNegocios, "Administrar Negocios click");

        await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
        await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
        await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
        await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

        await takeCheckpoint(page, "04_administrar_negocios_full_page.png", true);
        evidence.screenshots.administrarNegocios = "04_administrar_negocios_full_page.png";
      },
      "04_administrar_negocios_failure.png",
    );

    await runStep(
      "Información General",
      async () => {
        const pageText = (await page.locator("body").innerText()).replace(/\s+/g, " ");

        if (!/\S+@\S+\.\S+/.test(pageText)) {
          throw new Error("User email is not visible.");
        }

        const likelyNameRegex = /\b[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\b/;
        if (!likelyNameRegex.test(pageText.replace(googleEmail, ""))) {
          throw new Error("User name is not clearly visible.");
        }

        await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
        await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
      },
      "05_informacion_general_failure.png",
    );

    await runStep(
      "Detalles de la Cuenta",
      async () => {
        await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
        await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
        await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
      },
      "06_detalles_cuenta_failure.png",
    );

    await runStep(
      "Tus Negocios",
      async () => {
        await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
        await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
        await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

        const businessAreaVisible = await waitForAnyVisible(
          page,
          [
            () => page.locator('[role="list"] [role="listitem"]'),
            () => page.locator("table tbody tr"),
            () => page.locator('[class*="business"], [class*="negocio"]'),
          ],
          "business list or cards",
          12000,
        ).then(() => true).catch(() => false);

        if (!businessAreaVisible) {
          throw new Error("Business list area is not visible.");
        }
      },
      "07_tus_negocios_failure.png",
    );

    await runStep(
      "Términos y Condiciones",
      async () => {
        const termsLink = await waitForAnyVisible(
          page,
          [
            () => page.getByRole("link", { name: /T[eé]rminos y Condiciones/i }),
            () => page.getByText(/T[eé]rminos y Condiciones/i),
          ],
          "Términos y Condiciones link",
        );

        const { legalPage, openedNewTab, appUrlBeforeClick } = await openLegalLink(page, context, termsLink);
        await validateLegalPage(legalPage, /T[eé]rminos y Condiciones/i);

        evidence.legalUrls.terms = legalPage.url();
        await takeCheckpoint(legalPage, "08_terminos_y_condiciones.png", true);
        evidence.screenshots.terminos = "08_terminos_y_condiciones.png";

        await returnToApplicationTab(page, legalPage, openedNewTab, appUrlBeforeClick);
      },
      "08_terminos_failure.png",
    );

    await runStep(
      "Política de Privacidad",
      async () => {
        const privacyLink = await waitForAnyVisible(
          page,
          [
            () => page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }),
            () => page.getByText(/Pol[ií]tica de Privacidad/i),
          ],
          "Política de Privacidad link",
        );

        const { legalPage, openedNewTab, appUrlBeforeClick } = await openLegalLink(page, context, privacyLink);
        await validateLegalPage(legalPage, /Pol[ií]tica de Privacidad/i);

        evidence.legalUrls.privacy = legalPage.url();
        await takeCheckpoint(legalPage, "09_politica_de_privacidad.png", true);
        evidence.screenshots.politica = "09_politica_de_privacidad.png";

        await returnToApplicationTab(page, legalPage, openedNewTab, appUrlBeforeClick);
      },
      "09_politica_failure.png",
    );

    fs.writeFileSync(
      REPORT_PATH,
      JSON.stringify(
        {
          testName: "saleads_mi_negocio_full_test",
          generatedAt: new Date().toISOString(),
          environmentUrl: loginUrl,
          accountUsed: googleEmail,
          report,
          evidence,
        },
        null,
        2,
      ),
    );

    // eslint-disable-next-line no-console
    console.log(`Final report written to: ${REPORT_PATH}`);
    // eslint-disable-next-line no-console
    console.log(JSON.stringify(report, null, 2));

    expect(
      failures,
      `One or more workflow validations failed.\n${failures.join("\n")}`,
    ).toHaveLength(0);
  });
});
