const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const STEP_KEYS = [
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

function buildReport() {
  return Object.fromEntries(
    STEP_KEYS.map((key) => [key, { status: "FAIL", details: [] }]),
  );
}

function formatError(error) {
  if (!error) return "Unknown error";
  if (error instanceof Error) return error.message;
  return String(error);
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch {
    // Some views keep background requests alive; continue after DOM is ready.
  }
  await page.waitForTimeout(300);
}

async function pickFirstVisible(locators, timeoutMs = 15000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of locators) {
      try {
        if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
          return locator.first();
        }
      } catch {
        // Keep trying other candidates and retry until timeout.
      }
    }
    await locators[0].page().waitForTimeout(200);
  }

  throw new Error("Could not find a visible element from the provided selectors.");
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = true) {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function locateSectionByHeading(page, headingPattern) {
  const heading = page.getByRole("heading", { name: headingPattern }).first();
  await expect(heading).toBeVisible();

  const section = heading.locator("xpath=ancestor::section[1]");
  if ((await section.count()) > 0) {
    return section.first();
  }

  return heading.locator("xpath=ancestor::div[1]").first();
}

async function maybeChooseGoogleAccount(authPage) {
  const accountCandidateLocators = [
    authPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
    authPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
  ];

  for (const candidate of accountCandidateLocators) {
    try {
      if ((await candidate.count()) > 0 && (await candidate.first().isVisible())) {
        await candidate.first().click();
        await waitForUiToSettle(authPage);
        return true;
      }
    } catch {
      // Continue with next candidate.
    }
  }

  return false;
}

async function openLegalDocument({
  page,
  context,
  legalSection,
  linkPattern,
  headingPattern,
  screenshotFileName,
  testInfo,
}) {
  const link = await pickFirstVisible([
    legalSection.getByRole("link", { name: linkPattern }),
    legalSection.getByRole("button", { name: linkPattern }),
    legalSection.getByText(linkPattern),
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await link.click();
  await waitForUiToSettle(page);

  const popup = await popupPromise;
  const legalPage = popup || page;
  await waitForUiToSettle(legalPage);

  const heading = await pickFirstVisible(
    [
      legalPage.getByRole("heading", { name: headingPattern }),
      legalPage.getByText(headingPattern),
    ],
    20000,
  );
  await expect(heading).toBeVisible();

  const legalBody = legalPage.locator("main, article, body").first();
  const legalText = (await legalBody.innerText()).trim();
  if (legalText.length < 40) {
    throw new Error("Legal content appears empty or too short.");
  }

  const screenshotPath = await captureCheckpoint(
    legalPage,
    testInfo,
    screenshotFileName,
    true,
  );
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToSettle(page);
  }

  return { finalUrl, screenshotPath };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = buildReport();
  const failures = [];

  const markPass = (step, detail, extra = {}) => {
    report[step] = { status: "PASS", details: [detail], ...extra };
  };

  const markFail = (step, error) => {
    const message = formatError(error);
    report[step] = { status: "FAIL", details: [message] };
    failures.push(`${step}: ${message}`);
  };

  // Step 1: Login with Google
  try {
    const startUrl =
      process.env.SALEADS_START_URL ||
      process.env.PLAYWRIGHT_BASE_URL ||
      process.env.BASE_URL;

    if (page.url() === "about:blank") {
      if (!startUrl) {
        throw new Error(
          "Browser started on about:blank. Provide SALEADS_START_URL (or BASE_URL/PLAYWRIGHT_BASE_URL) for the login page.",
        );
      }
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }

    const loginButton = await pickFirstVisible([
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|inicia sesi[oó]n con google|google/i,
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|inicia sesi[oó]n con google|google/i,
      }),
      page.getByText(
        /sign in with google|iniciar sesi[oó]n con google|inicia sesi[oó]n con google/i,
      ),
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await loginButton.click();
    await waitForUiToSettle(page);
    const authPopup = await popupPromise;

    if (authPopup) {
      await waitForUiToSettle(authPopup);
      await maybeChooseGoogleAccount(authPopup);
      await authPopup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
      if (!authPopup.isClosed()) {
        await authPopup.close().catch(() => {});
      }
      await page.bringToFront();
    } else {
      await maybeChooseGoogleAccount(page);
    }

    await waitForUiToSettle(page);
    await expect(
      await pickFirstVisible(
        [
          page.getByRole("navigation"),
          page.locator("aside"),
          page.getByText(/negocio|mi negocio|dashboard|inicio/i),
        ],
        20000,
      ),
    ).toBeVisible();

    await expect(
      await pickFirstVisible([page.getByRole("navigation"), page.locator("aside")], 20000),
    ).toBeVisible();

    const dashboardScreenshot = await captureCheckpoint(
      page,
      testInfo,
      "01-dashboard-loaded.png",
      true,
    );
    markPass("Login", "Dashboard and left sidebar are visible after Google login.", {
      screenshot: dashboardScreenshot,
    });
  } catch (error) {
    markFail("Login", error);
  }

  // Step 2: Open Mi Negocio menu
  try {
    const negocioSection = await pickFirstVisible([
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    await negocioSection.click();
    await waitForUiToSettle(page);

    const miNegocio = await pickFirstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);
    await miNegocio.click();
    await waitForUiToSettle(page);

    await expect(
      await pickFirstVisible([
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ]),
    ).toBeVisible();

    await expect(
      await pickFirstVisible([
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ]),
    ).toBeVisible();

    const menuScreenshot = await captureCheckpoint(
      page,
      testInfo,
      "02-mi-negocio-menu-expanded.png",
      true,
    );
    markPass("Mi Negocio menu", "Mi Negocio submenu expanded with expected options.", {
      screenshot: menuScreenshot,
    });
  } catch (error) {
    markFail("Mi Negocio menu", error);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const agregarNegocio = await pickFirstVisible([
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i),
    ]);
    await agregarNegocio.click();
    await waitForUiToSettle(page);

    const modalTitle = page.getByText(/crear nuevo negocio/i).first();
    await expect(modalTitle).toBeVisible();

    const nombreInput = await pickFirstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.getByRole("textbox", { name: /nombre del negocio/i }),
    ]);
    await expect(nombreInput).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const cancelarButton = page.getByRole("button", { name: /cancelar/i }).first();
    const crearNegocioButton = page.getByRole("button", { name: /crear negocio/i }).first();
    await expect(cancelarButton).toBeVisible();
    await expect(crearNegocioButton).toBeVisible();

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    const modalScreenshot = await captureCheckpoint(
      page,
      testInfo,
      "03-crear-nuevo-negocio-modal.png",
      true,
    );

    await cancelarButton.click();
    await waitForUiToSettle(page);
    await expect(modalTitle).toBeHidden({ timeout: 10000 });

    markPass("Agregar Negocio modal", "Agregar Negocio modal validated and closed.", {
      screenshot: modalScreenshot,
    });
  } catch (error) {
    markFail("Agregar Negocio modal", error);
  }

  // Step 4: Open Administrar Negocios
  try {
    const administrarCandidates = [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ];

    let administrarVisible = false;
    for (const candidate of administrarCandidates) {
      if ((await candidate.count()) > 0 && (await candidate.first().isVisible())) {
        administrarVisible = true;
        break;
      }
    }

    if (!administrarVisible) {
      const miNegocio = await pickFirstVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);
      await miNegocio.click();
      await waitForUiToSettle(page);
    }

    const administrarNegocios = await pickFirstVisible(administrarCandidates);
    await administrarNegocios.click();
    await waitForUiToSettle(page);

    await expect(page.getByRole("heading", { name: /informaci[oó]n general/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /detalles de la cuenta/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /tus negocios/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /secci[oó]n legal/i })).toBeVisible();

    const accountScreenshot = await captureCheckpoint(
      page,
      testInfo,
      "04-administrar-negocios-account-page.png",
      true,
    );
    markPass(
      "Administrar Negocios view",
      "Account page loaded with all expected sections visible.",
      { screenshot: accountScreenshot },
    );
  } catch (error) {
    markFail("Administrar Negocios view", error);
  }

  // Step 5: Validate Información General
  try {
    const infoSection = await locateSectionByHeading(page, /informaci[oó]n general/i);
    const infoText = (await infoSection.innerText()).trim();

    const hasUserNameLabel =
      (await infoSection.getByText(/nombre|usuario|name/i).count()) > 0 ||
      (await page.getByText(/juan|lucas/i).count()) > 0;
    if (!hasUserNameLabel) {
      throw new Error("User name signal was not found in Información General.");
    }

    if (!/juanlucasbarbiergarzon@gmail\.com|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoText)) {
      throw new Error("User email was not found in Información General.");
    }

    if (!/BUSINESS PLAN/i.test(infoText)) {
      throw new Error("Text 'BUSINESS PLAN' not found in Información General.");
    }

    await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    markPass("Información General", "Información General shows user/account plan details.");
  } catch (error) {
    markFail("Información General", error);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    const detailsSection = await locateSectionByHeading(page, /detalles de la cuenta/i);
    const detailsText = (await detailsSection.innerText()).trim();

    if (!/cuenta creada/i.test(detailsText)) {
      throw new Error("'Cuenta creada' was not found.");
    }
    if (!/estado activo/i.test(detailsText)) {
      throw new Error("'Estado activo' was not found.");
    }
    if (!/idioma seleccionado/i.test(detailsText)) {
      throw new Error("'Idioma seleccionado' was not found.");
    }

    markPass("Detalles de la Cuenta", "Detalles de la Cuenta contains all expected labels.");
  } catch (error) {
    markFail("Detalles de la Cuenta", error);
  }

  // Step 7: Validate Tus Negocios
  try {
    const negociosSection = await locateSectionByHeading(page, /tus negocios/i);
    await expect(
      await pickFirstVisible(
        [
          negociosSection.locator("table"),
          negociosSection.locator("ul"),
          negociosSection.locator("ol"),
          negociosSection.locator("[role='row']"),
          negociosSection.getByText(/negocio/i),
        ],
        10000,
      ),
    ).toBeVisible();

    await expect(
      await pickFirstVisible([
        negociosSection.getByRole("button", { name: /agregar negocio/i }),
        negociosSection.getByRole("link", { name: /agregar negocio/i }),
        negociosSection.getByText(/agregar negocio/i),
      ]),
    ).toBeVisible();

    await expect(negociosSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    markPass("Tus Negocios", "Tus Negocios section and business capacity text are visible.");
  } catch (error) {
    markFail("Tus Negocios", error);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const legalSection = await locateSectionByHeading(page, /secci[oó]n legal/i);
    const termsResult = await openLegalDocument({
      page,
      context,
      legalSection,
      linkPattern: /t[eé]rminos y condiciones/i,
      headingPattern: /t[eé]rminos y condiciones/i,
      screenshotFileName: "05-terminos-y-condiciones.png",
      testInfo,
    });

    markPass(
      "Términos y Condiciones",
      "Términos y Condiciones content loaded successfully.",
      {
        screenshot: termsResult.screenshotPath,
        finalUrl: termsResult.finalUrl,
      },
    );
  } catch (error) {
    markFail("Términos y Condiciones", error);
  }

  // Step 9: Validate Política de Privacidad
  try {
    const legalSection = await locateSectionByHeading(page, /secci[oó]n legal/i);
    const privacyResult = await openLegalDocument({
      page,
      context,
      legalSection,
      linkPattern: /pol[ií]tica de privacidad/i,
      headingPattern: /pol[ií]tica de privacidad/i,
      screenshotFileName: "06-politica-de-privacidad.png",
      testInfo,
    });

    markPass("Política de Privacidad", "Política de Privacidad content loaded successfully.", {
      screenshot: privacyResult.screenshotPath,
      finalUrl: privacyResult.finalUrl,
    });
  } catch (error) {
    markFail("Política de Privacidad", error);
  }

  const orderedReport = STEP_KEYS.map((key) => ({ step: key, ...report[key] }));
  const finalReportText = JSON.stringify(
    {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      report: orderedReport,
    },
    null,
    2,
  );

  // Step 10: Final report as requested by the workflow.
  await testInfo.attach("saleads-mi-negocio-final-report", {
    contentType: "application/json",
    body: Buffer.from(finalReportText, "utf-8"),
  });
  console.log(finalReportText);

  expect(
    failures,
    `One or more SaleADS Mi Negocio validations failed:\n${failures.join("\n")}`,
  ).toEqual([]);
});
