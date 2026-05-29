import { expect, Locator, Page, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

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

type StepStatus = {
  status: "PASS" | "FAIL";
  details: string;
};

type WorkflowReport = {
  name: string;
  startedAt: string;
  finishedAt: string;
  startUrl: string | null;
  legalUrls: {
    terminosYCondiciones: string | null;
    politicaDePrivacidad: string | null;
  };
  screenshots: string[];
  steps: Record<ReportField, StepStatus>;
};

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

const DEFAULT_START_URL = process.env.SALEADS_START_URL ?? null;
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const NEGOCIO_TEST_NAME = "Negocio Prueba Automatización";

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

function buildInitialReport(): WorkflowReport {
  const steps = REPORT_FIELDS.reduce<Record<ReportField, StepStatus>>((accumulator, field) => {
    accumulator[field] = {
      status: "FAIL",
      details: "Step not executed."
    };
    return accumulator;
  }, {} as Record<ReportField, StepStatus>);

  return {
    name: "saleads_mi_negocio_full_test",
    startedAt: new Date().toISOString(),
    finishedAt: "",
    startUrl: DEFAULT_START_URL,
    legalUrls: {
      terminosYCondiciones: null,
      politicaDePrivacidad: null
    },
    screenshots: [],
    steps
  };
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function firstVisible(
  page: Page,
  candidates: Locator[],
  description: string,
  timeoutMs = 15_000
): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const target = candidate.first();
      if (await target.isVisible().catch(() => false)) {
        return target;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Could not find visible element: ${description}`);
}

async function assertVisible(
  page: Page,
  candidates: Locator[],
  description: string,
  timeoutMs = 20_000
): Promise<Locator> {
  const target = await firstVisible(page, candidates, description, timeoutMs);
  await expect(target, `${description} should be visible.`).toBeVisible();
  return target;
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => undefined);
  await locator.click();
  await waitForUi(page);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function maybeSelectGoogleAccount(targetPage: Page, email: string): Promise<boolean> {
  const emailPattern = new RegExp(escapeRegExp(email), "i");
  const accountOption = await firstVisible(
    targetPage,
    [
      targetPage.getByText(emailPattern),
      targetPage.getByRole("link", { name: emailPattern }),
      targetPage.getByRole("button", { name: emailPattern }),
      targetPage.locator(`text=${email}`)
    ],
    `Google account option "${email}"`,
    10_000
  ).catch(() => null);

  if (!accountOption) {
    return false;
  }

  await clickAndWait(targetPage, accountOption);
  return true;
}

async function takeCheckpointScreenshot(
  page: Page,
  report: WorkflowReport,
  directory: string,
  fileName: string,
  fullPage = false
): Promise<void> {
  await mkdir(directory, { recursive: true });
  const screenshotPath = path.join(directory, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  report.screenshots.push(screenshotPath);
}

async function runValidationStep(
  report: WorkflowReport,
  field: ReportField,
  fn: () => Promise<void>
): Promise<void> {
  try {
    await fn();
    report.steps[field] = {
      status: "PASS",
      details: "Validated successfully."
    };
  } catch (error) {
    report.steps[field] = {
      status: "FAIL",
      details: errorMessage(error)
    };
  }
}

async function openAndValidateLegalPage(params: {
  appPage: Page;
  labelPattern: RegExp;
  expectedHeading: RegExp;
  screenshotName: string;
  screenshotDirectory: string;
  report: WorkflowReport;
}): Promise<string> {
  const { appPage, labelPattern, expectedHeading, screenshotName, screenshotDirectory, report } = params;
  const context = appPage.context();

  const link = await assertVisible(
    appPage,
    [appPage.getByRole("link", { name: labelPattern }), appPage.getByText(labelPattern)],
    `Legal link ${labelPattern}`
  );

  const previousUrl = appPage.url();
  let legalPage: Page = appPage;

  const maybePopup = await Promise.all([
    context.waitForEvent("page", { timeout: 7_000 }).catch(() => null),
    clickAndWait(appPage, link)
  ]).then(([popup]) => popup);

  if (maybePopup) {
    legalPage = maybePopup;
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.bringToFront();
  } else {
    await waitForUi(appPage);
  }

  await assertVisible(
    legalPage,
    [legalPage.getByRole("heading", { name: expectedHeading }), legalPage.getByText(expectedHeading)],
    `Heading ${expectedHeading}`
  );

  const legalBodyText = await legalPage.locator("body").innerText();
  if (legalBodyText.trim().length < 150) {
    throw new Error("Legal content appears too short.");
  }

  await takeCheckpointScreenshot(legalPage, report, screenshotDirectory, screenshotName, true);

  const finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close().catch(() => undefined);
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== previousUrl) {
    await appPage.goBack().catch(() => undefined);
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("SaleADS Google login and Mi Negocio full workflow", async ({ page }, testInfo) => {
  const report = buildInitialReport();
  const artifactDirectory = testInfo.outputPath("artifacts");
  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");

  try {
    await runValidationStep(report, "Login", async () => {
      if (DEFAULT_START_URL) {
        await page.goto(DEFAULT_START_URL, { waitUntil: "domcontentloaded" });
      }

      await waitForUi(page);

      const loginButton = await assertVisible(
        page,
        [
          page.getByRole("button", { name: /sign in with google/i }),
          page.getByRole("button", { name: /inicia(r)? sesi[oó]n con google/i }),
          page.getByRole("button", { name: /continuar con google/i }),
          page.getByRole("button", { name: /google/i }),
          page.getByText(/sign in with google|inicia(r)? sesi[oó]n con google|continuar con google/i)
        ],
        "Google login button",
        45_000
      );

      const context = page.context();
      const popup = await Promise.all([
        context.waitForEvent("page", { timeout: 7_000 }).catch(() => null),
        clickAndWait(page, loginButton)
      ]).then(([popupPage]) => popupPage);

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await maybeSelectGoogleAccount(popup, GOOGLE_ACCOUNT_EMAIL);
        await popup.waitForEvent("close", { timeout: 20_000 }).catch(() => undefined);
      } else {
        await maybeSelectGoogleAccount(page, GOOGLE_ACCOUNT_EMAIL);
      }

      await assertVisible(
        page,
        [
          page.locator("aside"),
          page.getByRole("navigation"),
          page.locator('[class*="sidebar"]'),
          page.getByText(/mi negocio|negocio/i)
        ],
        "Main app interface with left sidebar",
        90_000
      );

      await takeCheckpointScreenshot(page, report, artifactDirectory, "01-dashboard-loaded.png", true);
    });

    await runValidationStep(report, "Mi Negocio menu", async () => {
      const sidebar = await assertVisible(
        page,
        [page.locator("aside"), page.getByRole("navigation"), page.locator('[class*="sidebar"]')],
        "Left sidebar"
      );
      await expect(sidebar).toBeVisible();

      const negocioMenu = await assertVisible(
        page,
        [
          page.getByRole("button", { name: /^negocio$/i }),
          page.getByRole("link", { name: /^negocio$/i }),
          page.getByText(/^negocio$/i)
        ],
        "Negocio section"
      );
      await clickAndWait(page, negocioMenu);

      const miNegocioOption = await assertVisible(
        page,
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i)
        ],
        "Mi Negocio option"
      );
      await clickAndWait(page, miNegocioOption);

      await assertVisible(
        page,
        [
          page.getByRole("button", { name: /agregar negocio/i }),
          page.getByRole("link", { name: /agregar negocio/i }),
          page.getByText(/agregar negocio/i)
        ],
        "Agregar Negocio submenu option"
      );

      await assertVisible(
        page,
        [
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i)
        ],
        "Administrar Negocios submenu option"
      );

      await takeCheckpointScreenshot(page, report, artifactDirectory, "02-mi-negocio-menu-expanded.png");
    });

    await runValidationStep(report, "Agregar Negocio modal", async () => {
      const agregarNegocio = await assertVisible(
        page,
        [
          page.getByRole("button", { name: /agregar negocio/i }),
          page.getByRole("link", { name: /agregar negocio/i }),
          page.getByText(/agregar negocio/i)
        ],
        "Agregar Negocio action"
      );
      await clickAndWait(page, agregarNegocio);

      const modalTitle = await assertVisible(
        page,
        [page.getByRole("heading", { name: /crear nuevo negocio/i }), page.getByText(/crear nuevo negocio/i)],
        "Crear Nuevo Negocio modal title"
      );
      await expect(modalTitle).toBeVisible();

      const negocioInput = await assertVisible(
        page,
        [page.getByLabel(/nombre del negocio/i), page.getByPlaceholder(/nombre del negocio/i)],
        "Nombre del Negocio field"
      );
      await expect(negocioInput).toBeVisible();

      await assertVisible(
        page,
        [page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)],
        "Business quota text"
      );

      await assertVisible(
        page,
        [page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)],
        "Cancelar button"
      );
      await assertVisible(
        page,
        [page.getByRole("button", { name: /crear negocio/i }), page.getByText(/crear negocio/i)],
        "Crear Negocio button"
      );

      await negocioInput.fill(NEGOCIO_TEST_NAME);
      await waitForUi(page);

      await takeCheckpointScreenshot(page, report, artifactDirectory, "03-crear-nuevo-negocio-modal.png");

      const cancelar = await assertVisible(
        page,
        [page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)],
        "Cancelar button to close modal"
      );
      await clickAndWait(page, cancelar);
    });

    await runValidationStep(report, "Administrar Negocios view", async () => {
      const miNegocio = await assertVisible(
        page,
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i)
        ],
        "Mi Negocio toggle"
      );
      await clickAndWait(page, miNegocio);

      const administrarNegocios = await assertVisible(
        page,
        [
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i)
        ],
        "Administrar Negocios option"
      );
      await clickAndWait(page, administrarNegocios);

      await assertVisible(page, [page.getByText(/informaci[oó]n general/i)], "Información General section");
      await assertVisible(page, [page.getByText(/detalles de la cuenta/i)], "Detalles de la Cuenta section");
      await assertVisible(page, [page.getByText(/tus negocios/i)], "Tus Negocios section");
      await assertVisible(
        page,
        [page.getByText(/secci[oó]n legal/i), page.getByText(/legal/i)],
        "Sección Legal section"
      );

      await takeCheckpointScreenshot(page, report, artifactDirectory, "04-administrar-negocios-page.png", true);
    });

    await runValidationStep(report, "Información General", async () => {
      const nombre = await assertVisible(
        page,
        [page.locator("section").filter({ hasText: /informaci[oó]n general/i }).locator("h4, h5, p, span").nth(1)],
        "User name in Información General"
      );
      await expect(nombre).toBeVisible();

      await assertVisible(
        page,
        [page.getByText(/@/, { exact: false }), page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)],
        "User email"
      );
      await assertVisible(page, [page.getByText(/business plan/i)], "BUSINESS PLAN text");
      await assertVisible(
        page,
        [page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)],
        "Cambiar Plan button"
      );
    });

    await runValidationStep(report, "Detalles de la Cuenta", async () => {
      await assertVisible(page, [page.getByText(/cuenta creada/i)], "Cuenta creada label");
      await assertVisible(page, [page.getByText(/estado activo/i)], "Estado activo label");
      await assertVisible(page, [page.getByText(/idioma seleccionado/i)], "Idioma seleccionado label");
    });

    await runValidationStep(report, "Tus Negocios", async () => {
      await assertVisible(page, [page.getByText(/tus negocios/i)], "Tus Negocios title");
      await assertVisible(
        page,
        [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
        "Agregar Negocio button in Tus Negocios"
      );
      await assertVisible(page, [page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)], "Tus Negocios quota text");
    });

    await runValidationStep(report, "Términos y Condiciones", async () => {
      const finalUrl = await openAndValidateLegalPage({
        appPage: page,
        labelPattern: /t[eé]rminos y condiciones/i,
        expectedHeading: /t[eé]rminos y condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png",
        screenshotDirectory: artifactDirectory,
        report
      });

      report.legalUrls.terminosYCondiciones = finalUrl;
    });

    await runValidationStep(report, "Política de Privacidad", async () => {
      const finalUrl = await openAndValidateLegalPage({
        appPage: page,
        labelPattern: /pol[ií]tica de privacidad/i,
        expectedHeading: /pol[ií]tica de privacidad/i,
        screenshotName: "06-politica-de-privacidad.png",
        screenshotDirectory: artifactDirectory,
        report
      });

      report.legalUrls.politicaDePrivacidad = finalUrl;
    });
  } finally {
    report.finishedAt = new Date().toISOString();
    await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: reportPath,
      contentType: "application/json"
    });
  }

  const failedSteps = Object.entries(report.steps)
    .filter(([, step]) => step.status === "FAIL")
    .map(([name]) => name);

  expect(
    failedSteps,
    failedSteps.length > 0 ? `Workflow validations failed: ${failedSteps.join(", ")}` : undefined
  ).toEqual([]);
});
