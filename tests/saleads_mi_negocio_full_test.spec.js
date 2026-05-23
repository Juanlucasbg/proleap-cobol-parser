const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const TEST_BUSINESS_NAME =
  process.env.SALEADS_TEST_BUSINESS_NAME || "Negocio Prueba Automatizacion";

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

function createResultMap() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function normalizeText(input) {
  return (input || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch {
    // Some UI transitions do not reach networkidle reliably.
  }
  await page.waitForTimeout(400);
}

async function isVisible(locator, timeout = 4000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function findFirstVisible(candidates, timeout = 4000) {
  for (const candidate of candidates) {
    if (await isVisible(candidate, timeout)) {
      return candidate.first();
    }
  }
  throw new Error("None of the candidate elements became visible.");
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page, testInfo, filename, options = {}) {
  const path = testInfo.outputPath(filename);
  await page.screenshot({ path, fullPage: Boolean(options.fullPage) });
  await testInfo.attach(filename, { path, contentType: "image/png" });
}

async function clickGoogleLogin(page) {
  const loginButton = await findFirstVisible([
    page.getByRole("button", {
      name: /sign in with google|iniciar sesion con google|inicia sesion con google/i,
    }),
    page.getByText(/sign in with google|iniciar sesion con google|inicia sesion con google/i),
    page.getByRole("button", { name: /google/i }),
  ]);

  const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await clickAndWait(page, loginButton);
  const popupPage = await popupPromise;

  return popupPage;
}

async function trySelectGoogleAccount(targetPage) {
  const escapedEmail = GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const accountCandidate = targetPage.getByText(new RegExp(escapedEmail, "i"));
  if (await isVisible(accountCandidate, 8000)) {
    await clickAndWait(targetPage, accountCandidate.first());
    return true;
  }
  return false;
}

async function ensureBusinessMenuExpanded(page) {
  const addBusinessOption = page.getByText(/agregar negocio/i);
  const manageBusinessOption = page.getByText(/administrar negocios/i);

  if ((await isVisible(addBusinessOption, 1500)) && (await isVisible(manageBusinessOption, 1500))) {
    return;
  }

  const businessMenu = await findFirstVisible([
    page.getByRole("button", { name: /mi negocio/i }),
    page.getByRole("link", { name: /mi negocio/i }),
    page.getByText(/mi negocio/i),
    page.getByRole("button", { name: /negocio/i }),
    page.getByText(/^negocio$/i),
  ]);
  await clickAndWait(page, businessMenu);
}

async function validateSectionHeading(page, textRegex) {
  const heading = await findFirstVisible([
    page.getByRole("heading", { name: textRegex }),
    page.getByText(textRegex),
  ]);
  await expect(heading).toBeVisible();
}

async function validateLegalLink({
  page,
  urls,
  linkRegex,
  headingRegex,
  reportKey,
  screenshotName,
  testInfo,
}) {
  const link = await findFirstVisible([
    page.getByRole("link", { name: linkRegex }),
    page.getByText(linkRegex),
  ]);

  const previousUrl = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickAndWait(page, link);
  const popup = await popupPromise;

  let legalPage = page;
  let openedInNewTab = false;

  if (popup) {
    openedInNewTab = true;
    legalPage = popup;
    await waitForUi(legalPage);
  }

  await validateSectionHeading(legalPage, headingRegex);

  const contentRoot = legalPage.locator("main, article, body");
  const contentText = await contentRoot.first().innerText();
  expect(contentText.length).toBeGreaterThan(120);

  await captureCheckpoint(legalPage, testInfo, screenshotName, { fullPage: true });
  urls[reportKey] = legalPage.url();

  if (openedInNewTab) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (normalizeText(page.url()) !== normalizeText(previousUrl)) {
    await page.goBack();
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, baseURL }, testInfo) => {
  const results = createResultMap();
  const urls = {
    "Términos y Condiciones": null,
    "Política de Privacidad": null,
  };
  const failures = [];

  const recordResult = (field, pass, errorMessage = null) => {
    results[field] = pass ? "PASS" : "FAIL";
    if (!pass) {
      failures.push(`${field}: ${errorMessage || "validation failed"}`);
    }
  };

  if (baseURL) {
    await page.goto(baseURL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  // Step 1: Login with Google
  try {
    const popupPage = await clickGoogleLogin(page);

    if (popupPage) {
      await waitForUi(popupPage);
      await trySelectGoogleAccount(popupPage);
      try {
        await popupPage.waitForEvent("close", { timeout: 45000 });
      } catch {
        await popupPage.close();
      }
      await page.bringToFront();
    } else {
      await trySelectGoogleAccount(page);
    }

    await waitForUi(page);

    const appMain = await findFirstVisible([
      page.locator("aside"),
      page.locator("nav"),
      page.getByText(/negocio/i),
    ]);
    await expect(appMain).toBeVisible();

    const sidebar = await findFirstVisible([
      page.locator("aside"),
      page.locator("nav"),
      page.getByRole("navigation"),
    ]);
    await expect(sidebar).toBeVisible();

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png");
    recordResult("Login", true);
  } catch (error) {
    recordResult("Login", false, error.message);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await ensureBusinessMenuExpanded(page);
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png");
    recordResult("Mi Negocio menu", true);
  } catch (error) {
    recordResult("Mi Negocio menu", false, error.message);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const addBusiness = await findFirstVisible([
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i),
    ]);
    await clickAndWait(page, addBusiness);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const businessNameInput = page.getByLabel(/nombre del negocio/i);
    await businessNameInput.click();
    await businessNameInput.fill(TEST_BUSINESS_NAME);
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }));
    recordResult("Agregar Negocio modal", true);
  } catch (error) {
    recordResult("Agregar Negocio modal", false, error.message);
  }

  // Step 4: Open Administrar Negocios
  try {
    await ensureBusinessMenuExpanded(page);

    const manageBusinesses = await findFirstVisible([
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ]);
    await clickAndWait(page, manageBusinesses);

    await validateSectionHeading(page, /informacion general|información general/i);
    await validateSectionHeading(page, /detalles de la cuenta/i);
    await validateSectionHeading(page, /tus negocios/i);
    await validateSectionHeading(page, /seccion legal|sección legal/i);

    await captureCheckpoint(page, testInfo, "04-administrar-negocios-view.png", { fullPage: true });
    recordResult("Administrar Negocios view", true);
  } catch (error) {
    recordResult("Administrar Negocios view", false, error.message);
  }

  // Step 5: Validate Información General
  try {
    const infoGeneralContainer = await findFirstVisible([
      page.locator("section, div").filter({ hasText: /informacion general|información general/i }),
      page.locator("section, div").filter({ hasText: /business plan/i }),
    ]);

    await expect(infoGeneralContainer.getByText(/business plan/i)).toBeVisible();
    await expect(infoGeneralContainer.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    await expect(page.getByText(/@/).first()).toBeVisible();

    const hasPotentialName = await infoGeneralContainer
      .locator("h1, h2, h3, h4, p, span, div")
      .filter({ hasText: /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/ })
      .first()
      .isVisible()
      .catch(() => false);

    expect(hasPotentialName).toBeTruthy();
    recordResult("Información General", true);
  } catch (error) {
    recordResult("Información General", false, error.message);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
    recordResult("Detalles de la Cuenta", true);
  } catch (error) {
    recordResult("Detalles de la Cuenta", false, error.message);
  }

  // Step 7: Validate Tus Negocios
  try {
    const businessesSection = await findFirstVisible([
      page.locator("section, div").filter({ hasText: /tus negocios/i }),
      page.locator("section, div").filter({ hasText: /tienes 2 de 3 negocios/i }),
    ]);

    await expect(businessesSection).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();

    const hasBusinessRows =
      (await businessesSection.locator("li, tr, [role='listitem'], [role='row']").count()) > 0;
    expect(hasBusinessRows).toBeTruthy();

    recordResult("Tus Negocios", true);
  } catch (error) {
    recordResult("Tus Negocios", false, error.message);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    await validateLegalLink({
      page,
      urls,
      linkRegex: /terminos y condiciones|términos y condiciones/i,
      headingRegex: /terminos y condiciones|términos y condiciones/i,
      reportKey: "Términos y Condiciones",
      screenshotName: "08-terminos-y-condiciones.png",
      testInfo,
    });
    recordResult("Términos y Condiciones", true);
  } catch (error) {
    recordResult("Términos y Condiciones", false, error.message);
  }

  // Step 9: Validate Política de Privacidad
  try {
    await validateLegalLink({
      page,
      urls,
      linkRegex: /politica de privacidad|política de privacidad/i,
      headingRegex: /politica de privacidad|política de privacidad/i,
      reportKey: "Política de Privacidad",
      screenshotName: "09-politica-de-privacidad.png",
      testInfo,
    });
    recordResult("Política de Privacidad", true);
  } catch (error) {
    recordResult("Política de Privacidad", false, error.message);
  }

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    steps: results,
    legal_urls: urls,
    failures,
  };

  await testInfo.attach("final-report.json", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json",
  });

  console.log(JSON.stringify(finalReport, null, 2));
  expect(failures, failures.join("\n")).toEqual([]);
});
