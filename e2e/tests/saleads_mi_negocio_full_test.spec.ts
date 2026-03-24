import { expect, Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

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

type FinalReport = Record<ReportField, StepStatus>;

const SCREENSHOTS_DIR = path.resolve(__dirname, "..", "artifacts", "screenshots");
const REPORT_PATH = path.resolve(
  __dirname,
  "..",
  "artifacts",
  "saleads_mi_negocio_full_test.report.json"
);

const googleAccountEmail =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";

const legalPageWaitTimeoutMs = Number(process.env.SALEADS_LEGAL_WAIT_TIMEOUT_MS ?? "45000");
const uiTimeoutMs = Number(process.env.SALEADS_UI_TIMEOUT_MS ?? "15000");
const typeInModal = (process.env.SALEADS_MODAL_FILL_NAME ?? "true").toLowerCase() === "true";
const configuredLoginUrl =
  process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL ?? process.env.PLAYWRIGHT_BASE_URL;

const report: FinalReport = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL",
};

const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};

function ensureArtifactsDir() {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

async function capture(page: Page, name: string, fullPage = false) {
  ensureArtifactsDir();
  await page.screenshot({
    path: path.join(SCREENSHOTS_DIR, `${name}.png`),
    fullPage,
  });
}

async function waitForUiLoad(page: Page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

function softMarkStepFailure(reportKey: ReportField, details: string) {
  report[reportKey] = "FAIL";
  test.info().annotations.push({
    type: "step-failure",
    description: `${reportKey}: ${details}`,
  });
}

async function clickByVisibleText(page: Page, visibleText: string) {
  const target = page.getByText(visibleText, { exact: true }).first();
  await expect(target).toBeVisible({ timeout: uiTimeoutMs });
  await target.click();
  await waitForUiLoad(page);
}

async function validateLegalPageFromLink(
  page: Page,
  linkText: string,
  expectedHeading: "Términos y Condiciones" | "Política de Privacidad"
) {
  const link = page.getByText(linkText, { exact: true }).first();
  await expect(link).toBeVisible({ timeout: uiTimeoutMs });

  const [maybeNewPage] = await Promise.all([
    page.context().waitForEvent("page", { timeout: 4000 }).catch(() => null),
    link.click(),
  ]);

  const targetPage = maybeNewPage ?? page;
  if (maybeNewPage) {
    await maybeNewPage.waitForLoadState("domcontentloaded", { timeout: legalPageWaitTimeoutMs });
  } else {
    await waitForUiLoad(page);
  }

  await expect(
    targetPage.getByRole("heading", { name: expectedHeading }).first(),
    `Expected heading '${expectedHeading}'`
  ).toBeVisible({ timeout: legalPageWaitTimeoutMs });
  await expect(
    targetPage.locator("body"),
    `Expected legal content to be visible on '${expectedHeading}' page`
  ).toContainText(/\S+/, { timeout: legalPageWaitTimeoutMs });

  await capture(
    targetPage,
    expectedHeading.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/\s+/g, "_")
  );

  const finalUrl = targetPage.url();
  legalUrls[expectedHeading] = finalUrl;
  test.info().annotations.push({
    type: "final-url",
    description: `${expectedHeading}: ${finalUrl}`,
  });

  if (maybeNewPage) {
    await maybeNewPage.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  }
}

async function getAppPageAfterGoogleSignIn(page: Page, popup: Page | null): Promise<Page> {
  // Prefer the page where app shell (sidebar/nav) becomes visible first.
  const candidates = [page, popup].filter(Boolean) as Page[];

  for (const candidate of candidates) {
    try {
      await candidate.waitForLoadState("domcontentloaded", { timeout: uiTimeoutMs });
    } catch {
      // Ignore isolated load-state failures and try content-based checks.
    }

    const appShell = candidate.locator("aside, nav").first();
    if (await appShell.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  // Fallback: wait a bit longer on each candidate before failing.
  for (const candidate of candidates) {
    const appShell = candidate.locator("aside, nav").first();
    if (await appShell.isVisible({ timeout: 60000 }).catch(() => false)) {
      return candidate;
    }
  }

  throw new Error("Could not detect main application interface after Google login.");
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio module workflow", async ({ page }) => {
    let appPage = page;

    if (configuredLoginUrl) {
      await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    }

    // Step 1: Login with Google
    await test.step("Step 1 - Login with Google", async () => {
      try {
        await waitForUiLoad(appPage);

        const signInButton = appPage
          .getByRole("button", { name: /sign in with google|google|iniciar.*google/i })
          .first();
        await expect(signInButton).toBeVisible({ timeout: uiTimeoutMs });

        const [popup] = await Promise.all([
          appPage.context().waitForEvent("page", { timeout: 10000 }).catch(() => null),
          signInButton.click(),
        ]);

        if (popup) {
          await popup.waitForLoadState("domcontentloaded", { timeout: uiTimeoutMs });
          const accountOption = popup.getByText(googleAccountEmail, { exact: true }).first();
          if (await accountOption.isVisible().catch(() => false)) {
            await accountOption.click();
          }
        } else {
          const accountOption = appPage.getByText(googleAccountEmail, { exact: true }).first();
          if (await accountOption.isVisible().catch(() => false)) {
            await accountOption.click();
          }
        }

        appPage = await getAppPageAfterGoogleSignIn(appPage, popup);
        await appPage.bringToFront();
        await waitForUiLoad(appPage);
        await expect(appPage.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
        await expect(appPage.locator("body")).toContainText(/negocio/i, { timeout: 60000 });

        await capture(appPage, "01_dashboard_loaded");
        report.Login = "PASS";
      } catch (error) {
        softMarkStepFailure("Login", String(error));
        throw error;
      }
    });

    // Step 2: Open Mi Negocio menu
    await test.step("Step 2 - Open Mi Negocio menu", async () => {
      try {
        await clickByVisibleText(appPage, "Negocio");
        await clickByVisibleText(appPage, "Mi Negocio");

        await expect(appPage.getByText("Agregar Negocio", { exact: true })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByText("Administrar Negocios", { exact: true })).toBeVisible({
          timeout: uiTimeoutMs,
        });

        await capture(appPage, "02_mi_negocio_menu_expanded");
        report["Mi Negocio menu"] = "PASS";
      } catch (error) {
        softMarkStepFailure("Mi Negocio menu", String(error));
        throw error;
      }
    });

    // Step 3: Validate Agregar Negocio modal
    await test.step("Step 3 - Validate Agregar Negocio modal", async () => {
      try {
        await clickByVisibleText(appPage, "Agregar Negocio");

        const modalTitle = appPage.getByText("Crear Nuevo Negocio", { exact: true });
        await expect(modalTitle).toBeVisible({ timeout: uiTimeoutMs });
        const nameInput = appPage
          .getByLabel("Nombre del Negocio")
          .or(appPage.getByPlaceholder("Nombre del Negocio"))
          .first();
        await expect(nameInput).toBeVisible({ timeout: uiTimeoutMs });
        await expect(appPage.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByRole("button", { name: "Cancelar" })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByRole("button", { name: "Crear Negocio" })).toBeVisible({
          timeout: uiTimeoutMs,
        });

        await capture(appPage, "03_agregar_negocio_modal");

        if (typeInModal) {
          await nameInput.click();
          await nameInput.fill("Negocio Prueba Automatización");
        }
        await appPage.getByRole("button", { name: "Cancelar" }).click();
        await waitForUiLoad(appPage);

        report["Agregar Negocio modal"] = "PASS";
      } catch (error) {
        softMarkStepFailure("Agregar Negocio modal", String(error));
        throw error;
      }
    });

    // Step 4: Open Administrar Negocios
    await test.step("Step 4 - Open Administrar Negocios", async () => {
      try {
        if (
          !(await appPage
            .getByText("Administrar Negocios", { exact: true })
            .isVisible()
            .catch(() => false))
        ) {
          await clickByVisibleText(appPage, "Mi Negocio");
        }
        await clickByVisibleText(appPage, "Administrar Negocios");

        await expect(appPage.getByText("Información General", { exact: true })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByText("Tus Negocios", { exact: true })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByText("Sección Legal", { exact: true })).toBeVisible({
          timeout: uiTimeoutMs,
        });

        await capture(appPage, "04_administrar_negocios_view", true);
        report["Administrar Negocios view"] = "PASS";
      } catch (error) {
        softMarkStepFailure("Administrar Negocios view", String(error));
        throw error;
      }
    });

    // Step 5: Validate Información General
    await test.step("Step 5 - Validate Información General", async () => {
      try {
        const body = appPage.locator("body");
        await expect(appPage.getByText("BUSINESS PLAN", { exact: true })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByRole("button", { name: "Cambiar Plan" })).toBeVisible({
          timeout: uiTimeoutMs,
        });

        // Environment-independent checks: email pattern and person-like name pattern.
        await expect(body).toContainText(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,}/, {
          timeout: uiTimeoutMs,
        });
        await expect(body).toContainText(/\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\b/, {
          timeout: uiTimeoutMs,
        });

        report["Información General"] = "PASS";
      } catch (error) {
        softMarkStepFailure("Información General", String(error));
        throw error;
      }
    });

    // Step 6: Validate Detalles de la Cuenta
    await test.step("Step 6 - Validate Detalles de la Cuenta", async () => {
      try {
        await expect(appPage.getByText("Cuenta creada", { exact: false })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByText("Estado activo", { exact: false })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByText("Idioma seleccionado", { exact: false })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        report["Detalles de la Cuenta"] = "PASS";
      } catch (error) {
        softMarkStepFailure("Detalles de la Cuenta", String(error));
        throw error;
      }
    });

    // Step 7: Validate Tus Negocios
    await test.step("Step 7 - Validate Tus Negocios", async () => {
      try {
        await expect(appPage.getByText("Tus Negocios", { exact: true })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByRole("button", { name: "Agregar Negocio" })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        await expect(appPage.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible({
          timeout: uiTimeoutMs,
        });
        report["Tus Negocios"] = "PASS";
      } catch (error) {
        softMarkStepFailure("Tus Negocios", String(error));
        throw error;
      }
    });

    // Step 8: Validate Términos y Condiciones
    await test.step("Step 8 - Validate Términos y Condiciones", async () => {
      try {
        await validateLegalPageFromLink(
          appPage,
          "Términos y Condiciones",
          "Términos y Condiciones"
        );
        report["Términos y Condiciones"] = "PASS";
      } catch (error) {
        softMarkStepFailure("Términos y Condiciones", String(error));
        throw error;
      }
    });

    // Step 9: Validate Política de Privacidad
    await test.step("Step 9 - Validate Política de Privacidad", async () => {
      try {
        await validateLegalPageFromLink(
          appPage,
          "Política de Privacidad",
          "Política de Privacidad"
        );
        report["Política de Privacidad"] = "PASS";
      } catch (error) {
        softMarkStepFailure("Política de Privacidad", String(error));
        throw error;
      }
    });
  });

  test.afterAll(async () => {
    ensureArtifactsDir();
    fs.writeFileSync(
      REPORT_PATH,
      JSON.stringify(
        {
          testName: "saleads_mi_negocio_full_test",
          generatedAt: new Date().toISOString(),
          report,
          legalUrls,
        },
        null,
        2
      ),
      "utf8"
    );
  });
});
