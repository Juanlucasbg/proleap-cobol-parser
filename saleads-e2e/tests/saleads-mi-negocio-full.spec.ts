import { expect, Locator, Page, test, TestInfo } from "@playwright/test";

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
  status: "PASS" | "FAIL";
  details?: string;
};

type FinalReport = Record<ReportKey, StepResult>;

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

async function findVisible(
  page: Page,
  locators: Locator[],
  description: string,
  timeoutMs = 10000
): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      const candidate = locator.first();
      const isVisible = await candidate.isVisible().catch(() => false);

      if (isVisible) {
        return candidate;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Could not find a visible element for "${description}".`);
}

async function checkpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage
  });
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarNegocio = page.getByText(/^Agregar Negocio$/i).first();
  const administrarNegocios = page.getByText(/^Administrar Negocios$/i).first();

  const agregarVisible = await agregarNegocio.isVisible().catch(() => false);
  const administrarVisible = await administrarNegocios.isVisible().catch(() => false);

  if (!agregarVisible || !administrarVisible) {
    const miNegocio = await findVisible(
      page,
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      "Mi Negocio menu item"
    );

    await miNegocio.click();
    await waitForUi(page);
  }

  await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
  await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
}

async function openLegalPage(
  page: Page,
  testInfo: TestInfo,
  linkNameRegex: RegExp,
  headingRegex: RegExp,
  screenshotName: string
): Promise<string> {
  const context = page.context();
  const appUrlBeforeClick = page.url();

  const legalLink = await findVisible(
    page,
    [
      page.getByRole("link", { name: linkNameRegex }),
      page.getByText(linkNameRegex)
    ],
    `legal link ${String(linkNameRegex)}`
  );

  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await legalLink.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForTimeout(1000);

  const legalHeading = await findVisible(
    targetPage,
    [
      targetPage.getByRole("heading", { name: headingRegex }),
      targetPage.getByText(headingRegex)
    ],
    `legal heading ${String(headingRegex)}`
  );
  await expect(legalHeading).toBeVisible();

  const bodyText = await targetPage.locator("body").innerText();
  if (bodyText.trim().length < 120) {
    throw new Error("Legal content appears too short; expected substantial legal text.");
  }

  await checkpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.setTimeout(180000);

  const report: FinalReport = {
    Login: { status: "FAIL", details: "Step did not execute." },
    "Mi Negocio menu": { status: "FAIL", details: "Step did not execute." },
    "Agregar Negocio modal": { status: "FAIL", details: "Step did not execute." },
    "Administrar Negocios view": { status: "FAIL", details: "Step did not execute." },
    "Información General": { status: "FAIL", details: "Step did not execute." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Step did not execute." },
    "Tus Negocios": { status: "FAIL", details: "Step did not execute." },
    "Términos y Condiciones": { status: "FAIL", details: "Step did not execute." },
    "Política de Privacidad": { status: "FAIL", details: "Step did not execute." }
  };

  const failures: string[] = [];
  const evidence: Record<string, string> = {};
  let loginSuccessful = false;
  let adminViewLoaded = false;

  const runStep = async (
    key: ReportKey,
    fn: () => Promise<void>,
    dependency?: { ok: boolean; message: string }
  ) => {
    if (dependency && !dependency.ok) {
      report[key] = { status: "FAIL", details: `Blocked: ${dependency.message}` };
      failures.push(`${key}: Blocked: ${dependency.message}`);
      return;
    }

    try {
      await fn();
      report[key] = { status: "PASS" };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[key] = { status: "FAIL", details: message };
      failures.push(`${key}: ${message}`);
    }
  };

  await runStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;

    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No login URL provided and browser is on about:blank. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL."
      );
    }

    const loginButton = await findVisible(
      page,
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google/i }),
        page.getByText(/sign in with google|iniciar sesion con google|iniciar sesión con google|continuar con google/i)
      ],
      "Sign in with Google button"
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    const authPage = popup ?? page;
    await authPage.waitForLoadState("domcontentloaded");

    const accountOption = authPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await waitForUi(authPage);
    }

    if (popup) {
      await popup.waitForClose({ timeout: 45000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await waitForUi(page);
    await expect(page.locator("main").first()).toBeVisible();

    const sidebar = await findVisible(
      page,
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator("[data-testid*='sidebar']")
      ],
      "left sidebar navigation",
      30000
    );
    await expect(sidebar).toBeVisible();

    await checkpoint(page, testInfo, "01-dashboard-loaded.png", true);
    loginSuccessful = true;
  });

  await runStep(
    "Mi Negocio menu",
    async () => {
    const negocioSection = await findVisible(
      page,
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i)
      ],
      "Negocio section"
    );
    await negocioSection.click();
    await waitForUi(page);

    const miNegocio = await findVisible(
      page,
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      "Mi Negocio option"
    );
    await miNegocio.click();
    await waitForUi(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await checkpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
    },
    { ok: loginSuccessful, message: "Login step failed." }
  );

  await runStep(
    "Agregar Negocio modal",
    async () => {
    await ensureMiNegocioExpanded(page);

    const agregarNegocioButton = await findVisible(
      page,
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      "Agregar Negocio"
    );
    await agregarNegocioButton.click();
    await waitForUi(page);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i).first()).toBeVisible();

    const nombreInput = await findVisible(
      page,
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[name*='negocio' i]")
      ],
      "Nombre del Negocio input"
    );
    await expect(nombreInput).toBeVisible();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();
    await checkpoint(page, testInfo, "03-agregar-negocio-modal.png");

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    await waitForUi(page);

    const cancelarButton = page.getByRole("button", { name: /^Cancelar$/i }).first();
    await cancelarButton.click();
    await waitForUi(page);
    },
    { ok: loginSuccessful, message: "Login step failed." }
  );

  await runStep(
    "Administrar Negocios view",
    async () => {
    await ensureMiNegocioExpanded(page);

    const administrarNegocios = await findVisible(
      page,
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ],
      "Administrar Negocios"
    );
    await administrarNegocios.click();
    await waitForUi(page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
    await checkpoint(page, testInfo, "04-administrar-negocios-view.png", true);
    adminViewLoaded = true;
    },
    { ok: loginSuccessful, message: "Login step failed." }
  );

  await runStep(
    "Información General",
    async () => {
    const generalSection = page.locator("section, div").filter({ hasText: /Información General/i }).first();
    await expect(generalSection).toBeVisible();

    const sectionText = await generalSection.innerText();
    const emailMatch = sectionText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    if (!emailMatch) {
      throw new Error("No user email detected in Información General section.");
    }

    const lines = sectionText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const ignored = new Set([
      "Información General",
      "BUSINESS PLAN",
      "Cambiar Plan"
    ]);
    const probableName = lines.find((line) => !ignored.has(line) && !line.includes("@") && /[A-Za-z]{2,}/.test(line));
    if (!probableName) {
      throw new Error("No user name-like value detected in Información General section.");
    }

    await expect(generalSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(generalSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    },
    { ok: adminViewLoaded, message: "Administrar Negocios view step failed." }
  );

  await runStep(
    "Detalles de la Cuenta",
    async () => {
    const detailsSection = page.locator("section, div").filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
    },
    { ok: adminViewLoaded, message: "Administrar Negocios view step failed." }
  );

  await runStep(
    "Tus Negocios",
    async () => {
    const businessSection = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const businessItemCount = await businessSection
      .locator("li, [role='listitem'], tbody tr, [data-testid*='business']")
      .count();
    if (businessItemCount < 1) {
      throw new Error("Business list is not visible or has no visible items.");
    }
    },
    { ok: adminViewLoaded, message: "Administrar Negocios view step failed." }
  );

  await runStep(
    "Términos y Condiciones",
    async () => {
    const termsUrl = await openLegalPage(
      page,
      testInfo,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones.png"
    );
    evidence["Términos y Condiciones URL"] = termsUrl;
    },
    { ok: adminViewLoaded, message: "Administrar Negocios view step failed." }
  );

  await runStep(
    "Política de Privacidad",
    async () => {
    const privacyUrl = await openLegalPage(
      page,
      testInfo,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
      "06-politica-de-privacidad.png"
    );
    evidence["Política de Privacidad URL"] = privacyUrl;
    },
    { ok: adminViewLoaded, message: "Administrar Negocios view step failed." }
  );

  const finalPayload = {
    report,
    evidence
  };

  await testInfo.attach("saleads-mi-negocio-final-report.json", {
    body: Buffer.from(JSON.stringify(finalPayload, null, 2)),
    contentType: "application/json"
  });

  if (failures.length > 0) {
    throw new Error(`Workflow validations failed:\n${failures.join("\n")}`);
  }
});
