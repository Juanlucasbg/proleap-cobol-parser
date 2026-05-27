import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const TARGET_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const runId = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.join(__dirname, "artifacts", runId);
const screenshotDir = path.join(artifactsDir, "screenshots");

await mkdir(screenshotDir, { recursive: true });

const report = {
  name: "saleads_mi_negocio_full_test",
  startedAt: new Date().toISOString(),
  loginUrl: process.env.SALEADS_LOGIN_URL ?? null,
  screenshots: {},
  legalUrls: {},
  results: Object.fromEntries(
    REQUIRED_REPORT_FIELDS.map((field) => [
      field,
      {
        status: "FAIL",
        details: "Not executed."
      }
    ])
  )
};

function setResult(field, status, details) {
  report.results[field] = {
    status,
    details
  };
}

function failRemainingFrom(stepName, reason) {
  const stepIndex = REQUIRED_REPORT_FIELDS.indexOf(stepName);
  for (let i = stepIndex; i < REQUIRED_REPORT_FIELDS.length; i += 1) {
    const field = REQUIRED_REPORT_FIELDS[i];
    if (report.results[field]?.status !== "PASS") {
      setResult(field, "FAIL", `Prerequisite failed: ${reason}`);
    }
  }
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function screenshot(page, key, options = {}) {
  const fileName = `${key}.png`;
  const outputPath = path.join(screenshotDir, fileName);
  await page.screenshot({
    path: outputPath,
    fullPage: options.fullPage ?? false
  });
  report.screenshots[key] = outputPath;
}

async function firstVisible(locators, timeout = 8000) {
  for (const locator of locators) {
    const candidate = locator.first();
    try {
      await candidate.waitFor({ state: "visible", timeout });
      return candidate;
    } catch {
      // Try next locator candidate.
    }
  }
  return null;
}

async function isVisible(locator, timeout = 8000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickVisibleAndWait(pageForLoad, locators, timeout = 8000) {
  const locator = await firstVisible(locators, timeout);
  if (!locator) {
    return false;
  }

  await locator.click();
  await waitForUi(pageForLoad);
  return true;
}

async function validateLegalContent(page) {
  const body = page.locator("body");
  const text = (await body.innerText().catch(() => "")).replace(/\s+/g, " ").trim();
  if (text.length < 150) {
    return false;
  }
  return /(t[ée]rminos|condiciones|privacidad|datos personales|legal)/i.test(text);
}

async function openAndValidateLegalPage({
  page,
  context,
  linkLabelRegex,
  headingRegex,
  reportField,
  screenshotKey
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  const clicked = await clickVisibleAndWait(
    page,
    [
      page.getByRole("link", { name: linkLabelRegex }),
      page.getByRole("button", { name: linkLabelRegex }),
      page.getByText(linkLabelRegex)
    ],
    12000
  );

  if (!clicked) {
    setResult(reportField, "FAIL", `Could not locate "${linkLabelRegex}" in legal section.`);
    return false;
  }

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await waitForUi(legalPage);

  const headingVisible = await firstVisible(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex)
    ],
    12000
  );

  const contentVisible = await validateLegalContent(legalPage);
  const finalUrl = legalPage.url();
  report.legalUrls[reportField] = finalUrl;

  if (headingVisible && contentVisible) {
    await screenshot(legalPage, screenshotKey, { fullPage: true });
    setResult(reportField, "PASS", `Validated legal document at ${finalUrl}`);
  } else {
    const missing = [
      headingVisible ? null : "heading not found",
      contentVisible ? null : "legal content not detected"
    ]
      .filter(Boolean)
      .join("; ");
    setResult(reportField, "FAIL", `Legal page validation failed: ${missing || "unknown reason"}. URL: ${finalUrl}`);
  }

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront().catch(() => {});
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return report.results[reportField].status === "PASS";
}

let browser;
try {
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (!loginUrl) {
    setResult("Login", "FAIL", "SALEADS_LOGIN_URL is required and must point to the active environment login page.");
    failRemainingFrom("Mi Negocio menu", "Login");
    throw new Error("Missing SALEADS_LOGIN_URL.");
  }

  browser = await chromium.launch({
    headless: process.env.HEADLESS !== "false"
  });
  const context = await browser.newContext({
    viewport: { width: 1536, height: 960 }
  });
  const page = await context.newPage();

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  // Step 1: Login with Google
  const loginPopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  const loginClicked = await clickVisibleAndWait(
    page,
    [
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
    ],
    12000
  );

  if (!loginClicked) {
    setResult("Login", "FAIL", "Google login button was not found.");
    failRemainingFrom("Mi Negocio menu", "Login");
    throw new Error("Unable to start Google login.");
  }

  const loginPopup = await loginPopupPromise;
  const authPage = loginPopup ?? page;
  await waitForUi(authPage);

  await clickVisibleAndWait(
    authPage,
    [
      authPage.getByRole("button", { name: new RegExp(TARGET_ACCOUNT_EMAIL, "i") }),
      authPage.getByRole("link", { name: new RegExp(TARGET_ACCOUNT_EMAIL, "i") }),
      authPage.getByText(TARGET_ACCOUNT_EMAIL)
    ],
    9000
  );

  await waitForUi(page);
  const sidebarVisible = await firstVisible(
    [
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/mi negocio|negocio/i)
    ],
    30000
  );

  if (!sidebarVisible) {
    setResult("Login", "FAIL", "Main application sidebar did not become visible after Google login.");
    failRemainingFrom("Mi Negocio menu", "Login");
    throw new Error("Login did not reach main application.");
  }

  setResult("Login", "PASS", "Main interface loaded and left sidebar is visible.");
  await screenshot(page, "01-dashboard-loaded");

  // Step 2: Open Mi Negocio menu
  const miNegocioOpened = await clickVisibleAndWait(
    page,
    [
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
      page.getByText(/negocio/i)
    ],
    12000
  );

  if (!miNegocioOpened) {
    setResult("Mi Negocio menu", "FAIL", 'Could not click "Mi Negocio" in sidebar.');
    failRemainingFrom("Agregar Negocio modal", "Mi Negocio menu");
    throw new Error("Mi Negocio menu not accessible.");
  }

  const addBusinessVisible = await isVisible(page.getByText(/agregar negocio/i), 12000);
  const manageBusinessVisible = await isVisible(page.getByText(/administrar negocios/i), 12000);
  if (!addBusinessVisible || !manageBusinessVisible) {
    setResult(
      "Mi Negocio menu",
      "FAIL",
      `Menu did not expand correctly. Agregar Negocio: ${addBusinessVisible}, Administrar Negocios: ${manageBusinessVisible}`
    );
    failRemainingFrom("Agregar Negocio modal", "Mi Negocio menu");
    throw new Error("Mi Negocio submenu validation failed.");
  }

  setResult("Mi Negocio menu", "PASS", "Mi Negocio expanded with Agregar Negocio and Administrar Negocios visible.");
  await screenshot(page, "02-mi-negocio-menu-expanded");

  // Step 3: Validate Agregar Negocio modal
  const addBusinessClicked = await clickVisibleAndWait(
    page,
    [
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ],
    12000
  );

  if (!addBusinessClicked) {
    setResult("Agregar Negocio modal", "FAIL", 'Could not click "Agregar Negocio".');
    failRemainingFrom("Administrar Negocios view", "Agregar Negocio modal");
    throw new Error("Agregar Negocio action failed.");
  }

  const createBusinessTitleVisible = await isVisible(page.getByText(/crear nuevo negocio/i), 10000);
  const businessNameFieldVisible = await isVisible(
    page.getByRole("textbox", { name: /nombre del negocio/i }),
    8000
  );
  const quotaTextVisible = await isVisible(page.getByText(/tienes 2 de 3 negocios/i), 8000);
  const cancelButtonVisible = await isVisible(page.getByRole("button", { name: /cancelar/i }), 8000);
  const createButtonVisible = await isVisible(page.getByRole("button", { name: /crear negocio/i }), 8000);

  if (
    !createBusinessTitleVisible ||
    !businessNameFieldVisible ||
    !quotaTextVisible ||
    !cancelButtonVisible ||
    !createButtonVisible
  ) {
    setResult(
      "Agregar Negocio modal",
      "FAIL",
      `Modal validation failed. title=${createBusinessTitleVisible}, input=${businessNameFieldVisible}, quota=${quotaTextVisible}, cancel=${cancelButtonVisible}, create=${createButtonVisible}`
    );
    failRemainingFrom("Administrar Negocios view", "Agregar Negocio modal");
    throw new Error("Agregar Negocio modal validation failed.");
  }

  await screenshot(page, "03-agregar-negocio-modal");
  await clickVisibleAndWait(
    page,
    [page.getByRole("textbox", { name: /nombre del negocio/i })],
    5000
  );
  await page.getByRole("textbox", { name: /nombre del negocio/i }).fill("Negocio Prueba Automatización");
  await clickVisibleAndWait(
    page,
    [page.getByRole("button", { name: /cancelar/i }), page.getByText(/cancelar/i)],
    8000
  );

  setResult("Agregar Negocio modal", "PASS", "Modal fields and controls validated successfully.");

  // Step 4: Open Administrar Negocios
  const adminVisibleBeforeClick = await isVisible(page.getByText(/administrar negocios/i), 3000);
  if (!adminVisibleBeforeClick) {
    await clickVisibleAndWait(
      page,
      [
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      8000
    );
  }

  const adminClicked = await clickVisibleAndWait(
    page,
    [
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ],
    12000
  );

  if (!adminClicked) {
    setResult("Administrar Negocios view", "FAIL", 'Could not click "Administrar Negocios".');
    failRemainingFrom("Información General", "Administrar Negocios view");
    throw new Error("Administrar Negocios navigation failed.");
  }

  const generalInfoVisible = await isVisible(page.getByText(/informaci[oó]n general/i), 12000);
  const accountDetailsVisible = await isVisible(page.getByText(/detalles de la cuenta/i), 12000);
  const businessesVisible = await isVisible(page.getByText(/tus negocios/i), 12000);
  const legalSectionVisible = await isVisible(page.getByText(/secci[oó]n legal/i), 12000);
  if (!generalInfoVisible || !accountDetailsVisible || !businessesVisible || !legalSectionVisible) {
    setResult(
      "Administrar Negocios view",
      "FAIL",
      `Page sections missing. general=${generalInfoVisible}, details=${accountDetailsVisible}, businesses=${businessesVisible}, legal=${legalSectionVisible}`
    );
    failRemainingFrom("Información General", "Administrar Negocios view");
    throw new Error("Account page sections not fully visible.");
  }

  await screenshot(page, "04-administrar-negocios-page", { fullPage: true });
  setResult("Administrar Negocios view", "PASS", "Administrar Negocios page loaded with all required sections.");

  // Step 5: Validate Información General
  const userNameVisible = await isVisible(page.locator("section").getByText(/@|[a-z]{2,}\s+[a-z]{2,}/i), 5000);
  const userEmailVisible = await isVisible(page.getByText(/@/), 8000);
  const businessPlanVisible = await isVisible(page.getByText(/business plan/i), 8000);
  const changePlanVisible = await isVisible(
    page.getByRole("button", { name: /cambiar plan/i }),
    8000
  );
  if (userNameVisible && userEmailVisible && businessPlanVisible && changePlanVisible) {
    setResult("Información General", "PASS", "Información General shows user profile, plan and change plan action.");
  } else {
    setResult(
      "Información General",
      "FAIL",
      `Información General missing fields. userName=${userNameVisible}, email=${userEmailVisible}, plan=${businessPlanVisible}, cambiarPlan=${changePlanVisible}`
    );
  }

  // Step 6: Validate Detalles de la Cuenta
  const accountCreatedVisible = await isVisible(page.getByText(/cuenta creada/i), 8000);
  const activeStateVisible = await isVisible(page.getByText(/estado activo/i), 8000);
  const languageSelectedVisible = await isVisible(page.getByText(/idioma seleccionado/i), 8000);
  if (accountCreatedVisible && activeStateVisible && languageSelectedVisible) {
    setResult("Detalles de la Cuenta", "PASS", "Detalles de la Cuenta fields are visible.");
  } else {
    setResult(
      "Detalles de la Cuenta",
      "FAIL",
      `Detalles de la Cuenta missing fields. cuentaCreada=${accountCreatedVisible}, estadoActivo=${activeStateVisible}, idiomaSeleccionado=${languageSelectedVisible}`
    );
  }

  // Step 7: Validate Tus Negocios
  const businessListVisible = await isVisible(page.getByText(/tus negocios/i), 8000);
  const addBusinessButtonVisible = await isVisible(
    page.getByRole("button", { name: /agregar negocio/i }),
    8000
  );
  const quotaVisibleInSection = await isVisible(page.getByText(/tienes 2 de 3 negocios/i), 8000);
  if (businessListVisible && addBusinessButtonVisible && quotaVisibleInSection) {
    setResult("Tus Negocios", "PASS", "Business list, add button and quota text are visible.");
  } else {
    setResult(
      "Tus Negocios",
      "FAIL",
      `Tus Negocios validation failed. list=${businessListVisible}, button=${addBusinessButtonVisible}, quota=${quotaVisibleInSection}`
    );
  }

  // Step 8: Validate Términos y Condiciones
  await openAndValidateLegalPage({
    page,
    context,
    linkLabelRegex: /t[ée]rminos y condiciones/i,
    headingRegex: /t[ée]rminos y condiciones/i,
    reportField: "Términos y Condiciones",
    screenshotKey: "05-terminos-y-condiciones"
  });

  // Step 9: Validate Política de Privacidad
  await openAndValidateLegalPage({
    page,
    context,
    linkLabelRegex: /pol[íi]tica de privacidad/i,
    headingRegex: /pol[íi]tica de privacidad/i,
    reportField: "Política de Privacidad",
    screenshotKey: "06-politica-de-privacidad"
  });

  await context.close();
} catch (error) {
  report.error = error instanceof Error ? error.message : String(error);
} finally {
  if (browser) {
    await browser.close().catch(() => {});
  }

  report.finishedAt = new Date().toISOString();
  report.summary = Object.fromEntries(
    Object.entries(report.results).map(([step, value]) => [step, value.status])
  );

  const reportPath = path.join(artifactsDir, "final-report.json");
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(`Final report written to ${reportPath}`);
  console.log(JSON.stringify(report.summary, null, 2));

  const hasFailures = Object.values(report.results).some((result) => result.status !== "PASS");
  if (hasFailures) {
    process.exitCode = 1;
  }
}
