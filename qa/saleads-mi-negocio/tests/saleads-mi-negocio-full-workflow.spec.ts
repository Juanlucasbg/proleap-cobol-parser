import { expect, type Locator, type Page, test, type TestInfo } from "@playwright/test";

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

const reportOrder: ReportField[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function isVisible(locator: Locator, timeout = 2500): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function findClickableByVisibleText(page: Page, text: string | RegExp): Promise<Locator> {
  const candidates: Locator[] = [
    page.getByRole("button", { name: text }).first(),
    page.getByRole("link", { name: text }).first(),
    page.getByRole("menuitem", { name: text }).first(),
    page.locator("[role='button']", { hasText: text }).first(),
    page.locator("button", { hasText: text }).first(),
    page.locator("a", { hasText: text }).first(),
    page.getByText(text).first()
  ];

  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate;
    }
  }

  throw new Error(`No visible clickable element found for: ${String(text)}`);
}

async function clickByVisibleText(page: Page, text: string | RegExp): Promise<void> {
  const locator = await findClickableByVisibleText(page, text);
  await locator.click();
  await waitForUiToLoad(page);
}

async function saveScreenshot(
  page: Page,
  testInfo: TestInfo,
  checkpointName: string,
  fullPage = false
): Promise<void> {
  const safe = checkpointName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
  const path = testInfo.outputPath(`checkpoint-${safe}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(checkpointName, { path, contentType: "image/png" });
}

async function chooseGoogleAccountIfPrompted(page: Page): Promise<void> {
  const account = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
  if (await isVisible(account, 7000)) {
    await account.click();
    await waitForUiToLoad(page);
  }
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarNegocio = page.getByText(/Agregar Negocio/i).first();
  const administrarNegocios = page.getByText(/Administrar Negocios/i).first();
  if ((await isVisible(agregarNegocio, 1500)) && (await isVisible(administrarNegocios, 1500))) {
    return;
  }

  await clickByVisibleText(page, /Mi Negocio/i);
}

async function validateLegalLink(
  page: Page,
  testInfo: TestInfo,
  linkText: string | RegExp,
  headingText: string | RegExp,
  screenshotName: string
): Promise<string> {
  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickByVisibleText(page, linkText);

  const popup = await popupPromise;
  const targetPage = popup ?? page;
  await waitForUiToLoad(targetPage);

  const heading = targetPage.getByRole("heading", { name: headingText }).first();
  if (await isVisible(heading, 8000)) {
    await expect(heading).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingText).first()).toBeVisible();
  }

  await expect(targetPage.locator("body")).toContainText(/\S+/, { timeout: 10000 });
  await saveScreenshot(targetPage, testInfo, screenshotName, true);

  const finalUrl = targetPage.url();
  await testInfo.attach(`${screenshotName}-url`, {
    body: Buffer.from(finalUrl, "utf-8"),
    contentType: "text/plain"
  });

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(reportOrder.map((field) => [field, "FAIL"])) as Record<
    ReportField,
    StepStatus
  >;
  const failures: string[] = [];

  const runStep = async (label: ReportField, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      report[label] = "PASS";
    } catch (error) {
      report[label] = "FAIL";
      failures.push(`${label}: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  await runStep("Login", async () => {
    const startUrl = process.env.SALEADS_START_URL?.trim();
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    }

    const loginButton = await findClickableByVisibleText(
      page,
      /Sign in with Google|Iniciar sesión con Google|Iniciar sesion con Google|Continuar con Google|Google/i
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUiToLoad(page);

    const popup = await popupPromise;
    if (popup) {
      await waitForUiToLoad(popup);
      await chooseGoogleAccountIfPrompted(popup);
      await popup.close().catch(() => undefined);
      await page.bringToFront();
      await waitForUiToLoad(page);
    } else {
      await chooseGoogleAccountIfPrompted(page);
    }

    const appMain = page.locator("main, [role='main']").first();
    const sidebar = page.locator("aside, nav").first();
    await expect(appMain).toBeVisible({ timeout: 30000 });
    await expect(sidebar).toBeVisible({ timeout: 30000 });
    await saveScreenshot(page, testInfo, "dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, /Negocio/i).catch(() => undefined);
    await clickByVisibleText(page, /Mi Negocio/i);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15000 });
    await saveScreenshot(page, testInfo, "mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await ensureMiNegocioExpanded(page);
    await clickByVisibleText(page, /Agregar Negocio/i);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();
    await saveScreenshot(page, testInfo, "agregar-negocio-modal");

    const nameField = page.getByLabel(/Nombre del Negocio/i).first();
    await nameField.click();
    await nameField.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, /Cancelar/i);
  });

  await runStep("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);
    await clickByVisibleText(page, /Administrar Negocios/i);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Sección Legal|Seccion Legal/i).first()).toBeVisible({
      timeout: 15000
    });
    await saveScreenshot(page, testInfo, "administrar-negocios-account-page", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/juan/i).first()).toBeVisible({ timeout: 10000 });
    await expect(
      page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first()
    ).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({
      timeout: 10000
    });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 10000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({
      timeout: 10000
    });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 10000 });
  });

  await runStep("Términos y Condiciones", async () => {
    await validateLegalLink(
      page,
      testInfo,
      /Términos y Condiciones|Terminos y Condiciones/i,
      /Términos y Condiciones|Terminos y Condiciones/i,
      "terminos-y-condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalLink(
      page,
      testInfo,
      /Política de Privacidad|Politica de Privacidad/i,
      /Política de Privacidad|Politica de Privacidad/i,
      "politica-de-privacidad"
    );
  });

  const reportText = JSON.stringify(report, null, 2);
  await testInfo.attach("final-report.json", {
    body: Buffer.from(reportText, "utf-8"),
    contentType: "application/json"
  });
  // Required checkpoint summary for automation logs.
  console.table(report);

  expect(failures, `Workflow validation failures:\n${failures.join("\n")}`).toEqual([]);
});
