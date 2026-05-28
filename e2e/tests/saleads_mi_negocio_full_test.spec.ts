import { expect, Locator, Page, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL" | "SKIPPED";

type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepReport = {
  status: StepStatus;
  details: string[];
  evidence: string[];
};

const REPORT_KEYS: ReportKey[] = [
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

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";

const escapeRegex = (value: string): string =>
  value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const toRegex = (value: string): RegExp => new RegExp(escapeRegex(value), "i");

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return null;
}

async function findClickable(scope: Page | Locator, labels: string[]): Promise<Locator> {
  const patterns = labels.map((label) => toRegex(label));
  const candidates: Locator[] = [];

  for (const pattern of patterns) {
    candidates.push(
      scope.getByRole("button", { name: pattern }),
      scope.getByRole("link", { name: pattern }),
      scope.getByRole("menuitem", { name: pattern }),
      scope.getByText(pattern)
    );
  }

  const found = await firstVisible(candidates);
  if (!found) {
    throw new Error(`No visible clickable element found for labels: ${labels.join(", ")}`);
  }

  return found;
}

async function expectVisibleText(scope: Page | Locator, labels: string[]): Promise<void> {
  const patterns = labels.map((label) => toRegex(label));
  const candidates: Locator[] = [];

  for (const pattern of patterns) {
    candidates.push(
      scope.getByRole("heading", { name: pattern }),
      scope.getByRole("button", { name: pattern }),
      scope.getByRole("link", { name: pattern }),
      scope.getByRole("textbox", { name: pattern }),
      scope.getByText(pattern)
    );
  }

  const found = await firstVisible(candidates);
  if (!found) {
    throw new Error(`No visible element found for labels: ${labels.join(", ")}`);
  }
  await expect(found).toBeVisible();
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(
    REPORT_KEYS.map((key) => [key, { status: "SKIPPED", details: [], evidence: [] } satisfies StepReport])
  ) as Record<ReportKey, StepReport>;

  const setStepStatus = (key: ReportKey, status: StepStatus, detail?: string): void => {
    report[key].status = status;
    if (detail) {
      report[key].details.push(detail);
    }
  };

  const saveCheckpoint = async (
    key: ReportKey,
    filename: string,
    targetPage: Page = page,
    fullPage = false
  ): Promise<void> => {
    const filePath = testInfo.outputPath(filename);
    await targetPage.screenshot({ path: filePath, fullPage });
    report[key].evidence.push(filePath);
  };

  const markRemainingSkipped = (fromIndex: number, reason: string): void => {
    for (const key of REPORT_KEYS.slice(fromIndex)) {
      if (report[key].status === "SKIPPED") {
        report[key].details.push(reason);
      }
    }
  };

  let sidebarScope: Locator | Page = page;

  // Step 1: Login with Google.
  try {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const loginButton = await findClickable(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Ingresar con Google",
      "Google",
    ]);
    await clickAndWait(loginButton, page);

    const accountOption = page.getByText(toRegex(GOOGLE_ACCOUNT_EMAIL)).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await clickAndWait(accountOption, page);
    }

    await expectVisibleText(page, ["Negocio"]);
    const sidebar = await firstVisible([page.locator("aside"), page.getByRole("navigation"), page.locator("nav")]);
    if (!sidebar) {
      throw new Error("Left sidebar was not detected after login.");
    }

    sidebarScope = sidebar;
    await saveCheckpoint("Login", "01-dashboard-loaded.png", page, true);
    setStepStatus("Login", "PASS", "Main interface and left sidebar are visible.");
  } catch (error) {
    setStepStatus("Login", "FAIL", String(error));
    markRemainingSkipped(1, "Blocked because login validation failed.");
  }

  // Step 2: Open Mi Negocio menu.
  if (report["Login"].status === "PASS") {
    try {
      const negocioOption = await findClickable(sidebarScope, ["Negocio"]);
      await clickAndWait(negocioOption, page);

      const miNegocioOption = await findClickable(sidebarScope, ["Mi Negocio"]);
      await clickAndWait(miNegocioOption, page);

      await expectVisibleText(sidebarScope, ["Agregar Negocio"]);
      await expectVisibleText(sidebarScope, ["Administrar Negocios"]);

      await saveCheckpoint("Mi Negocio menu", "02-mi-negocio-menu-expanded.png");
      setStepStatus("Mi Negocio menu", "PASS", "Submenu expanded with requested options.");
    } catch (error) {
      setStepStatus("Mi Negocio menu", "FAIL", String(error));
      markRemainingSkipped(2, "Blocked because Mi Negocio menu could not be opened.");
    }
  }

  // Step 3: Validate Agregar Negocio modal.
  if (report["Mi Negocio menu"].status === "PASS") {
    try {
      const addBusiness = await findClickable(sidebarScope, ["Agregar Negocio"]);
      await clickAndWait(addBusiness, page);

      const modal = page.getByRole("dialog").first();
      await expect(modal).toBeVisible();
      await expectVisibleText(modal, ["Crear Nuevo Negocio"]);
      await expectVisibleText(modal, ["Nombre del Negocio"]);
      await expectVisibleText(modal, ["Tienes 2 de 3 negocios"]);
      await expectVisibleText(modal, ["Cancelar"]);
      await expectVisibleText(modal, ["Crear Negocio"]);

      await saveCheckpoint("Agregar Negocio modal", "03-agregar-negocio-modal.png");

      const nameField =
        (await firstVisible([
          modal.getByRole("textbox", { name: toRegex("Nombre del Negocio") }),
          modal.getByPlaceholder(toRegex("Nombre del Negocio")),
          modal.getByLabel(toRegex("Nombre del Negocio")),
          modal.locator("input"),
        ])) ?? modal.locator("input").first();
      await nameField.click();
      await nameField.fill("Negocio Prueba Automatización");
      const cancelButton = await findClickable(modal, ["Cancelar"]);
      await clickAndWait(cancelButton, page);

      setStepStatus("Agregar Negocio modal", "PASS", "Modal fields and actions validated.");
    } catch (error) {
      setStepStatus("Agregar Negocio modal", "FAIL", String(error));
      markRemainingSkipped(3, "Blocked because Agregar Negocio modal validation failed.");
    }
  }

  // Step 4: Open Administrar Negocios and validate sections.
  if (report["Agregar Negocio modal"].status === "PASS") {
    try {
      const miNegocioOption = await findClickable(sidebarScope, ["Mi Negocio"]);
      await clickAndWait(miNegocioOption, page);

      const manageBusinesses = await findClickable(sidebarScope, ["Administrar Negocios"]);
      await clickAndWait(manageBusinesses, page);

      await expectVisibleText(page, ["Información General"]);
      await expectVisibleText(page, ["Detalles de la Cuenta"]);
      await expectVisibleText(page, ["Tus Negocios"]);
      await expectVisibleText(page, ["Sección Legal"]);

      await saveCheckpoint("Administrar Negocios view", "04-administrar-negocios-page.png", page, true);
      setStepStatus("Administrar Negocios view", "PASS", "Account page sections are visible.");
    } catch (error) {
      setStepStatus("Administrar Negocios view", "FAIL", String(error));
      markRemainingSkipped(4, "Blocked because Administrar Negocios page did not load.");
    }
  }

  // Step 5: Validate Información General.
  if (report["Administrar Negocios view"].status === "PASS") {
    try {
      await expectVisibleText(page, ["BUSINESS PLAN"]);
      await expectVisibleText(page, ["Cambiar Plan"]);
      await expectVisibleText(page, [GOOGLE_ACCOUNT_EMAIL]);

      const infoSection = page.getByText(toRegex("Información General")).first().locator("xpath=ancestor::*[self::section or self::div][1]");
      const infoText = await infoSection.innerText().catch(() => "");
      if (!infoText.trim()) {
        throw new Error("No user information text detected in Información General section.");
      }

      setStepStatus("Información General", "PASS", "Plan, email, and user information are visible.");
    } catch (error) {
      setStepStatus("Información General", "FAIL", String(error));
    }
  }

  // Step 6: Validate Detalles de la Cuenta.
  if (report["Administrar Negocios view"].status === "PASS") {
    try {
      await expectVisibleText(page, ["Cuenta creada"]);
      await expectVisibleText(page, ["Estado activo"]);
      await expectVisibleText(page, ["Idioma seleccionado"]);

      setStepStatus("Detalles de la Cuenta", "PASS", "Account details fields are visible.");
    } catch (error) {
      setStepStatus("Detalles de la Cuenta", "FAIL", String(error));
    }
  }

  // Step 7: Validate Tus Negocios.
  if (report["Administrar Negocios view"].status === "PASS") {
    try {
      await expectVisibleText(page, ["Tus Negocios"]);
      await expectVisibleText(page, ["Agregar Negocio"]);
      await expectVisibleText(page, ["Tienes 2 de 3 negocios"]);

      const businessCards = page.locator("[data-testid*='business'], [class*='business'], li, article");
      expect(await businessCards.count()).toBeGreaterThan(0);

      setStepStatus("Tus Negocios", "PASS", "Business list and quota text are visible.");
    } catch (error) {
      setStepStatus("Tus Negocios", "FAIL", String(error));
    }
  }

  const openLegalDocument = async (
    reportKey: "Términos y Condiciones" | "Política de Privacidad",
    linkLabel: string
  ): Promise<void> => {
    const legalLink = await findClickable(page, [linkLabel]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
    const navigationPromise = page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 8000 }).catch(() => null);

    await legalLink.click();
    await waitForUi(page);

    const popupPage = await popupPromise;
    const targetPage = popupPage ?? page;

    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
    } else {
      await navigationPromise;
    }

    await expectVisibleText(targetPage, [linkLabel]);
    const legalParagraphs = await targetPage.locator("p").count();
    expect(legalParagraphs).toBeGreaterThan(0);

    const finalUrl = targetPage.url();
    report[reportKey].details.push(`Final URL: ${finalUrl}`);
    await saveCheckpoint(
      reportKey,
      reportKey === "Términos y Condiciones" ? "08-terminos-y-condiciones.png" : "09-politica-de-privacidad.png",
      targetPage,
      true
    );

    if (popupPage) {
      await popupPage.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  };

  // Step 8: Validate Términos y Condiciones.
  if (report["Administrar Negocios view"].status === "PASS") {
    try {
      await openLegalDocument("Términos y Condiciones", "Términos y Condiciones");
      setStepStatus("Términos y Condiciones", "PASS", "Legal terms page validated.");
    } catch (error) {
      setStepStatus("Términos y Condiciones", "FAIL", String(error));
    }
  }

  // Step 9: Validate Política de Privacidad.
  if (report["Administrar Negocios view"].status === "PASS") {
    try {
      await openLegalDocument("Política de Privacidad", "Política de Privacidad");
      setStepStatus("Política de Privacidad", "PASS", "Privacy policy page validated.");
    } catch (error) {
      setStepStatus("Política de Privacidad", "FAIL", String(error));
    }
  }

  const finalReport = REPORT_KEYS.map((key) => ({
    step: key,
    status: report[key].status,
    details: report[key].details,
    evidence: report[key].evidence,
  }));

  const reportText = JSON.stringify(finalReport, null, 2);
  testInfo.annotations.push({ type: "final-report", description: reportText });
  await testInfo.attach("saleads-mi-negocio-report.json", {
    body: reportText,
    contentType: "application/json",
  });

  // Required final PASS/FAIL summary for each requested report field.
  console.log(`\nFinal Report: ${reportText}\n`);

  const failedSteps = finalReport.filter((item) => item.status === "FAIL");
  expect(failedSteps, "One or more validation steps failed.").toHaveLength(0);
});
