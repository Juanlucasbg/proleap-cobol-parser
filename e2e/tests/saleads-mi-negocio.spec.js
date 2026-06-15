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

function makeReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = { status: "FAIL", details: "Not executed" };
  }
  return report;
}

function toRegex(pattern) {
  if (pattern instanceof RegExp) {
    return pattern;
  }
  return new RegExp(pattern, "i");
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(750);
}

async function firstVisibleLocator(locators) {
  for (const locator of locators) {
    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  return null;
}

async function clickVisibleText(scope, page, patterns, description, waitAfterClick = true) {
  const regexes = patterns.map(toRegex);

  for (const regex of regexes) {
    const candidate = await firstVisibleLocator([
      scope.getByRole("button", { name: regex }),
      scope.getByRole("link", { name: regex }),
      scope.getByRole("menuitem", { name: regex }),
      scope.getByRole("tab", { name: regex }),
      scope.getByText(regex)
    ]);

    if (candidate) {
      await candidate.click();
      if (waitAfterClick) {
        await waitForUi(page);
      }
      return;
    }
  }

  throw new Error(`Unable to find clickable element for: ${description}`);
}

async function expectVisibleText(scope, patterns, description) {
  const regexes = patterns.map(toRegex);

  for (const regex of regexes) {
    const candidate = await firstVisibleLocator([
      scope.getByRole("heading", { name: regex }),
      scope.getByRole("button", { name: regex }),
      scope.getByRole("link", { name: regex }),
      scope.getByText(regex)
    ]);

    if (candidate) {
      await expect(candidate, description).toBeVisible();
      return;
    }
  }

  throw new Error(`Unable to validate visible text for: ${description}`);
}

async function maybeSelectGoogleAccount(context) {
  const accountRegex = new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i");
  const endAt = Date.now() + 25000;

  while (Date.now() < endAt) {
    for (const candidatePage of context.pages()) {
      const accountOption = candidatePage.getByText(accountRegex).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitForUi(candidatePage);
        return true;
      }
    }
    await context.pages()[0].waitForTimeout(500);
  }

  return false;
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function validateLegalDocument({
  context,
  appPage,
  testInfo,
  linkTextPatterns,
  headingPatterns,
  screenshotName,
  reportLabel
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);

  await clickVisibleText(appPage, appPage, linkTextPatterns, `Click ${reportLabel}`, false);

  const popup = await popupPromise;
  const targetPage = popup || appPage;

  await waitForUi(targetPage);
  await expectVisibleText(targetPage, headingPatterns, `${reportLabel} heading`);

  const legalContent = targetPage.locator("main, article, body").first();
  await expect(legalContent, `${reportLabel} content container`).toBeVisible();

  const finalUrl = targetPage.url();
  console.log(`[EVIDENCE] ${reportLabel} URL: ${finalUrl}`);

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = makeReport();
  const legalUrls = {};

  const loginUrl = process.env.SALEADS_URL || process.env.BASE_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await test.step("1) Login with Google", async () => {
    try {
      await clickVisibleText(
        page,
        page,
        [/Sign in with Google/i, /Iniciar sesi[oó]n con Google/i, /Google/i],
        "Login with Google"
      );

      await maybeSelectGoogleAccount(context);

      await expectVisibleText(page, [/Negocio/i, /Mi Negocio/i], "Main app interface");
      const sidebar = page.locator("aside, nav").first();
      await expect(sidebar, "Left sidebar navigation").toBeVisible();

      await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
      report["Login"] = { status: "PASS", details: "Dashboard and sidebar loaded." };
    } catch (error) {
      report["Login"] = { status: "FAIL", details: error.message };
    }
  });

  await test.step("2) Open Mi Negocio menu", async () => {
    try {
      await clickVisibleText(page, page, [/Negocio/i], "Negocio section");
      await clickVisibleText(page, page, [/Mi Negocio/i], "Mi Negocio option");

      await expectVisibleText(page, [/Agregar Negocio/i], "Agregar Negocio visible");
      await expectVisibleText(page, [/Administrar Negocios/i], "Administrar Negocios visible");

      await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", true);
      report["Mi Negocio menu"] = { status: "PASS", details: "Menu expanded with both options visible." };
    } catch (error) {
      report["Mi Negocio menu"] = { status: "FAIL", details: error.message };
    }
  });

  await test.step("3) Validate Agregar Negocio modal", async () => {
    try {
      await clickVisibleText(page, page, [/Agregar Negocio/i], "Agregar Negocio option");

      const modal = page
        .locator('[role="dialog"], div')
        .filter({ hasText: /Crear Nuevo Negocio/i })
        .first();
      await expect(modal, "Crear Nuevo Negocio modal").toBeVisible();

      await expectVisibleText(modal, [/Crear Nuevo Negocio/i], "Modal title");
      await expectVisibleText(modal, [/Nombre del Negocio/i], "Nombre del Negocio field");
      await expectVisibleText(modal, [/Tienes\s+2\s+de\s+3\s+negocios/i], "Business quota text");
      await expectVisibleText(modal, [/Cancelar/i], "Cancelar button");
      await expectVisibleText(modal, [/Crear Negocio/i], "Crear Negocio button");

      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", true);

      const businessNameField = await firstVisibleLocator([
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
        modal.locator('input[type="text"]')
      ]);

      if (businessNameField) {
        await businessNameField.click();
        await businessNameField.fill("Negocio Prueba Automatización");
      }

      await clickVisibleText(modal, page, [/Cancelar/i], "Cerrar modal");
      report["Agregar Negocio modal"] = { status: "PASS", details: "Modal validated and closed successfully." };
    } catch (error) {
      report["Agregar Negocio modal"] = { status: "FAIL", details: error.message };
    }
  });

  await test.step("4) Open Administrar Negocios", async () => {
    try {
      await clickVisibleText(page, page, [/Mi Negocio/i], "Re-open Mi Negocio if collapsed");
      await clickVisibleText(page, page, [/Administrar Negocios/i], "Administrar Negocios");

      await expectVisibleText(page, [/Informaci[oó]n General/i], "Información General section");
      await expectVisibleText(page, [/Detalles de la Cuenta/i], "Detalles de la Cuenta section");
      await expectVisibleText(page, [/Tus Negocios/i], "Tus Negocios section");
      await expectVisibleText(page, [/Secci[oó]n Legal/i], "Sección Legal section");

      await captureCheckpoint(page, testInfo, "04-administrar-negocios-account-page.png", true);
      report["Administrar Negocios view"] = { status: "PASS", details: "All account sections are visible." };
    } catch (error) {
      report["Administrar Negocios view"] = { status: "FAIL", details: error.message };
    }
  });

  await test.step("5) Validate Información General", async () => {
    try {
      const visibleEmail = page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();
      await expect(visibleEmail, "User email is visible").toBeVisible();

      await expectVisibleText(
        page,
        [/Juan/i, /Nombre/i, /Usuario/i],
        "User name or user-name label is visible"
      );
      await expectVisibleText(page, [/BUSINESS PLAN/i], "BUSINESS PLAN text");
      await expectVisibleText(page, [/Cambiar Plan/i], "Cambiar Plan button");

      report["Información General"] = { status: "PASS", details: "Name, email, plan and action button validated." };
    } catch (error) {
      report["Información General"] = { status: "FAIL", details: error.message };
    }
  });

  await test.step("6) Validate Detalles de la Cuenta", async () => {
    try {
      await expectVisibleText(page, [/Cuenta creada/i], "Cuenta creada label");
      await expectVisibleText(page, [/Estado activo/i], "Estado activo label");
      await expectVisibleText(page, [/Idioma seleccionado/i], "Idioma seleccionado label");

      report["Detalles de la Cuenta"] = { status: "PASS", details: "Account details labels are visible." };
    } catch (error) {
      report["Detalles de la Cuenta"] = { status: "FAIL", details: error.message };
    }
  });

  await test.step("7) Validate Tus Negocios", async () => {
    try {
      await expectVisibleText(page, [/Tus Negocios/i], "Tus Negocios section title");
      await expectVisibleText(page, [/Agregar Negocio/i], "Agregar Negocio button in business section");
      await expectVisibleText(page, [/Tienes\s+2\s+de\s+3\s+negocios/i], "Business quota text");

      const businessEntries = page
        .locator("li, [role='row'], .card, [data-testid*='business'], button, a")
        .filter({ hasNotText: /Agregar Negocio/i });
      const businessEntriesCount = await businessEntries.count();
      if (businessEntriesCount < 1) {
        throw new Error("Business list entries are not visible.");
      }

      report["Tus Negocios"] = { status: "PASS", details: "Business list and limits validated." };
    } catch (error) {
      report["Tus Negocios"] = { status: "FAIL", details: error.message };
    }
  });

  await test.step("8) Validate Términos y Condiciones", async () => {
    try {
      legalUrls["Términos y Condiciones"] = await validateLegalDocument({
        context,
        appPage: page,
        testInfo,
        linkTextPatterns: [/T[eé]rminos y Condiciones/i],
        headingPatterns: [/T[eé]rminos y Condiciones/i],
        screenshotName: "08-terminos-y-condiciones.png",
        reportLabel: "Términos y Condiciones"
      });

      report["Términos y Condiciones"] = {
        status: "PASS",
        details: `Legal document opened successfully. URL: ${legalUrls["Términos y Condiciones"]}`
      };
    } catch (error) {
      report["Términos y Condiciones"] = { status: "FAIL", details: error.message };
    }
  });

  await test.step("9) Validate Política de Privacidad", async () => {
    try {
      legalUrls["Política de Privacidad"] = await validateLegalDocument({
        context,
        appPage: page,
        testInfo,
        linkTextPatterns: [/Pol[ií]tica de Privacidad/i],
        headingPatterns: [/Pol[ií]tica de Privacidad/i],
        screenshotName: "09-politica-de-privacidad.png",
        reportLabel: "Política de Privacidad"
      });

      report["Política de Privacidad"] = {
        status: "PASS",
        details: `Legal document opened successfully. URL: ${legalUrls["Política de Privacidad"]}`
      };
    } catch (error) {
      report["Política de Privacidad"] = { status: "FAIL", details: error.message };
    }
  });

  await test.step("10) Final Report", async () => {
    console.log("\n=== SaleADS Mi Negocio Validation Report ===");
    for (const field of REPORT_FIELDS) {
      console.log(`- ${field}: ${report[field].status} (${report[field].details})`);
    }

    const failures = REPORT_FIELDS.filter((field) => report[field].status !== "PASS");
    expect(
      failures,
      `Validation failures:\n${failures.map((field) => `${field}: ${report[field].details}`).join("\n")}`
    ).toEqual([]);
  });
});
