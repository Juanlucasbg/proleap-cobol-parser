import { expect, type Locator, type Page, test } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import * as path from "node:path";

type ReportStatus = "PASS" | "FAIL";

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

const REPORT_FIELDS: ReportField[] = [
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

const ARTIFACTS_DIR = path.resolve(process.cwd(), "artifacts", "saleads-mi-negocio");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");

function ensureArtifactsDirectories(): void {
  mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

function fileSafe(input: string): string {
  return input.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForTimeout(800);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUiToSettle(page);
}

async function firstVisible(candidates: Locator[], timeoutMs = 3000): Promise<Locator | null> {
  for (const candidate of candidates) {
    const first = candidate.first();
    try {
      await first.waitFor({ state: "visible", timeout: timeoutMs });
      return first;
    } catch {
      // Keep trying the next selector.
    }
  }

  return null;
}

async function findByVisibleText(page: Page, pattern: RegExp): Promise<Locator | null> {
  return firstVisible(
    [
      page.getByRole("button", { name: pattern }),
      page.getByRole("link", { name: pattern }),
      page.getByRole("menuitem", { name: pattern }),
      page.getByRole("tab", { name: pattern }),
      page.getByRole("option", { name: pattern }),
      page.getByText(pattern),
    ],
    4000,
  );
}

async function sidebarIsVisible(page: Page): Promise<boolean> {
  const sidebar = await firstVisible(
    [page.locator("aside"), page.getByRole("navigation"), page.locator('[class*="sidebar"]')],
    2500,
  );
  return Boolean(sidebar);
}

async function screenshot(page: Page, name: string, fullPage = false): Promise<string> {
  const filename = `${Date.now()}-${fileSafe(name)}.png`;
  const filePath = path.join(SCREENSHOTS_DIR, filename);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function sectionText(page: Page, headingPattern: RegExp): Promise<string> {
  const heading = page.getByRole("heading", { name: headingPattern }).first();
  await expect(heading).toBeVisible();

  const container = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  return container.innerText();
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  ensureArtifactsDirectories();

  const report: Record<ReportField, { status: ReportStatus; details: string }> = REPORT_FIELDS.reduce(
    (acc, key) => {
      acc[key] = { status: "FAIL", details: "Not executed." };
      return acc;
    },
    {} as Record<ReportField, { status: ReportStatus; details: string }>,
  );
  const failures: string[] = [];
  const legalUrls: Record<string, string> = {};
  const checkpointScreenshots: Record<string, string> = {};
  let loginSucceeded = false;

  const recordStep = async (field: ReportField, callback: () => Promise<void>): Promise<void> => {
    try {
      await callback();
      report[field] = { status: "PASS", details: "Validation completed successfully." };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[field] = { status: "FAIL", details: message };
      failures.push(`${field}: ${message}`);
    }
  };

  await recordStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No login page is loaded. Provide SALEADS_LOGIN_URL (or BASE_URL) to run this flow in the current environment.",
      );
    }

    const loggedInBeforeClick = await sidebarIsVisible(page);
    if (!loggedInBeforeClick) {
      const loginButton = await findByVisibleText(
        page,
        /sign in with google|iniciar sesi[oó]n con google|ingresar con google|continuar con google|google/i,
      );
      if (!loginButton) {
        throw new Error("Login button for Google was not found.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickAndWait(page, loginButton);

      const googlePage = (await popupPromise) ?? page;
      await waitForUiToSettle(googlePage);

      const accountOption = googlePage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
      try {
        await accountOption.waitFor({ state: "visible", timeout: 7000 });
        await clickAndWait(googlePage, accountOption);
      } catch {
        // Account chooser may not appear if session is already active.
      }

      if (googlePage !== page) {
        await googlePage.waitForEvent("close", { timeout: 30000 }).catch(() => undefined);
      }
    }

    await expect
      .poll(async () => sidebarIsVisible(page), {
        timeout: 90000,
        message: "Main application interface with left sidebar did not appear after login.",
      })
      .toBe(true);

    checkpointScreenshots.dashboard = await screenshot(page, "dashboard-loaded");
    loginSucceeded = true;
  });

  await recordStep("Mi Negocio menu", async () => {
    if (!loginSucceeded) {
      throw new Error("Skipped because login validation failed.");
    }

    const negocioSection = await findByVisibleText(page, /^Negocio$/i);
    if (negocioSection) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocioOption = await findByVisibleText(page, /^Mi Negocio$/i);
    if (!miNegocioOption) {
      throw new Error("Could not find 'Mi Negocio' option in left sidebar.");
    }
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();

    checkpointScreenshots.menu = await screenshot(page, "mi-negocio-expanded-menu");
  });

  await recordStep("Agregar Negocio modal", async () => {
    if (!loginSucceeded) {
      throw new Error("Skipped because login validation failed.");
    }

    const agregarNegocio = await findByVisibleText(page, /^Agregar Negocio$/i);
    if (!agregarNegocio) {
      throw new Error("'Agregar Negocio' option is not visible.");
    }
    await clickAndWait(page, agregarNegocio);

    const modalTitle = page.getByRole("heading", { name: /^Crear Nuevo Negocio$/i });
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/^Nombre del Negocio$/i)).toBeVisible();
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    const nameInput = page.getByLabel(/^Nombre del Negocio$/i);
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }));

    checkpointScreenshots.modal = await screenshot(page, "agregar-negocio-modal");
  });

  await recordStep("Administrar Negocios view", async () => {
    if (!loginSucceeded) {
      throw new Error("Skipped because login validation failed.");
    }

    const miNegocioOption = await findByVisibleText(page, /^Mi Negocio$/i);
    if (miNegocioOption) {
      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegocios = await findByVisibleText(page, /^Administrar Negocios$/i);
    if (!administrarNegocios) {
      throw new Error("'Administrar Negocios' option is not visible.");
    }
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByRole("heading", { name: /Informaci[oó]n General/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Detalles de la Cuenta/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Tus Negocios/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Secci[oó]n Legal/i })).toBeVisible();

    checkpointScreenshots.account = await screenshot(page, "administrar-negocios-account-page", true);
  });

  await recordStep("Información General", async () => {
    if (!loginSucceeded) {
      throw new Error("Skipped because login validation failed.");
    }

    const infoText = await sectionText(page, /Informaci[oó]n General/i);

    if (!/[A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,}/.test(infoText)) {
      throw new Error("A user full name was not detected in 'Información General'.");
    }
    if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoText)) {
      throw new Error("A user email was not detected in 'Información General'.");
    }
    if (!/BUSINESS PLAN/i.test(infoText)) {
      throw new Error("'BUSINESS PLAN' text is missing in 'Información General'.");
    }
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await recordStep("Detalles de la Cuenta", async () => {
    if (!loginSucceeded) {
      throw new Error("Skipped because login validation failed.");
    }

    const detailsText = await sectionText(page, /Detalles de la Cuenta/i);
    if (!/Cuenta creada/i.test(detailsText)) {
      throw new Error("'Cuenta creada' is missing in 'Detalles de la Cuenta'.");
    }
    if (!/Estado activo/i.test(detailsText)) {
      throw new Error("'Estado activo' is missing in 'Detalles de la Cuenta'.");
    }
    if (!/Idioma seleccionado/i.test(detailsText)) {
      throw new Error("'Idioma seleccionado' is missing in 'Detalles de la Cuenta'.");
    }
  });

  await recordStep("Tus Negocios", async () => {
    if (!loginSucceeded) {
      throw new Error("Skipped because login validation failed.");
    }

    const negociosHeading = page.getByRole("heading", { name: /Tus Negocios/i }).first();
    await expect(negociosHeading).toBeVisible();
    const negociosSection = negociosHeading.locator(
      "xpath=ancestor::*[self::section or self::article or self::div][1]",
    );

    await expect(negociosSection.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(negociosSection.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();

    const listItemCount = await negociosSection.locator("li, [role='listitem'], tbody tr").count();
    const negociosText = await negociosSection.innerText();
    if (listItemCount === 0 && !/Negocio/i.test(negociosText)) {
      throw new Error("Business list content was not detected in 'Tus Negocios'.");
    }
  });

  const validateLegalPage = async (
    linkName: RegExp,
    headingName: RegExp,
    screenshotName: string,
    urlKey: string,
  ): Promise<void> => {
    const applicationUrl = page.url();
    const legalLink = await findByVisibleText(page, linkName);
    if (!legalLink) {
      throw new Error(`Legal link not found: ${linkName.toString()}`);
    }

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, legalLink);

    let legalPage = await popupPromise;
    if (!legalPage) {
      legalPage = page;
      await waitForUiToSettle(legalPage);
    } else {
      await waitForUiToSettle(legalPage);
    }

    await expect(legalPage.getByRole("heading", { name: headingName })).toBeVisible();
    const legalText = await legalPage.locator("body").innerText();
    if (legalText.trim().length < 120) {
      throw new Error("Legal content text seems too short or missing.");
    }

    legalUrls[urlKey] = legalPage.url();
    checkpointScreenshots[screenshotName] = await screenshot(legalPage, screenshotName, true);

    if (legalPage !== page) {
      await legalPage.close();
      await waitForUiToSettle(page);
      return;
    }

    if (page.url() !== applicationUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        await page.goto(applicationUrl, { waitUntil: "domcontentloaded" });
      });
      await waitForUiToSettle(page);
    }
  };

  await recordStep("Términos y Condiciones", async () => {
    if (!loginSucceeded) {
      throw new Error("Skipped because login validation failed.");
    }

    await validateLegalPage(
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "terminos-y-condiciones",
      "terminosYCondiciones",
    );
  });

  await recordStep("Política de Privacidad", async () => {
    if (!loginSucceeded) {
      throw new Error("Skipped because login validation failed.");
    }

    await validateLegalPage(
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "politica-de-privacidad",
      "politicaDePrivacidad",
    );
  });

  const reportPayload = {
    testName: "saleads_mi_negocio_full_test",
    executedAt: new Date().toISOString(),
    loginUrl: process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL ?? page.url(),
    report,
    legalUrls,
    screenshots: checkpointScreenshots,
  };

  const reportJsonPath = path.join(ARTIFACTS_DIR, "final-report.json");
  const reportMarkdownPath = path.join(ARTIFACTS_DIR, "final-report.md");

  writeFileSync(reportJsonPath, JSON.stringify(reportPayload, null, 2), "utf8");
  const markdown = [
    "# SaleADS Mi Negocio Full Test",
    "",
    `- Executed at: ${reportPayload.executedAt}`,
    `- Login URL: ${reportPayload.loginUrl}`,
    "",
    "## PASS/FAIL by Validation Step",
    "",
    ...REPORT_FIELDS.map((field) => {
      const result = report[field];
      return `- **${field}**: ${result.status}${result.details ? ` (${result.details})` : ""}`;
    }),
    "",
    "## Legal URLs",
    "",
    `- Términos y Condiciones: ${legalUrls.terminosYCondiciones ?? "N/A"}`,
    `- Política de Privacidad: ${legalUrls.politicaDePrivacidad ?? "N/A"}`,
  ].join("\n");
  writeFileSync(reportMarkdownPath, markdown, "utf8");

  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
