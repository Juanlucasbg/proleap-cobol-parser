const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

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

const ENTRY_URL_ENV_KEYS = [
  "SALEADS_LOGIN_URL",
  "SALEADS_URL",
  "BASE_URL",
  "TARGET_URL",
];

const LEGAL_CONTENT_HINTS = [
  /condiciones/i,
  /privacidad/i,
  /legal/i,
  /datos personales/i,
  /uso del servicio/i,
];

const APP_SIDEBAR_HINTS = [/Negocio/i, /Mi Negocio/i, /Business/i];

const escapeRegex = (value) =>
  value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const toMatcher = (value) =>
  value instanceof RegExp ? value : new RegExp(escapeRegex(value), "i");

const candidateLabel = (value) => (value instanceof RegExp ? value.toString() : value);

function createReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: "Not executed." };
    return acc;
  }, {});
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function firstVisibleLocator(page, locators) {
  for (const locator of locators) {
    const visible = await locator.first().isVisible({ timeout: 1200 }).catch(() => false);
    if (visible) {
      return locator.first();
    }
  }

  return null;
}

function textLocators(page, text) {
  const nameRegex = toMatcher(text);

  return [
    page.getByRole("button", { name: nameRegex }),
    page.getByRole("link", { name: nameRegex }),
    page.getByRole("menuitem", { name: nameRegex }),
    page.getByRole("tab", { name: nameRegex }),
    page.getByRole("heading", { name: nameRegex }),
    page.getByText(nameRegex),
  ];
}

async function clickByVisibleText(page, candidates) {
  const labels = Array.isArray(candidates) ? candidates : [candidates];

  for (const label of labels) {
    const locator = await firstVisibleLocator(page, textLocators(page, label));
    if (locator) {
      await locator.click();
      await waitForUi(page);
      return label;
    }
  }

  throw new Error(
    `Unable to find clickable element for: ${labels.map((label) => candidateLabel(label)).join(", ")}`,
  );
}

async function assertAnyTextVisible(page, candidates, timeout = 15000) {
  const labels = Array.isArray(candidates) ? candidates : [candidates];

  for (const label of labels) {
    const locator = page.getByText(toMatcher(label)).first();
    const visible = await locator.isVisible({ timeout }).catch(() => false);
    if (visible) {
      return label;
    }
  }

  throw new Error(
    `None of the expected texts is visible: ${labels.map((label) => candidateLabel(label)).join(", ")}`,
  );
}

async function sidebarVisible(page) {
  const navLike = page.locator("aside, nav, [role='navigation']");
  if (await navLike.first().isVisible({ timeout: 8000 }).catch(() => false)) {
    for (const hint of APP_SIDEBAR_HINTS) {
      const hasHint = await navLike
        .filter({ hasText: hint })
        .first()
        .isVisible({ timeout: 1000 })
        .catch(() => false);
      if (hasHint) {
        return true;
      }
    }
  }

  for (const hint of APP_SIDEBAR_HINTS) {
    const visible = await page.getByText(hint).first().isVisible({ timeout: 1200 }).catch(() => false);
    if (visible) {
      return true;
    }
  }

  return false;
}

async function capture(page, dir, name, fullPage = false) {
  const filePath = path.join(dir, name);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function clickGoogleLogin(page, context) {
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickByVisibleText(page, [
    "Sign in with Google",
    "Iniciar sesión con Google",
    "Iniciar sesion con Google",
    "Iniciar sesión",
    "Iniciar sesion",
    "Login with Google",
    "Continuar con Google",
  ]);
  const popup = await popupPromise;

  return popup || page;
}

async function handleGoogleAccountSelection(authPage, email) {
  const accountText = authPage.getByText(toMatcher(email)).first();
  if (await accountText.isVisible({ timeout: 5000 }).catch(() => false)) {
    await accountText.click();
    await waitForUi(authPage);
  }

  const emailField = authPage.locator("input[type='email'], input[name='identifier']").first();
  if (await emailField.isVisible({ timeout: 2000 }).catch(() => false)) {
    await emailField.fill(email);
    const nextButton = await firstVisibleLocator(authPage, [
      authPage.getByRole("button", { name: /Siguiente|Next/i }),
      authPage.getByText(/Siguiente|Next/i),
    ]);
    if (nextButton) {
      await nextButton.click();
      await waitForUi(authPage);
    }
  }

  const password = process.env.GOOGLE_PASSWORD;
  const passwordField = authPage.locator("input[type='password']").first();
  if (password && (await passwordField.isVisible({ timeout: 3000 }).catch(() => false))) {
    await passwordField.fill(password);
    const nextButton = await firstVisibleLocator(authPage, [
      authPage.getByRole("button", { name: /Siguiente|Next/i }),
      authPage.getByText(/Siguiente|Next/i),
    ]);
    if (nextButton) {
      await nextButton.click();
      await waitForUi(authPage);
    }
  }
}

async function validateLegalDocument({
  appPage,
  context,
  report,
  reportKey,
  linkText,
  headingTexts,
  screenshotsDir,
  screenshotName,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickByVisibleText(appPage, linkText);
  const popup = await popupPromise;
  const legalPage = popup || appPage;

  await legalPage.bringToFront();
  await waitForUi(legalPage);

  let headingMatched = false;
  for (const headingText of headingTexts) {
    const headingLocator = legalPage.getByRole("heading", { name: toMatcher(headingText) }).first();
    if (await headingLocator.isVisible({ timeout: 4000 }).catch(() => false)) {
      headingMatched = true;
      break;
    }
  }
  if (!headingMatched) {
    await assertAnyTextVisible(legalPage, headingTexts, 5000);
  }

  let contentMatched = false;
  for (const hint of LEGAL_CONTENT_HINTS) {
    if (await legalPage.getByText(hint).first().isVisible({ timeout: 1500 }).catch(() => false)) {
      contentMatched = true;
      break;
    }
  }
  if (!contentMatched) {
    const bodyText = await legalPage.locator("body").innerText().catch(() => "");
    if (bodyText.trim().length < 120) {
      throw new Error("Legal content appears empty or too short.");
    }
  }

  const screenshotPath = await capture(legalPage, screenshotsDir, screenshotName, true);
  const finalUrl = legalPage.url();

  report[reportKey] = {
    status: "PASS",
    details: "Legal document validated successfully.",
    finalUrl,
    screenshot: screenshotPath,
  };

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await legalPage.goBack().catch(() => null);
    await waitForUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const screenshotsDir = path.join(
    process.cwd(),
    "test-results",
    "saleads_mi_negocio_full_test",
    "screenshots",
  );
  const reportDir = path.join(process.cwd(), "test-results", "saleads_mi_negocio_full_test");
  const reportPath = path.join(reportDir, "report.json");

  await fs.mkdir(screenshotsDir, { recursive: true });

  const report = createReport();
  const failures = [];
  const accountEmail = process.env.GOOGLE_ACCOUNT_EMAIL || DEFAULT_GOOGLE_ACCOUNT;

  const entryUrlEnvKey = ENTRY_URL_ENV_KEYS.find((key) => process.env[key]);
  if (entryUrlEnvKey) {
    await page.goto(process.env[entryUrlEnvKey], { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else {
    throw new Error(
      "No environment URL found. Provide SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL/TARGET_URL).",
    );
  }

  const runStep = async (key, handler) => {
    try {
      await handler();
      if (report[key].status !== "PASS") {
        report[key] = { status: "PASS", details: "Validation completed." };
      }
    } catch (error) {
      failures.push(key);
      report[key] = {
        status: "FAIL",
        details: error instanceof Error ? error.message : String(error),
      };
    }
  };

  try {
    await runStep("Login", async () => {
      if (await sidebarVisible(page)) {
        const screenshotPath = await capture(page, screenshotsDir, "01-dashboard-after-login.png", true);
        report.Login = {
          status: "PASS",
          details: "Session already authenticated; dashboard detected.",
          screenshot: screenshotPath,
        };
        return;
      }

      const authPage = await clickGoogleLogin(page, context);
      await authPage.bringToFront();
      await waitForUi(authPage);
      await handleGoogleAccountSelection(authPage, accountEmail);

      await page.bringToFront();
      await expect.poll(async () => sidebarVisible(page), { timeout: 90000 }).toBeTruthy();

      const screenshotPath = await capture(page, screenshotsDir, "01-dashboard-after-login.png", true);
      report.Login = {
        status: "PASS",
        details: "Main app interface and left sidebar are visible.",
        screenshot: screenshotPath,
      };
    });

    await runStep("Mi Negocio menu", async () => {
      await clickByVisibleText(page, ["Mi Negocio"]);
      await assertAnyTextVisible(page, ["Agregar Negocio", "Administrar Negocios"]);
      const screenshotPath = await capture(page, screenshotsDir, "02-mi-negocio-menu-expanded.png");
      report["Mi Negocio menu"] = {
        status: "PASS",
        details: "Mi Negocio expanded with expected submenu entries.",
        screenshot: screenshotPath,
      };
    });

    await runStep("Agregar Negocio modal", async () => {
      await clickByVisibleText(page, ["Agregar Negocio"]);
      await assertAnyTextVisible(page, ["Crear Nuevo Negocio"]);
      await assertAnyTextVisible(page, ["Nombre del Negocio"]);
      await assertAnyTextVisible(page, ["Tienes 2 de 3 negocios"]);
      await assertAnyTextVisible(page, ["Cancelar"]);
      await assertAnyTextVisible(page, ["Crear Negocio"]);

      const businessNameField = await firstVisibleLocator(page, [
        page.getByLabel(/Nombre del Negocio/i),
        page.locator("input[placeholder*='Nombre']").first(),
        page.locator("input").first(),
      ]);
      if (businessNameField) {
        await businessNameField.click();
        await businessNameField.fill("Negocio Prueba Automatizacion");
      }

      const screenshotPath = await capture(page, screenshotsDir, "03-agregar-negocio-modal.png");
      await clickByVisibleText(page, ["Cancelar"]);
      report["Agregar Negocio modal"] = {
        status: "PASS",
        details: "Crear Nuevo Negocio modal validated and closed.",
        screenshot: screenshotPath,
      };
    });

    await runStep("Administrar Negocios view", async () => {
      const adminVisible = await page
        .getByText(/Administrar Negocios/i)
        .first()
        .isVisible({ timeout: 1500 })
        .catch(() => false);
      if (!adminVisible) {
        await clickByVisibleText(page, ["Mi Negocio"]);
      }

      await clickByVisibleText(page, ["Administrar Negocios"]);
      await assertAnyTextVisible(page, [/Informaci[oó]n General/i, "Informacion General"]);
      await assertAnyTextVisible(page, ["Detalles de la Cuenta"]);
      await assertAnyTextVisible(page, ["Tus Negocios"]);
      await assertAnyTextVisible(page, [/Secci[oó]n Legal/i, "Seccion Legal"]);
      const screenshotPath = await capture(page, screenshotsDir, "04-administrar-negocios-account-page.png", true);
      report["Administrar Negocios view"] = {
        status: "PASS",
        details: "Account management page loaded with expected sections.",
        screenshot: screenshotPath,
      };
    });

    await runStep("Información General", async () => {
      const emailRegex = /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i;
      const nameVisible = await page
        .locator("section,div")
        .filter({ hasText: /Informaci[oó]n General/i })
        .first()
        .isVisible({ timeout: 8000 })
        .catch(() => false);
      if (!nameVisible) {
        throw new Error("Informacion General section was not visible.");
      }

      const emailVisible = await page.getByText(emailRegex).first().isVisible({ timeout: 4000 }).catch(() => false);
      if (!emailVisible) {
        throw new Error("User email was not visible.");
      }

      await assertAnyTextVisible(page, ["BUSINESS PLAN"]);
      await assertAnyTextVisible(page, ["Cambiar Plan"]);
      report["Información General"] = {
        status: "PASS",
        details: "Informacion General details validated.",
      };
    });

    await runStep("Detalles de la Cuenta", async () => {
      await assertAnyTextVisible(page, ["Cuenta creada"]);
      await assertAnyTextVisible(page, ["Estado activo"]);
      await assertAnyTextVisible(page, ["Idioma seleccionado"]);
      report["Detalles de la Cuenta"] = {
        status: "PASS",
        details: "Detalles de la Cuenta labels are visible.",
      };
    });

    await runStep("Tus Negocios", async () => {
      await assertAnyTextVisible(page, ["Tus Negocios"]);
      await assertAnyTextVisible(page, ["Agregar Negocio"]);
      await assertAnyTextVisible(page, ["Tienes 2 de 3 negocios"]);
      report["Tus Negocios"] = {
        status: "PASS",
        details: "Business list and capacity information are visible.",
      };
    });

    await runStep("Términos y Condiciones", async () => {
      await validateLegalDocument({
        appPage: page,
        context,
        report,
        reportKey: "Términos y Condiciones",
        linkText: ["Términos y Condiciones", "Terminos y Condiciones"],
        headingTexts: ["Términos y Condiciones", "Terminos y Condiciones"],
        screenshotsDir,
        screenshotName: "08-terminos-y-condiciones.png",
      });
    });

    await runStep("Política de Privacidad", async () => {
      await validateLegalDocument({
        appPage: page,
        context,
        report,
        reportKey: "Política de Privacidad",
        linkText: ["Política de Privacidad", "Politica de Privacidad"],
        headingTexts: ["Política de Privacidad", "Politica de Privacidad"],
        screenshotsDir,
        screenshotName: "09-politica-de-privacidad.png",
      });
    });
  } finally {
    await fs.mkdir(reportDir, { recursive: true });
    await fs.writeFile(
      reportPath,
      JSON.stringify(
        {
          suite: "saleads_mi_negocio_full_test",
          runAt: new Date().toISOString(),
          environmentUrl: page.url(),
          finalReport: report,
        },
        null,
        2,
      ),
      "utf-8",
    );
  }

  expect(failures, `One or more validation steps failed: ${failures.join(", ")}`).toEqual([]);
});
