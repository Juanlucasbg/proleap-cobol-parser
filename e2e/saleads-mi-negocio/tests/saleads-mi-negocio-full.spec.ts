import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

const reportFields: ReportField[] = [
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

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function firstVisible(page: Page, candidates: Locator[], timeoutMs = 12_000): Promise<Locator> {
  const pollEveryMs = 250;
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      const visible = await locator.isVisible().catch(() => false);

      if (visible) {
        return locator;
      }
    }

    await page.waitForTimeout(pollEveryMs);
  }

  throw new Error("No expected visible element was found.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUiToSettle(page);
}

async function openLegalPageAndValidate(
  page: Page,
  testInfo: TestInfo,
  linkText: string,
  heading: RegExp,
  screenshotFile: string,
  urlAttachmentName: string,
): Promise<void> {
  const legalLink = await firstVisible(page, [
    page.getByRole("link", { name: new RegExp(linkText, "i") }),
    page.getByText(new RegExp(linkText, "i")),
  ]);

  const popupPromise = page
    .context()
    .waitForEvent("page", { timeout: 5_000 })
    .catch(() => null);

  await clickAndWait(page, legalLink);

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  await targetPage.waitForLoadState("domcontentloaded");
  await expect(targetPage.getByRole("heading", { name: heading }).first()).toBeVisible();
  await expect(targetPage.locator("main, body").first()).toContainText(heading);

  const finalUrl = targetPage.url();
  await targetPage.screenshot({
    path: testInfo.outputPath(screenshotFile),
    fullPage: true,
  });
  await testInfo.attach(urlAttachmentName, {
    body: finalUrl,
    contentType: "text/plain",
  });

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else {
    await page.goBack().catch(() => undefined);
    await waitForUiToSettle(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.skip(
    !process.env.SALEADS_LOGIN_URL,
    "Set SALEADS_LOGIN_URL to the current environment login page.",
  );

  const report = Object.fromEntries(
    reportFields.map((field) => [field, "FAIL" as ReportStatus]),
  ) as Record<ReportField, ReportStatus>;
  const failures: string[] = [];

  async function runStep(field: ReportField, action: () => Promise<void>): Promise<void> {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      failures.push(`${field}: ${message}`);
      await page.screenshot({
        path: testInfo.outputPath(`${field.replace(/\s+/g, "_")}-failure.png`),
        fullPage: true,
      });
    }
  }

  await page.goto(process.env.SALEADS_LOGIN_URL as string, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);

  await runStep("Login", async () => {
    const loginButton = await firstVisible(page, [
      page.getByRole("button", {
        name: /sign in with google|iniciar sesión con google|continuar con google/i,
      }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
      page.getByRole("button", { name: /google/i }),
    ]);

    const popupPromise = page
      .context()
      .waitForEvent("page", { timeout: 8_000 })
      .catch(() => null);

    await clickAndWait(page, loginButton);

    const googlePopup = await popupPromise;
    const googlePage = googlePopup ?? page;

    const accountSelector = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    if (await accountSelector.isVisible().catch(() => false)) {
      await accountSelector.click();
      await waitForUiToSettle(googlePage);
    }

    if (googlePopup) {
      await googlePopup.waitForClose({ timeout: 60_000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await waitForUiToSettle(page);

    const sidebar = await firstVisible(page, [
      page.locator("aside").first(),
      page.locator("[class*='sidebar']").first(),
      page.getByRole("navigation").first(),
    ]);

    await expect(sidebar).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true,
    });
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisible(page, [
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await firstVisible(page, [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-expanded.png"),
      fullPage: true,
    });
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusinessMenuItem = await firstVisible(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await clickAndWait(page, addBusinessMenuItem);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
    const businessNameInput = await firstVisible(page, [
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[type='text'], input").first(),
    ]);
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true,
    });

    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }).first());
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocioOption = await firstVisible(page, [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);
    await clickAndWait(page, miNegocioOption);

    const manageBusinesses = await firstVisible(page, [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ]);
    await clickAndWait(page, manageBusinesses);

    await expect(page.getByText(/información general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/sección legal/i).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-page.png"),
      fullPage: true,
    });
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();

    const probableName = page
      .locator("section, div")
      .filter({ hasText: /información general/i })
      .first();
    await expect(probableName).toContainText(/[A-Za-zÀ-ÿ]{2,}/);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    await openLegalPageAndValidate(
      page,
      testInfo,
      "Términos y Condiciones",
      /términos y condiciones/i,
      "05-terminos-y-condiciones.png",
      "terminos-final-url.txt",
    );
  });

  await runStep("Política de Privacidad", async () => {
    await openLegalPageAndValidate(
      page,
      testInfo,
      "Política de Privacidad",
      /política de privacidad/i,
      "06-politica-de-privacidad.png",
      "politica-final-url.txt",
    );
  });

  const reportText = JSON.stringify(report, null, 2);
  await testInfo.attach("final-report.json", {
    body: reportText,
    contentType: "application/json",
  });

  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
