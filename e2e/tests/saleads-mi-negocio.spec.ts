import { expect, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

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

type StepStatus = "PASS" | "FAIL" | "SKIPPED";

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

const DEFAULT_EVIDENCE_DIR = path.join("test-results", "saleads-mi-negocio");

const normalizeSpaces = (value: string): string => value.replace(/\s+/g, " ").trim();

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

async function firstVisible(page: Page, candidates: Locator[], timeoutMs = 15_000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error("No candidate element became visible in time.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

function buildReportTemplate(): Record<ReportField, StepStatus> {
  return REPORT_FIELDS.reduce(
    (acc, field) => ({ ...acc, [field]: "SKIPPED" }),
    {} as Record<ReportField, StepStatus>,
  );
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report = buildReportTemplate();
  const errors: string[] = [];
  const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};

  const evidenceDir = path.resolve(process.env.SALEADS_EVIDENCE_DIR ?? DEFAULT_EVIDENCE_DIR);
  fs.mkdirSync(evidenceDir, { recursive: true });

  let screenshotIndex = 1;
  const screenshot = async (name: string, fullPage = false): Promise<string> => {
    const fileName = `${String(screenshotIndex).padStart(2, "0")}_${name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "_")}.png`;
    screenshotIndex += 1;
    const filePath = path.join(evidenceDir, fileName);
    await page.screenshot({ path: filePath, fullPage });
    return filePath;
  };

  const executeStep = async (field: ReportField, run: () => Promise<void>): Promise<void> => {
    await test.step(field, async () => {
      try {
        await run();
        report[field] = "PASS";
      } catch (error) {
        report[field] = "FAIL";
        const message = error instanceof Error ? error.message : String(error);
        errors.push(`${field}: ${message}`);
      }
    });
  };

  await executeStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    } else if (page.url() === "about:blank") {
      throw new Error("Set SALEADS_LOGIN_URL or start with an already-open SaleADS login page.");
    }

    await waitForUi(page);

    const googleButton = await firstVisible(page, [
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
    ]);
    await clickAndWait(page, googleButton);

    const googleAccount = page.getByText("juanlucasbarbiergarzon@gmail.com");
    if (await googleAccount.first().isVisible().catch(() => false)) {
      await clickAndWait(page, googleAccount.first());
    }

    const sidebar = await firstVisible(page, [
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/^Negocio$/i),
      page.getByText(/^Mi Negocio$/i),
    ]);
    await expect(sidebar).toBeVisible();
    await screenshot("dashboard_loaded");
  });

  await executeStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisible(page, [
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocio = await firstVisible(page, [
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
    ]);
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await screenshot("mi_negocio_menu_expanded");
  });

  await executeStep("Agregar Negocio modal", async () => {
    await clickAndWait(page, page.getByText(/^Agregar Negocio$/i).first());

    const modalTitle = page.getByText(/^Crear Nuevo Negocio$/i).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();
    await screenshot("agregar_negocio_modal");

    await page.getByLabel(/Nombre del Negocio/i).first().fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }).first());
    await expect(modalTitle).not.toBeVisible();
  });

  await executeStep("Administrar Negocios view", async () => {
    const miNegocio = await firstVisible(page, [
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
    ]);
    await clickAndWait(page, miNegocio);

    await clickAndWait(page, page.getByText(/^Administrar Negocios$/i).first());

    await expect(page.getByText(/^Información General$/i).first()).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
    await screenshot("administrar_negocios_view_full", true);
  });

  await executeStep("Información General", async () => {
    const infoSection = page.locator("section, div").filter({ has: page.getByText(/^Información General$/i) }).first();
    await expect(infoSection).toBeVisible();

    const textBlob = normalizeSpaces((await infoSection.innerText()) || "");
    if (!/[A-Za-zÀ-ÿ]/.test(textBlob)) {
      throw new Error("No textual information found in Información General.");
    }

    const email = page.locator('text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/').first();
    await expect(email).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await executeStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await executeStep("Tus Negocios", async () => {
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  const validateLegalPage = async (
    linkText: "Términos y Condiciones" | "Política de Privacidad",
    headingText: RegExp,
  ): Promise<string> => {
    const legalLink = await firstVisible(page, [
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByText(new RegExp(linkText, "i")),
    ]);

    const [newPage] = await Promise.all([
      context.waitForEvent("page", { timeout: 7_000 }).catch(() => null as Page | null),
      legalLink.click(),
    ]);

    let targetPage: Page = page;
    if (newPage) {
      targetPage = newPage;
      await targetPage.waitForLoadState("domcontentloaded");
    } else {
      await page.waitForLoadState("domcontentloaded");
    }

    await waitForUi(targetPage);
    await expect(targetPage.getByRole("heading", { name: headingText }).first()).toBeVisible();

    const legalContent = targetPage
      .locator("main,article,section,body")
      .locator("p,li,div")
      .filter({ hasText: /\S+/ })
      .first();
    await expect(legalContent).toBeVisible();

    const filePath = path.join(
      evidenceDir,
      `${linkText.toLowerCase().replace(/[^a-z0-9]+/g, "_")}.png`,
    );
    await targetPage.screenshot({ path: filePath, fullPage: true });

    const finalUrl = targetPage.url();
    if (newPage) {
      await newPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    return finalUrl;
  };

  await executeStep("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await validateLegalPage(
      "Términos y Condiciones",
      /Términos y Condiciones/i,
    );
  });

  await executeStep("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await validateLegalPage(
      "Política de Privacidad",
      /Política de Privacidad/i,
    );
  });

  const markdownRows = REPORT_FIELDS.map((field) => `| ${field} | ${report[field]} |`);
  const reportMarkdown = [
    "# SaleADS Mi Negocio Workflow Report",
    "",
    "| Validation Step | Status |",
    "| --- | --- |",
    ...markdownRows,
    "",
    "## Final URLs",
    "",
    `- Términos y Condiciones: ${legalUrls["Términos y Condiciones"] ?? "N/A"}`,
    `- Política de Privacidad: ${legalUrls["Política de Privacidad"] ?? "N/A"}`,
    "",
    "## Errors",
    ...(errors.length > 0 ? errors.map((error) => `- ${error}`) : ["- None"]),
  ].join("\n");

  const reportPath = path.join(evidenceDir, "final-report.md");
  fs.writeFileSync(reportPath, reportMarkdown, "utf8");
  console.log(reportMarkdown);

  if (errors.length > 0) {
    throw new Error(`One or more validations failed.\n${errors.join("\n")}`);
  }
});
