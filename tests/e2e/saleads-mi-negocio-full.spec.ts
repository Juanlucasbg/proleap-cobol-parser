import { expect, type Locator, type Page, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  step: string;
  status: StepStatus;
  details: string;
};

const DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const APP_URL =
  process.env.SALEADS_URL ||
  process.env.BASE_URL ||
  process.env.PLAYWRIGHT_TEST_BASE_URL;

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

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function clickAndWait(page: Page, target: Locator): Promise<void> {
  await expect(target).toBeVisible({ timeout: 20_000 });
  await target.scrollIntoViewIfNeeded();
  await target.click();
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function pickFirstVisible(candidates: Locator[]): Promise<Locator> {
  for (const candidate of candidates) {
    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return candidates[0].first();
}

async function checkpoint(page: Page, name: string): Promise<void> {
  await page.screenshot({
    path: `test-results/checkpoints/${name}.png`,
    fullPage: true,
  });
}

async function validateLegalPage(
  page: Page,
  linkText: RegExp,
  headingText: RegExp,
  checkpointName: string,
): Promise<string> {
  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  const link = page.getByRole("link", { name: linkText }).first();
  await clickAndWait(page, link);

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  await targetPage.waitForLoadState("domcontentloaded").catch(() => {});
  await targetPage.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});

  await expect(targetPage.getByRole("heading", { name: headingText }).first()).toBeVisible({
    timeout: 20_000,
  });

  const visibleParagraph = targetPage.locator("main p, article p, p").first();
  await expect(visibleParagraph).toBeVisible({ timeout: 20_000 });
  const legalText = (await visibleParagraph.innerText()).trim();
  expect(legalText.length).toBeGreaterThan(20);

  await checkpoint(targetPage, checkpointName);

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
  }

  return finalUrl;
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }) => {
    if (!APP_URL) {
      throw new Error(
        "Missing SALEADS_URL (or BASE_URL / PLAYWRIGHT_TEST_BASE_URL). Provide the login page URL for the current environment.",
      );
    }

    const results = new Map<string, StepResult>();
    const googleEmail = process.env.SALEADS_GOOGLE_EMAIL || DEFAULT_GOOGLE_EMAIL;
    const configuredUserName = process.env.SALEADS_EXPECTED_USER_NAME;
    let termsUrl = "";
    let privacyUrl = "";

    const runStep = async (name: (typeof REPORT_FIELDS)[number], logic: () => Promise<void>) => {
      try {
        await logic();
        results.set(name, { step: name, status: "PASS", details: "Validated successfully" });
      } catch (error) {
        const details = error instanceof Error ? error.message : String(error);
        results.set(name, { step: name, status: "FAIL", details });
      }
    };

    await page.goto(APP_URL, { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});

    await runStep("Login", async () => {
      const loginButton = await pickFirstVisible([
        page.getByRole("button", {
          name: /google|iniciar sesi[oó]n|sign in|login|continuar con google/i,
        }),
        page.getByRole("link", {
          name: /google|iniciar sesi[oó]n|sign in|login|continuar con google/i,
        }),
        page.getByText(/google|iniciar sesi[oó]n|sign in|login|continuar con google/i),
      ]);

      const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const popup = await popupPromise;

      const authPage = popup ?? page;
      const onGooglePage =
        /accounts\.google\.com/i.test(authPage.url()) ||
        (await authPage.getByText(/elige una cuenta|choose an account/i).first().isVisible().catch(() => false));

      if (onGooglePage) {
        const emailOption = authPage.getByText(new RegExp(escapeRegExp(googleEmail), "i")).first();
        if (await emailOption.isVisible().catch(() => false)) {
          await clickAndWait(authPage, emailOption);
        }
      }

      if (popup) {
        await popup.waitForClose({ timeout: 90_000 }).catch(() => {});
      }

      await page.bringToFront();
      await page.waitForLoadState("domcontentloaded").catch(() => {});
      await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});

      const sidebar = page.locator("aside, nav").first();
      await expect(sidebar).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 20_000 });

      await checkpoint(page, "01-dashboard-loaded");
    });

    await runStep("Mi Negocio menu", async () => {
      const negocioSection = await pickFirstVisible([page.getByText(/^Negocio$/i), page.getByText(/negocio/i)]);
      await clickAndWait(page, negocioSection);

      const miNegocio = page.getByText(/^Mi Negocio$/i).first();
      await clickAndWait(page, miNegocio);

      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: 20_000 });

      await checkpoint(page, "02-mi-negocio-menu-expanded");
    });

    await runStep("Agregar Negocio modal", async () => {
      await clickAndWait(page, page.getByText(/^Agregar Negocio$/i).first());

      const modal = await pickFirstVisible([
        page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
        page.locator("[role='dialog'], .modal, [aria-modal='true']").filter({ hasText: /Crear Nuevo Negocio/i }),
      ]);

      await expect(modal.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 20_000 });

      const nameInput = await pickFirstVisible([
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
        modal.getByRole("textbox", { name: /Nombre del Negocio/i }),
        modal.locator("input"),
      ]);
      await expect(nameInput).toBeVisible({ timeout: 20_000 });

      await expect(modal.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(modal.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible({ timeout: 20_000 });
      await expect(modal.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible({ timeout: 20_000 });

      await nameInput.fill("Negocio Prueba Automatización");
      await checkpoint(page, "03-agregar-negocio-modal");
      await clickAndWait(page, modal.getByRole("button", { name: /^Cancelar$/i }).first());
    });

    await runStep("Administrar Negocios view", async () => {
      const administrarOption = page.getByText(/^Administrar Negocios$/i).first();
      if (!(await administrarOption.isVisible().catch(() => false))) {
        await clickAndWait(page, page.getByText(/^Mi Negocio$/i).first());
      }

      await clickAndWait(page, administrarOption);

      await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 20_000 });

      await checkpoint(page, "04-administrar-negocios-view");
    });

    await runStep("Información General", async () => {
      await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 20_000 });

      if (configuredUserName) {
        await expect(page.getByText(new RegExp(escapeRegExp(configuredUserName), "i")).first()).toBeVisible({
          timeout: 20_000,
        });
      } else {
        await expect(page.getByText(/juan|lucas|barbier|garzon/i).first()).toBeVisible({ timeout: 20_000 });
      }

      await expect(page.getByText(new RegExp(escapeRegExp(googleEmail), "i")).first()).toBeVisible({
        timeout: 20_000,
      });
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 20_000 });
    });

    await runStep("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await runStep("Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({
        timeout: 20_000,
      });
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 20_000 });

      const listCandidate = page
        .locator("[role='list'], ul, table, [class*='business'], [class*='negocio']")
        .first();
      await expect(listCandidate).toBeVisible({ timeout: 20_000 });
    });

    await runStep("Términos y Condiciones", async () => {
      termsUrl = await validateLegalPage(
        page,
        /T[ée]rminos y Condiciones/i,
        /T[ée]rminos y Condiciones/i,
        "05-terminos-y-condiciones",
      );
    });

    await runStep("Política de Privacidad", async () => {
      privacyUrl = await validateLegalPage(
        page,
        /Pol[íi]tica de Privacidad/i,
        /Pol[íi]tica de Privacidad/i,
        "06-politica-de-privacidad",
      );
    });

    const report: StepResult[] = REPORT_FIELDS.map((field) => {
      return results.get(field) ?? { step: field, status: "FAIL", details: "Step did not execute" };
    });

    report.push({
      step: "Términos y Condiciones URL",
      status: termsUrl ? "PASS" : "FAIL",
      details: termsUrl || "No URL captured",
    });
    report.push({
      step: "Política de Privacidad URL",
      status: privacyUrl ? "PASS" : "FAIL",
      details: privacyUrl || "No URL captured",
    });

    await test.info().attach("saleads-mi-negocio-final-report.json", {
      body: Buffer.from(JSON.stringify(report, null, 2), "utf-8"),
      contentType: "application/json",
    });

    // eslint-disable-next-line no-console
    console.table(report);

    const failed = report.filter((item) => item.status === "FAIL");
    expect(
      failed,
      `Failed validations: ${failed.map((item) => `${item.step}: ${item.details}`).join(" | ")}`,
    ).toHaveLength(0);
  });
});
