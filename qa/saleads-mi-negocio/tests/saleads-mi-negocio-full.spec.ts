import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import fs from "node:fs/promises";

type StepStatus = "PASS" | "FAIL";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

interface StepResult {
  status: StepStatus;
  details?: string;
}

interface WorkflowReport {
  runAtUtc: string;
  environmentUrl: string;
  legalUrls: {
    terminosYCondiciones?: string;
    politicaDePrivacidad?: string;
  };
  steps: Record<ReportField, StepResult>;
}

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS: ReportField[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad"
];

const WAIT_UI_SETTLE_MS = 500;

function createInitialStepReport(): Record<ReportField, StepResult> {
  const entries = REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed." }] as const);
  return Object.fromEntries(entries) as Record<ReportField, StepResult>;
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 8_000 });
  } catch {
    // Many SPAs never reach complete network idleness because of polling.
  }
  await page.waitForTimeout(WAIT_UI_SETTLE_MS);
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    if ((await locator.count()) === 0) {
      continue;
    }

    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }
  return null;
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToSettle(page);
}

async function takeScreenshot(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false
): Promise<void> {
  const imagePath = testInfo.outputPath(fileName);
  await page.screenshot({ path: imagePath, fullPage });
  await testInfo.attach(fileName, {
    path: imagePath,
    contentType: "image/png"
  });
}

async function findMiNegocioToggle(page: Page): Promise<Locator | null> {
  return firstVisible([
    page.getByRole("button", { name: /mi negocio/i }),
    page.getByRole("link", { name: /mi negocio/i }),
    page.getByText(/^mi negocio$/i)
  ]);
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
  if (administrarVisible) {
    return;
  }

  const menuToggle = await findMiNegocioToggle(page);
  if (!menuToggle) {
    throw new Error("Could not find the 'Mi Negocio' menu option.");
  }
  await clickAndWait(menuToggle, page);
}

async function maybeSelectGoogleAccount(googlePage: Page): Promise<boolean> {
  const accountLocator = await firstVisible([
    googlePage.getByText(ACCOUNT_EMAIL, { exact: true }),
    googlePage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
    googlePage.getByRole("link", { name: new RegExp(ACCOUNT_EMAIL, "i") })
  ]);

  if (!accountLocator) {
    return false;
  }

  await accountLocator.click();
  return true;
}

async function assertTextVisible(page: Page, textRegex: RegExp): Promise<void> {
  await expect(page.getByText(textRegex).first()).toBeVisible();
}

async function runStep(
  report: WorkflowReport,
  field: ReportField,
  stepAction: () => Promise<void>
): Promise<void> {
  try {
    await stepAction();
    report.steps[field] = { status: "PASS" };
  } catch (error) {
    report.steps[field] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error)
    };
  }
}

async function openLegalLinkAndValidate(
  page: Page,
  testInfo: TestInfo,
  linkText: RegExp,
  expectedHeading: RegExp,
  screenshotName: string
): Promise<{ finalUrl: string }> {
  const popupPromise = page.context().waitForEvent("page", { timeout: 6_000 }).catch(() => null);
  const link = await firstVisible([
    page.getByRole("link", { name: linkText }),
    page.getByText(linkText)
  ]);

  if (!link) {
    throw new Error(`Legal link not found: ${linkText}`);
  }

  await link.scrollIntoViewIfNeeded();
  await link.click();
  await waitForUiToSettle(page);

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await legalPage.waitForLoadState("domcontentloaded");

  const headingLocator = await firstVisible([
    legalPage.getByRole("heading", { name: expectedHeading }),
    legalPage.getByText(expectedHeading)
  ]);
  if (!headingLocator) {
    throw new Error(`Legal page heading not found: ${expectedHeading}`);
  }
  await expect(headingLocator).toBeVisible();

  const bodyTextLength = await legalPage
    .locator("main, article, body")
    .first()
    .innerText()
    .then((text) => text.trim().length)
    .catch(() => 0);

  if (bodyTextLength < 120) {
    throw new Error("Legal page content did not contain enough text.");
  }

  await takeScreenshot(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  }

  return { finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: WorkflowReport = {
    runAtUtc: new Date().toISOString(),
    environmentUrl: process.env.SALEADS_LOGIN_URL ?? "Detected at runtime",
    legalUrls: {},
    steps: createInitialStepReport()
  };

  if (process.env.SALEADS_LOGIN_URL) {
    await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
  }

  await runStep(report, "Login", async () => {
    const loginButton = await firstVisible([
      page.getByRole("button", { name: /sign in with google|login with google|continuar con google|ingresar con google/i }),
      page.getByRole("link", { name: /sign in with google|login with google|continuar con google|ingresar con google/i }),
      page.getByText(/sign in with google|login with google|continuar con google|ingresar con google/i)
    ]);

    if (!loginButton) {
      throw new Error("Could not find login button or 'Sign in with Google'.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await maybeSelectGoogleAccount(popup);
      await popup.waitForEvent("close", { timeout: 30_000 }).catch(() => undefined);
    } else {
      await maybeSelectGoogleAccount(page);
    }

    await waitForUiToSettle(page);
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
    await assertTextVisible(page, /negocio|mi negocio/i);
    report.environmentUrl = page.url();
    await takeScreenshot(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep(report, "Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await assertTextVisible(page, /negocio/i);

    const miNegocioToggle = await findMiNegocioToggle(page);
    if (!miNegocioToggle) {
      throw new Error("Could not find 'Mi Negocio' in the left sidebar.");
    }

    await clickAndWait(miNegocioToggle, page);
    await assertTextVisible(page, /agregar negocio/i);
    await assertTextVisible(page, /administrar negocios/i);
    await takeScreenshot(page, testInfo, "02-mi-negocio-expanded.png");
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    await ensureMiNegocioExpanded(page);

    const agregarNegocio = await firstVisible([
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    if (!agregarNegocio) {
      throw new Error("Could not find 'Agregar Negocio'.");
    }

    await clickAndWait(agregarNegocio, page);

    const modal = page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
    await expect(modal).toBeVisible({ timeout: 15_000 });
    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();

    let businessNameInput = modal.getByLabel(/nombre del negocio/i);
    if ((await businessNameInput.count()) === 0) {
      businessNameInput = modal.getByPlaceholder(/nombre del negocio/i);
    }
    if ((await businessNameInput.count()) === 0) {
      businessNameInput = modal.locator("input").first();
    }

    await expect(businessNameInput).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await takeScreenshot(page, testInfo, "03-agregar-negocio-modal.png");
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(modal.getByRole("button", { name: /cancelar/i }), page);
  });

  await runStep(report, "Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);

    const administrarNegocios = await firstVisible([
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    if (!administrarNegocios) {
      throw new Error("Could not find 'Administrar Negocios'.");
    }

    await clickAndWait(administrarNegocios, page);
    await assertTextVisible(page, /informacion general|informaci[oó]n general/i);
    await assertTextVisible(page, /detalles de la cuenta/i);
    await assertTextVisible(page, /tus negocios/i);
    await assertTextVisible(page, /seccion legal|secci[oó]n legal/i);
    await takeScreenshot(page, testInfo, "04-administrar-negocios-view.png", true);
  });

  await runStep(report, "Informacion General", async () => {
    const infoGeneralSection = page
      .locator("section,div,article")
      .filter({ has: page.getByText(/informacion general|informaci[oó]n general/i).first() })
      .first();
    await expect(infoGeneralSection).toBeVisible();

    await assertTextVisible(page, /business plan/i);
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();

    const hasLikelyName = await infoGeneralSection.evaluate((section) => {
      const texts = Array.from(section.querySelectorAll("*"))
        .map((element) => (element.textContent ?? "").trim())
        .filter(Boolean);

      return texts.some((line) => {
        if (line.length < 3 || line.length > 60) {
          return false;
        }
        if (line.includes("@")) {
          return false;
        }
        if (/(business plan|cambiar plan|informacion general|información general)/i.test(line)) {
          return false;
        }
        return /^[A-Za-z]+(?:\s+[A-Za-z]+){1,3}$/.test(line);
      });
    });

    if (!hasLikelyName) {
      throw new Error("Could not confirm that a user name is visible in 'Informacion General'.");
    }
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await assertTextVisible(page, /cuenta creada/i);
    await assertTextVisible(page, /estado activo/i);
    await assertTextVisible(page, /idioma seleccionado/i);
  });

  await runStep(report, "Tus Negocios", async () => {
    const tusNegociosSection = page
      .locator("section,div,article")
      .filter({ has: page.getByText(/tus negocios/i).first() })
      .first();
    await expect(tusNegociosSection).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();

    const itemCount = await tusNegociosSection.locator("li, tr, [class*='business'], [data-testid*='business']").count();
    if (itemCount < 1) {
      throw new Error("Business list appears empty.");
    }
  });

  await runStep(report, "Terminos y Condiciones", async () => {
    const legalResult = await openLegalLinkAndValidate(
      page,
      testInfo,
      /terminos y condiciones|t[ée]rminos y condiciones/i,
      /terminos y condiciones|t[ée]rminos y condiciones/i,
      "05-terminos-y-condiciones.png"
    );
    report.legalUrls.terminosYCondiciones = legalResult.finalUrl;
  });

  await runStep(report, "Politica de Privacidad", async () => {
    const legalResult = await openLegalLinkAndValidate(
      page,
      testInfo,
      /politica de privacidad|pol[ií]tica de privacidad/i,
      /politica de privacidad|pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad.png"
    );
    report.legalUrls.politicaDePrivacidad = legalResult.finalUrl;
  });

  const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf-8");
  await testInfo.attach("saleads-mi-negocio-report.json", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedSteps = Object.entries(report.steps).filter(([, result]) => result.status === "FAIL");
  expect(
    failedSteps,
    `One or more workflow validations failed.\n${JSON.stringify(report, null, 2)}`
  ).toHaveLength(0);
});
