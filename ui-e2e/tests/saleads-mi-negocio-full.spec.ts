import { expect, type Locator, type Page, test } from "@playwright/test";
import * as fs from "node:fs/promises";
import * as path from "node:path";

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
  status: "PASS" | "FAIL";
  details: string[];
  evidence: string[];
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_LIMIT_TEXT = /Tienes\s+2\s+de\s+3\s+negocios/i;
const BUSINESS_NAME = "Negocio Prueba Automatización";

function escapeRegex(raw: string): string {
  return raw.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function normalizeForPath(raw: string): string {
  return raw.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 7_000 });
  } catch {
    // Some environments keep active connections; DOM content loaded is enough.
  }
}

async function findVisibleLocator(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const first = candidate.first();
    try {
      await first.waitFor({ state: "visible", timeout: 2_000 });
      return first;
    } catch {
      // Try next candidate.
    }
  }
  return null;
}

function withTextCandidates(page: Page, text: string): Locator[] {
  const escaped = escapeRegex(text);
  const exact = new RegExp(`^\\s*${escaped}\\s*$`, "i");
  const contains = new RegExp(escaped, "i");

  return [
    page.getByRole("button", { name: exact }),
    page.getByRole("link", { name: exact }),
    page.getByRole("menuitem", { name: exact }),
    page.getByRole("tab", { name: exact }),
    page.getByRole("option", { name: exact }),
    page.getByText(exact),
    page.getByText(contains)
  ];
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const locator = await findVisibleLocator(withTextCandidates(page, text));
  if (!locator) {
    throw new Error(`No visible clickable element found for text: "${text}"`);
  }

  await locator.click();
  await waitForUi(page);
}

async function expectVisibleText(page: Page, textRegex: RegExp): Promise<void> {
  const locator = page.getByText(textRegex).first();
  await locator.waitFor({ state: "visible", timeout: 30_000 });
  await expect(locator).toBeVisible();
}

async function captureShot(
  page: Page,
  screenshotsDir: string,
  label: string,
  fullPage = false
): Promise<string> {
  const fileName = `${normalizeForPath(label)}.png`;
  const filePath = path.join(screenshotsDir, fileName);

  await waitForUi(page);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function selectGoogleAccountIfVisible(targetPage: Page): Promise<boolean> {
  const accountLocator = targetPage.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")).first();
  try {
    await accountLocator.waitFor({ state: "visible", timeout: 15_000 });
    await accountLocator.click();
    await waitForUi(targetPage);
    return true;
  } catch {
    return false;
  }
}

function newStepResult(): StepResult {
  return { status: "PASS", details: [], evidence: [] };
}

async function runStep(
  report: Record<ReportField, StepResult>,
  field: ReportField,
  action: () => Promise<void>
): Promise<void> {
  try {
    await action();
  } catch (error) {
    report[field].status = "FAIL";
    report[field].details.push(error instanceof Error ? error.message : String(error));
  }
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregar = page.getByText(/Agregar Negocio/i).first();
  const administrar = page.getByText(/Administrar Negocios/i).first();

  if ((await agregar.isVisible().catch(() => false)) && (await administrar.isVisible().catch(() => false))) {
    return;
  }

  const negocioSection = await findVisibleLocator(withTextCandidates(page, "Negocio"));
  if (negocioSection) {
    await negocioSection.click();
    await waitForUi(page);
  }

  await clickByVisibleText(page, "Mi Negocio");

  await agregar.waitFor({ state: "visible", timeout: 20_000 });
  await administrar.waitFor({ state: "visible", timeout: 20_000 });
}

async function clickLegalLinkAndValidate(params: {
  page: Page;
  screenshotsDir: string;
  linkText: string;
  headingRegex: RegExp;
  report: Record<ReportField, StepResult>;
  field: "Términos y Condiciones" | "Política de Privacidad";
}): Promise<void> {
  const { page, screenshotsDir, linkText, headingRegex, report, field } = params;
  const appUrlBefore = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);

  await clickByVisibleText(page, linkText);

  let targetPage = (await popupPromise) ?? page;
  if (targetPage !== page) {
    await targetPage.waitForLoadState("domcontentloaded");
  } else {
    await waitForUi(targetPage);
  }

  const heading = targetPage.getByRole("heading", { name: headingRegex }).first();
  if ((await heading.count()) === 0) {
    await expectVisibleText(targetPage, headingRegex);
  } else {
    await expect(heading).toBeVisible({ timeout: 20_000 });
  }

  const legalContent = targetPage.locator("main, article, section, body").first();
  const contentText = (await legalContent.innerText()).trim();
  if (contentText.length < 100) {
    throw new Error(`Legal content looks too short for "${linkText}" page.`);
  }

  const screenshotPath = await captureShot(targetPage, screenshotsDir, `legal-${linkText}`, true);
  report[field].evidence.push(screenshotPath);
  report[field].details.push(`Final URL: ${targetPage.url()}`);

  if (targetPage !== page) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  // Same-tab navigation fallback: return to the application view.
  try {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 20_000 });
  } catch {
    await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
  }
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const startedAt = new Date();
  const runId = startedAt.toISOString().replace(/[:.]/g, "-");
  const screenshotsDir = path.resolve(process.cwd(), "screenshots", runId);
  await fs.mkdir(screenshotsDir, { recursive: true });

  const report: Record<ReportField, StepResult> = {
    Login: newStepResult(),
    "Mi Negocio menu": newStepResult(),
    "Agregar Negocio modal": newStepResult(),
    "Administrar Negocios view": newStepResult(),
    "Información General": newStepResult(),
    "Detalles de la Cuenta": newStepResult(),
    "Tus Negocios": newStepResult(),
    "Términos y Condiciones": newStepResult(),
    "Política de Privacidad": newStepResult()
  };

  // Support any environment URL by accepting runtime values only.
  if (page.url() === "about:blank") {
    const providedUrl = process.env.SALEADS_START_URL ?? process.env.SALEADS_BASE_URL;
    if (!providedUrl) {
      throw new Error(
        "No initial page loaded. Set SALEADS_START_URL or SALEADS_BASE_URL to the current environment login URL."
      );
    }
    await page.goto(providedUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await runStep(report, "Login", async () => {
    const sidebarNav = page.locator("aside, nav").first();
    const negocioInSidebar = page.getByText(/Negocio/i).first();

    const alreadyLoggedIn =
      (await sidebarNav.isVisible().catch(() => false)) && (await negocioInSidebar.isVisible().catch(() => false));

    if (!alreadyLoggedIn) {
      const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);

      const loginLocator = await findVisibleLocator([
        ...withTextCandidates(page, "Sign in with Google"),
        ...withTextCandidates(page, "Continuar con Google"),
        ...withTextCandidates(page, "Iniciar sesión con Google"),
        ...withTextCandidates(page, "Login"),
        ...withTextCandidates(page, "Iniciar sesión")
      ]);

      if (!loginLocator) {
        throw new Error("Could not find a login or Google sign-in button.");
      }

      await loginLocator.click();
      await waitForUi(page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const accountSelectedInPopup = await selectGoogleAccountIfVisible(popup);
        if (accountSelectedInPopup) {
          try {
            await popup.waitForEvent("close", { timeout: 30_000 });
          } catch {
            await waitForUi(page);
          }
        }
      } else {
        await selectGoogleAccountIfVisible(page);
      }
    }

    await page.getByText(/Negocio/i).first().waitFor({ state: "visible", timeout: 60_000 });
    const dashboardScreenshot = await captureShot(page, screenshotsDir, "dashboard-loaded", true);
    report.Login.evidence.push(dashboardScreenshot);
    report.Login.details.push("Main interface and left sidebar are visible.");
  });

  await runStep(report, "Mi Negocio menu", async () => {
    await ensureMiNegocioExpanded(page);
    await expectVisibleText(page, /Agregar Negocio/i);
    await expectVisibleText(page, /Administrar Negocios/i);
    const menuScreenshot = await captureShot(page, screenshotsDir, "mi-negocio-menu-expanded");
    report["Mi Negocio menu"].evidence.push(menuScreenshot);
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, "Agregar Negocio");

    await expectVisibleText(page, /Crear Nuevo Negocio/i);
    await expectVisibleText(page, /Nombre del Negocio/i);
    await expectVisibleText(page, BUSINESS_LIMIT_TEXT);
    await expectVisibleText(page, /Cancelar/i);
    await expectVisibleText(page, /Crear Negocio/i);

    const modalScreenshot = await captureShot(page, screenshotsDir, "agregar-negocio-modal");
    report["Agregar Negocio modal"].evidence.push(modalScreenshot);

    // Optional action from the workflow: type business name if field is available.
    try {
      const input = await findVisibleLocator([
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("div[role='dialog'] input").first(),
        page.locator("input[name*='negocio' i]").first()
      ]);

      if (input) {
        await input.click();
        await input.fill(BUSINESS_NAME);
      }
    } catch {
      report["Agregar Negocio modal"].details.push(
        "Optional typing step skipped because the business name input was not interactable."
      );
    }

    await clickByVisibleText(page, "Cancelar");
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeHidden({ timeout: 15_000 });
  });

  await runStep(report, "Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);
    await clickByVisibleText(page, "Administrar Negocios");

    await expectVisibleText(page, /Información General/i);
    await expectVisibleText(page, /Detalles de la Cuenta/i);
    await expectVisibleText(page, /Tus Negocios/i);
    await expectVisibleText(page, /Sección Legal/i);

    const accountPageShot = await captureShot(page, screenshotsDir, "administrar-negocios-account-page", true);
    report["Administrar Negocios view"].evidence.push(accountPageShot);
  });

  await runStep(report, "Información General", async () => {
    const infoSection = page.locator("section,div").filter({ hasText: /Información General/i }).first();
    await expect(infoSection).toBeVisible({ timeout: 20_000 });

    const sectionText = await infoSection.innerText();
    const sectionLines = sectionText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    const userEmailLine = sectionLines.find((line) => /@/.test(line));
    if (!userEmailLine) {
      throw new Error("User email is not visible in 'Información General'.");
    }

    const likelyUserNameLine = sectionLines.find(
      (line) =>
        !/@/.test(line) &&
        !/informaci[oó]n general/i.test(line) &&
        !/business plan/i.test(line) &&
        !/cambiar plan/i.test(line)
    );
    if (!likelyUserNameLine) {
      throw new Error("A likely user name is not visible in 'Información General'.");
    }

    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    const detailsSection = page.locator("section,div").filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(detailsSection).toBeVisible({ timeout: 20_000 });
    await expect(detailsSection.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    const businessSection = page.locator("section,div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible({ timeout: 20_000 });
    await expect(businessSection.getByText(BUSINESS_LIMIT_TEXT).first()).toBeVisible();

    const addBusinessCta = await findVisibleLocator([
      businessSection.getByRole("button", { name: /Agregar Negocio/i }),
      businessSection.getByRole("link", { name: /Agregar Negocio/i }),
      businessSection.getByText(/Agregar Negocio/i).first()
    ]);
    if (!addBusinessCta) {
      throw new Error("'Agregar Negocio' button/link is not visible in 'Tus Negocios'.");
    }
    await expect(addBusinessCta).toBeVisible();

    const businessRows = businessSection.locator("li, tr, [data-testid*='business'], [class*='business']");
    const rowCount = await businessRows.count();
    if (rowCount === 0) {
      const sectionText = await businessSection.innerText();
      if (sectionText.trim().length < 30) {
        throw new Error("Business list content appears empty.");
      }
    }
  });

  await runStep(report, "Términos y Condiciones", async () => {
    await clickLegalLinkAndValidate({
      page,
      screenshotsDir,
      linkText: "Términos y Condiciones",
      headingRegex: /Términos y Condiciones/i,
      report,
      field: "Términos y Condiciones"
    });
  });

  await runStep(report, "Política de Privacidad", async () => {
    await clickLegalLinkAndValidate({
      page,
      screenshotsDir,
      linkText: "Política de Privacidad",
      headingRegex: /Pol[ií]tica de Privacidad/i,
      report,
      field: "Política de Privacidad"
    });
  });

  const finalReport = {
    test: "saleads_mi_negocio_full_test",
    startedAt: startedAt.toISOString(),
    endedAt: new Date().toISOString(),
    results: report
  };

  const reportPath = path.join(screenshotsDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

  // Final report in console output (step 10 requirement).
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(finalReport, null, 2));

  for (const [field, result] of Object.entries(report)) {
    expect.soft(result.status, `${field} should pass`).toBe("PASS");
  }
});
