import { expect, test, type Locator, type Page } from "@playwright/test";
import fs from "node:fs";

type Status = "PASS" | "FAIL";

type StepResult = {
  step: string;
  status: Status;
  details: string;
};

type WorkflowResult = {
  login: StepResult;
  miNegocioMenu: StepResult;
  agregarNegocioModal: StepResult;
  administrarNegociosView: StepResult;
  informacionGeneral: StepResult;
  detallesCuenta: StepResult;
  tusNegocios: StepResult;
  terminosCondiciones: StepResult;
  politicaPrivacidad: StepResult;
};

const SCREENSHOT_DIR = "screenshots";

const selectors = {
  loginButton: [
    "button:has-text('Sign in with Google')",
    "button:has-text('Iniciar sesión con Google')",
    "button:has-text('Continuar con Google')",
    "a:has-text('Sign in with Google')",
    "a:has-text('Iniciar sesión con Google')",
  ],
  sidebarNav: [
    "nav",
    "[aria-label*='sidebar' i]",
    "[class*='sidebar' i]",
    "aside",
  ],
};

const accountEmail = "juanlucasbarbiergarzon@gmail.com";
const startUrl = process.env.SALEADS_URL ?? process.env.BASE_URL;

function getByTextLoose(page: Page, text: string): Locator {
  return page.getByText(text, { exact: false });
}

async function waitUiAfterClick(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
}

async function firstVisibleLocator(page: Page, selectorList: string[], timeoutMs = 8000): Promise<Locator | null> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const selector of selectorList) {
      const locator = page.locator(selector).first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await page.waitForTimeout(250);
  }

  return null;
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const button = page.getByRole("button", { name: text, exact: false }).first();
  if (await button.isVisible().catch(() => false)) {
    await button.click();
    await waitUiAfterClick(page);
    return;
  }

  const link = page.getByRole("link", { name: text, exact: false }).first();
  if (await link.isVisible().catch(() => false)) {
    await link.click();
    await waitUiAfterClick(page);
    return;
  }

  const looseText = getByTextLoose(page, text).first();
  if (await looseText.isVisible().catch(() => false)) {
    await looseText.click();
    await waitUiAfterClick(page);
    return;
  }

  throw new Error(`Could not find clickable element with text: ${text}`);
}

async function getBusinessNameInput(page: Page): Promise<Locator> {
  const labelLocator = page.getByLabel("Nombre del Negocio", { exact: false }).first();
  if (await labelLocator.isVisible().catch(() => false)) {
    return labelLocator;
  }

  const placeholderLocator = page.getByPlaceholder("Nombre del Negocio", { exact: false }).first();
  if (await placeholderLocator.isVisible().catch(() => false)) {
    return placeholderLocator;
  }

  const textboxByName = page.getByRole("textbox", { name: "Nombre del Negocio", exact: false }).first();
  if (await textboxByName.isVisible().catch(() => false)) {
    return textboxByName;
  }

  // Fallback: first visible text input in the modal.
  const modalTextbox = page.locator('[role="dialog"] input[type="text"]').first();
  if (await modalTextbox.isVisible().catch(() => false)) {
    return modalTextbox;
  }

  throw new Error("Input field 'Nombre del Negocio' was not found.");
}

async function closeModalIfOpen(page: Page): Promise<void> {
  const cancelButton = page.getByRole("button", { name: "Cancelar", exact: false }).first();
  if (await cancelButton.isVisible().catch(() => false)) {
    await cancelButton.click();
    await waitUiAfterClick(page);
  }
}

async function chooseGoogleAccountIfPrompt(page: Page): Promise<void> {
  // Google auth may appear either in the same tab or as popup.
  const candidate = page.getByText(accountEmail, { exact: false }).first();
  if (await candidate.isVisible().catch(() => false)) {
    await candidate.click();
    await waitUiAfterClick(page);
  }
}

function blockedStep(step: string, reason: string): StepResult {
  return {
    step,
    status: "FAIL",
    details: `Blocked by previous failure: ${reason}`,
  };
}

async function clickTermsOrPolicyAndValidate(
  page: Page,
  linkText: string,
  heading: string,
  screenshotName: string
): Promise<{ url: string }> {
  const target = page.getByRole("link", { name: linkText, exact: false }).first();
  if (!(await target.isVisible().catch(() => false))) {
    throw new Error(`Legal link "${linkText}" is not visible.`);
  }

  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  await target.click();
  await waitUiAfterClick(page);
  const popup = await popupPromise;

  const legalPage = popup ?? page;
  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForLoadState("networkidle");

  await expect(legalPage.getByRole("heading", { name: heading, exact: false }).first()).toBeVisible({
    timeout: 15000,
  });
  await expect(legalPage.locator("body")).toContainText(heading, { timeout: 15000 });

  await legalPage.screenshot({ path: `${SCREENSHOT_DIR}/${screenshotName}`, fullPage: true });
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitUiAfterClick(page);
  }

  return { url: finalUrl };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
    const stepResults: WorkflowResult = {
      login: { step: "Login", status: "FAIL", details: "" },
      miNegocioMenu: { step: "Mi Negocio menu", status: "FAIL", details: "" },
      agregarNegocioModal: { step: "Agregar Negocio modal", status: "FAIL", details: "" },
      administrarNegociosView: { step: "Administrar Negocios view", status: "FAIL", details: "" },
      informacionGeneral: { step: "Información General", status: "FAIL", details: "" },
      detallesCuenta: { step: "Detalles de la Cuenta", status: "FAIL", details: "" },
      tusNegocios: { step: "Tus Negocios", status: "FAIL", details: "" },
      terminosCondiciones: { step: "Términos y Condiciones", status: "FAIL", details: "" },
      politicaPrivacidad: { step: "Política de Privacidad", status: "FAIL", details: "" },
    };
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    let blockerReason: string | null = null;
    let termsUrl = "";
    let privacyUrl = "";

    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await page.waitForLoadState("networkidle");
    } else if (page.url() === "about:blank") {
      blockerReason =
        "Page is about:blank. Provide SALEADS_URL (or BASE_URL) to the login page for the target environment.";
    }

    // Step 1: Login with Google
    try {
      if (blockerReason) {
        throw new Error(blockerReason);
      }
      const loginButton = await firstVisibleLocator(page, selectors.loginButton, 10000);
      if (!loginButton) {
        throw new Error("Google login button not found on current page.");
      }

      const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
      await loginButton.click();
      await waitUiAfterClick(page);
      const googlePopup = await popupPromise;

      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded");
        await googlePopup.waitForLoadState("networkidle");
        await chooseGoogleAccountIfPrompt(googlePopup);
        await googlePopup.waitForTimeout(1000);
        await page.bringToFront();
      } else {
        await chooseGoogleAccountIfPrompt(page);
      }

      await waitUiAfterClick(page);

      const sidebar = await firstVisibleLocator(page, selectors.sidebarNav, 15000);
      expect(sidebar, "Left sidebar navigation should be visible after login").not.toBeNull();

      await page.screenshot({ path: `${SCREENSHOT_DIR}/01-dashboard-loaded.png`, fullPage: true });
      stepResults.login = { step: "Login", status: "PASS", details: "Dashboard and sidebar are visible." };
    } catch (error) {
      stepResults.login = {
        step: "Login",
        status: "FAIL",
        details: error instanceof Error ? error.message : "Unknown login error",
      };
      blockerReason = stepResults.login.details;
    }

    // Step 2: Open Mi Negocio menu
    try {
      if (blockerReason) {
        throw new Error(blockerReason);
      }
      await clickByVisibleText(page, "Negocio");
      await clickByVisibleText(page, "Mi Negocio");
      await expect(getByTextLoose(page, "Agregar Negocio").first()).toBeVisible({ timeout: 15000 });
      await expect(getByTextLoose(page, "Administrar Negocios").first()).toBeVisible({ timeout: 15000 });

      await page.screenshot({ path: `${SCREENSHOT_DIR}/02-mi-negocio-menu-expanded.png`, fullPage: true });
      stepResults.miNegocioMenu = {
        step: "Mi Negocio menu",
        status: "PASS",
        details: "Menu expanded with Agregar Negocio and Administrar Negocios.",
      };
    } catch (error) {
      stepResults.miNegocioMenu = {
        step: "Mi Negocio menu",
        status: "FAIL",
        details: error instanceof Error ? error.message : "Unknown Mi Negocio menu error",
      };
      blockerReason = stepResults.miNegocioMenu.details;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      if (blockerReason) {
        throw new Error(blockerReason);
      }
      await clickByVisibleText(page, "Agregar Negocio");
      await expect(getByTextLoose(page, "Crear Nuevo Negocio").first()).toBeVisible({ timeout: 15000 });
      const businessName = await getBusinessNameInput(page);
      await expect(businessName).toBeVisible({ timeout: 15000 });
      await expect(getByTextLoose(page, "Tienes 2 de 3 negocios").first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: "Cancelar", exact: false })).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: "Crear Negocio", exact: false })).toBeVisible({ timeout: 15000 });

      await page.screenshot({ path: `${SCREENSHOT_DIR}/03-agregar-negocio-modal.png`, fullPage: true });

      await businessName.click();
      await businessName.fill("Negocio Prueba Automatización");
      await closeModalIfOpen(page);

      stepResults.agregarNegocioModal = {
        step: "Agregar Negocio modal",
        status: "PASS",
        details: "Modal fields validated and closed via Cancelar.",
      };
    } catch (error) {
      stepResults.agregarNegocioModal = {
        step: "Agregar Negocio modal",
        status: "FAIL",
        details: error instanceof Error ? error.message : "Unknown Agregar Negocio modal error",
      };
      blockerReason = stepResults.agregarNegocioModal.details;
    }

    // Step 4: Open Administrar Negocios
    try {
      if (blockerReason) {
        throw new Error(blockerReason);
      }
      // Re-expand if collapsed
      if (!(await getByTextLoose(page, "Administrar Negocios").first().isVisible().catch(() => false))) {
        await clickByVisibleText(page, "Mi Negocio");
      }

      await clickByVisibleText(page, "Administrar Negocios");
      await expect(getByTextLoose(page, "Información General").first()).toBeVisible({ timeout: 15000 });
      await expect(getByTextLoose(page, "Detalles de la Cuenta").first()).toBeVisible({ timeout: 15000 });
      await expect(getByTextLoose(page, "Tus Negocios").first()).toBeVisible({ timeout: 15000 });
      await expect(getByTextLoose(page, "Sección Legal").first()).toBeVisible({ timeout: 15000 });

      await page.screenshot({ path: `${SCREENSHOT_DIR}/04-administrar-negocios-view.png`, fullPage: true });
      stepResults.administrarNegociosView = {
        step: "Administrar Negocios view",
        status: "PASS",
        details: "All expected account sections are visible.",
      };
    } catch (error) {
      stepResults.administrarNegociosView = {
        step: "Administrar Negocios view",
        status: "FAIL",
        details: error instanceof Error ? error.message : "Unknown Administrar Negocios view error",
      };
      blockerReason = stepResults.administrarNegociosView.details;
    }

    // Step 5: Validate Información General
    try {
      if (blockerReason) {
        throw new Error(blockerReason);
      }
      const bodyText = await page.locator("body").innerText();
      const hasEmail = bodyText.includes("@");
      const hasLikelyUserName =
        page.getByText(/^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ'\- ]{2,}$/).first().isVisible().catch(() => false);
      expect(hasEmail, "User email should be visible in Información General section").toBeTruthy();
      expect(await hasLikelyUserName, "A user name-like text should be visible").toBeTruthy();
      await expect(getByTextLoose(page, "BUSINESS PLAN").first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: "Cambiar Plan", exact: false })).toBeVisible({ timeout: 15000 });
      stepResults.informacionGeneral = {
        step: "Información General",
        status: "PASS",
        details: "User details, plan text, and Cambiar Plan button are visible.",
      };
    } catch (error) {
      stepResults.informacionGeneral = {
        step: "Información General",
        status: "FAIL",
        details: error instanceof Error ? error.message : "Unknown Información General error",
      };
      blockerReason = stepResults.informacionGeneral.details;
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      if (blockerReason) {
        throw new Error(blockerReason);
      }
      await expect(getByTextLoose(page, "Cuenta creada").first()).toBeVisible({ timeout: 15000 });
      await expect(getByTextLoose(page, "Estado activo").first()).toBeVisible({ timeout: 15000 });
      await expect(getByTextLoose(page, "Idioma seleccionado").first()).toBeVisible({ timeout: 15000 });
      stepResults.detallesCuenta = {
        step: "Detalles de la Cuenta",
        status: "PASS",
        details: "Cuenta creada, Estado activo, and Idioma seleccionado are visible.",
      };
    } catch (error) {
      stepResults.detallesCuenta = {
        step: "Detalles de la Cuenta",
        status: "FAIL",
        details: error instanceof Error ? error.message : "Unknown Detalles de la Cuenta error",
      };
      blockerReason = stepResults.detallesCuenta.details;
    }

    // Step 7: Validate Tus Negocios
    try {
      if (blockerReason) {
        throw new Error(blockerReason);
      }
      await expect(getByTextLoose(page, "Tus Negocios").first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: "Agregar Negocio", exact: false })).toBeVisible({ timeout: 15000 });
      await expect(getByTextLoose(page, "Tienes 2 de 3 negocios").first()).toBeVisible({ timeout: 15000 });
      stepResults.tusNegocios = {
        step: "Tus Negocios",
        status: "PASS",
        details: "Business list context and quota text are visible.",
      };
    } catch (error) {
      stepResults.tusNegocios = {
        step: "Tus Negocios",
        status: "FAIL",
        details: error instanceof Error ? error.message : "Unknown Tus Negocios error",
      };
      blockerReason = stepResults.tusNegocios.details;
    }

    // Step 8: Validate Términos y Condiciones
    try {
      if (blockerReason) {
        throw new Error(blockerReason);
      }
      const result = await clickTermsOrPolicyAndValidate(
        page,
        "Términos y Condiciones",
        "Términos y Condiciones",
        "05-terminos-y-condiciones.png"
      );
      termsUrl = result.url;
      stepResults.terminosCondiciones = {
        step: "Términos y Condiciones",
        status: "PASS",
        details: `Legal page validated. URL: ${termsUrl}`,
      };
    } catch (error) {
      stepResults.terminosCondiciones = {
        step: "Términos y Condiciones",
        status: "FAIL",
        details: error instanceof Error ? error.message : "Unknown Términos y Condiciones error",
      };
      blockerReason = stepResults.terminosCondiciones.details;
    }

    // Step 9: Validate Política de Privacidad
    try {
      if (blockerReason) {
        throw new Error(blockerReason);
      }
      const result = await clickTermsOrPolicyAndValidate(
        page,
        "Política de Privacidad",
        "Política de Privacidad",
        "06-politica-de-privacidad.png"
      );
      privacyUrl = result.url;
      stepResults.politicaPrivacidad = {
        step: "Política de Privacidad",
        status: "PASS",
        details: `Legal page validated. URL: ${privacyUrl}`,
      };
    } catch (error) {
      stepResults.politicaPrivacidad = {
        step: "Política de Privacidad",
        status: "FAIL",
        details: error instanceof Error ? error.message : "Unknown Política de Privacidad error",
      };
    }

    if (stepResults.miNegocioMenu.details === "" && blockerReason) {
      stepResults.miNegocioMenu = blockedStep("Mi Negocio menu", blockerReason);
    }
    if (stepResults.agregarNegocioModal.details === "" && blockerReason) {
      stepResults.agregarNegocioModal = blockedStep("Agregar Negocio modal", blockerReason);
    }
    if (stepResults.administrarNegociosView.details === "" && blockerReason) {
      stepResults.administrarNegociosView = blockedStep("Administrar Negocios view", blockerReason);
    }
    if (stepResults.informacionGeneral.details === "" && blockerReason) {
      stepResults.informacionGeneral = blockedStep("Información General", blockerReason);
    }
    if (stepResults.detallesCuenta.details === "" && blockerReason) {
      stepResults.detallesCuenta = blockedStep("Detalles de la Cuenta", blockerReason);
    }
    if (stepResults.tusNegocios.details === "" && blockerReason) {
      stepResults.tusNegocios = blockedStep("Tus Negocios", blockerReason);
    }
    if (stepResults.terminosCondiciones.details === "" && blockerReason) {
      stepResults.terminosCondiciones = blockedStep("Términos y Condiciones", blockerReason);
    }
    if (stepResults.politicaPrivacidad.details === "" && blockerReason) {
      stepResults.politicaPrivacidad = blockedStep("Política de Privacidad", blockerReason);
    }

    // Step 10: Final report
    const finalReportTable = Object.values(stepResults).map((item) => ({
      Step: item.step,
      Status: item.status,
      Details: item.details,
    }));
    // Console output is used by CI logs as final report.
    // eslint-disable-next-line no-console
    console.table(finalReportTable);

    await testInfo.attach("final-report.json", {
      contentType: "application/json",
      body: Buffer.from(JSON.stringify(stepResults, null, 2), "utf-8"),
    });

    const failedSteps = Object.values(stepResults).filter((step) => step.status === "FAIL");
    expect(
      failedSteps,
      failedSteps.length === 0
        ? "All workflow validations passed."
        : `Workflow failed on: ${failedSteps.map((step) => step.step).join(", ")}`
    ).toHaveLength(0);
  });
});
