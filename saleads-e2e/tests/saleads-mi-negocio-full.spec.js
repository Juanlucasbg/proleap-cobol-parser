const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const REPORT_FIELDS = [
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

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(900);
}

async function firstVisibleByText(page, textOptions) {
  for (const text of textOptions) {
    const matcher = new RegExp(escapeRegExp(text), "i");
    const candidates = [
      page.getByRole("button", { name: matcher }),
      page.getByRole("link", { name: matcher }),
      page.getByRole("menuitem", { name: matcher }),
      page.getByRole("tab", { name: matcher }),
      page.getByText(matcher)
    ];

    for (const candidate of candidates) {
      const node = candidate.first();
      if (await node.isVisible().catch(() => false)) {
        return node;
      }
    }
  }

  return null;
}

async function clickByVisibleText(page, textOptions, stepDescription) {
  const locator = await firstVisibleByText(page, textOptions);
  if (!locator) {
    throw new Error(`Unable to locate visible target for: ${stepDescription}`);
  }

  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click({ timeout: 15000 });
  await waitForUiLoad(page);
}

async function assertVisibleText(page, text) {
  await expect(page.getByText(new RegExp(escapeRegExp(text), "i")).first()).toBeVisible();
}

async function saveCheckpoint(page, artifactsDir, fileName, fullPage = false) {
  const targetPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: targetPath, fullPage });
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (!loginUrl) {
    throw new Error("Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to run this environment-agnostic test.");
  }

  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.resolve(__dirname, "..", "artifacts", timestamp);
  await fs.mkdir(artifactsDir, { recursive: true });

  const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const errors = {};
  const legalUrls = {};

  async function runSection(field, fn) {
    try {
      await fn();
      results[field] = "PASS";
    } catch (error) {
      errors[field] = error instanceof Error ? error.message : String(error);
      results[field] = "FAIL";
    }
  }

  async function openLegalPageAndValidate(linkText, headingText, screenshotName, urlField) {
    const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickByVisibleText(page, [linkText], `${linkText} link click`);

    const popup = await popupPromise;
    const legalPage = popup || page;

    await waitForUiLoad(legalPage);
    await expect(legalPage.getByRole("heading", { name: new RegExp(escapeRegExp(headingText), "i") })).toBeVisible();

    const bodyText = await legalPage.locator("body").innerText();
    expect(bodyText.trim().length).toBeGreaterThan(120);

    await saveCheckpoint(legalPage, artifactsDir, screenshotName, true);
    legalUrls[urlField] = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUiLoad(page);
      return;
    }

    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiLoad(page);
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiLoad(page);

  await runSection("Login", async () => {
    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await clickByVisibleText(
      page,
      ["Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"],
      "login with Google button"
    );

    const popup = await popupPromise;
    const authPage = popup || page;
    await waitForUiLoad(authPage);

    const chooseAccount = await firstVisibleByText(authPage, ["juanlucasbarbiergarzon@gmail.com"]);
    if (chooseAccount) {
      await chooseAccount.click({ timeout: 12000 });
      await waitForUiLoad(authPage);
    }

    if (popup) {
      await popup.waitForClose({ timeout: 20000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUiLoad(page);
    const sidebarVisible =
      (await page.locator("aside, nav").first().isVisible().catch(() => false)) ||
      (await firstVisibleByText(page, ["Negocio", "Mi Negocio"]).then((v) => Boolean(v)));
    expect(sidebarVisible).toBeTruthy();
    await saveCheckpoint(page, artifactsDir, "01-dashboard-loaded.png", true);
  });

  await runSection("Mi Negocio menu", async () => {
    await clickByVisibleText(page, ["Negocio", "Mi Negocio"], "Negocio section");
    const maybeMiNegocio = await firstVisibleByText(page, ["Mi Negocio"]);
    if (maybeMiNegocio) {
      await maybeMiNegocio.click({ timeout: 10000 }).catch(() => {});
      await waitForUiLoad(page);
    }

    await assertVisibleText(page, "Agregar Negocio");
    await assertVisibleText(page, "Administrar Negocios");
    await saveCheckpoint(page, artifactsDir, "02-mi-negocio-menu-expanded.png");
  });

  await runSection("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, ["Agregar Negocio"], "Agregar Negocio open modal");
    await assertVisibleText(page, "Crear Nuevo Negocio");
    await assertVisibleText(page, "Nombre del Negocio");
    await assertVisibleText(page, "Tienes 2 de 3 negocios");
    await assertVisibleText(page, "Cancelar");
    await assertVisibleText(page, "Crear Negocio");
    await saveCheckpoint(page, artifactsDir, "03-agregar-negocio-modal.png");

    const input = page.getByLabel(new RegExp(escapeRegExp("Nombre del Negocio"), "i")).first();
    if (await input.isVisible().catch(() => false)) {
      await input.click();
      await input.fill("Negocio Prueba Automatización");
    }

    await clickByVisibleText(page, ["Cancelar"], "close Crear Nuevo Negocio modal");
  });

  await runSection("Administrar Negocios view", async () => {
    const administrarVisible = await firstVisibleByText(page, ["Administrar Negocios"]);
    if (!administrarVisible) {
      await clickByVisibleText(page, ["Mi Negocio", "Negocio"], "re-open Mi Negocio menu");
    }

    await clickByVisibleText(page, ["Administrar Negocios"], "Administrar Negocios navigation");
    await assertVisibleText(page, "Información General");
    await assertVisibleText(page, "Detalles de la Cuenta");
    await assertVisibleText(page, "Tus Negocios");
    await assertVisibleText(page, "Sección Legal");
    await saveCheckpoint(page, artifactsDir, "04-administrar-negocios-view-full.png", true);
  });

  await runSection("Información General", async () => {
    await assertVisibleText(page, "Información General");
    await assertVisibleText(page, "juanlucasbarbiergarzon@gmail.com");
    await assertVisibleText(page, "BUSINESS PLAN");
    await assertVisibleText(page, "Cambiar Plan");

    const sectionText = await page.locator("body").innerText();
    const nonEmailProfileLine = sectionText
      .split("\n")
      .map((line) => line.trim())
      .find((line) => line.length > 2 && !line.includes("@") && !/BUSINESS PLAN|Cambiar Plan|Información General/i.test(line));
    expect(Boolean(nonEmailProfileLine)).toBeTruthy();
  });

  await runSection("Detalles de la Cuenta", async () => {
    await assertVisibleText(page, "Cuenta creada");
    await assertVisibleText(page, "Estado activo");
    await assertVisibleText(page, "Idioma seleccionado");
  });

  await runSection("Tus Negocios", async () => {
    await assertVisibleText(page, "Tus Negocios");
    await assertVisibleText(page, "Agregar Negocio");
    await assertVisibleText(page, "Tienes 2 de 3 negocios");
  });

  await runSection("Términos y Condiciones", async () => {
    await openLegalPageAndValidate(
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05-terminos-y-condiciones.png",
      "terminosYCondicionesUrl"
    );
  });

  await runSection("Política de Privacidad", async () => {
    await openLegalPageAndValidate(
      "Política de Privacidad",
      "Política de Privacidad",
      "06-politica-de-privacidad.png",
      "politicaDePrivacidadUrl"
    );
  });

  const report = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    loginUrl,
    results,
    legalUrls,
    errors
  };

  const reportPath = path.join(artifactsDir, "final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

  if (Object.values(results).includes("FAIL")) {
    throw new Error(`One or more SaleADS validations failed. See report: ${reportPath}`);
  }
});
