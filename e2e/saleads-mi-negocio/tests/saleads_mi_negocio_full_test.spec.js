const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL =
  process.env.SALEADS_LOGIN_URL ||
  process.env.SALEADS_BASE_URL ||
  process.env.BASE_URL ||
  "";
const EXPECTED_USER_NAME = process.env.SALEADS_EXPECTED_USER_NAME || "";

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

function buildReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {});
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiToLoad(page) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 15_000 }),
    page.waitForLoadState("networkidle", { timeout: 15_000 }),
  ]);
  await page.waitForTimeout(400);
}

function candidatesByText(page, regex) {
  return [
    page.getByRole("button", { name: regex }).first(),
    page.getByRole("link", { name: regex }).first(),
    page.getByRole("menuitem", { name: regex }).first(),
    page.getByRole("tab", { name: regex }).first(),
    page.getByRole("heading", { name: regex }).first(),
    page.getByRole("option", { name: regex }).first(),
    page.getByLabel(regex).first(),
    page.getByText(regex).first(),
  ];
}

async function firstVisible(candidates, timeoutMs = 8_000) {
  const timeoutPerLocator = Math.max(750, Math.floor(timeoutMs / candidates.length));
  for (const locator of candidates) {
    try {
      await locator.waitFor({ state: "visible", timeout: timeoutPerLocator });
      return locator;
    } catch (error) {
      // Try next candidate.
    }
  }
  throw new Error("No visible element found for the provided candidates.");
}

async function clickByVisibleText(page, regex, timeoutMs = 8_000) {
  const locator = await firstVisible(candidatesByText(page, regex), timeoutMs);
  await locator.click();
  await waitForUiToLoad(page);
  return locator;
}

async function ensureTextVisible(page, regex, timeoutMs = 8_000) {
  const locator = await firstVisible(candidatesByText(page, regex), timeoutMs);
  await expect(locator).toBeVisible();
  return locator;
}

async function takeCheckpoint(page, testInfo, name, fullPage = false) {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, {
    path,
    contentType: "image/png",
  });
}

async function ensureSidebarVisible(page) {
  const sidebarCandidates = [
    page.locator("aside:visible").first(),
    page.locator("nav:visible").first(),
    page.locator('[role="navigation"]:visible').first(),
    page.getByText(/negocio|dashboard|mi negocio/i).first(),
  ];
  const sidebar = await firstVisible(sidebarCandidates, 12_000);
  await expect(sidebar).toBeVisible();
}

async function validateLegalLink({
  appPage,
  linkRegex,
  headingRegex,
  screenshotName,
  testInfo,
  legalSection,
}) {
  const context = appPage.context();
  const linkCandidates = legalSection
    ? [
        legalSection.getByRole("link", { name: linkRegex }).first(),
        legalSection.getByText(linkRegex).first(),
      ]
    : candidatesByText(appPage, linkRegex);

  const link = await firstVisible(linkCandidates, 10_000);
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  const previousUrl = appPage.url();

  await link.click();

  const popup = await popupPromise;
  let targetPage = appPage;

  if (popup) {
    targetPage = popup;
    await targetPage.waitForLoadState("domcontentloaded", { timeout: 20_000 });
    await targetPage.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
  } else {
    if (appPage.url() === previousUrl) {
      await appPage.waitForURL((url) => url !== previousUrl, { timeout: 15_000 }).catch(() => {});
    }
    await waitForUiToLoad(appPage);
  }

  await ensureTextVisible(targetPage, headingRegex, 20_000);
  await takeCheckpoint(targetPage, testInfo, screenshotName, true);

  const finalUrl = targetPage.url();
  console.log(`[EVIDENCE] ${screenshotName} URL: ${finalUrl}`);

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToLoad(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildReport();
  const diagnostics = {};

  async function runField(field, fn) {
    try {
      await fn();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      diagnostics[field] = error instanceof Error ? error.message : String(error);
    }
  }

  if (SALEADS_LOGIN_URL) {
    await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUiToLoad(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No login URL provided. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to the current SaleADS login page."
    );
  }

  await runField("Login", async () => {
    const loginPopupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickByVisibleText(
      page,
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|acceder con google|google/i,
      15_000
    );

    const popup = await loginPopupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 });

      const accountMatcher = new RegExp(`^${escapeRegExp(GOOGLE_ACCOUNT_EMAIL)}$`, "i");
      const accountCandidates = [
        popup.getByRole("button", { name: accountMatcher }).first(),
        popup.getByText(accountMatcher).first(),
      ];

      const account = await firstVisible(accountCandidates, 10_000).catch(() => null);
      if (account) {
        await account.click();
      }
      await popup.waitForEvent("close", { timeout: 30_000 }).catch(() => {});
    } else {
      const accountMatcher = new RegExp(`^${escapeRegExp(GOOGLE_ACCOUNT_EMAIL)}$`, "i");
      const account = await firstVisible(candidatesByText(page, accountMatcher), 6_000).catch(() => null);
      if (account) {
        await account.click();
      }
    }

    await page.bringToFront();
    await waitForUiToLoad(page);
    await ensureSidebarVisible(page);
    await takeCheckpoint(page, testInfo, "01-dashboard-loaded");
  });

  await runField("Mi Negocio menu", async () => {
    await ensureSidebarVisible(page);
    await clickByVisibleText(page, /negocio/i, 10_000).catch(() => {});
    await clickByVisibleText(page, /mi negocio/i, 10_000);

    await ensureTextVisible(page, /agregar negocio/i, 10_000);
    await ensureTextVisible(page, /administrar negocios/i, 10_000);
    await takeCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
  });

  await runField("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /agregar negocio/i, 10_000);
    await ensureTextVisible(page, /crear nuevo negocio/i, 10_000);
    await ensureTextVisible(page, /nombre del negocio/i, 10_000);
    await ensureTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i, 10_000);
    await ensureTextVisible(page, /cancelar/i, 10_000);
    await ensureTextVisible(page, /crear negocio/i, 10_000);

    const nameInput = await firstVisible(
      [
        page.getByLabel(/nombre del negocio/i).first(),
        page.getByPlaceholder(/nombre del negocio/i).first(),
        page.locator("input:visible").first(),
      ],
      10_000
    );
    await nameInput.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, /cancelar/i, 10_000);

    await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal");
  });

  await runField("Administrar Negocios view", async () => {
    const adminVisible = await firstVisible(candidatesByText(page, /administrar negocios/i), 4_000).catch(() => null);
    if (!adminVisible) {
      await clickByVisibleText(page, /mi negocio/i, 8_000);
    }

    await clickByVisibleText(page, /administrar negocios/i, 10_000);
    await ensureTextVisible(page, /informaci[oó]n general/i, 12_000);
    await ensureTextVisible(page, /detalles de la cuenta/i, 12_000);
    await ensureTextVisible(page, /tus negocios/i, 12_000);
    await ensureTextVisible(page, /secci[oó]n legal/i, 12_000);
    await takeCheckpoint(page, testInfo, "04-administrar-negocios", true);
  });

  await runField("Información General", async () => {
    await ensureTextVisible(page, /informaci[oó]n general/i, 12_000);

    const emailLocator = await firstVisible(
      [
        page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first(),
        page.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i")).first(),
      ],
      12_000
    );
    await expect(emailLocator).toBeVisible();

    if (EXPECTED_USER_NAME) {
      await ensureTextVisible(page, new RegExp(escapeRegExp(EXPECTED_USER_NAME), "i"), 8_000);
    } else {
      const pageText = await page.locator("body").innerText();
      const hasLikelyName = /juan|lucas|barbier|garzon/i.test(pageText) || /nombre/i.test(pageText);
      expect(
        hasLikelyName,
        "No se detectó un nombre de usuario claro; define SALEADS_EXPECTED_USER_NAME para validación estricta."
      ).toBeTruthy();
    }

    await ensureTextVisible(page, /business plan/i, 10_000);
    await ensureTextVisible(page, /cambiar plan/i, 10_000);
  });

  await runField("Detalles de la Cuenta", async () => {
    await ensureTextVisible(page, /cuenta creada/i, 10_000);
    await ensureTextVisible(page, /estado activo/i, 10_000);
    await ensureTextVisible(page, /idioma seleccionado/i, 10_000);
  });

  await runField("Tus Negocios", async () => {
    await ensureTextVisible(page, /tus negocios/i, 10_000);
    await ensureTextVisible(page, /agregar negocio/i, 10_000);
    await ensureTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i, 10_000);

    const listOrItem = await firstVisible(
      [
        page.locator("[role='list'] > *:visible").first(),
        page.locator("table tbody tr:visible").first(),
        page.locator("ul li:visible").first(),
        page.locator("[class*='business'], [id*='business']").first(),
      ],
      8_000
    );
    await expect(listOrItem).toBeVisible();
  });

  const legalSection = await firstVisible(
    [
      page.locator("section,div").filter({ hasText: /secci[oó]n legal/i }).first(),
      page.getByText(/secci[oó]n legal/i).locator("xpath=ancestor::*[self::section or self::div][1]").first(),
    ],
    10_000
  ).catch(() => null);

  await runField("Términos y Condiciones", async () => {
    await validateLegalLink({
      appPage: page,
      linkRegex: /t[ée]rminos y condiciones/i,
      headingRegex: /t[ée]rminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones",
      testInfo,
      legalSection,
    });
  });

  await runField("Política de Privacidad", async () => {
    await validateLegalLink({
      appPage: page,
      linkRegex: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: "06-politica-de-privacidad",
      testInfo,
      legalSection,
    });
  });

  const reportPayload = {
    name: "saleads_mi_negocio_full_test",
    results: report,
    diagnostics,
  };
  console.log("FINAL_REPORT", JSON.stringify(reportPayload, null, 2));

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(reportPayload, null, 2), "utf-8"),
    contentType: "application/json",
  });

  const failed = Object.entries(report).filter(([, status]) => status !== "PASS");
  expect(
    failed,
    `Some workflow validations failed:\n${JSON.stringify(reportPayload, null, 2)}`
  ).toEqual([]);
});
