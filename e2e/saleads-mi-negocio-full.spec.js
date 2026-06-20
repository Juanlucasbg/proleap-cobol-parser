const { test, expect } = require("@playwright/test");

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
];

function createReport() {
  const report = {};

  for (const field of REPORT_FIELDS) {
    report[field] = {
      status: "FAIL",
      details: ""
    };
  }

  return report;
}

function mark(report, field, status, details) {
  report[field] = {
    status,
    details
  };
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function clickFirstVisible(candidates, actionLabel, timeoutMs = 20000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const candidate of candidates) {
      const visible = await candidate.first().isVisible({ timeout: 1200 }).catch(() => false);

      if (visible) {
        await candidate.first().click();
        return;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 300));
  }

  throw new Error(`Could not find visible element to click for: ${actionLabel} (timeout ${timeoutMs}ms)`);
}

async function ensureVisible(candidates, assertLabel, timeoutMs = 20000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const candidate of candidates) {
      const visible = await candidate.first().isVisible({ timeout: 1200 }).catch(() => false);

      if (visible) {
        return candidate.first();
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 300));
  }

  throw new Error(`Could not validate visibility for: ${assertLabel} (timeout ${timeoutMs}ms)`);
}

async function checkpoint(page, testInfo, name, fullPage = false) {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

function getLoginUrl() {
  return (
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    process.env.PLAYWRIGHT_TEST_BASE_URL ||
    ""
  );
}

function hasLegalContent(rawText) {
  const text = (rawText || "").replace(/\s+/g, " ").trim();
  return text.length >= 180;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();

  let termsUrl = "";
  let privacyUrl = "";
  let runtimeErrorMessage = "";

  try {
    // Step 1: Login with Google
    const loginUrl = getLoginUrl();
    if (page.url() === "about:blank") {
      if (!loginUrl) {
        throw new Error(
          "No login URL available. Set SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL) to any SaleADS environment login page."
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    await clickFirstVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        page.locator("button:has-text('Google'), a:has-text('Google')")
      ],
      "Login with Google"
    );
    await waitForUi(page);

    const accountChoice = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i);
    const chooserVisible = await accountChoice.first().isVisible({ timeout: 12000 }).catch(() => false);
    if (chooserVisible) {
      await accountChoice.first().click();
      await waitForUi(page);
    }

    await ensureVisible(
      [
        page.locator("main"),
        page.getByText(/dashboard|inicio|mi negocio|negocio/i),
        page.getByRole("navigation")
      ],
      "Main application interface"
    );

    await ensureVisible(
      [
        page.locator("aside"),
        page.locator("nav").filter({ hasText: /negocio|mi negocio/i }),
        page.getByRole("navigation").filter({ hasText: /negocio|mi negocio/i })
      ],
      "Left sidebar navigation"
    );

    await checkpoint(page, testInfo, "step-1-dashboard-loaded");
    mark(report, "Login", "PASS", "Logged in and sidebar visible.");

    // Step 2: Open Mi Negocio menu
    await clickFirstVisible(
      [
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^Negocio$/i)
      ],
      "Negocio section"
    );
    await waitForUi(page);

    await clickFirstVisible(
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      "Mi Negocio option"
    );
    await waitForUi(page);

    await ensureVisible(
      [page.getByText(/agregar negocio/i), page.getByRole("button", { name: /agregar negocio/i })],
      "Agregar Negocio in submenu"
    );
    await ensureVisible(
      [page.getByText(/administrar negocios/i), page.getByRole("button", { name: /administrar negocios/i })],
      "Administrar Negocios in submenu"
    );

    await checkpoint(page, testInfo, "step-2-mi-negocio-expanded");
    mark(report, "Mi Negocio menu", "PASS", "Mi Negocio submenu expanded with expected options.");

    // Step 3: Validate Agregar Negocio modal
    await clickFirstVisible(
      [
        page.locator("aside, nav").getByRole("button", { name: /agregar negocio/i }),
        page.locator("aside, nav").getByText(/agregar negocio/i),
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio"
    );
    await waitForUi(page);

    await ensureVisible([page.getByText(/crear nuevo negocio/i)], "Modal title Crear Nuevo Negocio");
    await ensureVisible(
      [page.getByLabel(/nombre del negocio/i), page.getByPlaceholder(/nombre del negocio/i)],
      "Input Nombre del Negocio"
    );
    await ensureVisible([page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)], "Tienes 2 de 3 negocios text");
    await ensureVisible([page.getByRole("button", { name: /cancelar/i })], "Cancelar button");
    await ensureVisible([page.getByRole("button", { name: /crear negocio/i })], "Crear Negocio button");

    await checkpoint(page, testInfo, "step-3-crear-negocio-modal");

    const businessNameInput = await ensureVisible(
      [page.getByLabel(/nombre del negocio/i), page.getByPlaceholder(/nombre del negocio/i)],
      "Business name input field for optional typing"
    );
    const canTypeBusinessName = await businessNameInput.isVisible({ timeout: 1000 }).catch(() => false);
    if (canTypeBusinessName) {
      await businessNameInput.fill("Negocio Prueba Automatización");
    }

    await clickFirstVisible([page.getByRole("button", { name: /cancelar/i })], "Cancelar modal");
    await waitForUi(page);

    mark(report, "Agregar Negocio modal", "PASS", "Modal validations completed and closed.");

    // Step 4: Open Administrar Negocios
    const adminVisibleBefore = await page
      .getByText(/administrar negocios/i)
      .first()
      .isVisible({ timeout: 2000 })
      .catch(() => false);
    if (!adminVisibleBefore) {
      await clickFirstVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i)
        ],
        "Re-expand Mi Negocio"
      );
      await waitForUi(page);
    }

    await clickFirstVisible(
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      "Administrar Negocios"
    );
    await waitForUi(page);

    await ensureVisible([page.getByText(/informaci[oó]n general/i)], "Información General section");
    await ensureVisible([page.getByText(/detalles de la cuenta/i)], "Detalles de la Cuenta section");
    await ensureVisible([page.getByText(/tus negocios/i)], "Tus Negocios section");
    await ensureVisible([page.getByText(/secci[oó]n legal/i)], "Sección Legal section");

    await checkpoint(page, testInfo, "step-4-account-page-full", true);
    mark(report, "Administrar Negocios view", "PASS", "Account page loaded with all expected sections.");

    // Step 5: Validate Información General
    await ensureVisible([page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)], "User email visibility");
    await ensureVisible([page.getByText(/business plan/i)], "BUSINESS PLAN text");
    await ensureVisible([page.getByRole("button", { name: /cambiar plan/i })], "Cambiar Plan button");

    const infoText = (await page.locator("body").innerText()).replace(/\s+/g, " ");
    if (!/\b[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ]+(\s+[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ]+)+\b/.test(infoText)) {
      throw new Error("Could not confidently detect a visible user name in Información General.");
    }

    mark(report, "Información General", "PASS", "Name, email, plan and change-plan button are visible.");

    // Step 6: Validate Detalles de la Cuenta
    await ensureVisible([page.getByText(/cuenta creada/i)], "Cuenta creada text");
    await ensureVisible([page.getByText(/estado activo/i)], "Estado activo text");
    await ensureVisible([page.getByText(/idioma seleccionado/i)], "Idioma seleccionado text");

    mark(report, "Detalles de la Cuenta", "PASS", "Detalles de la Cuenta fields validated.");

    // Step 7: Validate Tus Negocios
    await ensureVisible([page.getByText(/tus negocios/i)], "Tus Negocios heading");
    await ensureVisible([page.getByRole("button", { name: /agregar negocio/i })], "Agregar Negocio button in list");
    await ensureVisible([page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)], "Tienes 2 de 3 negocios text");

    mark(report, "Tus Negocios", "PASS", "Business list, action button and limit text are visible.");

    // Step 8: Validate Términos y Condiciones
    const termsLink = page
      .getByRole("link", { name: /t[eé]rminos y condiciones/i })
      .or(page.getByText(/t[eé]rminos y condiciones/i))
      .first();

    const termsPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await termsLink.click();
    await waitForUi(page);
    const termsPopup = await termsPopupPromise;

    let termsPage = page;
    if (termsPopup) {
      termsPage = termsPopup;
      await termsPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
      await waitForUi(termsPage);
    }

    await ensureVisible(
      [
        termsPage.getByRole("heading", { name: /t[eé]rminos y condiciones/i }),
        termsPage.getByText(/t[eé]rminos y condiciones/i)
      ],
      "Términos y Condiciones heading"
    );

    const termsBodyText = await termsPage.locator("body").innerText();
    if (!hasLegalContent(termsBodyText)) {
      throw new Error("Términos y Condiciones content is not sufficiently visible.");
    }

    termsUrl = termsPage.url();
    await checkpoint(termsPage, testInfo, "step-8-terminos-y-condiciones");

    if (termsPopup) {
      await termsPage.close();
      await page.bringToFront();
    } else {
      await page.goBack().catch(() => {});
      await waitForUi(page);
    }

    mark(report, "Términos y Condiciones", "PASS", `Validated legal page at URL: ${termsUrl}`);

    // Step 9: Validate Política de Privacidad
    const privacyLink = page
      .getByRole("link", { name: /pol[ií]tica de privacidad/i })
      .or(page.getByText(/pol[ií]tica de privacidad/i))
      .first();

    const privacyPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await privacyLink.click();
    await waitForUi(page);
    const privacyPopup = await privacyPopupPromise;

    let privacyPage = page;
    if (privacyPopup) {
      privacyPage = privacyPopup;
      await privacyPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
      await waitForUi(privacyPage);
    }

    await ensureVisible(
      [
        privacyPage.getByRole("heading", { name: /pol[ií]tica de privacidad/i }),
        privacyPage.getByText(/pol[ií]tica de privacidad/i)
      ],
      "Política de Privacidad heading"
    );

    const privacyBodyText = await privacyPage.locator("body").innerText();
    if (!hasLegalContent(privacyBodyText)) {
      throw new Error("Política de Privacidad content is not sufficiently visible.");
    }

    privacyUrl = privacyPage.url();
    await checkpoint(privacyPage, testInfo, "step-9-politica-de-privacidad");

    if (privacyPopup) {
      await privacyPage.close();
      await page.bringToFront();
    } else {
      await page.goBack().catch(() => {});
      await waitForUi(page);
    }

    mark(report, "Política de Privacidad", "PASS", `Validated legal page at URL: ${privacyUrl}`);
  } catch (error) {
    // Preserve failure details while still producing a final per-step report.
    const message = error instanceof Error ? error.message : String(error);
    runtimeErrorMessage = message;

    for (const field of REPORT_FIELDS) {
      if (!report[field].details) {
        mark(report, field, "FAIL", `Not completed due to runtime error: ${message}`);
      }
    }

    await testInfo.attach("runtime-error", {
      body: Buffer.from(message, "utf-8"),
      contentType: "text/plain"
    });
  }

  const finalReport = {
    workflow: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    finalUrls: {
      termsAndConditions: termsUrl || "N/A",
      privacyPolicy: privacyUrl || "N/A"
    },
    runtimeError: runtimeErrorMessage || "N/A",
    results: report
  };

  await testInfo.attach("final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json"
  });

  const failedFields = REPORT_FIELDS.filter((field) => report[field].status !== "PASS");
  expect(
    failedFields,
    `One or more validations failed. Full report:\n${JSON.stringify(finalReport, null, 2)}`
  ).toEqual([]);
});
