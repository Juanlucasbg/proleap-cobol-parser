import { expect, Page, test } from "@playwright/test";

type StepStatus = "PASS" | "FAIL";

type ValidationReport = {
  Login: StepStatus;
  "Mi Negocio menu": StepStatus;
  "Agregar Negocio modal": StepStatus;
  "Administrar Negocios view": StepStatus;
  "Informacion General": StepStatus;
  "Detalles de la Cuenta": StepStatus;
  "Tus Negocios": StepStatus;
  "Terminos y Condiciones": StepStatus;
  "Politica de Privacidad": StepStatus;
};

const report: ValidationReport = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Informacion General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Terminos y Condiciones": "FAIL",
  "Politica de Privacidad": "FAIL",
};

async function waitUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await page.waitForTimeout(500);
}

function normalize(input: string): string {
  return input
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .trim();
}

function textRegex(label: string): RegExp {
  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const flexible = escaped
    .replace(/a/gi, "[aáàäâã]")
    .replace(/e/gi, "[eéèëê]")
    .replace(/i/gi, "[iíìïî]")
    .replace(/o/gi, "[oóòöôõ]")
    .replace(/u/gi, "[uúùüû]");

  return new RegExp(flexible, "i");
}

async function clickByVisibleText(page: Page, label: string): Promise<void> {
  const labelPattern = textRegex(label);
  const direct = page.getByText(labelPattern).first();
  if (await direct.isVisible().catch(() => false)) {
    await direct.click();
    await waitUi(page);
    return;
  }

  const broad = page
    .locator("button, a, [role='button'], [role='menuitem'], li, div, span")
    .filter({ hasText: labelPattern })
    .first();
  await expect(broad, `Expected clickable element with text "${label}"`).toBeVisible();
  await broad.click();
  await waitUi(page);
}

async function ensureMainAppVisible(page: Page): Promise<void> {
  const sidebar = page.locator(
    "nav, aside, [role='navigation'], [class*='sidebar'], [id*='sidebar']"
  );
  await expect(sidebar.first()).toBeVisible({ timeout: 60_000 });
}

async function openMiNegocioMenu(page: Page): Promise<void> {
  const negocioTrigger = page.getByText("Negocio", { exact: false }).first();
  await expect(negocioTrigger).toBeVisible({ timeout: 30_000 });
  await negocioTrigger.click();
  await waitUi(page);

  const miNegocio = page.getByText("Mi Negocio", { exact: false }).first();
  await expect(miNegocio).toBeVisible({ timeout: 30_000 });
  await miNegocio.click();
  await waitUi(page);
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregar = page.getByText("Agregar Negocio", { exact: false }).first();
  if (await agregar.isVisible().catch(() => false)) {
    return;
  }

  const miNegocio = page.getByText("Mi Negocio", { exact: false }).first();
  await miNegocio.click();
  await waitUi(page);
}

async function validateLegalLink(
  page: Page,
  linkText: string,
  headingRegex: RegExp,
  screenshotName: string
): Promise<string> {
  const context = page.context();
  const currentPage = page;
  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await clickByVisibleText(currentPage, linkText);
  const maybePopup = await popupPromise;

  let targetPage: Page = currentPage;
  if (maybePopup) {
    targetPage = maybePopup;
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.bringToFront();
  } else {
    await currentPage.waitForLoadState("domcontentloaded");
  }

  const heading = targetPage.getByRole("heading", { name: headingRegex }).first();
  const fallbackTitle = targetPage.getByText(headingRegex).first();
  if (await heading.isVisible().catch(() => false)) {
    await expect(heading).toBeVisible({ timeout: 30_000 });
  } else {
    await expect(fallbackTitle).toBeVisible({ timeout: 30_000 });
  }
  await expect(targetPage.locator("body")).toContainText(/\w{20,}/);
  await targetPage.screenshot({ path: `artifacts/screenshots/${screenshotName}`, fullPage: true });

  const finalUrl = targetPage.url();

  if (targetPage !== currentPage) {
    await targetPage.close();
    await currentPage.bringToFront();
  }

  await waitUi(currentPage);
  return finalUrl;
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login to SaleADS with Google and validate Mi Negocio workflow", async ({ page }) => {
    test.setTimeout(180_000);

    await page.context().grantPermissions([], { origin: page.url() || undefined }).catch(() => undefined);

    // Step 1: Login with Google (starting from pre-opened login page if available).
    // If baseURL exists we navigate; otherwise we use the already open application page.
    if (test.info().project.use.baseURL) {
      await page.goto("/", { waitUntil: "domcontentloaded" });
      await waitUi(page);
    }

    const loginCandidate = page
      .locator("button, a, [role='button']")
      .filter({ hasText: /google|iniciar sesi[oó]n|sign in/i })
      .first();

    if (await loginCandidate.isVisible().catch(() => false)) {
      await loginCandidate.click();
      await waitUi(page);
    }

    const juanAccount = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }).first();
    if (await juanAccount.isVisible().catch(() => false)) {
      await juanAccount.click();
      await waitUi(page);
    }

    await ensureMainAppVisible(page);
    report.Login = "PASS";
    await page.screenshot({ path: "artifacts/screenshots/01-dashboard-loaded.png", fullPage: true });

    // Step 2: Open Mi Negocio menu and validate options.
    await openMiNegocioMenu(page);
    await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: false }).first()).toBeVisible();
    report["Mi Negocio menu"] = "PASS";
    await page.screenshot({ path: "artifacts/screenshots/02-mi-negocio-expanded.png", fullPage: true });

    // Step 3: Validate Agregar Negocio modal.
    await clickByVisibleText(page, "Agregar Negocio");
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByLabel("Nombre del Negocio", { exact: false })).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    report["Agregar Negocio modal"] = "PASS";
    await page.screenshot({ path: "artifacts/screenshots/03-agregar-negocio-modal.png", fullPage: true });

    const negocioInput = page.getByLabel("Nombre del Negocio", { exact: false });
    if (await negocioInput.isVisible().catch(() => false)) {
      await negocioInput.fill("Negocio Prueba Automatización");
    }
    await clickByVisibleText(page, "Cancelar");

    // Step 4: Open Administrar Negocios and validate sections.
    await ensureMiNegocioExpanded(page);
    await clickByVisibleText(page, "Administrar Negocios");
    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
    report["Administrar Negocios view"] = "PASS";
    await page.screenshot({ path: "artifacts/screenshots/04-administrar-negocios-page.png", fullPage: true });

    // Step 5: Validate Información General.
    await expect(page.locator("body")).toContainText(/@/);
    await expect(page.locator("body")).toContainText(/business plan/i);
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    report["Informacion General"] = "PASS";

    // Step 6: Validate Detalles de la Cuenta.
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    report["Detalles de la Cuenta"] = "PASS";

    // Step 7: Validate Tus Negocios.
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    report["Tus Negocios"] = "PASS";

    // Step 8: Validate Términos y Condiciones.
    const terminosUrl = await validateLegalLink(
      page,
      "Términos y Condiciones",
      /T[ée]rminos y Condiciones/i,
      "05-terminos-y-condiciones.png"
    );
    report["Terminos y Condiciones"] = "PASS";

    // Step 9: Validate Política de Privacidad.
    const privacidadUrl = await validateLegalLink(
      page,
      "Política de Privacidad",
      /Pol[íi]tica de Privacidad/i,
      "06-politica-de-privacidad.png"
    );
    report["Politica de Privacidad"] = "PASS";

    const finalReport = {
      Login: report.Login,
      "Mi Negocio menu": report["Mi Negocio menu"],
      "Agregar Negocio modal": report["Agregar Negocio modal"],
      "Administrar Negocios view": report["Administrar Negocios view"],
      "Información General": report["Informacion General"],
      "Detalles de la Cuenta": report["Detalles de la Cuenta"],
      "Tus Negocios": report["Tus Negocios"],
      "Términos y Condiciones": report["Terminos y Condiciones"],
      "Política de Privacidad": report["Politica de Privacidad"],
      "Términos URL final": terminosUrl,
      "Política URL final": privacidadUrl,
    };

    await test.info().attach("final-report.json", {
      contentType: "application/json",
      body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    });

    // Ensure all validations are PASS in this single-run workflow.
    for (const [step, status] of Object.entries(finalReport)) {
      if (!normalize(step).includes("url")) {
        expect(status, `Expected ${step} to PASS`).toBe("PASS");
      }
    }
  });
});
