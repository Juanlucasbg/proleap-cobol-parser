const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_KEYS = [
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

async function waitForUi(page) {
  await page.waitForTimeout(300);
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(300);
}

async function isVisible(locator, timeout = 4000) {
  try {
    await expect(locator.first()).toBeVisible({ timeout });
    return true;
  } catch {
    return false;
  }
}

async function isAnyVisible(locators, timeout = 4000) {
  for (const locator of locators) {
    if (await isVisible(locator, timeout)) {
      return true;
    }
  }

  return false;
}

async function clickFirstVisible(page, locators, description) {
  for (const locator of locators) {
    if (await isVisible(locator, 2500)) {
      await locator.first().click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Could not click ${description}.`);
}

async function fillFirstVisible(locators, value, description) {
  for (const locator of locators) {
    if (await isVisible(locator, 2000)) {
      await locator.first().fill(value);
      return;
    }
  }

  throw new Error(`Could not fill ${description}.`);
}

async function maybeChooseGoogleAccount(authPage) {
  await waitForUi(authPage);

  const accountLocators = [
    authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
    authPage.locator(`[data-email="${GOOGLE_ACCOUNT_EMAIL}"]`),
    authPage.locator(`text=${GOOGLE_ACCOUNT_EMAIL}`),
  ];

  for (const locator of accountLocators) {
    if (await isVisible(locator, 3000)) {
      await locator.first().click();
      await waitForUi(authPage);
      return true;
    }
  }

  return false;
}

async function ensureAdministrarNegociosVisible(page) {
  const administrarNegociosLocators = [
    page.getByRole("link", { name: /Administrar Negocios/i }),
    page.getByRole("button", { name: /Administrar Negocios/i }),
    page.getByText(/Administrar Negocios/i),
  ];

  if (await isAnyVisible(administrarNegociosLocators, 2000)) {
    return;
  }

  await clickFirstVisible(
    page,
    [
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
      page.getByRole("button", { name: /Negocio/i }),
      page.getByRole("link", { name: /Negocio/i }),
      page.getByText(/^Negocio$/i),
    ],
    "Mi Negocio/Negocio menu to reveal submenu"
  );
}

async function openLegalLinkAndValidate({
  appPage,
  context,
  linkNameRegex,
  headingRegex,
  screenshotFileName,
  testInfo,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickFirstVisible(
    appPage,
    [
      appPage.getByRole("link", { name: linkNameRegex }),
      appPage.getByRole("button", { name: linkNameRegex }),
      appPage.getByText(linkNameRegex),
    ],
    `legal link: ${linkNameRegex}`
  );

  const popup = await popupPromise;
  const legalPage = popup || appPage;
  const openedNewTab = Boolean(popup);

  await waitForUi(legalPage);

  const headingFound = await isAnyVisible(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex),
    ],
    15000
  );

  if (!headingFound) {
    throw new Error(`Could not find legal heading ${headingRegex}.`);
  }

  const legalParagraphVisible = await isAnyVisible(
    [legalPage.locator("main p"), legalPage.locator("article p"), legalPage.locator("body p")],
    10000
  );

  if (!legalParagraphVisible) {
    throw new Error("Legal content text was not visible.");
  }

  await legalPage.screenshot({
    path: testInfo.outputPath(screenshotFileName),
    fullPage: true,
  });

  const finalUrl = legalPage.url();

  if (openedNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test.describe("SaleADS Mi Negocio Workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    test.setTimeout(6 * 60 * 1000);

    const report = Object.fromEntries(REPORT_KEYS.map((key) => [key, "NOT_RUN"]));
    const failures = [];
    const legalUrls = {
      "Términos y Condiciones": null,
      "Política de Privacidad": null,
    };

    const loginUrl =
      process.env.SALEADS_LOGIN_URL ||
      process.env.SALEADS_BASE_URL ||
      process.env.BASE_URL ||
      process.env.APP_URL ||
      null;

    let appPage = page;

    const runStep = async (key, action) => {
      try {
        await action();
        report[key] = "PASS";
      } catch (error) {
        report[key] = "FAIL";
        failures.push(`${key}: ${error.message}`);
      }
    };

    await runStep("Login", async () => {
      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
        await waitForUi(page);
      } else if (page.url().startsWith("about:blank")) {
        throw new Error(
          "No login URL available. Set SALEADS_LOGIN_URL, SALEADS_BASE_URL, BASE_URL, or APP_URL."
        );
      }

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

      await clickFirstVisible(
        page,
        [
          page.getByRole("button", { name: /Sign in with Google/i }),
          page.getByRole("button", { name: /Iniciar sesi[oó]n con Google/i }),
          page.getByText(/Sign in with Google/i),
          page.getByText(/Iniciar sesi[oó]n con Google/i),
          page.getByRole("button", { name: /Google/i }),
        ],
        "Google login button"
      );

      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded").catch(() => {});
        await maybeChooseGoogleAccount(popup);

        await Promise.race([
          popup.waitForEvent("close", { timeout: 60000 }),
          popup.waitForURL((url) => !/accounts\.google\.com/i.test(url), { timeout: 60000 }),
          page.waitForLoadState("domcontentloaded", { timeout: 60000 }),
        ]).catch(() => {});

        appPage = !popup.isClosed() && !/accounts\.google\.com/i.test(popup.url()) ? popup : page;
      } else {
        await maybeChooseGoogleAccount(page);
        appPage = page;
      }

      await waitForUi(appPage);

      const sidebarVisible = await isAnyVisible(
        [
          appPage.locator("aside"),
          appPage.getByRole("navigation"),
          appPage.getByText(/Mi Negocio|Negocio/i),
        ],
        20000
      );

      if (!sidebarVisible) {
        throw new Error("Main app interface did not load with visible left sidebar.");
      }

      await appPage.screenshot({
        path: testInfo.outputPath("01-dashboard-loaded.png"),
        fullPage: true,
      });
    });

    await runStep("Mi Negocio menu", async () => {
      await clickFirstVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /Negocio/i }),
          appPage.getByRole("link", { name: /^Negocio$/i }),
          appPage.getByText(/^Negocio$/i),
        ],
        "Negocio"
      );

      await clickFirstVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /Mi Negocio/i }),
          appPage.getByRole("link", { name: /Mi Negocio/i }),
          appPage.getByText(/Mi Negocio/i),
        ],
        "Mi Negocio"
      );

      await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByText(/Administrar Negocios/i).first()).toBeVisible({
        timeout: 15000,
      });

      await appPage.screenshot({
        path: testInfo.outputPath("02-mi-negocio-expanded.png"),
        fullPage: true,
      });
    });

    await runStep("Agregar Negocio modal", async () => {
      await clickFirstVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /Agregar Negocio/i }),
          appPage.getByRole("link", { name: /Agregar Negocio/i }),
          appPage.getByText(/Agregar Negocio/i),
        ],
        "Agregar Negocio"
      );

      await expect(appPage.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
        timeout: 15000,
      });
      await expect(appPage.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({
        timeout: 15000,
      });
      await expect(appPage.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({
        timeout: 15000,
      });

      await fillFirstVisible(
        [
          appPage.getByRole("textbox", { name: /Nombre del Negocio/i }),
          appPage.getByLabel(/Nombre del Negocio/i),
          appPage.getByPlaceholder(/Nombre del Negocio/i),
          appPage.locator('input[name*="nombre"], input[id*="nombre"]').first(),
        ],
        "Negocio Prueba Automatización",
        "Nombre del Negocio"
      );

      await appPage.screenshot({
        path: testInfo.outputPath("03-agregar-negocio-modal.png"),
        fullPage: true,
      });

      await clickFirstVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /Cancelar/i }),
          appPage.getByText(/^Cancelar$/i),
        ],
        "Cancelar modal"
      );
    });

    await runStep("Administrar Negocios view", async () => {
      await ensureAdministrarNegociosVisible(appPage);

      await clickFirstVisible(
        appPage,
        [
          appPage.getByRole("link", { name: /Administrar Negocios/i }),
          appPage.getByRole("button", { name: /Administrar Negocios/i }),
          appPage.getByText(/Administrar Negocios/i),
        ],
        "Administrar Negocios"
      );

      await expect(appPage.getByText(/Informaci[oó]n General/i).first()).toBeVisible({
        timeout: 15000,
      });
      await expect(appPage.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({
        timeout: 15000,
      });
      await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 15000 });

      await appPage.screenshot({
        path: testInfo.outputPath("04-administrar-negocios-view.png"),
        fullPage: true,
      });
    });

    await runStep("Información General", async () => {
      const userVisible = await isAnyVisible(
        [
          appPage.locator("section,div").getByText(/@/),
          appPage.locator("section,div").getByText(/[A-Za-z].*[A-Za-z]/),
        ],
        12000
      );

      if (!userVisible) {
        throw new Error("User name/email was not visible in Información General.");
      }

      await expect(appPage.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 12000 });
      await expect(appPage.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({
        timeout: 12000,
      });
    });

    await runStep("Detalles de la Cuenta", async () => {
      await expect(appPage.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 12000 });
      await expect(appPage.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 12000 });
      await expect(appPage.getByText(/Idioma seleccionado/i).first()).toBeVisible({
        timeout: 12000,
      });
    });

    await runStep("Tus Negocios", async () => {
      await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 12000 });
      await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 12000 });
      await expect(appPage.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
        timeout: 12000,
      });
    });

    await runStep("Términos y Condiciones", async () => {
      legalUrls["Términos y Condiciones"] = await openLegalLinkAndValidate({
        appPage,
        context,
        linkNameRegex: /T[eé]rminos y Condiciones/i,
        headingRegex: /T[eé]rminos y Condiciones/i,
        screenshotFileName: "05-terminos-y-condiciones.png",
        testInfo,
      });
    });

    await runStep("Política de Privacidad", async () => {
      legalUrls["Política de Privacidad"] = await openLegalLinkAndValidate({
        appPage,
        context,
        linkNameRegex: /Pol[ií]tica de Privacidad/i,
        headingRegex: /Pol[ií]tica de Privacidad/i,
        screenshotFileName: "06-politica-de-privacidad.png",
        testInfo,
      });
    });

    const finalReport = {
      name: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      report,
      legalUrls,
      failures,
    };

    const finalReportPath = testInfo.outputPath("final-report.json");
    await fs.writeFile(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: finalReportPath,
      contentType: "application/json",
    });

    expect(
      failures,
      `Validation failures:\n${failures.map((failure) => `- ${failure}`).join("\n")}`
    ).toEqual([]);
  });
});
