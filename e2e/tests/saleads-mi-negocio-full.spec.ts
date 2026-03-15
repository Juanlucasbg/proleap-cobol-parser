import { expect, type BrowserContext, type Locator, type Page, type TestInfo, test } from "@playwright/test";

type StepName =
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

type StepResult = {
  name: StepName;
  status: StepStatus;
  details?: string;
};

const STEP_FIELDS: StepName[] = [
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

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

function escapeRegex(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function firstVisible(locator: Locator): Promise<Locator | null> {
  const count = await locator.count();
  const max = Math.min(count, 8);

  for (let i = 0; i < max; i += 1) {
    const candidate = locator.nth(i);
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  return null;
}

async function findInteractiveByText(page: Page, labels: string[]): Promise<Locator | null> {
  for (const label of labels) {
    const exact = new RegExp(`^\\s*${escapeRegex(label)}\\s*$`, "i");
    const loose = new RegExp(escapeRegex(label), "i");

    const roleCandidates = [
      page.getByRole("button", { name: exact }),
      page.getByRole("link", { name: exact }),
      page.getByRole("menuitem", { name: exact }),
      page.getByRole("button", { name: loose }),
      page.getByRole("link", { name: loose }),
      page.getByRole("menuitem", { name: loose }),
    ];

    for (const candidate of roleCandidates) {
      const visible = await firstVisible(candidate);
      if (visible) {
        return visible;
      }
    }

    const textCandidate = await firstVisible(page.getByText(loose));
    if (textCandidate) {
      return textCandidate;
    }
  }

  return null;
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function sidebarVisible(page: Page): Promise<boolean> {
  const candidates = [
    page.locator("aside").first(),
    page.getByRole("navigation").first(),
  ];

  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      return true;
    }
  }

  return false;
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, name: string): Promise<void> {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function runStep(
  name: StepName,
  results: StepResult[],
  fn: () => Promise<void>,
): Promise<void> {
  try {
    await fn();
    results.push({ name, status: "PASS" });
  } catch (error) {
    const details = error instanceof Error ? error.message : String(error);
    results.push({ name, status: "FAIL", details });
  }
}

async function validateLegalPage(
  appPage: Page,
  context: BrowserContext,
  testInfo: TestInfo,
  linkLabels: string[],
  headingRegex: RegExp,
  checkpointName: string,
): Promise<string> {
  const link = await findInteractiveByText(appPage, linkLabels);
  if (!link) {
    throw new Error(`Could not find legal link: ${linkLabels.join(" / ")}`);
  }

  const openedPagePromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  await clickAndWait(appPage, link);
  const maybeNewPage = await openedPagePromise;

  const legalPage = maybeNewPage ?? appPage;
  await legalPage.waitForLoadState("domcontentloaded");

  await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({
    timeout: 30_000,
  });

  const legalContent = legalPage.locator("main p, article p, body p, body li").first();
  await expect(legalContent).toBeVisible({ timeout: 30_000 });

  await captureCheckpoint(legalPage, testInfo, checkpointName);
  const finalUrl = legalPage.url();

  if (maybeNewPage) {
    await appPage.bringToFront();
  } else {
    await legalPage.goBack().catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.setTimeout(15 * 60 * 1000);

  const results: StepResult[] = [];
  const finalUrls: Record<string, string> = {};

  const saleadsUrl = process.env.SALEADS_URL ?? process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL;
  if (saleadsUrl) {
    await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error("No SALEADS_URL/SALEADS_LOGIN_URL/BASE_URL provided and no preloaded login page is available.");
  }

  await runStep("Login", results, async () => {
    const alreadyInApp = await sidebarVisible(page);
    if (!alreadyInApp) {
      const loginBtn = await findInteractiveByText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
      ]);

      if (!loginBtn) {
        throw new Error("Google login button was not found.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(page, loginBtn);
      const popupPage = await popupPromise;
      const googlePage = popupPage ?? page;

      const accountChoice = await firstVisible(
        googlePage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }),
      );
      if (accountChoice) {
        await clickAndWait(googlePage, accountChoice);
      }
    }

    await expect(page.locator("main, [role='main']").first()).toBeVisible({ timeout: 90_000 });
    await expect.poll(async () => sidebarVisible(page), { timeout: 90_000 }).toBe(true);
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", results, async () => {
    const negocioTrigger =
      (await findInteractiveByText(page, ["Mi Negocio"])) ?? (await findInteractiveByText(page, ["Negocio"]));
    if (!negocioTrigger) {
      throw new Error("Could not find 'Negocio' or 'Mi Negocio' in sidebar.");
    }

    await clickAndWait(page, negocioTrigger);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded");
  });

  await runStep("Agregar Negocio modal", results, async () => {
    const addBusiness = await findInteractiveByText(page, ["Agregar Negocio"]);
    if (!addBusiness) {
      throw new Error("Could not find 'Agregar Negocio' action.");
    }

    await clickAndWait(page, addBusiness);

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible({ timeout: 30_000 });
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: 30_000 });

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal");

    const nameField = page.getByLabel(/Nombre del Negocio/i);
    await nameField.click();
    await nameField.fill("Negocio Prueba Automatizacion");

    const cancelBtn = page.getByRole("button", { name: /Cancelar/i });
    await clickAndWait(page, cancelBtn);
    await expect(modalTitle).toBeHidden({ timeout: 30_000 });
  });

  await runStep("Administrar Negocios view", results, async () => {
    const adminOptionVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!adminOptionVisible) {
      const negocioToggle =
        (await findInteractiveByText(page, ["Mi Negocio"])) ?? (await findInteractiveByText(page, ["Negocio"]));
      if (negocioToggle) {
        await clickAndWait(page, negocioToggle);
      }
    }

    const adminBusiness = await findInteractiveByText(page, ["Administrar Negocios"]);
    if (!adminBusiness) {
      throw new Error("Could not find 'Administrar Negocios' action.");
    }

    await clickAndWait(page, adminBusiness);

    await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible({ timeout: 60_000 });
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-view");
  });

  await runStep("Información General", results, async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 30_000 });

    const userName = page.locator("h1, h2, h3, p, span").filter({ hasText: /\b[A-Za-z]{2,}\b/ }).first();
    await expect(userName).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/@/).first()).toBeVisible({ timeout: 30_000 });
  });

  await runStep("Detalles de la Cuenta", results, async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Estado activo|Estado\s*:\s*Activo/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 30_000 });
  });

  await runStep("Tus Negocios", results, async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 30_000 });

    const businessItems = page.locator("main li, main tr, main .card");
    await expect(businessItems.first()).toBeVisible({ timeout: 30_000 });
  });

  await runStep("Términos y Condiciones", results, async () => {
    finalUrls["Términos y Condiciones"] = await validateLegalPage(
      page,
      context,
      testInfo,
      ["Términos y Condiciones", "Terminos y Condiciones"],
      /T[eé]rminos y Condiciones/i,
      "05-terminos-condiciones",
    );
  });

  await runStep("Política de Privacidad", results, async () => {
    finalUrls["Política de Privacidad"] = await validateLegalPage(
      page,
      context,
      testInfo,
      ["Política de Privacidad", "Politica de Privacidad"],
      /Pol[ií]tica de Privacidad/i,
      "06-politica-privacidad",
    );
  });

  const report: Record<StepName, StepStatus> = {
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

  for (const field of STEP_FIELDS) {
    const result = results.find((entry) => entry.name === field);
    if (result) {
      report[field] = result.status;
    }
  }

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    statusByField: report,
    evidence: {
      termsUrl: finalUrls["Términos y Condiciones"] ?? "",
      privacyUrl: finalUrls["Política de Privacidad"] ?? "",
    },
    rawResults: results,
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json",
  });
  // Keep the report visible in CI logs.
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(finalReport, null, 2));

  const failures = results.filter((entry) => entry.status === "FAIL");
  expect(
    failures,
    `Validation failures:\n${failures.map((f) => `- ${f.name}: ${f.details ?? "no details"}`).join("\n")}`,
  ).toHaveLength(0);
});
