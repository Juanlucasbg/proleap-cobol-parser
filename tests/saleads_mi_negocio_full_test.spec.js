const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad"
];

function sanitizeFileName(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => null);
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => null);
  await page.waitForTimeout(800);
}

async function firstVisibleLocator(candidates, timeoutMs = 12000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of candidates) {
      if (await locator.first().isVisible().catch(() => false)) {
        return locator.first();
      }
    }

    await candidates[0].page().waitForTimeout(300);
  }

  throw new Error("None of the candidate locators became visible.");
}

async function clickWithUiWait(locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => null);
  await locator.click();
  await waitForUi(locator.page());
}

async function takeCheckpoint(page, testInfo, name, fullPage = false) {
  const imagePath = testInfo.outputPath(`${sanitizeFileName(name)}.png`);
  await page.screenshot({ path: imagePath, fullPage });
  await testInfo.attach(name, { path: imagePath, contentType: "image/png" });
}

async function recordStep(report, stepName, execution) {
  try {
    await execution();
    report[stepName] = { status: "PASS" };
  } catch (error) {
    report[stepName] = { status: "FAIL", details: error.message };
  }
}

async function clickAndCaptureDestination(page, clickAction) {
  const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await clickAction();
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => null);
    await popup.bringToFront().catch(() => null);
    return { destinationPage: popup, openedNewTab: true };
  }

  await waitForUi(page);
  return { destinationPage: page, openedNewTab: false };
}

async function locateSidebar(page) {
  const sidebarCandidates = [
    page.locator("aside"),
    page.getByRole("navigation"),
    page.getByText(/^Negocio$/i)
  ];

  for (const candidate of sidebarCandidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      return candidate.first();
    }
  }

  return null;
}

async function ensureMiNegocioExpanded(page) {
  const negocioNode = await firstVisibleLocator([
    page.getByRole("button", { name: /^Negocio$/i }),
    page.getByRole("link", { name: /^Negocio$/i }),
    page.getByText(/^Negocio$/i)
  ]);

  await clickWithUiWait(negocioNode);

  const miNegocioNode = await firstVisibleLocator([
    page.getByRole("button", { name: /^Mi Negocio$/i }),
    page.getByRole("link", { name: /^Mi Negocio$/i }),
    page.getByText(/^Mi Negocio$/i)
  ]);

  await clickWithUiWait(miNegocioNode);
}

async function validateLegalPage({ page, headingRegex, screenshotName, reportUrls, reportUrlKey, testInfo }) {
  await expect(page.getByRole("heading", { name: headingRegex })).toBeVisible({ timeout: 20000 });
  await expect(page.locator("body")).toContainText(/./);
  await takeCheckpoint(page, testInfo, screenshotName, true);
  reportUrls[reportUrlKey] = page.url();
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const finalReport = Object.fromEntries(REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }]));
  const legalUrls = {};

  const targetLoginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL ||
    process.env.PLAYWRIGHT_BASE_URL;

  test.skip(
    !targetLoginUrl,
    "Set SALEADS_LOGIN_URL, SALEADS_BASE_URL, BASE_URL, or PLAYWRIGHT_BASE_URL to the SaleADS login page."
  );

  await page.goto(targetLoginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await recordStep(finalReport, "Login", async () => {
    const loginButton = await firstVisibleLocator([
      page.getByRole("button", { name: /sign in with google|continuar con google|iniciar sesion con google|google/i }),
      page.getByRole("link", { name: /sign in with google|continuar con google|iniciar sesion con google|google/i }),
      page.getByText(/sign in with google|continuar con google|iniciar sesion con google/i)
    ]);

    const { destinationPage } = await clickAndCaptureDestination(page, async () => {
      await loginButton.click();
    });

    const googleAccountOption = destinationPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
    if (await googleAccountOption.isVisible({ timeout: 7000 }).catch(() => false)) {
      await clickWithUiWait(googleAccountOption);
    }

    const sidebarPageCandidates = [page, destinationPage];
    let resolvedAppPage = null;

    for (const candidatePage of sidebarPageCandidates) {
      const sidebar = await locateSidebar(candidatePage);
      if (sidebar) {
        resolvedAppPage = candidatePage;
        break;
      }
    }

    if (!resolvedAppPage) {
      await page.waitForTimeout(2000);
      resolvedAppPage = page;
      await expect(await locateSidebar(resolvedAppPage)).not.toBeNull();
    }

    await resolvedAppPage.bringToFront().catch(() => null);
    await takeCheckpoint(resolvedAppPage, testInfo, "dashboard-loaded", true);
  });

  await recordStep(finalReport, "Mi Negocio menu", async () => {
    await ensureMiNegocioExpanded(page);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible({ timeout: 12000 });
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible({ timeout: 12000 });
    await takeCheckpoint(page, testInfo, "mi-negocio-menu-expanded");
  });

  await recordStep(finalReport, "Agregar Negocio modal", async () => {
    const addBusinessEntry = await firstVisibleLocator([
      page.getByRole("menuitem", { name: /^Agregar Negocio$/i }),
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await clickWithUiWait(addBusinessEntry);

    const modalTitle = page.getByRole("heading", { name: /^Crear Nuevo Negocio$/i });
    await expect(modalTitle).toBeVisible({ timeout: 15000 });
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();
    await takeCheckpoint(page, testInfo, "agregar-negocio-modal");

    const businessNameField = page.getByLabel(/Nombre del Negocio/i);
    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatizacion");
    await clickWithUiWait(page.getByRole("button", { name: /^Cancelar$/i }));
  });

  await recordStep(finalReport, "Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);

    const manageBusinessesEntry = await firstVisibleLocator([
      page.getByRole("menuitem", { name: /^Administrar Negocios$/i }),
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);
    await clickWithUiWait(manageBusinessesEntry);

    await expect(page.getByText(/Informacion General/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Seccion Legal/i)).toBeVisible({ timeout: 20000 });
    await takeCheckpoint(page, testInfo, "administrar-negocios", true);
  });

  await recordStep(finalReport, "Informacion General", async () => {
    await expect(page.locator("body")).toContainText(/BUSINESS PLAN/i);
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    await expect(page.locator("body")).toContainText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    await expect(page.locator("body")).toContainText(/Nombre|Usuario|Perfil/i);
  });

  await recordStep(finalReport, "Detalles de la Cuenta", async () => {
    await expect(page.locator("body")).toContainText(/Cuenta creada/i);
    await expect(page.locator("body")).toContainText(/Estado activo|Activo/i);
    await expect(page.locator("body")).toContainText(/Idioma seleccionado|Idioma/i);
  });

  await recordStep(finalReport, "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.locator("body")).toContainText(/Tienes 2 de 3 negocios/i);
  });

  await recordStep(finalReport, "Terminos y Condiciones", async () => {
    const termsLink = await firstVisibleLocator([
      page.getByRole("link", { name: /T[eé]rminos y Condiciones/i }),
      page.getByText(/T[eé]rminos y Condiciones/i)
    ]);

    const { destinationPage, openedNewTab } = await clickAndCaptureDestination(page, async () => {
      await termsLink.click();
    });

    await validateLegalPage({
      page: destinationPage,
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotName: "terminos-y-condiciones",
      reportUrls: legalUrls,
      reportUrlKey: "terminosYCondicionesUrl",
      testInfo
    });

    if (openedNewTab) {
      await destinationPage.close();
      await page.bringToFront().catch(() => null);
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUi(page);
    }
  });

  await recordStep(finalReport, "Politica de Privacidad", async () => {
    const privacyLink = await firstVisibleLocator([
      page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }),
      page.getByText(/Pol[ií]tica de Privacidad/i)
    ]);

    const { destinationPage, openedNewTab } = await clickAndCaptureDestination(page, async () => {
      await privacyLink.click();
    });

    await validateLegalPage({
      page: destinationPage,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "politica-de-privacidad",
      reportUrls: legalUrls,
      reportUrlKey: "politicaDePrivacidadUrl",
      testInfo
    });

    if (openedNewTab) {
      await destinationPage.close();
      await page.bringToFront().catch(() => null);
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUi(page);
    }
  });

  const reportPayload = {
    workflow: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: finalReport,
    evidence: legalUrls
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(reportPayload, null, 2), "utf8"),
    contentType: "application/json"
  });

  // Step 10 requirement: explicit PASS/FAIL output for each validation section.
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(reportPayload, null, 2));

  const failedSteps = Object.entries(finalReport).filter(([, result]) => result.status !== "PASS");
  expect(
    failedSteps,
    `One or more workflow sections failed:\n${JSON.stringify(failedSteps, null, 2)}`
  ).toEqual([]);
});
