const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function safeFileName(value) {
  return value
    .toLowerCase()
    .replace(/[^\w]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

async function isVisible(locator, timeout = 4000) {
  try {
    return await locator.first().isVisible({ timeout });
  } catch (error) {
    return false;
  }
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(700);

  const spinner = page
    .locator(
      '[aria-busy="true"], [class*="loading"], [class*="spinner"], [data-testid*="loading"]'
    )
    .first();
  if (await isVisible(spinner, 1000)) {
    await spinner.waitFor({ state: "hidden", timeout: 12000 }).catch(() => {});
  }
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const screenshotPath = testInfo.outputPath(`${safeFileName(name)}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function clickVisibleByText(page, patterns, failureLabel) {
  for (const pattern of patterns) {
    const candidates = [
      page.getByRole("button", { name: pattern }).first(),
      page.getByRole("link", { name: pattern }).first(),
      page.getByText(pattern).first(),
    ];

    for (const candidate of candidates) {
      if (await isVisible(candidate)) {
        await candidate.click();
        await waitForUi(page);
        return;
      }
    }
  }

  throw new Error(`Could not find clickable element for: ${failureLabel}`);
}

async function ensureSidebarVisible(page) {
  const sidebarCandidates = [
    page.locator("aside").first(),
    page.locator("nav").first(),
    page.getByText(/mi negocio|negocio/i).first(),
  ];

  for (const locator of sidebarCandidates) {
    if (await isVisible(locator, 30000)) {
      return;
    }
  }

  throw new Error("Main interface did not appear and sidebar is not visible.");
}

async function ensureAnyVisible(locators, failureMessage) {
  for (const locator of locators) {
    if (await isVisible(locator, 8000)) {
      return locator;
    }
  }

  throw new Error(failureMessage);
}

async function validateLegalDocument({
  page,
  context,
  testInfo,
  linkPattern,
  headingPattern,
  screenshotName,
}) {
  const appUrlBeforeClick = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);

  await clickVisibleByText(page, [linkPattern], `legal link: ${linkPattern}`);

  let legalPage = await popupPromise;
  if (!legalPage) {
    legalPage = page;
  }

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await waitForUi(legalPage);

  const headingCandidate = await ensureAnyVisible(
    [
      legalPage.getByRole("heading", { name: headingPattern }).first(),
      legalPage.getByText(headingPattern).first(),
    ],
    `Heading not found for legal page: ${headingPattern}`
  );
  await expect(headingCandidate).toBeVisible();

  const legalText = await legalPage.locator("body").innerText();
  expect(legalText).toMatch(/(t[eé]rminos|condiciones|privacidad|datos|informaci[oó]n|uso)/i);

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (legalPage !== page) {
    await legalPage.close();
    await page.bringToFront();
  } else {
    await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const saleadsLoginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL ||
    "";

  test.skip(
    !saleadsLoginUrl,
    "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to the login page of your target SaleADS environment."
  );

  await page.goto(saleadsLoginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  const evidence = {
    termsUrl: "",
    privacyUrl: "",
  };

  const runValidation = async (field, action) => {
    try {
      await test.step(field, action);
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      failures.push(`${field}: ${error.message}`);
    }
  };

  await runValidation("Login", async () => {
    const alreadyLoggedIn = await isVisible(page.getByText(/mi negocio|negocio/i).first(), 4000);

    if (!alreadyLoggedIn) {
      const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);

      await clickVisibleByText(
        page,
        [
          /sign in with google/i,
          /iniciar sesi[oó]n con google/i,
          /continuar con google/i,
          /google/i,
          /iniciar sesi[oó]n/i,
        ],
        "login button or Sign in with Google"
      );

      const googlePopup = await popupPromise;

      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});

        const accountCandidate = googlePopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
        if (await isVisible(accountCandidate, 8000)) {
          await accountCandidate.click();
        }

        await googlePopup.waitForClose({ timeout: 30000 }).catch(() => {});
      } else {
        const inlineAccountCandidate = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
        if (await isVisible(inlineAccountCandidate, 5000)) {
          await inlineAccountCandidate.click();
          await waitForUi(page);
        }
      }
    }

    await ensureSidebarVisible(page);
    await expect(page.getByText(/mi negocio|negocio/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, "dashboard_loaded");
  });

  await runValidation("Mi Negocio menu", async () => {
    await expect(page.getByText(/negocio/i).first()).toBeVisible();
    await clickVisibleByText(page, [/mi negocio/i], "Mi Negocio");

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "mi_negocio_menu_expanded");
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickVisibleByText(page, [/agregar negocio/i], "Agregar Negocio");

    const modalTitle = page.getByText(/crear nuevo negocio/i).first();
    await expect(modalTitle).toBeVisible();

    const businessNameInput = await ensureAnyVisible(
      [
        page.getByLabel(/nombre del negocio/i).first(),
        page.getByPlaceholder(/nombre del negocio/i).first(),
      ],
      "Input field 'Nombre del Negocio' not found."
    );
    await expect(businessNameInput).toBeVisible();

    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "agregar_negocio_modal");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickVisibleByText(page, [/cancelar/i], "Cancelar");
  });

  await runValidation("Administrar Negocios view", async () => {
    if (!(await isVisible(page.getByText(/administrar negocios/i).first(), 2000))) {
      await clickVisibleByText(page, [/mi negocio/i], "Mi Negocio");
    }

    await clickVisibleByText(page, [/administrar negocios/i], "Administrar Negocios");

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, "administrar_negocios_page", true);
  });

  await runValidation("Información General", async () => {
    const infoSection = page
      .locator("section, div")
      .filter({ has: page.getByText(/informaci[oó]n general/i) })
      .first();

    await expect(infoSection).toBeVisible();

    const infoText = await infoSection.innerText();
    expect(infoText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    expect(infoText).toMatch(/business plan/i);
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();

    const hasLikelyPersonName = infoText
      .split("\n")
      .map((line) => line.trim())
      .some(
        (line) =>
          /^[A-Za-zÀ-ÿ]{2,}(?:\s+[A-Za-zÀ-ÿ]{2,})+$/.test(line) &&
          !/informaci[oó]n general|business plan|cambiar plan|@/i.test(line)
      );

    expect(hasLikelyPersonName).toBeTruthy();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    const businessSection = page
      .locator("section, div")
      .filter({ has: page.getByText(/tus negocios/i) })
      .first();

    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(businessSection.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();

    const businessSectionText = await businessSection.innerText();
    const businessLines = businessSectionText
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line.length > 0);
    expect(businessLines.length).toBeGreaterThan(3);
  });

  await runValidation("Términos y Condiciones", async () => {
    evidence.termsUrl = await validateLegalDocument({
      page,
      context,
      testInfo,
      linkPattern: /t[eé]rminos y condiciones/i,
      headingPattern: /t[eé]rminos y condiciones/i,
      screenshotName: "terminos_y_condiciones",
    });
  });

  await runValidation("Política de Privacidad", async () => {
    evidence.privacyUrl = await validateLegalDocument({
      page,
      context,
      testInfo,
      linkPattern: /pol[ií]tica de privacidad/i,
      headingPattern: /pol[ií]tica de privacidad/i,
      screenshotName: "politica_de_privacidad",
    });
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls: {
      termsAndConditions: evidence.termsUrl,
      privacyPolicy: evidence.privacyUrl,
    },
    failures,
  };

  const reportPath = testInfo.outputPath(path.join("reports", "saleads_mi_negocio_full_test.report.json"));
  await fs.mkdir(path.dirname(reportPath), { recursive: true });
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.table(report);
  console.log(`Términos y Condiciones URL: ${evidence.termsUrl || "(not captured)"}`);
  console.log(`Política de Privacidad URL: ${evidence.privacyUrl || "(not captured)"}`);

  expect(
    Object.values(report).every((status) => status === "PASS"),
    `Validation failures:\n${failures.join("\n")}`
  ).toBeTruthy();
});
