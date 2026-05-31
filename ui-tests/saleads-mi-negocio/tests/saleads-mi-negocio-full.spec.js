const fs = require("node:fs/promises");
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

function slugify(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function isVisible(locator) {
  try {
    return (await locator.count()) > 0 && (await locator.first().isVisible());
  } catch (_error) {
    return false;
  }
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }
  return null;
}

async function waitForUi(targetPage) {
  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForTimeout(900);
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL ||
    null;

  const stepResults = REPORT_FIELDS.reduce((accumulator, field) => {
    accumulator[field] = { status: "FAIL", details: "Not executed." };
    return accumulator;
  }, {});

  const evidence = {
    screenshots: [],
    finalUrls: {},
  };

  const setResult = (field, status, details) => {
    stepResults[field] = { status, details };
  };

  const screenshot = async (label, targetPage = page, fullPage = false) => {
    const index = evidence.screenshots.length + 1;
    const name = `${String(index).padStart(2, "0")}-${slugify(label)}.png`;
    const output = testInfo.outputPath(path.join("screenshots", name));
    await fs.mkdir(path.dirname(output), { recursive: true });
    await targetPage.screenshot({ path: output, fullPage });
    evidence.screenshots.push({
      checkpoint: label,
      path: output,
      pageUrl: targetPage.url(),
    });
  };

  const clickByText = async (texts) => {
    const candidates = [];
    for (const textPattern of texts) {
      candidates.push(page.getByRole("button", { name: textPattern }));
      candidates.push(page.getByRole("link", { name: textPattern }));
      candidates.push(page.getByText(textPattern, { exact: false }));
    }

    const target = await firstVisibleLocator(candidates);
    if (!target) {
      throw new Error(
        `No clickable element found for patterns: ${texts
          .map((pattern) => pattern.toString())
          .join(", ")}`
      );
    }

    await target.click();
    await waitForUi(page);
  };

  const ensureMiNegocioExpanded = async () => {
    const administrarVisible = await isVisible(
      page.getByText(/Administrar Negocios/i)
    );

    if (!administrarVisible) {
      await clickByText([/Mi Negocio/i]);
    }
  };

  const runLegalValidation = async ({
    reportField,
    linkTextRegex,
    headingRegex,
    evidenceKey,
  }) => {
    const newTabPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

    await clickByText([linkTextRegex]);

    const legalPage = await newTabPromise;
    const targetPage = legalPage || page;

    await waitForUi(targetPage);
    await expect(targetPage.getByText(headingRegex).first()).toBeVisible();

    const bodyText = (await targetPage.locator("body").innerText()).trim();
    if (bodyText.length < 150) {
      throw new Error("Legal content appears too short to validate.");
    }

    await screenshot(`${evidenceKey} page`, targetPage, true);
    evidence.finalUrls[evidenceKey] = targetPage.url();

    if (legalPage) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack();
      await waitForUi(page);
    }

    setResult(reportField, "PASS", `Validated legal page and captured URL.`);
  };

  try {
    // Step 1: Login with Google
    try {
      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      } else if (page.url() === "about:blank") {
        throw new Error(
          "No starting URL provided. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL."
        );
      }

      await waitForUi(page);

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickByText([
        /Sign in with Google/i,
        /Iniciar sesion con Google/i,
        /Iniciar con Google/i,
        /Continuar con Google/i,
      ]);

      const popupPage = await popupPromise;
      const authPage = popupPage || page;
      await waitForUi(authPage);

      const accountSelector = authPage.getByText(
        "juanlucasbarbiergarzon@gmail.com",
        { exact: true }
      );

      if (await isVisible(accountSelector)) {
        await accountSelector.click();
        await waitForUi(authPage);
      }

      if (popupPage) {
        await page.bringToFront();
      }

      await expect(page.locator("aside, nav").first()).toBeVisible({
        timeout: 45000,
      });
      await expect(page.getByText(/Negocio/i).first()).toBeVisible({
        timeout: 45000,
      });

      await screenshot("dashboard loaded");
      setResult("Login", "PASS", "Main interface and left sidebar are visible.");
    } catch (error) {
      setResult("Login", "FAIL", error.message);
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      await expect(page.locator("aside, nav").first()).toBeVisible();
      await expect(page.getByText(/Negocio/i).first()).toBeVisible();

      await clickByText([/Mi Negocio/i]);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

      await screenshot("mi negocio menu expanded");
      setResult(
        "Mi Negocio menu",
        "PASS",
        "Submenu expanded with Agregar Negocio and Administrar Negocios."
      );
    } catch (error) {
      setResult("Mi Negocio menu", "FAIL", error.message);
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByText([/Agregar Negocio/i]);
      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
      await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      const businessNameInput = page.getByLabel(/Nombre del Negocio/i);
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatizacion");

      await screenshot("agregar negocio modal");

      await page.getByRole("button", { name: /Cancelar/i }).click();
      await waitForUi(page);

      setResult(
        "Agregar Negocio modal",
        "PASS",
        "Modal fields, limits, and action buttons validated."
      );
    } catch (error) {
      setResult("Agregar Negocio modal", "FAIL", error.message);
    }

    // Step 4: Open Administrar Negocios
    try {
      await ensureMiNegocioExpanded();
      await clickByText([/Administrar Negocios/i]);

      await expect(page.getByText(/Informacion General|Información General/i)).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Seccion Legal|Sección Legal/i)).toBeVisible();

      await screenshot("administrar negocios page", page, true);
      setResult(
        "Administrar Negocios view",
        "PASS",
        "Account page sections are visible."
      );
    } catch (error) {
      setResult("Administrar Negocios view", "FAIL", error.message);
      throw error;
    }

    // Step 5: Validate Informacion General
    try {
      const possibleName = await firstVisibleLocator([
        page.getByText(/Juan/i),
        page.getByText(/Nombre/i),
      ]);
      if (!possibleName) {
        throw new Error("Could not confirm user name visibility.");
      }

      await expect(
        page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false })
      ).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      setResult(
        "Informacion General",
        "PASS",
        "User details, plan label, and Cambiar Plan button are visible."
      );
    } catch (error) {
      setResult("Informacion General", "FAIL", error.message);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();

      setResult(
        "Detalles de la Cuenta",
        "PASS",
        "Cuenta creada, Estado activo, and Idioma seleccionado are visible."
      );
    } catch (error) {
      setResult("Detalles de la Cuenta", "FAIL", error.message);
    }

    // Step 7: Validate Tus Negocios
    try {
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

      const listCandidates = page.locator("li, tr, [role='listitem'], [class*='business']");
      const listCount = await listCandidates.count();
      if (listCount === 0) {
        throw new Error("Could not detect business list entries.");
      }

      setResult(
        "Tus Negocios",
        "PASS",
        "Business list, add button, and business quota text are visible."
      );
    } catch (error) {
      setResult("Tus Negocios", "FAIL", error.message);
    }

    // Step 8: Validate Terminos y Condiciones
    try {
      await runLegalValidation({
        reportField: "Terminos y Condiciones",
        linkTextRegex: /Terminos y Condiciones|Términos y Condiciones/i,
        headingRegex: /Terminos y Condiciones|Términos y Condiciones/i,
        evidenceKey: "terminos-y-condiciones",
      });
    } catch (error) {
      setResult("Terminos y Condiciones", "FAIL", error.message);
    }

    // Step 9: Validate Politica de Privacidad
    try {
      await runLegalValidation({
        reportField: "Politica de Privacidad",
        linkTextRegex: /Politica de Privacidad|Política de Privacidad/i,
        headingRegex: /Politica de Privacidad|Política de Privacidad/i,
        evidenceKey: "politica-de-privacidad",
      });
    } catch (error) {
      setResult("Politica de Privacidad", "FAIL", error.message);
    }
  } finally {
    const reportPayload = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      startUrl: loginUrl,
      currentUrl: page.url(),
      results: stepResults,
      evidence,
    };

    const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(reportPayload, null, 2), "utf8");

    // Keep the report highly visible in terminal output for automation logs.
    // eslint-disable-next-line no-console
    console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_START");
    // eslint-disable-next-line no-console
    console.log(JSON.stringify(reportPayload, null, 2));
    // eslint-disable-next-line no-console
    console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_END");
  }

  const failedFields = REPORT_FIELDS.filter(
    (field) => stepResults[field].status !== "PASS"
  );
  expect(
    failedFields,
    `One or more required validations failed: ${failedFields.join(", ")}`
  ).toEqual([]);
});
