import { writeFile } from "node:fs/promises";
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

type ReportEntry = {
  status: "PASS" | "FAIL";
  details: string;
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page: Page, ms = 900): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(ms);
}

async function firstVisible(page: Page, label: string, candidates: Locator[], timeoutMs = 20_000): Promise<Locator> {
  const endTime = Date.now() + timeoutMs;

  while (Date.now() < endTime) {
    for (const candidate of candidates) {
      const target = candidate.first();
      if (await target.isVisible().catch(() => false)) {
        return target;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`Unable to find visible element for: ${label}`);
}

async function clickAndWait(page: Page, locator: Locator, clickName: string): Promise<void> {
  await expect(locator, `${clickName} should be visible before clicking`).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  const filePath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, { path: filePath, contentType: "image/png" });
}

async function chooseGoogleAccountIfPresent(page: Page): Promise<boolean> {
  const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUi(page);
    return true;
  }

  return false;
}

async function validateLegalPage(
  appPage: Page,
  testInfo: TestInfo,
  linkTextRegex: RegExp,
  headingRegex: RegExp,
  screenshotName: string
): Promise<{ finalUrl: string; openedInNewTab: boolean }> {
  const context = appPage.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  const linkLocator = await firstVisible(appPage, linkTextRegex.source, [
    appPage.getByRole("link", { name: linkTextRegex }),
    appPage.getByText(linkTextRegex)
  ]);

  await clickAndWait(appPage, linkLocator, `Legal link ${linkTextRegex}`);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;
  const openedInNewTab = popup !== null;

  await waitForUi(targetPage, 1_100);
  await expect(targetPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({ timeout: 30_000 });

  const bodyParagraph = await firstVisible(targetPage, `legal paragraph for ${headingRegex.source}`, [
    targetPage.locator("main p"),
    targetPage.locator("article p"),
    targetPage.locator("p")
  ]);
  const legalText = (await bodyParagraph.innerText()).trim();
  if (legalText.length < 20) {
    throw new Error(`Legal content appears too short for ${headingRegex.source}.`);
  }

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (openedInNewTab) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(appPage);
  }

  return { finalUrl, openedInNewTab };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: Record<ReportField, ReportEntry> = {
    Login: { status: "FAIL", details: "Not executed" },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed" },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed" },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed" },
    "Información General": { status: "FAIL", details: "Not executed" },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed" },
    "Tus Negocios": { status: "FAIL", details: "Not executed" },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed" },
    "Política de Privacidad": { status: "FAIL", details: "Not executed" }
  };

  const legalUrls: Record<string, string> = {};
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL ?? process.env.BASE_URL;

  const execute = async (field: ReportField, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      report[field] = { status: "PASS", details: "Validation completed successfully." };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[field] = { status: "FAIL", details: message };
    }
  };

  await execute("Login", async () => {
    if (page.url() === "about:blank") {
      if (!loginUrl) {
        throw new Error(
          "Browser started on about:blank. Set SALEADS_LOGIN_URL/SALEADS_URL/BASE_URL to the login page for your environment."
        );
      }
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);

    const googleSignInTrigger = await firstVisible(page, "Google sign-in trigger", [
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.locator("button:has-text('Google')"),
      page.locator("[role='button']:has-text('Google')"),
      page.locator("a:has-text('Google')")
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await googleSignInTrigger.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await chooseGoogleAccountIfPresent(popup);
      await popup.waitForClose({ timeout: 45_000 }).catch(() => undefined);
    } else {
      await chooseGoogleAccountIfPresent(page);
    }

    await page.bringToFront();
    await waitForUi(page, 1_500);

    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 60_000 });
    const sidebar = await firstVisible(page, "left sidebar", [
      page.locator("aside"),
      page.locator("nav"),
      page.getByRole("navigation")
    ]);
    await expect(sidebar).toBeVisible();

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded");
  });

  await execute("Mi Negocio menu", async () => {
    const negocioMenu = await firstVisible(page, "Negocio menu section", [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ]);
    await clickAndWait(page, negocioMenu, "Negocio");

    const miNegocioOption = await firstVisible(page, "Mi Negocio option", [
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i)
    ]);
    await clickAndWait(page, miNegocioOption, "Mi Negocio");

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded");
  });

  await execute("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible(page, "Agregar Negocio menu item", [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await clickAndWait(page, agregarNegocio, "Agregar Negocio");

    await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first()).toBeVisible();

    const nombreInput = await firstVisible(page, "Nombre del Negocio input", [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[name*='negocio' i]")
    ]);
    await expect(nombreInput).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const cancelarButton = await firstVisible(page, "Cancelar button", [
      page.getByRole("button", { name: /^Cancelar$/i }),
      page.getByText(/^Cancelar$/i)
    ]);
    await expect(await firstVisible(page, "Crear Negocio button", [
      page.getByRole("button", { name: /^Crear Negocio$/i }),
      page.getByText(/^Crear Negocio$/i)
    ])).toBeVisible();

    await nombreInput.click();
    await waitForUi(page);
    await nombreInput.fill("Negocio Prueba Automatización");
    await waitForUi(page);

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");
    await clickAndWait(page, cancelarButton, "Cancelar (modal)");
  });

  await execute("Administrar Negocios view", async () => {
    const miNegocioOption = await firstVisible(page, "Mi Negocio option", [
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i)
    ]);
    await clickAndWait(page, miNegocioOption, "Mi Negocio (expand for admin)");

    const administrarNegocios = await firstVisible(page, "Administrar Negocios option", [
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i)
    ]);
    await clickAndWait(page, administrarNegocios, "Administrar Negocios");

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page", true);
  });

  await execute("Información General", async () => {
    await expect(page.getByText(/Información General/i).first()).toBeVisible();

    const emailValue = await firstVisible(page, "user email value", [
      page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
      page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
    ]);
    await expect(emailValue).toBeVisible();

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    // User name can vary by environment and profile settings, so we validate the presence
    // of a dedicated user label or profile value in this section in addition to email.
    await expect(
      await firstVisible(page, "user name indicator", [
        page.getByText(/^Nombre$/i),
        page.getByText(/Nombre del usuario/i),
        page.getByText(/Perfil/i)
      ])
    ).toBeVisible();
  });

  await execute("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await execute("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const businessListIndicator = await firstVisible(page, "business list", [
      page.locator("[class*='business' i] li"),
      page.locator("table"),
      page.locator("ul li")
    ]);
    await expect(businessListIndicator).toBeVisible();
  });

  await execute("Términos y Condiciones", async () => {
    const result = await validateLegalPage(
      page,
      testInfo,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones"
    );
    legalUrls["Términos y Condiciones"] = result.finalUrl;
  });

  await execute("Política de Privacidad", async () => {
    const result = await validateLegalPage(
      page,
      testInfo,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "06-politica-de-privacidad"
    );
    legalUrls["Política de Privacidad"] = result.finalUrl;
  });

  const reportLines = [
    "Final Report - saleads_mi_negocio_full_test",
    ...Object.entries(report).map(([field, entry]) => `- ${field}: ${entry.status} (${entry.details})`)
  ];
  const finalReportText = reportLines.join("\n");

  const reportPath = testInfo.outputPath("final-report.txt");
  await writeFile(reportPath, finalReportText, "utf8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "text/plain" });

  const legalUrlsPath = testInfo.outputPath("legal-urls.json");
  await writeFile(legalUrlsPath, JSON.stringify(legalUrls, null, 2), "utf8");
  await testInfo.attach("legal-urls", { path: legalUrlsPath, contentType: "application/json" });

  console.log(finalReportText);
  console.log(`Legal URLs: ${JSON.stringify(legalUrls)}`);
  console.log(`Final report artifact path: ${reportPath}`);
  console.log(`Legal URLs artifact path: ${legalUrlsPath}`);

  const failedFields = Object.entries(report)
    .filter(([, entry]) => entry.status === "FAIL")
    .map(([field]) => field);
  expect(failedFields, `One or more workflow validations failed: ${failedFields.join(", ")}`).toHaveLength(0);
});
