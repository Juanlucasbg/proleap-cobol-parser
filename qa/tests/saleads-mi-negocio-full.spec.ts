import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type TestReport = {
  name: string;
  executedAt: string;
  environment: string;
  statuses: Record<string, StepStatus>;
  details: Record<string, string>;
  evidence: {
    screenshots: string[];
    finalUrls: {
      termsAndConditions?: string;
      privacyPolicy?: string;
    };
  };
};

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
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

const SCREENSHOT_DIR = path.join("test-results", TEST_NAME, "screenshots");
const REPORT_FILE = path.join("test-results", TEST_NAME, "final-report.json");

async function ensureEvidencePaths(): Promise<void> {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
  await fs.mkdir(path.dirname(REPORT_FILE), { recursive: true });
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 7_500 });
  } catch {
    // Some SPA views keep background network traffic alive; domcontentloaded is sufficient fallback.
  }
  await page.waitForTimeout(400);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function firstVisible(candidates: Locator[], timeoutMs = 15_000): Promise<Locator | null> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const candidate of candidates) {
      const first = candidate.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return null;
}

function screenshotPath(label: string): string {
  const safeLabel = label.replace(/[^a-zA-Z0-9-_]/g, "-");
  return path.join(SCREENSHOT_DIR, `${Date.now()}-${safeLabel}.png`);
}

async function capture(page: Page, label: string, screenshots: string[], fullPage = false): Promise<void> {
  const filePath = screenshotPath(label);
  await page.screenshot({ path: filePath, fullPage });
  screenshots.push(filePath);
}

async function chooseGoogleAccountIfVisible(page: Page): Promise<boolean> {
  const accountCandidate = await firstVisible(
    [
      page.getByText(GOOGLE_ACCOUNT, { exact: false }),
      page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT, "i") }),
      page.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT, "i") }),
    ],
    8_000,
  );

  if (!accountCandidate) {
    return false;
  }

  await accountCandidate.click();
  await waitForUi(page);
  return true;
}

async function ensureOnLoginPage(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    return;
  }

  const configuredLoginUrl = test.info().config.use.baseURL || process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
  if (!configuredLoginUrl) {
    throw new Error(
      "No login page URL available. Open the login page before running, or set SALEADS_LOGIN_URL/SALEADS_URL/BASE_URL.",
    );
  }

  await page.goto(String(configuredLoginUrl), { waitUntil: "domcontentloaded" });
  await waitForUi(page);
}

async function runStep(
  report: TestReport,
  field: (typeof REPORT_FIELDS)[number],
  stepFn: () => Promise<void>,
): Promise<void> {
  try {
    await stepFn();
    report.statuses[field] = "PASS";
    report.details[field] = "Validated successfully.";
  } catch (error) {
    report.statuses[field] = "FAIL";
    report.details[field] = error instanceof Error ? error.message : String(error);
  }
}

test(TEST_NAME, async ({ context, page }) => {
  await ensureEvidencePaths();

  const report: TestReport = {
    name: TEST_NAME,
    executedAt: new Date().toISOString(),
    environment: "unknown",
    statuses: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])) as Record<string, StepStatus>,
    details: {},
    evidence: {
      screenshots: [],
      finalUrls: {},
    },
  };

  await runStep(report, "Login", async () => {
    await ensureOnLoginPage(page);
    report.environment = page.url() === "about:blank" ? "unknown" : new URL(page.url()).origin;

    const loginButton = await firstVisible(
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ],
      20_000,
    );
    if (!loginButton) {
      throw new Error("Google login button not found.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await chooseGoogleAccountIfVisible(popup);
      await popup.waitForTimeout(1_000);
    } else {
      await chooseGoogleAccountIfVisible(page);
    }

    await page.bringToFront();
    await waitForUi(page);

    const sidebar = await firstVisible([page.locator("aside"), page.locator("nav"), page.getByText(/negocio/i)], 20_000);
    if (!sidebar) {
      throw new Error("Main application/sidebar did not appear after login.");
    }

    await capture(page, "dashboard-loaded", report.evidence.screenshots, true);
  });

  await runStep(report, "Mi Negocio menu", async () => {
    const negocioSection = await firstVisible(
      [
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^negocio$/i),
      ],
      20_000,
    );
    if (!negocioSection) {
      throw new Error("Sidebar section 'Negocio' not found.");
    }

    const miNegocioOption = await firstVisible(
      [
        page.getByRole("button", { name: /^mi negocio$/i }),
        page.getByRole("link", { name: /^mi negocio$/i }),
        page.getByText(/^mi negocio$/i),
      ],
      10_000,
    );
    if (!miNegocioOption) {
      throw new Error("Option 'Mi Negocio' not found.");
    }

    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();
    await capture(page, "mi-negocio-expanded-menu", report.evidence.screenshots, true);
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    const addBusinessMenuItem = await firstVisible(
      [
        page.getByRole("button", { name: /^agregar negocio$/i }),
        page.getByRole("link", { name: /^agregar negocio$/i }),
        page.getByText(/^agregar negocio$/i),
      ],
      10_000,
    );
    if (!addBusinessMenuItem) {
      throw new Error("Menu item 'Agregar Negocio' not found.");
    }

    await clickAndWait(addBusinessMenuItem, page);
    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await capture(page, "agregar-negocio-modal", report.evidence.screenshots);

    const businessNameInput = await firstVisible(
      [page.getByLabel(/nombre del negocio/i), page.getByPlaceholder(/nombre del negocio/i)],
      5_000,
    );
    if (businessNameInput) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }

    const cancelButton = page.getByRole("button", { name: /cancelar/i }).first();
    await clickAndWait(cancelButton, page);
  });

  await runStep(report, "Administrar Negocios view", async () => {
    const miNegocioToggle = await firstVisible(
      [page.getByRole("button", { name: /^mi negocio$/i }), page.getByText(/^mi negocio$/i)],
      8_000,
    );
    if (miNegocioToggle) {
      await clickAndWait(miNegocioToggle, page);
    }

    const manageBusinesses = await firstVisible(
      [
        page.getByRole("button", { name: /^administrar negocios$/i }),
        page.getByRole("link", { name: /^administrar negocios$/i }),
        page.getByText(/^administrar negocios$/i),
      ],
      10_000,
    );
    if (!manageBusinesses) {
      throw new Error("Option 'Administrar Negocios' not found.");
    }

    await clickAndWait(manageBusinesses, page);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();
    await capture(page, "administrar-negocios-account-page", report.evidence.screenshots, true);
  });

  await runStep(report, "Información General", async () => {
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    await expect(page.getByText(/@/)).toBeVisible();

    const userNameCandidate = await firstVisible(
      [
        page.locator("section").getByText(/[A-Za-z]{2,}\s+[A-Za-z]{2,}/),
        page.getByText(/nombre|name/i),
      ],
      8_000,
    );
    if (!userNameCandidate) {
      throw new Error("User name is not visible in Información General.");
    }
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await runStep(report, "Términos y Condiciones", async () => {
    const termsLink = await firstVisible(
      [
        page.getByRole("link", { name: /t[eé]rminos y condiciones/i }),
        page.getByText(/t[eé]rminos y condiciones/i),
      ],
      10_000,
    );
    if (!termsLink) {
      throw new Error("Link 'Términos y Condiciones' not found.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(termsLink, page);
    const popup = await popupPromise;
    const legalPage = popup ?? page;

    await legalPage.waitForLoadState("domcontentloaded");
    await expect(legalPage.getByText(/t[eé]rminos y condiciones/i)).toBeVisible();
    await expect(legalPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúñÑ]{20,}/);
    report.evidence.finalUrls.termsAndConditions = legalPage.url();
    await capture(legalPage, "terminos-y-condiciones", report.evidence.screenshots, true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  });

  await runStep(report, "Política de Privacidad", async () => {
    const privacyLink = await firstVisible(
      [
        page.getByRole("link", { name: /pol[ií]tica de privacidad/i }),
        page.getByText(/pol[ií]tica de privacidad/i),
      ],
      10_000,
    );
    if (!privacyLink) {
      throw new Error("Link 'Política de Privacidad' not found.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(privacyLink, page);
    const popup = await popupPromise;
    const legalPage = popup ?? page;

    await legalPage.waitForLoadState("domcontentloaded");
    await expect(legalPage.getByText(/pol[ií]tica de privacidad/i)).toBeVisible();
    await expect(legalPage.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúñÑ]{20,}/);
    report.evidence.finalUrls.privacyPolicy = legalPage.url();
    await capture(legalPage, "politica-de-privacidad", report.evidence.screenshots, true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  });

  await fs.writeFile(REPORT_FILE, JSON.stringify(report, null, 2), "utf8");
  console.log(`Final report written to: ${REPORT_FILE}`);
  console.table(report.statuses);

  const failedSteps = Object.entries(report.statuses).filter(([, status]) => status === "FAIL");
  expect(failedSteps, `Failed workflow validations: ${JSON.stringify(failedSteps)}`).toHaveLength(0);
});
