const { test, expect } = require('@playwright/test');

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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function buildCaseInsensitivePattern(values) {
  return new RegExp(values.map(escapeRegExp).join('|'), 'i');
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  try {
    await page.waitForLoadState('networkidle', { timeout: 10_000 });
  } catch {
    // Some pages keep long-lived requests; domcontentloaded is enough in that case.
  }
  await page.waitForTimeout(400);
}

async function firstVisible(candidates, description, timeout = 15_000) {
  for (const locator of candidates) {
    try {
      await locator.first().waitFor({ state: 'visible', timeout });
      return locator.first();
    } catch {
      // Try next candidate.
    }
  }

  throw new Error(`Could not find visible element for: ${description}`);
}

function controlCandidates(root, textOptions) {
  const pattern = buildCaseInsensitivePattern(textOptions);

  return [
    root.getByRole('button', { name: pattern }),
    root.getByRole('link', { name: pattern }),
    root.getByRole('menuitem', { name: pattern }),
    root.getByRole('tab', { name: pattern }),
    root.getByRole('treeitem', { name: pattern }),
    root.getByText(pattern),
  ];
}

async function findVisibleControl(root, textOptions, description, timeout = 15_000) {
  return firstVisible(controlCandidates(root, textOptions), description, timeout);
}

async function clickControlAndWait(page, root, textOptions, description) {
  const control = await findVisibleControl(root, textOptions, description);
  await control.click();
  await waitForUi(page);
  return control;
}

async function captureCheckpoint(page, testInfo, checkpointName, fullPage = true) {
  const safeName = checkpointName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '');

  const screenshotPath = testInfo.outputPath(`${safeName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(checkpointName, {
    path: screenshotPath,
    contentType: 'image/png',
  });
}

function initReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = 'FAIL';
  }
  return report;
}

async function openLegalContentAndReturn({
  page,
  context,
  root,
  linkTextOptions,
  headingTextOptions,
  screenshotName,
  returnUrl,
  testInfo,
}) {
  const popupPromise = context.waitForEvent('page', { timeout: 8_000 }).catch(() => null);
  await clickControlAndWait(page, root, linkTextOptions, `${linkTextOptions[0]} control`);

  const popup = await popupPromise;
  const legalPage = popup || page;

  if (popup) {
    await waitForUi(legalPage);
  }

  const legalHeading = await firstVisible(
    [
      legalPage.getByRole('heading', { name: buildCaseInsensitivePattern(headingTextOptions) }),
      legalPage.getByText(buildCaseInsensitivePattern(headingTextOptions)),
    ],
    `${headingTextOptions[0]} heading`,
  );
  await expect(legalHeading).toBeVisible();

  const legalBodyText = (await legalPage.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  expect(
    legalBodyText.length,
    `Expected legal page to contain meaningful text for ${headingTextOptions[0]}`,
  ).toBeGreaterThan(120);

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await legalPage.close();
    await page.bringToFront();
  } else {
    const navigatedBack = await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => null);
    if (!navigatedBack && returnUrl) {
      await page.goto(returnUrl, { waitUntil: 'domcontentloaded' });
    }
  }

  await waitForUi(page);
  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const report = initReport();
  const failureReasons = {};
  const legalUrls = {};

  const markPass = (field) => {
    report[field] = 'PASS';
  };

  const markFail = (field, error) => {
    report[field] = 'FAIL';
    failureReasons[field] = error instanceof Error ? error.message : String(error);
  };

  let accountPageUrl = '';

  try {
    const entryUrl = process.env.SALEADS_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
    if (entryUrl) {
      await page.goto(entryUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    } else if (page.url() === 'about:blank') {
      throw new Error(
        'Set SALEADS_URL (or SALEADS_BASE_URL/BASE_URL) when the browser is not already on the SaleADS login page.',
      );
    }

    // Step 1: Login with Google
    const loginButton = await findVisibleControl(
      page,
      [
        'Sign in with Google',
        'Iniciar sesión con Google',
        'Ingresar con Google',
        'Continuar con Google',
        'Login with Google',
      ],
      'Google sign-in button',
      20_000,
    );

    const googlePopupPromise = context.waitForEvent('page', { timeout: 8_000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await waitForUi(googlePopup);

      const accountOption = await firstVisible(
        [
          googlePopup.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
          googlePopup.getByRole('button', { name: /juanlucasbarbiergarzon@gmail\.com/i }),
          googlePopup.getByRole('link', { name: /juanlucasbarbiergarzon@gmail\.com/i }),
        ],
        'Google account selector',
        8_000,
      ).catch(() => null);

      if (accountOption) {
        await accountOption.click();
        await waitForUi(googlePopup);
      }
    }

    await waitForUi(page);

    const sidebar = await firstVisible(
      [
        page.getByRole('navigation'),
        page.locator('aside'),
        page.locator('[class*="sidebar" i]'),
      ],
      'main app sidebar',
    );
    await expect(sidebar).toBeVisible();

    markPass('Login');
    await captureCheckpoint(page, testInfo, '01-dashboard-loaded');

    // Step 2: Open Mi Negocio menu
    await clickControlAndWait(page, page, ['Negocio'], 'Negocio section');
    await clickControlAndWait(page, page, ['Mi Negocio'], 'Mi Negocio menu option');

    await expect(
      await findVisibleControl(page, ['Agregar Negocio'], 'Agregar Negocio submenu option'),
    ).toBeVisible();
    await expect(
      await findVisibleControl(page, ['Administrar Negocios'], 'Administrar Negocios submenu option'),
    ).toBeVisible();

    markPass('Mi Negocio menu');
    await captureCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded', false);

    // Step 3: Validate Agregar Negocio modal
    await clickControlAndWait(page, page, ['Agregar Negocio'], 'Agregar Negocio submenu click');

    const modal = await firstVisible(
      [
        page.getByRole('dialog').filter({ hasText: /Crear Nuevo Negocio/i }),
        page.locator('[role="dialog"], [aria-modal="true"], .modal').filter({
          hasText: /Crear Nuevo Negocio/i,
        }),
      ],
      'Crear Nuevo Negocio modal',
    );
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const businessNameInput = await firstVisible(
      [
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
        modal.locator('input[name*="negocio" i], input[id*="negocio" i]'),
        modal.locator('input').first(),
      ],
      'Nombre del Negocio input',
    );
    await expect(businessNameInput).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(await findVisibleControl(modal, ['Cancelar'], 'Cancelar button')).toBeVisible();
    await expect(await findVisibleControl(modal, ['Crear Negocio'], 'Crear Negocio button')).toBeVisible();
    await captureCheckpoint(page, testInfo, '03-agregar-negocio-modal');

    await businessNameInput.click();
    await waitForUi(page);
    await businessNameInput.fill('Negocio Prueba Automatización');
    await clickControlAndWait(page, modal, ['Cancelar'], 'Cancelar modal button');
    await expect(modal).toBeHidden({ timeout: 10_000 });

    markPass('Agregar Negocio modal');

    // Step 4: Open Administrar Negocios
    await clickControlAndWait(page, page, ['Mi Negocio'], 'Mi Negocio menu re-open');
    await clickControlAndWait(page, page, ['Administrar Negocios'], 'Administrar Negocios option');

    const infoHeading = await firstVisible(
      [page.getByRole('heading', { name: /Información General/i }), page.getByText(/Información General/i)],
      'Información General section',
    );
    const accountDetailsHeading = await firstVisible(
      [page.getByRole('heading', { name: /Detalles de la Cuenta/i }), page.getByText(/Detalles de la Cuenta/i)],
      'Detalles de la Cuenta section',
    );
    const businessesHeading = await firstVisible(
      [page.getByRole('heading', { name: /Tus Negocios/i }), page.getByText(/Tus Negocios/i)],
      'Tus Negocios section',
    );
    const legalHeading = await firstVisible(
      [page.getByRole('heading', { name: /Sección Legal/i }), page.getByText(/Sección Legal/i)],
      'Sección Legal section',
    );

    await expect(infoHeading).toBeVisible();
    await expect(accountDetailsHeading).toBeVisible();
    await expect(businessesHeading).toBeVisible();
    await expect(legalHeading).toBeVisible();

    accountPageUrl = page.url();
    markPass('Administrar Negocios view');
    await captureCheckpoint(page, testInfo, '04-administrar-negocios-view', true);

    // Step 5: Validate Información General
    const infoSection = page
      .locator('section, div')
      .filter({ has: page.getByText(/Información General/i) })
      .first();
    const infoSectionText = (await infoSection.innerText()).replace(/\s+/g, ' ').trim();

    expect(
      infoSectionText.includes('@'),
      'Expected user email to be visible inside Información General',
    ).toBeTruthy();
    expect(
      /BUSINESS PLAN/i.test(infoSectionText),
      'Expected BUSINESS PLAN to be visible inside Información General',
    ).toBeTruthy();
    await expect(await findVisibleControl(infoSection, ['Cambiar Plan'], 'Cambiar Plan button')).toBeVisible();

    const hasLikelyUserName = infoSectionText
      .split(' ')
      .filter(Boolean)
      .some((word) => /^[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}$/.test(word));
    expect(hasLikelyUserName, 'Expected user name to be visible in Información General').toBeTruthy();
    markPass('Información General');

    // Step 6: Validate Detalles de la Cuenta
    const detailsSection = page
      .locator('section, div')
      .filter({ has: page.getByText(/Detalles de la Cuenta/i) })
      .first();
    const detailsText = (await detailsSection.innerText()).replace(/\s+/g, ' ').trim();

    expect(/Cuenta creada/i.test(detailsText), 'Expected "Cuenta creada" text').toBeTruthy();
    expect(/Estado.*activo/i.test(detailsText), 'Expected "Estado activo" text').toBeTruthy();
    expect(/Idioma seleccionado/i.test(detailsText), 'Expected "Idioma seleccionado" text').toBeTruthy();
    markPass('Detalles de la Cuenta');

    // Step 7: Validate Tus Negocios
    const businessesSection = page
      .locator('section, div')
      .filter({ has: page.getByText(/Tus Negocios/i) })
      .first();

    await expect(businessesSection).toBeVisible();
    await expect(await findVisibleControl(businessesSection, ['Agregar Negocio'], 'Tus Negocios add button')).toBeVisible();
    await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const businessesContent = (await businessesSection.innerText()).replace(/\s+/g, ' ').trim();
    expect(
      businessesContent.length > 30,
      'Expected visible business list/content inside Tus Negocios section',
    ).toBeTruthy();
    markPass('Tus Negocios');

    // Step 8: Validate Términos y Condiciones
    const legalSection = page
      .locator('section, div')
      .filter({ has: page.getByText(/Sección Legal/i) })
      .first();
    legalUrls['Términos y Condiciones'] = await openLegalContentAndReturn({
      page,
      context,
      root: legalSection,
      linkTextOptions: ['Términos y Condiciones'],
      headingTextOptions: ['Términos y Condiciones'],
      screenshotName: '08-terminos-y-condiciones',
      returnUrl: accountPageUrl,
      testInfo,
    });
    markPass('Términos y Condiciones');

    // Step 9: Validate Política de Privacidad
    legalUrls['Política de Privacidad'] = await openLegalContentAndReturn({
      page,
      context,
      root: legalSection,
      linkTextOptions: ['Política de Privacidad'],
      headingTextOptions: ['Política de Privacidad'],
      screenshotName: '09-politica-de-privacidad',
      returnUrl: accountPageUrl,
      testInfo,
    });
    markPass('Política de Privacidad');
  } catch (error) {
    for (const field of REPORT_FIELDS) {
      if (report[field] !== 'PASS') {
        markFail(field, error);
      }
    }
  } finally {
    const finalReportPayload = {
      test: 'saleads_mi_negocio_full_test',
      report,
      legalUrls,
      failures: failureReasons,
    };

    // Emit machine-readable report for CI parsing.
    console.log(`SALEADS_FINAL_REPORT=${JSON.stringify(finalReportPayload)}`);
    await testInfo.attach('saleads-final-report', {
      body: Buffer.from(JSON.stringify(finalReportPayload, null, 2)),
      contentType: 'application/json',
    });
  }

  const failedFields = Object.entries(report)
    .filter(([, status]) => status !== 'PASS')
    .map(([field]) => field);
  expect(failedFields, `Final report contains FAIL entries: ${failedFields.join(', ')}`).toEqual([]);
});
