const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const TEST_NAME = "saleads_mi_negocio_full_test";
const ARTIFACTS_DIR = path.join(process.cwd(), "artifacts", TEST_NAME);
const REPORT_PATH = path.join(ARTIFACTS_DIR, "final-report.json");

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

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function ensureArtifactsDir() {
  fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });
}

function toFileName(label) {
  return label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function checkpoint(page, label, fullPage = false) {
  await ensureArtifactsDir();
  await waitForUi(page);
  await page.screenshot({
    path: path.join(ARTIFACTS_DIR, `${toFileName(label)}.png`),
    fullPage,
  });
}

async function isVisible(locator, timeout = 1000) {
  return locator.isVisible({ timeout }).catch(() => false);
}

async function findVisibleByTexts(page, texts) {
  for (const text of texts) {
    const exact = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
    const includes = new RegExp(escapeRegex(text), "i");
    const candidates = [
      page.getByRole("button", { name: exact }).first(),
      page.getByRole("link", { name: exact }).first(),
      page.getByRole("menuitem", { name: exact }).first(),
      page.getByRole("tab", { name: exact }).first(),
      page.getByText(exact).first(),
      page.getByText(includes).first(),
    ];

    for (const candidate of candidates) {
      if (await isVisible(candidate)) {
        return candidate;
      }
    }
  }

  throw new Error(`No visible element found for text(s): ${texts.join(", ")}`);
}

async function clickByTextAndWait(page, texts) {
  const locator = await findVisibleByTexts(page, texts);
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click({ timeout: 20_000 });
  await waitForUi(page);
  return locator;
}

async function findBusinessNameInput(page) {
  const candidates = [
    page.getByLabel(/Nombre del Negocio/i).first(),
    page.getByPlaceholder(/Nombre del Negocio/i).first(),
    page.locator("input[type='text']").first(),
    page.locator("input:not([type])").first(),
  ];

  for (const candidate of candidates) {
    if (await isVisible(candidate, 1_500)) {
      return candidate;
    }
  }

  throw new Error("No visible input found for 'Nombre del Negocio'.");
}

async function ensureMiNegocioExpanded(page) {
  const agregarVisible = await isVisible(page.getByText(/Agregar Negocio/i).first(), 1200);
  const administrarVisible = await isVisible(page.getByText(/Administrar Negocios/i).first(), 1200);

  if (!agregarVisible || !administrarVisible) {
    await clickByTextAndWait(page, ["Mi Negocio"]);
  }
}

test(TEST_NAME, async ({ page, context }) => {
  const results = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed." }]),
  );
  const finalUrls = {
    "Términos y Condiciones": null,
    "Política de Privacidad": null,
  };

  let appPage = page;

  async function runStep(reportField, action) {
    try {
      await action();
      results[reportField] = { status: "PASS" };
    } catch (error) {
      const details = error instanceof Error ? error.message : String(error);
      results[reportField] = { status: "FAIL", details };
      await checkpoint(appPage, `failed-${reportField}`, true).catch(() => {});
    }
  }

  await runStep("Login", async () => {
    const baseUrl = process.env.SALEADS_URL || process.env.BASE_URL || process.env.APP_URL;
    if (page.url() === "about:blank") {
      if (!baseUrl) {
        throw new Error(
          "Browser started on about:blank. Provide SALEADS_URL/BASE_URL/APP_URL or preload SaleADS login page.",
        );
      }
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const loginLocator = await findVisibleByTexts(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google",
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await loginLocator.click({ timeout: 20_000 });
    await waitForUi(page);
    const popup = await popupPromise;

    if (popup) {
      await waitForUi(popup);
      const account = popup.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      if (await isVisible(account, 5_000)) {
        await account.click({ timeout: 10_000 });
        await waitForUi(popup);
      }
    } else {
      const account = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      if (await isVisible(account, 3_000)) {
        await account.click({ timeout: 10_000 });
        await waitForUi(page);
      }
    }

    for (const candidate of [...context.pages()].reverse()) {
      const hasSidebar = await isVisible(candidate.locator("aside, nav").first(), 4_000);
      if (hasSidebar) {
        appPage = candidate;
        break;
      }
    }

    await expect(appPage.locator("aside, nav").first()).toBeVisible();
    await checkpoint(appPage, "step-1-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    await expect(appPage.locator("aside, nav").first()).toBeVisible();

    const negocioVisible = await isVisible(appPage.getByText(/^Negocio$/i).first(), 2_000);
    if (negocioVisible) {
      await clickByTextAndWait(appPage, ["Negocio"]);
    }

    await clickByTextAndWait(appPage, ["Mi Negocio"]);
    await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await checkpoint(appPage, "step-2-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await ensureMiNegocioExpanded(appPage);
    await clickByTextAndWait(appPage, ["Agregar Negocio"]);

    await expect(appPage.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(appPage.getByText(/^Cancelar$/i).first()).toBeVisible();
    await expect(appPage.getByText(/Crear Negocio/i).first()).toBeVisible();

    const businessInput = await findBusinessNameInput(appPage);
    await expect(businessInput).toBeVisible();
    await businessInput.click();
    await businessInput.fill("Negocio Prueba Automatización");
    await clickByTextAndWait(appPage, ["Cancelar"]);

    await checkpoint(appPage, "step-3-agregar-negocio-modal");
  });

  await runStep("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(appPage);
    await clickByTextAndWait(appPage, ["Administrar Negocios"]);

    await expect(appPage.getByText(/Información General/i).first()).toBeVisible();
    await expect(appPage.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(appPage.getByText(/Sección Legal/i).first()).toBeVisible();
    await checkpoint(appPage, "step-4-administrar-negocios-view", true);
  });

  await runStep("Información General", async () => {
    const email = appPage.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
    await expect(email).toBeVisible();
    await expect(appPage.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(appPage.getByText(/Cambiar Plan/i).first()).toBeVisible();

    const nameSignals = [
      appPage.getByText(/Nombre/i).first(),
      appPage.getByText(/Usuario/i).first(),
      appPage.locator("h1, h2, h3, h4, p, span, strong").first(),
    ];
    let hasName = false;
    for (const signal of nameSignals) {
      if (await isVisible(signal, 1_500)) {
        hasName = true;
        break;
      }
    }
    expect(hasName).toBeTruthy();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(appPage.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(appPage.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(appPage.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const businessSection = appPage.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible();
  });

  async function validateLegalDocument(linkText, headingText, reportKey, screenshotName) {
    const link = await findVisibleByTexts(appPage, [linkText]);
    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await link.click({ timeout: 20_000 });
    await waitForUi(appPage);
    const popup = await popupPromise;
    const legalPage = popup || appPage;
    await waitForUi(legalPage);

    await expect(legalPage.getByText(new RegExp(escapeRegex(headingText), "i")).first()).toBeVisible();
    const content = await legalPage.locator("body").innerText();
    expect(content.trim().length).toBeGreaterThan(120);

    finalUrls[reportKey] = legalPage.url();
    await checkpoint(legalPage, screenshotName, true);

    if (legalPage !== appPage) {
      await legalPage.close();
      await appPage.bringToFront();
      await waitForUi(appPage);
      return;
    }

    await appPage.goBack({ timeout: 15_000 }).catch(() => {});
    await waitForUi(appPage);
  }

  await runStep("Términos y Condiciones", async () => {
    await validateLegalDocument(
      "Términos y Condiciones",
      "Términos y Condiciones",
      "Términos y Condiciones",
      "step-8-terminos-y-condiciones",
    );
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalDocument(
      "Política de Privacidad",
      "Política de Privacidad",
      "Política de Privacidad",
      "step-9-politica-de-privacidad",
    );
  });

  await ensureArtifactsDir();
  fs.writeFileSync(
    REPORT_PATH,
    `${JSON.stringify(
      {
        testName: TEST_NAME,
        generatedAt: new Date().toISOString(),
        report: results,
        urls: finalUrls,
      },
      null,
      2,
    )}\n`,
    "utf8",
  );

  const failedSteps = Object.entries(results).filter(([, value]) => value.status === "FAIL");
  expect(
    failedSteps,
    `One or more validation steps failed. See ${REPORT_PATH} for details.`,
  ).toHaveLength(0);
});
