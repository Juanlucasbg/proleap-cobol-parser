import { expect, type Locator, type Page, type TestInfo, test } from "@playwright/test";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

type StepResult = "PASS" | "FAIL";

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

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {
    // Some SPA screens keep network requests alive continuously.
  });
}

async function getFirstVisible(page: Page, candidates: Locator[], timeoutMs = 20_000): Promise<Locator> {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`No visible locator found after ${timeoutMs}ms.`);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => {
    // Scrolling can fail for fixed-position elements; clicking can still work.
  });
  await locator.click();
  await waitForUi(page);
}

function escapeRegex(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const results: Record<(typeof REPORT_FIELDS)[number], StepResult> = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, "FAIL"])
  ) as Record<(typeof REPORT_FIELDS)[number], StepResult>;
  const failures: string[] = [];
  const legalUrls: Partial<Record<"Términos y Condiciones" | "Política de Privacidad", string>> = {};
  let screenshotCounter = 0;

  const checkpoint = async (name: string, targetPage = page, fullPage = false): Promise<void> => {
    screenshotCounter += 1;
    const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "-");
    const fileName = `${String(screenshotCounter).padStart(2, "0")}-${safeName}.png`;
    const filePath = testInfo.outputPath(fileName);

    await targetPage.screenshot({ path: filePath, fullPage });
    await testInfo.attach(`checkpoint-${safeName}`, {
      path: filePath,
      contentType: "image/png"
    });
  };

  const runStep = async (stepName: (typeof REPORT_FIELDS)[number], action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      results[stepName] = "PASS";
    } catch (error) {
      results[stepName] = "FAIL";
      const reason = error instanceof Error ? error.message : String(error);
      failures.push(`${stepName}: ${reason}`);
    }
  };

  const configuredBaseUrl = (testInfo.project.use as { baseURL?: string }).baseURL;

  await runStep("Login", async () => {
    if (page.url() === "about:blank") {
      if (!configuredBaseUrl) {
        throw new Error(
          "No login URL configured. Set SALEADS_LOGIN_URL/BASE_URL or pre-open the SaleADS login page before running this test."
        );
      }

      await page.goto(configuredBaseUrl);
      await waitForUi(page);
    }

    const signInWithGoogle = await getFirstVisible(page, [
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i)
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 12_000 }).catch(() => null);
    await clickAndWait(page, signInWithGoogle);

    const popup = await popupPromise;

    if (popup) {
      await waitForUi(popup);
      const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitForUi(popup);
      }

      await popup.waitForEvent("close", { timeout: 40_000 }).catch(() => {
        // If popup remains open the auth might have completed in the main page.
      });
    } else {
      const samePageAccountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
      if (await samePageAccountOption.isVisible().catch(() => false)) {
        await clickAndWait(page, samePageAccountOption);
      }
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(page.getByText(/negocio/i).first()).toBeVisible();

    await checkpoint("dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await getFirstVisible(page, [
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i })
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await getFirstVisible(page, [
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    await checkpoint("mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusinessEntry = await getFirstVisible(page, [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await clickAndWait(page, addBusinessEntry);

    await expect(page.getByRole("heading", { name: /^Crear Nuevo Negocio$/i })).toBeVisible();
    const businessNameInput = await getFirstVisible(page, [
      page.getByLabel(/^Nombre del Negocio$/i),
      page.getByPlaceholder(/^Nombre del Negocio$/i),
      page.locator("input[name*='nombre'], input[placeholder*='Negocio']")
    ]);
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    await checkpoint("crear-nuevo-negocio-modal");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }));
  });

  await runStep("Administrar Negocios view", async () => {
    const adminEntryVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
    if (!adminEntryVisible) {
      const miNegocioOption = await getFirstVisible(page, [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ]);
      await clickAndWait(page, miNegocioOption);
    }

    const manageBusinessesEntry = await getFirstVisible(page, [
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);
    await clickAndWait(page, manageBusinessesEntry);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await checkpoint("administrar-negocios-page", page, true);
  });

  await runStep("Información General", async () => {
    const infoSection = page
      .locator("section, div")
      .filter({ has: page.getByText(/Información General/i).first() })
      .first();
    await expect(infoSection).toBeVisible();

    const infoText = (await infoSection.innerText()).replace(/\s+/g, " ");
    const emailMatch = infoText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    if (!emailMatch) {
      throw new Error("User email is not visible in Información General.");
    }

    const normalized = infoText.toLowerCase();
    const hasLikelyName =
      /[a-záéíóúñ]{3,}\s+[a-záéíóúñ]{2,}/i.test(infoText) &&
      !normalized.includes("información general") &&
      !normalized.includes("business plan");

    if (!hasLikelyName) {
      throw new Error("User name is not clearly visible in Información General.");
    }

    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = page
      .locator("section, div")
      .filter({ has: page.getByText(/Detalles de la Cuenta/i).first() })
      .first();
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const businessesSection = page
      .locator("section, div")
      .filter({ has: page.getByText(/Tus Negocios/i).first() })
      .first();
    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const hasList = (await businessesSection.locator("li, [role='listitem'], table tbody tr, article").count()) > 0;
    if (!hasList) {
      throw new Error("Business list is not visible in Tus Negocios section.");
    }
  });

  const validateLegalDocument = async (
    linkText: "Términos y Condiciones" | "Política de Privacidad",
    expectedHeading: RegExp
  ): Promise<void> => {
    const legalLink = await getFirstVisible(page, [
      page.getByRole("link", { name: new RegExp(`^${escapeRegex(linkText)}$`, "i") }),
      page.getByRole("button", { name: new RegExp(`^${escapeRegex(linkText)}$`, "i") }),
      page.getByText(new RegExp(`^${escapeRegex(linkText)}$`, "i"))
    ]);

    const popupPromise = page.context().waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(page, legalLink);

    const popup = await popupPromise;
    const target = popup ?? page;

    await waitForUi(target);
    await expect(target.getByRole("heading", { name: expectedHeading })).toBeVisible();

    const legalText = (await target.locator("main, article, body").first().innerText()).replace(/\s+/g, " ").trim();
    if (legalText.length < 120) {
      throw new Error(`${linkText} page content looks incomplete.`);
    }

    await checkpoint(`legal-${linkText}`, target, true);
    legalUrls[linkText] = target.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  };

  await runStep("Términos y Condiciones", async () => {
    await validateLegalDocument("Términos y Condiciones", /Términos y Condiciones/i);
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalDocument("Política de Privacidad", /Política de Privacidad/i);
  });

  const finalReport = REPORT_FIELDS.map((field) => ({
    step: field,
    status: results[field],
    finalUrl: legalUrls[field as keyof typeof legalUrls] ?? ""
  }));

  await testInfo.attach("final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2)),
    contentType: "application/json"
  });

  // eslint-disable-next-line no-console
  console.table(finalReport);

  if (failures.length > 0) {
    throw new Error(`One or more workflow checks failed:\n${failures.join("\n")}`);
  }
});
