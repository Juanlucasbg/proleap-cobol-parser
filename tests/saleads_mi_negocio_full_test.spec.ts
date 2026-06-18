import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import { writeFile } from "node:fs/promises";

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

type ValidationStatus = "PASS" | "FAIL";

type ValidationResult = {
  status: ValidationStatus;
  details: string[];
  evidence: string[];
  finalUrl?: string;
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

function initializeReport(): Record<ReportField, ValidationResult> {
  return REPORT_FIELDS.reduce(
    (acc, field) => ({
      ...acc,
      [field]: { status: "FAIL", details: [], evidence: [] },
    }),
    {} as Record<ReportField, ValidationResult>,
  );
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForTimeout(400);
}

async function firstVisible(candidates: Locator[], timeoutMs = 10_000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      if (await candidate.first().isVisible().catch(() => false)) {
        return candidate.first();
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  return candidates[0].first();
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function attachScreenshot(
  page: Page,
  testInfo: TestInfo,
  label: string,
  fullPage = false,
): Promise<string> {
  const fileName = `${label.replace(/\s+/g, "-").toLowerCase()}.png`;
  const outputPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: outputPath, fullPage });
  await testInfo.attach(label, { path: outputPath, contentType: "image/png" });
  return outputPath;
}

function pushFailure(
  report: Record<ReportField, ValidationResult>,
  field: ReportField,
  error: unknown,
): void {
  report[field].status = "FAIL";
  report[field].details.push(error instanceof Error ? error.message : String(error));
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = initializeReport();
  const googleAccountEmail =
    process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";
  const expectedUserName = process.env.SALEADS_EXPECTED_USER_NAME;
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;

  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to run the environment-agnostic SaleADS test.",
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  // Step 1 - Login with Google
  try {
    const loginTrigger = await firstVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesión con google|iniciar sesion con google/i,
        }),
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesión con google|iniciar sesion con google/i),
      ],
      20_000,
    );

    await expect(loginTrigger).toBeVisible();

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, loginTrigger);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
      const popupAccountOption = popup.getByText(googleAccountEmail, { exact: true });
      if (await popupAccountOption.isVisible().catch(() => false)) {
        await popupAccountOption.click();
      }
      await popup.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const inlineAccountOption = page.getByText(googleAccountEmail, { exact: true });
      if (await inlineAccountOption.isVisible().catch(() => false)) {
        await inlineAccountOption.click();
        await waitForUi(page);
      }
    }

    const leftSidebar = await firstVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator("[data-testid*='sidebar'], [class*='sidebar']"),
      ],
      20_000,
    );
    await expect(leftSidebar).toBeVisible();
    await expect(page.getByText(/negocio/i).first()).toBeVisible();

    const dashboardShot = await attachScreenshot(page, testInfo, "01-dashboard-loaded");
    report["Login"].status = "PASS";
    report["Login"].evidence.push(dashboardShot);
    report["Login"].details.push("Main interface and left sidebar navigation are visible.");
  } catch (error) {
    pushFailure(report, "Login", error);
  }

  // Step 2 - Open Mi Negocio menu
  try {
    const negocioSection = await firstVisible(
      [
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^negocio$/i),
      ],
      15_000,
    );
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await firstVisible(
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ],
      15_000,
    );
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

    const expandedMenuShot = await attachScreenshot(page, testInfo, "02-mi-negocio-expanded-menu");
    report["Mi Negocio menu"].status = "PASS";
    report["Mi Negocio menu"].evidence.push(expandedMenuShot);
    report["Mi Negocio menu"].details.push(
      "Submenu expanded and shows Agregar Negocio + Administrar Negocios.",
    );
  } catch (error) {
    pushFailure(report, "Mi Negocio menu", error);
  }

  // Step 3 - Validate Agregar Negocio modal
  try {
    const agregarNegocioOption = await firstVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ],
      10_000,
    );
    await clickAndWait(page, agregarNegocioOption);

    const modal = page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(modal.getByText(/nombre del negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();
    const modalShot = await attachScreenshot(page, testInfo, "03-crear-negocio-modal");

    const input = await firstVisible(
      [
        modal.getByLabel(/nombre del negocio/i),
        modal.getByPlaceholder(/nombre del negocio/i),
        modal.locator("input"),
      ],
      6_000,
    );
    await input.click();
    await input.fill("Negocio Prueba Automatización");
    await clickAndWait(page, modal.getByRole("button", { name: /cancelar/i }));

    report["Agregar Negocio modal"].status = "PASS";
    report["Agregar Negocio modal"].evidence.push(modalShot);
    report["Agregar Negocio modal"].details.push(
      "Crear Nuevo Negocio modal validated, optional input filled, then canceled.",
    );
  } catch (error) {
    pushFailure(report, "Agregar Negocio modal", error);
  }

  // Step 4 - Open Administrar Negocios
  try {
    const administrarNegociosVisible = await page
      .getByText(/administrar negocios/i)
      .first()
      .isVisible()
      .catch(() => false);

    if (!administrarNegociosVisible) {
      const miNegocioOption = await firstVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        10_000,
      );
      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegociosOption = await firstVisible(
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ],
      15_000,
    );

    await clickAndWait(page, administrarNegociosOption);
    await expect(page.getByText(/información general|informacion general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/sección legal|seccion legal/i).first()).toBeVisible();

    const accountPageShot = await attachScreenshot(
      page,
      testInfo,
      "04-administrar-negocios-page",
      true,
    );
    report["Administrar Negocios view"].status = "PASS";
    report["Administrar Negocios view"].evidence.push(accountPageShot);
    report["Administrar Negocios view"].details.push(
      "Información General, Detalles de la Cuenta, Tus Negocios y Sección Legal are visible.",
    );
  } catch (error) {
    pushFailure(report, "Administrar Negocios view", error);
  }

  // Step 5 - Validate Información General
  try {
    const infoGeneralSection = page
      .locator("section, div")
      .filter({ hasText: /información general|informacion general/i })
      .first();
    await expect(infoGeneralSection).toBeVisible();

    if (expectedUserName) {
      await expect(infoGeneralSection.getByText(expectedUserName, { exact: false }).first()).toBeVisible();
    } else {
      const possibleName = infoGeneralSection
        .locator("h1, h2, h3, h4, p, span, strong")
        .filter({ hasNotText: /@/ })
        .first();
      await expect(possibleName).toBeVisible();
    }

    const emailCandidate = await firstVisible(
      [
        page.locator("[href^='mailto:']"),
        page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/),
      ],
      8_000,
    );
    await expect(emailCandidate).toBeVisible();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    report["Información General"].status = "PASS";
    report["Información General"].details.push(
      "User identity, email, BUSINESS PLAN, and Cambiar Plan button are visible.",
    );
  } catch (error) {
    pushFailure(report, "Información General", error);
  }

  // Step 6 - Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();

    report["Detalles de la Cuenta"].status = "PASS";
    report["Detalles de la Cuenta"].details.push(
      "Cuenta creada, Estado activo, and Idioma seleccionado are visible.",
    );
  } catch (error) {
    pushFailure(report, "Detalles de la Cuenta", error);
  }

  // Step 7 - Validate Tus Negocios
  try {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();

    const listContainer = page
      .locator("section, div")
      .filter({ hasText: /tus negocios/i })
      .first();
    await expect(listContainer).toBeVisible();

    report["Tus Negocios"].status = "PASS";
    report["Tus Negocios"].details.push(
      "Business list area, Agregar Negocio button, and 2 de 3 counter are visible.",
    );
  } catch (error) {
    pushFailure(report, "Tus Negocios", error);
  }

  // Step 8 and 9 - Validate legal links (new tab or same tab)
  const validateLegalLink = async (
    field: "Términos y Condiciones" | "Política de Privacidad",
    linkMatcher: RegExp,
    headingMatcher: RegExp,
    screenshotLabel: string,
  ): Promise<void> => {
    const appUrlBeforeClick = page.url();
    const link = await firstVisible(
      [
        page.getByRole("link", { name: linkMatcher }),
        page.getByRole("button", { name: linkMatcher }),
        page.getByText(linkMatcher),
      ],
      12_000,
    );

    const popupPromise = context.waitForEvent("page", { timeout: 6_000 }).catch(() => null);
    await clickAndWait(page, link);
    const popup = await popupPromise;
    const targetPage = popup ?? page;

    await targetPage.waitForLoadState("domcontentloaded", { timeout: 20_000 });
    await targetPage.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);

    const headingCandidate = await firstVisible(
      [
        targetPage.getByRole("heading", { name: headingMatcher }),
        targetPage.getByText(headingMatcher),
      ],
      15_000,
    );
    await expect(headingCandidate).toBeVisible();

    await expect(
      targetPage.getByText(
        /términos|terminos|privacidad|datos personales|responsabilidad|condiciones|uso/i,
      ).first(),
    ).toBeVisible();

    const legalShot = await attachScreenshot(targetPage, testInfo, screenshotLabel, true);
    report[field].status = "PASS";
    report[field].evidence.push(legalShot);
    report[field].finalUrl = targetPage.url();
    report[field].details.push(`Legal page loaded with heading and content. URL: ${targetPage.url()}`);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
      return;
    }

    if (page.url() !== appUrlBeforeClick) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(page);
    }
  };

  try {
    await validateLegalLink(
      "Términos y Condiciones",
      /términos y condiciones|terminos y condiciones/i,
      /términos y condiciones|terminos y condiciones/i,
      "08-terminos-y-condiciones",
    );
  } catch (error) {
    pushFailure(report, "Términos y Condiciones", error);
  }

  try {
    await validateLegalLink(
      "Política de Privacidad",
      /política de privacidad|politica de privacidad/i,
      /política de privacidad|politica de privacidad/i,
      "09-politica-de-privacidad",
    );
  } catch (error) {
    pushFailure(report, "Política de Privacidad", error);
  }

  const finalSummary = REPORT_FIELDS.reduce(
    (acc, field) => ({
      ...acc,
      [field]: report[field].status,
    }),
    {} as Record<ReportField, ValidationStatus>,
  );

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await writeFile(
    reportPath,
    JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        summary: finalSummary,
        details: report,
      },
      null,
      2,
    ),
    "utf8",
  );
  await testInfo.attach("final-report-json", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failedFields,
    `Validation failures found in: ${failedFields.join(", ") || "none"}.
See attached final-report-json for detailed evidence and error messages.`,
  ).toEqual([]);
});
