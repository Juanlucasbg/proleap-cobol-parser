import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

type ReportEntry = {
  status: "PASS" | "FAIL";
  details: string;
  url?: string;
};

type Report = Record<
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad",
  ReportEntry
>;

const defaultReport = (): Report => ({
  Login: { status: "FAIL", details: "Step not executed." },
  "Mi Negocio menu": { status: "FAIL", details: "Step not executed." },
  "Agregar Negocio modal": { status: "FAIL", details: "Step not executed." },
  "Administrar Negocios view": { status: "FAIL", details: "Step not executed." },
  "Información General": { status: "FAIL", details: "Step not executed." },
  "Detalles de la Cuenta": { status: "FAIL", details: "Step not executed." },
  "Tus Negocios": { status: "FAIL", details: "Step not executed." },
  "Términos y Condiciones": { status: "FAIL", details: "Step not executed." },
  "Política de Privacidad": { status: "FAIL", details: "Step not executed." }
});

async function clickAndWaitUi(page: Page, target: Locator): Promise<void> {
  await target.click();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function screenshot(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function firstVisible(locators: Locator[]): Promise<Locator> {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }

  throw new Error("No candidate locator was visible.");
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login con Google y valida flujo de Mi Negocio completo", async ({
    page,
    context
  }, testInfo) => {
    test.setTimeout(180_000);

    const report = defaultReport();
    const setReport = (
      key: keyof Report,
      status: "PASS" | "FAIL",
      details: string,
      url?: string
    ): void => {
      report[key] = { status, details, ...(url ? { url } : {}) };
    };

    if (!testInfo.project.use.baseURL) {
      throw new Error(
        "BASE_URL is required for Playwright execution. Set BASE_URL to the current SaleADS environment login URL."
      );
    }

    await page.goto(testInfo.project.use.baseURL, {
      waitUntil: "domcontentloaded"
    });

    // Step 1: Login with Google
    try {
      const sidebarCandidate = page.locator("aside, nav").filter({
        hasText: /Negocio|Mi Negocio/i
      });

      if (!(await sidebarCandidate.first().isVisible().catch(() => false))) {
        const loginButton = await firstVisible([
          page.getByRole("button", { name: /sign in with google/i }),
          page.getByRole("button", { name: /continuar con google/i }),
          page.getByRole("button", { name: /iniciar sesión con google/i }),
          page.getByText(/sign in with google/i),
          page.getByText(/google/i)
        ]);

        const popupPromise = context
          .waitForEvent("page", { timeout: 8_000 })
          .catch(() => null);

        await clickAndWaitUi(page, loginButton);
        const popup = await popupPromise;

        if (popup) {
          await popup.waitForLoadState("domcontentloaded");
          const accountOption = popup.getByText(
            "juanlucasbarbiergarzon@gmail.com",
            { exact: true }
          );
          if (await accountOption.isVisible().catch(() => false)) {
            await accountOption.click();
            await page.waitForLoadState("domcontentloaded");
          }
          await page.bringToFront();
        } else {
          const samePageAccount = page.getByText(
            "juanlucasbarbiergarzon@gmail.com",
            { exact: true }
          );
          if (await samePageAccount.isVisible().catch(() => false)) {
            await clickAndWaitUi(page, samePageAccount);
          }
        }
      }

      await expect(
        page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio/i }).first()
      ).toBeVisible({ timeout: 20_000 });
      await screenshot(page, testInfo, "01-dashboard-loaded.png");
      setReport("Login", "PASS", "Main interface and left sidebar are visible.");
    } catch (error) {
      setReport("Login", "FAIL", `Login validation failed: ${String(error)}`);
    }

    // Step 2: Open Mi Negocio menu
    try {
      const negocioOption = await firstVisible([
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i)
      ]);
      await clickAndWaitUi(page, negocioOption);

      const miNegocioOption = await firstVisible([
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ]);
      await clickAndWaitUi(page, miNegocioOption);

      await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
      await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();
      await screenshot(page, testInfo, "02-mi-negocio-expanded.png");
      setReport(
        "Mi Negocio menu",
        "PASS",
        "Mi Negocio expanded with Agregar Negocio and Administrar Negocios."
      );
    } catch (error) {
      setReport(
        "Mi Negocio menu",
        "FAIL",
        `Could not expand/validate Mi Negocio menu: ${String(error)}`
      );
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const agregarNegocioButton = await firstVisible([
        page.getByRole("menuitem", { name: /^Agregar Negocio$/i }),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ]);
      await clickAndWaitUi(page, agregarNegocioButton);

      const modal = page.getByRole("dialog");
      await expect(modal.getByText("Crear Nuevo Negocio")).toBeVisible();
      await expect(modal.getByLabel("Nombre del Negocio")).toBeVisible();
      await expect(modal.getByText("Tienes 2 de 3 negocios")).toBeVisible();
      await expect(modal.getByRole("button", { name: "Cancelar" })).toBeVisible();
      await expect(
        modal.getByRole("button", { name: /Crear Negocio/i })
      ).toBeVisible();
      await screenshot(page, testInfo, "03-agregar-negocio-modal.png");

      const businessNameInput = modal.getByLabel("Nombre del Negocio");
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndWaitUi(page, modal.getByRole("button", { name: "Cancelar" }));

      setReport(
        "Agregar Negocio modal",
        "PASS",
        "Modal and required fields/buttons are visible and functional."
      );
    } catch (error) {
      setReport(
        "Agregar Negocio modal",
        "FAIL",
        `Agregar Negocio modal validation failed: ${String(error)}`
      );
    }

    // Step 4: Open Administrar Negocios
    try {
      if (!(await page.getByText(/^Administrar Negocios$/i).isVisible().catch(() => false))) {
        const miNegocioOption = await firstVisible([
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ]);
        await clickAndWaitUi(page, miNegocioOption);
      }

      const administrarNegocios = await firstVisible([
        page.getByRole("menuitem", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ]);
      await clickAndWaitUi(page, administrarNegocios);

      await expect(page.getByText("Información General")).toBeVisible({
        timeout: 20_000
      });
      await expect(page.getByText("Detalles de la Cuenta")).toBeVisible();
      await expect(page.getByText("Tus Negocios")).toBeVisible();
      await expect(page.getByText("Sección Legal")).toBeVisible();
      await screenshot(page, testInfo, "04-administrar-negocios-page.png", true);
      setReport(
        "Administrar Negocios view",
        "PASS",
        "All expected account sections are visible."
      );
    } catch (error) {
      setReport(
        "Administrar Negocios view",
        "FAIL",
        `Administrar Negocios page validation failed: ${String(error)}`
      );
    }

    // Step 5: Validate Información General
    try {
      await expect(page.getByText("Información General")).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
      await expect(page.getByText(/\S+@\S+\.\S+/)).toBeVisible();
      setReport(
        "Información General",
        "PASS",
        "User identity, email, plan and Cambiar Plan are visible."
      );
    } catch (error) {
      setReport(
        "Información General",
        "FAIL",
        `Información General validation failed: ${String(error)}`
      );
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await expect(page.getByText("Cuenta creada")).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText("Idioma seleccionado")).toBeVisible();
      setReport(
        "Detalles de la Cuenta",
        "PASS",
        "Cuenta creada, Estado activo and Idioma seleccionado are visible."
      );
    } catch (error) {
      setReport(
        "Detalles de la Cuenta",
        "FAIL",
        `Detalles de la Cuenta validation failed: ${String(error)}`
      );
    }

    // Step 7: Validate Tus Negocios
    try {
      await expect(page.getByText("Tus Negocios")).toBeVisible();
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios")).toBeVisible();
      setReport(
        "Tus Negocios",
        "PASS",
        "Business list, Agregar Negocio button and usage limit text are visible."
      );
    } catch (error) {
      setReport("Tus Negocios", "FAIL", `Tus Negocios validation failed: ${String(error)}`);
    }

    const validateLegalLink = async (
      linkText: "Términos y Condiciones" | "Política de Privacidad",
      reportKey: "Términos y Condiciones" | "Política de Privacidad",
      screenshotName: string
    ): Promise<void> => {
      const currentAppPage = page;
      const popupPromise = context
        .waitForEvent("page", { timeout: 8_000 })
        .catch(() => null);

      const legalLink = await firstVisible([
        currentAppPage.getByRole("link", { name: linkText }),
        currentAppPage.getByRole("button", { name: linkText }),
        currentAppPage.getByText(linkText)
      ]);

      await clickAndWaitUi(currentAppPage, legalLink);

      const popup = await popupPromise;
      const targetPage = popup ?? currentAppPage;
      await targetPage.waitForLoadState("domcontentloaded");

      await expect(targetPage.getByRole("heading", { name: linkText })).toBeVisible({
        timeout: 20_000
      });

      const legalBodyText = await targetPage.locator("body").innerText();
      if (legalBodyText.trim().length < 120) {
        throw new Error(`${linkText} page does not contain enough legal content text.`);
      }

      await screenshot(targetPage, testInfo, screenshotName, true);
      const finalUrl = targetPage.url();
      setReport(reportKey, "PASS", `${linkText} content loaded correctly.`, finalUrl);

      if (popup) {
        await popup.close();
        await currentAppPage.bringToFront();
      } else {
        await currentAppPage.goBack({ waitUntil: "domcontentloaded" });
        await currentAppPage.waitForTimeout(800);
      }
    };

    // Step 8: Validate Términos y Condiciones
    try {
      await validateLegalLink(
        "Términos y Condiciones",
        "Términos y Condiciones",
        "05-terminos-y-condiciones.png"
      );
    } catch (error) {
      setReport(
        "Términos y Condiciones",
        "FAIL",
        `Términos y Condiciones validation failed: ${String(error)}`
      );
    }

    // Step 9: Validate Política de Privacidad
    try {
      await validateLegalLink(
        "Política de Privacidad",
        "Política de Privacidad",
        "06-politica-de-privacidad.png"
      );
    } catch (error) {
      setReport(
        "Política de Privacidad",
        "FAIL",
        `Política de Privacidad validation failed: ${String(error)}`
      );
    }

    // Step 10: Final Report
    const reportJson = JSON.stringify(report, null, 2);
    await testInfo.attach("saleads-mi-negocio-final-report", {
      body: Buffer.from(reportJson, "utf-8"),
      contentType: "application/json"
    });
    console.log("Final Step Report:\n", reportJson);

    const failedEntries = Object.entries(report).filter(
      ([, value]) => value.status === "FAIL"
    );
    expect(
      failedEntries,
      `One or more workflow validations failed: ${JSON.stringify(failedEntries)}`
    ).toHaveLength(0);
  });
});
