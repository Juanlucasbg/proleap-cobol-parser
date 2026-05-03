import { expect, Locator, Page, test } from "@playwright/test";
import { mkdir } from "node:fs/promises";

type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepResult = {
  pass: boolean;
  details: string[];
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function firstVisible(locators: Locator[], timeoutMs = 6_000): Promise<Locator | null> {
  const end = Date.now() + timeoutMs;

  while (Date.now() < end) {
    for (const locator of locators) {
      if (await locator.first().isVisible().catch(() => false)) {
        return locator.first();
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 200));
  }

  return null;
}

async function getClickableByText(page: Page, text: string): Promise<Locator | null> {
  const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const re = new RegExp(escaped, "i");

  return firstVisible([
    page.getByRole("button", { name: re }),
    page.getByRole("link", { name: re }),
    page.getByRole("menuitem", { name: re }),
    page.getByRole("tab", { name: re }),
    page.getByText(re),
  ]);
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const target = await getClickableByText(page, text);
  if (!target) {
    throw new Error(`Could not find clickable element with text: ${text}`);
  }

  await target.click();
  await waitForUiToSettle(page);
}

async function ensureVisibleText(page: Page, text: string, timeoutMs = 15_000): Promise<void> {
  const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const re = new RegExp(escaped, "i");

  const visible = await firstVisible(
    [
      page.getByRole("heading", { name: re }),
      page.getByRole("button", { name: re }),
      page.getByRole("link", { name: re }),
      page.getByText(re),
    ],
    timeoutMs,
  );

  if (!visible) {
    throw new Error(`Expected text not visible: ${text}`);
  }
}

function createReport(): Record<ReportKey, StepResult> {
  return {
    Login: { pass: false, details: [] },
    "Mi Negocio menu": { pass: false, details: [] },
    "Agregar Negocio modal": { pass: false, details: [] },
    "Administrar Negocios view": { pass: false, details: [] },
    "Información General": { pass: false, details: [] },
    "Detalles de la Cuenta": { pass: false, details: [] },
    "Tus Negocios": { pass: false, details: [] },
    "Términos y Condiciones": { pass: false, details: [] },
    "Política de Privacidad": { pass: false, details: [] },
  };
}

async function writeCheckpointScreenshot(page: Page, name: string, fullPage = false): Promise<void> {
  await mkdir("test-results/screenshots", { recursive: true });
  await page.screenshot({
    path: `test-results/screenshots/${name}.png`,
    fullPage,
  });
}

async function ensureLegalContentVisible(page: Page): Promise<void> {
  const legalContent = await firstVisible(
    [
      page.locator("main p"),
      page.locator("article p"),
      page.locator("section p"),
      page.getByText(/(uso|privacidad|datos|condiciones|t[eé]rminos)/i),
    ],
    15_000,
  );

  if (!legalContent) {
    throw new Error("Legal content text was not visible.");
  }
}

async function openLoginPageIfConfigured(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    return;
  }

  const configuredLoginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (!configuredLoginUrl) {
    throw new Error(
      "Browser is on about:blank. Open SaleADS login page first or set SALEADS_LOGIN_URL/SALEADS_BASE_URL.",
    );
  }

  await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const legalUrls: { terms: string; privacy: string } = { terms: "", privacy: "" };

  const setPass = (key: ReportKey, message: string) => {
    report[key].pass = true;
    report[key].details.push(message);
  };

  const setFail = (key: ReportKey, message: string) => {
    report[key].pass = false;
    report[key].details.push(message);
  };

  const runStep = async (key: ReportKey, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      if (!report[key].pass) {
        setPass(key, "All requested validations passed.");
      }
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      setFail(key, reason);
      await page.screenshot({
        path: `test-results/screenshots/${key.replace(/\s+/g, "_").toLowerCase()}_failed.png`,
        fullPage: true,
      });
    }
  };

  await openLoginPageIfConfigured(page);

  await runStep("Login", async () => {
    const sidebar = await firstVisible([
      page.getByRole("navigation"),
      page.locator("aside"),
      page.getByText(/Negocio/i),
    ]);

    if (!sidebar) {
      const loginButton = await firstVisible([
        page.getByRole("button", { name: /google|iniciar sesi[oó]n|sign in|login/i }),
        page.getByRole("link", { name: /google|iniciar sesi[oó]n|sign in|login/i }),
        page.getByText(/google/i),
      ]);

      if (!loginButton) {
        throw new Error("Could not find Google login button.");
      }

      const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await loginButton.click();
      await waitForUiToSettle(page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const accountOption = await firstVisible(
          [
            popup.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
            popup.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
          ],
          8_000,
        );

        if (accountOption) {
          await accountOption.click();
        }

        await popup.waitForTimeout(1_000).catch(() => undefined);
      } else {
        const accountOptionMain = await firstVisible(
          [
            page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
            page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
          ],
          5_000,
        );
        if (accountOptionMain) {
          await accountOptionMain.click();
        }
      }
    }

    await ensureVisibleText(page, "Negocio");
    const sidebarAfterLogin = await firstVisible([
      page.getByRole("navigation"),
      page.locator("aside"),
      page.getByText(/Negocio/i),
    ]);

    if (!sidebarAfterLogin) {
      throw new Error("Main app interface/left sidebar was not visible after login.");
    }

    await writeCheckpointScreenshot(page, "01_dashboard_loaded");
    setPass("Login", "Dashboard and sidebar were visible.");
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, "Negocio");
    await clickByVisibleText(page, "Mi Negocio");

    await ensureVisibleText(page, "Agregar Negocio");
    await ensureVisibleText(page, "Administrar Negocios");

    await writeCheckpointScreenshot(page, "02_mi_negocio_menu_expanded");
    setPass("Mi Negocio menu", "Mi Negocio submenu expanded with both options visible.");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, "Agregar Negocio");

    await ensureVisibleText(page, "Crear Nuevo Negocio");
    await ensureVisibleText(page, "Nombre del Negocio");
    await ensureVisibleText(page, "Tienes 2 de 3 negocios");
    await ensureVisibleText(page, "Cancelar");
    await ensureVisibleText(page, "Crear Negocio");

    await writeCheckpointScreenshot(page, "03_agregar_negocio_modal");

    const businessNameInput = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator('input[name*="negocio" i]'),
      page.locator("input").filter({ hasText: /^$/ }),
    ]);

    if (businessNameInput) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
    }

    await clickByVisibleText(page, "Cancelar");
    setPass("Agregar Negocio modal", "Modal content validated and closed through Cancelar.");
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarOption = await getClickableByText(page, "Administrar Negocios");
    if (!administrarOption) {
      await clickByVisibleText(page, "Mi Negocio");
    }

    await clickByVisibleText(page, "Administrar Negocios");

    await ensureVisibleText(page, "Información General");
    await ensureVisibleText(page, "Detalles de la Cuenta");
    await ensureVisibleText(page, "Tus Negocios");
    await ensureVisibleText(page, "Sección Legal");

    await writeCheckpointScreenshot(page, "04_administrar_negocios_view", true);
    setPass("Administrar Negocios view", "Account sections are visible.");
  });

  await runStep("Información General", async () => {
    const nameVisible = await firstVisible([
      page.getByText(/@/i).locator(".."),
      page.getByText(/BUSINESS PLAN/i),
      page.getByText(/Cambiar Plan/i),
    ]);

    if (!nameVisible) {
      throw new Error("Unable to confirm user identity block in Información General.");
    }

    await ensureVisibleText(page, "BUSINESS PLAN");
    await ensureVisibleText(page, "Cambiar Plan");
    await ensureVisibleText(page, "@");
    setPass("Información General", "User details, plan, and plan-change button are visible.");
  });

  await runStep("Detalles de la Cuenta", async () => {
    await ensureVisibleText(page, "Cuenta creada");
    await ensureVisibleText(page, "Estado activo");
    await ensureVisibleText(page, "Idioma seleccionado");
    setPass("Detalles de la Cuenta", "Detalles de la Cuenta fields are visible.");
  });

  await runStep("Tus Negocios", async () => {
    await ensureVisibleText(page, "Tus Negocios");
    await ensureVisibleText(page, "Agregar Negocio");
    await ensureVisibleText(page, "Tienes 2 de 3 negocios");
    setPass("Tus Negocios", "Business list and capacity text are visible.");
  });

  await runStep("Términos y Condiciones", async () => {
    const termsLink = await getClickableByText(page, "Términos y Condiciones");
    if (!termsLink) {
      throw new Error("Could not find Términos y Condiciones link.");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 6_000 }).catch(() => null);
    const previousUrl = page.url();
    await termsLink.click();

    const popup = await popupPromise;
    const legalPage = popup ?? page;

    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUiToSettle(legalPage);

    await ensureVisibleText(legalPage, "Términos y Condiciones");
    await ensureLegalContentVisible(legalPage);
    legalUrls.terms = legalPage.url();
    await writeCheckpointScreenshot(legalPage, "05_terminos_y_condiciones", true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else if (page.url() !== previousUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }

    setPass("Términos y Condiciones", `Legal page validated at URL: ${legalUrls.terms}`);
  });

  await runStep("Política de Privacidad", async () => {
    const privacyLink = await getClickableByText(page, "Política de Privacidad");
    if (!privacyLink) {
      throw new Error("Could not find Política de Privacidad link.");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 6_000 }).catch(() => null);
    const previousUrl = page.url();
    await privacyLink.click();

    const popup = await popupPromise;
    const legalPage = popup ?? page;

    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUiToSettle(legalPage);

    await ensureVisibleText(legalPage, "Política de Privacidad");
    await ensureLegalContentVisible(legalPage);
    legalUrls.privacy = legalPage.url();
    await writeCheckpointScreenshot(legalPage, "06_politica_de_privacidad", true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else if (page.url() !== previousUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }

    setPass("Política de Privacidad", `Legal page validated at URL: ${legalUrls.privacy}`);
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    environmentUrl: page.url(),
    legalUrls,
    results: report,
  };

  await testInfo.attach("saleads-mi-negocio-final-report.json", {
    contentType: "application/json",
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
  });

  console.log("==== SaleADS Mi Negocio Final Report ====");
  for (const [key, value] of Object.entries(report)) {
    const status = value.pass ? "PASS" : "FAIL";
    console.log(`${key}: ${status}`);
    for (const detail of value.details) {
      console.log(`  - ${detail}`);
    }
  }
  console.log(`Términos y Condiciones URL: ${legalUrls.terms || "N/A"}`);
  console.log(`Política de Privacidad URL: ${legalUrls.privacy || "N/A"}`);

  const failedSteps = Object.entries(report).filter(([, result]) => !result.pass);
  expect(
    failedSteps,
    `Failed validations: ${failedSteps.map(([name]) => name).join(", ") || "none"}`,
  ).toHaveLength(0);
});
