const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const VALIDATION_FIELDS = [
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

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function firstVisible(page, locators, description, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      const candidate = locator.first();
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
    await page.waitForTimeout(300);
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function byVisibleText(page, matcher, description, timeoutMs = 12000) {
  return firstVisible(
    page,
    [
      page.getByRole("button", { name: matcher }),
      page.getByRole("link", { name: matcher }),
      page.getByRole("menuitem", { name: matcher }),
      page.getByText(matcher),
    ],
    description,
    timeoutMs,
  );
}

async function clickByText(page, matcher, description, timeoutMs = 12000) {
  const target = await byVisibleText(page, matcher, description, timeoutMs);
  await target.click({ timeout: 15000 });
  await waitForUiLoad(page);
}

async function validateLegalPage({
  appPage,
  linkMatcher,
  headingMatcher,
  contentMatcher,
  screenshotName,
  testInfo,
}) {
  const startUrl = appPage.url();
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
  const navigationPromise = appPage.waitForNavigation({ timeout: 10000 }).catch(() => null);

  await clickByText(appPage, linkMatcher, `legal link: ${linkMatcher}`);

  const popup = await popupPromise;
  let targetPage = appPage;

  if (popup) {
    targetPage = popup;
    await targetPage.waitForLoadState("domcontentloaded", { timeout: 20000 });
    await waitForUiLoad(targetPage);
  } else {
    await navigationPromise;
    await waitForUiLoad(appPage);
  }

  await expect(targetPage.getByText(headingMatcher).first()).toBeVisible({ timeout: 20000 });

  const contentLocator = await firstVisible(
    targetPage,
    [
      targetPage.getByText(contentMatcher),
      targetPage.locator("main p, article p, section p, p"),
      targetPage.locator("li"),
    ],
    `legal content for ${headingMatcher}`,
    12000,
  );

  await expect(contentLocator).toBeVisible();
  await targetPage.screenshot({ path: testInfo.outputPath(screenshotName), fullPage: true });

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else if (appPage.url() !== startUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiLoad(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const validationStatus = Object.fromEntries(VALIDATION_FIELDS.map((field) => [field, "FAIL"]));
  const finalUrls = {
    "Términos y Condiciones": "",
    "Política de Privacidad": "",
  };
  const failures = [];

  const runValidation = async (field, fn) => {
    try {
      await fn();
      validationStatus[field] = "PASS";
    } catch (error) {
      validationStatus[field] = "FAIL";
      failures.push(`${field}: ${error.message}`);
    }
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  }

  await runValidation("Login", async () => {
    const loginButton = await byVisibleText(
      page,
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      "Google login button",
      30000,
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await loginButton.click({ timeout: 15000 });
    await waitForUiLoad(page);

    const popup = await popupPromise;
    let authPage = popup;

    if (authPage) {
      await authPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
      await waitForUiLoad(authPage);
    } else {
      const sameTabGoogleFlow =
        /accounts\.google\.com/i.test(page.url()) ||
        (await page.getByText(/elige una cuenta|choose an account|google/i).first().isVisible().catch(() => false));
      if (sameTabGoogleFlow) {
        authPage = page;
      }
    }

    if (authPage) {
      const accountOption = await byVisibleText(
        authPage,
        /juanlucasbarbiergarzon@gmail\.com/i,
        "Google account option",
        15000,
      );
      await accountOption.click({ timeout: 15000 });
      await waitForUiLoad(authPage);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({ timeout: 30000 });
    await page.screenshot({ path: testInfo.outputPath("01-dashboard-loaded.png"), fullPage: true });
  });

  await runValidation("Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 20000 });

    await clickByText(page, /mi negocio/i, "Mi Negocio menu");

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 20000 });
    await page.screenshot({ path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"), fullPage: true });
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickByText(page, /agregar negocio/i, "Agregar Negocio option");

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 15000 });

    const nameInput = await firstVisible(
      page,
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator("input[name*='nombre' i], input[placeholder*='nombre' i]"),
      ],
      "Nombre del Negocio field",
    );

    await expect(nameInput).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 15000 });

    await page.screenshot({ path: testInfo.outputPath("03-agregar-negocio-modal.png"), fullPage: true });

    await nameInput.fill("Negocio Prueba Automatización");
    await clickByText(page, /cancelar/i, "Cancelar modal");
    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeHidden({ timeout: 15000 });
  });

  await runValidation("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickByText(page, /mi negocio/i, "Re-open Mi Negocio");
    }

    await clickByText(page, /administrar negocios/i, "Administrar Negocios option");

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible({ timeout: 20000 });
    await page.screenshot({ path: testInfo.outputPath("04-administrar-negocios-view.png"), fullPage: true });
  });

  await runValidation("Información General", async () => {
    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({ timeout: 15000 });

    const emailLocator = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
    await expect(emailLocator).toBeVisible({ timeout: 15000 });

    await expect(
      page.getByText(/nombre|name|usuario|perfil/i).first(),
      "Expected user name label/value to be visible in Información General",
    ).toBeVisible({ timeout: 15000 });
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/estado activo|activo/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/idioma seleccionado|idioma/i).first()).toBeVisible({ timeout: 15000 });
  });

  await runValidation("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible({ timeout: 15000 });

    const businessList = await firstVisible(
      page,
      [
        page.locator("[role='list'], ul, table").filter({ hasText: /negocio/i }),
        page.getByText(/negocio/i),
      ],
      "business list",
      15000,
    );
    await expect(businessList).toBeVisible();
  });

  await runValidation("Términos y Condiciones", async () => {
    finalUrls["Términos y Condiciones"] = await validateLegalPage({
      appPage: page,
      linkMatcher: /t[eé]rminos y condiciones/i,
      headingMatcher: /t[eé]rminos y condiciones/i,
      contentMatcher: /condiciones|t[eé]rminos|uso|legal/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo,
    });
  });

  await runValidation("Política de Privacidad", async () => {
    finalUrls["Política de Privacidad"] = await validateLegalPage({
      appPage: page,
      linkMatcher: /pol[ií]tica de privacidad/i,
      headingMatcher: /pol[ií]tica de privacidad/i,
      contentMatcher: /privacidad|datos|informaci[oó]n personal|legal/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo,
    });
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    status: validationStatus,
    legalFinalUrls: finalUrls,
    failures,
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json",
  });

  expect(
    Object.values(validationStatus).every((result) => result === "PASS"),
    `One or more validations failed:\n${failures.join("\n")}`,
  ).toBeTruthy();
});
