const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const RUN_ID = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACTS_DIR = path.join(__dirname, "..", "artifacts", RUN_ID);

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

function buildReport() {
  const validations = {};

  for (const field of REPORT_FIELDS) {
    validations[field] = {
      status: "FAIL",
      details: [],
      evidence: [],
      finalUrl: null,
    };
  }

  return {
    testName: "saleads_mi_negocio_full_test",
    startedAt: new Date().toISOString(),
    environment: {
      startUrl: process.env.SALEADS_LOGIN_URL || null,
      googleAccount: GOOGLE_ACCOUNT_EMAIL,
    },
    validations,
    status: "FAIL",
    reportFile: null,
    completedAt: null,
  };
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function waitForVisible(locator, timeout = 2500) {
  try {
    await locator.waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function resolveVisibleLocator(page, textRegex, preferClickable = false) {
  const clickableCandidates = [
    page.getByRole("button", { name: textRegex }).first(),
    page.getByRole("link", { name: textRegex }).first(),
    page.getByRole("menuitem", { name: textRegex }).first(),
    page.getByRole("tab", { name: textRegex }).first(),
    page.getByRole("option", { name: textRegex }).first(),
    page.getByText(textRegex).first(),
  ];

  const nonClickableCandidates = [
    page.getByRole("heading", { name: textRegex }).first(),
    page.getByLabel(textRegex).first(),
    page.getByText(textRegex).first(),
  ];

  const candidates = preferClickable
    ? [...clickableCandidates, ...nonClickableCandidates]
    : [...nonClickableCandidates, ...clickableCandidates];

  for (const locator of candidates) {
    if (await waitForVisible(locator)) {
      return locator;
    }
  }

  throw new Error(`Visible element not found for pattern: ${textRegex}`);
}

async function clickByText(page, textRegex) {
  const locator = await resolveVisibleLocator(page, textRegex, true);
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function maybeClickByText(page, textRegex, timeout = 2000) {
  try {
    const locator = await resolveVisibleLocator(page, textRegex, true);
    await locator.scrollIntoViewIfNeeded().catch(() => {});
    await locator.click({ timeout });
    await waitForUi(page);
    return true;
  } catch {
    return false;
  }
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function captureCheckpoint(page, report, stepName, fileStem, fullPage = false) {
  const filename = `${fileStem}.png`;
  const absolutePath = path.join(ARTIFACTS_DIR, filename);
  await page.screenshot({ path: absolutePath, fullPage });
  report.validations[stepName].evidence.push(path.relative(path.join(__dirname, ".."), absolutePath));
}

async function runValidation(report, name, action) {
  try {
    await action();
    report.validations[name].status = "PASS";
  } catch (error) {
    report.validations[name].status = "FAIL";
    report.validations[name].details.push(error instanceof Error ? error.message : String(error));
  }
}

async function validateLegalPage({
  page,
  context,
  report,
  validationName,
  linkRegex,
  headingRegex,
  screenshotName,
}) {
  const link = await resolveVisibleLocator(page, linkRegex, true);
  await link.scrollIntoViewIfNeeded().catch(() => {});

  const newTabPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await link.click();
  const maybeNewTab = await newTabPromise;
  const legalPage = maybeNewTab || page;

  await waitForUi(legalPage);
  await resolveVisibleLocator(legalPage, headingRegex, false);

  const legalText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (legalText.length < 120) {
    throw new Error("Legal content text is too short or not visible.");
  }

  report.validations[validationName].finalUrl = legalPage.url();
  await captureCheckpoint(legalPage, report, validationName, screenshotName, true);

  if (maybeNewTab) {
    await maybeNewTab.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
  const report = buildReport();

  await runValidation(report, "Login", async () => {
    if (process.env.SALEADS_LOGIN_URL) {
      await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    if (page.url() === "about:blank") {
      throw new Error(
        "No login page is loaded. Provide SALEADS_LOGIN_URL (environment-specific login URL)."
      );
    }

    const loginButtonRegex =
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|acceder con google|google/i;
    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    const loginButton = await resolveVisibleLocator(page, loginButtonRegex, true);
    await loginButton.click();

    const popup = await popupPromise;
    const googlePage = popup || page;
    await waitForUi(googlePage);

    const accountRegex = new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i");
    const selectedAccount = await maybeClickByText(googlePage, accountRegex, 5000);
    if (selectedAccount) {
      await waitForUi(googlePage);
      report.validations["Login"].details.push(`Selected account: ${GOOGLE_ACCOUNT_EMAIL}`);
    } else {
      report.validations["Login"].details.push(
        "Google account picker not shown or account selection was already completed."
      );
    }

    if (popup) {
      await page.bringToFront();
      await waitForUi(page);
    }

    const sidebarVisible = await waitForVisible(page.locator("aside").first(), 8000);
    if (!sidebarVisible) {
      await resolveVisibleLocator(page, /negocio|dashboard|inicio/i, false);
    }

    await captureCheckpoint(page, report, "Login", "01-dashboard-loaded", true);
  });

  await runValidation(report, "Mi Negocio menu", async () => {
    await maybeClickByText(page, /^negocio$/i);
    await clickByText(page, /mi negocio/i);
    await resolveVisibleLocator(page, /agregar negocio/i, false);
    await resolveVisibleLocator(page, /administrar negocios/i, false);
    await captureCheckpoint(page, report, "Mi Negocio menu", "02-mi-negocio-menu-expanded");
  });

  await runValidation(report, "Agregar Negocio modal", async () => {
    await clickByText(page, /agregar negocio/i);
    await resolveVisibleLocator(page, /crear nuevo negocio/i, false);

    let input = page.getByLabel(/nombre del negocio/i).first();
    if (!(await waitForVisible(input, 3000))) {
      input = page.getByPlaceholder(/nombre del negocio/i).first();
    }
    if (!(await waitForVisible(input, 3000))) {
      throw new Error("Input field 'Nombre del Negocio' is not visible.");
    }

    await resolveVisibleLocator(page, /tienes 2 de 3 negocios/i, false);
    await resolveVisibleLocator(page, /^cancelar$/i, false);
    await resolveVisibleLocator(page, /crear negocio/i, false);
    await captureCheckpoint(page, report, "Agregar Negocio modal", "03-agregar-negocio-modal");

    await input.click();
    await input.fill("Negocio Prueba Automatización");
    await clickByText(page, /^cancelar$/i);
  });

  await runValidation(report, "Administrar Negocios view", async () => {
    const adminVisible = await waitForVisible(page.getByText(/administrar negocios/i).first(), 2000);
    if (!adminVisible) {
      await maybeClickByText(page, /mi negocio/i);
    }

    await clickByText(page, /administrar negocios/i);
    await resolveVisibleLocator(page, /informaci[oó]n general/i, false);
    await resolveVisibleLocator(page, /detalles de la cuenta/i, false);
    await resolveVisibleLocator(page, /tus negocios/i, false);
    await resolveVisibleLocator(page, /secci[oó]n legal/i, false);
    await captureCheckpoint(
      page,
      report,
      "Administrar Negocios view",
      "04-administrar-negocios-view",
      true
    );
  });

  await runValidation(report, "Información General", async () => {
    const pageText = await page.locator("body").innerText();

    const emailMatch = pageText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    if (!emailMatch) {
      throw new Error("User email is not visible.");
    }

    const nameLinePattern = /^[A-Za-zÀ-ÿ]{2,}(?:\s+[A-Za-zÀ-ÿ]{2,})+$/;
    const hasLikelyName = pageText
      .split("\n")
      .map((line) => line.trim())
      .some((line) => line.length <= 60 && nameLinePattern.test(line));
    if (!hasLikelyName) {
      throw new Error("User name is not clearly visible.");
    }

    await resolveVisibleLocator(page, /business plan/i, false);
    await resolveVisibleLocator(page, /cambiar plan/i, false);
  });

  await runValidation(report, "Detalles de la Cuenta", async () => {
    await resolveVisibleLocator(page, /cuenta creada/i, false);
    await resolveVisibleLocator(page, /estado activo/i, false);
    await resolveVisibleLocator(page, /idioma seleccionado/i, false);
  });

  await runValidation(report, "Tus Negocios", async () => {
    await resolveVisibleLocator(page, /tus negocios/i, false);

    const hasList = await waitForVisible(
      page.locator("ul, table, [role='list'], [role='table'], [data-testid*='business']").first(),
      5000
    );
    if (!hasList) {
      throw new Error("Business list is not visible.");
    }

    await resolveVisibleLocator(page, /agregar negocio/i, false);
    await resolveVisibleLocator(page, /tienes 2 de 3 negocios/i, false);
  });

  await runValidation(report, "Términos y Condiciones", async () => {
    await validateLegalPage({
      page,
      context,
      report,
      validationName: "Términos y Condiciones",
      linkRegex: /t[ée]rminos y condiciones/i,
      headingRegex: /t[ée]rminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones",
    });
  });

  await runValidation(report, "Política de Privacidad", async () => {
    await validateLegalPage({
      page,
      context,
      report,
      validationName: "Política de Privacidad",
      linkRegex: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: "06-politica-de-privacidad",
    });
  });

  const failures = Object.entries(report.validations)
    .filter(([, value]) => value.status === "FAIL")
    .map(([name]) => name);

  report.status = failures.length === 0 ? "PASS" : "FAIL";
  report.completedAt = new Date().toISOString();
  report.reportFile = path.join("artifacts", RUN_ID, "final-report.json");

  const reportPath = path.join(ARTIFACTS_DIR, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  console.log(
    JSON.stringify(
      {
        status: report.status,
        reportFile: report.reportFile,
        failures,
      },
      null,
      2
    )
  );

  expect(
    failures,
    `Validation failures: ${failures.join(", ")}. Review ${reportPath} for details.`
  ).toEqual([]);
});
