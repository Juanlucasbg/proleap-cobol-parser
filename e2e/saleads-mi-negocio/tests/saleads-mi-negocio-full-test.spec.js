const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REQUIRED_REPORT_FIELDS = [
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

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function buildAccentInsensitiveRegex(text) {
  const accentMap = {
    a: "[aá]",
    e: "[eé]",
    i: "[ií]",
    o: "[oó]",
    u: "[uúü]",
    n: "[nñ]",
  };

  let pattern = "";
  for (const char of text) {
    const lower = char.toLowerCase();
    if (accentMap[lower]) {
      pattern += accentMap[lower];
    } else if (/\s/.test(char)) {
      pattern += "\\s+";
    } else {
      pattern += escapeRegex(char);
    }
  }

  return new RegExp(pattern, "i");
}

async function waitForUi(targetPage) {
  await targetPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => null);
  await targetPage.waitForLoadState("networkidle", { timeout: 4000 }).catch(() => null);
  await targetPage.waitForTimeout(500);
}

async function isVisible(locator, timeout = 2500) {
  return locator.isVisible({ timeout }).catch(() => false);
}

function normalizeName(name) {
  return name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

async function findVisibleLocatorByText(page, textOrRegex) {
  const regex =
    textOrRegex instanceof RegExp ? textOrRegex : buildAccentInsensitiveRegex(textOrRegex);
  const candidates = [
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByRole("menuitem", { name: regex }).first(),
    page.getByRole("tab", { name: regex }).first(),
    page.getByText(regex).first(),
  ];

  for (const locator of candidates) {
    if (await isVisible(locator)) {
      return locator;
    }
  }

  return null;
}

async function clickVisibleText(page, text) {
  const locator = await findVisibleLocatorByText(page, text);
  if (!locator) {
    throw new Error(`Could not find a visible element using text: ${text}`);
  }

  await locator.scrollIntoViewIfNeeded().catch(() => null);
  await locator.click();
  await waitForUi(page);
  return locator;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(
    REQUIRED_REPORT_FIELDS.map((field) => [
      field,
      { status: "FAIL", detail: "Step did not run." },
    ]),
  );

  const evidence = {
    screenshots: [],
    finalUrls: {},
  };

  const checkpoint = async (name, targetPage = page, fullPage = false) => {
    const fileName = `${normalizeName(name)}.png`;
    const outputPath = testInfo.outputPath(fileName);
    await targetPage.screenshot({ path: outputPath, fullPage });
    evidence.screenshots.push({
      name,
      path: outputPath,
    });
  };

  const markPass = (field, detail) => {
    report[field] = { status: "PASS", detail };
  };

  const markFail = async (field, error) => {
    report[field] = {
      status: "FAIL",
      detail: error instanceof Error ? error.message : String(error),
    };
    await checkpoint(`failure-${field}`).catch(() => null);
  };

  const runStep = async (field, callback) => {
    try {
      await callback();
      if (report[field].status !== "FAIL") {
        markPass(field, "Validation completed successfully.");
      }
    } catch (error) {
      await markFail(field, error);
    }
  };

  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_START_URL ||
    process.env.BASE_URL ||
    process.env.APP_URL;

  if (!loginUrl) {
    throw new Error(
      "Missing start URL. Set SALEADS_LOGIN_URL (or SALEADS_START_URL/BASE_URL/APP_URL) to the SaleADS login page.",
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runStep("Login", async () => {
    const loginCandidates = [
      "Sign in with Google",
      "Iniciar sesion con Google",
      "Iniciar sesion",
      "Acceder con Google",
      "Continuar con Google",
      "Google",
    ];

    let loginLocator = null;
    for (const text of loginCandidates) {
      loginLocator = await findVisibleLocatorByText(page, text);
      if (loginLocator) break;
    }

    if (!loginLocator) {
      throw new Error("Could not find the login action for Google sign-in.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await loginLocator.click();
    await waitForUi(page);
    const popup = await popupPromise;

    const authPage = popup || page;
    await waitForUi(authPage);

    const accountOption = authPage.getByText(GOOGLE_ACCOUNT_EMAIL).first();
    if (await isVisible(accountOption, 6000)) {
      await accountOption.click();
      await waitForUi(authPage);
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 45000 }).catch(() => null);
      await page.bringToFront();
      await waitForUi(page);
    }

    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 45000 });
    await expect(page.getByText(/Negocio|Mi Negocio|Dashboard|Inicio/i).first()).toBeVisible({
      timeout: 45000,
    });
    await checkpoint("dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioEntry =
      (await findVisibleLocatorByText(page, "Negocio")) ||
      (await findVisibleLocatorByText(page, "Mi Negocio"));
    if (!negocioEntry) {
      throw new Error("Could not find 'Negocio' or 'Mi Negocio' in the sidebar.");
    }

    await negocioEntry.click();
    await waitForUi(page);

    if (!(await isVisible(page.getByText(/Mi Negocio/i).first()))) {
      await clickVisibleText(page, "Mi Negocio");
    }

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15000 });
    await checkpoint("mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickVisibleText(page, "Agregar Negocio");

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible({ timeout: 15000 });

    const nameByLabel = page.getByLabel(/Nombre del Negocio/i).first();
    const nameByPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i).first();
    const nameInput = (await isVisible(nameByLabel)) ? nameByLabel : nameByPlaceholder;
    await expect(nameInput).toBeVisible({ timeout: 15000 });

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({
      timeout: 15000,
    });
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({
      timeout: 15000,
    });

    await checkpoint("agregar-negocio-modal");

    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatizacion");
    await clickVisibleText(page, "Cancelar");
  });

  await runStep("Administrar Negocios view", async () => {
    if (!(await isVisible(page.getByText(/Administrar Negocios/i).first(), 3000))) {
      if (await isVisible(page.getByText(/Mi Negocio/i).first(), 3000)) {
        await clickVisibleText(page, "Mi Negocio");
      } else {
        await clickVisibleText(page, "Negocio");
        await clickVisibleText(page, "Mi Negocio");
      }
    }

    await clickVisibleText(page, "Administrar Negocios");

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({
      timeout: 20000,
    });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 20000 });

    await checkpoint("administrar-negocios-view", page, true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({
      timeout: 15000,
    });
    await expect(page.getByText(/@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/).first()).toBeVisible({
      timeout: 15000,
    });

    const possibleUserName = page.locator("h1, h2, h3, strong").filter({
      hasNotText: /Informaci[oó]n General|Detalles de la Cuenta|Tus Negocios|Secci[oó]n Legal/i,
    });
    if (!(await isVisible(possibleUserName.first(), 5000))) {
      throw new Error("Could not find a visible user name in Información General.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 15000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({
      timeout: 15000,
    });

    const businessList = page.locator("ul, table, [role='list']").filter({
      hasText: /negocio|business/i,
    });
    if (!(await isVisible(businessList.first(), 5000))) {
      throw new Error("Could not verify a visible business list container.");
    }
  });

  const validateLegalPage = async (field, linkText, headingRegex, contentRegex) => {
    const appUrlBeforeClick = page.url();
    const link = await findVisibleLocatorByText(page, linkText);
    if (!link) {
      throw new Error(`Could not find legal link: ${linkText}`);
    }

    const newPagePromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await link.click();
    await waitForUi(page);

    let legalPage = await newPagePromise;
    const openedNewTab = Boolean(legalPage);

    if (openedNewTab) {
      await waitForUi(legalPage);
    } else {
      legalPage = page;
      await waitForUi(legalPage);
    }

    const headingByRole = legalPage.getByRole("heading", { name: headingRegex }).first();
    if (await isVisible(headingByRole, 8000)) {
      await expect(headingByRole).toBeVisible({ timeout: 15000 });
    } else {
      await expect(legalPage.getByText(headingRegex).first()).toBeVisible({ timeout: 15000 });
    }

    await expect(legalPage.locator("body")).toContainText(contentRegex, { timeout: 20000 });
    await checkpoint(`${field}-legal-page`, legalPage, true);
    evidence.finalUrls[field] = legalPage.url();

    if (openedNewTab) {
      await legalPage.close();
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
  };

  await runStep("Términos y Condiciones", async () => {
    await validateLegalPage(
      "Términos y Condiciones",
      /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
      /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
      /terminos|condiciones|servicio|usuario|uso/i,
    );
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalPage(
      "Política de Privacidad",
      /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
      /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
      /privacidad|datos|informacion|informaci[oó]n|tratamiento/i,
    );
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      startUrl: loginUrl,
    },
    results: report,
    evidence,
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  const failedSteps = Object.entries(report)
    .filter(([, value]) => value.status === "FAIL")
    .map(([key]) => key);

  expect(
    failedSteps,
    `The following steps failed: ${failedSteps.join(", ") || "none"}. See final report artifact.`,
  ).toEqual([]);
});
