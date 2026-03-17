import { expect, Locator, Page, test } from "@playwright/test";

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

type ReportStatus = "PASS" | "FAIL";

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function clickableByText(page: Page, text: string): Locator {
  const exactText = new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i");

  return page
    .getByRole("button", { name: exactText })
    .or(page.getByRole("link", { name: exactText }))
    .or(page.getByRole("menuitem", { name: exactText }))
    .or(page.getByText(exactText))
    .first();
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

async function clickAndWait(page: Page, target: Locator): Promise<void> {
  await expect(target).toBeVisible();
  await target.click();
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
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

  const notes: string[] = [];
  const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};

  const mark = async (field: ReportField, assertion: () => Promise<void>) => {
    try {
      await assertion();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      notes.push(`${field}: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  await mark("Login", async () => {
    if (page.url() === "about:blank") {
      const loginUrl = process.env.SALEADS_LOGIN_URL;

      if (!loginUrl) {
        throw new Error(
          "Set SALEADS_LOGIN_URL, or launch the test with an already-open SaleADS login page.",
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    const loginTrigger = page
      .getByRole("button", {
        name: /google|sign in|iniciar sesi[oó]n|continuar con google|login/i,
      })
      .or(
        page.getByRole("link", {
          name: /google|sign in|iniciar sesi[oó]n|continuar con google|login/i,
        }),
      )
      .or(page.getByText(/google/i))
      .first();

    await expect(loginTrigger).toBeVisible();

    const popupPromise = context
      .waitForEvent("page", { timeout: 12000 })
      .catch(() => null);

    await loginTrigger.click();
    await waitForUi(page);

    const googlePopup = await popupPromise;

    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");

      const accountSelector = googlePopup
        .getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false })
        .or(googlePopup.getByRole("button", { name: new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i") }))
        .first();

      if (await accountSelector.isVisible().catch(() => false)) {
        await accountSelector.click();
      }
    }

    await page.bringToFront();
    await waitForUi(page);

    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(
      page.getByText(/dashboard|inicio|negocio|mi negocio/i).first(),
    ).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true,
    });
  });

  await mark("Mi Negocio menu", async () => {
    const negocio = clickableByText(page, "Negocio");
    const miNegocio = clickableByText(page, "Mi Negocio");

    await clickAndWait(page, negocio);
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-expanded-menu.png"),
      fullPage: true,
    });
  });

  await mark("Agregar Negocio modal", async () => {
    await clickAndWait(page, clickableByText(page, "Agregar Negocio"));

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();

    const nombreNegocioInput = page
      .getByLabel(/Nombre del Negocio/i)
      .or(page.getByPlaceholder(/Nombre del Negocio/i))
      .or(page.locator("input[name*=nombre i], input[id*=nombre i]"))
      .first();

    await expect(nombreNegocioInput).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(clickableByText(page, "Cancelar")).toBeVisible();
    await expect(clickableByText(page, "Crear Negocio")).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("03-crear-nuevo-negocio-modal.png"),
      fullPage: true,
    });

    await nombreNegocioInput.click();
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, clickableByText(page, "Cancelar"));
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).not.toBeVisible();
  });

  await mark("Administrar Negocios view", async () => {
    const administrarNegocios = page.getByText(/Administrar Negocios/i).first();

    if (!(await administrarNegocios.isVisible().catch(() => false))) {
      await clickAndWait(page, clickableByText(page, "Mi Negocio"));
    }

    await clickAndWait(page, page.getByText(/Administrar Negocios/i).first());

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-cuenta.png"),
      fullPage: true,
    });
  });

  await mark("Información General", async () => {
    await expect(page.getByText(/información general/i).first()).toBeVisible();
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(clickableByText(page, "Cambiar Plan")).toBeVisible();
  });

  await mark("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await mark("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
  });

  const validateLegalPage = async (
    field: "Términos y Condiciones" | "Política de Privacidad",
    heading: RegExp,
    screenshotName: string,
  ) => {
    await mark(field, async () => {
      const appPage = page;
      const popupPromise = context
        .waitForEvent("page", { timeout: 8000 })
        .catch(() => null);

      await clickAndWait(appPage, clickableByText(appPage, field));
      const popup = await popupPromise;
      const targetPage = popup ?? appPage;

      await targetPage.waitForLoadState("domcontentloaded");
      await expect(targetPage.getByRole("heading", { name: heading }).first()).toBeVisible();
      await expect(targetPage.locator("body")).toContainText(/\w{20,}/);

      legalUrls[field] = targetPage.url();

      await targetPage.screenshot({
        path: testInfo.outputPath(screenshotName),
        fullPage: true,
      });

      if (popup) {
        await popup.close();
        await appPage.bringToFront();
      } else {
        await appPage.goBack({ waitUntil: "domcontentloaded" });
      }

      await waitForUi(appPage);
    });
  };

  await validateLegalPage(
    "Términos y Condiciones",
    /Términos y Condiciones/i,
    "05-terminos-y-condiciones.png",
  );

  await validateLegalPage(
    "Política de Privacidad",
    /Política de Privacidad/i,
    "06-politica-de-privacidad.png",
  );

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    report: report,
    legalUrls,
    notes,
  };

  await testInfo.attach("final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json",
  });

  // Keep machine-readable status in the terminal output for automation collectors.
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(finalReport, null, 2));

  const failedFields = Object.entries(report)
    .filter(([, status]) => status === "FAIL")
    .map(([field]) => field);

  expect(
    failedFields,
    `Final report contains FAIL in: ${failedFields.join(", ")}`,
  ).toEqual([]);
});
