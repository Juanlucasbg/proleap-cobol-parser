import { expect, type BrowserContext, type Locator, type Page, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

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

type StepResult = {
  status: StepStatus;
  details: string;
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page: Page): Promise<void> {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => undefined),
    page.waitForTimeout(1200)
  ]);
  await page.waitForTimeout(300);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 15000 });
  await locator.click();
  await waitForUi(page);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const visible = await candidate
      .first()
      .isVisible({ timeout: 2500 })
      .catch(() => false);
    if (visible) {
      return candidate.first();
    }
  }
  return null;
}

async function waitForAnyVisible(candidates: Locator[], timeout = 15000): Promise<Locator> {
  const waits = candidates.map((candidate) => candidate.first().waitFor({ state: "visible", timeout }));
  await Promise.any(waits);
  const visible = await firstVisible(candidates);
  if (!visible) {
    throw new Error("No expected UI element became visible.");
  }
  return visible;
}

async function capture(page: Page, folder: string, name: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: path.join(folder, `${name}.png`),
    fullPage
  });
}

async function ensureLoginPage(page: Page): Promise<void> {
  const explicitLoginUrl = process.env.SALEADS_LOGIN_URL;
  const baseUrl = process.env.SALEADS_BASE_URL;

  if (explicitLoginUrl) {
    await page.goto(explicitLoginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (baseUrl) {
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No SaleADS page is loaded. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to run this test in any environment."
    );
  }
}

async function selectGoogleAccountIfVisible(page: Page): Promise<void> {
  const accountLocator = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
  const accountVisible = await accountLocator.isVisible({ timeout: 7000 }).catch(() => false);
  if (accountVisible) {
    await accountLocator.click();
    await waitForUi(page);
  }
}

async function openLegalDocument(
  context: BrowserContext,
  appPage: Page,
  linkName: RegExp,
  heading: RegExp,
  screenshotFolder: string,
  screenshotName: string
): Promise<{ finalUrl: string; usedPopup: boolean }> {
  const startingUrl = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const link = await waitForAnyVisible(
    [
      appPage.getByRole("link", { name: linkName }),
      appPage.getByRole("button", { name: linkName }),
      appPage.getByText(linkName)
    ],
    10000
  );
  await clickAndWait(link, appPage);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => undefined);
  await waitForUi(legalPage);

  await waitForAnyVisible(
    [legalPage.getByRole("heading", { name: heading }), legalPage.getByText(heading)],
    15000
  );
  await expect(legalPage.locator("main, body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóúÑñ]{20,}/);
  await capture(legalPage, screenshotFolder, screenshotName);

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else if (appPage.url() !== startingUrl) {
    await appPage
      .goBack({ waitUntil: "domcontentloaded" })
      .catch(async () => appPage.goto(startingUrl, { waitUntil: "domcontentloaded" }));
    await waitForUi(appPage);
  }

  return { finalUrl, usedPopup: Boolean(popup) };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report: Record<ReportField, StepResult> = {
    Login: { status: "FAIL", details: "Not executed." },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed." },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed." },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed." },
    "Información General": { status: "FAIL", details: "Not executed." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed." },
    "Tus Negocios": { status: "FAIL", details: "Not executed." },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed." },
    "Política de Privacidad": { status: "FAIL", details: "Not executed." }
  };

  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const screenshotFolder = path.join(process.cwd(), "artifacts", "saleads-mi-negocio", runId);
  await mkdir(screenshotFolder, { recursive: true });

  const markPass = (field: ReportField, details: string): void => {
    report[field] = { status: "PASS", details };
  };
  const markFail = (field: ReportField, error: unknown): void => {
    report[field] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error)
    };
  };

  try {
    await ensureLoginPage(page);

    const loginButton = await firstVisible([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google|google/i)
    ]);
    if (!loginButton) {
      throw new Error("Google login button not found.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => undefined);
      await selectGoogleAccountIfVisible(popup);
      await popup.waitForEvent("close", { timeout: 30000 }).catch(() => undefined);
    } else {
      await selectGoogleAccountIfVisible(page);
    }

    await waitForAnyVisible(
      [
        page.getByRole("navigation"),
        page.locator("aside"),
        page.getByText(/mi negocio|negocio/i),
        page.getByText(/dashboard|inicio/i)
      ],
      30000
    );

    await capture(page, screenshotFolder, "01-dashboard-loaded");
    markPass("Login", "Dashboard loaded and left navigation visible.");
  } catch (error) {
    markFail("Login", error);
  }

  try {
    const negocio = await waitForAnyVisible([page.getByText(/^Negocio$/i), page.getByRole("link", { name: /^Negocio$/i })]);
    await clickAndWait(negocio, page);

    const miNegocio = await waitForAnyVisible([
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    await clickAndWait(miNegocio, page);

    await waitForAnyVisible([page.getByText(/agregar negocio/i), page.getByText(/administrar negocios/i)]);
    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();
    await capture(page, screenshotFolder, "02-mi-negocio-menu-expanded");
    markPass("Mi Negocio menu", "Mi Negocio menu expanded with required submenu options.");
  } catch (error) {
    markFail("Mi Negocio menu", error);
  }

  try {
    const addBusiness = await waitForAnyVisible([
      page.getByRole("menuitem", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    await clickAndWait(addBusiness, page);

    await waitForAnyVisible(
      [page.getByRole("heading", { name: /crear nuevo negocio/i }), page.getByText(/crear nuevo negocio/i)],
      15000
    );
    await expect(page.getByText(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await capture(page, screenshotFolder, "03-agregar-negocio-modal");

    const nameField = await firstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").filter({ hasText: "" })
    ]);
    if (nameField) {
      await nameField.click();
      await nameField.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }
    await clickAndWait(page.getByRole("button", { name: /cancelar/i }), page);
    markPass("Agregar Negocio modal", "Agregar Negocio modal displayed with all required fields and controls.");
  } catch (error) {
    markFail("Agregar Negocio modal", error);
  }

  try {
    const manageBusinessVisible = await page.getByText(/administrar negocios/i).isVisible().catch(() => false);
    if (!manageBusinessVisible) {
      const miNegocioAgain = await waitForAnyVisible([
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ]);
      await clickAndWait(miNegocioAgain, page);
    }

    const manageBusiness = await waitForAnyVisible([
      page.getByRole("menuitem", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    await clickAndWait(manageBusiness, page);

    await waitForAnyVisible([page.getByText(/información general/i), page.getByText(/detalles de la cuenta/i)], 20000);
    await expect(page.getByText(/información general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/sección legal/i)).toBeVisible();
    await capture(page, screenshotFolder, "04-administrar-negocios-view", true);
    markPass("Administrar Negocios view", "Account page loaded with all required sections.");
  } catch (error) {
    markFail("Administrar Negocios view", error);
  }

  try {
    await expect(page.getByText(/información general/i)).toBeVisible();
    await waitForAnyVisible([page.getByText(/business plan/i), page.getByRole("button", { name: /cambiar plan/i })]);
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const visibleTexts = (await page.locator("body").innerText()).toLowerCase();
    if (!visibleTexts.includes("@")) {
      throw new Error("User email was not detected in Información General.");
    }

    markPass("Información General", "User identity details, BUSINESS PLAN, and Cambiar Plan are visible.");
  } catch (error) {
    markFail("Información General", error);
  }

  try {
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
    markPass("Detalles de la Cuenta", "Account details section shows creation date, active state, and language.");
  } catch (error) {
    markFail("Detalles de la Cuenta", error);
  }

  try {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    markPass("Tus Negocios", "Business list, Agregar Negocio action, and usage text are visible.");
  } catch (error) {
    markFail("Tus Negocios", error);
  }

  try {
    const terms = await openLegalDocument(
      context,
      page,
      /términos y condiciones/i,
      /términos y condiciones/i,
      screenshotFolder,
      "05-terminos-y-condiciones"
    );
    markPass(
      "Términos y Condiciones",
      `Legal page validated (${terms.usedPopup ? "new tab" : "same tab"}). URL: ${terms.finalUrl}`
    );
  } catch (error) {
    markFail("Términos y Condiciones", error);
  }

  try {
    const policy = await openLegalDocument(
      context,
      page,
      /política de privacidad/i,
      /política de privacidad/i,
      screenshotFolder,
      "06-politica-de-privacidad"
    );
    markPass(
      "Política de Privacidad",
      `Legal page validated (${policy.usedPopup ? "new tab" : "same tab"}). URL: ${policy.finalUrl}`
    );
  } catch (error) {
    markFail("Política de Privacidad", error);
  }

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    screenshotFolder,
    results: report
  };

  await writeFile(
    path.join(screenshotFolder, "final-report.json"),
    `${JSON.stringify(finalReport, null, 2)}\n`,
    "utf8"
  );

  // eslint-disable-next-line no-console
  console.log(JSON.stringify(finalReport, null, 2));

  const failingSteps = Object.entries(report).filter(([, result]) => result.status === "FAIL");
  expect(
    failingSteps,
    `One or more validation steps failed. Full report: ${path.join(screenshotFolder, "final-report.json")}`
  ).toEqual([]);
});
