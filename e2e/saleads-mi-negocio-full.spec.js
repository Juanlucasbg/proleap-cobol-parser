const fs = require('node:fs/promises');
const { test } = require('@playwright/test');

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
const REPORT_FIELDS = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Información General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'Términos y Condiciones',
  'Política de Privacidad'
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function toRegex(textOrRegex, exact = false) {
  if (textOrRegex instanceof RegExp) {
    return textOrRegex;
  }

  const escaped = escapeRegExp(textOrRegex);
  return exact ? new RegExp(`^\\s*${escaped}\\s*$`, 'i') : new RegExp(escaped, 'i');
}

async function waitForUi(page) {
  await Promise.race([
    page.waitForLoadState('networkidle', { timeout: 10_000 }),
    page.waitForLoadState('domcontentloaded', { timeout: 10_000 })
  ]).catch(() => {});
  await page.waitForTimeout(400);
}

async function isLocatorVisible(locator, timeout = 4_000) {
  try {
    await locator.first().waitFor({ state: 'visible', timeout });
    return true;
  } catch (_error) {
    return false;
  }
}

async function findVisibleByText(scope, textOrRegex, options = {}) {
  const {
    roles = ['button', 'link'],
    exact = false,
    timeout = 10_000
  } = options;
  const regex = toRegex(textOrRegex, exact);

  const candidates = [];
  for (const role of roles) {
    candidates.push(scope.getByRole(role, { name: regex }).first());
  }
  candidates.push(scope.getByText(regex).first());

  for (const candidate of candidates) {
    if (await isLocatorVisible(candidate, timeout)) {
      return candidate;
    }
  }

  return null;
}

async function clickByText(page, textOrRegex, options = {}) {
  const locator = await findVisibleByText(page, textOrRegex, options);
  if (!locator) {
    throw new Error(`Could not find clickable element with text: ${textOrRegex}`);
  }

  await locator.click();
  await waitForUi(page);
}

async function waitForAnyVisible(locators, timeout = 12_000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeout) {
    for (const locator of locators) {
      if (await locator.first().isVisible().catch(() => false)) {
        return true;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }

  return false;
}

async function captureCheckpoint(page, testInfo, filename, fullPage = false) {
  const path = testInfo.outputPath(filename);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(filename, { path, contentType: 'image/png' });
}

async function getSectionContainer(page, headingText) {
  const headingRegex = toRegex(headingText);
  const roleHeading = page.getByRole('heading', { name: headingRegex }).first();
  if (await roleHeading.isVisible().catch(() => false)) {
    return roleHeading.locator('xpath=ancestor::*[self::section or self::div][1]');
  }

  const plainHeading = page.getByText(headingRegex).first();
  if (await plainHeading.isVisible().catch(() => false)) {
    return plainHeading.locator('xpath=ancestor::*[self::section or self::div][1]');
  }

  return null;
}

async function selectGoogleAccountIfVisible(page) {
  const currentPageAccount = await findVisibleByText(page, GOOGLE_ACCOUNT_EMAIL, {
    roles: ['button', 'link'],
    exact: true,
    timeout: 5_000
  });
  if (currentPageAccount) {
    await currentPageAccount.click();
    await waitForUi(page);
    return;
  }

  const allPages = page.context().pages();
  const maybePopup = allPages[allPages.length - 1];
  if (!maybePopup || maybePopup === page) {
    return;
  }

  await maybePopup.waitForLoadState('domcontentloaded', { timeout: 15_000 }).catch(() => {});
  const popupAccount = await findVisibleByText(maybePopup, GOOGLE_ACCOUNT_EMAIL, {
    roles: ['button', 'link'],
    exact: true,
    timeout: 6_000
  });
  if (popupAccount) {
    await popupAccount.click();
    await maybePopup.waitForLoadState('networkidle', { timeout: 20_000 }).catch(() => {});
  }
}

async function openLegalDocumentAndValidate(options) {
  const { page, testInfo, linkText, headingText, screenshotName } = options;
  const context = page.context();
  const pageCountBeforeClick = context.pages().length;

  await clickByText(page, linkText, { roles: ['link', 'button'], timeout: 12_000 });
  await page.waitForTimeout(1_500);

  let targetPage = page;
  const pagesAfterClick = context.pages();
  if (pagesAfterClick.length > pageCountBeforeClick) {
    targetPage = pagesAfterClick[pagesAfterClick.length - 1];
    await targetPage.waitForLoadState('domcontentloaded', { timeout: 20_000 }).catch(() => {});
  }

  const headingVisible = Boolean(
    await findVisibleByText(targetPage, headingText, {
      roles: ['heading'],
      timeout: 15_000
    })
  );

  const legalContentVisible = await waitForAnyVisible(
    [
      targetPage.locator('p').first(),
      targetPage.locator('li').first(),
      targetPage.locator('article').first()
    ],
    12_000
  );

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (targetPage !== page) {
    await targetPage.close().catch(() => {});
    await page.bringToFront().catch(() => {});
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await waitForUi(page);
  }

  return {
    pass: headingVisible && legalContentVisible,
    finalUrl
  };
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  test.setTimeout(10 * 60 * 1000);

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, 'FAIL']));
  const failureDetails = [];
  const urls = {};

  const markResult = (field, isPass, detail) => {
    report[field] = isPass ? 'PASS' : 'FAIL';
    if (!isPass && detail) {
      failureDetails.push(`${field}: ${detail}`);
    }
  };

  // Step 1 - Login with Google
  try {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || process.env.BASE_URL;
    if (page.url() === 'about:blank') {
      if (!loginUrl) {
        throw new Error(
          'Page started at about:blank and no URL was provided. Set SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL).'
        );
      }
      await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    }

    const loginCandidates = [
      'Sign in with Google',
      'Continue with Google',
      'Iniciar sesión con Google',
      'Continuar con Google',
      'Google'
    ];

    let loginClicked = false;
    for (const candidate of loginCandidates) {
      const loginButton = await findVisibleByText(page, candidate, {
        roles: ['button', 'link'],
        timeout: 6_000
      });
      if (loginButton) {
        await loginButton.click();
        loginClicked = true;
        await waitForUi(page);
        break;
      }
    }

    if (!loginClicked) {
      throw new Error('No Google login button was found.');
    }

    await selectGoogleAccountIfVisible(page);

    const sidebarVisible = await waitForAnyVisible(
      [
        page.locator('aside').first(),
        page.locator('nav').first(),
        page.getByText(/Mi\s*Negocio|Negocio/i).first()
      ],
      60_000
    );
    const mainInterfaceVisible = await waitForAnyVisible(
      [page.locator('main').first(), page.locator('section').first()],
      20_000
    );

    await captureCheckpoint(page, testInfo, '01-dashboard-loaded.png', true);
    markResult('Login', mainInterfaceVisible && sidebarVisible, 'Main UI or sidebar did not become visible.');
  } catch (error) {
    markResult('Login', false, error.message);
  }

  // Step 2 - Open Mi Negocio menu
  try {
    const negocioSection = await findVisibleByText(page, 'Negocio', {
      roles: ['button', 'link'],
      timeout: 8_000
    });
    if (negocioSection) {
      await negocioSection.click();
      await waitForUi(page);
    }

    await clickByText(page, 'Mi Negocio', { roles: ['button', 'link'], timeout: 10_000 });
    const addBusinessVisible = Boolean(
      await findVisibleByText(page, 'Agregar Negocio', { roles: ['link', 'button'], timeout: 10_000 })
    );
    const manageBusinessesVisible = Boolean(
      await findVisibleByText(page, 'Administrar Negocios', {
        roles: ['link', 'button'],
        timeout: 10_000
      })
    );

    await captureCheckpoint(page, testInfo, '02-mi-negocio-expanded.png', true);
    markResult(
      'Mi Negocio menu',
      addBusinessVisible && manageBusinessesVisible,
      'Mi Negocio submenu did not expose expected options.'
    );
  } catch (error) {
    markResult('Mi Negocio menu', false, error.message);
  }

  // Step 3 - Validate Agregar Negocio modal
  try {
    await clickByText(page, 'Agregar Negocio', { roles: ['button', 'link'], timeout: 10_000 });

    const modalTitle = Boolean(
      await findVisibleByText(page, 'Crear Nuevo Negocio', { roles: ['heading'], timeout: 10_000 })
    );
    const nameInputVisible = await waitForAnyVisible(
      [
        page.getByLabel(/Nombre del Negocio/i).first(),
        page.getByPlaceholder(/Nombre del Negocio/i).first(),
        page.locator('input[name*="nombre"], input[id*="nombre"]').first()
      ],
      10_000
    );
    const businessLimitText = Boolean(
      await findVisibleByText(page, 'Tienes 2 de 3 negocios', { roles: [], timeout: 10_000 })
    );
    const cancelButtonVisible = Boolean(
      await findVisibleByText(page, 'Cancelar', { roles: ['button'], timeout: 8_000 })
    );
    const createBusinessButtonVisible = Boolean(
      await findVisibleByText(page, 'Crear Negocio', { roles: ['button'], timeout: 8_000 })
    );

    await captureCheckpoint(page, testInfo, '03-agregar-negocio-modal.png', true);

    if (nameInputVisible) {
      const input = page
        .getByLabel(/Nombre del Negocio/i)
        .or(page.getByPlaceholder(/Nombre del Negocio/i))
        .first();
      await input.fill('Negocio Prueba Automatizacion');
    }
    if (cancelButtonVisible) {
      await clickByText(page, 'Cancelar', { roles: ['button'], timeout: 5_000 });
    } else {
      await page.keyboard.press('Escape').catch(() => {});
      await waitForUi(page);
    }

    markResult(
      'Agregar Negocio modal',
      modalTitle && nameInputVisible && businessLimitText && cancelButtonVisible && createBusinessButtonVisible,
      'Agregar Negocio modal did not match expected structure.'
    );
  } catch (error) {
    markResult('Agregar Negocio modal', false, error.message);
  }

  // Step 4 - Open Administrar Negocios
  try {
    const administrarVisible = await findVisibleByText(page, 'Administrar Negocios', {
      roles: ['button', 'link'],
      timeout: 4_000
    });
    if (!administrarVisible) {
      await clickByText(page, 'Mi Negocio', { roles: ['button', 'link'], timeout: 8_000 });
    }

    await clickByText(page, 'Administrar Negocios', {
      roles: ['button', 'link'],
      timeout: 12_000
    });

    const hasInfoGeneral = Boolean(
      await findVisibleByText(page, 'Información General', { roles: ['heading'], timeout: 20_000 })
    );
    const hasDetallesCuenta = Boolean(
      await findVisibleByText(page, 'Detalles de la Cuenta', { roles: ['heading'], timeout: 12_000 })
    );
    const hasTusNegocios = Boolean(
      await findVisibleByText(page, 'Tus Negocios', { roles: ['heading'], timeout: 12_000 })
    );
    const hasLegalSection = Boolean(
      await findVisibleByText(page, 'Sección Legal', { roles: ['heading'], timeout: 12_000 })
    );

    await captureCheckpoint(page, testInfo, '04-administrar-negocios-page.png', true);
    markResult(
      'Administrar Negocios view',
      hasInfoGeneral && hasDetallesCuenta && hasTusNegocios && hasLegalSection,
      'Account management page did not show all expected sections.'
    );
  } catch (error) {
    markResult('Administrar Negocios view', false, error.message);
  }

  // Step 5 - Validate Información General
  try {
    const infoSection = await getSectionContainer(page, 'Información General');
    const infoScope = infoSection || page;

    const emailVisible = Boolean(
      await findVisibleByText(infoScope, /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i, {
        roles: [],
        timeout: 12_000
      })
    );
    const businessPlanVisible = Boolean(
      await findVisibleByText(infoScope, /BUSINESS\s*PLAN/i, { roles: [], timeout: 12_000 })
    );
    const changePlanVisible = Boolean(
      await findVisibleByText(infoScope, 'Cambiar Plan', { roles: ['button', 'link'], timeout: 12_000 })
    );

    let hasPotentialUserName = false;
    const infoText = infoSection
      ? (await infoSection.innerText().catch(() => '')) || ''
      : (await page.locator('body').innerText().catch(() => '')) || '';
    const lines = infoText
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean);
    for (const line of lines) {
      const isMetaLine = /informaci[oó]n general|business plan|cambiar plan|@|cuenta creada|estado activo|idioma/i.test(
        line
      );
      if (!isMetaLine && line.length >= 3) {
        hasPotentialUserName = true;
        break;
      }
    }

    markResult(
      'Información General',
      emailVisible && businessPlanVisible && changePlanVisible && hasPotentialUserName,
      'Información General fields are incomplete.'
    );
  } catch (error) {
    markResult('Información General', false, error.message);
  }

  // Step 6 - Validate Detalles de la Cuenta
  try {
    const detailsSection = await getSectionContainer(page, 'Detalles de la Cuenta');
    const detailsScope = detailsSection || page;

    const accountCreatedVisible = Boolean(
      await findVisibleByText(detailsScope, 'Cuenta creada', { roles: [], timeout: 10_000 })
    );
    const activeStatusVisible = Boolean(
      await findVisibleByText(detailsScope, /Estado\s*activo|activo/i, { roles: [], timeout: 10_000 })
    );
    const selectedLanguageVisible = Boolean(
      await findVisibleByText(detailsScope, 'Idioma seleccionado', { roles: [], timeout: 10_000 })
    );

    markResult(
      'Detalles de la Cuenta',
      accountCreatedVisible && activeStatusVisible && selectedLanguageVisible,
      'Detalles de la Cuenta is missing required values.'
    );
  } catch (error) {
    markResult('Detalles de la Cuenta', false, error.message);
  }

  // Step 7 - Validate Tus Negocios
  try {
    const businessesSection = await getSectionContainer(page, 'Tus Negocios');
    const businessScope = businessesSection || page;

    const addBusinessButtonVisible = Boolean(
      await findVisibleByText(businessScope, 'Agregar Negocio', {
        roles: ['button', 'link'],
        timeout: 10_000
      })
    );
    const businessLimitVisible = Boolean(
      await findVisibleByText(businessScope, 'Tienes 2 de 3 negocios', { roles: [], timeout: 10_000 })
    );
    const businessListVisible = await waitForAnyVisible(
      [
        businessScope.locator('li').first(),
        businessScope.locator('[role="listitem"]').first(),
        businessScope.locator('article').first(),
        businessScope.locator('table tbody tr').first()
      ],
      10_000
    );

    markResult(
      'Tus Negocios',
      businessListVisible && addBusinessButtonVisible && businessLimitVisible,
      'Tus Negocios section did not expose expected list and controls.'
    );
  } catch (error) {
    markResult('Tus Negocios', false, error.message);
  }

  // Step 8 - Validate Términos y Condiciones
  try {
    const termsResult = await openLegalDocumentAndValidate({
      page,
      testInfo,
      linkText: 'Términos y Condiciones',
      headingText: 'Términos y Condiciones',
      screenshotName: '08-terminos-y-condiciones.png'
    });

    urls.terminosYCondiciones = termsResult.finalUrl;
    markResult(
      'Términos y Condiciones',
      termsResult.pass,
      'Terms and Conditions page did not render expected legal content.'
    );
  } catch (error) {
    markResult('Términos y Condiciones', false, error.message);
  }

  // Step 9 - Validate Política de Privacidad
  try {
    const privacyResult = await openLegalDocumentAndValidate({
      page,
      testInfo,
      linkText: 'Política de Privacidad',
      headingText: 'Política de Privacidad',
      screenshotName: '09-politica-de-privacidad.png'
    });

    urls.politicaDePrivacidad = privacyResult.finalUrl;
    markResult(
      'Política de Privacidad',
      privacyResult.pass,
      'Privacy Policy page did not render expected legal content.'
    );
  } catch (error) {
    markResult('Política de Privacidad', false, error.message);
  }

  // Step 10 - Final report
  const finalReport = {
    generatedAt: new Date().toISOString(),
    report,
    urls,
    failures: failureDetails
  };
  const reportPath = testInfo.outputPath('final-report.json');
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), 'utf8');
  await testInfo.attach('final-report.json', {
    path: reportPath,
    contentType: 'application/json'
  });

  const failedFields = REPORT_FIELDS.filter((field) => report[field] !== 'PASS');
  if (failedFields.length > 0) {
    throw new Error(`Final report contains failed checks: ${failedFields.join(', ')}`);
  }
});
