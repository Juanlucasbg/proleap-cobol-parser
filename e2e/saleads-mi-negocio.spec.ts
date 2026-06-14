import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type StepReport = {
  status: StepStatus;
  details?: string;
  finalUrl?: string;
};

type WorkflowReport = Record<string, StepReport>;

const GOOGLE_ACCOUNT_EMAIL = process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";

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

function emptyReport(): WorkflowReport {
  return REPORT_FIELDS.reduce<WorkflowReport>((acc, key) => {
    acc[key] = { status: "FAIL", details: "Not executed" };
    return acc;
  }, {});
}

function asErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(350);
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => undefined);
}

function textCandidates(page: Page, pattern: RegExp): Locator[] {
  return [
    page.getByRole("button", { name: pattern }).first(),
    page.getByRole("link", { name: pattern }).first(),
    page.getByRole("menuitem", { name: pattern }).first(),
    page.getByRole("tab", { name: pattern }).first(),
    page.getByText(pattern).first()
  ];
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    try {
      await candidate.waitFor({ state: "visible", timeout: 1500 });
      return candidate;
    } catch {
      // Try next candidate.
    }
  }
  return null;
}

async function clickByVisibleText(page: Page, patterns: RegExp[], label: string): Promise<void> {
  for (const pattern of patterns) {
    const locator = await firstVisible(textCandidates(page, pattern));
    if (locator) {
      await locator.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Could not find clickable element for: ${label}`);
}

async function checkpoint(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage
  });
}

async function runStep(
  report: WorkflowReport,
  stepName: keyof WorkflowReport,
  action: () => Promise<Partial<StepReport> | void>
): Promise<void> {
  try {
    const extra = (await action()) ?? {};
    report[stepName] = {
      status: "PASS",
      ...extra
    };
  } catch (error) {
    report[stepName] = {
      status: "FAIL",
      details: asErrorMessage(error)
    };
  }
}

async function openMiNegocioMenu(page: Page): Promise<void> {
  const miNegocioPattern = /mi negocio/i;
  const negocioPattern = /^negocio$/i;

  const directMiNegocio = await firstVisible(textCandidates(page, miNegocioPattern));
  if (directMiNegocio) {
    await directMiNegocio.click();
    await waitForUi(page);
  } else {
    await clickByVisibleText(page, [negocioPattern], "Negocio");
    await clickByVisibleText(page, [miNegocioPattern], "Mi Negocio");
  }

  await expect(page.getByText(/agregar negocio/i)).toBeVisible();
  await expect(page.getByText(/administrar negocios/i)).toBeVisible();
}

async function validateLegalPage(
  page: Page,
  testInfo: TestInfo,
  linkText: string,
  headingPattern: RegExp,
  screenshotName: string
): Promise<string> {
  const originUrl = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickByVisibleText(page, [new RegExp(escapeRegex(linkText), "i")], linkText);

  const popup = await popupPromise;
  const legalPage = popup ?? page;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
  }

  await waitForUi(legalPage);
  await expect(legalPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible();
  await expect(legalPage.locator("main, article, body").first()).toContainText(/[A-Za-zÁÉÍÓÚáéíóúÑñ]{10,}/);

  await checkpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== originUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = emptyReport();
  const loginUrl = process.env.SALEADS_LOGIN_URL;

  if (page.url() === "about:blank") {
    if (!loginUrl) {
      throw new Error(
        "Set SALEADS_LOGIN_URL with the current environment login page URL before running this test."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await runStep(report, "Login", async () => {
    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);

    await clickByVisibleText(
      page,
      [/sign in with google/i, /inicia(r)? con google/i, /continuar con google/i, /^google$/i],
      "Login with Google"
    );

    const popup = await popupPromise;
    const accountPattern = new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i");

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const popupAccount = await firstVisible(textCandidates(popup, accountPattern));
      if (popupAccount) {
        await popupAccount.click();
        await waitForUi(popup);
      }
      await popup.waitForEvent("close", { timeout: 30000 }).catch(() => undefined);
    } else {
      const samePageAccount = await firstVisible(textCandidates(page, accountPattern));
      if (samePageAccount) {
        await samePageAccount.click();
        await waitForUi(page);
      }
    }

    await page.bringToFront();
    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({ timeout: 30000 });

    await checkpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep(report, "Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await openMiNegocioMenu(page);
    await checkpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, [/agregar negocio/i], "Agregar Negocio");

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await checkpoint(page, testInfo, "03-agregar-negocio-modal.png");

    await page.getByLabel(/nombre del negocio/i).click();
    await page.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatización");
    await page.getByRole("button", { name: /cancelar/i }).click();
    await waitForUi(page);
  });

  await runStep(report, "Administrar Negocios view", async () => {
    await openMiNegocioMenu(page);
    await clickByVisibleText(page, [/administrar negocios/i], "Administrar Negocios");

    await expect(page.getByText(/información general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/sección legal/i)).toBeVisible();

    await checkpoint(page, testInfo, "04-administrar-negocios-page.png", true);
  });

  await runStep(report, "Información General", async () => {
    await expect(page.getByText(/información general/i)).toBeVisible();
    await expect(page.getByText(/nombre|usuario|name/i).first()).toBeVisible();
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expect(page.getByText(/business plan/i)).toBeVisible();

    const planChangeButton = await firstVisible([
      page.getByRole("button", { name: /cambiar plan/i }).first(),
      page.getByRole("link", { name: /cambiar plan/i }).first(),
      page.getByText(/cambiar plan/i).first()
    ]);

    if (!planChangeButton) {
      throw new Error("Could not find 'Cambiar Plan' control.");
    }
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const addBusinessControl = await firstVisible([
      page.getByRole("button", { name: /agregar negocio/i }).first(),
      page.getByRole("link", { name: /agregar negocio/i }).first(),
      page.getByText(/agregar negocio/i).first()
    ]);

    if (!addBusinessControl) {
      throw new Error("Could not find 'Agregar Negocio' in 'Tus Negocios' section.");
    }

    const listLikeElements = page.locator(
      "section:has-text('Tus Negocios') li, section:has-text('Tus Negocios') article, section:has-text('Tus Negocios') tbody tr, section:has-text('Tus Negocios') [role='listitem']"
    );

    const listCount = await listLikeElements.count();
    if (listCount === 0) {
      await expect(page.getByText(/negocio/i).first()).toBeVisible();
    } else {
      await expect(listLikeElements.first()).toBeVisible();
    }
  });

  await runStep(report, "Términos y Condiciones", async () => {
    const finalUrl = await validateLegalPage(
      page,
      testInfo,
      "Términos y Condiciones",
      /términos y condiciones/i,
      "05-terminos-y-condiciones.png"
    );

    return { finalUrl };
  });

  await runStep(report, "Política de Privacidad", async () => {
    const finalUrl = await validateLegalPage(
      page,
      testInfo,
      "Política de Privacidad",
      /política de privacidad/i,
      "06-politica-de-privacidad.png"
    );

    return { finalUrl };
  });

  const finalReportJson = JSON.stringify(report, null, 2);
  console.log("saleads_mi_negocio_full_test final report:\n" + finalReportJson);

  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: Buffer.from(finalReportJson),
    contentType: "application/json"
  });

  const failedSteps = Object.entries(report)
    .filter(([, result]) => result.status === "FAIL")
    .map(([stepName]) => stepName);

  expect(
    failedSteps,
    `The following workflow validations failed: ${failedSteps.join(", ") || "none"}`
  ).toEqual([]);
});
