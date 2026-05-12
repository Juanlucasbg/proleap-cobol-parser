const fs = require("fs");
const path = require("path");
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

const SCREENSHOT_DIR = path.join(
  process.cwd(),
  "test-results",
  "saleads-mi-negocio",
  "screenshots",
);
const REPORT_PATH = path.join(
  process.cwd(),
  "test-results",
  "saleads-mi-negocio",
  "final-report.json",
);

function ensureArtifactsDir() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function initReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: "" };
    return acc;
  }, {});
}

async function waitForUiLoad(page) {
  await Promise.race([
    page.waitForLoadState("domcontentloaded", { timeout: 6000 }),
    page.waitForTimeout(1200),
  ]);
  await page.waitForTimeout(400);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible({ timeout: 20000 });
  await locator.click();
  await waitForUiLoad(page);
}

async function firstVisible(locators) {
  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }
  return null;
}

async function saveCheckpoint(page, fileName, fullPage = false) {
  ensureArtifactsDir();
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, fileName),
    fullPage,
  });
}

async function validateLegalPage({
  page,
  linkText,
  headingText,
  screenshotName,
}) {
  const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const legalLink = await firstVisible([
    page.getByRole("link", { name: linkText, exact: true }),
    page.getByText(linkText, { exact: true }),
  ]);

  if (!legalLink) {
    throw new Error(`No se encontró el enlace legal "${linkText}".`);
  }

  await clickAndWait(legalLink, page);

  const popup = await popupPromise;
  const legalPage = popup || page;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 45000 });

  const heading = await firstVisible([
    legalPage.getByRole("heading", { name: headingText }),
    legalPage.getByText(headingText, { exact: false }),
  ]);

  if (!heading) {
    throw new Error(`No se encontró el encabezado "${headingText}".`);
  }

  await expect(heading).toBeVisible({ timeout: 20000 });

  const legalContent = legalPage.locator("p, li").first();
  await expect(legalContent).toBeVisible({ timeout: 20000 });

  await saveCheckpoint(legalPage, screenshotName);

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 15000 }).catch(() => null);
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_APP_URL ||
    process.env.BASE_URL;

  if (!loginUrl) {
    throw new Error(
      "Debes definir SALEADS_LOGIN_URL (o SALEADS_APP_URL / BASE_URL) para iniciar en la pantalla de login del ambiente actual.",
    );
  }

  const report = initReport();
  const failures = [];

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiLoad(page);

  async function runStep(stepName, stepFn) {
    try {
      await stepFn();
      report[stepName] = { status: "PASS", details: "" };
    } catch (error) {
      report[stepName] = { status: "FAIL", details: error.message };
      failures.push(`${stepName}: ${error.message}`);
    }
  }

  await runStep("Login", async () => {
    const sidebarCandidate = page.locator("aside, nav").filter({ hasText: /negocio/i }).first();

    if (!(await sidebarCandidate.isVisible().catch(() => false))) {
      const loginButton = await firstVisible([
        page.getByRole("button", {
          name: /sign in with google|iniciar sesión con google|continuar con google|google/i,
        }),
        page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
      ]);

      if (!loginButton) {
        throw new Error("No se encontró el botón de login con Google.");
      }

      const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickAndWait(loginButton, page);
      const googlePopup = await popupPromise;

      const googlePage = googlePopup || page;
      const accountOption = googlePage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(accountOption, googlePage);
      }

      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => null);
        await googlePopup.close().catch(() => null);
        await page.bringToFront();
      }
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 60000 });
    await saveCheckpoint(page, "01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioItem = await firstVisible([
      page.getByText("Negocio", { exact: true }),
      page.getByRole("link", { name: "Negocio", exact: true }),
      page.getByRole("button", { name: "Negocio", exact: true }),
    ]);

    if (negocioItem) {
      await clickAndWait(negocioItem, page);
    }

    const miNegocioItem = await firstVisible([
      page.getByText("Mi Negocio", { exact: true }),
      page.getByRole("link", { name: "Mi Negocio", exact: true }),
      page.getByRole("button", { name: "Mi Negocio", exact: true }),
    ]);

    if (!miNegocioItem) {
      throw new Error("No se encontró la opción Mi Negocio.");
    }

    await clickAndWait(miNegocioItem, page);

    await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible({ timeout: 20000 });
    await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible({ timeout: 20000 });
    await saveCheckpoint(page, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickAndWait(page.getByText("Agregar Negocio", { exact: true }).first(), page);

    await expect(page.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible({ timeout: 20000 });
    await expect(page.getByLabel("Nombre del Negocio")).toBeVisible({ timeout: 20000 });
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: "Cancelar", exact: true })).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: "Crear Negocio", exact: true })).toBeVisible({
      timeout: 20000,
    });

    const businessNameInput = page.getByLabel("Nombre del Negocio");
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: "Cancelar", exact: true }), page);

    await saveCheckpoint(page, "03-agregar-negocio-modal.png");
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocioItem = await firstVisible([
      page.getByText("Mi Negocio", { exact: true }),
      page.getByRole("link", { name: "Mi Negocio", exact: true }),
      page.getByRole("button", { name: "Mi Negocio", exact: true }),
    ]);

    if (miNegocioItem) {
      await clickAndWait(miNegocioItem, page);
    }

    await clickAndWait(page.getByText("Administrar Negocios", { exact: true }).first(), page);

    await expect(page.getByText("Información General", { exact: true })).toBeVisible({ timeout: 30000 });
    await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible({ timeout: 30000 });
    await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible({ timeout: 30000 });
    await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible({ timeout: 30000 });
    await saveCheckpoint(page, "04-administrar-negocios.png", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/@/).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText("BUSINESS PLAN", { exact: false })).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: "Cambiar Plan", exact: true })).toBeVisible({
      timeout: 20000,
    });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible({ timeout: 20000 });
    await expect(page.getByText("Estado activo", { exact: false })).toBeVisible({ timeout: 20000 });
    await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible({ timeout: 20000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: "Agregar Negocio", exact: true })).toBeVisible({
      timeout: 20000,
    });
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible({ timeout: 20000 });
  });

  await runStep("Términos y Condiciones", async () => {
    const finalUrl = await validateLegalPage({
      page,
      linkText: "Términos y Condiciones",
      headingText: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
    });

    report["Términos y Condiciones"].details = `URL final: ${finalUrl}`;
  });

  await runStep("Política de Privacidad", async () => {
    const finalUrl = await validateLegalPage({
      page,
      linkText: "Política de Privacidad",
      headingText: /Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
    });

    report["Política de Privacidad"].details = `URL final: ${finalUrl}`;
  });

  ensureArtifactsDir();
  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");

  console.log("Final report:");
  console.table(
    Object.fromEntries(
      Object.entries(report).map(([key, value]) => [key, value.status]),
    ),
  );
  console.log(`Detailed report saved at: ${REPORT_PATH}`);

  expect(
    failures,
    `Se encontraron validaciones fallidas:\n${failures.join("\n")}`,
  ).toEqual([]);
});
