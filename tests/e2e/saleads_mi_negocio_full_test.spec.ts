import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";

type StepStatus = "PASS" | "FAIL";

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
  status: StepStatus;
  details: string;
  evidence?: string[];
  url?: string;
};

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function firstVisible(page: Page, candidates: Locator[]): Promise<Locator> {
  for (const candidate of candidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      return candidate.first();
    }
  }

  throw new Error("No visible candidate locator found.");
}

async function takeEvidence(
  page: Page,
  testInfo: TestInfo,
  filename: string,
  fullPage = false
): Promise<string> {
  const evidenceDir = testInfo.outputPath("evidence");
  await mkdir(evidenceDir, { recursive: true });
  const screenshotPath = `${evidenceDir}/${filename}`;
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(filename, { path: screenshotPath, contentType: "image/png" });
  return screenshotPath;
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
  const report: Record<ReportKey, StepResult> = {
    Login: { status: "FAIL", details: "Not executed." },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed." },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed." },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed." },
    "Información General": { status: "FAIL", details: "Not executed." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed." },
    "Tus Negocios": { status: "FAIL", details: "Not executed." },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed." },
    "Política de Privacidad": { status: "FAIL", details: "Not executed." }
  };

  const runStep = async (name: ReportKey, action: () => Promise<void>): Promise<boolean> => {
    try {
      await action();
      report[name].status = "PASS";
      if (report[name].details === "Not executed.") {
        report[name].details = "Validation passed.";
      }
      return true;
    } catch (error) {
      report[name].status = "FAIL";
      report[name].details = error instanceof Error ? error.message : String(error);
      return false;
    }
  };

  const markDependentStepsAsBlocked = (reason: string): void => {
    (Object.keys(report) as ReportKey[]).forEach((key) => {
      if (report[key].details === "Not executed.") {
        report[key] = {
          status: "FAIL",
          details: `Blocked: ${reason}`
        };
      }
    });
  };

  const providedLoginUrl =
    process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL ?? process.env.BASE_URL;

  if (providedLoginUrl) {
    await page.goto(providedLoginUrl, { waitUntil: "domcontentloaded" });
  }

  if (page.url() === "about:blank") {
    markDependentStepsAsBlocked(
      "No login page available. Provide SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL) to run this test in the target environment."
    );
  } else {
    await runStep("Login", async () => {
      const loginButton = await firstVisible(page, [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /iniciar sesi[óo]n con google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/iniciar sesi[óo]n con google/i)
      ]);

      const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
      await clickAndWait(loginButton, page);
      const popup = await popupPromise;

      const googlePage = popup ?? page;
      await waitForUi(googlePage);

      const accountOption = await firstVisible(googlePage, [
        googlePage.locator(`[data-identifier="${ACCOUNT_EMAIL}"]`),
        googlePage.getByText(ACCOUNT_EMAIL, { exact: false }),
        googlePage.getByRole("link", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
        googlePage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") })
      ]);

      await accountOption.click().catch(() => undefined);
      await waitForUi(googlePage);

      if (popup) {
        await waitForUi(page);
      }

      const sidebar = await firstVisible(page, [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator('[class*="sidebar"]')
      ]);
      await expect(sidebar).toBeVisible();

      const dashboardShot = await takeEvidence(page, testInfo, "01-dashboard-loaded.png");
      report.Login = {
        status: "PASS",
        details: "Main interface and left sidebar are visible after Google login.",
        evidence: [dashboardShot]
      };
    });

    if (report.Login.status === "PASS") {
      await runStep("Mi Negocio menu", async () => {
        const negocioItem = await firstVisible(page, [
          page.getByRole("link", { name: /^Negocio$/i }),
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i)
        ]);
        await clickAndWait(negocioItem, page);

        const miNegocioItem = await firstVisible(page, [
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ]);
        await clickAndWait(miNegocioItem, page);

        await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
        await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();

        const menuShot = await takeEvidence(page, testInfo, "02-mi-negocio-menu-expanded.png");
        report["Mi Negocio menu"] = {
          status: "PASS",
          details: "Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios.",
          evidence: [menuShot]
        };
      });

      await runStep("Agregar Negocio modal", async () => {
        const agregarNegocioMenu = await firstVisible(page, [
          page.getByRole("link", { name: /^Agregar Negocio$/i }),
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i)
        ]);
        await clickAndWait(agregarNegocioMenu, page);

        const modal = page.getByRole("dialog");
        await expect(modal).toBeVisible();
        await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
        const nombreInput = modal.getByLabel(/Nombre del Negocio/i).first();
        await expect(nombreInput).toBeVisible();
        await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
        await expect(modal.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
        await expect(modal.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

        await nombreInput.click();
        await nombreInput.fill("Negocio Prueba Automatización");
        await clickAndWait(modal.getByRole("button", { name: /^Cancelar$/i }), page);

        const modalShot = await takeEvidence(page, testInfo, "03-agregar-negocio-modal.png");
        report["Agregar Negocio modal"] = {
          status: "PASS",
          details:
            "Crear Nuevo Negocio modal validated with required text, field and action buttons; modal closed with Cancelar.",
          evidence: [modalShot]
        };
      });

      await runStep("Administrar Negocios view", async () => {
        const miNegocioItem = await firstVisible(page, [
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ]);
        await clickAndWait(miNegocioItem, page);

        const administrarNegocios = await firstVisible(page, [
          page.getByRole("link", { name: /^Administrar Negocios$/i }),
          page.getByRole("button", { name: /^Administrar Negocios$/i }),
          page.getByText(/^Administrar Negocios$/i)
        ]);
        await clickAndWait(administrarNegocios, page);

        await expect(page.getByText(/^Información General$/i)).toBeVisible();
        await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
        await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
        await expect(page.getByText(/Sección Legal/i)).toBeVisible();

        const accountPageShot = await takeEvidence(
          page,
          testInfo,
          "04-administrar-negocios-account-page.png",
          true
        );
        report["Administrar Negocios view"] = {
          status: "PASS",
          details: "Administrar Negocios page loaded with all expected sections.",
          evidence: [accountPageShot]
        };
      });

      await runStep("Información General", async () => {
        const section = page.locator("section, div").filter({ hasText: /^Información General$/i }).first();
        await expect(section).toBeVisible();
        await expect(section.getByText(/BUSINESS PLAN/i)).toBeVisible();
        await expect(section.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

        const sectionText = await section.innerText();
        if (!/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i.test(sectionText)) {
          throw new Error("User email is not visible in Información General.");
        }

        const lines = sectionText
          .split("\n")
          .map((line) => line.trim())
          .filter(Boolean);
        if (lines.length < 3) {
          throw new Error("User name information is not clearly visible in Información General.");
        }

        report["Información General"] = {
          status: "PASS",
          details: "User name/email, BUSINESS PLAN text and Cambiar Plan button validated."
        };
      });

      await runStep("Detalles de la Cuenta", async () => {
        const section = page.locator("section, div").filter({ hasText: /^Detalles de la Cuenta$/i }).first();
        await expect(section).toBeVisible();
        await expect(section.getByText(/Cuenta creada/i)).toBeVisible();
        await expect(section.getByText(/Estado activo/i)).toBeVisible();
        await expect(section.getByText(/Idioma seleccionado/i)).toBeVisible();

        report["Detalles de la Cuenta"] = {
          status: "PASS",
          details: "Cuenta creada, Estado activo, and Idioma seleccionado texts are visible."
        };
      });

      await runStep("Tus Negocios", async () => {
        const section = page.locator("section, div").filter({ hasText: /^Tus Negocios$/i }).first();
        await expect(section).toBeVisible();
        await expect(section.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
        await expect(section.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

        const listItems = section.locator("li, [role='listitem'], .card, [class*='negocio']");
        if ((await listItems.count()) === 0) {
          throw new Error("Business list is not visible in Tus Negocios section.");
        }

        report["Tus Negocios"] = {
          status: "PASS",
          details: "Business list, Agregar Negocio button, and quota text validated."
        };
      });

      const validateLegalPage = async (
        reportName: "Términos y Condiciones" | "Política de Privacidad",
        linkPattern: RegExp,
        headingPattern: RegExp,
        screenshotName: string
      ): Promise<void> => {
        const appUrl = page.url();
        const legalLink = await firstVisible(page, [
          page.getByRole("link", { name: linkPattern }),
          page.getByText(linkPattern)
        ]);

        const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
        await legalLink.click();
        const popup = await popupPromise;
        const legalPage = popup ?? page;

        await waitForUi(legalPage);
        await expect(legalPage.getByRole("heading", { name: headingPattern })).toBeVisible({
          timeout: 20_000
        });

        const legalContent = await legalPage.locator("body").innerText();
        if (legalContent.trim().length < 200) {
          throw new Error(`Legal content for ${reportName} appears incomplete.`);
        }

        const legalShot = await takeEvidence(legalPage, testInfo, screenshotName, true);
        const legalUrl = legalPage.url();

        report[reportName] = {
          status: "PASS",
          details: `${reportName} page validated with heading and legal content.`,
          evidence: [legalShot],
          url: legalUrl
        };

        if (popup) {
          await popup.close();
          await page.bringToFront();
        } else if (page.url() !== appUrl) {
          if (page.url() !== "about:blank") {
            await page.goBack().catch(() => page.goto(appUrl));
          } else {
            await page.goto(appUrl);
          }
          await waitForUi(page);
        }
      };

      await runStep("Términos y Condiciones", async () => {
        await validateLegalPage(
          "Términos y Condiciones",
          /Términos y Condiciones/i,
          /Términos y Condiciones/i,
          "05-terminos-y-condiciones.png"
        );
      });

      await runStep("Política de Privacidad", async () => {
        await validateLegalPage(
          "Política de Privacidad",
          /Política de Privacidad/i,
          /Política de Privacidad/i,
          "06-politica-de-privacidad.png"
        );
      });
    } else {
      markDependentStepsAsBlocked("Login step failed.");
    }
  }

  const reportPath = testInfo.outputPath("final-report.json");
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

  const failedSteps = Object.entries(report).filter(([, value]) => value.status === "FAIL");
  console.log("Final validation report:", JSON.stringify(report, null, 2));
  expect(
    failedSteps,
    `Workflow failed on ${failedSteps.map(([name]) => name).join(", ")}`
  ).toHaveLength(0);
});
