import { expect, Locator, Page, test } from "@playwright/test";
import path from "node:path";
import { promises as fs } from "node:fs";

type ReportField =
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

const REPORT_FIELDS: ReportField[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function waitAnyVisible(name: string, locators: Locator[], timeoutMs = 15_000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of locators) {
      if (await candidate.first().isVisible().catch(() => false)) {
        return candidate.first();
      }
    }
    await locators[0].page().waitForTimeout(250);
  }

  throw new Error(`Could not find visible element for: ${name}`);
}

async function openLegalLinkAndValidate(
  appPage: Page,
  linkText: "Términos y Condiciones" | "Política de Privacidad",
  screenshotPath: string
): Promise<string> {
  const beforeUrl = appPage.url();
  const link = await waitAnyVisible(linkText, [
    appPage.getByRole("link", { name: new RegExp(linkText, "i") }),
    appPage.getByRole("button", { name: new RegExp(linkText, "i") }),
    appPage.getByText(new RegExp(`^${linkText}$`, "i"))
  ]);

  const popupPromise = appPage.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
  await clickAndWait(appPage, link);
  const popup = await popupPromise;

  const legalPage = popup ?? appPage;
  await waitForUi(legalPage);

  const heading = await waitAnyVisible(`${linkText} heading`, [
    legalPage.getByRole("heading", { name: new RegExp(linkText, "i") }),
    legalPage.getByText(new RegExp(linkText, "i"))
  ]);
  await expect(heading).toBeVisible();

  const legalContent = await waitAnyVisible(`${linkText} content`, [
    legalPage.locator("main p, article p, section p"),
    legalPage.locator("p"),
    legalPage.locator("li")
  ]);
  await expect(legalContent).toBeVisible();

  await legalPage.screenshot({ path: screenshotPath, fullPage: true });
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== beforeUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  if (process.env.SALEADS_URL) {
    await page.goto(process.env.SALEADS_URL, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);
  if (page.url() === "about:blank") {
    throw new Error(
      "No SaleADS URL loaded. Set SALEADS_URL or start the test when the page is already on the SaleADS login screen."
    );
  }

  const screenshotDir = path.join(testInfo.outputDir, "checkpoints");
  await fs.mkdir(screenshotDir, { recursive: true });

  const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])) as Record<ReportField, StepResult>;
  const failures: string[] = [];
  const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};

  const runStep = async (field: ReportField, executor: () => Promise<void>) => {
    try {
      await executor();
      results[field] = "PASS";
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      failures.push(`${field}: ${message}`);
      results[field] = "FAIL";
    }
  };

  await runStep("Login", async () => {
    const loginButton = await waitAnyVisible("Google login button", [
      page.getByRole("button", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i)
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    if (popup) {
      await waitForUi(popup);
      const account = popup.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      if (await account.isVisible().catch(() => false)) {
        await clickAndWait(popup, account);
      }
      await popup.waitForEvent("close", { timeout: 45_000 }).catch(() => {});
      await page.bringToFront();
    } else {
      const account = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      if (await account.isVisible().catch(() => false)) {
        await clickAndWait(page, account);
      }
    }

    await waitForUi(page);

    const sidebar = await waitAnyVisible("left sidebar", [page.locator("aside"), page.locator("nav")], 30_000);
    await expect(sidebar).toBeVisible();

    await page.screenshot({ path: path.join(screenshotDir, "01-dashboard-loaded.png"), fullPage: true });
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await waitAnyVisible("Negocio section", [
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /negocio/i })
    ]);
    await expect(negocioSection).toBeVisible();

    const miNegocio = await waitAnyVisible("Mi Negocio menu option", [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);
    await clickAndWait(page, miNegocio);

    const agregarNegocio = await waitAnyVisible("Agregar Negocio option", [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    const administrarNegocios = await waitAnyVisible("Administrar Negocios option", [
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);

    await expect(agregarNegocio).toBeVisible();
    await expect(administrarNegocios).toBeVisible();

    await page.screenshot({ path: path.join(screenshotDir, "02-mi-negocio-menu-expanded.png"), fullPage: true });
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await waitAnyVisible("Agregar Negocio option", [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await clickAndWait(page, agregarNegocio);

    const modalTitle = await waitAnyVisible("Crear Nuevo Negocio modal", [
      page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
      page.getByText(/Crear Nuevo Negocio/i)
    ]);
    await expect(modalTitle).toBeVisible();

    const nombreLabel = await waitAnyVisible("Nombre del Negocio input", [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[name*='nombre'], input[id*='nombre']")
    ]);
    await expect(nombreLabel).toBeVisible();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await nombreLabel.fill("Negocio Prueba Automatizacion");
    await page.screenshot({ path: path.join(screenshotDir, "03-agregar-negocio-modal.png"), fullPage: true });

    const cancelar = page.getByRole("button", { name: /Cancelar/i }).first();
    await clickAndWait(page, cancelar);
    await expect(modalTitle).not.toBeVisible();
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocio = await waitAnyVisible("Mi Negocio", [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);

    const administrarExists = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
    if (!administrarExists) {
      await clickAndWait(page, miNegocio);
    }

    const administrarNegocios = await waitAnyVisible("Administrar Negocios option", [
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();

    await page.screenshot({ path: path.join(screenshotDir, "04-administrar-negocios-page.png"), fullPage: true });
  });

  await runStep("Información General", async () => {
    await expect(
      page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first(),
      "Expected a visible user email in Informacion General"
    ).toBeVisible();

    const possibleName = await waitAnyVisible("user name indicator", [
      page.getByText(/nombre|usuario|user/i),
      page.locator("[data-testid*='name'], [id*='name']")
    ]);
    await expect(possibleName).toBeVisible();

    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(
      page.getByRole("button", { name: /^Agregar Negocio$/i }).first().or(page.getByText(/^Agregar Negocio$/i).first())
    ).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await openLegalLinkAndValidate(
      page,
      "Términos y Condiciones",
      path.join(screenshotDir, "05-terminos-y-condiciones.png")
    );
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await openLegalLinkAndValidate(
      page,
      "Política de Privacidad",
      path.join(screenshotDir, "06-politica-de-privacidad.png")
    );
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    timestamp: new Date().toISOString(),
    results,
    legalUrls
  };

  const finalReportPath = path.join(testInfo.outputDir, "saleads_mi_negocio_final_report.json");
  await fs.writeFile(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json"
  });

  if (failures.length > 0) {
    throw new Error(`One or more validation steps failed:\n- ${failures.join("\n- ")}`);
  }
});
