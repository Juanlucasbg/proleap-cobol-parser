import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type ResultStatus = "PASS" | "FAIL" | "NOT_RUN";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepResult = {
  status: ResultStatus;
  details: string[];
  evidence: string[];
  urls: string[];
};

type WorkflowReport = {
  name: string;
  generatedAt: string;
  environment: string;
  summary: {
    passed: number;
    failed: number;
    notRun: number;
  };
  results: Record<ReportField, StepResult>;
};

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_NAME = "saleads_mi_negocio_full_test";
const REPORT_FIELDS: ReportField[] = [
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

function buildReport(environmentLabel: string): WorkflowReport {
  const results = {} as Record<ReportField, StepResult>;
  for (const field of REPORT_FIELDS) {
    results[field] = { status: "NOT_RUN", details: [], evidence: [], urls: [] };
  }

  return {
    name: TEST_NAME,
    generatedAt: new Date().toISOString(),
    environment: environmentLabel,
    summary: {
      passed: 0,
      failed: 0,
      notRun: REPORT_FIELDS.length
    },
    results
  };
}

function updateSummary(report: WorkflowReport): void {
  let passed = 0;
  let failed = 0;
  let notRun = 0;

  for (const field of REPORT_FIELDS) {
    const status = report.results[field].status;
    if (status === "PASS") {
      passed += 1;
    } else if (status === "FAIL") {
      failed += 1;
    } else {
      notRun += 1;
    }
  }

  report.summary = { passed, failed, notRun };
}

function markPass(report: WorkflowReport, field: ReportField, detail: string): void {
  report.results[field].status = "PASS";
  report.results[field].details.push(detail);
  updateSummary(report);
}

function markFail(report: WorkflowReport, field: ReportField, detail: string): void {
  report.results[field].status = "FAIL";
  report.results[field].details.push(detail);
  updateSummary(report);
}

function pushEvidence(report: WorkflowReport, field: ReportField, screenshotPath: string): void {
  report.results[field].evidence.push(screenshotPath);
}

function pushUrl(report: WorkflowReport, field: ReportField, finalUrl: string): void {
  report.results[field].urls.push(finalUrl);
}

function sanitizeName(input: string): string {
  return input
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const locator = candidate.first();
    const count = await locator.count().catch(() => 0);
    if (count === 0) {
      continue;
    }
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  return null;
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => undefined);
  await page.waitForTimeout(400);
}

async function checkpoint(page: Page, directory: string, label: string, fullPage = false): Promise<string> {
  const fileName = `${Date.now()}-${sanitizeName(label)}.png`;
  const destination = path.join(directory, fileName);
  await page.screenshot({ path: destination, fullPage });
  return destination;
}

async function ensureOnLoginPageIfConfigured(page: Page): Promise<void> {
  const configuredUrl = process.env.SALEADS_LOGIN_URL;
  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => undefined);
  }
}

function environmentLabel(page: Page): string {
  try {
    const { host } = new URL(page.url());
    return host || "unknown-host";
  } catch {
    return "unknown-host";
  }
}

async function validateLegalPage(params: {
  appPage: Page;
  context: BrowserContext;
  artifactDir: string;
  report: WorkflowReport;
  field: "Términos y Condiciones" | "Política de Privacidad";
  linkText: string;
  headingText: string;
}): Promise<void> {
  const { appPage, context, artifactDir, report, field, linkText, headingText } = params;

  const legalLink = await firstVisible([
    appPage.getByRole("link", { name: new RegExp(linkText, "i") }),
    appPage.getByText(new RegExp(linkText, "i"))
  ]);

  if (!legalLink) {
    markFail(report, field, `No se encontró el enlace '${linkText}'.`);
    return;
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickAndWait(appPage, legalLink);
  const popup = await popupPromise;

  const targetPage = popup ?? appPage;
  await targetPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => undefined);
  await targetPage.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => undefined);

  const heading = await firstVisible([
    targetPage.getByRole("heading", { name: new RegExp(headingText, "i") }),
    targetPage.getByText(new RegExp(headingText, "i"))
  ]);
  const headingFound = Boolean(heading);

  const pageText = await targetPage.locator("body").innerText().catch(() => "");
  const contentFound = pageText.trim().length > 150;

  if (headingFound && contentFound) {
    markPass(report, field, "Se detectó contenido legal visible.");
  } else {
    const missingParts: string[] = [];
    if (!headingFound) {
      missingParts.push(`encabezado '${headingText}'`);
    }
    if (!contentFound) {
      missingParts.push("contenido legal visible");
    }
    markFail(report, field, `No se validó correctamente: ${missingParts.join(" y ")}.`);
  }

  pushUrl(report, field, targetPage.url());
  const screenshot = await checkpoint(targetPage, artifactDir, `${field}-page`);
  pushEvidence(report, field, screenshot);

  if (popup) {
    await popup.close().catch(() => undefined);
    await appPage.bringToFront().catch(() => undefined);
    await appPage.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => undefined);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await appPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
  }
}

async function writeFinalReport(directory: string, report: WorkflowReport): Promise<void> {
  const jsonPath = path.join(directory, "final-report.json");
  const markdownPath = path.join(directory, "final-report.md");

  const markdown = [
    `# ${report.name}`,
    "",
    `- Generated at: ${report.generatedAt}`,
    `- Environment: ${report.environment}`,
    "",
    "## Summary",
    "",
    `- Passed: ${report.summary.passed}`,
    `- Failed: ${report.summary.failed}`,
    `- Not run: ${report.summary.notRun}`,
    "",
    "## Step Results",
    "",
    "| Step | Status | Details | URLs | Evidence |",
    "| --- | --- | --- | --- | --- |",
    ...REPORT_FIELDS.map((field) => {
      const result = report.results[field];
      const details = result.details.join(" <br> ") || "-";
      const urls = result.urls.join(" <br> ") || "-";
      const evidence = result.evidence.join(" <br> ") || "-";
      return `| ${field} | ${result.status} | ${details} | ${urls} | ${evidence} |`;
    }),
    ""
  ].join("\n");

  await writeFile(jsonPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  await writeFile(markdownPath, markdown, "utf8");
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  await ensureOnLoginPageIfConfigured(page);
  const now = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactDir = path.join(process.cwd(), "artifacts", TEST_NAME, now);
  await mkdir(artifactDir, { recursive: true });

  const report = buildReport(environmentLabel(page));

  // 1) Login with Google.
  const loginButton = await firstVisible([
    page.getByRole("button", { name: /sign in with google|google|iniciar sesi[oó]n con google|iniciar con google/i }),
    page.getByRole("link", { name: /sign in with google|google|iniciar sesi[oó]n con google|iniciar con google/i }),
    page.getByText(/sign in with google|iniciar sesi[oó]n con google|iniciar con google/i)
  ]);

  if (loginButton) {
    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => undefined);
      const accountOption = await firstVisible([popup.getByText(ACCOUNT_EMAIL), popup.getByRole("button", { name: ACCOUNT_EMAIL })]);
      if (accountOption) {
        await clickAndWait(popup, accountOption);
      }
    } else {
      const accountOption = await firstVisible([page.getByText(ACCOUNT_EMAIL), page.getByRole("button", { name: ACCOUNT_EMAIL })]);
      if (accountOption) {
        await clickAndWait(page, accountOption);
      }
    }
  }

  const mainInterfaceVisible = await firstVisible([
    page.locator("main"),
    page.getByRole("navigation"),
    page.getByText(/dashboard|inicio|panel/i)
  ]);
  const sidebarVisible = await firstVisible([page.locator("aside"), page.getByRole("navigation"), page.getByText(/negocio/i)]);

  if (mainInterfaceVisible && sidebarVisible) {
    markPass(report, "Login", "La interfaz principal y la barra lateral están visibles.");
  } else {
    markFail(report, "Login", "No se pudo confirmar la carga de la interfaz principal o barra lateral.");
  }
  pushEvidence(report, "Login", await checkpoint(page, artifactDir, "dashboard-loaded"));

  // 2) Open Mi Negocio menu.
  const negocioOption = await firstVisible([
    page.getByRole("button", { name: /^negocio$/i }),
    page.getByRole("link", { name: /^negocio$/i }),
    page.getByText(/^negocio$/i)
  ]);

  if (negocioOption) {
    await clickAndWait(page, negocioOption);
  }

  const miNegocioOption = await firstVisible([
    page.getByRole("button", { name: /mi negocio/i }),
    page.getByRole("link", { name: /mi negocio/i }),
    page.getByText(/mi negocio/i)
  ]);
  if (miNegocioOption) {
    await clickAndWait(page, miNegocioOption);
  }

  const agregarSidebar = await firstVisible([
    page.getByRole("button", { name: /agregar negocio/i }),
    page.getByRole("link", { name: /agregar negocio/i }),
    page.getByText(/agregar negocio/i)
  ]);
  const administrarSidebar = await firstVisible([
    page.getByRole("button", { name: /administrar negocios/i }),
    page.getByRole("link", { name: /administrar negocios/i }),
    page.getByText(/administrar negocios/i)
  ]);

  if (agregarSidebar && administrarSidebar) {
    markPass(report, "Mi Negocio menu", "El menú se expandió y muestra Agregar/Administrar Negocios.");
  } else {
    markFail(report, "Mi Negocio menu", "No se pudieron validar todas las opciones del submenú Mi Negocio.");
  }
  pushEvidence(report, "Mi Negocio menu", await checkpoint(page, artifactDir, "mi-negocio-expanded-menu"));

  // 3) Validate Agregar Negocio modal.
  if (agregarSidebar) {
    await clickAndWait(page, agregarSidebar);
  }

  const modalTitle = await firstVisible([
    page.getByRole("heading", { name: /crear nuevo negocio/i }),
    page.getByText(/crear nuevo negocio/i)
  ]);
  const businessNameInput = await firstVisible([
    page.getByLabel(/nombre del negocio/i),
    page.getByPlaceholder(/nombre del negocio/i),
    page.locator("input[name*='negocio' i], input[placeholder*='negocio' i]")
  ]);
  const businessLimitText = await firstVisible([page.getByText(/tienes 2 de 3 negocios/i)]);
  const cancelButton = await firstVisible([page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)]);
  const createButton = await firstVisible([page.getByRole("button", { name: /crear negocio/i }), page.getByText(/crear negocio/i)]);

  if (modalTitle && businessNameInput && businessLimitText && cancelButton && createButton) {
    markPass(report, "Agregar Negocio modal", "El modal y sus elementos obligatorios se visualizaron correctamente.");
  } else {
    markFail(report, "Agregar Negocio modal", "Faltan uno o más elementos esperados en el modal Agregar Negocio.");
  }
  pushEvidence(report, "Agregar Negocio modal", await checkpoint(page, artifactDir, "agregar-negocio-modal"));

  if (businessNameInput) {
    await businessNameInput.click().catch(() => undefined);
    await businessNameInput.fill("Negocio Prueba Automatizacion").catch(() => undefined);
  }
  if (cancelButton) {
    await clickAndWait(page, cancelButton);
  }

  // 4) Open Administrar Negocios.
  const miNegocioReopen = await firstVisible([
    page.getByRole("button", { name: /mi negocio/i }),
    page.getByRole("link", { name: /mi negocio/i }),
    page.getByText(/mi negocio/i)
  ]);
  if (miNegocioReopen) {
    await clickAndWait(page, miNegocioReopen);
  }

  const administrarNegocios = await firstVisible([
    page.getByRole("button", { name: /administrar negocios/i }),
    page.getByRole("link", { name: /administrar negocios/i }),
    page.getByText(/administrar negocios/i)
  ]);
  if (administrarNegocios) {
    await clickAndWait(page, administrarNegocios);
  }

  const informacionGeneralSection = await firstVisible([
    page.getByText(/informaci[oó]n general/i),
    page.getByRole("heading", { name: /informaci[oó]n general/i })
  ]);
  const detallesCuentaSection = await firstVisible([page.getByText(/detalles de la cuenta/i), page.getByRole("heading", { name: /detalles de la cuenta/i })]);
  const tusNegociosSection = await firstVisible([page.getByText(/tus negocios/i), page.getByRole("heading", { name: /tus negocios/i })]);
  const seccionLegalSection = await firstVisible([
    page.getByText(/secci[oó]n legal/i),
    page.getByRole("heading", { name: /secci[oó]n legal/i })
  ]);

  if (informacionGeneralSection && detallesCuentaSection && tusNegociosSection && seccionLegalSection) {
    markPass(report, "Administrar Negocios view", "Las 4 secciones de la página de cuenta se encuentran visibles.");
  } else {
    markFail(report, "Administrar Negocios view", "No se detectaron todas las secciones esperadas en Administrar Negocios.");
  }
  pushEvidence(report, "Administrar Negocios view", await checkpoint(page, artifactDir, "administrar-negocios-page", true));

  // 5) Validate Información General.
  const userNameVisible = await firstVisible([page.locator("main").getByText(/\b[a-zA-Z]+ [a-zA-Z]+\b/), page.getByText(/nombre/i)]);
  const userEmailVisible = await firstVisible([page.getByText(/@/), page.getByText(/correo|email/i)]);
  const businessPlanText = await firstVisible([page.getByText(/business plan/i)]);
  const cambiarPlanButton = await firstVisible([page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)]);

  if (userNameVisible && userEmailVisible && businessPlanText && cambiarPlanButton) {
    markPass(report, "Información General", "Nombre, email, BUSINESS PLAN y Cambiar Plan están visibles.");
  } else {
    markFail(report, "Información General", "Faltan elementos esperados en Información General.");
  }

  // 6) Validate Detalles de la Cuenta.
  const cuentaCreada = await firstVisible([page.getByText(/cuenta creada/i)]);
  const estadoActivo = await firstVisible([page.getByText(/estado activo/i)]);
  const idiomaSeleccionado = await firstVisible([page.getByText(/idioma seleccionado/i)]);

  if (cuentaCreada && estadoActivo && idiomaSeleccionado) {
    markPass(report, "Detalles de la Cuenta", "Cuenta creada, Estado activo e Idioma seleccionado están visibles.");
  } else {
    markFail(report, "Detalles de la Cuenta", "No se validaron todos los campos de Detalles de la Cuenta.");
  }

  // 7) Validate Tus Negocios.
  const negociosList = await firstVisible([page.getByText(/tus negocios/i), page.locator("section").filter({ hasText: /tus negocios/i })]);
  const agregarNegocioButton = await firstVisible([page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)]);
  const negocioLimitInfo = await firstVisible([page.getByText(/tienes 2 de 3 negocios/i)]);

  if (negociosList && agregarNegocioButton && negocioLimitInfo) {
    markPass(report, "Tus Negocios", "La lista, botón Agregar Negocio y límite de negocios están visibles.");
  } else {
    markFail(report, "Tus Negocios", "No se validaron todos los elementos requeridos en Tus Negocios.");
  }

  // 8) Validate Términos y Condiciones.
  await validateLegalPage({
    appPage: page,
    context,
    artifactDir,
    report,
    field: "Términos y Condiciones",
    linkText: "Términos y Condiciones",
    headingText: "Términos y Condiciones"
  });

  // 9) Validate Política de Privacidad.
  await validateLegalPage({
    appPage: page,
    context,
    artifactDir,
    report,
    field: "Política de Privacidad",
    linkText: "Política de Privacidad",
    headingText: "Política de Privacidad"
  });

  // 10) Final report.
  updateSummary(report);
  await writeFinalReport(artifactDir, report);

  const failedFields = REPORT_FIELDS.filter((field) => report.results[field].status === "FAIL");
  expect(
    failedFields,
    `Validaciones fallidas: ${failedFields.length > 0 ? failedFields.join(", ") : "ninguna"}`
  ).toEqual([]);
});
