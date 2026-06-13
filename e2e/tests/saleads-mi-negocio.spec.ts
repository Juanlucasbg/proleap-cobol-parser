import { expect, type Locator, type Page, type TestInfo, test } from "@playwright/test";
import fs from "node:fs/promises";

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

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details: string[];
};

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

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";

function createReport(): Record<ReportField, StepResult> {
  return REPORT_FIELDS.reduce(
    (acc, field) => ({
      ...acc,
      [field]: { status: "FAIL", details: [] },
    }),
    {} as Record<ReportField, StepResult>,
  );
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const first = candidate.first();
    const visible = await first.isVisible({ timeout: 2_000 }).catch(() => false);
    if (visible) {
      return first;
    }
  }

  return null;
}

async function clickByAnyText(page: Page, possibleTexts: string[]): Promise<void> {
  for (const text of possibleTexts) {
    const regex = new RegExp(escapeRegExp(text), "i");
    const element = await firstVisible([
      page.getByRole("button", { name: regex }),
      page.getByRole("link", { name: regex }),
      page.getByRole("menuitem", { name: regex }),
      page.getByRole("tab", { name: regex }),
      page.getByText(regex),
    ]);

    if (element) {
      await element.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`No clickable element found for any text in: ${possibleTexts.join(", ")}`);
}

async function expectAnyVisible(page: Page, possibleTexts: string[]): Promise<void> {
  for (const text of possibleTexts) {
    const regex = new RegExp(escapeRegExp(text), "i");
    const visible = await firstVisible([
      page.getByRole("heading", { name: regex }),
      page.getByRole("button", { name: regex }),
      page.getByRole("link", { name: regex }),
      page.getByText(regex),
    ]);

    if (visible) {
      return;
    }
  }

  throw new Error(`No visible element found for any text in: ${possibleTexts.join(", ")}`);
}

async function takeCheckpoint(
  page: Page,
  testName: string,
  testInfo: TestInfo,
  fullPage = false,
): Promise<void> {
  const screenshotPath = testInfo.outputPath(`${testName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(testName, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function openLegalLinkAndValidate(
  page: Page,
  linkText: string,
  headingText: string,
  screenshotName: string,
  testInfo: TestInfo,
): Promise<string> {
  const popupPromise = page
    .context()
    .waitForEvent("page", { timeout: 8_000 })
    .catch(() => null);

  await clickByAnyText(page, [linkText]);

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  await waitForUi(targetPage);

  await expect(targetPage.getByRole("heading", { name: new RegExp(escapeRegExp(headingText), "i") }).first()).toBeVisible();

  const legalBody = targetPage.locator("main, article, body").first();
  await expect(legalBody).toContainText(/[A-Za-zÁÉÍÓÚÑáéíóúñ]{4,}/);

  await takeCheckpoint(targetPage, screenshotName, testInfo, true);

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const saleadsUrl = process.env.SALEADS_URL;
  test.skip(
    !saleadsUrl,
    "Set SALEADS_URL with the SaleADS login URL of the current environment (dev/staging/production).",
  );

  const report = createReport();
  let termsUrl = "";
  let privacyUrl = "";

  const runValidation = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[field].status = "PASS";
    } catch (error) {
      report[field].status = "FAIL";
      report[field].details.push(toErrorMessage(error));
    }
  };

  await page.goto(saleadsUrl as string, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runValidation("Login", async () => {
    const googlePopupPromise = page
      .context()
      .waitForEvent("page", { timeout: 10_000 })
      .catch(() => null);

    await clickByAnyText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Login with Google",
    ]);

    const googlePopup = await googlePopupPromise;

    if (googlePopup) {
      await waitForUi(googlePopup);
      const account = googlePopup.getByText(
        new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i"),
      );
      const canPickAccount = await account.first().isVisible({ timeout: 5_000 }).catch(() => false);

      if (canPickAccount) {
        await account.first().click();
      }

      await googlePopup.waitForEvent("close", { timeout: 40_000 }).catch(() => null);
      await page.bringToFront();
    } else {
      const account = page.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i"));
      const canPickAccount = await account.first().isVisible({ timeout: 5_000 }).catch(() => false);
      if (canPickAccount) {
        await account.first().click();
      }
    }

    await page.waitForLoadState("domcontentloaded");
    await page.waitForLoadState("networkidle").catch(() => null);
    await waitForUi(page);

    const sidebar = await firstVisible([page.locator("aside"), page.locator("nav")]);
    if (!sidebar) {
      throw new Error("Main interface was not detected after login (sidebar/nav missing).");
    }

    await takeCheckpoint(page, "01-dashboard-loaded", testInfo, true);
  });

  await runValidation("Mi Negocio menu", async () => {
    await clickByAnyText(page, ["Negocio"]);
    await clickByAnyText(page, ["Mi Negocio"]);

    await expectAnyVisible(page, ["Agregar Negocio"]);
    await expectAnyVisible(page, ["Administrar Negocios"]);

    await takeCheckpoint(page, "02-mi-negocio-menu-expanded", testInfo, true);
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickByAnyText(page, ["Agregar Negocio"]);

    await expectAnyVisible(page, ["Crear Nuevo Negocio"]);

    const nameField = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[type='text']").filter({ hasText: /Nombre del Negocio/i }),
      page.locator("input").first(),
    ]);

    if (!nameField) {
      throw new Error("Input field 'Nombre del Negocio' not found.");
    }

    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();
    await expectAnyVisible(page, ["Cancelar"]);
    await expectAnyVisible(page, ["Crear Negocio"]);

    await nameField.fill("Negocio Prueba Automatización");
    await takeCheckpoint(page, "03-agregar-negocio-modal", testInfo, true);
    await clickByAnyText(page, ["Cancelar"]);
  });

  await runValidation("Administrar Negocios view", async () => {
    const adminOptionVisible = await page
      .getByText(/Administrar Negocios/i)
      .first()
      .isVisible({ timeout: 2_000 })
      .catch(() => false);

    if (!adminOptionVisible) {
      await clickByAnyText(page, ["Negocio"]);
      await clickByAnyText(page, ["Mi Negocio"]);
    }

    await clickByAnyText(page, ["Administrar Negocios"]);

    await expectAnyVisible(page, ["Información General"]);
    await expectAnyVisible(page, ["Detalles de la Cuenta"]);
    await expectAnyVisible(page, ["Tus Negocios"]);
    await expectAnyVisible(page, ["Sección Legal"]);

    await takeCheckpoint(page, "04-administrar-negocios-account-page", testInfo, true);
  });

  await runValidation("Información General", async () => {
    await expectAnyVisible(page, ["Información General"]);
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expectAnyVisible(page, ["BUSINESS PLAN"]);
    await expectAnyVisible(page, ["Cambiar Plan"]);

    const visibleUserNameCandidate = await firstVisible([
      page.getByText(/Nombre/i),
      page.locator("[data-testid*='name' i]"),
      page.locator("h1, h2, h3").first(),
    ]);

    if (!visibleUserNameCandidate) {
      throw new Error("User name was not detected in 'Información General'.");
    }
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expectAnyVisible(page, ["Detalles de la Cuenta"]);
    await expectAnyVisible(page, ["Cuenta creada"]);
    await expectAnyVisible(page, ["Estado activo"]);
    await expectAnyVisible(page, ["Idioma seleccionado"]);
  });

  await runValidation("Tus Negocios", async () => {
    await expectAnyVisible(page, ["Tus Negocios"]);
    await expectAnyVisible(page, ["Agregar Negocio"]);
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();
  });

  await runValidation("Términos y Condiciones", async () => {
    termsUrl = await openLegalLinkAndValidate(
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "08-terminos-y-condiciones",
      testInfo,
    );
  });

  await runValidation("Política de Privacidad", async () => {
    privacyUrl = await openLegalLinkAndValidate(
      page,
      "Política de Privacidad",
      "Política de Privacidad",
      "09-politica-de-privacidad",
      testInfo,
    );
  });

  const finalReport = {
    generatedAt: new Date().toISOString(),
    target: {
      saleadsUrl,
    },
    reportFields: REPORT_FIELDS.reduce(
      (acc, field) => ({
        ...acc,
        [field]: report[field],
      }),
      {} as Record<ReportField, StepResult>,
    ),
    evidence: {
      "Términos y Condiciones URL": termsUrl,
      "Política de Privacidad URL": privacyUrl,
    },
  };

  const reportPath = testInfo.outputPath("10-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("10-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failedFields,
    `Final Report failed fields: ${failedFields
      .map((field) => `${field}: ${report[field].details.join(" | ")}`)
      .join(" ; ")}`,
  ).toEqual([]);
});
