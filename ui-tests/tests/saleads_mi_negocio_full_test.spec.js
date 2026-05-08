const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const TEST_NAME = "saleads_mi_negocio_full_test";
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

const DEFAULT_ARTIFACTS_DIR = path.resolve(__dirname, "..", "artifacts");
const ARTIFACTS_DIR = process.env.SALEADS_ARTIFACTS_DIR || DEFAULT_ARTIFACTS_DIR;
const SCREENSHOT_DIR = path.join(ARTIFACTS_DIR, "screenshots", TEST_NAME);
const REPORT_PATH = path.join(ARTIFACTS_DIR, `${TEST_NAME}.report.json`);
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toPattern(textOrRegex) {
  if (textOrRegex instanceof RegExp) {
    return textOrRegex;
  }

  return new RegExp(escapeRegExp(textOrRegex), "i");
}

async function isLocatorVisible(locator) {
  try {
    if ((await locator.count()) < 1) {
      return false;
    }

    return await locator.first().isVisible();
  } catch {
    return false;
  }
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");

  try {
    await page.waitForLoadState("networkidle", { timeout: 10_000 });
  } catch {
    // Some pages keep long-running requests open; domcontentloaded is enough in that case.
  }
}

async function clickByVisibleText(page, textCandidates) {
  for (const candidate of textCandidates) {
    const pattern = toPattern(candidate);
    const locators = [
      page.getByRole("button", { name: pattern }),
      page.getByRole("link", { name: pattern }),
      page.getByRole("menuitem", { name: pattern }),
      page.getByRole("tab", { name: pattern }),
      page.getByText(pattern)
    ];

    for (const locator of locators) {
      if (await isLocatorVisible(locator)) {
        await locator.first().click();
        await waitForUiToSettle(page);
        return;
      }
    }
  }

  throw new Error(
    `Could not find clickable visible text candidate: ${textCandidates
      .map((candidate) => candidate.toString())
      .join(", ")}`
  );
}

async function expectAnyVisible(page, locatorFactories, timeoutMs, errorMessage) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const createLocator of locatorFactories) {
      const locator = createLocator(page);
      if (await isLocatorVisible(locator)) {
        return locator.first();
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(errorMessage);
}

async function expectTextVisible(page, textOrRegex, timeoutMs = 15_000) {
  const pattern = toPattern(textOrRegex);
  await expect(page.getByText(pattern).first()).toBeVisible({ timeout: timeoutMs });
}

async function captureScreenshot(page, fileName, fullPage = false) {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
  const screenshotPath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function chooseGoogleAccountIfPrompted(page) {
  const accountLocator = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
  if (await isLocatorVisible(accountLocator)) {
    await accountLocator.click();
    await waitForUiToSettle(page);
  }
}

async function assertLegalContentVisible(page) {
  const paragraph = page.locator("main p, article p, p").first();
  if (await isLocatorVisible(paragraph)) {
    return;
  }

  const bodyText = await page.locator("body").innerText();
  if (bodyText.trim().length < 200) {
    throw new Error("Legal content text is not visible or too short.");
  }
}

test(TEST_NAME, async ({ page }) => {
  const stepResults = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const details = {};

  const saveReport = async () => {
    await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
    const report = {
      testName: TEST_NAME,
      generatedAtUtc: new Date().toISOString(),
      results: REPORT_FIELDS.map((field) => ({
        field,
        status: stepResults[field]
      })),
      details
    };

    await fs.writeFile(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    console.log(`Final report saved at: ${REPORT_PATH}`);
    console.table(report.results);
  };

  async function runValidation(field, action) {
    try {
      await action();
      stepResults[field] = "PASS";
    } catch (error) {
      stepResults[field] = "FAIL";
      details[field] = String(error && error.message ? error.message : error);
    }
  }

  await runValidation("Login", async () => {
    const saleadsUrl = process.env.SALEADS_URL;
    if (saleadsUrl) {
      await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
    } else if (page.url() === "about:blank") {
      throw new Error(
        "SALEADS_URL is not configured and browser is on about:blank. Set SALEADS_URL or start the browser on the SaleADS login page."
      );
    }

    await waitForUiToSettle(page);

    const googlePopupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);

    await clickByVisibleText(page, [
      /Sign in with Google/i,
      /Iniciar sesi[oó]n con Google/i,
      /Continuar con Google/i,
      /Google/i
    ]);

    const googlePopup = await googlePopupPromise;

    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      await chooseGoogleAccountIfPrompted(googlePopup);
      await googlePopup.waitForEvent("close", { timeout: 45_000 }).catch(() => null);
      await page.bringToFront();
    } else {
      await chooseGoogleAccountIfPrompted(page);
    }

    await waitForUiToSettle(page);

    await expectAnyVisible(
      page,
      [
        (candidatePage) => candidatePage.locator("aside"),
        (candidatePage) => candidatePage.getByRole("navigation"),
        (candidatePage) => candidatePage.getByText(/Mi Negocio|Negocio/i)
      ],
      45_000,
      "Main app interface / left sidebar was not visible after Google login."
    );

    await captureScreenshot(page, "01-dashboard-loaded.png", true);
  });

  await runValidation("Mi Negocio menu", async () => {
    await clickByVisibleText(page, [/Mi Negocio/i, /Negocio/i]);
    await expectTextVisible(page, /Agregar Negocio/i, 15_000);
    await expectTextVisible(page, /Administrar Negocios/i, 15_000);
    await captureScreenshot(page, "02-mi-negocio-menu-expanded.png");
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, [/Agregar Negocio/i]);
    await expectTextVisible(page, /Crear Nuevo Negocio/i, 15_000);
    await expectTextVisible(page, /Nombre del Negocio/i, 15_000);
    await expectTextVisible(page, /Tienes 2 de 3 negocios/i, 15_000);
    await expectTextVisible(page, /Cancelar/i, 15_000);
    await expectTextVisible(page, /Crear Negocio/i, 15_000);

    const businessNameInput = page
      .getByRole("textbox", { name: /Nombre del Negocio/i })
      .first();
    if (await isLocatorVisible(businessNameInput)) {
      await businessNameInput.fill("Negocio Prueba Automatización");
    }

    await captureScreenshot(page, "03-agregar-negocio-modal.png");
    await clickByVisibleText(page, [/Cancelar/i]);
  });

  await runValidation("Administrar Negocios view", async () => {
    await clickByVisibleText(page, [/Mi Negocio/i, /Negocio/i]).catch(() => null);
    await clickByVisibleText(page, [/Administrar Negocios/i]);
    await waitForUiToSettle(page);
    await expectTextVisible(page, /Informaci[oó]n General/i, 20_000);
    await expectTextVisible(page, /Detalles de la Cuenta/i, 20_000);
    await expectTextVisible(page, /Tus Negocios/i, 20_000);
    await expectTextVisible(page, /Secci[oó]n Legal/i, 20_000);
    await captureScreenshot(page, "04-administrar-negocios-page.png", true);
  });

  await runValidation("Información General", async () => {
    await expectAnyVisible(
      page,
      [
        (candidatePage) => candidatePage.locator('[data-testid*="name"]'),
        (candidatePage) => candidatePage.getByText(/@/i)
      ],
      10_000,
      "User name/email area not visible."
    );
    await expectTextVisible(page, /BUSINESS PLAN/i, 15_000);
    await expectTextVisible(page, /Cambiar Plan/i, 15_000);
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expectTextVisible(page, /Cuenta creada/i, 15_000);
    await expectTextVisible(page, /Estado activo|Estado.*activo/i, 15_000);
    await expectTextVisible(page, /Idioma seleccionado/i, 15_000);
  });

  await runValidation("Tus Negocios", async () => {
    await expectTextVisible(page, /Tus Negocios/i, 15_000);
    await expectTextVisible(page, /Agregar Negocio/i, 15_000);
    await expectTextVisible(page, /Tienes 2 de 3 negocios/i, 15_000);
  });

  await runValidation("Términos y Condiciones", async () => {
    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickByVisibleText(page, [/T[eé]rminos y Condiciones/i]);
    let legalPage = await popupPromise;

    if (!legalPage) {
      legalPage = page;
    }

    await waitForUiToSettle(legalPage);
    await expectTextVisible(legalPage, /T[eé]rminos y Condiciones/i, 20_000);
    await assertLegalContentVisible(legalPage);
    await captureScreenshot(legalPage, "05-terminos-y-condiciones.png", true);
    details.terminosUrl = legalPage.url();

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUiToSettle(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUiToSettle(page);
    }
  });

  await runValidation("Política de Privacidad", async () => {
    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickByVisibleText(page, [/Pol[ií]tica de Privacidad/i]);
    let legalPage = await popupPromise;

    if (!legalPage) {
      legalPage = page;
    }

    await waitForUiToSettle(legalPage);
    await expectTextVisible(legalPage, /Pol[ií]tica de Privacidad/i, 20_000);
    await assertLegalContentVisible(legalPage);
    await captureScreenshot(legalPage, "06-politica-de-privacidad.png", true);
    details.politicaPrivacidadUrl = legalPage.url();

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUiToSettle(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUiToSettle(page);
    }
  });

  await saveReport();

  const failedFields = REPORT_FIELDS.filter((field) => stepResults[field] !== "PASS");
  expect(
    failedFields,
    `One or more validations failed. See ${REPORT_PATH} and screenshots in ${SCREENSHOT_DIR}`
  ).toEqual([]);
});
