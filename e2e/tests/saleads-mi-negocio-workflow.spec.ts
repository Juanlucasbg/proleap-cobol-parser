import { expect, Locator, Page, test } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  name: string;
  status: StepStatus;
  details: string;
};

const reportFields = [
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

function createDefaultResults(): Map<string, StepResult> {
  return new Map<string, StepResult>(
    reportFields.map((name) => [
      name,
      { name, status: "FAIL", details: "No se pudo ejecutar este paso." },
    ])
  );
}

const artifactsRoot = process.env.E2E_ARTIFACTS_DIR || "artifacts";
const runDir = join(artifactsRoot, "mi-negocio");
const reportPath = join(runDir, "final-report.json");
const termsUrlPath = join(runDir, "terminos-url.txt");
const privacyUrlPath = join(runDir, "privacidad-url.txt");

function textMatcher(value: string): RegExp {
  return new RegExp(value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
  await page.waitForTimeout(400);
}

async function firstVisible(locator: Locator): Promise<Locator | null> {
  const count = await locator.count();
  for (let i = 0; i < count; i += 1) {
    const candidate = locator.nth(i);
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }
  return null;
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const buttonVisible = await firstVisible(page.getByRole("button", { name: textMatcher(text) }));
  if (buttonVisible) {
    await buttonVisible.click();
    await waitForUi(page);
    return;
  }

  const linkVisible = await firstVisible(page.getByRole("link", { name: textMatcher(text) }));
  if (linkVisible) {
    await linkVisible.click();
    await waitForUi(page);
    return;
  }

  const fallbackLocator = page.getByText(textMatcher(text));
  const fallbackVisible = await firstVisible(fallbackLocator);
  if (fallbackVisible) {
    await fallbackVisible.click();
    await waitForUi(page);
    return;
  }

  throw new Error(`No visible element found with text: ${text}`);
}

async function maybeChooseGoogleAccount(page: Page, accountEmail: string): Promise<void> {
  const accountOption = page.getByText(textMatcher(accountEmail));
  const accountVisible = await firstVisible(accountOption);
  if (accountVisible) {
    await accountVisible.click();
    await waitForUi(page);
  }
}

async function clickPossiblyNewTab(
  page: Page,
  text: string
): Promise<{ tab: Page; openedNewTab: boolean }> {
  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  await clickByVisibleText(page, text);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle");
    return { tab: popup, openedNewTab: true };
  }

  return { tab: page, openedNewTab: false };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test.beforeAll(async () => {
    mkdirSync(runDir, { recursive: true });
  });

  test("saleads_mi_negocio_full_test", async ({ page }) => {
    const results = createDefaultResults();
    const recordResult = (name: string, status: StepStatus, details: string): void => {
      results.set(name, { name, status, details });
    };

    const baseUrl = process.env.SALEADS_BASE_URL;
    const googleEmail =
      process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const runStep = async (
      name: string,
      successDetails: string,
      action: () => Promise<void>
    ): Promise<void> => {
      try {
        await action();
        recordResult(name, "PASS", successDetails);
      } catch (error) {
        recordResult(name, "FAIL", String(error));
      }
    };

    try {
      // 1) Login with Google
      await runStep(
        "Login",
        "Main interface loaded and left sidebar is visible.",
        async () => {
          const loginLabels = [
            "Sign in with Google",
            "Iniciar sesión con Google",
            "Login with Google",
            "Continuar con Google",
          ];
          let clicked = false;
          for (const label of loginLabels) {
            const candidateVisible = await firstVisible(
              page.getByRole("button", { name: textMatcher(label) })
            );
            if (candidateVisible) {
              const popupPromise = page
                .waitForEvent("popup", { timeout: 5000 })
                .catch(() => null);
              await candidateVisible.click();
              await waitForUi(page);
              const popup = await popupPromise;
              if (popup) {
                await popup.waitForLoadState("domcontentloaded");
                await maybeChooseGoogleAccount(popup, googleEmail);
              }
              clicked = true;
              break;
            }
          }

          if (!clicked) {
            const popupPromise = page
              .waitForEvent("popup", { timeout: 5000 })
              .catch(() => null);
            await clickByVisibleText(page, "Google");
            await waitForUi(page);
            const popup = await popupPromise;
            if (popup) {
              await popup.waitForLoadState("domcontentloaded");
              await maybeChooseGoogleAccount(popup, googleEmail);
            }
          }

          await waitForUi(page);
          await maybeChooseGoogleAccount(page, googleEmail);
          await waitForUi(page);

          const sidebar = page.locator(
            "aside, nav, [class*='sidebar'], [data-testid*='sidebar']"
          );
          await expect(sidebar.first()).toBeVisible();
          await page.screenshot({
            path: join(runDir, "01-dashboard.png"),
            fullPage: true,
          });
        }
      );

      // 2) Open Mi Negocio menu
      await runStep(
        "Mi Negocio menu",
        "Submenu expanded and expected options are visible.",
        async () => {
          await clickByVisibleText(page, "Negocio");
          await clickByVisibleText(page, "Mi Negocio");

          await expect(
            page.getByText(textMatcher("Agregar Negocio")).first()
          ).toBeVisible();
          await expect(
            page.getByText(textMatcher("Administrar Negocios")).first()
          ).toBeVisible();
          await page.screenshot({
            path: join(runDir, "02-mi-negocio-menu.png"),
            fullPage: true,
          });
        }
      );

      // 3) Validate Agregar Negocio modal
      await runStep(
        "Agregar Negocio modal",
        "Modal content validated and closed with Cancelar.",
        async () => {
          await clickByVisibleText(page, "Agregar Negocio");

          const modal = page
            .locator("[role='dialog'], .modal, [class*='modal']")
            .first();
          await expect(modal).toBeVisible();
          await expect(
            page.getByText(textMatcher("Crear Nuevo Negocio")).first()
          ).toBeVisible();
          await expect(
            page.getByText(textMatcher("Nombre del Negocio")).first()
          ).toBeVisible();
          await expect(
            page.getByText(textMatcher("Tienes 2 de 3 negocios")).first()
          ).toBeVisible();
          await expect(page.getByText(textMatcher("Cancelar")).first()).toBeVisible();
          await expect(
            page.getByText(textMatcher("Crear Negocio")).first()
          ).toBeVisible();

          const fallbackInput = modal.locator("input, textarea").first();
          await fallbackInput.click();
          await fallbackInput.fill("Negocio Prueba Automatización");

          await page.screenshot({
            path: join(runDir, "03-agregar-negocio-modal.png"),
            fullPage: true,
          });
          await clickByVisibleText(page, "Cancelar");
          await expect(modal).not.toBeVisible();
        }
      );

      // 4) Open Administrar Negocios
      await runStep(
        "Administrar Negocios view",
        "Account page sections are visible.",
        async () => {
          if (
            !(await page
              .getByText(textMatcher("Administrar Negocios"))
              .first()
              .isVisible()
              .catch(() => false))
          ) {
            await clickByVisibleText(page, "Mi Negocio");
          }

          await clickByVisibleText(page, "Administrar Negocios");

          await expect(
            page.getByText(textMatcher("Información General")).first()
          ).toBeVisible();
          await expect(
            page.getByText(textMatcher("Detalles de la Cuenta")).first()
          ).toBeVisible();
          await expect(page.getByText(textMatcher("Tus Negocios")).first()).toBeVisible();
          await expect(page.getByText(textMatcher("Sección Legal")).first()).toBeVisible();
          await page.screenshot({
            path: join(runDir, "04-administrar-negocios.png"),
            fullPage: true,
          });
        }
      );

      // 5) Validate Información General
      await runStep(
        "Información General",
        "Name, email, plan, and Cambiar Plan button are visible.",
        async () => {
          const infoSection = page
            .locator("section, div")
            .filter({ has: page.getByText(textMatcher("Información General")) })
            .first();

          await expect(infoSection).toContainText(/@/i);
          await expect(infoSection).toContainText(/\S+/i);
          await expect(infoSection).toContainText(textMatcher("BUSINESS PLAN"));
          await expect(
            infoSection.getByText(textMatcher("Cambiar Plan")).first()
          ).toBeVisible();
        }
      );

      // 6) Validate Detalles de la Cuenta
      await runStep(
        "Detalles de la Cuenta",
        "Account details labels are visible.",
        async () => {
          const detailsSection = page
            .locator("section, div")
            .filter({ has: page.getByText(textMatcher("Detalles de la Cuenta")) })
            .first();
          await expect(detailsSection).toContainText(textMatcher("Cuenta creada"));
          await expect(detailsSection).toContainText(textMatcher("Estado"));
          await expect(detailsSection).toContainText(textMatcher("Activo"));
          await expect(detailsSection).toContainText(textMatcher("Idioma seleccionado"));
        }
      );

      // 7) Validate Tus Negocios
      await runStep(
        "Tus Negocios",
        "Business list and limits text are visible.",
        async () => {
          const businessesSection = page
            .locator("section, div")
            .filter({ has: page.getByText(textMatcher("Tus Negocios")) })
            .first();
          await expect(businessesSection).toBeVisible();
          await expect(
            businessesSection.getByText(textMatcher("Agregar Negocio")).first()
          ).toBeVisible();
          await expect(
            businessesSection.getByText(textMatcher("Tienes 2 de 3 negocios")).first()
          ).toBeVisible();
        }
      );

      // 8) Validate Términos y Condiciones
      await runStep(
        "Términos y Condiciones",
        "Legal page validated, screenshot captured, and URL recorded.",
        async () => {
          const terms = await clickPossiblyNewTab(page, "Términos y Condiciones");
          await expect(
            terms.tab.getByText(textMatcher("Términos y Condiciones")).first()
          ).toBeVisible();
          await expect(terms.tab.locator("main, body")).toContainText(/\S{20,}/);

          writeFileSync(termsUrlPath, terms.tab.url(), "utf-8");
          await terms.tab.screenshot({
            path: join(runDir, "08-terminos-y-condiciones.png"),
            fullPage: true,
          });

          if (terms.openedNewTab) {
            await terms.tab.close();
            await page.bringToFront();
          } else {
            await page.goBack();
            await waitForUi(page);
          }
        }
      );

      // 9) Validate Política de Privacidad
      await runStep(
        "Política de Privacidad",
        "Privacy page validated, screenshot captured, and URL recorded.",
        async () => {
          const privacy = await clickPossiblyNewTab(page, "Política de Privacidad");
          await expect(
            privacy.tab.getByText(textMatcher("Política de Privacidad")).first()
          ).toBeVisible();
          await expect(privacy.tab.locator("main, body")).toContainText(/\S{20,}/);

          writeFileSync(privacyUrlPath, privacy.tab.url(), "utf-8");
          await privacy.tab.screenshot({
            path: join(runDir, "09-politica-de-privacidad.png"),
            fullPage: true,
          });

          if (privacy.openedNewTab) {
            await privacy.tab.close();
            await page.bringToFront();
          } else {
            await page.goBack();
            await waitForUi(page);
          }
        }
      );
    } finally {
      // 10) Final report
      const report = {
        name: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        fields: [...reportFields],
        results: reportFields.map((name) => results.get(name)),
      };

      writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf-8");
    }

    expect([...results.values()].every((step) => step.status === "PASS")).toBeTruthy();
  });
});
