import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

interface StepResult {
  id: number;
  name: string;
  status: StepStatus;
  details: string;
}

const STEP_FIELDS = [
  { id: 1, name: "Login" },
  { id: 2, name: "Mi Negocio menu" },
  { id: 3, name: "Agregar Negocio modal" },
  { id: 4, name: "Administrar Negocios view" },
  { id: 5, name: "Información General" },
  { id: 6, name: "Detalles de la Cuenta" },
  { id: 7, name: "Tus Negocios" },
  { id: 8, name: "Términos y Condiciones" },
  { id: 9, name: "Política de Privacidad" },
] as const;

const RESULTS_DIR = path.resolve(__dirname, "..", "test-results");
const ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_LIMIT_TEXT = "Tienes 2 de 3 negocios";
const TEST_BUSINESS_NAME = "Negocio Prueba Automatización";
const LEGAL_HEADING_TERMS = "Términos y Condiciones";
const LEGAL_HEADING_PRIVACY = "Política de Privacidad";

function sanitizeFileName(value: string): string {
  return value
    .normalize("NFKD")
    .replace(/[^\w\s-]/g, "")
    .trim()
    .replace(/\s+/g, "-")
    .toLowerCase();
}

function toErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function upsertStepResult(
  results: StepResult[],
  id: number,
  name: string,
  status: StepStatus,
  details: string
): void {
  const normalized = details.replace(/\s+/g, " ").trim();
  const index = results.findIndex((item) => item.id === id);
  const next = { id, name, status, details: normalized };
  if (index >= 0) {
    results[index] = next;
  } else {
    results.push(next);
  }
}

async function runStep(
  results: StepResult[],
  id: number,
  name: string,
  fn: () => Promise<void>
): Promise<boolean> {
  try {
    await fn();
    upsertStepResult(results, id, name, "PASS", "Validations completed.");
    return true;
  } catch (error) {
    upsertStepResult(results, id, name, "FAIL", toErrorMessage(error));
    return false;
  }
}

function markBlockedStep(
  results: StepResult[],
  id: number,
  name: string,
  dependency: string
): void {
  upsertStepResult(results, id, name, "FAIL", `Blocked because ${dependency} failed.`);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {
    // Some environments may keep long-lived requests open.
  });
  await page.waitForTimeout(450);
}

async function clickVisibleText(page: Page, text: string): Promise<void> {
  const button = page.getByRole("button", { name: text, exact: false }).first();
  if (await button.isVisible().catch(() => false)) {
    await button.click();
    await waitForUi(page);
    return;
  }

  const link = page.getByRole("link", { name: text, exact: false }).first();
  if (await link.isVisible().catch(() => false)) {
    await link.click();
    await waitForUi(page);
    return;
  }

  const textNode = page.getByText(text, { exact: false }).first();
  await expect(textNode).toBeVisible({ timeout: 20_000 });
  await textNode.click();
  await waitForUi(page);
}

async function captureEvidence(
  page: Page,
  filename: string,
  fullPage = false
): Promise<void> {
  fs.mkdirSync(RESULTS_DIR, { recursive: true });
  await page.screenshot({
    path: path.join(RESULTS_DIR, filename),
    fullPage,
  });
}

async function ensureStartingPage(page: Page): Promise<void> {
  if (page.url() === "about:blank") {
    const envUrl = process.env.SALEADS_URL;
    if (!envUrl) {
      throw new Error(
        "No page loaded. Set SALEADS_URL or open the SaleADS login page before running."
      );
    }

    await page.goto(envUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);
}

async function ensureSidebarVisible(page: Page): Promise<void> {
  const nav = page.locator("nav, aside").first();
  if (await nav.isVisible().catch(() => false)) {
    return;
  }

  await expect(page.getByText("Negocio", { exact: false }).first()).toBeVisible({
    timeout: 20_000,
  });
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarVisible = await page
    .getByText("Agregar Negocio", { exact: false })
    .first()
    .isVisible()
    .catch(() => false);
  const administrarVisible = await page
    .getByText("Administrar Negocios", { exact: false })
    .first()
    .isVisible()
    .catch(() => false);

  if (agregarVisible && administrarVisible) {
    return;
  }

  await clickVisibleText(page, "Negocio");
  await clickVisibleText(page, "Mi Negocio");
  await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible({
    timeout: 20_000,
  });
  await expect(page.getByText("Administrar Negocios", { exact: false }).first()).toBeVisible({
    timeout: 20_000,
  });
}

async function selectGoogleAccountIfVisible(targetPage: Page): Promise<void> {
  const accountChoice = targetPage.getByText(ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountChoice.isVisible().catch(() => false)) {
    await accountChoice.click();
    await waitForUi(targetPage);
  }
}

async function clickGoogleLogin(page: Page): Promise<void> {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  const loginTexts = [
    "Sign in with Google",
    "Iniciar sesión con Google",
    "Continuar con Google",
    "Google",
  ];

  let clicked = false;
  for (const label of loginTexts) {
    const candidate = page.getByRole("button", { name: label, exact: false }).first();
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click();
      clicked = true;
      break;
    }
  }

  if (!clicked) {
    await clickVisibleText(page, "Google");
  } else {
    await waitForUi(page);
  }

  const popup = await popupPromise;
  if (popup) {
    await waitForUi(popup);
    await selectGoogleAccountIfVisible(popup);
    await popup.close().catch(() => {
      // Ignore if popup closes automatically after account selection.
    });
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  await selectGoogleAccountIfVisible(page);
}

async function locateBusinessNameInput(page: Page): Promise<Locator> {
  const byLabel = page.getByLabel("Nombre del Negocio", { exact: false }).first();
  if (await byLabel.isVisible().catch(() => false)) {
    return byLabel;
  }

  const byPlaceholder = page
    .getByPlaceholder("Nombre del Negocio", { exact: false })
    .first();
  if (await byPlaceholder.isVisible().catch(() => false)) {
    return byPlaceholder;
  }

  const modal = page.getByRole("dialog").first();
  return modal.locator("input, textarea").first();
}

async function sectionWithHeading(page: Page, heading: string): Promise<Locator> {
  const title = page.getByText(heading, { exact: false }).first();
  await expect(title).toBeVisible({ timeout: 20_000 });

  const section = page
    .locator("section, article, div")
    .filter({ has: title })
    .first();

  if (await section.isVisible().catch(() => false)) {
    return section;
  }

  return page.locator("body");
}

function hasLikelyNameValue(blockText: string): boolean {
  const lines = blockText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .filter(
      (line) =>
        !/información general|business plan|cambiar plan|@/i.test(line) &&
        line.length >= 3
    );

  return lines.some((line) =>
    /^[A-Za-zÁÉÍÓÚÑáéíóúñ' -]+$/.test(line) && /[A-Za-zÁÉÍÓÚÑáéíóúñ]/.test(line)
  );
}

async function handleLegalLinkAndReturn(
  page: Page,
  linkText: string,
  expectedHeading: string,
  screenshotName: string
): Promise<string> {
  const appUrlBefore = page.url();
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickVisibleText(page, linkText);

  const popup = await popupPromise;
  const legalPage = popup ?? page;

  await waitForUi(legalPage);
  await expect(legalPage.getByText(expectedHeading, { exact: false }).first()).toBeVisible({
    timeout: 20_000,
  });
  await expect(legalPage.locator("body")).toContainText(/.{40,}/s, { timeout: 20_000 });
  await captureEvidence(legalPage, screenshotName, true);

  const finalUrl = legalPage.url();

  if (popup) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUi(page);
    return finalUrl;
  }

  if (page.url() !== appUrlBefore) {
    await page.goBack().catch(async () => {
      await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(page);
  }

  return finalUrl;
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login via Google and validate full Mi Negocio workflow", async ({ page }) => {
    const results: StepResult[] = [];
    const legalUrls: Record<string, string> = {};

    const step1Ok = await runStep(results, 1, "Login", async () => {
      await ensureStartingPage(page);
      await clickGoogleLogin(page);
      await ensureSidebarVisible(page);
      await captureEvidence(page, "01-dashboard-loaded.png", true);
    });

    const step2Ok = step1Ok
      ? await runStep(results, 2, "Mi Negocio menu", async () => {
          await ensureSidebarVisible(page);
          await clickVisibleText(page, "Negocio");
          await clickVisibleText(page, "Mi Negocio");
          await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible({
            timeout: 20_000,
          });
          await expect(
            page.getByText("Administrar Negocios", { exact: false }).first()
          ).toBeVisible({
            timeout: 20_000,
          });
          await captureEvidence(page, "02-mi-negocio-expanded-menu.png");
        })
      : (markBlockedStep(results, 2, "Mi Negocio menu", "Login"), false);

    const step3Ok = step2Ok
      ? await runStep(results, 3, "Agregar Negocio modal", async () => {
          await ensureMiNegocioExpanded(page);
          await clickVisibleText(page, "Agregar Negocio");

          await expect(
            page.getByText("Crear Nuevo Negocio", { exact: false }).first()
          ).toBeVisible({
            timeout: 20_000,
          });
          await expect(
            page.getByText("Nombre del Negocio", { exact: false }).first()
          ).toBeVisible({
            timeout: 20_000,
          });
          await expect(page.getByText(BUSINESS_LIMIT_TEXT, { exact: false }).first()).toBeVisible({
            timeout: 20_000,
          });
          await expect(
            page.getByRole("button", { name: "Cancelar", exact: false }).first()
          ).toBeVisible({
            timeout: 20_000,
          });
          await expect(
            page.getByRole("button", { name: "Crear Negocio", exact: false }).first()
          ).toBeVisible({
            timeout: 20_000,
          });

          const nameInput = await locateBusinessNameInput(page);
          await expect(nameInput).toBeVisible({ timeout: 20_000 });
          await captureEvidence(page, "03-agregar-negocio-modal.png");

          await nameInput.click();
          await nameInput.fill(TEST_BUSINESS_NAME);
          await clickVisibleText(page, "Cancelar");
        })
      : (markBlockedStep(results, 3, "Agregar Negocio modal", "Mi Negocio menu"), false);

    const step4Ok = step2Ok
      ? await runStep(results, 4, "Administrar Negocios view", async () => {
          await ensureMiNegocioExpanded(page);
          await clickVisibleText(page, "Administrar Negocios");

          await expect(
            page.getByText("Información General", { exact: false }).first()
          ).toBeVisible({
            timeout: 20_000,
          });
          await expect(
            page.getByText("Detalles de la Cuenta", { exact: false }).first()
          ).toBeVisible({
            timeout: 20_000,
          });
          await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible({
            timeout: 20_000,
          });
          await expect(page.getByText("Sección Legal", { exact: false }).first()).toBeVisible({
            timeout: 20_000,
          });
          await captureEvidence(page, "04-administrar-negocios-page.png", true);
        })
      : (markBlockedStep(results, 4, "Administrar Negocios view", "Mi Negocio menu"), false);

    if (!step4Ok) {
      markBlockedStep(results, 5, "Información General", "Administrar Negocios view");
      markBlockedStep(results, 6, "Detalles de la Cuenta", "Administrar Negocios view");
      markBlockedStep(results, 7, "Tus Negocios", "Administrar Negocios view");
      markBlockedStep(results, 8, "Términos y Condiciones", "Administrar Negocios view");
      markBlockedStep(results, 9, "Política de Privacidad", "Administrar Negocios view");
    } else {
      await runStep(results, 5, "Información General", async () => {
        const infoSection = await sectionWithHeading(page, "Información General");
        const infoText = await infoSection.innerText();

        expect(hasLikelyNameValue(infoText), "User name is not visible.").toBeTruthy();
        await expect(page.getByText(/@/, { exact: false }).first()).toBeVisible({ timeout: 20_000 });
        await expect(page.getByText("BUSINESS PLAN", { exact: false }).first()).toBeVisible({
          timeout: 20_000,
        });
        await expect(
          page.getByRole("button", { name: "Cambiar Plan", exact: false }).first()
        ).toBeVisible({ timeout: 20_000 });
      });

      await runStep(results, 6, "Detalles de la Cuenta", async () => {
        await expect(page.getByText("Cuenta creada", { exact: false }).first()).toBeVisible({
          timeout: 20_000,
        });
        await expect(page.getByText("Estado activo", { exact: false }).first()).toBeVisible({
          timeout: 20_000,
        });
        await expect(
          page.getByText("Idioma seleccionado", { exact: false }).first()
        ).toBeVisible({
          timeout: 20_000,
        });
      });

      await runStep(results, 7, "Tus Negocios", async () => {
        const negociosSection = await sectionWithHeading(page, "Tus Negocios");

        await expect(page.getByText(BUSINESS_LIMIT_TEXT, { exact: false }).first()).toBeVisible({
          timeout: 20_000,
        });
        await expect(
          page.getByRole("button", { name: "Agregar Negocio", exact: false }).first()
        ).toBeVisible({
          timeout: 20_000,
        });

        const listCandidates = negociosSection.locator(
          "li, [role='listitem'], table tbody tr, [role='row']"
        );
        const hasStructuredList = (await listCandidates.count()) > 0;

        if (!hasStructuredList) {
          const sectionText = await negociosSection.innerText();
          const meaningfulLines = sectionText
            .split("\n")
            .map((line) => line.trim())
            .filter(Boolean);
          expect(meaningfulLines.length, "Business list content is not visible.").toBeGreaterThan(3);
        }
      });

      await runStep(results, 8, "Términos y Condiciones", async () => {
        const termsUrl = await handleLegalLinkAndReturn(
          page,
          LEGAL_HEADING_TERMS,
          LEGAL_HEADING_TERMS,
          "08-terminos-y-condiciones.png"
        );
        legalUrls[LEGAL_HEADING_TERMS] = termsUrl;
      });

      await runStep(results, 9, "Política de Privacidad", async () => {
        const privacyUrl = await handleLegalLinkAndReturn(
          page,
          LEGAL_HEADING_PRIVACY,
          LEGAL_HEADING_PRIVACY,
          "09-politica-de-privacidad.png"
        );
        legalUrls[LEGAL_HEADING_PRIVACY] = privacyUrl;
      });
    }

    for (const field of STEP_FIELDS) {
      if (!results.some((item) => item.id === field.id)) {
        upsertStepResult(results, field.id, field.name, "FAIL", "Step did not execute.");
      }
    }

    const orderedResults = STEP_FIELDS.map((field) => {
      const item = results.find((result) => result.id === field.id);
      return item ?? { id: field.id, name: field.name, status: "FAIL", details: "Missing result." };
    });

    const reportLines = [
      "saleads_mi_negocio_full_test - Final Report",
      "===========================================",
      ...orderedResults.map((result) => `${result.name}: ${result.status} - ${result.details}`),
      "",
      `Términos y Condiciones URL: ${legalUrls[LEGAL_HEADING_TERMS] ?? "N/A"}`,
      `Política de Privacidad URL: ${legalUrls[LEGAL_HEADING_PRIVACY] ?? "N/A"}`,
    ];

    fs.mkdirSync(RESULTS_DIR, { recursive: true });
    const reportPath = path.join(
      RESULTS_DIR,
      `final-report-${sanitizeFileName(new Date().toISOString())}.txt`
    );
    fs.writeFileSync(reportPath, `${reportLines.join("\n")}\n`, "utf-8");

    test.info().annotations.push({
      type: "final-report",
      description: reportPath,
    });

    const failed = orderedResults.filter((result) => result.status === "FAIL");
    expect(
      failed,
      `Failed steps: ${failed.map((result) => `${result.id}-${result.name}`).join(", ")}`
    ).toHaveLength(0);
    expect(step3Ok || step2Ok, "Mi Negocio menu flow did not reach modal checkpoint.").toBeTruthy();
  });
});
