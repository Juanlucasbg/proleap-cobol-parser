import { expect, Locator, Page, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

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

type StepResult = {
  status: "PASS" | "FAIL";
  details?: string;
  finalUrl?: string;
};

const REPORT_FIELDS: ReportField[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
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

async function clickByVisibleText(page: Page, labels: string[]): Promise<void> {
  for (const label of labels) {
    const regex = new RegExp(escapeRegex(label), "i");
    const locator = await firstVisible([
      page.getByRole("button", { name: regex }),
      page.getByRole("link", { name: regex }),
      page.getByRole("menuitem", { name: regex }),
      page.getByRole("tab", { name: regex }),
      page.getByText(regex)
    ]);

    if (locator) {
      await locator.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Could not find a visible element with labels: ${labels.join(", ")}`);
}

async function expectAnyVisible(locators: Locator[], errorMessage: string): Promise<Locator> {
  const locator = await firstVisible(locators);
  if (!locator) {
    throw new Error(errorMessage);
  }
  return locator;
}

async function captureScreenshot(page: Page, filePath: string, fullPage = false): Promise<void> {
  await page.screenshot({ path: filePath, fullPage });
}

function cleanError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL ?? process.env.BASE_URL;
  if (!loginUrl) {
    throw new Error("Missing SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) environment variable.");
  }

  const artifactsDir = path.resolve("artifacts", "saleads-mi-negocio");
  const screenshotsDir = path.join(artifactsDir, "screenshots");
  const reportPath = path.join(artifactsDir, "final-report.json");
  await mkdir(screenshotsDir, { recursive: true });

  const report: Record<ReportField, StepResult> = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }])
  ) as Record<ReportField, StepResult>;

  async function runStep(field: ReportField, action: () => Promise<void>): Promise<void> {
    try {
      await action();
      report[field] = { status: "PASS", finalUrl: report[field].finalUrl };
    } catch (error) {
      report[field] = {
        status: "FAIL",
        details: cleanError(error),
        finalUrl: report[field].finalUrl
      };
    }
  }

  await runStep("Login", async () => {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
    await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google"
    ]);
    const popup = await popupPromise;

    const authPage = popup ?? page;
    await waitForUi(authPage);

    const accountCandidate = authPage.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")).first();
    if (await accountCandidate.isVisible({ timeout: 8_000 }).catch(() => false)) {
      await accountCandidate.click();
      await waitForUi(authPage);
    }

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => undefined);
    }

    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 90_000 });
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });

    await captureScreenshot(page, path.join(screenshotsDir, "01-dashboard-loaded.png"));
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, ["Negocio"]);
    await clickByVisibleText(page, ["Mi Negocio"]);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20_000 });

    await captureScreenshot(page, path.join(screenshotsDir, "02-mi-negocio-menu-expanded.png"));
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, ["Agregar Negocio"]);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 20_000 });

    await expectAnyVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input").filter({ hasText: /Nombre del Negocio/i })
      ],
      "Input field 'Nombre del Negocio' is not visible."
    );

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 20_000 });
    await captureScreenshot(page, path.join(screenshotsDir, "03-agregar-negocio-modal.png"));

    const businessNameInput = await expectAnyVisible(
      [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)],
      "Could not find 'Nombre del Negocio' input for optional typing."
    );
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, ["Cancelar"]);
  });

  await runStep("Administrar Negocios view", async () => {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, ["Mi Negocio"]);
    }

    await clickByVisibleText(page, ["Administrar Negocios"]);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 30_000 });

    await captureScreenshot(page, path.join(screenshotsDir, "04-administrar-negocios-view.png"), true);
  });

  await runStep("Información General", async () => {
    await expectAnyVisible(
      [page.getByText(/@/).first(), page.getByText(/gmail\.com/i).first()],
      "User email is not visible."
    );
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 20_000 });

    const nameLocator = await firstVisible([
      page.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")),
      page.locator("h1, h2, h3, p, span").filter({ hasText: /[A-Za-z]{2,}\s+[A-Za-z]{2,}/ }).first()
    ]);

    if (!nameLocator) {
      throw new Error("User name is not visible.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
  });

  async function validateLegalLink(
    reportField: "Términos y Condiciones" | "Política de Privacidad",
    linkText: string,
    headingText: string,
    screenshotName: string
  ): Promise<void> {
    await runStep(reportField, async () => {
      const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
      await clickByVisibleText(page, [linkText]);
      const popup = await popupPromise;
      const legalPage = popup ?? page;

      await waitForUi(legalPage);
      await expect(legalPage.getByText(new RegExp(escapeRegex(headingText), "i")).first()).toBeVisible({
        timeout: 30_000
      });
      await expect(legalPage.locator("body")).toContainText(/\S+/, { timeout: 30_000 });

      report[reportField].finalUrl = legalPage.url();
      await captureScreenshot(legalPage, path.join(screenshotsDir, screenshotName), true);

      if (popup) {
        await popup.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
        await waitForUi(page);
      }
    });
  }

  await validateLegalLink(
    "Términos y Condiciones",
    "Términos y Condiciones",
    "Términos y Condiciones",
    "05-terminos-y-condiciones.png"
  );
  await validateLegalLink(
    "Política de Privacidad",
    "Política de Privacidad",
    "Política de Privacidad",
    "06-politica-de-privacidad.png"
  );

  await writeFile(
    reportPath,
    JSON.stringify(
      {
        name: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        loginUrl,
        results: REPORT_FIELDS.map((field) => ({
          field,
          status: report[field].status,
          details: report[field].details,
          finalUrl: report[field].finalUrl
        }))
      },
      null,
      2
    ),
    "utf8"
  );

  console.log("SaleADS Mi Negocio final report:");
  for (const field of REPORT_FIELDS) {
    const result = report[field];
    const details = result.details ? ` | ${result.details}` : "";
    const finalUrl = result.finalUrl ? ` | URL: ${result.finalUrl}` : "";
    console.log(`- ${field}: ${result.status}${details}${finalUrl}`);
  }
  console.log(`JSON report saved to: ${reportPath}`);

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failedFields,
    `The following validations failed: ${failedFields.join(", ")}`
  ).toEqual([]);
});
