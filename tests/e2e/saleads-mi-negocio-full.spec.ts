import { expect, type Locator, type Page, test, type TestInfo } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

type ReportStatus = "PASS" | "FAIL";

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

type WorkflowReport = Record<ReportField, ReportStatus>;

const INITIAL_REPORT: WorkflowReport = {
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

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await page.waitForTimeout(500);
}

async function firstVisible(locators: Locator[], timeoutMs = 15_000): Promise<Locator> {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of locators) {
      if (await locator.first().isVisible().catch(() => false)) {
        return locator.first();
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error("No visible locator matched within timeout.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function screenshot(testInfo: TestInfo, page: Page, name: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage,
  });
}

async function runStep(
  report: WorkflowReport,
  failures: string[],
  field: ReportField,
  label: string,
  callback: () => Promise<void>,
): Promise<void> {
  try {
    await test.step(label, callback);
    report[field] = "PASS";
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    failures.push(`${label}: ${message}`);
    report[field] = "FAIL";
  }
}

async function getSidebar(page: Page): Promise<Locator> {
  return firstVisible([
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator("[class*='sidebar']").first(),
  ]);
}

async function getByVisibleText(page: Page, pattern: RegExp): Promise<Locator> {
  return firstVisible([
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByRole("menuitem", { name: pattern }),
    page.getByText(pattern),
  ]);
}

async function clickLegalLinkAndValidate(
  page: Page,
  testInfo: TestInfo,
  linkPattern: RegExp,
  headingPattern: RegExp,
  screenshotName: string,
): Promise<string> {
  const link = await getByVisibleText(page, linkPattern);
  const popupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
  const previousUrl = page.url();

  await clickAndWait(page, link);

  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    const heading = await firstVisible(
      [popup.getByRole("heading", { name: headingPattern }), popup.getByText(headingPattern)],
      15_000,
    );
    await expect(heading).toBeVisible();

    const legalText = (await popup.locator("body").innerText()).trim();
    expect(legalText.length).toBeGreaterThan(30);

    await screenshot(testInfo, popup, screenshotName, true);
    const popupUrl = popup.url();
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return popupUrl;
  }

  const heading = await firstVisible(
    [page.getByRole("heading", { name: headingPattern }), page.getByText(headingPattern)],
    15_000,
  );
  await expect(heading).toBeVisible();
  const legalText = (await page.locator("body").innerText()).trim();
  expect(legalText.length).toBeGreaterThan(30);

  await screenshot(testInfo, page, screenshotName, true);
  const finalUrl = page.url();

  if (finalUrl !== previousUrl) {
    await page.goBack().catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("SaleADS Mi Negocio full workflow", async ({ page, baseURL }, testInfo) => {
  test.setTimeout(240_000);

  const report: WorkflowReport = { ...INITIAL_REPORT };
  const failures: string[] = [];
  const legalUrls: Record<string, string> = {};

  await runStep(report, failures, "Login", "Step 1 - Login with Google", async () => {
    if (page.url() === "about:blank") {
      if (!baseURL) {
        throw new Error(
          "No SaleADS page is open. Set SALEADS_BASE_URL so the test can open the login page dynamically.",
        );
      }

      await page.goto(baseURL, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    const alreadyLoggedIn = await getSidebar(page)
      .then(async (sidebar) => (await sidebar.isVisible().catch(() => false)) && (await page.getByText(/negocio/i).first().isVisible().catch(() => false)))
      .catch(() => false);

    if (alreadyLoggedIn) {
      await screenshot(testInfo, page, "01-dashboard-loaded.png");
      return;
    }

    const loginButton = await getByVisibleText(page, /google/i);
    const authPopupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
    await clickAndWait(page, loginButton);

    const authPopup = await authPopupPromise;

    if (authPopup) {
      await authPopup.waitForLoadState("domcontentloaded");
      const accountOption = authPopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }
      await authPopup.waitForClose({ timeout: 30_000 }).catch(() => undefined);
      await page.bringToFront();
    } else {
      const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(page, accountOption);
      }
    }

    await waitForUi(page);

    const sidebar = await getSidebar(page);
    await expect(sidebar).toBeVisible();
    await expect(page.getByText(/negocio/i)).toBeVisible();
    await screenshot(testInfo, page, "01-dashboard-loaded.png");
  });

  await runStep(report, failures, "Mi Negocio menu", "Step 2 - Open Mi Negocio menu", async () => {
    const sidebar = await getSidebar(page);
    await expect(sidebar).toBeVisible();
    const negocioSection = await getByVisibleText(page, /^negocio$/i);
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await getByVisibleText(page, /mi negocio/i);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();
    await screenshot(testInfo, page, "02-mi-negocio-expanded.png");
  });

  await runStep(report, failures, "Agregar Negocio modal", "Step 3 - Validate Agregar Negocio modal", async () => {
    const addBusinessButton = await getByVisibleText(page, /agregar negocio/i);
    await clickAndWait(page, addBusinessButton);

    const modalTitle = await firstVisible([
      page.getByRole("heading", { name: /crear nuevo negocio/i }),
      page.getByText(/crear nuevo negocio/i),
    ]);

    await expect(modalTitle).toBeVisible();

    const businessNameInput = await firstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[name*='negocio']").first(),
    ]);

    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();
    await screenshot(testInfo, page, "03-crear-nuevo-negocio-modal.png");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");

    const cancelButton = page.getByRole("button", { name: /cancelar/i });
    await clickAndWait(page, cancelButton);
    await expect(modalTitle).not.toBeVisible();
  });

  await runStep(
    report,
    failures,
    "Administrar Negocios view",
    "Step 4 - Open Administrar Negocios and validate sections",
    async () => {
      const adminOptionVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);

      if (!adminOptionVisible) {
        const miNegocioOption = await getByVisibleText(page, /mi negocio/i);
        await clickAndWait(page, miNegocioOption);
      }

      const administrarNegocios = await getByVisibleText(page, /administrar negocios/i);
      await clickAndWait(page, administrarNegocios);

      await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();
      await screenshot(testInfo, page, "04-administrar-negocios-account-page.png", true);
    },
  );

  await runStep(report, failures, "Información General", "Step 5 - Validate Información General", async () => {
    const bodyText = await page.locator("body").innerText();
    expect(bodyText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const lines = bodyText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    const likelyName = lines.find(
      (line) =>
        /^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ' -]{2,}$/.test(line) &&
        !/informaci[oó]n general|business plan|cambiar plan|detalles de la cuenta|tus negocios|secci[oó]n legal|@/i.test(line),
    );

    expect(likelyName, "Expected a visible user name in Información General.").toBeTruthy();
  });

  await runStep(report, failures, "Detalles de la Cuenta", "Step 6 - Validate Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep(report, failures, "Tus Negocios", "Step 7 - Validate Tus Negocios", async () => {
    const businessListCandidates = [
      page.locator("li"),
      page.locator("[role='row']"),
      page.locator("[class*='business']"),
    ];

    const businessListVisible = await firstVisible(businessListCandidates, 8_000).catch(() => null);
    expect(businessListVisible).toBeTruthy();
    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
  });

  await runStep(
    report,
    failures,
    "Términos y Condiciones",
    "Step 8 - Validate Términos y Condiciones",
    async () => {
      const termsUrl = await clickLegalLinkAndValidate(
        page,
        testInfo,
        /t[eé]rminos y condiciones/i,
        /t[eé]rminos y condiciones/i,
        "05-terminos-y-condiciones.png",
      );

      legalUrls["Términos y Condiciones"] = termsUrl;
    },
  );

  await runStep(
    report,
    failures,
    "Política de Privacidad",
    "Step 9 - Validate Política de Privacidad",
    async () => {
      const privacyUrl = await clickLegalLinkAndValidate(
        page,
        testInfo,
        /pol[ií]tica de privacidad/i,
        /pol[ií]tica de privacidad/i,
        "06-politica-de-privacidad.png",
      );

      legalUrls["Política de Privacidad"] = privacyUrl;
    },
  );

  await test.step("Step 10 - Final report", async () => {
    const finalReport = {
      ...report,
      legalUrls,
    };

    await testInfo.attach("mi-negocio-final-report.json", {
      body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
      contentType: "application/json",
    });
  });

  if (failures.length > 0) {
    throw new Error(`Workflow completed with failures:\n- ${failures.join("\n- ")}`);
  }
});
