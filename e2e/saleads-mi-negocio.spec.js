const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

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

const DEFAULT_REPORT = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function firstVisible(locatorCandidates, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locatorCandidates) {
      const candidate = locator.first();
      try {
        if (await candidate.isVisible()) {
          return candidate;
        }
      } catch (_error) {
        // Ignore detached elements and continue checking alternatives.
      }
    }
    await locatorCandidates[0].page().waitForTimeout(300);
  }

  throw new Error("None of the expected UI elements became visible.");
}

async function findActionByText(page, label) {
  const containsLabel = new RegExp(escapeRegExp(label), "i");
  const exactLabel = new RegExp(`^\\s*${escapeRegExp(label)}\\s*$`, "i");

  return firstVisible([
    page.getByRole("button", { name: exactLabel }),
    page.getByRole("button", { name: containsLabel }),
    page.getByRole("link", { name: exactLabel }),
    page.getByRole("link", { name: containsLabel }),
    page.getByRole("menuitem", { name: exactLabel }),
    page.getByRole("menuitem", { name: containsLabel }),
    page.getByRole("tab", { name: containsLabel }),
    page.getByText(exactLabel),
    page.getByText(containsLabel)
  ]);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiLoad(page);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function runStep(report, key, fn) {
  try {
    await fn();
    report[key] = "PASS";
  } catch (error) {
    report[key] = `FAIL: ${String(error.message || error).split("\n")[0]}`;
  }
}

async function validateMainAppLoaded(page) {
  await firstVisible([
    page.locator("aside"),
    page.getByRole("navigation"),
    page.getByText(/Negocio|Dashboard|Inicio/i)
  ]);
  await expect(
    await firstVisible([page.getByText(/Negocio|Mi Negocio/i), page.getByRole("navigation")])
  ).toBeVisible();
}

async function clickLegalAndValidate({
  appPage,
  linkText,
  headingText,
  screenshotName,
  testInfo
}) {
  const popupPromise = appPage.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  const clickable = await findActionByText(appPage, linkText);
  await clickable.click();
  await waitForUiLoad(appPage);

  const popupPage = await popupPromise;
  const legalPage = popupPage || appPage;

  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded");
  }

  const headingRegex = new RegExp(escapeRegExp(headingText), "i");
  await expect(
    await firstVisible([
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex)
    ])
  ).toBeVisible();

  await expect(
    await firstVisible([
      legalPage.locator("main p").first(),
      legalPage.locator("article p").first(),
      legalPage.locator("body p").first(),
      legalPage.getByText(/\S+\s+\S+\s+\S+/)
    ])
  ).toBeVisible();

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else {
    await appPage.goBack();
    await waitForUiLoad(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = { ...DEFAULT_REPORT };
  const legalUrls = {};

  const startUrl =
    process.env.SALEADS_START_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No initial page loaded. Provide SALEADS_START_URL (or SALEADS_BASE_URL / BASE_URL)."
    );
  }

  await waitForUiLoad(page);

  await runStep(report, "Login", async () => {
    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);

    const loginButton = await firstVisible([
      page.getByRole("button", {
        name: /Sign in with Google|Iniciar sesión con Google|Iniciar sesion con Google|Continuar con Google|Google/i
      }),
      page.getByRole("link", {
        name: /Sign in with Google|Iniciar sesión con Google|Iniciar sesion con Google|Continuar con Google|Google/i
      }),
      page.getByText(
        /Sign in with Google|Iniciar sesión con Google|Iniciar sesion con Google|Continuar con Google/i
      )
    ]);

    await loginButton.click();
    await waitForUiLoad(page);

    const authPopup = await popupPromise;
    const authPage = authPopup || page;
    await authPage.waitForLoadState("domcontentloaded");

    const googleSelectorVisible = await firstVisible(
      [
        authPage.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT), "i")),
        authPage.getByText(/Choose an account|Elige una cuenta|Seleccionar cuenta/i),
        authPage.getByRole("heading", { name: /Choose an account|Elige una cuenta/i })
      ],
      5000
    ).catch(() => null);

    if (googleSelectorVisible) {
      const accountOption = await firstVisible(
        [
          authPage.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT), "i")),
          authPage.getByRole("button", { name: new RegExp(escapeRegExp(GOOGLE_ACCOUNT), "i") }),
          authPage.getByRole("link", { name: new RegExp(escapeRegExp(GOOGLE_ACCOUNT), "i") })
        ],
        6000
      );
      await accountOption.click();
    }

    if (authPopup) {
      await authPopup.waitForEvent("close", { timeout: 20000 }).catch(() => null);
      await page.bringToFront();
    }

    await waitForUiLoad(page);
    await validateMainAppLoaded(page);
    await captureCheckpoint(page, testInfo, "checkpoint-dashboard-loaded", true);
  });

  await runStep(report, "Mi Negocio menu", async () => {
    const negocioSection = await findActionByText(page, "Negocio");
    await clickAndWait(page, negocioSection);

    const miNegocio = await findActionByText(page, "Mi Negocio");
    await clickAndWait(page, miNegocio);

    await expect(await findActionByText(page, "Agregar Negocio")).toBeVisible();
    await expect(await findActionByText(page, "Administrar Negocios")).toBeVisible();

    await captureCheckpoint(page, testInfo, "checkpoint-mi-negocio-menu-expanded");
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    const agregarNegocioMenuItem = await findActionByText(page, "Agregar Negocio");
    await clickAndWait(page, agregarNegocioMenuItem);

    await expect(await firstVisible([page.getByText(/Crear Nuevo Negocio/i)])).toBeVisible();
    await expect(
      await firstVisible([
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.getByRole("textbox", { name: /Nombre del Negocio/i })
      ])
    ).toBeVisible();
    await expect(await firstVisible([page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)])).toBeVisible();
    await expect(await findActionByText(page, "Cancelar")).toBeVisible();
    await expect(await findActionByText(page, "Crear Negocio")).toBeVisible();

    await captureCheckpoint(page, testInfo, "checkpoint-agregar-negocio-modal", true);

    const businessNameField = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.getByRole("textbox", { name: /Nombre del Negocio/i })
    ]);
    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatización");
    await clickAndWait(page, await findActionByText(page, "Cancelar"));
  });

  await runStep(report, "Administrar Negocios view", async () => {
    const administrarVisible = await findActionByText(page, "Administrar Negocios").catch(() => null);
    if (!administrarVisible) {
      await clickAndWait(page, await findActionByText(page, "Mi Negocio"));
    }

    await clickAndWait(page, await findActionByText(page, "Administrar Negocios"));

    await expect(await firstVisible([page.getByText(/Información General/i)])).toBeVisible();
    await expect(await firstVisible([page.getByText(/Detalles de la Cuenta/i)])).toBeVisible();
    await expect(await firstVisible([page.getByText(/Tus Negocios/i)])).toBeVisible();
    await expect(
      await firstVisible([
        page.getByText(/Sección Legal/i),
        page.getByText(/Seccion Legal/i),
        page.getByText(/Legal/i)
      ])
    ).toBeVisible();

    await captureCheckpoint(page, testInfo, "checkpoint-administrar-negocios-view", true);
  });

  await runStep(report, "Información General", async () => {
    await expect(await firstVisible([page.getByText(/Información General/i)])).toBeVisible();
    await expect(
      await firstVisible([
        page.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT), "i")),
        page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)
      ])
    ).toBeVisible();
    await expect(
      await firstVisible([
        page.getByText(/Nombre/i),
        page.getByText(/Usuario/i),
        page.locator("h1, h2, h3").filter({ hasText: /\S+/ }).first()
      ])
    ).toBeVisible();
    await expect(await firstVisible([page.getByText(/BUSINESS PLAN/i)])).toBeVisible();
    await expect(await findActionByText(page, "Cambiar Plan")).toBeVisible();
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await expect(await firstVisible([page.getByText(/Detalles de la Cuenta/i)])).toBeVisible();
    await expect(await firstVisible([page.getByText(/Cuenta creada/i)])).toBeVisible();
    await expect(await firstVisible([page.getByText(/Estado activo/i)])).toBeVisible();
    await expect(await firstVisible([page.getByText(/Idioma seleccionado/i)])).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    const tusNegociosSection = await firstVisible([
      page.locator("section, div").filter({ hasText: /Tus Negocios/i }),
      page.getByText(/Tus Negocios/i)
    ]);

    await expect(tusNegociosSection).toBeVisible();
    await expect(await findActionByText(page, "Agregar Negocio")).toBeVisible();
    await expect(await firstVisible([page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)])).toBeVisible();
    await expect(
      await firstVisible(
        [
          page.locator("li").filter({ hasText: /Negocio/i }),
          page.locator("[role='row']").filter({ hasText: /Negocio/i }),
          page.locator("article").filter({ hasText: /Negocio/i }),
          page.getByText(/Negocio/i)
        ],
        7000
      )
    ).toBeVisible();
  });

  await runStep(report, "Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await clickLegalAndValidate({
      appPage: page,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotName: "checkpoint-terminos-y-condiciones",
      testInfo
    });
  });

  await runStep(report, "Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await clickLegalAndValidate({
      appPage: page,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotName: "checkpoint-politica-de-privacidad",
      testInfo
    });
  });

  const reportLines = REPORT_FIELDS.map((field) => `${field}: ${report[field]}`);
  const urlLines = Object.entries(legalUrls).map(([name, url]) => `${name} URL: ${url}`);
  const reportText = ["Final Report", ...reportLines, ...urlLines].join("\n");

  await testInfo.attach("final-report", {
    body: reportText,
    contentType: "text/plain"
  });
  console.log(`\n${reportText}\n`);

  const failedFields = REPORT_FIELDS.filter((field) => !report[field].startsWith("PASS"));
  if (failedFields.length > 0) {
    throw new Error(
      `Workflow validation failed for: ${failedFields.join(", ")}.\nSee attached final-report.`
    );
  }
});
