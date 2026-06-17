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

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, { status: "NOT_RUN", details: "" }]));
}

function markPass(report, field, details = "") {
  report[field] = { status: "PASS", details };
}

function markFail(report, field, error) {
  const message = error instanceof Error ? error.message : String(error);
  report[field] = { status: "FAIL", details: message };
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function isVisible(locator, timeout = 1500) {
  try {
    return await locator.first().isVisible({ timeout });
  } catch {
    return false;
  }
}

async function firstVisible(candidates) {
  for (const locator of candidates) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }
  return null;
}

async function resolveByVisibleText(page, textRegex) {
  return firstVisible([
    page.getByRole("button", { name: textRegex }),
    page.getByRole("link", { name: textRegex }),
    page.getByRole("menuitem", { name: textRegex }),
    page.getByText(textRegex)
  ]);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible({ timeout: 15000 });
  await locator.click();
  await waitForUiLoad(page);
}

async function maybeSelectGoogleAccount(authPage) {
  const accountLocator = authPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")).first();
  if (await isVisible(accountLocator, 5000)) {
    await accountLocator.click();
    await waitForUiLoad(authPage);
  }
}

async function ensureMiNegocioExpanded(page) {
  const agregarLocator = page.getByText(/agregar negocio/i).first();
  const administrarLocator = page.getByText(/administrar negocios/i).first();

  if ((await isVisible(agregarLocator)) && (await isVisible(administrarLocator))) {
    return;
  }

  const negocioMenu = await resolveByVisibleText(page, /mi negocio|negocio/i);
  if (!negocioMenu) {
    throw new Error("Could not find 'Mi Negocio' or 'Negocio' option in sidebar.");
  }

  await clickAndWait(page, negocioMenu);
  await expect(agregarLocator).toBeVisible({ timeout: 15000 });
  await expect(administrarLocator).toBeVisible({ timeout: 15000 });
}

async function validateLegalPageAndReturn(page, linkRegex, headingRegex, screenshotPath) {
  const legalLink = await resolveByVisibleText(page, linkRegex);
  if (!legalLink) {
    throw new Error(`Could not find legal link for ${linkRegex}.`);
  }

  const popupPromise = page.waitForEvent("popup", { timeout: 4000 }).catch(() => null);

  await legalLink.click();
  const popup = await popupPromise;

  let targetPage = page;
  let openedPopup = false;

  if (popup) {
    targetPage = popup;
    openedPopup = true;
    await waitForUiLoad(targetPage);
  } else {
    await waitForUiLoad(page);
  }

  const headingLocator = await firstVisible([
    targetPage.getByRole("heading", { name: headingRegex }),
    targetPage.getByText(headingRegex)
  ]);

  if (!headingLocator) {
    throw new Error(`Heading ${headingRegex} not visible on legal page.`);
  }

  await expect
    .poll(async () => targetPage.locator("p, li, article, section").count(), { timeout: 15000 })
    .toBeGreaterThan(0);

  await targetPage.screenshot({ path: screenshotPath, fullPage: true });
  const finalUrl = targetPage.url();

  if (openedPopup) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const startUrl = process.env.SALEADS_START_URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUiLoad(page);

  if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_START_URL to the current environment login page, or provide a preloaded login page in your runner."
    );
  }

  try {
    const loginButton = await resolveByVisibleText(
      page,
      /sign in with google|continue with google|iniciar sesi[oó]n con google|google/i
    );
    if (!loginButton) {
      throw new Error("Google sign-in button was not found.");
    }

    const googlePopupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
    await loginButton.click();

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await waitForUiLoad(googlePopup);
      await maybeSelectGoogleAccount(googlePopup);
      await googlePopup.waitForEvent("close", { timeout: 120000 }).catch(() => {});
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await waitForUiLoad(page);
      await maybeSelectGoogleAccount(page);
      await waitForUiLoad(page);
    }

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/negocio/i)
    ]);
    if (!sidebar) {
      throw new Error("Main application or left sidebar was not detected after login.");
    }

    await page.screenshot({ path: testInfo.outputPath("01-dashboard-loaded.png"), fullPage: true });
    markPass(report, "Login");
  } catch (error) {
    markFail(report, "Login", error);
  }

  try {
    await ensureMiNegocioExpanded(page);
    await page.screenshot({ path: testInfo.outputPath("02-mi-negocio-expanded.png"), fullPage: true });
    markPass(report, "Mi Negocio menu");
  } catch (error) {
    markFail(report, "Mi Negocio menu", error);
  }

  try {
    const agregarNegocioOption = await resolveByVisibleText(page, /agregar negocio/i);
    if (!agregarNegocioOption) {
      throw new Error("'Agregar Negocio' option is not visible.");
    }

    await clickAndWait(page, agregarNegocioOption);

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/nombre del negocio/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 15000 });

    await page.screenshot({ path: testInfo.outputPath("03-agregar-negocio-modal.png"), fullPage: true });

    const nombreInput = await firstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").filter({ hasText: /nombre/i })
    ]);
    if (nombreInput) {
      await nombreInput.click();
      await nombreInput.fill("Negocio Prueba Automatizacion");
      await waitForUiLoad(page);
    }

    const cancelarButton = page.getByRole("button", { name: /cancelar/i }).first();
    await clickAndWait(page, cancelarButton);
    markPass(report, "Agregar Negocio modal");
  } catch (error) {
    markFail(report, "Agregar Negocio modal", error);
  }

  try {
    await ensureMiNegocioExpanded(page);
    const administrarOption = await resolveByVisibleText(page, /administrar negocios/i);
    if (!administrarOption) {
      throw new Error("'Administrar Negocios' option is not visible.");
    }

    await clickAndWait(page, administrarOption);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible({ timeout: 15000 });

    await page.screenshot({ path: testInfo.outputPath("04-administrar-negocios-full.png"), fullPage: true });
    markPass(report, "Administrar Negocios view");
  } catch (error) {
    markFail(report, "Administrar Negocios view", error);
  }

  try {
    const emailText = page.getByText(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i).first();
    await expect(emailText).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible({ timeout: 15000 });

    const possibleName = await firstVisible([
      page.getByText(/nombre/i),
      page.locator("h1, h2, h3").filter({ hasNotText: /informaci[oó]n general/i }).first()
    ]);
    if (!possibleName) {
      throw new Error("User name was not clearly visible in Informacion General.");
    }

    markPass(report, "Información General");
  } catch (error) {
    markFail(report, "Información General", error);
  }

  try {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/estado activo/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible({ timeout: 15000 });
    markPass(report, "Detalles de la Cuenta");
  } catch (error) {
    markFail(report, "Detalles de la Cuenta", error);
  }

  try {
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 15000 });
    markPass(report, "Tus Negocios");
  } catch (error) {
    markFail(report, "Tus Negocios", error);
  }

  try {
    const terminosUrl = await validateLegalPageAndReturn(
      page,
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      testInfo.outputPath("08-terminos-y-condiciones.png")
    );
    markPass(report, "Términos y Condiciones", `URL: ${terminosUrl}`);
  } catch (error) {
    markFail(report, "Términos y Condiciones", error);
  }

  try {
    const privacidadUrl = await validateLegalPageAndReturn(
      page,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      testInfo.outputPath("09-politica-privacidad.png")
    );
    markPass(report, "Política de Privacidad", `URL: ${privacidadUrl}`);
  } catch (error) {
    markFail(report, "Política de Privacidad", error);
  }

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedFields = Object.entries(report)
    .filter(([, result]) => result.status !== "PASS")
    .map(([field, result]) => `${field}: ${result.status}${result.details ? ` (${result.details})` : ""}`);

  expect(
    failedFields,
    failedFields.length ? `Validation failures:\n- ${failedFields.join("\n- ")}` : "All validations passed."
  ).toEqual([]);
});
