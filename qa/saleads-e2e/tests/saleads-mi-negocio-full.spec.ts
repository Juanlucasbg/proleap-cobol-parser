import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type ResultStatus = "PASS" | "FAIL" | "SKIPPED";

type StepResult = {
  status: ResultStatus;
  details: string;
  evidence: string[];
  finalUrl?: string;
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

const CHECKPOINT_DIR = path.resolve(process.cwd(), "test-results", "checkpoints");

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForTimeout(700);
}

async function firstVisible(candidates: Locator[], timeoutMs = 3500): Promise<Locator | null> {
  for (const candidate of candidates) {
    const current = candidate.first();
    try {
      await current.waitFor({ state: "visible", timeout: timeoutMs });
      return current;
    } catch {
      // Try the next candidate.
    }
  }
  return null;
}

function textCandidates(page: Page, label: string): Locator[] {
  const exact = new RegExp(`^${escapeRegex(label)}$`, "i");
  const contains = new RegExp(escapeRegex(label), "i");

  return [
    page.getByRole("button", { name: exact }),
    page.getByRole("link", { name: exact }),
    page.getByRole("menuitem", { name: exact }),
    page.getByRole("tab", { name: exact }),
    page.getByRole("button", { name: contains }),
    page.getByRole("link", { name: contains }),
    page.getByRole("menuitem", { name: contains }),
    page.getByText(exact),
    page.getByText(contains)
  ];
}

async function clickByVisibleText(page: Page, labels: string[], timeoutMs = 5000): Promise<void> {
  for (const label of labels) {
    const locator = await firstVisible(textCandidates(page, label), timeoutMs);
    if (!locator) {
      continue;
    }

    await locator.click();
    await waitForUi(page);
    return;
  }

  throw new Error(`Could not find visible clickable element for labels: ${labels.join(", ")}`);
}

async function isVisibleByText(page: Page, labels: string[], timeoutMs = 3000): Promise<boolean> {
  for (const label of labels) {
    const locator = await firstVisible(textCandidates(page, label), timeoutMs);
    if (locator) {
      return true;
    }
  }
  return false;
}

async function findSectionHeading(page: Page, heading: RegExp): Promise<Locator | null> {
  const headingLocator = await firstVisible(
    [
      page.getByRole("heading", { name: heading }),
      page.getByText(heading),
      page.locator("section, div").filter({ hasText: heading })
    ],
    7000
  );

  return headingLocator;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const baseUrl = process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;
  if (!baseUrl) {
    throw new Error("Missing SALEADS_BASE_URL (or BASE_URL). The test is environment-agnostic and requires runtime URL injection.");
  }

  const results = REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = { status: "SKIPPED", details: "Step was not executed.", evidence: [] };
      return acc;
    },
    {} as Record<(typeof REPORT_FIELDS)[number], StepResult>
  );

  let screenshotIndex = 1;
  await fs.mkdir(CHECKPOINT_DIR, { recursive: true });

  const checkpoint = async (name: string, fullPage = false): Promise<string> => {
    const filename = `${String(screenshotIndex).padStart(2, "0")}-${name}.png`;
    const outputPath = path.join(CHECKPOINT_DIR, filename);
    screenshotIndex += 1;

    await page.screenshot({ path: outputPath, fullPage });
    await testInfo.attach(filename, { path: outputPath, contentType: "image/png" });
    return outputPath;
  };

  const pass = (field: (typeof REPORT_FIELDS)[number], details: string, evidence: string[] = [], finalUrl?: string): void => {
    results[field] = { status: "PASS", details, evidence, finalUrl };
  };

  const fail = (field: (typeof REPORT_FIELDS)[number], error: unknown, evidence: string[] = []): void => {
    const details = error instanceof Error ? error.message : String(error);
    results[field] = { status: "FAIL", details, evidence };
  };

  await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  // 1) Login with Google.
  try {
    await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google",
      "Login"
    ]);

    const accountPicker = await firstVisible(
      [
        page.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
        page.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
        page.getByRole("link", { name: /juanlucasbarbiergarzon@gmail\.com/i })
      ],
      9000
    );

    if (accountPicker) {
      await accountPicker.click();
      await waitForUi(page);
    }

    const sidebarVisible = await firstVisible(
      [page.locator("aside"), page.getByRole("navigation"), page.locator("[class*='sidebar']")],
      15000
    );
    if (!sidebarVisible) {
      throw new Error("Main interface loaded but sidebar navigation was not visible.");
    }

    const dashboardMarker = await isVisibleByText(page, ["Negocio", "Mi Negocio", "Dashboard", "Inicio"], 12000);
    if (!dashboardMarker) {
      throw new Error("Dashboard marker text was not found after login.");
    }

    const dashboardShot = await checkpoint("dashboard-loaded");
    pass("Login", "Main application interface and sidebar are visible after Google login.", [dashboardShot]);
  } catch (error) {
    fail("Login", error);
  }

  // 2) Open Mi Negocio menu.
  try {
    await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
    if (!(await isVisibleByText(page, ["Agregar Negocio"], 5000))) {
      await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
    }

    const hasAddBusiness = await isVisibleByText(page, ["Agregar Negocio"], 7000);
    const hasManageBusinesses = await isVisibleByText(page, ["Administrar Negocios"], 7000);
    if (!hasAddBusiness || !hasManageBusinesses) {
      throw new Error("Mi Negocio submenu did not expose both Agregar Negocio and Administrar Negocios.");
    }

    const menuShot = await checkpoint("mi-negocio-expanded");
    pass("Mi Negocio menu", "Mi Negocio submenu expanded and both expected options were visible.", [menuShot]);
  } catch (error) {
    fail("Mi Negocio menu", error);
  }

  // 3) Validate Agregar Negocio modal.
  try {
    await clickByVisibleText(page, ["Agregar Negocio"]);

    const modal = await firstVisible(
      [
        page.getByRole("dialog", { name: /crear nuevo negocio/i }),
        page.locator("[role='dialog']").filter({ hasText: /crear nuevo negocio/i }),
        page.locator(".modal, [class*='modal']").filter({ hasText: /crear nuevo negocio/i })
      ],
      10000
    );
    if (!modal) {
      throw new Error("Agregar Negocio click did not open the expected modal.");
    }

    const modalChecks = await Promise.all([
      firstVisible([modal.getByText(/crear nuevo negocio/i)], 3000),
      firstVisible([modal.getByText(/nombre del negocio/i)], 3000),
      firstVisible([modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)], 3000),
      firstVisible([modal.getByRole("button", { name: /cancelar/i })], 3000),
      firstVisible([modal.getByRole("button", { name: /crear negocio/i })], 3000)
    ]);

    if (modalChecks.some((entry) => !entry)) {
      throw new Error("The Agregar Negocio modal did not contain all expected fields/text/buttons.");
    }

    const modalShot = await checkpoint("agregar-negocio-modal");

    const businessNameField = await firstVisible(
      [modal.getByRole("textbox", { name: /nombre del negocio/i }), modal.getByPlaceholder(/nombre del negocio/i)],
      2000
    );
    if (businessNameField) {
      await businessNameField.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }

    await clickByVisibleText(page, ["Cancelar"]);
    pass("Agregar Negocio modal", "Crear Nuevo Negocio modal validated and closed with Cancelar.", [modalShot]);
  } catch (error) {
    fail("Agregar Negocio modal", error);
  }

  // 4) Open Administrar Negocios.
  try {
    if (!(await isVisibleByText(page, ["Administrar Negocios"], 3000))) {
      await clickByVisibleText(page, ["Mi Negocio", "Negocio"]);
    }

    await clickByVisibleText(page, ["Administrar Negocios"]);
    await waitForUi(page);

    const sectionChecks = await Promise.all([
      findSectionHeading(page, /informaci[oó]n general/i),
      findSectionHeading(page, /detalles de la cuenta/i),
      findSectionHeading(page, /tus negocios/i),
      findSectionHeading(page, /secci[oó]n legal/i)
    ]);

    if (sectionChecks.some((section) => !section)) {
      throw new Error("Administrar Negocios view is missing one or more expected sections.");
    }

    const adminViewShot = await checkpoint("administrar-negocios-view", true);
    pass("Administrar Negocios view", "All account sections are visible in Administrar Negocios.", [adminViewShot]);
  } catch (error) {
    fail("Administrar Negocios view", error);
  }

  // 5) Validate Información General.
  try {
    const infoSection = await firstVisible(
      [page.locator("section, div").filter({ hasText: /informaci[oó]n general/i })],
      6000
    );
    if (!infoSection) {
      throw new Error("Información General section is not visible.");
    }

    const hasEmail = await firstVisible([infoSection.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)], 4000);
    const hasBusinessPlan = await firstVisible([infoSection.getByText(/business plan/i)], 4000);
    const hasChangePlan = await firstVisible([infoSection.getByRole("button", { name: /cambiar plan/i })], 4000);
    const hasUserNameMarker = await firstVisible(
      [infoSection.getByText(/nombre|usuario|perfil/i), page.getByText(/juanlucasbarbiergarzon/i)],
      4000
    );

    if (!hasEmail || !hasBusinessPlan || !hasChangePlan || !hasUserNameMarker) {
      throw new Error("Información General is missing user name/email, BUSINESS PLAN text, or Cambiar Plan button.");
    }

    pass("Información General", "Información General displays user data and plan controls.");
  } catch (error) {
    fail("Información General", error);
  }

  // 6) Validate Detalles de la Cuenta.
  try {
    const detailsSection = await firstVisible(
      [page.locator("section, div").filter({ hasText: /detalles de la cuenta/i })],
      6000
    );
    if (!detailsSection) {
      throw new Error("Detalles de la Cuenta section is not visible.");
    }

    const hasCreatedDate = await firstVisible([detailsSection.getByText(/cuenta creada/i)], 4000);
    const hasActiveStatus = await firstVisible([detailsSection.getByText(/estado activo|activo/i)], 4000);
    const hasLanguage = await firstVisible([detailsSection.getByText(/idioma seleccionado|idioma/i)], 4000);
    if (!hasCreatedDate || !hasActiveStatus || !hasLanguage) {
      throw new Error("Detalles de la Cuenta is missing one or more required account details.");
    }

    pass("Detalles de la Cuenta", "Detalles de la Cuenta includes creation date, active status, and selected language.");
  } catch (error) {
    fail("Detalles de la Cuenta", error);
  }

  // 7) Validate Tus Negocios.
  try {
    const businessesSection = await firstVisible(
      [page.locator("section, div").filter({ hasText: /tus negocios/i })],
      6000
    );
    if (!businessesSection) {
      throw new Error("Tus Negocios section is not visible.");
    }

    const hasBusinessList = await firstVisible([businessesSection.locator("li, tr, [class*='business']").first()], 5000);
    const hasAddButton = await firstVisible([businessesSection.getByRole("button", { name: /agregar negocio/i })], 3000);
    const hasQuotaText = await firstVisible([businessesSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)], 3000);

    if (!hasBusinessList || !hasAddButton || !hasQuotaText) {
      throw new Error("Tus Negocios is missing business list, add button, or business quota text.");
    }

    pass("Tus Negocios", "Tus Negocios section shows list, add button, and quota text.");
  } catch (error) {
    fail("Tus Negocios", error);
  }

  const validateLegalLink = async (
    field: "Términos y Condiciones" | "Política de Privacidad",
    linkLabel: string,
    headingPattern: RegExp,
    shotName: string
  ): Promise<void> => {
    const appUrlBeforeClick = page.url();
    const link = await firstVisible(textCandidates(page, linkLabel), 7000);
    if (!link) {
      throw new Error(`Could not find legal link: ${linkLabel}`);
    }

    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await link.click();
    await waitForUi(page);
    const popup = await popupPromise;

    const legalPage = popup ?? page;
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => undefined);
    await legalPage.waitForTimeout(800);

    const headingVisible = await firstVisible(
      [legalPage.getByRole("heading", { name: headingPattern }), legalPage.getByText(headingPattern)],
      10000
    );
    if (!headingVisible) {
      throw new Error(`Heading "${headingPattern}" is not visible on legal page.`);
    }

    const legalText = await legalPage.locator("body").innerText();
    if (legalText.trim().length < 120) {
      throw new Error("Legal content appears too short or empty.");
    }

    const legalUrl = legalPage.url();
    const shotPath = path.join(CHECKPOINT_DIR, `${String(screenshotIndex).padStart(2, "0")}-${shotName}.png`);
    screenshotIndex += 1;
    await legalPage.screenshot({ path: shotPath, fullPage: true });
    await testInfo.attach(path.basename(shotPath), { path: shotPath, contentType: "image/png" });

    pass(field, `${linkLabel} page validated with heading and legal content.`, [shotPath], legalUrl);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (page.url() !== appUrlBeforeClick) {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  };

  // 8) Validate Términos y Condiciones.
  try {
    await validateLegalLink(
      "Términos y Condiciones",
      "Términos y Condiciones",
      /t[eé]rminos y condiciones/i,
      "terminos-y-condiciones"
    );
  } catch (error) {
    fail("Términos y Condiciones", error);
  }

  // 9) Validate Política de Privacidad.
  try {
    await validateLegalLink(
      "Política de Privacidad",
      "Política de Privacidad",
      /pol[ií]tica de privacidad/i,
      "politica-de-privacidad"
    );
  } catch (error) {
    fail("Política de Privacidad", error);
  }

  // 10) Final report.
  const finalReport = REPORT_FIELDS.map((field) => ({
    field,
    status: results[field].status,
    details: results[field].details,
    evidence: results[field].evidence,
    finalUrl: results[field].finalUrl ?? null
  }));

  const reportDir = path.resolve(process.cwd(), "test-results");
  await fs.mkdir(reportDir, { recursive: true });
  const reportPath = path.join(reportDir, "saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  await testInfo.attach("final-report.json", { path: reportPath, contentType: "application/json" });

  // eslint-disable-next-line no-console
  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT", JSON.stringify(finalReport, null, 2));

  const failedFields = finalReport.filter((entry) => entry.status !== "PASS").map((entry) => entry.field);
  expect(failedFields, `Validation failures in: ${failedFields.join(", ")}`).toEqual([]);
});
