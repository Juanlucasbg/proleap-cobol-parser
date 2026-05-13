const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const WAIT_MS = 1200;
const DEFAULT_TIMEOUT = 15000;

function timestamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page, timeoutMs = WAIT_MS) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(timeoutMs);
}

async function firstVisible(locators) {
  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  return null;
}

async function clickVisible(page, labelOrRegex, options = {}) {
  const exact = typeof labelOrRegex === "string";
  const textLocator = page.getByText(labelOrRegex, { exact }).first();
  const buttonLocator = page.getByRole("button", { name: labelOrRegex }).first();
  const linkLocator = page.getByRole("link", { name: labelOrRegex }).first();

  const target = await firstVisible([buttonLocator, linkLocator, textLocator]);
  if (!target) {
    throw new Error(`No visible element found for: ${String(labelOrRegex)}`);
  }

  await target.click(options);
  await waitForUi(page);
}

async function isAnyVisible(page, labelsOrRegexes) {
  for (const entry of labelsOrRegexes) {
    const exact = typeof entry === "string";
    const visible = await firstVisible([
      page.getByText(entry, { exact }).first(),
      page.getByRole("button", { name: entry }).first(),
      page.getByRole("link", { name: entry }).first(),
      page.getByRole("heading", { name: entry }).first()
    ]);

    if (visible) {
      return true;
    }
  }

  return false;
}

async function capture(page, artifactDir, name, fullPage = false) {
  const outputPath = path.join(artifactDir, `${name}.png`);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

function recordResult(results, key, pass, details, evidence = {}, error = null) {
  results[key] = {
    status: pass ? "PASS" : "FAIL",
    details,
    evidence,
    error: error ? String(error) : null
  };
}

async function maybeSelectGoogleAccount(pageLike) {
  if (!pageLike) {
    return false;
  }

  const accountOption = pageLike.getByText(ACCOUNT_EMAIL, { exact: true }).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUi(pageLike);
    return true;
  }

  const chooseAccountTitle = pageLike.getByText(/Choose an account|Elegir una cuenta/i).first();
  if (await chooseAccountTitle.isVisible().catch(() => false)) {
    const fallbackOption = pageLike.getByText(ACCOUNT_EMAIL).first();
    if (await fallbackOption.isVisible().catch(() => false)) {
      await fallbackOption.click();
      await waitForUi(pageLike);
      return true;
    }
  }

  return false;
}

async function waitForSidebar(page) {
  const sidebar = await firstVisible([
    page.locator("aside"),
    page.getByRole("navigation").first(),
    page.locator("[class*='sidebar']").first()
  ]);

  if (!sidebar) {
    throw new Error("Main sidebar navigation not visible.");
  }
}

async function navigateLegalLink(page, artifactDir, linkText, headingRegex) {
  const context = page.context();
  const currentUrl = page.url();
  const pageEvent = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);

  await clickVisible(page, linkText);

  const newTab = await pageEvent;
  const targetPage = newTab || page;
  await waitForUi(targetPage, 1500);

  const hasHeading = await isAnyVisible(targetPage, [headingRegex]);
  const contentVisible = await isAnyVisible(targetPage, [
    /términos|condiciones|privacidad|policy|legal/i
  ]);

  const screenshot = await capture(
    targetPage,
    artifactDir,
    linkText.toLowerCase().replace(/\s+/g, "_"),
    true
  );
  const finalUrl = targetPage.url();

  if (newTab) {
    await newTab.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== currentUrl) {
    await page.goBack().catch(() => null);
    await waitForUi(page);
  }

  return { hasHeading, contentVisible, screenshot, finalUrl };
}

async function main() {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || process.env.BASE_URL || null;
  const artifactDir = path.join(
    process.cwd(),
    "artifacts",
    "saleads_mi_negocio_full_test",
    timestamp()
  );
  await ensureDir(artifactDir);

  const report = {
    startedAt: new Date().toISOString(),
    loginUrl,
    accountEmailAttempted: ACCOUNT_EMAIL,
    artifactsDir: artifactDir,
    results: {}
  };

  const browser = await chromium.launch({
    headless: process.env.HEADLESS !== "false"
  });
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 }
  });
  const page = await context.newPage();
  page.setDefaultTimeout(DEFAULT_TIMEOUT);

  try {
    if (!loginUrl) {
      throw new Error(
        "Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL). The test is URL-agnostic and requires runtime environment input."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    // Step 1: Login with Google
    try {
      const alreadyInApp = await isAnyVisible(page, [
        /Mi Negocio/i,
        /Negocio/i,
        /Dashboard|Inicio/i
      ]);

      if (!alreadyInApp) {
        const contextPopup = context
          .waitForEvent("page", { timeout: 10000 })
          .catch(() => null);
        await clickVisible(page, /sign in with google|iniciar sesi[oó]n con google|google/i);
        const popup = await contextPopup;

        if (popup) {
          await popup.waitForLoadState("domcontentloaded");
          await maybeSelectGoogleAccount(popup);
        } else {
          await maybeSelectGoogleAccount(page);
        }
      }

      await waitForSidebar(page);
      const dashboardShot = await capture(page, artifactDir, "01_dashboard_loaded");
      recordResult(
        report.results,
        "Login",
        true,
        "Main interface and left sidebar are visible after Google login.",
        { screenshot: dashboardShot }
      );
    } catch (error) {
      const loginFailShot = await capture(page, artifactDir, "01_login_failure").catch(() => null);
      recordResult(
        report.results,
        "Login",
        false,
        "Unable to complete Google login and validate main interface/sidebar.",
        { screenshot: loginFailShot },
        error
      );
    }

    // Step 2: Open Mi Negocio menu
    try {
      await clickVisible(page, /Negocio/i);
      await clickVisible(page, /Mi Negocio/i);

      const hasAgregar = await isAnyVisible(page, [/Agregar Negocio/i]);
      const hasAdmin = await isAnyVisible(page, [/Administrar Negocios/i]);
      const expandedShot = await capture(page, artifactDir, "02_mi_negocio_menu_expanded");

      recordResult(
        report.results,
        "Mi Negocio menu",
        hasAgregar && hasAdmin,
        "Submenu expanded and options validated.",
        { screenshot: expandedShot, hasAgregar, hasAdmin }
      );
    } catch (error) {
      recordResult(
        report.results,
        "Mi Negocio menu",
        false,
        "Unable to open/validate Mi Negocio submenu.",
        {},
        error
      );
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickVisible(page, /Agregar Negocio/i);
      const titleVisible = await isAnyVisible(page, [/Crear Nuevo Negocio/i]);
      const nameInputVisible =
        (await page.getByLabel(/Nombre del Negocio/i).first().isVisible().catch(() => false)) ||
        (await page.locator("input[placeholder*='Nombre']").first().isVisible().catch(() => false));
      const quotaVisible = await isAnyVisible(page, [/Tienes 2 de 3 negocios/i]);
      const cancelVisible = await isAnyVisible(page, [/Cancelar/i]);
      const createVisible = await isAnyVisible(page, [/Crear Negocio/i]);

      const modalShot = await capture(page, artifactDir, "03_agregar_negocio_modal");

      if (nameInputVisible) {
        const input =
          page.getByLabel(/Nombre del Negocio/i).first() ||
          page.locator("input[placeholder*='Nombre']").first();
        await input.click();
        await input.fill("Negocio Prueba Automatización");
        await waitForUi(page);
      }

      if (await isAnyVisible(page, [/Cancelar/i])) {
        await clickVisible(page, /Cancelar/i);
      }

      recordResult(
        report.results,
        "Agregar Negocio modal",
        titleVisible && nameInputVisible && quotaVisible && cancelVisible && createVisible,
        "Agregar Negocio modal elements validated.",
        {
          screenshot: modalShot,
          checks: {
            titleVisible,
            nameInputVisible,
            quotaVisible,
            cancelVisible,
            createVisible
          }
        }
      );
    } catch (error) {
      recordResult(
        report.results,
        "Agregar Negocio modal",
        false,
        "Unable to validate Agregar Negocio modal.",
        {},
        error
      );
    }

    // Step 4: Open Administrar Negocios
    try {
      if (!(await isAnyVisible(page, [/Administrar Negocios/i]))) {
        if (await isAnyVisible(page, [/Mi Negocio/i])) {
          await clickVisible(page, /Mi Negocio/i);
        }
      }

      await clickVisible(page, /Administrar Negocios/i);
      await waitForUi(page, 1800);

      const hasInfoGeneral = await isAnyVisible(page, [/Informaci[oó]n General/i]);
      const hasDetalles = await isAnyVisible(page, [/Detalles de la Cuenta/i]);
      const hasTusNegocios = await isAnyVisible(page, [/Tus Negocios/i]);
      const hasLegal = await isAnyVisible(page, [/Secci[oó]n Legal/i]);
      const pageShot = await capture(page, artifactDir, "04_administrar_negocios_view", true);

      recordResult(
        report.results,
        "Administrar Negocios view",
        hasInfoGeneral && hasDetalles && hasTusNegocios && hasLegal,
        "Account management sections validated.",
        {
          screenshot: pageShot,
          checks: { hasInfoGeneral, hasDetalles, hasTusNegocios, hasLegal }
        }
      );
    } catch (error) {
      recordResult(
        report.results,
        "Administrar Negocios view",
        false,
        "Unable to open/validate Administrar Negocios page.",
        {},
        error
      );
    }

    // Step 5: Validate Información General
    try {
      const emailVisible = await page
        .getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)
        .first()
        .isVisible()
        .catch(() => false);
      const nameVisible = await isAnyVisible(page, [/Nombre/i, /Usuario/i, /Perfil/i]);
      const planVisible = await isAnyVisible(page, [/BUSINESS PLAN/i]);
      const changePlanVisible = await isAnyVisible(page, [/Cambiar Plan/i]);

      recordResult(
        report.results,
        "Información General",
        emailVisible && nameVisible && planVisible && changePlanVisible,
        "Información General card validated.",
        { checks: { nameVisible, emailVisible, planVisible, changePlanVisible } }
      );
    } catch (error) {
      recordResult(
        report.results,
        "Información General",
        false,
        "Unable to validate Información General.",
        {},
        error
      );
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      const createdVisible = await isAnyVisible(page, [/Cuenta creada/i]);
      const activeVisible = await isAnyVisible(page, [/Estado activo/i]);
      const langVisible = await isAnyVisible(page, [/Idioma seleccionado/i]);

      recordResult(
        report.results,
        "Detalles de la Cuenta",
        createdVisible && activeVisible && langVisible,
        "Detalles de la Cuenta validated.",
        { checks: { createdVisible, activeVisible, langVisible } }
      );
    } catch (error) {
      recordResult(
        report.results,
        "Detalles de la Cuenta",
        false,
        "Unable to validate Detalles de la Cuenta.",
        {},
        error
      );
    }

    // Step 7: Validate Tus Negocios
    try {
      const listVisible = await isAnyVisible(page, [/Tus Negocios/i, /Negocio/i]);
      const addButtonVisible = await isAnyVisible(page, [/Agregar Negocio/i]);
      const quotaVisible = await isAnyVisible(page, [/Tienes 2 de 3 negocios/i]);

      recordResult(
        report.results,
        "Tus Negocios",
        listVisible && addButtonVisible && quotaVisible,
        "Tus Negocios section validated.",
        { checks: { listVisible, addButtonVisible, quotaVisible } }
      );
    } catch (error) {
      recordResult(
        report.results,
        "Tus Negocios",
        false,
        "Unable to validate Tus Negocios section.",
        {},
        error
      );
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const terms = await navigateLegalLink(
        page,
        artifactDir,
        "Términos y Condiciones",
        /Términos y Condiciones/i
      );

      recordResult(
        report.results,
        "Términos y Condiciones",
        terms.hasHeading && terms.contentVisible,
        "Legal terms link validated.",
        { screenshot: terms.screenshot, finalUrl: terms.finalUrl }
      );
    } catch (error) {
      recordResult(
        report.results,
        "Términos y Condiciones",
        false,
        "Unable to validate Términos y Condiciones.",
        {},
        error
      );
    }

    // Step 9: Validate Política de Privacidad
    try {
      const privacy = await navigateLegalLink(
        page,
        artifactDir,
        "Política de Privacidad",
        /Política de Privacidad/i
      );

      recordResult(
        report.results,
        "Política de Privacidad",
        privacy.hasHeading && privacy.contentVisible,
        "Privacy policy link validated.",
        { screenshot: privacy.screenshot, finalUrl: privacy.finalUrl }
      );
    } catch (error) {
      recordResult(
        report.results,
        "Política de Privacidad",
        false,
        "Unable to validate Política de Privacidad.",
        {},
        error
      );
    }
  } finally {
    report.finishedAt = new Date().toISOString();
    report.summary = Object.fromEntries(
      Object.entries(report.results).map(([key, value]) => [key, value.status])
    );
    report.overallStatus = Object.values(report.results).every((v) => v.status === "PASS")
      ? "PASS"
      : "FAIL";

    const reportPath = path.join(artifactDir, "final_report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    await browser.close();

    console.log(`Report generated at: ${reportPath}`);
    console.table(report.summary);

    if (report.overallStatus === "FAIL") {
      process.exitCode = 1;
    }
  }
}

main().catch((error) => {
  console.error("Unhandled execution error:", error);
  process.exit(1);
});
