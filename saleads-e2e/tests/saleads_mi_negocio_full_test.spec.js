const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const START_URL =
  process.env.SALEADS_URL ||
  process.env.SALEADS_LOGIN_URL ||
  process.env.BASE_URL;

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

function initReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
}

async function findVisibleLocator(candidates, description) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    try {
      if (await locator.isVisible()) {
        return locator;
      }
    } catch (_error) {
      // continue trying candidates
    }
  }

  for (const candidate of candidates) {
    const locator = candidate.first();
    try {
      await locator.waitFor({ state: "visible", timeout: 8_000 });
      return locator;
    } catch (_error) {
      // continue trying candidates
    }
  }

  throw new Error(`Could not find visible element for: ${description}`);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiLoad(page);
}

async function captureScreenshot(page, testInfo, name, fullPage = false) {
  const filePath = testInfo.outputPath(name);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, { path: filePath, contentType: "image/png" });
}

async function attachText(testInfo, name, value) {
  const filePath = testInfo.outputPath(name);
  await fs.writeFile(filePath, value, "utf8");
  await testInfo.attach(name, { path: filePath, contentType: "text/plain" });
}

async function selectGoogleAccountIfVisible(googlePage, email) {
  await waitForUiLoad(googlePage);

  const accountLocator = await findVisibleLocator(
    [
      googlePage.getByText(email, { exact: false }),
      googlePage.getByRole("link", { name: new RegExp(email, "i") }),
      googlePage.getByRole("button", { name: new RegExp(email, "i") }),
      googlePage.locator(`[data-email="${email}"]`),
      googlePage.locator(`text=${email}`),
    ],
    `Google account ${email}`,
  );

  await accountLocator.click();
  await googlePage.waitForTimeout(1_000);
}

async function openLegalPageAndValidate({
  appPage,
  linkRegex,
  headingRegex,
  screenshotName,
  testInfo,
}) {
  const context = appPage.context();
  const link = await findVisibleLocator(
    [
      appPage.getByRole("link", { name: linkRegex }),
      appPage.getByRole("button", { name: linkRegex }),
      appPage.getByText(linkRegex),
    ],
    `legal link ${linkRegex}`,
  );

  const [newPage] = await Promise.all([
    context.waitForEvent("page", { timeout: 8_000 }).catch(() => null),
    link.click(),
  ]);

  const legalPage = newPage || appPage;
  await waitForUiLoad(legalPage);

  const heading = await findVisibleLocator(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex),
    ],
    `heading ${headingRegex}`,
  );
  await expect(heading).toBeVisible();

  const legalText = (await legalPage.locator("body").innerText()).trim();
  expect(legalText.length).toBeGreaterThan(120);

  await captureScreenshot(legalPage, testInfo, screenshotName, true);

  const finalUrl = legalPage.url();
  await attachText(
    testInfo,
    `${screenshotName.replace(".png", "")}-url.txt`,
    `Final URL: ${finalUrl}\n`,
  );

  if (newPage) {
    await newPage.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiLoad(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = initReport();
  const failures = [];
  const evidence = {};

  const runStep = async (fieldName, stepFn) => {
    try {
      await stepFn();
      report[fieldName] = "PASS";
    } catch (error) {
      report[fieldName] = "FAIL";
      failures.push(`${fieldName}: ${error.message}`);
    }
  };

  await runStep("Login", async () => {
    if (!START_URL) {
      throw new Error(
        "Missing SALEADS_URL (or SALEADS_LOGIN_URL / BASE_URL) environment variable.",
      );
    }

    await page.goto(START_URL, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);

    const loginTrigger = await findVisibleLocator(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesión con google|continuar con google|google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesión con google|continuar con google|google/i,
        }),
        page.getByText(
          /sign in with google|iniciar sesión con google|continuar con google/i,
        ),
      ],
      "login button",
    );

    const [popup] = await Promise.all([
      context.waitForEvent("page", { timeout: 8_000 }).catch(() => null),
      loginTrigger.click(),
    ]);

    await waitForUiLoad(page);

    if (popup) {
      await waitForUiLoad(popup);
      if (/google/i.test(popup.url()) || /accounts/i.test(popup.url())) {
        await selectGoogleAccountIfVisible(popup, GOOGLE_ACCOUNT_EMAIL).catch(() => {});
      }
      await popup.waitForTimeout(1_000);
    }

    const sidebar = await findVisibleLocator(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.getByText(/negocio|dashboard|inicio/i),
      ],
      "main app interface and sidebar",
    );
    await expect(sidebar).toBeVisible();

    await captureScreenshot(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioMenu = await findVisibleLocator(
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
        page.getByText(/^negocio$/i),
      ],
      "Mi Negocio menu entry",
    );
    await clickAndWait(negocioMenu, page);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();

    await captureScreenshot(page, testInfo, "02-mi-negocio-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusiness = await findVisibleLocator(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ],
      "Agregar Negocio action",
    );
    await clickAndWait(addBusiness, page);

    const modalTitle = await findVisibleLocator(
      [
        page.getByRole("heading", { name: /crear nuevo negocio/i }),
        page.getByText(/crear nuevo negocio/i),
      ],
      "Crear Nuevo Negocio modal title",
    );
    await expect(modalTitle).toBeVisible();

    const nameInput = await findVisibleLocator(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator("input").filter({ hasText: "" }),
      ],
      "Nombre del Negocio input",
    );
    await expect(nameInput).toBeVisible();

    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await captureScreenshot(page, testInfo, "03-agregar-negocio-modal.png");

    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /cancelar/i }), page);
  });

  await runStep("Administrar Negocios view", async () => {
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      const negocioMenu = await findVisibleLocator(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        "Mi Negocio menu entry for re-expand",
      );
      await clickAndWait(negocioMenu, page);
    }

    const manageBusiness = await findVisibleLocator(
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ],
      "Administrar Negocios action",
    );
    await clickAndWait(manageBusiness, page);

    await expect(page.getByText(/información general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/sección legal/i)).toBeVisible();

    await captureScreenshot(page, testInfo, "04-administrar-negocios-page.png", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const emailPattern = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    const bodyText = await page.locator("body").innerText();
    expect(bodyText).toMatch(emailPattern);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    evidence.termsUrl = await openLegalPageAndValidate({
      appPage: page,
      linkRegex: /términos y condiciones/i,
      headingRegex: /términos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo,
    });
  });

  await runStep("Política de Privacidad", async () => {
    evidence.privacyUrl = await openLegalPageAndValidate({
      appPage: page,
      linkRegex: /política de privacidad/i,
      headingRegex: /política de privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo,
    });
  });

  const reportLines = REPORT_FIELDS.map((field) => `${field}: ${report[field]}`);
  if (evidence.termsUrl) {
    reportLines.push(`Términos y Condiciones URL: ${evidence.termsUrl}`);
  }
  if (evidence.privacyUrl) {
    reportLines.push(`Política de Privacidad URL: ${evidence.privacyUrl}`);
  }

  const finalReport = `saleads_mi_negocio_full_test\n${reportLines.join("\n")}\n`;
  await attachText(testInfo, "final-report.txt", finalReport);
  console.log(finalReport);

  expect(
    failures,
    `One or more workflow validations failed:\n${failures.join("\n")}`,
  ).toHaveLength(0);
});
