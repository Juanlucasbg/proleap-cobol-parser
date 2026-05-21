import { expect, Locator, Page, test, TestInfo } from "@playwright/test";
import fs from "node:fs/promises";

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const EXPECTED_USER_EMAIL =
  process.env.SALEADS_EXPECTED_USER_EMAIL || GOOGLE_ACCOUNT_EMAIL;
const EXPECTED_USER_NAME = process.env.SALEADS_EXPECTED_USER_NAME;

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
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type StepStatus = "PASS" | "FAIL";
type StepReport = {
  status: StepStatus;
  details?: string;
  evidence?: Record<string, string>;
};

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function isVisible(locator: Locator, timeout = 1_500): Promise<boolean> {
  return locator.isVisible({ timeout }).catch(() => false);
}

async function findFirstVisible(candidates: Locator[]): Promise<Locator> {
  for (const candidate of candidates) {
    const first = candidate.first();
    if (await isVisible(first)) {
      return first;
    }
  }

  throw new Error("No visible candidate element found.");
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToSettle(page);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false,
): Promise<string> {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, {
    path: screenshotPath,
    contentType: "image/png",
  });
  return screenshotPath;
}

async function runStep(
  reports: Record<ReportField, StepReport>,
  field: ReportField,
  execution: () => Promise<void>,
): Promise<void> {
  try {
    await execution();
    reports[field] = { status: "PASS" };
  } catch (error) {
    reports[field] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error),
    };
  }
}

function requireSuccessfulDependency(
  reports: Record<ReportField, StepReport>,
  dependency: ReportField,
): void {
  if (reports[dependency].status !== "PASS") {
    throw new Error(`Dependency "${dependency}" did not pass.`);
  }
}

async function selectGoogleAccountIfVisible(page: Page): Promise<void> {
  const accountOption = page.getByText(new RegExp(`^${escapeRegex(GOOGLE_ACCOUNT_EMAIL)}$`, "i"));
  if (await isVisible(accountOption.first(), 2_000)) {
    await clickAndWait(accountOption.first(), page);
  }
}

async function navigateToLoginPageWhenNeeded(page: Page, testInfo: TestInfo): Promise<void> {
  if (page.url() !== "about:blank") {
    return;
  }

  const configuredBaseUrl = (testInfo.project.use as { baseURL?: string }).baseURL;
  expect(
    configuredBaseUrl,
    "Set SALEADS_LOGIN_URL or BASE_URL when browser does not start on login page.",
  ).toBeTruthy();

  await page.goto(configuredBaseUrl as string, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);
}

async function openLegalDocument(
  page: Page,
  testInfo: TestInfo,
  linkRegex: RegExp,
  headingRegex: RegExp,
  screenshotName: string,
): Promise<string> {
  const link = await findFirstVisible([
    page.getByRole("link", { name: linkRegex }),
    page.getByText(linkRegex),
  ]);

  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickAndWait(link, page);

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await expect(popup.getByRole("heading", { name: headingRegex })).toBeVisible();
    await expect(popup.locator("body")).toContainText(/\w{20,}/);
    const popupUrl = popup.url();
    await captureCheckpoint(popup, testInfo, screenshotName);
    await popup.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
    return popupUrl;
  }

  await expect(page.getByRole("heading", { name: headingRegex })).toBeVisible();
  await expect(page.locator("body")).toContainText(/\w{20,}/);
  const pageUrl = page.url();
  await captureCheckpoint(page, testInfo, screenshotName);
  await page.goBack();
  await waitForUiToSettle(page);
  return pageUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const reports = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Step not executed." }]),
  ) as Record<ReportField, StepReport>;

  const evidence: Record<string, string> = {};

  await runStep(reports, "Login", async () => {
    await navigateToLoginPageWhenNeeded(page, testInfo);

    const sidebarCandidate = page.locator("aside, nav").first();

    if (!(await isVisible(sidebarCandidate, 3_000))) {
      const loginCta = await findFirstVisible([
        page.getByRole("button", { name: /sign in with google|continuar con google|google/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n|sign in|login/i }),
        page.getByRole("link", { name: /sign in with google|continuar con google|google/i }),
      ]);

      const popupPromise = page.context().waitForEvent("page", { timeout: 6_000 }).catch(() => null);
      await clickAndWait(loginCta, page);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await selectGoogleAccountIfVisible(popup);
        await popup.waitForEvent("close", { timeout: 60_000 }).catch(() => undefined);
        await page.bringToFront();
      } else {
        await selectGoogleAccountIfVisible(page);
      }
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 60_000 });

    evidence.dashboard = await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png");
  });

  await runStep(reports, "Mi Negocio menu", async () => {
    requireSuccessfulDependency(reports, "Login");

    const negocio = await findFirstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    await clickAndWait(negocio, page);

    const miNegocio = await findFirstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();

    evidence.miNegocioMenu = await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png");
  });

  await runStep(reports, "Agregar Negocio modal", async () => {
    requireSuccessfulDependency(reports, "Mi Negocio menu");

    const agregarNegocio = await findFirstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await clickAndWait(agregarNegocio, page);

    const modalTitle = page.getByRole("heading", { name: /^Crear Nuevo Negocio$/i });
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/^Nombre del Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Tienes 2 de 3 negocios$/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    evidence.agregarModal = await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const businessNameField = page.getByLabel(/^Nombre del Negocio$/i);
    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }), page);
    await expect(modalTitle).not.toBeVisible();
  });

  await runStep(reports, "Administrar Negocios view", async () => {
    requireSuccessfulDependency(reports, "Agregar Negocio modal");

    if (!(await isVisible(page.getByText(/^Administrar Negocios$/i).first(), 1_500))) {
      const miNegocio = await findFirstVisible([
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);
      await clickAndWait(miNegocio, page);
    }

    const administrarNegocios = await findFirstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/^Información General$/i)).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();

    evidence.administrarView = await captureCheckpoint(
      page,
      testInfo,
      "04-administrar-negocios-view.png",
      true,
    );
  });

  await runStep(reports, "Información General", async () => {
    requireSuccessfulDependency(reports, "Administrar Negocios view");

    const infoGeneralSection = page
      .locator("section,div")
      .filter({ has: page.getByText(/^Información General$/i) })
      .first();

    await expect(infoGeneralSection).toBeVisible();
    await expect(page.getByText(new RegExp(escapeRegex(EXPECTED_USER_EMAIL), "i")).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cambiar Plan$/i })).toBeVisible();

    if (EXPECTED_USER_NAME) {
      await expect(page.getByText(new RegExp(escapeRegex(EXPECTED_USER_NAME), "i")).first()).toBeVisible();
    } else {
      await expect(infoGeneralSection.locator("p,span,h1,h2,h3,h4").first()).toBeVisible();
    }
  });

  await runStep(reports, "Detalles de la Cuenta", async () => {
    requireSuccessfulDependency(reports, "Administrar Negocios view");

    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep(reports, "Tus Negocios", async () => {
    requireSuccessfulDependency(reports, "Administrar Negocios view");

    const negociosSection = page
      .locator("section,div")
      .filter({ has: page.getByText(/^Tus Negocios$/i) })
      .first();

    await expect(negociosSection).toBeVisible();
    await expect(negociosSection.locator("li, tr, [role='row']").first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(/^Tienes 2 de 3 negocios$/i)).toBeVisible();
  });

  await runStep(reports, "Términos y Condiciones", async () => {
    requireSuccessfulDependency(reports, "Administrar Negocios view");

    evidence.terminosUrl = await openLegalDocument(
      page,
      testInfo,
      /^T[eé]rminos y Condiciones$/i,
      /^T[eé]rminos y Condiciones$/i,
      "05-terminos-y-condiciones.png",
    );
  });

  await runStep(reports, "Política de Privacidad", async () => {
    requireSuccessfulDependency(reports, "Administrar Negocios view");

    evidence.politicaUrl = await openLegalDocument(
      page,
      testInfo,
      /^Pol[ií]tica de Privacidad$/i,
      /^Pol[ií]tica de Privacidad$/i,
      "06-politica-de-privacidad.png",
    );
  });

  for (const field of REPORT_FIELDS) {
    reports[field].evidence = evidence;
  }

  const reportPayload = {
    testName: "saleads_mi_negocio_full_test",
    steps: reports,
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(reportPayload, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log("Final report:", JSON.stringify(reportPayload, null, 2));

  const failedSteps = REPORT_FIELDS.filter((field) => reports[field].status === "FAIL");
  expect(
    failedSteps,
    `These workflow validations failed: ${failedSteps.join(", ")}`,
  ).toEqual([]);
});
