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
  "Política de Privacidad",
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type ReportStatus = "PASS" | "FAIL";

function toFileName(label: string): string {
  return label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(300);
}

async function takeCheckpoint(
  page: Page,
  testInfo: TestInfo,
  label: string,
  fullPage = false
): Promise<void> {
  const path = testInfo.outputPath(`${toFileName(label)}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(`checkpoint-${label}`, {
    path,
    contentType: "image/png",
  });
}

async function waitForAnyVisible(
  page: Page,
  locators: Locator[],
  description: string,
  timeoutMs = 15000
): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of locators) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function ensureMenuExpanded(page: Page): Promise<void> {
  const miNegocioVisible = await page
    .getByText(/Mi Negocio/i)
    .first()
    .isVisible()
    .catch(() => false);

  if (!miNegocioVisible) {
    const negocio = await waitForAnyVisible(
      page,
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      "Negocio section"
    );
    await clickAndWait(page, negocio);
  }
}

async function runValidation(params: {
  appPage: Page;
  testInfo: TestInfo;
  report: Record<ReportField, ReportStatus>;
  failures: string[];
  field: ReportField;
  action: () => Promise<void>;
}): Promise<void> {
  const { appPage, testInfo, report, failures, field, action } = params;

  try {
    await action();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    failures.push(`${field}: ${(error as Error).message}`);

    if (!appPage.isClosed()) {
      await takeCheckpoint(appPage, testInfo, `failure-${field}`, true).catch(() => {});
    }
  }
}

async function validateLegalPage(params: {
  appPage: Page;
  contextPages: Page[];
  linkRegex: RegExp;
  headingRegex: RegExp;
  screenshotLabel: string;
  testInfo: TestInfo;
}): Promise<{ finalUrl: string; appPage: Page }> {
  const { appPage, contextPages, linkRegex, headingRegex, screenshotLabel, testInfo } = params;

  const link = await waitForAnyVisible(
    appPage,
    [
      appPage.getByRole("link", { name: linkRegex }),
      appPage.getByRole("button", { name: linkRegex }),
      appPage.getByText(linkRegex),
    ],
    `legal link ${linkRegex}`
  );

  const appUrlBefore = appPage.url();
  const previousPages = new Set(contextPages);
  const newPagePromise = appPage.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickAndWait(appPage, link);

  const maybeNewPage = await newPagePromise;
  let legalPage = appPage;

  if (maybeNewPage && !previousPages.has(maybeNewPage)) {
    legalPage = maybeNewPage;
    await waitForUi(legalPage);
  }

  await waitForAnyVisible(
    legalPage,
    [legalPage.getByRole("heading", { name: headingRegex }), legalPage.getByText(headingRegex)],
    `heading ${headingRegex}`,
    20000
  );

  await waitForAnyVisible(
    legalPage,
    [
      legalPage.locator("main p, article p, section p, p").first(),
      legalPage.getByText(/t[eé]rminos|condiciones|privacidad|datos|acept/i),
    ],
    "legal body content",
    20000
  );

  const finalUrl = legalPage.url();
  await takeCheckpoint(legalPage, testInfo, screenshotLabel, true);

  if (legalPage !== appPage) {
    await legalPage.close().catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
    return { finalUrl, appPage };
  }

  if (appPage.url() !== appUrlBefore) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return { finalUrl, appPage };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])) as Record<
    ReportField,
    ReportStatus
  >;
  const failures: string[] = [];
  const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};
  const expectedNameToken = (process.env.SALEADS_EXPECTED_NAME_TOKEN ?? "juan").trim();
  let appPage = page;

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_LOGIN_URL to the login page for the active environment (dev/staging/prod)."
    );
  }

  await runValidation({
    appPage,
    testInfo,
    report,
    failures,
    field: "Login",
    action: async () => {
      const googleLoginTrigger = await waitForAnyVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n|continuar/i }),
          appPage.getByRole("link", { name: /google|sign in|iniciar sesi[oó]n|continuar/i }),
          appPage.getByText(/google/i),
        ],
        "Google login trigger",
        25000
      );

      const pagesBefore = new Set(appPage.context().pages());
      const newPagePromise = appPage.context().waitForEvent("page", { timeout: 15000 }).catch(() => null);

      await clickAndWait(appPage, googleLoginTrigger);

      const maybeNewPage = await newPagePromise;
      let authPage = appPage;
      if (maybeNewPage && !pagesBefore.has(maybeNewPage)) {
        authPage = maybeNewPage;
        await waitForUi(authPage);
      }

      const accountOption = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(authPage, accountOption);
      }

      await authPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
      await authPage.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});

      if (authPage !== appPage && !authPage.isClosed()) {
        const authLooksLikeMainApp = await authPage
          .getByText(/Mi Negocio|Administrar Negocios|Negocio/i)
          .first()
          .isVisible()
          .catch(() => false);

        if (authLooksLikeMainApp) {
          appPage = authPage;
        }
      }

      await appPage.bringToFront();
      await waitForUi(appPage);

      await waitForAnyVisible(
        appPage,
        [appPage.locator("aside"), appPage.locator("nav").filter({ hasText: /Negocio|Mi Negocio/i })],
        "left sidebar navigation",
        30000
      );
      await waitForAnyVisible(
        appPage,
        [
          appPage.getByText(/Negocio/i),
          appPage.getByText(/Mi Negocio/i),
          appPage.getByRole("heading"),
        ],
        "main application UI indicators",
        30000
      );

      await takeCheckpoint(appPage, testInfo, "step-1-dashboard-loaded", true);
    },
  });

  await runValidation({
    appPage,
    testInfo,
    report,
    failures,
    field: "Mi Negocio menu",
    action: async () => {
      await ensureMenuExpanded(appPage);

      const miNegocio = await waitForAnyVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /Mi Negocio/i }),
          appPage.getByRole("link", { name: /Mi Negocio/i }),
          appPage.getByText(/Mi Negocio/i),
        ],
        "Mi Negocio menu option"
      );
      await clickAndWait(appPage, miNegocio);

      await waitForAnyVisible(
        appPage,
        [appPage.getByText(/Agregar Negocio/i), appPage.getByRole("link", { name: /Agregar Negocio/i })],
        "Agregar Negocio submenu"
      );
      await waitForAnyVisible(
        appPage,
        [
          appPage.getByText(/Administrar Negocios/i),
          appPage.getByRole("link", { name: /Administrar Negocios/i }),
        ],
        "Administrar Negocios submenu"
      );

      await takeCheckpoint(appPage, testInfo, "step-2-mi-negocio-expanded", true);
    },
  });

  await runValidation({
    appPage,
    testInfo,
    report,
    failures,
    field: "Agregar Negocio modal",
    action: async () => {
      const agregarNegocio = await waitForAnyVisible(
        appPage,
        [
          appPage.getByRole("link", { name: /Agregar Negocio/i }),
          appPage.getByRole("button", { name: /Agregar Negocio/i }),
          appPage.getByText(/Agregar Negocio/i),
        ],
        "Agregar Negocio option"
      );
      await clickAndWait(appPage, agregarNegocio);

      await waitForAnyVisible(
        appPage,
        [appPage.getByRole("heading", { name: /Crear Nuevo Negocio/i }), appPage.getByText(/Crear Nuevo Negocio/i)],
        "Crear Nuevo Negocio modal title"
      );

      const negocioNameInput = await waitForAnyVisible(
        appPage,
        [
          appPage.getByLabel(/Nombre del Negocio/i),
          appPage.getByRole("textbox", { name: /Nombre del Negocio/i }),
          appPage.getByPlaceholder(/Nombre del Negocio/i),
        ],
        "Nombre del Negocio input"
      );

      await waitForAnyVisible(
        appPage,
        [appPage.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)],
        "business usage text"
      );
      await waitForAnyVisible(
        appPage,
        [appPage.getByRole("button", { name: /Cancelar/i }), appPage.getByText(/^Cancelar$/i)],
        "Cancelar button"
      );
      await waitForAnyVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /Crear Negocio/i }),
          appPage.getByText(/Crear Negocio/i),
        ],
        "Crear Negocio button"
      );

      await takeCheckpoint(appPage, testInfo, "step-3-agregar-negocio-modal", true);

      await negocioNameInput.click();
      await negocioNameInput.fill("Negocio Prueba Automatización");
      const cancelar = await waitForAnyVisible(
        appPage,
        [appPage.getByRole("button", { name: /Cancelar/i }), appPage.getByText(/^Cancelar$/i)],
        "Cancelar modal button"
      );
      await clickAndWait(appPage, cancelar);
    },
  });

  await runValidation({
    appPage,
    testInfo,
    report,
    failures,
    field: "Administrar Negocios view",
    action: async () => {
      await ensureMenuExpanded(appPage);

      const miNegocio = await waitForAnyVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /Mi Negocio/i }),
          appPage.getByRole("link", { name: /Mi Negocio/i }),
          appPage.getByText(/Mi Negocio/i),
        ],
        "Mi Negocio menu for second expansion"
      );
      await clickAndWait(appPage, miNegocio);

      const administrarNegocios = await waitForAnyVisible(
        appPage,
        [
          appPage.getByRole("link", { name: /Administrar Negocios/i }),
          appPage.getByRole("button", { name: /Administrar Negocios/i }),
          appPage.getByText(/Administrar Negocios/i),
        ],
        "Administrar Negocios option"
      );
      await clickAndWait(appPage, administrarNegocios);

      await waitForAnyVisible(
        appPage,
        [appPage.getByRole("heading", { name: /Informaci[oó]n General/i }), appPage.getByText(/Informaci[oó]n General/i)],
        "Información General section"
      );
      await waitForAnyVisible(
        appPage,
        [appPage.getByRole("heading", { name: /Detalles de la Cuenta/i }), appPage.getByText(/Detalles de la Cuenta/i)],
        "Detalles de la Cuenta section"
      );
      await waitForAnyVisible(
        appPage,
        [appPage.getByRole("heading", { name: /Tus Negocios/i }), appPage.getByText(/Tus Negocios/i)],
        "Tus Negocios section"
      );
      await waitForAnyVisible(
        appPage,
        [appPage.getByRole("heading", { name: /Secci[oó]n Legal/i }), appPage.getByText(/Secci[oó]n Legal/i)],
        "Sección Legal section"
      );

      await takeCheckpoint(appPage, testInfo, "step-4-administrar-negocios-page", true);
    },
  });

  await runValidation({
    appPage,
    testInfo,
    report,
    failures,
    field: "Información General",
    action: async () => {
      if (expectedNameToken.length > 0) {
        await waitForAnyVisible(
          appPage,
          [appPage.getByText(new RegExp(expectedNameToken, "i")), appPage.getByText(/Nombre|Usuario/i)],
          "user name indicator"
        );
      }

      await waitForAnyVisible(
        appPage,
        [appPage.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)],
        "user email text"
      );
      await waitForAnyVisible(appPage, [appPage.getByText(/BUSINESS PLAN/i)], "business plan label");
      await waitForAnyVisible(
        appPage,
        [appPage.getByRole("button", { name: /Cambiar Plan/i }), appPage.getByText(/Cambiar Plan/i)],
        "Cambiar Plan button"
      );
    },
  });

  await runValidation({
    appPage,
    testInfo,
    report,
    failures,
    field: "Detalles de la Cuenta",
    action: async () => {
      await waitForAnyVisible(appPage, [appPage.getByText(/Cuenta creada/i)], "Cuenta creada label");
      await waitForAnyVisible(appPage, [appPage.getByText(/Estado activo/i)], "Estado activo label");
      await waitForAnyVisible(
        appPage,
        [appPage.getByText(/Idioma seleccionado/i)],
        "Idioma seleccionado label"
      );
    },
  });

  await runValidation({
    appPage,
    testInfo,
    report,
    failures,
    field: "Tus Negocios",
    action: async () => {
      const negociosHeading = await waitForAnyVisible(
        appPage,
        [appPage.getByRole("heading", { name: /Tus Negocios/i }), appPage.getByText(/Tus Negocios/i)],
        "Tus Negocios heading"
      );

      const businessList = negociosHeading
        .locator("xpath=ancestor::*[self::section or self::div][1]")
        .locator("li, [role='listitem'], tr, [data-testid*='business']");
      await expect(businessList.first()).toBeVisible({ timeout: 15000 });

      await waitForAnyVisible(
        appPage,
        [appPage.getByRole("button", { name: /Agregar Negocio/i }), appPage.getByText(/Agregar Negocio/i)],
        "Agregar Negocio button in account page"
      );

      await waitForAnyVisible(
        appPage,
        [appPage.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)],
        "business count text"
      );
    },
  });

  await runValidation({
    appPage,
    testInfo,
    report,
    failures,
    field: "Términos y Condiciones",
    action: async () => {
      const result = await validateLegalPage({
        appPage,
        contextPages: appPage.context().pages(),
        linkRegex: /T[eé]rminos y Condiciones/i,
        headingRegex: /T[eé]rminos y Condiciones/i,
        screenshotLabel: "step-8-terminos-y-condiciones",
        testInfo,
      });
      legalUrls["Términos y Condiciones"] = result.finalUrl;
      appPage = result.appPage;
    },
  });

  await runValidation({
    appPage,
    testInfo,
    report,
    failures,
    field: "Política de Privacidad",
    action: async () => {
      const result = await validateLegalPage({
        appPage,
        contextPages: appPage.context().pages(),
        linkRegex: /Pol[ií]tica de Privacidad/i,
        headingRegex: /Pol[ií]tica de Privacidad/i,
        screenshotLabel: "step-9-politica-de-privacidad",
        testInfo,
      });
      legalUrls["Política de Privacidad"] = result.finalUrl;
      appPage = result.appPage;
    },
  });

  const finalReport = {
    validations: report,
    legalUrls,
  };
  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json",
  });

  console.log("=== SaleADS Mi Negocio Final Report ===");
  for (const field of REPORT_FIELDS) {
    console.log(`${field}: ${report[field]}`);
  }
  console.log(`Términos y Condiciones URL: ${legalUrls["Términos y Condiciones"] ?? "N/A"}`);
  console.log(`Política de Privacidad URL: ${legalUrls["Política de Privacidad"] ?? "N/A"}`);

  expect(
    failures,
    failures.length === 0 ? "All validations passed." : `Validation failures:\n- ${failures.join("\n- ")}`
  ).toEqual([]);
});
