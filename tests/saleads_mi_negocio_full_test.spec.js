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

function buildInitialReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function toFileSafeName(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(400);
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
}

async function isVisible(locator, timeout = 2500) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function findVisibleLocator(candidates, timeout = 8000) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    if (await isVisible(locator, timeout)) {
      return locator;
    }
  }

  throw new Error("Unable to find a visible locator from the provided candidates.");
}

async function clickByVisibleText(page, textPattern, options = {}) {
  const timeout = options.timeout ?? 12000;
  const candidates = [
    page.getByRole("button", { name: textPattern }),
    page.getByRole("link", { name: textPattern }),
    page.getByRole("menuitem", { name: textPattern }),
    page.getByRole("tab", { name: textPattern }),
    page.getByRole("treeitem", { name: textPattern }),
    page.getByText(textPattern),
  ];

  const locator = await findVisibleLocator(candidates, timeout);
  await locator.click();
  await waitForUi(page);

  return locator;
}

async function assertVisibleByText(page, textPattern, timeout = 15000) {
  const locator = await findVisibleLocator(
    [
      page.getByRole("heading", { name: textPattern }),
      page.getByRole("button", { name: textPattern }),
      page.getByRole("link", { name: textPattern }),
      page.getByText(textPattern),
    ],
    timeout,
  );

  await expect(locator).toBeVisible();
  return locator;
}

async function screenshot(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function openLegalPageAndReturn({
  page,
  linkPattern,
  headingPattern,
  screenshotName,
  testInfo,
}) {
  const context = page.context();
  const currentPages = new Set(context.pages());
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const navigationPromise = page
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 8000 })
    .catch(() => null);

  await clickByVisibleText(page, linkPattern);

  const popupPage = await popupPromise;
  let legalPage = page;

  if (popupPage && !currentPages.has(popupPage)) {
    legalPage = popupPage;
    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUi(legalPage);
  } else {
    await navigationPromise;
    await waitForUi(page);
  }

  await assertVisibleByText(legalPage, headingPattern);
  const legalText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (legalText.length < 120) {
    throw new Error("Legal content appears to be missing or too short.");
  }

  await screenshot(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (legalPage !== page) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildInitialReport();
  const failures = [];
  const legalUrls = {};

  const baseUrl =
    process.env.SALEADS_START_URL || process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;

  if (baseUrl) {
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No login page is available. Set SALEADS_START_URL, SALEADS_LOGIN_URL or BASE_URL.",
    );
  }

  await waitForUi(page);

  const runValidation = async (fieldName, fn) => {
    try {
      await fn();
      report[fieldName] = "PASS";
    } catch (error) {
      report[fieldName] = "FAIL";
      failures.push(`${fieldName}: ${error.message}`);
      await screenshot(page, testInfo, `failure-${toFileSafeName(fieldName)}.png`, true).catch(() => {});
    }
  };

  await runValidation("Login", async () => {
    const loginButton = await findVisibleLocator([
      page.getByRole("button", { name: /sign in with google|login with google|google/i }),
      page.getByRole("link", { name: /sign in with google|login with google|google/i }),
      page.getByText(/sign in with google|login with google|iniciar sesi[oó]n con google/i),
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    const googleEmail = "juanlucasbarbiergarzon@gmail.com";

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountOption = popup.getByText(googleEmail, { exact: true }).first();
      if (await isVisible(accountOption, 6000)) {
        await accountOption.click();
      }
      await popup.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
    } else {
      const accountOption = page.getByText(googleEmail, { exact: true }).first();
      if (await isVisible(accountOption, 6000)) {
        await accountOption.click();
        await waitForUi(page);
      }
    }

    await findVisibleLocator(
      [
        page.getByRole("navigation"),
        page.locator("aside"),
        page.getByText(/mi negocio|negocio/i),
      ],
      20000,
    );

    await screenshot(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runValidation("Mi Negocio menu", async () => {
    await clickByVisibleText(page, /negocio/i);
    await clickByVisibleText(page, /mi negocio/i);
    await assertVisibleByText(page, /agregar negocio/i);
    await assertVisibleByText(page, /administrar negocios/i);
    await screenshot(page, testInfo, "02-mi-negocio-menu-expanded.png", true);
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /^agregar negocio$/i);
    await assertVisibleByText(page, /crear nuevo negocio/i);

    const businessNameInput = await findVisibleLocator([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[name*='negocio' i]"),
      page.locator("input[placeholder*='negocio' i]"),
    ]);

    await expect(businessNameInput).toBeVisible();
    await assertVisibleByText(page, /tienes\s+2\s+de\s+3\s+negocios/i);
    await assertVisibleByText(page, /cancelar/i);
    await assertVisibleByText(page, /crear negocio/i);

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await screenshot(page, testInfo, "03-agregar-negocio-modal.png", true);
    await clickByVisibleText(page, /cancelar/i);
  });

  await runValidation("Administrar Negocios view", async () => {
    const adminOption = page.getByText(/administrar negocios/i).first();
    if (!(await isVisible(adminOption, 2500))) {
      await clickByVisibleText(page, /mi negocio/i);
    }

    await clickByVisibleText(page, /administrar negocios/i);
    await assertVisibleByText(page, /informaci[oó]n general/i);
    await assertVisibleByText(page, /detalles de la cuenta/i);
    await assertVisibleByText(page, /tus negocios/i);
    await assertVisibleByText(page, /secci[oó]n legal/i);
    await screenshot(page, testInfo, "04-administrar-negocios-page.png", true);
  });

  await runValidation("Información General", async () => {
    const infoGeneralContainer = await findVisibleLocator([
      page.locator("section, div").filter({ hasText: /informaci[oó]n general/i }),
      page.locator("main"),
    ]);
    const rawInfoText = await infoGeneralContainer.innerText();
    const infoText = rawInfoText.replace(/\s+/g, " ").trim();

    if (!/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i.test(infoText)) {
      throw new Error("User email was not found in Información General.");
    }

    const nameCandidate = rawInfoText
      .split("\n")
      .map((line) => line.trim())
      .find(
        (line) =>
          Boolean(line) &&
          !line.includes("@") &&
          !/informaci[oó]n|general|business|plan|cambiar|cuenta|detalles|tus negocios|secci[oó]n legal/i.test(
            line,
          ) &&
          /^[\p{L}.' -]{3,}$/u.test(line),
      );

    if (!nameCandidate) {
      throw new Error("User name candidate was not found in Información General.");
    }

    await assertVisibleByText(page, /business plan/i);
    await assertVisibleByText(page, /cambiar plan/i);
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await assertVisibleByText(page, /cuenta creada/i);
    await assertVisibleByText(page, /estado activo/i);
    await assertVisibleByText(page, /idioma seleccionado/i);
  });

  await runValidation("Tus Negocios", async () => {
    await assertVisibleByText(page, /tus negocios/i);
    await assertVisibleByText(page, /agregar negocio/i);
    await assertVisibleByText(page, /tienes\s+2\s+de\s+3\s+negocios/i);
  });

  await runValidation("Términos y Condiciones", async () => {
    const termsUrl = await openLegalPageAndReturn({
      page,
      linkPattern: /t[eé]rminos y condiciones/i,
      headingPattern: /t[eé]rminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo,
    });
    legalUrls.terms = termsUrl;
  });

  await runValidation("Política de Privacidad", async () => {
    const privacyUrl = await openLegalPageAndReturn({
      page,
      linkPattern: /pol[ií]tica de privacidad/i,
      headingPattern: /pol[ií]tica de privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo,
    });
    legalUrls.privacy = privacyUrl;
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    statusByField: report,
    legalUrls,
    failures,
  };

  await testInfo.attach("final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json",
  });

  console.log("FINAL_VALIDATION_REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  const failedFields = Object.entries(report)
    .filter(([, status]) => status === "FAIL")
    .map(([field]) => field);

  expect(
    failedFields,
    `Validation failed for: ${failedFields.join(", ")}\n${failures.join("\n")}`,
  ).toEqual([]);
});
