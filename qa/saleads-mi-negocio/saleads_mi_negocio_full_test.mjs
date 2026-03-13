import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const BASE_URL = process.env.SALEADS_BASE_URL;
const HEADLESS = process.env.HEADLESS !== "false";
const DEFAULT_TIMEOUT = Number(process.env.SALEADS_TIMEOUT_MS || "20000");

const reportFields = [
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

const results = Object.fromEntries(reportFields.map((field) => [field, "FAIL"]));
const failures = [];
const legalUrls = {
  "Términos y Condiciones": null,
  "Política de Privacidad": null,
};

if (!BASE_URL) {
  console.error("Missing required env var: SALEADS_BASE_URL");
  console.error(
    "Set SALEADS_BASE_URL to the environment login page URL (dev/staging/prod) and rerun."
  );
  process.exit(1);
}

const runId = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.join(process.cwd(), "artifacts", runId);

function sectionHeader(title) {
  return `\n========== ${title} ==========\n`;
}

async function waitForUi(page, timeout = DEFAULT_TIMEOUT) {
  await page.waitForLoadState("domcontentloaded", { timeout }).catch(() => undefined);
  await page.waitForTimeout(1000);
}

async function ensureVisible(locator, message, timeout = DEFAULT_TIMEOUT) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
  } catch {
    throw new Error(message);
  }
}

async function firstVisibleByText(root, patterns, timeout = DEFAULT_TIMEOUT) {
  for (const pattern of patterns) {
    const locator =
      typeof pattern === "string"
        ? root.getByText(pattern, { exact: false }).first()
        : root.getByText(pattern).first();
    try {
      await locator.waitFor({ state: "visible", timeout });
      return locator;
    } catch {
      // continue
    }
  }
  return null;
}

async function firstVisibleByRole(root, role, names, timeout = DEFAULT_TIMEOUT) {
  for (const name of names) {
    const locator = root.getByRole(role, { name }).first();
    try {
      await locator.waitFor({ state: "visible", timeout });
      return locator;
    } catch {
      // continue
    }
  }
  return null;
}

async function screenshot(page, name, fullPage = false) {
  const outputPath = path.join(artifactsDir, name);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function sectionText(page, sectionTitleRegex) {
  const heading = page.getByText(sectionTitleRegex).first();
  await ensureVisible(heading, `Could not find section heading ${sectionTitleRegex}`);
  const section = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]").first();
  return section.innerText();
}

async function validateLegalLink({ page, context, linkRegex, headingRegex, screenshotName, reportKey }) {
  const appPage = page;
  const legalSectionHeading = await firstVisibleByText(page, [/secci[oó]n legal/i], 4000);
  const searchRoot =
    legalSectionHeading
      ? legalSectionHeading
          .locator("xpath=ancestor::*[self::section or self::article or self::div][1]")
          .first()
      : page;
  const link =
    (await firstVisibleByRole(searchRoot, "link", [linkRegex])) ||
    (await firstVisibleByText(searchRoot, [linkRegex]));

  if (!link) {
    throw new Error(`Could not find legal link for ${reportKey}`);
  }

  const newTabPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await link.click();
  await waitForUi(appPage);

  const newTab = await newTabPromise;
  const targetPage = newTab || appPage;
  await waitForUi(targetPage);

  const heading =
    (await firstVisibleByRole(targetPage, "heading", [headingRegex], 15000)) ||
    (await firstVisibleByText(targetPage, [headingRegex], 15000));

  if (!heading) {
    throw new Error(`${reportKey}: heading not found`);
  }

  const legalText = await targetPage.locator("body").innerText();
  if (legalText.trim().length < 250) {
    throw new Error(`${reportKey}: legal content looks too short`);
  }

  legalUrls[reportKey] = targetPage.url();
  await screenshot(targetPage, screenshotName, true);

  if (newTab && !newTab.isClosed()) {
    await newTab.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
  await waitForUi(appPage);
}

async function runStep(reportKey, fn) {
  process.stdout.write(sectionHeader(`STEP: ${reportKey}`));
  try {
    await fn();
    results[reportKey] = "PASS";
    console.log(`[PASS] ${reportKey}`);
  } catch (error) {
    results[reportKey] = "FAIL";
    failures.push({ step: reportKey, error: error instanceof Error ? error.message : String(error) });
    console.error(`[FAIL] ${reportKey}: ${error instanceof Error ? error.message : String(error)}`);
  }
}

async function main() {
  await fs.mkdir(artifactsDir, { recursive: true });

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext({
    viewport: { width: 1600, height: 1000 },
  });
  const page = await context.newPage();
  page.setDefaultTimeout(DEFAULT_TIMEOUT);

  try {
    console.log(`Navigating to ${BASE_URL}`);
    await page.goto(BASE_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    await runStep("Login", async () => {
      let loginButton =
        (await firstVisibleByRole(page, "button", [
          /sign in with google/i,
          /iniciar sesi[oó]n con google/i,
          /continuar con google/i,
          /google/i,
        ])) ||
        (await firstVisibleByText(page, [
          /sign in with google/i,
          /iniciar sesi[oó]n con google/i,
          /continuar con google/i,
        ]));

      // Fallback for homepages that require entering a dedicated login view first.
      if (!loginButton) {
        const openLogin =
          (await firstVisibleByRole(page, "button", [
            /iniciar sesi[oó]n/i,
            /sign in/i,
            /login/i,
            /entrar/i,
          ], 8000)) ||
          (await firstVisibleByRole(page, "link", [
            /iniciar sesi[oó]n/i,
            /sign in/i,
            /login/i,
            /entrar/i,
          ], 8000)) ||
          (await firstVisibleByText(page, [
            /iniciar sesi[oó]n/i,
            /sign in/i,
            /login/i,
            /entrar/i,
          ], 8000));

        if (openLogin) {
          await clickAndWait(openLogin, page);
          loginButton =
            (await firstVisibleByRole(page, "button", [
              /sign in with google/i,
              /iniciar sesi[oó]n con google/i,
              /continuar con google/i,
              /google/i,
            ], 10000)) ||
            (await firstVisibleByText(page, [
              /sign in with google/i,
              /iniciar sesi[oó]n con google/i,
              /continuar con google/i,
            ], 10000));
        }
      }

      if (!loginButton) {
        throw new Error("Could not locate Google login button.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(loginButton, page);
      const popup = await popupPromise;

      if (popup) {
        await waitForUi(popup);
        const account = await firstVisibleByText(popup, [/juanlucasbarbiergarzon@gmail\.com/i], 6000);
        if (account) {
          await account.click();
          await waitForUi(popup);
        }
      }

      // Main app + sidebar checks
      const sidebar = page.locator("aside, nav").first();
      await ensureVisible(sidebar, "Main app sidebar not visible after login.");
      await screenshot(page, "01_dashboard_loaded.png", true);
    });

    await runStep("Mi Negocio menu", async () => {
      const sidebar = page.locator("aside, nav").first();
      await ensureVisible(sidebar, "Sidebar not visible.");

      const negocio = await firstVisibleByText(sidebar, [/^negocio$/i, /negocio/i]);
      if (negocio) {
        await clickAndWait(negocio, page);
      }

      const miNegocio = await firstVisibleByText(sidebar, [/mi negocio/i]);
      if (!miNegocio) {
        throw new Error("Could not find 'Mi Negocio' in sidebar.");
      }

      await clickAndWait(miNegocio, page);

      const agregarNegocio = await firstVisibleByText(sidebar, [/agregar negocio/i], 10000);
      const administrarNegocios = await firstVisibleByText(sidebar, [/administrar negocios/i], 10000);
      if (!agregarNegocio || !administrarNegocios) {
        throw new Error("Mi Negocio submenu did not show expected options.");
      }

      await screenshot(page, "02_mi_negocio_menu_expanded.png", true);
    });

    await runStep("Agregar Negocio modal", async () => {
      const addBusinessLink =
        (await firstVisibleByRole(page, "button", [/agregar negocio/i], 8000)) ||
        (await firstVisibleByRole(page, "link", [/agregar negocio/i], 8000)) ||
        (await firstVisibleByText(page, [/agregar negocio/i], 8000));

      if (!addBusinessLink) {
        throw new Error("Could not find 'Agregar Negocio'.");
      }

      await clickAndWait(addBusinessLink, page);

      const modalTitle = await firstVisibleByText(page, [/crear nuevo negocio/i], 12000);
      if (!modalTitle) {
        throw new Error("Modal title 'Crear Nuevo Negocio' not visible.");
      }

      const dialog = page.locator('[role="dialog"]').first();
      const modalRoot = (await dialog.isVisible().catch(() => false)) ? dialog : page;

      const nameInput =
        (await firstVisibleByRole(modalRoot, "textbox", [/nombre del negocio/i], 8000)) ||
        modalRoot.locator("input").first();
      await ensureVisible(nameInput, "Input field 'Nombre del Negocio' not found.", 8000);

      const limitText = await firstVisibleByText(modalRoot, [/tienes\s+2\s+de\s+3\s+negocios/i], 8000);
      if (!limitText) {
        throw new Error("Text 'Tienes 2 de 3 negocios' not visible in modal.");
      }

      const cancelButton = await firstVisibleByRole(modalRoot, "button", [/cancelar/i], 8000);
      const createButton = await firstVisibleByRole(modalRoot, "button", [/crear negocio/i], 8000);
      if (!cancelButton || !createButton) {
        throw new Error("Modal buttons 'Cancelar' and/or 'Crear Negocio' are missing.");
      }

      await nameInput.fill("Negocio Prueba Automatización");
      await screenshot(page, "03_agregar_negocio_modal.png", true);
      await clickAndWait(cancelButton, page);
    });

    await runStep("Administrar Negocios view", async () => {
      const sidebar = page.locator("aside, nav").first();
      await ensureVisible(sidebar, "Sidebar not visible for Administrar Negocios step.");

      let administrar = await firstVisibleByText(sidebar, [/administrar negocios/i], 5000);
      if (!administrar) {
        const miNegocio = await firstVisibleByText(sidebar, [/mi negocio/i], 5000);
        if (miNegocio) {
          await clickAndWait(miNegocio, page);
        }
        administrar = await firstVisibleByText(sidebar, [/administrar negocios/i], 10000);
      }

      if (!administrar) {
        throw new Error("Could not find 'Administrar Negocios'.");
      }

      await clickAndWait(administrar, page);

      const requiredSections = [
        /informaci[oó]n general/i,
        /detalles de la cuenta/i,
        /tus negocios/i,
        /secci[oó]n legal/i,
      ];

      for (const sectionRegex of requiredSections) {
        const section = await firstVisibleByText(page, [sectionRegex], 15000);
        if (!section) {
          throw new Error(`Required section missing: ${sectionRegex}`);
        }
      }

      await screenshot(page, "04_administrar_negocios_page_full.png", true);
    });

    await runStep("Información General", async () => {
      if (results["Administrar Negocios view"] !== "PASS") {
        throw new Error("Prerequisite failed: Administrar Negocios view.");
      }
      const text = await sectionText(page, /informaci[oó]n general/i);
      const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(text);
      const lines = text
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);
      const hasNameCandidate = lines.some(
        (line) =>
          !/@/.test(line) &&
          !/informaci[oó]n|business plan|cambiar plan|plan/i.test(line) &&
          /^[\p{L}][\p{L}\s.'-]{2,}$/u.test(line)
      );
      const hasBusinessPlan = /business plan/i.test(text);
      const hasChangePlan = /cambiar plan/i.test(text);

      if (!hasEmail) {
        throw new Error("User email is not visible in Información General.");
      }
      if (!hasNameCandidate) {
        throw new Error("User name candidate is not visible in Información General.");
      }
      if (!hasBusinessPlan) {
        throw new Error("Text 'BUSINESS PLAN' not visible in Información General.");
      }
      if (!hasChangePlan) {
        throw new Error("Button/text 'Cambiar Plan' not visible in Información General.");
      }
    });

    await runStep("Detalles de la Cuenta", async () => {
      if (results["Administrar Negocios view"] !== "PASS") {
        throw new Error("Prerequisite failed: Administrar Negocios view.");
      }
      const text = await sectionText(page, /detalles de la cuenta/i);
      const checks = [/cuenta creada/i, /estado activo/i, /idioma seleccionado/i];
      for (const check of checks) {
        if (!check.test(text)) {
          throw new Error(`Missing expected text in Detalles de la Cuenta: ${check}`);
        }
      }
    });

    await runStep("Tus Negocios", async () => {
      if (results["Administrar Negocios view"] !== "PASS") {
        throw new Error("Prerequisite failed: Administrar Negocios view.");
      }
      const text = await sectionText(page, /tus negocios/i);
      const hasAdd = /agregar negocio/i.test(text);
      const hasLimit = /tienes\s+2\s+de\s+3\s+negocios/i.test(text);
      const hasListLikeContent = text.split("\n").map((line) => line.trim()).filter(Boolean).length >= 4;

      if (!hasListLikeContent) {
        throw new Error("Business list is not visible in Tus Negocios.");
      }
      if (!hasAdd) {
        throw new Error("Button/text 'Agregar Negocio' is not visible in Tus Negocios.");
      }
      if (!hasLimit) {
        throw new Error("Text 'Tienes 2 de 3 negocios' is not visible in Tus Negocios.");
      }
    });

    await runStep("Términos y Condiciones", async () => {
      if (results["Administrar Negocios view"] !== "PASS") {
        throw new Error("Prerequisite failed: Administrar Negocios view.");
      }
      await validateLegalLink({
        page,
        context,
        linkRegex: /t[eé]rminos y condiciones/i,
        headingRegex: /t[eé]rminos y condiciones/i,
        screenshotName: "05_terminos_y_condiciones.png",
        reportKey: "Términos y Condiciones",
      });
    });

    await runStep("Política de Privacidad", async () => {
      if (results["Administrar Negocios view"] !== "PASS") {
        throw new Error("Prerequisite failed: Administrar Negocios view.");
      }
      await validateLegalLink({
        page,
        context,
        linkRegex: /pol[ií]tica de privacidad/i,
        headingRegex: /pol[ií]tica de privacidad/i,
        screenshotName: "06_politica_de_privacidad.png",
        reportKey: "Política de Privacidad",
      });
    });
  } finally {
    await browser.close();
  }

  const summary = {
    test_name: "saleads_mi_negocio_full_test",
    executed_at: new Date().toISOString(),
    base_url: BASE_URL,
    headless: HEADLESS,
    artifacts_dir: artifactsDir,
    final_urls: legalUrls,
    results,
    failures,
  };

  await fs.writeFile(path.join(artifactsDir, "final_report.json"), `${JSON.stringify(summary, null, 2)}\n`, "utf8");

  process.stdout.write(sectionHeader("FINAL REPORT"));
  console.table(results);
  console.log(`Artifacts: ${artifactsDir}`);
  console.log(`Términos URL: ${legalUrls["Términos y Condiciones"] ?? "N/A"}`);
  console.log(`Privacidad URL: ${legalUrls["Política de Privacidad"] ?? "N/A"}`);

  if (failures.length > 0) {
    process.exit(1);
  }
}

await main();
