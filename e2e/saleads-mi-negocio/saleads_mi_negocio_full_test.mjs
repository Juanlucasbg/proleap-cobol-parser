import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const GOOGLE_ACCOUNT_NAME = process.env.GOOGLE_ACCOUNT_NAME || "";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

const SECTION_RESULTS = Object.fromEntries(
  REPORT_FIELDS.map((field) => [
    field,
    {
      status: "PASS",
      checks: [],
    },
  ]),
);

const LEGAL_URLS = {
  "Terminos y Condiciones": "",
  "Politica de Privacidad": "",
};

const SCREENSHOTS = [];

function boolFromEnv(value, defaultValue) {
  if (value === undefined) {
    return defaultValue;
  }
  return value.toLowerCase() !== "false";
}

function getTimestampForPath() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function sanitizeFileName(value) {
  return value.replace(/[^a-zA-Z0-9-_]/g, "_");
}

function markCheck(section, check, pass, details = "") {
  SECTION_RESULTS[section].checks.push({
    check,
    status: pass ? "PASS" : "FAIL",
    details,
  });
  if (!pass) {
    SECTION_RESULTS[section].status = "FAIL";
  }
}

async function evaluate(section, check, callback) {
  try {
    await callback();
    markCheck(section, check, true);
    return true;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    markCheck(section, check, false, message);
    return false;
  }
}

async function waitForUiLoad(page) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 15000 }),
    page.waitForLoadState("networkidle", { timeout: 15000 }),
  ]);
  await page.waitForTimeout(500);
}

async function isVisible(locator) {
  try {
    return await locator.first().isVisible({ timeout: 2500 });
  } catch {
    return false;
  }
}

async function clickByVisibleText(page, texts, contextLabel) {
  for (const text of texts) {
    const candidates = [
      page.getByRole("button", { name: text, exact: false }).first(),
      page.getByRole("link", { name: text, exact: false }).first(),
      page.getByRole("menuitem", { name: text, exact: false }).first(),
      page.getByText(text, { exact: false }).first(),
    ];

    for (const locator of candidates) {
      if (await isVisible(locator)) {
        await locator.click();
        await waitForUiLoad(page);
        return;
      }
    }
  }

  throw new Error(
    `Unable to click ${contextLabel}. Tried: ${texts.map((text) => `"${text}"`).join(", ")}`,
  );
}

async function expectVisibleText(page, texts, section, check) {
  await evaluate(section, check, async () => {
    for (const text of texts) {
      const locator = page.getByText(text, { exact: false }).first();
      if (await isVisible(locator)) {
        return;
      }
    }
    throw new Error(`None of the expected texts were visible: ${texts.join(", ")}`);
  });
}

async function expectVisibleLocator(locator, section, check, errorMessage) {
  await evaluate(section, check, async () => {
    if (!(await isVisible(locator))) {
      throw new Error(errorMessage);
    }
  });
}

async function takeCheckpointScreenshot(page, artifactsDir, name, fullPage = true) {
  const fileName = `${sanitizeFileName(name)}.png`;
  const screenshotPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  SCREENSHOTS.push(screenshotPath);
}

async function tryClickGoogleAccount(page) {
  const accountLocator = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await isVisible(accountLocator)) {
    await accountLocator.click();
    await waitForUiLoad(page);
    return true;
  }
  return false;
}

async function openAndValidateLegalDocument({
  page,
  artifactsDir,
  linkText,
  expectedHeading,
  reportField,
  screenshotName,
}) {
  const originalUrl = page.url();
  const popupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
  const navigationPromise = page.waitForNavigation({ timeout: 12000 }).catch(() => null);

  await evaluate(reportField, `Click "${linkText}"`, async () => {
    await clickByVisibleText(page, [linkText], `legal link "${linkText}"`);
  });

  const popup = await popupPromise;
  let legalPage = page;
  if (popup) {
    legalPage = popup;
    await waitForUiLoad(legalPage);
  } else {
    await navigationPromise;
    await waitForUiLoad(legalPage);
  }

  await expectVisibleText(
    legalPage,
    [expectedHeading],
    reportField,
    `Heading "${expectedHeading}" is visible`,
  );

  await evaluate(reportField, "Legal content text is visible", async () => {
    const contentCandidates = [
      legalPage.locator("article p").first(),
      legalPage.locator("main p").first(),
      legalPage.locator("p").first(),
      legalPage.getByText(/(servicio|datos|privacidad|terminos|condiciones|uso)/i).first(),
    ];
    for (const candidate of contentCandidates) {
      if (await isVisible(candidate)) {
        return;
      }
    }
    throw new Error("No legal paragraph content could be validated.");
  });

  await evaluate(reportField, "Capture screenshot", async () => {
    await takeCheckpointScreenshot(legalPage, artifactsDir, screenshotName, true);
  });

  await evaluate(reportField, "Capture final URL", async () => {
    const finalUrl = legalPage.url();
    if (!finalUrl || finalUrl === "about:blank") {
      throw new Error("Final legal URL is empty.");
    }
    LEGAL_URLS[reportField] = finalUrl;
  });

  await evaluate(reportField, "Return to application tab", async () => {
    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUiLoad(page);
    } else if (page.url() !== originalUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    }
  });
}

async function run() {
  const headless = boolFromEnv(process.env.HEADLESS, true);
  const startUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_APP_URL ||
    process.env.BASE_URL ||
    process.argv[2] ||
    "";
  const artifactsDir =
    process.env.SALEADS_ARTIFACTS_DIR ||
    path.join(process.cwd(), "artifacts", getTimestampForPath());

  await fs.mkdir(artifactsDir, { recursive: true });

  if (!startUrl) {
    markCheck(
      "Login",
      "Open login page",
      false,
      "No URL provided. Set SALEADS_LOGIN_URL, SALEADS_APP_URL, BASE_URL, or pass URL as first arg.",
    );
    for (const field of REPORT_FIELDS.filter((name) => name !== "Login")) {
      markCheck(field, "Blocked by login precondition", false, "Login page URL was not provided.");
    }

    const reportPath = path.join(artifactsDir, "saleads_mi_negocio_report.json");
    await fs.writeFile(
      reportPath,
      JSON.stringify(
        {
          name: "saleads_mi_negocio_full_test",
          status: "FAIL",
          reason: "Missing start URL.",
          results: SECTION_RESULTS,
          legalUrls: LEGAL_URLS,
          screenshots: SCREENSHOTS,
        },
        null,
        2,
      ),
      "utf8",
    );

    console.log(JSON.stringify({ status: "FAIL", reportPath }, null, 2));
    process.exitCode = 1;
    return;
  }

  const browser = await chromium.launch({ headless });
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    await evaluate("Login", "Open login page", async () => {
      await page.goto(startUrl, { waitUntil: "domcontentloaded", timeout: 30000 });
      await waitForUiLoad(page);
    });

    const loginPopupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
    await evaluate("Login", "Click login button or Sign in with Google", async () => {
      await clickByVisibleText(
        page,
        [
          "Sign in with Google",
          "Iniciar sesion con Google",
          "Iniciar sesión con Google",
          "Login with Google",
          "Continuar con Google",
          "Google",
        ],
        "Google sign in button",
      );
    });

    const loginPopup = await loginPopupPromise;
    if (loginPopup) {
      await waitForUiLoad(loginPopup);
      await evaluate("Login", `Choose Google account (${GOOGLE_ACCOUNT_EMAIL})`, async () => {
        const clicked = await tryClickGoogleAccount(loginPopup);
        if (!clicked) {
          throw new Error("Google account chooser was opened but account was not visible.");
        }
      });
    } else {
      await evaluate("Login", "Optionally choose Google account", async () => {
        await tryClickGoogleAccount(page);
      });
    }

    await evaluate("Login", "Main application interface appears", async () => {
      await waitForUiLoad(page);
      const notGoogleUrl = !page.url().includes("accounts.google.");
      const appSignals = [
        page.locator("main").first(),
        page.locator("aside").first(),
        page.getByRole("navigation").first(),
        page.getByText("Negocio", { exact: false }).first(),
      ];
      const hasAppSignal = await Promise.any(
        appSignals.map(async (signal) => {
          if (await isVisible(signal)) {
            return true;
          }
          throw new Error("signal not visible");
        }),
      ).catch(() => false);
      if (!notGoogleUrl || !hasAppSignal) {
        throw new Error("Main app shell did not appear after Google login.");
      }
    });

    await expectVisibleLocator(
      page.locator("aside, nav").first(),
      "Login",
      "Left sidebar navigation is visible",
      "Sidebar navigation was not visible.",
    );

    await evaluate("Login", "Capture dashboard screenshot", async () => {
      await takeCheckpointScreenshot(page, artifactsDir, "01-dashboard-loaded", true);
    });

    await evaluate("Mi Negocio menu", "Locate left sidebar navigation", async () => {
      const sidebar = page.locator("aside, nav").first();
      if (!(await isVisible(sidebar))) {
        throw new Error("Sidebar is not visible.");
      }
    });

    await evaluate("Mi Negocio menu", 'Find section "Negocio"', async () => {
      const negocioLabel = page.getByText("Negocio", { exact: false }).first();
      if (!(await isVisible(negocioLabel))) {
        throw new Error('Section label "Negocio" is not visible.');
      }
    });

    await evaluate("Mi Negocio menu", 'Click option "Mi Negocio"', async () => {
      await clickByVisibleText(page, ["Mi Negocio"], '"Mi Negocio" menu option');
    });

    await expectVisibleText(
      page,
      ["Agregar Negocio"],
      "Mi Negocio menu",
      'Confirm "Agregar Negocio" is visible',
    );

    await expectVisibleText(
      page,
      ["Administrar Negocios"],
      "Mi Negocio menu",
      'Confirm "Administrar Negocios" is visible',
    );

    await evaluate("Mi Negocio menu", "Capture expanded menu screenshot", async () => {
      await takeCheckpointScreenshot(page, artifactsDir, "02-mi-negocio-menu-expanded", true);
    });

    await evaluate("Agregar Negocio modal", 'Click "Agregar Negocio"', async () => {
      await clickByVisibleText(page, ["Agregar Negocio"], '"Agregar Negocio" menu option');
    });

    await expectVisibleText(
      page,
      ["Crear Nuevo Negocio"],
      "Agregar Negocio modal",
      'Modal title "Crear Nuevo Negocio" is visible',
    );

    await evaluate(
      "Agregar Negocio modal",
      'Input field "Nombre del Negocio" exists',
      async () => {
        const fieldCandidates = [
          page.getByLabel("Nombre del Negocio", { exact: false }).first(),
          page.getByPlaceholder("Nombre del Negocio", { exact: false }).first(),
          page.locator('input[name*="negocio" i]').first(),
          page.locator("input").first(),
        ];
        for (const candidate of fieldCandidates) {
          if (await isVisible(candidate)) {
            return;
          }
        }
        throw new Error('Input "Nombre del Negocio" was not found.');
      },
    );

    await expectVisibleText(
      page,
      ["Tienes 2 de 3 negocios"],
      "Agregar Negocio modal",
      'Text "Tienes 2 de 3 negocios" is visible',
    );

    await expectVisibleText(
      page,
      ["Cancelar"],
      "Agregar Negocio modal",
      'Button "Cancelar" is present',
    );

    await expectVisibleText(
      page,
      ["Crear Negocio"],
      "Agregar Negocio modal",
      'Button "Crear Negocio" is present',
    );

    await evaluate("Agregar Negocio modal", "Capture modal screenshot", async () => {
      await takeCheckpointScreenshot(page, artifactsDir, "03-agregar-negocio-modal", true);
    });

    await evaluate("Agregar Negocio modal", 'Type "Negocio Prueba Automatizacion" (optional)', async () => {
      const input = page
        .getByLabel("Nombre del Negocio", { exact: false })
        .or(page.getByPlaceholder("Nombre del Negocio", { exact: false }))
        .first();
      await input.click();
      await input.fill("Negocio Prueba Automatizacion");
      await waitForUiLoad(page);
    });

    await evaluate("Agregar Negocio modal", 'Click "Cancelar" to close modal', async () => {
      await clickByVisibleText(page, ["Cancelar"], '"Cancelar" modal button');
    });

    await evaluate("Administrar Negocios view", 'Expand "Mi Negocio" if collapsed', async () => {
      const adminOption = page.getByText("Administrar Negocios", { exact: false }).first();
      if (!(await isVisible(adminOption))) {
        await clickByVisibleText(page, ["Mi Negocio"], '"Mi Negocio" menu option');
      }
    });

    await evaluate("Administrar Negocios view", 'Click "Administrar Negocios"', async () => {
      await clickByVisibleText(page, ["Administrar Negocios"], '"Administrar Negocios" menu option');
    });

    await expectVisibleText(
      page,
      ["Informacion General", "Información General"],
      "Administrar Negocios view",
      'Section "Informacion General" exists',
    );

    await expectVisibleText(
      page,
      ["Detalles de la Cuenta"],
      "Administrar Negocios view",
      'Section "Detalles de la Cuenta" exists',
    );

    await expectVisibleText(
      page,
      ["Tus Negocios"],
      "Administrar Negocios view",
      'Section "Tus Negocios" exists',
    );

    await expectVisibleText(
      page,
      ["Seccion Legal", "Sección Legal"],
      "Administrar Negocios view",
      'Section "Seccion Legal" exists',
    );

    await evaluate("Administrar Negocios view", "Capture full account page screenshot", async () => {
      await takeCheckpointScreenshot(page, artifactsDir, "04-administrar-negocios-page", true);
    });

    await evaluate("Informacion General", "User name is visible", async () => {
      if (GOOGLE_ACCOUNT_NAME) {
        const expectedName = page.getByText(GOOGLE_ACCOUNT_NAME, { exact: false }).first();
        if (!(await isVisible(expectedName))) {
          throw new Error(`Configured user name "${GOOGLE_ACCOUNT_NAME}" is not visible.`);
        }
        return;
      }

      const candidateName = page.locator("h1, h2, h3, strong").first();
      if (!(await isVisible(candidateName))) {
        throw new Error(
          "No explicit GOOGLE_ACCOUNT_NAME configured and no visible user-name-like heading was found.",
        );
      }
    });

    await evaluate("Informacion General", "User email is visible", async () => {
      const emailPattern = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
      const emailByKnownAccount = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await isVisible(emailByKnownAccount)) {
        return;
      }
      const genericEmail = page.getByText(emailPattern).first();
      if (!(await isVisible(genericEmail))) {
        throw new Error("No visible user email found.");
      }
    });

    await expectVisibleText(
      page,
      ["BUSINESS PLAN"],
      "Informacion General",
      'Text "BUSINESS PLAN" is visible',
    );

    await expectVisibleText(
      page,
      ["Cambiar Plan"],
      "Informacion General",
      'Button "Cambiar Plan" is visible',
    );

    await expectVisibleText(
      page,
      ["Cuenta creada"],
      "Detalles de la Cuenta",
      '"Cuenta creada" is visible',
    );
    await expectVisibleText(
      page,
      ["Estado activo"],
      "Detalles de la Cuenta",
      '"Estado activo" is visible',
    );
    await expectVisibleText(
      page,
      ["Idioma seleccionado"],
      "Detalles de la Cuenta",
      '"Idioma seleccionado" is visible',
    );

    await evaluate("Tus Negocios", "Business list is visible", async () => {
      const candidates = [
        page.locator('section:has-text("Tus Negocios") li').first(),
        page.locator('section:has-text("Tus Negocios") [role="listitem"]').first(),
        page.locator('section:has-text("Tus Negocios") table tbody tr').first(),
        page.getByText("Tus Negocios", { exact: false }).first(),
      ];
      for (const candidate of candidates) {
        if (await isVisible(candidate)) {
          return;
        }
      }
      throw new Error("Business list or section content was not visible.");
    });

    await expectVisibleText(
      page,
      ["Agregar Negocio"],
      "Tus Negocios",
      'Button "Agregar Negocio" exists',
    );

    await expectVisibleText(
      page,
      ["Tienes 2 de 3 negocios"],
      "Tus Negocios",
      'Text "Tienes 2 de 3 negocios" is visible',
    );

    await openAndValidateLegalDocument({
      page,
      artifactsDir,
      linkText: "Términos y Condiciones",
      expectedHeading: "Términos y Condiciones",
      reportField: "Terminos y Condiciones",
      screenshotName: "05-terminos-y-condiciones",
    });

    await openAndValidateLegalDocument({
      page,
      artifactsDir,
      linkText: "Política de Privacidad",
      expectedHeading: "Política de Privacidad",
      reportField: "Politica de Privacidad",
      screenshotName: "06-politica-de-privacidad",
    });
  } finally {
    await context.close();
    await browser.close();
  }

  const overallStatus = Object.values(SECTION_RESULTS).every((result) => result.status === "PASS")
    ? "PASS"
    : "FAIL";

  const reportPayload = {
    name: "saleads_mi_negocio_full_test",
    executedAt: new Date().toISOString(),
    status: overallStatus,
    results: SECTION_RESULTS,
    legalUrls: LEGAL_URLS,
    screenshots: SCREENSHOTS,
  };

  const reportPath = path.join(artifactsDir, "saleads_mi_negocio_report.json");
  await fs.writeFile(reportPath, JSON.stringify(reportPayload, null, 2), "utf8");

  console.log(JSON.stringify({ status: overallStatus, reportPath, report: reportPayload }, null, 2));
  if (overallStatus === "FAIL") {
    process.exitCode = 1;
  }
}

run().catch((error) => {
  console.error("Fatal test execution error:", error);
  process.exitCode = 1;
});
