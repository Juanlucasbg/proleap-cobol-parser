import { expect, Locator, Page, test } from "@playwright/test";
import { writeFile } from "node:fs/promises";

type StepStatus = "PASS" | "FAIL";

type FinalReport = Record<
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad",
  StepStatus
>;

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function buildInitialReport(): FinalReport {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => undefined);
  await page.waitForTimeout(800);
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => undefined);
}

async function isVisible(locator: Locator, timeout = 4000): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function firstVisible(candidates: Locator[], timeout = 6000): Promise<Locator> {
  for (const candidate of candidates) {
    if (await isVisible(candidate, timeout)) {
      return candidate.first();
    }
  }

  throw new Error("No visible element found among selector candidates.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  const target = locator.first();
  await expect(target).toBeVisible();
  await target.click();
  await waitForUi(page);
}

async function saveCheckpoint(page: Page, fileName: string): Promise<void> {
  await page.screenshot({
    path: test.info().outputPath(fileName),
    fullPage: true
  });
}

async function selectGoogleAccountIfVisible(googlePage: Page): Promise<void> {
  const accountCandidate = googlePage.getByText(new RegExp(ACCOUNT_EMAIL, "i")).first();

  if (await isVisible(accountCandidate, 5000)) {
    await accountCandidate.click();
    await waitForUi(googlePage);
  }
}

async function ensureStartPage(page: Page): Promise<void> {
  const explicitStartUrl = process.env.SALEADS_START_URL;

  if (explicitStartUrl) {
    await page.goto(explicitStartUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "Missing start URL. Set SALEADS_START_URL to the current environment login page."
    );
  }
}

async function openSectionByHeading(page: Page, heading: RegExp): Promise<Locator> {
  const headingLocator = page.getByText(heading).first();
  await expect(headingLocator).toBeVisible();
  return headingLocator.locator("xpath=ancestor::*[self::section or self::div][1]");
}

async function validateLegalPageContent(targetPage: Page, titlePattern: RegExp): Promise<void> {
  await expect(targetPage.getByText(titlePattern).first()).toBeVisible();

  const pageText = await targetPage.locator("body").innerText();
  expect(pageText.replace(/\s+/g, " ").trim().length).toBeGreaterThan(120);
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report = buildInitialReport();
  const capturedUrls: Record<string, string> = {};
  const errors: string[] = [];

  try {
    await ensureStartPage(page);

    // Step 1: Login with Google
    const loginButton = await firstVisible([
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
      }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 12000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => undefined);
      await selectGoogleAccountIfVisible(popup);
    } else {
      await selectGoogleAccountIfVisible(page);
    }

    const sidebar = await firstVisible(
      [
        page.locator("aside"),
        page.locator("nav"),
        page.getByText(/negocio|dashboard|inicio|mi negocio/i)
      ],
      90000
    );
    await expect(sidebar).toBeVisible();
    report.Login = "PASS";
    await saveCheckpoint(page, "01-dashboard-loaded.png");

    // Step 2: Open Mi Negocio menu
    const negocioEntry = await firstVisible([
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i })
    ]);
    await clickAndWait(page, negocioEntry);

    const miNegocioEntry = await firstVisible([
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i })
    ]);
    await clickAndWait(page, miNegocioEntry);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    report["Mi Negocio menu"] = "PASS";
    await saveCheckpoint(page, "02-mi-negocio-menu-expanded.png");

    // Step 3: Validate Agregar Negocio modal
    await clickAndWait(page, page.getByText(/^Agregar Negocio$/i).first());
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();

    const negocioInput = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[type='text']"),
      page.locator("input")
    ]);

    await expect(negocioInput).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();
    await saveCheckpoint(page, "03-agregar-negocio-modal.png");

    // Optional actions
    await negocioInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }).first());
    report["Agregar Negocio modal"] = "PASS";

    // Step 4: Open Administrar Negocios
    if (!(await isVisible(page.getByText(/^Administrar Negocios$/i).first()))) {
      await clickAndWait(page, miNegocioEntry);
    }
    await clickAndWait(page, page.getByText(/^Administrar Negocios$/i).first());

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
    report["Administrar Negocios view"] = "PASS";
    await saveCheckpoint(page, "04-administrar-negocios-full-page.png");

    // Step 5: Validate Informacion General
    const infoSection = await openSectionByHeading(page, /Informaci[oó]n General/i);
    const infoText = (await infoSection.innerText())
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    const hasLikelyUserName = infoText.some(
      (line) =>
        /^[A-Za-zÀ-ÿ' -]{3,}$/.test(line) &&
        !/informaci[oó]n general|business plan|cambiar plan/i.test(line)
    );
    expect(hasLikelyUserName).toBeTruthy();
    await expect(
      infoSection
        .getByText(new RegExp(`${ACCOUNT_EMAIL}|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}`, "i"))
        .first()
    ).toBeVisible();
    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    report["Información General"] = "PASS";

    // Step 6: Validate Detalles de la Cuenta
    const accountDetailsSection = await openSectionByHeading(page, /Detalles de la Cuenta/i);
    await expect(accountDetailsSection.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(accountDetailsSection.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(accountDetailsSection.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    report["Detalles de la Cuenta"] = "PASS";

    // Step 7: Validate Tus Negocios
    const businessSection = await openSectionByHeading(page, /Tus Negocios/i);
    await expect(businessSection.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    const businessText = await businessSection.innerText();
    expect(businessText.replace(/\s+/g, " ").trim().length).toBeGreaterThan(60);
    report["Tus Negocios"] = "PASS";

    // Step 8: Validate Terminos y Condiciones (new tab or same tab)
    const termsLink = await firstVisible([
      page.getByRole("link", { name: /T[eé]rminos y Condiciones/i }),
      page.getByText(/T[eé]rminos y Condiciones/i)
    ]);
    const termsTabPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickAndWait(page, termsLink);

    const termsTab = await termsTabPromise;
    if (termsTab) {
      await termsTab.waitForLoadState("domcontentloaded", { timeout: 25000 });
      await waitForUi(termsTab);
      await validateLegalPageContent(termsTab, /T[eé]rminos y Condiciones/i);
      capturedUrls["Términos y Condiciones"] = termsTab.url();
      await termsTab.screenshot({
        path: test.info().outputPath("05-terminos-y-condiciones.png"),
        fullPage: true
      });
      await termsTab.close();
      await page.bringToFront();
    } else {
      await validateLegalPageContent(page, /T[eé]rminos y Condiciones/i);
      capturedUrls["Términos y Condiciones"] = page.url();
      await saveCheckpoint(page, "05-terminos-y-condiciones.png");
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(page);
    }
    report["Términos y Condiciones"] = "PASS";

    // Step 9: Validate Politica de Privacidad (new tab or same tab)
    const privacyLink = await firstVisible([
      page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }),
      page.getByText(/Pol[ií]tica de Privacidad/i)
    ]);
    const privacyTabPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickAndWait(page, privacyLink);

    const privacyTab = await privacyTabPromise;
    if (privacyTab) {
      await privacyTab.waitForLoadState("domcontentloaded", { timeout: 25000 });
      await waitForUi(privacyTab);
      await validateLegalPageContent(privacyTab, /Pol[ií]tica de Privacidad/i);
      capturedUrls["Política de Privacidad"] = privacyTab.url();
      await privacyTab.screenshot({
        path: test.info().outputPath("06-politica-de-privacidad.png"),
        fullPage: true
      });
      await privacyTab.close();
      await page.bringToFront();
    } else {
      await validateLegalPageContent(page, /Pol[ií]tica de Privacidad/i);
      capturedUrls["Política de Privacidad"] = page.url();
      await saveCheckpoint(page, "06-politica-de-privacidad.png");
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(page);
    }
    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    errors.push(message);
  }

  const finalReport = {
    report,
    urls: capturedUrls,
    errors
  };

  await writeFile(test.info().outputPath("final-report.json"), JSON.stringify(finalReport, null, 2), "utf8");
  await test.info().attach("final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });

  expect(errors, `Workflow failed. Report: ${JSON.stringify(finalReport, null, 2)}`).toEqual([]);
});
