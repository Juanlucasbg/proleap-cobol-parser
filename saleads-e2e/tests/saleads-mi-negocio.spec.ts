import { expect, Locator, Page, test, TestInfo } from "@playwright/test";

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

type StepResult = {
  status: "PASS" | "FAIL";
  details?: string;
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
  "Política de Privacidad"
];

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForTimeout(500);
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForLoadState("networkidle").catch(() => undefined);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator.first()).toBeVisible({ timeout: 20_000 });
  await locator.first().click();
  await waitForUiToSettle(page);
}

async function firstVisible(page: Page, locators: Locator[], timeoutMs = 15_000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await locator.first().isVisible().catch(() => false)) {
        return locator.first();
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error("No visible element found in locator candidates.");
}

async function expectAnyVisible(locators: Locator[]): Promise<void> {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      return;
    }
  }

  throw new Error("Expected at least one matching locator to be visible.");
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

function buildReport(
  results: Record<ReportField, StepResult>,
  urls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>>
): string {
  const lines = ["# SaleADS Mi Negocio Workflow Report", ""];

  for (const field of REPORT_FIELDS) {
    const result = results[field];
    lines.push(`- **${field}**: ${result.status}${result.details ? ` (${result.details})` : ""}`);
  }

  lines.push("");
  lines.push("## URLs capturadas");
  lines.push(`- Términos y Condiciones: ${urls["Términos y Condiciones"] ?? "N/A"}`);
  lines.push(`- Política de Privacidad: ${urls["Política de Privacidad"] ?? "N/A"}`);

  return lines.join("\n");
}

async function validateLegalDocument(
  appPage: Page,
  testInfo: TestInfo,
  linkText: "Términos y Condiciones" | "Política de Privacidad",
  expectedHeading: RegExp,
  screenshotName: string
): Promise<string> {
  const appUrlBeforeClick = appPage.url();
  const newTabPromise = appPage.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  const link = await firstVisible(appPage, [appPage.getByRole("link", { name: linkText }), appPage.getByText(linkText)]);

  await clickAndWait(appPage, link);

  const maybeNewTab = await newTabPromise;
  const targetPage = maybeNewTab ?? appPage;
  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForLoadState("networkidle").catch(() => undefined);

  const headingByRole = targetPage.getByRole("heading", { name: expectedHeading }).first();
  const headingByText = targetPage.getByText(expectedHeading).first();
  await expectAnyVisible([headingByRole, headingByText]);

  const legalContent = targetPage.locator("p, li, article, section, main").filter({ hasText: /\S+/ }).first();
  await expect(legalContent).toBeVisible({ timeout: 20_000 });

  await targetPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true
  });

  const legalUrl = targetPage.url();

  if (maybeNewTab) {
    await maybeNewTab.close();
    await appPage.bringToFront();
  } else if (appPage.url() !== appUrlBeforeClick) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    });
    await waitForUiToSettle(appPage);
  }

  return legalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const results = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" } satisfies StepResult])
  ) as Record<ReportField, StepResult>;
  const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};

  const runStep = async (field: ReportField, fn: () => Promise<void>) => {
    try {
      await fn();
      results[field] = { status: "PASS" };
    } catch (error) {
      results[field] = { status: "FAIL", details: errorMessage(error) };
    }
  };

  await runStep("Login", async () => {
    if (process.env.SALEADS_URL) {
      await page.goto(process.env.SALEADS_URL, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }

    const signInWithGoogleButton = await firstVisible(page, [
      page.getByRole("button", { name: /google|sign in|iniciar sesión/i }),
      page.getByText(/sign in with google|continuar con google|iniciar con google/i)
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, signInWithGoogleButton);

    const popupPage = await popupPromise;
    const googlePage = popupPage ?? page;
    await googlePage.waitForLoadState("domcontentloaded").catch(() => undefined);

    const accountOption = googlePage.getByText("juanlucasbarbiergarzon@gmail.com").first();
    if (await accountOption.isVisible().catch(() => false)) {
      await clickAndWait(googlePage, accountOption);
    }

    const sidebarLocator = await firstVisible(page, [page.getByText(/negocio/i), page.locator("aside"), page.locator("nav")], 60_000);
    await expect(sidebarLocator).toBeVisible({ timeout: 60_000 });

    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true
    });
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisible(page, [page.getByText(/^Negocio$/i), page.getByText(/negocio/i)]);
    await expect(negocioSection).toBeVisible();

    const miNegocioOption = await firstVisible(page, [page.getByRole("button", { name: /mi negocio/i }), page.getByText(/mi negocio/i)]);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText("Agregar Negocio").first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Administrar Negocios").first()).toBeVisible({ timeout: 20_000 });

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"),
      fullPage: true
    });
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickAndWait(page, page.getByText("Agregar Negocio").first());

    await expect(page.getByRole("heading", { name: "Crear Nuevo Negocio" }).first()).toBeVisible({ timeout: 20_000 });
    await expectAnyVisible([page.getByLabel("Nombre del Negocio"), page.getByPlaceholder("Nombre del Negocio"), page.getByText("Nombre del Negocio")]);
    await expect(page.getByText("Tienes 2 de 3 negocios").first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true
    });

    const nombreField = await firstVisible(page, [
      page.getByLabel("Nombre del Negocio"),
      page.getByPlaceholder("Nombre del Negocio"),
      page.locator("input[name*='nombre']")
    ]);
    await nombreField.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: "Cancelar" }).first());
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText("Administrar Negocios").first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocioOption = await firstVisible(page, [page.getByRole("button", { name: /mi negocio/i }), page.getByText(/mi negocio/i)]);
      await clickAndWait(page, miNegocioOption);
    }

    await clickAndWait(page, page.getByText("Administrar Negocios").first());

    await expect(page.getByText("Información General").first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Detalles de la Cuenta").first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Tus Negocios").first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Sección Legal").first()).toBeVisible({ timeout: 20_000 });

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-view.png"),
      fullPage: true
    });
  });

  await runStep("Información General", async () => {
    await expect(page.getByText("BUSINESS PLAN").first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible({ timeout: 20_000 });
    await expectAnyVisible([page.getByText(/@/), page.getByText(/gmail\.com|outlook\.com|hotmail\.com|yahoo\.com/i)]);
    await expectAnyVisible([page.getByText(/usuario|user|nombre/i), page.locator("[data-testid*='name' i]")]);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText("Cuenta creada").first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Estado activo").first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Idioma seleccionado").first()).toBeVisible({ timeout: 20_000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText("Tus Negocios").first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: "Agregar Negocio" }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Tienes 2 de 3 negocios").first()).toBeVisible({ timeout: 20_000 });
    await expectAnyVisible([
      page.locator("table"),
      page.locator("ul li"),
      page.locator("[data-testid*='business' i]"),
      page.getByText(/negocio/i)
    ]);
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await validateLegalDocument(
      page,
      testInfo,
      "Términos y Condiciones",
      /términos y condiciones/i,
      "05-terminos-y-condiciones.png"
    );

    await expect(page.getByText("Información General").first()).toBeVisible({ timeout: 20_000 });
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await validateLegalDocument(
      page,
      testInfo,
      "Política de Privacidad",
      /política de privacidad/i,
      "06-politica-de-privacidad.png"
    );

    await expect(page.getByText("Información General").first()).toBeVisible({ timeout: 20_000 });
  });

  const finalReport = buildReport(results, legalUrls);
  await testInfo.attach("saleads-mi-negocio-final-report", {
    contentType: "text/markdown",
    body: Buffer.from(finalReport, "utf-8")
  });

  console.log(finalReport);

  const failedFields = REPORT_FIELDS.filter((field) => results[field].status === "FAIL");
  expect(
    failedFields,
    failedFields.length === 0
      ? "All validations passed."
      : `Failed validations:\n${failedFields
          .map((field) => `${field}: ${results[field].details ?? "No details"}`)
          .join("\n")}`
  ).toEqual([]);
});
