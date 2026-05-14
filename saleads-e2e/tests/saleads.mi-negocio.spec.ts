import { expect, Locator, Page, test, TestInfo } from "@playwright/test";

type Result = "PASS" | "FAIL";

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

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10_000 });
  } catch {
    // Some pages keep background polling active; domcontentloaded is enough.
  }
}

async function waitAfterClick(page: Page, target: Locator): Promise<void> {
  await expect(target).toBeVisible();
  await target.click();
  await waitForUi(page);
}

async function firstVisibleLocator(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      return candidate.first();
    }
  }
  return null;
}

async function expectAnyVisible(message: string, candidates: Locator[]): Promise<Locator> {
  const visible = await firstVisibleLocator(candidates);
  if (!visible) {
    throw new Error(message);
  }
  await expect(visible).toBeVisible();
  return visible;
}

async function capture(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage
  });
}

async function validateLegalPage(params: {
  appPage: Page;
  testInfo: TestInfo;
  linkNamePattern: RegExp;
  headingPattern: RegExp;
  screenshotName: string;
}): Promise<string> {
  const { appPage, testInfo, linkNamePattern, headingPattern, screenshotName } = params;
  const link = await expectAnyVisible(`Could not find legal link: ${linkNamePattern}`, [
    appPage.getByRole("link", { name: linkNamePattern }),
    appPage.getByRole("button", { name: linkNamePattern }),
    appPage.getByText(linkNamePattern)
  ]);

  const popupPromise = appPage.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
  await waitAfterClick(appPage, link);
  const popup = await popupPromise;

  const legalPage = popup ?? appPage;
  await waitForUi(legalPage);

  await expectAnyVisible(
    `Legal heading not found for ${headingPattern}`,
    [
      legalPage.getByRole("heading", { name: headingPattern }),
      legalPage.getByText(headingPattern)
    ]
  );

  const bodyText = await legalPage.locator("body").innerText();
  if (bodyText.trim().length < 120) {
    throw new Error("Legal content appears too short or empty.");
  }

  await capture(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const results: Record<ReportField, Result> = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, "FAIL"])
  ) as Record<ReportField, Result>;
  const failures: string[] = [];
  const evidence: Record<string, string> = {};

  const recordFailure = (field: ReportField, error: unknown): void => {
    const reason = error instanceof Error ? error.message : String(error);
    failures.push(`${field}: ${reason}`);
    results[field] = "FAIL";
  };

  const runValidation = async (field: ReportField, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      results[field] = "PASS";
    } catch (error) {
      recordFailure(field, error);
    }
  };

  // Step 1: Login with Google (or verify already authenticated session)
  await runValidation("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (page.url() === "about:blank" && loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);

    const sidebar = await firstVisibleLocator([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/^Negocio$/i)
    ]);

    if (!sidebar) {
      const signInButton = await expectAnyVisible("Google login button not found.", [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
        page.getByRole("button", { name: /continuar con google/i }),
        page.getByText(/google/i)
      ]);

      const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
      await waitAfterClick(page, signInButton);
      const popup = await popupPromise;

      const accountEmail = "juanlucasbarbiergarzon@gmail.com";
      if (popup) {
        await waitForUi(popup);
        const accountOption = await expectAnyVisible("Google account option not visible.", [
          popup.getByText(accountEmail, { exact: true }),
          popup.getByRole("link", { name: accountEmail }),
          popup.getByRole("button", { name: accountEmail })
        ]);
        await accountOption.click();
        await popup.waitForLoadState("domcontentloaded").catch(() => undefined);
      } else {
        const accountOption = await firstVisibleLocator([
          page.getByText(accountEmail, { exact: true }),
          page.getByRole("button", { name: accountEmail }),
          page.getByRole("link", { name: accountEmail })
        ]);
        if (accountOption) {
          await accountOption.click();
        }
      }
    }

    await expectAnyVisible("Main application interface not visible after login.", [
      page.locator("aside"),
      page.getByRole("navigation")
    ]);
    await expectAnyVisible("Left sidebar navigation not visible after login.", [
      page.getByText(/^Negocio$/i),
      page.getByRole("navigation")
    ]);

    await capture(page, testInfo, "01-dashboard-loaded.png");
  });

  // Step 2: Open Mi Negocio menu
  await runValidation("Mi Negocio menu", async () => {
    const negocio = await expectAnyVisible("Negocio section not found in sidebar.", [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ]);
    await waitAfterClick(page, negocio);

    const miNegocio = await expectAnyVisible("Mi Negocio option not found.", [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    await waitAfterClick(page, miNegocio);

    await expectAnyVisible("Agregar Negocio not visible in Mi Negocio submenu.", [
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    await expectAnyVisible("Administrar Negocios not visible in Mi Negocio submenu.", [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);

    await capture(page, testInfo, "02-mi-negocio-expanded-menu.png");
  });

  // Step 3: Validate Agregar Negocio modal
  await runValidation("Agregar Negocio modal", async () => {
    const agregarNegocio = await expectAnyVisible("Agregar Negocio action not found.", [
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    await waitAfterClick(page, agregarNegocio);

    await expectAnyVisible("Modal title 'Crear Nuevo Negocio' not visible.", [
      page.getByRole("heading", { name: /crear nuevo negocio/i }),
      page.getByText(/crear nuevo negocio/i)
    ]);
    const businessNameField = await expectAnyVisible("Input 'Nombre del Negocio' not found.", [
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i)
    ]);
    await expectAnyVisible("'Tienes 2 de 3 negocios' text not visible.", [
      page.getByText(/tienes 2 de 3 negocios/i)
    ]);
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await capture(page, testInfo, "03-agregar-negocio-modal.png");

    await businessNameField.fill("Negocio Prueba Automatización");
    await waitAfterClick(page, page.getByRole("button", { name: /cancelar/i }));
  });

  // Step 4: Open Administrar Negocios
  await runValidation("Administrar Negocios view", async () => {
    const administrarNegociosVisible = await firstVisibleLocator([
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);

    if (!administrarNegociosVisible) {
      const negocio = await expectAnyVisible("Negocio section not found while reopening menu.", [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i)
      ]);
      await waitAfterClick(page, negocio);

      const miNegocio = await expectAnyVisible("Mi Negocio not found while reopening menu.", [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ]);
      await waitAfterClick(page, miNegocio);
    }

    const administrarNegocios = await expectAnyVisible("Administrar Negocios option not found.", [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    await waitAfterClick(page, administrarNegocios);

    await expectAnyVisible("'Información General' section missing.", [
      page.getByRole("heading", { name: /informaci[oó]n general/i }),
      page.getByText(/informaci[oó]n general/i)
    ]);
    await expectAnyVisible("'Detalles de la Cuenta' section missing.", [
      page.getByRole("heading", { name: /detalles de la cuenta/i }),
      page.getByText(/detalles de la cuenta/i)
    ]);
    await expectAnyVisible("'Tus Negocios' section missing.", [
      page.getByRole("heading", { name: /tus negocios/i }),
      page.getByText(/tus negocios/i)
    ]);
    await expectAnyVisible("'Sección Legal' section missing.", [
      page.getByRole("heading", { name: /secci[oó]n legal/i }),
      page.getByText(/secci[oó]n legal/i)
    ]);

    await capture(page, testInfo, "04-administrar-negocios-view.png", true);
  });

  // Step 5: Validate Información General
  await runValidation("Información General", async () => {
    await expectAnyVisible("Could not find user email in account information.", [
      page.getByText(/juanlucasbarbiergarzon@gmail.com/i),
      page.getByText(/@gmail\.com/i),
      page.getByText(/@/i)
    ]);
    await expectAnyVisible("Could not find user name indicator in account information.", [
      page.getByText(/nombre/i),
      page.getByText(/usuario/i),
      page.getByText(/[A-Za-zÁ-ÿ]{2,}\s+[A-Za-zÁ-ÿ]{2,}/)
    ]);
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
  });

  // Step 6: Validate Detalles de la Cuenta
  await runValidation("Detalles de la Cuenta", async () => {
    await expectAnyVisible("'Cuenta creada' not visible.", [page.getByText(/cuenta creada/i)]);
    await expectAnyVisible("'Estado activo' not visible.", [page.getByText(/estado activo/i)]);
    await expectAnyVisible("'Idioma seleccionado' not visible.", [
      page.getByText(/idioma seleccionado/i)
    ]);
  });

  // Step 7: Validate Tus Negocios
  await runValidation("Tus Negocios", async () => {
    await expectAnyVisible("Business list title not visible.", [page.getByText(/tus negocios/i)]);
    await expectAnyVisible("Agregar Negocio button missing in businesses section.", [
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    await expectAnyVisible("'Tienes 2 de 3 negocios' missing in businesses section.", [
      page.getByText(/tienes 2 de 3 negocios/i)
    ]);
  });

  // Step 8: Validate Términos y Condiciones
  await runValidation("Términos y Condiciones", async () => {
    const finalUrl = await validateLegalPage({
      appPage: page,
      testInfo,
      linkNamePattern: /t[eé]rminos y condiciones/i,
      headingPattern: /t[eé]rminos y condiciones/i,
      screenshotName: "08-terminos-y-condiciones.png"
    });
    evidence.terminosUrl = finalUrl;
  });

  // Step 9: Validate Política de Privacidad
  await runValidation("Política de Privacidad", async () => {
    const finalUrl = await validateLegalPage({
      appPage: page,
      testInfo,
      linkNamePattern: /pol[ií]tica de privacidad/i,
      headingPattern: /pol[ií]tica de privacidad/i,
      screenshotName: "09-politica-de-privacidad.png"
    });
    evidence.politicaUrl = finalUrl;
  });

  const finalReport = {
    test: "saleads_mi_negocio_full_test",
    results,
    evidence,
    failures
  };

  await testInfo.attach("final-report.json", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });

  console.log("FINAL REPORT:", JSON.stringify(finalReport, null, 2));

  if (failures.length > 0) {
    throw new Error(`One or more validations failed:\n${failures.join("\n")}`);
  }
});
