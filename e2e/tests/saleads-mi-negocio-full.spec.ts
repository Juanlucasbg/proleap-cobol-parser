import { expect, Locator, Page, test, type BrowserContext, type TestInfo } from "@playwright/test";
import { writeFileSync } from "node:fs";

type StepOutcome = "PASS" | "FAIL";

type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type FinalReport = {
  generatedAt: string;
  legalUrls: {
    terminosYCondiciones: string | null;
    politicaDePrivacidad: string | null;
  };
  results: Record<ReportKey, StepOutcome>;
  failures: string[];
};

const EMAIL_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

function createBaseReport(): FinalReport {
  return {
    generatedAt: new Date().toISOString(),
    legalUrls: {
      terminosYCondiciones: null,
      politicaDePrivacidad: null,
    },
    results: {
      Login: "FAIL",
      "Mi Negocio menu": "FAIL",
      "Agregar Negocio modal": "FAIL",
      "Administrar Negocios view": "FAIL",
      "Información General": "FAIL",
      "Detalles de la Cuenta": "FAIL",
      "Tus Negocios": "FAIL",
      "Términos y Condiciones": "FAIL",
      "Política de Privacidad": "FAIL",
    },
    failures: [],
  };
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
  await page.waitForLoadState("networkidle", { timeout: 7_000 }).catch(() => {
    // Some SPAs keep long-lived connections; domcontentloaded + short wait is enough.
  });
}

async function firstVisibleOrThrow(locators: Locator[], failureMessage: string): Promise<Locator> {
  for (const locator of locators) {
    const first = locator.first();
    if (await first.isVisible({ timeout: 3_000 }).catch(() => false)) {
      return first;
    }
  }

  throw new Error(failureMessage);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUiToLoad(page);
}

async function ensureLoginPageAvailable(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    await waitForUiToLoad(page);
    return;
  }

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (!loginUrl) {
    throw new Error(
      "Browser started on about:blank. Set SALEADS_LOGIN_URL or launch with an already-open SaleADS.ai login page.",
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToLoad(page);
}

async function saveScreenshot(page: Page, testInfo: TestInfo, filename: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(filename),
    fullPage,
  });
}

function buildUserFacingError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function openMiNegocioMenu(page: Page): Promise<void> {
  const negocioTrigger = await firstVisibleOrThrow(
    [
      page.getByRole("button", { name: /Negocio/i }),
      page.getByRole("link", { name: /Negocio/i }),
      page.getByText(/^Negocio$/i),
    ],
    "No se encontró la sección 'Negocio' en el sidebar.",
  );
  await clickAndWait(negocioTrigger, page);

  const miNegocioOption = await firstVisibleOrThrow(
    [
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/^Mi Negocio$/i),
    ],
    "No se encontró la opción 'Mi Negocio'.",
  );
  await clickAndWait(miNegocioOption, page);
}

async function validateLegalLink(
  page: Page,
  context: BrowserContext,
  linkText: RegExp,
  expectedHeading: RegExp,
  screenshotName: string,
  testInfo: TestInfo,
): Promise<string> {
  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);

  const legalLink = await firstVisibleOrThrow(
    [page.getByRole("link", { name: linkText }), page.getByText(linkText)],
    `No se encontró el enlace legal: ${linkText}`,
  );
  await clickAndWait(legalLink, page);

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await waitForUiToLoad(legalPage);

  await firstVisibleOrThrow(
    [legalPage.getByRole("heading", { name: expectedHeading }), legalPage.getByText(expectedHeading)],
    `No se encontró el encabezado legal esperado: ${expectedHeading}`,
  );

  const legalText = await legalPage.locator("body").innerText();
  expect(legalText.replace(/\s+/g, " ").trim().length).toBeGreaterThan(120);

  await saveScreenshot(legalPage, testInfo, screenshotName, true);
  const capturedUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToLoad(page).catch(() => {});
  }

  return capturedUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createBaseReport();

  const setPass = (key: ReportKey): void => {
    report.results[key] = "PASS";
  };

  const setFail = (key: ReportKey, error: unknown): void => {
    report.results[key] = "FAIL";
    report.failures.push(`${key}: ${buildUserFacingError(error)}`);
  };

  await ensureLoginPageAvailable(page);

  // Step 1: Login with Google
  try {
    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);

    const loginButton = await firstVisibleOrThrow(
      [
        page.getByRole("button", { name: /Google|Sign in|Iniciar sesión/i }),
        page.getByRole("link", { name: /Google|Sign in|Iniciar sesión/i }),
        page.getByText(/Google|Sign in|Iniciar sesión/i),
      ],
      "No se encontró el botón de login con Google.",
    );
    await clickAndWait(loginButton, page);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await waitForUiToLoad(googlePopup);

      const emailOption = await firstVisibleOrThrow(
        [googlePopup.getByText(EMAIL_ACCOUNT, { exact: true }), googlePopup.getByRole("link", { name: EMAIL_ACCOUNT })],
        `No se encontró la cuenta de Google esperada: ${EMAIL_ACCOUNT}`,
      );
      await clickAndWait(emailOption, googlePopup);
      await waitForUiToLoad(page);
    } else {
      const sameTabEmailOption = page.getByText(EMAIL_ACCOUNT, { exact: true }).first();
      if (await sameTabEmailOption.isVisible({ timeout: 5_000 }).catch(() => false)) {
        await clickAndWait(sameTabEmailOption, page);
      }
    }

    await firstVisibleOrThrow(
      [page.getByText(/Negocio/i), page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio/i })],
      "No se detectó la interfaz principal con sidebar después del login.",
    );
    await saveScreenshot(page, testInfo, "01-dashboard-loaded.png", true);
    setPass("Login");
  } catch (error) {
    setFail("Login", error);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await openMiNegocioMenu(page);

    await firstVisibleOrThrow([page.getByText(/Agregar Negocio/i)], "No se encontró 'Agregar Negocio'.");
    await firstVisibleOrThrow([page.getByText(/Administrar Negocios/i)], "No se encontró 'Administrar Negocios'.");
    await saveScreenshot(page, testInfo, "02-mi-negocio-menu-expanded.png");
    setPass("Mi Negocio menu");
  } catch (error) {
    setFail("Mi Negocio menu", error);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const agregarNegocioMenuOption = await firstVisibleOrThrow(
      [
        page.getByRole("menuitem", { name: /^Agregar Negocio$/i }),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      "No se encontró la opción 'Agregar Negocio'.",
    );
    await clickAndWait(agregarNegocioMenuOption, page);

    await firstVisibleOrThrow(
      [page.getByRole("heading", { name: /Crear Nuevo Negocio/i }), page.getByText(/Crear Nuevo Negocio/i)],
      "No se encontró el modal 'Crear Nuevo Negocio'.",
    );
    const negocioInput = await firstVisibleOrThrow(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[name*='negocio'], input[placeholder*='Negocio']"),
      ],
      "No se encontró el input 'Nombre del Negocio'.",
    );
    await firstVisibleOrThrow([page.getByText(/Tienes 2 de 3 negocios/i)], "No se encontró 'Tienes 2 de 3 negocios'.");
    await firstVisibleOrThrow([page.getByRole("button", { name: /Cancelar/i })], "No se encontró el botón 'Cancelar'.");
    await firstVisibleOrThrow(
      [page.getByRole("button", { name: /Crear Negocio/i })],
      "No se encontró el botón 'Crear Negocio'.",
    );

    await negocioInput.click();
    await negocioInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /Cancelar/i }).first(), page);

    await saveScreenshot(page, testInfo, "03-agregar-negocio-modal.png", true);
    setPass("Agregar Negocio modal");
  } catch (error) {
    setFail("Agregar Negocio modal", error);
  }

  // Step 4: Open Administrar Negocios and validate sections
  try {
    await openMiNegocioMenu(page);

    const administrarNegocios = await firstVisibleOrThrow(
      [
        page.getByRole("menuitem", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      "No se encontró la opción 'Administrar Negocios'.",
    );
    await clickAndWait(administrarNegocios, page);

    await firstVisibleOrThrow(
      [page.getByText(/Información General/i), page.getByRole("heading", { name: /Información General/i })],
      "No se encontró la sección 'Información General'.",
    );
    await firstVisibleOrThrow(
      [page.getByText(/Detalles de la Cuenta/i), page.getByRole("heading", { name: /Detalles de la Cuenta/i })],
      "No se encontró la sección 'Detalles de la Cuenta'.",
    );
    await firstVisibleOrThrow(
      [page.getByText(/Tus Negocios/i), page.getByRole("heading", { name: /Tus Negocios/i })],
      "No se encontró la sección 'Tus Negocios'.",
    );
    await firstVisibleOrThrow(
      [
        page.getByText(/Sección Legal/i),
        page.getByText(/Términos y Condiciones/i),
        page.getByText(/Política de Privacidad/i),
      ],
      "No se encontró la sección legal.",
    );

    await saveScreenshot(page, testInfo, "04-administrar-negocios-full-page.png", true);
    setPass("Administrar Negocios view");
  } catch (error) {
    setFail("Administrar Negocios view", error);
  }

  // Step 5: Validate Información General
  try {
    await firstVisibleOrThrow(
      [
        page.getByText(new RegExp(EMAIL_ACCOUNT.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
        page.getByText(/@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/),
      ],
      "No se encontró email visible en Información General.",
    );
    await firstVisibleOrThrow([page.getByText(/BUSINESS PLAN/i)], "No se encontró el texto 'BUSINESS PLAN'.");
    await firstVisibleOrThrow([page.getByRole("button", { name: /Cambiar Plan/i })], "No se encontró el botón 'Cambiar Plan'.");

    const bodyText = await page.locator("body").innerText();
    expect(bodyText).toMatch(/[A-Za-zÁÉÍÓÚáéíóúñÑ]{2,}\s+[A-Za-zÁÉÍÓÚáéíóúñÑ]{2,}/);
    setPass("Información General");
  } catch (error) {
    setFail("Información General", error);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await firstVisibleOrThrow([page.getByText(/Cuenta creada/i)], "No se encontró 'Cuenta creada'.");
    await firstVisibleOrThrow([page.getByText(/Estado activo/i)], "No se encontró 'Estado activo'.");
    await firstVisibleOrThrow([page.getByText(/Idioma seleccionado/i)], "No se encontró 'Idioma seleccionado'.");
    setPass("Detalles de la Cuenta");
  } catch (error) {
    setFail("Detalles de la Cuenta", error);
  }

  // Step 7: Validate Tus Negocios
  try {
    await firstVisibleOrThrow([page.getByText(/Tus Negocios/i)], "No se encontró el encabezado de 'Tus Negocios'.");
    await firstVisibleOrThrow(
      [page.getByRole("button", { name: /Agregar Negocio/i }), page.getByText(/^Agregar Negocio$/i)],
      "No se encontró el botón 'Agregar Negocio' en la sección Tus Negocios.",
    );
    await firstVisibleOrThrow([page.getByText(/Tienes 2 de 3 negocios/i)], "No se encontró 'Tienes 2 de 3 negocios'.");
    setPass("Tus Negocios");
  } catch (error) {
    setFail("Tus Negocios", error);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    report.legalUrls.terminosYCondiciones = await validateLegalLink(
      page,
      context,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones.png",
      testInfo,
    );
    setPass("Términos y Condiciones");
  } catch (error) {
    setFail("Términos y Condiciones", error);
  }

  // Step 9: Validate Política de Privacidad
  try {
    report.legalUrls.politicaDePrivacidad = await validateLegalLink(
      page,
      context,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
      "06-politica-de-privacidad.png",
      testInfo,
    );
    setPass("Política de Privacidad");
  } catch (error) {
    setFail("Política de Privacidad", error);
  }

  const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf-8");
  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json",
  });

  // Step 10: Return PASS/FAIL for each validation step.
  expect(report.failures, JSON.stringify(report, null, 2)).toHaveLength(0);
});
