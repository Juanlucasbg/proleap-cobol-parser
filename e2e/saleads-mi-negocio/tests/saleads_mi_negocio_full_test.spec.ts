import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

import { expect, type BrowserContext, type Locator, type Page, test } from "@playwright/test";

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type ReportState = Record<ReportField, boolean>;

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function getFirstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    const first = locator.first();
    const visible = await first.isVisible().catch(() => false);
    if (visible) {
      return first;
    }
  }

  return null;
}

async function captureCheckpoint(
  page: Page,
  outputDir: string,
  filename: string,
  fullPage = false,
): Promise<void> {
  const checkpointsDir = path.join(outputDir, "checkpoints");
  mkdirSync(checkpointsDir, { recursive: true });

  await page.screenshot({
    path: path.join(checkpointsDir, `${filename}.png`),
    fullPage,
  });
}

async function chooseGoogleAccountIfPrompted(candidatePage: Page): Promise<void> {
  const accountOption = candidatePage.getByText(GOOGLE_ACCOUNT, { exact: false }).first();
  const accountVisible = await accountOption.isVisible({ timeout: 7000 }).catch(() => false);

  if (accountVisible) {
    await accountOption.click();
    await waitForUi(candidatePage);
  }
}

async function openLegalPageAndReturn(params: {
  appPage: Page;
  context: BrowserContext;
  outputDir: string;
  linkText: string;
  expectedHeading: string;
  screenshotName: string;
}): Promise<string> {
  const { appPage, context, outputDir, expectedHeading, linkText, screenshotName } = params;
  const legalLink = await getFirstVisible([
    appPage.getByRole("link", { name: new RegExp(escapeRegex(linkText), "i") }),
    appPage.getByText(new RegExp(`^${escapeRegex(linkText)}$`, "i")),
  ]);

  expect(legalLink, `No se encontró el enlace legal '${linkText}'.`).not.toBeNull();

  const appUrlBefore = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  const navPromise = appPage.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 10000 }).catch(() => null);

  await legalLink!.click();
  const popup = await popupPromise;
  await navPromise;

  const legalPage = popup ?? appPage;
  await waitForUi(legalPage);

  const legalHeading = await getFirstVisible([
    legalPage.getByRole("heading", { name: new RegExp(escapeRegex(expectedHeading), "i") }),
    legalPage.getByText(new RegExp(escapeRegex(expectedHeading), "i")),
  ]);
  expect(legalHeading, `No se encontró el encabezado '${expectedHeading}'.`).not.toBeNull();

  const legalBodyText = (await legalPage.locator("body").innerText()).trim();
  expect(legalBodyText.length, `No se detectó contenido legal para '${linkText}'.`).toBeGreaterThan(200);

  await captureCheckpoint(legalPage, outputDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== appUrlBefore) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
  const results: ReportState = {
    Login: false,
    "Mi Negocio menu": false,
    "Agregar Negocio modal": false,
    "Administrar Negocios view": false,
    "Información General": false,
    "Detalles de la Cuenta": false,
    "Tus Negocios": false,
    "Términos y Condiciones": false,
    "Política de Privacidad": false,
  };
  const legalUrls: Record<string, string> = {
    "Términos y Condiciones": "N/A",
    "Política de Privacidad": "N/A",
  };
  const errors: Partial<Record<ReportField, string>> = {};

  const runValidation = async (field: ReportField, callback: () => Promise<void>): Promise<void> => {
    try {
      await callback();
      results[field] = true;
    } catch (error) {
      results[field] = false;
      errors[field] = error instanceof Error ? error.message : String(error);
    }
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await runValidation("Login", async () => {
    if (page.url() === "about:blank") {
      throw new Error("No hay URL de login. Define SALEADS_LOGIN_URL o inicia la prueba desde la pantalla de login.");
    }

    const loginButton = await getFirstVisible([
      page.getByRole("button", { name: /sign in with google|iniciar sesión con google|continuar con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesión con google|continuar con google|google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
    ]);
    expect(loginButton, "No se encontró el botón de login con Google.").not.toBeNull();

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton!.click();
    await waitForUi(page);

    const popup = await popupPromise;
    await chooseGoogleAccountIfPrompted(popup ?? page);

    if (popup) {
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => undefined);
    }

    await page.bringToFront();
    await waitForUi(page);

    const mainInterfaceVisible = await getFirstVisible([
      page.locator("main"),
      page.getByRole("main"),
      page.getByText(/dashboard|inicio|panel|mi negocio|negocio/i),
    ]);
    expect(mainInterfaceVisible, "No se detectó la interfaz principal tras login.").not.toBeNull();

    const sidebarVisible = await getFirstVisible([
      page.getByRole("navigation"),
      page.locator("aside"),
      page.locator('[class*="sidebar"]'),
    ]);
    expect(sidebarVisible, "No se detectó la barra lateral tras login.").not.toBeNull();

    await captureCheckpoint(page, testInfo.outputDir, "01-dashboard-loaded");
  });

  if (results.Login) {
    await runValidation("Mi Negocio menu", async () => {
      const negocioSection = await getFirstVisible([
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ]);
      expect(negocioSection, "No se encontró la sección 'Negocio'.").not.toBeNull();
      await negocioSection!.click();
      await waitForUi(page);

      const miNegocioOption = await getFirstVisible([
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);
      expect(miNegocioOption, "No se encontró la opción 'Mi Negocio'.").not.toBeNull();
      await miNegocioOption!.click();
      await waitForUi(page);

      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
      await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

      await captureCheckpoint(page, testInfo.outputDir, "02-mi-negocio-expanded");
    });

    await runValidation("Agregar Negocio modal", async () => {
      const agregarNegocioOption = await getFirstVisible([
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);
      expect(agregarNegocioOption, "No se encontró 'Agregar Negocio'.").not.toBeNull();
      await agregarNegocioOption!.click();
      await waitForUi(page);

      const modalTitle = page.getByText(/^Crear Nuevo Negocio$/i).first();
      await expect(modalTitle).toBeVisible();

      await expect(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i)).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

      const businessNameField = page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i)).first();
      await businessNameField.click();
      await businessNameField.fill("Negocio Prueba Automatización");
      await waitForUi(page);

      await captureCheckpoint(page, testInfo.outputDir, "03-agregar-negocio-modal");

      await page.getByRole("button", { name: /^Cancelar$/i }).first().click();
      await waitForUi(page);
    });

    await runValidation("Administrar Negocios view", async () => {
      const administrarNegociosOption = await getFirstVisible([
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ]);
      if (!administrarNegociosOption) {
        const miNegocioOption = await getFirstVisible([
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByRole("link", { name: /Mi Negocio/i }),
          page.getByText(/^Mi Negocio$/i),
        ]);
        if (miNegocioOption) {
          await miNegocioOption.click();
          await waitForUi(page);
        }
      }

      const administrarNegociosVisible = await getFirstVisible([
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ]);
      expect(administrarNegociosVisible, "No se encontró 'Administrar Negocios'.").not.toBeNull();
      await administrarNegociosVisible!.click();
      await waitForUi(page);

      await expect(page.getByText(/^Información General$/i).first()).toBeVisible();
      await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible();
      await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
      await expect(page.getByText(/^Sección Legal$/i).first()).toBeVisible();

      await captureCheckpoint(page, testInfo.outputDir, "04-administrar-negocios-page", true);
    });

    await runValidation("Información General", async () => {
      const infoGeneralSection = page.locator("section,article,div").filter({ hasText: "Información General" }).first();
      await expect(infoGeneralSection).toBeVisible();

      const hasUserNameIndicator = await getFirstVisible([
        infoGeneralSection.getByText(/nombre|usuario/i),
        infoGeneralSection.locator("p,span,div").filter({ hasText: /^[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/ }),
      ]);
      expect(hasUserNameIndicator, "No se detectó el nombre de usuario en 'Información General'.").not.toBeNull();

      await expect(infoGeneralSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
      await expect(infoGeneralSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(infoGeneralSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    });

    await runValidation("Detalles de la Cuenta", async () => {
      const detailsSection = page.locator("section,article,div").filter({ hasText: "Detalles de la Cuenta" }).first();
      await expect(detailsSection).toBeVisible();
      await expect(detailsSection.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(detailsSection.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(detailsSection.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });

    await runValidation("Tus Negocios", async () => {
      const businessesSection = page.locator("section,article,div").filter({ hasText: "Tus Negocios" }).first();
      await expect(businessesSection).toBeVisible();

      const businessList = await getFirstVisible([
        businessesSection.getByRole("list").first(),
        businessesSection.locator('[class*="list"], [class*="card"], table').first(),
      ]);
      expect(businessList, "No se detectó la lista de negocios.").not.toBeNull();

      await expect(businessesSection.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
      await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    });

    await runValidation("Términos y Condiciones", async () => {
      legalUrls["Términos y Condiciones"] = await openLegalPageAndReturn({
        appPage: page,
        context,
        outputDir: testInfo.outputDir,
        linkText: "Términos y Condiciones",
        expectedHeading: "Términos y Condiciones",
        screenshotName: "05-terminos-y-condiciones",
      });
    });

    await runValidation("Política de Privacidad", async () => {
      legalUrls["Política de Privacidad"] = await openLegalPageAndReturn({
        appPage: page,
        context,
        outputDir: testInfo.outputDir,
        linkText: "Política de Privacidad",
        expectedHeading: "Política de Privacidad",
        screenshotName: "06-politica-de-privacidad",
      });
    });
  } else {
    for (const field of REPORT_FIELDS) {
      if (field !== "Login") {
        errors[field] = "Omitido porque el login inicial no fue exitoso.";
      }
    }
  }

  const report = REPORT_FIELDS.reduce<Record<ReportField, "PASS" | "FAIL">>((accumulator, field) => {
    accumulator[field] = results[field] ? "PASS" : "FAIL";
    return accumulator;
  }, {} as Record<ReportField, "PASS" | "FAIL">);

  const reportPayload = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    legalUrls,
    errors,
  };

  const reportFilePath = path.join(testInfo.outputDir, "final-report.json");
  mkdirSync(path.dirname(reportFilePath), { recursive: true });
  writeFileSync(reportFilePath, JSON.stringify(reportPayload, null, 2), "utf-8");
  await testInfo.attach("final-report", {
    path: reportFilePath,
    contentType: "application/json",
  });
  console.log("FINAL_REPORT", JSON.stringify(reportPayload));

  const failedFields = REPORT_FIELDS.filter((field) => !results[field]);
  expect(
    failedFields,
    `Validaciones fallidas: ${failedFields.join(", ") || "ninguna"}.\nDetalle: ${JSON.stringify(errors, null, 2)}`,
  ).toEqual([]);
});
