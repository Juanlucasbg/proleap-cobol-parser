const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

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
];

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toTextRegex(value) {
  return new RegExp(escapeRegex(value), "i");
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const startUrl =
    process.env.SALEADS_START_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    "";

  const artifactsDir = path.join(testInfo.outputDir, "checkpoints");
  await fs.mkdir(artifactsDir, { recursive: true });

  const stepReport = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "NOT_EXECUTED", details: "" }]),
  );
  const legalUrls = {
    termsAndConditionsUrl: "",
    privacyPolicyUrl: "",
  };

  const setPass = (field, details = "") => {
    stepReport[field] = { status: "PASS", details };
  };

  const setFail = (field, error) => {
    const message = error instanceof Error ? error.message : String(error);
    stepReport[field] = { status: "FAIL", details: message };
  };

  const waitForUi = async (targetPage = page) => {
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.waitForLoadState("networkidle").catch(() => {});
    await targetPage.waitForTimeout(500);
  };

  const captureScreenshot = async (
    targetPage,
    fileName,
    options = { fullPage: false },
  ) => {
    const fullPath = path.join(artifactsDir, fileName);
    await targetPage.screenshot({ path: fullPath, fullPage: Boolean(options.fullPage) });
    await testInfo.attach(fileName, {
      path: fullPath,
      contentType: "image/png",
    });
    return fullPath;
  };

  const firstVisible = async (locators, timeoutMs = 20000) => {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      for (const locator of locators) {
        if (await locator.isVisible().catch(() => false)) {
          return locator;
        }
      }
      await page.waitForTimeout(300);
    }
    throw new Error("None of the expected UI elements became visible in time.");
  };

  const visibleTextCandidates = (target, text) => {
    const regex = toTextRegex(text);
    return [
      target.getByRole("button", { name: regex }).first(),
      target.getByRole("link", { name: regex }).first(),
      target.getByRole("menuitem", { name: regex }).first(),
      target.getByText(regex).first(),
      target.locator(`[aria-label*="${text}"]`).first(),
      target.locator(`[title*="${text}"]`).first(),
    ];
  };

  const clickVisibleText = async (target, textOptions) => {
    for (const text of textOptions) {
      const locator = await firstVisible(visibleTextCandidates(target, text), 5000).catch(
        () => null,
      );
      if (locator) {
        await locator.scrollIntoViewIfNeeded().catch(() => {});
        await locator.click({ timeout: 10000 });
        await waitForUi(page);
        return locator;
      }
    }
    throw new Error(`Unable to click any option from: ${textOptions.join(", ")}`);
  };

  const expectTextVisible = async (target, text, timeoutMs = 20000) => {
    const locator = await firstVisible(visibleTextCandidates(target, text), timeoutMs);
    await expect(locator).toBeVisible({ timeout: timeoutMs });
    return locator;
  };

  const expectAnyTextVisible = async (target, texts, timeoutMs = 20000) => {
    for (const text of texts) {
      const visible = await firstVisible(visibleTextCandidates(target, text), 3000).catch(
        () => null,
      );
      if (visible) {
        await expect(visible).toBeVisible({ timeout: timeoutMs });
        return visible;
      }
    }
    throw new Error(`None of these texts are visible: ${texts.join(", ")}`);
  };

  const runStep = async (field, stepFn) => {
    try {
      await stepFn();
      if (stepReport[field].status !== "FAIL") {
        setPass(field);
      }
    } catch (error) {
      setFail(field, error);
    }
  };

  const finishAndAssert = async () => {
    const finalPayload = {
      name: "saleads_mi_negocio_full_test",
      executedAt: new Date().toISOString(),
      startUrl: startUrl || "NOT_PROVIDED",
      report: stepReport,
      legalEvidence: legalUrls,
    };

    const reportPath = path.join(testInfo.outputDir, "saleads_mi_negocio_full_test_report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(finalPayload, null, 2)}\n`, "utf8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    // Final Report: PASS or FAIL for each required section.
    // eslint-disable-next-line no-console
    console.log("----- SaleADS Mi Negocio Workflow Final Report -----");
    for (const field of REPORT_FIELDS) {
      const { status, details } = stepReport[field];
      const detailsText = details ? ` | ${details}` : "";
      // eslint-disable-next-line no-console
      console.log(`${field}: ${status}${detailsText}`);
    }
    // eslint-disable-next-line no-console
    console.log(`Términos y Condiciones URL: ${legalUrls.termsAndConditionsUrl || "N/A"}`);
    // eslint-disable-next-line no-console
    console.log(`Política de Privacidad URL: ${legalUrls.privacyPolicyUrl || "N/A"}`);

    const failedSteps = REPORT_FIELDS.filter((field) => stepReport[field].status !== "PASS");
    expect(
      failedSteps,
      `Workflow has failing steps. See JSON report at: ${reportPath}`,
    ).toEqual([]);
  };

  if (!startUrl) {
    for (const field of REPORT_FIELDS) {
      setFail(
        field,
        "Missing SALEADS_START_URL (or SALEADS_URL / BASE_URL). Provide the environment login URL to run this workflow.",
      );
    }
    await finishAndAssert();
    return;
  } else {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  let sidebar = page.locator("aside, nav, [class*='sidebar']").first();

  await runStep("Login", async () => {
    const googlePopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Iniciar con Google",
      "Continuar con Google",
    ]);
    const googlePopup = await googlePopupPromise;
    const authPage = googlePopup || page;

    if (googlePopup) {
      await authPage.waitForLoadState("domcontentloaded");
      await waitForUi(authPage);
    }

    // If Google account chooser appears, select the expected account.
    const googleAccountOption = authPage
      .getByText("juanlucasbarbiergarzon@gmail.com", { exact: false })
      .first();
    if (await googleAccountOption.isVisible().catch(() => false)) {
      await googleAccountOption.click({ timeout: 10000 });
      await waitForUi(authPage);
    }

    if (googlePopup) {
      await googlePopup.waitForClose({ timeout: 60000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUi(page);
    sidebar = await firstVisible(
      [
        page.locator("aside").first(),
        page.locator("nav").first(),
        page.locator("[class*='sidebar']").first(),
      ],
      30000,
    );
    await expect(sidebar).toBeVisible();
    await expectAnyTextVisible(sidebar, ["Negocio", "Mi Negocio"], 30000);
    await captureScreenshot(page, "01-dashboard-loaded.png", { fullPage: true });
  });

  await runStep("Mi Negocio menu", async () => {
    sidebar = await firstVisible(
      [
        page.locator("aside").first(),
        page.locator("nav").first(),
        page.locator("[class*='sidebar']").first(),
      ],
      20000,
    );

    await expectAnyTextVisible(sidebar, ["Negocio"], 15000);
    await clickVisibleText(sidebar, ["Mi Negocio", "Mi negocio"]);
    await expectTextVisible(sidebar, "Agregar Negocio", 15000);
    await expectTextVisible(sidebar, "Administrar Negocios", 15000);
    await captureScreenshot(page, "02-mi-negocio-expanded-menu.png", { fullPage: true });
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickVisibleText(sidebar, ["Agregar Negocio"]);

    await expectAnyTextVisible(page, ["Crear Nuevo Negocio"], 15000);
    const nameInput = await firstVisible(
      [
        page.getByLabel(toTextRegex("Nombre del Negocio")).first(),
        page.getByPlaceholder(toTextRegex("Nombre del Negocio")).first(),
        page.locator("input[name*='nombre'], input[id*='nombre']").first(),
      ],
      15000,
    );
    await expect(nameInput).toBeVisible();
    await expectAnyTextVisible(page, ["Tienes 2 de 3 negocios"], 15000);
    await expectAnyTextVisible(page, ["Cancelar"], 15000);
    await expectAnyTextVisible(page, ["Crear Negocio"], 15000);

    await captureScreenshot(page, "03-agregar-negocio-modal.png", { fullPage: true });

    await nameInput.fill("Negocio Prueba Automatización");
    await clickVisibleText(page, ["Cancelar"]);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await firstVisible(
      visibleTextCandidates(sidebar, "Administrar Negocios"),
      5000,
    ).catch(() => null);

    if (!administrarVisible) {
      await clickVisibleText(sidebar, ["Mi Negocio", "Mi negocio"]);
    }

    await clickVisibleText(sidebar, ["Administrar Negocios"]);
    await expectAnyTextVisible(page, ["Información General"], 30000);
    await expectAnyTextVisible(page, ["Detalles de la Cuenta"], 30000);
    await expectAnyTextVisible(page, ["Tus Negocios"], 30000);
    await expectAnyTextVisible(page, ["Sección Legal"], 30000);
    await captureScreenshot(page, "04-administrar-negocios-page.png", { fullPage: true });
  });

  await runStep("Información General", async () => {
    await expectAnyTextVisible(page, ["BUSINESS PLAN"], 20000);
    await expectAnyTextVisible(page, ["Cambiar Plan"], 20000);

    const userEmail = page
      .locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/")
      .first();
    await expect(userEmail).toBeVisible({ timeout: 20000 });

    // Any non-empty text inside this section is considered a visible user name.
    const infoSection = page
      .locator("section, div")
      .filter({ hasText: toTextRegex("Información General") })
      .first();
    const userNameCandidate = infoSection.locator("h2, h3, h4, p, span, strong").first();
    await expect(userNameCandidate).toBeVisible({ timeout: 20000 });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expectAnyTextVisible(page, ["Cuenta creada"], 20000);
    await expectAnyTextVisible(page, ["Estado activo", "Activo"], 20000);
    await expectAnyTextVisible(page, ["Idioma seleccionado"], 20000);
  });

  await runStep("Tus Negocios", async () => {
    await expectAnyTextVisible(page, ["Tus Negocios"], 20000);
    await expectAnyTextVisible(page, ["Agregar Negocio"], 20000);
    await expectAnyTextVisible(page, ["Tienes 2 de 3 negocios"], 20000);

    const negociosSection = page
      .locator("section, div")
      .filter({ hasText: toTextRegex("Tus Negocios") })
      .first();
    const businessItems = negociosSection.locator("li, article, .card, [class*='business']");
    await expect(businessItems.first()).toBeVisible({ timeout: 20000 });
  });

  const validateLegalPage = async ({ linkText, headingText, screenshotName, urlKey }) => {
    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickVisibleText(page, [linkText]);
    const popup = await popupPromise;

    let legalPage = page;
    if (popup) {
      legalPage = popup;
      await legalPage.waitForLoadState("domcontentloaded");
      await waitForUi(legalPage);
    }

    const headingCandidate = await firstVisible(
      [
        legalPage.getByRole("heading", { name: toTextRegex(headingText) }).first(),
        legalPage.getByText(toTextRegex(headingText)).first(),
      ],
      30000,
    );
    await expect(headingCandidate).toBeVisible();

    const legalContent = legalPage.locator("main p, article p, p").first();
    await expect(legalContent).toBeVisible({ timeout: 30000 });

    legalUrls[urlKey] = legalPage.url();
    await captureScreenshot(legalPage, screenshotName, { fullPage: true });

    if (popup) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  };

  await runStep("Términos y Condiciones", async () => {
    await validateLegalPage({
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotName: "05-terminos-y-condiciones.png",
      urlKey: "termsAndConditionsUrl",
    });
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalPage({
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotName: "06-politica-de-privacidad.png",
      urlKey: "privacyPolicyUrl",
    });
  });
  await finishAndAssert();
});
