const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const START_URL = process.env.SALEADS_LOGIN_URL;
const UI_SETTLE_MS = Number(process.env.SALEADS_UI_SETTLE_MS || 800);
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

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(
    REPORT_FIELDS.map((name) => [name, { status: "PENDING", details: "" }]),
  );
  const evidenceUrls = {};

  const markPass = (stepName, details = "") => {
    report[stepName] = { status: "PASS", details };
  };

  const markFail = (stepName, errorOrMessage) => {
    const details =
      typeof errorOrMessage === "string"
        ? errorOrMessage
        : errorOrMessage?.message || "Unknown error";
    report[stepName] = { status: "FAIL", details: details.split("\n")[0] };
  };

  const markBlockedAsFail = (stepName, blocker) => {
    if (report[stepName].status === "PENDING") {
      markFail(stepName, `Blocked by previous failure: ${blocker}`);
    }
  };

  const takeCheckpoint = async (name, targetPage = page, fullPage = false) => {
    const path = testInfo.outputPath(`${name}.png`);
    await targetPage.screenshot({ path, fullPage });
    await testInfo.attach(name, {
      path,
      contentType: "image/png",
    });
  };

  const waitForUi = async (targetPage = page) => {
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.waitForLoadState("networkidle").catch(() => {});
    await targetPage.waitForTimeout(UI_SETTLE_MS);
  };

  const firstVisible = async (candidates) => {
    for (const locator of candidates) {
      if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
        return locator.first();
      }
    }
    return null;
  };

  const clickAndWait = async (locator, targetPage = page) => {
    await expect(locator).toBeVisible();
    await locator.click();
    await waitForUi(targetPage);
  };

  const ensureLoginPageReady = async () => {
    const currentUrl = page.url();
    if (currentUrl === "about:blank" && START_URL) {
      await page.goto(START_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
      return;
    }
    if (currentUrl === "about:blank" && !START_URL) {
      throw new Error(
        "Browser is on about:blank. Set SALEADS_LOGIN_URL or start from the SaleADS login page.",
      );
    }
    await waitForUi(page);
  };

  const getSidebar = () =>
    page.locator("aside, nav, [role='navigation']").first();

  const openLegalAndValidate = async ({
    linkText,
    headingText,
    reportKey,
    screenshotName,
  }) => {
    const legalLink = await firstVisible([
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByRole("button", { name: new RegExp(linkText, "i") }),
      page.getByText(new RegExp(linkText, "i")),
    ]);

    if (!legalLink) {
      throw new Error(`Could not find legal link '${linkText}'.`);
    }

    const popupPromise = context
      .waitForEvent("page", { timeout: 6000 })
      .catch(() => null);

    await clickAndWait(legalLink, page);

    const popupPage = await popupPromise;
    const targetPage = popupPage || page;
    await waitForUi(targetPage);

    await expect(
      targetPage.getByRole("heading", {
        name: new RegExp(headingText, "i"),
      }),
    ).toBeVisible();

    // Generic legal content check: require visible body text with enough length.
    const legalContent = targetPage.locator("main, article, body").first();
    await expect(legalContent).toContainText(/\S.{40,}/);

    evidenceUrls[reportKey] = targetPage.url();
    await takeCheckpoint(screenshotName, targetPage, true);

    if (popupPage) {
      await popupPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  };

  // Step 1: Login with Google
  try {
    await ensureLoginPageReady();

    const loginButton = await firstVisible([
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesión con google/i }),
      page.getByRole("button", { name: /continuar con google/i }),
      page.getByRole("link", { name: /sign in with google/i }),
      page.getByRole("link", { name: /iniciar sesión con google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesión con google/i),
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
    ]);

    if (!loginButton) {
      throw new Error("Google login button could not be found.");
    }

    const authPopupPromise = context
      .waitForEvent("page", { timeout: 6000 })
      .catch(() => null);

    await clickAndWait(loginButton, page);

    const authPopup = await authPopupPromise;
    const authPage = authPopup || page;
    await waitForUi(authPage);

    const accountOption = authPage.getByText(ACCOUNT_EMAIL, { exact: true });
    if ((await accountOption.count()) > 0 && (await accountOption.isVisible())) {
      await clickAndWait(accountOption, authPage);
    }

    if (authPopup) {
      await authPopup.waitForClose({ timeout: 30000 }).catch(() => {});
    }

    await waitForUi(page);
    await expect(getSidebar()).toBeVisible({ timeout: 45000 });
    await takeCheckpoint("01-dashboard-loaded", page, true);
    markPass("Login", "Dashboard loaded and sidebar visible.");
  } catch (error) {
    markFail("Login", error);
  }

  if (report["Login"].status === "FAIL") {
    for (const field of REPORT_FIELDS) {
      markBlockedAsFail(field, "Login");
    }
  } else {
    // Step 2: Open Mi Negocio menu
    try {
      const sidebar = getSidebar();
      await expect(sidebar).toBeVisible({ timeout: 30000 });

      const negocioSection = await firstVisible([
        sidebar.getByRole("button", { name: /^negocio$/i }),
        sidebar.getByRole("link", { name: /^negocio$/i }),
        sidebar.getByText(/^negocio$/i),
      ]);

      if (negocioSection) {
        await clickAndWait(negocioSection);
      }

      const miNegocioOption = await firstVisible([
        sidebar.getByRole("button", { name: /mi negocio/i }),
        sidebar.getByRole("link", { name: /mi negocio/i }),
        sidebar.getByText(/mi negocio/i),
      ]);

      if (!miNegocioOption) {
        throw new Error("Could not find 'Mi Negocio' option in sidebar.");
      }

      await clickAndWait(miNegocioOption);
      await expect(sidebar.getByText(/agregar negocio/i)).toBeVisible();
      await expect(sidebar.getByText(/administrar negocios/i)).toBeVisible();

      await takeCheckpoint("02-mi-negocio-menu-expanded");
      markPass("Mi Negocio menu", "Submenu expanded with expected options.");
    } catch (error) {
      markFail("Mi Negocio menu", error);
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const addBusinessEntry = await firstVisible([
        page.getByRole("menuitem", { name: /agregar negocio/i }),
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ]);

      if (!addBusinessEntry) {
        throw new Error("Could not find 'Agregar Negocio'.");
      }

      await clickAndWait(addBusinessEntry);

      await expect(
        page.getByRole("heading", { name: /crear nuevo negocio/i }),
      ).toBeVisible();
      await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible({
        timeout: 5000,
      });
      await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(
        page.getByRole("button", { name: /crear negocio/i }),
      ).toBeVisible();

      const nameInput = page.getByLabel(/nombre del negocio/i);
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
      await clickAndWait(page.getByRole("button", { name: /cancelar/i }));

      await takeCheckpoint("03-agregar-negocio-modal");
      markPass("Agregar Negocio modal", "Modal validated with required controls.");
    } catch (error) {
      markFail("Agregar Negocio modal", error);
    }

    // Step 4: Open Administrar Negocios
    try {
      if (
        !(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))
      ) {
        const sidebar = getSidebar();
        const miNegocioOption = await firstVisible([
          sidebar.getByRole("button", { name: /mi negocio/i }),
          sidebar.getByRole("link", { name: /mi negocio/i }),
          sidebar.getByText(/mi negocio/i),
        ]);
        if (miNegocioOption) {
          await clickAndWait(miNegocioOption);
        }
      }

      const manageBusinesses = await firstVisible([
        page.getByRole("menuitem", { name: /administrar negocios/i }),
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ]);

      if (!manageBusinesses) {
        throw new Error("Could not find 'Administrar Negocios'.");
      }

      await clickAndWait(manageBusinesses);
      await expect(page.getByText(/información general/i)).toBeVisible({
        timeout: 30000,
      });
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/sección legal/i)).toBeVisible();

      await takeCheckpoint("04-administrar-negocios-page", page, true);
      markPass(
        "Administrar Negocios view",
        "Account page loaded with all main sections.",
      );
    } catch (error) {
      markFail("Administrar Negocios view", error);
    }

    // Step 5: Validate Información General
    try {
      const infoGeneralContainer = page
        .locator("section, div")
        .filter({ has: page.getByText(/información general/i) })
        .first();

      await expect(infoGeneralContainer).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)).toBeVisible();
      await expect(page.getByText(/business plan/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

      // Name validation is intentionally generic to avoid coupling to a specific account.
      const identityCandidate = await firstVisible([
        page.getByText(/nombre/i),
        page.getByText(/usuario/i),
        infoGeneralContainer.locator("h1, h2, h3, h4, p, span").first(),
      ]);
      if (!identityCandidate) {
        throw new Error("No visible user identity field found.");
      }

      markPass(
        "Información General",
        "User identity, email, plan, and change-plan button are visible.",
      );
    } catch (error) {
      markFail("Información General", error);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await expect(page.getByText(/cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/estado activo/i)).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();

      markPass("Detalles de la Cuenta", "Expected account detail fields are visible.");
    } catch (error) {
      markFail("Detalles de la Cuenta", error);
    }

    // Step 7: Validate Tus Negocios
    try {
      const businessesSection = page
        .locator("section, div")
        .filter({ has: page.getByText(/tus negocios/i) })
        .first();
      await expect(businessesSection).toBeVisible();
      await expect(
        businessesSection.getByRole("button", { name: /agregar negocio/i }),
      ).toBeVisible();
      await expect(
        businessesSection.getByText(/tienes\s+2\s+de\s+3\s+negocios/i),
      ).toBeVisible();

      const listLikeContent = businessesSection
        .locator("li, [role='listitem'], table tbody tr, .card")
        .first();
      await expect(listLikeContent).toBeVisible();

      markPass(
        "Tus Negocios",
        "Business list, add button, and usage limit text are visible.",
      );
    } catch (error) {
      markFail("Tus Negocios", error);
    }

    // Step 8: Validate Términos y Condiciones
    try {
      await openLegalAndValidate({
        linkText: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        reportKey: "Términos y Condiciones",
        screenshotName: "08-terminos-y-condiciones",
      });
      markPass(
        "Términos y Condiciones",
        `Validated legal page. URL: ${evidenceUrls["Términos y Condiciones"]}`,
      );
    } catch (error) {
      markFail("Términos y Condiciones", error);
    }

    // Step 9: Validate Política de Privacidad
    try {
      await openLegalAndValidate({
        linkText: "Política de Privacidad",
        headingText: "Política de Privacidad",
        reportKey: "Política de Privacidad",
        screenshotName: "09-politica-de-privacidad",
      });
      markPass(
        "Política de Privacidad",
        `Validated legal page. URL: ${evidenceUrls["Política de Privacidad"]}`,
      );
    } catch (error) {
      markFail("Política de Privacidad", error);
    }
  }

  const finalReport = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, report[field]]),
  );

  // Step 10: Final report as requested (PASS/FAIL per validation step).
  await testInfo.attach("final-report.json", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json",
  });
  console.log("SaleADS Mi Negocio workflow report:");
  console.log(JSON.stringify(finalReport, null, 2));

  const failures = Object.entries(finalReport).filter(
    ([, value]) => value.status !== "PASS",
  );

  expect(
    failures,
    `One or more validations failed:\n${JSON.stringify(failures, null, 2)}`,
  ).toEqual([]);
});
