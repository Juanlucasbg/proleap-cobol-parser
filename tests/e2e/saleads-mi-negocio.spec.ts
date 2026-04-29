import { expect, Locator, Page, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";

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

type StepReport = Record<ReportField, { status: "PASS" | "FAIL"; details?: string }>;

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");

  try {
    await page.waitForLoadState("networkidle", { timeout: 8_000 });
  } catch {
    // Some environments keep long-running requests open.
  }
}

async function firstVisible(locators: Locator[]): Promise<Locator> {
  for (const locator of locators) {
    const count = await locator.count();
    if (count === 0) {
      continue;
    }

    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  throw new Error("No visible locator matched.");
}

async function clickAndWait(locator: Locator, pageAfterClick: Page): Promise<void> {
  await locator.click();
  await waitForUi(pageAfterClick);
}

function buildDefaultReport(): StepReport {
  return {
    Login: { status: "FAIL", details: "Not executed" },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed" },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed" },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed" },
    "Información General": { status: "FAIL", details: "Not executed" },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed" },
    "Tus Negocios": { status: "FAIL", details: "Not executed" },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed" },
    "Política de Privacidad": { status: "FAIL", details: "Not executed" }
  };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildDefaultReport();
  const legalUrls: Record<"terminos" | "privacidad", string | null> = {
    terminos: null,
    privacidad: null
  };

  const evidenceDir = testInfo.outputPath("evidence");
  await mkdir(evidenceDir, { recursive: true });

  const screenshot = async (name: string, targetPage: Page = page, fullPage = true): Promise<void> => {
    await targetPage.screenshot({
      path: `${evidenceDir}/${name}.png`,
      fullPage
    });
  };

  const markPass = (key: ReportField): void => {
    report[key] = { status: "PASS" };
  };

  const markFail = (key: ReportField, error: unknown): void => {
    const details = error instanceof Error ? error.message : String(error);
    report[key] = { status: "FAIL", details };
  };

  const runStep = async (key: ReportField, handler: () => Promise<void>): Promise<void> => {
    try {
      await handler();
      markPass(key);
    } catch (error) {
      markFail(key, error);
    }
  };

  await runStep("Login", async () => {
    const configuredLoginUrl = process.env.SALEADS_LOGIN_URL;

    if (page.url() === "about:blank") {
      if (!configuredLoginUrl) {
        throw new Error(
          "Browser started on about:blank. Set SALEADS_LOGIN_URL to the environment login page URL."
        );
      }
      await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    const loginButton = await firstVisible([
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[oó]n con google/i),
      page.getByText(/google/i)
    ]);

    await clickAndWait(loginButton, page);

    const popup = await popupPromise;
    const authPage = popup ?? page;
    await authPage.bringToFront();
    await waitForUi(authPage);

    const accountOption = await firstVisible([
      authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
      authPage.locator(`text=${GOOGLE_ACCOUNT_EMAIL}`),
      authPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") })
    ]).catch(() => null);

    if (accountOption) {
      await clickAndWait(accountOption, authPage);
    }

    await page.bringToFront();

    await expect(
      firstVisible([
        page.locator("aside").filter({ hasText: /negocio|mi negocio|dashboard/i }),
        page.getByRole("navigation"),
        page.getByText(/mi negocio|dashboard|negocio/i)
      ])
    ).resolves.toBeDefined();

    await screenshot("01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisible([
      page.getByRole("button", { name: /negocio/i }),
      page.getByRole("link", { name: /negocio/i }),
      page.getByText(/^negocio$/i),
      page.getByText(/negocio/i)
    ]);
    await clickAndWait(negocioSection, page);

    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();

    await screenshot("02-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickAndWait(
      await firstVisible([
        page.getByRole("button", { name: /^agregar negocio$/i }),
        page.getByRole("link", { name: /^agregar negocio$/i }),
        page.getByText(/^agregar negocio$/i)
      ]),
      page
    );

    const modal = await firstVisible([
      page.getByRole("dialog"),
      page.locator('[role="dialog"]'),
      page.locator(".modal")
    ]);

    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(modal.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await modal.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatización");
    await screenshot("03-agregar-negocio-modal");

    await clickAndWait(modal.getByRole("button", { name: /cancelar/i }), page);
    await expect(modal).toBeHidden();
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocio = await firstVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ]);
      await clickAndWait(miNegocio, page);
    }

    await clickAndWait(
      await firstVisible([
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ]),
      page
    );

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

    await screenshot("04-administrar-negocios-account-page");
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const userNameVisible = await firstVisible([
      page.locator('[data-testid*="name" i]'),
      page.getByText(/usuario|nombre/i),
      page.locator("h1, h2, h3").filter({ hasText: /\S+/ })
    ])
      .then(async (locator) => locator.isVisible())
      .catch(() => false);
    expect(userNameVisible).toBeTruthy();

    const userEmailVisible = await firstVisible([
      page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i"),
      page.getByText(/@/)
    ])
      .then(async (locator) => locator.isVisible())
      .catch(() => false);
    expect(userEmailVisible).toBeTruthy();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
  });

  const validateLegalLink = async (
    reportKey: "Términos y Condiciones" | "Política de Privacidad",
    linkName: RegExp,
    headingName: RegExp,
    screenshotName: string,
    urlKey: "terminos" | "privacidad"
  ): Promise<void> => {
    await runStep(reportKey, async () => {
      const appPage = page;
      const popupPromise = appPage.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);

      const legalLink = await firstVisible([
        appPage.getByRole("link", { name: linkName }),
        appPage.getByRole("button", { name: linkName }),
        appPage.getByText(linkName)
      ]);
      await legalLink.click();

      const popup = await popupPromise;
      const legalPage = popup ?? appPage;
      await legalPage.bringToFront();
      await waitForUi(legalPage);

      const heading = await firstVisible([
        legalPage.getByRole("heading", { name: headingName }),
        legalPage.getByText(headingName)
      ]);
      await expect(heading).toBeVisible();

      const legalContent = await firstVisible([
        legalPage.locator("main p"),
        legalPage.locator("article p"),
        legalPage.locator("body p"),
        legalPage.getByText(/(condiciones|pol[ií]tica|privacidad|t[eé]rminos)/i)
      ]);
      await expect(legalContent).toBeVisible();

      legalUrls[urlKey] = legalPage.url();
      await screenshot(screenshotName, legalPage);

      if (popup) {
        await popup.close();
        await appPage.bringToFront();
        await waitForUi(appPage);
      } else {
        await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
        await appPage.bringToFront();
        await waitForUi(appPage);
      }
    });
  };

  await validateLegalLink(
    "Términos y Condiciones",
    /t[eé]rminos y condiciones/i,
    /t[eé]rminos y condiciones/i,
    "05-terminos-y-condiciones",
    "terminos"
  );

  await validateLegalLink(
    "Política de Privacidad",
    /pol[ií]tica de privacidad/i,
    /pol[ií]tica de privacidad/i,
    "06-politica-de-privacidad",
    "privacidad"
  );

  const finalReportPayload = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    result: report,
    finalUrls: {
      terminosYCondiciones: legalUrls.terminos,
      politicaDePrivacidad: legalUrls.privacidad
    }
  };

  await writeFile(
    `${evidenceDir}/final-report.json`,
    JSON.stringify(finalReportPayload, null, 2),
    "utf-8"
  );

  // Emit structured output in console logs for automation consumers.
  // eslint-disable-next-line no-console
  console.log("Final Report:", JSON.stringify(finalReportPayload, null, 2));

  const failedSteps = Object.entries(report).filter(([, stepResult]) => stepResult.status === "FAIL");
  expect(
    failedSteps,
    `Validation failed in ${failedSteps.length} step(s): ${failedSteps
      .map(([step, status]) => `${step} (${status.details ?? "no details"})`)
      .join("; ")}`
  ).toHaveLength(0);
});
