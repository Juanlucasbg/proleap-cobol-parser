const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");
const {
  clickByVisibleText,
  fillByLabelOrPlaceholder,
  screenshotCheckpoint,
  validateVisibleText,
  waitForUi,
  withPopupOrSameTab
} = require("../utils/workflow-helpers");

const REPORT_PATH = path.join("artifacts", "saleads-mi-negocio-report.json");
const BASE_URL_ENV =
  process.env.SALEADS_URL ||
  process.env.PLAYWRIGHT_BASE_URL ||
  process.env.BASE_URL;
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TXT = {
  negocio: /negocio/i,
  miNegocio: /mi\s*negocio/i,
  agregarNegocio: /agregar\s*negocio/i,
  administrarNegocios: /administrar\s*negocios/i,
  crearNuevoNegocio: /crear\s*nuevo\s*negocio/i,
  nombreDelNegocio: /nombre\s*del\s*negocio/i,
  tienes2de3: /tienes\s*2\s*de\s*3\s*negocios/i,
  cancelar: /cancelar/i,
  crearNegocio: /crear\s*negocio/i,
  infoGeneral: /informaci[oó]n\s*general/i,
  detallesCuenta: /detalles\s*de\s*la\s*cuenta/i,
  tusNegocios: /tus\s*negocios/i,
  seccionLegal: /secci[oó]n\s*legal/i,
  businessPlan: /business\s*plan/i,
  cambiarPlan: /cambiar\s*plan/i,
  cuentaCreada: /cuenta\s*creada/i,
  estadoActivo: /estado\s*activo/i,
  idiomaSeleccionado: /idioma\s*seleccionado/i,
  terminosYCondiciones: /t[eé]rminos?\s*y\s*condiciones/i,
  politicaDePrivacidad: /pol[ií]tica\s*de\s*privacidad/i
};

function createMatrix() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
}

async function ensureArtifactDirs() {
  await fs.mkdir(path.join("artifacts", "screenshots"), { recursive: true });
}

async function optionalGoogleAccountSelection(page) {
  const candidates = [
    page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first(),
    page.locator("div[data-email]").filter({ hasText: GOOGLE_ACCOUNT_EMAIL }).first(),
    page.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }).first(),
    page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }).first()
  ];

  for (const locator of candidates) {
    if (await locator.count()) {
      if (await locator.isVisible().catch(() => false)) {
        await locator.click();
        return true;
      }
    }
  }

  return false;
}

async function waitForMainApp(page) {
  const sidebarCandidates = [
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator("[class*='sidebar']"),
    page.getByText("Negocio", { exact: false })
  ];

  await waitForUi(page);
  await Promise.race(
    sidebarCandidates.map((candidate) =>
      candidate.first().waitFor({ state: "visible", timeout: 45000 })
    )
  );
}

async function validateUserNameVisible(page) {
  const infoHeading = page.getByText(TXT.infoGeneral).first();
  const infoContainer = infoHeading.locator(
    "xpath=ancestor::*[self::section or self::div][1]"
  );
  const infoText = await infoContainer.innerText();
  const infoLines = infoText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
  const candidateName = infoLines.find(
    (line) =>
      /\b[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\b/.test(line) &&
      !/@/.test(line) &&
      !/informaci[oó]n general|business plan|cambiar plan/i.test(line)
  );

  expect(candidateName, "Expected user name to be visible").toBeTruthy();
}

async function validateBusinessListVisible(page) {
  const sectionHeading = page.getByText(TXT.tusNegocios).first();
  const sectionContainer = sectionHeading.locator(
    "xpath=ancestor::*[self::section or self::div][1]"
  );

  const listLikeLocator = sectionContainer.locator(
    "li, [role='listitem'], table tbody tr, [class*='business'], [class*='negocio']"
  );
  const listLikeCount = await listLikeLocator.count();
  if (listLikeCount > 0) {
    await expect(listLikeLocator.first()).toBeVisible({ timeout: 15000 });
    return;
  }

  const textLines = (await sectionContainer.innerText())
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
  const businessNameCandidates = textLines.filter(
    (line) =>
      !TXT.tusNegocios.test(line) &&
      !TXT.agregarNegocio.test(line) &&
      !TXT.tienes2de3.test(line)
  );

  expect(
    businessNameCandidates.length,
    "Expected at least one business entry in Tus Negocios list"
  ).toBeGreaterThan(0);
}

async function assertLegalPageContent(page) {
  await expect.poll(async () => {
    const bodyText = await page.locator("body").innerText();
    return bodyText.trim().length;
  }).toBeGreaterThan(60);
}

async function verifySectionExists(page, sectionTitle) {
  await validateVisibleText(page, sectionTitle, 20000);
}

async function safeBackToAppPage(appPage, legalPage, openedInNewTab) {
  if (openedInNewTab && !legalPage.isClosed()) {
    await legalPage.close();
  } else if (!openedInNewTab) {
    await appPage.goBack().catch(() => null);
    await waitForUi(appPage);
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio module workflow", async ({
    page
  }, testInfo) => {
    const matrix = createMatrix();
    const legalUrls = {
      terminosYCondiciones: "",
      politicaDePrivacidad: ""
    };

    await ensureArtifactDirs();

    // Step 1: Login with Google
    try {
      if (BASE_URL_ENV) {
        await page.goto(BASE_URL_ENV, { waitUntil: "domcontentloaded" });
      } else {
        await waitForUi(page);
        if (page.url().startsWith("about:blank")) {
          throw new Error(
            "No login URL available. Set SALEADS_URL/BASE_URL for the target environment."
          );
        }
      }

      const loginButton = page
        .getByRole("button", { name: /sign in with google|continuar con google|google|iniciar sesi[oó]n/i })
        .first();

      await expect(loginButton).toBeVisible({ timeout: 45000 });

      const authResult = await withPopupOrSameTab(page, async () => {
        await loginButton.click();
      });
      const authPage = authResult.targetPage;
      await optionalGoogleAccountSelection(authPage);

      if (authResult.openedInNewTab) {
        await Promise.race([
          authPage.waitForEvent("close", { timeout: 45000 }).catch(() => null),
          waitForMainApp(page)
        ]);
      }
      await waitForMainApp(page);
      await screenshotCheckpoint(page, "01-dashboard-loaded");

      matrix.Login = "PASS";
    } catch (error) {
      await screenshotCheckpoint(page, "01-login-failed").catch(() => null);
      throw new Error(`Step 1 (Login with Google) failed: ${error.message}`);
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickByVisibleText(page, TXT.negocio);
      await clickByVisibleText(page, TXT.miNegocio);

      await validateVisibleText(page, TXT.agregarNegocio);
      await validateVisibleText(page, TXT.administrarNegocios);
      await screenshotCheckpoint(page, "02-mi-negocio-menu-expanded");

      matrix["Mi Negocio menu"] = "PASS";
    } catch (error) {
      await screenshotCheckpoint(page, "02-mi-negocio-menu-failed").catch(() => null);
      throw new Error(`Step 2 (Open Mi Negocio menu) failed: ${error.message}`);
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, TXT.agregarNegocio);

      await validateVisibleText(page, TXT.crearNuevoNegocio);
      await validateVisibleText(page, TXT.nombreDelNegocio);
      await validateVisibleText(page, TXT.tienes2de3);
      await validateVisibleText(page, TXT.cancelar);
      await validateVisibleText(page, TXT.crearNegocio);
      await screenshotCheckpoint(page, "03-agregar-negocio-modal");

      await fillByLabelOrPlaceholder(
        page,
        TXT.nombreDelNegocio,
        "Negocio Prueba Automatizacion"
      );
      await clickByVisibleText(page, TXT.cancelar);

      matrix["Agregar Negocio modal"] = "PASS";
    } catch (error) {
      await screenshotCheckpoint(page, "03-agregar-negocio-modal-failed").catch(
        () => null
      );
      throw new Error(
        `Step 3 (Validate Agregar Negocio modal) failed: ${error.message}`
      );
    }

    // Step 4: Open Administrar Negocios
    try {
      const administrarLocator = page.getByText(TXT.administrarNegocios, {
        exact: false
      });
      if (!(await administrarLocator.first().isVisible().catch(() => false))) {
        await clickByVisibleText(page, TXT.miNegocio);
      }

      await clickByVisibleText(page, TXT.administrarNegocios);
      await waitForUi(page);

      await verifySectionExists(page, TXT.infoGeneral);
      await verifySectionExists(page, TXT.detallesCuenta);
      await verifySectionExists(page, TXT.tusNegocios);
      await verifySectionExists(page, TXT.seccionLegal);
      await screenshotCheckpoint(page, "04-administrar-negocios-page");

      matrix["Administrar Negocios view"] = "PASS";
    } catch (error) {
      await screenshotCheckpoint(page, "04-administrar-negocios-failed").catch(
        () => null
      );
      throw new Error(`Step 4 (Open Administrar Negocios) failed: ${error.message}`);
    }

    // Step 5: Validate Informacion General
    try {
      await validateVisibleText(page, TXT.infoGeneral);
      await validateUserNameVisible(page);
      await expect(
        page.locator("text=/@/").first(),
        "Expected user email to be visible in Informacion General"
      ).toBeVisible({ timeout: 20000 });
      await validateVisibleText(page, TXT.businessPlan);
      await validateVisibleText(page, TXT.cambiarPlan);
      matrix["Información General"] = "PASS";
    } catch (error) {
      throw new Error(`Step 5 (Validate Informacion General) failed: ${error.message}`);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await validateVisibleText(page, TXT.cuentaCreada);
      await validateVisibleText(page, TXT.estadoActivo);
      await validateVisibleText(page, TXT.idiomaSeleccionado);
      matrix["Detalles de la Cuenta"] = "PASS";
    } catch (error) {
      throw new Error(
        `Step 6 (Validate Detalles de la Cuenta) failed: ${error.message}`
      );
    }

    // Step 7: Validate Tus Negocios
    try {
      await validateVisibleText(page, TXT.tusNegocios);
      await validateBusinessListVisible(page);
      await validateVisibleText(page, TXT.agregarNegocio);
      await validateVisibleText(page, TXT.tienes2de3);
      matrix["Tus Negocios"] = "PASS";
    } catch (error) {
      throw new Error(`Step 7 (Validate Tus Negocios) failed: ${error.message}`);
    }

    // Step 8: Validate Terminos y Condiciones
    try {
      const termsResult = await withPopupOrSameTab(page, async () => {
        await clickByVisibleText(page, TXT.terminosYCondiciones);
      });
      const termsPage = termsResult.targetPage;

      await validateVisibleText(termsPage, TXT.terminosYCondiciones, 20000);
      await assertLegalPageContent(termsPage);

      legalUrls.terminosYCondiciones = termsPage.url();
      await screenshotCheckpoint(termsPage, "08-terminos-y-condiciones");
      await safeBackToAppPage(page, termsPage, termsResult.openedInNewTab);
      await waitForUi(page);

      matrix["Términos y Condiciones"] = "PASS";
    } catch (error) {
      throw new Error(
        `Step 8 (Validate Terminos y Condiciones) failed: ${error.message}`
      );
    }

    // Step 9: Validate Politica de Privacidad
    try {
      const privacyResult = await withPopupOrSameTab(page, async () => {
        await clickByVisibleText(page, TXT.politicaDePrivacidad);
      });
      const privacyPage = privacyResult.targetPage;

      await validateVisibleText(privacyPage, TXT.politicaDePrivacidad, 20000);
      await assertLegalPageContent(privacyPage);

      legalUrls.politicaDePrivacidad = privacyPage.url();
      await screenshotCheckpoint(privacyPage, "09-politica-de-privacidad");
      await safeBackToAppPage(page, privacyPage, privacyResult.openedInNewTab);
      await waitForUi(page);

      matrix["Política de Privacidad"] = "PASS";
    } catch (error) {
      throw new Error(
        `Step 9 (Validate Politica de Privacidad) failed: ${error.message}`
      );
    } finally {
      const report = {
        testName: "saleads_mi_negocio_full_test",
        startedAt: new Date(testInfo.startTime).toISOString(),
        finishedAt: new Date().toISOString(),
        browser: testInfo.project.name,
        baseUrl: BASE_URL_ENV || page.url(),
        matrix,
        legalUrls
      };

      console.log("saleads_mi_negocio_full_test validation matrix:");
      console.table(matrix);
      console.log("Legal URLs:", legalUrls);

      await fs.writeFile(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
      await testInfo.attach("final-report", {
        path: REPORT_PATH,
        contentType: "application/json"
      });
    }
  });
});
