import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type Report = Record<ReportField, "PASS" | "FAIL">;

function createReport(): Report {
  return REPORT_FIELDS.reduce((acc, key) => {
    acc[key] = "FAIL";
    return acc;
  }, {} as Report);
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  checkpointName: string,
  fullPage = false
): Promise<void> {
  const safeName = checkpointName.replace(/[^a-zA-Z0-9_-]+/g, "_").toLowerCase();
  await page.screenshot({
    path: testInfo.outputPath(`${Date.now()}_${safeName}.png`),
    fullPage
  });
}

async function firstVisible(candidates: Locator[], timeoutMs = 6_000): Promise<Locator> {
  for (const candidate of candidates) {
    const first = candidate.first();
    try {
      await first.waitFor({ state: "visible", timeout: timeoutMs });
      return first;
    } catch (error) {
      // Continue trying next selector.
    }
  }

  throw new Error("No candidate locator became visible.");
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.click();
  await waitForUiToSettle(page);
}

async function runStep(report: Report, field: ReportField, action: () => Promise<void>): Promise<boolean> {
  try {
    await action();
    report[field] = "PASS";
    return true;
  } catch (error) {
    report[field] = "FAIL";
    const detail = error instanceof Error ? error.message : String(error);
    console.error(`[${field}] FAIL -> ${detail}`);
    return false;
  }
}

async function getSectionByTitle(page: Page, titleRegex: RegExp): Promise<Locator> {
  const heading = await firstVisible([
    page.getByRole("heading", { name: titleRegex }),
    page.getByText(titleRegex)
  ]);

  return heading.locator("xpath=ancestor::section[1] | ancestor::div[1]");
}

async function assertLegalPage(
  page: Page,
  linkRegex: RegExp,
  headingRegex: RegExp,
  screenshotName: string,
  testInfo: TestInfo
): Promise<string> {
  const context = page.context();
  const appUrl = page.url();
  const legalSection = await getSectionByTitle(page, /Sección Legal/i);
  const legalLink = await firstVisible([
    legalSection.getByRole("link", { name: linkRegex }),
    legalSection.getByRole("button", { name: linkRegex }),
    legalSection.getByText(linkRegex)
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickAndWait(legalLink, page);
  let legalPage = await popupPromise;

  if (!legalPage) {
    legalPage = page;
  }

  await waitForUiToSettle(legalPage);

  await firstVisible([
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex)
  ]);

  const legalText = await legalPage.locator("body").innerText();
  expect(legalText.trim().length).toBeGreaterThan(200);

  await captureCheckpoint(legalPage, testInfo, screenshotName);
  const finalUrl = legalPage.url();
  console.log(`[Legal URL] ${headingRegex.source}: ${finalUrl}`);

  if (legalPage !== page) {
    await legalPage.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrl, { waitUntil: "domcontentloaded" });
    });
  }

  await waitForUiToSettle(page);
  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const evidenceUrls: Record<string, string> = {};

  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "Define SALEADS_LOGIN_URL or SALEADS_BASE_URL to run against the current environment login page."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);

  const loginOk = await runStep(report, "Login", async () => {
    const loginButton = await firstVisible([
      page.getByRole("button", { name: /Sign in with Google/i }),
      page.getByRole("button", { name: /Iniciar sesión con Google/i }),
      page.getByRole("button", { name: /Continuar con Google/i }),
      page.getByRole("link", { name: /Sign in with Google/i }),
      page.getByText(/Sign in with Google|Iniciar sesión con Google|Continuar con Google/i)
    ]);

    await clickAndWait(loginButton, page);

    const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL);
    if (await accountOption.first().isVisible({ timeout: 8_000 }).catch(() => false)) {
      await clickAndWait(accountOption.first(), page);
    }

    await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator('[class*="sidebar"]')
    ]);
    await expect(page.getByText(/Negocio/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "dashboard_loaded");
  });

  const miNegocioOk = await runStep(report, "Mi Negocio menu", async () => {
    if (!loginOk) {
      throw new Error("Cannot validate Mi Negocio menu because login step failed.");
    }

    const negocioEntry = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ]);
    await clickAndWait(negocioEntry, page);

    const miNegocioEntry = await firstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i)
    ]);
    await clickAndWait(miNegocioEntry, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "mi_negocio_expanded_menu");
  });

  const agregarModalOk = await runStep(report, "Agregar Negocio modal", async () => {
    if (!miNegocioOk) {
      throw new Error("Cannot validate Agregar Negocio modal because Mi Negocio menu step failed.");
    }

    const addBusinessAction = await firstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await clickAndWait(addBusinessAction, page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    const businessNameInput = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").first()
    ]);
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    await businessNameInput.fill("Negocio Prueba Automatización");
    const cancelButton = await firstVisible([
      page.getByRole("button", { name: /^Cancelar$/i }),
      page.getByText(/^Cancelar$/i)
    ]);
    await captureCheckpoint(page, testInfo, "agregar_negocio_modal");
    await clickAndWait(cancelButton, page);
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeHidden();
  });

  const administrarOk = await runStep(report, "Administrar Negocios view", async () => {
    if (!miNegocioOk) {
      throw new Error("Cannot validate Administrar Negocios because Mi Negocio menu step failed.");
    }

    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      const miNegocioEntry = await firstVisible([
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i)
      ]);
      await clickAndWait(miNegocioEntry, page);
    }

    const manageBusiness = await firstVisible([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i)
    ]);
    await clickAndWait(manageBusiness, page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "administrar_negocios_view", true);
  });

  await runStep(report, "Información General", async () => {
    if (!administrarOk) {
      throw new Error("Cannot validate Información General because account view step failed.");
    }

    const section = await getSectionByTitle(page, /Información General/i);
    const sectionText = await section.innerText();
    const nameCandidate = sectionText
      .split("\n")
      .map((line) => line.trim())
      .find(
        (line) =>
          line.length >= 3 &&
          !/@/.test(line) &&
          !/información general|business plan|cambiar plan/i.test(line)
      );

    expect(nameCandidate, "Expected user name-like text in Información General").toBeTruthy();
    await expect(section.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expect(section.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(section.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    if (!administrarOk) {
      throw new Error("Cannot validate Detalles de la Cuenta because account view step failed.");
    }

    const section = await getSectionByTitle(page, /Detalles de la Cuenta/i);
    await expect(section.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(section.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(section.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    if (!administrarOk) {
      throw new Error("Cannot validate Tus Negocios because account view step failed.");
    }

    const section = await getSectionByTitle(page, /Tus Negocios/i);
    await expect(section).toBeVisible();
    await expect(section.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(section.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const sectionText = await section.innerText();
    expect(sectionText.trim().length).toBeGreaterThan(40);
  });

  await runStep(report, "Términos y Condiciones", async () => {
    if (!administrarOk) {
      throw new Error("Cannot validate Términos y Condiciones because account view step failed.");
    }

    evidenceUrls["Términos y Condiciones"] = await assertLegalPage(
      page,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      "terminos_y_condiciones",
      testInfo
    );
  });

  await runStep(report, "Política de Privacidad", async () => {
    if (!administrarOk) {
      throw new Error("Cannot validate Política de Privacidad because account view step failed.");
    }

    evidenceUrls["Política de Privacidad"] = await assertLegalPage(
      page,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
      "politica_de_privacidad",
      testInfo
    );
  });

  const finalReport = {
    report,
    legalUrls: evidenceUrls
  };

  console.log("Final Report:");
  for (const field of REPORT_FIELDS) {
    console.log(`- ${field}: ${report[field]}`);
  }
  Object.entries(evidenceUrls).forEach(([name, url]) => {
    console.log(`- ${name} URL: ${url}`);
  });

  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });

  const failures = Object.entries(report).filter(([, status]) => status !== "PASS");
  expect(failures, "One or more SaleADS Mi Negocio validations failed").toEqual([]);
});
