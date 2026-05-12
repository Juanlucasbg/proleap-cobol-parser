const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const WAIT_TIMEOUT_MS = 20000;

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

const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
const evidence = {
  screenshots: [],
  termsAndConditionsUrl: null,
  privacyPolicyUrl: null,
};

function nowTag() {
  return new Date().toISOString().replace(/[^\d]/g, "-");
}

const artifactsDir = path.join(__dirname, "artifacts", `${TEST_NAME}-${nowTag()}`);

async function settlePage(page) {
  await page.waitForTimeout(700);
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
}

async function captureScreenshot(page, label, fullPage = false) {
  const fileName = `${String(evidence.screenshots.length + 1).padStart(2, "0")}-${label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")}.png`;
  const screenshotPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  evidence.screenshots.push(screenshotPath);
}

async function waitForAnyVisible(candidates, timeoutMs = WAIT_TIMEOUT_MS) {
  const endTime = Date.now() + timeoutMs;

  while (Date.now() < endTime) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if ((await locator.count()) === 0) {
        continue;
      }
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 300));
  }

  throw new Error("No visible element matched the expected selectors.");
}

async function clickAndWait(locator, page) {
  await locator.waitFor({ state: "visible", timeout: WAIT_TIMEOUT_MS });
  await locator.click();
  await settlePage(page);
}

async function ensureVisible(locator, message) {
  try {
    await locator.first().waitFor({ state: "visible", timeout: WAIT_TIMEOUT_MS });
  } catch (error) {
    throw new Error(message);
  }
}

async function validateLegalPage(legalPage, headingRegex, screenshotLabel) {
  const heading = await waitForAnyVisible(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex),
    ],
    WAIT_TIMEOUT_MS,
  );

  await ensureVisible(heading, `Heading ${headingRegex} is not visible.`);

  const bodyText = legalPage.locator("main p, article p, section p, p").first();
  await ensureVisible(bodyText, "Legal content text is not visible.");

  await captureScreenshot(legalPage, screenshotLabel, true);
  return legalPage.url();
}

async function clickLegalLinkAndReturn({
  appPage,
  context,
  linkRegex,
  headingRegex,
  screenshotLabel,
}) {
  const legalLink = await waitForAnyVisible(
    [appPage.getByRole("link", { name: linkRegex }), appPage.getByText(linkRegex)],
    WAIT_TIMEOUT_MS,
  );

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickAndWait(legalLink, appPage);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    const url = await validateLegalPage(popup, headingRegex, screenshotLabel);
    await popup.close();
    await appPage.bringToFront();
    await settlePage(appPage);
    return url;
  }

  await appPage.waitForLoadState("domcontentloaded").catch(() => {});
  const url = await validateLegalPage(appPage, headingRegex, screenshotLabel);
  await appPage.goBack().catch(() => {});
  await settlePage(appPage);
  return url;
}

async function run() {
  fs.mkdirSync(artifactsDir, { recursive: true });

  const wsEndpoint = process.env.PLAYWRIGHT_WS_ENDPOINT;
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const headless = process.env.HEADLESS !== "false";

  let browser;
  let context;
  let page;

  try {
    if (wsEndpoint) {
      browser = await chromium.connectOverCDP(wsEndpoint);
      context = browser.contexts()[0] || (await browser.newContext());
      page = context.pages()[0] || (await context.newPage());
    } else {
      browser = await chromium.launch({ headless });
      context = await browser.newContext();
      page = await context.newPage();
      if (!loginUrl) {
        throw new Error(
          "SALEADS_LOGIN_URL is required when PLAYWRIGHT_WS_ENDPOINT is not set.",
        );
      }
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }

    await settlePage(page);

    try {
      const loginButton = await waitForAnyVisible(
        [
          page.getByRole("button", {
            name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
          }),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        ],
        WAIT_TIMEOUT_MS,
      );

      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickAndWait(loginButton, page);
      const googlePage = await popupPromise;

      const accountLocatorCandidates = (targetPage) => [
        targetPage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
        targetPage.getByText(new RegExp(ACCOUNT_EMAIL, "i")),
      ];

      if (googlePage) {
        await googlePage.waitForLoadState("domcontentloaded");
        const accountOption = await waitForAnyVisible(
          accountLocatorCandidates(googlePage),
          12000,
        ).catch(() => null);

        if (accountOption) {
          await clickAndWait(accountOption, googlePage);
        }

        await googlePage.waitForTimeout(1000).catch(() => {});
      } else {
        const accountOption = await waitForAnyVisible(accountLocatorCandidates(page), 10000).catch(
          () => null,
        );
        if (accountOption) {
          await clickAndWait(accountOption, page);
        }
      }

      await waitForAnyVisible(
        [page.getByRole("navigation"), page.locator("aside"), page.getByText(/Negocio/i)],
        WAIT_TIMEOUT_MS,
      );
      await ensureVisible(
        page.getByText(/Negocio/i),
        "Left sidebar navigation is not visible after login.",
      );

      await captureScreenshot(page, "dashboard-loaded");
      report["Login"] = "PASS";
    } catch (error) {
      report["Login"] = `FAIL - ${error.message}`;
      throw error;
    }

    try {
      const negocioEntry = await waitForAnyVisible(
        [
          page.getByRole("button", { name: /Negocio/i }),
          page.getByRole("link", { name: /Negocio/i }),
          page.getByText(/^Negocio$/i),
        ],
        WAIT_TIMEOUT_MS,
      );
      await clickAndWait(negocioEntry, page);

      const miNegocioEntry = await waitForAnyVisible(
        [
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByRole("link", { name: /Mi Negocio/i }),
          page.getByText(/Mi Negocio/i),
        ],
        WAIT_TIMEOUT_MS,
      );
      await clickAndWait(miNegocioEntry, page);

      await ensureVisible(page.getByText(/Agregar Negocio/i), "'Agregar Negocio' is not visible.");
      await ensureVisible(
        page.getByText(/Administrar Negocios/i),
        "'Administrar Negocios' is not visible.",
      );

      await captureScreenshot(page, "mi-negocio-expanded-menu");
      report["Mi Negocio menu"] = "PASS";
    } catch (error) {
      report["Mi Negocio menu"] = `FAIL - ${error.message}`;
      throw error;
    }

    try {
      const agregarNegocioMenuEntry = await waitForAnyVisible(
        [
          page.getByRole("button", { name: /Agregar Negocio/i }),
          page.getByRole("link", { name: /Agregar Negocio/i }),
          page.getByText(/^Agregar Negocio$/i),
        ],
        WAIT_TIMEOUT_MS,
      );
      await clickAndWait(agregarNegocioMenuEntry, page);

      const modal = await waitForAnyVisible(
        [page.getByRole("dialog"), page.locator("[role='dialog']"), page.locator(".modal").first()],
        WAIT_TIMEOUT_MS,
      );
      await ensureVisible(
        modal.getByText(/Crear Nuevo Negocio/i),
        "Modal title 'Crear Nuevo Negocio' was not visible.",
      );
      const negocioNameInput = await waitForAnyVisible(
        [
          modal.getByLabel(/Nombre del Negocio/i),
          modal.getByPlaceholder(/Nombre del Negocio/i),
          modal.locator("input[name*='nombre'], input[id*='nombre']").first(),
        ],
        WAIT_TIMEOUT_MS,
      );
      await ensureVisible(
        modal.getByText(/Tienes 2 de 3 negocios/i),
        "Text 'Tienes 2 de 3 negocios' was not found in modal.",
      );
      await ensureVisible(modal.getByRole("button", { name: /Cancelar/i }), "Button 'Cancelar' is missing.");
      await ensureVisible(
        modal.getByRole("button", { name: /Crear Negocio/i }),
        "Button 'Crear Negocio' is missing.",
      );

      await captureScreenshot(page, "agregar-negocio-modal");

      await negocioNameInput.click();
      await negocioNameInput.fill("Negocio Prueba Automatización");
      await settlePage(page);
      await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }).first(), page);

      report["Agregar Negocio modal"] = "PASS";
    } catch (error) {
      report["Agregar Negocio modal"] = `FAIL - ${error.message}`;
      throw error;
    }

    try {
      const miNegocioEntry = await waitForAnyVisible(
        [
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByRole("link", { name: /Mi Negocio/i }),
          page.getByText(/Mi Negocio/i),
        ],
        WAIT_TIMEOUT_MS,
      );
      await clickAndWait(miNegocioEntry, page);

      const administrarNegociosEntry = await waitForAnyVisible(
        [
          page.getByRole("button", { name: /Administrar Negocios/i }),
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i),
        ],
        WAIT_TIMEOUT_MS,
      );
      await clickAndWait(administrarNegociosEntry, page);

      await ensureVisible(
        page.getByText(/Informaci[oó]n General/i),
        "Section 'Información General' is missing.",
      );
      await ensureVisible(
        page.getByText(/Detalles de la Cuenta/i),
        "Section 'Detalles de la Cuenta' is missing.",
      );
      await ensureVisible(page.getByText(/Tus Negocios/i), "Section 'Tus Negocios' is missing.");
      await ensureVisible(page.getByText(/Secci[oó]n Legal/i), "Section 'Sección Legal' is missing.");

      await captureScreenshot(page, "administrar-negocios-cuenta", true);
      report["Administrar Negocios view"] = "PASS";
    } catch (error) {
      report["Administrar Negocios view"] = `FAIL - ${error.message}`;
      throw error;
    }

    try {
      await ensureVisible(
        page.locator("h1, h2, h3, section").getByText(/BUSINESS PLAN/i),
        "'BUSINESS PLAN' text is missing.",
      );
      await ensureVisible(
        page.getByRole("button", { name: /Cambiar Plan/i }),
        "Button 'Cambiar Plan' is missing.",
      );
      const infoSection = page
        .locator("section, div")
        .filter({ hasText: /Informaci[oó]n General/i })
        .first();
      await ensureVisible(infoSection, "'Información General' section container is not visible.");
      await ensureVisible(
        infoSection.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first(),
        "User email is not visible.",
      );
      await ensureVisible(
        infoSection.locator("text=/[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+/").first(),
        "User name is not visible in 'Información General'.",
      );
      report["Información General"] = "PASS";
    } catch (error) {
      report["Información General"] = `FAIL - ${error.message}`;
      throw error;
    }

    try {
      await ensureVisible(page.getByText(/Cuenta creada/i), "'Cuenta creada' is not visible.");
      await ensureVisible(page.getByText(/Estado activo/i), "'Estado activo' is not visible.");
      await ensureVisible(
        page.getByText(/Idioma seleccionado/i),
        "'Idioma seleccionado' is not visible.",
      );
      report["Detalles de la Cuenta"] = "PASS";
    } catch (error) {
      report["Detalles de la Cuenta"] = `FAIL - ${error.message}`;
      throw error;
    }

    try {
      await ensureVisible(page.getByText(/Tus Negocios/i), "Business list section title is missing.");
      await ensureVisible(page.getByRole("button", { name: /Agregar Negocio/i }), "Button 'Agregar Negocio' is missing in business section.");
      await ensureVisible(
        page.getByText(/Tienes 2 de 3 negocios/i),
        "Text 'Tienes 2 de 3 negocios' is missing in business section.",
      );
      report["Tus Negocios"] = "PASS";
    } catch (error) {
      report["Tus Negocios"] = `FAIL - ${error.message}`;
      throw error;
    }

    try {
      evidence.termsAndConditionsUrl = await clickLegalLinkAndReturn({
        appPage: page,
        context,
        linkRegex: /T[eé]rminos y Condiciones/i,
        headingRegex: /T[eé]rminos y Condiciones/i,
        screenshotLabel: "terminos-y-condiciones",
      });
      report["Términos y Condiciones"] = "PASS";
    } catch (error) {
      report["Términos y Condiciones"] = `FAIL - ${error.message}`;
      throw error;
    }

    try {
      evidence.privacyPolicyUrl = await clickLegalLinkAndReturn({
        appPage: page,
        context,
        linkRegex: /Pol[ií]tica de Privacidad/i,
        headingRegex: /Pol[ií]tica de Privacidad/i,
        screenshotLabel: "politica-de-privacidad",
      });
      report["Política de Privacidad"] = "PASS";
    } catch (error) {
      report["Política de Privacidad"] = `FAIL - ${error.message}`;
      throw error;
    }
  } catch (fatalError) {
    // Keep final reporting logic alive even if one step fails.
  } finally {
    const finalReport = {
      testName: TEST_NAME,
      generatedAt: new Date().toISOString(),
      report,
      evidence,
    };

    const reportPath = path.join(artifactsDir, "final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    console.log(JSON.stringify(finalReport, null, 2));
    console.log(`Report saved at: ${reportPath}`);

    const hasFail = Object.values(report).some((status) => status !== "PASS");
    if (browser) {
      await browser.close().catch(() => {});
    }
    process.exitCode = hasFail ? 1 : 0;
  }
}

run();
