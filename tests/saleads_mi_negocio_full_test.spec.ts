import { expect, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs/promises";

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

type ReportEntry = {
  status: "PASS" | "FAIL";
  details: string;
  evidence?: string[];
  finalUrl?: string;
};

const reportKeys: ReportKey[] = [
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

function initReport(): Record<ReportKey, ReportEntry> {
  return reportKeys.reduce(
    (acc, key) => ({
      ...acc,
      [key]: {
        status: "FAIL",
        details: "Not executed"
      }
    }),
    {} as Record<ReportKey, ReportEntry>
  );
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function isVisible(locator: Locator, timeout = 2000): Promise<boolean> {
  return locator.isVisible({ timeout }).catch(() => false);
}

async function findFirstVisible(page: Page, texts: (string | RegExp)[]): Promise<Locator> {
  const candidates: Locator[] = [];

  for (const text of texts) {
    candidates.push(page.getByRole("button", { name: text }).first());
    candidates.push(page.getByRole("link", { name: text }).first());
    candidates.push(page.getByRole("menuitem", { name: text }).first());
    candidates.push(page.getByRole("tab", { name: text }).first());
    candidates.push(page.getByText(text, { exact: typeof text === "string" }).first());
  }

  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate;
    }
  }

  throw new Error(`No visible element found for: ${texts.map(String).join(", ")}`);
}

async function clickByText(page: Page, texts: (string | RegExp)[]): Promise<void> {
  const target = await findFirstVisible(page, texts);
  await target.click();
  await waitForUi(page);
}

async function clickIfVisible(page: Page, texts: (string | RegExp)[]): Promise<boolean> {
  try {
    await clickByText(page, texts);
    return true;
  } catch {
    return false;
  }
}

async function ensureSidebarVisible(page: Page): Promise<void> {
  const sidebarCandidates: Locator[] = [
    page.locator("aside").first(),
    page.locator("nav").filter({ hasText: /mi negocio|negocio|dashboard|cuenta/i }).first(),
    page.getByText(/mi negocio|negocio/i).first()
  ];

  await expect
    .poll(
      async () => {
        for (const candidate of sidebarCandidates) {
          if (await isVisible(candidate)) {
            return true;
          }
        }
        return false;
      },
      {
        timeout: 90000,
        message: "Main app interface (left sidebar) did not become visible."
      }
    )
    .toBeTruthy();
}

async function maybeSelectGoogleAccount(page: Page): Promise<void> {
  const account = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
  if (await isVisible(account, 12000)) {
    await account.click();
    await waitForUi(page);
    return;
  }

  const accountSelectorVisible = await isVisible(
    page.getByText(/choose an account|elige una cuenta/i).first(),
    3000
  );
  if (accountSelectorVisible) {
    throw new Error("Google account selector is visible, but the expected account was not found.");
  }
}

async function saveCheckpoint(page: Page, outputPath: string, fullPage = false): Promise<void> {
  await page.screenshot({ path: outputPath, fullPage });
}

async function assertTextVisible(page: Page, text: string | RegExp, timeout = 30000): Promise<void> {
  await expect(page.getByText(text).first()).toBeVisible({ timeout });
}

async function validateLegalLink(
  appPage: Page,
  linkTexts: (string | RegExp)[],
  expectedHeading: RegExp,
  screenshotPath: string
): Promise<{ finalUrl: string; openedNewTab: boolean }> {
  const context = appPage.context();
  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);

  await clickByText(appPage, linkTexts);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  await targetPage.waitForLoadState("domcontentloaded");
  await expect(targetPage.getByText(expectedHeading).first()).toBeVisible({ timeout: 45000 });

  const legalText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(legalText.length, "Expected legal page content text to be visible.").toBeGreaterThan(120);

  await saveCheckpoint(targetPage, screenshotPath, true);

  return {
    finalUrl: targetPage.url(),
    openedNewTab: popup !== null
  };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = initReport();
  const failures: string[] = [];
  const appPage = page;

  const markPass = (key: ReportKey, details: string, evidence?: string[], finalUrl?: string): void => {
    report[key] = { status: "PASS", details, evidence, finalUrl };
  };

  const markFail = async (key: ReportKey, error: unknown): Promise<void> => {
    const details = error instanceof Error ? error.message : String(error);
    report[key] = { status: "FAIL", details };
    failures.push(`${key}: ${details}`);

    try {
      const failureShot = testInfo.outputPath(`${key.replace(/\s+/g, "_").toLowerCase()}_failure.png`);
      await appPage.screenshot({ path: failureShot, fullPage: true });
    } catch {
      // Best effort only, because failures can happen on a closed/reloaded page.
    }
  };

  // Step 1: Login with Google
  try {
    if (appPage.url() === "about:blank") {
      const baseUrl = process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;
      if (!baseUrl) {
        throw new Error(
          "Browser started on about:blank. Provide SALEADS_BASE_URL/BASE_URL or pre-open the SaleADS login page."
        );
      }
      await appPage.goto(baseUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(appPage);
    }

    const loginButton = await findFirstVisible(appPage, [
      /sign in with google/i,
      /iniciar sesi[oó]n con google/i,
      /continuar con google/i,
      /google/i,
      /login/i,
      /iniciar sesi[oó]n/i
    ]);

    const popupPromise = appPage.context().waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(appPage);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await maybeSelectGoogleAccount(popup);
    } else {
      await maybeSelectGoogleAccount(appPage);
    }

    await ensureSidebarVisible(appPage);
    const dashboardScreenshot = testInfo.outputPath("01-dashboard-loaded.png");
    await saveCheckpoint(appPage, dashboardScreenshot, true);
    markPass("Login", "Main app interface and sidebar are visible after Google login.", [dashboardScreenshot]);
  } catch (error) {
    await markFail("Login", error);
  }

  // Step 2: Open Mi Negocio menu
  try {
    await ensureSidebarVisible(appPage);
    await clickIfVisible(appPage, [/^negocio$/i, /negocio/i]);
    await clickByText(appPage, [/mi negocio/i]);
    await assertTextVisible(appPage, /agregar negocio/i);
    await assertTextVisible(appPage, /administrar negocios/i);

    const menuScreenshot = testInfo.outputPath("02-mi-negocio-menu-expanded.png");
    await saveCheckpoint(appPage, menuScreenshot, true);
    markPass(
      "Mi Negocio menu",
      "Mi Negocio expanded and both submenu items are visible.",
      [menuScreenshot]
    );
  } catch (error) {
    await markFail("Mi Negocio menu", error);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await clickByText(appPage, [/agregar negocio/i]);
    await assertTextVisible(appPage, /crear nuevo negocio/i);
    await assertTextVisible(appPage, /nombre del negocio/i);
    await assertTextVisible(appPage, /tienes 2 de 3 negocios/i);
    await expect(appPage.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    const modalScreenshot = testInfo.outputPath("03-agregar-negocio-modal.png");
    await saveCheckpoint(appPage, modalScreenshot, true);

    const nameInputCandidates: Locator[] = [
      appPage.getByLabel(/nombre del negocio/i).first(),
      appPage.getByPlaceholder(/nombre del negocio/i).first(),
      appPage.locator("input[type='text']").first()
    ];

    for (const input of nameInputCandidates) {
      if (await isVisible(input)) {
        await input.click();
        await input.fill("Negocio Prueba Automatización");
        break;
      }
    }

    await appPage.getByRole("button", { name: /cancelar/i }).first().click();
    await waitForUi(appPage);

    markPass(
      "Agregar Negocio modal",
      "Modal content and action buttons were validated successfully.",
      [modalScreenshot]
    );
  } catch (error) {
    await markFail("Agregar Negocio modal", error);
  }

  // Step 4: Open Administrar Negocios
  try {
    await clickIfVisible(appPage, [/mi negocio/i]);
    await clickByText(appPage, [/administrar negocios/i]);
    await assertTextVisible(appPage, /información general/i, 60000);
    await assertTextVisible(appPage, /detalles de la cuenta/i);
    await assertTextVisible(appPage, /tus negocios/i);
    await assertTextVisible(appPage, /sección legal/i);

    const accountScreenshot = testInfo.outputPath("04-administrar-negocios-full.png");
    await saveCheckpoint(appPage, accountScreenshot, true);
    markPass(
      "Administrar Negocios view",
      "All required account sections are visible.",
      [accountScreenshot]
    );
  } catch (error) {
    await markFail("Administrar Negocios view", error);
  }

  // Step 5: Validate Información General
  try {
    await assertTextVisible(appPage, /business plan/i);
    await expect(appPage.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
    await expect(
      appPage.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()
    ).toBeVisible();
    await assertTextVisible(appPage, /nombre|usuario|perfil|bienvenido|hola/i);

    markPass("Información General", "Name-like text, email, plan and change plan action are visible.");
  } catch (error) {
    await markFail("Información General", error);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await assertTextVisible(appPage, /cuenta creada/i);
    await assertTextVisible(appPage, /estado activo/i);
    await assertTextVisible(appPage, /idioma seleccionado/i);

    markPass("Detalles de la Cuenta", "Account details fields are visible.");
  } catch (error) {
    await markFail("Detalles de la Cuenta", error);
  }

  // Step 7: Validate Tus Negocios
  try {
    await assertTextVisible(appPage, /tus negocios/i);
    await clickIfVisible(appPage, [/agregar negocio/i]);
    await clickIfVisible(appPage, [/cancelar/i]);
    await assertTextVisible(appPage, /agregar negocio/i);
    await assertTextVisible(appPage, /tienes 2 de 3 negocios/i);

    const businessItems =
      (await appPage.locator("li, tr, [role='row'], [class*='negocio'], [class*='business']").count()) > 0;
    const headingCount = await appPage.getByText(/negocio/i).count();
    expect(
      businessItems || headingCount > 2,
      "Expected visible business list/content under 'Tus Negocios'."
    ).toBeTruthy();

    markPass("Tus Negocios", "Business section, usage text and add business action are visible.");
  } catch (error) {
    await markFail("Tus Negocios", error);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const termsScreenshot = testInfo.outputPath("05-terminos-y-condiciones.png");
    const termsResult = await validateLegalLink(
      appPage,
      [/términos y condiciones/i, /terminos y condiciones/i],
      /términos y condiciones|terminos y condiciones/i,
      termsScreenshot
    );

    if (termsResult.openedNewTab) {
      const latestPage = appPage.context().pages().at(-1);
      if (latestPage && latestPage !== appPage) {
        await latestPage.close();
        await appPage.bringToFront();
        await waitForUi(appPage);
      }
    } else {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(appPage);
    }

    markPass(
      "Términos y Condiciones",
      "Legal terms page opened and content was validated.",
      [termsScreenshot],
      termsResult.finalUrl
    );
  } catch (error) {
    await markFail("Términos y Condiciones", error);
  }

  // Step 9: Validate Política de Privacidad
  try {
    const privacyScreenshot = testInfo.outputPath("06-politica-de-privacidad.png");
    const privacyResult = await validateLegalLink(
      appPage,
      [/política de privacidad/i, /politica de privacidad/i],
      /política de privacidad|politica de privacidad/i,
      privacyScreenshot
    );

    if (privacyResult.openedNewTab) {
      const latestPage = appPage.context().pages().at(-1);
      if (latestPage && latestPage !== appPage) {
        await latestPage.close();
      }
      await appPage.bringToFront();
      await waitForUi(appPage);
    } else {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(appPage);
    }

    markPass(
      "Política de Privacidad",
      "Privacy policy page opened and content was validated.",
      [privacyScreenshot],
      privacyResult.finalUrl
    );
  } catch (error) {
    await markFail("Política de Privacidad", error);
  }

  // Step 10: final report artifact
  const finalReportPath = testInfo.outputPath("saleads_mi_negocio_final_report.json");
  await fs.writeFile(finalReportPath, JSON.stringify(report, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json"
  });

  if (failures.length > 0) {
    throw new Error(`One or more validations failed:\n- ${failures.join("\n- ")}`);
  }
});
