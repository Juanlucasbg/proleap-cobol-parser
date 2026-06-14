import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import { writeFile } from "node:fs/promises";

type StepKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepStatus = "PASS" | "FAIL";

interface StepResult {
  status: StepStatus;
  details: string[];
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 10_000 }).catch(() => null);
  await page.waitForLoadState("networkidle", { timeout: 3_000 }).catch(() => null);
  await page.waitForTimeout(800);
}

async function clickAndSettle(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 15_000 });
  await locator.click();
  await waitForUiToSettle(page);
}

async function clickFirstVisible(page: Page, options: Locator[]): Promise<void> {
  for (const option of options) {
    if (await option.first().isVisible({ timeout: 2_000 }).catch(() => false)) {
      await clickAndSettle(option.first(), page);
      return;
    }
  }

  throw new Error("No visible clickable option found.");
}

function createReport(): Record<StepKey, StepResult> {
  return {
    Login: { status: "FAIL", details: ["Step not executed"] },
    "Mi Negocio menu": { status: "FAIL", details: ["Step not executed"] },
    "Agregar Negocio modal": { status: "FAIL", details: ["Step not executed"] },
    "Administrar Negocios view": { status: "FAIL", details: ["Step not executed"] },
    "Información General": { status: "FAIL", details: ["Step not executed"] },
    "Detalles de la Cuenta": { status: "FAIL", details: ["Step not executed"] },
    "Tus Negocios": { status: "FAIL", details: ["Step not executed"] },
    "Términos y Condiciones": { status: "FAIL", details: ["Step not executed"] },
    "Política de Privacidad": { status: "FAIL", details: ["Step not executed"] },
  };
}

function passStep(report: Record<StepKey, StepResult>, key: StepKey, details: string[]): void {
  report[key] = { status: "PASS", details };
}

function failStep(report: Record<StepKey, StepResult>, key: StepKey, error: unknown): void {
  report[key] = {
    status: "FAIL",
    details: [error instanceof Error ? error.message : String(error)],
  };
}

async function screenshot(testInfo: TestInfo, page: Page, name: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(`${name}.png`),
    fullPage,
  });
}

async function openMainPageIfNeeded(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    return;
  }

  const saleadsUrl = process.env.SALEADS_URL;
  if (!saleadsUrl) {
    throw new Error(
      "Page is about:blank and SALEADS_URL is not set. Provide SALEADS_URL, or start this test from an already-open SaleADS login page.",
    );
  }

  await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
}

async function openMiNegocioMenu(page: Page): Promise<void> {
  const navigation = page.getByRole("navigation").first();
  await expect(navigation).toBeVisible({ timeout: 20_000 });

  const negocioSection = page.getByText(/^Negocio$/i).first();
  await expect(negocioSection).toBeVisible({ timeout: 20_000 });

  await clickFirstVisible(page, [
    page.getByRole("button", { name: /mi negocio/i }),
    page.getByText(/^Mi Negocio$/i),
  ]);
}

async function validateLegalLink(
  page: Page,
  testInfo: TestInfo,
  label: "Términos y Condiciones" | "Política de Privacidad",
): Promise<string> {
  const currentPage = page;
  const context = currentPage.context();
  const linkOptions = [
    currentPage.getByRole("link", { name: new RegExp(`^${label}$`, "i") }),
    currentPage.getByRole("button", { name: new RegExp(`^${label}$`, "i") }),
    currentPage.getByText(new RegExp(`^${label}$`, "i")),
  ];

  let selectedLink: Locator | null = null;
  for (const option of linkOptions) {
    if (await option.first().isVisible({ timeout: 3_000 }).catch(() => false)) {
      selectedLink = option.first();
      break;
    }
  }

  if (!selectedLink) {
    throw new Error(`Could not find legal link/button with label "${label}".`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await selectedLink.click();
  await waitForUiToSettle(currentPage);

  const popup = await popupPromise;
  const target = popup ?? currentPage;
  await target.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => null);

  await expect(target.getByRole("heading", { name: new RegExp(label, "i") }).first()).toBeVisible({
    timeout: 20_000,
  });
  await expect(target.locator("body")).toContainText(new RegExp(label.split(" ")[0], "i"), {
    timeout: 20_000,
  });

  await target.screenshot({
    path: testInfo.outputPath(`checkpoint-${label.toLowerCase().replace(/\s+/g, "-")}.png`),
    fullPage: true,
  });

  const finalUrl = target.url();

  if (popup) {
    await popup.close();
    await currentPage.bringToFront();
    await waitForUiToSettle(currentPage);
  } else {
    await currentPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUiToSettle(currentPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};

  try {
    await openMainPageIfNeeded(page);

    await clickFirstVisible(page, [
      page.getByRole("button", { name: /(sign in with google|continuar con google|iniciar sesi[oó]n con google)/i }),
      page.getByText(/(sign in with google|continuar con google|iniciar sesi[oó]n con google)/i),
    ]);

    const googleAccount = page.getByText("juanlucasbarbiergarzon@gmail.com").first();
    if (await googleAccount.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await clickAndSettle(googleAccount, page);
    }

    await expect(page.getByRole("navigation").first()).toBeVisible({ timeout: 45_000 });
    await screenshot(testInfo, page, "checkpoint-dashboard-loaded");
    passStep(report, "Login", ["Main interface visible", "Left sidebar navigation visible", "Dashboard screenshot captured"]);
  } catch (error) {
    failStep(report, "Login", error);
  }

  try {
    await openMiNegocioMenu(page);
    await expect(page.getByText("Agregar Negocio")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Administrar Negocios")).toBeVisible({ timeout: 20_000 });
    await screenshot(testInfo, page, "checkpoint-mi-negocio-expanded");
    passStep(report, "Mi Negocio menu", [
      "Submenu expanded",
      "'Agregar Negocio' visible",
      "'Administrar Negocios' visible",
      "Expanded menu screenshot captured",
    ]);
  } catch (error) {
    failStep(report, "Mi Negocio menu", error);
  }

  try {
    const addBusinessOption = page.getByText(/^Agregar Negocio$/i).first();
    await clickAndSettle(addBusinessOption, page);

    const modal = page.getByRole("dialog").first();
    await expect(modal).toBeVisible({ timeout: 20_000 });
    await expect(modal.getByText("Crear Nuevo Negocio")).toBeVisible();
    await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText("Tienes 2 de 3 negocios")).toBeVisible();
    await expect(modal.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();
    await screenshot(testInfo, page, "checkpoint-agregar-negocio-modal");

    await modal.getByLabel(/Nombre del Negocio/i).click();
    await modal.getByLabel(/Nombre del Negocio/i).fill("Negocio Prueba Automatización");
    await clickAndSettle(modal.getByRole("button", { name: /^Cancelar$/i }), page);

    passStep(report, "Agregar Negocio modal", [
      "Modal validated",
      "Optional field entry validated",
      "Modal closed with 'Cancelar'",
      "Modal screenshot captured",
    ]);
  } catch (error) {
    failStep(report, "Agregar Negocio modal", error);
  }

  try {
    await openMiNegocioMenu(page);
    await clickAndSettle(page.getByText(/^Administrar Negocios$/i).first(), page);

    await expect(page.getByText("Información General")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Detalles de la Cuenta")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Tus Negocios")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Sección Legal")).toBeVisible({ timeout: 20_000 });

    await screenshot(testInfo, page, "checkpoint-administrar-negocios-view", true);
    passStep(report, "Administrar Negocios view", [
      "All account sections visible",
      "Full page screenshot captured",
    ]);
  } catch (error) {
    failStep(report, "Administrar Negocios view", error);
  }

  try {
    const infoSection = page.locator("section").filter({ hasText: "Información General" }).first();
    await expect(infoSection).toBeVisible({ timeout: 20_000 });
    await expect(infoSection.getByText(/@/)).toBeVisible({ timeout: 20_000 });
    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 20_000 });
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 20_000 });

    // Name can vary per account; validating presence of a non-empty profile line.
    await expect(infoSection.locator("p,span,div").filter({ hasText: /[A-Za-zÀ-ÿ]{2,}/ }).first()).toBeVisible({
      timeout: 20_000,
    });

    passStep(report, "Información General", [
      "User name block visible",
      "User email visible",
      "'BUSINESS PLAN' visible",
      "'Cambiar Plan' button visible",
    ]);
  } catch (error) {
    failStep(report, "Información General", error);
  }

  try {
    const detailsSection = page.locator("section").filter({ hasText: "Detalles de la Cuenta" }).first();
    await expect(detailsSection).toBeVisible({ timeout: 20_000 });
    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 20_000 });
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible({ timeout: 20_000 });
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 20_000 });

    passStep(report, "Detalles de la Cuenta", [
      "'Cuenta creada' visible",
      "'Estado activo' visible",
      "'Idioma seleccionado' visible",
    ]);
  } catch (error) {
    failStep(report, "Detalles de la Cuenta", error);
  }

  try {
    const businessSection = page.locator("section").filter({ hasText: "Tus Negocios" }).first();
    await expect(businessSection).toBeVisible({ timeout: 20_000 });
    await expect(businessSection.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible({
      timeout: 20_000,
    });
    await expect(businessSection.getByText("Tienes 2 de 3 negocios")).toBeVisible({ timeout: 20_000 });
    await expect(businessSection.locator("li,article,div").filter({ hasText: /Negocio/i }).first()).toBeVisible({
      timeout: 20_000,
    });

    passStep(report, "Tus Negocios", [
      "Business list visible",
      "'Agregar Negocio' button exists",
      "'Tienes 2 de 3 negocios' visible",
    ]);
  } catch (error) {
    failStep(report, "Tus Negocios", error);
  }

  try {
    const termsUrl = await validateLegalLink(page, testInfo, "Términos y Condiciones");
    legalUrls["Términos y Condiciones"] = termsUrl;
    passStep(report, "Términos y Condiciones", [
      "Heading validated",
      "Legal content visible",
      "Screenshot captured",
      `Final URL: ${termsUrl}`,
    ]);
  } catch (error) {
    failStep(report, "Términos y Condiciones", error);
  }

  try {
    const privacyUrl = await validateLegalLink(page, testInfo, "Política de Privacidad");
    legalUrls["Política de Privacidad"] = privacyUrl;
    passStep(report, "Política de Privacidad", [
      "Heading validated",
      "Legal content visible",
      "Screenshot captured",
      `Final URL: ${privacyUrl}`,
    ]);
  } catch (error) {
    failStep(report, "Política de Privacidad", error);
  }

  const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  await writeFile(
    reportPath,
    JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        report,
        legalUrls,
      },
      null,
      2,
    ),
    "utf-8",
  );

  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedSteps = Object.entries(report)
    .filter(([, result]) => result.status === "FAIL")
    .map(([key]) => key);

  expect(
    failedSteps,
    `One or more workflow validations failed.\nReport saved at: ${reportPath}\nFailed: ${failedSteps.join(", ")}`,
  ).toEqual([]);
});
