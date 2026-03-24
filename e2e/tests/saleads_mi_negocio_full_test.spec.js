const fs = require('fs/promises');
const path = require('path');
const { test, expect } = require('@playwright/test');

const ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function normalizeFileName(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
}

function textCandidates(page, text) {
  const exact = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, 'i');
  const contains = new RegExp(escapeRegex(text), 'i');

  return [
    page.getByRole('button', { name: exact }),
    page.getByRole('link', { name: exact }),
    page.getByRole('menuitem', { name: exact }),
    page.getByRole('tab', { name: exact }),
    page.getByRole('heading', { name: exact }),
    page.getByRole('button', { name: contains }),
    page.getByRole('link', { name: contains }),
    page.getByText(exact),
    page.getByText(contains),
  ];
}

async function firstVisible(locators, timeoutPerLocator = 1800) {
  for (const locator of locators) {
    const first = locator.first();
    try {
      await first.waitFor({ state: 'visible', timeout: timeoutPerLocator });
      return first;
    } catch (error) {
      // Try next locator candidate.
    }
  }
  return null;
}

async function findVisibleByText(page, text) {
  return firstVisible(textCandidates(page, text));
}

async function findVisibleByAnyText(page, texts) {
  for (const text of texts) {
    const located = await findVisibleByText(page, text);
    if (located) {
      return { text, locator: located };
    }
  }
  return null;
}

async function requireVisibleByAnyText(page, texts, failureMessage) {
  const located = await findVisibleByAnyText(page, texts);
  expect(located, failureMessage).not.toBeNull();
  return located;
}

async function clickAndWait(page, locator) {
  await locator.waitFor({ state: 'visible', timeout: 20000 });
  await locator.click();
  await waitForUi(page);
}

async function clickAnyTextAndWait(page, texts, failureMessage) {
  const located = await requireVisibleByAnyText(page, texts, failureMessage);
  await clickAndWait(page, located.locator);
  return located.text;
}

async function takeCheckpoint(page, testInfo, name, fullPage = false) {
  const fileName = `${Date.now()}-${normalizeFileName(name)}.png`;
  const filePath = testInfo.outputPath(fileName);

  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, {
    path: filePath,
    contentType: 'image/png',
  });
}

async function detectSidebar(page) {
  return firstVisible([
    page.locator('aside'),
    page.getByRole('navigation'),
    page.locator('[class*="sidebar"]'),
    page.locator('nav'),
  ], 2500);
}

async function markStep(report, stepName, fn) {
  try {
    await fn();
    report[stepName] = { status: 'PASS' };
  } catch (error) {
    report[stepName] = {
      status: 'FAIL',
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const entryUrl = process.env.SALEADS_ENTRY_URL;
  if (page.url() === 'about:blank' && entryUrl) {
    await page.goto(entryUrl, { waitUntil: 'domcontentloaded' });
    await waitForUi(page);
  }

  const report = {
    Login: null,
    'Mi Negocio menu': null,
    'Agregar Negocio modal': null,
    'Administrar Negocios view': null,
    'Información General': null,
    'Detalles de la Cuenta': null,
    'Tus Negocios': null,
    'Términos y Condiciones': null,
    'Política de Privacidad': null,
  };

  let appUrl = page.url();
  const legalUrls = {};

  await markStep(report, 'Login', async () => {
    const sidebarBeforeLogin = await detectSidebar(page);
    if (!sidebarBeforeLogin) {
      const loginTexts = [
        'Sign in with Google',
        'Iniciar sesion con Google',
        'Iniciar sesion con google',
        'Continuar con Google',
        'Login with Google',
      ];

      const googlePopupPromise = page.context().waitForEvent('page', { timeout: 12000 }).catch(() => null);
      await clickAnyTextAndWait(page, loginTexts, 'Google login button was not found on login page.');
      const googlePopup = await googlePopupPromise;

      if (googlePopup) {
        await googlePopup.waitForLoadState('domcontentloaded');
        const accountInPopup = await findVisibleByText(googlePopup, ACCOUNT_EMAIL);
        if (accountInPopup) {
          await clickAndWait(googlePopup, accountInPopup);
        }
      } else {
        const accountInCurrentPage = await findVisibleByText(page, ACCOUNT_EMAIL);
        if (accountInCurrentPage) {
          await clickAndWait(page, accountInCurrentPage);
        }
      }
    }

    const sidebar = await detectSidebar(page);
    expect(sidebar, 'Main interface did not load after Google login.').not.toBeNull();
    await expect(sidebar, 'Left sidebar navigation is not visible.').toBeVisible();
    await takeCheckpoint(page, testInfo, 'step-1-dashboard-loaded');
    appUrl = page.url();
  });

  await markStep(report, 'Mi Negocio menu', async () => {
    await requireVisibleByAnyText(page, ['Negocio'], '"Negocio" section was not found in the sidebar.');
    await clickAnyTextAndWait(page, ['Mi Negocio'], '"Mi Negocio" menu option was not found.');

    await requireVisibleByAnyText(page, ['Agregar Negocio'], '"Agregar Negocio" submenu option is not visible.');
    await requireVisibleByAnyText(page, ['Administrar Negocios'], '"Administrar Negocios" submenu option is not visible.');
    await takeCheckpoint(page, testInfo, 'step-2-mi-negocio-expanded');
  });

  await markStep(report, 'Agregar Negocio modal', async () => {
    await clickAnyTextAndWait(page, ['Agregar Negocio'], 'Could not open "Agregar Negocio" modal.');
    await requireVisibleByAnyText(page, ['Crear Nuevo Negocio'], 'Modal title "Crear Nuevo Negocio" is missing.');
    await requireVisibleByAnyText(page, ['Nombre del Negocio'], '"Nombre del Negocio" field is missing.');
    await requireVisibleByAnyText(
      page,
      ['Tienes 2 de 3 negocios'],
      'Business quota text "Tienes 2 de 3 negocios" is missing in modal.',
    );
    await requireVisibleByAnyText(page, ['Cancelar'], '"Cancelar" button is missing in modal.');
    await requireVisibleByAnyText(page, ['Crear Negocio'], '"Crear Negocio" button is missing in modal.');

    const businessNameInput = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator('input[name*="negocio" i]'),
    ], 1200);
    if (businessNameInput) {
      await businessNameInput.click();
      await waitForUi(page);
      await page.keyboard.type('Negocio Prueba Automatizacion');
      await waitForUi(page);
    }

    await takeCheckpoint(page, testInfo, 'step-3-agregar-negocio-modal');
    await clickAnyTextAndWait(page, ['Cancelar'], 'Could not close "Agregar Negocio" modal using "Cancelar".');
  });

  await markStep(report, 'Administrar Negocios view', async () => {
    const adminMenuVisible = await findVisibleByText(page, 'Administrar Negocios');
    if (!adminMenuVisible) {
      await clickAnyTextAndWait(page, ['Mi Negocio'], 'Could not re-expand "Mi Negocio" menu.');
    }

    await clickAnyTextAndWait(page, ['Administrar Negocios'], 'Could not open "Administrar Negocios" page.');
    await requireVisibleByAnyText(page, ['Informacion General', 'Información General'], '"Informacion General" section is missing.');
    await requireVisibleByAnyText(page, ['Detalles de la Cuenta'], '"Detalles de la Cuenta" section is missing.');
    await requireVisibleByAnyText(page, ['Tus Negocios'], '"Tus Negocios" section is missing.');
    await requireVisibleByAnyText(page, ['Seccion Legal', 'Sección Legal'], '"Seccion Legal" section is missing.');
    await takeCheckpoint(page, testInfo, 'step-4-administrar-negocios', true);
  });

  await markStep(report, 'Información General', async () => {
    await requireVisibleByAnyText(page, ['Informacion General', 'Información General'], 'Missing "Informacion General" section.');
    await requireVisibleByAnyText(page, [ACCOUNT_EMAIL], 'User email is not visible in "Informacion General".');
    await requireVisibleByAnyText(page, ['BUSINESS PLAN'], 'Text "BUSINESS PLAN" is not visible.');
    await requireVisibleByAnyText(page, ['Cambiar Plan'], 'Button "Cambiar Plan" is not visible.');

    const probableName = await firstVisible([
      page.getByText(/^[A-Za-z][A-Za-z\s.'-]{2,}$/),
      page.getByRole('heading', { name: /^[A-Za-z][A-Za-z\s.'-]{2,}$/ }),
    ], 1200);
    expect(probableName, 'User name is not visible in "Informacion General".').not.toBeNull();
  });

  await markStep(report, 'Detalles de la Cuenta', async () => {
    await requireVisibleByAnyText(page, ['Cuenta creada'], 'Text "Cuenta creada" is not visible.');
    await requireVisibleByAnyText(page, ['Estado activo'], 'Text "Estado activo" is not visible.');
    await requireVisibleByAnyText(page, ['Idioma seleccionado'], 'Text "Idioma seleccionado" is not visible.');
  });

  await markStep(report, 'Tus Negocios', async () => {
    await requireVisibleByAnyText(page, ['Tus Negocios'], 'Section "Tus Negocios" is not visible.');
    await requireVisibleByAnyText(page, ['Agregar Negocio'], 'Button "Agregar Negocio" is missing in "Tus Negocios".');
    await requireVisibleByAnyText(
      page,
      ['Tienes 2 de 3 negocios'],
      'Text "Tienes 2 de 3 negocios" is not visible in "Tus Negocios".',
    );

    const listLike = await firstVisible([
      page.getByRole('list'),
      page.locator('table'),
      page.locator('[class*="business"]'),
    ], 1200);
    expect(listLike, 'Business list is not visible in "Tus Negocios".').not.toBeNull();
  });

  async function validateLegalLink(linkTexts, headingTexts, reportKey, screenshotName) {
    await requireVisibleByAnyText(page, ['Seccion Legal', 'Sección Legal'], 'Legal section is not visible.');

    const popupPromise = page.context().waitForEvent('page', { timeout: 7000 }).catch(() => null);
    await clickAnyTextAndWait(page, linkTexts, `Could not click legal link: ${linkTexts[0]}.`);
    const popup = await popupPromise;

    const legalPage = popup || page;
    await waitForUi(legalPage);

    await requireVisibleByAnyText(legalPage, headingTexts, `Expected legal heading not found for ${reportKey}.`);
    const legalContent = await firstVisible([
      legalPage.locator('article p'),
      legalPage.locator('main p'),
      legalPage.locator('p'),
      legalPage.getByText(/(derechos|privacidad|terminos|condiciones|datos|uso)/i),
    ], 1500);
    expect(legalContent, `Legal content text is not visible for ${reportKey}.`).not.toBeNull();

    await takeCheckpoint(legalPage, testInfo, screenshotName, true);
    legalUrls[reportKey] = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const currentUrl = page.url();
      if (currentUrl !== appUrl) {
        await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => null);
        await waitForUi(page);
      }
    }
  }

  await markStep(report, 'Términos y Condiciones', async () => {
    await validateLegalLink(
      ['Terminos y Condiciones', 'Términos y Condiciones'],
      ['Terminos y Condiciones', 'Términos y Condiciones'],
      'Términos y Condiciones',
      'step-8-terminos-y-condiciones',
    );
  });

  await markStep(report, 'Política de Privacidad', async () => {
    await validateLegalLink(
      ['Politica de Privacidad', 'Política de Privacidad'],
      ['Politica de Privacidad', 'Política de Privacidad'],
      'Política de Privacidad',
      'step-9-politica-de-privacidad',
    );
  });

  const finalReport = {
    test_name: 'saleads_mi_negocio_full_test',
    generated_at: new Date().toISOString(),
    start_url: appUrl,
    legal_urls: legalUrls,
    results: report,
  };

  const jsonPath = testInfo.outputPath('final-report.json');
  await fs.mkdir(path.dirname(jsonPath), { recursive: true });
  await fs.writeFile(jsonPath, JSON.stringify(finalReport, null, 2), 'utf8');
  await testInfo.attach('final-report', {
    path: jsonPath,
    contentType: 'application/json',
  });

  // Step 10: final PASS/FAIL summary for all required report fields.
  const failedSteps = Object.entries(report)
    .filter(([, result]) => result?.status !== 'PASS')
    .map(([name]) => name);

  expect(
    failedSteps,
    `One or more workflow validations failed.\n${JSON.stringify(finalReport, null, 2)}`,
  ).toEqual([]);
});
