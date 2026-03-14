const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

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

const LOGIN_BUTTON_TEXTS = [
  "Sign in with Google",
  "Iniciar sesión con Google",
  "Ingresar con Google",
  "Continuar con Google",
  "Google"
];

const APP_SIDEBAR_TEXTS = ["Negocio", "Mi Negocio", "Administrar Negocios"];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toSlug(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch {
    // Some screens keep polling; domcontentloaded is enough fallback.
  }
  await page.waitForTimeout(500);
}

async function isLocatorVisible(locator, timeout = 2500) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickFirstVisibleLocator(locators, actionLabel) {
  for (const locator of locators) {
    const count = await locator.count();
    for (let i = 0; i < count; i += 1) {
      const item = locator.nth(i);
      if (await item.isVisible()) {
        await item.click();
        return;
      }
    }
  }

  throw new Error(`Could not find clickable element for "${actionLabel}"`);
}

function locatorCandidatesForText(page, text) {
  const exactRegex = new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i");
  const fuzzyRegex = new RegExp(escapeRegExp(text), "i");

  return [
    page.getByRole("button", { name: exactRegex }),
    page.getByRole("link", { name: exactRegex }),
    page.getByRole("menuitem", { name: exactRegex }),
    page.getByText(exactRegex),
    page.getByText(fuzzyRegex)
  ];
}

async function clickByVisibleText(page, texts, actionLabel) {
  for (const text of texts) {
    const candidates = locatorCandidatesForText(page, text);
    try {
      await clickFirstVisibleLocator(candidates, actionLabel);
      await waitForUi(page);
      return text;
    } catch {
      // Continue trying next text variation.
    }
  }

  throw new Error(
    `Failed to click "${actionLabel}". Tried text variants: ${texts.join(", ")}`
  );
}

async function expectAnyVisibleText(page, texts, validationLabel, timeout = 6000) {
  for (const text of texts) {
    const exact = page.getByText(text, { exact: true });
    if (await isLocatorVisible(exact, timeout)) {
      return text;
    }

    const fuzzy = page.getByText(new RegExp(escapeRegExp(text), "i"));
    if (await isLocatorVisible(fuzzy, timeout)) {
      return text;
    }
  }

  throw new Error(
    `Validation failed for "${validationLabel}". Missing visible text variants: ${texts.join(", ")}`
  );
}

async function captureScreenshot(page, outputDir, name, fullPage = false) {
  const filePath = path.join(outputDir, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function step(report, field, action) {
  try {
    await action();
    report.results[field] = "PASS";
  } catch (error) {
    report.results[field] = "FAIL";
    report.errors.push({
      field,
      message: error instanceof Error ? error.message : String(error)
    });
  }
}

async function validateLegalLink({
  appPage,
  report,
  outputDir,
  linkTexts,
  headingTexts,
  reportField,
  screenshotName,
  urlField
}) {
  const appUrlBefore = appPage.url();
  const context = appPage.context();
  const popupPromise = context
    .waitForEvent("page", { timeout: 10000 })
    .then((newPage) => ({ type: "popup", newPage }))
    .catch(() => null);
  const navigationPromise = appPage
    .waitForNavigation({ timeout: 10000, waitUntil: "domcontentloaded" })
    .then(() => ({ type: "navigation" }))
    .catch(() => null);

  await clickByVisibleText(appPage, linkTexts, reportField);

  let targetPage = appPage;
  const outcome = await Promise.race([popupPromise, navigationPromise]);
  if (outcome && outcome.type === "popup") {
    targetPage = outcome.newPage;
    await targetPage.waitForLoadState("domcontentloaded");
    await waitForUi(targetPage);
  } else {
    await waitForUi(targetPage);
  }

  await expectAnyVisibleText(targetPage, headingTexts, `${reportField} heading`);
  const bodyText = await targetPage.locator("body").innerText();
  if (!bodyText || bodyText.trim().length < 120) {
    throw new Error(`${reportField} content appears too short or empty`);
  }

  const screenshot = await captureScreenshot(targetPage, outputDir, screenshotName);
  report.evidence.screenshots.push(screenshot);
  report.evidence.urls[urlField] = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.close().catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    const wentBack = await appPage
      .goBack({ waitUntil: "domcontentloaded" })
      .then(() => true)
      .catch(() => false);
    if (!wentBack && appPage.url() !== appUrlBefore) {
      await appPage.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(appPage);
  }
}

async function run() {
  const baseUrl =
    process.env.SALEADS_BASE_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    "";

  if (!baseUrl) {
    throw new Error(
      "Missing SALEADS_BASE_URL (or SALEADS_URL / BASE_URL). This test is environment-agnostic and requires the target login URL at runtime."
    );
  }

  const startedAt = new Date().toISOString();
  const stamp = nowStamp();
  const outputDir = path.resolve(
    process.cwd(),
    "artifacts",
    `saleads_mi_negocio_full_test_${stamp}`
  );
  await fs.mkdir(outputDir, { recursive: true });

  const report = {
    testName: "saleads_mi_negocio_full_test",
    startedAt,
    finishedAt: null,
    baseUrl,
    results: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "NOT_RUN"])),
    evidence: {
      screenshots: [],
      urls: {}
    },
    errors: []
  };

  const headless = process.env.HEADLESS !== "false";
  const browser = await chromium.launch({ headless });
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    await step(report, "Login", async () => {
      const alreadyLoggedIn =
        (await isLocatorVisible(page.locator("aside"), 1500)) ||
        (await isLocatorVisible(page.getByRole("navigation"), 1500)) ||
        (await isLocatorVisible(page.getByText(/mi negocio|negocio/i), 1500));

      if (!alreadyLoggedIn) {
        const popupPromise = context
          .waitForEvent("page", { timeout: 10000 })
          .catch(() => null);

        await clickByVisibleText(page, LOGIN_BUTTON_TEXTS, "Sign in with Google");

        const googlePage = await popupPromise;
        if (googlePage) {
          await googlePage.waitForLoadState("domcontentloaded");
          await waitForUi(googlePage);

          const accountTexts = ["juanlucasbarbiergarzon@gmail.com"];
          const accountVisible = await isLocatorVisible(
            googlePage.getByText(accountTexts[0], { exact: true }),
            5000
          );
          if (accountVisible) {
            await clickByVisibleText(
              googlePage,
              accountTexts,
              "Google account selector"
            );
          }

          await page.bringToFront();
          await waitForUi(page);
        } else {
          const accountVisibleOnMain = await isLocatorVisible(
            page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }),
            4000
          );
          if (accountVisibleOnMain) {
            await clickByVisibleText(
              page,
              ["juanlucasbarbiergarzon@gmail.com"],
              "Google account selector on same tab"
            );
          }
        }
      }

      await expectAnyVisibleText(
        page,
        APP_SIDEBAR_TEXTS,
        "main app/sidebar",
        15000
      );
      const dashboardScreenshot = await captureScreenshot(
        page,
        outputDir,
        "01_dashboard_loaded"
      );
      report.evidence.screenshots.push(dashboardScreenshot);
    });

    await step(report, "Mi Negocio menu", async () => {
      await expectAnyVisibleText(page, ["Negocio", "Mi Negocio"], "left sidebar");
      await clickByVisibleText(page, ["Mi Negocio"], "Mi Negocio menu");
      await expectAnyVisibleText(
        page,
        ["Agregar Negocio"],
        "Mi Negocio submenu - Agregar Negocio"
      );
      await expectAnyVisibleText(
        page,
        ["Administrar Negocios"],
        "Mi Negocio submenu - Administrar Negocios"
      );

      const menuScreenshot = await captureScreenshot(
        page,
        outputDir,
        "02_mi_negocio_menu_expanded"
      );
      report.evidence.screenshots.push(menuScreenshot);
    });

    await step(report, "Agregar Negocio modal", async () => {
      await clickByVisibleText(page, ["Agregar Negocio"], "Agregar Negocio");
      await expectAnyVisibleText(
        page,
        ["Crear Nuevo Negocio"],
        "Crear Nuevo Negocio modal title"
      );
      await expectAnyVisibleText(
        page,
        ["Nombre del Negocio"],
        "Nombre del Negocio input label"
      );
      const modalInputVisible =
        (await isLocatorVisible(page.getByLabel(/nombre del negocio/i), 1500)) ||
        (await isLocatorVisible(
          page.getByPlaceholder(/nombre del negocio/i),
          1500
        )) ||
        (await isLocatorVisible(page.locator('[role="dialog"] input'), 1500));
      if (!modalInputVisible) {
        throw new Error("Input field for Nombre del Negocio is missing");
      }
      await expectAnyVisibleText(
        page,
        ["Tienes 2 de 3 negocios"],
        "business quota text"
      );
      await expectAnyVisibleText(page, ["Cancelar"], "Cancelar button");
      await expectAnyVisibleText(page, ["Crear Negocio"], "Crear Negocio button");

      const modalScreenshot = await captureScreenshot(
        page,
        outputDir,
        "03_agregar_negocio_modal"
      );
      report.evidence.screenshots.push(modalScreenshot);

      const inputByLabel = page.getByLabel(/nombre del negocio/i);
      if (await isLocatorVisible(inputByLabel, 1000)) {
        await inputByLabel.first().fill("Negocio Prueba Automatización");
      }
      await clickByVisibleText(page, ["Cancelar"], "Cancelar modal");
    });

    await step(report, "Administrar Negocios view", async () => {
      const administrarVisible = await isLocatorVisible(
        page.getByText(/administrar negocios/i),
        1000
      );
      if (!administrarVisible) {
        await clickByVisibleText(page, ["Mi Negocio"], "Mi Negocio menu re-expand");
      }
      await clickByVisibleText(page, ["Administrar Negocios"], "Administrar Negocios");

      await expectAnyVisibleText(
        page,
        ["Información General"],
        "Información General section"
      );
      await expectAnyVisibleText(
        page,
        ["Detalles de la Cuenta"],
        "Detalles de la Cuenta section"
      );
      await expectAnyVisibleText(page, ["Tus Negocios"], "Tus Negocios section");
      await expectAnyVisibleText(
        page,
        ["Sección Legal", "Legal"],
        "Sección Legal section"
      );

      const accountScreenshot = await captureScreenshot(
        page,
        outputDir,
        "04_administrar_negocios_page",
        true
      );
      report.evidence.screenshots.push(accountScreenshot);
    });

    await step(report, "Información General", async () => {
      const infoSection = page
        .locator(
          'section:has-text("Información General"), div:has-text("Información General")'
        )
        .first();

      const infoSectionVisible = await isLocatorVisible(infoSection, 4000);
      if (!infoSectionVisible) {
        throw new Error("Could not locate Información General block");
      }

      const infoText = await infoSection.innerText();
      const hasLikelyName = /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(
        infoText
      );
      if (!hasLikelyName) {
        throw new Error("User name is not clearly visible in Información General");
      }

      await expectAnyVisibleText(page, ["@"], "user email");
      await expectAnyVisibleText(page, ["BUSINESS PLAN"], "BUSINESS PLAN text");
      await expectAnyVisibleText(page, ["Cambiar Plan"], "Cambiar Plan button");
    });

    await step(report, "Detalles de la Cuenta", async () => {
      await expectAnyVisibleText(page, ["Cuenta creada"], "Cuenta creada");
      await expectAnyVisibleText(page, ["Estado activo"], "Estado activo");
      await expectAnyVisibleText(
        page,
        ["Idioma seleccionado"],
        "Idioma seleccionado"
      );
    });

    await step(report, "Tus Negocios", async () => {
      await expectAnyVisibleText(page, ["Tus Negocios"], "Tus Negocios heading");
      await expectAnyVisibleText(page, ["Agregar Negocio"], "Agregar Negocio button");
      await expectAnyVisibleText(
        page,
        ["Tienes 2 de 3 negocios"],
        "business quota in Tus Negocios"
      );
    });

    await step(report, "Términos y Condiciones", async () => {
      await validateLegalLink({
        appPage: page,
        report,
        outputDir,
        linkTexts: ["Términos y Condiciones", "Terminos y Condiciones"],
        headingTexts: ["Términos y Condiciones", "Terminos y Condiciones"],
        reportField: "Términos y Condiciones",
        screenshotName: "05_terminos_y_condiciones",
        urlField: "terminosYCondiciones"
      });
    });

    await step(report, "Política de Privacidad", async () => {
      await validateLegalLink({
        appPage: page,
        report,
        outputDir,
        linkTexts: ["Política de Privacidad", "Politica de Privacidad"],
        headingTexts: ["Política de Privacidad", "Politica de Privacidad"],
        reportField: "Política de Privacidad",
        screenshotName: "06_politica_de_privacidad",
        urlField: "politicaDePrivacidad"
      });
    });
  } finally {
    report.finishedAt = new Date().toISOString();
    const reportPath = path.join(
      outputDir,
      `${toSlug(report.testName)}_report.json`
    );
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    await context.close();
    await browser.close();

    console.log(JSON.stringify(report, null, 2));
    console.log(`\nReport path: ${reportPath}`);
  }

  const failed = Object.values(report.results).some((status) => status !== "PASS");
  if (failed) {
    process.exitCode = 1;
  }
}

run().catch((error) => {
  console.error("saleads_mi_negocio_full_test failed to execute.");
  console.error(error);
  process.exitCode = 1;
});
