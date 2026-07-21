#!/usr/bin/env node

const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
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

const LOGIN_URL = process.env.SALEADS_LOGIN_URL || "";
const GOOGLE_ACCOUNT = process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_ACCOUNT;
const WS_ENDPOINT = process.env.SALEADS_BROWSER_WS_ENDPOINT || "";
const HEADLESS = !["0", "false", "no"].includes(
  (process.env.SALEADS_HEADLESS || "true").toLowerCase()
);
const TIMEOUT_MS = Number(process.env.SALEADS_TIMEOUT_MS || 20000);

const runId = new Date().toISOString().replace(/[:.]/g, "-");
const outputRoot = process.env.SALEADS_OUTPUT_DIR
  ? path.resolve(process.env.SALEADS_OUTPUT_DIR)
  : path.join(process.cwd(), "artifacts", runId);
const screenshotDir = path.join(outputRoot, "screenshots");

const report = {
  name: TEST_NAME,
  executedAt: new Date().toISOString(),
  input: {
    loginUrl: LOGIN_URL || null,
    browserWsEndpointProvided: Boolean(WS_ENDPOINT),
    googleAccount: GOOGLE_ACCOUNT,
    headless: HEADLESS
  },
  results: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])),
  steps: [],
  evidence: {
    screenshots: [],
    finalUrls: {}
  },
  notes: []
};

let browser;
let context;
let appPage;

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toSlug(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function ensureDirectories() {
  await fs.mkdir(screenshotDir, { recursive: true });
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: TIMEOUT_MS }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(900);
}

async function captureScreenshot(page, name, fullPage = false) {
  const filename = `${toSlug(name)}.png`;
  const location = path.join(screenshotDir, filename);
  await page.screenshot({ path: location, fullPage });
  report.evidence.screenshots.push(location);
  return location;
}

async function firstVisibleLocator(locators, timeout = 2500) {
  for (const locator of locators) {
    try {
      const first = locator.first();
      await first.waitFor({ state: "visible", timeout });
      return first;
    } catch (error) {
      // Try next candidate.
    }
  }
  return null;
}

async function findClickableByText(page, texts) {
  const locators = [];

  for (const text of texts) {
    const regex = text instanceof RegExp ? text : new RegExp(escapeRegex(text), "i");
    locators.push(page.getByRole("button", { name: regex }));
    locators.push(page.getByRole("link", { name: regex }));
    locators.push(page.getByText(regex));
  }

  return firstVisibleLocator(locators);
}

async function assertVisibleText(page, text) {
  const regex = text instanceof RegExp ? text : new RegExp(escapeRegex(text), "i");
  const locator = page.getByText(regex);
  await locator.first().waitFor({ state: "visible", timeout: TIMEOUT_MS });
}

async function clickAndWait(locator, page) {
  await locator.click({ timeout: TIMEOUT_MS });
  await waitForUiLoad(page);
}

async function isCloudflareBlocked(page) {
  const bodyText = (await page.locator("body").innerText().catch(() => "")) || "";
  return (
    /sorry,\s*you have been blocked/i.test(bodyText) ||
    /attention required/i.test(bodyText) ||
    /cloudflare/i.test(bodyText)
  );
}

async function runStep(field, stepFn) {
  const step = {
    field,
    status: "FAIL",
    startedAt: new Date().toISOString(),
    validations: [],
    error: null
  };
  report.steps.push(step);

  const check = async (description, validationFn) => {
    try {
      await validationFn();
      step.validations.push({ description, status: "PASS" });
      return true;
    } catch (error) {
      step.validations.push({
        description,
        status: "FAIL",
        error: error instanceof Error ? error.message : String(error)
      });
      return false;
    }
  };

  try {
    const passed = await stepFn(check);
    if (!passed) {
      throw new Error(`One or more validations failed in step "${field}".`);
    }
    step.status = "PASS";
    report.results[field] = "PASS";
  } catch (error) {
    step.error = error instanceof Error ? error.message : String(error);
    report.results[field] = "FAIL";
    if (appPage) {
      await captureScreenshot(appPage, `${field}-failure`).catch(() => {});
    }
  } finally {
    step.finishedAt = new Date().toISOString();
  }
}

async function openLegalLinkAndValidate({ field, linkText, headingText, screenshotName }) {
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const navigationPromise = appPage
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 7000 })
    .catch(() => null);

  const legalLink = await findClickableByText(appPage, [linkText]);
  if (!legalLink) {
    throw new Error(`Could not find legal link "${linkText}".`);
  }

  await legalLink.click({ timeout: TIMEOUT_MS });
  const popup = await popupPromise;
  const legalPage = popup || appPage;

  if (popup) {
    await waitForUiLoad(legalPage);
  } else {
    await navigationPromise;
    await waitForUiLoad(legalPage);
  }

  await assertVisibleText(legalPage, headingText);
  const legalBodyText = await legalPage.locator("body").innerText();
  if (legalBodyText.replace(/\s+/g, " ").trim().length < 250) {
    throw new Error(`Legal content for "${headingText}" appears too short or not loaded.`);
  }

  await captureScreenshot(legalPage, screenshotName, true);
  report.evidence.finalUrls[field] = legalPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront().catch(() => {});
    await waitForUiLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded", timeout: TIMEOUT_MS }).catch(() => {});
    await waitForUiLoad(appPage);
  }
}

function renderMarkdownReport() {
  const lines = [];
  lines.push(`# ${report.name}`);
  lines.push("");
  lines.push(`- Executed at: ${report.executedAt}`);
  lines.push(`- Login URL: ${report.input.loginUrl || "not provided"}`);
  lines.push(`- Google account target: ${report.input.googleAccount}`);
  lines.push("");
  lines.push("## Final status");
  lines.push("");
  for (const field of REPORT_FIELDS) {
    lines.push(`- ${field}: **${report.results[field]}**`);
  }
  lines.push("");
  lines.push("## Final URLs");
  lines.push("");
  for (const [field, url] of Object.entries(report.evidence.finalUrls)) {
    lines.push(`- ${field}: ${url}`);
  }
  if (!Object.keys(report.evidence.finalUrls).length) {
    lines.push("- None captured.");
  }
  lines.push("");
  lines.push("## Evidence");
  lines.push("");
  for (const shot of report.evidence.screenshots) {
    lines.push(`- ${shot}`);
  }
  if (!report.evidence.screenshots.length) {
    lines.push("- No screenshots captured.");
  }

  return `${lines.join("\n")}\n`;
}

async function bootstrapBrowser() {
  if (WS_ENDPOINT) {
    browser = await chromium.connectOverCDP(WS_ENDPOINT);
    context = browser.contexts()[0] || (await browser.newContext());
    appPage = context.pages()[0] || (await context.newPage());
    report.notes.push("Connected to existing browser via SALEADS_BROWSER_WS_ENDPOINT.");
  } else {
    browser = await chromium.launch({ headless: HEADLESS });
    context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
    appPage = await context.newPage();
    report.notes.push("Launched a new Chromium browser.");
  }

  if (LOGIN_URL) {
    await appPage.goto(LOGIN_URL, { waitUntil: "domcontentloaded", timeout: TIMEOUT_MS });
    await waitForUiLoad(appPage);
  } else {
    const currentUrl = appPage.url();
    if (!currentUrl || currentUrl === "about:blank") {
      throw new Error(
        "Missing login target: provide SALEADS_LOGIN_URL or connect to an existing browser page with SALEADS_BROWSER_WS_ENDPOINT."
      );
    }
    report.notes.push(`Using existing browser tab URL: ${currentUrl}`);
  }
}

async function executeWorkflow() {
  await ensureDirectories();
  await bootstrapBrowser();

  await runStep("Login", async (check) => {
    const blocked = await isCloudflareBlocked(appPage);
    if (blocked) {
      throw new Error("Cloudflare or WAF blocked access before login.");
    }

    const loginButton = await findClickableByText(appPage, [
      "Sign in with Google",
      "Continuar con Google",
      "Iniciar sesión con Google",
      /^Google$/i
    ]);

    let ok = true;
    ok =
      (await check("Locate Google login button", async () => {
        if (!loginButton) {
          throw new Error("Google login button not found.");
        }
      })) && ok;

    if (loginButton) {
      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await loginButton.click({ timeout: TIMEOUT_MS });
      await waitForUiLoad(appPage);
      const popup = await popupPromise;

      if (popup) {
        await waitForUiLoad(popup);
        const accountOption = await findClickableByText(popup, [GOOGLE_ACCOUNT]);
        if (accountOption) {
          await accountOption.click({ timeout: TIMEOUT_MS });
        }
      } else {
        const accountOption = await findClickableByText(appPage, [GOOGLE_ACCOUNT]);
        if (accountOption) {
          await accountOption.click({ timeout: TIMEOUT_MS });
          await waitForUiLoad(appPage);
        }
      }
    }

    ok =
      (await check("Confirm main application interface appears", async () => {
        const negocioText = await firstVisibleLocator([
          appPage.getByText(/Negocio/i),
          appPage.getByText(/Dashboard/i),
          appPage.getByRole("heading", { name: /Dashboard|Inicio|Panel/i })
        ]);
        if (!negocioText) {
          throw new Error("Main app interface indicators not found.");
        }
      })) && ok;

    ok =
      (await check("Confirm left sidebar navigation is visible", async () => {
        const sidebar = await firstVisibleLocator([
          appPage.locator("aside"),
          appPage.locator("nav"),
          appPage.getByRole("navigation")
        ]);
        if (!sidebar) {
          throw new Error("Sidebar navigation is not visible.");
        }
      })) && ok;

    await captureScreenshot(appPage, "dashboard-loaded");
    return ok;
  });

  await runStep("Mi Negocio menu", async (check) => {
    let ok = true;

    const miNegocioButton = await findClickableByText(appPage, ["Mi Negocio"]);
    ok =
      (await check("Open Mi Negocio from sidebar", async () => {
        if (!miNegocioButton) {
          throw new Error('Sidebar option "Mi Negocio" not found.');
        }
        await clickAndWait(miNegocioButton, appPage);
      })) && ok;

    ok =
      (await check('Confirm "Agregar Negocio" is visible', async () => {
        await assertVisibleText(appPage, "Agregar Negocio");
      })) && ok;
    ok =
      (await check('Confirm "Administrar Negocios" is visible', async () => {
        await assertVisibleText(appPage, "Administrar Negocios");
      })) && ok;

    await captureScreenshot(appPage, "mi-negocio-menu-expanded");
    return ok;
  });

  await runStep("Agregar Negocio modal", async (check) => {
    let ok = true;

    const addBusinessEntry = await findClickableByText(appPage, ["Agregar Negocio"]);
    ok =
      (await check('Click "Agregar Negocio"', async () => {
        if (!addBusinessEntry) {
          throw new Error('Option "Agregar Negocio" not found in menu.');
        }
        await clickAndWait(addBusinessEntry, appPage);
      })) && ok;

    ok =
      (await check('Modal title "Crear Nuevo Negocio" is visible', async () => {
        await assertVisibleText(appPage, "Crear Nuevo Negocio");
      })) && ok;

    ok =
      (await check('Input field "Nombre del Negocio" exists', async () => {
        const input = await firstVisibleLocator([
          appPage.getByLabel(/Nombre del Negocio/i),
          appPage.getByPlaceholder(/Nombre del Negocio/i),
          appPage.getByRole("textbox", { name: /Nombre del Negocio/i })
        ]);
        if (!input) {
          throw new Error('Input "Nombre del Negocio" not visible.');
        }
      })) && ok;

    ok =
      (await check('Text "Tienes 2 de 3 negocios" is visible', async () => {
        await assertVisibleText(appPage, "Tienes 2 de 3 negocios");
      })) && ok;

    ok =
      (await check('Buttons "Cancelar" and "Crear Negocio" are present', async () => {
        const cancel = await findClickableByText(appPage, ["Cancelar"]);
        const create = await findClickableByText(appPage, ["Crear Negocio"]);
        if (!cancel || !create) {
          throw new Error('Modal actions "Cancelar" or "Crear Negocio" not found.');
        }
      })) && ok;

    const nameField = await firstVisibleLocator([
      appPage.getByLabel(/Nombre del Negocio/i),
      appPage.getByPlaceholder(/Nombre del Negocio/i),
      appPage.getByRole("textbox", { name: /Nombre del Negocio/i })
    ]);
    if (nameField) {
      await nameField.click({ timeout: TIMEOUT_MS });
      await nameField.fill("Negocio Prueba Automatización");
    }
    await captureScreenshot(appPage, "agregar-negocio-modal");

    const cancelButton = await findClickableByText(appPage, ["Cancelar"]);
    if (cancelButton) {
      await clickAndWait(cancelButton, appPage);
    }

    return ok;
  });

  await runStep("Administrar Negocios view", async (check) => {
    let ok = true;

    const adminBusiness = await findClickableByText(appPage, ["Administrar Negocios"]);
    if (!adminBusiness) {
      const miNegocioButton = await findClickableByText(appPage, ["Mi Negocio"]);
      if (miNegocioButton) {
        await clickAndWait(miNegocioButton, appPage);
      }
    }

    ok =
      (await check('Click "Administrar Negocios"', async () => {
        const adminEntry = await findClickableByText(appPage, ["Administrar Negocios"]);
        if (!adminEntry) {
          throw new Error('"Administrar Negocios" option is not visible.');
        }
        await clickAndWait(adminEntry, appPage);
      })) && ok;

    ok =
      (await check('Section "Información General" exists', async () => {
        await assertVisibleText(appPage, "Información General");
      })) && ok;
    ok =
      (await check('Section "Detalles de la Cuenta" exists', async () => {
        await assertVisibleText(appPage, "Detalles de la Cuenta");
      })) && ok;
    ok =
      (await check('Section "Tus Negocios" exists', async () => {
        await assertVisibleText(appPage, "Tus Negocios");
      })) && ok;
    ok =
      (await check('Section "Sección Legal" exists', async () => {
        await assertVisibleText(appPage, "Sección Legal");
      })) && ok;

    await captureScreenshot(appPage, "administrar-negocios-view", true);
    return ok;
  });

  await runStep("Información General", async (check) => {
    let ok = true;

    ok =
      (await check("User email is visible", async () => {
        const emailLocator = appPage.locator(
          "text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"
        );
        await emailLocator.first().waitFor({ state: "visible", timeout: TIMEOUT_MS });
      })) && ok;

    ok =
      (await check("User name is visible", async () => {
        const emailLocator = appPage.locator(
          "text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"
        );
        await emailLocator.first().waitFor({ state: "visible", timeout: TIMEOUT_MS });
        const container = emailLocator
          .first()
          .locator("xpath=ancestor::*[self::div or self::section][1]");
        const text = await container.innerText();
        const candidateLines = text
          .split(/\r?\n/)
          .map((line) => line.trim())
          .filter(Boolean)
          .filter((line) => !/@/.test(line))
          .filter((line) => !/informaci[oó]n general|business plan|cambiar plan/i.test(line))
          .filter((line) => line.length >= 3);
        if (!candidateLines.length) {
          throw new Error("No visible user name found near account email.");
        }
      })) && ok;

    ok =
      (await check('Text "BUSINESS PLAN" is visible', async () => {
        await assertVisibleText(appPage, "BUSINESS PLAN");
      })) && ok;
    ok =
      (await check('Button "Cambiar Plan" is visible', async () => {
        const button = await findClickableByText(appPage, ["Cambiar Plan"]);
        if (!button) {
          throw new Error('"Cambiar Plan" button not found.');
        }
      })) && ok;

    return ok;
  });

  await runStep("Detalles de la Cuenta", async (check) => {
    let ok = true;
    ok =
      (await check('"Cuenta creada" is visible', async () => {
        await assertVisibleText(appPage, "Cuenta creada");
      })) && ok;
    ok =
      (await check('"Estado activo" is visible', async () => {
        await assertVisibleText(appPage, "Estado activo");
      })) && ok;
    ok =
      (await check('"Idioma seleccionado" is visible', async () => {
        await assertVisibleText(appPage, "Idioma seleccionado");
      })) && ok;

    return ok;
  });

  await runStep("Tus Negocios", async (check) => {
    let ok = true;
    ok =
      (await check("Business list is visible", async () => {
        const heading = appPage.getByText(/Tus Negocios/i).first();
        await heading.waitFor({ state: "visible", timeout: TIMEOUT_MS });
        const section = heading.locator("xpath=ancestor::*[self::section or self::div][1]");
        const sectionText = (await section.innerText()).replace(/\s+/g, " ").trim();
        if (sectionText.length < 40) {
          throw new Error("Business section appears empty.");
        }
      })) && ok;
    ok =
      (await check('Button "Agregar Negocio" exists', async () => {
        const button = await findClickableByText(appPage, ["Agregar Negocio"]);
        if (!button) {
          throw new Error('"Agregar Negocio" button not found in businesses section.');
        }
      })) && ok;
    ok =
      (await check('Text "Tienes 2 de 3 negocios" is visible', async () => {
        await assertVisibleText(appPage, "Tienes 2 de 3 negocios");
      })) && ok;

    return ok;
  });

  await runStep("Términos y Condiciones", async (check) => {
    let ok = true;
    ok =
      (await check('Open "Términos y Condiciones" and validate heading/content', async () => {
        await openLegalLinkAndValidate({
          field: "Términos y Condiciones",
          linkText: "Términos y Condiciones",
          headingText: "Términos y Condiciones",
          screenshotName: "terminos-y-condiciones"
        });
      })) && ok;
    return ok;
  });

  await runStep("Política de Privacidad", async (check) => {
    let ok = true;
    ok =
      (await check('Open "Política de Privacidad" and validate heading/content', async () => {
        await openLegalLinkAndValidate({
          field: "Política de Privacidad",
          linkText: "Política de Privacidad",
          headingText: "Política de Privacidad",
          screenshotName: "politica-de-privacidad"
        });
      })) && ok;
    return ok;
  });
}

async function finalize(exitError = null) {
  if (exitError) {
    report.notes.push(`Fatal error: ${exitError.message}`);
  }

  const reportPath = path.join(outputRoot, "report.json");
  const markdownPath = path.join(outputRoot, "report.md");
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  await fs.writeFile(markdownPath, renderMarkdownReport(), "utf8");

  if (browser) {
    await browser.close().catch(() => {});
  }

  console.log(`Workflow report JSON: ${reportPath}`);
  console.log(`Workflow report Markdown: ${markdownPath}`);
  console.log(JSON.stringify(report.results, null, 2));

  const anyFailure = Object.values(report.results).some((value) => value !== "PASS");
  if (exitError || anyFailure) {
    process.exitCode = 1;
  } else {
    process.exitCode = 0;
  }
}

(async () => {
  let fatalError = null;
  try {
    await executeWorkflow();
  } catch (error) {
    fatalError = error instanceof Error ? error : new Error(String(error));
  } finally {
    await finalize(fatalError);
  }
})();
