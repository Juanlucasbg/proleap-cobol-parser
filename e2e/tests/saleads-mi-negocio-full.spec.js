const { test, expect } = require("@playwright/test");

const FINAL_REPORT_FIELDS = [
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

function buildInitialReport() {
  return Object.fromEntries(FINAL_REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

async function clickAndWaitForUi(page, locator) {
  await expect(locator).toBeVisible();
  await Promise.all([
    page.waitForLoadState("networkidle").catch(() => {}),
    locator.click()
  ]);
  await page.waitForTimeout(500);
}

async function clickAndWaitNavigationOrUi(page, locator) {
  await expect(locator).toBeVisible();
  await Promise.all([
    page.waitForLoadState("domcontentloaded").catch(() => {}),
    locator.click()
  ]);
  await page.waitForTimeout(500);
}

async function pickFirstVisible(page, candidates, timeoutMs = 2500) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    if (await locator.isVisible({ timeout: timeoutMs }).catch(() => false)) {
      return locator;
    }
  }
  return null;
}

async function checkpoint(page, testInfo, name, fullPage = false) {
  const fileName = `${name}.png`;
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(fileName, { path, contentType: "image/png" });
}

async function runStep(report, failures, key, fn) {
  try {
    await fn();
    report[key] = "PASS";
  } catch (error) {
    report[key] = "FAIL";
    failures.push({
      step: key,
      message: error instanceof Error ? error.message : String(error)
    });
  }
}

async function tryGoogleAccountSelection(targetPage) {
  const accountLocator = targetPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
  if (await accountLocator.isVisible({ timeout: 5000 }).catch(() => false)) {
    await clickAndWaitNavigationOrUi(targetPage, accountLocator);
  }
}

async function validateLegalLink({
  page,
  testInfo,
  reportEvidence,
  linkName,
  headingPattern,
  screenshotName
}) {
  const linkLocator = await pickFirstVisible(page, [
    page.getByRole("link", { name: new RegExp(linkName, "i") }),
    page.getByText(new RegExp(linkName, "i"))
  ]);

  if (!linkLocator) {
    throw new Error(`Could not find legal link: ${linkName}`);
  }

  const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
  await clickAndWaitNavigationOrUi(page, linkLocator);
  let targetPage = await popupPromise;
  let openedInNewTab = true;

  if (!targetPage) {
    targetPage = page;
    openedInNewTab = false;
  }

  await targetPage.waitForLoadState("domcontentloaded");
  await expect(targetPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible();

  const legalBodyText = targetPage
    .locator("main, article, section, body")
    .filter({ hasText: /condiciones|privacidad|datos|uso|usuarios|servicio|informaci[oó]n/i })
    .first();
  await expect(legalBodyText).toBeVisible();

  const finalUrl = targetPage.url();
  reportEvidence[linkName] = { finalUrl };

  await checkpoint(targetPage, testInfo, screenshotName, true);

  if (openedInNewTab) {
    await targetPage.close();
    await expect(page).toHaveURL(/.*/);
  } else {
    await page.goBack().catch(() => {});
    await page.waitForLoadState("domcontentloaded").catch(() => {});
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildInitialReport();
  const failures = [];
  const evidence = {};

  const entryUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL ||
    process.env.PLAYWRIGHT_TEST_BASE_URL;

  if (!entryUrl) {
    test.skip(
      true,
      "Set SALEADS_LOGIN_URL, SALEADS_BASE_URL, BASE_URL, or PLAYWRIGHT_TEST_BASE_URL to run in your target environment."
    );
  }

  await page.goto(entryUrl, { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle").catch(() => {});

  await runStep(report, failures, "Login", async () => {
    const sidebarCandidate = await pickFirstVisible(page, [
      page.getByRole("navigation"),
      page.locator("aside"),
      page.getByText(/Mi Negocio|Negocio/i)
    ], 3000);

    if (!sidebarCandidate) {
      const loginButton = await pickFirstVisible(page, [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google/i)
      ], 5000);

      if (!loginButton) {
        throw new Error("Could not find a Google login control.");
      }

      const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
      await clickAndWaitNavigationOrUi(page, loginButton);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded").catch(() => {});
        await tryGoogleAccountSelection(popup);
        await popup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
      } else {
        await tryGoogleAccountSelection(page);
      }
    }

    await expect(
      page
        .locator("aside, nav")
        .filter({ hasText: /Negocio|Dashboard|Mi Negocio/i })
        .first()
    ).toBeVisible({ timeout: 45000 });

    evidence.dashboardUrl = page.url();
    await checkpoint(page, testInfo, "01-dashboard-loaded", true);
  });

  await runStep(report, failures, "Mi Negocio menu", async () => {
    const negocioSection = await pickFirstVisible(page, [
      page.getByRole("button", { name: /Negocio/i }),
      page.getByRole("link", { name: /Negocio/i }),
      page.getByText(/^Negocio$/i)
    ]);
    if (negocioSection) {
      await clickAndWaitForUi(page, negocioSection);
    }

    const miNegocioControl = await pickFirstVisible(page, [
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i)
    ]);

    if (!miNegocioControl) {
      throw new Error('Could not find "Mi Negocio" in sidebar.');
    }

    await clickAndWaitForUi(page, miNegocioControl);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await checkpoint(page, testInfo, "02-mi-negocio-expanded");
  });

  await runStep(report, failures, "Agregar Negocio modal", async () => {
    const agregarNegocio = await pickFirstVisible(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);

    if (!agregarNegocio) {
      throw new Error('Could not find "Agregar Negocio".');
    }

    await clickAndWaitForUi(page, agregarNegocio);

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible();

    const nombreInput = await pickFirstVisible(page, [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: "" }).first()
    ]);
    if (!nombreInput) {
      throw new Error('Could not find "Nombre del Negocio" input.');
    }

    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    await checkpoint(page, testInfo, "03-agregar-negocio-modal");

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");

    await clickAndWaitForUi(page, page.getByRole("button", { name: /Cancelar/i }).first());
    await expect(modalTitle).toBeHidden();
  });

  await runStep(report, failures, "Administrar Negocios view", async () => {
    const adminOptionVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!adminOptionVisible) {
      const miNegocioControl = await pickFirstVisible(page, [
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i)
      ]);
      if (!miNegocioControl) {
        throw new Error('Could not re-open "Mi Negocio" menu.');
      }
      await clickAndWaitForUi(page, miNegocioControl);
    }

    await clickAndWaitNavigationOrUi(page, page.getByText(/Administrar Negocios/i).first());

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

    evidence.accountPageUrl = page.url();
    await checkpoint(page, testInfo, "04-administrar-negocios-page", true);
  });

  await runStep(report, failures, "Información General", async () => {
    const infoSection = page
      .locator("section, div")
      .filter({ hasText: /Informaci[oó]n General/i })
      .first();

    await expect(infoSection).toBeVisible();
    await expect(infoSection.getByText(/@/).first()).toBeVisible();
    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runStep(report, failures, "Detalles de la Cuenta", async () => {
    const detailsSection = page
      .locator("section, div")
      .filter({ hasText: /Detalles de la Cuenta/i })
      .first();

    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo|activo/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep(report, failures, "Tus Negocios", async () => {
    const businessesSection = page
      .locator("section, div")
      .filter({ hasText: /Tus Negocios/i })
      .first();

    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(businessesSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
  });

  await runStep(report, failures, "Términos y Condiciones", async () => {
    await validateLegalLink({
      page,
      testInfo,
      reportEvidence: evidence,
      linkName: "Términos y Condiciones",
      headingPattern: /T[eé]rminos y Condiciones/i,
      screenshotName: "08-terminos-y-condiciones"
    });
  });

  await runStep(report, failures, "Política de Privacidad", async () => {
    await validateLegalLink({
      page,
      testInfo,
      reportEvidence: evidence,
      linkName: "Política de Privacidad",
      headingPattern: /Pol[ií]tica de Privacidad/i,
      screenshotName: "09-politica-de-privacidad"
    });
  });

  const finalReportPayload = {
    testName: "saleads_mi_negocio_full_test",
    statusByStep: report,
    evidence,
    failures
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(finalReportPayload, null, 2), "utf8"),
    contentType: "application/json"
  });

  console.log("SaleADS Mi Negocio final report:");
  console.table(report);

  if (failures.length > 0) {
    throw new Error(
      `Workflow finished with ${failures.length} failing step(s): ${failures
        .map((item) => `${item.step}: ${item.message}`)
        .join(" | ")}`
    );
  }
});
