import { expect, type Page, type TestInfo, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type ValidationKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const reportTemplate: Record<ValidationKey, StepStatus> = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Informacion General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Terminos y Condiciones": "FAIL",
  "Politica de Privacidad": "FAIL"
};

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<void> {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function clickVisibleText(page: Page, candidates: RegExp[]): Promise<void> {
  for (const pattern of candidates) {
    const byRole = page.getByRole("button", { name: pattern }).first();
    if (await byRole.isVisible().catch(() => false)) {
      await byRole.click();
      await waitForUi(page);
      return;
    }

    const byText = page.getByText(pattern).first();
    if (await byText.isVisible().catch(() => false)) {
      await byText.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`No visible element matched any selector: ${candidates.map(String).join(", ")}`);
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const administrarItem = page.getByText(/Administrar Negocios/i).first();
  if (await administrarItem.isVisible().catch(() => false)) {
    return;
  }

  await clickVisibleText(page, [/Mi Negocio/i]);
}

async function runLegalValidation(
  page: Page,
  testInfo: TestInfo,
  linkPattern: RegExp,
  headingPattern: RegExp,
  evidenceName: string
): Promise<string> {
  const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
  await clickVisibleText(page, [linkPattern]);
  const popup = await popupPromise;
  const legalPage = popup ?? page;

  await waitForUi(legalPage);
  await expect(legalPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible({
    timeout: 20_000
  });
  await expect(legalPage.locator("p, li").first()).toBeVisible({ timeout: 20_000 });

  await captureCheckpoint(legalPage, testInfo, evidenceName, true);
  const finalUrl = legalPage.url();
  testInfo.annotations.push({ type: `${evidenceName}-url`, description: finalUrl });

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack().catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const stepReport: Record<ValidationKey, StepStatus> = { ...reportTemplate };
  const failures: string[] = [];
  const legalUrls: Partial<Record<ValidationKey, string>> = {};

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL). The test is environment-agnostic and does not hardcode domains."
    );
  }

  const runStep = async (title: string, key: ValidationKey, action: () => Promise<void>) => {
    try {
      await test.step(title, action);
      stepReport[key] = "PASS";
    } catch (error) {
      stepReport[key] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      failures.push(`${title}: ${message}`);
    }
  };

  await runStep("1. Login with Google", "Login", async () => {
    const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
    await clickVisibleText(page, [
      /Sign in with Google/i,
      /Iniciar sesi[oó]n con Google/i,
      /Continuar con Google/i,
      /^Google$/i
    ]);
    const popup = await popupPromise;
    const authPage = popup ?? page;

    await waitForUi(authPage);
    const accountOption = authPage.getByText(ACCOUNT_EMAIL).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 90_000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 60_000 });
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded", true);
  });

  await runStep("2. Open Mi Negocio menu", "Mi Negocio menu", async () => {
    await clickVisibleText(page, [/Negocio/i]);
    await clickVisibleText(page, [/Mi Negocio/i]);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded");
  });

  await runStep("3. Validate Agregar Negocio modal", "Agregar Negocio modal", async () => {
    await clickVisibleText(page, [/Agregar Negocio/i]);
    const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
    await expect(modal).toBeVisible({ timeout: 20_000 });
    await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    const nameInput = modal.getByLabel(/Nombre del Negocio/i).first();
    if (await nameInput.isVisible().catch(() => false)) {
      await nameInput.fill("Negocio Prueba Automatizacion");
    } else {
      await modal.getByPlaceholder(/Nombre del Negocio/i).first().fill("Negocio Prueba Automatizacion");
    }

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");
    await modal.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUi(page);
  });

  await runStep("4. Open Administrar Negocios", "Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);
    await clickVisibleText(page, [/Administrar Negocios/i]);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 20_000 });
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page", true);
  });

  await runStep("5. Validate Informacion General", "Informacion General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/@/).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.locator("h1, h2, h3, strong").filter({ hasText: /[A-Za-z]{2,}/ }).first()).toBeVisible({
      timeout: 20_000
    });
  });

  await runStep("6. Validate Detalles de la Cuenta", "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await runStep("7. Validate Tus Negocios", "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await runStep("8. Validate Terminos y Condiciones", "Terminos y Condiciones", async () => {
    legalUrls["Terminos y Condiciones"] = await runLegalValidation(
      page,
      testInfo,
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "08-terminos-condiciones"
    );
  });

  await runStep("9. Validate Politica de Privacidad", "Politica de Privacidad", async () => {
    legalUrls["Politica de Privacidad"] = await runLegalValidation(
      page,
      testInfo,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "09-politica-privacidad"
    );
  });

  const orderedSummary = {
    Login: stepReport.Login,
    "Mi Negocio menu": stepReport["Mi Negocio menu"],
    "Agregar Negocio modal": stepReport["Agregar Negocio modal"],
    "Administrar Negocios view": stepReport["Administrar Negocios view"],
    "Informacion General": stepReport["Informacion General"],
    "Detalles de la Cuenta": stepReport["Detalles de la Cuenta"],
    "Tus Negocios": stepReport["Tus Negocios"],
    "Terminos y Condiciones": stepReport["Terminos y Condiciones"],
    "Politica de Privacidad": stepReport["Politica de Privacidad"]
  };

  console.log("Final validation report:", JSON.stringify(orderedSummary, null, 2));
  if (legalUrls["Terminos y Condiciones"]) {
    console.log("Terminos y Condiciones URL:", legalUrls["Terminos y Condiciones"]);
  }
  if (legalUrls["Politica de Privacidad"]) {
    console.log("Politica de Privacidad URL:", legalUrls["Politica de Privacidad"]);
  }

  if (failures.length > 0) {
    throw new Error(`One or more workflow validations failed:\n${failures.join("\n")}`);
  }
});
