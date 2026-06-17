const { test } = require("@playwright/test");

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
];

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function slugify(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
    .toLowerCase();
}

async function captureCheckpoint(page, testInfo, label, fullPage = false) {
  const filename = `${Date.now()}-${slugify(label)}.png`;
  const filepath = testInfo.outputPath(`screenshots/${filename}`);
  await page.screenshot({ path: filepath, fullPage });
  await testInfo.attach(label, { path: filepath, contentType: "image/png" });
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
}

async function clickVisibleText(page, regex, options = {}) {
  const candidates = [
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByText(regex).first()
  ];

  for (const locator of candidates) {
    if (await locator.first().isVisible({ timeout: 3000 }).catch(() => false)) {
      await locator.first().click(options);
      await waitForUiToLoad(page);
      return true;
    }
  }

  return false;
}

async function requireVisible(locator, label, timeout = 10000) {
  const isVisible = await locator.first().isVisible({ timeout }).catch(() => false);
  if (!isVisible) {
    throw new Error(`Expected visible element was not found: ${label}`);
  }
}

async function validateLegalLink({
  appPage,
  testInfo,
  linkText,
  headingText,
  screenshotLabel
}) {
  const linkRegex = new RegExp(linkText, "i");
  const headingRegex = new RegExp(headingText, "i");

  const legalLink =
    (await appPage.getByRole("link", { name: linkRegex }).first().isVisible().catch(() => false))
      ? appPage.getByRole("link", { name: linkRegex }).first()
      : appPage.getByText(linkRegex).first();

  await requireVisible(legalLink, `Link "${linkText}"`);

  const originalUrl = appPage.url();
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await legalLink.click();
  await waitForUiToLoad(appPage);

  const popup = await popupPromise;
  const legalPage = popup || appPage;

  await waitForUiToLoad(legalPage);

  const headingByRole = legalPage.getByRole("heading", { name: headingRegex }).first();
  const headingByText = legalPage.getByText(headingRegex).first();

  const headingVisible =
    (await headingByRole.isVisible({ timeout: 10000 }).catch(() => false)) ||
    (await headingByText.isVisible({ timeout: 10000 }).catch(() => false));

  if (!headingVisible) {
    throw new Error(`Heading "${headingText}" was not visible in legal page.`);
  }

  const legalBodyText = ((await legalPage.locator("body").innerText()).trim() || "").replace(/\s+/g, " ");
  if (legalBodyText.length < 200) {
    throw new Error(`Legal content for "${headingText}" appears too short.`);
  }

  await captureCheckpoint(legalPage, testInfo, screenshotLabel, true);
  const finalUrl = legalPage.url();
  testInfo.annotations.push({ type: `URL ${headingText}`, description: finalUrl });

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else if (appPage.url() !== originalUrl) {
    await appPage.goBack().catch(() => {});
    await waitForUiToLoad(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const errors = [];

  const setResult = (field, passed, details = "") => {
    report[field] = passed ? "PASS" : "FAIL";
    if (!passed && details) {
      errors.push(`${field}: ${details}`);
    }
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL) to the current environment login page."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToLoad(page);

  // Step 1: Login with Google
  try {
    const sidebarAlreadyVisible = await page
      .locator("aside, nav")
      .getByText(/Negocio|Mi Negocio/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (!sidebarAlreadyVisible) {
      const clickedLogin = await clickVisibleText(page, /Google|Iniciar sesi[oó]n|Sign in/i);
      if (!clickedLogin) {
        throw new Error("Google login button was not found.");
      }

      const accountOption = page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")).first();
      if (await accountOption.isVisible({ timeout: 7000 }).catch(() => false)) {
        await accountOption.click();
        await waitForUiToLoad(page);
      }
    }

    await requireVisible(page.locator("aside, nav").first(), "Main sidebar");
    await requireVisible(page.getByText(/Negocio|Mi Negocio/i).first(), "Left sidebar navigation");
    await captureCheckpoint(page, testInfo, "dashboard-loaded");
    setResult("Login", true);
  } catch (error) {
    setResult("Login", false, error.message);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await clickVisibleText(page, /^Negocio$/i).catch(() => {});

    const openedMiNegocio = await clickVisibleText(page, /^Mi Negocio$/i);
    if (!openedMiNegocio) {
      throw new Error('Option "Mi Negocio" was not found or clickable.');
    }

    await requireVisible(page.getByText(/Agregar Negocio/i).first(), "Agregar Negocio option");
    await requireVisible(page.getByText(/Administrar Negocios/i).first(), "Administrar Negocios option");
    await captureCheckpoint(page, testInfo, "mi-negocio-menu-expanded");
    setResult("Mi Negocio menu", true);
  } catch (error) {
    setResult("Mi Negocio menu", false, error.message);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const openedAgregar = await clickVisibleText(page, /^Agregar Negocio$/i);
    if (!openedAgregar) {
      throw new Error('"Agregar Negocio" option was not clickable.');
    }

    const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
    await requireVisible(modal, "Crear Nuevo Negocio modal");

    await requireVisible(page.getByText(/Crear Nuevo Negocio/i).first(), "Modal title");
    await requireVisible(page.getByLabel(/Nombre del Negocio/i).first(), "Nombre del Negocio input");
    await requireVisible(page.getByText(/Tienes 2 de 3 negocios/i).first(), "Business count text");
    await requireVisible(page.getByRole("button", { name: /Cancelar/i }).first(), "Cancelar button");
    await requireVisible(page.getByRole("button", { name: /Crear Negocio/i }).first(), "Crear Negocio button");

    const nameInput = page.getByLabel(/Nombre del Negocio/i).first();
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await page.getByRole("button", { name: /Cancelar/i }).first().click();
    await waitForUiToLoad(page);

    await captureCheckpoint(page, testInfo, "agregar-negocio-modal");
    setResult("Agregar Negocio modal", true);
  } catch (error) {
    setResult("Agregar Negocio modal", false, error.message);
  }

  // Step 4: Open Administrar Negocios
  try {
    await clickVisibleText(page, /^Mi Negocio$/i).catch(() => {});
    const openedAdmin = await clickVisibleText(page, /^Administrar Negocios$/i);
    if (!openedAdmin) {
      throw new Error('"Administrar Negocios" option was not clickable.');
    }

    await requireVisible(page.getByText(/Informaci[oó]n General/i).first(), "Información General section");
    await requireVisible(page.getByText(/Detalles de la Cuenta/i).first(), "Detalles de la Cuenta section");
    await requireVisible(page.getByText(/Tus Negocios/i).first(), "Tus Negocios section");
    await requireVisible(page.getByText(/Secci[oó]n Legal/i).first(), "Sección Legal section");

    await captureCheckpoint(page, testInfo, "administrar-negocios-page", true);
    setResult("Administrar Negocios view", true);
  } catch (error) {
    setResult("Administrar Negocios view", false, error.message);
  }

  // Step 5: Validate Información General
  try {
    await requireVisible(page.getByText(/BUSINESS PLAN/i).first(), "BUSINESS PLAN text");
    await requireVisible(page.getByRole("button", { name: /Cambiar Plan/i }).first(), "Cambiar Plan button");

    const infoSection = page
      .locator("section, div")
      .filter({ hasText: /Informaci[oó]n General/i })
      .first();
    const sectionText = ((await infoSection.innerText()).trim() || "").replace(/\s+/g, " ");
    if (!sectionText.includes("@")) {
      throw new Error("User email was not found in Información General.");
    }
    if (sectionText.length < 30) {
      throw new Error("Información General content seems incomplete.");
    }

    setResult("Información General", true);
  } catch (error) {
    setResult("Información General", false, error.message);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await requireVisible(page.getByText(/Cuenta creada/i).first(), "Cuenta creada");
    await requireVisible(page.getByText(/Estado activo/i).first(), "Estado activo");
    await requireVisible(page.getByText(/Idioma seleccionado/i).first(), "Idioma seleccionado");
    setResult("Detalles de la Cuenta", true);
  } catch (error) {
    setResult("Detalles de la Cuenta", false, error.message);
  }

  // Step 7: Validate Tus Negocios
  try {
    await requireVisible(page.getByText(/Tus Negocios/i).first(), "Tus Negocios section");
    await requireVisible(page.getByRole("button", { name: /Agregar Negocio/i }).first(), "Agregar Negocio button");
    await requireVisible(page.getByText(/Tienes 2 de 3 negocios/i).first(), "Tienes 2 de 3 negocios text");
    setResult("Tus Negocios", true);
  } catch (error) {
    setResult("Tus Negocios", false, error.message);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const finalUrl = await validateLegalLink({
      appPage: page,
      testInfo,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotLabel: "terminos-y-condiciones"
    });
    testInfo.annotations.push({ type: "Términos y Condiciones URL", description: finalUrl });
    setResult("Términos y Condiciones", true);
  } catch (error) {
    setResult("Términos y Condiciones", false, error.message);
  }

  // Step 9: Validate Política de Privacidad
  try {
    const finalUrl = await validateLegalLink({
      appPage: page,
      testInfo,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotLabel: "politica-de-privacidad"
    });
    testInfo.annotations.push({ type: "Política de Privacidad URL", description: finalUrl });
    setResult("Política de Privacidad", true);
  } catch (error) {
    setResult("Política de Privacidad", false, error.message);
  }

  // Step 10: Final report
  const finalReport = REPORT_FIELDS.map((field) => `${field}: ${report[field]}`).join("\n");
  testInfo.annotations.push({ type: "Final Report", description: finalReport });
  console.log("\n=== SaleADS Mi Negocio Workflow Report ===");
  console.log(finalReport);

  const failedFields = REPORT_FIELDS.filter((field) => report[field] !== "PASS");
  if (failedFields.length > 0) {
    throw new Error(
      `Workflow finished with failures in: ${failedFields.join(", ")}\n` +
        `Details:\n${errors.join("\n") || "No additional details."}`
    );
  }
});
