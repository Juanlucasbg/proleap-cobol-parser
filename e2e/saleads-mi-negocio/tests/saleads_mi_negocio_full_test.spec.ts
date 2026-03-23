import { expect, type BrowserContext, type Locator, type Page, test, type TestInfo } from "@playwright/test";

type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

type StepReport = Record<ReportKey, "PASS" | "FAIL">;

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REQUIRED_KEYS: ReportKey[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad"
];

function createInitialReport(): StepReport {
  return REQUIRED_KEYS.reduce((acc, key) => {
    acc[key] = "FAIL";
    return acc;
  }, {} as StepReport);
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return null;
}

async function findByVisibleText(page: Page, text: RegExp): Promise<Locator | null> {
  return firstVisible([
    page.getByRole("button", { name: text }),
    page.getByRole("link", { name: text }),
    page.getByRole("menuitem", { name: text }),
    page.getByRole("tab", { name: text }),
    page.getByRole("heading", { name: text }),
    page.getByText(text)
  ]);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 20_000 });
  await locator.click();
  await waitForUiToLoad(page);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = true
): Promise<string> {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  return path;
}

async function executeStep(
  report: StepReport,
  failures: string[],
  key: ReportKey,
  run: () => Promise<void>
): Promise<void> {
  try {
    await run();
    report[key] = "PASS";
  } catch (error) {
    report[key] = "FAIL";
    const detail = error instanceof Error ? error.message : String(error);
    failures.push(`${key}: ${detail}`);
  }
}

function sectionByHeading(page: Page, headingPattern: RegExp): Locator {
  const heading = page.getByRole("heading", { name: headingPattern }).first();
  return page.locator("section,article,div").filter({ has: heading }).first();
}

async function ensureStartPage(page: Page): Promise<void> {
  const startUrl = process.env.SALEADS_START_URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToLoad(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No URL loaded. Set SALEADS_START_URL or launch the test from a preloaded SaleADS login page."
    );
  }
}

async function handleGoogleAccountSelection(page: Page, context: BrowserContext): Promise<void> {
  const googlePopupPromise = page.waitForEvent("popup", { timeout: 15_000 }).catch(() => null);
  const googlePagePromise = context.waitForEvent("page", { timeout: 15_000 }).catch(() => null);

  const loginButton = await firstVisible([
    page.getByRole("button", { name: /google|sign in|iniciar sesi.n/i }),
    page.getByRole("link", { name: /google|sign in|iniciar sesi.n/i }),
    page.getByText(/sign in with google|continuar con google|iniciar sesi.n con google/i)
  ]);

  if (!loginButton) {
    throw new Error("Could not find a visible Google sign-in button.");
  }

  await clickAndWait(page, loginButton);

  const popup = await googlePopupPromise;
  const contextPage = await googlePagePromise;
  let authPage: Page | null = null;

  if (popup && popup.url().includes("accounts.google.com")) {
    authPage = popup;
  } else if (contextPage && contextPage.url().includes("accounts.google.com")) {
    authPage = contextPage;
  } else if (page.url().includes("accounts.google.com")) {
    authPage = page;
  }

  if (!authPage) {
    return;
  }

  await waitForUiToLoad(authPage);
  const accountChip = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountChip.isVisible().catch(() => false)) {
    await accountChip.click();
    await waitForUiToLoad(authPage);
  }

  if (authPage !== page) {
    await authPage.waitForEvent("close", { timeout: 30_000 }).catch(() => undefined);
    await page.bringToFront();
    await waitForUiToLoad(page);
  }
}

async function openLegalPageAndValidate(
  page: Page,
  context: BrowserContext,
  testInfo: TestInfo,
  linkText: RegExp,
  headingText: RegExp,
  screenshotName: string
): Promise<string> {
  const legalLink = await findByVisibleText(page, linkText);
  if (!legalLink) {
    throw new Error(`Could not find legal link: ${linkText}`);
  }

  const newTabPromise = context.waitForEvent("page", { timeout: 20_000 }).catch(() => null);
  const navigationPromise = page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 20_000 }).catch(() => null);

  await legalLink.click();
  await waitForUiToLoad(page);

  const newTab = await newTabPromise;
  await navigationPromise;

  const targetPage = newTab ?? page;
  await waitForUiToLoad(targetPage);

  await expect(targetPage.getByRole("heading", { name: headingText }).first()).toBeVisible({ timeout: 20_000 });
  await captureCheckpoint(targetPage, testInfo, screenshotName);

  const finalUrl = targetPage.url();

  if (newTab) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createInitialReport();
  const failures: string[] = [];
  const evidence: string[] = [];
  const finalUrls: Record<string, string> = {};

  await executeStep(report, failures, "Login", async () => {
    await ensureStartPage(page);

    const sidebarBeforeLogin = await firstVisible([
      page.locator("aside"),
      page.locator("nav"),
      page.getByText(/mi negocio|negocio/i)
    ]);

    if (!sidebarBeforeLogin) {
      await handleGoogleAccountSelection(page, context);
    }

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.locator("nav"),
      page.getByText(/mi negocio|negocio/i)
    ]);

    expect(sidebar, "Main application interface did not appear after login.").not.toBeNull();
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 20_000 });

    evidence.push(await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png"));
  });

  await executeStep(report, failures, "Mi Negocio menu", async () => {
    const negocioSection = await findByVisibleText(page, /negocio/i);
    if (negocioSection) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocioOption = await findByVisibleText(page, /mi negocio/i);
    if (!miNegocioOption) {
      throw new Error("Could not find 'Mi Negocio' in left sidebar.");
    }
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 20_000 });

    evidence.push(await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", false));
  });

  await executeStep(report, failures, "Agregar Negocio modal", async () => {
    const addBusinessAction = await findByVisibleText(page, /agregar negocio/i);
    if (!addBusinessAction) {
      throw new Error("Could not find 'Agregar Negocio'.");
    }

    await clickAndWait(page, addBusinessAction);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 20_000 });

    const businessNameInput = await firstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[name*='nombre'], input[id*='nombre']").first()
    ]);

    if (!businessNameInput) {
      throw new Error("Input field 'Nombre del Negocio' was not found.");
    }

    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible({ timeout: 20_000 });

    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }).first());

    evidence.push(await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png"));
  });

  await executeStep(report, failures, "Administrar Negocios view", async () => {
    const miNegocioOption = await findByVisibleText(page, /mi negocio/i);
    if (miNegocioOption) {
      await clickAndWait(page, miNegocioOption);
    }

    const manageBusinessAction = await findByVisibleText(page, /administrar negocios/i);
    if (!manageBusinessAction) {
      throw new Error("Could not find 'Administrar Negocios'.");
    }

    await clickAndWait(page, manageBusinessAction);

    await expect(page.getByRole("heading", { name: /informaci.n general/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("heading", { name: /detalles de la cuenta/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("heading", { name: /tus negocios/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("heading", { name: /secci.n legal/i }).first()).toBeVisible({ timeout: 20_000 });

    evidence.push(await captureCheckpoint(page, testInfo, "04-administrar-negocios-account-page-full.png"));
  });

  await executeStep(report, failures, "Informacion General", async () => {
    const infoSection = sectionByHeading(page, /informaci.n general/i);
    await expect(infoSection).toBeVisible({ timeout: 20_000 });

    const sectionText = (await infoSection.innerText()).replace(/\s+/g, " ").trim();
    const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(sectionText);
    if (!hasEmail) {
      throw new Error("User email is not visible in 'Informacion General'.");
    }

    const textLines = sectionText
      .split(" ")
      .map((part) => part.trim())
      .filter(Boolean);
    if (textLines.length < 8) {
      throw new Error("Could not confirm visible user name-like content in 'Informacion General'.");
    }

    await expect(infoSection.getByText(/business plan/i).first()).toBeVisible({ timeout: 20_000 });

    const changePlanButton = await firstVisible([
      infoSection.getByRole("button", { name: /cambiar plan/i }),
      infoSection.getByText(/cambiar plan/i)
    ]);
    if (!changePlanButton) {
      throw new Error("Button 'Cambiar Plan' is not visible.");
    }
  });

  await executeStep(report, failures, "Detalles de la Cuenta", async () => {
    const accountDetailsSection = sectionByHeading(page, /detalles de la cuenta/i);
    await expect(accountDetailsSection).toBeVisible({ timeout: 20_000 });
    await expect(accountDetailsSection.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(accountDetailsSection.getByText(/estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(accountDetailsSection.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await executeStep(report, failures, "Tus Negocios", async () => {
    const businessesSection = sectionByHeading(page, /tus negocios/i);
    await expect(businessesSection).toBeVisible({ timeout: 20_000 });

    const addBusinessButton = await firstVisible([
      businessesSection.getByRole("button", { name: /agregar negocio/i }),
      businessesSection.getByText(/agregar negocio/i)
    ]);
    if (!addBusinessButton) {
      throw new Error("Button 'Agregar Negocio' was not found in 'Tus Negocios'.");
    }

    await expect(businessesSection.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });

    const sectionText = (await businessesSection.innerText()).replace(/\s+/g, " ").trim();
    if (sectionText.length < 40) {
      throw new Error("Business list section does not appear to contain enough visible content.");
    }
  });

  await executeStep(report, failures, "Terminos y Condiciones", async () => {
    finalUrls["Terminos y Condiciones"] = await openLegalPageAndValidate(
      page,
      context,
      testInfo,
      /t.rminos y condiciones/i,
      /t.rminos y condiciones/i,
      "05-terminos-y-condiciones.png"
    );
  });

  await executeStep(report, failures, "Politica de Privacidad", async () => {
    finalUrls["Politica de Privacidad"] = await openLegalPageAndValidate(
      page,
      context,
      testInfo,
      /pol.tica de privacidad/i,
      /pol.tica de privacidad/i,
      "06-politica-de-privacidad.png"
    );
  });

  const finalReport = {
    report,
    finalUrls,
    evidence
  };

  await testInfo.attach("saleads-mi-negocio-final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json"
  });

  testInfo.annotations.push({
    type: "final-report",
    description: JSON.stringify(finalReport)
  });

  expect(
    failures,
    `Workflow completed with validation failures:\n${failures.map((failure) => `- ${failure}`).join("\n")}`
  ).toEqual([]);
});
