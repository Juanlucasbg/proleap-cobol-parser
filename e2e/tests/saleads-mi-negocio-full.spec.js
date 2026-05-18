const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

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

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

function createReport() {
  return REPORT_FIELDS.reduce((acc, key) => {
    acc[key] = { status: "FAIL", details: "Not executed" };
    return acc;
  }, {});
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(500);
}

async function pickVisible(locators, timeoutMs = 10000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of locators) {
      if (await locator.first().isVisible().catch(() => false)) {
        return locator.first();
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error("No visible locator matched the expected text selectors.");
}

async function clickAndWait(locator, page) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function ensureMiNegocioExpanded(page) {
  const administrarVisible = await page
    .getByText(/Administrar Negocios/i)
    .first()
    .isVisible()
    .catch(() => false);
  if (administrarVisible) {
    return;
  }

  const negocioToggle = await pickVisible(
    [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
      page.getByText(/Mi Negocio/i),
    ],
    12000,
  );

  await clickAndWait(negocioToggle, page);
}

async function screenshot(page, evidenceDir, fileName, fullPage = false) {
  const screenshotPath = path.join(evidenceDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  test.skip(
    !process.env.SALEADS_LOGIN_URL,
    "Set SALEADS_LOGIN_URL to the login page of the target environment.",
  );

  const report = createReport();
  const errors = [];
  const legalUrls = {};

  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const evidenceDir = path.resolve(
    process.cwd(),
    "test-results",
    "saleads-mi-negocio",
    runId,
  );
  fs.mkdirSync(evidenceDir, { recursive: true });

  await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  // Step 1: Login with Google
  try {
    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);

    const loginButton = await pickVisible(
      [
        page.getByRole("button", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
        page.getByRole("button", { name: /iniciar sesión|login|acceder/i }),
      ],
      15000,
    );

    await clickAndWait(loginButton, page);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await waitForUi(googlePopup);
      const accountOption = googlePopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }
      await googlePopup.waitForLoadState("networkidle").catch(() => {});
    } else {
      const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(accountOption, page);
      }
    }

    await waitForUi(page);
    await expect(
      pickVisible(
        [
          page.locator("aside"),
          page.getByText(/Mi Negocio/i),
          page.getByText(/^Negocio$/i),
        ],
        30000,
      ),
    ).resolves.toBeTruthy();

    await screenshot(page, evidenceDir, "01-dashboard-loaded.png");
    report["Login"] = { status: "PASS", details: "Dashboard and left sidebar are visible." };
  } catch (error) {
    report["Login"] = { status: "FAIL", details: String(error) };
    errors.push(`Login: ${error}`);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await ensureMiNegocioExpanded(page);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await screenshot(page, evidenceDir, "02-mi-negocio-menu-expanded.png");
    report["Mi Negocio menu"] = {
      status: "PASS",
      details: "Mi Negocio submenu expanded with required options.",
    };
  } catch (error) {
    report["Mi Negocio menu"] = { status: "FAIL", details: String(error) };
    errors.push(`Mi Negocio menu: ${error}`);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const agregarNegocio = await pickVisible(
      [
        page.getByRole("button", { name: /Agregar Negocio/i }),
        page.getByRole("link", { name: /Agregar Negocio/i }),
        page.getByText(/Agregar Negocio/i),
      ],
      12000,
    );
    await clickAndWait(agregarNegocio, page);

    const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await screenshot(page, evidenceDir, "03-crear-nuevo-negocio-modal.png");

    const negocioInput = modal.getByRole("textbox", { name: /Nombre del Negocio/i });
    if (await negocioInput.isVisible().catch(() => false)) {
      await negocioInput.click();
      await negocioInput.fill("Negocio Prueba Automatización");
    }
    await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }), page);

    report["Agregar Negocio modal"] = {
      status: "PASS",
      details: "Modal and all required fields/buttons are visible.",
    };
  } catch (error) {
    report["Agregar Negocio modal"] = { status: "FAIL", details: String(error) };
    errors.push(`Agregar Negocio modal: ${error}`);
  }

  // Step 4: Open Administrar Negocios
  try {
    await ensureMiNegocioExpanded(page);
    const administrar = await pickVisible(
      [
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ],
      12000,
    );
    await clickAndWait(administrar, page);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();

    await screenshot(page, evidenceDir, "04-administrar-negocios-page.png", true);
    report["Administrar Negocios view"] = {
      status: "PASS",
      details: "Account page loaded with all required sections.",
    };
  } catch (error) {
    report["Administrar Negocios view"] = { status: "FAIL", details: String(error) };
    errors.push(`Administrar Negocios view: ${error}`);
  }

  // Step 5: Validate Información General
  try {
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const emailCandidate = page.getByText(/@/).first();
    await expect(emailCandidate).toBeVisible();

    const profileName = page.locator("h1, h2, h3, p, span").filter({ hasText: /\S+/ }).first();
    await expect(profileName).toBeVisible();

    report["Información General"] = {
      status: "PASS",
      details: "General information section contains user, plan and actions.",
    };
  } catch (error) {
    report["Información General"] = { status: "FAIL", details: String(error) };
    errors.push(`Información General: ${error}`);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    report["Detalles de la Cuenta"] = {
      status: "PASS",
      details: "Account details section displays created, status and language.",
    };
  } catch (error) {
    report["Detalles de la Cuenta"] = { status: "FAIL", details: String(error) };
    errors.push(`Detalles de la Cuenta: ${error}`);
  }

  // Step 7: Validate Tus Negocios
  try {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    report["Tus Negocios"] = {
      status: "PASS",
      details: "Business list and limits are visible.",
    };
  } catch (error) {
    report["Tus Negocios"] = { status: "FAIL", details: String(error) };
    errors.push(`Tus Negocios: ${error}`);
  }

  async function validateLegalLink(stepName, linkText, expectedHeading, screenshotName) {
    const link = await pickVisible(
      [
        page.getByRole("link", { name: new RegExp(linkText, "i") }),
        page.getByRole("button", { name: new RegExp(linkText, "i") }),
        page.getByText(new RegExp(linkText, "i")),
      ],
      12000,
    );

    const newTabPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await link.scrollIntoViewIfNeeded().catch(() => {});
    await link.click();
    await waitForUi(page);

    const newTab = await newTabPromise;
    const targetPage = newTab || page;
    await waitForUi(targetPage);

    await expect(targetPage.getByText(expectedHeading)).toBeVisible();

    const legalContentVisible = await targetPage
      .locator("main p, article p, p")
      .first()
      .isVisible()
      .catch(() => false);
    if (!legalContentVisible) {
      throw new Error("Legal content text is not visible.");
    }

    legalUrls[stepName] = targetPage.url();
    await screenshot(targetPage, evidenceDir, screenshotName, true);

    if (newTab) {
      await newTab.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack().catch(() => {});
      await waitForUi(page);
    }
  }

  // Step 8: Validate Términos y Condiciones
  try {
    await validateLegalLink(
      "Términos y Condiciones",
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      "08-terminos-y-condiciones.png",
    );
    report["Términos y Condiciones"] = {
      status: "PASS",
      details: `Legal page validated. URL: ${legalUrls["Términos y Condiciones"]}`,
    };
  } catch (error) {
    report["Términos y Condiciones"] = { status: "FAIL", details: String(error) };
    errors.push(`Términos y Condiciones: ${error}`);
  }

  // Step 9: Validate Política de Privacidad
  try {
    await validateLegalLink(
      "Política de Privacidad",
      "Política de Privacidad",
      /Política de Privacidad/i,
      "09-politica-de-privacidad.png",
    );
    report["Política de Privacidad"] = {
      status: "PASS",
      details: `Legal page validated. URL: ${legalUrls["Política de Privacidad"]}`,
    };
  } catch (error) {
    report["Política de Privacidad"] = { status: "FAIL", details: String(error) };
    errors.push(`Política de Privacidad: ${error}`);
  }

  // Step 10: Final report
  const reportPath = path.join(evidenceDir, "final-report.json");
  fs.writeFileSync(
    reportPath,
    JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        executedAt: new Date().toISOString(),
        environment: process.env.SALEADS_LOGIN_URL,
        report,
        legalUrls,
      },
      null,
      2,
    ),
  );

  console.log("SaleADS Mi Negocio final report:");
  for (const key of REPORT_FIELDS) {
    const entry = report[key];
    console.log(`- ${key}: ${entry.status}`);
  }
  console.log(`Evidence directory: ${evidenceDir}`);
  console.log(`Report file: ${reportPath}`);

  expect(errors, `Validation failures:\n${errors.join("\n")}`).toEqual([]);
});
