import { expect, Locator, Page, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

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

type ReportStatus = "PASS" | "FAIL";

type ReportEntry = {
  status: ReportStatus;
  details: string;
};

const REPORT_FIELDS: ReportField[] = [
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

const GOOGLE_LOGIN_REGEX =
  /sign in with google|iniciar sesi[oó]n con google|continuar con google|acceder con google/i;

function createDefaultReport(): Record<ReportField, ReportEntry> {
  return REPORT_FIELDS.reduce(
    (accumulator, field) => {
      accumulator[field] = { status: "FAIL", details: "Step was not executed." };
      return accumulator;
    },
    {} as Record<ReportField, ReportEntry>,
  );
}

async function waitForAppIdle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded").catch(() => undefined);
  await page.waitForTimeout(800);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.first().click();
  await waitForAppIdle(page);
}

async function firstVisibleLocator(locators: Locator[], timeoutMs = 5_000): Promise<Locator | null> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      const isVisible = await locator.first().isVisible().catch(() => false);
      if (isVisible) {
        return locator.first();
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  return null;
}

async function waitForCondition(
  condition: () => Promise<boolean>,
  timeoutMs = 10_000,
  pollEveryMs = 250,
): Promise<boolean> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await condition()) {
      return true;
    }
    await new Promise((resolve) => setTimeout(resolve, pollEveryMs));
  }
  return false;
}

async function takeCheckpoint(page: Page, artifactDir: string, fileName: string, fullPage = false): Promise<void> {
  await mkdir(artifactDir, { recursive: true });
  await page.screenshot({
    path: path.join(artifactDir, fileName),
    fullPage,
  });
}

async function isSidebarVisible(page: Page): Promise<boolean> {
  const candidates = [
    page.locator("aside"),
    page.locator("[role='navigation']"),
    page.getByText(/^Negocio$/i),
    page.getByText(/^Mi Negocio$/i),
  ];
  return (await firstVisibleLocator(candidates, 2_500)) !== null;
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report = createDefaultReport();
  const failures: string[] = [];
  const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};

  const artifactsDir = path.resolve(__dirname, "..", "artifacts");
  const screenshotsDir = path.join(artifactsDir, "screenshots");
  const loginUrl = process.env.SALEADS_LOGIN_URL;

  if (loginUrl && page.url() === "about:blank") {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  }
  await waitForAppIdle(page);

  async function runStep(field: ReportField, stepFn: () => Promise<void>): Promise<void> {
    try {
      await stepFn();
      report[field] = { status: "PASS", details: "Validation completed successfully." };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[field] = { status: "FAIL", details: message };
      failures.push(`${field}: ${message}`);
    }
  }

  await runStep("Login", async () => {
    if (!(await isSidebarVisible(page))) {
      const loginButton = await firstVisibleLocator(
        [
          page.getByRole("button", { name: GOOGLE_LOGIN_REGEX }),
          page.getByRole("link", { name: GOOGLE_LOGIN_REGEX }),
          page.getByText(GOOGLE_LOGIN_REGEX),
        ],
        15_000,
      );

      if (!loginButton) {
        throw new Error("Could not find the 'Sign in with Google' button.");
      }

      const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(page, loginButton);

      const popup = await popupPromise;
      const authPage = popup ?? page;
      await waitForAppIdle(authPage);

      const accountOption = authPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
      const accountSelectorVisible = await accountOption.isVisible().catch(() => false);
      if (accountSelectorVisible) {
        await accountOption.click();
        await waitForAppIdle(authPage);
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 40_000 }).catch(() => undefined);
      }
    }

    const appReady = await waitForCondition(() => isSidebarVisible(page), 60_000);
    if (!appReady) {
      throw new Error("Main interface did not load and sidebar is not visible after login.");
    }

    await takeCheckpoint(page, screenshotsDir, "01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      15_000,
    );
    if (!negocioSection) {
      throw new Error("The 'Negocio' section was not found in the left sidebar.");
    }
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ],
      6_000,
    );
    if (miNegocioOption) {
      await clickAndWait(page, miNegocioOption);
    }

    const addBusinessVisible = await waitForCondition(
      async () =>
        (await firstVisibleLocator(
          [
            page.getByRole("button", { name: /^Agregar Negocio$/i }),
            page.getByRole("link", { name: /^Agregar Negocio$/i }),
            page.getByText(/^Agregar Negocio$/i),
          ],
          1_000,
        )) !== null,
      10_000,
    );

    const manageBusinessVisible = await waitForCondition(
      async () =>
        (await firstVisibleLocator(
          [
            page.getByRole("button", { name: /^Administrar Negocios$/i }),
            page.getByRole("link", { name: /^Administrar Negocios$/i }),
            page.getByText(/^Administrar Negocios$/i),
          ],
          1_000,
        )) !== null,
      10_000,
    );

    if (!addBusinessVisible || !manageBusinessVisible) {
      throw new Error("Mi Negocio submenu did not expose both 'Agregar Negocio' and 'Administrar Negocios'.");
    }

    await takeCheckpoint(page, screenshotsDir, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusinessOption = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      15_000,
    );
    if (!addBusinessOption) {
      throw new Error("'Agregar Negocio' option is not visible.");
    }

    await clickAndWait(page, addBusinessOption);

    const modalTitle = page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first();
    await expect(modalTitle).toBeVisible({ timeout: 15_000 });

    const businessNameInput = await firstVisibleLocator(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[name*='negocio'], input[id*='negocio']"),
      ],
      10_000,
    );
    if (!businessNameInput) {
      throw new Error("Input field 'Nombre del Negocio' is not present.");
    }

    const quotaTextVisible = await page.getByText(/Tienes 2 de 3 negocios/i).isVisible().catch(() => false);
    if (!quotaTextVisible) {
      throw new Error("Text 'Tienes 2 de 3 negocios' is missing in modal.");
    }

    const cancelButton = await firstVisibleLocator(
      [page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)],
      5_000,
    );
    const createButton = await firstVisibleLocator(
      [page.getByRole("button", { name: /^Crear Negocio$/i }), page.getByText(/^Crear Negocio$/i)],
      5_000,
    );

    if (!cancelButton || !createButton) {
      throw new Error("Modal does not expose both 'Cancelar' and 'Crear Negocio' buttons.");
    }

    await businessNameInput.fill("Negocio Prueba Automatización");
    await takeCheckpoint(page, screenshotsDir, "03-agregar-negocio-modal.png");
    await clickAndWait(page, cancelButton);
  });

  await runStep("Administrar Negocios view", async () => {
    const manageOptionVisible = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      3_000,
    );

    if (!manageOptionVisible) {
      const miNegocioSection = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        8_000,
      );
      if (!miNegocioSection) {
        throw new Error("Could not re-expand 'Mi Negocio' menu.");
      }
      await clickAndWait(page, miNegocioSection);
    }

    const manageOption = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      10_000,
    );
    if (!manageOption) {
      throw new Error("'Administrar Negocios' option is not available.");
    }

    await clickAndWait(page, manageOption);

    const sections: RegExp[] = [/Información General/i, /Detalles de la Cuenta/i, /Tus Negocios/i, /Sección Legal/i];
    for (const section of sections) {
      const visible = await page.getByText(section).first().isVisible().catch(() => false);
      if (!visible) {
        throw new Error(`Section '${section.source}' is missing from Administrar Negocios page.`);
      }
    }

    await takeCheckpoint(page, screenshotsDir, "04-administrar-negocios.png", true);
  });

  await runStep("Información General", async () => {
    const checks: Array<[string, Locator[]]> = [
      ["User name", [page.locator("[data-testid='user-name']"), page.locator("h1, h2, h3").filter({ hasText: /\S+/ })]],
      ["User email", [page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)]],
      ["BUSINESS PLAN text", [page.getByText(/BUSINESS PLAN/i)]],
      ["Cambiar Plan button", [page.getByRole("button", { name: /Cambiar Plan/i }), page.getByText(/Cambiar Plan/i)]],
    ];

    for (const [label, locatorCandidates] of checks) {
      const element = await firstVisibleLocator(locatorCandidates, 10_000);
      if (!element) {
        throw new Error(`${label} is not visible in 'Información General'.`);
      }
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    const requiredTexts = [/Cuenta creada/i, /Estado activo/i, /Idioma seleccionado/i];
    for (const requiredText of requiredTexts) {
      const visible = await page.getByText(requiredText).first().isVisible().catch(() => false);
      if (!visible) {
        throw new Error(`Missing text in Detalles de la Cuenta: '${requiredText.source}'.`);
      }
    }
  });

  await runStep("Tus Negocios", async () => {
    const tusNegociosVisible = await page.getByText(/Tus Negocios/i).first().isVisible().catch(() => false);
    if (!tusNegociosVisible) {
      throw new Error("Section heading 'Tus Negocios' is not visible.");
    }

    const addBusinessButton = await firstVisibleLocator(
      [page.getByRole("button", { name: /^Agregar Negocio$/i }), page.getByText(/^Agregar Negocio$/i)],
      10_000,
    );
    if (!addBusinessButton) {
      throw new Error("Button 'Agregar Negocio' is not visible in business list.");
    }

    const quotaVisible = await page.getByText(/Tienes 2 de 3 negocios/i).first().isVisible().catch(() => false);
    if (!quotaVisible) {
      throw new Error("Text 'Tienes 2 de 3 negocios' is not visible in Tus Negocios section.");
    }
  });

  async function validateLegalLink(
    reportField: "Términos y Condiciones" | "Política de Privacidad",
    linkRegex: RegExp,
    headingRegex: RegExp,
    screenshotFileName: string,
  ): Promise<void> {
    const legalLink = await firstVisibleLocator(
      [page.getByRole("link", { name: linkRegex }), page.getByText(linkRegex)],
      15_000,
    );
    if (!legalLink) {
      throw new Error(`Could not find legal link '${reportField}'.`);
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(page, legalLink);
    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await waitForAppIdle(legalPage);

    const headingVisible =
      (await legalPage.getByRole("heading", { name: headingRegex }).first().isVisible().catch(() => false)) ||
      (await legalPage.getByText(headingRegex).first().isVisible().catch(() => false));
    if (!headingVisible) {
      throw new Error(`Heading '${reportField}' was not found on legal content page.`);
    }

    const bodyText = (await legalPage.locator("body").innerText().catch(() => "")).trim();
    if (bodyText.length < 120) {
      throw new Error(`Legal page '${reportField}' appears to have insufficient content.`);
    }

    legalUrls[reportField] = legalPage.url();
    await takeCheckpoint(legalPage, screenshotsDir, screenshotFileName, true);

    if (popup) {
      await popup.close().catch(() => undefined);
      await page.bringToFront();
      await waitForAppIdle(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForAppIdle(page);
    }
  }

  await runStep("Términos y Condiciones", async () => {
    await validateLegalLink(
      "Términos y Condiciones",
      /T[ée]rminos y Condiciones/i,
      /T[ée]rminos y Condiciones/i,
      "08-terminos-y-condiciones.png",
    );
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalLink(
      "Política de Privacidad",
      /Pol[íi]tica de Privacidad/i,
      /Pol[íi]tica de Privacidad/i,
      "09-politica-de-privacidad.png",
    );
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      currentUrl: page.url(),
      loginUrlProvided: Boolean(loginUrl),
    },
    results: report,
    legalUrls,
  };

  await mkdir(artifactsDir, { recursive: true });
  await writeFile(path.join(artifactsDir, "saleads-mi-negocio-final-report.json"), JSON.stringify(finalReport, null, 2));

  const markdownLines = [
    "# SaleADS Mi Negocio Final Report",
    "",
    `Generated at: ${finalReport.generatedAt}`,
    "",
    "| Validation Step | Status | Details |",
    "| --- | --- | --- |",
    ...REPORT_FIELDS.map((field) => `| ${field} | ${report[field].status} | ${report[field].details} |`),
    "",
    "## Legal URLs",
    "",
    `- Términos y Condiciones: ${legalUrls["Términos y Condiciones"] ?? "N/A"}`,
    `- Política de Privacidad: ${legalUrls["Política de Privacidad"] ?? "N/A"}`,
  ];
  await writeFile(path.join(artifactsDir, "saleads-mi-negocio-final-report.md"), markdownLines.join("\n"));

  expect(
    failures,
    `One or more validations failed.\n${failures.length > 0 ? failures.join("\n") : "No failures."}`,
  ).toEqual([]);
});
