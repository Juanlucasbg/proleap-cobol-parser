const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const EXPECTED_USER_NAME = process.env.SALEADS_USER_NAME || "";

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

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toRegex(text) {
  return new RegExp(escapeRegex(text), "i");
}

function sanitizeName(name) {
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function isVisible(locator, timeout = 3000) {
  return locator
    .first()
    .isVisible({ timeout })
    .catch(() => false);
}

async function pickVisible(locators, description) {
  for (const locator of locators) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }

  throw new Error(`Could not find visible element: ${description}`);
}

async function screenshot(testInfo, page, name, fullPage = false) {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible({ timeout: 30000 });
  await locator.click();
  await waitForUi(page);
}

async function actionByVisibleText(page, text) {
  const regex = toRegex(text);

  return pickVisible(
    [
      page.getByRole("button", { name: regex }),
      page.getByRole("link", { name: regex }),
      page.getByRole("menuitem", { name: regex }),
      page.getByRole("tab", { name: regex }),
      page.getByText(regex, { exact: true }),
      page.getByText(regex),
    ],
    text,
  );
}

async function ensureLoginPage(page) {
  const configuredUrl = process.env.SALEADS_BASE_URL || process.env.SALEADS_URL;

  if (page.url() === "about:blank" && configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No active SaleADS page detected. Open the SaleADS login page first or set SALEADS_BASE_URL.",
    );
  }
}

async function chooseGoogleAccountIfPrompted(page) {
  const accountLocator = page.getByText(GOOGLE_ACCOUNT_EMAIL);

  if (await isVisible(accountLocator, 12000)) {
    await accountLocator.first().click();
    await waitForUi(page);
  }
}

async function runStep({ name, report, failures, page, testInfo, action }) {
  try {
    await action();
    report[name] = "PASS";
  } catch (error) {
    report[name] = "FAIL";
    failures.push(`${name}: ${error.message}`);
    await screenshot(testInfo, page, `failure-${sanitizeName(name)}`, true).catch(
      () => {},
    );
  }
}

async function openLegalLink({
  page,
  context,
  linkText,
  headingText,
  screenshotName,
  urls,
  testInfo,
}) {
  const link = await actionByVisibleText(page, linkText);
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await link.click();
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});

    await expect(
      popup.getByRole("heading", { name: toRegex(headingText) }).first(),
    ).toBeVisible({ timeout: 30000 });
    await expect(popup.locator("body")).toContainText(toRegex(headingText));

    urls[linkText] = popup.url();
    await screenshot(testInfo, popup, screenshotName, true);
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  await waitForUi(page);
  await expect(page.getByRole("heading", { name: toRegex(headingText) }).first()).toBeVisible({
    timeout: 30000,
  });
  await expect(page.locator("body")).toContainText(toRegex(headingText));

  urls[linkText] = page.url();
  await screenshot(testInfo, page, screenshotName, true);
  await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUi(page);
}

test("SaleADS Mi Negocio complete workflow", async ({ page, context }, testInfo) => {
  test.setTimeout(240000);

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  const legalUrls = {};

  await ensureLoginPage(page);

  await runStep({
    name: "Login",
    report,
    failures,
    page,
    testInfo,
    action: async () => {
      const loginButton = await pickVisible(
        [
          page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n|login/i }),
          page.getByRole("link", { name: /google|sign in|iniciar sesi[oó]n|login/i }),
          page.getByText(/google/i),
        ],
        "Login with Google button",
      );

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, loginButton);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await chooseGoogleAccountIfPrompted(popup);
        await popup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
        await page.bringToFront();
      } else {
        await chooseGoogleAccountIfPrompted(page);
      }

      await waitForUi(page);
      await expect(page.locator("aside, nav, [role='navigation']").first()).toBeVisible({
        timeout: 45000,
      });
      await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({
        timeout: 45000,
      });
      await screenshot(testInfo, page, "step-1-dashboard-loaded", true);
    },
  });

  await runStep({
    name: "Mi Negocio menu",
    report,
    failures,
    page,
    testInfo,
    action: async () => {
      const negocioSection = await actionByVisibleText(page, "Negocio").catch(() => null);
      if (negocioSection) {
        await clickAndWait(page, negocioSection);
      }

      const miNegocio = await actionByVisibleText(page, "Mi Negocio");
      await clickAndWait(page, miNegocio);

      await expect(page.getByText(toRegex("Agregar Negocio")).first()).toBeVisible();
      await expect(page.getByText(toRegex("Administrar Negocios")).first()).toBeVisible();
      await screenshot(testInfo, page, "step-2-mi-negocio-expanded", true);
    },
  });

  await runStep({
    name: "Agregar Negocio modal",
    report,
    failures,
    page,
    testInfo,
    action: async () => {
      await clickAndWait(page, await actionByVisibleText(page, "Agregar Negocio"));

      const modalTitle = page.getByText(toRegex("Crear Nuevo Negocio")).first();
      await expect(modalTitle).toBeVisible({ timeout: 20000 });

      const businessNameInput = await pickVisible(
        [
          page.getByLabel(toRegex("Nombre del Negocio")),
          page.getByPlaceholder(toRegex("Nombre del Negocio")),
          page.locator("input[name*='nombre'], input[id*='nombre']").first(),
        ],
        "Nombre del Negocio input",
      );

      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(toRegex("Tienes 2 de 3 negocios")).first()).toBeVisible();
      await expect(page.getByText(toRegex("Cancelar")).first()).toBeVisible();
      await expect(page.getByText(toRegex("Crear Negocio")).first()).toBeVisible();
      await screenshot(testInfo, page, "step-3-agregar-negocio-modal", true);

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndWait(page, await actionByVisibleText(page, "Cancelar"));
    },
  });

  await runStep({
    name: "Administrar Negocios view",
    report,
    failures,
    page,
    testInfo,
    action: async () => {
      if (!(await isVisible(page.getByText(toRegex("Administrar Negocios"))))) {
        await clickAndWait(page, await actionByVisibleText(page, "Mi Negocio"));
      }

      await clickAndWait(page, await actionByVisibleText(page, "Administrar Negocios"));

      await expect(page.getByText(toRegex("Información General")).first()).toBeVisible({
        timeout: 30000,
      });
      await expect(page.getByText(toRegex("Detalles de la Cuenta")).first()).toBeVisible();
      await expect(page.getByText(toRegex("Tus Negocios")).first()).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
      await screenshot(testInfo, page, "step-4-administrar-negocios", true);
    },
  });

  await runStep({
    name: "Información General",
    report,
    failures,
    page,
    testInfo,
    action: async () => {
      if (EXPECTED_USER_NAME) {
        await expect(page.getByText(toRegex(EXPECTED_USER_NAME)).first()).toBeVisible();
      } else {
        await expect(page.getByText(/nombre|usuario|perfil/i).first()).toBeVisible();
      }

      await expect(
        page.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")).first(),
      ).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByText(toRegex("Cambiar Plan")).first()).toBeVisible();
    },
  });

  await runStep({
    name: "Detalles de la Cuenta",
    report,
    failures,
    page,
    testInfo,
    action: async () => {
      await expect(page.getByText(toRegex("Cuenta creada")).first()).toBeVisible();
      await expect(page.getByText(toRegex("Estado activo")).first()).toBeVisible();
      await expect(page.getByText(toRegex("Idioma seleccionado")).first()).toBeVisible();
    },
  });

  await runStep({
    name: "Tus Negocios",
    report,
    failures,
    page,
    testInfo,
    action: async () => {
      await expect(page.getByText(toRegex("Tus Negocios")).first()).toBeVisible();
      await expect(page.getByText(toRegex("Agregar Negocio")).first()).toBeVisible();
      await expect(page.getByText(toRegex("Tienes 2 de 3 negocios")).first()).toBeVisible();
    },
  });

  await runStep({
    name: "Términos y Condiciones",
    report,
    failures,
    page,
    testInfo,
    action: async () => {
      await openLegalLink({
        page,
        context,
        linkText: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        screenshotName: "step-8-terminos-y-condiciones",
        urls: legalUrls,
        testInfo,
      });
    },
  });

  await runStep({
    name: "Política de Privacidad",
    report,
    failures,
    page,
    testInfo,
    action: async () => {
      await openLegalLink({
        page,
        context,
        linkText: "Política de Privacidad",
        headingText: "Política de Privacidad",
        screenshotName: "step-9-politica-de-privacidad",
        urls: legalUrls,
        testInfo,
      });
    },
  });

  const finalReport = { report, legalUrls };
  const finalReportContent = JSON.stringify(finalReport, null, 2);
  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: Buffer.from(finalReportContent),
    contentType: "application/json",
  });
  console.log(finalReportContent);

  expect(failures, `Workflow had failures:\n${failures.join("\n")}`).toEqual([]);
});
