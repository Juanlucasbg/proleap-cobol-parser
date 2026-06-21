import { expect, Locator, Page, TestInfo, test } from "@playwright/test";
import { writeFile } from "node:fs/promises";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => undefined);
  await page.waitForTimeout(1000);
}

async function firstVisible(candidates: Locator[], timeoutMs = 5000): Promise<Locator | null> {
  for (const candidate of candidates) {
    const target = candidate.first();
    const isVisible = await target.isVisible({ timeout: timeoutMs }).catch(() => false);
    if (isVisible) {
      return target;
    }
  }

  return null;
}

async function findByVisibleText(page: Page, labels: string[], clickable = true): Promise<Locator> {
  const candidates: Locator[] = [];

  for (const label of labels) {
    const pattern = new RegExp(escapeRegex(label), "i");

    if (clickable) {
      candidates.push(page.getByRole("button", { name: pattern }));
      candidates.push(page.getByRole("link", { name: pattern }));
      candidates.push(page.getByText(pattern));
    } else {
      candidates.push(page.getByRole("heading", { name: pattern }));
      candidates.push(page.getByText(pattern));
    }
  }

  const visible = await firstVisible(candidates);
  if (!visible) {
    throw new Error(`No visible element found for labels: ${labels.join(", ")}`);
  }

  return visible;
}

async function saveCheckpoint(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  const fileName = `${name}.png`;
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function validateLegalLink(
  page: Page,
  linkLabels: string[],
  headingLabel: string,
  screenshotName: string,
  testInfo: TestInfo
): Promise<string> {
  const link = await findByVisibleText(page, linkLabels);

  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  const navPromise = page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 12000 }).catch(() => null);

  await link.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const destination = popup ?? page;

  if (!popup) {
    await navPromise;
  } else {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20000 });
  }

  const heading = await findByVisibleText(destination, [headingLabel], false);
  await expect(heading).toBeVisible();

  const legalContentCandidates = [
    destination.getByText(/t[eé]rminos|condiciones|privacidad|datos|uso/i),
    destination.locator("article"),
    destination.locator("main p")
  ];
  const legalContent = await firstVisible(legalContentCandidates, 7000);
  if (!legalContent) {
    throw new Error(`No legal content detected for ${headingLabel}`);
  }

  await saveCheckpoint(destination, testInfo, screenshotName);
  const finalUrl = destination.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: Record<ReportField, "PASS" | "FAIL"> = {
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

  const failureMessages: string[] = [];
  const legalUrls: { terms?: string; privacy?: string } = {};

  const runStep = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      const message = error instanceof Error ? error.message : String(error);
      failureMessages.push(`[${field}] ${message}`);
    }
  };

  await runStep("Login", async () => {
    const targetUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
    if (!targetUrl) {
      throw new Error(
        "Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to the current environment login page URL."
      );
    }

    await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginButton = await findByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google"
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => undefined);
      const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      const canPickAccount = await accountOption.isVisible({ timeout: 8000 }).catch(() => false);

      if (canPickAccount) {
        await accountOption.click();
      }

      await popup.waitForEvent("close", { timeout: 45000 }).catch(() => undefined);
    } else {
      const inlineAccount = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      const canPickInline = await inlineAccount.isVisible({ timeout: 6000 }).catch(() => false);
      if (canPickInline) {
        await inlineAccount.click();
      }
    }

    await waitForUi(page);

    const appLanding = await firstVisible(
      [page.locator("aside"), page.getByText(/Negocio/i), page.getByText(/Dashboard/i)],
      20000
    );

    if (!appLanding) {
      throw new Error("Main application interface did not appear after Google login.");
    }

    const sidebar = await firstVisible([page.locator("aside"), page.locator("nav")], 20000);
    if (!sidebar) {
      throw new Error("Left sidebar navigation is not visible.");
    }

    await saveCheckpoint(page, testInfo, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await findByVisibleText(page, ["Negocio"]);
    await negocioSection.click();
    await waitForUi(page);

    const miNegocio = await findByVisibleText(page, ["Mi Negocio"]);
    await miNegocio.click();
    await waitForUi(page);

    await expect(await findByVisibleText(page, ["Agregar Negocio"])).toBeVisible();
    await expect(await findByVisibleText(page, ["Administrar Negocios"])).toBeVisible();

    await saveCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await findByVisibleText(page, ["Agregar Negocio"]);
    await agregarNegocio.click();
    await waitForUi(page);

    await expect(await findByVisibleText(page, ["Crear Nuevo Negocio"], false)).toBeVisible();
    await expect(await findByVisibleText(page, ["Nombre del Negocio"], false)).toBeVisible();
    await expect(await findByVisibleText(page, ["Tienes 2 de 3 negocios"], false)).toBeVisible();
    await expect(await findByVisibleText(page, ["Cancelar"])).toBeVisible();
    await expect(await findByVisibleText(page, ["Crear Negocio"])).toBeVisible();

    const nameFieldCandidates = [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").first()
    ];
    const nameField = await firstVisible(nameFieldCandidates, 6000);
    if (!nameField) {
      throw new Error("Could not find 'Nombre del Negocio' input.");
    }

    await nameField.click();
    await nameField.fill("Negocio Prueba Automatización");

    const cancelar = await findByVisibleText(page, ["Cancelar"]);
    await cancelar.click();
    await waitForUi(page);

    await saveCheckpoint(page, testInfo, "03-agregar-negocio-modal");
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocio = await findByVisibleText(page, ["Mi Negocio"]);
    await miNegocio.click().catch(() => undefined);
    await waitForUi(page);

    const administrar = await findByVisibleText(page, ["Administrar Negocios"]);
    await administrar.click();
    await waitForUi(page);

    await expect(await findByVisibleText(page, ["Información General"], false)).toBeVisible();
    await expect(await findByVisibleText(page, ["Detalles de la Cuenta"], false)).toBeVisible();
    await expect(await findByVisibleText(page, ["Tus Negocios"], false)).toBeVisible();
    await expect(await findByVisibleText(page, ["Sección Legal"], false)).toBeVisible();

    await saveCheckpoint(page, testInfo, "04-administrar-negocios-page", true);
  });

  await runStep("Información General", async () => {
    await expect(await findByVisibleText(page, ["BUSINESS PLAN"], false)).toBeVisible();
    await expect(await findByVisibleText(page, ["Cambiar Plan"])).toBeVisible();

    const userSignals = await firstVisible(
      [
        page.getByText(/@/),
        page.locator("text=/[A-Z][a-z]+\\s+[A-Z][a-z]+/"),
        page.getByText(/nombre|usuario/i)
      ],
      8000
    );

    if (!userSignals) {
      throw new Error("User name/email visibility check failed.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(await findByVisibleText(page, ["Cuenta creada"], false)).toBeVisible();
    await expect(await findByVisibleText(page, ["Estado activo"], false)).toBeVisible();
    await expect(await findByVisibleText(page, ["Idioma seleccionado"], false)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(await findByVisibleText(page, ["Tus Negocios"], false)).toBeVisible();
    await expect(await findByVisibleText(page, ["Agregar Negocio"])).toBeVisible();
    await expect(await findByVisibleText(page, ["Tienes 2 de 3 negocios"], false)).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    legalUrls.terms = await validateLegalLink(
      page,
      ["Términos y Condiciones", "Terminos y Condiciones"],
      "Términos y Condiciones",
      "08-terminos-y-condiciones",
      testInfo
    );
  });

  await runStep("Política de Privacidad", async () => {
    legalUrls.privacy = await validateLegalLink(
      page,
      ["Política de Privacidad", "Politica de Privacidad"],
      "Política de Privacidad",
      "09-politica-de-privacidad",
      testInfo
    );
  });

  const reportPayload = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls,
    failures: failureMessages
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await writeFile(reportPath, JSON.stringify(reportPayload, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedFields = Object.entries(report)
    .filter(([, status]) => status === "FAIL")
    .map(([field]) => field);

  expect(
    failedFields,
    `Validation failures:\n${failureMessages.join("\n") || "(No detailed failures available)"}`
  ).toEqual([]);
});
