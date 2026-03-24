import { expect, type BrowserContext, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL" | "SKIPPED";

type StepResult = {
  status: StepStatus;
  details: string;
  evidence?: Record<string, string>;
};

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
] as const;

async function waitForUi(page: Page): Promise<void> {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 8_000 }),
    page.waitForLoadState("domcontentloaded", { timeout: 8_000 })
  ]).catch(() => undefined);
  await page.waitForTimeout(700);
}

async function waitForVisible(locator: Locator, timeout = 10_000): Promise<Locator> {
  await locator.first().waitFor({ state: "visible", timeout });
  return locator.first();
}

async function firstVisible(candidates: Locator[], timeoutEach = 2_500): Promise<Locator | null> {
  for (const candidate of candidates) {
    try {
      await candidate.first().waitFor({ state: "visible", timeout: timeoutEach });
      return candidate.first();
    } catch {
      // try next candidate
    }
  }
  return null;
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await waitForVisible(locator);
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page: Page, screenshotsDir: string, fileName: string, fullPage = false): Promise<string> {
  const outputPath = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function ensureOnLoginOrNavigate(page: Page, baseUrl?: string): Promise<void> {
  const configuredLoginUrl =
    process.env.SALEADS_LOGIN_URL ??
    process.env.saleads_login_url ??
    process.env.SALEADS_BASE_URL ??
    process.env.BASE_URL ??
    baseUrl;

  if (configuredLoginUrl) {
    await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No login URL available. Set SALEADS_LOGIN_URL (or BASE_URL) to the current environment login page."
    );
  }
}

async function selectGoogleAccountIfPrompted(page: Page): Promise<void> {
  const accountLocator = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();

  if (await accountLocator.isVisible({ timeout: 6_000 }).catch(() => false)) {
    await accountLocator.click();
    await waitForUi(page);
  }
}

async function findHeadingOrText(page: Page, headingPattern: RegExp): Promise<Locator> {
  const headingCandidate = page.getByRole("heading", { name: headingPattern }).first();
  if (await headingCandidate.isVisible({ timeout: 4_000 }).catch(() => false)) {
    return headingCandidate;
  }

  const textCandidate = page.getByText(headingPattern).first();
  await waitForVisible(textCandidate, 12_000);
  return textCandidate;
}

function defaultResults(): Record<(typeof REPORT_FIELDS)[number], StepResult> {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "SKIPPED", details: "Not executed." }])
  ) as Record<(typeof REPORT_FIELDS)[number], StepResult>;
}

async function ensureLegalContentTextVisible(page: Page): Promise<void> {
  const paragraph = await firstVisible(
    [
      page.locator("article p"),
      page.locator("main p"),
      page.locator("p"),
      page.locator("li")
    ],
    5_000
  );

  if (!paragraph) {
    throw new Error("Could not confirm legal content text visibility.");
  }

  const paragraphText = (await paragraph.innerText().catch(() => "")).replace(/\s+/g, " ").trim();
  if (paragraphText.length < 20) {
    const bodyText = (await page.locator("body").innerText().catch(() => "")).replace(/\s+/g, " ").trim();
    if (bodyText.length < 120) {
      throw new Error("Legal content text appears too short or not loaded.");
    }
  }
}

async function openLegalLinkAndValidate(options: {
  page: Page;
  context: BrowserContext;
  linkPattern: RegExp;
  headingPattern: RegExp;
  screenshotsDir: string;
  screenshotName: string;
}): Promise<{ finalUrl: string; screenshotPath: string }> {
  const { page, context, linkPattern, headingPattern, screenshotsDir, screenshotName } = options;

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  const legalLink = await firstVisible(
    [
      page.getByRole("link", { name: linkPattern }),
      page.getByRole("button", { name: linkPattern }),
      page.getByText(linkPattern)
    ],
    4_000
  );

  if (!legalLink) {
    throw new Error(`Could not find legal link/button matching ${String(linkPattern)}.`);
  }

  await clickAndWait(legalLink, page);

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
    await waitForVisible(
      popup.getByRole("heading", { name: headingPattern }).first().or(popup.getByText(headingPattern).first()),
      20_000
    );
    await ensureLegalContentTextVisible(popup);
    const screenshotPath = await captureCheckpoint(popup, screenshotsDir, screenshotName, true);
    const finalUrl = popup.url();
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return { finalUrl, screenshotPath };
  }

  await waitForVisible(
    page.getByRole("heading", { name: headingPattern }).first().or(page.getByText(headingPattern).first()),
    20_000
  );
  await ensureLegalContentTextVisible(page);
  const screenshotPath = await captureCheckpoint(page, screenshotsDir, screenshotName, true);
  const finalUrl = page.url();
  await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
  await waitForUi(page);
  return { finalUrl, screenshotPath };
}

test("SaleADS - Mi Negocio full workflow", async ({ page, context }) => {
  const artifactsDir = path.join(process.cwd(), "artifacts");
  const screenshotsDir = path.join(artifactsDir, "screenshots");
  const reportPath = path.join(artifactsDir, "saleads-mi-negocio-final-report.json");
  await fs.mkdir(screenshotsDir, { recursive: true });

  const results = defaultResults();
  const metadata: Record<string, string> = {};

  let loginOk = false;
  let menuOk = false;
  let adminViewOk = false;

  // Step 1 - Login with Google.
  try {
    await ensureOnLoginOrNavigate(page);

    const loginButton = await firstVisible(
      [
        page.getByRole("button", { name: /Sign in with Google|Iniciar sesi[o\u00f3]n con Google|Continuar con Google/i }),
        page.getByRole("link", { name: /Sign in with Google|Iniciar sesi[o\u00f3]n con Google|Continuar con Google/i }),
        page.getByText(/Sign in with Google|Iniciar sesi[o\u00f3]n con Google|Continuar con Google/i)
      ],
      6_000
    );

    if (!loginButton) {
      throw new Error("Could not find Google login control.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
      await selectGoogleAccountIfPrompted(popup);
      await Promise.race([
        popup.waitForEvent("close", { timeout: 70_000 }),
        page.waitForLoadState("domcontentloaded", { timeout: 70_000 })
      ]).catch(() => undefined);
    } else {
      await selectGoogleAccountIfPrompted(page);
    }

    await waitForUi(page);

    // Validate dashboard/main app + left sidebar.
    const mainApp = await firstVisible(
      [
        page.locator("main"),
        page.locator("[role='main']"),
        page.getByText(/Dashboard|Negocio|Mi Negocio/i)
      ],
      8_000
    );
    if (!mainApp) {
      throw new Error("Main application interface was not detected after login.");
    }

    const sidebar = await firstVisible(
      [
        page.locator("aside"),
        page.locator("nav"),
        page.getByText(/^Negocio$/i)
      ],
      8_000
    );
    if (!sidebar) {
      throw new Error("Left sidebar navigation is not visible.");
    }

    const dashboardScreenshot = await captureCheckpoint(page, screenshotsDir, "01-dashboard-loaded.png");
    results["Login"] = {
      status: "PASS",
      details: "Main app interface and left sidebar are visible after Google login.",
      evidence: { screenshot: dashboardScreenshot }
    };
    loginOk = true;
  } catch (error) {
    results["Login"] = {
      status: "FAIL",
      details: `Login validation failed: ${error instanceof Error ? error.message : String(error)}`
    };
  }

  // Step 2 - Open Mi Negocio menu.
  try {
    if (!loginOk) {
      throw new Error("Skipped because Login step failed.");
    }

    const negocioSection = await firstVisible(
      [
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i)
      ],
      8_000
    );
    if (!negocioSection) {
      throw new Error("Could not find sidebar section 'Negocio'.");
    }
    await clickAndWait(negocioSection, page);

    const miNegocioOption = await firstVisible(
      [
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      8_000
    );
    if (!miNegocioOption) {
      throw new Error("Could not find option 'Mi Negocio'.");
    }
    await clickAndWait(miNegocioOption, page);

    await waitForVisible(page.getByText(/^Agregar Negocio$/i), 12_000);
    await waitForVisible(page.getByText(/^Administrar Negocios$/i), 12_000);
    const menuScreenshot = await captureCheckpoint(page, screenshotsDir, "02-mi-negocio-expanded-menu.png");

    results["Mi Negocio menu"] = {
      status: "PASS",
      details: "Mi Negocio submenu expanded and both submenu options are visible.",
      evidence: { screenshot: menuScreenshot }
    };
    menuOk = true;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    results["Mi Negocio menu"] = {
      status: message.includes("Skipped because") ? "SKIPPED" : "FAIL",
      details: message
    };
  }

  // Step 3 - Validate Agregar Negocio modal.
  try {
    if (!menuOk) {
      throw new Error("Skipped because Mi Negocio menu step failed.");
    }

    const agregarNegocioMenu = await firstVisible(
      [
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      8_000
    );
    if (!agregarNegocioMenu) {
      throw new Error("Could not find 'Agregar Negocio' menu option.");
    }
    await clickAndWait(agregarNegocioMenu, page);

    await waitForVisible(page.getByText(/^Crear Nuevo Negocio$/i), 12_000);
    await waitForVisible(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i)), 12_000);
    await waitForVisible(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i), 12_000);
    await waitForVisible(page.getByRole("button", { name: /^Cancelar$/i }), 12_000);
    await waitForVisible(page.getByRole("button", { name: /^Crear Negocio$/i }), 12_000);

    const modalScreenshot = await captureCheckpoint(page, screenshotsDir, "03-agregar-negocio-modal.png");

    const businessNameField = await firstVisible(
      [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)],
      4_000
    );
    if (businessNameField) {
      await businessNameField.click();
      await waitForUi(page);
      await businessNameField.fill("Negocio Prueba Automatizacion");
      await waitForUi(page);
    }

    const cancelButton = await firstVisible([page.getByRole("button", { name: /^Cancelar$/i })], 5_000);
    if (cancelButton) {
      await clickAndWait(cancelButton, page);
    }

    results["Agregar Negocio modal"] = {
      status: "PASS",
      details:
        "Modal validated with title, business name input, business quota text, and action buttons. Optional input/cancel actions executed.",
      evidence: { screenshot: modalScreenshot }
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    results["Agregar Negocio modal"] = {
      status: message.includes("Skipped because") ? "SKIPPED" : "FAIL",
      details: message
    };
  }

  // Step 4 - Open Administrar Negocios.
  try {
    if (!menuOk) {
      throw new Error("Skipped because Mi Negocio menu step failed.");
    }

    const adminOptionVisible = await page.getByText(/^Administrar Negocios$/i).isVisible().catch(() => false);
    if (!adminOptionVisible) {
      const miNegocioOption = await firstVisible(
        [
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ],
        8_000
      );
      if (!miNegocioOption) {
        throw new Error("Could not find 'Mi Negocio' to re-expand menu.");
      }
      await clickAndWait(miNegocioOption, page);
    }

    const administrarNegociosOption = await firstVisible(
      [
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ],
      8_000
    );
    if (!administrarNegociosOption) {
      throw new Error("Could not find 'Administrar Negocios' option.");
    }
    await clickAndWait(administrarNegociosOption, page);

    await findHeadingOrText(page, /^Informaci[o\u00f3]n General$/i);
    await findHeadingOrText(page, /^Detalles de la Cuenta$/i);
    await findHeadingOrText(page, /^Tus Negocios$/i);
    await findHeadingOrText(page, /^Secci[o\u00f3]n Legal$/i);

    const accountScreenshot = await captureCheckpoint(page, screenshotsDir, "04-administrar-negocios-page.png", true);
    results["Administrar Negocios view"] = {
      status: "PASS",
      details: "Account view loaded with required sections.",
      evidence: { screenshot: accountScreenshot }
    };
    adminViewOk = true;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    results["Administrar Negocios view"] = {
      status: message.includes("Skipped because") ? "SKIPPED" : "FAIL",
      details: message
    };
  }

  // Step 5 - Validate Informacion General.
  try {
    if (!adminViewOk) {
      throw new Error("Skipped because Administrar Negocios view failed.");
    }

    await findHeadingOrText(page, /^Informaci[o\u00f3]n General$/i);
    const emailLocator = page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first();
    await waitForVisible(emailLocator, 12_000);

    const nearbyText = await emailLocator
      .locator("xpath=ancestor::*[self::div or self::section][1]")
      .innerText()
      .catch(() => "");
    const nameCandidates = nearbyText
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => Boolean(line))
      .filter((line) => !line.includes("@"))
      .filter((line) => !/Informaci[o\u00f3]n General|BUSINESS PLAN|Cambiar Plan/i.test(line))
      .filter((line) => /[A-Za-z]/.test(line));

    if (nameCandidates.length === 0) {
      throw new Error("Could not confirm user name text near the user email.");
    }

    await waitForVisible(page.getByText(/^BUSINESS PLAN$/i), 12_000);
    await waitForVisible(page.getByRole("button", { name: /^Cambiar Plan$/i }), 12_000);

    results["Información General"] = {
      status: "PASS",
      details: "User name, user email, BUSINESS PLAN label, and Cambiar Plan button are visible."
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    results["Información General"] = {
      status: message.includes("Skipped because") ? "SKIPPED" : "FAIL",
      details: message
    };
  }

  // Step 6 - Validate Detalles de la Cuenta.
  try {
    if (!adminViewOk) {
      throw new Error("Skipped because Administrar Negocios view failed.");
    }

    await findHeadingOrText(page, /^Detalles de la Cuenta$/i);
    await waitForVisible(page.getByText(/Cuenta creada/i), 12_000);
    await waitForVisible(page.getByText(/Estado activo/i), 12_000);
    await waitForVisible(page.getByText(/Idioma seleccionado/i), 12_000);

    results["Detalles de la Cuenta"] = {
      status: "PASS",
      details: "Account details labels are visible: Cuenta creada, Estado activo, Idioma seleccionado."
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    results["Detalles de la Cuenta"] = {
      status: message.includes("Skipped because") ? "SKIPPED" : "FAIL",
      details: message
    };
  }

  // Step 7 - Validate Tus Negocios.
  try {
    if (!adminViewOk) {
      throw new Error("Skipped because Administrar Negocios view failed.");
    }

    const tusNegociosHeading = await findHeadingOrText(page, /^Tus Negocios$/i);
    await waitForVisible(tusNegociosHeading, 12_000);
    await waitForVisible(page.getByRole("button", { name: /^Agregar Negocio$/i }).or(page.getByText(/^Agregar Negocio$/i)), 12_000);
    await waitForVisible(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i), 12_000);

    const businessListVisible = await firstVisible(
      [
        page.locator("table tbody tr"),
        page.locator("ul li"),
        page.locator("[data-testid*='business']"),
        page.locator("[class*='business']")
      ],
      2_500
    );
    if (!businessListVisible) {
      throw new Error("Could not confirm visible business list rows/cards in 'Tus Negocios'.");
    }

    results["Tus Negocios"] = {
      status: "PASS",
      details: "Business list, Agregar Negocio control, and business quota text are visible."
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    results["Tus Negocios"] = {
      status: message.includes("Skipped because") ? "SKIPPED" : "FAIL",
      details: message
    };
  }

  // Step 8 - Validate Terminos y Condiciones.
  try {
    if (!adminViewOk) {
      throw new Error("Skipped because Administrar Negocios view failed.");
    }

    const legalResult = await openLegalLinkAndValidate({
      page,
      context,
      linkPattern: /^T[e\u00e9]rminos y Condiciones$/i,
      headingPattern: /^T[e\u00e9]rminos y Condiciones$/i,
      screenshotsDir,
      screenshotName: "08-terminos-y-condiciones.png"
    });
    metadata.terminosYCondicionesUrl = legalResult.finalUrl;
    results["Términos y Condiciones"] = {
      status: "PASS",
      details: "Terms page validated with heading and legal content visible.",
      evidence: {
        screenshot: legalResult.screenshotPath,
        finalUrl: legalResult.finalUrl
      }
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    results["Términos y Condiciones"] = {
      status: message.includes("Skipped because") ? "SKIPPED" : "FAIL",
      details: message
    };
  }

  // Step 9 - Validate Politica de Privacidad.
  try {
    if (!adminViewOk) {
      throw new Error("Skipped because Administrar Negocios view failed.");
    }

    const legalResult = await openLegalLinkAndValidate({
      page,
      context,
      linkPattern: /^Pol[i\u00ed]tica de Privacidad$/i,
      headingPattern: /^Pol[i\u00ed]tica de Privacidad$/i,
      screenshotsDir,
      screenshotName: "09-politica-de-privacidad.png"
    });
    metadata.politicaPrivacidadUrl = legalResult.finalUrl;
    results["Política de Privacidad"] = {
      status: "PASS",
      details: "Privacy page validated with heading and legal content visible.",
      evidence: {
        screenshot: legalResult.screenshotPath,
        finalUrl: legalResult.finalUrl
      }
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    results["Política de Privacidad"] = {
      status: message.includes("Skipped because") ? "SKIPPED" : "FAIL",
      details: message
    };
  }

  // Step 10 - Final report.
  const report = {
    generatedAt: new Date().toISOString(),
    testName: "saleads_mi_negocio_full_test",
    environmentUrlAtStart: page.url(),
    metadata,
    results
  };

  await fs.mkdir(artifactsDir, { recursive: true });
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  // Log final summary to test output.
  for (const field of REPORT_FIELDS) {
    const entry = results[field];
    console.log(`${field}: ${entry.status} - ${entry.details}`);
  }

  const failures = REPORT_FIELDS.filter((field) => results[field].status === "FAIL");
  expect(failures, `Failed validation steps: ${failures.join(", ")}`).toEqual([]);
});
