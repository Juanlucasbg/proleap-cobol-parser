import { expect, Locator, Page, test, TestInfo } from "@playwright/test";

type SectionStatus = "PASS" | "FAIL";

type ValidationReport = Record<
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad",
  SectionStatus
>;

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, fileName: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function firstVisible(page: Page, patterns: Array<{ role?: "button" | "link" | "menuitem" | "tab"; name: RegExp }>): Promise<Locator> {
  for (const candidate of patterns) {
    const locator =
      candidate.role === undefined
        ? page.getByText(candidate.name).first()
        : page.getByRole(candidate.role, { name: candidate.name }).first();

    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  throw new Error(`No visible element found for patterns: ${patterns.map((pattern) => pattern.name).join(", ")}`);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function runSection(
  report: ValidationReport,
  sectionName: keyof ValidationReport,
  action: () => Promise<void>,
  failures: string[],
): Promise<void> {
  try {
    await action();
    report[sectionName] = "PASS";
  } catch (error) {
    report[sectionName] = "FAIL";
    failures.push(`${sectionName}: ${(error as Error).message}`);
  }
}

test("SaleADS - login con Google y workflow completo de Mi Negocio", async ({ page, context }, testInfo) => {
  const report: ValidationReport = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };
  const failures: string[] = [];
  const legalUrls: Record<"terminos" | "privacidad", string> = {
    terminos: "",
    privacidad: "",
  };

  const startUrl = process.env.SALEADS_URL ?? process.env.SALEADS_START_URL;
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  }
  await waitForUi(page);

  await runSection(report, "Login", async () => {
    const loginButton = await firstVisible(page, [
      { role: "button", name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i },
      { role: "link", name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i },
      { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i },
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const popup = await popupPromise;
    if (popup) {
      await waitForUi(popup);

      const accountChoice = popup.getByText("juanlucasbarbiergarzon@gmail.com").first();
      if (await accountChoice.isVisible().catch(() => false)) {
        await clickAndWait(accountChoice, popup);
      }

      await popup.waitForClose({ timeout: 90_000 }).catch(() => undefined);
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const inlineAccountChoice = page.getByText("juanlucasbarbiergarzon@gmail.com").first();
      if (await inlineAccountChoice.isVisible().catch(() => false)) {
        await clickAndWait(inlineAccountChoice, page);
      }
    }

    await expect(page.locator("aside").first()).toBeVisible({ timeout: 90_000 });
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 90_000 });
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  }, failures);

  await runSection(report, "Mi Negocio menu", async () => {
    const negocioSection = await firstVisible(page, [
      { role: "button", name: /negocio/i },
      { role: "link", name: /negocio/i },
      { name: /negocio/i },
    ]);
    await clickAndWait(negocioSection, page);

    const miNegocioOption = await firstVisible(page, [
      { role: "button", name: /mi negocio/i },
      { role: "link", name: /mi negocio/i },
      { name: /mi negocio/i },
    ]);
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png");
  }, failures);

  await runSection(report, "Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible(page, [
      { role: "button", name: /^agregar negocio$/i },
      { role: "link", name: /^agregar negocio$/i },
      { name: /^agregar negocio$/i },
    ]);
    await clickAndWait(agregarNegocio, page);

    await expect(page.getByRole("heading", { name: /crear nuevo negocio/i })).toBeVisible();
    const nombreDelNegocio = page.getByLabel(/nombre del negocio/i).first();
    if (!(await nombreDelNegocio.isVisible().catch(() => false))) {
      await expect(page.getByPlaceholder(/nombre del negocio/i).first()).toBeVisible();
    } else {
      await expect(nombreDelNegocio).toBeVisible();
      await nombreDelNegocio.click();
      await nombreDelNegocio.fill("Negocio Prueba Automatizacion");
    }

    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");
    await clickAndWait(page.getByRole("button", { name: /cancelar/i }), page);
  }, failures);

  await runSection(report, "Administrar Negocios view", async () => {
    const miNegocioOption = await firstVisible(page, [
      { role: "button", name: /mi negocio/i },
      { role: "link", name: /mi negocio/i },
      { name: /mi negocio/i },
    ]);
    await clickAndWait(miNegocioOption, page);

    const administrarNegocios = await firstVisible(page, [
      { role: "button", name: /administrar negocios/i },
      { role: "link", name: /administrar negocios/i },
      { name: /administrar negocios/i },
    ]);
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-view-full.png", true);
  }, failures);

  await runSection(report, "Información General", async () => {
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const possibleUserName = page.locator("h1, h2, h3, [data-testid*='name']").first();
    await expect(possibleUserName).toBeVisible();
  }, failures);

  await runSection(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  }, failures);

  await runSection(report, "Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
  }, failures);

  const validateLegalPage = async (
    linkRegex: RegExp,
    headingRegex: RegExp,
    screenshotName: string,
    key: "terminos" | "privacidad",
  ): Promise<void> => {
    const legalLink = await firstVisible(page, [
      { role: "link", name: linkRegex },
      { role: "button", name: linkRegex },
      { name: linkRegex },
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(legalLink, page);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await waitForUi(legalPage);

    const heading = legalPage.getByRole("heading", { name: headingRegex }).first();
    if (await heading.isVisible().catch(() => false)) {
      await expect(heading).toBeVisible();
    } else {
      await expect(legalPage.getByText(headingRegex).first()).toBeVisible();
    }

    await expect(legalPage.locator("p, li, article, section").filter({ hasText: /\w+/ }).first()).toBeVisible();
    await captureCheckpoint(legalPage, testInfo, screenshotName, true);
    legalUrls[key] = legalPage.url();

    if (popup) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUi(page);
      return;
    }

    await page.goBack().catch(() => undefined);
    await waitForUi(page);
  };

  await runSection(report, "Términos y Condiciones", async () => {
    await validateLegalPage(
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "05-terminos-y-condiciones.png",
      "terminos",
    );
  }, failures);

  await runSection(report, "Política de Privacidad", async () => {
    await validateLegalPage(
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad.png",
      "privacidad",
    );
  }, failures);

  const reportOutput = {
    report,
    legalUrls,
    generatedAt: new Date().toISOString(),
  };

  await testInfo.attach("saleads-mi-negocio-report", {
    body: Buffer.from(JSON.stringify(reportOutput, null, 2), "utf-8"),
    contentType: "application/json",
  });

  console.table(report);
  console.log(`Final URL Términos y Condiciones: ${legalUrls.terminos || "N/A"}`);
  console.log(`Final URL Política de Privacidad: ${legalUrls.privacidad || "N/A"}`);

  if (failures.length > 0) {
    throw new Error(`Validation failures:\n${failures.join("\n")}`);
  }
});
