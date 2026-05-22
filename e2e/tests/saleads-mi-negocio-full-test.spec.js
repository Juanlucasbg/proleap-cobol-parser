const { test, expect } = require("@playwright/test");
const fs = require("node:fs");

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

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function firstVisibleLocator(candidates, timeoutMs = 5000) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    if (await locator.isVisible({ timeout: timeoutMs }).catch(() => false)) {
      return locator;
    }
  }

  throw new Error("No visible locator found from candidates.");
}

async function safeScreenshot(page, testInfo, fileName, options = {}) {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, ...options });
  return screenshotPath;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "SKIPPED"]));
  const details = {};
  const evidence = {};
  const legalUrls = {};

  const markPass = (field) => {
    report[field] = "PASS";
  };

  const markFail = (field, error) => {
    report[field] = "FAIL";
    details[field] = error instanceof Error ? error.message : String(error);
  };

  const markSkipped = (field, reason) => {
    report[field] = "SKIPPED";
    details[field] = reason;
  };

  async function runFieldStep(field, requiredFields, action) {
    const blockingField = requiredFields.find((requiredField) => report[requiredField] !== "PASS");
    if (blockingField) {
      markSkipped(field, `Skipped because "${blockingField}" was not PASS.`);
      return false;
    }

    try {
      await action();
      markPass(field);
      return true;
    } catch (error) {
      markFail(field, error);
      return false;
    }
  }

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || "";
  if (page.url() === "about:blank") {
    if (!loginUrl) {
      throw new Error(
        "Browser opened on about:blank. Provide SALEADS_LOGIN_URL (or SALEADS_BASE_URL) so the test can reach the current SaleADS login page without hardcoding an environment URL.",
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await runFieldStep("Login", [], async () => {
    const loginButton = await firstVisibleLocator([
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /sign in with google/i }),
      page.getByRole("link", { name: /iniciar sesi[oó]n con google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[oó]n con google/i),
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await waitForUi(popup);
      const popupAccount = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
      if (await popupAccount.isVisible({ timeout: 10000 }).catch(() => false)) {
        await popupAccount.click();
      }
      await popup.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});
    } else if (/accounts\.google\.com/i.test(page.url())) {
      const accountOnPage = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
      if (await accountOnPage.isVisible({ timeout: 10000 }).catch(() => false)) {
        await accountOnPage.click();
      }
    }

    await waitForUi(page);
    await expect(
      await firstVisibleLocator([
        page.locator("aside"),
        page.getByRole("navigation"),
        page.getByText(/^Negocio$/i),
        page.getByText(/Mi Negocio/i),
      ]),
    ).toBeVisible();

    evidence.dashboard = await safeScreenshot(page, testInfo, "01-dashboard-loaded.png");
  });

  await runFieldStep("Mi Negocio menu", ["Login"], async () => {
    const negocioEntry = await firstVisibleLocator([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
      page.getByText(/Mi Negocio/i),
    ]);

    await negocioEntry.click();
    await waitForUi(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    evidence.miNegocioExpanded = await safeScreenshot(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runFieldStep("Agregar Negocio modal", ["Mi Negocio menu"], async () => {
    await page.getByText(/^Agregar Negocio$/i).first().click();
    await waitForUi(page);

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible();

    await expect(
      await firstVisibleLocator([
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.getByText(/Nombre del Negocio/i),
      ]),
    ).toBeVisible();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    evidence.agregarNegocioModal = await safeScreenshot(page, testInfo, "03-agregar-negocio-modal.png");

    const nameInput = await firstVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
    ]);
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await page.getByRole("button", { name: /Cancelar/i }).first().click();
    await waitForUi(page);
  });

  await runFieldStep("Administrar Negocios view", ["Mi Negocio menu"], async () => {
    const administrarVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const negocioEntry = await firstVisibleLocator([
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
        page.getByText(/Mi Negocio/i),
      ]);
      await negocioEntry.click();
      await waitForUi(page);
    }

    await page.getByText(/^Administrar Negocios$/i).first().click();
    await waitForUi(page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    evidence.administrarNegocios = await safeScreenshot(page, testInfo, "04-administrar-negocios-page.png", {
      fullPage: true,
    });
  });

  await runFieldStep("Información General", ["Administrar Negocios view"], async () => {
    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runFieldStep("Detalles de la Cuenta", ["Administrar Negocios view"], async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runFieldStep("Tus Negocios", ["Administrar Negocios view"], async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  async function validateLegalLink(field, linkTextRegex, headingRegex, screenshotName) {
    await runFieldStep(field, ["Administrar Negocios view"], async () => {
      const sourcePage = page;
      const popupPromise = sourcePage.waitForEvent("popup", { timeout: 7000 }).catch(() => null);

      await sourcePage.getByRole("link", { name: linkTextRegex }).first().click();
      await waitForUi(sourcePage);

      const popup = await popupPromise;
      const legalPage = popup || sourcePage;
      await waitForUi(legalPage);

      await expect(
        await firstVisibleLocator([
          legalPage.getByRole("heading", { name: headingRegex }),
          legalPage.getByText(headingRegex),
        ]),
      ).toBeVisible();

      await expect(legalPage.locator("p, li").first()).toBeVisible();
      legalUrls[field] = legalPage.url();
      evidence[field] = await safeScreenshot(legalPage, testInfo, screenshotName, { fullPage: true });

      if (popup) {
        await popup.close();
        await sourcePage.bringToFront();
      } else {
        await sourcePage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(sourcePage);
      }
    });
  }

  await validateLegalLink(
    "Términos y Condiciones",
    /Términos y Condiciones/i,
    /Términos y Condiciones/i,
    "05-terminos-y-condiciones.png",
  );

  await validateLegalLink(
    "Política de Privacidad",
    /Política de Privacidad/i,
    /Política de Privacidad/i,
    "06-politica-de-privacidad.png",
  );

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    statuses: report,
    details,
    legalUrls,
    evidence,
  };

  const finalReportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  fs.writeFileSync(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  const failedFields = REPORT_FIELDS.filter((field) => report[field] !== "PASS");
  expect(failedFields, `Failed or skipped fields: ${failedFields.join(", ")}`).toEqual([]);
});
