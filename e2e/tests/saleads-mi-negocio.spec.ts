import { expect, test } from "@playwright/test";

type CheckStatus = "PASS" | "FAIL";

type CheckResult = {
  name: string;
  status: CheckStatus;
  details?: string;
};

function markPass(report: CheckResult[], name: string, details?: string): void {
  report.push({ name, status: "PASS", details });
}

function markFail(report: CheckResult[], name: string, error: unknown): void {
  const details = error instanceof Error ? error.message : String(error);
  report.push({ name, status: "FAIL", details });
}

async function runStep(
  report: CheckResult[],
  name: string,
  fn: () => Promise<void>,
  stopOnFailure = false,
): Promise<void> {
  try {
    await fn();
    markPass(report, name);
  } catch (error) {
    markFail(report, name, error);
    if (stopOnFailure) {
      throw error;
    }
  }
}

function screenshotPath(prefix: string): string {
  return `artifacts/screenshots/${Date.now()}-${prefix}.png`;
}

async function waitForUi(page: import("@playwright/test").Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
  await page.waitForTimeout(500);
}

async function clickByText(
  page: import("@playwright/test").Page,
  text: string,
): Promise<void> {
  const locator = page.getByText(new RegExp(`^\\s*${text}\\s*$`, "i")).first();
  await expect(locator).toBeVisible({ timeout: 15_000 });
  await locator.click();
  await waitForUi(page);
}

async function clickSidebarItem(
  page: import("@playwright/test").Page,
  text: string,
): Promise<void> {
  const candidate = page
    .locator("aside, nav")
    .getByText(new RegExp(`^\\s*${text}\\s*$`, "i"))
    .first();

  if ((await candidate.count()) > 0 && (await candidate.isVisible())) {
    await candidate.click();
    await waitForUi(page);
    return;
  }

  await clickByText(page, text);
}

async function openLegalLinkAndValidate(
  page: import("@playwright/test").Page,
  linkText: string,
  headingText: string,
): Promise<{ finalUrl: string; screenshot: string }> {
  const linkCandidates = page
    .locator("a, button, [role='link']")
    .filter({ hasText: new RegExp(linkText, "i") });
  const link = linkCandidates.first();
  await expect(link, `Legal link/button not visible: ${linkText}`).toBeVisible({
    timeout: 15_000,
  });

  const maybePopupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
  await link.click();

  const popup = await maybePopupPromise;
  const target = popup ?? page;

  await waitForUi(target);
  await expect(target.getByText(new RegExp(headingText, "i")).first()).toBeVisible({
    timeout: 15_000,
  });

  // Validate content is non-trivial and not just heading text.
  const bodyText = (await target.locator("body").innerText()).trim();
  expect(bodyText.length).toBeGreaterThan(200);

  const screenshot = screenshotPath(
    linkText
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/\s+/g, "-"),
  );
  await target.screenshot({ path: screenshot, fullPage: true });

  const finalUrl = target.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await page.goBack();
    await waitForUi(page);
  }

  return { finalUrl, screenshot };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }) => {
    const report: CheckResult[] = [];
    const baseUrl = process.env.SALEADS_BASE_URL;
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    // Step 1: Login with Google
    await runStep(
      report,
      "Login",
      async () => {
        const googleSignIn = page
          .locator("button, a")
          .filter({ hasText: /sign in with google|google|iniciar con google/i })
          .first();
        await expect(googleSignIn).toBeVisible({ timeout: 20_000 });

        const popupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
        await googleSignIn.click();

        const popup = await popupPromise;
        if (popup) {
          await waitForUi(popup);
          const accountOption = popup
            .getByText(/juanlucasbarbiergarzon@gmail\.com/i)
            .first();
          if ((await accountOption.count()) > 0 && (await accountOption.isVisible())) {
            await accountOption.click();
          }
          await popup.waitForEvent("close", { timeout: 30_000 }).catch(() => undefined);
        }

        await waitForUi(page);
        await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });

        const dashboardShot = screenshotPath("dashboard-loaded");
        await page.screenshot({ path: dashboardShot, fullPage: true });
      },
      true,
    );

    // Step 2: Open Mi Negocio menu
    await runStep(report, "Mi Negocio menu", async () => {
      await clickSidebarItem(page, "Negocio");
      await clickSidebarItem(page, "Mi Negocio");
      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({
        timeout: 15_000,
      });
      await page.screenshot({ path: screenshotPath("mi-negocio-menu-expanded"), fullPage: true });
    });

    // Step 3: Validate Agregar Negocio modal
    await runStep(report, "Agregar Negocio modal", async () => {
      await clickByText(page, "Agregar Negocio");
      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({
        timeout: 15_000,
      });
      await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      const field = page.getByLabel(/Nombre del Negocio/i).first();
      await field.click();
      await field.fill("Negocio Prueba Automatización");

      await page.screenshot({ path: screenshotPath("agregar-negocio-modal"), fullPage: true });
      await page.getByRole("button", { name: /Cancelar/i }).click();
      await waitForUi(page);
    });

    // Step 4: Open Administrar Negocios
    await runStep(report, "Administrar Negocios view", async () => {
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible())) {
        await clickSidebarItem(page, "Mi Negocio");
      }

      await clickByText(page, "Administrar Negocios");

      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({
        timeout: 15_000,
      });
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

      await page.screenshot({ path: screenshotPath("administrar-negocios"), fullPage: true });
    });

    // Step 5: Validate Información General
    await runStep(report, "Información General", async () => {
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
      await expect(page.getByText(/@/).first()).toBeVisible(); // email
      await expect(
        page
          .locator("section, div")
          .filter({ hasText: /Informaci[oó]n General/i })
          .first(),
      ).toContainText(/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/); // user name-like text
    });

    // Step 6: Validate Detalles de la Cuenta
    await runStep(report, "Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });

    // Step 7: Validate Tus Negocios
    await runStep(report, "Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    });

    // Step 8: Validate Términos y Condiciones
    await runStep(report, "Términos y Condiciones", async () => {
      const result = await openLegalLinkAndValidate(
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
      );
      test.info().annotations.push({
        type: "evidence",
        description: `Términos URL: ${result.finalUrl} | Screenshot: ${result.screenshot}`,
      });
    });

    // Step 9: Validate Política de Privacidad
    await runStep(report, "Política de Privacidad", async () => {
      const result = await openLegalLinkAndValidate(
        page,
        "Política de Privacidad",
        "Política de Privacidad",
      );
      test.info().annotations.push({
        type: "evidence",
        description: `Privacidad URL: ${result.finalUrl} | Screenshot: ${result.screenshot}`,
      });
    });

    // Step 10: Final report
    const expectedOrder = [
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

    const statusMap = new Map(report.map((entry) => [entry.name, entry]));
    const orderedReport = expectedOrder.map((name) => {
      const entry = statusMap.get(name);
      return {
        step: name,
        status: entry?.status ?? "FAIL",
        details: entry?.details ?? "No result recorded",
      };
    });

    const finalReport = JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        environmentUrl: page.url(),
        report: orderedReport,
      },
      null,
      2,
    );

    console.log("=== SALEADS MI NEGOCIO FINAL REPORT ===");
    console.log(finalReport);
    test.info().attach("saleads-mi-negocio-report.json", {
      body: Buffer.from(finalReport, "utf-8"),
      contentType: "application/json",
    });

    // Keep test outcome strict: any failed step should fail the test.
    const failed = orderedReport.filter((item) => item.status === "FAIL");
    expect(failed, `Failed steps:\n${JSON.stringify(failed, null, 2)}`).toHaveLength(0);
  });
});
