import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type StepStatus = "PASS" | "FAIL";

type ReportEntry = {
  status: StepStatus;
  details: string[];
};

type Report = Record<ReportField, ReportEntry>;

function emptyReport(): Report {
  const report = {} as Report;
  for (const field of REPORT_FIELDS) {
    report[field] = { status: "FAIL", details: [] };
  }
  return report;
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
  await page.waitForTimeout(350);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }
  return null;
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function capture(page: Page, testInfo: TestInfo, name: string): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage: true,
  });
}

function setPass(report: Report, field: ReportField, detail: string): void {
  report[field] = {
    status: "PASS",
    details: [...report[field].details, detail],
  };
}

function setFail(report: Report, field: ReportField, detail: string): void {
  report[field] = {
    status: "FAIL",
    details: [...report[field].details, detail],
  };
}

async function openLegalLink(
  appPage: Page,
  linkPattern: RegExp,
  headingPattern: RegExp,
  screenshotName: string,
  testInfo: TestInfo,
): Promise<string> {
  const link = await firstVisible([
    appPage.getByRole("link", { name: linkPattern }),
    appPage.getByRole("button", { name: linkPattern }),
    appPage.getByText(linkPattern),
  ]);
  if (!link) {
    throw new Error(`No se encontró el enlace legal: ${String(linkPattern)}`);
  }

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await link.click();
  await waitForUi(appPage);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  await waitForUi(legalPage);

  const heading = await firstVisible([
    legalPage.getByRole("heading", { name: headingPattern }),
    legalPage.getByText(headingPattern),
  ]);
  if (!heading) {
    throw new Error(`No se encontró el heading legal esperado: ${String(headingPattern)}`);
  }

  const legalParagraphs = legalPage.locator("p");
  await expect(legalParagraphs.first()).toBeVisible({ timeout: 15_000 });

  await capture(legalPage, testInfo, screenshotName);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = emptyReport();
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;

  // Step 1: Login with Google
  try {
    if (!loginUrl) {
      throw new Error(
        "Define SALEADS_LOGIN_URL o SALEADS_BASE_URL para ejecutar el flujo en cualquier entorno.",
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginButton = await firstVisible([
      page.getByRole("button", { name: /sign in with google|inicia.*google|iniciar.*google|continuar.*google/i }),
      page.getByRole("link", { name: /sign in with google|inicia.*google|iniciar.*google|continuar.*google/i }),
      page.getByText(/sign in with google|inicia.*google|iniciar.*google|continuar.*google/i),
    ]);
    if (!loginButton) {
      throw new Error("No se encontró el botón de login con Google.");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const authPage = await popupPromise;

    if (authPage) {
      await waitForUi(authPage);
      const accountOption = await firstVisible([
        authPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }),
        authPage.getByRole("button", { name: /juanlucasbarbiergarzon@gmail.com/i }),
        authPage.getByRole("link", { name: /juanlucasbarbiergarzon@gmail.com/i }),
      ]);
      if (accountOption) {
        await clickAndWait(authPage, accountOption);
      }

      await page.bringToFront();
      await waitForUi(page);
    } else {
      const accountOption = await firstVisible([
        page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }),
        page.getByRole("button", { name: /juanlucasbarbiergarzon@gmail.com/i }),
        page.getByRole("link", { name: /juanlucasbarbiergarzon@gmail.com/i }),
      ]);
      if (accountOption) {
        await clickAndWait(page, accountOption);
      }
    }

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("[class*='sidebar']"),
    ]);
    if (!sidebar) {
      throw new Error("No se detectó la barra lateral luego del login.");
    }

    const negocioMarker = await firstVisible([
      page.getByText(/negocio/i),
      page.getByRole("link", { name: /negocio/i }),
      page.getByRole("button", { name: /negocio/i }),
    ]);
    if (!negocioMarker) {
      throw new Error("No se detectó la interfaz principal tras el login.");
    }

    await capture(page, testInfo, "01-dashboard-loaded.png");
    setPass(report, "Login", "Login completado y sidebar visible.");
  } catch (error) {
    setFail(report, "Login", (error as Error).message);
  }

  // Step 2: Open Mi Negocio menu
  try {
    if (report["Login"].status === "FAIL") {
      throw new Error("Dependencia fallida: Login.");
    }

    const negocio = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    if (!negocio) {
      throw new Error("No se encontró la sección 'Negocio'.");
    }
    await clickAndWait(page, negocio);

    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    if (!miNegocio) {
      throw new Error("No se encontró la opción 'Mi Negocio'.");
    }
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: 10_000 });

    await capture(page, testInfo, "02-mi-negocio-menu-expanded.png");
    setPass(report, "Mi Negocio menu", "Menú expandido con Agregar/Administrar visibles.");
  } catch (error) {
    setFail(report, "Mi Negocio menu", (error as Error).message);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    if (report["Mi Negocio menu"].status === "FAIL") {
      throw new Error("Dependencia fallida: Mi Negocio menu.");
    }

    const agregarNegocio = await firstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    if (!agregarNegocio) {
      throw new Error("No se encontró 'Agregar Negocio'.");
    }

    await clickAndWait(page, agregarNegocio);
    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/Nombre del Negocio/i)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: 10_000 });

    const nameInput = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[type='text']"),
    ]);
    if (nameInput) {
      await nameInput.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }

    await capture(page, testInfo, "03-agregar-negocio-modal.png");

    const cancel = await firstVisible([
      page.getByRole("button", { name: /^Cancelar$/i }),
      page.getByText(/^Cancelar$/i),
    ]);
    if (cancel) {
      await clickAndWait(page, cancel);
    }

    setPass(report, "Agregar Negocio modal", "Modal validado y cerrado con Cancelar.");
  } catch (error) {
    setFail(report, "Agregar Negocio modal", (error as Error).message);
  }

  // Step 4: Open Administrar Negocios
  try {
    if (report["Mi Negocio menu"].status === "FAIL") {
      throw new Error("Dependencia fallida: Mi Negocio menu.");
    }

    if (!(await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false))) {
      const miNegocioAgain = await firstVisible([
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);
      if (miNegocioAgain) {
        await clickAndWait(page, miNegocioAgain);
      }
    }

    const administrar = await firstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    if (!administrar) {
      throw new Error("No se encontró 'Administrar Negocios'.");
    }

    await clickAndWait(page, administrar);
    await expect(page.getByText(/Información General/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible({ timeout: 15_000 });

    await capture(page, testInfo, "04-administrar-negocios-full-page.png");
    setPass(report, "Administrar Negocios view", "Vista de cuenta cargada con 4 secciones.");
  } catch (error) {
    setFail(report, "Administrar Negocios view", (error as Error).message);
  }

  // Step 5: Validate Información General
  try {
    if (report["Administrar Negocios view"].status === "FAIL") {
      throw new Error("Dependencia fallida: Administrar Negocios view.");
    }

    const emailVisible = await page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first().isVisible();
    if (!emailVisible) {
      throw new Error("No se detectó correo visible en Información General.");
    }

    const nameHint = await firstVisible([
      page.getByText(/juan/i),
      page.getByText(/nombre/i),
      page.getByText(/usuario/i),
    ]);
    if (!nameHint) {
      throw new Error("No se detectó nombre de usuario visible.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 10_000 });

    setPass(report, "Información General", "Nombre/Email/plan validados.");
  } catch (error) {
    setFail(report, "Información General", (error as Error).message);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    if (report["Administrar Negocios view"].status === "FAIL") {
      throw new Error("Dependencia fallida: Administrar Negocios view.");
    }

    await expect(page.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/Estado activo/i)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 10_000 });

    setPass(report, "Detalles de la Cuenta", "Cuenta creada / estado / idioma visibles.");
  } catch (error) {
    setFail(report, "Detalles de la Cuenta", (error as Error).message);
  }

  // Step 7: Validate Tus Negocios
  try {
    if (report["Administrar Negocios view"].status === "FAIL") {
      throw new Error("Dependencia fallida: Administrar Negocios view.");
    }

    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 10_000 });

    const listContainer = await firstVisible([
      page.locator("ul").filter({ has: page.getByText(/negocio/i) }),
      page.locator("[class*='business']").first(),
      page.getByText(/Negocio/i),
    ]);
    if (!listContainer) {
      throw new Error("No se detectó lista de negocios.");
    }

    setPass(report, "Tus Negocios", "Listado de negocios y límite del plan visibles.");
  } catch (error) {
    setFail(report, "Tus Negocios", (error as Error).message);
  }

  // Step 8: Validate Términos y Condiciones
  try {
    if (report["Administrar Negocios view"].status === "FAIL") {
      throw new Error("Dependencia fallida: Administrar Negocios view.");
    }

    const termsUrl = await openLegalLink(
      page,
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "05-terminos-y-condiciones.png",
      testInfo,
    );

    setPass(report, "Términos y Condiciones", `Página legal validada. URL final: ${termsUrl}`);
  } catch (error) {
    setFail(report, "Términos y Condiciones", (error as Error).message);
  }

  // Step 9: Validate Política de Privacidad
  try {
    if (report["Administrar Negocios view"].status === "FAIL") {
      throw new Error("Dependencia fallida: Administrar Negocios view.");
    }

    const privacyUrl = await openLegalLink(
      page,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "06-politica-de-privacidad.png",
      testInfo,
    );

    setPass(report, "Política de Privacidad", `Página legal validada. URL final: ${privacyUrl}`);
  } catch (error) {
    setFail(report, "Política de Privacidad", (error as Error).message);
  }

  // Step 10: Final report
  const reportPayload = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(reportPayload, null, 2), "utf-8"),
    contentType: "application/json",
  });

  // Console output is useful for CI logs and cron automations.
  // eslint-disable-next-line no-console
  console.log("FINAL_REPORT", JSON.stringify(reportPayload, null, 2));

  const failedSections = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failedSections,
    `Fallaron validaciones: ${failedSections.join(", ") || "ninguna"}`,
  ).toEqual([]);
});
