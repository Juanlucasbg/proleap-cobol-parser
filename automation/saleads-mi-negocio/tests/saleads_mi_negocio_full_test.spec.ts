import { expect, Locator, Page, test } from "@playwright/test";
import { promises as fs } from "node:fs";

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL;

type StepStatus = "PASS" | "FAIL";
type StepName =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepReport = {
  status: StepStatus;
  details?: string;
  url?: string;
};

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
}

async function firstVisible(candidates: Locator[], timeoutPerCandidate = 3_000): Promise<Locator> {
  for (const candidate of candidates) {
    try {
      const item = candidate.first();
      await item.waitFor({ state: "visible", timeout: timeoutPerCandidate });
      return item;
    } catch {
      // Try next candidate.
    }
  }

  throw new Error("No visible locator found for the requested UI element.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function maybeSelectGoogleAccount(page: Page): Promise<void> {
  const accountCandidates = [
    page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")),
    page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
  ];

  for (const candidate of accountCandidates) {
    const account = candidate.first();
    if (await account.isVisible().catch(() => false)) {
      await account.click();
      await waitForUi(page);
      return;
    }
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: Record<StepName, StepReport> = {
    Login: { status: "FAIL", details: "Step not executed." },
    "Mi Negocio menu": { status: "FAIL", details: "Step not executed." },
    "Agregar Negocio modal": { status: "FAIL", details: "Step not executed." },
    "Administrar Negocios view": { status: "FAIL", details: "Step not executed." },
    "Información General": { status: "FAIL", details: "Step not executed." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Step not executed." },
    "Tus Negocios": { status: "FAIL", details: "Step not executed." },
    "Términos y Condiciones": { status: "FAIL", details: "Step not executed." },
    "Política de Privacidad": { status: "FAIL", details: "Step not executed." },
  };

  const errors: string[] = [];

  const saveScreenshot = async (name: string, targetPage = page, fullPage = false): Promise<void> => {
    await targetPage.screenshot({
      path: testInfo.outputPath(`${name}.png`),
      fullPage,
    });
  };

  const runStep = async (name: StepName, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[name] = { status: "PASS" };
    } catch (error) {
      const details = error instanceof Error ? error.message : String(error);
      report[name] = { status: "FAIL", details };
      errors.push(`${name}: ${details}`);
    }
  };

  const sidebarLocator = page.locator("aside, nav").first();

  await runStep("Login", async () => {
    if (SALEADS_LOGIN_URL) {
      await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const alreadyLoggedIn = await page.getByText(/mi\s*negocio|negocio/i).first().isVisible().catch(() => false);

    if (!alreadyLoggedIn) {
      const loginTrigger = await firstVisible([
        page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n|continuar/i }),
        page.getByRole("link", { name: /google|sign in|iniciar sesi[oó]n|continuar/i }),
        page.getByText(/google|sign in|iniciar sesi[oó]n|continuar/i),
      ]);

      const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
      await clickAndWait(page, loginTrigger);

      const popup = await popupPromise;
      if (popup) {
        await waitForUi(popup);
        await maybeSelectGoogleAccount(popup);
      } else {
        await maybeSelectGoogleAccount(page);
      }
    }

    await expect(page.getByText(/mi\s*negocio|negocio/i).first()).toBeVisible({ timeout: 60_000 });
    await expect(sidebarLocator).toBeVisible({ timeout: 60_000 });
    await saveScreenshot("01-dashboard-loaded", page, true);
  });

  await runStep("Mi Negocio menu", async () => {
    await expect(sidebarLocator).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 20_000 });

    const miNegocioToggle = await firstVisible([
      page.getByRole("button", { name: /mi\s*negocio/i }),
      page.getByRole("link", { name: /mi\s*negocio/i }),
      page.getByText(/mi\s*negocio/i),
    ]);

    await clickAndWait(page, miNegocioToggle);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await saveScreenshot("02-mi-negocio-menu-expanded", page, true);
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickAndWait(page, page.getByText(/agregar negocio/i).first());

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 20_000 });

    const nameField = page.getByLabel(/nombre del negocio/i);
    await nameField.click();
    await nameField.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }));
    await saveScreenshot("03-agregar-negocio-modal", page, true);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocioToggle = await firstVisible([
        page.getByRole("button", { name: /mi\s*negocio/i }),
        page.getByRole("link", { name: /mi\s*negocio/i }),
        page.getByText(/mi\s*negocio/i),
      ]);
      await clickAndWait(page, miNegocioToggle);
    }

    await clickAndWait(page, page.getByText(/administrar negocios/i).first());

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible({ timeout: 30_000 });
    await saveScreenshot("04-administrar-negocios-page", page, true);
  });

  await runStep("Información General", async () => {
    const infoSection = page.locator("section,div").filter({ hasText: /informaci[oó]n general/i }).first();
    await expect(infoSection).toBeVisible({ timeout: 20_000 });
    const infoText = await infoSection.innerText();

    expect(infoText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    const cleanedText = infoText
      .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "")
      .replace(/informaci[oó]n general|business plan|cambiar plan/gi, "")
      .trim();
    expect(cleanedText.length).toBeGreaterThan(2);

    await expect(page.getByText(/business plan/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible({ timeout: 20_000 });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  await runStep("Tus Negocios", async () => {
    const businessesSection = page.locator("section,div").filter({ hasText: /tus negocios/i }).first();
    await expect(businessesSection).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 20_000 });
  });

  const validateLegalPage = async (
    linkPattern: RegExp,
    headingPattern: RegExp,
    screenshotName: string,
  ): Promise<string> => {
    const appUrl = page.url();
    const link = await firstVisible([
      page.getByRole("link", { name: linkPattern }),
      page.getByText(linkPattern),
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(page, link);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await waitForUi(legalPage);

    const heading = await firstVisible([
      legalPage.getByRole("heading", { name: headingPattern }),
      legalPage.getByText(headingPattern),
    ]);
    await expect(heading).toBeVisible({ timeout: 30_000 });

    const bodyText = await legalPage.locator("body").innerText();
    expect(bodyText.replace(/\s+/g, " ").trim().length).toBeGreaterThan(120);

    await saveScreenshot(screenshotName, legalPage, true);
    const finalUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
      return finalUrl;
    }

    if (page.url() !== appUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        await page.goto(appUrl, { waitUntil: "domcontentloaded" });
      });
      await waitForUi(page);
    }

    return finalUrl;
  };

  await runStep("Términos y Condiciones", async () => {
    const url = await validateLegalPage(
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "05-terminos-y-condiciones",
    );
    report["Términos y Condiciones"].url = url;
  });

  await runStep("Política de Privacidad", async () => {
    const url = await validateLegalPage(
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad",
    );
    report["Política de Privacidad"].url = url;
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
    generatedAt: new Date().toISOString(),
    steps: report,
  };

  const reportPath = testInfo.outputPath("mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  if (errors.length > 0) {
    throw new Error(`Validation failures:\n${errors.join("\n")}`);
  }
});
