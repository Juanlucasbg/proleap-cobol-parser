import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  id: number;
  label: string;
  status: StepStatus;
  details: string[];
};

const SCREENSHOT_DIR = path.resolve(process.cwd(), "artifacts", "screenshots");
const REPORT_DIR = path.resolve(process.cwd(), "artifacts", "reports");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad"
] as const;

function normalize(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function regexFromText(phrase: string): RegExp {
  const normalized = normalize(phrase).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(normalized, "i");
}

async function waitForStableUI(page: Page, ms = 600): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await page.waitForTimeout(ms);
}

async function capture(page: Page, name: string, fullPage = false): Promise<string> {
  const file = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage });
  return file;
}

async function expectVisible(label: string, locator: Locator, details: string[]): Promise<void> {
  try {
    await expect(locator.first(), label).toBeVisible({ timeout: 20000 });
    details.push(`OK: ${label}`);
  } catch (error) {
    details.push(`FAIL: ${label} (${String(error)})`);
    throw error;
  }
}

function preferTextLocator(page: Page, text: string): Locator {
  return page.getByText(new RegExp(text, "i")).first();
}

async function expectNormalizedTextInBody(page: Page, phrase: string, details: string[]): Promise<void> {
  const bodyText = normalize(await page.locator("body").innerText());
  const expected = normalize(phrase);
  if (!bodyText.includes(expected)) {
    details.push(`FAIL: "${phrase}" not found in normalized body text.`);
    throw new Error(`Normalized text "${phrase}" not found.`);
  }
  details.push(`OK: "${phrase}" found in normalized body text.`);
}

async function clickFirstVisible(page: Page, candidates: (() => Locator)[]): Promise<Locator> {
  for (const build of candidates) {
    const candidate = build().first();
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click();
      await waitForStableUI(page);
      return candidate;
    }
  }
  throw new Error("No visible candidate found to click.");
}

function getBaseUrl(): string | undefined {
  const envUrl = process.env.SALEADS_BASE_URL?.trim();
  return envUrl || undefined;
}

function writeJsonReport(reportName: string, payload: unknown): string {
  const file = path.join(REPORT_DIR, `${reportName}.json`);
  fs.writeFileSync(file, JSON.stringify(payload, null, 2), "utf8");
  return file;
}

test.describe("SaleADS - Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }) => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    fs.mkdirSync(REPORT_DIR, { recursive: true });

    const startedAt = new Date().toISOString();
    const baseUrl = getBaseUrl();
    const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

    const results: StepResult[] = [];
    const reportStatus: Record<(typeof REPORT_FIELDS)[number], StepStatus> = {
      Login: "FAIL",
      "Mi Negocio menu": "FAIL",
      "Agregar Negocio modal": "FAIL",
      "Administrar Negocios view": "FAIL",
      "Informacion General": "FAIL",
      "Detalles de la Cuenta": "FAIL",
      "Tus Negocios": "FAIL",
      "Terminos y Condiciones": "FAIL",
      "Politica de Privacidad": "FAIL"
    };

    const addResult = (id: number, label: string, status: StepStatus, details: string[]) => {
      results.push({ id, label, status, details });
    };

    const appPage = page;

    // Step 1: Login with Google
    {
      const details: string[] = [];
      let status: StepStatus = "PASS";
      try {
        if (baseUrl) {
          await appPage.goto(baseUrl, { waitUntil: "domcontentloaded" });
          await waitForStableUI(appPage);
          details.push(`Opened app with base URL: ${baseUrl}`);
        } else {
          details.push(
            `SALEADS_BASE_URL not provided. Continuing on existing page: ${appPage.url() || "about:blank"}`
          );
        }

        const hasSidebarBefore = await appPage
          .locator(
            [
              "aside",
              "nav[aria-label*='sidebar' i]",
              "[data-testid*='sidebar' i]",
              "[class*='sidebar' i]"
            ].join(",")
          )
          .first()
          .isVisible()
          .catch(() => false);

        if (!hasSidebarBefore) {
          const maybePopup = appPage
            .context()
            .waitForEvent("page", { timeout: 15000 })
            .catch(() => null);

          await clickFirstVisible(appPage, [
            () =>
              appPage.getByRole("button", {
                name: /sign in with google|login with google|continuar con google|iniciar sesi[oó]n con google/i
              }),
            () =>
              appPage.getByRole("link", {
                name: /sign in with google|login with google|continuar con google|iniciar sesi[oó]n con google/i
              }),
            () => appPage.locator("button:has-text('Google')"),
            () => appPage.locator("[role='button']:has-text('Google')"),
            () => appPage.locator("text=/Google/i")
          ]);
          details.push("Clicked Google login trigger.");

          const popup = await maybePopup;
          if (popup) {
            await popup.waitForLoadState("domcontentloaded");
            const accountOption = popup.getByText(new RegExp(googleAccount.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"));
            if (await accountOption.first().isVisible().catch(() => false)) {
              await accountOption.first().click();
              details.push(`Selected Google account ${googleAccount} in popup.`);
            } else {
              details.push("Google account selector not visible; likely already authenticated.");
            }
            await popup.waitForLoadState("networkidle").catch(() => undefined);
            if (!popup.isClosed()) {
              await popup.waitForTimeout(1000);
            }
          } else {
            const accountOptionInline = appPage.getByText(
              new RegExp(googleAccount.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")
            );
            if (await accountOptionInline.first().isVisible().catch(() => false)) {
              await accountOptionInline.first().click();
              details.push(`Selected Google account ${googleAccount} inline.`);
            } else {
              details.push("No account selection step needed.");
            }
          }
        } else {
          details.push("Sidebar already visible; session appears already authenticated.");
        }

        await waitForStableUI(appPage, 1000);

        const appHeader = appPage.locator(
          [
            "aside",
            "nav",
            "[class*='sidebar' i]",
            "[data-testid*='sidebar' i]",
            "[role='navigation']"
          ].join(",")
        );
        await expectVisible("main application navigation visible", appHeader, details);

        await capture(appPage, "01-dashboard-loaded");
        details.push("Captured screenshot: 01-dashboard-loaded.png");
      } catch (error) {
        status = "FAIL";
        await capture(appPage, "01-dashboard-loaded-fail").catch(() => undefined);
        details.push(`Step failed: ${String(error)}`);
      }
      reportStatus.Login = status;
      addResult(1, "Login", status, details);
    }

    // Step 2: Open Mi Negocio menu
    {
      const details: string[] = [];
      let status: StepStatus = "PASS";
      try {
        await expectVisible("Negocio section visible", preferTextLocator(appPage, "Negocio"), details);
        await clickFirstVisible(appPage, [
          () => appPage.getByRole("button", { name: /mi negocio/i }),
          () => appPage.getByRole("link", { name: /mi negocio/i }),
          () => appPage.getByText(/mi negocio/i),
          () => appPage.getByText(/negocio/i)
        ]);
        details.push("Opened Mi Negocio menu.");

        await expectVisible("Agregar Negocio visible", preferTextLocator(appPage, "Agregar Negocio"), details);
        await expectVisible("Administrar Negocios visible", preferTextLocator(appPage, "Administrar Negocios"), details);

        await capture(appPage, "02-mi-negocio-expanded");
        details.push("Captured screenshot: 02-mi-negocio-expanded.png");
      } catch (error) {
        status = "FAIL";
        await capture(appPage, "02-mi-negocio-expanded-fail").catch(() => undefined);
        details.push(`Step failed: ${String(error)}`);
      }
      reportStatus["Mi Negocio menu"] = status;
      addResult(2, "Mi Negocio menu", status, details);
    }

    // Step 3: Validate Agregar Negocio modal
    {
      const details: string[] = [];
      let status: StepStatus = "PASS";
      try {
        await clickFirstVisible(appPage, [
          () => appPage.getByRole("button", { name: /agregar negocio/i }),
          () => appPage.getByRole("link", { name: /agregar negocio/i }),
          () => appPage.getByText(/agregar negocio/i)
        ]);
        details.push("Clicked Agregar Negocio.");

        await expectVisible("Modal title Crear Nuevo Negocio", preferTextLocator(appPage, "Crear Nuevo Negocio"), details);
        const namedInput = appPage.getByRole("textbox", { name: /nombre del negocio/i }).first();
        const placeholderInput = appPage.getByPlaceholder(/nombre del negocio/i).first();
        const inputLocator = (await namedInput.isVisible().catch(() => false)) ? namedInput : placeholderInput;
        await expectVisible("Input Nombre del Negocio", inputLocator, details);
        await expectVisible(
          "Text Tienes 2 de 3 negocios",
          appPage.getByText(new RegExp("tienes\\s+2\\s+de\\s+3\\s+negocios", "i")),
          details
        );
        await expectVisible("Boton Cancelar", appPage.getByRole("button", { name: /cancelar/i }), details);
        await expectVisible("Boton Crear Negocio", appPage.getByRole("button", { name: /crear negocio/i }), details);

        await capture(appPage, "03-agregar-negocio-modal");
        details.push("Captured screenshot: 03-agregar-negocio-modal.png");

        // Optional actions requested by the scenario.
        const nameInput = inputLocator;
        await nameInput.click();
        await nameInput.fill("Negocio Prueba Automatizacion");
        details.push("Filled Nombre del Negocio with test value.");

        await appPage.getByRole("button", { name: /cancelar/i }).click();
        await waitForStableUI(appPage);
        details.push("Closed modal with Cancelar.");
      } catch (error) {
        status = "FAIL";
        await capture(appPage, "03-agregar-negocio-modal-fail").catch(() => undefined);
        details.push(`Step failed: ${String(error)}`);
      }
      reportStatus["Agregar Negocio modal"] = status;
      addResult(3, "Agregar Negocio modal", status, details);
    }

    // Step 4: Open Administrar Negocios
    {
      const details: string[] = [];
      let status: StepStatus = "PASS";
      try {
        const adminEntry = appPage.getByText(/administrar negocios/i).first();
        if (!(await adminEntry.isVisible().catch(() => false))) {
          await clickFirstVisible(appPage, [
            () => appPage.getByRole("button", { name: /mi negocio/i }),
            () => appPage.getByRole("link", { name: /mi negocio/i }),
            () => appPage.getByText(/mi negocio/i)
          ]);
          details.push("Re-expanded Mi Negocio menu.");
        }

        await clickFirstVisible(appPage, [
          () => appPage.getByRole("link", { name: /administrar negocios/i }),
          () => appPage.getByRole("button", { name: /administrar negocios/i }),
          () => appPage.getByText(/administrar negocios/i)
        ]);
        details.push("Opened Administrar Negocios.");

        await expectNormalizedTextInBody(appPage, "Informacion General", details);
        await expectNormalizedTextInBody(appPage, "Detalles de la Cuenta", details);
        await expectNormalizedTextInBody(appPage, "Tus Negocios", details);
        await expectVisible("Seccion Legal section", appPage.getByText(/seccion legal/i).first(), details);

        await capture(appPage, "04-administrar-negocios", true);
        details.push("Captured screenshot: 04-administrar-negocios.png");
      } catch (error) {
        status = "FAIL";
        await capture(appPage, "04-administrar-negocios-fail", true).catch(() => undefined);
        details.push(`Step failed: ${String(error)}`);
      }
      reportStatus["Administrar Negocios view"] = status;
      addResult(4, "Administrar Negocios view", status, details);
    }

    // Step 5: Validate Informacion General
    {
      const details: string[] = [];
      let status: StepStatus = "PASS";
      try {
        const pageText = (await appPage.locator("body").innerText()).slice(0, 50000);
        const bodyNormalized = normalize(pageText);
        if (!bodyNormalized.includes(normalize("business plan"))) {
          throw new Error("BUSINESS PLAN text not found.");
        }
        details.push("BUSINESS PLAN text found.");

        await expectVisible("Cambiar Plan button", appPage.getByRole("button", { name: /cambiar plan/i }), details);

        const userNameHint = process.env.SALEADS_EXPECTED_USER_NAME;
        const userEmailHint = process.env.SALEADS_EXPECTED_USER_EMAIL;
        if (userNameHint) {
          await expectVisible(
            `User name visible (${userNameHint})`,
            appPage.getByText(new RegExp(userNameHint.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
            details
          );
        } else {
          details.push("User name hint not provided; skipping exact name assertion.");
        }
        if (userEmailHint) {
          await expectVisible(
            `User email visible (${userEmailHint})`,
            appPage.getByText(new RegExp(userEmailHint.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
            details
          );
        } else {
          // Validate an email exists when explicit value is not provided.
          await expectVisible(
            "Some email is visible",
            appPage.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
            details
          );
          details.push("Validated visible email via generic pattern.");

          const lines = pageText
            .split(/\r?\n/)
            .map((line) => line.trim())
            .filter((line) => line.length > 1);
          const emailLineIndex = lines.findIndex((line) => /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(line));
          if (emailLineIndex === -1) {
            throw new Error("No visible email line found for user identity validation.");
          }
          const nearby = lines.slice(Math.max(0, emailLineIndex - 3), Math.min(lines.length, emailLineIndex + 4));
          const likelyNameLine = nearby.find(
            (line) => !/@/.test(line) && /^[\p{L}][\p{L}\s.'-]{2,}$/u.test(line) && line.length <= 70
          );
          if (!likelyNameLine) {
            throw new Error("Could not infer a visible user name near the email.");
          }
          details.push(`Validated inferred user name visibility via nearby text: "${likelyNameLine}".`);
        }
      } catch (error) {
        status = "FAIL";
        details.push(`Step failed: ${String(error)}`);
      }
      reportStatus["Informacion General"] = status;
      addResult(5, "Informacion General", status, details);
    }

    // Step 6: Validate Detalles de la Cuenta
    {
      const details: string[] = [];
      let status: StepStatus = "PASS";
      try {
        await expectNormalizedTextInBody(appPage, "Cuenta creada", details);
        await expectNormalizedTextInBody(appPage, "Estado activo", details);
        await expectNormalizedTextInBody(appPage, "Idioma seleccionado", details);
      } catch (error) {
        status = "FAIL";
        details.push(`Step failed: ${String(error)}`);
      }
      reportStatus["Detalles de la Cuenta"] = status;
      addResult(6, "Detalles de la Cuenta", status, details);
    }

    // Step 7: Validate Tus Negocios
    {
      const details: string[] = [];
      let status: StepStatus = "PASS";
      try {
        await expectNormalizedTextInBody(appPage, "Tus Negocios", details);
        await expectVisible(
          "Agregar Negocio button exists",
          appPage.getByRole("button", { name: /agregar negocio/i }).first(),
          details
        );
        await expectVisible(
          "Tienes 2 de 3 negocios visible",
          appPage.getByText(new RegExp("tienes\\s+2\\s+de\\s+3\\s+negocios", "i")),
          details
        );
      } catch (error) {
        status = "FAIL";
        details.push(`Step failed: ${String(error)}`);
      }
      reportStatus["Tus Negocios"] = status;
      addResult(7, "Tus Negocios", status, details);
    }

    async function validateLegalDestination(stepId: number, label: "Terminos y Condiciones" | "Politica de Privacidad", linkText: string) {
      const details: string[] = [];
      let status: StepStatus = "PASS";
      let finalUrl = "";
      try {
        const linkLocator = appPage.getByRole("link", { name: new RegExp(linkText, "i") }).first();
        const clickTarget = (await linkLocator.isVisible().catch(() => false))
          ? linkLocator
          : appPage.getByText(new RegExp(linkText, "i")).first();

        const previousPageCount = context.pages().length;
        await clickTarget.click();
        await waitForStableUI(appPage);

        let targetPage = appPage;
        if (context.pages().length > previousPageCount) {
          targetPage = context.pages()[context.pages().length - 1];
          await targetPage.waitForLoadState("domcontentloaded");
          await targetPage.waitForLoadState("networkidle").catch(() => undefined);
          details.push("Legal content opened in new tab.");
        } else {
          details.push("Legal content opened in same tab.");
        }

        const bodyText = normalize(await targetPage.locator("body").innerText());
        const expectedHeadingRegex =
          label === "Terminos y Condiciones"
            ? regexFromText("Terminos y Condiciones")
            : regexFromText("Politica de Privacidad");

        if (!expectedHeadingRegex.test(bodyText)) {
          throw new Error(`Expected heading for "${label}" not found in destination.`);
        }
        details.push(`Found heading for ${label}.`);

        if (bodyText.length < 80) {
          throw new Error("Legal content seems too short.");
        }
        details.push("Legal content text is visible.");

        finalUrl = targetPage.url();
        details.push(`Final URL: ${finalUrl}`);

        const shotName = label === "Terminos y Condiciones" ? "08-terminos-y-condiciones" : "09-politica-de-privacidad";
        await capture(targetPage, shotName, true);
        details.push(`Captured screenshot: ${shotName}.png`);

        if (targetPage !== appPage) {
          await targetPage.close().catch(() => undefined);
          await appPage.bringToFront();
          await waitForStableUI(appPage);
          details.push("Returned to application tab.");
        } else {
          await appPage.goBack().catch(() => undefined);
          await waitForStableUI(appPage);
          details.push("Navigated back to application page.");
        }
      } catch (error) {
        status = "FAIL";
        details.push(`Step failed: ${String(error)}`);
      }
      reportStatus[label] = status;
      addResult(stepId, label, status, details);
      return finalUrl;
    }

    // Step 8 + 9: legal links
    const terminosUrl = await validateLegalDestination(8, "Terminos y Condiciones", "T[ée]rminos y Condiciones");
    const politicaUrl = await validateLegalDestination(9, "Politica de Privacidad", "Pol[íi]tica de Privacidad");

    // Step 10: final report
    const finishedAt = new Date().toISOString();
    const summary = {
      testName: "saleads_mi_negocio_full_test",
      startedAt,
      finishedAt,
      baseUrl,
      googleAccount,
      report: {
        Login: reportStatus.Login,
        "Mi Negocio menu": reportStatus["Mi Negocio menu"],
        "Agregar Negocio modal": reportStatus["Agregar Negocio modal"],
        "Administrar Negocios view": reportStatus["Administrar Negocios view"],
        "Informacion General": reportStatus["Informacion General"],
        "Detalles de la Cuenta": reportStatus["Detalles de la Cuenta"],
        "Tus Negocios": reportStatus["Tus Negocios"],
        "Terminos y Condiciones": reportStatus["Terminos y Condiciones"],
        "Politica de Privacidad": reportStatus["Politica de Privacidad"]
      },
      legalUrls: {
        terminosUrl,
        politicaUrl
      },
      steps: results
    };

    const reportPath = writeJsonReport("mi-negocio-workflow-report", summary);
    test.info().annotations.push({
      type: "workflow-report",
      description: reportPath
    });

    const failedSteps = results.filter((step) => step.status === "FAIL");
    expect(
      failedSteps,
      `Failed steps: ${failedSteps.map((step) => `${step.id}-${step.label}`).join(", ")}`
    ).toHaveLength(0);
  });
});
