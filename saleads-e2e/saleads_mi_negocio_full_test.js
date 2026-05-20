const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const REQUIRED_FIELDS = [
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

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toRegex(text) {
  return new RegExp(escapeRegex(text), "i");
}

async function existsVisible(locator) {
  try {
    const count = await locator.count();
    if (count < 1) {
      return false;
    }
    return await locator.first().isVisible();
  } catch {
    return false;
  }
}

async function firstVisible(candidates) {
  for (const locator of candidates) {
    if (await existsVisible(locator)) {
      return locator.first();
    }
  }
  return null;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
  try {
    await page.waitForLoadState("networkidle", { timeout: 5000 });
  } catch {
    // Some views keep polling and never become fully idle.
  }
}

async function clickVisible(page, labelOptions) {
  const candidates = [];
  for (const label of labelOptions) {
    const matcher = toRegex(label);
    candidates.push(page.getByRole("button", { name: matcher }));
    candidates.push(page.getByRole("link", { name: matcher }));
    candidates.push(page.getByRole("menuitem", { name: matcher }));
    candidates.push(page.getByText(matcher));
  }
  const target = await firstVisible(candidates);
  if (!target) {
    throw new Error(`No visible element found for: ${labelOptions.join(", ")}`);
  }
  await target.click();
  await waitForUi(page);
}

async function assertAnyVisible(message, candidates) {
  const visible = await firstVisible(candidates);
  if (!visible) {
    throw new Error(message);
  }
}

async function main() {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const outputDir = path.join(__dirname, "artifacts", timestamp);
  await fs.mkdir(outputDir, { recursive: true });

  const report = {
    name: TEST_NAME,
    executedAt: new Date().toISOString(),
    statusByField: Object.fromEntries(
      REQUIRED_FIELDS.map((field) => [field, { result: "FAIL", detail: "Not executed" }]),
    ),
    artifacts: {
      screenshotsDir: outputDir,
      termsUrl: null,
      privacyUrl: null,
    },
  };

  let browser;
  let context;
  let appPage;

  const startUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || "";
  const wsEndpoint = process.env.PLAYWRIGHT_WS_ENDPOINT || "";

  try {
    if (wsEndpoint) {
      browser = await chromium.connectOverCDP(wsEndpoint);
      context = browser.contexts()[0] || (await browser.newContext());
      appPage = context.pages()[0] || (await context.newPage());
      await appPage.bringToFront();
      await waitForUi(appPage);
    } else {
      browser = await chromium.launch({
        headless: process.env.HEADLESS !== "false",
      });
      context = await browser.newContext();
      appPage = await context.newPage();
      if (!startUrl) {
        throw new Error(
          "Missing SALEADS_LOGIN_URL (or BASE_URL). This test is URL-agnostic but needs a runtime login URL.",
        );
      }
      await appPage.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(appPage);
    }

    // 1) Login with Google
    try {
      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickVisible(appPage, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Acceder con Google",
        "Google",
      ]);

      const googlePage = await popupPromise;
      if (googlePage) {
        await googlePage.waitForLoadState("domcontentloaded");
        const account = await firstVisible([
          googlePage.getByText("juanlucasbarbiergarzon@gmail.com"),
          googlePage.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
          googlePage.getByRole("link", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
        ]);
        if (account) {
          await account.click();
          await waitForUi(googlePage);
        }
        await appPage.bringToFront();
        await waitForUi(appPage);
      } else {
        const accountOnSamePage = await firstVisible([
          appPage.getByText("juanlucasbarbiergarzon@gmail.com"),
          appPage.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
          appPage.getByRole("link", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
        ]);
        if (accountOnSamePage) {
          await accountOnSamePage.click();
          await waitForUi(appPage);
        }
      }

      await assertAnyVisible("Main app interface not detected after login.", [
        appPage.locator("aside"),
        appPage.getByRole("navigation"),
        appPage.getByText(/Mi Negocio|Negocio/i),
      ]);

      await assertAnyVisible("Left sidebar navigation is not visible.", [
        appPage.locator("aside"),
        appPage.getByRole("navigation"),
      ]);

      await appPage.screenshot({
        path: path.join(outputDir, "01-dashboard-loaded.png"),
        fullPage: true,
      });
      report.statusByField["Login"] = { result: "PASS", detail: "Dashboard and sidebar are visible." };
    } catch (error) {
      report.statusByField["Login"] = { result: "FAIL", detail: error.message };
    }

    // 2) Open Mi Negocio menu
    try {
      await clickVisible(appPage, ["Negocio"]);
      await clickVisible(appPage, ["Mi Negocio"]);

      await assertAnyVisible("'Agregar Negocio' not visible in expanded menu.", [
        appPage.getByRole("link", { name: /Agregar Negocio/i }),
        appPage.getByRole("button", { name: /Agregar Negocio/i }),
        appPage.getByText(/Agregar Negocio/i),
      ]);
      await assertAnyVisible("'Administrar Negocios' not visible in expanded menu.", [
        appPage.getByRole("link", { name: /Administrar Negocios/i }),
        appPage.getByRole("button", { name: /Administrar Negocios/i }),
        appPage.getByText(/Administrar Negocios/i),
      ]);

      await appPage.screenshot({
        path: path.join(outputDir, "02-mi-negocio-menu-expanded.png"),
        fullPage: true,
      });
      report.statusByField["Mi Negocio menu"] = {
        result: "PASS",
        detail: "Mi Negocio menu expanded and expected options are visible.",
      };
    } catch (error) {
      report.statusByField["Mi Negocio menu"] = { result: "FAIL", detail: error.message };
    }

    // 3) Validate Agregar Negocio modal
    try {
      await clickVisible(appPage, ["Agregar Negocio"]);

      await assertAnyVisible("Modal title 'Crear Nuevo Negocio' is not visible.", [
        appPage.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
        appPage.getByText(/Crear Nuevo Negocio/i),
      ]);
      await assertAnyVisible("Input field 'Nombre del Negocio' is missing.", [
        appPage.getByLabel(/Nombre del Negocio/i),
        appPage.getByPlaceholder(/Nombre del Negocio/i),
        appPage.getByRole("textbox", { name: /Nombre del Negocio/i }),
      ]);
      await assertAnyVisible("Business limit text is not visible.", [
        appPage.getByText(/Tienes 2 de 3 negocios/i),
      ]);
      await assertAnyVisible("Button 'Cancelar' is missing in modal.", [
        appPage.getByRole("button", { name: /Cancelar/i }),
      ]);
      await assertAnyVisible("Button 'Crear Negocio' is missing in modal.", [
        appPage.getByRole("button", { name: /Crear Negocio/i }),
      ]);

      const nameField = await firstVisible([
        appPage.getByLabel(/Nombre del Negocio/i),
        appPage.getByPlaceholder(/Nombre del Negocio/i),
        appPage.getByRole("textbox", { name: /Nombre del Negocio/i }),
      ]);
      if (nameField) {
        await nameField.click();
        await waitForUi(appPage);
        await nameField.fill("Negocio Prueba Automatización");
      }
      await appPage.screenshot({
        path: path.join(outputDir, "03-agregar-negocio-modal.png"),
        fullPage: true,
      });
      await clickVisible(appPage, ["Cancelar"]);

      report.statusByField["Agregar Negocio modal"] = {
        result: "PASS",
        detail: "Modal fields, usage text and controls validated.",
      };
    } catch (error) {
      report.statusByField["Agregar Negocio modal"] = { result: "FAIL", detail: error.message };
    }

    // 4) Open Administrar Negocios
    try {
      if (!(await existsVisible(appPage.getByText(/Administrar Negocios/i)))) {
        await clickVisible(appPage, ["Mi Negocio"]);
      }
      await clickVisible(appPage, ["Administrar Negocios"]);

      await assertAnyVisible("'Información General' section not found.", [
        appPage.getByRole("heading", { name: /Información General/i }),
        appPage.getByText(/Información General/i),
      ]);
      await assertAnyVisible("'Detalles de la Cuenta' section not found.", [
        appPage.getByRole("heading", { name: /Detalles de la Cuenta/i }),
        appPage.getByText(/Detalles de la Cuenta/i),
      ]);
      await assertAnyVisible("'Tus Negocios' section not found.", [
        appPage.getByRole("heading", { name: /Tus Negocios/i }),
        appPage.getByText(/Tus Negocios/i),
      ]);
      await assertAnyVisible("'Sección Legal' section not found.", [
        appPage.getByRole("heading", { name: /Sección Legal/i }),
        appPage.getByText(/Sección Legal/i),
      ]);

      await appPage.screenshot({
        path: path.join(outputDir, "04-administrar-negocios-page.png"),
        fullPage: true,
      });
      report.statusByField["Administrar Negocios view"] = {
        result: "PASS",
        detail: "Account view loaded with all required sections.",
      };
    } catch (error) {
      report.statusByField["Administrar Negocios view"] = { result: "FAIL", detail: error.message };
    }

    // 5) Validate Información General
    try {
      await assertAnyVisible("User email not visible in 'Información General'.", [
        appPage.getByText(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/),
      ]);
      await assertAnyVisible("Expected business plan label is missing.", [
        appPage.getByText(/BUSINESS PLAN/i),
      ]);
      await assertAnyVisible("'Cambiar Plan' button is missing.", [
        appPage.getByRole("button", { name: /Cambiar Plan/i }),
        appPage.getByRole("link", { name: /Cambiar Plan/i }),
        appPage.getByText(/Cambiar Plan/i),
      ]);
      await assertAnyVisible("User name not visible in 'Información General'.", [
        appPage.getByText(/Juan|Lucas|Barbier|Garzon/i),
        appPage.getByText(/Nombre/i),
      ]);
      report.statusByField["Información General"] = {
        result: "PASS",
        detail: "Name/email/plan data and action button are visible.",
      };
    } catch (error) {
      report.statusByField["Información General"] = { result: "FAIL", detail: error.message };
    }

    // 6) Validate Detalles de la Cuenta
    try {
      await assertAnyVisible("'Cuenta creada' not found.", [appPage.getByText(/Cuenta creada/i)]);
      await assertAnyVisible("'Estado activo' not found.", [appPage.getByText(/Estado activo/i)]);
      await assertAnyVisible("'Idioma seleccionado' not found.", [
        appPage.getByText(/Idioma seleccionado/i),
      ]);
      report.statusByField["Detalles de la Cuenta"] = {
        result: "PASS",
        detail: "Account detail labels are visible.",
      };
    } catch (error) {
      report.statusByField["Detalles de la Cuenta"] = { result: "FAIL", detail: error.message };
    }

    // 7) Validate Tus Negocios
    try {
      await assertAnyVisible("'Tus Negocios' section is not visible.", [
        appPage.getByRole("heading", { name: /Tus Negocios/i }),
        appPage.getByText(/Tus Negocios/i),
      ]);
      await assertAnyVisible("'Agregar Negocio' button missing in business section.", [
        appPage.getByRole("button", { name: /Agregar Negocio/i }),
        appPage.getByRole("link", { name: /Agregar Negocio/i }),
        appPage.getByText(/Agregar Negocio/i),
      ]);
      await assertAnyVisible("Business quota text is missing in business section.", [
        appPage.getByText(/Tienes 2 de 3 negocios/i),
      ]);
      await assertAnyVisible("Business list is not visible.", [
        appPage.locator("ul, table, [role='list'], [role='table']").filter({
          hasText: /Negocio/i,
        }),
      ]);
      report.statusByField["Tus Negocios"] = {
        result: "PASS",
        detail: "Business section, controls and quota text validated.",
      };
    } catch (error) {
      report.statusByField["Tus Negocios"] = { result: "FAIL", detail: error.message };
    }

    async function validateLegalLink({
      linkText,
      headingText,
      screenshotName,
      reportField,
      urlField,
    }) {
      await appPage.bringToFront();
      await waitForUi(appPage);

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickVisible(appPage, [linkText]);

      const popup = await popupPromise;
      const targetPage = popup || appPage;
      await targetPage.waitForLoadState("domcontentloaded");
      await waitForUi(targetPage);

      await assertAnyVisible(`Heading '${headingText}' not found.`, [
        targetPage.getByRole("heading", { name: toRegex(headingText) }),
        targetPage.getByText(toRegex(headingText)),
      ]);

      const bodyText = await targetPage.locator("body").innerText();
      if (!bodyText || bodyText.trim().length < 120) {
        throw new Error(`Legal content for '${headingText}' appears empty or too short.`);
      }

      report.artifacts[urlField] = targetPage.url();
      await targetPage.screenshot({
        path: path.join(outputDir, screenshotName),
        fullPage: true,
      });

      if (popup) {
        await popup.close();
      } else {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(appPage);
      }

      report.statusByField[reportField] = {
        result: "PASS",
        detail: `${headingText} opened successfully (${report.artifacts[urlField]}).`,
      };
    }

    // 8) Validate Términos y Condiciones
    try {
      await validateLegalLink({
        linkText: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        screenshotName: "08-terminos-y-condiciones.png",
        reportField: "Términos y Condiciones",
        urlField: "termsUrl",
      });
    } catch (error) {
      report.statusByField["Términos y Condiciones"] = { result: "FAIL", detail: error.message };
    }

    // 9) Validate Política de Privacidad
    try {
      await validateLegalLink({
        linkText: "Política de Privacidad",
        headingText: "Política de Privacidad",
        screenshotName: "09-politica-de-privacidad.png",
        reportField: "Política de Privacidad",
        urlField: "privacyUrl",
      });
    } catch (error) {
      report.statusByField["Política de Privacidad"] = { result: "FAIL", detail: error.message };
    }
  } catch (fatalError) {
    for (const field of REQUIRED_FIELDS) {
      if (report.statusByField[field].detail === "Not executed") {
        report.statusByField[field] = { result: "FAIL", detail: `Fatal setup error: ${fatalError.message}` };
      }
    }
  } finally {
    const allPass = REQUIRED_FIELDS.every((field) => report.statusByField[field].result === "PASS");
    report.overallResult = allPass ? "PASS" : "FAIL";

    const reportPath = path.join(outputDir, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    console.log(`Test report written to: ${reportPath}`);
    console.log(JSON.stringify(report, null, 2));

    if (browser) {
      await browser.close();
    }

    if (!allPass) {
      process.exitCode = 1;
    }
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
