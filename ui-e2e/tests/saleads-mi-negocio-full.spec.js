const { test, expect } = require("@playwright/test");

const STEP_FIELDS = [
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

function createReport() {
  return Object.fromEntries(
    STEP_FIELDS.map((field) => [field, { status: "NOT_RUN", details: "" }]),
  );
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function clickAndWait(locator, page) {
  await locator.waitFor({ state: "visible", timeout: 15000 });
  await locator.click();
  await waitForUi(page);
}

async function expectAnyVisible(locators, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await locator.count()) {
        const candidate = locator.first();
        if (await candidate.isVisible().catch(() => false)) {
          return candidate;
        }
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error("No candidate locator became visible.");
}

async function captureScreenshot(page, testInfo, name, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage,
  });
}

async function runStep(report, field, stepFn) {
  try {
    await stepFn();
    report[field] = { status: "PASS", details: "" };
  } catch (error) {
    report[field] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error),
    };
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.setTimeout(240000);

  const report = createReport();

  const markNotRunAfterFailure = () => {
    for (const field of STEP_FIELDS) {
      if (report[field].status === "NOT_RUN") {
        report[field] = {
          status: "FAIL",
          details: "Not executed because a previous required step failed.",
        };
      }
    }
  };

  const startUrlFromEnv = process.env.SALEADS_START_URL;
  if (startUrlFromEnv) {
    await page.goto(startUrlFromEnv, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await runStep(report, "Login", async () => {
    const loginButton = await expectAnyVisible([
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
      page.getByRole("button", { name: /continuar con google/i }),
      page.getByRole("button", { name: /google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[oó]n con google/i),
      page.getByText(/continuar con google/i),
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const popup = await popupPromise;

    const googleEmailOptionRegex = /juanlucasbarbiergarzon@gmail\.com/i;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await waitForUi(popup);
      const accountOption = popup.getByText(googleEmailOptionRegex).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(accountOption, popup);
      }
      await popup.waitForTimeout(1000);
    } else {
      const accountOption = page.getByText(googleEmailOptionRegex).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(accountOption, page);
      }
    }

    await waitForUi(page);

    await expect(
      expectAnyVisible([
        page.locator("aside"),
        page.locator("nav"),
        page.locator("[data-testid*=sidebar]"),
      ]),
    ).resolves.toBeTruthy();

    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 20000 });
    await captureScreenshot(page, testInfo, "01-dashboard-loaded.png", true);
  });

  if (report["Login"].status !== "PASS") {
    markNotRunAfterFailure();
  } else {
    await runStep(report, "Mi Negocio menu", async () => {
      const negocioSection = await expectAnyVisible([
        page.getByText(/^Negocio$/i),
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
      ]);
      await clickAndWait(negocioSection, page);

      const miNegocioOption = await expectAnyVisible([
        page.getByText(/^Mi Negocio$/i),
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
      ]);
      await clickAndWait(miNegocioOption, page);

      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
      await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
      await captureScreenshot(page, testInfo, "02-mi-negocio-menu-expanded.png", true);
    });

    await runStep(report, "Agregar Negocio modal", async () => {
      const agregarNegocio = await expectAnyVisible([
        page.getByText(/^Agregar Negocio$/i),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
      ]);
      await clickAndWait(agregarNegocio, page);

      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
      const businessNameInput = await expectAnyVisible([
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[name*=nombre], input[id*=nombre]").first(),
      ]);
      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();
      await captureScreenshot(page, testInfo, "03-agregar-negocio-modal.png", true);

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }), page);
    });

    await runStep(report, "Administrar Negocios view", async () => {
      const miNegocioOption = await expectAnyVisible([
        page.getByText(/^Mi Negocio$/i),
        page.getByRole("button", { name: /^Mi Negocio$/i }),
      ]);
      await clickAndWait(miNegocioOption, page);

      const administrarNegocios = await expectAnyVisible([
        page.getByText(/^Administrar Negocios$/i),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
      ]);
      await clickAndWait(administrarNegocios, page);

      await expect(page.getByText(/^Información General$/i)).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
      await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
      await expect(page.getByText(/Sección Legal/i)).toBeVisible();
      await captureScreenshot(page, testInfo, "04-administrar-negocios-page.png", true);
    });

    await runStep(report, "Información General", async () => {
      await expect(page.getByText(/^Información General$/i)).toBeVisible();
      await expect(
        page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first(),
      ).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      const possibleName = page.getByText(/[A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,}/).first();
      await expect(possibleName).toBeVisible();
    });

    await runStep(report, "Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await runStep(report, "Tus Negocios", async () => {
      await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    });

    await runStep(report, "Términos y Condiciones", async () => {
      const termsLink = await expectAnyVisible([
        page.getByRole("link", { name: /Términos y Condiciones/i }),
        page.getByText(/Términos y Condiciones/i),
      ]);

      const currentUrl = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickAndWait(termsLink, page);
      let legalPage = await popupPromise;
      let openedInNewTab = true;

      if (!legalPage) {
        legalPage = page;
        openedInNewTab = false;
      }

      await waitForUi(legalPage);
      await expect(legalPage.getByText(/Términos y Condiciones/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(legalPage.locator("p, li").first()).toBeVisible();
      const termsUrl = legalPage.url();
      report["Términos y Condiciones"].details = `URL: ${termsUrl}`;
      await captureScreenshot(legalPage, testInfo, "05-terminos-y-condiciones.png", true);

      if (openedInNewTab) {
        await legalPage.close();
        await page.bringToFront();
      } else if (page.url() !== currentUrl) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }
    });

    await runStep(report, "Política de Privacidad", async () => {
      const privacyLink = await expectAnyVisible([
        page.getByRole("link", { name: /Política de Privacidad/i }),
        page.getByText(/Política de Privacidad/i),
      ]);

      const currentUrl = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickAndWait(privacyLink, page);
      let legalPage = await popupPromise;
      let openedInNewTab = true;

      if (!legalPage) {
        legalPage = page;
        openedInNewTab = false;
      }

      await waitForUi(legalPage);
      await expect(legalPage.getByText(/Política de Privacidad/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(legalPage.locator("p, li").first()).toBeVisible();
      const privacyUrl = legalPage.url();
      report["Política de Privacidad"].details = `URL: ${privacyUrl}`;
      await captureScreenshot(legalPage, testInfo, "06-politica-de-privacidad.png", true);

      if (openedInNewTab) {
        await legalPage.close();
        await page.bringToFront();
      } else if (page.url() !== currentUrl) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }
    });
  }

  const reportJson = JSON.stringify(report, null, 2);
  await testInfo.attach("mi-negocio-final-report.json", {
    body: reportJson,
    contentType: "application/json",
  });
  console.log("Final validation report:\n", reportJson);

  const failedSteps = Object.entries(report)
    .filter(([, value]) => value.status !== "PASS")
    .map(([name, value]) => `${name}: ${value.details || value.status}`);

  expect(
    failedSteps,
    `One or more Mi Negocio validations failed:\n${failedSteps.join("\n")}`,
  ).toEqual([]);
});
