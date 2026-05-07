import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

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

type StepResult = "PASS" | "FAIL";

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUiToSettle(page);
}

async function isVisible(locator: Locator, timeout = 15000): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function pickVisible(page: Page, candidates: Locator[], timeout = 20000): Promise<Locator> {
  const endTime = Date.now() + timeout;

  while (Date.now() < endTime) {
    for (const candidate of candidates) {
      if (await isVisible(candidate, 750)) {
        return candidate.first();
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error("Could not locate a visible element from provided candidates.");
}

async function screenshot(testInfo: TestInfo, page: Page, name: string, fullPage = false): Promise<void> {
  const filename = `${name.replace(/\s+/g, "_").toLowerCase()}.png`;
  await page.screenshot({
    path: testInfo.outputPath(filename),
    fullPage
  });
}

async function requireAllVisible(checks: Array<{ name: string; locator: Locator }>): Promise<void> {
  const missing: string[] = [];

  for (const check of checks) {
    if (!(await isVisible(check.locator))) {
      missing.push(check.name);
    }
  }

  if (missing.length > 0) {
    throw new Error(`Missing expected elements: ${missing.join(", ")}`);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const results: Record<ReportField, StepResult> = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };

  const evidence: Record<string, string> = {};

  async function runStep(field: ReportField, stepAction: () => Promise<void>): Promise<void> {
    try {
      await stepAction();
      results[field] = "PASS";
    } catch (error) {
      results[field] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      evidence[`${field} error`] = message;
      await screenshot(testInfo, page, `${field}_failure`, true).catch(() => {});
    }
  }

  async function openAndValidateLegalDocument(
    linkText: string,
    headingRegex: RegExp,
    screenshotName: string
  ): Promise<string> {
    const applicationUrlBefore = page.url();
    const link = await pickVisible(page, [
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByText(new RegExp(linkText, "i"))
    ]);

    const newTabPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, link);

    const potentialNewPage = await newTabPromise;
    const targetPage = potentialNewPage ?? page;
    const openedNewTab = Boolean(potentialNewPage);

    await waitForUiToSettle(targetPage);

    const headingByRole = targetPage.getByRole("heading", { name: headingRegex });
    const headingByText = targetPage.getByText(headingRegex).first();
    const headingVisible = (await isVisible(headingByRole, 15000)) || (await isVisible(headingByText, 15000));
    if (!headingVisible) {
      throw new Error(`Heading not found for ${linkText}.`);
    }

    const legalContent = (await targetPage.locator("body").innerText()).trim();
    if (legalContent.length < 120) {
      throw new Error(`Legal content appears too short for ${linkText}.`);
    }

    await screenshot(testInfo, targetPage, screenshotName, true);
    const finalUrl = targetPage.url();

    if (openedNewTab) {
      await targetPage.close();
      await page.bringToFront();
    } else if (page.url() !== applicationUrlBefore) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        await page.goto(applicationUrlBefore, { waitUntil: "domcontentloaded" });
      });
      await waitForUiToSettle(page);
    }

    return finalUrl;
  }

  await runStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No login URL was provided and browser started on about:blank. Set SALEADS_LOGIN_URL or open the login page before test execution."
      );
    }

    const loginButton = await pickVisible(page, [
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
      page.getByRole("button", { name: /iniciar sesi[oó]n|login|acceder/i })
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, loginButton);

    const popup = await popupPromise;
    if (popup) {
      await waitForUiToSettle(popup);
      const accountChooser = popup.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
      if (await isVisible(accountChooser, 10000)) {
        await clickAndWait(popup, accountChooser);
      }

      await popup.waitForEvent("close", { timeout: 15000 }).catch(() => {});
      await page.bringToFront();
    } else {
      const accountChooser = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
      if (await isVisible(accountChooser, 7000)) {
        await clickAndWait(page, accountChooser);
      }
    }

    await waitForUiToSettle(page);

    const mainVisible =
      (await isVisible(page.locator("main").first(), 30000)) ||
      (await isVisible(page.getByRole("main").first(), 30000)) ||
      (await isVisible(page.getByText(/dashboard|inicio/i).first(), 30000));

    const sidebarVisible =
      (await isVisible(page.locator("aside").first(), 30000)) ||
      (await isVisible(page.getByRole("navigation").first(), 30000)) ||
      (await isVisible(page.getByText(/negocio|mi negocio/i).first(), 30000));

    if (!mainVisible || !sidebarVisible) {
      throw new Error("Main application interface or sidebar was not visible after login.");
    }

    await screenshot(testInfo, page, "dashboard_loaded", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await requireAllVisible([
      {
        name: "Negocio section label",
        locator: page.getByText(/negocio/i).first()
      }
    ]);

    const miNegocio = await pickVisible(page, [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/^mi negocio$/i)
    ]);
    await clickAndWait(page, miNegocio);

    const agregarNegocio = page.getByText(/^agregar negocio$/i).first();
    const administrarNegocios = page.getByText(/^administrar negocios$/i).first();
    await requireAllVisible([
      { name: "Agregar Negocio", locator: agregarNegocio },
      { name: "Administrar Negocios", locator: administrarNegocios }
    ]);

    await screenshot(testInfo, page, "mi_negocio_menu_expanded", true);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await pickVisible(page, [
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i)
    ]);
    await clickAndWait(page, agregarNegocio);

    const modal = await pickVisible(page, [
      page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }),
      page.getByText(/crear nuevo negocio/i)
    ]);

    const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    const businessNameFallbackInput = page.getByPlaceholder(/nombre del negocio/i).first();
    const inputVisible = (await isVisible(businessNameInput)) || (await isVisible(businessNameFallbackInput));
    if (!inputVisible) {
      throw new Error("Input 'Nombre del Negocio' was not found.");
    }

    await requireAllVisible([
      { name: "Modal title Crear Nuevo Negocio", locator: modal },
      { name: "Text Tienes 2 de 3 negocios", locator: page.getByText(/tienes 2 de 3 negocios/i).first() },
      { name: "Cancelar button", locator: page.getByRole("button", { name: /^cancelar$/i }).first() },
      { name: "Crear Negocio button", locator: page.getByRole("button", { name: /^crear negocio$/i }).first() }
    ]);

    await screenshot(testInfo, page, "agregar_negocio_modal", true);

    if (await isVisible(businessNameInput)) {
      await businessNameInput.fill("Negocio Prueba Automatización");
    } else {
      await businessNameFallbackInput.fill("Negocio Prueba Automatización");
    }

    const cancelButton = page.getByRole("button", { name: /^cancelar$/i }).first();
    await clickAndWait(page, cancelButton);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegocios = page.getByText(/^administrar negocios$/i).first();
    if (!(await isVisible(administrarNegocios, 5000))) {
      const miNegocio = await pickVisible(page, [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/^mi negocio$/i)
      ]);
      await clickAndWait(page, miNegocio);
    }

    await clickAndWait(page, await pickVisible(page, [page.getByText(/^administrar negocios$/i)]));

    await requireAllVisible([
      { name: "Información General section", locator: page.getByText(/información general/i).first() },
      { name: "Detalles de la Cuenta section", locator: page.getByText(/detalles de la cuenta/i).first() },
      { name: "Tus Negocios section", locator: page.getByText(/tus negocios/i).first() },
      { name: "Sección Legal section", locator: page.getByText(/sección legal/i).first() }
    ]);

    await screenshot(testInfo, page, "administrar_negocios_page", true);
  });

  await runStep("Información General", async () => {
    await requireAllVisible([
      { name: "User name", locator: page.getByText(/^[a-z][a-z .'-]{2,}$/i).first() },
      { name: "User email", locator: page.getByText(/^[^\s@]+@[^\s@]+\.[^\s@]+$/i).first() },
      { name: "BUSINESS PLAN text", locator: page.getByText(/business plan/i).first() },
      { name: "Cambiar Plan button", locator: page.getByRole("button", { name: /cambiar plan/i }).first() }
    ]);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await requireAllVisible([
      { name: "Cuenta creada text", locator: page.getByText(/cuenta creada/i).first() },
      { name: "Estado activo text", locator: page.getByText(/estado activo/i).first() },
      { name: "Idioma seleccionado text", locator: page.getByText(/idioma seleccionado/i).first() }
    ]);
  });

  await runStep("Tus Negocios", async () => {
    const negociosSection = await pickVisible(page, [
      page.locator("section").filter({ hasText: /tus negocios/i }),
      page.getByText(/tus negocios/i)
    ]);

    await requireAllVisible([
      { name: "Business list section", locator: negociosSection },
      { name: "Agregar Negocio button", locator: page.getByRole("button", { name: /agregar negocio/i }).first() },
      { name: "Tienes 2 de 3 negocios text", locator: page.getByText(/tienes 2 de 3 negocios/i).first() }
    ]);
  });

  await runStep("Términos y Condiciones", async () => {
    const finalUrl = await openAndValidateLegalDocument(
      "Términos y Condiciones",
      /términos y condiciones/i,
      "terminos_y_condiciones"
    );
    evidence["Términos y Condiciones URL"] = finalUrl;
  });

  await runStep("Política de Privacidad", async () => {
    const finalUrl = await openAndValidateLegalDocument(
      "Política de Privacidad",
      /política de privacidad/i,
      "politica_de_privacidad"
    );
    evidence["Política de Privacidad URL"] = finalUrl;
  });

  const reportPayload = {
    workflow: "saleads_mi_negocio_full_test",
    timestamp: new Date().toISOString(),
    results,
    evidence
  };

  await testInfo.attach("final-report", {
    body: Buffer.from(JSON.stringify(reportPayload, null, 2), "utf-8"),
    contentType: "application/json"
  });

  console.log("Final validation report:");
  console.table(results);
  console.log("Evidence:", evidence);

  const failedSteps = Object.entries(results)
    .filter(([, value]) => value === "FAIL")
    .map(([step]) => step);
  expect(
    failedSteps,
    `One or more workflow validations failed: ${failedSteps.length > 0 ? failedSteps.join(", ") : "none"}`
  ).toEqual([]);
});
