import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

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

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }
  return null;
}

function textCandidates(page: Page, text: RegExp): Locator[] {
  return [
    page.getByRole("button", { name: text }),
    page.getByRole("link", { name: text }),
    page.getByRole("menuitem", { name: text }),
    page.getByRole("tab", { name: text }),
    page.getByRole("option", { name: text }),
    page.getByRole("heading", { name: text }),
    page.getByText(text),
  ];
}

async function clickByVisibleText(page: Page, text: RegExp): Promise<void> {
  for (const candidate of textCandidates(page, text)) {
    const locator = candidate.first();
    try {
      await expect(locator).toBeVisible({ timeout: 5_000 });
      await locator.scrollIntoViewIfNeeded().catch(() => undefined);
      await locator.click();
      await waitForUiToLoad(page);
      return;
    } catch {
      // Try the next candidate role/text locator.
    }
  }

  throw new Error(`No visible element found for: ${text}`);
}

async function expectVisibleText(page: Page, text: RegExp, reason: string): Promise<void> {
  for (const candidate of textCandidates(page, text)) {
    const locator = candidate.first();
    try {
      await expect(locator, reason).toBeVisible({ timeout: 5_000 });
      return;
    } catch {
      // Try the next candidate role/text locator.
    }
  }

  throw new Error(`${reason} Missing text pattern: ${text}`);
}

async function captureEvidence(page: Page, testInfo: TestInfo, fileName: string, fullPage = false): Promise<void> {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, { path: screenshotPath, contentType: "image/png" });
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
  const report: Record<ReportField, "PASS" | "FAIL"> = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };
  const failures: string[] = [];
  const urls: Record<string, string> = {};

  async function runStep(field: ReportField, action: () => Promise<void>): Promise<void> {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[field] = "FAIL";
      failures.push(`${field}: ${message}`);
    }
  }

  await runStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_LOGIN_URL to the current environment login page (no hardcoded domain in test code)."
      );
    }

    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickByVisibleText(page, /sign in with google|iniciar sesi[oó]n con google|google/i);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await waitForUiToLoad(popup);

      const accountLocator = await firstVisible(textCandidates(popup, /juanlucasbarbiergarzon@gmail\.com/i));
      if (accountLocator) {
        await accountLocator.click();
        await waitForUiToLoad(popup);
      }

      await popup.waitForEvent("close", { timeout: 45_000 }).catch(() => undefined);
      await page.bringToFront();
      await waitForUiToLoad(page);
    } else {
      const accountLocator = await firstVisible(textCandidates(page, /juanlucasbarbiergarzon@gmail\.com/i));
      if (accountLocator) {
        await accountLocator.click();
        await waitForUiToLoad(page);
      }
    }

    await expectVisibleText(page, /negocio/i, "Left sidebar with navigation should be visible.");
    await captureEvidence(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await expectVisibleText(page, /negocio/i, "Sidebar should contain 'Negocio'.");

    const miNegocioBefore = await firstVisible(textCandidates(page, /mi negocio/i));
    if (!miNegocioBefore) {
      await clickByVisibleText(page, /negocio/i);
    }

    await clickByVisibleText(page, /mi negocio/i);
    await expectVisibleText(page, /agregar negocio/i, "'Agregar Negocio' should be visible.");
    await expectVisibleText(page, /administrar negocios/i, "'Administrar Negocios' should be visible.");

    await captureEvidence(page, testInfo, "02-mi-negocio-expanded.png", true);
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /agregar negocio/i);
    await expectVisibleText(page, /crear nuevo negocio/i, "Modal title should be visible.");

    let businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    if (!(await businessNameInput.isVisible().catch(() => false))) {
      businessNameInput = page.getByPlaceholder(/nombre del negocio/i).first();
    }
    await expect(businessNameInput).toBeVisible();

    await expectVisibleText(page, /tienes\s+2\s+de\s+3\s+negocios/i, "Business quota text should be visible.");
    await expectVisibleText(page, /cancelar/i, "Cancelar button should be present.");
    await expectVisibleText(page, /crear negocio/i, "Crear Negocio button should be present.");
    await captureEvidence(page, testInfo, "03-agregar-negocio-modal.png", true);

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickByVisibleText(page, /cancelar/i);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegocios = await firstVisible(textCandidates(page, /administrar negocios/i));
    if (!administrarNegocios) {
      await clickByVisibleText(page, /mi negocio/i);
    }

    await clickByVisibleText(page, /administrar negocios/i);

    await expectVisibleText(page, /informaci[oó]n general/i, "Informacion General section should exist.");
    await expectVisibleText(page, /detalles de la cuenta/i, "Detalles de la Cuenta section should exist.");
    await expectVisibleText(page, /tus negocios/i, "Tus Negocios section should exist.");
    await expectVisibleText(page, /secci[oó]n legal/i, "Seccion Legal section should exist.");
    await captureEvidence(page, testInfo, "04-administrar-negocios-view.png", true);
  });

  await runStep("Información General", async () => {
    await expectVisibleText(page, /@/, "User email should be visible.");
    await expectVisibleText(page, /business plan/i, "BUSINESS PLAN should be visible.");
    await expectVisibleText(page, /cambiar plan/i, "Cambiar Plan should be visible.");

    const profileName = await firstVisible([
      page.locator("h1, h2, h3").filter({ hasNotText: /informaci[oó]n general|detalles de la cuenta|tus negocios/i }),
      page.getByText(/^[a-z]+(?:\s+[a-z]+)+$/i),
    ]);
    expect(profileName, "User name should be visible in Informacion General.").not.toBeNull();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expectVisibleText(page, /cuenta creada/i, "'Cuenta creada' should be visible.");
    await expectVisibleText(page, /estado activo/i, "'Estado activo' should be visible.");
    await expectVisibleText(page, /idioma seleccionado/i, "'Idioma seleccionado' should be visible.");
  });

  await runStep("Tus Negocios", async () => {
    await expectVisibleText(page, /tus negocios/i, "Business list section should be visible.");
    await expectVisibleText(page, /agregar negocio/i, "Agregar Negocio button should be visible.");
    await expectVisibleText(page, /tienes\s+2\s+de\s+3\s+negocios/i, "Business quota text should be visible.");
  });

  await runStep("Términos y Condiciones", async () => {
    const appUrlBefore = page.url();
    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);

    await clickByVisibleText(page, /t[eé]rminos y condiciones/i);
    const popup = await popupPromise;
    const legalPage = popup ?? page;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await waitForUiToLoad(popup);
    }

    await expectVisibleText(
      legalPage,
      /t[eé]rminos y condiciones/i,
      "Legal page heading 'Términos y Condiciones' should be visible."
    );
    await expect(firstVisible([legalPage.locator("p"), legalPage.locator("li")])).resolves.not.toBeNull();

    urls["Términos y Condiciones"] = legalPage.url();
    await captureEvidence(legalPage, testInfo, "05-terminos-y-condiciones.png", true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUiToLoad(page);
    } else if (page.url() !== appUrlBefore) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUiToLoad(page);
    }
  });

  await runStep("Política de Privacidad", async () => {
    const appUrlBefore = page.url();
    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);

    await clickByVisibleText(page, /pol[ií]tica de privacidad/i);
    const popup = await popupPromise;
    const legalPage = popup ?? page;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await waitForUiToLoad(popup);
    }

    await expectVisibleText(
      legalPage,
      /pol[ií]tica de privacidad/i,
      "Legal page heading 'Política de Privacidad' should be visible."
    );
    await expect(firstVisible([legalPage.locator("p"), legalPage.locator("li")])).resolves.not.toBeNull();

    urls["Política de Privacidad"] = legalPage.url();
    await captureEvidence(legalPage, testInfo, "06-politica-de-privacidad.png", true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUiToLoad(page);
    } else if (page.url() !== appUrlBefore) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUiToLoad(page);
    }
  });

  await testInfo.attach("saleads-mi-negocio-report.json", {
    body: JSON.stringify({ report, legalUrls: urls, failures }, null, 2),
    contentType: "application/json",
  });

  expect(
    failures,
    `One or more validations failed. Final report:\n${JSON.stringify({ report, legalUrls: urls }, null, 2)}`
  ).toEqual([]);
});
