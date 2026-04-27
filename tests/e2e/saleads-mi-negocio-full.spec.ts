import { expect, type Locator, type Page, type TestInfo, test } from "@playwright/test";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

type StepResult = "PASS" | "FAIL";

const googleAccountEmail =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";

const reportFields: ReportField[] = [
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

function createInitialReport(): Record<ReportField, StepResult> {
  return reportFields.reduce<Record<ReportField, StepResult>>((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {} as Record<ReportField, StepResult>);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function findVisibleLocator(
  page: Page,
  locatorBuilders: Array<() => Locator>,
  timeoutMs = 15_000,
): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const builder of locatorBuilders) {
      const candidate = builder().first();
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error("No visible locator found for provided candidates.");
}

async function findOptionalVisibleLocator(
  page: Page,
  locatorBuilders: Array<() => Locator>,
  timeoutMs = 5_000,
): Promise<Locator | null> {
  try {
    return await findVisibleLocator(page, locatorBuilders, timeoutMs);
  } catch {
    return null;
  }
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => undefined);
  await locator.click({ timeout: 12_000 });
  await waitForUiToLoad(page);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false,
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function selectGoogleAccountIfVisible(page: Page): Promise<void> {
  const emailPattern = new RegExp(escapeRegExp(googleAccountEmail), "i");
  const accountOption = await findOptionalVisibleLocator(
    page,
    [
      () => page.getByRole("button", { name: emailPattern }),
      () => page.getByRole("link", { name: emailPattern }),
      () => page.getByText(emailPattern),
      () => page.locator(`[data-identifier="${googleAccountEmail}"]`),
    ],
    10_000,
  );

  if (!accountOption) {
    return;
  }

  await accountOption.scrollIntoViewIfNeeded().catch(() => undefined);
  await accountOption.click({ timeout: 10_000 }).catch(() => undefined);

  if (!page.isClosed()) {
    await waitForUiToLoad(page);
  }
}

async function getSectionContainer(page: Page, sectionTitlePattern: RegExp): Promise<Locator> {
  const heading = await findVisibleLocator(page, [
    () => page.getByRole("heading", { name: sectionTitlePattern }),
    () => page.getByText(sectionTitlePattern),
  ]);

  const sectionLocator = heading.locator("xpath=ancestor::section[1]");
  if ((await sectionLocator.count()) > 0) {
    return sectionLocator.first();
  }

  const articleLocator = heading.locator("xpath=ancestor::article[1]");
  if ((await articleLocator.count()) > 0) {
    return articleLocator.first();
  }

  const divLocator = heading.locator("xpath=ancestor::div[1]");
  if ((await divLocator.count()) > 0) {
    return divLocator.first();
  }

  return page.locator("body");
}

async function openLegalLinkAndValidate(
  appPage: Page,
  linkPattern: RegExp,
  headingPattern: RegExp,
  screenshotName: string,
  testInfo: TestInfo,
): Promise<string> {
  const legalSection = await getSectionContainer(appPage, /Secci[o\u00f3]n Legal/i);
  const legalLink = await findVisibleLocator(appPage, [
    () => legalSection.getByRole("link", { name: linkPattern }),
    () => legalSection.getByText(linkPattern),
    () => appPage.getByRole("link", { name: linkPattern }),
    () => appPage.getByText(linkPattern),
  ]);

  const sourceUrl = appPage.url();
  const popupPromise = appPage
    .context()
    .waitForEvent("page", { timeout: 8_000 })
    .catch(() => null);

  await clickAndWait(legalLink, appPage);
  const popup = await popupPromise;

  const targetPage = popup ?? appPage;
  await waitForUiToLoad(targetPage);

  await findVisibleLocator(targetPage, [
    () => targetPage.getByRole("heading", { name: headingPattern }),
    () => targetPage.getByText(headingPattern),
  ]);

  const body = targetPage.locator("body");
  const legalText = (await body.innerText()).replace(/\s+/g, " ").trim();
  if (legalText.length < 150) {
    throw new Error("Legal content text is too short or not visible.");
  }

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close().catch(() => undefined);
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);
  } else if (appPage.url() !== sourceUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiToLoad(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createInitialReport();
  const failures: string[] = [];
  let terminosUrl = "";
  let politicaUrl = "";

  const runValidation = async (field: ReportField, action: () => Promise<void>) => {
    await test.step(field, async () => {
      try {
        await action();
        report[field] = "PASS";
      } catch (error) {
        report[field] = "FAIL";
        const message = error instanceof Error ? error.message : String(error);
        failures.push(`${field}: ${message}`);
      }
    });
  };

  await runValidation("Login", async () => {
    const configuredStartUrl = process.env.SALEADS_URL ?? process.env.BASE_URL;
    if (configuredStartUrl) {
      await page.goto(configuredStartUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    }

    const loginButton = await findVisibleLocator(page, [
      () =>
        page.getByRole("button", {
          name: /Sign in with Google|Iniciar sesi[o\u00f3]n con Google|Continuar con Google|Google/i,
        }),
      () =>
        page.getByRole("link", {
          name: /Sign in with Google|Iniciar sesi[o\u00f3]n con Google|Continuar con Google|Google/i,
        }),
      () =>
        page.getByText(
          /Sign in with Google|Iniciar sesi[o\u00f3]n con Google|Continuar con Google|Google/i,
        ),
    ]);

    const popupPromise = page
      .context()
      .waitForEvent("page", { timeout: 10_000 })
      .catch(() => null);

    await clickAndWait(loginButton, page);

    const loginPopup = await popupPromise;
    if (loginPopup) {
      await waitForUiToLoad(loginPopup);
      await selectGoogleAccountIfVisible(loginPopup);
      await loginPopup.waitForClose({ timeout: 60_000 }).catch(() => undefined);
    }

    await selectGoogleAccountIfVisible(page);
    await waitForUiToLoad(page);

    const sidebar = await findVisibleLocator(page, [
      () => page.locator("aside"),
      () => page.getByRole("navigation"),
      () => page.locator('[class*="sidebar"]'),
    ]);
    await expect(sidebar).toBeVisible();

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png");
  });

  await runValidation("Mi Negocio menu", async () => {
    const negocioSection = await findVisibleLocator(page, [
      () => page.getByRole("button", { name: /^Negocio$/i }),
      () => page.getByRole("link", { name: /^Negocio$/i }),
      () => page.getByText(/^Negocio$/i),
    ]);

    await clickAndWait(negocioSection, page);

    const miNegocioOption = await findVisibleLocator(page, [
      () => page.getByRole("button", { name: /^Mi Negocio$/i }),
      () => page.getByRole("link", { name: /^Mi Negocio$/i }),
      () => page.getByText(/^Mi Negocio$/i),
    ]);
    await clickAndWait(miNegocioOption, page);

    await expect(
      await findVisibleLocator(page, [
        () => page.getByRole("button", { name: /^Agregar Negocio$/i }),
        () => page.getByRole("link", { name: /^Agregar Negocio$/i }),
        () => page.getByText(/^Agregar Negocio$/i),
      ]),
    ).toBeVisible();

    await expect(
      await findVisibleLocator(page, [
        () => page.getByRole("button", { name: /^Administrar Negocios$/i }),
        () => page.getByRole("link", { name: /^Administrar Negocios$/i }),
        () => page.getByText(/^Administrar Negocios$/i),
      ]),
    ).toBeVisible();

    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runValidation("Agregar Negocio modal", async () => {
    const agregarNegocioAction = await findVisibleLocator(page, [
      () => page.getByRole("button", { name: /^Agregar Negocio$/i }),
      () => page.getByRole("link", { name: /^Agregar Negocio$/i }),
      () => page.getByText(/^Agregar Negocio$/i),
    ]);
    await clickAndWait(agregarNegocioAction, page);

    await expect(
      await findVisibleLocator(page, [
        () => page.getByRole("heading", { name: /^Crear Nuevo Negocio$/i }),
        () => page.getByText(/^Crear Nuevo Negocio$/i),
      ]),
    ).toBeVisible();

    const businessNameInput = await findVisibleLocator(page, [
      () => page.getByLabel(/Nombre del Negocio/i),
      () => page.getByPlaceholder(/Nombre del Negocio/i),
      () => page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
      () => page.locator('input[name*="negocio" i]'),
    ]);
    await expect(businessNameInput).toBeVisible();

    await expect(
      await findVisibleLocator(page, [() => page.getByText(/Tienes 2 de 3 negocios/i)]),
    ).toBeVisible();

    const cancelButton = await findVisibleLocator(page, [
      () => page.getByRole("button", { name: /^Cancelar$/i }),
      () => page.getByText(/^Cancelar$/i),
    ]);
    const createButton = await findVisibleLocator(page, [
      () => page.getByRole("button", { name: /^Crear Negocio$/i }),
      () => page.getByText(/^Crear Negocio$/i),
    ]);
    await expect(cancelButton).toBeVisible();
    await expect(createButton).toBeVisible();

    await captureCheckpoint(page, testInfo, "03-crear-nuevo-negocio-modal.png");

    await businessNameInput.click({ timeout: 10_000 });
    await waitForUiToLoad(page);
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await waitForUiToLoad(page);

    await clickAndWait(cancelButton, page);
  });

  await runValidation("Administrar Negocios view", async () => {
    let administrarNegociosOption = await findOptionalVisibleLocator(
      page,
      [
        () => page.getByRole("button", { name: /^Administrar Negocios$/i }),
        () => page.getByRole("link", { name: /^Administrar Negocios$/i }),
        () => page.getByText(/^Administrar Negocios$/i),
      ],
      5_000,
    );

    if (!administrarNegociosOption) {
      const miNegocioOption = await findVisibleLocator(page, [
        () => page.getByRole("button", { name: /^Mi Negocio$/i }),
        () => page.getByRole("link", { name: /^Mi Negocio$/i }),
        () => page.getByText(/^Mi Negocio$/i),
      ]);
      await clickAndWait(miNegocioOption, page);

      administrarNegociosOption = await findVisibleLocator(page, [
        () => page.getByRole("button", { name: /^Administrar Negocios$/i }),
        () => page.getByRole("link", { name: /^Administrar Negocios$/i }),
        () => page.getByText(/^Administrar Negocios$/i),
      ]);
    }

    await clickAndWait(administrarNegociosOption, page);

    await expect(
      await findVisibleLocator(page, [() => page.getByText(/Informaci[o\u00f3]n General/i)]),
    ).toBeVisible();
    await expect(
      await findVisibleLocator(page, [() => page.getByText(/Detalles de la Cuenta/i)]),
    ).toBeVisible();
    await expect(await findVisibleLocator(page, [() => page.getByText(/Tus Negocios/i)])).toBeVisible();
    await expect(
      await findVisibleLocator(page, [() => page.getByText(/Secci[o\u00f3]n Legal/i)]),
    ).toBeVisible();

    await captureCheckpoint(page, testInfo, "04-administrar-negocios-view.png", true);
  });

  await runValidation("Informacion General", async () => {
    const infoSection = await getSectionContainer(page, /Informaci[o\u00f3]n General/i);

    const explicitEmail = await findOptionalVisibleLocator(
      page,
      [() => page.getByText(new RegExp(escapeRegExp(googleAccountEmail), "i"))],
      5_000,
    );
    if (!explicitEmail) {
      const text = await infoSection.innerText();
      if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(text)) {
        throw new Error("User email is not visible in Informacion General.");
      }
    }

    const infoTextLines = (await infoSection.innerText())
      .split(/\n+/)
      .map((line) => line.trim())
      .filter(Boolean);
    const candidateName = infoTextLines.find(
      (line) =>
        !/informaci[o\u00f3]n general|business plan|cambiar plan|@/i.test(line) &&
        /[A-Za-z\u00c0-\u017f]{2,}/.test(line),
    );
    if (!candidateName) {
      throw new Error("User name is not visible in Informacion General.");
    }

    await expect(
      await findVisibleLocator(page, [() => page.getByText(/BUSINESS PLAN/i)]),
    ).toBeVisible();
    await expect(
      await findVisibleLocator(page, [
        () => page.getByRole("button", { name: /Cambiar Plan/i }),
        () => page.getByText(/Cambiar Plan/i),
      ]),
    ).toBeVisible();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(
      await findVisibleLocator(page, [() => page.getByText(/Cuenta creada/i)]),
    ).toBeVisible();
    await expect(await findVisibleLocator(page, [() => page.getByText(/Estado activo/i)])).toBeVisible();
    await expect(
      await findVisibleLocator(page, [() => page.getByText(/Idioma seleccionado/i)]),
    ).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    const negociosSection = await getSectionContainer(page, /Tus Negocios/i);

    const businessItem = await findOptionalVisibleLocator(
      page,
      [
        () => negociosSection.locator("ul li"),
        () => negociosSection.locator('[role="listitem"]'),
        () => negociosSection.locator("table tbody tr"),
        () => negociosSection.getByText(/Negocio/i),
      ],
      8_000,
    );

    if (!businessItem) {
      throw new Error("Business list is not visible in Tus Negocios.");
    }

    await expect(
      await findVisibleLocator(page, [
        () => negociosSection.getByRole("button", { name: /^Agregar Negocio$/i }),
        () => negociosSection.getByText(/^Agregar Negocio$/i),
      ]),
    ).toBeVisible();

    await expect(
      await findVisibleLocator(page, [() => negociosSection.getByText(/Tienes 2 de 3 negocios/i)]),
    ).toBeVisible();
  });

  await runValidation("Terminos y Condiciones", async () => {
    terminosUrl = await openLegalLinkAndValidate(
      page,
      /T[e\u00e9]rminos y Condiciones/i,
      /T[e\u00e9]rminos y Condiciones/i,
      "08-terminos-y-condiciones.png",
      testInfo,
    );
  });

  await runValidation("Politica de Privacidad", async () => {
    politicaUrl = await openLegalLinkAndValidate(
      page,
      /Pol[i\u00ed]tica de Privacidad/i,
      /Pol[i\u00ed]tica de Privacidad/i,
      "09-politica-de-privacidad.png",
      testInfo,
    );
  });

  await test.step("Final Report", async () => {
    const payload = {
      report,
      legalUrls: {
        terminosYCondiciones: terminosUrl,
        politicaDePrivacidad: politicaUrl,
      },
    };

    await testInfo.attach("saleads-mi-negocio-final-report.json", {
      body: Buffer.from(JSON.stringify(payload, null, 2), "utf-8"),
      contentType: "application/json",
    });

    console.log("SaleADS Mi Negocio final report:");
    console.log(JSON.stringify(payload, null, 2));
  });

  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
