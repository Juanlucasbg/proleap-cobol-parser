import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  name: string;
  status: StepStatus;
  details: string[];
};

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

const EVIDENCE_DIR = path.join(process.cwd(), "e2e-artifacts", "saleads-mi-negocio");
const REPORT_FILE = path.join(EVIDENCE_DIR, "final-report.json");
const TEST_BUSINESS_NAME = "Negocio Prueba Automatización";
const GO_TO_APP_TIMEOUT_MS = 90_000;
const UI_TIMEOUT_MS = 20_000;

function ensureEvidenceDir(): void {
  fs.mkdirSync(EVIDENCE_DIR, { recursive: true });
}

function sanitizeFileName(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function createInitialResults(): Record<string, StepResult> {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        name: field,
        status: "FAIL",
        details: ["Step was not executed."],
      } satisfies StepResult,
    ]),
  );
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {
    // Some app pages keep long-polling connections open.
  });
}

async function screenshotCheckpoint(page: Page, name: string, fullPage = false): Promise<string> {
  ensureEvidenceDir();
  const fileName = `${sanitizeFileName(name)}.png`;
  const screenshotPath = path.join(EVIDENCE_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

function textCandidateLocators(page: Page, label: string): Locator[] {
  const matcher = new RegExp(escapeRegExp(label), "i");
  return [
    page.getByRole("button", { name: matcher }),
    page.getByRole("link", { name: matcher }),
    page.getByRole("menuitem", { name: matcher }),
    page.getByRole("heading", { name: matcher }),
    page.getByText(matcher),
    page.getByLabel(matcher),
    page.getByPlaceholder(matcher),
  ];
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

async function clickByVisibleText(page: Page, labels: string[]): Promise<boolean> {
  for (const label of labels) {
    const target = await firstVisible(textCandidateLocators(page, label));
    if (target) {
      await target.click({ timeout: UI_TIMEOUT_MS });
      await waitForUi(page);
      return true;
    }
  }

  return false;
}

async function isAnyVisible(page: Page, labels: string[]): Promise<boolean> {
  for (const label of labels) {
    if (await firstVisible(textCandidateLocators(page, label))) {
      return true;
    }
  }
  return false;
}

async function pickGoogleAccountIfPrompted(page: Page): Promise<boolean> {
  const accountLabel = "juanlucasbarbiergarzon@gmail.com";
  const accountLocator = page.getByText(accountLabel, { exact: false });
  if (await accountLocator.first().isVisible().catch(() => false)) {
    await accountLocator.first().click();
    await waitForUi(page);
    return true;
  }
  return false;
}

async function getBusinessNameInput(page: Page): Promise<Locator | null> {
  const candidates = [
    page.getByLabel(/Nombre del Negocio/i),
    page.getByPlaceholder(/Nombre del Negocio/i),
    page.locator('input[name*="negocio" i]'),
  ];
  return firstVisible(candidates);
}

async function clickWithPopupOrNavigation(
  page: Page,
  labels: string[],
): Promise<{ targetPage: Page; finalUrl: string; openedNewTab: boolean }> {
  const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
  const navigationPromise = page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 10_000 }).catch(
    () => null,
  );

  const clicked = await clickByVisibleText(page, labels);
  expect(clicked).toBeTruthy();

  const popup = await popupPromise;
  const navigation = await navigationPromise;

  if (popup) {
    await waitForUi(popup);
    return { targetPage: popup, finalUrl: popup.url(), openedNewTab: true };
  }

  if (navigation) {
    await waitForUi(page);
  }

  return { targetPage: page, finalUrl: page.url(), openedNewTab: false };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
    ensureEvidenceDir();

    const results = createInitialResults();
    const appUrlFromEnv = process.env.SALEADS_BASE_URL || process.env.BASE_URL || "";
    let lastKnownAppUrl = appUrlFromEnv;

    const setResult = (field: (typeof REPORT_FIELDS)[number], status: StepStatus, details: string[]): void => {
      results[field] = {
        name: field,
        status,
        details,
      };
    };

    const runStep = async (
      field: (typeof REPORT_FIELDS)[number],
      execute: () => Promise<string[]>,
    ): Promise<boolean> => {
      try {
        const details = await execute();
        setResult(field, "PASS", details);
        return true;
      } catch (error) {
        setResult(field, "FAIL", [toErrorMessage(error)]);
        return false;
      }
    };

    try {
      await runStep("Login", async () => {
        if (page.url() === "about:blank") {
          if (!appUrlFromEnv) {
            throw new Error(
              "Page started at about:blank and SALEADS_BASE_URL/BASE_URL is missing. Provide a login URL for your SaleADS environment.",
            );
          }
          await page.goto(appUrlFromEnv, { waitUntil: "domcontentloaded", timeout: GO_TO_APP_TIMEOUT_MS });
        }

        await waitForUi(page);

        const loginButton = await firstVisible([
          ...textCandidateLocators(page, "Sign in with Google"),
          ...textCandidateLocators(page, "Iniciar sesión con Google"),
          ...textCandidateLocators(page, "Continuar con Google"),
          ...textCandidateLocators(page, "Google"),
        ]);
        expect(loginButton).toBeTruthy();

        const googlePopupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
        await loginButton!.click({ timeout: UI_TIMEOUT_MS });
        await waitForUi(page);

        const googlePopup = await googlePopupPromise;
        if (googlePopup) {
          await waitForUi(googlePopup);
          await pickGoogleAccountIfPrompted(googlePopup);
          await googlePopup.waitForEvent("close", { timeout: 20_000 }).catch(() => {
            // Continue if Google flow does not auto-close.
          });
          await page.bringToFront();
        }

        await pickGoogleAccountIfPrompted(page);
        await waitForUi(page);

        await expect
          .poll(
            async () => isAnyVisible(page, ["Negocio", "Mi Negocio", "Dashboard", "Inicio"]),
            { timeout: 45_000 },
          )
          .toBeTruthy();

        lastKnownAppUrl = page.url();
        const screenshotPath = await screenshotCheckpoint(page, "01-dashboard-loaded");
        return [`Main interface loaded and left sidebar visible. Screenshot: ${screenshotPath}`];
      });

      await runStep("Mi Negocio menu", async () => {
        const clicked = await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
        expect(clicked).toBeTruthy();

        await expect
          .poll(async () => isAnyVisible(page, ["Agregar Negocio"]), { timeout: UI_TIMEOUT_MS })
          .toBeTruthy();
        await expect
          .poll(async () => isAnyVisible(page, ["Administrar Negocios"]), { timeout: UI_TIMEOUT_MS })
          .toBeTruthy();

        const screenshotPath = await screenshotCheckpoint(page, "02-mi-negocio-menu-expanded");
        return [`'Mi Negocio' submenu expanded with expected entries. Screenshot: ${screenshotPath}`];
      });

      await runStep("Agregar Negocio modal", async () => {
        const clicked = await clickByVisibleText(page, ["Agregar Negocio"]);
        expect(clicked).toBeTruthy();

        await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: UI_TIMEOUT_MS });

        const businessNameInput = await getBusinessNameInput(page);
        expect(businessNameInput).toBeTruthy();
        await businessNameInput!.click();
        await businessNameInput!.fill(TEST_BUSINESS_NAME);

        const screenshotPath = await screenshotCheckpoint(page, "03-agregar-negocio-modal");
        const cancelClicked = await clickByVisibleText(page, ["Cancelar"]);
        expect(cancelClicked).toBeTruthy();

        return [`Modal validated, sample business name typed, and modal closed. Screenshot: ${screenshotPath}`];
      });

      await runStep("Administrar Negocios view", async () => {
        if (!(await isAnyVisible(page, ["Administrar Negocios"]))) {
          const expanded = await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
          expect(expanded).toBeTruthy();
        }

        const clicked = await clickByVisibleText(page, ["Administrar Negocios"]);
        expect(clicked).toBeTruthy();

        await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });

        lastKnownAppUrl = page.url();
        const screenshotPath = await screenshotCheckpoint(page, "04-administrar-negocios-account-page", true);
        return [`Account page loaded with all required sections. Screenshot: ${screenshotPath}`];
      });

      await runStep("Información General", async () => {
        const hasUserName = await isAnyVisible(page, ["Nombre", "Usuario", "Perfil"]);
        const hasUserEmail = await isAnyVisible(page, ["@", ".com", ".ai"]);
        const hasPlan = await isAnyVisible(page, ["BUSINESS PLAN"]);
        const hasChangePlan = await isAnyVisible(page, ["Cambiar Plan"]);

        expect(hasUserName).toBeTruthy();
        expect(hasUserEmail).toBeTruthy();
        expect(hasPlan).toBeTruthy();
        expect(hasChangePlan).toBeTruthy();

        return ["User name, user email, BUSINESS PLAN and Cambiar Plan are visible."];
      });

      await runStep("Detalles de la Cuenta", async () => {
        await expect(page.getByText(/Cuenta creada/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect(page.getByText(/Estado activo/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        return ["Cuenta creada, Estado activo and Idioma seleccionado are visible."];
      });

      await runStep("Tus Negocios", async () => {
        await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect
          .poll(async () => isAnyVisible(page, ["Agregar Negocio"]), { timeout: UI_TIMEOUT_MS })
          .toBeTruthy();
        return ["Business list section, Agregar Negocio button and usage text are visible."];
      });

      await runStep("Términos y Condiciones", async () => {
        const appUrlBeforeOpen = page.url();
        const { targetPage, finalUrl, openedNewTab } = await clickWithPopupOrNavigation(page, [
          "Términos y Condiciones",
          "Terminos y Condiciones",
        ]);

        await expect(targetPage.getByText(/T[ée]rminos y Condiciones/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect
          .poll(
            async () =>
              isAnyVisible(targetPage, ["condiciones", "servicio", "aceptas", "términos", "terminos"]),
            { timeout: UI_TIMEOUT_MS },
          )
          .toBeTruthy();

        const screenshotPath = await screenshotCheckpoint(targetPage, "08-terminos-y-condiciones");

        if (openedNewTab) {
          await targetPage.close();
          await page.bringToFront();
        } else if (targetPage === page && page.url() !== appUrlBeforeOpen) {
          await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
            if (lastKnownAppUrl) {
              await page.goto(lastKnownAppUrl, { waitUntil: "domcontentloaded", timeout: GO_TO_APP_TIMEOUT_MS });
            }
          });
          await waitForUi(page);
        }

        return [`Validated legal page. URL: ${finalUrl}. Screenshot: ${screenshotPath}`];
      });

      await runStep("Política de Privacidad", async () => {
        if (!(await isAnyVisible(page, ["Política de Privacidad", "Politica de Privacidad"]))) {
          if (lastKnownAppUrl) {
            await page.goto(lastKnownAppUrl, { waitUntil: "domcontentloaded", timeout: GO_TO_APP_TIMEOUT_MS });
            await waitForUi(page);
          }
        }

        const appUrlBeforeOpen = page.url();
        const { targetPage, finalUrl, openedNewTab } = await clickWithPopupOrNavigation(page, [
          "Política de Privacidad",
          "Politica de Privacidad",
        ]);

        await expect(targetPage.getByText(/Pol[íi]tica de Privacidad/i)).toBeVisible({ timeout: UI_TIMEOUT_MS });
        await expect
          .poll(
            async () => isAnyVisible(targetPage, ["privacidad", "datos personales", "información", "uso"]),
            { timeout: UI_TIMEOUT_MS },
          )
          .toBeTruthy();

        const screenshotPath = await screenshotCheckpoint(targetPage, "09-politica-de-privacidad");

        if (openedNewTab) {
          await targetPage.close();
          await page.bringToFront();
        } else if (targetPage === page && page.url() !== appUrlBeforeOpen) {
          await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
            if (lastKnownAppUrl) {
              await page.goto(lastKnownAppUrl, { waitUntil: "domcontentloaded", timeout: GO_TO_APP_TIMEOUT_MS });
            }
          });
          await waitForUi(page);
        }

        return [`Validated legal page. URL: ${finalUrl}. Screenshot: ${screenshotPath}`];
      });
    } finally {
      fs.writeFileSync(
        REPORT_FILE,
        JSON.stringify(
          {
            testName: "saleads_mi_negocio_full_test",
            generatedAt: new Date().toISOString(),
            baseUrl: appUrlFromEnv || page.url(),
            statusByField: REPORT_FIELDS.map((field) => ({
              field,
              status: results[field].status,
              details: results[field].details,
            })),
            testMetadata: {
              project: testInfo.project.name,
              workerIndex: testInfo.workerIndex,
            },
          },
          null,
          2,
        ),
      );

      await testInfo.attach("saleads-mi-negocio-final-report", {
        path: REPORT_FILE,
        contentType: "application/json",
      });
    }

    const failedFields = REPORT_FIELDS.filter((field) => results[field].status === "FAIL");
    expect(
      failedFields,
      `Failed workflow validations: ${failedFields
        .map((field) => `${field}: ${results[field].details.join(" | ")}`)
        .join(" || ")}`,
    ).toEqual([]);
  });
});
