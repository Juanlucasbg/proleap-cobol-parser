const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const screenshotDir = path.join(__dirname, "..", "artifacts", "screenshots");

async function ensureScreenshotDir() {
  await fs.mkdir(screenshotDir, { recursive: true });
}

async function checkpoint(page, name, fullPage = false) {
  await ensureScreenshotDir();
  const file = path.join(screenshotDir, `${Date.now()}-${name}.png`);
  await page.screenshot({ path: file, fullPage });
  return file;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
}

function normalizeUrl(url) {
  if (!url) {
    return null;
  }

  const hasProtocol = /^https?:\/\//i.test(url);
  return hasProtocol ? url : `https://${url}`;
}

async function clickByText(page, texts, options = {}) {
  const timeout = options.timeout ?? 10_000;
  const exact = options.exact ?? false;

  for (const text of texts) {
    const locator = page.getByText(text, { exact });
    const count = await locator.count();
    if (count > 0 && (await locator.first().isVisible())) {
      await locator.first().click();
      return text;
    }
  }

  for (const text of texts) {
    const roleLocator = page.getByRole("button", { name: new RegExp(text, "i") });
    const roleCount = await roleLocator.count();
    if (roleCount > 0 && (await roleLocator.first().isVisible())) {
      await roleLocator.first().click({ timeout });
      return text;
    }
  }

  throw new Error(`Could not click any target text: ${texts.join(", ")}`);
}

async function clickLegalLinkAndValidate({
  page,
  linkPattern,
  expectedHeadingPattern,
  screenshotName,
  report,
  reportKey
}) {
  const context = page.context();
  const previousPageCount = context.pages().length;

  const targetLink = page.getByRole("link", { name: linkPattern }).first();
  await expect(targetLink).toBeVisible();

  const popupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
  await targetLink.click();

  await waitForUi(page);

  const popup = await popupPromise;
  let legalPage = page;
  let openedInNewTab = false;

  if (popup) {
    legalPage = popup;
    openedInNewTab = true;
  } else {
    const pagesAfterClick = context.pages();
    openedInNewTab = pagesAfterClick.length > previousPageCount;
    if (openedInNewTab) {
      legalPage = pagesAfterClick[pagesAfterClick.length - 1];
    }
  }

  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForLoadState("networkidle");

  await expect(legalPage.getByRole("heading", { name: expectedHeadingPattern })).toBeVisible();
  await expect(
    legalPage.locator("main, article, section, body").filter({ hasText: /t[eé]rminos|condiciones|privacidad|datos|uso/i }).first()
  ).toBeVisible();

  const legalScreenshot = await checkpoint(legalPage, screenshotName, true);
  const legalUrl = legalPage.url();

  report[reportKey] = {
    status: "PASS",
    newTab: openedInNewTab,
    finalUrl: legalUrl,
    screenshot: legalScreenshot
  };

  if (openedInNewTab && legalPage !== page) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUi(page);
  }
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("Login with Google and validate Mi Negocio module", async ({ page }) => {
    const report = {
      Login: { status: "FAIL" },
      "Mi Negocio menu": { status: "FAIL" },
      "Agregar Negocio modal": { status: "FAIL" },
      "Administrar Negocios view": { status: "FAIL" },
      "Información General": { status: "FAIL" },
      "Detalles de la Cuenta": { status: "FAIL" },
      "Tus Negocios": { status: "FAIL" },
      "Términos y Condiciones": { status: "FAIL" },
      "Política de Privacidad": { status: "FAIL" }
    };

    try {
      // Step 1: Login with Google (browser is assumed to already be on login page).
      // If not, we optionally navigate with SALEADS_URL/BASE_URL for portability.
      const configuredUrl = normalizeUrl(process.env.SALEADS_URL || process.env.BASE_URL);
      if (configuredUrl && !/login|auth|signin|saleads/i.test(page.url())) {
        await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
      }
      await waitForUi(page);

      await clickByText(page, [
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Sign in with Google",
        "Login with Google"
      ]);
      await waitForUi(page);

      const googleAccountOption = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
      if (await googleAccountOption.isVisible().catch(() => false)) {
        await googleAccountOption.click();
        await waitForUi(page);
      }

      await expect(page.locator("aside")).toBeVisible();
      await expect(page.getByText("Negocio", { exact: false })).toBeVisible();
      const dashboardShot = await checkpoint(page, "01-dashboard-loaded");
      report.Login = { status: "PASS", screenshot: dashboardShot };

      // Step 2: Open Mi Negocio menu.
      await clickByText(page, ["Negocio"]);
      await waitForUi(page);
      await clickByText(page, ["Mi Negocio", "Mi negocio"]);
      await waitForUi(page);
      await expect(page.getByText("Agregar Negocio", { exact: false })).toBeVisible();
      await expect(page.getByText("Administrar Negocios", { exact: false })).toBeVisible();
      const menuShot = await checkpoint(page, "02-mi-negocio-expanded");
      report["Mi Negocio menu"] = { status: "PASS", screenshot: menuShot };

      // Step 3: Validate Agregar Negocio modal.
      await clickByText(page, ["Agregar Negocio"]);
      await waitForUi(page);

      await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible();
      const nombreInputCandidates = [
        page.getByLabel("Nombre del Negocio"),
        page.getByPlaceholder("Nombre del Negocio"),
        page.locator("input[name*='nombre' i], input[id*='nombre' i]").first()
      ];
      let nombreInput = null;
      for (const candidate of nombreInputCandidates) {
        if (await candidate.isVisible().catch(() => false)) {
          nombreInput = candidate;
          break;
        }
      }
      if (!nombreInput) {
        throw new Error("No visible input found for 'Nombre del Negocio'.");
      }

      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      await nombreInput.click();
      await nombreInput.fill("Negocio Prueba Automatización");
      const modalShot = await checkpoint(page, "03-agregar-negocio-modal");

      await page.getByRole("button", { name: /Cancelar/i }).click();
      await waitForUi(page);
      report["Agregar Negocio modal"] = { status: "PASS", screenshot: modalShot };

      // Step 4: Open Administrar Negocios.
      const adminNegociosEntry = page.getByText("Administrar Negocios", { exact: false }).first();
      if (!(await adminNegociosEntry.isVisible().catch(() => false))) {
        await clickByText(page, ["Mi Negocio", "Negocio"]);
        await waitForUi(page);
      }

      await clickByText(page, ["Administrar Negocios"]);
      await waitForUi(page);

      await expect(page.getByText("Información General", { exact: false })).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta", { exact: false })).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: false })).toBeVisible();
      await expect(page.getByText("Sección Legal", { exact: false })).toBeVisible();
      const adminShot = await checkpoint(page, "04-administrar-negocios", true);
      report["Administrar Negocios view"] = { status: "PASS", screenshot: adminShot };

      // Step 5: Validate Información General.
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      const emailMatcher = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-z]{2,}/i;
      await expect(page.getByText(emailMatcher).first()).toBeVisible();
      await expect(page.locator("h1,h2,h3,p,span,div").filter({ hasText: /[a-zA-Z]/ }).first()).toBeVisible();
      report["Información General"] = { status: "PASS" };

      // Step 6: Validate Detalles de la Cuenta.
      await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible();
      await expect(page.getByText("Estado activo", { exact: false })).toBeVisible();
      await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible();
      report["Detalles de la Cuenta"] = { status: "PASS" };

      // Step 7: Validate Tus Negocios.
      await expect(page.getByText("Tus Negocios", { exact: false })).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
      report["Tus Negocios"] = { status: "PASS" };

      // Step 8: Validate Términos y Condiciones.
      await clickLegalLinkAndValidate({
        page,
        linkPattern: /T[eé]rminos y Condiciones/i,
        expectedHeadingPattern: /T[eé]rminos y Condiciones/i,
        screenshotName: "05-terminos-condiciones",
        report,
        reportKey: "Términos y Condiciones"
      });

      // Step 9: Validate Política de Privacidad.
      await clickLegalLinkAndValidate({
        page,
        linkPattern: /Pol[ií]tica de Privacidad/i,
        expectedHeadingPattern: /Pol[ií]tica de Privacidad/i,
        screenshotName: "06-politica-privacidad",
        report,
        reportKey: "Política de Privacidad"
      });

      await ensureScreenshotDir();
      const reportPath = path.join(__dirname, "..", "artifacts", "final-report.json");
      await fs.mkdir(path.dirname(reportPath), { recursive: true });
      await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf-8");
    } catch (error) {
      const reportPath = path.join(__dirname, "..", "artifacts", "final-report.json");
      await fs.mkdir(path.dirname(reportPath), { recursive: true });
      await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf-8");
      throw error;
    }
  });
});
