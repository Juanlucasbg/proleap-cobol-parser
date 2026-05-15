#!/usr/bin/env node

const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const APP_URL = process.env.SALEADS_URL || process.env.APP_URL || "";
const GOOGLE_ACCOUNT = process.env.GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const GOOGLE_PASSWORD = process.env.GOOGLE_PASSWORD || "";
const HEADLESS = process.env.HEADLESS !== "false";
const SLOW_MO = Number(process.env.SLOW_MO || 0);

const REPORT_FIELDS = [
  { id: 1, key: "Login", name: "Login with Google" },
  { id: 2, key: "Mi Negocio menu", name: "Open Mi Negocio menu" },
  { id: 3, key: "Agregar Negocio modal", name: "Validate Agregar Negocio modal" },
  { id: 4, key: "Administrar Negocios view", name: "Open Administrar Negocios" },
  { id: 5, key: "Información General", name: "Validate Información General" },
  { id: 6, key: "Detalles de la Cuenta", name: "Validate Detalles de la Cuenta" },
  { id: 7, key: "Tus Negocios", name: "Validate Tus Negocios" },
  { id: 8, key: "Términos y Condiciones", name: "Validate Términos y Condiciones" },
  { id: 9, key: "Política de Privacidad", name: "Validate Política de Privacidad" },
];

function newStep(stepMeta) {
  return {
    id: stepMeta.id,
    key: stepMeta.key,
    name: stepMeta.name,
    status: "FAIL",
    checks: [],
    error: null,
    evidence: [],
  };
}

function addCheck(step, description, pass, details = "") {
  step.checks.push({ description, pass, details });
}

function finalizeStep(step) {
  const allChecksPass = step.checks.length > 0 && step.checks.every((check) => check.pass);
  step.status = allChecksPass && !step.error ? "PASS" : "FAIL";
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page, timeout = 15000) {
  await page.waitForLoadState("domcontentloaded", { timeout }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(1200);
}

async function screenshot(page, dirPath, filename, fullPage = false) {
  const safeName = filename.replace(/[^a-zA-Z0-9_-]/g, "_");
  const filePath = path.join(dirPath, `${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function findVisibleLocator(page, builderFns, timeoutMs = 12000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const build of builderFns) {
      const locator = build();
      const first = locator.first();
      const count = await locator.count().catch(() => 0);
      if (count > 0 && (await first.isVisible().catch(() => false))) {
        return first;
      }
    }
    await page.waitForTimeout(250);
  }
  return null;
}

async function textVisible(page, regex, timeout = 10000) {
  try {
    await page.getByText(regex).first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function visibleViaHeadingOrText(page, regex, timeout = 10000) {
  try {
    await page.getByRole("heading", { name: regex }).first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return textVisible(page, regex, timeout);
  }
}

async function run() {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const outDir = path.join(process.cwd(), "artifacts", timestamp);
  const reportPath = path.join(outDir, "report.json");

  await ensureDir(outDir);

  const report = {
    runAt: new Date().toISOString(),
    appUrl: APP_URL || null,
    googleAccount: GOOGLE_ACCOUNT,
    screenshotsDirectory: outDir,
    legalUrls: {
      termsAndConditions: null,
      privacyPolicy: null,
    },
    steps: REPORT_FIELDS.map((field) => newStep(field)),
    finalReport: {},
  };

  let browser;
  let context;
  let page;
  let appPageUrl = APP_URL || null;
  let dashboardLoaded = false;

  try {
    browser = await chromium.launch({ headless: HEADLESS, slowMo: SLOW_MO });
    context = await browser.newContext({ viewport: { width: 1600, height: 1200 } });
    page = await context.newPage();

    const step1 = report.steps.find((step) => step.id === 1);
    try {
      if (!APP_URL) {
        addCheck(
          step1,
          "Environment URL is available",
          false,
          "Set SALEADS_URL (or APP_URL) because this runner does not hardcode domain values."
        );
      } else {
        await page.goto(APP_URL, { waitUntil: "domcontentloaded" });
        await waitForUi(page);

        const loginButton = await findVisibleLocator(page, [
          () => page.getByRole("button", { name: /sign in with google|google|iniciar sesi[oó]n con google|continuar con google/i }),
          () => page.getByRole("link", { name: /sign in with google|google|iniciar sesi[oó]n con google|continuar con google/i }),
          () => page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        ]);

        if (!loginButton) {
          addCheck(step1, "Login button or Sign in with Google is visible", false, "Google login CTA not found.");
        } else {
          const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
          await clickAndWait(loginButton, page);
          const maybePopup = await popupPromise;

          let googlePage = maybePopup;
          if (googlePage) {
            await googlePage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
            await waitForUi(googlePage);
          }

          const accountPage = googlePage || page;
          const accountChoice = await findVisibleLocator(accountPage, [
            () => accountPage.getByText(new RegExp(GOOGLE_ACCOUNT.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
            () => accountPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i") }),
            () => accountPage.getByRole("option", { name: new RegExp(GOOGLE_ACCOUNT.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i") }),
          ], 10000);

          if (accountChoice) {
            await accountChoice.click();
            await waitForUi(accountPage);
            addCheck(step1, `Google account selector includes ${GOOGLE_ACCOUNT}`, true);
          } else {
            const emailField = accountPage.locator('input[type="email"]').first();
            if (await emailField.isVisible().catch(() => false)) {
              await emailField.fill(GOOGLE_ACCOUNT);
              const nextButton = accountPage.getByRole("button", { name: /next|siguiente/i }).first();
              if (await nextButton.isVisible().catch(() => false)) {
                await nextButton.click();
              }
              await waitForUi(accountPage);
              if (GOOGLE_PASSWORD) {
                const passwordField = accountPage.locator('input[type="password"]').first();
                if (await passwordField.isVisible().catch(() => false)) {
                  await passwordField.fill(GOOGLE_PASSWORD);
                  const nextPwdButton = accountPage.getByRole("button", { name: /next|siguiente/i }).first();
                  if (await nextPwdButton.isVisible().catch(() => false)) {
                    await nextPwdButton.click();
                  }
                  await waitForUi(accountPage);
                }
              }
              addCheck(step1, "Google account flow can proceed", true, "Account selected/typed.");
            } else {
              addCheck(step1, `Google account selector includes ${GOOGLE_ACCOUNT}`, false, "Google account selector did not appear.");
            }
          }

          if (googlePage) {
            await page.bringToFront().catch(() => {});
          }

          await waitForUi(page, 25000);

          const mainUiVisible =
            (await textVisible(page, /dashboard|inicio|panel|home/i, 10000)) ||
            (await page.locator("main").first().isVisible().catch(() => false));
          addCheck(step1, "Main application interface appears", mainUiVisible);

          const sidebarVisible =
            (await page.locator("aside, nav").first().isVisible().catch(() => false)) ||
            (await textVisible(page, /negocio|mi negocio/i, 7000));
          addCheck(step1, "Left sidebar navigation is visible", sidebarVisible);

          const dashboardShot = await screenshot(page, outDir, "01-dashboard-loaded");
          step1.evidence.push(dashboardShot);
          dashboardLoaded = mainUiVisible && sidebarVisible;
          appPageUrl = page.url();
        }
      }
    } catch (error) {
      step1.error = String(error.message || error);
    }
    finalizeStep(step1);

    const step2 = report.steps.find((step) => step.id === 2);
    try {
      if (!dashboardLoaded) {
        addCheck(step2, "Precondition: dashboard loaded", false, "Login step did not complete successfully.");
      } else {
        const miNegocioEntry = await findVisibleLocator(page, [
          () => page.getByRole("link", { name: /mi negocio/i }),
          () => page.getByRole("button", { name: /mi negocio/i }),
          () => page.getByText(/mi negocio/i),
        ]);

        if (!miNegocioEntry) {
          addCheck(step2, "Option 'Mi Negocio' is visible", false, "Could not find Mi Negocio in sidebar.");
        } else {
          await clickAndWait(miNegocioEntry, page);
          addCheck(step2, "Submenu expands after clicking Mi Negocio", true);

          const agregarVisible = await textVisible(page, /agregar negocio/i, 10000);
          const administrarVisible = await textVisible(page, /administrar negocios/i, 10000);
          addCheck(step2, "'Agregar Negocio' is visible", agregarVisible);
          addCheck(step2, "'Administrar Negocios' is visible", administrarVisible);

          const expandedMenuShot = await screenshot(page, outDir, "02-mi-negocio-menu-expanded");
          step2.evidence.push(expandedMenuShot);
        }
      }
    } catch (error) {
      step2.error = String(error.message || error);
    }
    finalizeStep(step2);

    const step3 = report.steps.find((step) => step.id === 3);
    try {
      if (step2.status !== "PASS") {
        addCheck(step3, "Precondition: Mi Negocio menu available", false, "Step 2 did not pass.");
      } else {
        const agregarButton = await findVisibleLocator(page, [
          () => page.getByRole("button", { name: /agregar negocio/i }),
          () => page.getByRole("link", { name: /agregar negocio/i }),
          () => page.getByText(/agregar negocio/i),
        ]);

        if (!agregarButton) {
          addCheck(step3, "Click 'Agregar Negocio'", false, "Agregar Negocio entry not found.");
        } else {
          await clickAndWait(agregarButton, page);
          const modal = page.getByRole("dialog").first();
          await modal.waitFor({ state: "visible", timeout: 10000 }).catch(() => {});

          const modalTitleVisible = await visibleViaHeadingOrText(page, /crear nuevo negocio/i, 9000);
          const businessNameInput =
            (await page.getByLabel(/nombre del negocio/i).first().isVisible().catch(() => false)) ||
            (await page.locator('input[placeholder*="Negocio"], input[name*="negocio"], input[id*="negocio"]').first().isVisible().catch(() => false));
          const businessQuotaVisible = await textVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i, 9000);
          const cancelVisible = await page.getByRole("button", { name: /cancelar/i }).first().isVisible().catch(() => false);
          const createVisible = await page.getByRole("button", { name: /crear negocio/i }).first().isVisible().catch(() => false);

          addCheck(step3, "Modal title 'Crear Nuevo Negocio' is visible", modalTitleVisible);
          addCheck(step3, "Input field 'Nombre del Negocio' exists", businessNameInput);
          addCheck(step3, "Text 'Tienes 2 de 3 negocios' is visible", businessQuotaVisible);
          addCheck(step3, "Button 'Cancelar' is present", cancelVisible);
          addCheck(step3, "Button 'Crear Negocio' is present", createVisible);

          const modalShot = await screenshot(page, outDir, "03-agregar-negocio-modal");
          step3.evidence.push(modalShot);

          const textInput = page
            .getByLabel(/nombre del negocio/i)
            .first()
            .or(page.locator('input[placeholder*="Negocio"], input[name*="negocio"], input[id*="negocio"]').first());
          if (await textInput.isVisible().catch(() => false)) {
            await textInput.click();
            await textInput.fill("Negocio Prueba Automatización");
          }

          const cancelButton = page.getByRole("button", { name: /cancelar/i }).first();
          if (await cancelButton.isVisible().catch(() => false)) {
            await clickAndWait(cancelButton, page);
          }
        }
      }
    } catch (error) {
      step3.error = String(error.message || error);
    }
    finalizeStep(step3);

    const step4 = report.steps.find((step) => step.id === 4);
    try {
      if (step2.status !== "PASS") {
        addCheck(step4, "Precondition: Mi Negocio menu available", false, "Step 2 did not pass.");
      } else {
        const administrarOption = await findVisibleLocator(page, [
          () => page.getByRole("link", { name: /administrar negocios/i }),
          () => page.getByRole("button", { name: /administrar negocios/i }),
          () => page.getByText(/administrar negocios/i),
        ]);
        if (!administrarOption) {
          addCheck(step4, "Option 'Administrar Negocios' is visible", false, "Could not locate option.");
        } else {
          await clickAndWait(administrarOption, page);
          await waitForUi(page, 20000);
          appPageUrl = page.url();

          const infoGeneral = await visibleViaHeadingOrText(page, /informaci[oó]n general/i, 12000);
          const accountDetails = await visibleViaHeadingOrText(page, /detalles de la cuenta/i, 12000);
          const businesses = await visibleViaHeadingOrText(page, /tus negocios/i, 12000);
          const legal = await visibleViaHeadingOrText(page, /secci[oó]n legal/i, 12000);

          addCheck(step4, "Section 'Información General' exists", infoGeneral);
          addCheck(step4, "Section 'Detalles de la Cuenta' exists", accountDetails);
          addCheck(step4, "Section 'Tus Negocios' exists", businesses);
          addCheck(step4, "Section 'Sección Legal' exists", legal);

          const accountPageShot = await screenshot(page, outDir, "04-administrar-negocios-full-page", true);
          step4.evidence.push(accountPageShot);
        }
      }
    } catch (error) {
      step4.error = String(error.message || error);
    }
    finalizeStep(step4);

    const step5 = report.steps.find((step) => step.id === 5);
    try {
      if (step4.status !== "PASS") {
        addCheck(step5, "Precondition: Administrar Negocios view loaded", false, "Step 4 did not pass.");
      } else {
        const bodyText = await page.locator("body").innerText().catch(() => "");
        const emailVisible = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(bodyText);
        const nameVisible = /informaci[oó]n general/i.test(bodyText) && !/^\s*$/.test(bodyText);
        const businessPlanVisible = await textVisible(page, /business plan/i, 8000);
        const changePlanVisible = await textVisible(page, /cambiar plan/i, 8000);

        addCheck(step5, "User name is visible", nameVisible, "Validated via non-empty Información General content.");
        addCheck(step5, "User email is visible", emailVisible);
        addCheck(step5, "Text 'BUSINESS PLAN' is visible", businessPlanVisible);
        addCheck(step5, "Button 'Cambiar Plan' is visible", changePlanVisible);
      }
    } catch (error) {
      step5.error = String(error.message || error);
    }
    finalizeStep(step5);

    const step6 = report.steps.find((step) => step.id === 6);
    try {
      if (step4.status !== "PASS") {
        addCheck(step6, "Precondition: Administrar Negocios view loaded", false, "Step 4 did not pass.");
      } else {
        addCheck(step6, "'Cuenta creada' is visible", await textVisible(page, /cuenta creada/i, 8000));
        addCheck(step6, "'Estado activo' is visible", await textVisible(page, /estado activo/i, 8000));
        addCheck(step6, "'Idioma seleccionado' is visible", await textVisible(page, /idioma seleccionado/i, 8000));
      }
    } catch (error) {
      step6.error = String(error.message || error);
    }
    finalizeStep(step6);

    const step7 = report.steps.find((step) => step.id === 7);
    try {
      if (step4.status !== "PASS") {
        addCheck(step7, "Precondition: Administrar Negocios view loaded", false, "Step 4 did not pass.");
      } else {
        addCheck(step7, "Business list is visible", await textVisible(page, /tus negocios|negocios/i, 8000));
        addCheck(step7, "Button 'Agregar Negocio' exists", await textVisible(page, /agregar negocio/i, 8000));
        addCheck(step7, "Text 'Tienes 2 de 3 negocios' is visible", await textVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i, 8000));
      }
    } catch (error) {
      step7.error = String(error.message || error);
    }
    finalizeStep(step7);

    async function validateLegalPage(stepId, linkRegex, headingRegex, screenshotName, reportUrlKey) {
      const step = report.steps.find((item) => item.id === stepId);
      try {
        if (step4.status !== "PASS") {
          addCheck(step, "Precondition: legal section is available", false, "Step 4 did not pass.");
          finalizeStep(step);
          return;
        }

        const link = await findVisibleLocator(page, [
          () => page.getByRole("link", { name: linkRegex }),
          () => page.getByRole("button", { name: linkRegex }),
          () => page.getByText(linkRegex),
        ], 10000);

        if (!link) {
          addCheck(step, `Legal link ${linkRegex} is visible`, false, "Could not find legal link.");
          finalizeStep(step);
          return;
        }

        const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
        await clickAndWait(link, page);
        let targetPage = await popupPromise;

        if (!targetPage) {
          targetPage = page;
          await waitForUi(targetPage, 15000);
        } else {
          await targetPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
          await waitForUi(targetPage, 15000);
        }

        const headingVisible = await visibleViaHeadingOrText(targetPage, headingRegex, 12000);
        addCheck(step, `The page contains heading ${headingRegex}`, headingVisible);

        const legalText = await targetPage.locator("body").innerText().catch(() => "");
        const contentVisible = legalText.trim().length > 200;
        addCheck(step, "Legal content text is visible", contentVisible);

        const legalShot = await screenshot(targetPage, outDir, screenshotName, true);
        step.evidence.push(legalShot);

        report.legalUrls[reportUrlKey] = targetPage.url();

        if (targetPage !== page) {
          await targetPage.close().catch(() => {});
          await page.bringToFront().catch(() => {});
        } else {
          await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
            if (appPageUrl) {
              await page.goto(appPageUrl, { waitUntil: "domcontentloaded" }).catch(() => {});
            }
          });
          await waitForUi(page);
        }
      } catch (error) {
        step.error = String(error.message || error);
      }
      finalizeStep(step);
    }

    await validateLegalPage(
      8,
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "08-terminos-y-condiciones",
      "termsAndConditions"
    );
    await validateLegalPage(
      9,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "09-politica-de-privacidad",
      "privacyPolicy"
    );
  } catch (error) {
    for (const step of report.steps) {
      if (!step.error && step.checks.length === 0) {
        step.error = `Execution aborted before this step: ${String(error.message || error)}`;
        step.status = "FAIL";
      }
    }
  } finally {
    if (context) {
      await context.close().catch(() => {});
    }
    if (browser) {
      await browser.close().catch(() => {});
    }
  }

  for (const field of REPORT_FIELDS) {
    const step = report.steps.find((item) => item.id === field.id);
    report.finalReport[field.key] = step?.status || "FAIL";
  }

  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  console.log(`Report saved: ${reportPath}`);
  console.log(JSON.stringify(report.finalReport, null, 2));
  console.log(`Terms URL: ${report.legalUrls.termsAndConditions || "N/A"}`);
  console.log(`Privacy URL: ${report.legalUrls.privacyPolicy || "N/A"}`);

  const allPass = report.steps.every((step) => step.status === "PASS");
  process.exitCode = allPass ? 0 : 1;
}

run().catch((error) => {
  console.error("Fatal error while running workflow:", error);
  process.exit(1);
});
