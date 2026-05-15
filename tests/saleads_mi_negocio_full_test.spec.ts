import { expect, type Locator, type Page, test } from "@playwright/test";

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
  details: string;
};

function initializeReport(): Record<ReportField, StepResult> {
  return REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = { status: "FAIL", details: "Not executed." };
      return acc;
    },
    {} as Record<ReportField, StepResult>,
  );
}

function toRegex(value: string): RegExp {
  const escaped = value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`^${escaped}$`, "i");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
}

async function firstVisibleLocator(candidates: Locator[], timeout = 10_000): Promise<Locator | null> {
  const endTime = Date.now() + timeout;
  while (Date.now() < endTime) {
    for (const candidate of candidates) {
      if (await candidate.first().isVisible().catch(() => false)) {
        return candidate.first();
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return null;
}

async function findClickableByText(page: Page, text: string): Promise<Locator | null> {
  const regex = toRegex(text);
  return firstVisibleLocator([
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByRole("tab", { name: regex }),
    page.getByText(text, { exact: true }),
  ]);
}

async function clickByText(page: Page, text: string): Promise<void> {
  const target = await findClickableByText(page, text);
  if (!target) {
    throw new Error(`Could not find clickable element with text "${text}".`);
  }

  await target.scrollIntoViewIfNeeded();
  await target.click();
  await waitForUi(page);
}

async function captureCheckpoint(
  page: Page,
  testOutputPath: (path: string) => string,
  label: string,
  fullPage = false,
): Promise<void> {
  const safeLabel = label.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "");
  await page.screenshot({
    path: testOutputPath(`${safeLabel}.png`),
    fullPage,
  });
}

async function maybeHandleGoogleAccountSelection(authPage: Page): Promise<void> {
  await authPage.waitForLoadState("domcontentloaded");
  const isGoogleDomain = /accounts\.google\.com/i.test(authPage.url());
  if (!isGoogleDomain) {
    return;
  }

  const accountLocator = await firstVisibleLocator(
    [
      authPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      authPage.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      authPage.getByText(GOOGLE_ACCOUNT_EMAIL),
    ],
    10_000,
  );

  if (accountLocator) {
    await accountLocator.click();
    await authPage.waitForLoadState("domcontentloaded");
  }
}

async function ensureOnLoginPage(page: Page): Promise<void> {
  const explicitLoginUrl = process.env.SALEADS_LOGIN_URL?.trim();
  const baseUrl = process.env.SALEADS_BASE_URL?.trim();

  if (explicitLoginUrl) {
    await page.goto(explicitLoginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (baseUrl) {
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_LOGIN_URL or SALEADS_BASE_URL for the current environment. This test avoids hardcoded domains and can run against dev/staging/prod via environment variables.",
    );
  }
}

async function openLegalLinkAndValidate(
  page: Page,
  linkText: "Términos y Condiciones" | "Política de Privacidad",
  headingRegex: RegExp,
  testOutputPath: (path: string) => string,
  screenshotLabel: string,
): Promise<string> {
  const link = await findClickableByText(page, linkText);
  if (!link) {
    throw new Error(`Could not find legal link "${linkText}".`);
  }

  const originUrl = page.url();
  const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);

  await link.click();
  const popup = await popupPromise;

  let legalPage = page;
  let openedNewTab = false;

  if (popup) {
    legalPage = popup;
    openedNewTab = true;
    await legalPage.waitForLoadState("domcontentloaded");
  } else {
    await waitForUi(page);
  }

  await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({ timeout: 30_000 });

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  expect(bodyText.length).toBeGreaterThan(120);

  await captureCheckpoint(legalPage, testOutputPath, screenshotLabel, true);
  const finalUrl = legalPage.url();
  test.info().attach(`${screenshotLabel}_url`, {
    contentType: "text/plain",
    body: finalUrl,
  });

  if (openedNewTab) {
    await legalPage.close();
    await page.bringToFront();
  } else if (page.url() !== originUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = initializeReport();
  let blockedBy: ReportField | null = null;

  const runStep = async (
    field: ReportField,
    stepFn: () => Promise<void>,
    options?: { blocksNextSteps?: boolean },
  ): Promise<void> => {
    if (blockedBy) {
      report[field] = {
        status: "FAIL",
        details: `Blocked because "${blockedBy}" failed.`,
      };
      return;
    }

    try {
      await stepFn();
      report[field] = { status: "PASS", details: "Validation passed." };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      report[field] = { status: "FAIL", details: errorMessage };
      if (options?.blocksNextSteps) {
        blockedBy = field;
      }
    }
  };

  await runStep(
    "Login",
    async () => {
      await ensureOnLoginPage(page);

      const loginButton = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /sign in with google/i }),
          page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
          page.getByRole("button", { name: /continuar con google/i }),
          page.getByRole("link", { name: /google/i }),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        ],
        20_000,
      );

      if (!loginButton) {
        throw new Error("Could not find the Google login button.");
      }

      const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
      await loginButton.click();
      const popup = await popupPromise;

      if (popup) {
        await maybeHandleGoogleAccountSelection(popup);
        await popup.waitForEvent("close", { timeout: 120_000 }).catch(() => undefined);
      } else {
        await maybeHandleGoogleAccountSelection(page);
      }

      await waitForUi(page);

      const mainUi = await firstVisibleLocator(
        [page.getByRole("main"), page.locator("main"), page.locator("aside"), page.getByText(/negocio|dashboard/i)],
        30_000,
      );
      if (!mainUi) {
        throw new Error("Main application interface did not appear after login.");
      }

      const sidebar = await firstVisibleLocator(
        [page.getByRole("navigation"), page.locator("aside"), page.getByText(/negocio/i)],
        20_000,
      );
      if (!sidebar) {
        throw new Error("Left sidebar navigation is not visible after login.");
      }

      await captureCheckpoint(page, testInfo.outputPath.bind(testInfo), "dashboard_loaded", true);
    },
    { blocksNextSteps: true },
  );

  await runStep(
    "Mi Negocio menu",
    async () => {
      const sidebar = await firstVisibleLocator(
        [page.getByRole("navigation"), page.locator("aside"), page.getByText(/negocio/i)],
        20_000,
      );
      if (!sidebar) {
        throw new Error("Could not find left sidebar navigation.");
      }

      const negocioEntry = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /negocio/i }),
          page.getByRole("link", { name: /negocio/i }),
          page.getByText("Negocio", { exact: true }),
        ],
        12_000,
      );
      if (!negocioEntry) {
        throw new Error('Could not find section labeled "Negocio".');
      }

      await negocioEntry.click();
      await waitForUi(page);

      await clickByText(page, "Mi Negocio");

      await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible({ timeout: 15_000 });

      await captureCheckpoint(page, testInfo.outputPath.bind(testInfo), "mi_negocio_menu_expanded");
    },
    { blocksNextSteps: true },
  );

  await runStep("Agregar Negocio modal", async () => {
    await clickByText(page, "Agregar Negocio");

    await expect(page.getByRole("heading", { name: /crear nuevo negocio/i })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await captureCheckpoint(page, testInfo.outputPath.bind(testInfo), "agregar_negocio_modal");

    const nameInput = page.getByLabel(/nombre del negocio/i);
    await nameInput.fill("Negocio Prueba Automatización");
    await clickByText(page, "Cancelar");
  });

  await runStep(
    "Administrar Negocios view",
    async () => {
      const adminOptionVisible = await page.getByText("Administrar Negocios", { exact: true }).isVisible().catch(() => false);
      if (!adminOptionVisible) {
        const miNegocioEntry = await findClickableByText(page, "Mi Negocio");
        if (miNegocioEntry) {
          await miNegocioEntry.click();
          await waitForUi(page);
        }
      }

      await clickByText(page, "Administrar Negocios");

      await expect(page.getByText("Información General", { exact: true })).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible({ timeout: 30_000 });

      await captureCheckpoint(page, testInfo.outputPath.bind(testInfo), "administrar_negocios_view", true);
    },
    { blocksNextSteps: true },
  );

  await runStep("Información General", async () => {
    const section = page.locator("section,div").filter({ has: page.getByText("Información General", { exact: true }) }).first();
    await expect(section).toBeVisible();

    const emailLocator = await firstVisibleLocator(
      [
        section.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
        section.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
      ],
      10_000,
    );
    if (!emailLocator) {
      throw new Error("User email is not visible in Información General.");
    }

    const nameLocator = await firstVisibleLocator(
      [
        section.getByText(/juan\s*lucas|barbier|garzon/i),
        section.getByText(/nombre|name|usuario/i),
        section.locator("h1,h2,h3,h4,h5,p,span,div"),
      ],
      8_000,
    );
    if (!nameLocator) {
      throw new Error("User name is not visible in Información General.");
    }

    await expect(section.getByText("BUSINESS PLAN", { exact: true })).toBeVisible();
    await expect(section.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const section = page.locator("section,div").filter({ has: page.getByText("Detalles de la Cuenta", { exact: true }) }).first();
    await expect(section).toBeVisible();

    await expect(section.getByText(/cuenta creada/i)).toBeVisible();
    await expect(section.getByText(/estado activo/i)).toBeVisible();
    await expect(section.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const section = page.locator("section,div").filter({ has: page.getByText("Tus Negocios", { exact: true }) }).first();
    await expect(section).toBeVisible();

    await expect(section.getByRole("button", { name: "Agregar Negocio" })).toBeVisible();
    await expect(section.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();

    const listCandidate = await firstVisibleLocator(
      [
        section.locator("li"),
        section.locator("[role='listitem']"),
        section.locator("table tbody tr"),
        section.locator("[class*='business']"),
      ],
      8_000,
    );
    if (!listCandidate) {
      throw new Error("Business list is not visible in Tus Negocios.");
    }
  });

  await runStep("Términos y Condiciones", async () => {
    await openLegalLinkAndValidate(
      page,
      "Términos y Condiciones",
      /t[ée]rminos y condiciones/i,
      testInfo.outputPath.bind(testInfo),
      "terminos_y_condiciones",
    );
  });

  await runStep("Política de Privacidad", async () => {
    await openLegalLinkAndValidate(
      page,
      "Política de Privacidad",
      /pol[íi]tica de privacidad/i,
      testInfo.outputPath.bind(testInfo),
      "politica_de_privacidad",
    );
  });

  const orderedReport = REPORT_FIELDS.map((field) => ({
    step: field,
    result: report[field].status,
    details: report[field].details,
  }));

  // Final report required by the workflow prompt.
  console.table(orderedReport);

  const failed = orderedReport.filter((item) => item.result === "FAIL");
  expect(
    failed,
    `Final report has failing checks:\n${failed.map((item) => `- ${item.step}: ${item.details}`).join("\n")}`,
  ).toEqual([]);
});
