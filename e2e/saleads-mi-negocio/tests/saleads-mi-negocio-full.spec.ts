import { expect, Locator, Page, test } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";

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

type StepReport = Record<ReportKey, "PASS" | "FAIL">;

const createReport = (): StepReport => ({
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL",
});

async function waitForUi(page: Page): Promise<void> {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 8_000 }),
    page.waitForTimeout(1_000),
  ]).catch(() => undefined);
}

async function waitForAnyVisible(
  candidates: Locator[],
  description: string,
  timeout = 10_000,
): Promise<Locator> {
  for (const locator of candidates) {
    try {
      const first = locator.first();
      await first.waitFor({ state: "visible", timeout });
      return first;
    } catch (error) {
      // Try the next candidate locator.
    }
  }

  throw new Error(`Could not find visible element: ${description}`);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function validateMainAppVisible(page: Page): Promise<void> {
  const appShell = await waitForAnyVisible(
    [
      page.locator("aside"),
      page.locator('[role="navigation"]'),
      page.getByText(/Mi Negocio|Negocio/i),
    ],
    "main application interface / sidebar",
  );
  await expect(appShell).toBeVisible();
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const adminVisible = await page
    .getByText(/^Administrar Negocios$/i)
    .first()
    .isVisible()
    .catch(() => false);
  const addVisible = await page
    .getByText(/^Agregar Negocio$/i)
    .first()
    .isVisible()
    .catch(() => false);

  if (adminVisible && addVisible) {
    return;
  }

  const miNegocio = await waitForAnyVisible(
    [
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ],
    "Mi Negocio sidebar option",
  );

  await clickAndWait(miNegocio, page);
}

async function validateLegalLink(
  page: Page,
  linkName: RegExp,
  headingName: RegExp,
): Promise<string> {
  const link = await waitForAnyVisible(
    [
      page.getByRole("link", { name: linkName }),
      page.getByRole("button", { name: linkName }),
      page.getByText(linkName),
    ],
    `legal link ${linkName.toString()}`,
  );

  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await link.click();
  await waitForUi(page);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 });
    await waitForUi(popup);

    const heading = await waitForAnyVisible(
      [popup.getByRole("heading", { name: headingName }), popup.getByText(headingName)],
      `heading ${headingName.toString()} in popup`,
    );
    await expect(heading).toBeVisible();
    const legalContent = await waitForAnyVisible(
      [popup.locator("main p, article p, p, main li, article li, li").filter({ hasText: /\S.{20,}/ })],
      `legal content text for ${headingName.toString()} in popup`,
    );
    await expect(legalContent).toBeVisible();
    const popupBodyText = await popup.locator("body").innerText();
    expect(popupBodyText.trim().length).toBeGreaterThan(200);

    await popup.screenshot({
      path: test.info().outputPath(
        `${headingName.source.toLowerCase().replace(/[^a-z0-9]+/g, "-")}.png`,
      ),
      fullPage: true,
    });

    const finalUrl = popup.url();
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return finalUrl;
  }

  const heading = await waitForAnyVisible(
    [page.getByRole("heading", { name: headingName }), page.getByText(headingName)],
    `heading ${headingName.toString()} in current tab`,
  );
  await expect(heading).toBeVisible();
  const legalContent = await waitForAnyVisible(
    [page.locator("main p, article p, p, main li, article li, li").filter({ hasText: /\S.{20,}/ })],
    `legal content text for ${headingName.toString()} in current tab`,
  );
  await expect(legalContent).toBeVisible();
  const pageBodyText = await page.locator("body").innerText();
  expect(pageBodyText.trim().length).toBeGreaterThan(200);

  await page.screenshot({
    path: test.info().outputPath(
      `${headingName.source.toLowerCase().replace(/[^a-z0-9]+/g, "-")}.png`,
    ),
    fullPage: true,
  });

  const finalUrl = page.url();
  await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
  await waitForUi(page);
  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  test.setTimeout(420_000);

  const failures: string[] = [];
  const report = createReport();
  const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};

  const runStep = async (name: ReportKey, fn: () => Promise<void>) => {
    try {
      await fn();
      report[name] = "PASS";
    } catch (error) {
      report[name] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      failures.push(`${name}: ${message}`);
    }
  };

  await runStep("Login", async () => {
    if (page.url() === "about:blank") {
      if (!test.info().project.use.baseURL) {
        throw new Error(
          "Browser started on about:blank. Provide SALEADS_URL/SALEADS_BASE_URL so the test can open the login page for the target environment.",
        );
      }
      await page.goto("/");
      await waitForUi(page);
    }

    const signInWithGoogle = await waitForAnyVisible(
      [
        page.getByRole("button", { name: /Sign in with Google|Iniciar sesión con Google/i }),
        page.getByRole("link", { name: /Sign in with Google|Iniciar sesión con Google/i }),
        page.getByText(/Sign in with Google|Iniciar sesión con Google/i),
      ],
      "Google sign-in button",
      20_000,
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickAndWait(signInWithGoogle, page);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 30_000 });
      await waitForUi(popup);

      const accountOption = await waitForAnyVisible(
        [popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }), popup.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL })],
        `Google account option ${GOOGLE_ACCOUNT_EMAIL}`,
        8_000,
      );
      await accountOption.click();
      await waitForUi(popup);
      await popup.close().catch(() => undefined);
    } else if (page.url().includes("accounts.google.com")) {
      const accountOption = await waitForAnyVisible(
        [page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }), page.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL })],
        `Google account option ${GOOGLE_ACCOUNT_EMAIL}`,
        8_000,
      );
      await clickAndWait(accountOption, page);
    }

    await validateMainAppVisible(page);
    await page.screenshot({
      path: test.info().outputPath("01-dashboard-loaded.png"),
      fullPage: true,
    });
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await waitForAnyVisible(
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      "Negocio section",
    );
    await clickAndWait(negocioSection, page);

    const miNegocioOption = await waitForAnyVisible(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ],
      "Mi Negocio option",
    );
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    await page.screenshot({
      path: test.info().outputPath("02-mi-negocio-menu-expanded.png"),
      fullPage: true,
    });
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusiness = await waitForAnyVisible(
      [
        page.getByRole("menuitem", { name: /^Agregar Negocio$/i }),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      "Agregar Negocio option",
    );
    await clickAndWait(addBusiness, page);

    const modalTitle = await waitForAnyVisible(
      [page.getByRole("heading", { name: /^Crear Nuevo Negocio$/i }), page.getByText(/^Crear Nuevo Negocio$/i)],
      "Crear Nuevo Negocio modal title",
    );
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i)).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

    await page.screenshot({
      path: test.info().outputPath("03-agregar-negocio-modal.png"),
      fullPage: true,
    });

    const businessNameInput = await waitForAnyVisible(
      [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)],
      "Nombre del Negocio input",
    );
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await waitForUi(page);

    const cancelButton = await waitForAnyVisible(
      [page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)],
      "Cancelar button",
    );
    await clickAndWait(cancelButton, page);
  });

  await runStep("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);

    const manageBusiness = await waitForAnyVisible(
      [
        page.getByRole("menuitem", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      "Administrar Negocios option",
    );
    await clickAndWait(manageBusiness, page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await page.screenshot({
      path: test.info().outputPath("04-administrar-negocios-account-page.png"),
      fullPage: true,
    });
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const profileName = page
      .locator("h1, h2, h3, p, span, strong")
      .filter({ hasNotText: /@|BUSINESS PLAN|Cambiar Plan/i })
      .first();
    await expect(profileName).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await validateLegalLink(
      page,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
    );
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await validateLegalLink(
      page,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
    );
  });

  await test.info().attach("final-report.json", {
    body: JSON.stringify(
      {
        report,
        legalUrls,
        failures,
      },
      null,
      2,
    ),
    contentType: "application/json",
  });

  expect(
    failures,
    `One or more validation steps failed.\n\n${JSON.stringify({ report, legalUrls, failures }, null, 2)}`,
  ).toEqual([]);
});
