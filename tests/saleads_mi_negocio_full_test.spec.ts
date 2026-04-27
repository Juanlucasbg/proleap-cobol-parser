import { expect, test, type Locator, type Page } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type ReportStatus = "PASS" | "FAIL" | "SKIPPED";

type StepResult = {
  name: string;
  status: ReportStatus;
  details: string[];
  screenshots: string[];
  urls: string[];
};

const EVIDENCE_DIR = path.resolve(process.cwd(), "saleads-evidence", "saleads_mi_negocio_full_test");

const STEPS: Array<{ key: string; name: string }> = [
  { key: "login", name: "Login" },
  { key: "miNegocioMenu", name: "Mi Negocio menu" },
  { key: "agregarNegocioModal", name: "Agregar Negocio modal" },
  { key: "administrarNegociosView", name: "Administrar Negocios view" },
  { key: "informacionGeneral", name: "Información General" },
  { key: "detallesCuenta", name: "Detalles de la Cuenta" },
  { key: "tusNegocios", name: "Tus Negocios" },
  { key: "terminosCondiciones", name: "Términos y Condiciones" },
  { key: "politicaPrivacidad", name: "Política de Privacidad" },
];

const textToRegex = (text: string): RegExp => new RegExp(text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");

const stepResults = (): Record<string, StepResult> =>
  Object.fromEntries(
    STEPS.map((step) => [
      step.key,
      {
        name: step.name,
        status: "SKIPPED",
        details: [],
        screenshots: [],
        urls: [],
      } satisfies StepResult,
    ]),
  );

const addDetail = (result: StepResult, detail: string): void => {
  result.details.push(detail);
};

const setPass = (result: StepResult): void => {
  result.status = "PASS";
};

const setFail = (result: StepResult, detail: string): void => {
  result.status = "FAIL";
  addDetail(result, detail);
};

const setSkipped = (result: StepResult, detail: string): void => {
  result.status = "SKIPPED";
  addDetail(result, detail);
};

const takeEvidenceShot = async (
  page: Page,
  result: StepResult,
  fileName: string,
  fullPage = false,
): Promise<void> => {
  await fs.mkdir(EVIDENCE_DIR, { recursive: true });
  const shotPath = path.join(EVIDENCE_DIR, fileName);
  await page.screenshot({ path: shotPath, fullPage });
  result.screenshots.push(shotPath);
};

const hasVisibleLocator = async (locator: Locator): Promise<boolean> => {
  const count = await locator.count();
  for (let index = 0; index < count; index += 1) {
    if (await locator.nth(index).isVisible()) {
      return true;
    }
  }
  return false;
};

const firstVisible = async (locator: Locator): Promise<Locator> => {
  const count = await locator.count();
  for (let index = 0; index < count; index += 1) {
    const candidate = locator.nth(index);
    if (await candidate.isVisible()) {
      return candidate;
    }
  }
  throw new Error("No visible locator found.");
};

const clickVisibleByText = async (page: Page, textOptions: string[]): Promise<void> => {
  for (const textOption of textOptions) {
    const regex = textToRegex(textOption);
    const candidates = page.getByText(regex);
    if (await hasVisibleLocator(candidates)) {
      await (await firstVisible(candidates)).click();
      await page.waitForLoadState("domcontentloaded");
      return;
    }

    const roleCandidates = page.getByRole("button", { name: regex });
    if (await hasVisibleLocator(roleCandidates)) {
      await (await firstVisible(roleCandidates)).click();
      await page.waitForLoadState("domcontentloaded");
      return;
    }
  }

  throw new Error(`No visible element found for texts: ${textOptions.join(", ")}`);
};

const validateVisibleText = async (page: Page, value: string): Promise<void> => {
  await expect(page.getByText(textToRegex(value)).first()).toBeVisible();
};

const ensureMenuExpanded = async (page: Page): Promise<void> => {
  const agregar = page.getByText(textToRegex("Agregar Negocio"));
  const administrar = page.getByText(textToRegex("Administrar Negocios"));
  const expanded = (await hasVisibleLocator(agregar)) && (await hasVisibleLocator(administrar));
  if (!expanded) {
    await clickVisibleByText(page, ["Mi Negocio", "Negocio"]);
    await page.waitForTimeout(600);
  }
};

const openLegalAndValidate = async (
  page: Page,
  linkText: string,
  expectedTitle: string,
  fallbackSnippet: string,
  screenshotFile: string,
  result: StepResult,
): Promise<void> => {
  const link = page.getByRole("link", { name: textToRegex(linkText) }).first();
  await expect(link).toBeVisible();

  const [maybePopup] = await Promise.all([
    page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null),
    link.click(),
  ]);

  const legalPage = maybePopup ?? page;
  await legalPage.waitForLoadState("domcontentloaded");
  await expect(legalPage.getByText(textToRegex(expectedTitle)).first()).toBeVisible();
  await expect(legalPage.getByText(textToRegex(fallbackSnippet)).first()).toBeVisible();
  result.urls.push(legalPage.url());
  await takeEvidenceShot(legalPage, result, screenshotFile, true);

  if (maybePopup) {
    await maybePopup.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" });
  }
};

const writeSummary = async (results: Record<string, StepResult>): Promise<void> => {
  const summaryPath = path.join(EVIDENCE_DIR, "final-report.json");
  await fs.mkdir(EVIDENCE_DIR, { recursive: true });
  await fs.writeFile(
    summaryPath,
    JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        results,
      },
      null,
      2,
    ),
    "utf8",
  );
};

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page }) => {
    const results = stepResults();
    const loginStep = results.login;
    const menuStep = results.miNegocioMenu;
    const modalStep = results.agregarNegocioModal;
    const adminStep = results.administrarNegociosView;
    const infoStep = results.informacionGeneral;
    const detailsStep = results.detallesCuenta;
    const negociosStep = results.tusNegocios;
    const termsStep = results.terminosCondiciones;
    const privacyStep = results.politicaPrivacidad;

    try {
      const configuredUrl = process.env.SALEADS_LOGIN_URL;
      if (configuredUrl) {
        await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
      } else {
        addDetail(
          loginStep,
          "SALEADS_LOGIN_URL not provided. Assuming test started on SaleADS login page as requested.",
        );
      }

      // Step 1: Login with Google
      try {
        await clickVisibleByText(page, ["Sign in with Google", "Iniciar sesión con Google", "Continuar con Google"]);
        await page.waitForLoadState("networkidle");

        const googleAccount = page.getByText(textToRegex("juanlucasbarbiergarzon@gmail.com")).first();
        if (await googleAccount.isVisible({ timeout: 3_000 }).catch(() => false)) {
          await googleAccount.click();
          await page.waitForLoadState("domcontentloaded");
          await page.waitForLoadState("networkidle");
        }

        await expect(page.locator("aside")).toBeVisible();
        setPass(loginStep);
        await takeEvidenceShot(page, loginStep, "01-dashboard-loaded.png");
      } catch (error) {
        setFail(loginStep, `Login flow failed: ${(error as Error).message}`);
      }

      // Step 2: Open Mi Negocio menu
      if (loginStep.status === "PASS") {
        try {
          await clickVisibleByText(page, ["Mi Negocio", "Negocio"]);
          await page.waitForTimeout(500);
          await validateVisibleText(page, "Agregar Negocio");
          await validateVisibleText(page, "Administrar Negocios");
          setPass(menuStep);
          await takeEvidenceShot(page, menuStep, "02-mi-negocio-menu-expanded.png");
        } catch (error) {
          setFail(menuStep, `Mi Negocio menu validation failed: ${(error as Error).message}`);
        }
      } else {
        setSkipped(menuStep, "Skipped because Login failed.");
      }

      // Step 3: Validate Agregar Negocio modal
      if (menuStep.status === "PASS") {
        try {
          await clickVisibleByText(page, ["Agregar Negocio"]);
          await validateVisibleText(page, "Crear Nuevo Negocio");
          await validateVisibleText(page, "Nombre del Negocio");
          await validateVisibleText(page, "Tienes 2 de 3 negocios");
          await validateVisibleText(page, "Cancelar");
          await validateVisibleText(page, "Crear Negocio");

          const input = page.getByLabel(textToRegex("Nombre del Negocio")).first();
          if (await input.isVisible().catch(() => false)) {
            await input.fill("Negocio Prueba Automatización");
          } else {
            const fallbackInput = page.getByPlaceholder(textToRegex("Nombre del Negocio")).first();
            if (await fallbackInput.isVisible().catch(() => false)) {
              await fallbackInput.fill("Negocio Prueba Automatización");
            }
          }

          await takeEvidenceShot(page, modalStep, "03-agregar-negocio-modal.png");
          await clickVisibleByText(page, ["Cancelar"]);
          setPass(modalStep);
        } catch (error) {
          setFail(modalStep, `Agregar Negocio modal validation failed: ${(error as Error).message}`);
        }
      } else {
        setSkipped(modalStep, "Skipped because Mi Negocio menu failed.");
      }

      // Step 4: Open Administrar Negocios
      if (menuStep.status === "PASS") {
        try {
          await ensureMenuExpanded(page);
          await clickVisibleByText(page, ["Administrar Negocios"]);
          await page.waitForLoadState("domcontentloaded");
          await page.waitForLoadState("networkidle");

          await validateVisibleText(page, "Información General");
          await validateVisibleText(page, "Detalles de la Cuenta");
          await validateVisibleText(page, "Tus Negocios");
          await validateVisibleText(page, "Sección Legal");
          setPass(adminStep);
          await takeEvidenceShot(page, adminStep, "04-administrar-negocios-full-page.png", true);
        } catch (error) {
          setFail(adminStep, `Administrar Negocios view validation failed: ${(error as Error).message}`);
        }
      } else {
        setSkipped(adminStep, "Skipped because Mi Negocio menu failed.");
      }

      // Step 5: Validate Información General
      if (adminStep.status === "PASS") {
        try {
          const visibleInInfo = async (label: string): Promise<void> => {
            await expect(page.getByText(textToRegex(label)).first()).toBeVisible();
          };
          await visibleInInfo("@");
          await visibleInInfo("BUSINESS PLAN");
          await visibleInInfo("Cambiar Plan");
          setPass(infoStep);
        } catch (error) {
          setFail(infoStep, `Información General validation failed: ${(error as Error).message}`);
        }
      } else {
        setSkipped(infoStep, "Skipped because Administrar Negocios view failed.");
      }

      // Step 6: Validate Detalles de la Cuenta
      if (adminStep.status === "PASS") {
        try {
          await validateVisibleText(page, "Cuenta creada");
          await validateVisibleText(page, "Estado activo");
          await validateVisibleText(page, "Idioma seleccionado");
          setPass(detailsStep);
        } catch (error) {
          setFail(detailsStep, `Detalles de la Cuenta validation failed: ${(error as Error).message}`);
        }
      } else {
        setSkipped(detailsStep, "Skipped because Administrar Negocios view failed.");
      }

      // Step 7: Validate Tus Negocios
      if (adminStep.status === "PASS") {
        try {
          await validateVisibleText(page, "Tus Negocios");
          await validateVisibleText(page, "Agregar Negocio");
          await validateVisibleText(page, "Tienes 2 de 3 negocios");
          setPass(negociosStep);
        } catch (error) {
          setFail(negociosStep, `Tus Negocios validation failed: ${(error as Error).message}`);
        }
      } else {
        setSkipped(negociosStep, "Skipped because Administrar Negocios view failed.");
      }

      // Step 8: Validate Términos y Condiciones
      if (adminStep.status === "PASS") {
        try {
          await openLegalAndValidate(
            page,
            "Términos y Condiciones",
            "Términos y Condiciones",
            "Condiciones",
            "08-terminos-y-condiciones.png",
            termsStep,
          );
          setPass(termsStep);
        } catch (error) {
          setFail(termsStep, `Términos y Condiciones validation failed: ${(error as Error).message}`);
        }
      } else {
        setSkipped(termsStep, "Skipped because Administrar Negocios view failed.");
      }

      // Step 9: Validate Política de Privacidad
      if (adminStep.status === "PASS") {
        try {
          await openLegalAndValidate(
            page,
            "Política de Privacidad",
            "Política de Privacidad",
            "Privacidad",
            "09-politica-de-privacidad.png",
            privacyStep,
          );
          setPass(privacyStep);
        } catch (error) {
          setFail(privacyStep, `Política de Privacidad validation failed: ${(error as Error).message}`);
        }
      } else {
        setSkipped(privacyStep, "Skipped because Administrar Negocios view failed.");
      }
    } finally {
      await writeSummary(results);
      const hardFailures = Object.values(results).filter((step) => step.status === "FAIL");
      expect(
        hardFailures.length,
        `One or more workflow steps failed. See ${path.join(EVIDENCE_DIR, "final-report.json")}.`,
      ).toBe(0);
    }
  });
});
