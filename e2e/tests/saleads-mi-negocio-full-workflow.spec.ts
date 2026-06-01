import { expect, Page, TestInfo, test } from "@playwright/test";
import { promises as fs } from "fs";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details?: string;
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
  "Política de Privacidad"
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];

const EMAIL_REGEX = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

async function clickAndWait(page: Page, locator: ReturnType<Page["locator"]>): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToSettle(page);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<void> {
  const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const screenshotPath = testInfo.outputPath(`${safeName}.png`);

  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png"
  });
}

function menuItem(page: Page, textPattern: RegExp) {
  return page.getByRole("menuitem", { name: textPattern }).first();
}

async function getVisibleByText(page: Page, textPattern: RegExp) {
  let locator = page.getByText(textPattern).first();

  if (await locator.isVisible().catch(() => false)) {
    return locator;
  }

  locator = page.getByRole("button", { name: textPattern }).first();
  if (await locator.isVisible().catch(() => false)) {
    return locator;
  }

  locator = page.getByRole("link", { name: textPattern }).first();
  if (await locator.isVisible().catch(() => false)) {
    return locator;
  }

  return page.getByText(textPattern).first();
}

async function validateLegalLink(
  appPage: Page,
  testInfo: TestInfo,
  linkName: RegExp,
  headingPattern: RegExp,
  screenshotName: string
): Promise<string> {
  const context = appPage.context();
  const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);

  const link = await getVisibleByText(appPage, linkName);
  await expect(link).toBeVisible();
  await link.click();

  const popup = await popupPromise;
  if (popup) {
    await waitForUiToSettle(popup);

    const heading = popup.getByRole("heading", { name: headingPattern }).first();
    if (await heading.isVisible().catch(() => false)) {
      await expect(heading).toBeVisible();
    } else {
      await expect(popup.getByText(headingPattern).first()).toBeVisible();
    }

    await expect(
      popup.getByText(/(condiciones|privacidad|datos|informaci[oó]n|usuarios|uso)/i).first()
    ).toBeVisible();
    await captureCheckpoint(popup, testInfo, screenshotName, true);

    const popupUrl = popup.url();
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToSettle(appPage);
    return popupUrl;
  }

  await waitForUiToSettle(appPage);
  const heading = appPage.getByRole("heading", { name: headingPattern }).first();
  if (await heading.isVisible().catch(() => false)) {
    await expect(heading).toBeVisible();
  } else {
    await expect(appPage.getByText(headingPattern).first()).toBeVisible();
  }

  await expect(
    appPage.getByText(/(condiciones|privacidad|datos|informaci[oó]n|usuarios|uso)/i).first()
  ).toBeVisible();
  await captureCheckpoint(appPage, testInfo, screenshotName, true);

  const currentUrl = appPage.url();
  await appPage.goBack().catch(() => Promise.resolve());
  await waitForUiToSettle(appPage);
  return currentUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const results = {} as Record<ReportField, StepResult>;
  for (const field of REPORT_FIELDS) {
    results[field] = { status: "FAIL", details: "Not executed" };
  }

  const legalUrls: Record<string, string> = {};

  const executeStep = async (field: ReportField, action: () => Promise<void>) => {
    try {
      await action();
      results[field] = { status: "PASS" };
    } catch (error) {
      results[field] = { status: "FAIL", details: getErrorMessage(error) };
    }
  };

  await executeStep("Login", async () => {
    if (page.url() === "about:blank") {
      const loginUrl = process.env.SALEADS_URL;

      if (!loginUrl) {
        throw new Error(
          "No login page found. Pre-open the SaleADS login page or set SALEADS_URL."
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }

    const googleLoginButton = await getVisibleByText(
      page,
      /(sign in with google|iniciar sesi[oó]n con google|login con google|google)/i
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, googleLoginButton);
    const popup = await popupPromise;

    if (popup) {
      await waitForUiToSettle(popup);
      const accountOption = popup
        .getByText(/juanlucasbarbiergarzon@gmail\.com/i)
        .first();

      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }

      await popup.waitForTimeout(1500);
      await page.bringToFront();
    } else {
      const inlineAccountOption = page
        .getByText(/juanlucasbarbiergarzon@gmail\.com/i)
        .first();

      if (await inlineAccountOption.isVisible().catch(() => false)) {
        await clickAndWait(page, inlineAccountOption);
      }
    }

    await expect(page.getByText(/(mi negocio|negocio|dashboard|inicio)/i).first()).toBeVisible({
      timeout: 60000
    });
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
    await captureCheckpoint(page, testInfo, "dashboard-loaded");
  });

  await executeStep("Mi Negocio menu", async () => {
    const negocioOption = await getVisibleByText(page, /^negocio$/i);
    await clickAndWait(page, negocioOption);

    await expect(await getVisibleByText(page, /agregar negocio/i)).toBeVisible();
    await expect(await getVisibleByText(page, /administrar negocios/i)).toBeVisible();
    await captureCheckpoint(page, testInfo, "mi-negocio-expanded-menu");
  });

  await executeStep("Agregar Negocio modal", async () => {
    await clickAndWait(page, await getVisibleByText(page, /agregar negocio/i));

    const dialog = page.getByRole("dialog").first();
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(dialog.getByText(/nombre del negocio/i)).toBeVisible();
    await expect(dialog.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(dialog.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(dialog.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    const businessNameInput = dialog.getByPlaceholder(/nombre del negocio/i).first();
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatizacion");
    } else {
      const inputByLabel = dialog.getByLabel(/nombre del negocio/i).first();
      await inputByLabel.click();
      await inputByLabel.fill("Negocio Prueba Automatizacion");
    }

    await captureCheckpoint(page, testInfo, "agregar-negocio-modal");
    await clickAndWait(page, dialog.getByRole("button", { name: /cancelar/i }));
  });

  await executeStep("Administrar Negocios view", async () => {
    const administrarNegociosOption = await getVisibleByText(page, /administrar negocios/i);
    if (!(await administrarNegociosOption.isVisible().catch(() => false))) {
      await clickAndWait(page, await getVisibleByText(page, /^negocio$/i));
    }

    await clickAndWait(page, await getVisibleByText(page, /administrar negocios/i));
    await expect(await getVisibleByText(page, /informaci[oó]n general/i)).toBeVisible({
      timeout: 20000
    });
    await expect(await getVisibleByText(page, /detalles de la cuenta/i)).toBeVisible();
    await expect(await getVisibleByText(page, /tus negocios/i)).toBeVisible();
    await expect(await getVisibleByText(page, /(secci[oó]n legal|legal)/i)).toBeVisible();
    await captureCheckpoint(page, testInfo, "administrar-negocios-account-page", true);
  });

  await executeStep("Información General", async () => {
    const infoGeneral = await getVisibleByText(page, /informaci[oó]n general/i);
    await expect(infoGeneral).toBeVisible();
    await expect(page.getByText(EMAIL_REGEX).first()).toBeVisible();
    await expect(await getVisibleByText(page, /business plan/i)).toBeVisible();
    await expect(await getVisibleByText(page, /cambiar plan/i)).toBeVisible();
    await expect(await getVisibleByText(page, /(nombre|usuario|perfil|cuenta)/i)).toBeVisible();
  });

  await executeStep("Detalles de la Cuenta", async () => {
    await expect(await getVisibleByText(page, /detalles de la cuenta/i)).toBeVisible();
    await expect(await getVisibleByText(page, /cuenta creada/i)).toBeVisible();
    await expect(await getVisibleByText(page, /estado activo/i)).toBeVisible();
    await expect(await getVisibleByText(page, /idioma seleccionado/i)).toBeVisible();
  });

  await executeStep("Tus Negocios", async () => {
    const tusNegociosSection = await getVisibleByText(page, /tus negocios/i);
    await expect(tusNegociosSection).toBeVisible();

    const businessListCandidates = page
      .locator("li, tr, article, [role='listitem'], [data-testid*='business']")
      .filter({ hasText: /./ });
    const businessListCount = await businessListCandidates.count();
    expect(businessListCount).toBeGreaterThan(0);

    await expect(await getVisibleByText(page, /agregar negocio/i)).toBeVisible();
    await expect(await getVisibleByText(page, /tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await executeStep("Términos y Condiciones", async () => {
    const termsUrl = await validateLegalLink(
      page,
      testInfo,
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "terminos-y-condiciones"
    );
    legalUrls["terminos_y_condiciones"] = termsUrl;
  });

  await executeStep("Política de Privacidad", async () => {
    const privacyUrl = await validateLegalLink(
      page,
      testInfo,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "politica-de-privacidad"
    );
    legalUrls["politica_de_privacidad"] = privacyUrl;
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    finalApplicationUrl: page.url(),
    legalUrls,
    results
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedSteps = Object.entries(results).filter(([, value]) => value.status === "FAIL");
  expect(failedSteps, `Failed validations: ${JSON.stringify(failedSteps, null, 2)}`).toEqual([]);
});
