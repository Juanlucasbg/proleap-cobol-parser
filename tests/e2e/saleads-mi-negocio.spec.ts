import fs from "node:fs";
import { expect, test, type BrowserContext, type Locator, type Page, type TestInfo } from "@playwright/test";

type ResultStatus = "PASS" | "FAIL";

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

type ReportField = (typeof REPORT_FIELDS)[number];
type ValidationReport = Record<ReportField, ResultStatus>;

function createReport(): ValidationReport {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
}

function screenshotFileName(label: string): string {
  return `${label.toLowerCase().replace(/[^a-z0-9]+/gi, "-").replace(/^-|-$/g, "")}.png`;
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 20_000 });
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  const checkpointPath = testInfo.outputPath(screenshotFileName(name));
  await page.screenshot({ path: checkpointPath, fullPage });
  await testInfo.attach(name, { path: checkpointPath, contentType: "image/png" });
}

async function firstVisibleLocator(candidates: Locator[], timeoutMs = 3_000): Promise<Locator> {
  for (const candidate of candidates) {
    const current = candidate.first();
    if (await current.isVisible({ timeout: timeoutMs }).catch(() => false)) {
      return current;
    }
  }

  throw new Error("Could not find a visible locator for the requested UI element.");
}

async function chooseGoogleAccountIfPrompted(context: BrowserContext, accountEmail: string): Promise<void> {
  for (const candidatePage of context.pages()) {
    const accountOption = candidatePage.getByText(accountEmail, { exact: false }).first();
    if (await accountOption.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await accountOption.click();
      await waitForUi(candidatePage);
      return;
    }
  }
}

async function locateApplicationPage(context: BrowserContext, fallback: Page): Promise<Page> {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    for (const candidatePage of context.pages()) {
      const negocioText = candidatePage.getByText(/Mi Negocio|Negocio/i).first();
      if (await negocioText.isVisible({ timeout: 1_000 }).catch(() => false)) {
        return candidatePage;
      }
    }

    await fallback.waitForTimeout(1_000);
  }

  return fallback;
}

function summarizeError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function prepareStartingPage(page: Page): Promise<void> {
  if (page.url() === "about:blank") {
    const targetUrl = process.env.SALEADS_URL ?? process.env.BASE_URL;
    if (!targetUrl) {
      throw new Error(
        "The browser starts on about:blank in Playwright. Set SALEADS_URL (or BASE_URL) to the current environment login URL."
      );
    }

    await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);
}

async function openLegalPageAndReturnToApp(
  appPage: Page,
  context: BrowserContext,
  linkRegex: RegExp,
  headingRegex: RegExp,
  checkpointName: string,
  testInfo: TestInfo
): Promise<string> {
  const appUrlBeforeOpen = appPage.url();
  const legalLink = await firstVisibleLocator([
    appPage.getByRole("link", { name: linkRegex }),
    appPage.getByRole("button", { name: linkRegex }),
    appPage.getByText(linkRegex)
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await clickAndWait(appPage, legalLink);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  await waitForUi(legalPage);

  const legalHeading = await firstVisibleLocator([
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex)
  ]);
  await expect(legalHeading).toBeVisible();

  const legalBodyText = (await legalPage.locator("body").innerText()).trim();
  expect(legalBodyText.length).toBeGreaterThan(120);

  await captureCheckpoint(legalPage, testInfo, checkpointName);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else if (legalPage.url() !== appUrlBeforeOpen) {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await legalPage.goto(appUrlBeforeOpen, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(legalPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";
  const report = createReport();
  const failures: string[] = [];
  const evidenceUrls: Record<string, string> = {};
  let appPage = page;

  const runValidation = async (field: ReportField, action: () => Promise<void>) => {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      failures.push(`${field}: ${summarizeError(error)}`);
    }
  };

  await runValidation("Login", async () => {
    await prepareStartingPage(appPage);

    const loginButton = await firstVisibleLocator([
      appPage.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
      appPage.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
      appPage.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i)
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(appPage, loginButton);
    const popup = await popupPromise;
    if (popup) {
      await waitForUi(popup);
    }

    await chooseGoogleAccountIfPrompted(context, googleAccount);
    appPage = await locateApplicationPage(context, appPage);

    await expect(appPage.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 40_000 });
    await captureCheckpoint(appPage, testInfo, "01-dashboard-loaded");
  });

  await runValidation("Mi Negocio menu", async () => {
    const negocioSection = await firstVisibleLocator([
      appPage.getByRole("button", { name: /^Negocio$/i }),
      appPage.getByText(/^Negocio$/i),
      appPage.getByText(/Negocio/i)
    ]);
    await clickAndWait(appPage, negocioSection);

    const miNegocioOption = await firstVisibleLocator([
      appPage.getByRole("button", { name: /Mi Negocio/i }),
      appPage.getByRole("link", { name: /Mi Negocio/i }),
      appPage.getByText(/Mi Negocio/i)
    ]);
    await clickAndWait(appPage, miNegocioOption);

    await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await captureCheckpoint(appPage, testInfo, "02-mi-negocio-expanded-menu");
  });

  await runValidation("Agregar Negocio modal", async () => {
    const agregarNegocioOption = await firstVisibleLocator([
      appPage.getByRole("button", { name: /^Agregar Negocio$/i }),
      appPage.getByRole("link", { name: /^Agregar Negocio$/i }),
      appPage.getByText(/^Agregar Negocio$/i)
    ]);
    await clickAndWait(appPage, agregarNegocioOption);

    await expect(appPage.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();

    const nombreNegocioInput = await firstVisibleLocator([
      appPage.getByLabel(/Nombre del Negocio/i),
      appPage.getByPlaceholder(/Nombre del Negocio/i),
      appPage.locator("input").filter({ hasText: /Nombre del Negocio/i })
    ]);
    await expect(nombreNegocioInput).toBeVisible();

    await expect(appPage.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await captureCheckpoint(appPage, testInfo, "03-crear-nuevo-negocio-modal");

    await nombreNegocioInput.click();
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await clickAndWait(appPage, appPage.getByRole("button", { name: /Cancelar/i }));
  });

  await runValidation("Administrar Negocios view", async () => {
    const administrarNegociosVisible = await appPage.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarNegociosVisible) {
      const miNegocioOption = await firstVisibleLocator([
        appPage.getByRole("button", { name: /Mi Negocio/i }),
        appPage.getByRole("link", { name: /Mi Negocio/i }),
        appPage.getByText(/Mi Negocio/i)
      ]);
      await clickAndWait(appPage, miNegocioOption);
    }

    const administrarNegociosOption = await firstVisibleLocator([
      appPage.getByRole("button", { name: /Administrar Negocios/i }),
      appPage.getByRole("link", { name: /Administrar Negocios/i }),
      appPage.getByText(/Administrar Negocios/i)
    ]);
    await clickAndWait(appPage, administrarNegociosOption);

    await expect(appPage.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(appPage.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(appPage.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
    await captureCheckpoint(appPage, testInfo, "04-administrar-negocios", true);
  });

  await runValidation("Información General", async () => {
    const infoSection = appPage.locator("section,div").filter({ hasText: /Informaci[oó]n General/i }).first();
    await expect(infoSection).toBeVisible();
    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const infoText = (await infoSection.innerText()).replace(/\s+/g, " ").trim();
    expect(infoText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    expect(
      infoText
        .split(" ")
        .some((token) => token.length >= 3 && !token.includes("@") && !/informaci[oó]n|general|business|plan|cambiar/i.test(token))
    ).toBeTruthy();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    const detailsSection = appPage.locator("section,div").filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    const businessesSection = appPage.locator("section,div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const businessSectionText = (await businessesSection.innerText()).trim();
    expect(businessSectionText.length).toBeGreaterThan(40);
  });

  await runValidation("Términos y Condiciones", async () => {
    const finalUrl = await openLegalPageAndReturnToApp(
      appPage,
      context,
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "05-terminos-y-condiciones",
      testInfo
    );

    evidenceUrls["Términos y Condiciones"] = finalUrl;
  });

  await runValidation("Política de Privacidad", async () => {
    const finalUrl = await openLegalPageAndReturnToApp(
      appPage,
      context,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "06-politica-de-privacidad",
      testInfo
    );

    evidenceUrls["Política de Privacidad"] = finalUrl;
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    evidence: {
      legalUrls: evidenceUrls
    },
    failures
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log(`Final report:\n${JSON.stringify(finalReport, null, 2)}`);
  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
