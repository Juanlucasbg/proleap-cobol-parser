const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS = [
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

function initializeReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", detail: "Not executed" }])
  );
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function captureCheckpoint(page, testInfo, filename, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(filename),
    fullPage
  });
}

async function clickAndWait(locator, pageForWait) {
  await locator.click();
  await waitForUiToSettle(pageForWait);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    if ((await locator.count()) > 0 && (await locator.first().isVisible().catch(() => false))) {
      return locator.first();
    }
  }
  return null;
}

async function clickByVisibleText(page, regex) {
  const locator = await firstVisibleLocator([
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByText(regex)
  ]);

  if (!locator) {
    throw new Error(`Could not find clickable element with text: ${regex}`);
  }

  await clickAndWait(locator, page);
  return locator;
}

async function ensureOnLoginPage(page) {
  const configuredUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "Browser started on about:blank. Provide SALEADS_LOGIN_URL/SALEADS_BASE_URL or open the SaleADS login page before running."
    );
  }
}

async function completeGoogleLogin(page) {
  const loginButton = await firstVisibleLocator([
    page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
    page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
    page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
    page.locator("button, a").filter({ hasText: /google/i })
  ]);

  if (!loginButton) {
    throw new Error("Google login button was not found on the current page.");
  }

  const popupPromise = page.context().waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await loginButton.click();
  await waitForUiToSettle(page);
  const popup = await popupPromise;

  const accountSelectorRegex = new RegExp(ACCOUNT_EMAIL, "i");

  if (popup) {
    await popup.waitForLoadState("domcontentloaded").catch(() => {});
    const accountOption = await firstVisibleLocator([
      popup.getByText(accountSelectorRegex),
      popup.getByRole("button", { name: accountSelectorRegex }),
      popup.getByRole("link", { name: accountSelectorRegex })
    ]);

    if (accountOption) {
      await clickAndWait(accountOption, popup);
    }

    await popup.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => {});
    await waitForUiToSettle(page);
  } else {
    const accountOption = await firstVisibleLocator([
      page.getByText(accountSelectorRegex),
      page.getByRole("button", { name: accountSelectorRegex }),
      page.getByRole("link", { name: accountSelectorRegex })
    ]);

    if (accountOption) {
      await clickAndWait(accountOption, page);
    }
  }
}

async function openLegalDocumentAndReturn(page, testInfo, linkNameRegex, headingRegex, screenshotName) {
  const appUrlBeforeClick = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  const possibleNavPromise = page.waitForURL(/.*/, { timeout: 8_000 }).catch(() => null);

  await clickByVisibleText(page, linkNameRegex);

  const popup = await popupPromise;
  let targetPage = page;
  if (popup) {
    targetPage = popup;
    await targetPage.waitForLoadState("domcontentloaded");
    await waitForUiToSettle(targetPage);
  } else {
    await possibleNavPromise;
    await waitForUiToSettle(targetPage);
  }

  const heading = await firstVisibleLocator([
    targetPage.getByRole("heading", { name: headingRegex }),
    targetPage.getByText(headingRegex)
  ]);
  if (!heading) {
    throw new Error(`Expected legal heading not found: ${headingRegex}`);
  }
  await expect(heading).toBeVisible();

  const legalContent = targetPage.locator("main p, article p, section p, p, li");
  await expect(legalContent.first()).toBeVisible();
  await captureCheckpoint(targetPage, testInfo, screenshotName, true);

  const finalUrl = targetPage.url();
  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" }).catch(() => {});
    });
    await waitForUiToSettle(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = initializeReport();
  let canContinue = true;
  const legalUrls = {};

  // Step 1: Login with Google
  try {
    await ensureOnLoginPage(page);
    await completeGoogleLogin(page);

    const sidebar = await firstVisibleLocator([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/negocio|mi negocio/i)
    ]);
    if (!sidebar) {
      throw new Error("Main app sidebar/navigation was not visible after login.");
    }
    await expect(sidebar).toBeVisible();

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
    report["Login"] = { status: "PASS", detail: "Main interface and sidebar are visible." };
  } catch (error) {
    report["Login"] = { status: "FAIL", detail: error.message };
    canContinue = false;
  }

  if (canContinue) {
    // Step 2: Open Mi Negocio menu
    try {
      await firstVisibleLocator([page.getByText(/negocio/i), page.getByRole("navigation")]);
      await clickByVisibleText(page, /^mi negocio$/i);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

      await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png", true);
      report["Mi Negocio menu"] = {
        status: "PASS",
        detail: "Mi Negocio expanded and submenu options are visible."
      };
    } catch (error) {
      report["Mi Negocio menu"] = { status: "FAIL", detail: error.message };
      canContinue = false;
    }
  }

  if (canContinue) {
    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByVisibleText(page, /agregar negocio/i);

      const modal = page.getByRole("dialog");
      await expect(modal).toBeVisible();
      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();

      const businessNameInput = await firstVisibleLocator([
        modal.getByLabel(/nombre del negocio/i),
        modal.getByPlaceholder(/nombre del negocio/i),
        modal.getByRole("textbox", { name: /nombre del negocio/i })
      ]);
      if (!businessNameInput) {
        throw new Error("Input 'Nombre del Negocio' is missing in modal.");
      }

      await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", true);

      await clickAndWait(businessNameInput, page);
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndWait(modal.getByRole("button", { name: /cancelar/i }), page);

      report["Agregar Negocio modal"] = {
        status: "PASS",
        detail: "Modal validated and optional field interaction completed."
      };
    } catch (error) {
      report["Agregar Negocio modal"] = { status: "FAIL", detail: error.message };
      canContinue = false;
    }
  }

  if (canContinue) {
    // Step 4: Open Administrar Negocios
    try {
      if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
        await clickByVisibleText(page, /^mi negocio$/i);
      }
      await clickByVisibleText(page, /administrar negocios/i);

      await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

      await captureCheckpoint(page, testInfo, "04-administrar-negocios-page.png", true);
      report["Administrar Negocios view"] = {
        status: "PASS",
        detail: "All expected account sections are visible."
      };
    } catch (error) {
      report["Administrar Negocios view"] = { status: "FAIL", detail: error.message };
      canContinue = false;
    }
  }

  // Step 5: Información General validations
  if (canContinue) {
    try {
      const infoSection = page
        .locator("section,div")
        .filter({ has: page.getByText(/informaci[oó]n general/i) })
        .first();

      await expect(infoSection.getByText(/business plan/i)).toBeVisible();
      await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

      const userEmail = await firstVisibleLocator([
        infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
        page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
      ]);
      if (!userEmail) {
        throw new Error("User email is not visible in Información General.");
      }

      const userName = await firstVisibleLocator([
        infoSection.getByText(/nombre|usuario|name/i),
        page.getByText(/bienvenido|hola|perfil/i)
      ]);
      if (!userName) {
        throw new Error("User name indicator is not visible.");
      }

      report["Información General"] = {
        status: "PASS",
        detail: "Name indicator, email, plan label, and plan button are visible."
      };
    } catch (error) {
      report["Información General"] = { status: "FAIL", detail: error.message };
    }
  }

  // Step 6: Detalles de la Cuenta validations
  if (canContinue) {
    try {
      await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/estado activo|activo/i).first()).toBeVisible();
      await expect(page.getByText(/idioma seleccionado|idioma/i).first()).toBeVisible();

      report["Detalles de la Cuenta"] = {
        status: "PASS",
        detail: "Account creation date, active status, and language are visible."
      };
    } catch (error) {
      report["Detalles de la Cuenta"] = { status: "FAIL", detail: error.message };
    }
  }

  // Step 7: Tus Negocios validations
  if (canContinue) {
    try {
      const businessesSection = page
        .locator("section,div")
        .filter({ has: page.getByText(/tus negocios/i) })
        .first();
      await expect(businessesSection).toBeVisible();
      await expect(businessesSection.getByText(/agregar negocio/i)).toBeVisible();
      await expect(businessesSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

      report["Tus Negocios"] = {
        status: "PASS",
        detail: "Business section, add button, and business limit text are visible."
      };
    } catch (error) {
      report["Tus Negocios"] = { status: "FAIL", detail: error.message };
    }
  }

  // Step 8: Validate Términos y Condiciones
  if (canContinue) {
    try {
      legalUrls["Términos y Condiciones"] = await openLegalDocumentAndReturn(
        page,
        testInfo,
        /t[ée]rminos y condiciones/i,
        /t[ée]rminos y condiciones/i,
        "05-terminos-y-condiciones.png"
      );

      report["Términos y Condiciones"] = {
        status: "PASS",
        detail: `Legal page validated at URL: ${legalUrls["Términos y Condiciones"]}`
      };
    } catch (error) {
      report["Términos y Condiciones"] = { status: "FAIL", detail: error.message };
    }
  }

  // Step 9: Validate Política de Privacidad
  if (canContinue) {
    try {
      legalUrls["Política de Privacidad"] = await openLegalDocumentAndReturn(
        page,
        testInfo,
        /pol[íi]tica de privacidad/i,
        /pol[íi]tica de privacidad/i,
        "06-politica-de-privacidad.png"
      );

      report["Política de Privacidad"] = {
        status: "PASS",
        detail: `Legal page validated at URL: ${legalUrls["Política de Privacidad"]}`
      };
    } catch (error) {
      report["Política de Privacidad"] = { status: "FAIL", detail: error.message };
    }
  }

  // Step 10: Final report
  const reportText = JSON.stringify(report, null, 2);
  // eslint-disable-next-line no-console
  console.log("\nSALEADS_MI_NEGOCIO_FINAL_REPORT\n" + reportText);
  await testInfo.attach("saleads-mi-negocio-final-report.json", {
    body: reportText,
    contentType: "application/json"
  });

  const failures = Object.entries(report).filter(([, result]) => result.status !== "PASS");
  expect(
    failures,
    `One or more validation blocks failed: ${JSON.stringify(failures, null, 2)}`
  ).toEqual([]);
});
