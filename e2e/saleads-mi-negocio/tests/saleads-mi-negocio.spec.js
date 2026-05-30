const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

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

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function firstVisible(hostPage, candidates, timeoutMs = 15000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of candidates) {
      const candidate = locator.first();

      if ((await candidate.count()) === 0) {
        continue;
      }

      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }

    await hostPage.waitForTimeout(250);
  }

  return null;
}

async function captureScreenshot(targetPage, testInfo, name, fullPage = false) {
  const screenshotDir = testInfo.outputPath("checkpoints");
  fs.mkdirSync(screenshotDir, { recursive: true });

  await targetPage.screenshot({
    path: path.join(screenshotDir, `${name}.png`),
    fullPage,
  });
}

async function clickAndWaitForUi(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("login with Google and validate all requested sections", async ({
    page,
    context,
  }, testInfo) => {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
    const googleEmail =
      process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";

    const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
    const failures = {};
    const legalUrls = {};

    async function runValidation(field, validation) {
      try {
        await validation();
        report[field] = "PASS";
      } catch (error) {
        failures[field] = error instanceof Error ? error.message : String(error);
      }
    }

    if (!loginUrl) {
      throw new Error(
        "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL) to the login page of the active SaleADS environment.",
      );
    }

    await runValidation("Login", async () => {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });

      const googleLoginButton = await firstVisible(page, [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByText(
          /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
        ),
      ]);

      if (!googleLoginButton) {
        throw new Error("Google login button was not found.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWaitForUi(page, googleLoginButton);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
      }

      const googleSurface = popup || page;
      const accountLocator = await firstVisible(googleSurface, [
        googleSurface.getByText(new RegExp(escapeRegex(googleEmail), "i")),
        googleSurface.getByRole("button", { name: new RegExp(escapeRegex(googleEmail), "i") }),
        googleSurface.getByRole("link", { name: new RegExp(escapeRegex(googleEmail), "i") }),
      ], 10000);

      if (accountLocator) {
        await accountLocator.click();
        await googleSurface.waitForLoadState("domcontentloaded").catch(() => {});
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
      }

      await page.bringToFront();
      await expect(page.locator("aside, nav")).toBeVisible({ timeout: 45000 });
      await expect(page.getByText(/Negocio/i)).toBeVisible({ timeout: 45000 });
      await captureScreenshot(page, testInfo, "step-1-dashboard-loaded");
    });

    await runValidation("Mi Negocio menu", async () => {
      await expect(page.locator("aside, nav")).toBeVisible();
      await expect(page.getByText(/Negocio/i)).toBeVisible();

      const miNegocioOption = await firstVisible(page, [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);

      if (!miNegocioOption) {
        throw new Error("The 'Mi Negocio' option was not found.");
      }

      await clickAndWaitForUi(page, miNegocioOption);
      await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
      await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();
      await captureScreenshot(page, testInfo, "step-2-mi-negocio-expanded");
    });

    await runValidation("Agregar Negocio modal", async () => {
      const addBusinessOption = await firstVisible(page, [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);

      if (!addBusinessOption) {
        throw new Error("The 'Agregar Negocio' action was not found.");
      }

      await clickAndWaitForUi(page, addBusinessOption);

      let modal = page
        .getByRole("dialog")
        .filter({ hasText: /Crear Nuevo Negocio/i })
        .first();

      if ((await modal.count()) === 0) {
        modal = page
          .locator('[role="dialog"], .modal, [class*="modal"]')
          .filter({ hasText: /Crear Nuevo Negocio/i })
          .first();
      }

      await expect(modal).toBeVisible();
      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

      const businessNameInput = await firstVisible(page, [
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
        modal.locator('input[name*="nombre" i], input[placeholder*="Nombre" i]'),
      ]);

      if (!businessNameInput) {
        throw new Error("Input 'Nombre del Negocio' was not found.");
      }

      await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();
      await captureScreenshot(page, testInfo, "step-3-agregar-negocio-modal");

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndWaitForUi(page, modal.getByRole("button", { name: /^Cancelar$/i }));
    });

    await runValidation("Administrar Negocios view", async () => {
      const adminOptionVisible = await page
        .getByText(/^Administrar Negocios$/i)
        .first()
        .isVisible()
        .catch(() => false);

      if (!adminOptionVisible) {
        const miNegocioOption = await firstVisible(page, [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ]);

        if (!miNegocioOption) {
          throw new Error("Could not re-open 'Mi Negocio' menu.");
        }

        await clickAndWaitForUi(page, miNegocioOption);
      }

      const adminOption = await firstVisible(page, [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ]);

      if (!adminOption) {
        throw new Error("The 'Administrar Negocios' option was not found.");
      }

      await clickAndWaitForUi(page, adminOption);
      await expect(page.getByText(/^Información General$/i)).toBeVisible();
      await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
      await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
      await expect(page.getByText(/Sección Legal/i)).toBeVisible();
      await captureScreenshot(page, testInfo, "step-4-administrar-negocios", true);
    });

    await runValidation("Información General", async () => {
      const infoSection = page
        .locator("section, article, div")
        .filter({ has: page.getByText(/^Información General$/i) })
        .first();

      await expect(infoSection).toBeVisible();

      const emailLocator = await firstVisible(page, [
        infoSection.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/),
        infoSection.locator("[data-testid*='email' i], [class*='email' i]"),
      ]);

      if (!emailLocator) {
        throw new Error("User email is not visible in 'Información General'.");
      }

      const candidateTexts = (await infoSection
        .locator("h1, h2, h3, p, span, strong")
        .allTextContents())
        .map((value) => value.trim())
        .filter(Boolean);

      const userName = candidateTexts.find((value) => {
        const isLikelyName =
          /^[A-Za-zÁÉÍÓÚáéíóúÑñ' -]{3,}$/.test(value) && value.split(/\s+/).length >= 2;
        const isKnownLabel =
          /información general|business plan|cambiar plan|cuenta creada|estado activo|idioma seleccionado|tienes \d+ de \d+ negocios/i.test(
            value,
          );
        const isEmail = value.includes("@");
        return isLikelyName && !isKnownLabel && !isEmail;
      });

      if (!userName) {
        throw new Error("User name is not visible in 'Información General'.");
      }

      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    });

    await runValidation("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await runValidation("Tus Negocios", async () => {
      await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
      await expect(
        page.locator("li, tr, [class*='business' i], [data-testid*='business' i]").first(),
      ).toBeVisible();
    });

    async function validateLegalLink(linkText, headingText, reportField, screenshotName, urlKey) {
      await runValidation(reportField, async () => {
        const appUrlBeforeClick = page.url();
        const linkRegex = new RegExp(`^${escapeRegex(linkText)}$`, "i");
        const headingRegex = new RegExp(escapeRegex(headingText), "i");

        const legalLink = await firstVisible(page, [
          page.getByRole("link", { name: linkRegex }),
          page.getByText(linkRegex),
        ]);

        if (!legalLink) {
          throw new Error(`Legal link '${linkText}' was not found.`);
        }

        const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
        await clickAndWaitForUi(page, legalLink);
        const popup = await popupPromise;

        const legalPage = popup || page;
        await legalPage.waitForLoadState("domcontentloaded");
        await legalPage.waitForTimeout(700);

        const heading = await firstVisible(legalPage, [
          legalPage.getByRole("heading", { name: headingRegex }),
          legalPage.getByText(headingRegex),
        ]);

        if (!heading) {
          throw new Error(`Heading '${headingText}' was not found.`);
        }

        await expect(heading).toBeVisible();
        await expect(
          legalPage.locator("main p, article p, p, div").filter({ hasText: /\S+/ }).first(),
        ).toBeVisible();

        await captureScreenshot(legalPage, testInfo, screenshotName, true);
        legalUrls[urlKey] = legalPage.url();

        if (popup) {
          await popup.close();
          await page.bringToFront();
        } else {
          await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
            await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
          });
        }

        await expect(page.locator("aside, nav")).toBeVisible();
      });
    }

    await validateLegalLink(
      "Términos y Condiciones",
      "Términos y Condiciones",
      "Términos y Condiciones",
      "step-8-terminos-y-condiciones",
      "terminos_y_condiciones",
    );

    await validateLegalLink(
      "Política de Privacidad",
      "Política de Privacidad",
      "Política de Privacidad",
      "step-9-politica-de-privacidad",
      "politica_de_privacidad",
    );

    const finalReport = {
      report,
      failures,
      legal_urls: legalUrls,
      generated_at: new Date().toISOString(),
    };

    const reportPath = testInfo.outputPath("final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");

    console.log("SaleADS Mi Negocio final report");
    console.log(JSON.stringify(finalReport, null, 2));

    const failedFields = Object.entries(report)
      .filter(([, status]) => status !== "PASS")
      .map(([field]) => field);

    expect(
      failedFields,
      `Validation failures detected: ${failedFields.join(", ") || "none"}`,
    ).toEqual([]);
  });
});
