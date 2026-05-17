const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
  await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => {});
}

async function isVisible(locator) {
  return locator.isVisible().catch(() => false);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    if (await isVisible(locator)) {
      return locator;
    }
  }
  return null;
}

async function clickByVisibleText(page, patterns, clickOptions = {}) {
  const patternList = Array.isArray(patterns) ? patterns : [patterns];

  for (const pattern of patternList) {
    const locator = await firstVisibleLocator([
      page.getByRole("button", { name: pattern }).first(),
      page.getByRole("link", { name: pattern }).first(),
      page.getByText(pattern).first(),
    ]);

    if (locator) {
      await locator.click(clickOptions);
      await waitForUi(page);
      return locator;
    }
  }

  throw new Error(`Could not find a clickable element for: ${patternList.join(", ")}`);
}

function buildMarkdownReport(stepResults, legalUrls) {
  const lines = [
    "# SaleADS Mi Negocio Workflow Report",
    "",
    "| Step | Status |",
    "| --- | --- |",
  ];

  for (const field of REPORT_FIELDS) {
    lines.push(`| ${field} | ${stepResults[field]} |`);
  }

  lines.push("");
  lines.push("## Legal URLs");
  lines.push("");
  lines.push(`- Terminos y Condiciones: ${legalUrls.terms || "N/A"}`);
  lines.push(`- Politica de Privacidad: ${legalUrls.privacy || "N/A"}`);
  lines.push("");

  return lines.join("\n");
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.setTimeout(300_000);

  const runDir = path.join(testInfo.outputDir, "saleads-mi-negocio-artifacts");
  fs.mkdirSync(runDir, { recursive: true });

  const stepResults = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  const legalUrls = {
    terms: "",
    privacy: "",
  };

  async function checkpointScreenshot(name, fullPage = false, targetPage = page) {
    const filePath = path.join(runDir, name);
    await targetPage.screenshot({ path: filePath, fullPage });
  }

  async function runStep(reportField, fn) {
    try {
      await fn();
      stepResults[reportField] = "PASS";
    } catch (error) {
      stepResults[reportField] = "FAIL";
      failures.push(`${reportField}: ${error.message}`);
    }
  }

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_LOGIN_URL to the current environment login page. This test does not hardcode a domain."
    );
  }

  await runStep("Login", async () => {
    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickByVisibleText(page, [
      /sign in with google/i,
      /iniciar sesion con google/i,
      /continuar con google/i,
      /google/i,
    ]);

    const popup = await popupPromise;
    const authPage = popup || page;
    await waitForUi(authPage);

    const accountLocator = authPage.getByText("juanlucasbarbiergarzon@gmail.com").first();
    if (await isVisible(accountLocator)) {
      await accountLocator.click();
      if (popup) {
        await popup.waitForEvent("close", { timeout: 25_000 }).catch(() => {});
      }
    }

    await waitForUi(page);

    const sidebarLocator = await firstVisibleLocator([
      page.locator("aside").first(),
      page.locator('nav:has-text("Negocio")').first(),
      page.locator('[class*="sidebar"]').first(),
    ]);
    if (!sidebarLocator) {
      throw new Error("Left sidebar was not found after login.");
    }

    await expect(sidebarLocator).toBeVisible({ timeout: 40_000 });
    await checkpointScreenshot("01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, [/Negocio/i]);
    await clickByVisibleText(page, [/Mi\s*Negocio/i]);

    await expect(page.getByText(/Agregar\s*Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar\s*Negocios/i).first()).toBeVisible();
    await checkpointScreenshot("02-mi-negocio-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, [/Agregar\s*Negocio/i]);

    const modal = page.locator('[role="dialog"], .modal, [aria-modal="true"]').filter({
      hasText: /Crear\s*Nuevo\s*Negocio/i,
    }).first();

    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Crear\s*Nuevo\s*Negocio/i)).toBeVisible();

    const nameInput = await firstVisibleLocator([
      modal.getByLabel(/Nombre\s*del\s*Negocio/i).first(),
      modal.getByPlaceholder(/Nombre\s*del\s*Negocio/i).first(),
      modal.locator('input[name*="nombre"]').first(),
    ]);
    if (!nameInput) {
      throw new Error("The input 'Nombre del Negocio' was not found.");
    }
    await expect(nameInput).toBeVisible();

    await expect(modal.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear\s*Negocio/i })).toBeVisible();

    await checkpointScreenshot("03-agregar-negocio-modal.png");

    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatizacion");
    await modal.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarLocator = page.getByText(/Administrar\s*Negocios/i).first();
    if (!(await isVisible(administrarLocator))) {
      await clickByVisibleText(page, [/Mi\s*Negocio/i]);
    }

    await clickByVisibleText(page, [/Administrar\s*Negocios/i]);

    await expect(page.getByText(/Informaci[oó]n\s*General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles\s*de\s*la\s*Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus\s*Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n\s*Legal/i).first()).toBeVisible();
    await checkpointScreenshot("04-account-page.png", true);
  });

  await runStep("Informacion General", async () => {
    const infoSection = page.locator("section,div,article").filter({
      hasText: /Informaci[oó]n\s*General/i,
    }).first();
    await expect(infoSection).toBeVisible();

    const emailLocator = page.locator(
      'text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/'
    ).first();
    await expect(emailLocator).toBeVisible();

    const infoText = await infoSection.innerText();
    const hasUserName = infoText
      .split(/\n+/)
      .map((line) => line.trim())
      .some((line) => {
        if (!line || line.includes("@")) return false;
        if (/Informaci[oó]n\s*General|BUSINESS\s*PLAN|Cambiar\s*Plan/i.test(line)) return false;
        return /[A-Za-z]/.test(line);
      });

    if (!hasUserName) {
      throw new Error("User name was not detected in Informacion General.");
    }

    await expect(page.getByText(/BUSINESS\s*PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar\s*Plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = page.locator("section,div,article").filter({
      hasText: /Detalles\s*de\s*la\s*Cuenta/i,
    }).first();

    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/Cuenta\s*creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado\s*activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma\s*seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const businessSection = page.locator("section,div,article").filter({
      hasText: /Tus\s*Negocios/i,
    }).first();

    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByText(/Agregar\s*Negocio/i)).toBeVisible();
    await expect(businessSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const sectionText = (await businessSection.innerText()).trim();
    if (sectionText.length < 35) {
      throw new Error("Business section content appears empty.");
    }
  });

  async function validateLegalLink({
    reportField,
    linkPattern,
    headingPattern,
    urlKey,
    screenshotFile,
  }) {
    await runStep(reportField, async () => {
      const context = page.context();
      const beforePageCount = context.pages().length;
      const newPagePromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

      const linkLocator = await firstVisibleLocator([
        page.getByRole("link", { name: linkPattern }).first(),
        page.getByText(linkPattern).first(),
      ]);
      if (!linkLocator) {
        throw new Error(`Could not find legal link for: ${linkPattern}`);
      }

      await linkLocator.click();
      await waitForUi(page);

      let legalPage = await newPagePromise;
      if (legalPage) {
        await legalPage.waitForLoadState("domcontentloaded");
        await legalPage.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => {});
      } else {
        const afterPageCount = context.pages().length;
        if (afterPageCount > beforePageCount) {
          legalPage = context.pages()[afterPageCount - 1];
          await legalPage.waitForLoadState("domcontentloaded");
        } else {
          legalPage = page;
        }
      }

      const heading = await firstVisibleLocator([
        legalPage.getByRole("heading", { name: headingPattern }).first(),
        legalPage.getByText(headingPattern).first(),
      ]);
      if (!heading) {
        throw new Error(`Heading not found: ${headingPattern}`);
      }
      await expect(heading).toBeVisible();

      const legalContent = (await legalPage.locator("body").innerText()).trim();
      if (legalContent.length < 150) {
        throw new Error("Legal content appears too short.");
      }

      legalUrls[urlKey] = legalPage.url();
      await checkpointScreenshot(screenshotFile, true, legalPage);

      if (legalPage !== page) {
        await legalPage.close();
        await page.bringToFront();
      } else {
        await page.goBack().catch(() => {});
      }
      await waitForUi(page);
    });
  }

  await validateLegalLink({
    reportField: "Terminos y Condiciones",
    linkPattern: /T[eé]rminos\s*y\s*Condiciones/i,
    headingPattern: /T[eé]rminos\s*y\s*Condiciones/i,
    urlKey: "terms",
    screenshotFile: "05-terminos-condiciones.png",
  });

  await validateLegalLink({
    reportField: "Politica de Privacidad",
    linkPattern: /Pol[ií]tica\s*de\s*Privacidad/i,
    headingPattern: /Pol[ií]tica\s*de\s*Privacidad/i,
    urlKey: "privacy",
    screenshotFile: "06-politica-privacidad.png",
  });

  const jsonReportPath = path.join(runDir, "final-report.json");
  const markdownReportPath = path.join(runDir, "final-report.md");
  fs.writeFileSync(
    jsonReportPath,
    JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        saleadsLoginUrlInput: loginUrl || "(not provided)",
        results: stepResults,
        legalUrls,
        failures,
      },
      null,
      2
    )
  );
  fs.writeFileSync(markdownReportPath, buildMarkdownReport(stepResults, legalUrls));

  await testInfo.attach("saleads-final-report-json", {
    path: jsonReportPath,
    contentType: "application/json",
  });
  await testInfo.attach("saleads-final-report-md", {
    path: markdownReportPath,
    contentType: "text/markdown",
  });

  expect(
    failures,
    `One or more workflow validations failed. Details:\n${failures.join("\n")}`
  ).toHaveLength(0);
});
