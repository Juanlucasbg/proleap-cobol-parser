import { test, expect, type BrowserContext, type Locator, type Page } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

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

type ReportEntry = {
  status: ReportStatus;
  details: string;
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const reportFields: ReportField[] = [
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

const initialReport = (): Record<ReportField, ReportEntry> => {
  const report = {} as Record<ReportField, ReportEntry>;

  for (const field of reportFields) {
    report[field] = {
      status: "FAIL",
      details: "Not executed."
    };
  }

  return report;
};

const escapeRegExp = (value: string): string => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const sanitizeFileName = (value: string): string =>
  value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

const waitForUi = async (page: Page): Promise<void> => {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 });
  await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
};

const locatorCandidates = (scope: Page | Locator, text: string): Locator[] => {
  const exact = new RegExp(`^${escapeRegExp(text)}$`, "i");
  const partial = new RegExp(escapeRegExp(text), "i");

  return [
    scope.getByRole("button", { name: exact }).first(),
    scope.getByRole("link", { name: exact }).first(),
    scope.getByRole("menuitem", { name: exact }).first(),
    scope.getByRole("tab", { name: exact }).first(),
    scope.getByText(exact).first(),
    scope.getByText(partial).first(),
    scope.locator(`text=${text}`).first()
  ];
};

const getVisibleLocatorByText = async (scope: Page | Locator, texts: string[]): Promise<Locator | null> => {
  for (const text of texts) {
    for (const locator of locatorCandidates(scope, text)) {
      const visible = await locator.isVisible({ timeout: 2_000 }).catch(() => false);
      if (visible) {
        return locator;
      }
    }
  }

  return null;
};

const clickVisibleText = async (scope: Page | Locator, texts: string[]): Promise<string> => {
  for (const text of texts) {
    for (const locator of locatorCandidates(scope, text)) {
      const visible = await locator.isVisible({ timeout: 2_000 }).catch(() => false);
      if (visible) {
        await locator.click();
        return text;
      }
    }
  }

  throw new Error(`Unable to find a clickable element for any of: ${texts.join(", ")}`);
};

const ensureTextVisible = async (scope: Page | Locator, text: string): Promise<void> => {
  const locator = await getVisibleLocatorByText(scope, [text]);
  if (!locator) {
    throw new Error(`Text not visible: ${text}`);
  }
};

const getSectionByHeading = async (page: Page, heading: string): Promise<Locator> => {
  const headingLocator = page.getByText(new RegExp(`^${escapeRegExp(heading)}$`, "i")).first();
  await expect(headingLocator).toBeVisible();
  return headingLocator.locator("xpath=ancestor::*[self::section or self::article or self::div][1]").first();
};

const maybeSelectGoogleAccount = async (page: Page): Promise<boolean> => {
  const accountLocator = await getVisibleLocatorByText(page, [GOOGLE_ACCOUNT_EMAIL]);
  if (!accountLocator) {
    return false;
  }

  await accountLocator.click();
  return true;
};

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = initialReport();
  const startingUrl = process.env.SALEADS_LOGIN_URL;
  const artifactsDir =
    process.env.SALEADS_ARTIFACTS_DIR || path.join(testInfo.project.outputDir, "saleads-mi-negocio");

  await fs.mkdir(artifactsDir, { recursive: true });

  const checkpoint = async (name: string, targetPage: Page = page, fullPage = false): Promise<void> => {
    const fileName = `${sanitizeFileName(name)}.png`;
    const outputPath = path.join(artifactsDir, fileName);
    await targetPage.screenshot({ path: outputPath, fullPage });
    await testInfo.attach(name, { path: outputPath, contentType: "image/png" });
  };

  const runStep = async (field: ReportField, action: () => Promise<string | void>): Promise<void> => {
    try {
      const details = (await action()) || "Validation succeeded.";
      report[field] = { status: "PASS", details };
    } catch (error) {
      const details = error instanceof Error ? error.message : String(error);
      report[field] = { status: "FAIL", details };
    }
  };

  const openLegalLinkAndValidate = async (
    appPage: Page,
    appContext: BrowserContext,
    linkText: string,
    headingText: string,
    screenshotName: string
  ): Promise<string> => {
    const linkLocator = await getVisibleLocatorByText(appPage, [linkText]);
    if (!linkLocator) {
      throw new Error(`Cannot find legal link: ${linkText}`);
    }

    const originalUrl = appPage.url();
    const popupPromise = appContext.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

    await linkLocator.click();

    const popupPage = await popupPromise;
    const legalPage = popupPage ?? appPage;
    await waitForUi(legalPage);

    await expect(legalPage.getByText(new RegExp(headingText, "i")).first()).toBeVisible();
    const legalBody = await legalPage.locator("body").innerText();
    if (legalBody.trim().length < 100) {
      throw new Error(`Legal page for "${linkText}" loaded but body content is too short.`);
    }

    await checkpoint(screenshotName, legalPage, true);
    const finalUrl = legalPage.url();

    if (popupPage) {
      await popupPage.close();
      await appPage.bringToFront();
    } else if (appPage.url() !== originalUrl) {
      await appPage.goBack().catch(() => undefined);
      await waitForUi(appPage);
    }

    return `Validated heading and legal content. URL: ${finalUrl}`;
  };

  if (startingUrl) {
    await page.goto(startingUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);

  await runStep("Login", async () => {
    const loginTexts = [
      "Sign in with Google",
      "Login with Google",
      "Iniciar sesión con Google",
      "Acceder con Google",
      "Continuar con Google"
    ];

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    let clicked = false;

    try {
      await clickVisibleText(page, loginTexts);
      clicked = true;
    } catch {
      clicked = false;
    }

    const popupPage = clicked ? await popupPromise : null;

    if (popupPage) {
      await waitForUi(popupPage);
      await maybeSelectGoogleAccount(popupPage);
      await popupPage.waitForTimeout(1_000);
    } else {
      await maybeSelectGoogleAccount(page);
    }

    await waitForUi(page);

    const sidebar = page.locator("aside, nav").first();
    const sidebarVisible = await sidebar.isVisible().catch(() => false);
    const negocioVisible =
      (await getVisibleLocatorByText(page, ["Negocio", "Mi Negocio"])) !== null ||
      (await getVisibleLocatorByText(page, ["Administrar Negocios"])) !== null;

    if (!sidebarVisible && !negocioVisible) {
      throw new Error("Main app layout/sidebar is not visible after login.");
    }

    await checkpoint("01-dashboard-loaded");
    return "Dashboard loaded and sidebar is visible.";
  });

  await runStep("Mi Negocio menu", async () => {
    await clickVisibleText(page, ["Negocio"]).catch(() => undefined);
    await waitForUi(page);
    await clickVisibleText(page, ["Mi Negocio"]);
    await waitForUi(page);

    await ensureTextVisible(page, "Agregar Negocio");
    await ensureTextVisible(page, "Administrar Negocios");
    await checkpoint("02-mi-negocio-expanded");
    return "Mi Negocio submenu expanded with expected entries.";
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickVisibleText(page, ["Agregar Negocio"]);
    await waitForUi(page);

    await ensureTextVisible(page, "Crear Nuevo Negocio");
    await ensureTextVisible(page, "Nombre del Negocio");
    await ensureTextVisible(page, "Tienes 2 de 3 negocios");
    await ensureTextVisible(page, "Cancelar");
    await ensureTextVisible(page, "Crear Negocio");
    await checkpoint("03-agregar-negocio-modal");

    const nombreField = await getVisibleLocatorByText(page, ["Nombre del Negocio"]);
    if (nombreField) {
      await nombreField.click();
      await page.keyboard.type("Negocio Prueba Automatización");
    }

    await clickVisibleText(page, ["Cancelar"]);
    await page.getByText(/Crear Nuevo Negocio/i).first().waitFor({ state: "hidden", timeout: 10_000 });
    await waitForUi(page);
    return "Modal fields, quota text and buttons are visible.";
  });

  await runStep("Administrar Negocios view", async () => {
    await clickVisibleText(page, ["Mi Negocio"]).catch(() => undefined);
    await clickVisibleText(page, ["Administrar Negocios"]);
    await waitForUi(page);

    await ensureTextVisible(page, "Información General");
    await ensureTextVisible(page, "Detalles de la Cuenta");
    await ensureTextVisible(page, "Tus Negocios");
    await ensureTextVisible(page, "Sección Legal");
    await checkpoint("04-administrar-negocios", page, true);
    return "Account view sections are visible.";
  });

  await runStep("Información General", async () => {
    const infoSection = await getSectionByHeading(page, "Información General");
    const emailLocator = infoSection
      .getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)
      .first();
    await expect(emailLocator).toBeVisible();

    const sectionText = (await infoSection.innerText())
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const nameCandidate = sectionText.find(
      (line) =>
        !line.includes("@") &&
        !/informaci[oó]n general|business plan|cambiar plan/i.test(line) &&
        /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(line)
    );

    if (!nameCandidate) {
      throw new Error("User name is not clearly visible in 'Información General'.");
    }

    await ensureTextVisible(infoSection, "BUSINESS PLAN");
    await ensureTextVisible(infoSection, "Cambiar Plan");
    return "User identity, plan and action button are visible.";
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = await getSectionByHeading(page, "Detalles de la Cuenta");
    await ensureTextVisible(detailsSection, "Cuenta creada");
    await ensureTextVisible(detailsSection, "Estado activo");
    await ensureTextVisible(detailsSection, "Idioma seleccionado");
    return "Account detail labels are visible.";
  });

  await runStep("Tus Negocios", async () => {
    const businessesSection = await getSectionByHeading(page, "Tus Negocios");
    await ensureTextVisible(businessesSection, "Agregar Negocio");
    await ensureTextVisible(businessesSection, "Tienes 2 de 3 negocios");

    const listItemCount = await businessesSection
      .locator("li, [role='listitem'], tr, article, [data-testid*='business']")
      .count();
    if (listItemCount === 0) {
      throw new Error("No visible business list items found in 'Tus Negocios'.");
    }

    return "Business list, Add button and quota message are visible.";
  });

  await runStep("Términos y Condiciones", async () =>
    openLegalLinkAndValidate(
      page,
      context,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05-terminos-y-condiciones"
    )
  );

  await runStep("Política de Privacidad", async () =>
    openLegalLinkAndValidate(
      page,
      context,
      "Política de Privacidad",
      "Política de Privacidad",
      "06-politica-de-privacidad"
    )
  );

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    appUrlAtEnd: page.url(),
    results: report
  };

  const reportPath = path.join(artifactsDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });
  console.log(JSON.stringify(finalReport, null, 2));

  const failedChecks = Object.entries(report).filter(([, entry]) => entry.status === "FAIL");
  if (failedChecks.length > 0) {
    throw new Error(
      `Workflow completed with failures: ${failedChecks
        .map(([field, entry]) => `${field} -> ${entry.details}`)
        .join(" | ")}`
    );
  }
});
