import { expect, test, type Locator, type Page } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type Report = {
  "Login": StepStatus;
  "Mi Negocio menu": StepStatus;
  "Agregar Negocio modal": StepStatus;
  "Administrar Negocios view": StepStatus;
  "Información General": StepStatus;
  "Detalles de la Cuenta": StepStatus;
  "Tus Negocios": StepStatus;
  "Términos y Condiciones": StepStatus;
  "Política de Privacidad": StepStatus;
};

const BASE_REPORT: Report = {
  "Login": "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL",
};

function normalize(text: string): string {
  return text.toLowerCase().normalize("NFD").replace(/\p{Diacritic}/gu, "").trim();
}

async function clickVisibleByText(page: Page, candidates: string[]): Promise<Locator> {
  for (const candidate of candidates) {
    const byRoleButton = page.getByRole("button", { name: new RegExp(candidate, "i") }).first();
    if (await byRoleButton.isVisible().catch(() => false)) {
      await byRoleButton.click();
      await page.waitForLoadState("networkidle");
      return byRoleButton;
    }

    const byText = page.getByText(new RegExp(candidate, "i")).first();
    if (await byText.isVisible().catch(() => false)) {
      await byText.click();
      await page.waitForLoadState("networkidle");
      return byText;
    }
  }

  throw new Error(`No visible clickable element found for candidates: ${candidates.join(", ")}`);
}

async function screenshot(page: Page, name: string): Promise<void> {
  const screenshotPath = path.join(process.cwd(), "e2e", "artifacts", "screenshots", `${name}.png`);
  fs.mkdirSync(path.dirname(screenshotPath), { recursive: true });
  await page.screenshot({
    path: screenshotPath,
    fullPage: true,
  });
}

async function assertTextVisible(page: Page, text: string): Promise<void> {
  const exact = page.getByText(text, { exact: false }).first();
  if (await exact.isVisible().catch(() => false)) {
    await expect(exact).toBeVisible();
    return;
  }

  const regex = page.getByText(new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i")).first();
  await expect(regex).toBeVisible();
}

async function validateHeadingOrMainContent(page: Page, text: string): Promise<void> {
  const heading = page.getByRole("heading", { name: new RegExp(text, "i") }).first();
  if (await heading.isVisible().catch(() => false)) {
    await expect(heading).toBeVisible();
    return;
  }
  await assertTextVisible(page, text);
}

async function assertLegalContentVisible(page: Page): Promise<void> {
  const legalMarkers = [
    /terminos|términos|politica|política|privacidad|condiciones|datos personales|responsabilidad/i,
    /aceptacion|aceptación|uso|servicio|derechos|obligaciones/i,
  ];
  const bodyText = (await page.locator("body").innerText()) ?? "";
  const hasLegalMarker = legalMarkers.some((marker) => marker.test(bodyText));
  expect(hasLegalMarker).toBeTruthy();
}

async function clickLegalLinkAndCapture(
  appPage: Page,
  linkText: string,
  screenshotName: string
): Promise<{ url: string; page: Page }> {
  const linkRegex = new RegExp(linkText, "i");
  const link = appPage.getByRole("link", { name: linkRegex }).first();
  let targetPage: Page = appPage;

  if (await link.isVisible().catch(() => false)) {
    const popupPromise = appPage.context().waitForEvent("page", { timeout: 4000 }).catch(() => null);
    await link.click();
    const popup = await popupPromise;
    targetPage = popup ?? appPage;
  } else {
    const fallback = appPage.getByText(linkRegex).first();
    const popupPromise = appPage.context().waitForEvent("page", { timeout: 4000 }).catch(() => null);
    await fallback.click();
    const popup = await popupPromise;
    targetPage = popup ?? appPage;
  }

  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForLoadState("networkidle");
  await screenshot(targetPage, screenshotName);
  return { url: targetPage.url(), page: targetPage };
}

function logReport(report: Report, termsUrl: string, privacyUrl: string): void {
  const outputDir = path.join(process.cwd(), "e2e", "artifacts");
  fs.mkdirSync(outputDir, { recursive: true });

  const finalReport = {
    ...report,
    "Términos y Condiciones URL": termsUrl,
    "Política de Privacidad URL": privacyUrl,
  };

  fs.writeFileSync(
    path.join(outputDir, "saleads_mi_negocio_full_test_report.json"),
    `${JSON.stringify(finalReport, null, 2)}\n`,
    "utf8"
  );
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page }) => {
    const report: Report = { ...BASE_REPORT };
    let termsUrl = "";
    let privacyUrl = "";
    try {
      // Optional navigation for environment-agnostic execution.
      if (page.url() === "about:blank") {
        const envUrl = process.env.SALEADS_BASE_URL ?? process.env.SALEADS_LOGIN_URL;
        if (envUrl) {
          await page.goto(envUrl, { waitUntil: "domcontentloaded" });
        }
      }

      // Step 1: Login with Google
      await page.waitForLoadState("domcontentloaded");
      await page.waitForLoadState("networkidle");

      const sidebarVisible = await page.locator("nav, aside").first().isVisible().catch(() => false);
      if (!sidebarVisible) {
        const loginCandidates = [
          "Sign in with Google",
          "Iniciar sesion con Google",
          "Iniciar sesión con Google",
          "Google",
          "Login",
          "Iniciar sesion",
          "Iniciar sesión",
        ];

        const popupPromise = page.context().waitForEvent("page", { timeout: 4000 }).catch(() => null);
        await clickVisibleByText(page, loginCandidates);
        const popup = await popupPromise;

        const googlePages = popup ? [popup, page] : page.context().pages();
        for (const currentPage of googlePages) {
          const googleAccount = currentPage
            .getByText("juanlucasbarbiergarzon@gmail.com", { exact: false })
            .first();
          if (await googleAccount.isVisible().catch(() => false)) {
            await googleAccount.click();
            await currentPage.waitForLoadState("networkidle");
            break;
          }
        }
      }

      await page.waitForLoadState("domcontentloaded");
      await page.waitForLoadState("networkidle");
      await expect(page.locator("nav, aside").first()).toBeVisible();
      await screenshot(page, "01_dashboard_loaded");
      report["Login"] = "PASS";

      // Step 2: Open Mi Negocio menu
      const negocioSection = page.getByText(/negocio/i).first();
      await expect(negocioSection).toBeVisible();
      await clickVisibleByText(page, ["Mi Negocio", "Mi negocio"]);
      await assertTextVisible(page, "Agregar Negocio");
      await assertTextVisible(page, "Administrar Negocios");
      await screenshot(page, "02_mi_negocio_menu_expanded");
      report["Mi Negocio menu"] = "PASS";

      // Step 3: Validate Agregar Negocio modal
      await clickVisibleByText(page, ["Agregar Negocio"]);
      await validateHeadingOrMainContent(page, "Crear Nuevo Negocio");
      await assertTextVisible(page, "Nombre del Negocio");
      await assertTextVisible(page, "Tienes 2 de 3 negocios");
      await assertTextVisible(page, "Cancelar");
      await assertTextVisible(page, "Crear Negocio");

      const input = page.getByLabel(/Nombre del Negocio/i).first();
      if (await input.isVisible().catch(() => false)) {
        await input.click();
        await input.fill("Negocio Prueba Automatización");
      } else {
        const inputByPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i).first();
        if (await inputByPlaceholder.isVisible().catch(() => false)) {
          await inputByPlaceholder.click();
          await inputByPlaceholder.fill("Negocio Prueba Automatización");
        }
      }

      await screenshot(page, "03_agregar_negocio_modal");
      await clickVisibleByText(page, ["Cancelar"]);
      report["Agregar Negocio modal"] = "PASS";

      // Step 4: Open Administrar Negocios
      const administrarNegocios = page.getByText(/Administrar Negocios/i).first();
      if (!(await administrarNegocios.isVisible().catch(() => false))) {
        await clickVisibleByText(page, ["Mi Negocio", "Mi negocio"]);
      }

      await clickVisibleByText(page, ["Administrar Negocios"]);
      await validateHeadingOrMainContent(page, "Información General");
      await assertTextVisible(page, "Detalles de la Cuenta");
      await assertTextVisible(page, "Tus Negocios");
      await assertTextVisible(page, "Sección Legal");
      await screenshot(page, "04_administrar_negocios_account_page");
      report["Administrar Negocios view"] = "PASS";

      // Step 5: Validate Información General
      const bodyText = normalize((await page.locator("body").innerText()) ?? "");
      expect(bodyText).toMatch(/business plan/);
      await assertTextVisible(page, "BUSINESS PLAN");
      await assertTextVisible(page, "Cambiar Plan");

      const emailPattern = /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/;
      expect(bodyText).toMatch(emailPattern);
      report["Información General"] = "PASS";

      // Step 6: Validate Detalles de la Cuenta
      await assertTextVisible(page, "Cuenta creada");
      await assertTextVisible(page, "Estado activo");
      await assertTextVisible(page, "Idioma seleccionado");
      report["Detalles de la Cuenta"] = "PASS";

      // Step 7: Validate Tus Negocios
      await assertTextVisible(page, "Tus Negocios");
      await assertTextVisible(page, "Agregar Negocio");
      await assertTextVisible(page, "Tienes 2 de 3 negocios");
      report["Tus Negocios"] = "PASS";

      // Step 8: Validate Términos y Condiciones
      const terms = await clickLegalLinkAndCapture(
        page,
        "Términos y Condiciones|Terminos y Condiciones",
        "05_terminos_y_condiciones"
      );
      await validateHeadingOrMainContent(terms.page, "Términos y Condiciones");
      await assertLegalContentVisible(terms.page);
      termsUrl = terms.url;
      report["Términos y Condiciones"] = "PASS";

      if (terms.page !== page) {
        await terms.page.close();
        await page.bringToFront();
        await page.waitForLoadState("networkidle");
      }

      // Step 9: Validate Política de Privacidad
      const privacy = await clickLegalLinkAndCapture(
        page,
        "Política de Privacidad|Politica de Privacidad",
        "06_politica_de_privacidad"
      );
      await validateHeadingOrMainContent(privacy.page, "Política de Privacidad");
      await assertLegalContentVisible(privacy.page);
      privacyUrl = privacy.url;
      report["Política de Privacidad"] = "PASS";

      if (privacy.page !== page) {
        await privacy.page.close();
        await page.bringToFront();
        await page.waitForLoadState("networkidle");
      }
    } finally {
      // Step 10: Final report
      logReport(report, termsUrl, privacyUrl);
      console.table(report);
      console.log("Términos y Condiciones URL:", termsUrl);
      console.log("Política de Privacidad URL:", privacyUrl);
    }
  });
});
