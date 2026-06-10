import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

type ValidationReport = Record<ReportField, "PASS" | "FAIL">;

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await page.waitForTimeout(500);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator.first()).toBeVisible();
  await locator.first().click();
  await waitForUi(page);
}

async function isVisible(locator: Locator, timeout = 15_000): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function findFirstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    if (await isVisible(candidate, 4_000)) {
      return candidate.first();
    }
  }

  return null;
}

async function capture(page: Page, testInfo: TestInfo, fileName: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function handleGoogleAccountSelection(authPage: Page): Promise<void> {
  const accountOption = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
  if (await isVisible(accountOption, 10_000)) {
    await clickAndWait(authPage, accountOption);
    return;
  }

  const emailInput = authPage.getByLabel(/email|correo/i);
  if (await isVisible(emailInput, 5_000)) {
    await emailInput.fill(GOOGLE_ACCOUNT_EMAIL);
    const nextButton =
      (await findFirstVisible([
        authPage.getByRole("button", { name: /^next$/i }),
        authPage.getByRole("button", { name: /^siguiente$/i })
      ])) ?? authPage.getByRole("button", { name: /next|siguiente/i }).first();
    await clickAndWait(authPage, nextButton);
  }
}

async function openLegalLinkAndValidate(params: {
  appPage: Page;
  link: Locator;
  heading: RegExp;
  screenshotName: string;
  testInfo: TestInfo;
}): Promise<{ valid: boolean; finalUrl: string }> {
  const { appPage, link, heading, screenshotName, testInfo } = params;
  const context = appPage.context();
  const appUrlBeforeClick = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);

  await clickAndWait(appPage, link);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  await waitForUi(legalPage);

  const headingVisible = await isVisible(legalPage.getByRole("heading", { name: heading }));
  const headingVisibleAsText = headingVisible || (await isVisible(legalPage.getByText(heading)));
  const legalTextVisible = await isVisible(
    legalPage.locator("main p, article p, section p, div p").filter({ hasText: /\S+/ }).first(),
    10_000
  );
  await capture(legalPage, testInfo, screenshotName, true);

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== appUrlBeforeClick) {
    await appPage.goBack().catch(() => undefined);
    await waitForUi(appPage);
  }

  return { valid: headingVisibleAsText && legalTextVisible, finalUrl };
}

test("SaleADS Mi Negocio full workflow", async ({ page }, testInfo) => {
  const report: ValidationReport = {
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

  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL (preferred) or SALEADS_BASE_URL to run this environment-agnostic workflow test."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  // Step 1: Login with Google
  const googleLoginButton = await findFirstVisible([
    page.getByRole("button", { name: /sign in with google|iniciar sesion con google|continuar con google/i }),
    page.getByRole("link", { name: /sign in with google|iniciar sesion con google|continuar con google/i }),
    page.getByText(/sign in with google|iniciar sesion con google|continuar con google/i)
  ]);

  if (googleLoginButton) {
    const popupPromise = page.context().waitForEvent("page", { timeout: 12_000 }).catch(() => null);
    await clickAndWait(page, googleLoginButton);
    const maybePopup = await popupPromise;
    if (maybePopup) {
      await waitForUi(maybePopup);
      await handleGoogleAccountSelection(maybePopup);
      await maybePopup.waitForEvent("close", { timeout: 60_000 }).catch(() => undefined);
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await handleGoogleAccountSelection(page);
      await waitForUi(page);
    }
  }

  const mainInterfaceVisible = await isVisible(page.locator("main, [role='main']").first(), 20_000);
  const leftSidebarVisible =
    (await isVisible(page.locator("aside").first(), 20_000)) ||
    (await isVisible(page.getByRole("navigation").first(), 20_000)) ||
    (await isVisible(page.getByText(/^Negocio$/i), 20_000));
  if (mainInterfaceVisible && leftSidebarVisible) {
    report.Login = "PASS";
  }
  await capture(page, testInfo, "01-dashboard-loaded.png");

  // Step 2: Open Mi Negocio menu
  const negocioSection = await findFirstVisible([
    page.getByRole("button", { name: /^Negocio$/i }),
    page.getByRole("link", { name: /^Negocio$/i }),
    page.getByText(/^Negocio$/i)
  ]);
  if (negocioSection) {
    await clickAndWait(page, negocioSection);
  }

  const miNegocioOption = await findFirstVisible([
    page.getByRole("button", { name: /^Mi Negocio$/i }),
    page.getByRole("link", { name: /^Mi Negocio$/i }),
    page.getByText(/^Mi Negocio$/i)
  ]);
  if (miNegocioOption) {
    await clickAndWait(page, miNegocioOption);
  }

  const agregarNegocioVisible = await isVisible(page.getByText(/^Agregar Negocio$/i), 10_000);
  const administrarNegociosVisible = await isVisible(page.getByText(/^Administrar Negocios$/i), 10_000);
  if (agregarNegocioVisible && administrarNegociosVisible) {
    report["Mi Negocio menu"] = "PASS";
  }
  await capture(page, testInfo, "02-mi-negocio-expanded-menu.png");

  // Step 3: Validate Agregar Negocio modal
  const agregarNegocioButton = await findFirstVisible([
    page.getByRole("button", { name: /^Agregar Negocio$/i }),
    page.getByRole("link", { name: /^Agregar Negocio$/i }),
    page.getByText(/^Agregar Negocio$/i)
  ]);

  if (agregarNegocioButton) {
    await clickAndWait(page, agregarNegocioButton);
    const modalTitleVisible = await isVisible(page.getByRole("heading", { name: /Crear Nuevo Negocio/i }));
    const businessNameInputVisible =
      (await isVisible(page.getByLabel(/Nombre del Negocio/i))) ||
      (await isVisible(page.getByPlaceholder(/Nombre del Negocio/i)));
    const planLimitVisible = await isVisible(page.getByText(/Tienes 2 de 3 negocios/i));
    const cancelButtonVisible = await isVisible(page.getByRole("button", { name: /^Cancelar$/i }));
    const createBusinessButtonVisible = await isVisible(page.getByRole("button", { name: /^Crear Negocio$/i }));

    if (
      modalTitleVisible &&
      businessNameInputVisible &&
      planLimitVisible &&
      cancelButtonVisible &&
      createBusinessButtonVisible
    ) {
      report["Agregar Negocio modal"] = "PASS";
    }

    await capture(page, testInfo, "03-agregar-negocio-modal.png");

    const businessNameInput =
      (await findFirstVisible([page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)])) ??
      page.locator("input").first();
    await businessNameInput.fill("Negocio Prueba Automatizacion");

    const cancelButton = await findFirstVisible([page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)]);
    if (cancelButton) {
      await clickAndWait(page, cancelButton);
    }
  }

  // Step 4: Open Administrar Negocios
  const administrarNegociosOption = await findFirstVisible([
    page.getByRole("button", { name: /^Administrar Negocios$/i }),
    page.getByRole("link", { name: /^Administrar Negocios$/i }),
    page.getByText(/^Administrar Negocios$/i)
  ]);

  if (!administrarNegociosOption) {
    const miNegocioToggle = await findFirstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);
    if (miNegocioToggle) {
      await clickAndWait(page, miNegocioToggle);
    }
  }

  const administrarNegociosOptionAfterExpand = await findFirstVisible([
    page.getByRole("button", { name: /^Administrar Negocios$/i }),
    page.getByRole("link", { name: /^Administrar Negocios$/i }),
    page.getByText(/^Administrar Negocios$/i)
  ]);
  if (administrarNegociosOptionAfterExpand) {
    await clickAndWait(page, administrarNegociosOptionAfterExpand);
  }

  const informacionGeneralVisible = await isVisible(page.getByText(/Informacion General|Información General/i), 15_000);
  const detallesCuentaVisible = await isVisible(page.getByText(/Detalles de la Cuenta/i), 15_000);
  const tusNegociosVisible = await isVisible(page.getByText(/^Tus Negocios$/i), 15_000);
  const seccionLegalVisible = await isVisible(page.getByText(/Seccion Legal|Sección Legal/i), 15_000);
  if (informacionGeneralVisible && detallesCuentaVisible && tusNegociosVisible && seccionLegalVisible) {
    report["Administrar Negocios view"] = "PASS";
  }
  await capture(page, testInfo, "04-administrar-negocios-page.png", true);

  // Step 5: Validate Informacion General
  const userNameVisible =
    (await isVisible(page.getByText(/Nombre/i))) || (await isVisible(page.locator("h1, h2, h3").filter({ hasText: /\S+/ }).first()));
  const userEmailVisible = await isVisible(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i));
  const businessPlanVisible = await isVisible(page.getByText(/BUSINESS PLAN/i));
  const cambiarPlanVisible = await isVisible(page.getByRole("button", { name: /Cambiar Plan/i }));
  if (userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible) {
    report["Informacion General"] = "PASS";
  }

  // Step 6: Validate Detalles de la Cuenta
  const cuentaCreadaVisible = await isVisible(page.getByText(/Cuenta creada/i));
  const estadoActivoVisible = await isVisible(page.getByText(/Estado activo/i));
  const idiomaSeleccionadoVisible = await isVisible(page.getByText(/Idioma seleccionado/i));
  if (cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible) {
    report["Detalles de la Cuenta"] = "PASS";
  }

  // Step 7: Validate Tus Negocios
  const businessListVisible =
    (await isVisible(page.getByRole("list").first(), 10_000)) ||
    (await isVisible(page.locator("table tbody tr").first(), 10_000)) ||
    (await isVisible(page.getByText(/Negocio/i), 10_000));
  const addBusinessButtonVisible = await isVisible(
    page
      .locator("section, div")
      .filter({ hasText: /^Tus Negocios$/i })
      .getByRole("button", { name: /Agregar Negocio/i })
      .first()
  );
  const businessLimitVisible = await isVisible(page.getByText(/Tienes 2 de 3 negocios/i));
  if (businessListVisible && addBusinessButtonVisible && businessLimitVisible) {
    report["Tus Negocios"] = "PASS";
  }

  // Step 8: Validate Terminos y Condiciones
  const terminosLink = await findFirstVisible([
    page.getByRole("link", { name: /Terminos y Condiciones|Términos y Condiciones/i }),
    page.getByText(/Terminos y Condiciones|Términos y Condiciones/i)
  ]);
  let terminosFinalUrl = "";
  if (terminosLink) {
    const result = await openLegalLinkAndValidate({
      appPage: page,
      link: terminosLink,
      heading: /Terminos y Condiciones|Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo
    });
    terminosFinalUrl = result.finalUrl;
    if (result.valid) {
      report["Terminos y Condiciones"] = "PASS";
    }
  }

  // Step 9: Validate Politica de Privacidad
  const privacidadLink = await findFirstVisible([
    page.getByRole("link", { name: /Politica de Privacidad|Política de Privacidad/i }),
    page.getByText(/Politica de Privacidad|Política de Privacidad/i)
  ]);
  let privacidadFinalUrl = "";
  if (privacidadLink) {
    const result = await openLegalLinkAndValidate({
      appPage: page,
      link: privacidadLink,
      heading: /Politica de Privacidad|Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo
    });
    privacidadFinalUrl = result.finalUrl;
    if (result.valid) {
      report["Politica de Privacidad"] = "PASS";
    }
  }

  // Step 10: Final report
  const finalReportOutput = {
    ...report,
    evidence: {
      terminosYCondicionesUrl: terminosFinalUrl,
      politicaDePrivacidadUrl: privacidadFinalUrl
    }
  };

  await testInfo.attach("saleads-mi-negocio-report", {
    body: Buffer.from(JSON.stringify(finalReportOutput, null, 2), "utf-8"),
    contentType: "application/json"
  });

  // eslint-disable-next-line no-console
  console.log("SaleADS Mi Negocio final report:", finalReportOutput);

  const failed = Object.entries(report).filter(([, status]) => status === "FAIL");
  expect(
    failed,
    `Workflow validations failed: ${failed.map(([label]) => label).join(", ")}`
  ).toEqual([]);
});
