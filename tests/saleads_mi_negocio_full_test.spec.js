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

function compactError(error) {
  if (!error || !error.message) {
    return "Unknown error";
  }

  return error.message.split("\n")[0].trim();
}

function assertTrue(condition, message) {
  expect(condition, message).toBeTruthy();
}

async function isVisible(locator) {
  return locator.first().isVisible().catch(() => false);
}

async function expectVisible(locator, message) {
  assertTrue(await isVisible(locator), message);
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function firstVisible(locator) {
  const target = locator.first();
  const visible = await target.isVisible().catch(() => false);
  return visible ? target : null;
}

async function findClickableByText(page, text) {
  const regex = new RegExp(escapeRegex(text), "i");
  const candidates = [
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByRole("tab", { name: regex }),
    page.getByRole("option", { name: regex }),
    page.getByText(regex),
  ];

  for (const candidate of candidates) {
    const target = await firstVisible(candidate);
    if (target) {
      return target;
    }
  }

  return null;
}

async function clickAnyText(page, texts) {
  for (const text of texts) {
    const target = await findClickableByText(page, text);
    if (target) {
      await target.click();
      await waitForUiToSettle(page);
      return true;
    }
  }

  return false;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const failures = [];
  const results = REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {});
  const legalUrls = {};

  let appPage = page;
  const openedPages = context.pages();
  const alreadyOpenPage = openedPages.find((candidate) => candidate.url() !== "about:blank");
  if (alreadyOpenPage) {
    appPage = alreadyOpenPage;
    await appPage.bringToFront();
  }

  const evidenceDir = path.join(process.cwd(), "evidence", "saleads_mi_negocio_full_test");
  fs.mkdirSync(evidenceDir, { recursive: true });

  let screenshotIndex = 1;
  async function captureCheckpoint(name, fullPage = false, targetPage = appPage) {
    const fileName = `${String(screenshotIndex).padStart(2, "0")}_${name}.png`;
    screenshotIndex += 1;
    const filePath = path.join(evidenceDir, fileName);
    await targetPage.screenshot({ path: filePath, fullPage });
  }

  async function runSection(reportField, action, options = {}) {
    const { requiresLogin = false, loginReady = true } = options;
    if (requiresLogin && !loginReady) {
      const blockedMessage = "Blocked by Login failure.";
      results[reportField] = `FAIL - ${blockedMessage}`;
      failures.push(`${reportField}: ${blockedMessage}`);
      return false;
    }

    try {
      await action();
      results[reportField] = "PASS";
      return true;
    } catch (error) {
      const message = compactError(error);
      results[reportField] = `FAIL - ${message}`;
      failures.push(`${reportField}: ${message}`);
      return false;
    }
  }

  let loginPassed = false;

  // Step 1: Login with Google.
  loginPassed = await runSection(
    "Login",
    async () => {
      // If no pre-opened login page is available, use environment-provided URL.
      if (appPage.url() === "about:blank") {
        const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
        assertTrue(Boolean(loginUrl), "Set SALEADS_LOGIN_URL or BASE_URL for the target environment.");
        await appPage.goto(loginUrl, { waitUntil: "domcontentloaded" });
        await waitForUiToSettle(appPage);
      }

      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      const clickedLogin = await clickAnyText(appPage, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Iniciar sesion con Google",
        "Continuar con Google",
        "Google",
      ]);
      assertTrue(clickedLogin, "Google login button not found on login page.");

      const popup = await popupPromise;
      const googlePage = popup || appPage;
      await waitForUiToSettle(googlePage);

      const accountEmail = "juanlucasbarbiergarzon@gmail.com";
      const accountTarget = await findClickableByText(googlePage, accountEmail);
      if (accountTarget) {
        await accountTarget.click();
        await waitForUiToSettle(googlePage);
      }

      if (popup) {
        await popup.waitForClose({ timeout: 30000 }).catch(() => {});
      }

      await appPage.bringToFront();
      await waitForUiToSettle(appPage);

      const sidebarVisible =
        (await isVisible(appPage.locator("aside"))) ||
        (await isVisible(appPage.getByRole("navigation"))) ||
        (await isVisible(appPage.getByText(/Mi Negocio|Negocio/i)));
      assertTrue(sidebarVisible, "Main app interface and sidebar are not visible after login.");

      await captureCheckpoint("dashboard_loaded");
    },
    { requiresLogin: false },
  );

  // Step 2: Open Mi Negocio menu.
  await runSection(
    "Mi Negocio menu",
    async () => {
      const negocioVisible = await isVisible(appPage.getByText(/Negocio/i));
      assertTrue(negocioVisible, "Sidebar section 'Negocio' is not visible.");

      const opened = await clickAnyText(appPage, ["Mi Negocio"]);
      assertTrue(opened, "Option 'Mi Negocio' was not found in sidebar.");

      await expectVisible(appPage.getByText(/Agregar Negocio/i), "'Agregar Negocio' is not visible.");
      await expectVisible(appPage.getByText(/Administrar Negocios/i), "'Administrar Negocios' is not visible.");
      await captureCheckpoint("mi_negocio_menu_expanded");
    },
    { requiresLogin: true, loginReady: loginPassed },
  );

  // Step 3: Validate Agregar Negocio modal.
  await runSection(
    "Agregar Negocio modal",
    async () => {
      const opened = await clickAnyText(appPage, ["Agregar Negocio"]);
      assertTrue(opened, "Could not click 'Agregar Negocio'.");

      await expectVisible(appPage.getByText(/Crear Nuevo Negocio/i), "Modal title 'Crear Nuevo Negocio' not visible.");
      await expectVisible(appPage.getByText(/Tienes 2 de 3 negocios/i), "Text 'Tienes 2 de 3 negocios' not visible.");
      await expectVisible(appPage.getByRole("button", { name: /Cancelar/i }), "Button 'Cancelar' not visible.");
      await expectVisible(appPage.getByRole("button", { name: /Crear Negocio/i }), "Button 'Crear Negocio' not visible.");

      const byLabel = await firstVisible(appPage.getByLabel(/Nombre del Negocio/i));
      const byPlaceholder = await firstVisible(appPage.getByPlaceholder(/Nombre del Negocio/i));
      const nameInput = byLabel || byPlaceholder;
      assertTrue(Boolean(nameInput), "Input field 'Nombre del Negocio' not visible.");
      await captureCheckpoint("agregar_negocio_modal");

      await nameInput.click();
      await waitForUiToSettle(appPage);
      await nameInput.fill("Negocio Prueba Automatizacion");
      await waitForUiToSettle(appPage);

      const cancelled = await clickAnyText(appPage, ["Cancelar"]);
      assertTrue(cancelled, "Could not click 'Cancelar' to close modal.");
    },
    { requiresLogin: true, loginReady: loginPassed },
  );

  // Step 4: Open Administrar Negocios.
  await runSection(
    "Administrar Negocios view",
    async () => {
      const adminVisible = await isVisible(appPage.getByText(/Administrar Negocios/i));
      if (!adminVisible) {
        await clickAnyText(appPage, ["Mi Negocio"]);
      }

      const opened = await clickAnyText(appPage, ["Administrar Negocios"]);
      assertTrue(opened, "Could not open 'Administrar Negocios'.");

      await expectVisible(
        appPage.getByText(/Informacion General|Información General/i),
        "Section 'Información General' is not visible.",
      );
      await expectVisible(appPage.getByText(/Detalles de la Cuenta/i), "Section 'Detalles de la Cuenta' is not visible.");
      await expectVisible(appPage.getByText(/Tus Negocios/i), "Section 'Tus Negocios' is not visible.");
      await expectVisible(appPage.getByText(/Seccion Legal|Sección Legal/i), "Section 'Sección Legal' is not visible.");
      await captureCheckpoint("administrar_negocios", true);
    },
    { requiresLogin: true, loginReady: loginPassed },
  );

  // Step 5: Validate Información General.
  await runSection(
    "Información General",
    async () => {
      await expectVisible(appPage.getByText(/BUSINESS PLAN/i), "Text 'BUSINESS PLAN' is not visible.");
      await expectVisible(appPage.getByRole("button", { name: /Cambiar Plan/i }), "Button 'Cambiar Plan' is not visible.");

      const userNameVisible =
        (await isVisible(appPage.getByText(/Nombre/i))) || (await isVisible(appPage.getByText(/Perfil|Cuenta/i)));
      const userEmailVisible = await isVisible(appPage.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i));
      assertTrue(userNameVisible, "User name is not visible.");
      assertTrue(userEmailVisible, "User email is not visible.");
    },
    { requiresLogin: true, loginReady: loginPassed },
  );

  // Step 6: Validate Detalles de la Cuenta.
  await runSection(
    "Detalles de la Cuenta",
    async () => {
      await expectVisible(appPage.getByText(/Cuenta creada/i), "'Cuenta creada' is not visible.");
      await expectVisible(appPage.getByText(/Estado activo/i), "'Estado activo' is not visible.");
      await expectVisible(appPage.getByText(/Idioma seleccionado/i), "'Idioma seleccionado' is not visible.");
    },
    { requiresLogin: true, loginReady: loginPassed },
  );

  // Step 7: Validate Tus Negocios.
  await runSection(
    "Tus Negocios",
    async () => {
      await expectVisible(appPage.getByText(/Tus Negocios/i), "Business list section is not visible.");
      await expectVisible(appPage.getByText(/Agregar Negocio/i), "Button 'Agregar Negocio' is not visible.");
      await expectVisible(appPage.getByText(/Tienes 2 de 3 negocios/i), "Text 'Tienes 2 de 3 negocios' is not visible.");
    },
    { requiresLogin: true, loginReady: loginPassed },
  );

  async function validateLegalLink(reportField, linkText, headingRegex, screenshotName, urlKey) {
    await runSection(
      reportField,
      async () => {
        const link = await findClickableByText(appPage, linkText);
        assertTrue(Boolean(link), `Legal link '${linkText}' is not visible.`);

        const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
        await link.click();
        await waitForUiToSettle(appPage);

        const popup = await popupPromise;
        const legalPage = popup || appPage;
        await waitForUiToSettle(legalPage);

        const headingVisible =
          (await isVisible(legalPage.getByRole("heading", { name: headingRegex }))) ||
          (await isVisible(legalPage.getByText(headingRegex)));
        assertTrue(headingVisible, `Heading for '${linkText}' is not visible.`);

        const bodyText = await legalPage.locator("body").innerText();
        assertTrue(bodyText.trim().length > 120, `Legal content for '${linkText}' appears empty.`);

        await captureCheckpoint(screenshotName, true, legalPage);
        legalUrls[urlKey] = legalPage.url();

        if (popup) {
          await popup.close();
          await appPage.bringToFront();
        } else {
          await appPage.goBack().catch(() => {});
          await waitForUiToSettle(appPage);
        }
      },
      { requiresLogin: true, loginReady: loginPassed },
    );
  }

  // Step 8: Validate Términos y Condiciones.
  await validateLegalLink(
    "Términos y Condiciones",
    "Términos y Condiciones",
    /Terminos y Condiciones|Términos y Condiciones/i,
    "terminos_y_condiciones",
    "TerminosYCondicionesURL",
  );

  // Step 9: Validate Política de Privacidad.
  await validateLegalLink(
    "Política de Privacidad",
    "Política de Privacidad",
    /Politica de Privacidad|Política de Privacidad/i,
    "politica_de_privacidad",
    "PoliticaDePrivacidadURL",
  );

  // Step 10: Final report.
  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    executedAt: new Date().toISOString(),
    results,
    evidence: legalUrls,
  };

  const reportPath = path.join(evidenceDir, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  // Print report for cron log parsers.
  console.log(JSON.stringify(finalReport, null, 2));

  expect(
    failures,
    `One or more validations failed.\n${failures.map((failure) => `- ${failure}`).join("\n")}`,
  ).toHaveLength(0);
});
