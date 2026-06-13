const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toExactTextRegex(text) {
  return new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

async function firstVisibleLocator(page, pattern) {
  const candidates = [
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByRole("menuitem", { name: pattern }),
    page.getByRole("tab", { name: pattern }),
    page.getByText(pattern),
  ];

  for (const candidate of candidates) {
    const target = candidate.first();
    const visible = await target.isVisible().catch(() => false);
    if (visible) {
      return target;
    }
  }

  throw new Error(`No visible element found for pattern: ${pattern.toString()}`);
}

async function clickVisibleText(page, text) {
  const target = await firstVisibleLocator(page, toExactTextRegex(text));
  await target.click();
  await waitForUiLoad(page);
}

async function ensureTextVisible(page, text) {
  await expect(page.getByText(toExactTextRegex(text)).first()).toBeVisible();
}

async function capture(page, outputDir, fileName) {
  const destination = path.join(outputDir, fileName);
  await page.screenshot({ path: destination, fullPage: true });
  return destination;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const notes = [];
  const evidence = { screenshots: {}, urls: {} };
  const outputDir = testInfo.outputPath("evidence");
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || "";

  await fs.mkdir(outputDir, { recursive: true });

  const runSection = async (field, action) => {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      notes.push(`${field}: ${error.message}`);
    }
  };

  await runSection("Login", async () => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Missing SALEADS_LOGIN_URL/SALEADS_BASE_URL. Provide the current environment login URL."
      );
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickVisibleText(page, "Sign in with Google");

    const googlePage = await popupPromise;
    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded");
      const accountChooser = googlePage.getByText(
        toExactTextRegex("juanlucasbarbiergarzon@gmail.com")
      );
      if (await accountChooser.first().isVisible().catch(() => false)) {
        await accountChooser.first().click();
      }
      await googlePage.waitForTimeout(1500);
    } else {
      const accountChooser = page.getByText(toExactTextRegex("juanlucasbarbiergarzon@gmail.com"));
      if (await accountChooser.first().isVisible().catch(() => false)) {
        await accountChooser.first().click();
      }
    }

    await page.bringToFront();
    await waitForUiLoad(page);

    const sidebarVisible =
      (await page.locator("aside").first().isVisible().catch(() => false)) ||
      (await page.getByRole("navigation").first().isVisible().catch(() => false));
    expect(sidebarVisible).toBeTruthy();

    await ensureTextVisible(page, "Negocio");

    evidence.screenshots.dashboard = await capture(page, outputDir, "01-dashboard-loaded.png");
  });

  await runSection("Mi Negocio menu", async () => {
    await clickVisibleText(page, "Mi Negocio");
    await ensureTextVisible(page, "Agregar Negocio");
    await ensureTextVisible(page, "Administrar Negocios");
    evidence.screenshots.miNegocioMenu = await capture(
      page,
      outputDir,
      "02-mi-negocio-menu-expanded.png"
    );
  });

  await runSection("Agregar Negocio modal", async () => {
    await clickVisibleText(page, "Agregar Negocio");
    await ensureTextVisible(page, "Crear Nuevo Negocio");
    await ensureTextVisible(page, "Nombre del Negocio");
    await ensureTextVisible(page, "Tienes 2 de 3 negocios");
    await ensureTextVisible(page, "Cancelar");
    await ensureTextVisible(page, "Crear Negocio");

    evidence.screenshots.agregarNegocioModal = await capture(
      page,
      outputDir,
      "03-agregar-negocio-modal.png"
    );

    const nameInput = page.getByLabel(toExactTextRegex("Nombre del Negocio")).first();
    if (await nameInput.isVisible().catch(() => false)) {
      await nameInput.fill("Negocio Prueba Automatización");
      await waitForUiLoad(page);
    }
    await clickVisibleText(page, "Cancelar");
  });

  await runSection("Administrar Negocios view", async () => {
    const adminVisible = await page
      .getByText(toExactTextRegex("Administrar Negocios"))
      .first()
      .isVisible()
      .catch(() => false);
    if (!adminVisible) {
      await clickVisibleText(page, "Mi Negocio");
    }

    await clickVisibleText(page, "Administrar Negocios");
    await ensureTextVisible(page, "Información General");
    await ensureTextVisible(page, "Detalles de la Cuenta");
    await ensureTextVisible(page, "Tus Negocios");
    await ensureTextVisible(page, "Sección Legal");

    evidence.screenshots.administrarNegocios = await capture(
      page,
      outputDir,
      "04-administrar-negocios-view.png"
    );
  });

  await runSection("Información General", async () => {
    await ensureTextVisible(page, "BUSINESS PLAN");
    await ensureTextVisible(page, "Cambiar Plan");

    const hasUserName = await page
      .locator("[data-testid*='name'], [class*='name'], p, span, h1, h2, h3")
      .filter({ hasText: /\S+/ })
      .first()
      .isVisible()
      .catch(() => false);
    expect(hasUserName).toBeTruthy();

    const hasEmail = await page
      .locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/")
      .first()
      .isVisible()
      .catch(() => false);
    expect(hasEmail).toBeTruthy();
  });

  await runSection("Detalles de la Cuenta", async () => {
    await ensureTextVisible(page, "Cuenta creada");
    await ensureTextVisible(page, "Estado activo");
    await ensureTextVisible(page, "Idioma seleccionado");
  });

  await runSection("Tus Negocios", async () => {
    await ensureTextVisible(page, "Tus Negocios");
    await ensureTextVisible(page, "Agregar Negocio");
    await ensureTextVisible(page, "Tienes 2 de 3 negocios");
  });

  const validateLegalPage = async ({ linkText, headingText, screenshotFile, reportKey }) => {
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    const appUrlBefore = page.url();

    await clickVisibleText(page, linkText);

    let targetPage = await popupPromise;
    if (targetPage) {
      await targetPage.waitForLoadState("domcontentloaded");
      await targetPage.bringToFront();
    } else {
      targetPage = page;
      await waitForUiLoad(targetPage);
    }

    await expect(targetPage.getByRole("heading", { name: new RegExp(headingText, "i") }).first()).toBeVisible();

    const pageText = await targetPage.locator("body").innerText();
    expect(pageText.trim().length).toBeGreaterThan(100);

    evidence.screenshots[reportKey] = await capture(targetPage, outputDir, screenshotFile);
    evidence.urls[reportKey] = targetPage.url();

    if (targetPage !== page) {
      await targetPage.close();
      await page.bringToFront();
      await waitForUiLoad(page);
      return;
    }

    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
    });
    await waitForUiLoad(page);
  };

  await runSection("Términos y Condiciones", async () => {
    await validateLegalPage({
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotFile: "05-terminos-y-condiciones.png",
      reportKey: "terminos_y_condiciones",
    });
  });

  await runSection("Política de Privacidad", async () => {
    await validateLegalPage({
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotFile: "06-politica-de-privacidad.png",
      reportKey: "politica_de_privacidad",
    });
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    executed_at: new Date().toISOString(),
    environment: {
      login_url: loginUrl || "not-provided",
      current_url: page.url(),
    },
    results: report,
    evidence,
    notes,
  };

  const finalReportPath = path.join(outputDir, "final-report.json");
  await fs.writeFile(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  // Step 10 requirement: emit PASS/FAIL for all report fields.
  console.log("FINAL_REPORT_START");
  console.log(JSON.stringify(finalReport, null, 2));
  console.log("FINAL_REPORT_END");

  expect.soft(report["Login"]).toBe("PASS");
  expect.soft(report["Mi Negocio menu"]).toBe("PASS");
  expect.soft(report["Agregar Negocio modal"]).toBe("PASS");
  expect.soft(report["Administrar Negocios view"]).toBe("PASS");
  expect.soft(report["Información General"]).toBe("PASS");
  expect.soft(report["Detalles de la Cuenta"]).toBe("PASS");
  expect.soft(report["Tus Negocios"]).toBe("PASS");
  expect.soft(report["Términos y Condiciones"]).toBe("PASS");
  expect.soft(report["Política de Privacidad"]).toBe("PASS");
});
