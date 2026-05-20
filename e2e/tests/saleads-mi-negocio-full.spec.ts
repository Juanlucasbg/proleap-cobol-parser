import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";
type StepResult = { status: StepStatus; detail?: string };
type StepResults = Record<string, StepResult>;

const ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const EXPECTED_USER_NAME = process.env.SALEADS_EXPECTED_USER_NAME;
const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;

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

const WAIT_AFTER_CLICK_MS = 700;

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator.first().isVisible().catch(() => false);
}

async function clickFirstVisible(
  page: Page,
  candidates: Locator[],
  description: string
): Promise<void> {
  for (const candidate of candidates) {
    const target = candidate.first();
    if (await isVisible(target)) {
      await target.click();
      await waitForUiLoad(page);
      return;
    }
  }

  throw new Error(`Could not find clickable element for: ${description}`);
}

async function screenshotCheckpoint(
  page: Page,
  checkpointName: string,
  testOutputDir: string,
  fullPage = true
): Promise<void> {
  const screenshotPath = path.join(testOutputDir, `${checkpointName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
}

async function maybeSelectGoogleAccount(page: Page): Promise<void> {
  const allPages = page.context().pages();

  for (const currentPage of allPages) {
    const accountLocator = currentPage.getByText(ACCOUNT_EMAIL, { exact: false });
    if (await isVisible(accountLocator)) {
      await accountLocator.first().click();
      await waitForUiLoad(currentPage);
      return;
    }
  }
}

async function waitForMainApp(page: Page): Promise<Page> {
  let detectedAppPage = page;

  await expect
    .poll(
      async () => {
        for (const currentPage of page.context().pages()) {
          const sidebar = currentPage.locator("aside, nav").first();
          const negocioText = currentPage.getByText(/mi negocio|negocio/i).first();
          if ((await isVisible(sidebar)) || (await isVisible(negocioText))) {
            detectedAppPage = currentPage;
            return true;
          }
        }

        return false;
      },
      {
        timeout: 90_000,
        message: "Main app UI did not render after login."
      }
    )
    .toBeTruthy();

  await detectedAppPage.bringToFront();
  return detectedAppPage;
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarNegocio = page.getByText(/agregar negocio/i).first();
  const administrarNegocios = page.getByText(/administrar negocios/i).first();

  if ((await isVisible(agregarNegocio)) && (await isVisible(administrarNegocios))) {
    return;
  }

  await clickFirstVisible(
    page,
    [
      page.getByText(/^mi negocio$/i),
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByText(/^negocio$/i),
      page.getByRole("button", { name: /negocio/i })
    ],
    "Mi Negocio menu trigger"
  );
}

async function validateSectionHeading(page: Page, headingPattern: RegExp): Promise<void> {
  await expect(page.getByText(headingPattern).first()).toBeVisible();
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const outputDir = path.join(process.cwd(), "test-results", "checkpoints");
  await fs.mkdir(outputDir, { recursive: true });
  let appPage = page;

  const results = REPORT_FIELDS.reduce<StepResults>((acc, field) => {
    acc[field] = { status: "FAIL" };
    return acc;
  }, {});

  const legalUrls: Record<string, string> = {};

  async function runStep(stepName: (typeof REPORT_FIELDS)[number], fn: () => Promise<void>) {
    try {
      await fn();
      results[stepName] = { status: "PASS" };
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      results[stepName] = { status: "FAIL", detail };
    }
  }

  await runStep("Login", async () => {
    if (!LOGIN_URL && appPage.url() === "about:blank") {
      throw new Error("SALEADS_LOGIN_URL or BASE_URL must be set for automated navigation.");
    }

    if (LOGIN_URL) {
      await appPage.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(appPage);
    }

    await clickFirstVisible(
      appPage,
      [
        appPage.getByRole("button", { name: /google/i }),
        appPage.getByRole("link", { name: /google/i }),
        appPage.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
      ],
      "Sign in with Google button"
    );

    await maybeSelectGoogleAccount(appPage);
    appPage = await waitForMainApp(appPage);
    await screenshotCheckpoint(appPage, "01-dashboard-loaded", outputDir);
  });

  await runStep("Mi Negocio menu", async () => {
    await ensureMiNegocioExpanded(appPage);
    await expect(appPage.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/administrar negocios/i).first()).toBeVisible();
    await screenshotCheckpoint(appPage, "02-mi-negocio-menu-expanded", outputDir);
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickFirstVisible(
      appPage,
      [
        appPage.getByRole("button", { name: /^agregar negocio$/i }),
        appPage.getByRole("link", { name: /^agregar negocio$/i }),
        appPage.getByText(/^agregar negocio$/i)
      ],
      "Agregar Negocio action"
    );

    await expect(appPage.getByText(/crear nuevo negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/nombre del negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    const businessNameInput = appPage.getByLabel(/nombre del negocio/i).first();
    if (await isVisible(businessNameInput)) {
      await businessNameInput.click();
      await waitForUiLoad(appPage);
      await businessNameInput.fill("Negocio Prueba Automatizacion");
    }

    await screenshotCheckpoint(appPage, "03-agregar-negocio-modal", outputDir);
    await clickFirstVisible(
      appPage,
      [appPage.getByRole("button", { name: /cancelar/i }), appPage.getByText(/^cancelar$/i)],
      "Cancelar modal button"
    );
  });

  await runStep("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(appPage);
    await clickFirstVisible(
      appPage,
      [
        appPage.getByRole("button", { name: /administrar negocios/i }),
        appPage.getByRole("link", { name: /administrar negocios/i }),
        appPage.getByText(/administrar negocios/i)
      ],
      "Administrar Negocios action"
    );

    await validateSectionHeading(appPage, /informaci[oó]n general/i);
    await validateSectionHeading(appPage, /detalles de la cuenta/i);
    await validateSectionHeading(appPage, /tus negocios/i);
    await validateSectionHeading(appPage, /secci[oó]n legal/i);
    await screenshotCheckpoint(appPage, "04-administrar-negocios-page", outputDir);
  });

  await runStep("Informacion General", async () => {
    const infoHeader = appPage.getByText(/informaci[oó]n general/i).first();
    await expect(infoHeader).toBeVisible();

    const accountEmail = appPage.getByText(new RegExp(ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"));
    await expect(accountEmail.first()).toBeVisible();
    await expect(appPage.getByText(/business plan/i).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();

    if (EXPECTED_USER_NAME) {
      await expect(appPage.getByText(new RegExp(EXPECTED_USER_NAME, "i")).first()).toBeVisible();
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(appPage.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(appPage.getByText(/estado activo/i).first()).toBeVisible();
    await expect(appPage.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(appPage.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(appPage.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
  });

  async function validateLegalLink(
    reportField: "Terminos y Condiciones" | "Politica de Privacidad",
    linkLabelPattern: RegExp,
    headingPattern: RegExp,
    screenshotName: string
  ): Promise<void> {
    await runStep(reportField, async () => {
      const beforeUrl = appPage.url();
      const popupPromise = appPage.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);

      await clickFirstVisible(
        appPage,
        [
          appPage.getByRole("link", { name: linkLabelPattern }),
          appPage.getByRole("button", { name: linkLabelPattern }),
          appPage.getByText(linkLabelPattern)
        ],
        `Legal link: ${reportField}`
      );

      const popup = await popupPromise;
      const legalPage = popup ?? appPage;

      await waitForUiLoad(legalPage);
      await expect(legalPage.getByText(headingPattern).first()).toBeVisible();

      const bodyText = legalPage.locator("body");
      await expect(bodyText).toContainText(headingPattern);

      legalUrls[reportField] = legalPage.url();
      await screenshotCheckpoint(legalPage, screenshotName, outputDir);

      if (popup) {
        await popup.close();
        await appPage.bringToFront();
      } else if (appPage.url() !== beforeUrl) {
        await appPage.goBack({ waitUntil: "domcontentloaded" });
        await waitForUiLoad(appPage);
      }
    });
  }

  await validateLegalLink(
    "Terminos y Condiciones",
    /t[eé]rminos y condiciones/i,
    /t[eé]rminos y condiciones/i,
    "05-terminos-y-condiciones"
  );

  await validateLegalLink(
    "Politica de Privacidad",
    /pol[ií]tica de privacidad/i,
    /pol[ií]tica de privacidad/i,
    "06-politica-de-privacidad"
  );

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    accountEmail: ACCOUNT_EMAIL,
    loginUrlUsed: LOGIN_URL || "none-provided",
    statusByValidationStep: results,
    evidence: {
      screenshotsDirectory: outputDir,
      legalUrls
    }
  };

  const reportPath = path.join(process.cwd(), "test-results", "saleads-mi-negocio-full-report.json");
  await fs.mkdir(path.dirname(reportPath), { recursive: true });
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

  const failedSteps = Object.entries(results).filter(([, data]) => data.status === "FAIL");
  expect(
    failedSteps,
    `One or more workflow validations failed:\n${JSON.stringify(failedSteps, null, 2)}`
  ).toHaveLength(0);
});
