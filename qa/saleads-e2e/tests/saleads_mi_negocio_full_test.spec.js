const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage
  });
}

async function expectAnyVisible(locators, description) {
  for (const locator of locators) {
    try {
      const first = locator.first();
      await first.waitFor({ state: "visible", timeout: 3000 });
      return first;
    } catch (error) {
      // keep trying fallback locators
    }
  }

  throw new Error(`Could not find visible element: ${description}`);
}

async function clickFirstVisible(page, locatorFactories, description) {
  const locators = locatorFactories.map((factory) => factory());
  const target = await expectAnyVisible(locators, description);
  await target.click();
  await waitForUiToSettle(page);
  return target;
}

async function selectGoogleAccountIfPrompt(context) {
  const emailRegex = new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
  const chooseAccountRegex = /Choose an account|Elige una cuenta|Selecciona una cuenta|Continuar como/i;

  for (let attempt = 0; attempt < 12; attempt += 1) {
    const pages = context.pages();

    for (const candidatePage of pages) {
      const emailOption = candidatePage.getByText(emailRegex).first();
      if (await emailOption.isVisible().catch(() => false)) {
        await emailOption.click();
        await waitForUiToSettle(candidatePage);
        return true;
      }
    }

    for (const candidatePage of pages) {
      const chooseAccountPrompt = candidatePage.getByText(chooseAccountRegex).first();
      if (await chooseAccountPrompt.isVisible().catch(() => false)) {
        const emailOption = candidatePage.getByRole("link", { name: emailRegex }).first();
        if (await emailOption.isVisible().catch(() => false)) {
          await emailOption.click();
          await waitForUiToSettle(candidatePage);
          return true;
        }
      }
    }

    await pages[0].waitForTimeout(1500);
  }

  return false;
}

async function ensureMiNegocioExpanded(page) {
  const agregarNegocioVisible = await page.getByText(/^Agregar Negocio$/i).first().isVisible().catch(() => false);
  const administrarNegociosVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
  if (agregarNegocioVisible && administrarNegociosVisible) {
    return;
  }

  await clickFirstVisible(
    page,
    [
      () => page.getByText(/^Negocio$/i),
      () => page.getByRole("button", { name: /^Negocio$/i }),
      () => page.getByRole("link", { name: /^Negocio$/i })
    ],
    "Negocio section"
  );

  await clickFirstVisible(
    page,
    [
      () => page.getByText(/^Mi Negocio$/i),
      () => page.getByRole("button", { name: /^Mi Negocio$/i }),
      () => page.getByRole("link", { name: /^Mi Negocio$/i })
    ],
    "Mi Negocio option"
  );
}

async function openAndValidateLegalDocument({
  page,
  context,
  linkName,
  headingPattern,
  checkpointName,
  testInfo
}) {
  const appUrlBeforeClick = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickFirstVisible(
    page,
    [
      () => page.getByRole("link", { name: linkName }),
      () => page.getByText(linkName)
    ],
    `${linkName} link`
  );

  const popup = await popupPromise;
  const legalPage = popup || page;
  await waitForUiToSettle(legalPage);

  await expectAnyVisible(
    [
      legalPage.getByRole("heading", { name: headingPattern }),
      legalPage.getByText(headingPattern)
    ],
    `${linkName} heading`
  );

  await expectAnyVisible(
    [
      legalPage.locator("main p, article p, section p, p").filter({ hasText: /\S+/ }),
      legalPage.locator("main li, article li, section li, li").filter({ hasText: /\S+/ })
    ],
    `${linkName} legal content`
  );

  await captureCheckpoint(legalPage, testInfo, checkpointName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else if (page.url() !== appUrlBeforeClick && appUrlBeforeClick && appUrlBeforeClick !== "about:blank") {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" }).catch(() => {});
    });
    await waitForUiToSettle(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = {
    Login: { status: "FAIL", details: "Not executed" },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed" },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed" },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed" },
    "Información General": { status: "FAIL", details: "Not executed" },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed" },
    "Tus Negocios": { status: "FAIL", details: "Not executed" },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed" },
    "Política de Privacidad": { status: "FAIL", details: "Not executed" }
  };

  const evidence = {
    screenshots: [],
    finalUrls: {}
  };

  const markPass = (key, details) => {
    report[key] = { status: "PASS", details };
  };

  const markFail = (key, error) => {
    report[key] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error)
    };
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_LOGIN_URL to the current SaleADS login page, or start the test with the browser already on the login view."
    );
  }

  // Step 1: Login with Google and validate dashboard.
  try {
    await clickFirstVisible(
      page,
      [
        () => page.getByRole("button", { name: /Google/i }),
        () => page.getByText(/Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google/i),
        () => page.getByRole("button", { name: /Iniciar sesi[oó]n|Login|Acceder/i })
      ],
      "Google login button"
    );

    await selectGoogleAccountIfPrompt(context);
    await page.bringToFront();
    await waitForUiToSettle(page);

    await expectAnyVisible(
      [
        page.locator("aside, nav, [class*='sidebar']").filter({ hasText: /Negocio|Mi Negocio/i }),
        page.getByText(/Negocio|Mi Negocio/i)
      ],
      "main application interface with left sidebar"
    );

    const dashboardShot = "01-dashboard-loaded.png";
    await captureCheckpoint(page, testInfo, dashboardShot, true);
    evidence.screenshots.push(dashboardShot);
    markPass("Login", "Dashboard and left sidebar are visible after Google login.");
  } catch (error) {
    markFail("Login", error);
  }

  // Step 2: Open Mi Negocio menu and validate submenu options.
  try {
    await ensureMiNegocioExpanded(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: 10000 });

    const menuShot = "02-mi-negocio-expanded-menu.png";
    await captureCheckpoint(page, testInfo, menuShot, true);
    evidence.screenshots.push(menuShot);
    markPass("Mi Negocio menu", "Mi Negocio submenu expanded with Agregar/Administrar options.");
  } catch (error) {
    markFail("Mi Negocio menu", error);
  }

  // Step 3: Validate Agregar Negocio modal.
  try {
    await clickFirstVisible(
      page,
      [
        () => page.getByRole("button", { name: /^Agregar Negocio$/i }),
        () => page.getByRole("link", { name: /^Agregar Negocio$/i }),
        () => page.getByText(/^Agregar Negocio$/i)
      ],
      "Agregar Negocio"
    );

    const modal = await expectAnyVisible(
      [
        page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
        page.locator("[role='dialog'], .modal, [class*='modal']").filter({ hasText: /Crear Nuevo Negocio/i })
      ],
      "Crear Nuevo Negocio modal"
    );

    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expectAnyVisible(
      [modal.getByLabel(/Nombre del Negocio/i), modal.getByPlaceholder(/Nombre del Negocio/i), modal.locator("input")],
      "Nombre del Negocio input"
    );
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    const modalShot = "03-crear-nuevo-negocio-modal.png";
    await captureCheckpoint(page, testInfo, modalShot, true);
    evidence.screenshots.push(modalShot);

    const businessNameInput = await expectAnyVisible(
      [modal.getByLabel(/Nombre del Negocio/i), modal.getByPlaceholder(/Nombre del Negocio/i), modal.locator("input")],
      "Nombre del Negocio input for optional typing"
    );
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await modal.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUiToSettle(page);

    markPass("Agregar Negocio modal", "Modal validated and closed with Cancelar.");
  } catch (error) {
    markFail("Agregar Negocio modal", error);
  }

  // Step 4: Open Administrar Negocios and validate main sections.
  try {
    await ensureMiNegocioExpanded(page);

    await clickFirstVisible(
      page,
      [
        () => page.getByRole("link", { name: /^Administrar Negocios$/i }),
        () => page.getByRole("button", { name: /^Administrar Negocios$/i }),
        () => page.getByText(/^Administrar Negocios$/i)
      ],
      "Administrar Negocios"
    );

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 20000 });

    const adminShot = "04-administrar-negocios-page.png";
    await captureCheckpoint(page, testInfo, adminShot, true);
    evidence.screenshots.push(adminShot);
    markPass("Administrar Negocios view", "All expected account sections are visible.");
  } catch (error) {
    markFail("Administrar Negocios view", error);
  }

  // Step 5: Validate Informacion General.
  try {
    await expectAnyVisible(
      [page.getByText(/Informaci[oó]n General/i), page.locator("section,div").filter({ hasText: /Informaci[oó]n General/i })],
      "Informacion General section"
    );
    await expectAnyVisible(
      [page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/), page.getByText(/Correo|Email/i)],
      "user email"
    );
    await expectAnyVisible([page.getByText(/Nombre|Usuario/i)], "user name");
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    markPass("Información General", "Informacion General fields and plan controls are visible.");
  } catch (error) {
    markFail("Información General", error);
  }

  // Step 6: Validate Detalles de la Cuenta.
  try {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();

    markPass("Detalles de la Cuenta", "Cuenta creada, Estado activo, Idioma seleccionado are visible.");
  } catch (error) {
    markFail("Detalles de la Cuenta", error);
  }

  // Step 7: Validate Tus Negocios.
  try {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expectAnyVisible(
      [
        page.locator("section,div").filter({ hasText: /Tus Negocios/i }).locator("li, tr, [role='row'], [class*='business']"),
        page.getByText(/Negocio/i)
      ],
      "business list"
    );
    await expectAnyVisible(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      "Agregar Negocio button in Tus Negocios"
    );
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    markPass("Tus Negocios", "Business list, Agregar Negocio control and quota text are visible.");
  } catch (error) {
    markFail("Tus Negocios", error);
  }

  // Step 8: Validate Terminos y Condiciones link.
  try {
    const termsUrl = await openAndValidateLegalDocument({
      page,
      context,
      linkName: /T[ée]rminos y Condiciones/i,
      headingPattern: /T[ée]rminos y Condiciones/i,
      checkpointName: "05-terminos-y-condiciones.png",
      testInfo
    });
    evidence.screenshots.push("05-terminos-y-condiciones.png");
    evidence.finalUrls.terminosYCondiciones = termsUrl;

    markPass("Términos y Condiciones", `Validated legal page. Final URL: ${termsUrl}`);
  } catch (error) {
    markFail("Términos y Condiciones", error);
  }

  // Step 9: Validate Politica de Privacidad link.
  try {
    const privacyUrl = await openAndValidateLegalDocument({
      page,
      context,
      linkName: /Pol[íi]tica de Privacidad/i,
      headingPattern: /Pol[íi]tica de Privacidad/i,
      checkpointName: "06-politica-de-privacidad.png",
      testInfo
    });
    evidence.screenshots.push("06-politica-de-privacidad.png");
    evidence.finalUrls.politicaDePrivacidad = privacyUrl;

    markPass("Política de Privacidad", `Validated legal page. Final URL: ${privacyUrl}`);
  } catch (error) {
    markFail("Política de Privacidad", error);
  }

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    evidence
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log("SALEADS_FINAL_REPORT_START");
  console.log(JSON.stringify(finalReport, null, 2));
  console.log("SALEADS_FINAL_REPORT_END");

  const failedSteps = Object.entries(report).filter(([, value]) => value.status !== "PASS");
  expect.soft(
    failedSteps,
    `Expected all validations to pass. Failed: ${failedSteps.map(([step]) => step).join(", ")}`
  ).toHaveLength(0);
});
