import { BrowserContext, expect, Locator, Page, test, TestInfo } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  step: string;
  status: StepStatus;
  details?: string;
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_FIELDS: string[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

test.describe("SaleADS Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    const results = new Map<string, StepResult>();
    let terminosUrl = "";
    let privacidadUrl = "";

    const markStep = (step: string, status: StepStatus, details?: string) => {
      results.set(step, { step, status, details });
    };

    const runStep = async (step: string, work: () => Promise<void>) => {
      try {
        await work();
        markStep(step, "PASS");
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        markStep(step, "FAIL", message);
      }
    };

    const screenshot = async (name: string, fullPage = false) => {
      await page.screenshot({
        path: testInfo.outputPath(`${name}.png`),
        fullPage
      });
    };

    const clickAndWaitForUi = async (targetPage: Page, locator: Locator) => {
      await expect(locator).toBeVisible();
      await locator.click();
      await waitForUiToLoad(targetPage);
    };

    const ensureLoginPage = async () => {
      const loginUrl = process.env.SALEADS_LOGIN_URL;

      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
        await waitForUiToLoad(page);
        return;
      }

      if (page.url() === "about:blank") {
        throw new Error(
          "Set SALEADS_LOGIN_URL for your environment, or run with a pre-opened login page."
        );
      }
    };

    await runStep("Login", async () => {
      await ensureLoginPage();

      const googleButton = await firstVisible([
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|continuar con google|iniciar sesi[oó]n con google/i)
      ]);
      const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);

      await clickAndWaitForUi(page, googleButton);

      const popup = await popupPromise;
      const authPage = popup ?? page;
      await waitForUiToLoad(authPage);

      const accountOption = authPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")).first();
      if (await isVisible(accountOption)) {
        await accountOption.click();
        await waitForUiToLoad(authPage);
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 30000 }).catch(() => undefined);
        await page.bringToFront();
      }

      await waitForUiToLoad(page);
      await expect(
        await firstVisible([page.locator("aside"), page.getByRole("navigation"), page.getByText(/negocio/i)])
      ).toBeVisible();

      await screenshot("01-dashboard-loaded");
    });

    await runStep("Mi Negocio menu", async () => {
      const negocioSection = await firstVisible([
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i)
      ]);

      if (await isVisible(negocioSection)) {
        await clickAndWaitForUi(page, negocioSection);
      }

      await clickAndWaitForUi(
        page,
        await firstVisible([
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByRole("link", { name: /Mi Negocio/i }),
          page.getByText(/Mi Negocio/i)
        ])
      );

      await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();

      await screenshot("02-mi-negocio-menu-expanded");
    });

    await runStep("Agregar Negocio modal", async () => {
      await clickAndWaitForUi(
        page,
        await firstVisible([
          page.getByRole("button", { name: /Agregar Negocio/i }),
          page.getByRole("link", { name: /Agregar Negocio/i }),
          page.getByText(/^Agregar Negocio$/i)
        ])
      );

      await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      const nombreNegocioInput = page.getByLabel(/Nombre del Negocio/i).first();
      await nombreNegocioInput.click();
      await nombreNegocioInput.fill("Negocio Prueba Automatización");

      await screenshot("03-agregar-negocio-modal");

      await clickAndWaitForUi(
        page,
        await firstVisible([page.getByRole("button", { name: /Cancelar/i }), page.getByText(/^Cancelar$/i)])
      );
    });

    await runStep("Administrar Negocios view", async () => {
      if (!(await isVisible(page.getByText(/Administrar Negocios/i).first()))) {
        await clickAndWaitForUi(
          page,
          await firstVisible([
            page.getByRole("button", { name: /Mi Negocio/i }),
            page.getByRole("link", { name: /Mi Negocio/i }),
            page.getByText(/Mi Negocio/i)
          ])
        );
      }

      await clickAndWaitForUi(
        page,
        await firstVisible([
          page.getByRole("button", { name: /Administrar Negocios/i }),
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i)
        ])
      );

      await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();

      await screenshot("04-administrar-negocios", true);
    });

    await runStep("Información General", async () => {
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      const emailText = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
      await expect(emailText).toBeVisible();

      const nameCandidate = page
        .locator("h1, h2, h3, strong, p, span")
        .filter({ hasNotText: /BUSINESS PLAN|Cambiar Plan|Cuenta creada|Estado activo|Idioma seleccionado/i })
        .first();
      await expect(nameCandidate).toBeVisible();
    });

    await runStep("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await runStep("Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    });

    await runStep("Términos y Condiciones", async () => {
      const legalNavigation = await openLegalDocument({
        context,
        appPage: page,
        linkPattern: /T[eé]rminos y Condiciones/i,
        headingPattern: /T[eé]rminos y Condiciones/i,
        screenshotName: "08-terminos-y-condiciones",
        testInfo
      });

      terminosUrl = legalNavigation.finalUrl;
    });

    await runStep("Política de Privacidad", async () => {
      const legalNavigation = await openLegalDocument({
        context,
        appPage: page,
        linkPattern: /Pol[ií]tica de Privacidad/i,
        headingPattern: /Pol[ií]tica de Privacidad/i,
        screenshotName: "09-politica-de-privacidad",
        testInfo
      });

      privacidadUrl = legalNavigation.finalUrl;
    });

    const finalReport = REPORT_FIELDS.map((field) => {
      const item = results.get(field) ?? { step: field, status: "FAIL", details: "Step did not run" };
      return {
        step: item.step,
        status: item.status,
        details: item.details ?? ""
      };
    });

    await testInfo.attach("saleads-final-report", {
      body: JSON.stringify(
        {
          report: finalReport,
          legalUrls: {
            terminosYCondiciones: terminosUrl,
            politicaDePrivacidad: privacidadUrl
          }
        },
        null,
        2
      ),
      contentType: "application/json"
    });

    // Visible in CI logs for quick PASS/FAIL scan.
    console.table(finalReport);
    console.log(`Términos y Condiciones URL: ${terminosUrl}`);
    console.log(`Política de Privacidad URL: ${privacidadUrl}`);

    const failed = finalReport.filter((step) => step.status === "FAIL");
    expect(failed, `Failed validation steps: ${JSON.stringify(failed, null, 2)}`).toEqual([]);
  });
});

const firstVisible = async (locators: Locator[], timeout = 5000): Promise<Locator> => {
  for (const candidate of locators) {
    const item = candidate.first();
    try {
      await item.waitFor({ state: "visible", timeout });
      return item;
    } catch {
      // Try next candidate.
    }
  }

  throw new Error("No visible locator found among provided candidates.");
};

const isVisible = async (locator: Locator, timeout = 2500): Promise<boolean> => {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
};

const waitForUiToLoad = async (page: Page) => {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
  await page.waitForTimeout(400);
};

const expectLegalContent = async (targetPage: Page, headingPattern: RegExp) => {
  await expect(targetPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible();

  const longContent = targetPage
    .locator("p, li, article, section")
    .filter({ hasText: /[A-Za-zÁÉÍÓÚáéíóúÑñ]{20,}/ })
    .first();

  if (await isVisible(longContent, 4000)) {
    await expect(longContent).toBeVisible();
  } else {
    await expect(targetPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúÑñ]{100,}/);
  }
};

const openLegalDocument = async ({
  context,
  appPage,
  linkPattern,
  headingPattern,
  screenshotName,
  testInfo
}: {
  context: BrowserContext;
  appPage: Page;
  linkPattern: RegExp;
  headingPattern: RegExp;
  screenshotName: string;
  testInfo: TestInfo;
}) => {
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  const legalLink = await firstVisible([
    appPage.getByRole("link", { name: linkPattern }),
    appPage.getByText(linkPattern)
  ]);

  await expect(legalLink).toBeVisible();
  await legalLink.click();
  await waitForUiToLoad(appPage);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  await waitForUiToLoad(targetPage);
  await expectLegalContent(targetPage, headingPattern);

  await targetPage.screenshot({
    path: testInfo.outputPath(`${screenshotName}.png`),
    fullPage: true
  });

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiToLoad(appPage);
  }

  return { finalUrl };
};
