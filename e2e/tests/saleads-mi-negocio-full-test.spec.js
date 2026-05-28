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
  "Política de Privacidad",
];

const EMAIL_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const NEW_BUSINESS_NAME = "Negocio Prueba Automatización";

function buildInitialReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {});
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function findVisibleByText(page, text) {
  const safeRegex = new RegExp(`^${escapeRegExp(text)}$`, "i");
  const candidates = [
    page.getByRole("button", { name: safeRegex }),
    page.getByRole("link", { name: safeRegex }),
    page.getByText(safeRegex),
    page.locator(`text="${text}"`),
  ];

  for (const candidate of candidates) {
    const locator = candidate.first();
    if (await locator.isVisible({ timeout: 2000 }).catch(() => false)) {
      return locator;
    }
  }

  return null;
}

async function clickByVisibleText(page, text) {
  const locator = await findVisibleByText(page, text);
  if (!locator) {
    throw new Error(`No visible element found with text "${text}"`);
  }

  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const fileName = `${Date.now()}-${name.toLowerCase().replace(/[^a-z0-9]+/g, "-")}.png`;
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, { path: screenshotPath, contentType: "image/png" });
}

async function assertHeadingVisible(page, headingText) {
  const headingRegex = new RegExp(escapeRegExp(headingText), "i");
  const heading = page.getByRole("heading", { name: headingRegex }).first();
  if (await heading.isVisible({ timeout: 5000 }).catch(() => false)) {
    await expect(heading).toBeVisible();
    return;
  }

  await expect(page.getByText(headingRegex).first()).toBeVisible();
}

test("SaleADS Mi Negocio full workflow", async ({ page, context }, testInfo) => {
  test.setTimeout(240000);

  const finalReport = buildInitialReport();
  const finalUrls = {};
  const failures = [];

  const runStep = async (stepName, stepFn) => {
    try {
      await stepFn();
      finalReport[stepName] = "PASS";
    } catch (error) {
      finalReport[stepName] = "FAIL";
      failures.push(`${stepName}: ${error.message}`);
    }
  };

  await runStep("Login", async () => {
    const configuredUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || process.env.BASE_URL;

    if (configuredUrl) {
      await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) to run in any environment without hardcoding."
      );
    }

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

    const googleButtonCandidates = [
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
    ];

    let clicked = false;
    for (const candidate of googleButtonCandidates) {
      const locator = candidate.first();
      if (await locator.isVisible({ timeout: 2000 }).catch(() => false)) {
        await locator.click();
        clicked = true;
        break;
      }
    }

    if (!clicked) {
      throw new Error("Google login button not found");
    }

    await waitForUi(page);
    const popup = await popupPromise;
    const authPage = popup || page;
    await waitForUi(authPage);

    const accountSelector = authPage.getByText(EMAIL_ACCOUNT, { exact: true }).first();
    if (await accountSelector.isVisible({ timeout: 5000 }).catch(() => false)) {
      await accountSelector.click();
      await waitForUi(authPage);
    }

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    }

    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 30000 });
    await captureCheckpoint(page, testInfo, "step-1-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 20000 });
    await clickByVisibleText(page, "Mi Negocio");
    await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();
    await captureCheckpoint(page, testInfo, "step-2-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, "Agregar Negocio");
    await assertHeadingVisible(page, "Crear Nuevo Negocio");
    await expect(page.getByText("Nombre del Negocio", { exact: true })).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Crear Negocio" })).toBeVisible();
    await captureCheckpoint(page, testInfo, "step-3-agregar-negocio-modal");

    const input = page.getByLabel("Nombre del Negocio").first();
    if (await input.isVisible({ timeout: 2000 }).catch(() => false)) {
      await input.fill(NEW_BUSINESS_NAME);
    } else {
      const fallbackInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
      await expect(fallbackInput).toBeVisible();
      await fallbackInput.fill(NEW_BUSINESS_NAME);
    }

    await clickByVisibleText(page, "Cancelar");
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page
      .getByText("Administrar Negocios", { exact: true })
      .first()
      .isVisible({ timeout: 1500 })
      .catch(() => false);
    if (!administrarVisible) {
      await clickByVisibleText(page, "Mi Negocio");
    }

    await clickByVisibleText(page, "Administrar Negocios");

    await assertHeadingVisible(page, "Información General");
    await assertHeadingVisible(page, "Detalles de la Cuenta");
    await assertHeadingVisible(page, "Tus Negocios");
    await assertHeadingVisible(page, "Sección Legal");
    await captureCheckpoint(page, testInfo, "step-4-administrar-negocios", true);
  });

  await runStep("Información General", async () => {
    const section = page.locator("section, div").filter({ hasText: /Información General/i }).first();
    await expect(section).toBeVisible();

    const text = await section.innerText();
    const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    if (!emailRegex.test(text)) {
      throw new Error("User email is not visible in Información General");
    }

    const nonLabelLines = text
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .filter((line) => !/informaci[oó]n general|business plan|cambiar plan/i.test(line))
      .filter((line) => !emailRegex.test(line));

    if (nonLabelLines.length === 0) {
      throw new Error("User name is not detectable in Información General");
    }

    await expect(section.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(section.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const section = page.locator("section, div").filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(section).toBeVisible();
    await expect(section.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(section.getByText(/Estado activo/i)).toBeVisible();
    await expect(section.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const section = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(section).toBeVisible();
    await expect(section.getByText("Agregar Negocio", { exact: true })).toBeVisible();
    await expect(section.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();

    const itemCount = await section.locator("li, [role='listitem'], table tbody tr, article").count();
    const text = await section.innerText();
    if (itemCount === 0 && !/negocio/i.test(text)) {
      throw new Error("Business list is not visible in Tus Negocios");
    }
  });

  const validateLegalPage = async (linkText, headingText, reportField, screenshotName) => {
    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickByVisibleText(page, linkText);

    const popup = await popupPromise;
    const legalPage = popup || page;
    await waitForUi(legalPage);
    await assertHeadingVisible(legalPage, headingText);

    const legalBodyText = await legalPage.locator("body").innerText();
    if (legalBodyText.trim().length < 200) {
      throw new Error(`${headingText} content seems too short`);
    }

    finalUrls[reportField] = legalPage.url();
    await captureCheckpoint(legalPage, testInfo, screenshotName, true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
      return;
    }

    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  };

  await runStep("Términos y Condiciones", async () => {
    await validateLegalPage(
      "Términos y Condiciones",
      "Términos y Condiciones",
      "Términos y Condiciones",
      "step-8-terminos-y-condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalPage(
      "Política de Privacidad",
      "Política de Privacidad",
      "Política de Privacidad",
      "step-9-politica-de-privacidad"
    );
  });

  const reportPayload = {
    name: "saleads_mi_negocio_full_test",
    results: finalReport,
    finalUrls,
    failures,
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(reportPayload, null, 2), "utf-8"),
    contentType: "application/json",
  });

  if (failures.length > 0) {
    throw new Error(`One or more validations failed:\n- ${failures.join("\n- ")}`);
  }
});
