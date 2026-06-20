const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function waitForAnyVisible(candidates, timeout = 15000) {
  const end = Date.now() + timeout;

  while (Date.now() < end) {
    for (const locator of candidates) {
      const item = locator.first();

      if (await item.isVisible().catch(() => false)) {
        return item;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error("No candidate element became visible in time.");
}

function clickableCandidates(page, textPattern) {
  return [
    page.getByRole("button", { name: textPattern }),
    page.getByRole("link", { name: textPattern }),
    page.getByRole("menuitem", { name: textPattern }),
    page.getByRole("tab", { name: textPattern }),
    page.getByText(textPattern),
  ];
}

function visibleTextCandidates(page, textPattern) {
  return [
    page.getByRole("heading", { name: textPattern }),
    page.getByRole("button", { name: textPattern }),
    page.getByRole("link", { name: textPattern }),
    page.getByRole("menuitem", { name: textPattern }),
    page.getByText(textPattern),
  ];
}

async function clickByVisibleText(page, textPattern, timeout = 15000) {
  const end = Date.now() + timeout;
  const clickErrors = [];

  while (Date.now() < end) {
    for (const locator of clickableCandidates(page, textPattern)) {
      const item = locator.first();

      if (!(await item.isVisible().catch(() => false))) {
        continue;
      }

      try {
        await item.click({ timeout: 2000 });
        await waitForUi(page);
        return;
      } catch (error) {
        clickErrors.push(error.message);
      }
    }

    await page.waitForTimeout(200);
  }

  throw new Error(
    `Could not click target matching ${textPattern}. Recent click errors: ${clickErrors.slice(-3).join(" | ")}`,
  );
}

async function assertVisibleText(page, textPattern, timeout = 15000) {
  await waitForAnyVisible(visibleTextCandidates(page, textPattern), timeout);
}

async function saveScreenshot(page, testInfo, fileName, fullPage = true) {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function openLegalLinkAndValidate({
  appPage,
  context,
  linkPattern,
  headingPattern,
  screenshotName,
  testInfo,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const sameTabNavigationPromise = appPage
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 10000 })
    .catch(() => null);

  await clickByVisibleText(appPage, linkPattern);

  const popup = await popupPromise;
  let legalPage = appPage;
  let openedInNewTab = false;

  if (popup) {
    openedInNewTab = true;
    legalPage = popup;
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  } else {
    await sameTabNavigationPromise;
  }

  await assertVisibleText(legalPage, headingPattern, 20000);

  const legalContent = legalPage.locator("main p, article p, p").first();
  await legalContent.waitFor({ state: "visible", timeout: 15000 });
  const legalContentText = (await legalContent.innerText()).trim();

  if (legalContentText.length < 25) {
    throw new Error("Legal content text is too short to be considered valid.");
  }

  await saveScreenshot(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (openedInNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const startUrl = process.env.SALEADS_START_URL || process.env.SALEADS_URL || process.env.BASE_URL;

  if (!startUrl) {
    throw new Error(
      "Missing start URL. Set SALEADS_START_URL (or SALEADS_URL / BASE_URL) for the target environment.",
    );
  }

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  let termsUrl = "";
  let privacyUrl = "";

  async function runStep(reportField, stepFn) {
    try {
      await stepFn();
      report[reportField] = "PASS";
    } catch (error) {
      report[reportField] = "FAIL";
      failures.push(`${reportField}: ${error.message}`);
    }
  }

  await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runStep("Login", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await clickByVisibleText(page, /sign in with google|iniciar sesi[óo]n con google|google/i, 20000);

    const googlePage = await popupPromise;

    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
      const accountOption = googlePage.getByText(ACCOUNT_EMAIL, { exact: false }).first();

      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitForUi(googlePage);
      }

      await page.bringToFront();
    } else {
      const accountOptionSamePage = page.getByText(ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOptionSamePage.isVisible().catch(() => false)) {
        await accountOptionSamePage.click();
        await waitForUi(page);
      }
    }

    await waitForUi(page);
    await assertVisibleText(page, /negocio/i, 30000);
    await waitForAnyVisible([page.locator("aside"), page.locator("nav")], 20000);
    await saveScreenshot(page, testInfo, "01-dashboard-after-login.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const negocioVisible = await page.getByText(/negocio/i).first().isVisible().catch(() => false);
      if (negocioVisible) {
        await clickByVisibleText(page, /negocio/i);
      }
      await clickByVisibleText(page, /mi negocio/i);
    }

    await assertVisibleText(page, /agregar negocio/i);
    await assertVisibleText(page, /administrar negocios/i);
    await saveScreenshot(page, testInfo, "02-mi-negocio-expanded.png", false);
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /agregar negocio/i);

    await assertVisibleText(page, /crear nuevo negocio/i);
    const nombreInput = await waitForAnyVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator('input[name*="negocio" i]'),
      ],
      15000,
    );
    await assertVisibleText(page, /tienes 2 de 3 negocios/i);
    await assertVisibleText(page, /cancelar/i);
    await assertVisibleText(page, /crear negocio/i);

    await saveScreenshot(page, testInfo, "03-agregar-negocio-modal.png", false);

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, /cancelar/i);
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const adminVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!adminVisible) {
      await clickByVisibleText(page, /mi negocio/i);
    }

    await clickByVisibleText(page, /administrar negocios/i);

    await assertVisibleText(page, /informaci[óo]n general/i, 30000);
    await assertVisibleText(page, /detalles de la cuenta/i);
    await assertVisibleText(page, /tus negocios/i);
    await assertVisibleText(page, /secci[óo]n legal/i);

    await saveScreenshot(page, testInfo, "04-administrar-negocios-page.png", true);
  });

  await runStep("Información General", async () => {
    const infoSection = await waitForAnyVisible(
      [page.locator("section, div").filter({ hasText: /informaci[óo]n general/i })],
      15000,
    );
    const emailNode = await waitForAnyVisible(
      [page.getByText(ACCOUNT_EMAIL, { exact: false }), page.getByText(/@[a-z0-9.-]+\.[a-z]{2,}/i)],
      15000,
    );

    const sectionText = await infoSection.innerText();
    const normalizedSectionText = sectionText.replace(emailNode ? ACCOUNT_EMAIL : "", " ").trim();
    if (!/[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}/.test(normalizedSectionText)) {
      throw new Error("Could not detect a user name or user-identifying text in Información General.");
    }

    await assertVisibleText(page, /business plan/i);
    await assertVisibleText(page, /cambiar plan/i);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await assertVisibleText(page, /cuenta creada/i);
    await assertVisibleText(page, /estado activo/i);
    await assertVisibleText(page, /idioma seleccionado/i);
  });

  await runStep("Tus Negocios", async () => {
    const businessSection = await waitForAnyVisible(
      [page.locator("section, div").filter({ hasText: /tus negocios/i })],
      15000,
    );

    const cardsAndRows = businessSection.locator("li, tr, .card, .business-item, [data-testid*='business']");
    const hasItems = (await cardsAndRows.count()) > 0;
    if (!hasItems) {
      throw new Error("Business list container did not expose any business entries.");
    }

    await assertVisibleText(page, /agregar negocio/i);
    await assertVisibleText(page, /tienes 2 de 3 negocios/i);
  });

  await runStep("Términos y Condiciones", async () => {
    termsUrl = await openLegalLinkAndValidate({
      appPage: page,
      context,
      linkPattern: /t[ée]rminos y condiciones/i,
      headingPattern: /t[ée]rminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo,
    });

    await assertVisibleText(page, /informaci[óo]n general/i, 20000);
  });

  await runStep("Política de Privacidad", async () => {
    privacyUrl = await openLegalLinkAndValidate({
      appPage: page,
      context,
      linkPattern: /pol[íi]tica de privacidad/i,
      headingPattern: /pol[íi]tica de privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo,
    });

    await assertVisibleText(page, /informaci[óo]n general/i, 20000);
  });

  const finalReport = {
    generatedAt: new Date().toISOString(),
    startUrl,
    results: report,
    evidence: {
      termsAndConditionsUrl: termsUrl,
      privacyPolicyUrl: privacyUrl,
    },
    failures,
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log("SALEADS MI NEGOCIO FINAL REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  expect(
    failures,
    `Validation failures detected:\n${failures.map((failure) => `- ${failure}`).join("\n")}`,
  ).toEqual([]);
});
