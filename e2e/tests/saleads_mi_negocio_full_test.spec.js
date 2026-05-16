const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

function slugify(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function firstVisible(locators) {
  for (const locator of locators) {
    if (await locator.count()) {
      const first = locator.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
  }
  return null;
}

async function saveCheckpoint(page, testInfo, evidence, checkpointName, options = {}) {
  const fileName = `${String(evidence.length + 1).padStart(2, "0")}-${slugify(
    checkpointName,
  )}.png`;
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({
    path: screenshotPath,
    fullPage: !!options.fullPage,
  });
  evidence.push({
    checkpoint: checkpointName,
    file: fileName,
    url: page.url(),
  });
  await testInfo.attach(checkpointName, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

function markStep(report, stepName, passed, details = {}) {
  report[stepName] = {
    status: passed ? "PASS" : "FAIL",
    ...details,
  };
}

test.describe("SaleADS Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    const report = {};
    const evidence = [];
    const errors = [];

    const expectedGoogleAccount =
      process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;
    const expectedUserEmail =
      process.env.SALEADS_EXPECTED_EMAIL || expectedGoogleAccount;
    const expectedUserName = process.env.SALEADS_EXPECTED_USER_NAME;
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;

    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    if (page.url().startsWith("about:blank")) {
      throw new Error(
        "No login page was available. Set SALEADS_LOGIN_URL (or BASE_URL) to the current SaleADS login URL.",
      );
    }

    // Step 1: Login with Google
    try {
      const loginButton = await firstVisible([
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/iniciar sesi[oó]n con google/i),
      ]);

      if (!loginButton) {
        throw new Error("Could not find a 'Sign in with Google' login trigger.");
      }

      const popupPromise = context
        .waitForEvent("page", { timeout: 10_000 })
        .catch(() => null);
      await clickAndWait(loginButton, page);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const accountOption = await firstVisible([
          popup.getByText(expectedGoogleAccount, { exact: true }),
          popup.getByRole("button", { name: expectedGoogleAccount }),
          popup.getByRole("link", { name: expectedGoogleAccount }),
        ]);

        if (accountOption) {
          await accountOption.click();
        }

        await popup.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => {});
        await popup.close().catch(() => {});
      }

      await waitForUi(page);

      const sidebarSignal = await firstVisible([
        page.locator("aside").getByText(/negocio|mi negocio/i),
        page.locator("nav").getByText(/negocio|mi negocio/i),
        page.getByText(/negocio|mi negocio/i),
      ]);

      if (!sidebarSignal) {
        throw new Error(
          "Main interface did not load with the expected left sidebar navigation.",
        );
      }

      await saveCheckpoint(page, testInfo, evidence, "01-dashboard-loaded");
      markStep(report, "Login", true);
    } catch (error) {
      markStep(report, "Login", false, { error: String(error.message || error) });
      errors.push(`Login: ${error.message || error}`);
    }

    // Step 2: Open Mi Negocio menu
    try {
      const negocioEntry = await firstVisible([
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^negocio$/i),
      ]);
      if (negocioEntry) {
        await clickAndWait(negocioEntry, page);
      }

      const miNegocioEntry = await firstVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);
      if (!miNegocioEntry) {
        throw new Error("Could not find 'Mi Negocio' in the sidebar.");
      }
      await clickAndWait(miNegocioEntry, page);

      await expect(page.getByText(/agregar negocio/i)).toBeVisible();
      await expect(page.getByText(/administrar negocios/i)).toBeVisible();

      await saveCheckpoint(page, testInfo, evidence, "02-mi-negocio-expanded");
      markStep(report, "Mi Negocio menu", true);
    } catch (error) {
      markStep(report, "Mi Negocio menu", false, { error: String(error.message || error) });
      errors.push(`Mi Negocio menu: ${error.message || error}`);
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const addBusinessTrigger = await firstVisible([
        page.getByRole("button", { name: /^agregar negocio$/i }),
        page.getByRole("link", { name: /^agregar negocio$/i }),
        page.getByText(/^agregar negocio$/i),
      ]);
      if (!addBusinessTrigger) {
        throw new Error("Could not find 'Agregar Negocio' option.");
      }
      await clickAndWait(addBusinessTrigger, page);

      await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
      const nameInput = page
        .getByLabel(/nombre del negocio/i)
        .or(page.getByPlaceholder(/nombre del negocio/i));
      await expect(nameInput.first()).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      await saveCheckpoint(page, testInfo, evidence, "03-agregar-negocio-modal");

      await nameInput.first().click();
      await nameInput.first().fill("Negocio Prueba Automatizacion");
      await clickAndWait(page.getByRole("button", { name: /cancelar/i }), page);

      markStep(report, "Agregar Negocio modal", true);
    } catch (error) {
      markStep(report, "Agregar Negocio modal", false, {
        error: String(error.message || error),
      });
      errors.push(`Agregar Negocio modal: ${error.message || error}`);
    }

    // Step 4: Open Administrar Negocios
    try {
      if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
        const miNegocioEntry = await firstVisible([
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ]);
        if (miNegocioEntry) {
          await clickAndWait(miNegocioEntry, page);
        }
      }

      const manageBusinessesEntry = await firstVisible([
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ]);
      if (!manageBusinessesEntry) {
        throw new Error("Could not find 'Administrar Negocios'.");
      }
      await clickAndWait(manageBusinessesEntry, page);

      await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

      await saveCheckpoint(page, testInfo, evidence, "04-administrar-negocios", {
        fullPage: true,
      });
      markStep(report, "Administrar Negocios view", true);
    } catch (error) {
      markStep(report, "Administrar Negocios view", false, {
        error: String(error.message || error),
      });
      errors.push(`Administrar Negocios view: ${error.message || error}`);
    }

    // Step 5: Validate Informacion General
    try {
      await expect(page.getByText(/business plan/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

      const emailNode = page.getByText(
        new RegExp(expectedUserEmail.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
      );
      await expect(emailNode).toBeVisible();

      if (expectedUserName) {
        await expect(
          page.getByText(
            new RegExp(expectedUserName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"),
          ),
        ).toBeVisible();
      }

      markStep(report, "Informacion General", true);
    } catch (error) {
      markStep(report, "Informacion General", false, {
        error: String(error.message || error),
      });
      errors.push(`Informacion General: ${error.message || error}`);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await expect(page.getByText(/cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/estado activo/i)).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
      markStep(report, "Detalles de la Cuenta", true);
    } catch (error) {
      markStep(report, "Detalles de la Cuenta", false, {
        error: String(error.message || error),
      });
      errors.push(`Detalles de la Cuenta: ${error.message || error}`);
    }

    // Step 7: Validate Tus Negocios
    try {
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /^agregar negocio$/i })).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      markStep(report, "Tus Negocios", true);
    } catch (error) {
      markStep(report, "Tus Negocios", false, { error: String(error.message || error) });
      errors.push(`Tus Negocios: ${error.message || error}`);
    }

    async function validateLegalLink(linkTextPattern, headingPattern, reportName, checkpointName) {
      const link = await firstVisible([
        page.getByRole("link", { name: linkTextPattern }),
        page.getByRole("button", { name: linkTextPattern }),
        page.getByText(linkTextPattern),
      ]);
      if (!link) {
        throw new Error(`Could not find legal link: ${linkTextPattern}`);
      }

      const popupPromise = context
        .waitForEvent("page", { timeout: 6_000 })
        .catch(() => null);
      await link.click();
      const popup = await popupPromise;
      const targetPage = popup || page;

      await waitForUi(targetPage);
      await expect(targetPage.getByRole("heading", { name: headingPattern })).toBeVisible();

      const bodyText = await targetPage.locator("body").innerText();
      if (!bodyText || bodyText.trim().length < 120) {
        throw new Error("Legal content text is not sufficiently visible.");
      }

      await saveCheckpoint(targetPage, testInfo, evidence, checkpointName);
      const finalUrl = targetPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack().catch(() => {});
        await waitForUi(page);
      }

      markStep(report, reportName, true, { finalUrl });
    }

    // Step 8: Validate Terminos y Condiciones
    try {
      await validateLegalLink(
        /t[eé]rminos y condiciones/i,
        /t[eé]rminos y condiciones/i,
        "Terminos y Condiciones",
        "05-terminos-y-condiciones",
      );
    } catch (error) {
      markStep(report, "Terminos y Condiciones", false, {
        error: String(error.message || error),
      });
      errors.push(`Terminos y Condiciones: ${error.message || error}`);
    }

    // Step 9: Validate Politica de Privacidad
    try {
      await validateLegalLink(
        /pol[ií]tica de privacidad/i,
        /pol[ií]tica de privacidad/i,
        "Politica de Privacidad",
        "06-politica-de-privacidad",
      );
    } catch (error) {
      markStep(report, "Politica de Privacidad", false, {
        error: String(error.message || error),
      });
      errors.push(`Politica de Privacidad: ${error.message || error}`);
    }

    // Step 10: Final report
    const orderedReport = {
      Login: report.Login || { status: "FAIL", error: "Not executed." },
      "Mi Negocio menu": report["Mi Negocio menu"] || {
        status: "FAIL",
        error: "Not executed.",
      },
      "Agregar Negocio modal": report["Agregar Negocio modal"] || {
        status: "FAIL",
        error: "Not executed.",
      },
      "Administrar Negocios view": report["Administrar Negocios view"] || {
        status: "FAIL",
        error: "Not executed.",
      },
      "Informacion General": report["Informacion General"] || {
        status: "FAIL",
        error: "Not executed.",
      },
      "Detalles de la Cuenta": report["Detalles de la Cuenta"] || {
        status: "FAIL",
        error: "Not executed.",
      },
      "Tus Negocios": report["Tus Negocios"] || {
        status: "FAIL",
        error: "Not executed.",
      },
      "Terminos y Condiciones": report["Terminos y Condiciones"] || {
        status: "FAIL",
        error: "Not executed.",
      },
      "Politica de Privacidad": report["Politica de Privacidad"] || {
        status: "FAIL",
        error: "Not executed.",
      },
    };

    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      runAt: new Date().toISOString(),
      loginUrl: loginUrl || page.url(),
      results: orderedReport,
      evidence,
      errors,
    };

    const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    // Also keep a workspace-level copy for easy CI artifact collection.
    const workspaceReportPath = path.resolve(
      __dirname,
      "..",
      "artifacts",
      "saleads-mi-negocio-final-report.json",
    );
    fs.mkdirSync(path.dirname(workspaceReportPath), { recursive: true });
    fs.writeFileSync(workspaceReportPath, JSON.stringify(finalReport, null, 2), "utf8");

    if (errors.length) {
      throw new Error(`One or more workflow validations failed:\n- ${errors.join("\n- ")}`);
    }
  });
});
