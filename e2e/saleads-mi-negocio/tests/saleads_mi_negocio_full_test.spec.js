const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

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
  "Política de Privacidad",
];

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function safeSlug(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

async function pickVisible(candidates) {
  for (const candidate of candidates) {
    const target = candidate.first();
    try {
      if (await target.isVisible()) {
        return target;
      }
    } catch (_error) {
      // Continue trying fallback locators.
    }
  }
  return null;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible({ timeout: 20000 });
  await locator.click();
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(1000);
}

async function waitForSidebar(context, preferredPage) {
  const timeoutAt = Date.now() + 60000;
  while (Date.now() < timeoutAt) {
    const candidates = [preferredPage, ...context.pages().filter((p) => p !== preferredPage)];
    for (const page of candidates) {
      const sidebar = await pickVisible([
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator('[class*="sidebar"]'),
      ]);
      if (sidebar) {
        return { page, sidebar };
      }
    }
    await preferredPage.waitForTimeout(1000);
  }

  throw new Error("Main application interface/sidebar did not appear after login.");
}

async function openLegalDocumentAndValidate(appPage, context, linkPattern, headingPattern, screenshotPath) {
  const link = await pickVisible([
    appPage.getByRole("link", { name: linkPattern }),
    appPage.getByRole("button", { name: linkPattern }),
    appPage.getByText(linkPattern),
  ]);
  if (!link) {
    throw new Error(`Could not find legal link: ${linkPattern}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
  await clickAndWait(appPage, link);

  let legalPage = await popupPromise;
  if (legalPage) {
    await legalPage.waitForLoadState("domcontentloaded");
  } else {
    legalPage = appPage;
  }

  const heading = await pickVisible([
    legalPage.getByRole("heading", { name: headingPattern }),
    legalPage.getByText(headingPattern),
  ]);
  if (!heading) {
    throw new Error(`Missing legal heading: ${headingPattern}`);
  }
  await expect(heading).toBeVisible({ timeout: 20000 });

  const legalText = (await legalPage.locator("body").innerText()).trim();
  expect(legalText.length).toBeGreaterThan(200);

  await legalPage.screenshot({ path: screenshotPath, fullPage: true });
  const finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await appPage.waitForLoadState("domcontentloaded").catch(() => {});
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await appPage.waitForTimeout(800);
  }

  return finalUrl;
}

function writeReport(reportPath, reportData) {
  ensureDir(path.dirname(reportPath));
  fs.writeFileSync(reportPath, JSON.stringify(reportData, null, 2), "utf8");
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const startUrl = process.env.SALEADS_START_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (!startUrl) {
    throw new Error("Missing SALEADS_START_URL (or SALEADS_BASE_URL / BASE_URL) environment variable.");
  }

  const screenshotDir = path.join(testInfo.outputDir, "screenshots");
  const reportPath = path.join(testInfo.outputDir, "saleads-mi-negocio-final-report.json");
  ensureDir(screenshotDir);

  const report = {};
  for (const key of REPORT_FIELDS) {
    report[key] = "FAIL";
  }

  const details = { startUrl, legalUrls: {}, errors: [] };
  let appPage = page;

  async function executeStep(stepName, handler) {
    try {
      await handler();
      report[stepName] = "PASS";
    } catch (error) {
      details.errors.push({ step: stepName, message: String(error.message || error) });
      const failShot = path.join(screenshotDir, `fail-${safeSlug(stepName)}.png`);
      await appPage.screenshot({ path: failShot, fullPage: true }).catch(() => {});
    }
  }

  await executeStep("Login", async () => {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });

    const loginButton = await pickVisible([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i),
    ]);
    if (!loginButton) {
      throw new Error("Google login button was not found.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickAndWait(page, loginButton);

    const googlePage = await popupPromise;
    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded");
      const accountOption = await pickVisible([
        googlePage.getByText(ACCOUNT_EMAIL, { exact: false }),
        googlePage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
        googlePage.getByRole("link", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
      ]);
      if (accountOption) {
        await clickAndWait(googlePage, accountOption);
      }
    } else {
      const inlineAccountOption = await pickVisible([
        page.getByText(ACCOUNT_EMAIL, { exact: false }),
        page.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
        page.getByRole("link", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
      ]);
      if (inlineAccountOption) {
        await clickAndWait(page, inlineAccountOption);
      }
    }

    const sidebarState = await waitForSidebar(context, page);
    appPage = sidebarState.page;
    await expect(sidebarState.sidebar).toBeVisible();
    await appPage.screenshot({ path: path.join(screenshotDir, "01-dashboard-loaded.png"), fullPage: true });
  });

  await executeStep("Mi Negocio menu", async () => {
    await appPage.bringToFront();

    const negocioOption = await pickVisible([
      appPage.getByRole("button", { name: /^Negocio$/i }),
      appPage.getByText(/^Negocio$/i),
    ]);
    if (!negocioOption) {
      throw new Error("Sidebar item 'Negocio' was not found.");
    }
    await clickAndWait(appPage, negocioOption);

    const miNegocioOption = await pickVisible([
      appPage.getByRole("button", { name: /^Mi Negocio$/i }),
      appPage.getByText(/^Mi Negocio$/i),
    ]);
    if (!miNegocioOption) {
      throw new Error("Sidebar option 'Mi Negocio' was not found.");
    }
    await clickAndWait(appPage, miNegocioOption);

    await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20000 });
    await expect(appPage.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await appPage.screenshot({ path: path.join(screenshotDir, "02-mi-negocio-menu-expanded.png"), fullPage: true });
  });

  await executeStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await pickVisible([
      appPage.getByRole("button", { name: /Agregar Negocio/i }),
      appPage.getByRole("menuitem", { name: /Agregar Negocio/i }),
      appPage.getByText(/Agregar Negocio/i),
    ]);
    if (!agregarNegocio) {
      throw new Error("Could not find 'Agregar Negocio'.");
    }
    await clickAndWait(appPage, agregarNegocio);

    const modal = appPage
      .locator('[role="dialog"], .modal, [class*="modal"]')
      .filter({ hasText: /Crear Nuevo Negocio/i })
      .first();
    await expect(modal).toBeVisible({ timeout: 20000 });

    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const businessNameInput = await pickVisible([
      modal.getByLabel(/Nombre del Negocio/i),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.locator("input"),
    ]);
    if (!businessNameInput) {
      throw new Error("Input 'Nombre del Negocio' is missing in modal.");
    }

    await expect(modal.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await appPage.screenshot({ path: path.join(screenshotDir, "03-agregar-negocio-modal.png"), fullPage: true });

    await businessNameInput.fill("Negocio Prueba Automatizacion");
    const cancelButton = modal.getByRole("button", { name: /Cancelar/i });
    await clickAndWait(appPage, cancelButton);
    await expect(modal).not.toBeVisible({ timeout: 15000 });
  });

  await executeStep("Administrar Negocios view", async () => {
    let administrarNegocios = await pickVisible([
      appPage.getByRole("button", { name: /Administrar Negocios/i }),
      appPage.getByRole("menuitem", { name: /Administrar Negocios/i }),
      appPage.getByText(/Administrar Negocios/i),
    ]);

    if (!administrarNegocios) {
      const miNegocioOption = await pickVisible([
        appPage.getByRole("button", { name: /^Mi Negocio$/i }),
        appPage.getByText(/^Mi Negocio$/i),
      ]);
      if (miNegocioOption) {
        await clickAndWait(appPage, miNegocioOption);
      }
      administrarNegocios = await pickVisible([
        appPage.getByRole("button", { name: /Administrar Negocios/i }),
        appPage.getByRole("menuitem", { name: /Administrar Negocios/i }),
        appPage.getByText(/Administrar Negocios/i),
      ]);
    }

    if (!administrarNegocios) {
      throw new Error("Could not find 'Administrar Negocios'.");
    }

    await clickAndWait(appPage, administrarNegocios);

    await expect(appPage.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 25000 });
    await expect(appPage.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 25000 });
    await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 25000 });
    await expect(appPage.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 25000 });
    await appPage.screenshot({ path: path.join(screenshotDir, "04-administrar-negocios-view.png"), fullPage: true });
  });

  await executeStep("Información General", async () => {
    const infoHeading = appPage.getByText(/Informaci[oó]n General/i).first();
    await expect(infoHeading).toBeVisible();

    const infoSection = appPage
      .locator("section,article,div")
      .filter({ has: appPage.getByText(/Informaci[oó]n General/i).first() })
      .first();
    const sectionText = await infoSection.innerText();
    const lines = sectionText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    const emailLine = lines.find((line) => /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(line));
    const possibleName = lines.find(
      (line) =>
        !/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(line) &&
        !/Informaci[oó]n General|BUSINESS PLAN|Cambiar Plan/i.test(line)
    );

    expect(emailLine).toBeTruthy();
    expect(possibleName).toBeTruthy();
    await expect(appPage.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await executeStep("Detalles de la Cuenta", async () => {
    await expect(appPage.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(appPage.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(appPage.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(appPage.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await executeStep("Tus Negocios", async () => {
    const negociosHeading = appPage.getByText(/Tus Negocios/i).first();
    await expect(negociosHeading).toBeVisible();

    const negociosSection = appPage
      .locator("section,article,div")
      .filter({ has: appPage.getByText(/Tus Negocios/i).first() })
      .first();
    const negociosText = (await negociosSection.innerText()).trim();
    const nonEmptyLines = negociosText.split("\n").map((line) => line.trim()).filter(Boolean);

    expect(nonEmptyLines.length).toBeGreaterThan(2);
    await expect(negociosSection.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(negociosSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
  });

  await executeStep("Términos y Condiciones", async () => {
    const url = await openLegalDocumentAndValidate(
      appPage,
      context,
      /T[ée]rminos y Condiciones/i,
      /T[ée]rminos y Condiciones/i,
      path.join(screenshotDir, "05-terminos-y-condiciones.png")
    );
    details.legalUrls.terminosYCondiciones = url;
  });

  await executeStep("Política de Privacidad", async () => {
    const url = await openLegalDocumentAndValidate(
      appPage,
      context,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      path.join(screenshotDir, "06-politica-de-privacidad.png")
    );
    details.legalUrls.politicaDePrivacidad = url;
  });

  const finalReport = {
    generatedAt: new Date().toISOString(),
    results: report,
    details,
  };
  writeReport(reportPath, finalReport);
  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json",
  });

  const failedSteps = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([step]) => step);

  expect(
    failedSteps,
    `One or more requested workflow validations failed.\nFailed steps: ${failedSteps.join(", ") || "none"}`
  ).toEqual([]);
});
