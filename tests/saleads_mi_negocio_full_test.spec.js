const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_NAME = "saleads_mi_negocio_full_test";
const CHECKPOINT_DIR = path.join("artifacts", TEST_NAME);

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function checkpointFile(name) {
  const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "_");
  return path.join(CHECKPOINT_DIR, `${safeName}.png`);
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 8000 });
  } catch {
    // Some screens keep live connections; continue after a short delay.
  }
  await page.waitForTimeout(900);
}

async function capture(page, name, fullPage = false) {
  fs.mkdirSync(CHECKPOINT_DIR, { recursive: true });
  const screenshotPath = checkpointFile(name);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function firstVisibleLocator(page, patterns) {
  const normalized = patterns.map((pattern) =>
    typeof pattern === "string" ? new RegExp(escapeRegExp(pattern), "i") : pattern
  );

  const locatorFactories = [
    (regex) => page.getByRole("button", { name: regex }),
    (regex) => page.getByRole("link", { name: regex }),
    (regex) => page.getByRole("menuitem", { name: regex }),
    (regex) => page.getByRole("tab", { name: regex }),
    (regex) => page.getByText(regex),
  ];

  for (const regex of normalized) {
    for (const factory of locatorFactories) {
      const locator = factory(regex).first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
  }

  return null;
}

async function clickByVisibleText(page, patterns) {
  const locator = await firstVisibleLocator(page, patterns);
  if (!locator) {
    throw new Error(`Could not find clickable element with text: ${patterns.join(", ")}`);
  }

  await locator.click();
  await waitForUi(page);
}

async function waitForSidebar(page) {
  const sidebarCandidates = [
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator("nav"),
  ];

  for (const candidate of sidebarCandidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      return candidate.first();
    }
  }

  throw new Error("Left sidebar navigation is not visible.");
}

async function isDashboardLoaded(page) {
  const hasSidebar = await waitForSidebar(page)
    .then(async (sidebar) => sidebar.isVisible())
    .catch(() => false);

  if (!hasSidebar) {
    return false;
  }

  const dashboardHints = [
    page.getByText(/Negocio/i),
    page.getByText(/Mi Negocio/i),
    page.getByText(/Administrar Negocios/i),
  ];

  for (const hint of dashboardHints) {
    if (await hint.first().isVisible().catch(() => false)) {
      return true;
    }
  }

  return hasSidebar;
}

async function selectGoogleAccountIfPrompted(pageOrPopup) {
  const accountLocators = [
    pageOrPopup.getByText(new RegExp(escapeRegExp(ACCOUNT_EMAIL), "i")).first(),
    pageOrPopup.locator(`[data-identifier="${ACCOUNT_EMAIL}"]`).first(),
    pageOrPopup.getByRole("button", { name: new RegExp(escapeRegExp(ACCOUNT_EMAIL), "i") }).first(),
  ];

  for (const locator of accountLocators) {
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      await waitForUi(pageOrPopup);
      return true;
    }
  }

  return false;
}

async function validateSectionText(page, sectionName, requiredTexts) {
  const section = page
    .locator("section, article, div")
    .filter({ hasText: new RegExp(escapeRegExp(sectionName), "i") })
    .first();

  await expect(section).toBeVisible();

  for (const text of requiredTexts) {
    await expect(
      section.getByText(
        typeof text === "string" ? new RegExp(escapeRegExp(text), "i") : text
      )
    ).toBeVisible();
  }
}

async function openLegalLink(page, linkText) {
  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickByVisibleText(page, [linkText]);
  const popup = await popupPromise;

  const targetPage = popup || page;
  await targetPage.waitForLoadState("domcontentloaded");
  await waitForUi(targetPage);

  return { targetPage, openedInNewTab: Boolean(popup) };
}

async function returnToApplication(page, openedPage, openedInNewTab) {
  if (openedInNewTab) {
    await openedPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await openedPage.goBack({ waitUntil: "domcontentloaded" });
    await waitForUi(openedPage);
  }
}

test(TEST_NAME, async ({ page }, testInfo) => {
  const report = {
    Login: { status: "FAIL", details: "" },
    "Mi Negocio menu": { status: "FAIL", details: "" },
    "Agregar Negocio modal": { status: "FAIL", details: "" },
    "Administrar Negocios view": { status: "FAIL", details: "" },
    "Información General": { status: "FAIL", details: "" },
    "Detalles de la Cuenta": { status: "FAIL", details: "" },
    "Tus Negocios": { status: "FAIL", details: "" },
    "Términos y Condiciones": { status: "FAIL", details: "", finalUrl: "" },
    "Política de Privacidad": { status: "FAIL", details: "", finalUrl: "" },
  };

  const checkpointEvidence = {};
  const baseOrLoginUrl =
    process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;

  if (baseOrLoginUrl) {
    await page.goto(baseOrLoginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_LOGIN_URL or SALEADS_BASE_URL (or BASE_URL). The test avoids hardcoded domains."
    );
  }

  // Step 1: Login with Google
  try {
    if (!(await isDashboardLoaded(page))) {
      const googlePopupPromise = page
        .context()
        .waitForEvent("page", { timeout: 10000 })
        .catch(() => null);

      await clickByVisibleText(page, [
        /Sign in with Google/i,
        /Iniciar sesi[oó]n con Google/i,
        /Continuar con Google/i,
        /^Google$/i,
      ]);

      const googlePopup = await googlePopupPromise;
      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded");
        await waitForUi(googlePopup);
        await selectGoogleAccountIfPrompted(googlePopup);
      } else {
        await selectGoogleAccountIfPrompted(page);
      }
    }

    await expect.poll(() => isDashboardLoaded(page)).toBeTruthy();
    await expect(await waitForSidebar(page)).toBeVisible();
    checkpointEvidence.dashboard = await capture(page, "dashboard_loaded");
    report.Login = {
      status: "PASS",
      details: "Dashboard loaded and left sidebar is visible after Google login.",
    };
  } catch (error) {
    report.Login = { status: "FAIL", details: String(error.message || error) };
  }

  // Step 2: Open Mi Negocio menu
  try {
    await waitForSidebar(page);
    await clickByVisibleText(page, [/^Negocio$/i, /^Mi Negocio$/i]);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    checkpointEvidence.miNegocioMenu = await capture(page, "mi_negocio_menu_expanded");
    report["Mi Negocio menu"] = {
      status: "PASS",
      details: "Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios visible.",
    };
  } catch (error) {
    report["Mi Negocio menu"] = { status: "FAIL", details: String(error.message || error) };
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await clickByVisibleText(page, [/Agregar Negocio/i]);
    const modal = page
      .locator('[role="dialog"], .modal, [data-state="open"]')
      .filter({ hasText: /Crear Nuevo Negocio/i })
      .first();

    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    const businessNameField = modal
      .locator('input[placeholder*="Nombre"], input[name*="nombre"], input')
      .first();
    if (await businessNameField.isVisible().catch(() => false)) {
      await businessNameField.click();
      await waitForUi(page);
      await businessNameField.fill("Negocio Prueba Automatización");
    }

    checkpointEvidence.agregarNegocioModal = await capture(page, "agregar_negocio_modal");

    const cancelButton = modal.getByRole("button", { name: /Cancelar/i }).first();
    if (await cancelButton.isVisible().catch(() => false)) {
      await cancelButton.click();
      await waitForUi(page);
    }

    report["Agregar Negocio modal"] = {
      status: "PASS",
      details: "Agregar Negocio modal validated and closed with Cancelar.",
    };
  } catch (error) {
    report["Agregar Negocio modal"] = {
      status: "FAIL",
      details: String(error.message || error),
    };
  }

  // Step 4: Open Administrar Negocios
  try {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, [/^Negocio$/i, /^Mi Negocio$/i]);
    }

    await clickByVisibleText(page, [/Administrar Negocios/i]);
    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();
    checkpointEvidence.administrarNegocios = await capture(
      page,
      "administrar_negocios_account_page",
      true
    );

    report["Administrar Negocios view"] = {
      status: "PASS",
      details: "Account page loaded with all required sections.",
    };
  } catch (error) {
    report["Administrar Negocios view"] = {
      status: "FAIL",
      details: String(error.message || error),
    };
  }

  // Step 5: Validate Información General
  try {
    const infoSection = page
      .locator("section, article, div")
      .filter({ hasText: /Informaci[oó]n General/i })
      .first();

    await expect(infoSection).toBeVisible();
    await expect(infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)).toBeVisible();
    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const infoText = (await infoSection.innerText()).trim();
    const hasPotentialName = infoText
      .split("\n")
      .map((line) => line.trim())
      .some(
        (line) =>
          line.length > 2 &&
          !line.includes("@") &&
          !/Informaci[oó]n General|BUSINESS PLAN|Cambiar Plan/i.test(line)
      );

    expect(hasPotentialName).toBeTruthy();

    report["Información General"] = {
      status: "PASS",
      details: "Name-like text, email, plan label, and Cambiar Plan are visible.",
    };
  } catch (error) {
    report["Información General"] = { status: "FAIL", details: String(error.message || error) };
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await validateSectionText(page, "Detalles de la Cuenta", [
      /Cuenta creada/i,
      /Estado activo/i,
      /Idioma seleccionado/i,
    ]);
    report["Detalles de la Cuenta"] = {
      status: "PASS",
      details: "Detalles de la Cuenta fields are visible.",
    };
  } catch (error) {
    report["Detalles de la Cuenta"] = { status: "FAIL", details: String(error.message || error) };
  }

  // Step 7: Validate Tus Negocios
  try {
    const businessesSection = page
      .locator("section, article, div")
      .filter({ hasText: /Tus Negocios/i })
      .first();

    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(businessesSection.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();

    const businessItem = businessesSection.locator("li, [role='listitem'], table tr, .card").first();
    await expect(businessItem).toBeVisible();

    report["Tus Negocios"] = {
      status: "PASS",
      details: "Business list, Agregar Negocio button, and usage text are visible.",
    };
  } catch (error) {
    report["Tus Negocios"] = { status: "FAIL", details: String(error.message || error) };
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const { targetPage, openedInNewTab } = await openLegalLink(page, "Términos y Condiciones");
    await expect(targetPage.getByText(/Términos y Condiciones/i).first()).toBeVisible();
    await expect(targetPage.locator("p, li, article, section").first()).toBeVisible();
    checkpointEvidence.terminos = await capture(targetPage, "terminos_y_condiciones", true);
    report["Términos y Condiciones"] = {
      status: "PASS",
      details: "Términos y Condiciones heading and legal content are visible.",
      finalUrl: targetPage.url(),
    };
    await returnToApplication(page, targetPage, openedInNewTab);
  } catch (error) {
    report["Términos y Condiciones"] = {
      status: "FAIL",
      details: String(error.message || error),
      finalUrl: "",
    };
  }

  // Step 9: Validate Política de Privacidad
  try {
    const { targetPage, openedInNewTab } = await openLegalLink(page, "Política de Privacidad");
    await expect(targetPage.getByText(/Política de Privacidad/i).first()).toBeVisible();
    await expect(targetPage.locator("p, li, article, section").first()).toBeVisible();
    checkpointEvidence.privacidad = await capture(targetPage, "politica_de_privacidad", true);
    report["Política de Privacidad"] = {
      status: "PASS",
      details: "Política de Privacidad heading and legal content are visible.",
      finalUrl: targetPage.url(),
    };
    await returnToApplication(page, targetPage, openedInNewTab);
  } catch (error) {
    report["Política de Privacidad"] = {
      status: "FAIL",
      details: String(error.message || error),
      finalUrl: "",
    };
  }

  // Step 10: Final report
  const reportPayload = {
    name: TEST_NAME,
    startedAt: new Date(testInfo.startTime).toISOString(),
    finishedAt: new Date().toISOString(),
    evidence: checkpointEvidence,
    results: report,
  };

  fs.mkdirSync(CHECKPOINT_DIR, { recursive: true });
  const reportFile = path.join(CHECKPOINT_DIR, "final_report.json");
  fs.writeFileSync(reportFile, JSON.stringify(reportPayload, null, 2), "utf8");

  testInfo.attach("final-report", {
    body: Buffer.from(JSON.stringify(reportPayload, null, 2), "utf8"),
    contentType: "application/json",
  });

  // Print summary to console for CI logs.
  const summary = Object.entries(report).map(([step, result]) => ({
    step,
    status: result.status,
    details: result.details,
    finalUrl: result.finalUrl || "",
  }));
  // eslint-disable-next-line no-console
  console.table(summary);

  const failedSteps = summary.filter((item) => item.status !== "PASS");
  expect(
    failedSteps,
    `One or more steps failed. Full report: ${reportFile}`
  ).toEqual([]);
});
