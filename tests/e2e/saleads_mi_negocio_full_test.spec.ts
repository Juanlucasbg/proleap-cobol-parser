import fs from "node:fs/promises";
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
type Result = "PASS" | "FAIL";

type FinalReport = {
  name: string;
  results: Record<ReportField, Result>;
  urls: {
    termsAndConditions: string | null;
    privacyPolicy: string | null;
  };
  errors: string[];
};

async function firstVisible(locator: Locator): Promise<Locator | null> {
  const count = await locator.count();
  for (let i = 0; i < count; i += 1) {
    const item = locator.nth(i);
    if (await item.isVisible().catch(() => false)) {
      return item;
    }
  }
  return null;
}

async function clickVisibleByText(page: Page, pattern: RegExp): Promise<void> {
  const candidates = [
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByText(pattern),
  ];

  for (const candidate of candidates) {
    const visible = await firstVisible(candidate);
    if (visible) {
      await visible.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`No visible clickable element found for ${pattern}`);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(750);
}

async function clickLinkAndGetDestination(page: Page, link: Locator): Promise<{ destination: Page; openedNewTab: boolean }> {
  const maybeNewTab = page.context().waitForEvent("page", { timeout: 5_000 }).catch(() => null);
  await link.click();
  await waitForUi(page);

  const newTab = await maybeNewTab;
  if (newTab) {
    await newTab.waitForLoadState("domcontentloaded");
    await newTab.waitForTimeout(750);
    return { destination: newTab, openedNewTab: true };
  }

  return { destination: page, openedNewTab: false };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])) as Record<ReportField, Result>;
  const errors: string[] = [];
  const urls = {
    termsAndConditions: null as string | null,
    privacyPolicy: null as string | null,
  };

  const capture = async (name: string, targetPage: Page = page, fullPage = true) => {
    await targetPage.screenshot({
      path: testInfo.outputPath(name),
      fullPage,
    });
  };

  const runStep = async (field: ReportField, callback: () => Promise<void>) => {
    try {
      await callback();
      results[field] = "PASS";
    } catch (error) {
      results[field] = "FAIL";
      errors.push(`${field}: ${String(error)}`);
    }
  };

  const startUrl = process.env.SALEADS_URL ?? process.env.BASE_URL;
  if (page.url() === "about:blank") {
    expect(startUrl, "Set SALEADS_URL (or BASE_URL) to the SaleADS login page for the current environment.").toBeTruthy();
    await page.goto(startUrl as string, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await runStep("Login", async () => {
    const loginButton = await firstVisible(
      page
        .getByRole("button", {
          name: /(sign in with google|iniciar sesi[oó]n con google|google)/i,
        })
        .or(page.getByRole("link", { name: /(sign in with google|iniciar sesi[oó]n con google|google)/i }))
        .or(page.getByText(/(sign in with google|iniciar sesi[oó]n con google|google)/i)),
    );
    if (!loginButton) {
      throw new Error("Could not find a visible Google login entry point.");
    }

    const popupWaiter = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popupPage = await popupWaiter;
    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      const accountInPopup = popupPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
      const popupAccount = await firstVisible(accountInPopup);
      if (popupAccount) {
        await popupAccount.click();
      }
      await popupPage.waitForTimeout(1_000);
    } else {
      const accountInPage = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
      const inlineAccount = await firstVisible(accountInPage);
      if (inlineAccount) {
        await inlineAccount.click();
      }
    }

    await page.waitForTimeout(2_000);
    await expect(page.getByText(/(Negocio|Mi Negocio|Dashboard|Inicio)/i).first()).toBeVisible();
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await capture("01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    await clickVisibleByText(page, /^Negocio$/i);
    await clickVisibleByText(page, /^Mi Negocio$/i);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await capture("02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickVisibleByText(page, /^Agregar Negocio$/i);
    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Nombre del Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Tienes 2 de 3 negocios$/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    await capture("03-agregar-negocio-modal.png");

    const businessNameField =
      (await firstVisible(page.getByLabel(/^Nombre del Negocio$/i))) ??
      (await firstVisible(page.getByPlaceholder(/^Nombre del Negocio$/i))) ??
      (await firstVisible(page.locator('input[name*="negocio" i]'))) ??
      (await firstVisible(page.locator("input[type='text']")));

    if (!businessNameField) {
      throw new Error("Could not locate 'Nombre del Negocio' input field.");
    }

    await businessNameField.fill("Negocio Prueba Automatización");
    await page.getByRole("button", { name: /^Cancelar$/i }).click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    await clickVisibleByText(page, /^Mi Negocio$/i);
    await clickVisibleByText(page, /^Administrar Negocios$/i);
    await expect(page.getByText(/^Información General$/i)).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
    await expect(page.getByText(/(Sección Legal|Seccion Legal)/i)).toBeVisible();
    await capture("04-administrar-negocios-full-page.png", page, true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.getByText(/^[A-Za-zÁÉÍÓÚÑáéíóúñ][A-Za-zÁÉÍÓÚÑáéíóúñ ]+$/).first()).toBeVisible();
    await expect(page.getByText(/\bBUSINESS PLAN\b/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cambiar Plan$/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/^Cuenta creada$/i)).toBeVisible();
    await expect(page.getByText(/^Estado activo$/i)).toBeVisible();
    await expect(page.getByText(/^Idioma seleccionado$/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(/^Tienes 2 de 3 negocios$/i)).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    const termsLink = page.getByRole("link", { name: /^T[eé]rminos y Condiciones$/i }).first();
    await expect(termsLink).toBeVisible();

    const { destination, openedNewTab } = await clickLinkAndGetDestination(page, termsLink);
    urls.termsAndConditions = destination.url();

    await expect(destination.getByText(/^T[eé]rminos y Condiciones$/i)).toBeVisible();
    await expect(destination.locator("main, article, section, p").first()).toBeVisible();
    await capture("05-terminos-y-condiciones.png", destination);

    if (openedNewTab) {
      await destination.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  });

  await runStep("Política de Privacidad", async () => {
    const privacyLink = page.getByRole("link", { name: /^Pol[ií]tica de Privacidad$/i }).first();
    await expect(privacyLink).toBeVisible();

    const { destination, openedNewTab } = await clickLinkAndGetDestination(page, privacyLink);
    urls.privacyPolicy = destination.url();

    await expect(destination.getByText(/^Pol[ií]tica de Privacidad$/i)).toBeVisible();
    await expect(destination.locator("main, article, section, p").first()).toBeVisible();
    await capture("06-politica-de-privacidad.png", destination);

    if (openedNewTab) {
      await destination.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  });

  const finalReport: FinalReport = {
    name: "saleads_mi_negocio_full_test",
    results,
    urls,
    errors,
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log("Final report:", JSON.stringify(finalReport, null, 2));
  expect.soft(errors, "Validation failures detected").toEqual([]);
});
