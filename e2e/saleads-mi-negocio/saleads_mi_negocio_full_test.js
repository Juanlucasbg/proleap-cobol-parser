const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright");

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

function envFlag(name, defaultValue) {
  const raw = process.env[name];
  if (raw == null) {
    return defaultValue;
  }
  return raw.toLowerCase() === "true";
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function nowId() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    const count = await locator.count();
    for (let i = 0; i < count; i += 1) {
      const item = locator.nth(i);
      if (await item.isVisible().catch(() => false)) {
        return item;
      }
    }
  }
  return null;
}

async function clickByVisibleText(page, text, { timeout = 15000 } = {}) {
  const exactRegex = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
  const looseRegex = new RegExp(escapeRegex(text), "i");

  const locator = await firstVisibleLocator([
    page.getByRole("button", { name: exactRegex }),
    page.getByRole("link", { name: exactRegex }),
    page.getByRole("menuitem", { name: exactRegex }),
    page.getByRole("tab", { name: exactRegex }),
    page.getByRole("button", { name: looseRegex }),
    page.getByRole("link", { name: looseRegex }),
    page.getByText(exactRegex),
    page.getByText(looseRegex),
  ]);

  if (!locator) {
    throw new Error(`No se encontró un elemento visible con texto: "${text}"`);
  }

  await locator.click({ timeout });
  await waitForUi(page);
}

async function assertTextVisible(page, text, { timeout = 15000 } = {}) {
  const regex = new RegExp(escapeRegex(text), "i");
  const locator = await firstVisibleLocator([
    page.getByRole("heading", { name: regex }),
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByText(regex),
    page.locator(`text=${text}`),
  ]);

  if (!locator) {
    throw new Error(`Texto no visible: "${text}"`);
  }

  await locator.waitFor({ state: "visible", timeout });
}

async function assertSidebarVisible(page) {
  const sidebar = await firstVisibleLocator([
    page.locator("aside"),
    page.locator("nav[aria-label*='sidebar' i]"),
    page.locator("nav"),
  ]);

  if (!sidebar) {
    throw new Error("No se encontró la barra lateral de navegación.");
  }
}

async function assertEmailVisible(page) {
  const emailRegex = /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i;
  const locator = await firstVisibleLocator([
    page.getByText(emailRegex),
    page.locator(":text-matches('[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}', 'i')"),
  ]);

  if (!locator) {
    throw new Error("No se encontró un email visible del usuario.");
  }
}

async function assertLikelyUserNameVisible(page, accountEmail) {
  const accountPrefix = accountEmail.split("@")[0];
  const userNameLocator = await firstVisibleLocator([
    page.getByText(/nombre/i),
    page.getByText(/usuario/i),
    page.getByText(/perfil/i),
    page.getByText(new RegExp(escapeRegex(accountPrefix), "i")),
  ]);

  if (!userNameLocator) {
    throw new Error("No se encontró un nombre de usuario visible.");
  }
}

async function capture(page, screenshotsDir, fileName, fullPage = false) {
  const filePath = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function closeModalIfVisible(page) {
  const modalCandidates = [
    page.getByRole("dialog"),
    page.locator("[role='dialog']"),
    page.locator(".modal:visible"),
  ];

  const modal = await firstVisibleLocator(modalCandidates);
  if (!modal) {
    return;
  }

  const cancelButton = await firstVisibleLocator([
    modal.getByRole("button", { name: /cancelar/i }),
    modal.getByText(/cancelar/i),
  ]);

  if (cancelButton) {
    await cancelButton.click().catch(() => {});
    await waitForUi(page);
  }
}

function createStatusMap() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };
}

function pass(statusMap, key) {
  statusMap[key] = "PASS";
}

async function maybeNavigateToLogin(page) {
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (!loginUrl) {
    throw new Error(
      "Define SALEADS_LOGIN_URL con la URL de login del entorno actual (sin hardcodear dominio en el test)."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
  await waitForUi(page);
}

async function loginWithGoogleIfNeeded(page, context, accountEmail) {
  const appReadyLocator = await firstVisibleLocator([
    page.getByRole("navigation"),
    page.locator("aside"),
    page.getByText(/mi negocio|negocio/i),
  ]);

  if (appReadyLocator) {
    return;
  }

  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickByVisibleText(page, "Sign in with Google").catch(async () => {
    await clickByVisibleText(page, "Iniciar sesión con Google").catch(async () => {
      await clickByVisibleText(page, "Google");
    });
  });

  const popupPage = await popupPromise;

  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
    await firstVisibleLocator([
      popupPage.getByText(new RegExp(escapeRegex(accountEmail), "i")),
      popupPage.getByRole("button", { name: new RegExp(escapeRegex(accountEmail), "i") }),
      popupPage.getByRole("link", { name: new RegExp(escapeRegex(accountEmail), "i") }),
    ])
      .then(async (locator) => {
        if (locator) {
          await locator.click({ timeout: 10000 }).catch(() => {});
        }
      })
      .catch(() => {});

    await popupPage.waitForEvent("close", { timeout: 30000 }).catch(() => {});
  }

  await waitForUi(page);
}

async function validateLegalLink({
  page,
  context,
  linkText,
  headingText,
  screenshotName,
  screenshotsDir,
}) {
  const newPagePromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickByVisibleText(page, linkText);
  const newPage = await newPagePromise;
  const legalPage = newPage || page;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await assertTextVisible(legalPage, headingText, { timeout: 20000 });

  const legalContentVisible = await firstVisibleLocator([
    legalPage.locator("article"),
    legalPage.locator("main"),
    legalPage.locator("section"),
    legalPage.locator("p"),
  ]);

  if (!legalContentVisible) {
    throw new Error(`No se encontró contenido legal visible para "${linkText}"`);
  }

  const screenshotPath = await capture(legalPage, screenshotsDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (newPage) {
    await newPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 20000 }).catch(() => {});
    await waitForUi(page);
  }

  return { finalUrl, screenshotPath };
}

async function main() {
  const browser = await chromium.launch({
    headless: envFlag("SALEADS_HEADLESS", true),
    slowMo: Number.parseInt(process.env.SALEADS_SLOWMO_MS || "200", 10),
  });

  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 },
  });
  const page = await context.newPage();

  const runDir = path.resolve(
    process.cwd(),
    process.env.SALEADS_EVIDENCE_DIR || "evidence/saleads-mi-negocio",
    nowId()
  );
  const screenshotsDir = path.join(runDir, "screenshots");
  fs.mkdirSync(screenshotsDir, { recursive: true });

  const accountEmail = process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;
  const statusMap = createStatusMap();
  const details = [];

  try {
    await maybeNavigateToLogin(page);

    // Step 1: Login with Google
    try {
      await loginWithGoogleIfNeeded(page, context, accountEmail);
      await assertSidebarVisible(page);
      await assertTextVisible(page, "Negocio", { timeout: 30000 }).catch(async () => {
        await assertTextVisible(page, "Mi Negocio", { timeout: 30000 });
      });
      const dashboardShot = await capture(page, screenshotsDir, "01-dashboard-loaded.png", true);
      details.push({ step: "Login", screenshot: dashboardShot });
      pass(statusMap, "Login");
    } catch (error) {
      details.push({ step: "Login", error: error.message });
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByVisibleText(page, "Negocio").catch(async () => {
        await clickByVisibleText(page, "Mi Negocio");
      });
      await clickByVisibleText(page, "Mi Negocio").catch(() => Promise.resolve());
      await assertTextVisible(page, "Agregar Negocio");
      await assertTextVisible(page, "Administrar Negocios");
      const menuShot = await capture(page, screenshotsDir, "02-mi-negocio-menu-expanded.png", true);
      details.push({ step: "Mi Negocio menu", screenshot: menuShot });
      pass(statusMap, "Mi Negocio menu");
    } catch (error) {
      details.push({ step: "Mi Negocio menu", error: error.message });
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, "Agregar Negocio");
      await assertTextVisible(page, "Crear Nuevo Negocio");
      await assertTextVisible(page, "Nombre del Negocio");
      await assertTextVisible(page, "Tienes 2 de 3 negocios");
      await assertTextVisible(page, "Cancelar");
      await assertTextVisible(page, "Crear Negocio");

      const input = await firstVisibleLocator([
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator("input"),
      ]);
      if (input) {
        await input.click().catch(() => {});
        await input.fill("Negocio Prueba Automatización").catch(() => {});
      }

      const modalShot = await capture(page, screenshotsDir, "03-agregar-negocio-modal.png", true);
      details.push({ step: "Agregar Negocio modal", screenshot: modalShot });
      pass(statusMap, "Agregar Negocio modal");

      await clickByVisibleText(page, "Cancelar").catch(async () => {
        await closeModalIfVisible(page);
      });
    } catch (error) {
      details.push({ step: "Agregar Negocio modal", error: error.message });
      await closeModalIfVisible(page);
    }

    // Step 4: Open Administrar Negocios
    try {
      await clickByVisibleText(page, "Mi Negocio").catch(() => Promise.resolve());
      await clickByVisibleText(page, "Administrar Negocios");

      await assertTextVisible(page, "Información General", { timeout: 30000 });
      await assertTextVisible(page, "Detalles de la Cuenta", { timeout: 30000 });
      await assertTextVisible(page, "Tus Negocios", { timeout: 30000 });
      await assertTextVisible(page, "Sección Legal", { timeout: 30000 });

      const accountShot = await capture(
        page,
        screenshotsDir,
        "04-administrar-negocios-view-full.png",
        true
      );
      details.push({ step: "Administrar Negocios view", screenshot: accountShot });
      pass(statusMap, "Administrar Negocios view");
    } catch (error) {
      details.push({ step: "Administrar Negocios view", error: error.message });
    }

    // Step 5: Validate Información General
    try {
      await assertTextVisible(page, "BUSINESS PLAN");
      await assertTextVisible(page, "Cambiar Plan");
      await assertEmailVisible(page);
      await assertLikelyUserNameVisible(page, accountEmail);
      pass(statusMap, "Información General");
    } catch (error) {
      details.push({ step: "Información General", error: error.message });
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await assertTextVisible(page, "Cuenta creada");
      await assertTextVisible(page, "Estado activo");
      await assertTextVisible(page, "Idioma seleccionado");
      pass(statusMap, "Detalles de la Cuenta");
    } catch (error) {
      details.push({ step: "Detalles de la Cuenta", error: error.message });
    }

    // Step 7: Validate Tus Negocios
    try {
      await assertTextVisible(page, "Tus Negocios");
      await assertTextVisible(page, "Agregar Negocio");
      await assertTextVisible(page, "Tienes 2 de 3 negocios");
      pass(statusMap, "Tus Negocios");
    } catch (error) {
      details.push({ step: "Tus Negocios", error: error.message });
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const termsEvidence = await validateLegalLink({
        page,
        context,
        linkText: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        screenshotName: "08-terminos-y-condiciones.png",
        screenshotsDir,
      });
      details.push({ step: "Términos y Condiciones", ...termsEvidence });
      pass(statusMap, "Términos y Condiciones");
    } catch (error) {
      details.push({ step: "Términos y Condiciones", error: error.message });
    }

    // Step 9: Validate Política de Privacidad
    try {
      const privacyEvidence = await validateLegalLink({
        page,
        context,
        linkText: "Política de Privacidad",
        headingText: "Política de Privacidad",
        screenshotName: "09-politica-de-privacidad.png",
        screenshotsDir,
      });
      details.push({ step: "Política de Privacidad", ...privacyEvidence });
      pass(statusMap, "Política de Privacidad");
    } catch (error) {
      details.push({ step: "Política de Privacidad", error: error.message });
    }
  } finally {
    const reportPath = path.join(runDir, "final-report.json");
    const report = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      environment: {
        loginUrl: process.env.SALEADS_LOGIN_URL || null,
        headless: envFlag("SALEADS_HEADLESS", true),
        accountEmail,
      },
      results: statusMap,
      details,
      artifacts: {
        runDir,
        screenshotsDir,
      },
    };

    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");
    console.table(statusMap);
    console.log(`Reporte final: ${reportPath}`);

    await context.close();
    await browser.close();
  }
}

main().catch((error) => {
  console.error("Error no controlado en la ejecución del test:", error);
  process.exitCode = 1;
});
