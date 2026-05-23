import { expect, type BrowserContext, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

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
type ReportValue = "NOT_RUN" | "PASS" | `FAIL: ${string}`;
type Report = Record<ReportField, ReportValue>;

const UI_STABILIZATION_WAIT_MS = 700;

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = initReport();
  const errors: string[] = [];
  const legalUrls: Record<string, string> = {};
  const evidenceDir = buildEvidenceDir(testInfo.outputDir);

  const loginPassed = await runStep("Login", report, errors, async () => {
    await openLoginPage(page);

    const loginTrigger = await firstVisible(
      [
        page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n|continuar/i }),
        page.getByRole("link", { name: /google|sign in|iniciar sesi[oó]n|continuar/i }),
        page.getByText(/google|sign in|iniciar sesi[oó]n|continuar/i),
      ],
      "Google login trigger",
      20_000,
    );

    const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
    await clickAndWait(page, loginTrigger);
    const googlePopup = await popupPromise;

    const activeAppPage = await finishGoogleSignIn({
      context,
      fallbackPage: page,
      googlePopup,
    });

    await expect(mainInterfaceLocator(activeAppPage)).toBeVisible({ timeout: 30_000 });
    await expect(sidebarLocator(activeAppPage)).toBeVisible({ timeout: 30_000 });
    await screenshot(activeAppPage, evidenceDir, "01-dashboard-loaded.png");
  });

  const miNegocioMenuPassed = loginPassed
    ? await runStep("Mi Negocio menu", report, errors, async () => {
    await ensureSidebarVisible(page);

    const negocioEntry = await firstVisible(
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      "Negocio menu entry",
    );
    await clickAndWait(page, negocioEntry);

    const miNegocioEntry = await firstVisible(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ],
      "Mi Negocio menu entry",
    );
    await clickAndWait(page, miNegocioEntry);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible({ timeout: 20_000 });
    await screenshot(page, evidenceDir, "02-mi-negocio-menu-expanded.png");
      })
    : skipStep("Mi Negocio menu", report, errors, "Prerequisite failed: Login");

  const agregarNegocioModalPassed = miNegocioMenuPassed
    ? await runStep("Agregar Negocio modal", report, errors, async () => {
    const agregarNegocioEntry = await firstVisible(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      "Agregar Negocio action",
    );
    await clickAndWait(page, agregarNegocioEntry);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeVisible({ timeout: 20_000 });
    const businessNameInput = await firstVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator('input[name*="negocio" i], input[placeholder*="Negocio"]'),
      ],
      "Nombre del Negocio input",
      20_000,
    );
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible({ timeout: 20_000 });

    await screenshot(page, evidenceDir, "03-agregar-negocio-modal.png");

    await businessNameInput.click();
    await waitForUi(page);
    await businessNameInput.fill("Negocio Prueba Automatización");
    await waitForUi(page);

    const cancelButton = await firstVisible(
      [page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)],
      "Cancelar modal button",
    );
    await clickAndWait(page, cancelButton);
    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).not.toBeVisible({ timeout: 20_000 });
      })
    : skipStep("Agregar Negocio modal", report, errors, "Prerequisite failed: Mi Negocio menu");

  const administrarViewPassed = loginPassed
    ? await runStep("Administrar Negocios view", report, errors, async () => {
    await ensureMiNegocioExpanded(page);

    const administrarNegociosEntry = await firstVisible(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      "Administrar Negocios entry",
      20_000,
    );
    await clickAndWait(page, administrarNegociosEntry);

    await expect(page.getByText(/Información General/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Sección Legal/i)).toBeVisible({ timeout: 30_000 });

    await screenshot(page, evidenceDir, "04-administrar-negocios-full-view.png");
      })
    : skipStep("Administrar Negocios view", report, errors, "Prerequisite failed: Login");

  const informacionGeneralPassed = administrarViewPassed
    ? await runStep("Información General", report, errors, async () => {
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 20_000 });

    const visibleEmail = await firstVisible(
      [
        page.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
        page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
      ],
      "User email in Informacion General",
      20_000,
    );
    await expect(visibleEmail).toBeVisible();

    const nameCandidate = await firstVisible(
      [
        page.getByText(/juan|lucas|barbier|garzon/i),
        page.locator("h1, h2, h3, [data-testid*='name' i]").filter({ hasText: /[A-Za-z]/ }),
      ],
      "User name in Informacion General",
      20_000,
    );
    await expect(nameCandidate).toBeVisible();
      })
    : skipStep("Información General", report, errors, "Prerequisite failed: Administrar Negocios view");

  const detallesCuentaPassed = administrarViewPassed
    ? await runStep("Detalles de la Cuenta", report, errors, async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Estado activo/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 20_000 });
      })
    : skipStep("Detalles de la Cuenta", report, errors, "Prerequisite failed: Administrar Negocios view");

  const tusNegociosPassed = administrarViewPassed
    ? await runStep("Tus Negocios", report, errors, async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible({ timeout: 20_000 });
      })
    : skipStep("Tus Negocios", report, errors, "Prerequisite failed: Administrar Negocios view");

  const terminosPassed = administrarViewPassed
    ? await runStep("Términos y Condiciones", report, errors, async () => {
    const legalUrl = await validateLegalDocument({
      page,
      context,
      evidenceDir,
      screenshotName: "05-terminos-y-condiciones.png",
      linkPattern: /Términos y Condiciones/i,
      headingPattern: /Términos y Condiciones/i,
    });
    legalUrls["Términos y Condiciones"] = legalUrl;
      })
    : skipStep("Términos y Condiciones", report, errors, "Prerequisite failed: Administrar Negocios view");

  const politicaPassed = administrarViewPassed
    ? await runStep("Política de Privacidad", report, errors, async () => {
    const legalUrl = await validateLegalDocument({
      page,
      context,
      evidenceDir,
      screenshotName: "06-politica-de-privacidad.png",
      linkPattern: /Política de Privacidad/i,
      headingPattern: /Política de Privacidad/i,
    });
    legalUrls["Política de Privacidad"] = legalUrl;
      })
    : skipStep("Política de Privacidad", report, errors, "Prerequisite failed: Administrar Negocios view");

  const finalReportPath = path.join(evidenceDir, "final-report.json");
  const finalReport = {
    generatedAt: new Date().toISOString(),
    report,
    legalUrls,
    evidenceDir,
  };
  fs.writeFileSync(finalReportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("final-report", { path: finalReportPath, contentType: "application/json" });

  void agregarNegocioModalPassed;
  void informacionGeneralPassed;
  void detallesCuentaPassed;
  void tusNegociosPassed;
  void terminosPassed;
  void politicaPassed;

  if (errors.length > 0) {
    throw new Error(`Workflow validation failed:\n${errors.map((item) => `- ${item}`).join("\n")}`);
  }
});

function initReport(): Report {
  return REPORT_FIELDS.reduce((accumulator, field) => {
    accumulator[field] = "NOT_RUN";
    return accumulator;
  }, {} as Report);
}

async function runStep(
  field: ReportField,
  report: Report,
  errors: string[],
  callback: () => Promise<void>,
): Promise<boolean> {
  try {
    await callback();
    report[field] = "PASS";
    return true;
  } catch (error) {
    const message = errorToMessage(error);
    report[field] = `FAIL: ${message}`;
    errors.push(`${field}: ${message}`);
    return false;
  }
}

function skipStep(field: ReportField, report: Report, errors: string[], reason: string): false {
  report[field] = `FAIL: ${reason}`;
  errors.push(`${field}: ${reason}`);
  return false;
}

async function openLoginPage(page: Page): Promise<void> {
  const startUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL ?? process.env.BASE_URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error("No login URL provided. Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL).");
  }
}

async function finishGoogleSignIn({
  context,
  fallbackPage,
  googlePopup,
}: {
  context: BrowserContext;
  fallbackPage: Page;
  googlePopup: Page | null;
}): Promise<Page> {
  if (googlePopup) {
    await googlePopup.waitForLoadState("domcontentloaded", { timeout: 30_000 });
    await waitForUi(googlePopup);
    await selectGoogleAccountIfVisible(googlePopup);

    const resolved = await waitForAppPage(context, fallbackPage, googlePopup);
    await waitForUi(resolved);
    return resolved;
  }

  await selectGoogleAccountIfVisible(fallbackPage);
  await waitForUi(fallbackPage);
  return fallbackPage;
}

async function selectGoogleAccountIfVisible(page: Page): Promise<void> {
  const accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com").first();

  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUi(page);
  }
}

async function waitForAppPage(context: BrowserContext, fallbackPage: Page, popupPage: Page): Promise<Page> {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    for (const candidate of context.pages()) {
      if (candidate.isClosed()) {
        continue;
      }

      if (await hasMainInterface(candidate)) {
        return candidate;
      }
    }

    if (popupPage.isClosed() && (await hasMainInterface(fallbackPage))) {
      return fallbackPage;
    }

    await fallbackPage.waitForTimeout(1_000);
  }

  throw new Error("Main application interface was not detected after Google sign in.");
}

async function validateLegalDocument({
  page,
  context,
  evidenceDir,
  screenshotName,
  linkPattern,
  headingPattern,
}: {
  page: Page;
  context: BrowserContext;
  evidenceDir: string;
  screenshotName: string;
  linkPattern: RegExp;
  headingPattern: RegExp;
}): Promise<string> {
  const legalLink = await firstVisible(
    [page.getByRole("link", { name: linkPattern }), page.getByText(linkPattern)],
    linkPattern.source,
    20_000,
  );

  const appUrlBeforeClick = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickAndWait(page, legalLink);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 30_000 });
    await waitForUi(popup);
    await validateLegalPageContent(popup, headingPattern);
    await screenshot(popup, evidenceDir, screenshotName);
    const legalUrl = popup.url();
    await popup.close().catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
    return legalUrl;
  }

  await validateLegalPageContent(page, headingPattern);
  await screenshot(page, evidenceDir, screenshotName);
  const legalUrl = page.url();

  if (page.url() !== appUrlBeforeClick) {
    await page.goBack().catch(async () => {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(page);
  }

  await page.bringToFront();
  return legalUrl;
}

async function validateLegalPageContent(legalPage: Page, headingPattern: RegExp): Promise<void> {
  const heading = await firstVisible(
    [legalPage.getByRole("heading", { name: headingPattern }), legalPage.getByText(headingPattern)],
    headingPattern.source,
    20_000,
  );
  await expect(heading).toBeVisible();

  const pageBody = legalPage.locator("body");
  const bodyText = (await pageBody.innerText()).replace(/\s+/g, " ").trim();
  expect(bodyText.length, "Legal content should include meaningful text").toBeGreaterThan(120);
}

async function ensureSidebarVisible(page: Page): Promise<void> {
  await expect(sidebarLocator(page)).toBeVisible({ timeout: 30_000 });
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  if (await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false)) {
    return;
  }

  const miNegocioEntry = await firstVisible(
    [
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ],
    "Mi Negocio menu entry for expand",
  );
  await clickAndWait(page, miNegocioEntry);
}

async function clickAndWait(waitPage: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 20_000 });
  await locator.click();
  await waitForUi(waitPage);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
  await page.waitForTimeout(UI_STABILIZATION_WAIT_MS);
}

async function firstVisible(candidates: Locator[], description: string, timeoutMs = 12_000): Promise<Locator> {
  const timeoutPerLocator = Math.max(Math.floor(timeoutMs / candidates.length), 1_000);

  for (const candidate of candidates) {
    const current = candidate.first();
    try {
      await current.waitFor({ state: "visible", timeout: timeoutPerLocator });
      return current;
    } catch {
      continue;
    }
  }

  throw new Error(`Unable to locate visible element for "${description}" in ${timeoutMs}ms.`);
}

function sidebarLocator(page: Page): Locator {
  return page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio/i }).first();
}

function mainInterfaceLocator(page: Page): Locator {
  return page
    .locator("aside, nav, main, [role='main']")
    .filter({ hasText: /Negocio|Mi Negocio|Dashboard|Panel/i })
    .first();
}

async function hasMainInterface(page: Page): Promise<boolean> {
  const locator = mainInterfaceLocator(page);
  return locator.isVisible().catch(() => false);
}

async function screenshot(page: Page, evidenceDir: string, filename: string): Promise<void> {
  await page.screenshot({
    path: path.join(evidenceDir, filename),
    fullPage: true,
  });
}

function buildEvidenceDir(outputDir: string): string {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const evidenceDir = path.join(outputDir, "evidence", timestamp);
  fs.mkdirSync(evidenceDir, { recursive: true });
  return evidenceDir;
}

function errorToMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}
