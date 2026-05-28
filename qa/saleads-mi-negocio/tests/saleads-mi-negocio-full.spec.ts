import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs";

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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_NAME = "Negocio Prueba Automatización";

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

async function pickVisible(candidates: Locator[], name: string): Promise<Locator> {
  for (let attempt = 0; attempt < 25; attempt += 1) {
    for (const candidate of candidates) {
      const first = candidate.first();
      const visible = await first.isVisible().catch(() => false);
      if (visible) {
        return first;
      }
    }
    await candidates[0].page().waitForTimeout(500);
  }
  throw new Error(`Could not find a visible element for "${name}"`);
}

function safeFileName(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9._-]+/g, "_")
    .toLowerCase();
}

async function captureCheckpoint(page: Page, checkpoint: string, fullPage = false): Promise<void> {
  const fileName = `${safeFileName(checkpoint)}.png`;
  const path = test.info().outputPath(fileName);
  await page.screenshot({ path, fullPage });
  await test.info().attach(checkpoint, {
    path,
    contentType: "image/png",
  });
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }) => {
    const report: Record<ReportField, "PASS" | "FAIL"> = {
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
    const legalUrls: Partial<Record<ReportField, string>> = {};
    const errors: string[] = [];

    const markStep = async (field: ReportField, run: () => Promise<void>): Promise<void> => {
      try {
        await run();
        report[field] = "PASS";
      } catch (error) {
        report[field] = "FAIL";
        const message = error instanceof Error ? error.message : String(error);
        errors.push(`${field}: ${message}`);
        await captureCheckpoint(page, `${field} - failure`, true).catch(() => undefined);
      }
    };

    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL;
    test.skip(!loginUrl, "Set SALEADS_LOGIN_URL (or SALEADS_URL) to the login page URL.");

    await page.goto(loginUrl as string, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    await markStep("Login", async () => {
      const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      const googleLogin = await pickVisible(
        [
          page.getByRole("button", { name: /google/i }),
          page.getByRole("link", { name: /google/i }),
          page.getByText(/sign in with google/i),
          page.getByText(/iniciar sesi[oó]n con google/i),
          page.getByText(/continuar con google/i),
        ],
        "Sign in with Google",
      );

      await googleLogin.click();
      await waitForUi(page);

      const authPage = (await popupPromise) ?? page;
      await authPage.waitForLoadState("domcontentloaded").catch(() => undefined);

      const accountOption = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
      const canPickAccount = await accountOption.isVisible().catch(() => false);
      if (canPickAccount) {
        await accountOption.click();
        await authPage.waitForLoadState("domcontentloaded").catch(() => undefined);
      }

      await page.bringToFront();
      await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 50_000 });
      await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
      await captureCheckpoint(page, "01-dashboard-loaded", true);
    });

    await markStep("Mi Negocio menu", async () => {
      const negocioSection = await pickVisible(
        [
          page.getByRole("button", { name: /^negocio$/i }),
          page.getByRole("link", { name: /^negocio$/i }),
          page.getByText(/^negocio$/i),
        ],
        "Negocio section",
      );
      await negocioSection.click();
      await waitForUi(page);

      const miNegocio = await pickVisible(
        [page.getByRole("link", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
        "Mi Negocio option",
      );
      await miNegocio.click();
      await waitForUi(page);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
      await captureCheckpoint(page, "02-mi-negocio-menu-expanded", true);
    });

    await markStep("Agregar Negocio modal", async () => {
      const addBusiness = await pickVisible(
        [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
        "Agregar Negocio button",
      );

      await addBusiness.click();
      await waitForUi(page);

      await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
      const businessNameInput = await pickVisible(
        [
          page.getByLabel(/nombre del negocio/i),
          page.getByPlaceholder(/nombre del negocio/i),
          page.getByRole("textbox", { name: /nombre del negocio/i }),
          page.locator("input").first(),
        ],
        "Nombre del Negocio input",
      );
      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();
      await captureCheckpoint(page, "03-agregar-negocio-modal", true);

      await businessNameInput.fill(BUSINESS_NAME);
      const cancelButton = await pickVisible(
        [page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)],
        "Cancelar button",
      );
      await cancelButton.click();
      await waitForUi(page);
    });

    await markStep("Administrar Negocios view", async () => {
      const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        const miNegocio = await pickVisible(
          [page.getByRole("link", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
          "Mi Negocio option",
        );
        await miNegocio.click();
        await waitForUi(page);
      }

      const administrarNegocios = await pickVisible(
        [page.getByRole("link", { name: /administrar negocios/i }), page.getByText(/administrar negocios/i)],
        "Administrar Negocios option",
      );
      await administrarNegocios.click();
      await waitForUi(page);

      await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();
      await captureCheckpoint(page, "04-administrar-negocios-view", true);
    });

    await markStep("Información General", async () => {
      await expect(page.getByText(/@/).first()).toBeVisible();
      await expect(page.getByText(/business plan/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();

      // A non-email short text in the section is treated as the user name evidence.
      const infoSection = page
        .locator("section, div")
        .filter({ has: page.getByText(/informaci[oó]n general/i).first() })
        .first();
      await expect(infoSection).toBeVisible();
    });

    await markStep("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
    });

    await markStep("Tus Negocios", async () => {
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    });

    const validateLegalLink = async (
      field: "Términos y Condiciones" | "Política de Privacidad",
      linkRegex: RegExp,
      headingRegex: RegExp,
      screenshotName: string,
    ): Promise<void> => {
      const link = await pickVisible([page.getByRole("link", { name: linkRegex }), page.getByText(linkRegex)], field);
      const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);

      await link.click();
      let legalPage = await popupPromise;
      if (legalPage) {
        await legalPage.waitForLoadState("domcontentloaded");
      } else {
        legalPage = page;
        await waitForUi(legalPage);
      }

      await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible();
      await expect(
        legalPage
          .locator("main, article, section, body")
          .getByText(/t[eé]rminos|condiciones|privacidad|datos personales|informaci[oó]n/i)
          .first(),
      ).toBeVisible();

      legalUrls[field] = legalPage.url();
      await captureCheckpoint(legalPage, screenshotName, true);

      if (legalPage !== page) {
        await legalPage.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
        await waitForUi(page);
      }
    };

    await markStep("Términos y Condiciones", async () => {
      await validateLegalLink(
        "Términos y Condiciones",
        /t[eé]rminos y condiciones/i,
        /t[eé]rminos y condiciones/i,
        "08-terminos-y-condiciones",
      );
    });

    await markStep("Política de Privacidad", async () => {
      await validateLegalLink(
        "Política de Privacidad",
        /pol[ií]tica de privacidad/i,
        /pol[ií]tica de privacidad/i,
        "09-politica-de-privacidad",
      );
    });

    const summary = {
      report,
      legalUrls,
      generatedAt: new Date().toISOString(),
      loginUrlUsed: loginUrl,
      errors,
    };
    const reportPath = test.info().outputPath("final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify(summary, null, 2), "utf8");
    await test.info().attach("final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    expect(errors, `Validation failures:\n${errors.join("\n")}`).toEqual([]);
  });
});
