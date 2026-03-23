import { expect, type BrowserContext, type Locator, type Page, type TestInfo, test } from "@playwright/test";

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

const REPORT_FIELDS: ReportField[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
];

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_LIMIT_TEXT = "Tienes 2 de 3 negocios";

function createInitialReport(): Record<ReportField, StepStatus> {
  return REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = "FAIL";
      return acc;
    },
    {} as Record<ReportField, StepStatus>,
  );
}

async function settleUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => undefined);
}

async function firstVisible(candidates: Locator[], timeoutMs = 15000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(`No candidate locator became visible within ${timeoutMs}ms.`);
}

async function maybeVisible(candidates: Locator[], timeoutMs = 4000): Promise<Locator | null> {
  try {
    return await firstVisible(candidates, timeoutMs);
  } catch {
    return null;
  }
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await settleUi(page);
}

async function checkpointScreenshot(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false,
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function markStep(
  field: ReportField,
  report: Record<ReportField, StepStatus>,
  failures: string[],
  fn: () => Promise<void>,
  critical = false,
): Promise<void> {
  try {
    await fn();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    const message = error instanceof Error ? error.message : String(error);
    failures.push(`${field}: ${message}`);
    if (critical) {
      throw error;
    }
  }
}

async function ensureStartingPoint(page: Page): Promise<void> {
  const configuredUrl = process.env.SALEADS_URL;
  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await settleUi(page);
  }

  const currentUrl = page.url();
  if (!configuredUrl && (currentUrl === "about:blank" || currentUrl === "data:,")) {
    throw new Error(
      "No starting URL available. Set SALEADS_URL or run the test in a context where the browser is already on the SaleADS login page.",
    );
  }
}

async function handleGoogleAccountSelectionIfPrompted(authPage: Page): Promise<void> {
  const accountOption = await maybeVisible(
    [
      authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
      authPage.locator(`[data-identifier="${GOOGLE_ACCOUNT_EMAIL}"]`),
      authPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
    ],
    8000,
  );

  if (!accountOption) {
    return;
  }

  await accountOption.click();
  await settleUi(authPage);
}

async function openLegalLinkAndValidate(
  appPage: Page,
  context: BrowserContext,
  linkName: "Términos y Condiciones" | "Política de Privacidad",
  expectedHeading: RegExp,
  reportKey: "termsUrl" | "privacyUrl",
  legalUrls: { termsUrl?: string; privacyUrl?: string },
  testInfo: TestInfo,
  screenshotName: string,
): Promise<void> {
  const legalLink = await firstVisible(
    [
      appPage.getByRole("link", { name: new RegExp(linkName, "i") }),
      appPage.getByText(new RegExp(linkName, "i")),
    ],
    12000,
  );

  const previousUrl = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await legalLink.click();
  await settleUi(appPage);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  await settleUi(legalPage);

  const heading = await firstVisible(
    [
      legalPage.getByRole("heading", { name: expectedHeading }),
      legalPage.getByText(expectedHeading),
    ],
    20000,
  );
  await expect(heading).toBeVisible();

  const legalBodyText = await legalPage.locator("body").innerText();
  expect(legalBodyText.trim().length).toBeGreaterThan(120);

  legalUrls[reportKey] = legalPage.url();
  await checkpointScreenshot(legalPage, testInfo, screenshotName, true);

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await settleUi(appPage);
    return;
  }

  if (appPage.url() !== previousUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.goto(previousUrl, { waitUntil: "domcontentloaded" });
    });
    await settleUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createInitialReport();
  const failures: string[] = [];
  const legalUrls: { termsUrl?: string; privacyUrl?: string } = {};

  try {
    await markStep(
      "Login",
      report,
      failures,
      async () => {
        await ensureStartingPoint(page);

        const appSidebarAlreadyVisible = await maybeVisible(
          [
            page.locator("aside, nav").filter({ hasText: /Mi Negocio|Negocio|Administrar Negocios/i }),
            page.getByRole("link", { name: /Mi Negocio/i }),
          ],
          3000,
        );

        if (!appSidebarAlreadyVisible) {
          const loginButton = await firstVisible(
            [
              page.getByRole("button", { name: /google|sign in|iniciar sesión|ingresar|continuar/i }),
              page.getByRole("link", { name: /google|sign in|iniciar sesión|ingresar|continuar/i }),
              page.getByText(/google/i),
            ],
            20000,
          );

          const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
          await clickAndWait(page, loginButton);

          const popup = await popupPromise;
          if (popup) {
            await settleUi(popup);
            await handleGoogleAccountSelectionIfPrompted(popup);
            await popup.waitForClose({ timeout: 60000 }).catch(() => undefined);
          } else if (page.url().includes("accounts.google.com")) {
            await handleGoogleAccountSelectionIfPrompted(page);
            await page
              .waitForURL((url) => !url.toString().includes("accounts.google.com"), { timeout: 60000 })
              .catch(() => undefined);
          }
        }

        const mainUi = await firstVisible(
          [
            page.locator("aside, nav").filter({ hasText: /Mi Negocio|Negocio|Administrar Negocios/i }),
            page.getByRole("link", { name: /Mi Negocio/i }),
          ],
          30000,
        );

        await expect(mainUi).toBeVisible();
        await checkpointScreenshot(page, testInfo, "01-dashboard-loaded.png", true);
      },
      true,
    );

    await markStep(
      "Mi Negocio menu",
      report,
      failures,
      async () => {
        const negocioEntry = await maybeVisible(
          [
            page.getByRole("button", { name: /^Negocio$/i }),
            page.getByRole("link", { name: /^Negocio$/i }),
            page.getByText(/^Negocio$/i),
          ],
          10000,
        );

        if (negocioEntry) {
          await clickAndWait(page, negocioEntry);
        }

        const miNegocioEntry = await firstVisible(
          [
            page.getByRole("button", { name: /Mi Negocio/i }),
            page.getByRole("link", { name: /Mi Negocio/i }),
            page.getByText(/Mi Negocio/i),
          ],
          12000,
        );
        await clickAndWait(page, miNegocioEntry);

        const agregarNegocio = await firstVisible(
          [
            page.getByRole("link", { name: /^Agregar Negocio$/i }),
            page.getByRole("button", { name: /^Agregar Negocio$/i }),
            page.getByText(/^Agregar Negocio$/i),
          ],
          12000,
        );
        const administrarNegocios = await firstVisible(
          [
            page.getByRole("link", { name: /^Administrar Negocios$/i }),
            page.getByRole("button", { name: /^Administrar Negocios$/i }),
            page.getByText(/^Administrar Negocios$/i),
          ],
          12000,
        );

        await expect(agregarNegocio).toBeVisible();
        await expect(administrarNegocios).toBeVisible();
        await checkpointScreenshot(page, testInfo, "02-mi-negocio-menu-expanded.png", true);
      },
      true,
    );

    await markStep("Agregar Negocio modal", report, failures, async () => {
      const agregarNegocioEntry = await firstVisible(
        [
          page.getByRole("link", { name: /^Agregar Negocio$/i }),
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i),
        ],
        12000,
      );
      await clickAndWait(page, agregarNegocioEntry);

      const modalTitle = await firstVisible(
        [page.getByRole("heading", { name: /Crear Nuevo Negocio/i }), page.getByText(/Crear Nuevo Negocio/i)],
        12000,
      );
      await expect(modalTitle).toBeVisible();

      const businessNameInput = await firstVisible(
        [
          page.getByLabel(/Nombre del Negocio/i),
          page.getByPlaceholder(/Nombre del Negocio/i),
          page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
        ],
        12000,
      );
      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(BUSINESS_LIMIT_TEXT)).toBeVisible();
      await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

      await checkpointScreenshot(page, testInfo, "03-agregar-negocio-modal.png", true);

      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }));
    });

    await markStep(
      "Administrar Negocios view",
      report,
      failures,
      async () => {
        let administrarNegocios = await maybeVisible(
          [
            page.getByRole("link", { name: /^Administrar Negocios$/i }),
            page.getByRole("button", { name: /^Administrar Negocios$/i }),
            page.getByText(/^Administrar Negocios$/i),
          ],
          5000,
        );

        if (!administrarNegocios) {
          const miNegocio = await firstVisible(
            [
              page.getByRole("button", { name: /Mi Negocio/i }),
              page.getByRole("link", { name: /Mi Negocio/i }),
              page.getByText(/Mi Negocio/i),
            ],
            12000,
          );
          await clickAndWait(page, miNegocio);

          administrarNegocios = await firstVisible(
            [
              page.getByRole("link", { name: /^Administrar Negocios$/i }),
              page.getByRole("button", { name: /^Administrar Negocios$/i }),
              page.getByText(/^Administrar Negocios$/i),
            ],
            12000,
          );
        }

        await clickAndWait(page, administrarNegocios);

        await expect(page.getByText(/Información General/i)).toBeVisible();
        await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
        await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
        await expect(page.getByText(/Sección Legal/i)).toBeVisible();

        await checkpointScreenshot(page, testInfo, "04-administrar-negocios-view-full.png", true);
      },
      true,
    );

    await markStep("Información General", report, failures, async () => {
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      const emailVisible = await maybeVisible(
        [
          page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
          page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
        ],
        12000,
      );
      if (!emailVisible) {
        throw new Error("User email was not visible in Información General.");
      }

      const infoSectionText = await page.locator("body").innerText();
      const hasNameHint = /Nombre|Usuario|Perfil|Account|Cuenta/i.test(infoSectionText);
      expect(hasNameHint).toBeTruthy();
    });

    await markStep("Detalles de la Cuenta", report, failures, async () => {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await markStep("Tus Negocios", report, failures, async () => {
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
      await expect(page.getByText(BUSINESS_LIMIT_TEXT)).toBeVisible();

      const listOrCard = await maybeVisible(
        [
          page.locator("ul li"),
          page.locator("table tbody tr"),
          page.locator("[role='row']"),
          page.locator("[class*='business'], [id*='business']"),
        ],
        8000,
      );
      if (!listOrCard) {
        throw new Error("Business list/card was not visible in Tus Negocios.");
      }
    });

    await markStep("Términos y Condiciones", report, failures, async () => {
      await openLegalLinkAndValidate(
        page,
        context,
        "Términos y Condiciones",
        /Términos y Condiciones/i,
        "termsUrl",
        legalUrls,
        testInfo,
        "05-terminos-y-condiciones.png",
      );
    });

    await markStep("Política de Privacidad", report, failures, async () => {
      await openLegalLinkAndValidate(
        page,
        context,
        "Política de Privacidad",
        /Política de Privacidad/i,
        "privacyUrl",
        legalUrls,
        testInfo,
        "06-politica-de-privacidad.png",
      );
    });
  } finally {
    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow",
      statusByField: report,
      evidence: {
        termsAndConditionsUrl: legalUrls.termsUrl ?? "NOT_CAPTURED",
        privacyPolicyUrl: legalUrls.privacyUrl ?? "NOT_CAPTURED",
      },
      failures,
    };

    const serialized = JSON.stringify(finalReport, null, 2);
    console.log("\nFINAL_REPORT_START");
    console.log(serialized);
    console.log("FINAL_REPORT_END\n");

    await testInfo.attach("final-report.json", {
      body: Buffer.from(serialized, "utf-8"),
      contentType: "application/json",
    });
  }

  expect.soft(failures, "Expected all workflow validations to pass").toHaveLength(0);
});
