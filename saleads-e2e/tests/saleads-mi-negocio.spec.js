const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

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
  "Política de Privacidad"
];

function toSlug(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function newReport() {
  return REPORT_KEYS.reduce((acc, key) => {
    acc[key] = "FAIL";
    return acc;
  }, {});
}

function createArtifactsDir() {
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const dir = path.join(process.cwd(), "test-results", "saleads-mi-negocio", stamp);
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

async function isVisible(locator, timeout = 5000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch (_error) {
    return false;
  }
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

async function clickAndWait(locator, page, timeout = 15000) {
  await expect(locator.first()).toBeVisible({ timeout });
  await locator.first().click();
  await waitForUi(page);
}

async function captureCheckpoint(page, artifactsDir, name, fullPage = false) {
  await page.screenshot({
    path: path.join(artifactsDir, `${toSlug(name)}.png`),
    fullPage
  });
}

async function selectGoogleAccountIfPrompted(authPage) {
  const accountLocator = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();

  if (await isVisible(accountLocator, 12000)) {
    await accountLocator.click();
    await authPage.waitForLoadState("domcontentloaded");
    return;
  }

  const accountChooser = authPage
    .getByText(/choose an account|elige una cuenta|selecciona una cuenta/i)
    .first();

  if (await isVisible(accountChooser, 3000)) {
    throw new Error(`Google account selector is open, but ${GOOGLE_ACCOUNT_EMAIL} is not visible.`);
  }
}

async function openLegalLinkAndValidate({
  page,
  linkText,
  expectedHeading,
  reportUrls,
  reportKey,
  artifactsDir
}) {
  const appUrlBeforeClick = page.url();
  const legalSection = page.locator("section, div").filter({ hasText: /Secci[oó]n Legal|Legal/i }).first();
  const withinTextLocator =
    typeof linkText === "string"
      ? legalSection.getByText(linkText, { exact: false }).first()
      : legalSection.getByText(linkText).first();
  const pageTextLocator =
    typeof linkText === "string" ? page.getByText(linkText, { exact: false }).first() : page.getByText(linkText).first();

  let link = withinTextLocator;
  if (!(await isVisible(link, 4000))) {
    link = pageTextLocator;
  }

  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await link.click();
  await waitForUi(page);
  const popup = await popupPromise;

  const legalPage = popup || page;
  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForTimeout(1200);

  const headingByRole = legalPage.getByRole("heading", { name: new RegExp(expectedHeading, "i") }).first();
  if (await isVisible(headingByRole, 8000)) {
    await expect(headingByRole).toBeVisible();
  } else {
    await expect(legalPage.getByText(new RegExp(expectedHeading, "i")).first()).toBeVisible({ timeout: 12000 });
  }

  const legalContent = legalPage.locator("main p, article p, p").first();
  await expect(legalContent).toBeVisible({ timeout: 12000 });

  await captureCheckpoint(legalPage, artifactsDir, reportKey, true);
  reportUrls[reportKey] = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report = newReport();
  const failures = [];
  const reportUrls = {
    "Términos y Condiciones": null,
    "Política de Privacidad": null
  };
  const artifactsDir = createArtifactsDir();

  const runStep = async (reportKey, stepFn) => {
    try {
      await stepFn();
      report[reportKey] = "PASS";
    } catch (error) {
      report[reportKey] = "FAIL";
      failures.push({
        step: reportKey,
        message: error instanceof Error ? error.message : String(error)
      });
      await captureCheckpoint(page, artifactsDir, `${reportKey}-failed`).catch(() => {});
    }
  };

  await runStep("Login", async () => {
    const startUrl = process.env.SALEADS_URL || process.env.APP_URL || process.env.BASE_URL;
    if (page.url() === "about:blank" && startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);

    const sidebar = page.locator("aside, nav").first();
    let dashboardReady = await isVisible(sidebar, 6000);

    if (!dashboardReady) {
      const googleLoginButton = page
        .getByRole("button", {
          name: /sign in with google|continue with google|iniciar sesi[oó]n con google|continuar con google|google/i
        })
        .first();

      await expect(googleLoginButton).toBeVisible({ timeout: 20000 });

      const popupPromise = page.waitForEvent("popup", { timeout: 12000 }).catch(() => null);
      await googleLoginButton.click();
      await waitForUi(page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await selectGoogleAccountIfPrompted(popup);
      } else {
        await selectGoogleAccountIfPrompted(page);
      }

      await waitForUi(page);
      dashboardReady = await isVisible(sidebar, 30000);
    }

    if (!dashboardReady) {
      await expect(page.getByText(/mi negocio|negocio/i).first()).toBeVisible({ timeout: 30000 });
      await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30000 });
    }

    await captureCheckpoint(page, artifactsDir, "dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    if (await isVisible(negocioSection, 6000)) {
      await clickAndWait(negocioSection, page);
    }

    await clickAndWait(page.getByText(/Mi Negocio/i).first(), page);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15000 });

    await captureCheckpoint(page, artifactsDir, "mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickAndWait(page.getByText(/Agregar Negocio/i).first(), page);

    let modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
    if (!(await isVisible(modal, 6000))) {
      modal = page.locator("[role='dialog'], .modal").filter({ hasText: /Crear Nuevo Negocio/i }).first();
    }

    await expect(modal).toBeVisible({ timeout: 10000 });
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    let businessNameInput = modal.getByLabel(/Nombre del Negocio/i).first();
    if (!(await isVisible(businessNameInput, 5000))) {
      businessNameInput = modal.getByPlaceholder(/Nombre del Negocio/i).first();
    }
    await expect(businessNameInput).toBeVisible();

    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await captureCheckpoint(page, artifactsDir, "agregar-negocio-modal");

    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }).first(), page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegocios = page.getByText(/Administrar Negocios/i).first();
    if (!(await isVisible(administrarNegocios, 5000))) {
      await clickAndWait(page.getByText(/Mi Negocio/i).first(), page);
    }

    await clickAndWait(page.getByText(/Administrar Negocios/i).first(), page);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 20000 });

    await captureCheckpoint(page, artifactsDir, "administrar-negocios-view", true);
  });

  await runStep("Información General", async () => {
    const generalInfoSection = page
      .locator("section, div")
      .filter({ hasText: /Informaci[oó]n General|BUSINESS PLAN/i })
      .first();
    await expect(generalInfoSection).toBeVisible({ timeout: 12000 });

    const generalInfoText = await generalInfoSection.innerText();
    if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(generalInfoText)) {
      throw new Error("User email is not visible in Información General.");
    }

    const possibleNameLines = generalInfoText
      .split("\n")
      .map((line) => line.trim())
      .filter(
        (line) =>
          line.length > 1 &&
          !line.includes("@") &&
          !/informaci[oó]n general|business plan|cambiar plan/i.test(line)
      );
    if (possibleNameLines.length === 0) {
      throw new Error("User name is not clearly visible in Información General.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 12000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 12000 });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 12000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 12000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 12000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 12000 });
    const businessList = page.locator("ul, [role='list'], table").filter({ hasText: /Negocio|Business/i }).first();
    await expect(businessList).toBeVisible({ timeout: 12000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 12000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 12000 });
  });

  await runStep("Términos y Condiciones", async () => {
    await openLegalLinkAndValidate({
      page,
      linkText: /T[eé]rminos y Condiciones/i,
      expectedHeading: "T[eé]rminos y Condiciones",
      reportUrls,
      reportKey: "Términos y Condiciones",
      artifactsDir
    });
  });

  await runStep("Política de Privacidad", async () => {
    await openLegalLinkAndValidate({
      page,
      linkText: /Pol[ií]tica de Privacidad/i,
      expectedHeading: "Pol[ií]tica de Privacidad",
      reportUrls,
      reportKey: "Política de Privacidad",
      artifactsDir
    });
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    urls: reportUrls,
    failures
  };

  const reportPath = path.join(artifactsDir, "final-report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf-8");

  console.log("Final step report:");
  console.table(report);
  console.log(`Evidence report written to: ${reportPath}`);

  expect(failures, `One or more workflow steps failed. Report: ${reportPath}`).toEqual([]);
});
