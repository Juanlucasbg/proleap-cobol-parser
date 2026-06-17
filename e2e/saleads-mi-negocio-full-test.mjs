import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const DEFAULT_TIMEOUT_MS = 25_000;
const POST_CLICK_WAIT_MS = 1_200;
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad"
];

const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
const evidenceDir = path.join(process.cwd(), "evidence", "saleads-mi-negocio", timestamp);
const reportJsonPath = path.join(evidenceDir, "final-report.json");
const reportMdPath = path.join(evidenceDir, "final-report.md");
const loginUrl = process.env.SALEADS_LOGIN_URL;
const headed = process.env.HEADED === "true";
const slowMo = Number.parseInt(process.env.SLOW_MO_MS || "0", 10);

const report = {
  startedAt: new Date().toISOString(),
  metadata: {
    loginUrlProvided: Boolean(loginUrl),
    headed,
    slowMoMs: Number.isNaN(slowMo) ? 0 : slowMo
  },
  evidenceDir,
  statuses: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])),
  errors: {},
  urls: {}
};

function sanitizeName(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUi(page, timeout = DEFAULT_TIMEOUT_MS) {
  await page.waitForLoadState("domcontentloaded", { timeout }).catch(() => {});
  await page.waitForTimeout(POST_CLICK_WAIT_MS);
}

async function createEvidenceDir() {
  await fs.mkdir(evidenceDir, { recursive: true });
}

async function capture(page, name, options = {}) {
  const filename = `${sanitizeName(name)}.png`;
  const fullPath = path.join(evidenceDir, filename);
  await page.screenshot({ path: fullPath, ...options });
  return fullPath;
}

async function pickVisibleLocator(scope, description, factories, timeout = DEFAULT_TIMEOUT_MS) {
  for (const factory of factories) {
    const candidate = factory(scope).first();
    try {
      await candidate.waitFor({ state: "visible", timeout });
      return candidate;
    } catch {
      // Try next candidate.
    }
  }

  throw new Error(`No visible element found for "${description}".`);
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function maybeChooseGoogleAccount(page) {
  const emailLocator = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  try {
    await emailLocator.waitFor({ state: "visible", timeout: 7_000 });
    await clickAndWait(emailLocator, page);
    return true;
  } catch {
    return false;
  }
}

function saveError(stepName, error) {
  report.errors[stepName] = error instanceof Error ? error.message : String(error);
  console.error(`[FAIL] ${stepName}: ${report.errors[stepName]}`);
}

function markPass(stepName) {
  report.statuses[stepName] = "PASS";
  console.log(`[PASS] ${stepName}`);
}

async function runStep(stepName, fn) {
  try {
    await fn();
    markPass(stepName);
  } catch (error) {
    saveError(stepName, error);
  }
}

async function assertTextVisible(scope, description, regex, timeout = DEFAULT_TIMEOUT_MS) {
  const target = scope.getByText(regex, { exact: false }).first();
  await target.waitFor({ state: "visible", timeout });
}

async function getSectionByHeading(page, headingRegex) {
  const heading = await pickVisibleLocator(
    page,
    `section heading ${headingRegex}`,
    [
      (scope) => scope.getByRole("heading", { name: headingRegex }),
      (scope) => scope.getByText(headingRegex, { exact: false })
    ],
    DEFAULT_TIMEOUT_MS
  );

  const container = heading.locator("xpath=ancestor-or-self::section[1]");
  if (await container.count()) {
    return container.first();
  }

  return heading.locator("xpath=ancestor-or-self::div[1]");
}

async function ensureMiNegocioExpanded(page) {
  const administrarOption = page.getByText(/Administrar Negocios/i, { exact: false }).first();
  if (await administrarOption.isVisible().catch(() => false)) {
    return;
  }

  const miNegocio = await pickVisibleLocator(
    page,
    "Mi Negocio toggle",
    [
      (scope) => scope.getByRole("button", { name: /Mi Negocio/i }),
      (scope) => scope.getByRole("link", { name: /Mi Negocio/i }),
      (scope) => scope.getByText(/Mi Negocio/i, { exact: false })
    ]
  );
  await clickAndWait(miNegocio, page);
}

async function validateLegalLink({
  page,
  context,
  linkRegex,
  headingRegex,
  screenshotName,
  urlKey
}) {
  const legalSection = await getSectionByHeading(page, /Secci.n Legal|Legal/i);
  const legalLink = await pickVisibleLocator(
    legalSection,
    `legal link ${linkRegex}`,
    [
      (scope) => scope.getByRole("link", { name: linkRegex }),
      (scope) => scope.getByRole("button", { name: linkRegex }),
      (scope) => scope.getByText(linkRegex, { exact: false })
    ]
  );

  const previousUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);

  await legalLink.click();
  await waitForUi(page);

  const popupPage = await popupPromise;
  const targetPage = popupPage ?? page;
  await waitForUi(targetPage, 30_000);

  await assertTextVisible(targetPage, "legal heading", headingRegex, 30_000);
  await assertTextVisible(
    targetPage,
    "legal content",
    /condiciones|privacidad|datos|informaci.n|responsabilidad|uso/i,
    30_000
  );

  await capture(targetPage, screenshotName, { fullPage: true });
  report.urls[urlKey] = targetPage.url();

  if (popupPage) {
    await popupPage.close().catch(() => {});
    await page.bringToFront().catch(() => {});
    await waitForUi(page);
    return;
  }

  if (page.url() !== previousUrl) {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 30_000 }).catch(() => {});
    await waitForUi(page);
  }
}

async function writeReports() {
  report.finishedAt = new Date().toISOString();
  await fs.writeFile(reportJsonPath, JSON.stringify(report, null, 2), "utf8");

  const lines = [];
  lines.push("# SaleADS Mi Negocio Full Test Report");
  lines.push("");
  lines.push(`- Started: ${report.startedAt}`);
  lines.push(`- Finished: ${report.finishedAt}`);
  lines.push(`- Evidence directory: ${report.evidenceDir}`);
  lines.push("");
  lines.push("| Checkpoint | Status |");
  lines.push("|---|---|");
  for (const field of REPORT_FIELDS) {
    lines.push(`| ${field} | ${report.statuses[field]} |`);
  }
  lines.push("");

  if (Object.keys(report.urls).length > 0) {
    lines.push("## Captured URLs");
    for (const [key, value] of Object.entries(report.urls)) {
      lines.push(`- ${key}: ${value}`);
    }
    lines.push("");
  }

  if (Object.keys(report.errors).length > 0) {
    lines.push("## Errors");
    for (const [key, value] of Object.entries(report.errors)) {
      lines.push(`- ${key}: ${value}`);
    }
  }

  await fs.writeFile(reportMdPath, `${lines.join("\n")}\n`, "utf8");
}

async function run() {
  await createEvidenceDir();

  const browser = await chromium.launch({
    headless: !headed,
    slowMo: Number.isNaN(slowMo) ? 0 : slowMo
  });
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 }
  });
  const page = await context.newPage();

  try {
    await runStep("Login", async () => {
      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60_000 });
      }

      await waitForUi(page);

      const loginButton = await pickVisibleLocator(
        page,
        "Google login button",
        [
          (scope) => scope.getByRole("button", { name: /Google|Sign in|Iniciar sesi.n/i }),
          (scope) => scope.getByRole("link", { name: /Google|Sign in|Iniciar sesi.n/i }),
          (scope) => scope.getByText(/Google|Sign in|Iniciar sesi.n/i, { exact: false })
        ],
        60_000
      );

      const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
      await loginButton.click();
      await waitForUi(page, 30_000);

      const popupPage = await popupPromise;
      if (popupPage) {
        await waitForUi(popupPage, 30_000);
        await maybeChooseGoogleAccount(popupPage);
        await popupPage.waitForEvent("close", { timeout: 30_000 }).catch(() => {});
      } else {
        await maybeChooseGoogleAccount(page);
      }

      await waitForUi(page, 45_000);

      await pickVisibleLocator(
        page,
        "left sidebar",
        [
          (scope) => scope.locator("aside"),
          (scope) => scope.getByRole("navigation"),
          (scope) => scope.locator("[class*='sidebar']")
        ],
        45_000
      );

      await capture(page, "01-dashboard-loaded");
    });

    await runStep("Mi Negocio menu", async () => {
      const negocioMenu = await pickVisibleLocator(page, "Negocio menu", [
        (scope) => scope.getByRole("button", { name: /^Negocio$/i }),
        (scope) => scope.getByRole("link", { name: /^Negocio$/i }),
        (scope) => scope.getByText(/^Negocio$/i, { exact: false }),
        (scope) => scope.getByText(/Negocio/i, { exact: false })
      ]);
      await clickAndWait(negocioMenu, page);

      const miNegocioOption = await pickVisibleLocator(page, "Mi Negocio option", [
        (scope) => scope.getByRole("button", { name: /Mi Negocio/i }),
        (scope) => scope.getByRole("link", { name: /Mi Negocio/i }),
        (scope) => scope.getByText(/Mi Negocio/i, { exact: false })
      ]);
      await clickAndWait(miNegocioOption, page);

      await assertTextVisible(page, "Agregar Negocio", /Agregar Negocio/i);
      await assertTextVisible(page, "Administrar Negocios", /Administrar Negocios/i);

      await capture(page, "02-mi-negocio-expanded-menu");
    });

    await runStep("Agregar Negocio modal", async () => {
      const agregarNegocio = await pickVisibleLocator(page, "Agregar Negocio action", [
        (scope) => scope.getByRole("button", { name: /Agregar Negocio/i }),
        (scope) => scope.getByRole("link", { name: /Agregar Negocio/i }),
        (scope) => scope.getByText(/Agregar Negocio/i, { exact: false })
      ]);
      await clickAndWait(agregarNegocio, page);

      const modalHeading = await pickVisibleLocator(page, "Crear Nuevo Negocio modal title", [
        (scope) => scope.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
        (scope) => scope.getByText(/Crear Nuevo Negocio/i, { exact: false })
      ]);
      const modal = modalHeading.locator("xpath=ancestor-or-self::div[@role='dialog'][1]");
      const modalScope = (await modal.count()) ? modal.first() : page;

      await assertTextVisible(modalScope, "Nombre del Negocio field", /Nombre del Negocio/i);
      await assertTextVisible(modalScope, "Business limit text", /Tienes 2 de 3 negocios/i);
      await assertTextVisible(modalScope, "Cancelar button", /Cancelar/i);
      await assertTextVisible(modalScope, "Crear Negocio button", /Crear Negocio/i);

      const nameInput = await pickVisibleLocator(
        modalScope,
        "Nombre del Negocio input",
        [
          (scope) => scope.getByLabel(/Nombre del Negocio/i),
          (scope) => scope.getByPlaceholder(/Nombre del Negocio/i),
          (scope) => scope.locator("input").filter({ hasText: "" })
        ],
        10_000
      );
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatizacion");
      await waitForUi(page);

      await capture(page, "03-agregar-negocio-modal");

      const cancelar = await pickVisibleLocator(modalScope, "Cancelar modal button", [
        (scope) => scope.getByRole("button", { name: /Cancelar/i }),
        (scope) => scope.getByText(/Cancelar/i, { exact: false })
      ]);
      await clickAndWait(cancelar, page);
    });

    await runStep("Administrar Negocios view", async () => {
      await ensureMiNegocioExpanded(page);

      const administrar = await pickVisibleLocator(page, "Administrar Negocios action", [
        (scope) => scope.getByRole("button", { name: /Administrar Negocios/i }),
        (scope) => scope.getByRole("link", { name: /Administrar Negocios/i }),
        (scope) => scope.getByText(/Administrar Negocios/i, { exact: false })
      ]);
      await clickAndWait(administrar, page);

      await assertTextVisible(page, "Informacion General section", /Informaci.n General/i, 30_000);
      await assertTextVisible(page, "Detalles de la Cuenta section", /Detalles de la Cuenta/i, 30_000);
      await assertTextVisible(page, "Tus Negocios section", /Tus Negocios/i, 30_000);
      await assertTextVisible(page, "Seccion Legal section", /Secci.n Legal|Legal/i, 30_000);

      await capture(page, "04-administrar-negocios-view", { fullPage: true });
    });

    await runStep("Informacion General", async () => {
      const infoSection = await getSectionByHeading(page, /Informaci.n General/i);
      const infoText = (await infoSection.innerText()).replace(/\s+/g, " ").trim();

      const hasUserName = infoText
        .split(/(?=[A-Z][a-z])|\n/)
        .some((entry) => /[a-zA-Z]{3,}/.test(entry) && !entry.includes("@") && !/business plan|cambiar plan|informaci.n general/i.test(entry));
      if (!hasUserName) {
        throw new Error("User name was not clearly detected inside Informacion General.");
      }

      if (!/@/.test(infoText)) {
        throw new Error("User email was not detected inside Informacion General.");
      }

      await assertTextVisible(infoSection, "BUSINESS PLAN text", /BUSINESS PLAN/i);
      await assertTextVisible(infoSection, "Cambiar Plan button", /Cambiar Plan/i);
    });

    await runStep("Detalles de la Cuenta", async () => {
      const detailsSection = await getSectionByHeading(page, /Detalles de la Cuenta/i);
      await assertTextVisible(detailsSection, "Cuenta creada text", /Cuenta creada/i);
      await assertTextVisible(detailsSection, "Estado activo text", /Estado activo/i);
      await assertTextVisible(detailsSection, "Idioma seleccionado text", /Idioma seleccionado/i);
    });

    await runStep("Tus Negocios", async () => {
      const businessesSection = await getSectionByHeading(page, /Tus Negocios/i);
      await assertTextVisible(businessesSection, "Agregar Negocio button", /Agregar Negocio/i);
      await assertTextVisible(businessesSection, "Business limit text", /Tienes 2 de 3 negocios/i);

      const businessRows = businessesSection.locator("li, article, tbody tr, [class*='business']");
      const businessRowsCount = await businessRows.count();
      const sectionText = (await businessesSection.innerText()).trim();
      if (businessRowsCount < 1 && sectionText.length < 20) {
        throw new Error("Business list appears empty or not visible.");
      }
    });

    await runStep("Terminos y Condiciones", async () => {
      await validateLegalLink({
        page,
        context,
        linkRegex: /T.rminos y Condiciones/i,
        headingRegex: /T.rminos y Condiciones/i,
        screenshotName: "05-terminos-y-condiciones",
        urlKey: "terminosYCondicionesUrl"
      });
    });

    await runStep("Politica de Privacidad", async () => {
      await validateLegalLink({
        page,
        context,
        linkRegex: /Pol.tica de Privacidad/i,
        headingRegex: /Pol.tica de Privacidad/i,
        screenshotName: "06-politica-de-privacidad",
        urlKey: "politicaDePrivacidadUrl"
      });
    });
  } finally {
    await writeReports();
    await browser.close();
  }

  const failedSteps = REPORT_FIELDS.filter((field) => report.statuses[field] !== "PASS");
  if (failedSteps.length > 0) {
    console.error(`Workflow completed with failures: ${failedSteps.join(", ")}`);
    process.exitCode = 1;
  } else {
    console.log("Workflow completed successfully with all checkpoints PASS.");
  }

  console.log(`JSON report: ${reportJsonPath}`);
  console.log(`Markdown report: ${reportMdPath}`);
}

run().catch(async (error) => {
  saveError("Unhandled", error);
  await writeReports().catch(() => {});
  console.error(error);
  process.exit(1);
});
