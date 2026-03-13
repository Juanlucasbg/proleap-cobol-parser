import { expect, type Locator, type Page, test } from "@playwright/test";
import { promises as fs } from "node:fs";

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

interface ReportEntry {
  status: ReportStatus;
  notes: string[];
}

const REPORT_FIELDS: ReportField[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

function createReport(): Record<ReportField, ReportEntry> {
  return REPORT_FIELDS.reduce(
    (acc, field) => {
      acc[field] = { status: "FAIL", notes: [] };
      return acc;
    },
    {} as Record<ReportField, ReportEntry>
  );
}

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(400);
}

async function firstVisible(page: Page, candidates: Locator[], timeoutMs = 20_000): Promise<Locator> {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const candidate of candidates) {
      const first = candidate.first();
      const visible = await first.isVisible().catch(() => false);
      if (visible) {
        return first;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error("Could not find a visible matching element within timeout.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function clickAndCaptureTargetPage(page: Page, locator: Locator): Promise<{ targetPage: Page; openedNewTab: boolean }> {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await locator.click();
  await waitForUi(page);

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 });
    await popup.bringToFront();
    await waitForUi(popup);
    return { targetPage: popup, openedNewTab: true };
  }

  return { targetPage: page, openedNewTab: false };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const googleAccountEmail = process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
  const finalUrls: Record<string, string> = {};

  const runField = async (field: ReportField, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      report[field].status = "PASS";
    } catch (error) {
      report[field].status = "FAIL";
      report[field].notes.push(toErrorMessage(error));
    }
  };

  await runField("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error("Set SALEADS_LOGIN_URL or provide a pre-navigated page on the SaleADS login screen.");
    }

    const loginButton = await firstVisible(page, [
      page.getByRole("button", { name: /sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google/i }),
      page.getByText(/sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google/i),
      page.getByRole("button", { name: /login|log in|iniciar sesion|iniciar sesión/i })
    ]);

    const postClick = await clickAndCaptureTargetPage(page, loginButton);

    const accountSelector = postClick.targetPage.getByText(googleAccountEmail, { exact: false }).first();
    const accountVisible = await accountSelector.isVisible().catch(() => false);
    if (accountVisible) {
      await accountSelector.click();
      await waitForUi(postClick.targetPage);
    }

    if (postClick.openedNewTab) {
      await postClick.targetPage.waitForEvent("close", { timeout: 60_000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await waitForUi(page);

    const sidebar = await firstVisible(page, [
      page.locator("aside"),
      page.locator("nav"),
      page.getByText(/negocio|mi negocio/i)
    ]);
    await expect(sidebar).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true
    });
  });

  await runField("Mi Negocio menu", async () => {
    const negocioTrigger = await firstVisible(page, [
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ]);
    await clickAndWait(page, negocioTrigger);

    const miNegocio = await firstVisible(page, [
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"),
      fullPage: true
    });
  });

  await runField("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();

    const businessNameInput = await firstVisible(page, [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: /Nombre del Negocio/i })
    ]);
    await expect(businessNameInput).toBeVisible();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true
    });

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }));
  });

  await runField("Administrar Negocios view", async () => {
    const miNegocio = await firstVisible(page, [
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);

    const adminVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
    if (!adminVisible) {
      await clickAndWait(page, miNegocio);
    }

    const administrarNegocios = await firstVisible(page, [
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-account-page.png"),
      fullPage: true
    });
  });

  await runField("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const emailLikeText = await firstVisible(page, [page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)]);
    await expect(emailLikeText).toBeVisible();

    const userNameLikeText = await firstVisible(page, [
      page.locator("h1, h2, h3, p, span, strong").filter({
        hasNotText: /@|BUSINESS PLAN|Cambiar Plan|Información General/i
      })
    ]);
    await expect(userNameLikeText).toBeVisible();
  });

  await runField("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runField("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runField("Términos y Condiciones", async () => {
    const termsLink = await firstVisible(page, [
      page.getByRole("link", { name: /Términos y Condiciones/i }),
      page.getByText(/Términos y Condiciones/i)
    ]);

    const termsPageResult = await clickAndCaptureTargetPage(page, termsLink);
    const termsPage = termsPageResult.targetPage;

    const termsHeading = await firstVisible(termsPage, [
      termsPage.getByRole("heading", { name: /Términos y Condiciones/i }),
      termsPage.getByText(/Términos y Condiciones/i)
    ]);
    await expect(termsHeading).toBeVisible();

    const termsTextLength = (await termsPage.locator("body").innerText()).trim().length;
    expect(termsTextLength).toBeGreaterThan(200);

    finalUrls["Términos y Condiciones"] = termsPage.url();

    await termsPage.screenshot({
      path: testInfo.outputPath("05-terminos-y-condiciones.png"),
      fullPage: true
    });

    if (termsPageResult.openedNewTab) {
      await termsPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(page);
    }
  });

  await runField("Política de Privacidad", async () => {
    const privacyLink = await firstVisible(page, [
      page.getByRole("link", { name: /Política de Privacidad/i }),
      page.getByText(/Política de Privacidad/i)
    ]);

    const privacyPageResult = await clickAndCaptureTargetPage(page, privacyLink);
    const privacyPage = privacyPageResult.targetPage;

    const privacyHeading = await firstVisible(privacyPage, [
      privacyPage.getByRole("heading", { name: /Política de Privacidad/i }),
      privacyPage.getByText(/Política de Privacidad/i)
    ]);
    await expect(privacyHeading).toBeVisible();

    const privacyTextLength = (await privacyPage.locator("body").innerText()).trim().length;
    expect(privacyTextLength).toBeGreaterThan(200);

    finalUrls["Política de Privacidad"] = privacyPage.url();

    await privacyPage.screenshot({
      path: testInfo.outputPath("06-politica-de-privacidad.png"),
      fullPage: true
    });

    if (privacyPageResult.openedNewTab) {
      await privacyPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(page);
    }
  });

  for (const [label, url] of Object.entries(finalUrls)) {
    report[label as ReportField]?.notes.push(`Final URL: ${url}`);
  }

  const finalReportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(
    finalReportPath,
    JSON.stringify(
      {
        test: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        report,
        urls: finalUrls
      },
      null,
      2
    ),
    "utf8"
  );

  await testInfo.attach("final-report", {
    path: finalReportPath,
    contentType: "application/json"
  });

  const failed = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failed,
    `Final report contains failures: ${failed.map((field) => `${field}: ${report[field].notes.join(" | ")}`).join("; ")}`
  ).toEqual([]);
});
