import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { chromium } from "playwright";

const DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function timestamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) {
      continue;
    }
    const [key, valueFromToken] = token.split("=");
    if (valueFromToken !== undefined) {
      args[key] = valueFromToken;
      continue;
    }
    const next = argv[i + 1];
    if (next && !next.startsWith("--")) {
      args[key] = next;
      i += 1;
    } else {
      args[key] = "true";
    }
  }
  return args;
}

function createReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed." }]),
  );
}

function mark(report, field, passed, details) {
  report[field] = {
    status: passed ? "PASS" : "FAIL",
    details,
  };
}

async function ensureDir(directory) {
  await fs.mkdir(directory, { recursive: true });
}

async function writeJson(filePath, data) {
  await fs.writeFile(filePath, `${JSON.stringify(data, null, 2)}\n`, "utf-8");
}

async function isVisible(locator) {
  try {
    return await locator.first().isVisible();
  } catch {
    return false;
  }
}

async function firstVisible(locators, timeoutMs = 15000, pollMs = 300) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    for (const locator of locators) {
      if (await isVisible(locator)) {
        return locator.first();
      }
    }
    await sleep(pollMs);
  }
  return null;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 3000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function takeScreenshot(page, artifactsDir, fileName, fullPage = false) {
  const shotPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: shotPath, fullPage });
  return shotPath;
}

async function clickGoogleButton(page, context, googleButton) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await googleButton.click();
  await waitForUi(page);
  return popupPromise;
}

async function chooseGoogleAccountIfPresent(possibleAuthPage, googleEmail) {
  const account = await firstVisible(
    [
      possibleAuthPage.getByText(googleEmail, { exact: true }),
      possibleAuthPage.getByRole("button", { name: googleEmail }),
      possibleAuthPage.getByRole("link", { name: googleEmail }),
      possibleAuthPage.locator(`text="${googleEmail}"`),
    ],
    10000,
  );

  if (account) {
    await account.click();
    await waitForUi(possibleAuthPage);
    return true;
  }
  return false;
}

async function waitForMainApplication(page) {
  const appReady = await firstVisible(
    [
      page.getByText(/Mi Negocio/i),
      page.getByText(/^Negocio$/i),
      page.locator("aside"),
      page.getByRole("navigation"),
    ],
    60000,
    500,
  );
  if (!appReady) {
    throw new Error("Main application shell did not appear after login.");
  }
}

async function verifySectionText(page, textRegex, label) {
  const matched = await firstVisible([page.getByText(textRegex)], 10000);
  if (!matched) {
    throw new Error(`Expected text not visible: ${label}`);
  }
}

async function openAndValidateLegal(page, context, artifactsDir, label, expectedHeading, screenshotName) {
  const link = await firstVisible(
    [
      page.getByRole("link", { name: new RegExp(label, "i") }),
      page.getByRole("button", { name: new RegExp(label, "i") }),
      page.getByText(new RegExp(label, "i")),
    ],
    10000,
  );
  if (!link) {
    throw new Error(`Could not find legal link/button: ${label}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await link.click();

  let targetPage = await popupPromise;
  if (!targetPage) {
    targetPage = page;
    await waitForUi(targetPage);
  } else {
    await waitForUi(targetPage);
  }

  const heading = await firstVisible([targetPage.getByText(new RegExp(expectedHeading, "i"))], 15000);
  if (!heading) {
    throw new Error(`Heading "${expectedHeading}" was not found.`);
  }

  const legalContent = await targetPage.locator("body").innerText();
  if (legalContent.trim().length < 120) {
    throw new Error(`${label} content appears too short.`);
  }

  const screenshot = await takeScreenshot(targetPage, artifactsDir, screenshotName, true);
  const finalUrl = targetPage.url();

  if (targetPage !== page) {
    await targetPage.close().catch(() => {});
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 10000 }).catch(() => {});
    await waitForUi(page);
  }

  return { screenshot, finalUrl };
}

async function run() {
  const args = parseArgs(process.argv.slice(2));
  const configuredLoginUrl = args["--url"] ?? process.env.SALEADS_LOGIN_URL ?? "";
  const googleEmail = process.env.SALEADS_GOOGLE_EMAIL ?? DEFAULT_GOOGLE_EMAIL;
  const headless = (process.env.HEADLESS ?? "true").toLowerCase() !== "false";
  const userDataDir =
    process.env.PW_USER_DATA_DIR ?? path.resolve(process.cwd(), ".pw-user-data");

  const runId = timestamp();
  const artifactsDir = path.resolve(process.cwd(), "artifacts", runId);
  await ensureDir(artifactsDir);

  const report = createReport();
  const screenshots = {};
  const urls = {};
  let context;
  let page;

  try {
    context = await chromium.launchPersistentContext(userDataDir, {
      headless,
      viewport: { width: 1440, height: 900 },
    });
    page = context.pages()[0] ?? (await context.newPage());

    const currentUrl = page.url();
    const loginUrl =
      configuredLoginUrl ||
      (currentUrl && !/^about:(blank|newtab)$/.test(currentUrl) ? currentUrl : "");
    if (!loginUrl) {
      throw new Error(
        "Missing login URL. Set SALEADS_LOGIN_URL or pass --url=<login_page_url>, or provide a persistent browser profile already on SaleADS login page.",
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    await waitForUi(page);

    // Step 1: Login with Google
    try {
      const googleButton = await firstVisible(
        [
          page.getByRole("button", {
            name: /sign in with google|continuar con google|iniciar sesión con google|google/i,
          }),
          page.getByText(/sign in with google|continuar con google|iniciar sesión con google/i),
        ],
        20000,
      );
      if (!googleButton) {
        throw new Error("Google login button was not found.");
      }

      const popup = await clickGoogleButton(page, context, googleButton);

      if (popup) {
        await chooseGoogleAccountIfPresent(popup, googleEmail);
      } else {
        await chooseGoogleAccountIfPresent(page, googleEmail);
      }

      await waitForMainApplication(page);
      screenshots.login = await takeScreenshot(page, artifactsDir, "01-dashboard-loaded.png", true);
      mark(report, "Login", true, "Dashboard and left sidebar are visible.");
    } catch (error) {
      mark(report, "Login", false, String(error.message || error));
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      const negocio = await firstVisible(
        [
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i),
          page.getByText(/Negocio/i),
        ],
        15000,
      );
      if (!negocio) {
        throw new Error('Menu option "Negocio" was not found.');
      }
      await clickAndWait(page, negocio);

      const miNegocio = await firstVisible([page.getByText(/Mi Negocio/i)], 10000);
      if (!miNegocio) {
        throw new Error('"Mi Negocio" option was not found.');
      }
      await clickAndWait(page, miNegocio);

      await verifySectionText(page, /Agregar Negocio/i, "Agregar Negocio");
      await verifySectionText(page, /Administrar Negocios/i, "Administrar Negocios");
      screenshots.miNegocioMenu = await takeScreenshot(
        page,
        artifactsDir,
        "02-mi-negocio-menu-expanded.png",
      );
      mark(report, "Mi Negocio menu", true, "Menu expanded and both submenu options are visible.");
    } catch (error) {
      mark(report, "Mi Negocio menu", false, String(error.message || error));
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const agregarMenuOption = await firstVisible([page.getByText(/Agregar Negocio/i)], 10000);
      if (!agregarMenuOption) {
        throw new Error('"Agregar Negocio" menu option was not found.');
      }
      await clickAndWait(page, agregarMenuOption);

      await verifySectionText(page, /Crear Nuevo Negocio/i, "Crear Nuevo Negocio");
      await verifySectionText(page, /Nombre del Negocio/i, "Nombre del Negocio");
      await verifySectionText(page, /Tienes 2 de 3 negocios/i, "Tienes 2 de 3 negocios");
      await verifySectionText(page, /Cancelar/i, "Cancelar");
      await verifySectionText(page, /Crear Negocio/i, "Crear Negocio");

      const businessNameInput = await firstVisible(
        [
          page.getByLabel(/Nombre del Negocio/i),
          page.getByPlaceholder(/Nombre del Negocio/i),
          page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
          page.locator("input"),
        ],
        10000,
      );
      if (businessNameInput) {
        await businessNameInput.fill("Negocio Prueba Automatización");
      }

      screenshots.agregarModal = await takeScreenshot(
        page,
        artifactsDir,
        "03-agregar-negocio-modal.png",
      );

      const cancelButton = await firstVisible([page.getByRole("button", { name: /Cancelar/i })], 5000);
      if (cancelButton) {
        await clickAndWait(page, cancelButton);
      }

      mark(report, "Agregar Negocio modal", true, "Modal content and controls validated successfully.");
    } catch (error) {
      mark(report, "Agregar Negocio modal", false, String(error.message || error));
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      let adminOption = await firstVisible([page.getByText(/Administrar Negocios/i)], 5000);
      if (!adminOption) {
        const miNegocioAgain = await firstVisible([page.getByText(/Mi Negocio/i)], 5000);
        if (!miNegocioAgain) {
          throw new Error('Could not reopen "Mi Negocio" to access "Administrar Negocios".');
        }
        await clickAndWait(page, miNegocioAgain);
        adminOption = await firstVisible([page.getByText(/Administrar Negocios/i)], 5000);
      }
      if (!adminOption) {
        throw new Error('"Administrar Negocios" option was not found.');
      }
      await clickAndWait(page, adminOption);

      await verifySectionText(page, /Información General/i, "Información General");
      await verifySectionText(page, /Detalles de la Cuenta/i, "Detalles de la Cuenta");
      await verifySectionText(page, /Tus Negocios/i, "Tus Negocios");
      await verifySectionText(page, /Sección Legal/i, "Sección Legal");

      screenshots.administrarView = await takeScreenshot(
        page,
        artifactsDir,
        "04-administrar-negocios-view.png",
        true,
      );
      mark(report, "Administrar Negocios view", true, "Account page loaded with all required sections.");
    } catch (error) {
      mark(report, "Administrar Negocios view", false, String(error.message || error));
      throw error;
    }

    // Step 5: Validate Información General
    try {
      const infoContainer = await firstVisible(
        [page.locator("section,div").filter({ hasText: /Información General/i })],
        10000,
      );
      if (!infoContainer) {
        throw new Error('"Información General" section is not visible.');
      }

      const emailMatch = await firstVisible(
        [page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)],
        10000,
      );
      if (!emailMatch) {
        throw new Error("No user email was found in Información General.");
      }

      const infoText = await infoContainer.innerText();
      const possibleName = infoText
        .split("\n")
        .map((line) => line.trim())
        .find(
          (line) =>
            line.length >= 3 &&
            !/@/.test(line) &&
            !/información general|business plan|cambiar plan|plan/i.test(line),
        );
      if (!possibleName) {
        throw new Error("Could not identify a visible user name.");
      }

      await verifySectionText(page, /BUSINESS PLAN/i, "BUSINESS PLAN");
      await verifySectionText(page, /Cambiar Plan/i, "Cambiar Plan");
      mark(report, "Información General", true, "Name, email, plan text, and button are visible.");
    } catch (error) {
      mark(report, "Información General", false, String(error.message || error));
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await verifySectionText(page, /Cuenta creada/i, "Cuenta creada");
      await verifySectionText(page, /Estado activo/i, "Estado activo");
      await verifySectionText(page, /Idioma seleccionado/i, "Idioma seleccionado");
      mark(report, "Detalles de la Cuenta", true, "All required account detail labels are visible.");
    } catch (error) {
      mark(report, "Detalles de la Cuenta", false, String(error.message || error));
    }

    // Step 7: Validate Tus Negocios
    try {
      await verifySectionText(page, /Tus Negocios/i, "Tus Negocios");
      await verifySectionText(page, /Agregar Negocio/i, "Agregar Negocio button");
      await verifySectionText(page, /Tienes 2 de 3 negocios/i, "Tienes 2 de 3 negocios");

      const businessesSection = await firstVisible(
        [page.locator("section,div").filter({ hasText: /Tus Negocios/i })],
        10000,
      );
      if (!businessesSection) {
        throw new Error('"Tus Negocios" section is not visible.');
      }

      const rowCount = await businessesSection.locator("li, tr, .card, .business").count();
      if (rowCount < 1) {
        throw new Error("Business list items were not detected.");
      }

      mark(report, "Tus Negocios", true, "Business list, button, and quota text are visible.");
    } catch (error) {
      mark(report, "Tus Negocios", false, String(error.message || error));
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const termsResult = await openAndValidateLegal(
        page,
        context,
        artifactsDir,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "05-terminos-y-condiciones.png",
      );
      screenshots.terminos = termsResult.screenshot;
      urls.terminos = termsResult.finalUrl;
      mark(
        report,
        "Términos y Condiciones",
        true,
        `Heading/content validated. URL: ${termsResult.finalUrl}`,
      );
    } catch (error) {
      mark(report, "Términos y Condiciones", false, String(error.message || error));
    }

    // Step 9: Validate Política de Privacidad
    try {
      const privacyResult = await openAndValidateLegal(
        page,
        context,
        artifactsDir,
        "Política de Privacidad",
        "Política de Privacidad",
        "06-politica-de-privacidad.png",
      );
      screenshots.privacidad = privacyResult.screenshot;
      urls.privacidad = privacyResult.finalUrl;
      mark(
        report,
        "Política de Privacidad",
        true,
        `Heading/content validated. URL: ${privacyResult.finalUrl}`,
      );
    } catch (error) {
      mark(report, "Política de Privacidad", false, String(error.message || error));
    }
  } catch (error) {
    const unhandledMessage = String(error.message || error);
    if (page) {
      try {
        screenshots.failure = await takeScreenshot(page, artifactsDir, "00-failure-state.png", true);
      } catch {
        // no-op
      }
    }
    for (const field of REPORT_FIELDS) {
      if (report[field].details === "Not executed.") {
        mark(report, field, false, `Execution interrupted: ${unhandledMessage}`);
      }
    }
  } finally {
    if (context) {
      await context.close().catch(() => {});
    }
  }

  const finalReport = {
    test_name: "saleads_mi_negocio_full_test",
    executed_at: new Date().toISOString(),
    artifacts_dir: artifactsDir,
    screenshots,
    urls,
    report,
  };

  const reportPath = path.join(artifactsDir, "final-report.json");
  await writeJson(reportPath, finalReport);

  const hasFailures = Object.values(report).some((entry) => entry.status === "FAIL");
  console.log(JSON.stringify(finalReport, null, 2));
  if (hasFailures) {
    process.exitCode = 1;
  }
}

await run();
