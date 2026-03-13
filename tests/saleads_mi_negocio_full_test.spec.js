const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

const REPORT_KEYS = [
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

function textRegex(text) {
  return new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

function createInitialReport() {
  return Object.fromEntries(
    REPORT_KEYS.map((key) => [key, { status: "FAIL", error: "Not executed" }])
  );
}

async function visible(locator, timeout = 2500) {
  return locator.first().isVisible({ timeout }).catch(() => false);
}

async function firstVisible(candidates, timeout = 2500) {
  for (const candidate of candidates) {
    if (await visible(candidate, timeout)) {
      return candidate.first();
    }
  }
  return null;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createInitialReport();
  const evidence = [];
  const runStamp = new Date().toISOString().replace(/[:.]/g, "-");
  const reportDir = testInfo.outputPath(`saleads-mi-negocio-${runStamp}`);
  fs.mkdirSync(reportDir, { recursive: true });

  async function waitUi(targetPage = page) {
    await targetPage.waitForLoadState("domcontentloaded").catch(() => {});
    await targetPage.waitForTimeout(900);
  }

  async function checkpoint(name, options = {}) {
    const { fullPage = false, targetPage = page } = options;
    const safeName = name.replace(/[^a-z0-9-_]+/gi, "_").toLowerCase();
    const shotPath = path.join(reportDir, `${safeName}.png`);
    await targetPage.screenshot({ path: shotPath, fullPage });
    evidence.push({ checkpoint: name, screenshot: shotPath });
  }

  async function clickByVisibleText(targetPage, text, description) {
    const regex = textRegex(text);
    const locator = await firstVisible(
      [
        targetPage.getByRole("button", { name: regex }),
        targetPage.getByRole("link", { name: regex }),
        targetPage.getByRole("menuitem", { name: regex }),
        targetPage.getByText(regex)
      ],
      4000
    );

    if (!locator) {
      throw new Error(`Could not find "${description}" using visible text "${text}".`);
    }

    await locator.click();
    await waitUi(targetPage);
  }

  async function ensureSidebarVisible() {
    const sidebar = page.locator("aside, nav").first();
    const sidebarByStructure = await visible(sidebar, 8000);
    const sidebarByText = await firstVisible(
      [page.getByText(/Negocio|Mi Negocio|Administrar Negocios/i)],
      8000
    );

    if (!sidebarByStructure && !sidebarByText) {
      throw new Error("Main interface or left sidebar navigation was not visible.");
    }
  }

  async function markStep(stepName, runStep) {
    try {
      await runStep();
      report[stepName] = { status: "PASS" };
    } catch (error) {
      report[stepName] = {
        status: "FAIL",
        error: error instanceof Error ? error.message : String(error)
      };
    }
  }

  async function openMiNegocioMenuIfNeeded() {
    const agregarVisible = await visible(page.getByText(textRegex("Agregar Negocio")), 2500);
    const administrarVisible = await visible(
      page.getByText(textRegex("Administrar Negocios")),
      2500
    );

    if (agregarVisible && administrarVisible) {
      return;
    }

    const miNegocio = await firstVisible(
      [
        page.getByRole("button", { name: textRegex("Mi Negocio") }),
        page.getByRole("link", { name: textRegex("Mi Negocio") }),
        page.getByText(textRegex("Mi Negocio"))
      ],
      5000
    );

    if (miNegocio) {
      await miNegocio.click();
      await waitUi();
    } else {
      await clickByVisibleText(page, "Negocio", "Negocio section");
      await clickByVisibleText(page, "Mi Negocio", "Mi Negocio option");
    }
  }

  async function validateLegalDocument({ linkText, headingText, reportKey, screenshotName }) {
    await markStep(reportKey, async () => {
      const appUrlBeforeClick = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

      await clickByVisibleText(page, linkText, `${linkText} link`);
      const popup = await popupPromise;
      const legalPage = popup || page;

      await waitUi(legalPage);

      const headingFound = await firstVisible(
        [
          legalPage.getByRole("heading", { name: textRegex(headingText) }),
          legalPage.getByText(textRegex(headingText))
        ],
        15000
      );

      if (!headingFound) {
        throw new Error(`Expected heading "${headingText}" was not visible.`);
      }

      const bodyText = (await legalPage.locator("body").innerText().catch(() => "")).trim();
      if (bodyText.length < 120) {
        throw new Error("Legal content text appears too short or missing.");
      }

      await checkpoint(screenshotName, { targetPage: legalPage, fullPage: true });
      report[reportKey].url = legalPage.url();

      if (popup) {
        await popup.close().catch(() => {});
        await page.bringToFront();
        await waitUi(page);
      } else if (page.url() !== appUrlBeforeClick) {
        await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" }).catch(() => {});
        await waitUi(page);
      }
    });
  }

  const saleadsUrl = process.env.SALEADS_URL || process.env.SALEADS_LOGIN_URL;
  if (saleadsUrl) {
    await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
    await waitUi();
  }

  await markStep("Login", async () => {
    const alreadyInApp = await firstVisible(
      [
        page.getByText(/Mi Negocio|Negocio|Dashboard|Panel/i),
        page.locator("aside, nav")
      ],
      6000
    );

    if (!alreadyInApp) {
      const googlePopupPromise = context
        .waitForEvent("page", { timeout: 10000 })
        .catch(() => null);

      const loginLocator = await firstVisible(
        [
          page.getByRole("button", { name: textRegex("Sign in with Google") }),
          page.getByRole("button", { name: textRegex("Iniciar sesión con Google") }),
          page.getByRole("button", { name: textRegex("Continuar con Google") }),
          page.getByRole("button", { name: textRegex("Login with Google") }),
          page.getByText(/Google/i)
        ],
        12000
      );

      if (!loginLocator) {
        throw new Error("Could not find login button or 'Sign in with Google'.");
      }

      await loginLocator.click();
      await waitUi(page);

      const googlePopup = await googlePopupPromise;
      const accountPage = googlePopup || page;
      await waitUi(accountPage);

      const accountSelector = await firstVisible(
        [accountPage.getByText(textRegex(GOOGLE_ACCOUNT_EMAIL))],
        10000
      );

      if (accountSelector) {
        await accountSelector.click();
        await waitUi(accountPage);
      }

      if (googlePopup) {
        await googlePopup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
      }
    }

    await ensureSidebarVisible();
    await checkpoint("step_1_dashboard_loaded");
  });

  await markStep("Mi Negocio menu", async () => {
    await openMiNegocioMenuIfNeeded();

    await expect(page.getByText(textRegex("Agregar Negocio"))).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(textRegex("Administrar Negocios"))).toBeVisible({
      timeout: 15000
    });
    await checkpoint("step_2_mi_negocio_menu_expanded");
  });

  await markStep("Agregar Negocio modal", async () => {
    await openMiNegocioMenuIfNeeded();
    await clickByVisibleText(page, "Agregar Negocio", "Agregar Negocio");

    await expect(page.getByText(textRegex("Crear Nuevo Negocio"))).toBeVisible({
      timeout: 15000
    });
    await expect(page.getByLabel(textRegex("Nombre del Negocio"))).toBeVisible({
      timeout: 15000
    });
    await expect(page.getByText(textRegex("Tienes 2 de 3 negocios"))).toBeVisible({
      timeout: 15000
    });
    await expect(page.getByRole("button", { name: textRegex("Cancelar") })).toBeVisible({
      timeout: 15000
    });
    await expect(page.getByRole("button", { name: textRegex("Crear Negocio") })).toBeVisible({
      timeout: 15000
    });

    await checkpoint("step_3_agregar_negocio_modal");

    const nombreNegocioInput = page.getByLabel(textRegex("Nombre del Negocio"));
    await nombreNegocioInput.click();
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, "Cancelar", "Cancelar");
  });

  await markStep("Administrar Negocios view", async () => {
    await openMiNegocioMenuIfNeeded();
    await clickByVisibleText(page, "Administrar Negocios", "Administrar Negocios");

    await expect(page.getByText(textRegex("Información General"))).toBeVisible({
      timeout: 20000
    });
    await expect(page.getByText(textRegex("Detalles de la Cuenta"))).toBeVisible({
      timeout: 20000
    });
    await expect(page.getByText(textRegex("Tus Negocios"))).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(textRegex("Sección Legal"))).toBeVisible({ timeout: 20000 });

    await checkpoint("step_4_administrar_negocios", { fullPage: true });
  });

  await markStep("Información General", async () => {
    const infoSection = page
      .locator("section, div")
      .filter({ has: page.getByText(textRegex("Información General")) })
      .first();
    await expect(infoSection).toBeVisible({ timeout: 20000 });

    const nameSignal = await firstVisible(
      [
        page.getByText(/Nombre/i),
        page.getByText(/juanlucasbarbiergarzon/i),
        page.getByText(/[A-Za-z]+ [A-Za-z]+/)
      ],
      10000
    );
    if (!nameSignal) {
      throw new Error("User name was not visibly detected in Información General.");
    }

    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)).toBeVisible({
      timeout: 10000
    });
    await expect(page.getByText(textRegex("BUSINESS PLAN"))).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: textRegex("Cambiar Plan") })).toBeVisible({
      timeout: 10000
    });
  });

  await markStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(textRegex("Cuenta creada"))).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(textRegex("Estado activo"))).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(textRegex("Idioma seleccionado"))).toBeVisible({
      timeout: 10000
    });
  });

  await markStep("Tus Negocios", async () => {
    const negociosSection = page
      .locator("section, div")
      .filter({ has: page.getByText(textRegex("Tus Negocios")) })
      .first();
    await expect(negociosSection).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(textRegex("Tienes 2 de 3 negocios"))).toBeVisible({
      timeout: 10000
    });

    const agregarNegocioButton = await firstVisible(
      [
        negociosSection.getByRole("button", { name: textRegex("Agregar Negocio") }),
        page.getByRole("button", { name: textRegex("Agregar Negocio") }),
        page.getByText(textRegex("Agregar Negocio"))
      ],
      10000
    );
    if (!agregarNegocioButton) {
      throw new Error('Button "Agregar Negocio" was not visible in Tus Negocios.');
    }
  });

  await validateLegalDocument({
    linkText: "Términos y Condiciones",
    headingText: "Términos y Condiciones",
    reportKey: "Términos y Condiciones",
    screenshotName: "step_8_terminos_y_condiciones"
  });

  await validateLegalDocument({
    linkText: "Política de Privacidad",
    headingText: "Política de Privacidad",
    reportKey: "Política de Privacidad",
    screenshotName: "step_9_politica_de_privacidad"
  });

  const finalReport = {
    test: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: saleadsUrl || "current-session-login-page",
    googleAccount: GOOGLE_ACCOUNT_EMAIL,
    summary: report,
    evidence
  };

  const reportPath = path.join(reportDir, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

  const failures = Object.entries(report).filter(([, value]) => value.status !== "PASS");
  expect(
    failures,
    `Some workflow validations failed:\n${JSON.stringify(failures, null, 2)}`
  ).toHaveLength(0);
});
