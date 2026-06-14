const fs = require("node:fs");
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
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 5_000 }).catch(() => {});
  await page.waitForTimeout(350);
}

function candidatesByText(page, textPattern) {
  return [
    page.getByRole("button", { name: textPattern }).first(),
    page.getByRole("link", { name: textPattern }).first(),
    page.getByRole("menuitem", { name: textPattern }).first(),
    page.getByRole("tab", { name: textPattern }).first(),
    page.getByText(textPattern).first(),
  ];
}

async function findVisibleElement(page, textPattern, timeoutMs = 8_000) {
  const end = Date.now() + timeoutMs;

  while (Date.now() < end) {
    for (const candidate of candidatesByText(page, textPattern)) {
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`No visible element found for pattern ${textPattern}`);
}

async function tryFindVisibleElement(page, textPattern, timeoutMs = 3_000) {
  try {
    return await findVisibleElement(page, textPattern, timeoutMs);
  } catch {
    return null;
  }
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function runStep(report, failures, stepName, stepFn) {
  try {
    await stepFn();
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = "FAIL";
    failures.push(`${stepName}: ${error instanceof Error ? error.message : String(error)}`);
  }
}

async function validateLegalLink({
  page,
  linkPattern,
  headingPattern,
  screenshotName,
  testInfo,
}) {
  const context = page.context();
  const currentUrl = page.url();
  const link = await findVisibleElement(page, linkPattern);
  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);

  await clickAndWait(page, link);

  const popupPage = await popupPromise;
  const legalPage = popupPage || page;

  await waitForUi(legalPage);
  await expect(legalPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible();
  await expect(legalPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚÑáéíóúñ]{20,}/);
  await captureCheckpoint(legalPage, testInfo, screenshotName);

  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close().catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
  } else if (finalUrl !== currentUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(currentUrl, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.setTimeout(240_000);

  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL;

  const report = Object.fromEntries(REPORT_KEYS.map((key) => [key, "FAIL"]));
  const failures = [];
  const evidence = {};

  await runStep(report, failures, "Login", async () => {
    if (!loginUrl) {
      throw new Error(
        "Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) to open the current environment login page."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginButton = await findVisibleElement(
      page,
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const googlePage = await popupPromise;

    if (googlePage) {
      await waitForUi(googlePage);
      const accountOption = await tryFindVisibleElement(
        googlePage,
        new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
        8_000
      );

      if (accountOption) {
        await clickAndWait(googlePage, accountOption);
      }

      await googlePage.waitForEvent("close", { timeout: 20_000 }).catch(() => {});
      await page.bringToFront();
    } else {
      const accountOption = await tryFindVisibleElement(
        page,
        new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
        5_000
      );
      if (accountOption) {
        await clickAndWait(page, accountOption);
      }
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 30_000 });
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded");
  });

  await runStep(report, failures, "Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible();

    const negocioOption = await findVisibleElement(page, /negocio/i);
    await clickAndWait(page, negocioOption);

    const miNegocioOption = await findVisibleElement(page, /mi negocio/i);
    await clickAndWait(page, miNegocioOption);

    await expect((await findVisibleElement(page, /agregar negocio/i))).toBeVisible();
    await expect((await findVisibleElement(page, /administrar negocios/i))).toBeVisible();
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded");
  });

  await runStep(report, failures, "Agregar Negocio modal", async () => {
    const agregarNegocioOption = await findVisibleElement(page, /^agregar negocio$/i);
    await clickAndWait(page, agregarNegocioOption);

    const modalTitle = page.getByRole("heading", { name: /crear nuevo negocio/i }).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");

    await page.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }));
    await expect(modalTitle).not.toBeVisible({ timeout: 10_000 });
  });

  await runStep(report, failures, "Administrar Negocios view", async () => {
    let administrarNegociosOption = await tryFindVisibleElement(page, /administrar negocios/i, 2_500);

    if (!administrarNegociosOption) {
      const miNegocioOption = await findVisibleElement(page, /mi negocio/i);
      await clickAndWait(page, miNegocioOption);
      administrarNegociosOption = await findVisibleElement(page, /administrar negocios/i);
    }

    await clickAndWait(page, administrarNegociosOption);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();
    await captureCheckpoint(page, testInfo, "04-administrar-negocios", true);
  });

  await runStep(report, failures, "Información General", async () => {
    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i").first()).toBeVisible();
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const possibleName = page.locator("h1, h2, h3, p, span").filter({
      hasText: /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/,
    });
    await expect(possibleName.first()).toBeVisible();
  });

  await runStep(report, failures, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep(report, failures, "Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
  });

  await runStep(report, failures, "Términos y Condiciones", async () => {
    evidence.termsUrl = await validateLegalLink({
      page,
      linkPattern: /t[ée]rminos y condiciones/i,
      headingPattern: /t[ée]rminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones",
      testInfo,
    });
  });

  await runStep(report, failures, "Política de Privacidad", async () => {
    evidence.privacyUrl = await validateLegalLink({
      page,
      linkPattern: /pol[ií]tica de privacidad/i,
      headingPattern: /pol[ií]tica de privacidad/i,
      screenshotName: "06-politica-de-privacidad",
      testInfo,
    });
  });

  const finalReport = {
    ...report,
    termsFinalUrl: evidence.termsUrl || "N/A",
    privacyFinalUrl: evidence.privacyUrl || "N/A",
  };

  const reportPath = testInfo.outputPath("final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2));
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log(`FINAL_REPORT=${JSON.stringify(finalReport)}`);

  if (failures.length > 0) {
    throw new Error(`One or more workflow validations failed:\n- ${failures.join("\n- ")}`);
  }
});
