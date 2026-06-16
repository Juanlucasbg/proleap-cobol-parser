const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const STEP_FIELDS = [
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

function toSlug(input) {
  return input
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
    .toLowerCase();
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(500);
}

async function firstVisibleLocator(candidates, timeoutMs = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const locator of candidates) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return null;
}

async function clickText(page, textPattern) {
  const locator = page.getByText(textPattern).first();
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function screenshot(page, artifactsDir, name, fullPage = false) {
  const fileName = `${toSlug(name)}.png`;
  const outputPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function writeReport(artifactsDir, report) {
  const jsonPath = path.join(artifactsDir, "final-report.json");
  const markdownPath = path.join(artifactsDir, "final-report.md");

  await fs.writeFile(jsonPath, JSON.stringify(report, null, 2), "utf-8");

  const lines = ["# SaleADS Mi Negocio Validation Report", ""];
  for (const stepName of STEP_FIELDS) {
    const result = report[stepName];
    lines.push(`- ${stepName}: ${result.status}`);
    if (result.details) {
      lines.push(`  - details: ${result.details}`);
    }
    if (result.url) {
      lines.push(`  - url: ${result.url}`);
    }
  }
  lines.push("");
  lines.push(`Artifacts directory: ${artifactsDir}`);

  await fs.writeFile(markdownPath, lines.join("\n"), "utf-8");
  return { jsonPath, markdownPath };
}

async function openLegalLink({
  page,
  context,
  linkText,
  headingPattern,
  artifactsDir,
  screenshotName
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickText(page, new RegExp(`^${linkText}$`, "i"));
  const popupPage = await popupPromise;
  const legalPage = popupPage || page;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await expect(legalPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible();

  const legalText = (await legalPage.locator("main, body").first().innerText()).trim();
  expect(legalText.length).toBeGreaterThan(100);

  await screenshot(legalPage, artifactsDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close().catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.PLAYWRIGHT_TEST_BASE_URL ||
    process.env.BASE_URL;

  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL (or PLAYWRIGHT_TEST_BASE_URL / BASE_URL) to the SaleADS login URL for the current environment."
    );
  }

  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.resolve(
    process.env.SALEADS_ARTIFACTS_DIR || path.join(process.cwd(), "artifacts", runId)
  );
  await fs.mkdir(artifactsDir, { recursive: true });

  const report = Object.fromEntries(
    STEP_FIELDS.map((stepName) => [stepName, { status: "FAIL", details: "Not executed." }])
  );

  const runStep = async (name, fn) => {
    try {
      const stepOutput = await fn();
      report[name] = { status: "PASS", ...(stepOutput || {}) };
    } catch (error) {
      report[name] = { status: "FAIL", details: error.message };
    }
  };

  await runStep("Login", async () => {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginButton = await firstVisibleLocator([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google/i }).first(),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google/i }).first(),
      page.getByRole("button", { name: /google/i }).first(),
      page.getByRole("link", { name: /google/i }).first(),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|google/i).first()
    ]);
    if (!loginButton) {
      throw new Error("Google login button not found.");
    }

    const googlePopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const googlePopup = await googlePopupPromise;
    const authPage = googlePopup || page;
    await authPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});

    const accountOption = authPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await waitForUi(authPage);
    }

    if (googlePopup) {
      await googlePopup.close().catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    }

    const sidebar = await firstVisibleLocator([
      page.locator("aside").first(),
      page.getByRole("navigation").first()
    ]);
    if (!sidebar) {
      throw new Error("Left sidebar is not visible after login.");
    }
    await expect(page.getByText(/negocio/i).first()).toBeVisible();

    await screenshot(page, artifactsDir, "step-1-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    await clickText(page, /^Negocio$/i);
    await clickText(page, /^Mi Negocio$/i);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    await screenshot(page, artifactsDir, "step-2-mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickText(page, /^Agregar Negocio$/i);
    await expect(page.getByRole("heading", { name: /^Crear Nuevo Negocio$/i })).toBeVisible();
    await expect(page.getByLabel(/^Nombre del Negocio$/i).or(page.getByPlaceholder(/Nombre del Negocio/i))).toBeVisible();
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    await screenshot(page, artifactsDir, "step-3-agregar-negocio-modal");

    const businessNameInput = page.getByLabel(/^Nombre del Negocio$/i).first();
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await waitForUi(page);

    await page.getByRole("button", { name: /^Cancelar$/i }).click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    if (!(await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false))) {
      await clickText(page, /^Mi Negocio$/i);
    }
    await clickText(page, /^Administrar Negocios$/i);

    await expect(page.getByText(/^Información General$/i)).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();

    await screenshot(page, artifactsDir, "step-4-administrar-negocios-view", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cambiar Plan$/i })).toBeVisible();
    await expect(page.getByText(/@/).first()).toBeVisible();

    const infoGeneralSection = page
      .locator("section,div")
      .filter({ has: page.getByText(/^Información General$/i) })
      .first();
    await expect(infoGeneralSection).toBeVisible();

    const infoText = await infoGeneralSection.innerText();
    if (!/[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]{2,}\s+[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]{2,}/.test(infoText)) {
      throw new Error("User name not detected in Información General.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    const url = await openLegalLink({
      page,
      context,
      linkText: "Términos y Condiciones",
      headingPattern: /Términos y Condiciones/i,
      artifactsDir,
      screenshotName: "step-8-terminos-y-condiciones"
    });
    return { url };
  });

  await runStep("Política de Privacidad", async () => {
    const url = await openLegalLink({
      page,
      context,
      linkText: "Política de Privacidad",
      headingPattern: /Política de Privacidad/i,
      artifactsDir,
      screenshotName: "step-9-politica-de-privacidad"
    });
    return { url };
  });

  const reportPaths = await writeReport(artifactsDir, report);
  // Make report locations visible in test logs for automation pickups.
  console.log(`SaleADS report JSON: ${reportPaths.jsonPath}`);
  console.log(`SaleADS report Markdown: ${reportPaths.markdownPath}`);

  const failedSteps = STEP_FIELDS.filter((name) => report[name].status !== "PASS");
  expect(
    failedSteps,
    `Workflow failed for steps: ${failedSteps.join(", ")}. See ${reportPaths.jsonPath}`
  ).toEqual([]);
});
