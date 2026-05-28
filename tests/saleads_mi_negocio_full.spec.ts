import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";

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

type StepStatus = "PASS" | "FAIL";

interface ReportEntry {
  status: StepStatus;
  details: string[];
}

const REPORT_FIELDS: ReportField[] = [
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

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const START_URL = process.env.SALEADS_START_URL ?? process.env.SALEADS_URL ?? "";

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function createReport(): Record<ReportField, ReportEntry> {
  return REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = { status: "FAIL", details: ["Step not executed."] };
      return acc;
    },
    {} as Record<ReportField, ReportEntry>,
  );
}

function markPass(
  report: Record<ReportField, ReportEntry>,
  field: ReportField,
  details: string[] = ["Validation completed."],
): void {
  report[field] = { status: "PASS", details };
}

function markFail(
  report: Record<ReportField, ReportEntry>,
  field: ReportField,
  error: unknown,
): void {
  const detail = error instanceof Error ? error.message : String(error);
  report[field] = { status: "FAIL", details: [detail] };
}

function markBlocked(
  report: Record<ReportField, ReportEntry>,
  field: ReportField,
  reason: string,
): void {
  report[field] = { status: "FAIL", details: [reason] };
}

async function getVisibleLocator(
  page: Page,
  candidates: Locator[],
  timeoutMs = 20_000,
): Promise<Locator> {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error("No matching visible element was found.");
}

async function clickAndWaitForUi(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await page.waitForLoadState("domcontentloaded", { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(800);
}

async function maybeSelectGoogleAccount(targetPage: Page): Promise<boolean> {
  const accountText = new RegExp(escapeRegExp(GOOGLE_ACCOUNT), "i");
  const accountOption = targetPage.getByText(accountText).first();

  if (!(await accountOption.isVisible().catch(() => false))) {
    return false;
  }

  await accountOption.click();
  await targetPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
  return true;
}

async function sectionByHeading(page: Page, headingRegex: RegExp): Promise<Locator> {
  const heading = await getVisibleLocator(page, [page.getByRole("heading", { name: headingRegex }), page.getByText(headingRegex)]);
  const section = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  await expect(section).toBeVisible();
  return section;
}

async function validateLegalDocument(
  context: BrowserContext,
  page: Page,
  linkRegex: RegExp,
  headingRegex: RegExp,
  screenshotPath: string,
): Promise<string> {
  const legalSection = await sectionByHeading(page, /Secci[oó]n Legal/i);
  const link = await getVisibleLocator(page, [
    legalSection.getByRole("link", { name: linkRegex }),
    legalSection.getByRole("button", { name: linkRegex }),
    legalSection.getByText(linkRegex),
  ]);

  const appUrlBeforeClick = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickAndWaitForUi(page, link);
  const popup = await popupPromise;
  const targetPage = popup ?? page;

  await targetPage.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => undefined);
  const headingCandidates = [
    targetPage.getByRole("heading", { name: headingRegex }),
    targetPage.getByText(headingRegex),
  ];
  const heading = await getVisibleLocator(targetPage, headingCandidates, 30_000);
  await expect(heading).toBeVisible();

  const bodyText = (await targetPage.locator("body").innerText()).trim();
  if (bodyText.length < 100) {
    throw new Error("Legal content text is too short or not visible.");
  }

  await targetPage.screenshot({ path: screenshotPath, fullPage: true });
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    if (!/Secci[oó]n Legal/i.test(await page.locator("body").innerText())) {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    }
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();

  let loginPassed = false;
  try {
    if (START_URL) {
      await page.goto(START_URL, { waitUntil: "domcontentloaded" });
    }

    const googleButton = await getVisibleLocator(page, [
      page.getByRole("button", { name: /Google|Sign in|Iniciar sesi[oó]n|Continuar/i }),
      page.getByRole("link", { name: /Google|Sign in|Iniciar sesi[oó]n|Continuar/i }),
      page.getByText(/Sign in with Google|Continuar con Google|Iniciar sesi[oó]n con Google/i),
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickAndWaitForUi(page, googleButton);
    const googlePopup = await popupPromise;

    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
      await maybeSelectGoogleAccount(googlePopup);
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 40_000 }).catch(() => undefined);
    } else {
      await maybeSelectGoogleAccount(page);
    }

    if (googlePopup && !googlePopup.isClosed()) {
      await googlePopup.close().catch(() => undefined);
    }

    await page.bringToFront();
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 90_000 });
    await expect(page.getByText(/Negocio|Dashboard|Inicio/i).first()).toBeVisible({ timeout: 90_000 });
    await page.screenshot({ path: testInfo.outputPath("01-dashboard-loaded.png"), fullPage: true });

    loginPassed = true;
    markPass(report, "Login", ["Dashboard loaded and left sidebar is visible."]);
  } catch (error) {
    markFail(report, "Login", error);
  }

  if (!loginPassed) {
    for (const field of REPORT_FIELDS) {
      if (field !== "Login" && report[field].details[0] === "Step not executed.") {
        markBlocked(report, field, "Blocked because Login step failed.");
      }
    }

    await testInfo.attach("saleads-mi-negocio-report", {
      body: JSON.stringify(report, null, 2),
      contentType: "application/json",
    });
    expect(REPORT_FIELDS.filter((field) => report[field].status === "FAIL"), JSON.stringify(report, null, 2)).toEqual([]);
    return;
  }

  try {
    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible();

    const negocioOption = await getVisibleLocator(page, [
      sidebar.getByRole("button", { name: /^Negocio$/i }),
      sidebar.getByRole("link", { name: /^Negocio$/i }),
      sidebar.getByText(/^Negocio$/i),
    ]);
    await clickAndWaitForUi(page, negocioOption);

    const miNegocioOption = await getVisibleLocator(page, [
      sidebar.getByRole("button", { name: /Mi Negocio/i }),
      sidebar.getByRole("link", { name: /Mi Negocio/i }),
      sidebar.getByText(/Mi Negocio/i),
    ]);
    await clickAndWaitForUi(page, miNegocioOption);

    await expect(sidebar.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(sidebar.getByText(/Administrar Negocios/i)).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"), fullPage: true });

    markPass(report, "Mi Negocio menu", ["Submenu expanded with Agregar Negocio and Administrar Negocios."]);
  } catch (error) {
    markFail(report, "Mi Negocio menu", error);
  }

  try {
    const agregarNegocioAction = await getVisibleLocator(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await clickAndWaitForUi(page, agregarNegocioAction);

    const modal = await getVisibleLocator(page, [
      page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
      page.locator("[role='dialog'], [aria-modal='true'], .modal").filter({ hasText: /Crear Nuevo Negocio/i }),
    ]);

    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const businessNameField = await getVisibleLocator(page, [
      modal.getByLabel(/Nombre del Negocio/i),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.locator("input").first(),
    ]);
    await expect(businessNameField).toBeVisible();

    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatizacion");
    await clickAndWaitForUi(page, modal.getByRole("button", { name: /Cancelar/i }));
    await page.screenshot({ path: testInfo.outputPath("03-agregar-negocio-modal.png"), fullPage: true });

    markPass(report, "Agregar Negocio modal", ["Modal fields and controls validated successfully."]);
  } catch (error) {
    markFail(report, "Agregar Negocio modal", error);
  }

  try {
    const administrarCandidates = [
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ];

    let administrarVisible = false;
    for (const candidate of administrarCandidates) {
      if (await candidate.first().isVisible().catch(() => false)) {
        administrarVisible = true;
        break;
      }
    }

    if (!administrarVisible) {
      const miNegocioOption = await getVisibleLocator(page, [
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ]);
      await clickAndWaitForUi(page, miNegocioOption);
    }

    const administrarNegocios = await getVisibleLocator(page, administrarCandidates);
    await clickAndWaitForUi(page, administrarNegocios);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible({ timeout: 30_000 });
    await page.screenshot({ path: testInfo.outputPath("04-administrar-negocios-page.png"), fullPage: true });

    markPass(report, "Administrar Negocios view", ["Account sections are visible."]);
  } catch (error) {
    markFail(report, "Administrar Negocios view", error);
  }

  try {
    const infoSection = await sectionByHeading(page, /Informaci[oó]n General/i);
    const infoText = await infoSection.innerText();
    const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

    if (!emailRegex.test(infoText)) {
      throw new Error("User email is not visible in Informacion General.");
    }

    const textWithoutKnownLabels = infoText
      .replace(/Informaci[oó]n General/gi, "")
      .replace(/BUSINESS PLAN/gi, "")
      .replace(/Cambiar Plan/gi, "")
      .replace(emailRegex, "")
      .trim();

    if (!/[A-Za-z]{3,}\s+[A-Za-z]{3,}/.test(textWithoutKnownLabels)) {
      throw new Error("User name is not clearly visible in Informacion General.");
    }

    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    markPass(report, "Información General", ["User name, user email, plan, and Cambiar Plan were validated."]);
  } catch (error) {
    markFail(report, "Información General", error);
  }

  try {
    const accountDetailsSection = await sectionByHeading(page, /Detalles de la Cuenta/i);
    await expect(accountDetailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(accountDetailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(accountDetailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();

    markPass(report, "Detalles de la Cuenta", ["Cuenta creada, Estado activo, and Idioma seleccionado are visible."]);
  } catch (error) {
    markFail(report, "Detalles de la Cuenta", error);
  }

  try {
    const businessSection = await sectionByHeading(page, /Tus Negocios/i);
    await expect(businessSection.locator("ul, ol, table, [role='list'], [role='table']").first()).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    markPass(report, "Tus Negocios", ["Business list, Agregar Negocio button, and quota text are visible."]);
  } catch (error) {
    markFail(report, "Tus Negocios", error);
  }

  try {
    const finalUrl = await validateLegalDocument(
      context,
      page,
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      testInfo.outputPath("05-terminos-y-condiciones.png"),
    );

    markPass(report, "Términos y Condiciones", [`Validated legal content. Final URL: ${finalUrl}`]);
  } catch (error) {
    markFail(report, "Términos y Condiciones", error);
  }

  try {
    const finalUrl = await validateLegalDocument(
      context,
      page,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      testInfo.outputPath("06-politica-de-privacidad.png"),
    );

    markPass(report, "Política de Privacidad", [`Validated legal content. Final URL: ${finalUrl}`]);
  } catch (error) {
    markFail(report, "Política de Privacidad", error);
  }

  const reportJson = JSON.stringify(report, null, 2);
  await testInfo.attach("saleads-mi-negocio-report", {
    body: reportJson,
    contentType: "application/json",
  });

  // Final report required by the workflow spec.
  console.log("SaleADS Mi Negocio final report:\n", reportJson);

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(failedFields, reportJson).toEqual([]);
});
