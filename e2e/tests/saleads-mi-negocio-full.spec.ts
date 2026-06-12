import fs from "node:fs";
import path from "node:path";
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

type StepResult = "PASS" | "FAIL" | "NOT RUN";

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

const SCREENSHOT_DIR = path.resolve(process.cwd(), "artifacts", "saleads-mi-negocio");

function createInitialReport(): Record<ReportField, StepResult> {
  return REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = "NOT RUN";
      return acc;
    },
    {} as Record<ReportField, StepResult>
  );
}

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function waitForAnyVisible(candidates: Locator[], timeoutMs: number): Promise<Locator | null> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const current = candidate.first();
      try {
        if (await current.isVisible()) {
          return current;
        }
      } catch {
        // Ignore stale or detached locator checks while polling.
      }
    }
    await delay(250);
  }

  return null;
}

async function screenshotCheckpoint(page: Page, name: string, fullPage = false): Promise<void> {
  const filePath = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
}

async function resolveSidebar(page: Page): Promise<Locator | null> {
  return waitForAnyVisible([page.locator("aside"), page.getByRole("navigation")], 20_000);
}

async function openLegalPage(params: {
  appPage: Page;
  linkPattern: RegExp;
  headingPattern: RegExp;
  screenshotName: string;
}): Promise<string> {
  const { appPage, linkPattern, headingPattern, screenshotName } = params;
  const link = await waitForAnyVisible(
    [appPage.getByRole("link", { name: linkPattern }), appPage.getByText(linkPattern)],
    20_000
  );

  if (!link) {
    throw new Error(`No se encontró el enlace legal con patrón: ${linkPattern}`);
  }

  const urlBeforeClick = appPage.url();
  const popupPromise = appPage.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);

  await link.click();
  await waitForUi(appPage);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  await legalPage.waitForLoadState("domcontentloaded");
  await waitForUi(legalPage);

  const legalHeading = await waitForAnyVisible(
    [legalPage.getByRole("heading", { name: headingPattern }), legalPage.getByText(headingPattern)],
    20_000
  );
  if (!legalHeading) {
    throw new Error(`No se encontró el encabezado legal esperado: ${headingPattern}`);
  }

  const legalText = (await legalPage.locator("body").innerText()).trim();
  if (legalText.length < 120) {
    throw new Error("El contenido legal visible es insuficiente.");
  }

  await screenshotCheckpoint(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => null);
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== urlBeforeClick) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(appPage);
  }

  return finalUrl;
}

test.describe("SaleADS Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

    const report = createInitialReport();
    const errors: string[] = [];
    const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};

    const markSection = async (field: ReportField, execute: () => Promise<void>): Promise<void> => {
      try {
        await test.step(field, execute);
        report[field] = "PASS";
      } catch (error) {
        report[field] = "FAIL";
        errors.push(`${field}: ${toErrorMessage(error)}`);
      }
    };

    const saleadsUrl = process.env.SALEADS_URL ?? process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL;
    if (!saleadsUrl) {
      throw new Error(
        "Set SALEADS_URL (or SALEADS_LOGIN_URL / BASE_URL) to the login page of the current environment."
      );
    }

    await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    await markSection("Login", async () => {
      const initialSidebar = await resolveSidebar(page);

      if (!initialSidebar) {
        const loginButton = await waitForAnyVisible(
          [
            page.getByRole("button", { name: /google/i }),
            page.getByRole("link", { name: /google/i }),
            page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i)
          ],
          30_000
        );

        if (!loginButton) {
          throw new Error("No se encontró el botón de login con Google.");
        }

        const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
        await loginButton.click();
        await waitForUi(page);

        const popup = await popupPromise;
        const googlePage = popup ?? page;
        await waitForUi(googlePage);

        const accountOption = googlePage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
        }

        if (popup) {
          await popup.waitForEvent("close", { timeout: 25_000 }).catch(() => null);
        }
      }

      const finalSidebar = await resolveSidebar(page);
      if (!finalSidebar) {
        throw new Error("No se visualiza la interfaz principal con barra lateral tras el login.");
      }

      await expect(finalSidebar).toBeVisible();
      await screenshotCheckpoint(page, "01-dashboard-loaded", false);
    });

    await markSection("Mi Negocio menu", async () => {
      const sidebar = await resolveSidebar(page);
      if (!sidebar) {
        throw new Error("No se pudo ubicar la barra lateral de navegación.");
      }

      const negocioOption = await waitForAnyVisible(
        [sidebar.getByText(/^Negocio$/i), page.getByText(/^Negocio$/i)],
        20_000
      );
      if (negocioOption) {
        await negocioOption.click();
        await waitForUi(page);
      }

      const miNegocioOption = await waitForAnyVisible(
        [sidebar.getByText(/^Mi Negocio$/i), page.getByText(/^Mi Negocio$/i)],
        20_000
      );
      if (!miNegocioOption) {
        throw new Error("No se encontró la opción 'Mi Negocio'.");
      }

      await miNegocioOption.click();
      await waitForUi(page);

      const agregarNegocio = await waitForAnyVisible(
        [sidebar.getByText(/^Agregar Negocio$/i), page.getByText(/^Agregar Negocio$/i)],
        15_000
      );
      const administrarNegocios = await waitForAnyVisible(
        [sidebar.getByText(/^Administrar Negocios$/i), page.getByText(/^Administrar Negocios$/i)],
        15_000
      );

      if (!agregarNegocio || !administrarNegocios) {
        throw new Error("El submenú de Mi Negocio no mostró todas las opciones esperadas.");
      }

      await screenshotCheckpoint(page, "02-mi-negocio-expanded", false);
    });

    await markSection("Agregar Negocio modal", async () => {
      const agregarNegocio = await waitForAnyVisible(
        [page.getByText(/^Agregar Negocio$/i), page.getByRole("button", { name: /^Agregar Negocio$/i })],
        15_000
      );

      if (!agregarNegocio) {
        throw new Error("No se encontró la opción 'Agregar Negocio'.");
      }

      await agregarNegocio.click();
      await waitForUi(page);

      const modal = await waitForAnyVisible(
        [page.getByRole("dialog"), page.locator('[role="dialog"]'), page.locator(".modal")],
        15_000
      );
      if (!modal) {
        throw new Error("No apareció el modal para crear negocio.");
      }

      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

      const businessNameInput = await waitForAnyVisible(
        [
          modal.getByLabel(/Nombre del Negocio/i),
          modal.getByPlaceholder(/Nombre del Negocio/i),
          modal.locator("input")
        ],
        10_000
      );
      if (!businessNameInput) {
        throw new Error("No se encontró el input 'Nombre del Negocio'.");
      }

      await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

      const cancelar = await waitForAnyVisible([modal.getByRole("button", { name: /^Cancelar$/i })], 8_000);
      const crear = await waitForAnyVisible([modal.getByRole("button", { name: /^Crear Negocio$/i })], 8_000);
      if (!cancelar || !crear) {
        throw new Error("No se encontraron los botones 'Cancelar' y 'Crear Negocio'.");
      }

      await screenshotCheckpoint(page, "03-agregar-negocio-modal", false);

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await cancelar.click();
      await expect(modal).not.toBeVisible({ timeout: 10_000 });
    });

    await markSection("Administrar Negocios view", async () => {
      const administrarNegociosVisible = await waitForAnyVisible([page.getByText(/^Administrar Negocios$/i)], 5_000);
      if (!administrarNegociosVisible) {
        const miNegocio = await waitForAnyVisible([page.getByText(/^Mi Negocio$/i)], 10_000);
        if (!miNegocio) {
          throw new Error("No se pudo reabrir 'Mi Negocio'.");
        }
        await miNegocio.click();
        await waitForUi(page);
      }

      const administrarNegocios = await waitForAnyVisible([page.getByText(/^Administrar Negocios$/i)], 15_000);
      if (!administrarNegocios) {
        throw new Error("No se encontró 'Administrar Negocios'.");
      }

      await administrarNegocios.click();
      await waitForUi(page);

      const informacionGeneral = await waitForAnyVisible([page.getByText(/Información General/i)], 20_000);
      const detallesCuenta = await waitForAnyVisible([page.getByText(/Detalles de la Cuenta/i)], 20_000);
      const tusNegocios = await waitForAnyVisible([page.getByText(/Tus Negocios/i)], 20_000);
      const seccionLegal = await waitForAnyVisible([page.getByText(/Sección Legal/i)], 20_000);

      if (!informacionGeneral || !detallesCuenta || !tusNegocios || !seccionLegal) {
        throw new Error("No se encontró la vista completa de Administrar Negocios.");
      }

      await screenshotCheckpoint(page, "04-administrar-negocios-account-page", true);
    });

    await markSection("Información General", async () => {
      const infoSectionHeading = await waitForAnyVisible([page.getByText(/Información General/i)], 12_000);
      if (!infoSectionHeading) {
        throw new Error("No se encontró la sección 'Información General'.");
      }

      const userNameOrLabel = await waitForAnyVisible(
        [page.getByText(/Nombre|Usuario/i), page.locator("main, body").getByText(/Nombre|Usuario/i)],
        10_000
      );
      if (!userNameOrLabel) {
        throw new Error("No hay evidencia visible de nombre de usuario.");
      }

      const userEmail = await waitForAnyVisible(
        [
          page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }),
          page.getByText(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-z]{2,}/i)
        ],
        10_000
      );
      if (!userEmail) {
        throw new Error("No se encontró email visible del usuario.");
      }

      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    });

    await markSection("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await markSection("Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();

      const businessListVisible = await waitForAnyVisible(
        [
          page.locator("section:has-text('Tus Negocios') li"),
          page.locator("section:has-text('Tus Negocios') tr"),
          page.locator("section:has-text('Tus Negocios') [class*='card']"),
          page.locator("section:has-text('Tus Negocios') [class*='business']")
        ],
        10_000
      );

      if (!businessListVisible) {
        throw new Error("No se encontró el listado de negocios.");
      }

      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    });

    await markSection("Términos y Condiciones", async () => {
      legalUrls["Términos y Condiciones"] = await openLegalPage({
        appPage: page,
        linkPattern: /Términos y Condiciones/i,
        headingPattern: /Términos y Condiciones/i,
        screenshotName: "08-terminos-y-condiciones"
      });
    });

    await markSection("Política de Privacidad", async () => {
      legalUrls["Política de Privacidad"] = await openLegalPage({
        appPage: page,
        linkPattern: /Política de Privacidad/i,
        headingPattern: /Política de Privacidad/i,
        screenshotName: "09-politica-de-privacidad"
      });
    });

    const finalReport = {
      report,
      legalUrls,
      errors
    };

    await testInfo.attach("saleads-mi-negocio-final-report", {
      body: Buffer.from(JSON.stringify(finalReport, null, 2)),
      contentType: "application/json"
    });

    console.log("SaleADS Mi Negocio final report:");
    console.log(JSON.stringify(finalReport, null, 2));

    expect(errors, `Hay validaciones fallidas:\n${errors.join("\n")}`).toEqual([]);
  });
});
