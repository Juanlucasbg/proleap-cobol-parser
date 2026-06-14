import { expect, Locator, Page, test, TestInfo } from "@playwright/test";
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

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details?: string;
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function buildDefaultReport(): Record<ReportField, StepResult> {
  return {
    Login: { status: "FAIL", details: "Step did not complete." },
    "Mi Negocio menu": { status: "FAIL", details: "Step did not complete." },
    "Agregar Negocio modal": { status: "FAIL", details: "Step did not complete." },
    "Administrar Negocios view": { status: "FAIL", details: "Step did not complete." },
    "Información General": { status: "FAIL", details: "Step did not complete." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Step did not complete." },
    "Tus Negocios": { status: "FAIL", details: "Step did not complete." },
    "Términos y Condiciones": { status: "FAIL", details: "Step did not complete." },
    "Política de Privacidad": { status: "FAIL", details: "Step did not complete." },
  };
}

async function waitForUiAfterClick(page: Page): Promise<void> {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 8_000 }),
    page.waitForTimeout(1_000),
  ]).catch(() => undefined);
  await page.waitForTimeout(400);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => undefined);
  await locator.click();
  await waitForUiAfterClick(page);
}

async function firstVisible(candidates: Locator[], timeoutMs = 12_000): Promise<Locator> {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      const isVisible = await locator.isVisible().catch(() => false);
      if (isVisible) {
        return locator;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error("None of the candidate locators became visible.");
}

async function checkpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false,
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function openLegalLinkAndValidate(options: {
  appPage: Page;
  linkText: string;
  expectedHeading: RegExp;
  screenshotName: string;
  testInfo: TestInfo;
}): Promise<string> {
  const { appPage, linkText, expectedHeading, screenshotName, testInfo } = options;
  const link = await firstVisible(
    [
      appPage.getByRole("link", { name: linkText }),
      appPage.getByText(linkText, { exact: true }),
      appPage.getByText(new RegExp(linkText, "i")),
    ],
    10_000,
  );

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickAndWait(appPage, link);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 });
    await expect(popup.getByRole("heading", { name: expectedHeading }).first()).toBeVisible({
      timeout: 15_000,
    });

    // Validate legal-content body text (not only heading).
    await expect(popup.locator("main, article, body").first()).toContainText(/\S+/, {
      timeout: 15_000,
    });
    await checkpoint(popup, testInfo, screenshotName, true);
    const finalUrl = popup.url();
    await popup.close().catch(() => undefined);
    await appPage.bringToFront();
    return finalUrl;
  }

  await appPage.waitForLoadState("domcontentloaded", { timeout: 20_000 });
  await expect(appPage.getByRole("heading", { name: expectedHeading }).first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(appPage.locator("main, article, body").first()).toContainText(/\S+/, {
    timeout: 15_000,
  });

  await checkpoint(appPage, testInfo, screenshotName, true);
  const finalUrl = appPage.url();
  await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
  await waitForUiAfterClick(appPage);
  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildDefaultReport();
  const legalUrls: { terminos?: string; privacidad?: string } = {};

  const markPass = (field: ReportField): void => {
    report[field] = { status: "PASS" };
  };

  const markFail = (field: ReportField, error: unknown): void => {
    const details = error instanceof Error ? error.message : String(error);
    report[field] = { status: "FAIL", details };
  };

  const runStep = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      markPass(field);
    } catch (error) {
      markFail(field, error);
    }
  };

  await runStep("Login", async () => {
    const baseUrl = process.env.SALEADS_BASE_URL?.trim();
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    } else if (page.url() === "about:blank") {
      throw new Error(
        "SALEADS_BASE_URL is required when Playwright starts from about:blank. Set the environment URL for the target environment.",
      );
    }

    const loginButton = await firstVisible(
      [
        page.getByRole("button", { name: /google|sign in|iniciar sesión|inicia sesión/i }),
        page.getByText(/google/i),
      ],
      20_000,
    );

    const googlePopupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const googlePopup = await googlePopupPromise;

    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 20_000 });
      const accountOption = googlePopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await googlePopup.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
      }
    } else {
      const samePageAccountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await samePageAccountOption.isVisible().catch(() => false)) {
        await clickAndWait(page, samePageAccountOption);
      }
    }

    await expect(
      await firstVisible([page.locator("aside"), page.getByRole("navigation")], 30_000),
    ).toBeVisible();
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await checkpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const sidebar = await firstVisible([page.locator("aside"), page.getByRole("navigation")], 15_000);
    const negocioSection = await firstVisible(
      [
        sidebar.getByText(/^Negocio$/i),
        sidebar.getByRole("button", { name: /Negocio/i }),
        page.getByText(/^Negocio$/i),
      ],
      10_000,
    );
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await firstVisible(
      [
        sidebar.getByRole("button", { name: /Mi Negocio/i }),
        sidebar.getByText(/Mi Negocio/i),
        page.getByText(/Mi Negocio/i),
      ],
      10_000,
    );
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 10_000 });
    await checkpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", false);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocioOption = await firstVisible(
      [page.getByText(/^Agregar Negocio$/i), page.getByRole("button", { name: /^Agregar Negocio$/i })],
      10_000,
    );
    await clickAndWait(page, agregarNegocioOption);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 15_000 });

    const nombreInput = await firstVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
      ],
      8_000,
    );

    await expect(nombreInput).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();
    await checkpoint(page, testInfo, "03-crear-nuevo-negocio-modal.png", false);

    await nombreInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /Cancelar/i }).first());
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocioOption = await firstVisible(
      [
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
        page.getByRole("link", { name: /Mi Negocio/i }),
      ],
      12_000,
    );
    await clickAndWait(page, miNegocioOption);

    const administrarNegocios = await firstVisible(
      [
        page.getByText(/Administrar Negocios/i),
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
      ],
      12_000,
    );
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 20_000 });
    await checkpoint(page, testInfo, "04-administrar-negocios-cuenta.png", true);
  });

  await runStep("Información General", async () => {
    const infoSection = await firstVisible(
      [
        page.locator("section").filter({ hasText: /Información General/i }),
        page.locator("div").filter({ hasText: /Información General/i }),
      ],
      10_000,
    );

    await expect(infoSection).toContainText(/\bBUSINESS PLAN\b/i);
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const infoText = await infoSection.innerText();
    const containsEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoText);
    if (!containsEmail) {
      throw new Error("Expected a visible user email in Información General.");
    }

    const normalizedLines = infoText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const likelyUserName = normalizedLines.find(
      (line) =>
        !line.includes("@") &&
        !/información general|business plan|cambiar plan|cuenta creada|estado activo|idioma seleccionado/i.test(
          line,
        ) &&
        line.length >= 3,
    );

    if (!likelyUserName) {
      throw new Error("Expected a visible user name in Información General.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = await firstVisible(
      [
        page.locator("section").filter({ hasText: /Detalles de la Cuenta/i }),
        page.locator("div").filter({ hasText: /Detalles de la Cuenta/i }),
      ],
      10_000,
    );

    await expect(detailsSection).toContainText(/Cuenta creada/i);
    await expect(detailsSection).toContainText(/Estado activo/i);
    await expect(detailsSection).toContainText(/Idioma seleccionado/i);
  });

  await runStep("Tus Negocios", async () => {
    const businessesSection = await firstVisible(
      [
        page.locator("section").filter({ hasText: /Tus Negocios/i }),
        page.locator("div").filter({ hasText: /Tus Negocios/i }),
      ],
      10_000,
    );

    await expect(businessesSection).toContainText(/Tus Negocios/i);
    await expect(businessesSection.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(businessesSection).toContainText(/Tienes 2 de 3 negocios/i);
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls.terminos = await openLegalLinkAndValidate({
      appPage: page,
      linkText: "Términos y Condiciones",
      expectedHeading: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo,
    });

    await expect(page.getByText(/Sección Legal|Información General|Tus Negocios/i).first()).toBeVisible({
      timeout: 15_000,
    });
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls.privacidad = await openLegalLinkAndValidate({
      appPage: page,
      linkText: "Política de Privacidad",
      expectedHeading: /Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo,
    });

    await expect(page.getByText(/Sección Legal|Información General|Tus Negocios/i).first()).toBeVisible({
      timeout: 15_000,
    });
  });

  const finalReportPath = testInfo.outputPath("mi-negocio-final-report.json");
  const finalPayload = {
    generatedAt: new Date().toISOString(),
    report,
    legalUrls,
  };
  await writeFile(finalReportPath, JSON.stringify(finalPayload, null, 2), "utf8");
  await testInfo.attach("mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  // Print report in logs for quick CI visibility.
  console.log(JSON.stringify(finalPayload, null, 2));

  const failedFields = Object.entries(report).filter(([, result]) => result.status === "FAIL");
  expect(
    failedFields,
    `Final report contains failed steps:\n${JSON.stringify(finalPayload, null, 2)}`,
  ).toEqual([]);
});
