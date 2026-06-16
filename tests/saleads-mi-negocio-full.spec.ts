import { expect, Locator, Page, test } from "@playwright/test";
import path from "node:path";
import { promises as fs } from "node:fs";

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

type StepStatus = "PASS" | "FAIL";

interface StepResult {
  status: StepStatus;
  details?: string;
  screenshot?: string;
  url?: string;
}

type FinalReport = Record<ReportKey, StepResult>;

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function ensureDir(dirPath: string): Promise<void> {
  await fs.mkdir(dirPath, { recursive: true });
}

async function isVisible(locator: Locator): Promise<boolean> {
  if ((await locator.count()) === 0) {
    return false;
  }

  return locator.first().isVisible().catch(() => false);
}

async function firstVisible(locators: Locator[]): Promise<Locator> {
  for (const locator of locators) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }

  throw new Error("No visible locator found for the provided candidates.");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page: Page, evidenceDir: string, fileName: string, fullPage = false): Promise<string> {
  const filePath = path.join(evidenceDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function createInitialReport(): FinalReport {
  return {
    Login: { status: "FAIL", details: "Step not executed." },
    "Mi Negocio menu": { status: "FAIL", details: "Step not executed." },
    "Agregar Negocio modal": { status: "FAIL", details: "Step not executed." },
    "Administrar Negocios view": { status: "FAIL", details: "Step not executed." },
    "Información General": { status: "FAIL", details: "Step not executed." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Step not executed." },
    "Tus Negocios": { status: "FAIL", details: "Step not executed." },
    "Términos y Condiciones": { status: "FAIL", details: "Step not executed." },
    "Política de Privacidad": { status: "FAIL", details: "Step not executed." }
  };
}

async function writeFinalReport(evidenceDir: string, report: FinalReport): Promise<void> {
  const reportPath = path.join(evidenceDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
}

async function openLegalLinkAndValidate(
  page: Page,
  linkText: string,
  headingText: string,
  evidenceDir: string,
  report: FinalReport,
  reportKey: "Términos y Condiciones" | "Política de Privacidad"
): Promise<void> {
  const appPage = page;
  const context = page.context();
  const originUrl = appPage.url();

  const link = await firstVisible([
    appPage.getByRole("link", { name: new RegExp(linkText, "i") }),
    appPage.getByText(new RegExp(linkText, "i"))
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickAndWait(appPage, link);
  const popupPage = await popupPromise;

  const targetPage = popupPage ?? appPage;
  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);

  const heading = await firstVisible([
    targetPage.getByRole("heading", { name: new RegExp(headingText, "i") }),
    targetPage.getByText(new RegExp(headingText, "i"))
  ]);
  await expect(heading).toBeVisible();

  const bodyText = await targetPage.locator("body").innerText();
  expect(bodyText.trim().length).toBeGreaterThan(120);

  const screenshot = await captureCheckpoint(
    targetPage,
    evidenceDir,
    reportKey === "Términos y Condiciones" ? "step8-terminos.png" : "step9-politica.png",
    true
  );

  report[reportKey] = {
    status: "PASS",
    screenshot,
    url: targetPage.url(),
    details: `${headingText} validated with visible legal text.`
  };

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
  } else if (appPage.url() !== originUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report = createInitialReport();
  const errors: string[] = [];
  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const evidenceDir = path.join(process.cwd(), "saleads-evidence", runId);
  await ensureDir(evidenceDir);

  try {
    const configuredLoginUrl =
      process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;

    if (configuredLoginUrl) {
      await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No login page available. Set SALEADS_LOGIN_URL/SALEADS_BASE_URL/BASE_URL or pre-open the SaleADS login page."
      );
    }

    const loginButton = await firstVisible([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google/i)
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 9000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popupPage = await popupPromise;

    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      const accountOption = popupPage.getByText(ACCOUNT_EMAIL, { exact: true }).first();
      if (await isVisible(accountOption)) {
        await accountOption.click();
      }
      await popupPage.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => undefined);
    } else {
      const accountOption = page.getByText(ACCOUNT_EMAIL, { exact: true }).first();
      if (await isVisible(accountOption)) {
        await accountOption.click();
        await waitForUi(page);
      }
    }

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/Negocio/i)
    ]);
    await expect(sidebar).toBeVisible({ timeout: 60000 });
    const dashboardShot = await captureCheckpoint(page, evidenceDir, "step1-dashboard.png", true);
    report.Login = {
      status: "PASS",
      screenshot: dashboardShot,
      details: "Main app interface loaded and sidebar navigation is visible."
    };
  } catch (error) {
    const screenshot = await captureCheckpoint(page, evidenceDir, "step1-login-failure.png", true).catch(
      () => undefined
    );
    report.Login = {
      status: "FAIL",
      screenshot,
      details: error instanceof Error ? error.message : "Unknown login failure."
    };
    errors.push(`Login: ${report.Login.details}`);
  }

  try {
    const negocioSection = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await firstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i)
    ]);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    const menuShot = await captureCheckpoint(page, evidenceDir, "step2-mi-negocio-menu.png", false);

    report["Mi Negocio menu"] = {
      status: "PASS",
      screenshot: menuShot,
      details: "Mi Negocio menu expanded with Agregar/Administrar options."
    };
  } catch (error) {
    const screenshot = await captureCheckpoint(page, evidenceDir, "step2-menu-failure.png", true).catch(
      () => undefined
    );
    report["Mi Negocio menu"] = {
      status: "FAIL",
      screenshot,
      details: error instanceof Error ? error.message : "Unable to validate Mi Negocio menu."
    };
    errors.push(`Mi Negocio menu: ${report["Mi Negocio menu"].details}`);
  }

  try {
    const agregarNegocio = await firstVisible([
      page.getByRole("button", { name: /Agregar Negocio/i }),
      page.getByRole("link", { name: /Agregar Negocio/i }),
      page.getByText(/Agregar Negocio/i)
    ]);
    await clickAndWait(page, agregarNegocio);

    const dialog = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
    await expect(dialog).toBeVisible({ timeout: 15000 });
    await expect(dialog.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(dialog.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(dialog.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(dialog.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(dialog.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    const nameField = dialog.getByLabel(/Nombre del Negocio/i).or(dialog.getByPlaceholder(/Nombre del Negocio/i));
    if (await isVisible(nameField)) {
      await nameField.fill("Negocio Prueba Automatización");
    }

    const modalShot = await captureCheckpoint(page, evidenceDir, "step3-agregar-negocio-modal.png", false);
    await clickAndWait(page, dialog.getByRole("button", { name: /Cancelar/i }));

    report["Agregar Negocio modal"] = {
      status: "PASS",
      screenshot: modalShot,
      details: "Crear Nuevo Negocio modal validated and closed with Cancelar."
    };
  } catch (error) {
    const screenshot = await captureCheckpoint(page, evidenceDir, "step3-modal-failure.png", true).catch(
      () => undefined
    );
    report["Agregar Negocio modal"] = {
      status: "FAIL",
      screenshot,
      details: error instanceof Error ? error.message : "Unable to validate Agregar Negocio modal."
    };
    errors.push(`Agregar Negocio modal: ${report["Agregar Negocio modal"].details}`);
  }

  try {
    const administrarNegocios = await firstVisible([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i)
    ]);
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    const accountShot = await captureCheckpoint(page, evidenceDir, "step4-administrar-negocios.png", true);
    report["Administrar Negocios view"] = {
      status: "PASS",
      screenshot: accountShot,
      details: "Account page sections are visible."
    };
  } catch (error) {
    const screenshot = await captureCheckpoint(page, evidenceDir, "step4-administrar-failure.png", true).catch(
      () => undefined
    );
    report["Administrar Negocios view"] = {
      status: "FAIL",
      screenshot,
      details: error instanceof Error ? error.message : "Unable to open Administrar Negocios."
    };
    errors.push(`Administrar Negocios view: ${report["Administrar Negocios view"].details}`);
  }

  try {
    const bodyText = await page.locator("body").innerText();
    expect(bodyText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    report["Información General"] = {
      status: "PASS",
      details: "Detected user data, email, BUSINESS PLAN and Cambiar Plan."
    };
  } catch (error) {
    report["Información General"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Información General validation failed."
    };
    errors.push(`Información General: ${report["Información General"].details}`);
  }

  try {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    report["Detalles de la Cuenta"] = {
      status: "PASS",
      details: "Detalles de la Cuenta section values are visible."
    };
  } catch (error) {
    report["Detalles de la Cuenta"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Detalles de la Cuenta validation failed."
    };
    errors.push(`Detalles de la Cuenta: ${report["Detalles de la Cuenta"].details}`);
  }

  try {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    report["Tus Negocios"] = {
      status: "PASS",
      details: "Tus Negocios list, add button and quota text are visible."
    };
  } catch (error) {
    report["Tus Negocios"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Tus Negocios validation failed."
    };
    errors.push(`Tus Negocios: ${report["Tus Negocios"].details}`);
  }

  try {
    await openLegalLinkAndValidate(
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      evidenceDir,
      report,
      "Términos y Condiciones"
    );
  } catch (error) {
    report["Términos y Condiciones"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Términos y Condiciones validation failed."
    };
    errors.push(`Términos y Condiciones: ${report["Términos y Condiciones"].details}`);
  }

  try {
    await openLegalLinkAndValidate(
      page,
      "Política de Privacidad",
      "Política de Privacidad",
      evidenceDir,
      report,
      "Política de Privacidad"
    );
  } catch (error) {
    report["Política de Privacidad"] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : "Política de Privacidad validation failed."
    };
    errors.push(`Política de Privacidad: ${report["Política de Privacidad"].details}`);
  }

  await writeFinalReport(evidenceDir, report);
  console.table(report);

  expect(errors, `One or more SaleADS workflow validations failed.\n${errors.join("\n")}`).toEqual([]);
});
