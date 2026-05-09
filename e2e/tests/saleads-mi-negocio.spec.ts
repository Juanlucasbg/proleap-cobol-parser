import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import * as fs from "node:fs/promises";
import * as path from "node:path";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informaci\u00f3n General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "T\u00e9rminos y Condiciones"
  | "Pol\u00edtica de Privacidad";

type StepStatus = "PASS" | "FAIL";

interface StepResult {
  status: StepStatus;
  details?: string;
  evidence?: string[];
  finalUrl?: string;
}

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const POLL_INTERVAL_MS = 250;

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page }, testInfo) => {
    const report = createInitialReport();
    const accountPageUrl = { value: "" };
    let appPage: Page = page;

    await runStep(
      report,
      "Login",
      async () => {
        const loginUrl = process.env.SALEADS_LOGIN_URL;
        if (!loginUrl) {
          throw new Error("SALEADS_LOGIN_URL is required. Use the login URL for the current environment.");
        }

        await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
        await waitForUiToLoad(page);

        const loginButton = await firstVisibleLocator([
          page.getByRole("button", {
            name: /sign in with google|continue with google|iniciar sesi[o\u00f3]n con google|continuar con google|google/i,
          }),
          page.getByRole("link", {
            name: /sign in with google|continue with google|iniciar sesi[o\u00f3]n con google|continuar con google|google/i,
          }),
          page.getByText(/sign in with google|continue with google|iniciar sesi[o\u00f3]n con google|continuar con google/i),
        ]);

        const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
        await clickAndWait(loginButton, page);
        const popup = await popupPromise;

        if (popup) {
          await popup.waitForLoadState("domcontentloaded");
          await selectGoogleAccountIfVisible(popup);
          await popup.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => undefined);
          if (await isMainApplicationVisible(popup)) {
            appPage = popup;
          }
        } else {
          await selectGoogleAccountIfVisible(page);
        }

        if (!(await isMainApplicationVisible(appPage)) && (await isMainApplicationVisible(page))) {
          appPage = page;
        }

        await expectSidebarVisible(appPage);
        const dashboardShot = await checkpointScreenshot(appPage, testInfo, "01-dashboard-loaded");
        report.Login.evidence = [dashboardShot];
      },
      () => appPage,
      testInfo,
    );

    await runStep(
      report,
      "Mi Negocio menu",
      async () => {
        await expectSidebarVisible(appPage);
        await expandMiNegocioMenu(appPage);
        const menuShot = await checkpointScreenshot(appPage, testInfo, "02-mi-negocio-menu-expanded");
        report["Mi Negocio menu"].evidence = [menuShot];
      },
      () => appPage,
      testInfo,
    );

    await runStep(
      report,
      "Agregar Negocio modal",
      async () => {
        await expandMiNegocioMenu(appPage);
        const sidebar = await sidebarLocator(appPage);
        const addBusinessEntry = await firstVisibleLocator([
          sidebar.getByRole("button", { name: /agregar negocio/i }),
          sidebar.getByRole("link", { name: /agregar negocio/i }),
          sidebar.getByText(/agregar negocio/i),
        ]);

        await clickAndWait(addBusinessEntry, appPage);

        const modal = appPage.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
        await expect(modal).toBeVisible();
        await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
        await expect(modal.getByLabel(/nombre del negocio/i)).toBeVisible();
        await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
        await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
        await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

        const modalShot = await checkpointScreenshot(appPage, testInfo, "03-agregar-negocio-modal");
        report["Agregar Negocio modal"].evidence = [modalShot];

        await modal.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatizacion");
        await clickAndWait(modal.getByRole("button", { name: /cancelar/i }), appPage);
        await expect(modal).toBeHidden({ timeout: 10000 });
      },
      () => appPage,
      testInfo,
    );

    await runStep(
      report,
      "Administrar Negocios view",
      async () => {
        await expandMiNegocioMenu(appPage);
        const sidebar = await sidebarLocator(appPage);
        const manageBusinesses = await firstVisibleLocator([
          sidebar.getByRole("button", { name: /administrar negocios/i }),
          sidebar.getByRole("link", { name: /administrar negocios/i }),
          sidebar.getByText(/administrar negocios/i),
        ]);
        await clickAndWait(manageBusinesses, appPage);

        await expect(appPage.getByText(/informaci[o\u00f3]n general/i)).toBeVisible();
        await expect(appPage.getByText(/detalles de la cuenta/i)).toBeVisible();
        await expect(appPage.getByText(/tus negocios/i)).toBeVisible();
        await expect(appPage.getByText(/secci[o\u00f3]n legal/i)).toBeVisible();

        accountPageUrl.value = appPage.url();
        const accountShot = await checkpointScreenshot(appPage, testInfo, "04-administrar-negocios-page", true);
        report["Administrar Negocios view"].evidence = [accountShot];
      },
      () => appPage,
      testInfo,
    );

    await runStep(
      report,
      "Informaci\u00f3n General",
      async () => {
        const section = await sectionByHeading(appPage, /informaci[o\u00f3]n general/i);
        await expect(section).toBeVisible();
        await expect(section.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false })).toBeVisible();
        await expect(section.getByText(/business plan/i)).toBeVisible();
        await expect(section.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

        const sectionText = (await section.textContent()) ?? "";
        const looksLikeUserName = sectionText
          .split(/\s+/)
          .some((token) => /^[A-Za-z][A-Za-z-]{2,}$/.test(token) && token.toLowerCase() !== "business");
        expect(looksLikeUserName, "Expected a visible user name in Informacion General").toBeTruthy();
      },
      () => appPage,
      testInfo,
    );

    await runStep(
      report,
      "Detalles de la Cuenta",
      async () => {
        const section = await sectionByHeading(appPage, /detalles de la cuenta/i);
        await expect(section).toBeVisible();
        await expect(section.getByText(/cuenta creada/i)).toBeVisible();
        await expect(section.getByText(/estado activo/i)).toBeVisible();
        await expect(section.getByText(/idioma seleccionado/i)).toBeVisible();
      },
      () => appPage,
      testInfo,
    );

    await runStep(
      report,
      "Tus Negocios",
      async () => {
        const section = await sectionByHeading(appPage, /tus negocios/i);
        await expect(section).toBeVisible();
        await expect(section.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
        await expect(section.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

        const listLikeLocator = await firstVisibleLocator([
          section.locator('[role="list"]'),
          section.locator('[role="table"]'),
          section.locator("ul"),
          section.locator("table"),
          section.locator("li"),
        ]);
        await expect(listLikeLocator).toBeVisible();
      },
      () => appPage,
      testInfo,
    );

    await runStep(
      report,
      "T\u00e9rminos y Condiciones",
      async () => {
        const result = await openLegalPageAndValidate({
          appPage,
          accountPageUrl: accountPageUrl.value,
          legalLinkPattern: /t[e\u00e9]rminos y condiciones/i,
          legalHeadingPattern: /t[e\u00e9]rminos y condiciones/i,
          screenshotName: "08-terminos-y-condiciones",
          testInfo,
        });

        report["T\u00e9rminos y Condiciones"].evidence = [result.screenshotPath];
        report["T\u00e9rminos y Condiciones"].finalUrl = result.finalUrl;
      },
      () => appPage,
      testInfo,
    );

    await runStep(
      report,
      "Pol\u00edtica de Privacidad",
      async () => {
        const result = await openLegalPageAndValidate({
          appPage,
          accountPageUrl: accountPageUrl.value,
          legalLinkPattern: /pol[i\u00ed]tica de privacidad/i,
          legalHeadingPattern: /pol[i\u00ed]tica de privacidad/i,
          screenshotName: "09-politica-de-privacidad",
          testInfo,
        });

        report["Pol\u00edtica de Privacidad"].evidence = [result.screenshotPath];
        report["Pol\u00edtica de Privacidad"].finalUrl = result.finalUrl;
      },
      () => appPage,
      testInfo,
    );

    const finalReportPath = testInfo.outputPath("final-report.json");
    await fs.writeFile(finalReportPath, JSON.stringify(report, null, 2), "utf8");
    await testInfo.attach("final-report.json", {
      body: Buffer.from(JSON.stringify(report, null, 2), "utf8"),
      contentType: "application/json",
    });

    console.log(`Final report saved to ${path.relative(process.cwd(), finalReportPath)}`);
    console.log(JSON.stringify(report, null, 2));

    const failedSteps = Object.entries(report)
      .filter(([, result]) => result.status === "FAIL")
      .map(([name]) => name);
    expect(failedSteps, `Some workflow checks failed: ${failedSteps.join(", ")}`).toEqual([]);
  });
});

async function runStep(
  report: Record<ReportField, StepResult>,
  field: ReportField,
  action: () => Promise<void>,
  pageProvider: () => Page,
  testInfo: TestInfo,
): Promise<void> {
  try {
    await action();
    report[field].status = "PASS";
    delete report[field].details;
  } catch (error) {
    report[field].status = "FAIL";
    report[field].details = error instanceof Error ? error.message : String(error);
    const failureShot = await checkpointScreenshot(pageProvider(), testInfo, `failed-${slugify(field)}`).catch(() => "");
    if (failureShot) {
      report[field].evidence = [...(report[field].evidence ?? []), failureShot];
    }
  }
}

function createInitialReport(): Record<ReportField, StepResult> {
  return {
    Login: { status: "FAIL", details: "Not executed" },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed" },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed" },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed" },
    "Informaci\u00f3n General": { status: "FAIL", details: "Not executed" },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed" },
    "Tus Negocios": { status: "FAIL", details: "Not executed" },
    "T\u00e9rminos y Condiciones": { status: "FAIL", details: "Not executed" },
    "Pol\u00edtica de Privacidad": { status: "FAIL", details: "Not executed" },
  };
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => undefined);
  const loadingIndicator = page.getByText(/cargando|loading|por favor espera/i).first();
  await loadingIndicator.waitFor({ state: "hidden", timeout: 10000 }).catch(() => undefined);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 15000 });
  await locator.click();
  await waitForUiToLoad(page);
}

async function firstVisibleLocator(candidates: Locator[], timeoutMs = 15000): Promise<Locator> {
  if (candidates.length === 0) {
    throw new Error("No locator candidates were provided.");
  }

  const timeoutAt = Date.now() + timeoutMs;
  while (Date.now() < timeoutAt) {
    for (const candidate of candidates) {
      const target = candidate.first();
      if (await target.isVisible().catch(() => false)) {
        return target;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }

  return candidates[0].first();
}

async function selectGoogleAccountIfVisible(authPage: Page): Promise<void> {
  const accountChoice = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountChoice.isVisible({ timeout: 10000 }).catch(() => false)) {
    await clickAndWait(accountChoice, authPage);
  }
}

async function isMainApplicationVisible(candidatePage: Page): Promise<boolean> {
  const navLocator = candidatePage.locator("aside, nav").filter({ hasText: /negocio|mi negocio/i }).first();
  return navLocator.isVisible({ timeout: 7000 }).catch(() => false);
}

async function expectSidebarVisible(candidatePage: Page): Promise<void> {
  const sidebar = await sidebarLocator(candidatePage);
  await expect(sidebar).toBeVisible({ timeout: 30000 });
}

async function sidebarLocator(appPage: Page): Promise<Locator> {
  return firstVisibleLocator([
    appPage.locator("aside").filter({ hasText: /negocio|mi negocio/i }),
    appPage.locator("nav").filter({ hasText: /negocio|mi negocio/i }),
  ]);
}

async function expandMiNegocioMenu(appPage: Page): Promise<void> {
  const sidebar = await sidebarLocator(appPage);
  const agregar = sidebar.getByText(/agregar negocio/i).first();
  const administrar = sidebar.getByText(/administrar negocios/i).first();

  if ((await agregar.isVisible().catch(() => false)) && (await administrar.isVisible().catch(() => false))) {
    return;
  }

  const negocio = await firstVisibleLocator(
    [
      sidebar.getByRole("button", { name: /^negocio$/i }),
      sidebar.getByRole("link", { name: /^negocio$/i }),
      sidebar.getByText(/^negocio$/i),
    ],
    4000,
  ).catch(() => null);
  if (negocio) {
    await clickAndWait(negocio, appPage);
  }

  const miNegocio = await firstVisibleLocator(
    [
      sidebar.getByRole("button", { name: /mi negocio/i }),
      sidebar.getByRole("link", { name: /mi negocio/i }),
      sidebar.getByText(/mi negocio/i),
    ],
    4000,
  ).catch(() => null);
  if (miNegocio) {
    await clickAndWait(miNegocio, appPage);
  }

  await expect(sidebar.getByText(/agregar negocio/i)).toBeVisible({ timeout: 15000 });
  await expect(sidebar.getByText(/administrar negocios/i)).toBeVisible({ timeout: 15000 });
}

async function sectionByHeading(appPage: Page, headingPattern: RegExp): Promise<Locator> {
  const heading = appPage.getByRole("heading", { name: headingPattern }).first();
  if (await heading.isVisible().catch(() => false)) {
    return heading.locator("xpath=ancestor-or-self::*[self::section or self::article or self::div][1]");
  }

  const textHeading = appPage.getByText(headingPattern).first();
  await expect(textHeading).toBeVisible();
  return textHeading.locator("xpath=ancestor-or-self::*[self::section or self::article or self::div][1]");
}

async function openLegalPageAndValidate({
  appPage,
  accountPageUrl,
  legalLinkPattern,
  legalHeadingPattern,
  screenshotName,
  testInfo,
}: {
  appPage: Page;
  accountPageUrl: string;
  legalLinkPattern: RegExp;
  legalHeadingPattern: RegExp;
  screenshotName: string;
  testInfo: TestInfo;
}): Promise<{ screenshotPath: string; finalUrl: string }> {
  const legalSection = await sectionByHeading(appPage, /secci[o\u00f3]n legal/i);
  const legalLink = await firstVisibleLocator([
    legalSection.getByRole("link", { name: legalLinkPattern }),
    legalSection.getByRole("button", { name: legalLinkPattern }),
    legalSection.getByText(legalLinkPattern),
  ]);

  const previousUrl = appPage.url();
  const popupPromise = appPage.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  await legalLink.click();

  const popup = await popupPromise;
  let legalPage = appPage;
  const openedInNewTab = Boolean(popup);

  if (popup) {
    legalPage = popup;
  } else if (appPage.url() === previousUrl) {
    await appPage.waitForURL((url) => url.toString() !== previousUrl, { timeout: 20000 }).catch(() => undefined);
  }

  await waitForUiToLoad(legalPage);

  const heading = await firstVisibleLocator([
    legalPage.getByRole("heading", { name: legalHeadingPattern }),
    legalPage.getByText(legalHeadingPattern),
  ]);
  await expect(heading).toBeVisible();

  const pageText = (await legalPage.locator("body").innerText()).trim();
  expect(pageText.length, "Expected legal content text to be visible").toBeGreaterThan(80);

  const screenshotPath = await checkpointScreenshot(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (openedInNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
  } else if (accountPageUrl && appPage.url() !== accountPageUrl) {
    await appPage.goto(accountPageUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToLoad(appPage);
  }

  return { screenshotPath, finalUrl };
}

async function checkpointScreenshot(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<string> {
  const filePath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return path.relative(process.cwd(), filePath);
}

function slugify(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)+/g, "");
}
