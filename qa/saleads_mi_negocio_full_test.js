#!/usr/bin/env node

const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const TIMEOUT_MS = Number(process.env.SALEADS_TIMEOUT_MS || 30000);
const EXPECTED_GOOGLE_EMAIL =
  process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const HEADLESS = String(process.env.SALEADS_HEADLESS || "false") === "true";
const START_URL = process.env.SALEADS_LOGIN_URL;
const RUN_STAMP = new Date().toISOString().replaceAll(":", "-");
const ARTIFACT_DIR = path.resolve(
  process.env.SALEADS_ARTIFACT_DIR || path.join(__dirname, "artifacts", RUN_STAMP),
);

const REPORT_KEYS = [
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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACT_DIR, { recursive: true });
}

async function waitForUiLoad(page) {
  try {
    await page.waitForLoadState("networkidle", { timeout: 8000 });
  } catch (_error) {
    await page.waitForLoadState("domcontentloaded", { timeout: 8000 });
  }
  await sleep(500);
}

async function screenshot(page, fileName, fullPage = false) {
  const screenshotPath = path.join(ARTIFACT_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function safeScreenshot(page, fileName, fullPage = false) {
  try {
    return await screenshot(page, fileName, fullPage);
  } catch (_error) {
    return null;
  }
}

async function firstVisibleLocator(page, locatorCandidates) {
  for (const locator of locatorCandidates) {
    if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
      return locator.first();
    }
  }
  return null;
}

async function clickWithUiWait(locator, page) {
  await locator.click({ timeout: TIMEOUT_MS });
  await waitForUiLoad(page);
}

function toAccentFlexibleRegex(text) {
  const map = {
    a: "[aáàäâã]",
    e: "[eéèëê]",
    i: "[iíìïî]",
    o: "[oóòöôõ]",
    u: "[uúùüû]",
    n: "[nñ]",
    c: "[cç]",
  };

  const escaped = text
    .split("")
    .map((char) => {
      const lower = char.toLowerCase();
      if (map[lower]) {
        return map[lower];
      }
      if ("\\^$.*+?()[]{}|".includes(char)) {
        return `\\${char}`;
      }
      return char;
    })
    .join("");

  return new RegExp(escaped, "i");
}

async function expectAnyVisible(page, texts) {
  const candidates = texts.map((text) =>
    page.getByText(toAccentFlexibleRegex(text), { exact: false }),
  );
  const visible = await firstVisibleLocator(page, candidates);
  if (!visible) {
    throw new Error(`None of these texts were visible: ${texts.join(", ")}`);
  }
  return visible;
}

async function expectSectionLabels(page, labels) {
  for (const label of labels) {
    await expectAnyVisible(page, [label]);
  }
}

function makeInitialReport() {
  const report = {};
  for (const key of REPORT_KEYS) {
    report[key] = {
      status: "FAIL",
      details: "",
      evidence: [],
    };
  }
  return report;
}

function markPass(report, key, details, evidence = []) {
  report[key] = {
    status: "PASS",
    details,
    evidence,
  };
}

function markFail(report, key, details, evidence = []) {
  report[key] = {
    status: "FAIL",
    details,
    evidence,
  };
}

async function chooseGoogleAccountIfShown(targetPage) {
  const emailLocator = targetPage.getByText(EXPECTED_GOOGLE_EMAIL, {
    exact: true,
  });
  if ((await emailLocator.count()) > 0) {
    await emailLocator.first().click({ timeout: 10000 });
    await waitForUiLoad(targetPage);
    return true;
  }

  const accountLocator = targetPage
    .locator(`[data-identifier="${EXPECTED_GOOGLE_EMAIL}"]`)
    .first();
  if ((await accountLocator.count()) > 0 && (await accountLocator.isVisible())) {
    await accountLocator.click({ timeout: 10000 });
    await waitForUiLoad(targetPage);
    return true;
  }

  return false;
}

async function validateLegalLink({
  appPage,
  context,
  linkTextCandidates,
  expectedHeadingCandidates,
  report,
  reportKey,
  screenshotName,
}) {
  const legalLink = await firstVisibleLocator(
    appPage,
    linkTextCandidates.map((t) => appPage.getByText(toAccentFlexibleRegex(t), { exact: false })),
  );
  if (!legalLink) {
    throw new Error(`Legal link not found for step ${reportKey}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickWithUiWait(legalLink, appPage);
  const popupPage = await popupPromise;

  let legalPage = appPage;
  let openedInNewTab = false;
  if (popupPage) {
    legalPage = popupPage;
    openedInNewTab = true;
    await popupPage.waitForLoadState("domcontentloaded", { timeout: TIMEOUT_MS });
    await waitForUiLoad(popupPage);
  }

  await expectAnyVisible(legalPage, expectedHeadingCandidates);
  const legalTextLocator = legalPage.locator("main, article, body").first();
  await legalTextLocator.waitFor({ state: "visible", timeout: TIMEOUT_MS });

  const legalScreenshot = await screenshot(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  markPass(report, reportKey, "Validated legal page content and heading.", [
    legalScreenshot,
    `URL: ${finalUrl}`,
  ]);

  if (openedInNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
    return;
  }

  await appPage
    .goBack({ waitUntil: "domcontentloaded", timeout: TIMEOUT_MS })
    .catch(() => null);
  await waitForUiLoad(appPage);
}

async function run() {
  if (!START_URL) {
    throw new Error(
      "Missing SALEADS_LOGIN_URL. Provide the current environment login URL without hardcoding a domain in the script.",
    );
  }

  await ensureArtifactsDir();
  const report = makeInitialReport();
  let browser;

  try {
    browser = await chromium.launch({ headless: HEADLESS });
    const context = await browser.newContext();
    const page = await context.newPage();

    await page.goto(START_URL, { waitUntil: "domcontentloaded", timeout: TIMEOUT_MS });
    await waitForUiLoad(page);

    let stepLoginPassed = false;
    let stepMenuPassed = false;
    let stepAdminPassed = false;

    // Step 1: Login with Google
    try {
      const loginLocator = await firstVisibleLocator(page, [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /google/i }),
        page.getByText("Sign in with Google", { exact: false }),
        page.getByText("Iniciar sesion con Google", { exact: false }),
        page.getByText("Iniciar sesión con Google", { exact: false }),
      ]);

      if (!loginLocator) {
        throw new Error("Google login button was not found.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickWithUiWait(loginLocator, page);
      const popupPage = await popupPromise;

      if (popupPage) {
        await popupPage.waitForLoadState("domcontentloaded", { timeout: TIMEOUT_MS });
        await waitForUiLoad(popupPage);
        await chooseGoogleAccountIfShown(popupPage);
        await popupPage.waitForEvent("close", { timeout: 15000 }).catch(() => null);
        await waitForUiLoad(page);
      } else {
        await chooseGoogleAccountIfShown(page);
      }

      const sidebarOrNav = await firstVisibleLocator(page, [
        page.getByText("Negocio", { exact: false }),
        page.locator("aside"),
        page.getByRole("navigation"),
      ]);
      if (!sidebarOrNav) {
        throw new Error("Main app interface or left sidebar is not visible.");
      }

      const dashboardShot = await screenshot(page, "01-dashboard-loaded.png");
      markPass(report, "Login", "Main interface and sidebar were visible after Google login.", [
        dashboardShot,
      ]);
      stepLoginPassed = true;
    } catch (error) {
      const evidence = [];
      const failureShot = await safeScreenshot(page, "01-login-failure.png");
      if (failureShot) {
        evidence.push(failureShot);
      }
      markFail(report, "Login", error.message, evidence);
    }

    // Step 2: Open Mi Negocio menu
    if (!stepLoginPassed) {
      markFail(
        report,
        "Mi Negocio menu",
        "Could not validate because login step failed.",
      );
    } else {
      try {
        const negocioSection = await firstVisibleLocator(page, [
          page.getByText(toAccentFlexibleRegex("Negocio"), { exact: false }),
          page.getByRole("button", { name: /negocio/i }),
        ]);
        if (!negocioSection) {
          throw new Error("Section 'Negocio' not found in left sidebar.");
        }
        await clickWithUiWait(negocioSection, page);

        const miNegocioOption = await firstVisibleLocator(page, [
          page.getByText(toAccentFlexibleRegex("Mi Negocio"), { exact: false }),
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
        ]);
        if (!miNegocioOption) {
          throw new Error("Option 'Mi Negocio' not found.");
        }
        await clickWithUiWait(miNegocioOption, page);

        await expectAnyVisible(page, ["Agregar Negocio"]);
        await expectAnyVisible(page, ["Administrar Negocios"]);

        const expandedMenuShot = await screenshot(page, "02-mi-negocio-expanded-menu.png");
        markPass(
          report,
          "Mi Negocio menu",
          "Menu expanded and both submenu options are visible.",
          [expandedMenuShot],
        );
        stepMenuPassed = true;
      } catch (error) {
        const evidence = [];
        const failureShot = await safeScreenshot(page, "02-mi-negocio-menu-failure.png");
        if (failureShot) {
          evidence.push(failureShot);
        }
        markFail(report, "Mi Negocio menu", error.message, evidence);
      }
    }

    // Step 3: Validate Agregar Negocio modal
    if (!stepMenuPassed) {
      markFail(
        report,
        "Agregar Negocio modal",
        "Could not validate because Mi Negocio menu step failed.",
      );
    } else {
      try {
        const agregarNegocioOption = await expectAnyVisible(page, ["Agregar Negocio"]);
        await clickWithUiWait(agregarNegocioOption, page);

        await expectAnyVisible(page, ["Crear Nuevo Negocio"]);
        const inputLabel = await firstVisibleLocator(page, [
          page.getByLabel(toAccentFlexibleRegex("Nombre del Negocio")),
          page.getByPlaceholder(toAccentFlexibleRegex("Nombre del Negocio")),
          page.getByText(toAccentFlexibleRegex("Nombre del Negocio"), { exact: false }),
        ]);
        if (!inputLabel) {
          throw new Error("Input field 'Nombre del Negocio' is not available.");
        }

        await expectAnyVisible(page, ["Tienes 2 de 3 negocios"]);
        await expectAnyVisible(page, ["Cancelar"]);
        await expectAnyVisible(page, ["Crear Negocio"]);

        if ((await page.getByLabel(toAccentFlexibleRegex("Nombre del Negocio")).count()) > 0) {
          await page
            .getByLabel(toAccentFlexibleRegex("Nombre del Negocio"))
            .first()
            .fill("Negocio Prueba Automatizacion");
        } else if (
          (await page.getByPlaceholder(toAccentFlexibleRegex("Nombre del Negocio")).count()) > 0
        ) {
          await page
            .getByPlaceholder(toAccentFlexibleRegex("Nombre del Negocio"))
            .first()
            .fill("Negocio Prueba Automatizacion");
        }

        const modalShot = await screenshot(page, "03-agregar-negocio-modal.png");
        await clickWithUiWait(
          page.getByText(toAccentFlexibleRegex("Cancelar"), { exact: false }).first(),
          page,
        );

        markPass(report, "Agregar Negocio modal", "Modal fields and controls validated successfully.", [
          modalShot,
        ]);
      } catch (error) {
        const evidence = [];
        const failureShot = await safeScreenshot(page, "03-agregar-negocio-modal-failure.png");
        if (failureShot) {
          evidence.push(failureShot);
        }
        markFail(report, "Agregar Negocio modal", error.message, evidence);
      }
    }

    // Step 4: Open Administrar Negocios
    if (!stepMenuPassed) {
      markFail(
        report,
        "Administrar Negocios view",
        "Could not validate because Mi Negocio menu step failed.",
      );
    } else {
      try {
        if (
          (await page
            .getByText(toAccentFlexibleRegex("Administrar Negocios"), { exact: false })
            .count()) === 0
        ) {
          const miNegocioOption = await firstVisibleLocator(page, [
            page.getByText(toAccentFlexibleRegex("Mi Negocio"), { exact: false }),
            page.getByRole("button", { name: /mi negocio/i }),
          ]);
          if (!miNegocioOption) {
            throw new Error("Could not re-open 'Mi Negocio' before opening account page.");
          }
          await clickWithUiWait(miNegocioOption, page);
        }

        await clickWithUiWait(
          page.getByText(toAccentFlexibleRegex("Administrar Negocios"), { exact: false }).first(),
          page,
        );

        await expectSectionLabels(page, [
          "Informacion General",
          "Detalles de la Cuenta",
          "Tus Negocios",
          "Seccion Legal",
        ]);

        const accountPageShot = await screenshot(page, "04-administrar-negocios-page.png", true);
        markPass(report, "Administrar Negocios view", "Account sections were displayed correctly.", [
          accountPageShot,
        ]);
        stepAdminPassed = true;
      } catch (error) {
        const evidence = [];
        const failureShot = await safeScreenshot(page, "04-administrar-negocios-failure.png");
        if (failureShot) {
          evidence.push(failureShot);
        }
        markFail(report, "Administrar Negocios view", error.message, evidence);
      }
    }

    // Step 5: Validate Informacion General
    if (!stepAdminPassed) {
      markFail(
        report,
        "Información General",
        "Could not validate because Administrar Negocios view step failed.",
      );
    } else {
      try {
        const emailVisible = await firstVisibleLocator(page, [page.locator("text=/@/")]);
        if (!emailVisible) {
          throw new Error("User email is not visible.");
        }
        const probableName = await firstVisibleLocator(page, [page.locator("h1, h2, h3, strong")]);
        if (!probableName) {
          throw new Error("User name is not visible.");
        }
        await expectAnyVisible(page, ["BUSINESS PLAN"]);
        await expectAnyVisible(page, ["Cambiar Plan"]);
        markPass(
          report,
          "Información General",
          "User identity information and plan controls are visible.",
        );
      } catch (error) {
        markFail(report, "Información General", error.message);
      }
    }

    // Step 6: Validate Detalles de la Cuenta
    if (!stepAdminPassed) {
      markFail(
        report,
        "Detalles de la Cuenta",
        "Could not validate because Administrar Negocios view step failed.",
      );
    } else {
      try {
        await expectSectionLabels(page, ["Cuenta creada", "Estado activo", "Idioma seleccionado"]);
        markPass(report, "Detalles de la Cuenta", "Account details section labels are visible.");
      } catch (error) {
        markFail(report, "Detalles de la Cuenta", error.message);
      }
    }

    // Step 7: Validate Tus Negocios
    if (!stepAdminPassed) {
      markFail(
        report,
        "Tus Negocios",
        "Could not validate because Administrar Negocios view step failed.",
      );
    } else {
      try {
        await expectSectionLabels(page, ["Tus Negocios", "Agregar Negocio", "Tienes 2 de 3 negocios"]);
        markPass(report, "Tus Negocios", "Business list and counters are visible.");
      } catch (error) {
        markFail(report, "Tus Negocios", error.message);
      }
    }

    // Step 8: Validate Terminos y Condiciones
    if (!stepAdminPassed) {
      markFail(
        report,
        "Términos y Condiciones",
        "Could not validate because Administrar Negocios view step failed.",
      );
    } else {
      try {
        await validateLegalLink({
          appPage: page,
          context,
          linkTextCandidates: ["Terminos y Condiciones", "Términos y Condiciones"],
          expectedHeadingCandidates: ["Terminos y Condiciones", "Términos y Condiciones"],
          report,
          reportKey: "Términos y Condiciones",
          screenshotName: "08-terminos-y-condiciones.png",
        });
      } catch (error) {
        const evidence = [];
        const failureShot = await safeScreenshot(page, "08-terminos-failure.png");
        if (failureShot) {
          evidence.push(failureShot);
        }
        markFail(report, "Términos y Condiciones", error.message, evidence);
      }
    }

    // Step 9: Validate Politica de Privacidad
    if (!stepAdminPassed) {
      markFail(
        report,
        "Política de Privacidad",
        "Could not validate because Administrar Negocios view step failed.",
      );
    } else {
      try {
        await validateLegalLink({
          appPage: page,
          context,
          linkTextCandidates: ["Politica de Privacidad", "Política de Privacidad"],
          expectedHeadingCandidates: ["Politica de Privacidad", "Política de Privacidad"],
          report,
          reportKey: "Política de Privacidad",
          screenshotName: "09-politica-de-privacidad.png",
        });
      } catch (error) {
        const evidence = [];
        const failureShot = await safeScreenshot(page, "09-privacidad-failure.png");
        if (failureShot) {
          evidence.push(failureShot);
        }
        markFail(report, "Política de Privacidad", error.message, evidence);
      }
    }
  } catch (error) {
    console.error(`Workflow failed: ${error.message}`);
    process.exitCode = 1;
  } finally {
    const reportPath = path.join(ARTIFACT_DIR, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    const hasFailures = REPORT_KEYS.some((field) => report[field].status !== "PASS");

    console.log("\nFinal Report");
    console.table(
      REPORT_KEYS.map((field) => ({
        field,
        status: report[field].status,
        details: report[field].details,
      })),
    );
    console.log(`\nEvidence directory: ${ARTIFACT_DIR}`);
    console.log(`Report file: ${reportPath}`);

    if (browser) {
      await browser.close();
    }
    if (hasFailures) {
      process.exitCode = 1;
    }
  }
}

run();
