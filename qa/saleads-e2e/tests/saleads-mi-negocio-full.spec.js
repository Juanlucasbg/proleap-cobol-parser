const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

async function waitForStableUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => {});
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const screenshotPath = testInfo.outputPath(`${Date.now()}-${name.replace(/\s+/g, "-")}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function findVisibleLocator(page, candidates, timeoutMs, description) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      const visible = await locator.isVisible().catch(() => false);
      if (visible) {
        return locator;
      }
    }
    await page.waitForTimeout(300);
  }

  throw new Error(`Unable to find visible element for: ${description}`);
}

async function clickAndWait(page, locator, description, waitNetwork = true) {
  await expect(locator, `Element should be visible before clicking: ${description}`).toBeVisible({
    timeout: 20_000,
  });
  await locator.click();
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  if (waitNetwork) {
    await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => {});
  }
}

async function openLegalDocument({
  appPage,
  context,
  testInfo,
  linkText,
  headingRegex,
  screenshotName,
}) {
  const link = await findVisibleLocator(
    appPage,
    [
      appPage.getByRole("link", { name: new RegExp(linkText, "i") }),
      appPage.getByText(new RegExp(linkText, "i")),
    ],
    20_000,
    `Legal link ${linkText}`
  );

  const newTabPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await link.click();
  await appPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});

  const maybeNewTab = await newTabPromise;
  const legalPage = maybeNewTab || appPage;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await waitForStableUi(legalPage);

  await findVisibleLocator(
    legalPage,
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex),
    ],
    20_000,
    `Heading ${headingRegex}`
  );

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);

  const legalUrl = legalPage.url();

  if (maybeNewTab) {
    await maybeNewTab.close().catch(() => {});
    await appPage.bringToFront();
    await waitForStableUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForStableUi(appPage);
  }

  return legalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context, baseURL }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  const legalUrls = {};

  const configuredLoginUrl =
    process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL || baseURL;

  if (page.url() === "about:blank") {
    if (!configuredLoginUrl) {
      throw new Error(
        "No SALEADS_LOGIN_URL/SALEADS_BASE_URL/BASE_URL was provided and no preloaded page exists."
      );
    }
    await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
  }
  await waitForStableUi(page);

  // Step 1: Login with Google
  try {
    const loginButton = await findVisibleLocator(
      page,
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|ingresar con google|continuar con google/i,
        }),
        page.getByText(
          /sign in with google|iniciar sesi[oó]n con google|ingresar con google|continuar con google/i
        ),
      ],
      8_000,
      "Google login button"
    ).catch(() => null);

    if (loginButton) {
      const authPopupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
      await clickAndWait(page, loginButton, "Google login button", false);
      const authPopup = await authPopupPromise;
      const authPage = authPopup || page;

      await waitForStableUi(authPage);

      const accountOption = await findVisibleLocator(
        authPage,
        [
          authPage.getByText(ACCOUNT_EMAIL, { exact: true }),
          authPage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
          authPage.getByText(new RegExp(ACCOUNT_EMAIL, "i")),
        ],
        8_000,
        "Google account option"
      ).catch(() => null);

      if (accountOption) {
        await clickAndWait(authPage, accountOption, "Google account option", false);
      }

      if (authPopup) {
        await authPopup.waitForEvent("close", { timeout: 25_000 }).catch(() => {});
        await page.bringToFront();
      }
    }

    await waitForStableUi(page);

    await findVisibleLocator(
      page,
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator('[class*="sidebar"]'),
      ],
      25_000,
      "Main interface/left sidebar"
    );
    await findVisibleLocator(
      page,
      [page.getByText(/negocio|mi negocio|dashboard/i), page.getByRole("link", { name: /mi negocio|dashboard/i })],
      25_000,
      "Main app content"
    );

    report["Login"] = "PASS";
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded", true);
  } catch (error) {
    failures.push(`Login: ${error.message}`);
  }

  // Step 2: Open Mi Negocio menu
  try {
    const negocioSection = await findVisibleLocator(
      page,
      [
        page.getByRole("button", { name: /negocio/i }),
        page.getByText(/^negocio$/i),
        page.getByText(/negocio/i),
      ],
      20_000,
      "Negocio section"
    ).catch(() => null);

    if (negocioSection) {
      await clickAndWait(page, negocioSection, "Negocio section");
    }

    const miNegocio = await findVisibleLocator(
      page,
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ],
      20_000,
      "Mi Negocio option"
    );
    await clickAndWait(page, miNegocio, "Mi Negocio option");

    await findVisibleLocator(
      page,
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      20_000,
      "Agregar Negocio option"
    );
    await findVisibleLocator(
      page,
      [page.getByRole("button", { name: /administrar negocios/i }), page.getByText(/administrar negocios/i)],
      20_000,
      "Administrar Negocios option"
    );

    report["Mi Negocio menu"] = "PASS";
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded-menu", false);
  } catch (error) {
    failures.push(`Mi Negocio menu: ${error.message}`);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const agregarNegocio = await findVisibleLocator(
      page,
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      20_000,
      "Agregar Negocio trigger"
    );
    await clickAndWait(page, agregarNegocio, "Agregar Negocio trigger", false);

    await findVisibleLocator(
      page,
      [page.getByRole("heading", { name: /crear nuevo negocio/i }), page.getByText(/crear nuevo negocio/i)],
      20_000,
      "Crear Nuevo Negocio title"
    );
    const businessNameField = await findVisibleLocator(
      page,
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator('input[name*="nombre" i]'),
        page.locator('input[id*="nombre" i]'),
      ],
      20_000,
      "Nombre del Negocio input"
    );
    await findVisibleLocator(
      page,
      [page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)],
      20_000,
      "Business quota text"
    );
    const cancelButton = await findVisibleLocator(
      page,
      [page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)],
      20_000,
      "Cancelar button"
    );
    await findVisibleLocator(
      page,
      [page.getByRole("button", { name: /crear negocio/i }), page.getByText(/crear negocio/i)],
      20_000,
      "Crear Negocio button"
    );

    await captureCheckpoint(page, testInfo, "03-crear-nuevo-negocio-modal", false);

    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatización");
    await clickAndWait(page, cancelButton, "Cancelar modal button", false);

    report["Agregar Negocio modal"] = "PASS";
  } catch (error) {
    failures.push(`Agregar Negocio modal: ${error.message}`);
  }

  // Step 4: Open Administrar Negocios
  try {
    const administrarNegocios = await findVisibleLocator(
      page,
      [page.getByRole("button", { name: /administrar negocios/i }), page.getByText(/administrar negocios/i)],
      20_000,
      "Administrar Negocios option"
    ).catch(async () => {
      const miNegocio = await findVisibleLocator(
        page,
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        20_000,
        "Mi Negocio option for re-expand"
      );
      await clickAndWait(page, miNegocio, "Mi Negocio option for re-expand");
      return findVisibleLocator(
        page,
        [page.getByRole("button", { name: /administrar negocios/i }), page.getByText(/administrar negocios/i)],
        20_000,
        "Administrar Negocios option after re-expand"
      );
    });

    await clickAndWait(page, administrarNegocios, "Administrar Negocios option");

    await findVisibleLocator(
      page,
      [page.getByRole("heading", { name: /información general/i }), page.getByText(/información general/i)],
      25_000,
      "Información General section"
    );
    await findVisibleLocator(
      page,
      [page.getByRole("heading", { name: /detalles de la cuenta/i }), page.getByText(/detalles de la cuenta/i)],
      25_000,
      "Detalles de la Cuenta section"
    );
    await findVisibleLocator(
      page,
      [page.getByRole("heading", { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      25_000,
      "Tus Negocios section"
    );
    await findVisibleLocator(
      page,
      [page.getByRole("heading", { name: /sección legal/i }), page.getByText(/sección legal/i)],
      25_000,
      "Sección Legal section"
    );

    report["Administrar Negocios view"] = "PASS";
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-account-page-full", true);
  } catch (error) {
    failures.push(`Administrar Negocios view: ${error.message}`);
  }

  // Step 5: Validate Información General
  try {
    await findVisibleLocator(
      page,
      [page.getByText(/nombre/i), page.getByText(/usuario/i), page.getByText(/[A-Za-zÁÉÍÓÚÑ]{3,}\s+[A-Za-zÁÉÍÓÚÑ]{3,}/)],
      20_000,
      "User name in Información General"
    );
    await findVisibleLocator(
      page,
      [
        page.locator("section").filter({ hasText: /información general/i }).getByText(/@/),
        page.getByText(/@/),
      ],
      20_000,
      "User email in Información General"
    );
    await findVisibleLocator(
      page,
      [page.getByText(/business plan/i), page.getByText(/plan/i)],
      20_000,
      "BUSINESS PLAN text"
    );
    await findVisibleLocator(
      page,
      [page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)],
      20_000,
      "Cambiar Plan button"
    );

    report["Información General"] = "PASS";
  } catch (error) {
    failures.push(`Información General: ${error.message}`);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await findVisibleLocator(page, [page.getByText(/cuenta creada/i)], 20_000, "Cuenta creada label");
    await findVisibleLocator(page, [page.getByText(/estado/i)], 20_000, "Estado label");
    await findVisibleLocator(page, [page.getByText(/activo/i)], 20_000, "Activo value");
    await findVisibleLocator(
      page,
      [page.getByText(/idioma seleccionado/i), page.getByText(/idioma/i)],
      20_000,
      "Idioma seleccionado label"
    );

    report["Detalles de la Cuenta"] = "PASS";
  } catch (error) {
    failures.push(`Detalles de la Cuenta: ${error.message}`);
  }

  // Step 7: Validate Tus Negocios
  try {
    const tusNegociosSection = await findVisibleLocator(
      page,
      [page.getByRole("heading", { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      20_000,
      "Tus Negocios section title"
    );
    await findVisibleLocator(
      page,
      [
        page.locator("section,div").filter({ has: tusNegociosSection }).locator("li, tr, [role='row']"),
        page.locator("section,div").filter({ hasText: /tus negocios/i }).locator("li, tr, [role='row']"),
      ],
      20_000,
      "Business list rows"
    ).catch(() => null);
    await findVisibleLocator(
      page,
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      20_000,
      "Agregar Negocio button in Tus Negocios"
    );
    await findVisibleLocator(
      page,
      [page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)],
      20_000,
      "Business quota text in Tus Negocios"
    );

    report["Tus Negocios"] = "PASS";
  } catch (error) {
    failures.push(`Tus Negocios: ${error.message}`);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    legalUrls["Términos y Condiciones"] = await openLegalDocument({
      appPage: page,
      context,
      testInfo,
      linkText: "Términos y Condiciones",
      headingRegex: /t[eé]rminos y condiciones/i,
      screenshotName: "08-terminos-y-condiciones",
    });
    report["Términos y Condiciones"] = "PASS";
  } catch (error) {
    failures.push(`Términos y Condiciones: ${error.message}`);
  }

  // Step 9: Validate Política de Privacidad
  try {
    legalUrls["Política de Privacidad"] = await openLegalDocument({
      appPage: page,
      context,
      testInfo,
      linkText: "Política de Privacidad",
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: "09-politica-de-privacidad",
    });
    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    failures.push(`Política de Privacidad: ${error.message}`);
  }

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    statusByStep: report,
    legalUrls,
    generatedAt: new Date().toISOString(),
    screenshotEvidence: "Attached to Playwright report + test artifacts",
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log("SaleADS Mi Negocio final report");
  console.table(report);
  if (Object.keys(legalUrls).length) {
    console.log("Legal URLs:", legalUrls);
  }

  expect(failures, `Workflow validation failures:\n${failures.join("\n")}`).toEqual([]);
});
