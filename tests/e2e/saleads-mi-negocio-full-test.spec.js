const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

function createReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = { status: "FAIL", details: [] };
  }
  return report;
}

function toErrorMessage(error) {
  if (!error) return "Unknown error";
  if (error instanceof Error) return error.message;
  return String(error);
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function firstVisible(page, locators, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      const candidate = locator.first();
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
    await page.waitForTimeout(250);
  }

  return null;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function attachScreenshot(page, testInfo, name, fullPage = false) {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function setStepStatus(report, stepName, fn) {
  try {
    await fn();
    report[stepName] = { status: "PASS", details: [] };
  } catch (error) {
    report[stepName] = {
      status: "FAIL",
      details: [toErrorMessage(error)],
    };
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  const legalUrls = {
    "Terminos y Condiciones": "",
    "Politica de Privacidad": "",
  };

  let appUrlBeforeLegal = "";

  await setStepStatus(report, "Login", async () => {
    const startUrl = process.env.SALEADS_START_URL || process.env.BASE_URL || "";
    if (page.url() === "about:blank") {
      if (!startUrl) {
        throw new Error(
          "No start URL detected. Set SALEADS_START_URL (or BASE_URL) to the login page of the current environment.",
        );
      }
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const sidebar = page.locator("aside");
    const sidebarVisible = await sidebar.first().isVisible().catch(() => false);

    if (!sidebarVisible) {
      const loginControl = await firstVisible(page, [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ]);

      if (!loginControl) {
        throw new Error("Google login control was not found on the current page.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await loginControl.click();
      await waitForUi(page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});

        const accountSelector = popup.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
        if (await accountSelector.isVisible().catch(() => false)) {
          await accountSelector.click();
        }
      } else {
        const accountSelector = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
        if (await accountSelector.isVisible().catch(() => false)) {
          await accountSelector.click();
        }
      }
    }

    await expect(page.locator("main").first()).toBeVisible({ timeout: 45000 });
    await expect(page.locator("aside").first()).toBeVisible({ timeout: 45000 });
    await attachScreenshot(page, testInfo, "01-dashboard-loaded");
  });

  await setStepStatus(report, "Mi Negocio menu", async () => {
    const negocioSection = await firstVisible(page, [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    if (!negocioSection) {
      throw new Error("Section 'Negocio' was not found in the sidebar.");
    }
    await clickAndWait(page, negocioSection);

    const miNegocioControl = await firstVisible(page, [
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ]);
    if (!miNegocioControl) {
      throw new Error("Option 'Mi Negocio' was not found.");
    }
    await clickAndWait(page, miNegocioControl);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await attachScreenshot(page, testInfo, "02-mi-negocio-menu-expanded");
  });

  await setStepStatus(report, "Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    if (!agregarNegocio) {
      throw new Error("'Agregar Negocio' control was not found.");
    }
    await clickAndWait(page, agregarNegocio);

    await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible();

    const nombreInput = page.getByLabel(/Nombre del Negocio/i).first();
    if (await nombreInput.isVisible().catch(() => false)) {
      await nombreInput.click();
      await nombreInput.fill("Negocio Prueba Automatizacion");
    } else {
      const fallbackInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
      await expect(fallbackInput).toBeVisible();
      await fallbackInput.click();
      await fallbackInput.fill("Negocio Prueba Automatizacion");
    }

    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();
    await attachScreenshot(page, testInfo, "03-agregar-negocio-modal");

    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }));
  });

  await setStepStatus(report, "Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);

    if (!administrarVisible) {
      const miNegocioControl = await firstVisible(page, [
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ]);
      if (miNegocioControl) {
        await clickAndWait(page, miNegocioControl);
      }
    }

    const administrar = await firstVisible(page, [
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ]);
    if (!administrar) {
      throw new Error("'Administrar Negocios' option was not found.");
    }

    await clickAndWait(page, administrar);
    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();
    await attachScreenshot(page, testInfo, "04-administrar-negocios-full-page", true);
  });

  await setStepStatus(report, "Informacion General", async () => {
    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i").first()).toBeVisible();

    const visibleNameCandidate = await firstVisible(page, [
      page.getByText(/Nombre/i),
      page.getByText(/Usuario/i),
      page.locator("h1, h2, h3").filter({ hasNotText: /Informaci[oó]n General|Detalles de la Cuenta|Tus Negocios/i }),
    ], 8000);

    if (!visibleNameCandidate) {
      throw new Error("Could not confirm a visible user name.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await setStepStatus(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await setStepStatus(report, "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  });

  async function validateLegalLink({ reportKey, linkPattern, headingPattern, screenshotName }) {
    const legalLink = await firstVisible(page, [
      page.getByRole("link", { name: linkPattern }),
      page.getByText(linkPattern),
    ]);
    if (!legalLink) {
      throw new Error(`Legal link '${reportKey}' was not found.`);
    }

    appUrlBeforeLegal = appUrlBeforeLegal || page.url();
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

    await legalLink.click();
    await waitForUi(page);

    let legalPage = await popupPromise;
    if (legalPage) {
      await legalPage.waitForLoadState("domcontentloaded", { timeout: 25000 }).catch(() => {});
    } else {
      legalPage = page;
      await legalPage.waitForLoadState("domcontentloaded", { timeout: 25000 }).catch(() => {});
    }

    const heading = await firstVisible(legalPage, [
      legalPage.getByRole("heading", { name: headingPattern }),
      legalPage.getByText(headingPattern),
    ], 25000);

    if (!heading) {
      throw new Error(`Heading for '${reportKey}' was not visible.`);
    }

    await expect(legalPage.locator("body")).toContainText(/\S+/);
    await attachScreenshot(legalPage, testInfo, screenshotName, true);
    legalUrls[reportKey] = legalPage.url();

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (appUrlBeforeLegal && page.url() !== appUrlBeforeLegal) {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  }

  await setStepStatus(report, "Terminos y Condiciones", async () => {
    await validateLegalLink({
      reportKey: "Terminos y Condiciones",
      linkPattern: /T[eé]rminos y Condiciones/i,
      headingPattern: /T[eé]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones",
    });
  });

  await setStepStatus(report, "Politica de Privacidad", async () => {
    await validateLegalLink({
      reportKey: "Politica de Privacidad",
      linkPattern: /Pol[ií]tica de Privacidad/i,
      headingPattern: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad",
    });
  });

  const orderedReport = {};
  for (const field of REPORT_FIELDS) {
    orderedReport[field] = report[field];
  }
  orderedReport.evidence = { legalUrls };

  const reportPath = testInfo.outputPath("final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(orderedReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  // Keep final summary in stdout for CI readability.
  const summaryLines = REPORT_FIELDS.map((field) => `${field}: ${orderedReport[field].status}`);
  // eslint-disable-next-line no-console
  console.log(["SaleADS Mi Negocio validation summary", ...summaryLines, JSON.stringify(legalUrls)].join("\n"));

  const failedSteps = REPORT_FIELDS.filter((field) => orderedReport[field].status !== "PASS");
  expect(
    failedSteps,
    `One or more validations failed:\n${failedSteps.map((step) => `- ${step}`).join("\n")}`,
  ).toEqual([]);
});
