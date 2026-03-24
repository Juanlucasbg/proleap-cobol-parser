const { test, expect } = require("@playwright/test");
const fs = require("fs");

const REPORT_NAME = "saleads_mi_negocio_full_test_report.json";

function sanitizeFilePart(value) {
  return String(value)
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

async function waitForUiStability(page, delayMs = 600) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(delayMs);
}

async function clickByVisibleText(page, candidates, options = {}) {
  const timeout = options.timeout ?? 10_000;

  for (const candidate of candidates) {
    const locator = page.getByRole(candidate.role, { name: candidate.name, exact: candidate.exact ?? false });
    const count = await locator.count();
    if (count > 0) {
      const first = locator.first();
      if (await first.isVisible()) {
        await first.click({ timeout });
        await waitForUiStability(page);
        return { clicked: true, descriptor: candidate };
      }
    }
  }

  // Fallback by plain text when role-based lookup is not enough.
  for (const candidate of candidates) {
    const textMatcher = typeof candidate.name === "string" ? candidate.name : candidate.name;
    const locator = page.getByText(textMatcher, { exact: candidate.exact ?? false });
    const count = await locator.count();
    if (count > 0) {
      const first = locator.first();
      if (await first.isVisible()) {
        await first.click({ timeout });
        await waitForUiStability(page);
        return { clicked: true, descriptor: candidate };
      }
    }
  }

  return { clicked: false, descriptor: null };
}

async function assertAnyVisibleText(page, textPatterns, label) {
  const failures = [];
  for (const pattern of textPatterns) {
    try {
      await expect(page.getByText(pattern).first(), `${label} (pattern: ${pattern})`).toBeVisible();
      return;
    } catch (err) {
      failures.push(String(err));
    }
  }
  throw new Error(`None of the expected texts for "${label}" were visible.\n${failures.join("\n---\n")}`);
}

async function assertVisibleByRoleName(page, role, name, label) {
  const locator = page.getByRole(role, { name });
  await expect(locator.first(), label).toBeVisible();
}

async function saveStepScreenshot(page, testInfo, stepKey) {
  const name = `${String(testInfo.title).slice(0, 40)}-${sanitizeFilePart(stepKey)}.png`;
  const filePath = testInfo.outputPath(name);
  await page.screenshot({ path: filePath, fullPage: true });
  return filePath;
}

async function withStep(stepResults, key, runStep) {
  const result = {
    name: key,
    status: "FAIL",
    details: "",
    evidence: []
  };

  try {
    await runStep(result);
    result.status = "PASS";
    if (!result.details) {
      result.details = "Completed successfully.";
    }
  } catch (error) {
    result.status = "FAIL";
    result.details = error instanceof Error ? error.message : String(error);
  }

  stepResults.push(result);
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    const stepResults = [];

    await withStep(stepResults, "Login", async (step) => {
      // Do not hardcode domains; if url is provided use it, else assume browser is already there.
      const baseUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL || "";
      if (baseUrl) {
        await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
      }
      await waitForUiStability(page, 1000);

      if (!baseUrl && page.url() === "about:blank") {
        throw new Error("No SALEADS_BASE_URL provided and browser is on about:blank; cannot continue.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      const loginClick = await clickByVisibleText(page, [
        { role: "button", name: /sign in with google/i },
        { role: "button", name: /continuar con google/i },
        { role: "button", name: /ingresar con google/i },
        { role: "button", name: /google/i },
        { role: "link", name: /sign in with google/i },
        { role: "link", name: /continuar con google/i }
      ]);
      if (!loginClick.clicked) {
        throw new Error("Could not find a visible Google login button/link.");
      }

      // Handle Google account picker if presented in popup or same tab.
      const accountEmail = "juanlucasbarbiergarzon@gmail.com";
      const popup = await popupPromise;
      const authPage = popup || page;

      try {
        const accountOption = authPage.getByText(accountEmail, { exact: false }).first();
        if (await accountOption.isVisible({ timeout: 6000 })) {
          await accountOption.click();
          await waitForUiStability(authPage, 1000);
        }
      } catch (err) {
        // Account picker may not appear depending on session/cookies.
      }

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => null);
        await page.bringToFront();
      }

      await waitForUiStability(page, 2000);
      await assertAnyVisibleText(page, [/negocio/i, /dashboard/i, /inicio/i], "Main app interface");
      await expect(page.locator("aside").first(), "Left sidebar should be visible").toBeVisible();

      const shot = await saveStepScreenshot(page, testInfo, "01-dashboard-loaded");
      step.evidence.push(shot);
      step.details = "Logged in (or already authenticated), dashboard/sidebar confirmed.";
    });

    await withStep(stepResults, "Mi Negocio menu", async (step) => {
      const openMiNegocio = await clickByVisibleText(page, [
        { role: "link", name: /mi negocio/i },
        { role: "button", name: /mi negocio/i },
        { role: "menuitem", name: /mi negocio/i },
        { role: "link", name: /negocio/i },
        { role: "button", name: /negocio/i }
      ]);
      if (!openMiNegocio.clicked) {
        throw new Error("Could not click 'Mi Negocio' from sidebar.");
      }

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

      const shot = await saveStepScreenshot(page, testInfo, "02-mi-negocio-expanded");
      step.evidence.push(shot);
      step.details = "Mi Negocio expanded with required submenu options visible.";
    });

    await withStep(stepResults, "Agregar Negocio modal", async (step) => {
      const clickAgregar = await clickByVisibleText(page, [
        { role: "link", name: /agregar negocio/i },
        { role: "button", name: /agregar negocio/i },
        { role: "menuitem", name: /agregar negocio/i }
      ]);
      if (!clickAgregar.clicked) {
        throw new Error("Could not click 'Agregar Negocio'.");
      }

      await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
      await expect(page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i)).first()).toBeVisible();
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
      await assertVisibleByRoleName(page, "button", /cancelar/i, "Cancelar button should be visible");
      await assertVisibleByRoleName(page, "button", /crear negocio/i, "Crear Negocio button should be visible");

      const nameInput = page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i)).first();
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatizacion");
      await waitForUiStability(page);

      const shot = await saveStepScreenshot(page, testInfo, "03-agregar-negocio-modal");
      step.evidence.push(shot);

      await page.getByRole("button", { name: /cancelar/i }).first().click();
      await waitForUiStability(page);
      step.details = "Agregar Negocio modal validated and closed with Cancelar.";
    });

    await withStep(stepResults, "Administrar Negocios view", async (step) => {
      // Ensure menu is open again if collapsed.
      if ((await page.getByText(/administrar negocios/i).count()) === 0) {
        await clickByVisibleText(page, [
          { role: "link", name: /mi negocio/i },
          { role: "button", name: /mi negocio/i },
          { role: "menuitem", name: /mi negocio/i }
        ]);
      }

      const clickAdministrar = await clickByVisibleText(page, [
        { role: "link", name: /administrar negocios/i },
        { role: "button", name: /administrar negocios/i },
        { role: "menuitem", name: /administrar negocios/i }
      ]);
      if (!clickAdministrar.clicked) {
        throw new Error("Could not click 'Administrar Negocios'.");
      }

      await assertAnyVisibleText(page, [/informacion general/i, /información general/i], "Informacion General section");
      await assertAnyVisibleText(page, [/detalles de la cuenta/i], "Detalles de la Cuenta section");
      await assertAnyVisibleText(page, [/tus negocios/i], "Tus Negocios section");
      await assertAnyVisibleText(page, [/seccion legal/i, /sección legal/i], "Seccion Legal section");

      const shot = await saveStepScreenshot(page, testInfo, "04-administrar-negocios-page");
      step.evidence.push(shot);
      step.details = "Administrar Negocios page sections are visible.";
    });

    await withStep(stepResults, "Información General", async (step) => {
      await assertAnyVisibleText(page, [/business plan/i], "Business plan text");
      await assertVisibleByRoleName(page, "button", /cambiar plan/i, "Cambiar Plan button should be visible");

      // Flexible checks for user data.
      const possibleEmail = page.locator('text=/@/').first();
      await expect(possibleEmail, "A user email should be visible").toBeVisible();

      // User name can vary; ensure at least one profile-like value is present nearby.
      await assertAnyVisibleText(page, [/usuario/i, /nombre/i, /perfil/i], "User name/profile label");
      step.details = "Informacion General user identity and plan controls validated.";
    });

    await withStep(stepResults, "Detalles de la Cuenta", async (step) => {
      await assertAnyVisibleText(page, [/cuenta creada/i], "Cuenta creada");
      await assertAnyVisibleText(page, [/estado activo/i, /activo/i], "Estado activo");
      await assertAnyVisibleText(page, [/idioma seleccionado/i, /idioma/i], "Idioma seleccionado");
      step.details = "Detalles de la Cuenta fields validated.";
    });

    await withStep(stepResults, "Tus Negocios", async (step) => {
      await assertAnyVisibleText(page, [/tus negocios/i], "Tus Negocios section title");
      await assertVisibleByRoleName(page, "button", /agregar negocio/i, "Agregar Negocio button in Tus Negocios");
      await assertAnyVisibleText(page, [/tienes\s*2\s*de\s*3\s*negocios/i], "Business count");
      step.details = "Tus Negocios list and controls validated.";
    });

    async function validateLegalLink(stepName, linkPattern, headingPattern, screenshotSuffix) {
      await withStep(stepResults, stepName, async (step) => {
        const appPage = page;
        const pagesBefore = context.pages().length;

        const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
        const linkClick = await clickByVisibleText(appPage, [
          { role: "link", name: linkPattern },
          { role: "button", name: linkPattern }
        ]);
        if (!linkClick.clicked) {
          throw new Error(`Could not click legal link for ${stepName}.`);
        }

        let legalPage = await popupPromise;
        if (!legalPage) {
          // Could be same-tab navigation.
          legalPage = appPage;
        } else {
          await legalPage.waitForLoadState("domcontentloaded");
        }

        await waitForUiStability(legalPage, 1200);

        await assertAnyVisibleText(legalPage, [headingPattern], `${stepName} heading`);
        // Generic legal-content check: page body has substantial text.
        const bodyText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
        if (bodyText.length < 200) {
          throw new Error(`${stepName} page does not appear to contain enough legal content.`);
        }

        const shot = await saveStepScreenshot(legalPage, testInfo, screenshotSuffix);
        step.evidence.push(shot);
        step.evidence.push(`final_url=${legalPage.url()}`);

        // Return to app tab when a new tab was used.
        if (legalPage !== appPage && context.pages().length >= pagesBefore) {
          await legalPage.close({ runBeforeUnload: true }).catch(() => null);
          await appPage.bringToFront();
          await waitForUiStability(appPage);
        } else if (legalPage === appPage) {
          // Same-tab navigation: return to app state explicitly.
          await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
          await waitForUiStability(appPage);
        }
      });
    }

    await validateLegalLink(
      "Términos y Condiciones",
      /terminos y condiciones|términos y condiciones/i,
      /terminos y condiciones|términos y condiciones/i,
      "08-terminos"
    );

    await validateLegalLink(
      "Política de Privacidad",
      /politica de privacidad|política de privacidad/i,
      /politica de privacidad|política de privacidad/i,
      "09-politica"
    );

    const orderedReportFields = [
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

    const byName = new Map(stepResults.map((s) => [s.name, s]));
    const report = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      environment: {
        saleadsBaseUrl: process.env.SALEADS_BASE_URL || null,
        pageUrlAtEnd: page.url()
      },
      results: orderedReportFields.map((field) => {
        const found = byName.get(field);
        return {
          field,
          status: found ? found.status : "FAIL",
          details: found ? found.details : "Step did not run.",
          evidence: found ? found.evidence : []
        };
      })
    };

    const reportPath = testInfo.outputPath(REPORT_NAME);
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf8");
    await testInfo.attach(REPORT_NAME, {
      path: reportPath,
      contentType: "application/json"
    });

    // Final assert for whole flow.
    const failed = report.results.filter((r) => r.status !== "PASS");
    expect(failed, `One or more workflow steps failed: ${failed.map((f) => f.field).join(", ")}`).toEqual([]);
  });
});
