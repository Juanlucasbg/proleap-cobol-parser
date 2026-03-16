const fs = require("fs");
const path = require("path");
const { test } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function sanitizeFilePart(value) {
  return value.toLowerCase().replace(/[^a-z0-9_-]+/g, "-");
}

async function waitForUi(page) {
  await page.waitForTimeout(700);
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
}

async function isVisible(locator, timeout = 6_000) {
  try {
    return await locator.first().isVisible({ timeout });
  } catch {
    return false;
  }
}

async function getVisibleByText(page, labels, timeout = 8_000) {
  for (const label of labels) {
    const asButton = page.getByRole("button", { name: new RegExp(escapeRegex(label), "i") });
    if (await isVisible(asButton, timeout)) return asButton.first();

    const asLink = page.getByRole("link", { name: new RegExp(escapeRegex(label), "i") });
    if (await isVisible(asLink, timeout)) return asLink.first();

    const asMenuItem = page.getByRole("menuitem", {
      name: new RegExp(escapeRegex(label), "i"),
    });
    if (await isVisible(asMenuItem, timeout)) return asMenuItem.first();

    const exactText = page.getByText(new RegExp(`^\\s*${escapeRegex(label)}\\s*$`, "i"));
    if (await isVisible(exactText, timeout)) return exactText.first();

    const looseText = page.getByText(new RegExp(escapeRegex(label), "i"));
    if (await isVisible(looseText, timeout)) return looseText.first();
  }

  return null;
}

async function clickByVisibleText(page, labels, timeout = 8_000) {
  const target = await getVisibleByText(page, labels, timeout);
  if (!target) {
    throw new Error(`Unable to find visible element with labels: ${labels.join(", ")}`);
  }

  await target.click();
  await waitForUi(page);
}

async function textVisible(page, labels, timeout = 8_000) {
  const target = await getVisibleByText(page, labels, timeout);
  return Boolean(target);
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.resolve(__dirname, "../artifacts");
  const screenshotsDir = path.join(artifactsDir, `screenshots-${runId}`);
  fs.mkdirSync(screenshotsDir, { recursive: true });

  let screenshotCounter = 0;
  const report = {
    Login: { status: "FAIL", details: [] },
    "Mi Negocio menu": { status: "FAIL", details: [] },
    "Agregar Negocio modal": { status: "FAIL", details: [] },
    "Administrar Negocios view": { status: "FAIL", details: [] },
    "Información General": { status: "FAIL", details: [] },
    "Detalles de la Cuenta": { status: "FAIL", details: [] },
    "Tus Negocios": { status: "FAIL", details: [] },
    "Términos y Condiciones": { status: "FAIL", details: [] },
    "Política de Privacidad": { status: "FAIL", details: [] },
  };

  const legalUrls = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null,
  };

  const takeScreenshot = async (targetPage, label, fullPage = false) => {
    screenshotCounter += 1;
    const fileName = `${String(screenshotCounter).padStart(2, "0")}_${sanitizeFilePart(label)}.png`;
    const filePath = path.join(screenshotsDir, fileName);
    await targetPage.screenshot({ path: filePath, fullPage });
    return filePath;
  };

  const setStep = (field, passed, details) => {
    report[field] = {
      status: passed ? "PASS" : "FAIL",
      details: details.filter(Boolean),
    };
  };

  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    process.env.APP_URL;

  const currentUrl = page.url();

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (currentUrl && currentUrl !== "about:blank") {
    await waitForUi(page);
  } else {
    throw new Error(
      "No login URL provided and no preloaded page detected. Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL / APP_URL), or run this test with the SaleADS login page already opened."
    );
  }

  // Step 1: Login with Google
  try {
    const sidebarVisibleBeforeLogin = await textVisible(page, ["Mi Negocio", "Negocio"], 4_000);
    if (!sidebarVisibleBeforeLogin) {
      const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
      await clickByVisibleText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Login with Google",
        "Google",
      ]);
      const googlePage = await popupPromise;
      const authPage = googlePage || page;
      await waitForUi(authPage);

      const accountOption = await getVisibleByText(authPage, [GOOGLE_ACCOUNT_EMAIL], 8_000);
      if (accountOption) {
        await accountOption.click();
        await waitForUi(authPage);
      }

      if (googlePage) {
        await googlePage.waitForEvent("close", { timeout: 20_000 }).catch(() => {});
      }
    }

    await waitForUi(page);
    const appVisible = await textVisible(page, ["Mi Negocio", "Negocio", "Dashboard"], 20_000);
    const sidebarVisible = await isVisible(page.locator("aside, nav"), 15_000);
    const dashboardScreenshot = await takeScreenshot(page, "dashboard_loaded");
    setStep("Login", appVisible && sidebarVisible, [
      `Main interface visible: ${appVisible}`,
      `Left sidebar visible: ${sidebarVisible}`,
      `Screenshot: ${dashboardScreenshot}`,
    ]);
  } catch (error) {
    const failureShot = await takeScreenshot(page, "login_failure").catch(() => null);
    setStep("Login", false, [`Error: ${error.message}`, failureShot ? `Screenshot: ${failureShot}` : ""]);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
    if (!(await textVisible(page, ["Mi Negocio"], 6_000))) {
      await clickByVisibleText(page, ["Mi Negocio"]);
    }

    const addBusinessVisible = await textVisible(page, ["Agregar Negocio"], 10_000);
    const manageBusinessVisible = await textVisible(page, ["Administrar Negocios"], 10_000);
    const menuScreenshot = await takeScreenshot(page, "mi_negocio_menu_expanded");
    setStep("Mi Negocio menu", addBusinessVisible && manageBusinessVisible, [
      `Submenu expanded: ${addBusinessVisible || manageBusinessVisible}`,
      `"Agregar Negocio" visible: ${addBusinessVisible}`,
      `"Administrar Negocios" visible: ${manageBusinessVisible}`,
      `Screenshot: ${menuScreenshot}`,
    ]);
  } catch (error) {
    const failureShot = await takeScreenshot(page, "mi_negocio_menu_failure").catch(() => null);
    setStep("Mi Negocio menu", false, [
      `Error: ${error.message}`,
      failureShot ? `Screenshot: ${failureShot}` : "",
    ]);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await clickByVisibleText(page, ["Agregar Negocio"]);
    const titleVisible = await textVisible(page, ["Crear Nuevo Negocio"], 10_000);
    const nameInputVisible = await isVisible(page.getByPlaceholder("Nombre del Negocio"), 5_000);
    const nameLabelVisible = await textVisible(page, ["Nombre del Negocio"], 4_000);
    const quotaTextVisible = await textVisible(page, ["Tienes 2 de 3 negocios"], 5_000);
    const cancelVisible = await textVisible(page, ["Cancelar"], 5_000);
    const createVisible = await textVisible(page, ["Crear Negocio"], 5_000);
    const modalScreenshot = await takeScreenshot(page, "agregar_negocio_modal");

    const input = page.getByPlaceholder("Nombre del Negocio");
    if (await isVisible(input, 4_000)) {
      await input.click();
      await input.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }

    await clickByVisibleText(page, ["Cancelar"]);
    setStep(
      "Agregar Negocio modal",
      titleVisible && (nameInputVisible || nameLabelVisible) && quotaTextVisible && cancelVisible && createVisible,
      [
        `"Crear Nuevo Negocio" visible: ${titleVisible}`,
        `"Nombre del Negocio" input/label visible: ${nameInputVisible || nameLabelVisible}`,
        `"Tienes 2 de 3 negocios" visible: ${quotaTextVisible}`,
        `"Cancelar" button visible: ${cancelVisible}`,
        `"Crear Negocio" button visible: ${createVisible}`,
        `Screenshot: ${modalScreenshot}`,
      ]
    );
  } catch (error) {
    const failureShot = await takeScreenshot(page, "agregar_negocio_modal_failure").catch(() => null);
    setStep("Agregar Negocio modal", false, [
      `Error: ${error.message}`,
      failureShot ? `Screenshot: ${failureShot}` : "",
    ]);
  }

  // Step 4: Open Administrar Negocios
  try {
    const manageVisible = await textVisible(page, ["Administrar Negocios"], 4_000);
    if (!manageVisible) {
      await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
    }
    await clickByVisibleText(page, ["Administrar Negocios"]);

    const infoGeneral = await textVisible(page, ["Información General"], 20_000);
    const accountDetails = await textVisible(page, ["Detalles de la Cuenta"], 20_000);
    const businesses = await textVisible(page, ["Tus Negocios"], 20_000);
    const legalSection = await textVisible(page, ["Sección Legal"], 20_000);
    const accountScreenshot = await takeScreenshot(page, "administrar_negocios_account_page", true);
    setStep("Administrar Negocios view", infoGeneral && accountDetails && businesses && legalSection, [
      `"Información General" visible: ${infoGeneral}`,
      `"Detalles de la Cuenta" visible: ${accountDetails}`,
      `"Tus Negocios" visible: ${businesses}`,
      `"Sección Legal" visible: ${legalSection}`,
      `Screenshot: ${accountScreenshot}`,
    ]);
  } catch (error) {
    const failureShot = await takeScreenshot(page, "administrar_negocios_failure").catch(() => null);
    setStep("Administrar Negocios view", false, [
      `Error: ${error.message}`,
      failureShot ? `Screenshot: ${failureShot}` : "",
    ]);
  }

  // Step 5: Validate Información General
  try {
    const userNameVisible =
      (await isVisible(page.locator('[data-testid*="name"], [class*="name"], [id*="name"]'), 3_000)) ||
      (await textVisible(page, ["Nombre", "User"], 4_000));
    const emailVisible = await isVisible(
      page.locator(`text=/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}/`),
      8_000
    );
    const planVisible = await textVisible(page, ["BUSINESS PLAN"], 8_000);
    const changePlanVisible = await textVisible(page, ["Cambiar Plan"], 8_000);

    setStep("Información General", userNameVisible && emailVisible && planVisible && changePlanVisible, [
      `User name visible: ${userNameVisible}`,
      `User email visible: ${emailVisible}`,
      `"BUSINESS PLAN" visible: ${planVisible}`,
      `"Cambiar Plan" visible: ${changePlanVisible}`,
    ]);
  } catch (error) {
    setStep("Información General", false, [`Error: ${error.message}`]);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    const createdVisible = await textVisible(page, ["Cuenta creada"], 8_000);
    const activeVisible = await textVisible(page, ["Estado activo"], 8_000);
    const languageVisible = await textVisible(page, ["Idioma seleccionado"], 8_000);
    setStep("Detalles de la Cuenta", createdVisible && activeVisible && languageVisible, [
      `"Cuenta creada" visible: ${createdVisible}`,
      `"Estado activo" visible: ${activeVisible}`,
      `"Idioma seleccionado" visible: ${languageVisible}`,
    ]);
  } catch (error) {
    setStep("Detalles de la Cuenta", false, [`Error: ${error.message}`]);
  }

  // Step 7: Validate Tus Negocios
  try {
    const listVisible =
      (await isVisible(page.locator("table, [role='table'], ul, [data-testid*='business']"), 8_000)) ||
      (await textVisible(page, ["Tus Negocios"], 5_000));
    const addBusinessButtonVisible = await textVisible(page, ["Agregar Negocio"], 8_000);
    const quotaVisible = await textVisible(page, ["Tienes 2 de 3 negocios"], 8_000);
    setStep("Tus Negocios", listVisible && addBusinessButtonVisible && quotaVisible, [
      `Business list visible: ${listVisible}`,
      `"Agregar Negocio" button visible: ${addBusinessButtonVisible}`,
      `"Tienes 2 de 3 negocios" visible: ${quotaVisible}`,
    ]);
  } catch (error) {
    setStep("Tus Negocios", false, [`Error: ${error.message}`]);
  }

  const openLegalPage = async (triggerText, headingText, reportField, legalUrlField) => {
    const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickByVisibleText(page, [triggerText]);

    const popupPage = await popupPromise;
    const legalPage = popupPage || page;
    await waitForUi(legalPage);

    const headingVisible =
      (await isVisible(
        legalPage.getByRole("heading", { name: new RegExp(escapeRegex(headingText), "i") }),
        10_000
      )) || (await textVisible(legalPage, [headingText], 10_000));
    const bodyText = (await legalPage.locator("body").innerText().catch(() => "")).trim();
    const contentVisible = bodyText.length > 200;
    const legalScreenshot = await takeScreenshot(legalPage, reportField.replace(/\s+/g, "_"));
    const finalUrl = legalPage.url();
    legalUrls[legalUrlField] = finalUrl;

    setStep(reportField, headingVisible && contentVisible, [
      `"${headingText}" heading visible: ${headingVisible}`,
      `Legal content visible: ${contentVisible}`,
      `Final URL: ${finalUrl}`,
      `Screenshot: ${legalScreenshot}`,
    ]);

    if (popupPage) {
      await popupPage.close().catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  };

  // Step 8: Validate Términos y Condiciones
  try {
    await openLegalPage(
      "Términos y Condiciones",
      "Términos y Condiciones",
      "Términos y Condiciones",
      "terminosYCondiciones"
    );
  } catch (error) {
    const failureShot = await takeScreenshot(page, "terminos_failure").catch(() => null);
    setStep("Términos y Condiciones", false, [
      `Error: ${error.message}`,
      failureShot ? `Screenshot: ${failureShot}` : "",
    ]);
  }

  // Step 9: Validate Política de Privacidad
  try {
    await openLegalPage(
      "Política de Privacidad",
      "Política de Privacidad",
      "Política de Privacidad",
      "politicaDePrivacidad"
    );
  } catch (error) {
    const failureShot = await takeScreenshot(page, "politica_failure").catch(() => null);
    setStep("Política de Privacidad", false, [
      `Error: ${error.message}`,
      failureShot ? `Screenshot: ${failureShot}` : "",
    ]);
  }

  // Step 10: Final report
  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    loginUrl: loginUrl || currentUrl,
    googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
    results: report,
    legalUrls,
    screenshotsDirectory: screenshotsDir,
  };

  const reportPath = path.join(artifactsDir, `final-report-${runId}.json`);
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2));

  const summaryRows = Object.entries(report).map(([step, value]) => ({
    step,
    status: value.status,
  }));
  // eslint-disable-next-line no-console
  console.table(summaryRows);
  // eslint-disable-next-line no-console
  console.log(`Final report: ${reportPath}`);
  // eslint-disable-next-line no-console
  console.log(`Screenshots directory: ${screenshotsDir}`);
});
