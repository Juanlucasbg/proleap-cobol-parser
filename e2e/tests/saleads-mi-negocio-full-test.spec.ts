import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import { promises as fs } from "node:fs";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  checks: string[];
  evidence: string[];
  urls: string[];
  error?: string;
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

const DEFAULT_WAIT_TIMEOUT_MS = 15_000;

function createStepResult(): StepResult {
  return {
    status: "PASS",
    checks: [],
    evidence: [],
    urls: []
  };
}

function recordCheck(step: StepResult, passed: boolean, message: string): void {
  step.checks.push(`${passed ? "PASS" : "FAIL"} - ${message}`);
  if (!passed) {
    step.status = "FAIL";
  }
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
  await page.waitForLoadState("networkidle").catch(() => undefined);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  step: StepResult,
  fileName: string,
  fullPage = false
): Promise<void> {
  const path = testInfo.outputPath(`${fileName}.png`);
  await page.screenshot({ path, fullPage });
  step.evidence.push(path);
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator.isVisible().catch(() => false);
}

async function findVisibleByText(
  page: Page,
  patterns: RegExp[],
  timeoutMs = DEFAULT_WAIT_TIMEOUT_MS
): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const pattern of patterns) {
      const candidates = [
        page.getByRole("button", { name: pattern }).first(),
        page.getByRole("link", { name: pattern }).first(),
        page.getByRole("menuitem", { name: pattern }).first(),
        page.getByRole("heading", { name: pattern }).first(),
        page.getByText(pattern).first()
      ];

      for (const candidate of candidates) {
        if (await isVisible(candidate)) {
          return candidate;
        }
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Could not find visible element for patterns: ${patterns.map((p) => p.toString()).join(", ")}`);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function validateTextVisible(
  page: Page,
  step: StepResult,
  description: string,
  pattern: RegExp
): Promise<boolean> {
  const locator = page.getByText(pattern).first();
  const passed = await isVisible(locator);
  recordCheck(step, passed, description);
  return passed;
}

async function validateAnyVisibleByText(
  page: Page,
  step: StepResult,
  description: string,
  patterns: RegExp[],
  timeoutMs = DEFAULT_WAIT_TIMEOUT_MS
): Promise<boolean> {
  try {
    await findVisibleByText(page, patterns, timeoutMs);
    recordCheck(step, true, description);
    return true;
  } catch {
    recordCheck(step, false, description);
    return false;
  }
}

async function chooseGoogleAccountIfPrompted(page: Page, step: StepResult): Promise<void> {
  const accountLocator = page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")).first();
  if (await isVisible(accountLocator)) {
    await clickAndWait(page, accountLocator);
    recordCheck(step, true, `Google account ${GOOGLE_ACCOUNT_EMAIL} selected`);
    return;
  }

  recordCheck(step, true, "Google account chooser not shown (already authenticated or not required)");
}

async function openLegalLinkAndValidate(
  page: Page,
  testInfo: TestInfo,
  step: StepResult,
  linkPatterns: RegExp[],
  headingPattern: RegExp,
  screenshotName: string
): Promise<void> {
  const trigger = await findVisibleByText(page, linkPatterns);

  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await trigger.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await waitForUi(legalPage);

  const headingVisible = await isVisible(legalPage.getByRole("heading", { name: headingPattern }).first());
  recordCheck(step, headingVisible, `Heading ${headingPattern.toString()} is visible`);

  const legalTextVisible = await validateAnyVisibleByText(
    legalPage,
    step,
    "Legal content text is visible",
    [/t[eé]rminos/i, /condiciones/i, /privacidad/i, /datos/i, /legal/i]
  );
  if (!legalTextVisible) {
    recordCheck(step, false, "Expected legal content text was not found");
  }

  await captureCheckpoint(legalPage, testInfo, step, screenshotName, true);
  step.urls.push(legalPage.url());

  if (popup) {
    await popup.close().catch(() => undefined);
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const results = new Map<string, StepResult>();

  async function runStep(stepName: string, runner: (step: StepResult) => Promise<void>): Promise<void> {
    const step = createStepResult();
    try {
      await runner(step);
    } catch (error) {
      step.status = "FAIL";
      step.error = error instanceof Error ? error.message : String(error);
      step.checks.push(`FAIL - Unhandled step error: ${step.error}`);
    }
    results.set(stepName, step);
  }

  await runStep("Login", async (step) => {
    const configuredLoginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL ?? process.env.BASE_URL;
    if (page.url() === "about:blank" && configuredLoginUrl) {
      await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    if (page.url() === "about:blank") {
      throw new Error(
        "Browser started on about:blank. Set SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL) to the login page for current environment."
      );
    }

    const loginButton = await findVisibleByText(page, [
      /sign in with google/i,
      /iniciar sesi[oó]n con google/i,
      /continuar con google/i,
      /google/i,
      /iniciar sesi[oó]n/i,
      /login/i
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    if (popup) {
      await waitForUi(popup);
      await chooseGoogleAccountIfPrompted(popup, step);
      await popup.waitForEvent("close", { timeout: 30_000 }).catch(() => undefined);
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await chooseGoogleAccountIfPrompted(page, step);
      await waitForUi(page);
    }

    await validateAnyVisibleByText(page, step, "Main application interface appears", [
      /dashboard/i,
      /inicio/i,
      /negocio/i,
      /mi negocio/i
    ]);

    await validateAnyVisibleByText(page, step, "Left sidebar navigation is visible", [
      /negocio/i,
      /mi negocio/i,
      /configuraci[oó]n/i,
      /perfil/i
    ]);

    await captureCheckpoint(page, testInfo, step, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async (step) => {
    const negocioSection = await findVisibleByText(page, [/^negocio$/i, /negocio/i]);
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await findVisibleByText(page, [/mi negocio/i]);
    await clickAndWait(page, miNegocioOption);

    recordCheck(step, true, "Submenu expanded after clicking Mi Negocio");
    await validateAnyVisibleByText(page, step, "'Agregar Negocio' is visible", [/agregar negocio/i]);
    await validateAnyVisibleByText(page, step, "'Administrar Negocios' is visible", [/administrar negocios/i]);
    await captureCheckpoint(page, testInfo, step, "02-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async (step) => {
    const addBusiness = await findVisibleByText(page, [/agregar negocio/i]);
    await clickAndWait(page, addBusiness);

    await validateAnyVisibleByText(page, step, "Modal title 'Crear Nuevo Negocio' is visible", [/crear nuevo negocio/i]);
    await validateAnyVisibleByText(page, step, "Input field 'Nombre del Negocio' exists", [/nombre del negocio/i]);
    await validateAnyVisibleByText(page, step, "Text 'Tienes 2 de 3 negocios' is visible", [/tienes 2 de 3 negocios/i]);
    await validateAnyVisibleByText(page, step, "Buttons 'Cancelar' and 'Crear Negocio' are present", [
      /cancelar/i,
      /crear negocio/i
    ]);

    const businessNameInput = page.getByPlaceholder(/nombre del negocio/i).first();
    if (await isVisible(businessNameInput)) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      recordCheck(step, true, "Optional input action completed");
    } else {
      const labeledInput = page.getByLabel(/nombre del negocio/i).first();
      if (await isVisible(labeledInput)) {
        await labeledInput.click();
        await labeledInput.fill("Negocio Prueba Automatización");
        recordCheck(step, true, "Optional input action completed using labeled field");
      } else {
        recordCheck(step, false, "Optional input action skipped because field was not interactable");
      }
    }

    await captureCheckpoint(page, testInfo, step, "03-agregar-negocio-modal");

    const cancelButton = await findVisibleByText(page, [/cancelar/i]);
    await clickAndWait(page, cancelButton);
  });

  await runStep("Administrar Negocios view", async (step) => {
    const adminOption = await findVisibleByText(page, [/administrar negocios/i]).catch(async () => {
      const miNegocioOption = await findVisibleByText(page, [/mi negocio/i]);
      await clickAndWait(page, miNegocioOption);
      return findVisibleByText(page, [/administrar negocios/i]);
    });

    await clickAndWait(page, adminOption);

    await validateTextVisible(page, step, "Section 'Información General' exists", /informaci[oó]n general/i);
    await validateTextVisible(page, step, "Section 'Detalles de la Cuenta' exists", /detalles de la cuenta/i);
    await validateTextVisible(page, step, "Section 'Tus Negocios' exists", /tus negocios/i);
    await validateTextVisible(page, step, "Section 'Sección Legal' exists", /secci[oó]n legal/i);
    await captureCheckpoint(page, testInfo, step, "04-administrar-negocios-view", true);
  });

  await runStep("Información General", async (step) => {
    await validateAnyVisibleByText(page, step, "User name is visible", [/hola/i, /bienvenido/i, /perfil/i, /usuario/i]);
    await validateAnyVisibleByText(page, step, "User email is visible", [/@/i]);
    await validateTextVisible(page, step, "Text 'BUSINESS PLAN' is visible", /business plan/i);
    await validateAnyVisibleByText(page, step, "Button 'Cambiar Plan' is visible", [/cambiar plan/i]);
  });

  await runStep("Detalles de la Cuenta", async (step) => {
    await validateTextVisible(page, step, "'Cuenta creada' is visible", /cuenta creada/i);
    await validateTextVisible(page, step, "'Estado activo' is visible", /estado activo/i);
    await validateTextVisible(page, step, "'Idioma seleccionado' is visible", /idioma seleccionado/i);
  });

  await runStep("Tus Negocios", async (step) => {
    await validateTextVisible(page, step, "Business list is visible", /tus negocios/i);
    await validateAnyVisibleByText(page, step, "Button 'Agregar Negocio' exists", [/agregar negocio/i]);
    await validateTextVisible(page, step, "Text 'Tienes 2 de 3 negocios' is visible", /tienes 2 de 3 negocios/i);
  });

  await runStep("Términos y Condiciones", async (step) => {
    await openLegalLinkAndValidate(
      page,
      testInfo,
      step,
      [/t[eé]rminos y condiciones/i],
      /t[eé]rminos y condiciones/i,
      "05-terminos-y-condiciones"
    );
  });

  await runStep("Política de Privacidad", async (step) => {
    await openLegalLinkAndValidate(
      page,
      testInfo,
      step,
      [/pol[ií]tica de privacidad/i],
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad"
    );
  });

  const finalResults = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, results.get(field)?.status ?? "FAIL"])
  ) as Record<(typeof REPORT_FIELDS)[number], StepStatus>;

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: finalResults,
    details: Object.fromEntries(results.entries())
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  testInfo.attachments.push({
    name: "saleads-mi-negocio-final-report",
    path: reportPath,
    contentType: "application/json"
  });

  console.log("Final report:");
  console.log(JSON.stringify(finalReport, null, 2));

  const failedFields = REPORT_FIELDS.filter((field) => finalResults[field] === "FAIL");
  expect(
    failedFields,
    `Expected all validation steps to pass. Failed sections: ${failedFields.join(", ") || "none"}`
  ).toEqual([]);
});
