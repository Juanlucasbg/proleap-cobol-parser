import { writeFile } from "node:fs/promises";
import { expect, test, type BrowserContext, type Locator, type Page, type TestInfo } from "@playwright/test";

const reportFields = [
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

type ReportField = (typeof reportFields)[number];
type Report = Record<ReportField, "PASS" | "FAIL">;

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {
    // Some environments keep long-polling requests open.
  });
  await page.waitForTimeout(350);
}

async function screenshotCheckpoint(page: Page, testInfo: TestInfo, name: string, fullPage = true): Promise<void> {
  const path = testInfo.outputPath(`${slugify(name)}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function firstVisible(candidates: Locator[], timeoutMs = 20000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const current = candidate.first();
      if (await current.isVisible().catch(() => false)) {
        return current;
      }
    }
    await candidates[0].page().waitForTimeout(250);
  }
  throw new Error("No visible locator matched any candidate.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 15000 });
  await locator.click();
  await waitForUi(page);
}

async function withReport(
  field: ReportField,
  report: Report,
  failures: string[],
  action: () => Promise<void>,
): Promise<void> {
  try {
    await action();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    failures.push(`${field}: ${errorMessage(error)}`);
  }
}

async function ensureLoginPage(page: Page): Promise<void> {
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;

  if (page.url() === "about:blank") {
    if (!loginUrl) {
      throw new Error(
        "Open the login page before running this test, or set SALEADS_LOGIN_URL / SALEADS_BASE_URL.",
      );
    }
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);
}

async function openAndValidateLegalLink(
  page: Page,
  context: BrowserContext,
  testInfo: TestInfo,
  linkText: RegExp,
  headingText: RegExp,
  checkpointName: string,
): Promise<string> {
  const link = await firstVisible(
    [page.getByRole("link", { name: linkText }), page.getByRole("button", { name: linkText }), page.getByText(linkText)],
    20000,
  );

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickAndWait(page, link);
  const popup = await popupPromise;
  const legalPage = popup ?? page;

  await waitForUi(legalPage);
  await expect(
    await firstVisible([legalPage.getByRole("heading", { name: headingText }), legalPage.getByText(headingText)], 20000),
  ).toBeVisible();
  await expect(legalPage.locator("body")).toContainText(
    /(t[eé]rminos|condiciones|privacidad|informaci[oó]n|datos|uso)/i,
    { timeout: 20000 },
  );

  await screenshotCheckpoint(legalPage, testInfo, checkpointName);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report: Report = Object.fromEntries(reportFields.map((field) => [field, "FAIL"])) as Report;
  const failures: string[] = [];
  const evidence: Record<string, string> = {};

  await withReport("Login", report, failures, async () => {
    await ensureLoginPage(page);

    const loginButton = await firstVisible(
      [
        page.getByRole("button", {
          name: /(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)/i,
        }),
        page.getByRole("link", {
          name: /(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)/i,
        }),
        page.getByText(/(sign in with google|iniciar sesi[oó]n con google|continuar con google)/i),
      ],
      25000,
    );

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;
    const authPage = popup ?? page;

    const googleAccount = authPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
    if (await googleAccount.isVisible().catch(() => false)) {
      await googleAccount.click();
      await waitForUi(authPage);
    }

    if (popup) {
      await page.bringToFront();
      await waitForUi(page);
    }

    await expect(
      await firstVisible([page.locator("aside"), page.getByRole("navigation"), page.locator('[class*="sidebar"]')], 30000),
    ).toBeVisible();
    await expect(page.locator("main")).toBeVisible({ timeout: 30000 });

    await screenshotCheckpoint(page, testInfo, "dashboard-loaded");
  });

  await withReport("Mi Negocio menu", report, failures, async () => {
    const sidebar = await firstVisible(
      [page.locator("aside"), page.getByRole("navigation"), page.locator('[class*="sidebar"]')],
      20000,
    );
    await expect(sidebar).toBeVisible();

    const miNegocio = await firstVisible(
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ],
      20000,
    );
    await clickAndWait(page, miNegocio);

    await expect(await firstVisible([page.getByText(/agregar negocio/i), page.getByRole("link", { name: /agregar negocio/i })])).toBeVisible();
    await expect(
      await firstVisible([
        page.getByText(/administrar negocios/i),
        page.getByRole("link", { name: /administrar negocios/i }),
      ]),
    ).toBeVisible();

    await screenshotCheckpoint(page, testInfo, "mi-negocio-menu-expanded", false);
  });

  await withReport("Agregar Negocio modal", report, failures, async () => {
    const agregarNegocio = await firstVisible(
      [page.getByRole("link", { name: /agregar negocio/i }), page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      20000,
    );
    await clickAndWait(page, agregarNegocio);

    await expect(await firstVisible([page.getByRole("heading", { name: /crear nuevo negocio/i }), page.getByText(/crear nuevo negocio/i)])).toBeVisible();

    const nombreInput = await firstVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator('input[name*="nombre"], input[placeholder*="Nombre"]'),
      ],
      20000,
    );
    await expect(nombreInput).toBeVisible();

    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 20000 });
    const cancelar = await firstVisible([page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)]);
    await expect(cancelar).toBeVisible();
    await expect(
      await firstVisible([page.getByRole("button", { name: /crear negocio/i }), page.getByText(/crear negocio/i)]),
    ).toBeVisible();

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatizacion");
    await screenshotCheckpoint(page, testInfo, "agregar-negocio-modal");

    await clickAndWait(page, cancelar);
  });

  await withReport("Administrar Negocios view", report, failures, async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocio = await firstVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        20000,
      );
      await clickAndWait(page, miNegocio);
    }

    const administrar = await firstVisible(
      [
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ],
      20000,
    );
    await clickAndWait(page, administrar);

    await expect(await firstVisible([page.getByRole("heading", { name: /informaci[oó]n general/i }), page.getByText(/informaci[oó]n general/i)])).toBeVisible();
    await expect(
      await firstVisible([page.getByRole("heading", { name: /detalles de la cuenta/i }), page.getByText(/detalles de la cuenta/i)]),
    ).toBeVisible();
    await expect(await firstVisible([page.getByRole("heading", { name: /tus negocios/i }), page.getByText(/tus negocios/i)])).toBeVisible();
    await expect(
      await firstVisible([page.getByRole("heading", { name: /secci[oó]n legal/i }), page.getByText(/secci[oó]n legal/i)]),
    ).toBeVisible();

    await screenshotCheckpoint(page, testInfo, "administrar-negocios-page");
  });

  await withReport("Información General", report, failures, async () => {
    await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 20000 });
    await expect(await firstVisible([page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)])).toBeVisible();
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/nombre/i).first()).toBeVisible({ timeout: 20000 });
  });

  await withReport("Detalles de la Cuenta", report, failures, async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/estado activo/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible({ timeout: 20000 });
  });

  await withReport("Tus Negocios", report, failures, async () => {
    await expect(await firstVisible([page.getByRole("heading", { name: /tus negocios/i }), page.getByText(/tus negocios/i)])).toBeVisible();
    await expect(
      await firstVisible([
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ]),
    ).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 20000 });
  });

  await withReport("Términos y Condiciones", report, failures, async () => {
    evidence.terminosUrl = await openAndValidateLegalLink(
      page,
      context,
      testInfo,
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "terminos-y-condiciones",
    );
  });

  await withReport("Política de Privacidad", report, failures, async () => {
    evidence.politicaPrivacidadUrl = await openAndValidateLegalLink(
      page,
      context,
      testInfo,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "politica-de-privacidad",
    );
  });

  const finalReport = {
    ...report,
    evidencia: evidence,
    failedChecks: failures,
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf-8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

  console.log("Final validation report:\n", JSON.stringify(finalReport, null, 2));
  expect(failures, `One or more validations failed.\n${failures.join("\n")}`).toEqual([]);
});
