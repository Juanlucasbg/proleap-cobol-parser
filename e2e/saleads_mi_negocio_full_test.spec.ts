import { expect, type Locator, type Page, test } from "@playwright/test";
import * as fs from "node:fs/promises";
import * as path from "node:path";

type LocatorRoot = Page | Locator;
type StepStatus = "PASS" | "FAIL";

const reportFields = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
] as const;

type ReportField = (typeof reportFields)[number];

const CLICKABLE_ROLE_NAMES = ["button", "link", "menuitem"] as const;

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function normalizeFileSegment(value: string): string {
  return value.replace(/[^a-z0-9.-]+/gi, "-").replace(/-+/g, "-").toLowerCase();
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

async function safeIsVisible(locator: Locator): Promise<boolean> {
  try {
    return await locator.first().isVisible();
  } catch {
    return false;
  }
}

function getLabelCandidates(root: LocatorRoot, label: string): Locator[] {
  const regex = new RegExp(escapeRegex(label), "i");
  const candidates: Locator[] = [];

  for (const roleName of CLICKABLE_ROLE_NAMES) {
    candidates.push(root.getByRole(roleName, { name: regex }).first());
  }

  candidates.push(root.getByText(regex).first());
  return candidates;
}

async function findVisibleByLabel(
  root: LocatorRoot,
  labels: string[],
  timeoutMs = 15_000,
): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const label of labels) {
      for (const candidate of getLabelCandidates(root, label)) {
        if (await safeIsVisible(candidate)) {
          return candidate.first();
        }
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(`Unable to find a visible element for labels: ${labels.join(", ")}`);
}

async function clickByVisibleLabel(
  root: LocatorRoot,
  page: Page,
  labels: string[],
  timeoutMs = 15_000,
  waitAfterClick = true,
): Promise<void> {
  const target = await findVisibleByLabel(root, labels, timeoutMs);
  await target.click();

  if (waitAfterClick) {
    await waitForUi(page);
  }
}

async function expectVisibleByLabel(root: LocatorRoot, labels: string[], timeoutMs = 15_000): Promise<void> {
  const candidate = await findVisibleByLabel(root, labels, timeoutMs);
  await expect(candidate).toBeVisible();
}

async function sectionByHeading(page: Page, headingRegex: RegExp): Promise<Locator> {
  const section = page
    .locator("section, article, [role='region'], div")
    .filter({ has: page.getByText(headingRegex) })
    .first();

  await expect(section).toBeVisible({ timeout: 20_000 });
  return section;
}

async function captureCheckpoint(page: Page, evidenceDir: string, fileName: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: path.join(evidenceDir, normalizeFileSegment(fileName)),
    fullPage,
  });
}

function isLikelyEmail(text: string): boolean {
  return /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i.test(text);
}

async function hasVisibleSidebar(page: Page): Promise<boolean> {
  const sidebar = page.locator("aside").first();
  const nav = page.locator("nav").first();
  return (await safeIsVisible(sidebar)) || (await safeIsVisible(nav));
}

async function validateLegalPage(
  page: Page,
  evidenceDir: string,
  linkLabels: string[],
  expectedHeading: RegExp,
  screenshotFileName: string,
  legalUrls: Record<string, string>,
  urlKey: string,
): Promise<void> {
  const legalSection = await sectionByHeading(page, /Seccion Legal|Sección Legal/i);
  const linkTarget = await findVisibleByLabel(legalSection, linkLabels, 15_000);
  const appUrlBeforeClick = page.url();

  const popupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
  await linkTarget.click();
  await waitForUi(page);

  const popupPage = await popupPromise;
  const targetPage = popupPage ?? page;
  await waitForUi(targetPage);

  const heading = targetPage.getByRole("heading", { name: expectedHeading }).first();
  if (await safeIsVisible(heading)) {
    await expect(heading).toBeVisible();
  } else {
    await expect(targetPage.getByText(expectedHeading).first()).toBeVisible({ timeout: 15_000 });
  }

  const bodyText = (await targetPage.locator("body").innerText()).trim();
  if (bodyText.length < 120) {
    throw new Error(`Legal content is unexpectedly short for ${urlKey}.`);
  }

  await captureCheckpoint(targetPage, evidenceDir, screenshotFileName, true);
  legalUrls[urlKey] = targetPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  try {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 15_000 });
  } catch {
    await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
  }
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const evidenceDir =
    process.env.SALEADS_EVIDENCE_DIR ?? path.join("artifacts", "saleads-mi-negocio", timestamp);
  const report: Record<ReportField, StepStatus> = Object.fromEntries(
    reportFields.map((field) => [field, "FAIL"]),
  ) as Record<ReportField, StepStatus>;
  const failures: string[] = [];
  const legalUrls: Record<string, string> = {};

  await fs.mkdir(evidenceDir, { recursive: true });

  const runStep = async (field: ReportField, validator: () => Promise<void>): Promise<void> => {
    try {
      await validator();
      report[field] = "PASS";
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      failures.push(`${field}: ${detail}`);
      report[field] = "FAIL";
    }
  };

  await runStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const appAlreadyLoaded = (await hasVisibleSidebar(page)) && (await safeIsVisible(page.getByText(/Negocio/i).first()));
    if (!appAlreadyLoaded) {
      await clickByVisibleLabel(
        page,
        page,
        [
          "Sign in with Google",
          "Iniciar sesion con Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Acceder con Google",
          "Login with Google",
        ],
        20_000,
      );

      const accountSelector = await findVisibleByLabel(
        page,
        ["juanlucasbarbiergarzon@gmail.com"],
        10_000,
      ).catch(() => null);

      if (accountSelector) {
        await accountSelector.click();
        await waitForUi(page);
      }
    }

    if (!(await hasVisibleSidebar(page))) {
      throw new Error("Left sidebar navigation is not visible after login.");
    }

    await expectVisibleByLabel(page, ["Negocio", "Mi Negocio"], 20_000);
    await captureCheckpoint(page, evidenceDir, "01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    await expectVisibleByLabel(page, ["Negocio"], 15_000);

    const addBusinessVisible = await safeIsVisible(page.getByText(/Agregar Negocio/i).first());
    const manageBusinessVisible = await safeIsVisible(page.getByText(/Administrar Negocios/i).first());
    if (!addBusinessVisible || !manageBusinessVisible) {
      await clickByVisibleLabel(page, page, ["Mi Negocio"], 15_000);
    }

    await expectVisibleByLabel(page, ["Agregar Negocio"], 10_000);
    await expectVisibleByLabel(page, ["Administrar Negocios"], 10_000);
    await captureCheckpoint(page, evidenceDir, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const sidebar = page.locator("aside, nav").first();
    await clickByVisibleLabel(sidebar, page, ["Agregar Negocio"], 15_000);

    const modal = page
      .locator("[role='dialog'], .modal")
      .filter({ has: page.getByText(/Crear Nuevo Negocio/i) })
      .first();
    await expect(modal).toBeVisible({ timeout: 15_000 });
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await captureCheckpoint(page, evidenceDir, "03-agregar-negocio-modal.png");

    const nameInput =
      (await safeIsVisible(modal.getByLabel(/Nombre del Negocio/i).first()))
        ? modal.getByLabel(/Nombre del Negocio/i).first()
        : modal.getByPlaceholder(/Nombre del Negocio/i).first();
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatizacion");
    await modal.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUi(page);
    await expect(modal).toBeHidden();
  });

  await runStep("Administrar Negocios view", async () => {
    const manageBusinessVisible = await safeIsVisible(page.getByText(/Administrar Negocios/i).first());
    if (!manageBusinessVisible) {
      await clickByVisibleLabel(page, page, ["Mi Negocio"], 15_000);
    }

    await clickByVisibleLabel(page.locator("aside, nav").first(), page, ["Administrar Negocios"], 15_000);
    await expectVisibleByLabel(page, ["Informacion General", "Información General"], 20_000);
    await expectVisibleByLabel(page, ["Detalles de la Cuenta"], 15_000);
    await expectVisibleByLabel(page, ["Tus Negocios"], 15_000);
    await expectVisibleByLabel(page, ["Seccion Legal", "Sección Legal"], 15_000);
    await captureCheckpoint(page, evidenceDir, "04-administrar-negocios-page.png", true);
  });

  await runStep("Informacion General", async () => {
    const infoSection = await sectionByHeading(page, /Informacion General|Información General/i);
    const infoText = (await infoSection.innerText()).trim();

    if (!isLikelyEmail(infoText)) {
      throw new Error("User email was not detected in Informacion General.");
    }

    const lines = infoText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const nameLine = lines.find(
      (line) =>
        /[A-Za-z]/.test(line) &&
        !isLikelyEmail(line) &&
        !/informacion general|información general|business plan|cambiar plan/i.test(line),
    );
    if (!nameLine) {
      throw new Error("User name was not detected in Informacion General.");
    }

    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expectVisibleByLabel(infoSection, ["Cambiar Plan"], 10_000);
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = await sectionByHeading(page, /Detalles de la Cuenta/i);
    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const businessSection = await sectionByHeading(page, /Tus Negocios/i);
    await expectVisibleByLabel(businessSection, ["Agregar Negocio"], 10_000);
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const explicitRows = businessSection.locator("li, [role='listitem'], tbody tr");
    const rowCount = await explicitRows.count();
    if (rowCount < 1) {
      const sectionText = (await businessSection.innerText()).trim();
      if (!/negocio/i.test(sectionText)) {
        throw new Error("Business list content was not detected in Tus Negocios.");
      }
    }
  });

  await runStep("Terminos y Condiciones", async () => {
    await validateLegalPage(
      page,
      evidenceDir,
      ["Terminos y Condiciones", "Términos y Condiciones"],
      /Terminos y Condiciones|Términos y Condiciones/i,
      "05-terminos-y-condiciones.png",
      legalUrls,
      "Terminos y Condiciones",
    );
  });

  await runStep("Politica de Privacidad", async () => {
    await validateLegalPage(
      page,
      evidenceDir,
      ["Politica de Privacidad", "Política de Privacidad"],
      /Politica de Privacidad|Política de Privacidad/i,
      "06-politica-de-privacidad.png",
      legalUrls,
      "Politica de Privacidad",
    );
  });

  console.log("=== saleads_mi_negocio_full_test report ===");
  for (const field of reportFields) {
    console.log(`${field}: ${report[field]}`);
  }
  console.log("Legal URLs:");
  console.log(JSON.stringify(legalUrls, null, 2));
  console.log(`Evidence directory: ${evidenceDir}`);

  if (failures.length > 0) {
    console.log("Failures:");
    for (const failure of failures) {
      console.log(`- ${failure}`);
    }
  }

  expect(
    failures,
    `One or more validations failed.\n${failures.map((item) => `- ${item}`).join("\n")}`,
  ).toHaveLength(0);
});
