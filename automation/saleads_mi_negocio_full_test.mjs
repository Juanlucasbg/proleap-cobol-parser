import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const TEST_NAME = "saleads_mi_negocio_full_test";

const report = {
  test: TEST_NAME,
  startedAt: new Date().toISOString(),
  steps: [],
  evidence: {
    screenshots: [],
    finalUrls: {},
  },
};

function boolToStatus(value) {
  return value ? "PASS" : "FAIL";
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page, timeout = 10000) {
  try {
    await page.waitForLoadState("networkidle", { timeout });
  } catch {
    try {
      await page.waitForLoadState("domcontentloaded", { timeout: 3000 });
    } catch {
      // Ignore and continue with explicit element waits in each step.
    }
  }
  await page.waitForTimeout(500);
}

function textRegex(text) {
  return new RegExp(`^\\s*${text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*$`, "i");
}

async function locatorByVisibleText(page, text) {
  const roles = ["button", "link", "menuitem", "tab", "heading"];
  for (const role of roles) {
    const candidate = page.getByRole(role, { name: textRegex(text) }).first();
    if ((await candidate.count()) > 0) {
      return candidate;
    }
  }
  const fallback = page.getByText(textRegex(text)).first();
  if ((await fallback.count()) > 0) {
    return fallback;
  }
  throw new Error(`Could not locate element by visible text: "${text}"`);
}

async function clickByVisibleText(page, text, options = {}) {
  const locator = await locatorByVisibleText(page, text);
  await locator.click({ timeout: 15000, ...options });
  await waitForUi(page);
}

async function expectVisibleByText(page, text, timeout = 15000) {
  const locator = await locatorByVisibleText(page, text);
  await locator.waitFor({ state: "visible", timeout });
  return locator;
}

async function capture(page, outputDir, fileName, fullPage = false) {
  const filePath = path.join(outputDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  report.evidence.screenshots.push(filePath);
}

function pushStep(label, validations) {
  const failed = validations.filter((v) => !v.pass);
  report.steps.push({
    label,
    status: failed.length === 0 ? "PASS" : "FAIL",
    validations,
  });
}

async function withStep(label, stepHandler) {
  const validations = [];
  const addValidation = (name, pass, details = "") => {
    validations.push({ name, pass, status: boolToStatus(pass), details });
  };

  try {
    await stepHandler(addValidation);
  } catch (error) {
    addValidation("Unhandled step error", false, error?.message ?? String(error));
  }

  pushStep(label, validations);
}

async function chooseGoogleAccountIfNeeded(page, accountEmail) {
  const accountText = page.getByText(accountEmail, { exact: false }).first();
  if ((await accountText.count()) > 0) {
    await accountText.click({ timeout: 10000 });
    await waitForUi(page);
    return true;
  }
  return false;
}

async function clickLegalLinkAndValidate({
  appPage,
  context,
  linkText,
  headingText,
  screenshotName,
  outputDir,
  finalUrlKey,
}) {
  const appUrlBefore = appPage.url();
  const existingPages = new Set(context.pages());
  const popupPromise = context.waitForEvent("page", { timeout: 4000 }).catch(() => null);

  const linkLocator = await locatorByVisibleText(appPage, linkText);
  await linkLocator.click({ timeout: 15000 });
  await waitForUi(appPage);

  let legalPage = await popupPromise;
  if (!legalPage) {
    const currentPages = context.pages();
    legalPage = currentPages.find((p) => !existingPages.has(p)) ?? appPage;
  }

  await waitForUi(legalPage, 15000);
  await expectVisibleByText(legalPage, headingText, 15000);

  const bodyText = await legalPage.locator("body").innerText();
  const hasLegalContent = bodyText.trim().length > 120;
  if (!hasLegalContent) {
    throw new Error(`"${headingText}" page did not expose enough legal content text.`);
  }

  await capture(legalPage, outputDir, screenshotName, true);
  report.evidence.finalUrls[finalUrlKey] = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== appUrlBefore) {
    await appPage.goBack({ waitUntil: "domcontentloaded", timeout: 15000 }).catch(() => null);
    await waitForUi(appPage);
  }
}

async function run() {
  const outputDir =
    process.env.SALEADS_OUTPUT_DIR ??
    path.join(process.cwd(), "artifacts", TEST_NAME, new Date().toISOString().replace(/[:.]/g, "-"));
  const baseUrl = process.env.SALEADS_BASE_URL ?? "";
  const wsEndpoint = process.env.SALEADS_WS_ENDPOINT ?? "";
  const accountEmail = process.env.SALEADS_GOOGLE_ACCOUNT ?? DEFAULT_GOOGLE_ACCOUNT;
  const headless = process.env.HEADLESS !== "false";

  await ensureDir(outputDir);

  const launchedBrowser = wsEndpoint ? null : await chromium.launch({ headless });
  const browser = wsEndpoint ? await chromium.connect(wsEndpoint) : launchedBrowser;
  const existingContext = browser.contexts()[0];
  const context = existingContext ?? (await browser.newContext({ viewport: { width: 1440, height: 900 } }));
  const page = context.pages()[0] ?? (await context.newPage());

  try {
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded", timeout: 30000 });
      await waitForUi(page, 15000);
    } else if (!wsEndpoint) {
      throw new Error(
        "Provide SALEADS_BASE_URL to open the login page, or SALEADS_WS_ENDPOINT to attach to an existing browser already on login."
      );
    } else {
      await waitForUi(page, 8000);
    }

    await withStep("Login", async (v) => {
      const signInCandidates = [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Google",
      ];
      let clicked = false;
      const googlePopupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);

      for (const candidate of signInCandidates) {
        try {
          const target = await locatorByVisibleText(page, candidate);
          await target.click({ timeout: 6000 });
          await waitForUi(page);
          clicked = true;
          break;
        } catch {
          // Try next candidate.
        }
      }
      v("Login button or Google sign-in is clickable", clicked, clicked ? "" : "No Google login button found.");

      const googlePopup = await googlePopupPromise;
      if (googlePopup) {
        await waitForUi(googlePopup, 15000);
        const selected = await chooseGoogleAccountIfNeeded(googlePopup, accountEmail);
        v(
          `Google account selector handled (${accountEmail})`,
          selected || googlePopup.url().includes("google."),
          selected ? "Account clicked successfully." : "Google account selector not explicitly shown."
        );
        await waitForUi(page, 15000);
      } else {
        v("Google popup handled (if present)", true, "No separate popup detected; flow likely completed inline.");
      }

      const sidebar = page.locator("aside, nav").first();
      const sidebarVisible = await sidebar.isVisible().catch(() => false);
      v("Main application interface appears", sidebarVisible, sidebarVisible ? "" : "Sidebar container not visible.");
      v("Left sidebar navigation is visible", sidebarVisible, sidebarVisible ? "" : "Left sidebar not detected.");

      await capture(page, outputDir, "01_dashboard_loaded.png", true);
    });

    await withStep("Mi Negocio menu", async (v) => {
      let negocioClicked = false;
      for (const label of ["Negocio", "Mi Negocio"]) {
        try {
          await clickByVisibleText(page, label);
          negocioClicked = true;
          break;
        } catch {
          // Keep trying labels.
        }
      }
      v("Sidebar section Negocio can be opened", negocioClicked, negocioClicked ? "" : "Negocio menu not found.");

      const agregar = await expectVisibleByText(page, "Agregar Negocio").catch(() => null);
      const administrar = await expectVisibleByText(page, "Administrar Negocios").catch(() => null);
      v("Submenu expanded", Boolean(agregar && administrar), "Expected both submenu entries to be visible.");
      v("'Agregar Negocio' is visible", Boolean(agregar));
      v("'Administrar Negocios' is visible", Boolean(administrar));

      await capture(page, outputDir, "02_mi_negocio_menu_expanded.png", true);
    });

    await withStep("Agregar Negocio modal", async (v) => {
      await clickByVisibleText(page, "Agregar Negocio");

      const modalTitle = await expectVisibleByText(page, "Crear Nuevo Negocio").catch(() => null);
      const nombreLabel = await expectVisibleByText(page, "Nombre del Negocio").catch(() => null);
      const quotaText = await expectVisibleByText(page, "Tienes 2 de 3 negocios").catch(() => null);
      const cancelBtn = await expectVisibleByText(page, "Cancelar").catch(() => null);
      const createBtn = await expectVisibleByText(page, "Crear Negocio").catch(() => null);

      v("Modal title 'Crear Nuevo Negocio' is visible", Boolean(modalTitle));
      v("Input field 'Nombre del Negocio' exists", Boolean(nombreLabel));
      v("Text 'Tienes 2 de 3 negocios' is visible", Boolean(quotaText));
      v("Buttons 'Cancelar' and 'Crear Negocio' are present", Boolean(cancelBtn && createBtn));

      const modalInput = page.getByLabel(textRegex("Nombre del Negocio")).first();
      if ((await modalInput.count()) > 0) {
        await modalInput.click({ timeout: 5000 });
        await modalInput.fill("Negocio Prueba Automatizacion");
      }

      await capture(page, outputDir, "03_agregar_negocio_modal.png", true);
      if (cancelBtn) {
        await cancelBtn.click({ timeout: 5000 });
        await waitForUi(page);
      }
    });

    await withStep("Administrar Negocios view", async (v) => {
      const isAdminVisible = await page.getByText(textRegex("Administrar Negocios")).first().isVisible().catch(() => false);
      if (!isAdminVisible) {
        for (const label of ["Mi Negocio", "Negocio"]) {
          try {
            await clickByVisibleText(page, label);
            break;
          } catch {
            // Keep trying labels.
          }
        }
      }

      await clickByVisibleText(page, "Administrar Negocios");

      const infoGeneral = await expectVisibleByText(page, "Informacion General").catch(async () =>
        expectVisibleByText(page, "Información General").catch(() => null)
      );
      const detallesCuenta = await expectVisibleByText(page, "Detalles de la Cuenta").catch(() => null);
      const tusNegocios = await expectVisibleByText(page, "Tus Negocios").catch(() => null);
      const seccionLegal = await expectVisibleByText(page, "Sección Legal").catch(async () =>
        expectVisibleByText(page, "Seccion Legal").catch(() => null)
      );

      v("Section 'Información General' exists", Boolean(infoGeneral));
      v("Section 'Detalles de la Cuenta' exists", Boolean(detallesCuenta));
      v("Section 'Tus Negocios' exists", Boolean(tusNegocios));
      v("Section 'Sección Legal' exists", Boolean(seccionLegal));

      await capture(page, outputDir, "04_administrar_negocios_page.png", true);
    });

    await withStep("Información General", async (v) => {
      const userNameVisible = (await page.locator("text=/[A-Z][a-z]+\\s+[A-Z][a-z]+/").first().count()) > 0;
      const userEmailVisible = (await page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first().count()) > 0;
      const businessPlan = await expectVisibleByText(page, "BUSINESS PLAN").catch(() => null);
      const cambiarPlan = await expectVisibleByText(page, "Cambiar Plan").catch(() => null);

      v("User name is visible", userNameVisible);
      v("User email is visible", userEmailVisible);
      v("Text 'BUSINESS PLAN' is visible", Boolean(businessPlan));
      v("Button 'Cambiar Plan' is visible", Boolean(cambiarPlan));
    });

    await withStep("Detalles de la Cuenta", async (v) => {
      const cuentaCreada = await expectVisibleByText(page, "Cuenta creada").catch(() => null);
      const estadoActivo = await expectVisibleByText(page, "Estado activo").catch(() => null);
      const idiomaSeleccionado = await expectVisibleByText(page, "Idioma seleccionado").catch(() => null);

      v("'Cuenta creada' is visible", Boolean(cuentaCreada));
      v("'Estado activo' is visible", Boolean(estadoActivo));
      v("'Idioma seleccionado' is visible", Boolean(idiomaSeleccionado));
    });

    await withStep("Tus Negocios", async (v) => {
      const businessesSection = await expectVisibleByText(page, "Tus Negocios").catch(() => null);
      const addBusinessButton = await expectVisibleByText(page, "Agregar Negocio").catch(() => null);
      const quotaText = await expectVisibleByText(page, "Tienes 2 de 3 negocios").catch(() => null);
      const businessCards = await page.locator("[class*='business'], [data-testid*='business'], li").count();

      v("Business list is visible", Boolean(businessesSection) && businessCards > 0, `Detected candidate list items: ${businessCards}`);
      v("Button 'Agregar Negocio' exists", Boolean(addBusinessButton));
      v("Text 'Tienes 2 de 3 negocios' is visible", Boolean(quotaText));
    });

    await withStep("Términos y Condiciones", async (v) => {
      try {
        await clickLegalLinkAndValidate({
          appPage: page,
          context,
          linkText: "Términos y Condiciones",
          headingText: "Términos y Condiciones",
          screenshotName: "05_terminos_y_condiciones.png",
          outputDir,
          finalUrlKey: "terminosYCondiciones",
        });
        v("Heading 'Términos y Condiciones' is visible", true);
        v("Legal content text is visible", true);
        v("Final URL captured", Boolean(report.evidence.finalUrls.terminosYCondiciones), report.evidence.finalUrls.terminosYCondiciones ?? "");
      } catch (error) {
        v("Heading 'Términos y Condiciones' is visible", false, error?.message ?? String(error));
        v("Legal content text is visible", false, "Could not validate legal page content.");
        v("Final URL captured", false, "URL capture failed.");
      }
    });

    await withStep("Política de Privacidad", async (v) => {
      try {
        await clickLegalLinkAndValidate({
          appPage: page,
          context,
          linkText: "Política de Privacidad",
          headingText: "Política de Privacidad",
          screenshotName: "06_politica_de_privacidad.png",
          outputDir,
          finalUrlKey: "politicaDePrivacidad",
        });
        v("Heading 'Política de Privacidad' is visible", true);
        v("Legal content text is visible", true);
        v("Final URL captured", Boolean(report.evidence.finalUrls.politicaDePrivacidad), report.evidence.finalUrls.politicaDePrivacidad ?? "");
      } catch (error) {
        v("Heading 'Política de Privacidad' is visible", false, error?.message ?? String(error));
        v("Legal content text is visible", false, "Could not validate legal page content.");
        v("Final URL captured", false, "URL capture failed.");
      }
    });
  } finally {
    report.finishedAt = new Date().toISOString();
    await fs.writeFile(path.join(outputDir, "report.json"), JSON.stringify(report, null, 2), "utf8");
    await browser.close();
  }

  const finalSummary = {
    Login: report.steps.find((s) => s.label === "Login")?.status ?? "FAIL",
    "Mi Negocio menu": report.steps.find((s) => s.label === "Mi Negocio menu")?.status ?? "FAIL",
    "Agregar Negocio modal": report.steps.find((s) => s.label === "Agregar Negocio modal")?.status ?? "FAIL",
    "Administrar Negocios view": report.steps.find((s) => s.label === "Administrar Negocios view")?.status ?? "FAIL",
    "Información General": report.steps.find((s) => s.label === "Información General")?.status ?? "FAIL",
    "Detalles de la Cuenta": report.steps.find((s) => s.label === "Detalles de la Cuenta")?.status ?? "FAIL",
    "Tus Negocios": report.steps.find((s) => s.label === "Tus Negocios")?.status ?? "FAIL",
    "Términos y Condiciones": report.steps.find((s) => s.label === "Términos y Condiciones")?.status ?? "FAIL",
    "Política de Privacidad": report.steps.find((s) => s.label === "Política de Privacidad")?.status ?? "FAIL",
  };

  console.log("\nFinal Report (PASS/FAIL by validation step):");
  console.table(finalSummary);
  console.log(`Evidence directory: ${outputDir}`);
  console.log(`Detailed JSON report: ${path.join(outputDir, "report.json")}`);

  const hasFailures = report.steps.some((step) => step.status === "FAIL");
  if (hasFailures) {
    console.error("One or more workflow validations failed. Inspect report.json and screenshots for details.");
    process.exitCode = 1;
  }
}

run().catch((error) => {
  console.error("\nFatal execution error:", error);
  process.exitCode = 1;
});
