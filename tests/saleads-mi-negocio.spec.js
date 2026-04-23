const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const ARTIFACTS_DIR = path.join(__dirname, "..", "artifacts", "saleads-mi-negocio");
const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_BUSINESS_NAME = "Negocio Prueba Automatización";

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function writeJson(filePath, data) {
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2), "utf8");
}

function normalize(text) {
  return (text || "").replace(/\s+/g, " ").trim();
}

async function waitForUiIdle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function safeScreenshot(page, fileName) {
  try {
    await page.screenshot({
      path: path.join(ARTIFACTS_DIR, fileName),
      fullPage: true
    });
    return true;
  } catch (_) {
    return false;
  }
}

async function clickByVisibleText(page, textOptions) {
  for (const text of textOptions) {
    const exactLocator = page.getByText(text, { exact: true });
    if (await exactLocator.count()) {
      await exactLocator.first().click();
      await waitForUiIdle(page);
      return text;
    }
    const partialLocator = page.getByText(text);
    if (await partialLocator.count()) {
      await partialLocator.first().click();
      await waitForUiIdle(page);
      return text;
    }
  }
  throw new Error(`Could not click any text option: ${textOptions.join(", ")}`);
}

async function findSidebar(page) {
  const candidates = [
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator('[class*="sidebar"]'),
    page.locator('[class*="SideBar"]')
  ];
  for (const candidate of candidates) {
    if (await candidate.count()) {
      return candidate.first();
    }
  }
  return null;
}

async function isAnyVisible(...locators) {
  for (const locator of locators) {
    if (await locator.count()) {
      if (await locator.first().isVisible().catch(() => false)) {
        return true;
      }
    }
  }
  return false;
}

async function firstVisibleLocator(...locators) {
  for (const locator of locators) {
    if (await locator.count()) {
      const handle = locator.first();
      if (await handle.isVisible()) {
        return handle;
      }
    }
  }
  return null;
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("Login with Google and validate Mi Negocio module workflow", async ({ page }) => {
    const startedAt = new Date().toISOString();
    ensureDir(ARTIFACTS_DIR);

    const report = {
      meta: {
        name: "saleads_mi_negocio_full_test",
        startedAt,
        environment: process.env.SALEADS_BASE_URL || "existing-open-login-page"
      },
      steps: [],
      urls: {}
    };

    const fieldNameByStepId = {
      1: "Login",
      2: "Mi Negocio menu",
      3: "Agregar Negocio modal",
      4: "Administrar Negocios view",
      5: "Información General",
      6: "Detalles de la Cuenta",
      7: "Tus Negocios",
      8: "Términos y Condiciones",
      9: "Política de Privacidad"
    };

    const stepStatusById = {};
    const recordStep = (id, name, passed, details = []) => {
      const status = passed ? "PASS" : "FAIL";
      stepStatusById[id] = status;
      report.steps.push({ id, name, status, details });
    };

    let appPage = page;
    let canContinueFromLogin = false;
    let onAccountPage = false;

    try {
      if (process.env.SALEADS_BASE_URL) {
        await page.goto(process.env.SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
        await waitForUiIdle(page);
      }
      await safeScreenshot(page, "step0-initial-state.png");
    } catch (_) {
      // Keep going to make it possible to run against an already-open login page in interactive setups.
    }

    try {
      // Step 1: Login with Google
      try {
        const loginPopupPromise = page.waitForEvent("popup", { timeout: 12000 }).catch(() => null);
        const loginTrigger = await firstVisibleLocator(
          page.getByRole("button", { name: /sign in with google|google|iniciar sesi[oó]n con google/i }),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
          page.getByRole("button", { name: /iniciar sesi[oó]n|login|acceder/i })
        );

        if (!loginTrigger) {
          throw new Error("Login trigger/button not found on current page.");
        }

        await loginTrigger.click();
        await waitForUiIdle(page);

        const loginPopup = await loginPopupPromise;
        if (loginPopup) {
          await loginPopup.waitForLoadState("domcontentloaded");
          const accountCandidate = loginPopup.getByText(ACCOUNT_EMAIL, { exact: true });
          if (await accountCandidate.count()) {
            await accountCandidate.first().click();
            await loginPopup.waitForLoadState("networkidle").catch(() => null);
          }
        } else {
          const samePageAccountCandidate = page.getByText(ACCOUNT_EMAIL, { exact: true });
          if (await samePageAccountCandidate.count()) {
            await samePageAccountCandidate.first().click();
            await waitForUiIdle(page);
          }
        }

        await page.waitForLoadState("domcontentloaded");
        await page.waitForTimeout(2500);

        const sidebar = await findSidebar(page);
        const sidebarVisible = sidebar ? await sidebar.isVisible().catch(() => false) : false;
        const mainUiVisible = (await page.locator("main").count()) > 0 || sidebarVisible;

        const screenshotOk = await safeScreenshot(page, "step1-dashboard.png");

        canContinueFromLogin = mainUiVisible && sidebarVisible;
        recordStep(1, "Login", canContinueFromLogin, [
          `Main UI visible: ${mainUiVisible}`,
          `Left sidebar visible: ${sidebarVisible}`,
          `Screenshot captured: ${screenshotOk}`
        ]);
      } catch (error) {
        const screenshotOk = await safeScreenshot(page, "step1-login-failed.png");
        recordStep(1, "Login", false, [String(error.message || error), `Screenshot captured: ${screenshotOk}`]);
      }

      // Step 2: Open Mi Negocio menu
      if (!canContinueFromLogin) {
        recordStep(2, "Mi Negocio menu", false, ["Blocked because login/main app validation failed."]);
      } else {
        try {
          await clickByVisibleText(appPage, ["Negocio"]);
          await clickByVisibleText(appPage, ["Mi Negocio"]);

          const agregarVisible = await isAnyVisible(
            appPage.getByText("Agregar Negocio", { exact: true }),
            appPage.getByRole("button", { name: "Agregar Negocio", exact: true })
          );
          const administrarVisible = await isAnyVisible(
            appPage.getByText("Administrar Negocios", { exact: true }),
            appPage.getByRole("button", { name: "Administrar Negocios", exact: true })
          );

          const screenshotOk = await safeScreenshot(appPage, "step2-mi-negocio-expanded.png");

          const passed = agregarVisible && administrarVisible;
          recordStep(2, "Mi Negocio menu", passed, [
            "Submenu expanded and entries validated.",
            `Agregar Negocio visible: ${agregarVisible}`,
            `Administrar Negocios visible: ${administrarVisible}`,
            `Screenshot captured: ${screenshotOk}`
          ]);
        } catch (error) {
          const screenshotOk = await safeScreenshot(appPage, "step2-mi-negocio-failed.png");
          recordStep(2, "Mi Negocio menu", false, [String(error.message || error), `Screenshot captured: ${screenshotOk}`]);
        }
      }

      // Step 3: Validate Agregar Negocio modal
      if (stepStatusById[2] !== "PASS") {
        recordStep(3, "Agregar Negocio modal", false, ["Blocked because Mi Negocio menu step failed."]);
      } else {
        try {
          await clickByVisibleText(appPage, ["Agregar Negocio"]);

          const modalTitle = appPage.getByText("Crear Nuevo Negocio", { exact: true });
          await expect(modalTitle).toBeVisible({ timeout: 10000 });

          const nombreInput = await firstVisibleLocator(
            appPage.getByLabel("Nombre del Negocio", { exact: true }),
            appPage.getByPlaceholder("Nombre del Negocio"),
            appPage.locator('input[name*="nombre" i], input[placeholder*="Nombre del Negocio"]')
          );

          const quotaText = appPage.getByText("Tienes 2 de 3 negocios", { exact: true });
          const cancelBtn = appPage.getByRole("button", { name: "Cancelar", exact: true });
          const createBtn = appPage.getByRole("button", { name: "Crear Negocio", exact: true });

          const checks = {
            modalTitle: await modalTitle.first().isVisible(),
            nombreInput: !!nombreInput,
            quotaText: await quotaText.first().isVisible(),
            cancelBtn: await cancelBtn.first().isVisible(),
            createBtn: await createBtn.first().isVisible()
          };

          const screenshotOk = await safeScreenshot(appPage, "step3-agregar-negocio-modal.png");

          if (nombreInput) {
            await nombreInput.click();
            await nombreInput.fill(TEST_BUSINESS_NAME);
          }
          await cancelBtn.first().click();
          await waitForUiIdle(appPage);

          const passed = Object.values(checks).every(Boolean);
          recordStep(3, "Agregar Negocio modal", passed, [
            ...Object.entries(checks).map(([k, v]) => `${k}: ${v}`),
            `Screenshot captured: ${screenshotOk}`
          ]);
        } catch (error) {
          const screenshotOk = await safeScreenshot(appPage, "step3-agregar-negocio-failed.png");
          recordStep(3, "Agregar Negocio modal", false, [String(error.message || error), `Screenshot captured: ${screenshotOk}`]);
        }
      }

      // Step 4: Open Administrar Negocios
      if (stepStatusById[2] !== "PASS") {
        recordStep(4, "Administrar Negocios view", false, ["Blocked because Mi Negocio menu step failed."]);
      } else {
        try {
          if (!(await appPage.getByText("Administrar Negocios", { exact: true }).count())) {
            await clickByVisibleText(appPage, ["Mi Negocio"]);
          }

          await clickByVisibleText(appPage, ["Administrar Negocios"]);
          await waitForUiIdle(appPage);

          const sectionChecks = {
            informacionGeneral: await isAnyVisible(appPage.getByText("Información General", { exact: true })),
            detallesCuenta: await isAnyVisible(appPage.getByText("Detalles de la Cuenta", { exact: true })),
            tusNegocios: await isAnyVisible(appPage.getByText("Tus Negocios", { exact: true })),
            seccionLegal: await isAnyVisible(appPage.getByText("Sección Legal", { exact: true }))
          };

          const screenshotOk = await safeScreenshot(appPage, "step4-administrar-negocios.png");

          onAccountPage = Object.values(sectionChecks).every(Boolean);
          recordStep(4, "Administrar Negocios view", onAccountPage, [
            ...Object.entries(sectionChecks).map(([k, v]) => `${k}: ${v}`),
            `Screenshot captured: ${screenshotOk}`
          ]);
        } catch (error) {
          const screenshotOk = await safeScreenshot(appPage, "step4-administrar-negocios-failed.png");
          recordStep(4, "Administrar Negocios view", false, [String(error.message || error), `Screenshot captured: ${screenshotOk}`]);
        }
      }

      // Step 5: Validate Información General
      if (!onAccountPage) {
        recordStep(5, "Información General", false, ["Blocked because Administrar Negocios view did not load correctly."]);
      } else {
        try {
          const hasUserName = (await appPage.locator("text=/^[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}(\\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,})+$/").count()) > 0;
          const hasEmail = (await appPage.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").count()) > 0;
          const businessPlan = await isAnyVisible(appPage.getByText("BUSINESS PLAN", { exact: true }));
          const cambiarPlan = await isAnyVisible(
            appPage.getByRole("button", { name: "Cambiar Plan", exact: true }),
            appPage.getByText("Cambiar Plan", { exact: true })
          );

          const passed = hasUserName && hasEmail && businessPlan && cambiarPlan;
          recordStep(5, "Información General", passed, [
            `User name visible: ${hasUserName}`,
            `User email visible: ${hasEmail}`,
            `BUSINESS PLAN visible: ${businessPlan}`,
            `Cambiar Plan visible: ${cambiarPlan}`
          ]);
        } catch (error) {
          recordStep(5, "Información General", false, [String(error.message || error)]);
        }
      }

      // Step 6: Validate Detalles de la Cuenta
      if (!onAccountPage) {
        recordStep(6, "Detalles de la Cuenta", false, ["Blocked because Administrar Negocios view did not load correctly."]);
      } else {
        try {
          const cuentaCreada = await isAnyVisible(appPage.getByText("Cuenta creada", { exact: true }));
          const estadoActivo = await isAnyVisible(appPage.getByText("Estado activo", { exact: true }));
          const idiomaSeleccionado = await isAnyVisible(appPage.getByText("Idioma seleccionado", { exact: true }));

          const passed = cuentaCreada && estadoActivo && idiomaSeleccionado;
          recordStep(6, "Detalles de la Cuenta", passed, [
            `Cuenta creada visible: ${cuentaCreada}`,
            `Estado activo visible: ${estadoActivo}`,
            `Idioma seleccionado visible: ${idiomaSeleccionado}`
          ]);
        } catch (error) {
          recordStep(6, "Detalles de la Cuenta", false, [String(error.message || error)]);
        }
      }

      // Step 7: Validate Tus Negocios
      if (!onAccountPage) {
        recordStep(7, "Tus Negocios", false, ["Blocked because Administrar Negocios view did not load correctly."]);
      } else {
        try {
          const businessListVisible =
            (await appPage.locator('section:has-text("Tus Negocios") table, section:has-text("Tus Negocios") [role="table"], section:has-text("Tus Negocios") ul').count()) > 0 ||
            (await appPage.locator('text=/Tienes\\s+\\d+\\s+de\\s+\\d+\\s+negocios/i').count()) > 0;
          const agregarNegocioBtn =
            (await appPage.getByRole("button", { name: "Agregar Negocio", exact: true }).count()) > 0 ||
            (await appPage.getByText("Agregar Negocio", { exact: true }).count()) > 0;
          const quotaVisible =
            (await appPage.getByText("Tienes 2 de 3 negocios", { exact: true }).count()) > 0 ||
            (await appPage.locator('text=/Tienes\\s+\\d+\\s+de\\s+\\d+\\s+negocios/i').count()) > 0;

          const passed = businessListVisible && agregarNegocioBtn && quotaVisible;
          recordStep(7, "Tus Negocios", passed, [
            `Business list visible: ${businessListVisible}`,
            `Agregar Negocio exists: ${agregarNegocioBtn}`,
            `Quota text visible: ${quotaVisible}`
          ]);
        } catch (error) {
          recordStep(7, "Tus Negocios", false, [String(error.message || error)]);
        }
      }

      // Step 8: Validate Términos y Condiciones
      if (!onAccountPage) {
        recordStep(8, "Términos y Condiciones", false, ["Blocked because Administrar Negocios view did not load correctly."]);
      } else {
        try {
          const termsLink = await firstVisibleLocator(
            appPage.getByRole("link", { name: /Términos y Condiciones|Terminos y Condiciones/i }),
            appPage.getByText(/Términos y Condiciones|Terminos y Condiciones/i)
          );
          if (!termsLink) {
            throw new Error("Términos y Condiciones link not found.");
          }

          const popupPromise = appPage.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
          await termsLink.click();
          await waitForUiIdle(appPage);

          const popup = await popupPromise;
          const legalPage = popup || appPage;
          if (popup) {
            await popup.waitForLoadState("domcontentloaded");
          }

          await legalPage.waitForLoadState("domcontentloaded");
          await legalPage.waitForTimeout(1000);

          const headingVisible = (await legalPage.getByText(/Términos y Condiciones|Terminos y Condiciones/i).count()) > 0;
          const bodyText = normalize(await legalPage.locator("body").innerText());
          const hasLegalText = bodyText.length > 100;

          const screenshotOk = await safeScreenshot(legalPage, "step8-terminos-y-condiciones.png");
          report.urls.terminosYCondiciones = legalPage.url();

          const passed = headingVisible && hasLegalText;
          recordStep(8, "Términos y Condiciones", passed, [
            `Heading visible: ${headingVisible}`,
            `Legal content visible: ${hasLegalText}`,
            `Final URL: ${legalPage.url()}`,
            `Screenshot captured: ${screenshotOk}`
          ]);

          if (popup && !popup.isClosed()) {
            await popup.close();
          }
          await appPage.bringToFront();
          await waitForUiIdle(appPage);
        } catch (error) {
          recordStep(8, "Términos y Condiciones", false, [String(error.message || error)]);
        }
      }

      // Step 9: Validate Política de Privacidad
      if (!onAccountPage) {
        recordStep(9, "Política de Privacidad", false, ["Blocked because Administrar Negocios view did not load correctly."]);
      } else {
        try {
          const privacyLink = await firstVisibleLocator(
            appPage.getByRole("link", { name: /Política de Privacidad|Politica de Privacidad/i }),
            appPage.getByText(/Política de Privacidad|Politica de Privacidad/i)
          );
          if (!privacyLink) {
            throw new Error("Política de Privacidad link not found.");
          }

          const popupPromise = appPage.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
          await privacyLink.click();
          await waitForUiIdle(appPage);

          const popup = await popupPromise;
          const legalPage = popup || appPage;
          if (popup) {
            await popup.waitForLoadState("domcontentloaded");
          }

          await legalPage.waitForLoadState("domcontentloaded");
          await legalPage.waitForTimeout(1000);

          const headingVisible = (await legalPage.getByText(/Política de Privacidad|Politica de Privacidad/i).count()) > 0;
          const bodyText = normalize(await legalPage.locator("body").innerText());
          const hasLegalText = bodyText.length > 100;

          const screenshotOk = await safeScreenshot(legalPage, "step9-politica-de-privacidad.png");
          report.urls.politicaDePrivacidad = legalPage.url();

          const passed = headingVisible && hasLegalText;
          recordStep(9, "Política de Privacidad", passed, [
            `Heading visible: ${headingVisible}`,
            `Legal content visible: ${hasLegalText}`,
            `Final URL: ${legalPage.url()}`,
            `Screenshot captured: ${screenshotOk}`
          ]);

          if (popup && !popup.isClosed()) {
            await popup.close();
          }
          await appPage.bringToFront();
          await waitForUiIdle(appPage);
        } catch (error) {
          recordStep(9, "Política de Privacidad", false, [String(error.message || error)]);
        }
      }
    } finally {
      report.meta.finishedAt = new Date().toISOString();
      report.summary = {
        Login: fieldNameByStepId[1] ? stepStatusById[1] || "FAIL" : "FAIL",
        "Mi Negocio menu": fieldNameByStepId[2] ? stepStatusById[2] || "FAIL" : "FAIL",
        "Agregar Negocio modal": fieldNameByStepId[3] ? stepStatusById[3] || "FAIL" : "FAIL",
        "Administrar Negocios view": fieldNameByStepId[4] ? stepStatusById[4] || "FAIL" : "FAIL",
        "Información General": fieldNameByStepId[5] ? stepStatusById[5] || "FAIL" : "FAIL",
        "Detalles de la Cuenta": fieldNameByStepId[6] ? stepStatusById[6] || "FAIL" : "FAIL",
        "Tus Negocios": fieldNameByStepId[7] ? stepStatusById[7] || "FAIL" : "FAIL",
        "Términos y Condiciones": fieldNameByStepId[8] ? stepStatusById[8] || "FAIL" : "FAIL",
        "Política de Privacidad": fieldNameByStepId[9] ? stepStatusById[9] || "FAIL" : "FAIL"
      };
      report.allPassed = Object.values(report.summary).every((status) => status === "PASS");

      writeJson(path.join(ARTIFACTS_DIR, "report.json"), report);
    }

    expect(report.allPassed, "One or more SaleADS workflow validations failed. See artifacts/saleads-mi-negocio/report.json").toBeTruthy();
  });
});
