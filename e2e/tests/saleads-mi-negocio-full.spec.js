const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

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

function createReportTemplate() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {});
}

async function waitForUi(targetPage) {
  await targetPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
  await targetPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await targetPage.waitForTimeout(500);
}

async function clickFirstVisible(targetPage, locators, friendlyName) {
  for (const locator of locators) {
    const candidate = locator.first();
    const visible = await candidate.isVisible({ timeout: 3000 }).catch(() => false);
    if (!visible) {
      continue;
    }

    await candidate.click();
    await waitForUi(targetPage);
    return candidate;
  }

  throw new Error(`Could not find visible element for "${friendlyName}".`);
}

async function pickGoogleAccountIfVisible(targetPage) {
  const accountLocators = [
    targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
    targetPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
    targetPage.locator(`[data-email="${GOOGLE_ACCOUNT_EMAIL}"]`),
  ];

  for (const locator of accountLocators) {
    const candidate = locator.first();
    const visible = await candidate.isVisible({ timeout: 6000 }).catch(() => false);
    if (!visible) {
      continue;
    }

    await candidate.click();
    await waitForUi(targetPage);
    return true;
  }

  return false;
}

async function captureCheckpoint(targetPage, checkpointDir, name, fullPage = false) {
  const screenshotPath = path.join(checkpointDir, `${name}.png`);
  await targetPage.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function runStep(stepName, report, failures, callback) {
  try {
    await callback();
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = "FAIL";
    failures.push({
      step: stepName,
      error: error instanceof Error ? error.message : String(error),
    });
  }
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    test.setTimeout(5 * 60 * 1000);

    const report = createReportTemplate();
    const failures = [];
    const evidence = {
      screenshots: [],
      legalUrls: {
        terminosYCondiciones: "",
        politicaDePrivacidad: "",
      },
    };

    const checkpointDir = path.join(testInfo.outputDir, "checkpoints");
    await fs.mkdir(checkpointDir, { recursive: true });

    const configuredUrl = process.env.SALEADS_URL || process.env.BASE_URL;
    if (!configuredUrl) {
      throw new Error(
        "Set SALEADS_URL (or BASE_URL) to the current SaleADS login page URL. The test never hardcodes a domain."
      );
    }

    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    await runStep("Login", report, failures, async () => {
      const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
      const contextPagePromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

      await clickFirstVisible(
        page,
        [
          page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
          page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google|google/i),
        ],
        "Login / Sign in with Google"
      );

      const popupPage = (await popupPromise) || (await contextPagePromise);
      if (popupPage) {
        await waitForUi(popupPage);
        await pickGoogleAccountIfVisible(popupPage);
      } else {
        await pickGoogleAccountIfVisible(page);
      }

      await expect(page.locator("aside, nav")).toBeVisible({ timeout: 60000 });
      await expect(page.getByText(/negocio|mi negocio/i)).toBeVisible({ timeout: 60000 });

      evidence.screenshots.push(await captureCheckpoint(page, checkpointDir, "01-dashboard-loaded"));
    });

    await runStep("Mi Negocio menu", report, failures, async () => {
      await clickFirstVisible(
        page,
        [
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByRole("link", { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i),
        ],
        "Negocio section"
      );

      await clickFirstVisible(
        page,
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        "Mi Negocio option"
      );

      await expect(page.getByText(/Agregar Negocio/i)).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Administrar Negocios/i)).toBeVisible({ timeout: 20000 });

      evidence.screenshots.push(await captureCheckpoint(page, checkpointDir, "02-mi-negocio-menu-expanded"));
    });

    await runStep("Agregar Negocio modal", report, failures, async () => {
      await clickFirstVisible(
        page,
        [
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByRole("link", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i),
        ],
        "Agregar Negocio"
      );

      await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 20000 });
      const businessNameInput = page.getByLabel(/Nombre del Negocio/i).or(
        page.getByPlaceholder(/Nombre del Negocio/i)
      );
      await expect(businessNameInput.first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible({ timeout: 20000 });

      evidence.screenshots.push(await captureCheckpoint(page, checkpointDir, "03-agregar-negocio-modal"));

      await businessNameInput.first().click();
      await businessNameInput.first().fill("Negocio Prueba Automatización");
      await clickFirstVisible(page, [page.getByRole("button", { name: /^Cancelar$/i })], "Cancelar button");
    });

    await runStep("Administrar Negocios view", report, failures, async () => {
      const administrarNegociosVisible = await page
        .getByText(/^Administrar Negocios$/i)
        .first()
        .isVisible({ timeout: 1500 })
        .catch(() => false);
      if (!administrarNegociosVisible) {
        await clickFirstVisible(
          page,
          [
            page.getByRole("button", { name: /^Mi Negocio$/i }),
            page.getByRole("link", { name: /^Mi Negocio$/i }),
            page.getByText(/^Mi Negocio$/i),
          ],
          "Mi Negocio option"
        );
      }

      await clickFirstVisible(
        page,
        [
          page.getByRole("button", { name: /^Administrar Negocios$/i }),
          page.getByRole("link", { name: /^Administrar Negocios$/i }),
          page.getByText(/^Administrar Negocios$/i),
        ],
        "Administrar Negocios"
      );

      await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible({ timeout: 30000 });
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 30000 });
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 30000 });
      await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible({ timeout: 30000 });

      evidence.screenshots.push(await captureCheckpoint(page, checkpointDir, "04-administrar-negocios", true));
    });

    await runStep("Información General", report, failures, async () => {
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 20000 });
      await expect(page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i").first()).toBeVisible({
        timeout: 20000,
      });

      const infoSection = page.locator("section, div").filter({ hasText: /Informaci[oó]n General/i }).first();
      const infoText = await infoSection.innerText();
      const hasLikelyUserName = infoText
        .split("\n")
        .map((line) => line.trim())
        .some((line) => /^[A-Za-zÀ-ÖØ-öø-ÿ][A-Za-zÀ-ÖØ-öø-ÿ\s.'-]{2,}$/.test(line));

      if (!hasLikelyUserName) {
        throw new Error("Could not confirm a visible user name in 'Información General'.");
      }
    });

    await runStep("Detalles de la Cuenta", report, failures, async () => {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Estado activo/i)).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 20000 });
    });

    await runStep("Tus Negocios", report, failures, async () => {
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 20000 });
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible({ timeout: 20000 });
    });

    async function validateLegalLink({ linkText, headingRegex, evidenceKey, screenshotName }) {
      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

      await clickFirstVisible(
        page,
        [
          page.getByRole("link", { name: linkText }),
          page.getByRole("button", { name: linkText }),
          page.getByText(linkText),
        ],
        `Legal link: ${linkText}`
      );

      const newTab = await popupPromise;
      const legalPage = newTab || page;
      await waitForUi(legalPage);

      const headingVisible = await legalPage
        .getByRole("heading", { name: headingRegex })
        .first()
        .isVisible({ timeout: 10000 })
        .catch(() => false);

      if (!headingVisible) {
        await expect(legalPage.getByText(headingRegex)).toBeVisible({ timeout: 10000 });
      }

      const legalTextLength = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim().length;
      if (legalTextLength < 150) {
        throw new Error(`The "${linkText}" page does not show enough legal content text.`);
      }

      evidence.screenshots.push(await captureCheckpoint(legalPage, checkpointDir, screenshotName));
      evidence.legalUrls[evidenceKey] = legalPage.url();

      if (newTab) {
        await legalPage.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }
    }

    await runStep("Términos y Condiciones", report, failures, async () => {
      await validateLegalLink({
        linkText: /T[eé]rminos y Condiciones/i,
        headingRegex: /T[eé]rminos y Condiciones/i,
        evidenceKey: "terminosYCondiciones",
        screenshotName: "05-terminos-y-condiciones",
      });
    });

    await runStep("Política de Privacidad", report, failures, async () => {
      await validateLegalLink({
        linkText: /Pol[ií]tica de Privacidad/i,
        headingRegex: /Pol[ií]tica de Privacidad/i,
        evidenceKey: "politicaDePrivacidad",
        screenshotName: "06-politica-de-privacidad",
      });
    });

    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      statusByField: report,
      legalUrls: evidence.legalUrls,
      screenshots: evidence.screenshots,
      failures,
    };

    const finalReportPath = path.join(testInfo.outputDir, "saleads-mi-negocio-final-report.json");
    await fs.writeFile(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await testInfo.attach("final-report", {
      path: finalReportPath,
      contentType: "application/json",
    });

    if (failures.length > 0) {
      throw new Error(`One or more workflow validations failed:\n${JSON.stringify(failures, null, 2)}`);
    }
  });
});
