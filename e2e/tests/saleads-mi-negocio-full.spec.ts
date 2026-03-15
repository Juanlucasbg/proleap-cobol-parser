import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs/promises";

type ReportStatus = "PASS" | "FAIL";

type FinalReport = {
  environmentUrl: string;
  generatedAt: string;
  results: Record<string, ReportStatus>;
  errors: Record<string, string>;
  screenshots: string[];
  urls: Record<string, string>;
};

const REQUIRED_REPORT_FIELDS = [
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

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function isVisible(locator: Locator, timeout = 0): Promise<boolean> {
  try {
    if (timeout > 0) {
      await expect(locator.first()).toBeVisible({ timeout });
      return true;
    }
    return await locator.first().isVisible();
  } catch {
    return false;
  }
}

async function firstVisibleLocator(candidates: Locator[], timeoutMs = 15_000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      if (await isVisible(candidate)) {
        return candidate.first();
      }
    }
    await candidates[0].page().waitForTimeout(250);
  }

  throw new Error("Could not find any visible locator from candidates.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: FinalReport = {
    environmentUrl: "",
    generatedAt: new Date().toISOString(),
    results: Object.fromEntries(REQUIRED_REPORT_FIELDS.map((field) => [field, "FAIL"])),
    errors: {},
    screenshots: [],
    urls: {}
  };

  const captureScreenshot = async (name: string, fullPage = false): Promise<void> => {
    const path = testInfo.outputPath(name);
    await page.screenshot({ path, fullPage });
    report.screenshots.push(name);
    await testInfo.attach(name, { path, contentType: "image/png" });
  };

  const runValidation = async (field: string, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      report.results[field] = "PASS";
    } catch (error) {
      report.results[field] = "FAIL";
      report.errors[field] = error instanceof Error ? error.message : String(error);
    }
  };

  // Step 1: Login with Google.
  await runValidation("Login", async () => {
    const configuredLoginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
    if (page.url() === "about:blank" && configuredLoginUrl) {
      await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    if (page.url() === "about:blank") {
      throw new Error(
        "Browser is on about:blank. Open the SaleADS login page first, or set SALEADS_LOGIN_URL/SALEADS_BASE_URL."
      );
    }

    report.environmentUrl = page.url();

    const loginButton = await firstVisibleLocator([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[oó]n con google/i),
      page.getByText(/continuar con google/i)
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(page, loginButton);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const popupAccount = popup.getByText(ACCOUNT_EMAIL, { exact: true });
      if (await isVisible(popupAccount, 10_000)) {
        await popupAccount.click();
      }
      await waitForUi(popup);
      await popup.close().catch(() => undefined);
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const sameTabAccount = page.getByText(ACCOUNT_EMAIL, { exact: true });
      if (await isVisible(sameTabAccount, 10_000)) {
        await clickAndWait(page, sameTabAccount);
      }
    }

    const sidebar = await firstVisibleLocator([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("[class*='sidebar'], [id*='sidebar']")
    ]);

    await expect(sidebar).toBeVisible({ timeout: 30_000 });
    await captureScreenshot("01-dashboard-loaded.png", true);
  });

  // Step 2: Open Mi Negocio menu.
  await runValidation("Mi Negocio menu", async () => {
    const sidebar = await firstVisibleLocator([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("[class*='sidebar'], [id*='sidebar']")
    ]);

    const negocioSection = await firstVisibleLocator([
      sidebar.getByText(/negocio/i),
      page.getByText(/negocio/i)
    ]);
    await expect(negocioSection).toBeVisible();

    const miNegocio = await firstVisibleLocator([
      sidebar.getByRole("button", { name: /mi negocio/i }),
      sidebar.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/^mi negocio$/i)
    ]);

    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 15_000 });
    await captureScreenshot("02-mi-negocio-menu-expanded.png");
  });

  // Step 3: Validate Agregar Negocio modal.
  await runValidation("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisibleLocator([
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 15_000 });

    await captureScreenshot("03-agregar-negocio-modal.png");

    const nombreNegocioInput = page.getByLabel(/nombre del negocio/i).first();
    await nombreNegocioInput.click();
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }).first());
  });

  // Step 4: Open Administrar Negocios and validate sections.
  await runValidation("Administrar Negocios view", async () => {
    if (!(await isVisible(page.getByText(/administrar negocios/i).first()))) {
      const miNegocio = await firstVisibleLocator([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/^mi negocio$/i)
      ]);
      await clickAndWait(page, miNegocio);
    }

    const administrarNegocios = await firstVisibleLocator([
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible({ timeout: 30_000 });
    await captureScreenshot("04-administrar-negocios.png", true);
  });

  // Step 5: Validate Información General.
  await runValidation("Información General", async () => {
    const bodyText = await page.locator("body").innerText();
    const hasAnyEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(bodyText);
    expect(hasAnyEmail).toBeTruthy();

    await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible({ timeout: 15_000 });

    // User name in account panels is often rendered as heading/subheading text.
    const visibleHeadings = page.getByRole("heading");
    expect(await visibleHeadings.count()).toBeGreaterThan(0);
  });

  // Step 6: Validate Detalles de la Cuenta.
  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/estado activo/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 15_000 });
  });

  // Step 7: Validate Tus Negocios.
  await runValidation("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 15_000 });
  });

  const validateLegalPage = async (
    linkPattern: RegExp,
    expectedHeadingPattern: RegExp,
    screenshotName: string,
    reportField: string
  ): Promise<void> => {
    const appUrlBeforeClick = page.url();
    const legalLink = await firstVisibleLocator([
      page.getByRole("link", { name: linkPattern }),
      page.getByRole("button", { name: linkPattern }),
      page.getByText(linkPattern)
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(page, legalLink);
    const popup = await popupPromise;
    const targetPage = popup ?? page;

    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);

    const heading = await firstVisibleLocator([
      targetPage.getByRole("heading", { name: expectedHeadingPattern }),
      targetPage.getByText(expectedHeadingPattern)
    ]);
    await expect(heading).toBeVisible({ timeout: 20_000 });

    const legalText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
    expect(legalText.length).toBeGreaterThan(200);

    const screenshotPath = testInfo.outputPath(screenshotName);
    await targetPage.screenshot({ path: screenshotPath, fullPage: true });
    report.screenshots.push(screenshotName);
    await testInfo.attach(screenshotName, { path: screenshotPath, contentType: "image/png" });

    report.urls[reportField] = targetPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
      return;
    }

    if (page.url() !== appUrlBeforeClick) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
      });
      await waitForUi(page);
    }
  };

  // Step 8: Validate Términos y Condiciones.
  await runValidation("Términos y Condiciones", async () => {
    await validateLegalPage(
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "05-terminos-y-condiciones.png",
      "Términos y Condiciones"
    );
  });

  // Step 9: Validate Política de Privacidad.
  await runValidation("Política de Privacidad", async () => {
    await validateLegalPage(
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad.png",
      "Política de Privacidad"
    );
  });

  // Step 10: Final report output.
  const finalReportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(finalReportPath, `${JSON.stringify(report, null, 2)}\n`, "utf-8");
  await testInfo.attach("final-report", { path: finalReportPath, contentType: "application/json" });

  const failedFields = REQUIRED_REPORT_FIELDS.filter((field) => report.results[field] === "FAIL");
  expect(
    failedFields,
    `Final validation report contains FAIL results: ${failedFields.join(", ") || "none"}`
  ).toEqual([]);
});
