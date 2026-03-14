import { expect, type Locator, type Page, test, type TestInfo } from "@playwright/test";
import { promises as fs } from "node:fs";

type StepStatus = "PASS" | "FAIL";

type ReportEntry = {
  status: StepStatus;
  details: string;
  url?: string;
};

type WorkflowReport = Record<string, ReportEntry>;

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

const LEGAL_MIN_TEXT_LENGTH = 120;

function initReport(): WorkflowReport {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        status: "FAIL",
        details: "Step did not complete.",
      } satisfies ReportEntry,
    ]),
  );
}

function setResult(report: WorkflowReport, field: string, status: StepStatus, details: string, url?: string): void {
  report[field] = { status, details, url };
}

async function pause(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 30_000 });
  await page.waitForLoadState("networkidle", { timeout: 30_000 }).catch(() => {
    // Some pages keep active requests; domcontentloaded is enough in that case.
  });
}

async function firstVisibleLocator(candidates: Locator[], timeoutMs = 15_000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await pause(300);
  }

  throw new Error("No visible candidate locator was found in time.");
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false,
): Promise<string> {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(fileName, { path, contentType: "image/png" });
  return path;
}

async function handleGoogleAccountSelection(googlePage: Page): Promise<void> {
  await waitForUiLoad(googlePage);

  const accountCandidate = await firstVisibleLocator(
    [
      googlePage.getByText(ACCOUNT_EMAIL, { exact: true }),
      googlePage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
      googlePage.locator(`text=${ACCOUNT_EMAIL}`),
    ],
    20_000,
  );

  await accountCandidate.click();
  await waitForUiLoad(googlePage);
}

async function ensureSidebarVisible(page: Page): Promise<void> {
  const sidebarCandidate = await firstVisibleLocator(
    [
      page.getByText(/Mi Negocio/i),
      page.getByText(/^Negocio$/i),
      page.getByRole("navigation"),
      page.locator("aside"),
    ],
    60_000,
  );
  await expect(sidebarCandidate).toBeVisible();
}

async function openLegalPageAndValidate(
  page: Page,
  linkTextRegex: RegExp,
  headingRegex: RegExp,
  screenshotName: string,
  reportField: string,
  report: WorkflowReport,
  testInfo: TestInfo,
): Promise<void> {
  const legalLink = await firstVisibleLocator(
    [
      page.getByRole("link", { name: linkTextRegex }),
      page.getByText(linkTextRegex),
    ],
    20_000,
  );

  const maybeNewPage = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await legalLink.click();
  await waitForUiLoad(page).catch(() => {
    // In a new-tab flow, the original tab might not navigate.
  });

  const newPage = await maybeNewPage;
  const legalPage = newPage ?? page;
  const openedNewTab = Boolean(newPage);

  await waitForUiLoad(legalPage);

  const headingCandidate = await firstVisibleLocator(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex),
    ],
    30_000,
  );
  await expect(headingCandidate).toBeVisible();

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  if (bodyText.length < LEGAL_MIN_TEXT_LENGTH) {
    throw new Error(`Legal content text seems too short (${bodyText.length} chars).`);
  }

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();
  setResult(report, reportField, "PASS", "Legal page opened and validated successfully.", finalUrl);

  if (openedNewTab && newPage) {
    await newPage.close();
    await page.bringToFront();
    await waitForUiLoad(page).catch(() => {
      // Returning focus is enough when parent page does not reload.
    });
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {
      // Not all same-tab legal routes support history back.
    });
    await waitForUiLoad(page).catch(() => {
      // Best-effort return to app state.
    });
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login and validate Mi Negocio workflow", async ({ page }, testInfo) => {
    const report = initReport();
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL;

    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    }

    // Step 1: Login with Google
    try {
      const loginButton = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n|ingresar|login/i }),
          page.getByText(/google/i),
        ],
        30_000,
      );

      const maybePopup = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await loginButton.click();
      await waitForUiLoad(page).catch(() => {
        // OAuth redirects can keep network active.
      });

      const popup = await maybePopup;
      if (popup) {
        await handleGoogleAccountSelection(popup);
        await popup.waitForClose({ timeout: 60_000 }).catch(() => {
          // Some flows keep popup open after redirect.
        });
      } else if (page.url().includes("accounts.google.com")) {
        await handleGoogleAccountSelection(page);
      }

      await ensureSidebarVisible(page);
      await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
      setResult(report, "Login", "PASS", "Login completed and sidebar is visible.");
    } catch (error) {
      setResult(report, "Login", "FAIL", `Login validation failed: ${String(error)}`);
    }

    // Step 2: Open Mi Negocio menu
    try {
      const negocioSection = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i),
          page.getByText(/Negocio/i),
        ],
        30_000,
      );
      await negocioSection.click();
      await waitForUiLoad(page);

      const miNegocioOption = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByRole("link", { name: /Mi Negocio/i }),
          page.getByText(/Mi Negocio/i),
        ],
        20_000,
      );
      await miNegocioOption.click();
      await waitForUiLoad(page);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20_000 });
      await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", true);
      setResult(report, "Mi Negocio menu", "PASS", "Mi Negocio menu expanded with both submenu options.");
    } catch (error) {
      setResult(report, "Mi Negocio menu", "FAIL", `Menu validation failed: ${String(error)}`);
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const addBusinessMenuOption = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /Agregar Negocio/i }),
          page.getByRole("link", { name: /Agregar Negocio/i }),
          page.getByText(/Agregar Negocio/i),
        ],
        20_000,
      );
      await addBusinessMenuOption.click();
      await waitForUiLoad(page);

      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 20_000 });

      const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatizacion");
      await page.getByRole("button", { name: /Cancelar/i }).first().click();
      await waitForUiLoad(page);

      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", true);
      setResult(report, "Agregar Negocio modal", "PASS", "Agregar Negocio modal validated successfully.");
    } catch (error) {
      setResult(report, "Agregar Negocio modal", "FAIL", `Agregar Negocio modal failed: ${String(error)}`);
    }

    // Step 4: Open Administrar Negocios
    try {
      const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        const miNegocioToggle = await firstVisibleLocator(
          [
            page.getByRole("button", { name: /Mi Negocio/i }),
            page.getByText(/Mi Negocio/i),
          ],
          15_000,
        );
        await miNegocioToggle.click();
        await waitForUiLoad(page);
      }

      const administrarNegocios = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /Administrar Negocios/i }),
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i),
        ],
        20_000,
      );
      await administrarNegocios.click();
      await waitForUiLoad(page);

      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 30_000 });

      await captureCheckpoint(page, testInfo, "04-administrar-negocios-page.png", true);
      setResult(report, "Administrar Negocios view", "PASS", "Administrar Negocios sections are visible.");
    } catch (error) {
      setResult(report, "Administrar Negocios view", "FAIL", `Administrar Negocios validation failed: ${String(error)}`);
    }

    // Step 5: Validate Información General
    try {
      const emailVisible = await firstVisibleLocator(
        [
          page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
          page.getByText(new RegExp(ACCOUNT_EMAIL, "i")),
        ],
        20_000,
      );
      await expect(emailVisible).toBeVisible();

      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 20_000 });

      const nameCandidate = await firstVisibleLocator(
        [
          page.locator("h1, h2, h3").filter({ hasNotText: /Administrar Negocios|Informaci[oó]n General|Detalles de la Cuenta|Tus Negocios|Secci[oó]n Legal/i }),
          page.getByText(/Nombre/i),
        ],
        15_000,
      );
      const nameText = (await nameCandidate.textContent())?.trim() ?? "";
      if (nameText.length < 2) {
        throw new Error("User name text was not detected.");
      }

      setResult(report, "Información General", "PASS", "Información General fields validated.");
    } catch (error) {
      setResult(report, "Información General", "FAIL", `Información General validation failed: ${String(error)}`);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
      setResult(report, "Detalles de la Cuenta", "PASS", "Detalles de la Cuenta fields validated.");
    } catch (error) {
      setResult(report, "Detalles de la Cuenta", "FAIL", `Detalles de la Cuenta validation failed: ${String(error)}`);
    }

    // Step 7: Validate Tus Negocios
    try {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
      setResult(report, "Tus Negocios", "PASS", "Tus Negocios section validated.");
    } catch (error) {
      setResult(report, "Tus Negocios", "FAIL", `Tus Negocios validation failed: ${String(error)}`);
    }

    // Step 8: Validate Términos y Condiciones
    try {
      await openLegalPageAndValidate(
        page,
        /T[eé]rminos y Condiciones/i,
        /T[eé]rminos y Condiciones/i,
        "08-terminos-y-condiciones.png",
        "Términos y Condiciones",
        report,
        testInfo,
      );
    } catch (error) {
      setResult(report, "Términos y Condiciones", "FAIL", `Términos y Condiciones failed: ${String(error)}`);
    }

    // Step 9: Validate Política de Privacidad
    try {
      await openLegalPageAndValidate(
        page,
        /Pol[ií]tica de Privacidad/i,
        /Pol[ií]tica de Privacidad/i,
        "09-politica-de-privacidad.png",
        "Política de Privacidad",
        report,
        testInfo,
      );
    } catch (error) {
      setResult(report, "Política de Privacidad", "FAIL", `Política de Privacidad failed: ${String(error)}`);
    }

    // Step 10: Final report
    const reportPath = testInfo.outputPath("saleads_mi_negocio_workflow_report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    await testInfo.attach("saleads-mi-negocio-workflow-report", {
      path: reportPath,
      contentType: "application/json",
    });

    // Printed report for CI logs.
    // eslint-disable-next-line no-console
    console.log(`Final workflow report:\n${JSON.stringify(report, null, 2)}`);

    const failedSections = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
    expect(
      failedSections,
      `Expected all steps to PASS, but these failed: ${failedSections.join(", ") || "none"}`,
    ).toEqual([]);
  });
});
