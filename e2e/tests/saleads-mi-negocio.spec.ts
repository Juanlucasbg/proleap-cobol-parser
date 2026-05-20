import { expect, type Locator, type Page, test } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_KEYS = [
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

type ReportKey = (typeof REPORT_KEYS)[number];
type StepStatus = "PASS" | "FAIL";

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
  await page.waitForLoadState("networkidle", { timeout: 7_000 }).catch(() => {});
}

async function firstVisible(candidates: Locator[]): Promise<Locator> {
  for (const candidate of candidates) {
    const element = candidate.first();
    const isVisible = await element.isVisible({ timeout: 2_500 }).catch(() => false);
    if (isVisible) {
      return element;
    }
  }

  throw new Error("No visible element matched the expected text.");
}

async function clickByVisibleText(page: Page, text: RegExp): Promise<void> {
  const target = await firstVisible([
    page.getByRole("button", { name: text }),
    page.getByRole("link", { name: text }),
    page.getByRole("menuitem", { name: text }),
    page.getByRole("tab", { name: text }),
    page.getByText(text)
  ]);

  await target.click();
  await waitForUiLoad(page);
}

async function saveCheckpointScreenshot(page: Page, filePath: string, fullPage = false): Promise<void> {
  await page.screenshot({ path: filePath, fullPage });
}

async function validateLegalLink(
  page: Page,
  linkText: RegExp,
  expectedHeading: RegExp,
  screenshotPath: string
): Promise<string> {
  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickByVisibleText(page, linkText);

  const popupPage = await popupPromise;
  if (popupPage) {
    await popupPage.waitForLoadState("domcontentloaded");
    await waitForUiLoad(popupPage);
    await expect(popupPage.getByRole("heading", { name: expectedHeading }).first()).toBeVisible();
    await expect(popupPage.locator("main, article, body").getByText(/\S+/).first()).toBeVisible();
    await saveCheckpointScreenshot(popupPage, screenshotPath, true);
    const finalUrl = popupPage.url();
    await popupPage.close();
    await page.bringToFront();
    return finalUrl;
  }

  await expect(page.getByRole("heading", { name: expectedHeading }).first()).toBeVisible();
  await expect(page.locator("main, article, body").getByText(/\S+/).first()).toBeVisible();
  await saveCheckpointScreenshot(page, screenshotPath, true);
  const finalUrl = page.url();
  await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUiLoad(page);
  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.setTimeout(240_000);

  const report: Record<ReportKey, StepStatus> = Object.fromEntries(
    REPORT_KEYS.map((key) => [key, "FAIL"])
  ) as Record<ReportKey, StepStatus>;
  const errors: string[] = [];
  let termsUrl = "";
  let privacyUrl = "";

  const runStep = async (key: ReportKey, action: () => Promise<void>) => {
    try {
      await action();
      report[key] = "PASS";
    } catch (error) {
      report[key] = "FAIL";
      errors.push(`${key}: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  await test.step("Step 1 - Login with Google", async () => {
    await runStep("Login", async () => {
      if (process.env.SALEADS_BASE_URL) {
        await page.goto(process.env.SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
      }

      await waitForUiLoad(page);

      const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
      const loginButton = await firstVisible([
        page.getByRole("button", { name: /sign in with google|iniciar sesi.n con google|google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi.n con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi.n con google/i)
      ]);
      await loginButton.click();
      await waitForUiLoad(page);

      const popup = await popupPromise;
      const authPage = popup ?? page;
      await waitForUiLoad(authPage);

      const accountOption = await firstVisible([
        authPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        authPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i"))
      ]).catch(() => null);

      if (accountOption) {
        await accountOption.click();
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 20_000 }).catch(() => {});
      }

      await waitForUiLoad(page);
      await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60_000 });
      await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({ timeout: 60_000 });

      await saveCheckpointScreenshot(page, testInfo.outputPath("01-dashboard-loaded.png"), true);
    });
  });

  await test.step("Step 2 - Open Mi Negocio menu", async () => {
    await runStep("Mi Negocio menu", async () => {
      await expect(page.locator("aside, nav").first()).toBeVisible();
      await clickByVisibleText(page, /negocio/i);
      await clickByVisibleText(page, /mi negocio/i);
      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
      await saveCheckpointScreenshot(page, testInfo.outputPath("02-mi-negocio-menu-expanded.png"), true);
    });
  });

  await test.step("Step 3 - Validate Agregar Negocio modal", async () => {
    await runStep("Agregar Negocio modal", async () => {
      await clickByVisibleText(page, /agregar negocio/i);
      const modal = await firstVisible([
        page.getByRole("dialog"),
        page.locator("[role='dialog']"),
        page.locator(".modal")
      ]);

      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
      await expect(
        firstVisible([
          modal.getByLabel(/nombre del negocio/i),
          modal.getByPlaceholder(/nombre del negocio/i),
          modal.getByText(/nombre del negocio/i)
        ])
      ).resolves.toBeDefined();
      await expect(modal.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      const nameInput = await firstVisible([
        modal.getByLabel(/nombre del negocio/i),
        modal.getByPlaceholder(/nombre del negocio/i),
        modal.locator("input[type='text']")
      ]);
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatizacion");
      await saveCheckpointScreenshot(page, testInfo.outputPath("03-agregar-negocio-modal.png"), true);
      await modal.getByRole("button", { name: /cancelar/i }).click();
      await waitForUiLoad(page);
    });
  });

  await test.step("Step 4 - Open Administrar Negocios", async () => {
    await runStep("Administrar Negocios view", async () => {
      const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        await clickByVisibleText(page, /mi negocio/i);
      }

      await clickByVisibleText(page, /administrar negocios/i);
      await expect(page.getByText(/informaci.n general/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByText(/secci.n legal/i).first()).toBeVisible();
      await saveCheckpointScreenshot(page, testInfo.outputPath("04-administrar-negocios-page.png"), true);
    });
  });

  await test.step("Step 5 - Validate Información General", async () => {
    await runStep("Información General", async () => {
      const infoSection = page.locator("section, div").filter({ hasText: /informaci.n general/i }).first();
      await expect(infoSection.getByText(/@[a-z0-9.-]+\.[a-z]{2,}/i).first()).toBeVisible();
      await expect(infoSection.getByText(/business plan/i).first()).toBeVisible();
      await expect(infoSection.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
      await expect(
        infoSection.locator("p, span, div").filter({ hasText: /nombre|usuario|user|juan/i }).first()
      ).toBeVisible();
    });
  });

  await test.step("Step 6 - Validate Detalles de la Cuenta", async () => {
    await runStep("Detalles de la Cuenta", async () => {
      const detailsSection = page.locator("section, div").filter({ hasText: /detalles de la cuenta/i }).first();
      await expect(detailsSection.getByText(/cuenta creada/i).first()).toBeVisible();
      await expect(detailsSection.getByText(/estado activo/i).first()).toBeVisible();
      await expect(detailsSection.getByText(/idioma seleccionado/i).first()).toBeVisible();
    });
  });

  await test.step("Step 7 - Validate Tus Negocios", async () => {
    await runStep("Tus Negocios", async () => {
      const businessSection = page.locator("section, div").filter({ hasText: /tus negocios/i }).first();
      await expect(businessSection).toBeVisible();
      await expect(businessSection.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
      await expect(businessSection.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    });
  });

  await test.step("Step 8 - Validate Términos y Condiciones", async () => {
    await runStep("Términos y Condiciones", async () => {
      termsUrl = await validateLegalLink(
        page,
        /t.rminos y condiciones/i,
        /t.rminos y condiciones/i,
        testInfo.outputPath("08-terminos-y-condiciones.png")
      );
    });
  });

  await test.step("Step 9 - Validate Política de Privacidad", async () => {
    await runStep("Política de Privacidad", async () => {
      privacyUrl = await validateLegalLink(
        page,
        /pol.tica de privacidad/i,
        /pol.tica de privacidad/i,
        testInfo.outputPath("09-politica-de-privacidad.png")
      );
    });
  });

  const finalReport = {
    report,
    finalUrls: {
      terminosYCondiciones: termsUrl,
      politicaDePrivacidad: privacyUrl
    },
    errors
  };

  await testInfo.attach("final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2)),
    contentType: "application/json"
  });

  // Keep the result explicit in the console output for CI logs.
  console.table(report);
  console.log("Final URLs:", finalReport.finalUrls);

  expect(errors, errors.join("\n")).toHaveLength(0);
});
