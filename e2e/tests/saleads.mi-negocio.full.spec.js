const fs = require("node:fs");
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

function slug(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function firstVisible(page, locators, timeout = 25_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    for (const candidate of locators) {
      const item = candidate.first();
      const isVisible = await item.isVisible().catch(() => false);
      if (isVisible) {
        return item;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error("No expected visible element was found.");
}

async function checkpoint(page, testInfo, label, fullPage = false) {
  const filePath = testInfo.outputPath(`${slug(label)}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(label, { path: filePath, contentType: "image/png" });
  return filePath;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const reasons = {};
  const evidence = {};
  const urls = {};

  async function runValidation(field, action) {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      reasons[field] = message;
      report[field] = "FAIL";
    }
  }

  const configuredLoginUrl = process.env.SALEADS_URL || process.env.SALEADS_LOGIN_URL;
  if (configuredLoginUrl) {
    await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Missing SALEADS_URL or SALEADS_LOGIN_URL. Set one of them to the current environment login page."
    );
  }

  await runValidation("Login", async () => {
    const loginButton = await firstVisible(page, [
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
      page.getByRole("button", { name: /continuar con google/i }),
      page.getByRole("button", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(page, loginButton);

    const popup = await popupPromise;
    const authPage = popup || page;
    await waitForUi(authPage);

    const accountSelector = authPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
    if (await accountSelector.isVisible().catch(() => false)) {
      await clickAndWait(authPage, accountSelector);
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 60_000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUi(page);

    const sidebar = await firstVisible(page, [
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("[class*='sidebar']"),
    ]);
    await expect(sidebar).toBeVisible();

    const negocioEntry = await firstVisible(page, [
      page.getByRole("button", { name: /negocio/i }),
      page.getByRole("link", { name: /negocio/i }),
      page.getByText(/^Negocio$/i),
    ]);
    await expect(negocioEntry).toBeVisible();

    evidence.loginDashboard = await checkpoint(page, testInfo, "dashboard-loaded", true);
  });

  await runValidation("Mi Negocio menu", async () => {
    const negocioEntry = await firstVisible(page, [
      page.getByRole("button", { name: /mi negocio|negocio/i }),
      page.getByRole("link", { name: /mi negocio|negocio/i }),
      page.getByText(/^Negocio$/i),
      page.getByText(/^Mi Negocio$/i),
    ]);
    await clickAndWait(page, negocioEntry);

    const agregarMenu = await firstVisible(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    const administrarMenu = await firstVisible(page, [
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);

    await expect(agregarMenu).toBeVisible();
    await expect(administrarMenu).toBeVisible();
    evidence.menuExpanded = await checkpoint(page, testInfo, "mi-negocio-menu-expanded");
  });

  await runValidation("Agregar Negocio modal", async () => {
    const addBusinessOption = await firstVisible(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await clickAndWait(page, addBusinessOption);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeVisible();
    const businessNameInput = page
      .getByLabel(/^Nombre del Negocio$/i)
      .or(page.getByPlaceholder(/^Nombre del Negocio$/i))
      .first();

    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    evidence.agregarModal = await checkpoint(page, testInfo, "agregar-negocio-modal");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }));
    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeHidden({ timeout: 10_000 });
  });

  await runValidation("Administrar Negocios view", async () => {
    const negocioEntry = await firstVisible(page, [
      page.getByRole("button", { name: /mi negocio|negocio/i }),
      page.getByRole("link", { name: /mi negocio|negocio/i }),
      page.getByText(/^Mi Negocio$/i),
      page.getByText(/^Negocio$/i),
    ]);
    await clickAndWait(page, negocioEntry);

    const administrarOption = await firstVisible(page, [
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    await clickAndWait(page, administrarOption);

    await expect(page.getByText(/^Informaci[oó]n General$/i)).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
    await expect(page.getByText(/^Secci[oó]n Legal$/i)).toBeVisible();

    evidence.accountPage = await checkpoint(page, testInfo, "administrar-negocios-page", true);
  });

  await runValidation("Información General", async () => {
    await expect(page.getByText(/^Informaci[oó]n General$/i)).toBeVisible();
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)).toBeVisible();
    await expect(page.getByText(/^BUSINESS PLAN$/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cambiar Plan$/i })).toBeVisible();

    // Name can vary by account and environment; validate that a non-empty profile title exists.
    const profileText = page.locator("h1, h2, h3, strong, [data-testid='user-name']").first();
    await expect(profileText).toBeVisible();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const listArea = page.locator("ul, table, [role='list'], [data-testid='business-list']").first();
    await expect(listArea).toBeVisible();
  });

  async function validateLegalLink(fieldName, linkText, headingRegex, evidenceName) {
    await runValidation(fieldName, async () => {
      const appUrlBefore = page.url();
      const popupPromise = page.context().waitForEvent("page", { timeout: 5_000 }).catch(() => null);
      const legalLink = await firstVisible(page, [
        page.getByRole("link", { name: linkText }),
        page.getByText(linkText, { exact: true }),
      ]);

      await clickAndWait(page, legalLink);
      const popup = await popupPromise;
      const legalPage = popup || page;

      await legalPage.waitForLoadState("domcontentloaded", { timeout: 30_000 });
      await legalPage.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});

      await expect(legalPage.getByRole("heading", { name: headingRegex })).toBeVisible();
      await expect(legalPage.locator("p, li").first()).toBeVisible();

      evidence[evidenceName] = await checkpoint(legalPage, testInfo, evidenceName, true);
      urls[fieldName] = legalPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
        await waitForUi(page);
      }
    });
  }

  await validateLegalLink(
    "Términos y Condiciones",
    "Términos y Condiciones",
    /^T[eé]rminos y Condiciones$/i,
    "terminos-y-condiciones"
  );

  await validateLegalLink(
    "Política de Privacidad",
    "Política de Privacidad",
    /^Pol[ií]tica de Privacidad$/i,
    "politica-de-privacidad"
  );

  const summary = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    reasons,
    urls,
    evidence,
  };

  const reportPath = testInfo.outputPath("mi-negocio-final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(summary, null, 2), "utf8");
  await testInfo.attach("mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  // Keep a concise summary in the execution log for quick review.
  // eslint-disable-next-line no-console
  console.table(report);

  const failed = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => `${field}: ${reasons[field] || "validation failed"}`);

  expect(
    failed,
    `Final Report (FAIL):\n${failed.join("\n")}\n\nReport JSON: ${reportPath}`
  ).toEqual([]);
});
