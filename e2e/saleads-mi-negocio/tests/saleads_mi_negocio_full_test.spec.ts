import { test, expect, Page, BrowserContext, Locator } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  details: string[];
  evidence: string[];
  finalUrl?: string;
};

type Report = Record<string, StepResult>;

function createArtifactsRoot(): string {
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const dir = path.resolve(process.cwd(), "artifacts", `saleads_mi_negocio_${stamp}`);
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function firstVisibleLocator(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  return null;
}

async function getByVisibleText(page: Page, text: string): Promise<Locator> {
  const regex = new RegExp(escapeRegex(text), "i");
  const visible = await firstVisibleLocator([
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByRole("option", { name: regex }),
    page.getByText(regex)
  ]);

  if (!visible) {
    throw new Error(`No visible element found for text: ${text}`);
  }

  return visible;
}

async function capture(page: Page, root: string, fileName: string, fullPage = false): Promise<string> {
  const target = path.join(root, fileName);
  await page.screenshot({ path: target, fullPage });
  return target;
}

async function maybeSelectGoogleAccount(targetPage: Page): Promise<boolean> {
  const accountRegex = /juanlucasbarbiergarzon@gmail\.com/i;
  const account = targetPage.getByText(accountRegex).first();
  if (await account.isVisible().catch(() => false)) {
    await account.click();
    await waitForUi(targetPage);
    return true;
  }

  return false;
}

async function assertTextVisible(page: Page, text: string): Promise<void> {
  const regex = new RegExp(escapeRegex(text), "i");
  const locator = await firstVisibleLocator([
    page.getByRole("heading", { name: regex }),
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByText(regex)
  ]);

  if (!locator) {
    throw new Error(`Expected visible text not found: ${text}`);
  }
}

async function openLegalDocument(
  context: BrowserContext,
  appPage: Page,
  root: string,
  linkText: string,
  headingText: string,
  screenshotName: string
): Promise<{ screenshot: string; finalUrl: string }> {
  const beforeUrl = appPage.url();
  const link = await getByVisibleText(appPage, linkText);
  const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);

  await link.click();
  let popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 30_000 });
    await waitForUi(popup);
  } else {
    await waitForUi(appPage);
    if (appPage.url() !== beforeUrl) {
      popup = appPage;
    }
  }

  const targetPage = popup ?? appPage;
  await assertTextVisible(targetPage, headingText);

  const legalText = (await targetPage.locator("body").innerText()).trim();
  if (legalText.length < 120) {
    throw new Error(`Legal page for "${linkText}" did not expose enough content text.`);
  }

  const screenshot = await capture(targetPage, root, screenshotName, true);
  const finalUrl = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return { screenshot, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const artifactsRoot = createArtifactsRoot();
  const report: Report = {
    Login: { status: "FAIL", details: [], evidence: [] },
    "Mi Negocio menu": { status: "FAIL", details: [], evidence: [] },
    "Agregar Negocio modal": { status: "FAIL", details: [], evidence: [] },
    "Administrar Negocios view": { status: "FAIL", details: [], evidence: [] },
    "Información General": { status: "FAIL", details: [], evidence: [] },
    "Detalles de la Cuenta": { status: "FAIL", details: [], evidence: [] },
    "Tus Negocios": { status: "FAIL", details: [], evidence: [] },
    "Términos y Condiciones": { status: "FAIL", details: [], evidence: [] },
    "Política de Privacidad": { status: "FAIL", details: [], evidence: [] }
  };

  const loginUrl = process.env.SALEADS_URL ?? process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No initial URL available. Set SALEADS_URL, SALEADS_LOGIN_URL, or BASE_URL to a SaleADS login page."
    );
  }

  async function runStep(stepName: keyof Report, executor: () => Promise<void>): Promise<void> {
    try {
      await executor();
      report[stepName].status = "PASS";
    } catch (error) {
      report[stepName].status = "FAIL";
      report[stepName].details.push(error instanceof Error ? error.message : String(error));
    }
  }

  await runStep("Login", async () => {
    const loginLabelCandidates = [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Login",
      "Iniciar sesión"
    ];

    let loginButton: Locator | null = null;
    for (const candidate of loginLabelCandidates) {
      loginButton = await firstVisibleLocator([
        page.getByRole("button", { name: new RegExp(escapeRegex(candidate), "i") }),
        page.getByText(new RegExp(escapeRegex(candidate), "i"))
      ]);
      if (loginButton) {
        break;
      }
    }

    if (!loginButton) {
      throw new Error("Could not find login/Google sign-in control by visible text.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);
    const googlePage = await popupPromise;

    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => {});
      await maybeSelectGoogleAccount(googlePage);
      await waitForUi(googlePage);
      await page.bringToFront();
    } else {
      await maybeSelectGoogleAccount(page);
      await waitForUi(page);
    }

    await expect
      .poll(async () => {
        const sidebar = page.locator("aside").first();
        const negocioTextVisible = await page.getByText(/Negocio/i).first().isVisible().catch(() => false);
        return (await sidebar.isVisible().catch(() => false)) || negocioTextVisible;
      }, { timeout: 60_000 })
      .toBeTruthy();

    const dashboardShot = await capture(page, artifactsRoot, "01_dashboard_loaded.png", true);
    report.Login.evidence.push(dashboardShot);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocio = await getByVisibleText(page, "Negocio");
    await negocio.click();
    await waitForUi(page);

    const miNegocio = await getByVisibleText(page, "Mi Negocio");
    await miNegocio.click();
    await waitForUi(page);

    await assertTextVisible(page, "Agregar Negocio");
    await assertTextVisible(page, "Administrar Negocios");

    const menuShot = await capture(page, artifactsRoot, "02_mi_negocio_expanded_menu.png", true);
    report["Mi Negocio menu"].evidence.push(menuShot);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await getByVisibleText(page, "Agregar Negocio");
    await agregarNegocio.click();
    await waitForUi(page);

    await assertTextVisible(page, "Crear Nuevo Negocio");
    await assertTextVisible(page, "Nombre del Negocio");
    await assertTextVisible(page, "Tienes 2 de 3 negocios");
    await assertTextVisible(page, "Cancelar");
    await assertTextVisible(page, "Crear Negocio");

    const modalShot = await capture(page, artifactsRoot, "03_agregar_negocio_modal.png", true);
    report["Agregar Negocio modal"].evidence.push(modalShot);

    const input = page.getByLabel(/Nombre del Negocio/i).first();
    if (await input.isVisible().catch(() => false)) {
      await input.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }

    const cancelar = await getByVisibleText(page, "Cancelar");
    await cancelar.click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocio = await getByVisibleText(page, "Mi Negocio");
      await miNegocio.click();
      await waitForUi(page);
    }

    const administrar = await getByVisibleText(page, "Administrar Negocios");
    await administrar.click();
    await waitForUi(page);

    await assertTextVisible(page, "Información General");
    await assertTextVisible(page, "Detalles de la Cuenta");
    await assertTextVisible(page, "Tus Negocios");
    await assertTextVisible(page, "Sección Legal");

    const accountPageShot = await capture(page, artifactsRoot, "04_administrar_negocios_page.png", true);
    report["Administrar Negocios view"].evidence.push(accountPageShot);
  });

  await runStep("Información General", async () => {
    await assertTextVisible(page, "Información General");
    await assertTextVisible(page, "BUSINESS PLAN");
    await assertTextVisible(page, "Cambiar Plan");

    const content = await page.locator("body").innerText();
    if (!/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(content)) {
      throw new Error("User email not detected in the account page content.");
    }

    const infoSection = page.locator("section,div").filter({ hasText: /Informaci[oó]n General/i }).first();
    const infoText = (await infoSection.innerText().catch(() => content)).trim();
    if (!/[A-Za-z][A-Za-z' -]{2,}/.test(infoText)) {
      throw new Error("User name was not confidently detected in Información General.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await assertTextVisible(page, "Detalles de la Cuenta");
    await assertTextVisible(page, "Cuenta creada");
    await assertTextVisible(page, "Estado activo");
    await assertTextVisible(page, "Idioma seleccionado");
  });

  await runStep("Tus Negocios", async () => {
    await assertTextVisible(page, "Tus Negocios");
    await assertTextVisible(page, "Agregar Negocio");
    await assertTextVisible(page, "Tienes 2 de 3 negocios");

    const businessSection = page.locator("section,div").filter({ hasText: /Tus Negocios/i }).first();
    const listLike = await firstVisibleLocator([
      businessSection.locator("li"),
      businessSection.locator("[role='listitem']"),
      businessSection.locator("[role='row']"),
      businessSection.locator("article"),
      businessSection.locator("table")
    ]);

    if (!listLike) {
      throw new Error("Business list/container not detected in Tus Negocios.");
    }
  });

  await runStep("Términos y Condiciones", async () => {
    const { screenshot, finalUrl } = await openLegalDocument(
      context,
      page,
      artifactsRoot,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05_terminos_y_condiciones.png"
    );

    report["Términos y Condiciones"].evidence.push(screenshot);
    report["Términos y Condiciones"].finalUrl = finalUrl;
  });

  await runStep("Política de Privacidad", async () => {
    const { screenshot, finalUrl } = await openLegalDocument(
      context,
      page,
      artifactsRoot,
      "Política de Privacidad",
      "Política de Privacidad",
      "06_politica_de_privacidad.png"
    );

    report["Política de Privacidad"].evidence.push(screenshot);
    report["Política de Privacidad"].finalUrl = finalUrl;
  });

  const finalReportPath = path.join(artifactsRoot, "final_report.json");
  fs.writeFileSync(finalReportPath, JSON.stringify(report, null, 2), "utf8");
  console.log(`Final report saved to: ${finalReportPath}`);
  console.log(JSON.stringify(report, null, 2));

  const failedSteps = Object.entries(report)
    .filter(([, result]) => result.status === "FAIL")
    .map(([stepName]) => stepName);

  expect(failedSteps, `Failed steps: ${failedSteps.join(", ")}`).toEqual([]);
});
