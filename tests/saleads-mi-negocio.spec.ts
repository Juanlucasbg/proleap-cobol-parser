import { expect, Locator, Page, test } from "@playwright/test";

type ReportStatus = "PASS" | "FAIL";

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

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 7_000 }).catch(() => undefined);
  await page.waitForTimeout(700);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 15_000 });
  await locator.click();
  await waitForUi(page);
}

async function openOrReuseTarget(page: Page): Promise<void> {
  const configuredUrl = process.env.SALEADS_APP_URL ?? process.env.BASE_URL;

  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No SaleADS URL configured. Set SALEADS_APP_URL (or BASE_URL) for this environment."
    );
  }
}

async function handleGoogleAccountPicker(
  page: Page,
  popupPromise: Promise<Page | null>
): Promise<void> {
  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    const account = popup.getByText(GOOGLE_ACCOUNT, { exact: true });
    if (await account.isVisible({ timeout: 6_000 }).catch(() => false)) {
      await account.click();
    }
    await popup.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  if (page.url().includes("accounts.google.com")) {
    const account = page.getByText(GOOGLE_ACCOUNT, { exact: true });
    if (await account.isVisible({ timeout: 6_000 }).catch(() => false)) {
      await account.click();
      await waitForUi(page);
    }
  }
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const addBusiness = page.getByText("Agregar Negocio", { exact: true });
  if (await addBusiness.isVisible().catch(() => false)) {
    return;
  }

  const miNegocio = page
    .getByRole("link", { name: /mi negocio/i })
    .or(page.getByRole("button", { name: /mi negocio/i }))
    .or(page.getByText("Mi Negocio", { exact: true }))
    .first();
  await clickAndWait(page, miNegocio);
}

async function openLegalLinkAndValidate(
  page: Page,
  linkName: "Términos y Condiciones" | "Política de Privacidad",
  headingName: RegExp,
  screenshotName: string,
  mainAppUrl: string | null
): Promise<string> {
  const context = page.context();
  const originalPage = page;

  const [newTab] = await Promise.all([
    context.waitForEvent("page", { timeout: 8_000 }).catch(() => null),
    page.getByRole("link", { name: linkName }).click(),
  ]);

  const targetPage = newTab ?? page;
  await waitForUi(targetPage);
  await expect(targetPage.getByRole("heading", { name: headingName })).toBeVisible({
    timeout: 20_000,
  });
  await expect(targetPage.locator("body")).toContainText(/\S+/, { timeout: 20_000 });
  await targetPage.screenshot({ path: screenshotName, fullPage: true });
  const finalUrl = targetPage.url();

  if (newTab) {
    await newTab.close();
    await originalPage.bringToFront();
    await waitForUi(originalPage);
  } else if (mainAppUrl) {
    await page.goto(mainAppUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("SaleADS - full Mi Negocio workflow", async ({ page }, testInfo) => {
  testInfo.setTimeout(240_000);

  const report: Record<ReportField, ReportStatus> = {
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
  const evidence: Record<string, string> = {};

  const runStep = async (name: ReportField, fn: () => Promise<void>) => {
    try {
      await fn();
      report[name] = "PASS";
    } catch (error) {
      report[name] = "FAIL";
      failures.push(`${name}: ${(error as Error).message}`);
    }
  };

  await runStep("Login", async () => {
    await openOrReuseTarget(page);

    const loginButton = page
      .getByRole("button", { name: /google|continuar con google|sign in with google|iniciar/i })
      .or(page.getByRole("link", { name: /google|continuar con google|sign in with google|iniciar/i }))
      .first();
    await expect(loginButton).toBeVisible({ timeout: 20_000 });
    const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);
    await handleGoogleAccountPicker(page, popupPromise);

    const sidebar = page
      .locator("aside, nav")
      .filter({ hasText: /negocio|mi negocio|dashboard|inicio/i })
      .first();
    await expect(sidebar).toBeVisible({ timeout: 40_000 });

    const dashboardScreenshot = testInfo.outputPath("01-dashboard-loaded.png");
    await page.screenshot({ path: dashboardScreenshot, fullPage: true });
    evidence.dashboard = dashboardScreenshot;
  });

  await runStep("Mi Negocio menu", async () => {
    if (report.Login !== "PASS") {
      throw new Error("Prerequisite failed: Login step did not complete.");
    }

    await ensureMiNegocioExpanded(page);
    await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible({
      timeout: 15_000,
    });

    const menuScreenshot = testInfo.outputPath("02-mi-negocio-expanded-menu.png");
    await page.screenshot({ path: menuScreenshot, fullPage: true });
    evidence.miNegocioMenu = menuScreenshot;
  });

  await runStep("Agregar Negocio modal", async () => {
    if (report["Mi Negocio menu"] !== "PASS") {
      throw new Error("Prerequisite failed: Mi Negocio menu step did not complete.");
    }

    await clickAndWait(page, page.getByText("Agregar Negocio", { exact: true }));
    await expect(page.getByRole("heading", { name: "Crear Nuevo Negocio" })).toBeVisible({
      timeout: 15_000,
    });
    const modal = page.locator('[role="dialog"]').filter({ hasText: "Crear Nuevo Negocio" }).first();
    await expect(modal.getByLabel("Nombre del Negocio")).toBeVisible({ timeout: 15_000 });
    await expect(modal).toContainText("Tienes 2 de 3 negocios");
    await expect(modal.getByRole("button", { name: "Cancelar" })).toBeVisible();
    await expect(modal.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

    const modalScreenshot = testInfo.outputPath("03-agregar-negocio-modal.png");
    await page.screenshot({ path: modalScreenshot, fullPage: true });
    evidence.agregarNegocioModal = modalScreenshot;

    await modal.getByLabel("Nombre del Negocio").fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, modal.getByRole("button", { name: "Cancelar" }));
  });

  await runStep("Administrar Negocios view", async () => {
    if (report["Mi Negocio menu"] !== "PASS") {
      throw new Error("Prerequisite failed: Mi Negocio menu step did not complete.");
    }

    await ensureMiNegocioExpanded(page);
    await clickAndWait(page, page.getByText("Administrar Negocios", { exact: true }));
    await expect(page.getByRole("heading", { name: "Información General" })).toBeVisible({
      timeout: 20_000,
    });
    await expect(page.getByRole("heading", { name: "Detalles de la Cuenta" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Tus Negocios" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Sección Legal" })).toBeVisible();

    const accountScreenshot = testInfo.outputPath("04-administrar-negocios-cuenta.png");
    await page.screenshot({ path: accountScreenshot, fullPage: true });
    evidence.administrarNegocios = accountScreenshot;
  });

  await runStep("Información General", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Prerequisite failed: Administrar Negocios view step did not complete.");
    }

    const section = page.locator("section, div").filter({ hasText: "Información General" }).first();
    await expect(section).toContainText(/@/);
    await expect(section).toContainText(/BUSINESS PLAN/i);
    await expect(section.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Prerequisite failed: Administrar Negocios view step did not complete.");
    }

    const section = page.locator("section, div").filter({ hasText: "Detalles de la Cuenta" }).first();
    await expect(section).toContainText("Cuenta creada");
    await expect(section).toContainText("Estado activo");
    await expect(section).toContainText("Idioma seleccionado");
  });

  await runStep("Tus Negocios", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Prerequisite failed: Administrar Negocios view step did not complete.");
    }

    const section = page.locator("section, div").filter({ hasText: "Tus Negocios" }).first();
    await expect(section).toContainText("Agregar Negocio");
    await expect(section).toContainText("Tienes 2 de 3 negocios");
    await expect(section.locator("li, article, [data-testid*=business], table")).toBeVisible();
  });

  let mainAppUrl: string | null = null;
  await runStep("Términos y Condiciones", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Prerequisite failed: Administrar Negocios view step did not complete.");
    }

    mainAppUrl = page.url();
    const termsScreenshot = testInfo.outputPath("05-terminos-y-condiciones.png");
    const finalUrl = await openLegalLinkAndValidate(
      page,
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      termsScreenshot,
      mainAppUrl
    );
    evidence.terminosScreenshot = termsScreenshot;
    evidence.terminosUrl = finalUrl;
  });

  await runStep("Política de Privacidad", async () => {
    if (report["Administrar Negocios view"] !== "PASS") {
      throw new Error("Prerequisite failed: Administrar Negocios view step did not complete.");
    }

    if (mainAppUrl && page.url() !== mainAppUrl) {
      await page.goto(mainAppUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
    const privacyScreenshot = testInfo.outputPath("06-politica-de-privacidad.png");
    const finalUrl = await openLegalLinkAndValidate(
      page,
      "Política de Privacidad",
      /Política de Privacidad/i,
      privacyScreenshot,
      mainAppUrl
    );
    evidence.politicaScreenshot = privacyScreenshot;
    evidence.politicaUrl = finalUrl;
  });

  const finalReport = {
    report,
    evidence,
    failures,
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2)),
    contentType: "application/json",
  });

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  expect(failures, `Workflow failures:\n${failures.join("\n")}`).toEqual([]);
});
