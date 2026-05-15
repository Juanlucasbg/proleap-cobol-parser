const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const SALEADS_URL =
  process.env.SALEADS_URL || process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || "";
const HEADLESS = process.env.HEADLESS !== "false";
const ACTION_TIMEOUT_MS = Number(process.env.ACTION_TIMEOUT_MS || 30000);
const OUTPUT_ROOT = process.env.OUTPUT_DIR || path.join(process.cwd(), "artifacts");

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

function createEmptyReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", notes: [] }]),
  );
}

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function slugify(value) {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

function escRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function textRegex(text) {
  return new RegExp(`^\\s*${escRegExp(text)}\\s*$`, "i");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function isVisible(locator, timeout = 1200) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function anyVisible(locatorBuilders, timeout) {
  for (const build of locatorBuilders) {
    if (await isVisible(build(), timeout)) {
      return true;
    }
  }
  return false;
}

async function clickByVisibleText(page, texts, options = {}) {
  const {
    waitAfter = true,
    timeout = 1800,
    throwOnNotFound = true,
    exact = true,
  } = options;

  const entries = [];
  for (const text of texts) {
    const matcher = exact ? textRegex(text) : new RegExp(escRegExp(text), "i");
    entries.push(
      { label: `button:${text}`, locator: () => page.getByRole("button", { name: matcher }).first() },
      { label: `link:${text}`, locator: () => page.getByRole("link", { name: matcher }).first() },
      { label: `menuitem:${text}`, locator: () => page.getByRole("menuitem", { name: matcher }).first() },
      { label: `tab:${text}`, locator: () => page.getByRole("tab", { name: matcher }).first() },
      { label: `text:${text}`, locator: () => page.getByText(matcher).first() },
    );
  }

  for (const entry of entries) {
    const locator = entry.locator();
    if (await isVisible(locator, timeout)) {
      await locator.click();
      if (waitAfter) {
        await waitForUi(page);
      }
      return entry.label;
    }
  }

  if (throwOnNotFound) {
    throw new Error(`Unable to click any of the requested texts: ${texts.join(", ")}`);
  }

  return null;
}

async function assertVisibleByText(page, texts, label) {
  const found = await anyVisible(
    texts.flatMap((text) => {
      const exactMatcher = textRegex(text);
      const partialMatcher = new RegExp(escRegExp(text), "i");
      return [
        () => page.getByRole("heading", { name: exactMatcher }).first(),
        () => page.getByRole("heading", { name: partialMatcher }).first(),
        () => page.getByText(exactMatcher).first(),
        () => page.getByText(partialMatcher).first(),
      ];
    }),
    2500,
  );

  if (!found) {
    throw new Error(`Validation failed: "${label}" not visible.`);
  }
}

async function takeScreenshot(page, outputDir, name, options = {}) {
  const filePath = path.join(outputDir, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage: Boolean(options.fullPage) });
  return filePath;
}

async function getBodyText(page) {
  try {
    return await page.locator("body").innerText();
  } catch {
    return "";
  }
}

function record(report, field, status, note) {
  report[field].status = status;
  if (note) {
    report[field].notes.push(note);
  }
}

async function validateLegalDocument({
  appPage,
  context,
  outputDir,
  linkTexts,
  headingTexts,
  field,
  screenshotName,
  report,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  await clickByVisibleText(appPage, linkTexts, { waitAfter: false, timeout: 2500 });
  const popup = await popupPromise;

  const legalPage = popup || appPage;
  await waitForUi(legalPage);

  await assertVisibleByText(legalPage, headingTexts, headingTexts[0]);
  const legalBodyText = await getBodyText(legalPage);
  if (legalBodyText.trim().length < 100) {
    throw new Error(`Legal content looks empty for "${field}".`);
  }

  const finalUrl = legalPage.url();
  const screenshot = await takeScreenshot(legalPage, outputDir, screenshotName, { fullPage: true });

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  record(report, field, "PASS", `URL: ${finalUrl}`);
  record(report, field, report[field].status, `Screenshot: ${screenshot}`);
}

async function main() {
  const report = createEmptyReport();
  const runId = nowStamp();
  const outputDir = path.join(OUTPUT_ROOT, `saleads_mi_negocio_${runId}`);
  await fs.mkdir(outputDir, { recursive: true });

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1200 },
    locale: "es-ES",
  });

  context.setDefaultTimeout(ACTION_TIMEOUT_MS);

  const page = await context.newPage();
  let hasFailures = false;

  try {
    // Step 1 - Login with Google
    try {
      if (SALEADS_URL) {
        await page.goto(SALEADS_URL, { waitUntil: "domcontentloaded" });
      } else if (page.url() === "about:blank") {
        throw new Error(
          "SALEADS_URL (or SALEADS_LOGIN_URL/BASE_URL) is required when the browser does not start on a login page.",
        );
      }

      await waitForUi(page);
      const loginPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickByVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Acceder con Google",
      ]);

      const loginPopup = await loginPopupPromise;
      const loginPage = loginPopup || page;
      await waitForUi(loginPage);

      await clickByVisibleText(
        loginPage,
        [GOOGLE_ACCOUNT_EMAIL],
        { waitAfter: false, timeout: 5000, throwOnNotFound: false, exact: false },
      );

      if (loginPopup) {
        await loginPopup.waitForClose({ timeout: 20000 }).catch(() => {});
        await page.bringToFront();
      }

      await waitForUi(page);
      await assertVisibleByText(page, ["Negocio"], "Main app interface");

      const sidebarVisible = await anyVisible(
        [
          () => page.locator("aside").first(),
          () => page.locator("nav").first(),
          () => page.getByText(/Mi Negocio/i).first(),
        ],
        3000,
      );
      if (!sidebarVisible) {
        throw new Error("Left sidebar navigation is not visible.");
      }

      const dashboardShot = await takeScreenshot(page, outputDir, "01_dashboard_loaded", { fullPage: true });
      record(report, "Login", "PASS", `Screenshot: ${dashboardShot}`);
    } catch (error) {
      hasFailures = true;
      record(report, "Login", "FAIL", String(error.message || error));
    }

    // Step 2 - Open Mi Negocio menu
    try {
      await clickByVisibleText(page, ["Negocio"], { throwOnNotFound: false });
      await clickByVisibleText(page, ["Mi Negocio"], { throwOnNotFound: false });

      await assertVisibleByText(page, ["Agregar Negocio"], "Agregar Negocio option");
      await assertVisibleByText(page, ["Administrar Negocios"], "Administrar Negocios option");

      const menuShot = await takeScreenshot(page, outputDir, "02_mi_negocio_menu_expanded");
      record(report, "Mi Negocio menu", "PASS", `Screenshot: ${menuShot}`);
    } catch (error) {
      hasFailures = true;
      record(report, "Mi Negocio menu", "FAIL", String(error.message || error));
    }

    // Step 3 - Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, ["Agregar Negocio"]);

      await assertVisibleByText(page, ["Crear Nuevo Negocio"], "Crear Nuevo Negocio modal title");

      const businessNameInputVisible = await anyVisible(
        [
          () => page.getByLabel(/Nombre del Negocio/i).first(),
          () => page.getByPlaceholder(/Nombre del Negocio/i).first(),
        ],
        2500,
      );
      if (!businessNameInputVisible) {
        throw new Error('Input field "Nombre del Negocio" is not visible.');
      }

      await assertVisibleByText(page, ["Tienes 2 de 3 negocios"], "Business quota text");
      await assertVisibleByText(page, ["Cancelar"], "Cancelar button");
      await assertVisibleByText(page, ["Crear Negocio"], "Crear Negocio button");

      const input = page.getByLabel(/Nombre del Negocio/i).first();
      if (await isVisible(input, 1500)) {
        await input.fill("Negocio Prueba Automatización");
      } else {
        await page.getByPlaceholder(/Nombre del Negocio/i).first().fill("Negocio Prueba Automatización");
      }

      const modalShot = await takeScreenshot(page, outputDir, "03_agregar_negocio_modal");
      await clickByVisibleText(page, ["Cancelar"]);

      record(report, "Agregar Negocio modal", "PASS", `Screenshot: ${modalShot}`);
    } catch (error) {
      hasFailures = true;
      record(report, "Agregar Negocio modal", "FAIL", String(error.message || error));
    }

    // Step 4 - Open Administrar Negocios view
    try {
      const adminVisible = await anyVisible([() => page.getByText(/Administrar Negocios/i).first()], 1200);
      if (!adminVisible) {
        await clickByVisibleText(page, ["Mi Negocio"], { throwOnNotFound: false });
      }

      await clickByVisibleText(page, ["Administrar Negocios"]);
      await assertVisibleByText(page, ["Información General"], "Información General section");
      await assertVisibleByText(page, ["Detalles de la Cuenta"], "Detalles de la Cuenta section");
      await assertVisibleByText(page, ["Tus Negocios"], "Tus Negocios section");
      await assertVisibleByText(page, ["Sección Legal"], "Sección Legal section");

      const accountShot = await takeScreenshot(page, outputDir, "04_administrar_negocios", { fullPage: true });
      record(report, "Administrar Negocios view", "PASS", `Screenshot: ${accountShot}`);
    } catch (error) {
      hasFailures = true;
      record(report, "Administrar Negocios view", "FAIL", String(error.message || error));
    }

    // Step 5 - Validate Información General
    try {
      const bodyText = await getBodyText(page);
      const hasEmail = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i.test(bodyText);
      const hasNameClue = /Nombre|Usuario|User\s*Name|Perfil|Cuenta/i.test(bodyText);

      if (!hasEmail) {
        throw new Error("No user email detected in Información General.");
      }
      if (!hasNameClue) {
        throw new Error("No user name indicator detected in Información General.");
      }

      await assertVisibleByText(page, ["BUSINESS PLAN"], "BUSINESS PLAN text");
      await assertVisibleByText(page, ["Cambiar Plan"], "Cambiar Plan button");

      record(report, "Información General", "PASS", "User name/email and plan data visible.");
    } catch (error) {
      hasFailures = true;
      record(report, "Información General", "FAIL", String(error.message || error));
    }

    // Step 6 - Validate Detalles de la Cuenta
    try {
      await assertVisibleByText(page, ["Cuenta creada"], "Cuenta creada label");
      await assertVisibleByText(page, ["Estado activo"], "Estado activo label");
      await assertVisibleByText(page, ["Idioma seleccionado"], "Idioma seleccionado label");
      record(report, "Detalles de la Cuenta", "PASS", "Required account detail labels are visible.");
    } catch (error) {
      hasFailures = true;
      record(report, "Detalles de la Cuenta", "FAIL", String(error.message || error));
    }

    // Step 7 - Validate Tus Negocios
    try {
      await assertVisibleByText(page, ["Tus Negocios"], "Tus Negocios section");
      await assertVisibleByText(page, ["Agregar Negocio"], "Agregar Negocio button");
      await assertVisibleByText(page, ["Tienes 2 de 3 negocios"], "Business quota text");

      const businessListCount = await page.locator("li, tr, [role='row'], .business-card, [data-testid*='business']").count();
      if (businessListCount < 1) {
        throw new Error("Business list items were not detected.");
      }

      record(report, "Tus Negocios", "PASS", `Detected ${businessListCount} business-list candidates.`);
    } catch (error) {
      hasFailures = true;
      record(report, "Tus Negocios", "FAIL", String(error.message || error));
    }

    // Step 8 - Validate Términos y Condiciones
    try {
      await validateLegalDocument({
        appPage: page,
        context,
        outputDir,
        linkTexts: ["Términos y Condiciones", "Terminos y Condiciones"],
        headingTexts: ["Términos y Condiciones", "Terminos y Condiciones"],
        field: "Términos y Condiciones",
        screenshotName: "08_terminos_y_condiciones",
        report,
      });
    } catch (error) {
      hasFailures = true;
      record(report, "Términos y Condiciones", "FAIL", String(error.message || error));
    }

    // Step 9 - Validate Política de Privacidad
    try {
      await validateLegalDocument({
        appPage: page,
        context,
        outputDir,
        linkTexts: ["Política de Privacidad", "Politica de Privacidad"],
        headingTexts: ["Política de Privacidad", "Politica de Privacidad"],
        field: "Política de Privacidad",
        screenshotName: "09_politica_de_privacidad",
        report,
      });
    } catch (error) {
      hasFailures = true;
      record(report, "Política de Privacidad", "FAIL", String(error.message || error));
    }
  } finally {
    const reportPath = path.join(outputDir, `final_report_${slugify(runId)}.json`);
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

    const table = Object.entries(report).map(([field, value]) => ({
      field,
      status: value.status,
      notes: value.notes.join(" | "),
    }));

    console.log("\nSaleADS Mi Negocio Full Test - Final Report");
    console.table(table);
    console.log(`Artifacts directory: ${outputDir}`);
    console.log(`Report JSON: ${reportPath}`);

    await browser.close();
  }

  if (hasFailures) {
    throw new Error("One or more validation steps failed. Check the final report for details.");
  }
}

main().catch((error) => {
  console.error(`\nTest execution failed: ${error.message || error}`);
  process.exitCode = 1;
});
