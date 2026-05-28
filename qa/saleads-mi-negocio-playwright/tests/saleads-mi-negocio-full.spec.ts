import { expect, Locator, Page, test } from "@playwright/test";

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

type ReportStatus = "PASS" | "FAIL";

interface ReportEntry {
  status: ReportStatus;
  details: string;
  evidence?: string[];
  finalUrl?: string;
}

const EMAIL_REGEX = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
const BUSINESS_QUOTA_REGEX = /Tienes\s+\d+\s+de\s+\d+\s+negocios/i;

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {
    // Some views keep active background requests; domcontentloaded is sufficient in that case.
  });
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function getVisibleLocator(
  candidates: Locator[],
  description: string,
  timeoutMs = 20_000,
): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const first = candidate.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
    await candidates[0].page().waitForTimeout(300);
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function clickAndWait(locator: Locator): Promise<void> {
  await locator.click();
  await waitForUi(locator.page());
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: Record<ReportField, ReportEntry> = {
    Login: { status: "FAIL", details: "Not executed." },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed." },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed." },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed." },
    "Información General": { status: "FAIL", details: "Not executed." },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed." },
    "Tus Negocios": { status: "FAIL", details: "Not executed." },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed." },
    "Política de Privacidad": { status: "FAIL", details: "Not executed." },
  };

  const checkpoint = async (name: string, targetPage: Page, fullPage = false): Promise<string> => {
    const fileName = `${slugify(name)}.png`;
    const outputPath = testInfo.outputPath(fileName);
    await targetPage.screenshot({ path: outputPath, fullPage });
    return outputPath;
  };

  // Step 1: Login with Google
  try {
    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const loginButton = await getVisibleLocator(
      [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/iniciar sesi[oó]n con google/i),
      ],
      "Google login button",
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(loginButton);
    const popup = await popupPromise;
    const authPage = popup ?? page;
    await waitForUi(authPage);

    const accountOption = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    if (await accountOption.waitFor({ state: "visible", timeout: 8_000 }).then(() => true).catch(() => false)) {
      await clickAndWait(accountOption);
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 90_000 }).catch(() => {
        // If popup does not close automatically, continue with current parent page state.
      });
      await page.bringToFront();
    }

    await waitForUi(page);
    await expect(
      await getVisibleLocator(
        [
          page.locator("aside"),
          page.getByRole("navigation"),
          page.getByText(/negocio|mi negocio/i),
        ],
        "left sidebar navigation",
      ),
    ).toBeVisible();

    const dashboardShot = await checkpoint("01-dashboard-loaded", page, true);
    report.Login = {
      status: "PASS",
      details: "Google login completed and application sidebar is visible.",
      evidence: [dashboardShot],
    };
  } catch (error) {
    report.Login = {
      status: "FAIL",
      details: `Login validation failed: ${(error as Error).message}`,
    };
  }

  // Step 2: Open Mi Negocio menu
  try {
    const negocioSection = await getVisibleLocator(
      [page.getByText("Negocio", { exact: true }), page.getByRole("button", { name: /negocio/i })],
      "Negocio section",
    );
    await clickAndWait(negocioSection);

    const miNegocioOption = await getVisibleLocator(
      [page.getByText("Mi Negocio", { exact: true }), page.getByRole("button", { name: /mi negocio/i })],
      "Mi Negocio option",
    );
    await clickAndWait(miNegocioOption);

    await expect(await getVisibleLocator([page.getByText("Agregar Negocio", { exact: true })], "Agregar Negocio")).toBeVisible();
    await expect(
      await getVisibleLocator([page.getByText("Administrar Negocios", { exact: true })], "Administrar Negocios"),
    ).toBeVisible();

    const expandedMenuShot = await checkpoint("02-mi-negocio-menu-expanded", page);
    report["Mi Negocio menu"] = {
      status: "PASS",
      details: "Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios options.",
      evidence: [expandedMenuShot],
    };
  } catch (error) {
    report["Mi Negocio menu"] = {
      status: "FAIL",
      details: `Mi Negocio menu validation failed: ${(error as Error).message}`,
    };
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const agregarNegocioOption = await getVisibleLocator(
      [page.getByText("Agregar Negocio", { exact: true }), page.getByRole("button", { name: /agregar negocio/i })],
      "Agregar Negocio option",
    );
    await clickAndWait(agregarNegocioOption);

    await expect(await getVisibleLocator([page.getByText("Crear Nuevo Negocio", { exact: true })], "modal title")).toBeVisible();

    const businessNameInput = await getVisibleLocator(
      [
        page.getByLabel("Nombre del Negocio", { exact: true }),
        page.getByPlaceholder("Nombre del Negocio"),
        page.locator("input[name*='nombre'], input[id*='nombre']"),
      ],
      "Nombre del Negocio input",
    );

    const quotaText = page.getByText("Tienes 2 de 3 negocios", { exact: true }).first();
    const quotaVisible =
      (await quotaText.isVisible().catch(() => false)) ||
      (await page.getByText(BUSINESS_QUOTA_REGEX).first().isVisible().catch(() => false));
    expect(quotaVisible).toBeTruthy();

    await expect(await getVisibleLocator([page.getByRole("button", { name: "Cancelar" })], "Cancelar button")).toBeVisible();
    await expect(await getVisibleLocator([page.getByRole("button", { name: "Crear Negocio" })], "Crear Negocio button")).toBeVisible();

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");

    const modalShot = await checkpoint("03-agregar-negocio-modal", page);
    const cancelButton = await getVisibleLocator([page.getByRole("button", { name: "Cancelar" })], "Cancelar button");
    await clickAndWait(cancelButton);

    report["Agregar Negocio modal"] = {
      status: "PASS",
      details: "Agregar Negocio modal displayed all expected fields and actions.",
      evidence: [modalShot],
    };
  } catch (error) {
    report["Agregar Negocio modal"] = {
      status: "FAIL",
      details: `Agregar Negocio modal validation failed: ${(error as Error).message}`,
    };
  }

  // Step 4: Open Administrar Negocios
  try {
    const adminOptionVisible = await page.getByText("Administrar Negocios", { exact: true }).first().isVisible().catch(() => false);
    if (!adminOptionVisible) {
      const miNegocioOption = await getVisibleLocator(
        [page.getByText("Mi Negocio", { exact: true }), page.getByRole("button", { name: /mi negocio/i })],
        "Mi Negocio option for expand",
      );
      await clickAndWait(miNegocioOption);
    }

    const administrarNegociosOption = await getVisibleLocator(
      [page.getByText("Administrar Negocios", { exact: true }), page.getByRole("button", { name: /administrar negocios/i })],
      "Administrar Negocios option",
    );
    await clickAndWait(administrarNegociosOption);

    await expect(await getVisibleLocator([page.getByText("Información General", { exact: true })], "Información General section")).toBeVisible();
    await expect(
      await getVisibleLocator([page.getByText("Detalles de la Cuenta", { exact: true })], "Detalles de la Cuenta section"),
    ).toBeVisible();
    await expect(await getVisibleLocator([page.getByText("Tus Negocios", { exact: true })], "Tus Negocios section")).toBeVisible();
    await expect(await getVisibleLocator([page.getByText("Sección Legal", { exact: true })], "Sección Legal section")).toBeVisible();

    const accountPageShot = await checkpoint("04-administrar-negocios-account-page", page, true);
    report["Administrar Negocios view"] = {
      status: "PASS",
      details: "Administrar Negocios loaded with all required sections.",
      evidence: [accountPageShot],
    };
  } catch (error) {
    report["Administrar Negocios view"] = {
      status: "FAIL",
      details: `Administrar Negocios view validation failed: ${(error as Error).message}`,
    };
  }

  // Step 5: Validate Información General
  try {
    await expect(await getVisibleLocator([page.getByText("BUSINESS PLAN", { exact: false })], "BUSINESS PLAN text")).toBeVisible();
    await expect(await getVisibleLocator([page.getByRole("button", { name: "Cambiar Plan" })], "Cambiar Plan button")).toBeVisible();

    const bodyText = await page.locator("body").innerText();
    expect(bodyText).toMatch(EMAIL_REGEX);

    const hasPotentialName = bodyText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .some((line) => /[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}/.test(line) && !line.includes("@"));

    expect(hasPotentialName).toBeTruthy();

    report["Información General"] = {
      status: "PASS",
      details: "Información General shows name, email, BUSINESS PLAN, and Cambiar Plan.",
    };
  } catch (error) {
    report["Información General"] = {
      status: "FAIL",
      details: `Información General validation failed: ${(error as Error).message}`,
    };
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    await expect(await getVisibleLocator([page.getByText(/Cuenta creada/i)], "Cuenta creada")).toBeVisible();
    await expect(await getVisibleLocator([page.getByText(/Estado activo/i)], "Estado activo")).toBeVisible();
    await expect(await getVisibleLocator([page.getByText(/Idioma seleccionado/i)], "Idioma seleccionado")).toBeVisible();

    report["Detalles de la Cuenta"] = {
      status: "PASS",
      details: "Detalles de la Cuenta contains account creation, status, and language values.",
    };
  } catch (error) {
    report["Detalles de la Cuenta"] = {
      status: "FAIL",
      details: `Detalles de la Cuenta validation failed: ${(error as Error).message}`,
    };
  }

  // Step 7: Validate Tus Negocios
  try {
    await expect(await getVisibleLocator([page.getByText("Tus Negocios", { exact: true })], "Tus Negocios heading")).toBeVisible();
    await expect(await getVisibleLocator([page.getByText("Agregar Negocio", { exact: true })], "Agregar Negocio button")).toBeVisible();

    const quotaVisible =
      (await page.getByText("Tienes 2 de 3 negocios", { exact: true }).first().isVisible().catch(() => false)) ||
      (await page.getByText(BUSINESS_QUOTA_REGEX).first().isVisible().catch(() => false));
    expect(quotaVisible).toBeTruthy();

    const businessListVisible = await page
      .locator("[role='listitem'], [role='row'], li, tr, .card")
      .first()
      .isVisible()
      .catch(() => false);
    expect(businessListVisible).toBeTruthy();

    report["Tus Negocios"] = {
      status: "PASS",
      details: "Tus Negocios list and Agregar Negocio controls are visible.",
    };
  } catch (error) {
    report["Tus Negocios"] = {
      status: "FAIL",
      details: `Tus Negocios validation failed: ${(error as Error).message}`,
    };
  }

  const openLegalDocument = async (
    linkLabel: "Términos y Condiciones" | "Política de Privacidad",
  ): Promise<{ finalUrl: string; screenshotPath: string }> => {
    const currentAppUrl = page.url();
    const link = await getVisibleLocator(
      [page.getByRole("link", { name: linkLabel }), page.getByText(linkLabel, { exact: true })],
      `${linkLabel} link`,
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(link);
    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await waitForUi(legalPage);

    await expect(
      await getVisibleLocator(
        [
          legalPage.getByRole("heading", { name: linkLabel, exact: false }),
          legalPage.getByText(linkLabel, { exact: false }),
        ],
        `${linkLabel} heading`,
      ),
    ).toBeVisible();

    const legalContentVisible = await legalPage
      .locator("main p, article p, section p, p")
      .first()
      .isVisible()
      .catch(() => false);
    expect(legalContentVisible).toBeTruthy();

    const shot = await checkpoint(`legal-${linkLabel}`, legalPage, true);
    const finalUrl = legalPage.url();

    if (popup) {
      await popup.close().catch(() => {
        // If close fails, continue execution and bring application tab to front.
      });
      await page.bringToFront();
      await waitForUi(page);
    } else if (page.url() !== currentAppUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {
        // If goBack fails, try navigating to the original app URL.
      });
      if (page.url() !== currentAppUrl) {
        await page.goto(currentAppUrl, { waitUntil: "domcontentloaded" });
      }
      await waitForUi(page);
    }

    return { finalUrl, screenshotPath: shot };
  };

  // Step 8: Validate Términos y Condiciones
  try {
    const termsResult = await openLegalDocument("Términos y Condiciones");
    report["Términos y Condiciones"] = {
      status: "PASS",
      details: "Legal terms page loaded with heading and content.",
      evidence: [termsResult.screenshotPath],
      finalUrl: termsResult.finalUrl,
    };
  } catch (error) {
    report["Términos y Condiciones"] = {
      status: "FAIL",
      details: `Términos y Condiciones validation failed: ${(error as Error).message}`,
    };
  }

  // Step 9: Validate Política de Privacidad
  try {
    const privacyResult = await openLegalDocument("Política de Privacidad");
    report["Política de Privacidad"] = {
      status: "PASS",
      details: "Privacy page loaded with heading and content.",
      evidence: [privacyResult.screenshotPath],
      finalUrl: privacyResult.finalUrl,
    };
  } catch (error) {
    report["Política de Privacidad"] = {
      status: "FAIL",
      details: `Política de Privacidad validation failed: ${(error as Error).message}`,
    };
  }

  const orderedReport: Record<ReportField, ReportEntry> = {
    Login: report.Login,
    "Mi Negocio menu": report["Mi Negocio menu"],
    "Agregar Negocio modal": report["Agregar Negocio modal"],
    "Administrar Negocios view": report["Administrar Negocios view"],
    "Información General": report["Información General"],
    "Detalles de la Cuenta": report["Detalles de la Cuenta"],
    "Tus Negocios": report["Tus Negocios"],
    "Términos y Condiciones": report["Términos y Condiciones"],
    "Política de Privacidad": report["Política de Privacidad"],
  };

  await testInfo.attach("final-report", {
    body: JSON.stringify(orderedReport, null, 2),
    contentType: "application/json",
  });

  console.log("Final PASS/FAIL report:", JSON.stringify(orderedReport, null, 2));

  const failedSteps = Object.entries(orderedReport).filter(([, value]) => value.status === "FAIL");
  expect(
    failedSteps,
    `One or more validations failed:\n${failedSteps.map(([step, result]) => `${step}: ${result.details}`).join("\n")}`,
  ).toEqual([]);
});
