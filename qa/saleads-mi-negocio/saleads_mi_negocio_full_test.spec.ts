import fs from "node:fs";
import path from "node:path";
import { expect, Locator, Page, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details: string;
  evidence: string[];
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
  "Política de Privacidad"
] as const;

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function createInitialReport(): Record<(typeof REPORT_FIELDS)[number], StepResult> {
  return REPORT_FIELDS.reduce(
    (acc, field) => ({
      ...acc,
      [field]: {
        status: "FAIL",
        details: "Step not executed.",
        evidence: []
      }
    }),
    {} as Record<(typeof REPORT_FIELDS)[number], StepResult>
  );
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function firstVisible(candidates: Locator[], timeoutMs = 15_000): Promise<Locator | null> {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    for (const candidate of candidates) {
      const node = candidate.first();
      if (await node.isVisible().catch(() => false)) {
        return node;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return null;
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.waitFor({ state: "visible", timeout: 20_000 });
  await locator.click();
  await waitForUiToSettle(page);
}

async function captureCheckpoint(
  page: Page,
  outputName: string,
  evidence: string[],
  fullPage = false
): Promise<void> {
  const screenshotPath = test.info().outputPath(outputName);
  await page.screenshot({ path: screenshotPath, fullPage });
  evidence.push(screenshotPath);
  await test.info().attach(outputName, { path: screenshotPath, contentType: "image/png" });
}

async function validateGoogleAccountSelection(googlePage: Page): Promise<void> {
  const accountOption = await firstVisible(
    [
      googlePage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      googlePage.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      googlePage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i"))
    ],
    10_000
  );

  if (accountOption) {
    await clickAndWait(googlePage, accountOption);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report = createInitialReport();

  async function runStep(name: (typeof REPORT_FIELDS)[number], work: () => Promise<void>): Promise<void> {
    try {
      await work();
      report[name].status = "PASS";
      if (report[name].details === "Step not executed.") {
        report[name].details = "All validations passed.";
      }
    } catch (error) {
      report[name].status = "FAIL";
      report[name].details = error instanceof Error ? error.message : String(error);
    }
  }

  const optionalStartUrl = process.env.SALEADS_START_URL;

  await runStep("Login", async () => {
    if (optionalStartUrl) {
      await page.goto(optionalStartUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No login page detected. Provide SALEADS_START_URL or pre-open the SaleADS login page."
      );
    }

    const loginButton = await firstVisible([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i)
    ]);

    if (!loginButton) {
      throw new Error("Could not locate login or 'Sign in with Google' control.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 12_000 }).catch(() => null);
    await loginButton.click();
    await waitForUiToSettle(page);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
      await validateGoogleAccountSelection(googlePopup);
      await googlePopup.waitForEvent("close", { timeout: 30_000 }).catch(() => undefined);
      await page.bringToFront();
    } else if (/accounts\.google\.com/i.test(page.url())) {
      await validateGoogleAccountSelection(page);
    }

    await waitForUiToSettle(page);
    const sidebar = await firstVisible([
      page.getByRole("navigation"),
      page.locator("aside"),
      page.getByText(/negocio/i)
    ], 30_000);

    if (!sidebar) {
      throw new Error("Main app interface did not load or left sidebar is not visible.");
    }

    await captureCheckpoint(page, "01-dashboard-loaded.png", report.Login.evidence);
  });

  await runStep("Mi Negocio menu", async () => {
    const miNegocioEntry = await firstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/^mi negocio$/i)
    ]);
    if (!miNegocioEntry) {
      throw new Error("Could not find 'Mi Negocio' in the sidebar.");
    }

    await clickAndWait(page, miNegocioEntry);

    const agregarNegocio = page.getByText(/^agregar negocio$/i).first();
    const administrarNegocios = page.getByText(/^administrar negocios$/i).first();

    await expect(agregarNegocio).toBeVisible({ timeout: 15_000 });
    await expect(administrarNegocios).toBeVisible({ timeout: 15_000 });

    await captureCheckpoint(page, "02-mi-negocio-expanded.png", report["Mi Negocio menu"].evidence);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocioAction = await firstVisible([
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i)
    ]);
    if (!agregarNegocioAction) {
      throw new Error("Could not find 'Agregar Negocio' action.");
    }

    await clickAndWait(page, agregarNegocioAction);

    const modalTitle = page.getByText(/^crear nuevo negocio$/i).first();
    await expect(modalTitle).toBeVisible({ timeout: 15_000 });
    await expect(page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i)).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^crear negocio$/i }).first()).toBeVisible();

    await captureCheckpoint(page, "03-agregar-negocio-modal.png", report["Agregar Negocio modal"].evidence);

    const nameInput = await firstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i)
    ]);
    if (nameInput) {
      await nameInput.fill("Negocio Prueba Automatización");
    }

    const cancelButton = page.getByRole("button", { name: /^cancelar$/i }).first();
    await clickAndWait(page, cancelButton);
    await expect(modalTitle).not.toBeVisible({ timeout: 10_000 });
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegocios = page.getByText(/^administrar negocios$/i).first();
    const menuVisible = await administrarNegocios.isVisible().catch(() => false);

    if (!menuVisible) {
      const miNegocioEntry = await firstVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/^mi negocio$/i)
      ]);
      if (!miNegocioEntry) {
        throw new Error("Could not re-open 'Mi Negocio' menu.");
      }
      await clickAndWait(page, miNegocioEntry);
    }

    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/^información general$/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/^detalles de la cuenta$/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/^tus negocios$/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/sección legal/i).first()).toBeVisible({ timeout: 20_000 });

    await captureCheckpoint(page, "04-administrar-negocios-view.png", report["Administrar Negocios view"].evidence, true);
  });

  await runStep("Información General", async () => {
    const bodyText = await page.locator("body").innerText();
    if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(bodyText)) {
      throw new Error("User email is not visible in account information.");
    }

    const nameLabelVisible = await page.getByText(/nombre|usuario/i).first().isVisible().catch(() => false);
    if (!nameLabelVisible) {
      throw new Error("User name indicator is not visible.");
    }

    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/^tus negocios$/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^agregar negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();

    const possibleBusinessItems = page.locator("article, [role='listitem'], li, .card");
    const hasBusinessItems = (await possibleBusinessItems.count().catch(() => 0)) > 0;
    if (!hasBusinessItems) {
      throw new Error("Business list items are not visible.");
    }
  });

  async function validateLegalLink(
    linkName: "Términos y Condiciones" | "Política de Privacidad",
    headingRegex: RegExp,
    screenshotName: string,
    reportField: "Términos y Condiciones" | "Política de Privacidad"
  ): Promise<void> {
    const link = await firstVisible([
      page.getByRole("link", { name: headingRegex }),
      page.getByRole("button", { name: headingRegex }),
      page.getByText(headingRegex)
    ]);
    if (!link) {
      throw new Error(`Could not find legal link '${linkName}'.`);
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await link.click();
    await waitForUiToSettle(page);

    const popup = await popupPromise;
    const legalPage = popup ?? page;

    await legalPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
    await expect(legalPage.getByText(headingRegex).first()).toBeVisible({ timeout: 20_000 });

    const legalText = (await legalPage.locator("body").innerText()).trim();
    if (legalText.length < 120) {
      throw new Error(`'${linkName}' content appears incomplete or too short.`);
    }

    await captureCheckpoint(legalPage, screenshotName, report[reportField].evidence, true);
    report[reportField].details = `Validated successfully. URL: ${legalPage.url()}`;

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUiToSettle(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUiToSettle(page);
    }
  }

  await runStep("Términos y Condiciones", async () => {
    await validateLegalLink(
      "Términos y Condiciones",
      /t[eé]rminos y condiciones/i,
      "05-terminos-y-condiciones.png",
      "Términos y Condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalLink(
      "Política de Privacidad",
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad.png",
      "Política de Privacidad"
    );
  });

  const reportPath = test.info().outputPath("saleads-mi-negocio-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf-8");
  await test.info().attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failed = Object.entries(report).filter(([, value]) => value.status === "FAIL");
  if (failed.length > 0) {
    const failures = failed.map(([key, value]) => `${key}: ${value.details}`).join("\n");
    throw new Error(`One or more workflow validations failed.\n${failures}`);
  }

  const summaryPath = path.resolve(path.dirname(reportPath), "saleads-mi-negocio-report-summary.txt");
  const summaryLines = REPORT_FIELDS.map((field) => `${field}: ${report[field].status}`);
  fs.writeFileSync(summaryPath, `${summaryLines.join("\n")}\n`, "utf-8");
  await test.info().attach("saleads-mi-negocio-summary", {
    path: summaryPath,
    contentType: "text/plain"
  });
});
