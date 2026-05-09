const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || "";
const ARTIFACTS_DIR =
  process.env.SALEADS_ARTIFACTS_DIR || path.join(__dirname, "..", "artifacts");

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

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
}

async function waitForUiToStabilize(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToStabilize(page);
}

async function captureScreenshot(page, name, fullPage = false) {
  const filePath = path.join(ARTIFACTS_DIR, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function expectAnyVisible(locators, description) {
  for (const locator of locators) {
    try {
      await expect(locator.first()).toBeVisible({ timeout: 5000 });
      return locator.first();
    } catch (error) {
      // try next locator candidate
    }
  }

  throw new Error(`Element not visible: ${description}`);
}

async function clickFirstVisible(page, locators, description) {
  const locator = await expectAnyVisible(locators, description);
  await clickAndWait(locator, page);
  return locator;
}

async function selectGoogleAccountIfVisible(targetPage) {
  const accountLocator = targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountLocator.isVisible().catch(() => false)) {
    await accountLocator.click();
    await waitForUiToStabilize(targetPage);
  }
}

async function openLegalLink({
  page,
  context,
  linkText,
  headingRegex,
  screenshotName
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const originalUrl = page.url();

  await clickFirstVisible(
    page,
    [
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByRole("button", { name: new RegExp(linkText, "i") }),
      page.getByText(new RegExp(linkText, "i"))
    ],
    linkText
  );

  const popup = await popupPromise;
  const legalPage = popup || page;
  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});

  await expectAnyVisible(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex)
    ],
    `Heading for ${linkText}`
  );

  await expect(legalPage.locator("body")).toContainText(
    /(términos|condiciones|privacidad|datos|uso|legal)/i
  );

  await captureScreenshot(legalPage, screenshotName);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (finalUrl !== originalUrl) {
    await page.goBack().catch(() => {});
    await waitForUiToStabilize(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  test.slow();
  await ensureArtifactsDir();

  const result = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  const evidence = {
    screenshots: {},
    finalUrls: {}
  };

  const runSection = async (reportField, fn) => {
    try {
      await fn();
      result[reportField] = "PASS";
    } catch (error) {
      result[reportField] = "FAIL";
      failures.push({
        section: reportField,
        message: error instanceof Error ? error.message : String(error)
      });
    }
  };

  if (!LOGIN_URL) {
    throw new Error(
      "SALEADS_LOGIN_URL (or SALEADS_BASE_URL) is required so the test can open the login page for the current environment."
    );
  }

  await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
  await waitForUiToStabilize(page);

  await runSection("Login", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /sign in with google|continuar con google|google/i }),
        page.getByText(/sign in with google|continuar con google|google/i)
      ],
      "Google login button"
    );

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfVisible(popup);
      await popup.waitForClose({ timeout: 120000 }).catch(() => {});
      await page.bringToFront();
      await waitForUiToStabilize(page);
    } else {
      await selectGoogleAccountIfVisible(page);
    }

    await expectAnyVisible(
      [
        page.getByRole("navigation"),
        page.getByText(/negocio/i),
        page.locator("aside")
      ],
      "Main app interface / left sidebar"
    );

    evidence.screenshots.dashboard = await captureScreenshot(page, "01-dashboard-loaded");
  });

  await runSection("Mi Negocio menu", async () => {
    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      "Mi Negocio menu option"
    );

    await expectAnyVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio option"
    );

    await expectAnyVisible(
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      "Administrar Negocios option"
    );

    evidence.screenshots.miNegocioMenu = await captureScreenshot(page, "02-mi-negocio-menu-expanded");
  });

  await runSection("Agregar Negocio modal", async () => {
    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio action"
    );

    await expectAnyVisible(
      [
        page.getByRole("heading", { name: /crear nuevo negocio/i }),
        page.getByText(/crear nuevo negocio/i)
      ],
      "Crear Nuevo Negocio modal title"
    );

    const businessNameInput = await expectAnyVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator("input[type='text']")
      ],
      "Nombre del Negocio input"
    );

    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expectAnyVisible(
      [page.getByRole("button", { name: /cancelar/i }), page.getByText(/cancelar/i)],
      "Cancelar button"
    );
    await expectAnyVisible(
      [page.getByRole("button", { name: /crear negocio/i }), page.getByText(/crear negocio/i)],
      "Crear Negocio button"
    );

    evidence.screenshots.agregarNegocioModal = await captureScreenshot(
      page,
      "03-agregar-negocio-modal"
    );

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickFirstVisible(
      page,
      [page.getByRole("button", { name: /cancelar/i }), page.getByText(/cancelar/i)],
      "Cancelar button"
    );
  });

  await runSection("Administrar Negocios view", async () => {
    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      "Mi Negocio (re-expand if needed)"
    );

    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      "Administrar Negocios option"
    );

    await expectAnyVisible(
      [
        page.getByRole("heading", { name: /información general/i }),
        page.getByText(/información general/i)
      ],
      "Información General section"
    );
    await expectAnyVisible(
      [
        page.getByRole("heading", { name: /detalles de la cuenta/i }),
        page.getByText(/detalles de la cuenta/i)
      ],
      "Detalles de la Cuenta section"
    );
    await expectAnyVisible(
      [page.getByRole("heading", { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      "Tus Negocios section"
    );
    await expectAnyVisible(
      [page.getByRole("heading", { name: /sección legal/i }), page.getByText(/sección legal/i)],
      "Sección Legal section"
    );

    evidence.screenshots.accountPage = await captureScreenshot(
      page,
      "04-administrar-negocios-page",
      true
    );
  });

  await runSection("Información General", async () => {
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expectAnyVisible(
      [page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)],
      "Cambiar Plan button"
    );
    await expect(page.locator("body")).toContainText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    await expectAnyVisible(
      [page.getByText(/nombre/i), page.getByText(/usuario/i)],
      "User name label or text"
    );
  });

  await runSection("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runSection("Tus Negocios", async () => {
    await expectAnyVisible(
      [page.getByRole("heading", { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      "Tus Negocios title"
    );
    await expectAnyVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio button in business list"
    );
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
  });

  await runSection("Términos y Condiciones", async () => {
    evidence.finalUrls.terminos = await openLegalLink({
      page,
      context,
      linkText: "Términos y Condiciones",
      headingRegex: /términos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones"
    });
  });

  await runSection("Política de Privacidad", async () => {
    evidence.finalUrls.politicaPrivacidad = await openLegalLink({
      page,
      context,
      linkText: "Política de Privacidad",
      headingRegex: /política de privacidad/i,
      screenshotName: "06-politica-de-privacidad"
    });
  });

  const reportOutput = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    accountEmailAttempted: GOOGLE_ACCOUNT_EMAIL,
    loginUrlUsed: LOGIN_URL,
    statusByField: result,
    failures,
    evidence
  };

  await fs.writeFile(
    path.join(ARTIFACTS_DIR, "saleads-mi-negocio-report.json"),
    JSON.stringify(reportOutput, null, 2),
    "utf8"
  );

  expect(failures, `Validation failures:\n${JSON.stringify(failures, null, 2)}`).toEqual([]);
});
