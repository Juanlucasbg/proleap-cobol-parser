import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const REQUIRED_REPORT_FIELDS = [
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

const EMAIL_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

const runTimestamp = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.resolve("e2e-artifacts", "saleads-mi-negocio", runTimestamp);
const screenshotsDir = path.join(artifactsDir, "screenshots");
const reportPath = path.join(artifactsDir, "final-report.json");

const report = {
  name: "saleads_mi_negocio_full_test",
  goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
  startedAt: new Date().toISOString(),
  artifactsDir,
  legalUrls: {},
  results: Object.fromEntries(
    REQUIRED_REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }])
  )
};

function setResult(field, status, details) {
  report.results[field] = { status, details };
}

async function ensureArtifacts() {
  await fs.mkdir(screenshotsDir, { recursive: true });
}

async function screenshot(page, fileName, options = {}) {
  const target = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: target, fullPage: true, ...options });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function tryVisible(locator, timeout = 2500) {
  try {
    return await locator.first().isVisible({ timeout });
  } catch {
    return false;
  }
}

async function firstVisible(candidates, timeout = 2500) {
  for (const candidate of candidates) {
    if (await tryVisible(candidate, timeout)) {
      return candidate.first();
    }
  }
  return null;
}

async function clickAndWait(page, locator) {
  await locator.waitFor({ state: "visible", timeout: 20000 });
  await locator.click();
  await waitForUi(page);
}

async function hasAnyVisible(page, texts) {
  for (const text of texts) {
    if (await tryVisible(page.getByText(text, { exact: false }))) {
      return true;
    }
  }
  return false;
}

function getLaunchMode() {
  const raw = process.env.HEADLESS;
  if (!raw) return true;
  return raw.toLowerCase() !== "false";
}

async function resolveContextAndPage() {
  const cdpUrl = process.env.CDP_URL;
  if (cdpUrl) {
    const browser = await chromium.connectOverCDP(cdpUrl);
    const context = browser.contexts()[0] ?? (await browser.newContext());
    const existingPage =
      context.pages().find((candidate) => !candidate.url().startsWith("about:blank")) ??
      context.pages()[0] ??
      (await context.newPage());
    return { browser, context, page: existingPage, usesConnectedBrowser: true };
  }

  const browser = await chromium.launch({ headless: getLaunchMode() });
  const context = await browser.newContext();
  const page = await context.newPage();

  const targetUrl = process.env.SALEADS_URL || process.env.APP_URL || process.env.BASE_URL;
  if (!targetUrl) {
    throw new Error(
      "No pre-opened browser was provided and no URL env var was set. Set CDP_URL to an existing browser page or set SALEADS_URL/APP_URL/BASE_URL to the current environment login URL."
    );
  }

  await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);
  return { browser, context, page, usesConnectedBrowser: false };
}

async function validateLegalPageContent(targetPage, expectedHeading) {
  const heading = await firstVisible(
    [
      targetPage.getByRole("heading", { name: expectedHeading, exact: false }),
      targetPage.getByText(expectedHeading, { exact: false })
    ],
    15000
  );

  if (!heading) {
    throw new Error(`Heading "${expectedHeading}" was not found.`);
  }

  const textContent = await targetPage.locator("body").innerText();
  if (textContent.replace(/\s+/g, " ").trim().length < 120) {
    throw new Error(`Legal content appears too short for "${expectedHeading}".`);
  }
}

async function openLegalLink({
  appPage,
  context,
  linkText,
  expectedHeading,
  screenshotFileName,
  resultField,
  urlField
}) {
  const linkLocator = await firstVisible(
    [
      appPage.getByRole("link", { name: linkText, exact: false }),
      appPage.getByText(linkText, { exact: false })
    ],
    15000
  );

  if (!linkLocator) {
    throw new Error(`Link "${linkText}" was not found in legal section.`);
  }

  const newTabPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  await clickAndWait(appPage, linkLocator);
  const maybeNewPage = await newTabPromise;
  const targetPage = maybeNewPage ?? appPage;

  await waitForUi(targetPage);
  await validateLegalPageContent(targetPage, expectedHeading);
  report.legalUrls[urlField] = targetPage.url();
  await screenshot(targetPage, screenshotFileName);

  if (maybeNewPage) {
    await maybeNewPage.close({ runBeforeUnload: true }).catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  setResult(resultField, "PASS", `${expectedHeading} validated at ${report.legalUrls[urlField]}`);
}

async function main() {
  await ensureArtifacts();
  let browser;
  let page;
  let context;

  try {
    const resolved = await resolveContextAndPage();
    browser = resolved.browser;
    page = resolved.page;
    context = resolved.context;

    await waitForUi(page);

    // Step 1: Login with Google
    try {
      const loginButton = await firstVisible(
        [
          page.getByRole("button", {
            name: /google|sign in|iniciar sesi[oó]n|continuar con google|acceder con google/i
          }),
          page.getByRole("link", {
            name: /google|sign in|iniciar sesi[oó]n|continuar con google|acceder con google/i
          }),
          page.getByText(/google|sign in|iniciar sesi[oó]n|continuar con google/i)
        ],
        30000
      );

      if (!loginButton) {
        throw new Error("Google login button was not found.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const popupPage = await popupPromise;

      if (popupPage) {
        await waitForUi(popupPage);
        const emailOption = await firstVisible(
          [
            popupPage.getByText(EMAIL_ACCOUNT, { exact: false }),
            popupPage.getByRole("button", { name: EMAIL_ACCOUNT, exact: false })
          ],
          15000
        );

        if (emailOption) {
          await clickAndWait(popupPage, emailOption);
          await popupPage.waitForClose({ timeout: 120000 }).catch(() => {});
          await page.bringToFront();
        }
      } else {
        const samePageEmailOption = await firstVisible(
          [
            page.getByText(EMAIL_ACCOUNT, { exact: false }),
            page.getByRole("button", { name: EMAIL_ACCOUNT, exact: false })
          ],
          6000
        );

        if (samePageEmailOption) {
          await clickAndWait(page, samePageEmailOption);
        }
      }

      await waitForUi(page);

      const mainUiVisible = await hasAnyVisible(page, ["Negocio", "Mi Negocio", "Dashboard", "Inicio"]);
      const sidebarVisible = await firstVisible(
        [
          page.locator("aside"),
          page.locator("nav"),
          page.getByRole("navigation")
        ],
        10000
      );

      if (!mainUiVisible || !sidebarVisible) {
        throw new Error("Main application interface and sidebar were not both visible after login.");
      }

      await screenshot(page, "01-dashboard-loaded.png");
      setResult("Login", "PASS", "Dashboard loaded and sidebar detected.");
    } catch (error) {
      setResult("Login", "FAIL", String(error.message || error));
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      const negocioSection = await firstVisible(
        [
          page.getByRole("button", { name: "Negocio", exact: false }),
          page.getByRole("link", { name: "Negocio", exact: false }),
          page.getByText("Negocio", { exact: false })
        ],
        12000
      );

      if (negocioSection) {
        await clickAndWait(page, negocioSection);
      }

      const miNegocio = await firstVisible(
        [
          page.getByRole("button", { name: "Mi Negocio", exact: false }),
          page.getByRole("link", { name: "Mi Negocio", exact: false }),
          page.getByText("Mi Negocio", { exact: false })
        ],
        15000
      );

      if (!miNegocio) {
        throw new Error('"Mi Negocio" option was not found.');
      }

      await clickAndWait(page, miNegocio);

      const agregarVisible = await firstVisible(
        [
          page.getByRole("link", { name: "Agregar Negocio", exact: false }),
          page.getByRole("button", { name: "Agregar Negocio", exact: false }),
          page.getByText("Agregar Negocio", { exact: false })
        ],
        15000
      );

      const administrarVisible = await firstVisible(
        [
          page.getByRole("link", { name: "Administrar Negocios", exact: false }),
          page.getByRole("button", { name: "Administrar Negocios", exact: false }),
          page.getByText("Administrar Negocios", { exact: false })
        ],
        15000
      );

      if (!agregarVisible || !administrarVisible) {
        throw new Error("Mi Negocio submenu did not show required options.");
      }

      await screenshot(page, "02-mi-negocio-menu-expanded.png");
      setResult("Mi Negocio menu", "PASS", "Mi Negocio menu expanded with expected submenu options.");
    } catch (error) {
      setResult("Mi Negocio menu", "FAIL", String(error.message || error));
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const addBusinessMenuOption = await firstVisible(
        [
          page.getByRole("link", { name: "Agregar Negocio", exact: false }),
          page.getByRole("button", { name: "Agregar Negocio", exact: false }),
          page.getByText("Agregar Negocio", { exact: false })
        ],
        15000
      );

      if (!addBusinessMenuOption) {
        throw new Error('"Agregar Negocio" action was not found.');
      }

      await clickAndWait(page, addBusinessMenuOption);

      const modalTitle = await firstVisible(
        [
          page.getByRole("heading", { name: "Crear Nuevo Negocio", exact: false }),
          page.getByText("Crear Nuevo Negocio", { exact: false })
        ],
        15000
      );

      const businessNameInput = await firstVisible(
        [
          page.getByLabel("Nombre del Negocio", { exact: false }),
          page.getByPlaceholder("Nombre del Negocio", { exact: false }),
          page.locator("input").filter({ hasText: "Nombre del Negocio" })
        ],
        6000
      );

      const quotaTextVisible = await tryVisible(page.getByText("Tienes 2 de 3 negocios", { exact: false }), 8000);
      const cancelButton = await firstVisible(
        [
          page.getByRole("button", { name: "Cancelar", exact: false }),
          page.getByText("Cancelar", { exact: false })
        ],
        8000
      );
      const createButton = await firstVisible(
        [
          page.getByRole("button", { name: "Crear Negocio", exact: false }),
          page.getByText("Crear Negocio", { exact: false })
        ],
        8000
      );

      if (!modalTitle || !businessNameInput || !quotaTextVisible || !cancelButton || !createButton) {
        throw new Error("Agregar Negocio modal is missing one or more required elements.");
      }

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await screenshot(page, "03-agregar-negocio-modal.png");
      await clickAndWait(page, cancelButton);

      setResult(
        "Agregar Negocio modal",
        "PASS",
        "Crear Nuevo Negocio modal validated with required fields and actions."
      );
    } catch (error) {
      setResult("Agregar Negocio modal", "FAIL", String(error.message || error));
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      const administrar = await firstVisible(
        [
          page.getByRole("link", { name: "Administrar Negocios", exact: false }),
          page.getByRole("button", { name: "Administrar Negocios", exact: false }),
          page.getByText("Administrar Negocios", { exact: false })
        ],
        10000
      );

      if (!administrar) {
        const miNegocio = await firstVisible(
          [
            page.getByRole("button", { name: "Mi Negocio", exact: false }),
            page.getByRole("link", { name: "Mi Negocio", exact: false }),
            page.getByText("Mi Negocio", { exact: false })
          ],
          10000
        );
        if (!miNegocio) {
          throw new Error('Could not reopen "Mi Negocio" to access "Administrar Negocios".');
        }
        await clickAndWait(page, miNegocio);
      }

      const administrarClickable = await firstVisible(
        [
          page.getByRole("link", { name: "Administrar Negocios", exact: false }),
          page.getByRole("button", { name: "Administrar Negocios", exact: false }),
          page.getByText("Administrar Negocios", { exact: false })
        ],
        15000
      );

      if (!administrarClickable) {
        throw new Error('"Administrar Negocios" option not found.');
      }

      await clickAndWait(page, administrarClickable);

      const requiredSections = [
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Sección Legal"
      ];

      for (const section of requiredSections) {
        if (!(await tryVisible(page.getByText(section, { exact: false }), 15000))) {
          throw new Error(`Required section "${section}" is missing.`);
        }
      }

      await screenshot(page, "04-administrar-negocios-page.png");
      setResult("Administrar Negocios view", "PASS", "All expected account sections are visible.");
    } catch (error) {
      setResult("Administrar Negocios view", "FAIL", String(error.message || error));
      throw error;
    }

    // Step 5: Validate Información General
    try {
      const infoSection = await firstVisible(
        [
          page.locator("section,div").filter({ hasText: "Información General" }),
          page.getByText("Información General", { exact: false })
        ],
        12000
      );
      if (!infoSection) {
        throw new Error('Section "Información General" not found.');
      }

      const infoText = await infoSection.innerText();
      const emailVisible = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i.test(infoText);
      const hasBusinessPlan = /BUSINESS\s+PLAN/i.test(infoText);
      const changePlanVisible = await tryVisible(page.getByRole("button", { name: "Cambiar Plan", exact: false }), 10000);

      if (!emailVisible || !hasBusinessPlan || !changePlanVisible) {
        throw new Error(
          'Información General did not show user data, "BUSINESS PLAN", and/or "Cambiar Plan" as expected.'
        );
      }

      setResult("Información General", "PASS", "User details, plan text and action button validated.");
    } catch (error) {
      setResult("Información General", "FAIL", String(error.message || error));
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      const requiredDetails = ["Cuenta creada", "Estado activo", "Idioma seleccionado"];
      for (const detail of requiredDetails) {
        if (!(await tryVisible(page.getByText(detail, { exact: false }), 12000))) {
          throw new Error(`"${detail}" was not visible in Detalles de la Cuenta.`);
        }
      }
      setResult("Detalles de la Cuenta", "PASS", "All required account detail labels are visible.");
    } catch (error) {
      setResult("Detalles de la Cuenta", "FAIL", String(error.message || error));
    }

    // Step 7: Validate Tus Negocios
    try {
      const sectionTitleVisible = await tryVisible(page.getByText("Tus Negocios", { exact: false }), 10000);
      const addBusinessButtonVisible = await tryVisible(
        page.getByRole("button", { name: "Agregar Negocio", exact: false }),
        10000
      );
      const quotaVisible = await tryVisible(page.getByText("Tienes 2 de 3 negocios", { exact: false }), 10000);
      const listItemsCount =
        (await page.locator("li").count()) +
        (await page.locator("[role='row']").count()) +
        (await page.locator("[class*='business']").count());

      if (!sectionTitleVisible || !addBusinessButtonVisible || !quotaVisible || listItemsCount < 1) {
        throw new Error("Tus Negocios section did not show expected list/button/quota content.");
      }

      setResult("Tus Negocios", "PASS", "Business list area and expected controls are visible.");
    } catch (error) {
      setResult("Tus Negocios", "FAIL", String(error.message || error));
    }

    // Step 8: Validate Términos y Condiciones
    try {
      await openLegalLink({
        appPage: page,
        context,
        linkText: "Términos y Condiciones",
        expectedHeading: "Términos y Condiciones",
        screenshotFileName: "05-terminos-y-condiciones.png",
        resultField: "Términos y Condiciones",
        urlField: "terminosYCondiciones"
      });
    } catch (error) {
      setResult("Términos y Condiciones", "FAIL", String(error.message || error));
    }

    // Step 9: Validate Política de Privacidad
    try {
      await openLegalLink({
        appPage: page,
        context,
        linkText: "Política de Privacidad",
        expectedHeading: "Política de Privacidad",
        screenshotFileName: "06-politica-de-privacidad.png",
        resultField: "Política de Privacidad",
        urlField: "politicaDePrivacidad"
      });
    } catch (error) {
      setResult("Política de Privacidad", "FAIL", String(error.message || error));
    }
  } finally {
    report.finishedAt = new Date().toISOString();
    await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    if (browser) {
      await browser.close().catch(() => {});
    }
  }

  const formatted = REQUIRED_REPORT_FIELDS.map((field) => ({
    step: field,
    status: report.results[field].status,
    details: report.results[field].details
  }));
  console.table(formatted);
  console.log(`Final report: ${reportPath}`);
  if (Object.values(report.results).some((entry) => entry.status !== "PASS")) {
    process.exitCode = 1;
  }
}

main().catch(async (error) => {
  report.finishedAt = new Date().toISOString();
  report.fatalError = String(error?.message || error);
  await fs.mkdir(artifactsDir, { recursive: true });
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.error(error);
  console.log(`Final report: ${reportPath}`);
  process.exitCode = 1;
});
