import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

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

type ReportValue = {
  status: "PASS" | "FAIL";
  details: string;
};

const REPORT_KEYS: ReportKey[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
];

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 7000 });
  } catch {
    // Some pages keep long-lived connections; allow continuation.
  }
  await page.waitForTimeout(500);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator.first()).toBeVisible();
  await locator.first().click();
  await waitForUi(page);
}

async function firstVisible(candidates: Locator[]): Promise<Locator> {
  for (const candidate of candidates) {
    try {
      await candidate.first().waitFor({ state: "visible", timeout: 2000 });
      return candidate.first();
    } catch {
      // Try next candidate.
    }
  }
  throw new Error("Could not find any visible locator from candidates.");
}

async function checkpoint(
  testInfo: TestInfo,
  name: string,
  page: Page,
  fullPage = false,
): Promise<void> {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function legalNavigation(
  page: Page,
  linkLocator: Locator,
  headingRegex: RegExp,
  screenshotName: string,
  reportName: ReportKey,
  testInfo: TestInfo,
): Promise<string> {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);

  await clickAndWait(linkLocator, page);

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    try {
      await popup.waitForLoadState("networkidle", { timeout: 7000 });
    } catch {
      // Allow continuation when pages have background requests.
    }
  }

  const heading = await firstVisible([
    targetPage.getByRole("heading", { name: headingRegex }),
    targetPage.getByText(headingRegex),
  ]);
  await expect(heading).toBeVisible();

  const legalBodyText = (await targetPage.locator("body").innerText()).trim();
  expect(legalBodyText.length).toBeGreaterThan(120);

  await checkpoint(testInfo, screenshotName, targetPage, true);
  const finalUrl = targetPage.url();
  console.log(`[EVIDENCE] ${reportName} URL: ${finalUrl}`);

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: Record<ReportKey, ReportValue> = Object.fromEntries(
    REPORT_KEYS.map((key) => [key, { status: "FAIL", details: "Not executed" }]),
  ) as Record<ReportKey, ReportValue>;

  const setPass = (key: ReportKey, details: string): void => {
    report[key] = { status: "PASS", details };
  };
  const setFail = (key: ReportKey, error: unknown): void => {
    const details = error instanceof Error ? error.message : String(error);
    report[key] = { status: "FAIL", details };
  };

  try {
    const startUrl = process.env.SALEADS_URL || process.env.BASE_URL;
    if (!startUrl) {
      throw new Error("Missing SALEADS_URL/BASE_URL. Provide the login URL of the target environment.");
    }

    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    // 1) Login with Google
    try {
      const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
      const signInButton = await firstVisible([
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
      ]);
      await clickAndWait(signInButton, page);

      // Google account picker may open either in popup or same tab.
      const googlePage = await popupPromise;

      const possibleGooglePage = googlePage ?? page;
      const accountOption = possibleGooglePage.getByText("juanlucasbarbiergarzon@gmail.com");
      if (await accountOption.first().isVisible().catch(() => false)) {
        await accountOption.first().click();
      }

      await waitForUi(page);

      const sidebar = await firstVisible([
        page.getByRole("navigation"),
        page.locator("aside"),
        page.getByText(/mi negocio|negocio/i),
      ]);

      await expect(sidebar).toBeVisible();
      await checkpoint(testInfo, "01-dashboard-loaded", page);
      setPass("Login", "Dashboard loaded and sidebar visible.");
    } catch (error) {
      setFail("Login", error);
    }

    // 2) Open Mi Negocio menu
    try {
      const negocioMenu = await firstVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
        page.getByText(/negocio/i),
      ]);
      await clickAndWait(negocioMenu, page);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

      await checkpoint(testInfo, "02-mi-negocio-menu-expanded", page);
      setPass("Mi Negocio menu", "Mi Negocio menu expanded with both submenu options.");
    } catch (error) {
      setFail("Mi Negocio menu", error);
    }

    // 3) Validate Agregar Negocio modal
    try {
      await clickAndWait(page.getByText(/agregar negocio/i).first(), page);

      await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
      const nameField = page.getByLabel(/nombre del negocio/i).first();
      await expect(nameField).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

      await checkpoint(testInfo, "03-agregar-negocio-modal", page);

      // Optional interaction
      await nameField.fill("Negocio Prueba Automatización");
      await clickAndWait(page.getByRole("button", { name: /cancelar/i }).first(), page);
      setPass("Agregar Negocio modal", "Modal validated and cancelled after optional typing.");
    } catch (error) {
      setFail("Agregar Negocio modal", error);
    }

    // 4) Open Administrar Negocios
    try {
      const miNegocioMenu = await firstVisible([
        page.getByText(/mi negocio/i),
        page.getByText(/negocio/i),
      ]);
      await clickAndWait(miNegocioMenu, page);
      await clickAndWait(page.getByText(/administrar negocios/i).first(), page);

      await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

      await checkpoint(testInfo, "04-administrar-negocios-page", page, true);
      setPass("Administrar Negocios view", "Account page sections are visible.");
    } catch (error) {
      setFail("Administrar Negocios view", error);
    }

    // 5) Validate Información General
    try {
      const emailLocator = page.locator('text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i').first();
      await expect(emailLocator).toBeVisible();

      // Name can vary; verify a likely user display field exists near profile area.
      const nameCandidate = await firstVisible([
        page.locator("[data-testid*=name]").first(),
        page.getByText(/nombre/i).first(),
        page.locator("h1, h2, h3").first(),
      ]);
      await expect(nameCandidate).toBeVisible();

      await expect(page.getByText(/business plan/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
      setPass("Información General", "User, plan, and action button validated.");
    } catch (error) {
      setFail("Información General", error);
    }

    // 6) Validate Detalles de la Cuenta
    try {
      await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
      setPass("Detalles de la Cuenta", "Account details labels are visible.");
    } catch (error) {
      setFail("Detalles de la Cuenta", error);
    }

    // 7) Validate Tus Negocios
    try {
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
      setPass("Tus Negocios", "Business list section and quota text are visible.");
    } catch (error) {
      setFail("Tus Negocios", error);
    }

    // 8) Validate Términos y Condiciones
    try {
      const termsUrl = await legalNavigation(
        page,
        await firstVisible([
          page.getByRole("link", { name: /t[ée]rminos y condiciones/i }),
          page.getByText(/t[ée]rminos y condiciones/i),
        ]),
        /t[ée]rminos y condiciones/i,
        "05-terminos-y-condiciones",
        "Términos y Condiciones",
        testInfo,
      );
      setPass("Términos y Condiciones", `Validated legal page. URL: ${termsUrl}`);
    } catch (error) {
      setFail("Términos y Condiciones", error);
    }

    // 9) Validate Política de Privacidad
    try {
      const privacyUrl = await legalNavigation(
        page,
        await firstVisible([
          page.getByRole("link", { name: /pol[ií]tica de privacidad/i }),
          page.getByText(/pol[ií]tica de privacidad/i),
        ]),
        /pol[ií]tica de privacidad/i,
        "06-politica-de-privacidad",
        "Política de Privacidad",
        testInfo,
      );
      setPass("Política de Privacidad", `Validated legal page. URL: ${privacyUrl}`);
    } catch (error) {
      setFail("Política de Privacidad", error);
    }
  } finally {
    console.log("========== FINAL REPORT ==========");
    for (const key of REPORT_KEYS) {
      const entry = report[key];
      console.log(`${key}: ${entry.status} - ${entry.details}`);
    }
    console.log("==================================");
  }

  const failed = REPORT_KEYS.filter((key) => report[key].status === "FAIL");
  expect(
    failed,
    `One or more validation groups failed: ${failed.join(", ")}.`,
  ).toEqual([]);
});
