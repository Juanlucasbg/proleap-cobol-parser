const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ||
  "juanlucasbarbiergarzon@gmail.com";
const SALEADS_BASE_URL = process.env.SALEADS_BASE_URL;

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

const ARTIFACTS_DIR = path.resolve(
  process.env.SALEADS_ARTIFACTS_DIR || path.join(__dirname, "..", "artifacts"),
);
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.resolve(
  process.env.SALEADS_REPORT_PATH ||
    path.join(ARTIFACTS_DIR, "saleads_mi_negocio_full_test_report.json"),
);

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function slug(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function screenshot(page, name, fullPage = false) {
  ensureDir(SCREENSHOTS_DIR);
  const filePath = path.join(
    SCREENSHOTS_DIR,
    `${new Date().toISOString().replace(/[:.]/g, "-")}-${name}.png`,
  );
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function firstVisible(page, candidates, timeoutMs = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const locator of candidates) {
      const item = locator.first();
      try {
        if ((await item.count()) > 0 && (await item.isVisible())) {
          return item;
        }
      } catch (_error) {
        // keep probing candidates
      }
    }
    await page.waitForTimeout(250);
  }
  return null;
}

function emptyReport() {
  const steps = {};
  for (const field of REPORT_FIELDS) {
    steps[field] = { status: "NOT_RUN" };
  }
  return {
    name: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate Mi Negocio module.",
    generatedAt: null,
    environment: {
      saleadsBaseUrl: SALEADS_BASE_URL || null,
      googleAccount: GOOGLE_ACCOUNT_EMAIL,
    },
    evidence: {
      screenshotsDirectory: SCREENSHOTS_DIR,
      termsUrl: null,
      privacyUrl: null,
    },
    steps,
    failures: [],
  };
}

async function validateLegalPage(page, linkRegex, headingRegex, screenshotName) {
  const link = await firstVisible(page, [
    page.getByRole("link", { name: linkRegex }),
    page.getByRole("button", { name: linkRegex }),
    page.getByText(linkRegex),
  ]);
  if (!link) {
    throw new Error(`Could not locate legal link: ${String(linkRegex)}`);
  }

  const context = page.context();
  const currentUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await link.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const targetPage = popup || page;
  await targetPage.waitForLoadState("domcontentloaded", { timeout: 30000 });

  const title = await firstVisible(targetPage, [
    targetPage.getByRole("heading", { name: headingRegex }),
    targetPage.getByText(headingRegex),
  ]);
  if (!title) {
    throw new Error(`Heading not found: ${String(headingRegex)}`);
  }

  const legalTextBlocks = targetPage.locator("main p, article p, section p, p");
  expect(await legalTextBlocks.count()).toBeGreaterThan(0);

  await screenshot(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== currentUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  ensureDir(ARTIFACTS_DIR);
  ensureDir(SCREENSHOTS_DIR);

  const report = emptyReport();
  const failures = [];

  async function runStep(field, callback) {
    try {
      await callback();
      report.steps[field] = { status: "PASS" };
    } catch (error) {
      report.steps[field] = {
        status: "FAIL",
        reason: error instanceof Error ? error.message : String(error),
      };
      failures.push(field);
      report.failures.push({
        field,
        reason: error instanceof Error ? error.message : String(error),
      });
      await screenshot(page, `${slug(field)}-failure`, true).catch(() => {});
    }
  }

  await runStep("Login", async () => {
    if (SALEADS_BASE_URL) {
      await page.goto(SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    if (page.url() === "about:blank") {
      throw new Error(
        "Missing SALEADS_BASE_URL. For standalone runs, provide the login URL of your target environment.",
      );
    }

    const loginButton = await firstVisible(page, [
      page.getByRole("button", { name: /sign in with google|google|iniciar sesi[oó]n/i }),
      page.getByRole("link", { name: /sign in with google|google|iniciar sesi[oó]n/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
    ]);
    if (!loginButton) {
      throw new Error("Login button or 'Sign in with Google' was not found.");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 30000 });
      const accountOption = await firstVisible(popup, [
        popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        popup.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
        popup.getByRole("link", { name: GOOGLE_ACCOUNT_EMAIL }),
      ], 8000);
      if (accountOption) {
        await accountOption.click();
      }
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const accountOption = await firstVisible(page, [
        page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        page.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
      ], 5000);
      if (accountOption) {
        await accountOption.click();
        await waitForUi(page);
      }
    }

    const sidebar = await firstVisible(page, [
      page.getByRole("navigation"),
      page.locator("aside"),
      page.locator("[data-testid*='sidebar']"),
      page.locator("[class*='sidebar']"),
    ]);
    if (!sidebar) {
      throw new Error("Main interface did not load or left sidebar is not visible.");
    }

    await screenshot(page, "dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioMenu = await firstVisible(page, [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    if (!negocioMenu) {
      throw new Error("Sidebar section 'Negocio' not found.");
    }

    await negocioMenu.click();
    await waitForUi(page);

    const agregarNegocio = await firstVisible(page, [
      page.getByRole("button", { name: /Agregar Negocio/i }),
      page.getByRole("link", { name: /Agregar Negocio/i }),
      page.getByText(/Agregar Negocio/i),
    ]);
    const administrarNegocios = await firstVisible(page, [
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ]);

    if (!agregarNegocio || !administrarNegocios) {
      throw new Error("Mi Negocio submenu did not show required options.");
    }

    await screenshot(page, "mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    if (!agregarNegocio) {
      throw new Error("Could not click 'Agregar Negocio'.");
    }

    await agregarNegocio.click();
    await waitForUi(page);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    const businessNameInput = await firstVisible(page, [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[name*='negocio' i]"),
    ]);
    if (!businessNameInput) {
      throw new Error("Input field 'Nombre del Negocio' was not found.");
    }
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await screenshot(page, "agregar-negocio-modal");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await page.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    let administrarNegocios = await firstVisible(page, [
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ], 5000);

    if (!administrarNegocios) {
      const negocioMenu = await firstVisible(page, [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ]);
      if (!negocioMenu) {
        throw new Error("Could not re-open 'Mi Negocio' menu.");
      }
      await negocioMenu.click();
      await waitForUi(page);
      administrarNegocios = await firstVisible(page, [
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ]);
    }

    if (!administrarNegocios) {
      throw new Error("'Administrar Negocios' option was not found.");
    }

    await administrarNegocios.click();
    await waitForUi(page);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();

    await screenshot(page, "administrar-negocios-account-page", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/")).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const possibleUserName = page
      .locator("h1, h2, h3, h4, p, span, strong")
      .filter({ hasNotText: /informaci[oó]n general|business plan|cambiar plan|@/i })
      .first();
    await expect(possibleUserName).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(
      firstVisible(page, [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]),
    ).resolves.not.toBeNull();

    const businessRows = page.locator("ul li, table tbody tr, [data-testid*='business'], [class*='business-card']");
    expect(await businessRows.count()).toBeGreaterThan(0);
  });

  await runStep("Términos y Condiciones", async () => {
    report.evidence.termsUrl = await validateLegalPage(
      page,
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "terminos-y-condiciones",
    );
  });

  await runStep("Política de Privacidad", async () => {
    report.evidence.privacyUrl = await validateLegalPage(
      page,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "politica-de-privacidad",
    );
  });

  report.generatedAt = new Date().toISOString();
  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");

  if (failures.length > 0) {
    throw new Error(
      `One or more validations failed: ${failures.join(", ")}. Report saved to ${REPORT_PATH}`,
    );
  }
});
