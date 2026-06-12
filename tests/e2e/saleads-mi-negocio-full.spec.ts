import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

type ReportStatus = "PASS" | "FAIL";

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
type ValidationReport = Record<ReportField, ReportStatus>;

function createReport(): ValidationReport {
  return REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = "FAIL";
      return acc;
    },
    {} as ValidationReport
  );
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function captureCheckpoint(testInfo: TestInfo, page: Page, name: string, fullPage = false): Promise<void> {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, { path: screenshotPath, contentType: "image/png" });
}

async function firstVisibleLocator(candidates: Locator[], timeoutMs = 20_000): Promise<Locator> {
  const started = Date.now();

  while (Date.now() - started < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();

      try {
        await locator.waitFor({ state: "visible", timeout: 1_200 });
        return locator;
      } catch {
        // Try next locator candidate.
      }
    }
  }

  throw new Error("No visible element found for the requested UI target.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => undefined);
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function selectGoogleAccountIfPrompted(page: Page, email: string): Promise<void> {
  const accountCandidate = await firstVisibleLocator(
    [
      page.getByRole("button", { name: new RegExp(email, "i") }),
      page.getByRole("link", { name: new RegExp(email, "i") }),
      page.getByText(new RegExp(email, "i"))
    ],
    6_000
  ).catch(() => null);

  if (accountCandidate) {
    await clickAndWait(page, accountCandidate);
  }
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const adminOption = page.getByText(/^Administrar Negocios$/i).first();
  const alreadyExpanded = await adminOption.isVisible().catch(() => false);

  if (alreadyExpanded) {
    return;
  }

  const negocioSection = await firstVisibleLocator(
    [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ],
    15_000
  );

  await clickAndWait(page, negocioSection);

  const miNegocioOption = await firstVisibleLocator(
    [
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i)
    ],
    15_000
  );

  await clickAndWait(page, miNegocioOption);
}

async function runValidation(
  field: ReportField,
  report: ValidationReport,
  failures: string[],
  action: () => Promise<void>
): Promise<void> {
  try {
    await action();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    failures.push(`${field}: ${(error as Error).message}`);
  }
}

async function validateLegalPage(
  page: Page,
  testInfo: TestInfo,
  linkText: RegExp,
  headingText: RegExp,
  screenshotName: string
): Promise<string> {
  const appUrlBeforeNavigation = page.url();
  const legalLink = await firstVisibleLocator(
    [
      page.getByRole("link", { name: linkText }),
      page.getByRole("button", { name: linkText }),
      page.getByText(linkText)
    ],
    15_000
  );

  const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
  await clickAndWait(page, legalLink);

  const popup = await popupPromise;
  const targetPage = popup ?? page;
  await waitForUi(targetPage);

  const heading = await firstVisibleLocator(
    [targetPage.getByRole("heading", { name: headingText }), targetPage.getByText(headingText)],
    30_000
  );
  await expect(heading).toBeVisible();

  const bodyText = (await targetPage.locator("body").innerText()).trim();
  if (bodyText.length < 120) {
    throw new Error("Legal content appears too short to validate.");
  }

  await captureCheckpoint(testInfo, targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close().catch(() => undefined);
    await page.bringToFront().catch(() => undefined);
  } else if (finalUrl !== appUrlBeforeNavigation) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const failures: string[] = [];
  const evidenceUrls: Record<string, string> = {};

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (page.url() === "about:blank") {
    if (!loginUrl) {
      throw new Error(
        "No active SaleADS login page found. Open the login page first or set SALEADS_LOGIN_URL."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);

  await runValidation("Login", report, failures, async () => {
    const googleLoginTrigger = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /Google|Sign in|Iniciar sesi[oó]n/i }),
        page.getByRole("link", { name: /Google|Sign in|Iniciar sesi[oó]n/i }),
        page.getByText(/Sign in with Google|Iniciar sesi[oó]n con Google/i),
        page.locator("button:has-text('Google'), a:has-text('Google')")
      ],
      45_000
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, googleLoginTrigger);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await waitForUi(googlePopup);
      await selectGoogleAccountIfPrompted(googlePopup, "juanlucasbarbiergarzon@gmail.com");
      await googlePopup.waitForEvent("close", { timeout: 20_000 }).catch(() => undefined);
    } else {
      await selectGoogleAccountIfPrompted(page, "juanlucasbarbiergarzon@gmail.com");
    }

    const appMain = await firstVisibleLocator(
      [page.getByRole("main"), page.locator("main"), page.locator("[data-testid*='dashboard']")],
      90_000
    );
    await expect(appMain).toBeVisible();

    const leftSidebar = await firstVisibleLocator(
      [
        page.getByRole("navigation"),
        page.locator("aside"),
        page.locator("[data-testid*='sidebar'], [class*='sidebar']")
      ],
      60_000
    );
    await expect(leftSidebar).toBeVisible();

    await captureCheckpoint(testInfo, page, "01-dashboard-loaded", true);
  });

  await runValidation("Mi Negocio menu", report, failures, async () => {
    await ensureMiNegocioExpanded(page);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();
    await captureCheckpoint(testInfo, page, "02-mi-negocio-expanded");
  });

  await runValidation("Agregar Negocio modal", report, failures, async () => {
    const agregarNegocioOption = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      15_000
    );

    await clickAndWait(page, agregarNegocioOption);

    const modal = await firstVisibleLocator(
      [
        page.getByRole("dialog", { name: /Crear Nuevo Negocio/i }),
        page.locator("[role='dialog']").filter({ hasText: /Crear Nuevo Negocio/i })
      ],
      20_000
    );
    await expect(modal).toBeVisible();
    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const nameInput = await firstVisibleLocator(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[name*='negocio' i], input[id*='negocio' i], input[placeholder*='Negocio' i]")
      ],
      10_000
    );
    await expect(nameInput).toBeVisible();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await captureCheckpoint(testInfo, page, "03-crear-negocio-modal");

    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatizacion");

    const cancelButton = await firstVisibleLocator(
      [page.getByRole("button", { name: /Cancelar/i }), page.getByText(/^Cancelar$/i)],
      5_000
    );
    await clickAndWait(page, cancelButton);
    await expect(modal).toBeHidden({ timeout: 10_000 });
  });

  await runValidation("Administrar Negocios view", report, failures, async () => {
    await ensureMiNegocioExpanded(page);

    const administrarNegocios = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ],
      15_000
    );
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();

    await captureCheckpoint(testInfo, page, "04-administrar-negocios-page", true);
  });

  await runValidation("Información General", report, failures, async () => {
    const infoGeneralSection = await firstVisibleLocator(
      [
        page.locator("section, div").filter({ hasText: /Informaci[oó]n General/i }),
        page.getByText(/Informaci[oó]n General/i)
      ],
      15_000
    );
    await expect(infoGeneralSection).toBeVisible();

    const emailText = await firstVisibleLocator(
      [
        infoGeneralSection.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/),
        page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)
      ],
      10_000
    );
    await expect(emailText).toBeVisible();

    const infoGeneralText = (await infoGeneralSection.innerText())
      .split(/\n+/)
      .map((line) => line.trim())
      .filter(Boolean);
    const hasLikelyUserName = infoGeneralText.some((line) =>
      /^[A-Za-zÁÉÍÓÚÑáéíóúñ]+(?:\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]+)+$/.test(line) &&
      !/informaci[oó]n general|business plan|cambiar plan/i.test(line)
    );
    if (!hasLikelyUserName) {
      throw new Error("Could not validate visible user name in Información General.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runValidation("Detalles de la Cuenta", report, failures, async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runValidation("Tus Negocios", report, failures, async () => {
    const tusNegociosSection = await firstVisibleLocator(
      [
        page.locator("section, div").filter({ hasText: /Tus Negocios/i }),
        page.getByText(/Tus Negocios/i)
      ],
      10_000
    );
    await expect(tusNegociosSection).toBeVisible();

    const businessList = await firstVisibleLocator(
      [
        tusNegociosSection.locator("li"),
        tusNegociosSection.locator("[role='listitem']"),
        tusNegociosSection.locator("table tbody tr"),
        tusNegociosSection.locator("[data-testid*='business'], [class*='business']")
      ],
      10_000
    ).catch(() => null);

    if (!businessList) {
      const sectionText = (await tusNegociosSection.innerText()).trim();
      const lineCount = sectionText
        .split(/\n+/)
        .map((line) => line.trim())
        .filter(Boolean).length;
      if (lineCount < 3) {
        throw new Error("Business list is not clearly visible in 'Tus Negocios'.");
      }
    } else {
      await expect(businessList).toBeVisible();
    }

    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await runValidation("Términos y Condiciones", report, failures, async () => {
    const termsUrl = await validateLegalPage(
      page,
      testInfo,
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "08-terminos-y-condiciones"
    );
    evidenceUrls["Términos y Condiciones"] = termsUrl;
  });

  await runValidation("Política de Privacidad", report, failures, async () => {
    const privacyUrl = await validateLegalPage(
      page,
      testInfo,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "09-politica-de-privacidad"
    );
    evidenceUrls["Política de Privacidad"] = privacyUrl;
  });

  const finalReport = {
    generatedAt: new Date().toISOString(),
    report,
    legalUrls: evidenceUrls
  };

  await testInfo.attach("10-final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });

  if (failures.length > 0) {
    throw new Error(`SaleADS Mi Negocio workflow failed validations:\n- ${failures.join("\n- ")}`);
  }
});
