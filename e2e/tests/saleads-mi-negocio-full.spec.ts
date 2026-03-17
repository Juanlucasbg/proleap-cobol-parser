import { expect, Locator, Page, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type FinalReport = {
  Login: StepStatus;
  "Mi Negocio menu": StepStatus;
  "Agregar Negocio modal": StepStatus;
  "Administrar Negocios view": StepStatus;
  "Información General": StepStatus;
  "Detalles de la Cuenta": StepStatus;
  "Tus Negocios": StepStatus;
  "Términos y Condiciones": StepStatus;
  "Política de Privacidad": StepStatus;
};

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function toRegex(text: string): RegExp {
  return new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator.isVisible().catch(() => false);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 6_000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click({ timeout: 20_000 });
  await waitForUi(page);
}

async function firstVisible(page: Page, options: Locator[]): Promise<Locator> {
  for (const option of options) {
    if (await isVisible(option.first())) {
      return option.first();
    }
  }

  throw new Error("No visible locator found from provided options.");
}

async function capture(page: Page, fileName: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: test.info().outputPath(fileName),
    fullPage,
  });
}

async function validateLegalPage(
  page: Page,
  linkText: string,
  headingText: string,
  screenshotName: string
): Promise<{ pass: boolean; finalUrl: string }> {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  const currentUrl = page.url();

  const legalLink = await firstVisible(page, [
    page.getByRole("link", { name: toRegex(linkText) }),
    page.getByText(toRegex(linkText)),
  ]);

  await clickAndWait(page, legalLink);
  const popup = await popupPromise;
  const legalPage = popup ?? page;

  await waitForUi(legalPage);

  const headingVisible =
    (await isVisible(legalPage.getByRole("heading", { name: toRegex(headingText) }).first())) ||
    (await isVisible(legalPage.getByText(toRegex(headingText)).first()));

  const legalContent = await legalPage.locator("main, article, body").first().textContent();
  const contentVisible = Boolean(legalContent && legalContent.trim().length > 120);

  await capture(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== currentUrl) {
    await page.goBack().catch(() => {});
    await waitForUi(page);
  }

  return {
    pass: headingVisible && contentVisible,
    finalUrl,
  };
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report: FinalReport = {
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

  if (process.env.SALEADS_START_URL) {
    await page.goto(process.env.SALEADS_START_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  // Step 1 - Login with Google.
  try {
    const loginButton = await firstVisible(page, [
      page.getByRole("button", { name: /google|sign in|iniciar sesión/i }),
      page.getByRole("link", { name: /google|sign in|iniciar sesión/i }),
      page.getByText(/google|sign in|iniciar sesión/i),
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    const authPage = popup ?? page;
    await waitForUi(authPage);

    const accountOption = authPage.getByText(toRegex(ACCOUNT_EMAIL)).first();
    if (await isVisible(accountOption)) {
      await clickAndWait(authPage, accountOption);
    }

    if (popup) {
      await Promise.race([
        popup.waitForEvent("close", { timeout: 60_000 }),
        page.waitForLoadState("domcontentloaded", { timeout: 60_000 }),
      ]).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    }

    const appVisible = await isVisible(
      page.locator("main, [role='main'], [data-testid*='dashboard']").first()
    );
    const sidebarVisible =
      (await isVisible(page.locator("aside, nav").first())) ||
      (await isVisible(page.getByText(/negocio|mi negocio/i).first()));

    report.Login = appVisible && sidebarVisible ? "PASS" : "FAIL";
    await capture(page, "01-dashboard-loaded.png", true);
  } catch {
    report.Login = "FAIL";
  }

  // Step 2 - Open Mi Negocio menu.
  try {
    const negocioSection = await firstVisible(page, [
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /Negocio/i }),
      page.getByRole("link", { name: /Negocio/i }),
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocio = await firstVisible(page, [
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
    ]);
    await clickAndWait(page, miNegocio);

    const agregarNegocioVisible = await isVisible(page.getByText(/^Agregar Negocio$/i).first());
    const administrarNegociosVisible = await isVisible(
      page.getByText(/^Administrar Negocios$/i).first()
    );

    report["Mi Negocio menu"] =
      agregarNegocioVisible && administrarNegociosVisible ? "PASS" : "FAIL";
    await capture(page, "02-mi-negocio-menu-expanded.png", true);
  } catch {
    report["Mi Negocio menu"] = "FAIL";
  }

  // Step 3 - Validate Agregar Negocio modal.
  try {
    const agregarNegocio = await firstVisible(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await clickAndWait(page, agregarNegocio);

    const modal = page.getByRole("dialog").first();
    const titleVisible =
      (await isVisible(modal.getByText(/Crear Nuevo Negocio/i))) ||
      (await isVisible(page.getByText(/Crear Nuevo Negocio/i).first()));
    const inputVisible =
      (await isVisible(modal.getByLabel(/Nombre del Negocio/i))) ||
      (await isVisible(page.getByPlaceholder(/Nombre del Negocio/i).first()));
    const limitVisible = await isVisible(page.getByText(/Tienes 2 de 3 negocios/i).first());
    const cancelVisible = await isVisible(page.getByRole("button", { name: /Cancelar/i }).first());
    const createVisible = await isVisible(
      page.getByRole("button", { name: /Crear Negocio/i }).first()
    );

    report["Agregar Negocio modal"] =
      titleVisible && inputVisible && limitVisible && cancelVisible && createVisible
        ? "PASS"
        : "FAIL";

    await capture(page, "03-agregar-negocio-modal.png", true);

    const nameInput = await firstVisible(page, [
      modal.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
    ]);
    await nameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /Cancelar/i }).first());
  } catch {
    report["Agregar Negocio modal"] = "FAIL";
  }

  // Step 4 - Open Administrar Negocios view.
  try {
    if (!(await isVisible(page.getByText(/^Administrar Negocios$/i).first()))) {
      const miNegocio = await firstVisible(page, [
        page.getByText(/^Mi Negocio$/i),
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
      ]);
      await clickAndWait(page, miNegocio);
    }

    const administrarNegocios = await firstVisible(page, [
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    await clickAndWait(page, administrarNegocios);
    await waitForUi(page);

    const infoGeneralVisible = await isVisible(page.getByText(/Información General/i).first());
    const detallesCuentaVisible = await isVisible(page.getByText(/Detalles de la Cuenta/i).first());
    const tusNegociosVisible = await isVisible(page.getByText(/Tus Negocios/i).first());
    const legalVisible = await isVisible(
      page.getByText(/Sección Legal|Términos y Condiciones|Política de Privacidad/i).first()
    );

    report["Administrar Negocios view"] =
      infoGeneralVisible && detallesCuentaVisible && tusNegociosVisible && legalVisible
        ? "PASS"
        : "FAIL";
    await capture(page, "04-administrar-negocios-page.png", true);
  } catch {
    report["Administrar Negocios view"] = "FAIL";
  }

  // Step 5 - Validate Información General.
  {
    const userNameVisible =
      (await isVisible(page.getByText(/Nombre|Usuario/i).first())) ||
      (await isVisible(page.getByText(/juanlucas|juan lucas|barbier/i).first()));
    const userEmailVisible = await isVisible(page.getByText(toRegex(ACCOUNT_EMAIL)).first());
    const businessPlanVisible = await isVisible(page.getByText(/BUSINESS PLAN/i).first());
    const cambiarPlanVisible = await isVisible(
      page.getByRole("button", { name: /Cambiar Plan/i }).first()
    );

    report["Información General"] =
      userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible
        ? "PASS"
        : "FAIL";
  }

  // Step 6 - Validate Detalles de la Cuenta.
  {
    const cuentaCreadaVisible = await isVisible(page.getByText(/Cuenta creada/i).first());
    const estadoActivoVisible = await isVisible(page.getByText(/Estado activo/i).first());
    const idiomaSeleccionadoVisible = await isVisible(page.getByText(/Idioma seleccionado/i).first());

    report["Detalles de la Cuenta"] =
      cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible ? "PASS" : "FAIL";
  }

  // Step 7 - Validate Tus Negocios.
  {
    const negociosListVisible =
      (await isVisible(page.getByText(/Tus Negocios/i).first())) ||
      (await isVisible(page.locator("table, [role='table'], [role='list']").first()));
    const addBusinessVisible = await isVisible(
      page.getByRole("button", { name: /^Agregar Negocio$/i }).first()
    );
    const negociosLimitVisible = await isVisible(page.getByText(/Tienes 2 de 3 negocios/i).first());

    report["Tus Negocios"] =
      negociosListVisible && addBusinessVisible && negociosLimitVisible ? "PASS" : "FAIL";
  }

  // Step 8 - Validate Términos y Condiciones.
  try {
    const termsResult = await validateLegalPage(
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05-terminos-y-condiciones.png"
    );
    report["Términos y Condiciones"] = termsResult.pass ? "PASS" : "FAIL";
    test.info().annotations.push({
      type: "Términos y Condiciones URL",
      description: termsResult.finalUrl,
    });
    console.log(`Términos y Condiciones URL: ${termsResult.finalUrl}`);
  } catch {
    report["Términos y Condiciones"] = "FAIL";
  }

  // Step 9 - Validate Política de Privacidad.
  try {
    const privacyResult = await validateLegalPage(
      page,
      "Política de Privacidad",
      "Política de Privacidad",
      "06-politica-de-privacidad.png"
    );
    report["Política de Privacidad"] = privacyResult.pass ? "PASS" : "FAIL";
    test.info().annotations.push({
      type: "Política de Privacidad URL",
      description: privacyResult.finalUrl,
    });
    console.log(`Política de Privacidad URL: ${privacyResult.finalUrl}`);
  } catch {
    report["Política de Privacidad"] = "FAIL";
  }

  // Step 10 - Final report.
  console.log("Final report:");
  console.table(report);

  for (const [section, status] of Object.entries(report)) {
    expect.soft(status, `${section} should pass`).toBe("PASS");
  }
});
