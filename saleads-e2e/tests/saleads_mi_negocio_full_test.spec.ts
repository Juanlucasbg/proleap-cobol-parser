import { expect, Locator, Page, test, TestInfo } from "@playwright/test";

type Status = "PASS" | "FAIL";

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

type StepReport = Record<ReportField, { status: Status; detail?: string }>;

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: StepReport = {
    Login: { status: "FAIL" },
    "Mi Negocio menu": { status: "FAIL" },
    "Agregar Negocio modal": { status: "FAIL" },
    "Administrar Negocios view": { status: "FAIL" },
    "Información General": { status: "FAIL" },
    "Detalles de la Cuenta": { status: "FAIL" },
    "Tus Negocios": { status: "FAIL" },
    "Términos y Condiciones": { status: "FAIL" },
    "Política de Privacidad": { status: "FAIL" },
  };

  const termsAndConditionsUrl = { value: "" };
  const privacyPolicyUrl = { value: "" };

  const startUrl = process.env.SALEADS_START_URL?.trim();
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  }

  await stepGuard(report, "Login", async () => {
    await loginWithGoogleIfNeeded(page);
    await expect(page.locator("main, [role='main'], nav").first()).toBeVisible();
    await expect(
      page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio/i }).first(),
    ).toBeVisible();
    await checkpoint(page, testInfo, "01-dashboard-loaded", true);
  });

  await stepGuard(report, "Mi Negocio menu", async () => {
    await ensureSidebarReady(page);
    await expandMiNegocioMenu(page);
    await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();
    await checkpoint(page, testInfo, "02-mi-negocio-expanded", true);
  });

  await stepGuard(report, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, "Agregar Negocio");

    const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
    await expect(modal.getByText("Nombre del Negocio", { exact: true })).toBeVisible();
    await expect(modal.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: "Cancelar", exact: true })).toBeVisible();
    await expect(modal.getByRole("button", { name: "Crear Negocio", exact: true })).toBeVisible();

    const nameInput = modal.getByLabel("Nombre del Negocio", { exact: true });
    if (await nameInput.count()) {
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
    }

    await checkpoint(page, testInfo, "03-agregar-negocio-modal", true);
    await clickByVisibleText(page, "Cancelar");
    await expect(modal).toBeHidden();
  });

  await stepGuard(report, "Administrar Negocios view", async () => {
    await ensureSidebarReady(page);
    await expandMiNegocioMenu(page);
    await clickByVisibleText(page, "Administrar Negocios");

    await expect(page.getByText("Información General", { exact: true })).toBeVisible();
    await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
    await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible();

    await checkpoint(page, testInfo, "04-administrar-negocios-account-page", true);
  });

  await stepGuard(report, "Información General", async () => {
    await expect(page.getByText("Información General", { exact: true })).toBeVisible();
    await expect(page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true })).toBeVisible();
    await expect(page.getByText("BUSINESS PLAN", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cambiar Plan", exact: true })).toBeVisible();
    await assertLikelyUserNameVisible(page);
  });

  await stepGuard(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await stepGuard(report, "Tus Negocios", async () => {
    await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Agregar Negocio", exact: true })).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first()).toBeVisible();
  });

  await stepGuard(report, "Términos y Condiciones", async () => {
    const result = await validateLegalPage({
      page,
      linkText: "Términos y Condiciones",
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones",
      testInfo,
    });
    termsAndConditionsUrl.value = result.finalUrl;
  });

  await stepGuard(report, "Política de Privacidad", async () => {
    const result = await validateLegalPage({
      page,
      linkText: "Política de Privacidad",
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad",
      testInfo,
    });
    privacyPolicyUrl.value = result.finalUrl;
  });

  const finalReport = {
    ...report,
    evidence: {
      termsAndConditionsFinalUrl: termsAndConditionsUrl.value,
      privacyPolicyFinalUrl: privacyPolicyUrl.value,
    },
  };

  // Final structured report requested by the workflow specification.
  console.log("FINAL_REPORT_START");
  console.log(JSON.stringify(finalReport, null, 2));
  console.log("FINAL_REPORT_END");

  await testInfo.attach("final-report.json", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json",
  });

  expect(Object.values(report).every((entry) => entry.status === "PASS")).toBeTruthy();
});

async function loginWithGoogleIfNeeded(page: Page): Promise<void> {
  const sidebarAlreadyVisible = page
    .locator("aside, nav")
    .filter({ hasText: /Negocio|Mi Negocio/i })
    .first();
  if (await sidebarAlreadyVisible.isVisible().catch(() => false)) {
    return;
  }

  const signInButton = page
    .getByRole("button", { name: /Sign in with Google|Iniciar sesi[oó]n con Google|Google/i })
    .first();
  await expect(signInButton).toBeVisible();

  const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  await clickAndWait(signInButton);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    if (await accountOption.count()) {
      await clickAndWait(accountOption);
    }
    await popup.waitForLoadState("networkidle").catch(() => undefined);
  } else {
    const inlineAccountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    if (await inlineAccountOption.isVisible().catch(() => false)) {
      await clickAndWait(inlineAccountOption);
    }
  }

  await page.waitForLoadState("networkidle").catch(() => undefined);
}

async function ensureSidebarReady(page: Page): Promise<void> {
  const sidebar = page.locator("aside, nav").first();
  await expect(sidebar).toBeVisible();
  await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible();
}

async function expandMiNegocioMenu(page: Page): Promise<void> {
  const agregarVisible = await page
    .getByText("Agregar Negocio", { exact: true })
    .first()
    .isVisible()
    .catch(() => false);
  const administrarVisible = await page
    .getByText("Administrar Negocios", { exact: true })
    .first()
    .isVisible()
    .catch(() => false);
  const hasExpandedItems = agregarVisible && administrarVisible;
  if (hasExpandedItems) {
    return;
  }

  const miNegocioTrigger = page.getByText("Mi Negocio", { exact: true }).first();
  await expect(miNegocioTrigger).toBeVisible();
  await clickAndWait(miNegocioTrigger);
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const locator = page.getByText(text, { exact: true }).first();
  await expect(locator).toBeVisible();
  await clickAndWait(locator);
}

async function clickAndWait(locator: Locator): Promise<void> {
  await locator.click();
  await locator.page().waitForLoadState("domcontentloaded").catch(() => undefined);
  await locator.page().waitForLoadState("networkidle").catch(() => undefined);
}

async function checkpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage: boolean,
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(`${name}.png`),
    fullPage,
  });
}

async function stepGuard(
  report: StepReport,
  key: ReportField,
  fn: () => Promise<void>,
): Promise<void> {
  try {
    await fn();
    report[key] = { status: "PASS" };
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    report[key] = { status: "FAIL", detail };
  }
}

async function assertLikelyUserNameVisible(page: Page): Promise<void> {
  const infoGeneralBlock = page
    .locator("section, div")
    .filter({ has: page.getByText("Información General", { exact: true }) })
    .first();

  const hasNameLikeText = await infoGeneralBlock.evaluate((node) => {
    const text = node.textContent ?? "";
    const compact = text.replace(/\s+/g, " ").trim();
    const nameRegex = /\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+\b/;
    return nameRegex.test(compact);
  });

  expect(hasNameLikeText).toBeTruthy();
}

async function validateLegalPage({
  page,
  linkText,
  headingRegex,
  screenshotName,
  testInfo,
}: {
  page: Page;
  linkText: string;
  headingRegex: RegExp;
  screenshotName: string;
  testInfo: TestInfo;
}): Promise<{ finalUrl: string }> {
  const appUrlBeforeClick = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 5_000 }).catch(() => null);

  await clickByVisibleText(page, linkText);
  const popup = await popupPromise;

  const legalPage = popup ?? page;
  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForLoadState("networkidle").catch(() => undefined);

  const heading = legalPage.getByRole("heading", { name: headingRegex }).first();
  const fallbackHeadingText = legalPage.getByText(headingRegex).first();
  const headingVisible =
    (await heading.isVisible().catch(() => false)) ||
    (await fallbackHeadingText.isVisible().catch(() => false));
  expect(headingVisible).toBeTruthy();

  const bodyText = await legalPage.locator("body").innerText();
  expect(bodyText.trim().length).toBeGreaterThan(120);

  await legalPage.screenshot({
    path: testInfo.outputPath(`${screenshotName}.png`),
    fullPage: true,
  });

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => undefined);
    await page.bringToFront();
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" }).catch(() => undefined);
  }

  return { finalUrl };
}
