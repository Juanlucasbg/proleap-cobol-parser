import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";

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
type ReportStatus = "PASS" | "FAIL";

const waitForUiLoad = async (page: Page): Promise<void> => {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
  await page.waitForTimeout(500);
};

const slug = (value: string): string =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();

const attachUrl = async (testInfo: TestInfo, name: string, url: string): Promise<void> => {
  await testInfo.attach(name, {
    body: Buffer.from(url, "utf-8"),
    contentType: "text/plain"
  });
};

const captureCheckpoint = async (
  testInfo: TestInfo,
  page: Page,
  name: string,
  fullPage = true
): Promise<void> => {
  const screenshotDir = path.join(testInfo.outputDir, "saleads-mi-negocio");
  await mkdir(screenshotDir, { recursive: true });

  const filePath = path.join(screenshotDir, `${Date.now()}-${slug(name)}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, { path: filePath, contentType: "image/png" });
};

const pickFirstVisible = async (candidates: Locator[], timeoutMs: number, message: string): Promise<Locator> => {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(message);
};

const clickAndWait = async (page: Page, locator: Locator): Promise<void> => {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiLoad(page);
};

const sectionWithText = (page: Page, headingPattern: RegExp): Locator => {
  return page.locator("section, article, div").filter({ has: page.getByText(headingPattern).first() }).first();
};

const ensureLoginContext = async (page: Page): Promise<void> => {
  if (page.url() !== "about:blank") {
    await waitForUiLoad(page);
    return;
  }

  const runtimeUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (!runtimeUrl) {
    throw new Error(
      "Browser started at about:blank. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL) for the current environment."
    );
  }

  await page.goto(runtimeUrl, { waitUntil: "domcontentloaded" });
  await waitForUiLoad(page);
};

const validateLegalDocument = async (
  page: Page,
  testInfo: TestInfo,
  linkText: string,
  headingPattern: RegExp
): Promise<string> => {
  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const link = await pickFirstVisible(
    [
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByRole("button", { name: new RegExp(linkText, "i") }),
      page.getByText(new RegExp(linkText, "i"))
    ],
    15000,
    `Could not find legal link "${linkText}".`
  );

  const previousUrl = page.url();
  await clickAndWait(page, link);

  const popup = await popupPromise;
  const targetPage = popup ?? page;
  await waitForUiLoad(targetPage);

  const heading = await pickFirstVisible(
    [targetPage.getByRole("heading", { name: headingPattern }), targetPage.getByText(headingPattern)],
    15000,
    `Could not find heading for "${linkText}".`
  );
  await expect(heading).toBeVisible();

  await expect(targetPage.locator("main, article, body").first()).toContainText(/[A-Za-zÁÉÍÓÚáéíóúñ]{20,}/);
  await captureCheckpoint(testInfo, targetPage, `${linkText} page`);

  const finalUrl = targetPage.url();
  await attachUrl(testInfo, `${slug(linkText)}-final-url`, finalUrl);

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== previousUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
  }

  await waitForUiLoad(page);
  return finalUrl;
};

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
    const report = REPORT_FIELDS.reduce(
      (acc, field) => ({ ...acc, [field]: "FAIL" as ReportStatus }),
      {} as Record<ReportField, ReportStatus>
    );
    const details: Partial<Record<ReportField, string>> = {};

    const runStep = async (field: ReportField, fn: () => Promise<void>): Promise<void> => {
      try {
        await fn();
        report[field] = "PASS";
      } catch (error) {
        report[field] = "FAIL";
        details[field] = error instanceof Error ? error.message : String(error);
        await captureCheckpoint(testInfo, page, `${field} failure`).catch(() => undefined);
      }
    };

    await runStep("Login", async () => {
      await ensureLoginContext(page);

      const loginButton = await pickFirstVisible(
        [
          page.getByRole("button", {
            name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
          }),
          page.getByRole("link", {
            name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
          }),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
          page.locator("button:has-text('Google'), a:has-text('Google')")
        ],
        30000,
        "Could not find Google login trigger."
      );

      const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickAndWait(page, loginButton);

      const popup = await popupPromise;
      if (popup) {
        await waitForUiLoad(popup);
        const accountSelector = await pickFirstVisible(
          [popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }), popup.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL })],
          15000,
          `Google account "${GOOGLE_ACCOUNT_EMAIL}" was not visible in popup.`
        );
        await clickAndWait(popup, accountSelector);
      } else {
        const accountSelector = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
        if (await accountSelector.isVisible().catch(() => false)) {
          await clickAndWait(page, accountSelector);
        }
      }

      await waitForUiLoad(page);
      const mainArea = await pickFirstVisible(
        [page.getByRole("main"), page.locator("main"), page.getByText(/dashboard|panel|inicio/i)],
        45000,
        "Main application interface did not appear after login."
      );
      await expect(mainArea).toBeVisible();

      const sidebar = await pickFirstVisible(
        [page.locator("aside"), page.getByRole("navigation"), page.getByText(/negocio/i)],
        45000,
        "Left sidebar navigation was not visible."
      );
      await expect(sidebar).toBeVisible();

      await captureCheckpoint(testInfo, page, "dashboard-loaded");
    });

    await runStep("Mi Negocio menu", async () => {
      const negocioSection = page.getByText(/^Negocio$/i).first();
      if (await negocioSection.isVisible().catch(() => false)) {
        await clickAndWait(page, negocioSection);
      }

      const miNegocio = await pickFirstVisible(
        [page.getByRole("link", { name: /mi negocio/i }), page.getByRole("button", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
        20000,
        "Could not find 'Mi Negocio' option."
      );
      await clickAndWait(page, miNegocio);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
      await captureCheckpoint(testInfo, page, "mi-negocio-menu-expanded", false);
    });

    await runStep("Agregar Negocio modal", async () => {
      const addBusiness = await pickFirstVisible(
        [page.getByRole("button", { name: /agregar negocio/i }), page.getByRole("link", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
        20000,
        "Could not find 'Agregar Negocio' trigger."
      );
      await clickAndWait(page, addBusiness);

      const modalTitle = page.getByText(/crear nuevo negocio/i).first();
      await expect(modalTitle).toBeVisible();

      const businessNameInput = await pickFirstVisible(
        [
          page.getByLabel(/nombre del negocio/i),
          page.getByPlaceholder(/nombre del negocio/i),
          page.locator("input[name*='negocio'], input[id*='negocio']")
        ],
        15000,
        "Input 'Nombre del Negocio' was not found."
      );
      await expect(businessNameInput).toBeVisible();

      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

      await captureCheckpoint(testInfo, page, "agregar-negocio-modal");
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }).first());
    });

    await runStep("Administrar Negocios view", async () => {
      const administrarNegocios = page.getByText(/administrar negocios/i).first();
      if (!(await administrarNegocios.isVisible().catch(() => false))) {
        const miNegocio = await pickFirstVisible(
          [page.getByRole("link", { name: /mi negocio/i }), page.getByRole("button", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
          15000,
          "Could not re-open 'Mi Negocio' menu."
        );
        await clickAndWait(page, miNegocio);
      }

      await clickAndWait(page, page.getByText(/administrar negocios/i).first());

      await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

      await captureCheckpoint(testInfo, page, "administrar-negocios-page");
    });

    await runStep("Información General", async () => {
      const infoGeneralSection = sectionWithText(page, /informaci[oó]n general/i);
      await expect(infoGeneralSection).toBeVisible();

      const sectionText = (await infoGeneralSection.innerText()).replace(/\s+/g, " ").trim();
      const emailMatch = sectionText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
      expect(emailMatch, "User email is not visible in 'Información General'.").not.toBeNull();

      const withoutEmail = sectionText
        .replace(emailMatch?.[0] ?? "", "")
        .replace(/informaci[oó]n general|business plan|cambiar plan|plan/gi, "");
      const nameLikeMatch = withoutEmail.match(/[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]{2,}(?:\s+[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]{2,})+/);
      expect(nameLikeMatch, "User name was not detected in 'Información General'.").not.toBeNull();

      await expect(infoGeneralSection.getByText(/business plan/i).first()).toBeVisible();
      await expect(infoGeneralSection.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
    });

    await runStep("Detalles de la Cuenta", async () => {
      const accountDetailsSection = sectionWithText(page, /detalles de la cuenta/i);
      await expect(accountDetailsSection).toBeVisible();
      await expect(accountDetailsSection.getByText(/cuenta creada/i).first()).toBeVisible();
      await expect(accountDetailsSection.getByText(/estado activo/i).first()).toBeVisible();
      await expect(accountDetailsSection.getByText(/idioma seleccionado/i).first()).toBeVisible();
    });

    await runStep("Tus Negocios", async () => {
      const businessSection = sectionWithText(page, /tus negocios/i);
      await expect(businessSection).toBeVisible();
      await expect(businessSection.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
      await expect(businessSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

      const entries = businessSection.locator(
        "li, tr, [role='listitem'], [data-testid*='business'], [class*='business']"
      );
      const hasEntry = await entries.first().isVisible().catch(() => false);
      const hasAnyBusinessText = /negocio/i.test((await businessSection.innerText()).replace(/\s+/g, " ").trim());
      expect(hasEntry || hasAnyBusinessText, "Business list was not visible in 'Tus Negocios'.").toBeTruthy();
    });

    await runStep("Términos y Condiciones", async () => {
      await validateLegalDocument(page, testInfo, "Términos y Condiciones", /t[eé]rminos y condiciones/i);
    });

    await runStep("Política de Privacidad", async () => {
      await validateLegalDocument(page, testInfo, "Política de Privacidad", /pol[ií]tica de privacidad/i);
    });

    const finalReport = REPORT_FIELDS.map((field) => ({
      field,
      status: report[field],
      detail: details[field] ?? ""
    }));
    // eslint-disable-next-line no-console
    console.table(finalReport);

    await testInfo.attach("saleads-mi-negocio-final-report", {
      body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
      contentType: "application/json"
    });

    const failures = finalReport.filter((entry) => entry.status === "FAIL");
    expect(
      failures,
      `One or more validation steps failed:\n${failures.map((failure) => `- ${failure.field}: ${failure.detail}`).join("\n")}`
    ).toEqual([]);
  });
});
