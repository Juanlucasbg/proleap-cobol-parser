import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_TIMEOUT_MS = Number(process.env.E2E_TIMEOUT_MS ?? 30000);
const APP_URL =
  process.env.SALEADS_LOGIN_URL ??
  process.env.SALEADS_BASE_URL ??
  process.env.SALEADS_URL;
const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const HEADLESS = process.env.HEADLESS !== "false";

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

function nowIsoSafe() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

const runTimestamp = nowIsoSafe();
const outputDir = path.join(
  process.cwd(),
  "artifacts",
  TEST_NAME,
  runTimestamp,
);

const report = {
  name: TEST_NAME,
  startedAt: new Date().toISOString(),
  environment: {
    appUrl: APP_URL ?? null,
    headless: HEADLESS,
    googleAccountHint: GOOGLE_ACCOUNT_EMAIL,
  },
  results: Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "PASS", details: [] }]),
  ),
  evidence: [],
  finalUrls: {
    termsAndConditions: null,
    privacyPolicy: null,
  },
  finishedAt: null,
};

function detail(step, message) {
  report.results[step].details.push(message);
}

function fail(step, message) {
  report.results[step].status = "FAIL";
  report.results[step].details.push(`FAIL: ${message}`);
}

function stepPassed(step) {
  return report.results[step].status === "PASS";
}

function failIfNoDetail(step, message) {
  if (!report.results[step].details.length) {
    fail(step, message);
  }
}

async function ensureDir() {
  await fs.mkdir(outputDir, { recursive: true });
}

async function screenshot(page, label, fullPage = false) {
  const fileName = `${String(report.evidence.length + 1).padStart(2, "0")}_${label}.png`;
  const filePath = path.join(outputDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  report.evidence.push({
    label,
    path: filePath,
    url: page.url(),
  });
  return filePath;
}

async function waitUi(page) {
  await page.waitForLoadState("domcontentloaded", {
    timeout: DEFAULT_TIMEOUT_MS,
  });
  await page.waitForTimeout(800);
}

async function firstVisible(candidates, timeoutMs = 5000) {
  for (const locator of candidates) {
    try {
      await locator.first().waitFor({ state: "visible", timeout: timeoutMs });
      return locator.first();
    } catch {
      // Continue trying fallbacks.
    }
  }

  return null;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitUi(page);
}

async function visibleText(root, textOrRegex) {
  const target = root.getByText(textOrRegex, { exact: false });
  return (await target.count()) > 0 && (await target.first().isVisible());
}

async function expectVisible(step, root, textOrRegex, successMessage) {
  const isVisible = await visibleText(root, textOrRegex);
  if (isVisible) {
    detail(step, `PASS: ${successMessage}`);
  } else {
    fail(step, `${successMessage} (no visible)`);
  }
}

async function sectionByHeading(page, headingRegex) {
  const heading = await firstVisible(
    [
      page.getByRole("heading", { name: headingRegex }),
      page.getByText(headingRegex),
    ],
    7000,
  );
  if (!heading) {
    return null;
  }

  return heading.locator(
    "xpath=ancestor::*[self::section or self::article or self::main or self::div][1]",
  );
}

async function loginWithGoogle(page) {
  const step = "Login";
  const genericLogin = await firstVisible(
    [
      page.getByRole("button", { name: /iniciar sesión|iniciar sesion|sign in|login|acceder/i }),
      page.getByRole("link", { name: /iniciar sesión|iniciar sesion|sign in|login|acceder/i }),
    ],
    4000,
  );

  if (genericLogin) {
    await clickAndWait(genericLogin, page);
    detail(step, "Click en botón inicial de acceso ejecutado.");
  }

  const popupPromise = page.context().waitForEvent("page", { timeout: 12000 }).catch(() => null);
  const loginButton = await firstVisible(
    [
      page.getByRole("button", { name: /iniciar sesión con google|iniciar sesion con google|continuar con google|sign in with google|google/i }),
      page.getByRole("link", { name: /iniciar sesión con google|iniciar sesion con google|continuar con google|sign in with google|google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|iniciar sesion con google|continuar con google/i),
    ],
    12000,
  );

  if (!loginButton) {
    fail(step, "No se encontró botón de login con Google.");
    await screenshot(page, "login_google_button_not_found");
    return false;
  }

  await clickAndWait(loginButton, page);
  detail(step, "Click en login con Google ejecutado.");

  let authPage = await popupPromise;
  if (!authPage) {
    authPage = page;
  }

  await waitUi(authPage);

  const accountChoice = await firstVisible(
    [
      authPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      authPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
    ],
    6000,
  );

  if (accountChoice) {
    await accountChoice.click();
    detail(step, `Cuenta de Google seleccionada: ${GOOGLE_ACCOUNT_EMAIL}`);
    try {
      await waitUi(authPage);
    } catch {
      // If popup closes automatically, this wait can fail and it's safe to continue.
    }
  } else {
    detail(step, "Selector de cuenta no visible; se continúa (posible sesión previa).");
  }

  if (authPage !== page) {
    await page.bringToFront();
  }

  await waitUi(page);

  const sidebarVisible = await firstVisible(
    [
      page.getByRole("navigation"),
      page.locator("aside"),
      page.locator('[class*="sidebar"]'),
    ],
    15000,
  );

  if (!sidebarVisible) {
    fail(step, "No se detectó barra lateral tras login.");
    await screenshot(page, "dashboard_sidebar_not_found");
    return false;
  }

  detail(step, "Interfaz principal y sidebar visibles tras login.");
  await screenshot(page, "dashboard_loaded");
  return true;
}

async function openMiNegocioMenu(page) {
  const step = "Mi Negocio menu";

  const negocioSection = await firstVisible([
    page.getByRole("button", { name: /^negocio$/i }),
    page.getByRole("link", { name: /^negocio$/i }),
    page.getByText(/^negocio$/i),
  ]);

  if (negocioSection) {
    await clickAndWait(negocioSection, page);
    detail(step, "Sección Negocio abierta.");
  } else {
    detail(step, "Sección Negocio no distinguible; intentando acceso directo a Mi Negocio.");
  }

  const miNegocio = await firstVisible([
    page.getByRole("button", { name: /mi negocio/i }),
    page.getByRole("link", { name: /mi negocio/i }),
    page.getByText(/mi negocio/i),
  ]);

  if (!miNegocio) {
    fail(step, "No se encontró opción Mi Negocio.");
    await screenshot(page, "mi_negocio_option_not_found");
    return false;
  }

  await clickAndWait(miNegocio, page);
  detail(step, "Click en Mi Negocio ejecutado.");

  await expectVisible(step, page, /agregar negocio/i, "'Agregar Negocio' visible");
  await expectVisible(step, page, /administrar negocios/i, "'Administrar Negocios' visible");

  await screenshot(page, "mi_negocio_menu_expanded");
  return stepPassed(step);
}

async function validateAgregarNegocioModal(page) {
  const step = "Agregar Negocio modal";

  const agregarNegocio = await firstVisible([
    page.getByRole("button", { name: /agregar negocio/i }),
    page.getByRole("link", { name: /agregar negocio/i }),
    page.getByText(/^agregar negocio$/i),
  ]);

  if (!agregarNegocio) {
    fail(step, "No se encontró botón/acción Agregar Negocio.");
    await screenshot(page, "agregar_negocio_entry_not_found");
    return false;
  }

  await clickAndWait(agregarNegocio, page);

  const modal = page.getByRole("dialog");
  try {
    await modal.waitFor({ state: "visible", timeout: DEFAULT_TIMEOUT_MS });
    detail(step, "Modal visible.");
  } catch {
    fail(step, "No apareció modal tras click en Agregar Negocio.");
    await screenshot(page, "agregar_negocio_modal_not_found");
    return false;
  }

  const modalScope = modal.first();
  const checks = [
    {
      text: /crear nuevo negocio/i,
      pass: "Título 'Crear Nuevo Negocio' visible",
    },
    {
      text: /nombre del negocio/i,
      pass: "Campo 'Nombre del Negocio' visible",
    },
    {
      text: /tienes\s*2\s*de\s*3\s*negocios/i,
      pass: "Texto de límite de negocios visible",
    },
    { text: /cancelar/i, pass: "Botón 'Cancelar' visible" },
    { text: /crear negocio/i, pass: "Botón 'Crear Negocio' visible" },
  ];

  for (const check of checks) {
    const item = modalScope.getByText(check.text, { exact: false });
    if ((await item.count()) > 0 && (await item.first().isVisible())) {
      detail(step, `PASS: ${check.pass}`);
    } else {
      fail(step, `${check.pass} (no visible)`);
    }
  }

  const nameField = await firstVisible(
    [
      modalScope.getByRole("textbox", { name: /nombre del negocio/i }),
      modalScope.getByLabel(/nombre del negocio/i),
      modalScope.getByPlaceholder(/nombre del negocio/i),
    ],
    4000,
  );

  if (nameField) {
    await nameField.fill("Negocio Prueba Automatización");
    detail(step, "Texto opcional cargado en Nombre del Negocio.");
  } else {
    detail(step, "Campo editable no detectado para la acción opcional.");
  }

  const cancelButton = await firstVisible(
    [
      modalScope.getByRole("button", { name: /cancelar/i }),
      modalScope.getByText(/^cancelar$/i),
    ],
    3000,
  );

  await screenshot(page, "agregar_negocio_modal");

  if (cancelButton) {
    await clickAndWait(cancelButton, page);
    detail(step, "Modal cerrado con 'Cancelar'.");
  } else {
    fail(step, "No se encontró botón Cancelar para cierre del modal.");
  }

  return stepPassed(step);
}

async function openAdministrarNegocios(page) {
  const step = "Administrar Negocios view";

  const administrar = await firstVisible(
    [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ],
    8000,
  );

  if (!administrar) {
    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);

    if (miNegocio) {
      await clickAndWait(miNegocio, page);
    }
  }

  const administrarAfterExpand = await firstVisible([
    page.getByRole("button", { name: /administrar negocios/i }),
    page.getByRole("link", { name: /administrar negocios/i }),
    page.getByText(/administrar negocios/i),
  ]);

  if (!administrarAfterExpand) {
    fail(step, "No se encontró acceso a Administrar Negocios.");
    await screenshot(page, "administrar_negocios_entry_not_found");
    return {
      ok: false,
      infoSection: null,
      detailsSection: null,
      businessesSection: null,
      legalSection: null,
    };
  }

  await clickAndWait(administrarAfterExpand, page);
  const infoSection = await sectionByHeading(page, /información general/i);
  const detailsSection = await sectionByHeading(page, /detalles de la cuenta/i);
  const businessesSection = await sectionByHeading(page, /tus negocios/i);
  const legalSection = await sectionByHeading(page, /sección legal/i);

  if (infoSection) {
    detail(step, "PASS: Sección 'Información General' visible");
  } else {
    fail(step, "Sección 'Información General' no visible");
  }

  if (detailsSection) {
    detail(step, "PASS: Sección 'Detalles de la Cuenta' visible");
  } else {
    fail(step, "Sección 'Detalles de la Cuenta' no visible");
  }

  if (businessesSection) {
    detail(step, "PASS: Sección 'Tus Negocios' visible");
  } else {
    fail(step, "Sección 'Tus Negocios' no visible");
  }

  if (legalSection) {
    detail(step, "PASS: Sección 'Sección Legal' visible");
  } else {
    fail(step, "Sección 'Sección Legal' no visible");
  }

  await screenshot(page, "administrar_negocios_page", true);
  return {
    ok: stepPassed(step),
    infoSection,
    detailsSection,
    businessesSection,
    legalSection,
  };
}

async function validateInformacionGeneral(infoSection) {
  const step = "Información General";

  if (!infoSection) {
    fail(step, "Precondición incumplida: no se cargó la sección Información General.");
    return;
  }

  const emailVisible =
    (await visibleText(infoSection, /juanlucasbarbiergarzon@gmail\.com/i)) ||
    (await visibleText(infoSection, /@[a-z0-9.-]+\.[a-z]{2,}/i));

  if (emailVisible) {
    detail(step, "PASS: Email visible.");
  } else {
    fail(step, "Email de usuario no visible.");
  }

  await expectVisible(step, infoSection, /business plan/i, "Texto 'BUSINESS PLAN' visible");
  await expectVisible(step, infoSection, /cambiar plan/i, "Botón 'Cambiar Plan' visible");

  const rawTexts = await infoSection.locator("h1,h2,h3,h4,p,span,strong").allTextContents();
  const nameCandidates = rawTexts
    .map((item) => item.trim())
    .filter(
      (item) =>
        item.length > 3 &&
        !/@/.test(item) &&
        /\s+/.test(item) &&
        !/información general|business plan|cambiar plan/i.test(item),
    );

  if (nameCandidates.length > 0) {
    detail(step, "PASS: Nombre de usuario visible.");
  } else {
    fail(step, "Nombre de usuario no visible.");
  }
}

async function validateDetallesCuenta(detailsSection) {
  const step = "Detalles de la Cuenta";

  if (!detailsSection) {
    fail(step, "Precondición incumplida: no se cargó la sección Detalles de la Cuenta.");
    return;
  }

  await expectVisible(step, detailsSection, /cuenta creada/i, "'Cuenta creada' visible");
  await expectVisible(step, detailsSection, /estado activo/i, "'Estado activo' visible");
  await expectVisible(step, detailsSection, /idioma seleccionado/i, "'Idioma seleccionado' visible");
}

async function validateTusNegocios(businessesSection) {
  const step = "Tus Negocios";

  if (!businessesSection) {
    fail(step, "Precondición incumplida: no se cargó la sección Tus Negocios.");
    return;
  }

  const listVisible = await firstVisible(
    [
      businessesSection.locator("li"),
      businessesSection.locator("[role='listitem']"),
      businessesSection.locator("tbody tr"),
      businessesSection.locator("[class*='negocio'], [class*='business']"),
    ],
    5000,
  );
  if (listVisible) {
    detail(step, "PASS: Listado de negocios visible.");
  } else {
    fail(step, "Listado de negocios no visible.");
  }

  await expectVisible(step, businessesSection, /agregar negocio/i, "Botón 'Agregar Negocio' visible");
  await expectVisible(step, businessesSection, /tienes\s*2\s*de\s*3\s*negocios/i, "Texto de cuota de negocios visible");
}

async function validateLegalLink({
  appPage,
  legalSection,
  linkRegex,
  headingRegex,
  stepName,
  screenshotLabel,
  urlKey,
}) {
  if (!legalSection) {
    fail(stepName, "Precondición incumplida: sección legal no visible en la app.");
    await screenshot(appPage, "seccion_legal_not_found");
    return;
  }

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 6000 }).catch(() => null);
  const link = await firstVisible([
    legalSection.getByRole("link", { name: linkRegex }),
    legalSection.getByRole("button", { name: linkRegex }),
    legalSection.getByText(linkRegex),
  ]);

  if (!link) {
    fail(stepName, `No se encontró enlace ${linkRegex.toString()} dentro de Sección Legal.`);
    await screenshot(appPage, `${screenshotLabel}_link_not_found`);
    return;
  }

  const previousUrl = appPage.url();
  await link.click();

  let legalPage = await popupPromise;
  if (!legalPage) {
    legalPage = appPage;
  }

  await waitUi(legalPage);

  const headingVisible = await visibleText(legalPage, headingRegex);
  if (headingVisible) {
    detail(stepName, `PASS: Encabezado ${headingRegex.toString()} visible.`);
  } else {
    fail(stepName, `Encabezado ${headingRegex.toString()} no visible.`);
  }

  const legalBodyVisible = await firstVisible(
    [
      legalPage.locator("article p"),
      legalPage.locator("main p"),
      legalPage.locator("section p"),
      legalPage.locator("p"),
    ],
    5000,
  );
  if (legalBodyVisible) {
    detail(stepName, "PASS: Texto legal visible.");
  } else {
    fail(stepName, "Texto legal no detectado.");
  }

  await screenshot(legalPage, screenshotLabel, true);
  report.finalUrls[urlKey] = legalPage.url();
  detail(stepName, `URL final: ${legalPage.url()}`);

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitUi(appPage);
    return;
  }

  if (appPage.url() !== previousUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitUi(appPage);
  }
}

async function writeReports() {
  report.finishedAt = new Date().toISOString();
  const reportPath = path.join(outputDir, "report.json");
  const markdownPath = path.join(outputDir, "report.md");

  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  const markdownLines = [
    `# ${TEST_NAME}`,
    "",
    `- Started: ${report.startedAt}`,
    `- Finished: ${report.finishedAt}`,
    `- App URL used: ${report.environment.appUrl ?? "(not provided)"}`,
    "",
    "## Final status by required field",
    "",
    "| Field | Status |",
    "| --- | --- |",
    ...REPORT_FIELDS.map((field) => `| ${field} | ${report.results[field].status} |`),
    "",
    "## Details",
    "",
    ...REPORT_FIELDS.flatMap((field) => [
      `### ${field}`,
      ...report.results[field].details.map((item) => `- ${item}`),
      "",
    ]),
    "## Evidence",
    "",
    ...report.evidence.map(
      (entry) => `- ${entry.label}: ${entry.path} (captured at ${entry.url})`,
    ),
    "",
    "## Final legal URLs",
    "",
    `- Términos y Condiciones: ${report.finalUrls.termsAndConditions ?? "N/A"}`,
    `- Política de Privacidad: ${report.finalUrls.privacyPolicy ?? "N/A"}`,
    "",
  ];

  await fs.writeFile(markdownPath, `${markdownLines.join("\n")}\n`, "utf8");
  return { reportPath, markdownPath };
}

async function run() {
  if (!APP_URL) {
    throw new Error(
      "Missing SALEADS_LOGIN_URL / SALEADS_BASE_URL / SALEADS_URL. Provide one env var for the active SaleADS environment login page.",
    );
  }

  await ensureDir();

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await context.newPage();
  page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

  try {
    await page.goto(APP_URL, { waitUntil: "domcontentloaded", timeout: DEFAULT_TIMEOUT_MS });
    await waitUi(page);
    await screenshot(page, "initial_page");

    await loginWithGoogle(page);
    await openMiNegocioMenu(page);
    await validateAgregarNegocioModal(page);
    const adminView = await openAdministrarNegocios(page);
    await validateInformacionGeneral(adminView.infoSection);
    await validateDetallesCuenta(adminView.detailsSection);
    await validateTusNegocios(adminView.businessesSection);
    await validateLegalLink({
      appPage: page,
      legalSection: adminView.legalSection,
      linkRegex: /términos y condiciones|terminos y condiciones/i,
      headingRegex: /términos y condiciones|terminos y condiciones/i,
      stepName: "Términos y Condiciones",
      screenshotLabel: "terminos_y_condiciones",
      urlKey: "termsAndConditions",
    });
    await validateLegalLink({
      appPage: page,
      legalSection: adminView.legalSection,
      linkRegex: /política de privacidad|politica de privacidad/i,
      headingRegex: /política de privacidad|politica de privacidad/i,
      stepName: "Política de Privacidad",
      screenshotLabel: "politica_de_privacidad",
      urlKey: "privacyPolicy",
    });
  } finally {
    await context.close();
    await browser.close();
  }

  const { reportPath, markdownPath } = await writeReports();
  const hasFailures = REPORT_FIELDS.some((field) => report.results[field].status === "FAIL");

  console.log("\n=== SaleADS Mi Negocio workflow report ===");
  for (const field of REPORT_FIELDS) {
    console.log(`- ${field}: ${report.results[field].status}`);
  }
  console.log(`\nJSON report: ${reportPath}`);
  console.log(`Markdown report: ${markdownPath}`);
  console.log(`Overall: ${hasFailures ? "FAIL" : "PASS"}`);

  if (hasFailures) {
    process.exitCode = 1;
  }
}

run().catch(async (error) => {
  console.error(`Fatal error: ${error.message}`);
  for (const field of REPORT_FIELDS) {
    failIfNoDetail(field, `No ejecutado por error fatal: ${error.message}`);
  }

  try {
    await ensureDir();
    const { reportPath, markdownPath } = await writeReports();
    console.error(`Fallback report generated:\n- ${reportPath}\n- ${markdownPath}`);
  } catch (writeError) {
    console.error(`No se pudo escribir reporte: ${writeError.message}`);
  }

  process.exit(1);
});
