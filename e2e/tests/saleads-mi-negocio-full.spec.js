const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

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

const START_URL_ENV = process.env.SALEADS_URL;

function initReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: [] };
    return acc;
  }, {});
}

function markPass(report, field, detail) {
  report[field].status = "PASS";
  if (detail) {
    report[field].details.push(detail);
  }
}

function markFail(report, field, error) {
  report[field].status = "FAIL";
  report[field].details.push(error instanceof Error ? error.message : String(error));
}

async function settleUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => null);
  await page.waitForLoadState("networkidle", { timeout: 6000 }).catch(() => null);
  await page.waitForTimeout(800);
}

async function clickAndWait(page, locator) {
  await expect(locator.first()).toBeVisible();
  await locator.first().click();
  await settleUi(page);
}

async function firstVisible(...locators) {
  for (const locator of locators) {
    if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
      return locator.first();
    }
  }

  for (const locator of locators) {
    try {
      await locator.first().waitFor({ state: "visible", timeout: 4000 });
      return locator.first();
    } catch (_error) {
      // Try next candidate.
    }
  }

  throw new Error("None of the candidate locators is visible.");
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const file = testInfo.outputPath(
    `${String(testInfo.attachments.length + 1).padStart(2, "0")}-${name}.png`,
  );
  await page.screenshot({ path: file, fullPage });
  await testInfo.attach(name, {
    path: file,
    contentType: "image/png",
  });
}

async function ensureAppReadyAfterLegalNavigation(appPage) {
  await appPage.bringToFront().catch(() => null);
  await settleUi(appPage);
  await expect(
    appPage
      .getByRole("heading", { name: /Informaci[oó]n General/i })
      .or(appPage.getByText(/Informaci[oó]n General/i))
      .first(),
  ).toBeVisible();
}

async function validateLegalLink({
  appPage,
  testInfo,
  linkNamePattern,
  headingPattern,
  report,
  reportField,
  screenshotName,
}) {
  const link = await firstVisible(
    appPage.getByRole("link", { name: linkNamePattern }),
    appPage.getByText(linkNamePattern),
  );

  const existingPages = new Set(appPage.context().pages());
  const originalUrl = appPage.url();

  await clickAndWait(appPage, link);

  const updatedPages = appPage.context().pages();
  const popupPage = updatedPages.find((candidate) => !existingPages.has(candidate)) || null;
  const legalPage = popupPage || appPage;

  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded");
    await popupPage.bringToFront();
    await settleUi(popupPage);
  }

  const legalHeading = await firstVisible(
    legalPage.getByRole("heading", { name: headingPattern }),
    legalPage.getByText(headingPattern),
  );
  await expect(legalHeading).toBeVisible();

  const visibleParagraph = legalPage.locator("p:visible").first();
  if ((await visibleParagraph.count()) > 0) {
    await expect(visibleParagraph).toBeVisible();
  } else {
    await expect(legalPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóú]{30,}/);
  }

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  report[reportField].details.push(`Final URL: ${legalPage.url()}`);

  if (popupPage) {
    await popupPage.close();
    await ensureAppReadyAfterLegalNavigation(appPage);
  } else if (appPage.url() !== originalUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" });
    await ensureAppReadyAfterLegalNavigation(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = initReport();

  testInfo.annotations.push({
    type: "environment-note",
    description:
      "Cross-environment run: no hardcoded domain is used. If SALEADS_URL is provided, it is used as start URL.",
  });

  if (START_URL_ENV) {
    await page.goto(START_URL_ENV, { waitUntil: "domcontentloaded" });
    await settleUi(page);
  }

  await test.step("1) Login with Google", async () => {
    try {
      if (page.url() === "about:blank") {
        throw new Error(
          "Browser is on about:blank. Open SaleADS login page first or set SALEADS_URL env var.",
        );
      }

      const loginButton = await firstVisible(
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
        page.getByRole("button", { name: /google/i }),
      );
      await clickAndWait(page, loginButton);

      const googleAccountSelector = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      if ((await googleAccountSelector.count()) > 0 && (await googleAccountSelector.isVisible())) {
        await clickAndWait(page, googleAccountSelector);
      }

      const sidebar = await firstVisible(
        page.getByRole("navigation").first(),
        page.locator("aside").first(),
        page.getByText(/Negocio|Mi Negocio/i).first(),
      );
      await expect(sidebar).toBeVisible();

      await captureCheckpoint(page, testInfo, "01-dashboard-loaded", true);
      markPass(report, "Login", "Main application interface and left sidebar are visible.");
    } catch (error) {
      markFail(report, "Login", error);
    }
  });

  await test.step("2) Open Mi Negocio menu", async () => {
    try {
      const negocioSection = await firstVisible(
        page.getByText(/^Negocio$/i),
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
      );
      await clickAndWait(page, negocioSection);

      const miNegocioOption = await firstVisible(
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      );
      await clickAndWait(page, miNegocioOption);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

      await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
      markPass(report, "Mi Negocio menu", "Submenu expanded with Agregar/Administrar options.");
    } catch (error) {
      markFail(report, "Mi Negocio menu", error);
    }
  });

  await test.step("3) Validate Agregar Negocio modal", async () => {
    try {
      const agregarNegocioEntry = await firstVisible(
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      );
      await clickAndWait(page, agregarNegocioEntry);

      const modalTitle = await firstVisible(
        page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
        page.getByText(/Crear Nuevo Negocio/i),
      );
      await expect(modalTitle).toBeVisible();

      const businessNameInput = await firstVisible(
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator('input[name*="negocio" i]').first(),
      );
      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

      await businessNameInput.click();
      await settleUi(page);
      await businessNameInput.fill("Negocio Prueba Automatización");
      await settleUi(page);

      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");
      await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }));

      markPass(report, "Agregar Negocio modal", "Modal content and controls validated.");
    } catch (error) {
      markFail(report, "Agregar Negocio modal", error);
    }
  });

  await test.step("4) Open Administrar Negocios", async () => {
    try {
      const administrarVisible = page.getByText(/^Administrar Negocios$/i).first();
      if (!(await administrarVisible.isVisible().catch(() => false))) {
        const miNegocioOption = await firstVisible(
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        );
        await clickAndWait(page, miNegocioOption);
      }

      await clickAndWait(page, page.getByText(/^Administrar Negocios$/i).first());

      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

      await captureCheckpoint(page, testInfo, "04-administrar-negocios-account-page", true);
      markPass(report, "Administrar Negocios view", "Account page sections are present.");
    } catch (error) {
      markFail(report, "Administrar Negocios view", error);
    }
  });

  await test.step("5) Validate Información General", async () => {
    try {
      const infoGeneralCard = page
        .locator("section,div")
        .filter({ has: page.getByText(/Informaci[oó]n General/i) })
        .first();
      await expect(infoGeneralCard).toContainText(/@/);
      await expect(infoGeneralCard).toContainText(/BUSINESS PLAN/i);
      await expect(infoGeneralCard.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      markPass(report, "Información General", "Name/email, plan, and Cambiar Plan button found.");
    } catch (error) {
      markFail(report, "Información General", error);
    }
  });

  await test.step("6) Validate Detalles de la Cuenta", async () => {
    try {
      const detailsCard = page
        .locator("section,div")
        .filter({ has: page.getByText(/Detalles de la Cuenta/i) })
        .first();
      await expect(detailsCard).toContainText(/Cuenta creada/i);
      await expect(detailsCard).toContainText(/Estado activo/i);
      await expect(detailsCard).toContainText(/Idioma seleccionado/i);

      markPass(report, "Detalles de la Cuenta", "Expected account detail labels are visible.");
    } catch (error) {
      markFail(report, "Detalles de la Cuenta", error);
    }
  });

  await test.step("7) Validate Tus Negocios", async () => {
    try {
      const businessesCard = page
        .locator("section,div")
        .filter({ has: page.getByText(/Tus Negocios/i) })
        .first();
      await expect(businessesCard).toBeVisible();
      await expect(businessesCard.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(businessesCard.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();

      markPass(report, "Tus Negocios", "Business list card and controls validated.");
    } catch (error) {
      markFail(report, "Tus Negocios", error);
    }
  });

  await test.step("8) Validate Términos y Condiciones", async () => {
    try {
      await validateLegalLink({
        appPage: page,
        testInfo,
        linkNamePattern: /T[eé]rminos y Condiciones/i,
        headingPattern: /T[eé]rminos y Condiciones/i,
        report,
        reportField: "Términos y Condiciones",
        screenshotName: "05-terminos-y-condiciones",
      });

      markPass(report, "Términos y Condiciones", "Legal heading, content, and final URL validated.");
    } catch (error) {
      markFail(report, "Términos y Condiciones", error);
    }
  });

  await test.step("9) Validate Política de Privacidad", async () => {
    try {
      await validateLegalLink({
        appPage: page,
        testInfo,
        linkNamePattern: /Pol[ií]tica de Privacidad/i,
        headingPattern: /Pol[ií]tica de Privacidad/i,
        report,
        reportField: "Política de Privacidad",
        screenshotName: "06-politica-de-privacidad",
      });

      markPass(report, "Política de Privacidad", "Legal heading, content, and final URL validated.");
    } catch (error) {
      markFail(report, "Política de Privacidad", error);
    }
  });

  await test.step("10) Final report", async () => {
    const summaryLines = REPORT_FIELDS.map((field) => `${field}: ${report[field].status}`);
    const reportPath = testInfo.outputPath("saleads_mi_negocio_report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    await testInfo.attach("saleads-mi-negocio-report", {
      path: reportPath,
      contentType: "application/json",
    });
    await testInfo.attach("saleads-mi-negocio-summary", {
      body: summaryLines.join("\n"),
      contentType: "text/plain",
    });

    console.log(`Detailed report attached at: ${reportPath}`);
    console.table(
      REPORT_FIELDS.map((field) => ({
        step: field,
        status: report[field].status,
        details: report[field].details.join(" | "),
      })),
    );

    expect(REPORT_FIELDS.map((field) => report[field].status)).not.toContain("FAIL");
  });
});
