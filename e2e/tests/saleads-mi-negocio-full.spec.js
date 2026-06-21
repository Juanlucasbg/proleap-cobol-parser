const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const CHECKPOINT_DIR = path.join(__dirname, "..", "artifacts", "screenshots");
const REPORT_PATH = path.join(__dirname, "..", "artifacts", "saleads-mi-negocio-report.json");
const EXPECTED_GOOGLE_ACCOUNT =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const EXPECTED_USER_EMAIL = process.env.SALEADS_EXPECTED_EMAIL || EXPECTED_GOOGLE_ACCOUNT;

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

function createInitialReport() {
  const steps = {};
  for (const field of REPORT_FIELDS) {
    steps[field] = {
      status: "PENDING",
      details: "",
    };
  }

  return {
    generatedAt: new Date().toISOString(),
    loginUrl: process.env.SALEADS_LOGIN_URL || "UNSET",
    screenshotsDirectory: CHECKPOINT_DIR,
    legalUrls: {},
    steps,
  };
}

function writeReport(report) {
  for (const field of REPORT_FIELDS) {
    if (report.steps[field].status === "PENDING") {
      report.steps[field].status = "FAIL";
      report.steps[field].details = report.steps[field].details || "Step was not executed.";
    }
  }

  fs.mkdirSync(path.dirname(REPORT_PATH), { recursive: true });
  fs.writeFileSync(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}

function setStepResult(report, stepName, status, details = "") {
  report.steps[stepName] = {
    status,
    details,
  };
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function waitForFirstVisible(page, candidates, description, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of candidates) {
      try {
        if (await locator.first().isVisible()) {
          return locator.first();
        }
      } catch (error) {
        // Keep polling until timeout.
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Could not find visible element: ${description}`);
}

async function screenshotCheckpoint(page, fileName, fullPage = false) {
  fs.mkdirSync(CHECKPOINT_DIR, { recursive: true });
  await page.screenshot({
    path: path.join(CHECKPOINT_DIR, fileName),
    fullPage,
  });
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function selectGoogleAccountIfPrompted(page) {
  const accountLocator = page.getByText(EXPECTED_GOOGLE_ACCOUNT, { exact: false });
  const chooserVisible = await accountLocator.first().isVisible().catch(() => false);

  if (!chooserVisible) {
    return;
  }

  await clickAndWait(page, accountLocator.first());
}

async function openLegalLink({
  appPage,
  stepName,
  linkNameRegex,
  headingRegex,
  screenshotName,
  report,
}) {
  const legalLink = await waitForFirstVisible(
    appPage,
    [appPage.getByRole("link", { name: linkNameRegex }), appPage.getByText(linkNameRegex)],
    `legal link ${linkNameRegex}`
  );

  const appUrlBefore = appPage.url();
  const newTabPromise = appPage.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickAndWait(appPage, legalLink);

  let legalPage = await newTabPromise;
  const openedNewTab = Boolean(legalPage);

  if (!legalPage) {
    legalPage = appPage;
  }

  await waitForUi(legalPage);

  const legalHeading = await waitForFirstVisible(
    legalPage,
    [legalPage.getByRole("heading", { name: headingRegex }), legalPage.getByText(headingRegex)],
    `legal heading ${headingRegex}`
  );
  await expect(legalHeading).toBeVisible();

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  if (bodyText.length < 120) {
    throw new Error(`Legal content appears too short for ${stepName}.`);
  }

  await screenshotCheckpoint(legalPage, screenshotName, true);
  report.legalUrls[stepName] = legalPage.url();
  setStepResult(report, stepName, "PASS", `URL: ${legalPage.url()}`);

  if (openedNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  if (appPage.url() !== appUrlBefore) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report = createInitialReport();

  try {
    if (!process.env.SALEADS_LOGIN_URL) {
      throw new Error(
        "SALEADS_LOGIN_URL is required for this automated run because Playwright starts with a blank tab."
      );
    }

    await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginButton = await waitForFirstVisible(
      page,
      [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
      ],
      "Google login button"
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    if (popup) {
      await waitForUi(popup);
      await selectGoogleAccountIfPrompted(popup);
      await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await selectGoogleAccountIfPrompted(page);
    }

    const mainInterface = await waitForFirstVisible(
      page,
      [page.locator("main"), page.locator("[role='main']"), page.locator("div").filter({ hasText: /dashboard/i })],
      "main application interface"
    );
    await expect(mainInterface).toBeVisible();

    const sidebar = await waitForFirstVisible(
      page,
      [page.getByRole("navigation"), page.locator("aside"), page.locator("[class*='sidebar']")],
      "left sidebar navigation"
    );
    await expect(sidebar).toBeVisible();
    await screenshotCheckpoint(page, "01-dashboard-loaded.png");
    setStepResult(report, "Login", "PASS", "Dashboard and left sidebar are visible.");
  } catch (error) {
    setStepResult(report, "Login", "FAIL", error.message);
    writeReport(report);
    throw error;
  }

  try {
    const negocioItem = await waitForFirstVisible(
      page,
      [page.getByRole("button", { name: /negocio/i }), page.getByText(/^Negocio$/i), page.getByText(/Negocio/i)],
      "Negocio menu item"
    );
    await clickAndWait(page, negocioItem);

    const miNegocioItem = await waitForFirstVisible(
      page,
      [page.getByRole("button", { name: /mi negocio/i }), page.getByText(/Mi Negocio/i)],
      "Mi Negocio option"
    );
    await clickAndWait(page, miNegocioItem);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();
    await screenshotCheckpoint(page, "02-mi-negocio-menu-expanded.png");
    setStepResult(report, "Mi Negocio menu", "PASS", "Menu expanded and submenu options are visible.");
  } catch (error) {
    setStepResult(report, "Mi Negocio menu", "FAIL", error.message);
  }

  try {
    const addBusinessMenuItem = await waitForFirstVisible(
      page,
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/Agregar Negocio/i)],
      "Agregar Negocio menu option"
    );
    await clickAndWait(page, addBusinessMenuItem);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i))).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i)).fill(
      "Negocio Prueba Automatización"
    );
    await screenshotCheckpoint(page, "03-agregar-negocio-modal.png");
    await clickAndWait(page, page.getByRole("button", { name: /Cancelar/i }));
    setStepResult(report, "Agregar Negocio modal", "PASS", "Modal content validated and closed.");
  } catch (error) {
    setStepResult(report, "Agregar Negocio modal", "FAIL", error.message);
  }

  try {
    const adminItemVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!adminItemVisible) {
      const miNegocioItem = await waitForFirstVisible(
        page,
        [page.getByRole("button", { name: /mi negocio/i }), page.getByText(/Mi Negocio/i)],
        "Mi Negocio option before admin navigation"
      );
      await clickAndWait(page, miNegocioItem);
    }

    const manageBusinesses = await waitForFirstVisible(
      page,
      [page.getByRole("button", { name: /administrar negocios/i }), page.getByText(/Administrar Negocios/i)],
      "Administrar Negocios option"
    );
    await clickAndWait(page, manageBusinesses);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();
    await screenshotCheckpoint(page, "04-administrar-negocios-full.png", true);
    setStepResult(report, "Administrar Negocios view", "PASS", "Account sections are visible.");
  } catch (error) {
    setStepResult(report, "Administrar Negocios view", "FAIL", error.message);
  }

  try {
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    await expect(page.getByText(EXPECTED_USER_EMAIL, { exact: false })).toBeVisible();

    const expectedName = process.env.SALEADS_EXPECTED_NAME;
    if (expectedName) {
      await expect(page.getByText(expectedName, { exact: false })).toBeVisible();
    } else {
      await expect(page.getByText(/Nombre|Usuario/i)).toBeVisible();
    }

    setStepResult(report, "Información General", "PASS", "Name/email/plan details are visible.");
  } catch (error) {
    setStepResult(report, "Información General", "FAIL", error.message);
  }

  try {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    setStepResult(report, "Detalles de la Cuenta", "PASS", "Account details section is valid.");
  } catch (error) {
    setStepResult(report, "Detalles de la Cuenta", "FAIL", error.message);
  }

  try {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    setStepResult(report, "Tus Negocios", "PASS", "Business list and quota info are visible.");
  } catch (error) {
    setStepResult(report, "Tus Negocios", "FAIL", error.message);
  }

  try {
    await openLegalLink({
      appPage: page,
      stepName: "Términos y Condiciones",
      linkNameRegex: /T[eé]rminos y Condiciones/i,
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      report,
    });
  } catch (error) {
    setStepResult(report, "Términos y Condiciones", "FAIL", error.message);
  }

  try {
    await openLegalLink({
      appPage: page,
      stepName: "Política de Privacidad",
      linkNameRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      report,
    });
  } catch (error) {
    setStepResult(report, "Política de Privacidad", "FAIL", error.message);
  }

  writeReport(report);
  console.log("Final Mi Negocio report:");
  console.log(JSON.stringify(report, null, 2));

  const failedSteps = REPORT_FIELDS.filter((field) => report.steps[field].status !== "PASS");
  expect(
    failedSteps,
    `One or more workflow validations failed. Review ${REPORT_PATH} and screenshots in ${CHECKPOINT_DIR}.`
  ).toEqual([]);
});
