const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

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

function buildReportSkeleton() {
  const results = {};

  for (const field of REPORT_FIELDS) {
    results[field] = { status: "FAIL", details: "Not executed." };
  }

  return {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results,
    evidence: {
      screenshots: [],
      urls: {}
    },
    errors: []
  };
}

async function waitForUiAfterClick(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function captureCheckpoint(page, testInfo, label, options = {}) {
  const safeName = label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
  const screenshotPath = testInfo.outputPath(`${safeName}.png`);

  await page.screenshot({ path: screenshotPath, fullPage: Boolean(options.fullPage) });
  await testInfo.attach(label, {
    path: screenshotPath,
    contentType: "image/png"
  });

  return screenshotPath;
}

async function firstVisibleLocator(locators) {
  for (const locator of locators) {
    const count = await locator.count().catch(() => 0);
    if (count === 0) {
      continue;
    }

    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  return null;
}

async function clickVisible(page, locators, labelForError) {
  const target = await firstVisibleLocator(locators);
  if (!target) {
    throw new Error(`Unable to find visible element for "${labelForError}".`);
  }

  await target.click();
  await waitForUiAfterClick(page);
}

async function clickLegalAndResolvePage(appPage, context, linkLocators) {
  const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
  await clickVisible(appPage, linkLocators, "legal document link");
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForTimeout(800);
    return { legalPage: popup, openedInNewTab: true };
  }

  await appPage.waitForLoadState("domcontentloaded");
  await appPage.waitForTimeout(800);
  return { legalPage: appPage, openedInNewTab: false };
}

async function runStep(report, fieldName, fn) {
  try {
    await fn();
    report.results[fieldName] = { status: "PASS", details: "Validation completed." };
  } catch (error) {
    report.results[fieldName] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error)
    };
    report.errors.push({
      field: fieldName,
      message: error instanceof Error ? error.message : String(error)
    });
    throw error;
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = buildReportSkeleton();
  let appPage = page;

  try {
    await runStep(report, "Login", async () => {
      const targetUrl =
        process.env.SALEADS_URL ||
        process.env.BASE_URL ||
        (testInfo.project.use ? testInfo.project.use.baseURL : undefined);

      if (targetUrl) {
        await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
      } else if (page.url() === "about:blank") {
        throw new Error(
          "No login URL configured. Set SALEADS_URL/BASE_URL or start the test with an already-open login page."
        );
      }

      const loginButton = await firstVisibleLocator([
        page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n/i }),
        page.getByRole("link", { name: /google|sign in|iniciar sesi[oó]n/i }),
        page.getByText(/google|sign in|iniciar sesi[oó]n/i)
      ]);

      if (!loginButton) {
        throw new Error("Google login entry point is not visible.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      await loginButton.click();
      await waitForUiAfterClick(page);

      const authPage = await popupPromise;
      const authContextPage = authPage || page;

      const accountOption = authContextPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible({ timeout: 10000 }).catch(() => false)) {
        await accountOption.click();
        await authContextPage.waitForTimeout(1000);
      }

      if (authPage) {
        await Promise.race([
          authPage.waitForEvent("close", { timeout: 60000 }),
          page.waitForLoadState("networkidle", { timeout: 60000 })
        ]).catch(() => null);
      }

      const sidebar = await firstVisibleLocator([
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator("nav")
      ]);
      expect(sidebar, "Left sidebar navigation should be visible after login.").not.toBeNull();
      await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({ timeout: 60000 });

      const shot = await captureCheckpoint(page, testInfo, "checkpoint-dashboard-loaded");
      report.evidence.screenshots.push({ checkpoint: "dashboard_loaded", path: shot });
    });

    await runStep(report, "Mi Negocio menu", async () => {
      await clickVisible(
        appPage,
        [
          appPage.getByRole("link", { name: /mi negocio/i }),
          appPage.getByRole("button", { name: /mi negocio/i }),
          appPage.getByText(/mi negocio/i)
        ],
        "Mi Negocio"
      );

      await expect(appPage.getByText(/agregar negocio/i)).toBeVisible({ timeout: 20000 });
      await expect(appPage.getByText(/administrar negocios/i)).toBeVisible({ timeout: 20000 });

      const shot = await captureCheckpoint(appPage, testInfo, "checkpoint-mi-negocio-menu-expanded");
      report.evidence.screenshots.push({ checkpoint: "mi_negocio_menu_expanded", path: shot });
    });

    await runStep(report, "Agregar Negocio modal", async () => {
      await clickVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /agregar negocio/i }),
          appPage.getByRole("link", { name: /agregar negocio/i }),
          appPage.getByText(/agregar negocio/i)
        ],
        "Agregar Negocio"
      );

      await expect(appPage.getByText(/crear nuevo negocio/i)).toBeVisible({ timeout: 20000 });

      const nameInput = await firstVisibleLocator([
        appPage.getByLabel(/nombre del negocio/i),
        appPage.getByPlaceholder(/nombre del negocio/i),
        appPage.locator("input[name*=negocio i]")
      ]);
      expect(nameInput, "Nombre del Negocio input should be visible.").not.toBeNull();

      await expect(appPage.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(appPage.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(appPage.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      const shot = await captureCheckpoint(appPage, testInfo, "checkpoint-agregar-negocio-modal");
      report.evidence.screenshots.push({ checkpoint: "agregar_negocio_modal", path: shot });

      if (nameInput) {
        await nameInput.fill("Negocio Prueba Automatización");
      }
      await appPage.getByRole("button", { name: /cancelar/i }).click();
      await waitForUiAfterClick(appPage);
    });

    await runStep(report, "Administrar Negocios view", async () => {
      if (!(await appPage.getByText(/administrar negocios/i).isVisible().catch(() => false))) {
        await clickVisible(
          appPage,
          [
            appPage.getByRole("link", { name: /mi negocio/i }),
            appPage.getByRole("button", { name: /mi negocio/i }),
            appPage.getByText(/mi negocio/i)
          ],
          "Mi Negocio (expand)"
        );
      }

      await clickVisible(
        appPage,
        [
          appPage.getByRole("link", { name: /administrar negocios/i }),
          appPage.getByRole("button", { name: /administrar negocios/i }),
          appPage.getByText(/administrar negocios/i)
        ],
        "Administrar Negocios"
      );

      await expect(appPage.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 20000 });
      await expect(appPage.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 20000 });
      await expect(appPage.getByText(/tus negocios/i)).toBeVisible({ timeout: 20000 });
      await expect(appPage.getByText(/secci[oó]n legal/i)).toBeVisible({ timeout: 20000 });

      const shot = await captureCheckpoint(appPage, testInfo, "checkpoint-administrar-negocios-page", {
        fullPage: true
      });
      report.evidence.screenshots.push({ checkpoint: "administrar_negocios_page", path: shot });
    });

    await runStep(report, "Información General", async () => {
      await expect(appPage.getByText(/business plan/i)).toBeVisible();
      await expect(appPage.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

      const emailLocator = appPage
        .locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/")
        .first();
      await expect(emailLocator).toBeVisible();

      const userNameHint = await firstVisibleLocator([
        appPage.getByText(/nombre/i),
        appPage.getByText(/usuario/i),
        appPage.getByText(/name/i)
      ]);
      expect(userNameHint, "Expected to find a visible user-name label/value.").not.toBeNull();
    });

    await runStep(report, "Detalles de la Cuenta", async () => {
      await expect(appPage.getByText(/cuenta creada/i)).toBeVisible();
      await expect(appPage.getByText(/estado activo/i)).toBeVisible();
      await expect(appPage.getByText(/idioma seleccionado/i)).toBeVisible();
    });

    await runStep(report, "Tus Negocios", async () => {
      await expect(appPage.getByText(/tus negocios/i)).toBeVisible();
      await expect(appPage.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
      await expect(appPage.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();

      const listLike = await firstVisibleLocator([
        appPage.locator("section:has-text('Tus Negocios') li"),
        appPage.locator("section:has-text('Tus Negocios') tr"),
        appPage.locator("section:has-text('Tus Negocios') [role='row']")
      ]);
      expect(listLike, "Business list should be visible in Tus Negocios section.").not.toBeNull();
    });

    await runStep(report, "Términos y Condiciones", async () => {
      const { legalPage, openedInNewTab } = await clickLegalAndResolvePage(appPage, context, [
        appPage.getByRole("link", { name: /t[eé]rminos y condiciones/i }),
        appPage.getByText(/t[eé]rminos y condiciones/i)
      ]);

      await expect(legalPage.getByRole("heading", { name: /t[eé]rminos y condiciones/i })).toBeVisible({
        timeout: 30000
      });

      const legalText = (await legalPage.locator("body").innerText()).trim();
      expect(legalText.length).toBeGreaterThan(120);

      const shot = await captureCheckpoint(legalPage, testInfo, "checkpoint-terminos-condiciones", {
        fullPage: true
      });
      report.evidence.screenshots.push({ checkpoint: "terminos_y_condiciones", path: shot });
      report.evidence.urls.terminosYCondiciones = legalPage.url();

      if (openedInNewTab) {
        await legalPage.close();
        await appPage.bringToFront();
      } else {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      }

      await waitForUiAfterClick(appPage);
    });

    await runStep(report, "Política de Privacidad", async () => {
      const { legalPage, openedInNewTab } = await clickLegalAndResolvePage(appPage, context, [
        appPage.getByRole("link", { name: /pol[ií]tica de privacidad/i }),
        appPage.getByText(/pol[ií]tica de privacidad/i)
      ]);

      await expect(legalPage.getByRole("heading", { name: /pol[ií]tica de privacidad/i })).toBeVisible({
        timeout: 30000
      });

      const legalText = (await legalPage.locator("body").innerText()).trim();
      expect(legalText.length).toBeGreaterThan(120);

      const shot = await captureCheckpoint(legalPage, testInfo, "checkpoint-politica-privacidad", {
        fullPage: true
      });
      report.evidence.screenshots.push({ checkpoint: "politica_de_privacidad", path: shot });
      report.evidence.urls.politicaDePrivacidad = legalPage.url();

      if (openedInNewTab) {
        await legalPage.close();
        await appPage.bringToFront();
      } else {
        await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      }

      await waitForUiAfterClick(appPage);
    });
  } finally {
    const reportPath = testInfo.outputPath("mi-negocio-final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    await testInfo.attach("mi-negocio-final-report", {
      path: reportPath,
      contentType: "application/json"
    });

    // Final report required by step 10.
    console.log("FINAL_REPORT", JSON.stringify(report.results));
    if (report.evidence.urls.terminosYCondiciones) {
      console.log("TERMINOS_URL", report.evidence.urls.terminosYCondiciones);
    }
    if (report.evidence.urls.politicaDePrivacidad) {
      console.log("PRIVACIDAD_URL", report.evidence.urls.politicaDePrivacidad);
    }
  }
});
