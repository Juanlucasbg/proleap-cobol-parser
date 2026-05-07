import { expect, Locator, Page, TestInfo, test } from "@playwright/test";

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

type StepReport = {
  status: StepStatus;
  detail: string;
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function buildInitialReport(): Record<ReportField, StepReport> {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = {
      status: "FAIL",
      detail: "Not executed."
    };
    return acc;
  }, {} as Record<ReportField, StepReport>);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function firstVisibleLocator(candidates: Locator[], timeoutMs = 15000): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const first = candidate.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error("No candidate locator became visible.");
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false
): Promise<void> {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, { path: screenshotPath, contentType: "image/png" });
}

async function maybePickGoogleAccount(page: Page): Promise<boolean> {
  const account = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();

  if (await account.isVisible().catch(() => false)) {
    await account.click();
    await waitForUi(page);
    return true;
  }

  return false;
}

async function safeRunStep(
  report: Record<ReportField, StepReport>,
  field: ReportField,
  fn: () => Promise<void>
): Promise<boolean> {
  try {
    await fn();
    report[field] = { status: "PASS", detail: "Validation completed successfully." };
    return true;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    report[field] = { status: "FAIL", detail };
    return false;
  }
}

async function ensureOnLoginPage(page: Page): Promise<void> {
  const currentUrl = page.url();

  if (currentUrl && currentUrl !== "about:blank") {
    return;
  }

  const targetUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (!targetUrl) {
    throw new Error(
      "Page started on about:blank. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to the current environment login page."
    );
  }

  await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);
}

async function openLegalPageAndValidate(params: {
  page: Page;
  testInfo: TestInfo;
  linkTexts: RegExp[];
  heading: RegExp;
  screenshotName: string;
}): Promise<string> {
  const { page, testInfo, linkTexts, heading, screenshotName } = params;
  const context = page.context();
  const appPage = page;
  const preClickUrl = appPage.url();

  const legalLink = await firstVisibleLocator(
    linkTexts.map((regex) => appPage.getByRole("link", { name: regex })),
    15000
  );

  const newPagePromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await legalLink.click();

  let legalPage = await newPagePromise;
  if (legalPage) {
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.bringToFront();
  } else {
    legalPage = appPage;
    await legalPage.waitForLoadState("domcontentloaded");
  }

  const headingLocator = legalPage.getByRole("heading", { name: heading }).first();
  if (await headingLocator.isVisible().catch(() => false)) {
    await expect(headingLocator).toBeVisible();
  } else {
    await expect(legalPage.getByText(heading)).toBeVisible();
  }

  const legalText = (await legalPage.locator("body").innerText()).trim();
  if (legalText.split(/\s+/).length < 30) {
    throw new Error("Legal content appears too short or missing.");
  }

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== preClickUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" });
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildInitialReport();
  const legalUrls: Record<string, string> = {};

  const loginOk = await safeRunStep(report, "Login", async () => {
    await ensureOnLoginPage(page);

    const loginButton = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i)
      ],
      20000
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await maybePickGoogleAccount(popup);
    }

    await maybePickGoogleAccount(page);

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
    await expect(
      firstVisibleLocator([page.getByText(/Negocio/i), page.getByRole("link", { name: /Negocio/i })], 60000)
    ).resolves.toBeTruthy();

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png");
  });

  const miNegocioOk = loginOk
    ? await safeRunStep(report, "Mi Negocio menu", async () => {
        await expect(page.getByText(/Negocio/i).first()).toBeVisible();

        const miNegocioOption = await firstVisibleLocator(
          [
            page.getByRole("button", { name: /Mi Negocio/i }),
            page.getByRole("link", { name: /Mi Negocio/i }),
            page.getByText(/Mi Negocio/i)
          ],
          20000
        );
        await miNegocioOption.click();
        await waitForUi(page);

        await expect(page.getByText("Agregar Negocio")).toBeVisible();
        await expect(page.getByText("Administrar Negocios")).toBeVisible();

        await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
      })
    : false;

  const agregarNegocioModalOk = miNegocioOk
    ? await safeRunStep(report, "Agregar Negocio modal", async () => {
        const agregarNegocioOption = await firstVisibleLocator(
          [
            page.getByRole("button", { name: "Agregar Negocio" }),
            page.getByRole("link", { name: "Agregar Negocio" }),
            page.getByText("Agregar Negocio")
          ],
          15000
        );
        await agregarNegocioOption.click();
        await waitForUi(page);

        await expect(page.getByText("Crear Nuevo Negocio")).toBeVisible();

        const negocioInput = await firstVisibleLocator(
          [
            page.getByLabel(/Nombre del Negocio/i),
            page.getByPlaceholder(/Nombre del Negocio/i),
            page.getByRole("textbox", { name: /Nombre del Negocio/i })
          ],
          15000
        );
        await expect(negocioInput).toBeVisible();
        await expect(page.getByText("Tienes 2 de 3 negocios")).toBeVisible();
        await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible();
        await expect(page.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

        await negocioInput.click();
        await negocioInput.fill("Negocio Prueba Automatización");

        await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

        await page.getByRole("button", { name: "Cancelar" }).click();
        await page.waitForTimeout(500);
      })
    : false;

  const administrarOk = agregarNegocioModalOk
    ? await safeRunStep(report, "Administrar Negocios view", async () => {
        const administrarVisible = await page.getByText("Administrar Negocios").first().isVisible().catch(() => false);
        if (!administrarVisible) {
          const miNegocioOption = await firstVisibleLocator(
            [
              page.getByRole("button", { name: /Mi Negocio/i }),
              page.getByRole("link", { name: /Mi Negocio/i }),
              page.getByText(/Mi Negocio/i)
            ],
            10000
          );
          await miNegocioOption.click();
          await waitForUi(page);
        }

        const administrarOption = await firstVisibleLocator(
          [
            page.getByRole("button", { name: "Administrar Negocios" }),
            page.getByRole("link", { name: "Administrar Negocios" }),
            page.getByText("Administrar Negocios")
          ],
          15000
        );
        await administrarOption.click();
        await waitForUi(page);

        await expect(page.getByText("Información General")).toBeVisible();
        await expect(page.getByText("Detalles de la Cuenta")).toBeVisible();
        await expect(page.getByText("Tus Negocios")).toBeVisible();
        await expect(page.getByText("Sección Legal")).toBeVisible();

        await captureCheckpoint(page, testInfo, "04-administrar-negocios-full.png", true);
      })
    : false;

  if (administrarOk) {
    await safeRunStep(report, "Información General", async () => {
      await expect(page.getByText("Información General")).toBeVisible();
      await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)).toBeVisible();
      await expect(page.getByText("BUSINESS PLAN")).toBeVisible();
      await expect(page.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();
    });

    await safeRunStep(report, "Detalles de la Cuenta", async () => {
      await expect(page.getByText("Cuenta creada")).toBeVisible();
      await expect(page.getByText("Estado activo")).toBeVisible();
      await expect(page.getByText("Idioma seleccionado")).toBeVisible();
    });

    await safeRunStep(report, "Tus Negocios", async () => {
      const tusNegocios = page.getByText("Tus Negocios").first();
      await expect(tusNegocios).toBeVisible();
      await expect(page.getByRole("button", { name: "Agregar Negocio" })).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios")).toBeVisible();

      const businessListEntry = page.locator("tr, li, [role='row']").first();
      await expect(businessListEntry).toBeVisible();
    });

    await safeRunStep(report, "Términos y Condiciones", async () => {
      const finalUrl = await openLegalPageAndValidate({
        page,
        testInfo,
        linkTexts: [/Términos y Condiciones/i],
        heading: /Términos y Condiciones/i,
        screenshotName: "08-terminos-y-condiciones.png"
      });
      legalUrls["Términos y Condiciones"] = finalUrl;
    });

    await safeRunStep(report, "Política de Privacidad", async () => {
      const finalUrl = await openLegalPageAndValidate({
        page,
        testInfo,
        linkTexts: [/Política de Privacidad/i, /Politica de Privacidad/i],
        heading: /Política de Privacidad|Politica de Privacidad/i,
        screenshotName: "09-politica-de-privacidad.png"
      });
      legalUrls["Política de Privacidad"] = finalUrl;
    });
  } else {
    report["Información General"] = { status: "FAIL", detail: "Blocked by Administrar Negocios view failure." };
    report["Detalles de la Cuenta"] = { status: "FAIL", detail: "Blocked by Administrar Negocios view failure." };
    report["Tus Negocios"] = { status: "FAIL", detail: "Blocked by Administrar Negocios view failure." };
    report["Términos y Condiciones"] = { status: "FAIL", detail: "Blocked by Administrar Negocios view failure." };
    report["Política de Privacidad"] = { status: "FAIL", detail: "Blocked by Administrar Negocios view failure." };
  }

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json"
  });

  const failedSteps = Object.entries(report).filter(([, result]) => result.status === "FAIL");
  expect(
    failedSteps,
    `One or more validations failed.\n${JSON.stringify(finalReport, null, 2)}`
  ).toHaveLength(0);
});
