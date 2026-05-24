import { expect, test, type BrowserContext, type Locator, type Page, type TestInfo } from "@playwright/test";

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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function initializeReport(): Record<ReportField, StepStatus> {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
}

function toSlug(value: string): string {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
}

async function firstVisible(candidates: Locator[], label: string, timeoutMs = 20_000): Promise<Locator> {
  const perCandidateTimeout = Math.max(Math.floor(timeoutMs / Math.max(candidates.length, 1)), 1_500);
  let lastError: unknown;

  for (const candidate of candidates) {
    try {
      const target = candidate.first();
      await target.waitFor({ state: "visible", timeout: perCandidateTimeout });
      return target;
    } catch (error) {
      lastError = error;
    }
  }

  throw new Error(`Could not locate a visible element for "${label}". Last error: ${String(lastError)}`);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => undefined);
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  filename: string,
  fullPage = false
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(filename),
    fullPage
  });
}

async function chooseGoogleAccountIfVisible(page: Page): Promise<void> {
  const accountCandidate = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  const visible = await accountCandidate.isVisible({ timeout: 8_000 }).catch(() => false);

  if (visible) {
    await accountCandidate.click();
    await waitForUi(page);
  }
}

async function ensureLoginPageReady(page: Page): Promise<void> {
  const startUrl = process.env.SALEADS_START_URL ?? process.env.SALEADS_BASE_URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);

  const loginButton = await firstVisible(
    [
      page.getByRole("button", { name: /sign in with google|iniciar sesion con google|iniciar sesión con google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesion con google|iniciar sesión con google/i }),
      page.getByText(/sign in with google|iniciar sesion con google|iniciar sesión con google/i)
    ],
    "Google login button"
  );

  await expect(loginButton).toBeVisible();
}

async function expandMiNegocioMenu(page: Page): Promise<void> {
  const negocioButton = await firstVisible(
    [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ],
    "Negocio menu item"
  );

  await clickAndWait(page, negocioButton);

  const miNegocioButton = await firstVisible(
    [
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ],
    "Mi Negocio menu item"
  );

  await clickAndWait(page, miNegocioButton);
}

async function validateLegalLink(params: {
  page: Page;
  context: BrowserContext;
  testInfo: TestInfo;
  linkRegex: RegExp;
  headingRegex: RegExp;
  contentRegex: RegExp;
  screenshotName: string;
}): Promise<string> {
  const { page, context, testInfo, linkRegex, headingRegex, contentRegex, screenshotName } = params;

  const legalLink = await firstVisible(
    [page.getByRole("link", { name: linkRegex }), page.getByText(linkRegex)],
    `legal link (${linkRegex.toString()})`
  );

  const currentUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);

  await legalLink.click();

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  await waitForUi(targetPage);
  await expect(targetPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible();
  await expect(
    targetPage.locator("main, article, section, p, li, div").filter({ hasText: contentRegex }).first()
  ).toBeVisible();

  await targetPage.screenshot({ path: testInfo.outputPath(screenshotName), fullPage: true });
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== currentUrl) {
    await page.goBack().catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = initializeReport();
  const failures: string[] = [];
  const legalUrls: Record<string, string> = {};

  async function runStep(name: ReportField, action: () => Promise<void>): Promise<void> {
    try {
      await action();
      report[name] = "PASS";
    } catch (error) {
      report[name] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      failures.push(`${name}: ${message}`);
      await page.screenshot({ path: testInfo.outputPath(`failure-${toSlug(name)}.png`), fullPage: true }).catch(() => undefined);
    }
  }

  await runStep("Login", async () => {
    await ensureLoginPageReady(page);

    const loginButton = await firstVisible(
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesion con google|iniciar sesión con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesion con google|iniciar sesión con google/i }),
        page.getByText(/sign in with google|iniciar sesion con google|iniciar sesión con google/i)
      ],
      "Google login button"
    );

    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await loginButton.click();
    const popup = await popupPromise;

    if (popup) {
      await waitForUi(popup);
      await chooseGoogleAccountIfVisible(popup);
      await popup.waitForEvent("close", { timeout: 60_000 }).catch(() => undefined);
      await page.bringToFront();
    } else {
      await waitForUi(page);
      await chooseGoogleAccountIfVisible(page);
    }

    const mainInterface = await firstVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.getByText(/Mi Negocio|Negocio|Dashboard|Panel/i)
      ],
      "main application interface"
    );
    await expect(mainInterface).toBeVisible();

    const sidebar = await firstVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator("[class*='sidebar'], [data-testid*='sidebar']")
      ],
      "left sidebar"
    );
    await expect(sidebar).toBeVisible();

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await expandMiNegocioMenu(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocioOption = await firstVisible(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      "Agregar Negocio option"
    );

    await clickAndWait(page, agregarNegocioOption);

    const modalTitle = page.getByText(/^Crear Nuevo Negocio$/i).first();
    await expect(modalTitle).toBeVisible();

    const businessNameInput = await firstVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[name*='nombre'], input[id*='nombre']")
      ],
      "Nombre del Negocio input"
    );
    await expect(businessNameInput).toBeVisible();

    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const cancelButton = await firstVisible(
      [page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)],
      "Cancelar button"
    );
    await clickAndWait(page, cancelButton);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await expandMiNegocioMenu(page);
    }

    const administrarNegociosOption = await firstVisible(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ],
      "Administrar Negocios option"
    );

    await clickAndWait(page, administrarNegociosOption);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "04-administrar-negocios-view-full.png", true);
  });

  await runStep("Información General", async () => {
    const generalSection = page.locator("section, div").filter({ hasText: /Información General/i }).first();
    await expect(generalSection).toBeVisible();

    const emailText = generalSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
    await expect(emailText).toBeVisible();

    const sectionText = await generalSection.innerText();
    const emailValue = sectionText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)?.[0] ?? "";
    const withoutStaticLabels = sectionText
      .replace(emailValue, "")
      .replace(/Información General/gi, "")
      .replace(/BUSINESS PLAN/gi, "")
      .replace(/Cambiar Plan/gi, "")
      .trim();

    expect(withoutStaticLabels.length, "Expected user name text to be visible in Información General section.").toBeGreaterThan(0);
    await expect(generalSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(generalSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = page.locator("section, div").filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(detailsSection).toBeVisible();

    await expect(detailsSection.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const businessesSection = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessesSection).toBeVisible();

    const businessesList = await firstVisible(
      [
        businessesSection.locator("ul, ol, table, [role='list'], [role='table']"),
        businessesSection.locator("[class*='business'], [data-testid*='business']"),
        businessesSection.locator("li, tr")
      ],
      "business list inside Tus Negocios",
      10_000
    );
    await expect(businessesList).toBeVisible();

    await expect(businessesSection.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(businessesSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await validateLegalLink({
      page,
      context,
      testInfo,
      linkRegex: /Términos y Condiciones/i,
      headingRegex: /Términos y Condiciones/i,
      contentRegex: /términos|condiciones|servicio|usuario/i,
      screenshotName: "08-terminos-y-condiciones.png"
    });
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await validateLegalLink({
      page,
      context,
      testInfo,
      linkRegex: /Política de Privacidad/i,
      headingRegex: /Política de Privacidad/i,
      contentRegex: /privacidad|datos personales|información/i,
      screenshotName: "09-politica-de-privacidad.png"
    });
  });

  const finalReport = {
    test: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    legalUrls
  };

  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });

  console.log("SALEADS MI NEGOCIO FINAL REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
