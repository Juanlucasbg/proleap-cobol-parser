const { test, expect } = require("@playwright/test");

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

function createReport() {
  return REPORT_FIELDS.reduce((acc, key) => {
    acc[key] = "FAIL";
    return acc;
  }, {});
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(400);
}

async function clickAndWait(locator, page) {
  await expect(locator.first()).toBeVisible();
  await locator.first().click();
  await waitForUi(page);
}

async function clickByVisibleText(page, textRegex) {
  const candidates = [
    page.getByRole("button", { name: textRegex }),
    page.getByRole("link", { name: textRegex }),
    page.getByRole("menuitem", { name: textRegex }),
    page.getByRole("tab", { name: textRegex }),
    page.getByText(textRegex),
  ];

  for (const locator of candidates) {
    if ((await locator.count()) > 0) {
      await clickAndWait(locator, page);
      return true;
    }
  }

  return false;
}

async function ensureAppPageReady(page) {
  const sidebar = page.locator("aside, nav");
  await expect(sidebar.first()).toBeVisible();
}

async function openLegalAndValidate({
  page,
  context,
  linkRegex,
  headingRegex,
  screenshotName,
  testInfo,
}) {
  const linkCandidates = [
    page.getByRole("link", { name: linkRegex }),
    page.getByRole("button", { name: linkRegex }),
    page.getByText(linkRegex),
  ];

  let sourceLocator = null;
  for (const candidate of linkCandidates) {
    if ((await candidate.count()) > 0) {
      sourceLocator = candidate.first();
      break;
    }
  }

  if (!sourceLocator) {
    throw new Error(`No se encontró enlace legal para ${linkRegex}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await sourceLocator.click();

  const popup = await popupPromise;
  const legalPage = popup || page;

  await waitForUi(legalPage);

  const headingCandidates = [
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex),
  ];

  let headingFound = false;
  for (const candidate of headingCandidates) {
    if ((await candidate.count()) > 0) {
      await expect(candidate.first()).toBeVisible();
      headingFound = true;
      break;
    }
  }

  if (!headingFound) {
    throw new Error(`No se encontró encabezado legal esperado: ${headingRegex}`);
  }

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  if (bodyText.length < 120) {
    throw new Error("Contenido legal insuficiente para validar.");
  }

  await legalPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true,
  });

  console.log(`[LEGAL_URL] ${headingRegex}: ${legalPage.url()}`);

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await page.goBack().catch(() => {});
    await waitForUi(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();

  // Step 1: Login with Google.
  try {
    const loginUrl =
      process.env.SALEADS_LOGIN_URL ||
      process.env.SALEADS_BASE_URL ||
      process.env.BASE_URL;

    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const onBlankPage = /^about:blank/.test(page.url());
    if (onBlankPage) {
      throw new Error(
        "La prueba requiere una URL vía SALEADS_LOGIN_URL/SALEADS_BASE_URL/BASE_URL o una página de login ya abierta."
      );
    }

    const loginClicked = await clickByVisibleText(page, /sign in with google|iniciar sesión con google|google/i);
    if (!loginClicked) {
      throw new Error("No se encontró el botón de login con Google.");
    }

    const accountPopup = await context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

    if (accountPopup) {
      await waitForUi(accountPopup);
      const accountOption = accountPopup.getByText(ACCOUNT_EMAIL, { exact: true });
      if ((await accountOption.count()) > 0) {
        await clickAndWait(accountOption, accountPopup);
      }
      await accountPopup.waitForEvent("close", { timeout: 15000 }).catch(() => {});
    } else {
      const accountOptionInPage = page.getByText(ACCOUNT_EMAIL, { exact: true });
      if ((await accountOptionInPage.count()) > 0) {
        await clickAndWait(accountOptionInPage, page);
      }
    }

    await ensureAppPageReady(page);
    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true,
    });

    report["Login"] = "PASS";
  } catch (error) {
    console.error(`[STEP_FAIL] Login: ${error.message}`);
  }

  // Step 2: Open Mi Negocio menu.
  try {
    const negocioClicked = await clickByVisibleText(page, /^Negocio$/i);
    if (!negocioClicked) {
      throw new Error("No se encontró la sección 'Negocio'.");
    }

    const miNegocioClicked = await clickByVisibleText(page, /^Mi Negocio$/i);
    if (!miNegocioClicked) {
      throw new Error("No se encontró la opción 'Mi Negocio'.");
    }

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"),
      fullPage: true,
    });

    report["Mi Negocio menu"] = "PASS";
  } catch (error) {
    console.error(`[STEP_FAIL] Mi Negocio menu: ${error.message}`);
  }

  // Step 3: Validate Agregar Negocio modal.
  try {
    await clickByVisibleText(page, /^Agregar Negocio$/i);

    const modal = page.getByRole("dialog");
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await modal.getByLabel(/Nombre del Negocio/i).fill("Negocio Prueba Automatización");
    await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }), page);
    await expect(modal).toBeHidden();

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true,
    });

    report["Agregar Negocio modal"] = "PASS";
  } catch (error) {
    console.error(`[STEP_FAIL] Agregar Negocio modal: ${error.message}`);
  }

  // Step 4: Open Administrar Negocios.
  try {
    if ((await page.getByText(/^Administrar Negocios$/i).count()) === 0) {
      await clickByVisibleText(page, /^Mi Negocio$/i);
    }

    await clickByVisibleText(page, /^Administrar Negocios$/i);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-page.png"),
      fullPage: true,
    });

    report["Administrar Negocios view"] = "PASS";
  } catch (error) {
    console.error(`[STEP_FAIL] Administrar Negocios view: ${error.message}`);
  }

  // Step 5: Validate Información General.
  try {
    const infoSection = page.locator("section, div").filter({ hasText: /Información General/i }).first();
    await expect(infoSection).toBeVisible();

    const text = await infoSection.innerText();
    const emailMatch = text.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    if (!emailMatch) {
      throw new Error("No se detectó email en Información General.");
    }

    const lines = text
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const possibleName = lines.find(
      (line) =>
        !/@/.test(line) &&
        !/Información General|BUSINESS PLAN|Cambiar Plan|Cuenta creada|Estado activo|Idioma/i.test(line)
    );
    if (!possibleName) {
      throw new Error("No se detectó nombre de usuario en Información General.");
    }

    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    report["Información General"] = "PASS";
  } catch (error) {
    console.error(`[STEP_FAIL] Información General: ${error.message}`);
  }

  // Step 6: Validate Detalles de la Cuenta.
  try {
    const accountDetails = page.locator("section, div").filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(accountDetails).toBeVisible();
    await expect(accountDetails.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(accountDetails.getByText(/Estado activo/i)).toBeVisible();
    await expect(accountDetails.getByText(/Idioma seleccionado/i)).toBeVisible();

    report["Detalles de la Cuenta"] = "PASS";
  } catch (error) {
    console.error(`[STEP_FAIL] Detalles de la Cuenta: ${error.message}`);
  }

  // Step 7: Validate Tus Negocios.
  try {
    const businessSection = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const businessItems = businessSection.locator(
      "li, [role='listitem'], tr, [role='row'], [class*='business'], [class*='negocio']"
    );
    const itemsCount = await businessItems.count();
    if (itemsCount < 1) {
      throw new Error("No se detectó listado de negocios visible.");
    }

    report["Tus Negocios"] = "PASS";
  } catch (error) {
    console.error(`[STEP_FAIL] Tus Negocios: ${error.message}`);
  }

  // Step 8: Validate Términos y Condiciones.
  try {
    await openLegalAndValidate({
      page,
      context,
      linkRegex: /Términos y Condiciones/i,
      headingRegex: /Términos y Condiciones/i,
      screenshotName: "08-terminos-y-condiciones.png",
      testInfo,
    });

    report["Términos y Condiciones"] = "PASS";
  } catch (error) {
    console.error(`[STEP_FAIL] Términos y Condiciones: ${error.message}`);
  }

  // Step 9: Validate Política de Privacidad.
  try {
    await openLegalAndValidate({
      page,
      context,
      linkRegex: /Política de Privacidad/i,
      headingRegex: /Política de Privacidad/i,
      screenshotName: "09-politica-de-privacidad.png",
      testInfo,
    });

    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    console.error(`[STEP_FAIL] Política de Privacidad: ${error.message}`);
  }

  // Step 10: Final Report.
  const hasFailures = Object.values(report).some((status) => status !== "PASS");
  console.log(`[FINAL_REPORT] ${JSON.stringify(report, null, 2)}`);

  expect(hasFailures, "Una o más validaciones fallaron. Revisar [FINAL_REPORT] en logs.").toBeFalsy();
});
