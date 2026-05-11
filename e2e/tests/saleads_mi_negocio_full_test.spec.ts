import { expect, Locator, Page, test, TestInfo } from "@playwright/test";

type ResultStatus = "PASS" | "FAIL";

type ValidationResult = {
  status: ResultStatus;
  details?: string;
};

type ValidationReport = Record<string, ValidationResult>;

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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function emptyReport(): ValidationReport {
  return REPORT_FIELDS.reduce<ValidationReport>((acc, field) => {
    acc[field] = { status: "FAIL", details: "Not executed" };
    return acc;
  }, {});
}

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => {
    // Some SPA views keep polling; domcontentloaded + small settle delay is enough.
  });
  await page.waitForTimeout(500);
}

async function takeCheckpoint(
  page: Page,
  testInfo: TestInfo,
  checkpointName: string,
  fullPage = false
): Promise<void> {
  const normalizedName = checkpointName.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const path = testInfo.outputPath(`${normalizedName}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(checkpointName, { path, contentType: "image/png" });
}

async function firstVisible(candidates: Locator[], timeoutMs = 15_000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  for (const locator of candidates) {
    const remaining = Math.max(deadline - Date.now(), 250);
    const becameVisible = await locator
      .first()
      .waitFor({ state: "visible", timeout: remaining })
      .then(() => true)
      .catch(() => false);
    if (becameVisible) {
      return locator.first();
    }
  }

  throw new Error("Could not find a visible element from candidate locators.");
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function selectGoogleAccountIfPrompted(targetPage: Page): Promise<void> {
  const accountLocator = await firstVisible(
    [
      targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
      targetPage.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
      targetPage.getByRole("link", { name: GOOGLE_ACCOUNT_EMAIL })
    ],
    8_000
  ).catch(() => null);

  if (accountLocator) {
    await accountLocator.click();
    await targetPage.waitForTimeout(500);
  }
}

async function findSectionByTitle(page: Page, titleRegex: RegExp): Promise<Locator> {
  const heading = await firstVisible(
    [
      page.getByRole("heading", { name: titleRegex }),
      page.getByText(titleRegex).filter({ visible: true })
    ],
    20_000
  );

  const sectionContainer = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  await expect(sectionContainer).toBeVisible();
  return sectionContainer;
}

async function validateLegalLink(
  appPage: Page,
  linkText: RegExp,
  expectedHeading: RegExp,
  checkpointName: string,
  testInfo: TestInfo
): Promise<string> {
  const legalLink = await firstVisible(
    [
      appPage.getByRole("link", { name: linkText }),
      appPage.getByText(linkText).filter({ visible: true })
    ],
    12_000
  );

  const originalUrl = appPage.url();
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickAndWait(legalLink, appPage);

  const popupPage = await popupPromise;
  const targetPage = popupPage ?? appPage;
  await waitForUi(targetPage);

  const heading = await firstVisible(
    [
      targetPage.getByRole("heading", { name: expectedHeading }),
      targetPage.getByText(expectedHeading).filter({ visible: true })
    ],
    20_000
  );

  await expect(heading).toBeVisible();
  await expect(targetPage.locator("p, li, article, section").first()).toBeVisible();
  await takeCheckpoint(targetPage, testInfo, checkpointName, true);

  const finalUrl = targetPage.url();

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== originalUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(appPage);
  }

  return finalUrl;
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login and validate Mi Negocio complete workflow", async ({ page }, testInfo) => {
    const report = emptyReport();
    const legalUrls: Record<"terms" | "privacy", string | null> = { terms: null, privacy: null };

    // Step 1: Login with Google.
    try {
      const baseURL = process.env.SALEADS_BASE_URL;
      if (baseURL) {
        await page.goto(baseURL, { waitUntil: "domcontentloaded" });
        await waitForUi(page);
      }

      const loginButton = await firstVisible(
        [
          page.getByRole("button", { name: /google|iniciar sesi[oó]n|sign in/i }),
          page.getByRole("link", { name: /google|iniciar sesi[oó]n|sign in/i }),
          page.getByText(/google|iniciar sesi[oó]n|sign in/i).filter({ visible: true })
        ],
        20_000
      );

      const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(loginButton, page);
      const popup = await popupPromise;

      if (popup) {
        await waitForUi(popup);
        await selectGoogleAccountIfPrompted(popup);
      } else if (page.url().includes("accounts.google.com")) {
        await selectGoogleAccountIfPrompted(page);
      }

      await waitForUi(page);

      const sidebar = await firstVisible(
        [
          page.locator("aside").filter({ hasText: /negocio|mi negocio|dashboard/i }),
          page.getByRole("navigation").filter({ hasText: /negocio|mi negocio|dashboard/i }),
          page.getByText(/mi negocio|negocio/i).locator("xpath=ancestor::*[self::aside or self::nav][1]")
        ],
        35_000
      );

      await expect(sidebar).toBeVisible();
      await expect(page.locator("main, [role='main']").first()).toBeVisible();
      await takeCheckpoint(page, testInfo, "01-dashboard-loaded");

      report["Login"] = { status: "PASS" };
    } catch (error) {
      report["Login"] = { status: "FAIL", details: toErrorMessage(error) };
    }

    // Step 2: Open Mi Negocio menu.
    try {
      const negocioSection = await firstVisible(
        [
          page.getByText(/^Negocio$/i).filter({ visible: true }),
          page.getByRole("button", { name: /Negocio/i }),
          page.getByRole("link", { name: /Negocio/i })
        ],
        15_000
      );

      await clickAndWait(negocioSection, page);

      const miNegocio = await firstVisible(
        [
          page.getByRole("link", { name: /Mi Negocio/i }),
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByText(/^Mi Negocio$/i).filter({ visible: true })
        ],
        15_000
      );

      await clickAndWait(miNegocio, page);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await takeCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");

      report["Mi Negocio menu"] = { status: "PASS" };
    } catch (error) {
      report["Mi Negocio menu"] = { status: "FAIL", details: toErrorMessage(error) };
    }

    // Step 3: Validate Agregar Negocio modal.
    try {
      const agregarNegocio = await firstVisible(
        [
          page.getByRole("button", { name: /Agregar Negocio/i }),
          page.getByRole("link", { name: /Agregar Negocio/i }),
          page.getByText(/^Agregar Negocio$/i).filter({ visible: true })
        ],
        15_000
      );

      await clickAndWait(agregarNegocio, page);

      const modal = await firstVisible(
        [
          page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
          page.locator("[role='dialog'], .modal, .MuiDialog-root").filter({
            hasText: /Crear Nuevo Negocio/i
          })
        ],
        15_000
      );

      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible();
      await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
      await takeCheckpoint(page, testInfo, "03-crear-nuevo-negocio-modal");

      await modal.getByLabel(/Nombre del Negocio/i).fill("Negocio Prueba Automatización");
      await modal.getByRole("button", { name: /Cancelar/i }).click();
      await waitForUi(page);

      report["Agregar Negocio modal"] = { status: "PASS" };
    } catch (error) {
      report["Agregar Negocio modal"] = { status: "FAIL", details: toErrorMessage(error) };
    }

    // Step 4: Open Administrar Negocios.
    try {
      const administrarNegocios = await firstVisible(
        [
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByRole("button", { name: /Administrar Negocios/i }),
          page.getByText(/^Administrar Negocios$/i).filter({ visible: true })
        ],
        8_000
      ).catch(async () => {
        const miNegocio = await firstVisible(
          [
            page.getByRole("link", { name: /Mi Negocio/i }),
            page.getByRole("button", { name: /Mi Negocio/i }),
            page.getByText(/^Mi Negocio$/i).filter({ visible: true })
          ],
          10_000
        );
        await clickAndWait(miNegocio, page);

        return firstVisible(
          [
            page.getByRole("link", { name: /Administrar Negocios/i }),
            page.getByRole("button", { name: /Administrar Negocios/i }),
            page.getByText(/^Administrar Negocios$/i).filter({ visible: true })
          ],
          12_000
        );
      });

      await clickAndWait(administrarNegocios, page);

      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
      await takeCheckpoint(page, testInfo, "04-administrar-negocios-view", true);

      report["Administrar Negocios view"] = { status: "PASS" };
    } catch (error) {
      report["Administrar Negocios view"] = { status: "FAIL", details: toErrorMessage(error) };
    }

    // Step 5: Validate Información General.
    try {
      const infoGeneralSection = await findSectionByTitle(page, /Informaci[oó]n General/i);
      const content = await infoGeneralSection.innerText();
      const emailMatch = content.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);

      expect(emailMatch).not.toBeNull();
      expect(content.replace(emailMatch?.[0] ?? "", "").trim().length).toBeGreaterThan(10);
      await expect(infoGeneralSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(infoGeneralSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      report["Información General"] = { status: "PASS" };
    } catch (error) {
      report["Información General"] = { status: "FAIL", details: toErrorMessage(error) };
    }

    // Step 6: Validate Detalles de la Cuenta.
    try {
      const accountDetailsSection = await findSectionByTitle(page, /Detalles de la Cuenta/i);
      await expect(accountDetailsSection.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(accountDetailsSection.getByText(/Estado activo/i)).toBeVisible();
      await expect(accountDetailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();

      report["Detalles de la Cuenta"] = { status: "PASS" };
    } catch (error) {
      report["Detalles de la Cuenta"] = { status: "FAIL", details: toErrorMessage(error) };
    }

    // Step 7: Validate Tus Negocios.
    try {
      const businessesSection = await findSectionByTitle(page, /Tus Negocios/i);
      await expect(businessesSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
      await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

      const listCandidate = businessesSection.locator(
        "ul li, ol li, [role='listitem'], tr, [role='row'], [class*='business'], [class*='negocio']"
      );
      const hasList = (await listCandidate.count()) > 0;
      expect(hasList).toBeTruthy();

      report["Tus Negocios"] = { status: "PASS" };
    } catch (error) {
      report["Tus Negocios"] = { status: "FAIL", details: toErrorMessage(error) };
    }

    // Step 8: Validate Términos y Condiciones.
    try {
      legalUrls.terms = await validateLegalLink(
        page,
        /T[eé]rminos y Condiciones/i,
        /T[eé]rminos y Condiciones/i,
        "05-terminos-y-condiciones",
        testInfo
      );

      report["Términos y Condiciones"] = { status: "PASS" };
    } catch (error) {
      report["Términos y Condiciones"] = { status: "FAIL", details: toErrorMessage(error) };
    }

    // Step 9: Validate Política de Privacidad.
    try {
      legalUrls.privacy = await validateLegalLink(
        page,
        /Pol[ií]tica de Privacidad/i,
        /Pol[ií]tica de Privacidad/i,
        "06-politica-de-privacidad",
        testInfo
      );

      report["Política de Privacidad"] = { status: "PASS" };
    } catch (error) {
      report["Política de Privacidad"] = { status: "FAIL", details: toErrorMessage(error) };
    }

    // Step 10: Final report attachment.
    const reportPayload = {
      test: "saleads_mi_negocio_full_test",
      results: report,
      evidence: {
        termsUrl: legalUrls.terms,
        privacyUrl: legalUrls.privacy
      }
    };

    await testInfo.attach("final-report.json", {
      body: Buffer.from(JSON.stringify(reportPayload, null, 2), "utf-8"),
      contentType: "application/json"
    });

    const failed = Object.entries(report).filter(([, value]) => value.status === "FAIL");
    expect(
      failed,
      `Validation failures:\n${JSON.stringify(
        {
          failures: failed,
          evidence: reportPayload.evidence
        },
        null,
        2
      )}`
    ).toHaveLength(0);
  });
});
