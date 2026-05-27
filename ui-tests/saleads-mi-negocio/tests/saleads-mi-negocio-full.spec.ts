import fs from "node:fs/promises";
import path from "node:path";
import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

interface StepResult {
  status: StepStatus;
  notes: string[];
  screenshots: string[];
  finalUrl?: string;
}

const REPORT_FIELDS: ReportField[] = [
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

const GOOGLE_ACCOUNT =
  process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";

function createReport(): Record<ReportField, StepResult> {
  return REPORT_FIELDS.reduce(
    (accumulator, stepName) => ({
      ...accumulator,
      [stepName]: { status: "PASS", notes: [], screenshots: [] },
    }),
    {} as Record<ReportField, StepResult>,
  );
}

function markPass(
  report: Record<ReportField, StepResult>,
  step: ReportField,
  note: string,
): void {
  report[step].notes.push(`PASS: ${note}`);
}

function markFail(
  report: Record<ReportField, StepResult>,
  step: ReportField,
  note: string,
  error?: unknown,
): void {
  report[step].status = "FAIL";
  const suffix =
    error instanceof Error ? ` (${error.message})` : error ? ` (${String(error)})` : "";
  report[step].notes.push(`FAIL: ${note}${suffix}`);
}

function escapeRegex(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 7_000 });
  } catch {
    // Some views keep background requests open. Continue when networkidle is not reached.
  }
  await page.waitForTimeout(700);
}

async function isVisible(locator: Locator): Promise<boolean> {
  if ((await locator.count()) === 0) {
    return false;
  }

  try {
    return await locator.first().isVisible();
  } catch {
    return false;
  }
}

function textLocators(page: Page, text: string): Locator[] {
  const expression = new RegExp(escapeRegex(text), "i");
  return [
    page.getByRole("button", { name: expression }),
    page.getByRole("link", { name: expression }),
    page.getByRole("menuitem", { name: expression }),
    page.getByRole("tab", { name: expression }),
    page.getByText(expression),
  ];
}

async function clickUsingVisibleText(
  page: Page,
  step: ReportField,
  report: Record<ReportField, StepResult>,
  textOptions: string[],
): Promise<boolean> {
  for (const textOption of textOptions) {
    const locators = textLocators(page, textOption);
    for (const locator of locators) {
      if (!(await isVisible(locator))) {
        continue;
      }

      try {
        await locator.first().click();
        await waitForUi(page);
        markPass(report, step, `Clicked "${textOption}".`);
        return true;
      } catch (error) {
        markFail(report, step, `Could not click "${textOption}".`, error);
      }
    }
  }

  markFail(
    report,
    step,
    `Could not find visible element for: ${textOptions.join(", ")}.`,
  );
  return false;
}

async function expectVisible(
  locator: Locator,
  step: ReportField,
  report: Record<ReportField, StepResult>,
  description: string,
): Promise<boolean> {
  try {
    await expect(locator.first()).toBeVisible({ timeout: 15_000 });
    markPass(report, step, `${description} is visible.`);
    return true;
  } catch (error) {
    markFail(report, step, `${description} is not visible.`, error);
    return false;
  }
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  report: Record<ReportField, StepResult>,
  step: ReportField,
  fileName: string,
  fullPage = false,
): Promise<void> {
  const capturePath = testInfo.outputPath(fileName);
  await page.screenshot({ path: capturePath, fullPage });
  report[step].screenshots.push(capturePath);
  await testInfo.attach(`${step} - ${fileName}`, {
    path: capturePath,
    contentType: "image/png",
  });
  markPass(report, step, `Screenshot captured (${fileName}).`);
}

async function validateLegalPage(
  appPage: Page,
  linkText: string,
  expectedHeading: string,
  step: ReportField,
  report: Record<ReportField, StepResult>,
  testInfo: TestInfo,
): Promise<void> {
  const context = appPage.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  const clicked = await clickUsingVisibleText(appPage, step, report, [linkText]);
  if (!clicked) {
    return;
  }

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await waitForUi(popup);
    markPass(report, step, `"${linkText}" opened in a new tab.`);
  } else {
    await waitForUi(appPage);
    markPass(report, step, `"${linkText}" opened in the same tab.`);
  }

  const headingRegex = new RegExp(escapeRegex(expectedHeading), "i");
  const headingByRole = targetPage.getByRole("heading", { name: headingRegex });
  const headingByText = targetPage.getByText(headingRegex);
  const headingVisible =
    (await isVisible(headingByRole)) ||
    (await expectVisible(headingByText, step, report, `Heading "${expectedHeading}"`));
  if (headingVisible && (await isVisible(headingByRole))) {
    markPass(report, step, `Heading "${expectedHeading}" is visible.`);
  }

  try {
    const bodyText = (await targetPage.locator("body").innerText()).trim();
    if (bodyText.length >= 120) {
      markPass(report, step, `Legal content has ${bodyText.length} characters.`);
    } else {
      markFail(report, step, "Legal content is too short.");
    }
  } catch (error) {
    markFail(report, step, "Could not validate legal content.", error);
  }

  await captureCheckpoint(
    targetPage,
    testInfo,
    report,
    step,
    `${step.toLowerCase().replace(/\s+/g, "-")}.png`,
    true,
  );

  report[step].finalUrl = targetPage.url();
  markPass(report, step, `Captured final URL: ${targetPage.url()}`);

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    markPass(report, step, "Returned to the application tab.");
    return;
  }

  await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
  await waitForUi(appPage);
  markPass(report, step, "Returned to the application view.");
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.setTimeout(6 * 60 * 1000);
  const report = createReport();

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    markPass(report, "Login", `Opened SALEADS_LOGIN_URL (${loginUrl}).`);
  } else if (page.url() === "about:blank") {
    markFail(
      report,
      "Login",
      "Browser started at about:blank. Set SALEADS_LOGIN_URL or open login page before running.",
    );
  } else {
    markPass(report, "Login", `Using pre-opened page (${page.url()}).`);
  }

  // Step 1: Login with Google.
  const loginPopupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  const clickedGoogle = await clickUsingVisibleText(page, "Login", report, [
    "Sign in with Google",
    "Iniciar sesión con Google",
    "Continuar con Google",
    "Google",
  ]);

  if (clickedGoogle) {
    const loginPopup = await loginPopupPromise;
    if (loginPopup) {
      await loginPopup.waitForLoadState("domcontentloaded");
      await waitForUi(loginPopup);
      markPass(report, "Login", "Google sign-in opened in a new tab.");

      await clickUsingVisibleText(loginPopup, "Login", report, [GOOGLE_ACCOUNT]);
      await loginPopup.waitForTimeout(2_000);
      if (!loginPopup.isClosed()) {
        markPass(
          report,
          "Login",
          "Google login tab remained open after account selection; continuing validation on app tab.",
        );
      }
    } else {
      await clickUsingVisibleText(page, "Login", report, [GOOGLE_ACCOUNT]);
    }
  }

  await waitForUi(page);
  await expectVisible(
    page.locator("aside, nav").first(),
    "Login",
    report,
    "Main application layout",
  );
  await expectVisible(
    page.getByText(/Negocio|Mi Negocio|Dashboard/i).first(),
    "Login",
    report,
    "Left sidebar navigation",
  );
  await captureCheckpoint(page, testInfo, report, "Login", "01-dashboard-loaded.png");

  // Step 2: Open Mi Negocio menu.
  await clickUsingVisibleText(page, "Mi Negocio menu", report, ["Negocio"]);
  await clickUsingVisibleText(page, "Mi Negocio menu", report, ["Mi Negocio"]);
  await expectVisible(
    page.getByText(/Agregar Negocio/i).first(),
    "Mi Negocio menu",
    report,
    "\"Agregar Negocio\" option",
  );
  await expectVisible(
    page.getByText(/Administrar Negocios/i).first(),
    "Mi Negocio menu",
    report,
    "\"Administrar Negocios\" option",
  );
  await captureCheckpoint(page, testInfo, report, "Mi Negocio menu", "02-mi-negocio-menu-expanded.png");

  // Step 3: Validate Agregar Negocio modal.
  await clickUsingVisibleText(page, "Agregar Negocio modal", report, ["Agregar Negocio"]);
  await expectVisible(
    page.getByText(/Crear Nuevo Negocio/i).first(),
    "Agregar Negocio modal",
    report,
    "Modal title \"Crear Nuevo Negocio\"",
  );
  const businessNameByLabel = page.getByLabel(/Nombre del Negocio/i);
  const businessNameByPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i);
  if (await isVisible(businessNameByLabel)) {
    await expectVisible(
      businessNameByLabel,
      "Agregar Negocio modal",
      report,
      "\"Nombre del Negocio\" input field",
    );
    await businessNameByLabel.fill("Negocio Prueba Automatizacion");
    markPass(report, "Agregar Negocio modal", "Typed sample business name.");
  } else {
    await expectVisible(
      businessNameByPlaceholder,
      "Agregar Negocio modal",
      report,
      "\"Nombre del Negocio\" input field",
    );
    if (await isVisible(businessNameByPlaceholder)) {
      await businessNameByPlaceholder.fill("Negocio Prueba Automatizacion");
      markPass(report, "Agregar Negocio modal", "Typed sample business name.");
    }
  }
  await expectVisible(
    page.getByText(/Tienes 2 de 3 negocios/i).first(),
    "Agregar Negocio modal",
    report,
    "\"Tienes 2 de 3 negocios\" text",
  );
  await expectVisible(
    page.getByRole("button", { name: /Cancelar/i }),
    "Agregar Negocio modal",
    report,
    "\"Cancelar\" button",
  );
  await expectVisible(
    page.getByRole("button", { name: /Crear Negocio/i }),
    "Agregar Negocio modal",
    report,
    "\"Crear Negocio\" button",
  );
  await captureCheckpoint(page, testInfo, report, "Agregar Negocio modal", "03-agregar-negocio-modal.png");
  await clickUsingVisibleText(page, "Agregar Negocio modal", report, ["Cancelar"]);

  // Step 4: Open Administrar Negocios.
  await clickUsingVisibleText(page, "Administrar Negocios view", report, ["Mi Negocio"]);
  await clickUsingVisibleText(page, "Administrar Negocios view", report, ["Administrar Negocios"]);
  await expectVisible(
    page.getByText(/Información General/i).first(),
    "Administrar Negocios view",
    report,
    "\"Información General\" section",
  );
  await expectVisible(
    page.getByText(/Detalles de la Cuenta/i).first(),
    "Administrar Negocios view",
    report,
    "\"Detalles de la Cuenta\" section",
  );
  await expectVisible(
    page.getByText(/Tus Negocios/i).first(),
    "Administrar Negocios view",
    report,
    "\"Tus Negocios\" section",
  );
  await expectVisible(
    page.getByText(/Sección Legal/i).first(),
    "Administrar Negocios view",
    report,
    "\"Sección Legal\" section",
  );
  await captureCheckpoint(
    page,
    testInfo,
    report,
    "Administrar Negocios view",
    "04-administrar-negocios.png",
    true,
  );

  // Step 5: Validate Información General.
  const accountText = await page.locator("body").innerText();
  if (/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(accountText)) {
    markPass(report, "Información General", "User email is visible.");
  } else {
    markFail(report, "Información General", "User email is not visible.");
  }
  if (/Nombre/i.test(accountText) || /Usuario/i.test(accountText)) {
    markPass(report, "Información General", "User name information is visible.");
  } else {
    markFail(report, "Información General", "User name information is not visible.");
  }
  await expectVisible(
    page.getByText(/BUSINESS PLAN/i).first(),
    "Información General",
    report,
    "\"BUSINESS PLAN\" text",
  );
  await expectVisible(
    page.getByRole("button", { name: /Cambiar Plan/i }),
    "Información General",
    report,
    "\"Cambiar Plan\" button",
  );

  // Step 6: Validate Detalles de la Cuenta.
  await expectVisible(
    page.getByText(/Cuenta creada/i).first(),
    "Detalles de la Cuenta",
    report,
    "\"Cuenta creada\" text",
  );
  await expectVisible(
    page.getByText(/Estado activo/i).first(),
    "Detalles de la Cuenta",
    report,
    "\"Estado activo\" text",
  );
  await expectVisible(
    page.getByText(/Idioma seleccionado/i).first(),
    "Detalles de la Cuenta",
    report,
    "\"Idioma seleccionado\" text",
  );

  // Step 7: Validate Tus Negocios.
  await expectVisible(
    page.getByText(/Tus Negocios/i).first(),
    "Tus Negocios",
    report,
    "\"Tus Negocios\" heading",
  );
  await expectVisible(
    page.getByRole("button", { name: /Agregar Negocio/i }),
    "Tus Negocios",
    report,
    "\"Agregar Negocio\" button",
  );
  await expectVisible(
    page.getByText(/Tienes 2 de 3 negocios/i).first(),
    "Tus Negocios",
    report,
    "\"Tienes 2 de 3 negocios\" text",
  );
  const businessListVisible =
    (await isVisible(page.locator("[role='listitem']"))) ||
    (await isVisible(page.locator("table tbody tr"))) ||
    (await isVisible(page.locator("ul li")));
  if (businessListVisible) {
    markPass(report, "Tus Negocios", "Business list is visible.");
  } else {
    markFail(report, "Tus Negocios", "Business list is not visible.");
  }

  // Step 8: Validate Términos y Condiciones.
  await validateLegalPage(
    page,
    "Términos y Condiciones",
    "Términos y Condiciones",
    "Términos y Condiciones",
    report,
    testInfo,
  );

  // Step 9: Validate Política de Privacidad.
  await validateLegalPage(
    page,
    "Política de Privacidad",
    "Política de Privacidad",
    "Política de Privacidad",
    report,
    testInfo,
  );

  // Step 10: Final report.
  const reportOutput = {
    generatedAt: new Date().toISOString(),
    loginUrlUsed: loginUrl ?? page.url(),
    googleAccountUsed: GOOGLE_ACCOUNT,
    results: REPORT_FIELDS.map((field) => ({
      field,
      status: report[field].status,
      notes: report[field].notes,
      screenshots: report[field].screenshots,
      finalUrl: report[field].finalUrl ?? null,
    })),
  };

  const reportDirectory = path.join(process.cwd(), "reports");
  const reportPath = path.join(reportDirectory, "saleads-mi-negocio-final-report.json");
  await fs.mkdir(reportDirectory, { recursive: true });
  await fs.writeFile(reportPath, JSON.stringify(reportOutput, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const failingSteps = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  if (failingSteps.length > 0) {
    throw new Error(`Workflow validation failed in: ${failingSteps.join(", ")}`);
  }
});
