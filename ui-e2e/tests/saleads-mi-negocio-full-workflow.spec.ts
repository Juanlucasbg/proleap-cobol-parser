import { writeFileSync } from "node:fs";
import { expect, Locator, Page, test, TestInfo } from "@playwright/test";

type ReportStatus = "PASS" | "FAIL";

type ReportEntry = {
  status: ReportStatus;
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
  "Política de Privacidad",
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {
    // Some environments keep long-lived requests; DOM readiness is enough.
  });
}

async function firstVisible(page: Page, candidates: Locator[], timeoutMs = 10_000): Promise<Locator> {
  const pollDelay = 250;
  const attempts = Math.ceil(timeoutMs / pollDelay);

  for (let i = 0; i < attempts; i += 1) {
    for (const candidate of candidates) {
      const target = candidate.first();
      const count = await target.count();
      if (!count) {
        continue;
      }

      if (await target.isVisible().catch(() => false)) {
        return target;
      }
    }

    await page.waitForTimeout(pollDelay);
  }

  throw new Error("No visible element found for candidate selectors.");
}

async function clickByVisibleText(page: Page, text: RegExp): Promise<Locator> {
  const target = await firstVisible(page, [
    page.getByRole("button", { name: text }),
    page.getByRole("link", { name: text }),
    page.getByRole("menuitem", { name: text }),
    page.getByRole("tab", { name: text }),
    page.getByText(text),
  ]);

  await target.click();
  await waitForUiLoad(page);
  return target;
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false,
): Promise<string> {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(fileName, {
    path,
    contentType: "image/png",
  });
  return path;
}

function formatError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function createEmptyReport(): Record<ReportField, ReportEntry> {
  return REPORT_FIELDS.reduce(
    (accumulator, field) => ({
      ...accumulator,
      [field]: {
        status: "FAIL",
        details: "Not executed.",
        evidence: [],
      },
    }),
    {} as Record<ReportField, ReportEntry>,
  );
}

async function validateGoogleAccountSelection(page: Page): Promise<void> {
  const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
  const loginButton = await firstVisible(page, [
    page.getByRole("button", { name: /sign in with google|continuar con google|ingresar con google|google/i }),
    page.getByRole("link", { name: /sign in with google|continuar con google|ingresar con google|google/i }),
    page.getByText(/sign in with google|continuar con google|ingresar con google/i),
  ]);

  await loginButton.click();

  const popup = await popupPromise;
  if (popup) {
    await waitForUiLoad(popup);
    const accountCandidate = await firstVisible(popup, [
      popup.getByText(new RegExp(GOOGLE_ACCOUNT, "i")),
      popup.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT, "i") }),
    ]).catch(() => null);

    if (accountCandidate) {
      await accountCandidate.click();
      await popup.waitForEvent("close", { timeout: 15_000 }).catch(() => {
        // Popup can stay open in some OAuth variants.
      });
    }

    await page.bringToFront();
    await waitForUiLoad(page);
    return;
  }

  const accountInSameTab = await firstVisible(page, [
    page.getByText(new RegExp(GOOGLE_ACCOUNT, "i")),
    page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT, "i") }),
  ], 4_000).catch(() => null);

  if (accountInSameTab) {
    await accountInSameTab.click();
  }

  await waitForUiLoad(page);
}

async function validateLegalLink(
  appPage: Page,
  testInfo: TestInfo,
  linkText: RegExp,
  expectedHeading: RegExp,
  screenshotName: string,
): Promise<{ screenshot: string; finalUrl: string }> {
  const link = await firstVisible(appPage, [
    appPage.getByRole("link", { name: linkText }),
    appPage.getByRole("button", { name: linkText }),
    appPage.getByText(linkText),
  ]);

  const popupPromise = appPage.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
  await link.click();

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  await waitForUiLoad(legalPage);

  const heading = await firstVisible(legalPage, [
    legalPage.getByRole("heading", { name: expectedHeading }),
    legalPage.getByText(expectedHeading),
  ]);
  await expect(heading).toBeVisible();

  await expect(legalPage.locator("body")).toContainText(/\S{40,}/);
  const screenshot = await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close({ runBeforeUnload: true }).catch(() => {
      // Some pages may disallow scripted close.
    });
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {
      // If history navigation is unavailable, the next step will fail loudly.
    });
    await waitForUiLoad(appPage);
  }

  return { screenshot, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createEmptyReport();
  let abortRemainingSteps = false;

  const markPass = (field: ReportField, details: string, evidence: string[] = [], finalUrl?: string) => {
    report[field] = { status: "PASS", details, evidence, finalUrl };
  };

  const markFail = (field: ReportField, details: string) => {
    report[field] = { status: "FAIL", details, evidence: [] };
  };

  const runStep = async (field: ReportField, action: () => Promise<void>) => {
    if (abortRemainingSteps) {
      markFail(field, "Not executed because a previous critical step failed.");
      return;
    }

    try {
      await test.step(field, action);
    } catch (error) {
      markFail(field, formatError(error));
      abortRemainingSteps = true;
    }
  };

  const targetUrl = process.env.SALEADS_URL ?? process.env.BASE_URL ?? testInfo.project.use.baseURL;
  if (targetUrl) {
    await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No start URL available. Set SALEADS_URL/BASE_URL or run the test from an already-open SaleADS login page context.",
    );
  }
  await waitForUiLoad(page);

  await runStep("Login", async () => {
    await validateGoogleAccountSelection(page);

    const sidebar = await firstVisible(page, [
      page.locator("aside"),
      page.locator("nav"),
      page.getByText(/negocio|dashboard|mi negocio/i),
    ]);
    await expect(sidebar).toBeVisible();
    await expect(page.getByText(/negocio/i)).toBeVisible();

    const screenshot = await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
    markPass("Login", "Login completed and main interface with sidebar is visible.", [screenshot]);
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, /mi negocio/i);
    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();

    const screenshot = await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png");
    markPass("Mi Negocio menu", "Mi Negocio menu expanded with required submenu options.", [screenshot]);
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /agregar negocio/i);

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(
      await firstVisible(page, [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.getByText(/nombre del negocio/i),
      ]),
    ).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    const screenshot = await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const nameInput = await firstVisible(page, [
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").filter({ hasText: /nombre del negocio/i }),
      page.locator("input[type='text']"),
    ]).catch(() => null);

    if (nameInput) {
      await nameInput.fill("Negocio Prueba Automatización");
    }

    await clickByVisibleText(page, /cancelar/i);
    markPass("Agregar Negocio modal", "Agregar Negocio modal validated and closed successfully.", [screenshot]);
  });

  await runStep("Administrar Negocios view", async () => {
    const adminMenuVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!adminMenuVisible) {
      await clickByVisibleText(page, /mi negocio/i);
    }

    await clickByVisibleText(page, /administrar negocios/i);
    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

    const screenshot = await captureCheckpoint(page, testInfo, "04-administrar-negocios-view.png", true);
    markPass("Administrar Negocios view", "Administrar Negocios page loaded with all required sections.", [screenshot]);
  });

  await runStep("Información General", async () => {
    const infoSection = page.locator("section,div,article").filter({ has: page.getByText(/informaci[oó]n general/i) }).first();
    await expect(infoSection).toBeVisible();

    const emailLocator = infoSection.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/");
    await expect(emailLocator).toBeVisible();

    const sectionText = (await infoSection.innerText()).replace(/\s+/g, " ").trim();
    if (!/[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}/.test(sectionText.replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, ""))) {
      throw new Error("User name was not detected in 'Información General'.");
    }

    await expect(infoSection.getByText(/business plan/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    markPass("Información General", "User name/email, plan badge, and 'Cambiar Plan' are visible.");
  });

  await runStep("Detalles de la Cuenta", async () => {
    const accountDetailsSection = page
      .locator("section,div,article")
      .filter({ has: page.getByText(/detalles de la cuenta/i) })
      .first();
    await expect(accountDetailsSection).toBeVisible();
    await expect(accountDetailsSection.getByText(/cuenta creada/i)).toBeVisible();
    await expect(accountDetailsSection.getByText(/estado activo/i)).toBeVisible();
    await expect(accountDetailsSection.getByText(/idioma seleccionado/i)).toBeVisible();
    markPass("Detalles de la Cuenta", "Required account detail labels are visible.");
  });

  await runStep("Tus Negocios", async () => {
    const businessesSection = page.locator("section,div,article").filter({ has: page.getByText(/tus negocios/i) }).first();
    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(businessesSection.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();

    const businessListItem = await firstVisible(page, [
      businessesSection.locator("li"),
      businessesSection.locator("[role='row']"),
      businessesSection.locator("article"),
      businessesSection.locator("div").filter({ hasText: /negocio/i }),
    ]).catch(() => null);

    if (!businessListItem) {
      throw new Error("Business list items were not detected in 'Tus Negocios'.");
    }

    markPass("Tus Negocios", "Business list, add button, and quota text validated.");
  });

  await runStep("Términos y Condiciones", async () => {
    const { screenshot, finalUrl } = await validateLegalLink(
      page,
      testInfo,
      /t[ée]rminos y condiciones/i,
      /t[ée]rminos y condiciones/i,
      "05-terminos-y-condiciones.png",
    );

    markPass("Términos y Condiciones", "Legal page loaded with heading and content.", [screenshot], finalUrl);
  });

  await runStep("Política de Privacidad", async () => {
    const { screenshot, finalUrl } = await validateLegalLink(
      page,
      testInfo,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad.png",
    );

    markPass("Política de Privacidad", "Privacy page loaded with heading and content.", [screenshot], finalUrl);
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: REPORT_FIELDS.map((field) => ({
      field,
      status: report[field].status,
      details: report[field].details,
      evidence: report[field].evidence,
      finalUrl: report[field].finalUrl,
    })),
  };

  const finalReportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  writeFileSync(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  const failures = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(failures, `One or more workflow validations failed. Report: ${finalReportPath}`).toEqual([]);
});
