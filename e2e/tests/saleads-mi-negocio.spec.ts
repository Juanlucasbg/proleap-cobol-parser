import { expect, Page, test } from "@playwright/test";

type StepName =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepResult = "PASS" | "FAIL";

const REPORT_ORDER: StepName[] = [
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const NEW_BUSINESS_NAME = "Negocio Prueba Automatización";

function normalize(text: string | null | undefined): string {
  return (text ?? "").replace(/\s+/g, " ").trim();
}

function escapeRegex(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function textMatcher(text: string): RegExp {
  return new RegExp(escapeRegex(text), "i");
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await page.waitForTimeout(750);
}

async function checkpointScreenshot(page: Page, name: string): Promise<void> {
  const safeName = name.replace(/[^a-zA-Z0-9_-]+/g, "-").toLowerCase();
  await test.info().attach(`${safeName}.png`, {
    body: await page.screenshot({ fullPage: true }),
    contentType: "image/png",
  });
}

async function findByVisibleText(page: Page, text: string) {
  const matcher = textMatcher(text);
  const candidates = [
    page.getByRole("button", { name: matcher }),
    page.getByRole("link", { name: matcher }),
    page.getByRole("menuitem", { name: matcher }),
    page.getByText(matcher, { exact: false }),
  ];

  for (const locator of candidates) {
    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return null;
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const target = await findByVisibleText(page, text);
  if (!target) {
    throw new Error(`No visible element found using text: ${text}`);
  }

  await target.click();
  await waitForUiLoad(page);
}

async function validateLegalLink(
  page: Page,
  linkText: "Términos y Condiciones" | "Política de Privacidad",
): Promise<string> {
  const matcher = textMatcher(linkText);
  const link = page.getByRole("link", { name: matcher }).first();
  await expect(link, `${linkText}: legal link should be visible`).toBeVisible();

  const pagePromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await link.click();

  const maybeNewTab = await pagePromise;
  const legalPage = maybeNewTab ?? page;

  if (maybeNewTab) {
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle").catch(() => undefined);
  } else {
    await waitForUiLoad(legalPage);
  }

  await expect(legalPage.getByRole("heading", { name: matcher }).first()).toBeVisible();
  await expect(legalPage.getByText(/\w+/, { exact: false }).first()).toBeVisible();

  await checkpointScreenshot(legalPage, linkText);
  const finalUrl = legalPage.url();
  test.info().annotations.push({ type: `${linkText} URL`, description: finalUrl });

  if (maybeNewTab) {
    await maybeNewTab.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const stepResults: Record<StepName, StepResult> = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };

  const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};
  const verificationErrors: string[] = [];

  const verify = async (stepName: StepName, fn: () => Promise<void>) => {
    try {
      await fn();
      stepResults[stepName] = "PASS";
    } catch (error) {
      stepResults[stepName] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      verificationErrors.push(`${stepName}: ${message}`);
    }
  };

  await test.step("Step 1: Login with Google", async () => {
    await verify("Login", async () => {
      await waitForUiLoad(page);

      const googleLoginTextOptions = [
        "Sign in with Google",
        "Continuar con Google",
        "Iniciar sesión con Google",
        "Google",
      ];

      let clickedLogin = false;
      for (const text of googleLoginTextOptions) {
        const candidate = await findByVisibleText(page, text);
        if (candidate) {
          await candidate.click();
          clickedLogin = true;
          break;
        }
      }

      if (!clickedLogin) {
        throw new Error("Could not find a Google login button by visible text.");
      }

      await waitForUiLoad(page);

      const accountOption = page.getByText(textMatcher(GOOGLE_ACCOUNT_EMAIL), { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitForUiLoad(page);
      }

      const sidebar = page
        .locator("aside, nav")
        .filter({
          has: page.getByText(/negocio|dashboard|mi negocio/i, { exact: false }).first(),
        })
        .first();
      await expect(sidebar, "Sidebar navigation should be visible after login").toBeVisible();

      await checkpointScreenshot(page, "dashboard-loaded");
    });
  });

  await test.step("Step 2: Open Mi Negocio menu", async () => {
    await verify("Mi Negocio menu", async () => {
      await clickByVisibleText(page, "Negocio");
      await clickByVisibleText(page, "Mi Negocio");

      await expect(page.getByText(textMatcher("Agregar Negocio"), { exact: false }).first()).toBeVisible();
      await expect(page.getByText(textMatcher("Administrar Negocios"), { exact: false }).first()).toBeVisible();

      await checkpointScreenshot(page, "mi-negocio-menu-expanded");
    });
  });

  await test.step("Step 3: Validate Agregar Negocio modal", async () => {
    await verify("Agregar Negocio modal", async () => {
      await clickByVisibleText(page, "Agregar Negocio");

      await expect(page.getByRole("heading", { name: textMatcher("Crear Nuevo Negocio") }).first()).toBeVisible();
      await expect(page.getByLabel(textMatcher("Nombre del Negocio")).first()).toBeVisible();
      await expect(page.getByText(textMatcher("Tienes 2 de 3 negocios"), { exact: false }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: textMatcher("Cancelar") }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: textMatcher("Crear Negocio") }).first()).toBeVisible();

      await checkpointScreenshot(page, "agregar-negocio-modal");

      const businessNameInput = page.getByLabel(textMatcher("Nombre del Negocio")).first();
      await businessNameInput.click();
      await businessNameInput.fill(NEW_BUSINESS_NAME);
      await clickByVisibleText(page, "Cancelar");
    });
  });

  await test.step("Step 4: Open Administrar Negocios", async () => {
    await verify("Administrar Negocios view", async () => {
      const administrarVisible = await page
        .getByText(textMatcher("Administrar Negocios"), { exact: false })
        .first()
        .isVisible()
        .catch(() => false);
      if (!administrarVisible) {
        await clickByVisibleText(page, "Mi Negocio");
      }

      await clickByVisibleText(page, "Administrar Negocios");
      await waitForUiLoad(page);

      await expect(page.getByText(textMatcher("Información General"), { exact: false }).first()).toBeVisible();
      await expect(page.getByText(textMatcher("Detalles de la Cuenta"), { exact: false }).first()).toBeVisible();
      await expect(page.getByText(textMatcher("Tus Negocios"), { exact: false }).first()).toBeVisible();
      await expect(page.getByText(textMatcher("Sección Legal"), { exact: false }).first()).toBeVisible();

      await checkpointScreenshot(page, "administrar-negocios-page");
    });
  });

  await test.step("Step 5: Validate Información General", async () => {
    await verify("Información General", async () => {
      const infoSection = page.locator("section, div").filter({
        has: page.getByText(textMatcher("Información General"), { exact: false }).first(),
      });

      await expect(infoSection.getByText(/@/, { exact: false }).first()).toBeVisible();
      await expect(
        infoSection.getByText(/business\s*plan/i, { exact: false }).first(),
        "BUSINESS PLAN text should be visible",
      ).toBeVisible();
      await expect(infoSection.getByRole("button", { name: textMatcher("Cambiar Plan") }).first()).toBeVisible();

      const sectionText = normalize(await infoSection.first().innerText());
      const nameCandidate = sectionText
        .split(" ")
        .find((token) => /^[A-Za-z][A-Za-zÀ-ÿ'.-]{1,}$/.test(token) && !token.includes("@"));
      if (!nameCandidate) {
        throw new Error("Could not infer a visible user name token in Información General.");
      }
    });
  });

  await test.step("Step 6: Validate Detalles de la Cuenta", async () => {
    await verify("Detalles de la Cuenta", async () => {
      const accountDetailsSection = page.locator("section, div").filter({
        has: page.getByText(textMatcher("Detalles de la Cuenta"), { exact: false }).first(),
      });

      await expect(accountDetailsSection.getByText(textMatcher("Cuenta creada"), { exact: false }).first()).toBeVisible();
      await expect(accountDetailsSection.getByText(textMatcher("Estado activo"), { exact: false }).first()).toBeVisible();
      await expect(
        accountDetailsSection.getByText(textMatcher("Idioma seleccionado"), { exact: false }).first(),
      ).toBeVisible();
    });
  });

  await test.step("Step 7: Validate Tus Negocios", async () => {
    await verify("Tus Negocios", async () => {
      const businessesSection = page.locator("section, div").filter({
        has: page.getByText(textMatcher("Tus Negocios"), { exact: false }).first(),
      });

      await expect(businessesSection.first()).toBeVisible();
      await expect(businessesSection.getByRole("button", { name: textMatcher("Agregar Negocio") }).first()).toBeVisible();
      await expect(
        businessesSection.getByText(textMatcher("Tienes 2 de 3 negocios"), { exact: false }).first(),
      ).toBeVisible();
    });
  });

  await test.step("Step 8: Validate Términos y Condiciones", async () => {
    await verify("Términos y Condiciones", async () => {
      legalUrls["Términos y Condiciones"] = await validateLegalLink(page, "Términos y Condiciones");
    });
  });

  await test.step("Step 9: Validate Política de Privacidad", async () => {
    await verify("Política de Privacidad", async () => {
      legalUrls["Política de Privacidad"] = await validateLegalLink(page, "Política de Privacidad");
    });
  });

  const orderedReport = REPORT_ORDER.map((name) => `${name}: ${stepResults[name]}`).join("\n");
  test.info().annotations.push({
    type: "Final Report",
    description: orderedReport,
  });

  const termsUrl = legalUrls["Términos y Condiciones"];
  if (termsUrl) {
    test.info().annotations.push({ type: "Final URL - Términos y Condiciones", description: termsUrl });
  }

  const privacyUrl = legalUrls["Política de Privacidad"];
  if (privacyUrl) {
    test.info().annotations.push({ type: "Final URL - Política de Privacidad", description: privacyUrl });
  }

  if (verificationErrors.length > 0) {
    throw new Error(`One or more validations failed:\n${verificationErrors.join("\n")}\n\n${orderedReport}`);
  }

  console.log("Final Report");
  for (const stepName of REPORT_ORDER) {
    console.log(`${stepName}: ${stepResults[stepName]}`);
  }
  if (termsUrl) {
    console.log(`Términos y Condiciones URL: ${termsUrl}`);
  }
  if (privacyUrl) {
    console.log(`Política de Privacidad URL: ${privacyUrl}`);
  }
});
