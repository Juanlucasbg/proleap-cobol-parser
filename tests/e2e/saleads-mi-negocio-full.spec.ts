import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import * as fs from "node:fs/promises";
import * as path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details: string[];
};

type WorkflowReport = Record<string, StepResult>;

const STEP_KEYS = {
  LOGIN: "Login",
  MENU: "Mi Negocio menu",
  MODAL: "Agregar Negocio modal",
  ADMIN_VIEW: "Administrar Negocios view",
  INFO_GENERAL: "Información General",
  ACCOUNT_DETAILS: "Detalles de la Cuenta",
  MY_BUSINESSES: "Tus Negocios",
  TERMS: "Términos y Condiciones",
  PRIVACY: "Política de Privacidad",
} as const;

const QUOTA_TEXT_REGEX = /Tienes\s+2\s+de\s+3\s+negocios/i;
const EMAIL_REGEX = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

function createEmptyReport(): WorkflowReport {
  const report: WorkflowReport = {};
  for (const key of Object.values(STEP_KEYS)) {
    report[key] = { status: "FAIL", details: [] };
  }
  return report;
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 12_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function isVisible(locator: Locator, timeout = 2_500): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

function loginButtonCandidates(page: Page): Locator[] {
  return [
    page.getByRole("button", { name: /sign in with google/i }),
    page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
    page.getByRole("button", { name: /google/i }),
    page.getByRole("link", { name: /sign in with google/i }),
    page.getByRole("link", { name: /iniciar sesi[oó]n con google/i }),
    page.getByText(/sign in with google/i),
    page.getByText(/iniciar sesi[oó]n con google/i),
  ];
}

function namedControlCandidates(page: Page, labelRegex: RegExp): Locator[] {
  return [
    page.getByRole("button", { name: labelRegex }),
    page.getByRole("link", { name: labelRegex }),
    page.getByRole("menuitem", { name: labelRegex }),
    page.getByText(labelRegex),
  ];
}

async function clickFirstVisible(candidates: Locator[], description: string, page: Page): Promise<void> {
  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      await candidate.first().click();
      await waitForUi(page);
      return;
    }
  }
  throw new Error(`No se encontró elemento visible para: ${description}`);
}

async function ensureVisible(candidates: Locator[], description: string): Promise<Locator> {
  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate.first();
    }
  }
  throw new Error(`No se encontró visible: ${description}`);
}

async function checkpoint(page: Page, testInfo: TestInfo, fileName: string, fullPage = false): Promise<void> {
  const screenDir = testInfo.outputPath("screenshots");
  await fs.mkdir(screenDir, { recursive: true });
  const screenPath = path.join(screenDir, fileName);
  await page.screenshot({ path: screenPath, fullPage });
  await testInfo.attach(fileName, { path: screenPath, contentType: "image/png" });
}

function normalizeTextLines(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}

function setPass(report: WorkflowReport, key: string, details: string[]): void {
  report[key] = { status: "PASS", details };
}

function setFail(report: WorkflowReport, key: string, err: unknown): void {
  report[key] = {
    status: "FAIL",
    details: [err instanceof Error ? err.message : String(err)],
  };
}

async function runStep(report: WorkflowReport, key: string, action: () => Promise<string[]>): Promise<void> {
  try {
    const details = await action();
    setPass(report, key, details);
  } catch (err) {
    setFail(report, key, err);
  }
}

async function openLegalDocumentAndReturn(
  page: Page,
  testInfo: TestInfo,
  labelRegex: RegExp,
  expectedHeadingRegex: RegExp,
  screenshotName: string,
): Promise<{ finalUrl: string }> {
  const appPage = page;
  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickFirstVisible(namedControlCandidates(page, labelRegex), labelRegex.source, page);
  const popup = await popupPromise;

  const legalPage = popup ?? page;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
  await legalPage.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);

  const heading = await ensureVisible(
    [
      legalPage.getByRole("heading", { name: expectedHeadingRegex }),
      legalPage.getByText(expectedHeadingRegex),
    ],
    `encabezado legal ${expectedHeadingRegex.source}`,
  );
  await expect(heading).toBeVisible();

  await expect(legalPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúñÑ]{6,}/);
  await checkpoint(legalPage, testInfo, screenshotName, true);

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close({ runBeforeUnload: true }).catch(() => undefined);
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (legalPage === appPage) {
    await appPage.goBack({ waitUntil: "domcontentloaded", timeout: 15_000 }).catch(() => undefined);
    await waitForUi(appPage);
  }

  return { finalUrl };
}

async function completeGoogleLoginFlow(page: Page): Promise<{ accountSelected: boolean }> {
  const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  await clickFirstVisible(loginButtonCandidates(page), "Login con Google", page);
  const popup = await popupPromise;

  const selectorPage = popup ?? page;
  await selectorPage.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
  await selectorPage.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);

  const accountOption = selectorPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false });
  let accountSelected = false;

  if (await isVisible(accountOption, 6_000)) {
    await accountOption.first().click();
    accountSelected = true;
    await selectorPage.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
  }

  if (popup) {
    // OAuth popup can close itself; if not, close and continue on app tab.
    await popup.close({ runBeforeUnload: true }).catch(() => undefined);
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await waitForUi(page);
  }

  return { accountSelected };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createEmptyReport();
  const configuredLoginUrl = process.env.SALEADS_LOGIN_URL;

  if (configuredLoginUrl) {
    await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  // Step 1: Login with Google
  await runStep(report, STEP_KEYS.LOGIN, async () => {
    await waitForUi(page);
    const { accountSelected } = await completeGoogleLoginFlow(page);

    const sidebar = await ensureVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
      ],
      "sidebar de navegación",
    );
    await expect(sidebar).toBeVisible();

    const appMarker = await ensureVisible(
      [
        page.getByText(/negocio/i),
        page.getByText(/dashboard/i),
        page.getByText(/mi negocio/i),
      ],
      "interfaz principal de la aplicación",
    );
    await expect(appMarker).toBeVisible();

    await checkpoint(page, testInfo, "01-dashboard-loaded.png", true);
    const details = ["Interfaz principal visible", "Sidebar izquierdo visible", "Screenshot: dashboard"];
    if (accountSelected) {
      details.push("Cuenta de Google seleccionada: juanlucasbarbiergarzon@gmail.com");
    } else {
      details.push("Selector de cuenta no apareció o sesión Google ya iniciada");
    }

    return details;
  });

  // Step 2: Open Mi Negocio menu
  await runStep(report, STEP_KEYS.MENU, async () => {
    const sidebar = await ensureVisible([page.locator("aside"), page.getByRole("navigation")], "sidebar");
    await expect(sidebar).toBeVisible();

    await clickFirstVisible(namedControlCandidates(page, /negocio/i), "Negocio", page);
    await clickFirstVisible(namedControlCandidates(page, /mi negocio/i), "Mi Negocio", page);

    const addBusinessItem = await ensureVisible(
      namedControlCandidates(page, /agregar negocio/i),
      "Agregar Negocio",
    );
    const manageBusinessesItem = await ensureVisible(
      namedControlCandidates(page, /administrar negocios/i),
      "Administrar Negocios",
    );
    await expect(addBusinessItem).toBeVisible();
    await expect(manageBusinessesItem).toBeVisible();

    await checkpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
    return ["Submenu expandido", "'Agregar Negocio' visible", "'Administrar Negocios' visible"];
  });

  // Step 3: Validate Agregar Negocio modal
  await runStep(report, STEP_KEYS.MODAL, async () => {
    await clickFirstVisible(namedControlCandidates(page, /agregar negocio/i), "Agregar Negocio", page);

    const modalTitle = await ensureVisible(
      [
        page.getByRole("heading", { name: /crear nuevo negocio/i }),
        page.getByText(/crear nuevo negocio/i),
      ],
      "Modal Crear Nuevo Negocio",
    );
    await expect(modalTitle).toBeVisible();

    const businessNameField = await ensureVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.getByRole("textbox", { name: /nombre del negocio/i }),
      ],
      "input Nombre del Negocio",
    );
    await expect(businessNameField).toBeVisible();

    await expect(page.getByText(QUOTA_TEXT_REGEX)).toBeVisible();
    await expect(await ensureVisible(namedControlCandidates(page, /cancelar/i), "Cancelar")).toBeVisible();
    await expect(await ensureVisible(namedControlCandidates(page, /crear negocio/i), "Crear Negocio")).toBeVisible();

    await checkpoint(page, testInfo, "03-agregar-negocio-modal.png");

    await businessNameField.fill("Negocio Prueba Automatización");
    await waitForUi(page);
    await clickFirstVisible(namedControlCandidates(page, /cancelar/i), "Cancelar", page);

    return [
      "Título del modal visible",
      "Campo 'Nombre del Negocio' visible",
      "Texto de cuota visible",
      "Botones 'Cancelar' y 'Crear Negocio' visibles",
      "Modal cerrado con 'Cancelar'",
    ];
  });

  // Step 4: Open Administrar Negocios
  await runStep(report, STEP_KEYS.ADMIN_VIEW, async () => {
    if (!(await isVisible(page.getByText(/administrar negocios/i)))) {
      await clickFirstVisible(namedControlCandidates(page, /mi negocio/i), "Mi Negocio", page);
    }
    await clickFirstVisible(namedControlCandidates(page, /administrar negocios/i), "Administrar Negocios", page);

    const infoGeneral = await ensureVisible(
      [page.getByRole("heading", { name: /información general/i }), page.getByText(/información general/i)],
      "Información General",
    );
    const accountDetails = await ensureVisible(
      [page.getByRole("heading", { name: /detalles de la cuenta/i }), page.getByText(/detalles de la cuenta/i)],
      "Detalles de la Cuenta",
    );
    const myBusinesses = await ensureVisible(
      [page.getByRole("heading", { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      "Tus Negocios",
    );
    const legalSection = await ensureVisible(
      [page.getByRole("heading", { name: /sección legal/i }), page.getByText(/sección legal/i)],
      "Sección Legal",
    );

    await expect(infoGeneral).toBeVisible();
    await expect(accountDetails).toBeVisible();
    await expect(myBusinesses).toBeVisible();
    await expect(legalSection).toBeVisible();

    await checkpoint(page, testInfo, "04-administrar-negocios-page-full.png", true);
    return [
      "'Información General' visible",
      "'Detalles de la Cuenta' visible",
      "'Tus Negocios' visible",
      "'Sección Legal' visible",
    ];
  });

  // Step 5: Validate Información General
  await runStep(report, STEP_KEYS.INFO_GENERAL, async () => {
    const infoSection = await ensureVisible(
      [
        page
          .getByRole("heading", { name: /información general/i })
          .locator("xpath=ancestor::*[self::section or self::div][1]"),
        page.getByText(/información general/i).locator("xpath=ancestor::*[self::section or self::div][1]"),
      ],
      "contenedor de Información General",
    );

    const infoText = await infoSection.innerText();
    const lines = normalizeTextLines(infoText);

    const emailLine = lines.find((line) => EMAIL_REGEX.test(line));
    if (!emailLine) {
      throw new Error("No se encontró email visible en 'Información General'.");
    }

    const likelyNameLine = lines.find(
      (line) =>
        !EMAIL_REGEX.test(line) &&
        !/información general|business plan|cambiar plan/i.test(line) &&
        /[A-Za-zÁÉÍÓÚáéíóúñÑ]{3,}/.test(line),
    );
    if (!likelyNameLine) {
      throw new Error("No se detectó nombre de usuario visible en 'Información General'.");
    }

    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(await ensureVisible(namedControlCandidates(page, /cambiar plan/i), "Cambiar Plan")).toBeVisible();

    return ["Nombre de usuario visible", "Email visible", "BUSINESS PLAN visible", "'Cambiar Plan' visible"];
  });

  // Step 6: Validate Detalles de la Cuenta
  await runStep(report, STEP_KEYS.ACCOUNT_DETAILS, async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();

    return ["'Cuenta creada' visible", "'Estado activo' visible", "'Idioma seleccionado' visible"];
  });

  // Step 7: Validate Tus Negocios
  await runStep(report, STEP_KEYS.MY_BUSINESSES, async () => {
    const businessesSection = await ensureVisible(
      [
        page.getByRole("heading", { name: /tus negocios/i }).locator("xpath=ancestor::*[self::section or self::div][1]"),
        page.getByText(/tus negocios/i).locator("xpath=ancestor::*[self::section or self::div][1]"),
      ],
      "contenedor Tus Negocios",
    );

    await expect(await ensureVisible(namedControlCandidates(page, /agregar negocio/i), "Agregar Negocio en sección")).toBeVisible();
    await expect(page.getByText(QUOTA_TEXT_REGEX)).toBeVisible();

    const sectionText = normalizeTextLines(await businessesSection.innerText()).join("\n");
    const hasBusinessListEvidence =
      /negocio/i.test(sectionText) &&
      !/no tienes negocios/i.test(sectionText);

    if (!hasBusinessListEvidence) {
      throw new Error("No se detectó lista de negocios visible.");
    }

    return ["Lista de negocios visible", "Botón 'Agregar Negocio' visible", "Texto de cuota visible"];
  });

  // Step 8: Validate Términos y Condiciones
  await runStep(report, STEP_KEYS.TERMS, async () => {
    const result = await openLegalDocumentAndReturn(
      page,
      testInfo,
      /términos y condiciones|terminos y condiciones/i,
      /términos y condiciones|terminos y condiciones/i,
      "08-terminos-y-condiciones.png",
    );

    return [
      "Encabezado de Términos y Condiciones visible",
      "Contenido legal visible",
      `URL final: ${result.finalUrl}`,
    ];
  });

  // Step 9: Validate Política de Privacidad
  await runStep(report, STEP_KEYS.PRIVACY, async () => {
    const result = await openLegalDocumentAndReturn(
      page,
      testInfo,
      /política de privacidad|politica de privacidad/i,
      /política de privacidad|politica de privacidad/i,
      "09-politica-de-privacidad.png",
    );

    return [
      "Encabezado de Política de Privacidad visible",
      "Contenido legal visible",
      `URL final: ${result.finalUrl}`,
    ];
  });

  // Step 10: Final report (PASS/FAIL per requested field)
  const summaryLines = Object.entries(report).map(([field, result]) => {
    const detail = result.details.length ? ` - ${result.details.join(" | ")}` : "";
    return `${field}: ${result.status}${detail}`;
  });

  const reportJsonPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  const reportTxtPath = testInfo.outputPath("saleads-mi-negocio-report.txt");
  await fs.writeFile(reportJsonPath, JSON.stringify(report, null, 2), "utf8");
  await fs.writeFile(reportTxtPath, summaryLines.join("\n"), "utf8");

  await testInfo.attach("saleads-mi-negocio-report.json", {
    path: reportJsonPath,
    contentType: "application/json",
  });
  await testInfo.attach("saleads-mi-negocio-report.txt", {
    path: reportTxtPath,
    contentType: "text/plain",
  });

  const failedSteps = Object.entries(report).filter(([, result]) => result.status === "FAIL");
  expect(
    failedSteps,
    `Se encontraron validaciones fallidas:\n${summaryLines.join("\n")}`,
  ).toHaveLength(0);
});
