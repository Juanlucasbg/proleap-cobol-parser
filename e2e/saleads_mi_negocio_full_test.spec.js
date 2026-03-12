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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function createLocatorCandidates(page, text) {
  return [
    page.getByRole("button", { name: new RegExp(`^\\s*${text}\\s*$`, "i") }).first(),
    page.getByRole("link", { name: new RegExp(`^\\s*${text}\\s*$`, "i") }).first(),
    page.getByRole("menuitem", { name: new RegExp(`^\\s*${text}\\s*$`, "i") }).first(),
    page.getByRole("tab", { name: new RegExp(`^\\s*${text}\\s*$`, "i") }).first(),
    page.getByText(new RegExp(`^\\s*${text}\\s*$`, "i")).first(),
    page.locator(`button:has-text("${text}")`).first(),
    page.locator(`a:has-text("${text}")`).first()
  ];
}

async function firstVisibleLocator(locators, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await locators[0].page().waitForTimeout(250);
  }

  return null;
}

async function clickText(page, texts) {
  for (const text of texts) {
    const locator = await firstVisibleLocator(createLocatorCandidates(page, text), 3000);
    if (locator) {
      await locator.scrollIntoViewIfNeeded().catch(() => {});
      await locator.click({ timeout: 10000 });
      await waitForUiToSettle(page);
      return { clicked: true, text };
    }
  }

  return { clicked: false, text: null };
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 4000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function runStep(report, stepName, fn, failures) {
  try {
    await fn();
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = "FAIL";
    failures.push({
      step: stepName,
      message: error instanceof Error ? error.message : String(error)
    });
  }
}

async function screenshot(page, testInfo, name, fullPage = false) {
  const path = testInfo.outputPath(name);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function ensureOnLoginPage(page) {
  const configuredStartUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.BASE_URL ||
    process.env.PLAYWRIGHT_TEST_BASE_URL;

  if (configuredStartUrl) {
    await page.goto(configuredStartUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "Browser is on about:blank. Open the SaleADS login page first or set SALEADS_LOGIN_URL/BASE_URL."
    );
  }
}

async function findSidebar(page) {
  const candidates = [
    page.locator("aside").first(),
    page.getByRole("navigation").first(),
    page.locator('[class*="sidebar"], [id*="sidebar"]').first()
  ];
  return firstVisibleLocator(candidates, 15000);
}

async function openLegalLinkAndValidate({
  page,
  linkLabel,
  expectedHeading,
  screenshotName,
  finalUrls,
  testInfo
}) {
  const link = await firstVisibleLocator(createLocatorCandidates(page, linkLabel), 12000);
  if (!link) {
    throw new Error(`Could not find legal link '${linkLabel}'.`);
  }

  const appUrlBefore = page.url();
  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);

  await link.scrollIntoViewIfNeeded().catch(() => {});
  await link.click({ timeout: 10000 });

  const popup = await popupPromise;
  const legalPage = popup || page;
  await waitForUiToSettle(legalPage);

  const heading = legalPage
    .getByRole("heading", { name: new RegExp(expectedHeading, "i") })
    .first();
  const headingByText = legalPage.getByText(new RegExp(expectedHeading, "i")).first();

  const headingVisible =
    (await heading.isVisible().catch(() => false)) ||
    (await headingByText.isVisible().catch(() => false));

  if (!headingVisible) {
    throw new Error(`Heading '${expectedHeading}' was not visible.`);
  }

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  if (bodyText.length < 120) {
    throw new Error(`Legal content for '${linkLabel}' looks too short.`);
  }

  finalUrls[linkLabel] = legalPage.url();
  await screenshot(legalPage, testInfo, screenshotName, true);

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    });
  }

  await waitForUiToSettle(page);
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const failures = [];
  const finalUrls = {};

  await runStep(
    report,
    "Login",
    async () => {
      await ensureOnLoginPage(page);

      const sidebarBeforeLogin = await findSidebar(page);

      if (!sidebarBeforeLogin) {
        const loginClickResult = await clickText(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Ingresar con Google",
          "Continuar con Google",
          "Google"
        ]);

        if (!loginClickResult.clicked) {
          throw new Error("Could not locate a login or Google sign-in button.");
        }

        const popup = await page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
        const googlePage = popup || page;

        const accountEntry = await firstVisibleLocator(
          createLocatorCandidates(googlePage, GOOGLE_ACCOUNT_EMAIL),
          12000
        );

        if (accountEntry) {
          await accountEntry.click({ timeout: 10000 });
          await waitForUiToSettle(googlePage);
        }

        if (popup) {
          await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
        }
      }

      const sidebar = await findSidebar(page);
      if (!sidebar) {
        throw new Error("Main app did not load: left sidebar navigation not visible.");
      }

      await expect(sidebar).toBeVisible();
      await screenshot(page, testInfo, "01-dashboard-loaded.png", true);
    },
    failures
  );

  await runStep(
    report,
    "Mi Negocio menu",
    async () => {
      const sidebar = await findSidebar(page);
      if (!sidebar) {
        throw new Error("Sidebar not found before opening Mi Negocio.");
      }

      await clickText(page, ["Negocio"]);

      const miNegocio = await firstVisibleLocator(createLocatorCandidates(page, "Mi Negocio"), 12000);
      if (!miNegocio) {
        throw new Error("Option 'Mi Negocio' was not found.");
      }

      await miNegocio.click({ timeout: 10000 });
      await waitForUiToSettle(page);

      const agregarNegocio = await firstVisibleLocator(
        createLocatorCandidates(page, "Agregar Negocio"),
        12000
      );
      const administrarNegocios = await firstVisibleLocator(
        createLocatorCandidates(page, "Administrar Negocios"),
        12000
      );

      if (!agregarNegocio) {
        throw new Error("'Agregar Negocio' is not visible after expanding menu.");
      }
      if (!administrarNegocios) {
        throw new Error("'Administrar Negocios' is not visible after expanding menu.");
      }

      await screenshot(page, testInfo, "02-mi-negocio-menu-expanded.png");
    },
    failures
  );

  await runStep(
    report,
    "Agregar Negocio modal",
    async () => {
      const agregarNegocio = await firstVisibleLocator(
        createLocatorCandidates(page, "Agregar Negocio"),
        12000
      );
      if (!agregarNegocio) {
        throw new Error("'Agregar Negocio' button not found.");
      }

      await agregarNegocio.click({ timeout: 10000 });
      await waitForUiToSettle(page);

      const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
      await expect(modalTitle).toBeVisible();

      const nombreInput = page.getByLabel(/Nombre del Negocio/i).first();
      if (!(await nombreInput.isVisible().catch(() => false))) {
        const placeholderInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
        await expect(placeholderInput).toBeVisible();
        await placeholderInput.click();
        await placeholderInput.fill("Negocio Prueba Automatización");
      } else {
        await nombreInput.click();
        await nombreInput.fill("Negocio Prueba Automatización");
      }

      const quotaText = page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first();
      await expect(quotaText).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

      await screenshot(page, testInfo, "03-agregar-negocio-modal.png");
      await clickText(page, ["Cancelar"]);
    },
    failures
  );

  await runStep(
    report,
    "Administrar Negocios view",
    async () => {
      await clickText(page, ["Mi Negocio"]);

      const administrarNegocios = await firstVisibleLocator(
        createLocatorCandidates(page, "Administrar Negocios"),
        12000
      );
      if (!administrarNegocios) {
        throw new Error("'Administrar Negocios' option not found.");
      }

      await administrarNegocios.click({ timeout: 10000 });
      await waitForUiToSettle(page);

      await expect(page.getByText(/Información General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

      await screenshot(page, testInfo, "04-administrar-negocios-view-full.png", true);
    },
    failures
  );

  await runStep(
    report,
    "Información General",
    async () => {
      const infoSection = page
        .locator("section,div")
        .filter({ has: page.getByText(/Información General/i).first() })
        .first();

      await expect(infoSection).toBeVisible();

      const sectionText = await infoSection.innerText();
      if (!/@/.test(sectionText)) {
        throw new Error("No visible user email found in 'Información General'.");
      }

      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    },
    failures
  );

  await runStep(
    report,
    "Detalles de la Cuenta",
    async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    },
    failures
  );

  await runStep(
    report,
    "Tus Negocios",
    async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

      const businessesContainer = page
        .locator("section,div")
        .filter({ has: page.getByText(/Tus Negocios/i).first() })
        .first();
      const hasVisibleContent =
        ((await businessesContainer.textContent()) || "").replace(/\s+/g, " ").trim().length > 20;

      if (!hasVisibleContent) {
        throw new Error("Business list content was not visible.");
      }
    },
    failures
  );

  await runStep(
    report,
    "Términos y Condiciones",
    async () => {
      await openLegalLinkAndValidate({
        page,
        linkLabel: "Términos y Condiciones",
        expectedHeading: "Términos y Condiciones",
        screenshotName: "05-terminos-y-condiciones.png",
        finalUrls,
        testInfo
      });
    },
    failures
  );

  await runStep(
    report,
    "Política de Privacidad",
    async () => {
      await openLegalLinkAndValidate({
        page,
        linkLabel: "Política de Privacidad",
        expectedHeading: "Política de Privacidad",
        screenshotName: "06-politica-de-privacidad.png",
        finalUrls,
        testInfo
      });
    },
    failures
  );

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    resultByStep: report,
    legalUrls: finalUrls,
    failures
  };

  const finalReportText = JSON.stringify(finalReport, null, 2);
  await testInfo.attach("final-report.json", {
    body: Buffer.from(finalReportText, "utf-8"),
    contentType: "application/json"
  });
  // eslint-disable-next-line no-console
  console.log(finalReportText);

  expect(failures, `Validation failures:\n${finalReportText}`).toEqual([]);
});
