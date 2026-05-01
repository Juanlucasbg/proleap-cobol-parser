import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import { promises as fs } from "node:fs";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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
type ReportStatus = "PASS" | "FAIL";

type ReportRecord = {
  status: ReportStatus;
  details?: string;
};

function sanitizeName(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForTimeout(300);
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 10_000 }),
    page.waitForLoadState("networkidle", { timeout: 7_500 }),
  ]);
  await page.waitForTimeout(200);
}

async function safeIsVisible(locator: Locator): Promise<boolean> {
  try {
    return await locator.first().isVisible();
  } catch {
    return false;
  }
}

async function resolveVisibleLocator(name: string, candidates: Locator[]): Promise<Locator> {
  for (const candidate of candidates) {
    const locator = candidate.first();
    try {
      await locator.waitFor({ state: "visible", timeout: 3_000 });
      return locator;
    } catch {
      // continue candidate search
    }
  }
  throw new Error(`Could not locate a visible element for: ${name}`);
}

async function clickAndWaitUi(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(
  testInfo: TestInfo,
  page: Page,
  checkpointName: string,
  fullPage = false,
): Promise<void> {
  const fileName = `${sanitizeName(checkpointName)}.png`;
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(checkpointName, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function findApplicationPage(contextPages: Page[]): Promise<Page | undefined> {
  for (const candidate of contextPages) {
    const sidebarVisible =
      (await safeIsVisible(candidate.locator("aside"))) ||
      (await safeIsVisible(candidate.getByRole("navigation")));
    const negocioVisible = await safeIsVisible(candidate.getByText(/mi negocio|negocio/i));

    if (sidebarVisible && negocioVisible) {
      return candidate;
    }
  }

  return undefined;
}

async function maybeSelectGoogleAccount(page: Page): Promise<void> {
  const accountChoice = await resolveVisibleLocator("Google account selector", [
    page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
    page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
    page.locator(`[data-email="${GOOGLE_ACCOUNT_EMAIL}"]`),
  ]).catch(() => undefined);

  if (!accountChoice) {
    return;
  }

  await accountChoice.click();
  await waitForUi(page);
}

async function withStepReport(
  report: Record<ReportField, ReportRecord>,
  field: ReportField,
  action: () => Promise<void>,
): Promise<void> {
  try {
    await action();
    report[field] = { status: "PASS" };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    report[field] = { status: "FAIL", details: message };
  }
}

async function openLegalLinkAndResolvePage(
  appPage: Page,
  linkText: RegExp,
): Promise<{ legalPage: Page; openedNewTab: boolean }> {
  const link = await resolveVisibleLocator(`Legal link ${String(linkText)}`, [
    appPage.getByRole("link", { name: linkText }),
    appPage.getByRole("button", { name: linkText }),
    appPage.getByText(linkText),
  ]);

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 10_000 }).catch(() => undefined);
  await clickAndWaitUi(appPage, link);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await waitForUi(popup);
    await popup.bringToFront();
    return { legalPage: popup, openedNewTab: true };
  }

  await waitForUi(appPage);
  return { legalPage: appPage, openedNewTab: false };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL" as ReportStatus, details: "Not executed" }]),
  ) as Record<ReportField, ReportRecord>;

  const configuredBaseUrl = process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;
  if (configuredBaseUrl) {
    await page.goto(configuredBaseUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  let appPage = page;

  await withStepReport(report, "Login", async () => {
    const appAlreadyLoaded =
      ((await safeIsVisible(page.locator("aside"))) || (await safeIsVisible(page.getByRole("navigation")))) &&
      (await safeIsVisible(page.getByText(/mi negocio|negocio/i)));

    if (!appAlreadyLoaded) {
      const loginButton = await resolveVisibleLocator("Login with Google button", [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ]);

      const popupPromise = page.context().waitForEvent("page", { timeout: 12_000 }).catch(() => undefined);
      await clickAndWaitUi(page, loginButton);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await maybeSelectGoogleAccount(popup);
      } else {
        await maybeSelectGoogleAccount(page);
      }
    }

    const deadline = Date.now() + 60_000;
    let detectedAppPage: Page | undefined;
    while (!detectedAppPage && Date.now() < deadline) {
      detectedAppPage = await findApplicationPage(page.context().pages());
      if (!detectedAppPage) {
        await page.waitForTimeout(1_000);
      }
    }

    if (!detectedAppPage) {
      throw new Error("Main app interface with sidebar was not detected after Google login.");
    }

    appPage = detectedAppPage;
    await expect(appPage.locator("aside, nav").first()).toBeVisible();
    await captureCheckpoint(testInfo, appPage, "01-dashboard-loaded");
  });

  await withStepReport(report, "Mi Negocio menu", async () => {
    await expect(appPage.locator("aside, nav").first()).toBeVisible();
    await expect(appPage.getByText(/negocio/i)).toBeVisible();

    const miNegocioTrigger = await resolveVisibleLocator("Mi Negocio option", [
      appPage.getByRole("button", { name: /mi negocio/i }),
      appPage.getByRole("link", { name: /mi negocio/i }),
      appPage.getByText(/^mi negocio$/i),
      appPage.getByText(/mi negocio/i),
    ]);

    const agregarNegocioOption = appPage.getByText(/agregar negocio/i).first();
    const administrarNegociosOption = appPage.getByText(/administrar negocios/i).first();

    if (!(await safeIsVisible(agregarNegocioOption)) || !(await safeIsVisible(administrarNegociosOption))) {
      await clickAndWaitUi(appPage, miNegocioTrigger);
    }

    if (!(await safeIsVisible(agregarNegocioOption)) || !(await safeIsVisible(administrarNegociosOption))) {
      await clickAndWaitUi(appPage, miNegocioTrigger);
    }

    await expect(agregarNegocioOption).toBeVisible();
    await expect(administrarNegociosOption).toBeVisible();
    await captureCheckpoint(testInfo, appPage, "02-mi-negocio-menu-expanded");
  });

  await withStepReport(report, "Agregar Negocio modal", async () => {
    const agregarNegocioOption = await resolveVisibleLocator("Agregar Negocio submenu option", [
      appPage.getByRole("button", { name: /agregar negocio/i }),
      appPage.getByRole("link", { name: /agregar negocio/i }),
      appPage.getByText(/agregar negocio/i),
    ]);

    await clickAndWaitUi(appPage, agregarNegocioOption);

    const modalTitle = appPage.getByText(/crear nuevo negocio/i).first();
    const nombreInput = appPage.getByLabel(/nombre del negocio/i).first();
    const negociosCounter = appPage.getByText(/tienes 2 de 3 negocios/i).first();
    const cancelButton = appPage.getByRole("button", { name: /cancelar/i }).first();
    const createButton = appPage.getByRole("button", { name: /crear negocio/i }).first();

    await expect(modalTitle).toBeVisible();
    await expect(nombreInput).toBeVisible();
    await expect(negociosCounter).toBeVisible();
    await expect(cancelButton).toBeVisible();
    await expect(createButton).toBeVisible();

    await captureCheckpoint(testInfo, appPage, "03-agregar-negocio-modal");

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    await clickAndWaitUi(appPage, cancelButton);
    await expect(modalTitle).not.toBeVisible();
  });

  await withStepReport(report, "Administrar Negocios view", async () => {
    const administrarOption = appPage.getByText(/administrar negocios/i).first();
    if (!(await safeIsVisible(administrarOption))) {
      const miNegocioTrigger = await resolveVisibleLocator("Mi Negocio option", [
        appPage.getByRole("button", { name: /mi negocio/i }),
        appPage.getByRole("link", { name: /mi negocio/i }),
        appPage.getByText(/mi negocio/i),
      ]);
      await clickAndWaitUi(appPage, miNegocioTrigger);
    }

    const administrarNegociosOption = await resolveVisibleLocator("Administrar Negocios submenu option", [
      appPage.getByRole("button", { name: /administrar negocios/i }),
      appPage.getByRole("link", { name: /administrar negocios/i }),
      appPage.getByText(/administrar negocios/i),
    ]);

    await clickAndWaitUi(appPage, administrarNegociosOption);

    await expect(appPage.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(appPage.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(appPage.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(appPage.getByText(/secci[oó]n legal/i).first()).toBeVisible();

    await captureCheckpoint(testInfo, appPage, "04-administrar-negocios", true);
  });

  await withStepReport(report, "Información General", async () => {
    const infoHeading = appPage.getByText(/informaci[oó]n general/i).first();
    await expect(infoHeading).toBeVisible();

    const infoSection = appPage.locator("section, div").filter({ has: infoHeading }).first();
    await expect(infoSection).toBeVisible();

    const emailCandidate = infoSection.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();
    await expect(emailCandidate).toBeVisible();

    const knownNameCandidate = infoSection.getByText(/juan|lucas|barbier|garzon/i).first();
    const textCandidates = (await infoSection.locator("h1, h2, h3, h4, p, span, strong").allTextContents())
      .map((text) => text.trim())
      .filter(Boolean);
    const hasNameLikeText = textCandidates.some(
      (text) =>
        /^[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}(?:\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,})+$/.test(text) &&
        !/business plan|cambiar plan|informaci[oó]n general/i.test(text),
    );

    if (!(await safeIsVisible(knownNameCandidate)) && !hasNameLikeText) {
      throw new Error("User name is not clearly visible in Información General.");
    }

    await expect(infoSection.getByText(/business plan/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
  });

  await withStepReport(report, "Detalles de la Cuenta", async () => {
    const accountDetailsHeading = appPage.getByText(/detalles de la cuenta/i).first();
    await expect(accountDetailsHeading).toBeVisible();

    const detailsSection = appPage.locator("section, div").filter({ has: accountDetailsHeading }).first();
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await withStepReport(report, "Tus Negocios", async () => {
    const businessesHeading = appPage.getByText(/tus negocios/i).first();
    await expect(businessesHeading).toBeVisible();

    const businessesSection = appPage.locator("section, div").filter({ has: businessesHeading }).first();
    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(businessesSection.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();

    const listEntries = businessesSection.locator("li, tr, [role='row'], [data-testid*='business'], [class*='business']");
    const entriesCount = await listEntries.count();
    expect(entriesCount).toBeGreaterThan(0);
  });

  await withStepReport(report, "Términos y Condiciones", async () => {
    const appUrlBefore = appPage.url();
    const { legalPage, openedNewTab } = await openLegalLinkAndResolvePage(appPage, /t[eé]rminos y condiciones/i);

    const title = await resolveVisibleLocator("Términos y Condiciones heading", [
      legalPage.getByRole("heading", { name: /t[eé]rminos y condiciones/i }),
      legalPage.getByText(/t[eé]rminos y condiciones/i),
    ]);

    await expect(title).toBeVisible();

    const legalTextLength = ((await legalPage.locator("body").innerText()).trim() || "").length;
    expect(legalTextLength).toBeGreaterThan(150);

    await captureCheckpoint(testInfo, legalPage, "05-terminos-y-condiciones");
    report["Términos y Condiciones"].details = `URL: ${legalPage.url()}`;

    if (openedNewTab) {
      await legalPage.close();
      await appPage.bringToFront();
      await waitForUi(appPage);
    } else if (appPage.url() !== appUrlBefore) {
      await appPage.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(appPage);
    }
  });

  await withStepReport(report, "Política de Privacidad", async () => {
    const appUrlBefore = appPage.url();
    const { legalPage, openedNewTab } = await openLegalLinkAndResolvePage(appPage, /pol[ií]tica de privacidad/i);

    const title = await resolveVisibleLocator("Política de Privacidad heading", [
      legalPage.getByRole("heading", { name: /pol[ií]tica de privacidad/i }),
      legalPage.getByText(/pol[ií]tica de privacidad/i),
    ]);

    await expect(title).toBeVisible();

    const legalTextLength = ((await legalPage.locator("body").innerText()).trim() || "").length;
    expect(legalTextLength).toBeGreaterThan(150);

    await captureCheckpoint(testInfo, legalPage, "06-politica-de-privacidad");
    report["Política de Privacidad"].details = `URL: ${legalPage.url()}`;

    if (openedNewTab) {
      await legalPage.close();
      await appPage.bringToFront();
      await waitForUi(appPage);
    } else if (appPage.url() !== appUrlBefore) {
      await appPage.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(appPage);
    }
  });

  const finalReportLines = REPORT_FIELDS.map((field) => {
    const detailsSuffix = report[field].details ? ` (${report[field].details})` : "";
    return `${field}: ${report[field].status}${detailsSuffix}`;
  });

  const finalReport = `Final Report\n${finalReportLines.join("\n")}`;
  console.log(finalReport);

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
  await testInfo.attach("final-report-json", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failedFields,
    `One or more workflow validations failed.\n${finalReportLines.join("\n")}`,
  ).toEqual([]);
});
