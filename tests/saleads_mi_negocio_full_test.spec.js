const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

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
  "Política de Privacidad"
];

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function resolveVisibleLocator(page, candidates, description, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      try {
        if (await locator.isVisible()) {
          return locator;
        }
      } catch (error) {
        // Keep polling until timeout so the caller gets one consistent error.
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`Could not find visible element: ${description}`);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const fileName = `${name.toLowerCase().replace(/[^a-z0-9]+/g, "_")}.png`;
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function openLegalAndValidate({
  page,
  linkTextRegex,
  headingRegex,
  reportKey,
  testInfo,
  finalUrls
}) {
  const legalLink = await resolveVisibleLocator(
    page,
    [
      page.getByRole("link", { name: linkTextRegex }),
      page.getByRole("button", { name: linkTextRegex }),
      page.getByText(linkTextRegex)
    ],
    `link ${linkTextRegex}`
  );

  const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const previousUrl = page.url();
  await legalLink.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const targetPage = popup || page;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForTimeout(800);
  }

  const legalHeading = await resolveVisibleLocator(
    targetPage,
    [
      targetPage.getByRole("heading", { name: headingRegex }),
      targetPage.getByText(headingRegex)
    ],
    `legal heading ${headingRegex}`
  );
  await expect(legalHeading).toBeVisible();

  const legalText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(
    legalText.length,
    `${reportKey} did not show enough legal content text.`
  ).toBeGreaterThan(120);

  await captureCheckpoint(targetPage, testInfo, `${reportKey}_legal_page`, true);
  finalUrls[reportKey] = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== previousUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      if (previousUrl) {
        await page.goto(previousUrl, { waitUntil: "domcontentloaded" });
      }
    });
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((key) => [key, "NOT_RUN"]));
  const errorDetails = {};
  const finalUrls = {
    "Términos y Condiciones": "",
    "Política de Privacidad": ""
  };

  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.SALEADS_BASE_URL ||
    "";

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  const runStep = async (reportKey, action) => {
    try {
      await action();
      report[reportKey] = "PASS";
    } catch (error) {
      report[reportKey] = "FAIL";
      errorDetails[reportKey] = error.message;
      await captureCheckpoint(page, testInfo, `${reportKey}_failed`, true).catch(() => {});
    }
  };

  await runStep("Login", async () => {
    const loginButton = await resolveVisibleLocator(
      page,
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesión con google|continuar con google|google/i
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesión con google|continuar con google|google/i
        }),
        page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i)
      ],
      "Google login button"
    );

    await clickAndWait(page, loginButton);

    const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await clickAndWait(page, accountOption);
    }

    const sidebar = await resolveVisibleLocator(
      page,
      [
        page.locator("aside"),
        page.locator("nav"),
        page.getByRole("navigation"),
        page.getByText(/mi negocio|negocio/i)
      ],
      "main app sidebar/navigation"
    );
    await expect(sidebar).toBeVisible();

    await captureCheckpoint(page, testInfo, "step_1_dashboard_loaded", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const miNegocioOption = await resolveVisibleLocator(
      page,
      [
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      "Mi Negocio option"
    );
    await clickAndWait(page, miNegocioOption);

    const agregarNegocio = await resolveVisibleLocator(
      page,
      [
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio item"
    );
    const administrarNegocios = await resolveVisibleLocator(
      page,
      [
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      "Administrar Negocios item"
    );

    await expect(agregarNegocio).toBeVisible();
    await expect(administrarNegocios).toBeVisible();
    await captureCheckpoint(page, testInfo, "step_2_mi_negocio_menu_expanded", true);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await resolveVisibleLocator(
      page,
      [
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio action"
    );
    await clickAndWait(page, agregarNegocio);

    const modalTitle = await resolveVisibleLocator(
      page,
      [page.getByRole("heading", { name: /crear nuevo negocio/i }), page.getByText(/crear nuevo negocio/i)],
      "Crear Nuevo Negocio modal title"
    );
    await expect(modalTitle).toBeVisible();

    const nombreInput = await resolveVisibleLocator(
      page,
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator("input[name*='nombre' i]"),
        page.locator("input[id*='nombre' i]")
      ],
      "Nombre del Negocio input"
    );
    await expect(nombreInput).toBeVisible();

    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();
    await captureCheckpoint(page, testInfo, "step_3_agregar_negocio_modal", true);

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }));
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocioOption = await resolveVisibleLocator(
        page,
        [
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i)
        ],
        "Mi Negocio re-open option"
      );
      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegocios = await resolveVisibleLocator(
      page,
      [
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      "Administrar Negocios action"
    );
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/información general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/sección legal/i)).toBeVisible();
    await captureCheckpoint(page, testInfo, "step_4_administrar_negocios_page", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/información general/i)).toBeVisible();
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)).toBeVisible();
    await expect(
      page.getByText(/business plan/i, { exact: false }).or(page.getByRole("heading", { name: /business plan/i }))
    ).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const hasUserName =
      (await page.getByText(/nombre|name/i).first().isVisible().catch(() => false)) ||
      (await page.locator("[data-testid*='name' i], [class*='name' i]").first().isVisible().catch(() => false));
    expect(hasUserName, "Expected a visible user name indicator in Información General").toBeTruthy();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();

    const businessListVisible =
      (await page.locator("section, div").filter({ hasText: /tus negocios/i }).locator("li, article, tr, [role='row']").first().isVisible().catch(() => false)) ||
      (await page.locator("[class*='business' i], [class*='negocio' i]").first().isVisible().catch(() => false));
    expect(businessListVisible, "Expected business list/cards to be visible in Tus Negocios").toBeTruthy();
  });

  await runStep("Términos y Condiciones", async () => {
    await openLegalAndValidate({
      page,
      linkTextRegex: /términos y condiciones|terminos y condiciones/i,
      headingRegex: /términos y condiciones|terminos y condiciones/i,
      reportKey: "Términos y Condiciones",
      testInfo,
      finalUrls
    });
  });

  await runStep("Política de Privacidad", async () => {
    await openLegalAndValidate({
      page,
      linkTextRegex: /política de privacidad|politica de privacidad/i,
      headingRegex: /política de privacidad|politica de privacidad/i,
      reportKey: "Política de Privacidad",
      testInfo,
      finalUrls
    });
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    resultByField: report,
    legalFinalUrls: finalUrls,
    errors: errorDetails
  };

  await fs.writeFile(
    testInfo.outputPath("saleads_mi_negocio_final_report.json"),
    JSON.stringify(finalReport, null, 2),
    "utf8"
  );
  await testInfo.attach("saleads_mi_negocio_final_report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });

  const failedFields = REPORT_FIELDS.filter((field) => report[field] !== "PASS");
  expect(
    failedFields,
    `Validation failures: ${failedFields.join(", ") || "none"}\n` +
      `Final report:\n${JSON.stringify(finalReport, null, 2)}`
  ).toEqual([]);
});
