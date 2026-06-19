import fs from "node:fs/promises";
import { expect, type Locator, type Page, test } from "@playwright/test";

type ReportStatus = "PASS" | "FAIL";

type ValidationReport = {
  Login: ReportStatus;
  "Mi Negocio menu": ReportStatus;
  "Agregar Negocio modal": ReportStatus;
  "Administrar Negocios view": ReportStatus;
  "Información General": ReportStatus;
  "Detalles de la Cuenta": ReportStatus;
  "Tus Negocios": ReportStatus;
  "Términos y Condiciones": ReportStatus;
  "Política de Privacidad": ReportStatus;
};

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function buildInitialReport(): ValidationReport {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };
}

function fileSafe(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
  await page.waitForTimeout(700);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 20_000 });
  await locator.click();
  await waitForUi(page);
}

async function checkpoint(page: Page, label: string): Promise<void> {
  await fs.mkdir("test-results/checkpoints", { recursive: true });
  await page.screenshot({
    path: `test-results/checkpoints/${fileSafe(label)}.png`,
    fullPage: true,
  });
}

async function findByText(page: Page, pattern: RegExp): Promise<Locator> {
  const byRoleButton = page.getByRole("button", { name: pattern }).first();
  if (await byRoleButton.isVisible().catch(() => false)) {
    return byRoleButton;
  }

  const byRoleLink = page.getByRole("link", { name: pattern }).first();
  if (await byRoleLink.isVisible().catch(() => false)) {
    return byRoleLink;
  }

  return page.locator("button, a, [role='button'], [role='menuitem']").filter({ hasText: pattern }).first();
}

async function sectionByTitle(page: Page, titlePattern: RegExp): Promise<Locator> {
  const heading = page.getByRole("heading", { name: titlePattern }).first();
  if (await heading.isVisible().catch(() => false)) {
    const sectionFromHeading = heading.locator("xpath=ancestor-or-self::section[1]");
    if (await sectionFromHeading.count()) {
      return sectionFromHeading;
    }
  }

  return page.locator("section, div").filter({ hasText: titlePattern }).first();
}

test("saleads mi negocio full workflow", async ({ page }) => {
  const report = buildInitialReport();
  const failures: string[] = [];
  const capturedUrls: Record<string, string> = {};
  const startUrl = process.env.SALEADS_START_URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url().startsWith("about:blank")) {
    throw new Error(
      "No login page available. Set SALEADS_START_URL to the environment login page URL.",
    );
  }

  const runValidation = async (label: keyof ValidationReport, action: () => Promise<void>) => {
    try {
      await action();
      report[label] = "PASS";
    } catch (error) {
      report[label] = "FAIL";
      failures.push(`${label}: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  await runValidation("Login", async () => {
    const loginButton = await findByText(
      page,
      /sign in with google|iniciar sesion con google|ingresar con google|continuar con google|google/i,
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    const googleContext = popup ?? page;
    await waitForUi(googleContext);

    const accountOption = googleContext.getByText(ACCOUNT_EMAIL, { exact: false }).first();
    if (await accountOption.isVisible({ timeout: 7_000 }).catch(() => false)) {
      await accountOption.click();
      await waitForUi(googleContext);
    }

    if (popup) {
      await Promise.race([
        popup.waitForEvent("close", { timeout: 25_000 }),
        page.waitForLoadState("domcontentloaded", { timeout: 25_000 }),
      ]).catch(() => undefined);
      await waitForUi(page);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
    await checkpoint(page, "01-dashboard-loaded");
  });

  await runValidation("Mi Negocio menu", async () => {
    const negocioEntry = await findByText(page, /negocio/i);
    await clickAndWait(page, negocioEntry);

    const miNegocioEntry = await findByText(page, /mi negocio/i);
    await clickAndWait(page, miNegocioEntry);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
    await checkpoint(page, "02-mi-negocio-menu-expanded");
  });

  await runValidation("Agregar Negocio modal", async () => {
    const agregarNegocio = page.getByText(/agregar negocio/i).first();
    await clickAndWait(page, agregarNegocio);

    const modal = page.locator("[role='dialog'], .modal").filter({ hasText: /crear nuevo negocio/i }).first();
    await expect(modal).toBeVisible({ timeout: 15_000 });

    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(modal.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();
    await checkpoint(page, "03-agregar-negocio-modal");

    await modal.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, modal.getByRole("button", { name: /cancelar/i }));
  });

  await runValidation("Administrar Negocios view", async () => {
    const miNegocio = await findByText(page, /mi negocio/i);
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      await clickAndWait(page, miNegocio);
    }

    const administrarNegocios = page.getByText(/administrar negocios/i).first();
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/informacion general|informaci[oó]n general/i).first()).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/seccion legal|secci[oó]n legal/i).first()).toBeVisible();
    await checkpoint(page, "04-administrar-negocios-full-page");
  });

  await runValidation("Información General", async () => {
    const infoSection = await sectionByTitle(page, /informacion general|informaci[oó]n general/i);
    await expect(infoSection).toBeVisible();
    const sectionText = await infoSection.innerText();

    const emailMatch = sectionText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    if (!emailMatch) {
      throw new Error("User email was not found in Información General.");
    }

    const probableNameText = sectionText.replace(emailMatch[0], "").replace(/\s+/g, " ").trim();
    if (probableNameText.length < 3) {
      throw new Error("User name was not detected in Información General.");
    }

    await expect(infoSection.getByText(/business plan/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    const detailsSection = await sectionByTitle(page, /detalles de la cuenta/i);
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    const negociosSection = await sectionByTitle(page, /tus negocios/i);
    await expect(negociosSection).toBeVisible();

    const listCandidates = negociosSection.locator("li, [role='listitem'], .card, tr");
    if ((await listCandidates.count()) < 1) {
      throw new Error("Business list is not visible in Tus Negocios.");
    }

    await expect(negociosSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(negociosSection.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
  });

  const validateLegalLink = async (
    reportKey: "Términos y Condiciones" | "Política de Privacidad",
    linkTextPattern: RegExp,
    headingPattern: RegExp,
    screenshotName: string,
  ) => {
    await runValidation(reportKey, async () => {
      const legalSection = await sectionByTitle(page, /seccion legal|secci[oó]n legal/i);
      await expect(legalSection).toBeVisible();

      const legalLink = legalSection.getByText(linkTextPattern).first();
      const popupPromise = page.waitForEvent("popup", { timeout: 7_000 }).catch(() => null);
      await clickAndWait(page, legalLink);

      const popup = await popupPromise;
      const legalPage = popup ?? page;
      await waitForUi(legalPage);

      await expect(legalPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible({
        timeout: 20_000,
      });

      const legalBodyText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
      if (legalBodyText.length < 80) {
        throw new Error("Legal content text is not visible.");
      }

      await fs.mkdir("test-results/checkpoints", { recursive: true });
      await legalPage.screenshot({
        path: `test-results/checkpoints/${fileSafe(screenshotName)}.png`,
        fullPage: true,
      });
      capturedUrls[reportKey] = legalPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
        await waitForUi(page);
      }
    });
  };

  await validateLegalLink(
    "Términos y Condiciones",
    /terminos y condiciones|t[eé]rminos y condiciones/i,
    /terminos y condiciones|t[eé]rminos y condiciones/i,
    "05-terminos-y-condiciones",
  );

  await validateLegalLink(
    "Política de Privacidad",
    /politica de privacidad|pol[ií]tica de privacidad/i,
    /politica de privacidad|pol[ií]tica de privacidad/i,
    "06-politica-de-privacidad",
  );

  const finalReport = {
    report,
    capturedUrls,
    failures,
  };

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  expect.soft(Object.values(report).every((status) => status === "PASS")).toBeTruthy();
  if (failures.length > 0) {
    throw new Error(`Validation failures:\n${failures.join("\n")}`);
  }
});
