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
  "Política de Privacidad"
];

function createReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {});
}

function toTextRegex(text) {
  return new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

async function waitForUiAfterClick(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
}

async function firstVisible(locators, timeoutMs, errorMessage) {
  for (const locator of locators) {
    const first = locator.first();
    try {
      await first.waitFor({ state: "visible", timeout: timeoutMs });
      return first;
    } catch (error) {
      // Try next locator.
    }
  }

  throw new Error(errorMessage);
}

async function clickByVisibleText(page, text, timeoutMs = 15000) {
  const textRegex = toTextRegex(text);
  const target = await firstVisible(
    [
      page.getByRole("button", { name: textRegex }),
      page.getByRole("link", { name: textRegex }),
      page.getByRole("menuitem", { name: textRegex }),
      page.getByRole("tab", { name: textRegex }),
      page.getByText(textRegex)
    ],
    timeoutMs,
    `Could not find visible target with text "${text}".`
  );

  await target.click();
  await waitForUiAfterClick(page);
}

async function pickGoogleAccountIfVisible(candidatePage, email) {
  const emailRegex = toTextRegex(email);
  const accountOption = await firstVisible(
    [
      candidatePage.getByText(emailRegex, { exact: false }),
      candidatePage.locator(`[data-identifier="${email}"]`),
      candidatePage.getByRole("button", { name: emailRegex })
    ],
    8000,
    "Google account chooser was not visible."
  ).catch(() => null);

  if (!accountOption) {
    return false;
  }

  await accountOption.click();
  await candidatePage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  return true;
}

async function validateLegalLink({
  appPage,
  linkText,
  expectedHeading,
  screenshotName,
  testInfo
}) {
  const context = appPage.context();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickByVisibleText(appPage, linkText);

  let targetPage = await popupPromise;
  if (targetPage) {
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  } else {
    targetPage = appPage;
  }

  await expect(targetPage.getByRole("heading", { name: toTextRegex(expectedHeading) })).toBeVisible();

  const legalBodyText = await targetPage.locator("body").innerText();
  expect(legalBodyText.trim().length).toBeGreaterThan(120);

  const finalUrl = targetPage.url();
  await targetPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true
  });

  if (targetPage !== appPage) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitForUiAfterClick(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiAfterClick(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const failureMessages = [];
  const legalUrls = {};

  async function runStep(field, callback) {
    try {
      await test.step(field, callback);
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      failureMessages.push(`${field}: ${error.message}`);
    }
  }

  await runStep("Login", async () => {
    const entryUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
    if (entryUrl) {
      await page.goto(entryUrl, { waitUntil: "domcontentloaded" });
      await waitForUiAfterClick(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_LOGIN_URL or SALEADS_BASE_URL, or run against a preloaded login page context."
      );
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);

    const loginButton = await firstVisible(
      [
        page.getByRole("button", { name: /iniciar sesión con google|sign in with google|continuar con google/i }),
        page.getByRole("link", { name: /iniciar sesión con google|sign in with google|continuar con google/i }),
        page.getByText(/iniciar sesión con google|sign in with google|continuar con google/i)
      ],
      20000,
      "Google login button was not found."
    );

    await loginButton.click();
    await waitForUiAfterClick(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await pickGoogleAccountIfVisible(popup, "juanlucasbarbiergarzon@gmail.com");
      await popup.waitForClose({ timeout: 30000 }).catch(() => {});
    } else {
      await pickGoogleAccountIfVisible(page, "juanlucasbarbiergarzon@gmail.com");
    }

    const sidebar = await firstVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator('[class*="sidebar"], [data-testid*="sidebar"]')
      ],
      30000,
      "Left sidebar was not visible after login."
    );

    await expect(sidebar).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true
    });
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, "Negocio");
    await clickByVisibleText(page, "Mi Negocio");

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"),
      fullPage: true
    });
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, "Agregar Negocio");

    await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true
    });

    await page.getByLabel(/Nombre del Negocio/i).fill("Negocio Prueba Automatizacion");
    await clickByVisibleText(page, "Cancelar");
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickByVisibleText(page, "Mi Negocio");
    }

    await clickByVisibleText(page, "Administrar Negocios");

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-page.png"),
      fullPage: true
    });
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const emailMatch = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    await expect(page.getByText(emailMatch)).toBeVisible();

    const userName = page.locator("section, div").filter({ hasText: /Información General/i }).first();
    const infoText = await userName.innerText();
    expect(infoText.trim().length).toBeGreaterThan(40);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await validateLegalLink({
      appPage: page,
      linkText: "Términos y Condiciones",
      expectedHeading: "Términos y Condiciones",
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo
    });
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await validateLegalLink({
      appPage: page,
      linkText: "Política de Privacidad",
      expectedHeading: "Política de Privacidad",
      screenshotName: "06-politica-de-privacidad.png",
      testInfo
    });
  });

  const finalReport = {
    report,
    legalUrls,
    generatedAt: new Date().toISOString()
  };

  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });

  console.log("FINAL_REPORT_START");
  console.log(JSON.stringify(finalReport, null, 2));
  console.log("FINAL_REPORT_END");

  expect(
    failureMessages,
    `Validation failures:\n${failureMessages.join("\n")}`
  ).toEqual([]);
});
