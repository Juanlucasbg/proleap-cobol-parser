const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const START_URL =
  process.env.SALEADS_LOGIN_URL ||
  process.env.SALEADS_URL ||
  process.env.SALEADS_BASE_URL ||
  "";

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

function createDefaultReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, { status: "NOT_RUN" }]));
}

function markStep(report, field, status, details = "") {
  report[field] = {
    status,
    details,
    checkedAt: new Date().toISOString(),
  };
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10_000 });
  } catch {
    // networkidle is best-effort for SPAs with long polling.
  }
}

async function isVisible(locator) {
  return locator.first().isVisible().catch(() => false);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }
  return null;
}

async function clickByVisibleText(container, pattern) {
  const locator = await firstVisibleLocator([
    container.getByRole("button", { name: pattern }),
    container.getByRole("link", { name: pattern }),
    container.getByRole("menuitem", { name: pattern }),
    container.getByText(pattern),
  ]);

  if (!locator) {
    throw new Error(`Unable to find clickable element with text matching ${pattern}`);
  }

  await locator.click();
}

async function getSidebar(page) {
  const sidebar = await firstVisibleLocator([page.getByRole("navigation"), page.locator("aside")]);
  if (!sidebar) {
    throw new Error("Left sidebar navigation is not visible.");
  }
  return sidebar;
}

async function attachScreenshot(testInfo, page, filename, fullPage = false) {
  const path = testInfo.outputPath(filename);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(filename, { path, contentType: "image/png" });
}

async function resolveBusinessNameInput(page) {
  const input = await firstVisibleLocator([
    page.getByLabel(/Nombre del Negocio/i),
    page.getByPlaceholder(/Nombre del Negocio/i),
    page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
    page.locator("input[name*='nombre'], input[placeholder*='Nombre']"),
  ]);

  if (!input) {
    throw new Error("Input field 'Nombre del Negocio' was not found.");
  }

  return input;
}

async function resolveLegalSection(page) {
  const legalHeading = await firstVisibleLocator([
    page.getByRole("heading", { name: /Sección Legal/i }),
    page.getByText(/Sección Legal/i),
  ]);

  if (!legalHeading) {
    throw new Error("Section 'Sección Legal' does not exist.");
  }

  // Use nearest visual container to scope legal links.
  return legalHeading.locator("xpath=ancestor::*[self::section or self::div][1]");
}

async function openLegalDocument({
  page,
  context,
  legalContainer,
  triggerPattern,
  expectedHeadingPattern,
  checkpointFileName,
  testInfo,
  accountPageUrl,
}) {
  const link = await firstVisibleLocator([
    legalContainer.getByRole("link", { name: triggerPattern }),
    legalContainer.getByRole("button", { name: triggerPattern }),
    legalContainer.getByText(triggerPattern),
    page.getByRole("link", { name: triggerPattern }),
    page.getByRole("button", { name: triggerPattern }),
  ]);

  if (!link) {
    throw new Error(`Unable to find legal link ${triggerPattern}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await link.click();
  const popup = await popupPromise;

  const documentPage = popup || page;
  await waitForUi(documentPage);

  const heading = await firstVisibleLocator([
    documentPage.getByRole("heading", { name: expectedHeadingPattern }),
    documentPage.getByText(expectedHeadingPattern),
  ]);

  if (!heading) {
    throw new Error(`Heading ${expectedHeadingPattern} was not found in legal document.`);
  }

  const bodyText = (await documentPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(bodyText.length).toBeGreaterThan(200);

  await attachScreenshot(testInfo, documentPage, checkpointFileName, true);
  const finalUrl = documentPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      if (accountPageUrl) {
        await page.goto(accountPageUrl, { waitUntil: "domcontentloaded" });
      }
    });
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createDefaultReport();
  const legalUrls = {
    terminosYCondiciones: "",
    politicaDePrivacidad: "",
  };
  let blocked = false;
  let accountPageUrl = "";

  async function runStep(field, fn) {
    if (blocked) {
      markStep(report, field, "BLOCKED", "Skipped because a previous step failed.");
      return;
    }

    try {
      await fn();
      markStep(report, field, "PASS");
    } catch (error) {
      blocked = true;
      markStep(
        report,
        field,
        "FAIL",
        error instanceof Error ? error.message : "Unknown error."
      );
    }
  }

  if (!START_URL) {
    for (const field of REPORT_FIELDS) {
      markStep(
        report,
        field,
        "BLOCKED",
        "Set SALEADS_LOGIN_URL or SALEADS_URL to run this environment-agnostic workflow."
      );
    }
  } else {
    await runStep("Login", async () => {
      await page.goto(START_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);

      const sidebarAlreadyVisible = await firstVisibleLocator([
        page.getByRole("navigation"),
        page.locator("aside"),
      ]);

      if (!sidebarAlreadyVisible) {
        const loginButton = await firstVisibleLocator([
          page.getByRole("button", { name: /Sign in with Google|Google/i }),
          page.getByRole("link", { name: /Sign in with Google|Google/i }),
          page.getByText(/Sign in with Google|Iniciar sesión con Google|Continuar con Google/i),
        ]);

        if (!loginButton) {
          throw new Error("Google login button was not found.");
        }

        const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
        await loginButton.click();
        const popup = await popupPromise;

        const googlePage = popup || page;
        await waitForUi(googlePage);

        const accountOption = await firstVisibleLocator([
          googlePage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
          googlePage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
          googlePage.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        ]);

        if (accountOption) {
          await accountOption.click();
        }

        if (popup) {
          await popup.waitForEvent("close", { timeout: 20_000 }).catch(() => {});
        }
      }

      await waitForUi(page);
      await getSidebar(page);
      await attachScreenshot(testInfo, page, "01-dashboard-loaded.png");
    });

    await runStep("Mi Negocio menu", async () => {
      const sidebar = await getSidebar(page);

      if (!(await isVisible(page.getByText(/Mi Negocio/i)))) {
        await clickByVisibleText(sidebar, /Negocio/i);
        await waitForUi(page);
      }

      await clickByVisibleText(sidebar, /Mi Negocio/i);
      await waitForUi(page);

      await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();

      await attachScreenshot(testInfo, page, "02-mi-negocio-menu-expanded.png");
    });

    await runStep("Agregar Negocio modal", async () => {
      await clickByVisibleText(page, /Agregar Negocio/i);
      await waitForUi(page);

      await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      const businessNameInput = await resolveBusinessNameInput(page);
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");

      await attachScreenshot(testInfo, page, "03-crear-nuevo-negocio-modal.png");

      await clickByVisibleText(page, /Cancelar/i);
      await expect(page.getByText(/Crear Nuevo Negocio/i)).not.toBeVisible();
    });

    await runStep("Administrar Negocios view", async () => {
      if (!(await isVisible(page.getByText(/Administrar Negocios/i)))) {
        await clickByVisibleText(page, /Mi Negocio/i);
        await waitForUi(page);
      }

      await clickByVisibleText(page, /Administrar Negocios/i);
      await waitForUi(page);

      await expect(page.getByText(/Información General/i)).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Sección Legal/i)).toBeVisible();

      accountPageUrl = page.url();
      await attachScreenshot(testInfo, page, "04-administrar-negocios-page-full.png", true);
    });

    await runStep("Información General", async () => {
      await expect(page.getByText(/Información General/i)).toBeVisible();
      await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      const pageText = (await page.locator("body").innerText()).replace(/\s+/g, " ").trim();
      expect(pageText).toMatch(/Nombre|Usuario|Perfil/i);
    });

    await runStep("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await runStep("Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

      const businessSectionText = (
        await page.locator("body").innerText()
      ).replace(/\s+/g, " ");
      expect(businessSectionText).toMatch(/Tus Negocios/);
    });

    await runStep("Términos y Condiciones", async () => {
      const legalContainer = await resolveLegalSection(page);
      legalUrls.terminosYCondiciones = await openLegalDocument({
        page,
        context,
        legalContainer,
        triggerPattern: /Términos y Condiciones/i,
        expectedHeadingPattern: /Términos y Condiciones/i,
        checkpointFileName: "08-terminos-y-condiciones.png",
        testInfo,
        accountPageUrl,
      });
    });

    await runStep("Política de Privacidad", async () => {
      const legalContainer = await resolveLegalSection(page);
      legalUrls.politicaDePrivacidad = await openLegalDocument({
        page,
        context,
        legalContainer,
        triggerPattern: /Política de Privacidad/i,
        expectedHeadingPattern: /Política de Privacidad/i,
        checkpointFileName: "09-politica-de-privacidad.png",
        testInfo,
        accountPageUrl,
      });
    });
  }

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    startedFromUrl: START_URL || null,
    googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
    legalUrls,
    report,
    generatedAt: new Date().toISOString(),
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const failures = Object.entries(report).filter(
    ([, value]) => value.status !== "PASS"
  );
  expect(
    failures,
    `One or more validations failed. Final report: ${JSON.stringify(finalReport, null, 2)}`
  ).toEqual([]);
});
