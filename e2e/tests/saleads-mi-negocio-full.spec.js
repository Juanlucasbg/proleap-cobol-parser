const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

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

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

function fileSafe(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, "-");
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(`${fileSafe(name)}.png`),
    fullPage
  });
}

async function clickByVisibleText(page, texts) {
  const options = Array.isArray(texts) ? texts : [texts];

  for (const text of options) {
    const candidates = [
      page.getByRole("button", { name: text, exact: true }).first(),
      page.getByRole("link", { name: text, exact: true }).first(),
      page.getByRole("menuitem", { name: text, exact: true }).first(),
      page.getByRole("tab", { name: text, exact: true }).first(),
      page.getByText(text, { exact: true }).first(),
      page.getByText(text).first()
    ];

    for (const candidate of candidates) {
      const visible = await candidate.isVisible({ timeout: 2500 }).catch(() => false);
      if (!visible) {
        continue;
      }

      await candidate.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Could not find a clickable element with text: ${options.join(", ")}`);
}

async function selectGoogleAccountIfVisible(googlePage) {
  const accountRow = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
  const isVisible = await accountRow.isVisible({ timeout: 12000 }).catch(() => false);

  if (isVisible) {
    await accountRow.click();
    return true;
  }

  return false;
}

async function runStep(results, errors, key, action) {
  try {
    await action();
    results[key] = "PASS";
  } catch (error) {
    results[key] = "FAIL";
    const message = error instanceof Error ? error.message : String(error);
    errors.push(`${key}: ${message}`);
  }
}

async function validateLegalLink({
  page,
  linkText,
  expectedHeading,
  testInfo,
  screenshotName
}) {
  const context = page.context();
  const originalUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 15000 }).catch(() => null);

  await clickByVisibleText(page, [linkText]);

  let targetPage = await popupPromise;
  if (!targetPage) {
    await page.waitForURL((url) => url !== originalUrl, { timeout: 20000 }).catch(() => {});
    targetPage = page;
  }

  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForTimeout(1200);

  const headingByRole = targetPage
    .getByRole("heading", { name: new RegExp(expectedHeading, "i") })
    .first();
  const headingVisible = await headingByRole.isVisible({ timeout: 8000 }).catch(() => false);

  if (headingVisible) {
    await expect(headingByRole).toBeVisible();
  } else {
    await expect(targetPage.getByText(new RegExp(expectedHeading, "i")).first()).toBeVisible();
  }

  await expect(
    targetPage.locator("main, article, body").getByText(/\S+/).first()
  ).toBeVisible();

  const finalUrl = targetPage.url();
  await testInfo.attach(`${fileSafe(screenshotName)}-url.txt`, {
    body: finalUrl,
    contentType: "text/plain"
  });
  await captureCheckpoint(targetPage, testInfo, screenshotName, true);

  if (targetPage !== page) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const errors = [];

  await runStep(results, errors, "Login", async () => {
    if (page.url() === "about:blank") {
      const loginUrl = process.env.SALEADS_LOGIN_URL;
      if (!loginUrl) {
        throw new Error(
          "Browser started on about:blank. Set SALEADS_LOGIN_URL or preload the SaleADS login page."
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    const popupPromise = page.context().waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Login con Google",
      "Iniciar con Google"
    ]);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfVisible(popup);
      await popup.waitForEvent("close", { timeout: 90000 }).catch(() => {});
      await page.bringToFront();
    } else if (page.url().includes("accounts.google.com")) {
      await selectGoogleAccountIfVisible(page);
      await page
        .waitForURL((url) => !url.includes("accounts.google.com"), { timeout: 90000 })
        .catch(() => {});
    }

    await waitForUi(page);

    const sidebar = page.locator("aside, nav, [class*='sidebar'], [aria-label*='sidebar' i]").first();
    await expect(sidebar).toBeVisible();

    const appReadyText = page
      .getByText(/dashboard|inicio|negocio|mi negocio|campa/i)
      .first();
    await expect(appReadyText).toBeVisible();

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded");
  });

  await runStep(results, errors, "Mi Negocio menu", async () => {
    await clickByVisibleText(page, ["Negocio"]);
    await clickByVisibleText(page, ["Mi Negocio"]);

    await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: true }).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
  });

  await runStep(results, errors, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, ["Agregar Negocio"]);

    const modal = page.getByRole("dialog").first();
    const modalVisible = await modal.isVisible({ timeout: 12000 }).catch(() => false);

    if (modalVisible) {
      await expect(modal.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
      await expect(modal.getByText("Nombre del Negocio", { exact: true })).toBeVisible();
      await expect(modal.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Cancelar", exact: true })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Crear Negocio", exact: true })).toBeVisible();

      const input = modal.getByLabel("Nombre del Negocio").first();
      const hasInputByLabel = await input.isVisible({ timeout: 2000 }).catch(() => false);
      if (hasInputByLabel) {
        await input.fill("Negocio Prueba Automatización");
      } else {
        await modal.getByPlaceholder(/nombre del negocio/i).first().fill("Negocio Prueba Automatización");
      }

      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");
      await modal.getByRole("button", { name: "Cancelar", exact: true }).click();
      await waitForUi(page);
      return;
    }

    // Fallback in case implementation renders a non-dialog container.
    await expect(page.getByText("Crear Nuevo Negocio", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Nombre del Negocio", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Cancelar", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Crear Negocio", { exact: true }).first()).toBeVisible();

    const fallbackInput = page.getByLabel("Nombre del Negocio").first();
    if (await fallbackInput.isVisible({ timeout: 2000 }).catch(() => false)) {
      await fallbackInput.fill("Negocio Prueba Automatización");
    }

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");
    await clickByVisibleText(page, ["Cancelar"]);
  });

  await runStep(results, errors, "Administrar Negocios view", async () => {
    const administrarOption = page.getByText("Administrar Negocios", { exact: true }).first();
    const administrarVisible = await administrarOption.isVisible({ timeout: 2000 }).catch(() => false);
    if (!administrarVisible) {
      await clickByVisibleText(page, ["Mi Negocio"]);
    }

    await clickByVisibleText(page, ["Administrar Negocios"]);

    await expect(page.getByText("Información General", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Detalles de la Cuenta", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: true }).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal|Seccion Legal/).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "04-administrar-negocios", true);
  });

  await runStep(results, errors, "Información General", async () => {
    await expect(page.getByText("Información General", { exact: true }).first()).toBeVisible();
    await expect(page.locator("body").getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expect(page.getByText("BUSINESS PLAN", { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Cambiar Plan", exact: true }).first()).toBeVisible();

    // "User name is visible" may vary by implementation; check common name labels in the section.
    await expect(page.getByText(/Nombre|Usuario|Perfil/i).first()).toBeVisible();
  });

  await runStep(results, errors, "Detalles de la Cuenta", async () => {
    await expect(page.getByText("Cuenta creada", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Estado activo", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Idioma seleccionado", { exact: true }).first()).toBeVisible();
  });

  await runStep(results, errors, "Tus Negocios", async () => {
    const sectionHeading = page.getByText("Tus Negocios", { exact: true }).first();
    await expect(sectionHeading).toBeVisible();
    await expect(page.getByRole("button", { name: "Agregar Negocio", exact: true }).first()).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true }).first()).toBeVisible();

    const businessCardsOrRows = page
      .locator("li, tr, [class*='card'], [class*='business']")
      .filter({ hasText: /\S+/ });
    await expect(businessCardsOrRows.first()).toBeVisible();
  });

  await runStep(results, errors, "Términos y Condiciones", async () => {
    await validateLegalLink({
      page,
      linkText: "Términos y Condiciones",
      expectedHeading: "Términos y Condiciones",
      testInfo,
      screenshotName: "08-terminos-y-condiciones"
    });
  });

  await runStep(results, errors, "Política de Privacidad", async () => {
    await validateLegalLink({
      page,
      linkText: "Política de Privacidad",
      expectedHeading: "Política de Privacidad",
      testInfo,
      screenshotName: "09-politica-de-privacidad"
    });
  });

  await testInfo.attach("10-final-report.json", {
    body: JSON.stringify(results, null, 2),
    contentType: "application/json"
  });

  if (errors.length > 0) {
    throw new Error(`One or more validations failed:\n- ${errors.join("\n- ")}`);
  }
});
