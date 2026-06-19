import fs from "node:fs/promises";
import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

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
  "Política de Privacidad"
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type ResultStatus = "PASS" | "FAIL";
type StepResult = Record<ReportField, { status: ResultStatus; details: string }>;

function buildInitialResults(): StepResult {
  return REPORT_FIELDS.reduce((acc, key) => {
    acc[key] = { status: "FAIL", details: "Not executed" };
    return acc;
  }, {} as StepResult);
}

function asErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded();
  await locator.click();
  await waitForUi(page);
}

async function waitForAnyVisible(
  page: Page,
  candidates: Locator[],
  timeoutMs: number,
  label: string
): Promise<Locator> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const candidate of candidates) {
      const current = candidate.first();
      if (await current.isVisible().catch(() => false)) {
        return current;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`Timed out waiting for visible element: ${label}`);
}

async function getByVisibleText(page: Page, textPattern: RegExp, label: string): Promise<Locator> {
  return waitForAnyVisible(
    page,
    [
      page.getByRole("button", { name: textPattern }),
      page.getByRole("link", { name: textPattern }),
      page.getByRole("menuitem", { name: textPattern }),
      page.getByRole("tab", { name: textPattern }),
      page.getByText(textPattern)
    ],
    30_000,
    label
  );
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  filename: string,
  fullPage = true
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(filename),
    fullPage
  });
}

async function runStep(
  results: StepResult,
  key: ReportField,
  action: () => Promise<string | void>
): Promise<void> {
  try {
    const details = await action();
    results[key] = {
      status: "PASS",
      details: details ?? "Validated successfully"
    };
  } catch (error) {
    results[key] = {
      status: "FAIL",
      details: asErrorMessage(error)
    };
  }
}

async function finalizeReport(results: StepResult, testInfo: TestInfo): Promise<void> {
  const reportRows = REPORT_FIELDS.map((field) => ({
    field,
    status: results[field].status,
    details: results[field].details
  }));

  const reportPayload = {
    workflow: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: reportRows
  };

  await fs.writeFile(
    testInfo.outputPath("saleads-mi-negocio-report.json"),
    JSON.stringify(reportPayload, null, 2),
    "utf-8"
  );

  console.log("SaleADS Mi Negocio workflow final report:");
  for (const row of reportRows) {
    console.log(`${row.field}: ${row.status} - ${row.details}`);
  }

  const failedRows = reportRows.filter((row) => row.status === "FAIL");
  expect(
    failedRows,
    `Some workflow validations failed:\n${failedRows
      .map((row) => `- ${row.field}: ${row.details}`)
      .join("\n")}`
  ).toEqual([]);
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const results = buildInitialResults();

  await runStep(results, "Login", async () => {
    if (process.env.SALEADS_URL) {
      await page.goto(process.env.SALEADS_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No login page is open. Set SALEADS_URL for the target environment (dev/staging/prod)."
      );
    }

    const loginButton = await getByVisibleText(
      page,
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      "Google login button"
    );

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, loginButton);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountChoice = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountChoice.isVisible().catch(() => false)) {
        await accountChoice.click();
        await popup.waitForLoadState("domcontentloaded").catch(() => {});
      }
      await popup.waitForEvent("close", { timeout: 60_000 }).catch(() => {});
      await page.bringToFront();
    } else {
      const accountChoice = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountChoice.isVisible().catch(() => false)) {
        await accountChoice.click();
        await waitForUi(page);
      }
    }

    await expect(page.locator("main, [role='main']").first()).toBeVisible({ timeout: 60_000 });
    const sidebar = page.locator("aside, nav").filter({ hasText: /negocio|mi negocio/i }).first();
    await expect(sidebar).toBeVisible({ timeout: 60_000 });
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png");
  });

  if (results["Login"].status === "FAIL") {
    for (const field of REPORT_FIELDS) {
      if (field === "Login") {
        continue;
      }
      results[field] = {
        status: "FAIL",
        details: "Blocked because login did not complete."
      };
    }
    await finalizeReport(results, testInfo);
    return;
  }

  await runStep(results, "Mi Negocio menu", async () => {
    const negocioEntry = await getByVisibleText(page, /^negocio$/i, "Negocio menu");
    await clickAndWait(page, negocioEntry);

    const miNegocioEntry = await getByVisibleText(page, /^mi negocio$/i, "Mi Negocio menu");
    await clickAndWait(page, miNegocioEntry);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png");
  });

  await runStep(results, "Agregar Negocio modal", async () => {
    const agregarNegocioEntry = await getByVisibleText(
      page,
      /^agregar negocio$/i,
      "Agregar Negocio menu entry"
    );
    await clickAndWait(page, agregarNegocioEntry);

    const modal = page.getByRole("dialog").first();
    await expect(modal).toBeVisible({ timeout: 20_000 });
    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();

    let negocioNameInput = modal.getByLabel(/nombre del negocio/i).first();
    if (!(await negocioNameInput.isVisible().catch(() => false))) {
      negocioNameInput = modal.getByPlaceholder(/nombre del negocio/i).first();
    }
    await expect(negocioNameInput).toBeVisible();
    await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await negocioNameInput.click();
    await negocioNameInput.fill("Negocio Prueba Automatización");
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    await clickAndWait(page, modal.getByRole("button", { name: /cancelar/i }));
    await expect(modal).toBeHidden({ timeout: 10_000 });
  });

  await runStep(results, "Administrar Negocios view", async () => {
    const administrarLocator = page.getByText(/^administrar negocios$/i).first();
    if (!(await administrarLocator.isVisible().catch(() => false))) {
      const miNegocioEntry = await getByVisibleText(page, /^mi negocio$/i, "Mi Negocio menu");
      await clickAndWait(page, miNegocioEntry);
    }

    await clickAndWait(page, await getByVisibleText(page, /^administrar negocios$/i, "Administrar Negocios"));
    await expect(page.getByText(/información general/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/sección legal/i)).toBeVisible({ timeout: 30_000 });
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page.png");
  });

  await runStep(results, "Información General", async () => {
    const infoSection = page.locator("section, div").filter({ hasText: /información general/i }).first();
    await expect(infoSection).toBeVisible({ timeout: 20_000 });

    const infoText = await infoSection.innerText();
    const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoText);
    expect(hasEmail).toBeTruthy();

    const candidateNameLine = infoText
      .split("\n")
      .map((line) => line.trim())
      .find(
        (line) =>
          line.length >= 3 &&
          /[A-Za-zÁÉÍÓÚÑáéíóúñ]/.test(line) &&
          !/@/.test(line) &&
          !/información general|business plan|cambiar plan|detalles de la cuenta|tus negocios|sección legal/i.test(
            line
          )
      );

    expect(candidateNameLine).toBeTruthy();
    await expect(infoSection.getByText(/business plan/i)).toBeVisible();
    await expect(infoSection.getByText(/cambiar plan/i)).toBeVisible();
  });

  await runStep(results, "Detalles de la Cuenta", async () => {
    const detailsSection = page.locator("section, div").filter({ hasText: /detalles de la cuenta/i }).first();
    await expect(detailsSection).toBeVisible({ timeout: 20_000 });
    await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep(results, "Tus Negocios", async () => {
    const businessSection = page.locator("section, div").filter({ hasText: /tus negocios/i }).first();
    await expect(businessSection).toBeVisible({ timeout: 20_000 });
    await expect(businessSection.getByText(/agregar negocio/i)).toBeVisible();
    await expect(businessSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const textContent = await businessSection.innerText();
    const hasBusinessListContent =
      textContent
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean)
        .filter(
          (line) =>
            !/tus negocios|agregar negocio|tienes\s*2\s*de\s*3\s*negocios/i.test(line)
        ).length > 0;
    expect(hasBusinessListContent).toBeTruthy();
  });

  await runStep(results, "Términos y Condiciones", async () => {
    const termsLink = await getByVisibleText(
      page,
      /términos y condiciones|terminos y condiciones/i,
      "Términos y Condiciones link"
    );

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    const navPromise = page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 12_000 }).catch(() => null);
    await clickAndWait(page, termsLink);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
    } else {
      await navPromise;
    }

    await getByVisibleText(
      legalPage,
      /términos y condiciones|terminos y condiciones/i,
      "Términos y Condiciones heading"
    );

    const legalText = (await legalPage.locator("body").innerText()).trim();
    expect(legalText.length).toBeGreaterThan(200);
    await captureCheckpoint(legalPage, testInfo, "05-terminos-y-condiciones.png");

    const finalUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }

    return `Validated legal page at ${finalUrl}`;
  });

  await runStep(results, "Política de Privacidad", async () => {
    const privacyLink = await getByVisibleText(
      page,
      /política de privacidad|politica de privacidad/i,
      "Política de Privacidad link"
    );

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    const navPromise = page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 12_000 }).catch(() => null);
    await clickAndWait(page, privacyLink);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
    } else {
      await navPromise;
    }

    await getByVisibleText(
      legalPage,
      /política de privacidad|politica de privacidad/i,
      "Política de Privacidad heading"
    );

    const legalText = (await legalPage.locator("body").innerText()).trim();
    expect(legalText.length).toBeGreaterThan(200);
    await captureCheckpoint(legalPage, testInfo, "06-politica-de-privacidad.png");

    const finalUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }

    return `Validated legal page at ${finalUrl}`;
  });

  await finalizeReport(results, testInfo);
});
