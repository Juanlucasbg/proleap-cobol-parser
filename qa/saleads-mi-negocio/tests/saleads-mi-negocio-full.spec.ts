import { expect, type Locator, type Page, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type ValidationStatus = "PASS" | "FAIL";
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

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const REQUIRED_FIELDS: ReportField[] = [
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

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => undefined);
  await page.waitForLoadState("networkidle", { timeout: 12_000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function firstVisible(locators: Locator[], timeoutMs = 20_000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;
  const firstPage = locators[0]?.page();

  while (Date.now() < deadline) {
    for (const locator of locators) {
      const current = locator.first();
      try {
        if (await current.isVisible()) {
          return current;
        }
      } catch {
        // Keep polling until timeout.
      }
    }
    if (firstPage) {
      await firstPage.waitForTimeout(250);
    }
  }

  throw new Error("No visible locator found before timeout.");
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToLoad(page);
}

async function captureCheckpoint(page: Page, artifactDir: string, fileName: string): Promise<string> {
  const fullPath = path.join(artifactDir, fileName);
  await page.screenshot({ path: fullPath, fullPage: true });
  return fullPath;
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const adminVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
  if (adminVisible) {
    return;
  }

  const negocio = await firstVisible([
    page.getByRole("button", { name: /^Negocio$/i }),
    page.getByRole("link", { name: /^Negocio$/i }),
    page.getByText(/^Negocio$/i),
  ]);
  await clickAndWait(negocio, page);

  const miNegocio = await firstVisible([
    page.getByRole("button", { name: /^Mi Negocio$/i }),
    page.getByRole("link", { name: /^Mi Negocio$/i }),
    page.getByText(/^Mi Negocio$/i),
  ]);
  await clickAndWait(miNegocio, page);
}

async function openLegalDocument(
  page: Page,
  linkRegex: RegExp,
  expectedHeadingRegex: RegExp,
  screenshotName: string,
  artifactDir: string,
): Promise<string> {
  const appUrlBeforeClick = page.url();
  const link = await firstVisible([
    page.getByRole("link", { name: linkRegex }),
    page.getByText(linkRegex),
  ]);

  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await link.click();
  await waitForUiToLoad(page);

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => undefined);
    await waitForUiToLoad(popup);
  }

  const legalHeading = await firstVisible(
    [
      legalPage.getByRole("heading", { name: expectedHeadingRegex }),
      legalPage.getByText(expectedHeadingRegex),
    ],
    30_000,
  );
  await expect(legalHeading).toBeVisible();

  const bodyContent = (await legalPage.locator("body").innerText()).trim();
  expect(bodyContent.length).toBeGreaterThan(50);

  await captureCheckpoint(legalPage, artifactDir, screenshotName);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else if (page.url() !== appUrlBeforeClick) {
    const goBackWorked = await page
      .goBack({ waitUntil: "domcontentloaded", timeout: 20_000 })
      .then(() => true)
      .catch(() => false);
    if (!goBackWorked && appUrlBeforeClick.startsWith("http")) {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    }
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const artifactsDir = path.resolve(process.cwd(), "artifacts", "saleads-mi-negocio");
  await mkdir(artifactsDir, { recursive: true });

  const report: Record<ReportField, ValidationStatus> = Object.fromEntries(
    REQUIRED_FIELDS.map((field) => [field, "FAIL"]),
  ) as Record<ReportField, ValidationStatus>;
  const failures: Partial<Record<ReportField, string>> = {};
  const evidence: Record<string, string> = {};

  const markResult = async (field: ReportField, validation: () => Promise<void>) => {
    try {
      await validation();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      failures[field] = error instanceof Error ? error.message : String(error);
    }
  };

  if (page.url() === "about:blank") {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (!loginUrl) {
      throw new Error(
        "SALEADS_LOGIN_URL is required when the browser starts on about:blank. Set this to the current environment login page.",
      );
    }
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  }
  await waitForUiToLoad(page);

  await markResult("Login", async () => {
    const loginButton = await firstVisible([
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
      }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
    ]);

    const googlePopupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const googlePopup = await googlePopupPromise;
    const googleContextPage = googlePopup ?? page;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => undefined);
      await waitForUiToLoad(googlePopup);
    }

    const accountOption = await firstVisible(
      [
        googleContextPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        googleContextPage.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
      ],
      15_000,
    ).catch(() => null);
    if (accountOption) {
      await clickAndWait(accountOption, googleContextPage);
    }
    if (googlePopup) {
      await googlePopup.waitForEvent("close", { timeout: 45_000 }).catch(() => undefined);
      await page.bringToFront();
    }
    await waitForUiToLoad(page);

    await expect(page.locator("aside, [role='navigation'], [class*='sidebar']").first()).toBeVisible({
      timeout: 45_000,
    });
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 45_000 });

    evidence.dashboardScreenshot = await captureCheckpoint(page, artifactsDir, "01-dashboard-loaded.png");
  });

  await markResult("Mi Negocio menu", async () => {
    const negocioSection = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    await clickAndWait(negocioSection, page);

    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    evidence.miNegocioMenuScreenshot = await captureCheckpoint(page, artifactsDir, "02-mi-negocio-expanded-menu.png");
  });

  await markResult("Agregar Negocio modal", async () => {
    const addBusiness = await firstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await clickAndWait(addBusiness, page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    const businessNameInput = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[type='text']"),
    ]);
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    evidence.agregarNegocioModalScreenshot = await captureCheckpoint(page, artifactsDir, "03-agregar-negocio-modal.png");

    const nameInput = businessNameInput;
    await nameInput.click();
    await waitForUiToLoad(page);
    await nameInput.fill("Negocio Prueba Automatización");

    const cancelButton = await firstVisible([page.getByRole("button", { name: /^Cancelar$/i })]);
    await clickAndWait(cancelButton, page);
  });

  await markResult("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);

    const adminBusinesses = await firstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    await clickAndWait(adminBusinesses, page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    evidence.administrarNegociosScreenshot = await captureCheckpoint(
      page,
      artifactsDir,
      "04-administrar-negocios-full-page.png",
    );
  });

  await markResult("Información General", async () => {
    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const potentialName = await firstVisible(
      [
        page.getByText(/Nombre/i),
        page.getByText(/Usuario/i),
        page.locator("h1, h2, h3").filter({ hasText: /[A-Za-zÁÉÍÓÚÑ]{3,}/ }),
      ],
      15_000,
    );
    await expect(potentialName).toBeVisible();
  });

  await markResult("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await markResult("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

    const businessList = await firstVisible([
      page.locator("[role='listitem']").filter({ hasText: /\S/ }),
      page.locator("tr").filter({ hasText: /\S/ }),
      page.locator("[class*='business']").filter({ hasText: /\S/ }),
    ]);
    await expect(businessList).toBeVisible();
  });

  await markResult("Términos y Condiciones", async () => {
    evidence.termsFinalUrl = await openLegalDocument(
      page,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      "05-terminos-y-condiciones.png",
      artifactsDir,
    );
    await ensureMiNegocioExpanded(page);
  });

  await markResult("Política de Privacidad", async () => {
    evidence.privacyFinalUrl = await openLegalDocument(
      page,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
      "06-politica-de-privacidad.png",
      artifactsDir,
    );
    await ensureMiNegocioExpanded(page);
  });

  const reportFile = path.join(artifactsDir, "final-report.json");
  await writeFile(
    reportFile,
    `${JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        report,
        failures,
        evidence,
      },
      null,
      2,
    )}\n`,
    "utf-8",
  );

  console.log("SaleADS Mi Negocio validation report:");
  console.table(report);
  console.log(`Detailed report: ${reportFile}`);

  const failedFields = REQUIRED_FIELDS.filter((field) => report[field] === "FAIL");
  expect(
    failedFields,
    `Some required validations failed: ${failedFields.join(", ")}. Check ${reportFile} for details.`,
  ).toHaveLength(0);
});
