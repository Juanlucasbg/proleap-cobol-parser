import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type ReportStatus = "PASS" | "FAIL" | "NOT_RUN";

type ReportEntry = {
  status: ReportStatus;
  details?: string;
  evidence?: Record<string, string>;
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
  "Política de Privacidad"
] as const;

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function getCaseInsensitiveRegex(text: string): RegExp {
  return new RegExp(escapeRegex(text), "i");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 30_000 });
  await locator.click();
  await waitForUi(page);
}

async function findVisibleByText(page: Page, text: string): Promise<Locator | null> {
  const regex = getCaseInsensitiveRegex(text);
  const candidates: Locator[] = [
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByRole("menuitem", { name: regex }).first(),
    page.getByRole("tab", { name: regex }).first(),
    page.getByText(regex).first()
  ];

  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  return null;
}

async function findVisibleByAnyText(page: Page, texts: string[]): Promise<Locator | null> {
  for (const text of texts) {
    const locator = await findVisibleByText(page, text);
    if (locator) {
      return locator;
    }
  }

  return null;
}

async function capture(
  page: Page,
  runDir: string,
  fileName: string,
  fullPage = false
): Promise<string> {
  const screenshotPath = path.join(runDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function selectGoogleAccountIfPresent(activePage: Page): Promise<void> {
  const accountLocator = await findVisibleByText(activePage, GOOGLE_ACCOUNT_EMAIL);
  if (!accountLocator) {
    return;
  }

  await clickAndWait(activePage, accountLocator);
}

function setReportEntry(
  report: Record<string, ReportEntry>,
  field: string,
  status: ReportStatus,
  details?: string,
  evidence?: Record<string, string>
): void {
  report[field] = {
    status,
    details,
    evidence
  };
}

function extractUserNameCandidate(sectionText: string): string | null {
  const blacklist = [
    "información general",
    "business plan",
    "cambiar plan",
    "cuenta creada",
    "estado activo",
    "idioma seleccionado"
  ];

  const line = sectionText
    .split("\n")
    .map((entry) => entry.trim())
    .filter(Boolean)
    .find(
      (entry) =>
        !entry.includes("@") &&
        !blacklist.some((term) => entry.toLowerCase().includes(term)) &&
        /[A-Za-zÁÉÍÓÚáéíóúÑñ]{3,}/.test(entry)
    );

  return line ?? null;
}

async function runValidationStep(
  report: Record<string, ReportEntry>,
  field: string,
  fn: () => Promise<Record<string, string> | void>
): Promise<void> {
  try {
    const evidence = (await fn()) ?? undefined;
    setReportEntry(report, field, "PASS", undefined, evidence);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    setReportEntry(report, field, "FAIL", message);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "NOT_RUN" as ReportStatus }])
  ) as Record<string, ReportEntry>;

  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const runDir = path.resolve(process.cwd(), "evidence", `saleads-mi-negocio-${timestamp}`);
  await fs.mkdir(runDir, { recursive: true });

  const saleadsUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (saleadsUrl) {
    await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else {
    await expect(
      page,
      "Set SALEADS_LOGIN_URL or pre-navigate page before running this spec."
    ).not.toHaveURL("about:blank");
  }

  await runValidationStep(report, "Login", async () => {
    const loginButton = await findVisibleByAnyText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google"
    ]);

    if (loginButton) {
      const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(page, loginButton);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await selectGoogleAccountIfPresent(popup);
      } else {
        await selectGoogleAccountIfPresent(page);
      }
    }

    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 90_000 });
    const dashboardShot = await capture(page, runDir, "01-dashboard-loaded.png", true);
    return { screenshot: dashboardShot };
  });

  await runValidationStep(report, "Mi Negocio menu", async () => {
    const negocioSection = await findVisibleByText(page, "Negocio");
    if (negocioSection) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocioOption = await findVisibleByText(page, "Mi Negocio");
    if (!miNegocioOption) {
      throw new Error("No se encontró la opción 'Mi Negocio' en la barra lateral.");
    }

    await clickAndWait(page, miNegocioOption);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 30_000 });

    const menuShot = await capture(page, runDir, "02-mi-negocio-menu-expanded.png");
    return { screenshot: menuShot };
  });

  await runValidationStep(report, "Agregar Negocio modal", async () => {
    const agregarNegocioOption = await findVisibleByText(page, "Agregar Negocio");
    if (!agregarNegocioOption) {
      throw new Error("No se encontró la opción 'Agregar Negocio'.");
    }

    await clickAndWait(page, agregarNegocioOption);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 30_000 });

    const businessNameInput = page
      .getByLabel(/Nombre del Negocio/i)
      .or(page.getByPlaceholder(/Nombre del Negocio/i))
      .first();
    await expect(businessNameInput).toBeVisible({ timeout: 30_000 });

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    const modalShot = await capture(page, runDir, "03-crear-nuevo-negocio-modal.png");

    await clickAndWait(page, businessNameInput);
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /Cancelar/i }).first());

    return { screenshot: modalShot };
  });

  await runValidationStep(report, "Administrar Negocios view", async () => {
    const adminOption = await findVisibleByText(page, "Administrar Negocios");
    if (!adminOption) {
      const miNegocioOption = await findVisibleByText(page, "Mi Negocio");
      if (!miNegocioOption) {
        throw new Error("No se pudo expandir 'Mi Negocio' para encontrar 'Administrar Negocios'.");
      }

      await clickAndWait(page, miNegocioOption);
    }

    const refreshedAdminOption = await findVisibleByText(page, "Administrar Negocios");
    if (!refreshedAdminOption) {
      throw new Error("No se encontró la opción 'Administrar Negocios'.");
    }

    await clickAndWait(page, refreshedAdminOption);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 45_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 45_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 45_000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 45_000 });

    const accountShot = await capture(page, runDir, "04-administrar-negocios-page.png", true);
    return { screenshot: accountShot };
  });

  await runValidationStep(report, "Información General", async () => {
    const infoHeading = page.getByText(/Información General/i).first();
    await expect(infoHeading).toBeVisible({ timeout: 30_000 });

    const infoSection = infoHeading.locator("xpath=ancestor::*[self::section or self::div][1]");
    await expect(infoSection).toBeVisible();

    const exactEmailLocator = page.getByText(getCaseInsensitiveRegex(GOOGLE_ACCOUNT_EMAIL)).first();
    const hasExactEmail = await exactEmailLocator.isVisible().catch(() => false);
    if (hasExactEmail) {
      await expect(exactEmailLocator).toBeVisible();
    } else {
      await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    }

    const sectionText = await infoSection.innerText();
    const userNameCandidate = extractUserNameCandidate(sectionText);
    if (!userNameCandidate) {
      throw new Error("No se encontró un nombre de usuario visible en 'Información General'.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runValidationStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 30_000 });
  });

  await runValidationStep(report, "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({
      timeout: 30_000
    });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 30_000 });
  });

  const validateLegalLink = async (
    linkText: string,
    headingText: RegExp,
    screenshotFile: string
  ): Promise<{ screenshot: string; finalUrl: string }> => {
    const link = await findVisibleByText(page, linkText);
    if (!link) {
      throw new Error(`No se encontró el enlace legal '${linkText}'.`);
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, link);

    const popup = await popupPromise;
    const legalPage = popup ?? page;

    await legalPage.waitForLoadState("domcontentloaded");
    await expect(legalPage.getByText(headingText).first()).toBeVisible({ timeout: 30_000 });

    const legalText = await legalPage.locator("body").innerText();
    if (legalText.trim().length < 120) {
      throw new Error(`El contenido de '${linkText}' parece incompleto.`);
    }

    const screenshot = await capture(legalPage, runDir, screenshotFile, true);
    const finalUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(page);
    }

    return { screenshot, finalUrl };
  };

  await runValidationStep(report, "Términos y Condiciones", async () => {
    return validateLegalLink(
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones.png"
    );
  });

  await runValidationStep(report, "Política de Privacidad", async () => {
    return validateLegalLink(
      "Política de Privacidad",
      /Política de Privacidad/i,
      "06-politica-de-privacidad.png"
    );
  });

  const finalReport = REPORT_FIELDS.map((field) => ({
    field,
    status: report[field].status,
    details: report[field].details ?? "",
    evidence: report[field].evidence ?? {}
  }));

  await fs.writeFile(path.join(runDir, "final-report.json"), JSON.stringify(finalReport, null, 2), "utf-8");

  console.log("SaleADS Mi Negocio Final Report");
  for (const entry of finalReport) {
    console.log(`- ${entry.field}: ${entry.status}${entry.details ? ` (${entry.details})` : ""}`);
    if (entry.evidence.finalUrl) {
      console.log(`  URL: ${entry.evidence.finalUrl}`);
    }
  }

  const failed = finalReport.filter((entry) => entry.status !== "PASS");
  expect(
    failed,
    `Validation failures:\n${failed.map((entry) => `${entry.field}: ${entry.details || entry.status}`).join("\n")}`
  ).toHaveLength(0);
});
