const { test, expect } = require("@playwright/test");

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const CLICK_SETTLE_MS = Number.parseInt(process.env.SALEADS_CLICK_SETTLE_MS || "1500", 10);

const reportFields = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

function escapeForRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(CLICK_SETTLE_MS);
}

async function firstVisibleLocator(page, patterns) {
  for (const pattern of patterns) {
    const candidates = [
      page.getByRole("button", { name: pattern }),
      page.getByRole("link", { name: pattern }),
      page.getByRole("menuitem", { name: pattern }),
      page.getByRole("tab", { name: pattern }),
      page.getByText(pattern),
    ];

    for (const candidate of candidates) {
      const locator = candidate.first();
      const visible = await locator.isVisible().catch(() => false);
      if (visible) {
        return locator;
      }
    }
  }

  return null;
}

async function clickByVisibleText(page, patterns, label) {
  const locator = await firstVisibleLocator(page, patterns);
  if (!locator) {
    throw new Error(`Could not find clickable element for: ${label}`);
  }

  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function expectVisibleByText(page, patterns, label) {
  const locator = await firstVisibleLocator(page, patterns);
  expect(locator, `${label} should be visible`).not.toBeNull();
}

async function captureCheckpoint(page, testInfo, name, fullPage = true) {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: "image/png" });
}

async function check(stepResult, validationName, callback) {
  try {
    await callback();
  } catch (error) {
    stepResult.status = "FAIL";
    stepResult.errors.push(
      `${validationName}: ${error instanceof Error ? error.message : String(error)}`,
    );
  }
}

async function maybeSelectGoogleAccount(googlePage, accountEmail) {
  const emailRegex = new RegExp(escapeForRegex(accountEmail), "i");
  const accountLocators = [
    googlePage.getByText(emailRegex).first(),
    googlePage.getByRole("button", { name: emailRegex }).first(),
    googlePage.getByRole("link", { name: emailRegex }).first(),
  ];

  for (const locator of accountLocators) {
    const visible = await locator.isVisible({ timeout: 6000 }).catch(() => false);
    if (visible) {
      await locator.click();
      await waitForUi(googlePage);
      return true;
    }
  }

  return false;
}

async function openLegalPage({
  page,
  testInfo,
  linkPatterns,
  headingPatterns,
  screenshotName,
  appPageLabel,
}) {
  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  await clickByVisibleText(page, linkPatterns, appPageLabel);

  const popup = await popupPromise;
  const targetPage = popup || page;

  await targetPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await waitForUi(targetPage);
  await expectVisibleByText(targetPage, headingPatterns, `${appPageLabel} heading`);
  await captureCheckpoint(targetPage, testInfo, screenshotName);

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close({ runBeforeUnload: true }).catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await targetPage.goBack({ waitUntil: "domcontentloaded", timeout: 20000 }).catch(() => {});
    await waitForUi(targetPage);
  }

  return finalUrl;
}

test("SaleADS Mi Negocio full workflow", async ({ page }, testInfo) => {
  const accountEmail = process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;
  const configuredLoginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;

  const report = Object.fromEntries(reportFields.map((field) => [field, "PASS"]));
  const reportDetails = {};
  const legalUrls = {
    terminosYCondiciones: "",
    politicaDePrivacidad: "",
  };

  if (configuredLoginUrl) {
    await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  if (page.url().startsWith("about:blank")) {
    throw new Error(
      "Browser is on about:blank. Open the SaleADS login page first, or set SALEADS_LOGIN_URL/BASE_URL.",
    );
  }

  async function runStep(reportField, callback) {
    const stepResult = {
      status: "PASS",
      errors: [],
    };

    await callback(stepResult);
    report[reportField] = stepResult.status;
    reportDetails[reportField] = stepResult.errors.length === 0 ? ["OK"] : stepResult.errors;
  }

  await runStep("Login", async (stepResult) => {
    await check(stepResult, "Locate Google login button", async () => {
      await expectVisibleByText(
        page,
        [
          /sign in with google/i,
          /iniciar sesi[o\u00f3]n con google/i,
          /continuar con google/i,
          /google/i,
        ],
        "Google login button",
      );
    });

    await check(stepResult, "Click Google login button", async () => {
      const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
      await clickByVisibleText(
        page,
        [
          /sign in with google/i,
          /iniciar sesi[o\u00f3]n con google/i,
          /continuar con google/i,
          /google/i,
        ],
        "Google login",
      );

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
        await maybeSelectGoogleAccount(popup, accountEmail);
      }

      await maybeSelectGoogleAccount(page, accountEmail);
    });

    await check(stepResult, "Confirm main application interface appears", async () => {
      await expectVisibleByText(
        page,
        [/negocio/i, /mi negocio/i, /dashboard/i],
        "Main application interface",
      );
    });

    await check(stepResult, "Confirm left sidebar navigation is visible", async () => {
      const sidebarVisible = await page.locator("aside").first().isVisible().catch(() => false);
      if (!sidebarVisible) {
        await expectVisibleByText(page, [/negocio/i, /mi negocio/i], "Left sidebar navigation");
      }
    });

    await check(stepResult, "Capture dashboard screenshot", async () => {
      await captureCheckpoint(page, testInfo, "01-dashboard-loaded");
    });
  });

  await runStep("Mi Negocio menu", async (stepResult) => {
    await check(stepResult, "Open Mi Negocio menu", async () => {
      await clickByVisibleText(
        page,
        [/mi negocio/i, /negocio/i],
        "Sidebar Mi Negocio menu",
      );
    });

    await check(stepResult, "Confirm submenu expanded", async () => {
      await expectVisibleByText(page, [/agregar negocio/i], "Agregar Negocio submenu");
      await expectVisibleByText(page, [/administrar negocios/i], "Administrar Negocios submenu");
    });

    await check(stepResult, "Capture expanded menu screenshot", async () => {
      await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded", false);
    });
  });

  await runStep("Agregar Negocio modal", async (stepResult) => {
    await check(stepResult, "Click Agregar Negocio", async () => {
      await clickByVisibleText(page, [/agregar negocio/i], "Agregar Negocio");
    });

    await check(stepResult, "Validate Crear Nuevo Negocio modal", async () => {
      await expectVisibleByText(page, [/crear nuevo negocio/i], "Crear Nuevo Negocio title");
      const nameInput = page
        .getByRole("textbox", { name: /nombre del negocio/i })
        .or(page.getByLabel(/nombre del negocio/i))
        .first();
      await expect(nameInput).toBeVisible();
      await expectVisibleByText(page, [/tienes 2 de 3 negocios/i], "Business quota text");
      await expectVisibleByText(page, [/cancelar/i], "Cancelar button");
      await expectVisibleByText(page, [/crear negocio/i], "Crear Negocio button");
    });

    await check(stepResult, "Capture modal screenshot", async () => {
      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal", false);
    });

    await check(stepResult, "Optional fill and cancel modal", async () => {
      const nameInput = page
        .getByRole("textbox", { name: /nombre del negocio/i })
        .or(page.getByLabel(/nombre del negocio/i))
        .first();
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatizacion");
      await clickByVisibleText(page, [/cancelar/i], "Cancelar in Crear Nuevo Negocio");
    });
  });

  await runStep("Administrar Negocios view", async (stepResult) => {
    await check(stepResult, "Expand Mi Negocio if needed", async () => {
      const adminVisible = await firstVisibleLocator(page, [/administrar negocios/i]);
      if (!adminVisible) {
        await clickByVisibleText(page, [/mi negocio/i, /negocio/i], "Expand Mi Negocio");
      }
    });

    await check(stepResult, "Open Administrar Negocios", async () => {
      await clickByVisibleText(page, [/administrar negocios/i], "Administrar Negocios");
    });

    await check(stepResult, "Validate account sections", async () => {
      await expectVisibleByText(page, [/informaci[o\u00f3]n general/i], "Informacion General");
      await expectVisibleByText(page, [/detalles de la cuenta/i], "Detalles de la Cuenta");
      await expectVisibleByText(page, [/tus negocios/i], "Tus Negocios");
      await expectVisibleByText(page, [/secci[o\u00f3]n legal/i], "Seccion Legal");
    });

    await check(stepResult, "Capture account page screenshot", async () => {
      await captureCheckpoint(page, testInfo, "04-administrar-negocios-page");
    });
  });

  await runStep("Informacion General", async (stepResult) => {
    await check(stepResult, "Validate user name visible", async () => {
      await expectVisibleByText(page, [/nombre/i, /usuario/i], "User name");
    });

    await check(stepResult, "Validate user email visible", async () => {
      await expectVisibleByText(
        page,
        [new RegExp(escapeForRegex(accountEmail), "i"), /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i],
        "User email",
      );
    });

    await check(stepResult, "Validate BUSINESS PLAN text", async () => {
      await expectVisibleByText(page, [/business plan/i], "BUSINESS PLAN");
    });

    await check(stepResult, "Validate Cambiar Plan button", async () => {
      await expectVisibleByText(page, [/cambiar plan/i], "Cambiar Plan");
    });
  });

  await runStep("Detalles de la Cuenta", async (stepResult) => {
    await check(stepResult, "Validate Cuenta creada", async () => {
      await expectVisibleByText(page, [/cuenta creada/i], "Cuenta creada");
    });

    await check(stepResult, "Validate Estado activo", async () => {
      await expectVisibleByText(page, [/estado activo/i], "Estado activo");
    });

    await check(stepResult, "Validate Idioma seleccionado", async () => {
      await expectVisibleByText(page, [/idioma seleccionado/i], "Idioma seleccionado");
    });
  });

  await runStep("Tus Negocios", async (stepResult) => {
    await check(stepResult, "Validate business list visible", async () => {
      await expectVisibleByText(page, [/tus negocios/i], "Tus Negocios heading");
      const listVisible = await page.locator("li, table tbody tr, [class*='business']").first().isVisible().catch(() => false);
      if (!listVisible) {
        await expectVisibleByText(page, [/negocio/i], "At least one business item");
      }
    });

    await check(stepResult, "Validate Agregar Negocio button", async () => {
      await expectVisibleByText(page, [/agregar negocio/i], "Agregar Negocio button");
    });

    await check(stepResult, "Validate business quota text", async () => {
      await expectVisibleByText(page, [/tienes 2 de 3 negocios/i], "Tienes 2 de 3 negocios");
    });
  });

  await runStep("Terminos y Condiciones", async (stepResult) => {
    await check(stepResult, "Open and validate Terminos y Condiciones", async () => {
      legalUrls.terminosYCondiciones = await openLegalPage({
        page,
        testInfo,
        linkPatterns: [/t[e\u00e9]rminos y condiciones/i],
        headingPatterns: [/t[e\u00e9]rminos y condiciones/i],
        screenshotName: "05-terminos-y-condiciones",
        appPageLabel: "Terminos y Condiciones",
      });
    });
  });

  await runStep("Politica de Privacidad", async (stepResult) => {
    await check(stepResult, "Open and validate Politica de Privacidad", async () => {
      legalUrls.politicaDePrivacidad = await openLegalPage({
        page,
        testInfo,
        linkPatterns: [/pol[i\u00ed]tica de privacidad/i],
        headingPatterns: [/pol[i\u00ed]tica de privacidad/i],
        screenshotName: "06-politica-de-privacidad",
        appPageLabel: "Politica de Privacidad",
      });
    });
  });

  const summary = {
    ...report,
    legalUrls,
    details: reportDetails,
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(summary, null, 2), "utf8"),
    contentType: "application/json",
  });

  const failedFields = reportFields.filter((field) => report[field] === "FAIL");
  expect(
    failedFields,
    `Validation failed for: ${failedFields.join(", ")}. See final-report.json attachment for details.`,
  ).toEqual([]);
});
