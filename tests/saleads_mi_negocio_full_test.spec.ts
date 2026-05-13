import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details?: string;
  url?: string;
};

function sanitizeFileName(input: string): string {
  return input.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1_000);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  checkpointName: string,
  fullPage = false,
): Promise<void> {
  const path = testInfo.outputPath(`${sanitizeFileName(checkpointName)}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(`checkpoint-${checkpointName}`, {
    path,
    contentType: "image/png",
  });
}

async function findVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const current = candidate.first();
    const isVisible = await current.isVisible().catch(() => false);

    if (isVisible) {
      return current;
    }
  }

  return null;
}

async function findActionByText(page: Page, text: RegExp): Promise<Locator | null> {
  return findVisible([
    page.getByRole("button", { name: text }),
    page.getByRole("link", { name: text }),
    page.getByRole("menuitem", { name: text }),
    page.getByRole("tab", { name: text }),
    page.getByText(text),
  ]);
}

async function ensureTextVisible(page: Page, text: RegExp, label: string): Promise<void> {
  const locator = await findVisible([page.getByRole("heading", { name: text }), page.getByText(text)]);

  if (!locator) {
    throw new Error(`Could not validate "${label}" because it is not visible.`);
  }
}

async function getSectionContainer(page: Page, sectionTitle: RegExp): Promise<Locator> {
  const heading = await findVisible([
    page.getByRole("heading", { name: sectionTitle }),
    page.getByText(sectionTitle),
  ]);

  if (!heading) {
    throw new Error(`Section "${sectionTitle.source}" was not found.`);
  }

  return heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const results = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL" as StepStatus }]),
  ) as Record<ReportField, StepResult>;

  async function runStep(
    field: ReportField,
    action: () => Promise<Partial<StepResult> | void>,
  ): Promise<void> {
    try {
      const extra = await action();
      results[field] = {
        status: "PASS",
        ...(extra ?? {}),
      };
    } catch (error) {
      results[field] = {
        status: "FAIL",
        details: errorMessage(error),
      };
    }
  }

  await runStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL;

    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    if (!loginUrl && page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_LOGIN_URL for this environment or start the test with the login page already open.",
      );
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
    const loginButton = await findActionByText(
      page,
      /sign in with google|iniciar sesión con google|iniciar con google|continuar con google|google/i,
    );

    if (!loginButton) {
      throw new Error('Could not find a visible "Sign in with Google" action.');
    }

    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });

      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }

      await popup.waitForLoadState("domcontentloaded").catch(() => undefined);
    } else {
      const inlineAccountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
      if (await inlineAccountOption.isVisible().catch(() => false)) {
        await inlineAccountOption.click();
      }
    }

    await page.waitForLoadState("domcontentloaded", { timeout: 45_000 }).catch(() => undefined);
    await page.waitForTimeout(2_000);

    const sidebar = await findVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("nav"),
    ]);

    if (!sidebar) {
      throw new Error("Main interface loaded but left sidebar/navigation is not visible.");
    }

    await captureCheckpoint(page, testInfo, "dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const miNegocioToggle = await findActionByText(page, /mi negocio|negocio/i);

    if (!miNegocioToggle) {
      throw new Error('Could not find "Negocio" or "Mi Negocio" in the sidebar.');
    }

    await miNegocioToggle.click();
    await waitForUi(page);

    await ensureTextVisible(page, /agregar negocio/i, "Agregar Negocio");
    await ensureTextVisible(page, /administrar negocios/i, "Administrar Negocios");

    await captureCheckpoint(page, testInfo, "mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusinessAction = await findActionByText(page, /agregar negocio/i);

    if (!addBusinessAction) {
      throw new Error('Could not find "Agregar Negocio".');
    }

    await addBusinessAction.click();
    await waitForUi(page);

    await ensureTextVisible(page, /crear nuevo negocio/i, "Crear Nuevo Negocio");
    await ensureTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i, "Tienes 2 de 3 negocios");
    await ensureTextVisible(page, /cancelar/i, "Cancelar");
    await ensureTextVisible(page, /crear negocio/i, "Crear Negocio");

    const businessNameInput = await findVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator('input[name*="negocio" i]'),
      page.locator("input").first(),
    ]);

    if (!businessNameInput) {
      throw new Error('Could not find input field "Nombre del Negocio".');
    }

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await waitForUi(page);

    const cancelButton = await findActionByText(page, /cancelar/i);
    if (!cancelButton) {
      throw new Error('Could not find "Cancelar" button to close modal.');
    }

    await captureCheckpoint(page, testInfo, "agregar-negocio-modal");

    await cancelButton.click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const manageBusinessOptionVisible = await findActionByText(page, /administrar negocios/i);

    if (!manageBusinessOptionVisible) {
      const miNegocioToggle = await findActionByText(page, /mi negocio|negocio/i);

      if (!miNegocioToggle) {
        throw new Error('Could not re-open "Mi Negocio" menu.');
      }

      await miNegocioToggle.click();
      await waitForUi(page);
    }

    const manageBusinessOption = await findActionByText(page, /administrar negocios/i);
    if (!manageBusinessOption) {
      throw new Error('Could not find "Administrar Negocios".');
    }

    await manageBusinessOption.click();
    await waitForUi(page);

    await ensureTextVisible(page, /información general/i, "Información General");
    await ensureTextVisible(page, /detalles de la cuenta/i, "Detalles de la Cuenta");
    await ensureTextVisible(page, /tus negocios/i, "Tus Negocios");
    await ensureTextVisible(page, /sección legal/i, "Sección Legal");

    await captureCheckpoint(page, testInfo, "administrar-negocios-page", true);
  });

  await runStep("Información General", async () => {
    const section = await getSectionContainer(page, /información general/i);
    const sectionText = (await section.innerText()).replace(/\s+/g, " ").trim();

    const emailRegex = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
    if (!emailRegex.test(sectionText)) {
      throw new Error("User email is not visible in Información General.");
    }

    const candidateNameText = sectionText
      .replace(/información general/gi, "")
      .replace(/business plan/gi, "")
      .replace(/cambiar plan/gi, "")
      .replace(emailRegex, "")
      .trim();

    if (!/[A-Za-zÀ-ÿ]{2,}(?:\s+[A-Za-zÀ-ÿ]{2,})+/.test(candidateNameText)) {
      throw new Error("User name is not clearly visible in Información General.");
    }

    await ensureTextVisible(page, /business plan/i, "BUSINESS PLAN");
    const changePlanButton = await findActionByText(page, /cambiar plan/i);
    if (!changePlanButton) {
      throw new Error('Button "Cambiar Plan" is not visible.');
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    const section = await getSectionContainer(page, /detalles de la cuenta/i);
    const sectionText = await section.innerText();

    if (!/cuenta creada/i.test(sectionText)) {
      throw new Error('Text "Cuenta creada" is not visible.');
    }

    if (!/estado activo/i.test(sectionText)) {
      throw new Error('Text "Estado activo" is not visible.');
    }

    if (!/idioma seleccionado/i.test(sectionText)) {
      throw new Error('Text "Idioma seleccionado" is not visible.');
    }
  });

  await runStep("Tus Negocios", async () => {
    const section = await getSectionContainer(page, /tus negocios/i);
    const sectionText = await section.innerText();

    const addBusinessButton = await findVisible([
      section.getByRole("button", { name: /agregar negocio/i }),
      section.getByRole("link", { name: /agregar negocio/i }),
      page.getByRole("button", { name: /agregar negocio/i }),
    ]);

    if (!addBusinessButton) {
      throw new Error('Button "Agregar Negocio" is not visible in Tus Negocios.');
    }

    if (!/tienes\s*2\s*de\s*3\s*negocios/i.test(sectionText)) {
      throw new Error('Text "Tienes 2 de 3 negocios" is not visible in Tus Negocios.');
    }

    const listCandidates = section.locator(
      "li, ul li, tr, [role='row'], [class*='business'], [class*='negocio']",
    );
    const listCount = await listCandidates.count();

    if (listCount === 0 && !/negocio/i.test(sectionText)) {
      throw new Error("Business list is not visible.");
    }
  });

  async function validateLegalDocument(
    field: Extract<ReportField, "Términos y Condiciones" | "Política de Privacidad">,
    linkText: RegExp,
    headingText: RegExp,
    screenshotName: string,
  ): Promise<void> {
    await runStep(field, async () => {
      const link = await findActionByText(page, linkText);

      if (!link) {
        throw new Error(`Could not find legal link for "${field}".`);
      }

      const appUrlBeforeClick = page.url();
      const maybePopupPage = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);

      await link.click();
      await waitForUi(page);

      const popupPage = await maybePopupPage;
      const legalPage = popupPage ?? page;

      await legalPage.waitForLoadState("domcontentloaded", { timeout: 30_000 });
      await legalPage.waitForTimeout(1_000);

      const heading = await findVisible([
        legalPage.getByRole("heading", { name: headingText }),
        legalPage.getByText(headingText),
      ]);
      if (!heading) {
        throw new Error(`Heading for "${field}" was not found.`);
      }

      const legalContent = (await legalPage.locator("body").innerText()).trim();
      if (legalContent.length < 200) {
        throw new Error(`Legal content for "${field}" appears too short or missing.`);
      }

      await captureCheckpoint(legalPage, testInfo, screenshotName, true);

      const finalUrl = legalPage.url();

      if (popupPage) {
        await popupPage.close();
        await page.bringToFront();
        await waitForUi(page);
      } else if (page.url() !== appUrlBeforeClick) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
        await waitForUi(page);
      }

      return {
        url: finalUrl,
        details: `Validated legal content at URL: ${finalUrl}`,
      };
    });
  }

  await validateLegalDocument(
    "Términos y Condiciones",
    /términos y condiciones|terminos y condiciones/i,
    /términos y condiciones|terminos y condiciones/i,
    "terminos-y-condiciones",
  );

  await validateLegalDocument(
    "Política de Privacidad",
    /política de privacidad|politica de privacidad/i,
    /política de privacidad|politica de privacidad/i,
    "politica-de-privacidad",
  );

  const reportSummary = REPORT_FIELDS.map((field) => ({
    field,
    status: results[field].status,
    details: results[field].details ?? "",
    url: results[field].url ?? "",
  }));

  const reportJson = JSON.stringify(results, null, 2);
  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: reportJson,
    contentType: "application/json",
  });

  console.table(reportSummary);

  const failingFields = REPORT_FIELDS.filter((field) => results[field].status === "FAIL");
  expect(
    failingFields,
    `Some validations failed:\n${failingFields
      .map((field) => `- ${field}: ${results[field].details ?? "Unknown error"}`)
      .join("\n")}`,
  ).toEqual([]);
});
