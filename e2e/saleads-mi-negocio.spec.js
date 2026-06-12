const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const START_URL =
  process.env.SALEADS_LOGIN_URL ||
  process.env.SALEADS_BASE_URL ||
  process.env.BASE_URL ||
  "";

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

function ensureArtifactsDir(testInfo) {
  const artifactsDir = path.join(
    testInfo.project.outputDir,
    "saleads-mi-negocio-artifacts"
  );
  fs.mkdirSync(artifactsDir, { recursive: true });
  return artifactsDir;
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(500);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToLoad(page);
}

async function firstVisibleLocator(locators) {
  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }
  return null;
}

async function findAppPage(context) {
  for (let i = 0; i < 45; i += 1) {
    for (const page of context.pages()) {
      const hasSidebar = await page
        .locator("aside, nav, [class*='sidebar'], [data-testid*='sidebar']")
        .first()
        .isVisible()
        .catch(() => false);

      const hasBusinessText = await page
        .getByText(/negocio|dashboard|inicio/i)
        .first()
        .isVisible()
        .catch(() => false);

      if (hasSidebar || hasBusinessText) {
        return page;
      }
    }

    await context.pages()[0].waitForTimeout(2000);
  }

  throw new Error("Main application interface did not appear after login.");
}

async function sectionByHeading(page, headingRegex) {
  const heading = page.getByText(headingRegex).first();
  await expect(heading).toBeVisible();

  return (
    heading.locator("xpath=ancestor::*[self::section or self::div][1]").first() ||
    page.locator("body")
  );
}

async function ensureMiNegocioExpanded(page) {
  const agregarVisible = await page
    .getByText(/agregar negocio/i)
    .first()
    .isVisible()
    .catch(() => false);
  const administrarVisible = await page
    .getByText(/administrar negocios/i)
    .first()
    .isVisible()
    .catch(() => false);

  if (agregarVisible && administrarVisible) {
    return;
  }

  const miNegocioButton = await firstVisibleLocator([
    page.getByRole("button", { name: /^mi negocio$/i }),
    page.getByRole("link", { name: /^mi negocio$/i }),
    page.getByText(/^mi negocio$/i),
  ]);

  if (!miNegocioButton) {
    throw new Error("Could not find 'Mi Negocio' menu item.");
  }

  await clickAndWait(page, miNegocioButton);

  // Some UIs toggle the accordion on each click. If still hidden, click once more.
  if (
    !(await page.getByText(/agregar negocio/i).first().isVisible().catch(() => false))
  ) {
    await clickAndWait(page, miNegocioButton);
  }

  await expect(page.getByText(/agregar negocio/i)).toBeVisible();
  await expect(page.getByText(/administrar negocios/i)).toBeVisible();
}

test("SaleADS Mi Negocio full workflow", async ({ page, context }, testInfo) => {
  test.setTimeout(300000);
  const artifactsDir = ensureArtifactsDir(testInfo);
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const evidence = {
    "Términos y Condiciones": null,
    "Política de Privacidad": null,
  };

  async function checkpoint(name, targetPage = page, fullPage = false) {
    await targetPage.screenshot({
      path: path.join(artifactsDir, name),
      fullPage,
    });
  }

  async function runStep(reportField, stepFn) {
    try {
      await stepFn();
      report[reportField] = "PASS";
      console.log(`[PASS] ${reportField}`);
    } catch (error) {
      report[reportField] = "FAIL";
      console.error(`[FAIL] ${reportField}: ${error.message}`);
    }
  }

  let appPage = page;
  let loginSucceeded = false;
  let adminViewLoaded = false;

  await runStep("Login", async () => {
    if (START_URL) {
      await page.goto(START_URL, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    } else {
      throw new Error(
        "Missing SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL). " +
          "To keep the test environment-agnostic, pass the login URL at runtime."
      );
    }

    const loginLocator = await firstVisibleLocator([
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      }),
      page.getByText(
        /sign in with google|iniciar sesi[oó]n con google|continuar con google/i
      ),
    ]);

    if (!loginLocator) {
      throw new Error("Google login button/link not found on login page.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, loginLocator);

    const googlePage = await popupPromise;
    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded");
      const accountOption = await firstVisibleLocator([
        googlePage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
        googlePage.getByText(new RegExp(ACCOUNT_EMAIL, "i")),
      ]);

      if (accountOption) {
        await accountOption.click();
      }
    } else {
      const accountOption = await firstVisibleLocator([
        page.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
        page.getByText(new RegExp(ACCOUNT_EMAIL, "i")),
      ]);

      if (accountOption) {
        await clickAndWait(page, accountOption);
      }
    }

    appPage = await findAppPage(context);
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);

    await expect(
      appPage
        .locator("aside, nav, [class*='sidebar'], [data-testid*='sidebar']")
        .first()
    ).toBeVisible();

    await checkpoint("01-dashboard-loaded.png", appPage, true);
    loginSucceeded = true;
  });

  await runStep("Mi Negocio menu", async () => {
    if (!loginSucceeded) {
      throw new Error("Precondition failed: Login step did not pass.");
    }
    await ensureMiNegocioExpanded(appPage);
    await checkpoint("02-mi-negocio-menu-expanded.png", appPage);
  });

  await runStep("Agregar Negocio modal", async () => {
    if (!loginSucceeded) {
      throw new Error("Precondition failed: Login step did not pass.");
    }

    await clickAndWait(appPage, appPage.getByText(/agregar negocio/i).first());

    const modalTitle = appPage.getByText(/crear nuevo negocio/i).first();
    await expect(modalTitle).toBeVisible();
    await expect(appPage.getByText(/nombre del negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(appPage.getByRole("button", { name: /crear negocio/i })).toBeVisible();
    await checkpoint("03-agregar-negocio-modal.png", appPage);

    const nameInput = await firstVisibleLocator([
      appPage.getByLabel(/nombre del negocio/i),
      appPage.getByPlaceholder(/nombre del negocio/i),
      appPage.getByRole("textbox").first(),
    ]);

    if (nameInput) {
      await nameInput.fill("Negocio Prueba Automatización");
    }

    await clickAndWait(appPage, appPage.getByRole("button", { name: /cancelar/i }));
  });

  await runStep("Administrar Negocios view", async () => {
    if (!loginSucceeded) {
      throw new Error("Precondition failed: Login step did not pass.");
    }
    await ensureMiNegocioExpanded(appPage);

    await clickAndWait(appPage, appPage.getByText(/administrar negocios/i).first());

    await expect(appPage.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(appPage.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(appPage.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(appPage.getByText(/secci[oó]n legal/i).first()).toBeVisible();
    await checkpoint("04-administrar-negocios-account-page.png", appPage, true);
    adminViewLoaded = true;
  });

  await runStep("Información General", async () => {
    if (!adminViewLoaded) {
      throw new Error("Precondition failed: Administrar Negocios view not loaded.");
    }

    const infoSection = await sectionByHeading(appPage, /informaci[oó]n general/i);

    const userNameCandidate = await firstVisibleLocator([
      infoSection.getByText(/nombre/i).first(),
      infoSection.getByText(/usuario/i).first(),
      infoSection
        .locator("h1, h2, h3, h4, p, span, div")
        .filter({ hasText: /[A-Za-z]{3,}/ })
        .first(),
    ]);

    if (!userNameCandidate) {
      throw new Error("User name was not visible in 'Información General'.");
    }

    await expect(infoSection.getByText(/@/).first()).toBeVisible();
    await expect(infoSection.getByText(/business plan/i).first()).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    if (!adminViewLoaded) {
      throw new Error("Precondition failed: Administrar Negocios view not loaded.");
    }

    const detailsSection = await sectionByHeading(appPage, /detalles de la cuenta/i);

    await expect(detailsSection.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/estado activo/i).first()).toBeVisible();
    await expect(detailsSection.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    if (!adminViewLoaded) {
      throw new Error("Precondition failed: Administrar Negocios view not loaded.");
    }

    const businessSection = await sectionByHeading(appPage, /tus negocios/i);

    await expect(
      businessSection
        .locator("[role='list'], ul, table, [class*='business'], [class*='negocio']")
        .first()
    ).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(businessSection.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  async function validateLegalLink(linkTextRegex, headingRegex, reportField, screenshotName) {
    const legalSection = await sectionByHeading(appPage, /secci[oó]n legal/i);
    const link = await firstVisibleLocator([
      legalSection.getByRole("link", { name: linkTextRegex }),
      legalSection.getByText(linkTextRegex).first(),
    ]);

    if (!link) {
      throw new Error(`Legal link not found: ${linkTextRegex}`);
    }

    const previousUrl = appPage.url();
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await link.click();

    const popup = await popupPromise;
    const legalPage = popup || appPage;
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle").catch(() => {});

    await expect(legalPage.getByText(headingRegex).first()).toBeVisible();
    await expect(
      legalPage
        .locator("article, section, main, p")
        .filter({ hasText: /.+/ })
        .first()
    ).toBeVisible();

    evidence[reportField] = legalPage.url();
    console.log(`[EVIDENCE] ${reportField} URL: ${legalPage.url()}`);
    await checkpoint(screenshotName, legalPage, true);

    if (popup) {
      await appPage.bringToFront();
      return;
    }

    if (appPage.url() !== previousUrl) {
      await appPage.goBack({ waitUntil: "domcontentloaded" });
      await waitForUiToLoad(appPage);
    }
  }

  await runStep("Términos y Condiciones", async () => {
    if (!adminViewLoaded) {
      throw new Error("Precondition failed: Administrar Negocios view not loaded.");
    }

    await validateLegalLink(
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "Términos y Condiciones",
      "05-terminos-y-condiciones.png"
    );
  });

  await runStep("Política de Privacidad", async () => {
    if (!adminViewLoaded) {
      throw new Error("Precondition failed: Administrar Negocios view not loaded.");
    }

    await validateLegalLink(
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "Política de Privacidad",
      "06-politica-de-privacidad.png"
    );
  });

  const finalReport = {
    report,
    evidence,
  };

  const reportPath = path.join(artifactsDir, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  console.log("FINAL_REPORT_START");
  console.log(JSON.stringify(finalReport, null, 2));
  console.log("FINAL_REPORT_END");

  const failed = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);

  expect(
    failed,
    `One or more SaleADS workflow validations failed: ${failed.join(", ")}`
  ).toEqual([]);
});
