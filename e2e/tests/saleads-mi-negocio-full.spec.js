const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const SCREENSHOT_DIR = path.resolve(__dirname, "..", "artifacts", "saleads-mi-negocio-full-test");
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function slugify(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

async function findVisibleLocator(page, candidateLocators, description, timeoutMs = 30000) {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const locator of candidateLocators) {
      const first = locator.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Could not find visible element: ${description}`);
}

async function clickAndWaitForUi(page, locator) {
  await expect(locator).toBeVisible({ timeout: 30000 });
  await locator.click();
  await page.waitForLoadState("domcontentloaded", { timeout: 60000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
}

async function takeCheckpoint(page, testInfo, label, fullPage = false) {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });

  const fileName = `${new Date().toISOString().replace(/[:.]/g, "-")}-${slugify(label)}.png`;
  const outputPath = path.join(SCREENSHOT_DIR, fileName);

  await page.screenshot({ path: outputPath, fullPage });
  await testInfo.attach(`checkpoint-${label}`, { path: outputPath, contentType: "image/png" });
}

async function validateLegalLink({
  page,
  context,
  testInfo,
  linkTextRegex,
  headingRegex,
  screenshotLabel,
  attachmentName
}) {
  const appUrlBeforeNavigation = page.url();
  const possibleNewTab = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  const legalLink = await findVisibleLocator(
    page,
    [page.getByRole("link", { name: linkTextRegex }), page.getByText(linkTextRegex)],
    `Legal link ${linkTextRegex}`
  );

  await legalLink.click();

  let legalPage = await possibleNewTab;
  if (!legalPage) {
    legalPage = page;
  }

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 60000 }).catch(() => {});
  await legalPage.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});

  const heading = await findVisibleLocator(
    legalPage,
    [legalPage.getByRole("heading", { name: headingRegex }), legalPage.getByText(headingRegex)],
    `Legal heading ${headingRegex}`,
    60000
  );
  await expect(heading).toBeVisible();

  const bodyText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(bodyText.length, "Expected legal content text to be visible").toBeGreaterThan(200);

  await takeCheckpoint(legalPage, testInfo, screenshotLabel);

  const finalUrl = legalPage.url();
  await testInfo.attach(attachmentName, { body: Buffer.from(finalUrl, "utf-8"), contentType: "text/plain" });

  if (legalPage === page) {
    await page.goto(appUrlBeforeNavigation, { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  } else {
    await legalPage.close();
    await page.bringToFront();
    await page.waitForLoadState("domcontentloaded", { timeout: 60000 }).catch(() => {});
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const finalReport = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
  const failures = [];

  async function runValidation(name, fn) {
    try {
      await fn();
      finalReport[name] = "PASS";
    } catch (error) {
      const details = error instanceof Error ? error.message : String(error);
      failures.push(`${name}: ${details}`);
      finalReport[name] = "FAIL";
    }
  }

  const saleadsUrl = process.env.SALEADS_URL || process.env.BASE_URL;
  if (saleadsUrl) {
    await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
  }

  if (page.url().startsWith("about:blank")) {
    throw new Error(
      "No SaleADS login page loaded. Provide SALEADS_URL (or BASE_URL) so this test can run in any environment."
    );
  }

  await runValidation("Login", async () => {
    const googleSignIn = await findVisibleLocator(
      page,
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|google/i)
      ],
      "Google sign-in entry point",
      60000
    );

    const possibleGoogleTab = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWaitForUi(page, googleSignIn);

    let authPage = await possibleGoogleTab;
    if (!authPage) {
      authPage = page;
    }

    await authPage.waitForLoadState("domcontentloaded", { timeout: 60000 }).catch(() => {});
    const accountChoice = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    if (await accountChoice.isVisible().catch(() => false)) {
      await clickAndWaitForUi(authPage, accountChoice);
    }

    if (authPage !== page) {
      await Promise.race([
        authPage.waitForEvent("close", { timeout: 60000 }).catch(() => {}),
        page.waitForLoadState("domcontentloaded", { timeout: 60000 }).catch(() => {})
      ]);
      await page.bringToFront();
    }

    await page.waitForLoadState("domcontentloaded", { timeout: 60000 }).catch(() => {});
    await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});

    const sidebar = await findVisibleLocator(
      page,
      [page.locator("aside"), page.getByRole("navigation"), page.locator('[class*="sidebar"]')],
      "left sidebar navigation",
      60000
    );
    await expect(sidebar).toBeVisible();
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 60000 });

    await takeCheckpoint(page, testInfo, "dashboard-loaded");
  });

  await runValidation("Mi Negocio menu", async () => {
    const negocioSection = await findVisibleLocator(
      page,
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i)
      ],
      "Negocio section"
    );
    await clickAndWaitForUi(page, negocioSection);

    const miNegocioOption = await findVisibleLocator(
      page,
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      "Mi Negocio option"
    );
    await clickAndWaitForUi(page, miNegocioOption);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: 30000 });

    await takeCheckpoint(page, testInfo, "mi-negocio-expanded-menu");
  });

  await runValidation("Agregar Negocio modal", async () => {
    const agregarNegocio = await findVisibleLocator(
      page,
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      "Agregar Negocio menu option"
    );
    await clickAndWaitForUi(page, agregarNegocio);

    const modalTitle = await findVisibleLocator(
      page,
      [page.getByRole("heading", { name: /Crear Nuevo Negocio/i }), page.getByText(/Crear Nuevo Negocio/i)],
      "Crear Nuevo Negocio modal title",
      30000
    );
    await expect(modalTitle).toBeVisible();

    const nameInput = await findVisibleLocator(
      page,
      [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)],
      "Nombre del Negocio input"
    );
    await expect(nameInput).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible({ timeout: 30000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: 30000 });

    await takeCheckpoint(page, testInfo, "crear-nuevo-negocio-modal");

    await nameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWaitForUi(page, page.getByRole("button", { name: /^Cancelar$/i }));
  });

  await runValidation("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocioOption = await findVisibleLocator(
        page,
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ],
        "Mi Negocio option"
      );
      await clickAndWaitForUi(page, miNegocioOption);
    }

    const administrarNegocios = await findVisibleLocator(
      page,
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ],
      "Administrar Negocios option"
    );
    await clickAndWaitForUi(page, administrarNegocios);

    await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible({ timeout: 60000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 60000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 60000 });
    await expect(page.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible({ timeout: 60000 });

    await takeCheckpoint(page, testInfo, "administrar-negocios-cuenta", true);
  });

  await runValidation("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/@/).first()).toBeVisible({ timeout: 30000 });

    const nonEmptyName = await page
      .locator("section, div")
      .filter({ hasText: /Informacion General|Información General/i })
      .first()
      .innerText();
    expect(nonEmptyName.trim().length).toBeGreaterThan(20);
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 30000 });
  });

  await runValidation("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 30000 });
  });

  await runValidation("Términos y Condiciones", async () => {
    const termsUrl = await validateLegalLink({
      page,
      context,
      testInfo,
      linkTextRegex: /T[eé]rminos y Condiciones/i,
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotLabel: "terminos-y-condiciones",
      attachmentName: "terminos-y-condiciones-final-url.txt"
    });
    expect(termsUrl.length).toBeGreaterThan(0);
  });

  await runValidation("Política de Privacidad", async () => {
    const privacyUrl = await validateLegalLink({
      page,
      context,
      testInfo,
      linkTextRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotLabel: "politica-de-privacidad",
      attachmentName: "politica-de-privacidad-final-url.txt"
    });
    expect(privacyUrl.length).toBeGreaterThan(0);
  });

  const reportPayload = {
    testName: "saleads_mi_negocio_full_test",
    executedAt: new Date().toISOString(),
    results: finalReport,
    failures
  };

  await testInfo.attach("final-workflow-report.json", {
    body: Buffer.from(JSON.stringify(reportPayload, null, 2), "utf-8"),
    contentType: "application/json"
  });

  console.log("Final report for saleads_mi_negocio_full_test:");
  console.log(JSON.stringify(reportPayload, null, 2));

  expect(failures, `Workflow validations failed:\n${failures.join("\n")}`).toEqual([]);
});
