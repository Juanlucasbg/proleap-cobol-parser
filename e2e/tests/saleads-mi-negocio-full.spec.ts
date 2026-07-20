import { expect, Locator, Page, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL" | "NOT_RUN";

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

type Report = Record<ReportField, StepStatus>;

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_BUSINESS_NAME = "Negocio Prueba Automatización";

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForTimeout(900);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => undefined);
  await locator.click();
  await waitForUi(page);
}

async function pickVisible(
  name: string,
  candidates: Locator[],
  timeoutMs = 15_000
): Promise<Locator> {
  const perCandidateTimeout = Math.max(
    1_000,
    Math.floor(timeoutMs / Math.max(candidates.length, 1))
  );

  for (const candidate of candidates) {
    const first = candidate.first();
    try {
      await first.waitFor({ state: "visible", timeout: perCandidateTimeout });
      return first;
    } catch {
      // Try the next candidate.
    }
  }

  throw new Error(`Unable to find visible element for: ${name}`);
}

async function checkpoint(
  page: Page,
  testName: string,
  fullPage = false
): Promise<void> {
  await page.screenshot({
    path: test.info().outputPath(`${testName}.png`),
    fullPage
  });
}

async function runStep(
  report: Report,
  key: ReportField,
  body: () => Promise<void>
): Promise<void> {
  try {
    await body();
    report[key] = "PASS";
  } catch (error) {
    report[key] = "FAIL";
    console.error(`Step failed: ${key}`);
    console.error(error);
  }
}

async function validateLegalDocument(
  page: Page,
  label: "Términos y Condiciones" | "Política de Privacidad",
  headingPattern: RegExp,
  screenshotName: string
): Promise<string> {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  const link = await pickVisible(label, [
    page.getByRole("link", { name: headingPattern }),
    page.getByRole("button", { name: headingPattern }),
    page.getByText(headingPattern)
  ]);

  await clickAndWait(page, link);

  const popup = await popupPromise;
  const legalPage = popup ?? page;

  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForTimeout(1_000);

  const heading = await pickVisible(`${label} heading`, [
    legalPage.getByRole("heading", { name: headingPattern }),
    legalPage.getByText(headingPattern)
  ]);
  await expect(heading).toBeVisible();

  const legalText = (await legalPage.locator("body").innerText()).trim();
  expect(legalText.length).toBeGreaterThan(120);

  await checkpoint(legalPage, screenshotName, true);

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await legalPage.goBack().catch(() => undefined);
    await waitForUi(legalPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: Report = {
    Login: "NOT_RUN",
    "Mi Negocio menu": "NOT_RUN",
    "Agregar Negocio modal": "NOT_RUN",
    "Administrar Negocios view": "NOT_RUN",
    "Información General": "NOT_RUN",
    "Detalles de la Cuenta": "NOT_RUN",
    "Tus Negocios": "NOT_RUN",
    "Términos y Condiciones": "NOT_RUN",
    "Política de Privacidad": "NOT_RUN"
  };

  const configuredUrl = process.env.SALEADS_LOGIN_URL;
  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  let termsUrl = "";
  let privacyUrl = "";

  await runStep(report, "Login", async () => {
    const loginButton = await pickVisible("Sign in with Google button", [
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
      page.getByRole("button", { name: /continuar con google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[oó]n con google/i)
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountOption = await pickVisible("Google account option", [
        popup.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
        popup.getByText(new RegExp(ACCOUNT_EMAIL, "i"))
      ]);
      await clickAndWait(popup, accountOption);
      await popup.waitForTimeout(1_000);
      if (!popup.isClosed()) {
        await popup.close().catch(() => undefined);
      }
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const accountOption = page.getByText(new RegExp(ACCOUNT_EMAIL, "i"));
      if (await accountOption.first().isVisible().catch(() => false)) {
        await clickAndWait(page, accountOption.first());
      }
    }

    await expect(
      await pickVisible("left sidebar", [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator("nav")
      ])
    ).toBeVisible({ timeout: 45_000 });

    await expect(
      await pickVisible("Negocio text in sidebar", [
        page.getByText(/^Negocio$/i),
        page.getByText(/Mi Negocio/i)
      ])
    ).toBeVisible();

    await checkpoint(page, "01-dashboard-loaded");
  });

  await runStep(report, "Mi Negocio menu", async () => {
    const miNegocioToggle = await pickVisible("Mi Negocio toggle", [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);

    await clickAndWait(page, miNegocioToggle);

    await expect(
      await pickVisible("Agregar Negocio", [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/^Agregar Negocio$/i)
      ])
    ).toBeVisible();

    await expect(
      await pickVisible("Administrar Negocios", [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/^Administrar Negocios$/i)
      ])
    ).toBeVisible();

    await checkpoint(page, "02-mi-negocio-menu-expanded");
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    const agregarNegocio = await pickVisible("Agregar Negocio menu option", [
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await clickAndWait(page, agregarNegocio);

    await expect(
      await pickVisible("Crear Nuevo Negocio title", [
        page.getByRole("heading", { name: /crear nuevo negocio/i }),
        page.getByText(/crear nuevo negocio/i)
      ])
    ).toBeVisible();

    const businessNameInput = await pickVisible("Nombre del Negocio input", [
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").filter({ hasText: /nombre del negocio/i })
    ]);
    await expect(businessNameInput).toBeVisible();

    await expect(
      await pickVisible("business quota text", [page.getByText(/Tienes 2 de 3 negocios/i)])
    ).toBeVisible();

    await expect(
      await pickVisible("Cancelar button", [page.getByRole("button", { name: /cancelar/i })])
    ).toBeVisible();
    await expect(
      await pickVisible("Crear Negocio button", [page.getByRole("button", { name: /crear negocio/i })])
    ).toBeVisible();

    await checkpoint(page, "03-agregar-negocio-modal");

    await businessNameInput.click();
    await businessNameInput.fill(TEST_BUSINESS_NAME);

    const cancelar = await pickVisible("Cancelar button for close", [
      page.getByRole("button", { name: /cancelar/i })
    ]);
    await clickAndWait(page, cancelar);
  });

  await runStep(report, "Administrar Negocios view", async () => {
    const adminEntryVisible = await page
      .getByText(/^Administrar Negocios$/i)
      .first()
      .isVisible()
      .catch(() => false);
    if (!adminEntryVisible) {
      const miNegocioToggle = await pickVisible("Mi Negocio toggle re-open", [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ]);
      await clickAndWait(page, miNegocioToggle);
    }

    const administrarNegocios = await pickVisible("Administrar Negocios entry", [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);
    await clickAndWait(page, administrarNegocios);

    await expect(
      await pickVisible("Información General section", [
        page.getByRole("heading", { name: /informaci[oó]n general/i }),
        page.getByText(/informaci[oó]n general/i)
      ])
    ).toBeVisible();
    await expect(
      await pickVisible("Detalles de la Cuenta section", [
        page.getByRole("heading", { name: /detalles de la cuenta/i }),
        page.getByText(/detalles de la cuenta/i)
      ])
    ).toBeVisible();
    await expect(
      await pickVisible("Tus Negocios section", [
        page.getByRole("heading", { name: /tus negocios/i }),
        page.getByText(/tus negocios/i)
      ])
    ).toBeVisible();
    await expect(
      await pickVisible("Sección Legal section", [
        page.getByRole("heading", { name: /secci[oó]n legal/i }),
        page.getByText(/secci[oó]n legal/i)
      ])
    ).toBeVisible();

    await checkpoint(page, "04-administrar-negocios-page", true);
  });

  await runStep(report, "Información General", async () => {
    await expect(await pickVisible("User name", [page.locator("h1, h2, h3").first(), page.locator("main").getByText(/\S+/)])).toBeVisible();
    await expect(await pickVisible("User email", [page.getByText(/@/)]))
      .toBeVisible();
    await expect(await pickVisible("BUSINESS PLAN text", [page.getByText(/BUSINESS PLAN/i)]))
      .toBeVisible();
    await expect(await pickVisible("Cambiar Plan button", [page.getByRole("button", { name: /cambiar plan/i })]))
      .toBeVisible();
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await expect(await pickVisible("Cuenta creada", [page.getByText(/Cuenta creada/i)])).toBeVisible();
    await expect(await pickVisible("Estado activo", [page.getByText(/Estado activo/i)])).toBeVisible();
    await expect(await pickVisible("Idioma seleccionado", [page.getByText(/Idioma seleccionado/i)])).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    await expect(
      await pickVisible("Business list", [
        page.getByRole("table"),
        page.getByRole("list"),
        page.locator("section").filter({ hasText: /Tus Negocios/i })
      ])
    ).toBeVisible();
    await expect(
      await pickVisible("Agregar Negocio button in Tus Negocios", [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ])
    ).toBeVisible();
    await expect(await pickVisible("Tienes 2 de 3 negocios text", [page.getByText(/Tienes 2 de 3 negocios/i)])).toBeVisible();
  });

  await runStep(report, "Términos y Condiciones", async () => {
    termsUrl = await validateLegalDocument(
      page,
      "Términos y Condiciones",
      /T[eé]rminos y Condiciones/i,
      "05-terminos-y-condiciones"
    );
  });

  await runStep(report, "Política de Privacidad", async () => {
    privacyUrl = await validateLegalDocument(
      page,
      "Política de Privacidad",
      /Pol[ií]tica de Privacidad/i,
      "06-politica-de-privacidad"
    );
  });

  const summary = {
    report,
    legalUrls: {
      terminosYCondiciones: termsUrl,
      politicaDePrivacidad: privacyUrl
    }
  };

  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: JSON.stringify(summary, null, 2),
    contentType: "application/json"
  });

  console.log("Final validation report:");
  console.log(JSON.stringify(summary, null, 2));

  const failed = Object.entries(report).filter(([, status]) => status !== "PASS");
  expect(
    failed,
    `Expected all workflow sections to pass. Failed: ${failed.map(([name]) => name).join(", ")}`
  ).toHaveLength(0);
});
