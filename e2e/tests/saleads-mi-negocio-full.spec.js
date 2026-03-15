const { test, expect } = require('@playwright/test');

const ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL || 'juanlucasbarbiergarzon@gmail.com';
const REPORT_FIELDS = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Información General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'Términos y Condiciones',
  'Política de Privacidad',
];

function createInitialReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: 'FAIL', details: 'Not executed.' }]),
  );
}

function sanitizeError(error) {
  if (!error || !error.message) {
    return 'Unknown error.';
  }

  return error.message.split('\n')[0].slice(0, 350);
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(600);
}

async function isVisible(locator, timeout = 2000) {
  try {
    await locator.waitFor({ state: 'visible', timeout });
    return true;
  } catch {
    return false;
  }
}

async function pickVisibleLocator(candidates, elementDescription) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    if (await isVisible(locator, 3500)) {
      return locator;
    }
  }

  throw new Error(`Could not find visible element for: ${elementDescription}`);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToLoad(page);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage,
  });
}

async function selectGoogleAccountIfPrompted(authPage) {
  const emailOption = authPage.getByText(ACCOUNT_EMAIL, { exact: false }).first();

  if (await isVisible(emailOption, 9000)) {
    await emailOption.click();
    await waitForUiToLoad(authPage);
  }
}

async function validateLegalContent(legalPage, headingRegex) {
  const headingByRole = legalPage.getByRole('heading', { name: headingRegex }).first();
  if (await isVisible(headingByRole, 10000)) {
    await expect(headingByRole).toBeVisible();
  } else {
    await expect(legalPage.getByText(headingRegex).first()).toBeVisible();
  }

  const longParagraph = legalPage.locator('main p, article p, p, li').filter({ hasText: /\S.{35,}/ }).first();
  await expect(longParagraph).toBeVisible();
}

async function openAndValidateLegalLink({
  page,
  testInfo,
  linkRegex,
  headingRegex,
  screenshotName,
}) {
  const legalLink = await pickVisibleLocator(
    [
      page.getByRole('link', { name: linkRegex }),
      page.getByRole('button', { name: linkRegex }),
      page.getByText(linkRegex),
    ],
    `legal link ${linkRegex}`,
  );

  const context = page.context();
  const newTabPromise = context.waitForEvent('page', { timeout: 7000 }).catch(() => null);
  await clickAndWait(page, legalLink);

  let legalPage = await newTabPromise;
  const openedInNewTab = Boolean(legalPage);

  if (openedInNewTab) {
    await legalPage.waitForLoadState('domcontentloaded');
    await legalPage.waitForTimeout(600);
  } else {
    legalPage = page;
  }

  await validateLegalContent(legalPage, headingRegex);
  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (openedInNewTab) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => null);
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = createInitialReport();
  let blockedBy = null;

  async function runField(fieldName, fieldRunner) {
    if (blockedBy) {
      report[fieldName] = { status: 'FAIL', details: `Blocked by: ${blockedBy}` };
      return;
    }

    try {
      await fieldRunner();
      report[fieldName] = { status: 'PASS', details: 'Validated.' };
    } catch (error) {
      const reason = sanitizeError(error);
      report[fieldName] = { status: 'FAIL', details: reason };
      blockedBy = fieldName;
      await captureCheckpoint(page, testInfo, `${fieldName.replace(/\s+/g, '-').toLowerCase()}-error.png`, true).catch(() => null);
    }
  }

  await runField('Login', async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || process.env.BASE_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
      await waitForUiToLoad(page);
    } else if (page.url() === 'about:blank') {
      throw new Error('Set SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL) to open the current environment login page.');
    }

    const googleLoginButton = await pickVisibleLocator(
      [
        page.getByRole('button', { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
        page.getByRole('link', { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i),
      ],
      'Google login button',
    );

    const popupPromise = page.context().waitForEvent('page', { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, googleLoginButton);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState('domcontentloaded');
      await selectGoogleAccountIfPrompted(popup);
      await popup.waitForEvent('close', { timeout: 25000 }).catch(() => null);
    } else {
      await selectGoogleAccountIfPrompted(page);
    }

    const sidebar = page.locator('aside, nav').filter({ hasText: /negocio|dashboard|inicio/i }).first();
    await expect(sidebar).toBeVisible({ timeout: 45000 });
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 45000 });

    await captureCheckpoint(page, testInfo, '01-dashboard-loaded.png', true);
  });

  await runField('Mi Negocio menu', async () => {
    const sidebar = page.locator('aside, nav').first();
    await expect(sidebar).toBeVisible();

    const negocioToggle = await pickVisibleLocator(
      [
        page.getByRole('button', { name: /^Negocio$/i }),
        page.getByRole('link', { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      'Negocio section',
    );
    await clickAndWait(page, negocioToggle);

    const miNegocioOption = await pickVisibleLocator(
      [
        page.getByRole('button', { name: /Mi Negocio/i }),
        page.getByRole('link', { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ],
      'Mi Negocio option',
    );
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded.png', false);
  });

  await runField('Agregar Negocio modal', async () => {
    const agregarNegocioOption = await pickVisibleLocator(
      [
        page.getByRole('button', { name: /^Agregar Negocio$/i }),
        page.getByRole('link', { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      'Agregar Negocio option',
    );
    await clickAndWait(page, agregarNegocioOption);

    const dialog = page.getByRole('dialog').first();
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(dialog.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(dialog.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(dialog.getByRole('button', { name: /Cancelar/i })).toBeVisible();
    await expect(dialog.getByRole('button', { name: /Crear Negocio/i })).toBeVisible();

    await captureCheckpoint(page, testInfo, '03-crear-negocio-modal.png', false);

    const businessNameInput = await pickVisibleLocator(
      [
        dialog.getByLabel(/Nombre del Negocio/i),
        dialog.getByPlaceholder(/Nombre del Negocio/i),
        dialog.locator('input').first(),
      ],
      'Nombre del Negocio input',
    );

    await businessNameInput.click();
    await waitForUiToLoad(page);
    await businessNameInput.fill('Negocio Prueba Automatizacion');
    await waitForUiToLoad(page);
    await clickAndWait(page, dialog.getByRole('button', { name: /Cancelar/i }));
  });

  await runField('Administrar Negocios view', async () => {
    const adminOptionVisible = await isVisible(page.getByText(/Administrar Negocios/i).first(), 2000);
    if (!adminOptionVisible) {
      const miNegocioToggle = await pickVisibleLocator(
        [
          page.getByRole('button', { name: /Mi Negocio/i }),
          page.getByRole('link', { name: /Mi Negocio/i }),
          page.getByText(/Mi Negocio/i),
        ],
        'Mi Negocio toggle',
      );
      await clickAndWait(page, miNegocioToggle);
    }

    const adminOption = await pickVisibleLocator(
      [
        page.getByRole('button', { name: /Administrar Negocios/i }),
        page.getByRole('link', { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ],
      'Administrar Negocios option',
    );
    await clickAndWait(page, adminOption);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, '04-administrar-negocios-view.png', true);
  });

  await runField('Información General', async () => {
    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();

    const visibleEmail = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
    await expect(visibleEmail).toBeVisible();

    const probableName = page
      .locator('h1, h2, h3, h4, strong, p, span')
      .filter({ hasNotText: /informaci[oó]n general|business plan|cambiar plan|@|cuenta creada|estado activo|idioma seleccionado|tus negocios|secci[oó]n legal/i })
      .first();
    await expect(probableName).toBeVisible();

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runField('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runField('Tus Negocios', async () => {
    const businessSection = page.locator('section, div').filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(businessSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

    const businessRowsOrCards = businessSection.locator('li, tr, [class*="card"], [class*="item"]');
    await expect(businessRowsOrCards.first()).toBeVisible();
  });

  await runField('Términos y Condiciones', async () => {
    const termsUrl = await openAndValidateLegalLink({
      page,
      testInfo,
      linkRegex: /T[ée]rminos y Condiciones/i,
      headingRegex: /T[ée]rminos y Condiciones/i,
      screenshotName: '08-terminos-y-condiciones.png',
    });

    report['Términos y Condiciones'].details = `Validated. URL: ${termsUrl}`;
  });

  await runField('Política de Privacidad', async () => {
    const privacyUrl = await openAndValidateLegalLink({
      page,
      testInfo,
      linkRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: '09-politica-de-privacidad.png',
    });

    report['Política de Privacidad'].details = `Validated. URL: ${privacyUrl}`;
  });

  // Final report requested by the scenario.
  console.log('\n=== SaleADS Mi Negocio Workflow Final Report ===');
  for (const field of REPORT_FIELDS) {
    const result = report[field];
    console.log(`- ${field}: ${result.status}${result.details ? ` | ${result.details}` : ''}`);
  }

  const failingFields = Object.entries(report).filter(([, value]) => value.status !== 'PASS');
  expect(
    failingFields,
    `Failed validations: ${failingFields.map(([key, value]) => `${key} -> ${value.details}`).join(' | ')}`,
  ).toEqual([]);
});
