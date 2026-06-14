import { BrowserContext, expect, Locator, Page, test } from "@playwright/test";

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

type Report = Record<ReportKey, "PASS" | "FAIL">;

function createReport(): Report {
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

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 10_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 5_000 }).catch(() => undefined);
  await page.waitForTimeout(700);
}

async function firstVisibleLocator(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const item = candidate.first();
    if (await item.isVisible().catch(() => false)) {
      return item;
    }
  }
  return null;
}

async function clickUsingVisibleText(page: Page, patterns: RegExp[], label: string): Promise<void> {
  const candidates: Locator[] = [];

  for (const pattern of patterns) {
    candidates.push(
      page.getByRole("button", { name: pattern }),
      page.getByRole("link", { name: pattern }),
      page.getByRole("menuitem", { name: pattern }),
      page.getByRole("tab", { name: pattern }),
      page.getByText(pattern),
    );
  }

  const target = await firstVisibleLocator(candidates);
  expect(target, `No visible element found for "${label}"`).not.toBeNull();
  await target!.click();
  await waitForUiToLoad(page);
}

async function expectVisibleText(page: Page, pattern: RegExp, label: string): Promise<void> {
  const visible = await firstVisibleLocator([
    page.getByRole("heading", { name: pattern }),
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByText(pattern),
  ]);

  expect(visible, `Expected visible text "${label}"`).not.toBeNull();
  await expect(visible!).toBeVisible();
}

async function trySelectGoogleAccount(page: Page): Promise<void> {
  const accountOption = await firstVisibleLocator([
    page.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
    page.getByRole("link", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
    page.getByText(new RegExp(ACCOUNT_EMAIL, "i")),
  ]);

  if (accountOption) {
    await accountOption.click();
    await waitForUiToLoad(page);
  }
}

async function captureCheckpoint(page: Page, fileName: string): Promise<void> {
  await page.screenshot({ path: fileName, fullPage: true });
}

async function runStep(
  report: Report,
  key: ReportKey,
  stepName: string,
  execution: () => Promise<void>,
  failures: string[],
): Promise<void> {
  try {
    await test.step(stepName, execution);
    report[key] = "PASS";
  } catch (error) {
    report[key] = "FAIL";
    failures.push(`${key}: ${(error as Error).message}`);
  }
}

async function validateLegalPageAndReturn(
  page: Page,
  context: BrowserContext,
  linkPattern: RegExp,
  headingPattern: RegExp,
  screenshotPath: string,
): Promise<string> {
  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await clickUsingVisibleText(page, [linkPattern], linkPattern.source);
  const popup = await popupPromise;

  const legalPage = popup ?? page;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
  await waitForUiToLoad(legalPage);

  await expectVisibleText(legalPage, headingPattern, headingPattern.source);
  await expectVisibleText(
    legalPage,
    /t[eé]rminos|condiciones|pol[ií]tica|privacidad|legal/i,
    "legal content",
  );
  await captureCheckpoint(legalPage, screenshotPath);

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => undefined);
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded", timeout: 15_000 }).catch(() => undefined);
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  const failures: string[] = [];
  const legalUrls: Record<string, string> = {};

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToLoad(page);
  }

  await runStep(report, "Login", "Step 1 - Login with Google", async () => {
    if (page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_LOGIN_URL or start the run with the browser already at the SaleADS login page.",
      );
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickUsingVisibleText(
      page,
      [/sign in with google/i, /iniciar sesi[oó]n con google/i, /google/i],
      "Login with Google button",
    );

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => undefined);
      await trySelectGoogleAccount(popup);
      await popup.waitForEvent("close", { timeout: 20_000 }).catch(() => undefined);
    }

    await trySelectGoogleAccount(page);
    await expectVisibleText(page, /negocio/i, "main app interface");
    await expectVisibleText(page, /negocio|dashboard|inicio/i, "left sidebar navigation");
    await captureCheckpoint(page, testInfo.outputPath("01-dashboard-loaded.png"));
  }, failures);

  await runStep(report, "Mi Negocio menu", "Step 2 - Open Mi Negocio menu", async () => {
    await expectVisibleText(page, /negocio/i, "Negocio section");
    await clickUsingVisibleText(page, [/mi negocio/i], "Mi Negocio");

    await expectVisibleText(page, /agregar negocio/i, "Agregar Negocio");
    await expectVisibleText(page, /administrar negocios/i, "Administrar Negocios");
    await captureCheckpoint(page, testInfo.outputPath("02-mi-negocio-expanded.png"));
  }, failures);

  await runStep(report, "Agregar Negocio modal", "Step 3 - Validate Agregar Negocio modal", async () => {
    await clickUsingVisibleText(page, [/agregar negocio/i], "Agregar Negocio");

    await expectVisibleText(page, /crear nuevo negocio/i, "Crear Nuevo Negocio modal title");
    await expectVisibleText(page, /nombre del negocio/i, "Nombre del Negocio input");
    await expectVisibleText(page, /tienes 2 de 3 negocios/i, "Tienes 2 de 3 negocios");
    await expectVisibleText(page, /cancelar/i, "Cancelar button");
    await expectVisibleText(page, /crear negocio/i, "Crear Negocio button");

    const businessNameInput = await firstVisibleLocator([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[name*='nombre' i]"),
    ]);

    if (businessNameInput) {
      await businessNameInput.fill("Negocio Prueba Automatización");
    }

    await captureCheckpoint(page, testInfo.outputPath("03-agregar-negocio-modal.png"));
    await clickUsingVisibleText(page, [/cancelar/i], "Cancelar");
  }, failures);

  await runStep(
    report,
    "Administrar Negocios view",
    "Step 4 - Open Administrar Negocios",
    async () => {
      const administrarNegociosVisible = await page
        .getByText(/administrar negocios/i)
        .first()
        .isVisible()
        .catch(() => false);

      if (!administrarNegociosVisible) {
        await clickUsingVisibleText(page, [/mi negocio/i], "Mi Negocio");
      }

      await clickUsingVisibleText(page, [/administrar negocios/i], "Administrar Negocios");

      await expectVisibleText(page, /informaci[oó]n general/i, "Información General");
      await expectVisibleText(page, /detalles de la cuenta/i, "Detalles de la Cuenta");
      await expectVisibleText(page, /tus negocios/i, "Tus Negocios");
      await expectVisibleText(page, /secci[oó]n legal/i, "Sección Legal");
      await captureCheckpoint(page, testInfo.outputPath("04-administrar-negocios.png"));
    },
    failures,
  );

  await runStep(report, "Información General", "Step 5 - Validate Información General", async () => {
    await expectVisibleText(page, /informaci[oó]n general/i, "Información General section");
    await expectVisibleText(page, /juan|barbier|garzon|juanlucas/i, "user name");
    await expectVisibleText(page, new RegExp(ACCOUNT_EMAIL, "i"), "user email");
    await expectVisibleText(page, /business plan/i, "BUSINESS PLAN");
    await expectVisibleText(page, /cambiar plan/i, "Cambiar Plan");
  }, failures);

  await runStep(report, "Detalles de la Cuenta", "Step 6 - Validate Detalles de la Cuenta", async () => {
    await expectVisibleText(page, /detalles de la cuenta/i, "Detalles de la Cuenta section");
    await expectVisibleText(page, /cuenta creada/i, "Cuenta creada");
    await expectVisibleText(page, /estado activo/i, "Estado activo");
    await expectVisibleText(page, /idioma seleccionado/i, "Idioma seleccionado");
  }, failures);

  await runStep(report, "Tus Negocios", "Step 7 - Validate Tus Negocios", async () => {
    await expectVisibleText(page, /tus negocios/i, "Tus Negocios section");
    await expectVisibleText(page, /agregar negocio/i, "Agregar Negocio button");
    await expectVisibleText(page, /tienes 2 de 3 negocios/i, "Tienes 2 de 3 negocios");
  }, failures);

  await runStep(
    report,
    "Términos y Condiciones",
    "Step 8 - Validate Términos y Condiciones",
    async () => {
      legalUrls["Términos y Condiciones"] = await validateLegalPageAndReturn(
        page,
        context,
        /t[eé]rminos y condiciones/i,
        /t[eé]rminos y condiciones/i,
        testInfo.outputPath("08-terminos-y-condiciones.png"),
      );
    },
    failures,
  );

  await runStep(
    report,
    "Política de Privacidad",
    "Step 9 - Validate Política de Privacidad",
    async () => {
      legalUrls["Política de Privacidad"] = await validateLegalPageAndReturn(
        page,
        context,
        /pol[ií]tica de privacidad/i,
        /pol[ií]tica de privacidad/i,
        testInfo.outputPath("09-politica-de-privacidad.png"),
      );
    },
    failures,
  );

  await test.step("Step 10 - Final Report", async () => {
    console.log("SaleADS Mi Negocio validation report:");
    console.table(report);
    console.log("Legal URLs:", legalUrls);
  });

  expect(
    failures,
    `One or more required validations failed.\n${failures.join("\n")}`,
  ).toHaveLength(0);
});
