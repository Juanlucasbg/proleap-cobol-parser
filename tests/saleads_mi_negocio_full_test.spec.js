const { test, expect } = require("@playwright/test");
const path = require("path");
const fs = require("fs/promises");

const ARTIFACTS_ROOT = path.join(process.cwd(), "test-results", "saleads-mi-negocio");
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

function slugify(text) {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function ensureArtifactsDir(testInfo) {
  const dir = path.join(ARTIFACTS_ROOT, slugify(testInfo.title));
  await fs.mkdir(dir, { recursive: true });
  return dir;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {
    // Some apps keep long polling; domcontentloaded is enough fallback.
  });
}

async function clickByVisibleText(page, candidateNames) {
  for (const name of candidateNames) {
    const button = page.getByRole("button", { name, exact: false }).first();
    if (await button.isVisible().catch(() => false)) {
      await button.click();
      await waitForUi(page);
      return true;
    }

    const link = page.getByRole("link", { name, exact: false }).first();
    if (await link.isVisible().catch(() => false)) {
      await link.click();
      await waitForUi(page);
      return true;
    }

    const text = page.getByText(name, { exact: false }).first();
    if (await text.isVisible().catch(() => false)) {
      await text.click();
      await waitForUi(page);
      return true;
    }
  }
  return false;
}

async function screenshotCheckpoint(page, artifactsDir, name, testInfo, fullPage = false) {
  const filePath = path.join(artifactsDir, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, {
    path: filePath,
    contentType: "image/png"
  });
}

function createReporter() {
  const rows = new Map();
  for (const field of REPORT_FIELDS) {
    rows.set(field, { status: "FAIL", detail: "Not executed due to prior failure." });
  }
  return {
    pass(stepName, detail = "") {
      rows.set(stepName, { status: "PASS", detail });
    },
    fail(stepName, error) {
      rows.set(stepName, {
        status: "FAIL",
        detail: error instanceof Error ? error.message : String(error)
      });
    },
    finalizeMarkdown() {
      const header = "| Step | Status | Detail |\n|---|---|---|";
      const body = REPORT_FIELDS.map((stepName) => {
        const result = rows.get(stepName);
        return `| ${stepName} | ${result.status} | ${String(result.detail).replace(/\|/g, "\\|")} |`;
      }).join("\n");
      return `${header}\n${body}`;
    },
    failedSteps() {
      return REPORT_FIELDS.filter((name) => rows.get(name).status !== "PASS");
    }
  };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.setTimeout(240000);
  const artifactsDir = await ensureArtifactsDir(testInfo);
  const reporter = createReporter();
  let blocked = false;

  async function runStep(reportName, stepTitle, fn) {
    await test.step(stepTitle, async () => {
      if (blocked) {
        reporter.fail(reportName, "Skipped due to previous step failure.");
        return;
      }

      try {
        await fn();
        reporter.pass(reportName, "Validated successfully.");
      } catch (error) {
        reporter.fail(reportName, error);
        blocked = true;
      }
    });
  }

  await runStep("Login", "1. Login with Google", async () => {
    if (page.url() === "about:blank") {
      const fallbackLoginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
      expect(
        fallbackLoginUrl,
        "Browser started on about:blank. Set SALEADS_LOGIN_URL or pre-open SaleADS login page."
      ).toBeTruthy();
      await page.goto(fallbackLoginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const loginClicked = await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Acceder con Google",
      "Google",
      "Login",
      "Iniciar sesión"
    ]);
    expect(loginClicked, "Google login trigger was not found on the page.").toBeTruthy();

    const googleChooser = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
    if (await googleChooser.isVisible().catch(() => false)) {
      await googleChooser.click();
      await waitForUi(page);
    }

    await expect(page.locator("nav, aside")).toBeVisible({ timeout: 30000 });
    await screenshotCheckpoint(page, artifactsDir, "01-dashboard-loaded", testInfo);
  });

  await runStep("Mi Negocio menu", "2. Open Mi Negocio menu", async () => {
    const negocioOpened = await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
    expect(negocioOpened, "Could not open the Negocio / Mi Negocio menu from sidebar.").toBeTruthy();

    await expect(page.getByText("Agregar Negocio", { exact: false })).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: false })).toBeVisible();
    await screenshotCheckpoint(page, artifactsDir, "02-mi-negocio-menu-expanded", testInfo);
  });

  await runStep("Agregar Negocio modal", "3. Validate Agregar Negocio modal", async () => {
    const addBusinessClicked = await clickByVisibleText(page, ["Agregar Negocio"]);
    expect(addBusinessClicked, "Could not click 'Agregar Negocio'.").toBeTruthy();

    await expect(page.getByText("Crear Nuevo Negocio", { exact: false })).toBeVisible();
    const businessNameInput = page
      .getByLabel("Nombre del Negocio", { exact: false })
      .or(page.getByPlaceholder("Nombre del Negocio", { exact: false }))
      .first();
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancelar", exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: "Crear Negocio", exact: false })).toBeVisible();

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await screenshotCheckpoint(page, artifactsDir, "03-agregar-negocio-modal", testInfo);
    await page.getByRole("button", { name: "Cancelar", exact: false }).click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", "4. Open Administrar Negocios", async () => {
    const administrarClicked = await clickByVisibleText(page, ["Administrar Negocios"]);
    if (!administrarClicked) {
      await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
      const secondTry = await clickByVisibleText(page, ["Administrar Negocios"]);
      expect(secondTry, "Could not navigate to Administrar Negocios.").toBeTruthy();
    }

    await expect(page.getByText("Información General", { exact: false })).toBeVisible({
      timeout: 30000
    });
    await expect(page.getByText("Detalles de la Cuenta", { exact: false })).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: false })).toBeVisible();
    await expect(page.getByText("Sección Legal", { exact: false })).toBeVisible();
    await screenshotCheckpoint(page, artifactsDir, "04-administrar-negocios", testInfo, true);
  });

  await runStep("Información General", "5. Validate Información General", async () => {
    const infoSection = page.locator("section,div").filter({ hasText: /Información General/i }).first();
    await expect(infoSection).toBeVisible();
    await expect(infoSection.getByText(/[A-Za-zÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑ ]{2,}/).first()).toBeVisible();
    await expect(infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expect(page.getByText("BUSINESS PLAN", { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cambiar Plan", exact: false })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", "6. Validate Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", "7. Validate Tus Negocios", async () => {
    const businessesSection = page.locator("section,div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByText("Agregar Negocio", { exact: false })).toBeVisible();
    await expect(businessesSection.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
  });

  await runStep("Términos y Condiciones", "8. Validate Términos y Condiciones", async () => {
    const applicationPage = page;
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    const clicked = await clickByVisibleText(applicationPage, ["Términos y Condiciones"]);
    expect(clicked, "Could not click 'Términos y Condiciones'.").toBeTruthy();
    const popup = await popupPromise;

    const legalPage = popup || applicationPage;
    await waitForUi(legalPage);
    await expect(legalPage.getByText("Términos y Condiciones", { exact: false }).first()).toBeVisible({
      timeout: 30000
    });

    const bodyText = await legalPage.locator("body").innerText();
    expect(bodyText.length).toBeGreaterThan(100);

    await screenshotCheckpoint(legalPage, artifactsDir, "08-terminos-y-condiciones", testInfo, true);
    const termsUrl = legalPage.url();
    await testInfo.attach("terminos-y-condiciones-url", {
      body: Buffer.from(termsUrl, "utf-8"),
      contentType: "text/plain"
    });

    if (popup) {
      await popup.close();
      await applicationPage.bringToFront();
      await waitForUi(applicationPage);
    } else {
      await legalPage.goBack().catch(() => {});
      await waitForUi(applicationPage);
    }
  });

  await runStep("Política de Privacidad", "9. Validate Política de Privacidad", async () => {
    const applicationPage = page;
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    const clicked = await clickByVisibleText(applicationPage, ["Política de Privacidad"]);
    expect(clicked, "Could not click 'Política de Privacidad'.").toBeTruthy();
    const popup = await popupPromise;

    const legalPage = popup || applicationPage;
    await waitForUi(legalPage);
    await expect(legalPage.getByText("Política de Privacidad", { exact: false }).first()).toBeVisible({
      timeout: 30000
    });

    const bodyText = await legalPage.locator("body").innerText();
    expect(bodyText.length).toBeGreaterThan(100);

    await screenshotCheckpoint(legalPage, artifactsDir, "09-politica-de-privacidad", testInfo, true);
    const privacyUrl = legalPage.url();
    await testInfo.attach("politica-de-privacidad-url", {
      body: Buffer.from(privacyUrl, "utf-8"),
      contentType: "text/plain"
    });

    if (popup) {
      await popup.close();
      await applicationPage.bringToFront();
      await waitForUi(applicationPage);
    } else {
      await legalPage.goBack().catch(() => {});
      await waitForUi(applicationPage);
    }
  });

  await test.step("10. Final report", async () => {
    const markdown = reporter.finalizeMarkdown();
    const reportPath = path.join(artifactsDir, "final-report.md");
    await fs.writeFile(reportPath, markdown, "utf-8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: reportPath,
      contentType: "text/markdown"
    });
  });

  expect(
    reporter.failedSteps(),
    "One or more validation steps failed. See attached final report for details."
  ).toHaveLength(0);
});
