const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
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
  "Política de Privacidad",
];

function resolveLoginUrl() {
  return (
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.APP_URL ||
    process.env.BASE_URL ||
    ""
  );
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function firstVisible(candidates, timeout = 10000) {
  for (const candidate of candidates) {
    try {
      await expect(candidate).toBeVisible({ timeout });
      return candidate;
    } catch (error) {
      // Try next candidate.
    }
  }

  throw new Error("Could not find a visible element from provided candidates.");
}

test(TEST_NAME, async ({ page, context }) => {
  const artifactDir = path.join(process.cwd(), "artifacts", TEST_NAME);
  fs.mkdirSync(artifactDir, { recursive: true });

  const results = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "NOT_RUN" }]),
  );
  const evidence = {
    screenshots: [],
    urls: {},
  };
  const failures = [];

  async function checkpoint(targetPage, fileName, fullPage = false) {
    await waitForUi(targetPage);
    const filePath = path.join(artifactDir, fileName);
    await targetPage.screenshot({ path: filePath, fullPage });
    evidence.screenshots.push(filePath);
  }

  async function runStep(stepName, task) {
    try {
      await task();
      results[stepName] = { status: "PASS" };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      results[stepName] = { status: "FAIL", error: message };
      failures.push(`[${stepName}] ${message}`);
    }
  }

  async function openAndValidateLegalPage({
    linkRegex,
    headingRegex,
    screenshotName,
    reportUrlKey,
  }) {
    const appUrlBeforeClick = page.url();
    const legalLink = await firstVisible([
      page.getByRole("link", { name: linkRegex }).first(),
      page.getByText(linkRegex).first(),
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await legalLink.click();
    await waitForUi(page);

    const popupPage = await popupPromise;
    const legalPage = popupPage || page;
    await waitForUi(legalPage);

    const legalHeading = await firstVisible([
      legalPage.getByRole("heading", { name: headingRegex }).first(),
      legalPage.getByText(headingRegex).first(),
    ]);
    await expect(legalHeading).toBeVisible();

    const legalContent = legalPage.locator("p, li, article, main, section").first();
    await expect(legalContent).toBeVisible();

    evidence.urls[reportUrlKey] = legalPage.url();
    await checkpoint(legalPage, screenshotName, true);

    if (popupPage) {
      await popupPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (page.url() !== appUrlBeforeClick) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUi(page);
    }
  }

  const loginUrl = resolveLoginUrl();

  await runStep("Login", async () => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url().startsWith("about:blank")) {
      throw new Error(
        "No login URL provided and browser is on about:blank. Set SALEADS_LOGIN_URL for this environment.",
      );
    }

    const loginButton = await firstVisible(
      [
        page
          .getByRole("button", {
            name: /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Google/i,
          })
          .first(),
        page
          .getByRole("link", {
            name: /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Google/i,
          })
          .first(),
        page.getByText(/Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google/i).first(),
      ],
      20000,
    );

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await waitForUi(googlePopup);
      const accountOption = googlePopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitForUi(googlePopup);
      }
    } else {
      const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitForUi(page);
      }
    }

    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 30000 });
    await checkpoint(page, "01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 20000 });

    const negocioItem = await firstVisible([
      sidebar.getByText(/^Negocio$/i).first(),
      sidebar.getByText(/Negocio/i).first(),
      page.getByText(/^Negocio$/i).first(),
    ]);
    await clickAndWait(page, negocioItem);

    const miNegocioItem = await firstVisible([
      sidebar.getByText(/^Mi Negocio$/i).first(),
      page.getByText(/^Mi Negocio$/i).first(),
    ]);
    await clickAndWait(page, miNegocioItem);

    await expect(sidebar.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 10000 });
    await expect(sidebar.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 10000 });
    await checkpoint(page, "02-mi-negocio-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const sidebar = page.locator("aside, nav").first();
    const agregarNegocioAction = await firstVisible([
      sidebar.getByText(/^Agregar Negocio$/i).first(),
      page.getByRole("button", { name: /^Agregar Negocio$/i }).first(),
      page.getByText(/^Agregar Negocio$/i).first(),
    ]);
    await clickAndWait(page, agregarNegocioAction);

    const modal = await firstVisible([
      page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first(),
      page.locator("[role='dialog']").filter({ hasText: /Crear Nuevo Negocio/i }).first(),
    ]);

    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await checkpoint(page, "03-agregar-negocio-modal.png");

    const nombreInput = await firstVisible([
      modal.getByLabel(/Nombre del Negocio/i).first(),
      modal.getByPlaceholder(/Nombre del Negocio/i).first(),
      modal.locator("input").first(),
    ]);
    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");

    const cancelarButton = modal.getByRole("button", { name: /Cancelar/i });
    await clickAndWait(page, cancelarButton);
    await expect(modal).toBeHidden({ timeout: 10000 });
  });

  await runStep("Administrar Negocios view", async () => {
    const sidebar = page.locator("aside, nav").first();
    const administrarNegocios = sidebar.getByText(/Administrar Negocios/i).first();

    const isVisible = await administrarNegocios.isVisible().catch(() => false);
    if (!isVisible) {
      const miNegocioItem = await firstVisible([
        sidebar.getByText(/^Mi Negocio$/i).first(),
        page.getByText(/^Mi Negocio$/i).first(),
      ]);
      await clickAndWait(page, miNegocioItem);
    }

    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 15000 });
    await checkpoint(page, "04-administrar-negocios-view.png", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();

    const bodyText = await page.locator("body").innerText();
    const lines = bodyText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const ignored = [
      /Informaci[oó]n General/i,
      /Detalles de la Cuenta/i,
      /Tus Negocios/i,
      /Secci[oó]n Legal/i,
      /BUSINESS PLAN/i,
      /Cambiar Plan/i,
      /Agregar Negocio/i,
      /Administrar Negocios/i,
      /Mi Negocio/i,
      /Negocio/i,
    ];
    const hasVisibleUserName = lines.some((line) => {
      const looksLikeName =
        /^[A-Za-zÁÉÍÓÚÑáéíóúñ'-]{2,}(?:\s+[A-Za-zÁÉÍÓÚÑáéíóúñ'-]{2,})+$/.test(line);
      return looksLikeName && !ignored.some((pattern) => pattern.test(line));
    });
    expect(hasVisibleUserName).toBeTruthy();

    await expect(
      page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first(),
    ).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo|Estado.*activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    await openAndValidateLegalPage({
      linkRegex: /T[eé]rminos y Condiciones/i,
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      reportUrlKey: "terminos_y_condiciones",
    });
  });

  await runStep("Política de Privacidad", async () => {
    await openAndValidateLegalPage({
      linkRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      reportUrlKey: "politica_de_privacidad",
    });
  });

  const finalReport = {
    name: TEST_NAME,
    generatedAt: new Date().toISOString(),
    loginUrlProvided: Boolean(loginUrl),
    results,
    evidence,
  };

  const reportPath = path.join(artifactDir, "final-report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  console.log(`Final report generated at: ${reportPath}`);
  console.table(
    Object.entries(results).map(([step, data]) => ({
      step,
      status: data.status,
    })),
  );

  if (failures.length > 0) {
    throw new Error(`Validation failures (${failures.length}):\n${failures.join("\n")}`);
  }
});
