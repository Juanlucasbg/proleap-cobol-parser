import {
  expect,
  test,
  type Locator,
  type Page,
  type TestInfo,
} from "@playwright/test";
import {
  checkpointScreenshot,
  type StepResult,
  writeJsonEvidence,
} from "../utils/reporting";

type WorkflowField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

const defaultResults: Record<WorkflowField, StepResult> = {
  Login: { name: "Login", status: "FAIL", details: "Not executed." },
  "Mi Negocio menu": {
    name: "Mi Negocio menu",
    status: "FAIL",
    details: "Not executed.",
  },
  "Agregar Negocio modal": {
    name: "Agregar Negocio modal",
    status: "FAIL",
    details: "Not executed.",
  },
  "Administrar Negocios view": {
    name: "Administrar Negocios view",
    status: "FAIL",
    details: "Not executed.",
  },
  "Información General": {
    name: "Información General",
    status: "FAIL",
    details: "Not executed.",
  },
  "Detalles de la Cuenta": {
    name: "Detalles de la Cuenta",
    status: "FAIL",
    details: "Not executed.",
  },
  "Tus Negocios": {
    name: "Tus Negocios",
    status: "FAIL",
    details: "Not executed.",
  },
  "Términos y Condiciones": {
    name: "Términos y Condiciones",
    status: "FAIL",
    details: "Not executed.",
  },
  "Política de Privacidad": {
    name: "Política de Privacidad",
    status: "FAIL",
    details: "Not executed.",
  },
};

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
}

function byText(page: Page, text: string): Locator {
  return page.getByText(text, { exact: false });
}

async function clickVisible(locator: Locator): Promise<void> {
  await expect(locator.first()).toBeVisible();
  await locator.first().click();
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await clickVisible(locator);
  await waitForUi(page);
}

async function ensureSidebar(page: Page): Promise<Locator> {
  const sidebarCandidates = [
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator("[class*='sidebar'], [id*='sidebar']"),
  ];

  for (const candidate of sidebarCandidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      return candidate.first();
    }
  }

  throw new Error("Sidebar navigation was not found.");
}

async function findSidebarAction(page: Page, text: string): Promise<Locator> {
  const sidebar = await ensureSidebar(page);
  const inSidebar = sidebar.getByText(text, { exact: false });
  if (await inSidebar.first().isVisible().catch(() => false)) {
    return inSidebar.first();
  }

  return byText(page, text).first();
}

async function maybeSelectGoogleAccount(page: Page): Promise<void> {
  const accountText = "juanlucasbarbiergarzon@gmail.com";
  const accountLocator = page.getByText(accountText, { exact: true });
  if (await accountLocator.isVisible().catch(() => false)) {
    await accountLocator.click();
    await waitForUi(page);
  }
}

async function validateLegalLink(
  page: Page,
  testInfo: TestInfo,
  legalLinkText: "Términos y Condiciones" | "Política de Privacidad",
  expectedHeading: string
): Promise<{ finalUrl: string; screenshotPath: string }> {
  const appUrlBeforeClick = page.url();
  const link = byText(page, legalLinkText);
  await expect(link.first()).toBeVisible();

  const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
  await link.first().click();

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  await waitForUi(targetPage);
  await expect(targetPage.getByText(expectedHeading, { exact: false })).toBeVisible();

  // Validate legal content exists by ensuring body has substantial text.
  const bodyText = await targetPage.locator("body").innerText();
  expect(bodyText.trim().length).toBeGreaterThan(100);

  const screenshotPath = await checkpointScreenshot(
    targetPage,
    testInfo,
    `${legalLinkText} page`,
    { fullPage: true }
  );

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  return { finalUrl, screenshotPath };
}

function findNameLikeLine(sectionText: string): string | undefined {
  const blocked = new Set([
    "Información General",
    "BUSINESS PLAN",
    "Cambiar Plan",
    "Cuenta creada",
    "Estado activo",
    "Idioma seleccionado",
  ]);

  return sectionText
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length >= 3)
    .find((line) => {
      if (blocked.has(line)) {
        return false;
      }
      if (line.includes("@")) {
        return false;
      }
      if (!/[A-Za-zÁÉÍÓÚáéíóúÑñ]/.test(line)) {
        return false;
      }
      return true;
    });
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const results: Record<WorkflowField, StepResult> = { ...defaultResults };
  const legalUrls: Record<string, string> = {};
  const screenshots: Record<string, string> = {};
  const executionErrors: string[] = [];

  const providedUrl = process.env.SALEADS_BASE_URL;
  if (providedUrl) {
    await page.goto(providedUrl, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No SALEADS_BASE_URL provided and browser is on about:blank. " +
        "Set SALEADS_BASE_URL or pre-open SaleADS login page before running."
    );
  }
  await waitForUi(page);

  // Step 1: Login with Google
  try {
    const googleSignInButton = page
      .getByRole("button", { name: /google|sign in|iniciar sesión/i })
      .or(byText(page, "Sign in with Google"))
      .or(byText(page, "Iniciar sesión con Google"));

    await clickAndWait(googleSignInButton, page);
    await maybeSelectGoogleAccount(page);

    await ensureSidebar(page);

    screenshots.dashboardLoaded = await checkpointScreenshot(
      page,
      testInfo,
      "Dashboard loaded"
    );

    results.Login = {
      name: "Login",
      status: "PASS",
      details: "Main interface and left sidebar are visible after Google login.",
    };
  } catch (error) {
    results.Login = {
      name: "Login",
      status: "FAIL",
      details: `Login validation failed: ${(error as Error).message}`,
    };
    executionErrors.push(results.Login.details);
  }

  // Step 2: Open Mi Negocio menu
  try {
    const negocio = await findSidebarAction(page, "Negocio");
    await clickAndWait(negocio, page);
    const miNegocio = await findSidebarAction(page, "Mi Negocio");
    await clickAndWait(miNegocio, page);

    await expect(byText(page, "Agregar Negocio")).toBeVisible();
    await expect(byText(page, "Administrar Negocios")).toBeVisible();

    screenshots.expandedMenu = await checkpointScreenshot(
      page,
      testInfo,
      "Mi Negocio expanded menu"
    );

    results["Mi Negocio menu"] = {
      name: "Mi Negocio menu",
      status: "PASS",
      details: "Submenu expanded and required options are visible.",
    };
  } catch (error) {
    results["Mi Negocio menu"] = {
      name: "Mi Negocio menu",
      status: "FAIL",
      details: `Mi Negocio menu validation failed: ${(error as Error).message}`,
    };
    executionErrors.push(results["Mi Negocio menu"].details);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await clickAndWait(byText(page, "Agregar Negocio"), page);

    await expect(byText(page, "Crear Nuevo Negocio")).toBeVisible();
    const nombreInput = page
      .getByLabel("Nombre del Negocio")
      .or(page.getByPlaceholder("Nombre del Negocio"))
      .or(page.locator("input[name*='negocio'], input[id*='negocio']").first());
    await expect(nombreInput.first()).toBeVisible();
    await expect(byText(page, "Tienes 2 de 3 negocios")).toBeVisible();
    await expect(byText(page, "Cancelar")).toBeVisible();
    await expect(byText(page, "Crear Negocio")).toBeVisible();

    await nombreInput.first().click();
    await nombreInput.first().fill("Negocio Prueba Automatización");

    screenshots.agregarNegocioModal = await checkpointScreenshot(
      page,
      testInfo,
      "Agregar Negocio modal"
    );

    await clickAndWait(byText(page, "Cancelar"), page);

    results["Agregar Negocio modal"] = {
      name: "Agregar Negocio modal",
      status: "PASS",
      details: "Modal fields and controls validated successfully.",
    };
  } catch (error) {
    results["Agregar Negocio modal"] = {
      name: "Agregar Negocio modal",
      status: "FAIL",
      details: `Agregar Negocio modal validation failed: ${(error as Error).message}`,
    };
    executionErrors.push(results["Agregar Negocio modal"].details);
  }

  // Step 4: Open Administrar Negocios
  try {
    if (!(await byText(page, "Administrar Negocios").isVisible().catch(() => false))) {
      const miNegocio = await findSidebarAction(page, "Mi Negocio");
      await clickAndWait(miNegocio, page);
    }

    await clickAndWait(byText(page, "Administrar Negocios"), page);

    await expect(byText(page, "Información General")).toBeVisible();
    await expect(byText(page, "Detalles de la Cuenta")).toBeVisible();
    await expect(byText(page, "Tus Negocios")).toBeVisible();
    await expect(byText(page, "Sección Legal")).toBeVisible();

    screenshots.administrarNegocios = await checkpointScreenshot(
      page,
      testInfo,
      "Administrar Negocios page",
      { fullPage: true }
    );

    results["Administrar Negocios view"] = {
      name: "Administrar Negocios view",
      status: "PASS",
      details: "All required account sections are present.",
    };
  } catch (error) {
    results["Administrar Negocios view"] = {
      name: "Administrar Negocios view",
      status: "FAIL",
      details: `Administrar Negocios validation failed: ${(error as Error).message}`,
    };
    executionErrors.push(results["Administrar Negocios view"].details);
  }

  // Step 5: Validate Información General
  try {
    const emailPattern = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    await expect(page.getByText(emailPattern)).toBeVisible();
    await expect(byText(page, "BUSINESS PLAN")).toBeVisible();
    await expect(byText(page, "Cambiar Plan")).toBeVisible();

    const infoGeneralSection = byText(page, "Información General")
      .locator("xpath=ancestor::*[self::section or self::div][1]")
      .first();
    await expect(infoGeneralSection).toContainText("Información General");
    const sectionText = (await infoGeneralSection.innerText()).trim();
    const detectedNameLine = findNameLikeLine(sectionText);
    expect(
      detectedNameLine,
      "Could not find a name-like text line in Información General section."
    ).toBeTruthy();

    results["Información General"] = {
      name: "Información General",
      status: "PASS",
      details: `User data, plan text, and plan action are visible. Name-like text detected: "${detectedNameLine}".`,
    };
  } catch (error) {
    results["Información General"] = {
      name: "Información General",
      status: "FAIL",
      details: `Información General validation failed: ${(error as Error).message}`,
    };
    executionErrors.push(results["Información General"].details);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(byText(page, "Cuenta creada")).toBeVisible();
    await expect(byText(page, "Estado activo")).toBeVisible();
    await expect(byText(page, "Idioma seleccionado")).toBeVisible();

    results["Detalles de la Cuenta"] = {
      name: "Detalles de la Cuenta",
      status: "PASS",
      details: "All account detail labels are visible.",
    };
  } catch (error) {
    results["Detalles de la Cuenta"] = {
      name: "Detalles de la Cuenta",
      status: "FAIL",
      details: `Detalles de la Cuenta validation failed: ${(error as Error).message}`,
    };
    executionErrors.push(results["Detalles de la Cuenta"].details);
  }

  // Step 7: Validate Tus Negocios
  try {
    const tusNegociosSection = byText(page, "Tus Negocios")
      .locator("xpath=ancestor::*[self::section or self::div][1]")
      .first();
    await expect(tusNegociosSection).toBeVisible();
    await expect(byText(page, "Agregar Negocio")).toBeVisible();
    await expect(byText(page, "Tienes 2 de 3 negocios")).toBeVisible();
    const sectionText = (await tusNegociosSection.innerText()).trim();
    expect(
      sectionText.length,
      "Tus Negocios section appears empty; expected business list content."
    ).toBeGreaterThan(40);

    results["Tus Negocios"] = {
      name: "Tus Negocios",
      status: "PASS",
      details: "Business section and constraints text are visible with non-empty content.",
    };
  } catch (error) {
    results["Tus Negocios"] = {
      name: "Tus Negocios",
      status: "FAIL",
      details: `Tus Negocios validation failed: ${(error as Error).message}`,
    };
    executionErrors.push(results["Tus Negocios"].details);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const terminosResult = await validateLegalLink(
      page,
      testInfo,
      "Términos y Condiciones",
      "Términos y Condiciones"
    );
    legalUrls["Términos y Condiciones"] = terminosResult.finalUrl;
    screenshots.terminos = terminosResult.screenshotPath;

    results["Términos y Condiciones"] = {
      name: "Términos y Condiciones",
      status: "PASS",
      details: `Legal content validated at URL: ${terminosResult.finalUrl}`,
    };
  } catch (error) {
    results["Términos y Condiciones"] = {
      name: "Términos y Condiciones",
      status: "FAIL",
      details: `Términos y Condiciones validation failed: ${(error as Error).message}`,
    };
    executionErrors.push(results["Términos y Condiciones"].details);
  }

  // Step 9: Validate Política de Privacidad
  try {
    const privacyResult = await validateLegalLink(
      page,
      testInfo,
      "Política de Privacidad",
      "Política de Privacidad"
    );
    legalUrls["Política de Privacidad"] = privacyResult.finalUrl;
    screenshots.politica = privacyResult.screenshotPath;

    results["Política de Privacidad"] = {
      name: "Política de Privacidad",
      status: "PASS",
      details: `Legal content validated at URL: ${privacyResult.finalUrl}`,
    };
  } catch (error) {
    results["Política de Privacidad"] = {
      name: "Política de Privacidad",
      status: "FAIL",
      details: `Política de Privacidad validation failed: ${(error as Error).message}`,
    };
    executionErrors.push(results["Política de Privacidad"].details);
  } finally {
    // Step 10 final report is always emitted even on failures.
    await writeJsonEvidence(testInfo, "final-report.json", {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      baseUrlUsed: providedUrl ?? "current browser URL (pre-opened login page)",
      statusByField: results,
      legalUrls,
      screenshots,
    });
  }

  expect(
    executionErrors,
    `Workflow had failed validations:\n${executionErrors.join("\n")}`
  ).toEqual([]);
});
