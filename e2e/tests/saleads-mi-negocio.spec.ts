import fs from "fs";
import path from "path";
import { expect, Locator, Page, test } from "@playwright/test";

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

type StepStatus = "PASS" | "FAIL";

type StepReport = Record<
  ReportField,
  {
    status: StepStatus;
    details?: string;
    finalUrl?: string;
  }
>;

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const START_URL =
  process.env.SALEADS_LOGIN_URL ??
  process.env.SALEADS_BASE_URL ??
  process.env.BASE_URL;

const artifactsDir = path.join(process.cwd(), "e2e", "artifacts");
const screenshotsDir = path.join(artifactsDir, "screenshots");
const reportsDir = path.join(artifactsDir, "report");

function sanitizeFilename(input: string): string {
  return input
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {
    // Some pages keep long polling connections open.
  });
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToLoad(page);
}

async function takeCheckpoint(
  page: Page,
  label: string,
  fullPage = false,
): Promise<string> {
  const fileName = `${Date.now()}-${sanitizeFilename(label)}.png`;
  const screenshotPath = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });

  return screenshotPath;
}

async function getClickableByVisibleText(
  page: Page,
  text: string,
): Promise<Locator> {
  const textRegex = new RegExp(text, "i");
  const link = page.getByRole("link", { name: textRegex }).first();
  if ((await link.count()) > 0) {
    return link;
  }

  const button = page.getByRole("button", { name: textRegex }).first();
  if ((await button.count()) > 0) {
    return button;
  }

  return page.getByText(textRegex).first();
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  fs.mkdirSync(screenshotsDir, { recursive: true });
  fs.mkdirSync(reportsDir, { recursive: true });

  const report: StepReport = {
    Login: { status: "FAIL", details: "Step not executed." },
    "Mi Negocio menu": { status: "FAIL", details: "Step not executed." },
    "Agregar Negocio modal": { status: "FAIL", details: "Step not executed." },
    "Administrar Negocios view": {
      status: "FAIL",
      details: "Step not executed.",
    },
    "Información General": { status: "FAIL", details: "Step not executed." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Step not executed." },
    "Tus Negocios": { status: "FAIL", details: "Step not executed." },
    "Términos y Condiciones": { status: "FAIL", details: "Step not executed." },
    "Política de Privacidad": { status: "FAIL", details: "Step not executed." },
  };

  let accountPageUrl = "";

  const runStep = async (field: ReportField, fn: () => Promise<void>) => {
    try {
      await fn();
      report[field] = { status: "PASS" };
    } catch (error) {
      report[field] = {
        status: "FAIL",
        details: error instanceof Error ? error.message : String(error),
      };
    }
  };

  await runStep("Login", async () => {
    if (page.url() === "about:blank") {
      if (!START_URL) {
        throw new Error(
          "No active SaleADS page detected. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to start on the login screen.",
        );
      }
      await page.goto(START_URL, { waitUntil: "domcontentloaded" });
    }

    await waitForUiToLoad(page);

    const appInterfaceReady = page.getByText(/Mi Negocio|Negocio/i).first();
    const loginButton = page
      .getByRole("button", {
        name: /google|sign in|iniciar sesión|inicia sesión|login|acceder/i,
      })
      .first();

    const appAlreadyLoaded = await appInterfaceReady
      .isVisible({ timeout: 8_000 })
      .catch(() => false);

    if (!appAlreadyLoaded) {
      const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(page, loginButton);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
        if (await accountOption.isVisible({ timeout: 10_000 }).catch(() => false)) {
          await accountOption.click();
        }
        await popup.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {
          // The popup may close immediately after successful account selection.
        });
      } else {
        const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
        if (await accountOption.isVisible({ timeout: 10_000 }).catch(() => false)) {
          await clickAndWait(page, accountOption);
        }
      }
    }

    await expect(page.getByText(/Mi Negocio|Negocio/i).first()).toBeVisible({
      timeout: 60_000,
    });
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await takeCheckpoint(page, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await getClickableByVisibleText(page, "Negocio");
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await getClickableByVisibleText(page, "Mi Negocio");
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: false }).first()).toBeVisible();
    await takeCheckpoint(page, "02-mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocioMenuOption = await getClickableByVisibleText(page, "Agregar Negocio");
    await clickAndWait(page, agregarNegocioMenuOption);

    await expect(page.getByText("Crear Nuevo Negocio", { exact: false })).toBeVisible();
    const nombreNegocioInput = page.getByLabel("Nombre del Negocio", { exact: false });
    await expect(nombreNegocioInput).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

    await nombreNegocioInput.click();
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await takeCheckpoint(page, "03-crear-nuevo-negocio-modal");

    await clickAndWait(page, page.getByRole("button", { name: "Cancelar" }));
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocioOption = await getClickableByVisibleText(page, "Mi Negocio");
    if (await miNegocioOption.isVisible().catch(() => false)) {
      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegocios = await getClickableByVisibleText(page, "Administrar Negocios");
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText("Información General", { exact: false })).toBeVisible();
    await expect(page.getByText("Detalles de la Cuenta", { exact: false })).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: false })).toBeVisible();
    await expect(page.getByText("Sección Legal", { exact: false })).toBeVisible();
    await takeCheckpoint(page, "04-administrar-negocios-view", true);
    accountPageUrl = page.url();
  });

  await runStep("Información General", async () => {
    await expect(page.getByText("Información General", { exact: false })).toBeVisible();
    await expect(
      page.getByText(/juanlucasbarbiergarzon@gmail.com|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
    ).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible();
    await expect(page.getByText("Estado activo", { exact: false })).toBeVisible();
    await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText("Tus Negocios", { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    const target = await getClickableByVisibleText(page, "Términos y Condiciones");
    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);

    await clickAndWait(page, target);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {
      // Some legal pages continue loading assets in the background.
    });

    const heading = legalPage
      .getByRole("heading", { name: /Términos y Condiciones/i })
      .first();
    if ((await heading.count()) > 0) {
      await expect(heading).toBeVisible();
    } else {
      await expect(legalPage.getByText("Términos y Condiciones", { exact: false })).toBeVisible();
    }

    await expect(legalPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóú]{20,}/);
    report["Términos y Condiciones"].finalUrl = legalPage.url();
    await takeCheckpoint(legalPage, "08-terminos-y-condiciones", true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else if (accountPageUrl) {
      await page.goto(accountPageUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    }
  });

  await runStep("Política de Privacidad", async () => {
    const target = await getClickableByVisibleText(page, "Política de Privacidad");
    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);

    await clickAndWait(page, target);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {
      // Some legal pages continue loading assets in the background.
    });

    const heading = legalPage
      .getByRole("heading", { name: /Política de Privacidad/i })
      .first();
    if ((await heading.count()) > 0) {
      await expect(heading).toBeVisible();
    } else {
      await expect(legalPage.getByText("Política de Privacidad", { exact: false })).toBeVisible();
    }

    await expect(legalPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóú]{20,}/);
    report["Política de Privacidad"].finalUrl = legalPage.url();
    await takeCheckpoint(legalPage, "09-politica-de-privacidad", true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else if (accountPageUrl) {
      await page.goto(accountPageUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    }
  });

  const finalReportPath = path.join(reportsDir, "saleads-mi-negocio-final-report.json");
  fs.writeFileSync(finalReportPath, JSON.stringify(report, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  // Step 10: final pass/fail summary across all validations.
  // eslint-disable-next-line no-console
  console.log("SaleADS Mi Negocio workflow report:", report);

  const failedSteps = Object.entries(report).filter(([, value]) => value.status === "FAIL");
  expect(
    failedSteps,
    `One or more SaleADS validations failed:\n${JSON.stringify(failedSteps, null, 2)}`,
  ).toHaveLength(0);
});
