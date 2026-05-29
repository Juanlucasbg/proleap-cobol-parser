import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const NEGOCIO_PRUEBA_NOMBRE = "Negocio Prueba Automatización";

type ReportStatus = "PASS" | "FAIL";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];

function createInitialReport(): Record<ReportField, ReportStatus> {
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

function asErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10_000 });
  } catch {
    // Some pages keep long-lived connections; domcontentloaded is enough in that case.
  }
  await page.waitForTimeout(500);
}

async function firstVisible(
  page: Page,
  candidates: Array<() => Locator>,
  description: string,
  timeoutMs = 20_000
): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidateFactory of candidates) {
      const candidate = candidateFactory().first();
      try {
        if (await candidate.isVisible()) {
          return candidate;
        }
      } catch {
        // Keep trying additional locators.
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Could not find visible element for: ${description}`);
}

async function capture(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = true
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function runValidationStep(
  report: Record<ReportField, ReportStatus>,
  failures: string[],
  field: ReportField,
  action: () => Promise<void>
): Promise<void> {
  try {
    await action();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    failures.push(`${field}: ${asErrorMessage(error)}`);
  }
}

async function assertTextVisible(page: Page, value: RegExp | string): Promise<void> {
  await expect(page.getByText(value).first()).toBeVisible({ timeout: 25_000 });
}

async function openLegalDocumentAndValidate(params: {
  appPage: Page;
  linkLabel: RegExp;
  heading: RegExp;
  screenshotFileName: string;
  testInfo: TestInfo;
}): Promise<string> {
  const { appPage, linkLabel, heading, screenshotFileName, testInfo } = params;
  const context = appPage.context();
  const legalLink = await firstVisible(
    appPage,
    [
      () => appPage.getByRole("link", { name: linkLabel }),
      () => appPage.getByRole("button", { name: linkLabel }),
      () => appPage.getByText(linkLabel)
    ],
    `legal link ${String(linkLabel)}`
  );

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await legalLink.click();
  const popupPage = await popupPromise;

  if (popupPage) {
    await waitForUi(popupPage);
    await assertTextVisible(popupPage, heading);
    const popupBody = await popupPage.locator("body").innerText();
    if (popupBody.trim().length < 150) {
      throw new Error("Legal page did not contain enough content.");
    }

    await capture(popupPage, testInfo, screenshotFileName);
    const finalUrl = popupPage.url();
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return finalUrl;
  }

  await waitForUi(appPage);
  await assertTextVisible(appPage, heading);
  const pageBody = await appPage.locator("body").innerText();
  if (pageBody.trim().length < 150) {
    throw new Error("Legal page did not contain enough content.");
  }

  await capture(appPage, testInfo, screenshotFileName);
  const finalUrl = appPage.url();
  await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
  await waitForUi(appPage);
  return finalUrl;
}

async function attachFinalReport(
  testInfo: TestInfo,
  report: Record<ReportField, ReportStatus>,
  failures: string[],
  termsUrl: string,
  privacyUrl: string
): Promise<void> {
  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    statusByField: report,
    urls: {
      terminosYCondiciones: termsUrl || "N/A",
      politicaDePrivacidad: privacyUrl || "N/A"
    },
    failures
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json"
  });

  console.log(JSON.stringify(finalReport, null, 2));
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page }, testInfo) => {
    const report = createInitialReport();
    const failures: string[] = [];

    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else {
      console.warn(
        "SALEADS_LOGIN_URL/SALEADS_BASE_URL is not set. Test will proceed assuming the runner already opened the SaleADS login page."
      );
    }

    if (!loginUrl && page.url() === "about:blank") {
      failures.push(
        "No SaleADS login page is available. Set SALEADS_LOGIN_URL/SALEADS_BASE_URL or preload the browser with the login page."
      );
      await attachFinalReport(testInfo, report, failures, "", "");
      expect(
        failures,
        `One or more validations failed:\n${failures.map((failure) => `- ${failure}`).join("\n")}`
      ).toEqual([]);
      return;
    }

    await runValidationStep(report, failures, "Login", async () => {
      const googleLoginTrigger = await firstVisible(
        page,
        [
          () => page.getByRole("button", { name: /google|iniciar|sign in|login/i }),
          () => page.getByRole("link", { name: /google|iniciar|sign in|login/i }),
          () => page.getByText(/google/i)
        ],
        "Google login trigger"
      );

      const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await googleLoginTrigger.click();
      await waitForUi(page);
      const popupPage = await popupPromise;

      if (popupPage) {
        await waitForUi(popupPage);
        const accountOption = popupPage.getByText(ACCOUNT_EMAIL, { exact: false }).first();
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
          await waitForUi(popupPage);
        }
        await popupPage.close().catch(() => undefined);
        await page.bringToFront();
      } else {
        const accountOption = page.getByText(ACCOUNT_EMAIL, { exact: false }).first();
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
          await waitForUi(page);
        }
      }

      await firstVisible(
        page,
        [() => page.getByRole("navigation"), () => page.locator("aside"), () => page.getByText(/Negocio/i)],
        "left sidebar navigation",
        40_000
      );
      await capture(page, testInfo, "01-dashboard-loaded.png");
    });

    await runValidationStep(report, failures, "Mi Negocio menu", async () => {
      const miNegocioOption = await firstVisible(
        page,
        [
          () => page.getByRole("button", { name: /Mi Negocio/i }),
          () => page.getByRole("link", { name: /Mi Negocio/i }),
          () => page.getByText(/Mi Negocio/i)
        ],
        "Mi Negocio menu option"
      );
      await miNegocioOption.click();
      await waitForUi(page);

      await assertTextVisible(page, /Agregar Negocio/i);
      await assertTextVisible(page, /Administrar Negocios/i);
      await capture(page, testInfo, "02-mi-negocio-expanded-menu.png", false);
    });

    await runValidationStep(report, failures, "Agregar Negocio modal", async () => {
      const agregarNegocioOption = await firstVisible(
        page,
        [
          () => page.getByRole("link", { name: /Agregar Negocio/i }),
          () => page.getByRole("button", { name: /Agregar Negocio/i }),
          () => page.getByText(/Agregar Negocio/i)
        ],
        "Agregar Negocio action"
      );
      await agregarNegocioOption.click();
      await waitForUi(page);

      await assertTextVisible(page, /Crear Nuevo Negocio/i);
      await assertTextVisible(page, /Nombre del Negocio/i);
      await assertTextVisible(page, /Tienes\s*2\s*de\s*3\s*negocios/i);
      await assertTextVisible(page, /Cancelar/i);
      await assertTextVisible(page, /Crear Negocio/i);
      await capture(page, testInfo, "03-agregar-negocio-modal.png");

      const nombreNegocioInput = await firstVisible(
        page,
        [
          () => page.getByRole("textbox", { name: /Nombre del Negocio/i }),
          () => page.getByPlaceholder(/Nombre del Negocio/i),
          () => page.locator("input[name*='negocio' i]"),
          () => page.locator("input[id*='negocio' i]")
        ],
        "Nombre del Negocio input"
      );
      await nombreNegocioInput.click();
      await nombreNegocioInput.fill(NEGOCIO_PRUEBA_NOMBRE);

      const cancelarButton = await firstVisible(
        page,
        [() => page.getByRole("button", { name: /Cancelar/i }), () => page.getByText(/Cancelar/i)],
        "Cancelar button in modal"
      );
      await cancelarButton.click();
      await waitForUi(page);
    });

    await runValidationStep(report, failures, "Administrar Negocios view", async () => {
      const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        const miNegocioOption = await firstVisible(
          page,
          [
            () => page.getByRole("button", { name: /Mi Negocio/i }),
            () => page.getByRole("link", { name: /Mi Negocio/i }),
            () => page.getByText(/Mi Negocio/i)
          ],
          "Mi Negocio menu option for re-expand"
        );
        await miNegocioOption.click();
        await waitForUi(page);
      }

      const administrarNegociosOption = await firstVisible(
        page,
        [
          () => page.getByRole("link", { name: /Administrar Negocios/i }),
          () => page.getByRole("button", { name: /Administrar Negocios/i }),
          () => page.getByText(/Administrar Negocios/i)
        ],
        "Administrar Negocios action"
      );
      await administrarNegociosOption.click();
      await waitForUi(page);

      await assertTextVisible(page, /Información General/i);
      await assertTextVisible(page, /Detalles de la Cuenta/i);
      await assertTextVisible(page, /Tus Negocios/i);
      await assertTextVisible(page, /Sección Legal/i);
      await capture(page, testInfo, "04-administrar-negocios-account-page.png");
    });

    await runValidationStep(report, failures, "Información General", async () => {
      await assertTextVisible(page, /Información General/i);
      await assertTextVisible(page, /BUSINESS PLAN/i);
      await assertTextVisible(page, /Cambiar Plan/i);

      const bodyText = await page.locator("body").innerText();
      const emailPattern = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
      if (!emailPattern.test(bodyText)) {
        throw new Error("Expected user email was not visible.");
      }

      const normalizedText = bodyText
        .replace(emailPattern, "")
        .replace(/Información General|BUSINESS PLAN|Cambiar Plan/gi, "")
        .replace(/\s+/g, " ")
        .trim();
      if (normalizedText.length < 3) {
        throw new Error("Expected user name was not visible.");
      }
    });

    await runValidationStep(report, failures, "Detalles de la Cuenta", async () => {
      await assertTextVisible(page, /Cuenta creada/i);
      await assertTextVisible(page, /Estado activo/i);
      await assertTextVisible(page, /Idioma seleccionado/i);
    });

    await runValidationStep(report, failures, "Tus Negocios", async () => {
      await assertTextVisible(page, /Tus Negocios/i);
      await assertTextVisible(page, /Agregar Negocio/i);
      await assertTextVisible(page, /Tienes\s*2\s*de\s*3\s*negocios/i);
    });

    let termsUrl = "";
    await runValidationStep(report, failures, "Términos y Condiciones", async () => {
      termsUrl = await openLegalDocumentAndValidate({
        appPage: page,
        linkLabel: /Términos y Condiciones/i,
        heading: /Términos y Condiciones/i,
        screenshotFileName: "05-terminos-y-condiciones.png",
        testInfo
      });
    });

    let privacyUrl = "";
    await runValidationStep(report, failures, "Política de Privacidad", async () => {
      privacyUrl = await openLegalDocumentAndValidate({
        appPage: page,
        linkLabel: /Política de Privacidad/i,
        heading: /Política de Privacidad/i,
        screenshotFileName: "06-politica-de-privacidad.png",
        testInfo
      });
    });

    await attachFinalReport(testInfo, report, failures, termsUrl, privacyUrl);
    expect(
      failures,
      `One or more validations failed:\n${failures.map((failure) => `- ${failure}`).join("\n")}`
    ).toEqual([]);
  });
});
