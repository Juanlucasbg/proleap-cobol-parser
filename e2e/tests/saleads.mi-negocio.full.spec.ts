import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import { promises as fs } from "node:fs";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

type ReportKey =
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
  details?: string;
  evidence?: Record<string, string>;
};

type FinalReport = Record<ReportKey, StepResult>;

function initReport(): FinalReport {
  return {
    "Login": { status: "FAIL", details: "Step not executed." },
    "Mi Negocio menu": { status: "FAIL", details: "Step not executed." },
    "Agregar Negocio modal": { status: "FAIL", details: "Step not executed." },
    "Administrar Negocios view": { status: "FAIL", details: "Step not executed." },
    "Información General": { status: "FAIL", details: "Step not executed." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Step not executed." },
    "Tus Negocios": { status: "FAIL", details: "Step not executed." },
    "Términos y Condiciones": { status: "FAIL", details: "Step not executed." },
    "Política de Privacidad": { status: "FAIL", details: "Step not executed." }
  };
}

function asErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  return null;
}

async function clickFirstVisible(candidates: Locator[], label: string): Promise<Locator> {
  const target = await firstVisible(candidates);
  if (!target) {
    throw new Error(`Could not find visible element for: ${label}`);
  }

  await target.click();
  await waitForUi(target.page());
  return target;
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<string> {
  const filePath = testInfo.outputPath(name);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function markBlocked(report: FinalReport, key: ReportKey, reason: string): void {
  report[key] = {
    status: "FAIL",
    details: reason
  };
}

async function runStep(
  report: FinalReport,
  key: ReportKey,
  work: () => Promise<Record<string, string> | undefined>
): Promise<boolean> {
  try {
    const evidence = await work();
    report[key] = {
      status: "PASS",
      ...(evidence ? { evidence } : {})
    };
    return true;
  } catch (error) {
    report[key] = {
      status: "FAIL",
      details: asErrorMessage(error)
    };
    return false;
  }
}

async function validateLegalLink(
  page: Page,
  testInfo: TestInfo,
  linkRegex: RegExp,
  headingRegex: RegExp,
  screenshotName: string,
  returnUrl: string
): Promise<{ screenshot: string; finalUrl: string }> {
  const popupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
  const link = await clickFirstVisible(
    [
      page.getByRole("link", { name: linkRegex }).first(),
      page.getByRole("button", { name: linkRegex }).first(),
      page.getByText(linkRegex).first()
    ],
    linkRegex.source
  );

  const popup = await popupPromise;
  const legalPage = popup ?? link.page();

  await legalPage.waitForLoadState("domcontentloaded");

  const heading = await firstVisible([
    legalPage.getByRole("heading", { name: headingRegex }).first(),
    legalPage.getByText(headingRegex).first()
  ]);
  if (!heading) {
    throw new Error(`Could not find legal heading: ${headingRegex.source}`);
  }
  await expect(heading).toBeVisible();

  const legalContent = await firstVisible([
    legalPage.locator("main p, main li, article p, article li").first(),
    legalPage.locator("body p, body li").first()
  ]);
  if (!legalContent) {
    throw new Error(`No legal content text found for: ${headingRegex.source}`);
  }
  await expect(legalContent).toBeVisible();

  const screenshot = await captureCheckpoint(legalPage, testInfo, screenshotName);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== returnUrl) {
    await page.goto(returnUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  return { screenshot, finalUrl };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("Login with Google and validate Mi Negocio module workflow", async ({ page }, testInfo) => {
    const report = initReport();

    const loginOk = await runStep(report, "Login", async () => {
      const configuredLoginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
      if (configuredLoginUrl) {
        await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
        await waitForUi(page);
      }

      if (page.url() === "about:blank") {
        throw new Error(
          "Browser started on about:blank. Provide SALEADS_LOGIN_URL/SALEADS_BASE_URL or preload login page in the harness."
        );
      }

      await clickFirstVisible(
        [
          page.getByRole("button", { name: /sign in with google|continuar con google|iniciar sesi[oó]n con google|google/i }).first(),
          page.getByRole("link", { name: /sign in with google|continuar con google|iniciar sesi[oó]n con google|google/i }).first(),
          page.getByText(/sign in with google|continuar con google|iniciar sesi[oó]n con google|google/i).first()
        ],
        "Sign in with Google"
      );

      const accountSelector = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
      if (await accountSelector.isVisible().catch(() => false)) {
        await accountSelector.click();
        await waitForUi(page);
      }

      await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({ timeout: 45_000 });

      const sidebar = await firstVisible([page.locator("aside").first(), page.getByRole("navigation").first()]);
      if (!sidebar) {
        throw new Error("Could not find left sidebar navigation after login.");
      }
      await expect(sidebar).toBeVisible();

      const dashboardScreenshot = await captureCheckpoint(
        page,
        testInfo,
        "checkpoint-01-dashboard-loaded.png"
      );
      return { dashboardScreenshot };
    });

    const menuOk = loginOk
      ? await runStep(report, "Mi Negocio menu", async () => {
          const sidebar = page.locator("aside, nav").first();
          await expect(sidebar).toBeVisible();

          const negocioLabel = await firstVisible([
            page.getByRole("button", { name: /^negocio$/i }).first(),
            page.getByRole("link", { name: /^negocio$/i }).first(),
            page.getByText(/^negocio$/i).first()
          ]);
          if (negocioLabel) {
            await negocioLabel.click();
            await waitForUi(page);
          }

          await clickFirstVisible(
            [
              page.getByRole("button", { name: /mi negocio/i }).first(),
              page.getByRole("link", { name: /mi negocio/i }).first(),
              page.getByText(/mi negocio/i).first()
            ],
            "Mi Negocio"
          );

          await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
          await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

          const menuScreenshot = await captureCheckpoint(
            page,
            testInfo,
            "checkpoint-02-mi-negocio-expanded-menu.png"
          );
          return { menuScreenshot };
        })
      : false;
    if (!loginOk) {
      markBlocked(report, "Mi Negocio menu", "Blocked because login step failed.");
    }

    const modalOk = menuOk
      ? await runStep(report, "Agregar Negocio modal", async () => {
          await clickFirstVisible(
            [
              page.getByRole("menuitem", { name: /agregar negocio/i }).first(),
              page.getByRole("link", { name: /agregar negocio/i }).first(),
              page.getByRole("button", { name: /agregar negocio/i }).first(),
              page.getByText(/agregar negocio/i).first()
            ],
            "Agregar Negocio"
          );

          const modal = page.getByRole("dialog").first();
          await expect(modal).toBeVisible();
          await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
          const nombreNegocioInput = await firstVisible([
            modal.getByLabel(/nombre del negocio/i),
            modal.getByPlaceholder(/nombre del negocio/i),
            modal.locator("input").first()
          ]);
          if (!nombreNegocioInput) {
            throw new Error("Could not find 'Nombre del Negocio' input field in modal.");
          }
          await expect(nombreNegocioInput).toBeVisible();
          await expect(modal.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
          await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
          await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

          const modalScreenshot = await captureCheckpoint(
            page,
            testInfo,
            "checkpoint-03-agregar-negocio-modal.png"
          );

          await nombreNegocioInput.click();
          await nombreNegocioInput.fill("Negocio Prueba Automatización");
          await modal.getByRole("button", { name: /cancelar/i }).click();
          await waitForUi(page);
          return { modalScreenshot };
        })
      : false;
    if (!menuOk) {
      markBlocked(report, "Agregar Negocio modal", "Blocked because Mi Negocio menu step failed.");
    }

    const accountViewOk = modalOk
      ? await runStep(report, "Administrar Negocios view", async () => {
          const adminOption = page.getByText(/administrar negocios/i).first();
          if (!(await adminOption.isVisible().catch(() => false))) {
            await clickFirstVisible(
              [
                page.getByRole("button", { name: /mi negocio/i }).first(),
                page.getByRole("link", { name: /mi negocio/i }).first(),
                page.getByText(/mi negocio/i).first()
              ],
              "Mi Negocio"
            );
          }

          await clickFirstVisible(
            [
              page.getByRole("menuitem", { name: /administrar negocios/i }).first(),
              page.getByRole("link", { name: /administrar negocios/i }).first(),
              page.getByRole("button", { name: /administrar negocios/i }).first(),
              page.getByText(/administrar negocios/i).first()
            ],
            "Administrar Negocios"
          );

          await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
          await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
          await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
          await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

          const accountPageScreenshot = await captureCheckpoint(
            page,
            testInfo,
            "checkpoint-04-administrar-negocios-full-page.png",
            true
          );
          return { accountPageScreenshot };
        })
      : false;
    if (!modalOk) {
      markBlocked(
        report,
        "Administrar Negocios view",
        "Blocked because Agregar Negocio modal step failed."
      );
    }

    const infoGeneralOk = accountViewOk
      ? await runStep(report, "Información General", async () => {
          const section = page.locator("section, div").filter({ hasText: /informaci[oó]n general/i }).first();
          await expect(section).toBeVisible();

          const sectionText = (await section.innerText()).trim();
          const hasName = sectionText
            .split(/\n+/)
            .map((line) => line.trim())
            .some(
              (line) =>
                /^[\p{L}][\p{L}\s'.-]{2,}$/u.test(line) &&
                !/@/.test(line) &&
                !/informaci[oó]n general|business plan|cambiar plan/i.test(line)
            );

          if (!hasName) {
            throw new Error("Could not confirm visible user name in Información General section.");
          }

          await expect(section.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
          await expect(section.getByText(/business plan/i)).toBeVisible();
          await expect(section.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
          return {};
        })
      : false;
    if (!accountViewOk) {
      markBlocked(report, "Información General", "Blocked because Administrar Negocios view step failed.");
    }

    const detallesOk = infoGeneralOk
      ? await runStep(report, "Detalles de la Cuenta", async () => {
          const section = page.locator("section, div").filter({ hasText: /detalles de la cuenta/i }).first();
          await expect(section).toBeVisible();
          await expect(section.getByText(/cuenta creada/i)).toBeVisible();
          await expect(section.getByText(/estado activo/i)).toBeVisible();
          await expect(section.getByText(/idioma seleccionado/i)).toBeVisible();
          return {};
        })
      : false;
    if (!infoGeneralOk) {
      markBlocked(report, "Detalles de la Cuenta", "Blocked because Información General step failed.");
    }

    const negociosOk = detallesOk
      ? await runStep(report, "Tus Negocios", async () => {
          const section = page.locator("section, div").filter({ hasText: /tus negocios/i }).first();
          await expect(section).toBeVisible();

          const sectionText = (await section.innerText()).trim();
          if (sectionText.length < 10) {
            throw new Error("Tus Negocios section appears empty.");
          }

          await expect(section.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
          await expect(section.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
          return {};
        })
      : false;
    if (!detallesOk) {
      markBlocked(report, "Tus Negocios", "Blocked because Detalles de la Cuenta step failed.");
    }

    const appReturnUrl = page.url();

    const termsOk = negociosOk
      ? await runStep(report, "Términos y Condiciones", async () => {
          const legalValidation = await validateLegalLink(
            page,
            testInfo,
            /t[ée]rminos y condiciones/i,
            /t[ée]rminos y condiciones/i,
            "checkpoint-08-terminos-y-condiciones.png",
            appReturnUrl
          );

          return {
            screenshot: legalValidation.screenshot,
            finalUrl: legalValidation.finalUrl
          };
        })
      : false;
    if (!negociosOk) {
      markBlocked(report, "Términos y Condiciones", "Blocked because Tus Negocios step failed.");
    }

    const privacyOk = termsOk
      ? await runStep(report, "Política de Privacidad", async () => {
          const legalValidation = await validateLegalLink(
            page,
            testInfo,
            /pol[ií]tica de privacidad/i,
            /pol[ií]tica de privacidad/i,
            "checkpoint-09-politica-de-privacidad.png",
            appReturnUrl
          );

          return {
            screenshot: legalValidation.screenshot,
            finalUrl: legalValidation.finalUrl
          };
        })
      : false;
    if (!termsOk) {
      markBlocked(
        report,
        "Política de Privacidad",
        "Blocked because Términos y Condiciones step failed."
      );
    }

    const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: reportPath,
      contentType: "application/json"
    });

    const failures = Object.entries(report).filter(([, result]) => result.status === "FAIL");
    expect(failures, `Final report contains failed validations: ${JSON.stringify(failures, null, 2)}`).toHaveLength(0);
    expect(privacyOk).toBeTruthy();
  });
});
