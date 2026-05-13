const { test, expect } = require("@playwright/test");

const CHECKS = [
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

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

function initializeReport() {
  return CHECKS.reduce((acc, key) => {
    acc[key] = { status: "FAIL", details: "Not executed" };
    return acc;
  }, {});
}

async function runCheck(report, key, action) {
  try {
    await action();
    report[key] = { status: "PASS", details: "Validation passed" };
  } catch (error) {
    report[key] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error)
    };
  }
}

async function clickFirstVisible(page, locators, description) {
  for (const locator of locators) {
    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click();
      await waitForUiToSettle(page);
      return;
    }
  }

  throw new Error(`Could not click ${description}`);
}

async function performGoogleLoginIfNeeded(page, context) {
  const sidebarAlreadyVisible = page
    .locator("aside, nav")
    .filter({ hasText: /Negocio|Mi Negocio/i })
    .first();

  if (await sidebarAlreadyVisible.isVisible().catch(() => false)) {
    return;
  }

  const loginButtonCandidates = [
    page.getByRole("button", { name: /Sign in with Google/i }),
    page.getByRole("button", { name: /Iniciar sesi[oó]n con Google/i }),
    page.getByRole("button", { name: /Continuar con Google/i }),
    page.getByText(/Sign in with Google|Iniciar sesi[oó]n con Google|Google/i)
  ];

  let clicked = false;
  for (const locator of loginButtonCandidates) {
    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await candidate.click();
      const popup = await popupPromise;
      const authPage = popup || page;

      if (popup) {
        await authPage.waitForLoadState("domcontentloaded");
      } else {
        await waitForUiToSettle(page);
      }

      const preferredAccount = authPage
        .getByText("juanlucasbarbiergarzon@gmail.com", { exact: false })
        .first();

      if (await preferredAccount.isVisible().catch(() => false)) {
        await preferredAccount.click();
      }

      if (popup) {
        await popup.waitForLoadState("load");
        await popup.close().catch(() => {});
        await page.bringToFront();
      }

      await waitForUiToSettle(page);
      clicked = true;
      break;
    }
  }

  if (!clicked) {
    throw new Error("Could not locate a Google login button.");
  }
}

async function openLegalDocument(page, context, linkName, expectedHeading, screenshotPath) {
  const link = page.getByRole("link", { name: new RegExp(linkName, "i") }).first();
  await expect(link).toBeVisible();

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await link.click();

  const popup = await popupPromise;
  const targetPage = popup || page;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
  } else {
    await waitForUiToSettle(page);
  }

  await expect(
    targetPage.getByRole("heading", { name: new RegExp(expectedHeading, "i") }).first()
  ).toBeVisible();

  const bodyText = await targetPage.locator("body").innerText();
  if (!bodyText || bodyText.trim().length < 120) {
    throw new Error(`${linkName} page does not appear to contain legal content.`);
  }

  await targetPage.screenshot({ path: screenshotPath, fullPage: true });
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = initializeReport();
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const businessName = process.env.SALEADS_TEST_BUSINESS_NAME || "Negocio Prueba Automatizacion";

  if (!loginUrl) {
    throw new Error(
      "SALEADS_LOGIN_URL is required. Provide the current environment login URL (dev/staging/prod)."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);

  // Step 1: Login with Google + validate dashboard and sidebar.
  await runCheck(report, "Login", async () => {
    await performGoogleLoginIfNeeded(page, context);

    const mainInterface = page.locator("main, [role='main']").first();
    const leftSidebar = page
      .locator("aside, nav")
      .filter({ hasText: /Negocio|Mi Negocio/i })
      .first();

    await expect(mainInterface).toBeVisible();
    await expect(leftSidebar).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true
    });
  });

  // Step 2: Open Mi Negocio menu and validate submenu.
  await runCheck(report, "Mi Negocio menu", async () => {
    await clickFirstVisible(
      page,
      [
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      "Mi Negocio menu trigger"
    );

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"),
      fullPage: true
    });
  });

  // Step 3: Validate Agregar Negocio modal.
  await runCheck(report, "Agregar Negocio modal", async () => {
    await clickFirstVisible(
      page,
      [
        page.getByRole("menuitem", { name: /Agregar Negocio/i }),
        page.getByRole("link", { name: /Agregar Negocio/i }),
        page.getByRole("button", { name: /Agregar Negocio/i }),
        page.getByText(/Agregar Negocio/i)
      ],
      "Agregar Negocio"
    );

    const modalTitle = page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true
    });

    const nameInput = page.getByLabel(/Nombre del Negocio/i).first();
    await nameInput.fill(businessName);
    await page.getByRole("button", { name: /Cancelar/i }).first().click();
    await waitForUiToSettle(page);
  });

  // Step 4: Open Administrar Negocios view and validate sections.
  await runCheck(report, "Administrar Negocios view", async () => {
    const manageOption = page.getByText(/Administrar Negocios/i).first();
    if (!(await manageOption.isVisible().catch(() => false))) {
      await clickFirstVisible(
        page,
        [
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ],
        "Mi Negocio menu reopen"
      );
    }

    await clickFirstVisible(
      page,
      [
        page.getByRole("menuitem", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i)
      ],
      "Administrar Negocios"
    );

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-cuenta.png"),
      fullPage: true
    });
  });

  // Step 5: Validate Información General section.
  await runCheck(report, "Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.locator("h1, h2, h3, p, span").filter({ hasText: /[A-Za-z].*[A-Za-z]/ }).first()).toBeVisible();
  });

  // Step 6: Validate Detalles de la Cuenta section.
  await runCheck(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  // Step 7: Validate Tus Negocios section.
  await runCheck(report, "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  // Step 8: Validate Términos y Condiciones legal document.
  await runCheck(report, "Términos y Condiciones", async () => {
    const termsUrl = await openLegalDocument(
      page,
      context,
      "Términos y Condiciones",
      "Términos y Condiciones",
      testInfo.outputPath("05-terminos-y-condiciones.png")
    );
    console.log(`[Evidence] Términos y Condiciones URL: ${termsUrl}`);
  });

  // Step 9: Validate Política de Privacidad legal document.
  await runCheck(report, "Política de Privacidad", async () => {
    const privacyUrl = await openLegalDocument(
      page,
      context,
      "Política de Privacidad",
      "Política de Privacidad",
      testInfo.outputPath("06-politica-de-privacidad.png")
    );
    console.log(`[Evidence] Política de Privacidad URL: ${privacyUrl}`);
  });

  // Step 10: Final report.
  const failedChecks = Object.entries(report).filter(([, result]) => result.status === "FAIL");
  console.log("=== saleads_mi_negocio_full_test report ===");
  for (const check of CHECKS) {
    const result = report[check];
    console.log(`${check}: ${result.status} - ${result.details}`);
  }

  expect(
    failedChecks,
    `Some workflow validations failed:\n${JSON.stringify(report, null, 2)}`
  ).toHaveLength(0);
});
