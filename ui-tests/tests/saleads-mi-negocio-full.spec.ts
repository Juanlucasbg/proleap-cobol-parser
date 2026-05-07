import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

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
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details: string[];
};

type FinalReport = Record<ReportField, StepResult>;

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
}

async function firstVisibleLocator(candidates: Locator[]): Promise<Locator | null> {
  for (const locator of candidates) {
    const candidate = locator.first();
    const count = await candidate.count();
    if (count === 0) {
      continue;
    }

    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  return null;
}

async function expectAnyVisible(candidates: Locator[], description: string): Promise<Locator> {
  const visible = await firstVisibleLocator(candidates);
  if (!visible) {
    throw new Error(`No visible element found for: ${description}`);
  }
  return visible;
}

async function clickByVisibleText(page: Page, textPattern: RegExp): Promise<void> {
  const target = await expectAnyVisible(
    [
      page.getByRole("button", { name: textPattern }),
      page.getByRole("link", { name: textPattern }),
      page.getByRole("menuitem", { name: textPattern }),
      page.getByText(textPattern),
    ],
    `click target with text pattern: ${textPattern}`
  );

  await target.click();
  await waitForUi(page);
}

function makeEmptyReport(): FinalReport {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = {
      status: "FAIL",
      details: ["Not executed."],
    };
    return acc;
  }, {} as FinalReport);
}

function setPass(report: FinalReport, field: ReportField, detail: string): void {
  report[field] = {
    status: "PASS",
    details: [detail],
  };
}

function setFail(report: FinalReport, field: ReportField, error: unknown): void {
  const message = error instanceof Error ? error.message : String(error);
  report[field] = {
    status: "FAIL",
    details: [message],
  };
}

async function ensureMenuExpanded(page: Page): Promise<void> {
  const addBusinessVisible = await page
    .getByText(/Agregar Negocio/i)
    .first()
    .isVisible()
    .catch(() => false);

  if (!addBusinessVisible) {
    await clickByVisibleText(page, /Mi Negocio/i);
  }
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
  test.setTimeout(300_000);

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT ?? DEFAULT_GOOGLE_ACCOUNT;

  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const evidenceDir = path.join(
    process.cwd(),
    "artifacts",
    "saleads_mi_negocio_full_test",
    runId
  );
  await fs.mkdir(evidenceDir, { recursive: true });

  const report = makeEmptyReport();
  const legalUrls: Record<"terminos" | "privacidad", string> = {
    terminos: "",
    privacidad: "",
  };

  const screenshot = async (
    filename: string,
    targetPage: Page = page,
    fullPage = false
  ): Promise<string> => {
    const imagePath = path.join(evidenceDir, filename);
    await targetPage.screenshot({ path: imagePath, fullPage });
    return imagePath;
  };

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  // Step 1: Login with Google
  try {
    const onLoginScreen =
      (await page
        .getByText(/google/i)
        .first()
        .isVisible()
        .catch(() => false)) ||
      (await page
        .getByRole("button", { name: /google/i })
        .first()
        .isVisible()
        .catch(() => false));

    if (!onLoginScreen) {
      throw new Error(
        "SaleADS login page was not detected. Provide SALEADS_LOGIN_URL for the target environment."
      );
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickByVisibleText(
      page,
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|ingresar con google|google/i
    );

    const googlePage = await popupPromise;
    const accountChooserPage = googlePage ?? page;
    await waitForUi(accountChooserPage);

    const accountLocator = accountChooserPage.getByText(googleAccount, { exact: true }).first();
    if (await accountLocator.isVisible().catch(() => false)) {
      await accountLocator.click();
      await waitForUi(accountChooserPage);
    }

    if (googlePage) {
      await googlePage.waitForEvent("close", { timeout: 40_000 }).catch(() => {});
    }

    await page.bringToFront();
    await waitForUi(page);
    await expectAnyVisible(
      [page.locator("aside"), page.getByRole("navigation"), page.getByText(/Mi Negocio|Negocio/i)],
      "main application interface and left sidebar"
    );

    await screenshot("01-dashboard-loaded.png");
    setPass(report, "Login", "Main app interface and left sidebar are visible after Google login.");
  } catch (error) {
    await screenshot("01-login-failure.png").catch(() => {});
    setFail(report, "Login", error);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await expectAnyVisible([page.locator("aside"), page.getByRole("navigation")], "left sidebar");
    await expectAnyVisible([page.getByText(/^Negocio$/i), page.getByText(/Negocio/i)], "Negocio section");
    await clickByVisibleText(page, /Mi Negocio/i);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await screenshot("02-mi-negocio-menu-expanded.png");
    setPass(
      report,
      "Mi Negocio menu",
      "Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios."
    );
  } catch (error) {
    await screenshot("02-mi-negocio-menu-failure.png").catch(() => {});
    setFail(report, "Mi Negocio menu", error);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await ensureMenuExpanded(page);
    await clickByVisibleText(page, /Agregar Negocio/i);

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    await screenshot("03-agregar-negocio-modal.png");

    const nameInput = await firstVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[type='text'], input:not([type])"),
    ]);

    if (nameInput) {
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
    }

    await clickByVisibleText(page, /Cancelar/i);
    setPass(
      report,
      "Agregar Negocio modal",
      "Crear Nuevo Negocio modal content validated and cancelled successfully."
    );
  } catch (error) {
    await screenshot("03-agregar-negocio-modal-failure.png").catch(() => {});
    setFail(report, "Agregar Negocio modal", error);
  }

  // Step 4: Open Administrar Negocios
  try {
    await ensureMenuExpanded(page);
    await clickByVisibleText(page, /Administrar Negocios/i);
    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await screenshot("04-administrar-negocios-page.png", page, true);
    setPass(
      report,
      "Administrar Negocios view",
      "Administrar Negocios page loaded with all required sections."
    );
  } catch (error) {
    await screenshot("04-administrar-negocios-failure.png").catch(() => {});
    setFail(report, "Administrar Negocios view", error);
  }

  // Step 5: Validate Información General
  try {
    const bodyText = await page.locator("body").innerText();
    if (!/@/.test(bodyText)) {
      throw new Error("No visible email found in Información General.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    setPass(
      report,
      "Información General",
      "User identity and plan controls validated in Información General."
    );
  } catch (error) {
    await screenshot("05-informacion-general-failure.png").catch(() => {});
    setFail(report, "Información General", error);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    setPass(report, "Detalles de la Cuenta", "Detalles de la Cuenta labels are visible.");
  } catch (error) {
    await screenshot("06-detalles-cuenta-failure.png").catch(() => {});
    setFail(report, "Detalles de la Cuenta", error);
  }

  // Step 7: Validate Tus Negocios
  try {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const businessCards = page.locator(
      "[data-testid*='business'], [class*='business'], [class*='negocio'], li, article"
    );
    if ((await businessCards.count()) === 0) {
      throw new Error("Business list/cards were not detected in Tus Negocios.");
    }

    setPass(report, "Tus Negocios", "Tus Negocios list and capacity state are visible.");
  } catch (error) {
    await screenshot("07-tus-negocios-failure.png").catch(() => {});
    setFail(report, "Tus Negocios", error);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const legalPopupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickByVisibleText(page, /Términos y Condiciones|Terminos y Condiciones/i);
    const legalPage = (await legalPopupPromise) ?? page;
    await waitForUi(legalPage);

    await expectAnyVisible(
      [
        legalPage.getByRole("heading", { name: /Términos y Condiciones|Terminos y Condiciones/i }),
        legalPage.getByText(/Términos y Condiciones|Terminos y Condiciones/i),
      ],
      "Términos y Condiciones heading"
    );

    const termsText = await legalPage.locator("body").innerText();
    if (termsText.trim().length < 120) {
      throw new Error("Legal content for Términos y Condiciones seems too short.");
    }

    legalUrls.terminos = legalPage.url();
    await screenshot("08-terminos-y-condiciones.png", legalPage, true);

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUi(page);
    }

    setPass(
      report,
      "Términos y Condiciones",
      `Legal page validated. Final URL: ${legalUrls.terminos || "N/A"}`
    );
  } catch (error) {
    await screenshot("08-terminos-failure.png").catch(() => {});
    setFail(report, "Términos y Condiciones", error);
  }

  // Step 9: Validate Política de Privacidad
  try {
    const legalPopupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickByVisibleText(page, /Política de Privacidad|Politica de Privacidad/i);
    const legalPage = (await legalPopupPromise) ?? page;
    await waitForUi(legalPage);

    await expectAnyVisible(
      [
        legalPage.getByRole("heading", { name: /Política de Privacidad|Politica de Privacidad/i }),
        legalPage.getByText(/Política de Privacidad|Politica de Privacidad/i),
      ],
      "Política de Privacidad heading"
    );

    const privacyText = await legalPage.locator("body").innerText();
    if (privacyText.trim().length < 120) {
      throw new Error("Legal content for Política de Privacidad seems too short.");
    }

    legalUrls.privacidad = legalPage.url();
    await screenshot("09-politica-de-privacidad.png", legalPage, true);

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUi(page);
    }

    setPass(
      report,
      "Política de Privacidad",
      `Legal page validated. Final URL: ${legalUrls.privacidad || "N/A"}`
    );
  } catch (error) {
    await screenshot("09-politica-failure.png").catch(() => {});
    setFail(report, "Política de Privacidad", error);
  }

  // Step 10: Final Report
  const statusMatrix = REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = report[field].status;
    return acc;
  }, {} as Record<ReportField, StepStatus>);

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    timestamp: new Date().toISOString(),
    report: statusMatrix,
    details: report,
    legalUrls,
    evidenceDirectory: evidenceDir,
  };

  const reportPath = path.join(evidenceDir, "final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");

  await testInfo.attach("final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf8"),
    contentType: "application/json",
  });

  console.log("SaleADS Mi Negocio final report:");
  console.table(statusMatrix);
  console.log(`Términos y Condiciones URL: ${legalUrls.terminos || "N/A"}`);
  console.log(`Política de Privacidad URL: ${legalUrls.privacidad || "N/A"}`);
  console.log(`Evidence directory: ${evidenceDir}`);

  const failedSteps = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failedSteps,
    `The following validation steps failed: ${failedSteps.join(", ")}`
  ).toHaveLength(0);
});
