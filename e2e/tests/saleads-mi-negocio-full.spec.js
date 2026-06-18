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

function reportTemplate() {
  return REPORT_FIELDS.reduce((result, field) => {
    result[field] = "FAIL";
    return result;
  }, {});
}

function safeFileName(name) {
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
}

async function checkpointScreenshot(page, testInfo, checkpointName, fullPage = false) {
  const fileName = `${Date.now()}-${safeFileName(checkpointName)}.png`;
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(checkpointName, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function waitForVisible(locator, description) {
  await expect(locator, description).toBeVisible({ timeout: 20000 });
  return locator;
}

async function findClickableByText(page, textRegex, description) {
  const candidates = [
    page.getByRole("button", { name: textRegex }).first(),
    page.getByRole("link", { name: textRegex }).first(),
    page.getByRole("menuitem", { name: textRegex }).first(),
    page.locator("button", { hasText: textRegex }).first(),
    page.locator("a", { hasText: textRegex }).first(),
    page.locator("[role='button']", { hasText: textRegex }).first(),
    page.locator("[role='menuitem']", { hasText: textRegex }).first(),
  ];

  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  const fallback = page.getByText(textRegex).first();
  await waitForVisible(fallback, description);
  return fallback;
}

async function clickAndWait(page, locator) {
  await waitForVisible(locator, "Expected clickable element to be visible before clicking.");
  await locator.click();
  await waitForUiLoad(page);
}

async function clickByTextAndWait(page, textRegex, description) {
  const clickable = await findClickableByText(page, textRegex, description);
  await clickAndWait(page, clickable);
}

async function validateHeadingOrText(page, textRegex) {
  const heading = page.getByRole("heading", { name: textRegex }).first();
  if (await heading.isVisible().catch(() => false)) {
    return;
  }

  await waitForVisible(page.getByText(textRegex).first(), `Expected text ${textRegex} to be visible.`);
}

async function validateLegalLink({
  page,
  context,
  linkRegex,
  headingRegex,
  checkpointName,
  urls,
  testInfo,
}) {
  const originUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickByTextAndWait(page, linkRegex, `Expected legal link ${linkRegex} to be visible.`);

  const popupPage = await popupPromise;
  const legalPage = popupPage || page;
  await waitForUiLoad(legalPage);

  await validateHeadingOrText(legalPage, headingRegex);
  const legalText = await legalPage.locator("body").innerText();
  expect(
    legalText.replace(/\s+/g, " ").trim().length,
    `Expected legal content text for ${headingRegex} to be visible.`
  ).toBeGreaterThan(60);

  await checkpointScreenshot(legalPage, testInfo, checkpointName, true);
  urls[checkpointName] = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUiLoad(page);
    return;
  }

  if (page.url() !== originUrl) {
    await page.goto(originUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = reportTemplate();
  const failures = [];
  const urls = {};

  const markStep = async (fieldName, stepFn) => {
    try {
      await stepFn();
      report[fieldName] = "PASS";
    } catch (error) {
      failures.push(`${fieldName}: ${error.message}`);
      report[fieldName] = "FAIL";
    }
  };

  await markStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    } else {
      expect(
        page.url(),
        "Set SALEADS_LOGIN_URL or start this test on the SaleADS login page."
      ).not.toBe("about:blank");
    }

    const googlePopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickByTextAndWait(
      page,
      /sign in with google|iniciar sesion con google|iniciar sesión con google|google/i,
      "Expected Sign in with Google action."
    );

    const googlePopup = await googlePopupPromise;
    const possibleGooglePage = googlePopup || page;
    const accountOption = possibleGooglePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });

    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await waitForUiLoad(page);
    }

    await waitForVisible(
      page.locator("aside, nav").filter({ hasText: /negocio/i }).first(),
      "Expected main app sidebar navigation after login."
    );
    await checkpointScreenshot(page, testInfo, "dashboard-loaded");
  });

  await markStep("Mi Negocio menu", async () => {
    await clickByTextAndWait(page, /mi negocio/i, "Expected Mi Negocio menu option.");

    await waitForVisible(page.getByText(/agregar negocio/i).first(), "Expected Agregar Negocio submenu item.");
    await waitForVisible(
      page.getByText(/administrar negocios/i).first(),
      "Expected Administrar Negocios submenu item."
    );
    await checkpointScreenshot(page, testInfo, "mi-negocio-menu-expanded");
  });

  await markStep("Agregar Negocio modal", async () => {
    await clickByTextAndWait(page, /agregar negocio/i, "Expected Agregar Negocio option to open modal.");

    const modal = page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
    await waitForVisible(modal, "Expected Crear Nuevo Negocio modal to appear.");

    await waitForVisible(modal.getByText(/crear nuevo negocio/i).first(), "Expected modal title Crear Nuevo Negocio.");
    const businessNameInput = modal.getByLabel(/nombre del negocio/i).first();
    await waitForVisible(businessNameInput, "Expected Nombre del Negocio input field.");
    await waitForVisible(
      modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first(),
      "Expected business quota text in modal."
    );
    await waitForVisible(modal.getByRole("button", { name: /cancelar/i }).first(), "Expected Cancelar button.");
    await waitForVisible(
      modal.getByRole("button", { name: /crear negocio/i }).first(),
      "Expected Crear Negocio button."
    );

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, modal.getByRole("button", { name: /cancelar/i }).first());
    await checkpointScreenshot(page, testInfo, "agregar-negocio-modal");
  });

  await markStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickByTextAndWait(page, /mi negocio/i, "Expected Mi Negocio menu to expand again.");
    }

    await clickByTextAndWait(page, /administrar negocios/i, "Expected Administrar Negocios option.");

    await validateHeadingOrText(page, /informacion general|información general/i);
    await validateHeadingOrText(page, /detalles de la cuenta/i);
    await validateHeadingOrText(page, /tus negocios/i);
    await validateHeadingOrText(page, /seccion legal|sección legal/i);
    await checkpointScreenshot(page, testInfo, "administrar-negocios-view", true);
  });

  await markStep("Información General", async () => {
    const infoSection = page
      .locator("section, div, article")
      .filter({ has: page.getByText(/informacion general|información general/i).first() })
      .first();
    await waitForVisible(infoSection, "Expected Información General section.");

    const text = await infoSection.innerText();
    expect(text, "Expected user name in Información General section.").toMatch(/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/);
    expect(text, "Expected user email in Información General section.").toMatch(
      /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/
    );
    expect(text, "Expected BUSINESS PLAN text.").toMatch(/business plan/i);

    await waitForVisible(
      page.getByRole("button", { name: /cambiar plan/i }).first(),
      "Expected Cambiar Plan button."
    );
  });

  await markStep("Detalles de la Cuenta", async () => {
    await waitForVisible(page.getByText(/cuenta creada/i).first(), "Expected Cuenta creada text.");
    await waitForVisible(page.getByText(/estado activo/i).first(), "Expected Estado activo text.");
    await waitForVisible(
      page.getByText(/idioma seleccionado/i).first(),
      "Expected Idioma seleccionado text."
    );
  });

  await markStep("Tus Negocios", async () => {
    await validateHeadingOrText(page, /tus negocios/i);
    await waitForVisible(page.getByText(/agregar negocio/i).first(), "Expected Agregar Negocio button.");
    await waitForVisible(
      page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first(),
      "Expected quota text in Tus Negocios section."
    );

    const businessItems = page.locator("li, [role='row'], [class*='business'], [data-testid*='business']");
    expect(await businessItems.count(), "Expected business list to be visible in Tus Negocios.").toBeGreaterThan(0);
  });

  await markStep("Términos y Condiciones", async () => {
    await validateLegalLink({
      page,
      context,
      linkRegex: /terminos y condiciones|términos y condiciones/i,
      headingRegex: /terminos y condiciones|términos y condiciones/i,
      checkpointName: "terminos-y-condiciones",
      urls,
      testInfo,
    });
  });

  await markStep("Política de Privacidad", async () => {
    await validateLegalLink({
      page,
      context,
      linkRegex: /politica de privacidad|política de privacidad/i,
      headingRegex: /politica de privacidad|política de privacidad/i,
      checkpointName: "politica-de-privacidad",
      urls,
      testInfo,
    });
  });

  await testInfo.attach("final-validation-report", {
    body: Buffer.from(JSON.stringify(report, null, 2), "utf-8"),
    contentType: "application/json",
  });
  await testInfo.attach("final-legal-urls", {
    body: Buffer.from(JSON.stringify(urls, null, 2), "utf-8"),
    contentType: "application/json",
  });

  if (failures.length) {
    throw new Error(
      `Validation failures:\n- ${failures.join("\n- ")}\n\nFinal report:\n${JSON.stringify(report, null, 2)}`
    );
  }
});
