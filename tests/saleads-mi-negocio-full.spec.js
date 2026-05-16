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

function newReport() {
  return REPORT_KEYS.reduce((acc, key) => {
    acc[key] = { status: "PENDING" };
    return acc;
  }, {});
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function firstVisibleLocator(locators, timeout = 15000) {
  for (const locator of locators) {
    const candidate = locator.first();
    const visible = await candidate
      .isVisible({ timeout })
      .catch(() => false);
    if (visible) {
      return candidate;
    }
  }

  throw new Error("Could not find any visible element from selector candidates.");
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function saveCheckpoint(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function selectGoogleAccountIfShown(pageOrPopup) {
  const accountLocator = pageOrPopup.getByText(GOOGLE_ACCOUNT_EMAIL).first();
  const accountVisible = await accountLocator.isVisible({ timeout: 7000 }).catch(() => false);

  if (accountVisible) {
    await accountLocator.click();
    await waitForUi(pageOrPopup);
  }
}

async function runStep(report, key, fn) {
  try {
    await fn();
    report[key] = { ...report[key], status: "PASS" };
  } catch (error) {
    report[key] = {
      ...report[key],
      status: "FAIL",
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

async function openLegalLinkAndValidate({
  appPage,
  context,
  linkTextRegex,
  headingRegex,
  testInfo,
  screenshotFile,
  reportEntry,
}) {
  const appUrlBeforeClick = appPage.url();
  const link = await firstVisibleLocator(
    [
      appPage.getByRole("link", { name: linkTextRegex }),
      appPage.getByText(linkTextRegex),
    ],
    20000,
  );

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await link.click();
  await waitForUi(appPage);

  let legalPage = await popupPromise;
  const isPopup = Boolean(legalPage);

  if (isPopup) {
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  } else {
    legalPage = appPage;
  }

  const headingLocator = await firstVisibleLocator(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex),
    ],
    20000,
  );
  await expect(headingLocator).toBeVisible();

  const legalBody = legalPage.locator("main, article, section, body").first();
  await expect(legalBody).toContainText(/\S+/, { timeout: 15000 });

  await saveCheckpoint(legalPage, testInfo, screenshotFile, true);
  reportEntry.url = legalPage.url();

  if (isPopup) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== appUrlBeforeClick) {
    await appPage.goto(appUrlBeforeClick);
    await waitForUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = newReport();
  const configuredLoginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;

  if (configuredLoginUrl) {
    await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else {
    throw new Error(
      "No environment URL configured. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL (or BASE_URL) to the current SaleADS login page.",
    );
  }

  await runStep(report, "Login", async () => {
    const loginButton = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByRole("button", { name: /google/i }),
      ],
      30000,
    );

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, loginButton);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfShown(googlePopup);
      await googlePopup.waitForEvent("close", { timeout: 120000 }).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await selectGoogleAccountIfShown(page);
    }

    const sidebar = await firstVisibleLocator(
      [
        page.locator("aside"),
        page.locator("nav").filter({ hasText: /negocio|mi negocio/i }),
      ],
      90000,
    );
    await expect(sidebar).toBeVisible();
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 20000 });

    await saveCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep(report, "Mi Negocio menu", async () => {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    const negocioVisible = await negocioSection.isVisible({ timeout: 8000 }).catch(() => false);

    if (negocioVisible) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocioOption = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ],
      15000,
    );
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: 15000 });

    await saveCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    const addBusinessMenuOption = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      15000,
    );
    await clickAndWait(page, addBusinessMenuOption);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 20000 });

    const businessNameInput = await firstVisibleLocator(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.getByRole("textbox", { name: /Nombre del Negocio/i }),
        page.locator("input").first(),
      ],
      15000,
    );
    await expect(businessNameInput).toBeVisible();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible({ timeout: 10000 });

    await saveCheckpoint(page, testInfo, "03-crear-nuevo-negocio-modal.png");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }).first());
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeHidden({ timeout: 10000 });
  });

  await runStep(report, "Administrar Negocios view", async () => {
    const adminOptionVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible({ timeout: 5000 }).catch(() => false);
    if (!adminOptionVisible) {
      const miNegocioOption = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        15000,
      );
      await clickAndWait(page, miNegocioOption);
    }

    const manageBusinessesOption = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      15000,
    );
    await clickAndWait(page, manageBusinessesOption);

    await expect(page.getByText(/^Información General$/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 20000 });

    await saveCheckpoint(page, testInfo, "04-administrar-negocios-account-page.png", true);
  });

  await runStep(report, "Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 15000 });

    const userIdentityVisible = await firstVisibleLocator(
      [
        page.getByText(/@/),
        page.getByText(/juan|lucas|barbier/i),
      ],
      15000,
    );
    await expect(userIdentityVisible).toBeVisible();
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 15000 });
  });

  await runStep(report, "Tus Negocios", async () => {
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
  });

  await runStep(report, "Términos y Condiciones", async () => {
    await openLegalLinkAndValidate({
      appPage: page,
      context,
      linkTextRegex: /Términos y Condiciones/i,
      headingRegex: /Términos y Condiciones/i,
      testInfo,
      screenshotFile: "05-terminos-y-condiciones.png",
      reportEntry: report["Términos y Condiciones"],
    });
  });

  await runStep(report, "Política de Privacidad", async () => {
    await openLegalLinkAndValidate({
      appPage: page,
      context,
      linkTextRegex: /Política de Privacidad/i,
      headingRegex: /Política de Privacidad/i,
      testInfo,
      screenshotFile: "06-politica-de-privacidad.png",
      reportEntry: report["Política de Privacidad"],
    });
  });

  const finalReport = JSON.stringify(report, null, 2);
  testInfo.annotations.push({ type: "final-report", description: finalReport });
  await testInfo.attach("saleads-mi-negocio-final-report.json", {
    body: Buffer.from(finalReport, "utf-8"),
    contentType: "application/json",
  });

  // eslint-disable-next-line no-console
  console.log("saleads_mi_negocio_full_test final report:\n", finalReport);

  const hasFailure = Object.values(report).some((entry) => entry.status === "FAIL");
  expect(hasFailure, "One or more Mi Negocio validations failed. See attached final report.").toBeFalsy();
});
