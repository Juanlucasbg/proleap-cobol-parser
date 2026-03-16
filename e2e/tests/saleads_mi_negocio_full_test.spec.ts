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

type StepResult = {
  status: StepStatus;
  details: string;
};

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

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function compactTextLines(text: string): string[] {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
}

async function waitForUiToLoad(targetPage: Page): Promise<void> {
  await targetPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {
    // Some SPA transitions do not trigger a fresh domcontentloaded event.
  });
  await targetPage.waitForLoadState("networkidle", { timeout: 5_000 }).catch(() => {
    // Network may stay active due to polling; continue after a brief pause.
  });
  await targetPage.waitForTimeout(800);
}

async function waitForVisibleByTexts(
  targetPage: Page,
  texts: string[],
  timeoutMs = 30_000
): Promise<Locator> {
  const endTime = Date.now() + timeoutMs;

  while (Date.now() < endTime) {
    for (const text of texts) {
      const exact = targetPage.getByText(new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i")).first();
      if (await exact.isVisible().catch(() => false)) {
        return exact;
      }

      const contains = targetPage.getByText(new RegExp(escapeRegExp(text), "i")).first();
      if (await contains.isVisible().catch(() => false)) {
        return contains;
      }

      const button = targetPage.getByRole("button", { name: new RegExp(escapeRegExp(text), "i") }).first();
      if (await button.isVisible().catch(() => false)) {
        return button;
      }

      const link = targetPage.getByRole("link", { name: new RegExp(escapeRegExp(text), "i") }).first();
      if (await link.isVisible().catch(() => false)) {
        return link;
      }
    }

    await targetPage.waitForTimeout(300);
  }

  throw new Error(`Could not find a visible element with any text: ${texts.join(", ")}`);
}

async function clickVisibleTextAndWait(targetPage: Page, texts: string[]): Promise<void> {
  const element = await waitForVisibleByTexts(targetPage, texts);
  await element.click();
  await waitForUiToLoad(targetPage);
}

async function firstVisibleLocator(
  locators: Locator[],
  timeoutMs: number,
  label: string
): Promise<Locator> {
  const endTime = Date.now() + timeoutMs;

  while (Date.now() < endTime) {
    for (const locator of locators) {
      if (await locator.first().isVisible().catch(() => false)) {
        return locator.first();
      }
    }
    await locators[0].page().waitForTimeout(300);
  }

  throw new Error(`Could not find visible locator for: ${label}`);
}

async function findSectionFromHeading(targetPage: Page, heading: string): Promise<Locator> {
  const headingLocator = await waitForVisibleByTexts(targetPage, [heading], 20_000);
  return headingLocator.locator("xpath=ancestor::*[self::section or self::div][1]");
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: Record<ReportField, StepResult> = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed." }])
  ) as Record<ReportField, StepResult>;

  const failures: string[] = [];
  const runtimeDir = path.join(testInfo.outputDir, `saleads-mi-negocio-${Date.now()}`);
  fs.mkdirSync(runtimeDir, { recursive: true });

  let termsAndConditionsUrl = "";
  let privacyPolicyUrl = "";

  const captureCheckpoint = async (name: string, targetPage: Page = page, fullPage = false): Promise<void> => {
    const screenshotPath = path.join(runtimeDir, `${name}.png`);
    await targetPage.screenshot({ path: screenshotPath, fullPage });
    await testInfo.attach(name, { path: screenshotPath, contentType: "image/png" });
  };

  const runValidation = async (field: ReportField, action: () => Promise<string>): Promise<void> => {
    try {
      const detail = await action();
      report[field] = { status: "PASS", details: detail };
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      report[field] = { status: "FAIL", details: detail };
      failures.push(`${field}: ${detail}`);
      await captureCheckpoint(`failure-${field.replace(/\s+/g, "-").toLowerCase()}`).catch(() => {
        // Screenshot best effort on failure.
      });
    }
  };

  await runValidation("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL ?? "";

    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    } else if (page.url() === "about:blank") {
      throw new Error("Set SALEADS_LOGIN_URL/SALEADS_BASE_URL or start this test on the SaleADS login page.");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickVisibleTextAndWait(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Iniciar sesion con Google",
      "Continuar con Google",
      "Google"
    ]);

    const googlePage = await popupPromise;
    const authPage = googlePage ?? page;
    await waitForUiToLoad(authPage);

    const accountEmail = "juanlucasbarbiergarzon@gmail.com";
    const accountOption = authPage.getByText(accountEmail, { exact: false }).first();
    if (await accountOption.isVisible({ timeout: 12_000 }).catch(() => false)) {
      await accountOption.click();
      await waitForUiToLoad(authPage);
    }

    if (googlePage) {
      await googlePage.waitForEvent("close", { timeout: 60_000 }).catch(() => {
        // Some auth implementations keep the tab open and redirect in place.
      });
      await page.bringToFront();
      await waitForUiToLoad(page);
    }

    await waitForVisibleByTexts(page, ["Negocio", "Mi Negocio", "Dashboard"], 90_000);

    await firstVisibleLocator(
      [page.locator("aside"), page.getByRole("navigation"), page.locator("[class*='sidebar']")],
      30_000,
      "left sidebar navigation"
    );

    await captureCheckpoint("step1-dashboard-loaded");
    return "Main interface and left sidebar are visible.";
  });

  await runValidation("Mi Negocio menu", async () => {
    await firstVisibleLocator(
      [page.locator("aside"), page.getByRole("navigation"), page.locator("[class*='sidebar']")],
      20_000,
      "left sidebar navigation"
    );

    const negocioSection = await waitForVisibleByTexts(page, ["Negocio"], 20_000);
    await negocioSection.click();
    await waitForUiToLoad(page);

    await clickVisibleTextAndWait(page, ["Mi Negocio"]);
    await waitForVisibleByTexts(page, ["Agregar Negocio"], 20_000);
    await waitForVisibleByTexts(page, ["Administrar Negocios"], 20_000);
    await captureCheckpoint("step2-mi-negocio-expanded");

    return "Mi Negocio submenu expanded and both options are visible.";
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickVisibleTextAndWait(page, ["Agregar Negocio"]);
    await waitForVisibleByTexts(page, ["Crear Nuevo Negocio"], 20_000);

    const businessNameInput = await firstVisibleLocator(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("xpath=//*[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")
      ],
      20_000,
      "Nombre del Negocio input"
    );

    await waitForVisibleByTexts(page, ["Tienes 2 de 3 negocios"], 10_000);
    await waitForVisibleByTexts(page, ["Cancelar"], 10_000);
    await waitForVisibleByTexts(page, ["Crear Negocio"], 10_000);

    await captureCheckpoint("step3-agregar-negocio-modal");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickVisibleTextAndWait(page, ["Cancelar"]);

    return "Modal fields, quota text, and action buttons validated.";
  });

  await runValidation("Administrar Negocios view", async () => {
    const adminOption = page.getByText(/Administrar Negocios/i).first();
    if (!(await adminOption.isVisible().catch(() => false))) {
      await clickVisibleTextAndWait(page, ["Mi Negocio"]);
    }

    await clickVisibleTextAndWait(page, ["Administrar Negocios"]);

    await waitForVisibleByTexts(page, ["Información General"], 30_000);
    await waitForVisibleByTexts(page, ["Detalles de la Cuenta"], 30_000);
    await waitForVisibleByTexts(page, ["Tus Negocios"], 30_000);
    await waitForVisibleByTexts(page, ["Sección Legal", "Seccion Legal"], 30_000);
    await captureCheckpoint("step4-administrar-negocios-account-page", page, true);

    return "All expected account sections are visible.";
  });

  await runValidation("Información General", async () => {
    const infoSection = await findSectionFromHeading(page, "Información General");

    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible({
      timeout: 20_000
    });
    await waitForVisibleByTexts(page, ["BUSINESS PLAN"], 20_000);
    await waitForVisibleByTexts(page, ["Cambiar Plan"], 20_000);

    const infoText = compactTextLines(await infoSection.innerText());
    const probableUserName = infoText.find((line) => {
      return (
        line.length >= 3 &&
        !/@/.test(line) &&
        !/información general|business plan|cambiar plan|plan/i.test(line)
      );
    });

    if (!probableUserName) {
      throw new Error("Could not detect a visible user name in Información General.");
    }

    return "User name, user email, BUSINESS PLAN and Cambiar Plan are visible.";
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await findSectionFromHeading(page, "Detalles de la Cuenta");
    await waitForVisibleByTexts(page, ["Cuenta creada"], 20_000);
    await waitForVisibleByTexts(page, ["Estado activo"], 20_000);
    await waitForVisibleByTexts(page, ["Idioma seleccionado"], 20_000);

    return "Cuenta creada, Estado activo and Idioma seleccionado are visible.";
  });

  await runValidation("Tus Negocios", async () => {
    const businessSection = await findSectionFromHeading(page, "Tus Negocios");

    await waitForVisibleByTexts(page, ["Agregar Negocio"], 20_000);
    await waitForVisibleByTexts(page, ["Tienes 2 de 3 negocios"], 20_000);

    const listCount =
      (await businessSection.locator("li").count()) +
      (await businessSection.locator("[role='listitem']").count()) +
      (await businessSection.locator("tbody tr").count()) +
      (await businessSection.locator("article").count()) +
      (await businessSection.locator("[class*='business']").count()) +
      (await businessSection.locator("[class*='negocio']").count());

    if (listCount === 0) {
      const lines = compactTextLines(await businessSection.innerText()).filter(
        (line) => !/tus negocios|agregar negocio|tienes 2 de 3 negocios/i.test(line)
      );
      if (lines.length === 0) {
        throw new Error("No visible business list items were detected in Tus Negocios.");
      }
    }

    return "Business list, Agregar Negocio button and quota text are visible.";
  });

  const validateLegalDocument = async (
    field: "Términos y Condiciones" | "Política de Privacidad",
    linkTexts: string[],
    headingTexts: string[],
    screenshotName: string
  ): Promise<string> => {
    const appUrlBeforeClick = page.url();
    const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);

    await clickVisibleTextAndWait(page, linkTexts);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => {
      // Continue with page text validation.
    });
    await waitForUiToLoad(legalPage);

    await waitForVisibleByTexts(legalPage, headingTexts, 30_000);
    const bodyContent = (await legalPage.locator("body").innerText()).trim();
    if (bodyContent.length < 150) {
      throw new Error(`${field} content appears too short to be a complete legal document.`);
    }

    await captureCheckpoint(screenshotName, legalPage, true);
    const finalUrl = legalPage.url();

    if (popup) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUiToLoad(page);
    } else {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    }

    return finalUrl;
  };

  await runValidation("Términos y Condiciones", async () => {
    termsAndConditionsUrl = await validateLegalDocument(
      "Términos y Condiciones",
      ["Términos y Condiciones", "Terminos y Condiciones"],
      ["Términos y Condiciones", "Terminos y Condiciones"],
      "step8-terminos-y-condiciones"
    );

    return `Heading and legal content are visible. Final URL: ${termsAndConditionsUrl}`;
  });

  await runValidation("Política de Privacidad", async () => {
    privacyPolicyUrl = await validateLegalDocument(
      "Política de Privacidad",
      ["Política de Privacidad", "Politica de Privacidad"],
      ["Política de Privacidad", "Politica de Privacidad"],
      "step9-politica-de-privacidad"
    );

    return `Heading and legal content are visible. Final URL: ${privacyPolicyUrl}`;
  });

  const reportPayload = {
    test: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    finalUrls: {
      terminosYCondiciones: termsAndConditionsUrl,
      politicaDePrivacidad: privacyPolicyUrl
    }
  };

  const reportPath = path.join(runtimeDir, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(reportPayload, null, 2), "utf8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

  // Keep a readable summary in test logs for quick triage.
  // eslint-disable-next-line no-console
  console.table(
    REPORT_FIELDS.map((field) => ({
      field,
      status: report[field].status,
      details: report[field].details
    }))
  );

  expect(
    failures,
    `Workflow validation failed:\n${failures.map((failure) => `- ${failure}`).join("\n")}`
  ).toEqual([]);
});
