const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

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

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const results = {};
  const legalUrls = {};
  let appPage = page;

  const checkpointDir = path.join(process.cwd(), "test-results", "checkpoints");
  fs.mkdirSync(checkpointDir, { recursive: true });

  const waitForUiLoad = async (targetPage) => {
    await targetPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await targetPage.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
    await targetPage.waitForTimeout(700);
  };

  const isVisible = async (locator, timeout = 3000) => {
    return locator.isVisible({ timeout }).catch(() => false);
  };

  const pickFirstVisible = async (locators, label) => {
    for (const locator of locators) {
      if (await isVisible(locator)) {
        return locator;
      }
    }
    throw new Error(`No visible element found for: ${label}`);
  };

  const clickAndWait = async (locator, targetPage = appPage) => {
    await locator.first().scrollIntoViewIfNeeded().catch(() => {});
    await locator.first().click();
    await waitForUiLoad(targetPage);
  };

  const captureCheckpoint = async (name, targetPage = appPage, fullPage = false) => {
    const fileName = `${name.replace(/[^a-z0-9]+/gi, "_").toLowerCase()}.png`;
    const filePath = path.join(checkpointDir, fileName);
    await targetPage.screenshot({ path: filePath, fullPage });
  };

  const runStep = async (field, fn) => {
    try {
      await fn();
      results[field] = { status: "PASS" };
    } catch (error) {
      results[field] = {
        status: "FAIL",
        details: error instanceof Error ? error.message : String(error)
      };
    }
  };

  const getAppPageFromContext = async () => {
    for (const ctxPage of context.pages()) {
      const url = ctxPage.url();
      if (url && !url.includes("accounts.google.com") && !url.startsWith("chrome-error://")) {
        return ctxPage;
      }
    }
    return appPage;
  };

  await runStep("Login", async () => {
    const startUrl = process.env.SALEADS_START_URL;
    if (startUrl) {
      await appPage.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(appPage);
    } else if (appPage.url() === "about:blank") {
      throw new Error(
        "Browser was not on a login page. Set SALEADS_START_URL or launch the test from an already-open SaleADS login page."
      );
    }

    const googleLoginButton = await pickFirstVisible(
      [
        appPage.getByRole("button", { name: /sign in with google/i }),
        appPage.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
        appPage.getByRole("button", { name: /continuar con google/i }),
        appPage.getByText(/sign in with google/i),
        appPage.getByText(/iniciar sesi[oó]n con google/i)
      ],
      "Google login button"
    );

    const popupPromise = appPage.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
    await clickAndWait(googleLoginButton, appPage);
    const popupPage = await popupPromise;

    if (popupPage) {
      await waitForUiLoad(popupPage);
      const googleAccountChoice = popupPage.getByText("juanlucasbarbiergarzon@gmail.com");
      if (await isVisible(googleAccountChoice, 5000)) {
        await clickAndWait(googleAccountChoice, popupPage);
      }
      await popupPage.waitForEvent("close", { timeout: 35000 }).catch(() => {});
    }

    appPage = await getAppPageFromContext();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);

    const sidebar = await pickFirstVisible(
      [appPage.locator("aside"), appPage.getByRole("navigation"), appPage.locator("nav")],
      "main sidebar"
    );
    await expect(sidebar).toBeVisible();
    await expect(appPage.getByText(/negocio/i).first()).toBeVisible();

    await captureCheckpoint("01_dashboard_loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    await expect(appPage.getByText(/negocio/i).first()).toBeVisible();

    const miNegocio = await pickFirstVisible(
      [
        appPage.getByRole("button", { name: /mi negocio/i }),
        appPage.getByRole("link", { name: /mi negocio/i }),
        appPage.getByText(/mi negocio/i)
      ],
      "Mi Negocio menu"
    );
    await clickAndWait(miNegocio);

    await expect(appPage.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/administrar negocios/i).first()).toBeVisible();

    await captureCheckpoint("02_mi_negocio_menu_expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocioMenu = await pickFirstVisible(
      [
        appPage.getByRole("button", { name: /agregar negocio/i }),
        appPage.getByRole("link", { name: /agregar negocio/i }),
        appPage.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio"
    );
    await clickAndWait(agregarNegocioMenu);

    const modal = appPage
      .getByRole("dialog")
      .filter({ has: appPage.getByText(/crear nuevo negocio/i) })
      .first();

    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(modal.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await captureCheckpoint("03_agregar_negocio_modal");

    const nombreInput = modal.getByLabel(/nombre del negocio/i);
    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    await clickAndWait(modal.getByRole("button", { name: /cancelar/i }), appPage);
    await expect(modal).toBeHidden();
  });

  await runStep("Administrar Negocios view", async () => {
    if (!(await isVisible(appPage.getByText(/administrar negocios/i).first()))) {
      const miNegocio = await pickFirstVisible(
        [
          appPage.getByRole("button", { name: /mi negocio/i }),
          appPage.getByRole("link", { name: /mi negocio/i }),
          appPage.getByText(/mi negocio/i)
        ],
        "Mi Negocio menu trigger"
      );
      await clickAndWait(miNegocio);
    }

    const administrarNegocios = await pickFirstVisible(
      [
        appPage.getByRole("button", { name: /administrar negocios/i }),
        appPage.getByRole("link", { name: /administrar negocios/i }),
        appPage.getByText(/administrar negocios/i)
      ],
      "Administrar Negocios"
    );
    await clickAndWait(administrarNegocios);

    await expect(appPage.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(appPage.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(appPage.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(appPage.getByText(/secci[oó]n legal/i).first()).toBeVisible();

    await captureCheckpoint("04_administrar_negocios_page", appPage, true);
  });

  await runStep("Información General", async () => {
    const infoSection = appPage.locator("section, div").filter({
      has: appPage.getByText(/informaci[oó]n general/i)
    });
    await expect(infoSection.getByText(/@/).first()).toBeVisible();
    await expect(infoSection.getByText(/business plan/i).first()).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(appPage.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(appPage.getByText(/estado activo/i).first()).toBeVisible();
    await expect(appPage.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const negociosSection = appPage.locator("section, div").filter({
      has: appPage.getByText(/tus negocios/i)
    });
    await expect(negociosSection).toBeVisible();
    await expect(negociosSection.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(negociosSection.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
  });

  const validateLegalLink = async (field, linkPattern, headingPattern, screenshotName) => {
    await runStep(field, async () => {
      const link = await pickFirstVisible(
        [
          appPage.getByRole("link", { name: linkPattern }),
          appPage.getByRole("button", { name: linkPattern }),
          appPage.getByText(linkPattern)
        ],
        `Legal link ${field}`
      );

      const previousUrl = appPage.url();
      const newPagePromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
      await clickAndWait(link, appPage);
      let legalPage = await newPagePromise;

      if (legalPage) {
        await legalPage.bringToFront();
        await waitForUiLoad(legalPage);
      } else {
        legalPage = appPage;
        await waitForUiLoad(legalPage);
      }

      const headingLocator = legalPage.getByRole("heading", { name: headingPattern });
      if (await isVisible(headingLocator, 7000)) {
        await expect(headingLocator.first()).toBeVisible();
      } else {
        await expect(legalPage.getByText(headingPattern).first()).toBeVisible();
      }

      const legalBody = (await legalPage.locator("body").innerText()).trim();
      if (legalBody.length < 200) {
        throw new Error(`Legal content was too short for ${field}.`);
      }

      legalUrls[field] = legalPage.url();
      await captureCheckpoint(screenshotName, legalPage, true);

      if (legalPage !== appPage) {
        await legalPage.close();
        await appPage.bringToFront();
      } else if (legalPage.url() !== previousUrl) {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUiLoad(appPage);
      }
    });
  };

  await validateLegalLink(
    "Términos y Condiciones",
    /t[eé]rminos y condiciones/i,
    /t[eé]rminos y condiciones/i,
    "05_terminos_y_condiciones"
  );

  await validateLegalLink(
    "Política de Privacidad",
    /pol[ií]tica de privacidad/i,
    /pol[ií]tica de privacidad/i,
    "06_politica_de_privacidad"
  );

  for (const field of REPORT_FIELDS) {
    if (!results[field]) {
      results[field] = { status: "FAIL", details: "Step was not executed." };
    }
  }

  const reportPayload = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results,
    legalUrls
  };

  const reportPath = path.join(process.cwd(), "test-results", "saleads_mi_negocio_report.json");
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, JSON.stringify(reportPayload, null, 2), "utf-8");

  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedFields = REPORT_FIELDS.filter((field) => results[field].status === "FAIL");
  expect(
    failedFields,
    `One or more validations failed. See ${reportPath} for details.`
  ).toHaveLength(0);
});
