import { expect, Locator, Page, test } from "@playwright/test";
import { promises as fs } from "node:fs";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

type ReportStatus = "PASS" | "FAIL";
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

async function waitForUi(page: Page): Promise<void> {
  await page.waitForTimeout(500);
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => undefined);
  await page.waitForLoadState("load", { timeout: 15000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => undefined);
}

async function firstVisibleLocator(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    const item = locator.first();
    if ((await item.count()) > 0 && (await item.isVisible().catch(() => false))) {
      return item;
    }
  }
  return null;
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function validateLegalLinkAndReturn(
  page: Page,
  linkNameRegex: RegExp,
  expectedHeadingRegex: RegExp,
  screenshotPath: string,
): Promise<string> {
  const link = await firstVisibleLocator([
    page.getByRole("link", { name: linkNameRegex }),
    page.getByText(linkNameRegex),
  ]);

  if (!link) {
    throw new Error(`No visible legal link found for ${linkNameRegex}.`);
  }

  const maybePopup = page.context().waitForEvent("page", { timeout: 5000 }).catch(() => null);
  await clickAndWait(link, page);

  const popup = await maybePopup;
  const targetPage = popup ?? page;

  await targetPage.waitForLoadState("domcontentloaded", { timeout: 30000 });
  await waitForUi(targetPage);

  await expect(targetPage.getByText(expectedHeadingRegex).first()).toBeVisible();
  const legalBodyText = (await targetPage.locator("body").innerText()).trim();
  expect(legalBodyText.length).toBeGreaterThan(120);

  await targetPage.screenshot({ path: screenshotPath, fullPage: true });
  const finalUrl = targetPage.url();
  expect(finalUrl).not.toBe("about:blank");

  if (popup) {
    await popup.close().catch(() => undefined);
    await page.bringToFront();
    await waitForUi(page);
  } else {
    const backToAppMarker = page
      .getByText(/Informaci[oó]n General|Detalles de la Cuenta|Tus Negocios/i)
      .first();
    if (!(await backToAppMarker.isVisible().catch(() => false))) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(page);
    }
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const stepReport: Record<ReportField, ReportStatus> = {
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
  const failures: string[] = [];
  const legalUrls: Record<"Términos y Condiciones" | "Política de Privacidad", string> = {
    "Términos y Condiciones": "",
    "Política de Privacidad": "",
  };

  const executeStep = async (field: ReportField, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      stepReport[field] = "PASS";
    } catch (error) {
      stepReport[field] = "FAIL";
      failures.push(`${field}: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  await executeStep("Login", async () => {
    const configuredUrl = process.env.SALEADS_URL ?? process.env.BASE_URL;
    if (configuredUrl) {
      await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else {
      await waitForUi(page);
      if (page.url() === "about:blank") {
        throw new Error("Set SALEADS_URL (or BASE_URL) or pre-open SaleADS login in the active browser context.");
      }
    }

    const loginTrigger = await firstVisibleLocator([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
    ]);

    if (!loginTrigger) {
      throw new Error("Google login button/link not found.");
    }

    const maybePopup = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(loginTrigger, page);
    const popup = await maybePopup;
    const authPage = popup ?? page;
    await authPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => undefined);

    const accountOption = await firstVisibleLocator([
      authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
      authPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      authPage.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
    ]);

    if (accountOption) {
      await clickAndWait(accountOption, authPage);
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(page.locator("main, [role='main']").first()).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true,
    });
  });

  await executeStep("Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible();

    const negocioSection = await firstVisibleLocator([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    if (negocioSection) {
      await clickAndWait(negocioSection, page);
    }

    const miNegocio = await firstVisibleLocator([
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ]);
    if (!miNegocio) {
      throw new Error("'Mi Negocio' option not visible.");
    }

    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"),
      fullPage: true,
    });
  });

  await executeStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisibleLocator([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    if (!agregarNegocio) {
      throw new Error("'Agregar Negocio' entry not visible.");
    }

    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const nombreNegocioInput = await firstVisibleLocator([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: "" }),
    ]);
    if (!nombreNegocioInput) {
      throw new Error("'Nombre del Negocio' input was not found.");
    }

    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("03-crear-nuevo-negocio-modal.png"),
      fullPage: true,
    });

    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /Cancelar/i }), page);
  });

  await executeStep("Administrar Negocios view", async () => {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      const miNegocio = await firstVisibleLocator([
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ]);
      if (!miNegocio) {
        throw new Error("Could not re-open 'Mi Negocio' menu.");
      }
      await clickAndWait(miNegocio, page);
    }

    const administrarNegocios = await firstVisibleLocator([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ]);
    if (!administrarNegocios) {
      throw new Error("'Administrar Negocios' option not visible.");
    }

    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-page.png"),
      fullPage: true,
    });
  });

  await executeStep("Información General", async () => {
    const infoHeading = page.getByText(/Informaci[oó]n General/i).first();
    await expect(infoHeading).toBeVisible();

    const infoContainer = page.locator("section, article, div").filter({ has: infoHeading }).first();
    const infoText = ((await infoContainer.textContent()) ?? "").replace(/\s+/g, " ").trim();
    expect(infoText).toMatch(/[A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,}/);
    expect(infoText).toMatch(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await executeStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await executeStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await executeStep("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await validateLegalLinkAndReturn(
      page,
      /T[ée]rminos y Condiciones/i,
      /T[ée]rminos y Condiciones/i,
      testInfo.outputPath("05-terminos-y-condiciones.png"),
    );
  });

  await executeStep("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await validateLegalLinkAndReturn(
      page,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      testInfo.outputPath("06-politica-de-privacidad.png"),
    );
  });

  const finalReport = {
    test: "saleads_mi_negocio_full_test",
    steps: stepReport,
    evidence: {
      termsUrl: legalUrls["Términos y Condiciones"],
      privacyUrl: legalUrls["Política de Privacidad"],
      outputDir: testInfo.outputDir,
    },
    failures,
  };

  const finalReportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");

  // Required checkpoint to provide the automation-level final report in test output.
  // eslint-disable-next-line no-console
  console.log("FINAL_REPORT", JSON.stringify(finalReport, null, 2));

  expect(failures, failures.join("\n")).toEqual([]);
});
