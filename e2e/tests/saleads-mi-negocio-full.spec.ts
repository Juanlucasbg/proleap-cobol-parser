import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";
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

const REQUIRED_REPORT_FIELDS: ReportField[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
];

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function createEmptyReport(): Record<ReportField, StepStatus> {
  return {
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
}

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {
    // Some SPA transitions never reach networkidle; domcontentloaded is enough here.
  });
}

async function isVisible(locator: Locator, timeoutMs = 1500): Promise<boolean> {
  return locator
    .first()
    .isVisible({ timeout: timeoutMs })
    .catch(() => false);
}

async function pickVisibleLocator(
  page: Page,
  locators: Locator[],
  timeoutMs = 15000
): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await isVisible(locator, 300)) {
        return locator.first();
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error("No visible locator matched the expected visible text.");
}

async function takeCheckpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function openLoginPageIfNeeded(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    return;
  }

  const urlFromEnv =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL;

  if (!urlFromEnv) {
    throw new Error(
      "Page is about:blank. Provide SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL) to start from a SaleADS login page."
    );
  }

  await page.goto(urlFromEnv, { waitUntil: "domcontentloaded" });
  await waitForUi(page);
}

async function selectGoogleAccountIfPrompted(authPage: Page): Promise<void> {
  const accountLocator = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, {
    exact: true,
  });

  if (await isVisible(accountLocator, 10000)) {
    await accountLocator.click();
    await waitForUi(authPage);
  }
}

async function validateLegalPage({
  appPage,
  testInfo,
  linkPattern,
  headingPattern,
  screenshotName,
}: {
  appPage: Page;
  testInfo: TestInfo;
  linkPattern: RegExp;
  headingPattern: RegExp;
  screenshotName: string;
}): Promise<string> {
  const context = appPage.context();

  const link = await pickVisibleLocator(appPage, [
    appPage.getByRole("link", { name: linkPattern }),
    appPage.getByText(linkPattern),
  ]);

  const popupPromise = context
    .waitForEvent("page", { timeout: 7000 })
    .catch(() => null);

  await link.click();
  await waitForUi(appPage);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  await waitForUi(targetPage);
  await expect(targetPage.getByText(headingPattern).first()).toBeVisible();

  const legalContentVisible = await isVisible(
    targetPage.locator("main, article, section, p").filter({ hasText: /\S/ }),
    10000
  );
  expect(legalContentVisible).toBeTruthy();

  await takeCheckpoint(targetPage, testInfo, screenshotName, true);

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack();
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createEmptyReport();
  const notes: string[] = [];
  let termsUrl = "";
  let privacyUrl = "";

  try {
    await openLoginPageIfNeeded(page);

    const loginButton = await pickVisibleLocator(page, [
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
      }),
      page.getByText(
        /sign in with google|iniciar sesi[oó]n con google|continuar con google/i
      ),
      page.getByRole("button", { name: /google/i }),
      page.getByText(/google/i),
    ]);

    const popupPromise = page
      .context()
      .waitForEvent("page", { timeout: 10000 })
      .catch(() => null);

    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await waitForUi(popup);
      await selectGoogleAccountIfPrompted(popup);
      await popup.waitForClose({ timeout: 60000 }).catch(() => {
        // Some flows keep the popup open after redirect.
      });
    } else {
      await selectGoogleAccountIfPrompted(page);
    }

    await waitForUi(page);

    const sidebarVisible = await isVisible(page.locator("aside, nav"), 30000);
    const negocioVisible = await isVisible(
      page.getByText(/mi negocio|negocio/i),
      30000
    );
    expect(sidebarVisible).toBeTruthy();
    expect(negocioVisible).toBeTruthy();

    report.Login = "PASS";
    await takeCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  } catch (error) {
    notes.push(`Login: ${toErrorMessage(error)}`);
  }

  try {
    const negocioSection = page.getByText(/^Negocio$/i);
    if (await isVisible(negocioSection)) {
      await negocioSection.first().click();
      await waitForUi(page);
    }

    const miNegocio = await pickVisibleLocator(page, [
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    await miNegocio.click();
    await waitForUi(page);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();

    report["Mi Negocio menu"] = "PASS";
    await takeCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  } catch (error) {
    notes.push(`Mi Negocio menu: ${toErrorMessage(error)}`);
  }

  try {
    const agregarNegocio = await pickVisibleLocator(page, [
      page.locator("aside, nav").getByText(/^Agregar Negocio$/i),
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);

    await agregarNegocio.click();
    await waitForUi(page);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const nombreInputVisible =
      (await isVisible(page.getByLabel(/Nombre del Negocio/i), 2000)) ||
      (await isVisible(page.getByPlaceholder(/Nombre del Negocio/i), 2000));
    expect(nombreInputVisible).toBeTruthy();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(
      page.getByRole("button", { name: /^Crear Negocio$/i })
    ).toBeVisible();

    const nombreInput =
      (await isVisible(page.getByLabel(/Nombre del Negocio/i), 1000))
        ? page.getByLabel(/Nombre del Negocio/i)
        : page.getByPlaceholder(/Nombre del Negocio/i);

    await nombreInput.fill("Negocio Prueba Automatización");
    await page.getByRole("button", { name: /^Cancelar$/i }).click();
    await waitForUi(page);

    report["Agregar Negocio modal"] = "PASS";
    await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");
  } catch (error) {
    notes.push(`Agregar Negocio modal: ${toErrorMessage(error)}`);
  }

  try {
    if (!(await isVisible(page.getByText(/Administrar Negocios/i), 2000))) {
      const miNegocio = await pickVisibleLocator(page, [
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);
      await miNegocio.click();
      await waitForUi(page);
    }

    const administrarNegocios = await pickVisibleLocator(page, [
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);

    await administrarNegocios.click();
    await waitForUi(page);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();

    report["Administrar Negocios view"] = "PASS";
    await takeCheckpoint(page, testInfo, "04-administrar-negocios-view.png", true);
  } catch (error) {
    notes.push(`Administrar Negocios view: ${toErrorMessage(error)}`);
  }

  try {
    await expect(page.getByText(/Información General/i)).toBeVisible();

    const infoContainer = page
      .getByText(/Información General/i)
      .first()
      .locator("xpath=ancestor::*[self::section or self::div][1]");
    const infoText = (await infoContainer.innerText()).replace(/\s+/g, " ").trim();

    const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoText);
    expect(hasEmail).toBeTruthy();

    const cleaned = infoText
      .replace(/Información General/gi, "")
      .replace(/BUSINESS PLAN/gi, "")
      .replace(/Cambiar Plan/gi, "");
    const hasLikelyUserName =
      /\b[A-Za-zÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ]+(?:\s+[A-Za-zÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ]+)+/.test(
        cleaned
      ) || /nombre/i.test(cleaned);

    expect(hasLikelyUserName).toBeTruthy();
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    report["Información General"] = "PASS";
  } catch (error) {
    notes.push(`Información General: ${toErrorMessage(error)}`);
  }

  try {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    report["Detalles de la Cuenta"] = "PASS";
  } catch (error) {
    notes.push(`Detalles de la Cuenta: ${toErrorMessage(error)}`);
  }

  try {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const agregarNegocioButtonVisible = await isVisible(
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      6000
    );
    expect(agregarNegocioButtonVisible).toBeTruthy();

    const negociosSection = page
      .getByText(/Tus Negocios/i)
      .first()
      .locator("xpath=ancestor::*[self::section or self::div][1]");
    const businessItemCount = await negociosSection
      .locator("li, [role='row'], tbody tr, .card")
      .count();

    expect(businessItemCount).toBeGreaterThan(0);
    report["Tus Negocios"] = "PASS";
  } catch (error) {
    notes.push(`Tus Negocios: ${toErrorMessage(error)}`);
  }

  try {
    termsUrl = await validateLegalPage({
      appPage: page,
      testInfo,
      linkPattern: /Términos y Condiciones/i,
      headingPattern: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
    });

    report["Términos y Condiciones"] = "PASS";
  } catch (error) {
    notes.push(`Términos y Condiciones: ${toErrorMessage(error)}`);
  }

  try {
    privacyUrl = await validateLegalPage({
      appPage: page,
      testInfo,
      linkPattern: /Política de Privacidad/i,
      headingPattern: /Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
    });

    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    notes.push(`Política de Privacidad: ${toErrorMessage(error)}`);
  }

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    evidence: {
      termsUrl,
      privacyUrl,
    },
    notes,
  };

  await testInfo.attach("saleads-mi-negocio-final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json",
  });

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  const failedFields = REQUIRED_REPORT_FIELDS.filter(
    (field) => report[field] === "FAIL"
  );
  expect(
    failedFields,
    `Validation failed in: ${failedFields.join(", ") || "none"}`
  ).toEqual([]);
});
