import { expect, Locator, Page, test, TestInfo } from "@playwright/test";

type ReportStatus = "PASS" | "FAIL";

type WorkflowReport = {
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

const REPORT_KEYS: Array<keyof WorkflowReport> = [
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

function emptyReport(): WorkflowReport {
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
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
}

async function findFirstVisible(candidates: Locator[], timeoutMs = 20_000): Promise<Locator> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const candidate of candidates) {
      const current = candidate.first();
      if (await current.isVisible().catch(() => false)) {
        return current;
      }
    }

    await new Promise((resolve) => {
      setTimeout(resolve, 250);
    });
  }

  throw new Error("None of the expected text-based locators became visible.");
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function attachScreenshot(page: Page, name: string, testInfo: TestInfo, fullPage = false): Promise<void> {
  const path = testInfo.outputPath(`${name.replace(/\s+/g, "_").toLowerCase()}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const administrar = page.getByText(/Administrar Negocios/i).first();
  if (await administrar.isVisible().catch(() => false)) {
    return;
  }

  const miNegocio = await findFirstVisible([
    page.getByRole("button", { name: /Mi Negocio/i }),
    page.getByRole("link", { name: /Mi Negocio/i }),
    page.getByText(/^Mi Negocio$/i)
  ]);

  await clickAndWait(miNegocio, page);
}

async function clickLegalLinkAndValidate(
  page: Page,
  linkText: RegExp,
  headingText: RegExp,
  screenshotName: string,
  testInfo: TestInfo
): Promise<string> {
  const context = page.context();
  const appUrlBeforeClick = page.url();
  const newPagePromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);

  const legalLink = await findFirstVisible([
    page.getByRole("link", { name: linkText }),
    page.getByText(linkText)
  ]);

  await clickAndWait(legalLink, page);

  const newPage = await newPagePromise;
  const targetPage = newPage ?? page;
  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);

  const heading = await findFirstVisible([
    targetPage.getByRole("heading", { name: headingText }),
    targetPage.getByText(headingText),
    targetPage.getByText(/T[eé]rminos y Condiciones|Pol[ií]tica de Privacidad/i)
  ], 15_000);
  await expect(heading).toBeVisible();

  const legalText = targetPage.locator("main p, article p, section p, p").first();
  await expect(legalText).toBeVisible();

  await attachScreenshot(targetPage, screenshotName, testInfo, true);
  const finalUrl = targetPage.url();

  if (targetPage !== page) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" });
    await waitForUi(page);
    await page.bringToFront();
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = emptyReport();
  const errors: string[] = [];
  const legalUrls: Record<string, string> = {};

  const runStep = async (label: keyof WorkflowReport, fn: () => Promise<void>) => {
    try {
      await fn();
      report[label] = "PASS";
    } catch (error) {
      report[label] = "FAIL";
      const detail = error instanceof Error ? error.message : String(error);
      errors.push(`${label}: ${detail}`);
    }
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await runStep("Login", async () => {
    const loginButton = await findFirstVisible([
      page.getByRole("button", { name: /Sign in with Google|Iniciar sesión con Google|Google/i }),
      page.getByRole("link", { name: /Sign in with Google|Iniciar sesión con Google|Google/i }),
      page.getByText(/Sign in with Google|Iniciar sesión con Google|Google/i)
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 6_000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const account = popup.getByText("juanlucasbarbiergarzon@gmail.com").first();
      if (await account.isVisible({ timeout: 8_000 }).catch(() => false)) {
        await account.click();
      }
      await popup.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => undefined);
      await page.bringToFront();
    } else {
      const account = page.getByText("juanlucasbarbiergarzon@gmail.com").first();
      if (await account.isVisible({ timeout: 8_000 }).catch(() => false)) {
        await clickAndWait(account, page);
      }
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
    await attachScreenshot(page, "step1_dashboard_loaded", testInfo, true);
  });

  await runStep("Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible();

    const negocioSection = await findFirstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ]);
    await clickAndWait(negocioSection, page);

    const miNegocioOption = await findFirstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await attachScreenshot(page, "step2_mi_negocio_expanded", testInfo, true);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await findFirstVisible([
      page.getByRole("button", { name: /Agregar Negocio/i }),
      page.getByRole("link", { name: /Agregar Negocio/i }),
      page.getByText(/Agregar Negocio/i)
    ]);

    await clickAndWait(agregarNegocio, page);

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible();
    const negocioInputField = await findFirstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[name*='negocio' i], input[id*='negocio' i]")
    ]);
    await expect(negocioInputField).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await attachScreenshot(page, "step3_crear_nuevo_negocio_modal", testInfo, true);

    await negocioInputField.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /Cancelar/i }), page);
  });

  await runStep("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);

    const administrar = await findFirstVisible([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i)
    ]);

    await clickAndWait(administrar, page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await attachScreenshot(page, "step4_administrar_negocios_page", testInfo, true);
  });

  await runStep("Información General", async () => {
    const bodyText = await page.locator("body").innerText();
    expect(bodyText).toMatch(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
    expect(bodyText).toMatch(/BUSINESS PLAN/i);
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const nameLike = /([A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,})/.test(bodyText);
    expect(nameLike).toBeTruthy();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    const addBusinessControl = await findFirstVisible([
      page.getByRole("button", { name: /Agregar Negocio/i }),
      page.getByRole("link", { name: /Agregar Negocio/i }),
      page.getByText(/Agregar Negocio/i)
    ]);
    await expect(addBusinessControl).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    const finalUrl = await clickLegalLinkAndValidate(
      page,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      "step8_terminos_y_condiciones",
      testInfo
    );
    legalUrls["Términos y Condiciones"] = finalUrl;
  });

  await runStep("Política de Privacidad", async () => {
    const finalUrl = await clickLegalLinkAndValidate(
      page,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
      "step9_politica_de_privacidad",
      testInfo
    );
    legalUrls["Política de Privacidad"] = finalUrl;
  });

  const finalReport = {
    report,
    legalUrls
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json"
  });

  console.log("Final workflow report:");
  for (const key of REPORT_KEYS) {
    console.log(`- ${key}: ${report[key]}`);
  }
  for (const [key, url] of Object.entries(legalUrls)) {
    console.log(`- ${key} URL: ${url}`);
  }

  expect(errors, errors.join("\n")).toEqual([]);
});
