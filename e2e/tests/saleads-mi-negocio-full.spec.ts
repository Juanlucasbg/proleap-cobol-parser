import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

const ARTIFACTS_DIR = path.resolve(process.cwd(), "artifacts");
const SCREENSHOT_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "saleads_mi_negocio_report.json");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type ResultStatus = "PASS" | "FAIL";

type StepResult = {
  status: ResultStatus;
  details: string[];
  evidence: string[];
};

type WorkflowResults = Record<ReportField, StepResult>;

function createInitialResults(): WorkflowResults {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL" as const, details: ["Not executed."], evidence: [] }]),
  ) as WorkflowResults;
}

function formatError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => undefined);
  await page.waitForTimeout(700);
}

async function waitForVisible(locator: Locator, timeout: number): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function findByVisibleText(page: Page, pattern: RegExp): Promise<Locator> {
  const candidates: Locator[] = [
    page.getByRole("button", { name: pattern }).first(),
    page.getByRole("link", { name: pattern }).first(),
    page.getByRole("menuitem", { name: pattern }).first(),
    page.getByRole("tab", { name: pattern }).first(),
    page.getByText(pattern).first(),
  ];

  for (const candidate of candidates) {
    if (await waitForVisible(candidate, 6_000)) {
      return candidate;
    }
  }

  throw new Error(`Unable to find visible element with text pattern ${pattern.toString()}.`);
}

async function clickByVisibleText(page: Page, pattern: RegExp): Promise<Locator> {
  const locator = await findByVisibleText(page, pattern);
  await locator.click();
  await waitForUiLoad(page);
  return locator;
}

async function screenshot(page: Page, fileName: string, fullPage = false): Promise<string> {
  const targetPath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: targetPath, fullPage });
  return path.relative(process.cwd(), targetPath);
}

async function chooseGoogleAccountIfVisible(
  page: Page,
  context: BrowserContext,
  popupAfterLogin: Page | null,
): Promise<void> {
  let authPage: Page | null = popupAfterLogin;

  if (!authPage && /accounts\.google\.com/i.test(page.url())) {
    authPage = page;
  }

  if (!authPage) {
    authPage = context.pages().find((candidate) => /accounts\.google\.com/i.test(candidate.url())) ?? null;
  }

  if (!authPage) {
    return;
  }

  await authPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);

  const accountLocator = authPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
  if (await waitForVisible(accountLocator, 10_000)) {
    await accountLocator.click();
  }

  await authPage.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => undefined);

  if (authPage !== page) {
    await authPage.waitForClose({ timeout: 30_000 }).catch(() => undefined);
    await page.bringToFront().catch(() => undefined);
  }
}

async function runStep(
  field: ReportField,
  results: WorkflowResults,
  action: () => Promise<string[]>,
): Promise<boolean> {
  try {
    const evidence = await action();
    results[field] = { status: "PASS", details: ["All validations passed."], evidence };
    return true;
  } catch (error) {
    results[field] = { status: "FAIL", details: [formatError(error)], evidence: [] };
    return false;
  }
}

function markBlocked(field: ReportField, results: WorkflowResults, reason: string): void {
  results[field] = { status: "FAIL", details: [`Blocked: ${reason}`], evidence: [] };
}

async function validateLegalPage(
  page: Page,
  context: BrowserContext,
  linkPattern: RegExp,
  headingPattern: RegExp,
  screenshotName: string,
): Promise<{ screenshotPath: string; finalUrl: string }> {
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickByVisibleText(page, linkPattern);
  const popup = await popupPromise;
  const targetPage = popup ?? page;

  await targetPage.waitForLoadState("domcontentloaded", { timeout: 40_000 }).catch(() => undefined);
  await targetPage.waitForTimeout(800);

  const headingByRole = targetPage.getByRole("heading", { name: headingPattern }).first();
  if (await waitForVisible(headingByRole, 10_000)) {
    await expect(headingByRole).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingPattern).first()).toBeVisible({ timeout: 20_000 });
  }

  const legalTextLength = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim().length;
  if (legalTextLength < 150) {
    throw new Error("Legal content looks too short; expected substantial legal text.");
  }

  const shotPath = await screenshot(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close().catch(() => undefined);
    await page.bringToFront().catch(() => undefined);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiLoad(page);
  }

  return { screenshotPath: shotPath, finalUrl };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }) => {
    await mkdir(SCREENSHOT_DIR, { recursive: true });

    const results = createInitialResults();
    const legalUrls: Record<string, string> = {};
    let reportPersisted = false;

    try {
      const loginOk = await runStep("Login", results, async () => {
        const configuredLoginUrl = process.env.SALEADS_LOGIN_URL;
        if (configuredLoginUrl) {
          await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
          await waitForUiLoad(page);
        } else if (page.url() === "about:blank") {
          throw new Error(
            "Set SALEADS_LOGIN_URL or start the browser on the SaleADS login page before running this test.",
          );
        }

        const loginPopupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
        await clickByVisibleText(page, /google|sign in with google|continuar con google|iniciar sesion/i);
        const loginPopup = await loginPopupPromise;
        await chooseGoogleAccountIfVisible(page, context, loginPopup);

        await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 60_000 });
        const sidebar = page.locator("aside, nav").filter({ hasText: /negocio/i }).first();
        await expect(sidebar).toBeVisible({ timeout: 30_000 });

        const shot = await screenshot(page, "01-dashboard-loaded.png", true);
        return [shot];
      });

      const menuOk = loginOk
        ? await runStep("Mi Negocio menu", results, async () => {
            await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 20_000 });
            await clickByVisibleText(page, /mi negocio/i);

            await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 20_000 });
            await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 20_000 });

            const shot = await screenshot(page, "02-mi-negocio-menu-expanded.png");
            return [shot];
          })
        : (markBlocked("Mi Negocio menu", results, "Login step failed."), false);

      const modalOk = menuOk
        ? await runStep("Agregar Negocio modal", results, async () => {
            await clickByVisibleText(page, /agregar negocio/i);

            await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 20_000 });

            const inputByLabel = page.getByLabel(/nombre del negocio/i).first();
            const inputByPlaceholder = page.getByPlaceholder(/nombre del negocio/i).first();
            const useLabelInput = await waitForVisible(inputByLabel, 5_000);
            const nameInput = useLabelInput ? inputByLabel : inputByPlaceholder;
            await expect(nameInput).toBeVisible({ timeout: 10_000 });

            await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 15_000 });
            await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible({ timeout: 10_000 });
            await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible({ timeout: 10_000 });

            const shot = await screenshot(page, "03-agregar-negocio-modal.png");

            await nameInput.click();
            await nameInput.fill("Negocio Prueba Automatizacion");
            await clickByVisibleText(page, /cancelar/i);
            await expect(page.getByText(/crear nuevo negocio/i).first()).toBeHidden({ timeout: 10_000 });

            return [shot];
          })
        : (markBlocked("Agregar Negocio modal", results, "Mi Negocio menu step failed."), false);

      const adminViewOk = loginOk
        ? await runStep("Administrar Negocios view", results, async () => {
            const administrarOption = page.getByText(/administrar negocios/i).first();
            if (!(await waitForVisible(administrarOption, 3_000))) {
              await clickByVisibleText(page, /mi negocio/i);
            }

            await clickByVisibleText(page, /administrar negocios/i);

            await expect(page.getByText(/informacion general/i).first()).toBeVisible({ timeout: 30_000 });
            await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 30_000 });
            await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 30_000 });
            await expect(page.getByText(/seccion legal/i).first()).toBeVisible({ timeout: 30_000 });

            const shot = await screenshot(page, "04-administrar-negocios-view.png", true);
            return [shot];
          })
        : (markBlocked("Administrar Negocios view", results, "Login step failed."), false);

      const infoOk = adminViewOk
        ? await runStep("Informacion General", results, async () => {
            const infoSection = page.locator("section, div, article").filter({ hasText: /informacion general/i }).first();
            await expect(infoSection).toBeVisible({ timeout: 20_000 });

            const emailLocator = infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
            await expect(emailLocator).toBeVisible({ timeout: 20_000 });

            const sectionText = (await infoSection.innerText()).split("\n").map((line) => line.trim()).filter(Boolean);
            const likelyName = sectionText.find(
              (line) =>
                !/informacion general|business plan|cambiar plan|@|cuenta|idioma|estado/i.test(line) &&
                line.length > 2,
            );
            if (!likelyName) {
              throw new Error("Could not confidently identify user name in Informacion General section.");
            }

            await expect(infoSection.getByText(/business plan/i).first()).toBeVisible({ timeout: 15_000 });
            await expect(infoSection.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({
              timeout: 15_000,
            });

            return [];
          })
        : (markBlocked("Informacion General", results, "Administrar Negocios view step failed."), false);

      const detailsOk = adminViewOk
        ? await runStep("Detalles de la Cuenta", results, async () => {
            const detailsSection = page
              .locator("section, div, article")
              .filter({ hasText: /detalles de la cuenta/i })
              .first();
            await expect(detailsSection).toBeVisible({ timeout: 20_000 });

            await expect(detailsSection.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 15_000 });
            await expect(detailsSection.getByText(/estado activo/i).first()).toBeVisible({ timeout: 15_000 });
            await expect(detailsSection.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 15_000 });

            return [];
          })
        : (markBlocked("Detalles de la Cuenta", results, "Administrar Negocios view step failed."), false);

      const negociosOk = adminViewOk
        ? await runStep("Tus Negocios", results, async () => {
            const negociosSection = page.locator("section, div, article").filter({ hasText: /tus negocios/i }).first();
            await expect(negociosSection).toBeVisible({ timeout: 20_000 });
            await expect(negociosSection.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible({
              timeout: 15_000,
            });
            await expect(negociosSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
              timeout: 15_000,
            });

            const sectionText = (await negociosSection.innerText()).split("\n").map((line) => line.trim()).filter(Boolean);
            const hasBusinessListContent = sectionText.some(
              (line) => !/tus negocios|agregar negocio|tienes\s*2\s*de\s*3\s*negocios/i.test(line),
            );

            if (!hasBusinessListContent) {
              throw new Error("Could not confirm visible business list content in Tus Negocios section.");
            }

            return [];
          })
        : (markBlocked("Tus Negocios", results, "Administrar Negocios view step failed."), false);

      const termsOk = adminViewOk
        ? await runStep("Terminos y Condiciones", results, async () => {
            const termsData = await validateLegalPage(
              page,
              context,
              /terminos y condiciones/i,
              /terminos y condiciones/i,
              "05-terminos-y-condiciones.png",
            );
            legalUrls["Terminos y Condiciones"] = termsData.finalUrl;
            return [termsData.screenshotPath, termsData.finalUrl];
          })
        : (markBlocked("Terminos y Condiciones", results, "Administrar Negocios view step failed."), false);

      const privacyOk = adminViewOk
        ? await runStep("Politica de Privacidad", results, async () => {
            const privacyData = await validateLegalPage(
              page,
              context,
              /politica de privacidad/i,
              /politica de privacidad/i,
              "06-politica-de-privacidad.png",
            );
            legalUrls["Politica de Privacidad"] = privacyData.finalUrl;
            return [privacyData.screenshotPath, privacyData.finalUrl];
          })
        : (markBlocked("Politica de Privacidad", results, "Administrar Negocios view step failed."), false);

      const finalReport = {
        name: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        environment: {
          startUrl: page.url(),
          configuredLoginUrl: process.env.SALEADS_LOGIN_URL ?? null,
        },
        summary: {
          loginCompleted: loginOk,
          menuValidated: menuOk,
          modalValidated: modalOk,
          adminViewValidated: adminViewOk,
          infoValidated: infoOk,
          detailsValidated: detailsOk,
          negociosValidated: negociosOk,
          termsValidated: termsOk,
          privacyValidated: privacyOk,
        },
        legalUrls,
        results,
      };

      await writeFile(REPORT_PATH, JSON.stringify(finalReport, null, 2), "utf8");
      reportPersisted = true;

      const failedSteps = Object.entries(results).filter(([, value]) => value.status === "FAIL");
      expect(
        failedSteps,
        `One or more SaleADS Mi Negocio validations failed.\n${JSON.stringify(results, null, 2)}`,
      ).toEqual([]);
    } finally {
      if (!reportPersisted) {
        await mkdir(ARTIFACTS_DIR, { recursive: true });
        const fallbackReport = {
          name: "saleads_mi_negocio_full_test",
          generatedAt: new Date().toISOString(),
          results,
        };
        await writeFile(REPORT_PATH, JSON.stringify(fallbackReport, null, 2), "utf8").catch(() => undefined);
      }
    }
  });
});
