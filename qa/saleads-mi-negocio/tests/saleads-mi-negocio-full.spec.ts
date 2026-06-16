import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";

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

type StepResult = "PASS" | "FAIL";

interface WorkflowReport {
  generatedAt: string;
  environmentUrl: string;
  status: Record<ReportField, StepResult>;
  evidenceUrls: {
    terminosYCondicionesUrl: string | null;
    politicaDePrivacidadUrl: string | null;
  };
  errors: string[];
}

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function exactTextRegex(text: string): RegExp {
  return new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 4000 }),
    page.waitForTimeout(1200)
  ]);
}

async function clickAndWait(page: Page, target: Locator): Promise<void> {
  const clickable = target.first();
  await expect(clickable).toBeVisible();
  await clickable.click();
  await waitForUi(page);
}

async function firstVisible(candidates: Locator[]): Promise<Locator> {
  for (const candidate of candidates) {
    const count = await candidate.count();
    if (count === 0) {
      continue;
    }

    const visible = candidate.first();
    if (await visible.isVisible().catch(() => false)) {
      return visible;
    }
  }

  throw new Error("No visible element found among candidates.");
}

async function findByVisibleText(root: Page | Locator, text: string | RegExp): Promise<Locator> {
  const candidates = [
    root.getByRole("button", { name: text }),
    root.getByRole("link", { name: text }),
    root.getByRole("menuitem", { name: text }),
    root.getByRole("tab", { name: text }),
    root.getByRole("treeitem", { name: text }),
    root.getByText(text, { exact: typeof text === "string" })
  ];

  return firstVisible(candidates);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<void> {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png"
  });
}

async function openLegalPageAndValidate(options: {
  appPage: Page;
  testInfo: TestInfo;
  linkText: string;
  headingText: string;
  screenshotName: string;
}): Promise<string> {
  const { appPage, testInfo, linkText, headingText, screenshotName } = options;
  const link = await findByVisibleText(appPage, exactTextRegex(linkText));
  const popupPromise = appPage.waitForEvent("popup", { timeout: 9000 }).catch(() => null);

  await clickAndWait(appPage, link);
  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  await waitForUi(targetPage);

  const headingCandidates = [
    targetPage.getByRole("heading", { name: new RegExp(headingText, "i") }),
    targetPage.getByText(new RegExp(headingText, "i"))
  ];
  await expect(await firstVisible(headingCandidates)).toBeVisible();

  const bodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(bodyText.length, `${linkText} page should contain legal content`).toBeGreaterThan(200);

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.goto(process.env.SALEADS_URL as string, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(appPage);
  }

  return finalUrl;
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("login with Google and validate Mi Negocio module", async ({ page }, testInfo) => {
    const environmentUrl = process.env.SALEADS_URL ?? "";
    const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT ?? DEFAULT_GOOGLE_ACCOUNT;

    const report: WorkflowReport = {
      generatedAt: new Date().toISOString(),
      environmentUrl: environmentUrl || "not provided",
      status: {
        Login: "FAIL",
        "Mi Negocio menu": "FAIL",
        "Agregar Negocio modal": "FAIL",
        "Administrar Negocios view": "FAIL",
        "Información General": "FAIL",
        "Detalles de la Cuenta": "FAIL",
        "Tus Negocios": "FAIL",
        "Términos y Condiciones": "FAIL",
        "Política de Privacidad": "FAIL"
      },
      evidenceUrls: {
        terminosYCondicionesUrl: null,
        politicaDePrivacidadUrl: null
      },
      errors: []
    };

    if (!environmentUrl) {
      throw new Error(
        "SALEADS_URL is required. This test is environment-agnostic and must be pointed to the current SaleADS login page at runtime."
      );
    }

    await page.goto(environmentUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const runStep = async (field: ReportField, handler: () => Promise<void>): Promise<void> => {
      try {
        await handler();
        report.status[field] = "PASS";
      } catch (error) {
        report.status[field] = "FAIL";
        const message = error instanceof Error ? error.message : String(error);
        report.errors.push(`${field}: ${message}`);
      }
    };

    try {
      await runStep("Login", async () => {
        const loginButton = await firstVisible([
          page.getByRole("button", { name: /google/i }),
          page.getByRole("link", { name: /google/i }),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
        ]);

        const popupPromise = page.waitForEvent("popup", { timeout: 9000 }).catch(() => null);
        await clickAndWait(page, loginButton);

        const popup = await popupPromise;
        if (popup) {
          await waitForUi(popup);
          const accountOption = popup.getByText(googleAccount, { exact: false });
          if ((await accountOption.count()) > 0) {
            await clickAndWait(popup, accountOption.first());
            await popup.waitForEvent("close", { timeout: 30000 }).catch(() => undefined);
          }
        } else {
          const accountOption = page.getByText(googleAccount, { exact: false });
          if ((await accountOption.count()) > 0) {
            await clickAndWait(page, accountOption.first());
          }
        }

        const sidebar = page
          .locator("aside, nav")
          .filter({ hasText: /negocio|mi negocio|dashboard|inicio/i })
          .first();
        await expect(sidebar).toBeVisible({ timeout: 120000 });
        await captureCheckpoint(page, testInfo, "01-dashboard-loaded", true);
      });

      await runStep("Mi Negocio menu", async () => {
        const negocioSection = await findByVisibleText(page, /negocio/i);
        await clickAndWait(page, negocioSection);

        const miNegocio = await findByVisibleText(page, /mi negocio/i);
        await clickAndWait(page, miNegocio);

        await expect(page.getByText(/agregar negocio/i)).toBeVisible();
        await expect(page.getByText(/administrar negocios/i)).toBeVisible();
        await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
      });

      await runStep("Agregar Negocio modal", async () => {
        const agregarNegocio = await findByVisibleText(page, /agregar negocio/i);
        await clickAndWait(page, agregarNegocio);

        const modal = await firstVisible([
          page.getByRole("dialog"),
          page.locator("[role='dialog']"),
          page.locator(".modal, [data-testid*='modal']")
        ]);

        await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();

        const nombreInput = await firstVisible([
          modal.getByLabel(/nombre del negocio/i),
          modal.getByPlaceholder(/nombre del negocio/i),
          modal.locator("input[name*='nombre' i], input[placeholder*='Nombre' i]")
        ]);
        await expect(nombreInput).toBeVisible();

        await expect(modal.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
        await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
        await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

        await nombreInput.click();
        await nombreInput.fill("Negocio Prueba Automatización");

        await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");
        await clickAndWait(page, modal.getByRole("button", { name: /cancelar/i }));
      });

      await runStep("Administrar Negocios view", async () => {
        if ((await page.getByText(/administrar negocios/i).count()) === 0) {
          const miNegocio = await findByVisibleText(page, /mi negocio/i);
          await clickAndWait(page, miNegocio);
        }

        const administrarNegocios = await findByVisibleText(page, /administrar negocios/i);
        await clickAndWait(page, administrarNegocios);

        await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
        await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
        await expect(page.getByText(/tus negocios/i)).toBeVisible();
        await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

        await captureCheckpoint(page, testInfo, "04-administrar-negocios-page", true);
      });

      await runStep("Información General", async () => {
        const infoSection = page.locator("section, div, article").filter({ hasText: /informaci[oó]n general/i }).first();
        await expect(infoSection.getByText(/@/)).toBeVisible();
        await expect(infoSection.getByText(/[a-z]/i)).toBeVisible();
        await expect(infoSection.getByText(/business plan/i)).toBeVisible();
        await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
      });

      await runStep("Detalles de la Cuenta", async () => {
        const detailsSection = page
          .locator("section, div, article")
          .filter({ hasText: /detalles de la cuenta/i })
          .first();
        await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible();
        await expect(detailsSection.getByText(/estado activo|activo/i)).toBeVisible();
        await expect(detailsSection.getByText(/idioma seleccionado|idioma/i)).toBeVisible();
      });

      await runStep("Tus Negocios", async () => {
        const businessesSection = page.locator("section, div, article").filter({ hasText: /tus negocios/i }).first();
        await expect(businessesSection).toBeVisible();
        await expect(businessesSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
        await expect(businessesSection.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
      });

      await runStep("Términos y Condiciones", async () => {
        report.evidenceUrls.terminosYCondicionesUrl = await openLegalPageAndValidate({
          appPage: page,
          testInfo,
          linkText: "Términos y Condiciones",
          headingText: "Términos y Condiciones",
          screenshotName: "05-terminos-y-condiciones"
        });
      });

      await runStep("Política de Privacidad", async () => {
        report.evidenceUrls.politicaDePrivacidadUrl = await openLegalPageAndValidate({
          appPage: page,
          testInfo,
          linkText: "Política de Privacidad",
          headingText: "Política de Privacidad",
          screenshotName: "06-politica-de-privacidad"
        });
      });
    } finally {
      const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
      await mkdir(testInfo.outputDir, { recursive: true });
      await writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
      await testInfo.attach("saleads-mi-negocio-report", {
        path: reportPath,
        contentType: "application/json"
      });
    }

    const failedSteps = Object.entries(report.status)
      .filter(([, status]) => status === "FAIL")
      .map(([field]) => field);

    expect(
      failedSteps,
      `The workflow has failing steps:\n${failedSteps.join("\n")}\n\nErrors:\n${report.errors.join("\n")}`
    ).toEqual([]);
  });
});
