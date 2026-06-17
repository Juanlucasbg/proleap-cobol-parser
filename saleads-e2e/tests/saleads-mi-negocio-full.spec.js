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

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL =
  process.env.SALEADS_LOGIN_URL ||
  process.env.SALEADS_BASE_URL ||
  process.env.BASE_URL;

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  if (page.isClosed()) {
    return;
  }

  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(400);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, { path: screenshotPath, contentType: "image/png" });
}

async function findFirstVisible(locatorCandidates, timeoutPerLocatorMs = 2000) {
  for (const locator of locatorCandidates) {
    const first = locator.first();
    const visible = await first.isVisible({ timeout: timeoutPerLocatorMs }).catch(() => false);
    if (visible) {
      return first;
    }
  }
  return null;
}

async function findClickableByText(page, regex) {
  return findFirstVisible([
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByRole("tab", { name: regex }),
    page.getByText(regex)
  ]);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function selectGoogleAccountIfPrompted(page, accountEmail) {
  const emailRegex = new RegExp(escapeRegex(accountEmail), "i");
  const accountLocator = await findFirstVisible(
    [
      page.getByRole("button", { name: emailRegex }),
      page.getByRole("link", { name: emailRegex }),
      page.locator(`[data-identifier="${accountEmail}"]`),
      page.getByText(emailRegex)
    ],
    3000
  );

  if (accountLocator) {
    await accountLocator.click();
    await waitForUi(page);
  }
}

async function clickLegalLinkAndOpenTarget(appPage, context, linkRegex) {
  const link = await findClickableByText(appPage, linkRegex);
  if (!link) {
    throw new Error(`No visible legal link found for ${linkRegex}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  const appUrlBeforeNavigation = appPage.url();

  await link.click();
  const popup = await popupPromise;
  const targetPage = popup || appPage;
  await waitForUi(targetPage);

  return { targetPage, popup, appUrlBeforeNavigation };
}

async function validateLegalPage(targetPage, headingRegex) {
  const heading = await findFirstVisible(
    [
      targetPage.getByRole("heading", { name: headingRegex }),
      targetPage.getByText(headingRegex)
    ],
    5000
  );

  if (!heading) {
    throw new Error(`Could not find legal heading matching ${headingRegex}`);
  }

  const bodyText = (await targetPage.locator("body").innerText()).trim();
  if (bodyText.length < 120) {
    throw new Error("Legal page content appears too short.");
  }
}

async function returnToApplicationTab(appPage, targetPage, popup, appUrlBeforeNavigation) {
  if (popup && targetPage === popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  if (appPage.url() !== appUrlBeforeNavigation) {
    await appPage
      .goBack({ waitUntil: "domcontentloaded" })
      .catch(async () => appPage.goto(appUrlBeforeNavigation, { waitUntil: "domcontentloaded" }));
    await waitForUi(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  const errors = {};
  let termsUrl = null;
  let privacyUrl = null;

  const runStep = async (fieldName, stepHandler) => {
    try {
      await stepHandler();
      report[fieldName] = "PASS";
    } catch (error) {
      report[fieldName] = "FAIL";
      errors[fieldName] = error instanceof Error ? error.message : String(error);
      console.error(`Step "${fieldName}" failed:`, error);
    }
  };

  await runStep("Login", async () => {
    if (!LOGIN_URL) {
      throw new Error(
        "Provide SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL) to run this environment-agnostic flow."
      );
    }

    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const signInWithGoogle = await findClickableByText(
      page,
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
    );
    if (!signInWithGoogle) {
      throw new Error("Google sign-in trigger was not found.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await signInWithGoogle.click();
    const popup = await popupPromise;

    if (popup) {
      await waitForUi(popup);
      await selectGoogleAccountIfPrompted(popup, GOOGLE_ACCOUNT_EMAIL);
      await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
    } else {
      await selectGoogleAccountIfPrompted(page, GOOGLE_ACCOUNT_EMAIL);
    }

    await waitForUi(page);

    await expect(page.getByText(/Negocio/i)).toBeVisible({ timeout: 60000 });
    await expect(page.locator("aside, nav, [class*='sidebar']").first()).toBeVisible({
      timeout: 60000
    });

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await findFirstVisible(
      [page.getByText(/^Negocio$/i), page.getByText(/Negocio/i)],
      5000
    );
    if (!negocioSection) {
      throw new Error("Sidebar section 'Negocio' was not found.");
    }

    const miNegocio = await findClickableByText(page, /Mi Negocio/i);
    if (!miNegocio) {
      throw new Error("'Mi Negocio' option was not found.");
    }

    await clickAndWait(page, miNegocio);
    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();
    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await findClickableByText(page, /^Agregar Negocio$/i);
    if (!agregarNegocio) {
      throw new Error("'Agregar Negocio' option was not visible.");
    }

    await clickAndWait(page, agregarNegocio);
    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(page.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const cancelarButton = await findClickableByText(page, /^Cancelar$/i);
    const crearNegocioButton = await findClickableByText(page, /^Crear Negocio$/i);
    if (!cancelarButton || !crearNegocioButton) {
      throw new Error("Modal buttons 'Cancelar' and/or 'Crear Negocio' were not found.");
    }

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const businessNameInput = await findFirstVisible(
      [
        page.getByRole("textbox", { name: /Nombre del Negocio/i }),
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input").first()
      ],
      3000
    );

    if (businessNameInput) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }

    await clickAndWait(page, cancelarButton);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page
      .getByText(/Administrar Negocios/i)
      .first()
      .isVisible()
      .catch(() => false);
    if (!administrarVisible) {
      const miNegocio = await findClickableByText(page, /Mi Negocio/i);
      if (miNegocio) {
        await clickAndWait(page, miNegocio);
      }
    }

    const administrarNegocios = await findClickableByText(page, /Administrar Negocios/i);
    if (!administrarNegocios) {
      throw new Error("'Administrar Negocios' option was not found.");
    }

    await clickAndWait(page, administrarNegocios);
    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page.png", true);
  });

  await runStep("Información General", async () => {
    const section = page
      .locator("section, div")
      .filter({ hasText: /Informaci[oó]n General/i })
      .first();
    await expect(section).toBeVisible();

    const sectionText = await section.innerText();
    if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(sectionText)) {
      throw new Error("No visible email found in 'Información General'.");
    }

    if (!/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(sectionText)) {
      throw new Error("No visible name-like text found in 'Información General'.");
    }

    await expect(section.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(section.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const section = page
      .locator("section, div")
      .filter({ hasText: /Detalles de la Cuenta/i })
      .first();
    await expect(section).toBeVisible();
    await expect(section.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(section.getByText(/Estado activo/i)).toBeVisible();
    await expect(section.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const section = page
      .locator("section, div")
      .filter({ hasText: /Tus Negocios/i })
      .first();
    await expect(section).toBeVisible();
    await expect(section.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(section.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const listItems = await section.locator("li, tr, [class*='business']").count();
    const text = await section.innerText();
    if (listItems === 0 && !/negocio/i.test(text)) {
      throw new Error("No visible business list content found in 'Tus Negocios'.");
    }
  });

  await runStep("Términos y Condiciones", async () => {
    const { targetPage, popup, appUrlBeforeNavigation } = await clickLegalLinkAndOpenTarget(
      page,
      context,
      /T[eé]rminos y Condiciones/i
    );

    await validateLegalPage(targetPage, /T[eé]rminos y Condiciones/i);
    termsUrl = targetPage.url();
    await captureCheckpoint(targetPage, testInfo, "05-terminos-y-condiciones.png", true);

    await returnToApplicationTab(page, targetPage, popup, appUrlBeforeNavigation);
  });

  await runStep("Política de Privacidad", async () => {
    const { targetPage, popup, appUrlBeforeNavigation } = await clickLegalLinkAndOpenTarget(
      page,
      context,
      /Pol[ií]tica de Privacidad/i
    );

    await validateLegalPage(targetPage, /Pol[ií]tica de Privacidad/i);
    privacyUrl = targetPage.url();
    await captureCheckpoint(targetPage, testInfo, "06-politica-de-privacidad.png", true);

    await returnToApplicationTab(page, targetPage, popup, appUrlBeforeNavigation);
  });

  const finalReport = {
    generatedAt: new Date().toISOString(),
    loginUrlUsed: LOGIN_URL || null,
    googleAccountExpected: GOOGLE_ACCOUNT_EMAIL,
    results: report,
    legalUrls: {
      terminosYCondiciones: termsUrl,
      politicaDePrivacidad: privacyUrl
    },
    failures: errors
  };

  await testInfo.attach("final-report.json", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json"
  });
  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  const failedFields = REPORT_FIELDS.filter((field) => report[field] !== "PASS");
  expect(
    failedFields,
    `One or more workflow checks failed. See final-report.json for details: ${failedFields.join(", ")}`
  ).toEqual([]);
});
