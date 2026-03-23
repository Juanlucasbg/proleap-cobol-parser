import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type Report = {
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const SCREENSHOT_DIR = "screenshots";

function textRegex(text: string): RegExp {
  return new RegExp(text, "i");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

async function isVisible(locator: Locator, timeout = 5000): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickFirstVisible(page: Page, labels: RegExp[]): Promise<boolean> {
  const candidates: Locator[] = [];

  for (const label of labels) {
    candidates.push(page.getByRole("button", { name: label }).first());
    candidates.push(page.getByRole("link", { name: label }).first());
    candidates.push(page.getByRole("menuitem", { name: label }).first());
    candidates.push(page.getByRole("tab", { name: label }).first());
    candidates.push(page.getByText(label).first());
  }

  for (const locator of candidates) {
    if (await isVisible(locator, 2000)) {
      await locator.click();
      await waitForUi(page);
      return true;
    }
  }

  return false;
}

async function expectAllVisible(locators: Locator[]): Promise<boolean> {
  let allVisible = true;
  for (const locator of locators) {
    const visible = await isVisible(locator, 10000);
    expect.soft(visible).toBeTruthy();
    allVisible = allVisible && visible;
  }
  return allVisible;
}

async function clickLegalLinkAndValidate(opts: {
  context: BrowserContext;
  appPage: Page;
  linkRegex: RegExp;
  headingRegex: RegExp;
  screenshotName: string;
  appReturnUrl: string;
}): Promise<{ passed: boolean; finalUrl: string }> {
  const { context, appPage, linkRegex, headingRegex, screenshotName, appReturnUrl } = opts;

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const clicked = await clickFirstVisible(appPage, [linkRegex]);
  if (!clicked) {
    return { passed: false, finalUrl: appPage.url() };
  }

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForTimeout(1200);

  const headingVisible = await isVisible(legalPage.getByRole("heading", { name: headingRegex }).first(), 10000)
    || await isVisible(legalPage.getByText(headingRegex).first(), 10000);
  const legalContentVisible = await isVisible(
    legalPage.locator("main,article,section,body").getByText(/(t[eé]rminos|condiciones|privacidad|datos|uso)/i).first(),
    10000,
  );

  await legalPage.screenshot({ path: `${SCREENSHOT_DIR}/${screenshotName}.png`, fullPage: true });
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goto(appReturnUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(appPage);
  }

  return {
    passed: headingVisible && legalContentVisible,
    finalUrl,
  };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report: Report = {
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

  const configuredUrl = process.env.SALEADS_URL;
  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  // Step 1: Login with Google
  try {
    const googlePopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    const loginClicked = await clickFirstVisible(page, [
      textRegex("Sign in with Google"),
      textRegex("Iniciar sesi[oó]n con Google"),
      textRegex("Continuar con Google"),
      textRegex("Google"),
    ]);

    expect.soft(loginClicked).toBeTruthy();

    // Some environments show Google account selection in a popup or in the same page.
    const googlePopup = await googlePopupPromise;
    const googlePage = googlePopup ?? page;

    const googleAccountSelector = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL).first();
    if (await isVisible(googleAccountSelector, 4000)) {
      await googleAccountSelector.click();
      await waitForUi(googlePage);
    } else {
      const altGoogleAccount = googlePage.getByRole("button", { name: textRegex(GOOGLE_ACCOUNT_EMAIL) }).first();
      if (await isVisible(altGoogleAccount, 4000)) {
        await altGoogleAccount.click();
        await waitForUi(googlePage);
      }
    }

    if (googlePopup) {
      await googlePopup.waitForEvent("close", { timeout: 20000 }).catch(() => null);
      await page.bringToFront();
    }

    await waitForUi(page);

    const mainInterfaceVisible = await isVisible(page.locator("main, [role='main']").first(), 15000);
    const leftSidebarVisible = await isVisible(page.locator("aside, nav").first(), 15000);
    expect.soft(mainInterfaceVisible).toBeTruthy();
    expect.soft(leftSidebarVisible).toBeTruthy();

    if (mainInterfaceVisible && leftSidebarVisible) {
      await page.screenshot({ path: `${SCREENSHOT_DIR}/step1-dashboard-loaded.png`, fullPage: true });
      report.Login = "PASS";
    }
  } catch (error) {
    console.error("Step Login with Google failed:", error);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await isVisible(page.getByText(/Negocio/i).first(), 15000);
    const miNegocioClicked = await clickFirstVisible(page, [textRegex("Mi Negocio")]);
    expect.soft(miNegocioClicked).toBeTruthy();

    const submenuExpanded = await expectAllVisible([
      page.getByText(/Agregar Negocio/i).first(),
      page.getByText(/Administrar Negocios/i).first(),
    ]);

    if (submenuExpanded) {
      await page.screenshot({ path: `${SCREENSHOT_DIR}/step2-mi-negocio-expanded.png`, fullPage: true });
      report["Mi Negocio menu"] = "PASS";
    }
  } catch (error) {
    console.error("Step Open Mi Negocio menu failed:", error);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const addBusinessClicked = await clickFirstVisible(page, [textRegex("Agregar Negocio")]);
    expect.soft(addBusinessClicked).toBeTruthy();

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
    const businessCountText = page.getByText(/Tienes 2 de 3 negocios/i).first();
    const cancelButton = page.getByRole("button", { name: /Cancelar/i }).first();
    const createButton = page.getByRole("button", { name: /Crear Negocio/i }).first();

    const modalChecks = await expectAllVisible([
      modalTitle,
      businessNameInput,
      businessCountText,
      cancelButton,
      createButton,
    ]);

    if (modalChecks) {
      await page.screenshot({ path: `${SCREENSHOT_DIR}/step3-crear-nuevo-negocio-modal.png`, fullPage: true });

      // Optional actions requested in the workflow.
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await cancelButton.click();
      await waitForUi(page);

      report["Agregar Negocio modal"] = "PASS";
    }
  } catch (error) {
    console.error("Step Validate Agregar Negocio modal failed:", error);
  }

  // Step 4: Open Administrar Negocios
  let accountPageUrl = page.url();
  try {
    if (!(await isVisible(page.getByText(/Administrar Negocios/i).first(), 3000))) {
      await clickFirstVisible(page, [textRegex("Mi Negocio")]);
    }

    const manageClicked = await clickFirstVisible(page, [textRegex("Administrar Negocios")]);
    expect.soft(manageClicked).toBeTruthy();

    const accountSectionsVisible = await expectAllVisible([
      page.getByText(/Informaci[oó]n General/i).first(),
      page.getByText(/Detalles de la Cuenta/i).first(),
      page.getByText(/Tus Negocios/i).first(),
      page.getByText(/Secci[oó]n Legal/i).first(),
    ]);

    if (accountSectionsVisible) {
      accountPageUrl = page.url();
      await page.screenshot({ path: `${SCREENSHOT_DIR}/step4-administrar-negocios-view.png`, fullPage: true });
      report["Administrar Negocios view"] = "PASS";
    }
  } catch (error) {
    console.error("Step Open Administrar Negocios failed:", error);
  }

  // Step 5: Validate Información General
  try {
    const userEmailVisible = await isVisible(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first(), 10000);
    const userNameVisible = await isVisible(
      page.getByText(/(Nombre|Usuario|Perfil|juan|Juan)/i).first(),
      10000,
    );
    const businessPlanVisible = await isVisible(page.getByText(/BUSINESS PLAN/i).first(), 10000);
    const changePlanVisible = await isVisible(page.getByRole("button", { name: /Cambiar Plan/i }).first(), 10000);

    expect.soft(userNameVisible).toBeTruthy();
    expect.soft(userEmailVisible).toBeTruthy();
    expect.soft(businessPlanVisible).toBeTruthy();
    expect.soft(changePlanVisible).toBeTruthy();

    if (userNameVisible && userEmailVisible && businessPlanVisible && changePlanVisible) {
      report["Información General"] = "PASS";
    }
  } catch (error) {
    console.error("Step Validate Información General failed:", error);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    const detailsVisible = await expectAllVisible([
      page.getByText(/Cuenta creada/i).first(),
      page.getByText(/Estado activo/i).first(),
      page.getByText(/Idioma seleccionado/i).first(),
    ]);

    if (detailsVisible) {
      report["Detalles de la Cuenta"] = "PASS";
    }
  } catch (error) {
    console.error("Step Validate Detalles de la Cuenta failed:", error);
  }

  // Step 7: Validate Tus Negocios
  try {
    const tusNegociosSection = page.getByText(/Tus Negocios/i).first();
    const listVisible = await isVisible(tusNegociosSection, 10000);
    const addBusinessButtonVisible = await isVisible(page.getByRole("button", { name: /Agregar Negocio/i }).first(), 10000)
      || await isVisible(page.getByRole("link", { name: /Agregar Negocio/i }).first(), 10000);
    const businessLimitVisible = await isVisible(page.getByText(/Tienes 2 de 3 negocios/i).first(), 10000);

    expect.soft(listVisible).toBeTruthy();
    expect.soft(addBusinessButtonVisible).toBeTruthy();
    expect.soft(businessLimitVisible).toBeTruthy();

    if (listVisible && addBusinessButtonVisible && businessLimitVisible) {
      report["Tus Negocios"] = "PASS";
    }
  } catch (error) {
    console.error("Step Validate Tus Negocios failed:", error);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const terminosResult = await clickLegalLinkAndValidate({
      context,
      appPage: page,
      linkRegex: /T[eé]rminos y Condiciones/i,
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotName: "step8-terminos-y-condiciones",
      appReturnUrl: accountPageUrl,
    });

    console.log(`TERMINOS_Y_CONDICIONES_URL=${terminosResult.finalUrl}`);
    if (terminosResult.passed) {
      report["Términos y Condiciones"] = "PASS";
    }
  } catch (error) {
    console.error("Step Validate Términos y Condiciones failed:", error);
  }

  // Step 9: Validate Política de Privacidad
  try {
    const privacyResult = await clickLegalLinkAndValidate({
      context,
      appPage: page,
      linkRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "step9-politica-de-privacidad",
      appReturnUrl: accountPageUrl,
    });

    console.log(`POLITICA_DE_PRIVACIDAD_URL=${privacyResult.finalUrl}`);
    if (privacyResult.passed) {
      report["Política de Privacidad"] = "PASS";
    }
  } catch (error) {
    console.error("Step Validate Política de Privacidad failed:", error);
  }

  // Step 10: Final Report
  console.log(`FINAL_REPORT=${JSON.stringify(report)}`);

  const allPassed = Object.values(report).every((status) => status === "PASS");
  expect(allPassed, `Validation report: ${JSON.stringify(report, null, 2)}`).toBeTruthy();
});
