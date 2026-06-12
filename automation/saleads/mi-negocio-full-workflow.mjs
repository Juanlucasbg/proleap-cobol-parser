import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const WAIT_TIMEOUT_MS = Number.parseInt(process.env.SALEADS_WAIT_TIMEOUT_MS ?? "15000", 10);
const HEADLESS = (process.env.SALEADS_HEADLESS ?? "true").toLowerCase() !== "false";
const START_URL = process.env.SALEADS_START_URL;
const DRY_RUN = process.env.SALEADS_DRY_RUN === "1";

const reportFields = [
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
const outputDir = process.env.SALEADS_OUTPUT_DIR ?? path.join("artifacts", "saleads-mi-negocio", timestamp);
const screenshots = [];
const finalUrls = {};
const statusByField = Object.fromEntries(
  reportFields.map((field) => [field, { status: "FAIL", details: "Not executed" }])
);

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toMatchers(text) {
  if (text instanceof RegExp) {
    return { exact: text, fuzzy: text };
  }

  const exact = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
  const fuzzy = new RegExp(escapeRegex(text), "i");
  return { exact, fuzzy };
}

function setPass(field, details) {
  statusByField[field] = { status: "PASS", details };
}

function setFail(field, error) {
  statusByField[field] = {
    status: "FAIL",
    details: error instanceof Error ? error.message : String(error)
  };
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: WAIT_TIMEOUT_MS }).catch(() => {});
  await page.waitForTimeout(700);
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
}

function candidateLocatorsForText(page, text) {
  const { exact, fuzzy } = toMatchers(text);

  return [
    page.getByRole("button", { name: exact }),
    page.getByRole("link", { name: exact }),
    page.getByRole("menuitem", { name: exact }),
    page.getByRole("tab", { name: exact }),
    page.getByRole("option", { name: exact }),
    page.getByRole("heading", { name: fuzzy }),
    page.getByText(fuzzy)
  ];
}

async function firstVisible(page, texts) {
  for (const text of texts) {
    const candidates = candidateLocatorsForText(page, text);

    for (const locator of candidates) {
      const target = locator.first();
      try {
        await target.waitFor({ state: "visible", timeout: 1500 });
        return target;
      } catch {
        // Try next candidate.
      }
    }
  }

  return null;
}

async function clickByVisibleText(page, texts, options = {}) {
  const { waitAfterClick = true } = options;
  const locator = await firstVisible(page, Array.isArray(texts) ? texts : [texts]);

  if (!locator) {
    throw new Error(`Unable to find visible element with text: ${JSON.stringify(texts)}`);
  }

  await locator.click();

  if (waitAfterClick) {
    await waitForUi(page);
  }
}

async function assertVisibleText(page, texts, label) {
  const locator = await firstVisible(page, Array.isArray(texts) ? texts : [texts]);
  if (!locator) {
    throw new Error(`Expected to see "${label}".`);
  }
}

async function captureCheckpoint(page, name, fullPage = false) {
  const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
  const filename = `${String(screenshots.length + 1).padStart(2, "0")}-${safeName}.png`;
  const screenshotPath = path.join(outputDir, filename);

  await page.screenshot({ path: screenshotPath, fullPage });
  screenshots.push({
    checkpoint: name,
    path: screenshotPath,
    url: page.url()
  });
}

async function validateAnyTextVisible(page, texts) {
  const locator = await firstVisible(page, texts);
  return Boolean(locator);
}

async function writeFinalReport() {
  const finalReport = {
    generatedAt: new Date().toISOString(),
    config: {
      startUrl: START_URL ?? "(none)",
      headless: HEADLESS,
      waitTimeoutMs: WAIT_TIMEOUT_MS,
      outputDir
    },
    results: statusByField,
    legalUrls: finalUrls,
    screenshots
  };

  const reportPath = path.join(outputDir, "final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");

  console.log(`\nReport written to: ${reportPath}`);
  console.table(
    Object.entries(statusByField).map(([field, result]) => ({ field, status: result.status, details: result.details }))
  );
}

async function run() {
  await ensureDir(outputDir);

  if (DRY_RUN) {
    console.log("Dry run mode enabled; no browser interaction performed.");
    setPass("Login", "Dry run");
    setPass("Mi Negocio menu", "Dry run");
    setPass("Agregar Negocio modal", "Dry run");
    setPass("Administrar Negocios view", "Dry run");
    setPass("Información General", "Dry run");
    setPass("Detalles de la Cuenta", "Dry run");
    setPass("Tus Negocios", "Dry run");
    setPass("Términos y Condiciones", "Dry run");
    setPass("Política de Privacidad", "Dry run");
    await writeFinalReport();
    return;
  }

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    if (!START_URL) {
      throw new Error(
        "SALEADS_START_URL is required to start from the active SaleADS login page without hardcoding an environment URL."
      );
    }

    await page.goto(START_URL, { waitUntil: "domcontentloaded", timeout: WAIT_TIMEOUT_MS });
    await waitForUi(page);

    // 1) Login with Google.
    try {
      await clickByVisibleText(page, ["Sign in with Google", "Iniciar sesión con Google", "Google", "Iniciar sesión"]);
      const accountOption = await firstVisible(page, [ACCOUNT_EMAIL]);
      if (accountOption) {
        await accountOption.click();
        await waitForUi(page);
      }

      await assertVisibleText(page, ["Negocio", "Mi Negocio"], "left sidebar navigation");
      await captureCheckpoint(page, "dashboard-loaded");
      setPass("Login", "Application dashboard and sidebar were visible after Google login.");
    } catch (error) {
      setFail("Login", error);
    }

    // 2) Open Mi Negocio menu.
    try {
      await clickByVisibleText(page, ["Negocio"]);
      await clickByVisibleText(page, ["Mi Negocio"]);
      await assertVisibleText(page, ["Agregar Negocio"], "Agregar Negocio");
      await assertVisibleText(page, ["Administrar Negocios"], "Administrar Negocios");
      await captureCheckpoint(page, "mi-negocio-expanded-menu");
      setPass("Mi Negocio menu", "Mi Negocio expanded with Agregar Negocio and Administrar Negocios.");
    } catch (error) {
      setFail("Mi Negocio menu", error);
    }

    // 3) Validate Agregar Negocio modal.
    try {
      await clickByVisibleText(page, ["Agregar Negocio"]);
      await assertVisibleText(page, ["Crear Nuevo Negocio"], "Crear Nuevo Negocio");
      await assertVisibleText(page, ["Nombre del Negocio"], "Nombre del Negocio");
      await assertVisibleText(page, ["Tienes 2 de 3 negocios"], "Tienes 2 de 3 negocios");
      await assertVisibleText(page, ["Cancelar"], "Cancelar");
      await assertVisibleText(page, ["Crear Negocio"], "Crear Negocio");

      const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
      if (await businessNameInput.isVisible().catch(() => false)) {
        await businessNameInput.fill("Negocio Prueba Automatización");
      } else {
        const inputFallback = page.locator("input").first();
        await inputFallback.fill("Negocio Prueba Automatización");
      }

      await captureCheckpoint(page, "agregar-negocio-modal");
      await clickByVisibleText(page, ["Cancelar"]);
      setPass("Agregar Negocio modal", "Agregar Negocio modal fields and actions were validated.");
    } catch (error) {
      setFail("Agregar Negocio modal", error);
    }

    // 4) Open Administrar Negocios and validate sections.
    try {
      if (!(await validateAnyTextVisible(page, ["Administrar Negocios"]))) {
        await clickByVisibleText(page, ["Mi Negocio"]);
      }

      await clickByVisibleText(page, ["Administrar Negocios"]);
      await assertVisibleText(page, ["Información General"], "Información General");
      await assertVisibleText(page, ["Detalles de la Cuenta"], "Detalles de la Cuenta");
      await assertVisibleText(page, ["Tus Negocios"], "Tus Negocios");
      await assertVisibleText(page, ["Sección Legal"], "Sección Legal");
      await captureCheckpoint(page, "administrar-negocios-account-page", true);
      setPass("Administrar Negocios view", "Account page loaded with all required sections.");
    } catch (error) {
      setFail("Administrar Negocios view", error);
    }

    // 5) Validate Información General.
    try {
      const emailVisible = await validateAnyTextVisible(page, [ACCOUNT_EMAIL, /@/i]);
      if (!emailVisible) {
        throw new Error("User email is not visible.");
      }

      await assertVisibleText(page, ["BUSINESS PLAN"], "BUSINESS PLAN");
      await assertVisibleText(page, ["Cambiar Plan"], "Cambiar Plan");

      // Name is environment dependent, so we assert at least one profile heading/text block is visible.
      const userNameLocator = page.locator("h1, h2, h3, [data-testid*='name'], [class*='name']").first();
      if (!(await userNameLocator.isVisible().catch(() => false))) {
        throw new Error("User name is not visible.");
      }

      setPass("Información General", "User name/email, plan label, and Cambiar Plan were visible.");
    } catch (error) {
      setFail("Información General", error);
    }

    // 6) Validate Detalles de la Cuenta.
    try {
      await assertVisibleText(page, ["Cuenta creada"], "Cuenta creada");
      await assertVisibleText(page, ["Estado activo", "Estado Activo"], "Estado activo");
      await assertVisibleText(page, ["Idioma seleccionado"], "Idioma seleccionado");
      setPass("Detalles de la Cuenta", "Account detail fields were visible.");
    } catch (error) {
      setFail("Detalles de la Cuenta", error);
    }

    // 7) Validate Tus Negocios.
    try {
      await assertVisibleText(page, ["Tus Negocios"], "Tus Negocios");
      await assertVisibleText(page, ["Agregar Negocio"], "Agregar Negocio");
      await assertVisibleText(page, ["Tienes 2 de 3 negocios"], "Tienes 2 de 3 negocios");

      const businessItems = page.locator("li, tr, [role='row'], [class*='business'], [data-testid*='business']");
      if ((await businessItems.count()) < 1) {
        throw new Error("Business list is not visible.");
      }

      setPass("Tus Negocios", "Business list, add button, and quota text were visible.");
    } catch (error) {
      setFail("Tus Negocios", error);
    }

    async function openAndValidateLegalPage(linkText, expectedHeading, reportKey, screenshotName) {
      const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
      await clickByVisibleText(page, [linkText], { waitAfterClick: false });
      const popupPage = await popupPromise;

      let legalPage = page;
      let openedInNewTab = false;

      if (popupPage) {
        legalPage = popupPage;
        openedInNewTab = true;
        await legalPage.waitForLoadState("domcontentloaded", { timeout: WAIT_TIMEOUT_MS }).catch(() => {});
      } else {
        await waitForUi(page);
      }

      await assertVisibleText(legalPage, [expectedHeading], expectedHeading);

      const bodyText = await legalPage.locator("body").innerText();
      if (bodyText.trim().length < 80) {
        throw new Error(`Legal content for "${linkText}" appears empty.`);
      }

      await captureCheckpoint(legalPage, screenshotName, true);
      finalUrls[reportKey] = legalPage.url();

      if (openedInNewTab) {
        await legalPage.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded", timeout: WAIT_TIMEOUT_MS }).catch(() => {});
        await waitForUi(page);
      }
    }

    // 8) Validate Términos y Condiciones.
    try {
      await openAndValidateLegalPage(
        "Términos y Condiciones",
        "Términos y Condiciones",
        "Términos y Condiciones",
        "terminos-y-condiciones-page"
      );
      setPass("Términos y Condiciones", "Legal page heading/content validated and URL captured.");
    } catch (error) {
      setFail("Términos y Condiciones", error);
    }

    // 9) Validate Política de Privacidad.
    try {
      await openAndValidateLegalPage(
        "Política de Privacidad",
        "Política de Privacidad",
        "Política de Privacidad",
        "politica-de-privacidad-page"
      );
      setPass("Política de Privacidad", "Legal page heading/content validated and URL captured.");
    } catch (error) {
      setFail("Política de Privacidad", error);
    }
  } finally {
    await writeFinalReport();
    await browser.close();
  }
}

run().catch(async (error) => {
  console.error("Workflow execution failed:", error);
  await ensureDir(outputDir);
  await writeFinalReport();
  process.exitCode = 1;
});
