import { Locator, Page, TestInfo, test } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad",
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type ReportStatus = "PASS" | "FAIL";

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(async () => {
    await page.waitForTimeout(600);
  });
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  filename: string,
  fullPage = false,
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(filename),
    fullPage,
  });
}

async function isVisible(locator: Locator, timeout = 2500): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function firstVisible(locators: Locator[], timeout = 2500): Promise<Locator | null> {
  for (const locator of locators) {
    if (await isVisible(locator, timeout)) {
      return locator.first();
    }
  }
  return null;
}

async function assertAnyVisible(locators: Locator[], missingDescription: string): Promise<void> {
  const match = await firstVisible(locators, 12000);
  if (!match) {
    throw new Error(`No visible element found for: ${missingDescription}`);
  }
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function openLegalLinkAndValidate(params: {
  appPage: Page;
  linkLocator: Locator;
  headingRegex: RegExp;
  screenshotFile: string;
  testInfo: TestInfo;
}): Promise<string> {
  const { appPage, linkLocator, headingRegex, screenshotFile, testInfo } = params;
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await linkLocator.click();
  const popupPage = await popupPromise;
  const legalPage = popupPage ?? appPage;

  await legalPage.waitForLoadState("domcontentloaded");
  await waitForUi(legalPage);

  await assertAnyVisible(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex),
    ],
    `legal heading ${headingRegex}`,
  );

  const legalText = (await legalPage.locator("body").innerText()).trim();
  if (legalText.length < 120) {
    throw new Error("Legal content looks too short or not loaded.");
  }

  await captureCheckpoint(legalPage, testInfo, screenshotFile, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.bringToFront();
    });
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, "FAIL" as ReportStatus]),
  ) as Record<ReportField, ReportStatus>;
  const errors: string[] = [];
  const legalUrls: Record<string, string> = {};

  const runSection = async (field: ReportField, fn: () => Promise<void>) => {
    try {
      await fn();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      errors.push(`${field}: ${message}`);
    }
  };

  const startUrl = process.env.SALEADS_START_URL ?? process.env.BASE_URL;
  if (page.url() === "about:blank") {
    if (!startUrl) {
      throw new Error(
        "Set SALEADS_START_URL (or BASE_URL) to the login page of the current SaleADS environment.",
      );
    }
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  }
  await waitForUi(page);

  await runSection("Login", async () => {
    const sidebarBeforeLogin = await firstVisible(
      [page.locator("aside"), page.getByRole("navigation"), page.getByText(/mi negocio|negocio/i)],
      2000,
    );

    if (!sidebarBeforeLogin) {
      const loginButton = await firstVisible(
        [
          page.getByRole("button", { name: /google|iniciar sesi[o\u00f3]n|sign in/i }),
          page.getByRole("link", { name: /google|iniciar sesi[o\u00f3]n|sign in/i }),
          page.getByText(/google/i),
        ],
        8000,
      );
      if (!loginButton) {
        throw new Error("Google login button was not found.");
      }

      const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const popupPage = await popupPromise;
      const authPage = popupPage ?? page;
      await authPage.waitForLoadState("domcontentloaded").catch(() => undefined);

      const accountOption = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
      if (await isVisible(accountOption, 10000)) {
        await accountOption.click();
        await waitForUi(authPage);
      }

      if (popupPage) {
        await popupPage.waitForEvent("close", { timeout: 20000 }).catch(() => undefined);
      }
    }

    await assertAnyVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.getByText(/mi negocio|negocio/i),
      ],
      "main application with left sidebar",
    );

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runSection("Mi Negocio menu", async () => {
    const negocioEntry = await firstVisible(
      [
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^negocio$/i),
      ],
      8000,
    );
    if (!negocioEntry) {
      throw new Error("Sidebar section 'Negocio' was not found.");
    }
    await clickAndWait(page, negocioEntry);

    const miNegocioEntry = await firstVisible(
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ],
      8000,
    );
    if (!miNegocioEntry) {
      throw new Error("Option 'Mi Negocio' was not found.");
    }
    await clickAndWait(page, miNegocioEntry);

    await assertAnyVisible(
      [page.getByRole("link", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      "'Agregar Negocio' option",
    );
    await assertAnyVisible(
      [
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ],
      "'Administrar Negocios' option",
    );

    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", false);
  });

  await runSection("Agregar Negocio modal", async () => {
    const addBusiness = await firstVisible(
      [page.getByRole("link", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      8000,
    );
    if (!addBusiness) {
      throw new Error("'Agregar Negocio' action not found.");
    }
    await clickAndWait(page, addBusiness);

    await assertAnyVisible(
      [page.getByRole("heading", { name: /crear nuevo negocio/i }), page.getByText(/crear nuevo negocio/i)],
      "modal title 'Crear Nuevo Negocio'",
    );

    const businessNameInput = await firstVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.getByRole("textbox", { name: /nombre del negocio/i }),
      ],
      8000,
    );
    if (!businessNameInput) {
      throw new Error("Input 'Nombre del Negocio' not found.");
    }

    await assertAnyVisible(
      [page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)],
      "text 'Tienes 2 de 3 negocios'",
    );
    await assertAnyVisible(
      [page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)],
      "button 'Cancelar'",
    );
    await assertAnyVisible(
      [page.getByRole("button", { name: /crear negocio/i }), page.getByText(/crear negocio/i)],
      "button 'Crear Negocio'",
    );

    await captureCheckpoint(page, testInfo, "03-crear-negocio-modal.png", false);

    await businessNameInput.fill("Negocio Prueba Automatizacion");
    const cancelButton = await firstVisible(
      [page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)],
      5000,
    );
    if (cancelButton) {
      await clickAndWait(page, cancelButton);
    }
  });

  await runSection("Administrar Negocios view", async () => {
    let adminOption = await firstVisible(
      [
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ],
      2000,
    );

    if (!adminOption) {
      const miNegocioEntry = await firstVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        8000,
      );
      if (miNegocioEntry) {
        await clickAndWait(page, miNegocioEntry);
      }
      adminOption = await firstVisible(
        [
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ],
        8000,
      );
    }

    if (!adminOption) {
      throw new Error("'Administrar Negocios' option not available.");
    }
    await clickAndWait(page, adminOption);

    await assertAnyVisible(
      [page.getByRole("heading", { name: /informaci[o\u00f3]n general/i }), page.getByText(/informaci[o\u00f3]n general/i)],
      "section 'Informacion General'",
    );
    await assertAnyVisible(
      [page.getByRole("heading", { name: /detalles de la cuenta/i }), page.getByText(/detalles de la cuenta/i)],
      "section 'Detalles de la Cuenta'",
    );
    await assertAnyVisible(
      [page.getByRole("heading", { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      "section 'Tus Negocios'",
    );
    await assertAnyVisible(
      [page.getByRole("heading", { name: /secci[o\u00f3]n legal/i }), page.getByText(/secci[o\u00f3]n legal/i)],
      "section 'Seccion Legal'",
    );

    await captureCheckpoint(page, testInfo, "04-administrar-negocios.png", true);
  });

  await runSection("Informaci\u00f3n General", async () => {
    await assertAnyVisible(
      [page.getByText(/@/), page.getByText(/usuario|nombre/i)],
      "user name or user email",
    );
    await assertAnyVisible([page.getByText(/business plan/i)], "text 'BUSINESS PLAN'");
    await assertAnyVisible(
      [page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)],
      "button 'Cambiar Plan'",
    );
  });

  await runSection("Detalles de la Cuenta", async () => {
    await assertAnyVisible([page.getByText(/cuenta creada/i)], "'Cuenta creada'");
    await assertAnyVisible([page.getByText(/estado activo/i)], "'Estado activo'");
    await assertAnyVisible([page.getByText(/idioma seleccionado/i)], "'Idioma seleccionado'");
  });

  await runSection("Tus Negocios", async () => {
    await assertAnyVisible(
      [page.getByRole("heading", { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      "business list header",
    );
    await assertAnyVisible(
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      "button 'Agregar Negocio'",
    );
    await assertAnyVisible(
      [page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)],
      "text 'Tienes 2 de 3 negocios'",
    );
  });

  await runSection("T\u00e9rminos y Condiciones", async () => {
    const termsLink = await firstVisible(
      [
        page.getByRole("link", { name: /t[e\u00e9]rminos y condiciones/i }),
        page.getByText(/t[e\u00e9]rminos y condiciones/i),
      ],
      8000,
    );
    if (!termsLink) {
      throw new Error("'Terminos y Condiciones' link not found.");
    }

    const finalUrl = await openLegalLinkAndValidate({
      appPage: page,
      linkLocator: termsLink,
      headingRegex: /t[e\u00e9]rminos y condiciones/i,
      screenshotFile: "05-terminos-y-condiciones.png",
      testInfo,
    });
    legalUrls["Terminos y Condiciones URL"] = finalUrl;
  });

  await runSection("Pol\u00edtica de Privacidad", async () => {
    const privacyLink = await firstVisible(
      [
        page.getByRole("link", { name: /pol[i\u00ed]tica de privacidad/i }),
        page.getByText(/pol[i\u00ed]tica de privacidad/i),
      ],
      8000,
    );
    if (!privacyLink) {
      throw new Error("'Politica de Privacidad' link not found.");
    }

    const finalUrl = await openLegalLinkAndValidate({
      appPage: page,
      linkLocator: privacyLink,
      headingRegex: /pol[i\u00ed]tica de privacidad/i,
      screenshotFile: "06-politica-de-privacidad.png",
      testInfo,
    });
    legalUrls["Politica de Privacidad URL"] = finalUrl;
  });

  await testInfo.attach("final-report.json", {
    body: Buffer.from(
      JSON.stringify(
        {
          report,
          legalUrls,
          failures: errors,
        },
        null,
        2,
      ),
    ),
    contentType: "application/json",
  });

  if (errors.length) {
    throw new Error(`Validation failures:\n- ${errors.join("\n- ")}`);
  }
});
