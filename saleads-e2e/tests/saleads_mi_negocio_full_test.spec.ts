import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informaci\u00f3n General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "T\u00e9rminos y Condiciones"
  | "Pol\u00edtica de Privacidad";

const ORDERED_FIELDS: ReportField[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad"
];

const screenshotDir = "checkpoints";
const GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const accentMap: Record<string, string> = {
  a: "[a\u00e1\u00e0\u00e4\u00e2\u00e3]",
  e: "[e\u00e9\u00e8\u00eb\u00ea]",
  i: "[i\u00ed\u00ec\u00ef\u00ee]",
  o: "[o\u00f3\u00f2\u00f6\u00f4\u00f5]",
  u: "[u\u00fa\u00f9\u00fc\u00fb]",
  n: "[n\u00f1]",
  c: "[c\u00e7]"
};

function normalizeText(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function escapeRegexChar(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function textRegex(text: string): RegExp {
  const pattern = text
    .split("")
    .map((char) => {
      if (/\s/.test(char)) {
        return "\\s+";
      }
      const mapped = accentMap[char.toLowerCase()];
      if (mapped) {
        return mapped;
      }
      return escapeRegexChar(char);
    })
    .join("");
  return new RegExp(pattern, "i");
}

function formatError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

async function waitForUiSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator.isVisible().catch(() => false);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 15000 });
  await locator.click();
  await waitForUiSettle(page);
}

async function firstVisibleLocator(candidates: Locator[], name: string): Promise<Locator> {
  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate;
    }
  }
  throw new Error(`Could not find a visible element for: ${name}`);
}

async function clickByText(page: Page, candidates: string[]): Promise<Locator> {
  for (const text of candidates) {
    const regex = textRegex(text);
    const locator = await firstVisibleLocator(
      [
        page.getByRole("button", { name: regex }).first(),
        page.getByRole("link", { name: regex }).first(),
        page.getByRole("menuitem", { name: regex }).first(),
        page.getByText(regex).first()
      ],
      text
    ).catch(() => null);
    if (locator) {
      await clickAndWait(page, locator);
      return locator;
    }
  }
  throw new Error(`Could not click any visible element with text candidates: ${candidates.join(", ")}`);
}

async function openLeftSidebar(page: Page): Promise<Locator> {
  return firstVisibleLocator(
    [
      page.locator("aside").first(),
      page.getByRole("navigation").first(),
      page.locator('[class*="sidebar"]').first(),
      page.locator('[data-testid*="sidebar"]').first()
    ],
    "left sidebar navigation"
  );
}

async function waitForSidebarAfterLogin(page: Page): Promise<Locator> {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const sidebar = await openLeftSidebar(page).catch(() => null);
    if (sidebar && (await isVisible(sidebar))) {
      return sidebar;
    }
    await page.waitForTimeout(1500);
  }
  throw new Error("Main application interface did not load (left sidebar not visible).");
}

async function attachScreenshot(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  const path = `${screenshotDir}/${name}.png`;
  await page.screenshot({ path: testInfo.outputPath(path), fullPage });
  await testInfo.attach(name, {
    path: testInfo.outputPath(path),
    contentType: "image/png"
  });
}

async function validateTextsVisible(page: Page, texts: string[]): Promise<void> {
  for (const text of texts) {
    await expect(page.getByText(textRegex(text)).first()).toBeVisible({ timeout: 15000 });
  }
}

async function maybeSelectGoogleAccount(page: Page): Promise<void> {
  const emailLocator = page.getByText(GOOGLE_EMAIL, { exact: false }).first();
  if (await isVisible(emailLocator)) {
    await clickAndWait(page, emailLocator);
    return;
  }

  const useAnotherAccount = page.getByText(/usar otra cuenta|use another account/i).first();
  if (await isVisible(useAnotherAccount)) {
    throw new Error(
      `Google account picker appeared but did not show ${GOOGLE_EMAIL}. Ensure this account exists in the browser profile.`
    );
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio complete workflow", async ({ page, context }, testInfo) => {
    const stepResults = new Map<ReportField, StepStatus>(ORDERED_FIELDS.map((field) => [field, "FAIL"]));
    const stepErrors = new Map<ReportField, string>();
    const legalUrls: Partial<Record<ReportField, string>> = {};
    let loginOk = false;
    let menuOk = false;
    let adminViewOk = false;

    async function runStep(field: ReportField, action: () => Promise<void>): Promise<void> {
      try {
        await action();
        stepResults.set(field, "PASS");
      } catch (error) {
        stepResults.set(field, "FAIL");
        stepErrors.set(field, formatError(error));
      }
    }

    async function validateLegalLink(
      reportField: ReportField,
      linkText: string,
      headingText: string,
      screenshotName: string
    ): Promise<void> {
      const initialUrl = page.url();
      const linkLocator = await firstVisibleLocator(
        [
          page.getByRole("link", { name: textRegex(linkText) }).first(),
          page.getByRole("button", { name: textRegex(linkText) }).first(),
          page.getByText(textRegex(linkText)).first()
        ],
        linkText
      );

      const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
      await clickAndWait(page, linkLocator);
      const popup = await popupPromise;
      const targetPage = popup ?? page;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await popup.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => undefined);
      } else {
        await waitForUiSettle(page);
      }

      const heading = targetPage.getByRole("heading", { name: textRegex(headingText) }).first();
      if (await isVisible(heading)) {
        await expect(heading).toBeVisible({ timeout: 15000 });
      } else {
        await expect(targetPage.getByText(textRegex(headingText)).first()).toBeVisible({ timeout: 15000 });
      }

      const legalText = normalizeText(await targetPage.locator("body").innerText());
      expect(legalText.length).toBeGreaterThan(80);
      await attachScreenshot(targetPage, testInfo, screenshotName, true);
      legalUrls[reportField] = targetPage.url();

      if (popup) {
        await popup.close().catch(() => undefined);
        await page.bringToFront();
        await waitForUiSettle(page);
      } else if (page.url() !== initialUrl) {
        await page.goBack().catch(() => undefined);
        await waitForUiSettle(page);
      }
    }

    await runStep("Login", async () => {
      const loginCandidates = [
        "Sign in with Google",
        "Iniciar sesion con Google",
        "Iniciar sesi\u00f3n con Google",
        "Continuar con Google",
        "Google"
      ];

      const popupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
      await clickByText(page, loginCandidates);
      const popup = await popupPromise;

      if (popup) {
        await waitForUiSettle(popup);
        await maybeSelectGoogleAccount(popup);
        await popup.waitForEvent("close", { timeout: 60000 }).catch(() => undefined);
      } else {
        await maybeSelectGoogleAccount(page);
      }

      await waitForUiSettle(page);
      const sidebar = await waitForSidebarAfterLogin(page);
      await expect(sidebar).toBeVisible();
      await attachScreenshot(page, testInfo, "01-dashboard-loaded");
      loginOk = true;
    });

    await runStep("Mi Negocio menu", async () => {
      if (!loginOk) {
        throw new Error("Precondition failed: login did not complete.");
      }
      await openLeftSidebar(page);
      await clickByText(page, ["Mi Negocio", "Negocio"]);
      await validateTextsVisible(page, ["Agregar Negocio", "Administrar Negocios"]);
      await attachScreenshot(page, testInfo, "02-mi-negocio-expanded");
      menuOk = true;
    });

    await runStep("Agregar Negocio modal", async () => {
      if (!menuOk) {
        throw new Error("Precondition failed: Mi Negocio menu is not open.");
      }
      await clickByText(page, ["Agregar Negocio"]);
      const modal = page.getByRole("dialog").first();
      await expect(modal).toBeVisible({ timeout: 15000 });
      await expect(modal.getByText(textRegex("Crear Nuevo Negocio")).first()).toBeVisible();
      await expect(modal.getByText(textRegex("Tienes 2 de 3 negocios")).first()).toBeVisible();
      await expect(modal.getByRole("button", { name: textRegex("Cancelar") })).toBeVisible();
      await expect(modal.getByRole("button", { name: textRegex("Crear Negocio") })).toBeVisible();

      const businessNameInput = await firstVisibleLocator(
        [
          modal.getByLabel(textRegex("Nombre del Negocio")).first(),
          modal.getByRole("textbox", { name: textRegex("Nombre del Negocio") }).first(),
          modal.getByPlaceholder(textRegex("Nombre del Negocio")).first(),
          modal.locator("input[type='text']").first()
        ],
        "Nombre del Negocio input"
      );
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatizacion");
      await attachScreenshot(page, testInfo, "03-agregar-negocio-modal");

      await clickAndWait(page, modal.getByRole("button", { name: textRegex("Cancelar") }));
      await expect(modal).toBeHidden({ timeout: 10000 });
    });

    await runStep("Administrar Negocios view", async () => {
      if (!loginOk) {
        throw new Error("Precondition failed: login did not complete.");
      }
      if (!(await isVisible(page.getByText(textRegex("Administrar Negocios")).first()))) {
        await clickByText(page, ["Mi Negocio", "Negocio"]);
      }
      await clickByText(page, ["Administrar Negocios"]);
      await waitForUiSettle(page);
      await validateTextsVisible(page, [
        "Informacion General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Seccion Legal"
      ]);
      await attachScreenshot(page, testInfo, "04-administrar-negocios-page", true);
      adminViewOk = true;
    });

    await runStep("Informaci\u00f3n General", async () => {
      if (!adminViewOk) {
        throw new Error("Precondition failed: Administrar Negocios view is not available.");
      }
      await validateTextsVisible(page, ["BUSINESS PLAN", "Cambiar Plan"]);

      const emailLocator = page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i").first();
      await expect(emailLocator).toBeVisible({ timeout: 10000 });
      const headingTexts = (await page.locator("h1, h2, h3").allTextContents()).map((text) => normalizeText(text));
      const hasLikelyUserName = headingTexts.some(
        (text) =>
          text.length > 2 &&
          !["informacion general", "detalles de la cuenta", "tus negocios", "seccion legal"].includes(text)
      );
      expect(hasLikelyUserName).toBeTruthy();
    });

    await runStep("Detalles de la Cuenta", async () => {
      if (!adminViewOk) {
        throw new Error("Precondition failed: Administrar Negocios view is not available.");
      }
      await validateTextsVisible(page, ["Cuenta creada", "Estado activo", "Idioma seleccionado"]);
    });

    await runStep("Tus Negocios", async () => {
      if (!adminViewOk) {
        throw new Error("Precondition failed: Administrar Negocios view is not available.");
      }
      await validateTextsVisible(page, ["Tus Negocios", "Agregar Negocio", "Tienes 2 de 3 negocios"]);
      const businessItems = page
        .locator("[class*='business'], [data-testid*='business'], ul li, table tbody tr")
        .filter({ hasText: /.+/ });
      expect(await isVisible(businessItems.first())).toBeTruthy();
    });

    await runStep("T\u00e9rminos y Condiciones", async () => {
      if (!adminViewOk) {
        throw new Error("Precondition failed: Administrar Negocios view is not available.");
      }
      await validateLegalLink(
        "T\u00e9rminos y Condiciones",
        "Terminos y Condiciones",
        "Terminos y Condiciones",
        "08-terminos-y-condiciones"
      );
    });

    await runStep("Pol\u00edtica de Privacidad", async () => {
      if (!adminViewOk) {
        throw new Error("Precondition failed: Administrar Negocios view is not available.");
      }
      await validateLegalLink(
        "Pol\u00edtica de Privacidad",
        "Politica de Privacidad",
        "Politica de Privacidad",
        "09-politica-de-privacidad"
      );
    });

    const reportLines = ORDERED_FIELDS.map((field) => `${field}: ${stepResults.get(field) ?? "FAIL"}`);
    const errorLines = ORDERED_FIELDS.filter((field) => stepErrors.has(field)).map(
      (field) => `${field} error: ${stepErrors.get(field)}`
    );
    const reportContent = [
      "saleads_mi_negocio_full_test final report",
      ...reportLines,
      "",
      `Terminos y Condiciones URL: ${legalUrls["T\u00e9rminos y Condiciones"] ?? "N/A"}`,
      `Politica de Privacidad URL: ${legalUrls["Pol\u00edtica de Privacidad"] ?? "N/A"}`,
      ...(errorLines.length > 0 ? ["", "Errors:", ...errorLines] : [])
    ].join("\n");

    await testInfo.attach("final-report", {
      body: Buffer.from(reportContent, "utf8"),
      contentType: "text/plain"
    });
    console.log(reportContent);

    const failedFields = ORDERED_FIELDS.filter((field) => stepResults.get(field) !== "PASS");
    expect(failedFields, reportContent).toEqual([]);

    const extraPages = context.pages().filter((openPage) => openPage !== page);
    for (const extraPage of extraPages) {
      await extraPage.close().catch(() => undefined);
    }
  });
});
