import { expect, type Locator, type Page, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  name: string;
  status: StepStatus;
  details: string;
};

const CHECKPOINT_DIR = "checkpoints";
const LOGIN_CANDIDATE_PATHS = ["/login", "/signin", "/auth/login", "/auth/signin"];
const APP_READY_MARKERS = [/dashboard/i, /inicio/i, /panel/i, /negocio/i, /mi negocio/i];
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
];

function env(name: string): string | undefined {
  const value = process.env[name];
  if (!value) return undefined;
  const trimmed = value.trim();
  return trimmed.length ? trimmed : undefined;
}

function normalizeBaseUrl(raw?: string): string | undefined {
  if (!raw) return undefined;
  return raw.endsWith("/") ? raw.slice(0, -1) : raw;
}

function cleanText(value: string | null): string {
  return (value ?? "").replace(/\s+/g, " ").trim();
}

async function screenshot(page: Page, name: string, fullPage = false): Promise<void> {
  await page.screenshot({ path: `${CHECKPOINT_DIR}/${name}.png`, fullPage });
}

async function isVisible(locator: Locator, timeout = 5000): Promise<boolean> {
  try {
    await expect(locator).toBeVisible({ timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickAndWaitUi(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 15000 });
  await locator.click();
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function gotoLogin(page: Page): Promise<void> {
  const explicitLoginUrl = env("SALEADS_LOGIN_URL");
  const explicitBaseUrl = normalizeBaseUrl(env("SALEADS_BASE_URL"));

  if (explicitLoginUrl) {
    await page.goto(explicitLoginUrl, { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => undefined);
    return;
  }

  if (!explicitBaseUrl) {
    return;
  }

  const attempts = [explicitBaseUrl, ...LOGIN_CANDIDATE_PATHS.map((path) => `${explicitBaseUrl}${path}`)];
  for (const url of attempts) {
    await page.goto(url, { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => undefined);
    const signInLocator = page.getByRole("button", { name: /google|iniciar|sign in|acceder/i }).first();
    if (await signInLocator.isVisible().catch(() => false)) {
      return;
    }
  }
}

async function pickGoogleAccountIfAsked(page: Page): Promise<boolean> {
  const account = env("SALEADS_GOOGLE_ACCOUNT") ?? "juanlucasbarbiergarzon@gmail.com";
  const accountChip = page.getByText(account, { exact: true });
  if (await accountChip.isVisible().catch(() => false)) {
    await accountChip.click();
    await page.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => undefined);
    return true;
  }
  return false;
}

async function waitForMainInterface(page: Page): Promise<void> {
  const sidebarCandidate = page.locator("aside, nav").filter({
    hasText: /negocio|dashboard|inicio|mi negocio/i,
  });

  for (let attempt = 0; attempt < 120; attempt += 1) {
    if (await sidebarCandidate.first().isVisible().catch(() => false)) {
      return;
    }

    const bodyText = cleanText(await page.locator("body").textContent());
    if (APP_READY_MARKERS.some((marker) => marker.test(bodyText))) {
      return;
    }

    await page.waitForTimeout(500);
  }

  throw new Error("Main interface did not load within expected timeout.");
}

async function expandMiNegocioMenu(page: Page): Promise<void> {
  const negocioEntry = page.getByText("Negocio", { exact: true }).first();
  if (await negocioEntry.isVisible().catch(() => false)) {
    await clickAndWaitUi(page, negocioEntry);
  }

  const miNegocioEntry = page.getByText("Mi Negocio", { exact: true }).first();
  await clickAndWaitUi(page, miNegocioEntry);
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregar = page.getByText("Agregar Negocio", { exact: true }).first();
  const administrar = page.getByText("Administrar Negocios", { exact: true }).first();
  if ((await agregar.isVisible().catch(() => false)) && (await administrar.isVisible().catch(() => false))) {
    return;
  }
  await expandMiNegocioMenu(page);
}

async function openLinkAndCaptureUrl(page: Page, linkText: string): Promise<{ url: string; openedInNewTab: boolean }> {
  const link = page.getByText(linkText, { exact: true }).first();
  await expect(link).toBeVisible({ timeout: 15000 });

  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  await link.click();
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => undefined);
    return { url: popup.url(), openedInNewTab: true };
  }

  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => undefined);
  return { url: page.url(), openedInNewTab: false };
}

test.describe("SaleADS - Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
    const results: StepResult[] = [];
    const record = (name: string, status: StepStatus, details: string) => {
      results.push({ name, status, details });
    };

    try {
      // Step 1: Login with Google
      try {
        await gotoLogin(page);
        const googleButton = page
          .getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i })
          .first();
        await expect(googleButton).toBeVisible({ timeout: 15000 });

        const googlePopupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
        await googleButton.click();
        const googlePopup = await googlePopupPromise;

        if (googlePopup) {
          await googlePopup.waitForLoadState("domcontentloaded");
          await pickGoogleAccountIfAsked(googlePopup);
          await googlePopup.waitForEvent("close", { timeout: 60000 }).catch(() => undefined);
          await page.bringToFront();
        } else {
          await page.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => undefined);
          await pickGoogleAccountIfAsked(page);
        }

        await page.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => undefined);
        await waitForMainInterface(page);

        const sidebarVisible = await isVisible(page.locator("aside, nav").first(), 20000);
        await screenshot(page, "01-dashboard-loaded");
        record("Login", sidebarVisible ? "PASS" : "FAIL", "Main interface and left navigation visibility checked.");
      } catch (error) {
        await screenshot(page, "01-login-failure");
        record("Login", "FAIL", `Login step failed: ${(error as Error).message}`);
        throw error;
      }

      // Step 2: Open Mi Negocio menu
      try {
        await expandMiNegocioMenu(page);
        const agregarVisible = await isVisible(page.getByText("Agregar Negocio", { exact: true }).first(), 15000);
        const administrarVisible = await isVisible(page.getByText("Administrar Negocios", { exact: true }).first(), 15000);
        await screenshot(page, "02-mi-negocio-expanded");

        record(
          "Mi Negocio menu",
          agregarVisible && administrarVisible ? "PASS" : "FAIL",
          "Validated expanded submenu with Agregar/Administrar options."
        );
        expect(agregarVisible).toBeTruthy();
        expect(administrarVisible).toBeTruthy();
      } catch (error) {
        await screenshot(page, "02-mi-negocio-failure");
        record("Mi Negocio menu", "FAIL", `Failed to expand/validate Mi Negocio: ${(error as Error).message}`);
        throw error;
      }

      // Step 3: Validate Agregar Negocio modal
      try {
        const agregarOption = page.getByText("Agregar Negocio", { exact: true }).first();
        await clickAndWaitUi(page, agregarOption);

        const modal = page.getByRole("dialog");
        await expect(modal).toBeVisible({ timeout: 15000 });
        await expect(modal).toContainText("Crear Nuevo Negocio");
        await expect(modal).toContainText("Nombre del Negocio");
        await expect(modal).toContainText("Tienes 2 de 3 negocios");
        await expect(modal.getByRole("button", { name: "Cancelar" })).toBeVisible();
        await expect(modal.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

        const businessNameInput = modal
          .getByRole("textbox", { name: /Nombre del Negocio/i })
          .or(modal.getByPlaceholder(/Nombre del Negocio/i))
          .first();

        if (await businessNameInput.isVisible().catch(() => false)) {
          await businessNameInput.click();
          await businessNameInput.fill("Negocio Prueba Automatización");
        }

        await screenshot(page, "03-agregar-negocio-modal");
        await clickAndWaitUi(page, modal.getByRole("button", { name: "Cancelar" }));
        record("Agregar Negocio modal", "PASS", "Validated modal content, buttons and optional field input.");
      } catch (error) {
        await screenshot(page, "03-modal-failure");
        record("Agregar Negocio modal", "FAIL", `Agregar Negocio modal validation failed: ${(error as Error).message}`);
        throw error;
      }

      // Step 4: Open Administrar Negocios
      try {
        await ensureMiNegocioExpanded(page);
        await clickAndWaitUi(page, page.getByText("Administrar Negocios", { exact: true }).first());
        await expect(page.locator("body")).toContainText("Información General", { timeout: 20000 });
        await expect(page.locator("body")).toContainText("Detalles de la Cuenta", { timeout: 20000 });
        await expect(page.locator("body")).toContainText("Tus Negocios", { timeout: 20000 });
        await expect(page.locator("body")).toContainText("Sección Legal", { timeout: 20000 });
        await screenshot(page, "04-administrar-negocios-page", true);
        record("Administrar Negocios view", "PASS", "Account page sections are visible.");
      } catch (error) {
        await screenshot(page, "04-administrar-negocios-failure", true);
        record("Administrar Negocios view", "FAIL", `Administrar Negocios page validation failed: ${(error as Error).message}`);
        throw error;
      }

      // Step 5: Validate Información General
      try {
        const body = page.locator("body");
        await expect(body).toContainText("BUSINESS PLAN");
        await expect(body.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();

        const maybeName = page.getByText(/nombre|name/i).first();
        const maybeEmail = page.getByText(/@/).first();
        expect(await maybeName.isVisible().catch(() => false)).toBeTruthy();
        expect(await maybeEmail.isVisible().catch(() => false)).toBeTruthy();

        record("Información General", "PASS", "Name/email and plan details validated.");
      } catch (error) {
        record("Información General", "FAIL", `Información General validation failed: ${(error as Error).message}`);
        throw error;
      }

      // Step 6: Validate Detalles de la Cuenta
      try {
        const body = page.locator("body");
        await expect(body).toContainText("Cuenta creada");
        await expect(body).toContainText(/Estado activo|Activo/i);
        await expect(body).toContainText("Idioma seleccionado");
        record("Detalles de la Cuenta", "PASS", "Cuenta creada, estado activo e idioma seleccionado validated.");
      } catch (error) {
        record("Detalles de la Cuenta", "FAIL", `Detalles de la Cuenta validation failed: ${(error as Error).message}`);
        throw error;
      }

      // Step 7: Validate Tus Negocios
      try {
        const body = page.locator("body");
        await expect(body).toContainText("Tus Negocios");
        await expect(body.getByRole("button", { name: "Agregar Negocio" })).toBeVisible();
        await expect(body).toContainText("Tienes 2 de 3 negocios");
        record("Tus Negocios", "PASS", "Business list section and quota text validated.");
      } catch (error) {
        record("Tus Negocios", "FAIL", `Tus Negocios validation failed: ${(error as Error).message}`);
        throw error;
      }

      // Step 8: Validate Términos y Condiciones
      let termsUrl = "";
      try {
        const legal = await openLinkAndCaptureUrl(page, "Términos y Condiciones");
        termsUrl = legal.url;

        if (legal.openedInNewTab) {
          const termsTab = page.context().pages()[page.context().pages().length - 1];
          await expect(termsTab.locator("body")).toContainText("Términos y Condiciones", { timeout: 20000 });
          await screenshot(termsTab, "08-terminos-y-condiciones");
          await termsTab.close();
          await page.bringToFront();
        } else {
          await expect(page.locator("body")).toContainText("Términos y Condiciones", { timeout: 20000 });
          await screenshot(page, "08-terminos-y-condiciones");
          await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
        }

        record("Términos y Condiciones", "PASS", `Legal page validated. URL: ${termsUrl}`);
      } catch (error) {
        record("Términos y Condiciones", "FAIL", `Términos y Condiciones validation failed: ${(error as Error).message}`);
        throw error;
      }

      // Step 9: Validate Política de Privacidad
      let privacyUrl = "";
      try {
        const legal = await openLinkAndCaptureUrl(page, "Política de Privacidad");
        privacyUrl = legal.url;

        if (legal.openedInNewTab) {
          const privacyTab = page.context().pages()[page.context().pages().length - 1];
          await expect(privacyTab.locator("body")).toContainText("Política de Privacidad", { timeout: 20000 });
          await screenshot(privacyTab, "09-politica-de-privacidad");
          await privacyTab.close();
          await page.bringToFront();
        } else {
          await expect(page.locator("body")).toContainText("Política de Privacidad", { timeout: 20000 });
          await screenshot(page, "09-politica-de-privacidad");
          await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
        }

        record("Política de Privacidad", "PASS", `Legal page validated. URL: ${privacyUrl}`);
      } catch (error) {
        record("Política de Privacidad", "FAIL", `Política de Privacidad validation failed: ${(error as Error).message}`);
        throw error;
      }
    } finally {
      for (const field of REPORT_FIELDS) {
        if (!results.some((result) => result.name === field)) {
          results.push({
            name: field,
            status: "FAIL",
            details: "Not executed because a previous step failed.",
          });
        }
      }

      const finalReportLines = [
        "## Final Report",
        "",
        ...results.map((item) => `- ${item.name}: ${item.status} (${cleanText(item.details)})`),
      ];
      await testInfo.attach("final-report", {
        body: Buffer.from(finalReportLines.join("\n"), "utf-8"),
        contentType: "text/markdown",
      });
    }
  });
});
