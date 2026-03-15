const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
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

function createEmptyReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = { status: "NOT_RUN" };
  }
  return report;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 6000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function waitAnyVisible(locators, description, timeoutMs = 15000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of locators) {
      try {
        const candidate = locator.first();
        if (await candidate.isVisible()) {
          return candidate;
        }
      } catch (_error) {
        // Ignore transient locator errors while probing candidates.
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(`No visible element found for: ${description}`);
}

function textCandidateLocators(page, textOrRegex) {
  return [
    page.getByRole("button", { name: textOrRegex }),
    page.getByRole("link", { name: textOrRegex }),
    page.getByRole("menuitem", { name: textOrRegex }),
    page.getByRole("tab", { name: textOrRegex }),
    page.getByText(textOrRegex),
  ];
}

async function clickByText(page, textOrRegex, description) {
  const target = await waitAnyVisible(
    textCandidateLocators(page, textOrRegex),
    description
  );
  await target.click();
  await waitForUi(page);
  return target;
}

async function expectTextVisible(page, textOrRegex, description, timeoutMs = 15000) {
  const heading = page.getByRole("heading", { name: textOrRegex });
  const text = page.getByText(textOrRegex);
  await waitAnyVisible([heading, text], description, timeoutMs);
}

async function captureScreenshot(page, evidenceDir, fileName, fullPage = false) {
  const outputPath = path.join(evidenceDir, fileName);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function clickLegalAndValidate({
  appPage,
  linkLabelRegex,
  expectedHeadingRegex,
  evidenceDir,
  screenshotFileName,
}) {
  const link = await waitAnyVisible(
    textCandidateLocators(appPage, linkLabelRegex),
    `Legal link: ${linkLabelRegex}`
  );

  const beforeUrl = appPage.url();
  let legalPage = appPage;

  try {
    const popupPromise = appPage.context().waitForEvent("page", { timeout: 7000 });
    await link.click();
    legalPage = await popupPromise;
    await waitForUi(legalPage);
  } catch (_error) {
    // If no new tab appeared, continue validating current page.
    await waitForUi(appPage);
    legalPage = appPage;
  }

  await expectTextVisible(
    legalPage,
    expectedHeadingRegex,
    `Legal heading: ${expectedHeadingRegex}`,
    20000
  );

  const legalText = ((await legalPage.locator("body").innerText()) || "").replace(/\s+/g, " ").trim();
  if (legalText.length < 120) {
    throw new Error("Legal content appears too short or empty.");
  }

  await captureScreenshot(legalPage, evidenceDir, screenshotFileName, true);
  const finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== beforeUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test.describe(TEST_NAME, () => {
  test("Login with Google and validate Mi Negocio full workflow", async ({ page }) => {
    const runId = new Date().toISOString().replace(/[.:]/g, "-");
    const evidenceDir = path.join(process.cwd(), "artifacts", TEST_NAME, runId);
    await fs.mkdir(evidenceDir, { recursive: true });

    const report = createEmptyReport();
    const failures = [];
    const evidence = {
      screenshots: [],
      finalUrls: {},
    };

    const markStep = async (stepName, action) => {
      try {
        await action();
        report[stepName] = { status: "PASS" };
      } catch (error) {
        report[stepName] = {
          status: "FAIL",
          error: error instanceof Error ? error.message : String(error),
        };
        failures.push(stepName);
      }
    };

    const initialUrl = page.url();
    if (initialUrl === "about:blank") {
      const providedUrl = process.env.SALEADS_URL;
      if (!providedUrl) {
        throw new Error(
          "Browser started on about:blank. Set SALEADS_URL or open the SaleADS login page before running."
        );
      }
      await page.goto(providedUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);

    await markStep("Login", async () => {
      await clickByText(
        page,
        /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        "Google login button"
      );

      const accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com");
      if (await accountOption.first().isVisible().catch(() => false)) {
        await accountOption.first().click();
        await waitForUi(page);
      }

      await waitAnyVisible(
        [
          page.locator("aside"),
          page.getByRole("navigation"),
          page.locator('[class*="sidebar"]'),
          page.locator('[data-testid*="sidebar"]'),
        ],
        "Main app sidebar",
        30000
      );

      const dashboardShot = await captureScreenshot(page, evidenceDir, "01_dashboard_loaded.png", true);
      evidence.screenshots.push(dashboardShot);
    });

    await markStep("Mi Negocio menu", async () => {
      await clickByText(page, /negocio/i, "Negocio section");
      await clickByText(page, /mi negocio/i, "Mi Negocio option");

      await expectTextVisible(page, /agregar negocio/i, "Agregar Negocio option");
      await expectTextVisible(page, /administrar negocios/i, "Administrar Negocios option");

      const menuShot = await captureScreenshot(page, evidenceDir, "02_mi_negocio_menu_expanded.png");
      evidence.screenshots.push(menuShot);
    });

    await markStep("Agregar Negocio modal", async () => {
      await clickByText(page, /agregar negocio/i, "Agregar Negocio menu option");

      await expectTextVisible(page, /crear nuevo negocio/i, "Crear Nuevo Negocio modal title");

      let nombreInput = page.getByLabel(/nombre del negocio/i);
      if ((await nombreInput.count()) === 0) {
        nombreInput = page.getByPlaceholder(/nombre del negocio/i);
      }
      await expect(nombreInput.first()).toBeVisible({ timeout: 15000 });

      await expectTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i, "Business quota text");
      await expectTextVisible(page, /cancelar/i, "Cancelar button");
      await expectTextVisible(page, /crear negocio/i, "Crear Negocio button");

      const modalShot = await captureScreenshot(page, evidenceDir, "03_agregar_negocio_modal.png");
      evidence.screenshots.push(modalShot);

      await nombreInput.first().click();
      await nombreInput.first().fill("Negocio Prueba Automatización");
      await clickByText(page, /cancelar/i, "Cancelar button");
    });

    await markStep("Administrar Negocios view", async () => {
      const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        await clickByText(page, /mi negocio/i, "Mi Negocio option (expand again)");
      }

      await clickByText(page, /administrar negocios/i, "Administrar Negocios option");

      await expectTextVisible(page, /informaci[oó]n general/i, "Información General section");
      await expectTextVisible(page, /detalles de la cuenta/i, "Detalles de la Cuenta section");
      await expectTextVisible(page, /tus negocios/i, "Tus Negocios section");
      await expectTextVisible(page, /secci[oó]n legal/i, "Sección Legal section");

      const accountPageShot = await captureScreenshot(
        page,
        evidenceDir,
        "04_administrar_negocios_account_page.png",
        true
      );
      evidence.screenshots.push(accountPageShot);
    });

    await markStep("Información General", async () => {
      await expectTextVisible(page, /informaci[oó]n general/i, "Información General section");

      const bodyText = ((await page.locator("body").innerText()) || "").replace(/\s+/g, " ");
      if (!/BUSINESS PLAN/i.test(bodyText)) {
        throw new Error("Text 'BUSINESS PLAN' is not visible.");
      }
      if (!/cambiar plan/i.test(bodyText)) {
        throw new Error("Button or text 'Cambiar Plan' is not visible.");
      }
      if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(bodyText)) {
        throw new Error("User email is not visible.");
      }
      if (!/nombre|usuario|user|perfil/i.test(bodyText)) {
        throw new Error("User name label/value could not be verified.");
      }
    });

    await markStep("Detalles de la Cuenta", async () => {
      await expectTextVisible(page, /cuenta creada/i, "Cuenta creada field");
      await expectTextVisible(page, /estado activo|activo/i, "Estado activo field");
      await expectTextVisible(page, /idioma seleccionado|idioma/i, "Idioma seleccionado field");
    });

    await markStep("Tus Negocios", async () => {
      await expectTextVisible(page, /tus negocios/i, "Tus Negocios section");
      await expectTextVisible(page, /agregar negocio/i, "Agregar Negocio button");
      await expectTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i, "Business quota text");
    });

    await markStep("Términos y Condiciones", async () => {
      const finalUrl = await clickLegalAndValidate({
        appPage: page,
        linkLabelRegex: /t[ée]rminos y condiciones/i,
        expectedHeadingRegex: /t[ée]rminos y condiciones/i,
        evidenceDir,
        screenshotFileName: "05_terminos_y_condiciones.png",
      });
      evidence.finalUrls["Términos y Condiciones"] = finalUrl;
    });

    await markStep("Política de Privacidad", async () => {
      const finalUrl = await clickLegalAndValidate({
        appPage: page,
        linkLabelRegex: /pol[ií]tica de privacidad/i,
        expectedHeadingRegex: /pol[ií]tica de privacidad/i,
        evidenceDir,
        screenshotFileName: "06_politica_de_privacidad.png",
      });
      evidence.finalUrls["Política de Privacidad"] = finalUrl;
    });

    const finalReport = {
      name: TEST_NAME,
      generatedAt: new Date().toISOString(),
      evidenceDir,
      report,
      evidence,
    };

    const reportPath = path.join(evidenceDir, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

    // Keep machine-readable output visible in CI logs.
    console.log(JSON.stringify(finalReport, null, 2));

    if (failures.length > 0) {
      throw new Error(`Workflow validations failed: ${failures.join(", ")}`);
    }
  });
});
