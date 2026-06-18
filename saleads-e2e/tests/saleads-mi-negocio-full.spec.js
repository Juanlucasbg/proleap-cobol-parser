const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const CHECKPOINT_DIR = path.join(
  __dirname,
  "..",
  "test-results",
  "saleads-mi-negocio",
  "screenshots"
);
const REPORT_PATH = path.join(
  __dirname,
  "..",
  "test-results",
  "saleads-mi-negocio",
  "report.json"
);

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

function initResultMap() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function ensureOutputDirs() {
  fs.mkdirSync(CHECKPOINT_DIR, { recursive: true });
  fs.mkdirSync(path.dirname(REPORT_PATH), { recursive: true });
}

function toSafeFilename(label) {
  return label
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9-_.]/g, "-")
    .replace(/-+/g, "-")
    .toLowerCase();
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(700);
}

async function takeCheckpoint(page, label, fullPage = false) {
  ensureOutputDirs();
  const file = path.join(CHECKPOINT_DIR, `${toSafeFilename(label)}.png`);
  await page.screenshot({ path: file, fullPage });
}

async function firstVisibleLocator(page, locators) {
  for (const locator of locators) {
    if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
      return locator.first();
    }
  }
  return null;
}

async function clickByText(page, texts) {
  const candidates = [];

  for (const text of texts) {
    const exact = new RegExp(`^${text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`, "i");
    candidates.push(page.getByRole("button", { name: exact }));
    candidates.push(page.getByRole("link", { name: exact }));
    candidates.push(page.getByText(exact));
    candidates.push(page.getByText(new RegExp(text, "i")));
  }

  const target = await firstVisibleLocator(page, candidates);
  if (!target) {
    throw new Error(`No se encontró elemento visible con texto: ${texts.join(", ")}`);
  }

  await target.click();
  await waitForUiToLoad(page);
}

async function validateHeadingAndContent(page, headingText) {
  const heading = await firstVisibleLocator(page, [
    page.getByRole("heading", { name: new RegExp(headingText, "i") }),
    page.getByText(new RegExp(headingText, "i"))
  ]);

  if (!heading) {
    throw new Error(`No se encontró el encabezado legal esperado: ${headingText}`);
  }

  const bodyText = await page.locator("body").innerText();
  if (!bodyText || bodyText.trim().length < 200) {
    throw new Error(`El contenido legal para "${headingText}" parece incompleto.`);
  }
}

async function openAndValidateLegalLink({
  appPage,
  context,
  linkText,
  headingText,
  checkpointName,
  urls
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const sameTabNavigationPromise = appPage
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 8000 })
    .catch(() => null);

  await clickByText(appPage, [linkText]);

  const popupPage = await popupPromise;
  let legalPage = appPage;

  if (popupPage) {
    legalPage = popupPage;
    await legalPage.waitForLoadState("domcontentloaded");
  } else {
    await sameTabNavigationPromise;
    await waitForUiToLoad(legalPage);
  }

  await validateHeadingAndContent(legalPage, headingText);
  urls[headingText] = legalPage.url();
  await takeCheckpoint(legalPage, checkpointName, true);

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);
    return;
  }

  await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUiToLoad(appPage);
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  ensureOutputDirs();

  const results = initResultMap();
  const errors = [];
  const legalUrls = {};

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
  const accountEmail = "juanlucasbarbiergarzon@gmail.com";

  async function runValidation(field, callback) {
    try {
      await callback();
      results[field] = "PASS";
    } catch (error) {
      results[field] = "FAIL";
      errors.push({
        field,
        message: error instanceof Error ? error.message : String(error)
      });
    }
  }

  await runValidation("Login", async () => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Define SALEADS_LOGIN_URL (o SALEADS_URL) para iniciar en la pantalla de login del entorno actual."
      );
    }

    const loginButton = await firstVisibleLocator(page, [
      page.getByRole("button", { name: /sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google|google/i }),
      page.getByText(/sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google/i)
    ]);

    if (!loginButton) {
      throw new Error("No se encontró el botón de login con Google.");
    }

    const googlePopupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

    await loginButton.click();
    await waitForUiToLoad(page);

    const googlePage = await googlePopupPromise;
    const accountPage = googlePage || page;

    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded");
    }

    const accountOption = await firstVisibleLocator(accountPage, [
      accountPage.getByText(new RegExp(accountEmail, "i")),
      accountPage.getByRole("button", { name: new RegExp(accountEmail, "i") }),
      accountPage.getByRole("link", { name: new RegExp(accountEmail, "i") })
    ]);

    if (accountOption) {
      await accountOption.click();
      await waitForUiToLoad(accountPage);
    }

    if (googlePage) {
      await googlePage.waitForClose({ timeout: 15000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUiToLoad(page);

    const sidebar = await firstVisibleLocator(page, [page.locator("aside"), page.locator("nav")]);
    if (!sidebar) {
      throw new Error("La interfaz principal no mostró barra lateral.");
    }

    const negocioText = await firstVisibleLocator(page, [page.getByText(/negocio/i)]);
    if (!negocioText) {
      throw new Error("No se visualizó navegación lateral con la sección Negocio.");
    }

    await takeCheckpoint(page, "step-1-dashboard-load", true);
  });

  await runValidation("Mi Negocio menu", async () => {
    await clickByText(page, ["Negocio"]);
    await clickByText(page, ["Mi Negocio"]);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await takeCheckpoint(page, "step-2-mi-negocio-menu-expanded");
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickByText(page, ["Agregar Negocio"]);

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    await takeCheckpoint(page, "step-3-crear-negocio-modal");

    const nameInput = page
      .getByLabel(/Nombre del Negocio/i)
      .or(page.getByPlaceholder(/Nombre del Negocio/i))
      .or(page.locator("input").first());
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await clickByText(page, ["Cancelar"]);
  });

  await runValidation("Administrar Negocios view", async () => {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible())) {
      await clickByText(page, ["Mi Negocio"]);
    }

    await clickByText(page, ["Administrar Negocios"]);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await takeCheckpoint(page, "step-4-administrar-negocios-page", true);
  });

  await runValidation("Información General", async () => {
    const infoSection = page
      .locator("section,div")
      .filter({ hasText: /Información General/i })
      .first();
    await expect(infoSection).toBeVisible();

    const emailInSection = infoSection
      .getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
      .first();
    await expect(emailInSection).toBeVisible();

    const sectionText = await infoSection.innerText();
    const hasPossibleName = sectionText
      .split("\n")
      .map((line) => line.trim())
      .some(
        (line) =>
          line.length >= 4 &&
          !line.includes("@") &&
          !/Información General|BUSINESS PLAN|Cambiar Plan/i.test(line)
      );

    if (!hasPossibleName) {
      throw new Error("No se identificó un nombre de usuario visible en Información General.");
    }

    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    const detailsSection = page
      .locator("section,div")
      .filter({ hasText: /Detalles de la Cuenta/i })
      .first();
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    const businessSection = page
      .locator("section,div")
      .filter({ hasText: /Tus Negocios/i })
      .first();
    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runValidation("Términos y Condiciones", async () => {
    await openAndValidateLegalLink({
      appPage: page,
      context,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      checkpointName: "step-8-terminos-y-condiciones",
      urls: legalUrls
    });
  });

  await runValidation("Política de Privacidad", async () => {
    await openAndValidateLegalLink({
      appPage: page,
      context,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      checkpointName: "step-9-politica-de-privacidad",
      urls: legalUrls
    });
  });

  const report = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      loginUrl: loginUrl || "browser-current-page"
    },
    results,
    legalUrls,
    errors
  };

  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
  console.log("Final report:", JSON.stringify(report, null, 2));

  const failedValidations = Object.entries(results).filter(([, status]) => status !== "PASS");
  expect(
    failedValidations,
    `Validaciones en FAIL: ${JSON.stringify(failedValidations)}\nErrores: ${JSON.stringify(errors)}`
  ).toEqual([]);
});
