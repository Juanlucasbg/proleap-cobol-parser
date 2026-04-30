import { expect, test, type Locator, type Page } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type Status = "PASS" | "FAIL";

type CheckResult = {
  status: Status;
  details: string;
};

type Report = {
  Login: CheckResult;
  "Mi Negocio menu": CheckResult;
  "Agregar Negocio modal": CheckResult;
  "Administrar Negocios view": CheckResult;
  "Información General": CheckResult;
  "Detalles de la Cuenta": CheckResult;
  "Tus Negocios": CheckResult;
  "Términos y Condiciones": CheckResult;
  "Política de Privacidad": CheckResult;
};

const report: Report = {
  Login: { status: "FAIL", details: "Not executed." },
  "Mi Negocio menu": { status: "FAIL", details: "Not executed." },
  "Agregar Negocio modal": { status: "FAIL", details: "Not executed." },
  "Administrar Negocios view": { status: "FAIL", details: "Not executed." },
  "Información General": { status: "FAIL", details: "Not executed." },
  "Detalles de la Cuenta": { status: "FAIL", details: "Not executed." },
  "Tus Negocios": { status: "FAIL", details: "Not executed." },
  "Términos y Condiciones": { status: "FAIL", details: "Not executed." },
  "Política de Privacidad": { status: "FAIL", details: "Not executed." },
};

function markPass(field: keyof Report, details: string) {
  report[field] = { status: "PASS", details };
}

function markFail(field: keyof Report, details: unknown) {
  const printable = details instanceof Error ? details.message : String(details);
  report[field] = { status: "FAIL", details: printable };
}

async function waitForUiAfterClick(page: Page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

async function firstVisible(locatorCandidates: Locator[]): Promise<Locator> {
  for (const candidate of locatorCandidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      return candidate.first();
    }
  }
  throw new Error("No visible candidate found.");
}

async function clickByText(page: Page, labels: RegExp[]) {
  const candidates = labels.map((label) =>
    page
      .getByRole("button", { name: label })
      .or(page.getByRole("link", { name: label }))
      .or(page.getByText(label))
  );
  const target = await firstVisible(candidates);
  await target.click();
  await waitForUiAfterClick(page);
}

async function ensureSidebarVisible(page: Page) {
  const sidebar = await firstVisible([
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator('[class*="sidebar"]'),
  ]);
  await expect(sidebar).toBeVisible();
}

async function capture(page: Page, name: string, fullPage = false) {
  await page.screenshot({ path: `evidence/${name}.png`, fullPage });
}

async function openLegalAndValidate(
  page: Page,
  label: RegExp,
  expectedHeading: RegExp,
  evidenceName: string,
  field: keyof Report
) {
  let newPage: Page | null = null;
  const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);

  await clickByText(page, [label]);
  newPage = await popupPromise;

  const targetPage = newPage ?? page;
  await targetPage.waitForLoadState("domcontentloaded");
  await expect(targetPage.getByRole("heading", { name: expectedHeading }).first()).toBeVisible();
  await expect(targetPage.locator("main, article, body")).toContainText(
    expectedHeading,
    { timeout: 15_000 }
  );

  const finalUrl = targetPage.url();
  await capture(targetPage, evidenceName, true);
  markPass(field, `Validated heading and legal content. URL: ${finalUrl}`);

  if (newPage) {
    await newPage.close();
    await page.bringToFront();
    await waitForUiAfterClick(page);
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test.afterAll(async () => {
    const targetDir = path.resolve("evidence");
    await fs.mkdir(targetDir, { recursive: true });
    await fs.writeFile(
      path.join(targetDir, "final-report.json"),
      JSON.stringify(
        {
          test: "saleads_mi_negocio_full_test",
          generatedAt: new Date().toISOString(),
          results: report,
        },
        null,
        2
      ),
      "utf8"
    );
  });

  test("Login with Google and validate Mi Negocio workflow", async ({ page }) => {
    test.setTimeout(240_000);
    await fs.mkdir(path.resolve("evidence"), { recursive: true });

    try {
      // Step 1: assumes current environment login page is already open.
      await clickByText(page, [/sign in with google/i, /continuar con google/i, /google/i]);
      const accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false });
      if (await accountOption.first().isVisible().catch(() => false)) {
        await accountOption.first().click();
        await waitForUiAfterClick(page);
      }

      await expect
        .poll(async () => page.url(), { timeout: 40_000 })
        .not.toMatch(/login|signin|auth/i);
      await ensureSidebarVisible(page);
      await capture(page, "01-dashboard-loaded", true);
      markPass("Login", "Main interface and left sidebar are visible.");
    } catch (error) {
      markFail("Login", error);
      throw error;
    }

    try {
      // Step 2: open Negocio -> Mi Negocio and validate submenu.
      await clickByText(page, [/negocio/i]);
      await clickByText(page, [/mi negocio/i]);
      await expect(page.getByText(/agregar negocio/i)).toBeVisible();
      await expect(page.getByText(/administrar negocios/i)).toBeVisible();
      await capture(page, "02-mi-negocio-menu-expanded");
      markPass("Mi Negocio menu", "Submenu expanded with expected entries.");
    } catch (error) {
      markFail("Mi Negocio menu", error);
      throw error;
    }

    try {
      // Step 3: validate Agregar Negocio modal.
      await clickByText(page, [/agregar negocio/i]);
      await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
      await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      const nameInput = page.getByLabel(/nombre del negocio/i);
      await nameInput.fill("Negocio Prueba Automatización");
      await capture(page, "03-agregar-negocio-modal");
      await page.getByRole("button", { name: /cancelar/i }).click();
      await waitForUiAfterClick(page);
      markPass("Agregar Negocio modal", "Modal and required fields/buttons validated.");
    } catch (error) {
      markFail("Agregar Negocio modal", error);
      throw error;
    }

    try {
      // Step 4: open Administrar Negocios account page.
      if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
        await clickByText(page, [/mi negocio/i]);
      }
      await clickByText(page, [/administrar negocios/i]);

      await expect(page.getByText(/información general/i)).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/sección legal/i)).toBeVisible();

      await capture(page, "04-administrar-negocios", true);
      markPass("Administrar Negocios view", "All required sections are visible.");
    } catch (error) {
      markFail("Administrar Negocios view", error);
      throw error;
    }

    try {
      // Step 5: validate Informacion General.
      await expect(page.getByText(/business plan/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
      await expect(page.getByText(/@/)).toBeVisible();
      await expect(page.locator("h1, h2, h3, p, span").filter({ hasText: /[A-Za-z]/ }).first()).toBeVisible();
      markPass("Información General", "Name/email indicators, plan label, and button visible.");
    } catch (error) {
      markFail("Información General", error);
      throw error;
    }

    try {
      // Step 6: validate Detalles de la Cuenta.
      await expect(page.getByText(/cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/estado activo/i)).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
      markPass("Detalles de la Cuenta", "Required account detail labels visible.");
    } catch (error) {
      markFail("Detalles de la Cuenta", error);
      throw error;
    }

    try {
      // Step 7: validate Tus Negocios.
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      markPass("Tus Negocios", "Business list area, action button, and count are visible.");
    } catch (error) {
      markFail("Tus Negocios", error);
      throw error;
    }

    try {
      // Step 8: validate Terminos y Condiciones.
      await openLegalAndValidate(
        page,
        /términos y condiciones|terminos y condiciones/i,
        /términos y condiciones|terminos y condiciones/i,
        "08-terminos-y-condiciones",
        "Términos y Condiciones"
      );
    } catch (error) {
      markFail("Términos y Condiciones", error);
      throw error;
    }

    try {
      // Step 9: validate Politica de Privacidad.
      await openLegalAndValidate(
        page,
        /política de privacidad|politica de privacidad/i,
        /política de privacidad|politica de privacidad/i,
        "09-politica-de-privacidad",
        "Política de Privacidad"
      );
    } catch (error) {
      markFail("Política de Privacidad", error);
      throw error;
    }
  });
});
