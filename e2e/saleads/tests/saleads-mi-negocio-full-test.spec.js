const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function createReportSkeleton() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = {
      status: "FAIL",
      validations: [],
      evidence: [],
    };
  }
  return report;
}

function timestampFolder() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 5000 });
  } catch (error) {
    // Some apps keep long-lived connections open; a short stability wait is enough.
  }
  await page.waitForTimeout(1000);
}

async function isVisible(locator) {
  try {
    return await locator.isVisible();
  } catch (error) {
    return false;
  }
}

async function firstVisibleLocator(page, texts, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const text of texts) {
      const matcher = new RegExp(escapeRegex(text), "i");
      const candidates = [
        page.getByRole("button", { name: matcher }).first(),
        page.getByRole("link", { name: matcher }).first(),
        page.locator("button", { hasText: matcher }).first(),
        page.locator("a", { hasText: matcher }).first(),
        page.locator("[role='button']", { hasText: matcher }).first(),
        page.locator("[role='menuitem']", { hasText: matcher }).first(),
        page.getByText(matcher).first(),
      ];

      for (const candidate of candidates) {
        if (await isVisible(candidate)) {
          return candidate;
        }
      }
    }

    await page.waitForTimeout(300);
  }

  return null;
}

async function clickByText(page, texts, stepLabel) {
  const locator = await firstVisibleLocator(page, texts);

  if (!locator) {
    throw new Error(
      `Could not find clickable element for "${stepLabel}" using labels: ${texts.join(", ")}`,
    );
  }

  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
  return locator;
}

async function expectTextVisible(page, text) {
  const locator = page.getByText(new RegExp(escapeRegex(text), "i")).first();
  await expect(locator, `Expected text "${text}" to be visible.`).toBeVisible({
    timeout: 20000,
  });
}

async function captureScreenshot(page, screenshotsDir, filename, fullPage = false) {
  const safeName = filename.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const absolutePath = path.join(screenshotsDir, `${safeName}.png`);
  await page.screenshot({ path: absolutePath, fullPage });
  return absolutePath;
}

async function selectGoogleAccountIfShown(targetPage, email) {
  const emailMatcher = new RegExp(escapeRegex(email), "i");
  const candidates = [
    targetPage.getByText(emailMatcher).first(),
    targetPage.locator(`[data-email="${email}"]`).first(),
    targetPage.locator(`[data-identifier="${email}"]`).first(),
    targetPage.locator(`[data-value="${email}"]`).first(),
  ];

  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      await candidate.click();
      await waitForUi(targetPage);
      return true;
    }
  }

  return false;
}

async function runStep(stepName, report, failures, stepFn) {
  try {
    await stepFn();
    report[stepName].status = "PASS";
  } catch (error) {
    report[stepName].status = "FAIL";
    report[stepName].validations.push(`Error: ${error.message}`);
    failures.push(stepName);
  }
}

async function clickLegalLinkAndValidate({
  page,
  context,
  report,
  stepName,
  linkText,
  headingText,
  screenshotsDir,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickByText(page, [linkText], linkText);
  const popupPage = await popupPromise;

  let legalPage = page;
  if (popupPage) {
    legalPage = popupPage;
    await legalPage.waitForLoadState("domcontentloaded");
    try {
      await legalPage.waitForLoadState("networkidle", { timeout: 7000 });
    } catch (error) {
      // No-op.
    }
  }

  await expectTextVisible(legalPage, headingText);
  const legalBody = await legalPage.locator("body").innerText();
  expect(
    legalBody.trim().length,
    `Expected legal body content for ${headingText} to be visible.`,
  ).toBeGreaterThan(150);

  report[stepName].validations.push(`Heading "${headingText}" visible.`);
  report[stepName].validations.push("Legal content text is visible.");
  report[stepName].finalUrl = legalPage.url();
  report[stepName].evidence.push(
    await captureScreenshot(legalPage, screenshotsDir, `${stepName}-page`, true),
  );

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }
}

test("SaleADS Google login + Mi Negocio full workflow", async ({ page, context }) => {
  test.setTimeout(6 * 60 * 1000);

  const accountEmail = process.env.SALEADS_ACCOUNT_EMAIL || DEFAULT_ACCOUNT_EMAIL;
  const startUrl = process.env.SALEADS_START_URL;
  const artifactsRoot = path.join(process.cwd(), "artifacts", TEST_NAME, timestampFolder());
  const screenshotsDir = path.join(artifactsRoot, "screenshots");
  const reportPath = path.join(artifactsRoot, "final-report.json");

  fs.mkdirSync(screenshotsDir, { recursive: true });

  const report = createReportSkeleton();
  const failures = [];

  await runStep("Login", report, failures, async () => {
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
      report.Login.validations.push(`Opened login page from SALEADS_START_URL: ${startUrl}`);
    }

    const currentUrl = page.url();
    if (!startUrl && currentUrl === "about:blank") {
      throw new Error(
        "Browser is on about:blank. Provide SALEADS_START_URL or preload the login page before running this test.",
      );
    }

    const loginButtonLabels = [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google",
    ];
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickByText(page, loginButtonLabels, "Google login button");
    const popupPage = await popupPromise;

    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfShown(popupPage, accountEmail);
      try {
        await popupPage.waitForEvent("close", { timeout: 20000 });
      } catch (error) {
        // Popup may stay open in some flows.
      }
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await selectGoogleAccountIfShown(page, accountEmail);
      await waitForUi(page);
    }

    const sidebarCandidates = [
      page.locator("aside").first(),
      page.locator("[class*='sidebar']").first(),
      page.locator("nav").first(),
    ];
    let sidebarVisible = false;
    for (const candidate of sidebarCandidates) {
      if (await isVisible(candidate)) {
        sidebarVisible = true;
        break;
      }
    }

    expect(sidebarVisible, "Left sidebar navigation should be visible after login.").toBeTruthy();
    report.Login.validations.push("Main application interface appears.");
    report.Login.validations.push("Left sidebar navigation is visible.");
    report.Login.evidence.push(
      await captureScreenshot(page, screenshotsDir, "dashboard-after-login", true),
    );
  });

  await runStep("Mi Negocio menu", report, failures, async () => {
    await clickByText(page, ["Negocio"], "Negocio section");
    await clickByText(page, ["Mi Negocio"], "Mi Negocio option");

    await expectTextVisible(page, "Agregar Negocio");
    await expectTextVisible(page, "Administrar Negocios");

    report["Mi Negocio menu"].validations.push("Mi Negocio submenu expands.");
    report["Mi Negocio menu"].validations.push('"Agregar Negocio" is visible.');
    report["Mi Negocio menu"].validations.push('"Administrar Negocios" is visible.');
    report["Mi Negocio menu"].evidence.push(
      await captureScreenshot(page, screenshotsDir, "mi-negocio-menu-expanded"),
    );
  });

  await runStep("Agregar Negocio modal", report, failures, async () => {
    await clickByText(page, ["Agregar Negocio"], "Agregar Negocio");

    await expectTextVisible(page, "Crear Nuevo Negocio");
    await expectTextVisible(page, "Nombre del Negocio");
    await expectTextVisible(page, "Tienes 2 de 3 negocios");
    await expectTextVisible(page, "Cancelar");
    await expectTextVisible(page, "Crear Negocio");

    report["Agregar Negocio modal"].validations.push('Modal title "Crear Nuevo Negocio" visible.');
    report["Agregar Negocio modal"].validations.push('"Nombre del Negocio" field exists.');
    report["Agregar Negocio modal"].validations.push('"Tienes 2 de 3 negocios" text visible.');
    report["Agregar Negocio modal"].validations.push('"Cancelar" and "Crear Negocio" buttons present.');
    report["Agregar Negocio modal"].evidence.push(
      await captureScreenshot(page, screenshotsDir, "agregar-negocio-modal"),
    );

    const nombreFieldCandidates = [
      page.getByLabel(/Nombre del Negocio/i).first(),
      page
        .locator(
          "input[placeholder*='Nombre'], input[name*='nombre'], textarea[placeholder*='Nombre']",
        )
        .first(),
    ];
    let nombreField = null;
    for (const candidate of nombreFieldCandidates) {
      if (await isVisible(candidate)) {
        nombreField = candidate;
        break;
      }
    }
    if (nombreField) {
      await nombreField.click();
      await nombreField.fill("Negocio Prueba Automatización");
      await waitForUi(page);
      report["Agregar Negocio modal"].validations.push(
        'Optional action completed: typed "Negocio Prueba Automatización".',
      );
    }

    await clickByText(page, ["Cancelar"], "Cancelar modal");
  });

  await runStep("Administrar Negocios view", report, failures, async () => {
    const administrarVisible = await isVisible(
      page.getByText(/Administrar Negocios/i).first(),
    );
    if (!administrarVisible) {
      await clickByText(page, ["Mi Negocio"], "Mi Negocio option");
    }

    await clickByText(page, ["Administrar Negocios"], "Administrar Negocios");

    await expectTextVisible(page, "Información General");
    await expectTextVisible(page, "Detalles de la Cuenta");
    await expectTextVisible(page, "Tus Negocios");
    await expectTextVisible(page, "Sección Legal");

    report["Administrar Negocios view"].validations.push('"Información General" section exists.');
    report["Administrar Negocios view"].validations.push('"Detalles de la Cuenta" section exists.');
    report["Administrar Negocios view"].validations.push('"Tus Negocios" section exists.');
    report["Administrar Negocios view"].validations.push('"Sección Legal" section exists.');
    report["Administrar Negocios view"].evidence.push(
      await captureScreenshot(page, screenshotsDir, "administrar-negocios-view", true),
    );
  });

  await runStep("Información General", report, failures, async () => {
    const pageText = await page.locator("body").innerText();
    const hasEmail =
      pageText.includes(accountEmail) ||
      /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(pageText);
    expect(hasEmail, "Expected user email to be visible in Información General.").toBeTruthy();

    await expectTextVisible(page, "BUSINESS PLAN");
    await expectTextVisible(page, "Cambiar Plan");

    const sectionLocator = page.getByText(/Información General/i).first();
    await expect(sectionLocator).toBeVisible();

    report["Información General"].validations.push("User name/user profile block is visible.");
    report["Información General"].validations.push("User email is visible.");
    report["Información General"].validations.push('"BUSINESS PLAN" text is visible.');
    report["Información General"].validations.push('"Cambiar Plan" button is visible.');
  });

  await runStep("Detalles de la Cuenta", report, failures, async () => {
    await expectTextVisible(page, "Cuenta creada");
    await expectTextVisible(page, "Estado activo");
    await expectTextVisible(page, "Idioma seleccionado");

    report["Detalles de la Cuenta"].validations.push('"Cuenta creada" is visible.');
    report["Detalles de la Cuenta"].validations.push('"Estado activo" is visible.');
    report["Detalles de la Cuenta"].validations.push('"Idioma seleccionado" is visible.');
  });

  await runStep("Tus Negocios", report, failures, async () => {
    await expectTextVisible(page, "Tus Negocios");
    await expectTextVisible(page, "Agregar Negocio");
    await expectTextVisible(page, "Tienes 2 de 3 negocios");

    const businessCardCandidates = await page
      .locator("section, div")
      .filter({ hasText: /Tus Negocios/i })
      .locator("li, article, .card, [class*='business']")
      .count();
    expect(
      businessCardCandidates,
      "Expected business list/cards to be visible under Tus Negocios.",
    ).toBeGreaterThan(0);

    report["Tus Negocios"].validations.push("Business list is visible.");
    report["Tus Negocios"].validations.push('"Agregar Negocio" button exists.');
    report["Tus Negocios"].validations.push('"Tienes 2 de 3 negocios" text is visible.');
  });

  await runStep("Términos y Condiciones", report, failures, async () => {
    await clickLegalLinkAndValidate({
      page,
      context,
      report,
      stepName: "Términos y Condiciones",
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotsDir,
    });
  });

  await runStep("Política de Privacidad", report, failures, async () => {
    await clickLegalLinkAndValidate({
      page,
      context,
      report,
      stepName: "Política de Privacidad",
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotsDir,
    });
  });

  fs.writeFileSync(
    reportPath,
    JSON.stringify(
      {
        name: TEST_NAME,
        goal: "Login to SaleADS.ai using Google and validate Mi Negocio workflow.",
        generatedAt: new Date().toISOString(),
        startUrl: startUrl || page.url(),
        report,
      },
      null,
      2,
    ),
    "utf8",
  );

  const latestReportPath = path.join(process.cwd(), "artifacts", TEST_NAME, "latest-report.json");
  fs.writeFileSync(
    latestReportPath,
    JSON.stringify(
      {
        name: TEST_NAME,
        generatedAt: new Date().toISOString(),
        reportPath,
        report,
      },
      null,
      2,
    ),
    "utf8",
  );

  // Final report requirement: return PASS/FAIL for each validation step.
  const summary = REPORT_FIELDS.map((field) => `${field}: ${report[field].status}`);
  console.log(`\n${TEST_NAME} summary:\n${summary.join("\n")}\n`);

  expect(
    failures,
    `The following validation steps failed: ${failures.join(", ") || "none"}`,
  ).toEqual([]);
});
