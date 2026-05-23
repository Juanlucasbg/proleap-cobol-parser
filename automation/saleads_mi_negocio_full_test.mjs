#!/usr/bin/env node

import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

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

function parseArgs(argv) {
  const args = {
    url: process.env.SALEADS_LOGIN_URL ?? "",
    headless: process.env.HEADLESS !== "false",
    accountEmail: process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com",
    outDir:
      process.env.SALEADS_ARTIFACTS_DIR ??
      path.resolve(process.cwd(), "artifacts", "saleads_mi_negocio_full_test"),
    slowMo: Number(process.env.SLOW_MO_MS ?? 250),
  };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--url" && argv[i + 1]) {
      args.url = argv[i + 1];
      i += 1;
    } else if (arg === "--headed") {
      args.headless = false;
    } else if (arg === "--headless") {
      args.headless = true;
    } else if (arg === "--account-email" && argv[i + 1]) {
      args.accountEmail = argv[i + 1];
      i += 1;
    } else if (arg === "--out-dir" && argv[i + 1]) {
      args.outDir = path.resolve(process.cwd(), argv[i + 1]);
      i += 1;
    } else if (arg === "--slow-mo-ms" && argv[i + 1]) {
      args.slowMo = Number(argv[i + 1]);
      i += 1;
    }
  }

  return args;
}

function createReport(url, accountEmail) {
  const sections = {};
  for (const field of REPORT_FIELDS) {
    sections[field] = {
      status: "FAIL",
      validations: [],
      evidence: [],
      notes: [],
    };
  }
  return {
    workflow: "saleads_mi_negocio_full_test",
    startedAt: new Date().toISOString(),
    loginUrl: url,
    googleAccount: accountEmail,
    sections,
  };
}

function addValidation(section, validation, ok, details = "") {
  section.validations.push({
    validation,
    status: ok ? "PASS" : "FAIL",
    details,
  });
}

function finishSection(section) {
  section.status = section.validations.every((v) => v.status === "PASS") ? "PASS" : "FAIL";
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 8000 });
  } catch {
    // Some pages keep active requests; treat this as non-fatal.
  }
  await page.waitForTimeout(400);
}

async function firstVisibleLocator(locators, timeout = 3500) {
  for (const locator of locators) {
    try {
      await locator.waitFor({ state: "visible", timeout });
      return locator;
    } catch {
      // Try next candidate locator.
    }
  }
  return null;
}

async function isVisible(locator, timeout = 3500) {
  try {
    await locator.waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function takeScreenshot(page, screenshotsDir, name, fullPage = false) {
  const filePath = path.join(screenshotsDir, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function detectEmail(text) {
  const match = text.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
  return match?.[0] ?? "";
}

function detectUserNameNearEmail(text, email) {
  if (!email) {
    return "";
  }
  const lines = text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  const idx = lines.findIndex((line) => line.includes(email));
  if (idx <= 0) {
    return "";
  }

  for (let i = Math.max(0, idx - 3); i < idx; i += 1) {
    const candidate = lines[i];
    if (
      candidate.length > 2 &&
      !candidate.includes("@") &&
      !/informacion general|business plan|detalles|cuenta/i.test(candidate)
    ) {
      return candidate;
    }
  }
  return "";
}

async function validateLegalPage({
  appPage,
  context,
  screenshotsDir,
  section,
  linkLabel,
  headingPattern,
  screenshotName,
}) {
  const beforeUrl = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  const link = await firstVisibleLocator([
    appPage.getByRole("link", { name: new RegExp(linkLabel, "i") }).first(),
    appPage.getByText(new RegExp(linkLabel, "i")).first(),
  ]);

  const hasLink = Boolean(link);
  addValidation(section, `Link "${linkLabel}" visible`, hasLink);
  if (!hasLink) {
    finishSection(section);
    return;
  }

  await link.click();
  await waitForUiLoad(appPage);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;
  await waitForUiLoad(targetPage);

  const headingVisible = await isVisible(
    targetPage.getByRole("heading", { name: headingPattern }).first(),
    10000,
  );
  addValidation(
    section,
    `Heading "${headingPattern.source}" visible`,
    headingVisible,
    headingVisible ? "" : "Expected legal heading was not found.",
  );

  const bodyText = (await targetPage.locator("body").innerText()).trim();
  const hasLegalContent = bodyText.length > 300;
  addValidation(
    section,
    "Legal content text visible",
    hasLegalContent,
    hasLegalContent ? "" : "Page body content looked too short.",
  );

  const screenshotPath = await takeScreenshot(targetPage, screenshotsDir, screenshotName, true);
  section.evidence.push(screenshotPath);
  section.notes.push(`Final URL: ${targetPage.url()}`);

  if (popup) {
    await popup.close();
  } else if (appPage.url() !== beforeUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" });
    await waitForUiLoad(appPage);
  }

  finishSection(section);
}

async function run() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.url) {
    throw new Error(
      "Missing target login URL. Pass --url <saleads-login-url> or set SALEADS_LOGIN_URL.",
    );
  }

  const screenshotsDir = path.join(args.outDir, "screenshots");
  await fs.mkdir(screenshotsDir, { recursive: true });

  const report = createReport(args.url, args.accountEmail);
  const browser = await chromium.launch({
    headless: args.headless,
    slowMo: Number.isFinite(args.slowMo) ? args.slowMo : 0,
  });

  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  const page = await context.newPage();

  try {
    await page.goto(args.url, { waitUntil: "domcontentloaded", timeout: 90000 });
    await waitForUiLoad(page);

    // Step 1: Login with Google.
    {
      const section = report.sections["Login"];
      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

      const loginButton = await firstVisibleLocator([
        page
          .getByRole("button", {
            name: /sign in with google|iniciar sesion con google|continuar con google|google/i,
          })
          .first(),
        page
          .getByRole("link", {
            name: /sign in with google|iniciar sesion con google|continuar con google|google/i,
          })
          .first(),
        page
          .getByText(/sign in with google|iniciar sesion con google|continuar con google|google/i)
          .first(),
      ]);

      const loginButtonVisible = Boolean(loginButton);
      addValidation(
        section,
        "Login button or 'Sign in with Google' is visible",
        loginButtonVisible,
      );

      if (loginButton) {
        await loginButton.click();
      }
      await waitForUiLoad(page);

      const popup = await popupPromise;
      if (popup) {
        await waitForUiLoad(popup);
      }
      const authPage = popup ?? page;

      const accountLocator = await firstVisibleLocator([
        authPage.getByText(args.accountEmail, { exact: true }).first(),
        authPage.getByRole("button", { name: new RegExp(args.accountEmail, "i") }).first(),
      ]);

      if (accountLocator) {
        await accountLocator.click();
      }

      if (popup) {
        try {
          await popup.waitForEvent("close", { timeout: 15000 });
        } catch {
          await popup.close();
        }
      }
      await waitForUiLoad(page);

      const sidebarVisible = await firstVisibleLocator([
        page.getByRole("navigation").first(),
        page.locator("aside").first(),
        page.locator('[class*="sidebar"]').first(),
      ]);
      addValidation(
        section,
        "Main application interface appears",
        Boolean(sidebarVisible),
        sidebarVisible ? "" : "Sidebar/main app shell was not detected after login.",
      );
      addValidation(
        section,
        "Left sidebar navigation is visible",
        Boolean(sidebarVisible),
        sidebarVisible ? "" : "Could not confirm left sidebar visibility.",
      );

      const screenshotPath = await takeScreenshot(page, screenshotsDir, "01_dashboard_loaded");
      section.evidence.push(screenshotPath);
      finishSection(section);
    }

    // Step 2: Open Mi Negocio menu.
    {
      const section = report.sections["Mi Negocio menu"];
      const sidebarVisible = await firstVisibleLocator([
        page.getByRole("navigation").first(),
        page.locator("aside").first(),
      ]);
      addValidation(section, "Left sidebar navigation exists", Boolean(sidebarVisible));

      const negocioLabelVisible = await isVisible(page.getByText(/negocio/i).first(), 5000);
      addValidation(section, 'Section "Negocio" is visible', negocioLabelVisible);

      const miNegocioTrigger = await firstVisibleLocator([
        page.getByRole("button", { name: /mi negocio/i }).first(),
        page.getByRole("link", { name: /mi negocio/i }).first(),
        page.getByText(/mi negocio/i).first(),
      ]);
      addValidation(section, 'Option "Mi Negocio" is clickable', Boolean(miNegocioTrigger));
      if (miNegocioTrigger) {
        await miNegocioTrigger.click();
        await waitForUiLoad(page);
      }

      const agregarVisible = await isVisible(page.getByText(/agregar negocio/i).first(), 7000);
      const administrarVisible = await isVisible(
        page.getByText(/administrar negocios/i).first(),
        7000,
      );
      addValidation(section, "Submenu expands", agregarVisible || administrarVisible);
      addValidation(section, '"Agregar Negocio" is visible', agregarVisible);
      addValidation(section, '"Administrar Negocios" is visible', administrarVisible);

      const screenshotPath = await takeScreenshot(page, screenshotsDir, "02_mi_negocio_menu_expanded");
      section.evidence.push(screenshotPath);
      finishSection(section);
    }

    // Step 3: Validate Agregar Negocio modal.
    {
      const section = report.sections["Agregar Negocio modal"];
      const agregarLink = await firstVisibleLocator([
        page.getByRole("button", { name: /agregar negocio/i }).first(),
        page.getByRole("link", { name: /agregar negocio/i }).first(),
        page.getByText(/agregar negocio/i).first(),
      ]);
      addValidation(section, 'Action "Agregar Negocio" exists', Boolean(agregarLink));
      if (agregarLink) {
        await agregarLink.click();
      }
      await waitForUiLoad(page);

      const modalTitleVisible = await isVisible(page.getByText(/crear nuevo negocio/i).first(), 7000);
      const inputVisible =
        (await isVisible(page.getByLabel(/nombre del negocio/i).first(), 2500)) ||
        (await isVisible(page.getByPlaceholder(/nombre del negocio/i).first(), 2500));
      const businessLimitTextVisible = await isVisible(
        page.getByText(/tienes 2 de 3 negocios/i).first(),
        7000,
      );
      const cancelButtonVisible = await isVisible(
        page.getByRole("button", { name: /cancelar/i }).first(),
        7000,
      );
      const createButtonVisible = await isVisible(
        page.getByRole("button", { name: /crear negocio/i }).first(),
        7000,
      );

      addValidation(section, 'Modal title "Crear Nuevo Negocio" is visible', modalTitleVisible);
      addValidation(section, 'Input "Nombre del Negocio" exists', inputVisible);
      addValidation(section, 'Text "Tienes 2 de 3 negocios" is visible', businessLimitTextVisible);
      addValidation(section, 'Button "Cancelar" is visible', cancelButtonVisible);
      addValidation(section, 'Button "Crear Negocio" is visible', createButtonVisible);

      const screenshotPath = await takeScreenshot(page, screenshotsDir, "03_agregar_negocio_modal");
      section.evidence.push(screenshotPath);

      const nameInput = await firstVisibleLocator([
        page.getByLabel(/nombre del negocio/i).first(),
        page.getByPlaceholder(/nombre del negocio/i).first(),
      ]);
      if (nameInput) {
        await nameInput.click();
        await nameInput.fill("Negocio Prueba Automatizacion");
      }
      const cancelButton = await firstVisibleLocator([
        page.getByRole("button", { name: /cancelar/i }).first(),
        page.getByText(/^cancelar$/i).first(),
      ]);
      if (cancelButton) {
        await cancelButton.click();
        await waitForUiLoad(page);
      }

      finishSection(section);
    }

    // Step 4: Open Administrar Negocios.
    {
      const section = report.sections["Administrar Negocios view"];

      const administrarVisible = await isVisible(
        page.getByText(/administrar negocios/i).first(),
        3000,
      );
      if (!administrarVisible) {
        const miNegocioTrigger = await firstVisibleLocator([
          page.getByRole("button", { name: /mi negocio/i }).first(),
          page.getByRole("link", { name: /mi negocio/i }).first(),
          page.getByText(/mi negocio/i).first(),
        ]);
        if (miNegocioTrigger) {
          await miNegocioTrigger.click();
          await waitForUiLoad(page);
        }
      }

      const administrarTrigger = await firstVisibleLocator([
        page.getByRole("button", { name: /administrar negocios/i }).first(),
        page.getByRole("link", { name: /administrar negocios/i }).first(),
        page.getByText(/administrar negocios/i).first(),
      ]);
      addValidation(section, '"Administrar Negocios" option is visible', Boolean(administrarTrigger));
      if (administrarTrigger) {
        await administrarTrigger.click();
        await waitForUiLoad(page);
      }

      const infoGeneralVisible = await isVisible(page.getByText(/informacion general/i).first(), 10000);
      const detallesVisible = await isVisible(
        page.getByText(/detalles de la cuenta/i).first(),
        10000,
      );
      const negociosVisible = await isVisible(page.getByText(/tus negocios/i).first(), 10000);
      const legalVisible = await isVisible(page.getByText(/seccion legal/i).first(), 10000);

      addValidation(section, 'Section "Informacion General" exists', infoGeneralVisible);
      addValidation(section, 'Section "Detalles de la Cuenta" exists', detallesVisible);
      addValidation(section, 'Section "Tus Negocios" exists', negociosVisible);
      addValidation(section, 'Section "Seccion Legal" exists', legalVisible);

      const screenshotPath = await takeScreenshot(
        page,
        screenshotsDir,
        "04_administrar_negocios_view",
        true,
      );
      section.evidence.push(screenshotPath);
      finishSection(section);
    }

    // Step 5: Validate Informacion General.
    {
      const section = report.sections["Informacion General"];
      const bodyText = await page.locator("body").innerText();
      const email = detectEmail(bodyText);
      const userName = detectUserNameNearEmail(bodyText, email);

      const businessPlanVisible = /business plan/i.test(bodyText);
      const cambiarPlanVisible =
        (await isVisible(page.getByRole("button", { name: /cambiar plan/i }).first(), 3000)) ||
        (await isVisible(page.getByText(/cambiar plan/i).first(), 3000));

      addValidation(section, "User name is visible", Boolean(userName), userName ? userName : "");
      addValidation(section, "User email is visible", Boolean(email), email || "");
      addValidation(section, 'Text "BUSINESS PLAN" is visible', businessPlanVisible);
      addValidation(section, 'Button "Cambiar Plan" is visible', cambiarPlanVisible);

      finishSection(section);
    }

    // Step 6: Validate Detalles de la Cuenta.
    {
      const section = report.sections["Detalles de la Cuenta"];
      const bodyText = await page.locator("body").innerText();
      addValidation(section, '"Cuenta creada" is visible', /cuenta creada/i.test(bodyText));
      addValidation(section, '"Estado activo" is visible', /estado activo/i.test(bodyText));
      addValidation(
        section,
        '"Idioma seleccionado" is visible',
        /idioma seleccionado/i.test(bodyText),
      );
      finishSection(section);
    }

    // Step 7: Validate Tus Negocios.
    {
      const section = report.sections["Tus Negocios"];
      const bodyText = await page.locator("body").innerText();

      const agregarButtonVisible =
        (await isVisible(page.getByRole("button", { name: /agregar negocio/i }).first(), 3500)) ||
        (await isVisible(page.getByText(/agregar negocio/i).first(), 3500));

      const businessLimitTextVisible = /tienes 2 de 3 negocios/i.test(bodyText);
      const businessListVisible = /tus negocios/i.test(bodyText) && /negocio/i.test(bodyText);

      addValidation(section, "Business list is visible", businessListVisible);
      addValidation(section, 'Button "Agregar Negocio" exists', agregarButtonVisible);
      addValidation(section, 'Text "Tienes 2 de 3 negocios" is visible', businessLimitTextVisible);
      finishSection(section);
    }

    // Step 8: Validate Terminos y Condiciones.
    await validateLegalPage({
      appPage: page,
      context,
      screenshotsDir,
      section: report.sections["Terminos y Condiciones"],
      linkLabel: "Terminos y Condiciones",
      headingPattern: /terminos y condiciones/i,
      screenshotName: "08_terminos_y_condiciones",
    });

    // Step 9: Validate Politica de Privacidad.
    await validateLegalPage({
      appPage: page,
      context,
      screenshotsDir,
      section: report.sections["Politica de Privacidad"],
      linkLabel: "Politica de Privacidad",
      headingPattern: /politica de privacidad/i,
      screenshotName: "09_politica_de_privacidad",
    });
  } finally {
    await browser.close();
  }

  report.finishedAt = new Date().toISOString();
  report.overallStatus = REPORT_FIELDS.every(
    (field) => report.sections[field].status === "PASS",
  )
    ? "PASS"
    : "FAIL";

  const reportPath = path.join(args.outDir, "final_report.json");
  await fs.mkdir(args.outDir, { recursive: true });
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  const summaryLines = ["SaleADS Mi Negocio workflow report", `Overall: ${report.overallStatus}`, ""];
  for (const field of REPORT_FIELDS) {
    summaryLines.push(`${field}: ${report.sections[field].status}`);
  }
  summaryLines.push("", `Report JSON: ${reportPath}`);
  console.log(summaryLines.join("\n"));

  process.exitCode = report.overallStatus === "PASS" ? 0 : 1;
}

run().catch((error) => {
  console.error("Workflow execution failed:", error.message);
  process.exitCode = 2;
});
