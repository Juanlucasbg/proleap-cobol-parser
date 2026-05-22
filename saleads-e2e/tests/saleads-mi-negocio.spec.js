const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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
];

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 8000 }),
    page.waitForTimeout(1200),
  ]);
}

async function waitVisible(locator, timeout = 10000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickByVisibleText(page, labels, options = {}) {
  const timeout = options.timeout ?? 12000;
  const waitAfter = options.waitAfter ?? true;
  const optional = options.optional ?? false;
  const roleOptions = { exact: true };

  for (const rawLabel of labels) {
    const label = rawLabel.trim();
    const exactPattern = new RegExp(`^\\s*${escapeRegex(label)}\\s*$`, "i");
    const candidates = [
      page.getByRole("button", { ...roleOptions, name: exactPattern }),
      page.getByRole("link", { ...roleOptions, name: exactPattern }),
      page.getByRole("menuitem", { ...roleOptions, name: exactPattern }),
      page.getByRole("tab", { ...roleOptions, name: exactPattern }),
      page.getByRole("option", { ...roleOptions, name: exactPattern }),
      page.getByText(exactPattern),
    ];

    for (const candidate of candidates) {
      if (await waitVisible(candidate, timeout)) {
        await candidate.first().click();
        if (waitAfter) {
          await waitForUiLoad(page);
        }
        return true;
      }
    }
  }

  if (optional) {
    return false;
  }

  throw new Error(
    `Could not find clickable element with text: ${labels.join(" / ")}`
  );
}

async function capture(page, artifactsDir, fileName, fullPage = false) {
  const filePath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function chooseGoogleAccountIfPrompted(targetPage, email) {
  const accountLabel = new RegExp(`^\\s*${escapeRegex(email)}\\s*$`, "i");
  const useAccountButton = targetPage
    .getByRole("button", { name: accountLabel })
    .first();
  const useAccountText = targetPage.getByText(accountLabel).first();

  if (await waitVisible(useAccountButton, 8000)) {
    await useAccountButton.click();
    await waitForUiLoad(targetPage);
    return true;
  }

  if (await waitVisible(useAccountText, 3000)) {
    await useAccountText.click();
    await waitForUiLoad(targetPage);
    return true;
  }

  return false;
}

async function validateLegalLink({
  page,
  context,
  artifactsDir,
  linkText,
  headingText,
  screenshotName,
}) {
  const applicationPage = page;
  const beforeUrl = applicationPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);

  await clickByVisibleText(applicationPage, [linkText], { waitAfter: false });
  const popup = await popupPromise;
  const targetPage = popup ?? applicationPage;

  await waitForUiLoad(targetPage);

  const headingVisible = await waitVisible(
    targetPage.getByRole("heading", { name: new RegExp(escapeRegex(headingText), "i") }),
    15000
  );
  const textVisible = await waitVisible(targetPage.getByText(new RegExp(escapeRegex(headingText), "i")), 8000);

  if (!headingVisible && !textVisible) {
    throw new Error(`Could not validate legal page heading: ${headingText}`);
  }

  const screenshotPath = await capture(targetPage, artifactsDir, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await applicationPage.bringToFront();
    await waitForUiLoad(applicationPage);
  } else if (applicationPage.url() !== beforeUrl) {
    await applicationPage.goBack();
    await waitForUiLoad(applicationPage);
  }

  return { finalUrl, screenshotPath };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.resolve(process.cwd(), "artifacts", `run-${timestamp}`);
  fs.mkdirSync(artifactsDir, { recursive: true });

  const report = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: Object.fromEntries(
      REPORT_FIELDS.map((field) => [
        field,
        { status: "FAIL", details: "Not completed." },
      ])
    ),
    evidence: {
      screenshots: [],
      urls: {},
    },
  };

  const markPass = (field, details) => {
    report.results[field] = { status: "PASS", details };
  };
  const markFail = (field, details) => {
    report.results[field] = { status: "FAIL", details };
  };

  let currentField = "Login";

  try {
    const loginUrl =
      process.env.SALEADS_LOGIN_URL || process.env.LOGIN_URL || process.env.BASE_URL;

    if (!loginUrl) {
      throw new Error(
        "Provide SALEADS_LOGIN_URL (or LOGIN_URL / BASE_URL) to start from the SaleADS login page."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);

    const googlePopupPromise = context
      .waitForEvent("page", { timeout: 10000 })
      .catch(() => null);

    await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Login con Google",
      "Google",
    ]);

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      await chooseGoogleAccountIfPrompted(googlePopup, GOOGLE_ACCOUNT_EMAIL);
    } else {
      await chooseGoogleAccountIfPrompted(page, GOOGLE_ACCOUNT_EMAIL);
    }

    await waitForUiLoad(page);
    const sidebarVisible = await waitVisible(page.locator("aside"), 20000);
    const negocioVisible = await waitVisible(page.getByText(/^Negocio$/i), 20000);
    if (!sidebarVisible && !negocioVisible) {
      throw new Error("Main app interface did not render with the expected left navigation.");
    }

    const dashboardShot = await capture(page, artifactsDir, "01-dashboard-loaded.png");
    report.evidence.screenshots.push(dashboardShot);
    markPass("Login", "Dashboard loaded and left sidebar is visible.");

    currentField = "Mi Negocio menu";
    await clickByVisibleText(page, ["Negocio"], { optional: true });
    await clickByVisibleText(page, ["Mi Negocio"]);

    await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();

    const expandedMenuShot = await capture(page, artifactsDir, "02-mi-negocio-expanded.png");
    report.evidence.screenshots.push(expandedMenuShot);
    markPass("Mi Negocio menu", "Submenu expanded with Agregar/Administrar Negocios.");

    currentField = "Agregar Negocio modal";
    await clickByVisibleText(page, ["Agregar Negocio"]);

    await expect(page.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
    const businessNameInputCandidates = [
      page.getByLabel("Nombre del Negocio", { exact: true }),
      page.getByPlaceholder("Nombre del Negocio"),
      page.locator('input[name*="negocio" i]'),
      page.locator('[role="dialog"] input, .modal input'),
    ];

    let businessNameInput = null;
    for (const candidate of businessNameInputCandidates) {
      if (await waitVisible(candidate, 5000)) {
        businessNameInput = candidate.first();
        break;
      }
    }
    if (!businessNameInput) {
      throw new Error("Input 'Nombre del Negocio' was not found in modal.");
    }

    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(page.getByText("Cancelar", { exact: true })).toBeVisible();
    await expect(page.getByText("Crear Negocio", { exact: true })).toBeVisible();

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");

    const modalShot = await capture(page, artifactsDir, "03-agregar-negocio-modal.png");
    report.evidence.screenshots.push(modalShot);

    await clickByVisibleText(page, ["Cancelar"]);
    markPass("Agregar Negocio modal", "Modal validated and closed with Cancelar.");

    currentField = "Administrar Negocios view";
    await clickByVisibleText(page, ["Mi Negocio"], { optional: true });
    await clickByVisibleText(page, ["Administrar Negocios"]);

    await expect(page.getByText("Información General", { exact: true })).toBeVisible();
    await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
    await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible();

    const accountViewShot = await capture(page, artifactsDir, "04-administrar-negocios.png", true);
    report.evidence.screenshots.push(accountViewShot);
    markPass("Administrar Negocios view", "Account management sections are visible.");

    currentField = "Información General";
    const emailText = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
    await expect(emailText).toBeVisible();
    const possibleNameNearEmail = emailText.locator(
      "xpath=ancestor::*[self::div or self::section][1]//*[normalize-space() and not(contains(normalize-space(), '@'))]"
    );
    if (!(await waitVisible(possibleNameNearEmail, 5000))) {
      throw new Error("User name was not visible near email in Información General.");
    }
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByText("Cambiar Plan", { exact: true })).toBeVisible();
    markPass("Información General", "Name/email plan details and button validated.");

    currentField = "Detalles de la Cuenta";
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    markPass("Detalles de la Cuenta", "Account details labels are visible.");

    currentField = "Tus Negocios";
    await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
    await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    markPass("Tus Negocios", "Business list and usage counter are visible.");

    currentField = "Términos y Condiciones";
    const termsData = await validateLegalLink({
      page,
      context,
      artifactsDir,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotName: "05-terminos-condiciones.png",
    });
    report.evidence.screenshots.push(termsData.screenshotPath);
    report.evidence.urls["Términos y Condiciones"] = termsData.finalUrl;
    markPass(
      "Términos y Condiciones",
      `Legal page validated at ${termsData.finalUrl}`
    );

    currentField = "Política de Privacidad";
    const privacyData = await validateLegalLink({
      page,
      context,
      artifactsDir,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotName: "06-politica-privacidad.png",
    });
    report.evidence.screenshots.push(privacyData.screenshotPath);
    report.evidence.urls["Política de Privacidad"] = privacyData.finalUrl;
    markPass(
      "Política de Privacidad",
      `Legal page validated at ${privacyData.finalUrl}`
    );
  } catch (error) {
    markFail(currentField, error instanceof Error ? error.message : String(error));
    throw error;
  } finally {
    const reportPath = path.join(artifactsDir, "final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json",
    });
  }
});
