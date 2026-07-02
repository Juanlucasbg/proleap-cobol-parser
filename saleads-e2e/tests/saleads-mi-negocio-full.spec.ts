import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type FinalReport = {
  name: string;
  generatedAt: string;
  environment: {
    baseUrl: string;
    accountEmail: string;
  };
  results: Record<string, StepStatus>;
  legalUrls: {
    terminosYCondiciones: string;
    politicaDePrivacidad: string;
  };
  screenshots: Record<string, string>;
  errors: string[];
};

const DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

function toPattern(text: string): RegExp {
  return new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => undefined);
}

async function firstVisible(page: Page, labels: string[]): Promise<Locator> {
  for (const label of labels) {
    const pattern = toPattern(label);
    const candidates: Locator[] = [
      page.getByRole("button", { name: pattern }),
      page.getByRole("link", { name: pattern }),
      page.getByRole("menuitem", { name: pattern }),
      page.getByRole("tab", { name: pattern }),
      page.getByText(pattern),
    ];

    for (const candidate of candidates) {
      const match = candidate.first();
      try {
        await match.waitFor({ state: "visible", timeout: 2500 });
        return match;
      } catch {
        // Continue scanning candidates until one is visibly interactable.
      }
    }
  }

  throw new Error(`No se encontró un elemento visible con alguno de estos textos: ${labels.join(", ")}`);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUiToLoad(page);
}

async function saveCheckpoint(
  page: Page,
  testInfo: TestInfo,
  screenshots: Record<string, string>,
  key: string,
  fullPage = false,
): Promise<void> {
  const screenshotPath = testInfo.outputPath(`${key}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(key, { path: screenshotPath, contentType: "image/png" });
  screenshots[key] = screenshotPath;
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
  test.setTimeout(300000);

  const baseUrl = process.env.SALEADS_BASE_URL ?? process.env.SALEADS_LOGIN_URL ?? "";

  const accountEmail = process.env.SALEADS_GOOGLE_ACCOUNT ?? DEFAULT_ACCOUNT_EMAIL;
  const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])) as Record<string, StepStatus>;
  const errors: string[] = [];
  const screenshots: Record<string, string> = {};
  let terminosUrl = "";
  let privacidadUrl = "";

  const runStep = async (name: (typeof REPORT_FIELDS)[number], callback: () => Promise<void>): Promise<void> => {
    try {
      await callback();
      results[name] = "PASS";
    } catch (error) {
      results[name] = "FAIL";
      errors.push(`${name}: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  if (!baseUrl) {
    errors.push(
      "Precondición no cumplida: define SALEADS_BASE_URL o SALEADS_LOGIN_URL para ejecutar el flujo en el entorno activo (dev/staging/production).",
    );
  } else {
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToLoad(page);

    await runStep("Login", async () => {
    const signInButton = await firstVisible(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google",
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, signInButton);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountOption = popup.getByText(toPattern(accountEmail)).first();
      if (await accountOption.isVisible({ timeout: 10000 }).catch(() => false)) {
        await accountOption.click();
        await waitForUiToLoad(popup);
      }

      await popup.waitForEvent("close", { timeout: 90000 }).catch(() => undefined);
      await page.bringToFront();
    } else {
      const inlineAccountOption = page.getByText(toPattern(accountEmail)).first();
      if (await inlineAccountOption.isVisible({ timeout: 8000 }).catch(() => false)) {
        await clickAndWait(page, inlineAccountOption);
      }
    }

    await waitForUiToLoad(page);
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 90000 });
    await saveCheckpoint(page, testInfo, screenshots, "01_dashboard_loaded");
  });

    await runStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisible(page, ["Negocio"]);
    await clickAndWait(page, negocioSection);

    const miNegocio = await firstVisible(page, ["Mi Negocio"]);
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(toPattern("Agregar Negocio")).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(toPattern("Administrar Negocios")).first()).toBeVisible({ timeout: 20000 });
    await saveCheckpoint(page, testInfo, screenshots, "02_mi_negocio_menu_expanded");
  });

    await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible(page, ["Agregar Negocio"]);
    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(toPattern("Crear Nuevo Negocio")).first()).toBeVisible({ timeout: 20000 });
    const nombreNegocio = page.getByLabel(toPattern("Nombre del Negocio")).first();
    await expect(nombreNegocio).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(toPattern("Tienes 2 de 3 negocios")).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: toPattern("Cancelar") }).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: toPattern("Crear Negocio") }).first()).toBeVisible({ timeout: 15000 });

    await saveCheckpoint(page, testInfo, screenshots, "03_agregar_negocio_modal");

    await nombreNegocio.click();
    await nombreNegocio.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: toPattern("Cancelar") }).first());
  });

    await runStep("Administrar Negocios view", async () => {
    const miNegocio = await firstVisible(page, ["Mi Negocio"]);
    await clickAndWait(page, miNegocio);

    const administrarNegocios = await firstVisible(page, ["Administrar Negocios"]);
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(toPattern("Información General")).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(toPattern("Detalles de la Cuenta")).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(toPattern("Tus Negocios")).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(toPattern("Sección Legal")).first()).toBeVisible({ timeout: 20000 });
    await saveCheckpoint(page, testInfo, screenshots, "04_administrar_negocios_page", true);
  });

    await runStep("Información General", async () => {
    const infoSection = page.locator("section, div").filter({ hasText: toPattern("Información General") }).first();
    await expect(infoSection).toBeVisible({ timeout: 20000 });
    await expect(infoSection.getByText(/@/).first()).toBeVisible({ timeout: 15000 });
    await expect(infoSection.getByText(toPattern("BUSINESS PLAN")).first()).toBeVisible({ timeout: 15000 });
    await expect(infoSection.getByRole("button", { name: toPattern("Cambiar Plan") }).first()).toBeVisible({
      timeout: 15000,
    });
  });

    await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = page.locator("section, div").filter({ hasText: toPattern("Detalles de la Cuenta") }).first();
    await expect(detailsSection).toBeVisible({ timeout: 20000 });
    await expect(detailsSection.getByText(toPattern("Cuenta creada")).first()).toBeVisible({ timeout: 15000 });
    await expect(detailsSection.getByText(toPattern("Estado activo")).first()).toBeVisible({ timeout: 15000 });
    await expect(detailsSection.getByText(toPattern("Idioma seleccionado")).first()).toBeVisible({ timeout: 15000 });
  });

    await runStep("Tus Negocios", async () => {
    const negociosSection = page.locator("section, div").filter({ hasText: toPattern("Tus Negocios") }).first();
    await expect(negociosSection).toBeVisible({ timeout: 20000 });
    await expect(negociosSection.getByText(toPattern("Tienes 2 de 3 negocios")).first()).toBeVisible({ timeout: 15000 });
    await expect(negociosSection.getByRole("button", { name: toPattern("Agregar Negocio") }).first()).toBeVisible({
      timeout: 15000,
    });
  });

    await runStep("Términos y Condiciones", async () => {
    const termsLink = await firstVisible(page, ["Términos y Condiciones"]);
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, termsLink);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await waitForUiToLoad(legalPage);

    const heading = legalPage
      .getByRole("heading", { name: toPattern("Términos y Condiciones") })
      .first();
    if (await heading.isVisible({ timeout: 10000 }).catch(() => false)) {
      await expect(heading).toBeVisible({ timeout: 15000 });
    } else {
      await expect(legalPage.getByText(toPattern("Términos y Condiciones")).first()).toBeVisible({ timeout: 15000 });
    }

    await expect(legalPage.locator("p, li, article, section").filter({ hasText: /\S{15,}/ }).first()).toBeVisible({
      timeout: 15000,
    });

    terminosUrl = legalPage.url();
    await saveCheckpoint(legalPage, testInfo, screenshots, "05_terminos_y_condiciones", true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUiToLoad(page);
    }
  });

    await runStep("Política de Privacidad", async () => {
    const privacyLink = await firstVisible(page, ["Política de Privacidad"]);
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, privacyLink);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await waitForUiToLoad(legalPage);

    const heading = legalPage.getByRole("heading", { name: toPattern("Política de Privacidad") }).first();
    if (await heading.isVisible({ timeout: 10000 }).catch(() => false)) {
      await expect(heading).toBeVisible({ timeout: 15000 });
    } else {
      await expect(legalPage.getByText(toPattern("Política de Privacidad")).first()).toBeVisible({ timeout: 15000 });
    }

    await expect(legalPage.locator("p, li, article, section").filter({ hasText: /\S{15,}/ }).first()).toBeVisible({
      timeout: 15000,
    });

    privacidadUrl = legalPage.url();
    await saveCheckpoint(legalPage, testInfo, screenshots, "06_politica_de_privacidad", true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUiToLoad(page);
    }
    });
  }

  const report: FinalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      baseUrl,
      accountEmail,
    },
    results,
    legalUrls: {
      terminosYCondiciones: terminosUrl,
      politicaDePrivacidad: privacidadUrl,
    },
    screenshots,
    errors,
  };

  const reportDir = testInfo.outputPath("reports");
  await mkdir(reportDir, { recursive: true });
  const reportPath = path.join(reportDir, "saleads-mi-negocio-final-report.json");
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedSteps = Object.entries(results)
    .filter(([, value]) => value === "FAIL")
    .map(([field]) => field);

  expect(
    failedSteps,
    `Se detectaron fallas en: ${failedSteps.join(", ")}. Revisa el reporte adjunto para detalles y evidencias.`,
  ).toEqual([]);
});
