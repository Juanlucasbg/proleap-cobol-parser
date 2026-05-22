import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";
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
const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
const artifactDir = path.resolve(
  process.env.SALEADS_ARTIFACT_DIR ?? path.join("artifacts", "saleads-mi-negocio", timestamp)
);
const reportPath = path.join(artifactDir, "report.json");

/**
 * Creates a readable file suffix from a human label.
 */
function slugify(text) {
  return text
    .normalize("NFKD")
    .replace(/[^\w\s-]/g, "")
    .trim()
    .replace(/\s+/g, "-")
    .toLowerCase();
}

async function waitForUi(page, timeout = 12000) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout }),
    page.waitForLoadState("networkidle", { timeout })
  ]);
  await page.waitForTimeout(500);
}

async function firstVisibleLocator(locators) {
  for (const locator of locators) {
    try {
      if (await locator.first().isVisible()) {
        return locator.first();
      }
    } catch {
      // Ignore strict and detached errors while probing.
    }
  }

  return null;
}

async function checkVisible(step, description, locator, timeout = 15000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    step.validations.push({ description, status: "PASS" });
    return true;
  } catch (error) {
    step.validations.push({
      description,
      status: "FAIL",
      details: error.message
    });
    step.status = "FAIL";
    return false;
  }
}

async function clickAndWait(step, locator, page, description) {
  try {
    await locator.first().click({ timeout: 15000 });
    await waitForUi(page);
    step.actions.push({ description, status: "PASS" });
    return true;
  } catch (error) {
    step.actions.push({ description, status: "FAIL", details: error.message });
    step.status = "FAIL";
    return false;
  }
}

async function captureScreenshot(step, page, name, fullPage = false) {
  const fileName = `${String(step.id).padStart(2, "0")}-${slugify(name)}.png`;
  const filePath = path.join(artifactDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  step.evidence.push({ type: "screenshot", path: filePath });
  return filePath;
}

function makeStep(id, field, details) {
  return {
    id,
    field,
    details,
    status: "PASS",
    actions: [],
    validations: [],
    evidence: []
  };
}

function buildSummary(results) {
  const summary = {};
  for (const step of results) {
    summary[step.field] = step.status;
  }
  return summary;
}

async function waitForSidebar(page) {
  const sidebarCandidates = [
    page.locator("aside"),
    page.locator("[role='navigation']"),
    page.getByText(/mi negocio|negocio/i)
  ];

  const sidebar = await firstVisibleLocator(sidebarCandidates);
  if (sidebar) {
    return true;
  }

  await page.getByText(/mi negocio|negocio/i).first().waitFor({
    state: "visible",
    timeout: 90000
  });
  return true;
}

async function withOptionalGoogleAccountSelection(page, step) {
  const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
  const googleAccountOnCurrentPage = page.getByText(ACCOUNT_EMAIL, { exact: false });

  let popup = null;
  try {
    popup = await popupPromise;
  } catch {
    popup = null;
  }

  const target = popup ?? page;
  await waitForUi(target, 20000);

  const accountLocator = target.getByText(ACCOUNT_EMAIL, { exact: false });
  if (await accountLocator.first().isVisible().catch(() => false)) {
    await clickAndWait(step, accountLocator, target, `Select Google account ${ACCOUNT_EMAIL}`);
  } else if (await googleAccountOnCurrentPage.first().isVisible().catch(() => false)) {
    await clickAndWait(
      step,
      googleAccountOnCurrentPage,
      page,
      `Select Google account ${ACCOUNT_EMAIL} on current page`
    );
  } else {
    step.actions.push({
      description: `Google account picker for ${ACCOUNT_EMAIL} did not appear`,
      status: "PASS"
    });
  }

  if (popup && !popup.isClosed()) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => null);
  }
}

async function openLegalLinkAndValidate({
  appPage,
  step,
  linkText,
  headingRegex,
  screenshotLabel
}) {
  const popupPromise = appPage.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
  const oldUrl = appPage.url();

  const link = await firstVisibleLocator([
    appPage.getByRole("link", { name: new RegExp(linkText, "i") }),
    appPage.getByText(new RegExp(linkText, "i"))
  ]);

  if (!link) {
    throw new Error(`Unable to find legal link with text "${linkText}".`);
  }

  await clickAndWait(step, link, appPage, `Click "${linkText}"`);
  const popup = await popupPromise;
  let legalPage = appPage;
  let openedInNewTab = false;

  if (popup) {
    legalPage = popup;
    openedInNewTab = true;
    await waitForUi(legalPage, 20000);
  } else {
    await waitForUi(appPage, 20000);
  }

  await checkVisible(
    step,
    `Page contains heading "${headingRegex.source.replace(/\\/g, "")}"`,
    legalPage.getByRole("heading", { name: headingRegex })
  );

  await checkVisible(
    step,
    "Legal content text is visible",
    legalPage.locator("main p, article p, body p").first()
  );

  const screenshotPath = await captureScreenshot(step, legalPage, screenshotLabel, true);
  step.evidence.push({ type: "url", value: legalPage.url() });
  step.actions.push({
    description: `Captured legal page URL: ${legalPage.url()}`,
    status: "PASS"
  });

  if (openedInNewTab && !legalPage.isClosed()) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== oldUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(appPage);
  }

  return { screenshotPath, url: legalPage.url(), openedInNewTab };
}

async function prepareBrowserAndPage() {
  const cdpUrl = process.env.SALEADS_CDP_URL;

  if (cdpUrl) {
    const browser = await chromium.connectOverCDP(cdpUrl);
    const context = browser.contexts()[0] ?? (await browser.newContext());
    const page =
      context.pages().find((candidate) => {
        const url = candidate.url();
        return url && !url.startsWith("chrome-extension://");
      }) ?? (await context.newPage());

    return { browser, context, page, mode: "cdp" };
  }

  const browser = await chromium.launch({
    headless: process.env.HEADLESS !== "false"
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 }
  });
  const page = await context.newPage();
  const loginUrl = process.env.SALEADS_LOGIN_URL;

  if (!loginUrl) {
    throw new Error(
      "SALEADS_LOGIN_URL is required when SALEADS_CDP_URL is not provided. " +
        "Use SALEADS_CDP_URL to attach to an already open SaleADS login page."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  return { browser, context, page, mode: "launch" };
}

async function main() {
  await fs.mkdir(artifactDir, { recursive: true });

  const report = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    artifactDir,
    environment: {
      mode: "unknown",
      saleadsLoginUrlProvided: Boolean(process.env.SALEADS_LOGIN_URL),
      saleadsCdpUrlProvided: Boolean(process.env.SALEADS_CDP_URL)
    },
    results: [],
    summary: {}
  };

  let browser;
  let page;

  try {
    const prepared = await prepareBrowserAndPage();
    browser = prepared.browser;
    page = prepared.page;
    report.environment.mode = prepared.mode;

    const loginStep = makeStep(1, "Login", "Login with Google and verify dashboard/sidebar.");
    report.results.push(loginStep);

    const appAlreadyLoaded = await firstVisibleLocator([
      page.locator("aside"),
      page.locator("[role='navigation']"),
      page.getByText(/mi negocio|negocio/i)
    ]);

    if (!appAlreadyLoaded) {
      const loginButton = await firstVisibleLocator([
        page.getByRole("button", {
          name: /sign in with google|continue with google|continuar con google|ingresar con google/i
        }),
        page.getByText(
          /sign in with google|continue with google|continuar con google|ingresar con google/i
        ),
        page.getByRole("button", { name: /iniciar sesi[oó]n|acceder|login|entrar/i })
      ]);

      if (!loginButton) {
        throw new Error("Could not find login or 'Sign in with Google' button.");
      }

      await clickAndWait(loginStep, loginButton, page, "Click login / Sign in with Google");
      await withOptionalGoogleAccountSelection(page, loginStep);
    } else {
      loginStep.actions.push({
        description: "Application already appeared authenticated; continuing workflow",
        status: "PASS"
      });
    }

    await waitForSidebar(page);
    await checkVisible(loginStep, "Main application interface appears", page.locator("body"));
    await checkVisible(
      loginStep,
      "Left sidebar navigation is visible",
      (await firstVisibleLocator([page.locator("aside"), page.locator("[role='navigation']")])) ??
        page.getByText(/mi negocio|negocio/i)
    );
    await captureScreenshot(loginStep, page, "dashboard-loaded", true);

    const menuStep = makeStep(
      2,
      "Mi Negocio menu",
      "Open sidebar Negocio > Mi Negocio and validate submenu options."
    );
    report.results.push(menuStep);

    const negocioSection = await firstVisibleLocator([
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /negocio/i }),
      page.getByRole("link", { name: /negocio/i })
    ]);

    if (negocioSection) {
      await clickAndWait(menuStep, negocioSection, page, "Open 'Negocio' section");
    }

    const miNegocio = await firstVisibleLocator([
      page.getByText(/mi negocio/i),
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i })
    ]);

    if (!miNegocio) {
      throw new Error("Unable to find 'Mi Negocio' option in left sidebar.");
    }

    await clickAndWait(menuStep, miNegocio, page, "Click 'Mi Negocio'");
    await checkVisible(menuStep, "Submenu expands", page.getByText(/agregar negocio|administrar negocios/i));
    await checkVisible(menuStep, "'Agregar Negocio' is visible", page.getByText(/agregar negocio/i));
    await checkVisible(
      menuStep,
      "'Administrar Negocios' is visible",
      page.getByText(/administrar negocios/i)
    );
    await captureScreenshot(menuStep, page, "mi-negocio-menu-expanded", true);

    const agregarModalStep = makeStep(
      3,
      "Agregar Negocio modal",
      "Open 'Agregar Negocio' modal and validate required fields and actions."
    );
    report.results.push(agregarModalStep);

    const agregarNegocioMenu = await firstVisibleLocator([
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i)
    ]);

    if (!agregarNegocioMenu) {
      throw new Error("Could not find 'Agregar Negocio' entry under Mi Negocio.");
    }

    await clickAndWait(agregarModalStep, agregarNegocioMenu, page, "Click 'Agregar Negocio'");
    await checkVisible(
      agregarModalStep,
      "Modal title 'Crear Nuevo Negocio' is visible",
      page.getByRole("heading", { name: /crear nuevo negocio/i })
    );
    await checkVisible(
      agregarModalStep,
      "Input field 'Nombre del Negocio' exists",
      page.getByLabel(/nombre del negocio/i)
    );
    await checkVisible(
      agregarModalStep,
      "Text 'Tienes 2 de 3 negocios' is visible",
      page.getByText(/tienes 2 de 3 negocios/i)
    );
    await checkVisible(
      agregarModalStep,
      "Buttons 'Cancelar' and 'Crear Negocio' are present",
      page.getByRole("button", { name: /cancelar/i })
    );
    await checkVisible(
      agregarModalStep,
      "Buttons 'Cancelar' and 'Crear Negocio' are present",
      page.getByRole("button", { name: /crear negocio/i })
    );
    await captureScreenshot(agregarModalStep, page, "agregar-negocio-modal", true);

    const negocioInput = page.getByLabel(/nombre del negocio/i);
    if (await negocioInput.first().isVisible().catch(() => false)) {
      await negocioInput.click();
      await negocioInput.fill("Negocio Prueba Automatización");
      agregarModalStep.actions.push({
        description: "Optional action: filled 'Nombre del Negocio' field",
        status: "PASS"
      });
    }

    const cancelarButton = page.getByRole("button", { name: /cancelar/i });
    if (await cancelarButton.first().isVisible().catch(() => false)) {
      await clickAndWait(agregarModalStep, cancelarButton, page, "Close modal with 'Cancelar'");
    }

    const administrarStep = makeStep(
      4,
      "Administrar Negocios view",
      "Open account management page and validate all major sections."
    );
    report.results.push(administrarStep);

    const miNegocioAgain = await firstVisibleLocator([
      page.getByText(/mi negocio/i),
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i })
    ]);
    if (miNegocioAgain) {
      await clickAndWait(administrarStep, miNegocioAgain, page, "Expand 'Mi Negocio' if collapsed");
    }

    const administrarNegociosOption = await firstVisibleLocator([
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);

    if (!administrarNegociosOption) {
      throw new Error("Could not find 'Administrar Negocios' option.");
    }

    await clickAndWait(administrarStep, administrarNegociosOption, page, "Click 'Administrar Negocios'");
    await checkVisible(
      administrarStep,
      "Section 'Información General' exists",
      page.getByRole("heading", { name: /informaci[oó]n general/i })
    );
    await checkVisible(
      administrarStep,
      "Section 'Detalles de la Cuenta' exists",
      page.getByRole("heading", { name: /detalles de la cuenta/i })
    );
    await checkVisible(
      administrarStep,
      "Section 'Tus Negocios' exists",
      page.getByRole("heading", { name: /tus negocios/i })
    );
    await checkVisible(
      administrarStep,
      "Section 'Sección Legal' exists",
      page.getByRole("heading", { name: /secci[oó]n legal/i })
    );
    await captureScreenshot(administrarStep, page, "administrar-negocios-page", true);

    const infoGeneralStep = makeStep(
      5,
      "Información General",
      "Validate user identity and plan details."
    );
    report.results.push(infoGeneralStep);
    await checkVisible(
      infoGeneralStep,
      "User name is visible",
      page.getByText(/nombre|usuario|perfil/i)
    );
    await checkVisible(
      infoGeneralStep,
      "User email is visible",
      page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
    );
    await checkVisible(
      infoGeneralStep,
      "Text 'BUSINESS PLAN' is visible",
      page.getByText(/business plan/i)
    );
    await checkVisible(
      infoGeneralStep,
      "Button 'Cambiar Plan' is visible",
      page.getByRole("button", { name: /cambiar plan/i })
    );

    const detallesStep = makeStep(
      6,
      "Detalles de la Cuenta",
      "Validate account details labels and values."
    );
    report.results.push(detallesStep);
    await checkVisible(detallesStep, "'Cuenta creada' is visible", page.getByText(/cuenta creada/i));
    await checkVisible(detallesStep, "'Estado activo' is visible", page.getByText(/estado activo/i));
    await checkVisible(
      detallesStep,
      "'Idioma seleccionado' is visible",
      page.getByText(/idioma seleccionado/i)
    );

    const tusNegociosStep = makeStep(7, "Tus Negocios", "Validate business list and capacity indicator.");
    report.results.push(tusNegociosStep);
    await checkVisible(
      tusNegociosStep,
      "Business list is visible",
      page.locator("li, tr, [data-testid*='business']").filter({ hasText: /negocio/i }).first()
    );
    await checkVisible(
      tusNegociosStep,
      "Button 'Agregar Negocio' exists",
      page.getByRole("button", { name: /agregar negocio/i })
    );
    await checkVisible(
      tusNegociosStep,
      "Text 'Tienes 2 de 3 negocios' is visible",
      page.getByText(/tienes 2 de 3 negocios/i)
    );

    const termsStep = makeStep(
      8,
      "Términos y Condiciones",
      "Open legal terms and validate heading and body content."
    );
    report.results.push(termsStep);
    await openLegalLinkAndValidate({
      appPage: page,
      step: termsStep,
      linkText: "Términos y Condiciones",
      headingRegex: /t[eé]rminos y condiciones/i,
      screenshotLabel: "terminos-y-condiciones"
    });

    const privacyStep = makeStep(
      9,
      "Política de Privacidad",
      "Open privacy policy and validate heading and body content."
    );
    report.results.push(privacyStep);
    await openLegalLinkAndValidate({
      appPage: page,
      step: privacyStep,
      linkText: "Política de Privacidad",
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotLabel: "politica-de-privacidad"
    });

    report.summary = buildSummary(report.results);
    const hasFailures = report.results.some((step) => step.status === "FAIL");
    report.finalStatus = hasFailures ? "FAIL" : "PASS";
  } catch (error) {
    report.finalStatus = "FAIL";
    report.error = error.message;
  } finally {
    const existingFields = new Set(report.results.map((step) => step.field));
    for (const field of REPORT_FIELDS) {
      if (!existingFields.has(field)) {
        report.results.push({
          id: report.results.length + 1,
          field,
          details: "Not executed due to earlier workflow failure.",
          status: "FAIL",
          actions: [],
          validations: [],
          evidence: []
        });
      }
    }

    report.summary = buildSummary(report.results);
    const hasFailures = report.results.some((step) => step.status === "FAIL");
    report.finalStatus = hasFailures ? "FAIL" : "PASS";

    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    if (browser) {
      await browser.close();
    }
  }

  console.log(`Report written to ${reportPath}`);
  console.table(
    report.results?.map((step) => ({
      step: step.field,
      status: step.status
    })) ?? []
  );
  console.log(`Final status: ${report.finalStatus}`);

  if (report.finalStatus === "FAIL") {
    process.exitCode = 1;
  }
}

main();
