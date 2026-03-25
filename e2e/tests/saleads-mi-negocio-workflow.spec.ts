import { mkdir } from "node:fs/promises";
import { expect, test } from "@playwright/test";

type CheckResult = "PASS" | "FAIL";

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

const report: Record<ReportKey, CheckResult> = {
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

const WAIT_TIMEOUT = 30_000;

function normalizeUrl(value: string): string {
  return value.replace(/\/+$/, "");
}

async function waitForUi(page: import("@playwright/test").Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
}

async function clickFirstVisible(
  page: import("@playwright/test").Page,
  candidates: string[],
): Promise<boolean> {
  for (const text of candidates) {
    const target = page.getByRole("button", { name: new RegExp(text, "i") }).first();
    if ((await target.count()) > 0 && (await target.isVisible())) {
      await target.click();
      await waitForUi(page);
      return true;
    }
  }

  for (const text of candidates) {
    const target = page.getByText(new RegExp(text, "i")).first();
    if ((await target.count()) > 0 && (await target.isVisible())) {
      await target.click();
      await waitForUi(page);
      return true;
    }
  }

  return false;
}

async function capture(
  page: import("@playwright/test").Page,
  name: string,
  fullPage = false,
): Promise<void> {
  await mkdir("artifacts/screenshots", { recursive: true });
  await page.screenshot({ path: `artifacts/screenshots/${name}.png`, fullPage });
}

async function visibleByText(
  page: import("@playwright/test").Page,
  expressions: RegExp[],
): Promise<boolean> {
  for (const expression of expressions) {
    const target = page.getByText(expression, { exact: false }).first();
    if ((await target.count()) > 0 && (await target.isVisible())) {
      return true;
    }
  }
  return false;
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }) => {
    const configuredBase = process.env.SALEADS_BASE_URL;

    if (configuredBase) {
      await page.goto(configuredBase, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else {
      await waitForUi(page);
    }

    const currentUrl = page.url();
    if (!configuredBase && !/^https?:\/\//i.test(currentUrl)) {
      throw new Error(
        "No SALEADS_BASE_URL configured and browser is not on a SaleADS login page. Open login page first or set SALEADS_BASE_URL.",
      );
    }

    const appOrigin = new URL(page.url()).origin;

    // Step 1 - Login with Google and validate app shell.
    {
      const sidebarVisibleBeforeLogin =
        (await page.locator("aside").first().count()) > 0 &&
        (await page.locator("aside").first().isVisible().catch(() => false));
      if (sidebarVisibleBeforeLogin) {
        report.Login = "PASS";
        await capture(page, "01-dashboard-loaded");
      } else {
        const popupPromise = context
          .waitForEvent("page", { timeout: 7_000 })
          .catch(() => null);

        const clicked = await clickFirstVisible(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Google",
        ]);

        expect(clicked, "Google login trigger should be visible").toBeTruthy();

        const popup = await popupPromise;
        const authPage = popup ?? page;
        await waitForUi(authPage);

        const googleAccount = authPage
          .locator("div[role='button'], li[role='option'], div")
          .filter({ hasText: "juanlucasbarbiergarzon@gmail.com" })
          .first();

        if ((await googleAccount.count()) > 0 && (await googleAccount.isVisible())) {
          await googleAccount.click();
          await waitForUi(authPage);
        }

        if (popup && !popup.isClosed()) {
          await popup.waitForLoadState("networkidle").catch(() => {});
          await page.bringToFront();
        }

        await expect(page.locator("aside").first()).toBeVisible({ timeout: WAIT_TIMEOUT });
        report.Login = "PASS";
        await capture(page, "01-dashboard-loaded");
      }
    }

    // Step 2 - Open Mi Negocio menu from left navigation.
    {
      const negocioSection = page.getByText(/^Negocio$/i).first();
      await expect(negocioSection).toBeVisible({ timeout: WAIT_TIMEOUT });

      const miNegocio = page.getByText(/^Mi Negocio$/i).first();
      await expect(miNegocio).toBeVisible({ timeout: WAIT_TIMEOUT });
      await miNegocio.click();
      await waitForUi(page);

      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });

      report["Mi Negocio menu"] = "PASS";
      await capture(page, "02-mi-negocio-expanded");
    }

    // Step 3 - Validate Agregar Negocio modal.
    {
      await page.getByText(/^Agregar Negocio$/i).first().click();
      await waitForUi(page);

      await expect(page.getByText("Crear Nuevo Negocio", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByLabel("Nombre del Negocio", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });

      await capture(page, "03-agregar-negocio-modal");

      await page.getByLabel("Nombre del Negocio", { exact: false }).click();
      await page
        .getByLabel("Nombre del Negocio", { exact: false })
        .fill("Negocio Prueba Automatización");
      await page.getByRole("button", { name: /^Cancelar$/i }).click();
      await waitForUi(page);

      report["Agregar Negocio modal"] = "PASS";
    }

    // Step 4 - Open Administrar Negocios and validate account page sections.
    {
      const administrarNegocios = page.getByText(/^Administrar Negocios$/i).first();
      if (!(await administrarNegocios.isVisible())) {
        await page.getByText(/^Mi Negocio$/i).first().click();
        await waitForUi(page);
      }

      await page.getByText(/^Administrar Negocios$/i).first().click();
      await waitForUi(page);

      await expect(page.getByText("Información General", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByText("Detalles de la Cuenta", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByText("Tus Negocios", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByText("Sección Legal", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });

      report["Administrar Negocios view"] = "PASS";
      await capture(page, "04-administrar-negocios", true);
    }

    // Step 5 - Validate Información General.
    {
      const hasVisibleName = await visibleByText(page, [
        /Bienvenido/i,
        /Hola/i,
        /Usuario/i,
        /Nombre/i,
      ]);
      expect(hasVisibleName, "User name indicator should be visible").toBeTruthy();

      const hasVisibleEmail = await visibleByText(page, [
        /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i,
      ]);
      expect(hasVisibleEmail, "User email should be visible").toBeTruthy();

      await expect(page.getByText("BUSINESS PLAN", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      report["Información General"] = "PASS";
    }

    // Step 6 - Validate Detalles de la Cuenta.
    {
      await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByText("Estado activo", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      report["Detalles de la Cuenta"] = "PASS";
    }

    // Step 7 - Validate Tus Negocios.
    {
      await expect(page.getByText("Tus Negocios", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      const businessList = page
        .locator("section,div,ul")
        .filter({ hasText: /negocio/i })
        .first();
      await expect(businessList).toBeVisible({ timeout: WAIT_TIMEOUT });
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      report["Tus Negocios"] = "PASS";
    }

    // Step 8 - Validate Términos y Condiciones.
    {
      const termsLink = page.getByRole("link", { name: /T[eé]rminos y Condiciones/i }).first();
      await expect(termsLink).toBeVisible({ timeout: WAIT_TIMEOUT });

      const currentPages = context.pages().length;
      await termsLink.click();
      await waitForUi(page);

      let legalPage = page;
      if (context.pages().length > currentPages) {
        legalPage = context.pages().at(-1) as typeof page;
        await legalPage.bringToFront();
      }

      await waitForUi(legalPage);
      await expect(
        legalPage.getByText(/T[eé]rminos y Condiciones/i, { exact: false }),
      ).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(legalPage.locator("main,article,section,body").first()).toContainText(
        /\w{10,}/,
        { timeout: WAIT_TIMEOUT },
      );

      await capture(legalPage, "08-terminos-y-condiciones");
      console.log(`[EVIDENCE] Términos y Condiciones URL: ${legalPage.url()}`);
      report["Términos y Condiciones"] = "PASS";

      if (legalPage !== page) {
        await legalPage.close();
      }
      await page.bringToFront();
      await waitForUi(page);
    }

    // Step 9 - Validate Política de Privacidad.
    {
      const privacyLink = page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }).first();
      await expect(privacyLink).toBeVisible({ timeout: WAIT_TIMEOUT });

      const currentPages = context.pages().length;
      await privacyLink.click();
      await waitForUi(page);

      let legalPage = page;
      if (context.pages().length > currentPages) {
        legalPage = context.pages().at(-1) as typeof page;
        await legalPage.bringToFront();
      }

      await waitForUi(legalPage);
      await expect(
        legalPage.getByText(/Pol[ií]tica de Privacidad/i, { exact: false }),
      ).toBeVisible({
        timeout: WAIT_TIMEOUT,
      });
      await expect(legalPage.locator("main,article,section,body").first()).toContainText(
        /\w{10,}/,
        { timeout: WAIT_TIMEOUT },
      );

      await capture(legalPage, "09-politica-de-privacidad");
      console.log(`[EVIDENCE] Política de Privacidad URL: ${legalPage.url()}`);
      report["Política de Privacidad"] = "PASS";

      if (legalPage !== page) {
        await legalPage.close();
      }
      await page.bringToFront();
      await waitForUi(page);
    }

    // Step 10 - Final report in stdout + assertion for workflow-level health.
    console.table(report);

    for (const [key, value] of Object.entries(report)) {
      expect(value, `${key} validation should pass`).toBe("PASS");
    }

    expect(normalizeUrl(page.url()).startsWith(normalizeUrl(appOrigin))).toBeTruthy();
  });
});
