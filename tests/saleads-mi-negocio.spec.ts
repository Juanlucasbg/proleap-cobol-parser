import { expect, Locator, Page, test } from "@playwright/test";

type ReportStatus = "PASS" | "FAIL";

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

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function expectAnyVisible(
  candidates: Locator[],
  description: string,
): Promise<Locator> {
  for (const candidate of candidates) {
    try {
      const first = candidate.first();
      await first.waitFor({ state: "visible", timeout: 5_000 });
      return first;
    } catch {
      // Try next candidate.
    }
  }

  throw new Error(`Could not find visible element: ${description}`);
}

async function clickAndWait(locator: Locator): Promise<void> {
  const page = locator.page();
  await locator.click();
  await waitForUi(page);
}

async function fillIfVisible(locator: Locator, value: string): Promise<void> {
  if (await locator.first().isVisible().catch(() => false)) {
    await locator.first().fill(value);
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({
    page,
    context,
  }, testInfo) => {
    const loginUrl =
      process.env.SALEADS_LOGIN_URL ??
      process.env.SALEADS_BASE_URL ??
      process.env.BASE_URL ??
      (typeof testInfo.project.use.baseURL === "string"
        ? testInfo.project.use.baseURL
        : undefined);

    if (!loginUrl) {
      throw new Error(
        "No environment URL provided. Set SALEADS_LOGIN_URL, SALEADS_BASE_URL, BASE_URL, or Playwright baseURL.",
      );
    }

    const report: Record<ReportField, ReportStatus> = {
      Login: "FAIL",
      "Mi Negocio menu": "FAIL",
      "Agregar Negocio modal": "FAIL",
      "Administrar Negocios view": "FAIL",
      "Información General": "FAIL",
      "Detalles de la Cuenta": "FAIL",
      "Tus Negocios": "FAIL",
      "Términos y Condiciones": "FAIL",
      "Política de Privacidad": "FAIL",
    };

    const failures: string[] = [];
    const finalUrls: Partial<Record<ReportField, string>> = {};

    const checkpoint = async (name: string, fullPage = false) => {
      await page.screenshot({
        path: testInfo.outputPath(`${name}.png`),
        fullPage,
      });
    };

    const runValidation = async (
      field: ReportField,
      action: () => Promise<void>,
    ) => {
      try {
        await action();
        report[field] = "PASS";
      } catch (error) {
        report[field] = "FAIL";
        failures.push(
          `[${field}] ${error instanceof Error ? error.message : String(error)}`,
        );
      }
    };

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    await runValidation("Login", async () => {
      const loginTrigger = await expectAnyVisible(
        [
          page.getByRole("button", { name: /google|sign in|iniciar/i }),
          page.getByRole("link", { name: /google|sign in|iniciar/i }),
          page.getByText(/google|sign in|iniciar/i),
        ],
        "Google login trigger",
      );

      const popupPromise = context
        .waitForEvent("page", { timeout: 8_000 })
        .catch(() => null);
      await clickAndWait(loginTrigger);

      const popup = await popupPromise;
      const accountText = /juanlucasbarbiergarzon@gmail\.com/i;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const googleAccount = popup.getByText(accountText);
        if (await googleAccount.first().isVisible().catch(() => false)) {
          await googleAccount.first().click();
          await waitForUi(popup);
        }
        await page.waitForLoadState("domcontentloaded");
      } else {
        const accountInPage = page.getByText(accountText);
        if (await accountInPage.first().isVisible().catch(() => false)) {
          await accountInPage.first().click();
          await waitForUi(page);
        }
      }

      await expectAnyVisible(
        [
          page.locator("aside"),
          page.getByRole("navigation"),
          page.getByText(/negocio/i),
        ],
        "Main sidebar navigation",
      );

      await checkpoint("01-dashboard-loaded", true);
    });

    await runValidation("Mi Negocio menu", async () => {
      const negocioSection = await expectAnyVisible(
        [
          page.getByText(/^Negocio$/i),
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByRole("link", { name: /^Negocio$/i }),
        ],
        "Negocio section in sidebar",
      );
      await clickAndWait(negocioSection);

      const miNegocioMenu = await expectAnyVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        "Mi Negocio menu",
      );
      await clickAndWait(miNegocioMenu);

      await expectAnyVisible(
        [
          page.getByRole("link", { name: /agregar negocio/i }),
          page.getByRole("button", { name: /agregar negocio/i }),
          page.getByText(/agregar negocio/i),
        ],
        "Agregar Negocio submenu option",
      );
      await expectAnyVisible(
        [
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ],
        "Administrar Negocios submenu option",
      );

      await checkpoint("02-mi-negocio-menu-expanded");
    });

    await runValidation("Agregar Negocio modal", async () => {
      const agregarNegocio = await expectAnyVisible(
        [
          page.getByRole("link", { name: /agregar negocio/i }),
          page.getByRole("button", { name: /agregar negocio/i }),
          page.getByText(/agregar negocio/i),
        ],
        "Agregar Negocio action",
      );
      await clickAndWait(agregarNegocio);

      await expectAnyVisible(
        [page.getByText(/Crear Nuevo Negocio/i)],
        "Crear Nuevo Negocio modal title",
      );
      const nombreInput = await expectAnyVisible(
        [
          page.getByLabel(/Nombre del Negocio/i),
          page.getByPlaceholder(/Nombre del Negocio/i),
          page.locator("input").filter({ hasText: "" }),
        ],
        "Nombre del Negocio input",
      );
      await expectAnyVisible(
        [page.getByText(/Tienes 2 de 3 negocios/i)],
        "Tienes 2 de 3 negocios text",
      );
      await expectAnyVisible(
        [page.getByRole("button", { name: /^Cancelar$/i })],
        "Cancelar button",
      );
      await expectAnyVisible(
        [page.getByRole("button", { name: /Crear Negocio/i })],
        "Crear Negocio button",
      );

      await fillIfVisible(nombreInput, "Negocio Prueba Automatización");
      await checkpoint("03-agregar-negocio-modal");

      const cancelButton = await expectAnyVisible(
        [page.getByRole("button", { name: /^Cancelar$/i })],
        "Cancelar button to close modal",
      );
      await clickAndWait(cancelButton);
    });

    await runValidation("Administrar Negocios view", async () => {
      const administrarOptionVisible = await page
        .getByText(/administrar negocios/i)
        .first()
        .isVisible()
        .catch(() => false);

      if (!administrarOptionVisible) {
        const miNegocioMenu = await expectAnyVisible(
          [
            page.getByRole("button", { name: /mi negocio/i }),
            page.getByRole("link", { name: /mi negocio/i }),
            page.getByText(/mi negocio/i),
          ],
          "Mi Negocio menu to re-expand",
        );
        await clickAndWait(miNegocioMenu);
      }

      const administrarNegocios = await expectAnyVisible(
        [
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ],
        "Administrar Negocios option",
      );
      await clickAndWait(administrarNegocios);

      await expectAnyVisible(
        [page.getByText(/Información General/i)],
        "Información General section",
      );
      await expectAnyVisible(
        [page.getByText(/Detalles de la Cuenta/i)],
        "Detalles de la Cuenta section",
      );
      await expectAnyVisible(
        [page.getByText(/Tus Negocios/i)],
        "Tus Negocios section",
      );
      await expectAnyVisible(
        [page.getByText(/Sección Legal/i)],
        "Sección Legal section",
      );

      await checkpoint("04-administrar-negocios-page", true);
    });

    await runValidation("Información General", async () => {
      await expectAnyVisible(
        [
          page.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
          page.getByText(/@/),
        ],
        "User email in Información General",
      );
      await expectAnyVisible(
        [
          page.getByText(/usuario|nombre/i),
          page.locator("h1, h2, h3").filter({ hasNotText: /información general/i }),
        ],
        "User name in Información General",
      );
      await expectAnyVisible(
        [page.getByText(/BUSINESS PLAN/i)],
        "BUSINESS PLAN text",
      );
      await expectAnyVisible(
        [page.getByRole("button", { name: /Cambiar Plan/i })],
        "Cambiar Plan button",
      );
    });

    await runValidation("Detalles de la Cuenta", async () => {
      await expectAnyVisible(
        [page.getByText(/Cuenta creada/i)],
        "Cuenta creada text",
      );
      await expectAnyVisible(
        [page.getByText(/Estado activo/i)],
        "Estado activo text",
      );
      await expectAnyVisible(
        [page.getByText(/Idioma seleccionado/i)],
        "Idioma seleccionado text",
      );
    });

    await runValidation("Tus Negocios", async () => {
      await expectAnyVisible(
        [page.getByText(/Tus Negocios/i)],
        "Tus Negocios title",
      );
      await expectAnyVisible(
        [
          page.getByRole("button", { name: /Agregar Negocio/i }),
          page.getByRole("link", { name: /Agregar Negocio/i }),
        ],
        "Agregar Negocio button in Tus Negocios",
      );
      await expectAnyVisible(
        [page.getByText(/Tienes 2 de 3 negocios/i)],
        "Tienes 2 de 3 negocios usage text",
      );
      await expectAnyVisible(
        [page.locator("ul li, table tbody tr, [data-testid*='business']")],
        "Business list/container",
      );
    });

    await runValidation("Términos y Condiciones", async () => {
      const termsLink = await expectAnyVisible(
        [
          page.getByRole("link", { name: /Términos y Condiciones/i }),
          page.getByText(/Términos y Condiciones/i),
        ],
        "Términos y Condiciones link",
      );

      const popupPromise = context
        .waitForEvent("page", { timeout: 6_000 })
        .catch(() => null);
      await clickAndWait(termsLink);

      const popup = await popupPromise;
      const legalPage = popup ?? page;
      await legalPage.waitForLoadState("domcontentloaded");

      await expectAnyVisible(
        [legalPage.getByRole("heading", { name: /Términos y Condiciones/i })],
        "Términos y Condiciones heading",
      );
      await expectAnyVisible(
        [legalPage.getByText(/Términos|Condiciones|legal/i)],
        "Legal content text",
      );

      await legalPage.screenshot({
        path: testInfo.outputPath("05-terminos-y-condiciones.png"),
        fullPage: true,
      });
      finalUrls["Términos y Condiciones"] = legalPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" });
        await waitForUi(page);
      }
    });

    await runValidation("Política de Privacidad", async () => {
      const privacyLink = await expectAnyVisible(
        [
          page.getByRole("link", { name: /Política de Privacidad/i }),
          page.getByText(/Política de Privacidad/i),
        ],
        "Política de Privacidad link",
      );

      const popupPromise = context
        .waitForEvent("page", { timeout: 6_000 })
        .catch(() => null);
      await clickAndWait(privacyLink);

      const popup = await popupPromise;
      const legalPage = popup ?? page;
      await legalPage.waitForLoadState("domcontentloaded");

      await expectAnyVisible(
        [legalPage.getByRole("heading", { name: /Política de Privacidad/i })],
        "Política de Privacidad heading",
      );
      await expectAnyVisible(
        [legalPage.getByText(/Política|Privacidad|legal/i)],
        "Privacy legal content text",
      );

      await legalPage.screenshot({
        path: testInfo.outputPath("06-politica-de-privacidad.png"),
        fullPage: true,
      });
      finalUrls["Política de Privacidad"] = legalPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" });
        await waitForUi(page);
      }
    });

    const reportLines = REPORT_FIELDS.map((field) => `${field}: ${report[field]}`);
    const finalReport = {
      name: "saleads_mi_negocio_full_test",
      report,
      finalUrls,
      failures,
    };

    await testInfo.attach("final-report", {
      body: JSON.stringify(finalReport, null, 2),
      contentType: "application/json",
    });

    // Console summary for CI logs.
    console.log("==== Final Report (saleads_mi_negocio_full_test) ====");
    for (const line of reportLines) {
      console.log(line);
    }
    if (finalUrls["Términos y Condiciones"]) {
      console.log(
        `Términos y Condiciones URL: ${finalUrls["Términos y Condiciones"]}`,
      );
    }
    if (finalUrls["Política de Privacidad"]) {
      console.log(
        `Política de Privacidad URL: ${finalUrls["Política de Privacidad"]}`,
      );
    }

    expect(failures, failures.join("\n")).toEqual([]);
  });
});
