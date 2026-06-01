import fs from "node:fs/promises";
import path from "node:path";
import { expect, type BrowserContext, type Locator, type Page, type TestInfo, test } from "@playwright/test";

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const stepNames = [
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

type StepName = (typeof stepNames)[number];
type StepStatus = "PASS" | "FAIL";

const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 5_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator.first().isVisible().catch(() => false);
}

async function getFirstVisible(candidates: Locator[], description: string): Promise<Locator> {
  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate.first();
    }
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page).catch((error: unknown) => {
    const message = error instanceof Error ? error.message : String(error);
    if (!/Target page, context or browser has been closed/i.test(message)) {
      throw error;
    }
  });
}

async function takeCheckpointScreenshot(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false
): Promise<void> {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, {
    path: screenshotPath,
    contentType: "image/png"
  });
}

async function ensureSubmenuExpanded(page: Page): Promise<void> {
  const administrarVisible = await isVisible(page.getByText(/^Administrar Negocios$/i));
  const agregarVisible = await isVisible(page.getByText(/^Agregar Negocio$/i));
  if (administrarVisible && agregarVisible) {
    return;
  }

  const miNegocioEntry = await getFirstVisible(
    [
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ],
    "Mi Negocio"
  );
  await clickAndWait(miNegocioEntry, page);
}

async function openLegalLinkAndReturn(
  appPage: Page,
  context: BrowserContext,
  testInfo: TestInfo,
  linkText: RegExp,
  headingText: RegExp,
  screenshotName: string
): Promise<string> {
  const link = await getFirstVisible(
    [
      appPage.getByRole("link", { name: linkText }),
      appPage.getByRole("button", { name: linkText }),
      appPage.getByText(linkText)
    ],
    `Legal link ${String(linkText)}`
  );

  const popupPromise = context.waitForEvent("page", { timeout: 7_500 }).catch(() => null);
  await clickAndWait(link, appPage);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    const heading = popup.getByRole("heading", { name: headingText }).first();
    if (await isVisible(heading)) {
      await expect(heading).toBeVisible();
    } else {
      await expect(popup.getByText(headingText).first()).toBeVisible();
    }
    await expect(popup.locator("body")).toContainText(/.{30,}/);
    await takeCheckpointScreenshot(popup, testInfo, screenshotName, true);
    const finalUrl = popup.url();
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return finalUrl;
  }

  const heading = appPage.getByRole("heading", { name: headingText }).first();
  if (await isVisible(heading)) {
    await expect(heading).toBeVisible();
  } else {
    await expect(appPage.getByText(headingText).first()).toBeVisible();
  }
  await expect(appPage.locator("body")).toContainText(/.{30,}/);
  await takeCheckpointScreenshot(appPage, testInfo, screenshotName, true);
  const finalUrl = appPage.url();
  await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
  await waitForUi(appPage);
  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const targetUrl = process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;
  expect(
    targetUrl,
    "Set SALEADS_BASE_URL (or BASE_URL) to the current SaleADS environment login URL."
  ).toBeTruthy();

  await page.goto(targetUrl as string, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  const report: Record<StepName, StepStatus> = Object.fromEntries(
    stepNames.map((name) => [name, "FAIL"])
  ) as Record<StepName, StepStatus>;
  const errors: string[] = [];
  const legalUrls: Record<string, string> = {};

  const runStep = async (stepName: StepName, action: () => Promise<void>): Promise<void> => {
    try {
      await test.step(stepName, action);
      report[stepName] = "PASS";
    } catch (error) {
      report[stepName] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      errors.push(`${stepName}: ${message}`);
    }
  };

  await runStep("Login", async () => {
    const loginCandidates = [
      page.getByRole("button", { name: /google|sign in|iniciar sesión|acceder/i }),
      page.getByRole("link", { name: /google|sign in|iniciar sesión|acceder/i }),
      page.locator("button, a").filter({ hasText: /google/i })
    ];

    let loginElement: Locator | null = null;
    for (const candidate of loginCandidates) {
      if (await isVisible(candidate)) {
        loginElement = candidate.first();
        break;
      }
    }

    if (loginElement) {
      const popupPromise = context.waitForEvent("page", { timeout: 7_500 }).catch(() => null);
      await clickAndWait(loginElement, page);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const accountOption = popup.getByText(ACCOUNT_EMAIL, { exact: false });
        if (await isVisible(accountOption)) {
          await clickAndWait(accountOption.first(), popup);
        }
        await popup.waitForClose({ timeout: 20_000 }).catch(() => undefined);
        await page.bringToFront();
      } else {
        const samePageAccount = page.getByText(ACCOUNT_EMAIL, { exact: false });
        if (await isVisible(samePageAccount)) {
          await clickAndWait(samePageAccount.first(), page);
        }
      }
    }

    const mainInterface = await getFirstVisible(
      [page.locator("main"), page.locator("#root"), page.locator("body")],
      "main app interface"
    );
    await expect(mainInterface).toBeVisible();
    const leftSidebar = await getFirstVisible(
      [page.locator("aside"), page.locator("nav"), page.locator("[data-testid*='sidebar']")],
      "left sidebar navigation"
    );
    await expect(leftSidebar).toBeVisible();
    await takeCheckpointScreenshot(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioCandidates = [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ];
    for (const candidate of negocioCandidates) {
      if (await isVisible(candidate)) {
        await clickAndWait(candidate.first(), page);
        break;
      }
    }

    const miNegocioEntry = await getFirstVisible(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      "Mi Negocio option"
    );
    await clickAndWait(miNegocioEntry, page);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();
    await takeCheckpointScreenshot(page, testInfo, "02-mi-negocio-menu-expanded.png", false);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await getFirstVisible(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      "Agregar Negocio"
    );
    await clickAndWait(agregarNegocio, page);

    const modal = await getFirstVisible(
      [page.getByRole("dialog"), page.locator("[role='dialog'], .modal, [data-state='open']")],
      "Crear Nuevo Negocio modal"
    );
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const businessInput = await getFirstVisible(
      [
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
        modal.locator("input[type='text']")
      ],
      "Nombre del Negocio input"
    );
    await expect(businessInput).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await takeCheckpointScreenshot(page, testInfo, "03-crear-nuevo-negocio-modal.png", false);

    await businessInput.click();
    await businessInput.fill("Negocio Prueba Automatización");
    await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }), page);
    await expect(modal).not.toBeVisible();
  });

  await runStep("Administrar Negocios view", async () => {
    await ensureSubmenuExpanded(page);

    const administrarNegocios = await getFirstVisible(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ],
      "Administrar Negocios option"
    );
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();
    await takeCheckpointScreenshot(page, testInfo, "04-administrar-negocios-page.png", true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(emailRegex).first()).toBeVisible();
    await expect(
      page.getByText(/Nombre|Usuario|User/i).first(),
      "Expected a visible user name or user label in Información General."
    ).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    const termsUrl = await openLegalLinkAndReturn(
      page,
      context,
      testInfo,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones.png"
    );
    legalUrls["Términos y Condiciones"] = termsUrl;
  });

  await runStep("Política de Privacidad", async () => {
    const privacyUrl = await openLegalLinkAndReturn(
      page,
      context,
      testInfo,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
      "06-politica-de-privacidad.png"
    );
    legalUrls["Política de Privacidad"] = privacyUrl;
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    results: report,
    legalUrls,
    errors
  };

  const reportPath = path.join(testInfo.outputDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("final-report.json", {
    path: reportPath,
    contentType: "application/json"
  });

  expect(errors, `Workflow failures:\n${errors.join("\n")}`).toEqual([]);
});
