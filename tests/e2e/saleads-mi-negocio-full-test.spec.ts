import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

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

const REPORT_FIELDS: ReportField[] = [
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

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

function toReportSummary(report: Record<ReportField, StepStatus>): string {
  return REPORT_FIELDS.map((field) => `${field}: ${report[field]}`).join("\n");
}

function initializeReport(): Record<ReportField, StepStatus> {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {} as Record<ReportField, StepStatus>);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");

  try {
    await page.waitForLoadState("networkidle", { timeout: 7_000 });
  } catch {
    // Some SaleADS pages keep open network connections; domcontentloaded is enough.
  }
}

async function screenshot(testInfo: TestInfo, page: Page, name: string): Promise<void> {
  await page.screenshot({ path: testInfo.outputPath(name), fullPage: true });
}

async function waitForVisible(locator: Locator, timeout = 12_000): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function getFirstVisible(candidates: Locator[], timeout = 12_000): Promise<Locator | null> {
  for (const locator of candidates) {
    if (await waitForVisible(locator, timeout)) {
      return locator.first();
    }
  }

  return null;
}

async function clickAndSettle(page: Page, locator: Locator): Promise<void> {
  await locator.first().click();
  await waitForUi(page);
}

async function isSidebarVisible(page: Page): Promise<boolean> {
  const sidebar = page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio/i });
  return waitForVisible(sidebar, 5_000);
}

async function ensureStartingPage(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    return;
  }

  const configuredUrl = process.env.SALEADS_URL;
  if (!configuredUrl) {
    throw new Error("Set SALEADS_URL to the SaleADS login page URL for this environment.");
  }

  await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);
}

async function tryGoogleAccountSelection(page: Page): Promise<void> {
  const accountOption = await getFirstVisible(
    [
      page.getByText(GOOGLE_ACCOUNT, { exact: true }),
      page.getByRole("button", { name: GOOGLE_ACCOUNT }),
      page.locator(`[data-email="${GOOGLE_ACCOUNT}"]`)
    ],
    8_000
  );

  if (accountOption) {
    await accountOption.click();
    await waitForUi(page);
    return;
  }

  const useAnotherAccount = await getFirstVisible(
    [page.getByText("Use another account"), page.getByText("Usar otra cuenta")],
    2_000
  );
  if (useAnotherAccount) {
    throw new Error(`Google account selector visible, but account '${GOOGLE_ACCOUNT}' not found.`);
  }
}

async function ensureMiNegocioExpanded(page: Page): Promise<boolean> {
  const agregarNegocio = page.getByText("Agregar Negocio", { exact: false });
  const administrarNegocios = page.getByText("Administrar Negocios", { exact: false });

  if ((await waitForVisible(agregarNegocio, 1_000)) && (await waitForVisible(administrarNegocios, 1_000))) {
    return true;
  }

  const negocioSection = await getFirstVisible(
    [page.getByText("Negocio", { exact: false }), page.getByRole("button", { name: /Negocio/i })],
    5_000
  );

  if (negocioSection) {
    await clickAndSettle(page, negocioSection);
  }

  const miNegocio = await getFirstVisible(
    [page.getByText("Mi Negocio", { exact: false }), page.getByRole("button", { name: /Mi Negocio/i })],
    5_000
  );

  if (miNegocio) {
    await clickAndSettle(page, miNegocio);
  }

  return (await waitForVisible(agregarNegocio, 8_000)) && (await waitForVisible(administrarNegocios, 8_000));
}

async function validateLegalPage(
  page: Page,
  linkText: string,
  headingText: string,
  testInfo: TestInfo
): Promise<{ ok: boolean; finalUrl: string }> {
  const context = page.context();
  const link = await getFirstVisible(
    [page.getByRole("link", { name: new RegExp(linkText, "i") }), page.getByText(linkText, { exact: false })],
    7_000
  );

  if (!link) {
    return { ok: false, finalUrl: page.url() };
  }

  const popupPromise = context.waitForEvent("page", { timeout: 5_000 }).catch(() => null);
  const initialUrl = page.url();

  await clickAndSettle(page, link);

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await waitForUi(legalPage);

  const heading = await getFirstVisible(
    [
      legalPage.getByRole("heading", { name: new RegExp(headingText, "i") }),
      legalPage.getByText(headingText, { exact: false })
    ],
    12_000
  );

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  const hasBodyContent = bodyText.length > 120;
  const hasHeading = Boolean(heading);
  const ok = hasHeading && hasBodyContent;

  await screenshot(
    testInfo,
    legalPage,
    `${linkText.toLowerCase().replace(/\s+/g, "-").replace(/[^\w-]/g, "")}.png`
  );

  const finalUrl = legalPage.url();
  testInfo.annotations.push({
    type: `final-url-${linkText.toLowerCase().replace(/\s+/g, "-")}`,
    description: finalUrl
  });

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== initialUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  return { ok, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.setTimeout(240_000);

  const report = initializeReport();

  await ensureStartingPage(page);

  // Step 1: Login with Google
  try {
    if (!(await isSidebarVisible(page))) {
      const loginButton = await getFirstVisible(
        [
          page.getByRole("button", { name: /Google|Sign in|Iniciar sesión/i }),
          page.getByRole("link", { name: /Google|Sign in|Iniciar sesión/i }),
          page.getByText("Sign in with Google", { exact: false }),
          page.getByText("Iniciar sesión con Google", { exact: false })
        ],
        12_000
      );

      if (!loginButton) {
        throw new Error("Google login trigger not found.");
      }

      const popupPromise = page.context().waitForEvent("page", { timeout: 7_000 }).catch(() => null);
      await clickAndSettle(page, loginButton);
      const popup = await popupPromise;

      if (popup) {
        await waitForUi(popup);
        await tryGoogleAccountSelection(popup);
      } else if (/accounts\.google\.com/i.test(page.url())) {
        await tryGoogleAccountSelection(page);
      }
    }

    const appLoaded = await isSidebarVisible(page);
    const mainInterfaceVisible =
      (await waitForVisible(page.locator("main"), 10_000)) || (await waitForVisible(page.locator("#root"), 10_000));

    report["Login"] = appLoaded && mainInterfaceVisible ? "PASS" : "FAIL";

    if (report["Login"] === "PASS") {
      await screenshot(testInfo, page, "01-dashboard.png");
    }
  } catch (error) {
    report["Login"] = "FAIL";
    testInfo.annotations.push({ type: "login-error", description: String(error) });
  }

  // Step 2: Open Mi Negocio menu
  try {
    const expanded = await ensureMiNegocioExpanded(page);
    const hasAgregar = await waitForVisible(page.getByText("Agregar Negocio", { exact: false }), 5_000);
    const hasAdministrar = await waitForVisible(page.getByText("Administrar Negocios", { exact: false }), 5_000);

    report["Mi Negocio menu"] = expanded && hasAgregar && hasAdministrar ? "PASS" : "FAIL";

    if (report["Mi Negocio menu"] === "PASS") {
      await screenshot(testInfo, page, "02-mi-negocio-expanded.png");
    }
  } catch (error) {
    report["Mi Negocio menu"] = "FAIL";
    testInfo.annotations.push({ type: "mi-negocio-menu-error", description: String(error) });
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const addBusinessAction = await getFirstVisible(
      [page.getByRole("menuitem", { name: /Agregar Negocio/i }), page.getByText("Agregar Negocio", { exact: false })],
      8_000
    );

    if (!addBusinessAction) {
      throw new Error("Agregar Negocio action not found.");
    }

    await clickAndSettle(page, addBusinessAction);

    const modalTitle = page.getByText("Crear Nuevo Negocio", { exact: false });
    const nameInput = page.getByLabel("Nombre del Negocio", { exact: false });
    const limitText = page.getByText("Tienes 2 de 3 negocios", { exact: false });
    const cancelButton = page.getByRole("button", { name: "Cancelar" });
    const createButton = page.getByRole("button", { name: "Crear Negocio" });

    const modalOk =
      (await waitForVisible(modalTitle, 7_000)) &&
      (await waitForVisible(nameInput, 7_000)) &&
      (await waitForVisible(limitText, 7_000)) &&
      (await waitForVisible(cancelButton, 7_000)) &&
      (await waitForVisible(createButton, 7_000));

    report["Agregar Negocio modal"] = modalOk ? "PASS" : "FAIL";

    if (modalOk) {
      await screenshot(testInfo, page, "03-agregar-negocio-modal.png");

      await nameInput.fill("Negocio Prueba Automatización");
      await clickAndSettle(page, cancelButton);
    }
  } catch (error) {
    report["Agregar Negocio modal"] = "FAIL";
    testInfo.annotations.push({ type: "agregar-negocio-error", description: String(error) });
  }

  // Step 4: Open Administrar Negocios
  try {
    await ensureMiNegocioExpanded(page);

    const adminBusiness = await getFirstVisible(
      [
        page.getByRole("menuitem", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText("Administrar Negocios", { exact: false })
      ],
      8_000
    );

    if (!adminBusiness) {
      throw new Error("Administrar Negocios action not found.");
    }

    await clickAndSettle(page, adminBusiness);

    const hasInfoGeneral = await waitForVisible(page.getByText("Información General", { exact: false }), 12_000);
    const hasDetalles = await waitForVisible(page.getByText("Detalles de la Cuenta", { exact: false }), 12_000);
    const hasTusNegocios = await waitForVisible(page.getByText("Tus Negocios", { exact: false }), 12_000);
    const hasLegal = await waitForVisible(page.getByText("Sección Legal", { exact: false }), 12_000);

    report["Administrar Negocios view"] =
      hasInfoGeneral && hasDetalles && hasTusNegocios && hasLegal ? "PASS" : "FAIL";

    if (report["Administrar Negocios view"] === "PASS") {
      await screenshot(testInfo, page, "04-administrar-negocios.png");
    }
  } catch (error) {
    report["Administrar Negocios view"] = "FAIL";
    testInfo.annotations.push({ type: "administrar-negocios-error", description: String(error) });
  }

  // Step 5: Validate Información General
  try {
    const infoSection = page
      .locator("section, div")
      .filter({ has: page.getByText("Información General", { exact: false }) })
      .first();
    const infoText = (await infoSection.innerText()).trim();

    const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoText);
    const hasBusinessPlan = await waitForVisible(page.getByText("BUSINESS PLAN", { exact: false }), 8_000);
    const hasChangePlanButton = await waitForVisible(page.getByRole("button", { name: /Cambiar Plan/i }), 8_000);
    const hasLikelyName = infoText
      .split("\n")
      .map((line) => line.trim())
      .some(
        (line) =>
          line.length >= 3 &&
          !/@/.test(line) &&
          !/información general|business plan|cambiar plan/i.test(line)
      );

    report["Información General"] = hasEmail && hasBusinessPlan && hasChangePlanButton && hasLikelyName ? "PASS" : "FAIL";
  } catch (error) {
    report["Información General"] = "FAIL";
    testInfo.annotations.push({ type: "informacion-general-error", description: String(error) });
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    const hasCuentaCreada = await waitForVisible(page.getByText("Cuenta creada", { exact: false }), 8_000);
    const hasEstadoActivo = await waitForVisible(page.getByText("Estado activo", { exact: false }), 8_000);
    const hasIdioma = await waitForVisible(page.getByText("Idioma seleccionado", { exact: false }), 8_000);

    report["Detalles de la Cuenta"] = hasCuentaCreada && hasEstadoActivo && hasIdioma ? "PASS" : "FAIL";
  } catch (error) {
    report["Detalles de la Cuenta"] = "FAIL";
    testInfo.annotations.push({ type: "detalles-cuenta-error", description: String(error) });
  }

  // Step 7: Validate Tus Negocios
  try {
    const tusNegociosSection = page
      .locator("section, div")
      .filter({ has: page.getByText("Tus Negocios", { exact: false }) })
      .first();
    const sectionText = (await tusNegociosSection.innerText()).trim();

    const hasBusinessList = sectionText.length > 50;
    const hasAddBusinessButton = await waitForVisible(page.getByRole("button", { name: "Agregar Negocio" }), 8_000);
    const hasLimitText = await waitForVisible(page.getByText("Tienes 2 de 3 negocios", { exact: false }), 8_000);

    report["Tus Negocios"] = hasBusinessList && hasAddBusinessButton && hasLimitText ? "PASS" : "FAIL";
  } catch (error) {
    report["Tus Negocios"] = "FAIL";
    testInfo.annotations.push({ type: "tus-negocios-error", description: String(error) });
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const termsResult = await validateLegalPage(page, "Términos y Condiciones", "Términos y Condiciones", testInfo);
    report["Términos y Condiciones"] = termsResult.ok ? "PASS" : "FAIL";
  } catch (error) {
    report["Términos y Condiciones"] = "FAIL";
    testInfo.annotations.push({ type: "terminos-error", description: String(error) });
  }

  // Step 9: Validate Política de Privacidad
  try {
    const policyResult = await validateLegalPage(page, "Política de Privacidad", "Política de Privacidad", testInfo);
    report["Política de Privacidad"] = policyResult.ok ? "PASS" : "FAIL";
  } catch (error) {
    report["Política de Privacidad"] = "FAIL";
    testInfo.annotations.push({ type: "politica-privacidad-error", description: String(error) });
  }

  // Step 10: Final Report
  const summary = toReportSummary(report);
  testInfo.annotations.push({ type: "final-report", description: summary });
  console.log("\n=== saleads_mi_negocio_full_test report ===\n" + summary + "\n");

  const failedFields = REPORT_FIELDS.filter((field) => report[field] === "FAIL");
  expect(failedFields, `Failed validations: ${failedFields.join(", ") || "none"}`).toEqual([]);
});
