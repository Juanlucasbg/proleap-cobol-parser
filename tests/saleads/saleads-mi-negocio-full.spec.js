const fs = require("fs/promises");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TERMS_PATTERN = /T[eé]rminos y Condiciones/i;
const PRIVACY_PATTERN = /Pol[ií]tica de Privacidad/i;

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(1000);
}

async function resolveVisibleLocator(candidates, timeoutMs = 15000) {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      const count = await locator.count();
      if (!count) {
        continue;
      }

      const visible = await locator.isVisible().catch(() => false);
      if (visible) {
        return locator;
      }
    }

    await candidates[0].page().waitForTimeout(250);
  }

  throw new Error("Unable to find visible element from provided candidates.");
}

async function isAnyVisible(candidates) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    const count = await locator.count();
    if (!count) {
      continue;
    }

    const visible = await locator.isVisible().catch(() => false);
    if (visible) {
      return true;
    }
  }

  return false;
}

async function captureCheckpoint(page, testInfo, filename, fullPage = false) {
  const screenshotPath = testInfo.outputPath(filename);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(filename, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function maybeSelectGoogleAccount(page, context) {
  const popup = await page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
  if (popup) {
    await popup.waitForLoadState("domcontentloaded").catch(() => {});
  }

  const possiblePages = popup
    ? [popup, ...context.pages().filter((candidate) => candidate !== page && candidate !== popup), page]
    : [...context.pages().filter((candidate) => candidate !== page), page];

  for (const candidatePage of possiblePages) {
    const emailOption = candidatePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    const visible = await emailOption.isVisible().catch(() => false);
    if (!visible) {
      continue;
    }

    await emailOption.click();
    await waitForUi(candidatePage);

    if (candidatePage !== page) {
      await candidatePage.waitForClose({ timeout: 20000 }).catch(() => {});
    }

    return;
  }
}

async function assertHeadingOrText(page, regex) {
  const heading = page.getByRole("heading", { name: regex }).first();
  const headingVisible = await heading.isVisible().catch(() => false);
  if (headingVisible) {
    await expect(heading).toBeVisible();
    return;
  }

  await expect(page.getByText(regex).first()).toBeVisible();
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("logs in with Google and validates Mi Negocio end-to-end", async ({ page, context }, testInfo) => {
    const startedAt = new Date().toISOString();
    const legalUrls = {};
    const reportFields = [
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
    const report = Object.fromEntries(reportFields.map((field) => [field, "NOT_RUN"]));

    async function runStep(field, action) {
      try {
        await action();
        report[field] = "PASS";
      } catch (error) {
        report[field] = `FAIL - ${error.message}`;
      }
    }

    await runStep("Login", async () => {
      const startUrl = process.env.SALEADS_START_URL || process.env.SALEADS_URL || process.env.BASE_URL;
      if (startUrl) {
        await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      } else if (page.url().startsWith("about:blank")) {
        throw new Error(
          "No login URL detected. Set SALEADS_START_URL (or SALEADS_URL/BASE_URL) for environment-agnostic execution.",
        );
      }

      await waitForUi(page);

      const appLoaded = await isAnyVisible([
        page.getByText(/Negocio/i),
        page.locator("aside"),
        page.getByRole("navigation"),
      ]);

      if (!appLoaded) {
        const googleLogin = await resolveVisibleLocator([
          page.getByRole("button", {
            name: /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Google/i,
          }),
          page.getByRole("link", {
            name: /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Google/i,
          }),
          page.getByText(
            /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Acceder con Google/i,
          ),
        ]);

        await googleLogin.click();
        await waitForUi(page);
        await maybeSelectGoogleAccount(page, context);
      }

      await expect(page.getByText(/Negocio/i).first()).toBeVisible();
      await expect(resolveVisibleLocator([page.locator("aside"), page.getByRole("navigation")])).resolves.toBeTruthy();
      await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png");
    });

    await runStep("Mi Negocio menu", async () => {
      const negocioOption = await resolveVisibleLocator([
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ]);
      await negocioOption.click();
      await waitForUi(page);

      const miNegocioOption = await resolveVisibleLocator([
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ]);
      await miNegocioOption.click();
      await waitForUi(page);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
    });

    await runStep("Agregar Negocio modal", async () => {
      const addBusinessButton = await resolveVisibleLocator([
        page.getByRole("button", { name: /Agregar Negocio/i }),
        page.getByRole("link", { name: /Agregar Negocio/i }),
        page.getByText(/Agregar Negocio/i),
      ]);

      await addBusinessButton.click();
      await waitForUi(page);

      await assertHeadingOrText(page, /Crear Nuevo Negocio/i);

      const businessNameInput = await resolveVisibleLocator([
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator('input[name*="nombre" i], input[placeholder*="Nombre" i]'),
      ]);

      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatizacion");
      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

      const cancelButton = await resolveVisibleLocator([
        page.getByRole("button", { name: /Cancelar/i }),
        page.getByText(/^Cancelar$/i),
      ]);
      await cancelButton.click();
      await waitForUi(page);
    });

    await runStep("Administrar Negocios view", async () => {
      const adminVisible = await isAnyVisible([
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ]);

      if (!adminVisible) {
        const miNegocioOption = await resolveVisibleLocator([
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByRole("link", { name: /Mi Negocio/i }),
          page.getByText(/Mi Negocio/i),
        ]);
        await miNegocioOption.click();
        await waitForUi(page);
      }

      const manageBusinesses = await resolveVisibleLocator([
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ]);

      await manageBusinesses.click();
      await waitForUi(page);

      await assertHeadingOrText(page, /Informaci[oó]n General/i);
      await assertHeadingOrText(page, /Detalles de la Cuenta/i);
      await assertHeadingOrText(page, /Tus Negocios/i);
      await assertHeadingOrText(page, /Secci[oó]n Legal/i);
      await captureCheckpoint(page, testInfo, "04-administrar-negocios-page.png", true);
    });

    await runStep("Información General", async () => {
      await assertHeadingOrText(page, /Informaci[oó]n General/i);
      await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    });

    await runStep("Detalles de la Cuenta", async () => {
      await assertHeadingOrText(page, /Detalles de la Cuenta/i);
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });

    await runStep("Tus Negocios", async () => {
      await assertHeadingOrText(page, /Tus Negocios/i);
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();

      const listLike = await isAnyVisible([
        page.locator("ul li"),
        page.locator("table tbody tr"),
        page.locator('[data-testid*="business"], [class*="business"]'),
      ]);

      if (!listLike) {
        throw new Error("Business list was not detected in Tus Negocios section.");
      }
    });

    async function validateLegalLink(fieldName, linkPattern, headingPattern, screenshotName, urlKey) {
      const appUrlBeforeClick = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

      const legalLink = await resolveVisibleLocator([
        page.getByRole("link", { name: linkPattern }),
        page.getByRole("button", { name: linkPattern }),
        page.getByText(linkPattern),
      ]);

      await legalLink.click();
      await waitForUi(page);

      let legalPage = await popupPromise;
      if (!legalPage) {
        legalPage = page;
      } else {
        await legalPage.waitForLoadState("domcontentloaded").catch(() => {});
      }

      await assertHeadingOrText(legalPage, headingPattern);

      const legalContentContainer = legalPage.locator("main, article, body").first();
      await expect(legalContentContainer).toContainText(/[A-Za-z0-9].{20,}/);
      legalUrls[urlKey] = legalPage.url();
      await captureCheckpoint(legalPage, testInfo, screenshotName, true);

      if (legalPage !== page) {
        await legalPage.close();
        await page.bringToFront();
        await waitForUi(page);
      } else if (page.url() !== appUrlBeforeClick) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }

      // Keep the parameter used in final output mapping explicit.
      return fieldName;
    }

    await runStep("Términos y Condiciones", async () => {
      await validateLegalLink(
        "Términos y Condiciones",
        TERMS_PATTERN,
        TERMS_PATTERN,
        "05-terminos-y-condiciones.png",
        "terminosYCondiciones",
      );
    });

    await runStep("Política de Privacidad", async () => {
      await validateLegalLink(
        "Política de Privacidad",
        PRIVACY_PATTERN,
        PRIVACY_PATTERN,
        "06-politica-de-privacidad.png",
        "politicaDePrivacidad",
      );
    });

    const finishedAt = new Date().toISOString();
    const finalReport = {
      startedAt,
      finishedAt,
      results: report,
      legalUrls,
    };

    const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await testInfo.attach("saleads-mi-negocio-final-report.json", {
      path: reportPath,
      contentType: "application/json",
    });

    console.table(report);
    if (Object.keys(legalUrls).length > 0) {
      console.log("Final legal URLs:", legalUrls);
    }

    const failedFields = Object.entries(report)
      .filter(([, status]) => !String(status).startsWith("PASS"))
      .map(([field, status]) => `${field}: ${status}`);

    expect(
      failedFields,
      `One or more workflow validations failed:\n${failedFields.join("\n")}`,
    ).toEqual([]);
  });
});
