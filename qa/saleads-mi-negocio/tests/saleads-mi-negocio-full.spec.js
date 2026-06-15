const { test, expect } = require("@playwright/test");

const DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function firstVisible(locatorCandidates, timeoutMs = 1_500) {
  for (const candidate of locatorCandidates) {
    const locator = candidate.first();
    const visible = await locator.isVisible({ timeout: timeoutMs }).catch(() => false);
    if (visible) {
      return locator;
    }
  }
  return null;
}

async function findClickableByVisibleText(page, text) {
  const textRegex = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
  const containsRegex = new RegExp(escapeRegex(text), "i");

  const locator = await firstVisible(
    [
      page.getByRole("button", { name: textRegex }),
      page.getByRole("link", { name: textRegex }),
      page.getByRole("menuitem", { name: textRegex }),
      page.getByRole("tab", { name: textRegex }),
      page.getByText(textRegex),
      page.getByText(containsRegex),
    ],
    2_500,
  );

  if (!locator) {
    throw new Error(`Could not find visible clickable element with text "${text}".`);
  }

  return locator;
}

async function clickByVisibleText(page, text) {
  const locator = await findClickableByVisibleText(page, text);
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function ensureMiNegocioExpanded(page) {
  const agregar = page.getByText(/^Agregar Negocio$/i).first();
  const administrar = page.getByText(/^Administrar Negocios$/i).first();

  const alreadyExpanded = (await agregar.isVisible({ timeout: 1_500 }).catch(() => false))
    && (await administrar.isVisible({ timeout: 1_500 }).catch(() => false));

  if (!alreadyExpanded) {
    await clickByVisibleText(page, "Mi Negocio");
  }

  await expect(agregar).toBeVisible({ timeout: 20_000 });
  await expect(administrar).toBeVisible({ timeout: 20_000 });
}

async function captureCheckpoint(testInfo, page, name, screenshots, fullPage = false) {
  const fileName = `${name}.png`;
  const filePath = testInfo.outputPath(fileName);
  await page.screenshot({ path: filePath, fullPage });
  screenshots.push(filePath);
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const notes = {};
  const screenshots = [];
  const urls = {};
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const googleAccountEmail = process.env.GOOGLE_ACCOUNT_EMAIL || DEFAULT_ACCOUNT_EMAIL;

  async function runValidation(field, action) {
    try {
      await action();
      results[field] = "PASS";
    } catch (error) {
      results[field] = "FAIL";
      notes[field] = error instanceof Error ? error.message : String(error);
    }
  }

  await runValidation("Login", async () => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "SALEADS_LOGIN_URL is required when test starts on a blank page. The test never hardcodes domains.",
      );
    }

    const loginButton = await firstVisible([
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|inicia sesi[oó]n con google|continuar con google|google/i,
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|inicia sesi[oó]n con google|continuar con google|google/i,
      }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
    ], 8_000);

    if (!loginButton) {
      throw new Error("Google login button was not visible.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await waitForUi(popup);
      const accountLocator = popup.getByText(googleAccountEmail, { exact: true });
      if (await accountLocator.isVisible({ timeout: 10_000 }).catch(() => false)) {
        await accountLocator.click();
        await waitForUi(popup);
      }
      await page.bringToFront();
    } else {
      const accountLocator = page.getByText(googleAccountEmail, { exact: true });
      if (await accountLocator.isVisible({ timeout: 8_000 }).catch(() => false)) {
        await accountLocator.click();
        await waitForUi(page);
      }
    }

    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 30_000 });
    const sidebar = await firstVisible(
      [page.getByRole("navigation"), page.locator("aside"), page.locator('[class*="sidebar"]')],
      10_000,
    );
    if (!sidebar) {
      throw new Error("Left sidebar navigation was not visible after login.");
    }

    await captureCheckpoint(testInfo, page, "01-dashboard-loaded", screenshots);
  });

  await runValidation("Mi Negocio menu", async () => {
    await ensureMiNegocioExpanded(page);
    await captureCheckpoint(testInfo, page, "02-mi-negocio-expanded", screenshots);
  });

  await runValidation("Agregar Negocio modal", async () => {
    await ensureMiNegocioExpanded(page);
    await clickByVisibleText(page, "Agregar Negocio");

    const modalTitle = page.getByText(/^Crear Nuevo Negocio$/i).first();
    await expect(modalTitle).toBeVisible({ timeout: 20_000 });
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

    await captureCheckpoint(testInfo, page, "03-agregar-negocio-modal", screenshots);

    const nameInput = page.getByLabel(/Nombre del Negocio/i).first();
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, "Cancelar");
  });

  await runValidation("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);
    await clickByVisibleText(page, "Administrar Negocios");

    await expect(page.getByText(/^Información General$/i).first()).toBeVisible({ timeout: 25_000 });
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
    await expect(page.getByText(/^Sección Legal$/i).first()).toBeVisible();

    await captureCheckpoint(testInfo, page, "04-administrar-negocios-view", screenshots, true);
  });

  await runValidation("Información General", async () => {
    await expect(page.getByText(/^Información General$/i).first()).toBeVisible({ timeout: 20_000 });

    const emailLocator = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
    await expect(emailLocator).toBeVisible();

    const infoText = (await page.locator("body").innerText()).replace(/\s+/g, " ");
    const hasLikelyName = /[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+ [A-ZÁÉÍÓÚÑ][a-záéíóúñ]+/.test(infoText);
    if (!hasLikelyName) {
      throw new Error("Could not detect a visible user name pattern in Información General.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();

    const businessList = await firstVisible(
      [
        page.locator('[role="list"]').first(),
        page.locator("ul").first(),
        page.locator("table").first(),
        page.getByText(/Negocio/i).first(),
      ],
      5_000,
    );
    if (!businessList) {
      throw new Error("Business list container was not visible.");
    }
  });

  async function validateLegalLink(linkText, expectedHeading, screenshotName) {
    const appUrlBefore = page.url();
    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);

    await clickByVisibleText(page, linkText);
    const popup = await popupPromise;
    const legalPage = popup || page;
    await waitForUi(legalPage);

    const heading = await firstVisible(
      [
        legalPage.getByRole("heading", { name: expectedHeading }),
        legalPage.getByText(expectedHeading),
      ],
      20_000,
    );
    if (!heading) {
      throw new Error(`Heading "${expectedHeading}" was not visible.`);
    }

    const bodyText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
    if (bodyText.length < 150) {
      throw new Error("Legal content text appears too short or not loaded.");
    }

    await captureCheckpoint(testInfo, legalPage, screenshotName, screenshots, true);

    const finalUrl = legalPage.url();
    urls[linkText] = finalUrl;

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
      return;
    }

    if (page.url() !== appUrlBefore) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  }

  await runValidation("Términos y Condiciones", async () => {
    await validateLegalLink(
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      "08-terminos-y-condiciones",
    );
  });

  await runValidation("Política de Privacidad", async () => {
    await validateLegalLink("Política de Privacidad", /Política de Privacidad/i, "09-politica-privacidad");
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate Mi Negocio module workflow.",
    results,
    notes,
    evidence: {
      screenshots,
      urls,
    },
  };

  await testInfo.attach("final-report.json", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json",
  });

  // Keep an explicit console artifact for CI logs.
  console.log(`Final report:\n${JSON.stringify(finalReport, null, 2)}`);

  const failed = Object.entries(results).filter(([, status]) => status !== "PASS");
  expect(
    failed,
    `One or more validation steps failed.\n${JSON.stringify(finalReport, null, 2)}`,
  ).toEqual([]);
});
