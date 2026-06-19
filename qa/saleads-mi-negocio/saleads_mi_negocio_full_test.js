const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

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

function newReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: "Not executed." };
    return acc;
  }, {});
}

function normalizeText(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1200);
}

async function ensureVisible(locator, timeoutMs = 15000) {
  await locator.first().waitFor({ state: "visible", timeout: timeoutMs });
}

async function takeScreenshot(page, outputDir, fileName, fullPage = false) {
  const destination = path.join(outputDir, fileName);
  await page.screenshot({ path: destination, fullPage });
  return destination;
}

function byText(page, text, exact = false) {
  return page.getByText(text, { exact }).first();
}

async function clickVisibleText(page, candidates, opts = {}) {
  const timeoutMs = opts.timeoutMs ?? 12000;
  for (const candidate of candidates) {
    const locator = byText(page, candidate);
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      await waitForUi(page);
      return candidate;
    }
  }

  for (const candidate of candidates) {
    const locator = byText(page, candidate);
    if (await locator.count()) {
      await locator.first().click({ timeout: timeoutMs });
      await waitForUi(page);
      return candidate;
    }
  }

  throw new Error(`No clickable element found for candidates: ${candidates.join(", ")}`);
}

async function sectionByHeading(page, headingText) {
  const heading = page.getByText(headingText, { exact: false }).first();
  await ensureVisible(heading, 20000);
  const section = heading.locator("xpath=ancestor::*[self::section or self::div][1]").first();
  return section;
}

async function validateLegalPage(targetPage, headingText) {
  const headingLocator = targetPage
    .getByRole("heading", { name: new RegExp(headingText, "i") })
    .first();
  const fallbackHeading = targetPage.getByText(headingText, { exact: false }).first();

  const hasRoleHeading = await headingLocator.isVisible().catch(() => false);
  if (!hasRoleHeading) {
    await ensureVisible(fallbackHeading, 20000);
  }

  const contentLocator = targetPage.locator("p, li, article, section").first();
  await ensureVisible(contentLocator, 10000);
  const contentText = await targetPage.locator("body").innerText();

  if (!contentText || normalizeText(contentText).length < 80) {
    throw new Error(`${headingText}: legal content appears too short or empty.`);
  }
}

async function run() {
  const report = newReport();
  const outputDir = path.join(process.cwd(), "artifacts", new Date().toISOString().replace(/[:.]/g, "-"));
  await fs.mkdir(outputDir, { recursive: true });

  const metadata = {
    generatedAt: new Date().toISOString(),
    outputDir,
    legalUrls: {
      terminosYCondiciones: null,
      politicaDePrivacidad: null,
    },
    errors: [],
  };

  let browser;
  let context;
  let page;

  try {
    if (process.env.CHROME_CDP_URL) {
      browser = await chromium.connectOverCDP(process.env.CHROME_CDP_URL);
      context = browser.contexts()[0] || (await browser.newContext());
      page = context.pages()[0] || (await context.newPage());
    } else {
      browser = await chromium.launch({
        headless: process.env.HEADLESS !== "false",
        slowMo: Number(process.env.SLOW_MO_MS || 0),
      });
      context = await browser.newContext();
      page = await context.newPage();
    }

    const startUrl = process.env.SALEADS_START_URL;
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No starting page is available. Set SALEADS_START_URL or provide CHROME_CDP_URL with a browser already on the SaleADS login page."
      );
    }

    await waitForUi(page);

    // Step 1: Login with Google.
    try {
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesion con Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Google",
      ]);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const accountLocator = popup.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
        if (await accountLocator.isVisible().catch(() => false)) {
          await accountLocator.click();
          await popup.waitForLoadState("domcontentloaded");
        }
      }

      await waitForUi(page);
      const sidebarLocator = page.locator("aside, nav").first();
      const sidebarVisible = await sidebarLocator.isVisible().catch(() => false);
      const negocioVisible = await page.getByText("Negocio", { exact: false }).first().isVisible().catch(() => false);

      if (!sidebarVisible && !negocioVisible) {
        throw new Error("Main interface/left sidebar not visible after login.");
      }

      const shot = await takeScreenshot(page, outputDir, "01-dashboard-loaded.png");
      report["Login"] = { status: "PASS", details: `Dashboard loaded. Screenshot: ${shot}` };
    } catch (error) {
      report["Login"] = { status: "FAIL", details: String(error.message || error) };
    }

    // Step 2: Open Mi Negocio menu.
    try {
      await clickVisibleText(page, ["Negocio"]);
      await clickVisibleText(page, ["Mi Negocio"]);
      await ensureVisible(page.getByText("Agregar Negocio", { exact: false }).first());
      await ensureVisible(page.getByText("Administrar Negocios", { exact: false }).first());

      const shot = await takeScreenshot(page, outputDir, "02-mi-negocio-expanded-menu.png");
      report["Mi Negocio menu"] = {
        status: "PASS",
        details: `Mi Negocio menu expanded with expected options. Screenshot: ${shot}`,
      };
    } catch (error) {
      report["Mi Negocio menu"] = { status: "FAIL", details: String(error.message || error) };
    }

    // Step 3: Validate Agregar Negocio modal.
    try {
      await clickVisibleText(page, ["Agregar Negocio"]);

      await ensureVisible(page.getByText("Crear Nuevo Negocio", { exact: false }).first());
      await ensureVisible(page.getByText("Nombre del Negocio", { exact: false }).first());
      await ensureVisible(page.getByText("Tienes 2 de 3 negocios", { exact: false }).first());
      await ensureVisible(page.getByText("Cancelar", { exact: true }).first());
      await ensureVisible(page.getByText("Crear Negocio", { exact: false }).first());

      const input = page.getByPlaceholder("Nombre del Negocio").first();
      if (await input.isVisible().catch(() => false)) {
        await input.fill("Negocio Prueba Automatizacion");
      } else {
        const fallbackInput = page.locator("input").first();
        await fallbackInput.fill("Negocio Prueba Automatizacion");
      }

      const shot = await takeScreenshot(page, outputDir, "03-agregar-negocio-modal.png");
      await clickVisibleText(page, ["Cancelar"]);

      report["Agregar Negocio modal"] = {
        status: "PASS",
        details: `Modal validated and cancelled. Screenshot: ${shot}`,
      };
    } catch (error) {
      report["Agregar Negocio modal"] = { status: "FAIL", details: String(error.message || error) };
    }

    // Step 4: Open Administrar Negocios and validate account sections.
    try {
      const administrarVisible = await page
        .getByText("Administrar Negocios", { exact: false })
        .first()
        .isVisible()
        .catch(() => false);
      if (!administrarVisible) {
        await clickVisibleText(page, ["Mi Negocio", "Negocio"]);
      }

      await clickVisibleText(page, ["Administrar Negocios"]);
      const infoGeneralVisible =
        (await page.getByText("Información General", { exact: false }).first().isVisible().catch(() => false)) ||
        (await page.getByText("Informacion General", { exact: false }).first().isVisible().catch(() => false));
      if (!infoGeneralVisible) {
        throw new Error("No se encontro la seccion Informacion General.");
      }
      await ensureVisible(page.getByText("Detalles de la Cuenta", { exact: false }).first(), 30000);
      await ensureVisible(page.getByText("Tus Negocios", { exact: false }).first(), 30000);
      const seccionLegalVisible =
        (await page.getByText("Sección Legal", { exact: false }).first().isVisible().catch(() => false)) ||
        (await page.getByText("Seccion Legal", { exact: false }).first().isVisible().catch(() => false));
      if (!seccionLegalVisible) {
        throw new Error("No se encontro la seccion legal.");
      }

      const shot = await takeScreenshot(page, outputDir, "04-administrar-negocios-account-page.png", true);
      report["Administrar Negocios view"] = {
        status: "PASS",
        details: `Account page with all required sections is visible. Screenshot: ${shot}`,
      };
    } catch (error) {
      report["Administrar Negocios view"] = { status: "FAIL", details: String(error.message || error) };
    }

    // Step 5: Validate Informacion General.
    try {
      const section = (await page.getByText("Información General", { exact: false }).first().isVisible().catch(() => false))
        ? await sectionByHeading(page, "Información General")
        : await sectionByHeading(page, "Informacion General");
      const sectionText = normalizeText(await section.innerText());

      if (!/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/.test(sectionText)) {
        throw new Error("User email is not visible in Informacion General.");
      }
      if (!sectionText.includes("business plan")) {
        throw new Error("BUSINESS PLAN is not visible.");
      }
      if (!sectionText.includes("cambiar plan")) {
        throw new Error("Cambiar Plan button/text is not visible.");
      }
      const likelyName = sectionText
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean)
        .some(
          (line) =>
            /^[a-z][a-z .'-]{2,}$/.test(line) &&
            !line.includes("@") &&
            !line.includes("business plan") &&
            !line.includes("cambiar plan") &&
            !line.includes("informacion general")
        );
      if (!likelyName) {
        throw new Error("A user name-like value was not detected in Informacion General.");
      }

      report["Información General"] = {
        status: "PASS",
        details: "User name, email, BUSINESS PLAN and Cambiar Plan are visible.",
      };
    } catch (error) {
      report["Información General"] = { status: "FAIL", details: String(error.message || error) };
    }

    // Step 6: Validate Detalles de la Cuenta.
    try {
      const section = await sectionByHeading(page, "Detalles de la Cuenta");
      const sectionText = normalizeText(await section.innerText());
      if (!sectionText.includes("cuenta creada")) {
        throw new Error("'Cuenta creada' is not visible.");
      }
      if (!sectionText.includes("estado activo")) {
        throw new Error("'Estado activo' is not visible.");
      }
      if (!sectionText.includes("idioma seleccionado")) {
        throw new Error("'Idioma seleccionado' is not visible.");
      }
      report["Detalles de la Cuenta"] = {
        status: "PASS",
        details: "Cuenta creada, Estado activo e Idioma seleccionado are visible.",
      };
    } catch (error) {
      report["Detalles de la Cuenta"] = { status: "FAIL", details: String(error.message || error) };
    }

    // Step 7: Validate Tus Negocios.
    try {
      const section = await sectionByHeading(page, "Tus Negocios");
      const sectionText = normalizeText(await section.innerText());
      if (!sectionText.includes("agregar negocio")) {
        throw new Error("'Agregar Negocio' is not visible in Tus Negocios.");
      }
      if (!sectionText.includes("tienes 2 de 3 negocios")) {
        throw new Error("'Tienes 2 de 3 negocios' is not visible in Tus Negocios.");
      }
      const hasBusinessList = (await section.locator("li, article, .card, table tbody tr").count()) > 0;
      if (!hasBusinessList) {
        throw new Error("Business list/cards were not detected in Tus Negocios.");
      }
      report["Tus Negocios"] = {
        status: "PASS",
        details: "Business list, Agregar Negocio and usage text are visible.",
      };
    } catch (error) {
      report["Tus Negocios"] = { status: "FAIL", details: String(error.message || error) };
    }

    // Step 8: Validate Terminos y Condiciones.
    try {
      const appPage = page;
      const previousUrl = appPage.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickVisibleText(appPage, ["Términos y Condiciones", "Terminos y Condiciones"]);
      const popup = await popupPromise;
      const legalPage = popup || appPage;

      await legalPage.waitForLoadState("domcontentloaded");
      await validateLegalPage(legalPage, "Términos y Condiciones");

      const shot = await takeScreenshot(legalPage, outputDir, "08-terminos-y-condiciones.png", true);
      metadata.legalUrls.terminosYCondiciones = legalPage.url();

      if (popup) {
        await popup.close();
        await appPage.bringToFront();
      } else if (appPage.url() !== previousUrl) {
        await appPage.goBack({ waitUntil: "domcontentloaded" });
      }
      await waitForUi(appPage);

      report["Términos y Condiciones"] = {
        status: "PASS",
        details: `Legal page validated. URL: ${metadata.legalUrls.terminosYCondiciones}. Screenshot: ${shot}`,
      };
    } catch (error) {
      report["Términos y Condiciones"] = { status: "FAIL", details: String(error.message || error) };
    }

    // Step 9: Validate Politica de Privacidad.
    try {
      const appPage = page;
      const previousUrl = appPage.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickVisibleText(appPage, ["Política de Privacidad", "Politica de Privacidad"]);
      const popup = await popupPromise;
      const legalPage = popup || appPage;

      await legalPage.waitForLoadState("domcontentloaded");
      await validateLegalPage(legalPage, "Política de Privacidad");

      const shot = await takeScreenshot(legalPage, outputDir, "09-politica-de-privacidad.png", true);
      metadata.legalUrls.politicaDePrivacidad = legalPage.url();

      if (popup) {
        await popup.close();
        await appPage.bringToFront();
      } else if (appPage.url() !== previousUrl) {
        await appPage.goBack({ waitUntil: "domcontentloaded" });
      }
      await waitForUi(appPage);

      report["Política de Privacidad"] = {
        status: "PASS",
        details: `Legal page validated. URL: ${metadata.legalUrls.politicaDePrivacidad}. Screenshot: ${shot}`,
      };
    } catch (error) {
      report["Política de Privacidad"] = { status: "FAIL", details: String(error.message || error) };
    }
  } catch (fatalError) {
    const fatalMessage = `Fatal execution error: ${String(fatalError.message || fatalError)}`;
    metadata.errors.push(fatalMessage);
    for (const field of REPORT_FIELDS) {
      if (report[field].details === "Not executed.") {
        report[field] = { status: "FAIL", details: fatalMessage };
      }
    }
  } finally {
    if (browser) {
      await browser.close();
    }
  }

  const finalReport = {
    ...metadata,
    report,
  };

  const reportPath = path.join(outputDir, "final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");

  console.log("Final SaleADS Mi Negocio report:");
  console.log(JSON.stringify(report, null, 2));
  console.log(`Detailed output: ${reportPath}`);

  const hasFail = Object.values(report).some((entry) => entry.status !== "PASS");
  process.exitCode = hasFail ? 1 : 0;
}

run().catch((error) => {
  console.error("Unexpected runner error:", error);
  process.exitCode = 1;
});
