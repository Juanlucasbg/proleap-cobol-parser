import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type ReportStatus = "PASS" | "FAIL";

type ReportEntry = {
  status: ReportStatus;
  details?: string;
  url?: string;
};

type ExecutionReport = Record<ReportField, ReportEntry>;

function buildInitialReport(): ExecutionReport {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: "Step not executed" };
    return acc;
  }, {} as ExecutionReport);
}

function asErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await Promise.race([
    page.waitForLoadState("networkidle"),
    page.waitForTimeout(1_500)
  ]).catch(() => undefined);
  await page.waitForTimeout(400);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 30_000 });
  await locator.click();
  await waitForUi(page);
}

async function takeCheckpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function firstVisible(locatorCandidates: Locator[]): Promise<Locator> {
  for (const candidate of locatorCandidates) {
    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  throw new Error("No visible locator candidate found.");
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarNegocio = page.getByText(/^Agregar Negocio$/i).first();
  const administrarNegocios = page.getByText(/^Administrar Negocios$/i).first();
  const alreadyExpanded =
    (await agregarNegocio.isVisible().catch(() => false)) &&
    (await administrarNegocios.isVisible().catch(() => false));

  if (alreadyExpanded) {
    return;
  }

  const negocioSection = await firstVisible([
    page.getByText(/^Negocio$/i),
    page.getByText(/Negocio/i)
  ]);
  await clickAndWait(negocioSection, page);

  const miNegocio = await firstVisible([
    page.getByText(/^Mi Negocio$/i),
    page.getByText(/Mi Negocio/i)
  ]);
  await clickAndWait(miNegocio, page);

  await expect(agregarNegocio).toBeVisible({ timeout: 30_000 });
  await expect(administrarNegocios).toBeVisible({ timeout: 30_000 });
}

async function validateLegalLink(
  page: Page,
  testInfo: TestInfo,
  linkText: string,
  headingRegex: RegExp,
  screenshotName: string
): Promise<string> {
  const legalLink = await firstVisible([
    page.getByRole("link", { name: new RegExp(linkText, "i") }),
    page.getByText(new RegExp(linkText, "i"))
  ]);

  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await legalLink.click();
  await waitForUi(page);

  const popupPage = await popupPromise;
  const legalPage = popupPage ?? page;

  await waitForUi(legalPage);

  const legalHeading = legalPage.getByRole("heading", { name: headingRegex }).first();
  await expect(legalHeading).toBeVisible({ timeout: 30_000 });

  const legalBodyText = (await legalPage.locator("main, article, body").first().innerText()).trim();
  if (legalBodyText.length < 100) {
    throw new Error(`Legal content for "${linkText}" appears to be too short.`);
  }

  await takeCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildInitialReport();

  const runStep = async (
    field: ReportField,
    handler: () => Promise<Omit<ReportEntry, "status"> | void>
  ): Promise<void> => {
    try {
      const stepDetails = await handler();
      report[field] = { status: "PASS", ...stepDetails };
    } catch (error) {
      report[field] = { status: "FAIL", details: asErrorMessage(error) };
    }
  };

  await runStep("Login", async () => {
    const startUrl = process.env.SALEADS_START_URL ?? process.env.SALEADS_URL;
    const currentUrl = page.url();
    if (currentUrl === "about:blank") {
      if (!startUrl) {
        throw new Error(
          "No page is open. Set SALEADS_START_URL (or SALEADS_URL) to the current environment login page."
        );
      }
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);

    const googleLoginTrigger = await firstVisible([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
      page.getByRole("button", { name: /google/i }),
      page.getByText(/google/i)
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await googleLoginTrigger.click();
    await waitForUi(page);

    const authPage = (await popupPromise) ?? page;
    await waitForUi(authPage);

    const accountOption = authPage.getByText(ACCOUNT_EMAIL, { exact: false }).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await waitForUi(authPage);
    }

    await waitForUi(page);
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 90_000 });
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 90_000 });

    await takeCheckpoint(page, testInfo, "01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    await ensureMiNegocioExpanded(page);
    await takeCheckpoint(page, testInfo, "02-mi-negocio-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = page.getByText(/^Agregar Negocio$/i).first();
    await clickAndWait(agregarNegocio, page);

    const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
    await expect(modal).toBeVisible({ timeout: 30_000 });
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await takeCheckpoint(page, testInfo, "03-crear-negocio-modal.png");

    const nombreInputCandidates = [
      modal.getByRole("textbox", { name: /Nombre del Negocio/i }),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.locator("input")
    ];
    const nombreInput = await firstVisible(nombreInputCandidates);
    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    await waitForUi(page);

    await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }), page);
    await expect(modal).toBeHidden({ timeout: 30_000 });
  });

  await runStep("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);
    await clickAndWait(page.getByText(/^Administrar Negocios$/i).first(), page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 45_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 45_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 45_000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 45_000 });

    await takeCheckpoint(page, testInfo, "04-administrar-negocios-cuenta.png", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const possibleName = await firstVisible([
      page.getByText(/Nombre/i),
      page.getByText(/Usuario/i),
      page.getByText(/Profile|Perfil/i),
      page.locator("header h1, header h2, main h1, main h2")
    ]);
    await expect(possibleName).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const businessListSignals = page.locator("main li, main tr, main [role='row'], main article");
    const businessSignalsCount = await businessListSignals.count();
    if (businessSignalsCount === 0) {
      throw new Error("Business list is not visible in 'Tus Negocios'.");
    }
  });

  await runStep("Términos y Condiciones", async () => {
    const finalUrl = await validateLegalLink(
      page,
      testInfo,
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones.png"
    );

    return {
      url: finalUrl
    };
  });

  await runStep("Política de Privacidad", async () => {
    const finalUrl = await validateLegalLink(
      page,
      testInfo,
      "Política de Privacidad",
      /Pol[ií]tica de Privacidad/i,
      "06-politica-de-privacidad.png"
    );

    return {
      url: finalUrl
    };
  });

  await testInfo.attach("final-report.json", {
    contentType: "application/json",
    body: Buffer.from(JSON.stringify(report, null, 2), "utf-8")
  });
  console.log("saleads_mi_negocio_full_test final report");
  console.log(JSON.stringify(report, null, 2));

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failedFields,
    `One or more validations failed. Full report:\n${JSON.stringify(report, null, 2)}`
  ).toHaveLength(0);
});
