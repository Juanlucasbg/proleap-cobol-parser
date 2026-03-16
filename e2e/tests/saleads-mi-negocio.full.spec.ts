import { test, expect, type BrowserContext, type Locator, type Page } from "@playwright/test";

type ReportStatus = "PASS" | "FAIL";
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function escapeRegex(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function regexFromText(text: string): RegExp {
  return new RegExp(escapeRegex(text), "i");
}

function createEmptyReport(): Record<ReportField, ReportStatus> {
  return REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = "FAIL";
      return acc;
    },
    {} as Record<ReportField, ReportStatus>
  );
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForTimeout(700);
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 4000 }).catch(() => undefined);
}

async function findVisible(candidates: Locator[], description: string, timeoutMs = 20_000): Promise<Locator> {
  const endAt = Date.now() + timeoutMs;

  while (Date.now() < endAt) {
    for (const candidate of candidates) {
      const first = candidate.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(`Element not visible: ${description}`);
}

function textCandidates(scope: Page | Locator, text: string): Locator[] {
  const matcher = regexFromText(text);
  return [
    scope.getByRole("button", { name: matcher }),
    scope.getByRole("link", { name: matcher }),
    scope.getByRole("menuitem", { name: matcher }),
    scope.getByRole("tab", { name: matcher }),
    scope.getByText(matcher)
  ];
}

async function findByVisibleText(scope: Page | Locator, labels: string[], description: string, timeoutMs = 20_000): Promise<Locator> {
  const candidates = labels.flatMap((label) => textCandidates(scope, label));
  return findVisible(candidates, description, timeoutMs);
}

async function clickAndWait(locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToSettle(locator.page());
}

async function clickAndCapturePopup(page: Page, context: BrowserContext, clickable: Locator): Promise<Page | null> {
  const popupFromPagePromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
  const popupFromContextPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickAndWait(clickable);

  let popup = await popupFromPagePromise;
  if (!popup) {
    popup = await popupFromContextPromise;
  }

  if (popup) {
    await popup.waitForLoadState("domcontentloaded").catch(() => undefined);
    await popup.waitForLoadState("networkidle", { timeout: 5_000 }).catch(() => undefined);
  }

  return popup;
}

async function trySelectGoogleAccount(target: Page): Promise<void> {
  const accountSelectors = [
    target.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
    target.getByRole("link", { name: regexFromText(GOOGLE_ACCOUNT_EMAIL) }),
    target.getByRole("button", { name: regexFromText(GOOGLE_ACCOUNT_EMAIL) })
  ];

  const accountLocator = await findVisible(accountSelectors, "Google account selector", 12_000).catch(() => null);
  if (accountLocator) {
    await clickAndWait(accountLocator);
  }
}

async function runStep(
  name: ReportField,
  report: Record<ReportField, ReportStatus>,
  stepErrors: string[],
  action: () => Promise<void>
): Promise<void> {
  try {
    await action();
    report[name] = "PASS";
  } catch (error) {
    report[name] = "FAIL";
    const message = error instanceof Error ? error.message : String(error);
    stepErrors.push(`${name}: ${message}`);
  }
}

async function openLegalPageAndReturn(
  page: Page,
  context: BrowserContext,
  testInfoOutputPath: (path: string) => string,
  linkText: string,
  expectedHeading: string
): Promise<string> {
  const legalSection = await findByVisibleText(page, ["Sección Legal"], "Sección Legal", 10_000).catch(() => null);
  const searchScope: Page | Locator = legalSection ? legalSection.locator("xpath=ancestor::*[self::section or self::article or self::div][1]") : page;

  const legalLink = await findByVisibleText(searchScope, [linkText], `${linkText} link`, 15_000);
  const targetPage = await clickAndCapturePopup(page, context, legalLink);
  const finalPage = targetPage ?? page;

  const heading = await findVisible(
    [
      finalPage.getByRole("heading", { name: regexFromText(expectedHeading) }),
      finalPage.getByText(regexFromText(expectedHeading))
    ],
    `${expectedHeading} heading`,
    25_000
  );
  await expect(heading).toBeVisible();

  const legalBody = finalPage.locator("main, article, body").first();
  await expect(legalBody).toBeVisible();
  const legalText = (await legalBody.innerText()).replace(/\s+/g, " ").trim();
  if (legalText.length < 120) {
    throw new Error(`Legal content seems too short for ${expectedHeading}.`);
  }

  const screenshotFile =
    expectedHeading === "Términos y Condiciones"
      ? "05-terminos-y-condiciones.png"
      : "06-politica-de-privacidad.png";
  await finalPage.screenshot({ path: testInfoOutputPath(screenshotFile), fullPage: true });

  const finalUrl = finalPage.url();

  if (targetPage) {
    await targetPage.close().catch(() => undefined);
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiToSettle(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createEmptyReport();
  const stepErrors: string[] = [];
  const legalUrls: Record<string, string> = {};

  await runStep("Login", report, stepErrors, async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }

    const directGoogleButton = await findByVisibleText(
      page,
      ["Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google", "Continuar con Google"],
      "Google login button",
      20_000
    ).catch(() => null);

    let popup: Page | null = null;
    if (directGoogleButton) {
      popup = await clickAndCapturePopup(page, context, directGoogleButton);
    } else {
      const loginButton = await findByVisibleText(
        page,
        ["Login", "Ingresar", "Iniciar sesión", "Iniciar sesion", "Acceder"],
        "Login button",
        20_000
      );
      await clickAndWait(loginButton);

      const googleButton = await findByVisibleText(
        page,
        ["Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google", "Continuar con Google"],
        "Google login button after login click",
        20_000
      );
      popup = await clickAndCapturePopup(page, context, googleButton);
    }

    const accountTarget = popup ?? page;
    await trySelectGoogleAccount(accountTarget);

    if (popup) {
      await popup.waitForEvent("close", { timeout: 60_000 }).catch(() => undefined);
    }

    const sidebar = await findVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator("[class*='sidebar' i]")
      ],
      "Left sidebar navigation",
      90_000
    );

    await expect(sidebar).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath("01-dashboard-loaded.png"), fullPage: true });
  });

  await runStep("Mi Negocio menu", report, stepErrors, async () => {
    const negocioSection = await findByVisibleText(page, ["Negocio"], "Negocio sidebar section", 15_000).catch(() => null);
    if (negocioSection) {
      await clickAndWait(negocioSection);
    }

    const miNegocio = await findByVisibleText(page, ["Mi Negocio"], "Mi Negocio option", 20_000);
    await clickAndWait(miNegocio);

    const agregarNegocio = await findByVisibleText(page, ["Agregar Negocio"], "Agregar Negocio option", 20_000);
    const administrarNegocios = await findByVisibleText(page, ["Administrar Negocios"], "Administrar Negocios option", 20_000);
    await expect(agregarNegocio).toBeVisible();
    await expect(administrarNegocios).toBeVisible();

    await page.screenshot({ path: testInfo.outputPath("02-mi-negocio-expanded-menu.png"), fullPage: true });
  });

  await runStep("Agregar Negocio modal", report, stepErrors, async () => {
    const agregarNegocio = await findByVisibleText(page, ["Agregar Negocio"], "Agregar Negocio option", 20_000);
    await clickAndWait(agregarNegocio);

    const modalTitle = await findVisible(
      [
        page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
        page.getByText(/Crear Nuevo Negocio/i)
      ],
      "Crear Nuevo Negocio modal title",
      20_000
    );
    await expect(modalTitle).toBeVisible();

    const businessNameInput = await findVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[name*='nombre' i], input[id*='nombre' i], input[placeholder*='negocio' i]")
      ],
      "Nombre del Negocio input",
      20_000
    );
    await expect(businessNameInput).toBeVisible();

    const quotaText = await findByVisibleText(page, ["Tienes 2 de 3 negocios"], "business quota text", 20_000);
    await expect(quotaText).toBeVisible();

    const cancelButton = await findByVisibleText(page, ["Cancelar"], "Cancelar button", 20_000);
    const createButton = await findByVisibleText(page, ["Crear Negocio"], "Crear Negocio button", 20_000);
    await expect(cancelButton).toBeVisible();
    await expect(createButton).toBeVisible();

    await page.screenshot({ path: testInfo.outputPath("03-agregar-negocio-modal.png"), fullPage: true });

    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(cancelButton);
  });

  await runStep("Administrar Negocios view", report, stepErrors, async () => {
    const administrarVisible = await findByVisibleText(page, ["Administrar Negocios"], "Administrar Negocios option", 4_000).catch(
      () => null
    );
    if (!administrarVisible) {
      const miNegocio = await findByVisibleText(page, ["Mi Negocio"], "Mi Negocio option", 10_000);
      await clickAndWait(miNegocio);
    }

    const administrarNegocios = await findByVisibleText(page, ["Administrar Negocios"], "Administrar Negocios option", 20_000);
    await clickAndWait(administrarNegocios);

    await expect(await findByVisibleText(page, ["Información General"], "Información General section", 25_000)).toBeVisible();
    await expect(await findByVisibleText(page, ["Detalles de la Cuenta"], "Detalles de la Cuenta section", 25_000)).toBeVisible();
    await expect(await findByVisibleText(page, ["Tus Negocios"], "Tus Negocios section", 25_000)).toBeVisible();
    await expect(await findByVisibleText(page, ["Sección Legal"], "Sección Legal section", 25_000)).toBeVisible();

    await page.screenshot({ path: testInfo.outputPath("04-administrar-negocios-account-page.png"), fullPage: true });
  });

  await runStep("Información General", report, stepErrors, async () => {
    await expect(await findByVisibleText(page, ["Información General"], "Información General section", 20_000)).toBeVisible();
    await expect(await findByVisibleText(page, ["BUSINESS PLAN"], "BUSINESS PLAN text", 20_000)).toBeVisible();
    await expect(await findByVisibleText(page, ["Cambiar Plan"], "Cambiar Plan button", 20_000)).toBeVisible();

    const emailLocator = page.getByText(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/).first();
    await expect(emailLocator).toBeVisible();

    const possibleName = await findVisible(
      [
        page.getByText(/Nombre/i),
        page.locator("h1, h2, h3, h4").filter({ hasNotText: /Información General|Detalles de la Cuenta|Tus Negocios|Sección Legal/i })
      ],
      "User name in Información General",
      15_000
    );
    await expect(possibleName).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", report, stepErrors, async () => {
    await expect(await findByVisibleText(page, ["Cuenta creada"], "Cuenta creada text", 20_000)).toBeVisible();
    await expect(await findByVisibleText(page, ["Estado activo"], "Estado activo text", 20_000)).toBeVisible();
    await expect(await findByVisibleText(page, ["Idioma seleccionado"], "Idioma seleccionado text", 20_000)).toBeVisible();
  });

  await runStep("Tus Negocios", report, stepErrors, async () => {
    await expect(await findByVisibleText(page, ["Tus Negocios"], "Tus Negocios section", 20_000)).toBeVisible();
    await expect(await findByVisibleText(page, ["Agregar Negocio"], "Agregar Negocio button in Tus Negocios", 20_000)).toBeVisible();
    await expect(await findByVisibleText(page, ["Tienes 2 de 3 negocios"], "business quota text", 20_000)).toBeVisible();

    const businessListItem = await findVisible(
      [
        page.locator("li").filter({ hasText: /negocio/i }),
        page.locator("[role='row']").filter({ hasText: /negocio/i }),
        page.locator("article, .card, [data-testid*='business' i]")
      ],
      "Business list content",
      15_000
    );
    await expect(businessListItem).toBeVisible();
  });

  await runStep("Términos y Condiciones", report, stepErrors, async () => {
    const finalUrl = await openLegalPageAndReturn(
      page,
      context,
      testInfo.outputPath.bind(testInfo),
      "Términos y Condiciones",
      "Términos y Condiciones"
    );
    legalUrls["Términos y Condiciones"] = finalUrl;
  });

  await runStep("Política de Privacidad", report, stepErrors, async () => {
    const finalUrl = await openLegalPageAndReturn(
      page,
      context,
      testInfo.outputPath.bind(testInfo),
      "Política de Privacidad",
      "Política de Privacidad"
    );
    legalUrls["Política de Privacidad"] = finalUrl;
  });

  const reportLines = REPORT_FIELDS.map((field) => `- ${field}: ${report[field]}`);
  const legalEvidence = [
    `- Términos y Condiciones URL: ${legalUrls["Términos y Condiciones"] ?? "N/A"}`,
    `- Política de Privacidad URL: ${legalUrls["Política de Privacidad"] ?? "N/A"}`
  ];
  const errorLines = stepErrors.length > 0 ? ["", "Errors:", ...stepErrors.map((error) => `- ${error}`)] : [];
  const finalReport = ["Final Report", ...reportLines, "", "Legal URLs", ...legalEvidence, ...errorLines].join("\n");

  await testInfo.attach("final-report", {
    body: finalReport,
    contentType: "text/plain"
  });

  const failedSteps = REPORT_FIELDS.filter((field) => report[field] === "FAIL");
  expect(
    failedSteps,
    `One or more workflow validations failed.\n\n${finalReport}`
  ).toEqual([]);
});
