import { expect, type Locator, type Page, type TestInfo, test } from "@playwright/test";
import { writeFile } from "node:fs/promises";

const DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

type ReportField = (typeof REPORT_FIELDS)[number];
type Status = "PASS" | "FAIL";

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function firstVisible(locator: Locator, timeout = 1500): Promise<boolean> {
  try {
    return await locator.isVisible({ timeout });
  } catch {
    return false;
  }
}

async function clickByVisibleName(page: Page, labels: string[]): Promise<void> {
  for (const label of labels) {
    const escaped = escapeRegex(label);
    const roleRegex = new RegExp(escaped, "i");
    const exactTextRegex = new RegExp(`^\\s*${escaped}\\s*$`, "i");
    const candidates = [
      page.getByRole("button", { name: roleRegex }).first(),
      page.getByRole("link", { name: roleRegex }).first(),
      page.getByRole("menuitem", { name: roleRegex }).first(),
      page.getByRole("tab", { name: roleRegex }).first(),
      page.getByText(exactTextRegex).first(),
      page.getByText(label, { exact: false }).first(),
    ];

    for (const candidate of candidates) {
      if (await firstVisible(candidate)) {
        await candidate.click();
        await waitForUi(page);
        return;
      }
    }
  }

  throw new Error(`No clickable visible element for labels: ${labels.join(", ")}`);
}

async function assertVisibleTexts(page: Page, texts: string[]): Promise<void> {
  for (const text of texts) {
    const escaped = escapeRegex(text);
    await expect(page.getByText(new RegExp(escaped, "i")).first()).toBeVisible();
  }
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, fileName: string): Promise<void> {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage: true });
  await testInfo.attach(fileName, { path, contentType: "image/png" });
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const addBusiness = page.getByText(/Agregar Negocio/i).first();
  const manageBusiness = page.getByText(/Administrar Negocios/i).first();

  if ((await firstVisible(addBusiness)) && (await firstVisible(manageBusiness))) {
    return;
  }

  if (await firstVisible(page.getByText(/Negocio/i).first())) {
    await clickByVisibleName(page, ["Negocio"]);
  }

  await clickByVisibleName(page, ["Mi Negocio", "Negocio"]);

  if (!(await firstVisible(addBusiness, 5000))) {
    await clickByVisibleName(page, ["Mi Negocio"]);
  }

  await expect(addBusiness).toBeVisible();
  await expect(manageBusiness).toBeVisible();
}

async function getSectionTextByHeading(page: Page, heading: string): Promise<string> {
  const headingLocator = page.getByText(new RegExp(`^\\s*${escapeRegex(heading)}\\s*$`, "i")).first();
  await expect(headingLocator).toBeVisible();
  const container = headingLocator.locator(
    "xpath=ancestor::*[self::section or self::article or self::div][1]",
  );
  return (await container.innerText()).replace(/\s+/g, " ").trim();
}

async function validateLegalLink(
  page: Page,
  testInfo: TestInfo,
  linkName: string,
  headingRegex: RegExp,
  screenshotFile: string,
): Promise<string> {
  const appUrlBefore = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickByVisibleName(page, [linkName]);

  const popup = await popupPromise;
  const targetPage = popup ?? page;
  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForTimeout(700);

  await expect(targetPage.getByText(headingRegex).first()).toBeVisible();

  const bodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (bodyText.length < 200) {
    throw new Error(`${linkName} page seems empty or missing legal content.`);
  }

  await captureCheckpoint(targetPage, testInfo, screenshotFile);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== appUrlBefore) {
    const backResult = await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    if (!backResult || page.url() !== appUrlBefore) {
      await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
    }
  }

  await waitForUi(page);
  return finalUrl;
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("logs in with Google and validates Mi Negocio flow end-to-end", async ({ page }, testInfo) => {
    const statuses = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])) as Record<
      ReportField,
      Status
    >;
    const errors: string[] = [];
    const urls: Record<"terminos" | "politica", string | null> = { terminos: null, politica: null };

    const runStep = async (field: ReportField, action: () => Promise<void>) => {
      try {
        await action();
        statuses[field] = "PASS";
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        errors.push(`${field}: ${message}`);
        statuses[field] = "FAIL";
      }
    };

    await runStep("Login", async () => {
      const targetUrl = process.env.SALEADS_URL || process.env.SALEADS_BASE_URL;
      if (targetUrl) {
        await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
      } else if (page.url() === "about:blank") {
        throw new Error("Set SALEADS_URL (or SALEADS_BASE_URL) to the current environment login page.");
      }

      await waitForUi(page);

      const sidebarVisible = await firstVisible(page.getByText(/Negocio/i).first(), 6000);
      if (!sidebarVisible) {
        const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
        await clickByVisibleName(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Google",
        ]);

        const maybeGooglePopup = await popupPromise;
        const googlePage = maybeGooglePopup ?? page;
        await googlePage.waitForLoadState("domcontentloaded");
        await googlePage.waitForTimeout(700);

        const accountEmail = process.env.GOOGLE_ACCOUNT_EMAIL || DEFAULT_GOOGLE_EMAIL;
        const accountLocator = googlePage
          .getByText(new RegExp(`^\\s*${escapeRegex(accountEmail)}\\s*$`, "i"))
          .first();
        if (await firstVisible(accountLocator, 10000)) {
          await accountLocator.click();
          await waitForUi(googlePage);
        }

        await page.bringToFront();
      }

      await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 45000 });
      await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png");
    });

    await runStep("Mi Negocio menu", async () => {
      await ensureMiNegocioExpanded(page);
      await assertVisibleTexts(page, ["Agregar Negocio", "Administrar Negocios"]);
      await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
    });

    await runStep("Agregar Negocio modal", async () => {
      await clickByVisibleName(page, ["Agregar Negocio"]);
      await assertVisibleTexts(page, ["Crear Nuevo Negocio", "Nombre del Negocio", "Tienes 2 de 3 negocios"]);
      await assertVisibleTexts(page, ["Cancelar", "Crear Negocio"]);

      let businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
      if (!(await firstVisible(businessNameInput))) {
        businessNameInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
      }
      await expect(businessNameInput).toBeVisible();

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatizacion");
      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");
      await clickByVisibleName(page, ["Cancelar"]);
    });

    await runStep("Administrar Negocios view", async () => {
      await ensureMiNegocioExpanded(page);
      await clickByVisibleName(page, ["Administrar Negocios"]);
      await assertVisibleTexts(page, ["Información General", "Detalles de la Cuenta", "Tus Negocios", "Sección Legal"]);
      await captureCheckpoint(page, testInfo, "04-administrar-negocios.png");
    });

    await runStep("Información General", async () => {
      const sectionText = await getSectionTextByHeading(page, "Información General");
      if (!/@/.test(sectionText)) {
        throw new Error("User email was not found in Información General.");
      }
      const normalized = sectionText.toLowerCase();
      if (!normalized.includes("business plan")) {
        throw new Error("BUSINESS PLAN text was not found.");
      }
      if (!normalized.includes("cambiar plan")) {
        throw new Error("Cambiar Plan button/label was not found.");
      }

      const nameLikeText = sectionText
        .replace(/información general/gi, "")
        .replace(/business plan/gi, "")
        .replace(/cambiar plan/gi, "")
        .replace(/\S+@\S+\.\S+/g, "")
        .trim();
      if (nameLikeText.length < 3) {
        throw new Error("User name was not detected in Información General.");
      }
    });

    await runStep("Detalles de la Cuenta", async () => {
      await assertVisibleTexts(page, ["Cuenta creada", "Estado activo", "Idioma seleccionado"]);
    });

    await runStep("Tus Negocios", async () => {
      await assertVisibleTexts(page, ["Tus Negocios", "Agregar Negocio", "Tienes 2 de 3 negocios"]);
      const sectionText = await getSectionTextByHeading(page, "Tus Negocios");
      if (sectionText.length < 40) {
        throw new Error("Business list content in Tus Negocios appears empty.");
      }
    });

    await runStep("Términos y Condiciones", async () => {
      urls.terminos = await validateLegalLink(
        page,
        testInfo,
        "Términos y Condiciones",
        /T[eé]rminos y Condiciones/i,
        "05-terminos-y-condiciones.png",
      );
    });

    await runStep("Política de Privacidad", async () => {
      urls.politica = await validateLegalLink(
        page,
        testInfo,
        "Política de Privacidad",
        /Pol[ií]tica de Privacidad/i,
        "06-politica-de-privacidad.png",
      );
    });

    const finalReport = {
      statusByStep: statuses,
      urls,
      failures: errors,
    };

    const reportPath = testInfo.outputPath("final-report.json");
    await writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

    const failedSteps = Object.entries(statuses)
      .filter(([, status]) => status === "FAIL")
      .map(([field]) => field);

    expect(
      failedSteps,
      `One or more validations failed.\n${errors.map((value) => `- ${value}`).join("\n")}`,
    ).toEqual([]);
  });
});
