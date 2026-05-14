import { expect, Locator, Page, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  status: StepStatus;
  checks: string[];
};

const accountEmail = "juanlucasbarbiergarzon@gmail.com";
const artifactsDir =
  process.env.SALEADS_ARTIFACTS_DIR ?? "artifacts/saleads-mi-negocio";

const reportFields = [
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

function createEmptyReport(): Record<(typeof reportFields)[number], StepResult> {
  return reportFields.reduce(
    (acc, field) => ({
      ...acc,
      [field]: { status: "FAIL", checks: [] }
    }),
    {} as Record<(typeof reportFields)[number], StepResult>
  );
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const current = candidate.first();
    if (await current.isVisible().catch(() => false)) {
      return current;
    }
  }

  return null;
}

function markCheck(
  report: Record<(typeof reportFields)[number], StepResult>,
  field: (typeof reportFields)[number],
  passed: boolean,
  message: string
): void {
  report[field].checks.push(`${passed ? "PASS" : "FAIL"} - ${message}`);
}

async function takeCheckpoint(
  page: Page,
  fileName: string,
  fullPage = false
): Promise<void> {
  await mkdir(path.join(artifactsDir, "screenshots"), { recursive: true });
  await page.screenshot({
    path: path.join(artifactsDir, "screenshots", fileName),
    fullPage
  });
}

function containsEmail(text: string): boolean {
  return /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i.test(text);
}

async function clickLegalLink(
  page: Page,
  label: string,
  expectedHeading: string,
  screenshotName: string
): Promise<{ passed: boolean; url: string; details: string[] }> {
  const details: string[] = [];
  const context = page.context();
  const link = await firstVisible([
    page.getByRole("link", { name: new RegExp(label, "i") }),
    page.getByText(label, { exact: true }),
    page.getByText(label)
  ]);

  if (!link) {
    return { passed: false, url: "", details: [`No se encontró '${label}'.`] };
  }

  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  const appUrlBefore = page.url();

  await link.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const legalPage = popup ?? page;

  if (popup) {
    await waitForUi(legalPage);
    details.push("El enlace abrió una nueva pestaña.");
  } else {
    details.push("El enlace navegó en la misma pestaña.");
  }

  const headingVisible = await firstVisible([
    legalPage.getByRole("heading", { name: new RegExp(expectedHeading, "i") }),
    legalPage.getByText(expectedHeading)
  ]);
  const hasHeading = Boolean(headingVisible);
  details.push(`Heading '${expectedHeading}' visible: ${hasHeading}`);

  const bodyText = await legalPage.locator("body").innerText().catch(() => "");
  const hasLegalText = bodyText.trim().length >= 120;
  details.push(`Texto legal visible: ${hasLegalText}`);

  await takeCheckpoint(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
  } else if (page.url() !== appUrlBefore) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return {
    passed: hasHeading && hasLegalText,
    url: finalUrl,
    details
  };
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report = createEmptyReport();

  // Step 1: Login with Google.
  try {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "SALEADS_LOGIN_URL no está configurada y no existe una página abierta para iniciar."
      );
    }

    const loginButton = await firstVisible([
      page.getByRole("button", { name: /google|sign in|login|iniciar/i }),
      page.getByRole("link", { name: /google|sign in|login|iniciar/i }),
      page.getByText("Sign in with Google"),
      page.getByText("Iniciar con Google")
    ]);

    if (!loginButton) {
      throw new Error("No se encontró un botón de login con Google.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await waitForUi(popup);
      const accountChoice = await firstVisible([popup.getByText(accountEmail)]);
      if (accountChoice) {
        await accountChoice.click();
        await waitForUi(popup);
      }
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
    } else {
      const inlineAccountChoice = await firstVisible([page.getByText(accountEmail)]);
      if (inlineAccountChoice) {
        await inlineAccountChoice.click();
        await waitForUi(page);
      }
    }

    const hasMainInterface = Boolean(
      await firstVisible([
        page.getByRole("navigation"),
        page.locator("aside"),
        page.getByText("Negocio")
      ])
    );
    const hasSidebar = Boolean(
      await firstVisible([page.getByRole("navigation"), page.locator("aside")])
    );

    markCheck(report, "Login", hasMainInterface, "Interfaz principal visible");
    markCheck(report, "Login", hasSidebar, "Sidebar izquierdo visible");
    report["Login"].status = hasMainInterface && hasSidebar ? "PASS" : "FAIL";
    await takeCheckpoint(page, "01-dashboard-loaded.png", true);
  } catch (error) {
    markCheck(report, "Login", false, String(error));
    report["Login"].status = "FAIL";
    await takeCheckpoint(page, "01-login-failure.png", true);
  }

  // Step 2: Open Mi Negocio menu.
  try {
    const negocio = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText("Negocio", { exact: true })
    ]);
    if (negocio) {
      await negocio.click();
      await waitForUi(page);
    }

    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText("Mi Negocio", { exact: true })
    ]);
    if (!miNegocio) {
      throw new Error("No se encontró 'Mi Negocio' en el sidebar.");
    }

    await miNegocio.click();
    await waitForUi(page);

    const hasAgregar = Boolean(await firstVisible([page.getByText("Agregar Negocio", { exact: true })]));
    const hasAdministrar = Boolean(
      await firstVisible([page.getByText("Administrar Negocios", { exact: true })])
    );

    markCheck(report, "Mi Negocio menu", hasAgregar, "'Agregar Negocio' visible");
    markCheck(report, "Mi Negocio menu", hasAdministrar, "'Administrar Negocios' visible");
    markCheck(report, "Mi Negocio menu", hasAgregar && hasAdministrar, "Submenú expandido");
    report["Mi Negocio menu"].status = hasAgregar && hasAdministrar ? "PASS" : "FAIL";
    await takeCheckpoint(page, "02-mi-negocio-expanded.png", true);
  } catch (error) {
    markCheck(report, "Mi Negocio menu", false, String(error));
    report["Mi Negocio menu"].status = "FAIL";
  }

  // Step 3: Validate Agregar Negocio modal.
  try {
    const agregarNegocio = await firstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText("Agregar Negocio", { exact: true })
    ]);
    if (!agregarNegocio) {
      throw new Error("No se encontró la opción 'Agregar Negocio'.");
    }

    await agregarNegocio.click();
    await waitForUi(page);

    const hasTitle = Boolean(await firstVisible([page.getByText("Crear Nuevo Negocio", { exact: true })]));
    const nameInput =
      (await firstVisible([
        page.getByLabel("Nombre del Negocio"),
        page.getByPlaceholder("Nombre del Negocio"),
        page.locator("input[name*='negocio' i], input[id*='negocio' i]")
      ])) ?? null;
    const hasBusinessQuota = Boolean(
      await firstVisible([page.getByText("Tienes 2 de 3 negocios", { exact: true })])
    );
    const hasCancelar = Boolean(await firstVisible([page.getByRole("button", { name: /^Cancelar$/i })]));
    const hasCrear = Boolean(await firstVisible([page.getByRole("button", { name: /^Crear Negocio$/i })]));

    markCheck(report, "Agregar Negocio modal", hasTitle, "Título 'Crear Nuevo Negocio' visible");
    markCheck(report, "Agregar Negocio modal", Boolean(nameInput), "Input 'Nombre del Negocio' visible");
    markCheck(report, "Agregar Negocio modal", hasBusinessQuota, "Texto 'Tienes 2 de 3 negocios' visible");
    markCheck(report, "Agregar Negocio modal", hasCancelar, "Botón 'Cancelar' visible");
    markCheck(report, "Agregar Negocio modal", hasCrear, "Botón 'Crear Negocio' visible");

    await takeCheckpoint(page, "03-agregar-negocio-modal.png", true);

    if (nameInput) {
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }

    const cancelButton = await firstVisible([page.getByRole("button", { name: /^Cancelar$/i })]);
    if (cancelButton) {
      await cancelButton.click();
      await waitForUi(page);
    }

    report["Agregar Negocio modal"].status =
      hasTitle && Boolean(nameInput) && hasBusinessQuota && hasCancelar && hasCrear ? "PASS" : "FAIL";
  } catch (error) {
    markCheck(report, "Agregar Negocio modal", false, String(error));
    report["Agregar Negocio modal"].status = "FAIL";
  }

  // Step 4: Open Administrar Negocios.
  try {
    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText("Mi Negocio", { exact: true })
    ]);
    if (miNegocio) {
      await miNegocio.click();
      await waitForUi(page);
    }

    const administrarNegocios = await firstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText("Administrar Negocios", { exact: true })
    ]);
    if (!administrarNegocios) {
      throw new Error("No se encontró 'Administrar Negocios'.");
    }

    await administrarNegocios.click();
    await waitForUi(page);

    const hasInfoGeneral = Boolean(await firstVisible([page.getByText("Información General", { exact: true })]));
    const hasDetalleCuenta = Boolean(
      await firstVisible([page.getByText("Detalles de la Cuenta", { exact: true })])
    );
    const hasTusNegocios = Boolean(await firstVisible([page.getByText("Tus Negocios", { exact: true })]));
    const hasLegal = Boolean(
      await firstVisible([page.getByText("Sección Legal", { exact: true }), page.getByText("Legal", { exact: true })])
    );

    markCheck(report, "Administrar Negocios view", hasInfoGeneral, "'Información General' visible");
    markCheck(report, "Administrar Negocios view", hasDetalleCuenta, "'Detalles de la Cuenta' visible");
    markCheck(report, "Administrar Negocios view", hasTusNegocios, "'Tus Negocios' visible");
    markCheck(report, "Administrar Negocios view", hasLegal, "'Sección Legal' visible");
    report["Administrar Negocios view"].status =
      hasInfoGeneral && hasDetalleCuenta && hasTusNegocios && hasLegal ? "PASS" : "FAIL";

    await takeCheckpoint(page, "04-administrar-negocios-page.png", true);
  } catch (error) {
    markCheck(report, "Administrar Negocios view", false, String(error));
    report["Administrar Negocios view"].status = "FAIL";
  }

  // Step 5: Validate Información General.
  try {
    const bodyText = await page.locator("body").innerText();
    const hasEmail = containsEmail(bodyText);
    const hasUserNameHint = /nombre|name|usuario/i.test(bodyText);
    const hasBusinessPlan = Boolean(await firstVisible([page.getByText("BUSINESS PLAN", { exact: true })]));
    const hasCambiarPlan = Boolean(await firstVisible([page.getByRole("button", { name: /^Cambiar Plan$/i })]));

    markCheck(report, "Información General", hasUserNameHint, "Nombre de usuario visible");
    markCheck(report, "Información General", hasEmail, "Email visible");
    markCheck(report, "Información General", hasBusinessPlan, "'BUSINESS PLAN' visible");
    markCheck(report, "Información General", hasCambiarPlan, "'Cambiar Plan' visible");
    report["Información General"].status =
      hasUserNameHint && hasEmail && hasBusinessPlan && hasCambiarPlan ? "PASS" : "FAIL";
  } catch (error) {
    markCheck(report, "Información General", false, String(error));
    report["Información General"].status = "FAIL";
  }

  // Step 6: Validate Detalles de la Cuenta.
  try {
    const hasCuentaCreada = Boolean(await firstVisible([page.getByText(/Cuenta creada/i)]));
    const hasEstadoActivo = Boolean(await firstVisible([page.getByText(/Estado activo/i)]));
    const hasIdioma = Boolean(await firstVisible([page.getByText(/Idioma seleccionado/i)]));

    markCheck(report, "Detalles de la Cuenta", hasCuentaCreada, "'Cuenta creada' visible");
    markCheck(report, "Detalles de la Cuenta", hasEstadoActivo, "'Estado activo' visible");
    markCheck(report, "Detalles de la Cuenta", hasIdioma, "'Idioma seleccionado' visible");
    report["Detalles de la Cuenta"].status = hasCuentaCreada && hasEstadoActivo && hasIdioma ? "PASS" : "FAIL";
  } catch (error) {
    markCheck(report, "Detalles de la Cuenta", false, String(error));
    report["Detalles de la Cuenta"].status = "FAIL";
  }

  // Step 7: Validate Tus Negocios.
  try {
    const hasBusinessList = Boolean(await firstVisible([page.getByText("Tus Negocios", { exact: true })]));
    const hasAddButton = Boolean(await firstVisible([page.getByRole("button", { name: /^Agregar Negocio$/i })]));
    const hasBusinessQuota = Boolean(
      await firstVisible([page.getByText("Tienes 2 de 3 negocios", { exact: true })])
    );

    markCheck(report, "Tus Negocios", hasBusinessList, "Listado de negocios visible");
    markCheck(report, "Tus Negocios", hasAddButton, "Botón 'Agregar Negocio' visible");
    markCheck(report, "Tus Negocios", hasBusinessQuota, "Texto 'Tienes 2 de 3 negocios' visible");
    report["Tus Negocios"].status = hasBusinessList && hasAddButton && hasBusinessQuota ? "PASS" : "FAIL";
  } catch (error) {
    markCheck(report, "Tus Negocios", false, String(error));
    report["Tus Negocios"].status = "FAIL";
  }

  // Step 8: Validate Términos y Condiciones.
  try {
    const result = await clickLegalLink(
      page,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05-terminos-y-condiciones.png"
    );
    for (const detail of result.details) {
      const passed = !detail.includes("visible: false");
      markCheck(report, "Términos y Condiciones", passed, detail);
    }
    markCheck(report, "Términos y Condiciones", Boolean(result.url), `URL final: ${result.url}`);
    report["Términos y Condiciones"].status = result.passed ? "PASS" : "FAIL";
  } catch (error) {
    markCheck(report, "Términos y Condiciones", false, String(error));
    report["Términos y Condiciones"].status = "FAIL";
  }

  // Step 9: Validate Política de Privacidad.
  try {
    const result = await clickLegalLink(
      page,
      "Política de Privacidad",
      "Política de Privacidad",
      "06-politica-de-privacidad.png"
    );
    for (const detail of result.details) {
      const passed = !detail.includes("visible: false");
      markCheck(report, "Política de Privacidad", passed, detail);
    }
    markCheck(report, "Política de Privacidad", Boolean(result.url), `URL final: ${result.url}`);
    report["Política de Privacidad"].status = result.passed ? "PASS" : "FAIL";
  } catch (error) {
    markCheck(report, "Política de Privacidad", false, String(error));
    report["Política de Privacidad"].status = "FAIL";
  }

  // Step 10: Final report.
  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report
  };

  await mkdir(artifactsDir, { recursive: true });
  await writeFile(
    path.join(artifactsDir, "final-report.json"),
    `${JSON.stringify(finalReport, null, 2)}\n`,
    "utf-8"
  );

  // Output report in runner logs for CI parsing.
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(finalReport, null, 2));

  const failures = Object.entries(report)
    .filter(([, value]) => value.status === "FAIL")
    .map(([key]) => key);

  expect(
    failures,
    `Validaciones fallidas: ${failures.join(", ") || "ninguna"}`
  ).toHaveLength(0);
});
