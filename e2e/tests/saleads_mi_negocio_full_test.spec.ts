import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

const GOOGLE_ACCOUNT_EMAIL = process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const SALEADS_START_URL = process.env.SALEADS_START_URL;
const REPORT_NAME = "saleads_mi_negocio_full_test";

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

type ReportResult = {
  status: "PASS" | "FAIL";
  details?: string;
  evidence?: string[];
  url?: string;
};

const report: Record<ReportField, ReportResult> = {
  Login: { status: "FAIL" },
  "Mi Negocio menu": { status: "FAIL" },
  "Agregar Negocio modal": { status: "FAIL" },
  "Administrar Negocios view": { status: "FAIL" },
  "Información General": { status: "FAIL" },
  "Detalles de la Cuenta": { status: "FAIL" },
  "Tus Negocios": { status: "FAIL" },
  "Términos y Condiciones": { status: "FAIL" },
  "Política de Privacidad": { status: "FAIL" },
};

const screenshotDir = path.resolve(__dirname, "..", "screenshots");
const reportDir = path.resolve(__dirname, "..", "test-results");

function ensureArtifactFolders(): void {
  fs.mkdirSync(screenshotDir, { recursive: true });
  fs.mkdirSync(reportDir, { recursive: true });
}

function escapeRegex(source: string): string {
  return source.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 5_000 });
  } catch {
    // Network idle can be noisy on SPAs; DOM ready is enough fallback.
  }
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    const count = await locator.count();
    for (let idx = 0; idx < count; idx += 1) {
      const candidate = locator.nth(idx);
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
  }
  return null;
}

async function clickUsingVisibleText(page: Page, texts: string[]): Promise<void> {
  for (const text of texts) {
    const pattern = new RegExp(escapeRegex(text), "i");
    const candidate = await firstVisible([
      page.getByRole("button", { name: pattern }),
      page.getByRole("link", { name: pattern }),
      page.getByRole("menuitem", { name: pattern }),
      page.getByRole("tab", { name: pattern }),
      page.getByText(pattern),
    ]);

    if (candidate) {
      await candidate.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Could not find a visible element with texts: ${texts.join(", ")}`);
}

async function takeCheckpoint(page: Page, filename: string, fullPage = false): Promise<string> {
  const filePath = path.join(screenshotDir, filename);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function setPass(field: ReportField, details?: string, evidence?: string[], url?: string): void {
  report[field] = { status: "PASS", details, evidence, url };
}

function setFail(field: ReportField, error: unknown): void {
  report[field] = {
    status: "FAIL",
    details: error instanceof Error ? error.message : String(error),
  };
}

async function writeReport(): Promise<void> {
  const outputPath = path.join(reportDir, `${REPORT_NAME}-report.json`);
  fs.writeFileSync(
    outputPath,
    JSON.stringify(
      {
        test_name: REPORT_NAME,
        generated_at: new Date().toISOString(),
        results: report,
      },
      null,
      2,
    ),
    "utf-8",
  );
}

async function attemptStep(field: ReportField, action: () => Promise<ReportResult>): Promise<void> {
  try {
    const result = await action();
    setPass(field, result.details, result.evidence, result.url);
  } catch (error) {
    setFail(field, error);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  ensureArtifactFolders();

  if (!SALEADS_START_URL) {
    throw new Error("Missing SALEADS_START_URL. Provide the current environment login URL at runtime.");
  }

  await page.goto(SALEADS_START_URL, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await attemptStep("Login", async () => {
    const loginButton = await firstVisible([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
    ]);

    if (!loginButton) {
      throw new Error("Could not find login with Google button.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const googlePage = await popupPromise;
    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded");
      const accountOption = await firstVisible([
        googlePage.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")),
        googlePage.getByRole("button", { name: new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i") }),
      ]);

      if (accountOption) {
        await accountOption.click();
      }

      try {
        await googlePage.waitForClose({ timeout: 20_000 });
      } catch {
        // Popup may remain open depending on Google flow and account state.
      }
    } else {
      const accountOption = await firstVisible([
        page.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")),
        page.getByRole("button", { name: new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i") }),
      ]);
      if (accountOption) {
        await accountOption.click();
      }
    }

    await waitForUi(page);

    await expect(
      firstVisible([
        page.getByRole("navigation"),
        page.locator("aside"),
      ]),
    ).resolves.not.toBeNull();
    await expect(page.getByText(/negocio|mi negocio/i)).toBeVisible();

    const screenshot = await takeCheckpoint(page, "01-dashboard-loaded.png");
    return {
      details: "Main application interface and left sidebar are visible after Google login.",
      evidence: [screenshot],
    };
  });

  await attemptStep("Mi Negocio menu", async () => {
    await clickUsingVisibleText(page, ["Negocio", "Mi Negocio"]);
    await clickUsingVisibleText(page, ["Mi Negocio"]);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();

    const screenshot = await takeCheckpoint(page, "02-mi-negocio-menu-expanded.png");
    return {
      details: "Mi Negocio submenu expanded and shows both expected options.",
      evidence: [screenshot],
    };
  });

  await attemptStep("Agregar Negocio modal", async () => {
    await clickUsingVisibleText(page, ["Agregar Negocio"]);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    const businessNameInput = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
      page.locator("input[name*='negocio' i]"),
    ]);
    if (!businessNameInput) {
      throw new Error("Input 'Nombre del Negocio' was not found.");
    }
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    const screenshot = await takeCheckpoint(page, "03-agregar-negocio-modal.png");
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickUsingVisibleText(page, ["Cancelar"]);

    return {
      details: "Agregar Negocio modal validated and optional fill/cancel action completed.",
      evidence: [screenshot],
    };
  });

  await attemptStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/Administrar Negocios/i).isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickUsingVisibleText(page, ["Mi Negocio"]);
    }
    await clickUsingVisibleText(page, ["Administrar Negocios"]);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();

    const screenshot = await takeCheckpoint(page, "04-administrar-negocios-view.png", true);
    return {
      details: "Administrar Negocios page loaded with all major sections.",
      evidence: [screenshot],
    };
  });

  await attemptStep("Información General", async () => {
    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const textSnapshot = await page.locator("body").innerText();
    if (!/Informaci[oó]n General[\s\S]{0,250}[A-Za-zÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑ\s]{1,}/i.test(textSnapshot)) {
      throw new Error("No obvious user name text was detected in Información General.");
    }

    return {
      details: "Información General includes user identity, email, plan, and plan-change action.",
    };
  });

  await attemptStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();

    return {
      details: "Detalles de la Cuenta contains creation date, active status, and selected language.",
    };
  });

  await attemptStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();

    const businessesList = await firstVisible([
      page.locator("ul, table, div").filter({ hasText: /negocio/i }),
      page.getByText(/negocio/i),
    ]);
    if (!businessesList) {
      throw new Error("Business list/content was not found in 'Tus Negocios'.");
    }

    return {
      details: "Tus Negocios list, quota text, and add button are visible.",
    };
  });

  async function validateLegalLink(
    field: "Términos y Condiciones" | "Política de Privacidad",
    linkText: string,
    heading: RegExp,
    screenshotFile: string,
  ): Promise<void> {
    await attemptStep(field, async () => {
      const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
      const sameTabNavigation = page.waitForNavigation({ timeout: 10_000 }).catch(() => null);

      await clickUsingVisibleText(page, [linkText]);

      let legalPage = await popupPromise;
      if (!legalPage) {
        await sameTabNavigation;
        legalPage = page;
      } else {
        await legalPage.waitForLoadState("domcontentloaded");
      }

      await waitForUi(legalPage);
      await expect(legalPage.getByText(heading)).toBeVisible();

      const legalContent = (await legalPage.locator("body").innerText()).trim();
      if (legalContent.length < 200) {
        throw new Error(`${field} content appears too short to be a full legal page.`);
      }

      const screenshot = await takeCheckpoint(legalPage, screenshotFile, true);
      const finalUrl = legalPage.url();

      if (legalPage !== page) {
        await legalPage.close();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      }

      await waitForUi(page);
      return {
        details: `${field} page validated with visible heading and legal text.`,
        evidence: [screenshot],
        url: finalUrl,
      };
    });
  }

  await validateLegalLink(
    "Términos y Condiciones",
    "Términos y Condiciones",
    /Términos y Condiciones/i,
    "08-terminos-y-condiciones.png",
  );

  await validateLegalLink(
    "Política de Privacidad",
    "Política de Privacidad",
    /Política de Privacidad/i,
    "09-politica-de-privacidad.png",
  );

  await writeReport();

  const failedFields = Object.entries(report).filter(([, result]) => result.status === "FAIL").map(([field]) => field);
  if (failedFields.length > 0) {
    throw new Error(`Workflow completed with failing validations: ${failedFields.join(", ")}`);
  }
});
