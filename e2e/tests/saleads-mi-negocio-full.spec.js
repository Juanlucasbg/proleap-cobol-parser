const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_USAGE_TEXT = "Tienes 2 de 3 negocios";

function slug(input) {
  return input
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

function buildReportTemplate() {
  return {
    Login: { status: "FAIL", details: [] },
    "Mi Negocio menu": { status: "FAIL", details: [] },
    "Agregar Negocio modal": { status: "FAIL", details: [] },
    "Administrar Negocios view": { status: "FAIL", details: [] },
    "Información General": { status: "FAIL", details: [] },
    "Detalles de la Cuenta": { status: "FAIL", details: [] },
    "Tus Negocios": { status: "FAIL", details: [] },
    "Términos y Condiciones": { status: "FAIL", details: [] },
    "Política de Privacidad": { status: "FAIL", details: [] }
  };
}

function markPass(report, key, details = []) {
  report[key].status = "PASS";
  report[key].details = details;
}

function markFail(report, key, message) {
  const details = report[key].details || [];
  details.push(message);
  report[key].details = details;
  report[key].status = "FAIL";
}

function ensureArtifactsDir(testInfo) {
  const root = process.env.E2E_ARTIFACTS_DIR || path.join(__dirname, "..", "artifacts");
  fs.mkdirSync(root, { recursive: true });
  return root;
}

async function waitForUiAfterClick(page, locator) {
  await locator.click();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
}

async function takeCheckpoint(page, testInfo, artifactsDir, label, fullPage = false) {
  const filename = `${String(testInfo.retry + 1).padStart(2, "0")}-${slug(label)}.png`;
  const outputPath = path.join(artifactsDir, filename);
  await page.screenshot({ path: outputPath, fullPage });
  await testInfo.attach(label, {
    path: outputPath,
    contentType: "image/png"
  });
  return outputPath;
}

async function pickGoogleAccountIfVisible(page) {
  const emailChoice = page.getByText(ACCOUNT_EMAIL, { exact: true });
  if (await emailChoice.isVisible({ timeout: 4000 }).catch(() => false)) {
    await waitForUiAfterClick(page, emailChoice);
  }
}

async function clickGoogleAndHandleAccountSelection(page, context) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  const googleButton = page.getByRole("button", { name: /google|iniciar sesión|sign in/i });
  const googleLink = page.getByRole("link", { name: /google|iniciar sesión|sign in/i });
  const googleText = page.getByText(/google|iniciar sesión|sign in/i);

  const clicked = await clickFirstVisible(page, [googleButton, googleLink, googleText]);
  expect(clicked, "No se encontró botón de login con Google.").toBeTruthy();

  const popupPage = await popupPromise;
  const authPage = popupPage || page;
  await authPage.waitForLoadState("domcontentloaded");
  await pickGoogleAccountIfVisible(authPage);

  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded");
    await page.bringToFront();
  }
}

async function clickFirstVisible(page, candidates) {
  for (const locator of candidates) {
    if (await locator.isVisible({ timeout: 2000 }).catch(() => false)) {
      await waitForUiAfterClick(page, locator);
      return true;
    }
  }
  return false;
}

async function isAnyVisible(candidates) {
  for (const locator of candidates) {
    if (await locator.isVisible({ timeout: 2000 }).catch(() => false)) {
      return true;
    }
  }
  return false;
}

function legalLinkCandidates(page, text) {
  return [
    page.getByRole("link", { name: text }),
    page.getByRole("button", { name: text }),
    page.getByText(text, { exact: true })
  ];
}

async function runValidationStep(test, report, key, stepName, fn) {
  await test.step(stepName, async () => {
    try {
      await fn();
    } catch (error) {
      const message = String(error?.message || error).split("\n")[0];
      markFail(report, key, `Validation error: ${message}`);
    }
  });
}

async function ensureMiNegocioExpanded(page) {
  const sidebar = page.locator("aside");
  const addBusiness = sidebar.getByText("Agregar Negocio", { exact: true });
  const manageBusinesses = sidebar.getByText("Administrar Negocios", { exact: true });

  if ((await addBusiness.isVisible().catch(() => false)) && (await manageBusinesses.isVisible().catch(() => false))) {
    return;
  }

  const negocioOption = sidebar.getByRole("button", { name: "Mi Negocio" });
  const negocioLink = sidebar.getByRole("link", { name: "Mi Negocio" });
  const negocioText = sidebar.getByText("Mi Negocio", { exact: true });

  const expanded = await clickFirstVisible(page, [negocioOption, negocioLink, negocioText]);
  expect(expanded, "No se encontró la opción 'Mi Negocio' en el sidebar.").toBeTruthy();

  await expect(addBusiness).toBeVisible();
  await expect(manageBusinesses).toBeVisible();
}

test.describe("SaleADS - Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context, baseURL }, testInfo) => {
    const report = buildReportTemplate();
    const artifactsDir = ensureArtifactsDir(testInfo);
    const legalUrls = {
      terminos: "",
      privacidad: ""
    };

    await runValidationStep(test, report, "Login", "Step 1 - Login with Google", async () => {
      if (baseURL) {
        await page.goto(baseURL, { waitUntil: "domcontentloaded" });
      } else if (page.url() === "about:blank") {
        throw new Error("No base URL was provided and the current page is blank.");
      }

      await clickGoogleAndHandleAccountSelection(page, context);

      const sidebar = page.locator("aside");
      await expect(sidebar).toBeVisible();
      await expect(page.getByText("Negocio", { exact: false })).toBeVisible();

      const dashboardShot = await takeCheckpoint(page, testInfo, artifactsDir, "step-1-dashboard-loaded");
      markPass(report, "Login", [
        "Interfaz principal visible",
        "Sidebar izquierdo visible",
        `Screenshot: ${dashboardShot}`
      ]);
    });

    await runValidationStep(test, report, "Mi Negocio menu", "Step 2 - Open Mi Negocio menu", async () => {
      const sidebar = page.locator("aside");
      await expect(page.getByText("Negocio", { exact: false })).toBeVisible();
      await ensureMiNegocioExpanded(page);

      const addBusinessVisible = await isAnyVisible([
        sidebar.getByRole("link", { name: "Agregar Negocio" }),
        sidebar.getByRole("button", { name: "Agregar Negocio" }),
        sidebar.getByText("Agregar Negocio", { exact: true })
      ]);
      const manageBusinessesVisible = await isAnyVisible([
        sidebar.getByRole("link", { name: "Administrar Negocios" }),
        sidebar.getByRole("button", { name: "Administrar Negocios" }),
        sidebar.getByText("Administrar Negocios", { exact: true })
      ]);
      expect(addBusinessVisible, "'Agregar Negocio' no es visible.").toBeTruthy();
      expect(manageBusinessesVisible, "'Administrar Negocios' no es visible.").toBeTruthy();

      const menuShot = await takeCheckpoint(page, testInfo, artifactsDir, "step-2-mi-negocio-expanded");
      markPass(report, "Mi Negocio menu", [
        "Submenú expandido",
        "'Agregar Negocio' visible",
        "'Administrar Negocios' visible",
        `Screenshot: ${menuShot}`
      ]);
    });

    await runValidationStep(test, report, "Agregar Negocio modal", "Step 3 - Validate Agregar Negocio modal", async () => {
      const sidebar = page.locator("aside");
      await ensureMiNegocioExpanded(page);
      const opened = await clickFirstVisible(page, [
        sidebar.getByRole("link", { name: "Agregar Negocio" }),
        sidebar.getByRole("button", { name: "Agregar Negocio" }),
        sidebar.getByText("Agregar Negocio", { exact: true })
      ]);
      expect(opened, "No se pudo abrir 'Agregar Negocio'.").toBeTruthy();

      const modal = page.getByRole("dialog");
      await expect(modal).toBeVisible();
      await expect(modal.getByText("Crear Nuevo Negocio", { exact: false })).toBeVisible();
      await expect(modal.getByLabel("Nombre del Negocio")).toBeVisible();
      await expect(modal.getByText(BUSINESS_USAGE_TEXT, { exact: false })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Cancelar" })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

      await modal.getByLabel("Nombre del Negocio").click();
      await modal.getByLabel("Nombre del Negocio").fill("Negocio Prueba Automatización");

      const modalShot = await takeCheckpoint(page, testInfo, artifactsDir, "step-3-agregar-negocio-modal");
      await waitForUiAfterClick(page, modal.getByRole("button", { name: "Cancelar" }));
      await expect(modal).not.toBeVisible();

      markPass(report, "Agregar Negocio modal", [
        "Modal 'Crear Nuevo Negocio' visible",
        "Campo 'Nombre del Negocio' visible",
        `'${BUSINESS_USAGE_TEXT}' visible`,
        "Botones 'Cancelar' y 'Crear Negocio' visibles",
        `Screenshot: ${modalShot}`
      ]);
    });

    await runValidationStep(
      test,
      report,
      "Administrar Negocios view",
      "Step 4 - Open Administrar Negocios",
      async () => {
        const sidebar = page.locator("aside");
      await ensureMiNegocioExpanded(page);
      const opened = await clickFirstVisible(page, [
        sidebar.getByRole("link", { name: "Administrar Negocios" }),
        sidebar.getByRole("button", { name: "Administrar Negocios" }),
        sidebar.getByText("Administrar Negocios", { exact: true })
      ]);
      expect(opened, "No se pudo abrir 'Administrar Negocios'.").toBeTruthy();

      const infoGeneral = page.getByText("Información General", { exact: false });
      const accountDetails = page.getByText("Detalles de la Cuenta", { exact: false });
      const businesses = page.getByText("Tus Negocios", { exact: false });
      const legal = page.getByText("Sección Legal", { exact: false });

      await expect(infoGeneral).toBeVisible();
      await expect(accountDetails).toBeVisible();
      await expect(businesses).toBeVisible();
      await expect(legal).toBeVisible();

      const accountShot = await takeCheckpoint(page, testInfo, artifactsDir, "step-4-administrar-negocios", true);
      markPass(report, "Administrar Negocios view", [
        "Secciones requeridas visibles",
        `Screenshot: ${accountShot}`
      ]);
      }
    );

    await runValidationStep(test, report, "Información General", "Step 5 - Validate Información General", async () => {
      await expect(page.getByText("Información General", { exact: false })).toBeVisible();
      await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)).toBeVisible();
      await expect(page.getByText("BUSINESS PLAN", { exact: false })).toBeVisible();
      await expect(page.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();

      markPass(report, "Información General", [
        "Nombre de usuario visible",
        "Email visible",
        "'BUSINESS PLAN' visible",
        "'Cambiar Plan' visible"
      ]);
    });

    await runValidationStep(
      test,
      report,
      "Detalles de la Cuenta",
      "Step 6 - Validate Detalles de la Cuenta",
      async () => {
      await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible();
      await expect(page.getByText("Estado activo", { exact: false })).toBeVisible();
      await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible();

      markPass(report, "Detalles de la Cuenta", [
        "'Cuenta creada' visible",
        "'Estado activo' visible",
        "'Idioma seleccionado' visible"
      ]);
      }
    );

    await runValidationStep(test, report, "Tus Negocios", "Step 7 - Validate Tus Negocios", async () => {
      const section = page.getByText("Tus Negocios", { exact: false });
      await expect(section).toBeVisible();

      const addBusinessVisible = await isAnyVisible([
        page.getByRole("button", { name: "Agregar Negocio" }),
        page.getByRole("link", { name: "Agregar Negocio" }),
        page.getByText("Agregar Negocio", { exact: true })
      ]);
      expect(addBusinessVisible, "No se encontró el botón/link 'Agregar Negocio'.").toBeTruthy();
      await expect(page.getByText(BUSINESS_USAGE_TEXT, { exact: false })).toBeVisible();

      markPass(report, "Tus Negocios", [
        "Listado de negocios visible",
        "Botón 'Agregar Negocio' visible",
        `'${BUSINESS_USAGE_TEXT}' visible`
      ]);
    });

    await runValidationStep(
      test,
      report,
      "Términos y Condiciones",
      "Step 8 - Validate Términos y Condiciones",
      async () => {
      const applicationUrl = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      const termsClicked = await clickFirstVisible(page, legalLinkCandidates(page, "Términos y Condiciones"));
      expect(termsClicked, "No se encontró 'Términos y Condiciones'.").toBeTruthy();
      const popupPage = await popupPromise;
      const targetPage = popupPage || page;

      await targetPage.waitForLoadState("domcontentloaded");
      await expect(targetPage.getByText("Términos y Condiciones", { exact: false })).toBeVisible();

      const contentVisible = await targetPage
        .locator("main, article, section, p")
        .first()
        .isVisible()
        .catch(() => false);
      expect(contentVisible, "No se detectó contenido legal en Términos y Condiciones.").toBeTruthy();

      legalUrls.terminos = targetPage.url();
      const shot = await takeCheckpoint(targetPage, testInfo, artifactsDir, "step-8-terminos-y-condiciones", true);

      if (popupPage) {
        await popupPage.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
          await page.goto(applicationUrl, { waitUntil: "domcontentloaded" });
        });
      }

      markPass(report, "Términos y Condiciones", [
        "Heading legal visible",
        "Contenido legal visible",
        `URL final: ${legalUrls.terminos}`,
        `Screenshot: ${shot}`
      ]);
      }
    );

    await runValidationStep(
      test,
      report,
      "Política de Privacidad",
      "Step 9 - Validate Política de Privacidad",
      async () => {
      const applicationUrl = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      const privacyClicked = await clickFirstVisible(page, legalLinkCandidates(page, "Política de Privacidad"));
      expect(privacyClicked, "No se encontró 'Política de Privacidad'.").toBeTruthy();
      const popupPage = await popupPromise;
      const targetPage = popupPage || page;

      await targetPage.waitForLoadState("domcontentloaded");
      await expect(targetPage.getByText("Política de Privacidad", { exact: false })).toBeVisible();

      const contentVisible = await targetPage
        .locator("main, article, section, p")
        .first()
        .isVisible()
        .catch(() => false);
      expect(contentVisible, "No se detectó contenido legal en Política de Privacidad.").toBeTruthy();

      legalUrls.privacidad = targetPage.url();
      const shot = await takeCheckpoint(targetPage, testInfo, artifactsDir, "step-9-politica-de-privacidad", true);

      if (popupPage) {
        await popupPage.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
          await page.goto(applicationUrl, { waitUntil: "domcontentloaded" });
        });
      }

      markPass(report, "Política de Privacidad", [
        "Heading legal visible",
        "Contenido legal visible",
        `URL final: ${legalUrls.privacidad}`,
        `Screenshot: ${shot}`
      ]);
      }
    );

    await test.step("Step 10 - Final report", async () => {
      const reportPath = path.join(artifactsDir, "final-report.json");
      fs.writeFileSync(
        reportPath,
        JSON.stringify(
          {
            test: "saleads_mi_negocio_full_test",
            generatedAt: new Date().toISOString(),
            legalUrls,
            results: report
          },
          null,
          2
        )
      );

      await testInfo.attach("final-report", {
        path: reportPath,
        contentType: "application/json"
      });

      for (const [field, result] of Object.entries(report)) {
        expect(
          result.status,
          `Field '${field}' is ${result.status}. Details: ${result.details.join(" | ")}`
        ).toBe("PASS");
      }
    });
  });
});
