import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

type Matcher = string | RegExp;
type SectionStatus = "PASS" | "FAIL";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
] as const;

const DEFAULT_ROLES: Array<
  "button" | "link" | "menuitem" | "tab" | "heading" | "navigation"
> = ["button", "link", "menuitem", "tab", "heading", "navigation"];

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function toRegExp(matcher: Matcher): RegExp {
  if (matcher instanceof RegExp) {
    return matcher;
  }

  return new RegExp(matcher.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForTimeout(700);
}

async function locatorVisible(locator: Locator, timeout = 2_500): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function findVisibleByText(
  scope: Page | Locator,
  matchers: Matcher[],
  roles = DEFAULT_ROLES
): Promise<Locator | null> {
  for (const matcher of matchers) {
    const pattern = toRegExp(matcher);

    for (const role of roles) {
      const roleLocator = scope.getByRole(role, { name: pattern }).first();
      if (await locatorVisible(roleLocator)) {
        return roleLocator;
      }
    }

    const textLocator = scope.getByText(pattern).first();
    if (await locatorVisible(textLocator)) {
      return textLocator;
    }
  }

  return null;
}

async function clickByVisibleText(
  page: Page,
  matchers: Matcher[],
  roles = DEFAULT_ROLES
): Promise<boolean> {
  const target = await findVisibleByText(page, matchers, roles);
  if (!target) {
    return false;
  }

  await target.scrollIntoViewIfNeeded().catch(() => undefined);
  await target.click();
  await waitForUi(page);

  return true;
}

async function isAnyVisible(scope: Page | Locator, matchers: Matcher[]): Promise<boolean> {
  return (await findVisibleByText(scope, matchers)) !== null;
}

async function takeCheckpoint(page: Page, testInfo: TestInfo, fileName: string, fullPage = false): Promise<void> {
  await page.screenshot({ path: testInfo.outputPath(fileName), fullPage });
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = new Map<string, { pass: boolean; details: string[] }>();
  for (const field of REPORT_FIELDS) {
    report.set(field, { pass: true, details: [] });
  }

  const mark = (section: (typeof REPORT_FIELDS)[number], check: boolean, detail: string) => {
    const current = report.get(section);
    if (!current) {
      return;
    }

    current.pass = current.pass && check;
    current.details.push(`${check ? "PASS" : "FAIL"} - ${detail}`);
  };

  const configuredUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL ||
    process.env.APP_BASE_URL;

  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  if (!configuredUrl && page.url() === "about:blank") {
    throw new Error(
      "No login URL configured. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL (or BASE_URL/APP_BASE_URL), or run this test from an already-open SaleADS login page."
    );
  }

  // Step 1: Login with Google.
  const googlePopupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  const loginClicked = await clickByVisibleText(page, [
    /Sign in with Google/i,
    /Iniciar sesi[oó]n con Google/i,
    /Continuar con Google/i,
    /^Google$/i
  ]);
  mark("Login", loginClicked, "Google login button is clickable");

  const popupPage = await googlePopupPromise;
  const authPage = popupPage ?? page;

  await waitForUi(authPage);
  const accountLocator = await findVisibleByText(authPage, [new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")], [
    "button",
    "link"
  ]);
  const accountVisible = accountLocator !== null;
  mark("Login", accountVisible, `Google account ${GOOGLE_ACCOUNT_EMAIL} appears`);
  if (accountLocator) {
    await accountLocator.click();
    await waitForUi(authPage);
  }

  if (popupPage) {
    await popupPage.waitForClose({ timeout: 60_000 }).catch(() => undefined);
    await page.bringToFront();
    await waitForUi(page);
  }

  const mainInterfaceVisible = await isAnyVisible(page, [/Dashboard/i, /Inicio/i, /Negocio/i, /Mi Negocio/i]);
  mark("Login", mainInterfaceVisible, "Main application interface appears");

  const sidebarVisible = await isAnyVisible(page, [/Negocio/i, /Mi Negocio/i, /Administrar Negocios/i]);
  mark("Login", sidebarVisible, "Left sidebar navigation is visible");
  await takeCheckpoint(page, testInfo, "01-dashboard-loaded.png");

  // Step 2: Open Mi Negocio menu.
  const negocioSectionClicked = await clickByVisibleText(page, [/^Negocio$/i, /Negocio/i]);
  mark("Mi Negocio menu", negocioSectionClicked, "Sidebar section Negocio can be opened");

  const miNegocioClicked = await clickByVisibleText(page, [/^Mi Negocio$/i, /Mi Negocio/i]);
  mark("Mi Negocio menu", miNegocioClicked, "Mi Negocio option can be selected");

  const agregarNegocioMenuVisible = await isAnyVisible(page, [/Agregar Negocio/i]);
  mark("Mi Negocio menu", agregarNegocioMenuVisible, "'Agregar Negocio' is visible in submenu");

  const administrarNegociosMenuVisible = await isAnyVisible(page, [/Administrar Negocios/i]);
  mark("Mi Negocio menu", administrarNegociosMenuVisible, "'Administrar Negocios' is visible in submenu");
  await takeCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");

  // Step 3: Validate Agregar Negocio modal.
  const agregarNegocioClicked = await clickByVisibleText(page, [/^Agregar Negocio$/i, /Agregar Negocio/i]);
  mark("Agregar Negocio modal", agregarNegocioClicked, "Agregar Negocio action is clickable");

  const modalTitleVisible = await isAnyVisible(page, [/Crear Nuevo Negocio/i]);
  mark("Agregar Negocio modal", modalTitleVisible, "Modal title 'Crear Nuevo Negocio' is visible");

  const nombreInputVisible = await isAnyVisible(page, [/Nombre del Negocio/i]);
  mark("Agregar Negocio modal", nombreInputVisible, "Input 'Nombre del Negocio' exists");

  const quotaVisible = await isAnyVisible(page, [/Tienes\s*2\s*de\s*3\s*negocios/i]);
  mark("Agregar Negocio modal", quotaVisible, "Text 'Tienes 2 de 3 negocios' is visible");

  const cancelVisible = await isAnyVisible(page, [/^Cancelar$/i, /Cancelar/i]);
  mark("Agregar Negocio modal", cancelVisible, "Button 'Cancelar' is present");

  const createVisible = await isAnyVisible(page, [/Crear Negocio/i]);
  mark("Agregar Negocio modal", createVisible, "Button 'Crear Negocio' is present");
  await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

  const nombreInput = await findVisibleByText(page, [/Nombre del Negocio/i], ["button", "link", "heading", "navigation"]);
  if (nombreInput) {
    const input =
      page.getByLabel(/Nombre del Negocio/i).first().or(page.getByPlaceholder(/Nombre del Negocio/i).first());
    if (await locatorVisible(input, 1_500)) {
      await input.click();
      await input.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }
  }
  await clickByVisibleText(page, [/^Cancelar$/i, /Cancelar/i]);

  // Step 4: Open Administrar Negocios.
  await clickByVisibleText(page, [/^Mi Negocio$/i, /Mi Negocio/i]);
  const administrarClicked = await clickByVisibleText(page, [/Administrar Negocios/i]);
  mark("Administrar Negocios view", administrarClicked, "Administrar Negocios option can be opened");

  const infoGeneralSectionVisible = await isAnyVisible(page, [/Informaci[oó]n General/i]);
  mark("Administrar Negocios view", infoGeneralSectionVisible, "Section 'Información General' exists");

  const accountDetailsVisible = await isAnyVisible(page, [/Detalles de la Cuenta/i]);
  mark("Administrar Negocios view", accountDetailsVisible, "Section 'Detalles de la Cuenta' exists");

  const negociosSectionVisible = await isAnyVisible(page, [/Tus Negocios/i]);
  mark("Administrar Negocios view", negociosSectionVisible, "Section 'Tus Negocios' exists");

  const legalSectionVisible = await isAnyVisible(page, [/Secci[oó]n Legal/i, /Legal/i]);
  mark("Administrar Negocios view", legalSectionVisible, "Section 'Sección Legal' exists");
  await takeCheckpoint(page, testInfo, "04-administrar-negocios-page.png", true);

  const accountPageUrl = page.url();

  // Step 5: Validate Información General.
  const infoSection = page.getByText(/Informaci[oó]n General/i).first();
  const infoSectionPresent = await locatorVisible(infoSection, 2_500);
  const userEmailVisible = await isAnyVisible(page, [/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i]);
  const businessPlanVisible = await isAnyVisible(page, [/BUSINESS PLAN/i]);
  const cambiarPlanVisible = await isAnyVisible(page, [/Cambiar Plan/i]);
  const likelyNameVisible =
    userEmailVisible &&
    (await isAnyVisible(page, [/Juan/i, /Lucas/i, /Barbier/i, /Garzon/i, /Perfil/i, /Usuario/i]));

  mark("Información General", infoSectionPresent, "Información General section heading is visible");
  mark("Información General", userEmailVisible, "User email is visible");
  mark("Información General", businessPlanVisible, "Text 'BUSINESS PLAN' is visible");
  mark("Información General", cambiarPlanVisible, "Button 'Cambiar Plan' is visible");
  mark("Información General", likelyNameVisible, "User name indicator is visible");

  // Step 6: Validate Detalles de la Cuenta.
  const cuentaCreadaVisible = await isAnyVisible(page, [/Cuenta creada/i]);
  const estadoActivoVisible = await isAnyVisible(page, [/Estado activo/i]);
  const idiomaSeleccionadoVisible = await isAnyVisible(page, [/Idioma seleccionado/i]);

  mark("Detalles de la Cuenta", cuentaCreadaVisible, "'Cuenta creada' is visible");
  mark("Detalles de la Cuenta", estadoActivoVisible, "'Estado activo' is visible");
  mark("Detalles de la Cuenta", idiomaSeleccionadoVisible, "'Idioma seleccionado' is visible");

  // Step 7: Validate Tus Negocios.
  const businessListVisible = await isAnyVisible(page, [/Tus Negocios/i, /Negocio/i]);
  const addBusinessButtonVisible = await isAnyVisible(page, [/Agregar Negocio/i]);
  const accountQuotaVisible = await isAnyVisible(page, [/Tienes\s*2\s*de\s*3\s*negocios/i]);

  mark("Tus Negocios", businessListVisible, "Business list section is visible");
  mark("Tus Negocios", addBusinessButtonVisible, "Button 'Agregar Negocio' exists");
  mark("Tus Negocios", accountQuotaVisible, "Text 'Tienes 2 de 3 negocios' is visible");

  // Step 8 and 9: Legal links validation.
  const legalOutcomes: Record<"Términos y Condiciones" | "Política de Privacidad", { url: string | null; pass: boolean }> =
    {
      "Términos y Condiciones": { url: null, pass: true },
      "Política de Privacidad": { url: null, pass: true }
    };

  const validateLegalPage = async (
    section: "Términos y Condiciones" | "Política de Privacidad",
    linkMatchers: Matcher[],
    headingMatcher: Matcher,
    screenshotFile: string
  ) => {
    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    const beforeUrl = page.url();
    const clicked = await clickByVisibleText(page, linkMatchers);
    mark(section, clicked, `${section} link is clickable`);

    const maybePopup = await popupPromise;
    const legalPage = maybePopup ?? page;
    await waitForUi(legalPage);

    const headingVisible = await isAnyVisible(legalPage, [headingMatcher]);
    mark(section, headingVisible, `${section} heading is visible`);

    const legalTextVisible =
      (await isAnyVisible(legalPage, [/T[eé]rminos/i, /Condiciones/i, /Pol[ií]tica/i, /Privacidad/i])) ||
      (await locatorVisible(legalPage.locator("p").first(), 2_000));
    mark(section, legalTextVisible, `${section} legal content text is visible`);

    legalOutcomes[section].url = legalPage.url();
    await takeCheckpoint(legalPage, testInfo, screenshotFile, true);

    if (maybePopup) {
      await maybePopup.close().catch(() => undefined);
      await page.bringToFront();
      await waitForUi(page);
    } else if (page.url() !== beforeUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        await page.goto(accountPageUrl, { waitUntil: "domcontentloaded" });
      });
      await waitForUi(page);
    }
  };

  await validateLegalPage(
    "Términos y Condiciones",
    [/T[eé]rminos y Condiciones/i],
    /T[eé]rminos y Condiciones/i,
    "05-terminos-y-condiciones.png"
  );

  await validateLegalPage(
    "Política de Privacidad",
    [/Pol[ií]tica de Privacidad/i],
    /Pol[ií]tica de Privacidad/i,
    "06-politica-de-privacidad.png"
  );

  const summary: Array<{ section: string; status: SectionStatus; details: string[] }> = REPORT_FIELDS.map((field) => {
    const state = report.get(field);
    return {
      section: field,
      status: state?.pass ? "PASS" : "FAIL",
      details: state?.details ?? []
    };
  });

  const finalReport = {
    test: "saleads_mi_negocio_full_test",
    summary,
    evidence: {
      dashboardScreenshot: "01-dashboard-loaded.png",
      menuScreenshot: "02-mi-negocio-menu-expanded.png",
      modalScreenshot: "03-agregar-negocio-modal.png",
      accountPageScreenshot: "04-administrar-negocios-page.png",
      terminosScreenshot: "05-terminos-y-condiciones.png",
      privacidadScreenshot: "06-politica-de-privacidad.png",
      terminosUrl: legalOutcomes["Términos y Condiciones"].url,
      privacidadUrl: legalOutcomes["Política de Privacidad"].url
    }
  };

  await testInfo.attach("final-report.json", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });

  // Expose PASS/FAIL table in test output.
  console.table(
    summary.map((item) => ({
      validation: item.section,
      result: item.status
    }))
  );
  console.log("Términos y Condiciones URL:", legalOutcomes["Términos y Condiciones"].url);
  console.log("Política de Privacidad URL:", legalOutcomes["Política de Privacidad"].url);

  const failedSections = summary.filter((item) => item.status === "FAIL").map((item) => item.section);
  expect(
    failedSections,
    `Validation failures in: ${failedSections.join(", ")}. Check final-report.json attachment for details.`
  ).toEqual([]);
});
