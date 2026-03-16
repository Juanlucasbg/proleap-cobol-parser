const { chromium } = require("playwright");
const fs = require("fs/promises");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

const reportTemplate = {
  name: TEST_NAME,
  goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
  startedAt: new Date().toISOString(),
  environment: {
    loginUrl: process.env.SALEADS_LOGIN_URL || null,
    headless: process.env.HEADLESS !== "false",
    googleAccount: process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT,
  },
  results: {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  },
  evidence: {},
  finalUrls: {},
  errors: {},
  finishedAt: null,
};

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 });
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => undefined);
}

async function saveScreenshot(page, outputDir, key, fullPage = false) {
  const filePath = path.join(outputDir, `${key}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function isVisible(locator) {
  return locator.first().isVisible().catch(() => false);
}

async function findFirstVisibleLocator(candidates, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      if (await isVisible(candidate)) {
        return candidate.first();
      }
    }
    await candidates[0].page().waitForTimeout(250);
  }
  throw new Error("No visible element matched the candidate selectors.");
}

async function clickFirstVisible(candidates, timeoutMs = 20000) {
  const locator = await findFirstVisibleLocator(candidates, timeoutMs);
  await locator.click();
  return locator;
}

async function expectVisibleText(page, textRegex, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const textLocator = page.getByText(textRegex, { exact: false }).first();
    if (await isVisible(textLocator)) {
      return textLocator;
    }
    await page.waitForTimeout(250);
  }
  throw new Error(`Expected text not visible: ${textRegex}`);
}

async function runStep(report, fieldName, stepFn) {
  try {
    await stepFn();
    report.results[fieldName] = "PASS";
  } catch (error) {
    report.results[fieldName] = "FAIL";
    report.errors[fieldName] = error instanceof Error ? error.message : String(error);
  }
}

function sanitizeName(text) {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

async function trySelectGoogleAccount(page, accountEmail) {
  const emailOptionCandidates = [
    page.getByText(new RegExp(`^${accountEmail.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`)),
    page.locator(`[data-identifier="${accountEmail}"]`),
    page.locator(`text="${accountEmail}"`),
  ];

  for (const candidate of emailOptionCandidates) {
    if (await isVisible(candidate)) {
      await candidate.first().click();
      await waitForUi(page).catch(() => undefined);
      return true;
    }
  }
  return false;
}

async function sectionTextNearHeading(page, headingRegex) {
  const heading = page.getByText(headingRegex, { exact: false }).first();
  if (!(await isVisible(heading))) {
    throw new Error(`Heading not visible: ${headingRegex}`);
  }
  return heading.evaluate((el) => {
    let current = el;
    for (let i = 0; i < 5 && current; i += 1) {
      const content = current.textContent || "";
      if (content.trim().length > 20) {
        return content;
      }
      current = current.parentElement;
    }
    return el.textContent || "";
  });
}

async function validateLegalPage({
  page,
  outputDir,
  report,
  linkTextRegex,
  headingRegex,
  resultField,
  screenshotKey,
}) {
  const appPage = page;
  const link = await findFirstVisibleLocator(
    [appPage.getByRole("link", { name: linkTextRegex }), appPage.getByText(linkTextRegex, { exact: false })],
    20000
  );

  const popupPromise = appPage.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  await link.click();
  const popup = await popupPromise;

  const legalPage = popup || appPage;
  await waitForUi(legalPage);

  await expectVisibleText(legalPage, headingRegex, 20000);
  const bodyText = await legalPage.locator("body").innerText();
  if (!bodyText || bodyText.trim().length < 120) {
    throw new Error(`${resultField}: legal content text is too short or empty.`);
  }

  const screenshotPath = await saveScreenshot(legalPage, outputDir, screenshotKey, true);
  report.evidence[screenshotKey] = screenshotPath;
  report.finalUrls[resultField] = legalPage.url();

  if (popup) {
    await popup.close().catch(() => undefined);
    await appPage.bringToFront();
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded", timeout: 20000 }).catch(() => undefined);
    await waitForUi(appPage).catch(() => undefined);
  }
}

async function main() {
  const report = JSON.parse(JSON.stringify(reportTemplate));
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const outputDir = path.resolve(
    process.cwd(),
    "artifacts",
    `${TEST_NAME}_${timestamp}`
  );
  await ensureDir(outputDir);

  const browser = await chromium.launch({ headless: process.env.HEADLESS !== "false" });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  try {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    }
    await waitForUi(page);
    if (!loginUrl && page.url() === "about:blank") {
      report.errors.Setup =
        "No SALEADS_LOGIN_URL was provided and no preloaded login page is available in this browser context.";
    }

    await runStep(report, "Login", async () => {
      const googleLoginCandidates = [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByText(
          /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
          { exact: false }
        ),
      ];

      const loginButton = await findFirstVisibleLocator(googleLoginCandidates, 30000);
      const popupPromise = page.waitForEvent("popup", { timeout: 12000 }).catch(() => null);
      await loginButton.click();
      const popup = await popupPromise;

      const authPage = popup || page;
      const accountEmail = process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;
      await trySelectGoogleAccount(authPage, accountEmail);

      if (popup) {
        await waitForUi(page).catch(() => undefined);
      } else {
        await waitForUi(authPage).catch(() => undefined);
      }

      await expectVisibleText(page, /negocio|mi negocio/i, 60000);
      const sidebarVisible =
        (await isVisible(page.locator("aside").first())) ||
        (await isVisible(page.locator('[role="navigation"]').first())) ||
        (await isVisible(page.getByText(/mi negocio|negocio/i).first()));
      if (!sidebarVisible) {
        throw new Error("Main app interface or left sidebar navigation is not visible.");
      }

      const screenshotPath = await saveScreenshot(page, outputDir, "01_dashboard_loaded");
      report.evidence.dashboard = screenshotPath;
    });

    await runStep(report, "Mi Negocio menu", async () => {
      const negocioCandidates = [
        page.getByText(/^Negocio$/i),
        page.getByRole("button", { name: /^Negocio$/i }),
      ];
      if (await isVisible(negocioCandidates[0])) {
        await clickFirstVisible(negocioCandidates, 10000);
        await waitForUi(page);
      }

      const miNegocioCandidates = [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i, { exact: false }),
      ];
      await clickFirstVisible(miNegocioCandidates, 20000);
      await waitForUi(page);

      await expectVisibleText(page, /agregar negocio/i, 20000);
      await expectVisibleText(page, /administrar negocios/i, 20000);

      const screenshotPath = await saveScreenshot(page, outputDir, "02_mi_negocio_menu_expanded");
      report.evidence.miNegocioMenu = screenshotPath;
    });

    await runStep(report, "Agregar Negocio modal", async () => {
      await clickFirstVisible(
        [
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i),
        ],
        20000
      );
      await waitForUi(page);

      await expectVisibleText(page, /crear nuevo negocio/i, 20000);
      await expectVisibleText(page, /nombre del negocio/i, 20000);
      await expectVisibleText(page, /tienes\s*2\s*de\s*3\s*negocios/i, 20000);
      await expectVisibleText(page, /cancelar/i, 20000);
      await expectVisibleText(page, /crear negocio/i, 20000);

      const nameField = page.getByLabel(/nombre del negocio/i);
      if (await isVisible(nameField)) {
        await nameField.fill("Negocio Prueba Automatización");
      } else {
        const fallbackInput = page.locator('input[placeholder*="Nombre"], input[name*="nombre" i]').first();
        if (await isVisible(fallbackInput)) {
          await fallbackInput.fill("Negocio Prueba Automatización");
        }
      }

      const screenshotPath = await saveScreenshot(page, outputDir, "03_agregar_negocio_modal");
      report.evidence.agregarNegocioModal = screenshotPath;

      await clickFirstVisible(
        [page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)],
        10000
      );
      await waitForUi(page);
    });

    await runStep(report, "Administrar Negocios view", async () => {
      const adminMenuVisible = await isVisible(page.getByText(/administrar negocios/i).first());
      if (!adminMenuVisible) {
        await clickFirstVisible(
          [page.getByRole("button", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
          10000
        );
        await waitForUi(page);
      }

      await clickFirstVisible(
        [page.getByRole("button", { name: /administrar negocios/i }), page.getByText(/administrar negocios/i)],
        20000
      );
      await waitForUi(page);

      await expectVisibleText(page, /informaci[oó]n general/i, 20000);
      await expectVisibleText(page, /detalles de la cuenta/i, 20000);
      await expectVisibleText(page, /tus negocios/i, 20000);
      await expectVisibleText(page, /secci[oó]n legal/i, 20000);

      const screenshotPath = await saveScreenshot(page, outputDir, "04_administrar_negocios_full", true);
      report.evidence.administrarNegocios = screenshotPath;
    });

    await runStep(report, "Información General", async () => {
      const sectionText = await sectionTextNearHeading(page, /informaci[oó]n general/i);
      const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(sectionText);
      const hasBusinessPlan = /business plan/i.test(sectionText);
      const hasNameLike =
        sectionText
          .split("\n")
          .map((line) => line.trim())
          .filter(Boolean)
          .some((line) => /[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}/.test(line)) ||
        (await isVisible(page.locator("img[alt*='profile' i], [aria-label*='profile' i]").first()));
      const hasChangePlanButton =
        (await isVisible(page.getByRole("button", { name: /cambiar plan/i }))) ||
        /cambiar plan/i.test(sectionText);

      if (!hasNameLike) {
        throw new Error("User name is not clearly visible in Información General.");
      }
      if (!hasEmail) {
        throw new Error("User email is not visible in Información General.");
      }
      if (!hasBusinessPlan) {
        throw new Error("BUSINESS PLAN text is missing in Información General.");
      }
      if (!hasChangePlanButton) {
        throw new Error("Cambiar Plan button is not visible.");
      }
    });

    await runStep(report, "Detalles de la Cuenta", async () => {
      const sectionText = await sectionTextNearHeading(page, /detalles de la cuenta/i);
      if (!/cuenta creada/i.test(sectionText)) {
        throw new Error("Cuenta creada is not visible.");
      }
      if (!/estado activo/i.test(sectionText)) {
        throw new Error("Estado activo is not visible.");
      }
      if (!/idioma seleccionado/i.test(sectionText)) {
        throw new Error("Idioma seleccionado is not visible.");
      }
    });

    await runStep(report, "Tus Negocios", async () => {
      const sectionText = await sectionTextNearHeading(page, /tus negocios/i);
      const hasBusinessListSignals =
        (await isVisible(page.locator("li, tr, [role='row']").first())) ||
        /negocio/i.test(sectionText);
      const hasAddBusiness =
        (await isVisible(page.getByRole("button", { name: /^Agregar Negocio$/i }))) ||
        /agregar negocio/i.test(sectionText);
      const hasLimitText = /tienes\s*2\s*de\s*3\s*negocios/i.test(sectionText);

      if (!hasBusinessListSignals) {
        throw new Error("Business list is not visible in Tus Negocios.");
      }
      if (!hasAddBusiness) {
        throw new Error("Agregar Negocio button is missing in Tus Negocios.");
      }
      if (!hasLimitText) {
        throw new Error("Text 'Tienes 2 de 3 negocios' is not visible in Tus Negocios.");
      }
    });

    await runStep(report, "Términos y Condiciones", async () => {
      await validateLegalPage({
        page,
        outputDir,
        report,
        linkTextRegex: /t[eé]rminos y condiciones/i,
        headingRegex: /t[eé]rminos y condiciones/i,
        resultField: "Términos y Condiciones",
        screenshotKey: "05_terminos_y_condiciones",
      });
    });

    await runStep(report, "Política de Privacidad", async () => {
      await validateLegalPage({
        page,
        outputDir,
        report,
        linkTextRegex: /pol[ií]tica de privacidad/i,
        headingRegex: /pol[ií]tica de privacidad/i,
        resultField: "Política de Privacidad",
        screenshotKey: "06_politica_de_privacidad",
      });
    });
  } finally {
    report.finishedAt = new Date().toISOString();
    const reportPath = path.join(outputDir, `${sanitizeName(TEST_NAME)}_report.json`);
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    await browser.close().catch(() => undefined);

    console.log("=== Final Report ===");
    console.table(report.results);
    console.log("Evidence directory:", outputDir);
    console.log("Report file:", reportPath);
    if (Object.keys(report.finalUrls).length > 0) {
      console.log("Final legal URLs:", report.finalUrls);
    }
    if (Object.keys(report.errors).length > 0) {
      console.log("Errors:", report.errors);
      process.exitCode = 1;
    }
  }
}

main().catch((error) => {
  console.error("Fatal test execution error:", error);
  process.exit(1);
});
