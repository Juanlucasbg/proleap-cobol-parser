import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import { promises as fs } from "node:fs";

type StepStatus = "PASS" | "FAIL";

type WorkflowReport = {
  name: string;
  goal: string;
  generatedAt: string;
  startUrl: string;
  finalApplicationUrl: string;
  legalUrls: {
    termsAndConditions?: string;
    privacyPolicy?: string;
  };
  results: Record<string, StepStatus>;
  errors: string[];
};

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

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function reportError(errors: string[], field: string, error: unknown): void {
  const message = error instanceof Error ? error.message : String(error);
  errors.push(`${field}: ${message}`);
}

async function sleep(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function firstVisibleLocator(
  candidates: Locator[],
  timeoutMs = 20_000
): Promise<Locator> {
  const timeoutAt = Date.now() + timeoutMs;

  while (Date.now() < timeoutAt) {
    for (const candidate of candidates) {
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
    await sleep(250);
  }

  throw new Error("No matching visible element was found.");
}

async function clickAndWaitForUi(page: Page, target: Locator): Promise<void> {
  await expect(target).toBeVisible();
  await target.scrollIntoViewIfNeeded().catch(() => undefined);
  await target.click();
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await sleep(500);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<void> {
  const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const path = testInfo.outputPath(`${safeName}.png`);
  await page.screenshot({ path, fullPage });
}

function loginButtonCandidates(page: Page): Locator[] {
  return [
    page
      .getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      })
      .first(),
    page
      .getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      })
      .first(),
    page
      .getByText(
        /sign in with google|iniciar sesi[oó]n con google|continuar con google/i
      )
      .first(),
  ];
}

function menuItemCandidates(page: Page, label: string): Locator[] {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const regex = new RegExp(`^${escaped}$`, "i");
  return [
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByText(regex).first(),
  ];
}

async function validateLegalDocument(
  appPage: Page,
  testInfo: TestInfo,
  linkText: string,
  expectedHeading: string
): Promise<string> {
  const link = await firstVisibleLocator(menuItemCandidates(appPage, linkText));

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickAndWaitForUi(appPage, link);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  await targetPage.waitForLoadState("domcontentloaded");
  await expect(targetPage.getByRole("heading", { name: new RegExp(expectedHeading, "i") }).first()).toBeVisible();

  const legalText = targetPage.locator("main p, article p, p").first();
  await expect(legalText).toBeVisible();
  await expect(legalText).not.toHaveText(/^\s*$/);

  await captureCheckpoint(targetPage, testInfo, expectedHeading, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.setTimeout(300_000);

  const results: Record<string, StepStatus> = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, "FAIL"])
  );
  const errors: string[] = [];

  const startUrl =
    process.env.SALEADS_START_URL ||
    process.env.SALEADS_LOGIN_URL ||
    process.env.BASE_URL ||
    "";

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No start URL available. Set SALEADS_START_URL (or SALEADS_LOGIN_URL / BASE_URL) to begin from the login page."
    );
  }

  let termsUrl = "";
  let privacyUrl = "";

  try {
    const loginButton = await firstVisibleLocator(loginButtonCandidates(page), 30_000);
    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWaitForUi(page, loginButton);

    const googlePage = (await popupPromise) ?? page;
    const onGoogleAccountSelector =
      /accounts\.google\.com/i.test(googlePage.url()) ||
      (await googlePage
        .getByText(/choose an account|elige una cuenta|selecciona una cuenta/i)
        .first()
        .isVisible()
        .catch(() => false));

    if (onGoogleAccountSelector) {
      const accountOption = await firstVisibleLocator(
        [
          googlePage.getByText(new RegExp(`^${ACCOUNT_EMAIL}$`, "i")).first(),
          googlePage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }).first(),
        ],
        20_000
      );
      await clickAndWaitForUi(googlePage, accountOption);
      if (googlePage !== page) {
        await googlePage.waitForEvent("close", { timeout: 20_000 }).catch(() => undefined);
      }
    }

    await page.waitForLoadState("domcontentloaded", { timeout: 60_000 });
    await page.waitForLoadState("networkidle", { timeout: 60_000 }).catch(() => undefined);

    await expect(page.locator("main, [role='main']").first()).toBeVisible();
    await expect(
      firstVisibleLocator(
        [
          page.getByRole("navigation").first(),
          page.locator("aside").first(),
          page.locator("[class*='sidebar']").first(),
        ],
        30_000
      )
    ).resolves.toBeTruthy();

    results["Login"] = "PASS";
    await captureCheckpoint(page, testInfo, "dashboard-loaded", true);
  } catch (error) {
    reportError(errors, "Login", error);
  }

  try {
    await expect(page.getByText(/^Negocio$/i).first()).toBeVisible();
    const miNegocio = await firstVisibleLocator(menuItemCandidates(page, "Mi Negocio"));
    await clickAndWaitForUi(page, miNegocio);

    await expect(await firstVisibleLocator(menuItemCandidates(page, "Agregar Negocio"))).toBeVisible();
    await expect(await firstVisibleLocator(menuItemCandidates(page, "Administrar Negocios"))).toBeVisible();

    results["Mi Negocio menu"] = "PASS";
    await captureCheckpoint(page, testInfo, "mi-negocio-expanded-menu");
  } catch (error) {
    reportError(errors, "Mi Negocio menu", error);
  }

  try {
    const agregarNegocio = await firstVisibleLocator(menuItemCandidates(page, "Agregar Negocio"));
    await clickAndWaitForUi(page, agregarNegocio);

    await expect(page.getByRole("heading", { name: /crear nuevo negocio/i })).toBeVisible();
    const nombreNegocioInput = await firstVisibleLocator(
      [
        page.getByLabel(/nombre del negocio/i).first(),
        page.getByPlaceholder(/nombre del negocio/i).first(),
        page.locator("input").first(),
      ],
      20_000
    );
    await expect(nombreNegocioInput).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    const cancelar = await firstVisibleLocator(menuItemCandidates(page, "Cancelar"));
    await expect(cancelar).toBeVisible();
    await expect(await firstVisibleLocator(menuItemCandidates(page, "Crear Negocio"))).toBeVisible();

    await captureCheckpoint(page, testInfo, "crear-nuevo-negocio-modal");

    await nombreNegocioInput.click();
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await clickAndWaitForUi(page, cancelar);

    results["Agregar Negocio modal"] = "PASS";
  } catch (error) {
    reportError(errors, "Agregar Negocio modal", error);
  }

  try {
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      const miNegocio = await firstVisibleLocator(menuItemCandidates(page, "Mi Negocio"));
      await clickAndWaitForUi(page, miNegocio);
    }

    const administrarNegocios = await firstVisibleLocator(menuItemCandidates(page, "Administrar Negocios"));
    await clickAndWaitForUi(page, administrarNegocios);

    await expect(page.getByText(/información general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/sección legal/i).first()).toBeVisible();

    results["Administrar Negocios view"] = "PASS";
    await captureCheckpoint(page, testInfo, "administrar-negocios-cuenta", true);
  } catch (error) {
    reportError(errors, "Administrar Negocios view", error);
  }

  try {
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(await firstVisibleLocator(menuItemCandidates(page, "Cambiar Plan"))).toBeVisible();

    const possibleName = page.locator("main h1, main h2, main h3, main p, main span").filter({
      hasNotText: /información general|business plan|cambiar plan|@/i,
    });
    await expect(possibleName.first()).toBeVisible();

    results["Información General"] = "PASS";
  } catch (error) {
    reportError(errors, "Información General", error);
  }

  try {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
    results["Detalles de la Cuenta"] = "PASS";
  } catch (error) {
    reportError(errors, "Detalles de la Cuenta", error);
  }

  try {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(await firstVisibleLocator(menuItemCandidates(page, "Agregar Negocio"))).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    results["Tus Negocios"] = "PASS";
  } catch (error) {
    reportError(errors, "Tus Negocios", error);
  }

  try {
    termsUrl = await validateLegalDocument(page, testInfo, "Términos y Condiciones", "Términos y Condiciones");
    results["Términos y Condiciones"] = "PASS";
  } catch (error) {
    reportError(errors, "Términos y Condiciones", error);
  }

  try {
    privacyUrl = await validateLegalDocument(page, testInfo, "Política de Privacidad", "Política de Privacidad");
    results["Política de Privacidad"] = "PASS";
  } catch (error) {
    reportError(errors, "Política de Privacidad", error);
  }

  const finalReport: WorkflowReport = {
    name: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
    generatedAt: new Date().toISOString(),
    startUrl: startUrl || page.url(),
    finalApplicationUrl: page.url(),
    legalUrls: {
      termsAndConditions: termsUrl || undefined,
      privacyPolicy: privacyUrl || undefined,
    },
    results,
    errors,
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log("SaleADS Mi Negocio validation report:");
  console.log(JSON.stringify(finalReport, null, 2));

  expect(errors, `Workflow failures:\n${errors.join("\n")}`).toEqual([]);
});
