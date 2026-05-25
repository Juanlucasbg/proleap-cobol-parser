import { expect, test, type BrowserContext, type Locator, type Page, type TestInfo } from "@playwright/test";
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

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details: string[];
};

type WorkflowReport = {
  generatedAt: string;
  baseUrlUsed: string | null;
  appUrlAtFinish: string;
  legalUrls: {
    terminosYCondiciones: string | null;
    politicaDePrivacidad: string | null;
  };
  steps: Record<ReportField, StepResult>;
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

const GOOGLE_EMAIL = process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const SALEADS_BASE_URL = process.env.SALEADS_BASE_URL ?? process.env.BASE_URL ?? "";

function newInitialResults(): Record<ReportField, StepResult> {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        status: "FAIL",
        details: ["Step did not complete."]
      }
    ])
  ) as Record<ReportField, StepResult>;
}

function setStepResult(steps: Record<ReportField, StepResult>, field: ReportField, status: StepStatus, detail: string): void {
  steps[field] = {
    status,
    details: [detail]
  };
}

function cleanError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => undefined);
  await page.waitForTimeout(700);
}

async function waitForVisible(candidates: Locator[], page: Page, timeoutMs = 15_000): Promise<Locator> {
  const timeoutAt = Date.now() + timeoutMs;

  while (Date.now() <= timeoutAt) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      const isVisible = await locator.isVisible().catch(() => false);

      if (isVisible) {
        return locator;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error("Unable to find a visible element from the expected selectors.");
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.click();
  await waitForUiToLoad(page);
}

async function takeCheckpoint(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  const checkpointDir = path.join(testInfo.outputDir, "checkpoints");
  await mkdir(checkpointDir, { recursive: true });
  const fileName = `${name.replace(/[^a-zA-Z0-9-]+/g, "-").toLowerCase()}.png`;
  await page.screenshot({
    path: path.join(checkpointDir, fileName),
    fullPage
  });
}

function sectionFromHeading(page: Page, headingRegex: RegExp): Locator {
  return page.locator("section, article, div").filter({
    has: page.getByText(headingRegex)
  }).first();
}

function nonEmptyLines(text: string): string[] {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

async function openLegalPageAndValidate(args: {
  context: BrowserContext;
  appPage: Page;
  linkRegex: RegExp;
  headingRegex: RegExp;
  screenshotName: string;
  testInfo: TestInfo;
}): Promise<string> {
  const { context, appPage, linkRegex, headingRegex, screenshotName, testInfo } = args;
  const appUrlBeforeClick = appPage.url();
  const legalLink = await waitForVisible(
    [
      appPage.getByRole("link", { name: linkRegex }),
      appPage.getByRole("button", { name: linkRegex }),
      appPage.getByText(linkRegex)
    ],
    appPage
  );

  const popupPromise = context.waitForEvent("page", { timeout: 9_000 }).catch(() => null);
  await legalLink.click();
  await waitForUiToLoad(appPage);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 });
    await waitForUiToLoad(popup);
  }

  const heading = await waitForVisible(
    [
      targetPage.getByRole("heading", { name: headingRegex }),
      targetPage.getByText(headingRegex)
    ],
    targetPage,
    30_000
  );

  await expect(heading).toBeVisible();

  const legalContentText = await targetPage.locator("body").innerText();

  if (nonEmptyLines(legalContentText).join(" ").length < 250) {
    throw new Error("Legal content text appears too short to be valid.");
  }

  await takeCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalLegalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);
  } else if (appPage.url() !== appUrlBeforeClick) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiToLoad(appPage);
  }

  return finalLegalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const steps = newInitialResults();
  let terminosUrl: string | null = null;
  let privacidadUrl: string | null = null;

  // Step 1: Login with Google.
  try {
    if (SALEADS_BASE_URL.trim().length > 0) {
      await page.goto(SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No starting URL detected. Set SALEADS_BASE_URL for your current environment to run this test."
      );
    }

    const loginButton = await waitForVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesión con google|ingresar con google|continuar con google/i
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesión con google|ingresar con google|continuar con google/i
        }),
        page.getByText(/sign in with google|iniciar sesión con google|ingresar con google|continuar con google/i)
      ],
      page,
      30_000
    );

    const googlePopupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await loginButton.click();
    await waitForUiToLoad(page);

    const googlePopup = await googlePopupPromise;
    const authPage = googlePopup ?? page;

    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 25_000 });
      await waitForUiToLoad(googlePopup);
    }

    const accountOption = authPage.getByText(GOOGLE_EMAIL, { exact: false }).first();
    if (await accountOption.isVisible({ timeout: 10_000 }).catch(() => false)) {
      await accountOption.click();
      await waitForUiToLoad(authPage);
    }

    if (googlePopup) {
      await page.bringToFront();
      await waitForUiToLoad(page);
    }

    const sidebar = await waitForVisible(
      [
        page.locator("aside"),
        page.locator("[class*='sidebar']"),
        page.locator("nav").filter({ hasText: /negocio|mi negocio/i }),
        page.getByText(/negocio/i)
      ],
      page,
      45_000
    );

    await expect(sidebar).toBeVisible();
    await takeCheckpoint(page, testInfo, "01-dashboard-loaded", true);
    setStepResult(steps, "Login", "PASS", "Application dashboard and left navigation became visible after Google login.");
  } catch (error) {
    setStepResult(steps, "Login", "FAIL", cleanError(error));
  }

  if (steps.Login.status === "FAIL") {
    for (const field of REPORT_FIELDS) {
      if (field !== "Login") {
        setStepResult(steps, field, "FAIL", "Blocked because Login step did not complete successfully.");
      }
    }
  } else {
    // Step 2: Open Mi Negocio menu.
    try {
    const negocioSection = await waitForVisible(
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i)
      ],
      page
    );
    await clickAndWait(negocioSection, page);

    const miNegocioOption = await waitForVisible(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      page
    );
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await takeCheckpoint(page, testInfo, "02-mi-negocio-expanded", false);

    setStepResult(steps, "Mi Negocio menu", "PASS", "Mi Negocio submenu expanded and both options were visible.");
    } catch (error) {
    setStepResult(steps, "Mi Negocio menu", "FAIL", cleanError(error));
    }

    // Step 3: Validate Agregar Negocio modal.
    try {
    const agregarNegocio = await waitForVisible(
      [
        page.getByRole("menuitem", { name: /^Agregar Negocio$/i }),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      page
    );
    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal", false);

    const businessNameField = page.getByLabel(/Nombre del Negocio/i).first();
    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }), page);

    setStepResult(steps, "Agregar Negocio modal", "PASS", "Modal fields and actions were validated successfully.");
    } catch (error) {
    setStepResult(steps, "Agregar Negocio modal", "FAIL", cleanError(error));
    }

    // Step 4: Open Administrar Negocios and validate account sections.
    try {
    const administrarNegocios = await waitForVisible(
      [
        page.getByRole("menuitem", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ],
      page
    );
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();

    await takeCheckpoint(page, testInfo, "04-administrar-negocios-view", true);
    setStepResult(steps, "Administrar Negocios view", "PASS", "All required account sections were displayed.");
    } catch (error) {
    setStepResult(steps, "Administrar Negocios view", "FAIL", cleanError(error));
    }

    // Step 5: Validate Información General.
    try {
    const generalSection = sectionFromHeading(page, /Información General/i);
    await expect(generalSection).toBeVisible();

    const sectionText = await generalSection.innerText();
    const lines = nonEmptyLines(sectionText);

    const hasEmail = lines.some((line) => /^[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}$/.test(line));
    const hasBusinessPlan = lines.some((line) => /BUSINESS PLAN/i.test(line));
    const hasCambiarPlan = lines.some((line) => /Cambiar Plan/i.test(line));
    const hasLikelyName = lines.some(
      (line) =>
        /^[A-Za-zÁÉÍÓÚÑáéíóúñ][A-Za-zÁÉÍÓÚÑáéíóúñ .'-]{2,}$/.test(line) &&
        !/información general|business plan|cambiar plan|cuenta|estado|idioma|negocio/i.test(line)
    );

    expect(hasEmail).toBeTruthy();
    expect(hasBusinessPlan).toBeTruthy();
    expect(hasCambiarPlan).toBeTruthy();
    expect(hasLikelyName).toBeTruthy();

    setStepResult(steps, "Información General", "PASS", "User identity, email, plan and plan-change action were visible.");
    } catch (error) {
    setStepResult(steps, "Información General", "FAIL", cleanError(error));
    }

    // Step 6: Validate Detalles de la Cuenta.
    try {
    const detailsSection = sectionFromHeading(page, /Detalles de la Cuenta/i);
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();

    setStepResult(steps, "Detalles de la Cuenta", "PASS", "Account details section contains created date, status and language.");
    } catch (error) {
    setStepResult(steps, "Detalles de la Cuenta", "FAIL", cleanError(error));
    }

    // Step 7: Validate Tus Negocios.
    try {
    const businessSection = sectionFromHeading(page, /Tus Negocios/i);
    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const businessSectionText = nonEmptyLines(await businessSection.innerText());
    const hasBusinessContent = businessSectionText.some(
      (line) => !/tus negocios|agregar negocio|tienes 2 de 3 negocios/i.test(line)
    );
    expect(hasBusinessContent).toBeTruthy();

    setStepResult(steps, "Tus Negocios", "PASS", "Business list, add button and business-count text are visible.");
    } catch (error) {
    setStepResult(steps, "Tus Negocios", "FAIL", cleanError(error));
    }

    // Step 8: Validate Términos y Condiciones.
    try {
    terminosUrl = await openLegalPageAndValidate({
      context,
      appPage: page,
      linkRegex: /Términos y Condiciones/i,
      headingRegex: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones",
      testInfo
    });

    setStepResult(steps, "Términos y Condiciones", "PASS", `Validated legal page at URL: ${terminosUrl}`);
    } catch (error) {
    setStepResult(steps, "Términos y Condiciones", "FAIL", cleanError(error));
    }

    // Step 9: Validate Política de Privacidad.
    try {
    privacidadUrl = await openLegalPageAndValidate({
      context,
      appPage: page,
      linkRegex: /Política de Privacidad/i,
      headingRegex: /Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad",
      testInfo
    });

    setStepResult(steps, "Política de Privacidad", "PASS", `Validated legal page at URL: ${privacidadUrl}`);
    } catch (error) {
    setStepResult(steps, "Política de Privacidad", "FAIL", cleanError(error));
    }
  }

  // Step 10: Final report.
  const report: WorkflowReport = {
    generatedAt: new Date().toISOString(),
    baseUrlUsed: SALEADS_BASE_URL || null,
    appUrlAtFinish: page.url(),
    legalUrls: {
      terminosYCondiciones: terminosUrl,
      politicaDePrivacidad: privacidadUrl
    },
    steps
  };

  const reportPath = testInfo.outputPath("mi-negocio-final-report.json");
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  // Printed output is useful for CI logs.
  console.log(`\nSALEADS_MI_NEGOCIO_REPORT\n${JSON.stringify(report, null, 2)}\n`);

  const failedFields = REPORT_FIELDS.filter((field) => steps[field].status === "FAIL");
  expect(
    failedFields,
    `Validation failures detected in: ${failedFields.join(", ")}. Check the report and screenshots in Playwright artifacts.`
  ).toEqual([]);
});
