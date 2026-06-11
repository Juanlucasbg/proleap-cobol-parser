const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

const REPORT_KEYS = [
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

function createTextLocators(page, textPattern) {
  return [
    page.getByRole("button", { name: textPattern }).first(),
    page.getByRole("link", { name: textPattern }).first(),
    page.getByRole("menuitem", { name: textPattern }).first(),
    page.getByText(textPattern).first()
  ];
}

async function findVisibleLocator(page, textPattern, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const locators = createTextLocators(page, textPattern);
    for (const locator of locators) {
      try {
        if (await locator.isVisible({ timeout: 250 })) {
          return locator;
        }
      } catch (error) {
        // Keep trying through alternative locators until timeout.
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Element with text/pattern "${String(textPattern)}" not visible.`);
}

async function clickByText(page, textPattern, timeoutMs = 15000) {
  const locator = await findVisibleLocator(page, textPattern, timeoutMs);
  await locator.click();
  await waitForUi(page);
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

test("SaleADS - Mi Negocio full workflow", async ({ page, context }, testInfo) => {
  const report = {};
  const failures = [];
  const googleAccount = process.env.SALEADS_GOOGLE_EMAIL || DEFAULT_GOOGLE_ACCOUNT;
  const baseUrl = process.env.SALEADS_BASE_URL;

  let termsUrl = "";
  let privacyUrl = "";

  const saveCheckpoint = async (fileName, targetPage = page, fullPage = false) => {
    await targetPage.screenshot({
      path: testInfo.outputPath(fileName),
      fullPage
    });
  };

  const markResult = (key, status, detail) => {
    report[key] = {
      status,
      detail: detail || ""
    };
  };

  const runStep = async (key, stepCallback) => {
    try {
      await stepCallback();
      if (!report[key]) {
        markResult(key, "PASS");
      } else if (report[key].status !== "FAIL") {
        report[key].status = "PASS";
      }
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      failures.push(`${key}: ${detail}`);
      markResult(key, "FAIL", detail);
    }
  };

  const openLegalPage = async (linkTextPattern, headingPattern, screenshotName) => {
    const opener = await findVisibleLocator(page, linkTextPattern, 15000);
    const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
    await opener.click();
    const popup = await popupPromise;

    const legalPage = popup || page;
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForTimeout(1200);

    await expect(legalPage.getByText(headingPattern).first()).toBeVisible({ timeout: 15000 });
    const legalBody = (await legalPage.locator("body").innerText()).trim();
    if (legalBody.length < 120) {
      throw new Error("Legal page content is unexpectedly short.");
    }

    await saveCheckpoint(screenshotName, legalPage, true);
    const finalUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUi(page);
    }

    return finalUrl;
  };

  await runStep("Login", async () => {
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    } else if (!page.url().startsWith("http")) {
      throw new Error("Set SALEADS_BASE_URL or pre-open the SaleADS login page before running.");
    }

    await waitForUi(page);

    const loginPattern = /sign in with google|iniciar sesi[oó]n con google|continuar con google/i;
    const googlePopupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
    const loginTrigger = await findVisibleLocator(page, loginPattern, 30000);
    await loginTrigger.click();
    await waitForUi(page);

    const googlePopup = await googlePopupPromise;
    const authPage = googlePopup || page;
    const accountOption = authPage.getByText(googleAccount, { exact: false }).first();
    if (await accountOption.isVisible({ timeout: 12000 }).catch(() => false)) {
      await accountOption.click();
      await waitForUi(authPage);
    }

    if (googlePopup) {
      await page.bringToFront();
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30000 });
    await expect(page.locator("body")).toContainText(/negocio|mi negocio|dashboard|inicio/i, {
      timeout: 30000
    });
    await saveCheckpoint("01-dashboard-loaded.png", page, true);
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByText(page, /negocio/i, 15000);
    await clickByText(page, /mi negocio/i, 15000);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 15000 });
    await saveCheckpoint("02-mi-negocio-expanded.png", page, true);
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByText(page, /agregar negocio/i, 15000);
    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByLabel(/nombre del negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible({
      timeout: 15000
    });
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 15000 });
    await saveCheckpoint("03-agregar-negocio-modal.png", page, true);

    const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickByText(page, /cancelar/i, 10000);
    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeHidden({ timeout: 10000 });
  });

  await runStep("Administrar Negocios view", async () => {
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      await clickByText(page, /mi negocio/i, 10000);
    }

    await clickByText(page, /administrar negocios/i, 15000);
    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible({ timeout: 20000 });
    await saveCheckpoint("04-administrar-negocios-page.png", page, true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/@/).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/business plan/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({
      timeout: 15000
    });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/estado activo|activo/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/idioma seleccionado|idioma/i).first()).toBeVisible({
      timeout: 15000
    });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible({
      timeout: 15000
    });
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible({
      timeout: 15000
    });
  });

  await runStep("Términos y Condiciones", async () => {
    termsUrl = await openLegalPage(
      /t[ée]rminos y condiciones/i,
      /t[ée]rminos y condiciones/i,
      "05-terminos-y-condiciones.png"
    );
    markResult("Términos y Condiciones", "PASS", `URL: ${termsUrl}`);
  });

  await runStep("Política de Privacidad", async () => {
    privacyUrl = await openLegalPage(
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad.png"
    );
    markResult("Política de Privacidad", "PASS", `URL: ${privacyUrl}`);
  });

  for (const key of REPORT_KEYS) {
    if (!report[key]) {
      markResult(key, "FAIL", "Step was not executed.");
      failures.push(`${key}: Step was not executed.`);
    }
  }

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    accountUsed: googleAccount,
    results: report
  };

  await fs.writeFile(
    testInfo.outputPath("saleads-mi-negocio-final-report.json"),
    JSON.stringify(finalReport, null, 2),
    "utf8"
  );

  if (failures.length > 0) {
    throw new Error(`Workflow finished with validation failures:\n- ${failures.join("\n- ")}`);
  }
});
