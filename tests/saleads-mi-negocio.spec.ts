import fs from "node:fs/promises";
import path from "node:path";
import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

type ReportStatus = "PASS" | "FAIL";

async function waitForUi(page: Page): Promise<void> {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 7_000 }),
    page.waitForTimeout(1_000),
  ]).catch(() => undefined);
  await page.waitForTimeout(250);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.scrollIntoViewIfNeeded();
  await locator.click();
  await waitForUi(page);
}

async function pickVisible(
  page: Page,
  candidates: Locator[],
  timeoutMs = 7_000,
): Promise<Locator | null> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const current = candidate.first();
      const visible = await current.isVisible().catch(() => false);
      if (visible) {
        return current;
      }
    }
    await page.waitForTimeout(200);
  }
  return null;
}

async function requireVisible(
  page: Page,
  description: string,
  candidates: Locator[],
  timeoutMs = 7_000,
): Promise<Locator> {
  const locator = await pickVisible(page, candidates, timeoutMs);
  if (!locator) {
    throw new Error(`Could not find visible element for: ${description}`);
  }
  await expect(locator).toBeVisible();
  return locator;
}

async function screenshot(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false,
): Promise<string> {
  const filePath = testInfo.outputPath(name);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function maybeSelectGoogleAccount(page: Page): Promise<void> {
  const account = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await account.isVisible().catch(() => false)) {
    await account.click();
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report: Record<ReportField, ReportStatus> = {
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
  const evidence: Record<string, string> = {};
  const legalUrls: Record<string, string> = {};
  const issues: string[] = [];

  const runStep = async (field: ReportField, fn: () => Promise<void>) => {
    try {
      await fn();
      report[field] = "PASS";
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      issues.push(`${field}: ${message}`);
      report[field] = "FAIL";
    }
  };

  await runStep("Login", async () => {
    if (page.url() === "about:blank") {
      const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
      if (!loginUrl) {
        throw new Error(
          "No login URL available. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL).",
        );
      }
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const sidebar = page.locator("aside").first();
    if (!(await sidebar.isVisible().catch(() => false))) {
      const loginButton = await requireVisible(page, "Google login button", [
        page.getByRole("button", { name: /google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/iniciar sesion con google/i),
      ]);

      const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await maybeSelectGoogleAccount(popup);
        await popup.waitForEvent("close", { timeout: 120_000 }).catch(() => undefined);
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await maybeSelectGoogleAccount(page);
      }
    }

    await expect(page.locator("aside").first()).toBeVisible();
    evidence.dashboard = await screenshot(page, testInfo, "01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    const sidebar = await requireVisible(page, "left sidebar", [page.locator("aside").first()]);
    const negocioSection = await requireVisible(page, "Negocio section", [
      sidebar.getByText(/^Negocio$/i),
      page.getByText(/^Negocio$/i),
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await requireVisible(page, "Mi Negocio option", [
      sidebar.getByText(/^Mi Negocio$/i),
      page.getByText(/^Mi Negocio$/i),
    ]);
    await clickAndWait(page, miNegocioOption);

    await requireVisible(page, "Agregar Negocio option", [
      sidebar.getByText(/^Agregar Negocio$/i),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await requireVisible(page, "Administrar Negocios option", [
      sidebar.getByText(/^Administrar Negocios$/i),
      page.getByText(/^Administrar Negocios$/i),
    ]);

    evidence.expandedMenu = await screenshot(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusiness = await requireVisible(page, "Agregar Negocio option", [
      page.getByText(/^Agregar Negocio$/i),
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
    ]);
    await clickAndWait(page, addBusiness);

    const modal = await requireVisible(page, "Crear Nuevo Negocio modal", [
      page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
      page.locator("[role='dialog']").filter({ hasText: /Crear Nuevo Negocio/i }),
    ]);

    await requireVisible(page, "Nombre del Negocio input", [
      modal.getByLabel(/Nombre del Negocio/i),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.locator("input").first(),
    ]);
    await requireVisible(page, "Tienes 2 de 3 negocios text", [
      modal.getByText(/Tienes 2 de 3 negocios/i),
      page.getByText(/Tienes 2 de 3 negocios/i),
    ]);
    const cancelButton = await requireVisible(page, "Cancelar button", [
      modal.getByRole("button", { name: /^Cancelar$/i }),
      page.getByRole("button", { name: /^Cancelar$/i }),
    ]);
    await requireVisible(page, "Crear Negocio button", [
      modal.getByRole("button", { name: /^Crear Negocio$/i }),
      page.getByRole("button", { name: /^Crear Negocio$/i }),
    ]);

    evidence.modal = await screenshot(page, testInfo, "03-agregar-negocio-modal.png");

    const nameInput = await requireVisible(page, "Nombre del Negocio input", [
      modal.getByLabel(/Nombre del Negocio/i),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.locator("input").first(),
    ]);
    await nameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, cancelButton);
    await expect(modal).toBeHidden();
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegocios = await requireVisible(page, "Administrar Negocios option", [
      page.getByText(/^Administrar Negocios$/i),
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
    ]);
    await clickAndWait(page, administrarNegocios);

    await requireVisible(page, "Informacion General section", [
      page.getByText(/Informacion General/i),
      page.getByText(/Informaci.n General/i),
    ]);
    await requireVisible(page, "Detalles de la Cuenta section", [
      page.getByText(/Detalles de la Cuenta/i),
    ]);
    await requireVisible(page, "Tus Negocios section", [page.getByText(/Tus Negocios/i)]);
    await requireVisible(page, "Seccion Legal section", [
      page.getByText(/Seccion Legal/i),
      page.getByText(/Secci.n Legal/i),
    ]);

    evidence.accountPage = await screenshot(
      page,
      testInfo,
      "04-administrar-negocios-page.png",
      true,
    );
  });

  await runStep("Informacion General", async () => {
    const section = await requireVisible(page, "Informacion General section", [
      page.locator("section,div").filter({ hasText: /Informaci.n General/i }).first(),
      page.getByText(/Informaci.n General/i),
    ]);

    await requireVisible(page, "user email", [
      section.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(".", "\\."), "i")),
      page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL.replace(".", "\\."), "i")),
      section.getByText(/@/),
    ]);
    await requireVisible(page, "user name", [
      section.getByText(/Nombre|Name|Usuario|User|Perfil/i),
      page.getByText(/Nombre|Name|Usuario|User|Perfil/i),
    ]);
    await requireVisible(page, "BUSINESS PLAN text", [
      section.getByText(/BUSINESS PLAN/i),
      page.getByText(/BUSINESS PLAN/i),
    ]);
    await requireVisible(page, "Cambiar Plan button", [
      section.getByRole("button", { name: /Cambiar Plan/i }),
      page.getByRole("button", { name: /Cambiar Plan/i }),
    ]);
  });

  await runStep("Detalles de la Cuenta", async () => {
    const section = await requireVisible(page, "Detalles de la Cuenta section", [
      page.locator("section,div").filter({ hasText: /Detalles de la Cuenta/i }).first(),
      page.getByText(/Detalles de la Cuenta/i),
    ]);

    await requireVisible(page, "Cuenta creada text", [
      section.getByText(/Cuenta creada/i),
      page.getByText(/Cuenta creada/i),
    ]);
    await requireVisible(page, "Estado activo text", [
      section.getByText(/Estado activo/i),
      page.getByText(/Estado activo/i),
    ]);
    await requireVisible(page, "Idioma seleccionado text", [
      section.getByText(/Idioma seleccionado/i),
      page.getByText(/Idioma seleccionado/i),
    ]);
  });

  await runStep("Tus Negocios", async () => {
    const section = await requireVisible(page, "Tus Negocios section", [
      page.locator("section,div").filter({ hasText: /Tus Negocios/i }).first(),
      page.getByText(/Tus Negocios/i),
    ]);

    await requireVisible(page, "Agregar Negocio button", [
      section.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await requireVisible(page, "Tienes 2 de 3 negocios text", [
      section.getByText(/Tienes 2 de 3 negocios/i),
      page.getByText(/Tienes 2 de 3 negocios/i),
    ]);

    const listCandidates = section.locator("li, tr, [class*='business'], [data-testid*='business']");
    const hasListItems = (await listCandidates.count()) > 0;
    const genericBusinessText = await pickVisible(page, [section.getByText(/Negocio/i)], 1_500);
    if (!hasListItems && !genericBusinessText) {
      throw new Error("Business list is not visible in Tus Negocios section.");
    }
  });

  const validateLegalLink = async (
    field: "Terminos y Condiciones" | "Politica de Privacidad",
    linkPattern: RegExp,
    headingPattern: RegExp,
    screenshotName: string,
    legalUrlKey: string,
  ) => {
    await runStep(field, async () => {
      const link = await requireVisible(page, `${field} link`, [
        page.getByRole("link", { name: linkPattern }),
        page.getByText(linkPattern),
      ]);

      const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(page, link);
      const popup = await popupPromise;

      const legalPage = popup ?? page;
      await legalPage.waitForLoadState("domcontentloaded");
      await waitForUi(legalPage);
      await expect(legalPage.getByText(headingPattern)).toBeVisible();

      const legalContent = legalPage.locator("main,article,body").getByText(/./).first();
      await expect(legalContent).toBeVisible();

      legalUrls[legalUrlKey] = legalPage.url();
      evidence[legalUrlKey] = await screenshot(legalPage, testInfo, screenshotName, true);

      if (popup) {
        await popup.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" });
        await waitForUi(page);
      }
    });
  };

  await validateLegalLink(
    "Terminos y Condiciones",
    /T.rminos y Condiciones/i,
    /T.rminos y Condiciones/i,
    "05-terminos-y-condiciones.png",
    "terminosYCondiciones",
  );
  await validateLegalLink(
    "Politica de Privacidad",
    /Pol.tica de Privacidad/i,
    /Pol.tica de Privacidad/i,
    "06-politica-de-privacidad.png",
    "politicaDePrivacidad",
  );

  const finalReportPath = testInfo.outputPath("mi-negocio-final-report.json");
  const finalReport = {
    workflow: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    statusByStep: report,
    issues,
    evidence,
    legalUrls,
  };
  await fs.mkdir(path.dirname(finalReportPath), { recursive: true });
  await fs.writeFile(finalReportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  const hasFailures = Object.values(report).some((status) => status === "FAIL");
  expect(hasFailures, `Workflow failures:\n${issues.join("\n")}`).toBe(false);
});
