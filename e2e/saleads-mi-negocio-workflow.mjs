import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const STEP_FIELDS = [
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

const GOOGLE_ACCOUNT = (process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com").trim();
const LOGIN_URL = (process.env.SALEADS_LOGIN_URL || "").trim();
const HEADLESS = (process.env.SALEADS_HEADLESS || "true").toLowerCase() !== "false";
const STORAGE_STATE = (process.env.SALEADS_STORAGE_STATE || "").trim();

const runTimestamp = new Date().toISOString();
const runId = runTimestamp.replace(/[:.]/g, "-");
const artifactsRoot = path.join(__dirname, "artifacts", runId);
const screenshotsDir = path.join(artifactsRoot, "screenshots");
const reportPath = path.join(artifactsRoot, "final-report.json");

const results = Object.fromEntries(
  STEP_FIELDS.map((stepName) => [
    stepName,
    {
      status: "FAIL",
      details: [],
      evidence: []
    }
  ])
);

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function sanitizeFileName(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

function ensureStep(stepName) {
  const step = results[stepName];
  if (!step) {
    throw new Error(`Unknown report step: ${stepName}`);
  }
  return step;
}

function addDetail(stepName, detail) {
  ensureStep(stepName).details.push(detail);
}

function addEvidence(stepName, evidenceLabel, evidenceValue) {
  ensureStep(stepName).evidence.push({
    label: evidenceLabel,
    value: evidenceValue
  });
}

function setPass(stepName) {
  ensureStep(stepName).status = "PASS";
}

function setFail(stepName, reason) {
  const step = ensureStep(stepName);
  step.status = "FAIL";
  if (reason) {
    step.details.push(reason);
  }
}

function failWithPrerequisite(stepName, prerequisiteDescription) {
  setFail(stepName, `Prerequisite failed: ${prerequisiteDescription}`);
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7_000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function firstVisibleLocator(locators) {
  for (const locator of locators) {
    const count = await locator.count().catch(() => 0);
    if (count < 1) {
      continue;
    }

    const first = locator.first();
    const visible = await first.isVisible().catch(() => false);
    if (visible) {
      return first;
    }
  }

  return null;
}

async function findClickableByRegex(page, regex, timeoutMs = 10_000) {
  const started = Date.now();
  const locators = [
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByText(regex)
  ];

  while (Date.now() - started < timeoutMs) {
    const visibleLocator = await firstVisibleLocator(locators);
    if (visibleLocator) {
      return visibleLocator;
    }
    await page.waitForTimeout(250);
  }

  return null;
}

async function clickAndWait(locator, page) {
  await locator.click({ timeout: 12_000 });
  await waitForUi(page);
}

async function textVisible(page, regex, timeoutMs = 10_000) {
  try {
    await page.getByText(regex).first().waitFor({ state: "visible", timeout: timeoutMs });
    return true;
  } catch {
    return false;
  }
}

async function capture(page, stepName, checkpoint, fullPage = false) {
  const screenshotName = `${sanitizeFileName(stepName)}-${sanitizeFileName(checkpoint)}.png`;
  const screenshotPath = path.join(screenshotsDir, screenshotName);
  await page.screenshot({ path: screenshotPath, fullPage });
  addEvidence(stepName, checkpoint, path.relative(artifactsRoot, screenshotPath));
}

async function hasMainAppInterface(page) {
  const sidebarVisible = await firstVisibleLocator([
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator("[class*='sidebar']")
  ]);
  const negocioVisible = await textVisible(page, /negocio|mi negocio/i, 1_500);
  return Boolean(sidebarVisible) && negocioVisible;
}

async function waitForMainAppInterface(page, timeoutMs = 60_000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await hasMainAppInterface(page)) {
      return true;
    }
    await page.waitForTimeout(500);
  }
  return false;
}

async function validateLegalLink({
  stepName,
  page,
  context,
  linkRegex,
  headingRegex
}) {
  const link = await findClickableByRegex(page, linkRegex, 15_000);
  if (!link) {
    throw new Error(`Could not find legal link: ${linkRegex}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await clickAndWait(link, page);

  let legalPage = await popupPromise;
  const openedNewTab = Boolean(legalPage);
  if (!legalPage) {
    legalPage = page;
  }

  await waitForUi(legalPage);

  const headingFoundByRole = await firstVisibleLocator([
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex)
  ]);
  if (!headingFoundByRole) {
    throw new Error(`Expected legal heading not found: ${headingRegex}`);
  }

  const contentText = await legalPage.locator("body").innerText();
  const contentLooksValid = contentText.replace(/\s+/g, " ").trim().length > 200;
  if (!contentLooksValid) {
    throw new Error("Legal content text is not sufficiently visible.");
  }

  await capture(legalPage, stepName, "legal-page");
  addEvidence(stepName, "final_url", legalPage.url());

  if (openedNewTab) {
    await legalPage.close({ runBeforeUnload: true }).catch(() => {});
    await page.bringToFront().catch(() => {});
    await waitForUi(page);
    addDetail(stepName, "Legal page opened in a new tab and was closed after validation.");
  } else {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 20_000 }).catch(() => {});
    await waitForUi(page);
    addDetail(stepName, "Legal page opened in the same tab and navigation returned to app.");
  }
}

async function run() {
  await fs.mkdir(screenshotsDir, { recursive: true });

  let browser;
  let context;
  let page;

  try {
    browser = await chromium.launch({
      headless: HEADLESS
    });

    const contextOptions = {};
    if (STORAGE_STATE) {
      contextOptions.storageState = STORAGE_STATE;
    }

    context = await browser.newContext(contextOptions);
    page = await context.newPage();

    const loginStep = "Login";
    if (!LOGIN_URL) {
      setFail(loginStep, "Missing SALEADS_LOGIN_URL. The script is environment-agnostic and requires the current environment login page URL.");
    } else {
      try {
        await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded", timeout: 60_000 });
        await waitForUi(page);

        if (!(await hasMainAppInterface(page))) {
          const loginButton = await firstVisibleLocator([
            page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
            page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
            page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
          ]);

          if (!loginButton) {
            throw new Error("Could not locate Google sign-in control.");
          }

          const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
          await clickAndWait(loginButton, page);
          const popupPage = await popupPromise;

          if (popupPage) {
            await waitForUi(popupPage);
            const accountRegex = new RegExp(escapeRegex(GOOGLE_ACCOUNT), "i");
            const accountEntry = await findClickableByRegex(popupPage, accountRegex, 12_000);
            if (accountEntry) {
              await accountEntry.click({ timeout: 10_000 });
              await waitForUi(popupPage);
              addDetail(loginStep, `Google account selector handled for ${GOOGLE_ACCOUNT}.`);
            } else {
              addDetail(loginStep, "Google account selector did not appear or account was already pre-selected.");
            }
          } else {
            const accountRegex = new RegExp(escapeRegex(GOOGLE_ACCOUNT), "i");
            const inlineAccountEntry = await findClickableByRegex(page, accountRegex, 7_000);
            if (inlineAccountEntry) {
              await clickAndWait(inlineAccountEntry, page);
              addDetail(loginStep, `Inline account selector handled for ${GOOGLE_ACCOUNT}.`);
            }
          }
        } else {
          addDetail(loginStep, "Session was already authenticated.");
        }

        const appInterfaceVisible = await waitForMainAppInterface(page, 60_000);
        if (!appInterfaceVisible) {
          throw new Error("Main application interface did not appear after login.");
        }

        await capture(page, loginStep, "dashboard-loaded");
        setPass(loginStep);
      } catch (error) {
        setFail(loginStep, error.message);
      }
    }

    if (results["Login"].status !== "PASS") {
      for (const stepName of STEP_FIELDS.slice(1)) {
        failWithPrerequisite(stepName, "Login step did not pass.");
      }
      return;
    }

    const menuStep = "Mi Negocio menu";
    try {
      const negocioSection = await findClickableByRegex(page, /negocio/i, 12_000);
      if (!negocioSection) {
        throw new Error("Sidebar section 'Negocio' was not found.");
      }
      await clickAndWait(negocioSection, page);

      const miNegocioOption = await findClickableByRegex(page, /mi negocio/i, 12_000);
      if (!miNegocioOption) {
        throw new Error("Option 'Mi Negocio' was not found.");
      }
      await clickAndWait(miNegocioOption, page);

      const agregarVisible = await textVisible(page, /agregar negocio/i, 12_000);
      const administrarVisible = await textVisible(page, /administrar negocios/i, 12_000);
      if (!agregarVisible || !administrarVisible) {
        throw new Error("Mi Negocio submenu did not expose expected options.");
      }

      await capture(page, menuStep, "mi-negocio-expanded");
      setPass(menuStep);
    } catch (error) {
      setFail(menuStep, error.message);
    }

    const modalStep = "Agregar Negocio modal";
    if (results[menuStep].status !== "PASS") {
      failWithPrerequisite(modalStep, "Mi Negocio menu step failed.");
    } else {
      try {
        const agregarNegocio = await findClickableByRegex(page, /agregar negocio/i, 12_000);
        if (!agregarNegocio) {
          throw new Error("Could not click 'Agregar Negocio'.");
        }
        await clickAndWait(agregarNegocio, page);

        const titleVisible = await textVisible(page, /crear nuevo negocio/i, 10_000);
        const quotaVisible = await textVisible(page, /tienes 2 de 3 negocios/i, 10_000);
        const cancelVisible = await textVisible(page, /cancelar/i, 10_000);
        const createVisible = await textVisible(page, /crear negocio/i, 10_000);

        const nameField = await firstVisibleLocator([
          page.getByLabel(/nombre del negocio/i),
          page.getByPlaceholder(/nombre del negocio/i),
          page.locator("input[name*='negocio' i]")
        ]);
        const inputVisible = Boolean(nameField);

        if (!titleVisible || !quotaVisible || !cancelVisible || !createVisible || !inputVisible) {
          throw new Error("Agregar Negocio modal is missing one or more required controls.");
        }

        if (nameField) {
          await nameField.fill("Negocio Prueba Automatizacion");
        }

        await capture(page, modalStep, "crear-negocio-modal");

        const cancelButton = await findClickableByRegex(page, /cancelar/i, 10_000);
        if (!cancelButton) {
          throw new Error("Cancel button was not found to close modal.");
        }
        await clickAndWait(cancelButton, page);

        setPass(modalStep);
      } catch (error) {
        setFail(modalStep, error.message);
      }
    }

    const adminStep = "Administrar Negocios view";
    if (results[menuStep].status !== "PASS") {
      failWithPrerequisite(adminStep, "Mi Negocio menu step failed.");
    } else {
      try {
        const miNegocioOption = await findClickableByRegex(page, /mi negocio/i, 8_000);
        if (miNegocioOption) {
          await clickAndWait(miNegocioOption, page);
        }

        const administrar = await findClickableByRegex(page, /administrar negocios/i, 12_000);
        if (!administrar) {
          throw new Error("Could not click 'Administrar Negocios'.");
        }
        await clickAndWait(administrar, page);

        const requiredSections = [
          /informaci[oó]n general/i,
          /detalles de la cuenta/i,
          /tus negocios/i,
          /secci[oó]n legal/i
        ];

        for (const sectionRegex of requiredSections) {
          const visible = await textVisible(page, sectionRegex, 12_000);
          if (!visible) {
            throw new Error(`Missing section: ${sectionRegex}`);
          }
        }

        await capture(page, adminStep, "account-page-full", true);
        setPass(adminStep);
      } catch (error) {
        setFail(adminStep, error.message);
      }
    }

    const infoStep = "Información General";
    if (results[adminStep].status !== "PASS") {
      failWithPrerequisite(infoStep, "Administrar Negocios view step failed.");
    } else {
      try {
        const bodyText = (await page.locator("body").innerText()).replace(/\s+/g, " ");
        const hasEmail = /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i.test(bodyText);
        const hasName = /juan|lucas|barbier|garzon/i.test(bodyText) || /\bnombre\b/i.test(bodyText);
        const hasPlan = await textVisible(page, /business plan/i, 10_000);
        const hasChangePlan = await textVisible(page, /cambiar plan/i, 10_000);

        if (!hasName || !hasEmail || !hasPlan || !hasChangePlan) {
          throw new Error("Información General missing expected user/plan fields.");
        }

        setPass(infoStep);
      } catch (error) {
        setFail(infoStep, error.message);
      }
    }

    const accountDetailsStep = "Detalles de la Cuenta";
    if (results[adminStep].status !== "PASS") {
      failWithPrerequisite(accountDetailsStep, "Administrar Negocios view step failed.");
    } else {
      try {
        const requiredTexts = [/cuenta creada/i, /estado activo/i, /idioma seleccionado/i];
        for (const requiredText of requiredTexts) {
          const visible = await textVisible(page, requiredText, 10_000);
          if (!visible) {
            throw new Error(`Missing detail in account section: ${requiredText}`);
          }
        }

        setPass(accountDetailsStep);
      } catch (error) {
        setFail(accountDetailsStep, error.message);
      }
    }

    const businessesStep = "Tus Negocios";
    if (results[adminStep].status !== "PASS") {
      failWithPrerequisite(businessesStep, "Administrar Negocios view step failed.");
    } else {
      try {
        const hasHeading = await textVisible(page, /tus negocios/i, 10_000);
        const hasAddButton = await textVisible(page, /agregar negocio/i, 10_000);
        const hasQuota = await textVisible(page, /tienes 2 de 3 negocios/i, 10_000);
        const bodyText = (await page.locator("body").innerText()).replace(/\s+/g, " ");
        const hasBusinessList = hasHeading && /negocio/i.test(bodyText);

        if (!hasHeading || !hasAddButton || !hasQuota || !hasBusinessList) {
          throw new Error("Tus Negocios section is missing required content.");
        }

        setPass(businessesStep);
      } catch (error) {
        setFail(businessesStep, error.message);
      }
    }

    const termsStep = "Términos y Condiciones";
    if (results[adminStep].status !== "PASS") {
      failWithPrerequisite(termsStep, "Administrar Negocios view step failed.");
    } else {
      try {
        await validateLegalLink({
          stepName: termsStep,
          page,
          context,
          linkRegex: /t[eé]rminos y condiciones/i,
          headingRegex: /t[eé]rminos y condiciones/i
        });
        setPass(termsStep);
      } catch (error) {
        setFail(termsStep, error.message);
      }
    }

    const privacyStep = "Política de Privacidad";
    if (results[adminStep].status !== "PASS") {
      failWithPrerequisite(privacyStep, "Administrar Negocios view step failed.");
    } else {
      try {
        await validateLegalLink({
          stepName: privacyStep,
          page,
          context,
          linkRegex: /pol[ií]tica de privacidad/i,
          headingRegex: /pol[ií]tica de privacidad/i
        });
        setPass(privacyStep);
      } catch (error) {
        setFail(privacyStep, error.message);
      }
    }
  } catch (error) {
    setFail("Login", `Unhandled run error: ${error.message}`);
    for (const stepName of STEP_FIELDS.slice(1)) {
      if (results[stepName].details.length === 0) {
        failWithPrerequisite(stepName, "Run aborted due to unhandled error.");
      }
    }
  } finally {
    await context?.close().catch(() => {});
    await browser?.close().catch(() => {});

    const finalReport = {
      name: "saleads_mi_negocio_full_test",
      generated_at: runTimestamp,
      environment: {
        login_url: LOGIN_URL || null,
        headless: HEADLESS,
        google_account: GOOGLE_ACCOUNT,
        storage_state: STORAGE_STATE || null
      },
      artifacts_root: artifactsRoot,
      fields: STEP_FIELDS.map((name) => ({
        name,
        status: results[name].status,
        details: results[name].details,
        evidence: results[name].evidence
      })),
      summary: {
        passed: STEP_FIELDS.filter((name) => results[name].status === "PASS").length,
        failed: STEP_FIELDS.filter((name) => results[name].status === "FAIL").length,
        overall: STEP_FIELDS.every((name) => results[name].status === "PASS") ? "PASS" : "FAIL"
      }
    };

    await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");

    console.log(`Final report: ${reportPath}`);
    for (const stepName of STEP_FIELDS) {
      console.log(`${stepName}: ${results[stepName].status}`);
    }

    if (finalReport.summary.overall !== "PASS") {
      process.exitCode = 1;
    }
  }
}

run();
