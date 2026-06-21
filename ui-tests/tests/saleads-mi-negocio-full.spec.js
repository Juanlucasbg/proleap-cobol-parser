const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function firstVisibleLocator(candidates) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    const isVisible = await locator.isVisible({ timeout: 2500 }).catch(() => false);
    if (isVisible) {
      return locator;
    }
  }
  return null;
}

async function clickByVisibleText(page, text) {
  const matcher = new RegExp(escapeRegExp(text), "i");
  const locator = await firstVisibleLocator([
    page.getByRole("button", { name: matcher }),
    page.getByRole("link", { name: matcher }),
    page.getByRole("menuitem", { name: matcher }),
    page.getByRole("tab", { name: matcher }),
    page.getByText(matcher)
  ]);

  if (!locator) {
    throw new Error(`No visible element found by text: ${text}`);
  }

  await locator.click();
  await waitForUiLoad(page);
}

async function clickFirstMatchingText(page, texts) {
  let lastError;
  for (const text of texts) {
    try {
      await clickByVisibleText(page, text);
      return text;
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError || new Error(`No visible element found for texts: ${texts.join(", ")}`);
}

async function ensureVisibleByText(page, text) {
  await expect(page.getByText(new RegExp(escapeRegExp(text), "i")).first()).toBeVisible();
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = {};
  const legalUrls = {};

  const setResult = (field, status, details = "") => {
    report[field] = {
      status,
      details
    };
  };

  const runValidation = async (field, callback) => {
    try {
      await callback();
      setResult(field, "PASS");
    } catch (error) {
      setResult(field, "FAIL", error.message);
    }
  };

  const screenshot = async (name, currentPage = page, fullPage = false) => {
    await currentPage.screenshot({
      path: testInfo.outputPath(name),
      fullPage
    });
  };

  await runValidation("Login", async () => {
    if (process.env.SALEADS_START_URL) {
      await page.goto(process.env.SALEADS_START_URL, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    } else if (page.url() === "about:blank") {
      throw new Error("SALEADS_START_URL is required when starting from a blank Playwright page.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickFirstMatchingText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Acceder con Google",
      "Continuar con Google",
      "Google"
    ]);
    const googlePage = await popupPromise;

    if (googlePage) {
      await waitForUiLoad(googlePage);
      const accountLocator = await firstVisibleLocator([
        googlePage.getByRole("button", { name: new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i") }),
        googlePage.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i"))
      ]);
      if (accountLocator) {
        await accountLocator.click();
      }
      await googlePage.waitForEvent("close", { timeout: 45000 }).catch(() => {});
    } else {
      const inlineAccount = await firstVisibleLocator([
        page.getByRole("button", { name: new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i") }),
        page.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i"))
      ]);
      if (inlineAccount) {
        await inlineAccount.click();
      }
    }

    await page.bringToFront();
    await waitForUiLoad(page);

    await ensureVisibleByText(page, "Negocio");
    const sidebarVisible = await firstVisibleLocator([
      page.locator("aside"),
      page.locator("nav").filter({ hasText: /Negocio/i }),
      page.getByText(/Negocio/i)
    ]);
    if (!sidebarVisible) {
      throw new Error("Main app sidebar was not visible after login.");
    }

    await screenshot("01-dashboard-loaded.png");
  });

  await runValidation("Mi Negocio menu", async () => {
    await clickFirstMatchingText(page, ["Negocio", "Mi Negocio"]);
    await clickByVisibleText(page, "Mi Negocio");
    await ensureVisibleByText(page, "Agregar Negocio");
    await ensureVisibleByText(page, "Administrar Negocios");
    await screenshot("02-mi-negocio-menu-expanded.png");
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, "Agregar Negocio");
    await ensureVisibleByText(page, "Crear Nuevo Negocio");

    const nameInput = await firstVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[name*='nombre' i], input[id*='nombre' i]")
    ]);
    if (!nameInput) {
      throw new Error("Nombre del Negocio input was not found.");
    }

    await ensureVisibleByText(page, "Tienes 2 de 3 negocios");
    await ensureVisibleByText(page, "Cancelar");
    await ensureVisibleByText(page, "Crear Negocio");

    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await screenshot("03-agregar-negocio-modal.png");
    await clickByVisibleText(page, "Cancelar");
  });

  await runValidation("Administrar Negocios view", async () => {
    const adminMenuVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!adminMenuVisible) {
      await clickByVisibleText(page, "Mi Negocio");
    }

    await clickByVisibleText(page, "Administrar Negocios");
    await ensureVisibleByText(page, "Información General");
    await ensureVisibleByText(page, "Detalles de la Cuenta");
    await ensureVisibleByText(page, "Tus Negocios");
    await ensureVisibleByText(page, "Sección Legal");
    await screenshot("04-administrar-negocios-page.png", page, true);
  });

  await runValidation("Información General", async () => {
    await ensureVisibleByText(page, "Información General");
    await ensureVisibleByText(page, "BUSINESS PLAN");
    await ensureVisibleByText(page, "Cambiar Plan");

    const userNameVisible = await firstVisibleLocator([
      page.getByText(/Nombre/i),
      page.getByLabel(/Nombre/i)
    ]);
    if (!userNameVisible) {
      throw new Error("User name field/label was not visible.");
    }

    const userEmailVisible = await firstVisibleLocator([
      page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
      page.locator("input[type='email']")
    ]);
    if (!userEmailVisible) {
      throw new Error("User email was not visible.");
    }
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await ensureVisibleByText(page, "Cuenta creada");
    await ensureVisibleByText(page, "Estado activo");
    await ensureVisibleByText(page, "Idioma seleccionado");
  });

  await runValidation("Tus Negocios", async () => {
    await ensureVisibleByText(page, "Tus Negocios");
    await ensureVisibleByText(page, "Agregar Negocio");
    await ensureVisibleByText(page, "Tienes 2 de 3 negocios");

    const businessListVisible = await firstVisibleLocator([
      page.locator("section,div").filter({ hasText: /Tus Negocios/i }).locator("li"),
      page.locator("section,div").filter({ hasText: /Tus Negocios/i }).locator("[role='row']"),
      page.locator("section,div").filter({ hasText: /Tus Negocios/i }).locator("[class*='business' i]")
    ]);
    if (!businessListVisible) {
      throw new Error("Business list was not visible inside Tus Negocios.");
    }
  });

  const validateLegalLink = async (field, linkText, expectedHeading, screenshotName) => {
    await runValidation(field, async () => {
      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickByVisibleText(page, linkText);
      const legalPage = (await popupPromise) || page;

      await waitForUiLoad(legalPage);

      const headingVisible = await firstVisibleLocator([
        legalPage.getByRole("heading", { name: new RegExp(escapeRegExp(expectedHeading), "i") }),
        legalPage.getByText(new RegExp(escapeRegExp(expectedHeading), "i"))
      ]);
      if (!headingVisible) {
        throw new Error(`Heading "${expectedHeading}" was not visible.`);
      }

      const contentVisible = await firstVisibleLocator([
        legalPage.locator("article p"),
        legalPage.locator("main p"),
        legalPage.locator("p"),
        legalPage.locator("li")
      ]);
      if (!contentVisible) {
        throw new Error("Legal content text was not visible.");
      }

      legalUrls[field] = legalPage.url();
      await screenshot(screenshotName, legalPage, true);

      if (legalPage !== page) {
        await legalPage.close();
        await page.bringToFront();
        await waitForUiLoad(page);
      } else {
        await page.goBack().catch(() => {});
        await waitForUiLoad(page);
      }
    });
  };

  await validateLegalLink(
    "Términos y Condiciones",
    "Términos y Condiciones",
    "Términos y Condiciones",
    "05-terminos-y-condiciones.png"
  );

  await validateLegalLink(
    "Política de Privacidad",
    "Política de Privacidad",
    "Política de Privacidad",
    "06-politica-de-privacidad.png"
  );

  for (const field of REPORT_FIELDS) {
    if (!report[field]) {
      setResult(field, "FAIL", "Step was skipped.");
    }
  }

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    summary: REPORT_FIELDS.reduce((acc, field) => {
      acc[field] = report[field];
      return acc;
    }, {}),
    evidence: {
      termsAndConditionsUrl: legalUrls["Términos y Condiciones"] || "",
      privacyPolicyUrl: legalUrls["Política de Privacidad"] || ""
    }
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json"
  });

  const failed = REPORT_FIELDS.filter((field) => report[field].status !== "PASS");
  console.log("Final workflow report:", JSON.stringify(finalReport, null, 2));
  expect(failed, `Workflow failures: ${failed.join(", ")}`).toEqual([]);
});
