const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const REQUIRED_REPORT_FIELDS = [
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const OUTPUT_ROOT = path.resolve(__dirname, "artifacts");
const SCREENSHOT_DIR = path.join(OUTPUT_ROOT, "screenshots");
const REPORT_FILE = path.join(OUTPUT_ROOT, "final-report.json");

const STEP_RESULT = {
  PASS: "PASS",
  FAIL: "FAIL",
  SKIPPED: "SKIPPED",
};

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function toPattern(value) {
  if (value instanceof RegExp) {
    return value;
  }
  const escaped = String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(escaped, "i");
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch (error) {
    // Some pages hold background connections forever. domcontentloaded is enough.
  }
}

async function screenshot(page, fileName, fullPage = false) {
  await ensureDir(SCREENSHOT_DIR);
  const fullPath = path.join(SCREENSHOT_DIR, `${fileName}-${nowStamp()}.png`);
  await page.screenshot({ path: fullPath, fullPage });
  return fullPath;
}

async function firstVisibleLocator(page, patternInput) {
  const pattern = toPattern(patternInput);
  const roleCandidates = [
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByRole("menuitem", { name: pattern }),
    page.getByRole("tab", { name: pattern }),
    page.getByRole("heading", { name: pattern }),
    page.getByText(pattern),
  ];

  for (const locator of roleCandidates) {
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

async function clickByVisibleText(page, label) {
  const locator = await firstVisibleLocator(page, label);
  if (!locator) {
    throw new Error(`No visible element found for label: ${String(label)}`);
  }
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUiLoad(page);
}

async function assertVisible(page, label, timeout = 12000) {
  const startedAt = Date.now();
  const maxUntil = startedAt + timeout;

  while (Date.now() <= maxUntil) {
    const locator = await firstVisibleLocator(page, label);
    if (locator) {
      return true;
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`Expected visible text/element not found: ${String(label)}`);
}

async function assertAnyVisible(page, labels, timeout = 12000) {
  const startedAt = Date.now();
  const maxUntil = startedAt + timeout;

  while (Date.now() <= maxUntil) {
    for (const label of labels) {
      const locator = await firstVisibleLocator(page, label);
      if (locator) {
        return true;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(
    `None of the expected visible labels were found: ${labels.join(", ")}`
  );
}

async function tryGoogleAccountSelection(mainPage, popupPage) {
  const candidatePage = popupPage || mainPage;
  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded").catch(() => {});
  }

  const accountLocator = await firstVisibleLocator(
    candidatePage,
    GOOGLE_ACCOUNT_EMAIL
  );
  if (accountLocator) {
    await accountLocator.click();
    await waitForUiLoad(candidatePage);
  }

  if (popupPage) {
    try {
      await popupPage.waitForEvent("close", { timeout: 15000 });
    } catch (error) {
      // Popup may remain open depending on auth flow. Keep going.
    }
  }
}

async function clickLegalAndValidate({
  page,
  context,
  linkLabel,
  expectedHeading,
  screenshotLabel,
  appUrlBeforeClick,
}) {
  const newTabPromise = context
    .waitForEvent("page", { timeout: 8000 })
    .catch(() => null);

  await clickByVisibleText(page, linkLabel);

  let legalPage = await newTabPromise;
  if (legalPage) {
    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUiLoad(legalPage);
  } else {
    legalPage = page;
    await waitForUiLoad(legalPage);
  }

  await assertVisible(legalPage, expectedHeading, 15000);
  const bodyText = await legalPage.locator("body").innerText();
  if (!bodyText || bodyText.trim().length < 100) {
    throw new Error(`Legal page for "${linkLabel}" appears to have no content.`);
  }

  const screenshotPath = await screenshot(legalPage, screenshotLabel, true);
  const finalUrl = legalPage.url();

  if (legalPage !== page) {
    await legalPage.close().catch(() => {});
    await page.bringToFront();
    await waitForUiLoad(page);
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiLoad(page);
  }

  return { screenshotPath, finalUrl };
}

async function run() {
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (!loginUrl) {
    throw new Error(
      "SALEADS_LOGIN_URL is required. Set it to the current environment login URL."
    );
  }

  await ensureDir(OUTPUT_ROOT);
  await ensureDir(SCREENSHOT_DIR);

  const finalReport = {
    test_name: "saleads_mi_negocio_full_test",
    started_at: new Date().toISOString(),
    rules: [
      "Environment-agnostic URL via SALEADS_LOGIN_URL variable.",
      "Visible text selectors are preferred.",
      "Wait after each click.",
      "Support for same-tab and new-tab legal links.",
      "Screenshots captured at key checkpoints.",
    ],
    fields: Object.fromEntries(
      REQUIRED_REPORT_FIELDS.map((field) => [field, STEP_RESULT.SKIPPED])
    ),
    evidence: {
      screenshots: [],
      final_urls: {},
    },
    notes: [],
  };

  const browser = await chromium.launch({
    headless: process.env.HEADLESS !== "false",
    slowMo: Number(process.env.SLOW_MO_MS || 0),
  });
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    // Step 1: Login with Google
    try {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);

      await assertAnyVisible(page, [
        /sign in with google/i,
        /iniciar sesión con google/i,
        /google/i,
        /login/i,
      ]);

      const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
      await clickByVisibleText(page, /sign in with google|iniciar sesión con google|google|login/i);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
      }

      await tryGoogleAccountSelection(page, popup);

      await assertAnyVisible(page, [/negocio/i, /mi negocio/i, /dashboard/i], 20000);
      await assertAnyVisible(page, [/sidebar/i, /negocio/i], 20000);

      const screenshotPath = await screenshot(page, "01-dashboard-loaded", true);
      finalReport.evidence.screenshots.push(screenshotPath);
      finalReport.fields.Login = STEP_RESULT.PASS;
    } catch (error) {
      finalReport.fields.Login = STEP_RESULT.FAIL;
      finalReport.notes.push(`Login step failed: ${error.message}`);
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByVisibleText(page, /negocio/i);
      await clickByVisibleText(page, /mi negocio/i);
      await assertVisible(page, /agregar negocio/i);
      await assertVisible(page, /administrar negocios/i);

      const screenshotPath = await screenshot(page, "02-mi-negocio-expanded", false);
      finalReport.evidence.screenshots.push(screenshotPath);
      finalReport.fields["Mi Negocio menu"] = STEP_RESULT.PASS;
    } catch (error) {
      finalReport.fields["Mi Negocio menu"] = STEP_RESULT.FAIL;
      finalReport.notes.push(`Mi Negocio menu step failed: ${error.message}`);
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, /agregar negocio/i);
      await assertVisible(page, /crear nuevo negocio/i);
      await assertVisible(page, /nombre del negocio/i);
      await assertVisible(page, /tienes 2 de 3 negocios/i);
      await assertVisible(page, /cancelar/i);
      await assertVisible(page, /crear negocio/i);

      const screenshotPath = await screenshot(page, "03-agregar-negocio-modal", false);
      finalReport.evidence.screenshots.push(screenshotPath);

      const inputLocator = await firstVisibleLocator(page, /nombre del negocio/i);
      if (inputLocator) {
        await inputLocator.click().catch(() => {});
      }
      const fieldByPlaceholder = page.getByPlaceholder(/nombre del negocio/i).first();
      if (await fieldByPlaceholder.isVisible().catch(() => false)) {
        await fieldByPlaceholder.fill("Negocio Prueba Automatización");
      }
      await clickByVisibleText(page, /cancelar/i);

      finalReport.fields["Agregar Negocio modal"] = STEP_RESULT.PASS;
    } catch (error) {
      finalReport.fields["Agregar Negocio modal"] = STEP_RESULT.FAIL;
      finalReport.notes.push(`Agregar Negocio modal step failed: ${error.message}`);
    }

    // Step 4: Open Administrar Negocios
    try {
      const adminVisible = await firstVisibleLocator(page, /administrar negocios/i);
      if (!adminVisible) {
        await clickByVisibleText(page, /mi negocio/i);
      }

      await clickByVisibleText(page, /administrar negocios/i);
      await assertVisible(page, /información general/i, 20000);
      await assertVisible(page, /detalles de la cuenta/i, 20000);
      await assertVisible(page, /tus negocios/i, 20000);
      await assertAnyVisible(page, [/sección legal/i, /terminos/i, /términos/i], 20000);

      const screenshotPath = await screenshot(page, "04-administrar-negocios-full", true);
      finalReport.evidence.screenshots.push(screenshotPath);
      finalReport.fields["Administrar Negocios view"] = STEP_RESULT.PASS;
    } catch (error) {
      finalReport.fields["Administrar Negocios view"] = STEP_RESULT.FAIL;
      finalReport.notes.push(`Administrar Negocios view step failed: ${error.message}`);
    }

    // Step 5: Validate Información General
    try {
      await assertVisible(page, /información general/i);
      await assertAnyVisible(page, [/nombre/i, /usuario/i, /name/i]);
      const bodyText = await page.locator("body").innerText();
      if (!/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i.test(bodyText)) {
        throw new Error("User email was not found.");
      }
      await assertVisible(page, /business plan/i);
      await assertVisible(page, /cambiar plan/i);

      finalReport.fields["Información General"] = STEP_RESULT.PASS;
    } catch (error) {
      finalReport.fields["Información General"] = STEP_RESULT.FAIL;
      finalReport.notes.push(`Información General validation failed: ${error.message}`);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await assertVisible(page, /detalles de la cuenta/i);
      await assertVisible(page, /cuenta creada/i);
      await assertVisible(page, /estado activo/i);
      await assertVisible(page, /idioma seleccionado/i);

      finalReport.fields["Detalles de la Cuenta"] = STEP_RESULT.PASS;
    } catch (error) {
      finalReport.fields["Detalles de la Cuenta"] = STEP_RESULT.FAIL;
      finalReport.notes.push(`Detalles de la Cuenta validation failed: ${error.message}`);
    }

    // Step 7: Validate Tus Negocios
    try {
      await assertVisible(page, /tus negocios/i);
      await assertVisible(page, /agregar negocio/i);
      await assertVisible(page, /tienes 2 de 3 negocios/i);

      finalReport.fields["Tus Negocios"] = STEP_RESULT.PASS;
    } catch (error) {
      finalReport.fields["Tus Negocios"] = STEP_RESULT.FAIL;
      finalReport.notes.push(`Tus Negocios validation failed: ${error.message}`);
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const appUrlBeforeClick = page.url();
      const termsEvidence = await clickLegalAndValidate({
        page,
        context,
        linkLabel: /términos y condiciones|terminos y condiciones/i,
        expectedHeading: /términos y condiciones|terminos y condiciones/i,
        screenshotLabel: "08-terminos-y-condiciones",
        appUrlBeforeClick,
      });
      finalReport.evidence.screenshots.push(termsEvidence.screenshotPath);
      finalReport.evidence.final_urls["Términos y Condiciones"] = termsEvidence.finalUrl;
      finalReport.fields["Términos y Condiciones"] = STEP_RESULT.PASS;
    } catch (error) {
      finalReport.fields["Términos y Condiciones"] = STEP_RESULT.FAIL;
      finalReport.notes.push(`Términos y Condiciones validation failed: ${error.message}`);
    }

    // Step 9: Validate Política de Privacidad
    try {
      const appUrlBeforeClick = page.url();
      const privacyEvidence = await clickLegalAndValidate({
        page,
        context,
        linkLabel: /política de privacidad|politica de privacidad/i,
        expectedHeading: /política de privacidad|politica de privacidad/i,
        screenshotLabel: "09-politica-de-privacidad",
        appUrlBeforeClick,
      });
      finalReport.evidence.screenshots.push(privacyEvidence.screenshotPath);
      finalReport.evidence.final_urls["Política de Privacidad"] = privacyEvidence.finalUrl;
      finalReport.fields["Política de Privacidad"] = STEP_RESULT.PASS;
    } catch (error) {
      finalReport.fields["Política de Privacidad"] = STEP_RESULT.FAIL;
      finalReport.notes.push(`Política de Privacidad validation failed: ${error.message}`);
    }
  } finally {
    finalReport.finished_at = new Date().toISOString();
    await fs.writeFile(REPORT_FILE, JSON.stringify(finalReport, null, 2), "utf8");
    await browser.close();
  }

  const summary = REQUIRED_REPORT_FIELDS.map((field) => ({
    field,
    result: finalReport.fields[field],
  }));

  console.log("=== SALEADS MI NEGOCIO FINAL REPORT ===");
  console.log(JSON.stringify(summary, null, 2));
  console.log(`Report file: ${REPORT_FILE}`);

  const hasFailures = Object.values(finalReport.fields).some(
    (value) => value === STEP_RESULT.FAIL
  );
  process.exitCode = hasFailures ? 1 : 0;
}

run().catch((error) => {
  console.error("Test execution failed unexpectedly:", error);
  process.exit(1);
});
