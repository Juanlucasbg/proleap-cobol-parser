import { expect, Locator, Page, test } from "@playwright/test";
import { promises as fs } from "fs";

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

function buildStatus(): Record<ReportField, StepStatus> {
  return REPORT_FIELDS.reduce(
    (acc, field) => ({ ...acc, [field]: "FAIL" }),
    {} as Record<ReportField, StepStatus>,
  );
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function firstVisibleLocator(
  candidates: Locator[],
  timeoutMs = 20_000,
): Promise<Locator> {
  const endTime = Date.now() + timeoutMs;

  while (Date.now() < endTime) {
    for (const candidate of candidates) {
      const first = candidate.first();
      const count = await first.count();

      if (count > 0 && (await first.isVisible().catch(() => false))) {
        return first;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 200));
  }

  throw new Error("No visible locator found within timeout.");
}

async function clickVisibleText(page: Page, text: string): Promise<void> {
  const regex = new RegExp(escapeRegExp(text), "i");
  const locator = await firstVisibleLocator([
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByText(regex),
  ]);

  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function maybeClickVisibleText(page: Page, text: string): Promise<boolean> {
  const regex = new RegExp(escapeRegExp(text), "i");
  const candidates = [
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByText(regex),
  ];

  for (const candidate of candidates) {
    const first = candidate.first();
    if ((await first.count()) > 0 && (await first.isVisible().catch(() => false))) {
      await first.click();
      await waitForUi(page);
      return true;
    }
  }

  return false;
}

async function ensureVisibleText(page: Page, text: string): Promise<void> {
  const regex = new RegExp(escapeRegExp(text), "i");
  await expect(
    await firstVisibleLocator([
      page.getByRole("heading", { name: regex }),
      page.getByRole("button", { name: regex }),
      page.getByRole("link", { name: regex }),
      page.getByText(regex),
    ]),
  ).toBeVisible();
}

async function chooseGoogleAccountIfShown(page: Page): Promise<void> {
  const accountEmail = "juanlucasbarbiergarzon@gmail.com";
  const accountLocator = page.getByText(accountEmail, { exact: false }).first();

  if ((await accountLocator.count()) > 0 && (await accountLocator.isVisible().catch(() => false))) {
    await accountLocator.click();
    await waitForUi(page);
  }
}

async function validateLegalPageAndReturnToApp(
  page: Page,
  linkText: string,
  headingText: string,
  screenshotName: string,
): Promise<string> {
  const context = page.context();
  const appUrlBeforeClick = page.url();
  const maybePopup = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);

  await clickVisibleText(page, linkText);

  const popup = await maybePopup;
  const legalPage = popup ?? page;

  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForTimeout(1000);
  await ensureVisibleText(legalPage, headingText);

  const legalContent = await firstVisibleLocator([
    legalPage.locator("article p"),
    legalPage.locator("main p"),
    legalPage.locator("section p"),
    legalPage.locator("p"),
  ]);
  await expect(legalContent).toBeVisible();

  await legalPage.screenshot({ path: screenshotName, fullPage: true });
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== appUrlBeforeClick) {
    await page
      .goBack({ waitUntil: "domcontentloaded" })
      .catch(async () => page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" }));
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const status = buildStatus();
  const issues: string[] = [];
  const legalUrls: Record<"terminos" | "privacidad", string> = {
    terminos: "",
    privacidad: "",
  };

  const startUrl =
    process.env.SALEADS_START_URL ?? process.env.SALEADS_URL ?? process.env.BASE_URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No login page available. Set SALEADS_START_URL/SALEADS_URL/BASE_URL for environment-agnostic execution.",
    );
  }

  const runStep = async (field: ReportField, action: () => Promise<void>) => {
    try {
      await action();
      status[field] = "PASS";
    } catch (error) {
      status[field] = "FAIL";
      issues.push(`${field}: ${(error as Error).message}`);
    }
  };

  await runStep("Login", async () => {
    const googleButton = await firstVisibleLocator([
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      }),
      page.getByText(
        /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      ),
    ]);

    const maybePopup = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await googleButton.click();
    await waitForUi(page);

    const popup = await maybePopup;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await chooseGoogleAccountIfShown(popup);
      await popup.waitForTimeout(1000);
    } else {
      await chooseGoogleAccountIfShown(page);
    }

    await ensureVisibleText(page, "Negocio");
    await expect(await firstVisibleLocator([page.locator("aside"), page.locator("nav")])).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true,
    });
  });

  await runStep("Mi Negocio menu", async () => {
    await maybeClickVisibleText(page, "Negocio");
    await clickVisibleText(page, "Mi Negocio");

    await ensureVisibleText(page, "Agregar Negocio");
    await ensureVisibleText(page, "Administrar Negocios");
    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-expanded-menu.png"),
      fullPage: true,
    });
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickVisibleText(page, "Agregar Negocio");
    await ensureVisibleText(page, "Crear Nuevo Negocio");
    await ensureVisibleText(page, "Nombre del Negocio");
    await ensureVisibleText(page, "Tienes 2 de 3 negocios");
    await ensureVisibleText(page, "Cancelar");
    await ensureVisibleText(page, "Crear Negocio");

    const businessNameInput = await firstVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator(
        "xpath=//label[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'nombre del negocio')]/following::input[1]",
      ),
      page.locator("input").first(),
    ]);
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true,
    });

    await clickVisibleText(page, "Cancelar");
  });

  await runStep("Administrar Negocios view", async () => {
    if (!(await maybeClickVisibleText(page, "Administrar Negocios"))) {
      await clickVisibleText(page, "Mi Negocio");
      await clickVisibleText(page, "Administrar Negocios");
    }

    await ensureVisibleText(page, "Información General");
    await ensureVisibleText(page, "Detalles de la Cuenta");
    await ensureVisibleText(page, "Tus Negocios");
    await ensureVisibleText(page, "Sección Legal");
    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-page.png"),
      fullPage: true,
    });
  });

  await runStep("Información General", async () => {
    await ensureVisibleText(page, "BUSINESS PLAN");
    await ensureVisibleText(page, "Cambiar Plan");
    await expect(page.locator("text=/@/").first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await ensureVisibleText(page, "Cuenta creada");
    await ensureVisibleText(page, "Estado activo");
    await ensureVisibleText(page, "Idioma seleccionado");
  });

  await runStep("Tus Negocios", async () => {
    await ensureVisibleText(page, "Tus Negocios");
    await ensureVisibleText(page, "Agregar Negocio");
    await ensureVisibleText(page, "Tienes 2 de 3 negocios");
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls.terminos = await validateLegalPageAndReturnToApp(
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      testInfo.outputPath("05-terminos-y-condiciones.png"),
    );
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls.privacidad = await validateLegalPageAndReturnToApp(
      page,
      "Política de Privacidad",
      "Política de Privacidad",
      testInfo.outputPath("06-politica-de-privacidad.png"),
    );
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    status,
    legalUrls,
    issues,
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  // Keep the test result strict while still producing the full report above.
  expect(
    REPORT_FIELDS.filter((field) => status[field] === "FAIL"),
    `Validation failed:\n${issues.join("\n")}`,
  ).toEqual([]);
});
