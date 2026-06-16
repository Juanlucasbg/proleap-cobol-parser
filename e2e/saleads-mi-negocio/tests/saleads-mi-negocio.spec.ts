import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

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

type StepStatus = "PASS" | "FAIL";

interface StepResult {
  status: StepStatus;
  details: string[];
}

const REPORT_FIELDS: ReportField[] = [
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

const SCREENSHOT_DIR = path.join("test-results", "screenshots");
const REPORT_JSON = path.join("test-results", "saleads-mi-negocio-report.json");
const REPORT_MD = path.join("test-results", "saleads-mi-negocio-report.md");

function initializeReport(): Record<ReportField, StepResult> {
  return REPORT_FIELDS.reduce(
    (accumulator, field) => ({
      ...accumulator,
      [field]: {
        status: "FAIL",
        details: ["Not executed."],
      },
    }),
    {} as Record<ReportField, StepResult>,
  );
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await page.waitForTimeout(700);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function takeCheckpoint(page: Page, fileName: string, fullPage = false): Promise<void> {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const screenshotPath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
}

async function findClickableByText(page: Page, pattern: RegExp): Promise<Locator> {
  const roleLocator = page
    .getByRole("button", { name: pattern })
    .or(page.getByRole("link", { name: pattern }))
    .first();
  if (await roleLocator.isVisible().catch(() => false)) {
    return roleLocator;
  }

  return page.locator("button, a, [role='button']").filter({ hasText: pattern }).first();
}

async function maybeSelectGoogleAccount(page: Page, email: string): Promise<boolean> {
  const emailOption = page.getByText(email, { exact: true });
  if (await emailOption.isVisible({ timeout: 8_000 }).catch(() => false)) {
    await emailOption.click();
    await page.waitForLoadState("networkidle").catch(() => undefined);
    return true;
  }

  return false;
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarNegocioItem = page.getByText("Agregar Negocio", { exact: true }).first();
  if (await agregarNegocioItem.isVisible().catch(() => false)) {
    return;
  }

  const miNegocioToggle = await findClickableByText(page, /mi negocio/i);
  await clickAndWait(miNegocioToggle, page);
}

function markBlocked(
  report: Record<ReportField, StepResult>,
  fields: ReportField[],
  reason: string,
): void {
  for (const field of fields) {
    report[field] = {
      status: "FAIL",
      details: [reason],
    };
  }
}

test("saleads_mi_negocio_full_test", async ({ context, page }) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const accountEmail =
    process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
  const legalUrls: Record<"terms" | "privacy", string> = { terms: "", privacy: "" };
  const report = initializeReport();

  fs.mkdirSync("test-results", { recursive: true });
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

  let blockRemaining = false;

  try {
    // Step 1: Login with Google
    try {
      if (!loginUrl) {
        throw new Error(
          "SALEADS_LOGIN_URL is required for portable runs. Set it to the login page of your environment.",
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);

      const googleLoginButton = await findClickableByText(
        page,
        /sign in with google|iniciar sesion con google|continuar con google|google/i,
      );
      const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);

      await clickAndWait(googleLoginButton, page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await maybeSelectGoogleAccount(popup, accountEmail);
        await popup.waitForEvent("close", { timeout: 120_000 }).catch(() => undefined);
        await page.bringToFront();
      } else {
        await maybeSelectGoogleAccount(page, accountEmail);
      }

      await waitForUi(page);
      await expect(page.locator("aside, nav").first()).toBeVisible();
      await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible();
      await takeCheckpoint(page, "01-dashboard-loaded.png");

      report.Login = {
        status: "PASS",
        details: ["Main interface and left navigation are visible after Google login."],
      };
    } catch (error) {
      blockRemaining = true;
      report.Login = {
        status: "FAIL",
        details: [String(error)],
      };
      markBlocked(
        report,
        [
          "Mi Negocio menu",
          "Agregar Negocio modal",
          "Administrar Negocios view",
          "Información General",
          "Detalles de la Cuenta",
          "Tus Negocios",
          "Términos y Condiciones",
          "Política de Privacidad",
        ],
        "Blocked because login step failed.",
      );
    }

    // Step 2: Open Mi Negocio menu
    if (!blockRemaining) {
      try {
        const miNegocioToggle = await findClickableByText(page, /mi negocio/i);
        await clickAndWait(miNegocioToggle, page);

        await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible();
        await expect(page.getByText("Administrar Negocios", { exact: true }).first()).toBeVisible();
        await takeCheckpoint(page, "02-mi-negocio-menu-expanded.png");

        report["Mi Negocio menu"] = {
          status: "PASS",
          details: ["Mi Negocio submenu expanded and expected options are visible."],
        };
      } catch (error) {
        blockRemaining = true;
        report["Mi Negocio menu"] = {
          status: "FAIL",
          details: [String(error)],
        };
        markBlocked(
          report,
          [
            "Agregar Negocio modal",
            "Administrar Negocios view",
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Términos y Condiciones",
            "Política de Privacidad",
          ],
          "Blocked because Mi Negocio menu step failed.",
        );
      }
    }

    // Step 3: Validate Agregar Negocio modal
    if (!blockRemaining) {
      try {
        const agregarNegocioMenuItem = page.getByText("Agregar Negocio", { exact: true }).first();
        await clickAndWait(agregarNegocioMenuItem, page);

        await expect(page.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
        const businessNameInput = page.getByLabel("Nombre del Negocio", { exact: true });
        await expect(businessNameInput).toBeVisible();
        await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Cancelar", exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Crear Negocio", exact: true })).toBeVisible();

        await businessNameInput.click();
        await businessNameInput.fill("Negocio Prueba Automatizacion");
        await takeCheckpoint(page, "03-agregar-negocio-modal.png");
        await clickAndWait(page.getByRole("button", { name: "Cancelar", exact: true }), page);

        report["Agregar Negocio modal"] = {
          status: "PASS",
          details: ["Modal and controls were validated successfully."],
        };
      } catch (error) {
        blockRemaining = true;
        report["Agregar Negocio modal"] = {
          status: "FAIL",
          details: [String(error)],
        };
        markBlocked(
          report,
          [
            "Administrar Negocios view",
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Términos y Condiciones",
            "Política de Privacidad",
          ],
          "Blocked because Agregar Negocio modal validation failed.",
        );
      }
    }

    // Step 4: Open Administrar Negocios
    if (!blockRemaining) {
      try {
        await ensureMiNegocioExpanded(page);
        const administrarNegociosItem = page.getByText("Administrar Negocios", { exact: true }).first();
        await clickAndWait(administrarNegociosItem, page);

        await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
        await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
        await expect(page.getByText(/tus negocios/i)).toBeVisible();
        await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();
        await takeCheckpoint(page, "04-administrar-negocios.png", true);

        report["Administrar Negocios view"] = {
          status: "PASS",
          details: ["Account page loaded with all expected sections."],
        };
      } catch (error) {
        blockRemaining = true;
        report["Administrar Negocios view"] = {
          status: "FAIL",
          details: [String(error)],
        };
        markBlocked(
          report,
          [
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Términos y Condiciones",
            "Política de Privacidad",
          ],
          "Blocked because Administrar Negocios view failed.",
        );
      }
    }

    // Step 5: Validate Informacion General
    if (!blockRemaining) {
      try {
        await expect(page.getByText(/@/).first()).toBeVisible();
        await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
        await expect(page.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();

        report["Información General"] = {
          status: "PASS",
          details: ["User info, plan label, and change plan action are visible."],
        };
      } catch (error) {
        report["Información General"] = {
          status: "FAIL",
          details: [String(error)],
        };
      }
    }

    // Step 6: Validate Detalles de la Cuenta
    if (!blockRemaining) {
      try {
        await expect(page.getByText("Cuenta creada", { exact: true })).toBeVisible();
        await expect(page.getByText("Estado activo", { exact: true })).toBeVisible();
        await expect(page.getByText("Idioma seleccionado", { exact: true })).toBeVisible();

        report["Detalles de la Cuenta"] = {
          status: "PASS",
          details: ["Account detail labels are visible."],
        };
      } catch (error) {
        report["Detalles de la Cuenta"] = {
          status: "FAIL",
          details: [String(error)],
        };
      }
    }

    // Step 7: Validate Tus Negocios
    if (!blockRemaining) {
      try {
        await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Agregar Negocio", exact: true })).toBeVisible();
        await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();

        report["Tus Negocios"] = {
          status: "PASS",
          details: ["Business list container and controls are visible."],
        };
      } catch (error) {
        report["Tus Negocios"] = {
          status: "FAIL",
          details: [String(error)],
        };
      }
    }

    // Step 8: Validate Terminos y Condiciones
    if (!blockRemaining) {
      try {
        const terminosLink = await findClickableByText(page, /t[eé]rminos y condiciones/i);
        const newTabPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
        await clickAndWait(terminosLink, page);
        const legalPage = (await newTabPromise) ?? page;

        await waitForUi(legalPage);
        await expect(
          legalPage.getByRole("heading", { name: /t[eé]rminos y condiciones/i }),
        ).toBeVisible();
        const legalText = await legalPage.locator("body").innerText();
        expect(legalText.trim().length).toBeGreaterThan(120);
        legalUrls.terms = legalPage.url();
        await takeCheckpoint(legalPage, "05-terminos-y-condiciones.png");

        if (legalPage !== page) {
          await legalPage.close();
          await page.bringToFront();
        } else {
          await page.goBack().catch(() => undefined);
          await waitForUi(page);
        }

        report["Términos y Condiciones"] = {
          status: "PASS",
          details: [`Legal page validated. URL: ${legalUrls.terms}`],
        };
      } catch (error) {
        report["Términos y Condiciones"] = {
          status: "FAIL",
          details: [String(error)],
        };
      }
    }

    // Step 9: Validate Politica de Privacidad
    if (!blockRemaining) {
      try {
        const privacidadLink = await findClickableByText(page, /pol[ií]tica de privacidad/i);
        const newTabPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
        await clickAndWait(privacidadLink, page);
        const legalPage = (await newTabPromise) ?? page;

        await waitForUi(legalPage);
        await expect(
          legalPage.getByRole("heading", { name: /pol[ií]tica de privacidad/i }),
        ).toBeVisible();
        const legalText = await legalPage.locator("body").innerText();
        expect(legalText.trim().length).toBeGreaterThan(120);
        legalUrls.privacy = legalPage.url();
        await takeCheckpoint(legalPage, "06-politica-de-privacidad.png");

        if (legalPage !== page) {
          await legalPage.close();
          await page.bringToFront();
        } else {
          await page.goBack().catch(() => undefined);
          await waitForUi(page);
        }

        report["Política de Privacidad"] = {
          status: "PASS",
          details: [`Privacy page validated. URL: ${legalUrls.privacy}`],
        };
      } catch (error) {
        report["Política de Privacidad"] = {
          status: "FAIL",
          details: [String(error)],
        };
      }
    }
  } finally {
    const output = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      report,
      legalUrls,
    };

    fs.writeFileSync(REPORT_JSON, JSON.stringify(output, null, 2), "utf-8");

    const mdLines: string[] = [
      "# SaleADS Mi Negocio Full Test Report",
      "",
      `Generated at: ${output.generatedAt}`,
      "",
      "| Step | Status | Details |",
      "| --- | --- | --- |",
    ];

    for (const field of REPORT_FIELDS) {
      mdLines.push(`| ${field} | ${report[field].status} | ${report[field].details.join(" ")} |`);
    }

    mdLines.push("");
    mdLines.push(`- Terminos y Condiciones URL: ${legalUrls.terms || "N/A"}`);
    mdLines.push(`- Politica de Privacidad URL: ${legalUrls.privacy || "N/A"}`);
    mdLines.push("");
    fs.writeFileSync(REPORT_MD, mdLines.join("\n"), "utf-8");
  }

  const failures = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failures,
    `One or more validations failed. Review ${REPORT_JSON} and screenshots in ${SCREENSHOT_DIR}.`,
  ).toEqual([]);
});
