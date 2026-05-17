#!/usr/bin/env node

import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

const WORKFLOW_NAME = "saleads_mi_negocio_full_test";
const TARGET_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

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

function parseCliArgs(argv) {
  const args = { url: undefined, headless: true, help: false };
  for (let idx = 2; idx < argv.length; idx += 1) {
    const arg = argv[idx];
    if (arg === "--help" || arg === "-h") {
      args.help = true;
    } else if (arg === "--url" && argv[idx + 1]) {
      args.url = argv[idx + 1];
      idx += 1;
    } else if (arg.startsWith("--url=")) {
      args.url = arg.split("=")[1];
    } else if (arg === "--headed") {
      args.headless = false;
    } else if (arg === "--headless") {
      args.headless = true;
    }
  }

  const headlessFromEnv = process.env.HEADLESS?.toLowerCase();
  if (headlessFromEnv === "false" || headlessFromEnv === "0") {
    args.headless = false;
  }

  if (!args.url) {
    args.url = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_START_URL;
  }

  return args;
}

function usage() {
  return [
    `Usage: node automation/saleads/${WORKFLOW_NAME}.mjs --url <login-page-url> [--headed]`,
    "",
    "Options:",
    "  --url <value>   SaleADS login URL for the current environment.",
    "  --headed        Run browser in headed mode.",
    "  --headless      Run browser in headless mode (default).",
    "  --help          Print this help.",
    "",
    "Environment variables:",
    "  SALEADS_LOGIN_URL / SALEADS_START_URL  Alternative to --url.",
    "  HEADLESS=false                        Force headed mode."
  ].join("\n");
}

function toSlug(input) {
  return input
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

async function waitForUiLoad(page) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 12000 }),
    page.waitForLoadState("networkidle", { timeout: 12000 })
  ]);
  await page.waitForTimeout(900);
}

async function waitForAny(page, locatorFactories, description, timeoutMs = 20000) {
  const started = Date.now();
  let lastError;

  while (Date.now() - started < timeoutMs) {
    for (const createLocator of locatorFactories) {
      const locator = createLocator(page).first();
      try {
        await locator.waitFor({ state: "visible", timeout: 1250 });
        return locator;
      } catch (error) {
        lastError = error;
      }
    }
  }

  throw new Error(`Unable to find visible element for "${description}". Last error: ${lastError?.message ?? "none"}`);
}

async function findOptional(page, locatorFactories, timeoutMs = 4500) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    for (const createLocator of locatorFactories) {
      const locator = createLocator(page).first();
      try {
        await locator.waitFor({ state: "visible", timeout: 750 });
        return locator;
      } catch {
        // Keep scanning candidate selectors.
      }
    }
  }
  return null;
}

async function clickAndWait(page, locator) {
  await locator.click({ timeout: 15000 });
  await waitForUiLoad(page);
}

async function validateLegalContent(page, headingRegex) {
  await waitForAny(
    page,
    [
      (ctx) => ctx.getByRole("heading", { name: headingRegex }),
      (ctx) => ctx.getByText(headingRegex)
    ],
    `heading ${headingRegex}`
  );

  const visibleParagraph = await findOptional(page, [
    (ctx) => ctx.locator("main p"),
    (ctx) => ctx.locator("article p"),
    (ctx) => ctx.locator("section p"),
    (ctx) => ctx.locator("p")
  ]);

  if (!visibleParagraph) {
    const bodyText = await page.locator("body").innerText();
    if (!bodyText || bodyText.trim().length < 80) {
      throw new Error("Legal content text is not visible.");
    }
  }
}

async function main() {
  const cli = parseCliArgs(process.argv);
  if (cli.help) {
    console.log(usage());
    return;
  }

  if (!cli.url) {
    throw new Error(
      "No start URL provided. Pass --url <login-url> or define SALEADS_LOGIN_URL/SALEADS_START_URL."
    );
  }

  const timestamp = new Date().toISOString().replaceAll(":", "-");
  const artifactRoot = path.resolve("automation", "artifacts", `${WORKFLOW_NAME}-${timestamp}`);
  await fs.mkdir(artifactRoot, { recursive: true });

  const report = {
    workflow: WORKFLOW_NAME,
    startedAt: new Date().toISOString(),
    urlUsed: cli.url,
    artifactsDir: artifactRoot,
    statusByField: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])),
    details: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "Not executed"])),
    screenshots: [],
    legalUrls: {}
  };

  const browser = await chromium.launch({ headless: cli.headless });
  const context = await browser.newContext({ viewport: { width: 1600, height: 900 } });
  const page = await context.newPage();

  let shotCounter = 1;
  async function takeScreenshot(targetPage, title, fullPage = false) {
    const fileName = `${String(shotCounter).padStart(2, "0")}-${toSlug(title)}.png`;
    const screenshotPath = path.join(artifactRoot, fileName);
    await targetPage.screenshot({ path: screenshotPath, fullPage });
    report.screenshots.push(screenshotPath);
    shotCounter += 1;
  }

  function mark(field, pass, message) {
    report.statusByField[field] = pass ? "PASS" : "FAIL";
    report.details[field] = message;
  }

  try {
    await page.goto(cli.url, { waitUntil: "domcontentloaded", timeout: 60000 });
    await waitForUiLoad(page);

    // 1) Login with Google
    const loginButton = await waitForAny(
      page,
      [
        (ctx) => ctx.getByRole("button", { name: /(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)/i }),
        (ctx) => ctx.getByRole("link", { name: /(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)/i }),
        (ctx) => ctx.getByText(/(sign in with google|iniciar sesi[oó]n con google|continuar con google)/i)
      ],
      "Login with Google button"
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const googlePopup = await popupPromise;

    if (googlePopup) {
      await waitForUiLoad(googlePopup);
      const accountOption = await findOptional(googlePopup, [
        (ctx) => ctx.getByText(TARGET_GOOGLE_ACCOUNT, { exact: false }),
        (ctx) => ctx.getByRole("button", { name: new RegExp(TARGET_GOOGLE_ACCOUNT, "i") }),
        (ctx) => ctx.getByRole("link", { name: new RegExp(TARGET_GOOGLE_ACCOUNT, "i") })
      ], 12000);

      if (accountOption) {
        await clickAndWait(googlePopup, accountOption);
      }
      await Promise.race([
        googlePopup.waitForEvent("close", { timeout: 15000 }).catch(() => null),
        page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => null)
      ]);
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      const accountOnSameTab = await findOptional(page, [
        (ctx) => ctx.getByText(TARGET_GOOGLE_ACCOUNT, { exact: false }),
        (ctx) => ctx.getByRole("button", { name: new RegExp(TARGET_GOOGLE_ACCOUNT, "i") }),
        (ctx) => ctx.getByRole("link", { name: new RegExp(TARGET_GOOGLE_ACCOUNT, "i") })
      ], 9000);
      if (accountOnSameTab) {
        await clickAndWait(page, accountOnSameTab);
      }
    }

    await waitForAny(
      page,
      [
        (ctx) => ctx.locator("aside"),
        (ctx) => ctx.getByRole("navigation"),
        (ctx) => ctx.getByText(/negocio/i)
      ],
      "main application and left sidebar"
    );
    await takeScreenshot(page, "dashboard-loaded");
    mark("Login", true, "Main interface and sidebar became visible after Google login.");

    // 2) Open Mi Negocio menu
    const negocioOption = await waitForAny(
      page,
      [
        (ctx) => ctx.getByRole("button", { name: /^negocio$/i }),
        (ctx) => ctx.getByRole("link", { name: /^negocio$/i }),
        (ctx) => ctx.getByText(/^negocio$/i)
      ],
      "Negocio menu option"
    );
    await clickAndWait(page, negocioOption);

    const miNegocioOption = await waitForAny(
      page,
      [
        (ctx) => ctx.getByRole("button", { name: /^mi negocio$/i }),
        (ctx) => ctx.getByRole("link", { name: /^mi negocio$/i }),
        (ctx) => ctx.getByText(/^mi negocio$/i)
      ],
      "Mi Negocio menu option"
    );
    await clickAndWait(page, miNegocioOption);

    await waitForAny(
      page,
      [
        (ctx) => ctx.getByRole("button", { name: /agregar negocio/i }),
        (ctx) => ctx.getByRole("link", { name: /agregar negocio/i }),
        (ctx) => ctx.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio visible"
    );
    await waitForAny(
      page,
      [
        (ctx) => ctx.getByRole("button", { name: /administrar negocios/i }),
        (ctx) => ctx.getByRole("link", { name: /administrar negocios/i }),
        (ctx) => ctx.getByText(/administrar negocios/i)
      ],
      "Administrar Negocios visible"
    );
    await takeScreenshot(page, "mi-negocio-menu-expanded");
    mark("Mi Negocio menu", true, "Mi Negocio submenu expanded with expected options.");

    // 3) Validate Agregar Negocio modal
    const agregarNegocioMenu = await waitForAny(
      page,
      [
        (ctx) => ctx.getByRole("button", { name: /agregar negocio/i }),
        (ctx) => ctx.getByRole("link", { name: /agregar negocio/i }),
        (ctx) => ctx.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio menu option"
    );
    await clickAndWait(page, agregarNegocioMenu);

    const modalTitle = await waitForAny(
      page,
      [
        (ctx) => ctx.getByRole("heading", { name: /crear nuevo negocio/i }),
        (ctx) => ctx.getByText(/crear nuevo negocio/i)
      ],
      "Crear Nuevo Negocio modal"
    );
    await modalTitle.scrollIntoViewIfNeeded();

    const nombreField = await waitForAny(
      page,
      [
        (ctx) => ctx.getByLabel(/nombre del negocio/i),
        (ctx) => ctx.getByPlaceholder(/nombre del negocio/i),
        (ctx) => ctx.getByRole("dialog").locator("input"),
        (ctx) => ctx.locator("input[name*='negocio' i]"),
        (ctx) => ctx.locator("input[placeholder*='Negocio' i]")
      ],
      "Nombre del Negocio input"
    );
    await waitForAny(page, [(ctx) => ctx.getByText(/tienes 2 de 3 negocios/i)], "quota text");
    await waitForAny(page, [(ctx) => ctx.getByRole("button", { name: /cancelar/i })], "Cancelar button");
    await waitForAny(page, [(ctx) => ctx.getByRole("button", { name: /crear negocio/i })], "Crear Negocio button");

    await takeScreenshot(page, "crear-nuevo-negocio-modal");

    await nombreField.click();
    await nombreField.fill("Negocio Prueba Automatizacion");
    const cancelarButton = await waitForAny(page, [(ctx) => ctx.getByRole("button", { name: /cancelar/i })], "Cancelar");
    await clickAndWait(page, cancelarButton);
    mark("Agregar Negocio modal", true, "Modal validations passed and optional fill/cancel flow executed.");

    // 4) Open Administrar Negocios
    let administrarOption = await findOptional(page, [
      (ctx) => ctx.getByRole("button", { name: /administrar negocios/i }),
      (ctx) => ctx.getByRole("link", { name: /administrar negocios/i }),
      (ctx) => ctx.getByText(/administrar negocios/i)
    ], 3500);

    if (!administrarOption) {
      const miNegocioToggle = await waitForAny(
        page,
        [
          (ctx) => ctx.getByRole("button", { name: /^mi negocio$/i }),
          (ctx) => ctx.getByRole("link", { name: /^mi negocio$/i }),
          (ctx) => ctx.getByText(/^mi negocio$/i)
        ],
        "Mi Negocio toggle"
      );
      await clickAndWait(page, miNegocioToggle);
      administrarOption = await waitForAny(
        page,
        [
          (ctx) => ctx.getByRole("button", { name: /administrar negocios/i }),
          (ctx) => ctx.getByRole("link", { name: /administrar negocios/i }),
          (ctx) => ctx.getByText(/administrar negocios/i)
        ],
        "Administrar Negocios option"
      );
    }

    await clickAndWait(page, administrarOption);

    await waitForAny(page, [(ctx) => ctx.getByText(/informaci[oó]n general/i)], "Información General section");
    await waitForAny(page, [(ctx) => ctx.getByText(/detalles de la cuenta/i)], "Detalles de la Cuenta section");
    await waitForAny(page, [(ctx) => ctx.getByText(/tus negocios/i)], "Tus Negocios section");
    await waitForAny(page, [(ctx) => ctx.getByText(/secci[oó]n legal/i)], "Sección Legal section");
    await takeScreenshot(page, "administrar-negocios-page", true);
    mark("Administrar Negocios view", true, "All required sections are visible.");

    // 5) Validate Información General
    await waitForAny(
      page,
      [
        (ctx) => ctx.getByText(/@/),
        (ctx) => ctx.getByText(/correo|email/i)
      ],
      "user email"
    );
    await waitForAny(page, [(ctx) => ctx.getByText(/business plan/i)], "BUSINESS PLAN text");
    await waitForAny(page, [(ctx) => ctx.getByRole("button", { name: /cambiar plan/i })], "Cambiar Plan button");
    mark("Información General", true, "Name/email/plan details are visible.");

    // 6) Validate Detalles de la Cuenta
    await waitForAny(page, [(ctx) => ctx.getByText(/cuenta creada/i)], "Cuenta creada text");
    await waitForAny(page, [(ctx) => ctx.getByText(/estado activo/i)], "Estado activo text");
    await waitForAny(page, [(ctx) => ctx.getByText(/idioma seleccionado/i)], "Idioma seleccionado text");
    mark("Detalles de la Cuenta", true, "Account details section labels are visible.");

    // 7) Validate Tus Negocios
    await waitForAny(page, [(ctx) => ctx.getByText(/tus negocios/i)], "Tus Negocios heading");
    await waitForAny(
      page,
      [
        (ctx) => ctx.getByRole("button", { name: /agregar negocio/i }),
        (ctx) => ctx.getByRole("link", { name: /agregar negocio/i })
      ],
      "Agregar Negocio button in business section"
    );
    await waitForAny(page, [(ctx) => ctx.getByText(/tienes 2 de 3 negocios/i)], "2 of 3 business quota text");
    mark("Tus Negocios", true, "Business list and controls are visible.");

    // 8) Validate Términos y Condiciones
    const terminosLink = await waitForAny(
      page,
      [
        (ctx) => ctx.getByRole("link", { name: /t[eé]rminos y condiciones/i }),
        (ctx) => ctx.getByRole("button", { name: /t[eé]rminos y condiciones/i }),
        (ctx) => ctx.getByText(/t[eé]rminos y condiciones/i)
      ],
      "Términos y Condiciones link"
    );

    const terminosPopupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, terminosLink);
    const terminosPopup = await terminosPopupPromise;
    const terminosPage = terminosPopup ?? page;
    await waitForUiLoad(terminosPage);
    await validateLegalContent(terminosPage, /t[eé]rminos y condiciones/i);
    report.legalUrls.terminosYCondiciones = terminosPage.url();
    await takeScreenshot(terminosPage, "terminos-y-condiciones", true);

    if (terminosPopup) {
      await terminosPopup.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded", timeout: 15000 }).catch(() => null);
      await waitForUiLoad(page);
    }
    mark("Términos y Condiciones", true, "Legal page opened and content validated.");

    // 9) Validate Política de Privacidad
    const privacyLink = await waitForAny(
      page,
      [
        (ctx) => ctx.getByRole("link", { name: /pol[ií]tica de privacidad/i }),
        (ctx) => ctx.getByRole("button", { name: /pol[ií]tica de privacidad/i }),
        (ctx) => ctx.getByText(/pol[ií]tica de privacidad/i)
      ],
      "Política de Privacidad link"
    );

    const privacyPopupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, privacyLink);
    const privacyPopup = await privacyPopupPromise;
    const privacyPage = privacyPopup ?? page;
    await waitForUiLoad(privacyPage);
    await validateLegalContent(privacyPage, /pol[ií]tica de privacidad/i);
    report.legalUrls.politicaDePrivacidad = privacyPage.url();
    await takeScreenshot(privacyPage, "politica-de-privacidad", true);

    if (privacyPopup) {
      await privacyPopup.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded", timeout: 15000 }).catch(() => null);
      await waitForUiLoad(page);
    }
    mark("Política de Privacidad", true, "Privacy page opened and content validated.");
  } catch (error) {
    const pendingField = REPORT_FIELDS.find((field) => report.details[field] === "Not executed");
    if (pendingField) {
      mark(pendingField, false, `Execution stopped with error: ${error.message}`);
    }
    report.executionError = error.stack ?? error.message;
  } finally {
    report.finishedAt = new Date().toISOString();
    report.overallStatus = Object.values(report.statusByField).every((status) => status === "PASS")
      ? "PASS"
      : "FAIL";

    const reportPath = path.join(artifactRoot, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    console.log(`\nWorkflow report saved to: ${reportPath}`);
    console.table(report.statusByField);
    if (report.legalUrls.terminosYCondiciones) {
      console.log(`Términos y Condiciones URL: ${report.legalUrls.terminosYCondiciones}`);
    }
    if (report.legalUrls.politicaDePrivacidad) {
      console.log(`Política de Privacidad URL: ${report.legalUrls.politicaDePrivacidad}`);
    }

    await context.close();
    await browser.close();

    if (report.overallStatus !== "PASS") {
      process.exitCode = 1;
    }
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
