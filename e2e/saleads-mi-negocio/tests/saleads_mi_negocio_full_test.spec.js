const { test, expect } = require("@playwright/test");

const DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function waitForAnyVisible(page, factories, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const factory of factories) {
      const locator = factory(page).first();

      try {
        if (await locator.isVisible()) {
          return locator;
        }
      } catch {
        // keep trying with other candidates while DOM updates
      }
    }

    await page.waitForTimeout(250);
  }

  return null;
}

async function assertVisible(page, factories, message) {
  const locator = await waitForAnyVisible(page, factories);
  expect(locator, message).toBeTruthy();
  return locator;
}

async function screenshot(testInfo, page, name, fullPage = false) {
  const fileName = `${Date.now()}-${name}.png`;
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const accountEmail = process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_ACCOUNT_EMAIL;
  const startUrl = process.env.SALEADS_START_URL || process.env.BASE_URL;

  if (page.url() === "about:blank" && startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank" && !startUrl) {
    throw new Error(
      "Set SALEADS_START_URL (or BASE_URL) to the current environment login page when starting from about:blank."
    );
  }

  await waitForUiLoad(page);

  const runStep = async (field, action) => {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      console.error(`[STEP FAIL] ${field}: ${error.message}`);
    }
  };

  await runStep("Login", async () => {
    const sidebarAlreadyVisible = await waitForAnyVisible(page, [
      (p) => p.getByRole("navigation"),
      (p) => p.locator("aside"),
      (p) => p.getByText(/Mi Negocio|Negocio/i)
    ], 4000);

    if (!sidebarAlreadyVisible) {
      const loginButton = await assertVisible(page, [
        (p) => p.getByRole("button", { name: /Sign in with Google|Iniciar sesi[oó]n con Google/i }),
        (p) => p.getByText(/Sign in with Google|Iniciar sesi[oó]n con Google/i),
        (p) => p.getByRole("button", { name: /Google/i })
      ], "Google login button was not found.");

      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await loginButton.click();
      await waitForUiLoad(page);

      let googlePage = await popupPromise;
      if (!googlePage && /accounts\.google\.com/.test(page.url())) {
        googlePage = page;
      }

      if (googlePage) {
        await waitForUiLoad(googlePage);
        const accountOption = await waitForAnyVisible(googlePage, [
          (p) => p.getByText(accountEmail, { exact: true }),
          (p) => p.getByRole("button", { name: accountEmail }),
          (p) => p.getByRole("link", { name: accountEmail })
        ], 12000);

        if (accountOption) {
          await accountOption.click();
        }

        if (googlePage !== page) {
          await Promise.race([
            googlePage.waitForEvent("close", { timeout: 20000 }),
            page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {})
          ]).catch(() => {});
        }
      }
    }

    await page.bringToFront();
    await waitForUiLoad(page);

    await assertVisible(page, [
      (p) => p.getByRole("main"),
      (p) => p.locator("main"),
      (p) => p.getByText(/Dashboard|Inicio|Mi Negocio|Negocio/i)
    ], "Main application interface was not visible after login.");

    await assertVisible(page, [
      (p) => p.getByRole("navigation"),
      (p) => p.locator("aside"),
      (p) => p.getByText(/Negocio|Mi Negocio/i)
    ], "Left sidebar navigation was not visible after login.");

    await screenshot(testInfo, page, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioItem = await assertVisible(page, [
      (p) => p.getByRole("button", { name: /^Negocio$/i }),
      (p) => p.getByRole("link", { name: /^Negocio$/i }),
      (p) => p.getByText(/^Negocio$/i)
    ], "Negocio section was not visible in the sidebar.");

    await negocioItem.click();
    await waitForUiLoad(page);

    const miNegocioItem = await assertVisible(page, [
      (p) => p.getByRole("button", { name: /Mi Negocio/i }),
      (p) => p.getByRole("link", { name: /Mi Negocio/i }),
      (p) => p.getByText(/Mi Negocio/i)
    ], "Mi Negocio option was not visible.");

    await miNegocioItem.click();
    await waitForUiLoad(page);

    await assertVisible(page, [
      (p) => p.getByRole("menuitem", { name: /Agregar Negocio/i }),
      (p) => p.getByRole("button", { name: /Agregar Negocio/i }),
      (p) => p.getByRole("link", { name: /Agregar Negocio/i }),
      (p) => p.getByText(/Agregar Negocio/i)
    ], "Agregar Negocio was not visible in the expanded submenu.");

    await assertVisible(page, [
      (p) => p.getByRole("menuitem", { name: /Administrar Negocios/i }),
      (p) => p.getByRole("button", { name: /Administrar Negocios/i }),
      (p) => p.getByRole("link", { name: /Administrar Negocios/i }),
      (p) => p.getByText(/Administrar Negocios/i)
    ], "Administrar Negocios was not visible in the expanded submenu.");

    await screenshot(testInfo, page, "02-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusinessAction = await assertVisible(page, [
      (p) => p.getByRole("menuitem", { name: /Agregar Negocio/i }),
      (p) => p.getByRole("button", { name: /Agregar Negocio/i }),
      (p) => p.getByRole("link", { name: /Agregar Negocio/i }),
      (p) => p.getByText(/Agregar Negocio/i)
    ], "Agregar Negocio entry was not available.");

    await addBusinessAction.click();
    await waitForUiLoad(page);

    await assertVisible(page, [
      (p) => p.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
      (p) => p.getByText(/Crear Nuevo Negocio/i)
    ], "Modal title 'Crear Nuevo Negocio' was not visible.");

    const nameInput = await assertVisible(page, [
      (p) => p.getByLabel(/Nombre del Negocio/i),
      (p) => p.getByPlaceholder(/Nombre del Negocio/i),
      (p) => p.getByRole("textbox", { name: /Nombre del Negocio/i }),
      (p) => p.locator("input[type='text'], input:not([type])")
    ], "Input 'Nombre del Negocio' was not visible.");

    await assertVisible(page, [
      (p) => p.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)
    ], "Business limit text 'Tienes 2 de 3 negocios' was not visible.");

    await assertVisible(page, [
      (p) => p.getByRole("button", { name: /Cancelar/i }),
      (p) => p.getByText(/^Cancelar$/i)
    ], "Cancelar button was not visible.");

    await assertVisible(page, [
      (p) => p.getByRole("button", { name: /Crear Negocio/i }),
      (p) => p.getByText(/^Crear Negocio$/i)
    ], "Crear Negocio button was not visible.");

    await screenshot(testInfo, page, "03-crear-nuevo-negocio-modal");

    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    const cancelButton = await assertVisible(page, [
      (p) => p.getByRole("button", { name: /Cancelar/i }),
      (p) => p.getByText(/^Cancelar$/i)
    ], "Cancelar button disappeared before optional cleanup.");
    await cancelButton.click();
    await waitForUiLoad(page);
  });

  await runStep("Administrar Negocios view", async () => {
    let manageBusiness = await waitForAnyVisible(page, [
      (p) => p.getByRole("menuitem", { name: /Administrar Negocios/i }),
      (p) => p.getByRole("button", { name: /Administrar Negocios/i }),
      (p) => p.getByRole("link", { name: /Administrar Negocios/i }),
      (p) => p.getByText(/Administrar Negocios/i)
    ], 6000);

    if (!manageBusiness) {
      const miNegocioToggle = await assertVisible(page, [
        (p) => p.getByRole("button", { name: /Mi Negocio/i }),
        (p) => p.getByRole("link", { name: /Mi Negocio/i }),
        (p) => p.getByText(/Mi Negocio/i)
      ], "Mi Negocio toggle was not visible to re-open submenu.");
      await miNegocioToggle.click();
      await waitForUiLoad(page);

      manageBusiness = await assertVisible(page, [
        (p) => p.getByRole("menuitem", { name: /Administrar Negocios/i }),
        (p) => p.getByRole("button", { name: /Administrar Negocios/i }),
        (p) => p.getByRole("link", { name: /Administrar Negocios/i }),
        (p) => p.getByText(/Administrar Negocios/i)
      ], "Administrar Negocios option was not visible after re-opening menu.");
    }

    await manageBusiness.click();
    await waitForUiLoad(page);

    await assertVisible(page, [
      (p) => p.getByRole("heading", { name: /Informaci[oó]n General/i }),
      (p) => p.getByText(/Informaci[oó]n General/i)
    ], "Información General section was not visible.");

    await assertVisible(page, [
      (p) => p.getByRole("heading", { name: /Detalles de la Cuenta/i }),
      (p) => p.getByText(/Detalles de la Cuenta/i)
    ], "Detalles de la Cuenta section was not visible.");

    await assertVisible(page, [
      (p) => p.getByRole("heading", { name: /Tus Negocios/i }),
      (p) => p.getByText(/Tus Negocios/i)
    ], "Tus Negocios section was not visible.");

    await assertVisible(page, [
      (p) => p.getByRole("heading", { name: /Secci[oó]n Legal/i }),
      (p) => p.getByText(/Secci[oó]n Legal/i)
    ], "Sección Legal section was not visible.");

    await screenshot(testInfo, page, "04-administrar-negocios", true);
  });

  await runStep("Información General", async () => {
    await assertVisible(page, [
      (p) => p.getByText(/Informaci[oó]n General/i)
    ], "Información General section title was missing.");

    await assertVisible(page, [
      (p) => p.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
    ], "User email was not visible.");

    await assertVisible(page, [
      (p) => p.getByText(/BUSINESS PLAN/i)
    ], "Text 'BUSINESS PLAN' was not visible.");

    await assertVisible(page, [
      (p) => p.getByRole("button", { name: /Cambiar Plan/i }),
      (p) => p.getByText(/Cambiar Plan/i)
    ], "Button 'Cambiar Plan' was not visible.");

    await assertVisible(page, [
      (p) => p.getByText(/Nombre|Usuario|Perfil/i),
      (p) => p.locator("[data-testid*=name],[class*=name]")
    ], "User name marker was not visible.");
  });

  await runStep("Detalles de la Cuenta", async () => {
    await assertVisible(page, [
      (p) => p.getByText(/Cuenta creada/i)
    ], "'Cuenta creada' label was not visible.");

    await assertVisible(page, [
      (p) => p.getByText(/Estado activo|Activo/i)
    ], "'Estado activo' label/value was not visible.");

    await assertVisible(page, [
      (p) => p.getByText(/Idioma seleccionado|Idioma/i)
    ], "'Idioma seleccionado' label/value was not visible.");
  });

  await runStep("Tus Negocios", async () => {
    await assertVisible(page, [
      (p) => p.getByText(/Tus Negocios/i)
    ], "Tus Negocios section title was not visible.");

    await assertVisible(page, [
      (p) => p.getByRole("button", { name: /Agregar Negocio/i }),
      (p) => p.getByText(/Agregar Negocio/i)
    ], "Agregar Negocio button in business list was not visible.");

    await assertVisible(page, [
      (p) => p.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)
    ], "Text 'Tienes 2 de 3 negocios' was not visible in Tus Negocios.");

    await assertVisible(page, [
      (p) => p.locator("ul li, table tbody tr, [role=row]").first(),
      (p) => p.getByText(/negocio/i)
    ], "Business list content was not visible.");
  });

  const openAndValidateLegalPage = async ({ linkPattern, headingPattern, screenshotName, reportField }) => {
    const appUrlBefore = page.url();
    const legalLink = await assertVisible(page, [
      (p) => p.getByRole("link", { name: linkPattern }),
      (p) => p.getByText(linkPattern)
    ], `${reportField}: legal link was not visible.`);

    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await legalLink.click();

    let targetPage = await popupPromise;
    if (!targetPage) {
      targetPage = page;
    }

    await waitForUiLoad(targetPage);

    await assertVisible(targetPage, [
      (p) => p.getByRole("heading", { name: headingPattern }),
      (p) => p.getByText(headingPattern)
    ], `${reportField}: expected heading was not visible.`);

    await assertVisible(targetPage, [
      (p) => p.locator("main p"),
      (p) => p.locator("article p"),
      (p) => p.locator("p")
    ], `${reportField}: legal content text was not visible.`);

    await screenshot(testInfo, targetPage, screenshotName, true);
    console.log(`[LEGAL_URL] ${reportField}: ${targetPage.url()}`);

    if (targetPage !== page) {
      await targetPage.close();
      await page.bringToFront();
      await waitForUiLoad(page);
      return;
    }

    if (page.url() !== appUrlBefore) {
      await page.goBack().catch(() => {});
      await waitForUiLoad(page);
    }
  };

  await runStep("Términos y Condiciones", async () => {
    await openAndValidateLegalPage({
      linkPattern: /T[eé]rminos y Condiciones/i,
      headingPattern: /T[eé]rminos y Condiciones/i,
      screenshotName: "08-terminos-y-condiciones",
      reportField: "Términos y Condiciones"
    });
  });

  await runStep("Política de Privacidad", async () => {
    await openAndValidateLegalPage({
      linkPattern: /Pol[ií]tica de Privacidad/i,
      headingPattern: /Pol[ií]tica de Privacidad/i,
      screenshotName: "09-politica-de-privacidad",
      reportField: "Política de Privacidad"
    });
  });

  console.log("=== FINAL REPORT ===");
  for (const field of REPORT_FIELDS) {
    console.log(`${field}: ${report[field]}`);
  }

  expect(Object.values(report)).not.toContain("FAIL");
});
