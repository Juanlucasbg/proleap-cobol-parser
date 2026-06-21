const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

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

function createReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: "Not executed." };
    return acc;
  }, {});
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(750);
}

async function firstVisible(candidates) {
  for (const locator of candidates) {
    const item = locator.first();
    try {
      if ((await item.count()) > 0 && (await item.isVisible())) {
        return item;
      }
    } catch (_error) {
      // Continue with next candidate.
    }
  }
  return null;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function checkpoint(page, testInfo, fileName, fullPage = false) {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(fileName, { path, contentType: "image/png" });
  return path;
}

async function runStep(report, field, fn) {
  try {
    await fn();
    report[field] = { status: "PASS", details: "Validated successfully." };
  } catch (error) {
    report[field] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error),
    };
  }
}

async function trySelectGoogleAccount(pageOrPopup) {
  const account = await firstVisible([
    pageOrPopup.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
    pageOrPopup.getByRole("link", { name: GOOGLE_ACCOUNT_EMAIL }),
    pageOrPopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
  ]);

  if (!account) {
    return;
  }

  await account.click();
  await pageOrPopup.waitForLoadState("domcontentloaded").catch(() => {});
}

async function expandMiNegocio(page) {
  const addBusiness = page.getByText("Agregar Negocio", { exact: true });
  const manageBusiness = page.getByText("Administrar Negocios", { exact: true });

  if ((await addBusiness.count()) > 0 && (await addBusiness.first().isVisible())) {
    return;
  }

  const miNegocioToggle = await firstVisible([
    page.getByRole("button", { name: /mi negocio/i }),
    page.getByRole("link", { name: /mi negocio/i }),
    page.getByText("Mi Negocio", { exact: true }),
  ]);

  if (!miNegocioToggle) {
    throw new Error("Could not locate 'Mi Negocio' in the left sidebar.");
  }

  await clickAndWait(page, miNegocioToggle);
  await expect(addBusiness.or(manageBusiness)).toBeVisible();
}

async function validateLegalPage({
  page,
  context,
  linkName,
  expectedHeading,
  screenshotName,
  testInfo,
}) {
  const legalLink = await firstVisible([
    page.getByRole("link", { name: linkName }),
    page.getByRole("button", { name: linkName }),
    page.getByText(linkName, { exact: false }),
  ]);

  if (!legalLink) {
    throw new Error(`Could not locate legal link '${linkName}'.`);
  }

  const appUrlBeforeClick = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await legalLink.click();
  const popup = await popupPromise;

  const targetPage = popup || page;
  await waitForUi(targetPage);

  const heading = targetPage.getByRole("heading", { name: expectedHeading });
  await expect(heading).toBeVisible();

  const bodyText = (await targetPage.locator("body").innerText()).trim();
  if (bodyText.length < 120) {
    throw new Error(`Expected legal content for '${linkName}', but body content is too short.`);
  }

  await checkpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  const runtimeEvidence = {
    dashboardScreenshot: null,
    expandedMenuScreenshot: null,
    modalScreenshot: null,
    accountPageScreenshot: null,
    termsScreenshot: null,
    termsUrl: null,
    privacyScreenshot: null,
    privacyUrl: null,
  };

  await runStep(report, "Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL;

    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Browser opened on about:blank. Provide SALEADS_LOGIN_URL for your current SaleADS environment."
      );
    }

    const loginButton = await firstVisible([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("button", { name: /sign in/i }),
      page.getByRole("button", { name: /iniciar sesi[oó]n/i }),
      page.getByRole("link", { name: /google/i }),
    ]);

    if (!loginButton) {
      throw new Error("Could not locate a login button for Google sign-in.");
    }

    const googlePopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    const googlePopup = await googlePopupPromise;

    if (googlePopup) {
      await waitForUi(googlePopup);
      await trySelectGoogleAccount(googlePopup);
    } else {
      await waitForUi(page);
      await trySelectGoogleAccount(page);
    }

    await waitForUi(page);
    await expect(page.locator("main")).toBeVisible();

    const sidebarVisibleSignal = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText("Negocio", { exact: true }),
    ]);
    if (!sidebarVisibleSignal) {
      throw new Error("Main app loaded, but left sidebar navigation was not found.");
    }

    runtimeEvidence.dashboardScreenshot = await checkpoint(
      page,
      testInfo,
      "01_dashboard_loaded.png",
      true
    );
  });

  await runStep(report, "Mi Negocio menu", async () => {
    await expandMiNegocio(page);
    await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();

    runtimeEvidence.expandedMenuScreenshot = await checkpoint(
      page,
      testInfo,
      "02_mi_negocio_menu_expanded.png",
      true
    );
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    const addBusinessOption = await firstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/ }),
      page.getByRole("link", { name: /^Agregar Negocio$/ }),
      page.getByText("Agregar Negocio", { exact: true }),
    ]);

    if (!addBusinessOption) {
      throw new Error("Could not locate 'Agregar Negocio' option.");
    }

    await clickAndWait(page, addBusinessOption);

    const modalTitle = page.getByRole("heading", { name: "Crear Nuevo Negocio" });
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel("Nombre del Negocio")).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

    await page.getByLabel("Nombre del Negocio").fill("Negocio Prueba Automatización");
    runtimeEvidence.modalScreenshot = await checkpoint(page, testInfo, "03_agregar_negocio_modal.png");
    await clickAndWait(page, page.getByRole("button", { name: "Cancelar" }));
    await expect(modalTitle).not.toBeVisible();
  });

  await runStep(report, "Administrar Negocios view", async () => {
    await expandMiNegocio(page);
    const manageBusinessOption = await firstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/ }),
      page.getByRole("link", { name: /^Administrar Negocios$/ }),
      page.getByText("Administrar Negocios", { exact: true }),
    ]);

    if (!manageBusinessOption) {
      throw new Error("Could not locate 'Administrar Negocios' option.");
    }

    await clickAndWait(page, manageBusinessOption);
    await expect(page.getByRole("heading", { name: "Información General" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Detalles de la Cuenta" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Tus Negocios" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Sección Legal" })).toBeVisible();

    runtimeEvidence.accountPageScreenshot = await checkpoint(
      page,
      testInfo,
      "04_administrar_negocios_view.png",
      true
    );
  });

  await runStep(report, "Información General", async () => {
    const infoSection = page.getByRole("heading", { name: "Información General" });
    await expect(infoSection).toBeVisible();
    await expect(page.getByText(/@/)).toBeVisible();
    await expect(page.getByText("BUSINESS PLAN", { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByRole("heading", { name: "Detalles de la Cuenta" })).toBeVisible();
    await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible();
    await expect(page.getByText("Estado activo", { exact: false })).toBeVisible();
    await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    await expect(page.getByRole("heading", { name: "Tus Negocios" })).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: "Agregar Negocio" })).toBeVisible();
  });

  await runStep(report, "Términos y Condiciones", async () => {
    runtimeEvidence.termsUrl = await validateLegalPage({
      page,
      context,
      linkName: "Términos y Condiciones",
      expectedHeading: /T[eé]rminos y Condiciones/i,
      screenshotName: "05_terminos_y_condiciones.png",
      testInfo,
    });
    runtimeEvidence.termsScreenshot = testInfo.outputPath("05_terminos_y_condiciones.png");
  });

  await runStep(report, "Política de Privacidad", async () => {
    runtimeEvidence.privacyUrl = await validateLegalPage({
      page,
      context,
      linkName: "Política de Privacidad",
      expectedHeading: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06_politica_de_privacidad.png",
      testInfo,
    });
    runtimeEvidence.privacyScreenshot = testInfo.outputPath("06_politica_de_privacidad.png");
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      saleadsLoginUrl: process.env.SALEADS_LOGIN_URL || "not-provided",
      googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
    },
    results: report,
    evidence: runtimeEvidence,
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_final_report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads_mi_negocio_final_report.json", {
    path: reportPath,
    contentType: "application/json",
  });

  const failingFields = Object.entries(report)
    .filter(([, value]) => value.status === "FAIL")
    .map(([field, value]) => `${field}: ${value.details}`);

  expect(
    failingFields,
    `Final Report: ${failingFields.length ? failingFields.join(" | ") : "all steps passed"}`
  ).toEqual([]);
});
