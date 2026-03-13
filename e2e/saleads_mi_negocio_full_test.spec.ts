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

type StepResult = {
  status: "PASS" | "FAIL";
  details: string;
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS: ReportField[] = [
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

function initReport(): Record<ReportField, StepResult> {
  return REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = { status: "FAIL", details: "Not executed" };
      return acc;
    },
    {} as Record<ReportField, StepResult>
  );
}

function pass(report: Record<ReportField, StepResult>, field: ReportField, details: string): void {
  report[field] = { status: "PASS", details };
}

function fail(report: Record<ReportField, StepResult>, field: ReportField, error: unknown): void {
  const details = error instanceof Error ? error.message : String(error);
  report[field] = { status: "FAIL", details };
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle").catch(() => {});
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function firstVisible(candidates: Locator[]): Promise<Locator> {
  for (const candidate of candidates) {
    const visible = await candidate.first().isVisible().catch(() => false);
    if (visible) {
      return candidate.first();
    }
  }

  throw new Error("No matching visible element found.");
}

async function checkpoint(page: Page, testInfo: TestInfo, name: string, fullPage = true): Promise<void> {
  await page.screenshot({ path: testInfo.outputPath(name), fullPage });
}

async function maybeSelectGoogleAccount(page: Page): Promise<boolean> {
  const accountCandidates: Locator[] = [
    page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
    page.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
    page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
    page.locator(`[data-identifier="${GOOGLE_ACCOUNT_EMAIL}"]`)
  ];

  for (const account of accountCandidates) {
    if (await account.first().isVisible().catch(() => false)) {
      await account.first().click();
      await waitForUi(page);
      return true;
    }
  }

  return false;
}

async function validateLegalLink(
  page: Page,
  testInfo: TestInfo,
  linkRegex: RegExp,
  headingRegex: RegExp,
  screenshotName: string
): Promise<string> {
  const appUrlBefore = page.url();
  const link = await firstVisible([
    page.getByRole("link", { name: linkRegex }),
    page.getByText(linkRegex, { exact: false })
  ]);

  const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  await clickAndWait(page, link);
  const popup = await popupPromise;

  const targetPage = popup ?? page;
  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForLoadState("networkidle").catch(() => {});

  const heading = targetPage.getByRole("heading", { name: headingRegex }).first();
  if (await heading.isVisible().catch(() => false)) {
    await expect(heading).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingRegex, { exact: false }).first()).toBeVisible();
  }

  await expect(targetPage.locator("p").first()).toBeVisible();
  await checkpoint(targetPage, testInfo, screenshotName);

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== appUrlBefore) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = initReport();

  // Step 1 - Login with Google
  try {
    if (page.url() === "about:blank") {
      const loginUrl = process.env.SALEADS_LOGIN_URL;
      if (!loginUrl) {
        throw new Error(
          "Browser started at about:blank. Set SALEADS_LOGIN_URL or preload the SaleADS login page."
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const loginButton = await firstVisible([
      page.getByRole("button", { name: /google|iniciar sesi[oó]n|sign in/i }),
      page.getByRole("link", { name: /google|iniciar sesi[oó]n|sign in/i }),
      page.getByText(/google|iniciar sesi[oó]n|sign in/i, { exact: false })
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await maybeSelectGoogleAccount(popup);
      await popup.waitForLoadState("networkidle").catch(() => {});
    } else {
      await maybeSelectGoogleAccount(page);
    }

    await waitForUi(page);
    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("nav")
    ]);
    await expect(sidebar).toBeVisible();
    await expect(page.getByText(/mi negocio|negocio|dashboard|inicio/i).first()).toBeVisible();

    await checkpoint(page, testInfo, "01-dashboard-loaded.png");
    pass(report, "Login", "Dashboard loaded and sidebar is visible.");
  } catch (error) {
    fail(report, "Login", error);
  }

  // Step 2 - Open Mi Negocio menu
  try {
    const miNegocioMenu = await firstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i, { exact: false }),
      page.getByText(/negocio/i, { exact: false })
    ]);

    await clickAndWait(page, miNegocioMenu);
    await expect(page.getByText(/agregar negocio/i, { exact: false }).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i, { exact: false }).first()).toBeVisible();

    await checkpoint(page, testInfo, "02-mi-negocio-expanded.png");
    pass(report, "Mi Negocio menu", "Mi Negocio expanded with submenu options visible.");
  } catch (error) {
    fail(report, "Mi Negocio menu", error);
  }

  // Step 3 - Validate Agregar Negocio modal
  try {
    const agregarNegocio = await firstVisible([
      page.getByRole("menuitem", { name: /agregar negocio/i }),
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/^agregar negocio$/i)
    ]);

    await clickAndWait(page, agregarNegocio);

    const modal = page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    const nombreNegocioInput = await firstVisible([
      modal.getByLabel(/nombre del negocio/i),
      modal.getByPlaceholder(/nombre del negocio/i),
      modal.locator("input").first()
    ]);
    await expect(nombreNegocioInput).toBeVisible();
    await expect(modal.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await checkpoint(page, testInfo, "03-agregar-negocio-modal.png");
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, modal.getByRole("button", { name: /cancelar/i }));
    pass(report, "Agregar Negocio modal", "Modal validated and closed with Cancelar.");
  } catch (error) {
    fail(report, "Agregar Negocio modal", error);
  }

  // Step 4 - Open Administrar Negocios
  try {
    const administrarNegociosVisible = await page
      .getByText(/administrar negocios/i, { exact: false })
      .first()
      .isVisible()
      .catch(() => false);

    if (!administrarNegociosVisible) {
      const miNegocioMenu = await firstVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i, { exact: false })
      ]);
      await clickAndWait(page, miNegocioMenu);
    }

    const administrarNegocios = await firstVisible([
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i, { exact: false })
    ]);

    await clickAndWait(page, administrarNegocios);
    await expect(page.getByText(/informaci[oó]n general/i, { exact: false })).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i, { exact: false })).toBeVisible();
    await expect(page.getByText(/tus negocios/i, { exact: false })).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i, { exact: false })).toBeVisible();

    await checkpoint(page, testInfo, "04-administrar-negocios-view.png");
    pass(report, "Administrar Negocios view", "Administrar Negocios sections are visible.");
  } catch (error) {
    fail(report, "Administrar Negocios view", error);
  }

  // Step 5 - Validate Información General
  try {
    await expect(page.getByText(/informaci[oó]n general/i, { exact: false })).toBeVisible();
    const emailValue = page.getByText(/[^@\s]+@[^@\s]+\.[^@\s]+/, { exact: false }).first();
    await expect(emailValue).toBeVisible();
    await expect(page.locator("h1, h2, h3").first()).toBeVisible();
    await expect(page.getByText(/business plan/i, { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    pass(report, "Información General", "User, email, plan and Cambiar Plan are visible.");
  } catch (error) {
    fail(report, "Información General", error);
  }

  // Step 6 - Validate Detalles de la Cuenta
  try {
    await expect(page.getByText(/cuenta creada/i, { exact: false })).toBeVisible();
    await expect(page.getByText(/estado activo/i, { exact: false })).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i, { exact: false })).toBeVisible();
    pass(report, "Detalles de la Cuenta", "Cuenta creada, estado activo e idioma are visible.");
  } catch (error) {
    fail(report, "Detalles de la Cuenta", error);
  }

  // Step 7 - Validate Tus Negocios
  try {
    await expect(page.getByText(/tus negocios/i, { exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i, { exact: false })).toBeVisible();
    pass(report, "Tus Negocios", "Business list and quota text are visible.");
  } catch (error) {
    fail(report, "Tus Negocios", error);
  }

  // Step 8 - Validate Términos y Condiciones
  try {
    const url = await validateLegalLink(
      page,
      testInfo,
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "05-terminos-y-condiciones.png"
    );
    pass(report, "Términos y Condiciones", `Legal page validated at URL: ${url}`);
    testInfo.annotations.push({ type: "TérminosURL", description: url });
  } catch (error) {
    fail(report, "Términos y Condiciones", error);
  }

  // Step 9 - Validate Política de Privacidad
  try {
    const url = await validateLegalLink(
      page,
      testInfo,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad.png"
    );
    pass(report, "Política de Privacidad", `Legal page validated at URL: ${url}`);
    testInfo.annotations.push({ type: "PrivacidadURL", description: url });
  } catch (error) {
    fail(report, "Política de Privacidad", error);
  }

  // Step 10 - Final report
  const lines = REPORT_FIELDS.map((field) => `${field}: ${report[field].status} - ${report[field].details}`);
  // eslint-disable-next-line no-console
  console.log(`\nFinal Report\n${"-".repeat(60)}\n${lines.join("\n")}\n${"-".repeat(60)}\n`);

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failedFields,
    `One or more validations failed.\n${failedFields
      .map((field) => `${field}: ${report[field].details}`)
      .join("\n")}`
  ).toHaveLength(0);
});
