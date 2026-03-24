import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

type StepResult = "PASS" | "FAIL";

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

type ReportKey = (typeof REPORT_FIELDS)[number];
type ValidationReport = Record<ReportKey, StepResult>;

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toNameRegex(value: string | RegExp): RegExp {
  if (value instanceof RegExp) {
    return value;
  }

  return new RegExp(escapeRegex(value), "i");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 7_500 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function firstVisible(locators: Locator[], timeoutMs = 15_000): Promise<Locator> {
  const startedAt = Date.now();

  while (Date.now() - startedAt <= timeoutMs) {
    for (const locator of locators) {
      const candidate = locator.first();
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error("No visible locator found before timeout.");
}

async function clickByVisibleText(page: Page, text: string | RegExp): Promise<void> {
  const nameRegex = toNameRegex(text);
  const locator = await firstVisible([
    page.getByRole("button", { name: nameRegex }),
    page.getByRole("link", { name: nameRegex }),
    page.getByRole("menuitem", { name: nameRegex }),
    page.getByRole("tab", { name: nameRegex }),
    page.getByText(nameRegex),
  ]);

  await locator.click();
  await waitForUi(page);
}

async function expectAnyVisible(locators: Locator[], message: string): Promise<void> {
  const isVisible = await firstVisible(locators).then(() => true).catch(() => false);
  expect(isVisible, message).toBe(true);
}

async function checkpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false,
): Promise<void> {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, { path: screenshotPath, contentType: "image/png" });
}

function createReport(): ValidationReport {
  return REPORT_FIELDS.reduce((accumulator, field) => {
    accumulator[field] = "FAIL";
    return accumulator;
  }, {} as ValidationReport);
}

async function runStep(
  report: ValidationReport,
  failures: string[],
  field: ReportKey,
  action: () => Promise<void>,
): Promise<void> {
  try {
    await action();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    failures.push(`${field}: ${error instanceof Error ? error.message : String(error)}`);
  }
}

async function clickLegalLinkAndValidate(
  appPage: Page,
  testInfo: TestInfo,
  options: {
    linkText: string | RegExp;
    expectedHeading: string | RegExp;
    screenshotFile: string;
    urlAttachmentName: string;
  },
): Promise<void> {
  const context = appPage.context();
  const appUrlBeforeClick = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7_500 }).catch(() => null);

  await clickByVisibleText(appPage, options.linkText);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;
  await waitForUi(targetPage);

  const headingRegex = toNameRegex(options.expectedHeading);
  await expectAnyVisible(
    [targetPage.getByRole("heading", { name: headingRegex }), targetPage.getByText(headingRegex)],
    `Expected legal heading "${headingRegex}" to be visible.`,
  );

  await expect(
    targetPage
      .locator("main, article, section, div")
      .filter({ hasText: /\S+/ })
      .first(),
  ).toBeVisible();

  const finalUrl = targetPage.url();
  await testInfo.attach(options.urlAttachmentName, {
    body: Buffer.from(finalUrl),
    contentType: "text/plain",
  });

  await checkpoint(targetPage, testInfo, options.screenshotFile, true);

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  if (appPage.url() !== appUrlBeforeClick) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      if (appUrlBeforeClick && appUrlBeforeClick !== "about:blank") {
        await appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
      }
    });
    await waitForUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const failures: string[] = [];

  const startUrl = process.env.SALEADS_URL ?? process.env.SALEADS_START_URL ?? process.env.BASE_URL;
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await runStep(report, failures, "Login", async () => {
    await clickByVisibleText(
      page,
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|acceder con google/i,
    );

    const googlePage = page.context().pages().at(-1);
    if (googlePage && googlePage !== page) {
      await waitForUi(googlePage);
      const account = googlePage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
      if (await account.isVisible().catch(() => false)) {
        await account.click();
        await waitForUi(googlePage);
      }
    } else {
      const account = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
      if (await account.isVisible().catch(() => false)) {
        await account.click();
        await waitForUi(page);
      }
    }

    await expectAnyVisible(
      [page.getByRole("navigation").first(), page.locator("aside").first(), page.locator('[class*="sidebar"]').first()],
      "Expected main interface and left sidebar to be visible after login.",
    );

    await checkpoint(page, testInfo, "01-dashboard-loaded.png");
  });

  await runStep(report, failures, "Mi Negocio menu", async () => {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    if (await negocioSection.isVisible().catch(() => false)) {
      await negocioSection.click();
      await waitForUi(page);
    }

    await clickByVisibleText(page, /Mi Negocio/i);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await checkpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep(report, failures, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /Agregar Negocio/i);
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();

    await expectAnyVisible(
      [
        page.getByLabel(/Nombre del Negocio/i).first(),
        page.getByPlaceholder(/Nombre del Negocio/i).first(),
        page.locator('input[placeholder*="Negocio"]').first(),
      ],
      "Expected 'Nombre del Negocio' input to exist in modal.",
    );
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    await checkpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const businessNameInput = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i).first(),
      page.getByPlaceholder(/Nombre del Negocio/i).first(),
      page.locator('input[placeholder*="Negocio"]').first(),
    ]);
    await businessNameInput.click();
    await waitForUi(page);
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, /Cancelar/i);
  });

  await runStep(report, failures, "Administrar Negocios view", async () => {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, /Mi Negocio/i);
    }

    await clickByVisibleText(page, /Administrar Negocios/i);
    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

    await checkpoint(page, testInfo, "04-administrar-negocios-full-page.png", true);
  });

  await runStep(report, failures, "Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();

    const maybeUserName = page.getByText(/[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+/).first();
    const userNameVisible = await maybeUserName.isVisible().catch(() => false);
    expect(userNameVisible, "Expected user name to be visible in 'Información General'.").toBe(true);
  });

  await runStep(report, failures, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep(report, failures, "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expectAnyVisible(
      [
        page.getByRole("list").first(),
        page.locator("table").first(),
        page.locator('[class*="business"]').first(),
        page.locator('[data-testid*="business"]').first(),
      ],
      "Expected business list to be visible.",
    );
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
  });

  await runStep(report, failures, "Términos y Condiciones", async () => {
    await clickLegalLinkAndValidate(page, testInfo, {
      linkText: /T[ée]rminos y Condiciones/i,
      expectedHeading: /T[ée]rminos y Condiciones/i,
      screenshotFile: "05-terminos-y-condiciones.png",
      urlAttachmentName: "05-terminos-y-condiciones-url.txt",
    });
  });

  await runStep(report, failures, "Política de Privacidad", async () => {
    await clickLegalLinkAndValidate(page, testInfo, {
      linkText: /Pol[íi]tica de Privacidad/i,
      expectedHeading: /Pol[íi]tica de Privacidad/i,
      screenshotFile: "06-politica-de-privacidad.png",
      urlAttachmentName: "06-politica-de-privacidad-url.txt",
    });
  });

  const finalReportText = [
    "Final report (PASS/FAIL):",
    ...REPORT_FIELDS.map((field) => `- ${field}: ${report[field]}`),
  ].join("\n");

  await testInfo.attach("final-report.txt", {
    body: Buffer.from(finalReportText),
    contentType: "text/plain",
  });

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(report, null, 2)),
    contentType: "application/json",
  });

  // Keep this as console output so it appears directly in CI logs.
  // eslint-disable-next-line no-console
  console.log(finalReportText);

  expect(
    failures,
    `One or more SaleADS Mi Negocio validations failed:\n${failures.join("\n")}`,
  ).toEqual([]);
});
