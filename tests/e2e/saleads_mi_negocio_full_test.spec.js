const fs = require("fs/promises");
const { test, expect } = require("@playwright/test");

const STEP_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad",
];

const GOOGLE_BUTTON_TEXT =
  /sign in with google|iniciar sesi(?:o|\u00f3)n con google|continuar con google|google/i;
const NEGOCIO_TEXT = /negocio/i;
const MI_NEGOCIO_TEXT = /mi negocio/i;
const AGREGAR_NEGOCIO_TEXT = /agregar negocio/i;
const ADMINISTRAR_NEGOCIOS_TEXT = /administrar negocios/i;
const CREAR_NUEVO_NEGOCIO_TEXT = /crear nuevo negocio/i;
const NOMBRE_NEGOCIO_TEXT = /nombre del negocio/i;
const NEGOCIOS_LIMIT_TEXT = /tienes\s*2\s*de\s*3\s*negocios/i;
const CANCELAR_TEXT = /cancelar/i;
const CREAR_NEGOCIO_TEXT = /crear negocio/i;
const INFO_GENERAL_TEXT = /informaci(?:o|\u00f3)n general/i;
const DETALLES_CUENTA_TEXT = /detalles de la cuenta/i;
const TUS_NEGOCIOS_TEXT = /tus negocios/i;
const SECCION_LEGAL_TEXT = /secci(?:o|\u00f3)n legal/i;
const BUSINESS_PLAN_TEXT = /business plan/i;
const CAMBIAR_PLAN_TEXT = /cambiar plan/i;
const CUENTA_CREADA_TEXT = /cuenta creada/i;
const ESTADO_ACTIVO_TEXT = /estado activo/i;
const IDIOMA_SELECCIONADO_TEXT = /idioma seleccionado/i;
const TERMINOS_Y_CONDICIONES_TEXT = /t(?:e|\u00e9)rminos y condiciones/i;
const POLITICA_PRIVACIDAD_TEXT = /pol(?:i|\u00ed)tica de privacidad/i;
const EMAIL_PATTERN = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

function createReport() {
  return Object.fromEntries(STEP_FIELDS.map((field) => [field, "NOT_RUN"]));
}

async function waitForUi(page) {
  try {
    await page.waitForLoadState("domcontentloaded", { timeout: 15000 });
  } catch {
    // Some transitions do not trigger a full navigation.
  }
  try {
    await page.waitForLoadState("networkidle", { timeout: 5000 });
  } catch {
    // Network may stay open due to polling/websockets.
  }
  await page.waitForTimeout(600);
}

async function findFirstVisible(candidates, timeoutMs = 15000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of candidates) {
      try {
        if (await locator.first().isVisible()) {
          return locator.first();
        }
      } catch {
        // Try the next candidate.
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  return null;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function takeCheckpoint(page, testInfo, name, fullPage = false) {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function runStep(report, details, field, work) {
  try {
    await work();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    details[field] = error instanceof Error ? error.message : String(error);
  }
}

async function selectGoogleAccountIfVisible(page, googleEmail) {
  const accountByText = page.getByText(googleEmail, { exact: false });
  const accountCandidates = [
    accountByText,
    page.getByRole("button", { name: new RegExp(googleEmail, "i") }),
    page.getByRole("link", { name: new RegExp(googleEmail, "i") }),
  ];
  const accountLocator = await findFirstVisible(accountCandidates, 5000);
  if (!accountLocator) {
    return false;
  }
  await clickAndWait(page, accountLocator);
  return true;
}

async function findSectionContainer(page, headingRegex) {
  const headingCandidates = [
    page.getByRole("heading", { name: headingRegex }),
    page.getByText(headingRegex),
  ];
  const heading = await findFirstVisible(headingCandidates, 15000);
  if (!heading) {
    return null;
  }
  const sectionContainer = heading.locator(
    "xpath=ancestor::*[self::section or self::article or self::div][1]",
  );
  return sectionContainer.first();
}

async function openLegalLinkAndValidate({
  page,
  section,
  linkNameRegex,
  headingRegex,
  screenshotName,
  testInfo,
}) {
  const appUrlBeforeClick = page.url();
  const link = await findFirstVisible(
    [
      section.getByRole("link", { name: linkNameRegex }),
      section.getByRole("button", { name: linkNameRegex }),
      section.getByText(linkNameRegex),
    ],
    15000,
  );

  if (!link) {
    throw new Error(`No visible legal link for: ${linkNameRegex}`);
  }

  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickAndWait(page, link);
  const popup = await popupPromise;

  const legalPage = popup || page;
  await waitForUi(legalPage);
  await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({
    timeout: 30000,
  });

  const legalContent = await findFirstVisible(
    [
      legalPage.locator("main p"),
      legalPage.locator("article p"),
      legalPage.locator("section p"),
      legalPage.locator("p"),
    ],
    10000,
  );
  if (!legalContent) {
    throw new Error("Legal page did not render visible paragraph content.");
  }

  await takeCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    try {
      await page.goBack({ waitUntil: "domcontentloaded", timeout: 15000 });
    } catch {
      await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
  const googleEmail = process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

  const report = createReport();
  const details = {};
  const legalUrls = {};

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  }
  await waitForUi(page);

  await runStep(report, details, "Login", async () => {
    const sidebarBeforeLogin = await findFirstVisible(
      [page.locator("aside"), page.getByRole("navigation"), page.locator("[class*='sidebar']")],
      4000,
    );

    if (!sidebarBeforeLogin) {
      const googleLoginButton = await findFirstVisible(
        [
          page.getByRole("button", { name: GOOGLE_BUTTON_TEXT }),
          page.getByRole("link", { name: GOOGLE_BUTTON_TEXT }),
          page.getByText(GOOGLE_BUTTON_TEXT),
        ],
        20000,
      );
      if (!googleLoginButton) {
        throw new Error("Google login trigger was not visible on the login screen.");
      }

      const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, googleLoginButton);
      const popup = await popupPromise;

      if (popup) {
        await waitForUi(popup);
        await selectGoogleAccountIfVisible(popup, googleEmail);
        try {
          await popup.waitForEvent("close", { timeout: 45000 });
        } catch {
          await waitForUi(page);
        }
      } else {
        await selectGoogleAccountIfVisible(page, googleEmail);
      }
    }

    const mainAppArea = await findFirstVisible(
      [page.locator("main"), page.locator("[role='main']"), page.locator("[class*='dashboard']")],
      30000,
    );
    if (!mainAppArea) {
      throw new Error("Main application interface did not appear after login.");
    }

    const sidebarAfterLogin = await findFirstVisible(
      [page.locator("aside"), page.getByRole("navigation"), page.locator("[class*='sidebar']")],
      20000,
    );
    if (!sidebarAfterLogin) {
      throw new Error("Left sidebar navigation is not visible after login.");
    }
    await takeCheckpoint(page, testInfo, "01_dashboard_loaded");
  });

  await runStep(report, details, "Mi Negocio menu", async () => {
    const negocioLabel = await findFirstVisible(
      [page.getByText(NEGOCIO_TEXT), page.getByRole("link", { name: NEGOCIO_TEXT })],
      15000,
    );
    if (!negocioLabel) {
      throw new Error("Could not find the 'Negocio' section in the sidebar.");
    }

    const miNegocioOption = await findFirstVisible(
      [
        page.getByRole("link", { name: MI_NEGOCIO_TEXT }),
        page.getByRole("button", { name: MI_NEGOCIO_TEXT }),
        page.getByText(MI_NEGOCIO_TEXT),
      ],
      15000,
    );
    if (!miNegocioOption) {
      throw new Error("Could not find 'Mi Negocio' option.");
    }
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(AGREGAR_NEGOCIO_TEXT).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(ADMINISTRAR_NEGOCIOS_TEXT).first()).toBeVisible({ timeout: 20000 });
    await takeCheckpoint(page, testInfo, "02_mi_negocio_expanded");
  });

  await runStep(report, details, "Agregar Negocio modal", async () => {
    const agregarNegocio = await findFirstVisible(
      [
        page.getByRole("link", { name: AGREGAR_NEGOCIO_TEXT }),
        page.getByRole("button", { name: AGREGAR_NEGOCIO_TEXT }),
        page.getByText(AGREGAR_NEGOCIO_TEXT),
      ],
      15000,
    );
    if (!agregarNegocio) {
      throw new Error("Could not find 'Agregar Negocio' option.");
    }
    await clickAndWait(page, agregarNegocio);

    const modal = page.getByRole("dialog");
    await expect(modal).toBeVisible({ timeout: 15000 });
    await expect(modal.getByText(CREAR_NUEVO_NEGOCIO_TEXT)).toBeVisible();
    await expect(
      modal.getByLabel(NOMBRE_NEGOCIO_TEXT).or(modal.getByPlaceholder(NOMBRE_NEGOCIO_TEXT)),
    ).toBeVisible();
    await expect(modal.getByText(NEGOCIOS_LIMIT_TEXT)).toBeVisible();
    await expect(modal.getByRole("button", { name: CANCELAR_TEXT })).toBeVisible();
    await expect(modal.getByRole("button", { name: CREAR_NEGOCIO_TEXT })).toBeVisible();
    await takeCheckpoint(page, testInfo, "03_agregar_negocio_modal");

    const nombreInput = await findFirstVisible(
      [modal.getByLabel(NOMBRE_NEGOCIO_TEXT), modal.getByPlaceholder(NOMBRE_NEGOCIO_TEXT)],
      8000,
    );
    if (nombreInput) {
      await nombreInput.click();
      await nombreInput.fill("Negocio Prueba Automatizacion");
    }

    await clickAndWait(page, modal.getByRole("button", { name: CANCELAR_TEXT }));
    await expect(modal).toBeHidden({ timeout: 10000 });
  });

  await runStep(report, details, "Administrar Negocios view", async () => {
    const adminVisible = await findFirstVisible(
      [page.getByText(ADMINISTRAR_NEGOCIOS_TEXT), page.getByRole("link", { name: ADMINISTRAR_NEGOCIOS_TEXT })],
      3000,
    );
    if (!adminVisible) {
      const miNegocioOption = await findFirstVisible(
        [
          page.getByRole("link", { name: MI_NEGOCIO_TEXT }),
          page.getByRole("button", { name: MI_NEGOCIO_TEXT }),
          page.getByText(MI_NEGOCIO_TEXT),
        ],
        10000,
      );
      if (!miNegocioOption) {
        throw new Error("Cannot expand 'Mi Negocio' to reach 'Administrar Negocios'.");
      }
      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegocios = await findFirstVisible(
      [
        page.getByRole("link", { name: ADMINISTRAR_NEGOCIOS_TEXT }),
        page.getByRole("button", { name: ADMINISTRAR_NEGOCIOS_TEXT }),
        page.getByText(ADMINISTRAR_NEGOCIOS_TEXT),
      ],
      15000,
    );
    if (!administrarNegocios) {
      throw new Error("Could not find 'Administrar Negocios'.");
    }
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(INFO_GENERAL_TEXT).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(DETALLES_CUENTA_TEXT).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(TUS_NEGOCIOS_TEXT).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(SECCION_LEGAL_TEXT).first()).toBeVisible({ timeout: 20000 });
    await takeCheckpoint(page, testInfo, "04_administrar_negocios_page", true);
  });

  await runStep(report, details, "Informaci\u00f3n General", async () => {
    const infoSection = await findSectionContainer(page, INFO_GENERAL_TEXT);
    if (!infoSection) {
      throw new Error("'Informacion General' section was not found.");
    }

    const emailText = await findFirstVisible([infoSection.getByText(EMAIL_PATTERN)], 12000);
    if (!emailText) {
      throw new Error("User email is not visible in 'Informacion General'.");
    }

    const nameSignal = await findFirstVisible(
      [
        infoSection.getByText(/nombre/i),
        infoSection.getByText(/usuario/i),
        infoSection.getByText(/perfil/i),
      ],
      5000,
    );
    if (!nameSignal) {
      throw new Error("User name signal was not visible in 'Informacion General'.");
    }

    await expect(infoSection.getByText(BUSINESS_PLAN_TEXT)).toBeVisible();
    await expect(page.getByRole("button", { name: CAMBIAR_PLAN_TEXT })).toBeVisible();
  });

  await runStep(report, details, "Detalles de la Cuenta", async () => {
    const detailsSection = await findSectionContainer(page, DETALLES_CUENTA_TEXT);
    if (!detailsSection) {
      throw new Error("'Detalles de la Cuenta' section was not found.");
    }
    await expect(detailsSection.getByText(CUENTA_CREADA_TEXT)).toBeVisible();
    await expect(detailsSection.getByText(ESTADO_ACTIVO_TEXT)).toBeVisible();
    await expect(detailsSection.getByText(IDIOMA_SELECCIONADO_TEXT)).toBeVisible();
  });

  await runStep(report, details, "Tus Negocios", async () => {
    const businessesSection = await findSectionContainer(page, TUS_NEGOCIOS_TEXT);
    if (!businessesSection) {
      throw new Error("'Tus Negocios' section was not found.");
    }

    const businessListSignal = await findFirstVisible(
      [
        businessesSection.getByRole("list"),
        businessesSection.locator("[class*='business']"),
        businessesSection.getByText(/negocio/i),
      ],
      10000,
    );
    if (!businessListSignal) {
      throw new Error("Business list was not visible in 'Tus Negocios'.");
    }

    await expect(businessesSection.getByRole("button", { name: AGREGAR_NEGOCIO_TEXT })).toBeVisible();
    await expect(businessesSection.getByText(NEGOCIOS_LIMIT_TEXT)).toBeVisible();
  });

  await runStep(report, details, "T\u00e9rminos y Condiciones", async () => {
    const legalSection = await findSectionContainer(page, SECCION_LEGAL_TEXT);
    if (!legalSection) {
      throw new Error("'Seccion Legal' section was not found.");
    }

    const finalUrl = await openLegalLinkAndValidate({
      page,
      section: legalSection,
      linkNameRegex: TERMINOS_Y_CONDICIONES_TEXT,
      headingRegex: TERMINOS_Y_CONDICIONES_TEXT,
      screenshotName: "05_terminos_y_condiciones",
      testInfo,
    });
    legalUrls.terminosYCondiciones = finalUrl;
  });

  await runStep(report, details, "Pol\u00edtica de Privacidad", async () => {
    const legalSection = await findSectionContainer(page, SECCION_LEGAL_TEXT);
    if (!legalSection) {
      throw new Error("'Seccion Legal' section was not found.");
    }

    const finalUrl = await openLegalLinkAndValidate({
      page,
      section: legalSection,
      linkNameRegex: POLITICA_PRIVACIDAD_TEXT,
      headingRegex: POLITICA_PRIVACIDAD_TEXT,
      screenshotName: "06_politica_de_privacidad",
      testInfo,
    });
    legalUrls.politicaDePrivacidad = finalUrl;
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    report,
    details,
    legalUrls,
    loginUrlUsed: loginUrl,
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  // Keep this log explicit so it is easy to parse from CI output.
  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  const failedSteps = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([stepName]) => stepName);
  expect(
    failedSteps,
    failedSteps.length ? `Validation failures in: ${failedSteps.join(", ")}` : undefined,
  ).toEqual([]);
});
