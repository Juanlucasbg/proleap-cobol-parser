const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

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
  "Política de Privacidad"
];

function buildInitialReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "NOT_RUN"]));
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(fileName, { path, contentType: "image/png" });
}

async function expectVisibleText(page, text, timeout = 30000) {
  const pattern = new RegExp(escapeRegExp(text), "i");
  const locators = [
    page.getByRole("heading", { name: pattern }).first(),
    page.getByRole("button", { name: pattern }).first(),
    page.getByRole("link", { name: pattern }).first(),
    page.getByText(pattern).first()
  ];

  let lastError;
  for (const locator of locators) {
    try {
      await expect(locator).toBeVisible({ timeout });
      return locator;
    } catch (error) {
      lastError = error;
    }
  }

  throw new Error(`Text "${text}" was not visible. Last error: ${lastError?.message || "n/a"}`);
}

async function clickVisibleText(page, textCandidates, clickContext) {
  let lastError;

  for (const text of textCandidates) {
    const pattern = new RegExp(escapeRegExp(text), "i");
    const locators = [
      page.getByRole("button", { name: pattern }).first(),
      page.getByRole("link", { name: pattern }).first(),
      page.getByRole("menuitem", { name: pattern }).first(),
      page.getByText(pattern).first()
    ];

    for (const locator of locators) {
      try {
        await locator.waitFor({ state: "visible", timeout: 2500 });
        await locator.click();
        await waitForUiToLoad(page);
        return;
      } catch (error) {
        lastError = error;
      }
    }
  }

  throw new Error(
    `Unable to click target for "${clickContext}". Tried texts: ${textCandidates.join(", ")}. Last error: ${
      lastError?.message || "n/a"
    }`
  );
}

async function selectGoogleAccountIfPrompted(page, email) {
  const pattern = new RegExp(escapeRegExp(email), "i");
  const accountLocator = page.getByText(pattern).first();
  const useAccountButton = page.getByRole("button", { name: pattern }).first();

  const accountVisible = await accountLocator.isVisible().catch(() => false);
  const useAccountVisible = await useAccountButton.isVisible().catch(() => false);

  if (accountVisible) {
    await accountLocator.click();
    await waitForUiToLoad(page);
    return true;
  }

  if (useAccountVisible) {
    await useAccountButton.click();
    await waitForUiToLoad(page);
    return true;
  }

  return false;
}

async function resolveAppPage(primaryPage, secondaryPage) {
  try {
    await expectVisibleText(primaryPage, "Negocio", 60000);
    return primaryPage;
  } catch (primaryError) {
    if (!secondaryPage || secondaryPage.isClosed()) {
      throw primaryError;
    }
    await expectVisibleText(secondaryPage, "Negocio", 60000);
    return secondaryPage;
  }
}

async function openAndValidateLegalDocument({
  appPage,
  linkText,
  expectedHeading,
  screenshotName,
  testInfo
}) {
  const context = appPage.context();
  const appUrlBeforeClick = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await clickVisibleText(appPage, [linkText], `open legal link: ${linkText}`);
  const popup = await popupPromise;
  const legalPage = popup || appPage;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
  }

  await waitForUiToLoad(legalPage);
  await expectVisibleText(legalPage, expectedHeading, 45000);

  const legalText = (await legalPage.locator("body").innerText()).trim();
  if (legalText.length < 80) {
    throw new Error(`Legal content for "${expectedHeading}" looks too short.`);
  }

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else if (appPage.url() !== appUrlBeforeClick) {
    await appPage
      .goBack({ waitUntil: "domcontentloaded" })
      .catch(async () => appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" }));
    await waitForUiToLoad(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildInitialReport();
  const failures = [];
  const finalUrls = {};
  let appPage = page;

  const baseUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_APP_URL || testInfo.project.use.baseURL;
  if (baseUrl) {
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToLoad(page);
  } else {
    throw new Error(
      "No login URL configured. Provide SALEADS_LOGIN_URL (or SALEADS_APP_URL) to open the environment login page."
    );
  }

  async function executeStep(reportField, action) {
    try {
      await action();
      report[reportField] = "PASS";
    } catch (error) {
      report[reportField] = "FAIL";
      failures.push(`${reportField}: ${error.message}`);
    }
  }

  await executeStep("Login", async () => {
    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await clickVisibleText(
      page,
      [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Inicia sesión con Google",
        "Continuar con Google",
        "Google"
      ],
      "Google login button"
    );

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfPrompted(popup, GOOGLE_ACCOUNT_EMAIL);
      appPage = await resolveAppPage(popup, page);
    } else {
      await selectGoogleAccountIfPrompted(page, GOOGLE_ACCOUNT_EMAIL);
      appPage = await resolveAppPage(page, null);
    }

    await expectVisibleText(appPage, "Negocio", 60000);
    await captureCheckpoint(appPage, testInfo, "checkpoint-01-dashboard.png");
  });

  await executeStep("Mi Negocio menu", async () => {
    await clickVisibleText(appPage, ["Negocio"], "open Negocio section");
    await clickVisibleText(appPage, ["Mi Negocio"], "open Mi Negocio menu");
    await expectVisibleText(appPage, "Agregar Negocio");
    await expectVisibleText(appPage, "Administrar Negocios");
    await captureCheckpoint(appPage, testInfo, "checkpoint-02-mi-negocio-menu.png");
  });

  await executeStep("Agregar Negocio modal", async () => {
    await clickVisibleText(appPage, ["Agregar Negocio"], "open Agregar Negocio modal");
    await expectVisibleText(appPage, "Crear Nuevo Negocio");
    await expectVisibleText(appPage, "Nombre del Negocio");
    await expectVisibleText(appPage, "Tienes 2 de 3 negocios");
    await expectVisibleText(appPage, "Cancelar");
    await expectVisibleText(appPage, "Crear Negocio");
    await captureCheckpoint(appPage, testInfo, "checkpoint-03-agregar-negocio-modal.png");

    const nameInputCandidates = [
      appPage.getByLabel(/Nombre del Negocio/i).first(),
      appPage.getByPlaceholder(/Nombre del Negocio/i).first(),
      appPage.getByRole("textbox", { name: /Nombre del Negocio/i }).first()
    ];
    for (const locator of nameInputCandidates) {
      const visible = await locator.isVisible().catch(() => false);
      if (visible) {
        await locator.click();
        await locator.fill("Negocio Prueba Automatización");
        break;
      }
    }

    await clickVisibleText(appPage, ["Cancelar"], "close Agregar Negocio modal");
  });

  await executeStep("Administrar Negocios view", async () => {
    const administrarVisible = await appPage.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickVisibleText(appPage, ["Mi Negocio"], "expand Mi Negocio menu again");
    }

    await clickVisibleText(appPage, ["Administrar Negocios"], "open Administrar Negocios");
    await expectVisibleText(appPage, "Información General", 45000);
    await expectVisibleText(appPage, "Detalles de la Cuenta");
    await expectVisibleText(appPage, "Tus Negocios");
    await expectVisibleText(appPage, "Sección Legal");
    await captureCheckpoint(appPage, testInfo, "checkpoint-04-administrar-negocios.png", true);
  });

  await executeStep("Información General", async () => {
    await expectVisibleText(appPage, "Información General");
    await expectVisibleText(appPage, "BUSINESS PLAN");
    await expectVisibleText(appPage, "Cambiar Plan");

    const userNameVisible = await appPage.getByText(/^[A-Za-zÀ-ÿ]+(?:\s+[A-Za-zÀ-ÿ]+)+$/).first().isVisible().catch(() => false);
    if (!userNameVisible) {
      throw new Error("User name was not detected in Información General.");
    }

    const userEmailVisible = await appPage.getByText(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/).first().isVisible().catch(() => false);
    if (!userEmailVisible) {
      throw new Error("User email was not detected in Información General.");
    }
  });

  await executeStep("Detalles de la Cuenta", async () => {
    await expectVisibleText(appPage, "Detalles de la Cuenta");
    await expectVisibleText(appPage, "Cuenta creada");
    await expectVisibleText(appPage, "Estado activo");
    await expectVisibleText(appPage, "Idioma seleccionado");
  });

  await executeStep("Tus Negocios", async () => {
    await expectVisibleText(appPage, "Tus Negocios");
    await expectVisibleText(appPage, "Agregar Negocio");
    await expectVisibleText(appPage, "Tienes 2 de 3 negocios");
  });

  await executeStep("Términos y Condiciones", async () => {
    finalUrls["Términos y Condiciones"] = await openAndValidateLegalDocument({
      appPage,
      linkText: "Términos y Condiciones",
      expectedHeading: "Términos y Condiciones",
      screenshotName: "checkpoint-05-terminos-y-condiciones.png",
      testInfo
    });
  });

  await executeStep("Política de Privacidad", async () => {
    finalUrls["Política de Privacidad"] = await openAndValidateLegalDocument({
      appPage,
      linkText: "Política de Privacidad",
      expectedHeading: "Política de Privacidad",
      screenshotName: "checkpoint-06-politica-de-privacidad.png",
      testInfo
    });
  });

  const finalReport = {
    generatedAtUtc: new Date().toISOString(),
    testName: "saleads_mi_negocio_full_test",
    results: report,
    legalFinalUrls: finalUrls,
    failures
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  await testInfo.attach("saleads-mi-negocio-report.json", {
    path: reportPath,
    contentType: "application/json"
  });

  console.table(report);
  if (Object.keys(finalUrls).length > 0) {
    console.log("Final legal URLs:", JSON.stringify(finalUrls));
  }

  expect(
    failures,
    `One or more workflow validations failed:\n${failures.map((failure) => `- ${failure}`).join("\n")}`
  ).toEqual([]);
});
