const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const SCREENSHOT_DIR = path.resolve(__dirname, "..", "artifacts", "screenshots");
const REPORT_PATH = path.resolve(__dirname, "..", "artifacts", "saleads_mi_negocio_final_report.json");

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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page, waitMs = 500) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(waitMs);
}

async function ensureArtifactsDirs() {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
  await fs.mkdir(path.dirname(REPORT_PATH), { recursive: true });
}

async function checkpoint(page, filename, fullPage = false) {
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, filename),
    fullPage
  });
}

async function clickByVisibleText(page, text, options = {}) {
  const exactRegex = new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i");

  const candidates = [
    page.getByRole("button", { name: exactRegex }),
    page.getByRole("link", { name: exactRegex }),
    page.getByRole("menuitem", { name: exactRegex }),
    page.getByRole("tab", { name: exactRegex }),
    page.getByText(exactRegex)
  ];

  let chosen;
  for (const locator of candidates) {
    if ((await locator.count()) > 0 && (await locator.first().isVisible().catch(() => false))) {
      chosen = locator.first();
      break;
    }
  }

  if (!chosen) {
    throw new Error(`Could not find a visible element with text "${text}".`);
  }

  await chosen.click(options);
  await waitForUi(page);
}

async function clickFirstVisible(locatorList) {
  for (const locator of locatorList) {
    if ((await locator.count()) > 0 && (await locator.first().isVisible().catch(() => false))) {
      await locator.first().click();
      return true;
    }
  }
  return false;
}

async function validateLegalPage({
  page,
  context,
  linkText,
  expectedHeading,
  screenshotFile
}) {
  const appUrlBefore = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickByVisibleText(page, linkText);

  const popup = await popupPromise;
  const target = popup || page;
  await waitForUi(target, 800);

  const headingCandidates = [
    target.getByRole("heading", { name: new RegExp(expectedHeading, "i") }),
    target.getByText(new RegExp(expectedHeading, "i"))
  ];

  let headingVisible = false;
  for (const locator of headingCandidates) {
    if ((await locator.count()) > 0 && (await locator.first().isVisible().catch(() => false))) {
      headingVisible = true;
      break;
    }
  }

  if (!headingVisible) {
    throw new Error(`Heading "${expectedHeading}" was not visible on legal page.`);
  }

  const legalContent = target.locator("article, main, section, p");
  const contentVisible = (await legalContent.count()) > 0 &&
    (await legalContent.first().isVisible().catch(() => false));
  if (!contentVisible) {
    throw new Error(`No visible legal content detected for "${expectedHeading}".`);
  }

  await checkpoint(target, screenshotFile, true);
  const finalUrl = target.url();

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== appUrlBefore) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  await ensureArtifactsDirs();

  const report = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "NOT_RUN", details: "" }])
  );
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL or SALEADS_URL to the login page URL of the current SaleADS environment."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page, 1000);

  // Step 1: Login with Google
  try {
    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

    const loginClicked = await clickFirstVisible([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i })
    ]);

    if (!loginClicked) {
      throw new Error("Login button for Google was not found.");
    }

    const authPage = (await popupPromise) || page;
    await waitForUi(authPage, 1000);

    const accountCandidate = authPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
    if ((await accountCandidate.count()) > 0 && (await accountCandidate.first().isVisible().catch(() => false))) {
      await accountCandidate.first().click();
      await waitForUi(authPage, 1000);
    }

    // Wait for dashboard / app shell
    await expect(
      page.getByText(/negocio|mi negocio|dashboard|inicio/i).first()
    ).toBeVisible({ timeout: 30000 });
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30000 });

    await checkpoint(page, `01-dashboard-loaded-${timestamp}.png`, true);
    report["Login"] = { status: "PASS", details: "Main interface and left sidebar are visible." };
  } catch (error) {
    report["Login"] = { status: "FAIL", details: error.message };
  }

  // Step 2: Open Mi Negocio menu
  try {
    await clickByVisibleText(page, "Negocio");
    await clickByVisibleText(page, "Mi Negocio");

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();

    await checkpoint(page, `02-mi-negocio-expanded-${timestamp}.png`, true);
    report["Mi Negocio menu"] = {
      status: "PASS",
      details: "Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios."
    };
  } catch (error) {
    report["Mi Negocio menu"] = { status: "FAIL", details: error.message };
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await clickByVisibleText(page, "Agregar Negocio");
    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    const businessNameInput = page.getByLabel(/nombre del negocio/i);
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, "Cancelar");

    await checkpoint(page, `03-agregar-negocio-modal-${timestamp}.png`, true);
    report["Agregar Negocio modal"] = {
      status: "PASS",
      details: "Crear Nuevo Negocio modal validated."
    };
  } catch (error) {
    report["Agregar Negocio modal"] = { status: "FAIL", details: error.message };
  }

  // Step 4: Open Administrar Negocios
  try {
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, "Mi Negocio");
    }
    await clickByVisibleText(page, "Administrar Negocios");

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible({ timeout: 30000 });

    await checkpoint(page, `04-administrar-negocios-page-${timestamp}.png`, true);
    report["Administrar Negocios view"] = {
      status: "PASS",
      details: "All expected account sections are visible."
    };
  } catch (error) {
    report["Administrar Negocios view"] = { status: "FAIL", details: error.message };
  }

  // Step 5: Validate Información General
  try {
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const emailVisible = await page.getByText(/@/).first().isVisible().catch(() => false);
    if (!emailVisible) {
      throw new Error("User email is not visible.");
    }

    const nameVisible = await page.locator("h1, h2, h3, p, span, div").filter({ hasText: /[A-Za-z]{2,}\s+[A-Za-z]{2,}/ }).first().isVisible().catch(() => false);
    if (!nameVisible) {
      throw new Error("User name is not visible.");
    }

    report["Información General"] = {
      status: "PASS",
      details: "Name, email, BUSINESS PLAN and Cambiar Plan are visible."
    };
  } catch (error) {
    report["Información General"] = { status: "FAIL", details: error.message };
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo|activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado|idioma/i)).toBeVisible();

    report["Detalles de la Cuenta"] = {
      status: "PASS",
      details: "Cuenta creada, Estado activo and Idioma seleccionado are visible."
    };
  } catch (error) {
    report["Detalles de la Cuenta"] = { status: "FAIL", details: error.message };
  }

  // Step 7: Validate Tus Negocios
  try {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();

    report["Tus Negocios"] = {
      status: "PASS",
      details: "Business list and business quota text validated."
    };
  } catch (error) {
    report["Tus Negocios"] = { status: "FAIL", details: error.message };
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const termsUrl = await validateLegalPage({
      page,
      context,
      linkText: "Términos y Condiciones",
      expectedHeading: "Términos y Condiciones",
      screenshotFile: `05-terminos-${timestamp}.png`
    });

    report["Términos y Condiciones"] = {
      status: "PASS",
      details: `Legal page validated. Final URL: ${termsUrl}`
    };
  } catch (error) {
    report["Términos y Condiciones"] = { status: "FAIL", details: error.message };
  }

  // Step 9: Validate Política de Privacidad
  try {
    const privacyUrl = await validateLegalPage({
      page,
      context,
      linkText: "Política de Privacidad",
      expectedHeading: "Política de Privacidad",
      screenshotFile: `06-politica-privacidad-${timestamp}.png`
    });

    report["Política de Privacidad"] = {
      status: "PASS",
      details: `Legal page validated. Final URL: ${privacyUrl}`
    };
  } catch (error) {
    report["Política de Privacidad"] = { status: "FAIL", details: error.message };
  }

  await fs.writeFile(REPORT_PATH, JSON.stringify({ generatedAt: new Date().toISOString(), report }, null, 2));
  console.log("SaleADS Mi Negocio final report:");
  console.log(JSON.stringify(report, null, 2));

  const failures = Object.entries(report).filter(([, result]) => result.status === "FAIL");
  expect(
    failures,
    `One or more workflow validations failed: ${JSON.stringify(failures, null, 2)}`
  ).toEqual([]);
});
