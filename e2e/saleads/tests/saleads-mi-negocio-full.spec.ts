import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import path from "node:path";
import { promises as fs } from "node:fs";

type ReportStatus = "PASS" | "FAIL" | "NOT_RUN";
type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const visible = await candidate.first().isVisible({ timeout: 2_500 }).catch(() => false);
    if (visible) {
      return candidate.first();
    }
  }
  return null;
}

async function requiredVisible(candidates: Locator[], errorMessage: string): Promise<Locator> {
  const found = await firstVisible(candidates);
  expect(found, errorMessage).not.toBeNull();
  return found as Locator;
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.click({ timeout: 20_000 });
  await waitForUi(page);
}

async function captureCheckpoint(
  name: string,
  page: Page,
  testInfo: TestInfo,
  options: { fullPage?: boolean } = {}
): Promise<string> {
  const checkpointsDir = path.join(testInfo.outputDir, "checkpoints");
  await fs.mkdir(checkpointsDir, { recursive: true });
  const screenshotPath = path.join(checkpointsDir, `${name}.png`);

  await page.screenshot({
    path: screenshotPath,
    fullPage: options.fullPage ?? false
  });

  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png"
  });

  return screenshotPath;
}

test("saleads_mi_negocio_full_test", async ({ page, baseURL }, testInfo) => {
  const report: Record<ReportKey, ReportStatus> = {
    Login: "NOT_RUN",
    "Mi Negocio menu": "NOT_RUN",
    "Agregar Negocio modal": "NOT_RUN",
    "Administrar Negocios view": "NOT_RUN",
    "Información General": "NOT_RUN",
    "Detalles de la Cuenta": "NOT_RUN",
    "Tus Negocios": "NOT_RUN",
    "Términos y Condiciones": "NOT_RUN",
    "Política de Privacidad": "NOT_RUN"
  };

  const failures: string[] = [];
  const legalUrls: Partial<Record<"terms" | "privacy", string>> = {};

  const setStepResult = (key: ReportKey, status: ReportStatus, reason?: unknown) => {
    report[key] = status;
    if (status === "FAIL" && reason) {
      const message = reason instanceof Error ? reason.message : String(reason);
      failures.push(`${key}: ${message}`);
    }
  };

  const runStep = async (key: ReportKey, fn: () => Promise<void>) => {
    try {
      await fn();
      setStepResult(key, "PASS");
    } catch (error) {
      setStepResult(key, "FAIL", error);
    }
  };

  await runStep("Login", async () => {
    if (baseURL) {
      await page.goto(baseURL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No login URL provided. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL, or pre-open the SaleADS login page."
      );
    }

    const googleButton = await requiredVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
      ],
      "Google login button was not found."
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(googleButton, page);
    const popup = await popupPromise;

    const googlePage = popup ?? page;
    await googlePage.waitForLoadState("domcontentloaded");
    await googlePage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);

    const accountCandidate = await firstVisible([
      googlePage.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")),
      googlePage.getByRole("button", { name: new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i") }),
      googlePage.getByRole("link", { name: new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i") })
    ]);

    if (accountCandidate) {
      await accountCandidate.click({ timeout: 20_000 });
      await googlePage.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);
    }

    if (popup) {
      await popup.waitForClose({ timeout: 20_000 }).catch(() => undefined);
    }

    await page.bringToFront();
    await waitForUi(page);

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/negocio|mi negocio|dashboard/i)
    ]);

    expect(sidebar, "Main app interface/sidebar is not visible after login.").not.toBeNull();
    await captureCheckpoint("01-dashboard-loaded", page, testInfo);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioItem = await requiredVisible(
      [page.getByText(/^Negocio$/i), page.getByRole("link", { name: /^Negocio$/i }), page.getByRole("button", { name: /^Negocio$/i })],
      "Sidebar section 'Negocio' was not found."
    );
    await clickAndWait(negocioItem, page);

    const miNegocioItem = await requiredVisible(
      [
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      "Option 'Mi Negocio' was not found."
    );
    await clickAndWait(miNegocioItem, page);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();
    await captureCheckpoint("02-mi-negocio-menu-expanded", page, testInfo);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await requiredVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      "Option 'Agregar Negocio' was not visible."
    );

    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
    const nombreInput = await requiredVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator('input[name*="nombre" i], input[id*="nombre" i]')
      ],
      "Input 'Nombre del Negocio' was not found."
    );

    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await captureCheckpoint("03-agregar-negocio-modal", page, testInfo);

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /cancelar/i }), page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await firstVisible([page.getByText(/administrar negocios/i)]);
    if (!administrarVisible) {
      const miNegocioItem = await requiredVisible(
        [
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i)
        ],
        "Could not re-open 'Mi Negocio' menu."
      );
      await clickAndWait(miNegocioItem, page);
    }

    const administrarNegocios = await requiredVisible(
      [
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      "Option 'Administrar Negocios' was not found."
    );
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

    await captureCheckpoint("04-administrar-negocios-page", page, testInfo, { fullPage: true });
  });

  await runStep("Información General", async () => {
    const infoSection = await requiredVisible(
      [page.locator("section, div").filter({ hasText: /informaci[oó]n general/i })],
      "Section 'Información General' was not found."
    );
    const infoText = await infoSection.innerText();

    expect(infoText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    expect(infoText).toMatch(/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/);
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado/i)).toBeVisible();
    await expect(page.getByText(/activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const negociosSection = await requiredVisible(
      [page.locator("section, div").filter({ hasText: /tus negocios/i })],
      "Section 'Tus Negocios' was not found."
    );
    const negociosText = await negociosSection.innerText();

    expect(negociosText.trim().length).toBeGreaterThan(40);
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const addBusinessButton = await firstVisible([
      negociosSection.getByRole("button", { name: /agregar negocio/i }),
      negociosSection.getByRole("link", { name: /agregar negocio/i }),
      page.getByRole("button", { name: /agregar negocio/i })
    ]);
    expect(addBusinessButton, "Button 'Agregar Negocio' was not found in business area.").not.toBeNull();
  });

  await runStep("Términos y Condiciones", async () => {
    const termsLink = await requiredVisible(
      [
        page.getByRole("link", { name: /t[eé]rminos y condiciones/i }),
        page.getByRole("button", { name: /t[eé]rminos y condiciones/i }),
        page.getByText(/t[eé]rminos y condiciones/i)
      ],
      "Link 'Términos y Condiciones' was not found."
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(termsLink, page);
    const popup = await popupPromise;
    const legalPage = popup ?? page;

    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);

    await expect(legalPage.getByRole("heading", { name: /t[eé]rminos y condiciones/i })).toBeVisible();
    const legalText = await legalPage.locator("body").innerText();
    expect(legalText.trim().length).toBeGreaterThan(100);

    legalUrls.terms = legalPage.url();
    await captureCheckpoint("05-terms-and-conditions", legalPage, testInfo, { fullPage: true });

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(page);
    }
  });

  await runStep("Política de Privacidad", async () => {
    const privacyLink = await requiredVisible(
      [
        page.getByRole("link", { name: /pol[ií]tica de privacidad/i }),
        page.getByRole("button", { name: /pol[ií]tica de privacidad/i }),
        page.getByText(/pol[ií]tica de privacidad/i)
      ],
      "Link 'Política de Privacidad' was not found."
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(privacyLink, page);
    const popup = await popupPromise;
    const legalPage = popup ?? page;

    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);

    await expect(legalPage.getByRole("heading", { name: /pol[ií]tica de privacidad/i })).toBeVisible();
    const legalText = await legalPage.locator("body").innerText();
    expect(legalText.trim().length).toBeGreaterThan(100);

    legalUrls.privacy = legalPage.url();
    await captureCheckpoint("06-privacy-policy", legalPage, testInfo, { fullPage: true });

    if (popup) {
      await popup.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUi(page);
    }
  });

  const finalReport = {
    generatedAt: new Date().toISOString(),
    report,
    legalUrls,
    failures
  };

  const reportPath = path.join(testInfo.outputDir, "mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log(`Final report: ${JSON.stringify(finalReport, null, 2)}`);
  expect(failures, `Validation failures:\n${failures.join("\n")}`).toHaveLength(0);
});
