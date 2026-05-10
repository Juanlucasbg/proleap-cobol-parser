import { test, expect, type BrowserContext, type Locator, type Page } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informaci\u00f3n General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "T\u00e9rminos y Condiciones"
  | "Pol\u00edtica de Privacidad";

type ValidationReport = Record<ReportKey, StepStatus>;

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_LIMIT_TEXT_REGEX = /Tienes\s+2\s+de\s+3\s+negocios/i;

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function pickFirstVisible(candidates: Locator[], failureMessage: string): Promise<Locator> {
  for (const candidate of candidates) {
    const locator = candidate.first();
    const visible = await locator.isVisible({ timeout: 3_000 }).catch(() => false);
    if (visible) {
      return locator;
    }
  }

  throw new Error(failureMessage);
}

async function writeScreenshot(page: Page, checkpointPath: string, fullPage = false): Promise<void> {
  await waitForUi(page);
  await page.screenshot({ path: checkpointPath, fullPage });
}

async function validateLegalPage(
  context: BrowserContext,
  applicationPage: Page,
  linkNameRegex: RegExp,
  headingRegex: RegExp,
  screenshotPath: string
): Promise<string> {
  const link = await pickFirstVisible(
    [
      applicationPage.getByRole("link", { name: linkNameRegex }),
      applicationPage.getByRole("button", { name: linkNameRegex }),
      applicationPage.getByText(linkNameRegex),
    ],
    `Could not find legal link: ${String(linkNameRegex)}`
  );

  const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
  const sameTabNavigationPromise = applicationPage
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 12_000 })
    .catch(() => null);

  await link.click();

  const popup = await popupPromise;
  if (popup) {
    await waitForUi(popup);
    const popupHeading = await pickFirstVisible(
      [popup.getByRole("heading", { name: headingRegex }), popup.getByText(headingRegex)],
      `Could not find heading ${String(headingRegex)} in popup.`
    );
    await expect(popupHeading).toBeVisible({ timeout: 20_000 });
    await expect(popup.locator("p, li").first()).toBeVisible({ timeout: 20_000 });
    await writeScreenshot(popup, screenshotPath, true);
    const finalUrl = popup.url();
    await popup.close();
    await applicationPage.bringToFront();
    await waitForUi(applicationPage);
    return finalUrl;
  }

  await sameTabNavigationPromise;
  await waitForUi(applicationPage);
  const pageHeading = await pickFirstVisible(
    [applicationPage.getByRole("heading", { name: headingRegex }), applicationPage.getByText(headingRegex)],
    `Could not find heading ${String(headingRegex)} in current tab.`
  );
  await expect(pageHeading).toBeVisible({ timeout: 20_000 });
  await expect(applicationPage.locator("p, li").first()).toBeVisible({ timeout: 20_000 });
  await writeScreenshot(applicationPage, screenshotPath, true);
  const finalUrl = applicationPage.url();
  await applicationPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
  await waitForUi(applicationPage);
  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report: ValidationReport = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Informaci\u00f3n General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "T\u00e9rminos y Condiciones": "FAIL",
    "Pol\u00edtica de Privacidad": "FAIL",
  };

  const failures: Partial<Record<ReportKey, string>> = {};
  const legalUrls: { terminosYCondiciones: string | null; politicaDePrivacidad: string | null } = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null,
  };

  const artifactsDir = path.resolve(process.env.SALEADS_ARTIFACTS_DIR ?? "artifacts");
  fs.mkdirSync(artifactsDir, { recursive: true });
  const reportPath = path.resolve(artifactsDir, "saleads_mi_negocio_full_test_report.json");

  const evidence: Record<string, string> = {
    dashboardLoaded: path.resolve(artifactsDir, "step_1_dashboard_loaded.png"),
    miNegocioExpanded: path.resolve(artifactsDir, "step_2_mi_negocio_expanded.png"),
    agregarNegocioModal: path.resolve(artifactsDir, "step_3_agregar_negocio_modal.png"),
    administrarNegociosView: path.resolve(artifactsDir, "step_4_administrar_negocios_view.png"),
    terminosYCondiciones: path.resolve(artifactsDir, "step_8_terminos_y_condiciones.png"),
    politicaDePrivacidad: path.resolve(artifactsDir, "step_9_politica_de_privacidad.png"),
  };

  let applicationPage = page;

  const executeStep = async (key: ReportKey, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[key] = "PASS";
    } catch (error) {
      report[key] = "FAIL";
      failures[key] = error instanceof Error ? error.message : String(error);
    }
  };

  await executeStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL ?? process.env.SALEADS_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const signInButton = await pickFirstVisible(
      [
        page.getByRole("button", { name: /google|sign in|iniciar sesi[o\u00f3]n/i }),
        page.getByRole("link", { name: /google|sign in|iniciar sesi[o\u00f3]n/i }),
        page.locator("button, a").filter({ hasText: /google/i }),
      ],
      "Could not find a Google sign-in button."
    );

    const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
    await signInButton.click();
    await waitForUi(page);

    const authPage = (await popupPromise) ?? page;
    await waitForUi(authPage);

    const accountSelection = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    const accountVisible = await accountSelection.isVisible({ timeout: 5_000 }).catch(() => false);
    if (accountVisible) {
      await accountSelection.click();
    }

    if (authPage !== page) {
      const popupClosed = authPage.waitForEvent("close", { timeout: 25_000 }).then(() => true).catch(() => false);
      const popupBecameApp = expect(authPage.locator("aside, nav").first()).toBeVisible({ timeout: 25_000 })
        .then(() => true)
        .catch(() => false);
      const mainPageLoaded = expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 25_000 })
        .then(() => true)
        .catch(() => false);

      const [popupClosedResult, popupAsAppResult, mainLoadedResult] = await Promise.all([
        popupClosed,
        popupBecameApp,
        mainPageLoaded,
      ]);

      if (!popupClosedResult && popupAsAppResult && !mainLoadedResult) {
        applicationPage = authPage;
      }
    }

    await waitForUi(applicationPage);
    await expect(applicationPage.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
    await expect(applicationPage.locator("main, [role='main']").first()).toBeVisible({ timeout: 30_000 });

    await writeScreenshot(applicationPage, evidence.dashboardLoaded, true);
  });

  await executeStep("Mi Negocio menu", async () => {
    await expect(applicationPage.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });

    const negocioSection = await pickFirstVisible(
      [
        applicationPage.getByRole("button", { name: /^Negocio$/i }),
        applicationPage.getByRole("link", { name: /^Negocio$/i }),
        applicationPage.getByText(/^Negocio$/i),
      ],
      "Could not find 'Negocio' section in sidebar."
    );
    await negocioSection.click();
    await waitForUi(applicationPage);

    const miNegocioOption = await pickFirstVisible(
      [
        applicationPage.getByRole("button", { name: /Mi Negocio/i }),
        applicationPage.getByRole("link", { name: /Mi Negocio/i }),
        applicationPage.getByText(/Mi Negocio/i),
      ],
      "Could not find 'Mi Negocio' option in sidebar."
    );
    await miNegocioOption.click();
    await waitForUi(applicationPage);

    await expect(applicationPage.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20_000 });

    await writeScreenshot(applicationPage, evidence.miNegocioExpanded);
  });

  await executeStep("Agregar Negocio modal", async () => {
    const agregarNegocioLink = await pickFirstVisible(
      [
        applicationPage.getByRole("button", { name: /Agregar Negocio/i }),
        applicationPage.getByRole("link", { name: /Agregar Negocio/i }),
        applicationPage.getByText(/Agregar Negocio/i),
      ],
      "Could not find 'Agregar Negocio' option."
    );

    await agregarNegocioLink.click();
    await waitForUi(applicationPage);

    const modalTitle = applicationPage.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible({ timeout: 20_000 });
    const nombreNegocioField = await pickFirstVisible(
      [applicationPage.getByLabel(/Nombre del Negocio/i), applicationPage.getByPlaceholder(/Nombre del Negocio/i)],
      "Could not find 'Nombre del Negocio' field."
    );
    await expect(nombreNegocioField).toBeVisible({ timeout: 20_000 });

    await expect(applicationPage.getByText(BUSINESS_LIMIT_TEXT_REGEX).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 20_000 });
    await writeScreenshot(applicationPage, evidence.agregarNegocioModal);

    const businessNameField = await pickFirstVisible(
      [applicationPage.getByLabel(/Nombre del Negocio/i), applicationPage.getByPlaceholder(/Nombre del Negocio/i)],
      "Could not locate 'Nombre del Negocio' input for optional action."
    );
    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatizacion");

    await applicationPage.getByRole("button", { name: /Cancelar/i }).first().click();
    await expect(modalTitle).toBeHidden({ timeout: 10_000 });
    await waitForUi(applicationPage);
  });

  await executeStep("Administrar Negocios view", async () => {
    const administrarVisible = await applicationPage
      .getByText(/Administrar Negocios/i)
      .first()
      .isVisible({ timeout: 2_000 })
      .catch(() => false);

    if (!administrarVisible) {
      const miNegocioOption = await pickFirstVisible(
        [
          applicationPage.getByRole("button", { name: /Mi Negocio/i }),
          applicationPage.getByRole("link", { name: /Mi Negocio/i }),
          applicationPage.getByText(/Mi Negocio/i),
        ],
        "Could not re-open 'Mi Negocio' before navigating to Administrar Negocios."
      );
      await miNegocioOption.click();
      await waitForUi(applicationPage);
    }

    const administrarNegocios = await pickFirstVisible(
      [
        applicationPage.getByRole("button", { name: /Administrar Negocios/i }),
        applicationPage.getByRole("link", { name: /Administrar Negocios/i }),
        applicationPage.getByText(/Administrar Negocios/i),
      ],
      "Could not find 'Administrar Negocios' option."
    );
    await administrarNegocios.click();
    await waitForUi(applicationPage);

    await expect(applicationPage.getByText(/Informaci[o\u00f3]n General/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByText(/Secci[o\u00f3]n Legal/i).first()).toBeVisible({ timeout: 20_000 });

    await writeScreenshot(applicationPage, evidence.administrarNegociosView, true);
  });

  await executeStep("Informaci\u00f3n General", async () => {
    await expect(applicationPage.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByText(/@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/).first()).toBeVisible({ timeout: 20_000 });

    const visibleTexts = await applicationPage.locator("h1, h2, h3, p, span, div").allTextContents();
    const hasLikelyUserName = visibleTexts.some((rawText) => {
      const text = rawText.trim();
      return /[A-Za-z]{2,}\s+[A-Za-z]{2,}/.test(text) && !text.includes("@") && !/BUSINESS PLAN|Cambiar Plan/i.test(text);
    });

    expect(hasLikelyUserName, "Could not detect a visible user name in Informacion General section.").toBeTruthy();
  });

  await executeStep("Detalles de la Cuenta", async () => {
    await expect(applicationPage.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await executeStep("Tus Negocios", async () => {
    await expect(applicationPage.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByText(BUSINESS_LIMIT_TEXT_REGEX).first()).toBeVisible({ timeout: 20_000 });
    await expect(applicationPage.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 20_000 });

    const businessRow = applicationPage.locator("table tbody tr, [role='listitem'], .business-card").first();
    await expect(businessRow).toBeVisible({ timeout: 20_000 });
  });

  await executeStep("T\u00e9rminos y Condiciones", async () => {
    legalUrls.terminosYCondiciones = await validateLegalPage(
      context,
      applicationPage,
      /T[e\u00e9]rminos y Condiciones/i,
      /T[e\u00e9]rminos y Condiciones/i,
      evidence.terminosYCondiciones
    );
  });

  await executeStep("Pol\u00edtica de Privacidad", async () => {
    legalUrls.politicaDePrivacidad = await validateLegalPage(
      context,
      applicationPage,
      /Pol[i\u00ed]tica de Privacidad/i,
      /Pol[i\u00ed]tica de Privacidad/i,
      evidence.politicaDePrivacidad
    );
  });

  const finalPayload = {
    name: "saleads_mi_negocio_full_test",
    generatedAtUtc: new Date().toISOString(),
    report,
    evidence,
    legalUrls,
    failures,
  };

  fs.writeFileSync(reportPath, JSON.stringify(finalPayload, null, 2), "utf-8");

  await test.info().attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedSteps = Object.entries(report)
    .filter(([, value]) => value === "FAIL")
    .map(([key]) => key);

  expect(
    failedSteps,
    failedSteps.length > 0
      ? `Failed steps: ${failedSteps.join(", ")}. See JSON report at ${reportPath}.`
      : "All workflow steps passed."
  ).toEqual([]);
});
