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
  "Política de Privacidad",
];

function createInitialReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function entryUrlFromEnv() {
  return (
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    ""
  );
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(500);
}

async function firstVisible(candidates, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      try {
        await expect(candidate).toBeVisible({ timeout: 1000 });
        return candidate;
      } catch (error) {
        // Keep trying until timeout.
      }
    }
  }

  throw new Error("No visible candidate locator matched.");
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function takeCheckpoint(page, testInfo, name, fullPage = true) {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage,
  });
}

async function markStep(report, key, fn) {
  await fn();
  report[key] = "PASS";
}

async function locateSidebar(page) {
  return firstVisible([
    page.locator("aside"),
    page.getByRole("navigation").first(),
    page.locator("nav").first(),
  ]);
}

async function validateLegalDocument({
  appPage,
  context,
  testInfo,
  linkNameRegex,
  headingRegex,
  screenshotName,
  urlFileName,
}) {
  const legalTrigger = await firstVisible([
    appPage.getByRole("link", { name: linkNameRegex }).first(),
    appPage.getByRole("button", { name: linkNameRegex }).first(),
    appPage.getByText(linkNameRegex).first(),
  ]);

  const appUrlBeforeClick = appPage.url();
  const newTabPromise = context
    .waitForEvent("page", { timeout: 7000 })
    .catch(() => null);

  await legalTrigger.click();

  let legalPage = await newTabPromise;
  let openedInNewTab = Boolean(legalPage);

  if (!legalPage) {
    legalPage = appPage;
  }

  await waitForUi(legalPage);

  const heading = await firstVisible([
    legalPage.getByRole("heading", { name: headingRegex }).first(),
    legalPage.locator("h1, h2, h3").filter({ hasText: headingRegex }).first(),
    legalPage.getByText(headingRegex).first(),
  ]);
  await expect(heading).toBeVisible();

  const bodyContent = await firstVisible([
    legalPage.locator("article p, article li").first(),
    legalPage.locator("main p, main li").first(),
    legalPage.locator("p, li").first(),
  ]);
  await expect(bodyContent).toBeVisible();

  const finalUrl = legalPage.url();
  await takeCheckpoint(legalPage, testInfo, screenshotName);
  await testInfo.attach(urlFileName, {
    body: finalUrl,
    contentType: "text/plain",
  });

  if (openedInNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
  } else if (appPage.url() !== appUrlBeforeClick) {
    await appPage.goBack().catch(() => {});
    await waitForUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createInitialReport();

  try {
    const envEntryUrl = entryUrlFromEnv();
    if (page.url() === "about:blank") {
      test.skip(
        !envEntryUrl,
        "Provide SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL) to run in your current environment."
      );
      await page.goto(envEntryUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    await markStep(report, "Login", async () => {
      const loginButton = await firstVisible([
        page.getByRole("button", {
          name: /google|sign in|iniciar sesi[oó]n/i,
        }),
        page.getByRole("link", {
          name: /google|sign in|iniciar sesi[oó]n/i,
        }),
        page.getByText(/google|sign in|iniciar sesi[oó]n/i).first(),
      ]);

      const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
      await clickAndWait(loginButton, page);
      const popup = await popupPromise;

      if (popup) {
        await waitForUi(popup);
        const accountLocator = popup.getByText(
          "juanlucasbarbiergarzon@gmail.com",
          { exact: false }
        );
        if (await accountLocator.isVisible().catch(() => false)) {
          await accountLocator.click();
        }
        await popup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
      } else {
        const accountLocator = page.getByText("juanlucasbarbiergarzon@gmail.com", {
          exact: false,
        });
        if (await accountLocator.isVisible().catch(() => false)) {
          await accountLocator.click();
        }
      }

      const sidebar = await locateSidebar(page);
      await expect(sidebar).toBeVisible({ timeout: 60000 });
      await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({
        timeout: 60000,
      });

      await takeCheckpoint(page, testInfo, "01-dashboard-loaded.png");
    });

    await markStep(report, "Mi Negocio menu", async () => {
      await locateSidebar(page);

      const negocioSection = await firstVisible([
        page.getByRole("button", { name: /^Negocio$/i }).first(),
        page.getByRole("button", { name: /Negocio/i }).first(),
        page.getByText(/^Negocio$/i).first(),
        page.getByText(/Negocio/i).first(),
      ]);
      await clickAndWait(negocioSection, page);

      const miNegocioOption = await firstVisible([
        page.getByRole("button", { name: /Mi Negocio/i }).first(),
        page.getByRole("link", { name: /Mi Negocio/i }).first(),
        page.getByText(/Mi Negocio/i).first(),
      ]);
      await clickAndWait(miNegocioOption, page);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

      await takeCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
    });

    await markStep(report, "Agregar Negocio modal", async () => {
      const addBusinessTrigger = await firstVisible([
        page.getByRole("button", { name: /Agregar Negocio/i }).first(),
        page.getByRole("link", { name: /Agregar Negocio/i }).first(),
        page.getByText(/Agregar Negocio/i).first(),
      ]);
      await clickAndWait(addBusinessTrigger, page);

      await expect(
        page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first()
      ).toBeVisible();
      await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

      const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");

      await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

      await clickAndWait(page.getByRole("button", { name: /Cancelar/i }).first(), page);
    });

    await markStep(report, "Administrar Negocios view", async () => {
      const miNegocioOption = await firstVisible([
        page.getByRole("button", { name: /Mi Negocio/i }).first(),
        page.getByRole("link", { name: /Mi Negocio/i }).first(),
        page.getByText(/Mi Negocio/i).first(),
      ]);
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
        await clickAndWait(miNegocioOption, page);
      }

      const manageBusinesses = await firstVisible([
        page.getByRole("button", { name: /Administrar Negocios/i }).first(),
        page.getByRole("link", { name: /Administrar Negocios/i }).first(),
        page.getByText(/Administrar Negocios/i).first(),
      ]);
      await clickAndWait(manageBusinesses, page);

      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

      await takeCheckpoint(page, testInfo, "04-administrar-negocios-full-page.png");
    });

    await markStep(report, "Información General", async () => {
      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();

      const pageText = await page.locator("body").innerText();
      expect(pageText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);

      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    });

    await markStep(report, "Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });

    await markStep(report, "Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

      const addBusinessButton = await firstVisible([
        page.getByRole("button", { name: /Agregar Negocio/i }).first(),
        page.getByRole("link", { name: /Agregar Negocio/i }).first(),
        page.getByText(/Agregar Negocio/i).first(),
      ]);
      await expect(addBusinessButton).toBeVisible();

      const businessListItem = await firstVisible([
        page.locator("ul li").first(),
        page.locator("table tbody tr").first(),
        page.locator("[data-testid*='business']").first(),
      ]);
      await expect(businessListItem).toBeVisible();
    });

    await markStep(report, "Términos y Condiciones", async () => {
      await validateLegalDocument({
        appPage: page,
        context,
        testInfo,
        linkNameRegex: /T[ée]rminos y Condiciones/i,
        headingRegex: /T[ée]rminos y Condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png",
        urlFileName: "terminos-url.txt",
      });
    });

    await markStep(report, "Política de Privacidad", async () => {
      await validateLegalDocument({
        appPage: page,
        context,
        testInfo,
        linkNameRegex: /Pol[ií]tica de Privacidad/i,
        headingRegex: /Pol[ií]tica de Privacidad/i,
        screenshotName: "06-politica-de-privacidad.png",
        urlFileName: "politica-url.txt",
      });
    });
  } finally {
    await testInfo.attach("saleads-mi-negocio-final-report.json", {
      body: JSON.stringify(report, null, 2),
      contentType: "application/json",
    });
  }

  for (const [field, status] of Object.entries(report)) {
    expect(status, `Validation failed for '${field}'.`).toBe("PASS");
  }
});
