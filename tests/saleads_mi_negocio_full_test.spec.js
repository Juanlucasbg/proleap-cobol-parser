const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL;

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

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiAfterClick(page) {
  await Promise.all([
    page.waitForLoadState("networkidle", { timeout: 7_500 }).catch(() => {}),
    page.waitForTimeout(700),
  ]);
}

async function clickFirstVisible(locatorEntries, description) {
  for (const entry of locatorEntries) {
    const locator = entry.first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      return locator;
    }
  }

  throw new Error(`Could not find visible element for: ${description}`);
}

async function expectVisibleAny(locatorEntries, description) {
  for (const entry of locatorEntries) {
    const locator = entry.first();
    if (await locator.isVisible().catch(() => false)) {
      await expect(locator).toBeVisible();
      return locator;
    }
  }

  throw new Error(`Expected visible element was not found: ${description}`);
}

async function maybeSelectGoogleAccount(pageLike, accountEmail) {
  const emailPattern = new RegExp(escapeRegex(accountEmail), "i");
  const accountLocatorCandidates = [
    pageLike.getByRole("button", { name: emailPattern }),
    pageLike.getByRole("link", { name: emailPattern }),
    pageLike.getByText(emailPattern),
  ];

  for (const candidate of accountLocatorCandidates) {
    const locator = candidate.first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      return true;
    }
  }

  return false;
}

async function captureCheckpoint(pageLike, testInfo, checkpointName, fullPage = false) {
  const safeName = checkpointName.toLowerCase().replace(/[^a-z0-9]+/g, "_");
  const imagePath = testInfo.outputPath(`${safeName}.png`);
  await pageLike.screenshot({ path: imagePath, fullPage });
  return imagePath;
}

async function validateLegalPage({
  page,
  context,
  testInfo,
  linkName,
  headingText,
  checkpointName,
}) {
  const appUrlBeforeClick = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await clickFirstVisible(
    [
      page.getByRole("link", { name: linkName }),
      page.getByText(linkName),
      page.getByRole("button", { name: linkName }),
    ],
    linkName,
  );
  await waitForUiAfterClick(page);

  const popupPage = await popupPromise;
  const legalPage = popupPage || page;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 60_000 });

  const headingPattern = new RegExp(headingText, "i");
  await expectVisibleAny(
    [
      legalPage.getByRole("heading", { name: headingPattern }),
      legalPage.getByText(headingPattern),
    ],
    `${headingText} heading`,
  );

  const bodyText = legalPage.locator("main, article, section, body").first();
  await expect(bodyText).toContainText(/\S{30,}/);

  const screenshotPath = await captureCheckpoint(legalPage, testInfo, checkpointName, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiAfterClick(page);
  }

  return { screenshotPath, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const results = {};
  const failures = [];
  const evidence = {};

  const markPass = (name, detail) => {
    results[name] = { status: "PASS", detail };
  };

  const markFail = (name, detail) => {
    results[name] = { status: "FAIL", detail };
    failures.push(`${name}: ${detail}`);
  };

  const markBlockedFrom = (startingField, reason) => {
    const startIndex = REPORT_FIELDS.indexOf(startingField);
    for (let i = startIndex; i < REPORT_FIELDS.length; i += 1) {
      const field = REPORT_FIELDS[i];
      if (!results[field]) {
        markFail(field, `Blocked by previous failure: ${reason}`);
      }
    }
  };

  // Step 1 - Login with Google.
  try {
    if (SALEADS_LOGIN_URL) {
      await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
    } else if (page.url() === "about:blank") {
      throw new Error(
        "SALEADS_LOGIN_URL is not set. Provide SALEADS_LOGIN_URL for environment-agnostic execution.",
      );
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);

    await clickFirstVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
        }),
        page.getByRole("button", { name: /login|iniciar sesi[oó]n|sign in/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ],
      "Login with Google button",
    );
    await waitForUiAfterClick(page);

    const popupPage = await popupPromise;
    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded", { timeout: 30_000 });
      await maybeSelectGoogleAccount(popupPage, GOOGLE_ACCOUNT_EMAIL);
      await popupPage.waitForClose({ timeout: 30_000 }).catch(() => {});
    } else {
      await maybeSelectGoogleAccount(page, GOOGLE_ACCOUNT_EMAIL);
      await waitForUiAfterClick(page);
    }

    await expectVisibleAny(
      [
        page.getByRole("navigation"),
        page.locator("aside"),
        page.getByText(/mi negocio|dashboard|inicio/i),
      ],
      "main app interface and sidebar",
    );

    evidence.dashboardScreenshot = await captureCheckpoint(page, testInfo, "dashboard_loaded");
    markPass("Login", "Dashboard and sidebar are visible after Google login.");
  } catch (error) {
    markFail("Login", error instanceof Error ? error.message : String(error));
    markBlockedFrom("Mi Negocio menu", "Login step failed");
  }

  // Step 2 - Open Mi Negocio menu.
  if (!results["Mi Negocio menu"]) {
    try {
      await clickFirstVisible(
        [
          page.getByRole("button", { name: /mi negocio|negocio/i }),
          page.getByRole("link", { name: /mi negocio|negocio/i }),
          page.getByText(/^mi negocio$/i),
          page.getByText(/^negocio$/i),
        ],
        "Mi Negocio menu",
      );
      await waitForUiAfterClick(page);

      await expectVisibleAny([page.getByText(/agregar negocio/i)], "Agregar Negocio");
      await expectVisibleAny([page.getByText(/administrar negocios/i)], "Administrar Negocios");

      evidence.expandedMenuScreenshot = await captureCheckpoint(
        page,
        testInfo,
        "mi_negocio_expanded_menu",
      );
      markPass("Mi Negocio menu", "Mi Negocio submenu expanded with expected options.");
    } catch (error) {
      markFail("Mi Negocio menu", error instanceof Error ? error.message : String(error));
    }
  }

  // Step 3 - Validate Agregar Negocio modal.
  if (!results["Agregar Negocio modal"]) {
    try {
      await clickFirstVisible(
        [
          page.getByRole("link", { name: /agregar negocio/i }),
          page.getByRole("button", { name: /agregar negocio/i }),
          page.getByText(/agregar negocio/i),
        ],
        "Agregar Negocio entry",
      );
      await waitForUiAfterClick(page);

      const modal = await expectVisibleAny(
        [
          page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }),
          page.locator("[role='dialog'], .modal, .ant-modal").filter({
            hasText: /crear nuevo negocio/i,
          }),
        ],
        "Crear Nuevo Negocio modal",
      );

      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
      await expectVisibleAny(
        [modal.getByLabel(/nombre del negocio/i), modal.getByPlaceholder(/nombre del negocio/i)],
        "Nombre del Negocio input",
      );
      await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      evidence.agregarNegocioModalScreenshot = await captureCheckpoint(
        modal,
        testInfo,
        "agregar_negocio_modal",
      );

      const nameField = modal
        .getByLabel(/nombre del negocio/i)
        .or(modal.getByPlaceholder(/nombre del negocio/i))
        .first();
      if (await nameField.isVisible().catch(() => false)) {
        await nameField.click();
        await nameField.fill("Negocio Prueba Automatización");
      }

      await modal.getByRole("button", { name: /cancelar/i }).click();
      await expect(modal).toBeHidden({ timeout: 10_000 });

      markPass("Agregar Negocio modal", "Modal content and controls validated.");
    } catch (error) {
      markFail("Agregar Negocio modal", error instanceof Error ? error.message : String(error));
    }
  }

  // Step 4 - Open Administrar Negocios.
  if (!results["Administrar Negocios view"]) {
    try {
      const administrarVisible = await page
        .getByText(/administrar negocios/i)
        .first()
        .isVisible()
        .catch(() => false);

      if (!administrarVisible) {
        await clickFirstVisible(
          [
            page.getByRole("button", { name: /mi negocio|negocio/i }),
            page.getByRole("link", { name: /mi negocio|negocio/i }),
            page.getByText(/mi negocio|negocio/i),
          ],
          "Mi Negocio menu re-open",
        );
        await waitForUiAfterClick(page);
      }

      await clickFirstVisible(
        [
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ],
        "Administrar Negocios",
      );
      await waitForUiAfterClick(page);

      await expectVisibleAny([page.getByText(/informaci[oó]n general/i)], "Información General");
      await expectVisibleAny(
        [page.getByText(/detalles de la cuenta/i)],
        "Detalles de la Cuenta section",
      );
      await expectVisibleAny([page.getByText(/tus negocios/i)], "Tus Negocios section");
      await expectVisibleAny([page.getByText(/secci[oó]n legal/i)], "Sección Legal section");

      evidence.administrarNegociosFullScreenshot = await captureCheckpoint(
        page,
        testInfo,
        "administrar_negocios_page_full",
        true,
      );

      markPass("Administrar Negocios view", "Account page sections are visible.");
    } catch (error) {
      markFail("Administrar Negocios view", error instanceof Error ? error.message : String(error));
    }
  }

  // Step 5 - Validate Información General.
  if (!results["Información General"]) {
    try {
      await expectVisibleAny(
        [page.getByText(/business plan/i), page.getByText(/plan/i)],
        "BUSINESS PLAN text",
      );
      await expectVisibleAny(
        [page.getByRole("button", { name: /cambiar plan/i })],
        "Cambiar Plan",
      );

      const emailPattern = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-z]{2,}/;
      await expect(page.getByText(emailPattern).first()).toBeVisible();
      await expect(
        page.locator("h1, h2, h3, [data-testid*='name'], [class*='name']").first(),
      ).toBeVisible();

      markPass("Información General", "Name, email, plan and change-plan button are visible.");
    } catch (error) {
      markFail("Información General", error instanceof Error ? error.message : String(error));
    }
  }

  // Step 6 - Validate Detalles de la Cuenta.
  if (!results["Detalles de la Cuenta"]) {
    try {
      await expectVisibleAny([page.getByText(/cuenta creada/i)], "Cuenta creada");
      await expectVisibleAny([page.getByText(/estado activo/i)], "Estado activo");
      await expectVisibleAny([page.getByText(/idioma seleccionado/i)], "Idioma seleccionado");

      markPass("Detalles de la Cuenta", "Account details labels are visible.");
    } catch (error) {
      markFail("Detalles de la Cuenta", error instanceof Error ? error.message : String(error));
    }
  }

  // Step 7 - Validate Tus Negocios.
  if (!results["Tus Negocios"]) {
    try {
      await expectVisibleAny([page.getByText(/tus negocios/i)], "Tus Negocios title");
      await expectVisibleAny(
        [
          page.getByRole("button", { name: /agregar negocio/i }),
          page.getByText(/agregar negocio/i),
        ],
        "Agregar Negocio in businesses section",
      );
      await expectVisibleAny(
        [page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)],
        "Business quota text",
      );

      markPass("Tus Negocios", "Business list section and controls validated.");
    } catch (error) {
      markFail("Tus Negocios", error instanceof Error ? error.message : String(error));
    }
  }

  // Step 8 - Validate Términos y Condiciones.
  if (!results["Términos y Condiciones"]) {
    try {
      evidence.terminos = await validateLegalPage({
        page,
        context,
        testInfo,
        linkName: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        checkpointName: "terminos_y_condiciones",
      });

      markPass(
        "Términos y Condiciones",
        `Legal page validated. Final URL: ${evidence.terminos.finalUrl}`,
      );
    } catch (error) {
      markFail("Términos y Condiciones", error instanceof Error ? error.message : String(error));
    }
  }

  // Step 9 - Validate Política de Privacidad.
  if (!results["Política de Privacidad"]) {
    try {
      evidence.politica = await validateLegalPage({
        page,
        context,
        testInfo,
        linkName: "Política de Privacidad",
        headingText: "Política de Privacidad",
        checkpointName: "politica_de_privacidad",
      });

      markPass(
        "Política de Privacidad",
        `Legal page validated. Final URL: ${evidence.politica.finalUrl}`,
      );
    } catch (error) {
      markFail("Política de Privacidad", error instanceof Error ? error.message : String(error));
    }
  }

  // Step 10 - Final report.
  for (const reportField of REPORT_FIELDS) {
    if (!results[reportField]) {
      markFail(reportField, "No validation result was recorded.");
    }
  }

  const reportPayload = {
    testName: "saleads_mi_negocio_full_test",
    timestamp: new Date().toISOString(),
    loginUrl: SALEADS_LOGIN_URL || "(not provided)",
    googleAccount: GOOGLE_ACCOUNT_EMAIL,
    results,
    evidence,
  };

  const reportDir = path.join(process.cwd(), "artifacts", "saleads");
  await fs.mkdir(reportDir, { recursive: true });
  const reportPath = path.join(reportDir, "saleads_mi_negocio_full_test_report.json");
  await fs.writeFile(reportPath, JSON.stringify(reportPayload, null, 2), "utf-8");
  await testInfo.attach("saleads-final-report", { path: reportPath, contentType: "application/json" });

  console.log("SaleADS final validation report:");
  for (const field of REPORT_FIELDS) {
    const result = results[field];
    console.log(`- ${field}: ${result.status} (${result.detail})`);
  }
  console.log(`Report file: ${reportPath}`);

  expect(failures, `One or more validation steps failed.\n${failures.join("\n")}`).toEqual([]);
});
