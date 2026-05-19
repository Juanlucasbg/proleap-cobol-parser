const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL;

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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
  try {
    await page.waitForLoadState("networkidle", { timeout: 5000 });
  } catch {
    // Apps with active polling/websockets can stay busy forever.
  }
}

async function findVisibleLocator(candidates) {
  for (const locator of candidates) {
    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }
  return null;
}

async function clickByVisibleText(page, text) {
  const pattern = new RegExp(escapeRegExp(text), "i");
  const locator = await findVisibleLocator([
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByRole("menuitem", { name: pattern }),
    page.getByText(pattern),
  ]);

  if (!locator) {
    throw new Error(`No visible element found for text "${text}".`);
  }

  await locator.click();
  await waitForUiLoad(page);
}

async function ensureOnLoginPage(page) {
  if (page.url() === "about:blank") {
    if (!LOGIN_URL) {
      throw new Error(
        "Page is about:blank. Provide SALEADS_LOGIN_URL or start the test with the browser already on the SaleADS login page."
      );
    }

    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
  }

  await waitForUiLoad(page);
}

async function openLegalPageAndValidate({
  page,
  context,
  linkText,
  headingText,
  screenshotPath,
}) {
  const pattern = new RegExp(escapeRegExp(linkText), "i");
  const link = await findVisibleLocator([
    page.getByRole("link", { name: pattern }),
    page.getByRole("button", { name: pattern }),
    page.getByText(pattern),
  ]);

  if (!link) {
    throw new Error(`Could not find legal link/button "${linkText}".`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await link.click();

  const popup = await popupPromise;
  const targetPage = popup || page;
  if (popup) {
    await targetPage.waitForLoadState("domcontentloaded");
  } else {
    await waitForUiLoad(targetPage);
  }

  const headingPattern = new RegExp(escapeRegExp(headingText), "i");
  const heading = await findVisibleLocator([
    targetPage.getByRole("heading", { name: headingPattern }),
    targetPage.getByText(headingPattern),
  ]);

  if (!heading) {
    throw new Error(`Heading "${headingText}" is not visible on legal page.`);
  }

  const legalBodyText = (await targetPage.locator("body").innerText()).trim();
  if (legalBodyText.length < 120) {
    throw new Error(`Legal page "${headingText}" does not expose enough visible content.`);
  }

  await targetPage.screenshot({ path: screenshotPath, fullPage: true });
  const url = targetPage.url();

  if (popup) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  }

  return { url, openedNewTab: Boolean(popup) };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "NOT_RUN", details: "", evidence: [] }])
  );

  const checkpointDir = testInfo.outputPath("checkpoints");
  await fs.mkdir(checkpointDir, { recursive: true });

  const markPass = (field, details, evidence = []) => {
    report[field] = { status: "PASS", details, evidence };
  };

  const markFail = (field, error) => {
    report[field] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error),
      evidence: [],
    };
  };

  try {
    await ensureOnLoginPage(page);
    const loginButton = await findVisibleLocator([
      page.getByRole("button", { name: /google|sign in|iniciar sesión|continuar/i }),
      page.getByRole("link", { name: /google|sign in|iniciar sesión|continuar/i }),
      page.getByText(/google|sign in|iniciar sesión|continuar/i),
    ]);

    if (!loginButton) {
      throw new Error("Google login button is not visible.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUiLoad(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const popupAccount = popup.getByText(GOOGLE_ACCOUNT, { exact: false }).first();
      if (await popupAccount.isVisible().catch(() => false)) {
        await popupAccount.click();
      }
    } else {
      const pageAccount = page.getByText(GOOGLE_ACCOUNT, { exact: false }).first();
      if (await pageAccount.isVisible().catch(() => false)) {
        await pageAccount.click();
        await waitForUiLoad(page);
      }
    }

    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 90000 });
    const sidebar = page
      .locator("aside, nav")
      .filter({ hasText: /Negocio|Mi Negocio/i })
      .first();
    await expect(sidebar).toBeVisible();

    const dashboardShot = path.join(checkpointDir, "01-dashboard-loaded.png");
    await page.screenshot({ path: dashboardShot, fullPage: true });
    markPass("Login", "Dashboard and left sidebar are visible after Google login.", [
      dashboardShot,
    ]);
  } catch (error) {
    markFail("Login", error);
  }

  try {
    await clickByVisibleText(page, "Mi Negocio");
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    const menuShot = path.join(checkpointDir, "02-mi-negocio-expanded.png");
    await page.screenshot({ path: menuShot, fullPage: true });
    markPass("Mi Negocio menu", "Mi Negocio submenu expands and required options are visible.", [
      menuShot,
    ]);
  } catch (error) {
    markFail("Mi Negocio menu", error);
  }

  try {
    await clickByVisibleText(page, "Agregar Negocio");
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    const nameInput = await findVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
    ]);
    if (nameInput) {
      await nameInput.fill("Negocio Prueba Automatizacion");
    }

    const modalShot = path.join(checkpointDir, "03-agregar-negocio-modal.png");
    await page.screenshot({ path: modalShot, fullPage: true });
    await clickByVisibleText(page, "Cancelar");

    markPass("Agregar Negocio modal", "Crear Nuevo Negocio modal was validated and closed.", [
      modalShot,
    ]);
  } catch (error) {
    markFail("Agregar Negocio modal", error);
  }

  try {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, "Mi Negocio");
    }

    await clickByVisibleText(page, "Administrar Negocios");
    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    const accountShot = path.join(checkpointDir, "04-administrar-negocios.png");
    await page.screenshot({ path: accountShot, fullPage: true });
    markPass(
      "Administrar Negocios view",
      "Account page sections are visible: Informacion General, Detalles, Tus Negocios, Seccion Legal.",
      [accountShot]
    );
  } catch (error) {
    markFail("Administrar Negocios view", error);
  }

  try {
    const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    const emailLocator = page.getByText(emailRegex).first();
    await expect(emailLocator).toBeVisible();

    const containerText = (
      await emailLocator
        .locator("xpath=ancestor::*[self::section or self::div][1]")
        .innerText()
        .catch(() => "")
    ).replace(emailRegex, "");
    if (containerText.trim().length < 2) {
      throw new Error("User name text is not visible close to the user email.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    markPass(
      "Información General",
      "User information, email, BUSINESS PLAN and Cambiar Plan button are visible."
    );
  } catch (error) {
    markFail("Información General", error);
  }

  try {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();

    markPass(
      "Detalles de la Cuenta",
      "Cuenta creada, Estado activo and Idioma seleccionado are visible."
    );
  } catch (error) {
    markFail("Detalles de la Cuenta", error);
  }

  try {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const businessCollection = page.locator("li, [role='row'], [data-testid*='business']");
    if ((await businessCollection.count()) < 1) {
      const text = await page.getByText(/Tus Negocios/i).first().locator("xpath=ancestor::*[self::section or self::div][1]").innerText();
      if (text.trim().split("\n").length < 3) {
        throw new Error("Business list content is not visible in Tus Negocios section.");
      }
    }

    markPass("Tus Negocios", "Business list and quota summary are visible.");
  } catch (error) {
    markFail("Tus Negocios", error);
  }

  try {
    const termsShot = path.join(checkpointDir, "08-terminos-y-condiciones.png");
    const terms = await openLegalPageAndValidate({
      page,
      context,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotPath: termsShot,
    });

    markPass(
      "Términos y Condiciones",
      `Legal page validated. URL: ${terms.url}. New tab opened: ${terms.openedNewTab}.`,
      [termsShot]
    );
  } catch (error) {
    markFail("Términos y Condiciones", error);
  }

  try {
    const privacyShot = path.join(checkpointDir, "09-politica-de-privacidad.png");
    const privacy = await openLegalPageAndValidate({
      page,
      context,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotPath: privacyShot,
    });

    markPass(
      "Política de Privacidad",
      `Legal page validated. URL: ${privacy.url}. New tab opened: ${privacy.openedNewTab}.`,
      [privacyShot]
    );
  } catch (error) {
    markFail("Política de Privacidad", error);
  }

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_START");
  console.log(JSON.stringify(finalReport, null, 2));
  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_END");

  const failures = Object.entries(report)
    .filter(([, value]) => value.status !== "PASS")
    .map(([key, value]) => `${key}: ${value.details}`);

  expect(
    failures,
    failures.length ? `Validation failures:\n${failures.join("\n")}` : "All validations passed."
  ).toEqual([]);
});
